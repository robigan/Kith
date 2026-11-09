package com.robigan.TheRealDex.domain

import kotlinx.datetime.LocalDate

data class LinkedInConnection(
    val firstName: String,
    val lastName: String,
    val url: String?,
    val email: String?,
    val company: String?,
    val position: String?,
    val connectedOn: LocalDate?,
) {
    val name: String get() = "$firstName $lastName".trim()
}

/**
 * Parses Connections.csv from LinkedIn's data export (Settings → Data privacy → Get a copy of your data).
 * The export starts with a few "Notes:" lines before the header row.
 */
object LinkedInCsv {
    fun parse(text: String): List<LinkedInConnection> {
        val rows = csvRows(text)
        val headerIndex = rows.indexOfFirst { row -> row.any { it.trim().equals("First Name", ignoreCase = true) } }
        if (headerIndex < 0) return emptyList()
        val header = rows[headerIndex].map { it.trim().lowercase() }
        fun col(name: String) = header.indexOf(name)
        val first = col("first name");
        val last = col("last name");
        val url = col("url")
        val email = col("email address");
        val company = col("company");
        val position = col("position")
        val connected = col("connected on")
        return rows.drop(headerIndex + 1).mapNotNull { row ->
            fun at(i: Int) = row.getOrNull(i)?.trim()?.ifEmpty { null }
            val f = at(first) ?: return@mapNotNull null
            LinkedInConnection(
                f,
                at(last) ?: "",
                at(url),
                at(email),
                at(company),
                at(position),
                at(connected)?.let(::parseDate)
            )
        }
    }

    private val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    /** "06 Nov 2025" or ISO "2025-11-06". */
    fun parseDate(s: String): LocalDate? {
        runCatching { return LocalDate.parse(s) }
        val parts = s.split(' ', '-').filter { it.isNotBlank() }
        if (parts.size != 3) return null
        val day = parts[0].toIntOrNull() ?: return null
        val month = months.indexOf(parts[1].take(3).lowercase()) + 1
        val year = parts[2].toIntOrNull() ?: return null
        if (month == 0) return null
        return runCatching { LocalDate(year, month, day) }.getOrNull()
    }

    private fun csvRows(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && text.getOrNull(i + 1) == '"' -> {
                    field.append('"'); i++
                }

                c == '"' -> quoted = !quoted
                !quoted && c == ',' -> {
                    row.add(field.toString()); field.clear()
                }

                !quoted && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && text.getOrNull(i + 1) == '\n') i++
                    row.add(field.toString()); field.clear()
                    if (row.any { it.isNotBlank() }) rows.add(row)
                    row = mutableListOf()
                }

                else -> field.append(c)
            }
            i++
        }
        row.add(field.toString())
        if (row.any { it.isNotBlank() }) rows.add(row)
        return rows
    }
}
