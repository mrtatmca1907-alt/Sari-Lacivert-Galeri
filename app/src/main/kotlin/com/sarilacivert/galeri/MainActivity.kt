package com.sarilacivert.galeri

import android.content.ComponentCallbacks2
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.sarilacivert.galeri.data.BitmapLoader
import com.sarilacivert.galeri.ui.GalleryApp
import com.sarilacivert.galeri.ui.GalleryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            GalleryTheme {
                GalleryApp()
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            BitmapLoader.clearGlobalMemory()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        BitmapLoader.clearGlobalMemory()
    }
}
