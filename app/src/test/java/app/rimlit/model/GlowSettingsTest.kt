package app.rimlit.model

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GlowSettingsTest {

    @Test
    fun autoColoursAreExtractedByTheCaller() {
        assertNull(GlowSettings(colorMode = ColorMode.AUTO).paletteFor("com.example.chat"))
    }

    @Test
    fun previewsShowAsManyAutoColoursAsChosen() {
        for (count in GlowSettings.Ranges.autoColorCount) {
            val settings = GlowSettings(autoColors = AutoColors(count = count))
            val palette = settings.previewSpec().palette as GlowPalette.Colors
            assertEquals(count, palette.argb.size)
        }
    }
}
