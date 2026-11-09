package com.robigan.TheRealDex.domain

import com.robigan.TheRealDex.model.Cadence
import com.robigan.TheRealDex.model.Contact
import com.robigan.TheRealDex.model.PeopleGroup
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

object Cadences {
    /** A contact's own cadence wins; otherwise the shortest cadence among its groups (FR-002). */
    fun effective(contact: Contact, groups: List<PeopleGroup>): Cadence? {
        contact.cadence?.let { return it }
        val groupCadences = groups.mapNotNull { it.cadence }
        if (groupCadences.isEmpty()) return null
        return groupCadences.minBy { it.approxDays }
    }

    /**
     * Due = last interaction + cadence (FR-003), unless a snooze or a chosen next reminder overrides it.
     * A contact with a cadence but no interactions yet is measured from when they were added.
     */
    fun dueDate(
        cadence: Cadence?,
        lastInteraction: LocalDate?,
        createdOn: LocalDate,
        override: LocalDate?
    ): LocalDate? {
        val period = cadence?.period ?: return null
        override?.let { return it }
        return (lastInteraction ?: createdOn).plus(period)
    }

    fun nextFrom(date: LocalDate, cadence: Cadence): LocalDate? = cadence.period?.let { date.plus(it) }
}
