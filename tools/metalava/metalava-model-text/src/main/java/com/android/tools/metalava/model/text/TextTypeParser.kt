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

package com.android.tools.metalava.model.text

import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.ArrayTypeItem
import com.android.tools.metalava.model.BaseTypeVisitor
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.DefaultAnnotationItem
import com.android.tools.metalava.model.JAVA_LANG_ANNOTATION
import com.android.tools.metalava.model.JAVA_LANG_ENUM
import com.android.tools.metalava.model.JAVA_LANG_OBJECT
import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.ReferenceTypeItem
import com.android.tools.metalava.model.TypeArgumentTypeItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeModifiers
import com.android.tools.metalava.model.TypeNullability
import com.android.tools.metalava.model.TypeParameterScope
import com.android.tools.metalava.model.TypeUse
import com.android.tools.metalava.model.TypeVisitor
import com.android.tools.metalava.model.VariableTypeItem
import com.android.tools.metalava.model.WildcardTypeItem
import com.android.tools.metalava.model.type.DefaultArrayTypeItem
import com.android.tools.metalava.model.type.DefaultClassTypeItem
import com.android.tools.metalava.model.type.DefaultPrimitiveTypeItem
import com.android.tools.metalava.model.type.DefaultTypeModifiers
import com.android.tools.metalava.model.type.DefaultVariableTypeItem
import com.android.tools.metalava.model.type.DefaultWildcardTypeItem
import kotlin.collections.HashMap

/** Parses and caches types for a [codebase]. */
internal class TextTypeParser(val codebase: Codebase, val kotlinStyleNulls: Boolean = false) {

    /**
     * The cache key, incorporates the information from [TypeUse] and [kotlinStyleNulls] as well as
     * the type string as they can all affect the created [TypeItem].
     *
     * e.g. [TypeUse.SUPER_TYPE] will cause the type to be treated as a super class and so always be
     * [TypeNullability.NONNULL] even if [kotlinStyleNulls] is `false` which would normally cause it
     * to be [TypeNullability.PLATFORM]. However, when [kotlinStyleNulls] is `true` then there is no
     * difference between [TypeUse.SUPER_TYPE] and [TypeUse.GENERAL] as they will both cause a class
     * type with no nullability suffix to be treated as [TypeNullability.NONNULL].
     *
     * That information is encapsulated in the [forceClassToBeNonNull] property.
     */
    private data class Key(val forceClassToBeNonNull: Boolean, val type: String)

    /** The cache from [Key] to [CacheEntry]. */
    private val typeCache = HashMap<Key, CacheEntry>()

    internal var requests = 0
    internal var cacheSkip = 0
    internal var cacheHit = 0
    internal var cacheSize = 0

    /** A [JAVA_LANG_ANNOTATION] suitable for use as a super type. */
    val superAnnotationType
        get() = createJavaLangSuperType(JAVA_LANG_ANNOTATION)

    /** A [JAVA_LANG_ENUM] suitable for use as a super type. */
    val superEnumType
        get() = createJavaLangSuperType(JAVA_LANG_ENUM)

    /** A [JAVA_LANG_OBJECT] suitable for use as a super type. */
    val superObjectType
        get() = createJavaLangSuperType(JAVA_LANG_OBJECT)

    /**
     * Create a [ClassTypeItem] for a standard java.lang class suitable for use by a super class or
     * interface.
     */
    private fun createJavaLangSuperType(standardClassName: String): ClassTypeItem {
        return getSuperType(standardClassName, TypeParameterScope.empty)
    }

    /** A [TypeItem] representing `java.lang.Object`, suitable for general use. */
    private val objectType: ReferenceTypeItem
        get() = cachedParseType(JAVA_LANG_OBJECT, TypeParameterScope.empty) as ReferenceTypeItem

    /**
     * Creates or retrieves a previously cached [ClassTypeItem] that is suitable for use as a super
     * type, e.g. in an `extends` or `implements` list.
     */
    fun getSuperType(
        type: String,
        typeParameterScope: TypeParameterScope,
    ): ClassTypeItem =
        obtainTypeFromString(type, typeParameterScope, TypeUse.SUPER_TYPE) as ClassTypeItem

