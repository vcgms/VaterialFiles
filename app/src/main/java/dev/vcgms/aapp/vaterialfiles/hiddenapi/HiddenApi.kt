package dev.vcgms.aapp.vaterialfiles.hiddenapi

import android.os.Build

object HiddenApi {
    fun disableHiddenApiChecks() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            System.loadLibrary("hiddenapi")
        }
    }
}
