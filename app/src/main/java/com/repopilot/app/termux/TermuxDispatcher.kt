package com.repopilot.app.termux

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.repopilot.app.core.AppConstants

object TermuxDispatcher {
    const val TERMUX_PACKAGE = "com.termux"
    const val ACTION = "com.termux.RUN_COMMAND"
    const val EXTRA_PATH = "com.termux.RUN_COMMAND_PATH"
    const val EXTRA_ARGS = "com.termux.RUN_COMMAND_ARGUMENTS"
    const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
    const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
    const val EXTRA_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT"

    fun isInstalled(context: Context): Boolean = runCatching { context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0); true }.getOrDefault(false)

    fun execute(context: Context, operation: String, args: List<String> = emptyList()): Result<Unit> = runCatching {
        require(operation.matches(Regex("[a-z-]+"))) { "Invalid operation" }
        val resultIntent = Intent(context, TermuxResultReceiver::class.java).setAction(TermuxResultReceiver.ACTION_RESULT)
        val pending = PendingIntent.getBroadcast(context, (System.nanoTime() and 0x7fffffff).toInt(), resultIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        val i = Intent(ACTION).apply {
            component = ComponentName(TERMUX_PACKAGE, "com.termux.app.RunCommandService")
            putExtra(EXTRA_PATH, AppConstants.BRIDGE_PATH)
            putExtra(EXTRA_ARGS, arrayOf(operation, *args.toTypedArray()))
            putExtra(EXTRA_WORKDIR, "/data/data/com.termux/files/home")
            putExtra(EXTRA_BACKGROUND, true)
            putExtra(EXTRA_PENDING_INTENT, pending)
        }
        context.startService(i)
    }
}
