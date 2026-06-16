package org.radarbase.mapper.storage

import java.io.Closeable
import java.io.InputStream
import java.nio.file.Path

/**
 * Abstracts the storage backend used for source and destination files.
 *
 * Implementations hide whether files live on a local filesystem or in an S3 bucket.
 * The pipeline uses this interface for all file I/O so that the same [SourceReader]
 * and [RecordWriter] logic works in both environments.
 */
interface StorageService : Closeable {

    /**
     * List all object/file paths under [root] whose names end with [suffix].
     * Returns full path strings that can be passed back to [newInputStream] and [store].
     */
    fun listFiles(root: String, suffix: String): List<String>

    /** Returns true if [path] exists in this storage backend. */
    fun exists(path: String): Boolean

    /** Open [path] for reading. The caller is responsible for closing the stream. */
    fun newInputStream(path: String): InputStream

    /**
     * Persist [localPath] to [remotePath] and delete [localPath] on success.
     * For local storage this is an atomic move; for S3 this uploads then deletes the temp file.
     */
    fun store(localPath: Path, remotePath: String)

    /**
     * Ensure the destination directory or bucket prefix is ready to receive files.
     * No-op for S3 (bucket is created lazily on first [store]).
     */
    fun ensureOutputDirectory(path: String)
}
