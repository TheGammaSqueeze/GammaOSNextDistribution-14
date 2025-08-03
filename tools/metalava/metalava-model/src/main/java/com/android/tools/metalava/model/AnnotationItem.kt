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

import kotlin.reflect.KClass

fun isNullnessAnnotation(qualifiedName: String): Boolean =
    isNullableAnnotation(qualifiedName) || isNonNullAnnotation(qualifiedName)

fun isNullableAnnotation(qualifiedName: String): Boolean {
    return qualifiedName.endsWith("Nullable")
}

fun isNonNullAnnotation(qualifiedName: String): Boolean {
    return qualifiedName.endsWith("NonNull") ||
        qualifiedName.endsWith("NotNull") ||
        qualifiedName.endsWith("Nonnull")
}

fun isJvmSyntheticAnnotation(qualifiedName: String): Boolean {
    return qualifiedName == "kotlin.jvm.JvmSynthetic"
}

interface AnnotationItem {
    val codebase: Codebase

    /** Fully qualified name of the annotation */
    val qualifiedName: String?

    /**
     * Determines the effect that this will have on whether an item annotated with this annotation
     * will be shown as part of the API or not.
     */
    val showability: Showability

    /** Generates source code for this annotation (using fully qualified names) */
    fun toSource(
        target: AnnotationTarget = AnnotationTarget.SIGNATURE_FILE,
        showDefaultAttrs: Boolean = true
    ): String

    /** The applicable targets for this annotation */
    val targets: Set<AnnotationTarget>

    /** Attributes of the annotation; may be empty. */
    val attributes: List<AnnotationAttribute>

    /**
     * The [TypeNullability] associated with this or `null` if this is not a nullability annotation.
     */
    val typeNullability: TypeNullability?

    /** True if this annotation represents @Nullable or @NonNull (or some synonymous annotation) */
    fun isNullnessAnnotation(): Boolean

    /** True if this annotation represents @Nullable (or some synonymous annotation) */
    fun isNullable(): Boolean

    /** True if this annotation represents @NonNull (or some synonymous annotation) */
    fun isNonNull(): Boolean

    /** True if this annotation represents @Retention (either the Java or Kotlin version) */
    fun isRetention(): Boolean = isRetention(qualifiedName)

    /** True if this annotation represents @JvmSynthetic */
    fun isJvmSynthetic(): Boolean {
        return isJvmSyntheticAnnotation(qualifiedName ?: return false)
    }

    /** True if this annotation represents @IntDef, @LongDef or @StringDef */
    fun isTypeDefAnnotation(): Boolean {
        val name = qualifiedName ?: return false
        if (!(name.endsWith("Def"))) {
            return false
        }
        return (ANDROIDX_INT_DEF == name ||
            ANDROIDX_STRING_DEF == name ||
            ANDROIDX_LONG_DEF == name ||
            ANDROID_INT_DEF == name ||
            ANDROID_STRING_DEF == name ||
            ANDROID_LONG_DEF == name)
    }

    /**
     * True if this annotation represents a @ParameterName annotation (or some synonymous
     * annotation). The parameter name should be the default attribute or "value".
     */
    fun isParameterName(): Boolean {
        return qualifiedName?.endsWith(".ParameterName") ?: return false
    }

    /**
     * True if this annotation represents a @DefaultValue annotation (or some synonymous
     * annotation). The default value should be the default attribute or "value".
     */
    fun isDefaultValue(): Boolean {
        return qualifiedName?.endsWith(".DefaultValue") ?: return false
    }

    /** Returns the given named attribute if specified */
    fun findAttribute(name: String?): AnnotationAttribute? {
        val actualName = name ?: ANNOTATION_ATTR_VALUE
        return attributes.firstOrNull { it.name == actualName }
    }

    /** Find the class declaration for the given annotation */
    fun resolve(): ClassItem?

    /** If this annotation has a typedef annotation associated with it, return it */
    fun findTypedefAnnotation(): AnnotationItem?

