package dev.vcgms.aapp.vaterialfiles.viewer.image

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import androidx.fragment.app.commit
import java8.nio.file.Path
import dev.vcgms.aapp.vaterialfiles.app.AppActivity
import dev.vcgms.aapp.vaterialfiles.file.FileItem
import dev.vcgms.aapp.vaterialfiles.file.isImage
import dev.vcgms.aapp.vaterialfiles.util.extraPathList
import dev.vcgms.aapp.vaterialfiles.util.putArgs

class ImageViewerActivity : AppActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Render in a wide color gamut so P3/Display-P3 images are displayed in their native
        // color space instead of being clamped to sRGB.
        window.setColorMode(ActivityInfo.COLOR_MODE_WIDE_COLOR_GAMUT)

        // Calls ensureSubDecor().
        findViewById<View>(android.R.id.content)
        if (savedInstanceState == null) {
            val intent = intent
            val position = intent.getIntExtra(EXTRA_POSITION, 0)
            val fragment = ImageViewerFragment()
                .putArgs(ImageViewerFragment.Args(intent, position))
            supportFragmentManager.commit { add(android.R.id.content, fragment) }
        }
    }

    companion object {
        private val EXTRA_POSITION = "${ImageViewerActivity::class.java.name}.extra.POSITION"

        fun putExtras(intent: Intent, paths: List<Path>, position: Int) {
            // All extra put here must be framework classes, or we may crash the resolver activity.
            intent.extraPathList = paths
            intent.putExtra(EXTRA_POSITION, position)
        }
    }
}

// Opens the built-in image viewer explicitly for the given file, collecting the sibling images
// from the adapter-ordered list so swiping moves between images in the same directory. An
// explicit launch bypasses the device's default handler for image/* (e.g. the system gallery).
fun startImageViewer(context: Context, files: List<FileItem>, target: Path) {
    val paths = mutableListOf<Path>()
    for (file in files) {
        if (file.mimeType.isImage) {
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
    val intent = Intent(context, ImageViewerActivity::class.java)
    ImageViewerActivity.putExtras(intent, paths, position)
    context.startActivity(intent)
}

private const val PATH_LIST_SIZE_MAX = 1000
