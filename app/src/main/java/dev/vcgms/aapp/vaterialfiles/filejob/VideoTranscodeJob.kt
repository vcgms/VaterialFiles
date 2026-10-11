package dev.vcgms.aapp.vaterialfiles.filejob

import java.io.InterruptedIOException
import dev.vcgms.aapp.vaterialfiles.R
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
        val transcoder = VideoTranscoder(service, params, object : VideoTranscoder.Listener {
            override fun onProgress(progress: Int) {
                VideoTranscodeRegistry.setProgress(params.sourcePath, progress)
                postNotification(
                    title, null, null, "$progress%", 100, progress, false, true
                )
            }
        })
        try {
            transcoder.run()
        } catch (e: InterruptedException) {
            VideoTranscodeRegistry.finish(params.sourcePath)
            throw InterruptedIOException()
        }
        VideoTranscodeRegistry.finish(params.sourcePath)
    }
}
