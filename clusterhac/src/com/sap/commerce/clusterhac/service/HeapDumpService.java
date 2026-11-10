package com.sap.commerce.clusterhac.service;

/**
 * Captures a heap dump of the current node's JVM and streams it, without ever landing the full dump
 * on local disk, to an Azure Blob Storage container. Implementations target the local node only;
 * cluster targeting is handled by the caller (pod-level sub-selection via
 * {@link ClusterScriptExecutorService#executeOnNodes}).
 *
 * <p>On SAP Commerce Cloud a heap dump cannot be written to a file on the pod: the dump often does
 * not fit the local disk, and while it is written the JVM freezes, the liveness probe fails and the
 * pod is restarted mid-dump. Streaming the dump out through a named pipe to Azure avoids both.
 */
public interface HeapDumpService {

  /**
   * Current per-node heap-dump state, surfaced back to HAC through the status round-trip.
   */
  enum Status {
    IDLE, IN_PROGRESS, DONE, FAILED
  }

  /**
   * Immutable snapshot of this node's most recent (or in-flight) heap dump.
   */
  class HeapDumpState {

    private final Status status;
    private final String blobName;
    private final String blobUrl;
    private final long bytesStreamed;
    private final String startedAt;
    private final String finishedAt;
    private final String message;

    public HeapDumpState(Status status, String blobName, String blobUrl, long bytesStreamed,
        String startedAt, String finishedAt, String message) {
      this.status = status;
      this.blobName = blobName;
      this.blobUrl = blobUrl;
      this.bytesStreamed = bytesStreamed;
      this.startedAt = startedAt;
      this.finishedAt = finishedAt;
      this.message = message;
    }

    public static HeapDumpState idle() {
      return new HeapDumpState(Status.IDLE, null, null, 0L, null, null, null);
    }

    public Status getStatus() {
      return status;
    }

    public String getBlobName() {
      return blobName;
    }

    public String getBlobUrl() {
      return blobUrl;
    }

    public long getBytesStreamed() {
      return bytesStreamed;
    }

    public String getStartedAt() {
      return startedAt;
    }

    public String getFinishedAt() {
      return finishedAt;
    }

    public String getMessage() {
      return message;
    }
  }

  /**
   * Triggers a heap dump on the local node and streams it to Azure. Returns immediately: the dump
   * runs on a background thread so the calling (cluster-event) thread is never blocked by the JVM
   * freeze. Progress is observable via {@link #getState()}. A second call while one is already
   * {@link Status#IN_PROGRESS} is a no-op.
   *
   * @param triggeredBy the HAC user who triggered the dump, folded into the blob name
   *     ({@code <host>/<host>_<user>_<timestamp>}). May be {@code null}/blank.
   */
  void dumpToAzure(String triggeredBy);

  /**
   * @return the current heap-dump state for this node (never {@code null}).
   */
  HeapDumpState getState();

  /**
   * @return {@code true} when the Azure storage account name, key and container are all configured,
   *     so the UI can warn before a trigger that would otherwise fail.
   */
  boolean isConfigured();
}
