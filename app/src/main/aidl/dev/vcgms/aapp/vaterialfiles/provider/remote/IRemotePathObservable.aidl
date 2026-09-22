package dev.vcgms.aapp.vaterialfiles.provider.remote;

import dev.vcgms.aapp.vaterialfiles.provider.remote.ParcelableException;
import dev.vcgms.aapp.vaterialfiles.util.RemoteCallback;

interface IRemotePathObservable {
    void addObserver(in RemoteCallback observer);

    void close(out ParcelableException exception);
}
