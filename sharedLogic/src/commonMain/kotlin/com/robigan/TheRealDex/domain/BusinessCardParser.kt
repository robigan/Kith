package com.robigan.TheRealDex.domain

data class ParsedCard(
    val name: String,
    val role: String?,
    val company: String?,
    val email: String?,
    val phone: String?,
)

/** Turns OCR'd business-card lines into contact fields (FR-026). Heuristic: first name-like line is the person. */
object BusinessCardParser {
    private val email = Regex("[\\w.+\\-]+@[\\w\\-]+(?:\\.[\\w\\-]+)+")
    private val phone = Regex("\\+?[\\d][\\d ()\\-.]{6,}\\d")
    private val url = Regex("(?i)(?:https?://|www\\.)\\S+|\\S+\\.(?:com|io|co|org|net|dev|app)(?:/\\S*)?")
    private val roleWords = Regex(
        "(?i)\\b(ceo|cto|cfo|coo|founder|co-founder|director|manager|head|lead|engineer|designer|partner|president|" +
                "vice|vp|officer|consultant|analyst|associate|developer|architect|principal|chief|owner|specialist|advisor)\\b"
    )
    private val companyWords =
        Regex("(?i)\\b(inc|ltd|llc|gmbh|ag|sa|plc|corp|co\\.|company|group|labs|ventures|studio|partners)\\b")

    fun parse(lines: List<String>): ParsedCard {
        val clean = lines.map { it.trim() }.filter { it.isNotEmpty() }
        val foundEmail = clean.firstNotNullOfOrNull { email.find(it)?.value }
        val foundPhone =
            clean.firstNotNullOfOrNull { l -> phone.find(l)?.value?.takeIf { d -> d.count(Char::isDigit) >= 7 } }
        val text = clean.filterNot { email.containsMatchIn(it) || phone.containsMatchIn(it) || url.containsMatchIn(it) }

        val role = text.firstOrNull { roleWords.containsMatchIn(it) }
        val name = text.firstOrNull { it != role && looksLikeName(it) } ?: text.firstOrNull { it != role } ?: ""
        val company = text.firstOrNull { it != role && it != name && companyWords.containsMatchIn(it) }
            ?: foundEmail?.substringAfter('@')?.substringBefore('.')
                ?.takeIf { it !in setOf("gmail", "outlook", "hotmail", "icloud", "yahoo", "proton", "me") }
                ?.replaceFirstChar { it.uppercase() }
            ?: text.firstOrNull { it != role && it != name }
        return ParsedCard(name, role, company, foundEmail, foundPhone)
    }

    private fun looksLikeName(line: String): Boolean {
        val words = line.split(' ').filter { it.isNotBlank() }
        return words.size in 2..4 && words.all { w ->
            w.first().isUpperCase() && w.all { it.isLetter() || it == '-' || it == '\'' || it == '.' }
        } &&
                !roleWords.containsMatchIn(line) && !companyWords.containsMatchIn(line)
    }
}
