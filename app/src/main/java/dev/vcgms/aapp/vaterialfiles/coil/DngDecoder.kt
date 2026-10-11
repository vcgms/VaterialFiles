package dev.vcgms.aapp.vaterialfiles.coil

import android.graphics.ImageDecoder
import coil3.asImage
import coil3.ImageLoader
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

// Decoder for DNG (RAW) images through the platform ImageDecoder, which supports DNG natively
// since API 33 (our minSdk); the default BitmapFactory decoder cannot handle DNG.
class DngDecoder(
    private val source: SourceFetchResult,
    private val options: Options,
) : Decoder {
    override suspend fun decode(): DecodeResult {
        val bitmap = withContext(Dispatchers.IO) {
            val imageSource = source.source.fileOrNull()?.let { ImageDecoder.createSource(it.toFile()) }
                ?: ImageDecoder.createSource(source.source.source().readByteArray())
            ImageDecoder.decodeBitmap(imageSource) { decoder, info, _ ->
                // Cap the maximum edge so a huge RAW file cannot exhaust memory.
                val sample = computeSampleSize(
                    max(info.size.width, info.size.height), MAX_DECODED_EDGE
                )
                decoder.setTargetSampleSize(sample)
            }
        }
        return DecodeResult(bitmap.asImage(), isSampled = true)
    }

    class Factory : Decoder.Factory {
        override fun create(
            result: SourceFetchResult,
            options: Options,
            imageLoader: ImageLoader
        ): Decoder? {
            if (result.mimeType != MIME_TYPE_DNG) {
                return null
            }
            return DngDecoder(result, options)
        }
    }

    companion object {
        private const val MIME_TYPE_DNG = "image/x-adobe-dng"
        private const val MAX_DECODED_EDGE = 4096

        private fun computeSampleSize(maxEdge: Int, maxTargetEdge: Int): Int {
            if (maxEdge <= maxTargetEdge) {
                return 1
            }
            var sample = 1
            while (maxEdge / (sample * 2) >= maxTargetEdge) {
                sample *= 2
            }
            return sample
        }
    }
}
