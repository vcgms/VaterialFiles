package dev.vcgms.aapp.vaterialfiles.filelist

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java8.nio.file.Path
import dev.vcgms.aapp.vaterialfiles.R
import dev.vcgms.aapp.vaterialfiles.app.clipboardManager
import dev.vcgms.aapp.vaterialfiles.databinding.FileListPaneBinding
import dev.vcgms.aapp.vaterialfiles.file.FileItem
import dev.vcgms.aapp.vaterialfiles.file.MimeType
import dev.vcgms.aapp.vaterialfiles.file.fileProviderUri
import dev.vcgms.aapp.vaterialfiles.file.isApk
import dev.vcgms.aapp.vaterialfiles.file.isImage
import dev.vcgms.aapp.vaterialfiles.filejob.FileJobService
import dev.vcgms.aapp.vaterialfiles.filelist.FileSortOptions.By
import dev.vcgms.aapp.vaterialfiles.filelist.FileSortOptions.Order
import dev.vcgms.aapp.vaterialfiles.fileproperties.FilePropertiesDialogFragment
import dev.vcgms.aapp.vaterialfiles.navigation.BookmarkDirectories
import dev.vcgms.aapp.vaterialfiles.navigation.BookmarkDirectory
import dev.vcgms.aapp.vaterialfiles.provider.archive.isArchivePath
import dev.vcgms.aapp.vaterialfiles.provider.linux.isLinuxPath
import dev.vcgms.aapp.vaterialfiles.settings.Settings
import dev.vcgms.aapp.vaterialfiles.util.Failure
import dev.vcgms.aapp.vaterialfiles.util.Loading
import dev.vcgms.aapp.vaterialfiles.util.Stateful
import dev.vcgms.aapp.vaterialfiles.util.Success
import dev.vcgms.aapp.vaterialfiles.util.copyText
import dev.vcgms.aapp.vaterialfiles.util.create
import dev.vcgms.aapp.vaterialfiles.util.createInstallPackageIntent
import dev.vcgms.aapp.vaterialfiles.util.createIntent
import dev.vcgms.aapp.vaterialfiles.util.createSendStreamIntent
import dev.vcgms.aapp.vaterialfiles.util.createViewIntent
import dev.vcgms.aapp.vaterialfiles.util.extraPath
import dev.vcgms.aapp.vaterialfiles.util.fadeToVisibilityUnsafe
import dev.vcgms.aapp.vaterialfiles.util.hasSw600Dp
import dev.vcgms.aapp.vaterialfiles.util.isOrientationLandscape
import dev.vcgms.aapp.vaterialfiles.util.putArgs
import dev.vcgms.aapp.vaterialfiles.util.showToast
import dev.vcgms.aapp.vaterialfiles.util.startActivitySafe
import dev.vcgms.aapp.vaterialfiles.util.valueCompat
import dev.vcgms.aapp.vaterialfiles.util.viewModels
import dev.vcgms.aapp.vaterialfiles.util.withChooser
import dev.vcgms.aapp.vaterialfiles.viewer.image.ImageViewerActivity

