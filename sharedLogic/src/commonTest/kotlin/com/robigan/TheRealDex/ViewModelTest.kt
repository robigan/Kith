package com.robigan.TheRealDex

import com.robigan.TheRealDex.data.SampleData
import com.robigan.TheRealDex.model.SignalType
import com.robigan.TheRealDex.presentation.TodayState
import com.robigan.TheRealDex.presentation.TodayViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus

class ViewModelTest {
    @Test
    fun sampleDataMatchesTheDesignsTodayScreen() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val env = TestEnv()
        SampleData(env.repo, env.dates).load()
        val vm = TodayViewModel(env.repo, env.dates)
        val state: TodayState = vm.state.first { !it.isLoading }

        assertEquals("Monday, 9 November", state.dateLabel)
        assertEquals(listOf("Priya Nair", "Sam Whitfield", "Jules Moreau", "Mum"), state.rows.map { it.name })
        assertEquals(listOf("12 days overdue", "Due today", "Due tomorrow", "Due Wed"), state.rows.map { it.status })
        assertEquals("4 due", state.dueLabel)
        assertEquals(
            listOf(SignalType.BIRTHDAY, SignalType.NEW_ROLE, SignalType.MET_ANNIVERSARY),
            state.reasons.map { it.kind },
        )
        assertEquals("Birthday · Thursday", state.reasons[0].label)
        assertEquals("Maya Okafor turns 34. Gift idea saved: ceramics class.", state.reasons[0].text)
        assertEquals("Met 1 year ago", state.reasons[2].label)
        assertEquals("Coffee with Priya Nair", state.needsNote.single().title)
        assertEquals("FRI", state.needsNote.single().weekday)
        vm.clear()
        Dispatchers.resetMain()
    }
}

class ScreenModelTest {
    @Test
    fun profileShowsKeepInTouchTimelineAndFacts() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val env = TestEnv()
        SampleData(env.repo, env.dates).load()
        val priya = env.repo.allContacts().first { it.name == "Priya Nair" }
        val vm = com.robigan.TheRealDex.presentation.ProfileViewModel(priya.id, env.repo, env.dates)
        val state = vm.state.first { it.name.isNotEmpty() }

        assertEquals("Product Lead at Fernwood · London", state.subtitle)
        assertEquals(listOf("Close friends", "Product people", "London"), state.chips)
        assertEquals("Every month", state.keepInTouch.cadenceLabel)
        assertEquals("From Close friends", state.keepInTouch.cadenceSource)
        assertEquals("Last: coffee, 28 Sep", state.keepInTouch.lastLabel)
        assertEquals("12 days overdue", state.keepInTouch.status)
        assertEquals("42 of 30 days elapsed, 12 days overdue", state.keepInTouch.accessibilityLabel)
        assertEquals("Coffee with Priya Nair", state.timeline.first().title)
        assertTrue(state.timeline.first().needsNote)
        assertEquals("14 February", state.facts.first { it.label == "Birthday" }.value)
        vm.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun logSheetAnalysesTheDesignNoteAndSaves() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val env = TestEnv()
        SampleData(env.repo, env.dates).load()
        val priya = env.repo.allContacts().first { it.name == "Priya Nair" }
        val meeting = env.repo.unnotedMeetings(DESIGN_TODAY.minus(DatePeriod(days = 14)), DESIGN_TODAY).first().single()
        val vm = com.robigan.TheRealDex.presentation.LogInteractionViewModel(listOf(priya.id), meeting.id, env.repo, env.dates)
        vm.state.first { it.participants.isNotEmpty() }
        vm.setNote(
            "Caught up at Ozone. She took the Head of Product offer at Tidewater — starts January. " +
                "Wants an intro to Ada at Brightline. Anika landed in Sydney last week."
        )
        val state = vm.state.first { it.note.isNotEmpty() }
        assertEquals("Fri 6 Nov", state.dateLabel)
        assertEquals(listOf("Ada Lindqvist"), state.mentions.map { it.name })
        assertEquals("Priya: Head of Product at Tidewater", state.roleChange)
        assertEquals(listOf("Intro Priya → Ada Lindqvist"), state.suggestions)
        assertEquals("1 mo", state.nextOptions.single { it.isActive }.label)
        assertEquals("You'll be reminded on Sun 6 Dec.", state.nextLine)

        vm.addFollowUp(state.suggestions.single())
        vm.setRoleChangeAccepted(true)
        assertTrue(vm.save())

        val saved = env.summary(priya.id)
        assertEquals("Tidewater", saved.contact.company)
        assertEquals("Head of Product", saved.contact.role)
        assertEquals(kotlinx.datetime.LocalDate(2026, 12, 6), saved.due)
        assertEquals(listOf("Intro Priya → Ada Lindqvist"), env.repo.followUpsFor(priya.id).first().map { it.text })
        vm.clear()
        Dispatchers.resetMain()
    }
}
