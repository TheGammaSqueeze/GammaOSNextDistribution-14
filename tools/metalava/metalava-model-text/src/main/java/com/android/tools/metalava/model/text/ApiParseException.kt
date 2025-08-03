/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.metalava.model.text

import com.android.tools.metalava.model.MetalavaApi
import com.android.tools.metalava.reporter.FileLocation

@MetalavaApi
class ApiParseException(
    message: String,
    private val location: FileLocation? = null,
    cause: Exception? = null,
) : Exception(message, cause) {

    internal constructor(
        message: String,
        tokenizer: Tokenizer,
        cause: Exception? = null,
    ) : this(message, tokenizer.fileLocation(), cause = cause)

    override val message: String
        get() {
            return buildString {
                location?.appendTo(this)
                if (isNotEmpty()) {
                    append(": ")
                }
                append(super.message)
            }
        }
}
