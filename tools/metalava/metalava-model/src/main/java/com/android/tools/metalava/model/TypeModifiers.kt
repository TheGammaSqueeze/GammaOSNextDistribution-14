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

package com.android.tools.metalava.model

/**
 * Modifiers for a [TypeItem], analogous to [ModifierList]s for [Item]s. Contains type-use
 * annotation information.
 */
interface TypeModifiers {
    /** The type-use annotations applied to the owning type. */
    fun annotations(): List<AnnotationItem>

    /** Adds the [annotation] to the list of annotations for the type. */
    fun addAnnotation(annotation: AnnotationItem)

    /** Removes the [annotation] from the list of annotations for the type, if it was present. */
    fun removeAnnotation(annotation: AnnotationItem)

    /** The nullability of the type. */
    fun nullability(): TypeNullability

    /**
     * Updates the nullability of the type to [newNullability]. Does not add or remove any nullness
     * annotations, so those should be handled separately through [addAnnotation] and/or
     * [removeAnnotation], if needed.
     */
    fun setNullability(newNullability: TypeNullability)

    /** Create a copy of this to which modifications can be made. */
    fun duplicate(withNullability: TypeNullability? = null): TypeModifiers
}

/** An enum representing the possible nullness values of a type. */
enum class TypeNullability(
    /** Kotlin nullability suffix. */
    val suffix: String,
    /**
     * Whether this represents a known nullability, `true` for [NULLABLE] and [NONNULL], `false`
     * otherwise.
     */
    val isKnown: Boolean = false,
) {
    /**
     * Nullability for a type that is annotated non-null, is primitive, or defined as non-null in
     * Kotlin.
     */
    NONNULL("", true),
    /** Nullability for a type that is annotated nullable or defined as nullable in Kotlin. */
    NULLABLE("?", true),
    /** Nullability for a Java type without a specified nullability. */
    PLATFORM("!"),
    /**
     * The nullability for a type without defined nullness. Examples include:
     * - A Kotlin type variable with inherited nullability.
     * - Wildcard types (nullness is defined through the bounds of the wildcard).
     */
    UNDEFINED("");

    companion object {
        /** Given a nullness [annotation], returns the corresponding [TypeNullability]. */
        fun ofAnnotation(annotation: AnnotationItem): TypeNullability {
            return if (isNullableAnnotation(annotation.qualifiedName.orEmpty())) {
                NULLABLE
            } else if (isNonNullAnnotation(annotation.qualifiedName.orEmpty())) {
                NONNULL
            } else {
                throw IllegalStateException("Not a nullness annotation: $annotation")
            }
        }
    }
}
