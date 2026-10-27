package ai.gravityfield.gravity_sdk.network

import ai.gravityfield.gravity_sdk.models.ElementType
import ai.gravityfield.gravity_sdk.models.Variables
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RawVariablesTest {
    private fun response(variables: String?): String {
        val variablesField = if (variables == null) "" else """, "variables": $variables"""
        return """
            {
              "user": {"uid": "u", "ses": "s"},
              "data": [{
                "selector": "sel",
                "payload": [{
                  "campaignId": "c", "experienceId": "e", "variationId": "v", "decisionId": "d",
                  "contents": [{
                    "contentId": "content-1", "contentType": "native", "deliveryMethod": "inline"
                    $variablesField
                  }]
                }]
              }]
            }
        """.trimIndent()
    }

    private val variables = """
        {
          "elements": [{"type": "text", "text": "Hi"}],
          "onLoad": null,
          "ab_button_color": "#FF0000",
          "ab_limit": 3,
          "ab_ratio": 0.5,
          "ab_enabled": true,
          "ab_missing": null,
          "ab_list": [1, "two", null, {"k": "v"}],
          "ab_nested": {"inner": {"deep": [true, false]}, "none": null}
        }
    """.trimIndent()

    private fun content(body: String) = parseContentResponse(body).data.data.single().payload.single().contents.single()

    @Test
    fun unknownKeysAndJsonTypesAreKept() {
        val raw = content(response(variables)).rawVariables

        assertEquals("#FF0000", raw["ab_button_color"])
        assertEquals(3, (raw["ab_limit"] as Number).toInt())
        assertEquals(0.5, (raw["ab_ratio"] as Number).toDouble(), 0.0)
        assertEquals(true, raw["ab_enabled"])
        assertTrue(raw.containsKey("ab_missing"))
        assertNull(raw["ab_missing"])
        assertTrue(raw.containsKey("onLoad"))
    }

    @Test
    fun nestedValuesKeepNullsInListsAndObjects() {
        val raw = content(response(variables)).rawVariables

        val list = raw["ab_list"] as List<*>
        assertEquals(4, list.size)
        assertEquals(1, (list[0] as Number).toInt())
        assertEquals("two", list[1])
        assertNull(list[2])
        assertEquals(mapOf("k" to "v"), list[3])

        val nested = raw["ab_nested"] as Map<*, *>
        assertEquals(mapOf("deep" to listOf(true, false)), nested["inner"])
        assertTrue(nested.containsKey("none"))
        assertNull(nested["none"])
    }

    @Test
    fun keyAccessAndTypedValueOf() {
        val vars = content(response(variables)).variables!!

        assertEquals("#FF0000", vars["ab_button_color"])
        assertEquals("#FF0000", vars.valueOf<String>("ab_button_color"))
        assertNull(vars.valueOf<String>("ab_limit"))
        assertNull(vars["no_such_key"])
    }

    @Test
    fun typedFieldsStillParse() {
        val vars = content(response(variables)).variables!!

        val element = vars.elements!!.single()
        assertEquals(ElementType.TEXT, element.type)
        assertEquals("Hi", element.text)
        assertNull(vars.onLoad)
    }

    @Test
    fun contentWithoutVariablesHasEmptyRaw() {
        val content = content(response(null))

        assertNull(content.variables)
        assertTrue(content.rawVariables.isEmpty())
    }

    @Test
    fun handBuiltVariablesHaveEmptyRaw() {
        val vars = Variables(null, null, null, null, null, null)

        assertTrue(vars.raw.isEmpty())
        assertNull(vars["anything"])
    }

    @Test
    fun emptyResponse() {
        val parsed = parseContentResponse("{}")

        assertTrue(parsed.data.data.isEmpty())
        assertTrue(parsed.json.isEmpty())
    }

    @Test
    fun rawJsonMatchesTheModelOfTheSameResponse() {
        val parsed = parseContentResponse(response(variables))

        val campaign = (parsed.json["data"] as List<*>).single() as Map<*, *>
        val payload = (campaign["payload"] as List<*>).single() as Map<*, *>
        val rawContent = (payload["contents"] as List<*>).single() as Map<*, *>

        assertEquals(parsed.data.data.single().selector, campaign["selector"])
        assertEquals(parsed.data.data.single().payload.single().campaignId, payload["campaignId"])
        assertEquals(content(response(variables)).rawVariables, rawContent["variables"])
        assertEquals(mapOf("uid" to "u", "ses" to "s"), parsed.json["user"])
    }
}
