package com.github.kr328.clash.design

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ContextThemeWrapper
import android.view.View
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.core.view.children
import com.github.kr328.clash.design.component.ProxyPageFactory
import com.github.kr328.clash.design.adapter.ProxyAdapter
import com.github.kr328.clash.design.adapter.ProxyPageAdapter
import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.core.util.trafficUploadBytes
import com.github.kr328.clash.core.util.trafficDownloadBytes
import com.github.kr328.clash.core.util.trafficTotal
import com.github.kr328.clash.design.component.ProxyView
import com.github.kr328.clash.design.component.ProxyViewConfig
import com.github.kr328.clash.design.component.ProxyViewState
import com.github.kr328.clash.design.databinding.DesignMainBinding
import com.github.kr328.clash.design.model.ProxyState
import com.github.kr328.clash.design.view.TrafficChartView
import com.github.kr328.clash.design.ui.Surface
import com.github.kr328.clash.design.util.resolveThemedColor
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "zh-rCN-w360dp-h900dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DashboardUiTest {
    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun context(dark: Boolean = false): Context = ContextThemeWrapper(
        RuntimeEnvironment.getApplication(), if (dark) R.style.AppThemeDark else R.style.AppThemeLight,
    )

    private fun layout(view: View, width: Int = 360, height: Int = 900) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    private fun snapshot(view: View, name: String) {
        if (System.getenv("UI_SCREENSHOTS") == null) return
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(view.context.resolveThemedColor(android.R.attr.windowBackground))
        view.draw(canvas)
        val file = File("build/ui-screenshots/$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun pack(upload: Long, download: Long): Long {
        fun word(value: Long) = if (value > 1024) (1L shl 30) or (value * 100 / 1024) else value
        return (word(upload) shl 32) or word(download)
    }

    @Test fun dashboardShowsRealTrafficAndClearsSpeedWhenStopped() = runBlocking {
        val design = MainDesign(context())
        val binding = DataBindingUtil.getBinding<DesignMainBinding>(design.root)!!
        design.setClashRunning(true)
        design.setTraffic(pack(512, 2048), pack(2048, 4096))
        binding.executePendingBindings()
        assertTrue(binding.uploadSpeed!!.startsWith("512"))
        assertTrue(binding.downloadUnit!!.contains("KiB"))
        assertTrue(binding.forwarded!!.contains("KiB"))
        design.setClashRunning(false)
        binding.executePendingBindings()
        assertTrue(binding.uploadSpeed!!.startsWith("0"))
        assertTrue(binding.downloadSpeed!!.startsWith("0"))
    }

    @Test fun dashboardLightAndDarkInflateAndRender() = runBlocking {
        for (dark in listOf(false, true)) {
            val design = MainDesign(context(dark))
            design.setClashRunning(true)
            design.setMode(com.github.kr328.clash.core.model.TunnelState.Mode.Rule)
            design.setProfileName("My subscription")
            for (i in 1..30) design.setTraffic(pack(i * 1000L, i * 12000L), pack(1048576, 4194304))
            val binding = DataBindingUtil.getBinding<DesignMainBinding>(design.root)!!
            binding.executePendingBindings()
            layout(design.root)
            assertTrue(binding.trafficChart.width > 100)
            assertTrue(binding.statusCard.height >= 100)
            assertTrue(binding.statusCard.performClick())
            assertEquals(MainDesign.Request.ToggleStatus, design.requests.receive())
            snapshot(design.root, if (dark) "dashboard-dark" else "dashboard-light")
        }
    }

    @Test @Config(qualifiers = "zh-rCN-w320dp-h1000dp-mdpi")
    fun narrowScreenWithLargeTextKeepsTrafficAndActionsVisible() = runBlocking {
        val app = RuntimeEnvironment.getApplication()
        val configuration = android.content.res.Configuration(app.resources.configuration)
        configuration.fontScale = 1.5f
        app.resources.updateConfiguration(configuration, app.resources.displayMetrics)
        val design = MainDesign(context())
        design.setProfileName("A very long subscription name with Unicode 香港 🇭🇰")
        val binding = DataBindingUtil.getBinding<DesignMainBinding>(design.root)!!
        binding.executePendingBindings()
        layout(design.root, 320, 1000)
        assertTrue(binding.trafficChart.width > 0)
        assertTrue(binding.statusCard.height > 0)
        snapshot(design.root, "dashboard-large-text")
    }

    @Test fun firstBindAndSelectionChangesDoNotDependOnStringIdentity() {
        val config = ProxyViewConfig(context(), 2)
        val parent = ProxyState("node")
        val state = ProxyViewState(config, Proxy("node", "Node", "VLESS", "VLESS", 80, false), parent, null)
        assertTrue(state.update())
        assertTrue(state.selected)
        parent.now = String(charArrayOf('n', 'o', 'd', 'e'))
        assertFalse(state.update())
        parent.now = "other"
        assertTrue(state.update())
        assertFalse(state.selected)
    }

    @Test fun proxyRowsRespectExactMeasurementsForEveryColumnCount() {
        for (columns in 1..3) {
            val config = ProxyViewConfig(context(), columns)
            val parent = ProxyState("node")
            val view = ProxyView(config.context, config)
            view.state = ProxyViewState(config,
                Proxy("node", "🇭🇰 Hong Kong • long node name", "VLESS / Reality", "VLESS", 80, false), parent, null)
            layout(view, 360 / columns, 96)
            assertEquals(96, view.measuredHeight)
            assertEquals(360 / columns, view.measuredWidth)
            snapshot(view, "proxy-$columns-columns")
        }
    }

    @Test fun chartHandlesOverflowAndZeroTrafficWithoutLayoutWork() {
        val chart = TrafficChartView(context())
        layout(chart, 300, 72)
        for (i in 1..100) chart.addSample(i.toLong(), Long.MAX_VALUE / i)
        chart.clear()
        assertFalse(chart.isLayoutRequested)
        assertEquals(300, chart.measuredWidth)
        val bitmap = Bitmap.createBitmap(300, 72, Bitmap.Config.ARGB_8888)
        chart.draw(Canvas(bitmap))
        bitmap.recycle()
    }

    @Test fun nativePackedTrafficUnitsAreDecodedBeforePlotting() {
        assertEquals(512L, pack(512, 2048).trafficUploadBytes())
        assertEquals(2048L, pack(512, 2048).trafficDownloadBytes())
        assertEquals("2.50 KiB", pack(512, 2048).trafficTotal())
        assertEquals(1048576L, ((2L shl 30) or 100L).trafficDownloadBytes())
        assertEquals(1073741824L, ((3L shl 30) or 100L).trafficDownloadBytes())
        assertTrue(pack(1025, 0).trafficUploadBytes() in 1024..1025)
        assertEquals("1.00 KiB", pack(512, 512).trafficTotal())
    }

    @Test fun latencyRefreshOnlyRebindsTheChangedNode() = runBlocking {
        val config = ProxyViewConfig(context(), 2)
        val adapter = ProxyAdapter(config) {}
        val pages = ProxyPageAdapter(Surface(), config, listOf(adapter)) {}
        val parent = ProxyState("a")
        val proxies = listOf(
            Proxy("a", "Node A", "VLESS", "VLESS", 80, false),
            Proxy("b", "Node B", "Trojan", "Trojan", 120, false),
        )
        pages.updateAdapter(0, proxies, true, parent, emptyMap())
        var changed = 0
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) {
                changed += itemCount
            }
        })
        pages.updateAdapter(0, proxies, true, parent, emptyMap())
        assertEquals(0, changed)
        pages.updateAdapter(0, listOf(proxies[0], proxies[1].copy(delay = 180)), true, parent, emptyMap())
        assertEquals(1, changed)
    }

    private fun exerciseColumnSwitches(width: Int, dark: Boolean) = runBlocking {
        val config = ProxyViewConfig(context(dark), 2)
        val nodes = List(40) { index -> Proxy("node-$index",
            listOf("🇭🇰 香港 · Hong Kong Premium", "🇯🇵 东京 · Tokyo Reality", "🇸🇬 Singapore 新加坡", "🇺🇸 Los Angeles 洛杉矶")[index % 4],
            "VLESS / Reality", "VLESS", if (index % 7 == 0) Short.MAX_VALUE.toInt() else 40 + index * 17, false) }
        val parent = ProxyState("node-0")
        val pages = ProxyPageAdapter(Surface(), config, List(2) { ProxyAdapter(config) {} }) {}
        for (page in 0..1) pages.updateAdapter(page, nodes, true, parent, emptyMap())
        val host = RecyclerView(config.context).apply {
            layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
            adapter = pages
            itemAnimator = null
        }
        layout(host, width, 700)
        fun grid() = (host.getChildAt(0).tag as ProxyPageFactory.Holder).recyclerView
        for (columns in listOf(2, 3, 1, 3, 2, 1)) {
            pages.updateColumns(columns)
            layout(host, width, 700)
            val list = grid()
            assertEquals(columns, (list.layoutManager as GridLayoutManager).spanCount)
            assertTrue(list.childCount >= columns)
            val rowTops = mutableMapOf<Int, Int>()
            list.children.forEach { child ->
                val position = list.getChildAdapterPosition(child)
                assertTrue(child.left >= list.paddingLeft)
                assertTrue(child.right <= list.width - list.paddingRight)
                assertTrue(kotlin.math.abs(child.width * columns - (list.width - list.paddingLeft - list.paddingRight)) <= columns)
                val row = position / columns
                val existing = rowTops.put(row, child.top)
                if (existing != null) assertEquals(existing, child.top)
                assertTrue(child.height > 0)
                assertTrue(child.contentDescription.toString().contains(nodes[position].title))
            }
            snapshot(host, "grid-${if (dark) "dark" else "light"}-$columns-$width")
        }
        grid().scrollToPosition(18)
        layout(host, width, 700)
        val anchor = (grid().layoutManager as GridLayoutManager).findFirstVisibleItemPosition()
        pages.updateColumns(3)
        layout(host, width, 700)
        val first = (grid().layoutManager as GridLayoutManager).findFirstVisibleItemPosition()
        assertTrue(first <= anchor && anchor - first < 3)
        host.scrollToPosition(1)
        layout(host, width, 700)
        assertEquals(3, (grid().layoutManager as GridLayoutManager).spanCount)
        assertEquals(40, grid().adapter!!.itemCount)
    }

    @Test fun liveGridSwitchesColumnsWhileScrolledAndRecyclesPages() {
        exerciseColumnSwitches(360, false)
        exerciseColumnSwitches(360, true)
    }

    @Test @Config(qualifiers = "zh-rCN-w320dp-h1000dp-mdpi")
    fun narrowGridWithLargeTextSurvivesRepeatedColumnSwitches() {
        val app = RuntimeEnvironment.getApplication()
        val configuration = android.content.res.Configuration(app.resources.configuration)
        configuration.fontScale = 1.5f
        app.resources.updateConfiguration(configuration, app.resources.displayMetrics)
        exerciseColumnSwitches(320, false)
    }
}
