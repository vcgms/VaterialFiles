package dev.vcgms.aapp.vaterialfiles.provider.remote;

import dev.vcgms.aapp.vaterialfiles.provider.common.ParcelableFileTime;
import dev.vcgms.aapp.vaterialfiles.provider.common.ParcelablePosixFileMode;
import dev.vcgms.aapp.vaterialfiles.provider.common.PosixGroup;
import dev.vcgms.aapp.vaterialfiles.provider.common.PosixUser;
import dev.vcgms.aapp.vaterialfiles.provider.remote.ParcelableException;
import dev.vcgms.aapp.vaterialfiles.provider.remote.ParcelableObject;

interface IRemotePosixFileAttributeView {
    ParcelableObject readAttributes(out ParcelableException exception);

    void setTimes(
        in ParcelableFileTime lastModifiedTime,
        in ParcelableFileTime lastAccessTime,
        in ParcelableFileTime createTime,
        out ParcelableException exception
    );

    void setOwner(in PosixUser owner, out ParcelableException exception);

    void setGroup(in PosixGroup group, out ParcelableException exception);

    void setMode(in ParcelablePosixFileMode mode, out ParcelableException exception);

    void setSeLinuxContext(in ParcelableObject context, out ParcelableException exception);

    void restoreSeLinuxContext(out ParcelableException exception);
}
