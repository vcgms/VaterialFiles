package dev.vcgms.aapp.vaterialfiles.security

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dev.vcgms.aapp.vaterialfiles.R
import dev.vcgms.aapp.vaterialfiles.app.application
import dev.vcgms.aapp.vaterialfiles.app.defaultSharedPreferences
import dev.vcgms.aapp.vaterialfiles.settings.AuthMode
import dev.vcgms.aapp.vaterialfiles.settings.Settings
import dev.vcgms.aapp.vaterialfiles.util.valueCompat
import java.security.MessageDigest
import java.security.SecureRandom

// Central place for the app lock: watches whole-app foreground/background transitions through
// ProcessLifecycleOwner so that authentication can be required on cold start and every time the
// app returns to the foreground, and stores/verifies the PIN as a salted SHA-256 hash.
object SecurityManager : DefaultLifecycleObserver {
    private const val SALT_LENGTH = 16

    @Volatile
    private var pendingAuth = false

    val authMode: AuthMode
        get() = Settings.AUTH_MODE.valueCompat

    fun initialize() {
        pendingAuth = authMode != AuthMode.NONE
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStop(owner: LifecycleOwner) {
        if (authMode != AuthMode.NONE) {
            pendingAuth = true
        }
    }

    fun maybeLaunchAuth(activity: android.app.Activity) {
        if (activity is AuthActivity) {
            return
        }
        if (authMode == AuthMode.NONE) {
            pendingAuth = false
            return
        }
        if (!pendingAuth) {
            return
        }
        activity.startActivity(Intent(activity, AuthActivity::class.java))
    }

    fun onAuthSuccess() {
        pendingAuth = false
    }

    val isDeviceSecure: Boolean
        get() {
            val keyguardManager =
                application.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            return keyguardManager.isDeviceSecure
        }

    val isPinSet: Boolean
        get() = pinHash != null

    fun setPin(pin: String) {
        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val hash = sha256(pin, salt)
        defaultSharedPreferences.edit {
            putString(saltKey, salt.toHex())
            putString(hashKey, hash.toHex())
        }
    }

    fun verifyPin(pin: String): Boolean {
        val saltHex = pinSalt ?: return false
        val expectedHash = pinHash ?: return false
        return sha256(pin, saltHex.toHexBytes()).toHex() == expectedHash
    }

    fun clearPin() {
        defaultSharedPreferences.edit {
            remove(saltKey)
            remove(hashKey)
        }
    }

    private val saltKey: String
        get() = application.getString(R.string.pref_key_auth_pin_salt)

    private val hashKey: String
        get() = application.getString(R.string.pref_key_auth_pin_hash)

    private val pinSalt: String?
        get() = defaultSharedPreferences.getString(saltKey, null)

    private val pinHash: String?
        get() = defaultSharedPreferences.getString(hashKey, null)

    private fun sha256(pin: String, salt: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").apply { update(salt) }
            .digest(pin.toByteArray(Charsets.UTF_8))

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.toHexBytes(): ByteArray {
        val result = ByteArray(length / 2)
        for (index in result.indices) {
            result[index] = ((Character.digit(this[index * 2], 16) shl 4) +
                Character.digit(this[index * 2 + 1], 16)).toByte()
        }
        return result
    }
}
