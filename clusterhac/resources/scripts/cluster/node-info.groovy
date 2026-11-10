import java.lang.management.ManagementFactory
import java.net.InetAddress
import javax.management.ObjectName

// Collects a compact per-node JVM + pod-health summary for the Cluster status / overview.
// Runs on each cluster node via the ClusterView event pipeline. Low-load: in-JVM MBean reads
// only (no thread dumps, no DB queries). Emits self-describing key=value pairs separated by
// tabs, so new metrics can be added without breaking the parser:
//   ip=..\theapUsedMb=..\theapMaxMb=..\tuptime=..\tgc=..\tthreads=..\tcpu=..\tnonHeapMb=..\tdbPool=..

def pairs = [:]

// --- Host ---
try { pairs.ip = InetAddress.getLocalHost().getHostAddress() } catch (Exception e) { pairs.ip = "n/a" }

// --- Heap ---
def heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage()
pairs.heapUsedMb = (heap.getUsed() / (1024L * 1024L)) as long
pairs.heapMaxMb = heap.getMax() > 0 ? (heap.getMax() / (1024L * 1024L)) as long : -1L

// --- Non-heap / Metaspace ---
try {
    def nh = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage()
    pairs.nonHeapMb = (nh.getUsed() / (1024L * 1024L)) as long
} catch (Exception e) { pairs.nonHeapMb = -1L }

// --- Uptime ---
long uptimeSec = ManagementFactory.getRuntimeMXBean().getUptime() / 1000L
pairs.uptime = "${uptimeSec / 86400L as long}d ${(uptimeSec % 86400L) / 3600L as long}h ${(uptimeSec % 3600L) / 60L as long}m"

// --- GC (count / total time; plus the most-recent pause duration for the health check) ---
long gcCount = 0L, gcTimeMs = 0L, lastGcMs = -1L
for (gcBean in ManagementFactory.getGarbageCollectorMXBeans()) {
    if (gcBean.getCollectionCount() > 0) { gcCount += gcBean.getCollectionCount() }
    if (gcBean.getCollectionTime() > 0) { gcTimeMs += gcBean.getCollectionTime() }
    // com.sun.management extension: duration of this collector's LAST GC (recent pause, not
    // a since-boot average). Best-effort — the interface may not be present on every JVM.
    try {
        def info = gcBean.getLastGcInfo()
        if (info != null && info.getDuration() > lastGcMs) { lastGcMs = info.getDuration() }
    } catch (Throwable ignored) {}
}
pairs.gc = "${gcCount} / ${gcTimeMs}ms"
pairs.lastGcMs = lastGcMs      // raw, for the recent-GC-pause health check (-1 = unknown)

// --- Threads (live / peak / daemon) ---
try {
    def tb = ManagementFactory.getThreadMXBean()
    pairs.threads = "${tb.getThreadCount()} / ${tb.getPeakThreadCount()} / ${tb.getDaemonThreadCount()}"
} catch (Exception e) { pairs.threads = "n/a" }

// --- CPU (process load %, system load average) ---
try {
    def os = ManagementFactory.getOperatingSystemMXBean()
    def procLoad = -1.0d
    try { procLoad = os.getProcessCpuLoad() } catch (Exception ignored) {}
    if (procLoad != null && procLoad >= 0) {
        int cpuPct = Math.round(procLoad * 100) as int
        pairs.cpuPct = cpuPct                    // raw, for the CPU health check
        String cpuStr = "${cpuPct}%"
        double loadAvg = os.getSystemLoadAverage()
        String la = loadAvg >= 0 ? String.format("%.2f", loadAvg) : "n/a"
        pairs.cpu = "${cpuStr} (load ${la})"
    } else {
        pairs.cpuPct = -1
        double loadAvg = os.getSystemLoadAverage()
        String la = loadAvg >= 0 ? String.format("%.2f", loadAvg) : "n/a"
        pairs.cpu = "n/a (load ${la})"
    }
} catch (Exception e) { pairs.cpu = "n/a"; pairs.cpuPct = -1 }

// --- DB connection pool (commons-pool2 GenericObjectPool; best-effort) ---
// NOTE: deliberately NOT the hybris:...datasource=DataSources MBean — reading it touches
// tenant-scoped code that clears the current thread's tenant, which breaks the subsequent
// event publish (getCurrentTenantNoFallback NPE). The plain commons-pool2 MBean is pure JMX
// and tenant-neutral. This platform uses commons-pool2 (NOT Hikari).
try {
    def mbs = ManagementFactory.getPlatformMBeanServer()
    // Prefer the master DB pool; fall back to any GenericObjectPool.
    def poolName = mbs.queryNames(new ObjectName("org.apache.commons.pool2:type=GenericObjectPool,name=master-master"), null).find { true }
    if (poolName == null) {
        poolName = mbs.queryNames(new ObjectName("org.apache.commons.pool2:type=GenericObjectPool,*"), null).find { true }
    }
    if (poolName != null) {
        def active = mbs.getAttribute(poolName, "NumActive")
        def idle = mbs.getAttribute(poolName, "NumIdle")
        def maxTotal = mbs.getAttribute(poolName, "MaxTotal")
        pairs.dbPool = "${active} active / ${idle} idle / ${maxTotal} max"
        pairs.poolActive = active   // raw, for the pool-saturation health check
        pairs.poolMax = maxTotal
    } else {
        pairs.dbPool = "n/a"
        pairs.poolActive = -1
        pairs.poolMax = -1
    }
} catch (Exception e) { pairs.dbPool = "n/a"; pairs.poolActive = -1; pairs.poolMax = -1 }

return pairs.collect { k, v -> "${k}=${v}" }.join("\t")
