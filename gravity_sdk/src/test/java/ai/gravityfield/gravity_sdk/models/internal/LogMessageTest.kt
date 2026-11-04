package ai.gravityfield.gravity_sdk.models.internal

import ai.gravityfield.gravity_sdk.network.ErrorReportSanitizer
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

class LogMessageTest {
    @Test fun serializedReportContainsOnlySanitizedAndBoundedText() {
        // Exercise the same sanitizer and serialization used by the report transport.
        val message = LogMessage("https://host/{id}?uid=SECRET", LogMessageLevel.ERROR, "section", "device-uid",
            "session", "1.2.0", "Android", stacktrace = "x".repeat(6000))
        val body = Json { encodeDefaults = true }.encodeToString(ErrorReportSanitizer.sanitize(message))
        val report = Json.parseToJsonElement(body).jsonObject
        assertFalse(body.contains("SECRET"))
        assertEquals("https://host/{id}", report["message"]!!.jsonPrimitive.content)
        assertEquals(5000, report["stacktrace"]!!.jsonPrimitive.content.length)
        assertTrue(report["stacktrace"]!!.jsonPrimitive.content.endsWith("..."))
        assertEquals("\"device-uid\"", report["uid"].toString())
        assertEquals("\"session\"", report["ses"].toString())
    }
}
