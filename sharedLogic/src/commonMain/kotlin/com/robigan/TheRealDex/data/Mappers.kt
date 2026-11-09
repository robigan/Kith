package com.robigan.TheRealDex.data

import com.robigan.TheRealDex.db.ContactRecord
import com.robigan.TheRealDex.db.InteractionRecord
import com.robigan.TheRealDex.db.SignalRecord
import com.robigan.TheRealDex.db.SmartViewRecord
import com.robigan.TheRealDex.model.Birthday
import com.robigan.TheRealDex.model.Cadence
import com.robigan.TheRealDex.model.Contact
import com.robigan.TheRealDex.model.Interaction
import com.robigan.TheRealDex.model.InteractionOrigin
import com.robigan.TheRealDex.model.InteractionType
import com.robigan.TheRealDex.model.SignalType
import com.robigan.TheRealDex.model.SmartView
import com.robigan.TheRealDex.model.SmartViewFilter
import com.robigan.TheRealDex.model.SourceProvider
import com.robigan.TheRealDex.model.StoredSignal
import com.robigan.TheRealDex.model.Tint
import kotlinx.datetime.LocalDate

internal fun Long.toDate(): LocalDate = LocalDate.fromEpochDays(this)
internal fun LocalDate.toDb(): Long = toEpochDays()
internal fun Boolean.toDb(): Long = if (this) 1L else 0L

internal fun ContactRecord.toModel(): Contact = Contact(
    id = id,
    name = name,
    role = role,
    company = company,
    location = location,
    relationship = relationship,
    howWeMet = howWeMet,
    metOn = metOn?.toDate(),
    family = family,
    birthday = if (birthdayMonth != null && birthdayDay != null) {
        Birthday(birthdayMonth.toInt(), birthdayDay.toInt(), birthdayYear?.toInt())
    } else null,
    workHistory = workHistory,
    email = email,
    phone = phone,
    tint = Tint.fromName(tint),
    starred = starred != 0L,
    cadence = Cadence.fromCode(cadence),
    dueOverride = dueOverride?.toDate(),
    createdOn = createdOn.toDate(),
)

internal fun InteractionRecord.toModel(participants: List<String>): Interaction = Interaction(
    id = id,
    type = InteractionType.fromName(type),
    date = date.toDate(),
    title = title,
    note = note,
    origin = InteractionOrigin.entries.firstOrNull { it.name == origin } ?: InteractionOrigin.MANUAL,
    location = location,
    durationMinutes = durationMinutes?.toInt(),
    participantIds = participants,
)

internal fun SignalRecord.toModel(): StoredSignal? = StoredSignal(
    id = id,
    type = SignalType.entries.firstOrNull { it.name == type } ?: return null,
    contactId = contactId,
    date = date.toDate(),
    text = text,
    source = SourceProvider.fromName(source),
)

internal fun SmartViewRecord.toModel(): SmartView = SmartView(
    id = id,
    name = name,
    code = code,
    tint = Tint.fromName(tint),
    filter = SmartViewFilter(
        tag = tag,
        groupId = groupId,
        minDaysSinceContact = minDaysSinceContact?.toInt(),
        location = location,
        source = SourceProvider.fromName(source),
        birthdayThisMonth = birthdayThisMonth != 0L,
        changedJobsWithinDays = changedJobsWithinDays?.toInt(),
        metContext = metContext,
    ),
)
