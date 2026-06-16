package org.radarbase.mapper.source

import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import kotlin.io.path.inputStream
import kotlin.io.path.name

/**
 * Reads CDISC ODM v1.3 XML files produced by Stage 1 (`radar-output-restructure`)
 * and converts them into [MappedRecord] lists.
 *
 * Each `<ItemGroupData>` element becomes one [MappedRecord]. The following fields
 * are populated in [MappedRecord.fields]:
 * `StudyOID`, `MetaDataVersionOID`, `SubjectKey`, `StudyEventOID`,
 * `StudyEventRepeatKey` (omitted when absent), `FormOID`, `ItemGroupOID`, `IGRepeatKey`.
 */
class OdmSourceReader : SourceReader {

    fun readAll(sourcePath: Path): List<MappedRecord> =
        Files.walk(sourcePath)
            .filter { it.name.endsWith(".xml") }
            .toList()
            .flatMap { readFile(it) }

    override fun readFile(path: Path): List<MappedRecord> =
        path.inputStream().use { readStream(it) }

    override fun readStream(input: InputStream): List<MappedRecord> {
        val records = mutableListOf<MappedRecord>()
        val reader = XML_FACTORY.createXMLStreamReader(input)

        val current = mutableMapOf<String, String>()
        val items = mutableListOf<MappedItem>()

        while (reader.hasNext()) {
            when (reader.next()) {
                XMLStreamConstants.START_ELEMENT -> when (reader.localName) {
                    "ClinicalData" -> {
                        reader.attr("StudyOID")?.let { current["StudyOID"] = it }
                        reader.attr("MetaDataVersionOID")?.let { current["MetaDataVersionOID"] = it }
                    }
                    "SubjectData" -> {
                        reader.attr("SubjectKey")?.let { current["SubjectKey"] = it }
                    }
                    "StudyEventData" -> {
                        reader.attr("StudyEventOID")?.let { current["StudyEventOID"] = it }
                        val repeatKey = reader.attr("StudyEventRepeatKey")
                        if (repeatKey != null) {
                            current["StudyEventRepeatKey"] = repeatKey
                        } else {
                            current.remove("StudyEventRepeatKey")
                        }
                    }
                    "FormData" -> {
                        reader.attr("FormOID")?.let { current["FormOID"] = it }
                    }
                    "ItemGroupData" -> {
                        reader.attr("ItemGroupOID")?.let { current["ItemGroupOID"] = it }
                        current["IGRepeatKey"] = reader.attr("IGRepeatKey") ?: "1"
                        items.clear()
                    }
                    "ItemData" -> reader.attr("ItemOID")?.let { id ->
                        items += MappedItem(id = id, value = reader.attr("Value").orEmpty())
                    }
                }
                XMLStreamConstants.END_ELEMENT -> {
                    if (reader.localName == "ItemGroupData") {
                        records += MappedRecord(
                            fields = current.toMap(),
                            items = items.toList(),
                        )
                    }
                }
            }
        }

        return records
    }

    private fun javax.xml.stream.XMLStreamReader.attr(name: String): String? =
        getAttributeValue(null, name)?.takeIf { it.isNotBlank() }

    private companion object {
        val XML_FACTORY: XMLInputFactory = XMLInputFactory.newInstance()
    }
}
