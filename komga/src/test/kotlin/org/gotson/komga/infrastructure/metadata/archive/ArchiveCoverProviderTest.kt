package org.gotson.komga.infrastructure.metadata.archive

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Paths

class ArchiveCoverProviderTest {
  @Test
  fun `given a poster key when resolving it then it is taken from the root of the series file system`() {
    val path = ArchiveCoverProvider.posterPath(Paths.get("/archive/tapas/series/1"), "tapas/blobs/ab/poster.jpg")

    assertThat(path).isEqualTo(Paths.get("/tapas/blobs/ab/poster.jpg"))
  }

  @Test
  fun `given a key that climbs out of the root when resolving it then null is returned`() {
    assertThat(ArchiveCoverProvider.posterPath(Paths.get("/archive/series/1"), "../etc/passwd")).isNull()
    assertThat(ArchiveCoverProvider.posterPath(Paths.get("/archive/series/1"), "")).isNull()
  }
}
