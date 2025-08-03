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

package android.accessibilityservice.cts;

import android.accessibility.cts.common.InstrumentedAccessibilityService;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.InputMethod;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.CountDownLatch;

/**
 * A stub accessibility service to install for testing IME APIs
 */
public class StubImeAccessibilityService extends InstrumentedAccessibilityService {
    private static final int INVALID = -11;
    public int selStart = -1;
    public int selEnd = -1;
    public int oldSelStart = -1;
    public int oldSelEnd = -1;
    private CountDownLatch mStartInputLatch = null;
    private CountDownLatch mSelectionChangeLatch = null;
    private int mSelectionTarget = INVALID;

    @FunctionalInterface
    public interface OnStartInputCallback {
        void onStartInput(EditorInfo editorInfo, boolean restarting);
    }

    private OnStartInputCallback mOnStartInputCallback;

    public void setOnStartInputCallback(OnStartInputCallback callback) {
        mOnStartInputCallback = callback;
    }

    @FunctionalInterface
    public interface OnUpdateSelectionCallback {
        void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd,
                int candidatesStart, int candidatesEnd);
    }

    @Nullable
    private OnUpdateSelectionCallback mOnUpdateSelectionCallback = null;

    @MainThread
    public void setOnUpdateSelectionCallback(@Nullable OnUpdateSelectionCallback listener) {
        mOnUpdateSelectionCallback = listener;
    }

    public void setStartInputCountDownLatch(CountDownLatch latch) {
        mStartInputLatch = latch;
    }

    public void setSelectionChangeLatch(CountDownLatch latch) {
        mSelectionChangeLatch = latch;
    }
    public void setSelectionTarget(int target) {
        mSelectionTarget = target;
    }

    class InputMethodImpl extends InputMethod {
        InputMethodImpl(@NonNull AccessibilityService service) {
            super(service);
        }

        @Override
        public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart,
                int newSelEnd, int candidatesStart, int candidatesEnd) {
            super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart,
                    candidatesEnd);
            // A11y receive placeholder notification.
            if ((mSelectionChangeLatch != null) && (newSelStart == mSelectionTarget)) {
                StubImeAccessibilityService.this.oldSelStart = oldSelStart;
                StubImeAccessibilityService.this.oldSelEnd = oldSelEnd;
                StubImeAccessibilityService.this.selStart = newSelStart;
                StubImeAccessibilityService.this.selEnd = newSelEnd;
                mSelectionChangeLatch.countDown();
                mSelectionChangeLatch = null;
            }
            if (mOnUpdateSelectionCallback != null) {
                mOnUpdateSelectionCallback.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart,
                        newSelEnd, candidatesStart, candidatesEnd);
            }
        }

        @Override
        public void onStartInput(EditorInfo attribute, boolean restarting) {
            super.onStartInput(attribute, restarting);
            if (mStartInputLatch != null) {
                mStartInputLatch.countDown();
                mStartInputLatch = null;
            }
            if (mOnStartInputCallback != null) {
                mOnStartInputCallback.onStartInput(attribute, restarting);
            }
        }
    }

    @Override
    public InputMethod onCreateInputMethod() {
        return new InputMethodImpl(this);
    }
}
