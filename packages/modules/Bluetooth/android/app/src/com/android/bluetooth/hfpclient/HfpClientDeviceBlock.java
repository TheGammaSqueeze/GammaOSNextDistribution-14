/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.bluetooth.hfpclient;

import android.bluetooth.BluetoothDevice;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.telecom.Connection;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccount;
import android.telecom.TelecomManager;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Helper class that manages the call handling for one device. HfpClientConnectionService holds a
// list of such blocks and routes traffic from the UI.
//
// Lifecycle of a Device Block is managed entirely by the Service which creates it. In essence it
// has only the active state otherwise the block should be GCed.
public class HfpClientDeviceBlock {
    private static final String TAG = "HfpClientDeviceBlock";
    private static final boolean DBG = false;

    private static final String KEY_SCO_STATE = "com.android.bluetooth.hfpclient.SCO_STATE";

    private final BluetoothDevice mDevice;
    private final PhoneAccount mPhoneAccount;
    private final Map<UUID, HfpClientConnection> mConnections = new HashMap<>();
    private final TelecomManager mTelecomManager;
    private final HfpClientConnectionService mConnServ;
    private HfpClientConference mConference;
    private Bundle mScoState;
    private final HeadsetClientServiceInterface mServiceInterface;

    HfpClientDeviceBlock(BluetoothDevice device, HfpClientConnectionService connServ,
            HeadsetClientServiceInterface serviceInterface) {
        mDevice = device;
        mConnServ = connServ;
        mServiceInterface = serviceInterface;
        mPhoneAccount = mConnServ.createAccount(device);
        mTelecomManager = mConnServ.getSystemService(TelecomManager.class);

        // Register the phone account since block is created only when devices are connected
        mTelecomManager.registerPhoneAccount(mPhoneAccount);
        mTelecomManager.enablePhoneAccount(mPhoneAccount.getAccountHandle(), true);
        mTelecomManager.setUserSelectedOutgoingPhoneAccount(mPhoneAccount.getAccountHandle());

        mScoState = getScoStateFromDevice(device);
        debug("SCO state = " + mScoState);


        List<HfpClientCall> calls = mServiceInterface.getCurrentCalls(mDevice);
        debug("Got calls " + calls);
        if (calls == null) {
            // We can get null as a return if we are not connected. Hence there may
            // be a race in getting the broadcast and HFP Client getting
            // disconnected before broadcast gets delivered.
            warn("Got connected but calls were null, ignoring the broadcast");
            return;
        }

        for (HfpClientCall call : calls) {
            handleCall(call);
        }
    }

    public BluetoothDevice getDevice() {
        return mDevice;
    }

    public int getAudioState() {
        return mScoState.getInt(KEY_SCO_STATE);
    }

    /* package */ Map<UUID, HfpClientConnection> getCalls() {
        return mConnections;
    }

    synchronized HfpClientConnection onCreateIncomingConnection(UUID callUuid) {
        HfpClientConnection connection = mConnections.get(callUuid);
        if (connection != null) {
            connection.onAdded();
            return connection;
        } else {
            error("Call " + callUuid + " ignored: connection does not exist");
            return null;
        }
    }

    HfpClientConnection onCreateOutgoingConnection(Uri address) {
        HfpClientConnection connection = buildConnection(null, address);
        if (connection != null) {
            connection.onAdded();
        }
        return connection;
    }

    synchronized void onAudioStateChange(int newState, int oldState) {
        debug("Call audio state changed " + oldState + " -> " + newState);
        mScoState.putInt(KEY_SCO_STATE, newState);

        for (HfpClientConnection connection : mConnections.values()) {
            connection.setExtras(mScoState);
        }
        if (mConference != null) {
            mConference.setExtras(mScoState);
        }
    }

    synchronized HfpClientConnection onCreateUnknownConnection(UUID callUuid) {
        HfpClientConnection connection = mConnections.get(callUuid);

        if (connection != null) {
            connection.onAdded();
            return connection;
        } else {
            error("Call " + callUuid + " ignored: connection does not exist");
            return null;
        }
    }

