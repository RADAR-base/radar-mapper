package org.radarbase.mapper.storage

import io.minio.BucketExistsArgs
import io.minio.GetObjectArgs
import io.minio.ListObjectsArgs
import io.minio.MakeBucketArgs
import io.minio.MinioClient
import io.minio.StatObjectArgs
import io.minio.UploadObjectArgs
import io.minio.errors.ErrorResponseException
import org.radarbase.mapper.config.S3StorageConfig
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path

/**
 * [StorageService] backed by an S3-compatible object store (MinIO, AWS S3, etc.).
 *
 * Files are read by streaming directly from S3. Writes are done by uploading a
 * local temp file then deleting it, since S3 requires the content length upfront.
 *
 * The bucket is created automatically on the first [store] call if it does not exist.
 */
class S3StorageService(config: S3StorageConfig) : StorageService {

    private val client: MinioClient = config.createClient()
    private val bucket: String = config.bucket
    private var bucketEnsured = false

    init {
        logger.info("S3 storage configured: endpoint={}, bucket={}", config.endpoint, bucket)
    }

    override fun listFiles(root: String, suffix: String): List<String> {
        val prefix = root.trimEnd('/') + "/"
        val request = ListObjectsArgs.builder()
            .bucket(bucket)
            .prefix(prefix)
            .recursive(true)
            .build()

        return client.listObjects(request)
            .map { it.get().objectName() }
            .filter { it.endsWith(suffix) }
    }

    override fun exists(path: String): Boolean = try {
        client.statObject(StatObjectArgs.builder().bucket(bucket).`object`(path).build())
        true
    } catch (e: ErrorResponseException) {
        if (e.errorResponse().code() in setOf("NoSuchKey", "NoSuchBucket")) false else throw e
    }

    override fun newInputStream(path: String): InputStream {
        val request = GetObjectArgs.builder()
            .bucket(bucket)
            .`object`(path)
            .build()
        return client.getObject(request)
    }

    override fun store(localPath: Path, remotePath: String) {
        ensureBucket()
        val request = UploadObjectArgs.builder()
            .bucket(bucket)
            .`object`(remotePath)
            .filename(localPath.toAbsolutePath().toString())
            .build()
        client.uploadObject(request)
        Files.deleteIfExists(localPath)
        logger.debug("Uploaded {} → s3://{}/{}", localPath, bucket, remotePath)
    }

    override fun ensureOutputDirectory(path: String) {
        ensureBucket()
    }

    private fun ensureBucket() {
        if (bucketEnsured) return
        val exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())
        if (!exists) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build())
            logger.info("Created S3 bucket '{}'", bucket)
        }
        bucketEnsured = true
    }

    override fun close() = Unit

    companion object {
        private val logger = LoggerFactory.getLogger(S3StorageService::class.java)
    }
}
