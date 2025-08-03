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

import android.annotation.SuppressLint
import android.tools.ScenarioBuilder
import android.tools.flicker.datastore.DataStore
import android.tools.flicker.legacy.FlickerBuilder
import android.tools.flicker.legacy.LegacyFlickerTest
import android.tools.utils.CleanFlickerEnvironmentRule
import android.tools.utils.KotlinMockito
import com.google.common.truth.Truth
import kotlin.reflect.KClass
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import org.junit.runner.Description
import org.junit.runners.model.FrameworkMethod
import org.junit.runners.model.TestClass
import org.junit.runners.parameterized.TestWithParameters
import org.mockito.Mockito

/** Tests for [LegacyFlickerDecorator] */
@SuppressLint("VisibleForTests")
class LegacyFlickerDecoratorTest {
    @Before
    fun setup() {
        android.tools.flicker.datastore.DataStore.clear()
    }

    @Test
    fun usesTestMethodsProvidedByInner() {
        val scenario =
            ScenarioBuilder().forClass(TestUtils.DummyTestClassValid::class.java.name).build()
        val test =
            TestWithParameters(
                "test",
                TestClass(TestUtils.DummyTestClassValid::class.java),
                listOf(TestUtils.VALID_ARGS_EMPTY)
            )
        val mockTransitionRunner = Mockito.mock(ITransitionRunner::class.java)
        val inner = Mockito.mock(IFlickerJUnitDecorator::class.java)
        val helper = LegacyFlickerDecorator(test.testClass, scenario, mockTransitionRunner, inner)

        val mockMethod = Mockito.mock(FrameworkMethod::class.java)
        Mockito.`when`(inner.getTestMethods(test)).thenReturn(listOf(mockMethod))
        Truth.assertThat(helper.getTestMethods(test)).containsExactly(mockMethod)
    }

    @Test
    fun callsTransitionRunnerToRunTransition() {
        val scenario =
            ScenarioBuilder().forClass(TestUtils.DummyTestClassValid::class.java.name).build()
        val test =
            TestWithParameters(
                "test",
                TestClass(TestUtils.DummyTestClassValid::class.java),
                listOf(TestUtils.VALID_ARGS_EMPTY)
            )
        val mockTransitionRunner = Mockito.mock(ITransitionRunner::class.java)
        val inner = Mockito.mock(IFlickerJUnitDecorator::class.java)
        val decorator =
            LegacyFlickerDecorator(test.testClass, scenario, mockTransitionRunner, inner)
        TestUtils.executionCount = 0
        val method =
            FrameworkMethod(TestUtils.DummyTestClassValid::class.java.getMethod("dummyExecute"))

        Mockito.`when`(inner.getTestMethods(KotlinMockito.any(Any::class.java)))
            .thenReturn(listOf())
        Mockito.`when`(inner.getChildDescription(KotlinMockito.any(FrameworkMethod::class.java)))
            .thenReturn(Mockito.mock(Description::class.java))

        val description = decorator.getChildDescription(method)
        val invokerTest = TestUtils.DummyTestClassValid(LegacyFlickerTest())

        decorator
            .getMethodInvoker(
                method,
                test = invokerTest,
            )
            .evaluate()

        Mockito.verify(mockTransitionRunner).runTransition(scenario, invokerTest, description)
    }

