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

/** Golden value type to convert to/from JSON. */
class DataPointType<T>(
    /** Type identifier written to the JSON, to support de-serialization of the values again. */
    val typeName: String,
    /**
     * Function to convert the [jsonValue] to a native [T].
     *
     * @throws UnknownTypeException if [jsonValue] cannot be converted to [T].
     */
    private val jsonToValue: (jsonValue: Any) -> T,
    private val valueToJson: (T) -> Any
) {
    fun makeDataPoint(nativeValue: T?): DataPoint<T> {
        return DataPoint.of(nativeValue, this)
    }

    internal fun fromJson(jsonValue: Any): DataPoint<T> {
        return when {
            NullDataPoint.isNullValue(jsonValue) -> DataPoint.nullValue()
            NotFoundDataPoint.isNotFoundValue(jsonValue) -> DataPoint.notFound()
            else ->
                try {
                    makeDataPoint(jsonToValue(jsonValue))
                } catch (e: UnknownTypeException) {
                    DataPoint.unknownType()
                }
        }
    }

    fun toJson(value: T): Any = valueToJson(value)
}

/** Signals that a JSON value cannot be deserialized by a [DataPointType]. */
class UnknownTypeException : Exception()
