package dev.vcgms.aapp.vaterialfiles.filejob

import java.io.InterruptedIOException
import dev.vcgms.aapp.vaterialfiles.R
import dev.vcgms.aapp.vaterialfiles.imageedit.ImageReencodeParams
import dev.vcgms.aapp.vaterialfiles.imageedit.ImageReencodeRegistry
import dev.vcgms.aapp.vaterialfiles.imageedit.ImageReencoder

// A background image re-encoding job. The encoding runs synchronously on the job thread, so
// interruption from FileJobService.cancelJob() cancels it; the completion state is published to
// ImageReencodeRegistry for the re-encode page to observe.
class ImageReencodeJob(
    override val id: Int,
    private val params: ImageReencodeParams,
) : FileJob() {

    override fun run() {
        val title = getString(
            R.string.image_reencode_notification_title, params.sourcePath.fileName.toString()
        )
        postNotification(title, null, null, null, 0, 0, true, true)
        ImageReencodeRegistry.setRunning(params.sourcePath)
        try {
            ImageReencoder.encode(service, params)
        } catch (e: InterruptedException) {
            ImageReencodeRegistry.setFinished(params.sourcePath)
            throw InterruptedIOException()
        } catch (e: Exception) {
            ImageReencodeRegistry.setFailed(params.sourcePath, e.toString())
            throw e
        }
        ImageReencodeRegistry.setFinished(params.sourcePath)
    }
}
