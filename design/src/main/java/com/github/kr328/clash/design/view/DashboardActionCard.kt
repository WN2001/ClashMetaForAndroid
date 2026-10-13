package com.github.kr328.clash.design.view

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.databinding.ComponentDashboardActionBinding
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.resolveThemedColor
import com.google.android.material.R as MaterialR

class DashboardActionCard @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : MotionCardView(context, attrs) {
    private val binding = ComponentDashboardActionBinding.inflate(context.layoutInflater, this, true)

    var text: CharSequence?
        get() = binding.title.text
        set(value) { binding.title.text = value }
    var subtext: CharSequence?
        get() = binding.subtitle.text
        set(value) {
            binding.subtitle.text = value
            binding.subtitle.visibility = if (value.isNullOrEmpty()) View.GONE else View.VISIBLE
        }
    var icon: Drawable?
        get() = binding.icon.drawable
        set(value) { binding.icon.setImageDrawable(value) }

    init {
        radius = 24 * resources.displayMetrics.density
        cardElevation = 0f
        minimumHeight = (112 * resources.displayMetrics.density).toInt()
        setCardBackgroundColor(context.resolveThemedColor(MaterialR.attr.colorSurfaceContainer))
        isClickable = true
        isFocusable = true
        context.obtainStyledAttributes(attrs, R.styleable.LargeActionCard).apply {
            try {
                text = getText(R.styleable.LargeActionCard_text)
                subtext = getText(R.styleable.LargeActionCard_subtext)
                icon = getDrawable(R.styleable.LargeActionCard_icon)
            } finally {
                recycle()
            }
        }
    }
}
