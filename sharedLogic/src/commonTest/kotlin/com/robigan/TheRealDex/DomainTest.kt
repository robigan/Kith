package com.robigan.TheRealDex

import com.robigan.TheRealDex.domain.BusinessCardParser
import com.robigan.TheRealDex.domain.Cadences
import com.robigan.TheRealDex.domain.DateText
import com.robigan.TheRealDex.domain.LinkedInCsv
import com.robigan.TheRealDex.domain.NoteAnalyzer
import com.robigan.TheRealDex.domain.Occasions
import com.robigan.TheRealDex.model.Cadence
import com.robigan.TheRealDex.model.Contact
import com.robigan.TheRealDex.model.PeopleGroup
import com.robigan.TheRealDex.model.Tint
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DomainTest {
    private fun contact(name: String, id: String = name, cadence: Cadence? = null, role: String? = null, company: String? = null) = Contact(
        id, name, role, company, null, null, null, null, null, null, null, null, null, Tint.SKY, false, cadence, null, DESIGN_TODAY,
    )

    @Test
    fun monthlyCadenceUsesCalendarMonths() {
        // From the design: logged Fri 6 Nov → reminders Fri 20 Nov, Sun 6 Dec, Sat 6 Feb.
        val logged = LocalDate(2026, 11, 6)
        assertEquals("Fri 20 Nov", DateText.short(Cadences.nextFrom(logged, Cadence.TWO_WEEKS)!!))
        assertEquals("Sun 6 Dec", DateText.short(Cadences.nextFrom(logged, Cadence.MONTHLY)!!))
        assertEquals("Sat 6 Feb", DateText.short(Cadences.nextFrom(logged, Cadence.QUARTERLY)!!))
    }

    @Test
    fun priyaIsTwelveDaysOverdue() {
        val due = Cadences.dueDate(Cadence.MONTHLY, LocalDate(2026, 9, 28), DESIGN_TODAY, null)!!
        assertEquals("12 days overdue", DateText.dueStatus(due, DESIGN_TODAY))
    }

    @Test
    fun dueStatusLabels() {
        assertEquals("Due today", DateText.dueStatus(DESIGN_TODAY, DESIGN_TODAY))
        assertEquals("Due tomorrow", DateText.dueStatus(LocalDate(2026, 11, 10), DESIGN_TODAY))
        assertEquals("Due Wed", DateText.dueStatus(LocalDate(2026, 11, 11), DESIGN_TODAY))
        assertEquals("1 day overdue", DateText.dueStatus(LocalDate(2026, 11, 8), DESIGN_TODAY))
        assertEquals("Monday, 9 November", DateText.long(DESIGN_TODAY))
    }

    @Test
    fun ownCadenceWinsThenShortestGroup() {
        val groups = listOf(PeopleGroup("a", "Founders", Cadence.QUARTERLY, emptyList()), PeopleGroup("b", "Job search", Cadence.TWO_WEEKS, emptyList()))
        assertEquals(Cadence.TWO_WEEKS, Cadences.effective(contact("Ada"), groups))
        assertEquals(Cadence.YEARLY, Cadences.effective(contact("Ada", cadence = Cadence.YEARLY), groups))
        assertNull(Cadences.effective(contact("Ada"), emptyList()))
    }

    @Test
    fun overrideReplacesComputedDue() {
        val override = LocalDate(2026, 12, 1)
        assertEquals(override, Cadences.dueDate(Cadence.WEEKLY, DESIGN_TODAY, DESIGN_TODAY, override))
        assertNull(Cadences.dueDate(Cadence.NEVER, DESIGN_TODAY, DESIGN_TODAY, override))
    }

    @Test
    fun leapDayBirthdaysFallOnTheTwentyEighth() {
        assertEquals(LocalDate(2027, 2, 28), Occasions.nextOccurrence(2, 29, LocalDate(2027, 1, 1)))
        assertEquals(LocalDate(2028, 2, 29), Occasions.nextOccurrence(2, 29, LocalDate(2028, 1, 1)))
        assertEquals(LocalDate(2027, 2, 14), Occasions.nextOccurrence(2, 14, DESIGN_TODAY))
    }

    private val designNote = "Caught up at Ozone. She took the Head of Product offer at Tidewater — starts January. " +
        "Wants an intro to Ada at Brightline. Anika landed in Sydney last week."

    @Test
    fun noteMentionsKnownPeopleByFullOrUniqueFirstName() {
        val people = listOf(contact("Priya Nair", "priya"), contact("Ada Lindqvist", "ada"), contact("Leo Brandt", "leo"))
        val mentions = NoteAnalyzer.mentions(designNote, people, setOf("priya"))
        assertEquals(listOf("Ada Lindqvist"), mentions.map { it.name })
    }

    @Test
    fun noteDetectsRoleChangeAndIntroFollowUp() {
        val priya = contact("Priya Nair", "priya", role = "Product Lead", company = "Fernwood")
        val people = listOf(priya, contact("Ada Lindqvist", "ada"))
        val change = assertNotNull(NoteAnalyzer.roleChange(designNote, priya))
        assertEquals("Head of Product", change.role)
        assertEquals("Tidewater", change.company)
        assertEquals(listOf("Intro Priya → Ada Lindqvist"), NoteAnalyzer.followUps(designNote, priya, people))
    }

    @Test
    fun otherRolePhrasings() {
        val leo = contact("Leo Brandt", "leo")
        assertEquals("Northwind", NoteAnalyzer.roleChange("He is now Head of Design at Northwind.", leo)?.company)
        val joined = NoteAnalyzer.roleChange("Leo joined Acme Corp as VP Design, very excited.", leo)
        assertEquals("VP Design", joined?.role)
        assertEquals("Acme Corp", joined?.company)
        assertNull(NoteAnalyzer.roleChange("We had coffee and talked about climbing.", leo))
    }

    @Test
    fun parsesLinkedInExportWithPreamble() {
        val csv = """
            Notes:
            "When exporting your connection data, you may notice that some of the email addresses are missing."

            First Name,Last Name,URL,Email Address,Company,Position,Connected On
            Leo,Brandt,https://www.linkedin.com/in/leobrandt,,Northwind,"Head of Design, Platform",06 Nov 2025
            Ada,Lindqvist,https://www.linkedin.com/in/ada,ada@brightline.example,Brightline Ventures,Partner,2024-03-01
        """.trimIndent()
        val rows = LinkedInCsv.parse(csv)
        assertEquals(2, rows.size)
        assertEquals("Leo Brandt", rows[0].name)
        assertEquals("Head of Design, Platform", rows[0].position)
        assertEquals(LocalDate(2025, 11, 6), rows[0].connectedOn)
        assertEquals("ada@brightline.example", rows[1].email)
        assertEquals(LocalDate(2024, 3, 1), rows[1].connectedOn)
    }

    @Test
    fun parsesBusinessCard() {
        val card = BusinessCardParser.parse(
            listOf("Brightline Ventures", "Ada Lindqvist", "Partner", "ada@brightline.example", "+44 20 7946 0958", "www.brightline.example")
        )
        assertEquals("Ada Lindqvist", card.name)
        assertEquals("Partner", card.role)
        assertEquals("Brightline Ventures", card.company)
        assertEquals("ada@brightline.example", card.email)
        assertEquals("+44 20 7946 0958", card.phone)
    }
}
