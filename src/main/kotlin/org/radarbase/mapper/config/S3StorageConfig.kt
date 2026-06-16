package org.radarbase.mapper.config

import com.fasterxml.jackson.annotation.JsonProperty
import io.minio.MinioClient

/**
 * S3 / MinIO connection settings.
 *
 * ```yaml
 * s3:
 *   endpoint: https://minio.example.com
 *   bucket: radar-output
 *   access_token: KEY
 *   secret_key: SECRET
 * ```
 *
 * When [accessToken] or [secretKey] are blank, the client falls back to the
 * EC2 instance-profile / IAM credentials provider (useful on AWS).
 */
data class S3StorageConfig(
    val endpoint: String,
    val bucket: String,
    @JsonProperty("access_token") val accessToken: String? = null,
    @JsonProperty("secret_key") val secretKey: String? = null,
) {
    fun createClient(): MinioClient = MinioClient.builder()
        .endpoint(endpoint)
        .apply {
            if (!accessToken.isNullOrBlank() && !secretKey.isNullOrBlank()) {
                credentials(accessToken, secretKey)
            }
        }
        .build()
}
