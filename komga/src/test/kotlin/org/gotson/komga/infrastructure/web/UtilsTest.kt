package org.gotson.komga.infrastructure.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.URL
import java.nio.file.Paths
import kotlin.io.path.pathString
import kotlin.io.path.toPath

class UtilsTest {
  @Test
  fun `given local path when converting to url and back then the path is kept`() {
    val path = Paths.get("/books/comics").toAbsolutePath().pathString

    val url = filePathToUrl(path)

    assertThat(url.protocol).isEqualTo("file")
    assertThat(url.toFilePath()).isEqualTo(path)
  }

  @Test
  fun `given s3x uri when converting to url and back then the uri is kept`() {
    val uri = "s3x://rustfs.local:9000/archive/tapas/series/"

    val url = filePathToUrl(uri)

    assertThat(url.protocol).isEqualTo("s3x")
    assertThat(url.toFilePath()).isEqualTo(uri)
  }

  @Test
  fun `given s3x url when resolving its path then the s3 nio provider serves it`() {
    val path = URL("s3x://rustfs.local:9000/archive/tapas/series/1/book.cbz").toURI().toPath()

    assertThat(path.fileSystem.provider().scheme).isEqualTo("s3x")
    assertThat(path.fileName.toString()).isEqualTo("book.cbz")
  }
}
