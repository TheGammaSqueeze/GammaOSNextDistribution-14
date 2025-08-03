/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.layoutlib.bridge.impl;

import org.junit.Test;

import android.content.res.StringBlock;
import android.text.SpannedString;
import android.text.TextUtils.TruncateAt;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.BulletSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.SubscriptSpan;
import android.text.style.SuperscriptSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;
import android.text.style.UnderlineSpan;

import static org.junit.Assert.*;

public class ResourceHelperTest {
    private static void assertNumberFormatException(Runnable runnable) {
        try {
            runnable.run();
            fail("NumberFormatException expected");
        } catch (NumberFormatException ignored) {
        }
    }

    @Test
    public void testGetColor() {
        assertNumberFormatException(() -> ResourceHelper.getColor(""));
        assertNumberFormatException(() -> ResourceHelper.getColor("AFAFAF"));
        assertNumberFormatException(() -> ResourceHelper.getColor("AAA"));
        assertNumberFormatException(() -> ResourceHelper.getColor("#JFAFAF"));
        assertNumberFormatException(() -> ResourceHelper.getColor("#AABBCCDDEE"));
        assertNumberFormatException(() -> ResourceHelper.getColor("#JAAA"));
        assertNumberFormatException(() -> ResourceHelper.getColor("#AA BBCC"));

        assertEquals(0xffaaaaaa, ResourceHelper.getColor("#AAA"));
        assertEquals(0xffaaaaaa, ResourceHelper.getColor("  #AAA"));
        assertEquals(0xffaaaaaa, ResourceHelper.getColor("#AAA   "));
        assertEquals(0xffaaaaaa, ResourceHelper.getColor("  #AAA   "));
        assertEquals(0xaaaaaa, ResourceHelper.getColor("#0AAA"));
        assertEquals(0xffaabbcc, ResourceHelper.getColor("#AABBCC"));
        assertEquals(0x12aabbcc, ResourceHelper.getColor("#12AABBCC"));
        assertEquals(0x12345, ResourceHelper.getColor("#12345"));
    }

    @Test
    public void testParseHtml() {
        CharSequence parsed = ResourceHelper.parseHtml("Text <b>bold</b> " +
                "<i>italic</i> <u>underline</u> <tt>monospace</tt> " +
                "<big>big</big> <small>small</small> " +
                "<sup>superscript</sup> <sub>subscript</sub> <strike>strike</strike> " +
                "<li>bullet</li> <marquee>marquee</marquee> " +
                "<a href=\"http://link.com\">link</a> " +
                "<font face=\"serif\" color=\"#ff0000\" height=\"20\" size=\"8\">font</font> " +
                "<em>fake italic</em> <del>fake strike</del> " +
                "End text");
        Class<?>[] classes = {StyleSpan.class, StyleSpan.class, UnderlineSpan.class,
                TypefaceSpan.class, RelativeSizeSpan.class, RelativeSizeSpan.class,
                SuperscriptSpan.class, SubscriptSpan.class, StrikethroughSpan.class,
                BulletSpan.class, TruncateAt.class, URLSpan.class, StringBlock.Height.class,
                AbsoluteSizeSpan.class, ForegroundColorSpan.class, TypefaceSpan.class};
        int[] starts = {5, 10, 17, 27, 37, 41, 47, 59, 69, 0, 83, 91, 0, 96, 96, 96};
        int[] ends = {9, 16, 26, 36, 40, 46, 58, 68, 75, 133, 90, 95, 133, 100, 100, 100};
        SpannedString spanned = (SpannedString)parsed;
        assertEquals("Text bold " +
                "italic underline monospace " +
                "big small " +
                "superscript subscript strike " +
                "bullet marquee " +
                "link " +
                "font " +
                "fake italic fake strike " +
                "End text", spanned.toString());
        Object[] spans = spanned.getSpans(0, spanned.length(), Object.class);
        for (int i =0; i < spans.length; i++) {
            assertEquals(classes[i], spans[i].getClass());
            assertEquals(starts[i], spanned.getSpanStart(spans[i]));
            assertEquals(ends[i], spanned.getSpanEnd(spans[i]));
        }
    }

    @Test
    public void testParsePlainHtml() {
        String plainText = "This text has no html tags";
        assertEquals(plainText, ResourceHelper.parseHtml(plainText));
    }
}