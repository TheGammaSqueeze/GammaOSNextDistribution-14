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
package android.graphics.fonts

import android.graphics.Paint
import android.graphics.Typeface
import androidx.test.filters.SmallTest
import androidx.test.runner.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class GB18030Test {
    @Test
    fun testLevel2CodePoints() {
        // According to the release note of SourceHans ver 2.002, the U+4DB6 was added for
        // supporting GB18030-2022.
        // https://github.com/notofonts/noto-cjk/blob/main/Serif/NEWS.md
        val paint = Paint().apply {
            typeface = Typeface.create("serif", Typeface.NORMAL)
        }
        assertThat(paint.hasGlyph("\u4DB6")).isTrue()
    }
}
