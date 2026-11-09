package com.robigan.TheRealDex.presentation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Base for screen models consumed from Swift. State is a StateFlow (an AsyncSequence via SKIE) that only runs
 * while Swift is iterating it; actions launch on the main dispatcher and write through the repository.
 */
abstract class ViewModel {
    protected val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    protected fun <T> Flow<T>.toState(initial: T): StateFlow<T> =
        stateIn(scope, SharingStarted.WhileSubscribed(5_000), initial)

    protected fun launch(block: suspend CoroutineScope.() -> Unit) {
        scope.launch(block = block)
    }

    /** Cancels pending work. Call when the owning Swift view goes away for good. */
    fun clear() = scope.cancel()
}
