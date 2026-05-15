package com.onyx.launcher

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.onyx.launcher.ui.App
import com.onyx.launcher.utils.PathManager

fun main() = application {
    PathManager.initialize()
    
    Window(
        onCloseRequest = ::exitApplication,
        title = "Onyx Launcher",
        state = rememberWindowState(width = 1200.dp, height = 800.dp)
    ) {
        App()
    }
}