    /**
     * Returns true iff the annotation is a show annotation.
     *
     * If `true` then an item annotated with this annotation (and any contents) will be added to the
     * API.
     *
     * e.g. if a class is annotated with this then it will also apply (unless overridden by a closer
     * annotation) to all its contents like nested classes, methods, fields, constructors,
     * properties, etc.
     */
    fun isShowAnnotation(): Boolean

    /**
     * Returns true iff this annotation is a show for stubs purposes annotation.
     *
     * If `true` then an item annotated with this annotation (and any contents) which are not
     * annotated with another [isShowAnnotation] will be added to the stubs but not the API.
     *
     * e.g. if a class is annotated with this then it will also apply (unless overridden by a closer
     * annotation) to all its contents like nested classes, methods, fields, constructors,
     * properties, etc.
     */
    fun isShowForStubPurposes(): Boolean

    /**
     * Returns true iff this annotation is a hide annotation.
     *
     * Hide annotations can either be explicitly specified when creating the [Codebase] or they can
     * be any annotation that is annotated with a hide meta-annotation (see [isHideMetaAnnotation]).
     *
     * If `true` then an item annotated with this annotation (and any contents) will be excluded
     * from the API.
     *
     * e.g. if a class is annotated with this then it will also apply (unless overridden by a closer
     * annotation) to all its contents like nested classes, methods, fields, constructors,
     * properties, etc.
     */
    fun isHideAnnotation(): Boolean

    fun isSuppressCompatibilityAnnotation(): Boolean

    /**
     * Returns true iff this annotation is a showability annotation, i.e. one that will affect
     * [showability].
     */
    fun isShowabilityAnnotation(): Boolean

    /** Returns the retention of this annotation */
    val retention: AnnotationRetention
        get() {
            val cls = resolve()
            if (cls != null) {
                if (cls.isAnnotationType()) {
                    return cls.getRetention()
                }
            }

            return AnnotationRetention.getDefault()
        }

    companion object {
        /**
         * The simple name of an annotation, which is the annotation name (not qualified name)
         * prefixed by @
         */
        fun simpleName(item: AnnotationItem): String {
            return item.qualifiedName?.let { "@${it.substringAfterLast('.')}" }.orEmpty()
        }

        /**
         * Given a "full" annotation name, shortens it by removing redundant package names. This is
         * intended to be used to reduce clutter in signature files.
         *
         * For example, this method will convert `@androidx.annotation.Nullable` to just
         * `@Nullable`, and `@androidx.annotation.IntRange(from=20)` to `IntRange(from=20)`.
         */
        fun shortenAnnotation(source: String): String {
            return when {
                source == "@java.lang.Deprecated" -> "@Deprecated"
                source.startsWith(ANDROID_ANNOTATION_PREFIX, 1) -> {
                    "@" + source.substring(ANDROID_ANNOTATION_PREFIX.length + 1)
                }
                source.startsWith(ANDROIDX_ANNOTATION_PREFIX, 1) -> {
                    "@" + source.substring(ANDROIDX_ANNOTATION_PREFIX.length + 1)
                }
                else -> source
            }
        }

        /**
         * Reverses the [shortenAnnotation] method. Intended for use when reading in signature files
         * that contain shortened type references.
         */
        fun unshortenAnnotation(source: String): String {
            return when {
                source == "@Deprecated" -> "@java.lang.Deprecated"
                // The first 4 annotations are in the android.annotation. package, not
                // androidx.annotation
                // Nullability annotations are written as @NonNull and @Nullable in API text files,
                // and these should be linked no android.annotation package when generating stubs.
                source.startsWith("@SystemService") ||
                    source.startsWith("@TargetApi") ||
                    source.startsWith("@SuppressLint") ||
                    source.startsWith("@FlaggedApi") ||
                    source.startsWith("@Nullable") ||
                    source.startsWith("@NonNull") -> "@android.annotation." + source.substring(1)
                // If the first character of the name (after "@") is lower-case, then
                // assume it's a package name, so no need to shorten it.
                source.startsWith("@") && source[1].isLowerCase() -> source
                else -> {
                    "@androidx.annotation." + source.substring(1)
                }
            }
        }
    }
}

