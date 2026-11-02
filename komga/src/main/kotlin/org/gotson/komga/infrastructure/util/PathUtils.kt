package org.gotson.komga.infrastructure.util

import java.nio.file.FileSystems
import java.nio.file.Path

/** False for paths served by another java.nio provider (S3), which cannot be turned into a [java.io.File]. */
fun Path.isOnDefaultFileSystem(): Boolean = fileSystem == FileSystems.getDefault()
