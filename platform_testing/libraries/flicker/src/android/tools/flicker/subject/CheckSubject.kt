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
import android.tools.flicker.subject.exceptions.ExceptionMessageBuilder
import android.tools.flicker.subject.exceptions.InvalidPropertyException
import android.tools.io.Reader

/** Subject for flicker checks */
data class CheckSubject<T>(
    private val actualValue: T?,
    private val timestamp: Timestamp,
    private val extraFacts: List<Fact>,
    private val reader: Reader?,
    private val lazyMessage: () -> String,
) {
    private val exceptionMessageBuilder: ExceptionMessageBuilder
        get() {
            val builder =
                ExceptionMessageBuilder().setTimestamp(timestamp).addExtraDescription(extraFacts)
            if (reader != null) {
                builder.setReader(reader)
            }
            return builder
        }

    fun isEqual(expectedValue: T?) {
        if (actualValue != expectedValue) {
            val errorMsgBuilder =
                exceptionMessageBuilder
                    .setMessage(lazyMessage.invoke())
                    .setExpected(expectedValue)
                    .setActual(actualValue)
            throw InvalidPropertyException(errorMsgBuilder)
        }
    }

    fun isNotEqual(expectedValue: T?) {
        if (actualValue == expectedValue) {
            val errorMsgBuilder =
                exceptionMessageBuilder
                    .setMessage(lazyMessage.invoke())
                    .setExpected("Different from $expectedValue")
                    .setActual(actualValue)
            throw InvalidPropertyException(errorMsgBuilder)
        }
    }

    fun isNull() {
        if (actualValue != null) {
            val errorMsgBuilder =
                exceptionMessageBuilder
                    .setMessage(lazyMessage.invoke())
                    .setExpected(null)
                    .setActual(actualValue)
            throw InvalidPropertyException(errorMsgBuilder)
        }
    }

    fun isNotNull() {
        if (actualValue == null) {
            val errorMsgBuilder =
                exceptionMessageBuilder
                    .setMessage(lazyMessage.invoke())
                    .setExpected("Not null")
                    .setActual(null)
            throw InvalidPropertyException(errorMsgBuilder)
        }
    }

    fun isLower(expectedValue: T?) {
        if (
            actualValue == null ||
                expectedValue == null ||
                (actualValue as Comparable<T>) >= expectedValue
        ) {
            val errorMsgBuilder =
                exceptionMessageBuilder
                    .setMessage(lazyMessage.invoke())
                    .setExpected("Lower than $expectedValue")
                    .setActual(actualValue)
            throw InvalidPropertyException(errorMsgBuilder)
        }
    }

    fun isLowerOrEqual(expectedValue: T?) {
        if (
            actualValue == null ||
                expectedValue == null ||
                (actualValue as Comparable<T>) > expectedValue
        ) {
            val errorMsgBuilder =
                exceptionMessageBuilder
                    .setMessage(lazyMessage.invoke())
                    .setExpected("Lower or equal to $expectedValue")
                    .setActual(actualValue)
            throw InvalidPropertyException(errorMsgBuilder)
        }
    }

    fun isGreater(expectedValue: T) {
        if (
            actualValue == null ||
                expectedValue == null ||
                (actualValue as Comparable<T>) <= expectedValue
        ) {
            val errorMsgBuilder =
                exceptionMessageBuilder
                    .setMessage(lazyMessage.invoke())
                    .setExpected("Greater than $expectedValue")
                    .setActual(actualValue)
            throw InvalidPropertyException(errorMsgBuilder)
        }
    }

    fun isGreaterOrEqual(expectedValue: T) {
        if (
            actualValue == null ||
                expectedValue == null ||
                (actualValue as Comparable<T>) < expectedValue
        ) {
            val errorMsgBuilder =
                exceptionMessageBuilder
                    .setMessage(lazyMessage.invoke())
                    .setExpected("Greater or equal to $expectedValue")
                    .setActual(actualValue)
            throw InvalidPropertyException(errorMsgBuilder)
        }
    }

    fun <U> contains(expectedValue: U) {
        if (actualValue !is List<*> || !(actualValue as List<U>).contains(expectedValue)) {
            val errorMsgBuilder =
                exceptionMessageBuilder
                    .setMessage(lazyMessage.invoke())
                    .setExpected("Contain $expectedValue")
                    .setActual(actualValue)
            throw InvalidPropertyException(errorMsgBuilder)
        }
    }
}
