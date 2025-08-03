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

package android.view.inputmethod.cts.disapproveime;

import android.inputmethodservice.InputMethodService;
import android.view.View;
import android.widget.LinearLayout;

/**
 * This IME simulates deprecated or inapplicable operations for test.
 */
public final class DisapproveInputMethodService extends InputMethodService {

    /**
     * The callback for {@link DisapproveInputMethodService} test verification.
     */
    public static DisapproveImeCallback mDisapproveImeCallback;

    @Override
    public View onCreateInputView() {
        return new LinearLayout(this);
    }

    @Override
    public void onCreate() {
        boolean hasLinkageError = false;
        try {
            super.onCreate();
        } catch (Throwable e) {
            if (e instanceof LinkageError) {
                hasLinkageError = true;
            }
        }
        if (mDisapproveImeCallback != null) {
            mDisapproveImeCallback.onCreate(hasLinkageError);
        }
    }

    @Override
    public void onDestroy() {
        // no-op
    }

    /**
     * Override the deprecated {@link InputMethodService#onCreateInputMethodSessionInterface}
     * for test.
     */
    @Override
    public AbstractInputMethodSessionImpl onCreateInputMethodSessionInterface() {
        return new MockInputMethodSessionImpl();
    }

    private class MockInputMethodSessionImpl extends InputMethodSessionImpl {
        // no-op
    }

    /**
     * Set {@link DisapproveImeCallback} callback.
     */
    public static void setCallback(DisapproveImeCallback callback) {
        mDisapproveImeCallback = callback;
    }

    /**
     * Defines a callback for {@link DisapproveInputMethodService} test.
     */
    public interface DisapproveImeCallback {
        void onCreate(boolean hasLinkageError);
    }
}
