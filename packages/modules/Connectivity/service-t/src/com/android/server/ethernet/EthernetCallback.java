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

package com.android.server.ethernet;

import android.net.EthernetNetworkManagementException;
import android.net.INetworkInterfaceOutcomeReceiver;
import android.os.RemoteException;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;

/** Convenience wrapper for INetworkInterfaceOutcomeReceiver */
@VisibleForTesting
public class EthernetCallback {
    private static final String TAG = EthernetCallback.class.getSimpleName();
    private final INetworkInterfaceOutcomeReceiver mReceiver;

    public EthernetCallback(INetworkInterfaceOutcomeReceiver receiver) {
        mReceiver = receiver;
    }

    /** Calls INetworkInterfaceOutcomeReceiver#onResult */
    public void onResult(String ifname) {
        try {
            if (mReceiver != null) {
                mReceiver.onResult(ifname);
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to report error to OutcomeReceiver", e);
        }
    }

    /** Calls INetworkInterfaceOutcomeReceiver#onError */
    public void onError(String msg) {
        try {
            if (mReceiver != null) {
                mReceiver.onError(new EthernetNetworkManagementException(msg));
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to report error to OutcomeReceiver", e);
        }
    }
}
