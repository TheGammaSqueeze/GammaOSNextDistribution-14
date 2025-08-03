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

package com.android.tools.metalava.model.text

import com.android.tools.metalava.model.ClassKind
import com.android.tools.metalava.model.JAVA_LANG_THROWABLE

/** The kind of the stub class that must be created. */
internal enum class StubKind(
    /** Make kind specific modifications to the [StubClassBuilder]. */
    val mutator: StubClassBuilder.() -> Unit,
) {
    CLASS({}),
    INTERFACE({ classKind = ClassKind.INTERFACE }),
    THROWABLE({
        // Throwables must extend `java.lang.Throwable`, unless they are `java.lang.Throwable`.
        if (qualifiedName != JAVA_LANG_THROWABLE) {
            superClassType = codebase.resolveClass(JAVA_LANG_THROWABLE).type()
        }
    }),
}
