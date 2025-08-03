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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.text.Highlights;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

/**
 * Test {@link TextView}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class TextViewSearchResultHighlightTest {

    private Context mContext;
    private Paint mPaint = new Paint();

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mPaint.setColor(Color.BLACK);
    }

    private void setTextAndMeasure(TextView view, String text) {
        view.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        view.setText(text);
        view.measure(View.MeasureSpec.makeMeasureSpec(1024, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(1024, View.MeasureSpec.AT_MOST));
    }

    private Canvas prepareMockCanvas(TextView textView) {
        Canvas canvas = mock(Canvas.class);
        when(canvas.getClipBounds(any())).thenAnswer(invocation -> {
            Rect outRect = invocation.getArgument(0);
            outRect.top = 0;
            outRect.left = 0;
            outRect.right = textView.getMeasuredWidth();
            outRect.bottom = textView.getMeasuredHeight();
            return true;
        });
        return canvas;
    }

    private Bitmap drawToBitmap(Path path) {
        RectF rF = new RectF();
        path.computeBounds(rF, true);
        Rect r = new Rect();
        rF.round(r);
        Bitmap bmp = Bitmap.createBitmap(r.width(), r.height(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawPath(path, mPaint);
        return bmp;
    }

    private void assertPathEquals(Path expected, Path actual) {
        Bitmap eBmp = drawToBitmap(expected);
        Bitmap aBmp = drawToBitmap(actual);

        assertThat(eBmp.sameAs(aBmp)).isTrue();
    }

    @Test
    public void setAndGetNull() {
        TextView textView = new TextView(mContext);
        textView.setSearchResultHighlights(null);
        assertThat(textView.getSearchResultHighlights()).isNull();
    }

    @Test
    public void setAndGet() {
        TextView textView = new TextView(mContext);
        int[] ranges = new int[] {1, 2, 3, 4};
        textView.setSearchResultHighlights(ranges);
        assertThat(textView.getSearchResultHighlights()).isEqualTo(ranges);
    }

    @Test
    public void setAndGetColor() {
        TextView textView = new TextView(mContext);
        textView.setSearchResultHighlightColor(Color.RED);
        assertThat(textView.getSearchResultHighlightColor()).isEqualTo(Color.RED);
    }

    @Test
    public void setAndGetFocusedColor() {
        TextView textView = new TextView(mContext);
        textView.setFocusedSearchResultHighlightColor(Color.RED);
        assertThat(textView.getFocusedSearchResultHighlightColor()).isEqualTo(Color.RED);
    }

    @Test
    public void setAndGetFocusedIndex() {
        TextView textView = new TextView(mContext);
        textView.setSearchResultHighlights(1, 2, 3, 4);
        textView.setFocusedSearchResultIndex(1);
        assertThat(textView.getFocusedSearchResultIndex()).isEqualTo(1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidArg_oddNumberRange() {
        new TextView(mContext).setSearchResultHighlights(1, 2, 3);
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidArg_reversedRange() {
        new TextView(mContext).setSearchResultHighlights(2, 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidArg_invalidFocusedIndex() {
        new TextView(mContext).setFocusedSearchResultIndex(-2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidArg_OOB_FocusedIndex() {
        TextView textView = new TextView(mContext);
        textView.setSearchResultHighlights(1, 2, 3, 4);
        textView.setFocusedSearchResultIndex(3);
    }

    @Test
    public void drawRect() {
        TextView textView = new TextView(mContext);
        setTextAndMeasure(textView, "Hello, World");


        final Path expectPath = new Path();
        final Paint pathPaint = new Paint();
        pathPaint.setColor(Color.GREEN);

        int[] ranges = new int[] {4, 8};
        textView.setSearchResultHighlightColor(Color.GREEN);
        textView.setSearchResultHighlights(ranges);

        ArgumentCaptor<Path> pathCaptor = ArgumentCaptor.forClass(Path.class);
        ArgumentCaptor<Paint> paintCaptor = ArgumentCaptor.forClass(Paint.class);

        Canvas canvas = prepareMockCanvas(textView);
        textView.draw(canvas);

        verify(canvas, times(1)).drawPath(pathCaptor.capture(), paintCaptor.capture());

        // Expect drawPath.
        textView.getLayout().getSelectionPath(4, 8, expectPath);
        assertPathEquals(expectPath, pathCaptor.getValue());
        assertThat(paintCaptor.getValue().getColor()).isEqualTo(Color.GREEN);
    }

    @Test
    public void drawWithHighlight() {
        TextView textView = new TextView(mContext);
        setTextAndMeasure(textView, "Hello, World");

        final Path expectPath = new Path();
        final Paint pathPaint = new Paint();

        pathPaint.setColor(Color.GREEN);

        Highlights highlights = new Highlights.Builder()
                .addRange(pathPaint, 4, 8)
                .build();
        textView.setHighlights(highlights);
        textView.setSearchResultHighlights(1, 3);
        textView.setSearchResultHighlightColor(Color.BLUE);

        ArgumentCaptor<Path> pathCaptor = ArgumentCaptor.forClass(Path.class);
        ArgumentCaptor<Paint> paintCaptor = ArgumentCaptor.forClass(Paint.class);

        Canvas canvas = prepareMockCanvas(textView);
        textView.draw(canvas);

        verify(canvas, times(2)).drawPath(pathCaptor.capture(), paintCaptor.capture());

        // Verify first highlight.
        textView.getLayout().getSelectionPath(4, 8, expectPath);
        assertPathEquals(expectPath, pathCaptor.getAllValues().get(0));
        assertThat(paintCaptor.getAllValues().get(0).getColor()).isEqualTo(Color.GREEN);

        // Verify second highlight.
        textView.getLayout().getSelectionPath(1, 3, expectPath);
        assertPathEquals(expectPath, pathCaptor.getAllValues().get(1));
        assertThat(paintCaptor.getAllValues().get(1).getColor()).isEqualTo(Color.BLUE);
    }

    @Test
    public void drawWithFocused() {
        TextView textView = new TextView(mContext);
        setTextAndMeasure(textView, "Hello, World");

        final Path expectPath = new Path();

        textView.setSearchResultHighlights(1, 3, 4, 8);
        textView.setSearchResultHighlightColor(Color.BLUE);
        textView.setFocusedSearchResultIndex(0);
        textView.setFocusedSearchResultHighlightColor(Color.GREEN);

        ArgumentCaptor<Path> pathCaptor = ArgumentCaptor.forClass(Path.class);
        ArgumentCaptor<Paint> paintCaptor = ArgumentCaptor.forClass(Paint.class);

        Canvas canvas = prepareMockCanvas(textView);
        textView.draw(canvas);

        verify(canvas, times(2)).drawPath(pathCaptor.capture(), paintCaptor.capture());

        // Verify first highlight.
        textView.getLayout().getSelectionPath(4, 8, expectPath);
        assertPathEquals(expectPath, pathCaptor.getAllValues().get(0));
        assertThat(paintCaptor.getAllValues().get(0).getColor()).isEqualTo(Color.BLUE);

        // Verify second highlight.
        textView.getLayout().getSelectionPath(1, 3, expectPath);
        assertPathEquals(expectPath, pathCaptor.getAllValues().get(1));
        assertThat(paintCaptor.getAllValues().get(1).getColor()).isEqualTo(Color.GREEN);
    }
}
