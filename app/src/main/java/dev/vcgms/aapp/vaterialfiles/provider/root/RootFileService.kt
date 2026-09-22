package dev.vcgms.aapp.vaterialfiles.provider.root

import android.annotation.SuppressLint
import android.content.Context
import android.os.Process
import android.util.Log
import dev.vcgms.aapp.vaterialfiles.BuildConfig
import dev.vcgms.aapp.vaterialfiles.compat.UserHandleCompat
import dev.vcgms.aapp.vaterialfiles.provider.FileSystemProviders
import dev.vcgms.aapp.vaterialfiles.provider.remote.RemoteFileService
import dev.vcgms.aapp.vaterialfiles.provider.remote.RemoteInterface
import dev.vcgms.aapp.vaterialfiles.util.lazyReflectedMethod

// We are expanding our root file service to shell UID, but let's keep the original name since it's
// a bit awkward to express root-or-shell-UID in one or two words.
val isRunningAsRoot =
    when (UserHandleCompat.getAppId(Process.myUid())) {
        Process.ROOT_UID, Process.SHELL_UID -> true
        else -> false
    }

@SuppressLint("StaticFieldLeak")
lateinit var rootContext: Context private set

object RootFileService : RemoteFileService(
    RemoteInterface {
        if (ShizukuFileServiceLauncher.isAvailable()) {
            ShizukuFileServiceLauncher.launchService()
        } else {
            LibSuFileServiceLauncher.launchService()
        }
    }
) {
    const val TIMEOUT_MILLIS = 15 * 1000L

    private val LOG_TAG = RootFileService::class.java.simpleName

    // Not actually restricted because there's no restriction when running as root.
    //@RestrictedHiddenApi
    private val activityThreadCurrentActivityThreadMethod by lazyReflectedMethod(
        "android.app.ActivityThread", "currentActivityThread"
    )
    //@RestrictedHiddenApi
    private val activityThreadGetSystemContextMethod by lazyReflectedMethod(
        "android.app.ActivityThread", "getSystemContext"
    )

    fun main() {
        Log.i(LOG_TAG, "Creating package context")
        rootContext = createPackageContext(BuildConfig.APPLICATION_ID)
        Log.i(LOG_TAG, "Installing file system providers")
        FileSystemProviders.install()
        FileSystemProviders.overflowWatchEvents = true
    }

    private fun createPackageContext(packageName: String): Context {
        val activityThread = activityThreadCurrentActivityThreadMethod.invoke(null)
        val systemContext = activityThreadGetSystemContextMethod.invoke(activityThread) as Context
        return systemContext.createPackageContext(
            packageName, Context.CONTEXT_IGNORE_SECURITY or Context.CONTEXT_INCLUDE_CODE
        )
    }
}
