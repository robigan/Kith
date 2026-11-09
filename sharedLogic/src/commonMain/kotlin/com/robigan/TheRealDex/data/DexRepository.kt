package com.robigan.TheRealDex.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.robigan.TheRealDex.db.ContactRecord
import com.robigan.TheRealDex.db.DexDatabase
import com.robigan.TheRealDex.db.InteractionRecord
import com.robigan.TheRealDex.db.SmartViewRecord
import com.robigan.TheRealDex.domain.Cadences
import com.robigan.TheRealDex.domain.DateProvider
import com.robigan.TheRealDex.model.Cadence
import com.robigan.TheRealDex.model.Contact
import com.robigan.TheRealDex.model.ContactSummary
import com.robigan.TheRealDex.model.Interaction
import com.robigan.TheRealDex.model.InteractionOrigin
import com.robigan.TheRealDex.model.InteractionType
import com.robigan.TheRealDex.model.PeopleGroup
import com.robigan.TheRealDex.model.SignalType
import com.robigan.TheRealDex.model.SmartView
import com.robigan.TheRealDex.model.SourceProvider
import com.robigan.TheRealDex.model.Tint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.datetime.DatePeriod
import kotlin.uuid.Uuid

/** Proof of a Today "done" swipe, so it can be undone (FR-014). */
data class DoneReceipt(val contactId: String, val interactionId: String, val previousOverrideEpochDay: Long?)

