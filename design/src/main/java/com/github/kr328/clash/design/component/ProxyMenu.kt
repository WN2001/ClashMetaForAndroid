package com.github.kr328.clash.design.component

import android.app.Dialog
import android.content.Context
import android.view.ViewGroup
import com.github.kr328.clash.core.model.ProxySort
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.design.ProxyDesign
import com.github.kr328.clash.design.databinding.DialogProxyMenuBinding
import com.github.kr328.clash.design.dialog.AppBottomSheetDialog
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.design.util.layoutInflater
import kotlinx.coroutines.channels.Channel

/**
 * 代理页"更多"菜单：Material BottomSheet 实现（原 PopupMenu 弃用）。
 *
 * mode 索引: 0=不修改 1=直连 2=全局 3=规则
 * sort 索引: 0=默认 1=名称 2=延迟
 *
 * overrideMode: 非 null 时表示从其他入口（如快速切换）打开，模式组当前值
 */
class ProxyMenu(
    private val context: Context,
    mode: TunnelState.Mode?,
    private val uiStore: UiStore,
    private val requests: Channel<ProxyDesign.Request>,
    private val updateConfig: () -> Unit,
) {
    private val modeIndex = when (mode) {
        null -> 0
        TunnelState.Mode.Direct -> 1
        TunnelState.Mode.Global -> 2
        TunnelState.Mode.Rule -> 3
        else -> 0
    }

    private val sortIndex = when (uiStore.proxySort) {
        ProxySort.Default -> 0
        ProxySort.Title -> 1
        ProxySort.Delay -> 2
        else -> 0
    }

    fun show() {
        val dialog = AppBottomSheetDialog(context)

        val binding = DialogProxyMenuBinding
            .inflate(context.layoutInflater, dialog.window?.decorView as ViewGroup?, false)

        binding.master = this
        binding.self = dialog
        binding.modeIndex = modeIndex
        binding.sortIndex = sortIndex
        binding.line = uiStore.proxyLine
        binding.exclude = uiStore.proxyExcludeNotSelectable

        dialog.setContentView(binding.root)
        dialog.show()
    }

    fun requestFilter(self: Dialog) {
        uiStore.proxyExcludeNotSelectable = !uiStore.proxyExcludeNotSelectable

        requests.trySend(ProxyDesign.Request.ReLaunch)

        self.dismiss()
    }

    fun requestLine(line: Int, self: Dialog) {
        uiStore.proxyLine = line

        updateConfig()

        requests.trySend(ProxyDesign.Request.ReloadAll)

        self.dismiss()
    }

    fun requestSort(sort: Int, self: Dialog) {
        uiStore.proxySort = when (sort) {
            0 -> ProxySort.Default
            1 -> ProxySort.Title
            else -> ProxySort.Delay
        }

        requests.trySend(ProxyDesign.Request.ReloadAll)

        self.dismiss()
    }

    fun requestMode(mode: Int, self: Dialog) {
        val m: TunnelState.Mode? = when (mode) {
            1 -> TunnelState.Mode.Direct
            2 -> TunnelState.Mode.Global
            3 -> TunnelState.Mode.Rule
            else -> null
        }

        requests.trySend(ProxyDesign.Request.PatchMode(m))

        self.dismiss()
    }
}
