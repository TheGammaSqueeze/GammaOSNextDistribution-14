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

import android.telecom.CallException;
import android.util.Log;

import com.android.server.telecom.Call;
import com.android.server.telecom.CallsManager;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class HoldCallTransaction extends VoipCallTransaction {

    private static final String TAG = HoldCallTransaction.class.getSimpleName();
    private final CallsManager mCallsManager;
    private final Call mCall;

    public HoldCallTransaction(CallsManager callsManager, Call call) {
        super(callsManager.getLock());
        mCallsManager = callsManager;
        mCall = call;
    }

    @Override
    public CompletionStage<VoipCallTransactionResult> processTransaction(Void v) {
        Log.d(TAG, "processTransaction");
        CompletableFuture<VoipCallTransactionResult> future = new CompletableFuture<>();

        if (mCallsManager.canHold(mCall)) {
            mCallsManager.markCallAsOnHold(mCall);
            future.complete(new VoipCallTransactionResult(
                    VoipCallTransactionResult.RESULT_SUCCEED, null));
        } else {
            Log.d(TAG, "processTransaction: onError");
            future.complete(new VoipCallTransactionResult(
                    CallException.CODE_CANNOT_HOLD_CURRENT_ACTIVE_CALL, "cannot hold call"));
        }
        return future;
    }
}
