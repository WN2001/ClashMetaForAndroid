package com.github.kr328.clash.design.view

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.core.content.ContextCompat
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.databinding.ComponentDashboardActionBinding
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.resolveThemedColor
import com.google.android.material.card.MaterialCardView
import com.google.android.material.R as MaterialR

class DashboardActionCard @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : MaterialCardView(context, attrs) {
    private val binding = ComponentDashboardActionBinding.inflate(context.layoutInflater, this, true)

    var text: CharSequence?
        get() = binding.title.text
        set(value) { binding.title.text = value }
    var subtext: CharSequence?
        get() = binding.subtitle.text
        set(value) { binding.subtitle.text = value }
    var icon: Drawable?
        get() = binding.icon.drawable
        set(value) { binding.icon.setImageDrawable(value) }

    init {
        radius = 24 * resources.displayMetrics.density
        cardElevation = 0f
        minimumHeight = (136 * resources.displayMetrics.density).toInt()
        setCardBackgroundColor(context.resolveThemedColor(MaterialR.attr.colorSurfaceContainerLow))
        strokeWidth = resources.displayMetrics.density.toInt().coerceAtLeast(1)
        setStrokeColor(ContextCompat.getColorStateList(context, R.color.dashboard_card_stroke))
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
