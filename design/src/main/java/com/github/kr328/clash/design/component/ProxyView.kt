package com.github.kr328.clash.design.component

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.text.Layout
import android.text.StaticLayout
import android.os.Build
import android.view.animation.PathInterpolator
import android.view.View
import androidx.core.graphics.ColorUtils
import com.github.kr328.clash.common.compat.getDrawableCompat
import com.github.kr328.clash.design.util.animationsEnabled

class ProxyView(
    context: Context,
    private val config: ProxyViewConfig,
) : View(context) {
    private val cardBounds = RectF()
    private val badgeBounds = RectF()
    private val clip = Path()
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
    private val subtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val delayPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
    private var title = ""
    private var titleLayout: StaticLayout? = null
    private var subtitle = ""
    private var delayText = ""
    private var titleY = 0f
    private var subtitleY = 0f
    private var delayY = 0f
    private var contentLeft = 0f
    private var selection = 0f
    private var selectionAnimator: ValueAnimator? = null

    var state: ProxyViewState? = null
        set(value) {
            selectionAnimator?.cancel()
            field = value
            value?.update()
            selection = if (value?.selected == true) 1f else 0f
            updateTextLayout()
            requestLayout()
            invalidate()
        }

    constructor(context: Context) : this(context, ProxyViewConfig(context, 2))

    init {
        background = context.getDrawableCompat(config.clickableBackground)
    }

    fun refreshState(animate: Boolean = true) {
        val current = state ?: return
        if (!current.update()) return
        updateTextLayout()
        contentDescription = "${current.title}, ${current.subtitle}, ${current.delayText}"
        isSelected = current.selected
        val target = if (current.selected) 1f else 0f
        selectionAnimator?.cancel()
        if (animate && isAttachedToWindow && context.animationsEnabled() && selection != target) {
            selectionAnimator = ValueAnimator.ofFloat(selection, target).apply {
                duration = 260
                interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
                addUpdateListener {
                    selection = it.animatedValue as Float
                    postInvalidateOnAnimation()
                }
                start()
            }
        } else {
            selection = target
            invalidate()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        titlePaint.textSize = config.textSize
        subtitlePaint.textSize = config.subtitleSize
        delayPaint.textSize = config.subtitleSize
        val titleLines = if (config.proxyLine == 1 || Build.VERSION.SDK_INT < 23) 1 else 2
        val measuredWidth = resolveSize(resources.displayMetrics.widthPixels, widthMeasureSpec)
        val availableWidth = (measuredWidth - (config.layoutPadding + config.contentPadding) * 2).toInt().coerceAtLeast(1)
        val measuredTitle = createTitleLayout(availableWidth)
        val titleHeight = maxOf(titlePaint.fontSpacing * titleLines, measuredTitle?.height?.toFloat() ?: 0f)
        val textHeight = titleHeight + subtitlePaint.fontSpacing +
            if (config.proxyLine == 1) 0f else delayPaint.fontSpacing + 8 * config.density
        val desired = (config.layoutPadding * 2 + config.contentPadding * 2 + textHeight + 14 * config.density).toInt()
        setMeasuredDimension(
            measuredWidth,
            resolveSize(desired, heightMeasureSpec),
        )
    }

    fun refreshLayout() {
        animate().cancel()
        scaleX = 1f
        scaleY = 1f
        requestLayout()
        invalidate()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val w = width
        val h = height
        cardBounds.set(config.layoutPadding, config.layoutPadding, w - config.layoutPadding, h - config.layoutPadding)
        clip.rewind()
        val r = minOf(config.cardRadius, cardBounds.width() / 2, cardBounds.height() / 2)
        val k = r * 0.12f
        clip.moveTo(cardBounds.left + r, cardBounds.top)
        clip.lineTo(cardBounds.right - r, cardBounds.top)
        clip.cubicTo(cardBounds.right - k, cardBounds.top, cardBounds.right, cardBounds.top + k, cardBounds.right, cardBounds.top + r)
        clip.lineTo(cardBounds.right, cardBounds.bottom - r)
        clip.cubicTo(cardBounds.right, cardBounds.bottom - k, cardBounds.right - k, cardBounds.bottom, cardBounds.right - r, cardBounds.bottom)
        clip.lineTo(cardBounds.left + r, cardBounds.bottom)
        clip.cubicTo(cardBounds.left + k, cardBounds.bottom, cardBounds.left, cardBounds.bottom - k, cardBounds.left, cardBounds.bottom - r)
        clip.lineTo(cardBounds.left, cardBounds.top + r)
        clip.cubicTo(cardBounds.left, cardBounds.top + k, cardBounds.left + k, cardBounds.top, cardBounds.left + r, cardBounds.top)
        clip.close()
        updateTextLayout()
    }

    private fun createTitleLayout(availableWidth: Int): StaticLayout? {
        val text = state?.title ?: return null
        if (Build.VERSION.SDK_INT < 23 || config.proxyLine == 1) return null
        return StaticLayout.Builder.obtain(text, 0, text.length, titlePaint, availableWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setMaxLines(2)
            .setEllipsize(TextUtils.TruncateAt.END).setIncludePad(true).build()
    }

    private fun updateTextLayout() {
        val current = state ?: return
        titlePaint.textSize = config.textSize
        subtitlePaint.textSize = config.subtitleSize
        delayPaint.textSize = config.subtitleSize
        contentLeft = config.layoutPadding + config.contentPadding
        val contentRight = (width - contentLeft).coerceAtLeast(contentLeft)
        titleY = config.layoutPadding + config.contentPadding - titlePaint.ascent()
        delayY = height - config.layoutPadding - config.contentPadding - delayPaint.descent()
        subtitleY = if (config.proxyLine == 1) delayY else delayY - delayPaint.fontSpacing - 8 * config.density
        val titleWidth = (contentRight - contentLeft).coerceAtLeast(0f)
        title = TextUtils.ellipsize(current.title, titlePaint, titleWidth, TextUtils.TruncateAt.END).toString()
        titleLayout = createTitleLayout(titleWidth.toInt().coerceAtLeast(1))
        val badgePadding = (if (config.proxyLine == 3) 4 else 5) * config.density
        val maxBadgeWidth = ((contentRight - contentLeft - 18 * config.density) *
            if (config.proxyLine == 1) 0.55f else 1f).minus(badgePadding * 2).coerceAtLeast(0f)
        val labelWidth = delayPaint.measureText(current.delayText)
        if (labelWidth > maxBadgeWidth && labelWidth > 0f) {
            delayPaint.textSize = (config.subtitleSize * maxBadgeWidth / labelWidth).coerceAtLeast(9 * config.density)
        }
        delayText = TextUtils.ellipsize(current.delayText, delayPaint, maxBadgeWidth, TextUtils.TruncateAt.END).toString()
        val delayWidth = delayPaint.measureText(delayText)
        val badgeLeft = if (config.proxyLine == 1) contentRight - 22 * config.density - delayWidth - badgePadding * 2 else contentLeft
        badgeBounds.set(badgeLeft, delayY + delayPaint.ascent() - 3 * config.density,
            badgeLeft + delayWidth + badgePadding * 2, delayY + delayPaint.descent() + 3 * config.density)
        val subtitleWidth = (if (config.proxyLine == 1) badgeBounds.left - contentLeft - 6 * config.density
            else contentRight - contentLeft).coerceAtLeast(0f)
        subtitle = TextUtils.ellipsize(current.subtitle, subtitlePaint, subtitleWidth, TextUtils.TruncateAt.END).toString()
    }

    override fun draw(canvas: Canvas) {
        if (state == null) return super.draw(canvas)
        val saved = canvas.save()
        canvas.clipPath(clip)
        fillPaint.color = ColorUtils.blendARGB(config.unselectedBackground, config.selectedBackground, selection)
        canvas.drawPath(clip, fillPaint)
        super.draw(canvas)
        canvas.restoreToCount(saved)
        strokePaint.color = if (isFocused) config.primaryColor else ColorUtils.blendARGB(
            ColorUtils.setAlphaComponent(config.outlineColor, 65), config.primaryColor, selection)
        strokePaint.strokeWidth = config.density * if (isFocused) 2 else 1
        canvas.drawPath(clip, strokePaint)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val current = state ?: return
        val control = ColorUtils.blendARGB(config.unselectedControl, config.selectedControl, selection)
        titlePaint.color = control
        subtitlePaint.color = ColorUtils.blendARGB(config.subtitleColor, config.selectedControl, selection)
        delayPaint.color = if (current.selected) config.selectedControl else current.delayColor
        val cachedTitle = titleLayout
        if (cachedTitle != null) {
            val save = canvas.save()
            canvas.translate(contentLeft, config.layoutPadding + config.contentPadding)
            cachedTitle.draw(canvas)
            canvas.restoreToCount(save)
        } else {
            canvas.drawText(title, contentLeft, titleY, titlePaint)
        }
        canvas.drawText(subtitle, contentLeft, subtitleY, subtitlePaint)
        fillPaint.color = ColorUtils.setAlphaComponent(delayPaint.color, 18)
        canvas.drawRoundRect(badgeBounds, 6 * config.density, 6 * config.density, fillPaint)
        canvas.drawText(delayText, badgeBounds.left + (if (config.proxyLine == 3) 4 else 5) * config.density, delayY, delayPaint)
        if (selection > 0f) {
            delayPaint.color = ColorUtils.setAlphaComponent(config.primaryColor, (selection * 255).toInt())
            canvas.drawText("✓", width - contentLeft - 14 * config.density, delayY, delayPaint)
        }
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        if (!isAttachedToWindow) return
        val scale = if (isPressed) 0.97f else 1f
        if (context.animationsEnabled()) {
            animate().scaleX(scale).scaleY(scale).setDuration(if (isPressed) 100 else 240)
                .setInterpolator(PathInterpolator(0.2f, 0f, 0f, 1f)).start()
        } else {
            scaleX = scale
            scaleY = scale
        }
        invalidate()
    }

    override fun onDetachedFromWindow() {
        selectionAnimator?.cancel()
        selectionAnimator = null
        selection = if (state?.selected == true) 1f else 0f
        animate().cancel()
        scaleX = 1f
        scaleY = 1f
        super.onDetachedFromWindow()
    }
}
