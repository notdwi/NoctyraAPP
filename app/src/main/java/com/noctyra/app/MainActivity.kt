package com.noctyra.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.noctyra.app.download.DownloadCenter
import com.noctyra.app.ui.navigation.NoctyraNavHost
import com.noctyra.app.ui.theme.NoctyraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            NoctyraTheme {
                NoctyraNavHost()
            }
        }
        window.decorView.post { DownloadCenter.start() }
    }
}
