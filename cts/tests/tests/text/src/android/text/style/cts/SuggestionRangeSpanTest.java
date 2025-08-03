/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.text.style.cts;

import static org.junit.Assert.assertEquals;

import android.os.Parcel;
import android.text.TextPaint;
import android.text.style.SuggestionRangeSpan;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test {@link SuggestionRangeSpan}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class SuggestionRangeSpanTest {
    @Test
    public void testConstructor() {
        final SuggestionRangeSpan span = new SuggestionRangeSpan();
        assertEquals(0, span.getBackgroundColor());
    }

    @Test
    public void testSerializationDeserialization() {
        final SuggestionRangeSpan original = new SuggestionRangeSpan();
        original.setBackgroundColor(1);
        Parcel parcel = null;
        final SuggestionRangeSpan clone;
        try {
            parcel = Parcel.obtain();
            original.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            clone = SuggestionRangeSpan.CREATOR.createFromParcel(parcel);
        } finally {
            if (parcel != null) {
                parcel.recycle();
            }
        }
        assertEquals(original.getBackgroundColor(), clone.getBackgroundColor());
    }

    @Test
    public void testSetAndGetBackgroundColor() {
        final SuggestionRangeSpan span = new SuggestionRangeSpan();
        assertEquals(0, span.getBackgroundColor());

        span.setBackgroundColor(1);
        assertEquals(1, span.getBackgroundColor());
    }

    @Test
    public void testUpdateDrawState() {
        final SuggestionRangeSpan span = new SuggestionRangeSpan();
        span.setBackgroundColor(1);
        TextPaint tp = new TextPaint();
        span.updateDrawState(tp);
        assertEquals(1, tp.bgColor);
    }
}

