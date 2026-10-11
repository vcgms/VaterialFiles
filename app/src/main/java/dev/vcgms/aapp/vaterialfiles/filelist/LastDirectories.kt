package dev.vcgms.aapp.vaterialfiles.filelist

import androidx.core.content.edit
import java.net.URI
import java8.nio.file.Path
import java8.nio.file.Paths
import dev.vcgms.aapp.vaterialfiles.app.defaultSharedPreferences
import dev.vcgms.aapp.vaterialfiles.settings.Settings
import dev.vcgms.aapp.vaterialfiles.storage.createOrLog
import dev.vcgms.aapp.vaterialfiles.util.valueCompat

// Remembers the last browsed directory of each view mode so it can be restored on the next
// launch. Recording is unconditional (it is cheap and keeps history ready); the "reopen last
// folder" switch only gates restoring.
object LastDirectories {
    // Column view needs no separate storage per column: the trail (and thus every column) is
    // derived from the deepest column's path, so restoring that path rebuilds all columns.
    enum class Mode { LIST, GRID, COLUMN, DUAL_LEFT, DUAL_RIGHT }

    private fun key(mode: Mode) = "last_directory_${mode.name}"

    fun record(mode: Mode, path: Path) {
        defaultSharedPreferences.edit {
            putString(key(mode), path.toUri().toString())
        }
    }

    fun restore(mode: Mode): Path? {
        if (!Settings.FILE_LIST_RESTORE_LAST_DIRECTORY.valueCompat) {
            return null
        }
        val uriString = defaultSharedPreferences.getString(key(mode), null) ?: return null
        val uri = URI::class.createOrLog(uriString) ?: return null
        return runCatching { Paths.get(uri) }.getOrNull()
    }
}
