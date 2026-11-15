package com.v2ray.ang.shroudly

sealed class ShroudlyEvent {
    data class Ready(val port: Int, val techniques: Int, val pid: Int = 0) : ShroudlyEvent()
    data class Stats(
        val connsTotal: Long,
        val connsActive: Long,
        val bytesUp: Long,
        val bytesDown: Long,
        val desync: Long,
        val tls: Long,
        val http: Long,
        val probeOk: Long,
        val probeFail: Long,
        val blocked: Long = 0,
        val fallback: Long = 0,
    ) : ShroudlyEvent()
    data class Host(
        val kind: String, // "bypass", "blocked", "cut", "unreachable", "send_failed"
        val host: String,
        val strategy: String,
        val attempts: Int = 1,
        val note: String? = null
    ) : ShroudlyEvent()
    data class Error(val message: String, val code: Int? = null) : ShroudlyEvent()
    data class Text(val line: String) : ShroudlyEvent()
    data class Other(val type: String) : ShroudlyEvent()

    companion object {
        private val HOST_KINDS = setOf("bypass", "blocked", "cut", "unreachable", "send_failed")

        fun parse(line: String): ShroudlyEvent {
            val m = FlatJson.parse(line) ?: return Text(line)
            fun str(k: String) = m[k] as? String
            fun num(k: String) = (m[k] as? Number)?.toLong() ?: 0L
            val type = str("type") ?: return Text(line)
            return when (type) {
                "ready" -> Ready(num("port").toInt(), num("techniques").toInt(), num("pid").toInt())
                "stats" -> Stats(
                    num("conns_total"), num("conns_active"), num("bytes_up"), num("bytes_down"),
                    num("desync"), num("tls"), num("http"), num("probe_ok"), num("probe_fail"),
                    num("blocked"), num("fallback")
                )
                in HOST_KINDS -> Host(
                    kind = type,
                    host = str("host") ?: "",
                    strategy = str("strategy") ?: "",
                    attempts = num("attempts").toInt(),
                    note = str("note")
                )
                "error" -> Error(str("msg") ?: "code ${num("code")}", (m["code"] as? Number)?.toInt())
                else -> Other(type)
            }
        }
    }
}

object FlatJson {
    fun parse(s: String): Map<String, Any?>? {
        val p = Parser(s.trim())
        return try {
            val m = p.obj()
            if (p.i != p.s.length) null else m
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private class Parser(val s: String) {
        var i = 0

        fun fail(): Nothing = throw IllegalArgumentException("bad json at $i")

        fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }

        fun expect(c: Char) { ws(); if (i >= s.length || s[i] != c) fail(); i++ }

        fun obj(): Map<String, Any?> {
            val out = LinkedHashMap<String, Any?>()
            expect('{')
            ws()
            if (i < s.length && s[i] == '}') { i++; return out }
            while (true) {
                ws()
                val k = string()
                expect(':')
                out[k] = value()
                ws()
                if (i >= s.length) fail()
                when (s[i++]) {
                    ',' -> continue
                    '}' -> return out
                    else -> fail()
                }
            }
        }

        fun value(): Any? {
            ws()
            if (i >= s.length) fail()
            return when (s[i]) {
                '"' -> string()
                't' -> word("true", true)
                'f' -> word("false", false)
                'n' -> word("null", null)
                else -> number()
            }
        }

        fun word(w: String, v: Any?): Any? {
            if (!s.startsWith(w, i)) fail()
            i += w.length
            return v
        }

        fun number(): Number {
            val start = i
            if (i < s.length && s[i] == '-') i++
            while (i < s.length && (s[i].isDigit() || s[i] in ".eE+-")) i++
            val t = s.substring(start, i)
            if (t.isEmpty() || t == "-") fail()
            return t.toLongOrNull() ?: t.toDoubleOrNull() ?: fail()
        }

        fun string(): String {
            if (i >= s.length || s[i] != '"') fail()
            i++
            val b = StringBuilder()
            while (true) {
                if (i >= s.length) fail()
                val c = s[i++]
                when (c) {
                    '"' -> return b.toString()
                    '\\' -> {
                        if (i >= s.length) fail()
                        when (val e = s[i++]) {
                            '"', '\\', '/' -> b.append(e)
                            'b' -> b.append('\b')
                            'f' -> b.append('\u000c')
                            'n' -> b.append('\n')
                            'r' -> b.append('\r')
                            't' -> b.append('\t')
                            'u' -> {
                                if (i + 4 > s.length) fail()
                                b.append(s.substring(i, i + 4).toIntOrNull(16)?.toChar() ?: fail())
                                i += 4
                            }
                            else -> fail()
                        }
                    }
                    else -> b.append(c)
                }
            }
        }
    }
}
