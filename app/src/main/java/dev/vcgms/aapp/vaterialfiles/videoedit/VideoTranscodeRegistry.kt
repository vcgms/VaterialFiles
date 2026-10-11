package dev.vcgms.aapp.vaterialfiles.videoedit

import java8.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow

sealed interface TranscodeProgress {
    data object NotStarted : TranscodeProgress

    data class Running(val progress: Int) : TranscodeProgress

    data object Finished : TranscodeProgress

    // The export failed; message carries a short human-readable reason for the UI.
    data class Failed(val message: String?) : TranscodeProgress
}

// In-memory progress registry for in-flight transcoding jobs, keyed by the source path. The
// transcode page collects from this while the job runs; Finished signals completion and Failed
// signals an error so the page can stop showing progress instead of hanging at 0%.
object VideoTranscodeRegistry {
    private val progressFlows = ConcurrentHashMap<Path, MutableStateFlow<TranscodeProgress>>()

    fun progressFlow(path: Path): MutableStateFlow<TranscodeProgress> =
        progressFlows.getOrPut(path) { MutableStateFlow(TranscodeProgress.NotStarted) }

    // Reset a path to a fresh running state before a new export. The registry outlives the page, so
    // without this a stale Finished/Failed left by a previous run of the same file would be replayed
    // to the collector the instant it subscribes and immediately tear the transcoding UI back down.
    fun start(path: Path) {
        progressFlow(path).value = TranscodeProgress.Running(0)
    }

    fun setProgress(path: Path, progress: Int) {
        progressFlows[path]?.value = TranscodeProgress.Running(progress)
    }

    fun finish(path: Path) {
        progressFlows[path]?.value = TranscodeProgress.Finished
    }

    fun fail(path: Path, message: String?) {
        progressFlows[path]?.value = TranscodeProgress.Failed(message)
    }
}