/** Get the [TypeNullability] from a list of [AnnotationItem]s. */
val List<AnnotationItem>.typeNullability
    get() = mapNotNull { it.typeNullability }.singleOrNull()

/**
 * Get the value of the named attribute as an object of the specified type or null if the attribute
 * could not be found.
 *
 * This can only be called for attributes which have a single value, it will throw an exception if
 * called for an attribute whose value is any array type. See [getAttributeValues] instead.
 *
 * This supports the following types for [T]:
 * * [String] - the attribute must be of type [String] or [Class].
 * * [AnnotationItem] - the attribute must be of an annotation type.
 * * [Boolean] - the attribute must be of type [Boolean].
 * * [Byte] - the attribute must be of type [Byte].
 * * [Char] - the attribute must be of type [Char].
 * * [Double] - the attribute must be of type [Double].
 * * [Float] - the attribute must be of type [Float].
 * * [Int] - the attribute must be of type [Int].
 * * [Long] - the attribute must be of type [Long].
 * * [Short] - the attribute must be of type [Short].
 *
 * Any other types will result in a [ClassCastException].
 */
inline fun <reified T : Any> AnnotationItem.getAttributeValue(name: String): T? {
    val value = nonInlineGetAttributeValue(T::class, name) ?: return null
    return value as T
}

/**
 * Non-inline portion of functionality needed by [getAttributeValue]; separated to reduce the cost
 * of inlining [getAttributeValue].
 */
@PublishedApi
internal fun AnnotationItem.nonInlineGetAttributeValue(kClass: KClass<*>, name: String): Any? {
    val attributeValue = findAttribute(name)?.value ?: return null
    val value =
        when (attributeValue) {
            is AnnotationArrayAttributeValue ->
                throw IllegalStateException("Annotation attribute is of type array")
            else -> attributeValue.value()
        }
            ?: return null

    return convertValue(codebase, kClass, value)
}

/**
 * Get the values of the named attribute as a list of objects of the specified type or null if the
 * attribute could not be found.
 *
 * This can be used to get the value of an attribute that is either one of the types in
 * [getAttributeValue] (in which case this returns a list containing a single item), or an array of
 * one of the types in [getAttributeValue] (in which case this returns a list containing all the
 * items in the array).
 */
inline fun <reified T : Any> AnnotationItem.getAttributeValues(name: String): List<T>? {
    return nonInlineGetAttributeValues(T::class, name) { it as T }
}

/**
 * Non-inline portion of functionality needed by [getAttributeValues]; separated to reduce the cost
 * of inlining [getAttributeValues].
 */
@PublishedApi
internal fun <T : Any> AnnotationItem.nonInlineGetAttributeValues(
    kClass: KClass<*>,
    name: String,
    caster: (Any) -> T
): List<T>? {
    val attributeValue = findAttribute(name)?.value ?: return null
    val values =
        when (attributeValue) {
            is AnnotationArrayAttributeValue -> attributeValue.values.mapNotNull { it.value() }
            else -> listOfNotNull(attributeValue.value())
        }

    return values.map { caster(convertValue(codebase, kClass, it)) }
}

/**
 * Perform some conversions to try and make [value] to be an instance of [kClass].
 *
 * This fixes up some known issues with [value] not corresponding to the expected type but otherwise
 * simply returns the value it is given. It is the caller's responsibility to actually cast the
 * returned value to the correct type.
 */
