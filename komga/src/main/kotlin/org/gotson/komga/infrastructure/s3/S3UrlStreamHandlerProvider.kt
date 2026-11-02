package org.gotson.komga.infrastructure.s3

import java.io.IOException
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
import java.net.spi.URLStreamHandlerProvider

/**
 * Lets [java.net.URL] parse `s3://` and `s3x://`, so books and series stored in S3 can be persisted as URLs.
 * Content is always read through the java.nio provider (`url.toURI().toPath()`), never through a [URLConnection].
 *
 * Registered in `META-INF/services/java.net.spi.URLStreamHandlerProvider`.
 */
class S3UrlStreamHandlerProvider : URLStreamHandlerProvider() {
  override fun createURLStreamHandler(protocol: String): URLStreamHandler? = if (protocol in SCHEMES) S3UrlStreamHandler else null

  private object S3UrlStreamHandler : URLStreamHandler() {
    override fun openConnection(u: URL): URLConnection = throw IOException("S3 URLs are read through java.nio, not URLConnection: $u")
  }

  companion object {
    val SCHEMES = setOf("s3", "s3x")
  }
}
