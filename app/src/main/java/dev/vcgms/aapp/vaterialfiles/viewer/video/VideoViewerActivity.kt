package dev.vcgms.aapp.vaterialfiles.viewer.video

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.commit
import java8.nio.file.Path
import dev.vcgms.aapp.vaterialfiles.app.AppActivity
import dev.vcgms.aapp.vaterialfiles.file.FileItem
import dev.vcgms.aapp.vaterialfiles.file.isVideo
import dev.vcgms.aapp.vaterialfiles.util.extraPathList
import dev.vcgms.aapp.vaterialfiles.util.putArgs

class VideoViewerActivity : AppActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MpvManager.ensureCreated(applicationContext)

        // Calls ensureSubDecor().
        findViewById<View>(android.R.id.content)
        if (savedInstanceState == null) {
            val intent = intent
            val position = intent.getIntExtra(EXTRA_POSITION, 0)
            val fragment = VideoViewerFragment()
                .putArgs(VideoViewerFragment.Args(intent, position))
            supportFragmentManager.commit { add(android.R.id.content, fragment) }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Only tear down libmpv when the activity is really going away; recreation (e.g.
        // rotation) keeps the single mpv instance alive for the next fragment.
        if (isFinishing) {
            MpvManager.destroy()
        }
    }

    companion object {
        private val EXTRA_POSITION = "${VideoViewerActivity::class.java.name}.extra.POSITION"

        fun putExtras(intent: Intent, paths: List<Path>, position: Int) {
            // All extra put here must be framework classes, or we may crash the resolver activity.
            intent.extraPathList = paths
            intent.putExtra(EXTRA_POSITION, position)
        }
    }
}

// Opens the built-in video viewer for the given file, collecting the sibling videos from the
// adapter-ordered list so previous/next switch between videos in the same directory.
fun startVideoViewer(context: Context, files: List<FileItem>, target: Path) {
    val paths = mutableListOf<Path>()
    for (file in files) {
        if (file.mimeType.isVideo) {
            paths.add(file.path)
        }
    }
    var position = paths.indexOf(target)
    if (position == -1) {
        paths.add(0, target)
        position = 0
    }
    // Don't send too many paths to avoid TransactionTooLargeException.
    if (paths.size > PATH_LIST_SIZE_MAX) {
        val start = (position - PATH_LIST_SIZE_MAX / 2)
            .coerceIn(0, paths.size - PATH_LIST_SIZE_MAX)
        val kept = paths.subList(start, start + PATH_LIST_SIZE_MAX).toList()
        paths.clear()
        paths.addAll(kept)
        position -= start
    }
    val intent = Intent(context, VideoViewerActivity::class.java)
    VideoViewerActivity.putExtras(intent, paths, position)
    context.startActivity(intent)
}

private const val PATH_LIST_SIZE_MAX = 1000
