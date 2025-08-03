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

import android.annotation.TargetApi;
import android.os.RemoteException;
import android.util.Log;

import androidx.core.uwb.backend.IRangingSessionCallback;
import androidx.core.uwb.backend.RangingControleeParameters;
import androidx.core.uwb.backend.RangingParameters;
import androidx.core.uwb.backend.UwbAddress;
import androidx.core.uwb.backend.UwbComplexChannel;
import androidx.core.uwb.backend.impl.internal.RangingController;
import androidx.core.uwb.backend.impl.internal.UwbServiceImpl;

import java.util.concurrent.Executors;

/** This class implement the operations of a uwb controller. */
public class UwbControllerClient extends UwbClient {

    public UwbControllerClient(RangingController rangingController, UwbServiceImpl uwbService) {
        super(rangingController, uwbService);
    }

    @Override
    public UwbComplexChannel getComplexChannel() throws RemoteException {
        RangingController controller = (RangingController) mDevice;
        androidx.core.uwb.backend.impl.internal.UwbComplexChannel channel =
                controller.getComplexChannel();
        UwbComplexChannel uwbComplexChannel = new UwbComplexChannel();
        uwbComplexChannel.channel = channel.getChannel();
        uwbComplexChannel.preambleIndex = channel.getPreambleIndex();
        return uwbComplexChannel;
    }

    @Override
    public void startRanging(RangingParameters parameters, IRangingSessionCallback callback)
            throws RemoteException {
        setRangingParameters(parameters);
        int status = ((RangingController) mDevice)
                .startRanging(convertCallback(callback), Executors.newSingleThreadExecutor());
        if (status != STATUS_OK) {
            Log.w(TAG, String.format("Ranging start failed with status %d", status));
        }
    }

    @Override
    public void stopRanging(IRangingSessionCallback callback) throws RemoteException {
        int status = ((RangingController) mDevice).stopRanging();
        if (status != STATUS_OK) {
            Log.w(TAG, String.format("Ranging stop failed with status %d", status));
        }
    }

    @Override
    public void addControlee(UwbAddress address) throws RemoteException {
        androidx.core.uwb.backend.impl.internal.UwbAddress uwbAddress =
                androidx.core.uwb.backend.impl.internal.UwbAddress.fromBytes(address.address);
        int status = ((RangingController) mDevice).addControlee(uwbAddress);
        if (status != STATUS_OK) {
            Log.w(TAG, String.format("Adding controlee failed with status %d", status));
        }
    }


    @Override
    public void addControleeWithSessionParams(RangingControleeParameters params)
            throws RemoteException {
        androidx.core.uwb.backend.impl.internal.UwbAddress uwbAddress =
                androidx.core.uwb.backend.impl.internal.UwbAddress
                        .fromBytes(params.address.address);
        androidx.core.uwb.backend.impl.internal.RangingControleeParameters controleeParameters =
                new androidx.core.uwb.backend.impl.internal.RangingControleeParameters(
                        uwbAddress, params.subSessionId, params.subSessionKey);
        int status = ((RangingController) mDevice)
                .addControleeWithSessionParams(controleeParameters);
        if (status != STATUS_OK) {
            Log.w(TAG, String.format("Adding controlee failed with status %d", status));
        }
    }

    @Override
    public void removeControlee(UwbAddress address) throws RemoteException {
        androidx.core.uwb.backend.impl.internal.UwbAddress uwbAddress =
                androidx.core.uwb.backend.impl.internal.UwbAddress.fromBytes(address.address);
        int status = ((RangingController) mDevice).removeControlee(uwbAddress);
        if (status != STATUS_OK) {
            Log.w(TAG, String.format("Removing controlee failed with status %d", status));
        }
    }

    @Override
    @TargetApi(31)
    public void reconfigureRangingInterval(int intervalSkipCount) throws RemoteException {
        int status = ((RangingController) mDevice).setBlockStriding(intervalSkipCount);
        if (status != STATUS_OK) {
            Log.w(TAG, String.format(
                    "Reconfiguring ranging interval failed with status %d", status));
        }
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
