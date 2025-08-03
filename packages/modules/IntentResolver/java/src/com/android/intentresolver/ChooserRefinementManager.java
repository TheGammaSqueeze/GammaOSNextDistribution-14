/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.intentresolver;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcel;
import android.os.ResultReceiver;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.android.intentresolver.chooser.TargetInfo;

import dagger.hilt.android.lifecycle.HiltViewModel;

import java.util.List;
import java.util.function.Consumer;

import javax.inject.Inject;


/**
 * Helper class to manage Sharesheet's "refinement" flow, where callers supply a "refinement
 * activity" that will be invoked when a target is selected, allowing the calling app to add
 * additional extras and other refinements (subject to {@link Intent#filterEquals}), e.g., to
 * convert the format of the payload, or lazy-download some data that was deferred in the original
 * call).
 */
@HiltViewModel
@UiThread
public final class ChooserRefinementManager extends ViewModel {
    private static final String TAG = "ChooserRefinement";

    @Nullable    // Non-null only during an active refinement session.
    private RefinementResultReceiver mRefinementResultReceiver;

    private boolean mConfigurationChangeInProgress = false;

    /**
     * A token for the completion of a refinement process that can be consumed exactly once.
     */
    public static class RefinementCompletion {
        private TargetInfo mTargetInfo;
        private boolean mConsumed;

        RefinementCompletion(TargetInfo targetInfo) {
            mTargetInfo = targetInfo;
        }

        /**
         * @return The output of the completed refinement process. Null if the process was aborted
         *         or failed.
         */
        public TargetInfo getTargetInfo() {
            return mTargetInfo;
        }

        /**
         * Mark this event as consumed if it wasn't already.
         *
         * @return true if this had not already been consumed.
         */
        public boolean consume() {
            if (!mConsumed) {
                mConsumed = true;
                return true;
            }
            return false;
        }
    }

    private MutableLiveData<RefinementCompletion> mRefinementCompletion = new MutableLiveData<>();

    @Inject
    public ChooserRefinementManager() {}

    public LiveData<RefinementCompletion> getRefinementCompletion() {
        return mRefinementCompletion;
    }

    /**
     * Delegate the user's {@code selectedTarget} to the refinement flow, if possible.
     * @return true if the selection should wait for a now-started refinement flow, or false if it
     * can proceed by the default (non-refinement) logic.
     */
    public boolean maybeHandleSelection(TargetInfo selectedTarget,
            IntentSender refinementIntentSender, Application application, Handler mainHandler) {
        if (refinementIntentSender == null) {
            return false;
        }
        if (selectedTarget.getAllSourceIntents().isEmpty()) {
            return false;
        }
        if (selectedTarget.isSuspended()) {
            // We expect all launches to fail for this target, so don't make the user go through the
            // refinement flow first. Besides, the default (non-refinement) handling displays a
            // warning in this case and recovers the session; we won't be equipped to recover if
            // problems only come up after refinement.
            return false;
        }

        destroy();  // Terminate any prior sessions.
        mRefinementResultReceiver = new RefinementResultReceiver(
                refinedIntent -> {
                    destroy();

                    TargetInfo refinedTarget =
                            selectedTarget.tryToCloneWithAppliedRefinement(refinedIntent);
                    if (refinedTarget != null) {
                        mRefinementCompletion.setValue(new RefinementCompletion(refinedTarget));
                    } else {
                        Log.e(TAG, "Failed to apply refinement to any matching source intent");
                        mRefinementCompletion.setValue(new RefinementCompletion(null));
                    }
                },
                () -> {
                    destroy();
                    mRefinementCompletion.setValue(new RefinementCompletion(null));
                },
                mainHandler);

        Intent refinementRequest = makeRefinementRequest(mRefinementResultReceiver, selectedTarget);
        try {
            refinementIntentSender.sendIntent(application, 0, refinementRequest, null, null);
            return true;
        } catch (IntentSender.SendIntentException e) {
            Log.e(TAG, "Refinement IntentSender failed to send", e);
        }
        return true;
    }

    /** ChooserActivity has stopped */
    public void onActivityStop(boolean configurationChanging) {
        mConfigurationChangeInProgress = configurationChanging;
    }

