package dev.vcgms.aapp.vaterialfiles.theme.display

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatActivity
import dev.vcgms.aapp.vaterialfiles.settings.Settings
import dev.vcgms.aapp.vaterialfiles.util.valueCompat
import kotlin.math.roundToInt

// Scales all in-app UI proportionally by adjusting Configuration.densityDpi (which affects both dp
// and sp). Every activity extends AppActivity, which wraps its base context through wrap() and
// registers itself here, so a change can recreate all live activities at once via sync().
object DisplaySizeHelper {
    private val activities = mutableSetOf<AppCompatActivity>()

    // Tracks the scale currently applied to live activities so that the initial emission from
    // observing the setting (which always fires once) does not needlessly recreate everything.
    private var currentScale: Float = Settings.DISPLAY_SIZE.valueCompat.scale

    fun apply(activity: AppCompatActivity) {
        activities += activity
    }

    fun unapply(activity: AppCompatActivity) {
        activities -= activity
    }

    fun sync() {
        val scale = Settings.DISPLAY_SIZE.valueCompat.scale
        if (scale == currentScale) {
            return
        }
        currentScale = scale
        for (activity in activities.toList()) {
            activity.recreate()
        }
    }

    fun wrap(context: Context): Context {
        val scale = Settings.DISPLAY_SIZE.valueCompat.scale
        if (scale == 1f) {
            return context
        }
        val configuration = Configuration(context.resources.configuration)
        // Reset fontScale so the app display size is the single source of truth for text scaling
        // and does not stack on top of the system font size.
        configuration.fontScale = 1f
        configuration.densityDpi = (context.resources.configuration.densityDpi * scale).roundToInt()
        return context.createConfigurationContext(configuration)
    }
}
