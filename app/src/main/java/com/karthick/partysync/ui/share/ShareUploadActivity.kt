package com.karthick.partysync.ui.share

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.karthick.partysync.data.local.prefs.AppThemeMode
import com.karthick.partysync.data.local.prefs.SettingsRepository
import com.karthick.partysync.ui.RequestNotificationPermissionEffect
import com.karthick.partysync.ui.theme.PartySyncTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Entry point launched from other apps' share sheets (see AndroidManifest.xml intent filters). */
@AndroidEntryPoint
class ShareUploadActivity : ComponentActivity() {

    private val viewModel: ShareUploadViewModel by viewModels()

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uris = sharedUrisFromIntent(intent)
        if (uris.isEmpty()) {
            finish()
            return
        }
        viewModel.setSharedUris(uris)

        setContent {
            val settings by settingsRepository.settings.collectAsState()
            val darkTheme = when (settings.themeMode) {
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            PartySyncTheme(darkTheme = darkTheme) {
                RequestNotificationPermissionEffect()
                ShareUploadScreen(onCancel = { finish() }, onDone = { finish() })
            }
        }
    }

    private fun sharedUrisFromIntent(intent: Intent): List<Uri> = when (intent.action) {
        Intent.ACTION_SEND -> listOfNotNull(extraStreamUri(intent))
        Intent.ACTION_SEND_MULTIPLE -> extraStreamUriList(intent)
        else -> emptyList()
    }

    private fun extraStreamUri(intent: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }

    private fun extraStreamUriList(intent: Intent): List<Uri> = (
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
        }
        ).orEmpty()
}
