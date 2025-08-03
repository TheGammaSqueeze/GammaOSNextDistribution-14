/*
 * Copyright (C) 2016 The Android Open Source Project
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

/*
 * Bluetooth Pbap PCE StateMachine
 *                      (Disconnected)
 *                           |    ^
 *                   CONNECT |    | DISCONNECTED
 *                           V    |
 *                 (Connecting) (Disconnecting)
 *                           |    ^
 *                 CONNECTED |    | DISCONNECT
 *                           V    |
 *                        (Connected)
 *
 * Valid Transitions:
 * State + Event -> Transition:
 *
 * Disconnected + CONNECT -> Connecting
 * Connecting + CONNECTED -> Connected
 * Connecting + TIMEOUT -> Disconnecting
 * Connecting + DISCONNECT -> Disconnecting
 * Connected + DISCONNECT -> Disconnecting
 * Disconnecting + DISCONNECTED -> (Safe) Disconnected
 * Disconnecting + TIMEOUT -> (Force) Disconnected
 * Disconnecting + CONNECT : Defer Message
 *
 */
package com.android.bluetooth.pbapclient;

import static android.Manifest.permission.BLUETOOTH_CONNECT;
import static android.Manifest.permission.BLUETOOTH_PRIVILEGED;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothPbapClient;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothUuid;
import android.content.Context;
import android.content.Intent;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.UserManager;
import android.util.Log;

import com.android.bluetooth.BluetoothMetricsProto;
import com.android.bluetooth.Utils;
import com.android.bluetooth.btservice.AdapterService;
import com.android.bluetooth.btservice.MetricsLogger;
import com.android.bluetooth.btservice.ProfileService;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.IState;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;

import java.util.ArrayList;
import java.util.List;

class PbapClientStateMachine extends StateMachine {
    private static final String TAG = "PbapClientStateMachine";
    private static final boolean DBG = Log.isLoggable(TAG, Log.DEBUG);

    // Messages for handling connect/disconnect requests.
    private static final int MSG_DISCONNECT = 2;
    static final int MSG_SDP_COMPLETE = 9;

    // Messages for handling error conditions.
    private static final int MSG_CONNECT_TIMEOUT = 3;
    private static final int MSG_DISCONNECT_TIMEOUT = 4;

    // Messages for feedback from ConnectionHandler.
    static final int MSG_CONNECTION_COMPLETE = 5;
    static final int MSG_CONNECTION_FAILED = 6;
    static final int MSG_CONNECTION_CLOSED = 7;
    static final int MSG_RESUME_DOWNLOAD = 8;

    static final int CONNECT_TIMEOUT = 10000;
    static final int DISCONNECT_TIMEOUT = 3000;

    private final Object mLock;
    private State mDisconnected;
    private State mConnecting;
    private State mConnected;
    private State mDisconnecting;

    // mCurrentDevice may only be changed in Disconnected State.
    private final BluetoothDevice mCurrentDevice;
    private PbapClientService mService;
    private PbapClientConnectionHandler mConnectionHandler;
    private HandlerThread mHandlerThread = null;
    private UserManager mUserManager = null;

    // mMostRecentState maintains previous state for broadcasting transitions.
    private int mMostRecentState = BluetoothProfile.STATE_DISCONNECTED;

    PbapClientStateMachine(PbapClientService svc, BluetoothDevice device) {
        this(svc, device, null);
    }

    @VisibleForTesting
    PbapClientStateMachine(PbapClientService svc, BluetoothDevice device,
            PbapClientConnectionHandler connectionHandler) {
        super(TAG);

        mService = svc;
        mCurrentDevice = device;
        mConnectionHandler = connectionHandler;
        mLock = new Object();
        mUserManager = mService.getSystemService(UserManager.class);
        mDisconnected = new Disconnected();
        mConnecting = new Connecting();
        mDisconnecting = new Disconnecting();
        mConnected = new Connected();

        addState(mDisconnected);
        addState(mConnecting);
        addState(mDisconnecting);
        addState(mConnected);

        setInitialState(mConnecting);
    }

    class Disconnected extends State {
        @Override
        public void enter() {
            if (DBG) Log.d(TAG, "Enter Disconnected: " + getCurrentMessage().what);
            onConnectionStateChanged(mCurrentDevice, mMostRecentState,
                    BluetoothProfile.STATE_DISCONNECTED);
            mMostRecentState = BluetoothProfile.STATE_DISCONNECTED;
            quit();
        }
    }

    class Connecting extends State {

