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

import android.tools.datatypes.Color
import android.tools.datatypes.Region
import android.tools.flicker.assertions.Fact
import android.tools.flicker.subject.FlickerSubject
import android.tools.flicker.subject.exceptions.ExceptionMessageBuilder
import android.tools.flicker.subject.exceptions.IncorrectVisibilityException
import android.tools.flicker.subject.exceptions.InvalidElementException
import android.tools.flicker.subject.exceptions.InvalidPropertyException
import android.tools.flicker.subject.region.RegionSubject
import android.tools.io.Reader
import android.tools.traces.component.ComponentSplashScreenMatcher
import android.tools.traces.component.IComponentMatcher
import android.tools.traces.component.IComponentNameMatcher
import android.tools.traces.surfaceflinger.Layer
import android.tools.traces.surfaceflinger.LayerTraceEntry
import android.tools.traces.surfaceflinger.LayersTrace

/**
 * Subject for [LayerTraceEntry] objects, used to make assertions over behaviors that occur on a
 * single SurfaceFlinger state.
 *
 * To make assertions over a specific state from a trace it is recommended to create a subject using
 * [LayersTraceSubject](myTrace) and select the specific state using:
 * ```
 *     [LayersTraceSubject.first]
 *     [LayersTraceSubject.last]
 *     [LayersTraceSubject.entry]
 * ```
 *
 * Alternatively, it is also possible to use [LayerTraceEntrySubject](myState).
 *
 * Example:
 * ```
 *    val trace = LayersTraceParser().parse(myTraceFile)
 *    val subject = LayersTraceSubject(trace).first()
 *        .contains("ValidLayer")
 *        .notContains("ImaginaryLayer")
 *        .coversExactly(DISPLAY_AREA)
 *        .invoke { myCustomAssertion(this) }
 * ```
 */
