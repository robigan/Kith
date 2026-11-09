package com.robigan.TheRealDex

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.inMemoryDriver
import com.robigan.TheRealDex.db.DexDatabase

actual fun testDriver(): SqlDriver = inMemoryDriver(DexDatabase.Schema)
