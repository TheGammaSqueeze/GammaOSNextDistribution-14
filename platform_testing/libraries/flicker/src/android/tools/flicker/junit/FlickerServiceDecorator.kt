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

package android.tools.flicker.junit

import android.app.Instrumentation
import android.device.collectors.util.SendToInstrumentation
import android.os.Bundle
import android.tools.Scenario
import android.tools.ScenarioBuilder
import android.tools.flicker.FlickerService
import android.tools.flicker.FlickerServiceResultsCollector.Companion.FLICKER_ASSERTIONS_COUNT_KEY
import android.tools.flicker.ScenarioInstance
import android.tools.flicker.Utils.captureTrace
import android.tools.flicker.annotation.ExpectedScenarios
import android.tools.flicker.annotation.FlickerConfigProvider
import android.tools.flicker.assertions.ScenarioAssertion
import android.tools.flicker.config.FlickerConfig
import android.tools.flicker.config.ScenarioId
import android.tools.io.Reader
import android.tools.traces.getDefaultFlickerOutputDir
import android.tools.traces.now
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth
import java.lang.reflect.Method
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.Description
import org.junit.runners.model.FrameworkMethod
import org.junit.runners.model.Statement
import org.junit.runners.model.TestClass

class FlickerServiceDecorator(
    testClass: TestClass,
    val paramString: String?,
    private val skipNonBlocking: Boolean,
    inner: IFlickerJUnitDecorator?,
    instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation(),
    flickerService: FlickerService? = null
) : AbstractFlickerRunnerDecorator(testClass, inner, instrumentation) {
    private val flickerService by lazy { flickerService ?: FlickerService(getFlickerConfig()) }

    private val testClassName =
        ScenarioBuilder().forClass("${testClass.name}${paramString ?: ""}").build()

    override fun getChildDescription(method: FrameworkMethod): Description {
        return if (isMethodHandledByDecorator(method)) {
            Description.createTestDescription(testClass.javaClass, method.name, *method.annotations)
        } else {
            inner?.getChildDescription(method) ?: error("No child descriptor found")
        }
    }

    private val flickerServiceMethodsFor =
        mutableMapOf<FrameworkMethod, Collection<InjectedTestCase>>()
    private val innerMethodsResults = mutableMapOf<FrameworkMethod, Throwable?>()

    override fun getTestMethods(test: Any): List<FrameworkMethod> {
        val innerMethods =
            inner?.getTestMethods(test)
                ?: error("FlickerServiceDecorator requires a non-null inner decorator")
        val testMethods = innerMethods.toMutableList()

        if (shouldComputeTestMethods()) {
            for (method in innerMethods) {
                if (!innerMethodsResults.containsKey(method)) {
                    var methodResult: Throwable? =
                        null // TODO: Maybe don't use null but wrap in another object
                    val reader =
                        captureTrace(testClassName, getDefaultFlickerOutputDir()) { writer ->
                            try {
                                Utils.notifyRunnerProgress(
                                    testClassName,
                                    "Running setup",
                                    instrumentation
                                )
                                val befores = testClass.getAnnotatedMethods(Before::class.java)
                                befores.forEach { it.invokeExplosively(test) }

                                Utils.notifyRunnerProgress(
                                    testClassName,
                                    "Running transition",
                                    instrumentation
                                )
                                writer.setTransitionStartTime(now())
                                method.invokeExplosively(test)
                                writer.setTransitionEndTime(now())

                                Utils.notifyRunnerProgress(
                                    testClassName,
                                    "Running teardown",
                                    instrumentation
                                )
                                val afters = testClass.getAnnotatedMethods(After::class.java)
                                afters.forEach { it.invokeExplosively(test) }
                            } catch (e: Throwable) {
                                methodResult = e
                            } finally {
                                innerMethodsResults[method] = methodResult
                            }
                        }
                    if (methodResult == null) {
                        Utils.notifyRunnerProgress(
                            testClassName,
                            "Computing Flicker service tests",
                            instrumentation
                        )
                        try {
                            flickerServiceMethodsFor[method] =
                                computeFlickerServiceTests(reader, testClassName, method)
                        } catch (e: Throwable) {
                            // Failed to compute flicker service methods
                            innerMethodsResults[method] = e
                        }
                    }
                }

                if (innerMethodsResults[method] == null) {
                    testMethods.addAll(flickerServiceMethodsFor[method]!!)
                }
            }
        }

        return testMethods
    }

    // TODO: Common with LegacyFlickerServiceDecorator, might be worth extracting this up
    private fun shouldComputeTestMethods(): Boolean {
        // Don't compute when called from validateInstanceMethods since this will fail
        // as the parameters will not be set. And AndroidLogOnlyBuilder is a non-executing runner
        // used to run tests in dry-run mode, so we don't want to execute in flicker transition in
        // that case either.
        val stackTrace = Thread.currentThread().stackTrace
        val isDryRun =
            stackTrace.any { it.methodName == "validateInstanceMethods" } ||
                stackTrace.any {
                    it.className == "androidx.test.internal.runner.AndroidLogOnlyBuilder"
                } ||
                stackTrace.any {
                    it.className == "androidx.test.internal.runner.NonExecutingRunner"
                }

        return !isDryRun
    }

    override fun getMethodInvoker(method: FrameworkMethod, test: Any): Statement {
        return object : Statement() {
            @Throws(Throwable::class)
            override fun evaluate() {
                val description = getChildDescription(method)
                if (isMethodHandledByDecorator(method)) {
                    (method as InjectedTestCase).execute(description)
                } else {
                    if (innerMethodsResults.containsKey(method)) {
                        innerMethodsResults[method]?.let { throw it }
                    } else {
                        inner?.getMethodInvoker(method, test)?.evaluate()
                    }
                }
            }
        }
    }

    override fun doValidateInstanceMethods(): List<Throwable> {
        val errors = super.doValidateInstanceMethods().toMutableList()

        val testMethods = testClass.getAnnotatedMethods(Test::class.java)
        if (testMethods.size > 1) {
            errors.add(IllegalArgumentException("Only one @Test annotated method is supported"))
        }

        // Validate Registry provider
        val flickerConfigProviderProviderFunctions =
            testClass.getAnnotatedMethods(FlickerConfigProvider::class.java).filter {
                it.isStatic && it.isPublic
            }
        if (flickerConfigProviderProviderFunctions.isEmpty()) {
            errors.add(
                IllegalArgumentException(
                    "A public static function returning a " +
                        "${FlickerConfig::class.simpleName} annotated with " +
                        "@${FlickerConfigProvider::class.simpleName} should be provided."
                )
            )
        } else if (flickerConfigProviderProviderFunctions.size > 1) {
            errors.add(
                IllegalArgumentException(
                    "Only one @${FlickerConfigProvider::class.simpleName} " +
                        "annotated method is supported."
                )
            )
        } else if (
            flickerConfigProviderProviderFunctions.first().returnType.name !=
                FlickerConfig::class.qualifiedName
        ) {
            errors.add(
                IllegalArgumentException(
                    "Expected method annotated with " +
                        "@${FlickerConfig::class.simpleName} to return " +
                        "${FlickerConfig::class.qualifiedName} but was " +
                        "${flickerConfigProviderProviderFunctions.first().returnType.name} instead."
                )
            )
        } else {
            // Validate @ExpectedScenarios annotation
            val expectedScenarioAnnotations =
                testClass.getAnnotatedMethods(ExpectedScenarios::class.java).map {
                    it.getAnnotation(ExpectedScenarios::class.java)
                }
            val registeredScenarios = getFlickerConfig().getEntries().map { it.scenarioId.name }
            for (expectedScenarioAnnotation in expectedScenarioAnnotations) {
                for (expectedScenario in expectedScenarioAnnotation.expectedScenarios) {
                    val scenarioRegistered = registeredScenarios.contains(expectedScenario)
                    if (!scenarioRegistered) {
                        errors.add(
                            IllegalArgumentException(
                                "Provided scenarios that are not registered to " +
                                    "@${ExpectedScenarios::class.simpleName} annotation. " +
                                    "$expectedScenario is not registered in the " +
                                    "${FlickerConfig::class.simpleName}. Available scenarios " +
                                    "are [${registeredScenarios.joinToString()}]."
                            )
                        )
                    }
                }
            }
        }

        return errors
    }

    private fun getFlickerConfig(): FlickerConfig {
        require(testClass.getAnnotatedMethods(ExpectedScenarios::class.java).size == 1) {
            "@ExpectedScenarios missing. " +
                "getFlickerConfig() may have been called before validation."
        }

        val flickerConfigProviderProviderFunction =
            testClass.getAnnotatedMethods(FlickerConfigProvider::class.java).first()
        // TODO: Pass the correct target
        return flickerConfigProviderProviderFunction.invokeExplosively(testClass) as FlickerConfig
    }

    override fun shouldRunBeforeOn(method: FrameworkMethod): Boolean {
        return false
    }

    override fun shouldRunAfterOn(method: FrameworkMethod): Boolean {
        return false
    }

    private fun isMethodHandledByDecorator(method: FrameworkMethod): Boolean {
        return method is InjectedTestCase && method.injectedBy == this
    }

    private fun computeFlickerServiceTests(
        reader: Reader,
        testScenario: Scenario,
        method: FrameworkMethod
    ): Collection<InjectedTestCase> {
        val expectedScenarios =
            (method.annotations
                    .filterIsInstance<ExpectedScenarios>()
                    .firstOrNull()
                    ?.expectedScenarios
                    ?: emptyArray())
                .map { ScenarioId(it) }
                .toSet()

        return getFaasTestCases(
            testScenario,
            expectedScenarios,
            paramString ?: "",
            reader,
            flickerService,
            instrumentation,
            this,
            skipNonBlocking
        )
    }

    companion object {
        private fun getDetectedScenarios(
            testScenario: Scenario,
            reader: Reader,
            flickerService: FlickerService
        ): Collection<ScenarioId> {
            val groupedAssertions = getGroupedAssertions(testScenario, reader, flickerService)
            return groupedAssertions.keys.map { it.type }.distinct()
        }

        private fun getCachedResultMethod(): Method {
            return InjectedTestCase::class.java.getMethod("execute", Description::class.java)
        }

        private fun getGroupedAssertions(
            testScenario: Scenario,
            reader: Reader,
            flickerService: FlickerService,
        ): Map<ScenarioInstance, Collection<ScenarioAssertion>> {
            if (
                !android.tools.flicker.datastore.DataStore.containsFlickerServiceResult(
                    testScenario
                )
            ) {
                val detectedScenarios = flickerService.detectScenarios(reader)
                val groupedAssertions = detectedScenarios.associateWith { it.generateAssertions() }
                android.tools.flicker.datastore.DataStore.addFlickerServiceAssertions(
                    testScenario,
                    groupedAssertions
                )
            }

            return android.tools.flicker.datastore.DataStore.getFlickerServiceAssertions(
                testScenario
            )
        }

        internal fun getFaasTestCases(
            testScenario: Scenario,
            expectedScenarios: Set<ScenarioId>,
            paramString: String,
            reader: Reader,
            flickerService: FlickerService,
            instrumentation: Instrumentation,
            caller: IFlickerJUnitDecorator,
            skipNonBlocking: Boolean,
        ): Collection<InjectedTestCase> {
            val groupedAssertions = getGroupedAssertions(testScenario, reader, flickerService)
            val organizedScenarioInstances = groupedAssertions.keys.groupBy { it.type }

            val faasTestCases = mutableListOf<FlickerServiceCachedTestCase>()
            organizedScenarioInstances.values.forEachIndexed {
                scenarioTypesIndex,
                scenarioInstancesOfSameType ->
                scenarioInstancesOfSameType.forEachIndexed { scenarioInstanceIndex, scenarioInstance
                    ->
                    val assertionsForScenarioInstance = groupedAssertions[scenarioInstance]!!

                    assertionsForScenarioInstance.forEach {
                        faasTestCases.add(
                            FlickerServiceCachedTestCase(
                                assertion = it,
                                method = getCachedResultMethod(),
                                skipNonBlocking = skipNonBlocking,
                                isLast =
                                    organizedScenarioInstances.values.size == scenarioTypesIndex &&
                                        scenarioInstancesOfSameType.size == scenarioInstanceIndex,
                                injectedBy = caller,
                                paramString =
                                    "${paramString}${
                                    if (scenarioInstancesOfSameType.size > 1) {
                                        "_${scenarioInstanceIndex + 1}"
                                    } else {
                                        ""
                                    }}",
                                instrumentation = instrumentation,
                            )
                        )
                    }
                }
            }

            val detectedScenarioTestCase =
                AnonymousInjectedTestCase(
                    getCachedResultMethod(),
                    "FaaS_DetectedExpectedScenarios$paramString",
                    injectedBy = caller
                ) {
                    val metricBundle = Bundle()
                    metricBundle.putString(FLICKER_ASSERTIONS_COUNT_KEY, "${faasTestCases.size}")
                    SendToInstrumentation.sendBundle(instrumentation, metricBundle)

                    Truth.assertThat(getDetectedScenarios(testScenario, reader, flickerService))
                        .containsAtLeastElementsIn(expectedScenarios)
                }

            return faasTestCases + listOf(detectedScenarioTestCase)
        }
    }
}
