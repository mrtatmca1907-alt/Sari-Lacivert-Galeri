package com.atmaca.gallery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF89B9FF),
                    secondary = Color(0xFFB9C8E4),
                    background = Color(0xFF101318),
                    surface = Color(0xFF181C23)
                )
            ) {
                Surface(Modifier.fillMaxSize()) {
                    AtmacaGalleryApp()
                }
            }
        }
    }
}
