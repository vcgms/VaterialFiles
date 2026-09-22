package dev.vcgms.aapp.vaterialfiles.provider.remote;

import dev.vcgms.aapp.vaterialfiles.provider.remote.ParcelableException;

interface IRemoteFileSystem {
    void close(out ParcelableException exception);
}
