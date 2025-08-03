/*
 * Copyright (C) 2017 The Android Open Source Project
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

interface ModifierList {
    val codebase: Codebase

    fun annotations(): List<AnnotationItem>

    fun owner(): Item

    fun getVisibilityLevel(): VisibilityLevel

    fun isPublic(): Boolean

    fun isProtected(): Boolean

    fun isPrivate(): Boolean

    @MetalavaApi fun isStatic(): Boolean

    fun isAbstract(): Boolean

    fun isFinal(): Boolean

    fun isNative(): Boolean

    fun isSynchronized(): Boolean

    fun isStrictFp(): Boolean

    fun isTransient(): Boolean

    fun isVolatile(): Boolean

    fun isDefault(): Boolean

    // Modifier in Kotlin, separate syntax (...) in Java but modeled as modifier here
    fun isVarArg(): Boolean = false

    // Kotlin
    fun isSealed(): Boolean = false

    fun isFunctional(): Boolean = false

    fun isCompanion(): Boolean = false

    fun isInfix(): Boolean = false

    fun isConst(): Boolean = false

    fun isSuspend(): Boolean = false

    fun isOperator(): Boolean = false

    fun isInline(): Boolean = false

    fun isValue(): Boolean = false

    fun isData(): Boolean = false

    fun isExpect(): Boolean = false

    fun isActual(): Boolean = false

    fun isEmpty(): Boolean

    fun isPackagePrivate() = !(isPublic() || isProtected() || isPrivate())

    fun isPublicOrProtected() = isPublic() || isProtected()

    // Rename? It's not a full equality, it's whether an override's modifier set is significant
    fun equivalentTo(other: ModifierList): Boolean {
        if (isPublic() != other.isPublic()) return false
        if (isProtected() != other.isProtected()) return false
        if (isPrivate() != other.isPrivate()) return false

        if (isStatic() != other.isStatic()) return false
        if (isAbstract() != other.isAbstract()) return false
        if (isFinal() != other.isFinal()) {
            return false
        }
        if (isTransient() != other.isTransient()) return false
        if (isVolatile() != other.isVolatile()) return false

        // Default does not require an override to "remove" it
        // if (isDefault() != other.isDefault()) return false

        return true
    }

    /** Returns true if this modifier list contains any nullness information */
    fun hasNullnessInfo(): Boolean = hasAnnotation(AnnotationItem::isNullnessAnnotation)

    /** Returns true if this modifier list contains any a Nullable annotation */
    fun isNullable(): Boolean = hasAnnotation(AnnotationItem::isNullable)

    /** Returns true if this modifier list contains any a NonNull annotation */
    fun isNonNull(): Boolean = hasAnnotation(AnnotationItem::isNonNull)

    /** Returns true if this modifier list contains the `@JvmSynthetic` annotation */
    fun hasJvmSyntheticAnnotation(): Boolean = hasAnnotation(AnnotationItem::isJvmSynthetic)

    /**
     * Returns true if this modifier list contains any suppress compatibility meta-annotations.
     *
     * Metalava will suppress compatibility checks for APIs which are within the scope of a
     * "suppress compatibility" meta-annotation, but they may still be written to API files or stub
     * JARs.
     *
     * "Suppress compatibility" meta-annotations allow Metalava to handle concepts like Jetpack
     * experimental APIs, where developers can use the [RequiresOptIn] meta-annotation to mark
     * feature sets with unstable APIs.
     */
    fun hasSuppressCompatibilityMetaAnnotations(): Boolean {
        return codebase.annotationManager.hasSuppressCompatibilityMetaAnnotations(this)
    }

    /** Returns true if this modifier list contains the given annotation */
    fun isAnnotatedWith(qualifiedName: String): Boolean {
        return findAnnotation(qualifiedName) != null
    }

    /**
     * Returns the annotation of the given qualified name (or equivalent) if found in this modifier
     * list
     */
    fun findAnnotation(qualifiedName: String): AnnotationItem? {
        val mappedName = codebase.annotationManager.normalizeInputName(qualifiedName)
        return findAnnotation { mappedName == it.qualifiedName }
    }

    /**
     * Returns true if the visibility modifiers in this modifier list is as least as visible as the
     * ones in the given [other] modifier list
     */
    fun asAccessibleAs(other: ModifierList): Boolean {
        val otherLevel = other.getVisibilityLevel()
        val thisLevel = getVisibilityLevel()
        // Generally the access level enum order determines relative visibility. However, there is
        // an exception because
        // package private and internal are not directly comparable.
        val result = thisLevel >= otherLevel
        return when (otherLevel) {
            VisibilityLevel.PACKAGE_PRIVATE -> result && thisLevel != VisibilityLevel.INTERNAL
            VisibilityLevel.INTERNAL -> result && thisLevel != VisibilityLevel.PACKAGE_PRIVATE
            else -> result
        }
    }

    /** User visible description of the visibility in this modifier list */
    fun getVisibilityString(): String {
        return getVisibilityLevel().userVisibleDescription
    }

    /**
     * Like [getVisibilityString], but package private has no modifiers; this typically corresponds
     * to the source code for the visibility modifiers in the modifier list
     */
    fun getVisibilityModifiers(): String {
        return getVisibilityLevel().javaSourceCodeModifier
    }
}

/**
 * Returns the first annotation in the modifier list that matches the supplied predicate, or null
 * otherwise.
 */
inline fun ModifierList.findAnnotation(predicate: (AnnotationItem) -> Boolean): AnnotationItem? {
    return annotations().firstOrNull(predicate)
}

/**
 * Returns true iff the modifier list contains any annotation that matches the supplied predicate.
 */
inline fun ModifierList.hasAnnotation(predicate: (AnnotationItem) -> Boolean): Boolean {
    return annotations().any(predicate)
}
