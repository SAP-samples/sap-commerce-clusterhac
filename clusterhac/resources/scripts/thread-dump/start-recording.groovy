import org.slf4j.LoggerFactory

def LOG = LoggerFactory.getLogger("start-recording")

if (!threadDumpCollector.isCollecting())
{
    threadDumpCollector.startCollecting($interval)
    LOG.info("Start recording with $interval interval on: " + InetAddress.getLocalHost().getCanonicalHostName())
} else{
    LOG.info("Thread dump is already running on: " + InetAddress.getLocalHost().getCanonicalHostName())
}

