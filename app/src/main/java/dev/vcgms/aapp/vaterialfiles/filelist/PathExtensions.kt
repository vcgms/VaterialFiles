package dev.vcgms.aapp.vaterialfiles.filelist

import java8.nio.file.Path
import dev.vcgms.aapp.vaterialfiles.file.MimeType
import dev.vcgms.aapp.vaterialfiles.file.isSupportedArchive
import dev.vcgms.aapp.vaterialfiles.provider.archive.archiveFile
import dev.vcgms.aapp.vaterialfiles.provider.archive.isArchivePath
import dev.vcgms.aapp.vaterialfiles.provider.document.isDocumentPath
import dev.vcgms.aapp.vaterialfiles.provider.document.resolver.DocumentResolver
import dev.vcgms.aapp.vaterialfiles.provider.linux.isLinuxPath

val Path.name: String
    get() = fileName?.toString() ?: if (isArchivePath) archiveFile.fileName.toString() else "/"

fun Path.toUserFriendlyString(): String = if (isLinuxPath) toFile().path else toUri().toString()

fun Path.isArchiveFile(mimeType: MimeType): Boolean = !isArchivePath && mimeType.isSupportedArchive

val Path.isLocalPath: Boolean
    get() =
        isLinuxPath || (isDocumentPath && DocumentResolver.isLocal(this as DocumentResolver.Path))

val Path.isRemotePath: Boolean
    get() = !isLocalPath
