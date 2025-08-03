/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.cts.inputmethod;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;

import androidx.annotation.AnyThread;

/**
 * A utility class to test compatibility with legacy apps that were built with old SDKs.
 */
public final class LegacyImeClientTestUtils {

    /**
     * Not intented to be instantiated.
     */
    private LegacyImeClientTestUtils() {
    }

    private static final class MinimallyImplementedNoOpInputConnection implements InputConnection {
        /**
         * {@inheritDoc}
         */
        @Override
        public CharSequence getTextBeforeCursor(int n, int flags) {
            return null;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public CharSequence getTextAfterCursor(int n, int flags) {
            return null;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getCursorCapsMode(int reqModes) {
            return 0;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public ExtractedText getExtractedText(ExtractedTextRequest request, int flags) {
            return null;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean deleteSurroundingText(int beforeLength, int afterLength) {
            return false;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean setComposingText(CharSequence text, int newCursorPosition) {
            return false;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean finishComposingText() {
            return false;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean commitText(CharSequence text, int newCursorPosition) {
            return false;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean commitCompletion(CompletionInfo text) {
            return false;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean setSelection(int start, int end) {
            return false;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean performEditorAction(int editorAction) {
            return false;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean performContextMenuAction(int id) {
            return false;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean beginBatchEdit() {
            return false;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean endBatchEdit() {
            return false;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean sendKeyEvent(KeyEvent event) {
            return false;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean clearMetaKeyStates(int states) {
            return false;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean reportFullscreenMode(boolean enabled) {
            return false;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean performPrivateCommand(String action, Bundle data) {
            return false;
        }
    }

    /**
     * @return an instance of {@link InputConnection} that implements only methods that were
     * available in {@link android.os.Build.VERSION_CODES#CUPCAKE}.
     */
    @AnyThread
    public static InputConnection createMinimallyImplementedNoOpInputConnection() {
        return new MinimallyImplementedNoOpInputConnection();
    }
}
