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

import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.reporter.FileLocation
import java.io.LineNumberReader
import java.io.Reader
import java.nio.file.Path
import java.util.Locale

/**
 * Encapsulates all the information related to the format of a signature file.
 *
 * Some of these will be initialized from the version specific defaults and some will be overridden
 * on the command line.
 */
data class FileFormat(
    val version: Version,
    /**
     * If specified then it contains property defaults that have been specified on the command line
     * and whose value should be used as the default for any property that has not been specified in
     * this format.
     *
     * Not every property is eligible to have its default overridden on the command line. Only those
     * that have a property getter to provide the default.
     */
    val formatDefaults: FileFormat? = null,

    /**
     * If non-null then it specifies the name of the API.
     *
     * It must start with a lower case letter, contain any number of lower case letters, numbers and
     * hyphens, and end with either a lowercase letter or number.
     *
     * Its purpose is to provide information to metalava and to a lesser extent the owner of the
     * file about which API the file contains. The exact meaning of the API name is determined by
     * the owner, metalava simply uses this as an identifier for comparison.
     */
    val name: String? = null,

    /**
     * If non-null then it specifies the name of the API surface.
     *
     * It must start with a lower case letter, contain any number of lower case letters, numbers and
     * hyphens, and end with either a lowercase letter or number.
     *
     * Its purpose is to provide information to metalava and to a lesser extent the owner of the
     * file about which API surface the file contains. The exact meaning of the API surface name is
     * determined by the owner, metalava simply uses this as an identifier for comparison.
     */
    val surface: String? = null,

    /**
     * If non-null then it indicates the target language for signature files.
     *
     * Although kotlin and java can interoperate reasonably well an API created from Java files is
     * generally targeted for use by Java code and vice versa.
     */
    val language: Language? = null,
    val specifiedOverloadedMethodOrder: OverloadedMethodOrder? = null,

    /**
     * Whether to include type-use annotations in the signature file. Type-use annotations can only
     * be included when [kotlinNameTypeOrder] is true, because the Java order makes it ambiguous
     * whether an annotation is type-use.
     */
    val includeTypeUseAnnotations: Boolean = false,

    /**
     * Whether to order the names and types of APIs using Kotlin-style syntax (`name: type`) or
     * Java-style syntax (`type name`).
     *
     * When Kotlin ordering is used, all method parameters without public names will be given the
     * placeholder name of `_`, which cannot be used as a Java identifier.
     *
     * For example, the following is an example of a method signature with Kotlin ordering:
     * ```
     * method public foo(_: int, _: char, _: String[]): String;
     * ```
     *
     * And the following is the equivalent Java ordering:
     * ```
     * method public String foo(int, char, String[]);
     * ```
     */
    val kotlinNameTypeOrder: Boolean = false,
    val kotlinStyleNulls: Boolean,
    /**
     * If non-null then it indicates that the file format is being used to migrate a signature file
     * to fix a bug that causes a change in the signature file contents but not a change in version.
     * e.g. This would be used when migrating a 2.0 file format that currently uses source order for
     * overloaded methods (using a command line parameter to override the default order of
     * signature) to a 2.0 file that uses signature order.
     *
     * This should be used to provide an explanation as to what is being migrated and why. It should
     * be relatively concise, e.g. something like:
     * ```
     * "See <short-url> for details"
     * ```
     *
     * This value cannot use `,` (because it is a separator between properties in [specifier]) or
     * `\n` (because it is the terminator of the signature format line).
     */
    val migrating: String? = null,
    val conciseDefaultValues: Boolean,
    val specifiedAddAdditionalOverrides: Boolean? = null,

    /**
     * Indicates whether the whole extends list for an interface is sorted.
     *
     * Previously, the first type in the extends list was used as the super type and if it was
     * present in the API then it would always be output first to the signature files. The code has
     * been refactored so that is no longer necessary but the previous behavior is maintained to
     * avoid churn in the API signature files.
     *
     * By default, this property preserves the previous behavior but if set to `true` then it will
     * stop treating the first interface specially and just sort all the interface types. The
     * sorting is by the full name (without the package) of the class.
     */
    val specifiedSortWholeExtendsList: Boolean? = null,
) {
    init {
        if (migrating != null && "[,\n]".toRegex().find(migrating) != null) {
            throw IllegalStateException(
                """invalid value for property 'migrating': '$migrating' contains at least one invalid character from the set {',', '\n'}"""
            )
        }

        validateIdentifier(name, "name")
        validateIdentifier(surface, "surface")

        if (includeTypeUseAnnotations && !kotlinNameTypeOrder) {
            throw IllegalStateException(
                "Type-use annotations can only be included in signatures when `kotlin-name-type-order=yes` is set"
            )
        }
    }

    /** Check that the supplied identifier is valid. */
    private fun validateIdentifier(identifier: String?, propertyName: String) {
        identifier ?: return
        if ("[a-z]([a-z0-9-]*[a-z0-9])?".toRegex().matchEntire(identifier) == null) {
            throw IllegalStateException(
                """invalid value for property '$propertyName': '$identifier' must start with a lower case letter, contain any number of lower case letters, numbers and hyphens, and end with either a lowercase letter or number"""
            )
        }
    }

    /**
     * Compute the effective value of an optional property whose default can be overridden.
     *
     * This returns the first non-null value in the following:
     * 1. This [FileFormat]'s property value.
     * 2. The [formatDefaults]'s property value
     * 3. The [default] value.
     *
     * @param getter a getter for the optional property's value.
     * @param default the default value.
     */
    private inline fun <T> effectiveValue(getter: FileFormat.() -> T?, default: T): T {
        return this.getter() ?: formatDefaults?.getter() ?: default
    }

    // This defaults to SIGNATURE but can be overridden on the command line.
    val overloadedMethodOrder
        get() = effectiveValue({ specifiedOverloadedMethodOrder }, OverloadedMethodOrder.SIGNATURE)

    // This defaults to false but can be overridden on the command line.
    val addAdditionalOverrides
        get() = effectiveValue({ specifiedAddAdditionalOverrides }, false)

    // This defaults to false but can be overridden on the command line.
    val sortWholeExtendsList
        get() = effectiveValue({ specifiedSortWholeExtendsList }, default = false)

    /** The base version of the file format. */
    enum class Version(
        /** The version number of this as a string, e.g. "3.0". */
        internal val versionNumber: String,

        /** Indicates whether the version supports properties fully or just for migrating. */
        internal val propertySupport: PropertySupport = PropertySupport.FOR_MIGRATING_ONLY,

        /**
         * Factory used to create a [FileFormat] instance encapsulating the defaults of this
         * version.
         */
        factory: (Version) -> FileFormat,
    ) {
        V2(
            versionNumber = "2.0",
            factory = { version ->
                FileFormat(
                    version = version,
                    kotlinStyleNulls = false,
                    conciseDefaultValues = false,
                )
            }
        ),
        V3(
            versionNumber = "3.0",
            factory = { version ->
                V2.defaults.copy(
                    version = version,
                    // This adds kotlinStyleNulls = true
                    kotlinStyleNulls = true,
                )
            }
        ),
        V4(
            versionNumber = "4.0",
            factory = { version ->
                V3.defaults.copy(
                    version = version,
                    // This adds conciseDefaultValues = true
                    conciseDefaultValues = true,
                )
            }
        ),
        V5(
            versionNumber = "5.0",
            // This adds full property support.
            propertySupport = PropertySupport.FULL,
            factory = { version ->
                V4.defaults.copy(
                    version = version,
                    // This does not add any property defaults, just full property support.
                )
            }
        );

        /**
         * The defaults associated with this version.
         *
         * It is initialized via a factory to break the cycle where the [Version] constructor
         * depends on the [FileFormat] constructor and vice versa.
         */
        internal val defaults = factory(this)

        /**
         * Get the version defaults plus any language defaults, if available.
         *
         * @param language the optional language whose defaults should be applied to the version
         *   defaults.
         */
        internal fun defaultsIncludingLanguage(language: Language?): FileFormat {
            language ?: return defaults
            return Builder(defaults).let {
                language.applyLanguageDefaults(it)
                it.build()
            }
        }
    }

    internal enum class PropertySupport {
        /**
         * The version only supports properties being temporarily specified in the signature file to
         * aid migration.
         */
        FOR_MIGRATING_ONLY,

        /**
         * The version supports properties fully, both for migration and permanent customization in
         * the signature file.
         */
        FULL
    }

    /**
     * The language which the signature targets. While a Java API can be used by Kotlin, and vice
     * versa, each API typically targets a specific language and this specifies that.
     *
     * This is independent of the [Version].
     */
    enum class Language(
        private val conciseDefaultValues: Boolean,
        private val kotlinStyleNulls: Boolean,
    ) {
        JAVA(conciseDefaultValues = false, kotlinStyleNulls = false),
        KOTLIN(conciseDefaultValues = true, kotlinStyleNulls = true);

        internal fun applyLanguageDefaults(builder: Builder) {
            if (builder.conciseDefaultValues == null) {
                builder.conciseDefaultValues = conciseDefaultValues
            }
            if (builder.kotlinStyleNulls == null) {
                builder.kotlinStyleNulls = kotlinStyleNulls
            }
        }
    }

    enum class OverloadedMethodOrder(val comparator: Comparator<MethodItem>) {
        /** Sort overloaded methods according to source order. */
        SOURCE(MethodItem.sourceOrderForOverloadedMethodsComparator),

        /** Sort overloaded methods by their signature. */
        SIGNATURE(MethodItem.comparator)
    }

    /**
     * Get the header for the signature file that corresponds to this format.
     *
     * This always starts with the signature format prefix, and the version number, following by a
     * newline and some option property assignments (e.g. `property=value`), one per line prefixed
     * with [PROPERTY_LINE_PREFIX].
     */
    fun header(): String {
        return buildString {
            append(SIGNATURE_FORMAT_PREFIX)
            append(version.versionNumber)
            append("\n")
            // Only output properties if the version supports them fully or it is migrating.
            if (version.propertySupport == PropertySupport.FULL || migrating != null) {
                iterateOverCustomizableProperties { property, value ->
                    append(PROPERTY_LINE_PREFIX)
                    append(property)
                    append("=")
                    append(value)
                    append("\n")
                }
            }
        }
    }

    /**
     * Get the specifier for this format.
     *
     * It starts with the version number followed by an optional `:` followed by at least one comma
     * separate `property=value` pair. This is used on the command line for the `--format` option.
     */
    fun specifier(): String {
        return buildString {
            append(version.versionNumber)

            var separator = VERSION_PROPERTIES_SEPARATOR
            iterateOverCustomizableProperties { property, value ->
                append(separator)
                separator = ","
                append(property)
                append("=")
                append(value)
            }
        }
    }

    /**
     * Iterate over all the properties of this format which have different values to the values in
     * this format's [Version.defaultsIncludingLanguage], invoking the [consumer] with each
     * property, value pair.
     */
    private fun iterateOverCustomizableProperties(consumer: (String, String) -> Unit) {
        val defaults = version.defaultsIncludingLanguage(language)
        if (this@FileFormat != defaults) {
            CustomizableProperty.values().forEach { prop ->
                // Get the string value of this property, if null then it was not specified so skip
                // the property.
                val thisValue = prop.stringFromFormat(this@FileFormat) ?: return@forEach
                val defaultValue = prop.stringFromFormat(defaults)
                if (thisValue != defaultValue) {
                    consumer(prop.propertyName, thisValue)
                }
            }
        }
    }

    /**
     * Validate the format
     *
     * @param exceptionContext information to add to the start of the exception message that
     *   provides context for the user.
     * @param migratingAllowed true if the [migrating] option is allowed, false otherwise. If it is
     *   allowed then it will also be required if [Version.propertySupport] is
     *   [PropertySupport.FOR_MIGRATING_ONLY].
     */
    private fun validate(exceptionContext: String = "", migratingAllowed: Boolean) {
        // If after applying all the properties the format matches its version defaults then
        // there is nothing else to check.
        if (this == version.defaults) {
            return
        }

        if (migratingAllowed) {
            // If the version does not support properties (except when migrating) and the
            // version defaults have been overridden then the `migrating` property is mandatory
            // when migrating is allowed.
            if (version.propertySupport != PropertySupport.FULL && migrating == null) {
                throw ApiParseException(
                    "${exceptionContext}must provide a 'migrating' property when customizing version ${version.versionNumber}"
                )
            }
        } else if (migrating != null) {
            throw ApiParseException("${exceptionContext}must not contain a 'migrating' property")
        }
    }

    companion object {
        private val allDefaults = Version.values().map { it.defaults }.toList()

        private val versionByNumber = Version.values().associateBy { it.versionNumber }

        // The defaults associated with version 2.0.
        val V2 = Version.V2.defaults

        // The defaults associated with version 3.0.
        val V3 = Version.V3.defaults

        // The defaults associated with version 4.0.
        val V4 = Version.V4.defaults

        // The defaults associated with version 5.0.
        val V5 = Version.V5.defaults

        // The defaults associated with the latest version.
        val LATEST = allDefaults.last()

        const val SIGNATURE_FORMAT_PREFIX = "// Signature format: "

        /**
         * The size of the buffer and read ahead limit.
         *
         * Should be big enough to handle any first package line, even one with lots of annotations.
         */
        private const val BUFFER_SIZE = 1024

        /**
         * Parse the start of the contents provided by [reader] to obtain the [FileFormat]
         *
         * @param path the [Path] of the file from which the content is being read.
         * @param reader the reader to use to read the file contents.
         * @param formatForLegacyFiles the optional format to use if the file uses a legacy, and now
         *   unsupported file format.
         * @return the [FileFormat] or null if the reader was blank.
         */
        fun parseHeader(
            path: Path,
            reader: Reader,
            formatForLegacyFiles: FileFormat? = null
        ): FileFormat? {
            val lineNumberReader =
                if (reader is LineNumberReader) reader else LineNumberReader(reader, BUFFER_SIZE)

            try {
                return parseHeader(lineNumberReader, formatForLegacyFiles)
            } catch (cause: ApiParseException) {
                // Wrap the exception and add contextual information to help user identify and fix
                // the problem. This is done here instead of when throwing the exception as the
                // original thrower does not have that context.
                throw ApiParseException(
                    "Signature format error - ${cause.message}",
                    FileLocation.createLocation(path, lineNumberReader.lineNumber),
                    cause,
                )
            }
        }

        /**
         * Parse the start of the contents provided by [reader] to obtain the [FileFormat]
         *
         * This consumes only the content that makes up the header. So, the rest of the file
         * contents can be read from the reader.
         *
         * @return the [FileFormat] or null if the reader was blank.
         */
        private fun parseHeader(
            reader: LineNumberReader,
            formatForLegacyFiles: FileFormat?
        ): FileFormat? {
            // Remember the starting position of the reader just in case it is necessary to reset
            // it back to this point.
            reader.mark(BUFFER_SIZE)

            // This reads the minimal amount to determine whether this is likely to be a
            // signature file.
            val prefixLength = SIGNATURE_FORMAT_PREFIX.length
            val buffer = CharArray(prefixLength)
            val prefix =
                reader.read(buffer, 0, prefixLength).let { count ->
                    if (count == -1) {
                        // An empty file.
                        return null
                    }
                    String(buffer, 0, count)
                }

            if (prefix != SIGNATURE_FORMAT_PREFIX) {
                // If the prefix is blank then either the whole file is blank in which case it is
                // handled specially, or the file is not blank and is not a signature file in which
                // case it is an error.
                if (prefix.isBlank()) {
                    var line = reader.readLine()
                    while (line != null && line.isBlank()) {
                        line = reader.readLine()
                    }
                    // If the line is null then te whole file is blank which is handled specially.
                    if (line == null) {
                        return null
                    }
                }

                // If formatForLegacyFiles has been provided then check to see if the file adheres
                // to a legacy format and if it does behave as if it was formatForLegacyFiles.
                if (formatForLegacyFiles != null) {
                    // Check for version 1.0, i.e. no header at all.
                    if (prefix.startsWith("package ")) {
                        reader.reset()
                        return formatForLegacyFiles
                    }
                }

                // An error occurred as the prefix did not match. A valid prefix must appear on a
                // single line so just in case what was read contains multiple lines trim it down to
                // a single line for error reporting. The LineNumberReader has translated non-unix
                // newline characters into `\n` so this is safe.
                val firstLine = prefix.substringBefore("\n")
                // As the error is going to be reported for the first line, even though possibly
                // multiple lines have been read set the line number to 1.
                reader.lineNumber = 1
                throw ApiParseException(
                    "invalid prefix, found '$firstLine', expected '$SIGNATURE_FORMAT_PREFIX'"
                )
            }

            // Read the rest of the line after the SIGNATURE_FORMAT_PREFIX which should just be the
            // version.
            val versionNumber = reader.readLine()
            val version = getVersionFromNumber(versionNumber)

            val format = parseProperties(reader, version)
            format.validate(migratingAllowed = true)
            return format
        }

        private const val VERSION_PROPERTIES_SEPARATOR = ":"

        /**
         * Parse a format specifier string and create a corresponding [FileFormat].
         *
         * The [specifier] consists of a version, e.g. `4.0`, followed by an optional list of comma
         * separate properties. If the properties are provided then they are separated from the
         * version with a `:`. A property is expressed as a property assignment, e.g.
         * `property=value`.
         *
         * This extracts the version and then if no properties are provided returns its defaults. If
         * properties are provided then each property is checked to make sure that it is a valid
         * property with a valid value and then it is applied on top of the version defaults. The
         * result of that is returned.
         *
         * @param specifier the specifier string that defines a [FileFormat].
         * @param migratingAllowed indicates whether the `migrating` property is allowed in the
         *   specifier.
         * @param extraVersions extra versions to add to the error message if a version is not
         *   supported but otherwise ignored. This allows the caller to handle some additional
         *   versions first but still report a helpful message.
         */
        fun parseSpecifier(
            specifier: String,
            migratingAllowed: Boolean = false,
            extraVersions: Set<String> = emptySet(),
        ): FileFormat {
            val specifierParts = specifier.split(VERSION_PROPERTIES_SEPARATOR, limit = 2)
            val versionNumber = specifierParts[0]
            val version = getVersionFromNumber(versionNumber, extraVersions)
            val versionDefaults = version.defaults
            if (specifierParts.size == 1) {
                return versionDefaults
            }

            val properties = specifierParts[1]

            val builder = Builder(versionDefaults)
            properties.trim().split(",").forEach { parsePropertyAssignment(builder, it) }
            val format = builder.build()

            format.validate(
                exceptionContext = "invalid format specifier: '$specifier' - ",
                migratingAllowed = migratingAllowed,
            )

            return format
        }

        /**
         * Get the [Version] from the number.
         *
         * @param versionNumber the version number as a string.
         * @param extraVersions extra versions to add to the error message if a version is not
         *   supported but otherwise ignored. This allows the caller to handle some additional
         *   versions first but still report a helpful message.
         */
        private fun getVersionFromNumber(
            versionNumber: String,
            extraVersions: Set<String> = emptySet(),
        ): Version =
            versionByNumber[versionNumber]
                ?: let {
                    val allVersions = versionByNumber.keys + extraVersions
                    val possibilities = allVersions.joinToString { "'$it'" }
                    throw ApiParseException(
                        "invalid version, found '$versionNumber', expected one of $possibilities"
                    )
                }

        /**
         * Parse a property assignment of the form `property=value`, updating the appropriate
         * property in [builder], or throwing an exception if there was a problem.
         *
         * @param builder the [Builder] into which the property's value will be added.
         * @param assignment the string of the form `property=value`.
         * @param propertyFilter optional filter that determines the set of allowable properties;
         *   defaults to all properties.
         */
        private fun parsePropertyAssignment(
            builder: Builder,
            assignment: String,
            propertyFilter: (CustomizableProperty) -> Boolean = { true },
        ) {
            val propertyParts = assignment.split("=")
            if (propertyParts.size != 2) {
                throw ApiParseException("expected <property>=<value> but found '$assignment'")
            }
            val name = propertyParts[0]
            val value = propertyParts[1]
            val customizable = CustomizableProperty.getByName(name, propertyFilter)
            customizable.setFromString(builder, value)
        }

        private const val PROPERTY_LINE_PREFIX = "// - "

        /**
         * Parse property pairs, one per line, each of which must be prefixed with
         * [PROPERTY_LINE_PREFIX], apply them to the supplied [version]s
         * [Version.defaultsIncludingLanguage] and returning the result.
         */
        private fun parseProperties(reader: LineNumberReader, version: Version): FileFormat {
            val builder = Builder(version.defaults)
            do {
                reader.mark(BUFFER_SIZE)
                val line = reader.readLine() ?: break
                if (line.startsWith("package ")) {
                    reader.reset()
                    break
                }

                // If the line does not start with "// - " then it is not a property so assume the
                // header is ended.
                val remainder = line.removePrefix(PROPERTY_LINE_PREFIX)
                if (remainder == line) {
                    reader.reset()
                    break
                }

                parsePropertyAssignment(builder, remainder)
            } while (true)

            return builder.build()
        }

        /**
         * Parse the supplied set of defaults and construct a [FileFormat].
         *
         * @param defaults comma separated list of property assignments that
         */
        fun parseDefaults(defaults: String): FileFormat {
            val builder = Builder(V2)
            defaults.trim().split(",").forEach {
                parsePropertyAssignment(
                    builder,
                    it,
                    { it.defaultable },
                )
            }
            return builder.build()
        }

        /**
         * Get the names of the [CustomizableProperty] that are [CustomizableProperty.defaultable].
         */
        fun defaultableProperties(): List<String> {
            return CustomizableProperty.values()
                .filter { it.defaultable }
                .map { it.propertyName }
                .sorted()
                .toList()
        }
    }

    /** A builder for [FileFormat] that applies some optional values to a base [FileFormat]. */
    internal class Builder(private val base: FileFormat) {
        var addAdditionalOverrides: Boolean? = null
        var conciseDefaultValues: Boolean? = null
        var includeTypeUseAnnotations: Boolean? = null
        var kotlinNameTypeOrder: Boolean? = null
        var kotlinStyleNulls: Boolean? = null
        var language: Language? = null
        var migrating: String? = null
        var name: String? = null
        var overloadedMethodOrder: OverloadedMethodOrder? = null
        var sortWholeExtendsList: Boolean? = null
        var surface: String? = null

        fun build(): FileFormat {
            // Apply any language defaults first as they take priority over version defaults.
            language?.applyLanguageDefaults(this)
            return base.copy(
                conciseDefaultValues = conciseDefaultValues ?: base.conciseDefaultValues,
                includeTypeUseAnnotations = includeTypeUseAnnotations
                        ?: base.includeTypeUseAnnotations,
                kotlinNameTypeOrder = kotlinNameTypeOrder ?: base.kotlinNameTypeOrder,
                kotlinStyleNulls = kotlinStyleNulls ?: base.kotlinStyleNulls,
                language = language ?: base.language,
                migrating = migrating ?: base.migrating,
                name = name ?: base.name,
                specifiedAddAdditionalOverrides = addAdditionalOverrides
                        ?: base.specifiedAddAdditionalOverrides,
                specifiedOverloadedMethodOrder = overloadedMethodOrder
                        ?: base.specifiedOverloadedMethodOrder,
                specifiedSortWholeExtendsList = sortWholeExtendsList
                        ?: base.specifiedSortWholeExtendsList,
                surface = surface ?: base.surface,
            )
        }
    }

    /** Information about the different customizable properties in [FileFormat]. */
    private enum class CustomizableProperty(val defaultable: Boolean = false) {
        // The order of values in this is significant as it determines the order of the properties
        // in signature headers. The values in this block are not in alphabetical order because it
        // is important that they are at the start of the signature header.

        NAME {
            override fun setFromString(builder: Builder, value: String) {
                builder.name = value
            }

            override fun stringFromFormat(format: FileFormat): String? = format.name
        },
        SURFACE {
            override fun setFromString(builder: Builder, value: String) {
                builder.surface = value
            }

            override fun stringFromFormat(format: FileFormat): String? = format.surface
        },

        /** language=[java|kotlin] */
        LANGUAGE {
            override fun setFromString(builder: Builder, value: String) {
                builder.language = enumFromString<Language>(value)
            }

            override fun stringFromFormat(format: FileFormat): String? =
                format.language?.stringFromEnum()
        },

        // The following values must be in alphabetical order.

        /** add-additional-overrides=[yes|no] */
        ADD_ADDITIONAL_OVERRIDES(defaultable = true) {
            override fun setFromString(builder: Builder, value: String) {
                builder.addAdditionalOverrides = yesNo(value)
            }

            override fun stringFromFormat(format: FileFormat): String? =
                format.specifiedAddAdditionalOverrides?.let { yesNo(it) }
        },
        /** concise-default-values=[yes|no] */
        CONCISE_DEFAULT_VALUES {
            override fun setFromString(builder: Builder, value: String) {
                builder.conciseDefaultValues = yesNo(value)
            }

            override fun stringFromFormat(format: FileFormat): String =
                yesNo(format.conciseDefaultValues)
        },
        /** include-type-use-annotations=[yes|no] */
        INCLUDE_TYPE_USE_ANNOTATIONS {
            override fun setFromString(builder: Builder, value: String) {
                builder.includeTypeUseAnnotations = yesNo(value)
            }

            override fun stringFromFormat(format: FileFormat): String =
                yesNo(format.includeTypeUseAnnotations)
        },
        /** kotlin-name-type-order=[yes|no] */
        KOTLIN_NAME_TYPE_ORDER {
            override fun setFromString(builder: Builder, value: String) {
                builder.kotlinNameTypeOrder = yesNo(value)
            }

            override fun stringFromFormat(format: FileFormat): String =
                yesNo(format.kotlinNameTypeOrder)
        },
        /** kotlin-style-nulls=[yes|no] */
        KOTLIN_STYLE_NULLS {
            override fun setFromString(builder: Builder, value: String) {
                builder.kotlinStyleNulls = yesNo(value)
            }

            override fun stringFromFormat(format: FileFormat): String =
                yesNo(format.kotlinStyleNulls)
        },
        MIGRATING {
            override fun setFromString(builder: Builder, value: String) {
                builder.migrating = value
            }

            override fun stringFromFormat(format: FileFormat): String? = format.migrating
        },
        /** overloaded-method-other=[source|signature] */
        OVERLOADED_METHOD_ORDER(defaultable = true) {
            override fun setFromString(builder: Builder, value: String) {
                builder.overloadedMethodOrder = enumFromString<OverloadedMethodOrder>(value)
            }

            override fun stringFromFormat(format: FileFormat): String? =
                format.specifiedOverloadedMethodOrder?.stringFromEnum()
        },
        SORT_WHOLE_EXTENDS_LIST(defaultable = true) {
            override fun setFromString(builder: Builder, value: String) {
                builder.sortWholeExtendsList = yesNo(value)
            }

            override fun stringFromFormat(format: FileFormat): String? =
                format.specifiedSortWholeExtendsList?.let { yesNo(it) }
        };

        /** The property name in the [parseSpecifier] input. */
        val propertyName: String = name.lowercase(Locale.US).replace("_", "-")

        /**
         * Set the corresponding property in the supplied [Builder] to the value corresponding to
         * the string representation [value].
         */
        abstract fun setFromString(builder: Builder, value: String)

        /**
         * Get the string representation of the corresponding property from the supplied
         * [FileFormat].
         */
        abstract fun stringFromFormat(format: FileFormat): String?

        /** Inline function to map from a string value to an enum value of the required type. */
        inline fun <reified T : Enum<T>> enumFromString(value: String): T {
            val enumValues = enumValues<T>()
            return nonInlineEnumFromString(enumValues, value)
        }

        /**
         * Non-inline portion of the function to map from a string value to an enum value of the
         * required type.
         */
        fun <T : Enum<T>> nonInlineEnumFromString(enumValues: Array<T>, value: String): T {
            return enumValues.firstOrNull { it.stringFromEnum() == value }
                ?: let {
                    val possibilities = enumValues.possibilitiesList { "'${it.stringFromEnum()}'" }
                    throw ApiParseException(
                        "unexpected value for $propertyName, found '$value', expected one of $possibilities"
                    )
                }
        }

        /**
         * Extension function to convert an enum value to an external string.
         *
         * It simply returns the lowercase version of the enum name with `_` replaced with `-`.
         */
        fun <T : Enum<T>> T.stringFromEnum(): String {
            return name.lowercase(Locale.US).replace("_", "-")
        }

        /**
         * Intermediate enum used to map from string to [Boolean]
         *
         * The instances are not used directly but are used via [YesNo.values].
         */
        enum class YesNo(val b: Boolean) {
            @Suppress("UNUSED") YES(true),
            @Suppress("UNUSED") NO(false)
        }

        /** Convert a "yes|no" string into a boolean. */
        fun yesNo(value: String): Boolean {
            return enumFromString<YesNo>(value).b
        }

        /** Convert a boolean into a `yes|no` string. */
        fun yesNo(value: Boolean): String = if (value) "yes" else "no"

        companion object {
            val byPropertyName = values().associateBy { it.propertyName }

            /**
             * Get the [CustomizableProperty] by name, throwing an [ApiParseException] if it could
             * not be found.
             *
             * @param name the name of the property.
             * @param propertyFilter optional filter that determines the set of allowable
             *   properties.
             */
            fun getByName(
                name: String,
                propertyFilter: (CustomizableProperty) -> Boolean,
            ): CustomizableProperty =
                byPropertyName[name]?.let { if (propertyFilter(it)) it else null }
                    ?: let {
                        val possibilities =
                            byPropertyName
                                .filter { (_, property) -> propertyFilter(property) }
                                .keys
                                .sorted()
                                .joinToString("', '")
                        throw ApiParseException(
                            "unknown format property name `$name`, expected one of '$possibilities'"
                        )
                    }
        }
    }
}

/**
 * Given an array of items return a list of possibilities.
 *
 * The last pair of items are separated by " or ", the other pairs are separated by ", ".
 */
fun <T> Array<T>.possibilitiesList(transform: (T) -> String): String {
    val allButLast = dropLast(1)
    val last = last()
    val options = buildString {
        allButLast.joinTo(this, transform = transform)
        append(" or ")
        append(transform(last))
    }
    return options
}
