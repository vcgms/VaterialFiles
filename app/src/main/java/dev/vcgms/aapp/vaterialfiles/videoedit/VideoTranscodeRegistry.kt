package dev.vcgms.aapp.vaterialfiles.videoedit

import java8.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow

sealed interface TranscodeProgress {
    data object NotStarted : TranscodeProgress

    data class Running(val progress: Int) : TranscodeProgress

    data object Finished : TranscodeProgress
}

// In-memory progress registry for in-flight transcoding jobs, keyed by the source path. The
// transcode page collects from this while the job runs; Finished signals completion.
object VideoTranscodeRegistry {
    private val progressFlows = ConcurrentHashMap<Path, MutableStateFlow<TranscodeProgress>>()

    fun progressFlow(path: Path): MutableStateFlow<TranscodeProgress> =
        progressFlows.getOrPut(path) { MutableStateFlow(TranscodeProgress.NotStarted) }

    fun setProgress(path: Path, progress: Int) {
        progressFlows[path]?.value = TranscodeProgress.Running(progress)
    }

    fun finish(path: Path) {
        progressFlows[path]?.value = TranscodeProgress.Finished
    }
}
