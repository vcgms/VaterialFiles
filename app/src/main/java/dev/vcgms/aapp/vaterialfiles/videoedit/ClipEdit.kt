package dev.vcgms.aapp.vaterialfiles.videoedit

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

// A single clip of a timeline edit: a time range of the source video plus per-clip visual
// adjustments. brightness/contrast range from -1 to 1; cropFraction is the centered symmetric
// crop fraction from 0 (no crop) to 0.5 (half the frame on each side).
@Parcelize
data class ClipEdit(
    val startUs: Long,
    val endUs: Long,
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val cropFraction: Float = 0f,
) : Parcelable
