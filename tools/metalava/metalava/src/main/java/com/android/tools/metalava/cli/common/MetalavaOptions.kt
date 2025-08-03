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

package com.android.tools.metalava.cli.common

import com.github.ajalt.clikt.completion.CompletionCandidates
import com.github.ajalt.clikt.core.GroupableOption
import com.github.ajalt.clikt.core.ParameterHolder
import com.github.ajalt.clikt.output.HelpFormatter
import com.github.ajalt.clikt.output.HelpFormatter.ParameterHelp.Option
import com.github.ajalt.clikt.parameters.arguments.ProcessedArgument
import com.github.ajalt.clikt.parameters.arguments.RawArgument
import com.github.ajalt.clikt.parameters.arguments.convert
import com.github.ajalt.clikt.parameters.options.NullableOption
import com.github.ajalt.clikt.parameters.options.OptionCallTransformContext
import com.github.ajalt.clikt.parameters.options.OptionDelegate
import com.github.ajalt.clikt.parameters.options.OptionWithValues
import com.github.ajalt.clikt.parameters.options.RawOption
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import java.io.File
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

// This contains extensions methods for creating custom Clikt options.

/** Convert the option to a [File] that represents an existing file. */
fun RawOption.existingFile(): NullableOption<File, File> {
    return fileConversion(::stringToExistingFile)
}

/** Convert the argument to a [File] that represents an existing file. */
fun RawArgument.existingFile(): ProcessedArgument<File, File> {
    return fileConversion(::stringToExistingFile)
}

/** Convert the argument to a [File] that represents an existing directory. */
fun RawArgument.existingDir(): ProcessedArgument<File, File> {
    return fileConversion(::stringToExistingDir)
}

/** Convert the option to a [File] that represents a new file. */
fun RawOption.newFile(): NullableOption<File, File> {
    return fileConversion(::stringToNewFile)
}

/** Convert the option to a [File] that represents a new directory. */
fun RawOption.newDir(): NullableOption<File, File> {
    return fileConversion(::stringToNewDir)
}

/** Convert the argument to a [File] that represents a new file. */
fun RawArgument.newFile(): ProcessedArgument<File, File> {
    return fileConversion(::stringToNewFile)
}

/** Convert the argument to a [File] that represents a new directory. */
fun RawArgument.newDir(): ProcessedArgument<File, File> {
    return fileConversion(::stringToNewDir)
}

/** Convert the option to a [File] using the supplied conversion function.. */
private fun RawOption.fileConversion(conversion: (String) -> File): NullableOption<File, File> {
    return convert({ localization.pathMetavar() }, CompletionCandidates.Path) { str ->
        try {
            conversion(str)
        } catch (e: MetalavaCliException) {
            e.message?.let { fail(it) } ?: throw e
        }
    }
}

/** Convert the argument to a [File] using the supplied conversion function. */
fun RawArgument.fileConversion(conversion: (String) -> File): ProcessedArgument<File, File> {
    return convert(CompletionCandidates.Path) { str ->
        try {
            conversion(str)
        } catch (e: MetalavaCliException) {
            e.message?.let { fail(it) } ?: throw e
        }
    }
}

/**
 * Converts a path to a [File] that represents the absolute path, with the following special
 * behavior:
 * - "~" will be expanded into the home directory path.
 * - If the given path starts with "@", it'll be converted into "@" + [file's absolute path]
 */
internal fun fileForPathInner(path: String): File {
    // java.io.File doesn't automatically handle ~/ -> home directory expansion.
    // This isn't necessary when metalava is run via the command line driver
    // (since shells will perform this expansion) but when metalava is run
    // directly, not from a shell.
    if (path.startsWith("~/")) {
        val home = System.getProperty("user.home") ?: return File(path)
        return File(home + path.substring(1))
    } else if (path.startsWith("@")) {
        return File("@" + File(path.substring(1)).absolutePath)
    }

    return File(path).absoluteFile
}

/**
 * Convert a string representing an existing directory to a [File].
 *
 * This will fail if:
 * * The file is not a regular directory.
 */
internal fun stringToExistingDir(value: String): File {
    val file = fileForPathInner(value)
    if (!file.isDirectory) {
        throw MetalavaCliException("$file is not a directory")
    }
    return file
}

/**
 * Convert a string representing a new directory to a [File].
 *
 * This will fail if:
 * * the directory exists and cannot be deleted.
 * * the directory cannot be created.
 */
