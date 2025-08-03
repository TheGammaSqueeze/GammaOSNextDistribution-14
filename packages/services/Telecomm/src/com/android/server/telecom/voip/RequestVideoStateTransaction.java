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

import static com.android.server.telecom.voip.VideoStateTranslation.TransactionalVideoStateToVideoProfileState;

import android.telecom.VideoProfile;
import android.util.Log;

import com.android.server.telecom.CallsManager;
import com.android.server.telecom.Call;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class RequestVideoStateTransaction extends VoipCallTransaction {

    private static final String TAG = RequestVideoStateTransaction.class.getSimpleName();
    private final Call mCall;
    private final int mVideoProfileState;

    public RequestVideoStateTransaction(CallsManager callsManager, Call call,
            int transactionalVideoState) {
        super(callsManager.getLock());
        mCall = call;
        mVideoProfileState = TransactionalVideoStateToVideoProfileState(transactionalVideoState);
    }

    @Override
    public CompletionStage<VoipCallTransactionResult> processTransaction(Void v) {
        Log.d(TAG, "processTransaction");
        CompletableFuture<VoipCallTransactionResult> future = new CompletableFuture<>();

        if (isRequestingVideoTransmission(mVideoProfileState) &&
                !mCall.isVideoCallingSupportedByPhoneAccount()) {
            future.complete(new VoipCallTransactionResult(
                    VoipCallTransactionResult.RESULT_FAILED,
                    "Video calling is not supported by the target account"));
        } else if (isRequestingVideoTransmission(mVideoProfileState) &&
                !mCall.isTransactionalCallSupportsVideoCalling()) {
            future.complete(new VoipCallTransactionResult(
                    VoipCallTransactionResult.RESULT_FAILED,
                    "Video calling is not supported according to the callAttributes"));
        } else {
            mCall.setVideoState(mVideoProfileState);
            future.complete(new VoipCallTransactionResult(
                    VoipCallTransactionResult.RESULT_SUCCEED,
                    "The Video State was changed successfully"));
        }
        return future;
    }

    private boolean isRequestingVideoTransmission(int targetVideoState) {
        return targetVideoState != VideoProfile.STATE_AUDIO_ONLY;
    }
}