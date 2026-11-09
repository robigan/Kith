package com.robigan.TheRealDex.domain

import com.robigan.TheRealDex.model.Birthday
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

/** Annual dates (birthdays, met-anniversaries). Feb 29 falls on Feb 28 in non-leap years. */
object Occasions {
    fun nextOccurrence(month: Int, day: Int, from: LocalDate): LocalDate {
        val thisYear = onYear(from.year, month, day)
        return if (thisYear >= from) thisYear else onYear(from.year + 1, month, day)
    }

    fun nextBirthday(b: Birthday, from: LocalDate): LocalDate = nextOccurrence(b.month, b.day, from)

    fun nextAnniversary(metOn: LocalDate, from: LocalDate): LocalDate = nextOccurrence(metOn.month.number, metOn.day, from)

    private fun onYear(year: Int, month: Int, day: Int): LocalDate =
        if (month == 2 && day == 29 && !isLeap(year)) LocalDate(year, 2, 28) else LocalDate(year, month, day)

    private fun isLeap(y: Int) = (y % 4 == 0 && y % 100 != 0) || y % 400 == 0
}
