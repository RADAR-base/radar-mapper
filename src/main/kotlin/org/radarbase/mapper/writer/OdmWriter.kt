package org.radarbase.mapper.writer

import org.radarbase.mapper.source.MappedRecord
import java.nio.file.Path
import javax.xml.stream.XMLOutputFactory
import javax.xml.stream.XMLStreamWriter
import kotlin.io.path.bufferedWriter

/**
 * Writes a list of [MappedRecord] objects as a CDISC ODM v1.3 XML file.
 *
 * Reads the following keys from [MappedRecord.fields]:
 * `StudyOID`, `MetaDataVersionOID`, `SubjectKey`, `StudyEventOID`,
 * `StudyEventRepeatKey` (optional), `FormOID`, `ItemGroupOID`, `IGRepeatKey`.
 *
 * Missing optional fields are silently omitted; missing required fields throw [IllegalArgumentException].
 */
class OdmWriter : RecordWriter {

    override fun write(records: List<MappedRecord>, path: Path) {
        require(records.isNotEmpty()) { "Cannot write empty record list to $path" }
        val first = records.first()

        path.bufferedWriter().use { writer ->
            val xml = XML_FACTORY.createXMLStreamWriter(writer)
            xml.writeStartDocument("UTF-8", "1.0")
            xml.writeCharacters("\n")
            xml.writeStartElement("ODM")
            xml.writeDefaultNamespace(ODM_NAMESPACE)
            xml.writeAttribute("FileType", "Transactional")
            xml.writeCharacters("\n    ")
            xml.writeStartElement("ClinicalData")
            xml.writeAttribute("StudyOID", first.require("StudyOID"))
            xml.writeAttribute("MetaDataVersionOID", first.require("MetaDataVersionOID"))

            for (record in records) {
                xml.writeRecord(record)
            }

            xml.writeCharacters("\n    ")
            xml.writeEndElement() // </ClinicalData>
            xml.writeCharacters("\n")
            xml.writeEndElement() // </ODM>
            xml.writeEndDocument()
            xml.flush()
        }
    }

    private fun XMLStreamWriter.writeRecord(record: MappedRecord) {
        writeCharacters("\n        ")
        writeStartElement("SubjectData")
        writeAttribute("SubjectKey", record.require("SubjectKey"))

        writeCharacters("\n            ")
        writeStartElement("StudyEventData")
        writeAttribute("StudyEventOID", record.require("StudyEventOID"))
        record.fields["StudyEventRepeatKey"]?.let { writeAttribute("StudyEventRepeatKey", it) }

        writeCharacters("\n                ")
        writeStartElement("FormData")
        writeAttribute("FormOID", record.require("FormOID"))

        writeCharacters("\n                    ")
        writeStartElement("ItemGroupData")
        writeAttribute("ItemGroupOID", record.require("ItemGroupOID"))
        writeAttribute("IGRepeatKey", record.fields["IGRepeatKey"] ?: "1")

        for (item in record.items) {
            writeCharacters("\n                        ")
            writeEmptyElement("ItemData")
            writeAttribute("ItemOID", item.id)
            writeAttribute("Value", item.value)
        }

        writeCharacters("\n                    ")
        writeEndElement() // </ItemGroupData>
        writeCharacters("\n                ")
        writeEndElement() // </FormData>
        writeCharacters("\n            ")
        writeEndElement() // </StudyEventData>
        writeCharacters("\n        ")
        writeEndElement() // </SubjectData>
    }

    private fun MappedRecord.require(field: String): String =
        fields[field] ?: throw IllegalArgumentException("Required ODM field '$field' missing from record fields")

    private companion object {
        const val ODM_NAMESPACE = "http://www.cdisc.org/ns/odm/v1.3"
        val XML_FACTORY: XMLOutputFactory = XMLOutputFactory.newInstance()
    }
}
