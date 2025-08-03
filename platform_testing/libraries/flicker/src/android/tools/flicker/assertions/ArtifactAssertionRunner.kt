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

package android.tools.flicker.assertions

import android.tools.Logger
import android.tools.io.Reader
import android.tools.io.RunStatus
import android.tools.traces.TRACE_CONFIG_REQUIRE_CHANGES
import android.tools.traces.io.IResultData
import android.tools.traces.io.ResultReaderWithLru

/**
 * Helper class to run an assertion on a flicker artifact
 *
 * @param result flicker artifact data
 * @param resultReader helper class to read the flicker artifact
 */
class ArtifactAssertionRunner(
    private val result: IResultData,
    resultReader: Reader = ResultReaderWithLru(result, TRACE_CONFIG_REQUIRE_CHANGES)
) : BaseAssertionRunner(resultReader) {
    override fun doUpdateStatus(newStatus: RunStatus) {
        Logger.withTracing("ArtifactAssertionRunner#doUpdateStatus") {
            result.updateStatus(newStatus)
        }
    }
}
