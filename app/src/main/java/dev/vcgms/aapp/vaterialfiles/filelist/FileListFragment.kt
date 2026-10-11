package dev.vcgms.aapp.vaterialfiles.filelist

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.res.ColorStateList
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.graphics.Paint
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ImageSpan
import android.view.DragEvent
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePaddingRelative
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commit
import androidx.fragment.app.commitNow
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.color.MaterialColors
import com.leinardi.android.speeddial.SpeedDialView
import java8.nio.file.Path
import java8.nio.file.Paths
import kotlin.math.roundToInt
import kotlinx.parcelize.Parcelize
import dev.vcgms.aapp.vaterialfiles.R
import dev.vcgms.aapp.vaterialfiles.app.application
import dev.vcgms.aapp.vaterialfiles.app.clipboardManager
import dev.vcgms.aapp.vaterialfiles.compat.checkSelfPermissionCompat
import dev.vcgms.aapp.vaterialfiles.compat.getDrawableCompat
import dev.vcgms.aapp.vaterialfiles.compat.setGroupDividerEnabledCompat
import dev.vcgms.aapp.vaterialfiles.databinding.FileListFragmentAppBarIncludeBinding
import dev.vcgms.aapp.vaterialfiles.databinding.FileListFragmentBinding
import dev.vcgms.aapp.vaterialfiles.databinding.FileListFragmentBottomBarIncludeBinding
import dev.vcgms.aapp.vaterialfiles.databinding.FileListFragmentContentIncludeBinding
import dev.vcgms.aapp.vaterialfiles.databinding.FileListFragmentIncludeBinding
import dev.vcgms.aapp.vaterialfiles.databinding.FileListFragmentSpeedDialIncludeBinding
import dev.vcgms.aapp.vaterialfiles.databinding.FileListOverflowMenuBinding
import dev.vcgms.aapp.vaterialfiles.databinding.FileListSelectOverflowMenuBinding
import dev.vcgms.aapp.vaterialfiles.databinding.FileListViewSortMenuBinding
import dev.vcgms.aapp.vaterialfiles.file.FileItem
import dev.vcgms.aapp.vaterialfiles.file.MimeType
import dev.vcgms.aapp.vaterialfiles.file.asFileSize
import dev.vcgms.aapp.vaterialfiles.file.asMimeTypeOrNull
import dev.vcgms.aapp.vaterialfiles.file.extension
import dev.vcgms.aapp.vaterialfiles.file.fileProviderUri
import dev.vcgms.aapp.vaterialfiles.file.isApk
import dev.vcgms.aapp.vaterialfiles.file.isImage
import dev.vcgms.aapp.vaterialfiles.file.isVideo
import dev.vcgms.aapp.vaterialfiles.filejob.FileJobService
import dev.vcgms.aapp.vaterialfiles.filelist.FileSortOptions.By
import dev.vcgms.aapp.vaterialfiles.filelist.FileSortOptions.Order
import dev.vcgms.aapp.vaterialfiles.fileproperties.FilePropertiesDialogFragment
import dev.vcgms.aapp.vaterialfiles.imageinfo.startImageInfoActivity
import dev.vcgms.aapp.vaterialfiles.imageedit.startImageReencodeActivity
import dev.vcgms.aapp.vaterialfiles.videoinfo.startVideoInfoActivity
import dev.vcgms.aapp.vaterialfiles.navigation.BookmarkDirectories
import dev.vcgms.aapp.vaterialfiles.navigation.BookmarkDirectory
import dev.vcgms.aapp.vaterialfiles.navigation.NavigationFragment
import dev.vcgms.aapp.vaterialfiles.navigation.NavigationRootMapLiveData
import dev.vcgms.aapp.vaterialfiles.provider.archive.createArchiveRootPath
import dev.vcgms.aapp.vaterialfiles.provider.archive.isArchivePath
import dev.vcgms.aapp.vaterialfiles.provider.linux.isLinuxPath
import dev.vcgms.aapp.vaterialfiles.settings.Settings
import dev.vcgms.aapp.vaterialfiles.settings.FileNameEllipsize
import dev.vcgms.aapp.vaterialfiles.terminal.Terminal
import dev.vcgms.aapp.vaterialfiles.ui.AppBarLayoutExpandHackListener
import dev.vcgms.aapp.vaterialfiles.ui.CoordinatorAppBarLayout
import dev.vcgms.aapp.vaterialfiles.ui.DrawerLayoutOnBackPressedCallback
import dev.vcgms.aapp.vaterialfiles.ui.FixQueryChangeSearchView
import dev.vcgms.aapp.vaterialfiles.ui.OverlayToolbarActionMode
import dev.vcgms.aapp.vaterialfiles.ui.PersistentBarLayout
import dev.vcgms.aapp.vaterialfiles.ui.PersistentBarLayoutToolbarActionMode
import dev.vcgms.aapp.vaterialfiles.ui.PersistentDrawerLayout
import dev.vcgms.aapp.vaterialfiles.ui.ScrollingViewOnApplyWindowInsetsListener
import dev.vcgms.aapp.vaterialfiles.ui.SpeedDialViewOnBackPressedCallback
import dev.vcgms.aapp.vaterialfiles.ui.ThemedFastScroller
import dev.vcgms.aapp.vaterialfiles.ui.ToolbarActionMode
import dev.vcgms.aapp.vaterialfiles.util.DebouncedRunnable
import dev.vcgms.aapp.vaterialfiles.util.Failure
import dev.vcgms.aapp.vaterialfiles.util.Loading
import dev.vcgms.aapp.vaterialfiles.util.ParcelableArgs
import dev.vcgms.aapp.vaterialfiles.util.Stateful
import dev.vcgms.aapp.vaterialfiles.util.Success
import dev.vcgms.aapp.vaterialfiles.util.addOnBackPressedCallback
import dev.vcgms.aapp.vaterialfiles.util.args
import dev.vcgms.aapp.vaterialfiles.util.asFileName
import dev.vcgms.aapp.vaterialfiles.util.asFileNameOrNull
import dev.vcgms.aapp.vaterialfiles.util.checkSelfPermission
import dev.vcgms.aapp.vaterialfiles.util.copyText
import dev.vcgms.aapp.vaterialfiles.util.create
import dev.vcgms.aapp.vaterialfiles.util.createInstallPackageIntent
import dev.vcgms.aapp.vaterialfiles.util.createIntent
import dev.vcgms.aapp.vaterialfiles.util.createManageAppAllFilesAccessPermissionIntent
import dev.vcgms.aapp.vaterialfiles.util.createSendStreamIntent
import dev.vcgms.aapp.vaterialfiles.util.createViewIntent
import dev.vcgms.aapp.vaterialfiles.util.extraPath
import dev.vcgms.aapp.vaterialfiles.util.extraPathList
import dev.vcgms.aapp.vaterialfiles.util.fadeToVisibilityUnsafe
import dev.vcgms.aapp.vaterialfiles.util.getDimensionDp
import dev.vcgms.aapp.vaterialfiles.util.getQuantityString
import dev.vcgms.aapp.vaterialfiles.util.hasSw600Dp
import dev.vcgms.aapp.vaterialfiles.util.isOrientationLandscape
import dev.vcgms.aapp.vaterialfiles.util.putArgs
import dev.vcgms.aapp.vaterialfiles.util.setOnEditorConfirmActionListener
import dev.vcgms.aapp.vaterialfiles.util.showToast
import dev.vcgms.aapp.vaterialfiles.util.startActivitySafe
import dev.vcgms.aapp.vaterialfiles.util.supportsExternalStorageManager
import dev.vcgms.aapp.vaterialfiles.util.takeIfNotEmpty
import dev.vcgms.aapp.vaterialfiles.util.valueCompat
import dev.vcgms.aapp.vaterialfiles.util.viewModels
import dev.vcgms.aapp.vaterialfiles.util.withChooser
import dev.vcgms.aapp.vaterialfiles.viewer.image.startImageViewer
import dev.vcgms.aapp.vaterialfiles.viewer.video.startVideoViewer

