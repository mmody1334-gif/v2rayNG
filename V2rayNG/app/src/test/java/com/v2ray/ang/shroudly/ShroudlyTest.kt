package com.v2ray.ang.shroudly

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class ShroudlyTest {

    @Test
    fun testBuildArgsDefault() {
        val args = ShroudlyEngine.buildArgs(
            port = 12345,
            authFile = null,
            blockIps = listOf("1.1.1.1", "8.8.8.8"),
            strategy = "auto",
            auto = false,
            quicOff = true
        )

        assertTrue(args.contains("--listen"))
        assertTrue(args.contains("127.0.0.1"))
        assertTrue(args.contains("--port"))
        assertTrue(args.contains("12345"))
        assertTrue(args.contains("--strategy"))
        assertTrue(args.contains("tlsrec(sni+1)"))
        assertTrue(args.contains("--auto"))
        assertTrue(args.contains("--doh"))
        assertTrue(args.contains("--drop-quic"))
        assertTrue(args.contains("--block-ip"))
        assertTrue(args.contains("1.1.1.1"))
        assertTrue(args.contains("8.8.8.8"))
    }

    @Test
    fun testBuildArgsCustomStrategy() {
        val args = ShroudlyEngine.buildArgs(
            port = 23456,
            authFile = File("/tmp/auth.txt"),
            blockIps = emptyList(),
            strategy = "split(sni)",
            auto = false,
            quicOff = false
        )

        assertTrue(args.contains("23456"))
        assertTrue(args.contains("split(sni)"))
        assertFalse(args.contains("--auto"))
        assertFalse(args.contains("--drop-quic"))
        assertTrue(args.contains(SocksAuth.OPTION))
        assertTrue(args.contains(File("/tmp/auth.txt").path))
    }

    @Test
    fun testSocksAuthGeneration() {
        val auth = SocksAuth.random()
        assertEquals(16, auth.user.length)
        assertEquals(32, auth.pass.length)
        val fileContent = auth.line()
        assertEquals("${auth.user}:${auth.pass}\n", fileContent)
    }

    @Test
    fun testFlatJsonAndEventParsing() {
        val readyLine = """{"type":"ready","port":1080,"techniques":3,"pid":42}"""
        val readyEv = ShroudlyEvent.parse(readyLine)
        assertTrue(readyEv is ShroudlyEvent.Ready)
        val ready = readyEv as ShroudlyEvent.Ready
        assertEquals(1080, ready.port)
        assertEquals(3, ready.techniques)
        assertEquals(42, ready.pid)

        val bypassLine = """{"type":"bypass","host":"example.com","strategy":"split(sni)","attempts":1}"""
        val bypassEv = ShroudlyEvent.parse(bypassLine)
        assertTrue(bypassEv is ShroudlyEvent.Host)
        val host = bypassEv as ShroudlyEvent.Host
        assertEquals("bypass", host.kind)
        assertEquals("example.com", host.host)
        assertEquals("split(sni)", host.strategy)

        val errLine = """{"type":"error","msg":"connection refused","code":111}"""
        val errEv = ShroudlyEvent.parse(errLine)
        assertTrue(errEv is ShroudlyEvent.Error)
        assertEquals("connection refused", (errEv as ShroudlyEvent.Error).message)

        val rawText = "Non json text"
        val textEv = ShroudlyEvent.parse(rawText)
        assertTrue(textEv is ShroudlyEvent.Text)
    }
}
