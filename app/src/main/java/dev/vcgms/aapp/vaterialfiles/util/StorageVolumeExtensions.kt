package dev.vcgms.aapp.vaterialfiles.util

import android.os.storage.StorageVolume
import dev.vcgms.aapp.vaterialfiles.compat.directoryCompat

val StorageVolume.isMounted: Boolean
    get() = directoryCompat != null
