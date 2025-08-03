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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.graphics.Matrix;
import android.graphics.RectF;
import android.os.Parcel;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.text.SegmentFinder;
import android.view.inputmethod.TextBoundsInfo;
import android.view.inputmethod.TextBoundsInfoResult;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class TextBoundsInfoTest {
    private static final float[] CHARACTER_BOUNDS1 = new float[] {
            0.0f, 0.0f, 10.0f, 20.0f,
            10.0f, 0.0f, 20.0f, 20.0f,
            20.0f, 0.0f, 30.0f, 20.0f,
            10.0f, 20.0f, 20.0f, 40.0f,
            0.0f, 20.0f, 10.0f, 40.0f};

    private static final float[] CHARACTER_BOUNDS2 = new float[] {
            20.0f, 0.0f, 30.0f, 20.0f,
            10.0f, 0.0f, 20.0f, 20.0f,
            0.0f, 0.0f, 10.0f, 20.0f,
            0.0f, 20.0f, 10.0f, 40.0f,
            10.0f, 20.0f, 20.0f, 40.0f};

    private static final int LTR_BIDI_LEVEL = 0;
    private static final int RTL_BIDI_LEVEL = 1;
    private static final int MAX_BIDI_LEVEL = 125;
    private static final int[] CHARACTER_BIDI_LEVEL1 = new int[] {LTR_BIDI_LEVEL, LTR_BIDI_LEVEL,
            LTR_BIDI_LEVEL, RTL_BIDI_LEVEL, MAX_BIDI_LEVEL};
    private static final int[] CHARACTER_BIDI_LEVEL2 = new int[] {MAX_BIDI_LEVEL, RTL_BIDI_LEVEL,
            LTR_BIDI_LEVEL, LTR_BIDI_LEVEL, LTR_BIDI_LEVEL};
    private static final int[] CHARACTER_FLAGS1 = new int[] {
            TextBoundsInfo.FLAG_CHARACTER_WHITESPACE,
            TextBoundsInfo.FLAG_CHARACTER_PUNCTUATION,
            TextBoundsInfo.FLAG_CHARACTER_LINEFEED,
            TextBoundsInfo.FLAG_LINE_IS_RTL,
            TextBoundsInfo.FLAG_LINE_IS_RTL
    };

    private static final int[] CHARACTER_FLAGS2 = new int[] {TextBoundsInfo.FLAG_LINE_IS_RTL,
            TextBoundsInfo.FLAG_LINE_IS_RTL, TextBoundsInfo.FLAG_CHARACTER_WHITESPACE, 0, 0};

    private static final SegmentFinder GRAPHEME_SEGMENT_FINDER1 =
            new SegmentFinder.PrescribedSegmentFinder(
                    new int[] { 0, 1, 1, 2, 2, 4, 4, 5, 5, 7, 7, 8, 8, 9, 9, 10, 10, 11 });

    private static final SegmentFinder GRAPHEME_SEGMENT_FINDER2 =
            new SegmentFinder.PrescribedSegmentFinder(
                    new int[] { 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10 });

    private static final SegmentFinder WORD_SEGMENT_FINDER1 =
            new SegmentFinder.PrescribedSegmentFinder(new int[] { 0, 3, 4, 5, 5, 10 });
    private static final SegmentFinder WORD_SEGMENT_FINDER2 =
            new SegmentFinder.PrescribedSegmentFinder(new int[] { 0, 4, 5, 8, 9, 11 });


    private static final SegmentFinder LINE_SEGMENT_FINDER1 =
            new SegmentFinder.PrescribedSegmentFinder(new int[] { 0, 5, 5, 8, 8, 10, 10, 15 });
    private static final SegmentFinder LINE_SEGMENT_FINDER2 =
            new SegmentFinder.PrescribedSegmentFinder(new int[] { 0, 5, 5, 7, 7, 10, 10, 15 });

    @Test
    @ApiTest(
            apis = {
                    "android.view.inputmethod.TextBoundsInfo#getStart",
                    "android.view.inputmethod.TextBoundsInfo#getEnd",
                    "android.view.inputmethod.TextBoundsInfo#getMatrix",
                    "android.view.inputmethod.TextBoundsInfo#getCharacterBounds",
                    "android.view.inputmethod.TextBoundsInfo#getCharacterFlags",
                    "android.view.inputmethod.TextBoundsInfo#getCharacterBidiLevel",
                    "android.view.inputmethod.TextBoundsInfo#getGraphemeSegmentFinder",
                    "android.view.inputmethod.TextBoundsInfo#getWordSegmentFinder",
                    "android.view.inputmethod.TextBoundsInfo#getLineSegmentFinder",
                    "android.view.inputmethod.TextBoundsInfo.Builder#setStart",
                    "android.view.inputmethod.TextBoundsInfo.Builder#setEnd",
                    "android.view.inputmethod.TextBoundsInfo.Builder#setMatrix",
                    "android.view.inputmethod.TextBoundsInfo.Builder#setCharacterBounds",
                    "android.view.inputmethod.TextBoundsInfo.Builder#setCharacterFlags",
                    "android.view.inputmethod.TextBoundsInfo.Builder#setCharacterBidiLevel",
                    "android.view.inputmethod.TextBoundsInfo.Builder#setGraphemeSegmentFinder",
                    "android.view.inputmethod.TextBoundsInfo.Builder#setWordSegmentFinder",
                    "android.view.inputmethod.TextBoundsInfo.Builder#setLineSegmentFinder",
                    "android.view.inputmethod.TextBoundsInfo.Builder#build",
            }
    )
    public void testBuilder() {
        final int start1 = 5;
        final int end1 = 10;
        final Matrix matrix1 = new Matrix();
        matrix1.setRotate(10f);

        TextBoundsInfo.Builder builder = new TextBoundsInfo.Builder(start1, end1);
        TextBoundsInfo textBoundsInfo1 = builder.setMatrix(matrix1)
                .setCharacterBounds(CHARACTER_BOUNDS1)
                .setCharacterBidiLevel(CHARACTER_BIDI_LEVEL1)
                .setCharacterFlags(CHARACTER_FLAGS1)
                .setGraphemeSegmentFinder(GRAPHEME_SEGMENT_FINDER1)
                .setWordSegmentFinder(WORD_SEGMENT_FINDER1)
                .setLineSegmentFinder(LINE_SEGMENT_FINDER1)
                .build();

        assertEquals(start1, textBoundsInfo1.getStartIndex());
        assertEquals(end1, textBoundsInfo1.getEndIndex());
        final Matrix actualMatrix = new Matrix();
        textBoundsInfo1.getMatrix(actualMatrix);
        assertEquals(matrix1, actualMatrix);
        assertCharacterBounds(CHARACTER_BOUNDS1, textBoundsInfo1);
        assertCharacterBidiLevel(CHARACTER_BIDI_LEVEL1, textBoundsInfo1);
        assertCharacterFlags(CHARACTER_FLAGS1, textBoundsInfo1);
        assertEquals(GRAPHEME_SEGMENT_FINDER1, textBoundsInfo1.getGraphemeSegmentFinder());
        assertEquals(WORD_SEGMENT_FINDER1, textBoundsInfo1.getWordSegmentFinder());
        assertEquals(LINE_SEGMENT_FINDER1, textBoundsInfo1.getLineSegmentFinder());

        // Build another TextBoundsInfo, making sure the Builder creates an identical object.
        TextBoundsInfo textBoundsInfo2 = builder.build();
        assertEquals(start1, textBoundsInfo2.getStartIndex());
        assertEquals(end1, textBoundsInfo2.getEndIndex());
        textBoundsInfo2.getMatrix(actualMatrix);
        assertEquals(matrix1, actualMatrix);
        assertCharacterBounds(CHARACTER_BOUNDS1, textBoundsInfo2);
        assertCharacterBidiLevel(CHARACTER_BIDI_LEVEL1, textBoundsInfo2);
        assertCharacterFlags(CHARACTER_FLAGS1, textBoundsInfo2);
        assertEquals(GRAPHEME_SEGMENT_FINDER1, textBoundsInfo2.getGraphemeSegmentFinder());
        assertEquals(WORD_SEGMENT_FINDER1, textBoundsInfo2.getWordSegmentFinder());
        assertEquals(LINE_SEGMENT_FINDER1, textBoundsInfo2.getLineSegmentFinder());

        final int start2 = 8;
        final int end2 = 13;
        final Matrix matrix2 = new Matrix();
        matrix2.setTranslate(20f, 30f);

        // Clear the existing parameters and build a different object.
        TextBoundsInfo textBoundsInfo3 = builder.clear()
                .setStartAndEnd(start2, end2)
                .setMatrix(matrix2)
                .setCharacterBounds(CHARACTER_BOUNDS2)
                .setCharacterBidiLevel(CHARACTER_BIDI_LEVEL2)
                .setCharacterFlags(CHARACTER_FLAGS2)
                .setGraphemeSegmentFinder(GRAPHEME_SEGMENT_FINDER2)
                .setWordSegmentFinder(WORD_SEGMENT_FINDER2)
                .setLineSegmentFinder(LINE_SEGMENT_FINDER2)
                .build();

        assertEquals(start2, textBoundsInfo3.getStartIndex());
        assertEquals(end2, textBoundsInfo3.getEndIndex());
        textBoundsInfo3.getMatrix(actualMatrix);
        assertEquals(matrix2, actualMatrix);
        assertCharacterBounds(CHARACTER_BOUNDS2, textBoundsInfo3);
        assertCharacterBidiLevel(CHARACTER_BIDI_LEVEL2, textBoundsInfo3);
        assertCharacterFlags(CHARACTER_FLAGS2, textBoundsInfo3);
        assertEquals(GRAPHEME_SEGMENT_FINDER2, textBoundsInfo3.getGraphemeSegmentFinder());
        assertEquals(WORD_SEGMENT_FINDER2, textBoundsInfo3.getWordSegmentFinder());
        assertEquals(LINE_SEGMENT_FINDER2, textBoundsInfo3.getLineSegmentFinder());
    }

    @Test
    @ApiTest(apis = { "android.view.inputmethod.TextBoundsInfo#writeToParcel" })
    public void testTextBoundsInfo_writeToParcel() {
        final Matrix matrix = new Matrix();
        matrix.setRotate(15f);

        TextBoundsInfo textBoundsInfo1 = new TextBoundsInfo.Builder(5, 10)
                .setMatrix(matrix)
                .setCharacterBounds(CHARACTER_BOUNDS1)
                .setCharacterBidiLevel(CHARACTER_BIDI_LEVEL1)
                .setCharacterFlags(CHARACTER_FLAGS1)
                .setGraphemeSegmentFinder(GRAPHEME_SEGMENT_FINDER1)
                .setWordSegmentFinder(WORD_SEGMENT_FINDER1)
                .setLineSegmentFinder(LINE_SEGMENT_FINDER1)
                .build();

        // Gut check, making sure the first TextBoundsInfo is built correctly.
        assertEquals(5, textBoundsInfo1.getStartIndex());
        assertEquals(10, textBoundsInfo1.getEndIndex());
        final Matrix actualMatrix = new Matrix();
        textBoundsInfo1.getMatrix(actualMatrix);
        assertEquals(matrix, actualMatrix);
        assertCharacterBounds(CHARACTER_BOUNDS1, textBoundsInfo1);
        assertCharacterFlags(CHARACTER_FLAGS1, textBoundsInfo1);
        assertCharacterBidiLevel(CHARACTER_BIDI_LEVEL1, textBoundsInfo1);
        assertEquals(GRAPHEME_SEGMENT_FINDER1, textBoundsInfo1.getGraphemeSegmentFinder());
        assertEquals(WORD_SEGMENT_FINDER1, textBoundsInfo1.getWordSegmentFinder());
        assertEquals(LINE_SEGMENT_FINDER1, textBoundsInfo1.getLineSegmentFinder());

        Parcel parcel = Parcel.obtain();
        final int dataPosition = parcel.dataPosition();
        parcel.writeParcelable(textBoundsInfo1, 0);
        parcel.setDataPosition(dataPosition);

        TextBoundsInfo textBoundsInfo2 =
                parcel.readParcelable(TextBoundsInfo.class.getClassLoader(), TextBoundsInfo.class);

        assertEquals(5, textBoundsInfo2.getStartIndex());
        assertEquals(10, textBoundsInfo2.getEndIndex());
        textBoundsInfo2.getMatrix(actualMatrix);
        assertEquals(matrix, actualMatrix);
        assertCharacterBounds(CHARACTER_BOUNDS1, textBoundsInfo2);
        assertCharacterFlags(CHARACTER_FLAGS1, textBoundsInfo1);
        assertCharacterBidiLevel(CHARACTER_BIDI_LEVEL1, textBoundsInfo1);
        // Only check the iterator in the given range.
        assertSegmentFinderEqualsInRange(GRAPHEME_SEGMENT_FINDER1,
                textBoundsInfo2.getGraphemeSegmentFinder(), 5, 10);
        assertSegmentFinderEqualsInRange(WORD_SEGMENT_FINDER1,
                textBoundsInfo2.getWordSegmentFinder(), 5, 10);
        assertSegmentFinderEqualsInRange(LINE_SEGMENT_FINDER1,
                textBoundsInfo2.getLineSegmentFinder(), 5, 10);
    }


    @Test(expected = IllegalStateException.class)
    @ApiTest(apis = { "android.view.inputmethod.TextBoundsInfo.Builder#build" })
    public void testBuilder_matrix_isRequired() {
        new TextBoundsInfo.Builder(5, 10)
                .setCharacterBounds(CHARACTER_BOUNDS1)
                .setCharacterBidiLevel(CHARACTER_BIDI_LEVEL1)
                .setCharacterFlags(CHARACTER_FLAGS1)
                .setGraphemeSegmentFinder(GRAPHEME_SEGMENT_FINDER1)
                .setWordSegmentFinder(WORD_SEGMENT_FINDER2)
                .setLineSegmentFinder(LINE_SEGMENT_FINDER1)
                .build();
    }

    @Test(expected = IllegalStateException.class)
    @ApiTest(apis = { "android.view.inputmethod.TextBoundsInfo.Builder#build" })
    public void testBuilder_startAndEnd_isRequired() {
        final TextBoundsInfo.Builder builder = new TextBoundsInfo.Builder(5, 10);
        builder.clear();
        builder.setMatrix(Matrix.IDENTITY_MATRIX)
                .setCharacterBounds(CHARACTER_BOUNDS1)
                .setCharacterBidiLevel(CHARACTER_BIDI_LEVEL1)
                .setCharacterFlags(CHARACTER_FLAGS1)
                .setGraphemeSegmentFinder(GRAPHEME_SEGMENT_FINDER1)
                .setWordSegmentFinder(WORD_SEGMENT_FINDER2)
                .setLineSegmentFinder(LINE_SEGMENT_FINDER1)
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    @ApiTest(apis = { "android.view.inputmethod.TextBoundsInfo.Builder#setStart" })
    public void testBuilder_start_isNegative() {
        new TextBoundsInfo.Builder(-1 , 5);
    }

    @Test(expected = IllegalArgumentException.class)
    @ApiTest(apis = { "android.view.inputmethod.TextBoundsInfo.Builder#setEnd" })
    public void testBuilder_end_isNegative() {
        new TextBoundsInfo.Builder(0, -1);
    }

    @Test(expected = IllegalArgumentException.class)
    @ApiTest(apis = { "android.view.inputmethod.TextBoundsInfo.Builder#build" })
    public void testBuilder_startGreaterThanEnd() {
        new TextBoundsInfo.Builder(1, 0);
    }

    @Test(expected = IllegalStateException.class)
    @ApiTest(apis = { "android.view.inputmethod.TextBoundsInfo.Builder#build" })
    public void testBuilder_characterBounds_isRequired() {
        new TextBoundsInfo.Builder(5, 10)
                .setMatrix(Matrix.IDENTITY_MATRIX)
                .setCharacterFlags(CHARACTER_FLAGS1)
                .setCharacterBidiLevel(CHARACTER_BIDI_LEVEL1)
                .setGraphemeSegmentFinder(GRAPHEME_SEGMENT_FINDER1)
                .setWordSegmentFinder(WORD_SEGMENT_FINDER2)
                .setLineSegmentFinder(LINE_SEGMENT_FINDER1)
                .build();
    }

    @Test(expected = NullPointerException.class)
    @ApiTest(apis = { "android.view.inputmethod.TextBoundsInfo.Builder#setCharacterBounds" })
    public void testBuilder_characterBounds_isNull() {
        new TextBoundsInfo.Builder(0, 5).setCharacterBounds(null);
    }

    @Test(expected = IllegalStateException.class)
    @ApiTest(apis = { "android.view.inputmethod.TextBoundsInfo.Builder#build" })
    public void testBuilder_characterBounds_wrongLength() {
        // Expected characterBounds.length == 20 to match the given range [0, 5).
        new TextBoundsInfo.Builder(5, 10)
                .setMatrix(Matrix.IDENTITY_MATRIX)
                .setCharacterBounds(new float[16])
                .setCharacterFlags(CHARACTER_FLAGS1)
                .setCharacterBidiLevel(CHARACTER_BIDI_LEVEL1)
                .setGraphemeSegmentFinder(GRAPHEME_SEGMENT_FINDER1)
                .setWordSegmentFinder(WORD_SEGMENT_FINDER2)
                .setLineSegmentFinder(LINE_SEGMENT_FINDER1)
                .build();
    }

    @Test(expected = IllegalStateException.class)
    @ApiTest(apis = { "android.view.inputmethod.TextBoundsInfo.Builder#build" })
    public void testBuilder_characterFlags_isRequired() {
        new TextBoundsInfo.Builder(5, 10)
                .setMatrix(Matrix.IDENTITY_MATRIX)
                .setCharacterBounds(CHARACTER_BOUNDS1)
                .setCharacterBidiLevel(CHARACTER_BIDI_LEVEL1)
                .setGraphemeSegmentFinder(GRAPHEME_SEGMENT_FINDER1)
                .setWordSegmentFinder(WORD_SEGMENT_FINDER2)
                .setLineSegmentFinder(LINE_SEGMENT_FINDER1)
                .build();
    }

    @Test(expected = NullPointerException.class)
    @ApiTest(apis = { "android.view.inputmethod.TextBoundsInfo.Builder#setCharacterFlags" })
    public void testBuilder_characterFlags_isNull() {
        new TextBoundsInfo.Builder(5, 10).setCharacterFlags(null);
    }

    @Test(expected = IllegalStateException.class)
    @ApiTest(apis = { "android.view.inputmethod.TextBoundsInfo.Builder#build" })
    public void testBuilder_characterFlags_wrongLength() {
        // Expected characterFlags.length == 5 to match the given range [0, 5).
        new TextBoundsInfo.Builder(5, 10)
                .setMatrix(Matrix.IDENTITY_MATRIX)
                .setCharacterFlags(new int[6])
                .setCharacterBidiLevel(CHARACTER_BIDI_LEVEL1)
                .setCharacterBounds(CHARACTER_BOUNDS1)
                .setGraphemeSegmentFinder(GRAPHEME_SEGMENT_FINDER1)
                .setWordSegmentFinder(WORD_SEGMENT_FINDER2)
                .setLineSegmentFinder(LINE_SEGMENT_FINDER1)
                .build();
    }

    @Test(expected = IllegalStateException.class)
    @ApiTest(apis = { "android.view.inputmethod.TextBoundsInfo.Builder#build" })
    public void testBuilder_characterBidiLevel_isRequired() {
        new TextBoundsInfo.Builder(5, 10)
                .setMatrix(Matrix.IDENTITY_MATRIX)
                .setCharacterBounds(CHARACTER_BOUNDS1)
                .setCharacterFlags(CHARACTER_FLAGS1)
                .setGraphemeSegmentFinder(GRAPHEME_SEGMENT_FINDER1)
                .setWordSegmentFinder(WORD_SEGMENT_FINDER2)
                .setLineSegmentFinder(LINE_SEGMENT_FINDER1)
                .build();
    }

    @Test(expected = NullPointerException.class)
    @ApiTest(apis = { "android.view.inputmethod.TextBoundsInfo.Builder#setCharacterBidiLevel" })
    public void testBuilder_characterBidiLevel_isNull() {
        new TextBoundsInfo.Builder(5, 10).setCharacterBidiLevel(null);
    }

    @Test(expected = IllegalStateException.class)
    @ApiTest(apis = { "android.view.inputmethod.TextBoundsInfo.Builder#build" })
    public void testBuilder_characterBidiLevel_wrongLength() {
        // Expected characterBidiLevel.length == 5 to match the given range [0, 5).
        new TextBoundsInfo.Builder(5, 10)
                .setMatrix(Matrix.IDENTITY_MATRIX)
                .setCharacterFlags(CHARACTER_FLAGS1)
                .setCharacterBidiLevel(new int[6])
                .setCharacterBounds(CHARACTER_BOUNDS1)
                .setGraphemeSegmentFinder(GRAPHEME_SEGMENT_FINDER1)
                .setWordSegmentFinder(WORD_SEGMENT_FINDER2)
                .setLineSegmentFinder(LINE_SEGMENT_FINDER1)
                .build();
    }

    @Test
    @ApiTest(apis = { "android.view.inputmethod.TextBoundsInfo.Builder#setCharacterBidiLevel" })
    public void testBuilder_characterBidiLevel_invalidBidiLevel() {
        // Bidi level must be in the range of [0, 125].
        try {
            new TextBoundsInfo.Builder(0, 1).setCharacterBidiLevel(new int[] { 126 });
            fail();
        } catch (IllegalArgumentException ignored) { }

        try {
            new TextBoundsInfo.Builder(0 , 1).setCharacterBidiLevel(new int[] { -1 });
            fail();
        } catch (IllegalArgumentException ignored) { }
    }

    @Test
    @ApiTest(apis = { "android.view.inputmethod.TextBoundsInfo.Builder#setCharacterBidiLevel" })
    public void testBuilder_characterBidiLevel_isCleared() {
        final var builder = new TextBoundsInfo.Builder(5, 10)
                .setMatrix(Matrix.IDENTITY_MATRIX)
                .setCharacterFlags(CHARACTER_FLAGS1)
                .setCharacterBidiLevel(CHARACTER_BIDI_LEVEL1)
                .setCharacterBounds(CHARACTER_BOUNDS1)
                .setGraphemeSegmentFinder(GRAPHEME_SEGMENT_FINDER1)
                .setWordSegmentFinder(WORD_SEGMENT_FINDER2)
                .setLineSegmentFinder(LINE_SEGMENT_FINDER1);
        builder.build();

        builder.clear();
        builder.setMatrix(Matrix.IDENTITY_MATRIX)
                .setStartAndEnd(5, 10)
                .setCharacterFlags(CHARACTER_FLAGS1)
                // omit setting characterBidiLevel.
                .setCharacterBounds(CHARACTER_BOUNDS1)
                .setGraphemeSegmentFinder(GRAPHEME_SEGMENT_FINDER1)
                .setWordSegmentFinder(WORD_SEGMENT_FINDER2)
                .setLineSegmentFinder(LINE_SEGMENT_FINDER1);

        assertThrows(IllegalStateException.class, builder::build);
    }

    @Test(expected = IllegalArgumentException.class)
    @ApiTest(apis = { "android.view.inputmethod.TextBoundsInfo.Builder#setCharacterFlags" })
    public void testBuilder_characterFlags_invalidFlag() {
        TextBoundsInfo.Builder builder = new TextBoundsInfo.Builder(0, 5);

        // 1 << 20 is an unknown flags
        int[] characterFlags = new int[] { 0, 1 << 20, 0, 0, 0};
        builder.setCharacterFlags(characterFlags);
    }

    @Test(expected = IllegalStateException.class)
    @ApiTest(apis = { "android.view.inputmethod.TextBoundsInfo.Builder#build" })
    public void testBuilder_graphemeSegmentFinder_isRequired() {
        new TextBoundsInfo.Builder(5, 10)
                .setMatrix(Matrix.IDENTITY_MATRIX)
                .setCharacterBounds(CHARACTER_BOUNDS1)
                .setCharacterBidiLevel(CHARACTER_BIDI_LEVEL1)
                .setCharacterFlags(CHARACTER_FLAGS1)
                .setWordSegmentFinder(WORD_SEGMENT_FINDER2)
                .setLineSegmentFinder(LINE_SEGMENT_FINDER1)
                .build();
    }

    @Test(expected = NullPointerException.class)
    @ApiTest(apis = { "android.view.inputmethod.TextBoundsInfo.Builder#setGraphemeSegmentFinder" })
    public void testBuilder_graphemeSegmentFinder_isNull() {
        new TextBoundsInfo.Builder(5, 10).setGraphemeSegmentFinder(null);
    }

    @Test(expected = IllegalStateException.class)
    @ApiTest(apis = { "android.view.inputmethod.TextBoundsInfo.Builder#build" })
    public void testBuilder_wordSegmentFinder_isRequired() {
        new TextBoundsInfo.Builder(5, 10)
                .setMatrix(Matrix.IDENTITY_MATRIX)
                .setCharacterBounds(CHARACTER_BOUNDS1)
                .setCharacterBidiLevel(CHARACTER_BIDI_LEVEL1)
                .setCharacterFlags(CHARACTER_FLAGS1)
                .setGraphemeSegmentFinder(GRAPHEME_SEGMENT_FINDER1)
                .setLineSegmentFinder(LINE_SEGMENT_FINDER1)
                .build();
    }

    @Test(expected = NullPointerException.class)
    @ApiTest(apis = { "android.view.inputmethod.TextBoundsInfo.Builder#setWordSegmentFinder" })
    public void testBuilder_wordSegmentFinder_isNull() {
        new TextBoundsInfo.Builder(5, 10).setWordSegmentFinder(null);
    }

    @Test(expected = IllegalStateException.class)
    @ApiTest(apis = { "android.view.inputmethod.TextBoundsInfo.Builder#build" })
    public void testBuilder_lineSegmentFinder_isRequired() {
        new TextBoundsInfo.Builder(5, 10)
                .setMatrix(Matrix.IDENTITY_MATRIX)
                .setCharacterBounds(CHARACTER_BOUNDS1)
                .setCharacterBidiLevel(CHARACTER_BIDI_LEVEL1)
                .setCharacterFlags(CHARACTER_FLAGS1)
                .setGraphemeSegmentFinder(GRAPHEME_SEGMENT_FINDER1)
                .setWordSegmentFinder(WORD_SEGMENT_FINDER2)
                .build();
    }

    @Test(expected = NullPointerException.class)
    @ApiTest(apis = { "android.view.inputmethod.TextBoundsInfo.Builder#setLineSegmentFinder" })
    public void testBuilder_lineSegmentFinder_isNull() {
        new TextBoundsInfo.Builder(5, 10).setLineSegmentFinder(null);
    }

    @Test
    @ApiTest(apis = { "android.view.inputmethod.TextBoundsInfoResult#getResultCode",
            "android.view.inputmethod.TextBoundsInfoResult#getTextBoundsInfo"})
    public void testTextBoundsInfoResult_constructor() {
        TextBoundsInfoResult result =
                new TextBoundsInfoResult(TextBoundsInfoResult.CODE_UNSUPPORTED);
        assertEquals(result.getResultCode(), TextBoundsInfoResult.CODE_UNSUPPORTED);

        result = new TextBoundsInfoResult(TextBoundsInfoResult.CODE_FAILED);
        assertEquals(result.getResultCode(), TextBoundsInfoResult.CODE_FAILED);

        result = new TextBoundsInfoResult(TextBoundsInfoResult.CODE_CANCELLED);
        assertEquals(result.getResultCode(), TextBoundsInfoResult.CODE_CANCELLED);

        final TextBoundsInfo textBoundsInfo = new TextBoundsInfo.Builder(5, 10)
                .setCharacterBounds(CHARACTER_BOUNDS1)
                .setCharacterBidiLevel(CHARACTER_BIDI_LEVEL1)
                .setMatrix(Matrix.IDENTITY_MATRIX)
                .setCharacterFlags(CHARACTER_FLAGS1)
                .setGraphemeSegmentFinder(GRAPHEME_SEGMENT_FINDER1)
                .setWordSegmentFinder(WORD_SEGMENT_FINDER1)
                .setLineSegmentFinder(LINE_SEGMENT_FINDER1)
                .build();

        result = new TextBoundsInfoResult(TextBoundsInfoResult.CODE_SUCCESS,
                        textBoundsInfo);
        assertEquals(result.getResultCode(), TextBoundsInfoResult.CODE_SUCCESS);
        assertEquals(result.getTextBoundsInfo(), textBoundsInfo);
    }

    @Test
    public void testTextBoundsInfoResult_resultCodeIsSuccess_textBoundsInfoIsRequired() {
        try {
            new TextBoundsInfoResult(TextBoundsInfoResult.CODE_SUCCESS);
            fail();
        } catch (IllegalStateException ignored) { }

        try {
            new TextBoundsInfoResult(TextBoundsInfoResult.CODE_SUCCESS, null);
            fail();
        } catch (IllegalStateException ignored) { }

    }

    private static void assertCharacterBounds(float[] characterBounds,
            TextBoundsInfo textBoundsInfo) {
        final int start = textBoundsInfo.getStartIndex();
        final int end = textBoundsInfo.getEndIndex();
        for (int offset = 0; offset < end - start; ++offset) {
            final RectF expectedRect = new RectF(
                    characterBounds[4 * offset],
                    characterBounds[4 * offset + 1],
                    characterBounds[4 * offset + 2],
                    characterBounds[4 * offset + 3]);

            final RectF actualRectF = new RectF();
            textBoundsInfo.getCharacterBounds(offset + start, actualRectF);
            assertEquals(expectedRect, actualRectF);
        }
    }

    private static void assertCharacterFlags(int[] characterFlags,
            TextBoundsInfo textBoundsInfo) {
        final int start = textBoundsInfo.getStartIndex();
        final int end = textBoundsInfo.getEndIndex();
        for (int offset = 0; offset < end - start; ++offset) {
            assertEquals(characterFlags[offset],
                    textBoundsInfo.getCharacterFlags(offset + start));
        }
    }

    private static void assertCharacterBidiLevel(int[] characterBidiLevels,
            TextBoundsInfo textBoundsInfo) {
        final int start = textBoundsInfo.getStartIndex();
        final int end = textBoundsInfo.getEndIndex();
        for (int offset = 0; offset < end - start; ++offset) {
            assertEquals(characterBidiLevels[offset],
                    textBoundsInfo.getCharacterBidiLevel(offset + start));
        }
    }

    /**
     * Helper method to assert that the given {@link SegmentFinder}s are the same in the given
     * range.
     */
    private static void assertSegmentFinderEqualsInRange(SegmentFinder expect,
            SegmentFinder actual, int start, int end) {
        int expectSegmentEnd = expect.nextEndBoundary(start);
        int expectSegmentStart = expect.previousStartBoundary(expectSegmentEnd);

        int actualSegmentEnd = actual.nextEndBoundary(start);
        int actualSegmentStart = actual.previousStartBoundary(actualSegmentEnd);

        while (expectSegmentStart != SegmentFinder.DONE && expectSegmentEnd <= end) {
            assertEquals(expectSegmentStart, actualSegmentStart);
            assertEquals(expectSegmentEnd, actualSegmentEnd);

            expectSegmentStart = expect.nextStartBoundary(expectSegmentStart);
            actualSegmentStart = actual.nextStartBoundary(actualSegmentStart);

            expectSegmentEnd = expect.nextEndBoundary(expectSegmentEnd);
            actualSegmentEnd = actual.nextEndBoundary(actualSegmentEnd);
        }

        // The actual SegmentFinder doesn't have more segments within the range.
        assertTrue(actualSegmentEnd > end || actualSegmentEnd == SegmentFinder.DONE);
    }
}
