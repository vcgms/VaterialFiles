package dev.vcgms.aapp.vaterialfiles.storage

import android.os.Bundle
import android.view.View
import androidx.fragment.app.commit
import dev.vcgms.aapp.vaterialfiles.app.AppActivity
import dev.vcgms.aapp.vaterialfiles.util.args
import dev.vcgms.aapp.vaterialfiles.util.putArgs

class EditDeviceStorageDialogActivity : AppActivity() {
    private val args by args<EditDeviceStorageDialogFragment.Args>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Calls ensureSubDecor().
        findViewById<View>(android.R.id.content)
        if (savedInstanceState == null) {
            val fragment = EditDeviceStorageDialogFragment().putArgs(args)
            supportFragmentManager.commit {
                add(fragment, EditDeviceStorageDialogFragment::class.java.name)
            }
        }
    }
}
