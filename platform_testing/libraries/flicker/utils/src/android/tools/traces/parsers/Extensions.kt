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

@file:JvmName("Extensions")

package android.tools.traces.parsers

import android.content.ComponentName
import android.tools.datatypes.Rect
import android.tools.traces.component.ComponentNameMatcher

fun Rect.toAndroidRect(): android.graphics.Rect {
    return android.graphics.Rect(left, top, right, bottom)
}

fun android.graphics.Rect.toFlickerRect(): Rect {
    return Rect.from(left, top, right, bottom)
}

/** Converts an Android [ComponentName] into a flicker [ComponentNameMatcher] */
fun ComponentName.toFlickerComponent(): ComponentNameMatcher =
    ComponentNameMatcher(this.packageName, this.className)
