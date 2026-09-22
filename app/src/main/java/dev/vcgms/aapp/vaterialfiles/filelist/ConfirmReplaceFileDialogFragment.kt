package dev.vcgms.aapp.vaterialfiles.filelist

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.parcelize.Parcelize
import dev.vcgms.aapp.vaterialfiles.R
import dev.vcgms.aapp.vaterialfiles.file.FileItem
import dev.vcgms.aapp.vaterialfiles.util.ParcelableArgs
import dev.vcgms.aapp.vaterialfiles.util.args
import dev.vcgms.aapp.vaterialfiles.util.putArgs
import dev.vcgms.aapp.vaterialfiles.util.show

class ConfirmReplaceFileDialogFragment : AppCompatDialogFragment() {
    private val args by args<Args>()

    private val listener: Listener
        get() = requireParentFragment() as Listener

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val file = args.file
        return MaterialAlertDialogBuilder(requireContext(), theme)
            .setMessage(getString(R.string.file_replace_message_format, file.name))
            .setPositiveButton(android.R.string.ok) { _, _ -> listener.replaceFile(file) }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }

    companion object {
        fun show(file: FileItem, fragment: Fragment) {
            ConfirmReplaceFileDialogFragment().putArgs(Args(file)).show(fragment)
        }
    }

    @Parcelize
    class Args(val file: FileItem) : ParcelableArgs

    interface Listener {
        fun replaceFile(file: FileItem)
    }
}