    /**
     * Creates or retrieves from the cache a [TypeItem] representing [type], in the context of the
     * type parameters from [typeParameterScope], if applicable.
     */
    fun obtainTypeFromString(
        type: String,
        typeParameterScope: TypeParameterScope,
        typeUse: TypeUse = TypeUse.GENERAL,
    ): TypeItem = cachedParseType(type, typeParameterScope, emptyList(), typeUse)

    /**
     * Creates or retrieves from the cache a [TypeItem] representing [type], in the context of the
     * type parameters from [typeParameterScope], if applicable.
     *
     * Used internally, as it has an extra [annotations] parameter that allows the annotations on
     * array components to be correctly associated with the correct component. They are optional
     * leading type-use annotations that have already been removed from the arrays type string.
     */
    private fun cachedParseType(
        type: String,
        typeParameterScope: TypeParameterScope,
        annotations: List<AnnotationItem> = emptyList(),
        typeUse: TypeUse = TypeUse.GENERAL,
    ): TypeItem {
        requests++

        // Class types used as super types, i.e. in an extends or implements list are forced to be
        // [TypeNullability.NONNULL], just as they would be if kotlinStyleNulls was true. Use the
        // same cache key for both so that they reuse cached types where possible.
        val forceClassToBeNonNull = typeUse == TypeUse.SUPER_TYPE || kotlinStyleNulls

        // Don't use the cache when there are type-use annotations not contained in the string.
        return if (annotations.isEmpty()) {
            val key = Key(forceClassToBeNonNull, type)

            // Get the cache entry for the supplied type and forceClassToBeNonNull.
            val result =
                typeCache.computeIfAbsent(key) { CacheEntry(it.type, it.forceClassToBeNonNull) }

            // Get the appropriate [TypeItem], creating one if necessary.
            result.getTypeItem(typeParameterScope)
        } else {
            cacheSkip++
            parseType(type, typeParameterScope, annotations, forceClassToBeNonNull)
        }
    }

    /** Converts the [type] to a [TypeItem] in the context of the [typeParameterScope]. */
    private fun parseType(
        type: String,
        typeParameterScope: TypeParameterScope,
        annotations: List<AnnotationItem>,
        // Forces a [ClassTypeItem] to have [TypeNullability.NONNULL]
        forceClassToBeNonNull: Boolean = false,
    ): TypeItem {
        val (unannotated, annotationsFromString) = trimLeadingAnnotations(type)
        val allAnnotations = annotations + annotationsFromString
        val (withoutNullability, nullability) =
            splitNullabilitySuffix(
                unannotated,
                // If forceClassToBeNonNull is true then a plain class type without any nullability
                // suffix must be treated as if it was not null, which is just how it would be
                // treated when kotlinStyleNulls is true. So, pretend that kotlinStyleNulls is true.
                kotlinStyleNulls || forceClassToBeNonNull
            )
        val trimmed = withoutNullability.trim()

        // Figure out what kind of type this is.
        //
        // Start with variable as the type parameter scope allows us to determine whether something
        // is a type parameter or not. Also, if a type parameter has the same name as a primitive
        // type (possible in Kotlin, but not Java) then it will be treated as a type parameter not a
        // primitive.
        //
        // Then try parsing as a primitive as while Kotlin classes can shadow primitive types
        // they would need to be fully qualified.
        return asVariable(trimmed, typeParameterScope, allAnnotations, nullability)
            ?: asPrimitive(type, trimmed, allAnnotations, nullability)
            // Try parsing as a wildcard before trying to parse as an array.
            // `? extends java.lang.String[]` should be parsed as a wildcard with an array bound,
            // not as an array of wildcards, for consistency with how this would be compiled.
            ?: asWildcard(trimmed, typeParameterScope, allAnnotations, nullability)
            // Try parsing as an array.
            ?: asArray(trimmed, allAnnotations, nullability, typeParameterScope)
            // If it isn't anything else, parse the type as a class.
            ?: asClass(trimmed, typeParameterScope, allAnnotations, nullability)
    }

