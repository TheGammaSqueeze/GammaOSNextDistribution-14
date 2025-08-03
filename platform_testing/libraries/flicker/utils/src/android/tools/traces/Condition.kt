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

package android.tools.traces

/**
 * The utility class to wait a condition with customized options. The default retry policy is 5
 * times with interval 1 second.
 *
 * @param <T> The type of the object to validate.
 *
 * <p>Sample:</p> <pre> // Simple case. if (Condition.waitFor("true value", () -> true)) {
 *
 * ```
 *     println("Success");
 * ```
 *
 * } // Wait for customized result with customized validation. String result =
 * Condition.waitForResult(new Condition<String>("string comparison")
 *
 * ```
 *         .setResultSupplier(() -> "Result string")
 *         .setResultValidator(str -> str.equals("Expected string"))
 *         .setRetryIntervalMs(500)
 *         .setRetryLimit(3)
 *         .setOnFailure(str -> println("Failed on " + str)));
 * ```
 *
 * </pre>
 *
 * @param message The message to show what is waiting for.
 * @param condition If it returns true, that means the condition is satisfied.
 */
open class Condition<T>(
    protected open val message: String = "",
    protected open val condition: (T) -> Boolean
) {
    /** @return if [value] satisfies the condition */
    fun isSatisfied(value: T): Boolean {
        return condition.invoke(value)
    }

    /** @return the negation of the current assertion */
    fun negate(): Condition<T> = Condition(message = "!$message") { !this.condition.invoke(it) }

    /** @return a formatted message for the passing or failing condition on a state */
    open fun getMessage(value: T): String = "$message(passed=${isSatisfied(value)})"

    override fun toString(): String = this.message
}
