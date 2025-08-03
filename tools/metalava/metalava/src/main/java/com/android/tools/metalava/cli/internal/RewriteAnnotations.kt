/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tools.metalava.cli.internal

import com.android.SdkConstants
import com.android.tools.metalava.ANDROID_NONNULL
import com.android.tools.metalava.ANDROID_NULLABLE
import com.android.tools.metalava.ANDROID_SDK_CONSTANT
import com.android.tools.metalava.RECENTLY_NONNULL
import com.android.tools.metalava.RECENTLY_NULLABLE
import com.android.tools.metalava.model.AnnotationRetention
import com.android.tools.metalava.model.Codebase
import java.io.File
import kotlin.text.Charsets.UTF_8

/**
 * Converts public stub annotation sources into package private annotation sources. This is needed
 * for the stub sources, where we want to reference annotations that aren't public, but (a) they
 * need to be public during compilation, and (b) they need to be package private when compiled and
 * packaged on their own such that annotation processors can find them. See b/110532131 for details.
 */
internal class RewriteAnnotations {
    /** Modifies annotation source files such that they are package private */
    fun modifyAnnotationSources(codebase: Codebase?, source: File, target: File, pkg: String = "") {
        val fileName = source.name
        if (fileName.endsWith(SdkConstants.DOT_JAVA)) {
            // Only copy non-source retention annotation classes
            val qualifiedName = pkg + "." + fileName.substring(0, fileName.indexOf('.'))
            if (hasSourceRetention(source, codebase, qualifiedName)) {
                return
            }

            // Copy and convert
            target.parentFile.mkdirs()
            target.writeText(source.readText(UTF_8).replace("\npublic @interface", "\n@interface"))
        } else if (source.isDirectory) {
            val newPackage = if (pkg.isEmpty()) fileName else "$pkg.$fileName"
            source.listFiles()?.forEach {
                modifyAnnotationSources(codebase, it, File(target, it.name), newPackage)
            }
        }
    }

    /**
     * Returns true if the given annotation class name has source retention as far as the stub
     * annotations are concerned.
     */
    private fun hasSourceRetention(
        source: File,
        codebase: Codebase?,
        qualifiedName: String
    ): Boolean {
        when {
            qualifiedName == RECENTLY_NULLABLE ||
                qualifiedName == RECENTLY_NONNULL ||
                qualifiedName == ANDROID_NULLABLE ||
                qualifiedName == ANDROID_NONNULL -> return false
            qualifiedName == ANDROID_SDK_CONSTANT -> return true
            qualifiedName.startsWith("androidx.annotation.") -> return true
        }

        // See if the annotation is pointing to an annotation class that is part of the API; if not,
        // skip it.
        if (codebase != null) {
            val cls = codebase.findClass(qualifiedName) ?: return true
            return cls.isAnnotationType() && cls.getRetention() == AnnotationRetention.SOURCE
        } else {
            error("$source: Found annotation with unknown desired retention: $qualifiedName")
        }
    }
}
