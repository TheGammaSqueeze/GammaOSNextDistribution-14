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

package android.tools.flicker.subject.layers

import android.tools.flicker.subject.FlickerTraceSubject
import android.tools.flicker.subject.exceptions.ExceptionMessageBuilder
import android.tools.flicker.subject.exceptions.InvalidElementException
import android.tools.flicker.subject.exceptions.InvalidPropertyException
import android.tools.flicker.subject.region.RegionTraceSubject
import android.tools.io.Reader
import android.tools.traces.component.ComponentNameMatcher
import android.tools.traces.component.EdgeExtensionComponentMatcher
import android.tools.traces.component.IComponentMatcher
import android.tools.traces.component.IComponentNameMatcher
import android.tools.traces.region.RegionTrace
import android.tools.traces.surfaceflinger.Layer
import android.tools.traces.surfaceflinger.LayersTrace

/**
 * Subject for [LayersTrace] objects, used to make assertions over behaviors that occur throughout a
 * whole trace
 *
 * To make assertions over a trace it is recommended to create a subject using
 * [LayersTraceSubject](myTrace).
 *
 * Example:
 * ```
 *    val trace = LayersTraceParser().parse(myTraceFile)
 *    val subject = LayersTraceSubject(trace)
 *        .contains("ValidLayer")
 *        .notContains("ImaginaryLayer")
 *        .coversExactly(DISPLAY_AREA)
 *        .forAllEntries()
 * ```
 *
 * Example2:
 * ```
 *    val trace = LayersTraceParser().parse(myTraceFile)
 *    val subject = LayersTraceSubject(trace) {
 *        check("Custom check") { myCustomAssertion(this) }
 *    }
 * ```
 */
