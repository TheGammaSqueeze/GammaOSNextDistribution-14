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

package com.android.tools.metalava.model.testing

import com.android.tools.metalava.model.ModelOptions
import com.android.tools.metalava.model.junit4.CustomizableParameterizedRunner
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.provider.FilterableCodebaseCreator
import com.android.tools.metalava.model.provider.InputFormat
import com.android.tools.metalava.model.testing.BaseModelProviderRunner.InstanceRunner
import com.android.tools.metalava.model.testing.BaseModelProviderRunner.InstanceRunnerFactory
import com.android.tools.metalava.model.testing.BaseModelProviderRunner.ModelProviderWrapper
import com.android.tools.metalava.testing.BaselineTestRule
import java.lang.reflect.AnnotatedElement
import java.util.Locale
import org.junit.runner.Runner
import org.junit.runners.Parameterized
import org.junit.runners.model.FrameworkMethod
import org.junit.runners.model.Statement
import org.junit.runners.model.TestClass
import org.junit.runners.parameterized.BlockJUnit4ClassRunnerWithParameters
import org.junit.runners.parameterized.ParametersRunnerFactory
import org.junit.runners.parameterized.TestWithParameters

/**
 * Base class for JUnit [Runner]s that need to run tests across a number of different codebase
 * creators.
 *
 * The basic approach is:
 * 1. Invoke the `codebaseCreatorConfigsGetter` lambda to get a list of [CodebaseCreatorConfig]s of
 *    type [C]. The type of the codebase creator objects can vary across different runners, hence
 *    why it is specified as a type parameter.
 * 2. Wrap [CodebaseCreatorConfig] in a [ModelProviderWrapper] to tunnel information needed through
 *    to [InstanceRunner].
 * 3. Generate the cross product of the [ModelProviderWrapper]s with any additional test arguments
 *    provided by the test class. If no test arguments are provided then just return the
 *    [ModelProviderWrapper]s directly. Either way the returned [TestArguments] object will contain
 *    an appropriate pattern for the number of arguments in each argument set.
 * 4. The [Parameterized.RunnersFactory] will take the list of test arguments returned and then use
 *    them to construct a set of [TestWithParameters] objects, each of which is passed to a
 *    [ParametersRunnerFactory] to create the [Runner] for the test.
 * 5. The [ParametersRunnerFactory] is instantiated by [Parameterized.RunnersFactory] directly from
 *    a class (in this case [InstanceRunnerFactory]) so there is no way for this to pass information
 *    into the [InstanceRunnerFactory]. So, instead it relies on the information to be passed
 *    through the [TestWithParameters] object that is passed to
 *    [ParametersRunnerFactory.createRunnerForTestWithParameters].
 * 6. The [InstanceRunnerFactory] extracts the [ModelProviderWrapper] from the [TestWithParameters]
 *    it is given and passes it in alongside the remaining arguments to [InstanceRunner].
 * 7. The [InstanceRunner] injects the [ModelProviderWrapper.codebaseCreatorConfig] into the test
 *    class along with any additional parameters and then runs the test as normal.
 *
 * @param C the type of the codebase creator object.
 * @param I the type of the injectable class through which the codebase creator will be injected
 *   into the test class.
 * @param clazz the test class to be run, must be assignable to `injectableClass`.
 * @param codebaseCreatorConfigsGetter a lambda for getting the [CodebaseCreatorConfig]s.
 * @param baselineResourcePath the resource path to the baseline file that should be consulted for
 *   known errors to ignore / check.
 * @param minimumCapabilities the minimum set of capabilities the codebase created must provide in
 *   order to be used by this runner.
 */
