package ai.gravityfield.gravity_sdk.network

import kotlinx.serialization.json.*
import ai.gravityfield.gravity_sdk.network.toMap as androidObjectMap
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Compare against the pre-release parser, using Android's actual JSONObject behavior. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CampaignResponseCompatibilityTest {
    private fun oldParse(body: String): CampaignIdsResponse {
        val fields = try {
            JSONObject(body).androidObjectMap()
        } catch (failure: JSONException) {
            val root = Json.parseToJsonElement(body).jsonObject
            val campaigns = root["campaigns"] as? JsonArray ?: throw failure
            val sanitized = JsonArray(campaigns.map { campaign ->
                if (campaign !is JsonObject) campaign else JsonObject(campaign.mapValues { (key, value) ->
                    if ((key == "priority" || key == "delayTime") && value is JsonPrimitive &&
                        !value.isString && value.doubleOrNull?.isFinite() == false) JsonNull else value
                })
            })
            JSONObject(JsonObject(root + ("campaigns" to sanitized)).toString()).androidObjectMap()
        }
        return CampaignIdsResponse.fromJson(fields)
    }

    private fun assertSameAccepted(body: String) {
        val before = oldParse(body)
        assertEquals(before, parseCampaignIdsResponse(body))
    }

    private val rawVectors = linkedMapOf(
        "1e128" to Int.MAX_VALUE, "1e-129" to 0, "1e-300" to 0,
        "1e400" to null, "1e-400" to 0, "-1e400" to null,
        "NaN" to null, "Infinity" to null, "-Infinity" to null,
        "0.99999999999999999999" to 1, "1500.99999999999999999999" to 1501,
        "0" to 0, "42" to 42, "-42" to -42, "1.9" to 1, "-1.9" to -1,
    )

    @Test fun rawNumbersUseDoublePrecisionAndPreserveSiblings() {
        for ((token, expected) in rawVectors) {
            val result = parseCampaignIdsResponse("""{"user":{"uid":"u","ses":"s"},"campaigns":[
                {"campaignId":"value","priority":$token,"delayTime":$token},
                {"campaignId":"sibling","priority":7,"delayTime":42}]}""")
            assertEquals(token, listOf("value", "sibling"), result.campaigns.map { it.campaignId })
            assertEquals(token, expected, result.campaigns[0].priority)
            assertEquals(token, expected, result.campaigns[0].delayTime)
            assertEquals(7, result.campaigns[1].priority)
            assertEquals(42, result.campaigns[1].delayTime)
            assertEquals("u", result.user!!.uid)
        }
    }

    @Test fun overflowingSiblingFallbackDoesNotMakeOtherRawNumbersExact() {
        val campaigns = rawVectors.keys.mapIndexed { index, token ->
            """{"campaignId":"$index","priority":$token,"delayTime":$token}"""
        }.joinToString(",")
        val result = parseCampaignIdsResponse("""{"campaigns":[$campaigns]}""")
        assertEquals(rawVectors.values.toList(), result.campaigns.map { it.priority })
        assertEquals(rawVectors.values.toList(), result.campaigns.map { it.delayTime })
    }

    @Test fun decimalStringsKeepExactParsingAndExponentBounds() {
        val vectors = rawVectors + mapOf("1e400" to Int.MAX_VALUE, "-1e400" to Int.MIN_VALUE,
            "0.99999999999999999999" to 0, "1500.99999999999999999999" to 1500)
        for ((token, expected) in vectors) {
            val result = parseCampaignIdsResponse("""{"campaigns":[{"campaignId":"value","priority":"$token","delayTime":"$token"}]}""")
            assertEquals(token, expected, result.campaigns.single().priority)
            assertEquals(token, expected, result.campaigns.single().delayTime)
        }
    }

    @Test fun unknownFieldsAndNullsPreserveAcceptedResponse() {
        for (body in listOf(
            """{"user":null,"campaigns":null,"unknown":{"array":[null,true,1.5,{"x":"value"}]}}""",
            """{"unknown":null}""",
            """{"user":{"uid":"u","ses":"s","future":true},"campaigns":[{"campaignId":"a","priority":null,"delayTime":null,"future":{"x":42}}]}""",
            """{"campaigns":[{"campaignId":"a","trigger":null}],"unknownNumber":9223372036854775807}""",
        )) assertSameAccepted(body)
    }

    @Test fun finiteNumbersAndUnusualStringMetadataPreserveAcceptedResponse() {
        for (value in listOf("0", "-0", "0.5", "-0.5", "1.25", "-1.25", "1e3", "1E-3",
            "2147483647", "-2147483648", "9223372036854775807", "1e100", "1e-400",
            "\"NaN\"", "\"Infinity\"", "\"0x10\"", "\"1.\"", "\"+.5\"", "true", "false", "{}", "[]")) {
            assertSameAccepted("""{"campaigns":[{"campaignId":"a","priority":$value,"delayTime":$value}]}""")
        }
    }

    @Test fun oddRawNumericFormsPreserveAcceptedMetadata() {
        // org.json also accepts some non-JSON literal spellings.
        for (value in listOf("+5", ".5", "5.", "NaN", "Infinity", "-Infinity", "0x10", "0X10", "09", "-010")) {
            assertSameAccepted("""{"campaigns":[{"campaignId":"a","priority":$value,"delayTime":$value}]}""")
        }
    }

    @Test fun legacyOctalLiteralKeepsItsOriginalMeaning() {
        val body = """{"campaigns":[{"campaignId":"a","priority":010,"delayTime":010}]}"""
        assertEquals(8, oldParse(body).campaigns.single().priority)
        assertEquals(8, parseCampaignIdsResponse(body).campaigns.single().priority)
    }

    @Test fun nonfiniteCampaignNumbersKeepSiblingsAsBefore() {
        for (number in listOf("1e400", "-1e400", "1e999999999")) {
            assertSameAccepted("""{"user":{"uid":"u","ses":"s"},"campaigns":[{"campaignId":"a","priority":$number,"delayTime":$number},{"campaignId":"b","priority":2,"delayTime":42}]}""")
        }
    }

    @Test fun duplicateKeysKeepTheLastValueAsBefore() {
        assertSameAccepted("""{"user":{"uid":"first","uid":"last","ses":"s"},"campaigns":[],"campaigns":[{"campaignId":"first","campaignId":"last","priority":1,"priority":7,"delayTime":5,"delayTime":42}]}""")
    }

    @Test fun largeUnknownFieldsAndCampaignListsPreserveAcceptedResponse() {
        val campaigns = (1..1000).joinToString(",") { """{"campaignId":"$it","priority":$it,"delayTime":42,"unknown":[null,true,1.5]}""" }
        assertSameAccepted("""{"unknown":"${"x".repeat(512 * 1024)}","campaigns":[$campaigns]}""")
    }

    @Test fun nonfiniteUnknownNumbersRemainRejectedByBothParsers() {
        val body = """{"unknown":1e400,"campaigns":[{"campaignId":"a"}]}"""
        assertTrue(runCatching { oldParse(body) }.isFailure)
        assertTrue(runCatching { parseCampaignIdsResponse(body) }.isFailure)
    }

    @Test fun lenientNonJsonSyntaxRemainsAccepted() {
        // Preserve the established JSONObject-first parsing boundary.
        for (body in listOf(
            """{/*comment*/"campaigns":[{"campaignId":"a"}]}""",
            """{'campaigns':[{'campaignId':'a'}]}""",
            """{campaigns:[{campaignId:"a"}]}""",
        )) {
            assertEquals("a", oldParse(body).campaigns.single().campaignId)
            assertEquals("a", parseCampaignIdsResponse(body).campaigns.single().campaignId)
        }
    }
}
