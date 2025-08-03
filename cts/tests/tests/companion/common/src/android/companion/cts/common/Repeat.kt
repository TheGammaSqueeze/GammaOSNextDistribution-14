/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.companion.cts.common

import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Repeats the execution of the annotated test method the given number of times (10 by default).
 *
 * Note that a [RepeatRule] must be present in the class for the annotation to take effect.
 *
 * For an example, see the following code:
 * ```
 * @get:Rule
 * val repeatRule = RepeatRule()
 *
 * @Test
 * @Repeat(5)
 * fun myTest() {
 *   ...
 * }
 * ```
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class Repeat(val times: Int = 10)

/**
 * Use this rule together with the [Repeat] annotation to automatically
 * repeat the execution of annotated tests.
 */
class RepeatRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement =
        description.getAnnotation(Repeat::class.java)?.let { repeat ->
            object : Statement() {
                @Throws(Throwable::class)
                override fun evaluate() = repeat(repeat.times) { base.evaluate() }
            }
        } ?: base
}
