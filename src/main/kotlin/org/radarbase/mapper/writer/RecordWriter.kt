package org.radarbase.mapper.writer

import org.radarbase.mapper.source.MappedRecord
import java.nio.file.Path

/**
 * Writes a list of [MappedRecord] objects to a destination file.
 *
 * Implementations serialise records into a specific output format (ODM XML, CSV, etc.).
 * All records in the list are assumed to belong to the same file.
 */
interface RecordWriter {
    fun write(records: List<MappedRecord>, path: Path)
}
