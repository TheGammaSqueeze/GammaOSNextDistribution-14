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

package android.graphics.cts;

import static com.google.common.truth.Truth.assertThat;

import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.fonts.Font;
import android.graphics.fonts.FontFamily;
import android.graphics.fonts.FontVariationAxis;
import android.graphics.fonts.SystemFonts;
import android.graphics.text.PositionedGlyphs;
import android.graphics.text.TextRunShaper;

import java.io.File;

public class TypefaceTestUtil {
    public static File getFirstFont(String text, Paint p) {
        PositionedGlyphs glyphs = TextRunShaper.shapeTextRun(
                text, 0, text.length(), 0, text.length(), 0f, 0f, false, p);

        assertThat(glyphs.glyphCount()).isNotEqualTo(0);
        Font font = glyphs.getFont(0);
        assertThat(font.getFile()).isNotNull();
        return font.getFile();
    }

    public static Typeface getRobotoTypeface(int weight, boolean italic) {
        FontFamily.Builder builder = null;
        for (Font font : SystemFonts.getAvailableFonts()) {
            boolean useThisFont = false;
            if (font.getFile().getName().startsWith("Roboto-")) {
                for (FontVariationAxis axis : font.getAxes()) {
                    if (axis.getTag().equals("wdth") && axis.getStyleValue() == 100f) {
                        useThisFont = true;
                    }
                }
            } else if (font.getFile().getName().equals("RobotoStatic-Regular.ttf")) {
                useThisFont = true;
            }

            if (useThisFont) {
                if (builder == null) {
                    builder = new FontFamily.Builder(font);
                } else {
                    builder.addFont(font);
                }
            }
        }
        assertThat(builder).isNotNull();
        Typeface tf = new Typeface.CustomFallbackBuilder(builder.build()).build();
        assertThat(tf).isNotNull();
        return Typeface.create(tf, weight, italic);
    }
}
