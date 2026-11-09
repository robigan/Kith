package com.robigan.TheRealDex.di

import com.robigan.TheRealDex.data.DexRepository
import com.robigan.TheRealDex.data.SampleData
import com.robigan.TheRealDex.data.SourceSync
import com.robigan.TheRealDex.db.DexDatabase
import com.robigan.TheRealDex.domain.DateProvider
import com.robigan.TheRealDex.domain.SystemDateProvider
import com.robigan.TheRealDex.presentation.AppViewModel
import com.robigan.TheRealDex.presentation.ContactEditorViewModel
import com.robigan.TheRealDex.presentation.GroupDetailViewModel
import com.robigan.TheRealDex.presentation.GroupsViewModel
import com.robigan.TheRealDex.presentation.LogInteractionViewModel
import com.robigan.TheRealDex.presentation.PeopleViewModel
import com.robigan.TheRealDex.presentation.ProfileViewModel
import com.robigan.TheRealDex.presentation.SmartViewViewModel
import com.robigan.TheRealDex.presentation.TodayViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

/** Provides the SqlDriver for the platform. */
expect val platformModule: Module

val dataModule = module {
    single { DexDatabase(get()) }
    singleOf(::SystemDateProvider) bind DateProvider::class
    single { DexRepository(get(), get()) }
    single { SourceSync(get(), get()) }
    singleOf(::SampleData)
}

val viewModelModule = module {
    factoryOf(::AppViewModel)
    factoryOf(::TodayViewModel)
    factoryOf(::PeopleViewModel)
    factoryOf(::GroupsViewModel)
    factoryOf(::ContactEditorViewModel)
    factory { (contactId: String) -> ProfileViewModel(contactId, get(), get()) }
    factory { (contactIds: List<String>, interactionId: String?) ->
        LogInteractionViewModel(
            contactIds,
            interactionId,
            get(),
            get()
        )
    }
    factory { (groupId: String) -> GroupDetailViewModel(groupId, get(), get()) }
    factory { (viewId: String) -> SmartViewViewModel(viewId, get(), get()) }
}

fun appModules(): List<Module> = listOf(platformModule, dataModule, viewModelModule)
