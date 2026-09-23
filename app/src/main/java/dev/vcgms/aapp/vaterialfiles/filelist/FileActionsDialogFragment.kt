package dev.vcgms.aapp.vaterialfiles.filelist

import android.app.Dialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Parcelable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.parcelize.Parcelize
import dev.vcgms.aapp.vaterialfiles.compat.getDrawableCompat
import dev.vcgms.aapp.vaterialfiles.util.ParcelableArgs
import dev.vcgms.aapp.vaterialfiles.util.args
import dev.vcgms.aapp.vaterialfiles.util.getColorByAttr
import dev.vcgms.aapp.vaterialfiles.util.getResourceIdByAttr
import dev.vcgms.aapp.vaterialfiles.util.putArgs
import dev.vcgms.aapp.vaterialfiles.util.show

// A centered dialog of file actions shown on long press when the per-item menu button is hidden
// (dual-pane mode and grid view). Actions are laid out in a two-column grid, each as an icon with
// a label; the selected action id is delivered back through a fragment result.
class FileActionsDialogFragment : AppCompatDialogFragment() {
    private val args by args<Args>()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val recyclerView = RecyclerView(context).apply {
            layoutManager = GridLayoutManager(context, COLUMN_COUNT)
            val padding = (GRID_PADDING_DP * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
            clipToPadding = false
            adapter = ActionsAdapter(args.actions) { action ->
                parentFragmentManager.setFragmentResult(
                    REQUEST_KEY,
                    Bundle().apply { putInt(KEY_ACTION_ID, action.actionId) }
                )
                dismiss()
            }
        }
        return MaterialAlertDialogBuilder(context, theme)
            .setView(recyclerView)
            .create()
    }

    private class ActionsAdapter(
        private val actions: List<FileAction>,
        private val onClick: (FileAction) -> Unit
    ) : RecyclerView.Adapter<ActionsAdapter.ViewHolder>() {
        class ViewHolder(
            cell: LinearLayout,
            val icon: ImageView,
            val label: TextView
        ) : RecyclerView.ViewHolder(cell)

        override fun getItemCount(): Int = actions.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val context = parent.context
            val density = context.resources.displayMetrics.density
            val iconTint = ColorStateList.valueOf(
                context.getColorByAttr(android.R.attr.colorControlNormal)
            )
            val textColor = context.getColorByAttr(android.R.attr.textColorPrimary)
            val icon = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    (ICON_SIZE_DP * density).toInt(),
                    (ICON_SIZE_DP * density).toInt()
                )
                imageTintList = iconTint
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            val label = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { marginStart = (LABEL_MARGIN_START_DP * density).toInt() }
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setTextSize(TypedValue.COMPLEX_UNIT_SP, LABEL_TEXT_SP)
                setTextColor(textColor)
            }
            val cell = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                isFocusable = true
                val paddingHorizontal = (CELL_PADDING_HORIZONTAL_DP * density).toInt()
                val paddingVertical = (CELL_PADDING_VERTICAL_DP * density).toInt()
                setPadding(
                    paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical
                )
                background = context.getDrawableCompat(
                    context.getResourceIdByAttr(android.R.attr.selectableItemBackground)
                )
                addView(icon)
                addView(label)
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            return ViewHolder(cell, icon, label)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val action = actions[position]
            holder.icon.visibility = if (action.iconRes != 0) View.VISIBLE else View.GONE
            if (action.iconRes != 0) {
                holder.icon.setImageResource(action.iconRes)
            }
            holder.label.text = action.title
            holder.itemView.setOnClickListener { onClick(action) }
        }

        private companion object {
            const val ICON_SIZE_DP = 24f
            const val LABEL_TEXT_SP = 13f
            const val LABEL_MARGIN_START_DP = 12f
            const val CELL_PADDING_HORIZONTAL_DP = 16f
            const val CELL_PADDING_VERTICAL_DP = 12f
        }
    }

    companion object {
        const val REQUEST_KEY = "file_list_file_actions_dialog"
        const val KEY_ACTION_ID = "action_id"

        private const val COLUMN_COUNT = 2
        private const val GRID_PADDING_DP = 8f

        fun show(fragment: Fragment, actions: List<FileAction>) {
            FileActionsDialogFragment().putArgs(Args(actions)).show(fragment)
        }
    }

    @Parcelize
    class Args(val actions: List<FileAction>) : ParcelableArgs

    @Parcelize
    data class FileAction(val actionId: Int, val title: String, val iconRes: Int) : Parcelable
}
