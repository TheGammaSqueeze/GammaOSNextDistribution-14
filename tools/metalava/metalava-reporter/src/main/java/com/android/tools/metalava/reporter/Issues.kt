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
package com.android.tools.metalava.reporter

import java.util.Locale
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

object Issues {
    private val allIssues: MutableList<Issue> = ArrayList(300)

    /** A list of all the issues. */
    val all: List<Issue> by this::allIssues

    private val nameToIssue: MutableMap<String, Issue> = HashMap(300)

    val PARSE_ERROR by Issue(Severity.ERROR)
    val DUPLICATE_SOURCE_CLASS by Issue(Severity.WARNING)
    // Compatibility issues
    val ADDED_PACKAGE by Issue(Severity.HIDDEN, Category.COMPATIBILITY)
    val ADDED_CLASS by Issue(Severity.HIDDEN, Category.COMPATIBILITY)
    val ADDED_METHOD by Issue(Severity.HIDDEN, Category.COMPATIBILITY)
    val ADDED_FIELD by Issue(Severity.HIDDEN, Category.COMPATIBILITY)
    val ADDED_INTERFACE by Issue(Severity.HIDDEN, Category.COMPATIBILITY)
    val REMOVED_PACKAGE by Issue(Severity.ERROR, Category.COMPATIBILITY)
    val REMOVED_CLASS by Issue(Severity.ERROR, Category.COMPATIBILITY)
    val REMOVED_METHOD by Issue(Severity.ERROR, Category.COMPATIBILITY)
    val REMOVED_FIELD by Issue(Severity.ERROR, Category.COMPATIBILITY)
    val REMOVED_INTERFACE by Issue(Severity.ERROR, Category.COMPATIBILITY)
    val CHANGED_STATIC by Issue(Severity.ERROR, Category.COMPATIBILITY)
    val ADDED_FINAL by Issue(Severity.ERROR, Category.COMPATIBILITY)
    val CHANGED_TRANSIENT by Issue(Severity.ERROR, Category.COMPATIBILITY)
    val CHANGED_VOLATILE by Issue(Severity.ERROR, Category.COMPATIBILITY)
    val CHANGED_TYPE by Issue(Severity.ERROR, Category.COMPATIBILITY)
    val CHANGED_VALUE by Issue(Severity.ERROR, Category.COMPATIBILITY)
    val CHANGED_SUPERCLASS by Issue(Severity.ERROR, Category.COMPATIBILITY)
    val CHANGED_SCOPE by Issue(Severity.ERROR, Category.COMPATIBILITY)
    val CHANGED_ABSTRACT by Issue(Severity.ERROR, Category.COMPATIBILITY)
    val CHANGED_DEFAULT by Issue(Severity.ERROR, Category.COMPATIBILITY)
    val CHANGED_THROWS by Issue(Severity.ERROR, Category.COMPATIBILITY)
    val CHANGED_NATIVE by Issue(Severity.HIDDEN, Category.COMPATIBILITY)
    val CHANGED_CLASS by Issue(Severity.ERROR, Category.COMPATIBILITY)
    val CHANGED_DEPRECATED by Issue(Severity.HIDDEN, Category.COMPATIBILITY)
    val CHANGED_SYNCHRONIZED by Issue(Severity.HIDDEN, Category.COMPATIBILITY)
    val CONFLICTING_SHOW_ANNOTATIONS by Issue(Severity.ERROR, Category.UNKNOWN)
    val ADDED_FINAL_UNINSTANTIABLE by Issue(Severity.HIDDEN, Category.COMPATIBILITY)
    val REMOVED_FINAL by Issue(Severity.ERROR, Category.COMPATIBILITY)
    val REMOVED_FINAL_STRICT by Issue(Severity.ERROR, Category.COMPATIBILITY)
    val REMOVED_DEPRECATED_CLASS by Issue(REMOVED_CLASS, Category.COMPATIBILITY)
    val REMOVED_DEPRECATED_METHOD by Issue(REMOVED_METHOD, Category.COMPATIBILITY)
    val REMOVED_DEPRECATED_FIELD by Issue(REMOVED_FIELD, Category.COMPATIBILITY)
    val ADDED_ABSTRACT_METHOD by Issue(Severity.ERROR, Category.COMPATIBILITY)
    val ADDED_REIFIED by Issue(Severity.ERROR, Category.COMPATIBILITY)
    val REMOVED_JVM_DEFAULT_WITH_COMPATIBILITY by Issue(Severity.ERROR, Category.COMPATIBILITY)

