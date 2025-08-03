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
import com.android.tools.metalava.model.BoundsTypeItem
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.ExceptionTypeItem
import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeNullability
import com.android.tools.metalava.model.TypeParameterItem
import com.android.tools.metalava.model.TypeParameterScope
import com.android.tools.metalava.model.WildcardTypeItem
import com.android.tools.metalava.model.typeNullability

/**
 * Factory for building [TypeItem] from the underlying model specific representation of the type.
 *
 * The purpose of this is to abstract away all the model specific details of type construction in a
 * consistent manner across the models so that the types can be constructed in a consistent manner.
 * It provides specialist functions for different type uses that can ensure the specific constraints
 * of those type uses are enforced. e.g. types used for super class and interfaces cannot be
 * nullable.
 *
 * At the moment the specialist functions are limited to just a few different types but over time it
 * will be expanded as more type creation logic is moved inside.
 *
 * @param T the underlying model specific representation of a type.
 * @param F the type of the factory implementation.
 */
interface TypeItemFactory<in T, F : TypeItemFactory<T, F>> {

    /**
     * The [TypeParameterScope] that is used by this factory to resolve references to
     * [TypeParameterItem]s.
     */
    val typeParameterScope: TypeParameterScope

    /**
     * Create a [TypeItemFactory] that can resolve references to the [typeParameters].
     *
     * Returns `this` if [typeParameters] is empty, otherwise returns a new [TypeItemFactory] with a
     * new [typeParameterScope] with the specified [scopeDescription] and containing the supplied
     * [typeParameters].
     */
    fun nestedFactory(scopeDescription: String, typeParameters: List<TypeParameterItem>): F

    /** Get a type suitable for use in a wildcard type bounds clause. */
    fun getBoundsType(underlyingType: T): BoundsTypeItem

    /** Get a type suitable for use in a `throws` list. */
    fun getExceptionType(underlyingType: T): ExceptionTypeItem

    /**
     * Get a general type suitable for use anywhere not covered by one of the more specific type
     * methods in this.
     */
    fun getGeneralType(underlyingType: T): TypeItem

    /**
     * Get a type suitable for use in an `implements` list of a concrete class, or an `extends` list
     * of an interface class.
     */
    fun getInterfaceType(underlyingType: T): ClassTypeItem

    /** Get a type suitable for use in an `extends` clause of a concrete class. */
    fun getSuperClassType(underlyingType: T): ClassTypeItem

    // Item specific type methods.

    /**
     * Get the type for a field.
     *
     * This considers a number of factors, in addition to the declared type, to determine the
     * appropriate [TypeNullability] for the field type, i.e.:
     * * Setting of [isEnumConstant]; if it is `true` then it is always [TypeNullability.NONNULL].
     * * If the field is `final` then the nullability of the field's value can be considered
     *   ([isInitialValueNonNull]). Otherwise, it cannot as it may change over the lifetime of the
     *   field.
     *
     * @param underlyingType the underlying model's type.
     * @param isFinal `true` if the field is `final`.
     * @param isEnumConstant `true` if the field is actually an enum constant.
     * @param isInitialValueNonNull a lambda that will be invoked on `final` fields to determine
     *   whether its initial value is non-null. This is a lambda as the determination of the initial
     *   value may be expensive.
     */
    fun getFieldType(
        underlyingType: T,
        itemAnnotations: List<AnnotationItem>,
        isEnumConstant: Boolean,
        isFinal: Boolean,
        isInitialValueNonNull: () -> Boolean,
    ): TypeItem = error("unsupported")
}

/**
 * Encapsulates the information necessary to compute the [TypeNullability] from a variety of
 * different sources with differing priorities.
 *
 * The priorities are:
 * 1. Forced by specification, e.g. enum constants, super class types, primitives.
 * 2. Kotlin; ignoring [TypeNullability.PLATFORM].
 * 3. Annotations.
 * 4. Nullability inferred from context, e.g. constant field with non-null value.
 * 4. [TypeNullability.PLATFORM]
 */
