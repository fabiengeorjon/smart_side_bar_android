package com.local.edgebar.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PinLogicTest {
    private val a = "com.example.a"
    private val b = "com.example.b"
    private val c = "org.example.c"

    @Test fun `pin appends once and keeps order`() {
        val pins = PinLogic.pin(PinLogic.pin(PinLogic.pin(emptyList(), a), b), a)
        assertEquals(listOf(a, b), pins)
    }

    @Test fun `pin rejects invalid package names`() {
        assertEquals(emptyList<String>(), PinLogic.pin(emptyList(), "not a package"))
        assertEquals(emptyList<String>(), PinLogic.pin(emptyList(), "single"))
        assertEquals(emptyList<String>(), PinLogic.pin(emptyList(), ""))
    }

    @Test fun `unpin removes only the target`() {
        assertEquals(listOf(a, c), PinLogic.unpin(listOf(a, b, c), b))
        assertEquals(listOf(a), PinLogic.unpin(listOf(a), "com.missing.app"))
    }

    @Test fun `toggle pins then unpins`() {
        val once = PinLogic.toggle(listOf(a), b)
        assertEquals(listOf(a, b), once)
        assertEquals(listOf(a), PinLogic.toggle(once, b))
    }

    @Test fun `move up and down is clamped`() {
        val pins = listOf(a, b, c)
        assertEquals(listOf(b, a, c), PinLogic.move(pins, b, -1))
        assertEquals(listOf(a, c, b), PinLogic.move(pins, b, +1))
        assertEquals(listOf(c, a, b), PinLogic.move(pins, c, -10))
        assertSame(pins, PinLogic.move(pins, a, -1))
        assertSame(pins, PinLogic.move(pins, "com.unknown.x", 1))
    }

    @Test fun `moveTo handles out of range indices`() {
        val pins = listOf(a, b, c)
        assertEquals(listOf(b, c, a), PinLogic.moveTo(pins, 0, 99))
        assertSame(pins, PinLogic.moveTo(pins, 5, 0))
    }

    @Test fun `prune drops uninstalled apps and keeps order`() {
        val installed = setOf(a, c)
        val result = PinLogic.prune(listOf(c, b, a), installed::contains)
        assertEquals(listOf(c, a), result.kept)
        assertEquals(listOf(b), result.removed)
        assertTrue(result.changed)
        assertFalse(PinLogic.prune(listOf(a), installed::contains).changed)
    }

    @Test fun `encode decode round trip`() {
        val pins = listOf(c, a, b)
        assertEquals(pins, PinLogic.decode(PinLogic.encode(pins)))
        assertEquals(emptyList<String>(), PinLogic.decode(""))
        assertEquals(emptyList<String>(), PinLogic.decode(null))
    }

    @Test fun `decode is tolerant of junk and duplicates`() {
        assertEquals(listOf(a, b), PinLogic.decode("  $a \n\n$b\n$a\nbad entry\n"))
    }
}
