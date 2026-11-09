package com.robigan.TheRealDex.data

import com.robigan.TheRealDex.db.ContactRecord
import com.robigan.TheRealDex.db.InteractionRecord
import com.robigan.TheRealDex.domain.DateProvider
import com.robigan.TheRealDex.model.Cadence
import com.robigan.TheRealDex.model.InteractionOrigin
import com.robigan.TheRealDex.model.InteractionType
import com.robigan.TheRealDex.model.SignalType
import com.robigan.TheRealDex.model.SourceProvider
import com.robigan.TheRealDex.model.Tags
import com.robigan.TheRealDex.model.Tint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus

/** The people from the design, placed relative to today so the Today screen looks like the mock-up. */
class SampleData(private val repository: DexRepository, private val dates: DateProvider) {

    suspend fun load() = withContext(Dispatchers.Default) {
        val db = repository.database
        val today = dates.today()
        fun ago(days: Int) = today.minus(DatePeriod(days = days))
        val ids = mutableMapOf<String, String>()

        fun person(
            key: String,
            name: String,
            tint: Tint,
            role: String? = null,
            company: String? = null,
            location: String? = null,
            relationship: String? = null,
            howWeMet: String? = null,
            metOn: LocalDate? = null,
            family: String? = null,
            birthday: LocalDate? = null,
            birthYear: Int? = null,
            workHistory: String? = null,
            email: String? = null,
            starred: Boolean = false,
            cadence: Cadence? = null,
            tags: List<String> = emptyList(),
            gifts: List<String> = emptyList(),
        ) {
            val id = repository.newId()
            ids[key] = id
            db.contactQueries.insert(
                ContactRecord(
                    id,
                    name,
                    role,
                    company,
                    location,
                    relationship,
                    howWeMet,
                    metOn?.toDb(),
                    family,
                    birthday?.month?.number?.toLong(),
                    birthday?.day?.toLong(),
                    birthYear?.toLong(),
                    workHistory,
                    email,
                    null,
                    tint.name,
                    starred.toDb(),
                    cadence?.code,
                    null,
                    ago(400).toDb(),
                )
            )
            tags.forEach { db.contactQueries.insertTag(id, it) }
            gifts.forEachIndexed { i, g -> db.contactQueries.insertGift(repository.newId(), id, g, i.toLong()) }
        }

        fun log(
            keys: List<String>, type: InteractionType, date: LocalDate, note: String = "", title: String? = null,
            origin: InteractionOrigin = InteractionOrigin.MANUAL, location: String? = null, minutes: Int? = null,
        ) {
            val id = repository.newId()
            db.interactionQueries.insert(
                InteractionRecord(
                    id,
                    type.name,
                    date.toDb(),
                    title,
                    note,
                    origin.name,
                    null,
                    location,
                    minutes?.toLong(),
                    dates.nowMillis()
                )
            )
            keys.forEach { db.interactionQueries.insertParticipant(id, ids.getValue(it)) }
        }

        fun group(name: String, cadence: Cadence?, members: List<String>) {
            val id = repository.newId()
            db.groupQueries.insert(id, name, cadence?.code, dates.nowMillis())
            members.forEach { db.groupQueries.insertMember(id, ids.getValue(it)) }
        }

        db.transaction {
            val maya = today.plus(DatePeriod(days = 3))
            person(
                "priya",
                "Priya Nair",
                Tint.PEACH,
                role = "Product Lead",
                company = "Fernwood",
                location = "London",
                howWeMet = "Product offsite in Lisbon, March 2022 — via Leo Brandt",
                metOn = LocalDate(2022, 3, 18),
                family = "Partner Dev · sister Anika (moving to Sydney)",
                birthday = LocalDate(2000, 2, 14),
                workHistory = "Monzo · Deliveroo",
                email = "priya@fernwood.example",
                starred = true,
                tags = listOf(Tags.WORK),
                gifts = listOf("Ottolenghi “Comfort”", "Bouldering day pass"),
            )
            person(
                "sam",
                "Sam Whitfield",
                Tint.SAGE,
                relationship = "Brother-in-law",
                cadence = Cadence.TWO_WEEKS,
                tags = listOf(Tags.FAMILY)
            )
            person(
                "jules",
                "Jules Moreau",
                Tint.SKY,
                role = "Engineer",
                company = "Halcyon",
                cadence = Cadence.QUARTERLY,
                tags = listOf(Tags.WORK)
            )
            person("mum", "Mum", Tint.ROSE, relationship = "Family", starred = true, tags = listOf(Tags.FAMILY))
            person(
                "ada",
                "Ada Lindqvist",
                Tint.SAND,
                role = "Partner",
                company = "Brightline Ventures",
                tags = listOf(Tags.WORK)
            )
            person(
                "alex",
                "Alex Chen",
                Tint.SKY,
                relationship = "College roommate",
                location = "Melbourne",
                starred = true
            )
            person("ben", "Ben Hartley", Tint.SAGE, relationship = "Climbing crew")
            person(
                "leo", "Leo Brandt", Tint.SKY, role = "Head of Design", company = "Northwind", location = "London",
                workHistory = "Halcyon (Design Lead)", tags = listOf(Tags.WORK),
            )
            person(
                "maya", "Maya Okafor", Tint.LILAC, birthday = maya, birthYear = maya.year - 34, starred = true,
                howWeMet = "Met at a conference in Berlin", gifts = listOf("Ceramics class"),
            )
            person(
                "tomas", "Tomás Reyes", Tint.SAGE, howWeMet = "Met at the Lisbon climbing meetup",
                metOn = today.minus(DatePeriod(years = 1)),
            )

            group("Close friends", Cadence.MONTHLY, listOf("priya", "alex", "maya"))
            group("Family", Cadence.WEEKLY, listOf("mum", "sam"))
            group("Job search", Cadence.TWO_WEEKS, listOf("leo", "ada", "jules"))
            group("Founders", Cadence.QUARTERLY, listOf("ada"))
            group("Climbing crew", Cadence.MONTHLY, listOf("tomas", "ben"))
            group("Product people", null, listOf("priya", "leo"))

            // Priya: monthly cadence, last coffee 42 days ago → 12 days overdue.
            log(listOf("priya"), InteractionType.NOTE, ago(101), "Loves Ottolenghi — book for her birthday.")
            log(listOf("priya"), InteractionType.CALL, ago(83), "Congratulated her on the promotion.")
            log(
                listOf("priya"),
                InteractionType.EVENT,
                ago(68),
                "Sat together at the AI panel.",
                title = "Mind the Product, London"
            )
            log(
                listOf("priya"),
                InteractionType.EMAIL,
                ago(56),
                "Sent my talk slides. She will share with her team.",
                title = "Email · Re: Mind the Product"
            )
            log(
                listOf("priya"), InteractionType.COFFEE, ago(42),
                "Thinking about leaving Fernwood next year. Wants intros to climate-tech founders. Mentioned her sister Anika is moving to Sydney.",
            )
            log(
                listOf("priya"),
                InteractionType.MEETING,
                ago(3),
                title = "Coffee with Priya Nair",
                origin = InteractionOrigin.CALENDAR,
                location = "Ozone, Old Street",
                minutes = 45,
            )
            log(listOf("sam"), InteractionType.CALL, ago(14), "Planned the summer trip.")
            log(
                listOf("jules"),
                InteractionType.MEETING,
                today.plus(DatePeriod(days = 1)).minus(DatePeriod(months = 3)),
                "Lunch near Halcyon."
            )
            log(listOf("mum"), InteractionType.CALL, ago(5), "Sunday call.")
            log(listOf("ada"), InteractionType.COFFEE, ago(6), "Brightline is raising Fund III.")
            log(listOf("alex"), InteractionType.CALL, ago(9), "Video call — new flat in Fitzroy.")
            log(listOf("ben", "tomas"), InteractionType.EVENT, ago(10), "Bouldering session.", title = "Climbing night")
            log(listOf("leo"), InteractionType.MESSAGE, ago(4), "Congrats on the new role!")
            log(listOf("maya"), InteractionType.COFFEE, ago(5), "Planning a pottery weekend.")

            repository.addSignalTx(
                SignalType.NEW_ROLE, ids.getValue("leo"), ago(1), "Leo Brandt is now Head of Design at Northwind.",
                SourceProvider.LINKEDIN, "sample:leo",
            )
            if (db.smartViewQueries.selectAll().executeAsList().none { it.code == "LDN" }) {
                repository.insertView("Lives in London", "LDN", Tint.SAGE, location = "London")
                repository.insertView("Met at conferences", "EVT", Tint.SAND, metContext = "conference")
            }
        }
    }
}
