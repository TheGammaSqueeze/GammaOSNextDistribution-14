/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.server.wifi;

import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.android.internal.util.AsyncChannel;
import com.android.server.wifi.p2p.WifiP2pServiceImpl;

/**
 * Used by {@link ClientModeImpl} to communicate with
 * {@link com.android.server.wifi.p2p.WifiP2pService}.
 *
 * TODO(b/159060934): need to think about how multiple STAs interact with P2P
 */
public class WifiP2pConnection {
    private static final String TAG = "WifiP2pConnection";

    private final Context mContext;
    private final Handler mHandler;
    private final ActiveModeWarden mActiveModeWarden;
    /** Channel for sending replies. */
    private final AsyncChannel mReplyChannel = new AsyncChannel();
    /** Used to initiate a connection with WifiP2pService */
    private AsyncChannel mWifiP2pChannel;
    private boolean mTemporarilyDisconnectWifi = false;

    /** Used to check if P2P state machine is in waitingState */
    private boolean mWaitingState = false;

    public WifiP2pConnection(Context context, Looper looper, ActiveModeWarden activeModeWarden) {
        mContext = context;
        mHandler = new P2pHandler(looper);
        mActiveModeWarden = activeModeWarden;
    }

    private void sendMessageToAllClientModeImpls(Message msg) {
        for (ClientModeManager clientModeManager : mActiveModeWarden.getClientModeManagers()) {
            // Need to make a copy of the message, or else MessageQueue will complain that the
            // original message is already in use.
            clientModeManager.sendMessageToClientModeImpl(Message.obtain(msg));
        }
    }

    private class P2pHandler extends Handler {
        P2pHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case AsyncChannel.CMD_CHANNEL_HALF_CONNECTED: {
                    AsyncChannel ac = (AsyncChannel) msg.obj;
                    if (ac == mWifiP2pChannel) {
                        if (msg.arg1 == AsyncChannel.STATUS_SUCCESSFUL) {
                            // Handler also has a sendMessage() method, but we want to call the
                            // sendMessage() method on WifiP2pConnection.
                            WifiP2pConnection.this
                                    .sendMessage(AsyncChannel.CMD_CHANNEL_FULL_CONNECTION);
                        } else {
                            // TODO(b/34283611): We should probably do some cleanup or attempt a
                            //  retry
                            Log.e(TAG, "WifiP2pService connection failure, error=" + msg.arg1);
                        }
                    } else {
                        Log.e(TAG, "got HALF_CONNECTED for unknown channel");
                    }
                } break;
                case AsyncChannel.CMD_CHANNEL_DISCONNECTED: {
                    AsyncChannel ac = (AsyncChannel) msg.obj;
                    if (ac == mWifiP2pChannel) {
                        Log.e(TAG, "WifiP2pService channel lost, message.arg1 =" + msg.arg1);
                        // TODO(b/34283611): Re-establish connection to state machine after a delay
                        // mWifiP2pChannel.connect(mContext, getHandler(),
                        // mWifiP2pManager.getMessenger());
                    }
                } break;
                case WifiP2pServiceImpl.DISCONNECT_WIFI_REQUEST: {
                    mTemporarilyDisconnectWifi = (msg.arg1 == 1);
                    if (mActiveModeWarden.getClientModeManagers().isEmpty()) {
                        // no active client mode managers, so request is trivially satisfied
                        replyToMessage(msg, WifiP2pServiceImpl.DISCONNECT_WIFI_RESPONSE);
                    } else {
                        // need to tell all client mode managers to disconnect
                        sendMessageToAllClientModeImpls(msg);
                    }
                } break;
                default: {
                    // Assume P2P message, forward to all ClientModeImpl instances
                    sendMessageToAllClientModeImpls(msg);
                } break;
            }
        }
    }

    /** Setup the connection to P2P Service after boot is complete. */
    public void handleBootCompleted() {
        if (!isP2pSupported()) return;

        WifiP2pManager p2pManager = mContext.getSystemService(WifiP2pManager.class);
        if (p2pManager == null) return;

        mWifiP2pChannel = new AsyncChannel();
        mWifiP2pChannel.connect(mContext, mHandler, p2pManager.getP2pStateMachineMessenger());
    }

    private boolean isP2pSupported() {
        return mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT);
    }

    /** Return true if the connection to P2P service is established, false otherwise. */
    public boolean isConnected() {
        return mWifiP2pChannel != null;
    }

    /** Send a message to P2P service. */
    public boolean sendMessage(int what) {
        if (mWifiP2pChannel == null) {
            Log.e(TAG, "Tried to sendMessage to uninitialized mWifiP2pChannel!", new Throwable());
            return false;
        }
        mWifiP2pChannel.sendMessage(what);
        return true;
    }

    /** Send a message to P2P service. */
    public boolean sendMessage(int what, int arg1) {
        if (mWifiP2pChannel == null) {
            Log.e(TAG, "Tried to sendMessage to uninitialized mWifiP2pChannel!", new Throwable());
            return false;
        }
        mWifiP2pChannel.sendMessage(what, arg1);
        return true;
    }

    /** Send a message to P2P service. */
    public boolean sendMessage(int what, int arg1, int arg2) {
        if (mWifiP2pChannel == null) {
            Log.e(TAG, "Tried to sendMessage to uninitialized mWifiP2pChannel!", new Throwable());
            return false;
        }
        mWifiP2pChannel.sendMessage(what, arg1, arg2);
        return true;
    }

    /**
     * State machine initiated requests can have replyTo set to null, indicating
     * there are no recipients, we ignore those reply actions.
     */
    public void replyToMessage(Message msg, int what) {
        if (msg.replyTo == null) return;
        Message dstMsg = obtainMessageWithWhatAndArg2(msg, what);
        mReplyChannel.replyToMessage(msg, dstMsg);
    }

    /**
     * arg2 on the source message has a unique id that needs to be retained in replies
     * to match the request
     * <p>see WifiManager for details
     */
    private Message obtainMessageWithWhatAndArg2(Message srcMsg, int what) {
        Message msg = Message.obtain();
        msg.what = what;
        msg.arg2 = srcMsg.arg2;
        return msg;
    }

    /** Whether P2P service requested that we temporarily disconnect from Wifi */
    public boolean shouldTemporarilyDisconnectWifi() {
        return mTemporarilyDisconnectWifi;
    }

    public void setP2pInWaitingState(boolean inWaitingState) {
        mWaitingState = inWaitingState;
    }

    /** whether the P2P state machine is in waitingState for user response to create interface */
    public boolean isP2pInWaitingState() {
        return mWaitingState;
    }
}