    synchronized void onConference(Connection connection1, Connection connection2) {
        if (mConference == null) {
            mConference = new HfpClientConference(mDevice, mPhoneAccount.getAccountHandle(),
                    mServiceInterface);
            mConference.setExtras(mScoState);
        }

        if (connection1.getConference() == null) {
            mConference.addConnection(connection1);
        }

        if (connection2.getConference() == null) {
            mConference.addConnection(connection2);
        }
    }

    // Remove existing calls and the phone account associated, the object will get garbage
    // collected soon
    synchronized void cleanup() {
        debug("Resetting state for device " + mDevice);
        disconnectAll();
        mTelecomManager.unregisterPhoneAccount(mPhoneAccount.getAccountHandle());
    }

    // Handle call change
    synchronized void handleCall(HfpClientCall call) {
        debug("Got call " + call.toString());

        HfpClientConnection connection = findConnectionKey(call);

        // We need to have special handling for calls that mysteriously convert from
        // DISCONNECTING -> ACTIVE/INCOMING state. This can happen for PTS (b/31159015).
        // We terminate the previous call and create a new one here.
        if (connection != null && isDisconnectingToActive(connection, call)) {
            connection.close(DisconnectCause.ERROR);
            mConnections.remove(call.getUUID());
            connection = null;
        }

        if (connection != null) {
            connection.updateCall(call);
            connection.handleCallChanged();
        }

        if (connection == null) {
            // Create the connection here, trigger Telecom to bind to us.
            buildConnection(call, null);

            // Depending on where this call originated make it an incoming call or outgoing
            // (represented as unknown call in telecom since). Since HfpClientCall is a
            // parcelable we simply pack the entire object in there.
            Bundle b = new Bundle();
            if (call.getState() == HfpClientCall.CALL_STATE_DIALING
                    || call.getState() == HfpClientCall.CALL_STATE_ALERTING
                    || call.getState() == HfpClientCall.CALL_STATE_ACTIVE
                    || call.getState() == HfpClientCall.CALL_STATE_HELD) {
                // This is an outgoing call. Even if it is an active call we do not have a way of
                // putting that parcelable in a seaprate field.
                b.putParcelable(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS,
                        new ParcelUuid(call.getUUID()));
                mTelecomManager.addNewUnknownCall(mPhoneAccount.getAccountHandle(), b);
            } else if (call.getState() == HfpClientCall.CALL_STATE_INCOMING
                    || call.getState() == HfpClientCall.CALL_STATE_WAITING) {
                // This is an incoming call.
                b.putParcelable(TelecomManager.EXTRA_INCOMING_CALL_EXTRAS,
                        new ParcelUuid(call.getUUID()));
                b.putBoolean(TelecomManager.EXTRA_CALL_HAS_IN_BAND_RINGTONE, call.isInBandRing());
                mTelecomManager.addNewIncomingCall(mPhoneAccount.getAccountHandle(), b);
            }
        } else if (call.getState() == HfpClientCall.CALL_STATE_TERMINATED) {
            debug("Removing call " + call);
            mConnections.remove(call.getUUID());
        }

        updateConferenceableConnections();
    }

    // Find the connection specified by the key, also update the key with ID if present.
    private synchronized HfpClientConnection findConnectionKey(HfpClientCall call) {
        debug("findConnectionKey local key set " + mConnections.toString());
        return mConnections.get(call.getUUID());
    }

    // Disconnect all calls
    private void disconnectAll() {
        for (HfpClientConnection connection : mConnections.values()) {
            connection.onHfpDisconnected();
        }

        mConnections.clear();

        if (mConference != null) {
            mConference.destroy();
            mConference = null;
        }
    }

    private boolean isDisconnectingToActive(HfpClientConnection prevConn,
            HfpClientCall newCall) {
        debug("prevConn " + prevConn.isClosing() + " new call " + newCall.getState());
        if (prevConn.isClosing() && prevConn.getCall().getState() != newCall.getState()
                && newCall.getState() != HfpClientCall.CALL_STATE_TERMINATED) {
            return true;
        }
        return false;
    }

