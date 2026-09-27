package com.repopilot.app.termux

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

data class TermuxResult(val stdout: String, val stderr: String, val exitCode: Int, val error: String? = null)

class TermuxResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val b = intent.getBundleExtra("com.termux.RUN_COMMAND_RESULT") ?: intent.extras
        val stdout = b?.getString("stdout").orEmpty()
        val stderr = b?.getString("stderr").orEmpty()
        val exit = when (val v = b?.get("exitCode")) { is Int -> v; is Long -> v.toInt(); else -> if (stderr.isBlank()) 0 else 1 }
        val err = b?.getString("errmsg")
        listener?.invoke(TermuxResult(stdout, stderr, exit, err))
    }
    companion object {
        const val ACTION_RESULT = "com.repopilot.app.TERMUX_RESULT"
        @Volatile var listener: ((TermuxResult) -> Unit)? = null
    }
}
