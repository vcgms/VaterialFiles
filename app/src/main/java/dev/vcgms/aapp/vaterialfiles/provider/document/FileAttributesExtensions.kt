package dev.vcgms.aapp.vaterialfiles.provider.document

import android.provider.DocumentsContract
import java8.nio.file.ProviderMismatchException
import java8.nio.file.attribute.BasicFileAttributes
import dev.vcgms.aapp.vaterialfiles.util.hasBits

val BasicFileAttributes.documentSupportsThumbnail: Boolean
    get() {
        this as? DocumentFileAttributes ?: throw ProviderMismatchException(toString())
        return flags().hasBits(DocumentsContract.Document.FLAG_SUPPORTS_THUMBNAIL)
    }
