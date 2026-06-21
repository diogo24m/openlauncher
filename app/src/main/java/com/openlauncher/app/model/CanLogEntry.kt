package com.openlauncher.app.model

data class CanLogEntry(
    val sequence: Long,
    val timestampMs: Long,
    val source: String,
    val action: String,
    val canId: String?,
    val dlc: Int?,
    val dataHex: String?,
    val raw: String
)

