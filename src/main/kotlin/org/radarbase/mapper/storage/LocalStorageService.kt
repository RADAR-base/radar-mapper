package org.radarbase.mapper.storage

import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlin.io.path.inputStream
import kotlin.io.path.moveTo
import kotlin.io.path.name

/** [StorageService] backed by the local filesystem. */
class LocalStorageService : StorageService {

    override fun listFiles(root: String, suffix: String): List<String> =
        Files.walk(Path.of(root))
            .filter { it.name.endsWith(suffix) }
            .map { it.toString() }
            .toList()

    override fun exists(path: String): Boolean = Path.of(path).toFile().exists()

    override fun newInputStream(path: String): InputStream =
        Path.of(path).inputStream()

    override fun store(localPath: Path, remotePath: String) {
        val dest = Path.of(remotePath)
        Files.createDirectories(dest.parent)
        try {
            localPath.moveTo(dest, REPLACE_EXISTING, ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            localPath.moveTo(dest, REPLACE_EXISTING)
        }
    }

    override fun ensureOutputDirectory(path: String) {
        Files.createDirectories(Path.of(path))
    }

    override fun close() = Unit
}
