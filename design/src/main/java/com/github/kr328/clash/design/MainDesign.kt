package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.core.util.trafficTotal
import com.github.kr328.clash.core.util.trafficUpload
import com.github.kr328.clash.core.util.trafficDownload
import com.github.kr328.clash.core.util.trafficUploadBytes
import com.github.kr328.clash.core.util.trafficDownloadBytes
import com.github.kr328.clash.design.databinding.DesignAboutBinding
import com.github.kr328.clash.design.databinding.DesignMainBinding
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.resolveThemedColor
import com.github.kr328.clash.design.util.root
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainDesign(context: Context) : Design<MainDesign.Request>(context) {
    enum class Request {
        ToggleStatus,
        OpenProxy,
        OpenProfiles,
        OpenProviders,
        OpenLogs,
        OpenSettings,
        OpenHelp,
        OpenAbout,
    }

    private val binding = DesignMainBinding
        .inflate(context.layoutInflater, context.root, false)

    override val root: View
        get() = binding.root

    suspend fun setProfileName(name: String?) {
        withContext(Dispatchers.Main) {
            binding.profileName = name
        }
    }

    suspend fun setClashRunning(running: Boolean) {
        withContext(Dispatchers.Main) {
            if (binding.clashRunning != running) {
                binding.clashRunning = running
                binding.statusCard.setTone(if (running) binding.colorClashStarted else binding.colorClashStopped)
                if (!running) {
                    setSpeedLabels("0 B", "0 B")
                    binding.trafficChart.clear()
                }
            }
        }
    }

    suspend fun setForwarded(value: Long) {
        withContext(Dispatchers.Main) {
            val text = value.trafficTotal()
            if (binding.forwarded != text) binding.forwarded = text
        }
    }

    suspend fun setTraffic(now: Long, total: Long) {
        withContext(Dispatchers.Main) {
            setSpeedLabels(now.trafficUpload(), now.trafficDownload())
            val forwarded = total.trafficTotal()
            if (binding.forwarded != forwarded) binding.forwarded = forwarded
            binding.trafficChart.addSample(now.trafficUploadBytes(), now.trafficDownloadBytes())
        }
    }

    private fun setSpeedLabels(upload: String, download: String) {
        val uploadValue = upload.substringBeforeLast(' ')
        val downloadValue = download.substringBeforeLast(' ')
        val uploadUnit = context.getString(R.string.format_traffic_speed, upload.substringAfterLast(' '))
        val downloadUnit = context.getString(R.string.format_traffic_speed, download.substringAfterLast(' '))
        if (binding.uploadSpeed != uploadValue) binding.uploadSpeed = uploadValue
        if (binding.downloadSpeed != downloadValue) binding.downloadSpeed = downloadValue
        if (binding.uploadUnit != uploadUnit) binding.uploadUnit = uploadUnit
        if (binding.downloadUnit != downloadUnit) binding.downloadUnit = downloadUnit
    }

    suspend fun setMode(mode: TunnelState.Mode) {
        withContext(Dispatchers.Main) {
            binding.mode = when (mode) {
                TunnelState.Mode.Direct -> context.getString(R.string.direct_mode)
                TunnelState.Mode.Global -> context.getString(R.string.global_mode)
                TunnelState.Mode.Rule -> context.getString(R.string.rule_mode)
                else -> context.getString(R.string.rule_mode)
            }
        }
    }

    suspend fun setHasProviders(has: Boolean) {
        withContext(Dispatchers.Main) {
            binding.hasProviders = has
        }
    }

    suspend fun showAbout(versionName: String) {
        withContext(Dispatchers.Main) {
            val binding = DesignAboutBinding.inflate(context.layoutInflater).apply {
                this.versionName = versionName
            }

            MaterialAlertDialogBuilder(context)
                .setView(binding.root)
                .show()
        }
    }

    init {
        binding.self = this
        setSpeedLabels("0 B", "0 B")
        binding.forwarded = "0 B"
        binding.colorClashStarted = context.resolveThemedColor(com.google.android.material.R.attr.colorPrimaryContainer)
        binding.colorClashStopped = context.resolveThemedColor(com.google.android.material.R.attr.colorSurfaceContainerHigh)
        binding.statusCard.setTone(binding.colorClashStopped)
    }

    fun request(request: Request) {
        requests.trySend(request)
    }
}
