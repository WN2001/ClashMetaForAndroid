package com.github.kr328.clash.design.view

import android.content.Context
import android.animation.ValueAnimator
import android.graphics.LinearGradient
import android.graphics.Shader
import android.view.animation.PathInterpolator
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils
import com.github.kr328.clash.design.util.resolveThemedColor
import com.github.kr328.clash.design.util.animationsEnabled
import com.google.android.material.R as MaterialR

class TrafficChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val uploads = LongArray(30)
    private val downloads = LongArray(30)
    private val previousUploads = FloatArray(30)
    private val previousDownloads = FloatArray(30)
    private var progress = 1f
    private var previousPeak = 1024f
    private var peak = 1024f
    private var animator: ValueAnimator? = null
    private var cursor = 0
    private var count = 0
    private val uploadPath = Path()
    private val downloadPath = Path()
    private val fillPath = Path()
    private val uploadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.resolveThemedColor(MaterialR.attr.colorTertiary)
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 2
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val downloadPaint = Paint(uploadPaint).apply {
        color = context.resolveThemedColor(MaterialR.attr.colorPrimary)
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ColorUtils.setAlphaComponent(downloadPaint.color, 22)
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ColorUtils.setAlphaComponent(context.resolveThemedColor(MaterialR.attr.colorOutline), 24)
        strokeWidth = resources.displayMetrics.density
    }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun addSample(upload: Long, download: Long) {
        animator?.cancel()
        for (i in uploads.indices) {
            previousUploads[i] += (target(uploads, i) - previousUploads[i]) * progress
            previousDownloads[i] += (target(downloads, i) - previousDownloads[i]) * progress
        }
        previousPeak += (peak - previousPeak) * progress
        uploads[cursor] = upload.coerceAtLeast(0)
        downloads[cursor] = download.coerceAtLeast(0)
        cursor = (cursor + 1) % uploads.size
        count = (count + 1).coerceAtMost(uploads.size)
        peak = 1024f
        for (i in uploads.indices) peak = maxOf(peak, target(uploads, i), target(downloads, i))
        if (isAttachedToWindow && context.animationsEnabled()) {
            progress = 0f
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 320
                interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
                addUpdateListener {
                    progress = it.animatedValue as Float
                    rebuildPaths()
                    invalidate()
                }
                start()
            }
        } else {
            progress = 1f
            rebuildPaths()
            invalidate()
        }
    }

    fun clear() {
        animator?.cancel()
        animator = null
        uploads.fill(0)
        downloads.fill(0)
        cursor = 0
        count = 0
        progress = 1f
        peak = 1024f
        previousPeak = peak
        previousUploads.fill(0f)
        previousDownloads.fill(0f)
        rebuildPaths()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        fillPaint.shader = LinearGradient(0f, 0f, 0f, h.toFloat().coerceAtLeast(1f),
            ColorUtils.setAlphaComponent(downloadPaint.color, 44), ColorUtils.setAlphaComponent(downloadPaint.color, 0), Shader.TileMode.CLAMP)
        rebuildPaths()
    }

    private fun target(values: LongArray, slot: Int): Float {
        if (slot < values.size - count) return 0f
        return values[(cursor - values.size + slot + values.size) % values.size].toFloat()
    }

    private fun rebuildPaths() {
        uploadPath.rewind()
        downloadPath.rewind()
        fillPath.rewind()
        if (width == 0 || height == 0 || count == 0) return
        val renderedPeak = previousPeak + (peak - previousPeak) * progress
        val inset = downloadPaint.strokeWidth
        val chartHeight = (height - inset * 2).coerceAtLeast(0f)
        val chartWidth = (width - inset * 2).coerceAtLeast(0f)
        val baseline = height - inset
        val startX = inset + chartWidth * (uploads.size - count) / (uploads.size - 1)
        var previousX = startX
        var previousUploadY = baseline
        var previousDownloadY = baseline
        for (i in 0 until count) {
            val slot = uploads.size - count + i
            val x = inset + chartWidth * (uploads.size - count + i) / (uploads.size - 1)
            val upload = previousUploads[slot] + (target(uploads, slot) - previousUploads[slot]) * progress
            val download = previousDownloads[slot] + (target(downloads, slot) - previousDownloads[slot]) * progress
            val uploadY = baseline - chartHeight * upload / renderedPeak
            val downloadY = baseline - chartHeight * download / renderedPeak
            if (i == 0) {
                uploadPath.moveTo(x, uploadY)
                downloadPath.moveTo(x, downloadY)
            } else {
                val midpoint = (previousX + x) / 2
                uploadPath.cubicTo(midpoint, previousUploadY, midpoint, uploadY, x, uploadY)
                downloadPath.cubicTo(midpoint, previousDownloadY, midpoint, downloadY, x, downloadY)
            }
            previousX = x
            previousUploadY = uploadY
            previousDownloadY = downloadY
        }
        fillPath.set(downloadPath)
        fillPath.lineTo(width - inset, baseline)
        fillPath.lineTo(startX, baseline)
        fillPath.close()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (line in 1..2) {
            val y = height * line / 3f
            canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
        }
        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(uploadPath, uploadPaint)
        canvas.drawPath(downloadPath, downloadPaint)
    }

    override fun onDetachedFromWindow() {
        animator?.end()
        animator = null
        super.onDetachedFromWindow()
    }
}
