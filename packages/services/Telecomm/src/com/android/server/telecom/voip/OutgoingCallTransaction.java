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

import static android.Manifest.permission.CALL_PRIVILEGED;
import static android.telecom.CallAttributes.CALL_CAPABILITIES_KEY;
import static android.telecom.CallAttributes.DISPLAY_NAME_KEY;
import static android.telecom.CallException.CODE_CALL_NOT_PERMITTED_AT_PRESENT_TIME;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.telecom.CallAttributes;
import android.telecom.TelecomManager;
import android.util.Log;

import com.android.server.telecom.Call;
import com.android.server.telecom.CallsManager;
import com.android.server.telecom.LoggedHandlerExecutor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class OutgoingCallTransaction extends VoipCallTransaction {

    private static final String TAG = OutgoingCallTransaction.class.getSimpleName();
    private final String mCallId;
    private final Context mContext;
    private final String mCallingPackage;
    private final CallAttributes mCallAttributes;
    private final CallsManager mCallsManager;
    private final Bundle mExtras;

    public OutgoingCallTransaction(String callId, Context context, CallAttributes callAttributes,
            CallsManager callsManager, Bundle extras) {
        super(callsManager.getLock());
        mCallId = callId;
        mContext = context;
        mCallAttributes = callAttributes;
        mCallsManager = callsManager;
        mExtras = extras;
        mCallingPackage = mContext.getOpPackageName();
    }

    public OutgoingCallTransaction(String callId, Context context, CallAttributes callAttributes,
            CallsManager callsManager) {
        this(callId, context, callAttributes, callsManager, new Bundle());
    }

    @Override
    public CompletionStage<VoipCallTransactionResult> processTransaction(Void v) {
        Log.d(TAG, "processTransaction");

        final boolean hasCallPrivilegedPermission = mContext.checkCallingPermission(
                CALL_PRIVILEGED) == PackageManager.PERMISSION_GRANTED;

        final Intent intent = new Intent(hasCallPrivilegedPermission ?
                Intent.ACTION_CALL_PRIVILEGED : Intent.ACTION_CALL, mCallAttributes.getAddress());

        if (mCallsManager.isOutgoingCallPermitted(mCallAttributes.getPhoneAccountHandle())) {
            Log.d(TAG, "processTransaction: outgoing call permitted");

            CompletableFuture<Call> callFuture =
                    mCallsManager.startOutgoingCall(mCallAttributes.getAddress(),
                            mCallAttributes.getPhoneAccountHandle(),
                            generateExtras(mCallAttributes),
                            mCallAttributes.getPhoneAccountHandle().getUserHandle(),
                            intent,
                            mCallingPackage);

            if (callFuture == null) {
                return CompletableFuture.completedFuture(
                        new VoipCallTransactionResult(
                                CODE_CALL_NOT_PERMITTED_AT_PRESENT_TIME,
                                "incoming call not permitted at the current time"));
            }
            CompletionStage<VoipCallTransactionResult> result = callFuture.thenComposeAsync(
                    (call) -> {

                        Log.d(TAG, "processTransaction: completing future");

                        if (call == null) {
                            Log.d(TAG, "processTransaction: call is null");
                            return CompletableFuture.completedFuture(
                                    new VoipCallTransactionResult(
                                            CODE_CALL_NOT_PERMITTED_AT_PRESENT_TIME,
                                            "call could not be created at this time"));
                        } else {
                            Log.d(TAG, "processTransaction: call done. id=" + call.getId());
                        }

                        return CompletableFuture.completedFuture(
                                new VoipCallTransactionResult(
                                        VoipCallTransactionResult.RESULT_SUCCEED,
                                        call, null));
                    }
                    , new LoggedHandlerExecutor(mHandler, "OCT.pT", null));

            return result;
        } else {
            return CompletableFuture.completedFuture(
                    new VoipCallTransactionResult(
                            CODE_CALL_NOT_PERMITTED_AT_PRESENT_TIME,
                            "incoming call not permitted at the current time"));

        }
    }

    private Bundle generateExtras(CallAttributes callAttributes) {
        mExtras.setDefusable(true);
        mExtras.putString(TelecomManager.TRANSACTION_CALL_ID_KEY, mCallId);
        mExtras.putInt(CALL_CAPABILITIES_KEY, callAttributes.getCallCapabilities());
        mExtras.putInt(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE,
                callAttributes.getCallType());
        mExtras.putCharSequence(DISPLAY_NAME_KEY, callAttributes.getDisplayName());
        return mExtras;
    }
}
