package ai.gravityfield.gravity_sdk.network

import ai.gravityfield.gravity_sdk.models.internal.LogMessage
import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder

internal object ErrorReportSanitizer {
    private val authorizationValue = Regex(
        """\b((?:proxy-|x-)?authorization)[ \t]*[:=][^\r\n]*""",
        RegexOption.IGNORE_CASE,
    )

    private fun normalizeText(text: String): String = normalizeEscapedUrls(
        authorizationValue.replace(text) { it.groupValues[1] + ": [redacted]" },
    )

    private val networkPrefix = Regex("""(?<![a-z0-9+.-])[a-z][a-z0-9+.-]*+://|(?<!:)//(?!/)""", RegexOption.IGNORE_CASE)
    private val prefix = Regex("""(?<![a-z0-9+.-])(?:[a-z][a-z0-9+.-]*+://|(?:file|package|dart|content|data):)|(?<!:)//(?!/)""", RegexOption.IGNORE_CASE)
    private val escapedUrl = Regex("""(?<![a-z0-9+.-])(?:[a-z][a-z0-9+.-]*+:(?:\\{1,4}/){2}|(?:\\{1,4}/){2}|[a-z][a-z0-9+.-]*+%(?:25){0,3}3a%(?:25){0,3}2f%(?:25){0,3}2f|%(?:25){0,3}2f%(?:25){0,3}2f)[^\s"'<>]*+""", RegexOption.IGNORE_CASE)
    private val encodedPrefix = Regex("""^(?:[a-z][a-z0-9+.-]*+%(?:25){0,3}3a%(?:25){0,3}2f%(?:25){0,3}2f|%(?:25){0,3}2f%(?:25){0,3}2f)""", RegexOption.IGNORE_CASE)

    fun sanitize(message: LogMessage): LogMessage = message.copy(
        message = sanitizeText(message.message, 1000),
        stacktrace = sanitizeText(message.stacktrace, 5000),
        tags = message.tags.mapValues { sanitizeUrls(it.value) },
        extra = message.extra.mapValues { sanitizeUrls(it.value) },
    )

    private fun normalizeEscapedUrls(text: String): String = text.replace(escapedUrl) { match ->
        val raw = match.value
        var decoded = raw
        repeat(4) {
            if (encodedPrefix.containsMatchIn(decoded)) {
                decoded = try { URLDecoder.decode(decoded.replace("+", "%2B"), "UTF-8") }
                catch (_: IllegalArgumentException) { "[redacted]" }
            }
        }
        var normalized = decoded
        repeat(4) { normalized = normalized.replace("\\/", "/") }
        normalized
    }

    private fun delimiter(char: Char) = char.isWhitespace() || char in "\"'<>"
    private fun tokenEnd(text: String, start: Int): Int {
        var end = start
        while (end < text.length && !delimiter(text[end])) end++
        return end
    }

    private fun validIpv6Authority(candidate: String): Boolean {
        if (!candidate.startsWith('[')) return false
        val bracket = candidate.indexOf(']')
        if (bracket < 0) return false
        val address = candidate.substring(1, bracket)
        if (':' !in address || address.any { it !in "0123456789abcdefABCDEF:." }) return false
        val rest = candidate.substring(bracket + 1)
        if (rest.isNotEmpty() && !(rest.startsWith(':') && rest.length > 1 && rest.drop(1).all { it.isDigit() })) return false
        // URI validates numeric IPv6 syntax locally; it never resolves the host.
        return try { URI("http://$candidate").host != null } catch (_: URISyntaxException) { false }
    }

    // Numeric passwords and host ports are indistinguishable when a later @ appears.
    private fun ambiguousAuthority(candidate: String) = ':' in candidate && '@' !in candidate && !validIpv6Authority(candidate)

    private fun passwordLikePort(authority: String): Boolean {
        if ('@' in authority) return false
        val port = if (authority.startsWith('[') && ']' in authority) {
            val suffix = authority.substringAfter(']')
            if (!suffix.startsWith(':')) return false
            suffix.drop(1)
        } else authority.substringAfter(':', "")
        fun numeric(value: String) = value.isNotEmpty() && value.all { it in '0'..'9' }
        // A diagnostic colon following a numeric port is punctuation (V24).
        return port.isNotEmpty() && !numeric(port) && !(port.endsWith(':') && numeric(port.dropLast(1)))
    }

