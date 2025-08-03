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
package android.text.cts

import android.content.Context
import android.graphics.Typeface
import android.graphics.text.LineBreakConfig
import android.graphics.text.LineBreaker
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.text.StaticLayout
import android.text.TextPaint
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import com.android.text.flags.Flags
import java.util.Locale
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class StaticLayoutLineBreakAutoTest {
    @Rule
    @JvmField
    val mCheckFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    companion object {
        // A test string for Japanese. The meaning is that "Today is a sunny day."
        // The expected line break of phrase and non-phrase cases are:
        //             \u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002
        //     Phrase: ^                 ^                             ^
        // Non-Phrase: ^     ^     ^     ^     ^     ^     ^     ^     ^
        private val JP_TEXT = "\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002"

        // A test string for Korean. The meaning is that "I want to eat breakfast."
        // The phrase based line break breaks at spaces, non-phrase based line break breaks at
        // grapheme.
        private val KO_TEXT = "\uC544\uCE68\uBC25\uC744\u0020\uBA39\uACE0\u0020" +
                "\uC2F6\uC2B5\uB2C8\uB2E4\u002E"

        // A test string for Japanese. The meaning is a name of snack.
        //          \u30B5\u30FC\u30BF\u30FC\u30A2\u30F3\u30C0\u30AE\u30FC
        //  Normal: ^     ^     ^     ^     ^     ^     ^     ^     ^     ^
        //  Strict: ^           ^           ^     ^     ^     ^           ^
        private val JP_TEXT_FOR_STRICT = "\u30B5\u30FC\u30BF\u30FC\u30A2\u30F3\u30C0\u30AE\u30FC"

        private val JP_FONT = "fonts/Hiragana.ttf"
        private val KO_FONT = "fonts/Hangul.ttf"

        private enum class Language { Japanese, Korean }

        private fun setupPaint(lang: Language): TextPaint {
            val context: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
            val paint = TextPaint()

            if (lang == Language.Japanese) {
                paint.typeface = Typeface.createFromAsset(context.assets, JP_FONT)
                paint.textLocale = Locale.JAPANESE
            } else {
                paint.typeface = Typeface.createFromAsset(context.assets, KO_FONT)
                paint.textLocale = Locale.KOREAN
            }
            paint.textSize = 10.0f // Make 1em == 10px.
            return paint
        }

        private fun buildLayout(
                text: String,
                lang: Language,
                lineBreakConfig: LineBreakConfig,
                width: Int
        ): StaticLayout {
            val builder = StaticLayout.Builder.obtain(
                text,
                0,
                text.length,
                    setupPaint(lang),
                width
            )
            builder.setBreakStrategy(LineBreaker.BREAK_STRATEGY_HIGH_QUALITY)
            builder.setLineBreakConfig(lineBreakConfig)
            return builder.build()
        }

        private fun assertLineBreak(
            text: String,
            lang: Language,
                                    lineBreakConfig: LineBreakConfig,
            width: Int,
            vararg expected: String
        ) {
            val layout = buildLayout(text, lang, lineBreakConfig, width)
            Assert.assertEquals(expected.size.toLong(), layout.lineCount.toLong())
            var currentExpectedOffset = 0
            for (i in expected.indices) {
                currentExpectedOffset += expected[i].length
                val msg = (i.toString() + "th line should be " + expected[i] + ", but it was " +
                        text.substring(layout.getLineStart(i), layout.getLineEnd(i)))
                Assert.assertEquals(
                    msg,
                    currentExpectedOffset.toLong(),
                    layout.getLineEnd(i).toLong()
                )
            }
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_WORD_STYLE_AUTO)
    @Test
    fun testAuto_JapaneseNone() {
        val config = LineBreakConfig.Builder()
                .setLineBreakWordStyle(LineBreakConfig.LINE_BREAK_WORD_STYLE_NONE)
                .build()

        assertLineBreak(
            JP_TEXT.repeat(1),
            Language.Japanese,
            config,
            100,
            "\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002"
        )

        assertLineBreak(
            JP_TEXT.repeat(2),
            Language.Japanese,
            config,
            100,
            "\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002\u672C\u65E5",
            "\u306F\u6674\u5929\u306A\u308A\u3002"
        )

        assertLineBreak(
            JP_TEXT.repeat(3),
            Language.Japanese,
            config,
            100,
            "\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002\u672C\u65E5",
            "\u306F\u6674\u5929\u306A\u308A\u3002\u672C\u65E5\u306F\u6674",
            "\u5929\u306A\u308A\u3002"
        )

        assertLineBreak(
            JP_TEXT.repeat(4),
            Language.Japanese,
            config,
            100,
            "\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002\u672C\u65E5",
            "\u306F\u6674\u5929\u306A\u308A\u3002\u672C\u65E5\u306F\u6674",
            "\u5929\u306A\u308A\u3002\u672C\u65E5\u306F\u6674\u5929\u306A",
            "\u308A\u3002"
        )

        assertLineBreak(
            JP_TEXT.repeat(5),
            Language.Japanese,
            config,
            100,
            "\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002\u672C\u65E5",
            "\u306F\u6674\u5929\u306A\u308A\u3002\u672C\u65E5\u306F\u6674",
            "\u5929\u306A\u308A\u3002\u672C\u65E5\u306F\u6674\u5929\u306A",
            "\u308A\u3002\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002"
        )

        assertLineBreak(JP_TEXT.repeat(6), Language.Japanese, config, 100,
            "\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002\u672C\u65E5",
            "\u306F\u6674\u5929\u306A\u308A\u3002\u672C\u65E5\u306F\u6674",
            "\u5929\u306A\u308A\u3002\u672C\u65E5\u306F\u6674\u5929\u306A",
            "\u308A\u3002\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002",
            "\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002"
        )
    }

    @RequiresFlagsEnabled(Flags.FLAG_WORD_STYLE_AUTO)
    @Test
    fun testAuto_JapanesePhrase() {
        val config = LineBreakConfig.Builder()
                .setLineBreakWordStyle(LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE)
                .build()

        assertLineBreak(
            JP_TEXT.repeat(1),
            Language.Japanese,
            config,
            100,
            "\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002"
        )

        assertLineBreak(
            JP_TEXT.repeat(2),
            Language.Japanese,
            config,
            100,
            "\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002",
            "\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002"
        )

        assertLineBreak(
            JP_TEXT.repeat(3),
            Language.Japanese,
            config,
            100,
            "\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002",
            "\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002",
            "\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002"
        )

        assertLineBreak(
            JP_TEXT.repeat(4),
            Language.Japanese,
            config,
            100,
            "\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002",
            "\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002",
            "\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002",
            "\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002"
        )

        // instead.
        assertLineBreak(
            JP_TEXT.repeat(5),
            Language.Japanese,
            config,
            100,
            "\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002",
            "\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002",
            "\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002",
            "\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002",
            "\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002"
        )

        assertLineBreak(
            JP_TEXT.repeat(6),
            Language.Japanese,
            config,
            100,
            "\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002",
            "\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002",
            "\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002",
            "\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002",
            "\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002",
            "\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002"
        )
    }

    @RequiresFlagsEnabled(Flags.FLAG_WORD_STYLE_AUTO)
    @Test
    fun testAuto_JapaneseAuto() {
        val config = LineBreakConfig.Builder()
                .setLineBreakWordStyle(LineBreakConfig.LINE_BREAK_WORD_STYLE_AUTO)
                .build()

        assertLineBreak(
            JP_TEXT.repeat(1),
            Language.Japanese,
            config,
            100,
            "\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002"
        )

        assertLineBreak(
            JP_TEXT.repeat(2),
            Language.Japanese,
            config,
            100,
            "\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002",
            "\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002"
        )

        assertLineBreak(
            JP_TEXT.repeat(3),
            Language.Japanese,
            config,
            100,
            "\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002",
            "\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002",
            "\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002"
        )

        assertLineBreak(
            JP_TEXT.repeat(4),
            Language.Japanese,
            config,
            100,
            "\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002",
            "\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002",
            "\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002",
            "\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002"
        )

        // When the line count becomes 5 or more as a result of phrase break, use non-phrase break
        // instead.
        assertLineBreak(
            JP_TEXT.repeat(5),
            Language.Japanese,
            config,
            100,
            "\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002\u672C\u65E5",
            "\u306F\u6674\u5929\u306A\u308A\u3002\u672C\u65E5\u306F\u6674",
            "\u5929\u306A\u308A\u3002\u672C\u65E5\u306F\u6674\u5929\u306A",
            "\u308A\u3002\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002"
        )

        assertLineBreak(
            JP_TEXT.repeat(6),
            Language.Japanese,
            config,
            100,
            "\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002\u672C\u65E5",
            "\u306F\u6674\u5929\u306A\u308A\u3002\u672C\u65E5\u306F\u6674",
            "\u5929\u306A\u308A\u3002\u672C\u65E5\u306F\u6674\u5929\u306A",
            "\u308A\u3002\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002",
            "\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002"
        )
    }

    @RequiresFlagsEnabled(Flags.FLAG_WORD_STYLE_AUTO)
    @Test
    fun testAuto_JapaneseStyleAutoStrict() {
        val normal = LineBreakConfig.Builder()
                .setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_NORMAL)
                .setLineBreakWordStyle(LineBreakConfig.LINE_BREAK_WORD_STYLE_NONE)
                .build()

        val strict = LineBreakConfig.Builder()
                .setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_STRICT)
                .setLineBreakWordStyle(LineBreakConfig.LINE_BREAK_WORD_STYLE_NONE)
                .build()

        val auto = LineBreakConfig.Builder()
                .setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_AUTO)
                .setLineBreakWordStyle(LineBreakConfig.LINE_BREAK_WORD_STYLE_NONE)
                .build()

        assertLineBreak(
            JP_TEXT_FOR_STRICT,
            Language.Japanese,
            normal,
            80,
            "\u30B5\u30FC\u30BF\u30FC\u30A2\u30F3\u30C0\u30AE",
            "\u30FC"
        )

        assertLineBreak(
            JP_TEXT_FOR_STRICT,
            Language.Japanese,
            strict,
            80,
            "\u30B5\u30FC\u30BF\u30FC\u30A2\u30F3\u30C0",
            "\u30AE\u30FC"
        )

        assertLineBreak(
            JP_TEXT_FOR_STRICT,
            Language.Japanese,
            auto,
            80,
            "\u30B5\u30FC\u30BF\u30FC\u30A2\u30F3\u30C0",
            "\u30AE\u30FC"
        )
    }

    @RequiresFlagsEnabled(Flags.FLAG_WORD_STYLE_AUTO)
    @Test
    fun testAuto_Korean() {
        val none = LineBreakConfig.Builder()
                .setLineBreakWordStyle(LineBreakConfig.LINE_BREAK_WORD_STYLE_NONE)
                .build()

        val phrase = LineBreakConfig.Builder()
                .setLineBreakWordStyle(LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE)
                .build()

        val auto = LineBreakConfig.Builder()
                .setLineBreakWordStyle(LineBreakConfig.LINE_BREAK_WORD_STYLE_AUTO)
                .build()

        // LINE_BREAK_WORD_STYLE_NONE should break at grapheme
        assertLineBreak(
            KO_TEXT,
            Language.Korean,
            none,
            100,
            "\uC544\uCE68\uBC25\uC744\u0020\uBA39\uACE0\u0020\uC2F6\uC2B5",
            "\uB2C8\uB2E4\u002E"
        )

        // LINE_BREAK_WORD_STYLE_PHRASE should break at whitespace
        assertLineBreak(
            KO_TEXT,
            Language.Korean,
            phrase,
            100,
            "\uC544\uCE68\uBC25\uC744\u0020\uBA39\uACE0\u0020",
            "\uC2F6\uC2B5\uB2C8\uB2E4\u002E"
        )

        // LINE_BREAK_WORD_STYLE_AUTO should work as PHRASE.
        assertLineBreak(
            KO_TEXT,
            Language.Korean,
            auto,
            100,
            "\uC544\uCE68\uBC25\uC744\u0020\uBA39\uACE0\u0020",
            "\uC2F6\uC2B5\uB2C8\uB2E4\u002E"
        )
    }
}
