package dev.vcgms.aapp.vaterialfiles.filelist

import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.TypedValue
import android.view.Menu
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import coil.dispose
import coil.load
import java8.nio.file.Path
import me.zhanghai.android.fastscroll.PopupTextProvider
import dev.vcgms.aapp.vaterialfiles.R
import dev.vcgms.aapp.vaterialfiles.coil.AppIconPackageName
import dev.vcgms.aapp.vaterialfiles.compat.foregroundCompat
import dev.vcgms.aapp.vaterialfiles.compat.getDrawableCompat
import dev.vcgms.aapp.vaterialfiles.compat.isSingleLineCompat
import dev.vcgms.aapp.vaterialfiles.databinding.FileItemGridBinding
import dev.vcgms.aapp.vaterialfiles.databinding.FileItemListBinding
import dev.vcgms.aapp.vaterialfiles.file.FileItem
import dev.vcgms.aapp.vaterialfiles.file.fileSize
import dev.vcgms.aapp.vaterialfiles.file.formatShort
import dev.vcgms.aapp.vaterialfiles.file.iconRes
import dev.vcgms.aapp.vaterialfiles.file.isApk
import dev.vcgms.aapp.vaterialfiles.provider.archive.isArchivePath
import dev.vcgms.aapp.vaterialfiles.provider.common.isEncrypted
import dev.vcgms.aapp.vaterialfiles.provider.common.newDirectoryStream
import dev.vcgms.aapp.vaterialfiles.settings.Settings
import dev.vcgms.aapp.vaterialfiles.ui.AnimatedListAdapter
import dev.vcgms.aapp.vaterialfiles.ui.CheckableForegroundLinearLayout
import dev.vcgms.aapp.vaterialfiles.ui.CheckableItemBackground
import dev.vcgms.aapp.vaterialfiles.util.isMaterial3Theme
import dev.vcgms.aapp.vaterialfiles.util.layoutInflater
import dev.vcgms.aapp.vaterialfiles.util.valueCompat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class FileListAdapter(
    private val listener: Listener
) : AnimatedListAdapter<FileItem, FileListAdapter.ViewHolder>(CALLBACK), PopupTextProvider {
    private var isSearching = false

    // The fragment hosting this adapter, used to show the item actions dialog.
    var hostFragment: Fragment? = null

    // When set (single-pane list mode), a long press on an item is reported to this callback so
    // that the fragment can start a drag instead of showing the item menu.
    var itemDragListener: ((View, FileItem) -> Unit)? = null

    // Whether the per-item three-dot menu button is shown; when hidden, a long press on the item
    // shows its menu in a centered dialog instead.
    var isMenuButtonVisible: Boolean = true

    // Denser styling (slightly smaller text and narrower side margins) used by dual-pane columns.
    var isDenseStyle: Boolean = false

    // When set (dual-pane mode), the item menu gains "move/copy to the other pane" options; the
    // callback receives the file and whether to copy (true) or move (false).
    var moveToOtherPaneListener: ((FileItem, Boolean) -> Unit)? = null

    var moveToOtherPaneTitle: String? = null

    var copyToOtherPaneTitle: String? = null

    var moveToOtherPaneIconRes: Int = 0

    var copyToOtherPaneIconRes: Int = 0

    // The file whose actions dialog is currently shown, so that its result can be dispatched.
    private var pendingActionsFile: FileItem? = null

    private lateinit var _viewType: FileViewType
    var viewType: FileViewType
        get() = _viewType
        set(value) {
            _viewType = value
            if (!isSearching) {
                super.replace(list, true)
            }
        }

    private lateinit var _sortOptions: FileSortOptions
    var sortOptions: FileSortOptions
        get() = _sortOptions
        set(value) {
            _sortOptions = value
            if (!isSearching) {
                val sortedList = list.sortedWith(value.createComparator())
                super.replace(sortedList, true)
                rebuildFilePositionMap()
            }
        }

    var pickOptions: PickOptions? = null
        set(value) {
            field = value
            notifyItemRangeChanged(0, itemCount, PAYLOAD_STATE_CHANGED)
        }

    private val selectedFiles = fileItemSetOf()

    private val filePositionMap = mutableMapOf<Path, Int>()

    private lateinit var _nameEllipsize: TextUtils.TruncateAt
    var nameEllipsize: TextUtils.TruncateAt
        get() = _nameEllipsize
        set(value) {
            _nameEllipsize = value
            notifyItemRangeChanged(0, itemCount, PAYLOAD_STATE_CHANGED)
        }

    fun replaceSelectedFiles(files: FileItemSet) {
        val changedFiles = fileItemSetOf()
        val iterator = selectedFiles.iterator()
        while (iterator.hasNext()) {
            val file = iterator.next()
            if (file !in files) {
                iterator.remove()
                changedFiles.add(file)
            }
        }
        for (file in files) {
            if (file !in selectedFiles) {
                selectedFiles.add(file)
                changedFiles.add(file)
            }
        }
        for (file in changedFiles) {
            val position = filePositionMap[file.path]
            position?.let { notifyItemChanged(it, PAYLOAD_STATE_CHANGED) }
        }
    }

    private fun selectFile(file: FileItem) {
        if (!isFileSelectable(file)) {
            return
        }
        val selected = file in selectedFiles
        val pickOptions = pickOptions
        if (!selected && pickOptions != null && !pickOptions.allowMultiple) {
            listener.clearSelectedFiles()
        }
        listener.selectFile(file, !selected)
    }

    // Selects the file only if it is not selected yet, keeping single-choice pick handling.
    private fun selectFileIfUnselected(file: FileItem) {
        if (file !in selectedFiles) {
            selectFile(file)
        }
    }

    fun selectAllFiles() {
        val files = fileItemSetOf()
        for (index in 0..<itemCount) {
            val file = getItem(index)
            if (isFileSelectable(file)) {
                files.add(file)
            }
        }
        listener.selectFiles(files, true)
    }

    private fun isFileSelectable(file: FileItem): Boolean {
        val pickOptions = pickOptions ?: return true
        return when (pickOptions.mode) {
            PickOptions.Mode.OPEN_FILE, PickOptions.Mode.CREATE_FILE ->
                !file.attributes.isDirectory &&
                    pickOptions.mimeTypes.any { it.match(file.mimeType) }
            PickOptions.Mode.OPEN_DIRECTORY -> file.attributes.isDirectory
        }
    }

    override fun clear() {
        super.clear()

        rebuildFilePositionMap()
    }

    @Deprecated("", ReplaceWith("replaceListAndSearching(list, searching)"))
    override fun replace(list: List<FileItem>, clear: Boolean) {
        throw UnsupportedOperationException()
    }

    fun replaceListAndIsSearching(list: List<FileItem>, isSearching: Boolean) {
        val clear = this.isSearching != isSearching
        this.isSearching = isSearching
        val sortedList = if (!isSearching) list.sortedWith(sortOptions.createComparator()) else list
        directoryItemCountCache.clear()
        directoryItemCountPending.clear()
        super.replace(sortedList, clear)
        rebuildFilePositionMap()
    }

    private fun rebuildFilePositionMap() {
        filePositionMap.clear()
        for (index in 0..<itemCount) {
            val file = getItem(index)
            filePositionMap[file.path] = index
        }
    }

    override fun getItemViewType(position: Int): Int = viewType.ordinal

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val viewType = FileViewType.entries[viewType]
        val inflater = parent.context.layoutInflater
        val holder = when (viewType) {
            FileViewType.LIST -> ViewHolder(FileItemListBinding.inflate(inflater, parent, false))
            FileViewType.GRID -> ViewHolder(FileItemGridBinding.inflate(inflater, parent, false))
        }
        return holder.apply {
            itemLayout.apply {
                val context = context
                val isMaterial3Theme = context.isMaterial3Theme
                if (viewType == FileViewType.GRID && isMaterial3Theme) {
                    foregroundCompat =
                        context.getDrawableCompat(R.drawable.file_item_grid_foreground_material3)
                }
                background = if (viewType == FileViewType.GRID && isMaterial3Theme) {
                    CheckableItemBackground.create(4f, 12f, context)
                } else {
                    CheckableItemBackground.create(0f, 0f, context)
                }
            }
            thumbnailOutlineView?.apply {
                val context = context
                if (context.isMaterial3Theme) {
                    background = context.getDrawableCompat(
                        R.drawable.file_item_grid_thumbnail_outline_material3
                    )
                }
            }
            if (isDenseStyle) {
                applyDenseStyle(holder, viewType)
            }
            popupMenu = PopupMenu(menuButton.context, menuButton)
                .apply {
                    setForceShowIcon(true)
                    inflate(R.menu.file_item)
                    if (moveToOtherPaneListener != null) {
                        menu.add(
                            Menu.NONE, R.id.action_move_to_other_pane, Menu.NONE,
                            moveToOtherPaneTitle
                        ).setIcon(moveToOtherPaneIconRes)
                        menu.add(
                            Menu.NONE, R.id.action_copy_to_other_pane, Menu.NONE,
                            copyToOtherPaneTitle
                        ).setIcon(copyToOtherPaneIconRes)
                    }
                }
            menuButton.setOnClickListener { popupMenu.show() }
        }
    }

    private fun applyDenseStyle(holder: ViewHolder, viewType: FileViewType) {
        val density = holder.itemView.resources.displayMetrics.density
        when (viewType) {
            FileViewType.LIST -> {
                (holder.iconLayout.layoutParams as? LinearLayout.LayoutParams)?.apply {
                    marginStart = (DENSE_LIST_ICON_MARGIN_START_DP * density).toInt()
                }
            }
            FileViewType.GRID -> {
                holder.thumbnailContainer?.let { container ->
                    (container.layoutParams as? LinearLayout.LayoutParams)?.apply {
                        marginStart = (DENSE_GRID_MARGIN_DP * density).toInt()
                        marginEnd = (DENSE_GRID_MARGIN_DP * density).toInt()
                        topMargin = (DENSE_GRID_MARGIN_DP * density).toInt()
                    }
                }
            }
        }
    }

    // Pins item text to fixed sizes shared by single-pane and dual-pane columns.
    private fun applyDenseTextSize(holder: ViewHolder) {
        holder.nameText.setTextSize(TypedValue.COMPLEX_UNIT_SP, DENSE_NAME_TEXT_SP)
        holder.descriptionText?.setTextSize(TypedValue.COMPLEX_UNIT_SP, DENSE_DESCRIPTION_TEXT_SP)
    }

    private val directoryItemCountCache = ConcurrentHashMap<Path, Int>()
    private val directoryItemCountPending = ConcurrentHashMap.newKeySet<Path>()
    private val itemCountExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "file-list-directory-item-count").apply { isDaemon = true }
    }
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun refresh() {
        directoryItemCountCache.clear()
        directoryItemCountPending.clear()
        super.refresh()
    }

    private fun buildDirectoryDescription(
        descriptionText: TextView,
        file: FileItem,
        itemCount: Int?
    ): String {
        val context = descriptionText.context
        val lastModificationTime = file.attributes.lastModifiedTime().toInstant()
            .formatShort(context)
        val descriptionSeparator = context.getString(R.string.file_item_description_separator)
        return if (itemCount != null) {
            val itemCountText = context.resources.getQuantityString(
                R.plurals.file_item_directory_item_count_format, itemCount, itemCount)
            listOf(lastModificationTime, itemCountText).joinToString(descriptionSeparator)
        } else {
            lastModificationTime
        }
    }

    private fun loadDirectoryItemCount(holder: ViewHolder, path: Path, file: FileItem) {
        itemCountExecutor.execute {
            val count = runCatching { path.newDirectoryStream().use { it.count() } }.getOrNull()
            directoryItemCountPending.remove(path)
            if (count != null) {
                directoryItemCountCache[path] = count
                mainHandler.post {
                    if (holder.boundPath == path) {
                        holder.descriptionText?.text =
                            buildDirectoryDescription(holder.descriptionText!!, file, count)
                    }
                }
            }
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        throw UnsupportedOperationException()
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: List<Any>) {
        val file = getItem(position)
        val isDirectory = file.attributes.isDirectory
        val isEnabled = isFileSelectable(file) || isDirectory
        holder.itemLayout.isEnabled = isEnabled
        holder.menuButton.isEnabled = isEnabled
        holder.menuButton.isVisible = isMenuButtonVisible && viewType == FileViewType.LIST
        // Item text sizes are uniform across single-pane and dual-pane columns.
        applyDenseTextSize(holder)
        val menu = holder.popupMenu.menu
        val path = file.path
        val hasPickOptions = pickOptions != null
        val isReadOnly = path.fileSystem.isReadOnly
        menu.findItem(R.id.action_cut).isVisible = !hasPickOptions && !isReadOnly
        menu.findItem(R.id.action_copy).isVisible = !hasPickOptions
        if (moveToOtherPaneListener != null) {
            menu.findItem(R.id.action_move_to_other_pane).isVisible = !hasPickOptions && !isReadOnly
            menu.findItem(R.id.action_copy_to_other_pane).isVisible = !hasPickOptions
        }
        val checked = file in selectedFiles
        holder.itemLayout.isChecked = checked
        holder.nameText.apply {
            if (isSingleLineCompat) {
                val nameEllipsize = nameEllipsize
                ellipsize = nameEllipsize
                isSelected = nameEllipsize == TextUtils.TruncateAt.MARQUEE
            }
        }
        if (payloads.isNotEmpty()) {
            return
        }
        bindViewHolderAnimation(holder)
        holder.itemLayout.apply {
            setOnClickListener {
                if (selectedFiles.isEmpty()) {
                    listener.openFile(file)
                } else {
                    selectFile(file)
                }
            }
            setOnLongClickListener {
                val dragListener = itemDragListener
                if (dragListener != null && viewType == FileViewType.LIST && pickOptions == null) {
                    dragListener(this, file)
                } else if (viewType == FileViewType.GRID || !isMenuButtonVisible) {
                    showItemActionsDialog(file, menu)
                } else if (selectedFiles.isEmpty()) {
                    selectFile(file)
                } else {
                    listener.openFile(file)
                }
                true
            }
        }
        holder.iconLayout.setOnClickListener { selectFile(file) }
        val iconRes = file.mimeType.iconRes
        holder.iconImage.apply {
            isVisible = true
            setImageResource(iconRes)
        }
        holder.directoryThumbnailImage?.isVisible = isDirectory
        holder.thumbnailOutlineView?.isVisible = !isDirectory
        val supportsThumbnail = file.supportsThumbnail
        val shouldLoadThumbnailIcon = supportsThumbnail && holder.thumbnailIconImage != null &&
            file.mimeType.isApk
        val attributes = file.attributes
        holder.thumbnailIconImage?.apply {
            dispose()
            isVisible = !isDirectory
            setImageResource(iconRes)
            if (shouldLoadThumbnailIcon) {
                load(path to attributes)
            }
        }
        holder.thumbnailImage.apply {
            dispose()
            setImageDrawable(null)
            val shouldLoadThumbnail = supportsThumbnail && !shouldLoadThumbnailIcon
            isVisible = shouldLoadThumbnail
            if (shouldLoadThumbnail) {
                load(path to attributes) {
                    listener { _, _ ->
                        val iconImage = holder.thumbnailIconImage ?: holder.iconImage
                        iconImage.isVisible = false
                    }
                }
            }
        }
        holder.appIconBadgeImage.apply {
            dispose()
            setImageDrawable(null)
            val appDirectoryPackageName = file.appDirectoryPackageName
            val hasAppIconBadge = appDirectoryPackageName != null
            isVisible = hasAppIconBadge
            if (hasAppIconBadge) {
                load(AppIconPackageName(appDirectoryPackageName!!))
            }
        }
        holder.badgeImage.apply {
            val badgeIconRes = if (file.attributesNoFollowLinks.isSymbolicLink) {
                if (file.isSymbolicLinkBroken) {
                    R.drawable.error_badge_icon_18dp
                } else {
                    R.drawable.symbolic_link_badge_icon_18dp
                }
            } else if (file.attributesNoFollowLinks.isEncrypted()) {
                R.drawable.encrypted_badge_icon_18dp
            } else {
                null
            }
            val hasBadge = badgeIconRes != null
            isVisible = hasBadge
            if (hasBadge) {
                setImageResource(badgeIconRes!!)
            } else {
                setImageDrawable(null)
            }
        }
        holder.nameText.text = file.name
        holder.boundPath = path
        holder.descriptionText?.text = if (isDirectory) {
            buildDirectoryDescription(holder.descriptionText!!, file, directoryItemCountCache[path])
        } else {
            val context = holder.descriptionText!!.context
            val lastModificationTime = attributes.lastModifiedTime().toInstant()
                .formatShort(context)
            val size = attributes.fileSize.formatHumanReadable(context)
            val descriptionSeparator = context.getString(R.string.file_item_description_separator)
            listOf(lastModificationTime, size).joinToString(descriptionSeparator)
        }
        if (isDirectory && !directoryItemCountCache.containsKey(path)
            && directoryItemCountPending.add(path)) {
            loadDirectoryItemCount(holder, path, file)
        }
        val isArchivePath = path.isArchivePath
        menu.findItem(R.id.action_copy)
            .setTitle(if (isArchivePath) R.string.file_item_action_extract else R.string.copy)
        menu.findItem(R.id.action_delete).isVisible = !isReadOnly
        menu.findItem(R.id.action_rename).isVisible = !isReadOnly
        menu.findItem(R.id.action_extract).isVisible = file.isArchiveFile
        menu.findItem(R.id.action_archive).isVisible = !isArchivePath
        menu.findItem(R.id.action_add_bookmark).isVisible = isDirectory
        holder.popupMenu.setOnMenuItemClickListener { performMenuAction(file, it.itemId) }
    }

    // Dispatches a menu item of the per-item menu, shared by the menu button popup and the long
    // press actions dialog.
    private fun performMenuAction(file: FileItem, itemId: Int): Boolean {
        return when (itemId) {
            R.id.action_select -> {
                listener.selectFile(file, true)
                true
            }
            R.id.action_open_with -> {
                listener.openFileWith(file)
                true
            }
            R.id.action_cut -> {
                listener.cutFile(file)
                true
            }
            R.id.action_copy -> {
                listener.copyFile(file)
                true
            }
            R.id.action_delete -> {
                listener.confirmDeleteFile(file)
                true
            }
            R.id.action_rename -> {
                listener.showRenameFileDialog(file)
                true
            }
            R.id.action_extract -> {
                listener.extractFile(file)
                true
            }
            R.id.action_archive -> {
                listener.showCreateArchiveDialog(file)
                true
            }
            R.id.action_share -> {
                listener.shareFile(file)
                true
            }
            R.id.action_copy_path -> {
                listener.copyPath(file)
                true
            }
            R.id.action_add_bookmark -> {
                listener.addBookmark(file)
                true
            }
            R.id.action_create_shortcut -> {
                listener.createShortcut(file)
                true
            }
            R.id.action_properties -> {
                listener.showPropertiesDialog(file)
                true
            }
            R.id.action_move_to_other_pane -> {
                moveToOtherPaneListener?.invoke(file, false)
                true
            }
            R.id.action_copy_to_other_pane -> {
                moveToOtherPaneListener?.invoke(file, true)
                true
            }
            else -> false
        }
    }

    fun onItemActionsDialogResult(actionId: Int) {
        val file = pendingActionsFile ?: return
        pendingActionsFile = null
        performMenuAction(file, actionId)
    }

    // Enables starting a continuous multi-selection from an item's icon: a drag that begins on an
    // icon selects (or, if the start item is already selected, deselects) every item it passes
    // over instead of scrolling the list; starting anywhere else scrolls normally. A plain tap on
    // the icon keeps its original toggle behavior.
    fun installIconMultiSelectGesture(recyclerView: RecyclerView) {
        recyclerView.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            private var downFile: FileItem? = null
            private var downSelected = false
            private var hasMoved = false
            private var lastTouchedFile: FileItem? = null

            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                if (e.actionMasked != MotionEvent.ACTION_DOWN) {
                    return false
                }
                val child = rv.findChildViewUnder(e.x, e.y) ?: return false
                val position = rv.getChildAdapterPosition(child)
                if (position == RecyclerView.NO_POSITION) {
                    return false
                }
                val holder = rv.getChildViewHolder(child) as? ViewHolder ?: return false
                if (!isInsideIconLayout(child, holder, e.x, e.y)) {
                    return false
                }
                val file = getItem(position)
                downFile = file
                downSelected = file in selectedFiles
                hasMoved = false
                lastTouchedFile = null
                // Keep the enclosing SwipeRefreshLayout from stealing this gesture, so dragging
                // from an icon never triggers pull-to-refresh.
                rv.requestDisallowInterceptTouchEvent(true)
                return true
            }

            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
                when (e.actionMasked) {
                    MotionEvent.ACTION_MOVE -> {
                        hasMoved = true
                        val child = rv.findChildViewUnder(e.x, e.y) ?: return
                        val position = rv.getChildAdapterPosition(child)
                        if (position == RecyclerView.NO_POSITION) {
                            return
                        }
                        val file = getItem(position)
                        if (file != lastTouchedFile) {
                            lastTouchedFile = file
                            if (downSelected) {
                                if (file in selectedFiles) {
                                    listener.selectFile(file, false)
                                }
                            } else {
                                selectFileIfUnselected(file)
                            }
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        val file = downFile
                        downFile = null
                        if (file != null && !hasMoved) {
                            selectFile(file)
                        }
                        hasMoved = false
                        lastTouchedFile = null
                        rv.requestDisallowInterceptTouchEvent(false)
                    }
                }
            }

            private fun isInsideIconLayout(
                child: View,
                holder: ViewHolder,
                x: Float,
                y: Float
            ): Boolean {
                val iconLayout = holder.iconLayout
                val left = child.left + iconLayout.left
                val top = child.top + iconLayout.top
                val right = left + iconLayout.width
                val bottom = top + iconLayout.height
                return x >= left && x < right && y >= top && y < bottom
            }
        })
    }

    private fun showItemActionsDialog(file: FileItem, menu: Menu) {
        val fragment = hostFragment ?: return
        pendingActionsFile = file
        FileActionsDialogFragment.show(fragment, buildFileActions(menu))
    }

    private fun buildFileActions(menu: Menu): List<FileActionsDialogFragment.FileAction> {
        val fragment = hostFragment
        val actions = mutableListOf<FileActionsDialogFragment.FileAction>()
        // Grid view has no per-item selection affordance, so offer entering selection mode here.
        if (viewType == FileViewType.GRID && fragment != null) {
            actions.add(
                FileActionsDialogFragment.FileAction(
                    R.id.action_select,
                    fragment.getString(R.string.file_item_action_select),
                    R.drawable.check_icon_control_normal_24dp
                )
            )
        }
        // Offer moving/copying to the other pane before the regular actions when available.
        for (itemId in intArrayOf(
            R.id.action_move_to_other_pane, R.id.action_copy_to_other_pane
        )) {
            val item = menu.findItem(itemId) ?: continue
            if (item.isVisible) {
                actions.add(
                    FileActionsDialogFragment.FileAction(
                        item.itemId, item.title.toString(), menuItemIconRes(item.itemId)
                    )
                )
            }
        }
        for (index in 0 until menu.size()) {
            val item = menu.getItem(index)
            if (!item.isVisible || item.itemId == R.id.action_move_to_other_pane ||
                item.itemId == R.id.action_copy_to_other_pane) {
                continue
            }
            // Resolve the label from resources rather than the inflated menu title so the dialog
            // always shows a non-empty label.
            val titleRes = menuItemTitleRes(item.itemId)
            val title = if (titleRes != 0 && fragment != null) {
                fragment.getString(titleRes)
            } else {
                item.title.toString()
            }
            actions.add(
                FileActionsDialogFragment.FileAction(
                    item.itemId, title, menuItemIconRes(item.itemId)
                )
            )
        }
        return actions
    }

    private fun menuItemTitleRes(itemId: Int): Int = when (itemId) {
        R.id.action_open_with -> R.string.file_item_action_open_with
        R.id.action_cut -> R.string.cut
        R.id.action_copy -> R.string.copy
        R.id.action_delete -> R.string.delete
        R.id.action_rename -> R.string.rename
        R.id.action_extract -> R.string.file_item_action_extract
        R.id.action_archive -> R.string.file_item_action_archive
        R.id.action_share -> R.string.share
        R.id.action_copy_path -> R.string.file_item_action_copy_path
        R.id.action_add_bookmark -> R.string.file_item_action_add_bookmark
        R.id.action_create_shortcut -> R.string.file_item_action_create_shortcut
        R.id.action_properties -> R.string.file_item_action_properties
        else -> 0
    }

    private fun menuItemIconRes(itemId: Int): Int = when (itemId) {
        R.id.action_open_with -> R.drawable.open_with_icon_control_normal_24dp
        R.id.action_cut -> R.drawable.cut_icon_control_normal_24dp
        R.id.action_copy -> R.drawable.copy_icon_control_normal_24dp
        R.id.action_delete -> R.drawable.delete_icon_control_normal_24dp
        R.id.action_rename -> R.drawable.edit_icon_control_normal_24dp
        R.id.action_extract -> R.drawable.extract_icon_control_normal_24dp
        R.id.action_archive -> R.drawable.archive_icon_control_normal_24dp
        R.id.action_share -> R.drawable.share_icon_control_normal_24dp
        R.id.action_copy_path -> R.drawable.link_icon_control_normal_24dp
        R.id.action_add_bookmark -> R.drawable.bookmark_add_icon_control_normal_24dp
        R.id.action_create_shortcut -> R.drawable.shortcut_icon_control_normal_24dp
        R.id.action_properties -> R.drawable.information_icon_control_normal_24dp
        R.id.action_move_to_other_pane -> moveToOtherPaneIconRes
        R.id.action_copy_to_other_pane -> copyToOtherPaneIconRes
        else -> 0
    }

    override fun getPopupText(view: View, position: Int): CharSequence {
        val file = getItem(position)
        return when (sortOptions.by) {
            FileSortOptions.By.NAME -> file.name.take(1).uppercase(Locale.getDefault())
            FileSortOptions.By.TYPE -> file.extension.uppercase(Locale.getDefault())
            FileSortOptions.By.SIZE -> file.attributes.fileSize.formatHumanReadable(view.context)
            FileSortOptions.By.LAST_MODIFIED ->
                file.attributes.lastModifiedTime().toInstant().formatShort(view.context)
        }
    }

    override val isAnimationEnabled: Boolean
        get() = Settings.FILE_LIST_ANIMATION.valueCompat

    companion object {
        private val PAYLOAD_STATE_CHANGED = Any()

        private const val DENSE_NAME_TEXT_SP = 14f
        private const val DENSE_DESCRIPTION_TEXT_SP = 12f
        private const val DENSE_LIST_ICON_MARGIN_START_DP = 4f
        private const val DENSE_GRID_MARGIN_DP = 8f

        private val CALLBACK = object : DiffUtil.ItemCallback<FileItem>() {
            override fun areItemsTheSame(oldItem: FileItem, newItem: FileItem): Boolean =
                oldItem.path == newItem.path

            override fun areContentsTheSame(oldItem: FileItem, newItem: FileItem): Boolean =
                oldItem == newItem
        }
    }

    class ViewHolder private constructor(
        root: View,
        val itemLayout: CheckableForegroundLinearLayout,
        val iconLayout: View,
        val iconImage: ImageView,
        val directoryThumbnailImage: ImageView?,
        val thumbnailOutlineView: View?,
        val thumbnailIconImage: ImageView?,
        val thumbnailImage: ImageView,
        val appIconBadgeImage: ImageView,
        val badgeImage: ImageView,
        val nameText: TextView,
        val descriptionText: TextView?,
        val menuButton: ImageButton,
        val thumbnailContainer: View?
    ) : RecyclerView.ViewHolder(root) {
        constructor(binding: FileItemListBinding) : this(
            binding.root,
            binding.itemLayout,
            binding.iconLayout,
            binding.iconImage,
            null,
            null,
            null,
            binding.thumbnailImage,
            binding.appIconBadgeImage,
            binding.badgeImage,
            binding.nameText,
            binding.descriptionText,
            binding.menuButton,
            null
        )

        constructor(binding: FileItemGridBinding) : this(
            binding.root,
            binding.itemLayout,
            binding.iconLayout,
            binding.iconImage,
            binding.directoryThumbnailImage,
            binding.thumbnailOutlineView,
            binding.thumbnailIconImage,
            binding.thumbnailImage,
            binding.appIconBadgeImage,
            binding.badgeImage,
            binding.nameText,
            null,
            binding.menuButton,
            binding.thumbnailContainer
        )

        lateinit var popupMenu: PopupMenu

        // The path currently bound to this holder, used to validate async directory item count
        // updates against view recycling.
        var boundPath: Path? = null
    }

    interface Listener {
        fun clearSelectedFiles()
        fun selectFile(file: FileItem, selected: Boolean)
        fun selectFiles(files: FileItemSet, selected: Boolean)
        fun openFile(file: FileItem)
        fun openFileWith(file: FileItem)
        fun cutFile(file: FileItem)
        fun copyFile(file: FileItem)
        fun confirmDeleteFile(file: FileItem)
        fun showRenameFileDialog(file: FileItem)
        fun extractFile(file: FileItem)
        fun showCreateArchiveDialog(file: FileItem)
        fun shareFile(file: FileItem)
        fun copyPath(file: FileItem)
        fun addBookmark(file: FileItem)
        fun createShortcut(file: FileItem)
        fun showPropertiesDialog(file: FileItem)
    }
}
