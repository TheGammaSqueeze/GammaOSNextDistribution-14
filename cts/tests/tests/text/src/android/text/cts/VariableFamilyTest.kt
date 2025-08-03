/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily
import android.graphics.fonts.FontStyle
import android.graphics.text.PositionedGlyphs
import android.graphics.text.TextRunShaper
import androidx.test.filters.SmallTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import androidx.test.platform.app.InstrumentationRegistry
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertFalse
import junit.framework.Assert.assertNull
import junit.framework.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4ClassRunner::class)
class VariableFamilyTest {

    private lateinit var wghtUprightFont: Font
    private lateinit var wghtItalicFont: Font
    private lateinit var wghtItalFont: Font
    private lateinit var nonVarFont: Font

    @Before
    fun setUp() {
        // This upright font has 'wght' axis but not 'ital' axis.
        // The PS name is "VariableUprightFont-wght"
        wghtUprightFont = makeFont("fonts/VariableUprightFontWght.ttf")
        // This italic font has 'wght' axis but not 'ital' axis.
        // The PS name is "VariableItalicFont-wght"
        wghtItalicFont = makeFont("fonts/VariableItalicFontWght.ttf")
        // This upright font has both 'wght' axis and 'ital' axis.
        // The PS name is "VariableFont-wght-ital"
        wghtItalFont = makeFont("fonts/VariableFontWghtItal.ttf")

        // The static font.
        nonVarFont = makeFont("fonts/samplefont.ttf")
    }

    @Test
    fun testVariableFamily_twoFonts_upright() {
        val family = requireNotNull(
            FontFamily.Builder(wghtUprightFont)
                .addFont(wghtItalicFont)
                .buildVariableFamily()
        )

        val glyphs = shape("a", family, FontStyle(100, FontStyle.FONT_SLANT_UPRIGHT))
        assertEquals(1, glyphs.glyphCount())
        assertEquals("VariableUprightFont-wght", glyphs.getPSName(0))
        assertFalse(glyphs.getFakeBold(0))
        assertFalse(glyphs.getFakeItalic(0))
        assertEquals(100f, glyphs.getWeightOverride(0))
        assertEquals(PositionedGlyphs.NO_OVERRIDE, glyphs.getItalicOverride(0))
    }

    @Test
    fun testVariableFamily_twoFonts_italic() {
        val family = requireNotNull(
            FontFamily.Builder(wghtUprightFont)
                .addFont(wghtItalicFont)
                .buildVariableFamily()
        )

        val glyphs = shape("a", family, FontStyle(200, FontStyle.FONT_SLANT_ITALIC))
        assertEquals(1, glyphs.glyphCount())
        assertEquals("VariableItalicFont-wght", glyphs.getPSName(0))
        assertFalse(glyphs.getFakeBold(0))
        assertFalse(glyphs.getFakeItalic(0))
        assertEquals(200f, glyphs.getWeightOverride(0))
        assertEquals(PositionedGlyphs.NO_OVERRIDE, glyphs.getItalicOverride(0))
    }

    @Test
    fun testVariableFamily_singleFonts_wghtOnly_upright() {
        val family = requireNotNull(FontFamily.Builder(wghtUprightFont).buildVariableFamily())

        val glyphs = shape("a", family, FontStyle(300, FontStyle.FONT_SLANT_UPRIGHT))
        assertEquals(1, glyphs.glyphCount())
        val font = glyphs.getFont(0)
        val psName = FontFileTestUtil.getPostScriptName(font.buffer, font.ttcIndex)
        assertEquals("VariableUprightFont-wght", psName)
        assertFalse(glyphs.getFakeBold(0))
        assertFalse(glyphs.getFakeItalic(0))
        assertEquals(300f, glyphs.getWeightOverride(0))
        assertEquals(PositionedGlyphs.NO_OVERRIDE, glyphs.getItalicOverride(0))
    }

    @Test
    fun testVariableFamily_singleFonts_wghtOnly_italic() {
        val family = requireNotNull(FontFamily.Builder(wghtUprightFont).buildVariableFamily())

        val glyphs = shape("a", family, FontStyle(500, FontStyle.FONT_SLANT_ITALIC))
        assertEquals(1, glyphs.glyphCount())
        assertEquals("VariableUprightFont-wght", glyphs.getPSName(0))
        assertFalse(glyphs.getFakeBold(0))
        assertTrue(glyphs.getFakeItalic(0))
        assertEquals(500f, glyphs.getWeightOverride(0))
        assertEquals(PositionedGlyphs.NO_OVERRIDE, glyphs.getItalicOverride(0))
    }

    @Test
    fun testVariableFamily_singleFonts_wght_ital_upright() {
        val family = requireNotNull(FontFamily.Builder(wghtItalFont).buildVariableFamily())

        val glyphs = shape("a", family, FontStyle(300, FontStyle.FONT_SLANT_UPRIGHT))
        assertEquals(1, glyphs.glyphCount())
        val font = glyphs.getFont(0)
        val psName = FontFileTestUtil.getPostScriptName(font.buffer, font.ttcIndex)
        assertEquals("VariableFont-wght-ital", psName)
        assertFalse(glyphs.getFakeBold(0))
        assertFalse(glyphs.getFakeItalic(0))
        assertEquals(300f, glyphs.getWeightOverride(0))
        assertEquals(0f, glyphs.getItalicOverride(0))
    }

    @Test
    fun testVariableFamily_singleFonts_wght_ital_italic() {
        val family = requireNotNull(FontFamily.Builder(wghtItalFont).buildVariableFamily())

        val glyphs = shape("a", family, FontStyle(500, FontStyle.FONT_SLANT_ITALIC))
        assertEquals(1, glyphs.glyphCount())
        assertEquals("VariableFont-wght-ital", glyphs.getPSName(0))
        assertFalse(glyphs.getFakeBold(0))
        assertFalse(glyphs.getFakeItalic(0))
        assertEquals(500f, glyphs.getWeightOverride(0))
        assertEquals(1f, glyphs.getItalicOverride(0))
    }

    @Test
    fun testVariableFamily_not_variableFamily_staticFont() {
        assertNull(FontFamily.Builder(nonVarFont).buildVariableFamily())
        assertNull(FontFamily.Builder(nonVarFont).addFont(wghtUprightFont).buildVariableFamily())
    }

    @Test
    fun testVariableFamily_not_variableFamily_three_or_more_fonts() {
        assertNull(
            FontFamily.Builder(nonVarFont)
                .addFont(wghtItalFont)
                .addFont(Font.Builder(nonVarFont).setWeight(550).build())
                .buildVariableFamily()
        )
    }

    private fun shape(str: String, family: FontFamily, style: FontStyle) =
            TextRunShaper.shapeTextRun(
            str, 0, str.length, 0, str.length, 0f, 0f, false, Paint().apply {
        typeface = Typeface.CustomFallbackBuilder(family).setStyle(style).build()
    })

    private fun makeFont(path: String): Font {
        val context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        return Font.Builder(context.assets, path).build()
    }

    private fun PositionedGlyphs.getPSName(i: Int): String {
        val font = getFont(i)
        return FontFileTestUtil.getPostScriptName(font.buffer, font.ttcIndex)
    }
}