internal fun stringToNewDir(value: String): File {
    val output = fileForPathInner(value)
    val ok =
        if (output.exists()) {
            if (output.isDirectory) {
                output.deleteRecursively()
            }
            if (output.exists()) {
                true
            } else {
                output.mkdir()
            }
        } else {
            output.mkdirs()
        }
    if (!ok) {
        throw MetalavaCliException("Could not create $output")
    }

    return output
}

/**
 * Convert a string representing an existing file to a [File].
 *
 * This will fail if:
 * * The file is not a regular file.
 */
internal fun stringToExistingFile(value: String): File {
    val file = fileForPathInner(value)
    if (!file.isFile) {
        throw MetalavaCliException("$file is not a file")
    }
    return file
}

/**
 * Convert a string representing a new file to a [File].
 *
 * This will fail if:
 * * the file is a directory.
 * * the file exists and cannot be deleted.
 * * the parent directory does not exist, and cannot be created.
 */
internal fun stringToNewFile(value: String): File {
    val output = fileForPathInner(value)

    if (output.exists()) {
        if (output.isDirectory) {
            throw MetalavaCliException("$output is a directory")
        }
        val deleted = output.delete()
        if (!deleted) {
            throw MetalavaCliException("Could not delete previous version of $output")
        }
    } else if (output.parentFile != null && !output.parentFile.exists()) {
        val ok = output.parentFile.mkdirs()
        if (!ok) {
            throw MetalavaCliException("Could not create ${output.parentFile}")
        }
    }

    return output
}

// Unicode Next Line (NEL) character which forces Clikt to insert a new line instead of just
// collapsing the `\n` into adjacent spaces. Acts like an HTML <br/>.
const val HARD_NEWLINE = "\u0085"

/**
 * Create a property delegate for an enum.
 *
 * This will generate help text that:
 * * uses lower case version of the enum value name (with `_` replaced with `-`) as the value to
 *   supply on the command line.
 * * formats the help for each enum value in its own block separated from surrounding blocks by
 *   blank lines.
 * * will tag the default enum value in the help.
 *
 * @param names the possible names for the option that can be used on the command line.
 * @param help the help for the option, does not need to include information about the default or
 *   the individual options as they will be added automatically.
 * @param enumValueHelpGetter given an enum value return the help for it.
 * @param key given an enum value return the value that must be specified on the command line. This
 *   is used to create a bidirectional mapping so that command line option can be mapped to the enum
 *   value and the default enum value mapped back to the default command line option. Defaults to
 *   using the lowercase version of the name with `_` replaced with `-`.
 * @param default the default value, must be provided to ensure correct type inference.
 */
internal inline fun <reified T : Enum<T>> ParameterHolder.enumOption(
    vararg names: String,
    help: String,
    noinline enumValueHelpGetter: (T) -> String,
    noinline key: (T) -> String = { it.name.lowercase().replace("_", "-") },
    default: T,
): OptionWithValues<T, T, T> {
    // Create a choice mapping from option to enum value using the `key` function.
    val enumValues = enumValues<T>()
    return nonInlineEnumOption(names, enumValues, help, enumValueHelpGetter, key, default)
}

/**
 * Extract the majority of the work into a non-inline function to avoid it creating too much bloat
 * in the call sites.
 */
internal fun <T : Enum<T>> ParameterHolder.nonInlineEnumOption(
    names: Array<out String>,
    enumValues: Array<T>,
    help: String,
    enumValueHelpGetter: (T) -> String,
    key: (T) -> String,
    default: T
): OptionWithValues<T, T, T> {
    // Filter out any enum values that do not provide any help.
    val optionToValue = enumValues.filter { enumValueHelpGetter(it) != "" }.associateBy { key(it) }

    // Get the help representation of the default value.
    val defaultForHelp = key(default)

    val constructedHelp = buildString {
        append(help)
        append(HARD_NEWLINE)
        for (enumValue in optionToValue.values) {
            val value = key(enumValue)
            // This must match the pattern used in MetalavaHelpFormatter.styleEnumHelpTextIfNeeded
            // which is used to deconstruct this.
            append(constructStyleableChoiceOption(value))
            append(" - ")
            append(enumValueHelpGetter(enumValue))
            append(HARD_NEWLINE)
        }
    }

    return option(names = names, help = constructedHelp)
        .choice(optionToValue)
        .default(default, defaultForHelp = defaultForHelp)
}

/**
 * Construct a styleable choice option.
 *
 * This prefixes and suffixes the choice option with `**` (like Markdown) so that they can be found
 * in the help text using [deconstructStyleableChoiceOption] and replaced with actual styling
 * sequences if needed.
 */
private fun constructStyleableChoiceOption(value: String) = "$HARD_NEWLINE**$value**"

