package dev.vcgms.aapp.vaterialfiles.fileproperties.permission

import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import java8.nio.file.Path
import dev.vcgms.aapp.vaterialfiles.R
import dev.vcgms.aapp.vaterialfiles.file.FileItem
import dev.vcgms.aapp.vaterialfiles.filejob.FileJobService
import dev.vcgms.aapp.vaterialfiles.provider.common.PosixFileAttributes
import dev.vcgms.aapp.vaterialfiles.provider.common.PosixGroup
import dev.vcgms.aapp.vaterialfiles.provider.common.toByteString
import dev.vcgms.aapp.vaterialfiles.util.SelectionLiveData
import dev.vcgms.aapp.vaterialfiles.util.putArgs
import dev.vcgms.aapp.vaterialfiles.util.show
import dev.vcgms.aapp.vaterialfiles.util.viewModels

class SetGroupDialogFragment : SetPrincipalDialogFragment() {
    override val viewModel: SetPrincipalViewModel by viewModels { { SetGroupViewModel() } }

    @StringRes
    override val titleRes: Int = R.string.file_properties_permission_set_group_title

    override fun createAdapter(selectionLiveData: SelectionLiveData<Int>): PrincipalListAdapter =
        GroupListAdapter(selectionLiveData)

    override val PosixFileAttributes.principal
        get() = group()!!

    override fun setPrincipal(path: Path, principal: PrincipalItem, recursive: Boolean) {
        val group = PosixGroup(principal.id, principal.name?.toByteString())
        FileJobService.setGroup(path, group, recursive, requireContext())
    }

    companion object {
        fun show(file: FileItem, fragment: Fragment) {
            SetGroupDialogFragment().putArgs(Args(file)).show(fragment)
        }
    }
}
