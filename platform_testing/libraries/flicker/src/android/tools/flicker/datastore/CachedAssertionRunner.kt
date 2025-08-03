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

import android.tools.Logger
import android.tools.Scenario
import android.tools.flicker.assertions.BaseAssertionRunner
import android.tools.io.Reader
import android.tools.io.RunStatus
import android.tools.traces.TRACE_CONFIG_REQUIRE_CHANGES

/**
 * Helper class to run an assertion on a flicker artifact from a [DataStore]
 *
 * @param scenario flicker scenario existing in the [DataStore]
 * @param resultReader helper class to read the flicker artifact
 */
class CachedAssertionRunner(
    private val scenario: Scenario,
    resultReader: Reader =
        android.tools.flicker.datastore.CachedResultReader(scenario, TRACE_CONFIG_REQUIRE_CHANGES)
) : BaseAssertionRunner(resultReader) {
    override fun doUpdateStatus(newStatus: RunStatus) {
        return Logger.withTracing("${this::class.simpleName}#doUpdateStatus") {
            val result = android.tools.flicker.datastore.DataStore.getResult(scenario)
            result.updateStatus(newStatus)
            android.tools.flicker.datastore.DataStore.replaceResult(scenario, result)
        }
    }
}