    /** ChooserActivity has resumed */
    public void onActivityResume() {
        if (mConfigurationChangeInProgress) {
            mConfigurationChangeInProgress = false;
        } else {
            if (mRefinementResultReceiver != null) {
                // This can happen if the refinement activity terminates without ever sending a
                // response to our `ResultReceiver`. We're probably not prepared to return the user
                // into a valid Chooser session, so we'll treat it as a cancellation instead.
                Log.w(TAG, "Chooser resumed while awaiting refinement result; aborting");
                destroy();
                mRefinementCompletion.setValue(new RefinementCompletion(null));
            }
        }
    }

    @Override
    protected void onCleared() {
        // App lifecycle over, time to clean up.
        destroy();
    }

    /** Clean up any ongoing refinement session. */
    private void destroy() {
        if (mRefinementResultReceiver != null) {
            mRefinementResultReceiver.destroyReceiver();
            mRefinementResultReceiver = null;
        }
    }

    private static Intent makeRefinementRequest(
            RefinementResultReceiver resultReceiver, TargetInfo originalTarget) {
        final Intent fillIn = new Intent();
        final List<Intent> sourceIntents = originalTarget.getAllSourceIntents();
        fillIn.putExtra(Intent.EXTRA_INTENT, sourceIntents.get(0));
        final int sourceIntentCount = sourceIntents.size();
        if (sourceIntentCount > 1) {
            fillIn.putExtra(
                    Intent.EXTRA_ALTERNATE_INTENTS,
                    sourceIntents
                            .subList(1, sourceIntentCount)
                            .toArray(new Intent[sourceIntentCount - 1]));
        }
        fillIn.putExtra(Intent.EXTRA_RESULT_RECEIVER, resultReceiver.copyForSending());
        return fillIn;
    }

    private static class RefinementResultReceiver extends ResultReceiver {
        private final Consumer<Intent> mOnSelectionRefined;
        private final Runnable mOnRefinementCancelled;

        private boolean mDestroyed;

        RefinementResultReceiver(
                Consumer<Intent> onSelectionRefined,
                Runnable onRefinementCancelled,
                Handler handler) {
            super(handler);
            mOnSelectionRefined = onSelectionRefined;
            mOnRefinementCancelled = onRefinementCancelled;
        }

        public void destroyReceiver() {
            mDestroyed = true;
        }

        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            if (mDestroyed) {
                Log.e(TAG, "Destroyed RefinementResultReceiver received a result");
                return;
            }

            destroyReceiver();  // This is the single callback we'll accept from this session.

            Intent refinedResult = tryToExtractRefinedResult(resultCode, resultData);
            if (refinedResult == null) {
                mOnRefinementCancelled.run();
            } else {
                mOnSelectionRefined.accept(refinedResult);
            }
        }

        /**
         * Apps can't load this class directly, so we need a regular ResultReceiver copy for
         * sending. Obtain this by parceling and unparceling (one weird trick).
         */
        ResultReceiver copyForSending() {
            Parcel parcel = Parcel.obtain();
            writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            ResultReceiver receiverForSending = ResultReceiver.CREATOR.createFromParcel(parcel);
            parcel.recycle();
            return receiverForSending;
        }

        /**
         * Get the refinement from the result data, if possible, or log diagnostics and return null.
         */
        @Nullable
        private static Intent tryToExtractRefinedResult(int resultCode, Bundle resultData) {
            if (Activity.RESULT_CANCELED == resultCode) {
                Log.i(TAG, "Refinement canceled by caller");
            } else if (Activity.RESULT_OK != resultCode) {
                Log.w(TAG, "Canceling refinement on unrecognized result code " + resultCode);
            } else if (resultData == null) {
                Log.e(TAG, "RefinementResultReceiver received null resultData; canceling");
            } else if (!(resultData.getParcelable(Intent.EXTRA_INTENT) instanceof Intent)) {
                Log.e(TAG, "No valid Intent.EXTRA_INTENT in 'OK' refinement result data");
            } else {
                return resultData.getParcelable(Intent.EXTRA_INTENT, Intent.class);
            }
            return null;
        }
    }
}
