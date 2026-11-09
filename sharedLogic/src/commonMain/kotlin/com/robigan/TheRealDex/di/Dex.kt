package com.robigan.TheRealDex.di

import com.robigan.TheRealDex.data.SourceSync
import com.robigan.TheRealDex.presentation.AppViewModel
import com.robigan.TheRealDex.presentation.ContactEditorViewModel
import com.robigan.TheRealDex.presentation.GroupDetailViewModel
import com.robigan.TheRealDex.presentation.GroupsViewModel
import com.robigan.TheRealDex.presentation.LogInteractionViewModel
import com.robigan.TheRealDex.presentation.PeopleViewModel
import com.robigan.TheRealDex.presentation.ProfileViewModel
import com.robigan.TheRealDex.presentation.SmartViewViewModel
import com.robigan.TheRealDex.presentation.TodayViewModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.context.startKoin
import org.koin.core.parameter.parametersOf

/**
 * Swift's entry point into the shared module (`Dex.shared` in Swift). Swift can't call Koin's reified `get()`,
 * so each screen model gets a typed factory here.
 */
object Dex : KoinComponent {
    private var started = false

    fun start() {
        if (started) return
        startKoin { modules(appModules()) }
        started = true
    }

    val sources: SourceSync get() = get()

    fun app(): AppViewModel = get()
    fun today(): TodayViewModel = get()
    fun people(): PeopleViewModel = get()
    fun groups(): GroupsViewModel = get()
    fun editor(): ContactEditorViewModel = get()
    fun profile(contactId: String): ProfileViewModel = get { parametersOf(contactId) }
    fun log(contactIds: List<String>, interactionId: String?): LogInteractionViewModel =
        get { parametersOf(contactIds, interactionId) }

    fun group(groupId: String): GroupDetailViewModel = get { parametersOf(groupId) }
    fun smartView(viewId: String): SmartViewViewModel = get { parametersOf(viewId) }
}
