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

import com.android.tools.metalava.model.ANNOTATION_ATTR_VALUE
import com.android.tools.metalava.model.AnnotationArrayAttributeValue
import com.android.tools.metalava.model.AnnotationAttribute
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.AnnotationSingleAttributeValue
import com.android.tools.metalava.model.DefaultAnnotationAttribute
import java.util.TreeMap

interface AnnotationFilter {
    // tells whether an annotation is included by the filter
    fun matches(annotation: AnnotationItem): Boolean
    // tells whether an annotation is included by this filter
    fun matches(annotationSource: String): Boolean

    // Returns a sorted set of fully qualified annotation names that may be included by this filter.
    // Note that this filter might incorporate parameters but this function strips them.
    fun getIncludedAnnotationNames(): Set<String>
    // Returns true if [getIncludedAnnotationNames] includes the given qualified name
    fun matchesAnnotationName(qualifiedName: String): Boolean

    // Returns true if nothing is matched by this filter
    fun isEmpty(): Boolean
    // Returns true if some annotation is matched by this filter
    fun isNotEmpty(): Boolean

    companion object {
        private val empty = AnnotationFilterBuilder().build()

        fun emptyFilter(): AnnotationFilter = empty
    }
}

/** Builder for [AnnotationFilter]s. */
class AnnotationFilterBuilder {
    private val inclusionExpressions = mutableListOf<AnnotationFilterEntry>()

    // Adds the given option as a fully qualified annotation name to match with this filter
    // Can be "androidx.annotation.RestrictTo"
    // Can be "androidx.annotation.RestrictTo(androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP)"
    // Note that the order of calls to this method could affect the return from
    // {@link #firstQualifiedName} .
    fun add(option: String) {
        val (matchResult, pattern) =
            if (option.startsWith("!")) {
                Pair(false, option.substring(1))
            } else {
                Pair(true, option)
            }
        inclusionExpressions.add(AnnotationFilterEntry.fromOption(pattern, matchResult))
    }

    /** Build the [AnnotationFilter]. */
    fun build(): AnnotationFilter {
        // Sort the expressions by match result, so that those expressions that exclude come before
        // those which include.
        val map =
            inclusionExpressions
                .sortedBy { it.matchResult }
                .groupByTo(TreeMap()) { it.qualifiedName }

        // Verify that the filter is consistent.
        for ((fqn, patterns) in map.entries) {
            val (includes, excludes) = patterns.partition { it.matchResult }
            if (excludes.isNotEmpty()) {
                for (exclude in excludes) {
                    if (exclude.attributes.isEmpty()) {
                        throw IllegalStateException(
                            "Exclude pattern '$exclude' is invalid as it does not specify attributes"
                        )
                    }
                }

                if (includes.isEmpty()) {
                    throw IllegalStateException(
                        "Patterns for '$fqn' contains ${excludes.size} excludes but no includes"
                    )
                }
            }
        }
        return ImmutableAnnotationFilter(map)
    }
}