open class BaseModelProviderRunner<C : FilterableCodebaseCreator, I : Any>(
    clazz: Class<*>,
    codebaseCreatorConfigsGetter: (TestClass) -> List<CodebaseCreatorConfig<C>>,
    baselineResourcePath: String,
    minimumCapabilities: Set<Capability> = emptySet(),
) :
    CustomizableParameterizedRunner(
        clazz,
        { testClass, additionalArguments ->
            createTestArguments(
                testClass,
                codebaseCreatorConfigsGetter,
                baselineResourcePath,
                additionalArguments,
                minimumCapabilities,
            )
        },
        InstanceRunnerFactory::class,
    ) {

    init {
        val injectableClass = CodebaseCreatorConfigAware::class.java
        if (!injectableClass.isAssignableFrom(clazz)) {
            error("Class ${clazz.name} does not implement ${injectableClass.name}")
        }
    }

    /**
     * A wrapper around a [CodebaseCreatorConfig] that tunnels information needed by
     * [InstanceRunnerFactory] through [TestWithParameters].
     */
    private class ModelProviderWrapper<C : FilterableCodebaseCreator>(
        val codebaseCreatorConfig: CodebaseCreatorConfig<C>,
        val baselineResourcePath: String,
        val additionalArgumentSet: List<Any> = emptyList(),
    ) {
        fun withAdditionalArgumentSet(argumentSet: List<Any>) =
            ModelProviderWrapper(codebaseCreatorConfig, baselineResourcePath, argumentSet)

        fun injectModelProviderInto(testInstance: Any) {
            @Suppress("UNCHECKED_CAST")
            val injectableTestInstance = testInstance as CodebaseCreatorConfigAware<C>
            injectableTestInstance.codebaseCreatorConfig = codebaseCreatorConfig
        }

        /**
         * Get the string representation which will end up inside `[]` in [TestWithParameters.name].
         */
        override fun toString() =
            if (additionalArgumentSet.isEmpty()) codebaseCreatorConfig.toString()
            else {
                buildString {
                    append(codebaseCreatorConfig.toString())
                    if (isNotEmpty()) {
                        append(",")
                    }
                    additionalArgumentSet.joinTo(this, separator = ",")
                }
            }
    }

    /** [ParametersRunnerFactory] for creating [Runner]s for a set of arguments. */
    class InstanceRunnerFactory : ParametersRunnerFactory {
        /**
         * Create a runner for the [TestWithParameters].
         *
         * The [TestWithParameters.parameters] contains at least one argument and the first argument
         * will be the [ModelProviderWrapper] provided by [createTestArguments]. This extracts that
         * from the list and passes them to [InstanceRunner] to inject them into the test class.
         */
        override fun createRunnerForTestWithParameters(test: TestWithParameters): Runner {
            val arguments = test.parameters

            // Get the [ModelProviderWrapper] from the arguments.
            val modelProviderWrapper = arguments[0] as ModelProviderWrapper<*>

            // Get any additional arguments from the wrapper.
            val additionalArguments = modelProviderWrapper.additionalArgumentSet

            // If the suffix to add to the end of the test name is empty then replace it with an
            // empty string. This will cause [InstanceRunner] to avoid adding a suffix to the end of
            // the test so that it can be run directly from the IDE.
            val suffix = test.name.takeIf { it != "[]" } ?: ""

            // Create a new set of [TestWithParameters] containing any additional arguments, which
            // may be an empty set. Keep the name as is as that will describe the codebase creator
            // as well as the other arguments.
            val newTest = TestWithParameters(suffix, test.testClass, additionalArguments)

            // Create a new [InstanceRunner] that will inject the codebase creator into the test
            // class
            // when created.
            return InstanceRunner(modelProviderWrapper, newTest)
        }
    }

    /**
     * Runner for a test that must implement [I].
     *
     * This will use the [modelProviderWrapper] to inject the codebase creator object into the test
     * class after creation.
     */
    private class InstanceRunner(
        private val modelProviderWrapper: ModelProviderWrapper<*>,
        test: TestWithParameters
    ) : BlockJUnit4ClassRunnerWithParameters(test) {

        /** The suffix to add at the end of the test name. */
        private val testSuffix = test.name

        /**
         * The runner name.
         *
         * If [testSuffix] is empty then this will be "[]", otherwise it will be the test suffix.
         * The "[]" is used because an empty string is not allowed. The name used here has no effect
         * on the [org.junit.runner.Description] objects generated or the running of the tests but
         * is visible through the [Runner] hierarchy and so can affect test runner code in Gradle
         * and IDEs. Using something similar to the standard pattern used by the [Parameterized]
         * runner minimizes the risk that it will cause issues with that code.
         */
        private val runnerName = testSuffix.takeIf { it != "" } ?: "[]"

        override fun createTest(): Any {
            val testInstance = super.createTest()
            modelProviderWrapper.injectModelProviderInto(testInstance)
            return testInstance
        }

        override fun getName(): String {
            return runnerName
        }

        override fun testName(method: FrameworkMethod): String {
            return method.name + testSuffix
        }

        /**
         * Override [methodInvoker] to allow the [Statement] it returns to be wrapped by a
         * [BaselineTestRule] to take into account known issues listed in a baseline file.
         */
        override fun methodInvoker(method: FrameworkMethod, test: Any): Statement {
            val statement = super.methodInvoker(method, test)
            val baselineTestRule =
                BaselineTestRule(
                    modelProviderWrapper.codebaseCreatorConfig.toString(),
                    modelProviderWrapper.baselineResourcePath,
                )
            return baselineTestRule.apply(statement, describeChild(method))
        }

        override fun getChildren(): List<FrameworkMethod> {
            return super.getChildren().filter { frameworkMethod ->
                // Create a predicate from any annotations on the methods.
                val predicate = createCreatorPredicate(sequenceOf(frameworkMethod.method))

                // Apply the predicate to the [CodebaseCreatorConfig] that would be used for this
                // method.
                predicate(modelProviderWrapper.codebaseCreatorConfig)
            }
        }
    }

    companion object {
        private fun <C : FilterableCodebaseCreator> createTestArguments(
            testClass: TestClass,
            codebaseCreatorConfigsGetter: (TestClass) -> List<CodebaseCreatorConfig<C>>,
            baselineResourcePath: String,
            additionalArguments: List<Array<Any>>?,
            minimumCapabilities: Set<Capability>,
        ): TestArguments {
            // Generate a sequence that traverse the super class hierarchy starting with the test
            // class.
            val hierarchy = generateSequence(testClass.javaClass) { it.superclass }

            val predicate =
                // Create a predicate from annotations on the test class and its ancestors.
                createCreatorPredicate(hierarchy)
                    // AND that with a predicate to check for minimum capabilities.
                    .and(createCapabilitiesPredicate(minimumCapabilities))

            // Get the list of [CodebaseCreatorConfig]s over which this must run the tests.
            val creatorConfigs =
                codebaseCreatorConfigsGetter(testClass)
                    // Filter out any [CodebaseCreatorConfig]s as requested.
                    .filter(predicate)

            // Wrap each codebase creator object with information needed by [InstanceRunnerFactory].
            val wrappers =
                creatorConfigs.map { creatorConfig ->
                    ModelProviderWrapper(creatorConfig, baselineResourcePath)
                }

            return if (additionalArguments == null) {
                // No additional arguments were provided so just return the wrappers.
                TestArguments("{0}", wrappers)
            } else {
                // Convert each argument set from Array<Any> to List<Any>
                val additionalArgumentSetLists = additionalArguments.map { it.toList() }
                // Duplicate every wrapper with each argument set.
                val combined =
                    wrappers.flatMap { wrapper ->
                        additionalArgumentSetLists.map { argumentSet ->
                            wrapper.withAdditionalArgumentSet(argumentSet)
                        }
                    }
                TestArguments("{0}", combined)
            }
        }

        private data class ProviderOptions(val provider: String, val options: String)

        /**
         * Create a [CreatorPredicate] for [CodebaseCreatorConfig]s based on the annotations on the
         * [annotatedElements],
         */
        private fun createCreatorPredicate(annotatedElements: Sequence<AnnotatedElement>) =
            predicateFromFilterByProvider(annotatedElements)
                .and(predicateFromRequiredCapabilities(annotatedElements))

        /** Create a [CreatorPredicate] from [FilterByProvider] annotations. */
        private fun predicateFromFilterByProvider(
            annotatedElements: Sequence<AnnotatedElement>
        ): CreatorPredicate {
            val providerToAction = mutableMapOf<String, FilterAction>()
            val providerOptionsToAction = mutableMapOf<ProviderOptions, FilterAction>()

            // Iterate over the annotated elements
            for (element in annotatedElements) {
                val annotations = element.getAnnotationsByType(FilterByProvider::class.java)
                for (annotation in annotations) {
                    val specifiedOptions = annotation.specifiedOptions
                    if (specifiedOptions == null) {
                        providerToAction.putIfAbsent(annotation.provider, annotation.action)
                    } else {
                        val key = ProviderOptions(annotation.provider, specifiedOptions)
                        providerOptionsToAction.putIfAbsent(key, annotation.action)
                    }
                }
            }

            // Create a predicate from the [FilterByProvider] annotations.
            return if (providerToAction.isEmpty() && providerOptionsToAction.isEmpty())
                alwaysTruePredicate
            else
                { config ->
                    val providerName = config.providerName
                    val key = ProviderOptions(providerName, config.modelOptions.toString())
                    val action = providerOptionsToAction[key] ?: providerToAction[providerName]
                    action != FilterAction.EXCLUDE
                }
        }

        /** Create a [CreatorPredicate] from [RequiresCapabilities]. */
        private fun predicateFromRequiredCapabilities(
            annotatedElements: Sequence<AnnotatedElement>
        ): CreatorPredicate {
            // Iterate over the annotated elements stopping at the first which is annotated with
            // [RequiresCapabilities] and return the set of [RequiresCapabilities.required]
            // [Capability]s.
            for (element in annotatedElements) {
                val requires = element.getAnnotation(RequiresCapabilities::class.java)
                if (requires != null) {
                    return createCapabilitiesPredicate(requires.required.toSet())
                }
            }

            return alwaysTruePredicate
        }

        /**
         * Create a [CreatorPredicate] to select [CodebaseCreatorConfig]s with the [required]
         * capabilities.
         */
        private fun createCapabilitiesPredicate(required: Set<Capability>): CreatorPredicate =
            if (required.isEmpty()) alwaysTruePredicate
            else { config -> config.creator.capabilities.containsAll(required) }
    }
}

