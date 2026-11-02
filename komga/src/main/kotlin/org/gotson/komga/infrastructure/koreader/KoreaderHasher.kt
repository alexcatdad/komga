package org.gotson.komga.infrastructure.koreader

import com.appmattus.crypto.Algorithm
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path

private val logger = KotlinLogging.logger {}

@Component
class KoreaderHasher {
  fun computeHash(path: Path): String {
    logger.debug { "Koreader hashing: $path" }

    return partialMd5(path)
  }

  /**
   * From https://github.com/koreader/koreader/blob/5bd3f3b42c95fd143d98f8fc9695d486fd92b7c8/frontend/util.lua#L1093-L1119
   */
  private fun partialMd5(path: Path): String {
    val step = 1024L
    val size = 1024
    val digest = Algorithm.MD5.createDigest()

    // a channel rather than a RandomAccessFile, so paths on non-default filesystems (S3) work too
    Files.newByteChannel(path).use { channel ->
      val buffer = ByteBuffer.allocate(size)
      (-1..10).forEach {
        channel.position(step shl (2 * it))
        buffer.clear()
        val size = channel.read(buffer)
        if (size > 0) digest.update(buffer.array(), 0, size)
      }
    }

    return digest.digest().toHexString()
  }
}
