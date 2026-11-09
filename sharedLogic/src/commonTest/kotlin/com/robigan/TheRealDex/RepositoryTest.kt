package com.robigan.TheRealDex

import com.robigan.TheRealDex.data.ContactInput
import com.robigan.TheRealDex.data.ImportedAttendee
import com.robigan.TheRealDex.data.ImportedEvent
import com.robigan.TheRealDex.data.LogInput
import com.robigan.TheRealDex.model.Cadence
import com.robigan.TheRealDex.model.InteractionType
import com.robigan.TheRealDex.model.SignalType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RepositoryTest {
    private fun TestEnv.log(id: String, date: LocalDate, next: Cadence = Cadence.MONTHLY, note: String = "") = repo.let {
        LogInput(InteractionType.COFFEE, date.toEpochDays(), listOf(id), note, next, emptyList())
    }

    @Test
    fun loggingResetsCadence() = runTest {
        val env = TestEnv()
        val id = env.repo.createContact(ContactInput("Priya Nair", cadence = Cadence.MONTHLY))
        env.repo.logInteraction(env.log(id, LocalDate(2026, 9, 28)))
        assertEquals(LocalDate(2026, 10, 28), env.summary(id).due)

        env.repo.logInteraction(env.log(id, LocalDate(2026, 11, 6)))
        assertEquals(LocalDate(2026, 12, 6), env.summary(id).due)
    }

    @Test
    fun nextReminderOverridesOnceAndNeverTurnsRemindersOff() = runTest {
        val env = TestEnv()
        val id = env.repo.createContact(ContactInput("Sam Whitfield", cadence = Cadence.MONTHLY))
        env.repo.logInteraction(env.log(id, DESIGN_TODAY, next = Cadence.TWO_WEEKS))
        assertEquals(DESIGN_TODAY.plus(DatePeriod(days = 14)), env.summary(id).due)
        assertEquals(Cadence.MONTHLY, env.summary(id).effectiveCadence)

        env.repo.logInteraction(env.log(id, DESIGN_TODAY, next = Cadence.NEVER))
        assertEquals(Cadence.NEVER, env.summary(id).effectiveCadence)
        assertNull(env.summary(id).due)
    }

    @Test
    fun loggingForUntrackedContactAdoptsCadence() = runTest {
        val env = TestEnv()
        val id = env.repo.createContact(ContactInput("Ben Hartley"))
        assertNull(env.summary(id).due)
        env.repo.logInteraction(env.log(id, DESIGN_TODAY, next = Cadence.QUARTERLY))
        assertEquals(Cadence.QUARTERLY, env.summary(id).effectiveCadence)
    }

    @Test
    fun markDoneAndUndo() = runTest {
        val env = TestEnv()
        val id = env.repo.createContact(ContactInput("Mum", cadence = Cadence.WEEKLY))
        env.repo.logInteraction(env.log(id, DESIGN_TODAY.minus(DatePeriod(days = 10)), next = Cadence.WEEKLY))
        val before = env.summary(id).due
        val receipt = env.repo.markDone(id)
        assertEquals(DESIGN_TODAY.plus(DatePeriod(days = 7)), env.summary(id).due)
        env.repo.undoDone(receipt)
        assertEquals(before, env.summary(id).due)
    }

    @Test
    fun snoozePushesAWeekPastToday() = runTest {
        val env = TestEnv()
        val id = env.repo.createContact(ContactInput("Jules Moreau", cadence = Cadence.WEEKLY))
        env.repo.logInteraction(env.log(id, DESIGN_TODAY.minus(DatePeriod(days = 30)), next = Cadence.WEEKLY))
        env.repo.snooze(id, env.summary(id).due?.toEpochDays())
        assertEquals(DESIGN_TODAY.plus(DatePeriod(days = 7)), env.summary(id).due)
    }

    @Test
    fun groupCadenceIsInherited() = runTest {
        val env = TestEnv()
        val id = env.repo.createContact(ContactInput("Alex Chen"))
        env.repo.createGroup("Close friends", Cadence.MONTHLY, listOf(id))
        env.repo.createGroup("Family", Cadence.WEEKLY, listOf(id))
        assertEquals(Cadence.WEEKLY, env.summary(id).effectiveCadence)
    }

    @Test
    fun unnotedCalendarMeetingsDoNotResetCadenceUntilNoted() = runTest {
        val env = TestEnv()
        val id = env.repo.createContact(ContactInput("Priya Nair", email = "priya@fernwood.example", cadence = Cadence.MONTHLY))
        env.repo.logInteraction(env.log(id, LocalDate(2026, 9, 28)))
        val meeting = LocalDate(2026, 11, 6)
        env.sync.importCalendarEvents(
            listOf(
                ImportedEvent(
                    "evt-1", "Coffee with Priya Nair", "Ozone, Old Street", meeting.toEpochDays() * 86_400_000L + 9 * 3_600_000L, 45,
                    listOf(ImportedAttendee(null, "PRIYA@fernwood.example"), ImportedAttendee("Stranger", "x@example.com")),
                )
            )
        )
        assertEquals(LocalDate(2026, 10, 28), env.summary(id).due)
        val unnoted = env.repo.unnotedMeetings(DESIGN_TODAY.minus(DatePeriod(days = 14)), DESIGN_TODAY).first()
        assertEquals(listOf(listOf(id)), unnoted.map { it.participantIds })

        env.repo.logInteraction(
            LogInput(InteractionType.COFFEE, meeting.toEpochDays(), listOf(id), "Caught up.", Cadence.MONTHLY, emptyList(), unnoted.single().id)
        )
        assertEquals(LocalDate(2026, 12, 6), env.summary(id).due)
        assertTrue(env.repo.unnotedMeetings(DESIGN_TODAY.minus(DatePeriod(days = 14)), DESIGN_TODAY).first().isEmpty())
    }

    @Test
    fun linkedInReimportDetectsJobChange() = runTest {
        val env = TestEnv()
        val header = "First Name,Last Name,URL,Email Address,Company,Position,Connected On\n"
        val first = env.sync.importLinkedIn(header + "Leo,Brandt,https://linkedin.com/in/leo,,Halcyon,Design Lead,06 Nov 2025\n")
        assertEquals(1, first.created)
        val second = env.sync.importLinkedIn(header + "Leo,Brandt,https://linkedin.com/in/leo,,Northwind,Head of Design,06 Nov 2025\n")
        assertEquals(1, second.signals)

        val snap = env.repo.snapshot().first()
        val leo = snap.people.single().contact
        assertEquals("Northwind", leo.company)
        assertEquals("Head of Design", leo.role)
        assertEquals("Halcyon (Design Lead)", leo.workHistory)
        assertEquals(SignalType.NEW_ROLE, snap.signals.single().type)
    }

    @Test
    fun contactImportMergesByEmail() = runTest {
        val env = TestEnv()
        val id = env.repo.createContact(ContactInput("Priya", email = "priya@fernwood.example"))
        val result = env.sync.importContacts(
            listOf(com.robigan.TheRealDex.data.ImportedContact("cn-1", "Priya Nair", "priya@fernwood.example", "+44 7700 900123", "Fernwood", "Product Lead", 2, 14, 0))
        )
        assertEquals(0, result.created)
        val s = env.summary(id)
        assertEquals("Priya", s.contact.name)
        assertEquals("Fernwood", s.contact.company)
        assertEquals(14, s.contact.birthday?.day)
    }

    @Test
    fun deletingContactRemovesTheirData() = runTest {
        val env = TestEnv()
        val id = env.repo.createContact(ContactInput("Tomás Reyes", tags = listOf("Climbing"), giftIdeas = listOf("Chalk bag")))
        env.repo.logInteraction(env.log(id, DESIGN_TODAY))
        env.repo.deleteContact(id)
        val snap = env.repo.snapshot().first()
        assertTrue(snap.people.isEmpty())
        assertEquals(0L, env.db.interactionQueries.selectAllParticipants().executeAsList().size.toLong())
    }
}
