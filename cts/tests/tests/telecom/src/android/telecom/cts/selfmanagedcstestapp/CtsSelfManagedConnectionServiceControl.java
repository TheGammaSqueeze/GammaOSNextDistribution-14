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

package android.telecom.cts.selfmanagedcstestapp;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telecom.cts.CtsSelfManagedConnectionService;
import android.util.Log;

/**
 * Control class for the self-managed connectionService app; allows CTS tests to perform operations
 * using the self-managed connectionService test app.
 */
public class CtsSelfManagedConnectionServiceControl extends Service {
    private static final String TAG = CtsSelfManagedConnectionServiceControl.class.getSimpleName();
    private static final String CONTROL_INTERFACE_ACTION =
            "android.telecom.cts.selfmanagedcstestapp.ACTION_SELF_MANAGED_CS_CONTROL";

    private Context mContext;
    private ConnectionServiceCallController mConnServiceCallController;
    private TelecomManager mTelecomManager;

    private final IBinder mCtsControl = new ICtsSelfManagedConnectionServiceControl.Stub() {
        @Override
        public void init() {
            mConnServiceCallController = ConnectionServiceCallController.getInstance();
            mTelecomManager = mContext.getSystemService(TelecomManager.class);
        }
        @Override
        public void deInit() {
            CtsSelfManagedConnectionService s = CtsSelfManagedConnectionService
                    .getConnectionService();
            if (s != null) s.tearDown();
        }
        @Override
        public boolean waitForBinding() {
            return CtsSelfManagedConnectionService.waitForBinding();
        }

        @Override
        public boolean waitForUpdate(int lock) {
            return CtsSelfManagedConnectionService.getConnectionService().waitForUpdate(lock);
        }

        @Override
        public void registerPhoneAccount(PhoneAccount phoneAccount) {
            if (mTelecomManager != null) {
                mTelecomManager.registerPhoneAccount(phoneAccount);
            }
        }

        @Override
        public void unregisterPhoneAccount(PhoneAccountHandle phoneAccountHandle) {
            if (mTelecomManager != null) {
                mTelecomManager.unregisterPhoneAccount(phoneAccountHandle);
            }
        }

        @Override
        public boolean isConnectionAvailable() {
            return mConnServiceCallController.isConnectionAvailable();
        }

        @Override
        public boolean waitOnHold() {
            return mConnServiceCallController.waitOnHold();
        }

        @Override
        public boolean waitOnUnHold() {
            return mConnServiceCallController.waitOnUnHold();
        }

        @Override
        public boolean waitOnDisconnect() {
            return mConnServiceCallController.waitOnDisconnect();
        }

        @Override
        public boolean initiateIncomingCall(PhoneAccountHandle handle, String uri) {
            return mConnServiceCallController.initiateIncomingCall(
                    mTelecomManager, handle, uri);
        }

        @Override
        public boolean placeOutgoingCall(PhoneAccountHandle handle, String uri) {
            return mConnServiceCallController.placeOutgoingCall(mTelecomManager, handle, uri);
        }

        @Override
        public boolean placeIncomingCall(PhoneAccountHandle handle, String uri, int videoState) {
            return mConnServiceCallController.placeIncomingCall(mTelecomManager,
                    handle, uri, videoState);
        }

        @Override
        public boolean isIncomingCall() {
            return mConnServiceCallController.isIncomingCall();
        }

        @Override
        public boolean waitOnAnswer() {
            return mConnServiceCallController.waitOnAnswer();
        }

        @Override
        public int getOnShowIncomingUiInvokeCounter() {
            return mConnServiceCallController.getOnShowIncomingUiInvokeCounter();
        }

        @Override
        public boolean getAudioModeIsVoip() {
            return mConnServiceCallController.getAudioModeIsVoip();
        }

        @Override
        public int getConnectionState() {
            return mConnServiceCallController.getState();
        }

        @Override
        public void setConnectionCapabilityNoHold() {
            mConnServiceCallController.setConnectionCapabilityNoHold();
        }

        @Override
        public void setConnectionActive() {
            mConnServiceCallController.setConnectionActive();
        }

        @Override
        public void disconnectConnection() {
            mConnServiceCallController.disconnectConnection();
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        if (CONTROL_INTERFACE_ACTION.equals(intent.getAction())) {
            mContext = this;
            Log.d(TAG, "onBind: return control interface.");
            return mCtsControl;
        }
        Log.d(TAG, "onBind: invalid intent.");
        return null;
    }
}
