package dev.vcgms.aapp.vaterialfiles.filelist

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.vcgms.aapp.vaterialfiles.R
import dev.vcgms.aapp.vaterialfiles.util.show

// Bottom choice dialog shown after dropping a dragged file onto the other pane: move here / copy
// here / cancel. The chosen index is delivered back through a fragment result.
class MoveCopyDialogFragment : AppCompatDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        MaterialAlertDialogBuilder(requireContext(), theme)
            .setItems(
                arrayOf(
                    getString(R.string.file_list_dual_pane_move_here),
                    getString(R.string.file_list_dual_pane_copy_here),
                    getString(android.R.string.cancel)
                )
            ) { _, which ->
                parentFragmentManager.setFragmentResult(
                    REQUEST_KEY,
                    Bundle().apply { putInt(KEY_RESULT, which) }
                )
            }
            .create()

    companion object {
        const val REQUEST_KEY = "file_list_move_copy_dialog"
        const val KEY_RESULT = "result"

        const val RESULT_MOVE = 0
        const val RESULT_COPY = 1
        const val RESULT_CANCEL = 2

        fun show(fragment: Fragment) {
            MoveCopyDialogFragment().show(fragment)
        }
    }
}
