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

package android.tools.flicker.assertions

/** Utility class to store assertions composed of multiple individual assertions */
class CompoundAssertion<T>(assertion: (T) -> Unit, name: String, optional: Boolean) : Assertion<T> {
    private val assertions = mutableListOf<NamedAssertion<T>>()

    init {
        add(assertion, name, optional)
    }

    override val isOptional
        get() = assertions.all { it.isOptional }

    override val name
        get() = assertions.joinToString(" and ") { it.name }

    /**
     * Executes all [assertions] on [target]
     *
     * In case of failure, returns the first non-optional failure (if available) or the first failed
     * assertion
     */
    override fun invoke(target: T) {
        val failures =
            assertions.mapNotNull { assertion ->
                val error = kotlin.runCatching { assertion.invoke(target) }.exceptionOrNull()
                if (error != null) {
                    Pair(assertion, error)
                } else {
                    null
                }
            }
        val nonOptionalFailure = failures.firstOrNull { !it.first.isOptional }
        if (nonOptionalFailure != null) {
            throw nonOptionalFailure.second
        }
        val firstFailure = failures.firstOrNull()
        // Only throw first failure if all siblings are also optional otherwise don't throw anything
        // If the CompoundAssertion is fully optional (i.e. all assertions in the compound assertion
        // are optional), then we want to make sure the AssertionsChecker knows about the failure to
        // not advance to the next state. Otherwise, the AssertionChecker doesn't need to know about
        // the failure and can just consider the assertion as passed and advance to the next state
        // since there were non-optional assertions which passed.
        if (firstFailure != null && isOptional) {
            throw firstFailure.second
        }
    }

    /** Adds a new assertion to the list */
    fun add(assertion: (T) -> Unit, name: String, optional: Boolean) {
        assertions.add(NamedAssertion(assertion, name, optional))
    }

    override fun toString(): String = name

    override fun equals(other: Any?): Boolean {
        if (other !is CompoundAssertion<*>) {
            return false
        }
        if (!super.equals(other)) {
            return false
        }
        assertions.forEachIndexed { index, assertion ->
            if (assertion != other.assertions[index]) {
                return false
            }
        }
        return true
    }

    override fun hashCode(): Int {
        return assertions.hashCode()
    }
}
