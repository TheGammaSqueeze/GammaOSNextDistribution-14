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

import android.tools.Scenario
import android.tools.flicker.datastore.DataStore
import android.tools.flicker.legacy.FlickerBuilder
import android.tools.flicker.legacy.LegacyFlickerTest
import java.lang.reflect.Modifier
import org.junit.runner.Description
import org.junit.runners.model.FrameworkMethod
import org.junit.runners.model.Statement
import org.junit.runners.model.TestClass

class LegacyFlickerDecorator(
    testClass: TestClass,
    val scenario: Scenario?,
    val transitionRunner: ITransitionRunner,
    inner: IFlickerJUnitDecorator? = null
) : AbstractFlickerRunnerDecorator(testClass, inner) {
    override fun getChildDescription(method: FrameworkMethod): Description {
        return inner?.getChildDescription(method)
            ?: error("Need inner to provide child description")
    }

    override fun getTestMethods(test: Any): List<FrameworkMethod> {
        return inner?.getTestMethods(test) ?: error("Need inner to provide test methods")
    }

    override fun getMethodInvoker(method: FrameworkMethod, test: Any): Statement {
        return object : Statement() {
            override fun evaluate() {
                requireNotNull(scenario) { "Expected to have a scenario to run" }
                val description = getChildDescription(method)
                if (!android.tools.flicker.datastore.DataStore.containsResult(scenario)) {
                    transitionRunner.runTransition(scenario, test, description)
                }
                inner?.getMethodInvoker(method, test)?.evaluate()
            }
        }
    }

    override fun doValidateConstructor(): List<Throwable> {
        val errors = super.doValidateConstructor().toMutableList()
        val ctor = testClass.javaClass.constructors.firstOrNull()
        if (ctor?.parameterTypes?.none { it == LegacyFlickerTest::class.java } != false) {
            errors.add(
                IllegalStateException(
                    "Constructor should have a parameter of type " +
                        LegacyFlickerTest::class.java.simpleName
                )
            )
        }
        return errors
    }

    override fun doValidateInstanceMethods(): List<Throwable> {
        val errors = super.doValidateInstanceMethods().toMutableList()

        val methods = getCandidateProviderMethods(testClass)

        if (methods.isEmpty() || methods.size > 1) {
            val prefix = if (methods.isEmpty()) "One" else "Only one"
            errors.add(
                IllegalArgumentException(
                    "$prefix object should be annotated with @FlickerBuilderProvider"
                )
            )
        } else {
            val method = methods.first()

            if (Modifier.isStatic(method.method.modifiers)) {
                errors.add(IllegalArgumentException("Method ${method.name}() should not be static"))
            }
            if (!Modifier.isPublic(method.method.modifiers)) {
                errors.add(IllegalArgumentException("Method ${method.name}() should be public"))
            }
            if (method.returnType != FlickerBuilder::class.java) {
                errors.add(
                    IllegalArgumentException(
                        "Method ${method.name}() should return a " +
                            "${FlickerBuilder::class.java.simpleName} object"
                    )
                )
            }
            if (method.method.parameterTypes.isNotEmpty()) {
                errors.add(
                    IllegalArgumentException("Method ${method.name} should have no parameters")
                )
            }
        }

        return errors
    }

    private val providerMethod: FrameworkMethod
        get() =
            getCandidateProviderMethods(testClass).firstOrNull()
                ?: error("Provider method not found")

    companion object {
        private fun getCandidateProviderMethods(testClass: TestClass): List<FrameworkMethod> =
            testClass.getAnnotatedMethods(FlickerBuilderProvider::class.java) ?: emptyList()
    }
}
