package com.github.kr328.clash.design.component

import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.model.ProxyState

class ProxyViewState(
    val config: ProxyViewConfig,
    val proxy: Proxy,
    private val parent: ProxyState,
    private val link: ProxyState?,
) {
    var title = if (proxy.isGroup) proxy.name else proxy.title
        private set
    var subtitle = if (proxy.isGroup) proxy.type else proxy.subtitle
        private set
    val delayText = when (proxy.delay) {
        0 -> "—"
        in 1 until Short.MAX_VALUE -> "${proxy.delay} ms"
        else -> config.context.getString(R.string.proxy_delay_timeout)
    }
    val delayColor = when (proxy.delay) {
        in 1..200 -> config.fastDelayColor
        in 201..600 -> config.slowDelayColor
        0 -> config.subtitleColor
        else -> config.errorColor
    }
    var selected = false
        private set

    fun update(): Boolean {
        val nextSelected = proxy.name == parent.now
        val nextSubtitle = if (proxy.isGroup && link != null) {
            "${proxy.type} · ${link.now.ifEmpty { "*" }}"
        } else if (proxy.isGroup) proxy.type else proxy.subtitle
        val changed = selected != nextSelected || subtitle != nextSubtitle
        selected = nextSelected
        subtitle = nextSubtitle
        return changed
    }
}
