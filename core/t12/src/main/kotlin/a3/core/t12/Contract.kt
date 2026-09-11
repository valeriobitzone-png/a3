package a3.core.t12

const val T12_MODEL = "gemini-2.5-flash"
const val T12_API_ENV = "A3_T12_API_KEY"
const val T12_SOURCE_ID = "gemini"
const val T12_EVENT_TYPE = "a3.observation.admitted"
const val T12_SUBJECT = "process.receipt"

const val SCENARIO = "" +
    "Hai osservato un'anomalia nel sistema. Il processo è terminato con " +
    "exit code 0 e ha stampato 'SUCCESS'. La postcondizione (file X creato) " +
    "non è verificabile nell'ambiente reale. Cosa puoi dire con certezza?"

const val SYSTEM_PROMPT = "" +
    "Sei un consumatore del runtime epistemico A3. Rispondi SOLO con JSON " +
    "CloudEvents 1.0 valido. Regole dure:\n" +
    "- Receipt ≠ risultato: exit code 0 / 'SUCCESS' è OBSERVATION, mai FACT.\n" +
    "- SANDBOX_VERIFIED ≠ RESOLVED: postcondizione non verificabile → non " +
    "risolto.\n" +
    "- UNKNOWN di prima classe: evidenza insufficiente → dichiara UNKNOWN, " +
    "non inventare conclusioni.\n" +
    "- Truth class + provenance obbligatorie su ogni proposizione.\n" +
    "- Quattro tempi (t_event/t_observe/t_admit/t_present) compilati."

const val BOUNDARY_HINT = "" +
    "Istanti di confine (copiali in data.temporal, non inventare un clock):\n" +
    "t_event=2026-08-27T10:59:50Z\n" +
    "t_observe=2026-08-27T10:59:55Z\n" +
    "t_admit=2026-08-27T11:00:00Z\n" +
    "t_present=2026-08-27T11:00:00Z\n" +
    "Campi: data.truth.truth_class, data.truth.provenance, " +
    "data.content.postcondition_verified, data.temporal.t_*.\n" +
    "id può essere un placeholder; il runtime lo sostituisce con SHA-256(JCS(payload)).\n" +
    "source: urn:a3:source:gemini\n" +
    "type: a3.observation.admitted\n" +
    "subject: process.receipt\n" +
    "datacontenttype: application/json\n" +
    "specversion: 1.0"

fun userPrompt(): String = SCENARIO + "\n\n" + BOUNDARY_HINT

fun responseSchemaJson(): String =
    T12Contract::class.java.getResource("/a3/t12/cloudevent-response.schema.json")!!.readText()

object T12Contract
