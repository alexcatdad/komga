package org.gotson.komga.infrastructure.metadata.archive

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.gotson.komga.domain.model.BookWithMedia
import org.gotson.komga.domain.model.Media
import org.gotson.komga.domain.model.makeBook
import org.gotson.komga.domain.model.makeSeries
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Instant
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class ArchiveSidecarProviderTest {
  private val provider = ArchiveSidecarProvider(ObjectMapper())

  @TempDir
  lateinit var root: Path

  private fun write(
    path: Path,
    content: String,
    modified: String = "2026-01-01T00:00:00Z",
  ): Path {
    path.parent.createDirectories()
    path.writeText(content)
    Files.setLastModifiedTime(path, FileTime.from(Instant.parse(modified)))
    return path
  }

  @Test
  fun `given episode document next to book when getting book metadata then title number and link are returned`() {
    // given
    val episode = root.resolve("series/1/episodes/10")
    val book = write(episode.resolve("aaa.epub"), "")
    // a novel's captured original sits in the same folder and must be ignored
    write(episode.resolve("bbb.json"), """{"version":3,"kind":"novel","episodeId":"10","title":"Original","position":1}""", "2026-03-01T00:00:00Z")
    write(episode.resolve("ccc.json"), """{"source":"Tapas","id":"10","seriesId":"1","title":"Chapter Two","url":"https://tapas.io/episode/10","position":2}""")

    // when
    val patch = provider.getBookMetadataFromBook(BookWithMedia(makeBook("aaa", url = book.toUri().toURL()), Media()))

    // then
    assertThat(patch).isNotNull
    with(patch!!) {
      assertThat(title).isEqualTo("Chapter Two")
      assertThat(number).isEqualTo("2")
      assertThat(numberSort).isEqualTo(2F)
      assertThat(links!!.single().label).isEqualTo("Tapas")
      assertThat(links.single().url).isEqualTo(URI("https://tapas.io/episode/10"))
    }
  }

  @Test
  fun `given several episode documents when getting book metadata then the newest one wins`() {
    // given
    val episode = root.resolve("series/1/episodes/11")
    val book = write(episode.resolve("aaa.cbz"), "")
    write(episode.resolve("old.json"), """{"id":"11","title":"Old title","position":1.5}""", "2026-01-01T00:00:00Z")
    write(episode.resolve("new.json"), """{"id":"11","title":"New title","position":1.5}""", "2026-02-01T00:00:00Z")

    // when
    val patch = provider.getBookMetadataFromBook(BookWithMedia(makeBook("aaa", url = book.toUri().toURL()), Media()))

    // then
    assertThat(patch!!.title).isEqualTo("New title")
    assertThat(patch.number).isEqualTo("1.5")
  }

  @Test
  fun `given no episode document when getting book metadata then null is returned`() {
    // given
    val book = write(root.resolve("series/2/book.cbz"), "")
    write(root.resolve("series/2/notes.json"), """{"something":"else"}""")

    // when
    val patch = provider.getBookMetadataFromBook(BookWithMedia(makeBook("book", url = book.toUri().toURL()), Media()))

    // then
    assertThat(patch).isNull()
  }

  @Test
  fun `given series document in metadata folder when getting series metadata then title and summary are returned`() {
    // given
    val seriesFolder = root.resolve("series/3")
    write(seriesFolder.resolve("metadata/aaa.json"), """{"id":"3","title":"My Series","url":"https://tapas.io/series/3","description":"About it","source":"Tapas"}""")

    // when
    val patch = provider.getSeriesMetadata(makeSeries("3", url = seriesFolder.toUri().toURL()))

    // then
    assertThat(patch).isNotNull
    assertThat(patch!!.title).isEqualTo("My Series")
    assertThat(patch.titleSort).isEqualTo("My Series")
    assertThat(patch.summary).isEqualTo("About it")
    assertThat(patch.publisher).isEqualTo("Tapas")
  }

  @Test
  fun `given no metadata folder when getting series metadata then null is returned`() {
    // given
    val seriesFolder = root.resolve("series/4").createDirectories()

    // when
    val patch = provider.getSeriesMetadata(makeSeries("4", url = seriesFolder.toUri().toURL()))

    // then
    assertThat(patch).isNull()
  }
}
