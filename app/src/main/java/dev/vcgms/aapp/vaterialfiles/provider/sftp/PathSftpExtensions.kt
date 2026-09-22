package dev.vcgms.aapp.vaterialfiles.provider.sftp

import java8.nio.file.Path
import dev.vcgms.aapp.vaterialfiles.provider.sftp.client.Authority

fun Authority.createSftpRootPath(): Path =
    SftpFileSystemProvider.getOrNewFileSystem(this).rootDirectory
