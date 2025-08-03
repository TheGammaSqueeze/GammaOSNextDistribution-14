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

package android.tools.flicker.subject.surfaceflinger

import android.tools.Cache
import android.tools.datatypes.Rect
import android.tools.datatypes.Region
import android.tools.flicker.subject.layers.LayerTraceEntrySubject
import android.tools.flicker.subject.layers.LayersTraceSubject
import android.tools.traces.component.ComponentNameMatcher
import android.tools.traces.component.OrComponentMatcher
import android.tools.utils.CleanFlickerEnvironmentRule
import android.tools.utils.MockLayerBuilder
import android.tools.utils.MockLayerTraceEntryBuilder
import android.tools.utils.TestComponents
import android.tools.utils.assertFail
import android.tools.utils.assertThatErrorContainsDebugInfo
import android.tools.utils.assertThrows
import android.tools.utils.getLayerTraceReaderFromAsset
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.ClassRule
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * Contains [LayerTraceEntrySubject] tests. To run this test: `atest
 * FlickerLibTest:LayerTraceEntrySubjectTest`
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class LayerTraceEntrySubjectTest {
    @Before
    fun before() {
        Cache.clear()
    }

    @Test
    fun exceptionContainsDebugInfo() {
        val reader = getLayerTraceReaderFromAsset("layers_trace_emptyregion.perfetto-trace")
        val trace = reader.readLayersTrace() ?: error("Unable to read layers trace")
        val error =
            assertThrows<AssertionError> {
                LayersTraceSubject(trace, reader).first().visibleRegion(TestComponents.IMAGINARY)
            }
        assertThatErrorContainsDebugInfo(error)
        Truth.assertThat(error).hasMessageThat().contains(TestComponents.IMAGINARY.className)
    }

    @Test
    fun testCanInspectBeginning() {
        val reader = getLayerTraceReaderFromAsset("layers_trace_launch_split_screen.perfetto-trace")
        val trace = reader.readLayersTrace() ?: error("Unable to read layers trace")
        LayerTraceEntrySubject(trace.entries.first(), reader)
            .isVisible(ComponentNameMatcher.NAV_BAR)
            .notContains(TestComponents.DOCKER_STACK_DIVIDER)
            .isVisible(TestComponents.LAUNCHER)
    }

    @Test
    fun testCanInspectEnd() {
        val reader = getLayerTraceReaderFromAsset("layers_trace_launch_split_screen.perfetto-trace")
        val trace = reader.readLayersTrace() ?: error("Unable to read layers trace")
        LayerTraceEntrySubject(trace.entries.last(), reader)
            .isVisible(ComponentNameMatcher.NAV_BAR)
            .isVisible(TestComponents.DOCKER_STACK_DIVIDER)
    }

    // b/75276931
    @Test
    fun canDetectUncoveredRegion() {
        val reader = getLayerTraceReaderFromAsset("layers_trace_emptyregion.perfetto-trace")
        val trace = reader.readLayersTrace() ?: error("Unable to read layers trace")
        val expectedRegion = Region.from(0, 0, 1440, 2960)
        assertFail("SkRegion((0,0,1440,1440)) should cover at least SkRegion((0,0,1440,2960))") {
            LayersTraceSubject(trace, reader)
                .getEntryBySystemUpTime(935346112030, byElapsedTimestamp = true)
                .visibleRegion()
                .coversAtLeast(expectedRegion)
        }
    }

    // Visible region tests
    @Test
    fun canTestLayerVisibleRegion_layerDoesNotExist() {
        val reader = getLayerTraceReaderFromAsset("layers_trace_emptyregion.perfetto-trace")
        val trace = reader.readLayersTrace() ?: error("Unable to read layers trace")
        val expectedVisibleRegion = Region.from(0, 0, 1, 1)
        assertFail(TestComponents.IMAGINARY.toWindowIdentifier()) {
            LayersTraceSubject(trace, reader)
                .getEntryBySystemUpTime(937229257165, byElapsedTimestamp = true)
                .visibleRegion(TestComponents.IMAGINARY)
                .coversExactly(expectedVisibleRegion)
        }
    }

    @Test
    fun canTestLayerVisibleRegion_layerDoesNotHaveExpectedVisibleRegion() {
        val reader = getLayerTraceReaderFromAsset("layers_trace_emptyregion.perfetto-trace")
        val trace = reader.readLayersTrace() ?: error("Unable to read layers trace")
        val expectedVisibleRegion = Region.from(0, 0, 1, 1)
        assertFail("[empty] should cover exactly SkRegion((0,0,1,1))") {
            LayersTraceSubject(trace, reader)
                .getEntryBySystemUpTime(937126074082, byElapsedTimestamp = true)
                .visibleRegion(TestComponents.DOCKER_STACK_DIVIDER)
                .coversExactly(expectedVisibleRegion)
        }
    }

    @Test
    fun canTestLayerVisibleRegion_layerIsHiddenByParent() {
        val reader = getLayerTraceReaderFromAsset("layers_trace_emptyregion.perfetto-trace")
        val trace = reader.readLayersTrace() ?: error("Unable to read layers trace")
        val expectedVisibleRegion = Region.from(0, 0, 1, 1)
        assertFail("[empty] should cover exactly SkRegion((0,0,1,1))") {
            LayersTraceSubject(trace, reader)
                .getEntryBySystemUpTime(935346112030, byElapsedTimestamp = true)
                .visibleRegion(TestComponents.SIMPLE_APP)
                .coversExactly(expectedVisibleRegion)
        }
    }

    @Test
    fun canTestLayerVisibleRegion_incorrectRegionSize() {
        val reader = getLayerTraceReaderFromAsset("layers_trace_emptyregion.perfetto-trace")
        val trace = reader.readLayersTrace() ?: error("Unable to read layers trace")
        val expectedVisibleRegion = Region.from(0, 0, 1440, 99)
        assertFail("SkRegion((0,0,1440,171)) should cover exactly SkRegion((0,0,1440,99))") {
            LayersTraceSubject(trace, reader)
                .getEntryBySystemUpTime(937126074082, byElapsedTimestamp = true)
                .visibleRegion(ComponentNameMatcher.STATUS_BAR)
                .coversExactly(expectedVisibleRegion)
        }
    }

    @Test
    fun canTestLayerVisibleRegion() {
        val reader = getLayerTraceReaderFromAsset("layers_trace_launch_split_screen.perfetto-trace")
        val trace = reader.readLayersTrace() ?: error("Unable to read layers trace")
        val expectedVisibleRegion = Region.from(0, 0, 1080, 145)
        LayersTraceSubject(trace, reader)
            .getEntryBySystemUpTime(90480846872160, byElapsedTimestamp = true)
            .visibleRegion(ComponentNameMatcher.STATUS_BAR)
            .coversExactly(expectedVisibleRegion)
    }

    @Test
    fun canTestLayerVisibleRegion_layerIsNotVisible() {
        val reader =
            getLayerTraceReaderFromAsset("layers_trace_invalid_layer_visibility.perfetto-trace")
        val trace = reader.readLayersTrace() ?: error("Unable to read layers trace")
        assertFail("Bounds is 0x0") {
            LayersTraceSubject(trace, reader)
                .getEntryBySystemUpTime(252794268378458, byElapsedTimestamp = true)
                .isVisible(TestComponents.SIMPLE_APP)
        }
    }

    @Test
    fun orComponentMatcher_visibility_oneVisibleOtherInvisible() {
        val app1Name = "com.simple.test.app1"
        val app2Name = "com.simple.test.app2"

        val layerTraceEntry =
            MockLayerTraceEntryBuilder()
                .addDisplay(
                    rootLayers =
                        listOf(
                            MockLayerBuilder(app1Name)
                                .setContainerLayer()
                                .addChild(MockLayerBuilder(app1Name).setVisible()),
                            MockLayerBuilder(app2Name)
                                .setContainerLayer()
                                .addChild(MockLayerBuilder(app2Name).setInvisible()),
                        )
                )
                .build()

        val subject = LayerTraceEntrySubject(layerTraceEntry)
        val component =
            OrComponentMatcher(
                listOf(ComponentNameMatcher(app1Name), ComponentNameMatcher(app2Name))
            )

        subject.isVisible(ComponentNameMatcher(app1Name))
        subject.isInvisible(ComponentNameMatcher(app2Name))

        subject.isInvisible(component)
        subject.isVisible(component)
    }

    @Test
    fun orComponentMatcher_visibility_oneVisibleOtherMissing() {
        val app1Name = "com.simple.test.app1"
        val app2Name = "com.simple.test.app2"

        val layerTraceEntry =
            MockLayerTraceEntryBuilder()
                .addDisplay(
                    rootLayers =
                        listOf(
                            MockLayerBuilder(app1Name)
                                .setContainerLayer()
                                .addChild(MockLayerBuilder(app1Name).setVisible())
                        )
                )
                .build()

        val subject = LayerTraceEntrySubject(layerTraceEntry)
        val component =
            OrComponentMatcher(
                listOf(ComponentNameMatcher(app1Name), ComponentNameMatcher(app2Name))
            )

        subject.isVisible(ComponentNameMatcher(app1Name))
        subject.notContains(ComponentNameMatcher(app2Name))

        subject.isInvisible(component)
        subject.isVisible(component)
    }

    @Test
    fun canUseOrComponentMatcher_visibility_allVisible() {
        val app1Name = "com.simple.test.app1"
        val app2Name = "com.simple.test.app2"

        val layerTraceEntry =
            MockLayerTraceEntryBuilder()
                .addDisplay(
                    rootLayers =
                        listOf(
                            MockLayerBuilder(app1Name)
                                .setContainerLayer()
                                .setAbsoluteBounds(Rect.from(0, 0, 200, 200))
                                .addChild(MockLayerBuilder("$app1Name child").setVisible()),
                            MockLayerBuilder(app2Name)
                                .setContainerLayer()
                                .setAbsoluteBounds(Rect.from(200, 200, 400, 400))
                                .addChild(MockLayerBuilder("$app2Name child").setVisible()),
                        )
                )
                .build()

        val subject = LayerTraceEntrySubject(layerTraceEntry)
        val component =
            OrComponentMatcher(
                listOf(ComponentNameMatcher(app1Name), ComponentNameMatcher(app2Name))
            )

        subject.isVisible(ComponentNameMatcher(app1Name))
        subject.isVisible(ComponentNameMatcher(app2Name))

        assertThrows<AssertionError> { subject.isInvisible(component) }
        subject.isVisible(component)
    }

    @Test
    fun canUseOrComponentMatcher_contains_withOneExists() {
        val app1Name = "com.simple.test.app1"
        val app2Name = "com.simple.test.app2"

        val layerTraceEntry =
            MockLayerTraceEntryBuilder()
                .addDisplay(
                    rootLayers =
                        listOf(
                            MockLayerBuilder(app1Name)
                                .setContainerLayer()
                                .addChild(MockLayerBuilder(app1Name))
                        )
                )
                .build()

        val subject = LayerTraceEntrySubject(layerTraceEntry)
        val component =
            OrComponentMatcher(
                listOf(ComponentNameMatcher(app1Name), ComponentNameMatcher(app2Name))
            )

        subject.contains(ComponentNameMatcher(app1Name))
        subject.notContains(ComponentNameMatcher(app2Name))

        subject.contains(component)

        assertFail("Found: com.simple.test.app1") { subject.notContains(component) }
    }

    @Test
    fun canUseOrComponentMatcher_contains_withNoneExists() {
        val app1Name = "com.simple.test.app1"
        val app2Name = "com.simple.test.app2"

        val layerTraceEntry = MockLayerTraceEntryBuilder().addDisplay(rootLayers = listOf()).build()

        val subject = LayerTraceEntrySubject(layerTraceEntry)
        val component =
            OrComponentMatcher(
                listOf(ComponentNameMatcher(app1Name), ComponentNameMatcher(app2Name))
            )

        subject.notContains(ComponentNameMatcher(app1Name))
        subject.notContains(ComponentNameMatcher(app2Name))

        subject.notContains(component)
        assertThrows<AssertionError> { subject.contains(component) }
    }

    @Test
    fun canUseOrComponentMatcher_contains_withBothExists() {
        val app1Name = "com.simple.test.app1"
        val app2Name = "com.simple.test.app2"

        val layerTraceEntry =
            MockLayerTraceEntryBuilder()
                .addDisplay(
                    rootLayers =
                        listOf(
                            MockLayerBuilder(app1Name)
                                .setContainerLayer()
                                .addChild(MockLayerBuilder(app1Name)),
                            MockLayerBuilder(app2Name)
                                .setContainerLayer()
                                .addChild(MockLayerBuilder(app2Name)),
                        )
                )
                .build()

        val subject = LayerTraceEntrySubject(layerTraceEntry)
        val component =
            OrComponentMatcher(
                listOf(ComponentNameMatcher(app1Name), ComponentNameMatcher(app2Name))
            )

        subject.contains(ComponentNameMatcher(app1Name))
        subject.contains(ComponentNameMatcher(app2Name))

        assertThrows<AssertionError> { subject.notContains(component) }
        subject.contains(component)
    }

    @Test
    fun detectOccludedLayerBecauseOfRoundedCorners() {
        val reader = getLayerTraceReaderFromAsset("layers_trace_rounded_corners.perfetto-trace")
        val trace = reader.readLayersTrace() ?: error("Unable to read layers trace")
        val entry =
            LayersTraceSubject(trace, reader)
                .getEntryBySystemUpTime(6216612368228, byElapsedTimestamp = true)
        val defaultPkg = "com.android.server.wm.flicker.testapp"
        val simpleActivityMatcher =
            ComponentNameMatcher(defaultPkg, "$defaultPkg.SimpleActivity#66086")
        val imeActivityMatcher = ComponentNameMatcher(defaultPkg, "$defaultPkg.ImeActivity#66060")
        val simpleActivitySubject =
            entry.layer(simpleActivityMatcher) ?: error("Layer should be available")
        val imeActivitySubject =
            entry.layer(imeActivityMatcher) ?: error("Layer should be available")
        val simpleActivityLayer = simpleActivitySubject.layer
        val imeActivityLayer = imeActivitySubject.layer
        // both layers have the same region
        imeActivitySubject.visibleRegion.coversExactly(simpleActivitySubject.visibleRegion.region)
        // both are visible
        entry.isInvisible(simpleActivityMatcher)
        entry.isVisible(imeActivityMatcher)
        // and simple activity is partially covered by IME activity
        Truth.assertWithMessage("IME activity has rounded corners")
            .that(simpleActivityLayer.occludedBy)
            .contains(imeActivityLayer)
        // because IME activity has rounded corners
        Truth.assertWithMessage("IME activity has rounded corners")
            .that(imeActivityLayer.cornerRadius)
            .isGreaterThan(0)
    }

    @Test
    fun canDetectInvisibleLayerOutOfScreen() {
        val reader =
            getLayerTraceReaderFromAsset("layers_trace_visible_outside_bounds.perfetto-trace")
        val trace = reader.readLayersTrace() ?: error("Unable to read layers trace")
        val subject =
            LayersTraceSubject(trace, reader)
                .getEntryBySystemUpTime(1253267561044, byElapsedTimestamp = true)
        val region = subject.visibleRegion(ComponentNameMatcher.IME_SNAPSHOT)
        region.isEmpty()
        subject.isInvisible(ComponentNameMatcher.IME_SNAPSHOT)
    }

    @Test
    fun canDetectInvisibleLayerOutOfScreen_ConsecutiveLayers() {
        val reader =
            getLayerTraceReaderFromAsset("layers_trace_visible_outside_bounds.perfetto-trace")
        val trace = reader.readLayersTrace() ?: error("Unable to read layers trace")
        val subject = LayersTraceSubject(trace, reader)
        subject.visibleLayersShownMoreThanOneConsecutiveEntry()
    }

    companion object {
        @ClassRule @JvmField val ENV_CLEANUP = CleanFlickerEnvironmentRule()
    }
}
