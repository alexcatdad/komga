package org.gotson.komga.infrastructure.metadata.archive

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import org.gotson.komga.domain.model.BookMetadataPatch
import org.gotson.komga.domain.model.BookMetadataPatchCapability
import org.gotson.komga.domain.model.BookWithMedia
import org.gotson.komga.domain.model.Library
import org.gotson.komga.domain.model.MetadataPatchTarget
import org.gotson.komga.domain.model.Series
import org.gotson.komga.domain.model.SeriesMetadata
import org.gotson.komga.domain.model.SeriesMetadataPatch
import org.gotson.komga.domain.model.WebLink
import org.gotson.komga.domain.persistence.BookRepository
import org.gotson.komga.infrastructure.mediacontainer.epub.getPackageFileContent
import org.gotson.komga.infrastructure.metadata.BookMetadataProvider
import org.gotson.komga.infrastructure.metadata.SeriesMetadataProvider
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import org.springframework.stereotype.Service
import java.net.URI
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.NotDirectoryException
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.inputStream
import kotlin.io.path.listDirectoryEntries

private val logger = KotlinLogging.logger {}

/**
 * Reads the JSON metadata that a content-addressed web-comic archive writes next to its files:
 * - book: an episode document (`id`, `title`, `position`, optional `url`, `source`) in the book's folder
 * - series: a series document (`title`, optional `description`, `url`, `source`, `poster`) in the series' `metadata/` folder;
 *   the series is read as a webtoon (vertical scroll). Without one, the title and source page come from the newest
 *   episode EPUB (`dc:title` holds the series name, `dc:source` the series page)
 *
 * Files are hash-named, so documents are recognised by their fields, and the newest one wins when there are several.
 * Other JSON in the same folders (such as a novel's captured original, which has a `kind`) is ignored.
 */
@Service
class ArchiveSidecarProvider(
  private val mapper: ObjectMapper,
  private val bookRepository: BookRepository,
) : BookMetadataProvider,
  SeriesMetadataProvider {
  override val capabilities =
    setOf(
      BookMetadataPatchCapability.TITLE,
      BookMetadataPatchCapability.NUMBER,
      BookMetadataPatchCapability.NUMBER_SORT,
      BookMetadataPatchCapability.LINKS,
    )

  override fun getBookMetadataFromBook(book: BookWithMedia): BookMetadataPatch? {
    val episode = newestDocument(book.book.path.parent) { it.isEpisode() } ?: return null
    val position = episode["position"].asDouble()
    val number = if (position % 1.0 == 0.0) position.toLong().toString() else position.toString()
    return BookMetadataPatch(
      title = episode["title"].asText(),
      number = number,
      numberSort = position.toFloat(),
      links = link(episode)?.let { listOf(it) },
    )
  }

  override fun getSeriesMetadata(series: Series): SeriesMetadataPatch? {
    val source = getSeriesSource(series) ?: return null
    return SeriesMetadataPatch(
      title = source.title,
      titleSort = source.title,
      status = null,
      summary = source.summary,
      // web comics are vertical strips; novels are opened by the EPUB reader, which ignores this
      readingDirection = SeriesMetadata.ReadingDirection.WEBTOON,
      publisher = source.publisher,
      ageRating = null,
      language = null,
      genres = null,
      totalBookCount = null,
      collections = emptySet(),
    )
  }

  /**
   * The series' title, summary, publisher, source page and poster, from its series document or else its newest episode EPUB.
   */
  fun getSeriesSource(series: Series): SeriesSource? {
    if (series.oneshot) return null
    newestDocument(series.path.resolve(METADATA_FOLDER)) { it.isSeries() }?.let { document ->
      return SeriesSource(
        title = document["title"].asText(),
        summary = document.text("description"),
        publisher = document.text("source"),
        url = document.text("url"),
        poster = document.text("poster"),
      )
    }
    val book =
      bookRepository
        .findAllBySeriesId(series.id)
        .filter { it.deletedDate == null && it.path.extension.equals("epub", ignoreCase = true) }
        .maxByOrNull { it.fileLastModified } ?: return null
    return try {
      val opf = getPackageFileContent(book.path)?.let { Jsoup.parse(it, "", Parser.xmlParser()) } ?: return null
      val title =
        opf
          .getElementsByTag("dc:title")
          .firstOrNull()
          ?.text()
          ?.ifBlank { null } ?: return null
      SeriesSource(
        title = title,
        summary = null,
        publisher = newestDocument(book.path.parent) { it.isEpisode() }?.text("source"),
        url =
          opf
            .getElementsByTag("dc:source")
            .firstOrNull()
            ?.text()
            ?.ifBlank { null },
        poster = null,
      )
    } catch (e: Exception) {
      logger.warn(e) { "Could not read the package document of ${book.path}" }
      null
    }
  }

  override fun shouldLibraryHandlePatch(
    library: Library,
    target: MetadataPatchTarget,
  ): Boolean =
    when (target) {
      MetadataPatchTarget.BOOK, MetadataPatchTarget.SERIES -> true
      else -> false
    }

  private fun newestDocument(
    folder: Path?,
    accept: (JsonNode) -> Boolean,
  ): JsonNode? {
    // no isDirectory() check: on S3 a folder is only a key prefix, which has no attributes of its own
    if (folder == null) return null
    return try {
      folder
        .listDirectoryEntries()
        .filter { it.extension.equals("json", ignoreCase = true) && Files.isRegularFile(it) }
        .sortedByDescending { it.getLastModifiedTime() }
        .firstNotNullOfOrNull { path ->
          try {
            path.inputStream().use { mapper.readTree(it) }?.takeIf(accept)
          } catch (e: Exception) {
            logger.debug(e) { "Not a readable JSON document: $path" }
            null
          }
        }
    } catch (_: NoSuchFileException) {
      null
    } catch (_: NotDirectoryException) {
      null
    } catch (e: Exception) {
      logger.warn(e) { "Could not read archive metadata in $folder" }
      null
    }
  }

  private fun JsonNode.isEpisode() = isObject && !has("kind") && this["id"]?.isTextual == true && this["title"]?.isTextual == true && this["position"]?.isNumber == true

  private fun JsonNode.isSeries() = isObject && !has("kind") && this["title"]?.isTextual == true && !has("position")

  private fun JsonNode.text(field: String) = this[field]?.takeIf { it.isTextual }?.asText()

  private fun link(document: JsonNode): WebLink? {
    val url = document["url"]?.takeIf { it.isTextual }?.asText() ?: return null
    return try {
      WebLink(document["source"]?.takeIf { it.isTextual }?.asText() ?: "Source", URI(url))
    } catch (_: Exception) {
      null
    }
  }

  data class SeriesSource(
    val title: String,
    val summary: String?,
    val publisher: String?,
    val url: String?,
    val poster: String?,
  )

  companion object {
    const val METADATA_FOLDER = "metadata"
  }
}
