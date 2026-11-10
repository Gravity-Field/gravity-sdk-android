package ai.gravityfield.gravity_sdk.network

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger

class CampaignIdParsingTest {
    @Test fun priorityAndDelayAcceptOnlyFiniteDecimalNumbersAndSaturate() {
        val cases = listOf(
            1500 to 1500, 1500L to 1500, 1500.9 to 1500, -1.5 to -1, 15.9 to 15, -15.9 to -15,
            "\u00A01500\u3000" to 1500, "\u00851500" to 1500,
            "\uFEFF15" to null, "\u200B15" to null,
            "1." to 1, "1.e2" to 100, "0.9999999999999999999999" to 0, "-0.9" to 0,
            "1e400" to Int.MAX_VALUE, "-1e400" to Int.MIN_VALUE, "1e-400" to 0,
            "99999999999999999999" to Int.MAX_VALUE, "9007199254740993" to Int.MAX_VALUE,
            "9223372036854775807.9" to Int.MAX_VALUE,
            "1_000" to null, "1,5" to null, "abc" to null, "١٥" to null,
            "0e9999999999999999999999999999999999999999999" to 0,
            "1500" to 1500, " 1500 " to 1500, "1500.0" to 1500,
            "+5" to 5, "-3" to -3, "1e3" to 1000, "-1.9" to -1,
            ".5" to 0, "5." to 5,
            "0x10" to null, true to null, false to null, null to null,
            "" to null, "  " to null, "bad" to null, emptyMap<String, Any>() to null,
            emptyList<Any>() to null, Double.NaN to null, Double.POSITIVE_INFINITY to null,
            Double.NEGATIVE_INFINITY to null, Float.NaN to null,
            "NaN" to null, "Infinity" to null, "-Infinity" to null,
            "1e999999999" to Int.MAX_VALUE, "-1e999999999" to Int.MIN_VALUE,
            "1e-999999999" to 0, "-1e-999999999" to 0,
            "1e9999999999999" to Int.MAX_VALUE, "-1e9999999999999" to Int.MIN_VALUE,
            "1e-9999999999999" to 0, "2147483646.999999999" to 2147483646,
            "-2147483647.999999999" to -2147483647,
            Long.MAX_VALUE to Int.MAX_VALUE, Long.MIN_VALUE to Int.MIN_VALUE,
            Double.MAX_VALUE to Int.MAX_VALUE, -Double.MAX_VALUE to Int.MIN_VALUE,
            BigInteger("999999999999999999999999") to Int.MAX_VALUE,
            BigDecimal("-999999999999999999999999.9") to Int.MIN_VALUE,
        )
        for ((value, expected) in cases) {
            val parsed = CampaignId.fromJson(mapOf("campaignId" to "c", "priority" to value, "delayTime" to value))
            assertEquals("priority for $value", expected, parsed.priority)
            assertEquals("delayTime for $value", expected, parsed.delayTime)
            val response = CampaignIdsResponse.fromJson(mapOf("campaigns" to listOf(
                mapOf("campaignId" to "c", "priority" to value, "delayTime" to value),
                mapOf("campaignId" to "sibling", "priority" to 7, "delayTime" to 42),
            )))
            assertEquals(2, response.campaigns.size)
            assertEquals(42, response.campaigns[1].delayTime)
        }
        // Exact Unicode White_Space set, including NEL and excluding Java-only
        // whitespace separators U+001C..U+001F, BOM and zero-width space.
        val whitespace = "\u0009\u000A\u000B\u000C\u000D\u0020\u0085\u00A0\u1680" +
            "\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2007\u2008\u2009\u200A" +
            "\u2028\u2029\u202F\u205F\u3000"
        for (char in whitespace + "\u001C\u001D\u001E\u001F\uFEFF\u200B") {
            val expected = if (char in whitespace) 15 else null
            val parsed = CampaignId.fromJson(mapOf("campaignId" to "c", "priority" to "$char" + "15$char", "delayTime" to "$char" + "15$char"))
            assertEquals("priority whitespace U+${char.code.toString(16)}", expected, parsed.priority)
            assertEquals("delay whitespace U+${char.code.toString(16)}", expected, parsed.delayTime)
        }
        val missing = CampaignId.fromJson(mapOf("campaignId" to "c"))
        assertNull(missing.priority)
        assertNull(missing.delayTime)
    }

    @Test(timeout = 3000) fun boundedDecimalConversionMatchesExactArithmetic() {
        val random = java.util.Random(12345)
        val min = BigDecimal.valueOf(Int.MIN_VALUE.toLong())
        val max = BigDecimal.valueOf(Int.MAX_VALUE.toLong())
        repeat(2000) {
            val digits = (1..(1 + random.nextInt(40))).joinToString("") { random.nextInt(10).toString() }
            val point = random.nextInt(digits.length + 1)
            val value = (if (random.nextBoolean()) "-" else "+") +
                digits.substring(0, point) + "." + digits.substring(point) + "e" + (random.nextInt(161) - 80)
            val exact = BigDecimal(value)
            val expected = when {
                exact < min -> Int.MIN_VALUE
                exact > max -> Int.MAX_VALUE
                else -> exact.toInt()
            }
            val parsed = CampaignId.fromJson(mapOf("campaignId" to "c", "priority" to value, "delayTime" to value))
            assertEquals("priority for $value", expected, parsed.priority)
            assertEquals("delayTime for $value", expected, parsed.delayTime)
        }
        // Input-length bounded scanning, including exponents beyond Long range.
        for ((value, expected) in listOf(
            "1e" + "9".repeat(10000) to Int.MAX_VALUE,
            "-1e" + "9".repeat(10000) to Int.MIN_VALUE,
            "1e-" + "9".repeat(10000) to 0,
            "0." + "0".repeat(10000) + "1e10001" to 1,
        )) {
            val parsed = CampaignId.fromJson(mapOf("campaignId" to "c", "priority" to value, "delayTime" to value))
            assertEquals(expected, parsed.priority)
            assertEquals(expected, parsed.delayTime)
        }
    }

}