    /**
     * Try parsing [type] as a primitive. This will return a non-null [PrimitiveTypeItem] if [type]
     * exactly matches a primitive name.
     *
     * [type] should have annotations and nullability markers stripped, with [original] as the
     * complete annotated type. Once annotations are properly handled (b/300081840), preserving
     * [original] won't be necessary.
     */
    private fun asPrimitive(
        original: String,
        type: String,
        annotations: List<AnnotationItem>,
        nullability: TypeNullability?
    ): PrimitiveTypeItem? {
        val kind =
            when (type) {
                "byte" -> PrimitiveTypeItem.Primitive.BYTE
                "char" -> PrimitiveTypeItem.Primitive.CHAR
                "double" -> PrimitiveTypeItem.Primitive.DOUBLE
                "float" -> PrimitiveTypeItem.Primitive.FLOAT
                "int" -> PrimitiveTypeItem.Primitive.INT
                "long" -> PrimitiveTypeItem.Primitive.LONG
                "short" -> PrimitiveTypeItem.Primitive.SHORT
                "boolean" -> PrimitiveTypeItem.Primitive.BOOLEAN
                "void" -> PrimitiveTypeItem.Primitive.VOID
                else -> return null
            }
        if (nullability != null && nullability != TypeNullability.NONNULL) {
            throw ApiParseException("Invalid nullability suffix on primitive: $original")
        }
        return DefaultPrimitiveTypeItem(modifiers(annotations, TypeNullability.NONNULL), kind)
    }

    /**
     * Try parsing [type] as an array. This will return a non-null [ArrayTypeItem] if [type] ends
     * with `[]` or `...`.
     *
     * The context [typeParameterScope] are used to parse the component type of the array.
     */
    private fun asArray(
        type: String,
        componentAnnotations: List<AnnotationItem>,
        nullability: TypeNullability?,
        typeParameterScope: TypeParameterScope
    ): ArrayTypeItem? {
        // Check if this is a regular array or varargs.
        val (inner, varargs) =
            if (type.endsWith("...")) {
                Pair(type.dropLast(3), true)
            } else if (type.endsWith("[]")) {
                Pair(type.dropLast(2), false)
            } else {
                return null
            }

        // Create lists of the annotations and nullability markers for each dimension of the array.
        // These are in separate lists because annotations appear in the type string in order from
        // outermost array annotations to innermost array annotations (for `T @A [] @B [] @ C[]`,
        // `@A` applies to the three-dimensional array, `@B` applies to the inner two-dimensional
        // arrays, and `@C` applies to the inner one-dimensional arrays), while nullability markers
        // appear in order from the innermost array nullability to the outermost array nullability
        // (for `T[]![]?[]`, the three-dimensional array has no nullability marker, the inner
        // two-dimensional arrays have `?` as the nullability marker, and the innermost arrays have
        // `!` as a nullability marker.
        val allAnnotations = mutableListOf<List<AnnotationItem>>()
        // The nullability marker for the outer array is already known, include it in the list.
        val allNullability = mutableListOf(nullability)

        // Remove annotations from the end of the string, add them to the list.
        var annotationsResult = trimTrailingAnnotations(inner)
        var componentString = annotationsResult.first
        allAnnotations.add(annotationsResult.second)

        // Remove nullability marker from the component type, but don't add it to the list yet, as
        // it might not be an array.
        var nullabilityResult = splitNullabilitySuffix(componentString, kotlinStyleNulls)
        componentString = nullabilityResult.first
        var componentNullability = nullabilityResult.second

        // Work through all layers of arrays to get to the inner component type.
        // Inner arrays can't be varargs.
        while (componentString.endsWith("[]")) {
            // The component is an array, add the nullability to the list.
            allNullability.add(componentNullability)

            // Remove annotations from the end of the string, add them to the list.
            annotationsResult = trimTrailingAnnotations(componentString.removeSuffix("[]"))
            componentString = annotationsResult.first
            allAnnotations.add(annotationsResult.second)

            // Remove nullability marker from the new component type, but don't add it to the list
            // yet, as the next component type might not be an array.
            nullabilityResult = splitNullabilitySuffix(componentString, kotlinStyleNulls)
            componentString = nullabilityResult.first
            componentNullability = nullabilityResult.second
        }

        // Re-add the component's nullability suffix when parsing the component type, and include
        // the leading annotations already removed from the type string.
        componentString += componentNullability?.suffix.orEmpty()
        val deepComponentType =
            cachedParseType(componentString, typeParameterScope, componentAnnotations)

        // Join the annotations and nullability markers -- as described in the comment above, these
        // appear in the string in reverse order of each other. The modifiers list will be ordered
        // from innermost array modifiers to outermost array modifiers.
        val allModifiers =
            allAnnotations.zip(allNullability.reversed()).map { (annotations, nullability) ->
                modifiers(annotations, nullability)
            }
        // The final modifiers are in the list apply to the outermost array.
        val componentModifiers = allModifiers.dropLast(1)
        val arrayModifiers = allModifiers.last()
        // Create the component type of the outermost array by building up the inner component type.
        val componentType =
            componentModifiers.fold(deepComponentType) { component, modifiers ->
                DefaultArrayTypeItem(modifiers, component, false)
            }

        // Create the outer array.
        return DefaultArrayTypeItem(arrayModifiers, componentType, varargs)
    }

