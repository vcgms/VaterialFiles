package dev.vcgms.aapp.vaterialfiles.terminal

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import dev.vcgms.aapp.vaterialfiles.app.packageManager
import dev.vcgms.aapp.vaterialfiles.util.startActivitySafe

object Terminal {
    fun open(path: String, context: Context) {
        val componentName =
            packageManager.queryIntentActivities(Intent(Intent.ACTION_SEND).setType("*/*"), 0)
                .firstOrNull { it.activityInfo.name.endsWith(".TermHere") }?.activityInfo
                ?.let { ComponentName(it.packageName, it.name) }
                ?: ComponentName("jackpal.androidterm", "jackpal.androidterm.TermHere")
        val intent = Intent()
            .setComponent(componentName)
            .setAction(Intent.ACTION_SEND)
            .putExtra(Intent.EXTRA_STREAM, Uri.parse(path))
        context.startActivitySafe(intent)
    }
}
