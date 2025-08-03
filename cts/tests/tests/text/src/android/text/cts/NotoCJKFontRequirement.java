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

package android.text.cts;

import static com.google.common.truth.Truth.assertThat;

import android.content.res.Resources;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.fonts.Font;
import android.graphics.fonts.FontFamily;
import android.graphics.fonts.SystemFonts;
import android.graphics.text.PositionedGlyphs;
import android.graphics.text.TextRunShaper;
import android.icu.util.ULocale;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class NotoCJKFontRequirement {

    private static final String TEST_OPEN_DOUBLE_KEY_BRACKET = "\u300C\u300E";

    private static final Set<String> CJK_SCRIPT = new HashSet<>(Arrays.asList(
            "Hani",  // General Han character
            "Hans",  // Simplified Chinese
            "Hant",  // Tranditional Chinese
            "Hira", "Hrkt", "Jpan", "Kana",  // Japanese
            "Hang", "Kore" // Hangul
    ));

    private boolean isCJKSupported() {
        final String[] localeNames = Resources.getSystem().getStringArray(
                Resources.getSystem().getIdentifier("supported_locales", "array", "android"));
        for (String locale : localeNames) {
            final ULocale uLocale = ULocale.addLikelySubtags(ULocale.forLanguageTag(locale));
            final String script = uLocale.getScript();

            if (CJK_SCRIPT.contains(script)) {
                return true;
            }
        }

        return false;
    }

    // List of CJK font configuration.
    private static final Set<String> CJK_FONT_PSNAMES = new HashSet<>();

    static {
        CJK_FONT_PSNAMES.add("NotoSansCJKsc-Regular");
        CJK_FONT_PSNAMES.add("NotoSansCJKtc-Regular");
        CJK_FONT_PSNAMES.add("NotoSansCJKjp-Regular");
        CJK_FONT_PSNAMES.add("NotoSansCJKkr-Regular");
        CJK_FONT_PSNAMES.add("NotoSerifCJKsc-Regular");
        CJK_FONT_PSNAMES.add("NotoSerifCJKtc-Regular");
        CJK_FONT_PSNAMES.add("NotoSerifCJKjp-Regular");
        CJK_FONT_PSNAMES.add("NotoSerifCJKkr-Regular");
    };

    // Returns list of Noto CJK fonts.
    private static List<Font> getNotoCJKFonts() {
        final ArrayList<Font> cjkFonts = new ArrayList<>();

        for (Font font : SystemFonts.getAvailableFonts()) {
            String psName = FontFileTestUtil.getPostScriptName(font.getFile(), font.getTtcIndex());
            if (CJK_FONT_PSNAMES.contains(psName)) {
                cjkFonts.add(font);
            }
        }

        return cjkFonts;
    }

    @Test
    public void testContextualSpacing() {
        if (!isCJKSupported()) {
            return;  // If the device doesn't support CJK language, don't require chws font.
        }

        Paint paint = new Paint();

        // Only expect chws feature on NotoCJK fonts.
        getNotoCJKFonts().forEach(font -> {
            Typeface tf = new Typeface.CustomFallbackBuilder(
                    new FontFamily.Builder(font).build()
            ).build();

            paint.setTextSize(100f);  // Make 1em = 100px
            paint.setTypeface(tf);
            paint.setFontFeatureSettings("\"chws\" 0");

            PositionedGlyphs offGlyphs = TextRunShaper.shapeTextRun(
                    TEST_OPEN_DOUBLE_KEY_BRACKET,
                    0, TEST_OPEN_DOUBLE_KEY_BRACKET.length(),
                    0, TEST_OPEN_DOUBLE_KEY_BRACKET.length(),
                    0f, 0f, false /* LTR */, paint);

            paint.setFontFeatureSettings("\"chws\" 1");

            PositionedGlyphs onGlyphs = TextRunShaper.shapeTextRun(
                    TEST_OPEN_DOUBLE_KEY_BRACKET,
                    0, TEST_OPEN_DOUBLE_KEY_BRACKET.length(),
                    0, TEST_OPEN_DOUBLE_KEY_BRACKET.length(),
                    0f, 0f, false /* LTR */, paint);

            assertThat(onGlyphs.getGlyphX(1)).isLessThan(offGlyphs.getGlyphX(1));
        });
    }
}
