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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.motion.golden.DataPoint.Companion.notFound
import platform.test.motion.golden.DataPoint.Companion.nullValue

@RunWith(AndroidJUnit4::class)
class DataPointTypeTest {
    data class Native(val id: String)

    private val subject =
        DataPointType(
            "native",
            { jsonValue ->
                jsonToValueInvocations++
                if (jsonValue is String) Native(jsonValue) else throw UnknownTypeException()
            }
        ) {
            valueToJsonInvocations++
            it.id
        }
    private var jsonToValueInvocations = 0
    private var valueToJsonInvocations = 0

    @Test
    fun makeDataPoint_ofNull_createsNullValue() {
        assertThat(subject.makeDataPoint(null)).isEqualTo(nullValue<Native>())
    }

    @Test
    fun makeDataPoint_ofInstance_createsValueDataPoint() {
        val dataPoint = subject.makeDataPoint(Native("one"))

        assertThat(dataPoint).isInstanceOf(ValueDataPoint::class.java)
        val valueDataPoint = dataPoint as ValueDataPoint
        assertThat(valueDataPoint.value).isSameInstanceAs(Native("one"))
        assertThat(valueDataPoint.type).isSameInstanceAs(subject)
    }

    @Test
    fun fromJson_ofNull_returnsNullValue() {
        val dataPoint = subject.fromJson(JSONObject.NULL)

        assertThat(dataPoint).isEqualTo(nullValue<Native>())
        assertThat(jsonToValueInvocations).isEqualTo(0)
    }

    @Test
    fun fromJson_ofNotFound_returnsNotFound() {
        val dataPoint = subject.fromJson(NotFoundDataPoint.instance.asJson())

        assertThat(dataPoint).isEqualTo(notFound<Native>())
        assertThat(jsonToValueInvocations).isEqualTo(0)
    }

    @Test
    fun fromJson_ofValue_returnsValueDataPoint() {
        val dataPoint = subject.fromJson("one")

        assertThat(dataPoint).isInstanceOf(ValueDataPoint::class.java)
        val valueDataPoint = dataPoint as ValueDataPoint
        assertThat(valueDataPoint.value).isEqualTo(Native("one"))
        assertThat(valueDataPoint.type).isSameInstanceAs(subject)
        assertThat(jsonToValueInvocations).isEqualTo(1)
    }

    @Test
    fun toJson_delegatesToConverter() {
        val json = subject.toJson(Native("one"))

        assertThat(json).isEqualTo("one")
        assertThat(valueToJsonInvocations).isEqualTo(1)
    }
}
