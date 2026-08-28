package com.atmaca.hub

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AtmacaApp(this) }
    }
}

data class AppEntry(val packageName: String, val label: String, val icon: android.graphics.drawable.Drawable?)

private fun loadApps(context: Context): List<AppEntry> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    @Suppress("DEPRECATION")
    return pm.queryIntentActivities(intent, 0).asSequence()
        .filter { it.activityInfo.packageName != context.packageName }
        .map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString(), it.loadIcon(pm)) }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
        .toList()
}

@Composable
fun AtmacaApp(context: Context) {
    val colors = darkColorScheme(primary = Color(0xFFFFD400), onPrimary = Color(0xFF071A3D), background = Color(0xFF071A3D), surface = Color(0xFF102A57), onBackground = Color.White, onSurface = Color.White)
    val apps = remember { loadApps(context) }
    val prefs = remember { context.getSharedPreferences("atmaca", Context.MODE_PRIVATE) }
    var selected by remember { mutableStateOf(prefs.getStringSet("selected", emptySet())?.toSet() ?: emptySet()) }
    var picker by remember { mutableStateOf(false) }
    MaterialTheme(colorScheme = colors) {
        if (picker) PickerScreen(apps, selected, { pkg -> selected = selected.toMutableSet().apply { if (!add(pkg)) remove(pkg) } }, {
            prefs.edit().putStringSet("selected", selected).apply(); picker = false
        }, { picker = false }) else HomeScreen(apps.filter { selected.contains(it.packageName) }, { picker = true }) { pkg ->
            context.packageManager.getLaunchIntentForPackage(pkg)?.let { context.startActivity(it) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(apps: List<AppEntry>, onEdit: () -> Unit, onLaunch: (String) -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("ATMACA") }) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            if (apps.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("Uygulama seçmek için Düzenle'ye dokun") }
            else LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.weight(1f), contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(apps, key = { it.packageName }) { app ->
                    Card(Modifier.fillMaxWidth().clickable { onLaunch(app.packageName) }) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            app.icon?.let { Image(BitmapPainter(it.toBitmap(96,96).asImageBitmap()), app.label, Modifier.size(56.dp)) }
                            Text(app.label, maxLines = 2, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
            Button(onClick = onEdit, modifier = Modifier.fillMaxWidth().padding(16.dp)) { Text("Düzenle") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerScreen(apps: List<AppEntry>, selected: Set<String>, onToggle: (String) -> Unit, onSave: () -> Unit, onCancel: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Uygulamaları seç") }, navigationIcon = { TextButton(onClick = onCancel) { Text("Geri") } }) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            LazyColumn(Modifier.weight(1f)) {
                items(apps, key = { it.packageName }) { app ->
                    Row(Modifier.fillMaxWidth().clickable { onToggle(app.packageName) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        app.icon?.let { Image(BitmapPainter(it.toBitmap(72,72).asImageBitmap()), app.label, Modifier.size(42.dp)) }
                        Spacer(Modifier.width(12.dp)); Text(app.label, Modifier.weight(1f)); Checkbox(selected.contains(app.packageName), { onToggle(app.packageName) })
                    }
                }
            }
            Button(onClick = onSave, modifier = Modifier.fillMaxWidth().padding(16.dp)) { Text("Kaydet (${selected.size})") }
        }
    }
}
