/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
package android.widget.cts

import android.os.Build
import android.platform.test.annotations.RequiresFlagsDisabled
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.widget.TextView
import androidx.test.InstrumentationRegistry
import androidx.test.filters.SmallTest
import androidx.test.runner.AndroidJUnit4
import com.android.text.flags.Flags.FLAG_USE_BOUNDS_FOR_WIDTH
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test [TextView].
 */
@SmallTest
@RunWith(AndroidJUnit4::class)
class TextViewUseBoundsForWidthTest {

    private val context = InstrumentationRegistry.getInstrumentation().getTargetContext()

    @JvmField
    @Rule
    val mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Test
    @RequiresFlagsEnabled(FLAG_USE_BOUNDS_FOR_WIDTH)
    fun testSetGetUseBoundsForWidth() {
        val textView = TextView(context)
        textView.useBoundsForWidth = true
        assertThat(textView.useBoundsForWidth).isTrue()

        textView.useBoundsForWidth = false
        assertThat(textView.useBoundsForWidth).isFalse()
    }

    @Test
    @RequiresFlagsEnabled(FLAG_USE_BOUNDS_FOR_WIDTH)
    fun testLayoutUseBoundsForTest() {
        val textView = TextView(context)
        textView.text = "Hello, World.\n This is Android."

        textView.useBoundsForWidth = true
        textView.measure(1024, 1024)
        assertThat(textView.layout.useBoundsForWidth).isTrue()

        textView.useBoundsForWidth = false
        textView.measure(1024, 1024)
        assertThat(textView.layout.useBoundsForWidth).isFalse()
    }

    @Test
    @RequiresFlagsEnabled(FLAG_USE_BOUNDS_FOR_WIDTH)
    fun testDefaultUseBoundsForTest_TargetSdk_VIC_FlagOn() {
        context.getApplicationInfo().targetSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM
        assertThat(TextView(context).useBoundsForWidth).isTrue()
    }

    @Test
    @RequiresFlagsDisabled(FLAG_USE_BOUNDS_FOR_WIDTH)
    fun testDefaultUseBoundsForTest_TargetSdk_VIC_FlagOff() {
        context.getApplicationInfo().targetSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM
        assertThat(TextView(context).useBoundsForWidth).isFalse()
    }
}
