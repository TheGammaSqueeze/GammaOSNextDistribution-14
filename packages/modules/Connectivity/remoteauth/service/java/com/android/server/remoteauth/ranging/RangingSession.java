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

import android.annotation.NonNull;
import android.content.Context;
import android.util.Log;

import androidx.annotation.IntDef;

import com.android.internal.util.Preconditions;
import com.android.server.remoteauth.util.Crypto;

import com.google.common.hash.Hashing;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.concurrent.Executor;

/**
 * The controller for starting and stopping ranging during which callers receive callbacks with
 * {@link RangingReport}s and {@link RangingError}s."
 *
 * <p>A session can be started and stopped multiple times. After starting, updates ({@link
 * RangingReport}, {@link RangingError}, etc) will be reported via the provided {@link
 * RangingCallback}. BaseKey and SyncData are used for auto derivation of supported ranging
 * parameters, which will be implementation specific. All session creation shall only be conducted
 * via {@link RangingManager#createSession}.
 *
 * <p>Ranging method specific implementation shall be implemented in the extended class.
 */
public abstract class RangingSession {
    private static final String TAG = "RangingSession";

    /** Types of ranging error. */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(
            value = {
                RANGING_ERROR_UNKNOWN,
                RANGING_ERROR_INVALID_PARAMETERS,
                RANGING_ERROR_STOPPED_BY_REQUEST,
                RANGING_ERROR_STOPPED_BY_PEER,
                RANGING_ERROR_FAILED_TO_START,
                RANGING_ERROR_FAILED_TO_STOP,
                RANGING_ERROR_SYSTEM_ERROR,
                RANGING_ERROR_SYSTEM_TIMEOUT,
            })
    public @interface RangingError {}

    /** Unknown ranging error type. */
    public static final int RANGING_ERROR_UNKNOWN = 0x0;

    /** Ranging error due to invalid parameters. */
    public static final int RANGING_ERROR_INVALID_PARAMETERS = 0x1;

    /** Ranging error due to stopped by calling {@link #stop}. */
    public static final int RANGING_ERROR_STOPPED_BY_REQUEST = 0x2;

    /** Ranging error due to stopped by the peer device. */
    public static final int RANGING_ERROR_STOPPED_BY_PEER = 0x3;

    /** Ranging error due to failure to start ranging. */
    public static final int RANGING_ERROR_FAILED_TO_START = 0x4;

    /** Ranging error due to failure to stop ranging. */
    public static final int RANGING_ERROR_FAILED_TO_STOP = 0x5;

    /**
     * Ranging error due to system error cause by changes such as privacy policy, power management
     * policy, permissions, and more.
     */
    public static final int RANGING_ERROR_SYSTEM_ERROR = 0x6;

    /** Ranging error due to system timeout in retry attempts. */
    public static final int RANGING_ERROR_SYSTEM_TIMEOUT = 0x7;

    /** Interface for ranging update callbacks. */
    public interface RangingCallback {
        /**
         * Call upon new {@link RangingReport}.
         *
         * @param sessionInfo info about this ranging session.
         * @param rangingReport new ranging report
         */
        void onRangingReport(
                @NonNull SessionInfo sessionInfo, @NonNull RangingReport rangingReport);

        /**
         * Call upon any ranging error events.
         *
         * @param sessionInfo info about this ranging session.
         * @param rangingError error type
         */
        void onError(@NonNull SessionInfo sessionInfo, @RangingError int rangingError);
    }

    protected Context mContext;
    protected SessionInfo mSessionInfo;
    protected float mLowerProximityBoundaryM;
    protected float mUpperProximityBoundaryM;
    protected boolean mAutoDeriveParams;
    protected byte[] mBaseKey;
    protected byte[] mSyncData;
    protected int mSyncCounter;
    protected byte[] mDerivedData;
    protected int mDerivedDataLength;

    protected RangingSession(
            @NonNull Context context,
            @NonNull SessionParameters sessionParameters,
            int derivedDataLength) {
        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(sessionParameters);
        mContext = context;
        mSessionInfo =
                new SessionInfo.Builder()
                        .setDeviceId(sessionParameters.getDeviceId())
                        .setRangingMethod(sessionParameters.getRangingMethod())
                        .build();
        mLowerProximityBoundaryM = sessionParameters.getLowerProximityBoundaryM();
        mUpperProximityBoundaryM = sessionParameters.getUpperProximityBoundaryM();
        mAutoDeriveParams = sessionParameters.getAutoDeriveParams();
        Log.i(
                TAG,
                "Creating a new RangingSession {info = "
                        + mSessionInfo
                        + ", autoDeriveParams = "
                        + mAutoDeriveParams
                        + "}");
        if (mAutoDeriveParams) {
            Preconditions.checkArgument(
                    derivedDataLength > 0, "derivedDataLength must be greater than 0");
            mDerivedDataLength = derivedDataLength;
            resetBaseKey(sessionParameters.getBaseKey());
            resetSyncData(sessionParameters.getSyncData());
        }
    }

