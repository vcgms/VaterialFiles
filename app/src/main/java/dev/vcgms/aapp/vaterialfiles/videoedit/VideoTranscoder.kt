package dev.vcgms.aapp.vaterialfiles.videoedit

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.Crop
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import java.io.IOException

// Wraps Media3 Transformer as a hardware-accelerated video re-encoder. The source video track is
// decoded and re-encoded with the given settings while the audio track is re-encoded to AAC by
// the transformer itself.
//
// Media3 1.11 requires every Transformer method (start/getProgress/cancel) to be called on its
// application looper thread, so all access is serialized onto a dedicated HandlerThread whose
// looper is passed to the builder.
class VideoTranscoder(
    private val context: Context,
    private val params: VideoTranscodeParams,
    private val listener: Listener,
) {
    interface Listener {
        fun onProgress(progress: Int)
    }

    private var transformer: Transformer? = null

    private var completed = false

    private var error: Exception? = null

    private val lock = Object()

    // Runs the export, blocking until it completes, fails or the calling thread is interrupted.
    @Throws(InterruptedException::class, IOException::class)
    fun run() {
        val handlerThread = HandlerThread("video-transcoder")
        handlerThread.start()
        val handler = Handler(handlerThread.looper)
        handler.post { start(handlerThread.looper) }
        try {
            val progressHolder = ProgressHolder()
            while (true) {
                synchronized(lock) {
                    if (completed || error != null) {
                        break
                    }
                }
                handler.post {
                    val transformer = transformer
                    if (transformer != null) {
                        try {
                            if (transformer.getProgress(progressHolder)
                                == Transformer.PROGRESS_STATE_AVAILABLE
                            ) {
                                listener.onProgress(progressHolder.progress)
                            }
                        } catch (e: Exception) {
                            synchronized(lock) {
                                if (error == null) {
                                    error = e
                                }
                            }
                        }
                    }
                }
                Thread.sleep(PROGRESS_POLL_INTERVAL_MS)
            }
            error?.let { throw IOException(it) }
        } finally {
            // Both normal completion and interruption go through here; cancel stops the export if
            // it is still running and must happen on the transformer's thread.
            handler.post {
                val transformer = transformer
                if (transformer != null && !completed) {
                    transformer.cancel()
                }
            }
            handlerThread.quitSafely()
        }
    }

    private fun start(looper: Looper) {
        try {
            val videoEncoderSettings = VideoEncoderSettings.Builder().apply {
                params.bitRate?.let { setBitrate(it) }
                params.bitrateMode?.let { setBitrateMode(it) }
                params.avcProfile?.let {
                    setEncodingProfileLevel(it, VideoEncoderSettings.NO_VALUE)
                }
                setiFrameIntervalSeconds(2f)
            }.build()
            val encoderFactory = DefaultEncoderFactory.Builder(context)
                .setRequestedVideoEncoderSettings(videoEncoderSettings)
                .build()
            transformer = Transformer.Builder(context)
                .setLooper(looper)
                .setVideoMimeType(params.videoMime)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setEncoderFactory(encoderFactory)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(
                        composition: Composition, exportResult: ExportResult
                    ) {
                        synchronized(lock) {
                            completed = true
                        }
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException,
                    ) {
                        synchronized(lock) {
                            error = exportException
                        }
                    }
                })
                .build()
            val clips = params.clips
            if (clips.isNullOrEmpty()) {
                transformer!!.start(
                    buildEditedMediaItem(buildBaseEffects(), null),
                    params.outputPath.toFile().path
                )
            } else {
                val editedMediaItems = clips.map { clip ->
                    buildEditedMediaItem(buildEffectsFor(clip), clip)
                }
                val composition = Composition.Builder(
                    EditedMediaItemSequence.withAudioAndVideoFrom(editedMediaItems)
                ).build()
                transformer!!.start(composition, params.outputPath.toFile().path)
            }
        } catch (e: Exception) {
            synchronized(lock) {
                if (error == null) {
                    error = e
                }
            }
        }
    }

    // Base effects shared by all clips: the target resolution (short side), when requested.
    private fun buildBaseEffects(): List<Effect> {
        val effects = mutableListOf<Effect>()
        params.shortSide?.let { effects.add(Presentation.createForShortSide(it)) }
        return effects
    }

    private fun buildEffectsFor(clip: ClipEdit): List<Effect> {
        val effects = buildBaseEffects().toMutableList()
        if (clip.cropFraction > 0f) {
            val fraction = clip.cropFraction.coerceIn(0f, 0.5f)
            effects.add(Crop(fraction, fraction, 1f - fraction, 1f - fraction))
        }
        if (clip.brightness != 0f) {
            effects.add(Brightness(clip.brightness.coerceIn(-1f, 1f)))
        }
        if (clip.contrast != 0f) {
            effects.add(Contrast(clip.contrast.coerceIn(-1f, 1f)))
        }
        return effects
    }

    private fun buildEditedMediaItem(effects: List<Effect>, clip: ClipEdit?): EditedMediaItem {
        val mediaItemBuilder = MediaItem.Builder()
            .setUri(Uri.fromFile(params.sourcePath.toFile()))
        clip?.let {
            mediaItemBuilder.setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionUs(it.startUs)
                    .setEndPositionUs(it.endUs)
                    .build()
            )
        }
        return EditedMediaItem.Builder(mediaItemBuilder.build()).apply {
            params.frameRate?.let { setFrameRate(it) }
            if (effects.isNotEmpty()) {
                setEffects(Effects(emptyList(), effects))
            }
        }.build()
    }

    companion object {
        private const val PROGRESS_POLL_INTERVAL_MS = 500L
    }
}
