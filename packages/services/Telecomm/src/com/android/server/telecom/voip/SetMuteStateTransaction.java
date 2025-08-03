/*
 * Copyright (C) 2024 The Android Open Source Project
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

import android.util.Log;

import com.android.server.telecom.CallsManager;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * This transaction should be used to change the global mute state for transactional
 * calls. There is currently no way for this transaction to fail.
 */
public class SetMuteStateTransaction extends VoipCallTransaction {

    private static final String TAG = SetMuteStateTransaction.class.getSimpleName();
    private final CallsManager mCallsManager;
    private final boolean mIsMuted;

    public SetMuteStateTransaction(CallsManager callsManager, boolean isMuted) {
        super(callsManager.getLock());
        mCallsManager = callsManager;
        mIsMuted = isMuted;
    }

    @Override
    public CompletionStage<VoipCallTransactionResult> processTransaction(Void v) {
        Log.d(TAG, "processTransaction");
        CompletableFuture<VoipCallTransactionResult> future = new CompletableFuture<>();

        mCallsManager.mute(mIsMuted);

        future.complete(new VoipCallTransactionResult(
                VoipCallTransactionResult.RESULT_SUCCEED,
                "The Mute State was changed successfully"));

        return future;
    }
}
