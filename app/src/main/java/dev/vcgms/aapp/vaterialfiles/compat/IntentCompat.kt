package dev.vcgms.aapp.vaterialfiles.compat

import android.content.Intent
import dev.vcgms.aapp.vaterialfiles.util.andInv

fun Intent.removeFlagsCompat(flags: Int) {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        removeFlags(flags)
    } else {
        setFlags(this.flags andInv flags)
    }
}
