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

/**
 * Utility class to store assertions with an identifier to help generate more useful debug data when
 * dealing with multiple assertions.
 *
 * @param predicate Assertion to execute
 * @param name Assertion name
 * @param isOptional If the assertion is optional (can fail) or not (must pass)
 */
open class NamedAssertion<T>(
    val predicate: (T) -> Unit,
    override val name: String,
    override val isOptional: Boolean = false
) : Assertion<T> {
    override operator fun invoke(target: T) = predicate(target)
    override fun toString(): String = "Assertion($name)${if (isOptional) "[optional]" else ""}"

    /**
     * We can't check the actual assertion is the same. We are checking for the name, which should
     * have a 1:1 correspondence with the assertion, but there is no actual guarantee of the same
     * execution of the assertion even if isEqual() is true.
     */
    override fun equals(other: Any?): Boolean {
        if (other !is NamedAssertion<*>) {
            return false
        }
        if (name != other.name) {
            return false
        }
        if (isOptional != other.isOptional) {
            return false
        }
        return true
    }

    override fun hashCode(): Int {
        var result = predicate.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + isOptional.hashCode()
        return result
    }
}
