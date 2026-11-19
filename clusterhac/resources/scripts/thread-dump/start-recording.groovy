import org.slf4j.LoggerFactory

final def log = LoggerFactory.getLogger("start-recording")

final String host = InetAddress.localHost.canonicalHostName

if (threadDumpCollector.collecting) {
    log.info("Thread dump is already running on: {}", host)
} else {
    threadDumpCollector.startCollecting($interval)
    log.info("Start recording with {} interval on: {}", $interval, host)
}
