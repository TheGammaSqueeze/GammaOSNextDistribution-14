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

package android.tools.io

/** Types of traces/dumps that cna be in a flicker result */
enum class TraceType(val fileName: String, val isTrace: Boolean) {
    SF("trace$PERFETTO_EXT", isTrace = true),
    WM("wm_trace$WINSCOPE_EXT", isTrace = true),
    TRANSACTION("trace$PERFETTO_EXT", isTrace = true),
    LEGACY_WM_TRANSITION("wm_transition_trace$WINSCOPE_EXT", isTrace = true),
    LEGACY_SHELL_TRANSITION("shell_transition_trace$WINSCOPE_EXT", isTrace = true),
    EVENT_LOG("eventlog$WINSCOPE_EXT", isTrace = true),
    SCREEN_RECORDING("transition.mp4", isTrace = true),
    SF_DUMP("trace$PERFETTO_EXT", isTrace = false),
    WM_DUMP("trace$WINSCOPE_EXT", isTrace = false),
    PROTOLOG("wm_log$WINSCOPE_EXT", isTrace = true),
    VIEW("view_capture_trace$WINSCOPE_EXT", isTrace = true),
    PERFETTO("trace$PERFETTO_EXT", isTrace = true),
}