    private synchronized HfpClientConnection buildConnection(HfpClientCall call,
            Uri number) {
        if (call == null && number == null) {
            error("Both call and number cannot be null.");
            return null;
        }

        debug("Creating connection on " + mDevice + " for " + call + "/" + number);

        HfpClientConnection connection = (call != null
                ? new HfpClientConnection(mDevice, call, mConnServ, mServiceInterface)
                : new HfpClientConnection(mDevice, number, mConnServ, mServiceInterface));
        connection.setExtras(mScoState);

        debug("Connection extras = " + connection.getExtras().toString());

        if (connection.getState() != Connection.STATE_DISCONNECTED) {
            mConnections.put(connection.getUUID(), connection);
        }

        return connection;
    }

    // Updates any conferencable connections.
    private void updateConferenceableConnections() {
        boolean addConf = false;
        debug("Existing connections: " + mConnections + " existing conference " + mConference);

        // If we have an existing conference call then loop through all connections and update any
        // connections that may have switched from conference -> non-conference.
        if (mConference != null) {
            for (Connection confConn : mConference.getConnections()) {
                if (!((HfpClientConnection) confConn).inConference()) {
                    debug("Removing connection " + confConn + " from conference.");
                    mConference.removeConnection(confConn);
                }
            }
        }

        // If we have connections that are not already part of the conference then add them.
        // NOTE: addConnection takes care of duplicates (by mem addr) and the lifecycle of a
        // connection is maintained by the UUID.
        for (Connection otherConn : mConnections.values()) {
            if (((HfpClientConnection) otherConn).inConference()) {
                // If this is the first connection with conference, create the conference first.
                if (mConference == null) {
                    mConference = new HfpClientConference(mDevice, mPhoneAccount.getAccountHandle(),
                            mServiceInterface);
                    mConference.setExtras(mScoState);
                }
                if (mConference.addConnection(otherConn)) {
                    debug("Adding connection " + otherConn + " to conference.");
                    addConf = true;
                }
            }
        }

        // If we have no connections in the conference we should simply end it.
        if (mConference != null && mConference.getConnections().size() == 0) {
            debug("Conference has no connection, destroying");
            mConference.setDisconnected(new DisconnectCause(DisconnectCause.LOCAL));
            mConference.destroy();
            mConference = null;
        }

        // If we have a valid conference and not previously added then add it.
        if (mConference != null && addConf) {
            debug("Adding conference to stack.");
            mConnServ.addConference(mConference);
        }
    }

    private Bundle getScoStateFromDevice(BluetoothDevice device) {
        Bundle bundle = new Bundle();

        HeadsetClientService headsetClientService = HeadsetClientService.getHeadsetClientService();
        if (headsetClientService == null) {
            return bundle;
        }

        bundle.putInt(KEY_SCO_STATE, headsetClientService.getAudioState(device));

        return bundle;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("<HfpClientDeviceBlock");
        sb.append(" device=" + mDevice);
        sb.append(" account=" + mPhoneAccount);
        sb.append(" connections=[");
        boolean first = true;
        for (HfpClientConnection connection :  mConnections.values()) {
            if (!first) {
                sb.append(", ");
            }
            sb.append(connection.toString());
            first = false;
        }
        sb.append("]");
        sb.append(" conference=" + mConference);
        sb.append(">");
        return sb.toString();
    }

    /**
     * Factory class for {@link HfpClientDeviceBlock}
     */
    public static class Factory {
        private static Factory sInstance = new Factory();

        @VisibleForTesting
        static void setInstance(Factory instance) {
            sInstance = instance;
        }

        /**
         * Returns an instance of {@link HfpClientDeviceBlock}
         */
        public static HfpClientDeviceBlock build(BluetoothDevice device,
                HfpClientConnectionService connServ,
                HeadsetClientServiceInterface serviceInterface) {
            return sInstance.buildInternal(device, connServ, serviceInterface);
        }

        protected HfpClientDeviceBlock buildInternal(BluetoothDevice device,
                HfpClientConnectionService connServ,
                HeadsetClientServiceInterface serviceInterface) {
            return new HfpClientDeviceBlock(device, connServ, serviceInterface);
        }

    }

    // Per-Device logging

    public void debug(String message) {
        if (DBG) {
            Log.d(TAG, "[device=" + mDevice + "] " + message);
        }
    }

    public void warn(String message) {
        Log.w(TAG, "[device=" + mDevice + "] " + message);
    }

    public void error(String message) {
        Log.e(TAG, "[device=" + mDevice + "] " + message);
    }
}