    @Test
    fun legacyTransitionRunnerRunsTransitionOnceAndCachesToDatastore() {
        val scenario =
            ScenarioBuilder().forClass(TestUtils.DummyTestClassValid::class.java.name).build()
        val test =
            TestWithParameters(
                "test",
                TestClass(TestUtils.DummyTestClassValid::class.java),
                listOf(TestUtils.VALID_ARGS_EMPTY)
            )
        val transitionRunner = LegacyFlickerJUnit4ClassRunner(test, scenario).transitionRunner

        val inner = Mockito.mock(IFlickerJUnitDecorator::class.java)
        val decorator = LegacyFlickerDecorator(test.testClass, scenario, transitionRunner, inner)
        TestUtils.executionCount = 0
        val method =
            FrameworkMethod(TestUtils.DummyTestClassValid::class.java.getMethod("dummyExecute"))

        Mockito.`when`(inner.getTestMethods(KotlinMockito.any(Any::class.java)))
            .thenReturn(listOf(method))
        Mockito.`when`(inner.getChildDescription(KotlinMockito.any(FrameworkMethod::class.java)))
            .thenReturn(Mockito.mock(Description::class.java))

        repeat(3) {
            decorator
                .getMethodInvoker(
                    method,
                    test = TestUtils.DummyTestClassValid(LegacyFlickerTest()),
                )
                .evaluate()
        }

        Truth.assertWithMessage("Executed").that(TestUtils.executionCount).isEqualTo(1)
        Truth.assertWithMessage("In Datastore")
            .that(
                android.tools.flicker.datastore.DataStore.containsResult(
                    TestUtils.DummyTestClassValid.SCENARIO
                )
            )
            .isTrue()
    }

    @Test
    fun failNoProviderMethods() {
        assertFailProviderMethod(
            TestUtils.DummyTestClassEmpty::class,
            expectedExceptions =
                listOf("One object should be annotated with @FlickerBuilderProvider")
        )
    }

    @Test
    fun failMultipleProviderMethods() {
        assertFailProviderMethod(
            TestUtils.DummyTestClassMultipleProvider::class,
            expectedExceptions =
                listOf("Only one object should be annotated with @FlickerBuilderProvider")
        )
    }

    @Test
    fun failStaticProviderMethod() {
        assertFailProviderMethod(
            TestUtils.DummyTestClassProviderStatic::class,
            expectedExceptions = listOf("Method myMethod() should not be static")
        )
    }

    @Test
    fun failPrivateProviderMethod() {
        assertFailProviderMethod(
            TestUtils.DummyTestClassProviderPrivateVoid::class,
            expectedExceptions =
                listOf(
                    "Method myMethod() should be public",
                    "Method myMethod() should return a " +
                        "${FlickerBuilder::class.java.simpleName} object"
                )
        )
    }

    @Test
    fun failConstructorWithNoArguments() {
        assertFailConstructor(emptyList())
    }

    @Test
    fun failWithInvalidConstructorArgument() {
        assertFailConstructor(listOf(1, 2, 3))
    }

    private fun assertFailProviderMethod(cls: KClass<*>, expectedExceptions: List<String>) {
        val test =
            TestWithParameters("test", TestClass(cls.java), listOf(TestUtils.VALID_ARGS_EMPTY))
        val mockTransitionRunner = Mockito.mock(ITransitionRunner::class.java)
        val decorator =
            LegacyFlickerDecorator(
                test.testClass,
                scenario = null,
                mockTransitionRunner,
                inner = null
            )
        val failures = decorator.doValidateInstanceMethods()
        Truth.assertWithMessage("Failure count").that(failures).hasSize(expectedExceptions.count())
        expectedExceptions.forEachIndexed { idx, expectedException ->
            val failure = failures[idx]
            Truth.assertWithMessage("Failure")
                .that(failure)
                .hasMessageThat()
                .contains(expectedException)
        }
    }

    private fun assertFailConstructor(args: List<Any>) {
        val test =
            TestWithParameters("test", TestClass(TestUtils.DummyTestClassEmpty::class.java), args)
        val mockTransitionRunner = Mockito.mock(ITransitionRunner::class.java)
        val decorator =
            LegacyFlickerDecorator(
                test.testClass,
                scenario = null,
                mockTransitionRunner,
                inner = null
            )
        val failures = decorator.doValidateConstructor()

        Truth.assertWithMessage("Failure count").that(failures).hasSize(1)

        val failure = failures.first()
        Truth.assertWithMessage("Expected failure")
            .that(failure)
            .hasMessageThat()
            .contains(
                "Constructor should have a parameter of type ${LegacyFlickerTest::class.simpleName}"
            )
    }

    companion object {
        @ClassRule @JvmField val ENV_CLEANUP = CleanFlickerEnvironmentRule()
    }
}
