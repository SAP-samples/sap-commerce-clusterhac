import org.slf4j.LoggerFactory

def LOG = LoggerFactory.getLogger("stop-recording")
threadDumpCollector.stopCollecting()

LOG.info("Stop recording on: " + InetAddress.getLocalHost().getCanonicalHostName())