package dev.vcgms.aapp.vaterialfiles.settings

import android.os.Build
import android.os.Bundle
import androidx.preference.Preference
import dev.vcgms.aapp.vaterialfiles.R
import dev.vcgms.aapp.vaterialfiles.security.SecurityManager
import dev.vcgms.aapp.vaterialfiles.security.SetPinDialogFragment
import dev.vcgms.aapp.vaterialfiles.theme.custom.CustomThemeHelper
import dev.vcgms.aapp.vaterialfiles.theme.display.DisplaySizeHelper
import dev.vcgms.aapp.vaterialfiles.theme.night.NightMode
import dev.vcgms.aapp.vaterialfiles.theme.night.NightModeHelper
import dev.vcgms.aapp.vaterialfiles.ui.PreferenceFragmentCompat
import dev.vcgms.aapp.vaterialfiles.util.show
import dev.vcgms.aapp.vaterialfiles.util.showToast
import dev.vcgms.aapp.vaterialfiles.util.valueCompat

class SettingsPreferenceFragment : PreferenceFragmentCompat() {
    private lateinit var localePreference: LocalePreference

    private lateinit var blackNightModePreference: Preference

    private lateinit var appearanceCategory: CollapsiblePreferenceCategory

    private lateinit var playbackCategory: CollapsiblePreferenceCategory

    private lateinit var screenshotJpgQualityPreference: Preference

    override fun onCreatePreferencesFix(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.settings)

        localePreference = preferenceScreen.findPreference(getString(R.string.pref_key_locale))!!
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            localePreference.setApplicationLocalesPre33 = { locales ->
                val activity = requireActivity() as SettingsActivity
                activity.setApplicationLocalesPre33(locales)
            }
        }
        blackNightModePreference =
            preferenceScreen.findPreference(getString(R.string.pref_key_black_night_mode))!!
        appearanceCategory = preferenceScreen
            .findPreference(getString(R.string.pref_key_settings_category_appearance))!!
        appearanceCategory.onExpandedChanged = { updateBlackNightModePreferenceVisible() }
        playbackCategory = preferenceScreen
            .findPreference(getString(R.string.pref_key_settings_category_playback))!!
        playbackCategory.onExpandedChanged = { updateScreenshotJpgQualityVisible() }
        screenshotJpgQualityPreference =
            preferenceScreen.findPreference(getString(R.string.pref_key_screenshot_jpg_quality))!!
        // Collapse every category by default; the expanded state survives configuration changes.
        for (index in 0 until preferenceScreen.preferenceCount) {
            (preferenceScreen.getPreference(index) as? CollapsiblePreferenceCategory)
                ?.isExpanded = false
        }
        updateBlackNightModePreferenceVisible()
        updateScreenshotJpgQualityVisible()
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        val viewLifecycleOwner = viewLifecycleOwner
        // The following may end up passing the same lambda instance to the observer because it has
        // no capture, and result in an IllegalArgumentException "Cannot add the same observer with
        // different lifecycles" if activity is finished and instantly started again. To work around
        // this, always use an instance method reference.
        // https://stackoverflow.com/a/27524543
        //Settings.THEME_COLOR.observe(viewLifecycleOwner) { CustomThemeHelper.sync() }
        //Settings.MATERIAL_DESIGN_3.observe(viewLifecycleOwner) { CustomThemeHelper.sync() }
        //Settings.NIGHT_MODE.observe(viewLifecycleOwner) { NightModeHelper.sync() }
        //Settings.BLACK_NIGHT_MODE.observe(viewLifecycleOwner) { CustomThemeHelper.sync() }
        Settings.MATERIAL_YOU.observe(viewLifecycleOwner, this::onMaterialYouChanged)
        Settings.DISPLAY_SIZE.observe(viewLifecycleOwner, this::onDisplaySizeChanged)
        Settings.NIGHT_MODE.observe(viewLifecycleOwner, this::onNightModeChanged)
        Settings.BLACK_NIGHT_MODE.observe(viewLifecycleOwner, this::onBlackNightModeChanged)
        Settings.AUTH_MODE.observe(viewLifecycleOwner, this::onAuthModeChanged)
        Settings.SCREENSHOT_FORMAT.observe(viewLifecycleOwner) { updateScreenshotJpgQualityVisible() }
    }

    private fun onMaterialYouChanged(materialYou: Boolean) {
        CustomThemeHelper.sync()
        updateBlackNightModePreferenceVisible()
    }

    private fun updateBlackNightModePreferenceVisible() {
        blackNightModePreference.isVisible =
            appearanceCategory.isExpanded && !Settings.MATERIAL_YOU.valueCompat
    }

    // The JPG quality slider only applies when screenshots are saved as JPG. The category's
    // collapse state also controls the child visibility, so both must be honored.
    private fun updateScreenshotJpgQualityVisible() {
        screenshotJpgQualityPreference.isVisible =
            playbackCategory.isExpanded && Settings.SCREENSHOT_FORMAT.valueCompat == ScreenshotFormat.JPG
    }

    private fun onDisplaySizeChanged(displaySize: DisplaySize) {
        DisplaySizeHelper.sync()
    }

    private fun onNightModeChanged(nightMode: NightMode) {
        NightModeHelper.sync()
    }

    private fun onBlackNightModeChanged(blackNightMode: Boolean) {
        CustomThemeHelper.sync()
    }

    private fun onAuthModeChanged(authMode: AuthMode) {
        when (authMode) {
            AuthMode.PASSWORD, AuthMode.FINGERPRINT ->
                if (!SecurityManager.isPinSet) {
                    SetPinDialogFragment().show(this)
                }
            AuthMode.SYSTEM ->
                if (!SecurityManager.isDeviceSecure) {
                    showToast(R.string.auth_no_device_credential)
                    Settings.AUTH_MODE.putValue(AuthMode.NONE)
                }
            AuthMode.NONE -> {}
        }
    }

    override fun onResume() {
        super.onResume()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Refresh locale preference summary because we aren't notified for an external change
            // between system default and the locale that's the current system default.
            localePreference.notifyChanged()
        }
    }
}
