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

import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.CancellationSignal;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Layout;
import android.text.TextWatcher;
import android.text.method.DigitsKeyListener;
import android.text.method.PasswordTransformationMethod;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.inputmethod.DeleteGesture;
import android.view.inputmethod.DeleteRangeGesture;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.HandwritingGesture;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InsertGesture;
import android.view.inputmethod.InsertModeGesture;
import android.view.inputmethod.JoinOrSplitGesture;
import android.view.inputmethod.PreviewableHandwritingGesture;
import android.view.inputmethod.RemoveSpaceGesture;
import android.view.inputmethod.SelectGesture;
import android.view.inputmethod.SelectRangeGesture;
import android.widget.EditText;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.compatibility.common.util.ApiTest;
import com.android.internal.graphics.ColorUtils;
import com.android.text.flags.Flags;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.IntConsumer;

@RunWith(AndroidJUnit4.class)
public class TextViewHandwritingGestureTest {
    private static final int WIDTH = 200;
    private static final int HEIGHT = 1000;
    private static final String DEFAULT_TEXT = ""
            // Line 0 (offset 0 to 10)
            + "XXX X XXX\n"
            // Line 1 (offset 10 to 27)
            + "XX X   XX  X. .X ";
    // All characters used in DEFAULT_TEXT ('X', ' ', '.') have width 10em in the test font used.
    // Font size is set to 1f, so that 10em is 10px.
    private static final float CHAR_WIDTH_PX = 10;
    private static final String INSERT_TEXT = "insert";
    private static final String FALLBACK_TEXT = "789";

    // The placeholder text used in insert mode.
    private static final String PLACEHOLDER_TEXT_MULTI_LINE = "\n\n";
    private static final String PLACEHOLDER_TEXT_SINGLE_LINE = "\uFFFD";
    private static final String DOT = "\u2022";

    private int mGestureLineMargin;
    private EditText mEditText;
    private int[] mLocationOnScreen;

    private int mResult = InputConnection.HANDWRITING_GESTURE_RESULT_UNKNOWN;
    private final IntConsumer mResultConsumer = value -> mResult = value;

    @Rule
    public ActivityTestRule<TextViewCtsActivity> mActivityRule =
            new ActivityTestRule<>(TextViewCtsActivity.class);

    @Rule
    public final CheckFlagsRule mCheckFlagRule = DeviceFlagsValueProvider.createCheckFlagsRule();


