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
package com.android.intentresolver.v2.ext

import android.content.Intent
import java.util.function.Predicate

/** Applies an operation on this Intent if matches the given filter. */
inline fun Intent.ifMatch(
    predicate: Predicate<Intent>,
    crossinline block: Intent.() -> Unit
): Intent {
    if (predicate.test(this)) {
        apply(block)
    }
    return this
}

/** True if the Intent has one of the specified actions. */
fun Intent.hasAction(vararg actions: String): Boolean = action in actions

/** True if the Intent has a specific component target */
fun Intent.hasComponent(): Boolean = (component != null)

/** True if the Intent has a single matching category. */
fun Intent.hasSingleCategory(category: String) = categories.singleOrNull() == category

/** True if the Intent is a SEND or SEND_MULTIPLE action. */
fun Intent.hasSendAction() = hasAction(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE)

/** True if the Intent resolves to the special Home (Launcher) component */
fun Intent.isHomeIntent() = hasAction(Intent.ACTION_MAIN) && hasSingleCategory(Intent.CATEGORY_HOME)
