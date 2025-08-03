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

package android.server.biometrics.fingerprint;

import android.app.Activity;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Bundle;
import android.server.wm.TestJournalProvider;

import androidx.annotation.NonNull;

import java.util.ArrayList;

/**
 * Authentication callback helper that allows easy transfer between test activities and
 * CTS via {@link android.server.wm.TestJournalProvider.TestJournal}, as well as serialization
 * and deserialization.
 *
 * Note that generally a single instance of this helper should only be used for a single
 * authentication.
 */
@SuppressWarnings("deprecation")
public class FingerprintCallbackHelper extends FingerprintManager.AuthenticationCallback {

    public static final String KEY = "key_auth_callback";

    public static class State {
        private static final String KEY_ERRORS_RECEIVED = "key_errors_received";
        private static final String KEY_ACQUIRED_RECEIVED = "key_acquired_received";
        private static final String KEY_NUM_ACCEPTED = "key_num_accepted";
        private static final String KEY_NUM_REJECTED = "key_num_rejected";

        public final ArrayList<Integer> mErrorsReceived;
        public final ArrayList<Integer> mAcquiredReceived;
        public int mNumAuthAccepted;
        public int mNumAuthRejected;

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
            return bundle;
        }

        private State(ArrayList<Integer> errorsReceived, ArrayList<Integer> acquiredReceived,
                int numAuthAccepted, int numAuthRejected) {
            mErrorsReceived = errorsReceived;
            mAcquiredReceived = acquiredReceived;
            mNumAuthAccepted = numAuthAccepted;
            mNumAuthRejected = numAuthRejected;
        }

        public static State fromBundle(@NonNull Bundle bundle) {
            return new State(
                    bundle.getIntegerArrayList(KEY_ERRORS_RECEIVED),
                    bundle.getIntegerArrayList(KEY_ACQUIRED_RECEIVED),
                    bundle.getInt(KEY_NUM_ACCEPTED),
                    bundle.getInt(KEY_NUM_REJECTED));
        }
    }

    private final Activity mActivity;
    private final State mState;

    @Override
    public void onAuthenticationError(int errorCode, CharSequence errString) {
        mState.mErrorsReceived.add(errorCode);
        updateJournal();
    }

    @Override
    public void onAuthenticationHelp(int helpCode, CharSequence helpString) {
        mState.mAcquiredReceived.add(helpCode);
        updateJournal();
    }

    @Override
    public void onAuthenticationSucceeded(FingerprintManager.AuthenticationResult result) {
        mState.mNumAuthAccepted++;
        updateJournal();
    }

    @Override
    public void onAuthenticationFailed() {
        mState.mNumAuthRejected++;
        updateJournal();
    }

    public FingerprintCallbackHelper(@NonNull Activity activity) {
        mActivity = activity;
        mState = new State();

        // Update with empty state. It's faster than waiting/retrying for null on CTS-side.
        updateJournal();
    }

    private void updateJournal() {
        TestJournalProvider.putExtras(mActivity,
                bundle -> bundle.putBundle(KEY, mState.toBundle()));
    }
}
