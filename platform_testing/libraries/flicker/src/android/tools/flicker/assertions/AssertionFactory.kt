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

package android.tools.flicker.assertions

import android.tools.Logger
import android.tools.Tag
import android.tools.flicker.subject.FlickerSubject
import android.tools.flicker.subject.FlickerTraceSubject
import android.tools.flicker.subject.events.EventLogSubject
import android.tools.flicker.subject.layers.LayerTraceEntrySubject
import android.tools.flicker.subject.layers.LayersTraceSubject
import android.tools.flicker.subject.region.RegionTraceSubject
import android.tools.flicker.subject.wm.WindowManagerStateSubject
import android.tools.flicker.subject.wm.WindowManagerTraceSubject
import android.tools.traces.component.IComponentMatcher

class AssertionFactory {
    private val wmAssertionFactory =
        AssertionDataFactory(WindowManagerStateSubject::class, WindowManagerTraceSubject::class)
    private val layersAssertionFactory =
        AssertionDataFactory(LayerTraceEntrySubject::class, LayersTraceSubject::class)
    private val eventLogAssertionFactory = AssertionStateDataFactory(EventLogSubject::class)

    /**
     * Create an [assertion] on the initial state of a WM trace (before transition)
     *
     * @param assertion Assertion predicate
     */
    fun createWmStartAssertion(assertion: WindowManagerStateSubject.() -> Unit): AssertionData =
        Logger.withTracing("createWmStartAssertion") {
            wmAssertionFactory.createStartStateAssertion(assertion as FlickerSubject.() -> Unit)
        }

    /**
     * Create an [assertion] on the final state of a WM trace (after transition)
     *
     * @param assertion Assertion predicate
     */
    fun createWmEndAssertion(assertion: WindowManagerStateSubject.() -> Unit): AssertionData =
        Logger.withTracing("createWmEndAssertion") {
            wmAssertionFactory.createEndStateAssertion(assertion as FlickerSubject.() -> Unit)
        }

    /**
     * Create an [assertion] on a WM trace
     *
     * @param assertion Assertion predicate
     */
    fun createWmAssertion(assertion: WindowManagerTraceSubject.() -> Unit): AssertionData =
        Logger.withTracing("createWmAssertion") {
            wmAssertionFactory.createTraceAssertion(
                assertion as (FlickerTraceSubject<FlickerSubject>) -> Unit
            )
        }

    /**
     * Create an [assertion] on a user defined moment ([tag]) of a WM trace
     *
     * @param assertion Assertion predicate
     */
    fun createWmTagAssertion(
        tag: String,
        assertion: WindowManagerStateSubject.() -> Unit
    ): AssertionData =
        Logger.withTracing("createWmTagAssertion") {
            wmAssertionFactory.createTagAssertion(tag, assertion as FlickerSubject.() -> Unit)
        }

    /**
     * Create an [assertion] on the visible region of WM state matching [componentMatcher]
     *
     * @param componentMatcher Components to search
     * @param assertion Assertion predicate
     */
    fun createWmVisibleRegionAssertion(
        componentMatcher: IComponentMatcher,
        assertion: RegionTraceSubject.() -> Unit
    ): AssertionData =
        Logger.withTracing("createWmVisibleRegionAssertion") {
            val closedAssertion: WindowManagerTraceSubject.() -> Unit = {
                require(!hasAssertions()) { "Subject was already used to execute assertions" }
                // convert WindowManagerTraceSubject to RegionTraceSubject
                val regionTraceSubject = visibleRegion(componentMatcher)
                // add assertions to the regionTraceSubject's AssertionChecker
                assertion(regionTraceSubject)
                // loop through all entries to validate assertions
                regionTraceSubject.forAllEntries()
            }

            wmAssertionFactory.createTraceAssertion(
                closedAssertion as (FlickerTraceSubject<FlickerSubject>) -> Unit
            )
        }

    /**
     * Create an [assertion] on the initial state of a SF trace (before transition)
     *
     * @param assertion Assertion predicate
     */
    fun createLayersStartAssertion(assertion: LayerTraceEntrySubject.() -> Unit): AssertionData =
        Logger.withTracing("createLayersStartAssertion") {
            layersAssertionFactory.createStartStateAssertion(assertion as FlickerSubject.() -> Unit)
        }

    /**
     * Create an [assertion] on the final state of a SF trace (after transition)
     *
     * @param assertion Assertion predicate
     */
    fun createLayersEndAssertion(assertion: LayerTraceEntrySubject.() -> Unit): AssertionData =
        Logger.withTracing("createLayersEndAssertion") {
            layersAssertionFactory.createEndStateAssertion(assertion as FlickerSubject.() -> Unit)
        }

    /**
     * Create an [assertion] on a SF trace
     *
     * @param assertion Assertion predicate
     */
    fun createLayersAssertion(assertion: LayersTraceSubject.() -> Unit): AssertionData =
        Logger.withTracing("createLayersAssertion") {
            layersAssertionFactory.createTraceAssertion(
                assertion as (FlickerTraceSubject<FlickerSubject>) -> Unit
            )
        }

    /**
     * Create an [assertion] on a user defined moment ([tag]) of a SF trace
     *
     * @param assertion Assertion predicate
     */
    fun createLayersTagAssertion(
        tag: String,
        assertion: LayerTraceEntrySubject.() -> Unit
    ): AssertionData =
        Logger.withTracing("createLayersTagAssertion") {
            layersAssertionFactory.createTagAssertion(tag, assertion as FlickerSubject.() -> Unit)
        }

    /**
     * Create an [assertion] on the visible region of a component on the layers trace matching
     * [componentMatcher]
     *
     * @param componentMatcher Components to search
     * @param useCompositionEngineRegionOnly If true, uses only the region calculated from the
     *   Composition Engine (CE) -- visibleRegion in the proto definition. Otherwise, calculates the
     *   visible region when the information is not available from the CE
     * @param assertion Assertion predicate
     */
    fun createLayersVisibleRegionAssertion(
        componentMatcher: IComponentMatcher,
        useCompositionEngineRegionOnly: Boolean = true,
        assertion: RegionTraceSubject.() -> Unit
    ): AssertionData =
        Logger.withTracing("createLayersVisibleRegionAssertion") {
            val closedAssertion: LayersTraceSubject.() -> Unit = {
                require(!hasAssertions()) { "Subject was already used to execute assertions" }
                // convert LayersTraceSubject to RegionTraceSubject
                val regionTraceSubject =
                    visibleRegion(componentMatcher, useCompositionEngineRegionOnly)

                // add assertions to the regionTraceSubject's AssertionChecker
                assertion(regionTraceSubject)
                // loop through all entries to validate assertions
                regionTraceSubject.forAllEntries()
            }

            layersAssertionFactory.createTraceAssertion(
                closedAssertion as (FlickerTraceSubject<*>) -> Unit
            )
        }

    /**
     * Create an [assertion] on a sequence of event logs
     *
     * @param assertion Assertion predicate
     */
    fun createEventLogAssertion(assertion: EventLogSubject.() -> Unit): AssertionData =
        Logger.withTracing("createEventLogAssertion") {
            eventLogAssertionFactory.createTagAssertion(
                Tag.ALL,
                assertion as FlickerSubject.() -> Unit
            )
        }
}
