import org.slf4j.LoggerFactory

final def log = LoggerFactory.getLogger("stop-recording")

threadDumpCollector.stopCollecting()

log.info("Stop recording on: {}", InetAddress.localHost.canonicalHostName)