    /**
     * Try parsing [type] as a wildcard. This will return a non-null [WildcardTypeItem] if [type]
     * begins with `?`.
     *
     * The context [typeParameterScope] are needed to parse the bounds of the wildcard.
     *
     * [type] should have annotations and nullability markers stripped.
     */
    private fun asWildcard(
        type: String,
        typeParameterScope: TypeParameterScope,
        annotations: List<AnnotationItem>,
        nullability: TypeNullability?
    ): WildcardTypeItem? {
        // See if this is a wildcard
        if (!type.startsWith("?")) return null

        // Unbounded wildcard type: there is an implicit Object extends bound
        if (type == "?")
            return DefaultWildcardTypeItem(
                modifiers(annotations, TypeNullability.UNDEFINED),
                objectType,
                null,
            )

        // If there's a bound, the nullability suffix applies there instead.
        val bound = type.substring(2) + nullability?.suffix.orEmpty()
        return if (bound.startsWith("extends")) {
            val extendsBound = bound.substring(8)
            DefaultWildcardTypeItem(
                modifiers(annotations, TypeNullability.UNDEFINED),
                getWildcardBound(extendsBound, typeParameterScope),
                null,
            )
        } else if (bound.startsWith("super")) {
            val superBound = bound.substring(6)
            DefaultWildcardTypeItem(
                modifiers(annotations, TypeNullability.UNDEFINED),
                // All wildcards have an implicit Object extends bound
                objectType,
                getWildcardBound(superBound, typeParameterScope),
            )
        } else {
            throw ApiParseException(
                "Type starts with \"?\" but doesn't appear to be wildcard: $type"
            )
        }
    }

    private fun getWildcardBound(bound: String, typeParameterScope: TypeParameterScope) =
        cachedParseType(bound, typeParameterScope) as ReferenceTypeItem

    /**
     * Try parsing [type] as a type variable. This will return a non-null [VariableTypeItem] if
     * [type] matches a parameter from [typeParameterScope].
     *
     * [type] should have annotations and nullability markers stripped.
     */
    private fun asVariable(
        type: String,
        typeParameterScope: TypeParameterScope,
        annotations: List<AnnotationItem>,
        nullability: TypeNullability?
    ): VariableTypeItem? {
        val param = typeParameterScope.findTypeParameter(type) ?: return null
        return DefaultVariableTypeItem(modifiers(annotations, nullability), param)
    }

    /**
     * Parse the [type] as a class. This function will always return a non-null [ClassTypeItem], so
     * it should only be used when it is certain that [type] is not a different kind of type.
     *
     * The context [typeParameterScope] are used to parse the parameters of the class type.
     *
     * [type] should have annotations and nullability markers stripped.
     */
    private fun asClass(
        type: String,
        typeParameterScope: TypeParameterScope,
        annotations: List<AnnotationItem>,
        nullability: TypeNullability?
    ): ClassTypeItem {
        return createClassType(type, null, typeParameterScope, annotations, nullability)
    }

