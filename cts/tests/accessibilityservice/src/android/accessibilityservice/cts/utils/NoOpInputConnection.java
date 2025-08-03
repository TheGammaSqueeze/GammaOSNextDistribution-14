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

package android.accessibilityservice.cts.utils;

import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.CorrectionInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputContentInfo;

/**
 * A no-op implementation of {@link InputConnection}.
 *
 * <p>Useful for mocking/spying.</p>
 */
public class NoOpInputConnection implements InputConnection {

    @Override
    public CharSequence getTextBeforeCursor(int n, int flags) {
        return null;
    }

    @Override
    public CharSequence getTextAfterCursor(int n, int flags) {
        return null;
    }

    @Override
    public CharSequence getSelectedText(int flags) {
        return null;
    }

    @Override
    public int getCursorCapsMode(int reqModes) {
        return 0;
    }

    @Override
    public ExtractedText getExtractedText(ExtractedTextRequest request, int flags) {
        return null;
    }

    @Override
    public boolean deleteSurroundingText(int beforeLength, int afterLength) {
        return false;
    }

    @Override
    public boolean deleteSurroundingTextInCodePoints(int beforeLength, int afterLength) {
        return false;
    }

    @Override
    public boolean setComposingText(CharSequence text, int newCursorPosition) {
        return false;
    }

    @Override
    public boolean setComposingRegion(int start, int end) {
        return false;
    }

    @Override
    public boolean finishComposingText() {
        return false;
    }

    @Override
    public boolean commitText(CharSequence text, int newCursorPosition) {
        return false;
    }

    @Override
    public boolean commitCompletion(CompletionInfo text) {
        return false;
    }

    @Override
    public boolean commitCorrection(CorrectionInfo correctionInfo) {
        return false;
    }

    @Override
    public boolean setSelection(int start, int end) {
        return false;
    }

    @Override
    public boolean performEditorAction(int editorAction) {
        return false;
    }

    @Override
    public boolean performContextMenuAction(int id) {
        return false;
    }

    @Override
    public boolean beginBatchEdit() {
        return false;
    }

    @Override
    public boolean endBatchEdit() {
        return false;
    }

    @Override
    public boolean sendKeyEvent(KeyEvent event) {
        return false;
    }

    @Override
    public boolean clearMetaKeyStates(int states) {
        return false;
    }

    @Override
    public boolean reportFullscreenMode(boolean enabled) {
        return false;
    }

    @Override
    public boolean performPrivateCommand(String action, Bundle data) {
        return false;
    }

    @Override
    public boolean requestCursorUpdates(int cursorUpdateMode) {
        return false;
    }

    @Override
    public boolean requestCursorUpdates(int cursorUpdateMode, int cursorUpdateFilter) {
        return false;
    }

    @Override
    public Handler getHandler() {
        return null;
    }

    @Override
    public void closeConnection() {
    }

    @Override
    public boolean commitContent(InputContentInfo inputContentInfo, int flags, Bundle opts) {
        return false;
    }
}
