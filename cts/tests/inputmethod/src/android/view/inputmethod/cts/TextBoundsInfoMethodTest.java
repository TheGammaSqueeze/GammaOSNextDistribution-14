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

package android.view.inputmethod.cts;

import static android.text.Layout.INCLUSION_STRATEGY_ANY_OVERLAP;
import static android.text.Layout.INCLUSION_STRATEGY_CONTAINS_ALL;
import static android.text.Layout.INCLUSION_STRATEGY_CONTAINS_CENTER;
import static android.view.inputmethod.TextBoundsInfo.FLAG_CHARACTER_LINEFEED;
import static android.view.inputmethod.TextBoundsInfo.FLAG_CHARACTER_WHITESPACE;
import static android.view.inputmethod.TextBoundsInfo.FLAG_LINE_IS_RTL;

import static com.google.common.truth.Truth.assertThat;

import android.graphics.Matrix;
import android.graphics.RectF;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.text.Layout;
import android.text.SegmentFinder;
import android.view.inputmethod.TextBoundsInfo;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class TextBoundsInfoMethodTest {
    // In this test, we assume that:
    //   * all characters' height is 20f.
    //   * space character width is 5f.
    //   * LTR character width is 10f,
    //   * RTL character width is 15f.
    //   * linefeed character width is 0f.

    /**
     *  Test character bounds for a text layout with 2 lines of LTR text: "L LL\nLL L", which
     *  should be rendered as:
     *  <pre>
     *    line 1: L0 _ L2 L3  // newline character at the end of the line.
     *    line 2: L5L6 _ L8   // L5 L6 belongs to a single grapheme.
     *    (L represents LTR character, the number after L is the character index)
     *  </pre>
     */
    private static final float[] CHARACTER_BOUNDS_LTR = {
            0f, 0f, 10f, 20f,
            10f, 0f, 15f, 20f,
            15f, 0f, 25f, 20f,
            25f, 0f, 35f, 20f,
            // new line
            35f, 0f, 35f, 20f,
            // second line
            0f, 20f, 10f, 40f,
            10f, 20f, 10f, 40f,
            10f, 20f, 15f, 40f,
            15f, 20f, 25f, 40f,
    };

    /**
     * Test flags for the LTR text.
     * Note that \n is both a whitespace and new linefeed.
     */
    private static final int[] CHARACTER_FLAGS_LTR = {0, FLAG_CHARACTER_WHITESPACE,
            0, 0, FLAG_CHARACTER_WHITESPACE | FLAG_CHARACTER_LINEFEED,
            0, 0, FLAG_CHARACTER_WHITESPACE, 0
    };

    /** The BiDi level for LTR text is 0. */
    private static final int[] CHARACTER_BIDI_LEVEL_LTR = { 0, 0, 0, 0, 0, 0, 0, 0, 0 };

    /** Test grapheme segment finder for the LTR text. */
    private static final SegmentFinder GRAPHEME_SEGMENTS_FINDER_LTR =
            new SegmentFinder.PrescribedSegmentFinder(new int[] { 0, 1, 1, 2, 2, 3, 3, 4, 4, 5,
                    5, 7, 7, 8, 8, 9 });

    /** Test word segment finder for the LTR text. */
    private static final SegmentFinder WORD_SEGMENT_FINDER_LTR =
            new SegmentFinder.PrescribedSegmentFinder(new int[] { 0, 1, 2, 4, 5, 7, 8, 9 });

    /** Test grapheme segment finder for the LTR text. */
    private static final SegmentFinder LINE_SEGMENTS_FINDER_LTR =
            new SegmentFinder.PrescribedSegmentFinder(new int[] { 0, 5, 5, 9 });

    private static final int START_LTR = 0;
    private static final int END_LTR = 9;

    private static final TextBoundsInfo TEXT_BOUNDS_INFO_LTR = new TextBoundsInfo
            .Builder(START_LTR, END_LTR)
            .setMatrix(Matrix.IDENTITY_MATRIX)
            .setCharacterBounds(CHARACTER_BOUNDS_LTR)
            .setCharacterFlags(CHARACTER_FLAGS_LTR)
            .setCharacterBidiLevel(CHARACTER_BIDI_LEVEL_LTR)
            .setGraphemeSegmentFinder(GRAPHEME_SEGMENTS_FINDER_LTR)
            .setWordSegmentFinder(WORD_SEGMENT_FINDER_LTR)
            .setLineSegmentFinder(LINE_SEGMENTS_FINDER_LTR)
            .build();

    /**
     *  Test character bounds for a text layout with 2 lines of RTL text: "R RR\nRR R", which
     *  should be rendered as:
     *  <pre>
     *    line 1: R3 R2 _ R0    // newline character at the end of the line.
     *    line 2: R8 _ R6R5     // R5R6 belongs to a single grapheme
     *    (R represents RTL character, the number after R is the character index)
     *   </pre>
     *   It assumes that the width of the text layout is 80f.
     */
    private static final float[] CHARACTER_BOUNDS_RTL = {
            65f, 0f, 80f, 20f,
            60f, 0f, 65f, 20f,
            45f, 0f, 60f, 20f,
            30f, 0f, 45f, 20f,
            // new line
            30f, 0f, 30f, 20f,
            // second line
            65f, 20f, 80f, 40f,
            65f, 20f, 65f, 40f,
            60f, 20f, 65f, 40f,
            45f, 20f, 60f, 40f
    };

    /**
     * Test flags for the RTL text.
     * Note that \n is both a whitespace and new linefeed.
     */
    private static final int[] CHARACTER_FLAGS_RTL = {
            FLAG_LINE_IS_RTL,
            FLAG_LINE_IS_RTL | FLAG_CHARACTER_WHITESPACE,
            FLAG_LINE_IS_RTL,
            FLAG_LINE_IS_RTL,
            FLAG_LINE_IS_RTL | FLAG_CHARACTER_WHITESPACE | FLAG_CHARACTER_LINEFEED,
            FLAG_LINE_IS_RTL,
            FLAG_LINE_IS_RTL,
            FLAG_LINE_IS_RTL | FLAG_CHARACTER_WHITESPACE,
            FLAG_LINE_IS_RTL
    };

    /** The BiDi level for LTR text is 1. */
    private static final int[] CHARACTER_BIDI_LEVEL_RTL = { 1, 1, 1, 1, 1, 1, 1, 1, 1 };

    /** Test grapheme segment finder for the RTL text. */
    private static final SegmentFinder GRAPHEME_SEGMENTS_FINDER_RTL =
            new SegmentFinder.PrescribedSegmentFinder(new int[] { 0, 1, 1, 2, 2, 3, 3, 4, 4, 5,
                    5, 7, 7, 8, 8, 9 });

    /** Test word segment finder for the RTL text. */
    private static final SegmentFinder WORD_SEGMENT_FINDER_RTL =
            new SegmentFinder.PrescribedSegmentFinder(new int[] { 0, 1, 2, 4, 5, 7, 8, 9 });

    /** Test grapheme segment finder for the RTL text. */
    private static final SegmentFinder LINE_SEGMENTS_FINDER_RTL =
            new SegmentFinder.PrescribedSegmentFinder(new int[] { 0, 5, 5, 9 });

    private static final int START_RTL = 0;
    private static final int END_RTL = 9;

    private static final TextBoundsInfo TEXT_BOUNDS_INFO_RTL = new TextBoundsInfo
            .Builder(START_RTL, END_RTL)
            .setMatrix(Matrix.IDENTITY_MATRIX)
            .setCharacterBounds(CHARACTER_BOUNDS_RTL)
            .setCharacterFlags(CHARACTER_FLAGS_RTL)
            .setCharacterBidiLevel(CHARACTER_BIDI_LEVEL_RTL)
            .setGraphemeSegmentFinder(GRAPHEME_SEGMENTS_FINDER_RTL)
            .setWordSegmentFinder(WORD_SEGMENT_FINDER_RTL)
            .setLineSegmentFinder(LINE_SEGMENTS_FINDER_RTL)
            .build();

    /**
     *  Test character bounds for a text layout with 2 lines of BiDi text: "LLRRLL\nRRLLRR",
     *  which should be rendered as:
     *  <pre>
     *    line 1: L0 L1 R3 R2 L4 L5                // newline character at the end of the line.
     *    line 2:     R12 R11 L9 L10 R8 R7
     *    (L/ R represents LTR/RTL character, the number after R is the character index)
     *   </pre>
     *   It assumes that the width of the text layout is 100f.
     */
    private static final float[] CHARACTER_BOUNDS_BIDI = {
            0f, 0f, 10f, 20f,
            10f, 0f, 20f, 20f,
            35f, 0f, 50f, 20f,
            20f, 0f, 35f, 20f,
            50f, 0f, 60f, 20f,
            60f, 0f, 70f, 20f,
            // new line
            70f, 0f, 70f, 20f,
            // second line
            85f, 20f, 100f, 40f,
            70f, 20f, 85f, 40f,
            50f, 20f, 60f, 40f,
            60f, 20f, 70f, 40f,
            35f, 20f, 50f, 40f,
            20f, 20f, 35f, 40f
    };

    /**
     * Test flags for the BIDI text.
     * Note that \n is both a whitespace and new linefeed.
     */
    private static final int[] CHARACTER_FLAGS_BIDI = { 0, 0, 0, 0, 0, 0,
            // '\n' character
            FLAG_CHARACTER_WHITESPACE | FLAG_CHARACTER_LINEFEED,
            // The second line is RTL.
            FLAG_LINE_IS_RTL,
            FLAG_LINE_IS_RTL,
            FLAG_LINE_IS_RTL,
            FLAG_LINE_IS_RTL,
            FLAG_LINE_IS_RTL,
            FLAG_LINE_IS_RTL
    };

    /** The BiDi level for LTR text is even, and BiDi level for RTL text is odd. */
    private static final int[] CHARACTER_BIDI_LEVEL_BIDI = { 0, 0, 1, 1, 0, 0, 0,
            1, 1, 2, 2, 1, 1 };

    /** Test grapheme segment finder for the BiDi text. */
    private static final SegmentFinder GRAPHEME_SEGMENTS_FINDER_BIDI =
            new SegmentFinder.PrescribedSegmentFinder(new int[] { 0, 1, 1, 2, 2, 3, 3, 4, 4, 5,
                    5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13 });

    /** Test word segment finder for the BiDi text. */
    private static final SegmentFinder WORD_SEGMENT_FINDER_BIDI =
            new SegmentFinder.PrescribedSegmentFinder(new int[] { 0, 2, 2, 4, 4, 6, 7, 13 });

    /** Test grapheme segment finder for the BiDi text. */
    private static final SegmentFinder LINE_SEGMENTS_FINDER_BIDI =
            new SegmentFinder.PrescribedSegmentFinder(new int[] { 0, 7, 7, 13 });

    private static final int START_BIDI = 0;
    private static final int END_BIDI = 13;

    private static final TextBoundsInfo TEXT_BOUNDS_INFO_BIDI = new TextBoundsInfo
            .Builder(START_BIDI, END_BIDI)
            .setMatrix(Matrix.IDENTITY_MATRIX)
            .setCharacterBounds(CHARACTER_BOUNDS_BIDI)
            .setCharacterFlags(CHARACTER_FLAGS_BIDI)
            .setCharacterBidiLevel(CHARACTER_BIDI_LEVEL_BIDI)
            .setGraphemeSegmentFinder(GRAPHEME_SEGMENTS_FINDER_BIDI)
            .setWordSegmentFinder(WORD_SEGMENT_FINDER_BIDI)
            .setLineSegmentFinder(LINE_SEGMENTS_FINDER_BIDI)
            .build();

    @Test
    @ApiTest(apis = { "android.view.inputmethod.TextBoundsInfo#getOffsetForPosition" })
    public void testGetOffsetForPosition_LTR() {
        final TextBoundsInfo textBoundsInfo = TEXT_BOUNDS_INFO_LTR;
        final int start = START_LTR;
        final int end = END_LTR;

        // Normally, if the start of the i-th character is passed, getOffsetForPosition returns i.
        // LTR character's start is the left edge of the character.
        // Character 5 and 6 belong to a single grapheme, and the width is assigned to character 5.
        // The character 6 has zero width, and the left of the character 6 equals to the left of
        // character 7, so it should return 7.
        final int[] expectedIndex = { 0, 1, 2, 3, 4, 5, 7, 7, 8 };
        assertGetOffsetForCharacterLeft(expectedIndex, textBoundsInfo, start, end);

        final RectF lastCharRect = new RectF();
        textBoundsInfo.getCharacterBounds(end - 1, lastCharRect);
        final float lastCharRight = lastCharRect.right;
        final float lastCharCenterY = lastCharRect.centerY();
        assertThat(textBoundsInfo.getOffsetForPosition(lastCharRight, lastCharCenterY))
                .isEqualTo(end - 1);
    }

    @Test
    @ApiTest(apis = { "android.view.inputmethod.TextBoundsInfo#getOffsetForPosition" })
    public void testGetOffsetForPosition_RTL() {
        final TextBoundsInfo textBoundsInfo = TEXT_BOUNDS_INFO_RTL;
        final int start = START_RTL;
        final int end = END_RTL;

        // Normally, if the start of the i-th character is passed, getOffsetForPosition returns i.
        // RTL character's start is the right edge of the character.
        // Character 5 and 6 belong to a single grapheme, and the width is assigned to character 5.
        // The character 6 has zero width, and the right of the character 6 equals to the right of
        // character 7, so it should return 7.
        final int[] expectedIndex = { 0, 1, 2, 3, 4, 5, 7, 7, 8 };
        assertGetOffsetForCharacterRight(expectedIndex, textBoundsInfo, start, end);

        final RectF lastCharRect = new RectF();
        textBoundsInfo.getCharacterBounds(end - 1, lastCharRect);
        final float lastCharLeft = lastCharRect.left;
        final float lastCharCenterY = lastCharRect.centerY();
        assertThat(textBoundsInfo.getOffsetForPosition(lastCharLeft, lastCharCenterY))
                .isEqualTo(end - 1);
    }

    @Test
    @ApiTest(apis = { "android.view.inputmethod.TextBoundsInfo#getOffsetForPosition" })
    public void testGetOffsetForPosition_BiDi() {
        final TextBoundsInfo textBoundsInfo = TEXT_BOUNDS_INFO_BIDI;

        // Normally, if the start of the i-th character is passed, getOffsetForPosition returns i,
        // except when the index i is a BiDi transition point. (The i-th character has a different
        // BiDi direction from the previous character.) When index i is a BiDi transition point and
        // the previous BiDi run has a smaller BiDi level, the end of the previous character
        // corresponds to the index i. Otherwise, the start of i-th character corresponds to
        // index i.
        //
        // text layout in example:
        // line 1:      L0 L1 R3 R2 L4 L5    (character 6 is '\n')
        // BiDi level:  0  0  1  1  0  0
        // position:         |               (index 2 is a BiDi transition point and the previous
        //                                    run has a smaller BiDi level, so index 2 corresponds
        //                                    to the end of the character 1)
        // line 2:       R12 R11 L9  L10 R8  R7
        // BiDi level:   1   1   2   2   1   1
        // position:                    |    (index 9 is a BiDi transition point and the previous
        //                                    run has a smaller BiDi level, so index 9 corresponds
        //                                    to the end of the character 8)

        final int[] expectedIndexForLeft = { 0, 1, 3, 2, 4, 5, 6 };
        assertGetOffsetForCharacterLeft(expectedIndexForLeft, textBoundsInfo, 0, 7);

        final int[] expectedIndexForRight = { 7, 8, 10, 9, 11, 12 };
        assertGetOffsetForCharacterRight(expectedIndexForRight, textBoundsInfo, 7, 13);
    }

    @Test
    @ApiTest(apis = { "android.view.inputmethod.TextBoundsInfo#getRangeForRect" })
    public void testGetRangeForRect_grapheme_containsCenter_LTR() {
        final TextBoundsInfo textBoundsInfo = TEXT_BOUNDS_INFO_LTR;

        // In the test data, all characters in the first line have the same top and bottom.
        final float line1CenterY = getCharacterCenterY(textBoundsInfo, 0);
        final float char1CenterX = getCharacterCenterX(textBoundsInfo, 1);
        final float char2CenterX = getCharacterCenterX(textBoundsInfo, 2);

        final SegmentFinder segmentFinder = textBoundsInfo.getGraphemeSegmentFinder();
        final Layout.TextInclusionStrategy inclusionStrategy = INCLUSION_STRATEGY_CONTAINS_CENTER;

        // This area includes the center of character 1 and 2; the range should be [1, 3).
        final RectF area1 = new RectF(char1CenterX - 1f, line1CenterY - 1f,
                char2CenterX + 1f, line1CenterY + 1f);
        assertGetRangeForRect(1, 3, textBoundsInfo, area1, segmentFinder, inclusionStrategy);

        // This area includes the no character center; it should return null.
        final RectF area2 = new RectF(char1CenterX + 1f, line1CenterY - 1f,
                char2CenterX - 1f, line1CenterY + 1f);
        assertGetRangeForRectIsNull(textBoundsInfo, area2, segmentFinder, inclusionStrategy);

        // In the test data, all characters in the second line have the same top and bottom.
        final float line2CenterY = getCharacterCenterY(textBoundsInfo, 5);
        final float char5CenterX = getCharacterCenterX(textBoundsInfo, 5);

        // The character at index 5 and 6 forms a grapheme cluster, and all width is assigned to
        // character 5. The center of the grapheme is included in the area; it should return [5, 7).
        final RectF area3 = new RectF(char5CenterX - 1f, line2CenterY - 1f,
                char5CenterX + 1f, line2CenterY + 1f);
        assertGetRangeForRect(5, 7, textBoundsInfo, area3, segmentFinder, inclusionStrategy);

        // The area covers 2 lines from the character 1 to 7; it should return [1, 8).
        final float char7CenterX = getCharacterCenterX(textBoundsInfo, 7);
        final RectF area4 = new RectF(char1CenterX - 1f, line1CenterY - 1f,
                char7CenterX + 1f, line2CenterY + 1f);
        assertGetRangeForRect(1, 8, textBoundsInfo, area4, segmentFinder, inclusionStrategy);
    }

    @Test
    @ApiTest(apis = { "android.view.inputmethod.TextBoundsInfo#getRangeForRect" })
    public void testGetRangeForRect_grapheme_anyOverlap_LTR() {
        final TextBoundsInfo textBoundsInfo = TEXT_BOUNDS_INFO_LTR;

        // In the test data, all characters in the first line have the same top and bottom.
        final float line1Top = getCharacterTop(textBoundsInfo, 0);
        final float line1Bottom = getCharacterBottom(textBoundsInfo, 0);
        final float char1Left = getCharacterLeft(textBoundsInfo, 1);
        final float char2Right = getCharacterRight(textBoundsInfo, 2);

        final SegmentFinder segmentFinder = textBoundsInfo.getGraphemeSegmentFinder();
        final Layout.TextInclusionStrategy inclusionStrategy = INCLUSION_STRATEGY_ANY_OVERLAP;

        // The character 0 and 3 also overlap with the area; the range should be [0, 4).
        final RectF area1 = new RectF(char1Left - 1f, line1Top, char2Right + 1f, line1Bottom);
        assertGetRangeForRect(0, 4, textBoundsInfo, area1, segmentFinder, inclusionStrategy);

        // The character 1 and 2 overlap with the area; the range should be [1, 3).
        final RectF area2 = new RectF(char1Left, line1Top, char2Right, line1Bottom);
        assertGetRangeForRect(1, 3, textBoundsInfo, area2, segmentFinder, inclusionStrategy);


        final float char7Right = getCharacterRight(textBoundsInfo, 7);
        // The area overlaps with 2 lines of text from character 1 to character 7; the range should
        // be [1, 8)
        final RectF area3 = new RectF(char1Left, line1Top, char7Right, line1Bottom + 1f);
        assertGetRangeForRect(1, 8, textBoundsInfo, area3, segmentFinder, inclusionStrategy);

    }

    @Test
    @ApiTest(apis = { "android.view.inputmethod.TextBoundsInfo#getRangeForRect" })
    public void testGetRangeForRect_grapheme_containsAll_LTR() {
        final TextBoundsInfo textBoundsInfo = TEXT_BOUNDS_INFO_LTR;

        // In the test data, all characters in the first line have the same top and bottom.
        final float line1Top = getCharacterTop(textBoundsInfo, 0);
        final float line1Bottom = getCharacterBottom(textBoundsInfo, 0);
        final float char1Left = getCharacterLeft(textBoundsInfo, 1);
        final float char2Right = getCharacterRight(textBoundsInfo, 2);

        final SegmentFinder segmentFinder = textBoundsInfo.getGraphemeSegmentFinder();
        final Layout.TextInclusionStrategy inclusionStrategy = INCLUSION_STRATEGY_CONTAINS_ALL;

        // The character 1 and 2 is contained by the area; the range should be [1, 3).
        final RectF area1 = new RectF(char1Left, line1Top, char2Right, line1Bottom);
        assertGetRangeForRect(1, 3, textBoundsInfo, area1, segmentFinder, inclusionStrategy);


        // No character is contained by the area; the range should be null.
        final RectF area2 = new RectF(char1Left + 1f, line1Top, char2Right - 1f, line1Bottom);
        assertGetRangeForRectIsNull(textBoundsInfo, area2, segmentFinder, inclusionStrategy);


        // In the test data, all characters in the second line have the same top and bottom.
        final float line2Bottom = getCharacterBottom(textBoundsInfo, 5);
        final float char7Right = getCharacterRight(textBoundsInfo, 7);

        // The area contains character 1, 2, 5, 6 and 7; The range should be [1, 8)
        final RectF area3 = new RectF(char1Left, line1Top, char7Right, line2Bottom);
        assertGetRangeForRect(1, 8, textBoundsInfo, area3, segmentFinder, inclusionStrategy);
    }

    @Test
    @ApiTest(apis = { "android.view.inputmethod.TextBoundsInfo#getRangeForRect" })
    public void testGetRangeForRect_grapheme_containsCenter_RTL() {
        final TextBoundsInfo textBoundsInfo = TEXT_BOUNDS_INFO_RTL;

        // In the test data, all characters in the first line have the same top and bottom.
        final float line1CenterY = getCharacterCenterY(textBoundsInfo, 0);
        final float char1CenterX = getCharacterCenterX(textBoundsInfo, 1);
        final float char2CenterX = getCharacterCenterX(textBoundsInfo, 2);

        final SegmentFinder segmentFinder = textBoundsInfo.getGraphemeSegmentFinder();
        final Layout.TextInclusionStrategy inclusionStrategy = INCLUSION_STRATEGY_CONTAINS_CENTER;

        // This area includes the center of character 1 and 2; the range should be [1, 3).
        final RectF area1 = new RectF(char2CenterX - 1f, line1CenterY - 1f,
                char1CenterX + 1f, line1CenterY + 1f);
        assertGetRangeForRect(1, 3, textBoundsInfo, area1, segmentFinder, inclusionStrategy);

        // This area includes the no character center; it should return null.
        final RectF area2 = new RectF(char2CenterX + 1f, line1CenterY - 1f,
                char1CenterX - 1f, line1CenterY + 1f);
        assertGetRangeForRectIsNull(textBoundsInfo, area2, segmentFinder, inclusionStrategy);

        // In the test data, all characters in the second line have the same top and bottom.
        final float line2CenterY = getCharacterCenterY(textBoundsInfo, 5);
        final float char5CenterX = getCharacterCenterX(textBoundsInfo, 5);

        // The character at index 5 and 6 forms a grapheme cluster, and all width is assigned to
        // character 5. The center of the grapheme is included in the area, it should return [5, 7).
        final RectF area3 = new RectF(char5CenterX - 1f, line2CenterY - 1f,
                char5CenterX + 1f, line2CenterY + 1f);
        assertGetRangeForRect(5, 7, textBoundsInfo, area3, segmentFinder, inclusionStrategy);

        // The area covers 2 lines from the character 1 to 7; it should return [1, 8).
        final float char7CenterX = getCharacterCenterX(textBoundsInfo, 7);
        final RectF area4 = new RectF(char7CenterX - 1f, line1CenterY - 1f,
                char1CenterX + 1f, line2CenterY + 1f);
        assertGetRangeForRect(1, 8, textBoundsInfo, area4, segmentFinder, inclusionStrategy);
    }

    @Test
    @ApiTest(apis = { "android.view.inputmethod.TextBoundsInfo#getRangeForRect" })
    public void testGetRangeForRect_grapheme_anyOverlap_RTL() {
        final TextBoundsInfo textBoundsInfo = TEXT_BOUNDS_INFO_RTL;

        // In the test data, all characters in the first line have the same top and bottom.
        final float line1Top = getCharacterTop(textBoundsInfo, 0);
        final float line1Bottom = getCharacterBottom(textBoundsInfo, 0);
        final float char1Right = getCharacterRight(textBoundsInfo, 1);
        final float char2Left = getCharacterLeft(textBoundsInfo, 2);

        final SegmentFinder segmentFinder = textBoundsInfo.getGraphemeSegmentFinder();
        final Layout.TextInclusionStrategy inclusionStrategy = INCLUSION_STRATEGY_ANY_OVERLAP;

        // The character 0 and 3 also overlap with the area; the range should be [0, 4).
        final RectF area1 = new RectF(char2Left - 1f, line1Top, char1Right + 1f, line1Bottom);
        assertGetRangeForRect(0, 4, textBoundsInfo, area1, segmentFinder, inclusionStrategy);

        // The character 1 and 2 overlap with the area; the range should be [1, 3).
        final RectF area2 = new RectF(char2Left, line1Top, char1Right, line1Bottom);
        assertGetRangeForRect(1, 3, textBoundsInfo, area2, segmentFinder, inclusionStrategy);

        final float char7Left = getCharacterLeft(textBoundsInfo, 7);
        // The area overlaps with 2 lines of text from character 1 to character 7; the range should
        // be [1, 8)
        final RectF area3 = new RectF(char7Left, line1Top, char1Right, line1Bottom + 1f);
        assertGetRangeForRect(1, 8, textBoundsInfo, area3, segmentFinder, inclusionStrategy);
    }

    @Test
    @ApiTest(apis = { "android.view.inputmethod.TextBoundsInfo#getRangeForRect" })
    public void testGetRangeForRect_grapheme_containsAll_RTL() {
        final TextBoundsInfo textBoundsInfo = TEXT_BOUNDS_INFO_RTL;

        // In the test data, all characters in the first line have the same top and bottom.
        final float line1Top = getCharacterTop(textBoundsInfo, 0);
        final float line1Bottom = getCharacterBottom(textBoundsInfo, 0);
        final float char1Right = getCharacterRight(textBoundsInfo, 1);
        final float char2Left = getCharacterLeft(textBoundsInfo, 2);

        final SegmentFinder segmentFinder = textBoundsInfo.getGraphemeSegmentFinder();
        final Layout.TextInclusionStrategy inclusionStrategy = INCLUSION_STRATEGY_CONTAINS_ALL;

        // The character 1 and 2 is contained by the area; the range should be [1, 3).
        final RectF area1 = new RectF(char2Left, line1Top, char1Right, line1Bottom);
        assertGetRangeForRect(1, 3, textBoundsInfo, area1, segmentFinder, inclusionStrategy);


        // No character is contained by the area; the range should be null.
        final RectF area2 = new RectF(char2Left + 1f, line1Top, char1Right - 1f, line1Bottom);
        assertGetRangeForRectIsNull(textBoundsInfo, area2, segmentFinder, inclusionStrategy);


        // In the test data, all characters in the second line have the same top and bottom.
        final float line2Bottom = getCharacterBottom(textBoundsInfo, 5);
        final float char7Left = getCharacterLeft(textBoundsInfo, 7);

        // The area contains character 1, 2, 5, 6 and 7; the range should be [1, 8)
        final RectF area3 = new RectF(char7Left, line1Top, char1Right, line2Bottom);
        assertGetRangeForRect(1, 8, textBoundsInfo, area3, segmentFinder, inclusionStrategy);
    }

    @Test
    @ApiTest(apis = { "android.view.inputmethod.TextBoundsInfo#getRangeForRect" })
    public void testGetRangeForRect_grapheme_containsCenter_BiDi() {
        final TextBoundsInfo textBoundsInfo = TEXT_BOUNDS_INFO_BIDI;

        // In the test data, all characters in the first line have the same top and bottom.
        final float line1CenterY = getCharacterCenterY(textBoundsInfo, 0);
        final float char2CenterX = getCharacterCenterX(textBoundsInfo, 2);
        final float char4CenterX = getCharacterCenterX(textBoundsInfo, 4);

        final SegmentFinder segmentFinder = textBoundsInfo.getGraphemeSegmentFinder();
        final Layout.TextInclusionStrategy inclusionStrategy = INCLUSION_STRATEGY_CONTAINS_CENTER;

        // This area includes the center of character 2 and 4; the range should be [2, 5).
        final RectF area1 = new RectF(char2CenterX - 1f, line1CenterY - 1f,
                char4CenterX + 1f, line1CenterY + 1f);
        assertGetRangeForRect(2, 5, textBoundsInfo, area1, segmentFinder, inclusionStrategy);

        // This area includes the no character center; it should return null.
        final RectF area2 = new RectF(char2CenterX + 1f, line1CenterY - 1f,
                char4CenterX - 1f, line1CenterY + 1f);
        assertGetRangeForRectIsNull(textBoundsInfo, area2, segmentFinder, inclusionStrategy);

        // In the test data, all characters in the second line have the same top and bottom.
        final float line2CenterY = getCharacterCenterY(textBoundsInfo, 7);
        final float char11CenterX = getCharacterCenterX(textBoundsInfo, 11);

        // This area includes character 2, 4, 9 and 11; the range should be [2, 12)
        final RectF area3 = new RectF(char11CenterX - 1f, line1CenterY - 1f,
                char4CenterX + 1f, line2CenterY + 1f);
        assertGetRangeForRect(2, 12, textBoundsInfo, area3, segmentFinder, inclusionStrategy);
    }

    @Test
    @ApiTest(apis = { "android.view.inputmethod.TextBoundsInfo#getRangeForRect" })
    public void testGetRangeForRect_grapheme_anyOverlap_BiDi() {
        final TextBoundsInfo textBoundsInfo = TEXT_BOUNDS_INFO_BIDI;

        // In the test data, all characters in the first line have the same top and bottom.
        final float line1Top = getCharacterTop(textBoundsInfo, 0);
        final float line1Bottom = getCharacterBottom(textBoundsInfo, 0);
        final float char2Left = getCharacterLeft(textBoundsInfo, 2);
        final float char4Right = getCharacterRight(textBoundsInfo, 4);

        final SegmentFinder segmentFinder = textBoundsInfo.getGraphemeSegmentFinder();
        final Layout.TextInclusionStrategy inclusionStrategy = INCLUSION_STRATEGY_ANY_OVERLAP;

        // This area overlaps with character 2, 3, 4 and 5; the range should be [2, 6).
        final RectF area1 = new RectF(char2Left - 1f, line1Top, char4Right + 1f, line1Bottom);
        assertGetRangeForRect(2, 6, textBoundsInfo, area1, segmentFinder, inclusionStrategy);

        // This area overlaps with character 2 and 4; the range should be [2, 5).
        final RectF area2 = new RectF(char2Left, line1Top, char4Right, line1Bottom);
        assertGetRangeForRect(2, 5, textBoundsInfo, area2, segmentFinder, inclusionStrategy);

        final float char11Left = getCharacterLeft(textBoundsInfo, 11);

        // This area overlaps with character 2, 4, 9 and 11; the range should be [2, 12)
        final RectF area3 = new RectF(char11Left, line1Top, char4Right, line1Bottom + 1f);
        assertGetRangeForRect(2, 12, textBoundsInfo, area3, segmentFinder, inclusionStrategy);
    }

    @Test
    @ApiTest(apis = { "android.view.inputmethod.TextBoundsInfo#getRangeForRect" })
    public void testGetRangeForRect_grapheme_containsAll_BiDi() {
        final TextBoundsInfo textBoundsInfo = TEXT_BOUNDS_INFO_BIDI;

        // In the test data, all characters in the first line have the same top and bottom.
        final float line1Top = getCharacterTop(textBoundsInfo, 0);
        final float line1Bottom = getCharacterBottom(textBoundsInfo, 0);
        final float char2Left = getCharacterLeft(textBoundsInfo, 2);
        final float char4Right = getCharacterRight(textBoundsInfo, 4);

        final SegmentFinder segmentFinder = textBoundsInfo.getGraphemeSegmentFinder();
        final Layout.TextInclusionStrategy inclusionStrategy = INCLUSION_STRATEGY_CONTAINS_ALL;

        // This area contains with character 2 and 4; the range should be [2, 5).
        final RectF area1 = new RectF(char2Left, line1Top, char4Right, line1Bottom);
        assertGetRangeForRect(2, 5, textBoundsInfo, area1, segmentFinder, inclusionStrategy);

        // This area contains no character; the range should be null.
        final RectF area2 = new RectF(char2Left + 1f, line1Top, char4Right - 1f, line1Bottom);
        assertGetRangeForRectIsNull(textBoundsInfo, area2, segmentFinder, inclusionStrategy);

        final float line2Bottom = getCharacterBottom(textBoundsInfo, 7);
        final float char11Left = getCharacterLeft(textBoundsInfo, 11);

        // This area contains character 2, 4, 9 and 11; the range should be [2, 12)
        final RectF area3 = new RectF(char11Left, line1Top, char4Right, line2Bottom);
        assertGetRangeForRect(2, 12, textBoundsInfo, area3, segmentFinder, inclusionStrategy);
    }

    @Test
    @ApiTest(apis = { "android.view.inputmethod.TextBoundsInfo#getRangeForRect" })
    public void testGetRangeForRect_word_containsCenter_LTR() {
        final TextBoundsInfo textBoundsInfo = TEXT_BOUNDS_INFO_LTR;

        // In the test data, all characters in the first line have the same top and bottom.
        final float line1CenterY = getCharacterCenterY(textBoundsInfo, 0);
        final float word0CenterX = getCharacterCenterX(textBoundsInfo, 0);
        final float word1CenterX = (getCharacterLeft(textBoundsInfo, 2)
                + getCharacterRight(textBoundsInfo, 3)) / 2;

        final SegmentFinder segmentFinder = textBoundsInfo.getWordSegmentFinder();
        final Layout.TextInclusionStrategy inclusionStrategy = INCLUSION_STRATEGY_CONTAINS_CENTER;

        // This area includes the center of word [0, 1) and [2, 4); the range should be [0, 5).
        final RectF area1 = new RectF(word0CenterX - 1f, line1CenterY - 1f,
                word1CenterX + 1f, line1CenterY + 1f);
        assertGetRangeForRect(0, 4, textBoundsInfo, area1, segmentFinder, inclusionStrategy);

        // This area doesn't include any word center.
        final RectF area2 = new RectF(word0CenterX + 1f, line1CenterY - 1f,
                word1CenterX - 1f, line1CenterY + 1f);
        assertGetRangeForRectIsNull(textBoundsInfo, area2, segmentFinder, inclusionStrategy);

        final float line2CenterY = getCharacterCenterY(textBoundsInfo, 5);
        final float word2CenterX = (getCharacterLeft(textBoundsInfo, 5)
                + getCharacterRight(textBoundsInfo, 6)) / 2;

        // The area includes the center of word [0, 1) and [5, 7); the range should be [0, 7).
        final RectF area3 = new RectF(word0CenterX - 1f, line1CenterY - 1f,
                word2CenterX + 1f, line2CenterY + 1f);
        assertGetRangeForRect(0, 7, textBoundsInfo, area3, segmentFinder, inclusionStrategy);
    }

    @Test
    @ApiTest(apis = { "android.view.inputmethod.TextBoundsInfo#getRangeForRect" })
    public void testGetRangeForRect_word_anyOverlap_LTR() {
        final TextBoundsInfo textBoundsInfo = TEXT_BOUNDS_INFO_LTR;

        // In the test data, all characters in the first line have the same top and bottom.
        final float line1Top = getCharacterTop(textBoundsInfo, 0);
        final float line1Bottom = getCharacterBottom(textBoundsInfo, 0);
        final float word0Right = getCharacterRight(textBoundsInfo, 0);
        final float word1Right = getCharacterRight(textBoundsInfo, 3);

        final SegmentFinder segmentFinder = textBoundsInfo.getWordSegmentFinder();
        final Layout.TextInclusionStrategy inclusionStrategy = INCLUSION_STRATEGY_ANY_OVERLAP;

        // This area overlaps the word [0, 1) and [2, 4); the range should be [0, 4).
        final RectF area1 = new RectF(word0Right - 1f, line1Top, word1Right, line1Bottom);
        assertGetRangeForRect(0, 4, textBoundsInfo, area1, segmentFinder, inclusionStrategy);

        // This area overlaps the word [2, 4); the range should be [2, 4).
        final RectF area2 = new RectF(word0Right, line1Top, word1Right, line1Bottom);
        assertGetRangeForRect(2, 4, textBoundsInfo, area2, segmentFinder, inclusionStrategy);

        final float word2Right = getCharacterRight(textBoundsInfo, 6);

        // The area overlaps with of word [0, 1) and [5, 7); the range should be [0, 7).
        final RectF area3 = new RectF(word0Right - 1f, line1Top, word2Right, line1Bottom + 1f);
        assertGetRangeForRect(0, 7, textBoundsInfo, area3, segmentFinder, inclusionStrategy);
    }

    @Test
    @ApiTest(apis = { "android.view.inputmethod.TextBoundsInfo#getRangeForRect" })
    public void testGetRangeForRect_word_containsAll_LTR() {
        final TextBoundsInfo textBoundsInfo = TEXT_BOUNDS_INFO_LTR;

        // In the test data, all characters in the first line have the same top and bottom.
        final float line1Top = getCharacterTop(textBoundsInfo, 0);
        final float line1Bottom = getCharacterBottom(textBoundsInfo, 0);
        final float word0Left = getCharacterLeft(textBoundsInfo, 0);
        final float word1Right = getCharacterRight(textBoundsInfo, 3);

        final SegmentFinder segmentFinder = textBoundsInfo.getWordSegmentFinder();
        final Layout.TextInclusionStrategy inclusionStrategy = INCLUSION_STRATEGY_CONTAINS_ALL;

        // This area contains the word [0, 1) and [2, 4); the range should be [0, 5).
        final RectF area1 = new RectF(word0Left, line1Top, word1Right, line1Bottom);
        assertGetRangeForRect(0, 4, textBoundsInfo, area1, segmentFinder, inclusionStrategy);


        // This area contains the no word; the range should be null.
        final RectF area2 = new RectF(word0Left + 1f, line1Top, word1Right - 1f, line1Bottom);
        assertGetRangeForRectIsNull(textBoundsInfo, area2, segmentFinder, inclusionStrategy);

        final float line2Bottom = getCharacterBottom(textBoundsInfo, 5);
        final float word2Right = getCharacterRight(textBoundsInfo, 6);

        // The area contains with of word [0, 1) and [5, 7); the range should be [0, 7).
        final RectF area3 = new RectF(word0Left, line1Top, word2Right, line2Bottom);
        assertGetRangeForRect(0, 7, textBoundsInfo, area3, segmentFinder, inclusionStrategy);
    }


    @Test
    @ApiTest(apis = { "android.view.inputmethod.TextBoundsInfo#getRangeForRect" })
    public void testGetRangeForRect_word_containsCenter_RTL() {
        final TextBoundsInfo textBoundsInfo = TEXT_BOUNDS_INFO_RTL;

        // In the test data, all characters in the first line have the same top and bottom.
        final float line1CenterY = getCharacterCenterY(textBoundsInfo, 0);
        final float word0CenterX = getCharacterCenterX(textBoundsInfo, 0);
        final float word1CenterX = (getCharacterRight(textBoundsInfo, 2)
                + getCharacterLeft(textBoundsInfo, 3)) / 2;

        final SegmentFinder segmentFinder = textBoundsInfo.getWordSegmentFinder();
        final Layout.TextInclusionStrategy inclusionStrategy = INCLUSION_STRATEGY_CONTAINS_CENTER;

        // This area includes the center of word [0, 1) and [2, 4); the range should be [0, 5).
        final RectF area1 = new RectF(word1CenterX - 1f, line1CenterY - 1f,
                word0CenterX + 1f, line1CenterY + 1f);
        assertGetRangeForRect(0, 4, textBoundsInfo, area1, segmentFinder, inclusionStrategy);

        // This area doesn't include any word center.
        final RectF area2 = new RectF(word1CenterX + 1f, line1CenterY - 1f,
                word0CenterX - 1f, line1CenterY + 1f);
        assertGetRangeForRectIsNull(textBoundsInfo, area2, segmentFinder, inclusionStrategy);

        final float line2CenterY = getCharacterCenterY(textBoundsInfo, 5);
        final float word2CenterX = (getCharacterRight(textBoundsInfo, 5)
                + getCharacterLeft(textBoundsInfo, 6)) / 2;

        // The area includes the center of word [0, 1) and [5, 7); the range should be [0, 7).
        final RectF area3 = new RectF(word0CenterX - 1f, line1CenterY - 1f,
                word2CenterX + 1f, line2CenterY + 1f);
        assertGetRangeForRect(0, 7, textBoundsInfo, area3, segmentFinder, inclusionStrategy);
    }

    @Test
    @ApiTest(apis = { "android.view.inputmethod.TextBoundsInfo#getRangeForRect" })
    public void testGetRangeForRect_word_anyOverlap_RTL() {
        final TextBoundsInfo textBoundsInfo = TEXT_BOUNDS_INFO_RTL;

        // In the test data, all characters in the first line have the same top and bottom.
        final float line1Top = getCharacterTop(textBoundsInfo, 0);
        final float line1Bottom = getCharacterBottom(textBoundsInfo, 0);
        final float word0Left = getCharacterLeft(textBoundsInfo, 0);
        final float word1Left = getCharacterLeft(textBoundsInfo, 3);

        final SegmentFinder segmentFinder = textBoundsInfo.getWordSegmentFinder();
        final Layout.TextInclusionStrategy inclusionStrategy = INCLUSION_STRATEGY_ANY_OVERLAP;

        // This area overlaps the word [0, 1) and [2, 4); the range should be [0, 4).
        final RectF area1 = new RectF(word1Left, line1Top, word0Left + 1f, line1Bottom);
        assertGetRangeForRect(0, 4, textBoundsInfo, area1, segmentFinder, inclusionStrategy);

        // This area overlaps the word [2, 4); the range should be [2, 4).
        final RectF area2 = new RectF(word1Left, line1Top, word0Left - 1f, line1Bottom);
        assertGetRangeForRect(2, 4, textBoundsInfo, area2, segmentFinder, inclusionStrategy);

        final float word2Left = getCharacterLeft(textBoundsInfo, 6);

        // The area overlaps with of word [0, 1) and [5, 7); the range should be [0, 7).
        final RectF area3 = new RectF(word0Left, line1Top, word2Left + 1f, line1Bottom + 1f);
        assertGetRangeForRect(0, 7, textBoundsInfo, area3, segmentFinder, inclusionStrategy);
    }

    @Test
    @ApiTest(apis = { "android.view.inputmethod.TextBoundsInfo#getRangeForRect" })
    public void testGetRangeForRect_word_containsAll_RTL() {
        final TextBoundsInfo textBoundsInfo = TEXT_BOUNDS_INFO_RTL;

        // In the test data, all characters in the first line have the same top and bottom.
        final float line1Top = getCharacterTop(textBoundsInfo, 0);
        final float line1Bottom = getCharacterBottom(textBoundsInfo, 0);
        final float word0Right = getCharacterRight(textBoundsInfo, 0);
        final float word1Left = getCharacterLeft(textBoundsInfo, 3);

        final SegmentFinder segmentFinder = textBoundsInfo.getWordSegmentFinder();
        final Layout.TextInclusionStrategy inclusionStrategy = INCLUSION_STRATEGY_CONTAINS_ALL;

        // This area contains the word [0, 1) and [2, 4); the range should be [0, 5).
        final RectF area1 = new RectF(word1Left, line1Top, word0Right, line1Bottom);
        assertGetRangeForRect(0, 4, textBoundsInfo, area1, segmentFinder, inclusionStrategy);


        // This area contains the no word; the range should be null.
        final RectF area2 = new RectF(word1Left + 1f, line1Top, word0Right - 1f, line1Bottom);
        assertGetRangeForRectIsNull(textBoundsInfo, area2, segmentFinder, inclusionStrategy);

        final float line2Bottom = getCharacterBottom(textBoundsInfo, 5);
        final float word2Left = getCharacterLeft(textBoundsInfo, 6);

        // The area contains with of word [0, 1) and [5, 7); the range should be [0, 7).
        final RectF area3 = new RectF(word2Left, line1Top, word0Right, line2Bottom);
        assertGetRangeForRect(0, 7, textBoundsInfo, area3, segmentFinder, inclusionStrategy);
    }

    @Test
    @ApiTest(apis = { "android.view.inputmethod.TextBoundsInfo#getRangeForRect" })
    public void testGetRangeForRect_word_containsCenter_BiDi() {
        final TextBoundsInfo textBoundsInfo = TEXT_BOUNDS_INFO_BIDI;

        // In the test data, all characters in the first line have the same top and bottom.
        final float line1CenterY = getCharacterCenterY(textBoundsInfo, 0);
        final float word0CenterX = (getCharacterLeft(textBoundsInfo, 0)
                + getCharacterRight(textBoundsInfo, 1)) / 2;

        final SegmentFinder segmentFinder = textBoundsInfo.getWordSegmentFinder();
        final Layout.TextInclusionStrategy inclusionStrategy = INCLUSION_STRATEGY_CONTAINS_CENTER;

        // This area includes the center the word [0, 2); the range should be [0, 2).
        final RectF area1 = new RectF(word0CenterX - 1f, line1CenterY - 1f,
                word0CenterX + 1f, line1CenterY + 1f);
        assertGetRangeForRect(0, 2, textBoundsInfo, area1, segmentFinder, inclusionStrategy);

        final float word2CenterX = (getCharacterLeft(textBoundsInfo, 4)
                + getCharacterRight(textBoundsInfo, 5)) / 2;
        // This area includes the center of the word [4, 6); the range should be [4, 6).
        final RectF area2 = new RectF(word2CenterX - 1f, line1CenterY - 1f,
                word2CenterX + 1f, line1CenterY + 1f);
        assertGetRangeForRect(4, 6, textBoundsInfo, area2, segmentFinder, inclusionStrategy);

        // In the test data, all characters in the second line have the same top and bottom.
        final float line2CenterY = getCharacterCenterY(textBoundsInfo, 7);
        // The word [7, 13) contains multiple runs [7, 9) [9, 11) [11, 13). If the area includes
        // any of the run the entire words is included.
        final float word3run0CenterX = (getCharacterLeft(textBoundsInfo, 7)
                + getCharacterRight(textBoundsInfo, 8)) / 2;

        // This area includes the center of BiDi run [7, 9) which belongs to word [7, 13); the
        // range should be [7, 13).
        final RectF area3 = new RectF(word3run0CenterX - 1f, line1CenterY - 1f,
                word3run0CenterX + 1f, line2CenterY + 1f);
        assertGetRangeForRect(7, 13, textBoundsInfo, area3, segmentFinder, inclusionStrategy);
    }

    @Test
    @ApiTest(apis = { "android.view.inputmethod.TextBoundsInfo#getRangeForRect" })
    public void testGetRangeForRect_word_anyOverlap_BiDi() {
        final TextBoundsInfo textBoundsInfo = TEXT_BOUNDS_INFO_BIDI;

        // In the test data, all characters in the first line have the same top and bottom.
        final float line1Top = getCharacterTop(textBoundsInfo, 0);
        final float line1Bottom = getCharacterBottom(textBoundsInfo, 0);
        final float word0LeftX = getCharacterLeft(textBoundsInfo, 0);
        final float word2LeftX = getCharacterLeft(textBoundsInfo, 4);

        final SegmentFinder segmentFinder = textBoundsInfo.getWordSegmentFinder();
        final Layout.TextInclusionStrategy inclusionStrategy = INCLUSION_STRATEGY_ANY_OVERLAP;

        // This area overlaps with the word [0, 2), [2, 4); the range should be [0, 4).
        final RectF area1 = new RectF(word0LeftX, line1Top, word2LeftX - 1f, line1Bottom);
        assertGetRangeForRect(0, 4, textBoundsInfo, area1, segmentFinder, inclusionStrategy);

        // This area overlaps with the word [0, 2), [2, 4) and [4, 6); the range should be [0, 6).
        final RectF area2 = new RectF(word0LeftX, line1Top, word2LeftX + 1f, line1Bottom);
        assertGetRangeForRect(0, 6, textBoundsInfo, area2, segmentFinder, inclusionStrategy);

        // This area overlaps with the word [0, 2), [2, 4) and [7, 13); the range should be [0, 13).
        final RectF area3 = new RectF(word0LeftX, line1Top, word2LeftX, line1Bottom + 1f);
        assertGetRangeForRect(0, 13, textBoundsInfo, area3, segmentFinder, inclusionStrategy);
    }

    @Test
    @ApiTest(apis = { "android.view.inputmethod.TextBoundsInfo#getRangeForRect" })
    public void testGetRangeForRect_word_containsAll_BiDi() {
        final TextBoundsInfo textBoundsInfo = TEXT_BOUNDS_INFO_BIDI;

        // In the test data, all characters in the first line have the same top and bottom.
        final float line1Top = getCharacterTop(textBoundsInfo, 0);
        final float line1Bottom = getCharacterBottom(textBoundsInfo, 0);
        final float word0LeftX = getCharacterLeft(textBoundsInfo, 0);
        final float word1RightX = getCharacterRight(textBoundsInfo, 2);

        final SegmentFinder segmentFinder = textBoundsInfo.getWordSegmentFinder();
        final Layout.TextInclusionStrategy inclusionStrategy = INCLUSION_STRATEGY_CONTAINS_ALL;

        // This area contains the word [0, 2), [2, 4); the range should be [0, 4).
        final RectF area1 = new RectF(word0LeftX, line1Top, word1RightX, line1Bottom);
        assertGetRangeForRect(0, 4, textBoundsInfo, area1, segmentFinder, inclusionStrategy);

        // This area contains the word [0, 2); the range should be [0, 2).
        final RectF area2 = new RectF(word0LeftX, line1Top, word1RightX - 1f, line1Bottom);
        assertGetRangeForRect(0, 2, textBoundsInfo, area2, segmentFinder, inclusionStrategy);

        final float line2Top = getCharacterTop(textBoundsInfo, 7);
        final float line2Bottom = getCharacterBottom(textBoundsInfo, 7);
        // The word [7, 13) contains multiple runs [7, 9) [9, 11) [11, 13). If the area includes
        // any of the run the entire words is included.
        final float word3run0Left = getCharacterLeft(textBoundsInfo, 8);
        final float word3run0Right = getCharacterRight(textBoundsInfo, 7);
        // This area contains the run [7, 9) which belongs to the word [7, 13); the range should be
        // [7, 13).
        final RectF area3 = new RectF(word3run0Left, line2Top, word3run0Right, line2Bottom);
        assertGetRangeForRect(7, 13, textBoundsInfo, area3, segmentFinder, inclusionStrategy);
    }

    /**
     * Helper method to assert that {@link TextBoundsInfo#getOffsetForPosition(float, float)}
     * returns the expected index for each character's left edge.
     */
    private static void assertGetOffsetForCharacterLeft(int[] expectedIndex,
            TextBoundsInfo textBoundsInfo, int start, int end) {
        for (int index = start; index < end; ++index) {
            final RectF characterBounds = new RectF();
            textBoundsInfo.getCharacterBounds(index, characterBounds);
            final float centerY = getCharacterCenterY(textBoundsInfo, index);

            assertThat(textBoundsInfo.getOffsetForPosition(characterBounds.left, centerY))
                    .isEqualTo(expectedIndex[index - start]);
            assertThat(textBoundsInfo.getOffsetForPosition(characterBounds.left + 1f, centerY))
                    .isEqualTo(expectedIndex[index - start]);
            assertThat(textBoundsInfo.getOffsetForPosition(characterBounds.left - 1f, centerY))
                    .isEqualTo(expectedIndex[index - start]);
        }
    }

    /**
     * Helper method to assert that {@link TextBoundsInfo#getOffsetForPosition(float, float)}
     * returns the expected index for each character's right edge.
     */
    private static void assertGetOffsetForCharacterRight(int[] expectedIndex,
            TextBoundsInfo textBoundsInfo, int start, int end) {
        for (int index = start; index < end; ++index) {
            final RectF characterBounds = new RectF();
            textBoundsInfo.getCharacterBounds(index, characterBounds);
            final float centerY = characterBounds.centerY();

            assertThat(textBoundsInfo.getOffsetForPosition(characterBounds.right, centerY))
                    .isEqualTo(expectedIndex[index - start]);
            assertThat(textBoundsInfo.getOffsetForPosition(characterBounds.right + 1f, centerY))
                    .isEqualTo(expectedIndex[index - start]);
            assertThat(textBoundsInfo.getOffsetForPosition(characterBounds.right - 1f, centerY))
                    .isEqualTo(expectedIndex[index - start]);
        }
    }

    private static void assertGetRangeForRect(int expectedRangeStart, int expectedRangeEnd,
            TextBoundsInfo textBoundsInfo, RectF area, SegmentFinder segmentFinder,
            Layout.TextInclusionStrategy textInclusionStrategy) {
        final int[] actualRange =
               textBoundsInfo.getRangeForRect(area, segmentFinder, textInclusionStrategy);
        assertThat(actualRange).isEqualTo(new int[] { expectedRangeStart, expectedRangeEnd });
    }

    private static void assertGetRangeForRectIsNull(TextBoundsInfo textBoundsInfo, RectF area,
            SegmentFinder segmentFinder, Layout.TextInclusionStrategy textInclusionStrategy) {
        final int[] actualRange =
                textBoundsInfo.getRangeForRect(area, segmentFinder, textInclusionStrategy);
        assertThat(actualRange).isNull();
    }

    private static float getCharacterTop(TextBoundsInfo textBoundsInfo, int index) {
        final RectF bounds = new RectF();
        textBoundsInfo.getCharacterBounds(index, bounds);
        return bounds.top;
    }

    private static float getCharacterBottom(TextBoundsInfo textBoundsInfo, int index) {
        final RectF bounds = new RectF();
        textBoundsInfo.getCharacterBounds(index, bounds);
        return bounds.bottom;
    }

    private static float getCharacterLeft(TextBoundsInfo textBoundsInfo, int index) {
        final RectF bounds = new RectF();
        textBoundsInfo.getCharacterBounds(index, bounds);
        return bounds.left;
    }

    private static float getCharacterRight(TextBoundsInfo textBoundsInfo, int index) {
        final RectF bounds = new RectF();
        textBoundsInfo.getCharacterBounds(index, bounds);
        return bounds.right;
    }

    private static float getCharacterCenterX(TextBoundsInfo textBoundsInfo, int index) {
        final RectF bounds = new RectF();
        textBoundsInfo.getCharacterBounds(index, bounds);
        return bounds.centerX();
    }

    private static float getCharacterCenterY(TextBoundsInfo textBoundsInfo, int index) {
        final RectF bounds = new RectF();
        textBoundsInfo.getCharacterBounds(index, bounds);
        return bounds.centerY();
    }
}
