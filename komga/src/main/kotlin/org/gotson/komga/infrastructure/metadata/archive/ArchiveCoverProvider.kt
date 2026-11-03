package org.gotson.komga.infrastructure.metadata.archive

import io.github.oshai.kotlinlogging.KotlinLogging
import org.gotson.komga.domain.model.Dimension
import org.gotson.komga.domain.model.Series
import org.gotson.komga.domain.model.ThumbnailSeries
import org.gotson.komga.infrastructure.image.ImageAnalyzer
import org.gotson.komga.infrastructure.mediacontainer.ContentDetector
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.fileSize
import kotlin.io.path.inputStream

private val logger = KotlinLogging.logger {}

/**
 * The poster an archive keeps for a series: the series document's `poster` is the image's key from the root of the
 * bucket (or file system), such as `tapas/blobs/ab/<sha>.jpg`. It is read in place, like a local artwork file.
 */
@Service
class ArchiveCoverProvider(
  private val sidecarProvider: ArchiveSidecarProvider,
  private val contentDetector: ContentDetector,
  private val imageAnalyzer: ImageAnalyzer,
) {
  fun getSeriesCover(series: Series): ThumbnailSeries? {
    val key = sidecarProvider.getSeriesSource(series)?.poster ?: return null
    return try {
      val path = posterPath(series.path, key) ?: return null
      if (!Files.isRegularFile(path)) return null.also { logger.info { "Poster of series $series is missing: $path" } }
      val mediaType = contentDetector.detectMediaType(path)
      if (!contentDetector.isImage(mediaType)) return null
      ThumbnailSeries(
        url = path.toUri().toURL(),
        seriesId = series.id,
        type = ThumbnailSeries.Type.SIDECAR,
        fileSize = path.fileSize(),
        mediaType = mediaType,
        dimension = path.inputStream().use { imageAnalyzer.getDimension(it) } ?: Dimension(0, 0),
      )
    } catch (e: Exception) {
      logger.warn(e) { "Could not read the poster of series $series: $key" }
      null
    }
  }

  companion object {
    fun posterPath(
      seriesPath: Path,
      key: String,
    ): Path? {
      if (key.isBlank() || key.split('/').any { it == ".." }) return null
      return seriesPath.root?.resolve(key.trimStart('/'))
    }
  }
}
