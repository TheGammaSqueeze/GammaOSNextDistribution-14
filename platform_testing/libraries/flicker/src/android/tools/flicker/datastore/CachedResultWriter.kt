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

import android.tools.traces.io.IResultData
import android.tools.traces.io.ResultWriter

/** Result writer that adds data of a [scenario] to the [DataStore] */
class CachedResultWriter : ResultWriter() {
    override fun write(): IResultData {
        val result = super.write()
        android.tools.flicker.datastore.DataStore.addResult(scenario, result)
        return result
    }
}
