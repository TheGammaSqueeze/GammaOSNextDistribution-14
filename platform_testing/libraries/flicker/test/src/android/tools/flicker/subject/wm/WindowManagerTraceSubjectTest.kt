/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.tools.flicker.subject.wm

import android.tools.Cache
import android.tools.CleanFlickerEnvironmentRuleWithDataStore
import android.tools.traces.component.ComponentNameMatcher
import android.tools.utils.TestComponents
import android.tools.utils.assertThatErrorContainsDebugInfo
import android.tools.utils.assertThrows
import android.tools.utils.getWmTraceReaderFromAsset
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.ClassRule
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * Contains [WindowManagerTraceSubject] tests. To run this test: `atest
 * FlickerLibTest:WindowManagerTraceSubjectTest`
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class WindowManagerTraceSubjectTest {
    private val chromeTraceReader =
        getWmTraceReaderFromAsset("wm_trace_openchrome.pb", legacyTrace = true)
    private val chromeTrace
        get() = chromeTraceReader.readWmTrace() ?: error("Unable to read WM trace")
    private val imeTraceReader = getWmTraceReaderFromAsset("wm_trace_ime.pb", legacyTrace = true)
    private val imeTrace = imeTraceReader.readWmTrace() ?: error("Unable to read WM trace")

    @Before
    fun before() {
        Cache.clear()
    }

    @Test
    fun testVisibleAppWindowForRange() {
        WindowManagerTraceSubject(chromeTrace, chromeTraceReader)
            .isAppWindowOnTop(TestComponents.LAUNCHER)
            .isAboveAppWindowVisible(TestComponents.SCREEN_DECOR_OVERLAY)
            .forElapsedTimeRange(9213763541297L, 9215536878453L)

        WindowManagerTraceSubject(chromeTrace, chromeTraceReader)
            .isAppWindowOnTop(TestComponents.LAUNCHER)
            .isAppWindowInvisible(TestComponents.CHROME_SPLASH_SCREEN)
            .isAboveAppWindowVisible(TestComponents.SCREEN_DECOR_OVERLAY)
            .then()
            .isAppWindowOnTop(TestComponents.CHROME_SPLASH_SCREEN)
            .isAppWindowInvisible(TestComponents.LAUNCHER)
            .isAboveAppWindowVisible(TestComponents.SCREEN_DECOR_OVERLAY)
            .then()
            .isAppWindowOnTop(TestComponents.CHROME_FIRST_RUN)
            .isAppWindowInvisible(TestComponents.LAUNCHER)
            .isAboveAppWindowVisible(TestComponents.SCREEN_DECOR_OVERLAY)
            .forElapsedTimeRange(9215551505798L, 9216093628925L)
    }

    @Test
    fun testCanTransitionInAppWindow() {
        WindowManagerTraceSubject(chromeTrace, chromeTraceReader)
            .isAppWindowOnTop(TestComponents.LAUNCHER)
            .isAboveAppWindowVisible(TestComponents.SCREEN_DECOR_OVERLAY)
            .then()
            .isAppWindowOnTop(TestComponents.CHROME_SPLASH_SCREEN)
            .isAboveAppWindowVisible(TestComponents.SCREEN_DECOR_OVERLAY)
            .then()
            .isAppWindowOnTop(TestComponents.CHROME_FIRST_RUN)
            .isAboveAppWindowVisible(TestComponents.SCREEN_DECOR_OVERLAY)
            .forAllEntries()
    }

    @Test
    fun testCanDetectTransitionWithOptionalValue() {
        val reader = getWmTraceReaderFromAsset("wm_trace_open_from_overview.pb", legacyTrace = true)
        val trace = reader.readWmTrace() ?: error("Unable to read WM trace")
        val subject = WindowManagerTraceSubject(trace, reader)
        subject
            .isAppWindowOnTop(TestComponents.LAUNCHER)
            .then()
            .isAppWindowOnTop(ComponentNameMatcher.SNAPSHOT)
            .then()
            .isAppWindowOnTop(TestComponents.CHROME_FIRST_RUN)
    }

    @Test
    fun testCanTransitionInAppWindow_withOptional() {
        WindowManagerTraceSubject(chromeTrace, chromeTraceReader)
            .isAppWindowOnTop(TestComponents.LAUNCHER)
            .isAboveAppWindowVisible(TestComponents.SCREEN_DECOR_OVERLAY)
            .then()
            .isAppWindowOnTop(TestComponents.CHROME_SPLASH_SCREEN)
            .isAboveAppWindowVisible(TestComponents.SCREEN_DECOR_OVERLAY)
            .then()
            .isAppWindowOnTop(TestComponents.CHROME_FIRST_RUN)
            .isAboveAppWindowVisible(TestComponents.SCREEN_DECOR_OVERLAY)
            .forAllEntries()
    }

    @Test
    fun testCanInspectBeginning() {
        WindowManagerTraceSubject(chromeTrace, chromeTraceReader)
            .first()
            .isAppWindowOnTop(TestComponents.LAUNCHER)
            .containsAboveAppWindow(TestComponents.SCREEN_DECOR_OVERLAY)
    }

    @Test
    fun testCanInspectAppWindowOnTop() {
        WindowManagerTraceSubject(chromeTrace, chromeTraceReader)
            .first()
            .isAppWindowOnTop(TestComponents.LAUNCHER)

        val failure =
            assertThrows<AssertionError> {
                WindowManagerTraceSubject(chromeTrace, chromeTraceReader)
                    .first()
                    .isAppWindowOnTop(TestComponents.IMAGINARY)
            }
        Truth.assertThat(failure).hasMessageThat().contains("ImaginaryWindow")
    }

    @Test
    fun testCanInspectEnd() {
        WindowManagerTraceSubject(chromeTrace, chromeTraceReader)
            .last()
            .isAppWindowOnTop(TestComponents.CHROME_FIRST_RUN)
            .containsAboveAppWindow(TestComponents.SCREEN_DECOR_OVERLAY)
    }

    @Test
    fun testCanTransitionNonAppWindow() {
        WindowManagerTraceSubject(imeTrace, imeTraceReader)
            .skipUntilFirstAssertion()
            .isNonAppWindowInvisible(ComponentNameMatcher.IME)
            .then()
            .isNonAppWindowVisible(ComponentNameMatcher.IME)
            .forAllEntries()
    }

    @Test(expected = AssertionError::class)
    fun testCanDetectOverlappingWindows() {
        WindowManagerTraceSubject(imeTrace, imeTraceReader)
            .doNotOverlap(
                ComponentNameMatcher.IME,
                ComponentNameMatcher.NAV_BAR,
                TestComponents.IME_ACTIVITY
            )
            .forAllEntries()
    }

    @Test
    fun testCanTransitionAboveAppWindow() {
        WindowManagerTraceSubject(imeTrace, imeTraceReader)
            .skipUntilFirstAssertion()
            .isAboveAppWindowInvisible(ComponentNameMatcher.IME)
            .then()
            .isAboveAppWindowVisible(ComponentNameMatcher.IME)
            .forAllEntries()
    }

    @Test
    fun testCanTransitionBelowAppWindow() {
        val reader = getWmTraceReaderFromAsset("wm_trace_open_app_cold.pb", legacyTrace = true)
        val trace = reader.readWmTrace() ?: error("Unable to read WM trace")
        WindowManagerTraceSubject(trace, reader)
            .skipUntilFirstAssertion()
            .isBelowAppWindowVisible(TestComponents.WALLPAPER)
            .then()
            .isBelowAppWindowInvisible(TestComponents.WALLPAPER)
            .forAllEntries()
    }

    @Test
    fun testCanDetectVisibleWindowsMoreThanOneConsecutiveEntry() {
        val reader =
            getWmTraceReaderFromAsset("wm_trace_valid_visible_windows.pb", legacyTrace = true)
        val trace = reader.readWmTrace() ?: error("Unable to read WM trace")
        WindowManagerTraceSubject(trace, reader)
            .visibleWindowsShownMoreThanOneConsecutiveEntry()
            .forAllEntries()
    }

    @Test
    fun testCanAssertWindowStateSequence() {
        val componentMatcher =
            ComponentNameMatcher.unflattenFromString(
                "com.android.chrome/org.chromium.chrome.browser.firstrun.FirstRunActivity"
            )
        val windowStates =
            WindowManagerTraceSubject(chromeTrace, chromeTraceReader).windowStates(componentMatcher)

        val visibilityChange =
            windowStates.zipWithNext { current, next ->
                current.windowState.isVisible != next.windowState.isVisible
            }

        Truth.assertWithMessage("Visibility should have changed only 1x in the trace")
            .that(visibilityChange.count { it })
            .isEqualTo(1)
    }

    @Test
    fun exceptionContainsDebugInfo() {
        val error =
            assertThrows<AssertionError> {
                WindowManagerTraceSubject(chromeTrace, chromeTraceReader).isEmpty()
            }
        assertThatErrorContainsDebugInfo(error)
    }

    @Test
    fun testCanDetectSnapshotStartingWindow() {
        val reader =
            getWmTraceReaderFromAsset(
                "quick_switch_to_app_killed_in_background_trace.pb",
                legacyTrace = true
            )
        val trace = reader.readWmTrace() ?: error("Unable to read WM trace")
        val app1 =
            ComponentNameMatcher(
                "com.android.server.wm.flicker.testapp",
                "com.android.server.wm.flicker.testapp.ImeActivity"
            )
        val app2 =
            ComponentNameMatcher(
                "com.android.server.wm.flicker.testapp",
                "com.android.server.wm.flicker.testapp.SimpleActivity"
            )
        WindowManagerTraceSubject(trace, reader)
            .isAppWindowVisible(app1)
            .then()
            .isAppSnapshotStartingWindowVisibleFor(app2, isOptional = true)
            .then()
            .isAppWindowVisible(app2)
            .then()
            .isAppSnapshotStartingWindowVisibleFor(app1, isOptional = true)
            .then()
            .isAppWindowVisible(app1)
            .forAllEntries()
    }

    @Test
    fun canDetectAppInvisibleSnapshotStartingWindowVisible() {
        val reader =
            getWmTraceReaderFromAsset(
                "quick_switch_to_app_killed_in_background_trace.pb",
                legacyTrace = true
            )
        val trace = reader.readWmTrace() ?: error("Unable to read WM trace")
        val subject =
            WindowManagerTraceSubject(trace, reader).getEntryByElapsedTimestamp(694827105830L)
        val app =
            ComponentNameMatcher(
                "com.android.server.wm.flicker.testapp",
                "com.android.server.wm.flicker.testapp.SimpleActivity"
            )
        subject.isAppWindowInvisible(app)
        subject.isAppWindowVisible(ComponentNameMatcher.SNAPSHOT)
    }

    @Test
    fun canDetectAppVisibleTablet() {
        val reader =
            getWmTraceReaderFromAsset("tablet/wm_trace_open_chrome.winscope", legacyTrace = true)
        val trace = reader.readWmTrace() ?: error("Unable to read WM trace")
        WindowManagerTraceSubject(trace, reader)
            .isAppWindowVisible(TestComponents.CHROME)
            .forAllEntries()
    }

    @Test
    fun canDetectAppOpenRecentsTablet() {
        val reader =
            getWmTraceReaderFromAsset("tablet/wm_trace_open_recents.winscope", legacyTrace = true)
        val trace = reader.readWmTrace() ?: error("Unable to read WM trace")
        WindowManagerTraceSubject(trace, reader).isRecentsActivityVisible().forAllEntries()
    }

    companion object {
        @ClassRule @JvmField val ENV_CLEANUP = CleanFlickerEnvironmentRuleWithDataStore()
    }
}