/** Encapsulates the configuration information needed by a codebase creator */
class CodebaseCreatorConfig<C : FilterableCodebaseCreator>(
    /** The creator that will create the codebase. */
    val creator: C,
    /**
     * The optional [InputFormat] of the files from which the codebase will be created. If this is
     * not specified then files of any [InputFormat] supported by the [creator] can be used.
     */
    val inputFormat: InputFormat? = null,

    /** Any additional options passed to the codebase creator. */
    val modelOptions: ModelOptions = ModelOptions.empty,
    includeProviderNameInTestName: Boolean = true,
    includeInputFormatInTestName: Boolean = false,
) {
    val providerName = creator.providerName

    private val toStringValue = buildString {
        var separator = ""
        if (includeProviderNameInTestName) {
            append(creator.providerName)
            separator = ","
        }

        // If the [inputFormat] is specified and required then include it in the test name,
        // otherwise ignore it.
        if (includeInputFormatInTestName && inputFormat != null) {
            append(separator)
            append(inputFormat.name.lowercase(Locale.US))
            separator = ","
        }

        // If the [ModelOptions] is not empty, then include it in the test name, otherwise ignore
        // it.
        if (modelOptions != ModelOptions.empty) {
            append(separator)
            append(modelOptions)
        }
    }

    /** Override this to return the string that will be used in the test name. */
    override fun toString() = toStringValue
}

/** A predicate for use when filtering [CodebaseCreatorConfig]s. */
typealias CreatorPredicate = (CodebaseCreatorConfig<*>) -> Boolean

/** The always `true` predicate. */
private val alwaysTruePredicate: (CodebaseCreatorConfig<*>) -> Boolean = { true }

/** AND this predicate with the [other] predicate. */
fun CreatorPredicate.and(other: CreatorPredicate) =
    if (this == alwaysTruePredicate) other
    else if (other == alwaysTruePredicate) this else { config -> this(config) && other(config) }
