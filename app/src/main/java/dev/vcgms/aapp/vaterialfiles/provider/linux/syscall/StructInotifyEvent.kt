package dev.vcgms.aapp.vaterialfiles.provider.linux.syscall

import dev.vcgms.aapp.vaterialfiles.provider.common.ByteString

class StructInotifyEvent(
    val wd: Int,
    val mask: Int, /* uint32_t */
    val cookie: Int, /* uint32_t */
    val name: ByteString?
)
