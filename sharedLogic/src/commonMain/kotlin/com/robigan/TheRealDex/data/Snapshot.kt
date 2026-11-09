package com.robigan.TheRealDex.data

import com.robigan.TheRealDex.model.ContactSummary
import com.robigan.TheRealDex.model.PeopleGroup
import com.robigan.TheRealDex.model.SmartView
import com.robigan.TheRealDex.model.SourceProvider
import com.robigan.TheRealDex.model.StoredSignal

data class SourceConnection(val provider: SourceProvider, val connected: Boolean, val lastSyncAtMillis: Long?)

/** A consistent view of everything the list screens read. Re-emitted whenever any underlying table changes. */
data class Snapshot(
    val people: List<ContactSummary>,
    val groups: List<PeopleGroup>,
    val signals: List<StoredSignal>,
    val dismissed: Set<String>,
    val smartViews: List<SmartView>,
    val connections: List<SourceConnection>,
) {
    val byId: Map<String, ContactSummary> by lazy { people.associateBy { it.contact.id } }

    companion object {
        val EMPTY = Snapshot(emptyList(), emptyList(), emptyList(), emptySet(), emptyList(), emptyList())
    }
}
