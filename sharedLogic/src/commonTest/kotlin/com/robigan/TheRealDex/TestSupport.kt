package com.robigan.TheRealDex

import app.cash.sqldelight.db.SqlDriver
import com.robigan.TheRealDex.data.DexRepository
import com.robigan.TheRealDex.data.SourceSync
import com.robigan.TheRealDex.db.DexDatabase
import com.robigan.TheRealDex.domain.DateProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate

expect fun testDriver(): SqlDriver

class FixedDates(var date: LocalDate) : DateProvider {
    override fun today(): LocalDate = date
    override fun nowMillis(): Long = date.toEpochDays() * 86_400_000L + 12 * 3_600_000L
}

/** Monday 9 November 2026, the date shown in the design. */
val DESIGN_TODAY = LocalDate(2026, 11, 9)

class TestEnv(today: LocalDate = DESIGN_TODAY) {
    val dates = FixedDates(today)
    val db = DexDatabase(testDriver())
    val repo = DexRepository(db, dates, Dispatchers.Unconfined)
    val sync = SourceSync(repo, dates, Dispatchers.Unconfined)

    suspend fun summary(id: String) = repo.snapshot().first().byId.getValue(id)
}
