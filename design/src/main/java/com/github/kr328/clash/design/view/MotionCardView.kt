package com.github.kr328.clash.design.view

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.animation.PathInterpolator
import androidx.core.graphics.ColorUtils
import com.github.kr328.clash.design.util.animationsEnabled
import com.github.kr328.clash.design.util.resolveThemedColor
import com.google.android.material.card.MaterialCardView
import com.google.android.material.shape.CornerTreatment
import com.google.android.material.shape.ShapePath

open class MotionCardView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    MaterialCardView(context, attrs) {
    private var toneAnimator: ValueAnimator? = null
    private var entered = false
    private val easing = PathInterpolator(0.2f, 0f, 0f, 1f)

    init {
        shapeAppearanceModel = shapeAppearanceModel.toBuilder().setAllCorners(SmoothCorner()).build()
        cardElevation = 0f
        strokeWidth = 0
        setStrokeColor(context.resolveThemedColor(com.google.android.material.R.attr.colorPrimary))
    }

    fun setTone(color: Int) {
        val from = cardBackgroundColor.defaultColor
        toneAnimator?.cancel()
        if (from == color) return
        if (!isAttachedToWindow || !context.animationsEnabled()) {
            setCardBackgroundColor(color)
            return
        }
        toneAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 280
            interpolator = easing
            addUpdateListener { setCardBackgroundColor(ColorUtils.blendARGB(from, color, it.animatedValue as Float)) }
            start()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (entered || !context.animationsEnabled()) return
        entered = true
        alpha = 0f
        translationY = 12 * resources.displayMetrics.density
        animate().alpha(1f).translationY(0f).setStartDelay((tag as? String)?.toLongOrNull() ?: 0)
            .setDuration(320).setInterpolator(easing).start()
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        if (!isAttachedToWindow) return
        strokeWidth = if (isFocused && !isInTouchMode) (2 * resources.displayMetrics.density).toInt() else 0
        val scale = if (isPressed) 0.97f else 1f
        if (context.animationsEnabled()) {
            animate().scaleX(scale).scaleY(scale).setStartDelay(0).setDuration(if (isPressed) 100 else 240)
                .setInterpolator(easing).start()
        } else {
            scaleX = 1f
            scaleY = 1f
        }
    }

    override fun onDetachedFromWindow() {
        toneAnimator?.end()
        toneAnimator = null
        animate().cancel()
        alpha = 1f
        translationY = 0f
        scaleX = 1f
        scaleY = 1f
        super.onDetachedFromWindow()
    }
}

private class SmoothCorner : CornerTreatment() {
    override fun getCornerPath(shapePath: ShapePath, angle: Float, interpolation: Float, radius: Float) {
        val size = radius * interpolation
        shapePath.reset(0f, size, 180f, 180f - angle)
        shapePath.cubicToPoint(0f, size * 0.12f, size * 0.12f, 0f, size, 0f)
    }
}
