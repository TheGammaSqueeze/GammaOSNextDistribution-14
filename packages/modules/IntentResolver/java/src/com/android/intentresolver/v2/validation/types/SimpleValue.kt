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
package com.android.intentresolver.v2.validation.types

import com.android.intentresolver.v2.validation.Importance
import com.android.intentresolver.v2.validation.Invalid
import com.android.intentresolver.v2.validation.NoValue
import com.android.intentresolver.v2.validation.Valid
import com.android.intentresolver.v2.validation.ValidationResult
import com.android.intentresolver.v2.validation.Validator
import com.android.intentresolver.v2.validation.ValueIsWrongType
import kotlin.reflect.KClass
import kotlin.reflect.cast

class SimpleValue<T : Any>(
    override val key: String,
    private val expected: KClass<T>,
) : Validator<T> {

    override fun validate(source: (String) -> Any?, importance: Importance): ValidationResult<T> {
        val value: Any? = source(key)
        return when {
            // The value is present and of the expected type.
            expected.isInstance(value) -> return Valid(expected.cast(value))

            // No value is present.
            value == null -> when (importance) {
                Importance.WARNING -> Invalid() // No warnings if optional, but missing
                Importance.CRITICAL -> Invalid(NoValue(key, importance, expected))
            }

            // The value is some other type.
            else ->
                Invalid(listOf(
                    ValueIsWrongType(
                        key,
                        importance,
                        actualType = value::class,
                        allowedTypes = listOf(expected)
                    )
                ))
        }
    }
}
