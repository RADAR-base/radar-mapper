package org.radarbase.mapper.source

import org.slf4j.LoggerFactory
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
 *
 * @param excludeValuePrefixes Item values starting with any of these prefixes are
 *   dropped during parsing to avoid memory pressure from large embedded payloads.
 */
class OdmSourceReader(
    private val excludeValuePrefixes: List<String> = listOf("data:"),
) : SourceReader {

    fun readAll(sourcePath: Path): List<MappedRecord> =
        Files.walk(sourcePath).use { stream ->
            stream.filter { it.name.endsWith(".xml") }
                .toList()
                .flatMap { readFile(it) }
        }

    override fun readFile(path: Path): List<MappedRecord> =
        path.inputStream().use { readStream(it) }

    override fun readStream(input: InputStream): List<MappedRecord> {
        // Source files from radar-output-restructure may contain multiple concatenated
        // XML documents. StAX rejects a second <?xml?> declaration, so we strip them
        // and wrap everything in a synthetic root to produce a single well-formed document.
        val raw = input.readAllBytes().toString(Charsets.UTF_8)
        val cleaned = XML_DECL_PATTERN.replace(raw, "")
        val wrapped = "<_root>$cleaned</_root>"
        return parseDocument(wrapped.byteInputStream(Charsets.UTF_8))
    }

    private fun parseDocument(input: InputStream): List<MappedRecord> {
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
                        val value = reader.attr("Value").orEmpty()
                        if (excludeValuePrefixes.none { value.startsWith(it) }) {
                            items += MappedItem(id = id, value = value)
                        } else {
                            logger.debug("Skipping item '{}': value matches excluded prefix", id)
                        }
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
        private val logger = LoggerFactory.getLogger(OdmSourceReader::class.java)
        private val XML_FACTORY: XMLInputFactory = XMLInputFactory.newInstance().apply {
            // Harden against XXE: source files come from S3 (external storage),
            // so a crafted ODM with <!ENTITY> could leak files or enable SSRF.
            setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
            setProperty(XMLInputFactory.SUPPORT_DTD, false)
        }
        private val XML_DECL_PATTERN = Regex("""<\?xml\s[^?]*\?>\s*""")
    }
}
