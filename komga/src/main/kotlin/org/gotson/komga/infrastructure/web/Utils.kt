package org.gotson.komga.infrastructure.web

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.net.URI
import java.net.URL
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import kotlin.io.path.pathString
import kotlin.io.path.toPath

private val remoteScheme = Regex("^(?!file:)[a-zA-Z][a-zA-Z0-9+.-]*://")

/** Local files show as a path; remote filesystems (s3://, s3x://) keep their URI. */
fun URL.toFilePath(): String = if (protocol == "file") this.toURI().toPath().pathString else this.toString()

fun filePathToUrl(filePath: String): URL =
  if (remoteScheme.containsMatchIn(filePath))
    URI(filePath).toURL()
  else
    Paths.get(filePath).toUri().toURL()

fun ResponseEntity.BodyBuilder.setCachePrivate() = this.cacheControl(cachePrivate)

val cachePrivate =
  CacheControl
    .maxAge(0, TimeUnit.SECONDS)
    .cachePrivate()
    .mustRevalidate()

fun getMediaTypeOrDefault(mediaTypeString: String?): MediaType {
  mediaTypeString?.let {
    try {
      return MediaType.parseMediaType(mediaTypeString)
    } catch (_: Exception) {
    }
  }
  return MediaType.APPLICATION_OCTET_STREAM
}

fun getCurrentRequest(): HttpServletRequest = (RequestContextHolder.getRequestAttributes() as ServletRequestAttributes?)?.request ?: throw IllegalStateException("Could not get current request")
