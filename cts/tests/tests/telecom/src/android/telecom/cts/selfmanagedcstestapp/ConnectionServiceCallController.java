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

import android.net.Uri;
import android.os.Bundle;
import android.telecom.Connection;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.telecom.cts.CtsSelfManagedConnectionService;
import android.telecom.cts.SelfManagedConnection;
import android.telecom.cts.TestUtils;
import android.util.Log;

public class ConnectionServiceCallController {
    private static final String TAG = ConnectionServiceCallController.class.getSimpleName();
    private static final Uri TEST_ADDRESS_1 = Uri.fromParts("sip", "call1@test.com", null);
    private static final Uri TEST_ADDRESS_2 = Uri.fromParts("tel", "6505551212", null);

    private static ConnectionServiceCallController sConnServiceCallController = null;

    private SelfManagedConnection mConnection = null;

    private ConnectionServiceCallController() {
    }

    public static ConnectionServiceCallController getInstance() {
        if (sConnServiceCallController == null) {
            sConnServiceCallController = new ConnectionServiceCallController();
        }

        return sConnServiceCallController;
    }

    public boolean waitForUpdate(int lock) {
        return CtsSelfManagedConnectionService.getConnectionService().waitForUpdate(lock);
    }

    public static boolean waitForBinding() {
        return CtsSelfManagedConnectionService.waitForBinding();
    }

    public boolean isConnectionAvailable() {
        return (mConnection != null) ? true : false;
    }

    public boolean waitOnHold() {
        return mConnection.waitOnHold();
    }

    public boolean waitOnUnHold() {
        return mConnection.waitOnUnHold();
    }

    public boolean waitOnDisconnect() {
        return mConnection.waitOnDisconnect();
    }

    public boolean initiateIncomingCall(TelecomManager telecomManager,
            PhoneAccountHandle handle, String uri) {
        Bundle extras = new Bundle();
        Uri address = Uri.parse(uri);
        extras.putParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS, address);
        telecomManager.addNewIncomingCall(handle, extras);

        mConnection = TestUtils.waitForAndGetConnection(address);

        if (mConnection == null) {
            Log.d(TAG, "Self-Managed Connection should NOT be null.");
            return false;
        }
        return true;
    }

    public boolean placeOutgoingCall(TelecomManager telecomManager,
            PhoneAccountHandle handle, String uri) {
        if (!telecomManager.isOutgoingCallPermitted(handle)) {
            Log.d(TAG, "outgoing call not permitted");
            return false;
        }

        Uri address = Uri.parse(uri);
        // Inform telecom of new incoming self-managed connection.
        Bundle extras = new Bundle();
        extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle);
        telecomManager.placeCall(address, extras);

        mConnection = TestUtils.waitForAndGetConnection(address);

        if (mConnection == null) {
            Log.d(TAG, "Self-Managed Connection should NOT be null.");
            return false;
        }

        return true;
    }

    public boolean placeIncomingCall(TelecomManager telecomManager,
            PhoneAccountHandle handle, String uri, int videoState) {
        if (!telecomManager.isIncomingCallPermitted(handle)) {
            Log.d(TAG, "incoming call not permitted");
            return false;
        }

        // Inform telecom of new incoming self-managed connection.
        Bundle extras = new Bundle();
        Uri address = Uri.parse(uri);
        extras.putParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS, address);

        if (!VideoProfile.isAudioOnly(videoState)) {
            extras.putInt(TelecomManager.EXTRA_INCOMING_VIDEO_STATE, videoState);
        }

        telecomManager.addNewIncomingCall(handle, extras);

        if (!CtsSelfManagedConnectionService.waitForBinding()) {
            Log.d(TAG, "Could not bind to Self-Managed ConnectionService");
            return false;
        }

        mConnection = TestUtils.waitForAndGetConnection(address);
        if (mConnection == null) {
            Log.d(TAG, "Self-Managed Connection should NOT be null.");
            return false;
        }

        return true;
    }

    public boolean isIncomingCall() {
        return mConnection.isIncomingCall();
    }

    public boolean waitOnAnswer() {
        return mConnection.waitOnAnswer();
    }

    public int getOnShowIncomingUiInvokeCounter() {
        return mConnection.getOnShowIncomingUiInvokeCounter().getInvokeCount();
    }

    public boolean getAudioModeIsVoip() {
        return mConnection.getAudioModeIsVoip();
    }

    public int getState() {
        return mConnection.getState();
    }

    public void setConnectionCapabilityNoHold() {
        int capabilities = mConnection.getConnectionCapabilities();
        capabilities &= ~Connection.CAPABILITY_HOLD;
        mConnection.setConnectionCapabilities(capabilities);
    }

    public void setConnectionActive() {
        mConnection.setActive();
    }

    public void disconnectConnection() {
        mConnection.setDisconnected(new DisconnectCause(DisconnectCause.LOCAL));
        mConnection.disconnectAndDestroy();
        mConnection = null;
    }
}
