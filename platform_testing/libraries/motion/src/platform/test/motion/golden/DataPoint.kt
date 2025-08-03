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

import org.json.JSONException
import org.json.JSONObject

/**
 * Describes a type safe data point of [T] in a motion test [TimeSeries].
 *
 * [DataPoint]s include a [DataPointType] that specified how a value is written to / read from a
 * JSON golden file. Additionally, some stand-in values such as [NotFoundDataPoint] and
 * [NullDataPoint] allow to describe why [DataPoint] are absent in [TimeSeries], making the golden
 * tests more robust.
 */
sealed interface DataPoint<in T> {

    fun asJson(): Any

    companion object {
        fun <T> of(value: T?, type: DataPointType<T>): DataPoint<T> {
            return if (value != null) ValueDataPoint(value, type) else nullValue()
        }

        fun <T> notFound(): DataPoint<T> {
            @Suppress("UNCHECKED_CAST") return NotFoundDataPoint.instance as NotFoundDataPoint<T>
        }
        fun <T> nullValue(): DataPoint<T> {
            @Suppress("UNCHECKED_CAST") return NullDataPoint.instance as NullDataPoint<T>
        }

        internal fun <T> unknownType(): DataPoint<T> {
            @Suppress("UNCHECKED_CAST") return UnknownType.instance as UnknownType<T>
        }
    }
}

/**
 * Wraps a non-`null` data point value.
 *
 * @see DataPoint.of
 */
data class ValueDataPoint<T>(val value: T & Any, val type: DataPointType<T>) : DataPoint<T> {
    override fun asJson() = type.toJson(this.value)
}

/**
 * [DataPoint] stand-in to represent `null` data point values.
 *
 * @see DataPoint.of
 * @see DataPoint.nullValue
 */
class NullDataPoint<T> private constructor() : DataPoint<T> {

    override fun asJson() = JSONObject.NULL

    companion object {
        internal val instance = NullDataPoint<Any>()

        fun isNullValue(jsonValue: Any): Boolean {
            return jsonValue == JSONObject.NULL
        }
    }
}

/**
 * [DataPoint] stand-in to represent data points that could not be sampled.
 *
 * This is usually the case when the subject to sample from did not exist in a specific frame in the
 * first place.
 *
 * @see DataPoint.notFound
 */
class NotFoundDataPoint<T> private constructor() : DataPoint<T> {

    override fun asJson() = JSONObject().apply { put("type", "not_found") }

    companion object {
        internal val instance = NotFoundDataPoint<Any>()

        fun isNotFoundValue(jsonValue: Any): Boolean {
            return jsonValue is JSONObject && "not_found" == jsonValue.getString("type")
        }
    }
}

/** [DataPoint] type indicating that a values was not readable during de-serialization. */
class UnknownType<T> private constructor() : DataPoint<T> {

    override fun asJson() = throw JSONException("Feature must not contain UnknownDataPoints")

    companion object {
        fun isUnknownValue(jsonValue: Any): Boolean {
            return jsonValue is JSONObject && "not_found" == jsonValue.getString("unknown")
        }

        internal val instance = UnknownType<Any>()
    }
}
