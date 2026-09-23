package dev.vcgms.aapp.vaterialfiles.filelist

// In-memory state shared between the two panes and the host fragment: the active pane id and the
// pane registry.
object DualPaneController {
    const val PANE_LEFT = 0
    const val PANE_RIGHT = 1

    var activePaneId: Int = PANE_LEFT

    private val panes = mutableMapOf<Int, FileListPaneFragment>()

    fun register(paneId: Int, pane: FileListPaneFragment) {
        panes[paneId] = pane
    }

    fun unregister(paneId: Int) {
        panes.remove(paneId)
    }

    fun findPane(paneId: Int): FileListPaneFragment? = panes[paneId]
}
