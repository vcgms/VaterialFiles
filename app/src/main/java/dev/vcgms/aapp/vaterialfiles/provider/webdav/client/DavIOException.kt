package dev.vcgms.aapp.vaterialfiles.provider.webdav.client

import at.bitfire.dav4jvm.exception.DavException
import java.io.IOException

class DavIOException(cause: IOException) : DavException(cause.message ?: "", cause) {
    override val cause: Throwable
        get() = super.cause!!
}

fun IOException.toDavException(): DavIOException = DavIOException(this)