// Immutable implementation of AnnotationFilter
private class ImmutableAnnotationFilter(
    private val qualifiedNameToEntries: Map<String, List<AnnotationFilterEntry>>
) : AnnotationFilter {

    override fun matches(annotationSource: String): Boolean {
        val wrapper = AnnotationFilterEntry.fromSource(annotationSource)
        return matches(wrapper)
    }

    override fun matches(annotation: AnnotationItem): Boolean {
        val qualifiedName = annotation.qualifiedName
        // If the annotation name is not in the map of annotation names that can be matched then
        // this can never match so return immediately rather than generating the source
        // representation of the annotation.
        if (qualifiedName !in qualifiedNameToEntries) {
            return false
        }
        val wrapper = AnnotationFilterEntry.fromAnnotationItem(annotation)
        return matches(wrapper)
    }

    private fun matches(annotation: AnnotationFilterEntry): Boolean {
        val entries = qualifiedNameToEntries[annotation.qualifiedName] ?: return false
        return entries.firstOrNull { entry -> annotationsMatch(entry, annotation) }?.matchResult
            ?: false
    }

    override fun getIncludedAnnotationNames(): Set<String> = qualifiedNameToEntries.keys

    override fun matchesAnnotationName(qualifiedName: String): Boolean {
        return qualifiedNameToEntries.contains(qualifiedName)
    }

    override fun isEmpty(): Boolean {
        return qualifiedNameToEntries.isEmpty()
    }

    override fun isNotEmpty(): Boolean {
        return !isEmpty()
    }

    private fun annotationsMatch(
        filter: AnnotationFilterEntry,
        existingAnnotation: AnnotationFilterEntry
    ): Boolean {
        if (filter.attributes.count() > existingAnnotation.attributes.count()) {
            return false
        }
        for (attribute in filter.attributes) {
            val existingValue = existingAnnotation.findAttribute(attribute.name)?.value
            val existingValueSource = existingValue?.toSource()
            val attributeValueSource = attribute.value.toSource()
            if (attribute.name == "value") {
                // Special-case where varargs value annotation attribute can be specified with
                // either @Foo(BAR) or @Foo({BAR}) and they are equivalent.
                when {
                    attribute.value is AnnotationSingleAttributeValue &&
                        existingValue is AnnotationArrayAttributeValue -> {
                        if (existingValueSource != "{$attributeValueSource}") return false
                    }
                    attribute.value is AnnotationArrayAttributeValue &&
                        existingValue is AnnotationSingleAttributeValue -> {
                        if ("{$existingValueSource}" != attributeValueSource) return false
                    }
                    else -> {
                        if (existingValueSource != attributeValueSource) return false
                    }
                }
            } else {
                if (existingValueSource != attributeValueSource) {
                    return false
                }
            }
        }
        return true
    }
}

// An AnnotationFilterEntry filters for annotations having a certain qualifiedName and
// possibly certain attributes.
// An AnnotationFilterEntry doesn't necessarily have a Codebase like an AnnotationItem does
private class AnnotationFilterEntry(
    val qualifiedName: String,
    val attributes: List<AnnotationAttribute>,
    /** The result that will be returned from [AnnotationFilter.matches] when this entry matches. */
    val matchResult: Boolean,
) {
    fun findAttribute(name: String?): AnnotationAttribute? {
        val actualName = name ?: ANNOTATION_ATTR_VALUE
        return attributes.firstOrNull { it.name == actualName }
    }

    override fun toString(): String {
        return buildString {
            if (!matchResult) {
                append("!")
            }
            append(qualifiedName)
            if (attributes.isNotEmpty()) {
                append("(")
                attributes.joinTo(this)
                append(")")
            }
        }
    }

    companion object {
        fun fromSource(source: String): AnnotationFilterEntry {
            val text = source.replace("@", "")
            return fromOption(text)
        }

        fun fromOption(text: String, matchResult: Boolean = true): AnnotationFilterEntry {
            val index = text.indexOf("(")

            val qualifiedName =
                if (index == -1) {
                    text
                } else {
                    text.substring(0, index)
                }

            val attributes: List<AnnotationAttribute> =
                if (index == -1) {
                    emptyList()
                } else {
                    DefaultAnnotationAttribute.createList(
                        text.substring(index + 1, text.lastIndexOf(')'))
                    )
                }
            return AnnotationFilterEntry(qualifiedName, attributes, matchResult)
        }

        fun fromAnnotationItem(annotationItem: AnnotationItem): AnnotationFilterEntry {
            // Have to call toSource to resolve attribute values into fully qualified class names.
            // For example: resolving RestrictTo(LIBRARY_GROUP) into
            // RestrictTo(androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP)
            // In addition, toSource (with the default argument showDefaultAttrs=true) retrieves
            // default attributes from the definition of the annotation. For example,
            // @SystemApi actually is converted into @android.annotation.SystemApi(\
            // client=android.annotation.SystemApi.Client.PRIVILEGED_APPS,\
            // process=android.annotation.SystemApi.Process.ALL)
            return fromSource(annotationItem.toSource())
        }
    }
}
