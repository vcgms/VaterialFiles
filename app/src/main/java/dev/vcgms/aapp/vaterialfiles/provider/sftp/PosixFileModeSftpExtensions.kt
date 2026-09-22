package dev.vcgms.aapp.vaterialfiles.provider.sftp

import dev.vcgms.aapp.vaterialfiles.provider.common.PosixFileModeBit
import dev.vcgms.aapp.vaterialfiles.provider.common.toInt
import net.schmizz.sshj.sftp.FileAttributes

fun Set<PosixFileModeBit>.toSftpAttributes(): FileAttributes =
    FileAttributes.Builder().withPermissions(toInt()).build()