        @Override
        public void enter() {
            if (DBG) {
                Log.d(TAG, "Enter Connecting: " + getCurrentMessage().what);
            }
            onConnectionStateChanged(mCurrentDevice, mMostRecentState,
                    BluetoothProfile.STATE_CONNECTING);
            mCurrentDevice.sdpSearch(BluetoothUuid.PBAP_PSE);
            mMostRecentState = BluetoothProfile.STATE_CONNECTING;

            // Create a separate handler instance and thread for performing
            // connect/download/disconnect operations as they may be time consuming and error prone.
            HandlerThread handlerThread =
                    new HandlerThread("PBAP PCE handler", Process.THREAD_PRIORITY_BACKGROUND);
            handlerThread.start();
            Looper looper = handlerThread.getLooper();

            // Keeps mock handler from being overwritten in tests
            if (mConnectionHandler == null && looper != null) {
                mConnectionHandler =
                        new PbapClientConnectionHandler.Builder()
                                .setLooper(looper)
                                .setContext(mService)
                                .setClientSM(PbapClientStateMachine.this)
                                .setRemoteDevice(mCurrentDevice)
                                .build();
            }
            mHandlerThread = handlerThread;
            sendMessageDelayed(MSG_CONNECT_TIMEOUT, CONNECT_TIMEOUT);
        }

        @Override
        public boolean processMessage(Message message) {
            if (DBG) {
                Log.d(TAG, "Processing MSG " + message.what + " from " + this.getName());
            }
            switch (message.what) {
                case MSG_DISCONNECT:
                    if (message.obj instanceof BluetoothDevice && message.obj.equals(
                            mCurrentDevice)) {
                        removeMessages(MSG_CONNECT_TIMEOUT);
                        transitionTo(mDisconnecting);
                    }
                    break;

                case MSG_CONNECTION_COMPLETE:
                    removeMessages(MSG_CONNECT_TIMEOUT);
                    transitionTo(mConnected);
                    break;

                case MSG_CONNECTION_FAILED:
                case MSG_CONNECT_TIMEOUT:
                    removeMessages(MSG_CONNECT_TIMEOUT);
                    transitionTo(mDisconnecting);
                    break;

                case MSG_SDP_COMPLETE:
                    PbapClientConnectionHandler connectionHandler = mConnectionHandler;
                    if (connectionHandler != null) {
                        connectionHandler
                                .obtainMessage(PbapClientConnectionHandler.MSG_CONNECT, message.obj)
                                .sendToTarget();
                    }
                    break;

                default:
                    Log.w(TAG, "Received unexpected message while Connecting");
                    return NOT_HANDLED;
            }
            return HANDLED;
        }
    }

    class Disconnecting extends State {
        @Override
        public void enter() {
            if (DBG) Log.d(TAG, "Enter Disconnecting: " + getCurrentMessage().what);
            onConnectionStateChanged(mCurrentDevice, mMostRecentState,
                    BluetoothProfile.STATE_DISCONNECTING);
            mMostRecentState = BluetoothProfile.STATE_DISCONNECTING;
            PbapClientConnectionHandler connectionHandler = mConnectionHandler;
            if (connectionHandler != null) {
                connectionHandler
                        .obtainMessage(PbapClientConnectionHandler.MSG_DISCONNECT)
                        .sendToTarget();
            }
            sendMessageDelayed(MSG_DISCONNECT_TIMEOUT, DISCONNECT_TIMEOUT);
        }

        @Override
        public boolean processMessage(Message message) {
            if (DBG) {
                Log.d(TAG, "Processing MSG " + message.what + " from " + this.getName());
            }
            PbapClientConnectionHandler connectionHandler = mConnectionHandler;
            HandlerThread handlerThread = mHandlerThread;

            switch (message.what) {
                case MSG_CONNECTION_CLOSED:
                    removeMessages(MSG_DISCONNECT_TIMEOUT);
                    if (handlerThread != null) {
                        handlerThread.quitSafely();
                    }
                    transitionTo(mDisconnected);
                    break;

                case MSG_DISCONNECT:
                    deferMessage(message);
                    break;

                case MSG_DISCONNECT_TIMEOUT:
                    Log.w(TAG, "Disconnect Timeout, Forcing");
                    if (connectionHandler != null) {
                        connectionHandler.abort();
                    }
                    if (handlerThread != null) {
                        handlerThread.quitSafely();
                    }
                    transitionTo(mDisconnected);
                    break;

                case MSG_RESUME_DOWNLOAD:
                    // Do nothing.
                    break;

                default:
                    Log.w(TAG, "Received unexpected message while Disconnecting");
                    return NOT_HANDLED;
            }
            return HANDLED;
        }
    }

    class Connected extends State {
        @Override
        public void enter() {
            if (DBG) Log.d(TAG, "Enter Connected: " + getCurrentMessage().what);
            onConnectionStateChanged(mCurrentDevice, mMostRecentState,
                    BluetoothProfile.STATE_CONNECTED);
            mMostRecentState = BluetoothProfile.STATE_CONNECTED;
            downloadIfReady();
        }

        @Override
        public boolean processMessage(Message message) {
            if (DBG) {
                Log.d(TAG, "Processing MSG " + message.what + " from " + this.getName());
            }
            switch (message.what) {
                case MSG_DISCONNECT:
                    if ((message.obj instanceof BluetoothDevice)
                            && ((BluetoothDevice) message.obj).equals(mCurrentDevice)) {
                        transitionTo(mDisconnecting);
                    }
                    break;

                case MSG_RESUME_DOWNLOAD:
                    downloadIfReady();
                    break;

                default:
                    Log.w(TAG, "Received unexpected message while Connected");
                    return NOT_HANDLED;
            }
            return HANDLED;
        }
    }

