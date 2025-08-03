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

package android.tools.utils

import android.tools.Timestamps
import android.tools.traces.TraceConfig
import android.tools.traces.TraceConfigs

object TestTraces {
    object LayerTrace {
        private const val ASSET = "layers_trace.perfetto-trace"
        val START_TIME = Timestamps.from(systemUptimeNanos = 1618663562444)
        val SLICE_TIME = Timestamps.from(systemUptimeNanos = 1618715108595)
        val END_TIME = Timestamps.from(systemUptimeNanos = 1620770824112)
        val FILE
            get() = readAssetAsFile(ASSET)
    }

    object WMTrace {
        private const val ASSET = "wm_trace.winscope"
        val START_TIME = Timestamps.from(elapsedNanos = 1618650751245)
        val SLICE_TIME = Timestamps.from(elapsedNanos = 1618730362295)
        val END_TIME = Timestamps.from(elapsedNanos = 1620756218174)
        val FILE
            get() = readAssetAsFile(ASSET)
    }

    object EventLog {
        private const val ASSET = "eventlog.winscope"
        val START_TIME = Timestamps.from(unixNanos = 1670594369069951546)
        val SLICE_TIME = Timestamps.from(unixNanos = 1670594384516466159)
        val END_TIME = Timestamps.from(unixNanos = 1670594389958451901)
        val FILE
            get() = readAssetAsFile(ASSET)
    }

    object TransactionTrace {
        private const val ASSET = "transactions_trace.perfetto-trace"
        val START_TIME =
            Timestamps.from(systemUptimeNanos = 1556111744859, elapsedNanos = 1556111744859)
        val VALID_SLICE_TIME =
            Timestamps.from(systemUptimeNanos = 1556147625539, elapsedNanos = 1556147625539)
        val INVALID_SLICE_TIME = Timestamps.from(systemUptimeNanos = 1622127714039 + 1)
        val END_TIME =
            Timestamps.from(systemUptimeNanos = 1622127714039, elapsedNanos = 1622127714039)
        val FILE
            get() = readAssetAsFile(ASSET)
    }

    object LegacyTransitionTrace {
        private const val WM_ASSET = "wm_transition_trace.winscope"
        private const val SHELL_ASSET = "shell_transition_trace.winscope"

        val START_TIME =
            Timestamps.from(elapsedNanos = 760760231809, systemUptimeNanos = 0, unixNanos = 0)
        val VALID_SLICE_TIME =
            Timestamps.from(
                elapsedNanos = 2770105426934 - 1000,
                systemUptimeNanos = 0,
                unixNanos = 0
            )
        val INVALID_SLICE_TIME =
            Timestamps.from(
                elapsedNanos = 2770105426934 + 1,
                systemUptimeNanos = 0,
                unixNanos = 0,
            )
        val END_TIME =
            Timestamps.from(elapsedNanos = 2770105426934, systemUptimeNanos = 0, unixNanos = 0)

        val WM_FILE
            get() = readAssetAsFile(WM_ASSET)
        val SHELL_FILE
            get() = readAssetAsFile(SHELL_ASSET)
    }

    object TransitionTrace {
        private const val ASSET = "transitions.perfetto-trace"

        val START_TIME =
            Timestamps.from(elapsedNanos = 479583450794, systemUptimeNanos = 0, unixNanos = 0)
        val VALID_SLICE_TIME =
            Timestamps.from(
                elapsedNanos = 479583450794 + 5000,
                systemUptimeNanos = 0,
                unixNanos = 0
            )
        val INVALID_SLICE_TIME =
            Timestamps.from(
                elapsedNanos = 487330863192 + 1,
                systemUptimeNanos = 0,
                unixNanos = 0,
            )
        val END_TIME =
            Timestamps.from(elapsedNanos = 487330863192, systemUptimeNanos = 0, unixNanos = 0)

        val FILE
            get() = readAssetAsFile(ASSET)
    }

    object ProtoLogTrace {
        private const val ASSET = "protolog.perfetto-trace"

        val START_TIME =
            Timestamps.from(elapsedNanos = 479583450794, systemUptimeNanos = 0, unixNanos = 0)
        val VALID_SLICE_TIME =
            Timestamps.from(
                elapsedNanos = 479583450794 + 5000,
                systemUptimeNanos = 0,
                unixNanos = 0
            )
        val INVALID_SLICE_TIME =
            Timestamps.from(
                elapsedNanos = 487330863192 + 1,
                systemUptimeNanos = 0,
                unixNanos = 0,
            )
        val END_TIME =
            Timestamps.from(elapsedNanos = 487330863192, systemUptimeNanos = 0, unixNanos = 0)

        val FILE
            get() = readAssetAsFile(ASSET)
    }

    val TIME_5 = Timestamps.from(5, 5, 5)
    val TIME_10 = Timestamps.from(10, 10, 10)

    val TEST_TRACE_CONFIG =
        TraceConfigs(
            wmTrace =
                TraceConfig(required = false, allowNoChange = false, usingExistingTraces = false),
            layersTrace =
                TraceConfig(required = false, allowNoChange = false, usingExistingTraces = false),
            transitionsTrace =
                TraceConfig(required = false, allowNoChange = false, usingExistingTraces = false),
            transactionsTrace =
                TraceConfig(required = false, allowNoChange = false, usingExistingTraces = false)
        )
}
