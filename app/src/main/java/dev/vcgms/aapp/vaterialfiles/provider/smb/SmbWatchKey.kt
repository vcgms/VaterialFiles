package dev.vcgms.aapp.vaterialfiles.provider.smb

import dev.vcgms.aapp.vaterialfiles.provider.common.AbstractWatchKey

internal class SmbWatchKey(
    watchService: SmbWatchService,
    path: SmbPath
) : AbstractWatchKey<SmbWatchKey, SmbPath>(watchService, path)
