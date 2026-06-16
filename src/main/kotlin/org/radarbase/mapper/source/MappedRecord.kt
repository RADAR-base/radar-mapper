package org.radarbase.mapper.source

/**
 * Source-agnostic intermediate representation of one enriched observation.
 *
 * [fields] is a flat string map whose keys are logical field names. Each source reader
 * populates the fields it has — writers read only the keys they need. There is no
 * fixed schema, so ODM and CSV source readers can coexist without forcing either into
 * the other's structure.
 *
 * ODM source fields: `SubjectKey`, `StudyOID`, `MetaDataVersionOID`, `StudyEventOID`,
 * `StudyEventRepeatKey`, `FormOID`, `ItemGroupOID`, `IGRepeatKey`.
 *
 * CSV source fields (future): `SubjectKey`, `StudyOID`, `StudyEventOID`.
 *
 * Enrichment results are applied by mutating specific keys before writing.
 */
data class MappedRecord(
    val fields: Map<String, String>,
    val items: List<MappedItem>,
)

data class MappedItem(
    val id: String,
    val value: String,
)
