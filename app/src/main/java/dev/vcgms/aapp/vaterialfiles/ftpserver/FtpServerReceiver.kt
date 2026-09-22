package dev.vcgms.aapp.vaterialfiles.ftpserver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.vcgms.aapp.vaterialfiles.app.application

class FtpServerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val action = intent.action) {
            ACTION_STOP -> FtpServerService.stop(context)
            else -> throw IllegalArgumentException(action)
        }
    }

    companion object {
        const val ACTION_STOP = "stop"

        fun createIntent(): Intent =
            Intent(application, FtpServerReceiver::class.java)
                .setAction(ACTION_STOP)
    }
}
