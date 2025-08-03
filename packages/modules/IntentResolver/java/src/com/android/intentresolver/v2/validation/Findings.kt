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

import android.util.Log
import com.android.intentresolver.v2.validation.Importance.CRITICAL
import com.android.intentresolver.v2.validation.Importance.WARNING
import kotlin.reflect.KClass

sealed interface Finding {
    val importance: Importance
    val message: String
}

enum class Importance {
    CRITICAL,
    WARNING,
}

val Finding.logcatPriority
    get() =
        when (importance) {
            CRITICAL -> Log.ERROR
            WARNING -> Log.WARN
        }

fun Finding.log(tag: String) {
    Log.println(logcatPriority, tag, message)
}

private fun formatMessage(key: String? = null, msg: String) = buildString {
    key?.also { append("['$key']: ") }
    append(msg)
}

data class IgnoredValue(
    val key: String,
    val reason: String,
) : Finding {
    override val importance = WARNING

    override val message: String
        get() = formatMessage(key, "Ignored. $reason")
}

data class NoValue(
    val key: String,
    override val importance: Importance,
    val allowedType: KClass<*>,
) : Finding {

    override val message: String
        get() =
            formatMessage(
                key,
                if (importance == CRITICAL) {
                    "expected value of ${allowedType.simpleName}, " + "but no value was present"
                } else {
                    "no ${allowedType.simpleName} value present"
                }
            )
}

data class WrongElementType(
    val key: String,
    override val importance: Importance,
    val container: KClass<*>,
    val actualType: KClass<*>,
    val expectedType: KClass<*>
) : Finding {
    override val message: String
        get() =
            formatMessage(
                key,
                "${container.simpleName} expected with elements of " +
                    "${expectedType.simpleName} " +
                    "but found ${actualType.simpleName} values instead"
            )
}

data class ValueIsWrongType(
    val key: String,
    override val importance: Importance,
    val actualType: KClass<*>,
    val allowedTypes: List<KClass<*>>,
) : Finding {

    override val message: String
        get() =
            formatMessage(
                key,
                "expected value of ${allowedTypes.map(KClass<*>::simpleName)} " +
                    "but was ${actualType.simpleName}"
            )
}

data class UncaughtException(val thrown: Throwable, val key: String? = null) : Finding {
    override val importance: Importance
        get() = CRITICAL
    override val message: String
        get() =
            formatMessage(
                key,
                "An unhandled exception was caught during validation: " +
                    thrown.stackTraceToString()
            )
}
