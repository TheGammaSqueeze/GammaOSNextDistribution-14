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

package com.android.tools.metalava

import com.android.tools.metalava.cli.common.SignatureFileLoader
import com.android.tools.metalava.model.AnnotationManager
import com.android.tools.metalava.model.ClassResolver
import com.android.tools.metalava.model.Codebase
import java.io.File

private data class CacheKey(val files: List<File>, val classResolver: ClassResolver?) {
    fun load(signatureFileLoader: SignatureFileLoader): Codebase =
        if (files.size == 1) {
            signatureFileLoader.load(files.single(), classResolver)
        } else {
            signatureFileLoader.loadFiles(files, classResolver)
        }
}

/** Loads signature files, caching them for reuse where appropriate. */
class SignatureFileCache(annotationManager: AnnotationManager) {
    private val signatureFileLoader = SignatureFileLoader(annotationManager)
    private val map = mutableMapOf<CacheKey, Codebase>()

    fun load(file: File, classResolver: ClassResolver? = null): Codebase =
        load(listOf(file), classResolver)

    fun load(files: List<File>, classResolver: ClassResolver? = null): Codebase {
        val key = CacheKey(files, classResolver)
        return map.computeIfAbsent(key) { k ->
            signatureFileLoader.loadFiles(k.files, k.classResolver)
        }
    }
}
