package dev.vcgms.aapp.vaterialfiles.ui

import android.os.Bundle
import android.view.View
import androidx.core.view.updatePaddingRelative
import androidx.preference.ListPreference
import androidx.preference.Preference
import com.takisoft.preferencex.PreferenceFragmentCompat as TakisoftPreferenceFragmentCompat
import dev.vcgms.aapp.vaterialfiles.R

abstract class PreferenceFragmentCompat : TakisoftPreferenceFragmentCompat() {
    // @see https://github.com/Gericop/Android-Support-Preference-V7-Fix/issues/201
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (preferenceScreen == null) {
            val preferenceScreen = preferenceManager.createPreferenceScreen(requireContext())
            setPreferenceScreen(preferenceScreen)
        }

        super.onViewCreated(view, savedInstanceState)

        // Add breathing room below the last preference so content never touches the edge.
        listView.clipToPadding = false
        listView.updatePaddingRelative(
            bottom = resources.getDimensionPixelSize(R.dimen.preference_list_bottom_padding)
        )
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        if (parentFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG) == null
            && preference is ListPreference) {
            displayPreferenceDialog(MaterialListPreferenceDialogFragmentCompat(), preference.key)
        } else {
            super.onDisplayPreferenceDialog(preference)
        }
    }

    companion object {
        // @see PreferenceFragmentCompat.DIALOG_FRAGMENT_TAG
        private const val DIALOG_FRAGMENT_TAG = "androidx.preference.PreferenceFragment.DIALOG"
    }
}
