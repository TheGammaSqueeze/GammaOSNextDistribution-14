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

package android.tools.flicker.config.common

import android.tools.flicker.assertors.ComponentTemplate
import android.tools.traces.component.ComponentNameMatcher

object Components {
    val NAV_BAR = ComponentTemplate("Navbar") { ComponentNameMatcher.NAV_BAR }
    val STATUS_BAR = ComponentTemplate("StatusBar") { ComponentNameMatcher.STATUS_BAR }
    val LAUNCHER = ComponentTemplate("Launcher") { ComponentNameMatcher.LAUNCHER }
}
