package dev.vcgms.aapp.vaterialfiles.storage

import android.os.Bundle
import android.view.View
import androidx.fragment.app.commit
import dev.vcgms.aapp.vaterialfiles.app.AppActivity
import dev.vcgms.aapp.vaterialfiles.util.args
import dev.vcgms.aapp.vaterialfiles.util.putArgs

class EditDocumentTreeDialogActivity : AppActivity() {
    private val args by args<EditDocumentTreeDialogFragment.Args>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Calls ensureSubDecor().
        findViewById<View>(android.R.id.content)
        if (savedInstanceState == null) {
            val fragment = EditDocumentTreeDialogFragment().putArgs(args)
            supportFragmentManager.commit {
                add(fragment, EditDocumentTreeDialogFragment::class.java.name)
            }
        }
    }
}
