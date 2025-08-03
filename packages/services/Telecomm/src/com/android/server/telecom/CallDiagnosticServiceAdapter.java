/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.annotation.NonNull;
import android.os.Binder;
import android.os.RemoteException;
import android.telecom.CallDiagnosticService;
import android.telecom.CallDiagnostics;
import android.telecom.Log;

import com.android.internal.telecom.ICallDiagnosticServiceAdapter;

/**
 * Adapter class used to provide a path for messages FROM a {@link CallDiagnosticService} back to
 * the telecom stack.
 */
public class CallDiagnosticServiceAdapter extends ICallDiagnosticServiceAdapter.Stub {
    public interface TelecomAdapter {
        void displayDiagnosticMessage(String callId, int messageId, CharSequence message);
        void clearDiagnosticMessage(String callId, int messageId);
        void sendDeviceToDeviceMessage(String callId, @CallDiagnostics.MessageType int message,
                int value);
        void overrideDisconnectMessage(String callId, CharSequence message);
    }

    private final TelecomAdapter mTelecomAdapter;
    private final String mOwnerPackageName;
    private final String mOwnerPackageAbbreviation;
    private final TelecomSystem.SyncRoot mLock;

    CallDiagnosticServiceAdapter(@NonNull TelecomAdapter telecomAdapter,
            @NonNull String ownerPackageName, @NonNull TelecomSystem.SyncRoot lock) {
        mTelecomAdapter = telecomAdapter;
        mOwnerPackageName = ownerPackageName;
        mOwnerPackageAbbreviation = Log.getPackageAbbreviation(ownerPackageName);
        mLock = lock;
    }

    @Override
    public void displayDiagnosticMessage(String callId, int messageId, CharSequence message)
            throws RemoteException {
        try {
            Log.startSession("CDSA.dDM", mOwnerPackageAbbreviation);
            long token = Binder.clearCallingIdentity();
            try {
                synchronized (mLock) {
                    Log.i(this, "displayDiagnosticMessage; callId=%s, msg=%d/%s", callId, messageId,
                            message);
                    mTelecomAdapter.displayDiagnosticMessage(callId, messageId, message);
                }
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        } finally {
            Log.endSession();
        }
    }

    @Override
    public void clearDiagnosticMessage(String callId, int messageId) throws RemoteException {
        try {
            Log.startSession("CDSA.cDM", mOwnerPackageAbbreviation);
            long token = Binder.clearCallingIdentity();
            try {
                synchronized (mLock) {
                    Log.i(this, "clearDiagnosticMessage; callId=%s, msg=%d", callId, messageId);
                    mTelecomAdapter.clearDiagnosticMessage(callId, messageId);
                }
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        } finally {
            Log.endSession();
        }
    }

    @Override
    public void sendDeviceToDeviceMessage(String callId, @CallDiagnostics.MessageType int message,
            int value)
            throws RemoteException {
        try {
            Log.startSession("CDSA.sDTDM", mOwnerPackageAbbreviation);
            long token = Binder.clearCallingIdentity();
            try {
                synchronized (mLock) {
                    Log.i(this, "sendDeviceToDeviceMessage; callId=%s, msg=%d/%d", callId, message,
                            value);
                    mTelecomAdapter.sendDeviceToDeviceMessage(callId, message, value);
                }
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        } finally {
            Log.endSession();
        }
    }

    @Override
    public void overrideDisconnectMessage(String callId, CharSequence message)
            throws RemoteException {
        try {
            Log.startSession("CDSA.oDM", mOwnerPackageAbbreviation);
            long token = Binder.clearCallingIdentity();
            try {
                synchronized (mLock) {
                    Log.i(this, "overrideDisconnectMessage; callId=%s, msg=%s", callId, message);
                    mTelecomAdapter.overrideDisconnectMessage(callId, message);
                }
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        } finally {
            Log.endSession();
        }
    }
}
