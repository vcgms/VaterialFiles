package dev.vcgms.aapp.vaterialfiles.provider.common

import android.os.Parcel
import java.time.Instant
import java8.nio.file.attribute.FileTime
import kotlinx.parcelize.Parceler
import dev.vcgms.aapp.vaterialfiles.compat.readSerializableCompat

object FileTimeParceler : Parceler<FileTime?> {
    override fun create(parcel: Parcel): FileTime? =
        parcel.readSerializableCompat<Instant>()?.let { FileTime.from(it) }

    override fun FileTime?.write(parcel: Parcel, flags: Int) {
        parcel.writeSerializable(this?.toInstant())
    }
}
