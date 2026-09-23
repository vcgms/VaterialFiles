package dev.vcgms.aapp.vaterialfiles.theme.custom

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.annotation.StyleRes
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.color.DynamicColors
import dev.vcgms.aapp.vaterialfiles.R
import dev.vcgms.aapp.vaterialfiles.compat.recreateCompat
import dev.vcgms.aapp.vaterialfiles.compat.setThemeCompat
import dev.vcgms.aapp.vaterialfiles.compat.themeResIdCompat
import dev.vcgms.aapp.vaterialfiles.settings.Settings
import dev.vcgms.aapp.vaterialfiles.theme.night.NightModeHelper
import dev.vcgms.aapp.vaterialfiles.util.SimpleActivityLifecycleCallbacks
import dev.vcgms.aapp.vaterialfiles.util.valueCompat

object CustomThemeHelper {
    private val activityBaseThemes = mutableMapOf<Activity, Int>()
    private val activityMaterialYou = mutableMapOf<Activity, Boolean>()

    fun initialize(application: Application) {
        application.registerActivityLifecycleCallbacks(object : SimpleActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                check(activityBaseThemes.containsKey(activity)) {
                    "Activity must extend AppActivity: $activity"
                }
            }

            override fun onActivityDestroyed(activity: Activity) {
                activityBaseThemes.remove(activity)
                activityMaterialYou.remove(activity)
            }
        })
    }

    fun apply(activity: Activity) {
        val baseThemeRes = activity.themeResIdCompat
        activityBaseThemes[activity] = baseThemeRes
        val customThemeRes = getCustomThemeRes(baseThemeRes, activity)
        activity.setThemeCompat(customThemeRes)
        // Material You (dynamic color) is layered on top of the Material 3 base theme and must be
        // applied before the activity inflates its content.
        val materialYou = Settings.MATERIAL_YOU.valueCompat
        activityMaterialYou[activity] = materialYou
        if (materialYou) {
            DynamicColors.applyToActivityIfAvailable(activity)
        }
    }

    fun sync() {
        for ((activity, baseThemeRes) in activityBaseThemes) {
            val currentThemeRes = activity.themeResIdCompat
            val customThemeRes = getCustomThemeRes(baseThemeRes, activity)
            val materialYouChanged =
                activityMaterialYou[activity] != Settings.MATERIAL_YOU.valueCompat
            // Dynamic color is baked in at theme time, so toggling Material You needs a recreate.
            if (materialYouChanged) {
                activity.recreateCompat()
                continue
            }
            if (currentThemeRes == customThemeRes) {
                continue
            }
            // Ignore ".Black" theme changes when not in night mode.
            if (!NightModeHelper.isInNightMode(activity as AppCompatActivity)
                && isBlackThemeChange(currentThemeRes, customThemeRes, activity)) {
                continue
            }
            if (activity is OnThemeChangedListener) {
                (activity as OnThemeChangedListener).onThemeChanged(customThemeRes)
            } else {
                activity.recreateCompat()
            }
        }
    }

    private fun getCustomThemeRes(@StyleRes baseThemeRes: Int, context: Context): Int {
        val resources = context.resources
        val baseThemeName = resources.getResourceName(baseThemeRes)
        // The app is Material 3 only now; every base theme maps onto its Material 3 variant.
        val defaultThemeName = resources.getResourceEntryName(R.style.Theme_MaterialFiles)
        val material3ThemeName =
            resources.getResourceEntryName(R.style.Theme_MaterialFiles_Material3)
        val customThemeName = baseThemeName.replace(defaultThemeName, material3ThemeName) +
            if (Settings.BLACK_NIGHT_MODE.valueCompat) ".Black" else ""
        return resources.getIdentifier(customThemeName, null, null)
    }

    private fun isBlackThemeChange(
        @StyleRes themeRes1: Int,
        @StyleRes themeRes2: Int,
        context: Context
    ): Boolean {
        val resources = context.resources
        val themeName1 = resources.getResourceName(themeRes1)
        val themeName2 = resources.getResourceName(themeRes2)
        return themeName1 == "$themeName2.Black" || themeName2 == "$themeName1.Black"
    }

    interface OnThemeChangedListener {
        fun onThemeChanged(@StyleRes theme: Int)
    }
}