    /**
     * Trigger a contacts download if the user is unlocked and our accounts are available to us
     */
    private void downloadIfReady() {
        boolean userReady = mUserManager.isUserUnlocked();
        boolean accountServiceReady = mService.isAuthenticationServiceReady();
        if (!userReady || !accountServiceReady) {
            Log.w(TAG, "Cannot download contacts yet, userReady=" + userReady
                    + ", accountServiceReady=" + accountServiceReady);
            return;
        }
        PbapClientConnectionHandler connectionHandler = mConnectionHandler;
        if (connectionHandler != null) {
            connectionHandler
                    .obtainMessage(PbapClientConnectionHandler.MSG_DOWNLOAD)
                    .sendToTarget();
        }
    }

    private void onConnectionStateChanged(BluetoothDevice device, int prevState, int state) {
        if (device == null) {
            Log.w(TAG, "onConnectionStateChanged with invalid device");
            return;
        }
        if (prevState != state && state == BluetoothProfile.STATE_CONNECTED) {
            MetricsLogger.logProfileConnectionEvent(BluetoothMetricsProto.ProfileId.PBAP_CLIENT);
        }
        Log.d(TAG, "Connection state " + device + ": " + prevState + "->" + state);
        AdapterService adapterService = AdapterService.getAdapterService();
        if (adapterService != null) {
            adapterService.updateProfileConnectionAdapterProperties(
                    device, BluetoothProfile.PBAP_CLIENT, state, prevState);
        }
        Intent intent = new Intent(BluetoothPbapClient.ACTION_CONNECTION_STATE_CHANGED);
        intent.putExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, prevState);
        intent.putExtra(BluetoothProfile.EXTRA_STATE, state);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        mService.sendBroadcastMultiplePermissions(intent,
                new String[] {BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED},
                Utils.getTempBroadcastOptions());
    }

    public void disconnect(BluetoothDevice device) {
        if (DBG) Log.d(TAG, "Disconnect Request " + device);
        sendMessage(MSG_DISCONNECT, device);
    }

    public void tryDownloadIfConnected() {
        sendMessage(MSG_RESUME_DOWNLOAD);
    }

    void doQuit() {
        PbapClientConnectionHandler connectionHandler = mConnectionHandler;
        if (connectionHandler != null) {
            connectionHandler.abort();
            mConnectionHandler = null;
        }

        HandlerThread handlerThread = mHandlerThread;
        if (handlerThread != null) {
            handlerThread.quitSafely();
            mHandlerThread = null;
        }
        quitNow();
    }

    @Override
    protected void onQuitting() {
        mService.cleanupDevice(mCurrentDevice);
    }

    public int getConnectionState() {
        IState currentState = getCurrentState();
        if (currentState instanceof Disconnected) {
            return BluetoothProfile.STATE_DISCONNECTED;
        } else if (currentState instanceof Connecting) {
            return BluetoothProfile.STATE_CONNECTING;
        } else if (currentState instanceof Connected) {
            return BluetoothProfile.STATE_CONNECTED;
        } else if (currentState instanceof Disconnecting) {
            return BluetoothProfile.STATE_DISCONNECTING;
        }
        Log.w(TAG, "Unknown State");
        return BluetoothProfile.STATE_DISCONNECTED;
    }

    public List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
        int clientState;
        BluetoothDevice currentDevice;
        synchronized (mLock) {
            clientState = getConnectionState();
            currentDevice = getDevice();
        }
        List<BluetoothDevice> deviceList = new ArrayList<BluetoothDevice>();
        for (int state : states) {
            if (clientState == state) {
                if (currentDevice != null) {
                    deviceList.add(currentDevice);
                }
            }
        }
        return deviceList;
    }

    public int getConnectionState(BluetoothDevice device) {
        if (device == null) {
            return BluetoothProfile.STATE_DISCONNECTED;
        }
        synchronized (mLock) {
            if (device.equals(mCurrentDevice)) {
                return getConnectionState();
            }
        }
        return BluetoothProfile.STATE_DISCONNECTED;
    }


    public BluetoothDevice getDevice() {
        /*
         * Disconnected is the only state where device can change, and to prevent the race
         * condition of reporting a valid device while disconnected fix the report here.  Note that
         * Synchronization of the state and device is not possible with current state machine
         * desingn since the actual Transition happens sometime after the transitionTo method.
         */
        if (getCurrentState() instanceof Disconnected) {
            return null;
        }
        return mCurrentDevice;
    }

    Context getContext() {
        return mService;
    }

    public void dump(StringBuilder sb) {
        ProfileService.println(sb, "mCurrentDevice: " + mCurrentDevice.getAddress() + "("
                + Utils.getName(mCurrentDevice) + ") " + this.toString());
    }
}
