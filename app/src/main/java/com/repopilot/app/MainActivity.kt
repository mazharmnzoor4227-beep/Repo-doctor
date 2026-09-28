package com.repopilot.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.repopilot.app.termux.TermuxResultReceiver
import com.repopilot.app.ui.RepoPilotApp
import com.repopilot.app.ui.RepoPilotTheme

class MainActivity : ComponentActivity() {
    private lateinit var controller: AppController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        controller = AppController(this)
        TermuxResultReceiver.listener = { result ->
            runOnUiThread { controller.onTermuxResult(result) }
        }
        setContent {
            RepoPilotTheme {
                RepoPilotApp(controller)
            }
        }
    }

    override fun onDestroy() {
        TermuxResultReceiver.listener = null
        if (::controller.isInitialized) controller.close()
        super.onDestroy()
    }
}
