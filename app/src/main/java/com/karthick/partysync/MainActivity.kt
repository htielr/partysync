package com.karthick.partysync

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.karthick.partysync.ui.RequestNotificationPermissionEffect
import com.karthick.partysync.ui.navigation.PartySyncNavHost
import com.karthick.partysync.ui.theme.PartySyncTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PartySyncTheme {
                RequestNotificationPermissionEffect()
                PartySyncNavHost()
            }
        }
    }
}
