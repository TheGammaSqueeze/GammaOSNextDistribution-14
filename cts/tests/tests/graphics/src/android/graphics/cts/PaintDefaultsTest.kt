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
package android.graphics.cts

import android.graphics.Paint
import android.platform.test.annotations.RequiresFlagsDisabled
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.filters.SmallTest
import androidx.test.runner.AndroidJUnit4
import com.android.text.flags.Flags
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class PaintDefaultsTest {

    @Rule
    @JvmField
    val mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @RequiresFlagsEnabled(Flags.FLAG_DEPRECATE_UI_FONTS)
    @Test
    fun testElegantTextDefaultEnabled() {
        assertThat(Paint().isElegantTextHeight).isTrue()
    }

    @RequiresFlagsDisabled(Flags.FLAG_DEPRECATE_UI_FONTS)
    @Test
    fun testElegantTextDefaultDisabled() {
        assertThat(Paint().isElegantTextHeight).isFalse()
    }
}
