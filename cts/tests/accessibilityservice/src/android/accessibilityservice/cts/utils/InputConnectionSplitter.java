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
import android.view.inputmethod.InputConnectionWrapper;
import android.view.inputmethod.InputContentInfo;
import android.view.inputmethod.SurroundingText;
import android.view.inputmethod.TextAttribute;

/**
 * A special version of {@link InputConnectionWrapper} so that you can forward method invocations
 * to another {@link InputConnection}.
 *
 * <p>Useful for mocking/spying.</p>
 */
public final class InputConnectionSplitter extends InputConnectionWrapper {
    private final InputConnection mForNotify;

    public InputConnectionSplitter(InputConnection target, InputConnection forNotify) {
        super(target, false);
        mForNotify = forNotify;
    }

    @Override
    public CharSequence getTextBeforeCursor(int n, int flags) {
        mForNotify.getTextBeforeCursor(n, flags);
        return super.getTextBeforeCursor(n, flags);
    }

    @Override
    public CharSequence getTextAfterCursor(int n, int flags) {
        mForNotify.getTextBeforeCursor(n, flags);
        return super.getTextBeforeCursor(n, flags);
    }

    @Override
    public CharSequence getSelectedText(int flags) {
        mForNotify.getSelectedText(flags);
        return super.getSelectedText(flags);
    }

    @Override
    public SurroundingText getSurroundingText(int beforeLength, int afterLength, int flags) {
        mForNotify.getSurroundingText(beforeLength, afterLength, flags);
        return super.getSurroundingText(beforeLength, afterLength, flags);
    }

    @Override
    public int getCursorCapsMode(int reqModes) {
        mForNotify.getCursorCapsMode(reqModes);
        return super.getCursorCapsMode(reqModes);
    }

    @Override
    public ExtractedText getExtractedText(ExtractedTextRequest request, int flags) {
        mForNotify.getExtractedText(request, flags);
        return super.getExtractedText(request, flags);
    }

    @Override
    public boolean deleteSurroundingTextInCodePoints(int beforeLength, int afterLength) {
        mForNotify.deleteSurroundingTextInCodePoints(beforeLength, afterLength);
        return super.deleteSurroundingTextInCodePoints(beforeLength, afterLength);
    }

    @Override
    public boolean deleteSurroundingText(int beforeLength, int afterLength) {
        mForNotify.deleteSurroundingText(beforeLength, afterLength);
        return super.deleteSurroundingText(beforeLength, afterLength);
    }

    @Override
    public boolean setComposingText(CharSequence text, int newCursorPosition) {
        mForNotify.setComposingText(text, newCursorPosition);
        return super.setComposingText(text, newCursorPosition);
    }

    @Override
    public boolean setComposingText(CharSequence text,
            int newCursorPosition, TextAttribute textAttribute) {
        mForNotify.setComposingText(text, newCursorPosition, textAttribute);
        return super.setComposingText(text, newCursorPosition, textAttribute);
    }

    @Override
    public boolean setComposingRegion(int start, int end) {
        mForNotify.setComposingRegion(start, end);
        return super.setComposingRegion(start, end);
    }

    @Override
    public boolean setComposingRegion(int start, int end, TextAttribute textAttribute) {
        mForNotify.setComposingRegion(start, end, textAttribute);
        return super.setComposingRegion(start, end, textAttribute);
    }

    @Override
    public boolean finishComposingText() {
        mForNotify.finishComposingText();
        return super.finishComposingText();
    }

    @Override
    public boolean commitText(CharSequence text, int newCursorPosition) {
        mForNotify.commitText(text, newCursorPosition);
        return super.commitText(text, newCursorPosition);
    }

    @Override
    public boolean commitText(CharSequence text, int newCursorPosition,
            TextAttribute textAttribute) {
        mForNotify.commitText(text, newCursorPosition, textAttribute);
        return super.commitText(text, newCursorPosition, textAttribute);
    }

    @Override
    public boolean commitCompletion(CompletionInfo text) {
        mForNotify.commitCompletion(text);
        return super.commitCompletion(text);
    }

    @Override
    public boolean commitCorrection(CorrectionInfo correctionInfo) {
        mForNotify.commitCorrection(correctionInfo);
        return super.commitCorrection(correctionInfo);
    }

    @Override
    public boolean setSelection(int start, int end) {
        mForNotify.setSelection(start, end);
        return super.setSelection(start, end);
    }

    @Override
    public boolean performEditorAction(int editorAction) {
        mForNotify.performEditorAction(editorAction);
        return super.performEditorAction(editorAction);
    }

    @Override
    public boolean performContextMenuAction(int id) {
        mForNotify.performContextMenuAction(id);
        return super.performContextMenuAction(id);
    }

    @Override
    public boolean beginBatchEdit() {
        mForNotify.beginBatchEdit();
        return super.beginBatchEdit();
    }

    @Override
    public boolean endBatchEdit() {
        mForNotify.endBatchEdit();
        return super.endBatchEdit();
    }

    @Override
    public boolean sendKeyEvent(KeyEvent event) {
        mForNotify.sendKeyEvent(event);
        return super.sendKeyEvent(event);

    }

    @Override
    public boolean clearMetaKeyStates(int states) {
        mForNotify.clearMetaKeyStates(states);
        return super.clearMetaKeyStates(states);
    }

    @Override
    public boolean reportFullscreenMode(boolean enabled) {
        mForNotify.reportFullscreenMode(enabled);
        return super.reportFullscreenMode(enabled);
    }

    @Override
    public boolean performSpellCheck() {
        mForNotify.performSpellCheck();
        return super.performSpellCheck();
    }

    @Override
    public boolean performPrivateCommand(String action, Bundle data) {
        mForNotify.performPrivateCommand(action, data);
        return super.performPrivateCommand(action, data);
    }

    @Override
    public boolean requestCursorUpdates(int cursorUpdateMode) {
        mForNotify.requestCursorUpdates(cursorUpdateMode);
        return super.requestCursorUpdates(cursorUpdateMode);
    }

    @Override
    public Handler getHandler() {
        mForNotify.getHandler();
        return super.getHandler();
    }

    @Override
    public void closeConnection() {
        mForNotify.closeConnection();
        super.closeConnection();
    }

    @Override
    public boolean commitContent(InputContentInfo inputContentInfo, int flags, Bundle opts) {
        mForNotify.commitContent(inputContentInfo, flags, opts);
        return super.commitContent(inputContentInfo, flags, opts);
    }

    @Override
    public boolean setImeConsumesInput(boolean imeConsumesInput) {
        mForNotify.setImeConsumesInput(imeConsumesInput);
        return super.setImeConsumesInput(imeConsumesInput);
    }
}