    // Issues in javadoc generation
    val UNRESOLVED_LINK by Issue(Severity.LINT, Category.DOCUMENTATION)
    val UNAVAILABLE_SYMBOL by Issue(Severity.WARNING, Category.DOCUMENTATION)
    val HIDDEN_SUPERCLASS by Issue(Severity.WARNING, Category.DOCUMENTATION)
    val DEPRECATED by Issue(Severity.HIDDEN, Category.DOCUMENTATION)
    val DEPRECATION_MISMATCH by Issue(Severity.ERROR, Category.DOCUMENTATION)
    val IO_ERROR by Issue(Severity.ERROR)
    val HIDDEN_TYPE_PARAMETER by Issue(Severity.WARNING, Category.DOCUMENTATION)
    val PRIVATE_SUPERCLASS by Issue(Severity.WARNING, Category.DOCUMENTATION)
    val NULLABLE by Issue(Severity.HIDDEN, Category.DOCUMENTATION)
    val INT_DEF by Issue(Severity.HIDDEN, Category.DOCUMENTATION)
    val REQUIRES_PERMISSION by Issue(Severity.LINT, Category.DOCUMENTATION)
    val BROADCAST_BEHAVIOR by Issue(Severity.LINT, Category.DOCUMENTATION)
    val SDK_CONSTANT by Issue(Severity.LINT, Category.DOCUMENTATION)
    val TODO by Issue(Severity.LINT, Category.DOCUMENTATION)
    val NO_ARTIFACT_DATA by Issue(Severity.HIDDEN, Category.DOCUMENTATION)
    val BROKEN_ARTIFACT_FILE by Issue(Severity.ERROR, Category.DOCUMENTATION)

    // Metalava warnings (not from doclava)

    val INVALID_FEATURE_ENFORCEMENT by Issue(Severity.LINT, Category.DOCUMENTATION)

    val MISSING_PERMISSION by Issue(Severity.LINT, Category.DOCUMENTATION)
    val MULTIPLE_THREAD_ANNOTATIONS by Issue(Severity.LINT, Category.DOCUMENTATION)
    val UNRESOLVED_CLASS by Issue(Severity.LINT, Category.DOCUMENTATION)
    val INVALID_NULL_CONVERSION by Issue(Severity.ERROR, Category.COMPATIBILITY)
    val PARAMETER_NAME_CHANGE by Issue(Severity.ERROR, Category.COMPATIBILITY)
    val OPERATOR_REMOVAL by Issue(Severity.ERROR, Category.COMPATIBILITY)
    val INFIX_REMOVAL by Issue(Severity.ERROR, Category.COMPATIBILITY)
    val VARARG_REMOVAL by Issue(Severity.ERROR, Category.COMPATIBILITY)
    val ADD_SEALED by Issue(Severity.ERROR, Category.COMPATIBILITY)
    val FUN_REMOVAL by Issue(Severity.ERROR, Category.COMPATIBILITY)
    val BECAME_UNCHECKED by Issue(Severity.ERROR, Category.COMPATIBILITY)
    val ANNOTATION_EXTRACTION by Issue(Severity.ERROR)
    val SUPERFLUOUS_PREFIX by Issue(Severity.WARNING)
    val HIDDEN_TYPEDEF_CONSTANT by Issue(Severity.ERROR)
    val EXPECTED_PLATFORM_TYPE by Issue(Severity.HIDDEN)
    val INTERNAL_ERROR by Issue(Severity.ERROR)
    val RETURNING_UNEXPECTED_CONSTANT by Issue(Severity.WARNING)
    val DEPRECATED_OPTION by Issue(Severity.WARNING)
    val BOTH_PACKAGE_INFO_AND_HTML by Issue(Severity.WARNING, Category.DOCUMENTATION)
    val UNMATCHED_MERGE_ANNOTATION by Issue(Severity.ERROR)
    // The plan is for this to be set as an error once (1) existing code is marked as @deprecated
    // and (2) the principle is adopted by the API council
    val REFERENCES_DEPRECATED by Issue(Severity.HIDDEN)
    val UNHIDDEN_SYSTEM_API by Issue(Severity.ERROR)
    val SHOWING_MEMBER_IN_HIDDEN_CLASS by Issue(Severity.ERROR)
    val INVALID_NULLABILITY_ANNOTATION by Issue(Severity.ERROR)
    val REFERENCES_HIDDEN by Issue(Severity.ERROR)
    val IGNORING_SYMLINK by Issue(Severity.INFO)
    val INVALID_NULLABILITY_ANNOTATION_WARNING by Issue(Severity.WARNING)
    // The plan is for this to be set as an error once (1) existing code is marked as @deprecated
    // and (2) the principle is adopted by the API council
    val EXTENDS_DEPRECATED by Issue(Severity.HIDDEN)
    val FORBIDDEN_TAG by Issue(Severity.ERROR)
    val MISSING_COLUMN by Issue(Severity.WARNING, Category.DOCUMENTATION)
    val INVALID_SYNTAX by Issue(Severity.ERROR)
    val UNRESOLVED_IMPORT by Issue(Severity.INFO)
    val HIDDEN_ABSTRACT_METHOD by Issue(Severity.ERROR)

