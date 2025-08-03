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

package androidx.core.uwb.backend.impl;

import static androidx.core.uwb.backend.impl.internal.Utils.STATUS_OK;
import static androidx.core.uwb.backend.impl.internal.Utils.TAG;

import android.os.RemoteException;
import android.util.Log;

import androidx.core.uwb.backend.IRangingSessionCallback;
import androidx.core.uwb.backend.RangingControleeParameters;
import androidx.core.uwb.backend.RangingParameters;
import androidx.core.uwb.backend.UwbAddress;
import androidx.core.uwb.backend.UwbComplexChannel;
import androidx.core.uwb.backend.impl.internal.RangingControlee;
import androidx.core.uwb.backend.impl.internal.UwbServiceImpl;

import java.util.concurrent.Executors;

/** This class implement the operations of a uwb controlee. */
public class UwbControleeClient extends UwbClient {

    public UwbControleeClient(RangingControlee rangingControlee, UwbServiceImpl uwbService) {
        super(rangingControlee, uwbService);
    }

    @Override
    public UwbComplexChannel getComplexChannel() throws RemoteException {
        return null;
    }

    @Override
    public void startRanging(RangingParameters parameters, IRangingSessionCallback callback)
            throws RemoteException {
        setRangingParameters(parameters);
        int status = mDevice.startRanging(
                convertCallback(callback), Executors.newSingleThreadExecutor());
        if (status != STATUS_OK) {
            Log.w(TAG, String.format("Ranging start failed with status %d", status));
        }
    }

    @Override
    public void stopRanging(IRangingSessionCallback callback) throws RemoteException {
        int status = mDevice.stopRanging();
        if (status != STATUS_OK) {
            Log.w(TAG, String.format("Ranging stop failed with status %d", status));
        }
    }

    @Override
    public void addControlee(UwbAddress address) throws RemoteException {
    }

    @Override
    public void addControleeWithSessionParams(RangingControleeParameters params)
            throws RemoteException {
    }

    @Override
    public void removeControlee(UwbAddress address) throws RemoteException {
    }

    @Override
    public void reconfigureRangingInterval(int intervalSkipCount) {
    }

    @Override
    public int getInterfaceVersion() throws RemoteException {
        return 0;
    }

    @Override
    public String getInterfaceHash() throws RemoteException {
        return null;
    }
}