/**
 * A regular expression that will match choice options created using
 * [constructStyleableChoiceOption].
 */
private val deconstructStyleableChoiceOption = """$HARD_NEWLINE\*\*([^*]+)\*\*""".toRegex()

/**
 * Replace the choice option (i.e. the value passed to [constructStyleableChoiceOption]) with the
 * result of calling the [transformer] on it.
 *
 * This must only be called on a [MatchResult] found using the [deconstructStyleableChoiceOption]
 * regular expression.
 */
private fun MatchResult.replaceChoiceOption(
    builder: StringBuilder,
    transformer: (String) -> String
) {
    val group = groups[1] ?: throw IllegalStateException("group 1 not found in $this")
    val choiceOption = group.value
    val replacementText = transformer(choiceOption)
    // Replace the choice option and the surrounding style markers but not the leading NEL.
    builder.replace(range.first + 1, range.last + 1, replacementText)
}

/**
 * Scan [help] using [deconstructStyleableChoiceOption] for enum value help created using
 * [constructStyleableChoiceOption] and if it was found then style it using the [terminal].
 *
 * If an enum value is found that matches the value of the [HelpFormatter.Tags.DEFAULT] tag in
 * [tags] then annotate is as the default and remove the tag, so it is not added by the default help
 * formatter.
 */
internal fun styleEnumHelpTextIfNeeded(
    help: String,
    tags: MutableMap<String, String>,
    terminal: Terminal
): String {
    val defaultForHelp = tags[HelpFormatter.Tags.DEFAULT]

    // Find all styleable choice options in the help text. If there are none then just return
    // and use the default rendering.
    val matchResults = deconstructStyleableChoiceOption.findAll(help).toList()
    if (matchResults.isEmpty()) {
        return help
    }

    val styledHelp = buildString {
        append(help)

        // Iterate over the matches in reverse order replacing any styleable choice options
        // with styled versions.
        for (matchResult in matchResults.reversed()) {
            matchResult.replaceChoiceOption(this) { optionValue ->
                val styledOptionValue = terminal.bold(optionValue)
                if (optionValue == defaultForHelp) {
                    // Remove the default value from the tags so it is not included in the help.
                    tags.remove(HelpFormatter.Tags.DEFAULT)

                    "$styledOptionValue (default)"
                } else {
                    styledOptionValue
                }
            }
        }
    }

    return styledHelp
}

/**
 * Extension method that allows a transformation to be provided to a Clikt option that will be
 * applied after Clikt has processed, transformed (including applying defaults) and validated the
 * value, but before it is returned.
 */
fun <I, O> OptionDelegate<I>.map(transform: (I) -> O): OptionDelegate<O> {
    return PostTransformDelegate(this, transform)
}

/**
 * An [OptionDelegate] that delegates to another [OptionDelegate] and applies a transformation to
 * the value it returns.
 */
private class PostTransformDelegate<I, O>(
    val delegate: OptionDelegate<I>,
    val transform: (I) -> O,
) : OptionDelegate<O>, GroupableOption by delegate {

    override val value: O
        get() = transform(delegate.value)

    override fun provideDelegate(
        thisRef: ParameterHolder,
        prop: KProperty<*>
    ): ReadOnlyProperty<ParameterHolder, O> {
        // Make sure that the wrapped option has registered itself properly.
        val providedDelegate = delegate.provideDelegate(thisRef, prop)
        check(providedDelegate == delegate) {
            "expected $delegate to return itself but it returned $providedDelegate"
        }

        // This is the delegate.
        return this
    }
}

/** A block that performs a side effect when provide a value */
typealias SideEffectAction = OptionCallTransformContext.(String) -> Unit

/** An option that simply performs a [SideEffectAction] */
typealias SideEffectOption = OptionWithValues<Unit, Unit, Unit>

/** Get the [SideEffectAction] (which is stored in [OptionWithValues.transformValue]). */
val SideEffectOption.action: SideEffectAction
    get() = transformValue

/**
 * Create a special option that performs a side effect.
 *
 * @param names names of the option.
 * @param help the help for the option.
 * @param action the action to perform, is passed the value associated with the option and is run
 *   within a [OptionCallTransformContext] context.
 */
fun ParameterHolder.sideEffectOption(
    vararg names: String,
    help: String,
    action: SideEffectAction,
): SideEffectOption {
    return option(names = names, help = help)
        .copy(
            // Perform the side effect when transforming the value.
            transformValue = { this.action(it) },
            transformEach = {},
            transformAll = {},
            validator = {}
        )
}

