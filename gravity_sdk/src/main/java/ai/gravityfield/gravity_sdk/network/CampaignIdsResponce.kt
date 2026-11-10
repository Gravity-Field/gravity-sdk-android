package ai.gravityfield.gravity_sdk.network

import ai.gravityfield.gravity_sdk.models.User
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import org.json.JSONException
import org.json.JSONObject

data class CampaignIdsResponse(
    val user: User?,
    val campaigns: List<CampaignId>,
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: Map<String, Any?>): CampaignIdsResponse {
            return CampaignIdsResponse(
                user = if (json["user"] != null) User.fromJson(json["user"] as Map<String, Any?>) else null,
                campaigns = (json["campaigns"] as? List<Map<String, Any?>>)?.map {
                    CampaignId.fromJson(
                        it
                    )
                } ?: emptyList()
            )
        }
    }
}

data class CampaignId(
    val campaignId: String,
    val trigger: String,
    val priority: Int?,
    val delayTime: Int?,
) {
    companion object {
        fun fromJson(json: Map<String, Any?>): CampaignId {
            return CampaignId(
                campaignId = json["campaignId"] as String,
                trigger = if (json["trigger"] != null) json["trigger"] as String else "",
                priority = parseCampaignInteger(json["priority"]),
                delayTime = parseCampaignInteger(json["delayTime"])
            )
        }
    }
}

// Decimal syntax only: in particular, JVM hex floats, NaN and infinity are not numbers here.
private val decimalNumber = Regex("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?")
// Unicode White_Space, not Char.isWhitespace(): NEL is included; BOM, zero-width
// space and the Java-only U+001C..U+001F separators are deliberately excluded.
private fun isCampaignWhitespace(char: Char): Boolean = when (char.code) {
    in 0x0009..0x000D, 0x0020, 0x0085, 0x00A0, 0x1680,
    in 0x2000..0x200A, 0x2028, 0x2029, 0x202F, 0x205F, 0x3000 -> true
    else -> false
}

/** Truncates decimal values exactly and bounds exponent work before expanding any digits. */
private fun parseCampaignInteger(value: Any?): Int? {
    val decimal = when (value) {
        // Android JSONTokener produces Integer, Long or Double, never Float.
        // Decimal syntax below also rejects nonfinite Number values (NaN/Infinity).
        is Number -> value.toString()
        is String -> value.trim(::isCampaignWhitespace)
        else -> return null
    }
    if (!decimalNumber.matches(decimal)) return null
    val negative = decimal[0] == '-'
    val start = if (decimal[0] == '-' || decimal[0] == '+') 1 else 0
    val exponentAt = decimal.indexOfAny(charArrayOf('e', 'E')).let { if (it < 0) decimal.length else it }
    val pointAt = decimal.indexOf('.').let { if (it < 0) exponentAt else it }
    val fractionDigits = if (pointAt < exponentAt) exponentAt - pointAt - 1 else 0
    var firstSignificant = start
    while (firstSignificant < exponentAt &&
        (decimal[firstSignificant] == '0' || decimal[firstSignificant] == '.')) firstSignificant++
    if (firstSignificant == exponentAt) return 0

    // An exponent beyond input length + 10 guarantees Int overflow or underflow
    // for any nonzero coefficient. Scan without materialising or expanding it.
    val exponentLimit = decimal.length.toLong() + 10
    var exponent = 0L
    if (exponentAt < decimal.length) {
        var index = exponentAt + 1
        val negativeExponent = decimal[index] == '-'
        if (decimal[index] == '-' || decimal[index] == '+') index++
        while (index < decimal.length) {
            exponent = minOf(exponentLimit, exponent * 10 + (decimal[index] - '0'))
            index++
        }
        if (negativeExponent) exponent = -exponent
    }
    val significantDigits = exponentAt - firstSignificant - if (pointAt > firstSignificant && pointAt < exponentAt) 1 else 0
    val integerDigits = significantDigits.toLong() + exponent - fractionDigits
    if (integerDigits <= 0) return 0
    if (integerDigits > 10) return if (negative) Int.MIN_VALUE else Int.MAX_VALUE

    var magnitude = 0L
    var consumed = 0
    var index = firstSignificant
    while (index < exponentAt && consumed < integerDigits) {
        val char = decimal[index++]
        if (char != '.') {
            magnitude = magnitude * 10 + (char - '0')
            consumed++
        }
    }
    while (consumed < integerDigits) {
        magnitude *= 10
        consumed++
    }
    val signed = if (negative) -magnitude else magnitude
    return signed.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
}

/** Keep siblings when Android's JSONObject rejects an overflowing numeric campaign field. */
internal fun parseCampaignIdsResponse(body: String): CampaignIdsResponse {
    val fields = try {
        JSONObject(body).toMap()
    } catch (failure: JSONException) {
        // Android JSONTokener decodes 1e400 as infinity, then JSONObject.put rejects
        // it. Decode just enough to omit nonfinite priority/delayTime fields and
        // retry the usual decoder, preserving all its other mapping behavior.
        val root = Json.parseToJsonElement(body).jsonObject
        val campaigns = root["campaigns"] as? JsonArray ?: throw failure
        val sanitized = JsonArray(campaigns.map { campaign ->
            if (campaign !is JsonObject) campaign else JsonObject(campaign.mapValues { (key, value) ->
                if ((key == "priority" || key == "delayTime") && value is JsonPrimitive &&
                    !value.isString && value.doubleOrNull?.isFinite() == false) JsonNull else value
            })
        })
        JSONObject(JsonObject(root + ("campaigns" to sanitized)).toString()).toMap()
    }
    return CampaignIdsResponse.fromJson(fields)
}
