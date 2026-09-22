package dev.vcgms.aapp.vaterialfiles.settings

import android.os.Bundle
import android.view.View
import androidx.fragment.app.add
import androidx.fragment.app.commit
import dev.vcgms.aapp.vaterialfiles.app.AppActivity

class BookmarkDirectoryListActivity : AppActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Calls ensureSubDecor().
        findViewById<View>(android.R.id.content)
        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                add<BookmarkDirectoryListFragment>(android.R.id.content)
            }
        }
    }
}
