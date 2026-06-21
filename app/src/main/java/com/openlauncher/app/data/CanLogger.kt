package com.openlauncher.app.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.openlauncher.app.model.CanLogEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CanLoggerState(
    val isRunning: Boolean = false,
    val entries: List<CanLogEntry> = emptyList(),
    val totalCount: Long = 0L,
    val droppedCount: Long = 0L,
    val currentLogPath: String? = null,
    val lastExportPath: String? = null,
    val watchedActions: List<String> = CanLogger.DEFAULT_BROADCAST_ACTIONS,
    val customActions: List<String> = emptyList(),
    val socketCanInterfaces: List<String> = emptyList(),
    val sourceCounts: Map<String, Long> = emptyMap(),
    val lastError: String? = null
)

class CanLogger(
    context: Context,
    private val scope: CoroutineScope
) {
    private val appContext = context.applicationContext
    private val _state = MutableStateFlow(CanLoggerState(socketCanInterfaces = detectSocketCanInterfaces()))
    val state: StateFlow<CanLoggerState> = _state

    private val fileDateFormat = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
    private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
    private val writerLock = Any()

    private var receiver: BroadcastReceiver? = null
    private var settingsObserver: ContentObserver? = null
    private var settingsPollJob: Job? = null
    private var logFile: File? = null
    private var writer: java.io.BufferedWriter? = null
    private var sequence = 0L
    private var lastSettingsSnapshot: Map<String, String?> = emptyMap()

    fun start() {
        if (_state.value.isRunning) return

        runCatching {
            val dir = File(appContext.getExternalFilesDir(null) ?: appContext.filesDir, "can-logs")
            dir.mkdirs()
            val file = File(dir, "openlauncher-can-${fileDateFormat.format(Date())}.csv")
            val opened = file.bufferedWriter()
            opened.appendLine(CSV_HEADER)
            synchronized(writerLock) {
                writer = opened
                logFile = file
            }
            sequence = 0L
            lastSettingsSnapshot = emptyMap()

            _state.update {
                it.copy(
                    isRunning = true,
                    entries = emptyList(),
                    totalCount = 0L,
                    droppedCount = 0L,
                    currentLogPath = file.absolutePath,
                    lastExportPath = null,
                    socketCanInterfaces = detectSocketCanInterfaces(),
                    sourceCounts = emptyMap(),
                    lastError = null
                )
            }

            registerIntentReceiver()
            registerSettingsObserver()
            startSettingsPolling()
            addMarker("LOGGER START")
        }.onFailure { error ->
            _state.update { it.copy(lastError = "CAN logger could not start: ${error.message}") }
            stop()
        }
    }

    fun stop() {
        unregisterIntentReceiver()
        unregisterSettingsObserver()
        settingsPollJob?.cancel()
        settingsPollJob = null

        runBlocking {
            withContext(Dispatchers.IO) {
                synchronized(writerLock) {
                    writer?.flush()
                    writer?.close()
                    writer = null
                }
            }
        }

        _state.update { it.copy(isRunning = false) }
    }

    fun clear() {
        _state.update { it.copy(entries = emptyList(), droppedCount = 0L, sourceCounts = emptyMap()) }
        if (_state.value.isRunning) addMarker("VIEW CLEARED")
    }

    fun saveSnapshot(): String? {
        val runningFile = logFile
        if (_state.value.isRunning && runningFile != null) {
            scope.launch(Dispatchers.IO) {
                synchronized(writerLock) { writer?.flush() }
            }
            _state.update { it.copy(lastExportPath = runningFile.absolutePath) }
            return runningFile.absolutePath
        }

        return runCatching {
            val dir = File(appContext.getExternalFilesDir(null) ?: appContext.filesDir, "can-logs")
            dir.mkdirs()
            val file = File(dir, "openlauncher-can-snapshot-${fileDateFormat.format(Date())}.csv")
            file.bufferedWriter().use { out ->
                out.appendLine(CSV_HEADER)
                _state.value.entries.asReversed().forEach { out.appendLine(toCsv(it)) }
            }
            _state.update { it.copy(lastExportPath = file.absolutePath) }
            file.absolutePath
        }.onFailure { error ->
            _state.update { it.copy(lastError = "Snapshot failed: ${error.message}") }
        }.getOrNull()
    }

    fun addMarker(label: String) {
        val cleanLabel = label.trim().ifEmpty { "MARK" }
        record(
            source = "marker",
            action = cleanLabel,
            canId = null,
            dlc = null,
            dataHex = null,
            raw = cleanLabel
        )
    }

    fun addCustomAction(action: String) {
        val cleanAction = action.trim()
        if (cleanAction.isEmpty()) return
        _state.update { current ->
            if (cleanAction in current.customActions || cleanAction in DEFAULT_BROADCAST_ACTIONS) {
                current
            } else {
                val custom = (current.customActions + cleanAction).distinct().sorted()
                current.copy(customActions = custom, watchedActions = (DEFAULT_BROADCAST_ACTIONS + custom).distinct())
            }
        }
        if (_state.value.isRunning) registerIntentReceiver()
    }

    fun removeCustomAction(action: String) {
        _state.update { current ->
            val custom = current.customActions.filterNot { it == action }
            current.copy(customActions = custom, watchedActions = (DEFAULT_BROADCAST_ACTIONS + custom).distinct())
        }
        if (_state.value.isRunning) registerIntentReceiver()
    }

    private fun registerIntentReceiver() {
        unregisterIntentReceiver()
        val actions = _state.value.watchedActions
        if (actions.isEmpty()) return

        val filter = IntentFilter().apply {
            actions.forEach { addAction(it) }
        }
        val nextReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                recordIntent(intent)
            }
        }
        receiver = nextReceiver
        runCatching {
            ContextCompat.registerReceiver(
                appContext,
                nextReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
        }.onFailure { error ->
            receiver = null
            _state.update { it.copy(lastError = "Broadcast receiver failed: ${error.message}") }
        }
    }

    private fun unregisterIntentReceiver() {
        receiver?.let { registered ->
            runCatching { appContext.unregisterReceiver(registered) }
        }
        receiver = null
    }

    private fun registerSettingsObserver() {
        unregisterSettingsObserver()
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                scope.launch(Dispatchers.IO) { refreshInterestingSettings("settings:change") }
            }
        }
        settingsObserver = observer
        runCatching {
            appContext.contentResolver.registerContentObserver(
                Settings.Global.CONTENT_URI,
                true,
                observer
            )
        }.onFailure { error ->
            settingsObserver = null
            _state.update { it.copy(lastError = "Settings observer failed: ${error.message}") }
        }
    }

    private fun unregisterSettingsObserver() {
        settingsObserver?.let { observer ->
            runCatching { appContext.contentResolver.unregisterContentObserver(observer) }
        }
        settingsObserver = null
    }

    private fun startSettingsPolling() {
        settingsPollJob?.cancel()
        settingsPollJob = scope.launch(Dispatchers.IO) {
            refreshInterestingSettings("settings:init")
            while (isActive && _state.value.isRunning) {
                delay(1_000L)
                refreshInterestingSettings("settings:poll")
            }
        }
    }

    private fun refreshInterestingSettings(source: String) {
        val next = runCatching { readInterestingSettings() }
            .onFailure { error ->
                _state.update { it.copy(lastError = "Settings scan failed: ${error.message}") }
            }
            .getOrNull() ?: return

        val previous = lastSettingsSnapshot
        lastSettingsSnapshot = next

        if (previous.isEmpty()) {
            next.forEach { (name, value) ->
                record(
                    source = source,
                    action = name,
                    canId = null,
                    dlc = null,
                    dataHex = null,
                    raw = "$name=${value.orEmpty()}"
                )
            }
            return
        }

        (previous.keys + next.keys).sorted().forEach { name ->
            val old = previous[name]
            val new = next[name]
            if (old != new) {
                record(
                    source = source,
                    action = name,
                    canId = null,
                    dlc = null,
                    dataHex = null,
                    raw = "$name=${new.orEmpty()}"
                )
            }
        }
    }

    private fun readInterestingSettings(): Map<String, String?> {
        val values = linkedMapOf<String, String?>()
        appContext.contentResolver.query(
            Settings.Global.CONTENT_URI,
            arrayOf("name", "value"),
            null,
            null,
            null
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            val valueIndex = cursor.getColumnIndex("value")
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIndex.coerceAtLeast(0)) ?: continue
                val value = cursor.getString(valueIndex.coerceAtLeast(0))
                if (isInterestingSetting(name, value)) values[name] = value
            }
        }
        return values
    }

    private fun isInterestingSetting(name: String, value: String?): Boolean {
        val text = "${name.lowercase(Locale.US)} ${value.orEmpty().lowercase(Locale.US)}"
        return INTERESTING_SETTING_TOKENS.any { token -> token in text }
    }

    private fun recordIntent(intent: Intent) {
        val action = intent.action.orEmpty()
        val extras = intent.extras
        val raw = buildString {
            append(action)
            if (intent.dataString != null) append(" data=").append(intent.dataString)
            if (extras != null && !extras.isEmpty) append(" extras=").append(formatBundle(extras))
        }
        val parsed = extractCanFrame(action, extras, raw)
        record(
            source = classifySource(action),
            action = action,
            canId = parsed.canId,
            dlc = parsed.dlc,
            dataHex = parsed.dataHex,
            raw = raw
        )
    }

    private fun record(
        source: String,
        action: String,
        canId: String?,
        dlc: Int?,
        dataHex: String?,
        raw: String
    ) {
        if (!_state.value.isRunning && source != "marker") return
        val entry = CanLogEntry(
            sequence = ++sequence,
            timestampMs = System.currentTimeMillis(),
            source = source,
            action = action,
            canId = canId,
            dlc = dlc,
            dataHex = dataHex,
            raw = raw.take(MAX_RAW_CHARS)
        )

        _state.update { current ->
            val nextEntries = (listOf(entry) + current.entries).take(MAX_MEMORY_ENTRIES)
            val sourceCounts = current.sourceCounts.toMutableMap()
            sourceCounts[source] = (sourceCounts[source] ?: 0L) + 1L
            current.copy(
                entries = nextEntries,
                totalCount = current.totalCount + 1L,
                droppedCount = (current.totalCount + 1L - nextEntries.size).coerceAtLeast(0L),
                sourceCounts = sourceCounts
            )
        }

        scope.launch(Dispatchers.IO) {
            synchronized(writerLock) {
                writer?.appendLine(toCsv(entry))
                writer?.flush()
            }
        }
    }

    private fun extractCanFrame(action: String, extras: Bundle?, raw: String): ParsedFrame {
        extras?.keySet()?.sorted()?.forEach { key ->
            val value = extras.get(key)
            if (value is ByteArray && looksLikeDataKey(key)) {
                val data = value.toHex()
                val canId = findCanId(extras)
                return ParsedFrame(canId = canId, dlc = value.size, dataHex = data)
            }
        }

        CANDUMP_PATTERN.find(raw)?.let { match ->
            val data = match.groupValues[2]
                .replace(" ", "")
                .chunked(2)
                .joinToString(" ")
                .uppercase(Locale.US)
            return ParsedFrame(
                canId = "0x${match.groupValues[1].uppercase(Locale.US)}",
                dlc = data.split(' ').count { it.isNotBlank() },
                dataHex = data
            )
        }

        return ParsedFrame(
            canId = if ("can" in action.lowercase(Locale.US)) findCanId(extras) else null,
            dlc = null,
            dataHex = null
        )
    }

    private fun findCanId(extras: Bundle?): String? {
        if (extras == null) return null
        extras.keySet().sorted().forEach { key ->
            val lower = key.lowercase(Locale.US)
            if ("id" !in lower && "addr" !in lower && "address" !in lower) return@forEach
            when (val value = extras.get(key)) {
                is Number -> return "0x${value.toLong().toString(16).uppercase(Locale.US)}"
                is String -> {
                    val clean = value.trim()
                    if (clean.isNotEmpty()) return if (clean.startsWith("0x", ignoreCase = true)) {
                        clean.uppercase(Locale.US)
                    } else {
                        "0x${clean.uppercase(Locale.US)}"
                    }
                }
            }
        }
        return null
    }

    private fun looksLikeDataKey(key: String): Boolean {
        val lower = key.lowercase(Locale.US)
        return "data" in lower || "bytes" in lower || "frame" in lower || "can" in lower || "mcu" in lower
    }

    private fun classifySource(action: String): String {
        val lower = action.lowercase(Locale.US)
        return when {
            "szchoiceway" in lower -> "szchoiceway"
            "microntek" in lower -> "microntek"
            "can" in lower -> "can-broadcast"
            "mcu" in lower -> "mcu"
            else -> "broadcast"
        }
    }

    private fun formatBundle(bundle: Bundle): String =
        bundle.keySet().sorted().joinToString(prefix = "{", postfix = "}") { key ->
            "$key=${formatValue(bundle.get(key))}"
        }

    private fun formatValue(value: Any?): String = when (value) {
        null -> "null"
        is ByteArray -> value.toHex()
        is IntArray -> value.joinToString(prefix = "[", postfix = "]")
        is LongArray -> value.joinToString(prefix = "[", postfix = "]")
        is FloatArray -> value.joinToString(prefix = "[", postfix = "]")
        is DoubleArray -> value.joinToString(prefix = "[", postfix = "]")
        is BooleanArray -> value.joinToString(prefix = "[", postfix = "]")
        is Array<*> -> value.joinToString(prefix = "[", postfix = "]") { formatValue(it) }
        is Bundle -> formatBundle(value)
        else -> value.toString()
    }.take(MAX_RAW_CHARS)

    private fun ByteArray.toHex(): String =
        joinToString(" ") { byte -> "%02X".format(byte.toInt() and 0xFF) }

    private fun toCsv(entry: CanLogEntry): String = listOf(
        entry.sequence.toString(),
        isoDateFormat.format(Date(entry.timestampMs)),
        entry.timestampMs.toString(),
        entry.source,
        entry.action,
        entry.canId.orEmpty(),
        entry.dlc?.toString().orEmpty(),
        entry.dataHex.orEmpty(),
        entry.raw
    ).joinToString(",") { csvEscape(it) }

    private fun csvEscape(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    private fun detectSocketCanInterfaces(): List<String> {
        val netDir = File("/sys/class/net")
        return runCatching {
            netDir.listFiles()
                ?.map { it.name }
                ?.filter { name -> name.startsWith("can") || name.startsWith("vcan") }
                ?.sorted()
                .orEmpty()
        }.getOrDefault(emptyList())
    }

    private data class ParsedFrame(
        val canId: String?,
        val dlc: Int?,
        val dataHex: String?
    )

    companion object {
        private const val MAX_MEMORY_ENTRIES = 400
        private const val MAX_RAW_CHARS = 2_000
        private const val CSV_HEADER = "sequence,timestamp_iso,timestamp_ms,source,action,can_id,dlc,data_hex,raw"

        private val CANDUMP_PATTERN =
            Regex("""\b([0-9A-Fa-f]{3,8})[#:\s]+((?:[0-9A-Fa-f]{2}\s*){1,64})\b""")

        private val INTERESTING_SETTING_TOKENS = listOf(
            "can",
            "canbus",
            "mcu",
            "vehicle",
            "car_",
            "door",
            "headlight",
            "light",
            "reverse",
            "steer",
            "wheel",
            "radio",
            "media_info"
        )

        val DEFAULT_BROADCAST_ACTIONS = listOf(
            "com.szchoiceway.eventcenter.EventUtils.ACTION_MCU_CMD_EVENT",
            "com.szchoiceway.eventcenter.EventUtils.ACTION_MCU_DATA_EVENT",
            "com.szchoiceway.eventcenter.EventUtils.ACTION_MCU_KEY_EVENT",
            "com.szchoiceway.eventcenter.EventUtils.ACTION_CANBUS_CMD_EVENT",
            "com.szchoiceway.eventcenter.EventUtils.ACTION_CANBUS_DATA_EVENT",
            "com.szchoiceway.eventcenter.EventUtils.ACTION_CANBUS_INFO",
            "com.szchoiceway.eventcenter.EventUtils.ACTION_CAR_INFO",
            "com.microntek.canbus",
            "com.microntek.canbuschange",
            "com.microntek.carstate",
            "com.microntek.controlinfo",
            "com.microntek.irkeyDown",
            "com.mcu.canbus",
            "com.mcu.event",
            "com.canbus.action.CANBUS_DATA",
            "com.android.canbus.action.CANBUS_DATA",
            "android.intent.action.CANBUS"
        ).distinct().sorted()
    }
}

