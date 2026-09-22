package dev.vcgms.aapp.vaterialfiles.security

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.vcgms.aapp.vaterialfiles.R
import dev.vcgms.aapp.vaterialfiles.databinding.SetPinDialogBinding
import dev.vcgms.aapp.vaterialfiles.settings.AuthMode
import dev.vcgms.aapp.vaterialfiles.settings.Settings

// Dialog for setting the numeric PIN used by the PASSWORD and FINGERPRINT authentication modes.
// If the user dismisses it without successfully saving a PIN, the authentication mode is reverted
// to NONE so the app is never left requiring a PIN that does not exist.
class SetPinDialogFragment : AppCompatDialogFragment() {
    private lateinit var binding: SetPinDialogBinding

    private var pinSet = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = SetPinDialogBinding.inflate(LayoutInflater.from(requireContext()))
        return MaterialAlertDialogBuilder(requireContext(), theme)
            .setTitle(R.string.auth_set_pin_title)
            .setMessage(R.string.auth_set_pin_message)
            .setView(binding.root)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }

    override fun onStart() {
        super.onStart()

        val dialog = requireDialog() as AlertDialog
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val pin = binding.pinEdit.text?.toString() ?: ""
            val confirmPin = binding.confirmPinEdit.text?.toString() ?: ""
            binding.pinLayout.error = null
            binding.confirmPinLayout.error = null
            when {
                pin.isEmpty() ->
                    binding.pinLayout.error = getString(R.string.auth_pin_empty)
                pin != confirmPin ->
                    binding.confirmPinLayout.error = getString(R.string.auth_pin_mismatch)
                else -> {
                    SecurityManager.setPin(pin)
                    pinSet = true
                    dialog.dismiss()
                }
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)

        if (!pinSet) {
            Settings.AUTH_MODE.putValue(AuthMode.NONE)
        }
    }
}
