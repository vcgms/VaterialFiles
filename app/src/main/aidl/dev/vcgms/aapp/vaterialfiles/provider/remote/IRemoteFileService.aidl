package dev.vcgms.aapp.vaterialfiles.provider.remote;

import dev.vcgms.aapp.vaterialfiles.provider.remote.IRemoteFileSystem;
import dev.vcgms.aapp.vaterialfiles.provider.remote.IRemoteFileSystemProvider;
import dev.vcgms.aapp.vaterialfiles.provider.remote.IRemotePosixFileAttributeView;
import dev.vcgms.aapp.vaterialfiles.provider.remote.IRemotePosixFileStore;
import dev.vcgms.aapp.vaterialfiles.provider.remote.ParcelableObject;

interface IRemoteFileService {
    IRemoteFileSystemProvider getRemoteFileSystemProviderInterface(String scheme);

    IRemoteFileSystem getRemoteFileSystemInterface(in ParcelableObject fileSystem);

    IRemotePosixFileStore getRemotePosixFileStoreInterface(in ParcelableObject fileStore);

    IRemotePosixFileAttributeView getRemotePosixFileAttributeViewInterface(
        in ParcelableObject attributeView
    );
}
