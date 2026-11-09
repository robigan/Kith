package com.robigan.TheRealDex.presentation

import com.robigan.TheRealDex.model.Cadence
import com.robigan.TheRealDex.model.ContactSummary
import com.robigan.TheRealDex.model.InteractionType
import com.robigan.TheRealDex.model.PeopleFilter
import com.robigan.TheRealDex.model.SignalType
import com.robigan.TheRealDex.model.SourceProvider
import com.robigan.TheRealDex.model.Tint

data class Avatar(val initials: String, val tint: Tint)

fun ContactSummary.avatar(): Avatar = Avatar(contact.initials, contact.tint)

data class PersonRow(val id: String, val name: String, val avatar: Avatar, val subtitle: String, val isDue: Boolean)

// Today

data class KeepInTouchRow(
    val contactId: String,
    val name: String,
    val avatar: Avatar,
    val status: String,
    val isUrgent: Boolean,
    val cadenceLabel: String,
    val isDone: Boolean,
    val doneLabel: String,
)

data class ReasonCard(
    val key: String,
    val kind: SignalType,
    val contactId: String,
    val avatar: Avatar,
    val label: String,
    val text: String,
)

data class NeedsNoteCard(
    val interactionId: String,
    val contactIds: List<String>,
    val weekday: String,
    val day: String,
    val title: String,
    val subtitle: String,
)

data class TodayState(
    val dateLabel: String,
    val rows: List<KeepInTouchRow>,
    val dueLabel: String,
    val reasons: List<ReasonCard>,
    val needsNote: List<NeedsNoteCard>,
    val hasPeople: Boolean,
    val isLoading: Boolean,
) {
    companion object {
        val EMPTY = TodayState("", emptyList(), "", emptyList(), emptyList(), hasPeople = true, isLoading = true)
    }
}

// People

data class FilterChip(val filter: PeopleFilter, val label: String, val count: Int, val isActive: Boolean)

data class PeopleSection(val letter: String, val rows: List<PersonRow>)

data class PeopleState(
    val query: String,
    val filters: List<FilterChip>,
    val sections: List<PeopleSection>,
    val letters: List<String>,
    val totalCount: Int,
) {
    companion object {
        val EMPTY = PeopleState("", emptyList(), emptyList(), emptyList(), 0)
    }
}

// Profile

data class KeepInTouchCard(
    val cadence: Cadence?,
    val cadenceLabel: String,
    /** e.g. "From Close friends" when the cadence is inherited from a group. */
    val cadenceSource: String?,
    val lastLabel: String,
    val status: String,
    val isUrgent: Boolean,
    /** 0…1 share of the cadence elapsed since the last interaction; above 1 means overdue. */
    val progress: Double,
    val accessibilityLabel: String,
)

data class TimelineEntry(
    val id: String,
    val type: InteractionType,
    val title: String,
    val whenLabel: String,
    val note: String,
    val needsNote: Boolean,
)

data class Fact(val label: String, val value: String)

data class FollowUpRow(val id: String, val text: String, val isDone: Boolean)

data class ProfileState(
    val exists: Boolean,
    val id: String,
    val name: String,
    val avatar: Avatar,
    val subtitle: String,
    val chips: List<String>,
    val starred: Boolean,
    val phone: String?,
    val email: String?,
    val keepInTouch: KeepInTouchCard,
    val timeline: List<TimelineEntry>,
    val facts: List<Fact>,
    val followUps: List<FollowUpRow>,
    val sources: List<String>,
) {
    companion object {
        fun empty(id: String) = ProfileState(
            exists = true, id = id, name = "", avatar = Avatar("", Tint.SKY), subtitle = "", chips = emptyList(), starred = false,
            phone = null, email = null,
            keepInTouch = KeepInTouchCard(null, "", null, "", "", false, 0.0, ""),
            timeline = emptyList(), facts = emptyList(), followUps = emptyList(), sources = emptyList(),
        )
    }
}

// Log interaction

data class PersonChip(val id: String, val name: String, val avatar: Avatar)

data class NextOption(val cadence: Cadence, val label: String, val isActive: Boolean)

data class LogState(
    val type: InteractionType,
    val types: List<InteractionType>,
    val title: String?,
    val participants: List<PersonChip>,
    val epochDay: Long,
    val dateLabel: String,
    val note: String,
    val mentions: List<PersonChip>,
    val roleChange: String?,
    val roleChangeAccepted: Boolean,
    val nextOptions: List<NextOption>,
    val nextLine: String,
    val suggestions: List<String>,
    val followUps: List<String>,
    val canSave: Boolean,
)

// Groups

data class GroupRow(
    val id: String,
    val name: String,
    val count: Int,
    val cadenceLabel: String,
    val dueCount: Int,
    val faces: List<Avatar>,
)

data class SmartViewRow(val id: String, val name: String, val code: String, val tint: Tint, val count: Int)

data class SourceRow(
    val provider: SourceProvider,
    val name: String,
    val status: String,
    val isConnected: Boolean,
    val isAvailable: Boolean,
)

data class GroupOption(val id: String, val name: String)

data class GroupsState(
    val groups: List<GroupRow>,
    val views: List<SmartViewRow>,
    val sources: List<SourceRow>,
    val tags: List<String>,
) {
    val groupOptions: List<GroupOption> get() = groups.map { GroupOption(it.id, it.name) }

    companion object {
        val EMPTY = GroupsState(emptyList(), emptyList(), emptyList(), emptyList())
    }
}

data class GroupDetailState(
    val exists: Boolean,
    val name: String,
    val cadence: Cadence?,
    val cadenceLabel: String,
    val members: List<PersonRow>,
) {
    companion object {
        val EMPTY = GroupDetailState(true, "", null, "", emptyList())
    }
}

data class SmartViewState(
    val exists: Boolean,
    val name: String,
    val code: String,
    val tint: Tint,
    val criteria: String,
    val people: List<PersonRow>,
) {
    companion object {
        val EMPTY = SmartViewState(true, "", "", Tint.SKY, "", emptyList())
    }
}
