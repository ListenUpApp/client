package com.calypsan.listenup.client.testing

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay

/**
 * Phase A test harness. Composes a NavDisplay with both standard entry decorators
 * installed and exposes helpers for boundary tests.
 *
 * Designed for reuse:
 *  - Phase B extends with DeepLinkTestHarness for Intent-driven tests
 *  - Phase C extends with per-platform graph-coverage assertions
 */
class NavDisplayTestHarness(
    private val composeRule: ComposeContentTestRule,
) {
    private lateinit var backStack: NavBackStack<NavKey>

    /**
     * Compose a NavDisplay starting at [initialKey] with both standard decorators.
     * The optional [entryProviderBlock] lets callers register `entry<Key> { ... }`
     * handlers inside the entryProvider DSL; when absent, every NavKey renders an
     * empty Box.
     */
    fun composeBackStack(
        initialKey: NavKey,
        entryProviderBlock: (EntryProviderScope<NavKey>.() -> Unit)? = null,
    ) {
        composeRule.setContent {
            val stack = rememberNavBackStack(initialKey)
            SideEffect { backStack = stack }
            NavDisplay(
                backStack = stack,
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                entryProvider = entryProvider(
                    fallback = { key -> NavEntry(key) { Box(Modifier) } },
                ) {
                    if (entryProviderBlock != null) {
                        entryProviderBlock()
                    }
                },
            )
        }
    }

    /**
     * The current back-stack contents. Snapshot read; do not retain across recompositions.
     */
    val currentBackStack: List<NavKey> get() = backStack.toList()

    /**
     * Push a key onto the back stack.
     */
    fun navigate(key: NavKey) {
        composeRule.runOnIdle { backStack.add(key) }
    }

    /**
     * Pop the top entry.
     */
    fun pop() {
        composeRule.runOnIdle {
            if (backStack.isNotEmpty()) backStack.removeAt(backStack.lastIndex)
        }
    }

    /**
     * Drive a SavedStateRegistry save → re-instantiate → restore round-trip and
     * return the restored back-stack contents. Used to simulate process death.
     *
     * Implementation note: this method's body is filled in during Task 4 once
     * the SavedStateRegistry round-trip mechanism is verified against the
     * Robolectric Activity scaffold.
     */
    @Suppress("FunctionOnlyReturningConstant")
    fun simulateProcessDeath(): List<NavKey> {
        TODO("Implemented in Task 4 — process-death boundary test")
    }
}
