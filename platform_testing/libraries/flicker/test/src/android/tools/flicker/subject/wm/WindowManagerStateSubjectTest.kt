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
import android.tools.datatypes.Region
import android.tools.traces.component.ComponentNameMatcher
import android.tools.traces.wm.KeyguardControllerState
import android.tools.traces.wm.WindowManagerState
import android.tools.utils.CleanFlickerEnvironmentRule
import android.tools.utils.TestComponents
import android.tools.utils.assertFail
import android.tools.utils.assertThatErrorContainsDebugInfo
import android.tools.utils.assertThrows
import android.tools.utils.getWmDumpReaderFromAsset
import android.tools.utils.getWmTraceReaderFromAsset
import android.tools.utils.newEmptyRootContainer
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.ClassRule
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * Contains [WindowManagerStateSubject] tests. To run this test: `atest
 * FlickerLibTest:WindowManagerStateSubjectTest`
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class WindowManagerStateSubjectTest {
    private val reader = getWmTraceReaderFromAsset("wm_trace_openchrome.pb", legacyTrace = true)
    private val trace
        get() = reader.readWmTrace() ?: error("Unable to read WM trace")

    // Launcher is visible in fullscreen in the first frame of the trace
    private val traceFirstFrameTimestamp = 9213763541297

    // The first frame where the chrome splash screen is shown
    private val traceFirstChromeFlashScreenTimestamp = 9215551505798

    // The bounds of the display used to generate the trace [trace]
    private val displayBounds = Region.from(0, 0, 1440, 2960)

    // The region covered by the status bar in the trace
    private val statusBarRegion = Region.from(0, 0, 1440, 171)

    @Before
    fun before() {
        Cache.clear()
    }

    @Test
    fun exceptionContainsDebugInfo() {
        val error =
            assertThrows<AssertionError> {
                WindowManagerTraceSubject(trace, reader)
                    .first()
                    .visibleRegion(TestComponents.IMAGINARY)
            }
        assertThatErrorContainsDebugInfo(error)
        Truth.assertThat(error).hasMessageThat().contains(TestComponents.IMAGINARY.className)
    }

    @Test
    fun canDetectAboveAppWindowVisibility_isVisible() {
        WindowManagerTraceSubject(trace, reader)
            .getEntryByElapsedTimestamp(traceFirstFrameTimestamp)
            .containsAboveAppWindow(ComponentNameMatcher.NAV_BAR)
            .containsAboveAppWindow(TestComponents.SCREEN_DECOR_OVERLAY)
            .containsAboveAppWindow(ComponentNameMatcher.STATUS_BAR)
    }

    @Test
    fun canDetectAboveAppWindowVisibility_isInvisible() {
        val subject =
            WindowManagerTraceSubject(trace, reader)
                .getEntryByElapsedTimestamp(traceFirstFrameTimestamp)
        assertFail("pip-dismiss-overlay") {
            subject
                .containsAboveAppWindow(TestComponents.PIP_OVERLAY)
                .isNonAppWindowVisible(TestComponents.PIP_OVERLAY)
        }

        assertFail("NavigationBar") {
            subject
                .containsAboveAppWindow(ComponentNameMatcher.NAV_BAR)
                .isNonAppWindowInvisible(ComponentNameMatcher.NAV_BAR)
        }
    }

    @Test
    fun canDetectWindowCoversAtLeastRegion_exactSize() {
        val entry =
            WindowManagerTraceSubject(trace, reader)
                .getEntryByElapsedTimestamp(traceFirstFrameTimestamp)

        entry.visibleRegion(ComponentNameMatcher.STATUS_BAR).coversAtLeast(statusBarRegion)
        entry.visibleRegion(TestComponents.LAUNCHER).coversAtLeast(displayBounds)
    }

    @Test
    fun canDetectWindowCoversAtLeastRegion_smallerRegion() {
        val entry =
            WindowManagerTraceSubject(trace, reader)
                .getEntryByElapsedTimestamp(traceFirstFrameTimestamp)
        entry
            .visibleRegion(ComponentNameMatcher.STATUS_BAR)
            .coversAtLeast(Region.from(0, 0, 100, 100))
        entry.visibleRegion(TestComponents.LAUNCHER).coversAtLeast(Region.from(0, 0, 100, 100))
    }

    @Test
    fun canDetectWindowCoversAtLeastRegion_largerRegion() {
        val subject =
            WindowManagerTraceSubject(trace, reader)
                .getEntryByElapsedTimestamp(traceFirstFrameTimestamp)
        assertFail("SkRegion((1440,0,1441,171))") {
            subject
                .visibleRegion(ComponentNameMatcher.STATUS_BAR)
                .coversAtLeast(Region.from(0, 0, 1441, 171))
        }

        assertFail("SkRegion((0,2960,1440,2961))") {
            subject
                .visibleRegion(TestComponents.LAUNCHER)
                .coversAtLeast(Region.from(0, 0, 1440, 2961))
        }
    }

    @Test
    fun canDetectWindowCoversExactlyRegion_exactSize() {
        val entry =
            WindowManagerTraceSubject(trace, reader)
                .getEntryByElapsedTimestamp(traceFirstFrameTimestamp)

        entry.visibleRegion(ComponentNameMatcher.STATUS_BAR).coversExactly(statusBarRegion)
        entry.visibleRegion(TestComponents.LAUNCHER).coversExactly(displayBounds)
    }

    @Test
    fun canDetectWindowCoversExactlyRegion_smallerRegion() {
        val subject =
            WindowManagerTraceSubject(trace, reader)
                .getEntryByElapsedTimestamp(traceFirstFrameTimestamp)
        assertFail("SkRegion((0,0,1440,171)) should cover at most SkRegion((0,0,100,100))") {
            subject
                .visibleRegion(ComponentNameMatcher.STATUS_BAR)
                .coversAtMost(Region.from(0, 0, 100, 100))
        }

        assertFail("Out-of-bounds region: SkRegion((100,0,1440,100)(0,100,1440,2960))") {
            subject.visibleRegion(TestComponents.LAUNCHER).coversAtMost(Region.from(0, 0, 100, 100))
        }
    }

    @Test
    fun canDetectWindowCoversExactlyRegion_largerRegion() {
        val subject =
            WindowManagerTraceSubject(trace, reader)
                .getEntryByElapsedTimestamp(traceFirstFrameTimestamp)
        assertFail("Uncovered region: SkRegion((1440,0,1441,171))") {
            subject
                .visibleRegion(ComponentNameMatcher.STATUS_BAR)
                .coversAtLeast(Region.from(0, 0, 1441, 171))
        }

        assertFail("Uncovered region: SkRegion((0,2960,1440,2961))") {
            subject
                .visibleRegion(TestComponents.LAUNCHER)
                .coversAtLeast(Region.from(0, 0, 1440, 2961))
        }
    }

    @Test
    fun canDetectWindowCoversAtMostRegion_extactSize() {
        val entry =
            WindowManagerTraceSubject(trace, reader)
                .getEntryByElapsedTimestamp(traceFirstFrameTimestamp)
        entry.visibleRegion(ComponentNameMatcher.STATUS_BAR).coversAtMost(statusBarRegion)
        entry.visibleRegion(TestComponents.LAUNCHER).coversAtMost(displayBounds)
    }

    @Test
    fun canDetectWindowCoversAtMostRegion_smallerRegion() {
        val subject =
            WindowManagerTraceSubject(trace, reader)
                .getEntryByElapsedTimestamp(traceFirstFrameTimestamp)
        assertFail("SkRegion((0,0,1440,171)) should cover at most SkRegion((0,0,100,100))") {
            subject
                .visibleRegion(ComponentNameMatcher.STATUS_BAR)
                .coversAtMost(Region.from(0, 0, 100, 100))
        }

        assertFail("SkRegion((0,0,1440,2960)) should cover at most SkRegion((0,0,100,100))") {
            subject.visibleRegion(TestComponents.LAUNCHER).coversAtMost(Region.from(0, 0, 100, 100))
        }
    }

    @Test
    fun canDetectWindowCoversAtMostRegion_largerRegion() {
        val entry =
            WindowManagerTraceSubject(trace, reader)
                .getEntryByElapsedTimestamp(traceFirstFrameTimestamp)

        entry
            .visibleRegion(ComponentNameMatcher.STATUS_BAR)
            .coversAtMost(Region.from(0, 0, 1441, 171))
        entry.visibleRegion(TestComponents.LAUNCHER).coversAtMost(Region.from(0, 0, 1440, 2961))
    }

    @Test
    fun canDetectBelowAppWindowVisibility() {
        WindowManagerTraceSubject(trace, reader)
            .getEntryByElapsedTimestamp(traceFirstFrameTimestamp)
            .containsNonAppWindow(TestComponents.WALLPAPER)
    }

    @Test
    fun canDetectAppWindowVisibility() {
        WindowManagerTraceSubject(trace, reader)
            .getEntryByElapsedTimestamp(traceFirstFrameTimestamp)
            .containsAppWindow(TestComponents.LAUNCHER)

        WindowManagerTraceSubject(trace, reader)
            .getEntryByElapsedTimestamp(traceFirstChromeFlashScreenTimestamp)
            .containsAppWindow(TestComponents.CHROME_SPLASH_SCREEN)
    }

    @Test
    fun canDetectAppWindowVisibilitySubject() {
        val reader =
            getWmTraceReaderFromAsset("wm_trace_launcher_visible_background.pb", legacyTrace = true)
        val trace = reader.readWmTrace() ?: error("Unable to read WM trace")
        val firstEntry = WindowManagerTraceSubject(trace, reader).first()
        val appWindowNames = firstEntry.wmState.appWindows.map { it.name }
        val expectedAppWindowName =
            "com.android.server.wm.flicker.testapp/" +
                "com.android.server.wm.flicker.testapp.SimpleActivity"
        firstEntry.check { "has1AppWindow" }.that(appWindowNames.size).isEqual(3)
        firstEntry
            .check { "App window names contain $expectedAppWindowName" }
            .that(appWindowNames)
            .contains(expectedAppWindowName)
    }

    @Test
    fun canDetectLauncherVisibility() {
        val reader =
            getWmTraceReaderFromAsset("wm_trace_launcher_visible_background.pb", legacyTrace = true)
        val trace = reader.readWmTrace() ?: error("Unable to read WM trace")
        val subject = WindowManagerTraceSubject(trace, reader)
        val firstTrace = subject.first()
        firstTrace.isAppWindowInvisible(TestComponents.LAUNCHER)

        // in the trace there are 2 launcher windows, a visible (usually the main launcher) and
        // an invisible one (the -1 window, for the swipe back on home screen action.
        // in flicker, the launcher is considered visible is any of them is visible
        subject.last().isAppWindowVisible(TestComponents.LAUNCHER)

        subject
            .isAppWindowNotOnTop(TestComponents.LAUNCHER)
            .isAppWindowInvisible(TestComponents.LAUNCHER)
            .then()
            .isAppWindowOnTop(TestComponents.LAUNCHER)
            .forAllEntries()
    }

    @Test
    fun canFailWithReasonForVisibilityChecks_windowNotFound() {
        assertFail(TestComponents.IMAGINARY.packageName) {
            WindowManagerTraceSubject(trace, reader)
                .getEntryByElapsedTimestamp(traceFirstFrameTimestamp)
                .containsNonAppWindow(TestComponents.IMAGINARY)
        }
    }

    @Test
    fun canFailWithReasonForVisibilityChecks_windowNotVisible() {
        assertFail(ComponentNameMatcher.IME.packageName) {
            WindowManagerTraceSubject(trace, reader)
                .getEntryByElapsedTimestamp(traceFirstFrameTimestamp)
                .containsNonAppWindow(ComponentNameMatcher.IME)
                .isNonAppWindowVisible(ComponentNameMatcher.IME)
        }
    }

    @Test
    fun canDetectAppZOrder() {
        WindowManagerTraceSubject(trace, reader)
            .getEntryByElapsedTimestamp(traceFirstChromeFlashScreenTimestamp)
            .containsAppWindow(TestComponents.LAUNCHER)
            .isAppWindowVisible(TestComponents.LAUNCHER)
            .isAboveWindow(TestComponents.CHROME_SPLASH_SCREEN, TestComponents.LAUNCHER)
            .isAppWindowOnTop(TestComponents.LAUNCHER)
    }

    @Test
    fun canFailWithReasonForZOrderChecks_windowNotOnTop() {
        assertFail(TestComponents.LAUNCHER.packageName) {
            WindowManagerTraceSubject(trace, reader)
                .getEntryByElapsedTimestamp(traceFirstChromeFlashScreenTimestamp)
                .isAppWindowOnTop(TestComponents.CHROME_SPLASH_SCREEN)
        }
    }

    @Test
    fun canDetectActivityVisibility() {
        val reader = getWmTraceReaderFromAsset("wm_trace_split_screen.pb", legacyTrace = true)
        val trace = reader.readWmTrace() ?: error("Unable to read WM trace")
        val lastEntry = WindowManagerTraceSubject(trace, reader).last()
        lastEntry.isAppWindowVisible(TestComponents.SHELL_SPLIT_SCREEN_PRIMARY)
        lastEntry.isAppWindowVisible(TestComponents.SHELL_SPLIT_SCREEN_SECONDARY)
    }

    @Test
    fun canHandleNoSubjects() {
        val emptyRootContainer = newEmptyRootContainer()
        val noWindowsState =
            WindowManagerState(
                elapsedTimestamp = 0,
                clockTimestamp = null,
                where = "",
                policy = null,
                focusedApp = "",
                focusedDisplayId = 0,
                _focusedWindow = "",
                inputMethodWindowAppToken = "",
                isHomeRecentsComponent = false,
                isDisplayFrozen = false,
                _pendingActivities = emptyList(),
                root = emptyRootContainer,
                keyguardControllerState =
                    KeyguardControllerState.from(
                        isAodShowing = false,
                        isKeyguardShowing = false,
                        keyguardOccludedStates = mapOf()
                    )
            )

        val mockComponent = ComponentNameMatcher("", "Mock")

        assertFail("Mock should exist") {
            WindowManagerStateSubject(noWindowsState).isAppWindowOnTop(mockComponent)
        }
    }

    @Test
    fun canDetectNoVisibleAppWindows() {
        val reader = getWmTraceReaderFromAsset("wm_trace_unlock.pb", legacyTrace = true)
        val trace = reader.readWmTrace() ?: error("Unable to read WM trace")
        val firstEntry = WindowManagerTraceSubject(trace, reader).first()
        firstEntry.hasNoVisibleAppWindow()
    }

    @Test
    fun canDetectHasVisibleAppWindows() {
        val reader = getWmTraceReaderFromAsset("wm_trace_unlock.pb", legacyTrace = true)
        val trace = reader.readWmTrace() ?: error("Unable to read WM trace")
        val lastEntry = WindowManagerTraceSubject(trace, reader).last()
        assertFail("Visible app windows") { lastEntry.hasNoVisibleAppWindow() }
    }

    @Test
    fun canDetectTaskFragment() {
        // Verify if parser can read a dump file with 2 TaskFragments showed side-by-side.
        val reader = getWmDumpReaderFromAsset("wm_trace_taskfragment.winscope")
        val trace = reader.readWmTrace() ?: error("Unable to read WM trace")
        // There's only one entry in dump file.
        val entry = WindowManagerTraceSubject(trace, reader).first()
        // Verify there's exact 2 TaskFragments in window hierarchy.
        Truth.assertThat(entry.wmState.taskFragments.size).isEqualTo(2)
    }

    @Test
    fun canDetectIsHomeActivityVisibleTablet() {
        val reader = getWmDumpReaderFromAsset("tablet/wm_dump_home_screen.winscope")
        val trace = reader.readWmTrace() ?: error("Unable to read WM trace")
        // There's only one entry in dump file.
        val entry = WindowManagerTraceSubject(trace, reader).first()
        // Verify that the device is in home screen
        Truth.assertThat(entry.wmState.isHomeActivityVisible).isTrue()
        // Verify that the subject is in home screen
        entry.isHomeActivityVisible()
    }

    @Test
    fun canDetectTaskBarIsVisible() {
        val reader = getWmDumpReaderFromAsset("tablet/wm_dump_home_screen.winscope")
        val trace = reader.readWmTrace() ?: error("Unable to read WM trace")
        // There's only one entry in dump file.
        val entry = WindowManagerTraceSubject(trace, reader).first()
        // Verify that the taskbar is visible
        entry.isNonAppWindowVisible(ComponentNameMatcher.TASK_BAR)
    }

    @Test
    fun canDetectWindowVisibilityWhen2WindowsHaveSameName() {
        val reader =
            getWmTraceReaderFromAsset("wm_trace_2activities_same_name.winscope", legacyTrace = true)
        val trace = reader.readWmTrace() ?: error("Unable to read WM trace")
        val componentMatcher =
            ComponentNameMatcher(
                "com.android.server.wm.flicker.testapp",
                "com.android.server.wm.flicker.testapp.NotificationActivity"
            )
        WindowManagerTraceSubject(trace, reader)
            .isAppWindowInvisible(componentMatcher)
            .then()
            .isAppWindowVisible(ComponentNameMatcher.SNAPSHOT, isOptional = true)
            .then()
            .isAppWindowVisible(ComponentNameMatcher.SPLASH_SCREEN, isOptional = true)
            .then()
            .isAppWindowVisible(componentMatcher)
            .forElapsedTimeRange(394872035003110L, 394874232110818L)
    }

    @Test
    fun canDetectInvisibleWindowBecauseActivityIsInvisible() {
        val entry =
            WindowManagerTraceSubject(trace, reader).getEntryByElapsedTimestamp(9215551505798L)
        entry.isAppWindowInvisible(TestComponents.CHROME_SPLASH_SCREEN)
    }

    companion object {
        @ClassRule @JvmField val ENV_CLEANUP = CleanFlickerEnvironmentRule()
    }
}
