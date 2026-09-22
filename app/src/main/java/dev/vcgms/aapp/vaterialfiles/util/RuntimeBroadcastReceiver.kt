package dev.vcgms.aapp.vaterialfiles.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import dev.vcgms.aapp.vaterialfiles.compat.registerReceiverCompat

class RuntimeBroadcastReceiver(
    private val filter: IntentFilter,
    private val receiver: BroadcastReceiver,
    private val context: Context,
    private val flags: Int = ContextCompat.RECEIVER_NOT_EXPORTED
) {
    fun register() {
        context.registerReceiverCompat(receiver, filter, flags)
    }

    fun unregister() {
        context.unregisterReceiver(receiver)
    }
}
