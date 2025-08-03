/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.graphics.cts;

import static com.google.common.truth.Truth.assertThat;

import android.content.res.AssetManager;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class Paint_TextBoundsTest {

    /**
     * A character that has 1em x 1em size from (0, 0) origin.
     */
    private static final String CHAR_1EMx1EM = "a";

    /**
     * A character that has 2em x 2em size from (0, 0) origin.
     */
    private static final String CHAR_2EMx2EM = "b";

    /**
     * A character that has 3em x 3em size from (0, 0) origin.
     */
    private static final String CHAR_3EMx3EM = "c";

    /**
     * A character that has 2em x 2em size from (1em, 0) origin.
     */
    private static final String CHAR_2EMx2EM_LSB_1EM = "d";

    /**
     * A character that has 1em x 1em size from (0, 1em) origin.
     */
    private static final String CHAR_1EMx1EM_Y1EM_ORIGIN = "e";

    private static Paint getPaint() {
        Paint paint = new Paint();
        AssetManager am = InstrumentationRegistry.getTargetContext().getAssets();
        Typeface typeface = Typeface.createFromAsset(am, "fonts/measurement/bbox.ttf");
        paint.setTypeface(typeface);
        paint.setTextSize(100f);  // Make 1em = 100px
        return paint;
    }

    @Test
    public void testSingleChar_1em() {
        Paint p = getPaint();
        Rect r = new Rect();
        p.getTextBounds(CHAR_1EMx1EM, 0, 1, r);
        assertThat(r.left).isEqualTo(0);
        assertThat(r.top).isEqualTo(-100);
        assertThat(r.right).isEqualTo(100);
        assertThat(r.bottom).isEqualTo(0);
    }

    @Test
    public void testSingleChar_2em() {
        Paint p = getPaint();
        Rect r = new Rect();
        p.getTextBounds(CHAR_2EMx2EM, 0, 1, r);
        assertThat(r.left).isEqualTo(0);
        assertThat(r.top).isEqualTo(-200);
        assertThat(r.right).isEqualTo(200);
        assertThat(r.bottom).isEqualTo(0);
    }

    @Test
    public void testSingleChar_2em_with_lsb() {
        Paint p = getPaint();
        Rect r = new Rect();
        p.getTextBounds(CHAR_2EMx2EM_LSB_1EM, 0, 1, r);
        assertThat(r.left).isEqualTo(100);
        assertThat(r.top).isEqualTo(-200);
        assertThat(r.right).isEqualTo(300);
        assertThat(r.bottom).isEqualTo(0);
    }

    @Test
    public void testSingleChar_1em_with_y1em_origin() {
        Paint p = getPaint();
        Rect r = new Rect();
        p.getTextBounds(CHAR_1EMx1EM_Y1EM_ORIGIN, 0, 1, r);
        assertThat(r.left).isEqualTo(0);
        assertThat(r.top).isEqualTo(-200);
        assertThat(r.right).isEqualTo(100);
        assertThat(r.bottom).isEqualTo(-100);
    }

    @Test
    public void testMultiChar_1em_1em() {
        Paint p = getPaint();
        Rect r = new Rect();
        p.getTextBounds(CHAR_1EMx1EM + CHAR_1EMx1EM, 0, 2, r);
        assertThat(r.left).isEqualTo(0);
        assertThat(r.top).isEqualTo(-100);
        assertThat(r.right).isEqualTo(200);
        assertThat(r.bottom).isEqualTo(0);
    }

    @Test
    public void testMultiChar_1em_2em() {
        Paint p = getPaint();
        Rect r = new Rect();
        p.getTextBounds(CHAR_1EMx1EM + CHAR_2EMx2EM, 0, 2, r);
        assertThat(r.left).isEqualTo(0);
        assertThat(r.top).isEqualTo(-200);
        assertThat(r.right).isEqualTo(300);
        assertThat(r.bottom).isEqualTo(0);
    }

    @Test
    public void testMultiChar_3em_2em_with_lsb() {
        Paint p = getPaint();
        Rect r = new Rect();
        p.getTextBounds(CHAR_3EMx3EM + CHAR_2EMx2EM_LSB_1EM, 0, 2, r);
        assertThat(r.left).isEqualTo(0);
        assertThat(r.top).isEqualTo(-300);
        assertThat(r.right).isEqualTo(600);
        assertThat(r.bottom).isEqualTo(0);
    }

    @Test
    public void testMultiChar_1em_with_y1em() {
        Paint p = getPaint();
        Rect r = new Rect();
        p.getTextBounds(CHAR_1EMx1EM + CHAR_1EMx1EM_Y1EM_ORIGIN, 0, 2, r);
        assertThat(r.left).isEqualTo(0);
        assertThat(r.top).isEqualTo(-200);
        assertThat(r.right).isEqualTo(200);
        assertThat(r.bottom).isEqualTo(0);
    }

    @Test
    public void testMultiChar_1em_5times() {
        Paint p = getPaint();
        Rect r = new Rect();
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 5; ++i) {
            b.append(CHAR_1EMx1EM);
        }
        p.getTextBounds(b.toString(), 0, b.length(), r);
        assertThat(r.left).isEqualTo(0);
        assertThat(r.top).isEqualTo(-100);
        assertThat(r.right).isEqualTo(500);
        assertThat(r.bottom).isEqualTo(0);
    }
}
