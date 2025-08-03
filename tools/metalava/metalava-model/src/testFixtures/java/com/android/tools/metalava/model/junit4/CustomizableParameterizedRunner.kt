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

package com.android.tools.metalava.model.junit4

import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.InaccessibleObjectException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import org.junit.AssumptionViolatedException
import org.junit.runner.Description
import org.junit.runner.Runner
import org.junit.runner.notification.RunNotifier
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import org.junit.runners.Parameterized.UseParametersRunnerFactory
import org.junit.runners.ParentRunner
import org.junit.runners.model.FrameworkMethod
import org.junit.runners.model.TestClass
import org.junit.runners.parameterized.ParametersRunnerFactory

/**
 * A customizable wrapper around the JUnit [Parameterized] runner.
 *
 * While it is very capable unfortunately, is not very customizable, e.g.
 * * Test arguments can only be retrieved from a function annotated with [Parameters], so there is
 *   no way to provide arguments automatically by the runner.
 * * The function that provides the arguments is not given the test [Class] that is being run (which
 *   may be a subclass of the class with the [Parameters] function). That means the argument
 *   generation cannot take into account information from the test class, e.g. class annotations.
 * * Once provided the arguments cannot be filtered.
 * * A custom [ParametersRunnerFactory] can be provided through the [UseParametersRunnerFactory]
 *   annotation, but it has to be specified on the test class, and cannot be inherited from the
 *   class that specifies the `@RunWith(Parameterized.class)`. There is no way to provide a single
 *   `@RunWith(ExtensionOfParameterized.class)` annotation that hard codes the
 *   [ParametersRunnerFactory].
 *
 * JUnit 4 is no longer under active development so there is no chance of getting these capabilities
 * added to the [Parameterized] runner. The JUnit Params library is a more capable parameterized
 * runner but unfortunately, it is not being actively maintained. JUnit 5 parameterization does not
 * support parameterizing the whole class.
 *
 * So, this class is being provided to rectify those limitations by providing a wrapper around a
 * [Parameterized] instance, using copious quantities of reflection to construct and manipulate it.
 * On top of that wrapper it will provide support for doing some (or all) of the above as needed.
 *
 * @param clazz the test class to run.
 * @param argumentsProvider provider of [TestArguments] used by this runner. Is also passed any
 *   additional parameters (provided by the test class using the standard [Parameterized]
 *   mechanism), if any. They can be filtered and/or combined in some way with parameters provides
 *   by this.
 */