    /**
     * Creates a class name for the class represented by [type] with optional [outerClassType].
     *
     * For instance, `test.pkg.Outer<P1>` would be the [outerClassType] when parsing `Inner<P2>`
     * from the [original] type `test.pkg.Outer<P1>.Inner<P2>`.
     */
    private fun createClassType(
        type: String,
        outerClassType: ClassTypeItem?,
        typeParameterScope: TypeParameterScope,
        annotations: List<AnnotationItem>,
        nullability: TypeNullability?
    ): ClassTypeItem {
        val (name, afterName, classAnnotations) = splitClassType(type)

        val qualifiedName =
            if (outerClassType != null) {
                // This is an inner type, add the prefix of the outer name
                "${outerClassType.qualifiedName}.$name"
            } else if (!name.contains('.')) {
                // Reverse the effect of [TypeItem.stripJavaLangPrefix].
                "java.lang.$name"
            } else {
                name
            }

        val (argumentStrings, remainder) = typeParameterStringsWithRemainder(afterName)
        val arguments =
            argumentStrings.map { cachedParseType(it, typeParameterScope) as TypeArgumentTypeItem }
        // If this is an outer class type (there's a remainder), call it non-null and don't apply
        // the leading annotations (they belong to the inner class type).
        val classModifiers =
            if (remainder != null) {
                modifiers(classAnnotations, TypeNullability.NONNULL)
            } else {
                modifiers(classAnnotations + annotations, nullability)
            }
        val classType =
            DefaultClassTypeItem(codebase, classModifiers, qualifiedName, arguments, outerClassType)

        if (remainder != null) {
            if (!remainder.startsWith('.')) {
                throw ApiParseException(
                    "Could not parse type `$type`. Found unexpected string after type parameters: $remainder"
                )
            }
            // This is an inner class type, recur with the new outer class
            return createClassType(
                remainder.substring(1),
                classType,
                typeParameterScope,
                annotations,
                nullability
            )
        }

        return classType
    }

    private fun modifiers(
        annotations: List<AnnotationItem>,
        nullability: TypeNullability?
    ): TypeModifiers {
        return DefaultTypeModifiers.create(
            annotations,
            nullability,
            "Type modifiers created by the text model are immutable because they are cached",
        )
    }

    /**
     * Removes all annotations at the beginning of the type, returning the trimmed type and list of
     * annotations.
     */
    fun trimLeadingAnnotations(type: String): Pair<String, List<AnnotationItem>> {
        val annotations = mutableListOf<AnnotationItem>()
        var trimmed = type.trim()
        while (trimmed.startsWith('@')) {
            val end = findAnnotationEnd(trimmed, 1)
            val annotationSource = trimmed.substring(0, end).trim()
            annotations.add(DefaultAnnotationItem.create(codebase, annotationSource))
            trimmed = trimmed.substring(end).trim()
        }
        return Pair(trimmed, annotations)
    }

    /**
     * Removes all annotations at the end of the [type], returning the trimmed type and list of
     * annotations. This is for use with arrays where annotations applying to the array type go
     * after the component type, for instance `String @A []`. The input [type] should **not**
     * include the array suffix (`[]` or `...`).
     */
    fun trimTrailingAnnotations(type: String): Pair<String, List<AnnotationItem>> {
        // The simple way to implement this would be to work from the end of the string, finding
        // `@` and removing annotations from the end. However, it is possible for an annotation
        // string to contain an `@`, so this is not a safe way to remove the annotations.
        // Instead, this finds all annotations starting from the beginning of the string, then
        // works backwards to find which ones are the trailing annotations.
        val allAnnotationIndices = mutableListOf<Pair<Int, Int>>()
        var trimmed = type.trim()

        // First find all annotations, saving the first and last index.
        var currIndex = 0
        while (currIndex < trimmed.length) {
            if (trimmed[currIndex] == '@') {
                val endIndex = findAnnotationEnd(trimmed, currIndex + 1)
                allAnnotationIndices.add(Pair(currIndex, endIndex))
                currIndex = endIndex + 1
            } else {
                currIndex++
            }
        }

        val annotations = mutableListOf<AnnotationItem>()
        // Go through all annotations from the back, seeing if they're at the end of the string.
        for ((start, end) in allAnnotationIndices.reversed()) {
            // This annotation isn't at the end, so we've hit the last trailing annotation
            if (end < trimmed.length) {
                break
            }
            val annotationSource = trimmed.substring(start)
            annotations.add(DefaultAnnotationItem.create(codebase, annotationSource))
            // Cut this annotation off, so now the next one can end at the last index.
            trimmed = trimmed.substring(0, start).trim()
        }
        return Pair(trimmed, annotations.reversed())
    }

