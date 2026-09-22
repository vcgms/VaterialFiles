package dev.vcgms.aapp.vaterialfiles.filelist

import android.content.Intent
import android.os.Bundle
import java8.nio.file.Path
import dev.vcgms.aapp.vaterialfiles.app.AppActivity
import dev.vcgms.aapp.vaterialfiles.app.application
import dev.vcgms.aapp.vaterialfiles.file.MimeType
import dev.vcgms.aapp.vaterialfiles.file.asMimeTypeOrNull
import dev.vcgms.aapp.vaterialfiles.file.fileProviderUri
import dev.vcgms.aapp.vaterialfiles.filejob.FileJobService
import dev.vcgms.aapp.vaterialfiles.provider.archive.isArchivePath
import dev.vcgms.aapp.vaterialfiles.util.createViewIntent
import dev.vcgms.aapp.vaterialfiles.util.extraPath
import dev.vcgms.aapp.vaterialfiles.util.startActivitySafe

class OpenFileActivity : AppActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = intent
        val path = intent.extraPath
        val mimeType = intent.type?.asMimeTypeOrNull()
        if (path != null && mimeType != null) {
            openFile(path, mimeType)
        }
        finish()
    }

    private fun openFile(path: Path, mimeType: MimeType) {
        if (path.isArchivePath) {
            FileJobService.open(path, mimeType, false, this)
        } else {
            val intent = path.fileProviderUri.createViewIntent(mimeType)
                .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                .apply { extraPath = path }
            startActivitySafe(intent)
        }
    }

    companion object {
        private const val ACTION_OPEN_FILE = "dev.vcgms.aapp.vaterialfiles.intent.action.OPEN_FILE"

        fun createIntent(path: Path, mimeType: MimeType): Intent =
            Intent(ACTION_OPEN_FILE)
                .setPackage(application.packageName)
                .setType(mimeType.value)
                .apply { extraPath = path }
    }
}
