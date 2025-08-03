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

package com.android.server.telecom;

import android.os.Binder;
import android.os.RemoteException;
import android.telecom.DisconnectCause;
import android.telecom.Log;
import android.telecom.StreamingCall;

import com.android.internal.telecom.IStreamingCallAdapter;

/**
 * Receives call commands and updates from general call streaming app and passes them through to
 * the original voip call app. {@link android.telecom.CallStreamingService} creates an instance of
 * this class and passes it to the general call streaming app after binding to it. This adapter can
 * receive commands and updates until the general call streaming app is unbound.
 */
public class StreamingCallAdapter extends IStreamingCallAdapter.Stub {
    private final static String TAG = "StreamingCallAdapter";

    private final TransactionalServiceWrapper mTransactionalServiceWrapper;
    private final Call mCall;
    private final String mOwnerPackageAbbreviation;

    public StreamingCallAdapter(TransactionalServiceWrapper wrapper, Call call,
            String ownerPackageName) {
        mTransactionalServiceWrapper = wrapper;
        mCall = call;
        mOwnerPackageAbbreviation = Log.getPackageAbbreviation(ownerPackageName);
    }

    @Override
    public void setStreamingState(int state) throws RemoteException {
        try {
            Log.startSession(LogUtils.Sessions.CSA_SET_STATE, mOwnerPackageAbbreviation);
            long token = Binder.clearCallingIdentity();
            try {
                Log.i(this, "setStreamingState(%d)", state);
                switch (state) {
                    case StreamingCall.STATE_STREAMING:
                        mTransactionalServiceWrapper.onSetActive(mCall);
                    case StreamingCall.STATE_HOLDING:
                        mTransactionalServiceWrapper.onSetInactive(mCall);
                    case StreamingCall.STATE_DISCONNECTED:
                        mTransactionalServiceWrapper.onDisconnect(mCall,
                                new DisconnectCause(DisconnectCause.LOCAL));
                    default:
                        // ignore
                }
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        } finally {
            Log.endSession();
        }
    }
}
