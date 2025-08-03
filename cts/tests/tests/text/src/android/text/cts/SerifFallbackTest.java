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

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.text.PositionedGlyphs;
import android.graphics.text.TextRunShaper;
import android.icu.util.ULocale;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class SerifFallbackTest {

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

    private boolean isWearDevice() {
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        return ctx.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH);
    }

    @Test
    public void testSerifFallback() {
        if (isWearDevice()) {
            return;  // Does not require serif font on Wear device.
        }

        if (!isCJKSupported()) {
            return;  // If the device doesn't support CJK language, don't require serif font.
        }

        final String testString = "\u9AA8";  // Han character used in Japanese, Chinese, Korean
        final Paint paint = new Paint();
        final Locale[] locales = new Locale[] {
                Locale.JAPAN, Locale.SIMPLIFIED_CHINESE, Locale.TRADITIONAL_CHINESE, Locale.KOREA
        };

        for (Locale locale : locales) {
            paint.setTextLocale(locale);
            paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            PositionedGlyphs sansSerifGlyphs = TextRunShaper.shapeTextRun(
                    testString, 0, testString.length(), 0, testString.length(), 0, 0, false, paint);

            assertThat(sansSerifGlyphs.glyphCount()).isEqualTo(1);

            paint.setTypeface(Typeface.create("serif", Typeface.NORMAL));
            PositionedGlyphs serifGlyphs = TextRunShaper.shapeTextRun(
                    testString, 0, testString.length(), 0, testString.length(), 0, 0, false, paint);

            assertThat(serifGlyphs.glyphCount()).isEqualTo(1);

            // The font used for CJK character of serif should be different from sans-serif.
            assertThat(serifGlyphs.getFont(0)).isNotEqualTo(sansSerifGlyphs.getFont(0));
        }

    }

}
