package dev.vcgms.aapp.vaterialfiles.imageedit

import java8.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow

sealed interface ImageReencodeState {
    data object NotStarted : ImageReencodeState

    data object Running : ImageReencodeState

    data object Finished : ImageReencodeState

    data class Failed(val message: String) : ImageReencodeState
}

// In-memory state registry for in-flight image re-encoding jobs, keyed by the source path. The
// re-encode page collects from this while the job runs; Finished/Failed signal completion.
object ImageReencodeRegistry {
    private val stateFlows = ConcurrentHashMap<Path, MutableStateFlow<ImageReencodeState>>()

    fun stateFlow(path: Path): MutableStateFlow<ImageReencodeState> =
        stateFlows.getOrPut(path) { MutableStateFlow(ImageReencodeState.NotStarted) }

    fun setRunning(path: Path) {
        stateFlows[path]?.value = ImageReencodeState.Running
    }

    fun setFinished(path: Path) {
        stateFlows[path]?.value = ImageReencodeState.Finished
    }

    fun setFailed(path: Path, message: String) {
        stateFlows[path]?.value = ImageReencodeState.Failed(message)
    }
}
