package dev.vcgms.aapp.vaterialfiles.settings

import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import androidx.core.view.isVisible
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceViewHolder
import dev.vcgms.aapp.vaterialfiles.databinding.PreferenceCollapsibleCategoryBinding

// A PreferenceCategory rendered as a Material 3 style card header that collapses its children
// when tapped. Collapsed by default; the expanded state survives configuration changes. Children
// visibility is managed by the collapse state, so external visibility constraints must be
// re-applied from onExpandedChanged after an expand.
class CollapsiblePreferenceCategory : PreferenceCategory {
    var onExpandedChanged: ((Boolean) -> Unit)? = null

    var isExpanded: Boolean = true
        set(value) {
            if (field == value) {
                return
            }
            field = value
            updateChildrenVisibility()
            notifyChanged()
            onExpandedChanged?.invoke(value)
        }

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
        super(context, attrs, defStyleAttr)

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes)

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        val binding = PreferenceCollapsibleCategoryBinding.bind(holder.itemView)
        binding.categoryChevron.isVisible = preferenceCount > 0
        // The chevron points right while collapsed and down while expanded.
        binding.categoryChevron.rotation = if (isExpanded) 90f else 0f
        holder.itemView.setOnClickListener {
            isExpanded = !isExpanded
        }
    }

    private fun updateChildrenVisibility() {
        for (index in 0 until preferenceCount) {
            getPreference(index).isVisible = isExpanded
        }
    }

    override fun onSaveInstanceState(): Parcelable {
        val state = Bundle()
        state.putBoolean(STATE_KEY_EXPANDED, isExpanded)
        super.onSaveInstanceState()?.let { state.putParcelable(SUPER_STATE_KEY, it) }
        return state
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is Bundle) {
            val superState = state.getParcelable<Parcelable>(SUPER_STATE_KEY)
            super.onRestoreInstanceState(superState)
            isExpanded = state.getBoolean(STATE_KEY_EXPANDED, true)
        } else {
            super.onRestoreInstanceState(state)
        }
    }

    companion object {
        private const val STATE_KEY_EXPANDED = "isExpanded"

        private const val SUPER_STATE_KEY = "superState"
    }
}
