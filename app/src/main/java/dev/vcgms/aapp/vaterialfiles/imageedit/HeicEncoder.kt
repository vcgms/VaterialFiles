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
// (MediaCodec) muxed with MediaMuxer's HEIF output. The Android SDK exposes no ImageEncoder class,
// so this is the standard public-API path for HEIC still images.
//
// Qualcomm's HEIC encoder is fed a byte-buffer RGBA frame: on some devices (e.g. OPPO/ColorOS) its
// Surface-input path is broken - the V4L2 driver fails to translate the profile and never encodes
// the input frame, so the muxer writes an empty container that cannot be opened. When the encoder
// does not accept the byte-buffer color format the Surface-input path is used instead.
object HeicEncoder {

    @Throws(IOException::class, InterruptedException::class)
    fun encode(bitmap: Bitmap, quality: Int, outputFile: File) {
        var encodedBitmap = bitmap
        // YUV420 subsampling requires even dimensions; an odd source size (e.g. a rotated photo)
        // makes the encoder reject the frame, so crop the last row/column off in that case.
        if (encodedBitmap.width % 2 != 0 || encodedBitmap.height % 2 != 0) {
            val evenWidth = encodedBitmap.width - (encodedBitmap.width % 2)
            val evenHeight = encodedBitmap.height - (encodedBitmap.height % 2)
            encodedBitmap = Bitmap.createBitmap(encodedBitmap, 0, 0, evenWidth, evenHeight)
        }
        val width = encodedBitmap.width
        val height = encodedBitmap.height
        if (width <= 0 || height <= 0) {
            throw IOException("Cannot encode an empty bitmap")
        }
        val mime = MediaFormat.MIMETYPE_IMAGE_ANDROID_HEIC
        val codec = MediaCodec.createEncoderByType(mime)
        try {
            var surfaceInput: Boolean
            try {
                codec.configure(
                    byteBufferFormat(mime, width, height, quality), null, null,
                    MediaCodec.CONFIGURE_FLAG_ENCODE
                )
                surfaceInput = false
            } catch (e: Exception) {
                // The byte-buffer color format is not accepted; fall back to a Surface input.
                codec.configure(
                    surfaceFormat(mime, width, height, quality), null, null,
                    MediaCodec.CONFIGURE_FLAG_ENCODE
                )
                surfaceInput = true
            }
            codec.start()
            if (surfaceInput) {
                val surface = codec.createInputSurface()
                try {
                    drawToSurface(encodedBitmap, surface, width, height)
                    codec.signalEndOfInputStream()
                    muxOutput(codec, outputFile)
                } finally {
                    surface.release()
                }
            } else {
                feedByteBuffer(codec, encodedBitmap, width, height)
                signalEndOfStream(codec)
                muxOutput(codec, outputFile)
            }
        } finally {
            try {
                codec.stop()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            codec.release()
        }
    }

    private fun byteBufferFormat(
        mime: String, width: Int, height: Int, quality: Int
    ): MediaFormat = MediaFormat.createVideoFormat(mime, width, height).apply {
        setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
        )
        setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.HEVCProfileMain)
        setInteger(MediaFormat.KEY_BIT_RATE, computeBitRate(width, height, quality))
        setInteger(MediaFormat.KEY_FRAME_RATE, 1)
        setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
    }

    private fun surfaceFormat(
        mime: String, width: Int, height: Int, quality: Int
    ): MediaFormat = MediaFormat.createVideoFormat(mime, width, height).apply {
        setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
        )
        setInteger(
            MediaFormat.KEY_PROFILE,
            MediaCodecInfo.CodecProfileLevel.HEVCProfileMainStill
        )
        setInteger(MediaFormat.KEY_BIT_RATE, computeBitRate(width, height, quality))
        setInteger(MediaFormat.KEY_FRAME_RATE, 1)
        setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
    }

    // Feeds the bitmap as one NV12 (YUV420 semi-planar) frame through a byte-buffer input. The
    // encoder's RGBA byte-buffer format cannot be converted by the framework (Codec2's buffer
    // converter rejects it), so the frame is converted to YUV in software first.
    @Throws(IOException::class, InterruptedException::class)
    private fun feedByteBuffer(codec: MediaCodec, bitmap: Bitmap, width: Int, height: Int) {
        val deadline = System.nanoTime() + INPUT_TIMEOUT_NANOS
        var inputIndex = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_MICROS)
        while (inputIndex == MediaCodec.INFO_TRY_AGAIN_LATER && System.nanoTime() < deadline) {
            if (Thread.interrupted()) {
                throw InterruptedException()
            }
            Thread.sleep(POLL_INTERVAL_MILLIS)
            inputIndex = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_MICROS)
        }
        if (inputIndex < 0) {
            throw IOException("HEIC encoder did not provide an input buffer")
        }
        val inputBuffer = codec.getInputBuffer(inputIndex)
            ?: throw IOException("Null encoder input buffer")
        val nv12 = bitmapToNv12(bitmap, width, height)
        inputBuffer.clear()
        inputBuffer.put(nv12)
        codec.queueInputBuffer(inputIndex, 0, nv12.size, 0, 0)
    }

    // Converts the bitmap to NV12 (BT.601 limited range), averaging each 2x2 block's chroma. Odd
    // dimensions are handled by clamping the block to the edge, so the chroma plane uses
    // ceil(width / 2) x ceil(height / 2) blocks.
    private fun bitmapToNv12(bitmap: Bitmap, width: Int, height: Int): ByteArray {
        val frameSize = width * height
        val uvBlocks = (width + 1) / 2 * ((height + 1) / 2)
        val nv12 = ByteArray(frameSize + uvBlocks * 2)
        val pixels = IntArray(frameSize)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        var uvIndex = frameSize
        var j = 0
        while (j < height) {
            val j2 = minOf(j + 1, height - 1)
            val row = j * width
            val row2 = j2 * width
            var i = 0
            while (i < width) {
                val i2 = minOf(i + 1, width - 1)
                val p00 = pixels[row + i]
                val p01 = pixels[row + i2]
                val p10 = pixels[row2 + i]
                val p11 = pixels[row2 + i2]
                // Luma for the block's pixels.
                nv12[row + i] = yFromRgb(p00)
                nv12[row + i2] = yFromRgb(p01)
                nv12[row2 + i] = yFromRgb(p10)
                nv12[row2 + i2] = yFromRgb(p11)
                // Averaged chroma.
                val sumR = (p00 shr 16 and 0xFF) + (p01 shr 16 and 0xFF) +
                    (p10 shr 16 and 0xFF) + (p11 shr 16 and 0xFF)
                val sumG = (p00 shr 8 and 0xFF) + (p01 shr 8 and 0xFF) +
                    (p10 shr 8 and 0xFF) + (p11 shr 8 and 0xFF)
                val sumB = (p00 and 0xFF) + (p01 and 0xFF) + (p10 and 0xFF) + (p11 and 0xFF)
                nv12[uvIndex] = ((-38 * sumR - 74 * sumG + 112 * sumB + 512) shr 10)
                    .plus(128).coerceIn(16, 240).toByte()
                nv12[uvIndex + 1] = ((112 * sumR - 94 * sumG - 18 * sumB + 512) shr 10)
                    .plus(128).coerceIn(16, 240).toByte()
                uvIndex += 2
                i += 2
            }
            j += 2
        }
        return nv12
    }

    private fun yFromRgb(pixel: Int): Byte {
        val r = pixel shr 16 and 0xFF
        val g = pixel shr 8 and 0xFF
        val b = pixel and 0xFF
        return ((66 * r + 129 * g + 25 * b + 128) shr 8).plus(16).coerceIn(16, 235).toByte()
    }

    // For a byte-buffer input there is no input surface, so end of stream is signaled by queueing
    // an empty buffer with the end-of-stream flag (signalEndOfInputStream requires a surface).
    @Throws(IOException::class, InterruptedException::class)
    private fun signalEndOfStream(codec: MediaCodec) {
        val deadline = System.nanoTime() + INPUT_TIMEOUT_NANOS
        var inputIndex = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_MICROS)
        while (inputIndex == MediaCodec.INFO_TRY_AGAIN_LATER && System.nanoTime() < deadline) {
            if (Thread.interrupted()) {
                throw InterruptedException()
            }
            Thread.sleep(POLL_INTERVAL_MILLIS)
            inputIndex = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_MICROS)
        }
        if (inputIndex < 0) {
            throw IOException("HEIC encoder did not accept the end-of-stream buffer")
        }
        codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
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

    @Throws(IOException::class, InterruptedException::class)
    private fun muxOutput(codec: MediaCodec, outputFile: File) {
        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_HEIF)
        var muxerStarted = false
        try {
            val bufferInfo = MediaCodec.BufferInfo()
            var trackIndex = -1
            var wroteSample = false
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
                            wroteSample = true
                        }
                        codec.releaseOutputBuffer(index, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                    }
                }
            }
            if (!muxerStarted) {
                throw IOException("HEIC encoder did not produce an output format")
            }
            if (!wroteSample) {
                throw IOException("HEIC encoder did not produce any image data")
            }
            muxer.stop()
            muxerStarted = false
        } finally {
            if (muxerStarted) {
                try {
                    muxer.stop()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            muxer.release()
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
    private const val INPUT_TIMEOUT_NANOS = 5L * 1_000_000_000L
    private const val ENCODE_TIMEOUT_NANOS = 30L * 1_000_000_000L
    private const val MIN_BIT_RATE = 500_000
    private const val MAX_BIT_RATE = 200_000_000
}
