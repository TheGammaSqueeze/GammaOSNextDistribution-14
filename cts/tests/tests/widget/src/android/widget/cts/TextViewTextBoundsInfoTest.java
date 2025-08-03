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

package android.widget.cts;

import static com.google.common.truth.Truth.assertThat;

import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.SegmentFinder;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.TextBoundsInfo;
import android.view.inputmethod.TextBoundsInfoResult;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class TextViewTextBoundsInfoTest {
    // The test font includes the following characters:
    // U+0020 ( ): 10em
    // U+0049 (I): 1em
    // U+0056 (V): 5em
    // U+0058 (X): 10em
    // U+05D0 (HEBREW LETTER ALEF): 1em
    // U+05D1 (HEBREW LETTER BET): 5em
    // U+10331 (U+D800 U+DF31): 10em
    private static Typeface sTypeface;
    private static Instrumentation sInstrumentation;

    private static final float TEXT_SIZE = 1;
    // The width of the text layout.
    private static final int TEXT_LAYOUT_WIDTH = 200;
    // The height of the text layout.
    private static final int TEXT_LAYOUT_HEIGHT = 100;
    // The left coordinate of the text layout in the EditText.
    private static final int TEXT_LAYOUT_LEFT = 10;
    // The top coordinate of the text layout in the EditText.
    private static final int TEXT_LAYOUT_TOP = 20;
    // The width of the EditText. It equals to TEXT_LAYOUT_WIDTH plus the left and right paddings.
    // We only add left padding in the test case.
    private static final int VIEW_WIDTH = TEXT_LAYOUT_WIDTH + TEXT_LAYOUT_LEFT;
    // The height of the EditText. It equals to TEXT_LAYOUT_HEIGHT plus the top and bottom paddings.
    // We only add top padding in the test case.
    private static final int VIEW_HEIGHT = TEXT_LAYOUT_HEIGHT + TEXT_LAYOUT_TOP;

    private static final String DEFAULT_TEXT =  ""
            // Line 0 characters [0,  8)
            + "IV \uD800\uDF31 X\n"
            // Line 1 characters [8, 10)
            + "\u05D0\u05D1";
    private static final int[] DEFAULT_GRAPHEME_BREAKS =
            { 0, 1, 1, 2, 2, 3, 3, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10 };
    private static final int[] DEFAULT_WORD_SEGMENTS =
            { 0, 2, 3, 5, 6, 7, 8, 10 };
    private static final int[] DEFAULT_LINE_SEGMENTS = { 0, 8, 8, 10 };

    private static final int[] DEFAULT_CHARACTER_FLAGS = { 0, 0,
            TextBoundsInfo.FLAG_CHARACTER_WHITESPACE, 0, 0,
            TextBoundsInfo.FLAG_CHARACTER_WHITESPACE, 0,
            // The newline is also a whitespace.
            TextBoundsInfo.FLAG_CHARACTER_WHITESPACE | TextBoundsInfo.FLAG_CHARACTER_LINEFEED,
            TextBoundsInfo.FLAG_LINE_IS_RTL, TextBoundsInfo.FLAG_LINE_IS_RTL };

    // Characters in [0, 8) are LTR whose Bidi level is 0, and the characters in [8, 10) are RTL
    // whose Bidi level is 1.
    private static final int[] DEFAULT_CHARACTER_BIDI_LEVELS = { 0, 0, 0, 0, 0, 0, 0, 0, 1, 1};

    private static final float LINE_HEIGHT = 12f;

    private static final RectF[] DEFAULT_CHARACTER_BOUNDS = {
            // The character bounds of the first line.
            new RectF(0f, 0f, 1f, LINE_HEIGHT),
            new RectF(1f, 0f, 6f, LINE_HEIGHT),
            new RectF(6f, 0f, 16f, LINE_HEIGHT),
            new RectF(16f, 0f, 26f, LINE_HEIGHT),
            // Only the first character in the grapheme cluster has width.
            new RectF(26f, 0f, 26f, LINE_HEIGHT),
            new RectF(26f, 0f, 36f, LINE_HEIGHT),
            new RectF(36f, 0f, 46f, LINE_HEIGHT),
            // line feed has no width
            new RectF(46f, 0f, 46f, LINE_HEIGHT),
            //The character bounds of the second line.
            new RectF(TEXT_LAYOUT_WIDTH - 1f, LINE_HEIGHT, TEXT_LAYOUT_WIDTH, 2 * LINE_HEIGHT),
            new RectF(TEXT_LAYOUT_WIDTH - 6f, LINE_HEIGHT, TEXT_LAYOUT_WIDTH - 1f, 2 * LINE_HEIGHT),

    };

    private EditText mEditText;
    // The mEditText's on-screen location.
    private int[] mLocationOnScreen = new int[2];
    private InputConnection mInputConnection;

    @Rule
    public ActivityTestRule<TextViewHandwritingCtsActivity> mActivityRule =
            new ActivityTestRule<>(TextViewHandwritingCtsActivity.class);

    @BeforeClass
    public static void setupClass() {
        sInstrumentation = InstrumentationRegistry.getInstrumentation();
        sTypeface = Typeface.createFromAsset(
                sInstrumentation.getTargetContext().getAssets(), "LayoutTestFont.ttf");
    }

    void setupEditText(float translationX, float translationY) throws Throwable {
        final Activity activity = mActivityRule.getActivity();

        mEditText = activity.findViewById(R.id.edittext);

        mActivityRule.runOnUiThread(() -> {
            mEditText.setTypeface(sTypeface);
            mEditText.setText(DEFAULT_TEXT);
            mEditText.setTextSize(TypedValue.COMPLEX_UNIT_PX, TEXT_SIZE);
            mEditText.setLayoutParams(new FrameLayout.LayoutParams(VIEW_WIDTH, VIEW_HEIGHT));
            // EditText will place the text layout at (paddingLeft, paddingTop) in itself.
            mEditText.setPadding(TEXT_LAYOUT_LEFT, TEXT_LAYOUT_TOP, 0, 0);
            // Set drawables to null, otherwise EditText will use drawable to compute text layout's
            // location within the view.
            mEditText.setCompoundDrawables(null, null, null, null);
            // Forcing the top of the Layout to be exactly LAYOUT_TOP.
            mEditText.setGravity(Gravity.TOP);
            mEditText.setTranslationX(translationX);
            mEditText.setTranslationY(translationY);
        });

        sInstrumentation.waitForIdleSync();

        mEditText.getLocationOnScreen(mLocationOnScreen);
        mInputConnection = mEditText.onCreateInputConnection(new EditorInfo());
    }

    @Test
    public void testRequestTextBoundsInfo_topOfLayout() throws Throwable {
        setupEditText(0f, 0f);

        final RectF rectF = new RectF(0f, -5f, 1f, 0f);
        final TextBoundsInfoResult textBoundsInfoResult = getTextBoundsInfoResult(rectF);
        final TextBoundsInfo textBoundsInfo = textBoundsInfoResult.getTextBoundsInfo();

        assertThat(textBoundsInfoResult.getResultCode())
                .isEqualTo(TextBoundsInfoResult.CODE_SUCCESS);

        final Matrix expectMatrix = new Matrix();
        expectMatrix.setTranslate(mLocationOnScreen[0], mLocationOnScreen[1]);
        assertEmptyTextBoundsInfo(textBoundsInfo, expectMatrix);
    }

    @Test
    public void testRequestTextBoundsInfo_leftOfLayout() throws Throwable {
        setupEditText(0f, 0f);

        final RectF rectF = new RectF(-2f, 0f, 0f, LINE_HEIGHT);
        final TextBoundsInfoResult textBoundsInfoResult = getTextBoundsInfoResult(rectF);
        final TextBoundsInfo textBoundsInfo = textBoundsInfoResult.getTextBoundsInfo();

        assertThat(textBoundsInfoResult.getResultCode())
                .isEqualTo(TextBoundsInfoResult.CODE_SUCCESS);

        final Matrix expectMatrix = new Matrix();
        expectMatrix.setTranslate(mLocationOnScreen[0], mLocationOnScreen[1]);
        assertEmptyTextBoundsInfo(textBoundsInfo, expectMatrix);
    }

    @Test
    public void testRequestTextBoundsInfo_rightOfLayout() throws Throwable {
        setupEditText(0f, 0f);

        final RectF rectF = new RectF(TEXT_LAYOUT_WIDTH, 0f, TEXT_LAYOUT_WIDTH + 1f, LINE_HEIGHT);
        final TextBoundsInfoResult textBoundsInfoResult = getTextBoundsInfoResult(rectF);
        final TextBoundsInfo textBoundsInfo = textBoundsInfoResult.getTextBoundsInfo();

        assertThat(textBoundsInfoResult.getResultCode())
                .isEqualTo(TextBoundsInfoResult.CODE_SUCCESS);

        final Matrix expectMatrix = new Matrix();
        expectMatrix.setTranslate(mLocationOnScreen[0], mLocationOnScreen[1]);
        assertEmptyTextBoundsInfo(textBoundsInfo, expectMatrix);
    }

    @Test
    public void testRequestTextBoundsInfo_bottomOfLayout() throws Throwable {
        setupEditText(0f, 0f);

        final RectF rectF = new RectF(0f, 2 * LINE_HEIGHT, 1f, 2 * LINE_HEIGHT + 1f);
        final TextBoundsInfoResult textBoundsInfoResult = getTextBoundsInfoResult(rectF);
        final TextBoundsInfo textBoundsInfo = textBoundsInfoResult.getTextBoundsInfo();

        assertThat(textBoundsInfoResult.getResultCode())
                .isEqualTo(TextBoundsInfoResult.CODE_SUCCESS);

        final Matrix expectMatrix = new Matrix();
        expectMatrix.setTranslate(mLocationOnScreen[0], mLocationOnScreen[1]);
        assertEmptyTextBoundsInfo(textBoundsInfo, expectMatrix);
    }

    @Test
    public void testRequestTextBoundsInfo_firstLine() throws Throwable {
        setupEditText(0f, 0f);

        final RectF rectF = new RectF(0f, 0f, TEXT_LAYOUT_WIDTH, LINE_HEIGHT - 1f);
        final TextBoundsInfoResult textBoundsInfoResult = getTextBoundsInfoResult(rectF);
        final TextBoundsInfo textBoundsInfo = textBoundsInfoResult.getTextBoundsInfo();

        assertThat(textBoundsInfoResult.getResultCode())
                .isEqualTo(TextBoundsInfoResult.CODE_SUCCESS);

        final Matrix expectMatrix = new Matrix();
        expectMatrix.setTranslate(mLocationOnScreen[0], mLocationOnScreen[1]);
        assertThat(textBoundsInfo.getStartIndex()).isEqualTo(0);
        assertThat(textBoundsInfo.getEndIndex()).isEqualTo(8);
        assertSegments(DEFAULT_GRAPHEME_BREAKS, textBoundsInfo.getGraphemeSegmentFinder(), 0, 8);
        assertSegments(DEFAULT_WORD_SEGMENTS, textBoundsInfo.getWordSegmentFinder(), 0, 8);
        assertSegments(DEFAULT_LINE_SEGMENTS, textBoundsInfo.getLineSegmentFinder(), 0, 8);


        assertCharacterBounds(DEFAULT_CHARACTER_BOUNDS, textBoundsInfo);
        assertCharacterFlags(DEFAULT_CHARACTER_FLAGS, textBoundsInfo);
        assertCharacterBidiLevels(DEFAULT_CHARACTER_BIDI_LEVELS, textBoundsInfo);
    }

    @Test
    public void testRequestTextBoundsInfo_lastLine() throws Throwable {
        setupEditText(0f, 0f);

        final RectF rectF = new RectF(0f, LINE_HEIGHT, TEXT_LAYOUT_WIDTH, 2 * LINE_HEIGHT);
        final TextBoundsInfoResult textBoundsInfoResult = getTextBoundsInfoResult(rectF);
        final TextBoundsInfo textBoundsInfo = textBoundsInfoResult.getTextBoundsInfo();

        assertThat(textBoundsInfoResult.getResultCode())
                .isEqualTo(TextBoundsInfoResult.CODE_SUCCESS);

        final Matrix expectMatrix = new Matrix();
        expectMatrix.setTranslate(mLocationOnScreen[0], mLocationOnScreen[1]);
        assertThat(textBoundsInfo.getStartIndex()).isEqualTo(8);
        assertThat(textBoundsInfo.getEndIndex()).isEqualTo(10);
        assertSegments(DEFAULT_GRAPHEME_BREAKS, textBoundsInfo.getGraphemeSegmentFinder(), 8, 10);
        assertSegments(DEFAULT_WORD_SEGMENTS, textBoundsInfo.getWordSegmentFinder(), 8, 10);
        assertSegments(DEFAULT_LINE_SEGMENTS, textBoundsInfo.getLineSegmentFinder(), 8, 10);

        assertCharacterBounds(DEFAULT_CHARACTER_BOUNDS, textBoundsInfo);
        assertCharacterFlags(DEFAULT_CHARACTER_FLAGS, textBoundsInfo);
        assertCharacterBidiLevels(DEFAULT_CHARACTER_BIDI_LEVELS, textBoundsInfo);
    }

    @Test
    public void testRequestTextBoundsInfo_multiLines() throws Throwable {
        setupEditText(0f, 0f);

        final RectF rectF = new RectF(0f, 0f, TEXT_LAYOUT_WIDTH, 2 * LINE_HEIGHT);
        final TextBoundsInfoResult textBoundsInfoResult = getTextBoundsInfoResult(rectF);
        final TextBoundsInfo textBoundsInfo = textBoundsInfoResult.getTextBoundsInfo();

        assertThat(textBoundsInfoResult.getResultCode())
                .isEqualTo(TextBoundsInfoResult.CODE_SUCCESS);

        final Matrix expectMatrix = new Matrix();
        expectMatrix.setTranslate(mLocationOnScreen[0], mLocationOnScreen[1]);
        assertThat(textBoundsInfo.getStartIndex()).isEqualTo(0);
        assertThat(textBoundsInfo.getEndIndex()).isEqualTo(10);
        assertSegments(DEFAULT_GRAPHEME_BREAKS, textBoundsInfo.getGraphemeSegmentFinder(), 0, 10);
        assertSegments(DEFAULT_WORD_SEGMENTS, textBoundsInfo.getWordSegmentFinder(), 0, 10);
        assertSegments(DEFAULT_LINE_SEGMENTS, textBoundsInfo.getLineSegmentFinder(), 0, 10);

        assertCharacterBounds(DEFAULT_CHARACTER_BOUNDS, textBoundsInfo);
        assertCharacterFlags(DEFAULT_CHARACTER_FLAGS, textBoundsInfo);
        assertCharacterBidiLevels(DEFAULT_CHARACTER_BIDI_LEVELS, textBoundsInfo);
    }

    @Test
    public void testRequestTextBoundsInfo_withTranslation() throws Throwable {
        setupEditText(15f, 25f);

        final RectF rectF = new RectF(0f, 0f, TEXT_LAYOUT_WIDTH, LINE_HEIGHT - 1f);
        final TextBoundsInfoResult textBoundsInfoResult = getTextBoundsInfoResult(rectF);
        final TextBoundsInfo textBoundsInfo = textBoundsInfoResult.getTextBoundsInfo();

        assertThat(textBoundsInfoResult.getResultCode())
                .isEqualTo(TextBoundsInfoResult.CODE_SUCCESS);

        final Matrix expectMatrix = new Matrix();
        expectMatrix.setTranslate(mLocationOnScreen[0], mLocationOnScreen[1]);
        final Matrix actualMatrix = new Matrix();
        textBoundsInfo.getMatrix(actualMatrix);
        assertThat(actualMatrix).isEqualTo(expectMatrix);
        assertThat(textBoundsInfo.getStartIndex()).isEqualTo(0);
        assertThat(textBoundsInfo.getEndIndex()).isEqualTo(8);
        assertSegments(DEFAULT_GRAPHEME_BREAKS, textBoundsInfo.getGraphemeSegmentFinder(), 0, 8);
        assertSegments(DEFAULT_WORD_SEGMENTS, textBoundsInfo.getWordSegmentFinder(), 0, 8);
        assertSegments(DEFAULT_LINE_SEGMENTS, textBoundsInfo.getLineSegmentFinder(), 0, 8);

        assertCharacterBounds(DEFAULT_CHARACTER_BOUNDS, textBoundsInfo);
        assertCharacterFlags(DEFAULT_CHARACTER_FLAGS, textBoundsInfo);
        assertCharacterBidiLevels(DEFAULT_CHARACTER_BIDI_LEVELS, textBoundsInfo);
    }

    private static void assertCharacterBounds(RectF[] expectBounds, TextBoundsInfo textBoundsInfo) {
        final int start = textBoundsInfo.getStartIndex();
        final int end = textBoundsInfo.getEndIndex();

        for (int index = start; index < end; ++index) {
            final RectF actualBounds = new RectF();
            textBoundsInfo.getCharacterBounds(index, actualBounds);
            final RectF expectedBounds = new RectF(expectBounds[index]);
            // The returned text bounds is in the view's coordinates.
            expectedBounds.offset(TEXT_LAYOUT_LEFT, TEXT_LAYOUT_TOP);

            assertThat(actualBounds).isEqualTo(expectedBounds);
        }
    }

    private static void assertCharacterFlags(int[] expectFlags, TextBoundsInfo textBoundsInfo) {
        final int start = textBoundsInfo.getStartIndex();
        final int end = textBoundsInfo.getEndIndex();

        for (int index = start; index < end; ++index) {
            assertThat(textBoundsInfo.getCharacterFlags(index)).isEqualTo(expectFlags[index]);
        }
    }

    private static void assertCharacterBidiLevels(int[] expectBidiLevels,
            TextBoundsInfo textBoundsInfo) {
        final int start = textBoundsInfo.getStartIndex();
        final int end = textBoundsInfo.getEndIndex();

        for (int index = start; index < end; ++index) {
            assertThat(textBoundsInfo.getCharacterBidiLevel(index))
                    .isEqualTo(expectBidiLevels[index]);
        }
    }

    private static void assertSegments(int[] segments, SegmentFinder segmentFinder,
            int start, int end) {
        if (end <= start) return;
        int index = 0;
        while (index < segments.length && segments[index] < start) {
            index += 2;
        }

        int segmentEnd = segmentFinder.nextEndBoundary(start);
        if (segmentEnd == SegmentFinder.DONE) {
            // Assert that no segment is expected in the specified range.
            assertThat(index >= segments.length).isTrue();
            return;
        }
        int segmentStart = segmentFinder.previousStartBoundary(segmentEnd);
        while (segmentEnd != SegmentFinder.DONE && segmentEnd <= end && index < segments.length) {
            // Only assert segments that are completely in the range.
            if (segmentStart >= start) {
                assertThat(segmentStart).isEqualTo(segments[index++]);
                assertThat(segmentEnd).isEqualTo(segments[index++]);
            }
            segmentStart = segmentFinder.nextStartBoundary(segmentStart);
            segmentEnd = segmentFinder.nextEndBoundary(segmentEnd);
        }

        if (segmentEnd == SegmentFinder.DONE) {
            // Make sure there aren't more segments expected.
            if (index < segments.length) {
                assertThat(segments[index + 1]).isGreaterThan(end);
            }
        } else {
            // Make sure segmentFinder doesn't have extra segments in the range.
            assertThat(segmentEnd).isGreaterThan(end);
        }
    }

    private static void assertEmptyTextBoundsInfo(TextBoundsInfo textBoundsInfo, Matrix matrix) {
        final Matrix actualMatrix = new Matrix();
        textBoundsInfo.getMatrix(actualMatrix);
        assertThat(actualMatrix).isEqualTo(matrix);
        assertThat(textBoundsInfo.getStartIndex()).isEqualTo(0);
        assertThat(textBoundsInfo.getEndIndex()).isEqualTo(0);
        assertSegmentFinderIsEmpty(textBoundsInfo.getGraphemeSegmentFinder());
        assertSegmentFinderIsEmpty(textBoundsInfo.getWordSegmentFinder());
        assertSegmentFinderIsEmpty(textBoundsInfo.getLineSegmentFinder());
    }

    private static void assertSegmentFinderIsEmpty(SegmentFinder segmentFinder) {
        assertThat(segmentFinder.nextEndBoundary(0)).isEqualTo(SegmentFinder.DONE);
        assertThat(segmentFinder.previousStartBoundary(Integer.MAX_VALUE))
                .isEqualTo(SegmentFinder.DONE);
    }

    /**
     * Query the {@link TextBoundsInfoResult} from the test {@link EditText}. The given
     * {@link RectF} is in text {@link android.text.Layout}'s coordinates. This method will
     * translate the given rectangle to the global rectangle and then pass to the
     * {@link InputConnection}.
     *
     * @param localRectF the area where {@link TextBoundsInfo} is requested, in the
     * {@link android.text.Layout} coordinates.
     *
     * @return the {@link TextBoundsInfoResult} returned by the test {@link EditText}
     */
    private TextBoundsInfoResult getTextBoundsInfoResult(RectF localRectF) {
        RectF globalRectF = new RectF(localRectF);
        globalRectF.offset(mLocationOnScreen[0] + mEditText.getTotalPaddingLeft(),
                mLocationOnScreen[1] + mEditText.getTotalPaddingTop());
        AtomicReference<TextBoundsInfoResult> result = new AtomicReference<>();
        mInputConnection.requestTextBoundsInfo(globalRectF, Runnable::run, result::set);
        return result.get();
    }
}
