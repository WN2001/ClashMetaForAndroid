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
 * overrideMode: 非 null 时表示从其他入口（如快速切换）打开，模式组当前值
 */
class ProxyMenu(
    private val context: Context,
    private val mode: TunnelState.Mode?,
    private val uiStore: UiStore,
    private val requests: Channel<ProxyDesign.Request>,
    private val updateConfig: () -> Unit,
) {
    fun show() {
        val dialog = AppBottomSheetDialog(context)

        val binding = DialogProxyMenuBinding
            .inflate(context.layoutInflater, dialog.window?.decorView as ViewGroup?, false)

        binding.master = this
        binding.self = dialog
        binding.mode = mode
        binding.line = uiStore.proxyLine
        binding.sort = uiStore.proxySort
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

    fun requestSort(sort: ProxySort, self: Dialog) {
        uiStore.proxySort = sort

        requests.trySend(ProxyDesign.Request.ReloadAll)

        self.dismiss()
    }

    fun requestMode(mode: TunnelState.Mode?, self: Dialog) {
        requests.trySend(ProxyDesign.Request.PatchMode(mode))

        self.dismiss()
    }
}
