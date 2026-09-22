package dev.vcgms.aapp.vaterialfiles.compat

import android.system.ErrnoException
import dev.vcgms.aapp.vaterialfiles.hiddenapi.RestrictedHiddenApi
import dev.vcgms.aapp.vaterialfiles.util.lazyReflectedField

@RestrictedHiddenApi
private val functionNameField by lazyReflectedField(ErrnoException::class.java, "functionName")

val ErrnoException.functionNameCompat: String
    get() = functionNameField.get(this) as String
