package com.github.kr328.clash.design.view

import android.animation.AnimatorInflater
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import androidx.annotation.AttrRes
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.databinding.ComponentLargeActionLabelBinding
import com.github.kr328.clash.design.util.*
import com.google.android.material.card.MaterialCardView

class LargeActionCard @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0
) : MaterialCardView(context, attributeSet, defStyleAttr) {
    private val binding = ComponentLargeActionLabelBinding
        .inflate(context.layoutInflater, this, true)

    var text: CharSequence?
        get() = binding.textView.text
        set(value) {
            binding.textView.text = value
        }

    var subtext: CharSequence?
        get() = binding.subtextView.text
        set(value) {
            binding.subtextView.text = value
        }

    var icon: Drawable?
        get() = binding.iconView.drawable
        set(value) {
            binding.iconView.setImageDrawable(value)
            binding.iconView.visibility = if (value == null) View.INVISIBLE else View.VISIBLE
        }

    init {
        context.resolveClickableAttrs(attributeSet, defStyleAttr) {
            isFocusable = focusable(true)
            isClickable = clickable(true)
            foreground = foreground() ?: context.selectableItemBackground
        }

        context.theme.obtainStyledAttributes(
            attributeSet,
            R.styleable.LargeActionCard,
            defStyleAttr,
            0
        ).apply {
            try {
                val useContainer = getBoolean(R.styleable.LargeActionCard_iconContainer, true)
                // 亚克力模式：半透明磨砂卡面 + 细描边（色斑背景透出，模拟 Win11 Acrylic）
                val acrylic = getBoolean(R.styleable.LargeActionCard_acrylicCard, false)
                icon = getDrawable(R.styleable.LargeActionCard_icon)
                text = getString(R.styleable.LargeActionCard_text)
                subtext = getString(R.styleable.LargeActionCard_subtext)

                // hero 状态卡（iconContainer=false）：背景已是强色，不套容器，图标用 onPrimary
                if (!useContainer) {
                    binding.iconView.background = null
                    binding.iconView.imageTintList = ColorStateList.valueOf(
                        context.resolveThemedColor(com.google.android.material.R.attr.colorOnPrimary)
                    )
                }

                if (acrylic) {
                    setCardBackgroundColor(context.resolveThemedColor(R.attr.colorAcrylicCard))
                    strokeColor = context.resolveThemedColor(
                        com.google.android.material.R.attr.colorOutlineVariant
                    )
                    strokeWidth = context.getPixels(R.dimen.acrylic_stroke_width)
                }
            } finally {
                recycle()
            }
        }

        minimumHeight = context.getPixels(R.dimen.large_action_card_min_height)
        radius = context.getPixels(R.dimen.large_action_card_radius).toFloat()
        elevation = context.getPixels(R.dimen.large_action_card_elevation).toFloat()
        setCardBackgroundColor(context.resolveThemedColor(com.google.android.material.R.attr.colorSurfaceContainerHigh))
        // 按下抬升反馈：按下时轻微上浮，松开回 0
        stateListAnimator = AnimatorInflater.loadStateListAnimator(context, R.animator.card_pressed_state_anim)
    }
}