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

package com.android.tools.metalava.model.type

import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeModifiers
import com.android.tools.metalava.model.TypeNullability

/** Modifiers for a [TypeItem]. */
class DefaultTypeModifiers(
    private val annotations: MutableList<AnnotationItem>,
    private var nullability: TypeNullability,
    /**
     * If non-null then this causes this instance to be treated as immutable and any attempt to
     * mutate it will throw an exception with this as the reason.
     */
    private val immutableReason: String? = null,
) : TypeModifiers {

    private fun ensureMutable() {
        immutableReason?.let { reason -> error(reason) }
    }

    override fun annotations(): List<AnnotationItem> = annotations

    override fun addAnnotation(annotation: AnnotationItem) {
        ensureMutable()
        annotations.add(annotation)
    }

    override fun removeAnnotation(annotation: AnnotationItem) {
        ensureMutable()
        annotations.remove(annotation)
    }

    override fun nullability(): TypeNullability {
        return nullability
    }

    override fun setNullability(newNullability: TypeNullability) {
        if (newNullability == nullability) return
        ensureMutable()
        nullability = newNullability
    }

    override fun duplicate(withNullability: TypeNullability?) =
        DefaultTypeModifiers(annotations.toMutableList(), withNullability ?: nullability)

    companion object {
        /** A set of empty, non-null [TypeModifiers] for sharing. */
        val emptyNonNullModifiers =
            create(emptyList(), TypeNullability.NONNULL, "emptyNonNullModifiers is shared")

        /**
         * Create a [DefaultTypeModifiers].
         *
         * If [knownNullability] is `null` then this will compute nullability from the
         * [annotations], if any, and if not then default to platform nullness.
         */
        fun create(
            annotations: List<AnnotationItem>,
            knownNullability: TypeNullability? = null,
            immutableReason: String? = null,
        ): TypeModifiers {
            // Use the known nullability, or find if there is a nullness annotation on the type,
            // defaulting to platform nullness if not.
            val nullability =
                knownNullability
                    ?: annotations
                        .firstOrNull { it.isNullnessAnnotation() }
                        ?.let { TypeNullability.ofAnnotation(it) }
                        ?: TypeNullability.PLATFORM
            return DefaultTypeModifiers(annotations.toMutableList(), nullability, immutableReason)
        }
    }
}
