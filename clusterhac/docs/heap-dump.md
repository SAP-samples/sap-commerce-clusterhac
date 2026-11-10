# ClusterHAC — Heap Dump Feature

> Capture a JVM heap dump from a selected SAP Commerce Cloud pod and stream it straight to Azure
> Blob Storage, without the dump ever landing on the pod's local disk.

---

## 1. Why this exists

Taking a heap dump on a CCv2 pod is hard for two reasons:

1. **Disk space** — a heap dump is often larger than the pod's available local disk, so writing it
   to a file on the pod fails or fills the disk.
2. **Liveness probe** — while a heap dump is written the JVM is frozen. The Kubernetes liveness
   probe fails its checks and the pod is **restarted mid-dump**, losing it.

The ClusterHAC **Heap Dump** tab solves both by **streaming** the dump out of the pod through a
named pipe (FIFO) directly to an Azure Blob Storage container. Nothing large is ever held in memory
or written to the pod's disk, so the dump survives the liveness probe and does not depend on local
disk capacity.

This mirrors the approach of the reference `azure-jmap` tool (`App.groovy`), implemented natively
inside the extension.

**HAC URL:** `/hac/cluster/heap-dump`

---

## 2. Key constraints

| Constraint | Why |
| --- | --- |
| **Pod-targeted only** | A heap dump freezes the JVM, so it is never broadcast to a node group or the whole cluster. The operator must select one or more specific pods. Empty selection is rejected client- and server-side. |
| **No dump data on local disk** | The dump streams through a 0-byte FIFO pipe node to Azure. Only the pipe node itself exists briefly on disk (no dump bytes); it is deleted after the run and swept on the next run. |
| **Uploader runs in a child JVM** | An in-process reader draining a FIFO that the same JVM is heap-dumping into **deadlocks** (the dump's safepoint pauses the reader thread). The reader/uploader therefore runs in a separate `java` process. |
| **Trigger via `jcmd`** | `jcmd <pid> GC.heap_dump -gz -overwrite <fifo>` is the only mechanism that supports both gzip **and** a FIFO target. The DiagnosticCommand MBean does not expose the op on the SapMachine JVM; `HotSpotDiagnosticMXBean.dumpHeap` refuses a pre-existing FIFO and has no gzip. |

---

## 3. Architecture

### 3.1 End-to-end flow

```
HAC browser                HAC/platform node (origin)              Target pod (selected node)
-----------                --------------------------              --------------------------
Trigger ───POST /trigger──► ClusterHeapDumpController
                            • resolve HAC user (uid)
                            • load heap-dump.groovy, inject uid
                            • executeOnNodes(script, nodeIds) ───cluster event──►  ScriptExecutorEventListener
                                                                                   runs heap-dump.groovy
                                                                                   → heapDumpService.dumpToAzure(uid)
                                                                                        │
                                                                                        ▼
                                                                   DefaultHeapDumpService (async worker thread)
                                                                     1. mkfifo  /…/clusterhac-heapdump-*.fifo
                                                                     2. spawn child JVM:
                                                                        java -cp <resolved> AzureBlobPipeStreamer …
                                                                        (reads FIFO → Azure AppendBlob)
                                                                     3. jcmd <pid> GC.heap_dump -gz -overwrite <fifo>
                                                                        │ gzip stream
                                                                        ▼
                                                                FIFO ──► child uploader ──AppendBlock──► Azure container
                                                                                                         <host>/<host>_<user>_<ts>.hprof.gz

Status table ◄──poll /dumpinfos + /dumpinfos/results── ClusterHeapDumpInfoExecutorService ◄─result event─ ClusterHeapDumpInfoEventListener
(live phase + bytes, per pod)                          (UUID-keyed Guava cache)                            reports heapDumpService.getState()
```

### 3.2 Two cluster round-trips (reused ClusterHAC patterns)

- **Trigger** — reuses the pod-targeted Groovy path: `ClusterScriptExecutorService.executeOnNodes(script, nodeIds)`
  runs `heap-dump.groovy` only on the explicitly selected nodes.
- **Status** — the UUID + `ClusterAwareEvent` broadcast/collect/poll pattern (cloned from the
  thread-dump-info quartet): the controller submits a request (UUID), every node replies with its
  current heap-dump state, replies land in a per-UUID Guava cache, and the UI polls for them.

### 3.3 Why a child process (the critical design point)

A heap dump pauses every thread of the dumping JVM at a safepoint. If the FIFO reader were a thread
**inside** that same JVM, it would be paused too — but the dump can't make progress until all threads
reach the safepoint, and the reader is blocked in a native `read()`/`open()` on the pipe. Result:
**self-deadlock** (verified: the dump starts, then hangs forever with no further output).

Running the reader/uploader as a **separate `java` process** breaks the cycle: that process is not
affected by the dumping JVM's safepoint, so it drains the FIFO while the dumping JVM writes to it.
This is exactly why the reference `azure-jmap` is a standalone program.

---

## 4. Components

All Java is in the `clusterhac` extension. Controllers live under `hac/src` (scanned by the hac web
module); services/events under `src`.

| File | Role |
| --- | --- |
| `hac/src/de/hybris/platform/hac/controller/ClusterHeapDumpController.java` | HAC controller: view, `/trigger` (pod-only), `/config-status`, `/dumpinfos`, `/dumpinfos/results`. Resolves the HAC user uid and injects it into the script. |
| `resources/scripts/heap-dump/heap-dump.groovy` | Runs on each selected pod; calls the injected `heapDumpService.dumpToAzure('<user>')`. |
| `src/com/sap/commerce/clusterhac/service/HeapDumpService.java` | Service interface + `HeapDumpState` (status/blob/bytes/timestamps/message) + `Status` enum. |
| `src/com/sap/commerce/clusterhac/service/impl/DefaultHeapDumpService.java` | Orchestrates the dump on the local node: FIFO creation + sweep, child-uploader launch, `jcmd` trigger, result parsing, live state. |
| `src/com/sap/commerce/clusterhac/service/impl/AzureBlobPipeStreamer.java` | FIFO→Azure AppendBlob streaming (chunked, gzip-aware blob naming) **and** the `main()` entry point run by the child uploader JVM. |
| `src/com/sap/commerce/clusterhac/service/ClusterHeapDumpInfoExecutorService.java` | Status round-trip: UUID submit, per-node result collection in a Guava cache. |
| `src/com/sap/commerce/clusterhac/event/ClusterHeapDumpInfoEvent.java` / `…ResultEvent.java` | Broadcast request / addressed-back result `ClusterAwareEvent`s. |
| `src/com/sap/commerce/clusterhac/event/listeners/ClusterHeapDumpInfoEventListener.java` / `…ResultEventListener.java` | Report this node's state / land replies in the cache. |
| `resources/clusterhac-beans.xml` | `ClusterHeapDumpInfoResult` bean (nodeID, clusterGroup, status, blobName, blobUrl, bytesStreamed, startedAt, finishedAt, message). |
| `resources/clusterhac-spring.xml` | Wires `heapDumpService`, the executor service, and the two listeners. |
| `resources/clusterhac-tab-config.json` | Registers the `/heap-dump` sub-tab ("Cluster Heap Dump"). |
| `hac/resources/jsp/heap-dump.jsp` | UI: pod picker (required), Azure-config warning, trigger (with confirm), status table. |
| `hac/resources/static/js/cluster/heap-dump.js` | Pod picker config, trigger POST, submit/poll/render of per-pod status (live bytes + phase). |
| `testsrc/com/sap/commerce/clusterhac/service/impl/DefaultHeapDumpServiceTest.java` | Unit tests for streaming/chunking, gzip blob naming, config resolution + fallback, missing-config guard. |

---

## 5. Configuration

| Property | Default | Meaning |
| --- | --- | --- |
| `clusterhac.heapdump.azure.connection-string` | *(blank)* | Azure Blob connection string the dump is streamed to. **If blank, falls back at runtime to** `azure.hotfolder.storage.account.connection-string` (the CCv2 hotfolder storage account). |
| `clusterhac.heapdump.azure.container` | `memory-dump-container` | Container the blobs are written to (created if missing). |
| `clusterhac.heapdump.gzip` | `1` | Gzip level (`1`=fastest … `9`=strongest, `0`=off). |

Resolution order for the connection string:

1. `clusterhac.heapdump.azure.connection-string` if set,
2. otherwise `azure.hotfolder.storage.account.connection-string`.

The tab shows a warning and `/trigger` is rejected until a connection string (via either property)
and a container are resolvable.

### Dependency

The extension declares `<requires-extension name="azurecloud"/>` so the Azure Blob SDK
(`com.azure.storage.blob.*`, shipped in `azurecloud/lib`) is on the classpath. No extra jar is
deployed — the child uploader JVM reuses the same jars.

### Security note

The connection string contains the storage account key. Read via `Config`, it is **visible from
HAC's own Configuration tab**. Keep the real value in `local.properties` (not committed), or supply a
scoped/time-limited SAS token. Rotating the key after use is recommended.

---

## 6. Blob naming

```
<container>/<host>/<host>_<user>_<yyyyMMdd-HHmmss>.hprof[.gz]
```

- `<host>/` is a virtual folder prefix (Azure has no real folders; the prefix materializes with the
  blob).
- `<user>` is the HAC user who triggered the dump (resolved in the controller, sanitized; `unknown`
  if unresolved). The target pod has no HTTP session, so the uid is captured at the origin and passed
  to the pod via a `$triggeredBy` placeholder in the Groovy script.
- `.gz` is appended when the stream's first bytes are gzip magic (`1F 8B 08`).

Example: `memory-dump-container/api-7d9f8/api-7d9f8_admin_20261026-154406.hprof.gz`

---

## 7. Streaming internals

- **Buffer** 256 KB; **block** just under Azure's 4 MB AppendBlob limit (`4 MiB − 4×256 KB`).
- Chunks are accumulated and uploaded via `AppendBlobClient.appendBlock(...)`; the container is
  auto-created if missing.
- The child reads the FIFO with `FileInputStream` (not `Files.newInputStream` — the latter returns a
  seekable channel whose `available()` calls `seek()`, which throws *Illegal seek* on a FIFO).
- The child emits `CLUSTERHAC_HEAPDUMP_PROGRESS <bytes>` every ~2 MB and a final
  `CLUSTERHAC_HEAPDUMP_RESULT OK <blob> <bytes> <url>` (or `ERR <message>`); the parent parses these
  to drive live status and the final outcome.

### Child classpath

`java.class.path` under Commerce/Tomcat is just the bootstrap jar, so the child's classpath is built
from the on-disk locations of the classes it needs (our class + Azure blob/common/core + SLF4J),
discovered via each class's `CodeSource`, adding each containing directory as `<dir>/*`. This pulls
in the full Azure/netty/reactor jar set that lives beside them in `azurecloud/lib`.

---

## 8. HTTP endpoints

| Method / path | Purpose |
| --- | --- |
| `GET /cluster/heap-dump` | Tab view. |
| `POST /cluster/heap-dump/trigger` (`nodeIds`) | Trigger on selected pods. Rejects empty `nodeIds` and unconfigured Azure. Returns `{ok, configured, nodeIds}`. |
| `GET /cluster/heap-dump/config-status` | `{configured: true|false}` for the UI warning. |
| `GET /cluster/heap-dump/dumpinfos` | Submit a status round-trip → `{uuid}`. |
| `GET /cluster/heap-dump/dumpinfos/results?uuid=` | Poll per-node results (array of `ClusterHeapDumpInfoResult`). |

---

## 9. Status lifecycle

`IDLE → IN_PROGRESS → DONE | FAILED`, per node.

The status table shows, per selected pod: node id, cluster groups, status, blob name, bytes streamed
(live, growing during the upload), and a details column (phase message + started/finished times). The
UI polls every 5 s while any node is `IN_PROGRESS`. Phases surfaced: *Starting… → Dumping heap and
streaming to Azure… → Streaming to Azure… (with live MB) → Heap dump streamed to Azure* (DONE), or a
failure message (FAILED). A failing child process surfaces as FAILED with the uploader's message —
never a silent hang.

---

## 10. Operational notes

- **Performance / safety** — the heap dump freezes the target JVM for the duration of the dump.
  Trigger only on pods you are actively investigating, and typically one at a time.
- **Duration** — large heaps take time (e.g. a ~91 MB gzipped dump ≈ 2 minutes to stream). The live
  byte counter reflects progress.
- **PID** — the dump targets the current JVM via `ProcessHandle.current().pid()` (works both locally
  and on CCv2, where it is `1`).
- **`jcmd` self-attach** — attaching to one's own JVM needs no extra privileges; a hardened pod that
  blocks self-attach would surface as a FAILED message.
- **Cleanup** — the current run's FIFO node is deleted on completion; stale FIFO nodes from
  crashed/killed runs are swept before each new dump. Pod restart wipes the temp dir regardless.

---

## 11. Build & verify

```bash
# from hybris/bin/platform
ant build        # compiles the extension, regenerates the ClusterHeapDumpInfoResult bean,
                 # bundles JSP/JS into the hac web module
ant unittests -Dtestclasses.packages=com.sap.commerce.clusterhac.service.impl.DefaultHeapDumpServiceTest
```

End-to-end check (needs a running server + a valid Azure connection string):

1. Open `/hac` → **Cluster → Cluster Heap Dump**; confirm the pod picker lists nodes.
2. With Azure unconfigured, confirm the warning banner and that `/trigger` is rejected.
3. Configure the connection string (or rely on the hotfolder fallback), select a pod, **Trigger**.
4. Watch the status row go `IN_PROGRESS` with a climbing MB count → `DONE` with a
   `…/<host>_<user>_<ts>.hprof.gz` blob name.
5. Confirm the blob exists in the container and opens in a heap analyzer (e.g. Eclipse MAT), and that
   the targeted pod was **not** restarted.

---

## 12. Troubleshooting

| Symptom | Cause / fix |
| --- | --- |
| Tab shows "Azure storage is not configured" | Neither `clusterhac.heapdump.azure.connection-string` nor `azure.hotfolder.storage.account.connection-string` is set, or no container. |
| Status → FAILED with an Azure auth/network message | Bad connection string, container permissions, or no egress to the storage account. The message comes from the uploader (`[heapdump-uploader] … CLUSTERHAC_HEAPDUMP_RESULT ERR …`). |
| Log stops after "Starting heap-dump uploader process" and no `[heapdump-uploader]` lines | The child JVM failed to start or its classpath was incomplete. Check the `-cp <N chars>` length in the log (should be large) and the `[heapdump-uploader]` output. |
| `Illegal seek` from the uploader | FIFO opened with a seekable stream. Fixed by reading via `FileInputStream`. |
| Dump starts then hangs forever, no completion | In-process FIFO reader (safepoint deadlock). Fixed by the separate uploader process. |
| `NoSuchMethodException: gcHeapDump` | DiagnosticCommand MBean route (abandoned). The trigger now shells out to `jcmd`. |
