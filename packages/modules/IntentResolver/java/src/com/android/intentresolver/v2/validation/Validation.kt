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
package com.android.intentresolver.v2.validation

import com.android.intentresolver.v2.validation.Importance.CRITICAL
import com.android.intentresolver.v2.validation.Importance.WARNING

/**
 * Provides a mechanism for validating a result from a set of properties.
 *
 * The results of validation are provided as [findings].
 */
interface Validation {
    val findings: List<Finding>

    /**
     * Require a valid property.
     *
     * If [property] is not valid, this [Validation] will be immediately completed as [Invalid].
     *
     * @param property the required property
     * @return a valid **T**
     */
    @Throws(InvalidResultError::class) fun <T> required(property: Validator<T>): T

    /**
     * Request an optional value for a property.
     *
     * If [property] is not valid, this [Validation] will be immediately completed as [Invalid].
     *
     * @param property the required property
     * @return a valid **T**
     */
    fun <T> optional(property: Validator<T>): T?

    /**
     * Report a property as __ignored__.
     *
     * The presence of any value will report a warning citing [reason].
     */
    fun <T> ignored(property: Validator<T>, reason: String)
}

/** Performs validation for a specific key -> value pair. */
interface Validator<T> {
    val key: String

    /**
     * Performs validation on a specific value from [source].
     *
     * @param source a source for reading the property value. Values are intentionally untyped
     *   (Any?) to avoid upstream code from making type assertions through type inference. Types are
     *   asserted later using a [Validator].
     * @param importance the importance of any findings
     */
    fun validate(source: (String) -> Any?, importance: Importance): ValidationResult<T>
}

internal class InvalidResultError internal constructor() : Error()

/**
 * Perform a number of validations on the source, assembling and returning a Result.
 *
 * When an exception is thrown by [validate], it is caught here. In response, a failed
 * [ValidationResult] is returned containing a [CRITICAL] [Finding] for the exception.
 *
 * @param validate perform validations and return a [ValidationResult]
 */
fun <T> validateFrom(source: (String) -> Any?, validate: Validation.() -> T): ValidationResult<T> {
    val validation = ValidationImpl(source)
    return runCatching { validate(validation) }
        .fold(
            onSuccess = { result -> Valid(result, validation.findings) },
            onFailure = {
                when (it) {
                    // A validator has interrupted validation. Return the findings.
                    is InvalidResultError -> Invalid(validation.findings)

                    // Some other exception was thrown from [validate],
                    else -> Invalid(error = UncaughtException(it))
                }
            }
        )
}

private class ValidationImpl(val source: (String) -> Any?) : Validation {
    override val findings = mutableListOf<Finding>()

    override fun <T> optional(property: Validator<T>): T? = validate(property, WARNING)

    override fun <T> required(property: Validator<T>): T {
        return validate(property, CRITICAL) ?: throw InvalidResultError()
    }

    override fun <T> ignored(property: Validator<T>, reason: String) {
        val result = property.validate(source, WARNING)
        if (result is Valid) {
            // Note: Any warnings about the value itself (result.findings) are ignored.
            findings += IgnoredValue(property.key, reason)
        }
    }

    private fun <T> validate(property: Validator<T>, importance: Importance): T? {
        return runCatching { property.validate(source, importance) }
            .fold(
                onSuccess = { result ->
                    return when (result) {
                        is Valid -> {
                            findings += result.warnings
                            result.value
                        }
                        is Invalid -> {
                            findings += result.errors
                            null
                        }
                    }
                },
                onFailure = {
                    findings += UncaughtException(it, property.key)
                    null
                }
            )
    }
}
