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

import android.tools.flicker.subject.events.EventLogSubject
import android.tools.flicker.subject.layers.LayerTraceEntrySubject
import android.tools.flicker.subject.layers.LayersTraceSubject
import android.tools.flicker.subject.region.RegionTraceSubject
import android.tools.flicker.subject.wm.WindowManagerStateSubject
import android.tools.flicker.subject.wm.WindowManagerTraceSubject
import android.tools.traces.component.IComponentMatcher

interface FlickerTest {
    /**
     * Execute [assertion] on the initial state of a WM trace (before transition)
     *
     * @param assertion Assertion predicate
     */
    fun assertWmStart(assertion: WindowManagerStateSubject.() -> Unit)

    /**
     * Execute [assertion] on the final state of a WM trace (after transition)
     *
     * @param assertion Assertion predicate
     */
    fun assertWmEnd(assertion: WindowManagerStateSubject.() -> Unit)

    /**
     * Execute [assertion] on a WM trace
     *
     * @param assertion Assertion predicate
     */
    fun assertWm(assertion: WindowManagerTraceSubject.() -> Unit)

    /**
     * Execute [assertion] on a user defined moment ([tag]) of a WM trace
     *
     * @param assertion Assertion predicate
     */
    fun assertWmTag(tag: String, assertion: WindowManagerStateSubject.() -> Unit)

    fun assertWmVisibleRegion(
        componentMatcher: IComponentMatcher,
        assertion: RegionTraceSubject.() -> Unit
    )

    /**
     * Execute [assertion] on the initial state of a SF trace (before transition)
     *
     * @param assertion Assertion predicate
     */
    fun assertLayersStart(assertion: LayerTraceEntrySubject.() -> Unit)

    /**
     * Execute [assertion] on the final state of a SF trace (after transition)
     *
     * @param assertion Assertion predicate
     */
    fun assertLayersEnd(assertion: LayerTraceEntrySubject.() -> Unit)

    /**
     * Execute [assertion] on a SF trace
     *
     * @param assertion Assertion predicate
     */
    fun assertLayers(assertion: LayersTraceSubject.() -> Unit)

    /**
     * Execute [assertion] on a user defined moment ([tag]) of a SF trace
     *
     * @param assertion Assertion predicate
     */
    fun assertLayersTag(tag: String, assertion: LayerTraceEntrySubject.() -> Unit)

    /**
     * Execute [assertion] on the visible region of a component on the layers trace matching
     * [componentMatcher]
     *
     * @param componentMatcher Components to search
     * @param useCompositionEngineRegionOnly If true, uses only the region calculated from the
     *   Composition Engine (CE) -- visibleRegion in the proto definition. Otherwise, calculates the
     *   visible region when the information is not available from the CE
     * @param assertion Assertion predicate
     */
    fun assertLayersVisibleRegion(
        componentMatcher: IComponentMatcher,
        useCompositionEngineRegionOnly: Boolean = true,
        assertion: RegionTraceSubject.() -> Unit
    )

    /**
     * Execute [assertion] on a sequence of event logs
     *
     * @param assertion Assertion predicate
     */
    fun assertEventLog(assertion: EventLogSubject.() -> Unit)
}
