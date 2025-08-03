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

import static android.view.inputmethod.InputConnection.HANDWRITING_GESTURE_RESULT_UNSUPPORTED;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.Bundle;
import android.os.Handler;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.view.KeyEvent;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.CorrectionInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.HandwritingGesture;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputContentInfo;
import android.view.inputmethod.SurroundingText;
import android.view.inputmethod.cts.util.InputConnectionTestUtils;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.function.IntConsumer;

@SmallTest
@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class InputConnectionDefaultMethodTest {
    @Mock private InputConnection mMockInputConnection;
    @Mock private IntConsumer mMockIntConsumer;
    @Mock private HandwritingGesture mMockHandwritingGesture;
    private TestInputConnection mTestInputConnection;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mTestInputConnection = new TestInputConnection(mMockInputConnection);
    }

    @Test
    public void getSurroundingText_mockInputConnection() {
        // a|test|bc
        when(mMockInputConnection.getTextBeforeCursor(anyInt(), anyInt())).thenReturn("a");
        when(mMockInputConnection.getTextAfterCursor(anyInt(), anyInt())).thenReturn("bc");
        when(mMockInputConnection.getSelectedText(anyInt())).thenReturn("test");

        // a|test|bc
        SurroundingText surroundingText =
                mTestInputConnection.getSurroundingText(3, 2, InputConnection.GET_TEXT_WITH_STYLES);

        assertThat(surroundingText.getText().toString()).isEqualTo("atestbc");
        assertThat(surroundingText.getSelectionStart()).isEqualTo(1);
        assertThat(surroundingText.getSelectionEnd()).isEqualTo(5);
        // Default implementation always treat offset as -1.
        assertThat(surroundingText.getOffset()).isEqualTo(-1);
    }

    @Test
    public void getSurroundingText_baseInputConnection() {
        // 0123|456|789
        final BaseInputConnection baseInputConnection =
                InputConnectionTestUtils.createBaseInputConnectionWithSelection(
                        InputConnectionTestUtils.formatString("0123[456]789"));
        final TestInputConnection testInputConnection =
                new TestInputConnection(baseInputConnection);

        // 123|456|78
        SurroundingText surroundingText =
                testInputConnection.getSurroundingText(3, 2, InputConnection.GET_TEXT_WITH_STYLES);

        assertThat(surroundingText.getText().toString()).isEqualTo("12345678");
        assertThat(surroundingText.getSelectionStart()).isEqualTo(3);
        assertThat(surroundingText.getSelectionEnd()).isEqualTo(6);
        // Default implementation always treat offset as -1.
        assertThat(surroundingText.getOffset()).isEqualTo(-1);
    }

    @Test
    public void setComposingTextWithTextAttribute() {
        mTestInputConnection.setComposingText("abc", 1, null);

        verify(mMockInputConnection).setComposingText(eq("abc"), eq(1));
    }

    @Test
    public void setComposingRegionWithTextAttribute() {
        mTestInputConnection.setComposingRegion(2, 3, null);

        verify(mMockInputConnection).setComposingRegion(eq(2), eq(3));
    }

    @Test
    public void commitTextWithTextAttribute() {
        mTestInputConnection.commitText("text", 1, null);

        verify(mMockInputConnection).commitText(eq("text"), eq(1));
    }

    @Test
    public void performSpellCheck() {
        assertThat(mTestInputConnection.performSpellCheck()).isFalse();
    }

    @Test
    public void performHandwritingGesture() {
        mTestInputConnection.performHandwritingGesture(
                mMockHandwritingGesture, MoreExecutors.directExecutor(), mMockIntConsumer);

        verify(mMockIntConsumer).accept(eq(HANDWRITING_GESTURE_RESULT_UNSUPPORTED));
    }

    @Test
    public void requestCursorUpdates() {
        int[] cursorUpdateFilters =
                new int[] {
                    0,
                    InputConnection.CURSOR_UPDATE_FILTER_EDITOR_BOUNDS,
                    InputConnection.CURSOR_UPDATE_FILTER_CHARACTER_BOUNDS,
                    InputConnection.CURSOR_UPDATE_FILTER_INSERTION_MARKER,
                    InputConnection.CURSOR_UPDATE_FILTER_VISIBLE_LINE_BOUNDS
                };

        when(mMockInputConnection.requestCursorUpdates(anyInt())).thenReturn(true);
        for (int filter : cursorUpdateFilters) {
            assertThat(
                            mTestInputConnection.requestCursorUpdates(
                                    InputConnection.CURSOR_UPDATE_IMMEDIATE, filter))
                    .isEqualTo(filter == 0);
        }
    }

    @Test
    public void setImeConsumesInput() {
        assertThat(mTestInputConnection.setImeConsumesInput(false)).isFalse();
        assertThat(mTestInputConnection.setImeConsumesInput(true)).isFalse();
    }

    @Test
    public void replaceText_mockInputConnection() {
        mTestInputConnection.replaceText(2, 3, "text", 1, null);

        verify(mMockInputConnection).beginBatchEdit();
        verify(mMockInputConnection).finishComposingText();
        verify(mMockInputConnection).setSelection(eq(2), eq(3));
        verify(mMockInputConnection).commitText(eq("text"), eq(1));
        verify(mMockInputConnection).endBatchEdit();
    }

    @Test
    public void replaceText_baseInputConnection() {
        // 0123|456|789
        final BaseInputConnection baseInputConnection =
                InputConnectionTestUtils.createBaseInputConnectionWithSelection(
                        InputConnectionTestUtils.formatString("0123[456]789"));
        final TestInputConnection testInputConnection =
                new TestInputConnection(baseInputConnection);

        // 0123|456|789 -> 01text|3456789
        testInputConnection.replaceText(2, 3, "text", 1, null);

        // 01text|3456789
        assertThat(testInputConnection.getTextBeforeCursor(100, 0).toString()).isEqualTo("01text");
        assertThat(testInputConnection.getSelectedText(0)).isNull();
        assertThat(testInputConnection.getTextAfterCursor(100, 0).toString()).isEqualTo("3456789");
    }

    /**
     * Simple implementation of {@link InputConnection} which delegates most calls to the underling
     * input connection.
     */
    private static class TestInputConnection implements InputConnection {
        private InputConnection mDelegate;

        TestInputConnection(InputConnection delegate) {
            mDelegate = delegate;
        }

        @Override
        public CharSequence getTextBeforeCursor(int n, int flags) {
            return mDelegate.getTextBeforeCursor(n, flags);
        }

        @Override
        public CharSequence getTextAfterCursor(int n, int flags) {
            return mDelegate.getTextAfterCursor(n, flags);
        }

        @Override
        public CharSequence getSelectedText(int flags) {
            return mDelegate.getSelectedText(flags);
        }

        @Override
        public int getCursorCapsMode(int reqModes) {
            return mDelegate.getCursorCapsMode(reqModes);
        }

        @Override
        public ExtractedText getExtractedText(ExtractedTextRequest request, int flags) {
            return mDelegate.getExtractedText(request, flags);
        }

        @Override
        public boolean deleteSurroundingText(int beforeLength, int afterLength) {
            return mDelegate.deleteSurroundingText(beforeLength, afterLength);
        }

        @Override
        public boolean deleteSurroundingTextInCodePoints(int beforeLength, int afterLength) {
            return mDelegate.deleteSurroundingTextInCodePoints(beforeLength, afterLength);
        }

        @Override
        public boolean setComposingText(CharSequence text, int newCursorPosition) {
            return mDelegate.setComposingText(text, newCursorPosition);
        }

        @Override
        public boolean setComposingRegion(int start, int end) {
            return mDelegate.setComposingRegion(start, end);
        }

        @Override
        public boolean finishComposingText() {
            return mDelegate.finishComposingText();
        }

        @Override
        public boolean commitText(CharSequence text, int newCursorPosition) {
            return mDelegate.commitText(text, newCursorPosition);
        }

        @Override
        public boolean commitCompletion(CompletionInfo text) {
            return mDelegate.commitCompletion(text);
        }

        @Override
        public boolean commitCorrection(CorrectionInfo correctionInfo) {
            return mDelegate.commitCorrection(correctionInfo);
        }

        @Override
        public boolean setSelection(int start, int end) {
            return mDelegate.setSelection(start, end);
        }

        @Override
        public boolean performEditorAction(int editorAction) {
            return mDelegate.performEditorAction(editorAction);
        }

        @Override
        public boolean performContextMenuAction(int id) {
            return mDelegate.performContextMenuAction(id);
        }

        @Override
        public boolean beginBatchEdit() {
            return mDelegate.beginBatchEdit();
        }

        @Override
        public boolean endBatchEdit() {
            return mDelegate.endBatchEdit();
        }

        @Override
        public boolean sendKeyEvent(KeyEvent event) {
            return mDelegate.sendKeyEvent(event);
        }

        @Override
        public boolean clearMetaKeyStates(int states) {
            return mDelegate.clearMetaKeyStates(states);
        }

        @Override
        public boolean reportFullscreenMode(boolean enabled) {
            return mDelegate.reportFullscreenMode(enabled);
        }

        @Override
        public boolean performPrivateCommand(String action, Bundle data) {
            return mDelegate.performPrivateCommand(action, data);
        }

        @Override
        public boolean requestCursorUpdates(int cursorUpdateMode) {
            return mDelegate.requestCursorUpdates(cursorUpdateMode);
        }

        @Override
        public Handler getHandler() {
            return mDelegate.getHandler();
        }

        @Override
        public void closeConnection() {
            mDelegate.closeConnection();
        }

        @Override
        public boolean commitContent(InputContentInfo inputContentInfo, int flags, Bundle opts) {
            return mDelegate.commitContent(inputContentInfo, flags, opts);
        }
    }
}
