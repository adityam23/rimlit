package app.rimlit.notify

import app.rimlit.engine.FADE_IN_MS
import app.rimlit.engine.OPEN_TIMEOUT_MS
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GlowNotificationsTest {

    @Test
    fun aShortGlowsNotificationOutlivesItsRunUp() {
        val runUp = NotificationColorExtractor.IMAGE_LOAD_TIMEOUT_MS + OPEN_TIMEOUT_MS + FADE_IN_MS
        assertEquals(300L + runUp, GlowNotifications.timeoutAfterMs(300L))
        assertTrue(GlowNotifications.timeoutAfterMs(300L) > runUp)
    }
}