class ContextNullability(
    /**
     * The [TypeNullability] that a [TypeItem] MUST have by virtue of what the type is, or where it
     * is used; e.g. [PrimitiveTypeItem]s and super class types MUST be [TypeNullability.NONNULL]
     * while [WildcardTypeItem]s MUST be [TypeNullability.UNDEFINED].
     *
     * This CANNOT be overridden by a nullability annotation.
     */
    val forcedNullability: TypeNullability? = null,

    /**
     * A [TypeNullability] that can be inferred from the context.
     *
     * It is passed as a lambda as it may be expensive to compute.
     */
    val inferNullability: (() -> TypeNullability?)? = null,
) {
    /**
     * Compute the [TypeNullability] according to the priority in the documentation for this class.
     */
    fun compute(
        kotlinNullability: TypeNullability?,
        typeAnnotations: List<AnnotationItem>
    ): TypeNullability =
        // If forced is set then use that as the top priority.
        forcedNullability
        // If kotlin provides it then use that as it is most accurate, ignore PLATFORM though
        // as that may be overridden by annotations or the default.
        ?: kotlinNullability?.takeIf { nullability -> nullability != TypeNullability.PLATFORM }
            // If annotations provide it then use them as the developer requested.
            ?: typeAnnotations.typeNullability
            // If an inferred nullability is provided then use it.
            ?: inferNullability?.invoke()
            // Finally default to [TypeNullability.PLATFORM].
            ?: TypeNullability.PLATFORM

    companion object {
        val none = ContextNullability()
        val forceNonNull = ContextNullability(TypeNullability.NONNULL)
        val forceUndefined = ContextNullability(TypeNullability.UNDEFINED)
    }
}

abstract class DefaultTypeItemFactory<in T, F : DefaultTypeItemFactory<T, F>>(
    final override val typeParameterScope: TypeParameterScope
) : TypeItemFactory<T, F> {

    final override fun nestedFactory(
        scopeDescription: String,
        typeParameters: List<TypeParameterItem>
    ): F {
        val scope = typeParameterScope.nestedScope(scopeDescription, typeParameters)
        return if (scope === typeParameterScope) self() else createNestedFactory(scope)
    }

    override fun getBoundsType(underlyingType: T) = getType(underlyingType) as BoundsTypeItem

    override fun getExceptionType(underlyingType: T) = getType(underlyingType) as ExceptionTypeItem

    override fun getGeneralType(underlyingType: T) = getType(underlyingType)

    override fun getInterfaceType(underlyingType: T) = getSuperType(underlyingType)

    override fun getSuperClassType(underlyingType: T) = getSuperType(underlyingType)

    /**
     * Creates a [ClassTypeItem] that is suitable for use as a super type, e.g. in an `extends` or
     * `implements` list.
     */
    private fun getSuperType(underlyingType: T): ClassTypeItem {
        return getType(underlyingType, contextNullability = ContextNullability.forceNonNull)
            as ClassTypeItem
    }

    override fun getFieldType(
        underlyingType: T,
        itemAnnotations: List<AnnotationItem>,
        isEnumConstant: Boolean,
        isFinal: Boolean,
        isInitialValueNonNull: () -> Boolean
    ): TypeItem {
        // Get the context nullability. Enum constants are always non-null, item annotations and
        // whether a field is final and has a non-null value are used only if no other source of
        // information about nullability is available.
        val contextNullability =
            if (isEnumConstant) ContextNullability.forceNonNull
            else {
                ContextNullability(
                    inferNullability = {
                        // Check annotations from the item first, and then whether the field is
                        // final and has a non-null value.
                        itemAnnotations.typeNullability
                            ?: if (isFinal && isInitialValueNonNull()) TypeNullability.NONNULL
                            else null
                    }
                )
            }

        // Get the field's type, passing in the context nullability.
        return getType(underlyingType, contextNullability = contextNullability)
    }

    /** Type safe access to `this`. */
    protected abstract fun self(): F

    /** Create a nested factory that is a copy of this one, except using [scope]. */
    protected abstract fun createNestedFactory(scope: TypeParameterScope): F

    /**
     * Get the [TypeItem] corresponding to the [underlyingType] and within the [contextNullability].
     */
    protected abstract fun getType(
        underlyingType: T,
        contextNullability: ContextNullability = ContextNullability.none,
    ): TypeItem
}
