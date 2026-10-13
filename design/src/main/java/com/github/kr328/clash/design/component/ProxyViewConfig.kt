package com.github.kr328.clash.design.component

import android.content.Context
import androidx.core.graphics.ColorUtils
import androidx.core.content.ContextCompat
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.util.getPixels
import com.github.kr328.clash.design.util.resolveThemedColor
import com.github.kr328.clash.design.util.resolveThemedResourceId
import com.google.android.material.R as MaterialR

class ProxyViewConfig(val context: Context, var proxyLine: Int) {
    val clickableBackground = context.resolveThemedResourceId(android.R.attr.selectableItemBackground)
    val selectedControl = context.resolveThemedColor(MaterialR.attr.colorOnPrimaryContainer)
    val selectedBackground = context.resolveThemedColor(MaterialR.attr.colorPrimaryContainer)
    val unselectedControl = context.resolveThemedColor(MaterialR.attr.colorOnSurface)
    val unselectedBackground = context.resolveThemedColor(MaterialR.attr.colorSurfaceContainerLow)
    val subtitleColor = context.resolveThemedColor(MaterialR.attr.colorOnSurfaceVariant)
    val outlineColor = context.resolveThemedColor(MaterialR.attr.colorOutlineVariant)
    val primaryColor = context.resolveThemedColor(MaterialR.attr.colorPrimary)
    private val darkSurface = ColorUtils.calculateLuminance(unselectedBackground) < 0.5
    val fastDelayColor = ContextCompat.getColor(context, if (darkSurface) R.color.proxy_delay_fast_dark else R.color.proxy_delay_fast)
    val slowDelayColor = ContextCompat.getColor(context, if (darkSurface) R.color.proxy_delay_slow_dark else R.color.proxy_delay_slow)
    val errorColor = context.resolveThemedColor(MaterialR.attr.colorError)
    val layoutPadding = context.getPixels(R.dimen.proxy_layout_padding).toFloat()
    private val normalPadding = context.getPixels(R.dimen.proxy_content_padding).toFloat()
    private val compactPadding = context.getPixels(R.dimen.proxy_content_padding_grid3).toFloat()
    val contentPadding get() = if (proxyLine == 3) compactPadding else normalPadding
    private val normalTextSize = context.getPixels(R.dimen.proxy_text_size).toFloat()
    private val compactTextSize = context.getPixels(R.dimen.proxy_text_size_grid3).toFloat()
    val textSize get() = if (proxyLine == 3) compactTextSize else normalTextSize
    val subtitleSize = context.getPixels(R.dimen.proxy_subtitle_size).toFloat()
    val cardRadius = context.getPixels(R.dimen.proxy_card_radius).toFloat()
    val density = context.resources.displayMetrics.density
}
