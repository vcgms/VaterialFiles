package dev.vcgms.aapp.vaterialfiles.provider.root

import dev.vcgms.aapp.vaterialfiles.provider.common.PosixFileAttributeView
import dev.vcgms.aapp.vaterialfiles.provider.remote.RemoteInterface
import dev.vcgms.aapp.vaterialfiles.provider.remote.RemotePosixFileAttributeView

open class RootPosixFileAttributeView(
    attributeView: PosixFileAttributeView
) : RemotePosixFileAttributeView(
    RemoteInterface { RootFileService.getRemotePosixFileAttributeViewInterface(attributeView) }
) {
    override fun name(): String {
        throw AssertionError()
    }
}
