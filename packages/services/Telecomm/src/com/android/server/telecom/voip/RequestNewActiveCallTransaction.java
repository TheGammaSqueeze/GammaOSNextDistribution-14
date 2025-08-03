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

package com.android.server.telecom.voip;

import android.os.OutcomeReceiver;
import android.telecom.CallAttributes;
import android.telecom.CallException;
import android.util.Log;

import com.android.server.telecom.Call;
import com.android.server.telecom.CallState;
import com.android.server.telecom.CallsManager;
import com.android.server.telecom.ConnectionServiceFocusManager;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * This transaction should be created when a requesting call would like to go from a valid inactive
 * state (ex. HELD, RINGING, DIALING) to ACTIVE.
 *
 * This class performs some pre-checks to spot a failure in requesting a new call focus and sends
 * the official request to transition the requested call to ACTIVE.
 *
 * Note:
 * - This Transaction is used for CallControl and CallEventCallbacks, do not put logic in the
 * onResult/onError that pertains to one direction.
 * - MaybeHoldCallForNewCallTransaction was performed before this so any potential active calls
 * should be held now.
 */
public class RequestNewActiveCallTransaction extends VoipCallTransaction {

    private static final String TAG = RequestNewActiveCallTransaction.class.getSimpleName();
    private final CallsManager mCallsManager;
    private final Call mCall;

    public RequestNewActiveCallTransaction(CallsManager callsManager, Call call) {
        super(callsManager.getLock());
        mCallsManager = callsManager;
        mCall = call;
    }

    @Override
    public CompletionStage<VoipCallTransactionResult> processTransaction(Void v) {
        Log.d(TAG, "processTransaction");
        CompletableFuture<VoipCallTransactionResult> future = new CompletableFuture<>();
        int currentCallState = mCall.getState();

        // certain calls cannot go active/answered (ex. disconnect calls, etc.)
        if (!canBecomeNewCallFocus(currentCallState)) {
            future.complete(new VoipCallTransactionResult(
                    CallException.CODE_CALL_CANNOT_BE_SET_TO_ACTIVE,
                    "CallState cannot be set to active or answered due to current call"
                            + " state being in invalid state"));
            return future;
        }

        if (mCallsManager.getActiveCall() != null) {
            future.complete(new VoipCallTransactionResult(
                    CallException.CODE_CALL_CANNOT_BE_SET_TO_ACTIVE,
                    "Already an active call. Request hold on current active call."));
            return future;
        }

        mCallsManager.requestNewCallFocusAndVerify(mCall, new OutcomeReceiver<>() {
                    @Override
                    public void onResult(Boolean result) {
                        Log.d(TAG, "processTransaction: onResult");
                        future.complete(new VoipCallTransactionResult(
                                VoipCallTransactionResult.RESULT_SUCCEED, null));
                    }

                    @Override
                    public void onError(CallException exception) {
                        Log.d(TAG, "processTransaction: onError");
                        future.complete(new VoipCallTransactionResult(
                                exception.getCode(), exception.getMessage()));
                    }
                });

        return future;
    }

    private boolean isPriorityCallingState(int currentCallState) {
        return ConnectionServiceFocusManager.PRIORITY_FOCUS_CALL_STATE.contains(currentCallState);
    }

    private boolean canBecomeNewCallFocus(int currentCallState) {
        return isPriorityCallingState(currentCallState) || currentCallState == CallState.ON_HOLD;
    }
}