    // API lint
    val START_WITH_LOWER by Issue(Severity.ERROR, Category.API_LINT)
    val START_WITH_UPPER by Issue(Severity.ERROR, Category.API_LINT)
    val ALL_UPPER by Issue(Severity.ERROR, Category.API_LINT)
    val ACRONYM_NAME by Issue(Severity.WARNING, Category.API_LINT)
    val ENUM by Issue(Severity.ERROR, Category.API_LINT)
    val ENDS_WITH_IMPL by Issue(Severity.ERROR, Category.API_LINT)
    val MIN_MAX_CONSTANT by Issue(Severity.WARNING, Category.API_LINT)
    val COMPILE_TIME_CONSTANT by Issue(Severity.ERROR, Category.API_LINT)
    val SINGULAR_CALLBACK by Issue(Severity.ERROR, Category.API_LINT)
    val CALLBACK_NAME by Issue(Severity.WARNING, Category.API_LINT)
    // Obsolete per https://s.android.com/api-guidelines.
    val CALLBACK_INTERFACE by Issue(Severity.HIDDEN, Category.API_LINT)
    val CALLBACK_METHOD_NAME by Issue(Severity.ERROR, Category.API_LINT)
    val LISTENER_INTERFACE by Issue(Severity.ERROR, Category.API_LINT)
    val SINGLE_METHOD_INTERFACE by Issue(Severity.ERROR, Category.API_LINT)
    val INTENT_NAME by Issue(Severity.ERROR, Category.API_LINT)
    val ACTION_VALUE by Issue(Severity.ERROR, Category.API_LINT)
    val EQUALS_AND_HASH_CODE by Issue(Severity.ERROR, Category.API_LINT)
    val PARCEL_CREATOR by Issue(Severity.ERROR, Category.API_LINT)
    val PARCEL_NOT_FINAL by Issue(Severity.ERROR, Category.API_LINT)
    val PARCEL_CONSTRUCTOR by Issue(Severity.ERROR, Category.API_LINT)
    val PROTECTED_MEMBER by Issue(Severity.ERROR, Category.API_LINT)
    val PAIRED_REGISTRATION by Issue(Severity.ERROR, Category.API_LINT)
    val REGISTRATION_NAME by Issue(Severity.ERROR, Category.API_LINT)
    val VISIBLY_SYNCHRONIZED by Issue(Severity.ERROR, Category.API_LINT)
    val INTENT_BUILDER_NAME by Issue(Severity.WARNING, Category.API_LINT)
    val CONTEXT_NAME_SUFFIX by Issue(Severity.ERROR, Category.API_LINT)
    val INTERFACE_CONSTANT by Issue(Severity.ERROR, Category.API_LINT)
    val ON_NAME_EXPECTED by Issue(Severity.WARNING, Category.API_LINT)
    val TOP_LEVEL_BUILDER by Issue(Severity.WARNING, Category.API_LINT)
    val MISSING_BUILD_METHOD by Issue(Severity.WARNING, Category.API_LINT)
    val BUILDER_SET_STYLE by Issue(Severity.WARNING, Category.API_LINT)
    val SETTER_RETURNS_THIS by Issue(Severity.WARNING, Category.API_LINT)
    val RAW_AIDL by Issue(Severity.ERROR, Category.API_LINT)
    val INTERNAL_CLASSES by Issue(Severity.ERROR, Category.API_LINT)
    val PACKAGE_LAYERING by Issue(Severity.WARNING, Category.API_LINT)
    val GETTER_SETTER_NAMES by Issue(Severity.ERROR, Category.API_LINT)
    val CONCRETE_COLLECTION by Issue(Severity.ERROR, Category.API_LINT)
    val OVERLAPPING_CONSTANTS by Issue(Severity.WARNING, Category.API_LINT)
    val GENERIC_EXCEPTION by Issue(Severity.ERROR, Category.API_LINT)
    val ILLEGAL_STATE_EXCEPTION by Issue(Severity.WARNING, Category.API_LINT)
    val RETHROW_REMOTE_EXCEPTION by Issue(Severity.ERROR, Category.API_LINT)
    val MENTIONS_GOOGLE by Issue(Severity.ERROR, Category.API_LINT)
    val HEAVY_BIT_SET by Issue(Severity.ERROR, Category.API_LINT)
    val MANAGER_CONSTRUCTOR by Issue(Severity.ERROR, Category.API_LINT)
    val MANAGER_LOOKUP by Issue(Severity.ERROR, Category.API_LINT)
    val AUTO_BOXING by Issue(Severity.ERROR, Category.API_LINT)
    val STATIC_UTILS by Issue(Severity.ERROR, Category.API_LINT)
    val CONTEXT_FIRST by Issue(Severity.ERROR, Category.API_LINT)
    val LISTENER_LAST by Issue(Severity.WARNING, Category.API_LINT)
    val EXECUTOR_REGISTRATION by Issue(Severity.WARNING, Category.API_LINT)
    val CONFIG_FIELD_NAME by Issue(Severity.ERROR, Category.API_LINT)
    val RESOURCE_FIELD_NAME by Issue(Severity.ERROR, Category.API_LINT)
    val RESOURCE_VALUE_FIELD_NAME by Issue(Severity.ERROR, Category.API_LINT)
    val RESOURCE_STYLE_FIELD_NAME by Issue(Severity.ERROR, Category.API_LINT)
    val STREAM_FILES by Issue(Severity.WARNING, Category.API_LINT)
    val PARCELABLE_LIST by Issue(Severity.WARNING, Category.API_LINT)
    val ABSTRACT_INNER by Issue(Severity.WARNING, Category.API_LINT)
    val BANNED_THROW by Issue(Severity.ERROR, Category.API_LINT)
    val EXTENDS_ERROR by Issue(Severity.ERROR, Category.API_LINT)
    val EXCEPTION_NAME by Issue(Severity.ERROR, Category.API_LINT)
    val METHOD_NAME_UNITS by Issue(Severity.ERROR, Category.API_LINT)
    val FRACTION_FLOAT by Issue(Severity.ERROR, Category.API_LINT)
    val PERCENTAGE_INT by Issue(Severity.ERROR, Category.API_LINT)
    val NOT_CLOSEABLE by Issue(Severity.WARNING, Category.API_LINT)
    val KOTLIN_OPERATOR by Issue(Severity.INFO, Category.API_LINT)
    val ARRAY_RETURN by Issue(Severity.WARNING, Category.API_LINT)
    val USER_HANDLE by Issue(Severity.WARNING, Category.API_LINT)
    val USER_HANDLE_NAME by Issue(Severity.WARNING, Category.API_LINT)
    val SERVICE_NAME by Issue(Severity.ERROR, Category.API_LINT)
    val METHOD_NAME_TENSE by Issue(Severity.WARNING, Category.API_LINT)
    val NO_CLONE by Issue(Severity.ERROR, Category.API_LINT)
    val USE_ICU by Issue(Severity.WARNING, Category.API_LINT)
    val USE_PARCEL_FILE_DESCRIPTOR by Issue(Severity.ERROR, Category.API_LINT)
    val NO_BYTE_OR_SHORT by Issue(Severity.WARNING, Category.API_LINT)
    val SINGLETON_CONSTRUCTOR by Issue(Severity.ERROR, Category.API_LINT)
    val KOTLIN_KEYWORD by Issue(Severity.ERROR, Category.API_LINT)
    val UNIQUE_KOTLIN_OPERATOR by Issue(Severity.ERROR, Category.API_LINT)
    val SAM_SHOULD_BE_LAST by Issue(Severity.WARNING, Category.API_LINT)
    val MISSING_JVMSTATIC by Issue(Severity.WARNING, Category.API_LINT)
    val DEFAULT_VALUE_CHANGE by Issue(Severity.ERROR, Category.API_LINT)
    val DOCUMENT_EXCEPTIONS by Issue(Severity.ERROR, Category.API_LINT)
    val FORBIDDEN_SUPER_CLASS by Issue(Severity.ERROR, Category.API_LINT)
    val MISSING_NULLABILITY by Issue(Severity.ERROR, Category.API_LINT)
    val INVALID_NULLABILITY_OVERRIDE by Issue(Severity.ERROR, Category.API_LINT)
    val MUTABLE_BARE_FIELD by Issue(Severity.ERROR, Category.API_LINT)
    val INTERNAL_FIELD by Issue(Severity.ERROR, Category.API_LINT)
    val PUBLIC_TYPEDEF by Issue(Severity.ERROR, Category.API_LINT)
    val ANDROID_URI by Issue(Severity.ERROR, Category.API_LINT)
    val BAD_FUTURE by Issue(Severity.ERROR, Category.API_LINT)
    val STATIC_FINAL_BUILDER by Issue(Severity.WARNING, Category.API_LINT)
    val GETTER_ON_BUILDER by Issue(Severity.WARNING, Category.API_LINT)
    val MISSING_GETTER_MATCHING_BUILDER by Issue(Severity.WARNING, Category.API_LINT)
    val OPTIONAL_BUILDER_CONSTRUCTOR_ARGUMENT by Issue(Severity.WARNING, Category.API_LINT)
    val NO_SETTINGS_PROVIDER by Issue(Severity.HIDDEN, Category.API_LINT)
    val NULLABLE_COLLECTION by Issue(Severity.WARNING, Category.API_LINT)
    val ASYNC_SUFFIX_FUTURE by Issue(Severity.ERROR, Category.API_LINT)
    val GENERIC_CALLBACKS by Issue(Severity.ERROR, Category.API_LINT)
    val KOTLIN_DEFAULT_PARAMETER_ORDER by Issue(Severity.ERROR, Category.API_LINT_ANDROIDX_MISC)
    val UNFLAGGED_API by Issue(Severity.HIDDEN, Category.API_LINT)
    val FLAGGED_API_LITERAL by Issue(Severity.HIDDEN, Category.API_LINT)

