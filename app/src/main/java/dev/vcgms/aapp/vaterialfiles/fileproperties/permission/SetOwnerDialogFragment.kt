package dev.vcgms.aapp.vaterialfiles.fileproperties.permission

import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import java8.nio.file.Path
import dev.vcgms.aapp.vaterialfiles.R
import dev.vcgms.aapp.vaterialfiles.file.FileItem
import dev.vcgms.aapp.vaterialfiles.filejob.FileJobService
import dev.vcgms.aapp.vaterialfiles.provider.common.PosixFileAttributes
import dev.vcgms.aapp.vaterialfiles.provider.common.PosixPrincipal
import dev.vcgms.aapp.vaterialfiles.provider.common.PosixUser
import dev.vcgms.aapp.vaterialfiles.provider.common.toByteString
import dev.vcgms.aapp.vaterialfiles.util.SelectionLiveData
import dev.vcgms.aapp.vaterialfiles.util.putArgs
import dev.vcgms.aapp.vaterialfiles.util.show
import dev.vcgms.aapp.vaterialfiles.util.viewModels

class SetOwnerDialogFragment : SetPrincipalDialogFragment() {
    override val viewModel: SetPrincipalViewModel by viewModels { { SetOwnerViewModel() } }

    @StringRes
    override val titleRes: Int = R.string.file_properties_permission_set_owner_title

    override fun createAdapter(selectionLiveData: SelectionLiveData<Int>): PrincipalListAdapter =
        UserListAdapter(selectionLiveData)

    override val PosixFileAttributes.principal: PosixPrincipal
        get() = owner()!!

    override fun setPrincipal(path: Path, principal: PrincipalItem, recursive: Boolean) {
        val owner = PosixUser(principal.id, principal.name?.toByteString())
        FileJobService.setOwner(path, owner, recursive, requireContext())
    }

    companion object {
        fun show(file: FileItem, fragment: Fragment) {
            SetOwnerDialogFragment().putArgs(Args(file)).show(fragment)
        }
    }
}