class LayerTraceEntrySubject(
    val entry: LayerTraceEntry,
    override val reader: Reader? = null,
    val trace: LayersTrace? = null,
) : FlickerSubject(), ILayerSubject<LayerTraceEntrySubject, RegionSubject> {
    override val timestamp = entry.timestamp

    val subjects by lazy { entry.flattenedLayers.map { LayerSubject(reader, timestamp, it) } }

    /** Executes a custom [assertion] on the current subject */
    operator fun invoke(assertion: (LayerTraceEntry) -> Unit): LayerTraceEntrySubject = apply {
        assertion(this.entry)
    }

    /** {@inheritDoc} */
    override fun isEmpty(): LayerTraceEntrySubject = apply {
        check { "SF state size" }.that(entry.flattenedLayers.size).isEqual(0)
    }

    /** {@inheritDoc} */
    override fun isNotEmpty(): LayerTraceEntrySubject = apply {
        check { "SF state size" }.that(entry.flattenedLayers.size).isGreater(0)
    }

    /** See [visibleRegion] */
    fun visibleRegion(): RegionSubject =
        visibleRegion(componentMatcher = null, useCompositionEngineRegionOnly = true)

    /** See [visibleRegion] */
    fun visibleRegion(componentMatcher: IComponentMatcher): RegionSubject =
        visibleRegion(componentMatcher, useCompositionEngineRegionOnly = true)

    /** {@inheritDoc} */
    override fun visibleRegion(
        componentMatcher: IComponentMatcher?,
        useCompositionEngineRegionOnly: Boolean
    ): RegionSubject {
        val selectedLayers =
            if (componentMatcher == null) {
                // No filters so use all subjects
                subjects
            } else {
                subjects.filter { componentMatcher.layerMatchesAnyOf(it.layer) }
            }

        if (selectedLayers.isEmpty()) {
            val errorMsgBuilder =
                ExceptionMessageBuilder()
                    .forSubject(this)
                    .forInvalidElement(
                        componentMatcher?.toLayerIdentifier() ?: "<any>",
                        expectElementExists = true
                    )
                    .addExtraDescription(
                        Fact("Use composition engine region", useCompositionEngineRegionOnly)
                    )
            throw InvalidElementException(errorMsgBuilder)
        }

        val visibleLayers = selectedLayers.filter { it.isVisible }
        return if (useCompositionEngineRegionOnly) {
            val visibleAreas = visibleLayers.mapNotNull { it.layer.visibleRegion }
            RegionSubject(visibleAreas, timestamp, reader)
        } else {
            val visibleAreas = visibleLayers.map { Region.from(it.layer.screenBounds) }
            RegionSubject(visibleAreas, timestamp, reader)
        }
    }

    /** {@inheritDoc} */
    override fun contains(componentMatcher: IComponentMatcher): LayerTraceEntrySubject = apply {
        if (!componentMatcher.layerMatchesAnyOf(entry.flattenedLayers)) {
            val errorMsgBuilder =
                ExceptionMessageBuilder()
                    .forSubject(this)
                    .forInvalidElement(
                        componentMatcher.toLayerIdentifier(),
                        expectElementExists = true
                    )
            throw InvalidElementException(errorMsgBuilder)
        }
    }

    /** {@inheritDoc} */
    override fun notContains(componentMatcher: IComponentMatcher): LayerTraceEntrySubject = apply {
        val layers = subjects.map { it.layer }
        val foundElements = componentMatcher.filterLayers(layers)

        if (foundElements.isNotEmpty()) {
            val errorMsgBuilder =
                ExceptionMessageBuilder()
                    .forSubject(this)
                    .forInvalidElement(
                        componentMatcher.toLayerIdentifier(),
                        expectElementExists = false
                    )
                    .setActual(foundElements.map { Fact("Found", it) })
            throw InvalidElementException(errorMsgBuilder)
        }
    }

    /** {@inheritDoc} */
    override fun isVisible(componentMatcher: IComponentMatcher): LayerTraceEntrySubject = apply {
        contains(componentMatcher)
        val layers = subjects.map { it.layer }
        val matchedLayers = componentMatcher.filterLayers(layers)
        val visibleLayers = matchedLayers.filter { it.isVisible }

        if (visibleLayers.isEmpty()) {
            val failedEntries =
                matchedLayers
                    .filterNot { it.isVisible }
                    .map { Fact(it.name, it.visibilityReason.joinToString()) }
            val errorMsgBuilder =
                ExceptionMessageBuilder()
                    .forSubject(this)
                    .forIncorrectVisibility(
                        componentMatcher.toLayerIdentifier(),
                        expectElementVisible = true
                    )
                    .setActual(failedEntries)
            throw IncorrectVisibilityException(errorMsgBuilder)
        }
    }

    /** {@inheritDoc} */
    override fun isInvisible(componentMatcher: IComponentMatcher): LayerTraceEntrySubject = apply {
        val layers = subjects.map { it.layer }
        val hasInvisibleComponent =
            componentMatcher.check(layers) { componentLayers ->
                componentLayers.all { layer ->
                    subjects.first { subject -> subject.layer == layer }.isInvisible
                }
            }

        if (hasInvisibleComponent) {
            return@apply
        }

        val failedEntries =
            componentMatcher
                .filterLayers(layers)
                .filter { it.isVisible }
                .map { Fact("Is visible", it.name) }
        val errorMsgBuilder =
            ExceptionMessageBuilder()
                .forSubject(this)
                .forIncorrectVisibility(
                    componentMatcher.toLayerIdentifier(),
                    expectElementVisible = false
                )
                .setActual(failedEntries)
        throw IncorrectVisibilityException(errorMsgBuilder)
    }

    /** {@inheritDoc} */
    override fun isSplashScreenVisibleFor(
        componentMatcher: IComponentNameMatcher
    ): LayerTraceEntrySubject = apply {
        val splashScreenMatcher = ComponentSplashScreenMatcher(componentMatcher)
        val matchingSubjects = subjects.filter { splashScreenMatcher.layerMatchesAnyOf(it.layer) }
        val hasVisibleMatchingSubject = matchingSubjects.any { it.isVisible }

        val errorMsgBuilder = ExceptionMessageBuilder().forSubject(this)
        if (!hasVisibleMatchingSubject) {
            if (matchingSubjects.isEmpty()) {
                errorMsgBuilder.forInvalidElement(
                    componentMatcher.toLayerIdentifier(),
                    expectElementExists = true
                )
            } else {
                errorMsgBuilder
                    .forIncorrectVisibility(
                        "Splash screen for ${componentMatcher.toLayerIdentifier()}",
                        expectElementVisible = true
                    )
                    .setActual(
                        matchingSubjects
                            .filter { it.isInvisible }
                            .map { Fact(it.name, it.visibilityReason.joinToString()) }
                    )
            }

            throw IncorrectVisibilityException(errorMsgBuilder)
        }
    }

    /** {@inheritDoc} */
    override fun hasColor(componentMatcher: IComponentMatcher): LayerTraceEntrySubject = apply {
        contains(componentMatcher)

        val targets = componentMatcher.filterLayers(subjects.map { it.layer })
        val hasLayerColor = targets.any { it.color.isNotEmpty }

        if (!hasLayerColor) {
            val errorMsgBuilder =
                ExceptionMessageBuilder()
                    .forSubject(this)
                    .forInvalidProperty("Color")
                    .setExpected("Not empty")
                    .setActual(targets.map { Fact(it.name, it.color) })
            throw InvalidPropertyException(errorMsgBuilder)
        }
    }

    /** {@inheritDoc} */
    override fun hasNoColor(componentMatcher: IComponentMatcher): LayerTraceEntrySubject = apply {
        val targets = componentMatcher.filterLayers(subjects.map { it.layer })
        val hasNoLayerColor = targets.all { it.color.isEmpty }

        if (!hasNoLayerColor) {
            val errorMsgBuilder =
                ExceptionMessageBuilder()
                    .forSubject(this)
                    .forInvalidProperty("Color")
                    .setExpected(Color.EMPTY.toString())
                    .setActual(targets.map { Fact(it.name, it.color) })
            throw InvalidPropertyException(errorMsgBuilder)
        }
    }

    /** {@inheritDoc} */
    override fun containsAtLeastOneDisplay(): LayerTraceEntrySubject = apply {
        check { "Displays" }.that(entry.displays.size).isGreater(0)
    }

    /** {@inheritDoc} */
    override fun hasRoundedCorners(componentMatcher: IComponentMatcher): LayerTraceEntrySubject =
        apply {
            contains(componentMatcher)

            val hasRoundedCornersLayer =
                componentMatcher.check(subjects.map { it.layer }) {
                    it.all { layer -> layer.cornerRadius > 0 }
                }

            if (!hasRoundedCornersLayer) {
                val errorMsgBuilder =
                    ExceptionMessageBuilder()
                        .forSubject(this)
                        .forInvalidProperty("RoundedCorners")
                        .setExpected("Not 0")
                        .setActual("0")
                        .addExtraDescription("Filter", componentMatcher.toLayerIdentifier())
                throw InvalidPropertyException(errorMsgBuilder)
            }
        }

    /** See [layer] */
    fun layer(componentMatcher: IComponentMatcher): LayerSubject? {
        return layer { componentMatcher.layerMatchesAnyOf(it) }
    }

    /** {@inheritDoc} */
    override fun layer(name: String, frameNumber: Long): LayerSubject? {
        return layer { it.name.contains(name) && it.currFrame == frameNumber }
    }

    /**
     * Obtains a [LayerSubject] for the first occurrence of a [Layer] matching [predicate] or throws
     * and error if the layer doesn't exist
     *
     * @param predicate to search for a layer
     * @return [LayerSubject] that can be used to make assertions
     */
    fun layer(predicate: (Layer) -> Boolean): LayerSubject? =
        subjects.firstOrNull { predicate(it.layer) }

    private fun getActivityRecordFor(layer: Layer): Layer? {
        if (layer.name.startsWith("ActivityRecord{")) {
            return layer
        }
        val parent = layer.parent ?: return null
        return getActivityRecordFor(parent)
    }

    override fun toString(): String {
        return "LayerTraceEntrySubject($entry)"
    }
}
