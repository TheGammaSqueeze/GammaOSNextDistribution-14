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

package android.text.cts;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.text.BoringLayout;
import android.text.DynamicLayout;
import android.text.Layout;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.style.LineBackgroundSpan;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;

import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class LayoutDrawOrderTest {

    private static final String TEST_STRING = "Hello, World";
    private static final TextPaint PAINT = new TextPaint();
    private static final int CANVAS_WIDTH = 480;
    private static final int CANVAS_HEIGHT = 320;

    private static final class TestPaths {
        public List<Path> highlightPaths;
        public List<Paint> highlightPaints;
        public Path selectionPath;
        public Paint selectionPaint;
    }

    private static Layout getBoringLayout(CharSequence text) {
        BoringLayout.Metrics boring = BoringLayout.isBoring(text, PAINT);
        return BoringLayout.make(text, PAINT, 0, Layout.Alignment.ALIGN_NORMAL,
                1f, 0f, boring, false);
    }

    private static Layout getStaticLayout(CharSequence text) {
        return StaticLayout.Builder.obtain(
                text, 0, text.length(), PAINT, CANVAS_WIDTH).build();
    }

    private static Layout getDynamicLayout(CharSequence text) {
        return DynamicLayout.Builder.obtain(text, PAINT, CANVAS_WIDTH).build();
    }

    private Canvas prepareMockCanvas() {
        Canvas canvas = mock(Canvas.class);
        when(canvas.getClipBounds(any())).thenAnswer(invocation -> {
            Rect outRect = invocation.getArgument(0);
            outRect.top = 0;
            outRect.left = 0;
            outRect.right = CANVAS_WIDTH;
            outRect.bottom = CANVAS_HEIGHT;
            return true;
        });
        return canvas;
    }

    private TestPaths prepareTestCase(Layout layout) {
        TestPaths out = new TestPaths();
        Path highlightPath = new Path();
        layout.getSelectionPath(1, 2, highlightPath);
        out.highlightPaths = List.of(highlightPath);
        out.highlightPaints = List.of(new Paint());
        out.selectionPath = new Path();
        layout.getSelectionPath(2, 3, out.selectionPath);
        out.selectionPaint = new Paint();
        return out;
    }

    @Test
    public void testBoringLayout_testDrawOrder_Selection_on_top_of_Highlight() {
        Canvas canvas = prepareMockCanvas();

        Layout layout = getBoringLayout(TEST_STRING);
        TestPaths testPaths = prepareTestCase(layout);
        layout.draw(canvas, testPaths.highlightPaths, testPaths.highlightPaints,
                testPaths.selectionPath, testPaths.selectionPaint, 0);

        InOrder order = inOrder(canvas);
        order.verify(canvas)
                .drawPath(
                        eq(testPaths.highlightPaths.get(0)),
                        eq(testPaths.highlightPaints.get(0)));
        order.verify(canvas)
                .drawPath(eq(testPaths.selectionPath), eq(testPaths.selectionPaint));
        order.verify(canvas)
                .drawText((CharSequence) any(), anyInt(), anyInt(), anyFloat(), anyFloat(), any());
    }

    @Test
    public void testStaticLayout_testDrawOrder_Selection_on_top_of_Highlight() {
        Canvas canvas = prepareMockCanvas();

        Layout layout = getStaticLayout(TEST_STRING);
        TestPaths testPaths = prepareTestCase(layout);
        layout.draw(canvas, testPaths.highlightPaths, testPaths.highlightPaints,
                testPaths.selectionPath, testPaths.selectionPaint, 0);

        InOrder order = inOrder(canvas);
        order.verify(canvas)
                .drawPath(
                        eq(testPaths.highlightPaths.get(0)),
                        eq(testPaths.highlightPaints.get(0)));
        order.verify(canvas)
                .drawPath(eq(testPaths.selectionPath), eq(testPaths.selectionPaint));
        order.verify(canvas)
                .drawText((CharSequence) any(), anyInt(), anyInt(), anyFloat(), anyFloat(), any());
    }

    @Test
    public void testDynamicLayout_testDrawOrder_Selection_on_top_of_Highlight() {
        Canvas canvas = prepareMockCanvas();

        Layout layout = getDynamicLayout(TEST_STRING);
        TestPaths testPaths = prepareTestCase(layout);
        layout.draw(canvas, testPaths.highlightPaths, testPaths.highlightPaints,
                testPaths.selectionPath, testPaths.selectionPaint, 0);

        InOrder order = inOrder(canvas);
        order.verify(canvas)
                .drawPath(
                        eq(testPaths.highlightPaths.get(0)),
                        eq(testPaths.highlightPaints.get(0)));
        order.verify(canvas)
                .drawPath(eq(testPaths.selectionPath), eq(testPaths.selectionPaint));
        order.verify(canvas)
                .drawText((CharSequence) any(), anyInt(), anyInt(), anyFloat(), anyFloat(), any());
    }

    @Test
    public void testStaticLayout_testDrawOrder_Highlight_on_top_of_Background() {
        Canvas canvas = prepareMockCanvas();

        SpannableString ss = new SpannableString(TEST_STRING);
        LineBackgroundSpan span = mock(LineBackgroundSpan.class);
        ss.setSpan(span, 4, 8, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

        Layout layout = getStaticLayout(ss);
        TestPaths testPaths = prepareTestCase(layout);
        layout.draw(canvas, testPaths.highlightPaths, testPaths.highlightPaints,
                testPaths.selectionPath, testPaths.selectionPaint, 0);

        // Verify the span background should be drawn before the highlight
        InOrder order = inOrder(canvas, span);
        order.verify(span)
                        .drawBackground(
                                eq(canvas), any(),
                                anyInt(), anyInt(), anyInt(), anyInt(), anyInt(),
                                any(),
                                anyInt(), anyInt(), anyInt());
        order.verify(canvas)
                .drawPath(
                        eq(testPaths.highlightPaths.get(0)),
                        eq(testPaths.highlightPaints.get(0)));
        order.verify(canvas)
                .drawTextRun((CharSequence) any(), anyInt(), anyInt(),
                        anyInt(), anyInt(), anyFloat(), anyFloat(), anyBoolean(), any());
    }

    @Test
    public void testDynamicLayout_testDrawOrder_Highlight_on_top_of_Background() {
        Canvas canvas = prepareMockCanvas();

        SpannableString ss = new SpannableString(TEST_STRING);
        LineBackgroundSpan span = mock(LineBackgroundSpan.class);
        ss.setSpan(span, 4, 8, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

        Layout layout = getStaticLayout(ss);
        TestPaths testPaths = prepareTestCase(layout);
        layout.draw(canvas, testPaths.highlightPaths, testPaths.highlightPaints,
                testPaths.selectionPath, testPaths.selectionPaint, 0);

        // Verify the span background should be drawn before the highlight
        InOrder order = inOrder(canvas, span);
        order.verify(span)
                .drawBackground(
                        eq(canvas), any(),
                        anyInt(), anyInt(), anyInt(), anyInt(), anyInt(),
                        any(),
                        anyInt(), anyInt(), anyInt());
        order.verify(canvas)
                .drawPath(
                        eq(testPaths.highlightPaths.get(0)),
                        eq(testPaths.highlightPaints.get(0)));
        order.verify(canvas)
                .drawTextRun((CharSequence) any(), anyInt(), anyInt(),
                        anyInt(), anyInt(), anyFloat(), anyFloat(), anyBoolean(), any());
    }
}
