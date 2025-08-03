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

package android.view.inputmethod.cts.inprocime;

import android.inputmethodservice.InputMethodService;

import androidx.annotation.AnyThread;
import androidx.annotation.MainThread;
import androidx.annotation.Nullable;

import java.util.concurrent.atomic.AtomicReference;

public final class InProcIme extends InputMethodService {

    private static AtomicReference<InProcIme> sInstance = new AtomicReference<>();

    @MainThread
    @Override
    public void onCreate() {
        super.onCreate();
        sInstance.compareAndSet(null, this);
    }

    @MainThread
    @Override
    public void onDestroy() {
        super.onDestroy();
        sInstance.compareAndSet(this, null);
        mOnUpdateSelectionListener = null;
    }

    @AnyThread
    public static InProcIme getInstance() {
        return sInstance.get();
    }

    @FunctionalInterface
    public interface OnUpdateSelectionListener {
        void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd,
                int candidatesStart, int candidatesEnd);
    }

    @Nullable
    private OnUpdateSelectionListener mOnUpdateSelectionListener = null;

    @MainThread
    public void setOnUpdateSelectionListener(@Nullable OnUpdateSelectionListener listener) {
        mOnUpdateSelectionListener = listener;
    }

    @MainThread
    @Override
    public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd,
            int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart,
                candidatesEnd);
        if (mOnUpdateSelectionListener != null) {
            mOnUpdateSelectionListener.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart,
                    newSelEnd, candidatesStart, candidatesEnd);
        }
    }
}
