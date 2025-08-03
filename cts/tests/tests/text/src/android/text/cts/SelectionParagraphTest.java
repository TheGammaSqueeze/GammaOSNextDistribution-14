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

import android.text.Layout;
import android.text.Selection;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.Pair;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class SelectionParagraphTest {
    private static final TextPaint PAINT = new TextPaint();
    private static final String LOREM_IPSUM = "Lorem ipsum dolor sit amet, consectetur adipiscing"
            + " elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.";
    private static final String TEST_STRING = LOREM_IPSUM + "\n" + LOREM_IPSUM + "\n" + LOREM_IPSUM;

    static {
        PAINT.setTextSize(32f);
    }

    private Layout getSixLineLayout(CharSequence text, TextPaint paint) {
        final float desiredWidth = Layout.getDesiredWidth(text, PAINT);
        return StaticLayout.Builder.obtain(text, 0, text.length(), paint, (int) (desiredWidth / 6))
                .build();
    }

    private void assertSelection(Spannable spannable, int start, int end) {
        assertThat(new Pair(
                Selection.getSelectionStart(spannable), Selection.getSelectionEnd(spannable))
        ).isEqualTo(new Pair(start, end));
    }

    @Test
    @ApiTest(apis = "android.text.Selection#moveToParagraphEnd")
    public void testMoveParagraphNext_from_beginning_cursor() {
        final SpannableString spannable = new SpannableString(TEST_STRING);
        final Layout layout = getSixLineLayout(spannable, PAINT);
        // Set the cursor at the beginning of the document.
        Selection.setSelection(spannable, 0);

        assertThat(Selection.moveToParagraphEnd(spannable, layout)).isTrue();
        assertSelection(spannable, LOREM_IPSUM.length(), LOREM_IPSUM.length());

        assertThat(Selection.moveToParagraphEnd(spannable, layout)).isTrue();
        // "+1" for the paragraph separate character.
        assertSelection(spannable, LOREM_IPSUM.length() * 2 + 1, LOREM_IPSUM.length() * 2 + 1);
    }

    @Test
    @ApiTest(apis = "android.text.Selection#moveToParagraphEnd")
    public void testMoveParagraphNext_from_middle_cursor() {
        final SpannableString spannable = new SpannableString(TEST_STRING);
        final Layout layout = getSixLineLayout(spannable, PAINT);

        // Set the cursor at the middle of the first paragraph.
        Selection.setSelection(spannable, LOREM_IPSUM.length() / 2);

        assertThat(Selection.moveToParagraphEnd(spannable, layout)).isTrue();
        assertSelection(spannable, LOREM_IPSUM.length(), LOREM_IPSUM.length());

        assertThat(Selection.moveToParagraphEnd(spannable, layout)).isTrue();
        // "+1" for the paragraph separate character.
        assertSelection(spannable, LOREM_IPSUM.length() * 2 + 1, LOREM_IPSUM.length() * 2 + 1);
    }

    @Test
    @ApiTest(apis = "android.text.Selection#moveToParagraphEnd")
    public void testMoveParagraphNext_from_paragraph_bounds_cursor() {
        final SpannableString spannable = new SpannableString(TEST_STRING);
        final Layout layout = getSixLineLayout(spannable, PAINT);

        // Set the cursor at the middle of the first paragraph.
        Selection.setSelection(spannable, LOREM_IPSUM.length());

        assertThat(Selection.moveToParagraphEnd(spannable, layout)).isTrue();
        // "+1" for the paragraph separate character.
        assertSelection(spannable, LOREM_IPSUM.length() * 2 + 1, LOREM_IPSUM.length() * 2 + 1);

        // Next time, moved to the end of document.
        assertThat(Selection.moveToParagraphEnd(spannable, layout)).isTrue();
        assertSelection(spannable, spannable.length(), spannable.length());

        // Since there is no characters, the moveToParagraphEnd API does nothing and returns false.
        assertThat(Selection.moveToParagraphEnd(spannable, layout)).isFalse();
        assertSelection(spannable, spannable.length(), spannable.length());
    }

    @Test
    @ApiTest(apis = "android.text.Selection#moveToParagraphEnd")
    public void testMoveParagraphNext_from_end_cursor() {
        final SpannableString spannable = new SpannableString(TEST_STRING);
        final Layout layout = getSixLineLayout(spannable, PAINT);

        // Set the cursor at the middle of the first paragraph.
        Selection.setSelection(spannable, spannable.length());

        // Since there is no characters, the moveToParagraphEnd API does nothing and returns false.
        assertThat(Selection.moveToParagraphEnd(spannable, layout)).isFalse();
        assertSelection(spannable, spannable.length(), spannable.length());
    }

    @Test
    @ApiTest(apis = "android.text.Selection#moveToParagraphEnd")
    public void testMoveParagraphNext_from_beginning_middle_selection() {
        final SpannableString spannable = new SpannableString(TEST_STRING);
        final Layout layout = getSixLineLayout(spannable, PAINT);

        // Set the selection from beginning of the text until middle of the first paragraph.
        Selection.setSelection(spannable, 0, LOREM_IPSUM.length() / 2);

        // Cancel the selection if there is the selection and collapse to the end.
        assertThat(Selection.moveToParagraphEnd(spannable, layout)).isTrue();
        assertSelection(spannable, LOREM_IPSUM.length() / 2, LOREM_IPSUM.length() / 2);

        assertThat(Selection.moveToParagraphEnd(spannable, layout)).isTrue();
        assertSelection(spannable, LOREM_IPSUM.length(), LOREM_IPSUM.length());
    }

    @Test
    @ApiTest(apis = "android.text.Selection#moveToParagraphEnd")
    public void testMoveParagraphNext_from_beginning_paragraph_bounds_selection() {
        final SpannableString spannable = new SpannableString(TEST_STRING);
        final Layout layout = getSixLineLayout(spannable, PAINT);

        // Set the selection from beginning of the text until end of the first paragraph.
        Selection.setSelection(spannable, 0, LOREM_IPSUM.length());

        // Cancel the selection if there is the selection and collapse to the end.
        assertThat(Selection.moveToParagraphEnd(spannable, layout)).isTrue();
        assertSelection(spannable, LOREM_IPSUM.length(), LOREM_IPSUM.length());

        assertThat(Selection.moveToParagraphEnd(spannable, layout)).isTrue();
        assertSelection(spannable, LOREM_IPSUM.length() * 2 + 1, LOREM_IPSUM.length() * 2 + 1);
    }

    @Test
    @ApiTest(apis = "android.text.Selection#moveToParagraphEnd")
    public void testMoveParagraphNext_from_beginning_end_selection() {
        final SpannableString spannable = new SpannableString(TEST_STRING);
        final Layout layout = getSixLineLayout(spannable, PAINT);

        // Set the selection from beginning of the text until end of the text.
        Selection.setSelection(spannable, 0, spannable.length());

        // Cancel the selection if there is the selection and collapse to the end.
        assertThat(Selection.moveToParagraphEnd(spannable, layout)).isTrue();
        assertSelection(spannable, spannable.length(), spannable.length());
    }

    @Test
    @ApiTest(apis = "android.text.Selection#moveToParagraphEnd")
    public void testMoveParagraphNext_from_middle_middle_selection() {
        final SpannableString spannable = new SpannableString(TEST_STRING);
        final Layout layout = getSixLineLayout(spannable, PAINT);

        // Set the selection from middle of the first paragraph to the middle of the first paragraph
        Selection.setSelection(spannable, LOREM_IPSUM.length() / 3, 2 * LOREM_IPSUM.length() / 3);

        // Cancel the selection if there is the selection and collapse to the end.
        assertThat(Selection.moveToParagraphEnd(spannable, layout)).isTrue();
        assertSelection(spannable, 2 * LOREM_IPSUM.length() / 3, 2 * LOREM_IPSUM.length() / 3);
    }

    @Test
    @ApiTest(apis = "android.text.Selection#moveToParagraphEnd")
    public void testMoveParagraphNext_from_middle_paragraph_bounds_selection() {
        final SpannableString spannable = new SpannableString(TEST_STRING);
        final Layout layout = getSixLineLayout(spannable, PAINT);

        // Set the selection from middle of the first paragraph to the middle of the first paragraph
        Selection.setSelection(spannable, LOREM_IPSUM.length() / 2, LOREM_IPSUM.length());

        // Cancel the selection if there is the selection and collapse to the end.
        assertThat(Selection.moveToParagraphEnd(spannable, layout)).isTrue();
        assertSelection(spannable, LOREM_IPSUM.length(), LOREM_IPSUM.length());
    }

    @Test
    @ApiTest(apis = "android.text.Selection#moveToParagraphEnd")
    public void testMoveParagraphNext_from_middle_end_selection() {
        final SpannableString spannable = new SpannableString(TEST_STRING);
        final Layout layout = getSixLineLayout(spannable, PAINT);

        // Set the selection from middle of the first paragraph to the middle of the first paragraph
        Selection.setSelection(spannable, LOREM_IPSUM.length() / 2, spannable.length());

        // Cancel the selection if there is the selection and collapse to the end.
        assertThat(Selection.moveToParagraphEnd(spannable, layout)).isTrue();
        assertSelection(spannable, spannable.length(), spannable.length());
    }

    @Test
    @ApiTest(apis = "android.text.Selection#moveToParagraphStart")
    public void testMoveParagraphPrev_from_beginning_cursor() {
        final SpannableString spannable = new SpannableString(TEST_STRING);
        final Layout layout = getSixLineLayout(spannable, PAINT);
        // Set the cursor at the beginning of the document.
        Selection.setSelection(spannable, 0);

        assertThat(Selection.moveToParagraphStart(spannable, layout)).isFalse();
        assertSelection(spannable, 0, 0);
    }

    @Test
    @ApiTest(apis = "android.text.Selection#moveToParagraphStart")
    public void testMoveParagraphPrev_from_middle_cursor() {
        final SpannableString spannable = new SpannableString(TEST_STRING);
        final Layout layout = getSixLineLayout(spannable, PAINT);

        // Set the cursor at the middle of the second paragraph.
        Selection.setSelection(spannable, 3 * LOREM_IPSUM.length() / 2);

        assertThat(Selection.moveToParagraphStart(spannable, layout)).isTrue();
        assertSelection(spannable, LOREM_IPSUM.length(), LOREM_IPSUM.length());
    }

    @Test
    @ApiTest(apis = "android.text.Selection#moveToParagraphStart")
    public void testMoveParagraphPrev_from_paragraph_bounds_cursor() {
        final SpannableString spannable = new SpannableString(TEST_STRING);
        final Layout layout = getSixLineLayout(spannable, PAINT);

        // Set the cursor at the end of the second paragraph.
        Selection.setSelection(spannable, 2 * LOREM_IPSUM.length() + 1);

        assertThat(Selection.moveToParagraphStart(spannable, layout)).isTrue();
        assertSelection(spannable, LOREM_IPSUM.length(), LOREM_IPSUM.length());

        // Next time, moved to the beginning of document.
        assertThat(Selection.moveToParagraphStart(spannable, layout)).isTrue();
        assertSelection(spannable, 0, 0);
    }

    @Test
    @ApiTest(apis = "android.text.Selection#moveToParagraphStart")
    public void testMoveParagraphPrev_from_end_cursor() {
        final SpannableString spannable = new SpannableString(TEST_STRING);
        final Layout layout = getSixLineLayout(spannable, PAINT);

        // Set the cursor at the middle of the first paragraph.
        Selection.setSelection(spannable, spannable.length());

        // Since there is no characters, the moveToParagraphEnd API does nothing and returns false.
        assertThat(Selection.moveToParagraphStart(spannable, layout)).isTrue();
        assertSelection(spannable, 2 * LOREM_IPSUM.length() + 1, 2 * LOREM_IPSUM.length() + 1);
    }

    @Test
    @ApiTest(apis = "android.text.Selection#moveToParagraphStart")
    public void testMoveParagraphPrev_from_beginning_middle_selection() {
        final SpannableString spannable = new SpannableString(TEST_STRING);
        final Layout layout = getSixLineLayout(spannable, PAINT);

        // Set the selection from beginning of the text until middle of the first paragraph.
        Selection.setSelection(spannable, 0, LOREM_IPSUM.length() / 2);

        // Cancel the selection if there is the selection and collapse to the start.
        assertThat(Selection.moveToParagraphStart(spannable, layout)).isTrue();
        assertSelection(spannable, 0, 0);
    }

    @Test
    @ApiTest(apis = "android.text.Selection#moveToParagraphStart")
    public void testMoveParagraphPrev_from_beginning_paragraph_bounds_selection() {
        final SpannableString spannable = new SpannableString(TEST_STRING);
        final Layout layout = getSixLineLayout(spannable, PAINT);

        // Set the selection from beginning of the text until end of the first paragraph.
        Selection.setSelection(spannable, 0, LOREM_IPSUM.length());

        // Cancel the selection if there is the selection and collapse to the start.
        assertThat(Selection.moveToParagraphStart(spannable, layout)).isTrue();
        assertSelection(spannable, 0, 0);
    }

    @Test
    @ApiTest(apis = "android.text.Selection#moveToParagraphStart")
    public void testMoveParagraphPrev_from_beginning_end_selection() {
        final SpannableString spannable = new SpannableString(TEST_STRING);
        final Layout layout = getSixLineLayout(spannable, PAINT);

        // Set the selection from beginning of the text until end of the text.
        Selection.setSelection(spannable, 0, spannable.length());

        // Cancel the selection if there is the selection and collapse to the start.
        assertThat(Selection.moveToParagraphStart(spannable, layout)).isTrue();
        assertSelection(spannable, 0, 0);
    }

    @Test
    @ApiTest(apis = "android.text.Selection#moveToParagraphStart")
    public void testMoveParagraphPrev_from_middle_middle_selection() {
        final SpannableString spannable = new SpannableString(TEST_STRING);
        final Layout layout = getSixLineLayout(spannable, PAINT);

        // Set the selection from middle of the first paragraph to the middle of the first paragraph
        Selection.setSelection(spannable, LOREM_IPSUM.length() / 3, 2 * LOREM_IPSUM.length() / 3);

        // Cancel the selection if there is the selection and collapse to the start.
        assertThat(Selection.moveToParagraphStart(spannable, layout)).isTrue();
        assertSelection(spannable, LOREM_IPSUM.length() / 3, LOREM_IPSUM.length() / 3);
    }

    @Test
    @ApiTest(apis = "android.text.Selection#moveToParagraphStart")
    public void testMoveParagraphPrev_from_middle_paragraph_bounds_selection() {
        final SpannableString spannable = new SpannableString(TEST_STRING);
        final Layout layout = getSixLineLayout(spannable, PAINT);

        // Set the selection from middle of the first paragraph to the middle of the first paragraph
        Selection.setSelection(spannable, LOREM_IPSUM.length() / 2, LOREM_IPSUM.length());

        // Cancel the selection if there is the selection and collapse to the start.
        assertThat(Selection.moveToParagraphStart(spannable, layout)).isTrue();
        assertSelection(spannable, LOREM_IPSUM.length() / 2, LOREM_IPSUM.length() / 2);
    }

    @Test
    @ApiTest(apis = "android.text.Selection#moveToParagraphStart")
    public void testMoveParagraphPrev_from_middle_end_selection() {
        final SpannableString spannable = new SpannableString(TEST_STRING);
        final Layout layout = getSixLineLayout(spannable, PAINT);

        // Set the selection from middle of the first paragraph to the middle of the first paragraph
        Selection.setSelection(spannable, LOREM_IPSUM.length() / 2, spannable.length());

        // Cancel the selection if there is the selection and collapse to the start.
        assertThat(Selection.moveToParagraphStart(spannable, layout)).isTrue();
        assertSelection(spannable, LOREM_IPSUM.length() / 2, LOREM_IPSUM.length() / 2);
    }

    @Test
    @ApiTest(apis = "android.text.Selection#extendToParagraphEnd")
    public void testExtendParagraphNext_from_beginning_cursor() {
        final SpannableString spannable = new SpannableString(TEST_STRING);
        // Set the cursor at the beginning of the document.
        Selection.setSelection(spannable, 0);

        assertThat(Selection.extendToParagraphEnd(spannable)).isTrue();
        assertSelection(spannable, 0, LOREM_IPSUM.length());

        assertThat(Selection.extendToParagraphEnd(spannable)).isTrue();
        // "+1" for the paragraph separate character.
        assertSelection(spannable, 0, LOREM_IPSUM.length() * 2 + 1);
    }

    @Test
    @ApiTest(apis = "android.text.Selection#extendToParagraphEnd")
    public void testExtendParagraphNext_from_middle_cursor() {
        final SpannableString spannable = new SpannableString(TEST_STRING);

        // Set the cursor at the middle of the first paragraph.
        Selection.setSelection(spannable, LOREM_IPSUM.length() / 2);

        assertThat(Selection.extendToParagraphEnd(spannable)).isTrue();
        assertSelection(spannable, LOREM_IPSUM.length() / 2, LOREM_IPSUM.length());

        assertThat(Selection.extendToParagraphEnd(spannable)).isTrue();
        // "+1" for the paragraph separate character.
        assertSelection(spannable, LOREM_IPSUM.length() / 2, LOREM_IPSUM.length() * 2 + 1);
    }

    @Test
    @ApiTest(apis = "android.text.Selection#extendToParagraphEnd")
    public void testExtendParagraphNext_from_paragraph_bounds_cursor() {
        final SpannableString spannable = new SpannableString(TEST_STRING);

        // Set the cursor at the middle of the first paragraph.
        Selection.setSelection(spannable, LOREM_IPSUM.length());

        assertThat(Selection.extendToParagraphEnd(spannable)).isTrue();
        // "+1" for the paragraph separate character.
        assertSelection(spannable, LOREM_IPSUM.length(), LOREM_IPSUM.length() * 2 + 1);

        // Next time, moved to the end of document.
        assertThat(Selection.extendToParagraphEnd(spannable)).isTrue();
        assertSelection(spannable, LOREM_IPSUM.length(), spannable.length());

        // Since there is no characters, the extendToParagraphEnd API does nothing and returns false
        assertThat(Selection.extendToParagraphEnd(spannable)).isFalse();
        assertSelection(spannable, LOREM_IPSUM.length(), spannable.length());
    }

    @Test
    @ApiTest(apis = "android.text.Selection#extendToParagraphEnd")
    public void testExtendParagraphNext_from_end_cursor() {
        final SpannableString spannable = new SpannableString(TEST_STRING);

        // Set the cursor at the middle of the first paragraph.
        Selection.setSelection(spannable, spannable.length());

        // Since there is no characters, the extendToParagraphEnd API does nothing and returns false
        assertThat(Selection.extendToParagraphEnd(spannable)).isFalse();
        assertSelection(spannable, spannable.length(), spannable.length());
    }

    @Test
    @ApiTest(apis = "android.text.Selection#extendToParagraphEnd")
    public void testExtendParagraphNext_from_beginning_middle_selection() {
        final SpannableString spannable = new SpannableString(TEST_STRING);

        // Set the selection from beginning of the text until middle of the first paragraph.
        Selection.setSelection(spannable, 0, LOREM_IPSUM.length() / 2);

        assertThat(Selection.extendToParagraphEnd(spannable)).isTrue();
        assertSelection(spannable, 0, LOREM_IPSUM.length());

        assertThat(Selection.extendToParagraphEnd(spannable)).isTrue();
        assertSelection(spannable, 0, LOREM_IPSUM.length() * 2 + 1);
    }

    @Test
    @ApiTest(apis = "android.text.Selection#extendToParagraphEnd")
    public void testExtendParagraphNext_from_beginning_paragraph_bounds_selection() {
        final SpannableString spannable = new SpannableString(TEST_STRING);

        // Set the selection from beginning of the text until end of the first paragraph.
        Selection.setSelection(spannable, 0, LOREM_IPSUM.length());

        assertThat(Selection.extendToParagraphEnd(spannable)).isTrue();
        assertSelection(spannable, 0, LOREM_IPSUM.length() * 2 + 1);

        assertThat(Selection.extendToParagraphEnd(spannable)).isTrue();
        assertSelection(spannable, 0, spannable.length());
    }

    @Test
    @ApiTest(apis = "android.text.Selection#extendToParagraphEnd")
    public void testExtendParagraphNext_from_beginning_end_selection() {
        final SpannableString spannable = new SpannableString(TEST_STRING);

        // Set the selection from beginning of the text until end of the text.
        Selection.setSelection(spannable, 0, spannable.length());

        assertThat(Selection.extendToParagraphEnd(spannable)).isFalse();
        assertSelection(spannable, 0, spannable.length());
    }

    @Test
    @ApiTest(apis = "android.text.Selection#extendToParagraphEnd")
    public void testExtendParagraphNext_from_middle_middle_selection() {
        final SpannableString spannable = new SpannableString(TEST_STRING);

        // Set the selection from middle of the first paragraph to the middle of the first paragraph
        Selection.setSelection(spannable, LOREM_IPSUM.length() / 3, 2 * LOREM_IPSUM.length() / 3);

        assertThat(Selection.extendToParagraphEnd(spannable)).isTrue();
        assertSelection(spannable, LOREM_IPSUM.length() / 3,  LOREM_IPSUM.length());
    }

    @Test
    @ApiTest(apis = "android.text.Selection#extendToParagraphEnd")
    public void testExtendParagraphNext_from_middle_paragraph_bounds_selection() {
        final SpannableString spannable = new SpannableString(TEST_STRING);

        // Set the selection from middle of the first paragraph to the middle of the first paragraph
        Selection.setSelection(spannable, LOREM_IPSUM.length() / 2, LOREM_IPSUM.length());

        assertThat(Selection.extendToParagraphEnd(spannable)).isTrue();
        assertSelection(spannable, LOREM_IPSUM.length() / 2, LOREM_IPSUM.length() * 2 + 1);
    }

    @Test
    @ApiTest(apis = "android.text.Selection#extendToParagraphEnd")
    public void testExtendParagraphNext_from_middle_end_selection() {
        final SpannableString spannable = new SpannableString(TEST_STRING);

        // Set the selection from middle of the first paragraph to the middle of the first paragraph
        Selection.setSelection(spannable, LOREM_IPSUM.length() / 2, spannable.length());

        assertThat(Selection.extendToParagraphEnd(spannable)).isFalse();
        assertSelection(spannable, LOREM_IPSUM.length() / 2, spannable.length());
    }

    @Test
    @ApiTest(apis = "android.text.Selection#extendToParagraphStart")
    public void testExtendParagraphPrev_from_beginning_cursor() {
        final SpannableString spannable = new SpannableString(TEST_STRING);

        // Set the cursor at the beginning of the document.
        Selection.setSelection(spannable, 0);

        assertThat(Selection.extendToParagraphStart(spannable)).isFalse();
        assertSelection(spannable, 0, 0);
    }

    @Test
    @ApiTest(apis = "android.text.Selection#extendToParagraphStart")
    public void testExtendParagraphPrev_from_middle_cursor() {
        final SpannableString spannable = new SpannableString(TEST_STRING);

        // Set the cursor at the middle of the second paragraph.
        Selection.setSelection(spannable, 3 * LOREM_IPSUM.length() / 2);

        assertThat(Selection.extendToParagraphStart(spannable)).isTrue();
        assertSelection(spannable, 3 * LOREM_IPSUM.length() / 2, LOREM_IPSUM.length());
    }

    @Test
    @ApiTest(apis = "android.text.Selection#extendToParagraphStart")
    public void testExtendParagraphPrev_from_paragraph_bounds_cursor() {
        final SpannableString spannable = new SpannableString(TEST_STRING);

        // Set the cursor at the end of the second paragraph.
        Selection.setSelection(spannable, 2 * LOREM_IPSUM.length() + 1);

        assertThat(Selection.extendToParagraphStart(spannable)).isTrue();
        assertSelection(spannable, 2 * LOREM_IPSUM.length() + 1, LOREM_IPSUM.length());

        // Next time, moved to the beginning of document.
        assertThat(Selection.extendToParagraphStart(spannable)).isTrue();
        assertSelection(spannable, 2 * LOREM_IPSUM.length() + 1, 0);
    }

    @Test
    @ApiTest(apis = "android.text.Selection#extendToParagraphStart")
    public void testExtendParagraphPrev_from_end_cursor() {
        final SpannableString spannable = new SpannableString(TEST_STRING);

        // Set the cursor at the middle of the first paragraph.
        Selection.setSelection(spannable, spannable.length());

        // Since there is no characters, the extendToParagraphEnd API does nothing and returns false
        assertThat(Selection.extendToParagraphStart(spannable)).isTrue();
        assertSelection(spannable, spannable.length(), 2 * LOREM_IPSUM.length() + 1);
    }

    @Test
    @ApiTest(apis = "android.text.Selection#extendToParagraphStart")
    public void testExtendParagraphPrev_from_beginning_middle_selection() {
        final SpannableString spannable = new SpannableString(TEST_STRING);

        // Set the selection from beginning of the text until middle of the first paragraph.
        Selection.setSelection(spannable, 0, LOREM_IPSUM.length() / 2);

        assertThat(Selection.extendToParagraphStart(spannable)).isTrue();
        assertSelection(spannable, 0, 0);
    }

    @Test
    @ApiTest(apis = "android.text.Selection#extendToParagraphStart")
    public void testExtendParagraphPrev_from_beginning_paragraph_bounds_selection() {
        final SpannableString spannable = new SpannableString(TEST_STRING);

        // Set the selection from beginning of the text until end of the first paragraph.
        Selection.setSelection(spannable, 0, LOREM_IPSUM.length());

        assertThat(Selection.extendToParagraphStart(spannable)).isTrue();
        assertSelection(spannable, 0, 0);
    }

    @Test
    @ApiTest(apis = "android.text.Selection#extendToParagraphStart")
    public void testExtendParagraphPrev_from_beginning_end_selection() {
        final SpannableString spannable = new SpannableString(TEST_STRING);

        // Set the selection from beginning of the text until end of the text.
        Selection.setSelection(spannable, 0, spannable.length());

        assertThat(Selection.extendToParagraphStart(spannable)).isTrue();
        assertSelection(spannable, 0, 2 * LOREM_IPSUM.length() + 1);
    }

    @Test
    @ApiTest(apis = "android.text.Selection#extendToParagraphStart")
    public void testExtendParagraphPrev_from_middle_middle_selection() {
        final SpannableString spannable = new SpannableString(TEST_STRING);

        // Set the selection from middle of the first paragraph to the middle of the first paragraph
        Selection.setSelection(spannable, LOREM_IPSUM.length() / 3, 2 * LOREM_IPSUM.length() / 3);

        assertThat(Selection.extendToParagraphStart(spannable)).isTrue();
        assertSelection(spannable, LOREM_IPSUM.length() / 3, 0);
    }

    @Test
    @ApiTest(apis = "android.text.Selection#extendToParagraphStart")
    public void testExtendParagraphPrev_from_middle_paragraph_bounds_selection() {
        final SpannableString spannable = new SpannableString(TEST_STRING);

        // Set the selection from middle of the first paragraph to the middle of the first paragraph
        Selection.setSelection(spannable, LOREM_IPSUM.length() / 2, LOREM_IPSUM.length());

        assertThat(Selection.extendToParagraphStart(spannable)).isTrue();
        assertSelection(spannable, LOREM_IPSUM.length() / 2, 0);
    }

    @Test
    @ApiTest(apis = "android.text.Selection#extendToParagraphStart")
    public void testExtendParagraphPrev_from_middle_end_selection() {
        final SpannableString spannable = new SpannableString(TEST_STRING);

        // Set the selection from middle of the first paragraph to the middle of the first paragraph
        Selection.setSelection(spannable, LOREM_IPSUM.length() / 2, spannable.length());

        // Cancel the selection if there is the selection and collapse to the start.
        assertThat(Selection.extendToParagraphStart(spannable)).isTrue();
        assertSelection(spannable, LOREM_IPSUM.length() / 2, 2 * LOREM_IPSUM.length() + 1);
    }
}
