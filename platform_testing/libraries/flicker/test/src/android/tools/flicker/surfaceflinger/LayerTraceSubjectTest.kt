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

package android.tools.flicker.surfaceflinger

import android.tools.flicker.subject.layers.LayersTraceSubject
import android.tools.utils.assertThatErrorContainsDebugInfo
import android.tools.utils.assertThrows
import android.tools.utils.getLayerTraceReaderFromAsset
import org.junit.Test

class LayerTraceSubjectTest {
    @Test
    fun exceptionContainsDebugInfo() {
        val reader = getLayerTraceReaderFromAsset("layers_trace_emptyregion.perfetto-trace")
        val trace = reader.readLayersTrace() ?: error("Unable to read layers trace")
        val error = assertThrows<AssertionError> { LayersTraceSubject(trace, reader).isEmpty() }
        assertThatErrorContainsDebugInfo(error)
    }
}
