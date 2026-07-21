package com.karthick.partysync

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.karthick.partysync.data.local.prefs.AppThemeMode
import com.karthick.partysync.data.local.prefs.SettingsRepository
import com.karthick.partysync.ui.RequestNotificationPermissionEffect
import com.karthick.partysync.ui.navigation.PartySyncNavHost
import com.karthick.partysync.ui.theme.PartySyncTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by settingsRepository.settings.collectAsState()
            val darkTheme = when (settings.themeMode) {
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            PartySyncTheme(darkTheme = darkTheme) {
                RequestNotificationPermissionEffect()
                PartySyncNavHost()
            }
        }
    }
}
