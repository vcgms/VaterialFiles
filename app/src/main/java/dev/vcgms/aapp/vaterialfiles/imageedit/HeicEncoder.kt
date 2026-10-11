package dev.vcgms.aapp.vaterialfiles.imageedit

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.view.Surface
import java.io.File
import java.io.IOException

// Encodes a single still bitmap into an HEIC (HEIF) file using the platform "image/heic" encoder
// (MediaCodec) fed through an input surface, muxed with MediaMuxer's HEIF output. The Android SDK
// exposes no ImageEncoder class, so this is the standard public-API path for HEIC still images.
object HeicEncoder {

    @Throws(IOException::class, InterruptedException::class)
    fun encode(bitmap: Bitmap, quality: Int, outputFile: File) {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) {
            throw IOException("Cannot encode an empty bitmap")
        }
        val mime = MediaFormat.MIMETYPE_IMAGE_ANDROID_HEIC
        val format = MediaFormat.createVideoFormat(mime, width, height).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
            )
            setInteger(MediaFormat.KEY_BIT_RATE, computeBitRate(width, height, quality))
            setInteger(MediaFormat.KEY_FRAME_RATE, 1)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val codec = MediaCodec.createEncoderByType(mime)
        var surface: Surface? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            surface = codec.createInputSurface()
            codec.start()
            drawToSurface(bitmap, surface, width, height)
            codec.signalEndOfInputStream()

            muxer = MediaMuxer(
                outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_HEIF
            )
            val bufferInfo = MediaCodec.BufferInfo()
            var trackIndex = -1
            val deadline = System.nanoTime() + ENCODE_TIMEOUT_NANOS
            var outputDone = false
            while (!outputDone) {
                if (Thread.interrupted()) {
                    throw InterruptedException()
                }
                if (System.nanoTime() > deadline) {
                    throw IOException("HEIC encoding timed out")
                }
                val index = codec.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_MICROS)
                when {
                    index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        Thread.sleep(POLL_INTERVAL_MILLIS)
                    }
                    index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (muxerStarted) {
                            throw IOException("Encoder output format changed twice")
                        }
                        trackIndex = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    index >= 0 -> {
                        val outputBuffer = codec.getOutputBuffer(index)
                            ?: throw IOException("Null encoder output buffer")
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                            // Codec-specific data is carried by the output format instead.
                            bufferInfo.size = 0
                        }
                        if (bufferInfo.size > 0 && muxerStarted) {
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(trackIndex, outputBuffer, bufferInfo)
                        }
                        codec.releaseOutputBuffer(index, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                    }
                }
            }
            if (muxerStarted) {
                muxer.stop()
                muxerStarted = false
            }
        } finally {
            try {
                codec.stop()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            codec.release()
            surface?.release()
            if (muxerStarted) {
                try {
                    muxer?.stop()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            muxer?.release()
        }
    }

    private fun drawToSurface(bitmap: Bitmap, surface: Surface, width: Int, height: Int) {
        val canvas = surface.lockHardwareCanvas()
        try {
            canvas.drawColor(Color.BLACK)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(bitmap, null, Rect(0, 0, width, height), paint)
        } finally {
            surface.unlockCanvasAndPost(canvas)
        }
    }

    // Rough bits-per-pixel model scaled by the requested quality; a single frame is encoded so the
    // bit rate is effectively the per-image budget.
    private fun computeBitRate(width: Int, height: Int, quality: Int): Int {
        val q = quality.coerceIn(1, 100) / 100f
        val bitsPerPixel = 0.1f + q * 0.9f
        val bitRate = (width.toLong() * height.toLong() * bitsPerPixel).toLong()
        return bitRate.coerceIn(MIN_BIT_RATE.toLong(), MAX_BIT_RATE.toLong()).toInt()
    }

    private const val DEQUEUE_TIMEOUT_MICROS = 10_000L
    private const val POLL_INTERVAL_MILLIS = 5L
    private const val ENCODE_TIMEOUT_NANOS = 30L * 1_000_000_000L
    private const val MIN_BIT_RATE = 500_000
    private const val MAX_BIT_RATE = 200_000_000
}
