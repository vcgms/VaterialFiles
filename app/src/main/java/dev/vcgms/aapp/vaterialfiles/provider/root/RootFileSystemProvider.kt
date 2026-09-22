package dev.vcgms.aapp.vaterialfiles.provider.root

import java8.nio.file.FileSystem
import java8.nio.file.LinkOption
import java8.nio.file.Path
import java8.nio.file.attribute.FileAttributeView
import dev.vcgms.aapp.vaterialfiles.provider.remote.RemoteFileSystemProvider
import dev.vcgms.aapp.vaterialfiles.provider.remote.RemoteInterface
import java.net.URI

open class RootFileSystemProvider(scheme: String) : RemoteFileSystemProvider(
    RemoteInterface { RootFileService.getRemoteFileSystemProviderInterface(scheme) }
) {
    override fun getScheme(): String {
        throw AssertionError()
    }

    override fun newFileSystem(uri: URI, env: Map<String, *>): FileSystem {
        throw AssertionError()
    }

    override fun getFileSystem(uri: URI): FileSystem {
        throw AssertionError()
    }

    override fun getPath(uri: URI): Path {
        throw AssertionError()
    }

    override fun <V : FileAttributeView> getFileAttributeView(
        path: Path,
        type: Class<V>,
        vararg options: LinkOption
    ): V? {
        throw AssertionError()
    }
}
