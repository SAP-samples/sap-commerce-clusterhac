import de.hybris.platform.catalog.model.CatalogUnawareMediaModel
import de.hybris.platform.core.model.media.MediaModel
import de.hybris.platform.hac.facade.ThreadDumpCollector
import de.hybris.platform.servicelayer.media.MediaService
import de.hybris.platform.servicelayer.model.ModelService
import org.apache.commons.io.FilenameUtils
import org.slf4j.LoggerFactory

import java.nio.file.Files
import java.nio.file.Path

final def log = LoggerFactory.getLogger("save-media")

// Bindings resolve to Spring beans by id. threadDumpCollector is an OOTB HAC bean; modelService
// and mediaService are core servicelayer beans (always present) — no cmsfacades dependency.
final ThreadDumpCollector collector = threadDumpCollector
final ModelService ms = modelService
final MediaService mediaSvc = mediaService

final String host = InetAddress.localHost.canonicalHostName

// getResult() may return null, or throw DumpNotAvailableException when no dumps were captured
// (e.g. save clicked before any snapshot, or before stop-recording finished). Guard so the node
// does not fail silently with an NPE.
final Path file
try {
    file = collector.result
} catch (Exception e) {
    log.warn("No thread-dump available to save on {}: {}", host, e.message)
    return
}
if (file == null || !Files.exists(file)) {
    log.warn("Thread-dump result file missing on {} (nothing recorded?)", host)
    return
}

final String fileName = FilenameUtils.normalize("thread_dump_${host}_${file.fileName}")
final long fileSize = Files.size(file)

log.info("Copy {} ({} bytes) to media: {}", file, fileSize, fileName)

// Create a plain (catalog-less) Media. The thread-dump DAO searches {Media} by
// code LIKE 'thread_dump_%' with no catalog-version restriction, so no catalog version is set.
// CatalogUnawareMedia redeclares catalogVersion as optional, so no catalog version is required.
// It extends Media, so the thread-dump DAO's {Media} query (which includes subtypes) still finds it.
final MediaModel media = ms.create(CatalogUnawareMediaModel)
media.setCode(fileName)
media.setDescription("Thread dump")
media.setAltText("Thread dump")
ms.save(media)

file.toFile().withInputStream { inputStream ->
    // Persists the binary and sets original file name + mime in one call.
    mediaSvc.setStreamForMedia(media, inputStream, "${fileName}.zip", "application/zip")
}

log.info("Saved thread dump to media: {}", fileName)