    private data class SplitUserinfo(val at: Int, val end: Int)
    private fun splitUserinfo(text: String, match: MatchResult, end: Int): SplitUserinfo? {
        if (!match.value.endsWith("//") || end >= text.length || text[end] !in "\"'" && !text[end].isWhitespace()) return null
        val candidate = text.substring(match.range.last + 1, end)
        if (':' !in candidate || '@' in candidate || candidate.any { it in "/?#" }) return null
        val nextUrl = prefix.find(text, end)?.range?.first ?: text.length
        var word = end
        while (word < nextUrl && (text[word].isWhitespace() || text[word] in "\"'")) word++
        if (word >= nextUrl) return null
        if (text[word] in "<>") return null
        val finish = tokenEnd(text, word).coerceAtMost(nextUrl)
        val relativeAt = text.substring(word, finish).indexOf('@')
        val firstAt = relativeAt.takeIf { it >= 0 }?.let { word + it }
        if (firstAt != null) {
            var authorityEnd = firstAt + 1
            while (authorityEnd < finish && text[authorityEnd] !in "/?#") authorityEnd++
            val lastAt = text.lastIndexOf('@', authorityEnd - 1)
            return SplitUserinfo(lastAt, finish)
        }
        return null
    }

    fun sanitizeUrls(text: String): String = sanitizePlain(normalizeText(text))

    private fun sanitizePlain(text: String): String = buildString {
        var cursor = 0
        while (cursor < text.length) {
            val match = prefix.find(text, cursor) ?: break
            append(text, cursor, match.range.first)
            val end = tokenEnd(text, match.range.last + 1)
            if (!match.value.endsWith("//")) {
                // A path scheme can wrap another URL: content:https://... must still be scrubbed.
                val token = text.substring(match.range.first, end)
                val cut = token.indexOfAny(charArrayOf('?', '#')).takeIf { it >= 0 } ?: token.length
                val nested = networkPrefix.find(token, match.value.length)?.range?.first
                    ?.takeIf { it < cut }?.let { match.range.first + it }
                if (nested != null) {
                    append(text, match.range.first, nested)
                    cursor = nested
                } else {
                    append(token, 0, cut)
                    cursor = end
                }
            } else {
                val split = splitUserinfo(text, match, end)
                if (split != null) {
                    append(cleanNetworkToken(match.value + "[redacted]" + text.substring(split.at + 1, split.end)))
                    cursor = split.end
                } else {
                    append(cleanNetworkToken(text.substring(match.range.first, end)))
                    cursor = end
                }
            }
        }
        append(text, cursor, text.length)
    }

    private fun cleanNetworkToken(token: String): String {
        val starts = networkPrefix.findAll(token).toList()
        return buildString {
            var nextAt = token.indexOf('@')
            for ((index, start) in starts.withIndex()) {
                val end = starts.getOrNull(index + 1)?.range?.first ?: token.length
                val region = token.substring(start.range.last + 1, end)
                val authorityEnd = region.indexOfAny(charArrayOf('/', '?', '#')).takeIf { it >= 0 } ?: region.length
                val authority = region.substring(0, authorityEnd)
                val afterAuthority = start.range.last + 1 + authorityEnd
                while (nextAt >= 0 && nextAt < afterAuthority) nextAt = token.indexOf('@', nextAt + 1)
                // A raw // can be part of malformed userinfo (SE//CRET@host), not a new endpoint.
                val rawSlashContinuation = starts.getOrNull(index + 1)?.value == "//"
                if (passwordLikePort(authority) ||
                    ambiguousAuthority(authority) && nextAt >= 0 && (nextAt < end || rawSlashContinuation)) {
                    append(start.value).append("[redacted]")
                    // Preserve an adjacent URL separator, never the ambiguous path/query.
                    append(region.takeLastWhile { it in "),};.!:" })
                    continue
                }
                val query = region.indexOfAny(charArrayOf('?', '#')).takeIf { it >= 0 } ?: region.length
                val core = region.substring(0, query)
                val slash = core.indexOf('/').takeIf { it >= 0 } ?: core.length
                append(start.value)
                append(core.substring(0, slash).substringAfterLast('@'))
                append(core, slash, core.length)
                if (query < region.length) return@buildString
            }
        }
    }

    private fun boundInput(text: String, limit: Int): String {
        if (text.length <= limit) return text
        var cursor = 0
        while (cursor < limit) {
            val match = prefix.find(text, cursor) ?: break
            if (match.range.first >= limit) break
            val rawEnd = tokenEnd(text, match.range.last + 1)
            val end = splitUserinfo(text, match, rawEnd)?.end ?: rawEnd
            if (end > limit) {
                // Drop only the cut URL; retain following prose within the remaining budget.
                val remaining = limit - match.range.first
                return text.substring(0, match.range.first) + text.substring(end, end + remaining.coerceAtMost(text.length - end))
            }
            cursor = end.coerceAtLeast(match.range.last + 1)
        }
        return text.take(limit)
    }

    internal fun sanitizeText(text: String, limit: Int): String {
        val bounded = boundInput(normalizeText(text), limit * 10)
        val clean = sanitizePlain(bounded)
        return if (clean.length > limit) clean.take(limit - 3) + "..." else clean
    }
}
