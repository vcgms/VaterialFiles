package dev.vcgms.aapp.vaterialfiles.provider.ftp

import java8.nio.file.Path
import dev.vcgms.aapp.vaterialfiles.provider.ftp.client.Authority

fun Authority.createFtpRootPath(): Path =
    FtpFileSystemProvider.getOrNewFileSystem(this).rootDirectory
