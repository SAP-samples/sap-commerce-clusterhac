import org.slf4j.LoggerFactory

// Triggers a heap dump on THIS node and streams it to Azure Blob Storage. The heavy lifting
// (FIFO + GC.heap_dump + AppendBlob streaming) lives in the injected heapDumpService so it stays
// testable; this script only kicks it off. dumpToAzure() returns immediately — the dump runs on a
// background thread and progress is reported back through the heap-dump status round-trip.
//
// Runs only on explicitly selected pods (never a node-group broadcast): a heap dump freezes the
// JVM, so it must be targeted.

def LOG = LoggerFactory.getLogger("heap-dump")

def host = InetAddress.getLocalHost().getCanonicalHostName()

if (!heapDumpService.isConfigured()) {
    LOG.warn("Heap dump requested on {} but Azure storage is not configured.", host)
    return "Azure storage not configured on " + host
}

LOG.info("Triggering heap dump on {} (triggered by '$triggeredBy') -> Azure", host)
heapDumpService.dumpToAzure('$triggeredBy')
return "Heap dump triggered on " + host
