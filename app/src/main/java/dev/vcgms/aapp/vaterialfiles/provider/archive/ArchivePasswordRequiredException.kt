package dev.vcgms.aapp.vaterialfiles.provider.archive

import android.content.Context
import java8.nio.file.Path
import dev.vcgms.aapp.vaterialfiles.fileaction.ArchivePasswordDialogActivity
import dev.vcgms.aapp.vaterialfiles.fileaction.ArchivePasswordDialogFragment
import dev.vcgms.aapp.vaterialfiles.provider.common.UserAction
import dev.vcgms.aapp.vaterialfiles.provider.common.UserActionRequiredException
import dev.vcgms.aapp.vaterialfiles.util.createIntent
import dev.vcgms.aapp.vaterialfiles.util.putArgs
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

class ArchivePasswordRequiredException(
    private val file: Path,
    reason: String?
) :
    UserActionRequiredException(file.toString(), null, reason) {

    override fun getUserAction(continuation: Continuation<Boolean>, context: Context): UserAction {
        return UserAction(
            ArchivePasswordDialogActivity::class.createIntent().putArgs(
                ArchivePasswordDialogFragment.Args(file) { continuation.resume(it) }
            ), ArchivePasswordDialogFragment.getTitle(context),
            ArchivePasswordDialogFragment.getMessage(file, context)
        )
    }
}
