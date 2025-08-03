/*
 * Copyright (C) 2013 The Android Open Source Project
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
import android.net.wifi.ITrafficStateCallback;
import android.net.wifi.WifiManager;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.Log;
import android.util.SparseBooleanArray;

import com.android.wifi.resources.R;

import java.io.FileDescriptor;
import java.io.PrintWriter;

/**
 * Polls for traffic stats and notifies the clients
 */
public class WifiTrafficPoller {
    private static final String TAG = "WifiTrafficPoller";

    private long mTxPkts = 0;
    private long mRxPkts = 0;

    private int mLastActivity = -1;

    private final SparseBooleanArray mCallbackFirstInvocationTracker = new SparseBooleanArray();
    private final RemoteCallbackList<ITrafficStateCallback> mRegisteredCallbacks;
    private final Context mContext;

    public WifiTrafficPoller(Context context) {
        mContext = context;
        mRegisteredCallbacks = new RemoteCallbackList<>();
    }

    /**
     * Add a new callback to the traffic poller.
     */
    public void addCallback(ITrafficStateCallback callback) {
        mCallbackFirstInvocationTracker.put(callback.hashCode(), true);
        if (!mRegisteredCallbacks.register(callback)) {
            Log.e(TAG, "Failed to add callback");
        }
    }

    /**
     * Remove an existing callback from the traffic poller.
     */
    public void removeCallback(ITrafficStateCallback callback) {
        mRegisteredCallbacks.unregister(callback);
        mCallbackFirstInvocationTracker.delete(callback.hashCode());
    }

    /**
     * Notifies clients of data activity if the activity changed since the last update.
     */
    public void notifyOnDataActivity(long newTxPkts, long newRxPkts) {
        if (newTxPkts <= 0 && newRxPkts <= 0) {
            return;
        }

        long sent = newTxPkts - mTxPkts;
        long received = newRxPkts - mRxPkts;
        int dataActivity = WifiManager.TrafficStateCallback.DATA_ACTIVITY_NONE;
        int txPacketThreshold = mContext.getResources().getInteger(
                R.integer.config_wifiTrafficPollerTxPacketThreshold);
        int rxPacketThreshold = mContext.getResources().getInteger(
                R.integer.config_wifiTrafficPollerRxPacketThreshold);

        if (sent > txPacketThreshold) {
            dataActivity |= WifiManager.TrafficStateCallback.DATA_ACTIVITY_OUT;
        }
        if (received > rxPacketThreshold) {
            dataActivity |= WifiManager.TrafficStateCallback.DATA_ACTIVITY_IN;
        }

        int itemCount = mRegisteredCallbacks.beginBroadcast();
        for (int i = 0; i < itemCount; i++) {
            ITrafficStateCallback callback = mRegisteredCallbacks.getBroadcastItem(i);
            int callbackIdentifier = callback.hashCode();
            if (mCallbackFirstInvocationTracker.get(callbackIdentifier)
                    || dataActivity != mLastActivity) {
                mCallbackFirstInvocationTracker.put(callbackIdentifier, false);
                try {
                    callback.onStateChanged(dataActivity);
                } catch (RemoteException e) {
                    // Failed to reach, skip
                    // Client removal is handled in WifiService
                }
            }
        }
        mRegisteredCallbacks.finishBroadcast();

        mTxPkts = newTxPkts;
        mRxPkts = newRxPkts;
        mLastActivity = dataActivity;
    }

    /**
     * Dump method for traffic poller.
     */
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("mTxPkts " + mTxPkts);
        pw.println("mRxPkts " + mRxPkts);
        pw.println("mLastActivity " + mLastActivity);
        pw.println("mRegisteredCallbacks " + mRegisteredCallbacks.getRegisteredCallbackCount());
    }
}
