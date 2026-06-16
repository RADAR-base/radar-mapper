package org.radarbase.mapper.source

import java.io.InputStream
import java.nio.file.Path

/**
 * Reads records from a source in a specific format (ODM XML, CSV, etc.) into
 * the source-agnostic [MappedRecord] model.
 */
interface SourceReader {
    fun readFile(path: Path): List<MappedRecord>
    fun readStream(input: InputStream): List<MappedRecord>
}
