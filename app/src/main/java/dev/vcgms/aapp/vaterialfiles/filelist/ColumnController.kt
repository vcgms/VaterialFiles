package dev.vcgms.aapp.vaterialfiles.filelist

import dev.vcgms.aapp.vaterialfiles.file.FileItem

// In-memory state for column view mode: the column registry keyed by column index, the active
// column, and the in-flight cross-column drag. Analogous to DualPaneController but supports an
// arbitrary number of columns.
object ColumnController {
    private val columns = mutableMapOf<Int, FileListPaneFragment>()

    var activeColumnIndex: Int = 0

    // The file being dragged between columns and the column it was dragged from, cleared once
    // the drag ends.
    var draggingFile: FileItem? = null

    var draggingColumnIndex: Int = -1

    fun register(index: Int, pane: FileListPaneFragment) {
        columns[index] = pane
    }

    fun unregister(index: Int) {
        columns.remove(index)
    }

    fun findColumn(index: Int): FileListPaneFragment? = columns[index]

    fun clear() {
        columns.clear()
        activeColumnIndex = 0
        draggingFile = null
        draggingColumnIndex = -1
    }
}
