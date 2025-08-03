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

import android.tools.flicker.subject.FlickerSubject
import kotlin.reflect.KClass

/** Class containing basic data about an assertion */
data class AssertionDataImpl(
    /** Segment of the trace where the assertion will be applied (e.g., start, end). */
    val tag: String,
    /** Expected run result type */
    val expectedSubjectClass: KClass<out FlickerSubject>,
    /** Assertion command */
    val assertion: FlickerSubject.() -> Unit
) : AssertionData {
    override fun checkAssertion(run: SubjectsParser) {
        val subject = run.getSubjectOfType(tag, expectedSubjectClass)
        subject?.let { assertion(it) }
    }
}