class DexRepository(
    private val db: DexDatabase,
    private val dates: DateProvider,
    private val io: CoroutineDispatcher = Dispatchers.Default,
) {
    internal val database: DexDatabase get() = db

    // region Reading

    fun snapshot(): Flow<Snapshot> {
        val contacts = db.contactQueries.selectAll().asFlow().mapToList(io)
        val tags = db.contactQueries.selectAllTags().asFlow().mapToList(io)
        val gifts = db.contactQueries.selectAllGifts().asFlow().mapToList(io)
        val groups = combine(
            db.groupQueries.selectAll().asFlow().mapToList(io),
            db.groupQueries.selectAllMembers().asFlow().mapToList(io),
        ) { groupRows, members ->
            val byGroup = members.groupBy({ it.groupId }, { it.contactId })
            groupRows.map { PeopleGroup(it.id, it.name, Cadence.fromCode(it.cadence), byGroup[it.id].orEmpty()) }
        }
        val last = db.interactionQueries.selectLastPerContact().asFlow().mapToList(io)
        val sources = db.sourceQueries.selectAllContactSources().asFlow().mapToList(io)
        val signals =
            db.signalQueries.selectAll().asFlow().mapToList(io).map { rows -> rows.mapNotNull { it.toModel() } }
        val dismissed = db.signalQueries.selectDismissed().asFlow().mapToList(io).map { it.toSet() }
        val views = db.smartViewQueries.selectAll().asFlow().mapToList(io).map { rows -> rows.map { it.toModel() } }
        val connections = db.sourceQueries.selectConnections().asFlow().mapToList(io).map { rows ->
            SourceProvider.entries.map { p ->
                val row = rows.firstOrNull { it.provider == p.name }
                SourceConnection(p, row?.connected == 1L, row?.lastSyncAt)
            }
        }

        val people =
            combine(contacts, tags, gifts, groups, combine(last, sources) { l, s -> l to s }) { c, t, g, gr, (l, s) ->
                val tagsBy = t.groupBy({ it.contactId }, { it.tag })
                val giftsBy = g.groupBy({ it.contactId }, { it.text })
                val groupsBy = HashMap<String, MutableList<PeopleGroup>>()
                gr.forEach { group -> group.memberIds.forEach { groupsBy.getOrPut(it) { mutableListOf() }.add(group) } }
                val lastBy = l.associate { it.contactId to it.lastDate.toDate() }
                val sourcesBy = s.groupBy({ it.contactId }, { SourceProvider.fromName(it.provider) })
                val list = c.map { row ->
                    val contact = row.toModel()
                    val contactGroups = groupsBy[contact.id].orEmpty()
                    val cadence = Cadences.effective(contact, contactGroups)
                    val lastDate = lastBy[contact.id]
                    ContactSummary(
                        contact = contact,
                        tags = tagsBy[contact.id].orEmpty().toSet(),
                        groups = contactGroups,
                        giftIdeas = giftsBy[contact.id].orEmpty(),
                        sources = sourcesBy[contact.id].orEmpty().filterNotNull().toSet(),
                        lastInteraction = lastDate,
                        effectiveCadence = cadence,
                        due = Cadences.dueDate(cadence, lastDate, contact.createdOn, contact.dueOverride),
                    )
                }
                list to gr
            }
        return combine(people, signals, dismissed, views, connections) { (list, gr), sig, dis, v, conn ->
            Snapshot(list, gr, sig, dis, v, conn)
        }
    }

    fun contact(id: String): Flow<Contact?> =
        db.contactQueries.selectById(id).asFlow().mapToOneOrNull(io).map { it?.toModel() }

    fun interactionsFor(contactId: String): Flow<List<Interaction>> =
        db.interactionQueries.selectForContact(contactId).asFlow().mapToList(io)
            .map { rows -> rows.map { it.toModel(emptyList()) } }

    /** Imported meetings in [from]..[to] that still need a note, with their participants. */
    fun unnotedMeetings(from: LocalDate, to: LocalDate): Flow<List<Interaction>> = combine(
        db.interactionQueries.selectUnnotedSince(InteractionOrigin.CALENDAR.name, from.toDb(), to.toDb()).asFlow()
            .mapToList(io),
        db.interactionQueries.selectAllParticipants().asFlow().mapToList(io),
    ) { rows, participants ->
        val byInteraction = participants.groupBy({ it.interactionId }, { it.contactId })
        rows.map { it.toModel(byInteraction[it.id].orEmpty()) }
    }

    fun followUpsFor(contactId: String): Flow<List<FollowUp>> =
        db.interactionQueries.selectFollowUps(contactId).asFlow().mapToList(io).map { rows ->
            rows.map { FollowUp(it.id, it.text, it.done != 0L) }
        }

    fun importantDatesFor(contactId: String): Flow<List<ImportantDate>> =
        db.contactQueries.selectDates(contactId).asFlow().mapToList(io).map { rows ->
            rows.map { ImportantDate(it.label, it.month.toInt(), it.day.toInt(), it.year?.toInt()) }
        }

    fun sourceCountsFor(contactId: String): Flow<Map<InteractionOrigin, Int>> =
        db.interactionQueries.countByOriginForContact(contactId).asFlow().mapToList(io).map { rows ->
            rows.mapNotNull { r ->
                InteractionOrigin.entries.firstOrNull { it.name == r.origin }?.let { it to r.total.toInt() }
            }.toMap()
        }

    /** Contacts whose interaction notes or titles contain [query]. */
    suspend fun contactIdsWithNotesMatching(query: String): Set<String> = withContext(io) {
        db.interactionQueries.searchNotes(query).executeAsList().toSet()
    }

    suspend fun interaction(id: String): Interaction? = withContext(io) {
        val row = db.interactionQueries.selectById(id).executeAsOneOrNull() ?: return@withContext null
        row.toModel(db.interactionQueries.selectParticipants(id).executeAsList())
    }

    suspend fun allContacts(): List<Contact> = withContext(io) {
        db.contactQueries.selectAll().executeAsList().map { it.toModel() }
    }

    suspend fun contactInput(id: String): ContactInput? = withContext(io) {
        val c = db.contactQueries.selectById(id).executeAsOneOrNull()?.toModel() ?: return@withContext null
        ContactInput(
            name = c.name,
            role = c.role.orEmpty(),
            company = c.company.orEmpty(),
            location = c.location.orEmpty(),
            relationship = c.relationship.orEmpty(),
            howWeMet = c.howWeMet.orEmpty(),
            metOnEpochDay = c.metOn?.toDb(),
            family = c.family.orEmpty(),
            birthdayMonth = c.birthday?.month ?: 0,
            birthdayDay = c.birthday?.day ?: 0,
            birthdayYear = c.birthday?.year ?: 0,
            workHistory = c.workHistory.orEmpty(),
            email = c.email.orEmpty(),
            phone = c.phone.orEmpty(),
            cadence = c.cadence,
            tags = db.contactQueries.selectTags(id).executeAsList(),
            giftIdeas = db.contactQueries.selectGifts(id).executeAsList().map { it.text },
            groupIds = db.groupQueries.selectAllMembers().executeAsList().filter { it.contactId == id }
                .map { it.groupId },
            starred = c.starred,
        )
    }

    // endregion

    // region Contacts

    suspend fun createContact(input: ContactInput): String = withContext(io) {
        val id = newId()
        db.transaction {
            db.contactQueries.insert(
                ContactRecord(
                    id = id,
                    name = input.name.trim(),
                    role = null,
                    company = null,
                    location = null,
                    relationship = null,
                    howWeMet = null,
                    metOn = null,
                    family = null,
                    birthdayMonth = null,
                    birthdayDay = null,
                    birthdayYear = null,
                    workHistory = null,
                    email = null,
                    phone = null,
                    tint = Tint.forName(input.name).name,
                    starred = input.starred.toDb(),
                    cadence = null,
                    dueOverride = null,
                    createdOn = dates.today().toDb(),
                )
            )
            writeContactFields(id, input)
        }
        id
    }

    suspend fun updateContact(id: String, input: ContactInput): Unit = withContext(io) {
        db.transaction { writeContactFields(id, input) }
    }

    private fun writeContactFields(id: String, input: ContactInput) {
        fun String.orNull() = trim().ifEmpty { null }
        val hasBirthday = input.birthdayMonth in 1..12 && input.birthdayDay in 1..31
        db.contactQueries.update(
            name = input.name.trim(),
            role = input.role.orNull(),
            company = input.company.orNull(),
            location = input.location.orNull(),
            relationship = input.relationship.orNull(),
            howWeMet = input.howWeMet.orNull(),
            metOn = input.metOnEpochDay,
            family = input.family.orNull(),
            birthdayMonth = if (hasBirthday) input.birthdayMonth.toLong() else null,
            birthdayDay = if (hasBirthday) input.birthdayDay.toLong() else null,
            birthdayYear = if (hasBirthday && input.birthdayYear > 0) input.birthdayYear.toLong() else null,
            workHistory = input.workHistory.orNull(),
            email = input.email.orNull(),
            phone = input.phone.orNull(),
            cadence = input.cadence?.code,
            id = id,
        )
        db.contactQueries.setStarred(input.starred.toDb(), id)
        db.contactQueries.deleteTags(id)
        input.tags.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()
            .forEach { db.contactQueries.insertTag(id, it) }
        db.contactQueries.deleteGifts(id)
        input.giftIdeas.map { it.trim() }.filter { it.isNotEmpty() }.forEachIndexed { i, text ->
            db.contactQueries.insertGift(newId(), id, text, i.toLong())
        }
        db.groupQueries.deleteMembershipsOfContact(id)
        input.groupIds.forEach { db.groupQueries.insertMember(it, id) }
    }

    suspend fun addImportantDate(contactId: String, label: String, month: Int, day: Int, year: Int?): Unit =
        withContext(io) {
            db.contactQueries.insertDate(newId(), contactId, label, month.toLong(), day.toLong(), year?.toLong())
        }

    suspend fun deleteContact(id: String): Unit = withContext(io) {
        db.transaction {
            db.contactQueries.deleteTags(id)
            db.contactQueries.deleteGifts(id)
            db.contactQueries.deleteDates(id)
            db.groupQueries.deleteMembershipsOfContact(id)
            db.interactionQueries.deleteParticipationsOfContact(id)
            db.interactionQueries.deleteOrphans()
            db.interactionQueries.deleteFollowUpsOfContact(id)
            db.sourceQueries.deleteSourcesOfContact(id)
            db.signalQueries.deleteForContact(id)
            db.contactQueries.delete(id)
        }
    }

    suspend fun setStarred(id: String, starred: Boolean): Unit = withContext(io) {
        db.contactQueries.setStarred(starred.toDb(), id)
    }

    /** null inherits the group cadence. Changing cadence clears any snooze or custom next reminder. */
    suspend fun setCadence(id: String, cadence: Cadence?): Unit = withContext(io) {
        db.contactQueries.setCadence(cadence?.code, id)
    }

    /** Records a job change: the previous role moves to work history (FR-045). */
    suspend fun applyRoleChange(contactId: String, role: String?, company: String): Unit = withContext(io) {
        db.transaction { applyRoleChangeTx(contactId, role, company) }
    }

    internal fun applyRoleChangeTx(contactId: String, role: String?, company: String) {
        val c = db.contactQueries.selectById(contactId).executeAsOneOrNull() ?: return
        val previous = listOfNotNull(c.company, c.role?.let { "($it)" }).joinToString(" ").ifBlank { null }
        val history = when {
            previous == null || c.company.equals(company, ignoreCase = true) -> c.workHistory
            c.workHistory.isNullOrBlank() -> previous
            else -> "$previous · ${c.workHistory}"
        }
        db.contactQueries.updateRole(
            role ?: c.role.takeIf { c.company.equals(company, ignoreCase = true) },
            company,
            history,
            contactId
        )
    }

    // endregion

    // region Interactions & cadence

    /** Saves a logged interaction, resets cadences (FR-004/FR-048) and applies the chosen next reminder (FR-046). */
    suspend fun logInteraction(input: LogInput): String = withContext(io) {
        val date = LocalDate.fromEpochDays(input.epochDay)
        val id = input.existingInteractionId ?: newId()
        db.transaction {
            if (input.existingInteractionId != null) {
                db.interactionQueries.update(input.type.name, input.epochDay, input.note.trim(), id)
            } else {
                db.interactionQueries.insert(
                    InteractionRecord(
                        id = id,
                        type = input.type.name,
                        date = input.epochDay,
                        title = null,
                        note = input.note.trim(),
                        origin = InteractionOrigin.MANUAL.name,
                        externalId = null,
                        location = null,
                        durationMinutes = null,
                        createdAt = dates.nowMillis(),
                    )
                )
            }
            db.interactionQueries.deleteParticipants(id)
            input.participantIds.distinct().forEach { db.interactionQueries.insertParticipant(id, it) }
            input.participantIds.distinct().forEach { applyNextReminder(it, date, input.nextReminder) }
            input.participantIds.firstOrNull()?.let { subject ->
                input.followUps.map { it.trim() }.filter { it.isNotEmpty() }.forEach {
                    db.interactionQueries.insertFollowUp(newId(), subject, id, it, dates.nowMillis())
                }
            }
        }
        id
    }

    private fun applyNextReminder(contactId: String, date: LocalDate, next: Cadence) {
        val contact = db.contactQueries.selectById(contactId).executeAsOneOrNull()?.toModel() ?: return
        val groups =
            db.groupQueries.selectAllMembers().executeAsList().filter { it.contactId == contactId }.map { it.groupId }
                .toSet()
        val effective = Cadences.effective(
            contact,
            db.groupQueries.selectAll().executeAsList().filter { it.id in groups }
                .map { PeopleGroup(it.id, it.name, Cadence.fromCode(it.cadence), emptyList()) },
        )
        when {
            next == Cadence.NEVER -> db.contactQueries.setCadence(Cadence.NEVER.code, contactId)
            effective == null || effective == Cadence.NEVER -> db.contactQueries.setCadence(next.code, contactId)
            effective == next -> db.contactQueries.setDueOverride(null, contactId)
            else -> db.contactQueries.setDueOverride(Cadences.nextFrom(date, next)?.toDb(), contactId)
        }
    }

    suspend fun deleteInteraction(id: String): Unit = withContext(io) {
        db.transaction {
            db.interactionQueries.deleteParticipants(id)
            db.interactionQueries.delete(id)
        }
    }

    /** Today swipe right: logs a check-in for today, which resets the cadence. */
    suspend fun markDone(contactId: String): DoneReceipt = withContext(io) {
        val id = newId()
        val previous = db.contactQueries.selectById(contactId).executeAsOneOrNull()?.dueOverride
        db.transaction {
            db.interactionQueries.insert(
                InteractionRecord(
                    id = id, type = InteractionType.CHECK_IN.name, date = dates.today().toDb(), title = null, note = "",
                    origin = InteractionOrigin.MANUAL.name, externalId = null, location = null, durationMinutes = null,
                    createdAt = dates.nowMillis(),
                )
            )
            db.interactionQueries.insertParticipant(id, contactId)
            db.contactQueries.setDueOverride(null, contactId)
        }
        DoneReceipt(contactId, id, previous)
    }

    suspend fun undoDone(receipt: DoneReceipt): Unit = withContext(io) {
        db.transaction {
            db.interactionQueries.deleteParticipants(receipt.interactionId)
            db.interactionQueries.delete(receipt.interactionId)
            db.contactQueries.setDueOverride(receipt.previousOverrideEpochDay, receipt.contactId)
        }
    }

    /** Today swipe left: pushes the due date one week past today or the current due date, whichever is later. */
    suspend fun snooze(contactId: String, currentDueEpochDay: Long?, days: Int = 7): Unit = withContext(io) {
        val base = maxOf(dates.today().toDb(), currentDueEpochDay ?: Long.MIN_VALUE)
        db.contactQueries.setDueOverride(LocalDate.fromEpochDays(base).plus(DatePeriod(days = days)).toDb(), contactId)
    }

    suspend fun setFollowUpDone(id: String, done: Boolean): Unit =
        withContext(io) { db.interactionQueries.setFollowUpDone(done.toDb(), id) }

    suspend fun deleteFollowUp(id: String): Unit = withContext(io) { db.interactionQueries.deleteFollowUp(id) }

    suspend fun dismissSignal(key: String): Unit = withContext(io) { db.signalQueries.dismiss(key) }

    internal fun addSignalTx(
        type: SignalType,
        contactId: String,
        date: LocalDate,
        text: String,
        source: SourceProvider?,
        key: String
    ): Boolean {
        return db.signalQueries.insert(newId(), type.name, contactId, date.toDb(), text, source?.name, key).value > 0
    }

    // endregion

    // region Groups

    suspend fun createGroup(name: String, cadence: Cadence?, memberIds: List<String>): String = withContext(io) {
        val id = newId()
        db.transaction {
            db.groupQueries.insert(id, name.trim(), cadence?.code, dates.nowMillis())
            memberIds.forEach { db.groupQueries.insertMember(id, it) }
        }
        id
    }

    suspend fun updateGroup(id: String, name: String, cadence: Cadence?): Unit = withContext(io) {
        db.groupQueries.update(name.trim(), cadence?.code, id)
    }

    suspend fun addMembers(groupId: String, contactIds: List<String>): Unit = withContext(io) {
        db.transaction { contactIds.forEach { db.groupQueries.insertMember(groupId, it) } }
    }

    suspend fun removeMember(groupId: String, contactId: String): Unit =
        withContext(io) { db.groupQueries.deleteMember(groupId, contactId) }

    suspend fun deleteGroup(id: String): Unit = withContext(io) {
        db.transaction {
            db.groupQueries.deleteMembersOfGroup(id)
            db.smartViewQueries.clearGroup(id)
            db.groupQueries.delete(id)
        }
    }

    // endregion

    // region Smart views

    suspend fun saveSmartView(id: String?, input: SmartViewInput): String = withContext(io) {
        val viewId = id ?: newId()
        db.transaction {
            if (id == null) {
                db.smartViewQueries.insert(
                    SmartViewRecord(
                        viewId, input.name.trim(), input.code.trim().uppercase().take(4), input.tint.name,
                        input.tag, input.groupId, input.minDaysSinceContact?.toLong(), input.location,
                        input.source?.name, input.birthdayThisMonth.toDb(), input.changedJobsWithinDays?.toLong(),
                        input.metContext, db.smartViewQueries.nextPosition().executeAsOne(),
                    )
                )
            } else {
                db.smartViewQueries.update(
                    input.name.trim(),
                    input.code.trim().uppercase().take(4),
                    input.tint.name,
                    input.tag,
                    input.groupId,
                    input.minDaysSinceContact?.toLong(),
                    input.location,
                    input.source?.name,
                    input.birthdayThisMonth.toDb(),
                    input.changedJobsWithinDays?.toLong(),
                    input.metContext,
                    viewId,
                )
            }
        }
        viewId
    }

    suspend fun smartView(id: String): SmartView? =
        withContext(io) { db.smartViewQueries.selectById(id).executeAsOneOrNull()?.toModel() }

    suspend fun deleteSmartView(id: String): Unit = withContext(io) { db.smartViewQueries.delete(id) }

    // endregion

    // region Setup

    /** Creates the source rows and starter smart views on first launch. */
    suspend fun ensureDefaults(): Unit = withContext(io) {
        db.transaction {
            val existing = db.sourceQueries.selectConnections().executeAsList().map { it.provider }.toSet()
            SourceProvider.entries.filter { it.name !in existing }
                .forEach { db.sourceQueries.upsertConnection(it.name, 0, null) }
            if (db.settingQueries.select(DEFAULTS_KEY).executeAsOneOrNull() == null) {
                db.settingQueries.put(DEFAULTS_KEY, "1")
                insertView("Not talked in 90+ days", "90D", Tint.PEACH, minDays = 90)
                insertView("Birthdays this month", "BDAY", Tint.LILAC, birthdayThisMonth = true)
                insertView("Changed jobs recently", "JOB", Tint.SKY, changedJobs = 60)
            }
        }
    }

    internal fun insertView(
        name: String, code: String, tint: Tint, minDays: Int? = null, birthdayThisMonth: Boolean = false,
        changedJobs: Int? = null, location: String? = null, metContext: String? = null, tag: String? = null,
    ) {
        db.smartViewQueries.insert(
            SmartViewRecord(
                newId(), name, code, tint.name, tag, null, minDays?.toLong(), location, null, birthdayThisMonth.toDb(),
                changedJobs?.toLong(), metContext, db.smartViewQueries.nextPosition().executeAsOne(),
            )
        )
    }

    suspend fun isEmpty(): Boolean = withContext(io) { db.contactQueries.count().executeAsOne() == 0L }

    // endregion

    internal fun newId(): String = Uuid.random().toString()

    private companion object {
        const val DEFAULTS_KEY = "defaults.v1"
    }
}

data class FollowUp(val id: String, val text: String, val done: Boolean)

data class ImportantDate(val label: String, val month: Int, val day: Int, val year: Int?)
