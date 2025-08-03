/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.server.biometrics;

import android.app.Activity;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Bundle;
import android.server.wm.TestJournalProvider;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.ArrayList;

public class BiometricCallbackHelper extends BiometricPrompt.AuthenticationCallback {

    private static final String TAG = "BiometricCallbackHelper";
    public static final String KEY = "key_auth_callback";

    public static class State {
        private static final String KEY_ERRORS_RECEIVED = "key_errors_received";
        private static final String KEY_ACQUIRED_RECEIVED = "key_acquired_received";
        private static final String KEY_NUM_ACCEPTED = "key_num_accepted";
        private static final String KEY_NUM_REJECTED = "key_num_rejected";
        private static final String KEY_NEGATIVE_BUTTON_PRESSED = "key_negative_button_pressed";

        public final ArrayList<Integer> mErrorsReceived;
        public final ArrayList<Integer> mAcquiredReceived;
        public int mNumAuthAccepted;
        public int mNumAuthRejected;
        public boolean mNegativeButtonPressed;

        public State() {
            mErrorsReceived = new ArrayList<>();
            mAcquiredReceived = new ArrayList<>();
        }

        public Bundle toBundle() {
            final Bundle bundle = new Bundle();
            bundle.putIntegerArrayList(KEY_ERRORS_RECEIVED, mErrorsReceived);
            bundle.putIntegerArrayList(KEY_ACQUIRED_RECEIVED, mAcquiredReceived);
            bundle.putInt(KEY_NUM_ACCEPTED, mNumAuthAccepted);
            bundle.putInt(KEY_NUM_REJECTED, mNumAuthRejected);
            bundle.putBoolean(KEY_NEGATIVE_BUTTON_PRESSED, mNegativeButtonPressed);
            return bundle;
        }

        private State(ArrayList<Integer> errorsReceived, ArrayList<Integer> acquiredReceived,
                int numAuthAccepted, int numAuthRejected, boolean negativeButtonPressed) {
            mErrorsReceived = errorsReceived;
            mAcquiredReceived = acquiredReceived;
            mNumAuthAccepted = numAuthAccepted;
            mNumAuthRejected = numAuthRejected;
            mNegativeButtonPressed = negativeButtonPressed;
        }

        public static BiometricCallbackHelper.State fromBundle(@NonNull Bundle bundle) {
            return new BiometricCallbackHelper.State(
                    bundle.getIntegerArrayList(KEY_ERRORS_RECEIVED),
                    bundle.getIntegerArrayList(KEY_ACQUIRED_RECEIVED),
                    bundle.getInt(KEY_NUM_ACCEPTED),
                    bundle.getInt(KEY_NUM_REJECTED),
                    bundle.getBoolean(KEY_NEGATIVE_BUTTON_PRESSED));
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb.append("Accept: ").append(mNumAuthAccepted)
                    .append(", Reject: ").append(mNumAuthRejected)
                    .append(", Acquired Count: " ).append(mAcquiredReceived.size())
                    .append(", Errors Count: ").append(mErrorsReceived.size())
                    .append(", Negative pressed: ").append(mNegativeButtonPressed);
            return sb.toString();
        }
    }

    private final Activity mActivity;
    private final State mState;

    @Override
    public void onAuthenticationError(int errorCode, CharSequence errString) {
        Log.d(TAG, "onAuthenticationError: " + errorCode + ", " + errString);
        mState.mErrorsReceived.add(errorCode);
        updateJournal();
    }

    @Override
    public void onAuthenticationHelp(int helpCode, CharSequence helpString) {
        Log.d(TAG, "onAuthenticationHelp: " + helpCode + ", " + helpString);
        mState.mAcquiredReceived.add(helpCode);
        updateJournal();
    }

    @Override
    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
        Log.d(TAG, "onAuthenticationSucceeded");
        mState.mNumAuthAccepted++;
        updateJournal();
    }

    @Override
    public void onAuthenticationFailed() {
        Log.d(TAG, "onAuthenticationFailed");
        mState.mNumAuthRejected++;
        updateJournal();
    }

    void onNegativeButtonPressed() {
        Log.d(TAG, "onNegativeButtonPressed");
        mState.mNegativeButtonPressed = true;
        updateJournal();
    }

    public BiometricCallbackHelper(@NonNull Activity activity) {
        mActivity = activity;
        mState = new BiometricCallbackHelper.State();

        // Update with empty state. It's faster than waiting/retrying for null on CTS-side.
        updateJournal();
    }

    private void updateJournal() {
        TestJournalProvider.putExtras(mActivity,
                bundle -> bundle.putBundle(KEY, mState.toBundle()));
    }
}
