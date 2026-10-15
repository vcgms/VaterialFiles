package dev.vcgms.aapp.vaterialfiles.ui

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.slider.Slider
import dev.vcgms.aapp.vaterialfiles.R

// A preference row with an inline Material slider, storing an integer in [min, max]. The stock
// androidx SeekBarPreference depends on a seekbar widget layout that the Material 3 preference
// theme does not provide, so this self-contained row replaces it.
class SliderPreference(
    context: Context,
    attrs: AttributeSet?,
) : Preference(context, attrs) {
    private var min = 0

    private var max = 100

    private var defaultValue = 0

    init {
        layoutResource = R.layout.preference_slider_material3
        context.obtainStyledAttributes(attrs, R.styleable.SliderPreference, 0, 0).apply {
            min = getInt(R.styleable.SliderPreference_min, min)
            max = getInt(R.styleable.SliderPreference_max, max)
            recycle()
        }
        defaultValue = (min + max) / 2
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val slider = holder.itemView.findViewById<Slider>(R.id.slider) ?: return
        slider.valueFrom = min.toFloat()
        slider.valueTo = max.toFloat()
        slider.stepSize = 1f
        slider.value = getPersistedInt(defaultValue).coerceIn(min, max).toFloat()
        slider.setLabelFormatter { value -> value.toInt().toString() }
        slider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                persistInt(value.toInt())
                notifyChanged()
            }
        }
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any = a.getInt(index, defaultValue)

    override fun onSetInitialValue(defaultValue: Any?) {
        this.defaultValue = (defaultValue as? Int)?.coerceIn(min, max) ?: this.defaultValue
        setDefaultValue(this.defaultValue)
        getPersistedInt(this.defaultValue)
    }
}
