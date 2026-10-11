package dev.vcgms.aapp.vaterialfiles.filejob

import java.io.File
import java.io.InterruptedIOException
import java.nio.file.StandardCopyOption
import dev.vcgms.aapp.vaterialfiles.R
import dev.vcgms.aapp.vaterialfiles.provider.common.delete
import dev.vcgms.aapp.vaterialfiles.provider.common.deleteIfExists
import dev.vcgms.aapp.vaterialfiles.videoedit.VideoTranscodeParams
import dev.vcgms.aapp.vaterialfiles.videoedit.VideoTranscodeRegistry
import dev.vcgms.aapp.vaterialfiles.videoedit.VideoTranscoder

// A background video re-encoding job; the transformer runs asynchronously while the job thread
// blocks on polling, so that interruption from FileJobService.cancelJob() cancels the export.
class VideoTranscodeJob(
    override val id: Int,
    private val params: VideoTranscodeParams,
) : FileJob() {

    override fun run() {
        val title = getString(
            R.string.video_transcode_notification_title, params.sourcePath.fileName.toString()
        )
        postNotification(title, null, null, null, 100, 0, true, true)
        // An in-place re-encode (delete-source mode with an unchanged name) cannot stream into the
        // file being read, so export to a temporary file and move it over the source afterwards.
        val inPlace = params.outputPath == params.sourcePath
        val tempFile = if (inPlace) {
            File.createTempFile("transcode_", ".tmp", service.cacheDir)
        } else {
            null
        }
        val effectiveParams = if (tempFile != null) {
            params.copy(outputPath = java8.nio.file.Paths.get(tempFile.absolutePath))
        } else {
            params
        }
        val transcoder = VideoTranscoder(service, effectiveParams, object : VideoTranscoder.Listener {
            override fun onProgress(progress: Int) {
                VideoTranscodeRegistry.setProgress(params.sourcePath, progress)
                postNotification(
                    title, null, null, "$progress%", 100, progress, false, true
                )
            }
        })
        // Set once the output file is fully written (and moved into place for an in-place
        // re-encode); a failure after this point (e.g. deleting the source) must not remove the
        // already-complete output.
        var outputComplete = false
        try {
            transcoder.run()
            if (tempFile != null) {
                java.nio.file.Files.move(
                    tempFile.toPath(),
                    params.outputPath.toFile().toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }
            outputComplete = true
            // The source was already replaced by the output in the in-place case.
            if (params.deleteSource && !inPlace) {
                params.sourcePath.delete()
            }
            VideoTranscodeRegistry.finish(params.sourcePath)
        } catch (e: InterruptedException) {
            // Cancelled by the user: delete the partially written output (the temp file for an
            // in-place re-encode, otherwise the output file itself) and report finished so the page
            // resets its UI (cancellation is not an error).
            if (!outputComplete) {
                effectiveParams.outputPath.deleteIfExists()
            }
            VideoTranscodeRegistry.finish(params.sourcePath)
            throw InterruptedIOException()
        } catch (e: Exception) {
            // Export or move failed: delete the partially written output and surface the reason so
            // the page stops showing progress instead of hanging at 0%. Not rethrown on purpose: the
            // page collects Failed and reports it, and rethrowing would make FileJob toast it twice.
            e.printStackTrace()
            if (!outputComplete) {
                effectiveParams.outputPath.deleteIfExists()
            }
            VideoTranscodeRegistry.fail(params.sourcePath, e.message ?: e.toString())
        }
    }
}