    /**
     * Given [type] which represents a class, splits the string into the qualified name of the
     * class, the remainder of the type string, and a list of type-use annotations. The remainder of
     * the type string might be the type parameter list, inner class names, or a combination
     *
     * For `java.util.@A @B List<java.lang.@C String>`, returns the triple ("java.util.List",
     * "<java.lang.@C String", listOf("@A", "@B")).
     *
     * For `test.pkg.Outer.Inner`, returns the triple ("test.pkg.Outer", ".Inner", emptyList()).
     *
     * For `test.pkg.@test.pkg.A Outer<P1>.@test.pkg.B Inner<P2>`, returns the triple
     * ("test.pkg.Outer", "<P1>.@test.pkg.B Inner<P2>", listOf("@test.pkg.A")).
     */
    fun splitClassType(type: String): Triple<String, String?, List<AnnotationItem>> {
        // The constructed qualified type name
        var name = ""
        // The part of the type which still needs to be parsed
        var remaining = type.trim()
        // The annotations of the type, may be set later
        var annotations = emptyList<AnnotationItem>()

        var dotIndex = remaining.indexOf('.')
        var paramIndex = remaining.indexOf('<')
        var annotationIndex = remaining.indexOf('@')

        // Find which of '.', '<', or '@' comes first, if any
        var minIndex = minIndex(dotIndex, paramIndex, annotationIndex)
        while (minIndex != null) {
            when (minIndex) {
                // '.' is first, the next part is part of the qualified class name.
                dotIndex -> {
                    val nextNameChunk = remaining.substring(0, dotIndex)
                    name += nextNameChunk
                    remaining = remaining.substring(dotIndex)
                    // Assumes that package names are all lower case and class names will have
                    // an upper class character (the [START_WITH_UPPER] API lint check should
                    // make this a safe assumption). If the name is a class name, we've found
                    // the complete class name, return.
                    if (nextNameChunk.any { it.isUpperCase() }) {
                        return Triple(name, remaining, annotations)
                    }
                }
                // '<' is first, the end of the class name has been reached.
                paramIndex -> {
                    name += remaining.substring(0, paramIndex)
                    remaining = remaining.substring(paramIndex)
                    return Triple(name, remaining, annotations)
                }
                // '@' is first, trim all annotations.
                annotationIndex -> {
                    name += remaining.substring(0, annotationIndex)
                    trimLeadingAnnotations(remaining.substring(annotationIndex)).let {
                        (first, second) ->
                        remaining = first
                        annotations = second
                    }
                }
            }
            // Reset indices -- the string may now start with '.' for the next chunk of the name
            // but this should find the end of the next chunk.
            dotIndex = remaining.indexOf('.', 1)
            paramIndex = remaining.indexOf('<')
            annotationIndex = remaining.indexOf('@')
            minIndex = minIndex(dotIndex, paramIndex, annotationIndex)
        }
        // End of the name reached with no leftover string.
        name += remaining
        return Triple(name, null, annotations)
    }

