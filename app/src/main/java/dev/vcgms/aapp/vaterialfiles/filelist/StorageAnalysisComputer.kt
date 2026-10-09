package dev.vcgms.aapp.vaterialfiles.filelist

import android.os.Handler
import android.os.Looper
import java8.nio.file.FileVisitResult
import java8.nio.file.FileVisitor
import java8.nio.file.Files
import java8.nio.file.Path
import java8.nio.file.attribute.BasicFileAttributes
import java.io.IOException
import java.util.concurrent.Executors

// Computes the true total size of a directory (including hidden descendants) and the sizes of
// each of its direct children, with a single recursive walk. Results are reported progressively
// on the main thread; starting a new walk or canceling invalidates any in-flight walk so late
// callbacks from an outdated directory are never delivered.
class StorageAnalysisComputer {
    interface Listener {
        // Called on the main thread as the walk progresses; childSizes only contains children
        // whose subtrees have been fully visited, total is the root size accumulated so far, and
        // complete marks the final report with the true totals.
        fun onSizes(childSizes: Map<Path, Long>, total: Long, complete: Boolean)
    }

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "file-list-storage-analysis").apply { isDaemon = true }
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var generation = 0

    @Volatile
    private var listener: Listener? = null

    fun start(root: Path, listener: Listener) {
        this.listener = listener
        val currentGeneration = ++generation
        executor.execute {
            walk(root, currentGeneration)
        }
    }

    fun cancel() {
        listener = null
        ++generation
    }

    private fun walk(root: Path, myGeneration: Int) {
        val childSizes = HashMap<Path, Long>()
        // Accumulated sizes per directory depth; index 0 is the root itself.
        val stack = ArrayList<Long>()
        var total = 0L
        var lastReportTimeMillis = 0L
        fun report() {
            val snapshot = HashMap(childSizes)
            val totalSnapshot = stack.firstOrNull() ?: total
            mainHandler.post {
                if (myGeneration == generation) {
                    listener?.onSizes(snapshot, totalSnapshot, false)
                }
            }
        }
        try {
            Files.walkFileTree(root, object : FileVisitor<Path> {
                override fun preVisitDirectory(
                    directory: Path,
                    attributes: BasicFileAttributes
                ): FileVisitResult {
                    if (myGeneration != generation) {
                        return FileVisitResult.TERMINATE
                    }
                    stack.add(0L)
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(
                    file: Path,
                    attributes: BasicFileAttributes
                ): FileVisitResult {
                    if (myGeneration != generation) {
                        return FileVisitResult.TERMINATE
                    }
                    stack[stack.size - 1] = stack[stack.size - 1] + attributes.size()
                    if (stack.size == 1) {
                        childSizes[file] = attributes.size()
                    }
                    val currentTimeMillis = System.currentTimeMillis()
                    if (currentTimeMillis >= lastReportTimeMillis + PROGRESS_INTERVAL_MILLIS) {
                        lastReportTimeMillis = currentTimeMillis
                        report()
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(
                    file: Path,
                    exception: IOException
                ): FileVisitResult {
                    exception.printStackTrace()
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(
                    directory: Path,
                    exception: IOException?
                ): FileVisitResult {
                    if (myGeneration != generation) {
                        return FileVisitResult.TERMINATE
                    }
                    exception?.printStackTrace()
                    val size = stack.removeAt(stack.size - 1)
                    if (stack.isEmpty()) {
                        total = size
                    } else {
                        stack[stack.size - 1] = stack[stack.size - 1] + size
                        if (directory.parent == root) {
                            childSizes[directory] = size
                        }
                    }
                    val currentTimeMillis = System.currentTimeMillis()
                    if (currentTimeMillis >= lastReportTimeMillis + PROGRESS_INTERVAL_MILLIS) {
                        lastReportTimeMillis = currentTimeMillis
                        report()
                    }
                    return FileVisitResult.CONTINUE
                }
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
        // Report the final result only if this walk is still the current one.
        if (myGeneration == generation) {
            val snapshot = HashMap(childSizes)
            mainHandler.post {
                if (myGeneration == generation) {
                    listener?.onSizes(snapshot, total, true)
                }
            }
        }
    }

    companion object {
        private const val PROGRESS_INTERVAL_MILLIS = 250L
    }
}
