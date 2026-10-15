package app.rimlit.ui.glow

import android.os.Build
import android.view.WindowManager

/**
 * Lets a glow window draw into the display cutout so the border reaches the physical screen edge.
 * `ALWAYS` is API 30+; on API 29 `SHORT_EDGES` covers the portrait notch/punch-hole case.
 */
fun WindowManager.LayoutParams.drawIntoCutout() {
    layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
    } else {
        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }
}
