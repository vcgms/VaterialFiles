package dev.vcgms.aapp.vaterialfiles.app

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import dev.vcgms.aapp.vaterialfiles.security.SecurityManager
import dev.vcgms.aapp.vaterialfiles.theme.custom.CustomThemeHelper
import dev.vcgms.aapp.vaterialfiles.theme.display.DisplaySizeHelper
import dev.vcgms.aapp.vaterialfiles.theme.night.NightModeHelper

abstract class AppActivity : AppCompatActivity() {
    private var isDelegateCreated = false

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(DisplaySizeHelper.wrap(newBase))
    }

    override fun getDelegate(): AppCompatDelegate {
        val delegate = super.getDelegate()

        if (!isDelegateCreated) {
            isDelegateCreated = true
            NightModeHelper.apply(this)
        }
        return delegate
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        CustomThemeHelper.apply(this)
        DisplaySizeHelper.apply(this)

        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()

        SecurityManager.maybeLaunchAuth(this)
    }

    override fun onDestroy() {
        DisplaySizeHelper.unapply(this)

        super.onDestroy()
    }

    override fun onSupportNavigateUp(): Boolean {
        if (!super.onSupportNavigateUp()) {
            finish()
        }
        return true
    }
}
