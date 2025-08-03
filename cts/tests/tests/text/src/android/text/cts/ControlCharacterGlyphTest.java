/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.text.cts;

import static com.google.common.truth.Truth.assertThat;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ControlCharacterGlyphTest {

    @Test
    public void testLineBreakDrawsNothing() {
        Bitmap bmp = Bitmap.createBitmap(200, 200, Bitmap.Config.ALPHA_8);

        Canvas canvas = new Canvas(bmp);
        canvas.drawText("\n\n\n", 50, 100, new Paint());
        Bitmap emptyBitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ALPHA_8);

        assertThat(bmp.sameAs(emptyBitmap)).isTrue();
    }
}