    /**
     * Starts ranging based on the given {@link RangingParameters}.
     *
     * <p>Start can be called again after {@link #stop()} has been called, else it will result in a
     * no-op.
     *
     * @param rangingParameters parameters to start the ranging.
     * @param executor Executor to run the rangingCallback.
     * @param rangingCallback callback to notify of ranging events.
     * @throws NullPointerException if params are null.
     * @throws IllegalArgumentException if rangingParameters is invalid.
     */
    public abstract void start(
            @NonNull RangingParameters rangingParameters,
            @NonNull Executor executor,
            @NonNull RangingCallback rangingCallback);

    /**
     * Stops ranging.
     *
     * <p>Calling stop without first calling {@link #start()} will result in a no-op.
     */
    public abstract void stop();

    /**
     * Resets the base key that's used to derive all possible ranging parameters. The baseKey shall
     * be reset whenever there is a risk that it may no longer be valid and secured. For example,
     * the secure connection between the devices is lost.
     *
     * @param baseKey new baseKey must be 16 or 32 bytes.
     * @throws NullPointerException if baseKey is null.
     * @throws IllegalArgumentException if baseKey has invalid length.
     */
    public void resetBaseKey(@NonNull byte[] baseKey) {
        if (!mAutoDeriveParams) {
            Log.w(TAG, "autoDeriveParams is disabled, new baseKey is ignored.");
            return;
        }
        Preconditions.checkNotNull(baseKey);
        if (baseKey.length != 16 && baseKey.length != 32) {
            throw new IllegalArgumentException("Invalid baseKey length: " + baseKey.length);
        }
        mBaseKey = baseKey;
        updateDerivedData();
        Log.i(TAG, "resetBaseKey");
    }

    /**
     * Resets the synchronization by giving a new syncData used for ranging parameters derivation.
     * Resetting the syncData is not required before each {@link #start}, but the more time the
     * derivations are done before resetting syncData, the higher the risk the derivation will be
     * out of sync between the devices. Therefore, syncData shall be refreshed in a best effort
     * manner.
     *
     * @param syncData new syncData must be 16 bytes.
     * @throws NullPointerException if baseKey is null.
     * @throws IllegalArgumentException if syncData has invalid length.
     */
    public void resetSyncData(@NonNull byte[] syncData) {
        if (!mAutoDeriveParams) {
            Log.w(TAG, "autoDeriveParams is disabled, new syncData is ignored.");
            return;
        }
        Preconditions.checkNotNull(syncData);
        if (syncData.length != 16) {
            throw new IllegalArgumentException("Invalid syncData length: " + syncData.length);
        }
        mSyncData = syncData;
        mSyncCounter = 0;
        updateDerivedData();
        Log.i(TAG, "resetSyncData");
    }

    /** Recomputes mDerivedData using the latest mBaseKey, mSyncData, and mSyncCounter. */
    protected boolean updateDerivedData() {
        if (!mAutoDeriveParams) {
            Log.w(TAG, "autoDeriveParams is disabled, updateDerivedData is skipped.");
            return false;
        }
        if (mBaseKey == null
                || mBaseKey.length == 0
                || mSyncData == null
                || mSyncData.length == 0) {
            Log.w(TAG, "updateDerivedData: Missing baseKey/syncData");
            return false;
        }
        byte[] hashedSyncData =
                Hashing.sha256()
                        .newHasher()
                        .putBytes(mSyncData)
                        .putInt(mSyncCounter)
                        .hash()
                        .asBytes();
        byte[] newDerivedData = Crypto.computeHkdf(mBaseKey, hashedSyncData, mDerivedDataLength);
        if (newDerivedData == null) {
            Log.w(TAG, "updateDerivedData: computeHkdf failed");
            return false;
        }
        mDerivedData = newDerivedData;
        mSyncCounter++;
        Log.i(TAG, "updateDerivedData");
        return true;
    }
}
