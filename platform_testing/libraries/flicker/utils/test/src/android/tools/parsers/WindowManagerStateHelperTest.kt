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

package android.tools.parsers

import android.annotation.SuppressLint
import android.tools.Cache
import android.tools.Rotation
import android.tools.Timestamps
import android.tools.datatypes.ActiveBuffer
import android.tools.datatypes.Color
import android.tools.datatypes.Matrix33
import android.tools.datatypes.RectF
import android.tools.datatypes.Region
import android.tools.traces.DeviceStateDump
import android.tools.traces.component.ComponentNameMatcher
import android.tools.traces.component.IComponentName
import android.tools.traces.parsers.WindowManagerStateHelper
import android.tools.traces.surfaceflinger.HwcCompositionType
import android.tools.traces.surfaceflinger.Layer
import android.tools.traces.surfaceflinger.LayerTraceEntryBuilder
import android.tools.traces.surfaceflinger.Transform
import android.tools.traces.wm.WindowManagerState
import android.tools.traces.wm.WindowManagerTrace
import android.tools.utils.CleanFlickerEnvironmentRule
import android.tools.utils.getWmDumpReaderFromAsset
import android.tools.utils.getWmTraceReaderFromAsset
import androidx.test.filters.FlakyTest
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.ClassRule
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * Contains [WindowManagerStateHelper] tests. To run this test: `atest
 * FlickerLibTest:WindowManagerTraceHelperTest`
 */
