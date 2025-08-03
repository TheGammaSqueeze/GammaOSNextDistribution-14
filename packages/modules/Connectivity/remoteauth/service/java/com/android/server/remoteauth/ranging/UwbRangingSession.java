/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.server.remoteauth.ranging;

import static androidx.core.uwb.backend.impl.internal.RangingDevice.SESSION_ID_UNSET;
import static androidx.core.uwb.backend.impl.internal.Utils.STATUS_OK;
import static androidx.core.uwb.backend.impl.internal.Utils.SUPPORTED_BPRF_PREAMBLE_INDEX;
import static androidx.core.uwb.backend.impl.internal.UwbAddress.SHORT_ADDRESS_LENGTH;

import static com.android.server.remoteauth.ranging.RangingReport.PROXIMITY_STATE_INSIDE;
import static com.android.server.remoteauth.ranging.RangingReport.PROXIMITY_STATE_OUTSIDE;
import static com.android.server.remoteauth.ranging.SessionParameters.DEVICE_ROLE_INITIATOR;

import static com.google.uwb.support.fira.FiraParams.UWB_CHANNEL_9;

import android.annotation.NonNull;
import android.annotation.RequiresApi;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.core.uwb.backend.impl.internal.RangingController;
import androidx.core.uwb.backend.impl.internal.RangingDevice;
import androidx.core.uwb.backend.impl.internal.RangingPosition;
import androidx.core.uwb.backend.impl.internal.RangingSessionCallback;
import androidx.core.uwb.backend.impl.internal.RangingSessionCallback.RangingSuspendedReason;
import androidx.core.uwb.backend.impl.internal.UwbAddress;
import androidx.core.uwb.backend.impl.internal.UwbComplexChannel;
import androidx.core.uwb.backend.impl.internal.UwbDevice;
import androidx.core.uwb.backend.impl.internal.UwbServiceImpl;

import com.android.internal.util.Preconditions;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** UWB (ultra wide-band) specific implementation of {@link RangingSession}. */
@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
public class UwbRangingSession extends RangingSession {
    private static final String TAG = "UwbRangingSession";

    private static final int COMPLEX_CHANNEL_LENGTH = 1;
    private static final int STS_KEY_LENGTH = 16;
    private static final int DERIVED_DATA_LENGTH =
            COMPLEX_CHANNEL_LENGTH + SHORT_ADDRESS_LENGTH + SHORT_ADDRESS_LENGTH + STS_KEY_LENGTH;

    private final UwbServiceImpl mUwbServiceImpl;
    private final RangingDevice mRangingDevice;

    private Executor mExecutor;
    private RangingCallback mRangingCallback;

    public UwbRangingSession(
            @NonNull Context context,
            @NonNull SessionParameters sessionParameters,
            @NonNull UwbServiceImpl uwbServiceImpl) {
        super(context, sessionParameters, DERIVED_DATA_LENGTH);
        Preconditions.checkNotNull(uwbServiceImpl);
        mUwbServiceImpl = uwbServiceImpl;
        if (sessionParameters.getDeviceRole() == DEVICE_ROLE_INITIATOR) {
            mRangingDevice = (RangingDevice) mUwbServiceImpl.getController(context);
        } else {
            mRangingDevice = (RangingDevice) mUwbServiceImpl.getControlee(context);
        }
    }

    @Override
    public void start(
            @NonNull RangingParameters rangingParameters,
            @NonNull Executor executor,
            @NonNull RangingCallback rangingCallback) {
        Preconditions.checkNotNull(rangingParameters, "rangingParameters must not be null");
        Preconditions.checkNotNull(executor, "executor must not be null");
        Preconditions.checkNotNull(rangingCallback, "rangingCallback must not be null");

        setUwbRangingParameters(rangingParameters);
        int status =
                mRangingDevice.startRanging(
                        convertCallback(rangingCallback, executor),
                        Executors.newSingleThreadExecutor());
        if (status != STATUS_OK) {
            Log.w(TAG, String.format("Uwb ranging start failed with status %d", status));
            executor.execute(
                    () -> rangingCallback.onError(mSessionInfo, RANGING_ERROR_FAILED_TO_START));
            return;
        }
        mExecutor = executor;
        mRangingCallback = rangingCallback;
        Log.i(TAG, "start");
    }

    @Override
    public void stop() {
        if (mRangingCallback == null) {
            Log.w(TAG, String.format("Failed to stop unstarted session"));
            return;
        }
        int status = mRangingDevice.stopRanging();
        if (status != STATUS_OK) {
            Log.w(TAG, String.format("Uwb ranging stop failed with status %d", status));
            mExecutor.execute(
                    () -> mRangingCallback.onError(mSessionInfo, RANGING_ERROR_FAILED_TO_STOP));
            return;
        }
        mRangingCallback = null;
        Log.i(TAG, "stop");
    }

