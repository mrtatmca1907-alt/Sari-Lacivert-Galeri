package com.sarilacivert.galeri

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.sarilacivert.galeri.ui.AtmacaModernApp
import com.sarilacivert.galeri.ui.GalleryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            GalleryTheme {
                AtmacaModernApp()
            }
        }
    }
}
