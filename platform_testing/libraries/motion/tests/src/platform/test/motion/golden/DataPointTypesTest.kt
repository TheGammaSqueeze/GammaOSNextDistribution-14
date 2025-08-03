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
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataPointTypesTest {

    @Test
    fun boolean_fromJson_native() {
        assertThat(DataPointTypes.boolean.fromJson(true)).isEqualTo(true.asDataPoint())
    }

    @Test
    fun boolean_fromJson_trueString() {
        assertThat(DataPointTypes.boolean.fromJson("true")).isEqualTo(true.asDataPoint())
    }

    @Test
    fun boolean_fromJson_falseString() {
        assertThat(DataPointTypes.boolean.fromJson("false")).isEqualTo(false.asDataPoint())
    }

    @Test
    fun boolean_fromJson_unknown() {
        assertThat(DataPointTypes.boolean.fromJson(Something("bar")))
            .isEqualTo(DataPoint.unknownType<Boolean>())
    }

    @Test
    fun float_fromJson_native() {
        assertThat(DataPointTypes.float.fromJson(1.5f)).isEqualTo(1.5f.asDataPoint())
    }

    @Test
    fun float_fromJson_number() {
        assertThat(DataPointTypes.float.fromJson(1.5)).isEqualTo(1.5f.asDataPoint())
    }

    @Test
    fun float_fromJson_floatString() {
        assertThat(DataPointTypes.float.fromJson("1.5")).isEqualTo(1.5f.asDataPoint())
    }

    @Test
    fun float_fromJson_unparseableString() {
        assertThat(DataPointTypes.float.fromJson("foo")).isEqualTo(DataPoint.unknownType<Float>())
    }

    @Test
    fun float_fromJson_unknown() {
        assertThat(DataPointTypes.float.fromJson(Something("bar")))
            .isEqualTo(DataPoint.unknownType<Float>())
    }

    @Test
    fun int_fromJson_native() {
        assertThat(DataPointTypes.int.fromJson(1)).isEqualTo(1.asDataPoint())
    }

    @Test
    fun int_fromJson_number() {
        assertThat(DataPointTypes.int.fromJson(1.5)).isEqualTo(1.asDataPoint())
    }

    @Test
    fun int_fromJson_floatString() {
        assertThat(DataPointTypes.int.fromJson("1")).isEqualTo(1.asDataPoint())
    }

    @Test
    fun int_fromJson_unparseableString() {
        assertThat(DataPointTypes.int.fromJson("foo")).isEqualTo(DataPoint.unknownType<Float>())
    }

    @Test
    fun int_fromJson_unknown() {
        assertThat(DataPointTypes.int.fromJson(Something("bar")))
            .isEqualTo(DataPoint.unknownType<Float>())
    }

    @Test
    fun string_fromJson_native() {
        assertThat(DataPointTypes.string.fromJson("foo")).isEqualTo("foo".asDataPoint())
    }

    @Test
    fun string_fromJson_converts_toString() {
        assertThat(DataPointTypes.string.fromJson(Something("bar")))
            .isEqualTo("Something(string=bar)".asDataPoint())
    }

    data class Something(val string: String)
}
