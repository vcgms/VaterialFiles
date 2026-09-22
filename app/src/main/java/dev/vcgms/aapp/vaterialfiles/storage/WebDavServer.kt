package dev.vcgms.aapp.vaterialfiles.storage

import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import java8.nio.file.Path
import kotlinx.parcelize.Parcelize
import dev.vcgms.aapp.vaterialfiles.R
import dev.vcgms.aapp.vaterialfiles.provider.webdav.client.Authentication
import dev.vcgms.aapp.vaterialfiles.provider.webdav.client.Authority
import dev.vcgms.aapp.vaterialfiles.provider.webdav.createWebDavRootPath
import dev.vcgms.aapp.vaterialfiles.util.createIntent
import dev.vcgms.aapp.vaterialfiles.util.putArgs
import kotlin.random.Random

@Parcelize
class WebDavServer(
    override val id: Long,
    override val customName: String?,
    val authority: Authority,
    val authentication: Authentication,
    val relativePath: String
) : Storage() {
    constructor(
        id: Long?,
        customName: String?,
        authority: Authority,
        authentication: Authentication,
        relativePath: String
    ) : this(id ?: Random.nextLong(), customName, authority, authentication, relativePath)

    override val iconRes: Int
        @DrawableRes
        get() = R.drawable.computer_icon_white_24dp

    override fun getDefaultName(context: Context): String =
        if (relativePath.isNotEmpty()) "$authority/$relativePath" else authority.toString()

    override val description: String
        get() = authority.toString()

    override val path: Path
        get() = authority.createWebDavRootPath().resolve(relativePath)

    override fun createEditIntent(): Intent =
        EditWebDavServerActivity::class.createIntent().putArgs(EditWebDavServerFragment.Args(this))
}
