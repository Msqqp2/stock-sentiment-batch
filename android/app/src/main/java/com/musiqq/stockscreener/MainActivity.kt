package com.musiqq.stockscreener

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.musiqq.stockscreener.ui.navigation.AppNavigation
import com.musiqq.stockscreener.ui.settings.SettingsViewModel
import com.musiqq.stockscreener.ui.theme.StockScreenerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val themeMode by settingsViewModel.themeMode.collectAsState()

            StockScreenerTheme(themeMode = themeMode) {
                AppNavigation()
            }
        }
    }
}
