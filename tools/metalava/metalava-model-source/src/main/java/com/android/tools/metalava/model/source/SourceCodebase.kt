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

package com.android.tools.metalava.model.source

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Codebase

/**
 * Extends [Codebase] to add any additional members needed for [Codebase] implementations created
 * from source files.
 */
interface SourceCodebase : Codebase {
    /**
     * Returns a list of the top-level classes declared in the codebase's source (rather than on its
     * classpath).
     */
    fun getTopLevelClassesFromSource(): List<ClassItem>

    // False since this codebase would be reading from source
    override fun trustedApi(): Boolean = false
}
