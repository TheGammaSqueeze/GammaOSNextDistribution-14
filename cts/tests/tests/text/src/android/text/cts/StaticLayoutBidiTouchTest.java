/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static org.junit.Assert.assertNotNull;

import android.content.res.AssetManager;
import android.graphics.Typeface;
import android.platform.test.annotations.AsbSecurityTest;
import android.text.Layout;
import android.text.SpannableString;
import android.text.StaticLayout;
import android.text.TextDirectionHeuristics;
import android.text.TextPaint;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class StaticLayoutBidiTouchTest {

    @Test
    @AsbSecurityTest(cveBugId = 193849901)
    public void touchOffsetTest() {
        AssetManager am = InstrumentationRegistry.getInstrumentation().getContext().getAssets();
        TextPaint p = new TextPaint();
        Typeface tf = new Typeface.Builder(am, "fonts/StaticLayoutTouchLocation.ttf").build();
        assertNotNull(tf);
        p.setTypeface(tf);
        p.setTextSize(10f);

        CharSequence text = new SpannableString("\u0627\u0020\u2066\u0020\u0061");
        int width = 10;
        Layout layout = StaticLayout.Builder.obtain(text, 0, text.length(), p, width)
                .setTextDirection(TextDirectionHeuristics.RTL)
                .build();

        // Accessing from left with 20 dividing points.
        for (int i = 0; i < layout.getLineCount(); ++i) {
            for (float w = 0; w < layout.getLineWidth(i); w += layout.getLineWidth(i) / 20f) {
                layout.getOffsetForHorizontal(i, w);
            }
        }
    }
}
