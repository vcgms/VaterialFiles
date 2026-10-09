package dev.vcgms.aapp.vaterialfiles.filelist

import android.os.Environment
import java8.nio.file.Path
import java8.nio.file.Paths
import dev.vcgms.aapp.vaterialfiles.R
import dev.vcgms.aapp.vaterialfiles.app.application
import dev.vcgms.aapp.vaterialfiles.file.MimeType
import dev.vcgms.aapp.vaterialfiles.file.isSupportedArchive
import dev.vcgms.aapp.vaterialfiles.provider.archive.archiveFile
import dev.vcgms.aapp.vaterialfiles.provider.archive.isArchivePath
import dev.vcgms.aapp.vaterialfiles.provider.document.isDocumentPath
import dev.vcgms.aapp.vaterialfiles.provider.document.resolver.DocumentResolver
import dev.vcgms.aapp.vaterialfiles.provider.linux.isLinuxPath

val Path.name: String
    get() = emulatedUserStorageName()
        ?: fileName?.toString()
        ?: if (isArchivePath) {
            archiveFile.fileName.toString()
        } else {
            "/"
        }

// Returns the display name of an emulated user storage root (/storage/emulated/<userId>), or
// null for any other path. The current user's storage reads "User storage" while other users'
// storage reads "User <id> storage".
private fun Path.emulatedUserStorageName(): String? {
    if (nameCount != 3 || getName(0).toString() != "storage" ||
        getName(1).toString() != "emulated") {
        return null
    }
    val userId = getName(2).toString()
    return if (userId == currentUserStorageId) {
        application.getString(R.string.user_storage_name)
    } else {
        application.getString(R.string.user_storage_name_with_id_format, userId)
    }
}

@Suppress("DEPRECATION")
private val currentUserStorageId: String? =
    Paths.get(Environment.getExternalStorageDirectory().absolutePath).fileName?.toString()

fun Path.toUserFriendlyString(): String = if (isLinuxPath) toFile().path else toUri().toString()

fun Path.isArchiveFile(mimeType: MimeType): Boolean = !isArchivePath && mimeType.isSupportedArchive

val Path.isLocalPath: Boolean
    get() =
        isLinuxPath || (isDocumentPath && DocumentResolver.isLocal(this as DocumentResolver.Path))

val Path.isRemotePath: Boolean
    get() = !isLocalPath
