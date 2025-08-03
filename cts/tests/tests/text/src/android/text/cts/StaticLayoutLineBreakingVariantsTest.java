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

import static android.graphics.text.LineBreaker.BREAK_STRATEGY_HIGH_QUALITY;
import static android.graphics.text.LineBreaker.BREAK_STRATEGY_SIMPLE;

import static com.android.text.flags.Flags.FLAG_PHRASE_STRICT_FALLBACK;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.text.LineBreakConfig;
import android.os.LocaleList;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.text.StaticLayout;
import android.text.TextPaint;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class StaticLayoutLineBreakingVariantsTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    private static TextPaint setupPaint(LocaleList locales) {
        // The test font covers all KATAKANA LETTERS (U+30A1..U+30FC) in Japanese and they all have
        // 1em width.
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        TextPaint paint = new TextPaint();
        paint.setTypeface(Typeface.createFromAsset(context.getAssets(),
                  "fonts/BreakVariantsTest.ttf"));
        paint.setTextSize(10.0f);  // Make 1em == 10px.
        paint.setTextLocales(locales);
        return paint;
    }

    private static StaticLayout buildLayout(String text, LocaleList locales,
            int breakStrategy, LineBreakConfig lineBreakConfig, int width) {
        StaticLayout.Builder builder = StaticLayout.Builder.obtain(text, 0, text.length(),
                setupPaint(locales), width);
        builder.setBreakStrategy(breakStrategy);
        builder.setLineBreakConfig(lineBreakConfig);
        return builder.build();
    }

    private static void assertLineBreak(String text, int breakStrategy, String locale,
            LineBreakConfig lineBreakConfig, int width, String... expected) {
        final StaticLayout layout = buildLayout(text, LocaleList.forLanguageTags(locale),
                breakStrategy, lineBreakConfig, width);
        assertEquals(expected.length, layout.getLineCount());

        int currentExpectedOffset = 0;
        for (int i = 0; i < expected.length; ++i) {
            currentExpectedOffset += expected[i].length();
            final String msg = i + "th line should be " + expected[i] + ", but it was "
                    + text.substring(layout.getLineStart(i), layout.getLineEnd(i));
            assertEquals(msg, currentExpectedOffset, layout.getLineEnd(i));
        }
    }

    // The following three test cases verifies the loose/normal/strict line break segmentations
    // works on Android. For more details see
    // http://cldr.unicode.org/development/development-process/design-proposals/specifying-text-break-variants-in-locale-ids

    // The test string is "Battery Saver" in Japanese. Here are the list of breaking points.
    //        \u30D0\u30C3\u30C6\u30EA\u30FC\u30BB\u30FC\u30D0\u30FC
    // loose :^     ^     ^     ^     ^     ^     ^     ^     ^     ^
    // strict:^           ^     ^           ^           ^           ^
    // phrase:^                                                     ^
    private static final String SAMPLE_TEXT =
            "\u30D0\u30C3\u30C6\u30EA\u30FC\u30BB\u30FC\u30D0\u30FC";

    // Another test string is "I'm also curious about new models." in Japanese.
    // Here are the list of breaking points.
    //         \u65B0\u3057\u3044\u6A5F\u7A2E\u3082\u6C17\u306B\u306A\u308B\u3057\u3002
    // loose : ^     ^     ^     ^     ^     ^     ^     ^     ^     ^     ^           ^
    // strict: ^     ^     ^     ^     ^     ^     ^     ^     ^     ^     ^           ^
    // phrase: ^                 ^                 ^           ^                       ^
    private static final String SAMPLE_TEXT2 =
            "\u65B0\u3057\u3044\u6A5F\u7A2E\u3082\u6C17\u306B\u306A\u308B\u3057\u3002";

    // Another test string is "live message" in Japanese.
    // Here are the list of breaking points
    //        \u30E9\u30A4\u30D6\u30E1\u30C3\u30BB\u30FC\u30B8
    // loose :^     ^     ^     ^     ^     ^     ^     ^     ^
    // strict:^     ^     ^     ^           ^           ^     ^
    // phrase:^                                               ^
    private static final String SAMPLE_TEXT3 = "\u30E9\u30A4\u30D6\u30E1\u30C3\u30BB\u30FC\u30B8";
    @Test
    public void testBreakVariant_loose() {
        LineBreakConfig config = new LineBreakConfig.Builder()
                .setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_LOOSE)
                .setLineBreakWordStyle(LineBreakConfig.LINE_BREAK_WORD_STYLE_NONE).build();
        assertLineBreak(SAMPLE_TEXT, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 90, SAMPLE_TEXT);
        assertLineBreak(SAMPLE_TEXT, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 80,
                "\u30D0\u30C3\u30C6\u30EA\u30FC\u30BB\u30FC\u30D0",
                "\u30FC");
        assertLineBreak(SAMPLE_TEXT, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 70,
                "\u30D0\u30C3\u30C6\u30EA\u30FC\u30BB\u30FC",
                "\u30D0\u30FC");
        assertLineBreak(SAMPLE_TEXT, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 60,
                "\u30D0\u30C3\u30C6\u30EA\u30FC\u30BB",
                "\u30FC\u30D0\u30FC");
        assertLineBreak(SAMPLE_TEXT, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 50,
                "\u30D0\u30C3\u30C6\u30EA\u30FC",
                "\u30BB\u30FC\u30D0\u30FC");
        assertLineBreak(SAMPLE_TEXT, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 40,
                "\u30D0\u30C3\u30C6\u30EA",
                "\u30FC\u30BB\u30FC\u30D0",
                "\u30FC");
        assertLineBreak(SAMPLE_TEXT, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 30,
                "\u30D0\u30C3\u30C6",
                "\u30EA\u30FC\u30BB",
                "\u30FC\u30D0\u30FC");
        assertLineBreak(SAMPLE_TEXT, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 20,
                "\u30D0\u30C3",
                "\u30C6\u30EA",
                "\u30FC\u30BB",
                "\u30FC\u30D0",
                "\u30FC");
        assertLineBreak(SAMPLE_TEXT, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 10,
                "\u30D0",
                "\u30C3",
                "\u30C6",
                "\u30EA",
                "\u30FC",
                "\u30BB",
                "\u30FC",
                "\u30D0",
                "\u30FC");
    }

    @Test
    public void testBreakVariant_loose_text2() {
        LineBreakConfig config = new LineBreakConfig.Builder()
                .setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_LOOSE)
                .setLineBreakWordStyle(LineBreakConfig.LINE_BREAK_WORD_STYLE_NONE).build();
        assertLineBreak(SAMPLE_TEXT2, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 120, SAMPLE_TEXT2);
        assertLineBreak(SAMPLE_TEXT2, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 110,
                "\u65B0\u3057\u3044\u6A5F\u7A2E\u3082\u6C17\u306B\u306A\u308B",
                "\u3057\u3002");
        assertLineBreak(SAMPLE_TEXT2, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 100,
                "\u65B0\u3057\u3044\u6A5F\u7A2E\u3082\u6C17\u306B\u306A\u308B",
                "\u3057\u3002");
        assertLineBreak(SAMPLE_TEXT2, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 90,
                "\u65B0\u3057\u3044\u6A5F\u7A2E\u3082\u6C17\u306B\u306A",
                "\u308B\u3057\u3002");
        assertLineBreak(SAMPLE_TEXT2, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 80,
                "\u65B0\u3057\u3044\u6A5F\u7A2E\u3082\u6C17\u306B",
                "\u306A\u308B\u3057\u3002");
        assertLineBreak(SAMPLE_TEXT2, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 70,
                "\u65B0\u3057\u3044\u6A5F\u7A2E\u3082\u6C17",
                "\u306B\u306A\u308B\u3057\u3002");
        assertLineBreak(SAMPLE_TEXT2, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 60,
                "\u65B0\u3057\u3044\u6A5F\u7A2E\u3082",
                "\u6C17\u306B\u306A\u308B\u3057\u3002");
        assertLineBreak(SAMPLE_TEXT2, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 50,
                "\u65B0\u3057\u3044\u6A5F\u7A2E",
                "\u3082\u6C17\u306B\u306A\u308B",
                "\u3057\u3002");
        assertLineBreak(SAMPLE_TEXT2, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 40,
                "\u65B0\u3057\u3044\u6A5F",
                "\u7A2E\u3082\u6C17\u306B",
                "\u306A\u308B\u3057\u3002");
        assertLineBreak(SAMPLE_TEXT2, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 30,
                "\u65B0\u3057\u3044",
                "\u6A5F\u7A2E\u3082",
                "\u6C17\u306B\u306A",
                "\u308B\u3057\u3002");
        assertLineBreak(SAMPLE_TEXT2, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 20,
                "\u65B0\u3057",
                "\u3044\u6A5F",
                "\u7A2E\u3082",
                "\u6C17\u306B",
                "\u306A\u308B",
                "\u3057\u3002");
        assertLineBreak(SAMPLE_TEXT2, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 10,
                "\u65B0",
                "\u3057",
                "\u3044",
                "\u6A5F",
                "\u7A2E",
                "\u3082",
                "\u6C17",
                "\u306B",
                "\u306A",
                "\u308B",
                "\u3057",
                "\u3002");
    }

    @Test
    public void testBreakVariant_strict() {
        LineBreakConfig config = new LineBreakConfig.Builder()
                .setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_STRICT)
                .setLineBreakWordStyle(LineBreakConfig.LINE_BREAK_WORD_STYLE_NONE).build();
        assertLineBreak(SAMPLE_TEXT, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 90, SAMPLE_TEXT);
        assertLineBreak(SAMPLE_TEXT, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 80,
                "\u30D0\u30C3\u30C6\u30EA\u30FC\u30BB\u30FC",
                "\u30D0\u30FC");
        assertLineBreak(SAMPLE_TEXT, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 70,
                "\u30D0\u30C3\u30C6\u30EA\u30FC\u30BB\u30FC",
                "\u30D0\u30FC");
        assertLineBreak(SAMPLE_TEXT, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 60,
                "\u30D0\u30C3\u30C6\u30EA\u30FC",
                "\u30BB\u30FC\u30D0\u30FC");
        assertLineBreak(SAMPLE_TEXT, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 50,
                "\u30D0\u30C3\u30C6\u30EA\u30FC",
                "\u30BB\u30FC\u30D0\u30FC");
        assertLineBreak(SAMPLE_TEXT, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 40,
                "\u30D0\u30C3\u30C6",
                "\u30EA\u30FC\u30BB\u30FC",
                "\u30D0\u30FC");
        assertLineBreak(SAMPLE_TEXT, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 30,
                "\u30D0\u30C3\u30C6",
                "\u30EA\u30FC",
                "\u30BB\u30FC",
                "\u30D0\u30FC");
        assertLineBreak(SAMPLE_TEXT, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 20,
                "\u30D0\u30C3",
                "\u30C6",
                "\u30EA\u30FC",
                "\u30BB\u30FC",
                "\u30D0\u30FC");
        assertLineBreak(SAMPLE_TEXT, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 10,
                "\u30D0",
                "\u30C3",
                "\u30C6",
                "\u30EA",
                "\u30FC",
                "\u30BB",
                "\u30FC",
                "\u30D0",
                "\u30FC");
    }

    @Test
    public void testBreakVariant_strict_text2() {
        LineBreakConfig config = new LineBreakConfig.Builder()
                .setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_STRICT)
                .setLineBreakWordStyle(LineBreakConfig.LINE_BREAK_WORD_STYLE_NONE).build();
        assertLineBreak(SAMPLE_TEXT2, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 120, SAMPLE_TEXT2);
        assertLineBreak(SAMPLE_TEXT2, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 110,
                "\u65B0\u3057\u3044\u6A5F\u7A2E\u3082\u6C17\u306B\u306A\u308B",
                "\u3057\u3002");
        assertLineBreak(SAMPLE_TEXT2, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 100,
                "\u65B0\u3057\u3044\u6A5F\u7A2E\u3082\u6C17\u306B\u306A\u308B",
                "\u3057\u3002");
        assertLineBreak(SAMPLE_TEXT2, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 90,
                "\u65B0\u3057\u3044\u6A5F\u7A2E\u3082\u6C17\u306B\u306A",
                "\u308B\u3057\u3002");
        assertLineBreak(SAMPLE_TEXT2, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 80,
                "\u65B0\u3057\u3044\u6A5F\u7A2E\u3082\u6C17\u306B",
                "\u306A\u308B\u3057\u3002");
        assertLineBreak(SAMPLE_TEXT2, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 70,
                "\u65B0\u3057\u3044\u6A5F\u7A2E\u3082\u6C17",
                "\u306B\u306A\u308B\u3057\u3002");
        assertLineBreak(SAMPLE_TEXT2, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 60,
                "\u65B0\u3057\u3044\u6A5F\u7A2E\u3082",
                "\u6C17\u306B\u306A\u308B\u3057\u3002");
        assertLineBreak(SAMPLE_TEXT2, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 50,
                "\u65B0\u3057\u3044\u6A5F\u7A2E",
                "\u3082\u6C17\u306B\u306A\u308B",
                "\u3057\u3002");
        assertLineBreak(SAMPLE_TEXT2, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 40,
                "\u65B0\u3057\u3044\u6A5F",
                "\u7A2E\u3082\u6C17\u306B",
                "\u306A\u308B\u3057\u3002");
        assertLineBreak(SAMPLE_TEXT2, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 30,
                "\u65B0\u3057\u3044",
                "\u6A5F\u7A2E\u3082",
                "\u6C17\u306B\u306A",
                "\u308B\u3057\u3002");
        assertLineBreak(SAMPLE_TEXT2, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 20,
                "\u65B0\u3057",
                "\u3044\u6A5F",
                "\u7A2E\u3082",
                "\u6C17\u306B",
                "\u306A\u308B",
                "\u3057\u3002");
        assertLineBreak(SAMPLE_TEXT2, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 10,
                "\u65B0",
                "\u3057",
                "\u3044",
                "\u6A5F",
                "\u7A2E",
                "\u3082",
                "\u6C17",
                "\u306B",
                "\u306A",
                "\u308B",
                "\u3057",
                "\u3002");
    }


    @Test
    public void testBreakVariant_phrase() {
        LineBreakConfig config = new LineBreakConfig.Builder()
                .setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_NONE)
                .setLineBreakWordStyle(LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE).build();
        assertLineBreak(SAMPLE_TEXT, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 90, SAMPLE_TEXT);
        assertLineBreak(SAMPLE_TEXT, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 80,
                "\u30D0\u30C3\u30C6\u30EA\u30FC\u30BB\u30FC\u30D0",
                "\u30FC");
        assertLineBreak(SAMPLE_TEXT, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 70,
                "\u30D0\u30C3\u30C6\u30EA\u30FC\u30BB\u30FC",
                "\u30D0\u30FC");
        assertLineBreak(SAMPLE_TEXT, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 60,
                "\u30D0\u30C3\u30C6\u30EA\u30FC\u30BB",
                "\u30FC\u30D0\u30FC");
        assertLineBreak(SAMPLE_TEXT, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 50,
                "\u30D0\u30C3\u30C6\u30EA\u30FC",
                "\u30BB\u30FC\u30D0\u30FC");
        assertLineBreak(SAMPLE_TEXT, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 40,
                "\u30D0\u30C3\u30C6\u30EA",
                "\u30FC\u30BB\u30FC\u30D0",
                "\u30FC");
        assertLineBreak(SAMPLE_TEXT, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 30,
                "\u30D0\u30C3\u30C6",
                "\u30EA\u30FC\u30BB",
                "\u30FC\u30D0\u30FC");
        assertLineBreak(SAMPLE_TEXT, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 20,
                "\u30D0\u30C3",
                "\u30C6\u30EA",
                "\u30FC\u30BB",
                "\u30FC\u30D0",
                "\u30FC");
        assertLineBreak(SAMPLE_TEXT, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 10,
                "\u30D0",
                "\u30C3",
                "\u30C6",
                "\u30EA",
                "\u30FC",
                "\u30BB",
                "\u30FC",
                "\u30D0",
                "\u30FC");
    }

    @RequiresFlagsEnabled(FLAG_PHRASE_STRICT_FALLBACK)
    @Test
    public void testBreakVariant_phrase_text2() {
        LineBreakConfig config = new LineBreakConfig.Builder()
                .setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_LOOSE)
                .setLineBreakWordStyle(LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE).build();
        assertLineBreak(SAMPLE_TEXT2, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 120, SAMPLE_TEXT2);
        assertLineBreak(SAMPLE_TEXT2, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 110,
                "\u65B0\u3057\u3044\u6A5F\u7A2E\u3082\u6C17\u306B",
                "\u306A\u308B\u3057\u3002");
        assertLineBreak(SAMPLE_TEXT2, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 100,
                "\u65B0\u3057\u3044\u6A5F\u7A2E\u3082\u6C17\u306B",
                "\u306A\u308B\u3057\u3002");
        assertLineBreak(SAMPLE_TEXT2, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 90,
                "\u65B0\u3057\u3044\u6A5F\u7A2E\u3082\u6C17\u306B",
                "\u306A\u308B\u3057\u3002");
        assertLineBreak(SAMPLE_TEXT2, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 80,
                "\u65B0\u3057\u3044\u6A5F\u7A2E\u3082\u6C17\u306B",
                "\u306A\u308B\u3057\u3002");
        assertLineBreak(SAMPLE_TEXT2, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 70,
                "\u65B0\u3057\u3044\u6A5F\u7A2E\u3082",
                "\u6C17\u306B\u306A\u308B\u3057\u3002");
        assertLineBreak(SAMPLE_TEXT2, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 60,
                "\u65B0\u3057\u3044\u6A5F\u7A2E\u3082",
                "\u6C17\u306B\u306A\u308B\u3057\u3002");
        assertLineBreak(SAMPLE_TEXT2, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 50,
                "\u65B0\u3057\u3044",
                "\u6A5F\u7A2E\u3082\u6C17\u306B",
                "\u306A\u308B\u3057\u3002");
        assertLineBreak(SAMPLE_TEXT2, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 40,
                "\u65B0\u3057\u3044",
                "\u6A5F\u7A2E\u3082",
                "\u6C17\u306B",
                "\u306A\u308B\u3057\u3002");
        assertLineBreak(SAMPLE_TEXT2, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 30,
                "\u65B0\u3057\u3044",
                "\u6A5F\u7A2E\u3082",
                "\u6C17\u306B",
                "\u306A\u308B",
                "\u3057\u3002");
        assertLineBreak(SAMPLE_TEXT2, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 20,
                "\u65B0\u3057",
                "\u3044",
                "\u6A5F\u7A2E",
                "\u3082",
                "\u6C17\u306B",
                "\u306A\u308B",
                "\u3057\u3002");
        assertLineBreak(SAMPLE_TEXT2, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 10,
                "\u65B0",
                "\u3057",
                "\u3044",
                "\u6A5F",
                "\u7A2E",
                "\u3082",
                "\u6C17",
                "\u306B",
                "\u306A",
                "\u308B",
                "\u3057",
                "\u3002");
    }

    @RequiresFlagsEnabled(FLAG_PHRASE_STRICT_FALLBACK)
    @Test
    public void testPhraseFallback_Strict_Greedy() {
        LineBreakConfig config = new LineBreakConfig.Builder()
                .setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_STRICT)
                .setLineBreakWordStyle(LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE).build();
        assertLineBreak(SAMPLE_TEXT3, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 80, SAMPLE_TEXT3);
        assertLineBreak(SAMPLE_TEXT3, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 70,
                "\u30E9\u30A4\u30D6\u30E1\u30C3\u30BB\u30FC",
                "\u30B8");
        assertLineBreak(SAMPLE_TEXT3, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 60,
                "\u30E9\u30A4\u30D6\u30E1\u30C3",
                "\u30BB\u30FC\u30B8");
        assertLineBreak(SAMPLE_TEXT3, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 50,
                "\u30E9\u30A4\u30D6\u30E1\u30C3",
                "\u30BB\u30FC\u30B8");
        assertLineBreak(SAMPLE_TEXT3, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 40,
                "\u30E9\u30A4\u30D6",
                "\u30E1\u30C3\u30BB\u30FC",
                "\u30B8");
        assertLineBreak(SAMPLE_TEXT3, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 30,
                "\u30E9\u30A4\u30D6",
                "\u30E1\u30C3",
                "\u30BB\u30FC\u30B8");
        assertLineBreak(SAMPLE_TEXT3, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 20,
                "\u30E9\u30A4",
                "\u30D6",
                "\u30E1\u30C3",
                "\u30BB\u30FC",
                "\u30B8");
        assertLineBreak(SAMPLE_TEXT3, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 10,
                "\u30E9",
                "\u30A4",
                "\u30D6",
                "\u30E1",
                "\u30C3",
                "\u30BB",
                "\u30FC",
                "\u30B8");
    }

    @RequiresFlagsEnabled(FLAG_PHRASE_STRICT_FALLBACK)
    @Test
    public void testPhraseFallback_Strict_Optimal() {
        LineBreakConfig config = new LineBreakConfig.Builder()
                .setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_STRICT)
                .setLineBreakWordStyle(LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE).build();
        assertLineBreak(SAMPLE_TEXT3, BREAK_STRATEGY_HIGH_QUALITY, "ja-JP", config, 80,
                SAMPLE_TEXT3);
        assertLineBreak(SAMPLE_TEXT3, BREAK_STRATEGY_HIGH_QUALITY, "ja-JP", config, 70,
                "\u30E9\u30A4\u30D6\u30E1\u30C3\u30BB\u30FC",
                "\u30B8");
        assertLineBreak(SAMPLE_TEXT3, BREAK_STRATEGY_HIGH_QUALITY, "ja-JP", config, 60,
                "\u30E9\u30A4\u30D6\u30E1\u30C3",
                "\u30BB\u30FC\u30B8");
        assertLineBreak(SAMPLE_TEXT3, BREAK_STRATEGY_HIGH_QUALITY, "ja-JP", config, 50,
                "\u30E9\u30A4\u30D6\u30E1\u30C3",
                "\u30BB\u30FC\u30B8");
        assertLineBreak(SAMPLE_TEXT3, BREAK_STRATEGY_HIGH_QUALITY, "ja-JP", config, 40,
                "\u30E9\u30A4\u30D6",
                "\u30E1\u30C3\u30BB\u30FC",
                "\u30B8");
        assertLineBreak(SAMPLE_TEXT3, BREAK_STRATEGY_HIGH_QUALITY, "ja-JP", config, 30,
                "\u30E9\u30A4\u30D6",
                "\u30E1\u30C3",
                "\u30BB\u30FC\u30B8");
        assertLineBreak(SAMPLE_TEXT3, BREAK_STRATEGY_HIGH_QUALITY, "ja-JP", config, 20,
                "\u30E9\u30A4",
                "\u30D6",
                "\u30E1\u30C3",
                "\u30BB\u30FC",
                "\u30B8");
        assertLineBreak(SAMPLE_TEXT3, BREAK_STRATEGY_HIGH_QUALITY, "ja-JP", config, 10,
                "\u30E9",
                "\u30A4",
                "\u30D6",
                "\u30E1",
                "\u30C3",
                "\u30BB",
                "\u30FC",
                "\u30B8");
    }

    @Test
    public void testPhraseFallback_Loose_Greedy() {
        LineBreakConfig config = new LineBreakConfig.Builder()
                .setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_LOOSE)
                .setLineBreakWordStyle(LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE).build();
        assertLineBreak(SAMPLE_TEXT3, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 80, SAMPLE_TEXT3);
        assertLineBreak(SAMPLE_TEXT3, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 70,
                "\u30E9\u30A4\u30D6\u30E1\u30C3\u30BB\u30FC",
                "\u30B8");
        assertLineBreak(SAMPLE_TEXT3, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 60,
                "\u30E9\u30A4\u30D6\u30E1\u30C3\u30BB",
                "\u30FC\u30B8");
        assertLineBreak(SAMPLE_TEXT3, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 50,
                "\u30E9\u30A4\u30D6\u30E1\u30C3",
                "\u30BB\u30FC\u30B8");
        assertLineBreak(SAMPLE_TEXT3, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 40,
                "\u30E9\u30A4\u30D6\u30E1",
                "\u30C3\u30BB\u30FC\u30B8");
        assertLineBreak(SAMPLE_TEXT3, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 30,
                "\u30E9\u30A4\u30D6",
                "\u30E1\u30C3\u30BB",
                "\u30FC\u30B8");
        assertLineBreak(SAMPLE_TEXT3, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 20,
                "\u30E9\u30A4",
                "\u30D6\u30E1",
                "\u30C3\u30BB",
                "\u30FC\u30B8");
        assertLineBreak(SAMPLE_TEXT3, BREAK_STRATEGY_SIMPLE, "ja-JP", config, 10,
                "\u30E9",
                "\u30A4",
                "\u30D6",
                "\u30E1",
                "\u30C3",
                "\u30BB",
                "\u30FC",
                "\u30B8");
    }

    @Test
    public void testPhraseFallback_Loose_Optimal() {
        LineBreakConfig config = new LineBreakConfig.Builder()
                .setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_LOOSE)
                .setLineBreakWordStyle(LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE).build();
        assertLineBreak(SAMPLE_TEXT3, BREAK_STRATEGY_HIGH_QUALITY, "ja-JP", config, 80,
                SAMPLE_TEXT3);
        assertLineBreak(SAMPLE_TEXT3, BREAK_STRATEGY_HIGH_QUALITY, "ja-JP", config, 70,
                "\u30E9\u30A4\u30D6\u30E1\u30C3\u30BB\u30FC",
                "\u30B8");
        assertLineBreak(SAMPLE_TEXT3, BREAK_STRATEGY_HIGH_QUALITY, "ja-JP", config, 60,
                "\u30E9\u30A4\u30D6\u30E1\u30C3\u30BB",
                "\u30FC\u30B8");
        assertLineBreak(SAMPLE_TEXT3, BREAK_STRATEGY_HIGH_QUALITY, "ja-JP", config, 50,
                "\u30E9\u30A4\u30D6\u30E1\u30C3",
                "\u30BB\u30FC\u30B8");
        assertLineBreak(SAMPLE_TEXT3, BREAK_STRATEGY_HIGH_QUALITY, "ja-JP", config, 40,
                "\u30E9\u30A4\u30D6\u30E1",
                "\u30C3\u30BB\u30FC\u30B8");
        assertLineBreak(SAMPLE_TEXT3, BREAK_STRATEGY_HIGH_QUALITY, "ja-JP", config, 30,
                "\u30E9\u30A4\u30D6",
                "\u30E1\u30C3\u30BB",
                "\u30FC\u30B8");
        assertLineBreak(SAMPLE_TEXT3, BREAK_STRATEGY_HIGH_QUALITY, "ja-JP", config, 20,
                "\u30E9\u30A4",
                "\u30D6\u30E1",
                "\u30C3\u30BB",
                "\u30FC\u30B8");
        assertLineBreak(SAMPLE_TEXT3, BREAK_STRATEGY_HIGH_QUALITY, "ja-JP", config, 10,
                "\u30E9",
                "\u30A4",
                "\u30D6",
                "\u30E1",
                "\u30C3",
                "\u30BB",
                "\u30FC",
                "\u30B8");
    }
}
