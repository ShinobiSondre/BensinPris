package com.bensinpris.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bensinpris.app.ui.BensinPrisTheme
import com.bensinpris.app.ui.HomeScreen
import com.bensinpris.app.ui.MainViewModel
import com.bensinpris.app.update.UpdateOverlay
import com.bensinpris.app.update.Updater

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // OpenStreetMap asks apps to name themselves; tiles are cached in the app's cache folder.
        org.osmdroid.config.Configuration.getInstance().apply {
            userAgentValue = "BensinPris/${BuildConfig.VERSION_NAME} (github.com/ShinobiSondre/BensinPris)"
            osmdroidBasePath = java.io.File(cacheDir, "osmdroid")
            osmdroidTileCache = java.io.File(cacheDir, "osmdroid/tiles")
        }
        Updater.start(this)
        setContent {
            BensinPrisTheme {
                HomeScreen(viewModel<MainViewModel>())
                UpdateOverlay()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Updater.onResume(this)
    }
}
