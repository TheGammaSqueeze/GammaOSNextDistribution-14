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
import android.net.Uri
import com.android.intentresolver.v2.validation.Importance
import com.android.intentresolver.v2.validation.Invalid
import com.android.intentresolver.v2.validation.NoValue
import com.android.intentresolver.v2.validation.Valid
import com.android.intentresolver.v2.validation.ValidationResult
import com.android.intentresolver.v2.validation.Validator
import com.android.intentresolver.v2.validation.ValueIsWrongType

class IntentOrUri(override val key: String) : Validator<Intent> {

    override fun validate(
        source: (String) -> Any?,
        importance: Importance
    ): ValidationResult<Intent> {

        return when (val value = source(key)) {
            // An intent, return it.
            is Intent -> Valid(value)

            // A Uri was supplied.
            // Unfortunately, converting Uri -> Intent requires a toString().
            is Uri -> Valid(Intent.parseUri(value.toString(), Intent.URI_INTENT_SCHEME))

            // No value present.
            null -> when (importance) {
                Importance.WARNING -> Invalid() // No warnings if optional, but missing
                Importance.CRITICAL -> Invalid(NoValue(key, importance, Intent::class))
            }

            // Some other type.
            else -> {
                return Invalid(
                    ValueIsWrongType(
                        key,
                        importance,
                        actualType = value::class,
                        allowedTypes = listOf(Intent::class, Uri::class)
                    )
                )
            }
        }
    }
}