    fun findIssueById(id: String?): Issue? {
        return nameToIssue[id]
    }

    fun findIssueByIdIgnoringCase(id: String): Issue? {
        for (e in allIssues) {
            if (id.equals(e.name, ignoreCase = true)) {
                return e
            }
        }
        return null
    }

    fun findCategoryById(id: String?): Category? = Category.values().find { it.id == id }

    fun findIssuesByCategory(category: Category?): List<Issue> =
        allIssues.filter { it.category == category }

    class Issue
    private constructor(
        val defaultLevel: Severity,
        /**
         * When `level` is set to [Severity.INHERIT], this is the parent from which the issue will
         * inherit its level.
         */
        val parent: Issue?,
        /** Applicable category */
        val category: Category,
    ) : ReadOnlyProperty<Issues, Issue> {
        /** The name of this issue */
        lateinit var name: String
            internal set

        internal constructor(
            defaultLevel: Severity,
            category: Category = Category.UNKNOWN
        ) : this(defaultLevel, null, category)

        internal constructor(
            parent: Issue,
            category: Category
        ) : this(Severity.INHERIT, parent, category)

        /**
         * Called to get the value of the delegating property; as this is the value just return it.
         */
        override fun getValue(thisRef: Issues, property: KProperty<*>): Issue {
            return this
        }

        /**
         * Called once on creation to retrieve the property delegate.
         *
         * Initializes the name and adds a mapping from the name to this and then just returns this
         * as the delegate.
         */
        operator fun provideDelegate(thisRef: Issues, property: KProperty<*>): Issue {
            // Initialize issue names based on the property names.
            name = enumConstantToCamelCase(property.name)
            nameToIssue[name] = this
            return this
        }

        override fun toString(): String {
            return "Issue $name"
        }

        init {
            allIssues.add(this)
        }
    }

    enum class Category(val description: String) {
        COMPATIBILITY("Compatibility"),
        DOCUMENTATION("Documentation"),
        API_LINT("API Lint"),
        // AndroidX API guidelines are split across multiple files, so add a category per-file
        API_LINT_ANDROIDX_MISC("API Lint"),
        UNKNOWN("Default");

        /** Identifier for use in command-line arguments and reporting. */
        val id: String = enumConstantToCamelCase(name)
    }

    init {
        // Make sure that every Issue was created as a property delegate using `by` and not just
        // assigned to the field using `=`.
        for (issue in allIssues) {
            check(issue.name != "")
        }
    }
}

/**
 * Convert enum constant name to camel case starting with an upper case letter.
 *
 * e.g. `ALPHA_BETA` becomes `AlphaBeta`.
 */
private fun enumConstantToCamelCase(name: String): String {
    return name
        .splitToSequence("_")
        .map { "${it[0]}${it.substring(1).lowercase(Locale.US)}" }
        .joinToString("")
}
