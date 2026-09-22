package dev.vcgms.aapp.vaterialfiles.provider.linux.syscall

import dev.vcgms.aapp.vaterialfiles.provider.common.ByteString

class StructGroup(
    val gr_name: ByteString?,
    val gr_passwd: ByteString?,
    val gr_gid: Int,
    val gr_mem: Array<ByteString>?
)
