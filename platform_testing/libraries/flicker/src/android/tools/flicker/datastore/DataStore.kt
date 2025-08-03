/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.tools.flicker.datastore

import android.tools.Scenario
import android.tools.flicker.ScenarioInstance
import android.tools.flicker.assertions.ScenarioAssertion
import android.tools.traces.io.IResultData
import androidx.annotation.VisibleForTesting

/** In memory data store for flicker transitions, assertions and results */
object DataStore {
    private var cachedResults = mutableMapOf<Scenario, IResultData>()
    private var cachedFlickerServiceAssertions =
        mutableMapOf<Scenario, Map<ScenarioInstance, Collection<ScenarioAssertion>>>()

    data class Backup(
        val cachedResults: MutableMap<Scenario, IResultData>,
        val cachedFlickerServiceAssertions:
            MutableMap<Scenario, Map<ScenarioInstance, Collection<ScenarioAssertion>>>
    )

    @VisibleForTesting
    fun clear() {
        android.tools.flicker.datastore.DataStore.cachedResults = mutableMapOf()
        android.tools.flicker.datastore.DataStore.cachedFlickerServiceAssertions = mutableMapOf()
    }

    fun backup(): android.tools.flicker.datastore.DataStore.Backup {
        return android.tools.flicker.datastore.DataStore.Backup(
            android.tools.flicker.datastore.DataStore.cachedResults.toMutableMap(),
            android.tools.flicker.datastore.DataStore.cachedFlickerServiceAssertions.toMutableMap()
        )
    }

    fun restore(backup: android.tools.flicker.datastore.DataStore.Backup) {
        android.tools.flicker.datastore.DataStore.cachedResults = backup.cachedResults
        android.tools.flicker.datastore.DataStore.cachedFlickerServiceAssertions =
            backup.cachedFlickerServiceAssertions
    }

    /** @return if the store has results for [scenario] */
    fun containsResult(scenario: Scenario): Boolean =
        android.tools.flicker.datastore.DataStore.cachedResults.containsKey(scenario)

    /**
     * Adds [result] to the store with [scenario] as id
     *
     * @throws IllegalStateException is [scenario] already exists in the data store
     */
    fun addResult(scenario: Scenario, result: IResultData) {
        require(!android.tools.flicker.datastore.DataStore.containsResult(scenario)) {
            "Result for $scenario already in data store"
        }
        android.tools.flicker.datastore.DataStore.cachedResults[scenario] = result
    }

    /**
     * Replaces the old value [scenario] result in the store by [newResult]
     *
     * @throws IllegalStateException is [scenario] doesn't exist in the data store
     */
    fun replaceResult(scenario: Scenario, newResult: IResultData) {
        if (!android.tools.flicker.datastore.DataStore.containsResult(scenario)) {
            error("Result for $scenario not in data store")
        }
        android.tools.flicker.datastore.DataStore.cachedResults[scenario] = newResult
    }

    /**
     * @return the result for [scenario]
     * @throws IllegalStateException is [scenario] doesn't exist in the data store
     */
    fun getResult(scenario: Scenario): IResultData =
        android.tools.flicker.datastore.DataStore.cachedResults[scenario]
            ?: error("No value for $scenario")

    /** @return if the store has results for [scenario] */
    fun containsFlickerServiceResult(scenario: Scenario): Boolean =
        android.tools.flicker.datastore.DataStore.cachedFlickerServiceAssertions.containsKey(
            scenario
        )

    fun addFlickerServiceAssertions(
        scenario: Scenario,
        groupedAssertions: Map<ScenarioInstance, Collection<ScenarioAssertion>>
    ) {
        if (android.tools.flicker.datastore.DataStore.containsFlickerServiceResult(scenario)) {
            error("Result for $scenario already in data store")
        }
        android.tools.flicker.datastore.DataStore.cachedFlickerServiceAssertions[scenario] =
            groupedAssertions
    }

    fun getFlickerServiceAssertions(
        scenario: Scenario
    ): Map<ScenarioInstance, Collection<ScenarioAssertion>> {
        return android.tools.flicker.datastore.DataStore.cachedFlickerServiceAssertions[scenario]
            ?: error("No flicker service results for $scenario")
    }
}
