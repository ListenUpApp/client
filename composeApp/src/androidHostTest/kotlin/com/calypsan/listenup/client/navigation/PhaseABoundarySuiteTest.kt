package com.calypsan.listenup.client.navigation

import androidx.compose.ui.test.junit4.createComposeRule
import com.calypsan.listenup.client.testing.NavDisplayTestHarness
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * Phase A boundary suite. Tests that exercise the structural invariants the
 * Phase A migration must satisfy — covered incrementally as each task lands:
 *
 *  - Task 4: process-death back-stack survival via `rememberNavBackStack`.
 *  - Task 5: every `NavDisplay` invocation site installs both standard entry decorators.
 *  - Task 6: per-entry ViewModel scoping (VM-per-entry + cleared-on-pop).
 *
 * `@Config(sdk = [35])` pins Robolectric to the highest SDK its 4.15.1 release
 * supports; the project's compileSdk = 37 outpaces Robolectric's currently-shipped
 * SDK shadows. Bump in lockstep when Robolectric ships an SDK 37 build.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PhaseABoundarySuiteTest {

    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `back stack survives process death via SavedStateRegistry round-trip`() {
        val harness = NavDisplayTestHarness(composeRule)

        // Build a non-trivial back stack against the real production routes.
        harness.composeBackStack(Shell)
        harness.navigate(BookDetail(bookId = "book-X"))
        harness.navigate(BookEdit(bookId = "book-X"))

        val expected = listOf(Shell, BookDetail("book-X"), BookEdit("book-X"))
        assertEquals(expected, harness.currentBackStack)

        // Simulate process death and assert the stack restores.
        val restored = harness.simulateProcessDeath()
        assertEquals(expected, restored, "Back stack did not survive process-death round-trip")
    }
}