    companion object {
        /**
         * Splits the Kotlin-style nullability marker off the type string, returning a pair of the
         * cleaned type string and the nullability suffix.
         */
        fun splitNullabilitySuffix(
            type: String,
            kotlinStyleNulls: Boolean
        ): Pair<String, TypeNullability?> {
            return if (kotlinStyleNulls) {
                // Don't interpret the wildcard type `?` as a nullability marker.
                if (type == "?") {
                    Pair(type, TypeNullability.UNDEFINED)
                } else if (type.endsWith("?")) {
                    Pair(type.dropLast(1), TypeNullability.NULLABLE)
                } else if (type.endsWith("!")) {
                    Pair(type.dropLast(1), TypeNullability.PLATFORM)
                } else {
                    Pair(type, TypeNullability.NONNULL)
                }
            } else if (type.length > 1 && type.endsWith("?") || type.endsWith("!")) {
                throw ApiParseException(
                    "Format does not support Kotlin-style null type syntax: $type"
                )
            } else {
                Pair(type, null)
            }
        }

        /**
         * Returns the minimum valid list index from the input, or null if there isn't one. -1 is
         * not a valid index.
         */
        private fun minIndex(vararg index: Int): Int? = index.filter { it != -1 }.minOrNull()

        /**
         * Given a string and the index in that string which is the start of an annotation (the
         * character _after_ the `@`), returns the index of the end of the annotation.
         */
        fun findAnnotationEnd(type: String, start: Int): Int {
            var index = start
            val length = type.length
            var balance = 0
            while (index < length) {
                val c = type[index]
                if (c == '(') {
                    balance++
                } else if (c == ')') {
                    balance--
                    if (balance == 0) {
                        return index + 1
                    }
                } else if (c != '.' && !Character.isJavaIdentifierPart(c) && balance == 0) {
                    break
                }
                index++
            }
            return index
        }

        /**
         * Breaks a string representing type parameters into a list of the type parameter strings.
         *
         * E.g. `"<A, B, C>"` -> `["A", "B", "C"]` and `"<List<A>, B>"` -> `["List<A>", "B"]`.
         */
        fun typeParameterStrings(typeString: String?): List<String> {
            return typeParameterStringsWithRemainder(typeString).first
        }

        /**
         * Breaks a string representing type parameters into a list of the type parameter strings,
         * and also returns the remainder of the string after the closing ">".
         *
         * E.g. `"<A, B, C>.Inner"` -> `Pair(["A", "B", "C"], ".Inner")`
         */
        fun typeParameterStringsWithRemainder(typeString: String?): Pair<List<String>, String?> {
            val s = typeString ?: return Pair(emptyList(), null)
            if (!s.startsWith("<")) return Pair(emptyList(), s)
            val list = mutableListOf<String>()
            var balance = 0
            var expect = false
            var start = 0
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '<') {
                    balance++
                    expect = balance == 1
                } else if (c == '>') {
                    balance--
                    if (balance == 0) {
                        add(list, s, start, i)
                        return if (i == s.length - 1) {
                            Pair(list, null)
                        } else {
                            Pair(list, s.substring(i + 1))
                        }
                    }
                } else if (c == ',') {
                    expect =
                        if (balance == 1) {
                            add(list, s, start, i)
                            true
                        } else {
                            false
                        }
                } else {
                    // This is the start of a parameter
                    if (expect && balance == 1) {
                        start = i
                        expect = false
                    }

                    if (c == '@') {
                        // Skip the entire text of the annotation
                        i = findAnnotationEnd(typeString, i + 1)
                        continue
                    }
                }
                i++
            }
            return Pair(list, null)
        }

        /**
         * Adds the substring of [s] from [from] to [to] to the [list], trimming whitespace from the
         * front.
         */
        private fun add(list: MutableList<String>, s: String, from: Int, to: Int) {
            for (i in from until to) {
                if (!Character.isWhitespace(s[i])) {
                    list.add(s.substring(i, to))
                    return
                }
            }
        }
    }

    /**
     * The cache entry, that contains the [TypeItem] that has been produced from the [type] and
     * [forceClassToBeNonNull] properties.
     */
    internal inner class CacheEntry(
        /** The string type from which the [TypeItem] will be parsed. */
        private val type: String,

        /**
         * Indicates whether an outermost [ClassTypeItem] is forced to be [TypeNullability.NONNULL].
         *
         * It is passed into [parseType] and if `true` it will cause the top level class type to be
         * treated as if it was being parsed when [kotlinStyleNulls] is `true` as that sets
         * [TypeNullability.NONNULL] by default.
         */
        private val forceClassToBeNonNull: Boolean,
    ) {
        /**
         * Map from [TypeParameterScope] to the [TypeItem] created for it.
         *
         * The [TypeParameterScope] that will be used to cache a type depends on the unqualified
         * names used in the type. It will use the closest enclosing scope of the one supplied that
         * adds at least one type parameter whose name is used in the type.
         *
         * See [TypeParameterScope.findSignificantScope].
         */
        private val scopeToItem = mutableMapOf<TypeParameterScope, TypeItem>()

        /**
         * The set of unqualified names used by [type].
         *
         * This is determined solely by the contents of the [type] string and so will be the same
         * for all [TypeItem]s cached in this entry.
         *
         * If this has not been set then no type items have been cached in this entry. It is set the
         * first time that a [TypeItem] is cached.
         */
        private lateinit var unqualifiedNamesInType: Set<String>

        /** Get the [TypeItem] for this type depending on the setting of [forceClassToBeNonNull]. */
        fun getTypeItem(typeParameterScope: TypeParameterScope): TypeItem {
            // If this is not the first time through then check to see if anything suitable has been
            // cached.
            val scopeForCachingOrNull =
                if (::unqualifiedNamesInType.isInitialized) {
                    // Find the scope to use for caching this type and then check to see if a
                    // [TypeItem]
                    // has been cached for that scope and if so return it. Otherwise, drop out.
                    typeParameterScope.findSignificantScope(unqualifiedNamesInType).also {
                        scopeForCaching ->
                        scopeToItem[scopeForCaching]?.let {
                            cacheHit++
                            return it
                        }
                    }
                } else {
                    // This is the first time through, so [unqualifiedNamesInType] is not available
                    // so drop through and initialize later.
                    null
                }

            // Parse the [type] to produce a [TypeItem].
            val typeItem = createTypeItem(typeParameterScope)
            cacheSize++

            // Find the scope for caching if it was not found above.
            val scopeForCaching =
                scopeForCachingOrNull
                    ?: let {
                        // This will only happen if [unqualifiedNamesInType] is uninitialized so
                        // make sure to initialize it.
                        unqualifiedNamesInType = unqualifiedNameGatherer.gatherFrom(typeItem)

                        // Find the scope for caching. It could not be found before because
                        // [unqualifiedNamesInType] was not initialized.
                        typeParameterScope.findSignificantScope(unqualifiedNamesInType)
                    }

            // Store the type item in the scope selected for caching.
            scopeToItem[scopeForCaching] = typeItem

            // Return it.
            return typeItem
        }

        /**
         * Create a new [TypeItem] for [type] with the given [forceClassToBeNonNull] setting and for
         * the requested [typeParameterScope].
         */
        private fun createTypeItem(typeParameterScope: TypeParameterScope): TypeItem {
            return parseType(type, typeParameterScope, emptyList(), forceClassToBeNonNull)
        }
    }

    /**
     * A [TypeVisitor] that will extract all unqualified names from the type.
     *
     * These are the names that could be used as a type parameter name and so whose meaning could
     * change depending on the [TypeParameterScope], i.e. the set of type parameters currently in
     * scope.
     */
    private class UnqualifiedNameGatherer : BaseTypeVisitor() {

        private val unqualifiedNames = mutableSetOf<String>()

        override fun visit(primitiveType: PrimitiveTypeItem) {
            // Primitive type names are added because Kotlin allows them to be shadowed by a type
            // parameter.
            unqualifiedNames.add(primitiveType.kind.primitiveName)
        }

        override fun visitClassType(classType: ClassTypeItem) {
            // Classes in java.lang package can be represented in the type without the leading
            // package, all other types must be fully qualified. At this point it is not clear
            // whether the type used in the input type string was qualified or not as the package
            // has been prepended so this assumes that they all are just to be on the safe side.
            // It is only for legacy reasons that all `java.lang` package prefixes are stripped
            // when generating the API signature files. See b/324047248.
            val name = classType.qualifiedName
            if (!name.contains('.')) {
                unqualifiedNames.add(name)
            } else {
                val trimmed = TypeItem.stripJavaLangPrefix(name)
                if (trimmed != name) {
                    unqualifiedNames.add(trimmed)
                }
            }
        }

        override fun visitVariableType(variableType: VariableTypeItem) {
            unqualifiedNames.add(variableType.name)
        }

        /** Gather the names from [typeItem] returning an immutable set of the unqualified names. */
        fun gatherFrom(typeItem: TypeItem): Set<String> {
            unqualifiedNames.clear()
            typeItem.accept(this)
            return unqualifiedNames.toSet()
        }
    }

    /**
     * An instance of [UnqualifiedNameGatherer] used for gathering all the unqualified names from
     * all the [TypeItem]s cached by this.
     */
    private val unqualifiedNameGatherer = UnqualifiedNameGatherer()
}
