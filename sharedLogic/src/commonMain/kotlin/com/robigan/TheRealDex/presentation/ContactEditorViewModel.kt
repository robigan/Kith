package com.robigan.TheRealDex.presentation

import com.robigan.TheRealDex.data.ContactInput
import com.robigan.TheRealDex.data.DexRepository
import com.robigan.TheRealDex.domain.BusinessCardParser
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

/** Add / edit contact form and business-card prefill (FR-026, FR-070…FR-072). */
class ContactEditorViewModel(private val repository: DexRepository) : ViewModel() {
    val groups: StateFlow<List<GroupOption>> = repository.snapshot().map { snap ->
        snap.groups.map { GroupOption(it.id, it.name) }
    }.toState(emptyList())

    suspend fun load(contactId: String): ContactInput? = repository.contactInput(contactId)

    /** Creates the contact when [contactId] is null. Returns the contact's id. */
    suspend fun save(contactId: String?, input: ContactInput): String {
        if (contactId == null) return repository.createContact(input)
        repository.updateContact(contactId, input)
        return contactId
    }

    suspend fun delete(contactId: String) = repository.deleteContact(contactId)

    fun fromBusinessCard(lines: List<String>): ContactInput {
        val card = BusinessCardParser.parse(lines)
        return ContactInput(
            name = card.name,
            role = card.role.orEmpty(),
            company = card.company.orEmpty(),
            email = card.email.orEmpty(),
            phone = card.phone.orEmpty(),
        )
    }
}
