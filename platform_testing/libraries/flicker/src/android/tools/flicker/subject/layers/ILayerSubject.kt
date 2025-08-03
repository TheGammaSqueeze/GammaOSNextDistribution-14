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

import android.tools.traces.component.IComponentMatcher
import android.tools.traces.component.IComponentNameMatcher
import android.tools.traces.surfaceflinger.Display
import android.tools.traces.surfaceflinger.Layer

/** Base interface for Layer trace and state assertions */
interface ILayerSubject<LayerSubjectType, RegionSubjectType> {
    /** Asserts that the current SurfaceFlinger state doesn't contain layers */
    fun isEmpty(): LayerSubjectType

    /** Asserts that the current SurfaceFlinger state contains layers */
    fun isNotEmpty(): LayerSubjectType

    /**
     * Obtains the region occupied by all layers matching [componentMatcher]
     *
     * @param componentMatcher Components to search
     * @param useCompositionEngineRegionOnly If true, uses only the region calculated from the
     *   Composition Engine (CE) -- visibleRegion in the proto definition. Otherwise, calculates the
     *   visible region when the information is not available from the CE
     */
    fun visibleRegion(
        componentMatcher: IComponentMatcher? = null,
        useCompositionEngineRegionOnly: Boolean = true
    ): RegionSubjectType

    /**
     * Asserts the state contains a [Layer] matching [componentMatcher].
     *
     * @param componentMatcher Components to search
     */
    fun contains(componentMatcher: IComponentMatcher): LayerSubjectType

    /**
     * Asserts the state doesn't contain a [Layer] matching [componentMatcher]
     *
     * @param componentMatcher Components to search
     */
    fun notContains(componentMatcher: IComponentMatcher): LayerSubjectType

    /**
     * Asserts that a [Layer] matching [componentMatcher] is visible.
     *
     * @param componentMatcher Components to search
     */
    fun isVisible(componentMatcher: IComponentMatcher): LayerSubjectType

    /**
     * Asserts that a [Layer] matching [componentMatcher] doesn't exist or is invisible.
     *
     * @param componentMatcher Components to search
     */
    fun isInvisible(componentMatcher: IComponentMatcher): LayerSubjectType

    /**
     * Asserts that the entry contains a visible splash screen [Layer] for a [layer] matching
     * [componentMatcher]
     *
     * @param componentMatcher Components to search
     */
    fun isSplashScreenVisibleFor(componentMatcher: IComponentNameMatcher): LayerSubjectType

    /**
     * Asserts that a [Layer] matching [componentMatcher] has a color set on it.
     *
     * @param componentMatcher Components to search
     */
    fun hasColor(componentMatcher: IComponentMatcher): LayerSubjectType

    /**
     * Asserts that all [Layer]s matching [componentMatcher] have a no color set on them.
     *
     * @param componentMatcher Components to search
     */
    fun hasNoColor(componentMatcher: IComponentMatcher): LayerSubjectType

    /**
     * Asserts that all [Layer]s matching [componentMatcher] have rounded corners.
     *
     * @param componentMatcher Components to search
     */
    fun hasRoundedCorners(componentMatcher: IComponentMatcher): LayerSubjectType

    /**
     * Obtains a [LayerSubject] for the first occurrence of a [Layer] with [Layer.name] containing
     * [name] in [frameNumber].
     *
     * @return LayerSubject that can be used to make assertions on a single layer matching [name]
     *   and [frameNumber].
     */
    fun layer(name: String, frameNumber: Long): LayerSubject?

    /** Checks if the state contains at least one [Display] */
    fun containsAtLeastOneDisplay(): LayerSubjectType
}
