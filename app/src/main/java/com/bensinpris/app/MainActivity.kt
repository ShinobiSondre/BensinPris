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
