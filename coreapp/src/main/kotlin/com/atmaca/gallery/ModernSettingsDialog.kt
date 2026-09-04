package com.atmaca.gallery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun ModernSettingsDialog(
    gridColumns: Int,
    mediaFilter: MediaFilter,
    mediaSort: MediaSort,
    sortDirection: SortDirection,
    onDismiss: () -> Unit,
    onGridColumns: (Int) -> Unit,
    onMediaFilter: (MediaFilter) -> Unit,
    onMediaSort: (MediaSort) -> Unit,
    onSortDirection: (SortDirection) -> Unit,
    onOpenTrash: () -> Unit,
    onOpenDuplicates: () -> Unit,
    onOpenTool: (AtmacaToolPage) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.96f)
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Galeri ayarları", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Kapat") }
                }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 10.dp)
                ) {
                    item { SettingsSectionTitle("Gösterilecek medya") }
                    items(MediaFilter.entries) { filter ->
                        val index = MediaFilter.entries.indexOf(filter)
                        SettingsChoiceRow(mediaFilterLabels()[index], filter == mediaFilter) { onMediaFilter(filter) }
                    }

                    item { SettingsSectionTitle("Sıralama ölçütü") }
                    items(MediaSort.entries) { sort ->
                        val index = MediaSort.entries.indexOf(sort)
                        SettingsChoiceRow(mediaSortLabels()[index], sort == mediaSort) { onMediaSort(sort) }
                    }

                    if (mediaSort != MediaSort.RANDOM) {
                        item { SettingsSectionTitle("Sıralama yönü") }
                        items(SortDirection.entries) { direction ->
                            val index = SortDirection.entries.indexOf(direction)
                            SettingsChoiceRow(sortDirectionLabels()[index], direction == sortDirection) { onSortDirection(direction) }
                        }
                    }

                    item { SettingsSectionTitle("Izgara") }
                    items((3..6).toList()) { columns ->
                        SettingsChoiceRow("$columns sütun", columns == gridColumns) { onGridColumns(columns) }
                    }

                    item {
                        Text(
                            "Filtre, sıralama ve ızgara seçimi cihazda saklanır.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }

                    item {
                        CompleteSettingsExtras(
                            onOpenTrash = onOpenTrash,
                            onOpenDuplicates = onOpenDuplicates,
                            onOpenTool = onOpenTool
                        )
                    }
                    item { Spacer(Modifier.width(1.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}
