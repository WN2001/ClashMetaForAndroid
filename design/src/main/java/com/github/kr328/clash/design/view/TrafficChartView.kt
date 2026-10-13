package com.github.kr328.clash.design.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils
import com.github.kr328.clash.design.util.resolveThemedColor
import com.google.android.material.R as MaterialR

class TrafficChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val uploads = LongArray(30)
    private val downloads = LongArray(30)
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
        color = ColorUtils.setAlphaComponent(context.resolveThemedColor(MaterialR.attr.colorOutline), 45)
        strokeWidth = resources.displayMetrics.density
    }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun addSample(upload: Long, download: Long) {
        uploads[cursor] = upload.coerceAtLeast(0)
        downloads[cursor] = download.coerceAtLeast(0)
        cursor = (cursor + 1) % uploads.size
        count = (count + 1).coerceAtMost(uploads.size)
        rebuildPaths()
        invalidate()
    }

    fun clear() {
        uploads.fill(0)
        downloads.fill(0)
        cursor = 0
        count = 0
        rebuildPaths()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        rebuildPaths()
    }

    private fun rebuildPaths() {
        uploadPath.rewind()
        downloadPath.rewind()
        fillPath.rewind()
        if (width == 0 || height == 0 || count == 0) return
        var peak = 1024L
        for (i in 0 until count) {
            val index = (cursor - count + i + uploads.size) % uploads.size
            peak = maxOf(peak, uploads[index], downloads[index])
        }
        val inset = downloadPaint.strokeWidth
        val chartHeight = (height - inset * 2).coerceAtLeast(0f)
        val chartWidth = (width - inset * 2).coerceAtLeast(0f)
        val baseline = height - inset
        val startX = inset + chartWidth * (uploads.size - count) / (uploads.size - 1)
        var previousX = startX
        var previousUploadY = baseline
        var previousDownloadY = baseline
        for (i in 0 until count) {
            val index = (cursor - count + i + uploads.size) % uploads.size
            val x = inset + chartWidth * (uploads.size - count + i) / (uploads.size - 1)
            val uploadY = baseline - chartHeight * uploads[index].toFloat() / peak
            val downloadY = baseline - chartHeight * downloads[index].toFloat() / peak
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
        for (line in 1..3) {
            val y = height * line / 4f
            canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
        }
        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(uploadPath, uploadPaint)
        canvas.drawPath(downloadPath, downloadPaint)
    }
}
