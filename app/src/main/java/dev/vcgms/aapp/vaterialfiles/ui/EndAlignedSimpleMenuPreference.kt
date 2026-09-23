package dev.vcgms.aapp.vaterialfiles.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.preference.PreferenceViewHolder
import rikka.preference.SimpleMenuPreference

// A [SimpleMenuPreference] whose drop-down popup is anchored to the trailing (end) value view so
// the options menu opens on the right, matching Material 3 settings. Falls back to the library's
// default anchor if the private anchor field is unavailable.
class EndAlignedSimpleMenuPreference : SimpleMenuPreference {
    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
        super(context, attrs, defStyleAttr)

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        runCatching {
            val anchor = holder.itemView.findViewById<View>(android.R.id.summary)
            if (anchor != null) {
                val field = SimpleMenuPreference::class.java.getDeclaredField("mAnchor")
                field.isAccessible = true
                field.set(this, anchor)
            }
        }
    }
}
