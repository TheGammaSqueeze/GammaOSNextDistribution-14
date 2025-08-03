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

@file:JvmName("Consts")

package android.tools.traces

internal const val LOG_TAG = "FLICKER-PARSER"

val TRACE_CONFIG_REQUIRE_CHANGES =
    TraceConfigs(
        wmTrace = TraceConfig(required = true, allowNoChange = false, usingExistingTraces = false),
        layersTrace =
            TraceConfig(required = true, allowNoChange = false, usingExistingTraces = false),
        transitionsTrace =
            TraceConfig(required = false, allowNoChange = false, usingExistingTraces = false),
        transactionsTrace =
            TraceConfig(required = false, allowNoChange = false, usingExistingTraces = false)
    )

val SERVICE_TRACE_CONFIG =
    TraceConfigs(
        wmTrace = TraceConfig(required = false, allowNoChange = true, usingExistingTraces = false),
        layersTrace =
            TraceConfig(required = true, allowNoChange = true, usingExistingTraces = false),
        transitionsTrace =
            TraceConfig(required = true, allowNoChange = true, usingExistingTraces = false),
        transactionsTrace =
            TraceConfig(required = true, allowNoChange = true, usingExistingTraces = false)
    )