class FileListFragment : Fragment(), BreadcrumbLayout.Listener, FileListAdapter.Listener,
    ConfirmReplaceFileDialogFragment.Listener, OpenApkDialogFragment.Listener,
    ConfirmDeleteFilesDialogFragment.Listener, CreateArchiveDialogFragment.Listener,
    RenameFileDialogFragment.Listener, CreateFileDialogFragment.Listener,
    CreateDirectoryDialogFragment.Listener, NavigateToPathDialogFragment.Listener,
    NavigationFragment.Listener, ShowRequestAllFilesAccessRationaleDialogFragment.Listener,
    ShowRequestNotificationPermissionRationaleDialogFragment.Listener,
    ShowRequestNotificationPermissionInSettingsRationaleDialogFragment.Listener,
    ShowRequestStoragePermissionRationaleDialogFragment.Listener,
    ShowRequestStoragePermissionInSettingsRationaleDialogFragment.Listener {
    private val requestAllFilesAccessLauncher = registerForActivityResult(
        RequestAllFilesAccessContract(), this::onRequestAllFilesAccessResult
    )
    private val requestStoragePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(), this::onRequestStoragePermissionResult
    )
    private val requestStoragePermissionInSettingsLauncher = registerForActivityResult(
        RequestPermissionInSettingsContract(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
        this::onRequestStoragePermissionInSettingsResult
    )
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(), this::onRequestNotificationPermissionResult
    )
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private val requestNotificationPermissionInSettingsLauncher = registerForActivityResult(
        RequestPermissionInSettingsContract(android.Manifest.permission.POST_NOTIFICATIONS),
        this::onRequestNotificationPermissionInSettingsResult
    )

    private val args by args<Args>()
    private val argsPath by lazy { args.intent.extraPath }

    private val viewModel by viewModels { { FileListViewModel() } }

    private lateinit var binding: Binding

    private lateinit var navigationFragment: NavigationFragment

    private lateinit var menuBinding: MenuBinding

    private lateinit var overlayActionMode: ToolbarActionMode

    private lateinit var bottomActionMode: ToolbarActionMode

    private lateinit var layoutManager: GridLayoutManager

    private lateinit var adapter: FileListAdapter

    private var leftPane: FileListPaneFragment? = null

    private var rightPane: FileListPaneFragment? = null

    private var activePane: FileListPaneFragment? = null

    // Column view mode: the column panes in trail order, with their dynamically generated
    // container ids and the path each column is bound to (tracked here because a freshly
    // committed pane's view model is not readable until it attaches).
    private val columnPanes = mutableListOf<FileListPaneFragment>()

    private val columnContainerIds = mutableListOf<Int>()

    private val columnPaths = mutableListOf<Path>()

    // The source file of an in-flight single-pane drag, cleared once the drag ends.
    private var draggingFile: FileItem? = null

    private var pendingSinglePaneDrag: PendingSinglePaneDrag? = null

    private var navigateUpBackCallback: OnBackPressedCallback? = null

    private val storageAnalysisComputer = StorageAnalysisComputer()

    // The accumulated total size reported by the current storage analysis walk, or null before
    // the walk has reported anything.
    private var storageAnalysisTotal: Long? = null

    // Whether a storage analysis walk is currently in progress for the current directory.
    private var isStorageAnalysisComputing = false

    // Whether the user manually stopped the analysis for the current directory; navigating away
    // or an explicit restart clears it.
    private var isStorageAnalysisPaused = false

    // The path the user force-analyzed via the indicator tap, bypassing the path gates.
    private var forcedStorageAnalysisPath: Path? = null

    private val debouncedSearchRunnable = DebouncedRunnable(Handler(Looper.getMainLooper()), 1000) {
        if (!isResumed || !viewModel.isSearchViewExpanded) {
            return@DebouncedRunnable
        }
        val query = viewModel.searchViewQuery
        if (query.isEmpty()) {
            return@DebouncedRunnable
        }
        viewModel.search(query)
    }

    private val isDualPane: Boolean
        get() = Settings.FILE_LIST_DUAL_PANE.valueCompat

    // Column view (Miller columns): one column per trail level, rendered from reusable panes.
    private val isColumnMode: Boolean
        get() = Settings.FILE_LIST_COLUMN_MODE.valueCompat

    // Both multi-pane layouts share the "active pane" routing for view models, selection,
    // paste state and the overlay/bottom toolbars.
    private val isPaneMode: Boolean
        get() = isDualPane || isColumnMode

    // In dual-pane mode, path/paste-related operations act on the active pane's view model;
    // otherwise they act on this fragment's own view model.
    private val activeViewModel: FileListViewModel
        get() = if (isPaneMode) activePane?.viewModelOrNull ?: viewModel else viewModel

    // Selection is per-column in dual-pane mode; the overlay action bar always reflects the
    // active column's selection.
    private val activeSelectedFiles: FileItemSet
        get() = if (isPaneMode) {
            activePane?.viewModelOrNull?.selectedFiles ?: fileItemSetOf()
        } else {
            viewModel.selectedFiles
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        Binding.inflate(inflater, container, false)
            .also { binding = it }
            .root

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        if (savedInstanceState == null) {
            navigationFragment = NavigationFragment()
            childFragmentManager.commit { add(R.id.navigationFragment, navigationFragment) }
        } else {
            navigationFragment = childFragmentManager.findFragmentById(R.id.navigationFragment)
                as NavigationFragment
        }
        navigationFragment.listener = this
        val activity = requireActivity() as AppCompatActivity
        activity.setSupportActionBar(binding.toolbar)
        overlayActionMode = OverlayToolbarActionMode(binding.overlayToolbar, binding.toolbar)
        bottomActionMode = PersistentBarLayoutToolbarActionMode(
            binding.persistentBarLayout, binding.bottomBarLayout, binding.bottomToolbar
        )
        val contentLayoutInitialPaddingBottom = binding.contentLayout.paddingBottom
        binding.appBarLayout.addOnOffsetChangedListener { _, verticalOffset ->
            binding.contentLayout.updatePaddingRelative(
                bottom = contentLayoutInitialPaddingBottom +
                    binding.appBarLayout.totalScrollRange + verticalOffset
            )
        }
        binding.appBarLayout.syncBackgroundColorTo(binding.overlayToolbar)
        binding.breadcrumbLayout.setListener(this)
        binding.breadcrumbLayout.dropListener = ::onBreadcrumbDrop
        binding.breadcrumbLayout.columnMode = isColumnMode
        binding.breadcrumbLayout.columnItemTapListener = ::onBreadcrumbColumnItemTapped
        // The address bar scrolls in 1:1 lockstep with the column container (same item width,
        // same start padding), driven solely by the columns' horizontal scroll offset.
        binding.columnPaneContainer?.setOnScrollChangeListener { _, scrollX, _, _, _ ->
            binding.breadcrumbLayout.scrollTo(scrollX, 0)
        }
        if (isColumnMode) {
            // Column pane containers use dynamically generated ids, so column panes restored by
            // the fragment manager after a process recreation cannot be re-attached; drop them
            // and let renderColumns() rebuild the columns from the trail.
            val restoredPanes = childFragmentManager.fragments.filterIsInstance<FileListPaneFragment>()
            if (restoredPanes.isNotEmpty()) {
                childFragmentManager.commitNow {
                    for (pane in restoredPanes) {
                        remove(pane)
                    }
                }
            }
        }
        if (!(activity.hasSw600Dp && activity.isOrientationLandscape)) {
            binding.swipeRefreshLayout.setProgressViewEndTarget(
                true, binding.swipeRefreshLayout.progressViewEndOffset
            )
        }
        binding.swipeRefreshLayout.setOnRefreshListener { this.refresh() }
        layoutManager = GridLayoutManager(activity, 1)
        binding.recyclerView.layoutManager = layoutManager
        adapter = FileListAdapter(this).apply {
            hostFragment = this@FileListFragment
            // Long press opens the item menu in single-column mode, so this drag callback stays
            // dormant; it can be re-enabled by setting longPressStartsDrag = true.
            itemDragListener = { itemView, file -> startSinglePaneDrag(itemView, file) }
        }
        binding.recyclerView.adapter = adapter
        adapter.installIconMultiSelectGesture(binding.recyclerView)
        binding.recyclerView.setOnDragListener { _, event -> handleSinglePaneDragEvent(event) }
        val fastScroller = ThemedFastScroller.create(binding.recyclerView)
        binding.recyclerView.setOnApplyWindowInsetsListener(
            ScrollingViewOnApplyWindowInsetsListener(binding.recyclerView, fastScroller)
        )
        binding.speedDialView.inflate(R.menu.file_list_speed_dial)
        binding.speedDialView.setOnActionSelectedListener {
            when (it.id) {
                R.id.action_create_file -> showCreateFileDialog()
                R.id.action_create_directory -> showCreateDirectoryDialog()
            }
            // Returning false causes the speed dial to close without animation.
            //return false
            binding.speedDialView.close()
            true
        }

        val viewLifecycleOwner = viewLifecycleOwner
        addOnBackPressedCallback(
            object : OnBackPressedCallback(false) {
                override fun handleOnBackPressed() {
                    navigateUp()
                }
            }
                .also { callback ->
                    navigateUpBackCallback = callback
                    viewModel.breadcrumbLiveData.observe(viewLifecycleOwner) {
                        updateNavigateUpBackCallbackEnabled()
                    }
                }
        )
        addOnBackPressedCallback(overlayActionMode.onBackPressedCallback)
        addOnBackPressedCallback(SpeedDialViewOnBackPressedCallback(binding.speedDialView))
        binding.drawerLayout?.let {
            // Disable the edge swipe gesture; the drawer is opened and closed only through the
            // navigation (hamburger) button, while the back press callback still closes it.
            it.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            addOnBackPressedCallback(DrawerLayoutOnBackPressedCallback(it))
        }

        if (!viewModel.hasTrail) {
            var path = argsPath
            val intent = args.intent
            var pickOptions: PickOptions? = null
            when (val action = intent.action) {
                Intent.ACTION_GET_CONTENT, Intent.ACTION_OPEN_DOCUMENT,
                Intent.ACTION_CREATE_DOCUMENT -> {
                    val mode = if (action == Intent.ACTION_CREATE_DOCUMENT) {
                        PickOptions.Mode.CREATE_FILE
                    } else {
                        PickOptions.Mode.OPEN_FILE
                    }
                    val mimeType = intent.type?.asMimeTypeOrNull() ?: MimeType.ANY
                    val fileName = if (mode == PickOptions.Mode.CREATE_FILE) {
                        intent.getStringExtra(Intent.EXTRA_TITLE)?.asFileNameOrNull()?.value
                            ?: mimeType.extension?.let { "file.$it" } ?: "file"
                    } else {
                        null
                    }
                    val readOnly = action == Intent.ACTION_GET_CONTENT
                    val extraMimeTypes = if (mode == PickOptions.Mode.OPEN_FILE) {
                        intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)
                            ?.mapNotNull { it.asMimeTypeOrNull() }?.takeIfNotEmpty()
                    } else {
                        null
                    }
                    val mimeTypes = extraMimeTypes ?: listOf(mimeType)
                    val localOnly = intent.getBooleanExtra(Intent.EXTRA_LOCAL_ONLY, false)
                    val allowMultiple = mode != PickOptions.Mode.CREATE_FILE &&
                        intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
                    pickOptions =
                        PickOptions(mode, fileName, readOnly, mimeTypes, localOnly, allowMultiple)
                }
                Intent.ACTION_OPEN_DOCUMENT_TREE -> {
                    val localOnly = intent.getBooleanExtra(Intent.EXTRA_LOCAL_ONLY, false)
                    pickOptions = PickOptions(
                        PickOptions.Mode.OPEN_DIRECTORY, null, false, emptyList(), localOnly, false
                    )
                }
                ACTION_VIEW_DOWNLOADS ->
                    path = Paths.get(
                        Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS
                        ).path
                    )
                else ->
                    if (path != null) {
                        val mimeType = intent.type?.asMimeTypeOrNull()
                        if (mimeType != null && path.isArchiveFile(mimeType)) {
                            path = path.createArchiveRootPath()
                        }
                    }
            }
            // Restoring the last directory only applies to normal browsing; a pick intent should
            // stay neutral and start from the default directory.
            if (path == null && pickOptions == null) {
                path = restoreLastDirectory()
            }
            if (path == null) {
                path = Settings.FILE_LIST_DEFAULT_DIRECTORY.valueCompat
            }
            viewModel.resetTo(path)
            if (pickOptions != null) {
                viewModel.pickOptions = pickOptions
            }
        }
        viewModel.currentPathLiveData.observe(viewLifecycleOwner) { onCurrentPathChanged(it) }
        viewModel.searchViewExpandedLiveData.observe(viewLifecycleOwner) {
            onSearchViewExpandedChanged(it)
        }
        viewModel.breadcrumbLiveData.observe(viewLifecycleOwner) {
            if (!isDualPane) {
                // In dual-pane mode the active pane drives the address bar instead.
                binding.breadcrumbLayout.setData(it)
            }
            if (isColumnMode) {
                renderColumns(it.paths)
            }
        }
        viewModel.viewTypeLiveData.observe(viewLifecycleOwner) { onViewTypeChanged(it) }
        // Live data only calls observeForever() on its sources when it is active, so we have to
        // make view type live data active first (so that it can load its initial value) before we
        // register another observer that needs to get the view type.
        if (binding.persistentDrawerLayout != null) {
            Settings.FILE_LIST_PERSISTENT_DRAWER_OPEN.observe(viewLifecycleOwner) {
                onPersistentDrawerOpenChanged(it)
            }
        }
        viewModel.sortOptionsLiveData.observe(viewLifecycleOwner) { onSortOptionsChanged(it) }
        viewModel.viewSortPathSpecificLiveData.observe(viewLifecycleOwner) {
            onViewSortPathSpecificChanged(it)
        }
        viewModel.pickOptionsLiveData.observe(viewLifecycleOwner) { onPickOptionsChanged(it) }
        viewModel.selectedFilesLiveData.observe(viewLifecycleOwner) { onSelectedFilesChanged(it) }
        viewModel.pasteStateLiveData.observe(viewLifecycleOwner) { onPasteStateChanged(it) }
        Settings.FILE_NAME_ELLIPSIZE.observe(viewLifecycleOwner) { onFileNameEllipsizeChanged(it) }
        Settings.FILE_LIST_GRID_COLUMNS.observe(viewLifecycleOwner) { updateSpanCount() }
        viewModel.fileListLiveData.observe(viewLifecycleOwner) { onFileListChanged(it) }
        Settings.FILE_LIST_SHOW_HIDDEN_FILES.observe(viewLifecycleOwner) {
            onShowHiddenFilesChanged(it)
        }
        Settings.FILE_LIST_DUAL_PANE.observe(viewLifecycleOwner) { onDualPaneChanged(it) }
        Settings.FILE_LIST_COLUMN_MODE.observe(viewLifecycleOwner) { onColumnModeChanged(it) }
        Settings.FILE_LIST_STORAGE_ANALYSIS.observe(viewLifecycleOwner) {
            onStorageAnalysisChanged(it)
        }
        Settings.FILE_LIST_STORAGE_ANALYSIS_ONLY_USER_SUBDIRECTORIES.observe(viewLifecycleOwner) {
            startStorageAnalysisIfEnabled()
        }
        Settings.FILE_LIST_STORAGE_ANALYSIS_REMOTE_DIRECTORIES.observe(viewLifecycleOwner) {
            startStorageAnalysisIfEnabled()
        }
        binding.storageAnalysisIndicator.setOnClickListener {
            onStorageAnalysisIndicatorClicked()
        }
        binding.storageAnalysisIndicator.setOnLongClickListener {
            onStorageAnalysisIndicatorLongClicked()
            true
        }
        childFragmentManager.setFragmentResultListener(
            MoveCopyDialogFragment.REQUEST_KEY, viewLifecycleOwner
        ) { _, bundle ->
            onMoveCopyDialogResult(bundle.getInt(MoveCopyDialogFragment.KEY_RESULT))
        }
        childFragmentManager.setFragmentResultListener(
            FileActionsDialogFragment.REQUEST_KEY, viewLifecycleOwner
        ) { _, bundle ->
            adapter.onItemActionsDialogResult(bundle.getInt(FileActionsDialogFragment.KEY_ACTION_ID))
        }
    }

    override fun onResume() {
        super.onResume()

        if (!viewModel.isNotificationPermissionRequested) {
            ensureStorageAccess()
        }
        if (!viewModel.isStorageAccessRequested) {
            ensureNotificationPermission()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        storageAnalysisComputer.cancel()
        storageAnalysisTotal = null
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)

        menuBinding = MenuBinding.inflate(menu, inflater)
        setUpSearchView()
    }

    private fun setUpSearchView() {
        val searchView = menuBinding.searchItem.actionView as FixQueryChangeSearchView
        // MenuItem.OnActionExpandListener.onMenuItemActionExpand() is called before SearchView
        // resets the query.
        searchView.setOnSearchClickListener {
            viewModel.isSearchViewExpanded = true
            searchView.setQuery(viewModel.searchViewQuery, false)
            debouncedSearchRunnable()
        }
        // SearchView.OnCloseListener.onClose() is not always called.
        menuBinding.searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean = true

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                viewModel.isSearchViewExpanded = false
                viewModel.stopSearching()
                return true
            }
        })
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                debouncedSearchRunnable.cancel()
                viewModel.search(query)
                return true
            }

            override fun onQueryTextChange(query: String): Boolean {
                if (searchView.shouldIgnoreQueryChange) {
                    return false
                }
                viewModel.searchViewQuery = query
                debouncedSearchRunnable()
                return false
            }
        })
        if (viewModel.isSearchViewExpanded) {
            menuBinding.searchItem.expandActionView()
        }
    }

    private fun collapseSearchView() {
        if (this::menuBinding.isInitialized && menuBinding.searchItem.isActionViewExpanded) {
            menuBinding.searchItem.collapseActionView()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                binding.drawerLayout?.openDrawer(GravityCompat.START)
                if (binding.persistentDrawerLayout != null) {
                    Settings.FILE_LIST_PERSISTENT_DRAWER_OPEN.putValue(
                        !Settings.FILE_LIST_PERSISTENT_DRAWER_OPEN.valueCompat
                    )
                }
                true
            }
            R.id.action_view_sort -> {
                showViewSortMenu()
                true
            }
            R.id.action_overflow -> {
                showOverflowMenu()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private var overflowPopup: PopupWindow? = null

    private fun showOverflowMenu() {
        overflowPopup?.dismiss()
        val overflowBinding = FileListOverflowMenuBinding.inflate(layoutInflater)
        val pickOptions = viewModel.pickOptions
        overflowBinding.overflowSelectAll.isVisible =
            !isPaneMode && (pickOptions == null || pickOptions.allowMultiple)
        overflowBinding.overflowShowHiddenCheck.isVisible =
            Settings.FILE_LIST_SHOW_HIDDEN_FILES.valueCompat
        overflowBinding.overflowStorageAnalysisCheck.isVisible =
            Settings.FILE_LIST_STORAGE_ANALYSIS.valueCompat
        fun bindClick(view: View, action: () -> Unit) {
            view.setOnClickListener {
                overflowPopup?.dismiss()
                action()
            }
        }
        bindClick(overflowBinding.overflowNavigateUp) { navigateUp() }
        bindClick(overflowBinding.overflowRefresh) { refresh() }
        bindClick(overflowBinding.overflowSelectAll) { selectAllFiles() }
        bindClick(overflowBinding.overflowShare) { share() }
        bindClick(overflowBinding.overflowNewTask) { newTask() }
        bindClick(overflowBinding.overflowNavigateTo) { showNavigateToPathDialog() }
        bindClick(overflowBinding.overflowShowHiddenFiles) {
            setShowHiddenFiles(!Settings.FILE_LIST_SHOW_HIDDEN_FILES.valueCompat)
        }
        bindClick(overflowBinding.overflowStorageAnalysis) { toggleStorageAnalysis() }
        bindClick(overflowBinding.overflowCopyPath) { copyPath() }
        bindClick(overflowBinding.overflowOpenInTerminal) { openInTerminal() }
        bindClick(overflowBinding.overflowAddBookmark) { addBookmark() }
        bindClick(overflowBinding.overflowCreateShortcut) { createShortcut() }
        val popup = PopupWindow(
            overflowBinding.root,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )
        popup.elevation = resources.displayMetrics.density * 8
        popup.isOutsideTouchable = true
        popup.showAsDropDown(binding.toolbar, 0, 0, Gravity.END)
        overflowPopup = popup
    }

    private var viewSortPopup: PopupWindow? = null

    private fun showViewSortMenu() {
        viewSortPopup?.dismiss()
        val viewSortBinding = FileListViewSortMenuBinding.inflate(layoutInflater)
        // The three view modes are mutually exclusive; the selected one is rendered inverted
        // (filled background + surface-colored icon) to make the current mode obvious.
        fun tintViewButton(button: ImageButton, checked: Boolean) {
            val backgroundColor = if (checked) {
                MaterialColors.getColor(button, android.R.attr.colorControlNormal, Color.TRANSPARENT)
            } else {
                Color.TRANSPARENT
            }
            button.background = GradientDrawable().apply {
                cornerRadius = 8f * resources.displayMetrics.density
                setColor(backgroundColor)
            }
            val iconColor = if (checked) {
                MaterialColors.getColor(
                    button, com.google.android.material.R.attr.colorSurface, Color.WHITE
                )
            } else {
                MaterialColors.getColor(button, android.R.attr.colorControlNormal, Color.WHITE)
            }
            button.imageTintList = ColorStateList.valueOf(iconColor)
        }
        // Text buttons render the same inverted selection state as the icon buttons.
        fun tintViewText(view: TextView, checked: Boolean) {
            val backgroundColor = if (checked) {
                MaterialColors.getColor(view, android.R.attr.colorControlNormal, Color.TRANSPARENT)
            } else {
                Color.TRANSPARENT
            }
            view.background = GradientDrawable().apply {
                cornerRadius = 8f * resources.displayMetrics.density
                setColor(backgroundColor)
            }
            val textColor = if (checked) {
                MaterialColors.getColor(
                    view, com.google.android.material.R.attr.colorSurface, Color.WHITE
                )
            } else {
                MaterialColors.getColor(view, android.R.attr.colorControlNormal, Color.WHITE)
            }
            view.setTextColor(textColor)
        }
        tintViewButton(
            viewSortBinding.vsViewList,
            !isPaneMode && activeViewModel.viewType == FileViewType.LIST
        )
        tintViewButton(viewSortBinding.vsViewDual, isDualPane)
        tintViewButton(viewSortBinding.vsViewColumn, isColumnMode)
        tintViewButton(
            viewSortBinding.vsViewGrid,
            !isPaneMode && activeViewModel.viewType == FileViewType.GRID
        )
        viewSortBinding.vsViewList.setOnClickListener {
            viewSortPopup?.dismiss()
            Settings.FILE_LIST_DUAL_PANE.putValue(false)
            Settings.FILE_LIST_COLUMN_MODE.putValue(false)
            activeViewModel.viewType = FileViewType.LIST
        }
        viewSortBinding.vsViewDual.setOnClickListener {
            viewSortPopup?.dismiss()
            Settings.FILE_LIST_COLUMN_MODE.putValue(false)
            Settings.FILE_LIST_DUAL_PANE.putValue(true)
        }
        viewSortBinding.vsViewColumn.setOnClickListener {
            viewSortPopup?.dismiss()
            Settings.FILE_LIST_DUAL_PANE.putValue(false)
            Settings.FILE_LIST_COLUMN_MODE.putValue(true)
        }
        viewSortBinding.vsViewGrid.setOnClickListener {
            viewSortPopup?.dismiss()
            Settings.FILE_LIST_STORAGE_ANALYSIS.putValue(false)
            Settings.FILE_LIST_COLUMN_MODE.putValue(false)
            Settings.FILE_LIST_DUAL_PANE.putValue(false)
            activeViewModel.viewType = FileViewType.GRID
        }
        fun updateSortChecks() {
            val sortOptions = activeViewModel.sortOptions
            tintViewText(viewSortBinding.vsSortByName, sortOptions.by == By.NAME)
            tintViewText(viewSortBinding.vsSortByType, sortOptions.by == By.TYPE)
            tintViewText(viewSortBinding.vsSortBySize, sortOptions.by == By.SIZE)
            tintViewText(
                viewSortBinding.vsSortByLastModified, sortOptions.by == By.LAST_MODIFIED
            )
            viewSortBinding.vsCheckAscending.isVisible = sortOptions.order == Order.ASCENDING
            viewSortBinding.vsCheckDirectoriesFirst.isVisible = sortOptions.isDirectoriesFirst
            viewSortBinding.vsCheckPathSpecific.isVisible = activeViewModel.isViewSortPathSpecific
        }
        updateSortChecks()
        viewSortBinding.vsSortByName.setOnClickListener {
            activeViewModel.setSortBy(By.NAME)
            updateSortChecks()
        }
        viewSortBinding.vsSortByType.setOnClickListener {
            activeViewModel.setSortBy(By.TYPE)
            updateSortChecks()
        }
        viewSortBinding.vsSortBySize.setOnClickListener {
            activeViewModel.setSortBy(By.SIZE)
            updateSortChecks()
        }
        viewSortBinding.vsSortByLastModified.setOnClickListener {
            activeViewModel.setSortBy(By.LAST_MODIFIED)
            updateSortChecks()
        }
        viewSortBinding.vsSortOrderAscending.setOnClickListener {
            activeViewModel.setSortOrder(
                if (activeViewModel.sortOptions.order == Order.ASCENDING) {
                    Order.DESCENDING
                } else {
                    Order.ASCENDING
                }
            )
            updateSortChecks()
        }
        viewSortBinding.vsSortDirectoriesFirst.setOnClickListener {
            activeViewModel.setSortDirectoriesFirst(!activeViewModel.sortOptions.isDirectoriesFirst)
            updateSortChecks()
        }
        viewSortBinding.vsViewSortPathSpecific.setOnClickListener {
            activeViewModel.isViewSortPathSpecific = !activeViewModel.isViewSortPathSpecific
            updateSortChecks()
        }
        val popup = PopupWindow(
            viewSortBinding.root,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )
        popup.elevation = resources.displayMetrics.density * 8
        popup.isOutsideTouchable = true
        popup.showAsDropDown(binding.toolbar, 0, 0, Gravity.END)
        viewSortPopup = popup
    }

    private var selectionOverflowPopup: PopupWindow? = null

    private fun showSelectionOverflowMenu() {
        selectionOverflowPopup?.dismiss()
        val selectionBinding = FileListSelectOverflowMenuBinding.inflate(layoutInflater)
        fun bindClick(view: View, action: () -> Unit) {
            view.setOnClickListener {
                selectionOverflowPopup?.dismiss()
                action()
            }
        }
        bindClick(selectionBinding.soverflowSelectAll) { selectAllFiles() }
        bindClick(selectionBinding.soverflowShare) { shareFiles(activeSelectedFiles) }
        bindClick(selectionBinding.soverflowExtract) { extractFiles(activeSelectedFiles) }
        bindClick(selectionBinding.soverflowArchive) { showCreateArchiveDialog(activeSelectedFiles) }
        val imageFiles = activeSelectedFiles
            .filter { it.mimeType.isImage && !it.path.isArchivePath }
        selectionBinding.soverflowReencodeImage.isVisible = imageFiles.isNotEmpty()
        bindClick(selectionBinding.soverflowReencodeImage) {
            startImageReencodeActivity(requireContext(), imageFiles)
        }
        val popup = PopupWindow(
            selectionBinding.root,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )
        popup.elevation = resources.displayMetrics.density * 8
        popup.isOutsideTouchable = true
        popup.showAsDropDown(binding.overlayToolbar, 0, 0, Gravity.END)
        selectionOverflowPopup = popup
    }

    fun onKeyShortcut(keyCode: Int, event: KeyEvent): Boolean {
        if (bottomActionMode.isActive) {
            val menu = bottomActionMode.menu
            menu.setQwertyMode(
                KeyCharacterMap.load(event.deviceId).keyboardType != KeyCharacterMap.NUMERIC
            )
            if (menu.performShortcut(keyCode, event, 0)) {
                return true
            }
        }
        if (overlayActionMode.isActive) {
            val menu = overlayActionMode.menu
            menu.setQwertyMode(
                KeyCharacterMap.load(event.deviceId).keyboardType != KeyCharacterMap.NUMERIC
            )
            if (menu.performShortcut(keyCode, event, 0)) {
                return true
            }
        }
        return false
    }

    private fun onPersistentDrawerOpenChanged(open: Boolean) {
        binding.persistentDrawerLayout?.let {
            if (open) {
                it.openDrawer(GravityCompat.START)
            } else {
                it.closeDrawer(GravityCompat.START)
            }
        }
        updateSpanCount()
    }

    private fun onCurrentPathChanged(path: Path) {
        // A manual pause/force only applies to the directory it was made on; a new directory
        // falls back to the regular settings-driven behavior.
        isStorageAnalysisPaused = false
        forcedStorageAnalysisPath = null
        recordLastDirectory(path)
        updateToolbarTitle(path)
        updateToolbarSubtitle()
        updateOverlayToolbar()
        updateBottomToolbar()
    }

    // Remembers the directory for whichever view mode is currently showing it; dual-pane columns
    // record their own paths through the panes' currentPathListener.
    private fun recordLastDirectory(path: Path) {
        val mode = when {
            isColumnMode -> LastDirectories.Mode.COLUMN
            isDualPane -> null
            currentViewTypeForLastDirectory == FileViewType.GRID -> LastDirectories.Mode.GRID
            else -> LastDirectories.Mode.LIST
        }
        mode?.let { LastDirectories.record(it, path) }
    }

    // The view type live data is lazily mapped and may not have a value yet when the first path
    // change arrives (before it is observed), so fall back to the global setting instead of
    // reading it through valueCompat.
    private val currentViewTypeForLastDirectory: FileViewType
        get() = runCatching { viewModel.viewType }.getOrNull()
            ?: Settings.FILE_LIST_VIEW_TYPE.valueCompat

    // Re-records the current directory when the view mode changes, so that every mode keeps an
    // up-to-date entry even if the path itself did not change.
    private fun recordLastDirectoryForCurrentMode() {
        val path = runCatching { activeViewModel.currentPath }.getOrNull() ?: return
        recordLastDirectory(path)
    }

    private fun restoreLastDirectory(): Path? {
        val mode = when {
            isColumnMode -> LastDirectories.Mode.COLUMN
            isDualPane -> LastDirectories.Mode.DUAL_LEFT
            currentViewTypeForLastDirectory == FileViewType.GRID -> LastDirectories.Mode.GRID
            else -> LastDirectories.Mode.LIST
        }
        return LastDirectories.restore(mode)
    }

    private fun onSearchViewExpandedChanged(expanded: Boolean) {
        if (this::menuBinding.isInitialized) {
            menuBinding.viewSortItem.isVisible = !expanded
        }
        if (expanded) {
            Settings.FILE_LIST_STORAGE_ANALYSIS.putValue(false)
            // Search only applies to the single-column list.
            Settings.FILE_LIST_COLUMN_MODE.putValue(false)
        }
    }

    private fun onFileListChanged(stateful: Stateful<List<FileItem>>) {
        val files = stateful.value
        val isSearching = viewModel.searchState.isSearching
        updateToolbarSubtitle()
        val hasFiles = !files.isNullOrEmpty()
        binding.swipeRefreshLayout.isRefreshing = stateful is Loading && (hasFiles || isSearching)
        binding.progress.fadeToVisibilityUnsafe(stateful is Loading && !(hasFiles || isSearching))
        binding.errorText.fadeToVisibilityUnsafe(stateful is Failure && !hasFiles)
        val throwable = (stateful as? Failure)?.throwable
        if (throwable != null) {
            throwable.printStackTrace()
            val error = throwable.toString()
            if (hasFiles) {
                showToast(error)
            } else {
                binding.errorText.text = error
            }
        }
        binding.emptyView.fadeToVisibilityUnsafe(stateful is Success && !hasFiles)
        if (files != null) {
            updateAdapterFileList()
            if (stateful is Success) {
                startStorageAnalysisIfEnabled()
            }
        } else {
            // This resets animation as well.
            adapter.clear()
        }
        if (stateful is Success) {
            viewModel.pendingState?.let { layoutManager.onRestoreInstanceState(it) }
        }
    }

    // The toolbar title shows the current directory name of the active column (single- or
    // dual-pane), falling back to the generic title for the root. Callers that just observed a
    // path change should pass the new path so the title never reads a stale live-data value.
    private fun updateToolbarTitle(path: Path? = null) {
        // The toolbar always shows the current directory name; when the path is not available
        // yet, fall back to the file system root instead of the app name.
        val name = path?.name
            ?: runCatching { activeViewModel.currentPath.name }.getOrDefault("/")
        // Keep the activity title in sync so AppCompat's onTitleChanged (e.g. from onPostCreate
        // which runs after the fragment's onActivityCreated) never overwrites the toolbar with
        // the app name again.
        requireActivity().title = name
        binding.toolbar.title = name
    }

    private fun updateToolbarSubtitle() {
        // The active view model's file list state is a lazily mapped live data and may not have a
        // value yet right after the path changes, so skip until it is ready.
        val stateful = runCatching { activeViewModel.fileListStateful }.getOrNull() ?: return
        val files = stateful.value
        val isSearching = runCatching { activeViewModel.searchState.isSearching }.getOrDefault(false)
        when {
            stateful is Failure -> binding.toolbar.setSubtitle(R.string.error)
            stateful is Loading && !isSearching -> binding.toolbar.setSubtitle(R.string.loading)
            else -> files?.let { binding.toolbar.subtitle = getSubtitle(it) }
        }
    }

    private fun onDualPaneChanged(enabled: Boolean) {
        if (enabled) {
            // Dual pane and column view are mutually exclusive multi-pane layouts.
            Settings.FILE_LIST_COLUMN_MODE.putValue(false)
            // The panes own the storage analysis state in multi-pane layouts; stop the host's
            // single-column walk.
            storageAnalysisComputer.cancel()
            storageAnalysisTotal = null
            isStorageAnalysisComputing = false
            adapter.setStorageAnalysisEnabled(false)
        }
        binding.dualPaneContainer?.isVisible = enabled
        binding.contentLayout.isVisible = !enabled
        updateToolbarSubtitle()
        updateStorageAnalysisIndicator()
        if (enabled) {
            if (overlayActionMode.isActive) {
                overlayActionMode.finish()
            }
            // Dual-pane columns always use the list layout, never grid.
            viewModel.viewType = FileViewType.LIST
            ensurePanes()
            updateActivePaneVisual()
            updateTopBreadcrumbForActivePane()
            updateToolbarTitle()
        } else {
            if (bottomActionMode.isActive) {
                bottomActionMode.finish()
            }
            binding.breadcrumbLayout.setData(viewModel.breadcrumbLiveData.valueCompat)
        }
        updateBottomToolbar()
        updateNavigateUpBackCallbackEnabled()
        updateOverlayToolbar()
        recordLastDirectoryForCurrentMode()
    }

    private fun updateTopBreadcrumbForActivePane() {
        if (!isDualPane) {
            return
        }
        activePane?.breadcrumbDataOrNull?.let { binding.breadcrumbLayout.setData(it) }
    }

    private fun onPaneBreadcrumbChanged(pane: FileListPaneFragment, data: BreadcrumbData) {
        if (isDualPane && activePane === pane) {
            binding.breadcrumbLayout.setData(data)
            updateToolbarTitle(data.paths.getOrNull(data.selectedIndex))
            updateToolbarSubtitle()
            updateNavigateUpBackCallbackEnabled()
        }
    }

    private fun ensurePanes() {
        if (leftPane != null || rightPane != null) {
            return
        }
        val leftPane = FileListPaneFragment()
        val rightPane = FileListPaneFragment()
        childFragmentManager.commit {
            add(R.id.leftPane, leftPane)
            add(R.id.rightPane, rightPane)
        }
        this.leftPane = leftPane
        this.rightPane = rightPane
        leftPane.paneId = DualPaneController.PANE_LEFT
        rightPane.paneId = DualPaneController.PANE_RIGHT
        DualPaneController.register(DualPaneController.PANE_LEFT, leftPane)
        DualPaneController.register(DualPaneController.PANE_RIGHT, rightPane)
        val path = viewModel.currentPath
        leftPane.initialize(LastDirectories.restore(LastDirectories.Mode.DUAL_LEFT) ?: path)
        rightPane.initialize(LastDirectories.restore(LastDirectories.Mode.DUAL_RIGHT) ?: path)
        leftPane.currentPathListener = {
            LastDirectories.record(LastDirectories.Mode.DUAL_LEFT, it)
        }
        rightPane.currentPathListener = {
            LastDirectories.record(LastDirectories.Mode.DUAL_RIGHT, it)
        }
        leftPane.activeChangedListener = ::onPaneActivated
        rightPane.activeChangedListener = ::onPaneActivated
        leftPane.breadcrumbListener = { data -> onPaneBreadcrumbChanged(leftPane, data) }
        rightPane.breadcrumbListener = { data -> onPaneBreadcrumbChanged(rightPane, data) }
        leftPane.pasteStateListener = { onPanePasteStateChanged() }
        rightPane.pasteStateListener = { onPanePasteStateChanged() }
        // Refresh the toolbar subtitle and the storage analysis indicator when the active pane's
        // file list or analysis state changes.
        leftPane.subtitleListener = {
            if (isDualPane && activePane === leftPane) {
                updateToolbarSubtitle()
                updateStorageAnalysisIndicator()
            }
        }
        rightPane.subtitleListener = {
            if (isDualPane && activePane === rightPane) {
                updateToolbarSubtitle()
                updateStorageAnalysisIndicator()
            }
        }
        // Each column keeps its own selection; sync the overlay action bar with the active column.
        leftPane.selectionListener = { _ ->
            if (isDualPane && activePane === leftPane) {
                updateOverlayToolbar()
            }
        }
        rightPane.selectionListener = { _ ->
            if (isDualPane && activePane === rightPane) {
                updateOverlayToolbar()
            }
        }
        activePane = when (DualPaneController.activePaneId) {
            DualPaneController.PANE_RIGHT -> rightPane
            else -> leftPane
        }
    }

    private fun onPaneActivated(pane: FileListPaneFragment) {
        if (activePane !== pane) {
            if (isColumnMode) {
                ColumnController.activeColumnIndex = pane.paneId
            } else {
                DualPaneController.activePaneId = pane.paneId
            }
            activatePane(pane)
        }
    }

    private fun activatePane(pane: FileListPaneFragment) {
        activePane = pane
        updateActivePaneVisual()
        if (isColumnMode) {
            // In column mode the address bar shows the whole trail and only follows the scroll
            // offset of the column container.
            updateToolbarTitle(
                runCatching { pane.viewModelOrNull?.currentPath }.getOrNull()
            )
        } else {
            updateTopBreadcrumbForActivePane()
            updateToolbarTitle(
                pane.breadcrumbDataOrNull?.let { it.paths.getOrNull(it.selectedIndex) }
            )
        }
        updateBottomToolbar()
        updateNavigateUpBackCallbackEnabled()
        updateToolbarSubtitle()
        updateStorageAnalysisIndicator()
        updateOverlayToolbar()
    }

    private fun onColumnModeChanged(enabled: Boolean) {
        if (enabled) {
            Settings.FILE_LIST_DUAL_PANE.putValue(false)
            // The panes own the storage analysis state in multi-pane layouts; stop the host's
            // single-column walk.
            storageAnalysisComputer.cancel()
            storageAnalysisTotal = null
            isStorageAnalysisComputing = false
            adapter.setStorageAnalysisEnabled(false)
            if (overlayActionMode.isActive) {
                overlayActionMode.finish()
            }
            // Columns always use the list layout.
            viewModel.viewType = FileViewType.LIST
            binding.columnPaneContainer?.isVisible = true
            binding.contentLayout.isVisible = false
            binding.breadcrumbLayout.columnMode = true
            renderColumns(runCatching { viewModel.breadcrumbLiveData.valueCompat.paths }.getOrNull())
        } else {
            teardownColumns()
            binding.columnPaneContainer?.isVisible = false
            binding.breadcrumbLayout.columnMode = false
            if (!isDualPane) {
                binding.contentLayout.isVisible = true
                binding.breadcrumbLayout.setData(viewModel.breadcrumbLiveData.valueCompat)
            }
        }
        updateToolbarTitle()
        updateToolbarSubtitle()
        updateStorageAnalysisIndicator()
        updateBottomToolbar()
        updateNavigateUpBackCallbackEnabled()
        updateOverlayToolbar()
        recordLastDirectoryForCurrentMode()
    }

    // Makes the column at the given index the active one and brings it into view; the address
    // bar follows through the 1:1 scroll sync.
    private fun activateColumn(index: Int) {
        val pane = columnPanes.getOrNull(index) ?: return
        binding.columnPaneContainer?.smoothScrollTo(
            index * resources.getDimensionPixelSize(R.dimen.file_list_column_width), 0
        )
        if (activePane !== pane) {
            ColumnController.activeColumnIndex = index
            activatePane(pane)
        }
    }

    private fun onBreadcrumbColumnItemTapped(index: Int) {
        activateColumn(index)
    }

    // Renders one column per trail path: existing columns whose path is unchanged are kept,
    // deeper columns are removed and new ones are appended, then the container scrolls to the
    // deepest column.
    private fun renderColumns(paths: List<Path>?) {
        val container = binding.columnPaneContainer ?: return
        val row = binding.columnPaneRow ?: return
        if (paths == null) {
            return
        }
        while (columnPanes.size > paths.size) {
            removeColumnAt(columnPanes.size - 1)
        }
        for (index in paths.indices) {
            val path = paths[index]
            if (columnPanes.getOrNull(index) != null && columnPaths.getOrNull(index) == path) {
                continue
            }
            if (columnPanes.getOrNull(index) != null) {
                removeColumnAt(index)
            }
            addColumnAt(index, path)
        }
        if (activePane == null || columnPanes.none { it === activePane }) {
            activePane = columnPanes.lastOrNull()
            ColumnController.activeColumnIndex = columnPanes.size - 1
            updateActivePaneVisual()
        }
        container.post {
            val lastChild = row.getChildAt(row.childCount - 1) ?: return@post
            container.smoothScrollTo(lastChild.right, 0)
        }
        updateToolbarTitle()
        updateToolbarSubtitle()
        updateNavigateUpBackCallbackEnabled()
    }

    private fun addColumnAt(index: Int, path: Path) {
        val container = binding.columnPaneContainer ?: return
        val row = binding.columnPaneRow ?: return
        val containerId = View.generateViewId()
        val columnContainer = FragmentContainerView(requireContext()).apply {
            id = containerId
        }
        val columnWidth = resources.getDimensionPixelSize(R.dimen.file_list_column_width)
        row.addView(
            columnContainer, index,
            LinearLayout.LayoutParams(columnWidth, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        columnContainerIds.add(index, containerId)
        columnPaths.add(index, path)
        val pane = FileListPaneFragment()
        pane.paneId = index
        pane.columnMode = true
        columnPanes.add(index, pane)
        ColumnController.register(index, pane)
        childFragmentManager.commit {
            add(containerId, pane)
        }
        pane.initialize(path)
        pane.columnExpandListener = { clicked -> expandColumnFrom(pane, clicked) }
        pane.activeChangedListener = ::onPaneActivated
        pane.subtitleListener = {
            if (isColumnMode && activePane === pane) {
                updateToolbarSubtitle()
                updateStorageAnalysisIndicator()
            }
        }
        pane.selectionListener = { _ ->
            if (isColumnMode && activePane === pane) {
                updateOverlayToolbar()
            }
        }
        // Cross-column drag and drop: dropping onto a directory row of this column offers the
        // move/copy choice, exactly like the single-pane list drag.
        pane.viewLifecycleOwnerLiveData.observe(this) { owner ->
            if (owner != null) {
                pane.paneRecyclerViewOrNull?.setOnDragListener { _, event ->
                    handleColumnDragEvent(pane, event)
                }
            }
        }
    }

    private fun removeColumnAt(index: Int) {
        val pane = columnPanes.getOrNull(index) ?: return
        val containerId = columnContainerIds.getOrNull(index) ?: return
        childFragmentManager.commit {
            remove(pane)
        }
        binding.columnPaneRow?.let { row ->
            row.findViewById<View>(containerId)?.let { row.removeView(it) }
        }
        columnPanes.removeAt(index)
        columnContainerIds.removeAt(index)
        columnPaths.removeAt(index)
        ColumnController.unregister(index)
        if (activePane === pane) {
            activePane = null
        }
    }

    private fun teardownColumns() {
        if (columnPanes.isEmpty()) {
            ColumnController.clear()
            return
        }
        childFragmentManager.commit {
            for (pane in columnPanes) {
                remove(pane)
            }
        }
        binding.columnPaneRow?.removeAllViews()
        columnPanes.clear()
        columnContainerIds.clear()
        columnPaths.clear()
        ColumnController.clear()
        if (activePane != null && leftPane == null && rightPane == null) {
            activePane = null
        }
    }

    // Expands the trail with a directory tapped in the given column: the trail is truncated
    // after that column and the directory is appended, which re-renders the deeper columns.
    private fun expandColumnFrom(pane: FileListPaneFragment, path: Path) {
        val state = pane.saveScrollState() ?: layoutManager.onSaveInstanceState()!!
        viewModel.navigateTo(state, path)
    }

    private fun handleColumnDragEvent(pane: FileListPaneFragment, event: DragEvent): Boolean {
        return when (event.action) {
            // Accepting drag-location events is required for this view to receive ACTION_DROP.
            DragEvent.ACTION_DRAG_LOCATION -> ColumnController.draggingFile != null
            DragEvent.ACTION_DROP -> {
                val file = ColumnController.draggingFile ?: return false
                ColumnController.draggingFile = null
                val targetDirectory = pane.findColumnDropTargetDirectory(event, file)
                    ?: return false
                pendingSinglePaneDrag = PendingSinglePaneDrag(file, targetDirectory)
                MoveCopyDialogFragment.show(this)
                true
            }
            DragEvent.ACTION_DRAG_ENDED -> {
                // A handled drop clears the source above; a cancelled drag ends here.
                ColumnController.draggingFile = null
                true
            }
            else -> false
        }
    }

    private fun updateActivePaneVisual() {
        if (isColumnMode) {
            for (pane in columnPanes) {
                pane.setActive(activePane === pane)
            }
        } else {
            leftPane?.setActive(activePane === leftPane)
            rightPane?.setActive(activePane === rightPane)
        }
    }

    private fun onPanePasteStateChanged() {
        if (isDualPane) {
            updateBottomToolbar()
        }
    }

    // Starts a single-pane drag for the file; dropping onto a directory moves or copies it there.
    private fun startSinglePaneDrag(view: View, file: FileItem) {
        draggingFile = file
        view.startDragAndDrop(ClipData.newPlainText("", ""), View.DragShadowBuilder(view), view, 0)
    }

    private fun handleSinglePaneDragEvent(event: DragEvent): Boolean {
        return when (event.action) {
            // Accepting drag-location events is required for this view to receive ACTION_DROP.
            DragEvent.ACTION_DRAG_LOCATION -> draggingFile != null
            DragEvent.ACTION_DROP -> {
                val file = draggingFile ?: return false
                draggingFile = null
                val targetDirectory = findSinglePaneDropTargetDirectory(event, file) ?: return false
                pendingSinglePaneDrag = PendingSinglePaneDrag(file, targetDirectory)
                MoveCopyDialogFragment.show(this)
                true
            }
            DragEvent.ACTION_DRAG_ENDED -> {
                // A handled drop clears the source above; a cancelled drag ends here.
                draggingFile = null
                true
            }
            else -> false
        }
    }

    // Dropping a dragged file onto a breadcrumb entry moves/copies it into that ancestor directory.
    private fun onBreadcrumbDrop(path: Path) {
        val file = draggingFile ?: return
        draggingFile = null
        pendingSinglePaneDrag = PendingSinglePaneDrag(file, path)
        MoveCopyDialogFragment.show(this)
    }

    private fun findSinglePaneDropTargetDirectory(event: DragEvent, draggedFile: FileItem): Path? {
        val recyclerView = binding.recyclerView
        val x = event.x - recyclerView.left
        val y = event.y - recyclerView.top
        val child = recyclerView.findChildViewUnder(x, y) ?: return null
        val position = recyclerView.getChildAdapterPosition(child)
        if (position == RecyclerView.NO_POSITION) {
            return null
        }
        val file = adapter.getItem(position)
        return if (file.attributes.isDirectory && file.path != draggedFile.path) file.path else null
    }

    private fun onMoveCopyDialogResult(result: Int) {
        val pending = pendingSinglePaneDrag ?: return
        pendingSinglePaneDrag = null
        when (result) {
            MoveCopyDialogFragment.RESULT_MOVE -> {
                activeViewModel.addToPasteState(false, fileItemSetOf(pending.file))
                pasteFiles(pending.targetDirectory)
            }
            MoveCopyDialogFragment.RESULT_COPY -> {
                activeViewModel.addToPasteState(true, fileItemSetOf(pending.file))
                pasteFiles(pending.targetDirectory)
            }
            else -> {}
        }
    }

    private data class PendingSinglePaneDrag(
        val file: FileItem,
        val targetDirectory: Path
    )

    private fun getSubtitle(files: List<FileItem>): CharSequence {
        val directoryCount = files.count { it.attributes.isDirectory }
        val fileCount = files.size - directoryCount
        if (directoryCount == 0 && fileCount == 0) {
            return getString(R.string.empty)
        }
        val builder = SpannableStringBuilder()
        if (directoryCount > 0) {
            appendIconCount(builder, R.drawable.directory_icon_white_24dp, directoryCount)
        }
        if (directoryCount > 0 && fileCount > 0) {
            // A wider gap between the count and the next icon so it doesn't feel cramped.
            builder.append("   ")
        }
        if (fileCount > 0) {
            appendIconCount(builder, R.drawable.file_icon_white_24dp, fileCount)
        }
        if (isStorageAnalysisActive && !viewModel.searchState.isSearching) {
            builder.append("   ")
            val sizeText = effectiveStorageAnalysisTotal?.let {
                it.asFileSize().formatHumanReadable(requireContext())
            } ?: getString(R.string.file_item_storage_analysis_computing)
            appendIconText(
                builder, R.drawable.pie_chart_icon_control_normal_24dp, sizeText
            )
        }
        return builder
    }

    // Appends "[icon]text" using an inline image span so the subtitle stays compact; the icon is
    // tinted with the secondary text color to match the subtitle text.
    private fun appendIconText(
        builder: SpannableStringBuilder,
        iconRes: Int,
        text: CharSequence
    ) {
        val drawable = requireContext().getDrawableCompat(iconRes).mutate()
        val iconSize = (14 * resources.displayMetrics.density).roundToInt()
        drawable.setTint(
            MaterialColors.getColor(
                binding.toolbar, android.R.attr.textColorSecondary, Color.WHITE
            )
        )
        drawable.setBounds(0, 0, iconSize, iconSize)
        val start = builder.length
        builder.append('\uFFFC')
        builder.setSpan(
            CenterVerticalImageSpan(drawable),
            start, start + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        // A wider gap between the icon and the number so they don't touch.
        builder.append("  ")
        builder.append(text)
    }

    private fun appendIconCount(builder: SpannableStringBuilder, iconRes: Int, count: Int) {
        appendIconText(builder, iconRes, count.toString())
    }

    // ImageSpan that vertically centers the drawable against the surrounding text, so the inline
    // count icons line up with the digits instead of sitting on the baseline.
    private class CenterVerticalImageSpan(drawable: Drawable) :
        ImageSpan(drawable, ALIGN_BOTTOM) {
        override fun draw(
            canvas: Canvas,
            text: CharSequence?,
            start: Int,
            end: Int,
            x: Float,
            top: Int,
            y: Int,
            bottom: Int,
            paint: Paint
        ) {
            val drawable = drawable
            val rect = drawable.bounds
            canvas.save()
            val fontMetrics = paint.fontMetricsInt
            val textCenterY = y + (fontMetrics.ascent + fontMetrics.descent) / 2f
            val drawableCenterY = rect.height() / 2f
            canvas.translate(x, textCenterY - drawableCenterY)
            drawable.draw(canvas)
            canvas.restore()
        }
    }

    private fun onViewTypeChanged(viewType: FileViewType) {
        if (viewType != FileViewType.LIST && Settings.FILE_LIST_STORAGE_ANALYSIS.valueCompat) {
            Settings.FILE_LIST_STORAGE_ANALYSIS.putValue(false)
        }
        updateSpanCount()
        adapter.viewType = viewType
        recordLastDirectoryForCurrentMode()
    }

    private fun updateSpanCount() {
        layoutManager.spanCount = when (viewModel.viewType) {
            FileViewType.LIST -> 1
            FileViewType.GRID -> Settings.FILE_LIST_GRID_COLUMNS.valueCompat.count
        }
    }

    private fun onSortOptionsChanged(sortOptions: FileSortOptions) {
        adapter.sortOptions = sortOptions
    }

    private fun onViewSortPathSpecificChanged(pathSpecific: Boolean) {
    }

    private fun navigateUp() {
        collapseSearchView()
        if (isColumnMode) {
            // Back in column view: from a non-root column it steps back to the previous column;
            // from the root column it navigates up (removing the deepest column).
            val activeIndex = ColumnController.activeColumnIndex
            if (activeIndex > 0) {
                activateColumn(activeIndex - 1)
                return
            }
        }
        activeViewModel.navigateUp()
    }

    // The back gesture navigates the active pane up in dual-pane mode, or this fragment's own view
    // model in single-pane mode; it is only enabled while there is somewhere to go up to.
    private fun updateNavigateUpBackCallbackEnabled() {
        navigateUpBackCallback?.isEnabled = if (isColumnMode) {
            columnPanes.size > 1
        } else {
            activeViewModel.canNavigateUpBreadcrumb
        }
    }

    private fun showNavigateToPathDialog() {
        NavigateToPathDialogFragment.show(currentPath, this)
    }

    private fun newTask() {
        openInNewTask(currentPath)
    }

    private fun refresh() {
        activeViewModel.reload()
    }

    private fun setShowHiddenFiles(showHiddenFiles: Boolean) {
        Settings.FILE_LIST_SHOW_HIDDEN_FILES.putValue(showHiddenFiles)
    }

    private fun toggleStorageAnalysis() {
        Settings.FILE_LIST_STORAGE_ANALYSIS.putValue(
            !Settings.FILE_LIST_STORAGE_ANALYSIS.valueCompat
        )
    }

    private fun onStorageAnalysisChanged(enabled: Boolean) {
        if (!enabled) {
            storageAnalysisComputer.cancel()
            storageAnalysisTotal = null
            isStorageAnalysisComputing = false
            adapter.setStorageAnalysisEnabled(false)
        }
        updateToolbarSubtitle()
        updateStorageAnalysisIndicator()
        if (enabled) {
            startStorageAnalysisIfEnabled()
        }
    }

    // Whether the analysis display is actually active for the active pane: the toggle is on,
    // the user hasn't paused it, and the current path either passes the gates or was
    // force-analyzed explicitly. In dual-pane/column mode the active pane owns the state.
    private val isStorageAnalysisActive: Boolean
        get() {
            if (isPaneMode) {
                return activePane?.isStorageAnalysisActiveCompat == true
            }
            if (!Settings.FILE_LIST_STORAGE_ANALYSIS.valueCompat || isStorageAnalysisPaused) {
                return false
            }
            val path = runCatching { viewModel.currentPath }.getOrNull() ?: return false
            return isStorageAnalysisPathAllowed(path) || forcedStorageAnalysisPath == path
        }

    private val effectiveStorageAnalysisComputing: Boolean
        get() = if (isPaneMode) {
            activePane?.isStorageAnalysisComputing == true
        } else {
            isStorageAnalysisComputing
        }

    private val effectiveStorageAnalysisTotal: Long?
        get() = if (isPaneMode) activePane?.storageAnalysisTotal else storageAnalysisTotal

    private fun startStorageAnalysisIfEnabled() {
        if (isPaneMode) {
            // In dual-pane/column mode each pane owns its analysis state; the host only mirrors
            // the active pane's state in the indicator and the subtitle.
            updateStorageAnalysisIndicator()
            updateToolbarSubtitle()
            return
        }
        val enabled = isStorageAnalysisActive
        if (adapter.storageAnalysisEnabled != enabled) {
            adapter.setStorageAnalysisEnabled(enabled)
        }
        if (!enabled) {
            storageAnalysisComputer.cancel()
            storageAnalysisTotal = null
            isStorageAnalysisComputing = false
            updateStorageAnalysisIndicator()
            updateToolbarSubtitle()
            return
        }
        val stateful = runCatching { viewModel.fileListStateful }.getOrNull() ?: return
        val files = stateful.value
        if (files.isNullOrEmpty() || viewModel.searchState.isSearching) {
            return
        }
        storageAnalysisTotal = null
        isStorageAnalysisComputing = true
        adapter.updateStorageAnalysisSizes(emptyMap(), 0)
        updateStorageAnalysisIndicator()
        updateToolbarSubtitle()
        storageAnalysisComputer.start(viewModel.currentPath, storageAnalysisListener)
    }

    private val storageAnalysisListener = object : StorageAnalysisComputer.Listener {
        override fun onSizes(childSizes: Map<Path, Long>, total: Long, complete: Boolean) {
            if (!isStorageAnalysisActive) {
                return
            }
            if (complete) {
                storageAnalysisTotal = total
                isStorageAnalysisComputing = false
            } else if (storageAnalysisTotal != null) {
                // Keep the toolbar subtitle on its "calculating" placeholder until the walk is
                // done instead of showing the growing partial total.
                storageAnalysisTotal = null
            }
            adapter.updateStorageAnalysisSizes(childSizes, total)
            if (complete) {
                // Only reorder once the real sizes are all known so the list doesn't jump while
                // the walk is still in progress.
                adapter.sortByStorageAnalysisSizes()
                updateStorageAnalysisIndicator()
                // Reset the viewport to the top so the largest items are visible after the
                // re-sort, otherwise the list stays anchored around the previous viewport.
                binding.recyclerView.post { binding.recyclerView.scrollToPosition(0) }
            }
            updateToolbarSubtitle()
        }
    }

    // Reflects the storage analysis state in the breadcrumb bar indicator, which is always
    // visible: a slashed pie chart when the toggle is off, a dimmed pie chart when the current
    // path isn't analyzed, an indeterminate spinner while computing, and a full pie chart when
    // the analysis has completed.
    private fun updateStorageAnalysisIndicator() {
        val enabled = Settings.FILE_LIST_STORAGE_ANALYSIS.valueCompat
        val computing = isStorageAnalysisActive && effectiveStorageAnalysisComputing
        val done = isStorageAnalysisActive && !effectiveStorageAnalysisComputing &&
            effectiveStorageAnalysisTotal != null
        binding.storageAnalysisIndicator.isVisible = true
        when {
            !enabled -> {
                binding.storageAnalysisProgress.isVisible = false
                binding.storageAnalysisIcon.isVisible = true
                binding.storageAnalysisIcon.alpha = 0.5f
                binding.storageAnalysisIcon.setImageResource(
                    R.drawable.pie_chart_x_icon_control_normal_24dp
                )
            }
            computing -> {
                binding.storageAnalysisProgress.isVisible = true
                binding.storageAnalysisIcon.isVisible = false
            }
            done -> {
                binding.storageAnalysisProgress.isVisible = false
                binding.storageAnalysisIcon.isVisible = true
                binding.storageAnalysisIcon.alpha = 1f
                binding.storageAnalysisIcon.setImageResource(
                    R.drawable.pie_chart_check_icon_control_normal_24dp
                )
            }
            else -> {
                binding.storageAnalysisProgress.isVisible = false
                binding.storageAnalysisIcon.isVisible = true
                binding.storageAnalysisIcon.alpha = 0.5f
                binding.storageAnalysisIcon.setImageResource(
                    R.drawable.pie_chart_icon_control_normal_24dp
                )
            }
        }
    }

    // Short press behavior by state: turned off / not analyzed -> start a forced analysis of the
    // current directory (ignoring the path gates); computing -> stop and return to the not
    // analyzed state; complete -> show a hint that a long press turns the feature off.
    private fun onStorageAnalysisIndicatorClicked() {
        if (isPaneMode) {
            activePane?.onStorageAnalysisIndicatorClicked()
            return
        }
        val computing = isStorageAnalysisActive && isStorageAnalysisComputing
        val done = isStorageAnalysisActive && !isStorageAnalysisComputing &&
            storageAnalysisTotal != null
        when {
            computing -> {
                stopStorageAnalysis()
                showToast(R.string.storage_analysis_stopped_toast)
            }
            done -> showToast(R.string.storage_analysis_complete_toast)
            else -> {
                startStorageAnalysisForced()
                showToast(R.string.storage_analysis_started_toast)
            }
        }
    }

    private fun onStorageAnalysisIndicatorLongClicked() {
        isStorageAnalysisPaused = false
        forcedStorageAnalysisPath = null
        Settings.FILE_LIST_STORAGE_ANALYSIS.putValue(false)
        showToast(R.string.storage_analysis_turned_off_toast)
    }

    private fun startStorageAnalysisForced() {
        isStorageAnalysisPaused = false
        val path = runCatching { viewModel.currentPath }.getOrNull() ?: return
        forcedStorageAnalysisPath = path
        if (Settings.FILE_LIST_STORAGE_ANALYSIS.valueCompat) {
            startStorageAnalysisIfEnabled()
        } else {
            Settings.FILE_LIST_STORAGE_ANALYSIS.putValue(true)
        }
    }

    private fun stopStorageAnalysis() {
        isStorageAnalysisPaused = true
        forcedStorageAnalysisPath = null
        storageAnalysisComputer.cancel()
        storageAnalysisTotal = null
        isStorageAnalysisComputing = false
        adapter.setStorageAnalysisEnabled(false)
        updateStorageAnalysisIndicator()
        updateToolbarSubtitle()
    }

    private fun onShowHiddenFilesChanged(showHiddenFiles: Boolean) {
        updateAdapterFileList()
        startStorageAnalysisIfEnabled()
    }

    private fun updateAdapterFileList() {
        var files = viewModel.fileListStateful.value ?: return
        if (!Settings.FILE_LIST_SHOW_HIDDEN_FILES.valueCompat) {
            files = files.filterNot { it.isHidden }
        }
        adapter.replaceListAndIsSearching(files, viewModel.searchState.isSearching)
    }

    private fun share() {
        shareFile(currentPath, MimeType.DIRECTORY)
    }

    private fun copyPath() {
        copyPath(currentPath)
    }

    private fun openInTerminal() {
        val path = currentPath
        if (path.isLinuxPath) {
            Terminal.open(path.toFile().path, requireContext())
        } else {
            // TODO
        }
    }

    override fun navigateTo(path: Path) {
        collapseSearchView()
        if (isColumnMode) {
            // Tapping an address bar entry activates and scrolls to that column instead of
            // navigating (which would truncate the deeper columns).
            val data = runCatching { viewModel.breadcrumbLiveData.valueCompat }.getOrNull()
            val index = data?.paths?.indexOf(path) ?: -1
            if (index >= 0) {
                activateColumn(index)
            }
            return
        }
        if (isDualPane) {
            activePane?.navigateToPath(path)
            return
        }
        val state = layoutManager.onSaveInstanceState()
        viewModel.navigateTo(state!!, path)
    }

    override fun copyPath(path: Path) {
        clipboardManager.copyText(path.toUserFriendlyString(), requireContext())
    }

    override fun openInNewTask(path: Path) {
        val intent = FileListActivity.createViewIntent(path)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        startActivitySafe(intent)
    }

    private fun onPickOptionsChanged(pickOptions: PickOptions?) {
        if (pickOptions != null && Settings.FILE_LIST_STORAGE_ANALYSIS.valueCompat) {
            Settings.FILE_LIST_STORAGE_ANALYSIS.putValue(false)
        }
        if (pickOptions != null && Settings.FILE_LIST_COLUMN_MODE.valueCompat) {
            // Picking files only applies to the single-column list.
            Settings.FILE_LIST_COLUMN_MODE.putValue(false)
        }
        if (pickOptions != null) {
            // Only pick modes set the window title; the normal browsing toolbar title is driven
            // by updateToolbarTitle() and would be overwritten by the activity title sync.
            val count = if (pickOptions.allowMultiple) Int.MAX_VALUE else 1
            val title = when (pickOptions.mode) {
                PickOptions.Mode.OPEN_FILE ->
                    getQuantityString(R.plurals.file_list_title_open_file, count)
                PickOptions.Mode.CREATE_FILE -> getString(R.string.file_list_title_create_file)
                PickOptions.Mode.OPEN_DIRECTORY ->
                    getQuantityString(R.plurals.file_list_title_open_directory, count)
            }
            requireActivity().title = title
        }
        updateOverlayToolbar()
        updateBottomToolbar()
        adapter.pickOptions = pickOptions
    }

    private fun pickFiles(files: FileItemSet) {
        pickPaths(files.mapTo(linkedSetOf()) { it.path })
    }

    private fun pickPaths(paths: LinkedHashSet<Path>) {
        val intent = Intent().apply {
            val pickOptions = viewModel.pickOptions!!
            if (paths.size == 1) {
                val path = paths.single()
                data = path.fileProviderUri
                extraPath = path
            } else {
                val mimeTypes = pickOptions.mimeTypes.map { it.value }
                val items = paths.map { ClipData.Item(it.fileProviderUri) }
                clipData = ClipData::class.create(null, mimeTypes, items)
                extraPathList = paths.toList()
            }
            var flags =
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            if (!pickOptions.readOnly) {
                flags = flags or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            }
            if (pickOptions.mode == PickOptions.Mode.OPEN_DIRECTORY) {
                flags = flags or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
            }
            addFlags(flags)
        }
        requireActivity().run {
            setResult(Activity.RESULT_OK, intent)
            finish()
        }
    }

    private fun onSelectedFilesChanged(files: FileItemSet) {
        updateOverlayToolbar()
        adapter.replaceSelectedFiles(files)
    }

    private fun updateOverlayToolbar() {
        val files = activeSelectedFiles
        if (files.isEmpty()) {
            if (overlayActionMode.isActive) {
                overlayActionMode.finish()
            }
            return
        }
        val pickOptions = viewModel.pickOptions
        if (pickOptions != null) {
            overlayActionMode.title = getString(R.string.file_list_select_title_format, files.size)
            overlayActionMode.setMenuResource(R.menu.file_list_pick)
            val menu = overlayActionMode.menu
            val isOpen = when (pickOptions.mode) {
                PickOptions.Mode.OPEN_FILE, PickOptions.Mode.OPEN_DIRECTORY -> true
                PickOptions.Mode.CREATE_FILE -> false
            }
            menu.findItem(R.id.action_open).isVisible = isOpen
            menu.findItem(R.id.action_create).isVisible = !isOpen
            menu.findItem(R.id.action_select_all).isVisible = pickOptions.allowMultiple
        } else {
            overlayActionMode.title = getString(R.string.file_list_select_title_format, files.size)
            overlayActionMode.setMenuResource(R.menu.file_list_select)
            val menu = overlayActionMode.menu
            val isAnyFileReadOnly = files.any { it.path.fileSystem.isReadOnly }
            menu.findItem(R.id.action_cut).isVisible = !isAnyFileReadOnly
            val areAllFilesArchivePaths = files.all { it.path.isArchivePath }
            menu.findItem(R.id.action_copy)
                .setIcon(
                    if (areAllFilesArchivePaths) {
                        R.drawable.extract_icon_control_normal_24dp
                    } else {
                        R.drawable.copy_icon_control_normal_24dp
                    }
                )
                .setTitle(
                    if (areAllFilesArchivePaths) {
                        R.string.file_list_select_action_extract
                    } else {
                        R.string.copy
                    }
                )
            menu.findItem(R.id.action_delete).isVisible = !isAnyFileReadOnly
        }
        if (!overlayActionMode.isActive) {
            binding.appBarLayout.setExpanded(true)
            binding.appBarLayout.addOnOffsetChangedListener(
                AppBarLayoutExpandHackListener(binding.recyclerView)
            )
            overlayActionMode.start(object : ToolbarActionMode.Callback {
                override fun onToolbarActionModeMenuItemClicked(
                    toolbarActionMode: ToolbarActionMode,
                    item: MenuItem
                ): Boolean = onOverlayActionModeMenuItemClicked(item)

                override fun onToolbarActionModeFinished(toolbarActionMode: ToolbarActionMode) {
                    onOverlayActionModeFinished()
                }
            })
        }
    }

    private fun onOverlayActionModeMenuItemClicked(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.action_open -> {
                pickFiles(activeSelectedFiles)
                true
            }
            R.id.action_create -> {
                confirmReplaceFile(activeSelectedFiles.single())
                true
            }
            R.id.action_cut -> {
                cutFiles(activeSelectedFiles)
                true
            }
            R.id.action_copy -> {
                copyFiles(activeSelectedFiles)
                true
            }
            R.id.action_delete -> {
                confirmDeleteFiles(activeSelectedFiles)
                true
            }
            R.id.action_select_overflow -> {
                showSelectionOverflowMenu()
                true
            }
            else -> false
        }

    private fun onOverlayActionModeFinished() {
        activeViewModel.clearSelectedFiles()
    }

    private fun confirmReplaceFile(file: FileItem, setFileName: Boolean = true) {
        if (setFileName) {
            val fileName = file.name
            binding.bottomCreateFileNameEdit.setText(fileName)
            binding.bottomCreateFileNameEdit.setSelection(
                0, fileName.asFileName().baseName.length
            )
        }
        ConfirmReplaceFileDialogFragment.show(file, this)
    }

    override fun replaceFile(file: FileItem) {
        pickFiles(fileItemSetOf(file))
    }

    private fun cutFiles(files: FileItemSet) {
        activeViewModel.addToPasteState(false, files)
        activeViewModel.selectFiles(files, false)
    }

    private fun copyFiles(files: FileItemSet) {
        activeViewModel.addToPasteState(true, files)
        activeViewModel.selectFiles(files, false)
    }

    private fun confirmDeleteFiles(files: FileItemSet) {
        ConfirmDeleteFilesDialogFragment.show(files, this)
    }

    override fun deleteFiles(files: FileItemSet) {
        FileJobService.delete(makePathListForJob(files), requireContext())
        activeViewModel.selectFiles(files, false)
    }

    private fun extractFiles(files: FileItemSet) {
        copyFiles(files.mapTo(fileItemSetOf()) { it.createDummyArchiveRoot() })
        activeViewModel.selectFiles(files, false)
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
        val archiveFile = activeViewModel.currentPath.resolve(name)
        FileJobService.archive(
            makePathListForJob(files), archiveFile, format, filter, password, requireContext()
        )
        activeViewModel.selectFiles(files, false)
    }

    private fun shareFiles(files: FileItemSet) {
        shareFiles(files.map { it.path }, files.map { it.mimeType })
        activeViewModel.selectFiles(files, false)
    }

    private fun selectAllFiles() {
        if (isPaneMode) {
            activePane?.selectAllFiles()
        } else {
            adapter.selectAllFiles()
        }
    }

    private fun onPasteStateChanged(pasteState: PasteState) {
        updateBottomToolbar()
    }

    private fun updateBottomToolbar() {
        val pickOptions = activeViewModel.pickOptions
        if (pickOptions != null) {
            bottomActionMode.setMenuResource(R.menu.file_list_pick_bottom)
            val menu = bottomActionMode.menu
            when (pickOptions.mode) {
                PickOptions.Mode.CREATE_FILE -> {
                    bottomActionMode.title = null
                    binding.bottomCreateFileNameEdit.isVisible = true
                    val createMenuItem = menu.findItem(R.id.action_create)
                    binding.bottomCreateFileNameEdit.setOnEditorConfirmActionListener {
                        onBottomActionModeMenuItemClicked(createMenuItem)
                    }
                    if (!viewModel.isCreateFileNameEditInitialized) {
                        val fileName = pickOptions.fileName!!
                        binding.bottomCreateFileNameEdit.setText(fileName)
                        binding.bottomCreateFileNameEdit.setSelection(
                            0, fileName.asFileName().baseName.length
                        )
                        binding.bottomCreateFileNameEdit.requestFocus()
                        viewModel.isCreateFileNameEditInitialized = true
                    }
                    menu.findItem(R.id.action_open).isVisible = false
                    createMenuItem.isVisible = true
                }
                PickOptions.Mode.OPEN_DIRECTORY -> {
                    val path = activeViewModel.currentPath
                    val navigationRoot = NavigationRootMapLiveData.valueCompat[path]
                    val name = navigationRoot?.getName(requireContext()) ?: path.name
                    bottomActionMode.title =
                        getString(R.string.file_list_open_current_directory_format, name)
                    binding.bottomCreateFileNameEdit.isVisible = false
                    menu.findItem(R.id.action_open).isVisible = true
                    menu.findItem(R.id.action_create).isVisible = false
                }
                else -> {
                    if (bottomActionMode.isActive) {
                        bottomActionMode.finish()
                    }
                    return
                }
            }
        } else {
            val pasteState = activeViewModel.pasteState
            val files = pasteState.files
            if (files.isEmpty()) {
                if (bottomActionMode.isActive) {
                    bottomActionMode.finish()
                }
                return
            }
            val areAllFilesArchivePaths = files.all { it.path.isArchivePath }
            bottomActionMode.title = getString(
                if (pasteState.copy) {
                    if (areAllFilesArchivePaths) {
                        R.string.file_list_paste_extract_title_format
                    } else {
                        R.string.file_list_paste_copy_title_format
                    }
                } else {
                    R.string.file_list_paste_move_title_format
                }, files.size
            )
            binding.bottomCreateFileNameEdit.isVisible = false
            bottomActionMode.setMenuResource(R.menu.file_list_paste)
            val isCurrentPathReadOnly = activeViewModel.currentPath.fileSystem.isReadOnly
            bottomActionMode.menu.findItem(R.id.action_paste)
                .setTitle(
                    if (areAllFilesArchivePaths) R.string.file_list_paste_action_extract_here else R.string.paste
                )
                .isEnabled = !isCurrentPathReadOnly
        }
        if (!bottomActionMode.isActive) {
            bottomActionMode.start(object : ToolbarActionMode.Callback {
                override fun onToolbarNavigationIconClicked(toolbarActionMode: ToolbarActionMode) {
                    onBottomToolbarNavigationIconClicked()
                }

                override fun onToolbarActionModeMenuItemClicked(
                    toolbarActionMode: ToolbarActionMode,
                    item: MenuItem
                ): Boolean = onBottomActionModeMenuItemClicked(item)

                override fun onToolbarActionModeFinished(toolbarActionMode: ToolbarActionMode) {
                    onBottomActionModeFinished()
                }
            })
        }
    }

    private fun onBottomToolbarNavigationIconClicked() {
        val pickOptions = viewModel.pickOptions
        if (pickOptions != null) {
            requireActivity().finish()
        } else {
            bottomActionMode.finish()
        }
    }

    private fun onBottomActionModeMenuItemClicked(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.action_open -> {
                pickPaths(linkedSetOf(viewModel.currentPath))
                true
            }
            R.id.action_create -> {
                val fileName = binding.bottomCreateFileNameEdit.text.toString()
                if (fileName.isEmpty()) {
                    showToast(R.string.file_list_create_file_name_error_empty)
                } else if (fileName.asFileNameOrNull() == null) {
                    showToast(R.string.file_list_create_file_name_error_invalid)
                } else {
                    val file = getFileWithName(fileName)
                    if (file != null) {
                        confirmReplaceFile(file, false)
                    } else {
                        val path = viewModel.currentPath.resolve(fileName)
                        pickPaths(linkedSetOf(path))
                    }
                }
                true
            }
            R.id.action_paste -> {
                pasteFiles(currentPath)
                true
            }
            else -> false
        }

    private fun onBottomActionModeFinished() {
        val pickOptions = viewModel.pickOptions
        if (pickOptions == null) {
            activeViewModel.clearPasteState()
        }
    }

    private fun pasteFiles(targetDirectory: Path) {
        val pasteState = activeViewModel.pasteState
        if (pasteState.copy) {
            FileJobService.copy(
                makePathListForJob(pasteState.files), targetDirectory, requireContext()
            )
        } else {
            FileJobService.move(
                makePathListForJob(pasteState.files), targetDirectory, requireContext()
            )
        }
        activeViewModel.clearPasteState()
    }

    private fun makePathListForJob(files: FileItemSet): List<Path> =
        files.map { it.path }.sortedBy { it.toUri() }

    private fun onFileNameEllipsizeChanged(fileNameEllipsize: FileNameEllipsize) {
        adapter.nameEllipsize = fileNameEllipsize
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
        val pickOptions = viewModel.pickOptions
        if (pickOptions != null) {
            if (file.attributes.isDirectory) {
                navigateTo(file.path)
            } else {
                when (pickOptions.mode) {
                    PickOptions.Mode.OPEN_FILE -> pickFiles(fileItemSetOf(file))
                    PickOptions.Mode.CREATE_FILE -> confirmReplaceFile(file)
                    PickOptions.Mode.OPEN_DIRECTORY -> {}
                }
            }
            return
        }
        if (file.mimeType.isApk) {
            openApk(file)
            return
        }
        if (file.mimeType.isVideo) {
            startVideoViewer(requireContext(), adapterFileList(), file.path)
            return
        }
        if (file.isListable) {
            navigateTo(file.listablePath)
            return
        }
        openFileWithIntent(file, false)
    }

    private fun adapterFileList(): List<FileItem> =
        (0..<adapter.itemCount).map { adapter.getItem(it) }

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
        navigateTo(file.listablePath)
    }

    override fun openFileWith(file: FileItem) {
        openFileWithIntent(file, true)
    }

    private fun openFileWithIntent(file: FileItem, withChooser: Boolean) {
        val path = file.path
        val mimeType = file.mimeType
        // Open images in the built-in viewer explicitly; an implicit VIEW intent could be
        // routed to the system gallery if it is the device default handler for image/*.
        if (mimeType.isImage && !path.isArchivePath) {
            startImageViewer(requireContext(), adapterFileList(), path)
            return
        }
        if (path.isArchivePath) {
            FileJobService.open(path, mimeType, withChooser, requireContext())
        } else {
            val intent = path.fileProviderUri.createViewIntent(mimeType)
                .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                .apply {
                    extraPath = path
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

    override fun cutFile(file: FileItem) {
        cutFiles(fileItemSetOf(file))
    }

    override fun copyFile(file: FileItem) {
        copyFiles(fileItemSetOf(file))
    }

    override fun confirmDeleteFile(file: FileItem) {
        confirmDeleteFiles(fileItemSetOf(file))
    }

    override fun showRenameFileDialog(file: FileItem) {
        RenameFileDialogFragment.show(file, this)
    }

    override fun hasFileWithName(name: String): Boolean = getFileWithName(name) != null

    private fun getFileWithName(name: String): FileItem? {
        val fileListData = activeViewModel.fileListStateful
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

    override fun shareFile(file: FileItem) {
        shareFile(file.path, file.mimeType)
    }

    private fun shareFile(path: Path, mimeType: MimeType) {
        shareFiles(listOf(path), listOf(mimeType))
    }

    private fun shareFiles(paths: List<Path>, mimeTypes: List<MimeType>) {
        val uris = paths.map { it.fileProviderUri }
        val intent = uris.createSendStreamIntent(mimeTypes)
            .withChooser()
        startActivitySafe(intent)
    }

    override fun copyPath(file: FileItem) {
        copyPath(file.path)
    }

    override fun addBookmark(file: FileItem) {
        addBookmark(file.path)
    }

    private fun addBookmark() {
        addBookmark(currentPath)
    }

    private fun addBookmark(path: Path) {
        BookmarkDirectories.add(BookmarkDirectory(null, path))
        showToast(R.string.file_add_bookmark_success)
    }

    override fun createShortcut(file: FileItem) {
        createShortcut(file.path, file.mimeType)
    }

    private fun createShortcut() {
        createShortcut(currentPath, MimeType.DIRECTORY)
    }

    private fun createShortcut(path: Path, mimeType: MimeType) {
        val context = requireContext()
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

    override fun showVideoInfo(file: FileItem) {
        startVideoInfoActivity(requireContext(), file)
    }

    override fun showImageInfo(file: FileItem) {
        startImageInfoActivity(requireContext(), file)
    }

    private fun showCreateFileDialog() {
        CreateFileDialogFragment.show(this)
    }

    override fun createFile(name: String) {
        val path = currentPath.resolve(name)
        FileJobService.create(path, false, requireContext())
    }

    private fun showCreateDirectoryDialog() {
        CreateDirectoryDialogFragment.show(this)
    }

    override fun createDirectory(name: String) {
        val path = currentPath.resolve(name)
        FileJobService.create(path, true, requireContext())
    }

    override val currentPath: Path
        get() = activeViewModel.currentPath

    override fun navigateToRoot(path: Path) {
        collapseSearchView()
        activeViewModel.resetTo(path)
    }

    override fun navigateToDefaultRoot() {
        navigateToRoot(Settings.FILE_LIST_DEFAULT_DIRECTORY.valueCompat)
    }

    override fun observeCurrentPath(owner: LifecycleOwner, observer: (Path) -> Unit) {
        viewModel.currentPathLiveData.observe(owner, observer)
    }

    override fun closeNavigationDrawer() {
        binding.drawerLayout?.closeDrawer(GravityCompat.START)
    }

    private fun ensureStorageAccess() {
        if (viewModel.isStorageAccessRequested) {
            return
        }
        if (Environment::class.supportsExternalStorageManager()) {
            if (!Environment.isExternalStorageManager()) {
                ShowRequestAllFilesAccessRationaleDialogFragment.show(this)
                viewModel.isStorageAccessRequested = true
            }
        } else if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            if (shouldShowRequestPermissionRationale(
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                )) {
                ShowRequestStoragePermissionRationaleDialogFragment.show(this)
            } else {
                requestStoragePermission()
            }
            viewModel.isStorageAccessRequested = true
        }
    }

    override fun onShowRequestAllFilesAccessRationaleResult(shouldRequest: Boolean) {
        if (shouldRequest) {
            requestAllFilesAccess()
        } else {
            viewModel.isStorageAccessRequested = false
            // This isn't an onActivityResult() callback so it's not delivered before calling
            // onResume(), and we need to do this manually.
            ensureNotificationPermission()
        }
    }

    private fun requestAllFilesAccess() {
        requestAllFilesAccessLauncher.launch(Unit)
    }

    private fun onRequestAllFilesAccessResult(isGranted: Boolean) {
        viewModel.isStorageAccessRequested = false
        if (isGranted) {
            refresh()
        }
    }

    override fun onShowRequestStoragePermissionRationaleResult(shouldRequest: Boolean) {
        if (shouldRequest) {
            requestStoragePermission()
        } else {
            viewModel.isStorageAccessRequested = false
        }
    }

    private fun requestStoragePermission() {
        requestStoragePermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    private fun onRequestStoragePermissionResult(isGranted: Boolean) {
        if (isGranted) {
            viewModel.isStorageAccessRequested = false
            refresh()
        } else if (shouldShowRequestPermissionRationale(
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        )) {
            ShowRequestStoragePermissionRationaleDialogFragment.show(this)
        } else {
            ShowRequestStoragePermissionInSettingsRationaleDialogFragment.show(this)
        }
    }

    override fun onShowRequestStoragePermissionInSettingsRationaleResult(shouldRequest: Boolean) {
        if (shouldRequest) {
            requestStoragePermissionInSettings()
        } else {
            viewModel.isStorageAccessRequested = false
        }
    }

    private fun requestStoragePermissionInSettings() {
        requestStoragePermissionInSettingsLauncher.launch(Unit)
    }

    private fun onRequestStoragePermissionInSettingsResult(isGranted: Boolean) {
        viewModel.isStorageAccessRequested = false
        if (isGranted) {
            refresh()
        }
    }

    private fun ensureNotificationPermission() {
        if (viewModel.isNotificationPermissionRequested) {
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) {
                if (shouldShowRequestPermissionRationale(
                        android.Manifest.permission.POST_NOTIFICATIONS
                    )) {
                    ShowRequestNotificationPermissionRationaleDialogFragment.show(this)
                } else {
                    requestNotificationPermission()
                }
                viewModel.isNotificationPermissionRequested = true
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onShowRequestNotificationPermissionRationaleResult(shouldRequest: Boolean) {
        if (shouldRequest) {
            requestNotificationPermission()
        } else {
            viewModel.isNotificationPermissionRequested = false
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun requestNotificationPermission() {
        requestNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun onRequestNotificationPermissionResult(isGranted: Boolean) {
        if (isGranted) {
            viewModel.isNotificationPermissionRequested = false
        } else if (shouldShowRequestPermissionRationale(
            android.Manifest.permission.POST_NOTIFICATIONS
        )) {
            ShowRequestNotificationPermissionRationaleDialogFragment.show(this)
        } else {
            ShowRequestNotificationPermissionInSettingsRationaleDialogFragment.show(this)
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onShowRequestNotificationPermissionInSettingsRationaleResult(
        shouldRequest: Boolean
    ) {
        if (shouldRequest) {
            requestNotificationPermissionInSettings()
        } else {
            viewModel.isNotificationPermissionRequested = false
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun requestNotificationPermissionInSettings() {
        requestNotificationPermissionInSettingsLauncher.launch(Unit)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun onRequestNotificationPermissionInSettingsResult(isGranted: Boolean) {
        if (isGranted) {
            viewModel.isNotificationPermissionRequested = false
        }
    }

    companion object {
        private const val ACTION_VIEW_DOWNLOADS =
            "dev.vcgms.aapp.vaterialfiles.intent.action.VIEW_DOWNLOADS"

    }

    private class RequestAllFilesAccessContract : ActivityResultContract<Unit, Boolean>() {
        @RequiresApi(Build.VERSION_CODES.R)
        override fun createIntent(context: Context, input: Unit): Intent =
            Environment::class.createManageAppAllFilesAccessPermissionIntent(context.packageName)

        @RequiresApi(Build.VERSION_CODES.R)
        override fun parseResult(resultCode: Int, intent: Intent?): Boolean =
            Environment.isExternalStorageManager()
    }

    private class RequestPermissionInSettingsContract(private val permissionName: String)
        : ActivityResultContract<Unit, Boolean>() {
        override fun createIntent(context: Context, input: Unit): Intent =
            Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            )

        override fun parseResult(resultCode: Int, intent: Intent?): Boolean =
            application.checkSelfPermissionCompat(permissionName) ==
                PackageManager.PERMISSION_GRANTED
    }

    @Parcelize
    class Args(val intent: Intent) : ParcelableArgs

    private class Binding private constructor(
        val root: View,
        val drawerLayout: DrawerLayout? = null,
        val persistentDrawerLayout: PersistentDrawerLayout? = null,
        val persistentBarLayout: PersistentBarLayout,
        val appBarLayout: CoordinatorAppBarLayout,
        val toolbar: Toolbar,
        val overlayToolbar: Toolbar,
        val breadcrumbLayout: BreadcrumbLayout,
        val storageAnalysisIndicator: View,
        val storageAnalysisIcon: ImageView,
        val storageAnalysisProgress: ProgressBar,
        val contentLayout: ViewGroup,
        val dualPaneContainer: LinearLayout?,
        val columnPaneContainer: HorizontalScrollView?,
        val columnPaneRow: LinearLayout?,
        val progress: ProgressBar,
        val errorText: TextView,
        val emptyView: View,
        val swipeRefreshLayout: SwipeRefreshLayout,
        val recyclerView: RecyclerView,
        val bottomBarLayout: ViewGroup,
        val bottomToolbar: Toolbar,
        val bottomCreateFileNameEdit: EditText,
        val speedDialView: SpeedDialView
    ) {
        companion object {
            fun inflate(
                inflater: LayoutInflater,
                root: ViewGroup?,
                attachToRoot: Boolean
            ): Binding {
                val binding = FileListFragmentBinding.inflate(inflater, root, attachToRoot)
                val bindingRoot = binding.root
                val includeBinding = FileListFragmentIncludeBinding.bind(bindingRoot)
                val appBarBinding = FileListFragmentAppBarIncludeBinding.bind(bindingRoot)
                val contentBinding = FileListFragmentContentIncludeBinding.bind(bindingRoot)
                val bottomBarBinding = FileListFragmentBottomBarIncludeBinding.bind(bindingRoot)
                val speedDialBinding = FileListFragmentSpeedDialIncludeBinding.bind(bindingRoot)
                return Binding(
                    bindingRoot, includeBinding.drawerLayout, includeBinding.persistentDrawerLayout,
                    includeBinding.persistentBarLayout, appBarBinding.appBarLayout,
                    appBarBinding.toolbar, appBarBinding.overlayToolbar,
                    appBarBinding.breadcrumbLayout,
                    appBarBinding.storageAnalysisIndicator, appBarBinding.storageAnalysisIcon,
                    appBarBinding.storageAnalysisProgress,
                    contentBinding.contentLayout,
                    includeBinding.dualPaneContainer,
                    includeBinding.columnPaneContainer,
                    includeBinding.columnPaneRow,
                    contentBinding.progress, contentBinding.errorText, contentBinding.emptyView,
                    contentBinding.swipeRefreshLayout, contentBinding.recyclerView,
                    bottomBarBinding.bottomBarLayout, bottomBarBinding.bottomToolbar,
                    bottomBarBinding.bottomCreateFileNameEdit, speedDialBinding.speedDialView
                )
            }
        }
    }

    private class MenuBinding private constructor(
        val menu: Menu,
        val searchItem: MenuItem,
        val viewSortItem: MenuItem
    ) {
        companion object {
            fun inflate(menu: Menu, inflater: MenuInflater): MenuBinding {
                inflater.inflate(R.menu.file_list, menu)
                return MenuBinding(
                    menu, menu.findItem(R.id.action_search), menu.findItem(R.id.action_view_sort)
                )
            }
        }
    }
}