@SuppressLint("VisibleForTests")
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class WindowManagerStateHelperTest {
    class TestWindowManagerStateHelper(
        _wmState: WindowManagerState,
        /** Predicate to supply a new UI information */
        deviceDumpSupplier: () -> DeviceStateDump,
        numRetries: Int = 5,
        retryIntervalMs: Long = 500L
    ) :
        WindowManagerStateHelper(
            InstrumentationRegistry.getInstrumentation(),
            clearCacheAfterParsing = false,
            deviceDumpSupplier,
            numRetries,
            retryIntervalMs
        ) {
        var wmState: WindowManagerState = _wmState
            private set

        override fun updateCurrState(value: DeviceStateDump) {
            wmState = value.wmState
        }
    }

    private val chromeComponent =
        ComponentNameMatcher.unflattenFromString(
            "com.android.chrome/org.chromium.chrome.browser" + ".firstrun.FirstRunActivity"
        )
    private val simpleAppComponentName =
        ComponentNameMatcher.unflattenFromString(
            "com.android.server.wm.flicker.testapp/.SimpleActivity"
        )

    @Before
    fun before() {
        Cache.clear()
    }

    private fun createImaginaryLayer(name: String, index: Int, id: Int, parentId: Int): Layer {
        val transform = Transform.from(0, Matrix33.EMPTY)
        val rect =
            RectF.from(
                left = index.toFloat(),
                top = index.toFloat(),
                right = index.toFloat() + 1,
                bottom = index.toFloat() + 1
            )
        return Layer.from(
            name,
            id,
            parentId,
            z = 0,
            visibleRegion = Region.from(rect.toRect()),
            activeBuffer = ActiveBuffer.from(1, 1, 1, 1),
            flags = 0,
            bounds = rect,
            color = Color.DEFAULT,
            isOpaque = true,
            shadowRadius = 0f,
            cornerRadius = 0f,
            screenBounds = rect,
            transform = transform,
            currFrame = 0,
            effectiveScalingMode = 0,
            bufferTransform = transform,
            hwcCompositionType = HwcCompositionType.HWC_TYPE_UNSPECIFIED,
            crop = rect.toRect(),
            backgroundBlurRadius = 0,
            isRelativeOf = false,
            zOrderRelativeOfId = -1,
            stackId = 0,
            excludesCompositionState = false
        )
    }

    private fun createImaginaryVisibleLayers(names: List<IComponentName>): Collection<Layer> {
        val root = createImaginaryLayer("root", -1, id = "root".hashCode(), parentId = -1)
        val layers = mutableListOf(root)
        names.forEachIndexed { index, name ->
            layers.add(
                createImaginaryLayer(
                    name.toLayerName(),
                    index,
                    id = name.hashCode(),
                    parentId = root.id
                )
            )
        }
        return layers
    }

    /**
     * Creates a device state dump provider based on the WM trace
     *
     * Alongside the SF trac,e this function creates an imaginary SF trace with visible Status and
     * NavBar, as well as all visible non-system windows (those with name containing /)
     */
    private fun WindowManagerTrace.asSupplier(startingTimestamp: Long = 0): () -> DeviceStateDump {
        val iterator =
            this.entries.dropWhile { it.timestamp.elapsedNanos < startingTimestamp }.iterator()
        return {
            if (iterator.hasNext()) {
                val wmState = iterator.next()
                val layerList: MutableList<IComponentName> =
                    mutableListOf(
                        android.tools.traces.component.ComponentNameMatcher.STATUS_BAR,
                        android.tools.traces.component.ComponentNameMatcher.NAV_BAR
                    )
                if (
                    wmState.isWindowSurfaceShown(
                        android.tools.traces.component.ComponentNameMatcher.SPLASH_SCREEN
                    )
                ) {
                    layerList.add(android.tools.traces.component.ComponentNameMatcher.SPLASH_SCREEN)
                }
                if (
                    wmState.isWindowSurfaceShown(
                        android.tools.traces.component.ComponentNameMatcher.SNAPSHOT
                    )
                ) {
                    layerList.add(android.tools.traces.component.ComponentNameMatcher.SNAPSHOT)
                }
                layerList.addAll(
                    wmState.visibleWindows
                        .filter { it.name.contains("/") }
                        .map {
                            android.tools.traces.component.ComponentNameMatcher.unflattenFromString(
                                it.name
                            )
                        }
                )
                if (wmState.inputMethodWindowState?.isSurfaceShown == true) {
                    layerList.add(android.tools.traces.component.ComponentNameMatcher.IME)
                }
                val layerTraceEntry =
                    LayerTraceEntryBuilder()
                        .setElapsedTimestamp(0)
                        .setDisplays(emptyList())
                        .setLayers(createImaginaryVisibleLayers(layerList))
                        .setVSyncId(-1)
                        .build()
                DeviceStateDump(wmState, layerTraceEntry)
            } else {
                error("Reached the end of the trace")
            }
        }
    }

    @Test
    fun canWaitForIme() {
        val reader = getWmTraceReaderFromAsset("wm_trace_ime.pb", legacyTrace = true)
        val trace = reader.readWmTrace() ?: error("Unable to read WM trace")
        val supplier = trace.asSupplier()
        val helper =
            TestWindowManagerStateHelper(
                trace.entries.first(),
                supplier,
                numRetries = trace.entries.size,
                retryIntervalMs = 1
            )
        Truth.assertWithMessage("IME window is visible")
            .that(helper.wmState.isVisible(ComponentNameMatcher.IME))
            .isFalse()
        helper.StateSyncBuilder().withImeShown().waitFor()
        Truth.assertWithMessage("IME window is visible")
            .that(helper.wmState.isVisible(ComponentNameMatcher.IME))
            .isTrue()
    }

    @Test
    fun canFailImeNotShown() {
        val reader = getWmTraceReaderFromAsset("wm_trace_ime.pb", legacyTrace = true)
        val trace = reader.readWmTrace() ?: error("Unable to read WM trace")
        val supplier = trace.asSupplier()
        val helper =
            TestWindowManagerStateHelper(
                trace.entries.first(),
                supplier,
                numRetries = trace.entries.size,
                retryIntervalMs = 1
            )
        Truth.assertWithMessage("IME window is visible")
            .that(helper.wmState.isVisible(ComponentNameMatcher.IME))
            .isFalse()
        helper.StateSyncBuilder().withImeShown().waitFor()
        Truth.assertWithMessage("IME window is visible")
            .that(helper.wmState.isVisible(ComponentNameMatcher.IME))
            .isTrue()
    }

    @Test
    fun canWaitForWindow() {
        val reader = getWmTraceReaderFromAsset("wm_trace_open_app_cold.pb", legacyTrace = true)
        val trace = reader.readWmTrace() ?: error("Unable to read WM trace")
        val supplier = trace.asSupplier()
        val helper =
            TestWindowManagerStateHelper(
                trace.entries.first(),
                supplier,
                numRetries = trace.entries.size,
                retryIntervalMs = 1
            )
        Truth.assertWithMessage("$simpleAppComponentName is visible")
            .that(helper.wmState.isVisible(simpleAppComponentName))
            .isFalse()
        helper.StateSyncBuilder().withWindowSurfaceAppeared(simpleAppComponentName).waitFor()
        Truth.assertWithMessage("$simpleAppComponentName is visible")
            .that(helper.wmState.isVisible(simpleAppComponentName))
            .isTrue()
        Truth.assertWithMessage("$simpleAppComponentName is app window")
            .that(helper.wmState.isAppWindow(simpleAppComponentName))
            .isTrue()
    }

    @Test
    fun canFailWindowNotShown() {
        val reader = getWmTraceReaderFromAsset("wm_trace_open_app_cold.pb", legacyTrace = true)
        val trace = reader.readWmTrace() ?: error("Unable to read WM trace")
        val supplier = trace.asSupplier()
        val helper =
            TestWindowManagerStateHelper(
                trace.entries.first(),
                supplier,
                numRetries = 3,
                retryIntervalMs = 1
            )
        Truth.assertWithMessage("$simpleAppComponentName is visible")
            .that(helper.wmState.isVisible(simpleAppComponentName))
            .isFalse()
        helper.StateSyncBuilder().withWindowSurfaceAppeared(simpleAppComponentName).waitFor()
        Truth.assertWithMessage("$simpleAppComponentName is visible")
            .that(helper.wmState.isVisible(simpleAppComponentName))
            .isFalse()
    }

    @Test
    fun canDetectHomeActivityVisibility() {
        val reader =
            getWmTraceReaderFromAsset("wm_trace_open_and_close_chrome.pb", legacyTrace = true)
        val trace = reader.readWmTrace() ?: error("Unable to read WM trace")
        val supplier = trace.asSupplier()
        val helper =
            TestWindowManagerStateHelper(
                trace.entries.first(),
                supplier,
                numRetries = trace.entries.size,
                retryIntervalMs = 1
            )
        Truth.assertWithMessage("Home activity is visible")
            .that(helper.wmState.isHomeActivityVisible)
            .isTrue()
        helper.StateSyncBuilder().withWindowSurfaceAppeared(chromeComponent).waitFor()
        Truth.assertWithMessage("Home activity is visible")
            .that(helper.wmState.isHomeActivityVisible)
            .isFalse()
        helper.StateSyncBuilder().withHomeActivityVisible().waitFor()
        Truth.assertWithMessage("Home activity is visible")
            .that(helper.wmState.isHomeActivityVisible)
            .isTrue()
    }

    @Test
    fun canWaitActivityRemoved() {
        val reader =
            getWmTraceReaderFromAsset("wm_trace_open_and_close_chrome.pb", legacyTrace = true)
        val trace = reader.readWmTrace() ?: error("Unable to read WM trace")
        val supplier = trace.asSupplier()
        val helper =
            TestWindowManagerStateHelper(
                trace.entries.first(),
                supplier,
                numRetries = trace.entries.size,
                retryIntervalMs = 1
            )

        Truth.assertWithMessage("$chromeComponent is visible")
            .that(helper.wmState.isVisible(chromeComponent))
            .isFalse()
        helper.StateSyncBuilder().withWindowSurfaceAppeared(chromeComponent).waitFor()
        Truth.assertWithMessage("$chromeComponent is visible")
            .that(helper.wmState.isVisible(chromeComponent))
            .isTrue()
        helper.StateSyncBuilder().withActivityRemoved(chromeComponent).waitFor()
        Truth.assertWithMessage("$chromeComponent exists")
            .that(helper.wmState.contains(chromeComponent))
            .isFalse()
    }

    @Test
    fun canWaitAppStateIdle() {
        val reader =
            getWmTraceReaderFromAsset("wm_trace_open_and_close_chrome.pb", legacyTrace = true)
        val trace = reader.readWmTrace() ?: error("Unable to read WM trace")
        val initialTimestamp = 69443918698679
        val supplier = trace.asSupplier(startingTimestamp = initialTimestamp)
        val initialEntry = trace.getEntryExactlyAt(Timestamps.from(elapsedNanos = initialTimestamp))
        val helper =
            TestWindowManagerStateHelper(
                initialEntry,
                supplier,
                numRetries = trace.entries.size,
                retryIntervalMs = 1
            )

        Truth.assertWithMessage("State is valid").that(helper.wmState.isComplete()).isFalse()
        helper.StateSyncBuilder().withAppTransitionIdle().waitFor()
        Truth.assertWithMessage("State is valid").that(helper.wmState.isComplete()).isTrue()
    }

    @Test
    fun canWaitForRotation() {
        val reader = getWmTraceReaderFromAsset("wm_trace_rotation.pb", legacyTrace = true)
        val trace = reader.readWmTrace() ?: error("Unable to read WM trace")
        val supplier = trace.asSupplier()
        val helper =
            TestWindowManagerStateHelper(
                trace.entries.first(),
                supplier,
                numRetries = trace.entries.size,
                retryIntervalMs = 1
            )
        Truth.assertWithMessage("Display rotation")
            .that(helper.wmState.getRotation(displayId = 0))
            .isEqualTo(Rotation.ROTATION_0)
        helper.StateSyncBuilder().withRotation(Rotation.ROTATION_270).waitFor()
        Truth.assertWithMessage("Display rotation")
            .that(helper.wmState.getRotation(displayId = 0))
            .isEqualTo(Rotation.ROTATION_270)
        helper.StateSyncBuilder().withRotation(Rotation.ROTATION_0).waitFor()
        Truth.assertWithMessage("Display rotation")
            .that(helper.wmState.getRotation(displayId = 0))
            .isEqualTo(Rotation.ROTATION_0)
    }

    @Test
    fun canDetectResumedActivitiesInStacks() {
        val reader = getWmDumpReaderFromAsset("wm_trace_resumed_activities_in_stack.pb")
        val trace = reader.readWmTrace() ?: error("Unable to read WM trace")
        val entry = trace.entries.first()
        Truth.assertWithMessage("Trace should have a resumed activity in stacks")
            .that(entry.resumedActivities)
            .hasSize(1)
    }

    @FlakyTest
    @Test
    fun canWaitForRecents() {
        val reader = getWmTraceReaderFromAsset("wm_trace_open_recents.pb", legacyTrace = true)
        val trace = reader.readWmTrace() ?: error("Unable to read WM trace")
        val supplier = trace.asSupplier()
        val helper =
            TestWindowManagerStateHelper(
                trace.entries.first(),
                supplier,
                numRetries = trace.entries.size,
                retryIntervalMs = 1
            )
        Truth.assertWithMessage("Recents activity is visible")
            .that(helper.wmState.isRecentsActivityVisible)
            .isFalse()
        helper.StateSyncBuilder().withRecentsActivityVisible().waitFor()
        Truth.assertWithMessage("Recents activity is visible")
            .that(helper.wmState.isRecentsActivityVisible)
            .isTrue()
    }

    companion object {
        @ClassRule @JvmField val ENV_CLEANUP = CleanFlickerEnvironmentRule()
    }
}
