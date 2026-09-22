package dev.vcgms.aapp.vaterialfiles.file

import android.net.Uri
import android.os.Parcelable
import android.provider.DocumentsContract
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import dev.vcgms.aapp.vaterialfiles.compat.DocumentsContractCompat
import dev.vcgms.aapp.vaterialfiles.util.StableUriParceler
import dev.vcgms.aapp.vaterialfiles.util.takeIfNotEmpty

@Parcelize
@JvmInline
value class ExternalStorageUri(val value: @WriteWith<StableUriParceler> Uri) : Parcelable {
    constructor(
        rootId: String,
        path: String
    ) : this(
        DocumentsContract.buildDocumentUriUsingTree(
            DocumentsContract.buildTreeDocumentUri(
                DocumentsContractCompat.EXTERNAL_STORAGE_PROVIDER_AUTHORITY,
                rootId
            ),
            "$rootId:$path"
        )
    )

    val rootId: String
        get() = DocumentsContract.getTreeDocumentId(value)

    val path: String
        get() = DocumentsContract.getDocumentId(value).removePrefix("$rootId:")
}

fun Uri.asExternalStorageUriOrNull(): ExternalStorageUri? =
    if (isExternalStorageUri) ExternalStorageUri(this) else null

fun Uri.asExternalStorageUri(): ExternalStorageUri {
    require(isExternalStorageUri)
    return ExternalStorageUri(this)
}

/** @see DocumentsContractCompat.isDocumentUri */
private val Uri.isExternalStorageUri: Boolean
    get() =
        DocumentsContractCompat.isDocumentUri(this) &&
            authority == DocumentsContractCompat.EXTERNAL_STORAGE_PROVIDER_AUTHORITY &&
            pathSegments.size == 4

val ExternalStorageUri.displayName: String
    get() = path.takeLastWhile { it != '/' }.takeIfNotEmpty() ?: "/"
