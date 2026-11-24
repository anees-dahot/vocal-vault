package com.vocalvault.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vocalvault.app.ui.AppRoot
import com.vocalvault.app.ui.theme.VocalVaultTheme
import com.vocalvault.app.ui.theme.resolveDark

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = (application as VocalVaultApp).store
        setContent {
            val mode by store.themeMode.collectAsStateWithLifecycle()
            val accent by store.accent.collectAsStateWithLifecycle()
            val dark = resolveDark(mode)
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            VocalVaultTheme(dark = dark, accentIndex = accent) {
                AppRoot()
            }
        }
    }
}
