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

package android.tools.flicker.subject

import android.tools.Timestamp
import android.tools.flicker.assertions.Fact
import android.tools.io.Reader

/** Base subject for flicker assertions */
abstract class FlickerSubject {
    abstract val timestamp: Timestamp
    internal open val reader: Reader? = null
    internal open val selfFacts: List<Fact> = emptyList()

    fun check(lazyMessage: () -> String): CheckSubjectBuilder {
        return CheckSubjectBuilder(this.timestamp, this.selfFacts, this.reader, lazyMessage)
    }

    companion object {
        const val ASSERTION_TAG = "Assertion"
    }
}