private fun convertValue(codebase: Codebase, kClass: KClass<*>, value: Any): Any {
    // The value stored for number types is not always the same as the type of the annotation
    // attributes. This is for a number of reasons, e.g.
    // * In a .class file annotation values are stored in the constant pool and some number types do
    //   not have their own constant form (or their own array constant form) so are stored as
    //   instances of a wider type. They need to be converted to the correct type.
    // * In signature files annotation values are not always stored as the narrowest type, may not
    //   have a suffix and type information may not always be available when parsing.
    if (Number::class.java.isAssignableFrom(kClass.java)) {
        value as Number
        return when (kClass) {
            // Byte does have its own constant form but when stored in an array it is stored as an
            // int.
            Byte::class -> value.toByte()
            // DefaultAnnotationValue.create() always reads integers as longs.
            Int::class -> value.toInt()
            // DefaultAnnotationValue.create() always reads floating point as doubles.
            Float::class -> value.toFloat()
            // Short does not have its own constant form.
            Short::class -> value.toShort()
            else -> value
        }
    }

    // TODO: Push down into the model as that is likely to be more efficient.
    if (kClass == AnnotationItem::class) {
        return DefaultAnnotationItem.create(codebase, value as String)
    }

    return value
}

/** Default implementation of an annotation item */
open class DefaultAnnotationItem
/** The primary constructor is private to force sub-classes to use the secondary constructor. */
private constructor(
    override val codebase: Codebase,

    /** Fully qualified name of the annotation (prior to name mapping) */
    protected val originalName: String?,

    /** Fully qualified name of the annotation (after name mapping) */
    final override val qualifiedName: String?,

    /** Possibly empty list of attributes. */
    attributesGetter: () -> List<AnnotationAttribute>,
) : AnnotationItem {

    /**
     * This constructor is needed to initialize [qualifiedName] using the [codebase] parameter
     * instead of the [DefaultAnnotationItem.codebase] property which is overridden by subclasses
     * and will not be initialized at the time it is used.
     */
    constructor(
        codebase: Codebase,
        originalName: String?,
        attributesGetter: () -> List<AnnotationAttribute>,
    ) : this(
        codebase,
        originalName,
        qualifiedName = codebase.annotationManager.normalizeInputName(originalName),
        attributesGetter,
    )

    override val targets: Set<AnnotationTarget> by lazy {
        codebase.annotationManager.computeTargets(this, codebase::findClass)
    }

    final override val attributes: List<AnnotationAttribute> by lazy(attributesGetter)

    /** Information that metalava has gathered about this annotation item. */
    val info: AnnotationInfo by lazy { codebase.annotationManager.getAnnotationInfo(this) }

    override val typeNullability: TypeNullability?
        get() = info.typeNullability

    override fun isNullnessAnnotation(): Boolean {
        return info.typeNullability != null
    }

    override fun isNullable(): Boolean {
        return info.typeNullability == TypeNullability.NULLABLE
    }

    override fun isNonNull(): Boolean {
        return info.typeNullability == TypeNullability.NONNULL
    }

    override val showability: Showability
        get() = info.showability

    override fun resolve(): ClassItem? {
        return codebase.findClass(originalName ?: return null)
    }

    /** If this annotation has a typedef annotation associated with it, return it */
    override fun findTypedefAnnotation(): AnnotationItem? {
        val className = originalName ?: return null
        return codebase
            .findClass(className)
            ?.modifiers
            ?.findAnnotation(AnnotationItem::isTypeDefAnnotation)
    }

    override fun isShowAnnotation(): Boolean = info.showability.show()

    override fun isShowForStubPurposes(): Boolean = info.showability.showForStubsOnly()

    override fun isHideAnnotation(): Boolean = info.showability.hide()

    override fun isSuppressCompatibilityAnnotation(): Boolean = info.suppressCompatibility

    override fun isShowabilityAnnotation(): Boolean = info.showability != Showability.NO_EFFECT

    override fun equals(other: Any?): Boolean {
        if (other !is AnnotationItem) return false
        return qualifiedName == other.qualifiedName && attributes == other.attributes
    }

    override fun hashCode(): Int {
        var result = qualifiedName?.hashCode() ?: 0
        result = 31 * result + attributes.hashCode()
        return result
    }

    override fun toSource(target: AnnotationTarget, showDefaultAttrs: Boolean): String {
        val qualifiedName =
            codebase.annotationManager.normalizeOutputName(qualifiedName, target) ?: return ""

        return formatAnnotationItem(qualifiedName, attributes)
    }

    final override fun toString() = toSource()

    companion object {
        fun formatAnnotationItem(
            qualifiedName: String,
            attributes: List<AnnotationAttribute>,
        ): String {
            return buildString {
                append("@")
                append(qualifiedName)
                if (attributes.isNotEmpty()) {
                    val suppressDefaultAnnotationAttribute = attributes.size == 1
                    append("(")
                    attributes.forEachIndexed { i, attribute ->
                        if (i != 0) {
                            append(", ")
                        }
                        if (
                            !suppressDefaultAnnotationAttribute ||
                                attribute.name != ANNOTATION_ATTR_VALUE
                        ) {
                            append(attribute.name)
                            append("=")
                        }
                        append(attribute.value)
                    }
                    append(")")
                }
            }
        }

        fun create(codebase: Codebase, source: String): AnnotationItem {
            val index = source.indexOf("(")
            val originalName =
                if (index == -1) source.substring(1) // Strip @
                else source.substring(1, index)

            fun attributes(): List<AnnotationAttribute> =
                if (index == -1) {
                    emptyList()
                } else {
                    DefaultAnnotationAttribute.createList(
                        source.substring(index + 1, source.lastIndexOf(')'))
                    )
                }

            return DefaultAnnotationItem(codebase, originalName, ::attributes)
        }
    }
}

