package dev.vcgms.aapp.vaterialfiles.provider.document

import android.net.Uri
import java8.nio.file.Path
import java8.nio.file.ProviderMismatchException
import dev.vcgms.aapp.vaterialfiles.provider.content.resolver.ResolverException
import dev.vcgms.aapp.vaterialfiles.provider.document.resolver.DocumentResolver
import java.io.IOException

val Path.documentUri: Uri
    @Throws(IOException::class)
    get() {
        this as? DocumentPath ?: throw ProviderMismatchException(toString())
        return try {
            DocumentResolver.getDocumentUri(this)
        } catch (e: ResolverException) {
            throw e.toFileSystemException(toString())
        }
    }

val Path.documentTreeUri: Uri
    get() {
        this as? DocumentPath ?: throw ProviderMismatchException(toString())
        return treeUri
    }

fun Uri.createDocumentTreeRootPath(): Path =
    DocumentFileSystemProvider.getOrNewFileSystem(this).rootDirectory
