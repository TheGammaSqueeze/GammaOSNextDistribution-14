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

package android.tools.device.apphelpers

import android.app.Instrumentation
import android.tools.traces.component.ComponentNameMatcher
import androidx.test.platform.app.InstrumentationRegistry

/**
 * Helper to launch the calculator app (not compatible with AOSP)
 *
 * This helper has no other functionality but the app launch.
 */
class CalculatorAppHelper
@JvmOverloads
constructor(instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()) :
    StandardAppHelper(
        instrumentation,
        "Calculator",
        ComponentNameMatcher(
            packageName = "com.google.android.calculator",
            className = "com.android.calculator2.Calculator"
        )
    )
