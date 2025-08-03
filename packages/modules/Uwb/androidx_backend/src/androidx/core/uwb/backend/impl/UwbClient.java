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

import static androidx.core.uwb.backend.impl.internal.Utils.DURATION_2_MS;
import static androidx.core.uwb.backend.impl.internal.Utils.STATUS_OK;
import static androidx.core.uwb.backend.impl.internal.Utils.TAG;

import android.os.RemoteException;
import android.util.Log;

import androidx.core.uwb.backend.IRangingSessionCallback;
import androidx.core.uwb.backend.IUwbClient;
import androidx.core.uwb.backend.RangingCapabilities;
import androidx.core.uwb.backend.RangingMeasurement;
import androidx.core.uwb.backend.RangingParameters;
import androidx.core.uwb.backend.UwbAddress;
import androidx.core.uwb.backend.impl.internal.RangingDevice;
import androidx.core.uwb.backend.impl.internal.RangingPosition;
import androidx.core.uwb.backend.impl.internal.RangingSessionCallback;
import androidx.core.uwb.backend.impl.internal.UwbDevice;
import androidx.core.uwb.backend.impl.internal.UwbRangeDataNtfConfig;
import androidx.core.uwb.backend.impl.internal.UwbServiceImpl;

import java.util.ArrayList;
import java.util.List;

/** Implements operations of IUwbClient. */
public abstract class UwbClient extends IUwbClient.Stub {
    protected final UwbServiceImpl mUwbService;
    protected final RangingDevice mDevice;
    protected boolean mSupportsAzimuthalAngle = true;

    protected UwbClient(RangingDevice device, UwbServiceImpl uwbService) {
        mDevice = device;
        mUwbService = uwbService;
    }

    @Override
    public boolean isAvailable() throws RemoteException {
        return mUwbService.isAvailable();
    }

    @Override
    public RangingCapabilities getRangingCapabilities() throws RemoteException {
        androidx.core.uwb.backend.impl.internal.RangingCapabilities cap =
                mUwbService.getRangingCapabilities();
        RangingCapabilities rangingCapabilities = new RangingCapabilities();
        mSupportsAzimuthalAngle = cap.supportsAzimuthalAngle();
        rangingCapabilities.supportsAzimuthalAngle = mSupportsAzimuthalAngle;
        rangingCapabilities.supportsDistance = cap.supportsDistance();
        rangingCapabilities.supportsElevationAngle = cap.supportsElevationAngle();
        rangingCapabilities.supportsRangingIntervalReconfigure =
                cap.supportsRangingIntervalReconfigure();
        rangingCapabilities.minRangingInterval = cap.getMinRangingInterval();
        rangingCapabilities.supportedChannels = cap.getSupportedChannels()
                .stream().mapToInt(Integer::intValue).toArray();
        rangingCapabilities.supportedNtfConfigs = cap.getSupportedNtfConfigs()
                .stream().mapToInt(Integer::intValue).toArray();
        rangingCapabilities.supportedConfigIds = cap.getSupportedConfigIds()
                .stream().mapToInt(Integer::intValue).toArray();
        rangingCapabilities.supportedSlotDurations = cap.getSupportedSlotDurations()
                .stream().mapToInt(Integer::intValue).toArray();
        rangingCapabilities.supportedRangingUpdateRates = cap.getSupportedRangingUpdateRates()
                .stream().mapToInt(Integer::intValue).toArray();
        rangingCapabilities.hasBackgroundRangingSupport = cap.hasBackgroundRangingSupport();
        return rangingCapabilities;
    }

    @Override
    public UwbAddress getLocalAddress() throws RemoteException {
        androidx.core.uwb.backend.impl.internal.UwbAddress address = mDevice.getLocalAddress();
        UwbAddress uwbAddress = new UwbAddress();
        uwbAddress.address = address.toBytes();
        return uwbAddress;
    }

