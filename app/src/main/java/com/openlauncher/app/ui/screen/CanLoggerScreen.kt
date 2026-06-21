package com.openlauncher.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlauncher.app.data.CanLoggerState
import com.openlauncher.app.model.CanLogEntry
import com.openlauncher.app.ui.theme.LocalDayMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CanLoggerScreen(
    state: CanLoggerState,
    accent: Color,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit,
    onSave: () -> Unit,
    onMarker: (String) -> Unit,
    onAddAction: (String) -> Unit,
    onRemoveAction: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDayMode = LocalDayMode.current
    val screenBg = MaterialTheme.colorScheme.background
    val titleColor = if (isDayMode) Color(0xFF111111) else accent
    val dimColor = if (isDayMode) Color(0xFF666666) else Color(0xFF555555)
    val borderColor = if (isDayMode) Color(0xFFCCCCCC) else Color(0xFF1E1E1E)
    val panelBg = if (isDayMode) Color.White else Color.Black.copy(alpha = 0.24f)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(screenBg)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(34.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.BugReport, null, tint = titleColor, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                "CAN LOGGER",
                color = titleColor,
                fontSize = 14.sp,
                letterSpacing = 3.sp
            )
            Spacer(Modifier.width(12.dp))
            StatusPill(
                text = if (state.isRunning) "RUNNING" else "IDLE",
                color = if (state.isRunning) accent else dimColor,
                isDayMode = isDayMode
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${state.totalCount} EVENTS",
                color = dimColor,
                fontSize = 10.sp,
                letterSpacing = 1.sp
            )
        }

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(
                modifier = Modifier
                    .widthIn(min = 260.dp, max = 330.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(panelBg)
                    .border(1.dp, borderColor, RoundedCornerShape(4.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LoggerControls(
                    state = state,
                    accent = accent,
                    isDayMode = isDayMode,
                    onStart = onStart,
                    onStop = onStop,
                    onClear = onClear,
                    onSave = onSave,
                    onMarker = onMarker
                )

                CustomActionPanel(
                    state = state,
                    accent = accent,
                    isDayMode = isDayMode,
                    onAddAction = onAddAction,
                    onRemoveAction = onRemoveAction
                )
            }

            LogTable(
                entries = state.entries,
                accent = accent,
                isDayMode = isDayMode,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
    }
}

@Composable
private fun LoggerControls(
    state: CanLoggerState,
    accent: Color,
    isDayMode: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit,
    onSave: () -> Unit,
    onMarker: (String) -> Unit
) {
    val labelColor = if (isDayMode) Color(0xFF111111) else Color(0xFFDDDDDD)
    val dimColor = if (isDayMode) Color(0xFF777777) else Color(0xFF555555)
    var markerText by remember { mutableStateOf("") }

    Text("CAPTURE", color = dimColor, fontSize = 9.sp, letterSpacing = 2.sp)

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = if (state.isRunning) onStop else onStart,
            shape = RoundedCornerShape(4.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (state.isRunning) Color(0xFF471515) else accent
            ),
            modifier = Modifier.weight(1f).height(38.dp)
        ) {
            Icon(
                if (state.isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                null,
                tint = if (state.isRunning) Color(0xFFFFB3B3) else Color.Black,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(if (state.isRunning) "STOP" else "START", fontSize = 10.sp, letterSpacing = 1.sp)
        }

        IconButton(onClick = onSave, modifier = Modifier.size(38.dp)) {
            Icon(Icons.Default.Save, "Save CSV", tint = accent, modifier = Modifier.size(18.dp))
        }

        IconButton(onClick = onClear, modifier = Modifier.size(38.dp)) {
            Icon(Icons.Default.Delete, "Clear", tint = Color(0xFF993333), modifier = Modifier.size(18.dp))
        }
    }

    MetricLine("In memory", "${state.entries.size} latest")
    MetricLine("Dropped from view", "${state.droppedCount}")
    MetricLine("SocketCAN", state.socketCanInterfaces.ifEmpty { listOf("not visible") }.joinToString(", "))
    state.lastExportPath?.let { MetricLine("CSV", it) }
    state.currentLogPath?.let { MetricLine("Live file", it) }
    state.lastError?.let {
        Text(it, color = Color(0xFFD05A5A), fontSize = 10.sp, lineHeight = 13.sp)
    }

    OutlinedTextField(
        value = markerText,
        onValueChange = { markerText = it },
        placeholder = { Text("door open, headlights, sport mode", color = dimColor, fontSize = 10.sp) },
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(
            color = labelColor,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        ),
        colors = loggerTextFieldColors(accent, isDayMode),
        trailingIcon = {
            IconButton(onClick = {
                onMarker(markerText)
                markerText = ""
            }) {
                Icon(Icons.Default.Bookmark, "Add marker", tint = accent, modifier = Modifier.size(16.dp))
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun CustomActionPanel(
    state: CanLoggerState,
    accent: Color,
    isDayMode: Boolean,
    onAddAction: (String) -> Unit,
    onRemoveAction: (String) -> Unit
) {
    val labelColor = if (isDayMode) Color(0xFF111111) else Color(0xFFDDDDDD)
    val dimColor = if (isDayMode) Color(0xFF777777) else Color(0xFF555555)
    var actionText by remember { mutableStateOf("") }

    Text("BROADCAST ACTIONS", color = dimColor, fontSize = 9.sp, letterSpacing = 2.sp)
    OutlinedTextField(
        value = actionText,
        onValueChange = { actionText = it },
        placeholder = { Text("vendor.intent.ACTION_CAN", color = dimColor, fontSize = 10.sp) },
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(
            color = labelColor,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        ),
        colors = loggerTextFieldColors(accent, isDayMode),
        trailingIcon = {
            IconButton(onClick = {
                onAddAction(actionText)
                actionText = ""
            }) {
                Icon(Icons.Default.Add, "Watch action", tint = accent, modifier = Modifier.size(16.dp))
            }
        },
        modifier = Modifier.fillMaxWidth()
    )

    if (state.customActions.isNotEmpty()) {
        state.customActions.forEach { action ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    action,
                    color = labelColor,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { onRemoveAction(action) }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, null, tint = Color(0xFF993333), modifier = Modifier.size(14.dp))
                }
            }
        }
    }

    Text(
        "${state.watchedActions.size} actions watched",
        color = dimColor,
        fontSize = 10.sp
    )
}

@Composable
private fun LogTable(
    entries: List<CanLogEntry>,
    accent: Color,
    isDayMode: Boolean,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isDayMode) Color(0xFFCCCCCC) else Color(0xFF1E1E1E)
    val panelBg = if (isDayMode) Color.White else Color.Black.copy(alpha = 0.24f)
    val dimColor = if (isDayMode) Color(0xFF777777) else Color(0xFF555555)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(panelBg)
            .border(1.dp, borderColor, RoundedCornerShape(4.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("TIME", color = dimColor, fontSize = 9.sp, letterSpacing = 1.sp, modifier = Modifier.width(72.dp))
            Text("SOURCE", color = dimColor, fontSize = 9.sp, letterSpacing = 1.sp, modifier = Modifier.width(94.dp))
            Text("ID", color = dimColor, fontSize = 9.sp, letterSpacing = 1.sp, modifier = Modifier.width(74.dp))
            Text("DATA / RAW", color = dimColor, fontSize = 9.sp, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
        }

        if (entries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Start logging, then perform one vehicle action at a time and add markers.",
                    color = dimColor,
                    fontSize = 12.sp
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(entries, key = { it.sequence }) { entry ->
                    LogRow(entry = entry, accent = accent, isDayMode = isDayMode)
                }
            }
        }
    }
}

@Composable
private fun LogRow(entry: CanLogEntry, accent: Color, isDayMode: Boolean) {
    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }
    val labelColor = if (isDayMode) Color(0xFF111111) else Color(0xFFDDDDDD)
    val dimColor = if (isDayMode) Color(0xFF777777) else Color(0xFF555555)
    val rowBorder = if (isDayMode) Color(0xFFE4E4E4) else Color(0xFF141414)
    val isMarker = entry.source == "marker"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 0.5.dp, color = rowBorder)
            .background(if (isMarker) accent.copy(alpha = 0.10f) else Color.Transparent)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            timeFormatter.format(Date(entry.timestampMs)),
            color = dimColor,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(72.dp)
        )
        Text(
            entry.source,
            color = if (isMarker) accent else labelColor,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(94.dp)
        )
        Text(
            entry.canId ?: "",
            color = if (entry.canId != null) accent else dimColor,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(74.dp)
        )
        SelectionContainer(modifier = Modifier.weight(1f)) {
            Text(
                entry.dataHex ?: entry.raw,
                color = labelColor,
                fontSize = 9.sp,
                lineHeight = 12.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun MetricLine(label: String, value: String) {
    val isDayMode = LocalDayMode.current
    val labelColor = if (isDayMode) Color(0xFF777777) else Color(0xFF555555)
    val valueColor = if (isDayMode) Color(0xFF111111) else Color(0xFFDDDDDD)
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, color = labelColor, fontSize = 10.sp, modifier = Modifier.width(88.dp))
        SelectionContainer(modifier = Modifier.weight(1f)) {
            Text(
                value,
                color = valueColor,
                fontSize = 10.sp,
                lineHeight = 13.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun StatusPill(text: String, color: Color, isDayMode: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = if (isDayMode) 0.14f else 0.18f))
            .border(1.dp, color.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text, color = color, fontSize = 9.sp, letterSpacing = 1.sp)
    }
}

@Composable
private fun loggerTextFieldColors(accent: Color, isDayMode: Boolean) =
    OutlinedTextFieldDefaults.colors(
        focusedBorderColor = accent,
        unfocusedBorderColor = if (isDayMode) Color(0xFFCCCCCC) else Color(0xFF2A2A2A),
        focusedTextColor = if (isDayMode) Color(0xFF111111) else Color.White,
        unfocusedTextColor = if (isDayMode) Color(0xFF111111) else Color.White,
        cursorColor = accent
    )
