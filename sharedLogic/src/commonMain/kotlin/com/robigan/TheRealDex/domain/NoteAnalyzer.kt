package com.robigan.TheRealDex.domain

import com.robigan.TheRealDex.model.Contact

data class Mention(val contactId: String, val name: String)

data class RoleChange(val contactId: String, val name: String, val role: String?, val company: String) {
    val summary: String get() = if (role != null) "$role at $company" else "New role at $company"
}

/**
 * Reads an interaction note and finds contacts it mentions, job changes for the person it's about (FR-045),
 * and follow-up suggestions such as introductions (FR-047).
 */
object NoteAnalyzer {
    private const val WORD = "[A-Z][\\w&'\\-]*(?:\\.[\\w&'\\-]+)*"
    private const val NAME = "$WORD(?: $WORD)*"
    private const val STOP = "(?=\\s*(?:[.,;:!?—–]|\\s-\\s|$| starts| starting| next| this| last| from| in | on ))"

    private val rolePatterns: List<Pair<Regex, (MatchResult) -> Pair<String?, String>>> = listOf(
        Regex("(?:took|accepted|got) (?:the |an? )?(?:(.+?) )?(?:offer|job|role|position) (?:at|with) ($NAME)$STOP") to
                { m -> m.groupValues[1].ifBlank { null } to m.groupValues[2] },
        Regex("(?:is now|now works as|became|has become|started as|promoted to|moving to be) (?:the |an? )?([A-Z].*?) at ($NAME)$STOP") to
                { m -> m.groupValues[1] to m.groupValues[2] },
        Regex("joined ($NAME) as (?:the |an? )?(.+?)$STOP") to
                { m -> m.groupValues[2] to m.groupValues[1] },
        Regex("new (?:job|role|position) at ($NAME)$STOP") to
                { m -> null to m.groupValues[1] },
    )

    private val introPattern =
        Regex("(?i:wants|asked for|asked me for|needs|would love|keen on|looking for) (?i:an? )?(?i:intro|introduction)(?:s)? (?i:to) ($NAME)")

    fun mentions(note: String, contacts: List<Contact>, excludeIds: Set<String>): List<Mention> {
        if (note.isBlank()) return emptyList()
        val firstNameCounts = contacts.groupingBy { it.firstName.lowercase() }.eachCount()
        return contacts.filter { c ->
            c.id !in excludeIds && c.name.isNotBlank() && (
                    containsWord(note, c.name, ignoreCase = true) ||
                            (c.name.contains(' ') && c.firstName.length >= 3 && firstNameCounts[c.firstName.lowercase()] == 1 &&
                                    containsWord(note, c.firstName, ignoreCase = false))
                    )
        }.map { Mention(it.id, it.name) }
    }

    fun roleChange(note: String, subject: Contact?): RoleChange? {
        if (subject == null || note.isBlank()) return null
        for ((regex, extract) in rolePatterns) {
            val match = regex.find(note) ?: continue
            val (role, company) = extract(match)
            val cleanRole = role?.trim()?.removeSuffix(" offer")?.ifBlank { null }
            val cleanCompany = company.trim()
            if (cleanCompany.equals(subject.company, ignoreCase = true) && cleanRole.equals(
                    subject.role,
                    ignoreCase = true
                )
            ) return null
            return RoleChange(subject.id, subject.name, cleanRole, cleanCompany)
        }
        return null
    }

    fun followUps(note: String, subject: Contact?, contacts: List<Contact>): List<String> {
        if (subject == null) return emptyList()
        return introPattern.findAll(note).mapNotNull { m ->
            val target = m.groupValues[1]
            val resolved = contacts.firstOrNull { it.name.equals(target, ignoreCase = true) }
                ?: contacts.singleOrNull {
                    it.firstName.equals(
                        target.substringBefore(' '),
                        ignoreCase = true
                    ) && it.id != subject.id
                }
            "Intro ${subject.firstName} → ${resolved?.name ?: target}"
        }.distinct().toList()
    }

    private fun containsWord(text: String, word: String, ignoreCase: Boolean): Boolean {
        val options = if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()
        return Regex("(?<![\\w])" + Regex.escape(word) + "(?![\\w])", options).containsMatchIn(text)
    }
}
