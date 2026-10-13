package com.github.kr328.clash.design.component

import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.design.view.VerticalScrollableHost

class ProxyPageFactory(private val config: ProxyViewConfig) {
    class Holder(
        val recyclerView: RecyclerView,
        val root: View,
    ) : RecyclerView.ViewHolder(root)

    private val childrenPool = RecyclerView.RecycledViewPool()

    fun newInstance(): Holder {
        val root = VerticalScrollableHost(config.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val recyclerView = RecyclerView(config.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        root.addView(recyclerView)

        recyclerView.apply {
            layoutManager = GridLayoutManager(config.context, config.proxyLine.coerceIn(1, 3))

            setRecycledViewPool(childrenPool)
            setHasFixedSize(true)
            itemAnimator = null
            clipToPadding = false
            setPadding((8 * config.density).toInt(), 0, (8 * config.density).toInt(), 0)
        }

        return Holder(recyclerView, root).apply {
            root.tag = this
        }
    }

    fun fromRoot(root: View): Holder {
        return root.tag!! as Holder
    }

    fun updateColumns(recyclerView: RecyclerView) {
        val layout = recyclerView.layoutManager as GridLayoutManager
        val anchor = layout.findFirstVisibleItemPosition()
        val offset = layout.findViewByPosition(anchor)?.top?.minus(recyclerView.paddingTop) ?: 0
        layout.spanCount = config.proxyLine
        layout.spanSizeLookup.invalidateSpanIndexCache()
        layout.spanSizeLookup.invalidateSpanGroupIndexCache()
        recyclerView.children.forEach { (it as? ProxyView)?.refreshLayout() }
        if (anchor >= 0) layout.scrollToPositionWithOffset(anchor, offset)
        recyclerView.requestLayout()
    }
}
