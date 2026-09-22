package dev.vcgms.aapp.vaterialfiles.app

import android.os.AsyncTask
import android.os.Build
import android.webkit.WebView
import jcifs.context.SingletonContext
import dev.vcgms.aapp.vaterialfiles.BuildConfig
import dev.vcgms.aapp.vaterialfiles.coil.initializeCoil
import dev.vcgms.aapp.vaterialfiles.filejob.fileJobNotificationTemplate
import dev.vcgms.aapp.vaterialfiles.ftpserver.ftpServerServiceNotificationTemplate
import dev.vcgms.aapp.vaterialfiles.hiddenapi.HiddenApi
import dev.vcgms.aapp.vaterialfiles.provider.FileSystemProviders
import dev.vcgms.aapp.vaterialfiles.security.SecurityManager
import dev.vcgms.aapp.vaterialfiles.settings.Settings
import dev.vcgms.aapp.vaterialfiles.storage.FtpServerAuthenticator
import dev.vcgms.aapp.vaterialfiles.storage.SftpServerAuthenticator
import dev.vcgms.aapp.vaterialfiles.storage.SmbServerAuthenticator
import dev.vcgms.aapp.vaterialfiles.storage.StorageVolumeListLiveData
import dev.vcgms.aapp.vaterialfiles.storage.WebDavServerAuthenticator
import dev.vcgms.aapp.vaterialfiles.theme.custom.CustomThemeHelper
import dev.vcgms.aapp.vaterialfiles.theme.night.NightModeHelper
import java.util.Properties
import dev.vcgms.aapp.vaterialfiles.provider.ftp.client.Client as FtpClient
import dev.vcgms.aapp.vaterialfiles.provider.sftp.client.Client as SftpClient
import dev.vcgms.aapp.vaterialfiles.provider.smb.client.Client as SmbClient
import dev.vcgms.aapp.vaterialfiles.provider.webdav.client.Client as WebDavClient

val appInitializers = listOf(
    ::disableHiddenApiChecks,
    ::initializeWebViewDebugging,
    ::initializeCoil,
    ::initializeFileSystemProviders,
    // Must run AFTER initializeFileSystemProviders: SecurityManager.initialize() reads
    // Settings.AUTH_MODE, which triggers Settings' static initializer that calls java8.nio
    // Paths.get() and therefore requires FileSystemProvider.installDefaultProvider() to have run,
    // otherwise it throws "Must initialize with FileSystemProvider.installDefaultProvider()".
    ::initializeSecurity,
    ::upgradeApp,
    ::initializeLiveDataObjects,
    ::initializeCustomTheme,
    ::initializeNightMode,
    ::createNotificationChannels
)

private fun initializeSecurity() {
    SecurityManager.initialize()
}

private fun disableHiddenApiChecks() {
    HiddenApi.disableHiddenApiChecks()
}

private fun initializeWebViewDebugging() {
    if (BuildConfig.DEBUG) {
        WebView.setWebContentsDebuggingEnabled(true)
    }
}

private fun initializeFileSystemProviders() {
    FileSystemProviders.install()
    FileSystemProviders.overflowWatchEvents = true
    // SingletonContext.init() calls NameServiceClientImpl.initCache() which connects to network.
    AsyncTask.THREAD_POOL_EXECUTOR.execute {
        SingletonContext.init(
            Properties().apply {
                setProperty("jcifs.netbios.cachePolicy", "0")
                setProperty("jcifs.smb.client.maxVersion", "SMB1")
            }
        )
    }
    FtpClient.authenticator = FtpServerAuthenticator
    SftpClient.authenticator = SftpServerAuthenticator
    SmbClient.authenticator = SmbServerAuthenticator
    WebDavClient.authenticator = WebDavServerAuthenticator
}

private fun initializeLiveDataObjects() {
    // Force initialization of LiveData objects so that it won't happen on a background thread.
    StorageVolumeListLiveData.value
    Settings.FILE_LIST_DEFAULT_DIRECTORY.value
}

private fun initializeCustomTheme() {
    CustomThemeHelper.initialize(application)
}

private fun initializeNightMode() {
    NightModeHelper.initialize(application)
}

private fun createNotificationChannels() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        notificationManager.createNotificationChannels(
            listOf(
                backgroundActivityStartNotificationTemplate.channelTemplate,
                fileJobNotificationTemplate.channelTemplate,
                ftpServerServiceNotificationTemplate.channelTemplate
            ).map { it.create(application) }
        )
    }
}
