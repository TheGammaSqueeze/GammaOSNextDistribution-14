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

package android.server.biometrics.cts.app;

import android.hardware.biometrics.BiometricPrompt;
import android.util.Log;

import androidx.annotation.NonNull;

/** Callback wrapper for tests. */
public class TestAuthCallback extends BiometricPrompt.AuthenticationCallback {

    private final String mTag;
    private boolean mHasAuthenticated = false;
    private boolean mHasError = false;

    /** Create a new callback. */
    public TestAuthCallback(@NonNull String tag) {
        mTag = tag;
    }

    @Override
    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
        Log.d(mTag, "onAuthenticationSucceeded");

        mHasAuthenticated = true;
    }

    @Override
    public void onAuthenticationError(int errorCode, CharSequence errString) {
        Log.d(mTag, "onAuthenticationError (" + errorCode + "): " + errString);

        mHasError = true;
    }

    @Override
    public void onAuthenticationHelp(int helpCode, CharSequence helpString) {
        Log.d(mTag, "onAuthenticationHelp");
    }

    @Override
    public void onAuthenticationFailed() {
        Log.d(mTag, "onAuthenticationFailed");
    }

    /** Wait for a terminal success or failure event (or timeout). */
    public void awaitResult() throws InterruptedException {
        final int interval = 100;
        long remaining = 5000;
        while (!mHasAuthenticated && !mHasError && remaining > 0) {
            Thread.sleep(interval);
            remaining -= interval;
        }
    }

    /** If {@link #onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult)} was called. */
    public boolean isAuthenticatedWithResult() {
        return mHasAuthenticated;
    }
}
