package dev.vcgms.aapp.vaterialfiles.compat

import android.content.pm.ApplicationInfo
import android.os.Build
import dev.vcgms.aapp.vaterialfiles.hiddenapi.RestrictedHiddenApi
import dev.vcgms.aapp.vaterialfiles.util.lazyReflectedField

@RestrictedHiddenApi
private val versionCodeField by lazyReflectedField(ApplicationInfo::class.java, "versionCode")

@RestrictedHiddenApi
private val longVersionCodeField by lazyReflectedField(
    ApplicationInfo::class.java, "longVersionCode"
)

val ApplicationInfo.longVersionCodeCompat: Long
    get() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            longVersionCodeField.getLong(this)
        } else {
            versionCodeField.getInt(this).toLong()
        }