    private void setUwbRangingParameters(RangingParameters rangingParameters) {
        androidx.core.uwb.backend.impl.internal.RangingParameters params =
                rangingParameters.getUwbRangingParameters();
        Preconditions.checkNotNull(params, "uwbRangingParameters must not be null");
        if (mAutoDeriveParams) {
            Preconditions.checkArgument(mDerivedData.length == DERIVED_DATA_LENGTH);
            ByteBuffer buffer = ByteBuffer.wrap(mDerivedData);

            byte complexChannelByte = buffer.get();
            int preambleIndex =
                    SUPPORTED_BPRF_PREAMBLE_INDEX.get(
                            Math.abs(complexChannelByte) % SUPPORTED_BPRF_PREAMBLE_INDEX.size());
            // Selecting channel 9 since it's the only mandatory channel.
            UwbComplexChannel complexChannel = new UwbComplexChannel(UWB_CHANNEL_9, preambleIndex);

            byte[] localAddress = new byte[SHORT_ADDRESS_LENGTH];
            byte[] peerAddress = new byte[SHORT_ADDRESS_LENGTH];
            if (mRangingDevice instanceof RangingController) {
                ((RangingController) mRangingDevice).setComplexChannel(complexChannel);
                buffer.get(localAddress);
                buffer.get(peerAddress);
            } else {
                buffer.get(peerAddress);
                buffer.get(localAddress);
            }
            byte[] stsKey = new byte[STS_KEY_LENGTH];
            buffer.get(stsKey);

            mRangingDevice.setLocalAddress(UwbAddress.fromBytes(localAddress));
            mRangingDevice.setRangingParameters(
                    new androidx.core.uwb.backend.impl.internal.RangingParameters(
                            params.getUwbConfigId(),
                            SESSION_ID_UNSET,
                            /* subSessionId= */ SESSION_ID_UNSET,
                            stsKey,
                            /* subSessionInfo= */ new byte[] {},
                            complexChannel,
                            List.of(UwbAddress.fromBytes(peerAddress)),
                            params.getRangingUpdateRate(),
                            params.getUwbRangeDataNtfConfig(),
                            params.getSlotDuration(),
                            params.isAoaDisabled()));
        } else {
            UwbAddress localAddress = rangingParameters.getUwbLocalAddress();
            Preconditions.checkNotNull(localAddress, "localAddress must not be null");
            UwbComplexChannel complexChannel = params.getComplexChannel();
            Preconditions.checkNotNull(complexChannel, "complexChannel must not be null");
            mRangingDevice.setLocalAddress(localAddress);
            if (mRangingDevice instanceof RangingController) {
                ((RangingController) mRangingDevice).setComplexChannel(complexChannel);
            }
            mRangingDevice.setRangingParameters(params);
        }
    }

    private RangingSessionCallback convertCallback(RangingCallback callback, Executor executor) {
        return new RangingSessionCallback() {

            @Override
            public void onRangingInitialized(UwbDevice device) {
                Log.i(TAG, "onRangingInitialized");
            }

            @Override
            public void onRangingResult(UwbDevice device, RangingPosition position) {
                float distanceM = position.getDistance().getValue();
                int proximityState =
                        (mLowerProximityBoundaryM <= distanceM
                                        && distanceM <= mUpperProximityBoundaryM)
                                ? PROXIMITY_STATE_INSIDE
                                : PROXIMITY_STATE_OUTSIDE;
                position.getDistance().getValue();
                RangingReport rangingReport =
                        new RangingReport.Builder()
                                .setDistanceM(distanceM)
                                .setProximityState(proximityState)
                                .build();
                executor.execute(() -> callback.onRangingReport(mSessionInfo, rangingReport));
            }

            @Override
            public void onRangingSuspended(UwbDevice device, @RangingSuspendedReason int reason) {
                executor.execute(() -> callback.onError(mSessionInfo, convertError(reason)));
            }
        };
    }

    @RangingError
    private static int convertError(@RangingSuspendedReason int reason) {
        if (reason == RangingSessionCallback.REASON_WRONG_PARAMETERS) {
            return RANGING_ERROR_INVALID_PARAMETERS;
        }
        if (reason == RangingSessionCallback.REASON_STOP_RANGING_CALLED) {
            return RANGING_ERROR_STOPPED_BY_REQUEST;
        }
        if (reason == RangingSessionCallback.REASON_STOPPED_BY_PEER) {
            return RANGING_ERROR_STOPPED_BY_PEER;
        }
        if (reason == RangingSessionCallback.REASON_FAILED_TO_START) {
            return RANGING_ERROR_FAILED_TO_START;
        }
        if (reason == RangingSessionCallback.REASON_SYSTEM_POLICY) {
            return RANGING_ERROR_SYSTEM_ERROR;
        }
        if (reason == RangingSessionCallback.REASON_MAX_RANGING_ROUND_RETRY_REACHED) {
            return RANGING_ERROR_SYSTEM_TIMEOUT;
        }
        return RANGING_ERROR_UNKNOWN;
    }
}