    @Before
    public void setup() {
        // The test font includes the following characters:
        // U+0020 ( ): 10em
        // U+002E (.): 10em
        // U+0058 (X): 10em
        Typeface typeface = Typeface.createFromAsset(
                InstrumentationRegistry.getInstrumentation().getTargetContext().getAssets(),
                "LayoutTestFont.ttf");

        mGestureLineMargin =
                ViewConfiguration.get(mActivityRule.getActivity())
                        .getScaledHandwritingGestureLineMargin();

        mEditText = new EditText(mActivityRule.getActivity());
        mEditText.setTypeface(typeface);
        // Make 1em equal to 1px.
        // Then all characters used in DEFAULT_TEXT ('X' and ' ') will have width 10px.
        mEditText.setTextSize(TypedValue.COMPLEX_UNIT_PX, 1.0f);
        // Point-based gestures are supported within mGestureLineMargin of a line's bounds.
        // Set the line spacing to be larger than 2 * mGestureLineMargin so that there is space
        // between the lines where gestures are not supported.
        mEditText.setLineSpacing(/* add= */ 2 * mGestureLineMargin + 20f, /* mult= */ 1f);
        mEditText.setText(DEFAULT_TEXT);

        mEditText.measure(
                View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        mEditText.layout(0, 0, WIDTH, HEIGHT);
        mEditText.setLayoutParams(new ViewGroup.LayoutParams(WIDTH, HEIGHT));

        mLocationOnScreen = mEditText.getLocationOnScreen();
        mLocationOnScreen[0] += mEditText.getTotalPaddingLeft();
        mLocationOnScreen[1] += mEditText.getTotalPaddingTop();
    }

    @Test
    @ApiTest(apis = "android.widget.TextView#onCreateInputConnection")
    public void onCreateInputConnection_reportsSupportedGestures() {
        EditorInfo editorInfo = new EditorInfo();
        mEditText.onCreateInputConnection(editorInfo);

        List<Class<? extends HandwritingGesture>> gestures =
                editorInfo.getSupportedHandwritingGestures();
        assertThat(gestures).containsAtLeast(
                SelectGesture.class,
                SelectRangeGesture.class,
                DeleteGesture.class,
                DeleteRangeGesture.class,
                InsertGesture.class,
                RemoveSpaceGesture.class,
                JoinOrSplitGesture.class,
                InsertModeGesture.class);
    }

    @Test
    @ApiTest(apis = "android.widget.TextView#onCreateInputConnection")
    public void onCreateInputConnection_reportsSupportedGesturePreviews() {
        EditorInfo editorInfo = new EditorInfo();
        mEditText.onCreateInputConnection(editorInfo);

        Set<Class<? extends PreviewableHandwritingGesture>> gestures =
                editorInfo.getSupportedHandwritingGesturePreviews();
        assertThat(gestures).containsAtLeast(
                SelectGesture.class,
                SelectRangeGesture.class,
                DeleteGesture.class,
                DeleteRangeGesture.class);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performSelectGesture_character() {
        float char1HorizontalCenter = 1.5f * CHAR_WIDTH_PX;
        float char2HorizontalCenter = 2.5f * CHAR_WIDTH_PX;
        // Horizontal range [char1HorizontalCenter - 1f, char2HorizontalCenter + 1f] covers the
        // centers of characters 1 and 2.
        RectF area = new RectF(
                char1HorizontalCenter - 1f,
                mEditText.getLayout().getLineTop(0),
                char2HorizontalCenter + 1f,
                mEditText.getLayout().getLineBottom(0));
        performSelectGesture(area, HandwritingGesture.GRANULARITY_CHARACTER);

        assertGestureSelectedRange(1, 3);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#previewHandwritingGesture")
    public void previewSelectGesture_character() {
        float char0HorizontalCenter = 0.5f * CHAR_WIDTH_PX;
        float char2HorizontalCenter = 2.5f * CHAR_WIDTH_PX;
        // Horizontal range [char0HorizontalCenter - 1f, char2HorizontalCenter + 1f] covers the
        // centers of characters 0, 1 and 2.
        RectF area = new RectF(
                char0HorizontalCenter - 1f,
                mEditText.getLayout().getLineTop(0),
                char2HorizontalCenter + 1f,
                mEditText.getLayout().getLineBottom(0));
        previewSelectGesture(area, HandwritingGesture.GRANULARITY_CHARACTER);

        assertSelectGesturePreviewHighlightRange(0, 3);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performSelectGesture_word() {
        // Word 1 spans from offset 4 to offset 5
        float word1HorizontalCenter = (4 + 5) / 2f * CHAR_WIDTH_PX;
        // Word 2 spans from offset 6 to offset 9
        float word2HorizontalCenter = (6 + 9) / 2f * CHAR_WIDTH_PX;
        // Horizontal range [word1HorizontalCenter - 1f, word2HorizontalCenter + 1f] covers the
        // centers of words 1 and 2.
        RectF area = new RectF(
                word1HorizontalCenter - 1f,
                mEditText.getLayout().getLineTop(0),
                word2HorizontalCenter + 1f,
                mEditText.getLayout().getLineBottom(0));
        performSelectGesture(area, HandwritingGesture.GRANULARITY_WORD);

        assertGestureSelectedRange(4, 9);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#previewHandwritingGesture")
    public void previewSelectGesture_word() {
        // Word 1 spans from offset 4 to offset 5
        float word1HorizontalCenter = (4 + 5) / 2f * CHAR_WIDTH_PX;
        // Word 2 spans from offset 6 to offset 9
        float word2HorizontalCenter = (6 + 9) / 2f * CHAR_WIDTH_PX;
        // Horizontal range [word1HorizontalCenter - 1f, word2HorizontalCenter + 1f] covers the
        // centers of words 1 and 2.
        RectF area = new RectF(
                word1HorizontalCenter - 1f,
                mEditText.getLayout().getLineTop(0),
                word2HorizontalCenter + 1f,
                mEditText.getLayout().getLineBottom(0));
        previewSelectGesture(area, HandwritingGesture.GRANULARITY_WORD);

        assertSelectGesturePreviewHighlightRange(4, 9);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performSelectGesture_betweenWords_shouldFallback() {
        mEditText.setSelection(2);

        // Word 1 spans from offset 4 to offset 5
        float word1HorizontalCenter = (4 + 5) / 2f * CHAR_WIDTH_PX;
        // Word 2 spans from offset 6 to offset 9
        float word2HorizontalCenter = (6 + 9) / 2f * CHAR_WIDTH_PX;
        // Horizontal range [word1HorizontalCenter + 1f, word2HorizontalCenter - 1f] does not cover
        // the centers of any words since it is between the centers of these consecutive words.
        RectF area = new RectF(
                word1HorizontalCenter + 1f,
                mEditText.getLayout().getLineTop(0),
                word2HorizontalCenter - 1f,
                mEditText.getLayout().getLineBottom(0));
        performSelectGesture(area, HandwritingGesture.GRANULARITY_WORD);

        assertFallbackTextInserted(/* initialCursorPosition= */ 2);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performSelectGesture_noFallback_shouldFail() {
        mEditText.setSelection(2);

        // Word 1 spans from offset 4 to offset 5
        float word1HorizontalCenter = (4 + 5) / 2f * CHAR_WIDTH_PX;
        // Word 2 spans from offset 6 to offset 9
        float word2HorizontalCenter = (6 + 9) / 2f * CHAR_WIDTH_PX;
        // Horizontal range [word1HorizontalCenter + 1f, word2HorizontalCenter - 1f] does not cover
        // the centers of any words since it is between the centers of these consecutive words.
        RectF area = new RectF(
                word1HorizontalCenter + 1f,
                mEditText.getLayout().getLineTop(0),
                word2HorizontalCenter - 1f,
                mEditText.getLayout().getLineBottom(0));
        performSelectGesture(
                area, HandwritingGesture.GRANULARITY_WORD, /* setFallbackText= */ false);

        assertGestureFailure(/* initialCursorPosition= */ 2);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#previewHandwritingGesture")
    public void previewSelectGesture_betweenWords_shouldFail() {
        mEditText.setSelection(2);

        // Word 1 spans from offset 4 to offset 5
        float word1HorizontalCenter = (4 + 5) / 2f * CHAR_WIDTH_PX;
        // Word 2 spans from offset 6 to offset 9
        float word2HorizontalCenter = (6 + 9) / 2f * CHAR_WIDTH_PX;
        // Horizontal range [word1HorizontalCenter + 1f, word2HorizontalCenter - 1f] does not cover
        // the centers of any words since it is between the centers of these consecutive words.
        RectF area = new RectF(
                word1HorizontalCenter + 1f,
                mEditText.getLayout().getLineTop(0),
                word2HorizontalCenter - 1f,
                mEditText.getLayout().getLineBottom(0));
        previewSelectGesture(area, HandwritingGesture.GRANULARITY_WORD);

        // Fallback text is not used for preview.
        assertNoChange(/* initialCursorPosition= */ 2);
        assertNoHighlight();
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performSelectRangeGesture_character() {
        float char5HorizontalCenter = 5.5f * CHAR_WIDTH_PX;
        float char7HorizontalCenter = 7.5f * CHAR_WIDTH_PX;
        // Horizontal range [char5HorizontalCenter - 1f, char7HorizontalCenter + 1f] covers the
        // centers of characters 5, 6, 7.
        RectF startArea = new RectF(
                char5HorizontalCenter - 1f,
                mEditText.getLayout().getLineTop(0),
                char7HorizontalCenter + 1f,
                mEditText.getLayout().getLineBottom(0));
        // Line 1 starts from offset 10.
        float char11HorizontalCenter = 1.5f * CHAR_WIDTH_PX;
        float char12HorizontalCenter = 2.5f * CHAR_WIDTH_PX;
        // Horizontal range [char11HorizontalCenter - 1f, char12HorizontalCenter + 1f] covers the
        // centers of characters 11 and 12.
        RectF endArea = new RectF(
                char11HorizontalCenter - 1f,
                mEditText.getLayout().getLineTop(1),
                char12HorizontalCenter + 1f,
                mEditText.getLayout().getLineBottom(1));
        performSelectRangeGesture(startArea, endArea, HandwritingGesture.GRANULARITY_CHARACTER);

        assertGestureSelectedRange(5, 13);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#previewHandwritingGesture")
    public void previewSelectRangeGesture_character() {
        float char5HorizontalCenter = 5.5f * CHAR_WIDTH_PX;
        float char7HorizontalCenter = 7.5f * CHAR_WIDTH_PX;
        // Horizontal range [char5HorizontalCenter - 1f, char7HorizontalCenter + 1f] covers the
        // centers of characters 5, 6, 7.
        RectF startArea = new RectF(
                char5HorizontalCenter - 1f,
                mEditText.getLayout().getLineTop(0),
                char7HorizontalCenter + 1f,
                mEditText.getLayout().getLineBottom(0));
        // Line 1 starts from offset 10.
        float char11HorizontalCenter = 1.5f * CHAR_WIDTH_PX;
        float char12HorizontalCenter = 2.5f * CHAR_WIDTH_PX;
        // Horizontal range [char11HorizontalCenter - 1f, char12HorizontalCenter + 1f] covers the
        // centers of characters 11 and 12.
        RectF endArea = new RectF(
                char11HorizontalCenter - 1f,
                mEditText.getLayout().getLineTop(1),
                char12HorizontalCenter + 1f,
                mEditText.getLayout().getLineBottom(1));
        previewSelectRangeGesture(startArea, endArea, HandwritingGesture.GRANULARITY_CHARACTER);

        assertSelectGesturePreviewHighlightRange(5, 13);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performSelectRangeGesture_word() {
        // Word 2 on line 0 spans from offset 6 to offset 9
        float word2HorizontalCenter = (6 + 9) / 2f * CHAR_WIDTH_PX;
        // Horizontal range [word2HorizontalCenter - 1f, word2HorizontalCenter + 1f] covers the
        // centers of word  2.
        RectF startArea = new RectF(
                word2HorizontalCenter - 1f,
                mEditText.getLayout().getLineTop(0),
                word2HorizontalCenter + 1f,
                mEditText.getLayout().getLineBottom(0));
        // Line 1 starts from offset 10.
        // Word 3 on line 1 spans from offset 10 to offset 12
        float word3HorizontalCenter = (0 + 2) / 2f * CHAR_WIDTH_PX;
        // Horizontal range [word3HorizontalCenter - 1f, word3HorizontalCenter + 1f] covers the
        // centers of word 2.
        RectF endArea = new RectF(
                word3HorizontalCenter - 1f,
                mEditText.getLayout().getLineTop(1),
                word3HorizontalCenter + 1f,
                mEditText.getLayout().getLineBottom(1));
        performSelectRangeGesture(startArea, endArea, HandwritingGesture.GRANULARITY_WORD);

        assertGestureSelectedRange(6, 12);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#previewHandwritingGesture")
    public void previewSelectRangeGesture_word() {
        // Word 2 on line 0 spans from offset 6 to offset 9
        float word2HorizontalCenter = (6 + 9) / 2f * CHAR_WIDTH_PX;
        // Horizontal range [word2HorizontalCenter - 1f, word2HorizontalCenter + 1f] covers the
        // centers of word  2.
        RectF startArea = new RectF(
                word2HorizontalCenter - 1f,
                mEditText.getLayout().getLineTop(0),
                word2HorizontalCenter + 1f,
                mEditText.getLayout().getLineBottom(0));
        // Line 1 starts from offset 10.
        // Word 3 on line 1 spans from offset 10 to offset 12
        float word3HorizontalCenter = (0 + 2) / 2f * CHAR_WIDTH_PX;
        // Horizontal range [word3HorizontalCenter - 1f, word3HorizontalCenter + 1f] covers the
        // centers of word 2.
        RectF endArea = new RectF(
                word3HorizontalCenter - 1f,
                mEditText.getLayout().getLineTop(1),
                word3HorizontalCenter + 1f,
                mEditText.getLayout().getLineBottom(1));
        previewSelectRangeGesture(startArea, endArea, HandwritingGesture.GRANULARITY_WORD);

        assertSelectGesturePreviewHighlightRange(6, 12);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performDeleteGesture_character() {
        float char1HorizontalCenter = 1.5f * CHAR_WIDTH_PX;
        float char2HorizontalCenter = 2.5f * CHAR_WIDTH_PX;
        // Horizontal range [char1HorizontalCenter - 1f, char2HorizontalCenter + 1f] covers
        // characters 1 and 2.
        RectF area = new RectF(
                char1HorizontalCenter - 1f,
                mEditText.getLayout().getLineTop(0),
                char2HorizontalCenter + 1f,
                mEditText.getLayout().getLineBottom(0));
        performDeleteGesture(area, HandwritingGesture.GRANULARITY_CHARACTER);

        assertGestureDeletedRange(1, 3);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#previewHandwritingGesture")
    public void previewDeleteGesture_character() {
        float char1HorizontalCenter = 1.5f * CHAR_WIDTH_PX;
        float char2HorizontalCenter = 2.5f * CHAR_WIDTH_PX;
        // Horizontal range [char1HorizontalCenter - 1f, char2HorizontalCenter + 1f] covers
        // characters 1 and 2.
        RectF area = new RectF(
                char1HorizontalCenter - 1f,
                mEditText.getLayout().getLineTop(0),
                char2HorizontalCenter + 1f,
                mEditText.getLayout().getLineBottom(0));
        previewDeleteGesture(area, HandwritingGesture.GRANULARITY_CHARACTER);

        assertDeleteGesturePreviewHighlightRange(1, 3);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performDeleteGesture_word() {
        // Word 1 spans from offset 4 to offset 5
        float word1HorizontalCenter = (4 + 5) / 2f * CHAR_WIDTH_PX;
        // Horizontal range [word1HorizontalCenter - 1f, word1HorizontalCenter + 1f] covers the
        // center of word 1.
        RectF area = new RectF(
                word1HorizontalCenter - 1f,
                mEditText.getLayout().getLineTop(0),
                word1HorizontalCenter + 1f,
                mEditText.getLayout().getLineBottom(0));
        performDeleteGesture(area, HandwritingGesture.GRANULARITY_WORD);

        // Word 1 (offset 4 to 5) is deleted.
        // Since there is whitespace at offset 5, the space before word 1 (offset 3 to 4) is also
        // deleted to avoid a double space.
        // Deleted range: "XXX [X] XXX\n" -> "XXX[ X] XXX\n"
        assertGestureDeletedRange(3, 5);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#previewHandwritingGesture")
    public void previewDeleteGesture_word() {
        // Word 1 spans from offset 4 to offset 5
        float word1HorizontalCenter = (4 + 5) / 2f * CHAR_WIDTH_PX;
        // Horizontal range [word1HorizontalCenter - 1f, word1HorizontalCenter + 1f] covers the
        // center of word 1.
        RectF area = new RectF(
                word1HorizontalCenter - 1f,
                mEditText.getLayout().getLineTop(0),
                word1HorizontalCenter + 1f,
                mEditText.getLayout().getLineBottom(0));
        previewDeleteGesture(area, HandwritingGesture.GRANULARITY_WORD);

        assertDeleteGesturePreviewHighlightRange(4, 5);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performDeleteGesture_word_startOfText() {
        // Word 0 spans from offset 0 to offset 3
        float word0HorizontalCenter = (0 + 3) / 2f * CHAR_WIDTH_PX;
        // Horizontal range [word0HorizontalCenter - 1f, word0HorizontalCenter + 1f] covers the
        // center of word 0.
        RectF area = new RectF(
                word0HorizontalCenter - 1f,
                mEditText.getLayout().getLineTop(0),
                word0HorizontalCenter + 1f,
                mEditText.getLayout().getLineBottom(0));
        performDeleteGesture(area, HandwritingGesture.GRANULARITY_WORD);

        // Word 0 (offset 0 to 3) is deleted.
        // The space after word 0 (offset 3 to 4) is also deleted to avoid a space at the start of
        // the line.
        // Deleted range: "[XXX] X XXX\n" -> "[XXX ]X XXX\n"
        assertGestureDeletedRange(0, 4);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performDeleteGesture_word_beforeNewLine() {
        // Word 1 spans from offset 4 to offset 5
        float word1HorizontalCenter = (4 + 5) / 2f * CHAR_WIDTH_PX;
        // Word 2 spans from offset 6 to offset 9
        float word2HorizontalCenter = (6 + 9) / 2f * CHAR_WIDTH_PX;
        // Horizontal range [word1HorizontalCenter - 1f, word2HorizontalCenter + 1f] covers the
        // centers of words 1 and 2.
        RectF area = new RectF(
                word1HorizontalCenter - 1f,
                mEditText.getLayout().getLineTop(0),
                word2HorizontalCenter + 1f,
                mEditText.getLayout().getLineBottom(0));
        performDeleteGesture(area, HandwritingGesture.GRANULARITY_WORD);

        // Word 1 (offset 4 to 5) and word 2 (offset 6 to 9) are deleted
        // Since there is a newline character at offset 9, the space before word 1 (offset 3 to 4)
        // is also deleted to avoid a space at the end of the line.
        // Deleted range: "XXX [X XXX]\n" -> "XXX[ X XXX]\n"
        assertGestureDeletedRange(3, 9);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performDeleteGesture_word_beforePunctuation() {
        // Line 1 "XX X   XX  X. .X " starts from offset 10
        // Word 6 spans from offset 21 to 22 (offset 11 to 12 relative to line 1)
        float word6HorizontalCenter = (11 + 12) / 2f * CHAR_WIDTH_PX;
        // Horizontal range [word6HorizontalCenter - 1f, word6HorizontalCenter + 1f] covers the
        // center of word 6.
        RectF area = new RectF(
                word6HorizontalCenter - 1f,
                mEditText.getLayout().getLineTop(1),
                word6HorizontalCenter + 1f,
                mEditText.getLayout().getLineBottom(1));
        performDeleteGesture(area, HandwritingGesture.GRANULARITY_WORD);

        // Word 6 (offset 21 to 22) is deleted.
        // Since there is punctuation ('.') at offset 22, the space before word 6 (offset 19 to 21)
        // is also deleted to avoid a space before the punctuation.
        // Deleted range: "XX X   XX  [X]. .X " -> "XX X   XX[  X]. .X "
        assertGestureDeletedRange(19, 22);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performDeleteGesture_word_afterPunctuation() {
        // Line 1 "XX X   XX  X. .X " starts from offset 10
        // Word 9 spans from offset 25 to 26 (offset 15 to 16 relative to line 1)
        float word9HorizontalCenter = (15 + 16) / 2f * CHAR_WIDTH_PX;
        // Horizontal range [word9HorizontalCenter - 1f, word9HorizontalCenter + 1f] covers the
        // center of word 9.
        RectF area = new RectF(
                word9HorizontalCenter - 1f,
                mEditText.getLayout().getLineTop(1),
                word9HorizontalCenter + 1f,
                mEditText.getLayout().getLineBottom(1));
        performDeleteGesture(area, HandwritingGesture.GRANULARITY_WORD);

        // Word 9 (offset 25 to 26) is deleted.
        // Since there is punctuation ('.') at offset 24, the space after word 9 (offset 26 to 27)
        // is also deleted to avoid a space after the punctuation.
        // Deleted range: "XX X   XX  X. .[X] " -> "XX X   XX  X. .[X ]"
        assertGestureDeletedRange(25, 27);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performDeleteGesture_betweenWords_shouldFallback() {
        mEditText.setSelection(2);

        // Word 1 spans from offset 4 to offset 5
        float word1HorizontalCenter = (4 + 5) / 2f * CHAR_WIDTH_PX;
        // Word 2 spans from offset 6 to offset 9
        float word2HorizontalCenter = (6 + 9) / 2f * CHAR_WIDTH_PX;
        // Horizontal range [word1HorizontalCenter + 1f, word2HorizontalCenter - 1f] does not cover
        // the centers of any words since it is between the centers of these consecutive words.
        RectF area = new RectF(
                word1HorizontalCenter + 1f,
                mEditText.getLayout().getLineTop(0),
                word2HorizontalCenter - 1f,
                mEditText.getLayout().getLineBottom(0));
        performDeleteGesture(area, HandwritingGesture.GRANULARITY_WORD);

        assertFallbackTextInserted(/* initialCursorPosition= */ 2);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performDeleteGesture_noFallback_shouldFail() {
        mEditText.setSelection(2);

        // Word 1 spans from offset 4 to offset 5
        float word1HorizontalCenter = (4 + 5) / 2f * CHAR_WIDTH_PX;
        // Word 2 spans from offset 6 to offset 9
        float word2HorizontalCenter = (6 + 9) / 2f * CHAR_WIDTH_PX;
        // Horizontal range [word1HorizontalCenter + 1f, word2HorizontalCenter - 1f] does not cover
        // the centers of any words since it is between the centers of these consecutive words.
        RectF area = new RectF(
                word1HorizontalCenter + 1f,
                mEditText.getLayout().getLineTop(0),
                word2HorizontalCenter - 1f,
                mEditText.getLayout().getLineBottom(0));
        performDeleteGesture(
                area, HandwritingGesture.GRANULARITY_WORD, /* setFallbackText= */ false);

        assertGestureFailure(/* initialCursorPosition= */ 2);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#previewHandwritingGesture")
    public void previewDeleteGesture_betweenWords_shouldFail() {
        mEditText.setSelection(2);

        // Word 1 spans from offset 4 to offset 5
        float word1HorizontalCenter = (4 + 5) / 2f * CHAR_WIDTH_PX;
        // Word 2 spans from offset 6 to offset 9
        float word2HorizontalCenter = (6 + 9) / 2f * CHAR_WIDTH_PX;
        // Horizontal range [word1HorizontalCenter + 1f, word2HorizontalCenter - 1f] does not cover
        // the centers of any words since it is between the centers of these consecutive words.
        RectF area = new RectF(
                word1HorizontalCenter + 1f,
                mEditText.getLayout().getLineTop(0),
                word2HorizontalCenter - 1f,
                mEditText.getLayout().getLineBottom(0));
        previewDeleteGesture(area, HandwritingGesture.GRANULARITY_WORD);

        // Fallback text is not used for preview.
        assertNoChange(/* initialCursorPosition= */ 2);
        assertNoHighlight();
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performDeleteRangeGesture_character() {
        float char5HorizontalCenter = 5.5f * CHAR_WIDTH_PX;
        float char7HorizontalCenter = 7.5f * CHAR_WIDTH_PX;
        // Horizontal range [char5HorizontalCenter - 1f, char7HorizontalCenter + 1f] covers the
        // centers of characters 5, 6, 7.
        RectF startArea = new RectF(
                char5HorizontalCenter - 1f,
                mEditText.getLayout().getLineTop(0),
                char7HorizontalCenter + 1f,
                mEditText.getLayout().getLineBottom(0));
        // Line 1 starts from offset 10.
        float char11HorizontalCenter = 1.5f * CHAR_WIDTH_PX;
        float char12HorizontalCenter = 2.5f * CHAR_WIDTH_PX;
        // Horizontal range [char11HorizontalCenter - 1f, char12HorizontalCenter + 1f] covers the
        // centers of characters 11 and 12.
        RectF endArea = new RectF(
                char11HorizontalCenter - 1f,
                mEditText.getLayout().getLineTop(1),
                char12HorizontalCenter + 1f,
                mEditText.getLayout().getLineBottom(1));
        performDeleteRangeGesture(startArea, endArea, HandwritingGesture.GRANULARITY_CHARACTER);

        assertGestureDeletedRange(5, 13);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#previewHandwritingGesture")
    public void previewDeleteRangeGesture_character() {
        float char5HorizontalCenter = 5.5f * CHAR_WIDTH_PX;
        float char7HorizontalCenter = 7.5f * CHAR_WIDTH_PX;
        // Horizontal range [char5HorizontalCenter - 1f, char7HorizontalCenter + 1f] covers the
        // centers of characters 5, 6, 7.
        RectF startArea = new RectF(
                char5HorizontalCenter - 1f,
                mEditText.getLayout().getLineTop(0),
                char7HorizontalCenter + 1f,
                mEditText.getLayout().getLineBottom(0));
        // Line 1 starts from offset 10.
        float char11HorizontalCenter = 1.5f * CHAR_WIDTH_PX;
        float char12HorizontalCenter = 2.5f * CHAR_WIDTH_PX;
        // Horizontal range [char11HorizontalCenter - 1f, char12HorizontalCenter + 1f] covers the
        // centers of characters 11 and 12.
        RectF endArea = new RectF(
                char11HorizontalCenter - 1f,
                mEditText.getLayout().getLineTop(1),
                char12HorizontalCenter + 1f,
                mEditText.getLayout().getLineBottom(1));
        previewDeleteRangeGesture(startArea, endArea, HandwritingGesture.GRANULARITY_CHARACTER);

        assertDeleteGesturePreviewHighlightRange(5, 13);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performDeleteRangeGesture_word() {
        // Word 2 on line 0 spans from offset 6 to offset 9
        float word2HorizontalCenter = (6 + 9) / 2f * CHAR_WIDTH_PX;
        // Horizontal range [word2HorizontalCenter - 1f, word2HorizontalCenter + 1f] covers the
        // centers of word  2.
        RectF startArea = new RectF(
                word2HorizontalCenter - 1f,
                mEditText.getLayout().getLineTop(0),
                word2HorizontalCenter + 1f,
                mEditText.getLayout().getLineBottom(0));
        // Line 1 starts from offset 10.
        // Word 3 on line 1 spans from offset 10 to offset 12
        float word3HorizontalCenter = (0 + 2) / 2f * CHAR_WIDTH_PX;
        // Horizontal range [word3HorizontalCenter - 1f, word3HorizontalCenter + 1f] covers the
        // centers of word  2.
        RectF endArea = new RectF(
                word3HorizontalCenter - 1f,
                mEditText.getLayout().getLineTop(1),
                word3HorizontalCenter + 1f,
                mEditText.getLayout().getLineBottom(1));
        performDeleteRangeGesture(startArea, endArea, HandwritingGesture.GRANULARITY_WORD);

        // Word 2 (offset 6 to 9) and word 3 (offset 10 to 12) are deleted
        // Since there is whitespace at offset 12, the space before word 2 (offset 5 to 6) is also
        // deleted to avoid a double space.
        // Deleted range: "XXX X [XXX\nXX] X   XX  X. .X " -> "XXX X[ XXX\nXX] X   XX  X. .X "
        assertGestureDeletedRange(5, 12);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#previewHandwritingGesture")
    public void previewDeleteRangeGesture_word() {
        // Word 2 on line 0 spans from offset 6 to offset 9
        float word2HorizontalCenter = (6 + 9) / 2f * CHAR_WIDTH_PX;
        // Horizontal range [word2HorizontalCenter - 1f, word2HorizontalCenter + 1f] covers the
        // centers of word  2.
        RectF startArea = new RectF(
                word2HorizontalCenter - 1f,
                mEditText.getLayout().getLineTop(0),
                word2HorizontalCenter + 1f,
                mEditText.getLayout().getLineBottom(0));
        // Line 1 starts from offset 10.
        // Word 3 on line 1 spans from offset 10 to offset 12
        float word3HorizontalCenter = (0 + 2) / 2f * CHAR_WIDTH_PX;
        // Horizontal range [word3HorizontalCenter - 1f, word3HorizontalCenter + 1f] covers the
        // centers of word  2.
        RectF endArea = new RectF(
                word3HorizontalCenter - 1f,
                mEditText.getLayout().getLineTop(1),
                word3HorizontalCenter + 1f,
                mEditText.getLayout().getLineBottom(1));
        previewDeleteRangeGesture(startArea, endArea, HandwritingGesture.GRANULARITY_WORD);

        // Word 2 (offset 6 to 9) and word 3 (offset 10 to 12) are deleted
        assertDeleteGesturePreviewHighlightRange(6, 12);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performInsertGesture_firstLine() {
        // The point is closest to offset 3 with horizontal position 3 * CHAR_WIDTH_PX.
        performInsertGesture(
                new PointF(3 * CHAR_WIDTH_PX + 2f, mEditText.getLayout().getLineTop(0) + 1f));

        assertGestureInsertedText(3, INSERT_TEXT);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performInsertGesture_secondLine() {
        // The point is closest to offset 3 with horizontal position 3 * CHAR_WIDTH_PX.
        performInsertGesture(
                new PointF(3 * CHAR_WIDTH_PX - 4f, mEditText.getLayout().getLineTop(1) + 1f));

        assertGestureInsertedText(13, INSERT_TEXT);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performInsertGesture_endOfText_insertTextFiltered_shouldFallback() {
        mEditText.setFilters(new InputFilter[] {new DigitsKeyListener(Locale.US)});
        mEditText.setSelection(6);

        // The point is at the end of line 1.
        performInsertGesture(
                new PointF(
                        mEditText.getLayout().getLineRight(1),
                        mEditText.getLayout().getLineTop(1) + 1f),
                /* setFallbackText= */ true);

        // Due to the input filter, all of the inserted text is filtered out.
        assertFallbackTextInserted(6);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performInsertGesture_aboveFirstLineWithinMargin() {
        // The point is closest to offset 3 with horizontal position 3 * CHAR_WIDTH_PX.
        // The point is (mGestureLineMargin - 1) above the top of the line.
        performInsertGesture(
                new PointF(
                        3 * CHAR_WIDTH_PX + 2f,
                        mEditText.getLayout().getLineTop(0) - mGestureLineMargin + 1f));

        assertGestureInsertedText(3, INSERT_TEXT);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performInsertGesture_aboveFirstLine_shouldFallback() {
        mEditText.setSelection(3);

        performInsertGesture(
                new PointF(32f, mEditText.getLayout().getLineTop(0) - mGestureLineMargin - 1f));

        assertFallbackTextInserted(/* initialCursorPosition= */ 3);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performInsertGesture_betweenLinesWithinMargin() {
        // The point is closest to offset 3 with horizontal position 3 * CHAR_WIDTH_PX.
        // The point is (mGestureLineMargin - 1) below the bottom of line 0.
        performInsertGesture(
                new PointF(
                        3 * CHAR_WIDTH_PX + 2f,
                        mEditText.getLayout().getLineBottom(0, false) + mGestureLineMargin - 1f));

        assertGestureInsertedText(3, INSERT_TEXT);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performInsertGesture_betweenLines_shouldFallback() {
        mEditText.setSelection(4);

        // Due to the additional line spacing, this point is in the space between the two lines.
        performInsertGesture(
                new PointF(
                        32f,
                        mEditText.getLayout().getLineBottom(0, false) + mGestureLineMargin + 1f));

        assertFallbackTextInserted(/* initialCursorPosition= */ 4);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performInsertGesture_belowLastLineWithinMargin() {
        // The point is closest to offset 13 with horizontal position 3 * CHAR_WIDTH_PX.
        // The point is (mGestureLineMargin - 1) below the bottom of line 1.
        performInsertGesture(
                new PointF(
                        3 * CHAR_WIDTH_PX + 2f,
                        mEditText.getLayout().getLineBottom(1) + mGestureLineMargin - 1f));

        assertGestureInsertedText(13, INSERT_TEXT);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performInsertGesture_belowLastLine_shouldFallback() {
        mEditText.setSelection(11);

        performInsertGesture(
                new PointF(32f, mEditText.getLayout().getLineBottom(1) + mGestureLineMargin + 1f));

        assertFallbackTextInserted(/* initialCursorPosition= */ 11);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performInsertGesture_leftOfLineWithinMargin() {
        // The point is closest to offset 0 at the start of line 0.
        // The point is (mGestureLineMargin - 1) to the left of line 0.
        performInsertGesture(
                new PointF(-mGestureLineMargin + 1f, mEditText.getLayout().getLineTop(0) + 1f));

        assertGestureInsertedText(0, INSERT_TEXT);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performInsertGesture_leftOfLine_shouldFallback() {
        mEditText.setSelection(7);

        performInsertGesture(
                new PointF(-mGestureLineMargin - 1f, mEditText.getLayout().getLineTop(0) + 1f));

        assertFallbackTextInserted(/* initialCursorPosition= */ 7);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performInsertGesture_rightOfLineWithinMargin() {
        // The point is closest to offset 9 at the end of line 0.
        // The point is (mGestureLineMargin - 1) to the right of line 0.
        performInsertGesture(
                new PointF(
                        mEditText.getLayout().getWidth() + mGestureLineMargin - 1f,
                        mEditText.getLayout().getLineTop(0) + 1f));

        assertGestureInsertedText(9, INSERT_TEXT);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performInsertGesture_rightOfLine_shouldFallback() {
        mEditText.setSelection(6);

        performInsertGesture(
                new PointF(
                        mEditText.getLayout().getWidth() + mGestureLineMargin + 5f,
                        mEditText.getLayout().getLineTop(0) + 1f));

        assertFallbackTextInserted(/* initialCursorPosition= */ 6);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performInsertGesture_noFallback_shouldFail() {
        mEditText.setSelection(6);

        performInsertGesture(
                new PointF(-mGestureLineMargin - 1f, mEditText.getLayout().getLineTop(0) - 1f),
                /* setFallbackText= */ false);

        assertGestureFailure(/* initialCursorPosition= */ 6);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performRemoveSpaceGesture_singleWhitespace() {
        // Line 0 "XXX X XXX" has whitespace from offset 3 to 4.
        // Start point is close to offset 2 on line 0.
        // End point is close to offset 3 on line 0, on the whitespace character.
        performRemoveSpaceGesture(
                new PointF(2 * CHAR_WIDTH_PX - 2f, mEditText.getLayout().getLineTop(0) + 1f),
                new PointF(3 * CHAR_WIDTH_PX + 2f, mEditText.getLayout().getLineTop(0) + 1f));

        assertGestureDeletedRange(3, 4);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performRemoveSpaceGesture_multipleWhitespace() {
        // Line 1 "XX X   XX  X. .X " starts from offset 10 and has whitespace from offset 12 to 13,
        // from offset 14 to 17, and from offset 19 to 21.
        // Start point is close to offset 12 on line 1.
        // End point is close to offset 20 on line 1.
        performRemoveSpaceGesture(
                new PointF(2 * CHAR_WIDTH_PX - 2f, mEditText.getLayout().getLineTop(1) + 1f),
                new PointF(10 * CHAR_WIDTH_PX - 2f, mEditText.getLayout().getLineTop(1) + 1f));

        // The line joining the points covers offsets 12 to 20, so it should remove the first two
        // whitespace groups, and only remove one character from the third whitespace group.
        int whitespaceGroup1Start = 12;
        int whitespaceGroup1End = 13;
        int whitespaceGroup2Start = 14;
        int whitespaceGroup2End = 17;
        int whitespaceGroup3Start = 19;
        int whitespaceGroup3End = 20;
        assertThat(mResult).isEqualTo(InputConnection.HANDWRITING_GESTURE_RESULT_SUCCESS);
        assertThat(mEditText.getText().toString()).isEqualTo(
                DEFAULT_TEXT.substring(0, whitespaceGroup1Start)
                        + DEFAULT_TEXT.substring(whitespaceGroup1End, whitespaceGroup2Start)
                        + DEFAULT_TEXT.substring(whitespaceGroup2End, whitespaceGroup3Start)
                        + DEFAULT_TEXT.substring(whitespaceGroup3End));
        // The cursor is at the last delete position.
        int cursorPosition = whitespaceGroup3Start
                - (whitespaceGroup1End - whitespaceGroup1Start)
                - (whitespaceGroup2End - whitespaceGroup2Start);
        assertThat(mEditText.getSelectionStart()).isEqualTo(cursorPosition);
        assertThat(mEditText.getSelectionEnd()).isEqualTo(cursorPosition);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performRemoveSpaceGesture_touchesTwoLines_appliesToFirstLine() {
        // Line 0 "XXX X XXX" has whitespace from offset 3 to 4.
        // Line 1 "XX X   XX  X. .X " starts from offset 10 and has whitespace from offset 12 to 13.
        // Start point is close to offset 12 on line 1.
        // End point is close to offset 4 on line 0.
        performRemoveSpaceGesture(
                new PointF(2 * CHAR_WIDTH_PX - 2f, mEditText.getLayout().getLineTop(1) + 1f),
                new PointF(4 * CHAR_WIDTH_PX + 2f, mEditText.getLayout().getLineTop(0) + 1f));

        // The start point is on line 1 and the end point is on line 0.
        // The gesture only applies to the first line touched (line 0), so the whitespace on line 1
        // is not deleted.
        assertGestureDeletedRange(3, 4);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performRemoveSpaceGesture_noWhitespaceTouched_shouldFallback() {
        mEditText.setSelection(7);

        // Line 0 "XXX X XXX" has no whitespace before offset 3.
        // Start point is close to offset 0 on line 0.
        // End point is close to offset 3 on line 0.
        performRemoveSpaceGesture(
                new PointF(2f, mEditText.getLayout().getLineTop(0) + 1f),
                new PointF(3 * CHAR_WIDTH_PX - 2f, mEditText.getLayout().getLineTop(0) + 1f));

        // There is no whitespace from offset 0 to 3.
        assertFallbackTextInserted(/* initialCursorPosition= */ 7);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performRemoveSpaceGesture_aboveFirstLine_shouldFallback() {
        mEditText.setSelection(3);

        // Both points are above line 0.
        performRemoveSpaceGesture(
                new PointF(
                        2 * CHAR_WIDTH_PX - 2f,
                        mEditText.getLayout().getLineTop(0) - mGestureLineMargin - 1f),
                new PointF(
                        4 * CHAR_WIDTH_PX + 2f,
                        mEditText.getLayout().getLineTop(0) - mGestureLineMargin - 2f));

        assertFallbackTextInserted(/* initialCursorPosition= */ 3);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performRemoveSpaceGesture_betweenLines_shouldFallback() {
        mEditText.setSelection(4);

        // Due to the additional line spacing, both points are in the space between the two lines.
        performRemoveSpaceGesture(
                new PointF(
                        2 * CHAR_WIDTH_PX - 2f,
                        mEditText.getLayout().getLineBottom(0, /* includeLineSpacing= */ false)
                                + mGestureLineMargin
                                + 1f),
                new PointF(
                        4 * CHAR_WIDTH_PX + 2f,
                        mEditText.getLayout().getLineBottom(0, /* includeLineSpacing= */ false)
                                + mGestureLineMargin
                                + 2f));

        assertFallbackTextInserted(/* initialCursorPosition= */ 4);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performRemoveSpaceGesture_belowLastLine_shouldFallback() {
        mEditText.setSelection(11);

        // Both points are below line 1.
        performRemoveSpaceGesture(
                new PointF(
                        2 * CHAR_WIDTH_PX - 2f,
                        mEditText.getLayout().getLineBottom(1) + mGestureLineMargin + 1f),
                new PointF(
                        4 * CHAR_WIDTH_PX + 2f,
                        mEditText.getLayout().getLineBottom(1) + mGestureLineMargin + 2f));

        assertFallbackTextInserted(/* initialCursorPosition= */ 11);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performRemoveSpaceGesture_leftOfLine_shouldFallback() {
        mEditText.setSelection(7);

        // Both points are to the left of line 0.
        performRemoveSpaceGesture(
                new PointF(-mGestureLineMargin - 2f, mEditText.getLayout().getLineTop(0) + 1f),
                new PointF(-mGestureLineMargin - 1f, mEditText.getLayout().getLineTop(0) + 2f));

        assertFallbackTextInserted(/* initialCursorPosition= */ 7);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performRemoveSpaceGesture_rightOfLine_shouldFallback() {
        mEditText.setSelection(6);

        // The first line has 9 characters, so it ends at horizontal position 9 * CHAR_WIDTH_PX.
        // Both points are to the right of line 0.
        performRemoveSpaceGesture(
                new PointF(
                        mEditText.getLayout().getWidth() + mGestureLineMargin + 5f,
                        mEditText.getLayout().getLineTop(0) + 1f),
                new PointF(
                        mEditText.getLayout().getWidth() + mGestureLineMargin + 7f,
                        mEditText.getLayout().getLineTop(0) + 2f));

        assertFallbackTextInserted(/* initialCursorPosition= */ 6);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performRemoveSpaceGesture_noFallback_shouldFail() {
        mEditText.setSelection(6);

        // Both points are above line 0.
        performRemoveSpaceGesture(
                new PointF(
                        2 * CHAR_WIDTH_PX - 2f,
                        mEditText.getLayout().getLineTop(0) - mGestureLineMargin - 1f),
                new PointF(
                        4 * CHAR_WIDTH_PX + 2f,
                        mEditText.getLayout().getLineTop(0) - mGestureLineMargin - 2f),
                /* setFallbackText= */ false);

        assertGestureFailure(/* initialCursorPosition= */ 6);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performJoinOrSplitGesture_whitespaceStartBoundary_shouldJoin() {
        // Line 1 "XX X   XX  X. .X " starts from offset 10 and has whitespace from offset 14 to 17.
        performJoinOrSplitGesture(
                new PointF(4 * CHAR_WIDTH_PX - 2f, mEditText.getLayout().getLineTop(1) + 1f));

        // The point is closest to offset 14, which is on the boundary of the whitespace from offset
        // 14 to 17.
        assertGestureDeletedRange(14, 17);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performJoinOrSplitGesture_whitespaceEndBoundary_shouldJoin() {
        // Line 1 "XX X   XX  X. .X " starts from offset 10 and has whitespace from offset 14 to 17.
        performJoinOrSplitGesture(
                new PointF(7 * CHAR_WIDTH_PX + 2f, mEditText.getLayout().getLineTop(1) + 1f));

        // The point is closest to offset 17, which is on the boundary of the whitespace from offset
        // 14 to 17.
        assertGestureDeletedRange(14, 17);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performJoinOrSplitGesture_whitespaceMiddle_shouldJoin() {
        // Line 1 "XX X   XX  X. .X " starts from offset 10 and has whitespace from offset 14 to 17.
        performJoinOrSplitGesture(
                new PointF(5 * CHAR_WIDTH_PX, mEditText.getLayout().getLineTop(1) + 1f));

        // The point is closest to offset 15, which is within the whitespace from offset 14 to 17.
        assertGestureDeletedRange(14, 17);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performJoinOrSplitGesture_noWhitespace_shouldInsertSpace() {
        // Line 1 "XX X   XX  X. .X " starts from offset 10 and has a word from offset 17 to 19.
        performJoinOrSplitGesture(
                new PointF(8 * CHAR_WIDTH_PX, mEditText.getLayout().getLineTop(1) + 1f));

        // The point is closest to offset 18, which does not touch whitespace.
        assertGestureInsertedText(18, " ");
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performJoinOrSplitGesture_spaceFiltered_shouldFallback() {
        mEditText.setFilters(new InputFilter[] {new DigitsKeyListener(Locale.US)});
        mEditText.setSelection(6);

        // Line 1 "XX X   XX  X. .X " starts from offset 10 and has a word from offset 17 to 19.
        performJoinOrSplitGesture(
                new PointF(8 * CHAR_WIDTH_PX, mEditText.getLayout().getLineTop(1) + 1f));

        // The point is closest to offset 18, which does not touch whitespace.
        // Due to the input filter, the space is filtered out.
        assertFallbackTextInserted(6);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performJoinOrSplitGesture_aboveFirstLine_shouldFallback() {
        mEditText.setSelection(3);

        // The point is above line 0.
        performJoinOrSplitGesture(
                new PointF(32f, mEditText.getLayout().getLineTop(0) - mGestureLineMargin - 1f));

        assertFallbackTextInserted(/* initialCursorPosition= */ 3);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performJoinOrSplitGesture_betweenLines_shouldFallback() {
        mEditText.setSelection(4);

        // Due to the additional line spacing, this point is in the space between the two lines.
        performJoinOrSplitGesture(
                new PointF(
                        32f,
                        mEditText.getLayout().getLineBottom(0, /* includeLineSpacing= */ false)
                                + mGestureLineMargin
                                + 1f));

        assertFallbackTextInserted(/* initialCursorPosition= */ 4);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performJoinOrSplitGesture_belowLastLine_shouldFallback() {
        mEditText.setSelection(11);

        // The point is below line 1.
        performJoinOrSplitGesture(
                new PointF(32f, mEditText.getLayout().getLineBottom(1) + mGestureLineMargin + 1f));

        assertFallbackTextInserted(/* initialCursorPosition= */ 11);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performJoinOrSplitGesture_leftOfLine_shouldFallback() {
        mEditText.setSelection(7);

        // The point is to the left of line 0.
        performJoinOrSplitGesture(
                new PointF(-mGestureLineMargin - 1f, mEditText.getLayout().getLineTop(0) + 1f));

        assertFallbackTextInserted(/* initialCursorPosition= */ 7);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performJoinOrSplitGesture_rightOfLine_shouldFallback() {
        mEditText.setSelection(6);

        // The first line has 9 characters, so it ends at horizontal position 9 * CHAR_WIDTH_PX.
        // The point is to the right of line 0.
        performJoinOrSplitGesture(
                new PointF(
                        mEditText.getLayout().getWidth() + mGestureLineMargin + 5f,
                        mEditText.getLayout().getLineTop(0) + 1f));

        assertFallbackTextInserted(/* initialCursorPosition= */ 6);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performJoinOrSplitGesture_noFallback_shouldFail() {
        mEditText.setSelection(6);

        // The point is above line 0.
        performJoinOrSplitGesture(
                new PointF(-1f, mEditText.getLayout().getLineTop(0) - mGestureLineMargin - 1f),
                /* setFallbackText= */ false);

        assertGestureFailure(/* initialCursorPosition= */ 6);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performInsertModeGesture_firstLine() {
        // The point is closest to offset 3 with horizontal position 3 * CHAR_WIDTH_PX.
        InsertModeGesture gesture = performInsertModeGesture(
                new PointF(3 * CHAR_WIDTH_PX + 2f, mEditText.getLayout().getLineTop(0) + 1f));

        assertGestureInsertMode(/* offset= */ 3);

        gesture.getCancellationSignal().cancel();
        assertNoInsertMode();
        assertCursorOffset(3);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performInsertModeGesture_secondLine() {
        // The point is closest to offset 3 with horizontal position 3 * CHAR_WIDTH_PX.
        InsertModeGesture gesture = performInsertModeGesture(
                new PointF(3 * CHAR_WIDTH_PX - 4f, mEditText.getLayout().getLineTop(1) + 1f));

        assertGestureInsertMode(/* offset= */ 13);

        gesture.getCancellationSignal().cancel();
        assertNoInsertMode();
        assertCursorOffset(13);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performInsertModeGesture_aboveFirstLineWithinMargin() {
        // The point is closest to offset 3 with horizontal position 3 * CHAR_WIDTH_PX.
        // The point is (mGestureLineMargin - 1) above the top of the line.
        InsertModeGesture gesture = performInsertModeGesture(
                new PointF(
                        3 * CHAR_WIDTH_PX + 2f,
                        mEditText.getLayout().getLineTop(0) - mGestureLineMargin + 1f));

        assertGestureInsertMode(3);

        gesture.getCancellationSignal().cancel();
        assertNoInsertMode();
        assertCursorOffset(3);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performInsertModeGesture_aboveFirstLine_shouldFallback() {
        mEditText.setSelection(3);

        performInsertGesture(
                new PointF(32f, mEditText.getLayout().getLineTop(0) - mGestureLineMargin - 1f));

        assertFallbackTextInserted(/* initialCursorPosition= */ 3);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performInsertModeGesture_betweenLinesWithinMargin() {
        // The point is closest to offset 3 with horizontal position 3 * CHAR_WIDTH_PX.
        // The point is (mGestureLineMargin - 1) below the bottom of line 0.
        InsertModeGesture gesture = performInsertModeGesture(
                new PointF(
                        3 * CHAR_WIDTH_PX + 2f,
                        mEditText.getLayout().getLineBottom(0, false) + mGestureLineMargin - 1f));

        assertGestureInsertMode(/* offset= */ 3);

        gesture.getCancellationSignal().cancel();
        assertNoInsertMode();
        assertCursorOffset(3);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performInsertModeGesture_betweenLines_shouldFallback() {
        mEditText.setSelection(4);

        // Due to the additional line spacing, this point is in the space between the two lines.
        performInsertModeGesture(
                new PointF(
                        32f,
                        mEditText.getLayout().getLineBottom(0, false) + mGestureLineMargin + 1f));

        assertFallbackTextInserted(/* initialCursorPosition= */ 4);
        assertNoInsertMode();
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performInsertModeGesture_belowLastLineWithinMargin() {
        // The point is closest to offset 13 with horizontal position 3 * CHAR_WIDTH_PX.
        // The point is (mGestureLineMargin - 1) below the bottom of line 1.
        InsertModeGesture gesture = performInsertModeGesture(
                new PointF(
                        3 * CHAR_WIDTH_PX + 2f,
                        mEditText.getLayout().getLineBottom(1) + mGestureLineMargin - 1f));

        assertGestureInsertMode(/* offset= */ 13);

        gesture.getCancellationSignal().cancel();
        assertNoInsertMode();
        assertCursorOffset(13);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performInsertModeGesture_belowLastLine_shouldFallback() {
        mEditText.setSelection(11);

        performInsertModeGesture(
                new PointF(32f, mEditText.getLayout().getLineBottom(1) + mGestureLineMargin + 1f));

        assertFallbackTextInserted(/* initialCursorPosition= */ 11);
        assertNoInsertMode();
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performInsertModeGesture_leftOfLineWithinMargin() {
        // The point is closest to offset 0 at the start of line 0.
        // The point is (mGestureLineMargin - 1) to the left of line 0.
        InsertModeGesture gesture = performInsertModeGesture(
                new PointF(-mGestureLineMargin + 1f, mEditText.getLayout().getLineTop(0) + 1f));

        assertGestureInsertMode(/* offset= */ 0);

        gesture.getCancellationSignal().cancel();
        assertNoInsertMode();
        assertCursorOffset(0);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performInsertModeGesture_leftOfLine_shouldFallback() {
        mEditText.setSelection(7);

        performInsertModeGesture(
                new PointF(-mGestureLineMargin - 1f, mEditText.getLayout().getLineTop(0) + 1f));

        assertFallbackTextInserted(/* initialCursorPosition= */ 7);
        assertNoInsertMode();
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performInsertModeGesture_rightOfLineWithinMargin() {
        // The point is closest to offset 9 at the end of line 0.
        // The point is (mGestureLineMargin - 1) to the right of line 0.
        InsertModeGesture gesture = performInsertModeGesture(
                new PointF(
                        mEditText.getLayout().getWidth() + mGestureLineMargin - 1f,
                        mEditText.getLayout().getLineTop(0) + 1f));

        assertGestureInsertMode(/* offset= */ 9);

        gesture.getCancellationSignal().cancel();
        assertNoInsertMode();
        assertCursorOffset(9);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performInsertModeGesture_rightOfLine_shouldFallback() {
        mEditText.setSelection(6);

        performInsertModeGesture(
                new PointF(
                        mEditText.getLayout().getWidth() + mGestureLineMargin + 5f,
                        mEditText.getLayout().getLineTop(0) + 1f));

        assertFallbackTextInserted(/* initialCursorPosition= */ 6);
        assertNoInsertMode();
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performInsertModeGesture_noFallback_shouldFail() {
        mEditText.setSelection(6);

        performInsertModeGesture(
                new PointF(-mGestureLineMargin - 1f, mEditText.getLayout().getLineTop(0) - 1f),
                /* setFallbackText= */ false);

        assertGestureFailure(/* initialCursorPosition= */ 6);
        assertNoInsertMode();
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performInsertModeGesture_singleLine() {
        setEditTextSingleLine();

        // The point is closest to offset 3 with horizontal position 3 * CHAR_WIDTH_PX.
        InsertModeGesture gesture = performInsertModeGesture(
                new PointF(3 * CHAR_WIDTH_PX + 2f, mEditText.getLayout().getLineTop(0) + 1f));

        assertGestureInsertMode(/* offset= */ 3);

        gesture.getCancellationSignal().cancel();
        assertNoInsertMode();
        assertCursorOffset(3);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performInsertModeGesture_aboveLineWithinMargin_singleLine() {
        setEditTextSingleLine();
        // The point is closest to offset 3 with horizontal position 3 * CHAR_WIDTH_PX.
        // The point is (mGestureLineMargin - 1) above the top of the line.
        InsertModeGesture gesture = performInsertModeGesture(
                new PointF(
                        3 * CHAR_WIDTH_PX + 2f,
                        mEditText.getLayout().getLineTop(0) - mGestureLineMargin + 1f));

        assertGestureInsertMode(3);

        gesture.getCancellationSignal().cancel();
        assertNoInsertMode();
        assertCursorOffset(3);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performInsertModeGesture_aboveLine_shouldFallback_singleLine() {
        setEditTextSingleLine();
        mEditText.setSelection(3);

        performInsertGesture(
                new PointF(32f, mEditText.getLayout().getLineTop(0) - mGestureLineMargin - 1f));

        assertFallbackTextInserted(/* initialCursorPosition= */ 3);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performInsertModeGesture_belowLineWithinMargin_singleLine() {
        setEditTextSingleLine();
        // The point is closest to offset 3 with horizontal position 3 * CHAR_WIDTH_PX.
        // The point is (mGestureLineMargin - 1) below the bottom of line 0.
        InsertModeGesture gesture = performInsertModeGesture(
                new PointF(
                        3 * CHAR_WIDTH_PX + 2f,
                        mEditText.getLayout().getLineBottom(0) + mGestureLineMargin - 1f));

        assertGestureInsertMode(/* offset= */ 3);

        gesture.getCancellationSignal().cancel();
        assertNoInsertMode();
        assertCursorOffset(3);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performInsertModeGesture_belowLine_shouldFallback_singleLine() {
        setEditTextSingleLine();
        mEditText.setSelection(11);

        performInsertModeGesture(
                new PointF(32f, mEditText.getLayout().getLineBottom(0) + mGestureLineMargin + 1f));

        assertFallbackTextInserted(/* initialCursorPosition= */ 11);
        assertNoInsertMode();
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performInsertModeGesture_leftOfLineWithinMargin_singleLine() {
        setEditTextSingleLine();
        // The point is closest to offset 0 at the start of line 0.
        // The point is (mGestureLineMargin - 1) to the left of line 0.
        InsertModeGesture gesture = performInsertModeGesture(
                new PointF(-mGestureLineMargin + 1f, mEditText.getLayout().getLineTop(0) + 1f));

        assertGestureInsertMode(/* offset= */ 0);

        gesture.getCancellationSignal().cancel();
        assertNoInsertMode();
        assertCursorOffset(0);
    }


    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performInsertModeGesture_leftOfLine_shouldFallback_singleLine() {
        setEditTextSingleLine();
        mEditText.setSelection(7);

        performInsertModeGesture(
                new PointF(-mGestureLineMargin - 1f, mEditText.getLayout().getLineTop(0) + 1f));

        assertFallbackTextInserted(/* initialCursorPosition= */ 7);
        assertNoInsertMode();
    }


    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performInsertModeGesture_rightOfLineWithinMargin_singleLine() {
        setEditTextSingleLine();
        // The point is closest to offset 27 at the end of line 0.
        // The point is (mGestureLineMargin - 1) to the right of line 0.
        InsertModeGesture gesture = performInsertModeGesture(
                new PointF(
                        mEditText.getLayout().getWidth() + mGestureLineMargin - 1f,
                        mEditText.getLayout().getLineTop(0) + 1f));

        assertGestureInsertMode(/* offset= */ 27);

        gesture.getCancellationSignal().cancel();
        assertNoInsertMode();
        assertCursorOffset(27);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performInsertModeGesture_rightOfLine_shouldFallback_singleLine() {
        setEditTextSingleLine();
        mEditText.setSelection(6);

        performInsertModeGesture(
                new PointF(
                        mEditText.getLayout().getWidth() + mGestureLineMargin + 5f,
                        mEditText.getLayout().getLineTop(0) + 1f));

        assertFallbackTextInserted(/* initialCursorPosition= */ 6);
        assertNoInsertMode();
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performInsertModeGesture_noFallback_shouldFail_singleLine() {
        setEditTextSingleLine();
        mEditText.setSelection(6);

        performInsertModeGesture(
                new PointF(-mGestureLineMargin - 1f, mEditText.getLayout().getLineTop(0) - 1f),
                /* setFallbackText= */ false);

        assertGestureFailure(/* initialCursorPosition= */ 6);
        assertNoInsertMode();
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performInsertModeGesture_appendText() {
        InsertModeGesture gesture = performInsertModeGesture(
                new PointF(3 * CHAR_WIDTH_PX, mEditText.getLayout().getLineTop(0) - 1f),
                /* setFallbackText= */ false);

        final int expectedOffset = 3;
        assertGestureInsertMode(expectedOffset);

        final String insertText = "insert text";
        performAppendText(insertText);
        assertThat(mEditText.getText().toString()).isEqualTo(
                DEFAULT_TEXT.substring(0, expectedOffset) + insertText
                        + DEFAULT_TEXT.substring(expectedOffset));

        // The highlight range includes the placeholder text.
        assertGestureInsertModeHighlightRange(expectedOffset,
                expectedOffset + insertText.length() + PLACEHOLDER_TEXT_MULTI_LINE.length());
        gesture.getCancellationSignal().cancel();
        assertNoInsertMode();
        assertCursorOffset(expectedOffset + insertText.length());
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performInsertModeGesture_appendText_singleLine() {
        setEditTextSingleLine();

        InsertModeGesture gesture = performInsertModeGesture(
                new PointF(3 * CHAR_WIDTH_PX, mEditText.getLayout().getLineTop(0) - 1f),
                /* setFallbackText= */ false);

        final int expectedOffset = 3;
        assertGestureInsertMode(expectedOffset);

        final String insertText = "insert text";
        performAppendText(insertText);
        assertThat(mEditText.getText().toString()).isEqualTo(
                DEFAULT_TEXT.substring(0, expectedOffset) + insertText
                        + DEFAULT_TEXT.substring(expectedOffset));

        // The highlight range includes the placeholder text.
        assertGestureInsertModeHighlightRange(expectedOffset,
                expectedOffset + insertText.length() + PLACEHOLDER_TEXT_SINGLE_LINE.length());
        gesture.getCancellationSignal().cancel();
        assertNoInsertMode();
        assertCursorOffset(expectedOffset + insertText.length());
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performInsertModeGesture_exitAfterLostFocus() {
        mEditText.requestFocus();

        performInsertModeGesture(
                new PointF(3 * CHAR_WIDTH_PX, mEditText.getLayout().getLineTop(0) - 1f),
                /* setFallbackText= */ false);

        final int expectedOffset = 3;
        assertGestureInsertMode(expectedOffset);

        mEditText.clearFocus();
        assertNoInsertMode();
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performInsertModeGesture_exitAfterSetText() {
        mEditText.requestFocus();

        performInsertModeGesture(
                new PointF(3 * CHAR_WIDTH_PX, mEditText.getLayout().getLineTop(0) - 1f),
                /* setFallbackText= */ false);

        final int expectedOffset = 3;
        assertGestureInsertMode(expectedOffset);

        mEditText.setText("");
        assertNoInsertMode();
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performInsertModeGesture_setTransformationMethod() {
        InsertModeGesture gesture = performInsertModeGesture(
                new PointF(3 * CHAR_WIDTH_PX, mEditText.getLayout().getLineTop(0) - 1f),
                /* setFallbackText= */ false);

        int expectedOffset = 3;
        assertGestureInsertMode(expectedOffset);

        // Set PasswordTransformation, which will replace all character to DOT.
        mEditText.setTransformationMethod(new PasswordTransformationMethod());
        layoutEditText();

        String placeholder = PLACEHOLDER_TEXT_MULTI_LINE;
        String expectedText = DOT.repeat(expectedOffset) + placeholder
                + DOT.repeat(DEFAULT_TEXT.length() - expectedOffset);
        String displayText = mEditText.getLayout().getText().toString();

        assertThat(displayText).isEqualTo(expectedText);
        assertCursorOffset(expectedOffset);
        assertGestureInsertModeHighlightRange(expectedOffset,
                expectedOffset + placeholder.length());

        gesture.getCancellationSignal().cancel();
        assertNoInsertModeHighlight();
        assertThat(mEditText.getLayout().getText().toString())
                .isEqualTo(DOT.repeat(DEFAULT_TEXT.length()));
        assertCursorOffset(expectedOffset);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performInsertModeGesture_setTransformationMethod_singleLine() {
        setEditTextSingleLine();
        InsertModeGesture gesture = performInsertModeGesture(
                new PointF(3 * CHAR_WIDTH_PX, mEditText.getLayout().getLineTop(0) - 1f),
                /* setFallbackText= */ false);

        int expectedOffset = 3;
        assertGestureInsertMode(expectedOffset);

        // Set PasswordTransformation, which will replace all character to DOT.
        mEditText.setTransformationMethod(new PasswordTransformationMethod());
        layoutEditText();

        String placeholder = PLACEHOLDER_TEXT_SINGLE_LINE;
        String expectedText = DOT.repeat(expectedOffset) + placeholder
                + DOT.repeat(DEFAULT_TEXT.length() - expectedOffset);
        String displayText = mEditText.getLayout().getText().toString();

        assertThat(displayText).isEqualTo(expectedText);
        assertCursorOffset(expectedOffset);
        assertGestureInsertModeHighlightRange(expectedOffset,
                expectedOffset + placeholder.length());

        gesture.getCancellationSignal().cancel();
        assertNoInsertModeHighlight();
        assertThat(mEditText.getLayout().getText().toString())
                .isEqualTo(DOT.repeat(DEFAULT_TEXT.length()));
        assertCursorOffset(expectedOffset);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    @RequiresFlagsEnabled(Flags.FLAG_INSERT_MODE_NOT_UPDATE_SELECTION)
    public void performInsertModeGesture_notTriggerTextWatcher() {
        TextWatcher testTextWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                fail("TextWatcher#beforeTextChanged shouldn't be called when entering insert mode");
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                fail("TextWatcher#onTextChanged shouldn't be called when entering insert mode");
            }

            @Override
            public void afterTextChanged(Editable s) {
                fail("TextWatcher#afterTextChanged shouldn't be called when entering insert mode");
            }
        };
        mEditText.addTextChangedListener(testTextWatcher);

        // Enter and exit the insert mode, the testTextWatcher shouldn't be called.
        InsertModeGesture gesture = performInsertModeGesture(
                new PointF(3 * CHAR_WIDTH_PX, mEditText.getLayout().getLineTop(0) - 1f),
                /* setFallbackText= */ false);

        gesture.getCancellationSignal().cancel();
    }

    private void setEditTextSingleLine() {
        mEditText.setSingleLine(true);
        mEditText.measure(
                View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        mEditText.layout(0, 0, WIDTH, HEIGHT);
        mEditText.setLayoutParams(new ViewGroup.LayoutParams(WIDTH, HEIGHT));

        mLocationOnScreen = mEditText.getLocationOnScreen();
        mLocationOnScreen[0] += mEditText.getTotalPaddingLeft();
        mLocationOnScreen[1] += mEditText.getTotalPaddingTop();
    }

    private void performSelectGesture(RectF area, int granularity) {
        performSelectGesture(area, granularity, /* setFallbackText= */ true);
    }

    private void performSelectGesture(RectF area, int granularity, boolean setFallbackText) {
        area.offset(mLocationOnScreen[0], mLocationOnScreen[1]);
        HandwritingGesture gesture = new SelectGesture.Builder()
                .setSelectionArea(area)
                .setGranularity(granularity)
                .setFallbackText(setFallbackText ? FALLBACK_TEXT : null)
                .build();
        InputConnection inputConnection = mEditText.onCreateInputConnection(new EditorInfo());
        inputConnection.performHandwritingGesture(gesture, Runnable::run, mResultConsumer);
    }

    private void previewSelectGesture(RectF area, int granularity) {
        area.offset(mLocationOnScreen[0], mLocationOnScreen[1]);
        SelectGesture gesture = new SelectGesture.Builder()
                .setSelectionArea(area)
                .setGranularity(granularity)
                .build();
        InputConnection inputConnection = mEditText.onCreateInputConnection(new EditorInfo());
        inputConnection.previewHandwritingGesture(gesture, null);
    }

    private void performSelectRangeGesture(RectF startArea, RectF endArea, int granularity) {
        startArea.offset(mLocationOnScreen[0], mLocationOnScreen[1]);
        endArea.offset(mLocationOnScreen[0], mLocationOnScreen[1]);
        HandwritingGesture gesture = new SelectRangeGesture.Builder()
                .setSelectionStartArea(startArea)
                .setSelectionEndArea(endArea)
                .setGranularity(granularity)
                .setFallbackText(FALLBACK_TEXT)
                .build();
        InputConnection inputConnection = mEditText.onCreateInputConnection(new EditorInfo());
        inputConnection.performHandwritingGesture(gesture, Runnable::run, mResultConsumer);
    }

    private void previewSelectRangeGesture(RectF startArea, RectF endArea, int granularity) {
        startArea.offset(mLocationOnScreen[0], mLocationOnScreen[1]);
        endArea.offset(mLocationOnScreen[0], mLocationOnScreen[1]);
        SelectRangeGesture gesture = new SelectRangeGesture.Builder()
                .setSelectionStartArea(startArea)
                .setSelectionEndArea(endArea)
                .setGranularity(granularity)
                .setFallbackText(FALLBACK_TEXT) // fallback text should be ignored for preview
                .build();
        InputConnection inputConnection = mEditText.onCreateInputConnection(new EditorInfo());
        inputConnection.previewHandwritingGesture(gesture, null);
    }

    private void performDeleteGesture(RectF area, int granularity) {
        performDeleteGesture(area, granularity, /* setFallbackText= */ true);
    }

    private void performDeleteGesture(RectF area, int granularity, boolean setFallbackText) {
        area.offset(mLocationOnScreen[0], mLocationOnScreen[1]);
        HandwritingGesture gesture = new DeleteGesture.Builder()
                .setDeletionArea(area)
                .setGranularity(granularity)
                .setFallbackText(setFallbackText ? FALLBACK_TEXT : null)
                .build();
        InputConnection inputConnection = mEditText.onCreateInputConnection(new EditorInfo());
        inputConnection.performHandwritingGesture(gesture, Runnable::run, mResultConsumer);
    }

    private void previewDeleteGesture(RectF area, int granularity) {
        area.offset(mLocationOnScreen[0], mLocationOnScreen[1]);
        DeleteGesture gesture = new DeleteGesture.Builder()
                .setDeletionArea(area)
                .setGranularity(granularity)
                .build();
        InputConnection inputConnection = mEditText.onCreateInputConnection(new EditorInfo());
        inputConnection.previewHandwritingGesture(gesture, null);
    }

    private void performDeleteRangeGesture(RectF startArea, RectF endArea, int granularity) {
        startArea.offset(mLocationOnScreen[0], mLocationOnScreen[1]);
        endArea.offset(mLocationOnScreen[0], mLocationOnScreen[1]);
        HandwritingGesture gesture = new DeleteRangeGesture.Builder()
                .setDeletionStartArea(startArea)
                .setDeletionEndArea(endArea)
                .setGranularity(granularity)
                .setFallbackText(FALLBACK_TEXT)
                .build();
        InputConnection inputConnection = mEditText.onCreateInputConnection(new EditorInfo());
        inputConnection.performHandwritingGesture(gesture, Runnable::run, mResultConsumer);
    }

    private void previewDeleteRangeGesture(RectF startArea, RectF endArea, int granularity) {
        startArea.offset(mLocationOnScreen[0], mLocationOnScreen[1]);
        endArea.offset(mLocationOnScreen[0], mLocationOnScreen[1]);
        DeleteRangeGesture gesture = new DeleteRangeGesture.Builder()
                .setDeletionStartArea(startArea)
                .setDeletionEndArea(endArea)
                .setGranularity(granularity)
                .setFallbackText(FALLBACK_TEXT) // fallback text should be ignored for preview
                .build();
        InputConnection inputConnection = mEditText.onCreateInputConnection(new EditorInfo());
        inputConnection.previewHandwritingGesture(gesture, null);
    }

    private void performInsertGesture(PointF point) {
        performInsertGesture(point, /* setFallbackText= */ true);
    }

    private void performInsertGesture(PointF point, boolean setFallbackText) {
        point.offset(mLocationOnScreen[0], mLocationOnScreen[1]);
        HandwritingGesture gesture = new InsertGesture.Builder()
                .setInsertionPoint(point)
                .setTextToInsert(INSERT_TEXT)
                .setFallbackText(setFallbackText ? FALLBACK_TEXT : null)
                .build();
        InputConnection inputConnection = mEditText.onCreateInputConnection(new EditorInfo());
        inputConnection.performHandwritingGesture(gesture, Runnable::run, mResultConsumer);
    }

    private InsertModeGesture performInsertModeGesture(PointF point) {
        return performInsertModeGesture(point, /* setFallbackText= */ true);
    }

    private InsertModeGesture performInsertModeGesture(PointF point, boolean setFallbackText) {
        point.offset(mLocationOnScreen[0], mLocationOnScreen[1]);
        InsertModeGesture gesture = new InsertModeGesture.Builder()
                .setInsertionPoint(point)
                .setCancellationSignal(new CancellationSignal())
                .setFallbackText(setFallbackText ? FALLBACK_TEXT : null)
                .build();
        InputConnection inputConnection = mEditText.onCreateInputConnection(new EditorInfo());
        inputConnection.performHandwritingGesture(gesture, Runnable::run, mResultConsumer);
        // Performing insert mode will invalidate the layout and only updates it until relayout.
        // Since the text EditText is headless, we have to call it manually.
        layoutEditText();
        return gesture;
    }

    private void layoutEditText() {
        mEditText.measure(
                View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        mEditText.layout(0, 0, WIDTH, HEIGHT);
        mEditText.setLayoutParams(new ViewGroup.LayoutParams(WIDTH, HEIGHT));
    }

    private void performAppendText(String text) {
        InputConnection inputConnection = mEditText.onCreateInputConnection(new EditorInfo());
        inputConnection.commitText(text, 1);
    }


    private void performRemoveSpaceGesture(PointF startPoint, PointF endPoint) {
        performRemoveSpaceGesture(startPoint, endPoint, /* setFallbackText= */ true);
    }

    private void performRemoveSpaceGesture(
            PointF startPoint, PointF endPoint, boolean setFallbackText) {
        startPoint.offset(mLocationOnScreen[0], mLocationOnScreen[1]);
        endPoint.offset(mLocationOnScreen[0], mLocationOnScreen[1]);
        HandwritingGesture gesture = new RemoveSpaceGesture.Builder()
                .setPoints(startPoint, endPoint)
                .setFallbackText(setFallbackText ? FALLBACK_TEXT : null)
                .build();
        InputConnection inputConnection = mEditText.onCreateInputConnection(new EditorInfo());
        inputConnection.performHandwritingGesture(gesture, Runnable::run, mResultConsumer);
    }

    private void performJoinOrSplitGesture(PointF point) {
        performJoinOrSplitGesture(point, /* setFallbackText= */ true);
    }

    private void performJoinOrSplitGesture(PointF point, boolean setFallbackText) {
        point.offset(mLocationOnScreen[0], mLocationOnScreen[1]);
        HandwritingGesture gesture = new JoinOrSplitGesture.Builder()
                .setJoinOrSplitPoint(point)
                .setFallbackText(setFallbackText ? FALLBACK_TEXT : null)
                .build();
        InputConnection inputConnection = mEditText.onCreateInputConnection(new EditorInfo());
        inputConnection.performHandwritingGesture(gesture, Runnable::run, mResultConsumer);
    }

    private void assertGestureSelectedRange(int start, int end) {
        assertThat(mResult).isEqualTo(InputConnection.HANDWRITING_GESTURE_RESULT_SUCCESS);
        // Check that the text has not changed.
        assertThat(mEditText.getText().toString()).isEqualTo(DEFAULT_TEXT);
        assertThat(mEditText.getSelectionStart()).isEqualTo(start);
        assertThat(mEditText.getSelectionEnd()).isEqualTo(end);
    }

    private void assertGestureDeletedRange(int start, int end) {
        assertThat(mResult).isEqualTo(InputConnection.HANDWRITING_GESTURE_RESULT_SUCCESS);
        assertThat(mEditText.getText().toString())
                .isEqualTo(DEFAULT_TEXT.substring(0, start) + DEFAULT_TEXT.substring(end));
        assertThat(mEditText.getSelectionStart()).isEqualTo(start);
        assertThat(mEditText.getSelectionEnd()).isEqualTo(start);
    }

    private void assertGestureInsertedText(int offset, String insertText) {
        assertThat(mResult).isEqualTo(InputConnection.HANDWRITING_GESTURE_RESULT_SUCCESS);
        assertThat(mEditText.getText().toString()).isEqualTo(
                DEFAULT_TEXT.substring(0, offset) + insertText + DEFAULT_TEXT.substring(offset));
        assertThat(mEditText.getSelectionStart()).isEqualTo(offset + insertText.length());
        assertThat(mEditText.getSelectionEnd()).isEqualTo(offset + insertText.length());
    }

    private void assertGestureInsertMode(int offset) {
        assertThat(mResult).isEqualTo(InputConnection.HANDWRITING_GESTURE_RESULT_SUCCESS);
        assertThat(mEditText.getText().toString()).isEqualTo(DEFAULT_TEXT);
        assertThat(mEditText.getSelectionStart()).isEqualTo(offset);
        assertThat(mEditText.getSelectionEnd()).isEqualTo(offset);

        final boolean singleLine = mEditText.isSingleLine();
        final Layout layout = mEditText.getLayout();
        final String placeholder =
                singleLine ? PLACEHOLDER_TEXT_SINGLE_LINE : PLACEHOLDER_TEXT_MULTI_LINE;

        String expectedDisplayText =
                DEFAULT_TEXT.substring(0, offset) + placeholder + DEFAULT_TEXT.substring(offset);
        if (singleLine) {
            expectedDisplayText = expectedDisplayText.replace('\n', ' ');
        }

        assertThat(layout.getText().toString()).isEqualTo(expectedDisplayText);
        assertGestureInsertModeHighlightRange(offset, offset + placeholder.length());
    }

    private void assertNoInsertModeHighlight() {
        final Canvas canvas = prepareMockCanvas();
        mEditText.draw(canvas);
        verify(canvas, never()).drawRect(anyFloat(), anyFloat(), anyFloat(), anyFloat(), any());
    }

    private void assertNoInsertMode() {
        // There is no API to directly check if the editText is in insert mode.
        // Here we check that 1) no highlight is draw 2) the display text doesn't contain the
        // placeholder text from the insert mode.
        assertNoInsertModeHighlight();

        String expectedDisplayText = mEditText.getText().toString();
        if (mEditText.isSingleLine()) {
            expectedDisplayText = expectedDisplayText.replace('\n', ' ');
        }
        assertThat(mEditText.getLayout().getText().toString()).isEqualTo(expectedDisplayText);
    }

    private void assertGestureInsertModeHighlightRange(int start, int end) {
        final int textColor = mEditText.getTextColors().getDefaultColor();
        final int expectedColor =
                ColorUtils.setAlphaComponent(textColor, (int) (0.2f * Color.alpha(textColor)));

        assertGestureHighlightRange(start, end);
    }

    private void assertCursorOffset(int offset) {
        assertThat(mEditText.getSelectionStart()).isEqualTo(offset);
        assertThat(mEditText.getSelectionEnd()).isEqualTo(offset);
    }

    private void assertFallbackTextInserted(int initialCursorPosition) {
        assertThat(mResult).isEqualTo(InputConnection.HANDWRITING_GESTURE_RESULT_FALLBACK);
        assertThat(mEditText.getText().toString()).isEqualTo(
                DEFAULT_TEXT.substring(0, initialCursorPosition)
                        + FALLBACK_TEXT
                        + DEFAULT_TEXT.substring(initialCursorPosition));
        assertThat(mEditText.getSelectionStart())
                .isEqualTo(initialCursorPosition + FALLBACK_TEXT.length());
        assertThat(mEditText.getSelectionEnd())
                .isEqualTo(initialCursorPosition + FALLBACK_TEXT.length());
    }

    private void assertGestureFailure(int initialCursorPosition) {
        assertThat(mResult).isEqualTo(InputConnection.HANDWRITING_GESTURE_RESULT_FAILED);
        assertNoChange(initialCursorPosition);
    }

    private void assertNoChange(int initialCursorPosition) {
        assertThat(mEditText.getText().toString()).isEqualTo(DEFAULT_TEXT);
        assertThat(mEditText.getSelectionStart()).isEqualTo(initialCursorPosition);
        assertThat(mEditText.getSelectionEnd()).isEqualTo(initialCursorPosition);
    }

    private void assertSelectGesturePreviewHighlightRange(int start, int end) {
        // Selection preview highlight color is the same as selection highlight color.
        assertGestureHighlightRange(start, end, mEditText.getHighlightColor());
    }

    private void assertDeleteGesturePreviewHighlightRange(int start, int end) {
        // Deletion preview highlight color is 20% opacity of the default text color.
        int color = mEditText.getTextColors().getDefaultColor();
        color = ColorUtils.setAlphaComponent(color, (int) (0.2f * Color.alpha(color)));
        assertGestureHighlightRange(start, end, color);
    }

    private void assertGestureHighlightRange(int start, int end) {
        Canvas canvas = prepareMockCanvas();
        mEditText.draw(canvas);

        ArgumentCaptor<Path> pathCaptor = ArgumentCaptor.forClass(Path.class);
        ArgumentCaptor<Paint> paintCaptor = ArgumentCaptor.forClass(Paint.class);
        verify(canvas).drawPath(pathCaptor.capture(), paintCaptor.capture());

        Path expectedPath = new Path();
        mEditText.getLayout().getSelectionPath(start, end, expectedPath);
        assertPathEquals(expectedPath, pathCaptor.getValue());
    }

    private void assertGestureHighlightRange(int start, int end, int color) {
        Canvas canvas = prepareMockCanvas();
        mEditText.draw(canvas);

        ArgumentCaptor<Path> pathCaptor = ArgumentCaptor.forClass(Path.class);
        ArgumentCaptor<Paint> paintCaptor = ArgumentCaptor.forClass(Paint.class);
        verify(canvas).drawPath(pathCaptor.capture(), paintCaptor.capture());

        Path expectedPath = new Path();
        mEditText.getLayout().getSelectionPath(start, end, expectedPath);
        assertPathEquals(expectedPath, pathCaptor.getValue());
        assertThat(paintCaptor.getValue().getColor()).isEqualTo(color);
    }

    private void assertNoHighlight() {
        Canvas canvas = prepareMockCanvas();
        mEditText.draw(canvas);
        verify(canvas, never()).drawPath(any(), any());
    }

    private Canvas prepareMockCanvas() {
        Canvas canvas = mock(Canvas.class);
        when(canvas.getClipBounds(any())).thenAnswer(invocation -> {
            Rect outRect = invocation.getArgument(0);
            outRect.top = 0;
            outRect.left = 0;
            outRect.right = mEditText.getMeasuredWidth();
            outRect.bottom = mEditText.getMeasuredHeight();
            return true;
        });
        return canvas;
    }

    private void assertPathEquals(Path expected, Path actual) {
        Bitmap expectedBitmap = drawToBitmap(expected);
        Bitmap actualBitmap = drawToBitmap(actual);
        assertThat(expectedBitmap.sameAs(actualBitmap)).isTrue();
    }

    private Bitmap drawToBitmap(Path path) {
        RectF boundsRectF = new RectF();
        path.computeBounds(boundsRectF, true);
        Rect boundsRect = new Rect();
        boundsRectF.round(boundsRect);
        Bitmap bitmap = Bitmap.createBitmap(
                boundsRect.width(), boundsRect.height(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setColor(Color.BLACK);
        canvas.drawPath(path, paint);
        return bitmap;
    }
}
