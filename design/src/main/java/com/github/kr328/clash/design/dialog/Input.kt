package com.github.kr328.clash.design.dialog

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doOnTextChanged
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.databinding.DialogTextFieldBinding
import com.github.kr328.clash.design.util.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

suspend fun Context.requestModelTextInput(
    initial: String,
    title: CharSequence,
    hint: CharSequence? = null,
    error: CharSequence? = null,
    validator: Validator = ValidatorAcceptAll,
): String {
    return this.requestModelTextInput(initial, title, null, hint, error, validator)!!
}

suspend fun Context.requestModelTextInput(
    initial: String?,
    title: CharSequence,
    reset: CharSequence?,
    hint: CharSequence? = null,
    error: CharSequence? = null,
    validator: Validator = ValidatorAcceptAll,
): String? {
    return suspendCancellableCoroutine {
        val binding = DialogTextFieldBinding
            .inflate(layoutInflater, this.root, false)

        val builder = MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(binding.root)
            .setCancelable(false)
            .setPositiveButton(R.string.ok) { _, _ ->
                val text = binding.textField.text?.toString() ?: ""

                val valid = try {
                    validator(text)
                } catch (e: Exception) {
                    false
                }

                if (valid)
                    it.resume(text)
                else
                    it.resume(initial)
            }
            .setNegativeButton(R.string.cancel) { _, _ -> }
            .setOnDismissListener { _ ->
                Log.w("CMFA_DIALOG", "Input dialog onDismiss, isCompleted=${it.isCompleted}")
                if (!it.isCompleted)
                    it.resume(initial)
            }

        if (reset != null) {
            builder.setNeutralButton(reset) { _, _ ->
                it.resume(null)
            }
        }

        val dialog = builder.create()

        dialog.setCanceledOnTouchOutside(false)

        // 拦截系统预测性返回手势/边缘误触的 cancel，防止弹窗被误关
        dialog.setOnCancelListener {
            Log.w("CMFA_DIALOG", "Input dialog onCancel (intercepted)")
        }

        it.invokeOnCancellation {
            Log.w("CMFA_DIALOG", "Input coroutine cancelled, dismissing dialog")
            dialog.dismiss()
        }

        dialog.setOnShowListener {
            if (hint != null)
                binding.textLayout.hint = hint

            binding.textField.apply {
                binding.textLayout.isErrorEnabled = error != null

                doOnTextChanged { text, _, _, _ ->
                    val valid = try {
                        validator(text?.toString() ?: "")
                    } catch (e: Exception) {
                        // validator（如 native Clash.veritySecretKeys）异常时不冒泡，仅判为无效
                        false
                    }
                    if (!valid) {
                        if (error != null)
                            binding.textLayout.error = error

                        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = false
                    } else {
                        if (error != null)
                            binding.textLayout.error = null

                        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = true
                    }
                }

                setText(initial)

                setSelection(0, initial?.length ?: 0)

                requestTextInput()
            }
        }

        Log.w("CMFA_DIALOG", "Input dialog showing, title=$title")
        dialog.show()
    }
}