    protected void setRangingParameters(RangingParameters parameters) throws RemoteException {
        androidx.core.uwb.backend.impl.internal.UwbComplexChannel channel =
                new androidx.core.uwb.backend.impl.internal.UwbComplexChannel(
                        parameters.complexChannel.channel, parameters.complexChannel.preambleIndex);
        List<androidx.core.uwb.backend.impl.internal.UwbAddress> addresses = new ArrayList<>();
        for (androidx.core.uwb.backend.UwbDevice device : parameters.peerDevices) {
            addresses.add(androidx.core.uwb.backend.impl.internal.UwbAddress
                    .fromBytes(device.address.address));
        }
        UwbRangeDataNtfConfig.Builder uwbRangeDataNtfConfigBuilder =
                new UwbRangeDataNtfConfig.Builder();
        if (parameters.uwbRangeDataNtfConfig != null) {
            uwbRangeDataNtfConfigBuilder
                    .setRangeDataConfigType(parameters.uwbRangeDataNtfConfig.rangeDataNtfConfigType)
                    .setNtfProximityNear(parameters.uwbRangeDataNtfConfig.ntfProximityNearCm)
                    .setNtfProximityFar(parameters.uwbRangeDataNtfConfig.ntfProximityFarCm);
        }
        int duration = parameters.slotDuration == 0 ? DURATION_2_MS : parameters.slotDuration;
        mDevice.setRangingParameters(
                new androidx.core.uwb.backend.impl.internal.RangingParameters(
                        parameters.uwbConfigId, parameters.sessionId, parameters.subSessionId,
                        parameters.sessionKeyInfo, parameters.subSessionKeyInfo,
                        channel, addresses, parameters.rangingUpdateRate,
                        uwbRangeDataNtfConfigBuilder.build(), duration,
                        !mSupportsAzimuthalAngle || parameters.isAoaDisabled));
    }

    protected androidx.core.uwb.backend.impl.internal.RangingSessionCallback convertCallback(
            IRangingSessionCallback callback) {
        return new RangingSessionCallback() {
            @Override
            public void onRangingInitialized(UwbDevice device) {
                androidx.core.uwb.backend.UwbDevice backendDevice =
                        new androidx.core.uwb.backend.UwbDevice();
                backendDevice.address = new UwbAddress();
                backendDevice.address.address = device.getAddress().toBytes();
                try {
                    callback.onRangingInitialized(backendDevice);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onRangingResult(UwbDevice device, RangingPosition position) {
                androidx.core.uwb.backend.UwbDevice backendDevice =
                        new androidx.core.uwb.backend.UwbDevice();
                backendDevice.address = new UwbAddress();
                backendDevice.address.address = device.getAddress().toBytes();
                androidx.core.uwb.backend.RangingPosition rangingPosition =
                        new androidx.core.uwb.backend.RangingPosition();
                RangingMeasurement distance = new RangingMeasurement();
                distance.confidence = position.getDistance().getConfidence();
                distance.value = position.getDistance().getValue();
                rangingPosition.distance = distance;
                if (position.getAzimuth() != null) {
                    RangingMeasurement azimuth = new RangingMeasurement();
                    azimuth.confidence = position.getAzimuth().getConfidence();
                    azimuth.value = position.getAzimuth().getValue();
                    rangingPosition.azimuth = azimuth;
                }
                if (position.getElevation() != null) {
                    RangingMeasurement elevation = new RangingMeasurement();
                    elevation.confidence = position.getElevation().getConfidence();
                    elevation.value = position.getElevation().getValue();
                    rangingPosition.elevation = elevation;
                }
                rangingPosition.elapsedRealtimeNanos = position.getElapsedRealtimeNanos();
                try {
                    callback.onRangingResult(backendDevice, rangingPosition);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onRangingSuspended(UwbDevice device, int reason) {
                androidx.core.uwb.backend.UwbDevice backendDevice =
                        new androidx.core.uwb.backend.UwbDevice();
                backendDevice.address = new UwbAddress();
                backendDevice.address.address = device.getAddress().toBytes();
                try {
                    callback.onRangingSuspended(backendDevice, reason);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        };
    }

    @Override
    public void reconfigureRangeDataNtf(int configType, int proximityNear, int proximityFar)
            throws RemoteException {
        UwbRangeDataNtfConfig config = new UwbRangeDataNtfConfig.Builder()
                .setRangeDataConfigType(configType)
                .setNtfProximityNear(proximityNear)
                .setNtfProximityFar(proximityFar)
                .build();
        int status = mDevice.reconfigureRangeDataNtfConfig(config);
        if (status != STATUS_OK) {
            Log.w(TAG, String.format(
                    "Reconfiguring range data notification config failed with status %d", status));
        }
    }
}
