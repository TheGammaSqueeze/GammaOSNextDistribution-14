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

import android.content.Intent
import com.android.intentresolver.v2.validation.Importance
import com.android.intentresolver.v2.validation.Invalid
import com.android.intentresolver.v2.validation.NoValue
import com.android.intentresolver.v2.validation.Valid
import com.android.intentresolver.v2.validation.ValidationResult
import com.android.intentresolver.v2.validation.Validator
import com.android.intentresolver.v2.validation.ValueIsWrongType
import com.android.intentresolver.v2.validation.WrongElementType
import kotlin.reflect.KClass
import kotlin.reflect.cast

class ParceledArray<T : Any>(
    override val key: String,
    private val elementType: KClass<T>,
) : Validator<List<T>> {

    override fun validate(
        source: (String) -> Any?,
        importance: Importance
    ): ValidationResult<List<T>> {

        return when (val value: Any? = source(key)) {
            // No value present.
            null -> when (importance) {
                Importance.WARNING -> Invalid() // No warnings if optional, but missing
                Importance.CRITICAL -> Invalid(NoValue(key, importance, elementType))
            }
            // A parcel does not transfer the element type information for parcelable
            // arrays. This leads to a restored type of Array<Parcelable>, which is
            // incompatible with Array<T : Parcelable>.

            // To handle this safely, treat as Array<*>, assert contents of the expected
            // parcelable type, and return as a list.

            is Array<*> -> {
                val invalid = value.filterNotNull().firstOrNull { !elementType.isInstance(it) }
                when (invalid) {
                    // No invalid elements, result is ok.
                    null -> Valid(value.map { elementType.cast(it) })

                    // At least one incorrect element type found.
                    else ->
                        Invalid(
                            WrongElementType(
                                key,
                                importance,
                                actualType = invalid::class,
                                container = Array::class,
                                expectedType = elementType
                            )
                        )
                }
            }

            // The value is not an Array at all.
            else ->
                Invalid(
                    ValueIsWrongType(
                        key,
                        importance,
                        actualType = value::class,
                        allowedTypes = listOf(elementType)
                    )
                )
        }
    }
}
