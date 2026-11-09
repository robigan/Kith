package com.robigan.TheRealDex.data

import com.robigan.TheRealDex.db.ContactRecord
import com.robigan.TheRealDex.db.DexDatabase
import com.robigan.TheRealDex.db.InteractionRecord
import com.robigan.TheRealDex.domain.DateProvider
import com.robigan.TheRealDex.domain.LinkedInCsv
import com.robigan.TheRealDex.model.InteractionOrigin
import com.robigan.TheRealDex.model.InteractionType
import com.robigan.TheRealDex.model.SignalType
import com.robigan.TheRealDex.model.SourceProvider
import com.robigan.TheRealDex.model.Tint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Merges data from connected sources into contacts (FR-060…FR-066). Platform code reads the device APIs
 * (Contacts, EventKit, a LinkedIn export file) and hands plain values to these functions.
 * Matching order: same source ID → email → phone → exact name. Existing values the user entered are never overwritten.
 */
class SourceSync(
    private val repository: DexRepository,
    private val dates: DateProvider,
    private val io: CoroutineDispatcher = Dispatchers.Default,
) {
    private val db: DexDatabase get() = repository.database

    suspend fun importContacts(contacts: List<ImportedContact>): ImportResult = withContext(io) {
        var created = 0
        var updated = 0
        db.transaction {
            contacts.filter { it.name.isNotBlank() }.forEach { imported ->
                val existing =
                    match(SourceProvider.CONTACTS, imported.externalId, imported.email, imported.phone, imported.name)
                val id = if (existing == null) {
                    created++
                    insertContact(
                        imported.name,
                        imported.role,
                        imported.company,
                        imported.email,
                        imported.phone
                    ).also { id ->
                        if (imported.birthdayMonth > 0 && imported.birthdayDay > 0) setBirthday(id, imported)
                    }
                } else {
                    updated++
                    fillMissing(existing, imported.role, imported.company, imported.email, imported.phone)
                    if (existing.birthdayMonth == null && imported.birthdayMonth > 0 && imported.birthdayDay > 0) setBirthday(
                        existing.id,
                        imported
                    )
                    existing.id
                }
                db.sourceQueries.upsertContactSource(
                    id,
                    SourceProvider.CONTACTS.name,
                    imported.externalId,
                    imported.role,
                    imported.company
                )
            }
            markSynced(SourceProvider.CONTACTS)
        }
        ImportResult(created, updated, 0)
    }

    /**
     * Past events with at least one known attendee become Meeting interactions (resetting cadence);
     * those without a note surface as "Needs a note" on Today (FR-063).
     */
    suspend fun importCalendarEvents(events: List<ImportedEvent>): ImportResult = withContext(io) {
        var created = 0
        var updated = 0
        val now = dates.nowMillis()
        val tz = TimeZone.currentSystemDefault()
        db.transaction {
            events.filter { it.startEpochMillis <= now }.forEach { event ->
                val people = event.attendees.mapNotNull { a ->
                    match(SourceProvider.CALENDAR, null, a.email, null, a.name ?: "")
                }.distinctBy { it.id }
                if (people.isEmpty()) return@forEach
                val date = Instant.fromEpochMilliseconds(event.startEpochMillis).toLocalDateTime(tz).date.toDb()
                val existing = db.interactionQueries.selectByExternalId(event.externalId).executeAsOneOrNull()
                val id = if (existing != null) {
                    updated++
                    db.interactionQueries.updateImported(
                        event.title,
                        date,
                        event.location,
                        event.durationMinutes.toLong(),
                        existing.id
                    )
                    existing.id
                } else {
                    created++
                    repository.newId().also { id ->
                        db.interactionQueries.insert(
                            InteractionRecord(
                                id = id,
                                type = InteractionType.MEETING.name,
                                date = date,
                                title = event.title,
                                note = "",
                                origin = InteractionOrigin.CALENDAR.name,
                                externalId = event.externalId,
                                location = event.location,
                                durationMinutes = event.durationMinutes.toLong(),
                                createdAt = now,
                            )
                        )
                    }
                }
                people.forEach { person ->
                    val previousLast =
                        db.interactionQueries.selectLatestForContact(person.id).executeAsOneOrNull()?.date
                    db.interactionQueries.insertParticipant(id, person.id)
                    // A newer meeting supersedes any snooze or custom reminder.
                    if (existing == null && (previousLast == null || date > previousLast)) db.contactQueries.setDueOverride(
                        null,
                        person.id
                    )
                    if (db.sourceQueries.selectForContactAndProvider(person.id, SourceProvider.CALENDAR.name)
                            .executeAsOneOrNull() == null
                    ) {
                        db.sourceQueries.upsertContactSource(person.id, SourceProvider.CALENDAR.name, null, null, null)
                    }
                }
            }
            markSynced(SourceProvider.CALENDAR)
        }
        ImportResult(created, updated, 0)
    }

    /**
     * Imports LinkedIn's Connections.csv. A changed position or company on a connection seen before creates a
     * "New role" signal and moves the old role into work history (FR-061).
     */
    suspend fun importLinkedIn(csv: String): ImportResult = withContext(io) {
        val connections = LinkedInCsv.parse(csv)
        var created = 0
        var updated = 0
        var signals = 0
        val today = dates.today()
        db.transaction {
            connections.forEach { conn ->
                val existing = match(SourceProvider.LINKEDIN, conn.url, conn.email, null, conn.name)
                if (existing == null) {
                    created++
                    val id = insertContact(conn.name, conn.position, conn.company, conn.email, null)
                    if (conn.connectedOn != null) {
                        db.contactQueries.update(
                            conn.name, conn.position, conn.company, null, null, "Connected on LinkedIn",
                            conn.connectedOn.toDb(), null, null, null, null, null, conn.email, null, null, id,
                        )
                    }
                    db.sourceQueries.upsertContactSource(
                        id,
                        SourceProvider.LINKEDIN.name,
                        conn.url,
                        conn.position,
                        conn.company
                    )
                    return@forEach
                }
                updated++
                val link = db.sourceQueries.selectForContactAndProvider(existing.id, SourceProvider.LINKEDIN.name)
                    .executeAsOneOrNull()
                val company = conn.company
                val changed = link != null && company != null &&
                        (!conn.company.equals(
                            link.lastCompany,
                            ignoreCase = true
                        ) || !conn.position.equals(link.lastRole, ignoreCase = true))
                if (changed) {
                    repository.applyRoleChangeTx(existing.id, conn.position, company)
                    val role = conn.position?.let { "$it at $company" } ?: "at $company"
                    val added = repository.addSignalTx(
                        SignalType.NEW_ROLE,
                        existing.id,
                        today,
                        "${existing.name} is now $role.",
                        SourceProvider.LINKEDIN,
                        "linkedin:${conn.url ?: existing.id}:${conn.position}@$company",
                    )
                    if (added) signals++
                } else {
                    fillMissing(existing, conn.position, conn.company, conn.email, null)
                }
                db.sourceQueries.upsertContactSource(
                    existing.id,
                    SourceProvider.LINKEDIN.name,
                    conn.url,
                    conn.position,
                    conn.company
                )
            }
            markSynced(SourceProvider.LINKEDIN)
        }
        ImportResult(created, updated, signals)
    }

    /** Disconnecting keeps imported people and history; it only stops future syncs. */
    suspend fun setConnected(provider: SourceProvider, connected: Boolean): Unit = withContext(io) {
        val row = db.sourceQueries.selectConnections().executeAsList().firstOrNull { it.provider == provider.name }
        db.sourceQueries.upsertConnection(provider.name, connected.toDb(), row?.lastSyncAt)
    }

    private fun markSynced(provider: SourceProvider) {
        db.sourceQueries.upsertConnection(provider.name, 1, dates.nowMillis())
    }

    private fun match(
        provider: SourceProvider,
        externalId: String?,
        email: String?,
        phone: String?,
        name: String
    ): ContactRecord? {
        externalId?.let { ext ->
            db.sourceQueries.selectByExternalId(provider.name, ext).executeAsOneOrNull()?.let { link ->
                db.contactQueries.selectById(link.contactId).executeAsOneOrNull()?.let { return it }
            }
        }
        email?.takeIf { it.isNotBlank() }
            ?.let { e -> db.contactQueries.selectByEmail(e).executeAsOneOrNull()?.let { return it } }
        phone?.takeIf { it.isNotBlank() }
            ?.let { p -> db.contactQueries.selectByPhone(p).executeAsOneOrNull()?.let { return it } }
        return name.takeIf { it.isNotBlank() }?.let { db.contactQueries.selectByName(it.trim()).executeAsOneOrNull() }
    }

    private fun insertContact(name: String, role: String?, company: String?, email: String?, phone: String?): String {
        val id = repository.newId()
        db.contactQueries.insert(
            ContactRecord(
                id = id,
                name = name.trim(),
                role = role,
                company = company,
                location = null,
                relationship = null,
                howWeMet = null,
                metOn = null,
                family = null,
                birthdayMonth = null,
                birthdayDay = null,
                birthdayYear = null,
                workHistory = null,
                email = email,
                phone = phone,
                tint = Tint.forName(name).name,
                starred = 0,
                cadence = null,
                dueOverride = null,
                createdOn = dates.today().toDb(),
            )
        )
        return id
    }

    private fun fillMissing(c: ContactRecord, role: String?, company: String?, email: String?, phone: String?) {
        db.contactQueries.update(
            c.name,
            c.role ?: role,
            c.company ?: company,
            c.location,
            c.relationship,
            c.howWeMet,
            c.metOn,
            c.family,
            c.birthdayMonth,
            c.birthdayDay,
            c.birthdayYear,
            c.workHistory,
            c.email ?: email,
            c.phone ?: phone,
            c.cadence,
            c.id,
        )
    }

    private fun setBirthday(id: String, imported: ImportedContact) {
        val c = db.contactQueries.selectById(id).executeAsOne()
        db.contactQueries.update(
            c.name,
            c.role,
            c.company,
            c.location,
            c.relationship,
            c.howWeMet,
            c.metOn,
            c.family,
            imported.birthdayMonth.toLong(),
            imported.birthdayDay.toLong(),
            imported.birthdayYear.takeIf { it > 0 }?.toLong(),
            c.workHistory,
            c.email,
            c.phone,
            c.cadence,
            c.id,
        )
    }
}
