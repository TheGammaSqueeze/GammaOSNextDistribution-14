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

package android.tools

/**
 * Identify a trace location. By default, all traces have: [START], [END] and [ALL] locations,
 * representing initial, final and all trace states.
 *
 * In addition, it is possible to create custom trace locations (tags).
 */
object Tag {
    const val START = "start"
    const val END = "end"
    const val ALL = "all"
}
