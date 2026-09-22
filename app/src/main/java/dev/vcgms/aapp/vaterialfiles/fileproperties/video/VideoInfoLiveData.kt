package dev.vcgms.aapp.vaterialfiles.fileproperties.video

import android.media.MediaMetadataRetriever
import android.os.AsyncTask
import android.util.Size
import java.time.Duration
import java8.nio.file.Path
import dev.vcgms.aapp.vaterialfiles.compat.use
import dev.vcgms.aapp.vaterialfiles.fileproperties.PathObserverLiveData
import dev.vcgms.aapp.vaterialfiles.fileproperties.date
import dev.vcgms.aapp.vaterialfiles.fileproperties.extractMetadataNotBlank
import dev.vcgms.aapp.vaterialfiles.fileproperties.location
import dev.vcgms.aapp.vaterialfiles.util.Failure
import dev.vcgms.aapp.vaterialfiles.util.Loading
import dev.vcgms.aapp.vaterialfiles.util.Stateful
import dev.vcgms.aapp.vaterialfiles.util.Success
import dev.vcgms.aapp.vaterialfiles.util.setDataSource
import dev.vcgms.aapp.vaterialfiles.util.valueCompat

class VideoInfoLiveData(path: Path) : PathObserverLiveData<Stateful<VideoInfo>>(path) {
    init {
        loadValue()
        observe()
    }

    override fun loadValue() {
        value = Loading(value?.value)
        AsyncTask.THREAD_POOL_EXECUTOR.execute {
            val value = try {
                val videoInfo = MediaMetadataRetriever().use { retriever ->
                    retriever.setDataSource(path)
                    val title = retriever.extractMetadataNotBlank(
                        MediaMetadataRetriever.METADATA_KEY_TITLE
                    )
                    val width = retriever.extractMetadataNotBlank(
                        MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
                    )?.toIntOrNull()
                    val height = retriever.extractMetadataNotBlank(
                        MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
                    )?.toIntOrNull()
                    val dimensions = if (width != null && height != null) {
                        Size(width, height)
                    } else {
                        null
                    }
                    val duration = retriever.extractMetadataNotBlank(
                        MediaMetadataRetriever.METADATA_KEY_DURATION
                    )?.toLongOrNull()?.let { Duration.ofMillis(it) }
                    val date = retriever.date
                    val location = retriever.location
                    val bitRate = retriever.extractMetadataNotBlank(
                        MediaMetadataRetriever.METADATA_KEY_BITRATE
                    )?.toLongOrNull()
                    VideoInfo(title, dimensions, duration, date, location, bitRate)
                }
                Success(videoInfo)
            } catch (e: Exception) {
                Failure(valueCompat.value, e)
            }
            postValue(value)
        }
    }
}
