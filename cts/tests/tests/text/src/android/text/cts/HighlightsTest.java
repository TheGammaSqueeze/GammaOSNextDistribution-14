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

package android.text.cts;

import static com.google.common.truth.Truth.assertThat;

import android.graphics.Paint;
import android.text.Highlights;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class HighlightsTest {

    @Test
    public void buildEmpty() {
        Highlights highlights = new Highlights.Builder().build();
        assertThat(highlights).isNotNull();
        assertThat(highlights.getSize()).isEqualTo(0);
    }

    @Test
    public void build() {
        Paint paint = new Paint();
        Highlights highlights = new Highlights.Builder()
                .addRange(paint, 1, 2)
                .build();
        assertThat(highlights.getSize()).isEqualTo(1);
        assertThat(highlights.getPaint(0)).isSameInstanceAs(paint);
        assertThat(highlights.getRanges(0)).isEqualTo(new int[] { 1, 2 });
    }

    @Test
    public void build2() {
        Paint paint = new Paint();
        Paint paint2 = new Paint();
        Highlights highlights = new Highlights.Builder()
                .addRange(paint, 1, 2)
                .addRanges(paint2, 1, 2, 3, 4)
                .build();
        assertThat(highlights.getSize()).isEqualTo(2);
        assertThat(highlights.getPaint(0)).isSameInstanceAs(paint);
        assertThat(highlights.getRanges(0)).isEqualTo(new int[] { 1, 2 });
        assertThat(highlights.getPaint(1)).isSameInstanceAs(paint2);
        assertThat(highlights.getRanges(1)).isEqualTo(new int[] { 1, 2, 3, 4 });
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void access_OOB_Paint() {
        new Highlights.Builder().addRanges(new Paint(), 1, 2).build().getPaint(1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void access_OOB_Ranges() {
        new Highlights.Builder().addRanges(new Paint(), 1, 2).build().getRanges(1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidArg_oddNumberRange() {
        new Highlights.Builder().addRanges(new Paint(), 1, 2, 3);
    }

    @Test(expected = NullPointerException.class)
    public void invalidArg_nullPaint_addRange() {
        new Highlights.Builder().addRange(null, 1, 2);
    }

    @Test(expected = NullPointerException.class)
    public void invalidArg_nullPaint_addRanges() {
        new Highlights.Builder().addRanges(null, 1, 2, 3, 4);
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidArg_reversedRange_addRange() {
        new Highlights.Builder().addRange(null, 2, 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidArg_reversedRange_addRanges() {
        new Highlights.Builder().addRanges(null, 2, 1, 3, 4);
    }
}
