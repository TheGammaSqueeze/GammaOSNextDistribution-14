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

package android.tools.monitors.view

import android.tools.io.TraceType
import android.tools.monitors.TraceMonitorTest
import android.tools.traces.monitors.view.ViewTraceMonitor
import android.tools.utils.getLauncherPackageName
import com.google.common.truth.Truth
import org.junit.FixMethodOrder
import org.junit.runners.MethodSorters

/** Tests for [ViewTraceMonitor] */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ViewTraceMonitorTest : TraceMonitorTest<ViewTraceMonitor>() {
    override val traceType = TraceType.VIEW
    override val tag = LAUNCHER_TAG

    override fun getMonitor() = ViewTraceMonitor()

    override fun assertTrace(traceData: ByteArray) {
        Truth.assertThat(traceData.size).isGreaterThan(0)
    }

    companion object {
        private val LAUNCHER_TAG = "${getLauncherPackageName()}_0.vc"
    }
}
