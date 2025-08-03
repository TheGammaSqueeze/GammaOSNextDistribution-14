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

package android.widget.cts;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.test.annotation.UiThreadTest;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Test {@link TextView}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class TextViewStyleShortcutTest {
    private static final int META_CTRL = KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON;

    private static class Range implements Comparable<Range> {
        public final int start;
        public final int end;

        Range(int start, int end) {
            this.start = start;
            this.end = end;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Range range = (Range) o;
            return start == range.start && end == range.end;
        }

        @Override
        public int hashCode() {
            return Objects.hash(start, end);
        }

        @Override
        public String toString() {
            return "(" + start + ", " + end + ')';
        }

        @Override
        public int compareTo(Range o) {
            if (start == o.start) {
                if (end == o.end) {
                    return 0;
                } else {
                    return o.end > end ? 1 : -1;
                }
            } else {
                return o.start > start ? 1 : -1;
            }
        }
    }

    private static void sendCtrlB(View view) {
        view.onKeyShortcut(KeyEvent.KEYCODE_B, new KeyEvent(
                SystemClock.uptimeMillis(),
                SystemClock.uptimeMillis(),
                KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_B,
                0,  // key repeat
                META_CTRL));
    }

    private static void sendCtrlI(View view) {
        view.onKeyShortcut(KeyEvent.KEYCODE_I, new KeyEvent(
                SystemClock.uptimeMillis(),
                SystemClock.uptimeMillis(),
                KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_I,
                0,  // key repeat
                META_CTRL));
    }

    private static void sendCtrlU(View view) {
        view.onKeyShortcut(KeyEvent.KEYCODE_U, new KeyEvent(
                SystemClock.uptimeMillis(),
                SystemClock.uptimeMillis(),
                KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_U,
                0,  // key repeat
                META_CTRL));
    }

    private void assertBoldRegion(EditText et, Range... expectedRanges) {
        Spanned spanned = et.getText();
        Arrays.sort(expectedRanges);

        List<Range> actualList = new ArrayList<>();
        StyleSpan[] spans = spanned.getSpans(0, spanned.length(), StyleSpan.class);
        for (int i = 0; i < spans.length; ++i) {
            final StyleSpan span = spans[i];
            if ((span.getStyle() & Typeface.BOLD) == Typeface.BOLD) {
                actualList.add(new Range(spanned.getSpanStart(span), spanned.getSpanEnd(span)));
            }
        }
        Range[] actualRanges = actualList.toArray(new Range[0]);
        Arrays.sort(actualRanges);

        assertThat(actualRanges).isEqualTo(expectedRanges);
    }

    private void assertItalicRegion(EditText et, Range... expectedRanges) {
        Spanned spanned = et.getText();
        Arrays.sort(expectedRanges);

        List<Range> actualList = new ArrayList<>();
        StyleSpan[] spans = spanned.getSpans(0, spanned.length(), StyleSpan.class);
        for (int i = 0; i < spans.length; ++i) {
            final StyleSpan span = spans[i];
            if ((span.getStyle() & Typeface.ITALIC) == Typeface.ITALIC) {
                actualList.add(new Range(spanned.getSpanStart(span), spanned.getSpanEnd(span)));
            }
        }
        Range[] actualRanges = actualList.toArray(new Range[0]);
        Arrays.sort(actualRanges);

        assertThat(actualRanges).isEqualTo(expectedRanges);
    }

    private void assertUnderlineRegion(EditText et, Range... expectedRanges) {
        Spanned spanned = et.getText();
        Arrays.sort(expectedRanges);

        UnderlineSpan[] spans = spanned.getSpans(0, spanned.length(), UnderlineSpan.class);
        Range[] actualRanges = new Range[spans.length];
        for (int i = 0; i < spans.length; ++i) {
            final UnderlineSpan span = spans[i];
            actualRanges[i] = new Range(spanned.getSpanStart(span), spanned.getSpanEnd(span));

        }
        Arrays.sort(actualRanges);

        assertThat(actualRanges).isEqualTo(expectedRanges);
    }

    @UiThreadTest
    @Test
    public void testStyleShortcutEnabled() {
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        EditText et = new EditText(ctx);
        et.setText("0123456789", TextView.BufferType.EDITABLE);

        et.setSelection(1, 9);

        // By default, the style shortcut is disabled.
        sendCtrlB(et);
        assertBoldRegion(et);  // no bold region.

        // By calling setStyleShortcutEnabled true, the style shortcut should work.
        et.setStyleShortcutsEnabled(true);
        assertThat(et.isStyleShortcutEnabled()).isTrue();
        sendCtrlB(et);
        assertBoldRegion(et, new Range(1, 9));

        // By calling setStyleShortcutEnabled false, the style shortcut should stop working.
        et.setStyleShortcutsEnabled(false);
        assertThat(et.isStyleShortcutEnabled()).isFalse();
        sendCtrlB(et);
        assertBoldRegion(et, new Range(1, 9));
    }

    @UiThreadTest
    @Test
    public void testBold() {
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        EditText et = new EditText(ctx);
        et.setStyleShortcutsEnabled(true);
        et.setText("0123456789", TextView.BufferType.EDITABLE);

        // Test 1: select (1, 0) and make it bold.
        et.setSelection(1, 9);
        sendCtrlB(et);
        assertBoldRegion(et, new Range(1, 9));

        // Test 2: send Ctrl+B again for toggling bold style.
        sendCtrlB(et);
        assertBoldRegion(et);  // no bold region.
    }

    @UiThreadTest
    @Test
    public void testBoldOnlySelectedRegion() {
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        EditText et = new EditText(ctx);
        et.setStyleShortcutsEnabled(true);
        et.setText("0123456789", TextView.BufferType.EDITABLE);

        // Prepare: select (1, 4) and make it bold.
        et.setSelection(1, 4);
        sendCtrlB(et);
        assertBoldRegion(et, new Range(1, 4));

        // Test 1: select (7, 10) and make it bold. The (1, 4) style should be remained bold.
        et.setSelection(7, 10);
        sendCtrlB(et);
        assertBoldRegion(et, new Range(1, 4), new Range(7, 10));

        // Test 2: send Ctrl+B again for togging bold style. The (1, 4) style should be remained
        // bold.
        sendCtrlB(et);
        assertBoldRegion(et, new Range(1, 4));
    }

    @UiThreadTest
    @Test
    public void testBoldSelectIntersectedRegion() {
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        EditText et = new EditText(ctx);
        et.setStyleShortcutsEnabled(true);
        et.setText("0123456789", TextView.BufferType.EDITABLE);

        // Prepare: select (1, 5) and make it bold.
        et.setSelection(1, 5);
        sendCtrlB(et);
        assertBoldRegion(et, new Range(1, 5));

        // Test 1: select (3, 9) and make it bold.
        // The resulting bold region should be (1, 5) and (3, 9). (effective region is (1, 9))
        et.setSelection(3, 9);
        sendCtrlB(et);
        assertBoldRegion(et, new Range(1, 5), new Range(3, 9));

        // Test 2: send Ctrl+B again for togging bold style.
        // The (1, 5) style should be remained bold but changed to (1, 3) because (3, 9) region is
        // un-bolded.
        sendCtrlB(et);
        assertBoldRegion(et, new Range(1, 3));
    }

    @UiThreadTest
    @Test
    public void testBoldSelectCoveredRegion() {
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        EditText et = new EditText(ctx);
        et.setStyleShortcutsEnabled(true);
        et.setText("0123456789", TextView.BufferType.EDITABLE);

        // Prepare: select (3, 5) and make it bold.
        et.setSelection(3, 5);
        sendCtrlB(et);
        assertBoldRegion(et, new Range(3, 5));

        // Test 1: select (1, 9) and make it bold.
        // The resulting bold region should be (1, 9) and (3, 5). (effective region is (1, 9))
        et.setSelection(1, 9);
        sendCtrlB(et);
        assertBoldRegion(et, new Range(3, 5), new Range(1, 9));

        // Test 2: send Ctrl+B again for togging bold style.
        // The previous region (3, 5) was covered with selection (1, 9), so entire bold region
        // should be removed.
        sendCtrlB(et);
        assertBoldRegion(et);  // no bold region
    }

    @UiThreadTest
    @Test
    public void testBoldSelectSubRegion() {
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        EditText et = new EditText(ctx);
        et.setStyleShortcutsEnabled(true);
        et.setText("0123456789", TextView.BufferType.EDITABLE);

        // Prepare: select (1, 9) and make it bold.
        et.setSelection(1, 9);
        sendCtrlB(et);
        assertBoldRegion(et, new Range(1, 9));

        // Test 1: select (3, 5) and make it bold.
        // The selection region (3, 5) is entire bold. So, Ctrl+B will remove the bold style.
        // The range (3,5) splits the original bold region (1, 9) into (1, 3) and (5, 9).
        et.setSelection(3, 5);
        sendCtrlB(et);
        assertBoldRegion(et, new Range(1, 3), new Range(5, 9));
    }

    @UiThreadTest
    @Test
    public void testItalic() {
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        EditText et = new EditText(ctx);
        et.setStyleShortcutsEnabled(true);
        et.setText("0123456789", TextView.BufferType.EDITABLE);

        // Test 1: select (1, 0) and make it italic.
        et.setSelection(1, 9);
        sendCtrlI(et);
        assertItalicRegion(et, new Range(1, 9));

        // Test 2: send Ctrl+B again for toggling italic style.
        sendCtrlI(et);
        assertItalicRegion(et);  // no italic region.
    }

    @UiThreadTest
    @Test
    public void testItalicOnlySelectedRegion() {
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        EditText et = new EditText(ctx);
        et.setStyleShortcutsEnabled(true);
        et.setText("0123456789", TextView.BufferType.EDITABLE);

        // Prepare: select (1, 4) and make it italic.
        et.setSelection(1, 4);
        sendCtrlI(et);
        assertItalicRegion(et, new Range(1, 4));

        // Test 1: select (7, 10) and make it italic. The (1, 4) style should be remained italic.
        et.setSelection(7, 10);
        sendCtrlI(et);
        assertItalicRegion(et, new Range(1, 4), new Range(7, 10));

        // Test 2: send Ctrl+B again for togging italic style. The (1, 4) style should be remained
        // italic.
        sendCtrlI(et);
        assertItalicRegion(et, new Range(1, 4));
    }

    @UiThreadTest
    @Test
    public void testItalicSelectIntersectedRegion() {
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        EditText et = new EditText(ctx);
        et.setStyleShortcutsEnabled(true);
        et.setText("0123456789", TextView.BufferType.EDITABLE);

        // Prepare: select (1, 5) and make it italic.
        et.setSelection(1, 5);
        sendCtrlI(et);
        assertItalicRegion(et, new Range(1, 5));

        // Test 1: select (3, 9) and make it italic.
        // The resulting italic region should be (1, 5) and (3, 9). (effective region is (1, 9))
        et.setSelection(3, 9);
        sendCtrlI(et);
        assertItalicRegion(et, new Range(1, 5), new Range(3, 9));

        // Test 2: send Ctrl+B again for togging italic style.
        // The (1, 5) style should be remained italic but changed to (1, 3) because (3, 9) region is
        // un-italiced.
        sendCtrlI(et);
        assertItalicRegion(et, new Range(1, 3));
    }

    @UiThreadTest
    @Test
    public void testItalicSelectCoveredRegion() {
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        EditText et = new EditText(ctx);
        et.setStyleShortcutsEnabled(true);
        et.setText("0123456789", TextView.BufferType.EDITABLE);

        // Prepare: select (3, 5) and make it italic.
        et.setSelection(3, 5);
        sendCtrlI(et);
        assertItalicRegion(et, new Range(3, 5));

        // Test 1: select (1, 9) and make it italic.
        // The resulting italic region should be (1, 9) and (3, 5). (effective region is (1, 9))
        et.setSelection(1, 9);
        sendCtrlI(et);
        assertItalicRegion(et, new Range(3, 5), new Range(1, 9));

        // Test 2: send Ctrl+B again for togging italic style.
        // The previous region (3, 5) was covered with selection (1, 9), so entire italic region
        // should be removed.
        sendCtrlI(et);
        assertItalicRegion(et);  // no italic region
    }

    @UiThreadTest
    @Test
    public void testItalicSelectSubRegion() {
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        EditText et = new EditText(ctx);
        et.setStyleShortcutsEnabled(true);
        et.setText("0123456789", TextView.BufferType.EDITABLE);

        // Prepare: select (1, 9) and make it italic.
        et.setSelection(1, 9);
        sendCtrlI(et);
        assertItalicRegion(et, new Range(1, 9));

        // Test 1: select (3, 5) and make it italic.
        // The selection region (3, 5) is entire italic. So, Ctrl+B will remove the italic style.
        // The range (3,5) splits the original italic region (1, 9) into (1, 3) and (5, 9).
        et.setSelection(3, 5);
        sendCtrlI(et);
        assertItalicRegion(et, new Range(1, 3), new Range(5, 9));
    }

    @UiThreadTest
    @Test
    public void testUnderline() {
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        EditText et = new EditText(ctx);
        et.setStyleShortcutsEnabled(true);
        et.setText("0123456789", TextView.BufferType.EDITABLE);

        // Test 1: select (1, 0) and make it underline.
        et.setSelection(1, 9);
        sendCtrlU(et);
        assertUnderlineRegion(et, new Range(1, 9));

        // Test 2: send Ctrl+B again for toggling underline style.
        sendCtrlU(et);
        assertUnderlineRegion(et);  // no underline region.
    }

    @UiThreadTest
    @Test
    public void testUnderlineOnlySelectedRegion() {
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        EditText et = new EditText(ctx);
        et.setStyleShortcutsEnabled(true);
        et.setText("0123456789", TextView.BufferType.EDITABLE);

        // Prepare: select (1, 4) and make it underline.
        et.setSelection(1, 4);
        sendCtrlU(et);
        assertUnderlineRegion(et, new Range(1, 4));

        // Test 1: select (7, 10) and make it underline. The (1, 4) style should be remained
        // underline.
        et.setSelection(7, 10);
        sendCtrlU(et);
        assertUnderlineRegion(et, new Range(1, 4), new Range(7, 10));

        // Test 2: send Ctrl+B again for togging underline style. The (1, 4) style should be
        // remained underline.
        sendCtrlU(et);
        assertUnderlineRegion(et, new Range(1, 4));
    }

    @UiThreadTest
    @Test
    public void testUnderlineSelectIntersectedRegion() {
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        EditText et = new EditText(ctx);
        et.setStyleShortcutsEnabled(true);
        et.setText("0123456789", TextView.BufferType.EDITABLE);

        // Prepare: select (1, 5) and make it underline.
        et.setSelection(1, 5);
        sendCtrlU(et);
        assertUnderlineRegion(et, new Range(1, 5));

        // Test 1: select (3, 9) and make it underline.
        // The resulting underline region should be (1, 5) and (3, 9). (effective region is (1, 9))
        et.setSelection(3, 9);
        sendCtrlU(et);
        assertUnderlineRegion(et, new Range(1, 5), new Range(3, 9));

        // Test 2: send Ctrl+B again for togging underline style.
        // The (1, 5) style should be remained underline but changed to (1, 3) because (3, 9) region
        // is un-underlineed.
        sendCtrlU(et);
        assertUnderlineRegion(et, new Range(1, 3));
    }

    @UiThreadTest
    @Test
    public void testUnderlineSelectCoveredRegion() {
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        EditText et = new EditText(ctx);
        et.setStyleShortcutsEnabled(true);
        et.setText("0123456789", TextView.BufferType.EDITABLE);

        // Prepare: select (3, 5) and make it underline.
        et.setSelection(3, 5);
        sendCtrlU(et);
        assertUnderlineRegion(et, new Range(3, 5));

        // Test 1: select (1, 9) and make it underline.
        // The resulting underline region should be (1, 9) and (3, 5). (effective region is (1, 9))
        et.setSelection(1, 9);
        sendCtrlU(et);
        assertUnderlineRegion(et, new Range(3, 5), new Range(1, 9));

        // Test 2: send Ctrl+B again for togging underline style.
        // The previous region (3, 5) was covered with selection (1, 9), so entire underline region
        // should be removed.
        sendCtrlU(et);
        assertUnderlineRegion(et);  // no underline region
    }

    @UiThreadTest
    @Test
    public void testUnderlineSelectSubRegion() {
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        EditText et = new EditText(ctx);
        et.setStyleShortcutsEnabled(true);
        et.setText("0123456789", TextView.BufferType.EDITABLE);

        // Prepare: select (1, 9) and make it underline.
        et.setSelection(1, 9);
        sendCtrlU(et);
        assertUnderlineRegion(et, new Range(1, 9));

        // Test 1: select (3, 5) and make it underline.
        // The selection region (3, 5) is entire underline. So, Ctrl+B will remove the underline
        // style.
        // The range (3,5) splits the original underline region (1, 9) into (1, 3) and (5, 9).
        et.setSelection(3, 5);
        sendCtrlU(et);
        assertUnderlineRegion(et, new Range(1, 3), new Range(5, 9));
    }

    @UiThreadTest
    @Test
    public void testBoldItalicSelectBoldIntersectedRegion() {
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        EditText et = new EditText(ctx);
        et.setStyleShortcutsEnabled(true);
        et.setText("0123456789", TextView.BufferType.EDITABLE);

        // Prepare: Make (1, 5) BOLD_ITALIC.
        et.getText().setSpan(
                new StyleSpan(Typeface.BOLD_ITALIC), 1, 5, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        // Test 1: select (3, 9) and make it bold.
        // The resulting bold/italic region should be (1, 5) and (3, 9).
        // (effective region is (1, 9))
        et.setSelection(3, 9);
        sendCtrlB(et);
        assertBoldRegion(et, new Range(1, 5), new Range(3, 9));
        assertItalicRegion(et, new Range(1, 5));

        // Test 2: send Ctrl+B again for togging bold style.
        // The (1, 5) style should be remained bold italic but changed to (1, 3) because (3, 9)
        // region is un-bold. The remaining (3, 5) region shold remain italic.
        sendCtrlB(et);
        assertBoldRegion(et, new Range(1, 3));
        assertItalicRegion(et, new Range(1, 3), new Range(3, 5));
    }

    @UiThreadTest
    @Test
    public void testBoldItalicSelectBoldCoveredRegion() {
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        EditText et = new EditText(ctx);
        et.setStyleShortcutsEnabled(true);
        et.setText("0123456789", TextView.BufferType.EDITABLE);

        // Prepare: select (3, 5) and make it BOLD_ITALIC.
        et.getText().setSpan(
                new StyleSpan(Typeface.BOLD_ITALIC), 3, 5, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);


        // Test 1: select (1, 9) and make it bold.
        // The resulting bold region should be (1, 9) and (3, 5). (effective region is (1, 9))
        et.setSelection(1, 9);
        sendCtrlB(et);
        assertBoldRegion(et, new Range(3, 5), new Range(1, 9));
        assertItalicRegion(et, new Range(3, 5));

        // Test 2: send Ctrl+B again for togging bold style.
        // The previous region (3, 5) was covered with selection (1, 9), so entire bold region
        // should be removed. The italic region should be remained.
        sendCtrlB(et);
        assertBoldRegion(et);  // no bold region
        assertItalicRegion(et, new Range(3, 5));
    }

    @UiThreadTest
    @Test
    public void testBoldItalicSelectBoldSubRegion() {
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        EditText et = new EditText(ctx);
        et.setStyleShortcutsEnabled(true);
        et.setText("0123456789", TextView.BufferType.EDITABLE);

        // Prepare: select (1, 9) and make it bold.
        et.getText().setSpan(
                new StyleSpan(Typeface.BOLD_ITALIC), 1, 9, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);


        // Test 1: select (3, 5) and make it bold.
        // The selection region (3, 5) is entire bold. So, Ctrl+B will remove the bold style.
        // The range (3,5) splits the original bold region (1, 9) into (1, 3) and (5, 9).
        // The italic region effectively remains same.
        et.setSelection(3, 5);
        sendCtrlB(et);
        assertBoldRegion(et, new Range(1, 3), new Range(5, 9));
        assertItalicRegion(et, new Range(1, 3), new Range(3, 5), new Range(5, 9));
    }

    @UiThreadTest
    @Test
    public void testBoldItalicSelectItalicIntersectedRegion() {
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        EditText et = new EditText(ctx);
        et.setStyleShortcutsEnabled(true);
        et.setText("0123456789", TextView.BufferType.EDITABLE);

        // Prepare: Make (1, 5) BOLD_ITALIC.
        et.getText().setSpan(
                new StyleSpan(Typeface.BOLD_ITALIC), 1, 5, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        // Test 1: select (3, 9) and make it italic.
        // The resulting bold/italic region should be (1, 5) and (3, 9).
        // (effective region is (1, 9))
        et.setSelection(3, 9);
        sendCtrlI(et);
        assertBoldRegion(et, new Range(1, 5));
        assertItalicRegion(et, new Range(1, 5), new Range(3, 9));

        // Test 2: send Ctrl+B again for togging italic style.
        // The (1, 5) style should be remained italic italic but changed to (1, 3) because (3, 9)
        // region is un-italic. The remaining (3, 5) region should remain italic.
        sendCtrlI(et);
        assertItalicRegion(et, new Range(1, 3));
        assertBoldRegion(et, new Range(1, 3), new Range(3, 5));
    }

    @UiThreadTest
    @Test
    public void testBoldItalicSelectItalicCoveredRegion() {
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        EditText et = new EditText(ctx);
        et.setStyleShortcutsEnabled(true);
        et.setText("0123456789", TextView.BufferType.EDITABLE);

        // Prepare: select (3, 5) and make it BOLD_ITALIC.
        et.getText().setSpan(
                new StyleSpan(Typeface.BOLD_ITALIC), 3, 5, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);


        // Test 1: select (1, 9) and make it italic.
        // The resulting italic region should be (1, 9) and (3, 5). (effective region is (1, 9))
        et.setSelection(1, 9);
        sendCtrlI(et);
        assertBoldRegion(et, new Range(3, 5));
        assertItalicRegion(et, new Range(3, 5), new Range(1, 9));

        // Test 2: send Ctrl+B again for togging italic style.
        // The previous region (3, 5) was covered with selection (1, 9), so entire italic region
        // should be removed. The italic region should be remained.
        sendCtrlI(et);
        assertBoldRegion(et, new Range(3, 5));
        assertItalicRegion(et);  // no italic region
    }

    @UiThreadTest
    @Test
    public void testBoldItalicSelectItalicSubRegion() {
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        EditText et = new EditText(ctx);
        et.setStyleShortcutsEnabled(true);
        et.setText("0123456789", TextView.BufferType.EDITABLE);

        // Prepare: select (1, 9) and make it BOLD_ITALIC.
        et.getText().setSpan(
                new StyleSpan(Typeface.BOLD_ITALIC), 1, 9, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);


        // Test 1: select (3, 5) and make it italic.
        // The selection region (3, 5) is entire italic. So, Ctrl+B will remove the italic style.
        // The range (3,5) splits the original italic region (1, 9) into (1, 3) and (5, 9).
        // The italic region effectively remains same.
        et.setSelection(3, 5);
        sendCtrlI(et);
        assertBoldRegion(et, new Range(1, 3), new Range(3, 5), new Range(5, 9));
        assertItalicRegion(et, new Range(1, 3), new Range(5, 9));
    }
}
