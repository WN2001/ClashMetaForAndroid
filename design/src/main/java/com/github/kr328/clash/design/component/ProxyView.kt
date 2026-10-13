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
                duration = 180
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
        val titleLines = if (config.proxyLine == 1 || Build.VERSION.SDK_INT < 23) 1 else 2
        val textHeight = titlePaint.fontSpacing * titleLines + subtitlePaint.fontSpacing
        val desired = (config.layoutPadding * 2 + config.contentPadding * 2 + textHeight + 12 * config.density).toInt()
        setMeasuredDimension(
            resolveSize(resources.displayMetrics.widthPixels, widthMeasureSpec),
            resolveSize(desired, heightMeasureSpec),
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        cardBounds.set(config.layoutPadding, config.layoutPadding, w - config.layoutPadding, h - config.layoutPadding)
        clip.rewind()
        clip.addRoundRect(cardBounds, config.cardRadius, config.cardRadius, Path.Direction.CW)
        updateTextLayout()
    }

    private fun updateTextLayout() {
        val current = state ?: return
        titlePaint.textSize = config.textSize
        subtitlePaint.textSize = config.subtitleSize
        delayPaint.textSize = config.subtitleSize
        contentLeft = config.layoutPadding + config.contentPadding
        val contentRight = (width - contentLeft).coerceAtLeast(contentLeft)
        titleY = config.layoutPadding + config.contentPadding - titlePaint.ascent()
        subtitleY = height - config.layoutPadding - config.contentPadding - subtitlePaint.descent()
        val titleWidth = (contentRight - contentLeft - if (current.selected) 20 * config.density else 0f).coerceAtLeast(0f)
        title = TextUtils.ellipsize(current.title, titlePaint, titleWidth, TextUtils.TruncateAt.END).toString()
        titleLayout = if (Build.VERSION.SDK_INT >= 23 && config.proxyLine != 1) {
            StaticLayout.Builder.obtain(current.title, 0, current.title.length, titlePaint, titleWidth.toInt().coerceAtLeast(1))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setMaxLines(2)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setIncludePad(false)
                .build()
        } else null
        val badgePadding = 5 * config.density
        val maxBadgeWidth = ((contentRight - contentLeft) * 0.55f - badgePadding * 2).coerceAtLeast(0f)
        delayText = TextUtils.ellipsize(current.delayText, delayPaint, maxBadgeWidth, TextUtils.TruncateAt.END).toString()
        val delayWidth = delayPaint.measureText(delayText)
        badgeBounds.set(
            contentRight - delayWidth - badgePadding * 2,
            subtitleY + delayPaint.ascent() - 3 * config.density,
            contentRight,
            subtitleY + delayPaint.descent() + 3 * config.density,
        )
        val subtitleWidth = (badgeBounds.left - contentLeft - 6 * config.density).coerceAtLeast(0f)
        subtitle = TextUtils.ellipsize(current.subtitle, subtitlePaint, subtitleWidth, TextUtils.TruncateAt.END).toString()
    }

    override fun draw(canvas: Canvas) {
        if (state == null) return super.draw(canvas)
        val saved = canvas.save()
        canvas.clipPath(clip)
        fillPaint.color = ColorUtils.blendARGB(config.unselectedBackground, config.selectedBackground, selection)
        canvas.drawRoundRect(cardBounds, config.cardRadius, config.cardRadius, fillPaint)
        super.draw(canvas)
        canvas.restoreToCount(saved)
        strokePaint.color = if (isFocused) config.primaryColor else ColorUtils.blendARGB(config.outlineColor, config.primaryColor, selection)
        strokePaint.strokeWidth = config.density * if (isFocused) 2 else 1
        canvas.drawRoundRect(cardBounds, config.cardRadius, config.cardRadius, strokePaint)
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
        canvas.drawText(delayText, badgeBounds.left + 5 * config.density, subtitleY, delayPaint)
        if (current.selected) {
            canvas.drawText("✓", width - contentLeft - 16 * config.density, titleY, titlePaint)
        }
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        if (!isAttachedToWindow) return
        val scale = if (isPressed) 0.985f else 1f
        if (context.animationsEnabled()) {
            animate().scaleX(scale).scaleY(scale).setDuration(if (isPressed) 70 else 140).start()
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
