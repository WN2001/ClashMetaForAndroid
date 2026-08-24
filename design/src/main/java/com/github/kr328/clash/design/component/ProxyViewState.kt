package com.github.kr328.clash.design.component

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.design.model.ProxyState
import kotlin.math.absoluteValue
import kotlin.math.max

class ProxyViewState(
    val config: ProxyViewConfig,
    val proxy: Proxy,
    private val parent: ProxyState,
    private val link: ProxyState?
) {
    val paint = Paint()
    val rect = Rect()
    val path = Path()

    var title: String = ""
    var subtitle: String = ""
    var delayText: String = ""
    var background: Int = config.unselectedBackground
    var controls: Int = config.unselectedControl

    // 延迟数字按快慢分色（仅未选中态；选中态保持 onPrimary 白以保证强色背景对比度）
    val delayColor: Int
        get() = when {
            selected -> config.selectedControl
            delay <= 0 -> config.unselectedControl
            delay < 100 -> Color.parseColor("#43A047")  // 绿 快
            delay < 300 -> Color.parseColor("#F9A825")  // 黄 一般
            else -> Color.parseColor("#E53935")         // 红 慢
        }

    private var delay: Int = 0
    private var selected: Boolean = false
    private var parentNow: String = ""
    private var linkNow: String? = null

    private var lastFrameTime = System.currentTimeMillis()

    fun update(snap: Boolean): Boolean {
        val frameTime = System.currentTimeMillis()
        var invalidate = false

        if (proxy.isGroup) {
            title = proxy.name

            if (link == null) {
                subtitle = proxy.type
            } else {
                if (linkNow !== link.now) {
                    linkNow = link.now

                    subtitle = "%s(%s)".format(
                        proxy.type,
                        link.now.ifEmpty { "*" }
                    )
                }
            }
        } else {
            title = proxy.title
            subtitle = proxy.subtitle
        }

        if (delay != proxy.delay) {
            delay = proxy.delay
            delayText = if (proxy.delay in 0..Short.MAX_VALUE) proxy.delay.toString() else ""
        }

        if (parentNow !== parent.now) {
            parentNow = parent.now
            selected = proxy.name == parent.now
        }

        controls = if (selected) config.selectedControl else config.unselectedControl

        if (snap) {
            background = if (selected) config.selectedBackground else config.unselectedBackground
        } else {
            val target = if (selected) config.selectedBackground else config.unselectedBackground

            if (background != target) {
                val sa = Color.alpha(background)
                val sr = Color.red(background)
                val sg = Color.green(background)
                val sb = Color.blue(background)

                val ta = Color.alpha(target)
                val tr = Color.red(target)
                val tg = Color.green(target)
                val tb = Color.blue(target)

                val da = ta - sa
                val dr = tr - sr
                val dg = tg - sg
                val db = tb - sb

                val max = max(
                    da.absoluteValue,
                    max(
                        dr.absoluteValue,
                        max(
                            dg.absoluteValue,
                            db.absoluteValue
                        )
                    )
                )

                val frameOffset = frameTime - lastFrameTime

                val colorOffset = (frameOffset / max.toFloat().coerceAtLeast(0.001f))
                    .coerceIn(0.0f, 1.0f)

                background = if (colorOffset > 0.999f) {
                    target
                } else {
                    Color.argb(
                        (sa + da * colorOffset).toInt(),
                        (sr + dr * colorOffset).toInt(),
                        (sg + dg * colorOffset).toInt(),
                        (sb + db * colorOffset).toInt()
                    )
                }

                invalidate = true
            }
        }

        lastFrameTime = frameTime

        return invalidate
    }
}