// A self-contained file list pane used in dual-pane mode: list content only, with its own
// FileListViewModel. Interactions (open, long-press action menu, etc.) mirror FileListFragment,
// and move/copy to the other pane is offered through the per-item action menu.
class FileListPaneFragment : Fragment(), FileListAdapter.Listener,
    ConfirmDeleteFilesDialogFragment.Listener, RenameFileDialogFragment.Listener,
    CreateArchiveDialogFragment.Listener, OpenApkDialogFragment.Listener,
    FileNameDialogFragment.Listener {

    val viewModel by viewModels { { FileListViewModel() } }

    // The view model is only accessible once this fragment is attached to its activity, which
    // happens asynchronously after the host adds it with a commit() transaction.
    val viewModelOrNull: FileListViewModel?
        get() = if (isAdded) viewModel else null

    private var pendingInitialPath: Path? = null

    private var isActive = false

    // Called when this pane's breadcrumb changes so the host can mirror it in the top bar.
    var breadcrumbListener: ((BreadcrumbData) -> Unit)? = null

    val breadcrumbDataOrNull: BreadcrumbData?
        get() = viewModelOrNull?.breadcrumbLiveData?.valueCompat

    // Called when this pane's paste state changes so the host can update the bottom paste bar.
    var pasteStateListener: (() -> Unit)? = null

    // Called when this pane's file list changes so the host can refresh the toolbar subtitle.
    var subtitleListener: (() -> Unit)? = null

    private lateinit var binding: FileListPaneBinding

    private lateinit var layoutManager: GridLayoutManager

    private lateinit var adapter: FileListAdapter

    var paneId: Int = DualPaneController.PANE_LEFT

    // Called when the user taps this pane so the host can make it the active pane.
    var activeChangedListener: ((FileListPaneFragment) -> Unit)? = null

    val currentPath: Path
        get() = viewModel.currentPath

    val pasteState: PasteState
        get() = viewModel.pasteState

    fun initialize(path: Path) {
        pendingInitialPath = path
        applyInitialPathIfPossible()
    }

    fun setActive(isActive: Boolean) {
        this.isActive = isActive
        if (::binding.isInitialized) {
            binding.paneActiveIndicator.isVisible = isActive
        }
    }

    fun reload() {
        viewModel.reload()
    }

    fun addToPasteState(copy: Boolean, file: FileItem) {
        viewModel.addToPasteState(copy, fileItemSetOf(file))
    }

    fun pasteInto(targetDirectory: Path) {
        val pasteState = viewModel.pasteState
        if (pasteState.copy) {
            FileJobService.copy(makePathListForJob(pasteState.files), targetDirectory, requireContext())
        } else {
            FileJobService.move(makePathListForJob(pasteState.files), targetDirectory, requireContext())
        }
        viewModel.clearPasteState()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        FileListPaneBinding.inflate(inflater, container, false)
            .also { binding = it }
            .root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.paneActiveIndicator.isVisible = isActive
        val activity = requireActivity()
        if (!(activity.hasSw600Dp && activity.isOrientationLandscape)) {
            binding.paneSwipeRefreshLayout.setProgressViewEndTarget(
                true, binding.paneSwipeRefreshLayout.progressViewEndOffset
            )
        }
        binding.paneSwipeRefreshLayout.setOnRefreshListener { viewModel.reload() }
        layoutManager = GridLayoutManager(requireContext(), 1)
        binding.paneRecyclerView.layoutManager = layoutManager
        adapter = FileListAdapter(this).apply {
            hostFragment = this@FileListPaneFragment
            // Dual-pane columns hide the per-item menu button and show its menu on long press.
            isMenuButtonVisible = false
            isDenseStyle = true
            moveToOtherPaneListener = { file, copy -> onMoveCopyToOtherPane(file, copy) }
            moveToOtherPaneTitle = getString(
                if (paneId == DualPaneController.PANE_LEFT) {
                    R.string.file_item_action_move_to_right
                } else {
                    R.string.file_item_action_move_to_left
                }
            )
            copyToOtherPaneTitle = getString(
                if (paneId == DualPaneController.PANE_LEFT) {
                    R.string.file_item_action_copy_to_right
                } else {
                    R.string.file_item_action_copy_to_left
                }
            )
            moveToOtherPaneIconRes = if (paneId == DualPaneController.PANE_LEFT) {
                R.drawable.arrow_end_icon_control_normal_24dp
            } else {
                R.drawable.arrow_start_icon_control_normal_24dp
            }
            copyToOtherPaneIconRes = R.drawable.copy_icon_control_normal_24dp
        }
        binding.paneRecyclerView.adapter = adapter
        binding.root.setOnClickListener { activeChangedListener?.invoke(this) }
        // Any touch or scroll inside this pane makes it the active one, so the top bar breadcrumb
        // and back navigation always follow the pane the user is interacting with.
        binding.paneRecyclerView.addOnItemTouchListener(
            object : RecyclerView.SimpleOnItemTouchListener() {
                override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                    if (e.actionMasked == MotionEvent.ACTION_DOWN) {
                        activeChangedListener?.invoke(this@FileListPaneFragment)
                    }
                    return false
                }
            }
        )
        binding.paneRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                if (newState != RecyclerView.SCROLL_STATE_IDLE) {
                    activeChangedListener?.invoke(this@FileListPaneFragment)
                }
            }
        })
        childFragmentManager.setFragmentResultListener(
            FileActionsDialogFragment.REQUEST_KEY, viewLifecycleOwner
        ) { _, bundle ->
            adapter.onItemActionsDialogResult(bundle.getInt(FileActionsDialogFragment.KEY_ACTION_ID))
        }

        val viewLifecycleOwner = viewLifecycleOwner
        viewModel.breadcrumbLiveData.observe(viewLifecycleOwner) { breadcrumbListener?.invoke(it) }
        // These observers exist only to activate the lazy mapped live data; otherwise reading
        // currentPath / isViewSortPathSpecific (through valueCompat) would fail with a null value.
        viewModel.currentPathLiveData.observe(viewLifecycleOwner) {}
        viewModel.viewSortPathSpecificLiveData.observe(viewLifecycleOwner) {}
        viewModel.viewTypeLiveData.observe(viewLifecycleOwner) { onViewTypeChanged(it) }
        Settings.FILE_LIST_DUAL_PANE.observe(viewLifecycleOwner) { onDualPaneChanged(it) }
        viewModel.sortOptionsLiveData.observe(viewLifecycleOwner) { adapter.sortOptions = it }
        viewModel.selectedFilesLiveData.observe(viewLifecycleOwner) { adapter.replaceSelectedFiles(it) }
        viewModel.fileListLiveData.observe(viewLifecycleOwner) { onFileListChanged(it) }
        Settings.FILE_LIST_SHOW_HIDDEN_FILES.observe(viewLifecycleOwner) { updateAdapterFileList() }
        Settings.FILE_NAME_ELLIPSIZE.observe(viewLifecycleOwner) { adapter.nameEllipsize = it }
        Settings.FILE_LIST_GRID_COLUMNS.observe(viewLifecycleOwner) { updateSpanCount() }
        viewModel.pasteStateLiveData.observe(viewLifecycleOwner) { pasteStateListener?.invoke() }
        applyInitialPathIfPossible()
    }

    private fun applyInitialPathIfPossible() {
        val path = pendingInitialPath ?: return
        // The fragment is not attached yet right after the host's commit() transaction, and
        // accessing the view model would throw; defer until onViewCreated (where isAdded is true).
        if (!isAdded) {
            return
        }
        pendingInitialPath = null
        if (!viewModel.hasTrail) {
            viewModel.resetTo(path)
        }
    }

    private fun onViewTypeChanged(viewType: FileViewType) {
        updateSpanCount()
        adapter.viewType = effectiveViewType()
    }

    private fun onDualPaneChanged(enabled: Boolean) {
        if (enabled) {
            // Dual-pane columns always use the list layout, never grid.
            viewModel.viewType = FileViewType.LIST
        }
        updateSpanCount()
        adapter.viewType = effectiveViewType()
    }

    private fun effectiveViewType(): FileViewType =
        if (Settings.FILE_LIST_DUAL_PANE.valueCompat) FileViewType.LIST else viewModel.viewType

    private fun updateSpanCount() {
        layoutManager.spanCount = when (effectiveViewType()) {
            FileViewType.LIST -> 1
            FileViewType.GRID -> Settings.FILE_LIST_GRID_COLUMNS.valueCompat.count
        }
    }

    private fun onFileListChanged(stateful: Stateful<List<FileItem>>) {
        val files = stateful.value
        val hasFiles = !files.isNullOrEmpty()
        binding.paneSwipeRefreshLayout.isRefreshing = stateful is Loading && hasFiles
        binding.paneProgress.fadeToVisibilityUnsafe(stateful is Loading && !hasFiles)
        binding.paneErrorText.fadeToVisibilityUnsafe(stateful is Failure && !hasFiles)
        val throwable = (stateful as? Failure)?.throwable
        if (throwable != null) {
            throwable.printStackTrace()
            val error = throwable.toString()
            if (hasFiles) {
                showToast(error)
            } else {
                binding.paneErrorText.text = error
            }
        }
        binding.paneEmptyView.fadeToVisibilityUnsafe(stateful is Success && !hasFiles)
        if (files != null) {
            updateAdapterFileList()
        } else {
            adapter.clear()
        }
        if (stateful is Success) {
            viewModel.pendingState?.let { layoutManager.onRestoreInstanceState(it) }
        }
        subtitleListener?.invoke()
    }

    private fun updateAdapterFileList() {
        var files = viewModel.fileListStateful.value ?: return
        if (!Settings.FILE_LIST_SHOW_HIDDEN_FILES.valueCompat) {
            files = files.filterNot { it.isHidden }
        }
        adapter.replaceListAndIsSearching(files, viewModel.searchState.isSearching)
    }

    fun navigateToPath(path: Path) {
        val state = layoutManager.onSaveInstanceState()
        viewModel.navigateTo(state!!, path)
    }

    override fun clearSelectedFiles() {
        viewModel.clearSelectedFiles()
    }

    override fun selectFile(file: FileItem, selected: Boolean) {
        viewModel.selectFile(file, selected)
    }

    override fun selectFiles(files: FileItemSet, selected: Boolean) {
        viewModel.selectFiles(files, selected)
    }

    override fun openFile(file: FileItem) {
        if (file.mimeType.isApk) {
            openApk(file)
            return
        }
        if (file.isListable) {
            navigateToPath(file.listablePath)
            return
        }
        openFileWithIntent(file, false)
    }

    private fun openApk(file: FileItem) {
        if (!file.isListable) {
            installApk(file)
            return
        }
        when (Settings.OPEN_APK_DEFAULT_ACTION.valueCompat) {
            OpenApkDefaultAction.INSTALL -> installApk(file)
            OpenApkDefaultAction.VIEW -> viewApk(file)
            OpenApkDefaultAction.ASK -> OpenApkDialogFragment.show(file, this)
        }
    }

    override fun installApk(file: FileItem) {
        val path = file.path
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (!path.isArchivePath) path.fileProviderUri else null
        } else {
            // PackageInstaller only supports file URI before N.
            if (path.isLinuxPath) Uri.fromFile(path.toFile()) else null
        }
        if (uri != null) {
            startActivitySafe(uri.createInstallPackageIntent())
        } else {
            FileJobService.installApk(path, requireContext())
        }
    }

    override fun viewApk(file: FileItem) {
        navigateToPath(file.listablePath)
    }

    override fun openFileWith(file: FileItem) {
        openFileWithIntent(file, true)
    }

    private fun openFileWithIntent(file: FileItem, withChooser: Boolean) {
        val path = file.path
        val mimeType = file.mimeType
        if (path.isArchivePath) {
            FileJobService.open(path, mimeType, withChooser, requireContext())
        } else {
            val intent = path.fileProviderUri.createViewIntent(mimeType)
                .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                .apply {
                    extraPath = path
                    maybeAddImageViewerActivityExtras(this, path, mimeType)
                }
                .let {
                    if (withChooser) {
                        it.withChooser(
                            EditFileActivity::class.createIntent()
                                .putArgs(EditFileActivity.Args(path, mimeType)),
                            OpenFileAsDialogActivity::class.createIntent()
                                .putArgs(OpenFileAsDialogFragment.Args(path))
                        )
                    } else {
                        it
                    }
                }
            startActivitySafe(intent)
        }
    }

    private fun maybeAddImageViewerActivityExtras(intent: Intent, path: Path, mimeType: MimeType) {
        if (!mimeType.isImage) {
            return
        }
        var paths = mutableListOf<Path>()
        for (index in 0..<adapter.itemCount) {
            val file = adapter.getItem(index)
            val filePath = file.path
            if (file.mimeType.isImage || filePath == path) {
                paths.add(filePath)
            }
        }
        var position = paths.indexOf(path)
        if (position == -1) {
            return
        }
        if (paths.size > IMAGE_VIEWER_ACTIVITY_PATH_LIST_SIZE_MAX) {
            val start = (position - IMAGE_VIEWER_ACTIVITY_PATH_LIST_SIZE_MAX / 2)
                .coerceIn(0, paths.size - IMAGE_VIEWER_ACTIVITY_PATH_LIST_SIZE_MAX)
            paths = paths.subList(start, start + IMAGE_VIEWER_ACTIVITY_PATH_LIST_SIZE_MAX)
            position -= start
        }
        ImageViewerActivity.putExtras(intent, paths, position)
    }

    override fun cutFile(file: FileItem) {
        cutFiles(fileItemSetOf(file))
    }

    override fun copyFile(file: FileItem) {
        copyFiles(fileItemSetOf(file))
    }

    private fun cutFiles(files: FileItemSet) {
        viewModel.addToPasteState(false, files)
        viewModel.selectFiles(files, false)
    }

    private fun copyFiles(files: FileItemSet) {
        viewModel.addToPasteState(true, files)
        viewModel.selectFiles(files, false)
    }

    override fun confirmDeleteFile(file: FileItem) {
        ConfirmDeleteFilesDialogFragment.show(fileItemSetOf(file), this)
    }

    override fun deleteFiles(files: FileItemSet) {
        FileJobService.delete(makePathListForJob(files), requireContext())
        viewModel.selectFiles(files, false)
    }

    override fun showRenameFileDialog(file: FileItem) {
        RenameFileDialogFragment.show(file, this)
    }

    override fun hasFileWithName(name: String): Boolean = getFileWithName(name) != null

    private fun getFileWithName(name: String): FileItem? {
        val fileListData = viewModel.fileListStateful
        if (fileListData !is Success) {
            return null
        }
        return fileListData.value.find { it.name == name }
    }

    override fun renameFile(file: FileItem, newName: String) {
        FileJobService.rename(file.path, newName, requireContext())
        viewModel.selectFile(file, false)
    }

    override fun extractFile(file: FileItem) {
        copyFile(file.createDummyArchiveRoot())
    }

    override fun showCreateArchiveDialog(file: FileItem) {
        showCreateArchiveDialog(fileItemSetOf(file))
    }

    private fun showCreateArchiveDialog(files: FileItemSet) {
        CreateArchiveDialogFragment.show(files, this)
    }

    override fun archive(
        files: FileItemSet,
        name: String,
        format: Int,
        filter: Int,
        password: String?
    ) {
        val archiveFile = viewModel.currentPath.resolve(name)
        FileJobService.archive(
            makePathListForJob(files), archiveFile, format, filter, password, requireContext()
        )
        viewModel.selectFiles(files, false)
    }

    override fun shareFile(file: FileItem) {
        shareFiles(listOf(file.path), listOf(file.mimeType))
    }

    private fun shareFiles(paths: List<Path>, mimeTypes: List<MimeType>) {
        val uris = paths.map { it.fileProviderUri }
        val intent = uris.createSendStreamIntent(mimeTypes)
            .withChooser()
        startActivitySafe(intent)
    }

    override fun copyPath(file: FileItem) {
        clipboardManager.copyText(file.path.toUserFriendlyString(), requireContext())
    }

    override fun addBookmark(file: FileItem) {
        BookmarkDirectories.add(BookmarkDirectory(null, file.path))
        showToast(R.string.file_add_bookmark_success)
    }

    override fun createShortcut(file: FileItem) {
        val context = requireContext()
        val path = file.path
        val mimeType = file.mimeType
        val isDirectory = mimeType == MimeType.DIRECTORY
        val shortcutInfo = ShortcutInfoCompat.Builder(context, path.toString())
            .setShortLabel(path.name)
            .setIntent(
                if (isDirectory) {
                    FileListActivity.createViewIntent(path)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                } else {
                    OpenFileActivity.createIntent(path, mimeType)
                }
            )
            .setIcon(
                IconCompat.createWithResource(
                    context, if (isDirectory) {
                        R.mipmap.directory_shortcut_icon
                    } else {
                        R.mipmap.file_shortcut_icon
                    }
                )
            )
            .build()
        ShortcutManagerCompat.requestPinShortcut(context, shortcutInfo, null)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            showToast(R.string.shortcut_created)
        }
    }

    override fun showPropertiesDialog(file: FileItem) {
        FilePropertiesDialogFragment.show(file, this)
    }

    private fun makePathListForJob(files: FileItemSet): List<Path> =
        files.map { it.path }.sortedBy { it.toUri() }

    // Moves or copies the file into the other pane's current path, reusing the paste channel so
    // that progress notification and conflict handling stay identical to regular paste.
    private fun onMoveCopyToOtherPane(file: FileItem, copy: Boolean) {
        val otherPane = DualPaneController.findPane(
            if (paneId == DualPaneController.PANE_LEFT) {
                DualPaneController.PANE_RIGHT
            } else {
                DualPaneController.PANE_LEFT
            }
        ) ?: return
        addToPasteState(copy, file)
        pasteInto(otherPane.currentPath)
    }

    private companion object {
        const val IMAGE_VIEWER_ACTIVITY_PATH_LIST_SIZE_MAX = 1000
    }
}
