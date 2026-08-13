package com.haptiq.app.data

/**
 * A parsed shareable kick-map file: the song's identity (so a human can tell what
 * it's for when sharing between devices with different song IDs) plus the taught
 * kick times in ms.
 */
data class KickMapFile(
    val schema: Int,
    val title: String,
    val artist: String,
    val durationSeconds: Int,
    val onsets: IntArray
)

/**
 * Tiny dependency-free JSON codec for exported/imported kick maps. The format is a
 * single small object, so hand-rolled encoding beats pulling in a JSON library:
 *
 *   {"schema":1,"song":{"title":"...","artist":"...","durationSeconds":123},"onsets":[0,500,1000]}
 *
 * [encode] is lossless for the fields that matter; [decode] returns null on any
 * malformed input (never throws) so import can silently reject junk files instead
 * of crashing. Unknown fields are skipped, so a future schema version that ADDS
 * fields still imports today.
 */
object KickMapCodec {
    private const val SCHEMA = 1

    fun encode(song: Song, onsets: IntArray): String = buildString {
        append("{\"schema\":").append(SCHEMA)
        append(",\"song\":{\"title\":").append(jsonString(song.title))
        append(",\"artist\":").append(jsonString(song.artist))
        append(",\"durationSeconds\":").append(song.durationSeconds)
        append("},\"onsets\":[")
        onsets.forEachIndexed { i, t ->
            if (i > 0) append(',')
            append(t)
        }
        append("]}")
    }

    fun decode(json: String): KickMapFile? {
        val p = Parser(json)
        return try {
            p.skipWs()
            if (p.peek() != '{') return null
            p.next()
            var schema = -1
            var title = ""
            var artist = ""
            var durationSeconds = -1
            var onsets = IntArray(0)
            while (true) {
                p.skipWs()
                val c = p.peek()
                if (c == '}') { p.next(); break }
                val key = p.parseString() ?: return null
                p.skipWs()
                if (p.next() != ':') return null
                p.skipWs()
                when (key) {
                    "schema" -> schema = p.parseInt() ?: return null
                    "song" -> {
                        if (p.next() != '{') return null
                        while (true) {
                            p.skipWs()
                            if (p.peek() == '}') { p.next(); break }
                            val k2 = p.parseString() ?: return null
                            p.skipWs()
                            if (p.next() != ':') return null
                            p.skipWs()
                            when (k2) {
                                "title" -> title = p.parseString() ?: return null
                                "artist" -> artist = p.parseString() ?: return null
                                "durationSeconds" -> durationSeconds = p.parseInt() ?: return null
                                else -> if (!p.skipValue()) return null
                            }
                            p.skipWs()
                            if (p.peek() == ',') p.next()
                        }
                    }
                    "onsets" -> {
                        if (p.next() != '[') return null
                        val list = ArrayList<Int>(64)
                        while (true) {
                            p.skipWs()
                            when (p.peek()) {
                                ']' -> { p.next(); onsets = list.toIntArray(); break }
                                ',' -> { p.next(); continue }
                                else -> list.add(p.parseInt() ?: return null)
                            }
                        }
                    }
                    else -> if (!p.skipValue()) return null
                }
                p.skipWs()
                if (p.peek() == ',') p.next()
            }
            if (schema != SCHEMA || onsets.isEmpty()) return null
            KickMapFile(schema, title, artist, durationSeconds, onsets)
        } catch (e: Exception) {
            null
        }
    }

    private fun jsonString(s: String): String = buildString {
        append('"')
        for (c in s) {
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c.code < 0x20) append("\\u%04x".format(c.code)) else append(c)
            }
        }
        append('"')
    }

    /** Minimal forward-only JSON scanner — enough for this file format. */
    private class Parser(private val s: String) {
        private var i = 0

        fun peek(): Char = s.getOrNull(i) ?: '\u0000'

        fun next(): Char = s.getOrNull(i++) ?: '\u0000'

        fun skipWs() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        fun parseInt(): Int? {
            skipWs()
            var j = i
            if (j < s.length && (s[j] == '-' || s[j] == '+')) j++
            val start = j
            while (j < s.length && s[j].isDigit()) j++
            if (j == start) return null
            val v = s.substring(start, j).toIntOrNull() ?: return null
            i = j
            return v
        }

        fun parseString(): String? {
            skipWs()
            if (peek() != '"') return null
            next()
            val sb = StringBuilder()
            while (true) {
                val c = next()
                if (c == '\u0000') return null
                if (c == '"') return sb.toString()
                if (c == '\\') {
                    val e = next()
                    when (e) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'u' -> {
                            if (i + 4 > s.length) return null
                            val hex = s.substring(i, i + 4).toIntOrNull(16) ?: return null
                            i += 4
                            sb.append(hex.toChar())
                        }
                        else -> return null
                    }
                } else {
                    sb.append(c)
                }
            }
        }

        /** Consume any JSON value (object/array/string/number/literal) without inspecting it. */
        fun skipValue(): Boolean {
            skipWs()
            return when (peek()) {
                '"' -> parseString() != null
                '{', '[' -> {
                    val close = if (peek() == '{') '}' else ']'
                    next()
                    while (i < s.length) {
                        val c = peek()
                        if (c == close) { next(); return true }
                        if (c == '"') { if (parseString() == null) return false }
                        else if (c == '{' || c == '[') { if (!skipValue()) return false }
                        else next()
                    }
                    false
                }
                else -> {
                    if (parseInt() != null) true
                    else {
                        // JSON literal (true/false/null) — skip to a structural char.
                        while (i < s.length && s[i] !in ",}]" && !s[i].isWhitespace()) i++
                        true
                    }
                }
            }
        }
    }
}