class LayersTraceSubject(val trace: LayersTrace, override val reader: Reader? = null) :
    FlickerTraceSubject<LayerTraceEntrySubject>(),
    ILayerSubject<LayersTraceSubject, RegionTraceSubject> {

    override val subjects by lazy {
        trace.entries.map { LayerTraceEntrySubject(it, reader, trace) }
    }

    /** {@inheritDoc} */
    override fun then(): LayersTraceSubject = apply { super.then() }

    /** {@inheritDoc} */
    override fun isEmpty(): LayersTraceSubject = apply {
        check { "Trace is empty" }.that(trace.entries.isEmpty()).isEqual(true)
    }

    /** {@inheritDoc} */
    override fun isNotEmpty(): LayersTraceSubject = apply {
        check { "Trace is not empty" }.that(trace.entries.isNotEmpty()).isEqual(true)
    }

    /** {@inheritDoc} */
    override fun layer(name: String, frameNumber: Long): LayerSubject {
        val result = subjects.firstNotNullOfOrNull { it.layer(name, frameNumber) }

        if (result == null) {
            val errorMsgBuilder =
                ExceptionMessageBuilder()
                    .forSubject(this)
                    .forInvalidElement(name, expectElementExists = true)
                    .addExtraDescription("Frame number", frameNumber)
            throw InvalidElementException(errorMsgBuilder)
        }

        return result
    }

    /** @return List of [LayerSubject]s matching [name] in the order they appear on the trace */
    fun layers(name: String): List<LayerSubject> =
        subjects.mapNotNull { it.layer { layer -> layer.name.contains(name) } }

    /**
     * @return List of [LayerSubject]s matching [predicate] in the order they appear on the trace
     */
    fun layers(predicate: (Layer) -> Boolean): List<LayerSubject> =
        subjects.mapNotNull { it.layer { layer -> predicate(layer) } }

    /** Checks that all visible layers are shown for more than one consecutive entry */
    fun visibleLayersShownMoreThanOneConsecutiveEntry(
        ignoreLayers: List<IComponentMatcher> = VISIBLE_FOR_MORE_THAN_ONE_ENTRY_IGNORE_LAYERS
    ): LayersTraceSubject = apply {
        visibleEntriesShownMoreThanOneConsecutiveTime { subject ->
            subject.entry.visibleLayers
                .filter { visibleLayer ->
                    ignoreLayers.none { matcher -> matcher.layerMatchesAnyOf(visibleLayer) }
                }
                .map { it.name }
                .toSet()
        }
    }

    /** {@inheritDoc} */
    override fun notContains(componentMatcher: IComponentMatcher): LayersTraceSubject =
        notContains(componentMatcher, isOptional = false)

    /** See [notContains] */
    fun notContains(componentMatcher: IComponentMatcher, isOptional: Boolean): LayersTraceSubject =
        apply {
            addAssertion("notContains(${componentMatcher.toLayerIdentifier()})", isOptional) {
                it.notContains(componentMatcher)
            }
        }

    /** {@inheritDoc} */
    override fun contains(componentMatcher: IComponentMatcher): LayersTraceSubject =
        contains(componentMatcher, isOptional = false)

    /** See [contains] */
    fun contains(componentMatcher: IComponentMatcher, isOptional: Boolean): LayersTraceSubject =
        apply {
            addAssertion("contains(${componentMatcher.toLayerIdentifier()})", isOptional) {
                it.contains(componentMatcher)
            }
        }

    /** {@inheritDoc} */
    override fun isVisible(componentMatcher: IComponentMatcher): LayersTraceSubject =
        isVisible(componentMatcher, isOptional = false)

    /** See [isVisible] */
    fun isVisible(componentMatcher: IComponentMatcher, isOptional: Boolean): LayersTraceSubject =
        apply {
            addAssertion("isVisible(${componentMatcher.toLayerIdentifier()})", isOptional) {
                it.isVisible(componentMatcher)
            }
        }

    /** {@inheritDoc} */
    override fun isInvisible(componentMatcher: IComponentMatcher): LayersTraceSubject =
        isInvisible(componentMatcher, isOptional = false)

    /** See [isInvisible] */
    fun isInvisible(componentMatcher: IComponentMatcher, isOptional: Boolean): LayersTraceSubject =
        apply {
            addAssertion("isInvisible(${componentMatcher.toLayerIdentifier()})", isOptional) {
                it.isInvisible(componentMatcher)
            }
        }

    /** {@inheritDoc} */
    override fun isSplashScreenVisibleFor(
        componentMatcher: IComponentNameMatcher
    ): LayersTraceSubject = isSplashScreenVisibleFor(componentMatcher, isOptional = false)

    /** {@inheritDoc} */
    override fun hasColor(componentMatcher: IComponentMatcher): LayersTraceSubject = apply {
        addAssertion("hasColor(${componentMatcher.toLayerIdentifier()})") {
            it.hasColor(componentMatcher)
        }
    }

    /** {@inheritDoc} */
    override fun hasNoColor(componentMatcher: IComponentMatcher): LayersTraceSubject = apply {
        addAssertion("hasNoColor(${componentMatcher.toLayerIdentifier()})") {
            it.hasNoColor(componentMatcher)
        }
    }

    /** {@inheritDoc} */
    override fun hasRoundedCorners(componentMatcher: IComponentMatcher): LayersTraceSubject =
        apply {
            addAssertion("hasRoundedCorners(${componentMatcher.toLayerIdentifier()})") {
                it.hasRoundedCorners(componentMatcher)
            }
        }

    /** See [isSplashScreenVisibleFor] */
    fun isSplashScreenVisibleFor(
        componentMatcher: IComponentNameMatcher,
        isOptional: Boolean
    ): LayersTraceSubject = apply {
        addAssertion(
            "isSplashScreenVisibleFor(${componentMatcher.toLayerIdentifier()})",
            isOptional
        ) {
            it.isSplashScreenVisibleFor(componentMatcher)
        }
    }

    /** See [visibleRegion] */
    fun visibleRegion(): RegionTraceSubject =
        visibleRegion(componentMatcher = null, useCompositionEngineRegionOnly = false)

    /** See [visibleRegion] */
    fun visibleRegion(componentMatcher: IComponentMatcher?): RegionTraceSubject =
        visibleRegion(componentMatcher, useCompositionEngineRegionOnly = false)

    /** {@inheritDoc} */
    override fun visibleRegion(
        componentMatcher: IComponentMatcher?,
        useCompositionEngineRegionOnly: Boolean
    ): RegionTraceSubject {
        val regionTrace =
            RegionTrace(
                componentMatcher,
                subjects.map {
                    it.visibleRegion(componentMatcher, useCompositionEngineRegionOnly).regionEntry
                }
            )
        return RegionTraceSubject(regionTrace, reader)
    }

    fun atLeastOneEntryContainsOneDisplayOn(): LayersTraceSubject = apply {
        isNotEmpty()
        val anyEntryWithDisplayOn =
            subjects.any { it.entry.displays.any { display -> display.isOn } }
        if (!anyEntryWithDisplayOn) {
            val errorMsgBuilder =
                ExceptionMessageBuilder()
                    .forInvalidProperty("Display")
                    .setExpected("At least one display On in any entry")
                    .setActual("No displays on in any entry")
            throw InvalidPropertyException(errorMsgBuilder)
        }
    }

    override fun containsAtLeastOneDisplay(): LayersTraceSubject = apply {
        addAssertion("containAtLeastOneDisplay", isOptional = false) {
            it.containsAtLeastOneDisplay()
        }
    }

    /** Executes a custom [assertion] on the current subject */
    operator fun invoke(
        name: String,
        isOptional: Boolean = false,
        assertion: (LayerTraceEntrySubject) -> Unit
    ): LayersTraceSubject = apply { addAssertion(name, isOptional, assertion) }

    fun hasFrameSequence(name: String, frameNumbers: Iterable<Long>): LayersTraceSubject =
        hasFrameSequence(ComponentNameMatcher("", name), frameNumbers)

    fun hasFrameSequence(
        componentMatcher: IComponentMatcher,
        frameNumbers: Iterable<Long>
    ): LayersTraceSubject = apply {
        val firstFrame = frameNumbers.first()
        val entries =
            trace.entries
                .asSequence()
                // map entry to buffer layers with name
                .map { it.getLayerWithBuffer(componentMatcher) }
                // removing all entries without the layer
                .filterNotNull()
                // removing all entries with the same frame number
                .distinctBy { it.currFrame }
                // drop until the first frame we are interested in
                .dropWhile { layer -> layer.currFrame != firstFrame }

        var numFound = 0
        val frameNumbersMatch =
            entries
                .zip(frameNumbers.asSequence()) { layer, frameNumber ->
                    numFound++
                    layer.currFrame == frameNumber
                }
                .all { it }
        val allFramesFound = frameNumbers.count() == numFound
        if (!allFramesFound || !frameNumbersMatch) {
            val errorMsgBuilder =
                ExceptionMessageBuilder()
                    .forSubject(this)
                    .forInvalidElement(
                        componentMatcher.toLayerIdentifier(),
                        expectElementExists = true
                    )
                    .addExtraDescription("With frame sequence", frameNumbers.joinToString(","))
            throw InvalidElementException(errorMsgBuilder)
        }
    }

    /** Run the assertions for all trace entries within the specified time range */
    fun forSystemUpTimeRange(startTime: Long, endTime: Long) {
        val subjectsInRange =
            subjects.filter { it.entry.timestamp.systemUptimeNanos in startTime..endTime }
        assertionsChecker.test(subjectsInRange)
    }

    /**
     * User-defined entry point for the trace entry with [timestamp]
     *
     * @param timestamp of the entry
     */
    fun getEntryBySystemUpTime(
        timestamp: Long,
        byElapsedTimestamp: Boolean = false
    ): LayerTraceEntrySubject {
        return if (byElapsedTimestamp) {
            subjects.first { it.entry.elapsedTimestamp == timestamp }
        } else {
            subjects.first { it.entry.timestamp.systemUptimeNanos == timestamp }
        }
    }

    companion object {
        val VISIBLE_FOR_MORE_THAN_ONE_ENTRY_IGNORE_LAYERS =
            listOf(
                ComponentNameMatcher.SPLASH_SCREEN,
                ComponentNameMatcher.SNAPSHOT,
                ComponentNameMatcher.IME_SNAPSHOT,
                ComponentNameMatcher.PIP_CONTENT_OVERLAY,
                ComponentNameMatcher.EDGE_BACK_GESTURE_HANDLER,
                ComponentNameMatcher.COLOR_FADE,
                ComponentNameMatcher.TRANSITION_SNAPSHOT,
                ComponentNameMatcher.FLOATING_ROTATION_BUTTON,
                EdgeExtensionComponentMatcher()
            )
    }
}
