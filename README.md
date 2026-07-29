# radar-mapper

RADAR-base radar-mapper. Reads ODM XML files produced by [radar-output-restructure](https://github.com/RADAR-base/radar-output-restructure), enriches subject identifiers and event names, and writes enriched ODM XML ready for downstream import (e.g. REDCap).

## Pipeline

```mermaid
flowchart TD
    S[(Source\nLocal / S3)] -->|StorageService\nlistFiles / newInputStream| READ

    subgraph READ[Read]
        SR["«interface»\nSourceReader\n---\nreadFile / readStream"]
        ODM["OdmSourceReader"]
        SR -.->|impl| ODM
    end

    READ -->|List&lt;MappedRecord&gt;| ENRICH

    subgraph ENRICH[Enrich — slots applied in order]
        EP["«interface»\nEnrichmentProvider\n---\nlookup(key): String?"]
        CSV["CsvEnrichmentProvider"]
        MP["ManagementPortalEnrichmentProvider"]
        EP -.->|impl| CSV
        EP -.->|impl| MP
    end

    ENRICH -->|lookup miss| MISS{on_missing}
    MISS -->|fail| ERR[Abort run]
    MISS -->|warn| RETRY[Skip record\nfile not written\nretried next run]
    MISS -->|skip| DROP[Drop record\nfile still written]

    ENRICH -->|all resolved| FILTER

    subgraph FILTER[Filter]
        FS["«interface»\nFilterStrategy\n---\napply(record): MappedRecord"]
        RF["RecordFilter"]
        FS -.->|impl| RF
    end

    FILTER -->|List&lt;MappedRecord&gt;| WRITE

    subgraph WRITE[Write]
        RW["«interface»\nRecordWriter\n---\nwrite(records, path)"]
        ODW["OdmWriter"]
        RW -.->|impl| ODW
    end

    WRITE -->|StorageService\nstore| D[(Destination\nLocal / S3)]

    subgraph STORAGE[StorageService]
        SS["«interface»\nStorageService\n---\nlistFiles / exists\nnewInputStream / store"]
        LS["LocalStorageService"]
        S3["S3StorageService"]
        SS -.->|impl| LS
        SS -.->|impl| S3
    end

    STORAGE -.- READ
    STORAGE -.- WRITE
```

`MapperPipeline` orchestrates the run. `StorageService` abstracts local and S3 I/O so all other components work against `MappedRecord` only.

## Configuration

Copy `src/main/resources/mapper.yml` and fill in your values:

```yaml
source:
  type: local          # or s3
  path: /data/odm/

enrichment:
  - name: record_id
    source_field: SubjectKey
    output_field: SubjectKey
    provider:
      type: management_portal
      url: https://radar-base.example.com/managementportal
      client_id: radar_mapper
      client_secret: secret
      project: MY-PROJECT
      subject_attribute: REDCapRecordId
    on_missing: warn

  - name: event_name
    source_fields: [StudyEventOID, StudyOID]
    output_field: StudyEventOID
    provider:
      path: /config/event-lookup.csv
      key_columns: [questionnaireName, projectId]
      value_column: eventName
    on_missing: warn

destination:
  type: local          # or s3
  path: /data/output/
```

### Enrichment slots

| Field | Description |
|---|---|
| `name` | Slot identifier; result is stored under this name and can be referenced by later slots |
| `source_field` | Single record field used as the lookup key |
| `source_fields` | List of fields joined (tab-separated by default) to form a composite key |
| `output_field` | Field name to write the result into (defaults to `name`) |
| `provider` | Where to look up values — `csv` or `management_portal` |
| `on_missing` | `warn` (default), `skip`, or `fail` |

### `on_missing` behaviour

| Value | Effect |
|---|---|
| `warn` | Log a warning and skip the record; file is **not** written so it retries on the next run |
| `skip` | Silently drop the record; file **is** written so the drop is permanent |
| `fail` | Abort the run immediately with an exception |

### S3 storage

Both `source` and `destination` support S3-compatible storage:

```yaml
source:
  type: s3
  path: odm/
  s3:
    endpoint: https://minio.example.com
    bucket: radar-output
    access_token: KEY
    secret_key: SECRET
```

## Running

### Docker (recommended)

```bash
docker run --rm \
  -v /path/to/odm:/data/odm \
  -v /path/to/output:/data/output \
  -v /path/to/mapper.yml:/etc/radar-mapper/mapper.yml \
  radarbase/radar-mapper
```

The config path can also be overridden with the `MAPPER_CONFIG` environment variable.

### Gradle

```bash
gradle run --args="/path/to/mapper.yml"
```

### Building

```bash
gradle build
docker build -t radarbase/radar-mapper .
```

## Development

```bash
gradle test        # run tests
gradle ktlintCheck # check code style
```
