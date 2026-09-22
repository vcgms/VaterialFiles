package dev.vcgms.aapp.vaterialfiles.security

import android.app.KeyguardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import dev.vcgms.aapp.vaterialfiles.R
import dev.vcgms.aapp.vaterialfiles.app.AppActivity
import dev.vcgms.aapp.vaterialfiles.databinding.AuthActivityBinding
import dev.vcgms.aapp.vaterialfiles.settings.AuthMode

// Full-screen opaque lock activity that hides the underlying content until the user authenticates.
// Launched by SecurityManager on cold start and every time the app returns to the foreground.
class AuthActivity : AppActivity() {
    private lateinit var binding: AuthActivityBinding

    private val deviceCredentialLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            onAuthSuccess()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = AuthActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.unlockButton.setOnClickListener { verifyPin() }

        when (SecurityManager.authMode) {
            AuthMode.PASSWORD -> showPinInput()
            AuthMode.FINGERPRINT -> showBiometricPrompt()
            AuthMode.SYSTEM -> showDeviceCredential()
            AuthMode.NONE -> onAuthSuccess()
        }
    }

    private fun showPinInput() {
        binding.pinLayout.isVisible = true
        binding.unlockButton.isVisible = true
        binding.messageText.setText(R.string.auth_enter_pin)
    }

    private fun verifyPin() {
        val pin = binding.pinEdit.text?.toString() ?: ""
        if (SecurityManager.verifyPin(pin)) {
            onAuthSuccess()
        } else {
            binding.pinLayout.error = getString(R.string.auth_incorrect_pin)
        }
    }

    private fun showBiometricPrompt() {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK
        if (BiometricManager.from(this).canAuthenticate(authenticators)
            != BiometricManager.BIOMETRIC_SUCCESS) {
            showPinInputIfPossible()
            return
        }
        val biometricPrompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult
                ) {
                    onAuthSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // Covers the negative button ("Use PIN") and unrecoverable errors alike.
                    showPinInputIfPossible()
                }
            })
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.auth_biometric_title))
            .setSubtitle(getString(R.string.auth_biometric_subtitle))
            .setNegativeButtonText(getString(R.string.auth_use_pin))
            .setAllowedAuthenticators(authenticators)
            .build()
        biometricPrompt.authenticate(promptInfo)
    }

    private fun showDeviceCredential() {
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (!keyguardManager.isDeviceSecure) {
            Toast.makeText(this, R.string.auth_no_device_credential, Toast.LENGTH_LONG).show()
            showPinInputIfPossible()
            return
        }
        @Suppress("DEPRECATION")
        val intent = keyguardManager.createConfirmDeviceCredentialIntent(
            getString(R.string.auth_verify_title), getString(R.string.auth_verify_message))
        if (intent != null) {
            deviceCredentialLauncher.launch(intent)
        } else {
            showPinInputIfPossible()
        }
    }

    // Only fall back to the PIN input when a PIN actually exists, otherwise the lock could never be
    // dismissed; in that rare case we simply stay locked until a valid authenticator is available.
    private fun showPinInputIfPossible() {
        if (SecurityManager.isPinSet) {
            showPinInput()
        }
    }

    private fun onAuthSuccess() {
        SecurityManager.onAuthSuccess()
        finish()
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        // Intentionally empty: the lock screen must not be dismissible via back press.
    }
}