/**
 * Create a composite side effect option.
 *
 * This option will allow the individual options to be interleaved together and will ensure that the
 * side effects are applied in the order they appear on the command line. Adding the options
 * individually would cause them to be separated into groups and each group processed in order which
 * would mean the side effects were applied in a different order.
 *
 * The resulting option will still be displayed as multiple separate options in the help.
 */
fun ParameterHolder.compositeSideEffectOption(
    options: List<SideEffectOption>,
): OptionDelegate<Unit> {
    val optionByName =
        options
            .asSequence()
            .flatMap { option -> option.names.map { it to option }.asSequence() }
            .toMap()
    val names = optionByName.keys.toTypedArray()
    val help = constructCompositeOptionHelp(optionByName.values.map { it.optionHelp })
    return sideEffectOption(
        names = names,
        help = help,
        action = {
            val option = optionByName[name]!!
            val action = option.action
            this.action(it)
        }
    )
}

/**
 * A marker string that if present at the start of an options help will cause that option to be
 * split into separate options, one for each name in the [Option.names].
 *
 * See [constructCompositeOptionHelp]
 */
private const val COMPOSITE_OPTION = "\$COMPOSITE-OPTION\$\n"

/** Separator of help for each item in the string returned by [constructCompositeOptionHelp]. */
private const val COMPOSITE_SEPARATOR = "\n\$COMPOSITE-SEPARATOR\$\n"

/**
 * Construct the help for a composite option, which is an option that has multiple names and is
 * treated like a single option for the purposes of parsing but which needs to be displayed as a
 * number of separate options.
 *
 * @param individualOptionHelp must have an entry for every name in an option's set of names and it
 *   must be in the same order as that set.
 */
private fun constructCompositeOptionHelp(individualOptionHelp: List<String>) =
    "$COMPOSITE_OPTION${individualOptionHelp.joinToString(COMPOSITE_SEPARATOR)}"

/**
 * Checks to see if an [Option] is actually a composite option which needs splitting into separate
 * options for help formatting.
 */
internal fun Option.isCompositeOption(): Boolean = help.startsWith(COMPOSITE_OPTION)

/**
 * Deconstructs the help created by [constructCompositeOptionHelp] checking to make sure that there
 * is one item for every [Option.names].
 */
internal fun Option.deconstructCompositeHelp(): List<String> {
    val lines = help.removePrefix(COMPOSITE_OPTION).split(COMPOSITE_SEPARATOR)
    if (lines.size != names.size) {
        throw IllegalStateException(
            "Expected ${names.size} blocks of help but found ${lines.size} in ${help}"
        )
    }
    return lines
}

/** Decompose the [Option] into multiple separate options. */
internal fun Option.decompose(): Sequence<Option> {
    val lines = deconstructCompositeHelp()
    return names.asSequence().mapIndexed { i, name ->
        val metavar = if (name.endsWith("-category")) "<name>" else "<id>"
        val help = lines[i]
        copy(names = setOf(name), metavar = metavar, help = help)
    }
}

/**
 * Clikt does not allow `:` in option names but Metalava uses that for creating structured option
 * names, e.g. --part1:part2:part3.
 *
 * This method can be used to circumvent the built-in check and use a custom check that allows for
 * structure option names. Call it at the end of the `option(...)....allowStructureOptionName()`
 * call chain.
 */
fun <T> OptionWithValues<T, *, *>.allowStructuredOptionName(): OptionDelegate<T> {
    return StructuredOptionName(this)
}

/** Allows the same format for option names as Clikt with the addition of the ':' character. */
private fun checkStructuredOptionNames(names: Set<String>) {
    val invalidName = names.find { !it.matches(Regex("""[\-@/+]{1,2}[\w\-_:]+""")) }
    require(invalidName == null) { "Invalid option name \"$invalidName\"" }
}

/** Circumvents the usual Clikt name format check and substitutes its own name format check. */
class StructuredOptionName<T>(private val delegate: OptionDelegate<T>) :
    OptionDelegate<T> by delegate {

    override fun provideDelegate(
        thisRef: ParameterHolder,
        prop: KProperty<*>
    ): ReadOnlyProperty<ParameterHolder, T> {
        // If no names are provided then delegate this to the built-in method to infer the option
        // name as that name is guaranteed not to contain a ':'.
        if (names.isEmpty()) {
            return delegate.provideDelegate(thisRef, prop)
        }
        require(secondaryNames.isEmpty()) {
            "Secondary option names are only allowed on flag options."
        }
        checkStructuredOptionNames(names)
        thisRef.registerOption(delegate)
        return this
    }
}
