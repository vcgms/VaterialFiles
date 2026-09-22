package dev.vcgms.aapp.vaterialfiles.provider.smb

import java8.nio.file.Path
import dev.vcgms.aapp.vaterialfiles.provider.smb.client.Authority

fun Authority.createSmbRootPath(): Path =
    SmbFileSystemProvider.getOrNewFileSystem(this).rootDirectory
