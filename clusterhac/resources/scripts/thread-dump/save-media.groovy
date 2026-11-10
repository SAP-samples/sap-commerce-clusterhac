import de.hybris.platform.cmsfacades.data.MediaData
import de.hybris.platform.cmsfacades.dto.MediaFileDto
import de.hybris.platform.cmsfacades.media.MediaFacade
import de.hybris.platform.hac.facade.ThreadDumpCollector
import de.hybris.platform.cmsfacades.exception.ValidationException
import org.apache.commons.io.FilenameUtils
import org.slf4j.LoggerFactory

import java.nio.file.Path

def LOG = LoggerFactory.getLogger("save-media")

ThreadDumpCollector threadDumpCollector = threadDumpCollector

Path file = threadDumpCollector.getResult()

def host = InetAddress.getLocalHost().getCanonicalHostName()
def fileName = FilenameUtils.normalize("thread_dump_" + host + "_" + file.getFileName())

LOG.info("Copy " + file + " to media: " + fileName)

MediaFacade mediaFacade = mediaFacade

MediaData mediaData = new MediaData()
mediaData.setCode(fileName)
mediaData.setCatalogId("Default")
mediaData.setCatalogVersion("Staged")
mediaData.setDescription("Thread dump")
mediaData.setAltText("Thread dump")

MediaFileDto mediaFile = new MediaFileDto()
mediaFile.setMime("application/csv")
mediaFile.setName(fileName + ".zip")

file.toFile().withInputStream { inputStream ->
    mediaFile.setInputStream(inputStream)
    mediaFile.setSize(inputStream.available())
    try{
        mediaData = mediaFacade.addMedia(mediaData, mediaFile)
    }catch(ValidationException ve){
        LOG.error("Thread dump validation exceptions: " + ve.getMessage())
    }
    LOG.info("Saved packed to: " + fileName)
}