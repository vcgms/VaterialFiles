package dev.vcgms.aapp.vaterialfiles.util

fun Any.hash(vararg values: Any?): Int = values.contentDeepHashCode()
