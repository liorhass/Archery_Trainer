package com.liorapps.archerytrainer.screens.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ---------------------------------------------------------------------------
// Date / time helpers  (requires API 26+)
// ---------------------------------------------------------------------------
val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")
val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
val DATE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d/MM/yyyy HH:mm")

fun toLocalDateTime(epochMs: Long): LocalDateTime =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneId.systemDefault())

fun formatDate(epochMs: Long): String = DATE_FORMATTER.format(toLocalDateTime(epochMs))

/** Formats a UTC epoch-millisecond value as  "14:33" using the device's local time zone */
fun formatTime(epochMs: Long): String = TIME_FORMATTER.format(toLocalDateTime(epochMs))

/** Formats a UTC epoch-millisecond value as  "Mon, 14/5/26 14:33" using the device's local time zone */
fun formatDateTime(epochMs: Long): String = DATE_TIME_FORMATTER.format(toLocalDateTime(epochMs))
