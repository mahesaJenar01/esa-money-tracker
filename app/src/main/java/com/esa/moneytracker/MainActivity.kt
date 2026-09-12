package com.esa.moneytracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import com.esa.moneytracker.ui.navigation.AppNavigation
import com.esa.moneytracker.ui.theme.MoneyTrackerTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // The balance header runs under the status bar, so the app draws edge to edge.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            MoneyTrackerTheme {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    AppNavigation()
                }
            }
        }
    }

    /**
     * Coming back to the app is the moment to check the recurring bills.
     *
     * The process survives being backgrounded for days, so `Application.onCreate`
     * alone would leave a bill unwritten until the app was killed and started
     * again. Nothing here can double-charge: a plan's watermark is what decides
     * whether a due moment has already been written.
     */
    override fun onStart() {
        super.onStart()
        (application as MoneyTrackerApp).catchUpSubscriptions()
    }
}
