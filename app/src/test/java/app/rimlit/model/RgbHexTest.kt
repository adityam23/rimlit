package app.rimlit.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RgbHexTest {
    @Test
    fun formatRoundTrips() {
        val argb = 0xFF00BCD4.toInt()
        assertEquals("#00BCD4", RgbHex.format(argb))
        assertEquals(argb, RgbHex.parse(RgbHex.format(argb)))
    }

    @Test
    fun formatIgnoresAlpha() {
        assertEquals("#00BCD4", RgbHex.format(0x8000BCD4.toInt()))
        assertEquals("#000000", RgbHex.format(0))
    }

    @Test
    fun parseAcceptsAnyCaseWithOrWithoutHashAndSpaces() {
        val expected = 0xFF00BCD4.toInt()
        assertEquals(expected, RgbHex.parse("#00bcd4"))
        assertEquals(expected, RgbHex.parse("00BCD4"))
        assertEquals(expected, RgbHex.parse(" #00BCD4 "))
    }

    @Test
    fun parseIsOpaque() {
        assertEquals(0xFF000000.toInt(), RgbHex.parse("#000000"))
        assertEquals(0xFFFFFFFF.toInt(), RgbHex.parse("ffffff"))
    }

    @Test
    fun parseRejectsAnythingElse() {
        listOf("", "#", "#12345", "#1234567", "#GGGGGG", "#00BCD4FF", "0x00BCD4", "#FFF").forEach {
            assertNull(RgbHex.parse(it), "\"$it\"")
        }
    }
}