/** The default annotation attribute name when no name is provided. */
const val ANNOTATION_ATTR_VALUE = "value"

/** An attribute of an annotation, such as "value" */
interface AnnotationAttribute {
    /** The name of the annotation */
    val name: String
    /** The annotation value */
    val value: AnnotationAttributeValue

    /**
     * Return all leaf values; this flattens the complication of handling
     * {@code @SuppressLint("warning")} and {@code @SuppressLint({"warning1","warning2"})
     */
    fun leafValues(): List<AnnotationAttributeValue> {
        val result = mutableListOf<AnnotationAttributeValue>()
        AnnotationAttributeValue.addValues(value, result)
        return result
    }
}

const val ANNOTATION_VALUE_FALSE = "false"
const val ANNOTATION_VALUE_TRUE = "true"

/** An annotation value */
interface AnnotationAttributeValue {
    /** Generates source code for this annotation value */
    fun toSource(): String

    /** The value of the annotation */
    fun value(): Any?

    /**
     * If the annotation declaration references a field (or class etc.), return the resolved class
     */
    fun resolve(): Item?

    companion object {
        fun addValues(
            value: AnnotationAttributeValue,
            into: MutableList<AnnotationAttributeValue>
        ) {
            if (value is AnnotationArrayAttributeValue) {
                for (v in value.values) {
                    addValues(v, into)
                }
            } else if (value is AnnotationSingleAttributeValue) {
                into.add(value)
            }
        }
    }
}

/** An annotation value (for a single item, not an array) */
interface AnnotationSingleAttributeValue : AnnotationAttributeValue {
    val value: Any?

    override fun value() = value
}

/** An annotation value for an array of items */
interface AnnotationArrayAttributeValue : AnnotationAttributeValue {
    /** The annotation values */
    val values: List<AnnotationAttributeValue>

    override fun resolve(): Item? {
        error("resolve() should not be called on an array value")
    }

    override fun value() = values.mapNotNull { it.value() }.toTypedArray()
}

