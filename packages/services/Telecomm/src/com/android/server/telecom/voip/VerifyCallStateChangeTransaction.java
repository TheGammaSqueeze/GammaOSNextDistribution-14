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

package com.android.server.telecom.voip;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.telecom.Call;
import com.android.server.telecom.CallsManager;

import android.telecom.DisconnectCause;
import android.telecom.Log;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/**
 * VerifyCallStateChangeTransaction is a transaction that verifies a CallState change and has
 * the ability to disconnect if the CallState is not changed within the timeout window.
 * <p>
 * Note: This transaction has a timeout of 2 seconds.
 */
public class VerifyCallStateChangeTransaction extends VoipCallTransaction {
    private static final String TAG = VerifyCallStateChangeTransaction.class.getSimpleName();
    public static final int FAILURE_CODE = 0;
    public static final int SUCCESS_CODE = 1;
    public static final int TIMEOUT_SECONDS = 2;
    private final Call mCall;
    private final CallsManager mCallsManager;
    private final int mTargetCallState;
    private final boolean mShouldDisconnectUponFailure;
    private final CompletableFuture<Integer> mCallStateOrTimeoutResult = new CompletableFuture<>();
    private final CompletableFuture<VoipCallTransactionResult> mTransactionResult =
            new CompletableFuture<>();

    @VisibleForTesting
    public Call.CallStateListener mCallStateListenerImpl = new Call.CallStateListener() {
        @Override
        public void onCallStateChanged(int newCallState) {
            Log.d(TAG, "newState=[%d], expectedState=[%d]", newCallState, mTargetCallState);
            if (newCallState == mTargetCallState) {
                mCallStateOrTimeoutResult.complete(SUCCESS_CODE);
            }
            // NOTE:: keep listening to the call state until the timeout is reached. It's possible
            // another call state is reached in between...
        }
    };

    public VerifyCallStateChangeTransaction(CallsManager callsManager, Call call,
            int targetCallState, boolean shouldDisconnectUponFailure) {
        super(callsManager.getLock());
        mCallsManager = callsManager;
        mCall = call;
        mTargetCallState = targetCallState;
        mShouldDisconnectUponFailure = shouldDisconnectUponFailure;
    }

    @Override
    public CompletionStage<VoipCallTransactionResult> processTransaction(Void v) {
        Log.d(TAG, "processTransaction:");
        // It's possible the Call is already in the expected call state
        if (isNewCallStateTargetCallState()) {
            mTransactionResult.complete(
                    new VoipCallTransactionResult(VoipCallTransactionResult.RESULT_SUCCEED,
                            TAG));
            return mTransactionResult;
        }
        initCallStateListenerOnTimeout();
        // At this point, the mCallStateOrTimeoutResult has been completed. There are 2 scenarios:
        // (1) newCallState == targetCallState --> the transaction is successful
        // (2) timeout is reached --> evaluate the current call state and complete the t accordingly
        // also need to do cleanup for the transaction
        evaluateCallStateUponChangeOrTimeout();

        return mTransactionResult;
    }

    private boolean isNewCallStateTargetCallState() {
        return mCall.getState() == mTargetCallState;
    }

    private void initCallStateListenerOnTimeout() {
        mCall.addCallStateListener(mCallStateListenerImpl);
        mCallStateOrTimeoutResult.completeOnTimeout(FAILURE_CODE, TIMEOUT_SECONDS,
                TimeUnit.SECONDS);
    }

    private void evaluateCallStateUponChangeOrTimeout() {
        mCallStateOrTimeoutResult.thenAcceptAsync((result) -> {
            Log.i(TAG, "processTransaction: thenAcceptAsync: result=[%s]", result);
            mCall.removeCallStateListener(mCallStateListenerImpl);
            if (isNewCallStateTargetCallState()) {
                mTransactionResult.complete(
                        new VoipCallTransactionResult(VoipCallTransactionResult.RESULT_SUCCEED,
                                TAG));
            } else {
                maybeDisconnectCall();
                mTransactionResult.complete(
                        new VoipCallTransactionResult(VoipCallTransactionResult.RESULT_FAILED,
                                TAG));
            }
        }).exceptionally(exception -> {
            Log.i(TAG, "hit exception=[%s] while completing future", exception);
            mTransactionResult.complete(
                    new VoipCallTransactionResult(VoipCallTransactionResult.RESULT_FAILED,
                            TAG));
            return null;
        });
    }

    private void maybeDisconnectCall() {
        if (mShouldDisconnectUponFailure) {
            mCallsManager.markCallAsDisconnected(mCall,
                    new DisconnectCause(DisconnectCause.ERROR,
                            "did not hold in timeout window"));
            mCallsManager.markCallAsRemoved(mCall);
        }
    }

    @VisibleForTesting
    public CompletableFuture<Integer> getCallStateOrTimeoutResult() {
        return mCallStateOrTimeoutResult;
    }

    @VisibleForTesting
    public CompletableFuture<VoipCallTransactionResult> getTransactionResult() {
        return mTransactionResult;
    }

    @VisibleForTesting
    public Call.CallStateListener getCallStateListenerImpl() {
        return mCallStateListenerImpl;
    }
}
