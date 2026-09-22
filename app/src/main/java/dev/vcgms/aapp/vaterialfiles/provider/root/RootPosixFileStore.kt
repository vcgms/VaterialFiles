package dev.vcgms.aapp.vaterialfiles.provider.root

import dev.vcgms.aapp.vaterialfiles.provider.common.PosixFileStore
import dev.vcgms.aapp.vaterialfiles.provider.remote.RemoteInterface
import dev.vcgms.aapp.vaterialfiles.provider.remote.RemotePosixFileStore

class RootPosixFileStore(fileStore: PosixFileStore) : RemotePosixFileStore(
    RemoteInterface { RootFileService.getRemotePosixFileStoreInterface(fileStore) }
)