class DefaultAnnotationAttribute(
    override val name: String,
    override val value: AnnotationAttributeValue
) : AnnotationAttribute {
    companion object {
        fun create(name: String, value: String): DefaultAnnotationAttribute {
            return DefaultAnnotationAttribute(name, DefaultAnnotationValue.create(value))
        }

        fun createList(source: String): List<AnnotationAttribute> {
            val list = mutableListOf<AnnotationAttribute>() // TODO: default size = 2
            var begin = 0
            var index = 0
            val length = source.length
            while (index < length) {
                val c = source[index]
                if (c == '{') {
                    index = findEnd(source, index + 1, length, '}')
                } else if (c == '"') {
                    index = findEnd(source, index + 1, length, '"')
                } else if (c == ',') {
                    addAttribute(list, source, begin, index)
                    index++
                    begin = index
                    continue
                } else if (c == ' ' && index == begin) {
                    begin++
                }

                index++
            }

            if (begin < length) {
                addAttribute(list, source, begin, length)
            }

            return list
        }

        private fun findEnd(source: String, from: Int, to: Int, sentinel: Char): Int {
            var i = from
            while (i < to) {
                val c = source[i]
                if (c == '\\') {
                    i++
                } else if (c == sentinel) {
                    return i
                }
                i++
            }
            return to
        }

        private fun addAttribute(
            list: MutableList<AnnotationAttribute>,
            source: String,
            from: Int,
            to: Int
        ) {
            var split = source.indexOf('=', from)
            if (split >= to) {
                split = -1
            }
            val name: String
            val value: String
            val valueBegin: Int
            val valueEnd: Int
            if (split == -1) {
                valueBegin = 0
                valueEnd = to
                name = "value"
            } else {
                name = source.substring(from, split).trim()
                valueBegin = split + 1
                valueEnd = to
            }
            value = source.substring(valueBegin, valueEnd).trim()
            list.add(create(name, value))
        }
    }

    override fun toString(): String {
        return "$name=$value"
    }

    override fun equals(other: Any?): Boolean {
        if (other !is AnnotationAttribute) return false
        return name == other.name && value == other.value
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + value.hashCode()
        return result
    }
}

abstract class DefaultAnnotationValue(sourceGetter: () -> String) : AnnotationAttributeValue {
    companion object {
        fun create(valueSource: String): DefaultAnnotationValue {
            return if (valueSource.startsWith("{")) { // Array
                DefaultAnnotationArrayAttributeValue(
                    { valueSource },
                    {
                        assert(valueSource.startsWith("{") && valueSource.endsWith("}")) {
                            valueSource
                        }
                        valueSource
                            .substring(1, valueSource.length - 1)
                            .split(",")
                            .map { create(it.trim()) }
                            .toList()
                    },
                )
            } else {
                DefaultAnnotationSingleAttributeValue(
                    { valueSource },
                    {
                        when {
                            valueSource == ANNOTATION_VALUE_TRUE -> true
                            valueSource == ANNOTATION_VALUE_FALSE -> false
                            valueSource.startsWith("\"") -> valueSource.removeSurrounding("\"")
                            valueSource.startsWith('\'') -> valueSource.removeSurrounding("'")[0]
                            else ->
                                try {
                                    if (valueSource.contains(".")) {
                                        valueSource.toDouble()
                                    } else {
                                        valueSource.toLong()
                                    }
                                } catch (e: NumberFormatException) {
                                    valueSource
                                }
                        }
                    },
                )
            }
        }
    }

    /** The annotation value, expressed as source code */
    private val valueSource: String by lazy(LazyThreadSafetyMode.NONE, sourceGetter)

    override fun toSource() = valueSource

    override fun toString(): String = toSource()
}

open class DefaultAnnotationSingleAttributeValue(
    sourceGetter: () -> String,
    valueGetter: () -> Any?
) : DefaultAnnotationValue(sourceGetter), AnnotationSingleAttributeValue {

    override val value by lazy(LazyThreadSafetyMode.NONE, valueGetter)

    override fun resolve(): Item? = null

    override fun equals(other: Any?): Boolean {
        if (other !is AnnotationSingleAttributeValue) return false
        return value == other.value
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }
}

class DefaultAnnotationArrayAttributeValue(
    sourceGetter: () -> String,
    valuesGetter: () -> List<AnnotationAttributeValue>
) : DefaultAnnotationValue(sourceGetter), AnnotationArrayAttributeValue {

    override val values by lazy(LazyThreadSafetyMode.NONE, valuesGetter)

    override fun equals(other: Any?): Boolean {
        if (other !is AnnotationArrayAttributeValue) return false
        return values == other.values
    }

    override fun hashCode(): Int {
        return values.hashCode()
    }
}
