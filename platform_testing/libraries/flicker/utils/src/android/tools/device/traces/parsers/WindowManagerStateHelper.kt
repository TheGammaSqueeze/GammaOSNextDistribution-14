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

package android.tools.device.traces.parsers

import android.app.Instrumentation
import androidx.test.platform.app.InstrumentationRegistry

@Deprecated(
    "Please use the version in the android.tools.traces.parsers package instead",
    replaceWith = ReplaceWith("android.tools.traces.parsers.WindowManagerStateHelper")
)
class WindowManagerStateHelper @JvmOverloads
constructor(
    instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
): android.tools.traces.parsers.WindowManagerStateHelper(instrumentation)