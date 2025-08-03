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

package platform.test.motion.golden

import com.google.common.truth.FailureMetadata
import com.google.common.truth.Subject
import com.google.common.truth.Truth
import org.json.JSONObject

/** [Truth] subject for org.json. */
class JsonSubject private constructor(failureMetadata: FailureMetadata, private val actual: Any?) :
    Subject(failureMetadata, actual) {

    /**
     * Verifies the subject is a [JSONObject] that matches the [expected] json.
     *
     * Note that this verifications does assert the (meaningless) order of properties. This subject
     * needs to be re-written if that turns out to be a problem. For now, this simply hides that
     * hack within this implementation.
     */
    override fun isEqualTo(expected: Any?) {
        if (actual is JSONObject && expected is JSONObject) {
            check("serializedJson")
                .that(actual.toString(PRETTY_PRINT_INDENT))
                .isEqualTo(expected.toString(PRETTY_PRINT_INDENT))
        } else {
            super.isEqualTo(expected)
        }
    }

    companion object {
        private const val PRETTY_PRINT_INDENT = 2

        /** Returns a factory to be used with [Truth.assertAbout]. */
        fun json(): Factory<JsonSubject, Any> {
            return Factory { failureMetadata: FailureMetadata, subject: Any? ->
                JsonSubject(failureMetadata, subject)
            }
        }

        /** Shortcut for `Truth.assertAbout(json()).that(json)`. */
        fun assertThat(json: JSONObject): JsonSubject = Truth.assertAbout(json()).that(json)
    }
}
