package com.robigan.TheRealDex.domain

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.daysUntil
import kotlinx.datetime.number

/** English date labels matching the design ("Monday, 9 November", "Fri 6 Nov", "28 Sep"). */
object DateText {
    fun weekday(d: DayOfWeek): String = d.name.lowercase().replaceFirstChar { it.uppercase() }
    fun weekdayShort(d: DayOfWeek): String = weekday(d).take(3)
    fun month(m: Month): String = m.name.lowercase().replaceFirstChar { it.uppercase() }
    fun monthShort(m: Month): String = month(m).take(3)

    fun long(date: LocalDate): String = "${weekday(date.dayOfWeek)}, ${date.day} ${month(date.month)}"
    fun short(date: LocalDate): String = "${weekdayShort(date.dayOfWeek)} ${date.day} ${monthShort(date.month)}"

    /** "28 Sep", or "28 Sep 2024" outside the current year. */
    fun dayMonth(date: LocalDate, today: LocalDate): String =
        if (date.year == today.year) "${date.day} ${monthShort(date.month)}"
        else "${date.day} ${monthShort(date.month)} ${date.year}"

    fun birthday(month: Int, day: Int, year: Int?): String =
        "$day ${month(Month(month))}" + (year?.let { " $it" } ?: "")

    fun monthNumber(date: LocalDate): Int = date.month.number

    /** Relative due status: "12 days overdue", "Due today", "Due tomorrow", "Due Wed", "Due 28 Oct". */
    fun dueStatus(due: LocalDate, today: LocalDate): String {
        val days = today.daysUntil(due)
        return when {
            days < -1 -> "${-days} days overdue"
            days == -1 -> "1 day overdue"
            days == 0 -> "Due today"
            days == 1 -> "Due tomorrow"
            days < 7 -> "Due ${weekdayShort(due.dayOfWeek)}"
            else -> "Due ${dayMonth(due, today)}"
        }
    }

    /** "Today", "Tomorrow", or the weekday name for the coming week. */
    fun relativeDay(date: LocalDate, today: LocalDate): String = when (today.daysUntil(date)) {
        0 -> "Today"
        1 -> "Tomorrow"
        else -> weekday(date.dayOfWeek)
    }

    fun plural(n: Int, unit: String): String = if (n == 1) "1 $unit" else "$n ${unit}s"
}
