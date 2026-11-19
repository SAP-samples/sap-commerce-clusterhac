import de.hybris.platform.cmsfacades.data.MediaData
import de.hybris.platform.cmsfacades.dto.MediaFileDto
import de.hybris.platform.cmsfacades.exception.ValidationException
import de.hybris.platform.cmsfacades.media.MediaFacade
import de.hybris.platform.hac.facade.ThreadDumpCollector
import org.apache.commons.io.FilenameUtils
import org.slf4j.LoggerFactory

import java.nio.file.Files
import java.nio.file.Path

final def log = LoggerFactory.getLogger("save-media")

// Bindings provided by the script executor
final ThreadDumpCollector collector = threadDumpCollector
final MediaFacade facade = mediaFacade

final Path file = collector.result
final String host = InetAddress.localHost.canonicalHostName
final String fileName = FilenameUtils.normalize("thread_dump_${host}_${file.fileName}")
final long fileSize = Files.size(file)

log.info("Copy {} ({} bytes) to media: {}", file, fileSize, fileName)

final MediaData mediaData = new MediaData().with {
    code = fileName
    catalogId = "Default"
    catalogVersion = "Staged"
    description = "Thread dump"
    altText = "Thread dump"
    it
}

final MediaFileDto mediaFile = new MediaFileDto().with {
    mime = "application/zip"
    name = "${fileName}.zip"
    it
}

file.toFile().withInputStream { inputStream ->
    mediaFile.inputStream = inputStream
    mediaFile.size = fileSize
    try {
        facade.addMedia(mediaData, mediaFile)
        log.info("Saved packed to: {}", fileName)
    } catch (ValidationException ve) {
        log.error("Thread dump validation exceptions: {}", ve.message)
    }
}
