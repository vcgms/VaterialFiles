package dev.vcgms.aapp.vaterialfiles.settings

import android.os.Build
import android.os.Bundle
import dev.vcgms.aapp.vaterialfiles.R
import dev.vcgms.aapp.vaterialfiles.security.SecurityManager
import dev.vcgms.aapp.vaterialfiles.security.SetPinDialogFragment
import dev.vcgms.aapp.vaterialfiles.theme.custom.CustomThemeHelper
import dev.vcgms.aapp.vaterialfiles.theme.custom.ThemeColor
import dev.vcgms.aapp.vaterialfiles.theme.display.DisplaySizeHelper
import dev.vcgms.aapp.vaterialfiles.theme.night.NightMode
import dev.vcgms.aapp.vaterialfiles.theme.night.NightModeHelper
import dev.vcgms.aapp.vaterialfiles.ui.PreferenceFragmentCompat
import dev.vcgms.aapp.vaterialfiles.util.show
import dev.vcgms.aapp.vaterialfiles.util.showToast

class SettingsPreferenceFragment : PreferenceFragmentCompat() {
    private lateinit var localePreference: LocalePreference

    override fun onCreatePreferencesFix(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.settings)

        localePreference = preferenceScreen.findPreference(getString(R.string.pref_key_locale))!!
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            localePreference.setApplicationLocalesPre33 = { locales ->
                val activity = requireActivity() as SettingsActivity
                activity.setApplicationLocalesPre33(locales)
            }
        }
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
        Settings.THEME_COLOR.observe(viewLifecycleOwner, this::onThemeColorChanged)
        Settings.MATERIAL_DESIGN_3.observe(viewLifecycleOwner, this::onMaterialDesign3Changed)
        Settings.DISPLAY_SIZE.observe(viewLifecycleOwner, this::onDisplaySizeChanged)
        Settings.NIGHT_MODE.observe(viewLifecycleOwner, this::onNightModeChanged)
        Settings.BLACK_NIGHT_MODE.observe(viewLifecycleOwner, this::onBlackNightModeChanged)
        Settings.AUTH_MODE.observe(viewLifecycleOwner, this::onAuthModeChanged)
    }

    private fun onThemeColorChanged(themeColor: ThemeColor) {
        CustomThemeHelper.sync()
    }

    private fun onMaterialDesign3Changed(isMaterialDesign3: Boolean) {
        CustomThemeHelper.sync()
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
