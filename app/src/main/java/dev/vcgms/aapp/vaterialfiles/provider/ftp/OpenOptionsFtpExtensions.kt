package dev.vcgms.aapp.vaterialfiles.provider.ftp

import java8.nio.file.StandardOpenOption
import dev.vcgms.aapp.vaterialfiles.provider.common.OpenOptions

internal fun OpenOptions.checkForFtp() {
    if (deleteOnClose) {
        throw UnsupportedOperationException(StandardOpenOption.DELETE_ON_CLOSE.toString())
    }
    if (sync) {
        throw UnsupportedOperationException(StandardOpenOption.SYNC.toString())
    }
    if (dsync) {
        throw UnsupportedOperationException(StandardOpenOption.DSYNC.toString())
    }
}
