package dev.vcgms.aapp.vaterialfiles.provider.remote

import java8.nio.file.FileSystem
import dev.vcgms.aapp.vaterialfiles.provider.common.PosixFileAttributeView
import dev.vcgms.aapp.vaterialfiles.provider.common.PosixFileStore

abstract class RemoteFileService(private val remoteInterface: RemoteInterface<IRemoteFileService>) {
    @Throws(RemoteFileSystemException::class)
    fun getRemoteFileSystemProviderInterface(scheme: String): IRemoteFileSystemProvider =
        remoteInterface.get().call { getRemoteFileSystemProviderInterface(scheme) }

    @Throws(RemoteFileSystemException::class)
    fun getRemoteFileSystemInterface(fileSystem: FileSystem): IRemoteFileSystem =
        remoteInterface.get().call { getRemoteFileSystemInterface(fileSystem.toParcelable()) }

    @Throws(RemoteFileSystemException::class)
    fun getRemotePosixFileStoreInterface(fileStore: PosixFileStore): IRemotePosixFileStore =
        remoteInterface.get().call { getRemotePosixFileStoreInterface(fileStore.toParcelable()) }

    @Throws(RemoteFileSystemException::class)
    fun getRemotePosixFileAttributeViewInterface(
        attributeView: PosixFileAttributeView
    ): IRemotePosixFileAttributeView =
        remoteInterface.get().call {
            getRemotePosixFileAttributeViewInterface(attributeView.toParcelable())
        }
}
