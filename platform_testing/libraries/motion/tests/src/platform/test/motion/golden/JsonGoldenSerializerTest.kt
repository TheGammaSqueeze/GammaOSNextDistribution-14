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
import com.google.common.truth.Expect
import com.google.common.truth.Truth.assertThat
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.motion.golden.DataPoint.Companion.notFound
import platform.test.motion.golden.DataPoint.Companion.nullValue
import platform.test.motion.golden.DataPoint.Companion.unknownType
import platform.test.motion.golden.JsonSubject.Companion.json

@RunWith(AndroidJUnit4::class)
class JsonGoldenSerializerTest {

    private val subject = JsonGoldenSerializer(DataPointTypes.allTypes)

    @get:Rule val expect: Expect = Expect.create()
    private fun assertConversions(timeSeries: TimeSeries, json: String) {
        expect
            .withMessage("serialize to JSON")
            .about(json())
            .that(subject.toJson(timeSeries))
            .isEqualTo(JSONObject(json))

        expect
            .withMessage("deserialize from JSON")
            .that(subject.fromJson(JSONObject(json)))
            .isEqualTo(timeSeries)
    }

    @Test
    fun emptyTimeSeries() {
        assertConversions(TimeSeries(listOf(), listOf()), """{"frame_ids":[],"features":[]}""")
    }

    @Test
    fun timestampFrameId_asNumber() {
        assertConversions(
            TimeSeries(listOf(TimestampFrameId(33)), listOf()),
            """{"frame_ids":[33],"features":[]}"""
        )
    }

    @Test
    fun supplementalFrameId_asString() {
        assertConversions(
            TimeSeries(listOf(SupplementalFrameId("foo")), listOf()),
            """{"frame_ids":["foo"],"features":[]}"""
        )
    }

    @Test
    fun feature_withoutDataPoint_noType() {
        assertConversions(
            TimeSeries(listOf(), listOf(Feature<Int>("foo", emptyList()))),
            """{"frame_ids":[],"features":[{"name":"foo","data_points":[]}]}"""
        )
    }

    @Test
    fun feature_withSingleDataPoint_specifiesType() {
        assertConversions(
            TimeSeries(
                listOf(TimestampFrameId(1)),
                listOf(Feature("foo", listOf(42.asDataPoint())))
            ),
            """{"frame_ids":[1],"features":[{"name":"foo","type":"int","data_points":[42]}]}"""
        )
    }

    @Test
    fun feature_withMultipleDataPoints_specifiesType() {
        assertConversions(
            TimeSeries(
                listOf(TimestampFrameId(1), TimestampFrameId(2)),
                listOf(Feature("foo", listOf(42.asDataPoint(), 43.asDataPoint())))
            ),
            """{"frame_ids":[1,2],
                "features":[{"name":"foo","type":"int","data_points":[42,43]}]}}"""
        )
    }

    @Test
    fun feature_withNullDataPoints_specifiesTypeAndHandlesNull() {
        assertConversions(
            TimeSeries(
                listOf(TimestampFrameId(1), TimestampFrameId(2)),
                listOf(Feature("foo", listOf(nullValue(), 43.asDataPoint())))
            ),
            """{"frame_ids":[1,2],
                "features":[{"name":"foo","type":"int","data_points":[null,43]}]}}"""
        )
    }

    @Test
    fun feature_withNotFoundDataPoints_specifiesTypeAndHandlesNotFound() {
        assertConversions(
            TimeSeries(
                listOf(TimestampFrameId(1), TimestampFrameId(2)),
                listOf(Feature("foo", listOf(notFound(), 43.asDataPoint())))
            ),
            """{"frame_ids":[1,2],
                "features":[{"name":"foo","type":"int","data_points":[{"type":"not_found"},43]}]}}"""
        )
    }

    @Test
    fun feature_withNullOnlyDataPoints_noType() {
        assertConversions(
            TimeSeries(
                listOf(TimestampFrameId(1)),
                listOf(Feature<Int>("foo", listOf(nullValue())))
            ),
            """{"frame_ids":[1],"features":[{"name":"foo","data_points":[null]}]}"""
        )
    }

    @Test
    fun serialize_featureWithMultipleTypesPerDataPoint_throws() {
        assertThrows(JSONException::class.java) {
            subject.toJson(
                TimeSeries(
                    listOf(TimestampFrameId(1), TimestampFrameId(2)),
                    listOf(Feature("foo", listOf(42.asDataPoint(), 42f.asDataPoint())))
                )
            )
        }
    }

    @Test
    fun serialize_featureWithUnknownDataPoints_throws() {
        assertThrows(JSONException::class.java) {
            subject.toJson(
                TimeSeries(
                    listOf(TimestampFrameId(1), TimestampFrameId(2)),
                    listOf(Feature("foo", listOf(42.asDataPoint(), unknownType())))
                )
            )
        }
    }

    @Test
    fun deserialize_featureWithUnknownType_producesUnknown() {
        val timeSeries =
            subject.fromJson(
                JSONObject(
                    """{
                    "frame_ids":[1,2],
                    "features":[{"name":"foo","type":"bar","data_points":[null,43]}]
                }"""
                )
            )

        assertThat(timeSeries)
            .isEqualTo(
                TimeSeries(
                    listOf(TimestampFrameId(1), TimestampFrameId(2)),
                    listOf(Feature<Any>("foo", listOf(nullValue(), unknownType())))
                )
            )
    }
}
