package com.nidoham.premium

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nidoham.premium.ui.screen.MainScreen
import com.nidoham.premium.ui.theme.PremiumTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            PremiumTheme {
                MainScreen()
            }
        }
    }
}