abstract class CustomizableParameterizedRunner(
    clazz: Class<*>,
    argumentsProvider: (TestClass, List<Array<Any>>?) -> TestArguments,
    parametersRunnerFactoryClass: KClass<out ParametersRunnerFactory>? = null,
) : ParentRunner<Runner>(clazz) {

    /** The set of test arguments to use. */
    class TestArguments(
        /**
         * The pattern describing how to construct the test name suffix for a set of arguments.
         *
         * See [Parameters.name] for more details.
         */
        val pattern: String,

        /**
         * The sets of arguments.
         *
         * Each entry can be either an `Array<Any>` (for multiple arguments) or any other value (for
         * a single argument).
         */
        val argumentSets: List<Any>,
    )

    /** The wrapped [Parameterized] class. */
    private val parameterized: Parameterized =
        ParameterizedBuilder.build(clazz) {

            // Create a [TestClass] for the real test class and inject it.
            val testClass =
                InjectedTestClass(clazz, parametersRunnerFactoryClass).also {
                    // Inject it into [runnersFactory]
                    testClass = it
                }

            // Get additional arguments (if any) from the actual test class.
            val additionalArguments = getAdditionalArguments(testClass)

            // Obtain [TestArguments] from the provider and store the list of argument sets in the
            // [RunnersFactory.allParametersField].
            val testArguments = argumentsProvider(testClass, additionalArguments)
            allParameters = testArguments.argumentSets

            // Get the [FrameworkMethod] for the [FakeTestClass.fakeParameters] method, extract its
            // [FrameworkMethod.method], wrap that and the [TestArguments.pattern] in an
            // [InjectedFrameworkMethod] that will intercept a request for [Parameters] annotation
            // and return one containing the pattern supplied.
            val fakeParametersMethod = parametersMethod
            val injectedParametersMethod =
                InjectedFrameworkMethod(fakeParametersMethod.method, testArguments.pattern)
            parametersMethod = injectedParametersMethod

            // Make sure that the [RunnersFactory.parameterCount] field is set correctly to the
            // number of parameters.
            parameterCount =
                if (allParameters.isEmpty()) 0
                else {
                    val first = allParameters.first()
                    (first as? Array<*>)?.size ?: 1
                }
        }

    /** List containing [parameterized]. */
    private val children: List<Runner> = mutableListOf(parameterized)

    companion object {
        /**
         * Get additional arguments, if any, provided by the [testClass] through use of a
         * [Parameters] function.
         *
         * The returned values have been normalized so each entry is an `Array<Any>`.
         */
        private fun getAdditionalArguments(testClass: TestClass): List<Array<Any>>? {
            val parametersMethod =
                testClass.getAnnotatedMethods(Parameters::class.java).firstOrNull {
                    it.isPublic && it.isStatic
                }
                    ?: return null
            return when (val parameters = parametersMethod.invokeExplosively(null)) {
                    is List<*> -> parameters
                    is Iterable<*> -> parameters.toList()
                    is Array<*> -> parameters.toList()
                    else ->
                        error(
                            "${testClass.name}.{${parametersMethod.name}() must return an Iterable of arrays."
                        )
                }
                .filterNotNull()
                .map {
                    if (
                        it is Array<*> &&
                            it.javaClass.isArray &&
                            it.javaClass.componentType == Object::class.java
                    ) {
                        @Suppress("UNCHECKED_CAST")
                        it as Array<Any>
                    } else {
                        arrayOf(it)
                    }
                }
        }
    }

    /**
     * A [TestClass] subclass that is injected into the [Parameterized.RunnersFactory] in order to
     * intercept requests for information about the test class being run and supply information
     * provide by this.
     */
    private class InjectedTestClass(
        clazz: Class<*>,
        /**
         * The [ParametersRunnerFactory] class to use for creating runners for a specific set of
         * test parameters.
         */
        private val runnerFactoryClass: KClass<out ParametersRunnerFactory>?,
    ) : TestClass(clazz) {
        override fun <T : Annotation> getAnnotation(annotationType: Class<T>): T? {
            if (
                runnerFactoryClass != null &&
                    annotationType == UseParametersRunnerFactory::class.java
            ) {
                @Suppress("UNCHECKED_CAST")
                return UseParametersRunnerFactory(runnerFactoryClass) as T
            }
            return super.getAnnotation(annotationType)
        }
    }

    /**
     * An extension of [FrameworkMethod] that exists to provide the custom [TestArguments.pattern]
     * to [Parameterized.RunnersFactory] by intercepting a request for the [Parameters] annotation
     * and returning one with the supplied [pattern].
     */
    private class InjectedFrameworkMethod(method: Method, val pattern: String) :
        FrameworkMethod(method) {
        override fun <T : Annotation> getAnnotation(annotationType: Class<T>): T? {
            if (annotationType == Parameters::class.java) {
                @Suppress("UNCHECKED_CAST") return Parameters(name = pattern) as T
            }
            return super.getAnnotation(annotationType)
        }
    }

    override fun getDescription(): Description {
        // Return the wrapped [parameterized]'s [Description] otherwise the description ends up
        // looking something like this:
        //     <class>
        //         <class>
        //             ...<method>...
        //
        // Which can cause issues with gradle test runner's handling of @Ignore.
        return parameterized.description
    }

    override fun getChildren() = children

    override fun describeChild(child: Runner): Description = child.description

    override fun runChild(child: Runner, notifier: RunNotifier) {
        child.run(notifier)
    }

    /**
     * The main functionality of [Parameterized] is provided by the private class
     * [Parameterized.RunnersFactory]. This class provides an abstract that allows instances of that
     * to be constructed through reflection which is then used to construct a [Parameterized]
     * instance.
     */
    private class ParameterizedBuilder {

        private val runnersFactory =
            runnersFactoryConstructor.newInstance(FakeTestClass::class.java)

        // The following delegate to the corresponding field in [runnersFactory]
        var testClass: TestClass by testClassField
        var parametersMethod: FrameworkMethod by parametersMethodField
        var allParameters: List<Any> by allParametersField
        var parameterCount: Int by parameterCountField
        private var runnerOverride: Runner? by runnerOverrideField

        init {
            // The [FakeTestClass.fakeParameters] method throws an error so `runnerOverride` was set
            // to a special runner that will report an error when the tests are run. Set the field
            // to `null` to avoid that as actual arguments will be provided below.
            runnerOverride = null
        }

        /** Get this field from [runnersFactory]. */
        operator fun <T, V> Field.getValue(thisRef: T, property: KProperty<*>): V {
            @Suppress("UNCHECKED_CAST") return get(runnersFactory) as V
        }

        /** Set this field on [runnersFactory]. */
        operator fun <T, V> Field.setValue(thisRef: T, property: KProperty<*>, value: V) {
            set(runnersFactory, value)
        }

        /**
         * Fake test class that is passed to the [Parameterized.RunnersFactory] to ensure that its
         * constructor will complete successfully. Afterwards the fields in
         * [Parameterized.RunnersFactory] will be updated to match the actual test class.
         */
        class FakeTestClass {
            companion object {
                @JvmStatic
                @Parameters
                fun fakeArguments(): List<Any> = throw AssumptionViolatedException("fake arguments")
            }
        }

        companion object {
            fun build(clazz: Class<*>, block: ParameterizedBuilder.() -> Unit): Parameterized {
                val builder = ParameterizedBuilder()
                builder.block()
                // Create a new `Parameterized` object.
                return parameterizedConstructor.newInstance(clazz, builder.runnersFactory)
            }
            /** [Parameterized] class. */
            private val parameterizedClass = Parameterized::class.java

            /** The private [Parameterized.RunnersFactory] class. */
            private val runnersFactoryClass =
                parameterizedClass.declaredClasses.first { it.simpleName == "RunnersFactory" }

            // Get the private `Parameterized(Class, RunnersFactory)` constructor.
            private val parameterizedConstructor: Constructor<Parameterized> =
                parameterizedClass
                    .getDeclaredConstructor(Class::class.java, runnersFactoryClass)
                    .apply { isAccessible = true }

            // Create a new [Parameterized.RunnersFactory]. Uses [FakeTestClass] not the real test
            // class. The correct information will be injected into it below.
            private val runnersFactoryConstructor: Constructor<out Any> =
                runnersFactoryClass.getDeclaredConstructor(Class::class.java).apply {
                    isAccessible = true
                }

            /** [Field.modifiers] field. */
            private val modifiersField =
                getModifiersField().apply {
                    try {
                        // Modify the `modifiers` field for the field to remove `final`.
                        // This requires "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED".
                        isAccessible = true
                    } catch (e: InaccessibleObjectException) {
                        throw IllegalStateException(
                            "Add --add-opens=java.base/java.lang.reflect=ALL-UNNAMED to jvm options",
                            e
                        )
                    }
                }

            // Get various [Parameterized.RunnersFactory] fields and make them accessible and
            // settable.
            val testClassField = getSettableField("testClass")
            val parametersMethodField = getSettableField("parametersMethod")
            val allParametersField = getSettableField("allParameters")
            val parameterCountField = getSettableField("parameterCount")
            val runnerOverrideField = getSettableField("runnerOverride")

            /**
             * Get an accessible and settable (i.e. not `final`) declared field called [name] in
             * [runnersFactoryClass].
             */
            private fun getSettableField(name: String): Field {
                val field = runnersFactoryClass.getDeclaredField(name)
                field.isAccessible = true
                modifiersField.setInt(field, field.modifiers and Modifier.FINAL.inv())
                return field
            }

            /**
             * Need to use reflection to invoke `getDeclaredFields0` to get the hidden fields of the
             * [Field] class, then select the one called `modifiers`.
             */
            private fun getModifiersField(): Field {
                val getDeclaredFields0 =
                    Class::class.java.getDeclaredMethod("getDeclaredFields0", Boolean::class.java)
                getDeclaredFields0.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                val fields = getDeclaredFields0.invoke(Field::class.java, false) as Array<Field>
                return fields.first { it.name == "modifiers" }
            }
        }
    }
}
