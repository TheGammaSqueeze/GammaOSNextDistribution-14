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

package android.content.res.cts

import android.content.res.Flags
import android.content.res.FontScaleConverter
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@RequiresFlagsEnabled(Flags.FLAG_FONT_SCALE_CONVERTER_PUBLIC)
class FontScaleConverterTest {

    @get:Rule val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @SmallTest
    @Test
    fun scale200IsTwiceAtSmallSizes() {
        val table = FontScaleConverter.forScale(2F)!!
        assertThat(table.convertSpToDp(1F)).isWithin(CONVERSION_TOLERANCE).of(2f)
        assertThat(table.convertSpToDp(8F)).isWithin(CONVERSION_TOLERANCE).of(16f)
        assertThat(table.convertSpToDp(10F)).isWithin(CONVERSION_TOLERANCE).of(20f)
        assertThat(table.convertSpToDp(5F)).isWithin(CONVERSION_TOLERANCE).of(10f)
        assertThat(table.convertSpToDp(0F)).isWithin(CONVERSION_TOLERANCE).of(0f)
    }

    @SmallTest
    @Test
    fun scale200IsNonlinearAtLargeSizes() {
        val table = FontScaleConverter.forScale(2F)!!
        assertThat(table.convertSpToDp(24F)).isAtMost(36F)
        assertThat(table.convertSpToDp(30F)).isAtMost(38F)
        assertThat(table.convertSpToDp(45F)).isAtMost(50F)
        assertThat(table.convertSpToDp(50F)).isAtMost(60F)
        assertThat(table.convertSpToDp(100F)).isWithin(CONVERSION_TOLERANCE).of(100F)
    }

    @SmallTest
    @Test
    fun missingLookupTablePastEnd_returnsLinear() {
        val table = FontScaleConverter.forScale(3F)!!
        assertThat(table.convertSpToDp(1F)).isWithin(CONVERSION_TOLERANCE).of(3f)
        assertThat(table.convertSpToDp(8F)).isWithin(CONVERSION_TOLERANCE).of(24f)
        assertThat(table.convertSpToDp(10F)).isWithin(CONVERSION_TOLERANCE).of(30f)
        assertThat(table.convertSpToDp(5F)).isWithin(CONVERSION_TOLERANCE).of(15f)
        assertThat(table.convertSpToDp(0F)).isWithin(CONVERSION_TOLERANCE).of(0f)
        assertThat(table.convertSpToDp(50F)).isWithin(CONVERSION_TOLERANCE).of(150f)
        assertThat(table.convertSpToDp(100F)).isWithin(CONVERSION_TOLERANCE).of(300f)
    }

    @SmallTest
    @Test
    fun missingLookupTable199_returnsInterpolated() {
        val table = FontScaleConverter.forScale(1.9999F)!!
        assertThat(table.convertSpToDp(1F)).isWithin(CONVERSION_TOLERANCE).of(2f)
        assertThat(table.convertSpToDp(8F)).isWithin(CONVERSION_TOLERANCE).of(16f)
        assertThat(table.convertSpToDp(10F)).isWithin(CONVERSION_TOLERANCE).of(20f)
        assertThat(table.convertSpToDp(5F)).isWithin(CONVERSION_TOLERANCE).of(10f)
        assertThat(table.convertSpToDp(0F)).isWithin(CONVERSION_TOLERANCE).of(0f)
    }

    @SmallTest
    @Test
    fun missingLookupTable160_returnsInterpolated() {
        val table = FontScaleConverter.forScale(1.6F)!!
        assertThat(table.convertSpToDp(1F)).isWithin(CONVERSION_TOLERANCE).of(1f * 1.6F)
        assertThat(table.convertSpToDp(8F)).isWithin(CONVERSION_TOLERANCE).of(8f * 1.6F)
        assertThat(table.convertSpToDp(10F)).isWithin(CONVERSION_TOLERANCE).of(10f * 1.6F)
        assertThat(table.convertSpToDp(20F)).isLessThan(20f * 1.6F)
        assertThat(table.convertSpToDp(100F)).isLessThan(100f * 1.6F)
        assertThat(table.convertSpToDp(5F)).isWithin(CONVERSION_TOLERANCE).of(5f * 1.6F)
        assertThat(table.convertSpToDp(0F)).isWithin(CONVERSION_TOLERANCE).of(0f)
    }

    @SmallTest
    @Test
    fun missingLookupTableNegativeReturnsNull() {
        assertThat(FontScaleConverter.forScale(-1F)).isNull()
    }

    @SmallTest
    @Test
    fun unnecessaryFontScalesReturnsNull() {
        assertThat(FontScaleConverter.forScale(0F)).isNull()
        assertThat(FontScaleConverter.forScale(1.0F)).isNull()
        assertThat(FontScaleConverter.forScale(0.85F)).isNull()
    }

    @SmallTest
    @Test
    fun testIsNonLinearFontScalingActive() {
        assertThat(FontScaleConverter.isNonLinearFontScalingActive(1f)).isFalse()
        assertThat(FontScaleConverter.isNonLinearFontScalingActive(0f)).isFalse()
        assertThat(FontScaleConverter.isNonLinearFontScalingActive(-1f)).isFalse()
        assertThat(FontScaleConverter.isNonLinearFontScalingActive(0.85f)).isFalse()
        assertThat(FontScaleConverter.isNonLinearFontScalingActive(1.02f)).isFalse()
        assertThat(FontScaleConverter.isNonLinearFontScalingActive(1.10f)).isTrue()
        assertThat(FontScaleConverter.isNonLinearFontScalingActive(1.15f)).isTrue()
        assertThat(FontScaleConverter.isNonLinearFontScalingActive(1.1499999f))
                .isTrue()
        assertThat(FontScaleConverter.isNonLinearFontScalingActive(1.5f)).isTrue()
        assertThat(FontScaleConverter.isNonLinearFontScalingActive(2f)).isTrue()
        assertThat(FontScaleConverter.isNonLinearFontScalingActive(3f)).isTrue()
    }

    companion object {
        private const val CONVERSION_TOLERANCE = 0.25f
    }
}
