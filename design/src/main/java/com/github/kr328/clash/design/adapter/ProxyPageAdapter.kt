package com.github.kr328.clash.design.adapter

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.DiffUtil
import androidx.core.view.children
import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.component.ProxyPageFactory
import com.github.kr328.clash.design.component.ProxyViewConfig
import com.github.kr328.clash.design.component.ProxyViewState
import com.github.kr328.clash.design.component.ProxyView
import com.github.kr328.clash.design.model.ProxyPageState
import com.github.kr328.clash.design.model.ProxyState
import com.github.kr328.clash.design.ui.Surface
import com.github.kr328.clash.design.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ProxyPageAdapter(
    private val surface: Surface,
    private val config: ProxyViewConfig,
    private val adapters: List<ProxyAdapter>,
    private val stateChanged: (Int) -> Unit,
) : RecyclerView.Adapter<ProxyPageFactory.Holder>() {
    private val factory = ProxyPageFactory(config)
    private var parent: RecyclerView? = null

    val states = List(adapters.size) { ProxyPageState() }
    private val updateLocks = List(adapters.size) { Mutex() }

    fun updateColumns(columns: Int) {
        val next = columns.coerceIn(1, 3)
        if (config.proxyLine == next) return
        config.proxyLine = next
        parent?.children?.forEach { factory.updateColumns(factory.fromRoot(it).recyclerView) }
    }

    suspend fun updateAdapter(
        position: Int,
        proxies: List<Proxy>,
        selectable: Boolean,
        parent: ProxyState,
        links: Map<String, ProxyState>
    ) = updateLocks[position].withLock {
        val adapter = adapters[position]
        val previous = withContext(Dispatchers.Main) { adapter.states to adapter.selectable }
        val next = withContext(Dispatchers.Default) {
            proxies.map {
                val link = if (it.isGroup) links[it.name] else null

                ProxyViewState(config, it, parent, link)
            }
        }

        val diff = withContext(Dispatchers.Default) {
            DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                override fun getOldListSize() = previous.first.size
                override fun getNewListSize() = next.size
                override fun areItemsTheSame(old: Int, new: Int) =
                    previous.first[old].proxy.name == next[new].proxy.name
                override fun areContentsTheSame(old: Int, new: Int) =
                    previous.second == selectable && previous.first[old].proxy == next[new].proxy
            }, false)
        }

        withContext(Dispatchers.Main) {
            adapter.selectable = selectable
            adapter.states = next
            diff.dispatchUpdatesTo(adapter)
            requestRedrawVisible()
        }
    }

    fun requestRedrawVisible() {
        parent?.children?.forEach { page ->
            factory.fromRoot(page).recyclerView.children.forEach { view ->
                (view as? ProxyView)?.refreshState()
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProxyPageFactory.Holder {
        val holder = factory.newInstance()

        val toolbarHeight = config.context.getPixels(R.dimen.toolbar_height)
        val tabHeight = config.context.getPixels(R.dimen.tab_layout_height)

        holder.recyclerView.bindInsets(surface, toolbarHeight + tabHeight,
            bottom = (80 * config.density).toInt(), horizontal = (8 * config.density).toInt())
        holder.recyclerView.addScrolledToBottomObserver { view, bottom ->
            val position = view.position
            val state = states[position]

            if (state.bottom != bottom) {
                state.bottom = bottom

                stateChanged(position)
            }
        }

        return holder
    }

    override fun onBindViewHolder(holder: ProxyPageFactory.Holder, position: Int) {
        val adapter = adapters[position]

        states[position].bottom = false

        holder.recyclerView.apply {
            this.position = position
            this.swapAdapter(adapter, false)
        }
        factory.updateColumns(holder.recyclerView)
    }

    override fun getItemCount(): Int {
        return adapters.size
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        this.parent = recyclerView

        recyclerView.isFocusable = false
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        this.parent = null
    }


    private var RecyclerView.position: Int
        get() {
            return tag as? Int ?: -1
        }
        set(value) {
            tag = value
        }
}
