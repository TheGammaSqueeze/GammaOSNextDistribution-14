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

package com.android.rkpdapp.metrics;

import android.content.Context;
import android.hardware.security.keymint.IRemotelyProvisionedComponent;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.util.Log;

import com.android.rkpdapp.service.RemoteProvisioningService;
import com.android.rkpdapp.utils.StopWatch;

import java.time.Duration;

/**
 * Contains the metrics values that are recorded for every attempt to remotely provision keys.
 * This class will automatically push the atoms on close, and is intended to be used with a
 * try-with-resources block to ensure metrics are automatically logged on completion of an attempt.
 */
public final class ProvisioningAttempt implements AutoCloseable {
    // The state of remote provisioning enablement
    public enum Enablement {
        UNKNOWN,
        ENABLED_WITH_FALLBACK,
        ENABLED_RKP_ONLY,
        DISABLED
    }

    public enum Status {
        UNKNOWN,
        KEYS_SUCCESSFULLY_PROVISIONED,
        NO_PROVISIONING_NEEDED,
        PROVISIONING_DISABLED,
        INTERNAL_ERROR,
        NO_NETWORK_CONNECTIVITY,
        OUT_OF_ERROR_BUDGET,
        INTERRUPTED,
        GENERATE_KEYPAIR_FAILED,
        GENERATE_CSR_FAILED,
        GET_POOL_STATUS_FAILED,
        INSERT_CHAIN_INTO_POOL_FAILED,
        FETCH_GEEK_TIMED_OUT,
        FETCH_GEEK_IO_EXCEPTION,
        FETCH_GEEK_HTTP_ERROR,
        SIGN_CERTS_TIMED_OUT,
        SIGN_CERTS_IO_EXCEPTION,
        SIGN_CERTS_HTTP_ERROR,
        SIGN_CERTS_DEVICE_NOT_REGISTERED
    }

    private static final String TAG = RemoteProvisioningService.TAG;

    private final Context mContext;
    private final int mCause;
    private final StopWatch mServerWaitTimer = new StopWatch(TAG);
    private final StopWatch mBinderWaitTimer = new StopWatch(TAG);
    private final StopWatch mLockWaitTimer = new StopWatch(TAG);
    private final StopWatch mTotalTimer = new StopWatch(TAG);
    private final String mRemotelyProvisionedComponent;
    private Enablement mEnablement;
    private boolean mIsKeyPoolEmpty = false;
    private Status mStatus = Status.UNKNOWN;
    private int mHttpStatusError;
    private String mRootCertFingerprint = "<none>";
    private int mCertChainLength;

    private ProvisioningAttempt(Context context, int cause,
            String remotelyProvisionedComponent, Enablement enablement) {
        mContext = context;
        mCause = cause;
        mRemotelyProvisionedComponent = remotelyProvisionedComponent;
        mEnablement = enablement;
        mTotalTimer.start();
    }

    /** Start collecting metrics for scheduled provisioning. */
    public static ProvisioningAttempt createScheduledAttemptMetrics(Context context) {
        // Scheduled jobs (PeriodicProvisioner) intermix a lot of operations for multiple
        // components, which makes it difficult to tease apart what is happening for which
        // remotely provisioned component. Thus, on these calls, the component and
        // component-specific enablement are not logged.
        return new ProvisioningAttempt(
                context,
                RkpdStatsLog.REMOTE_KEY_PROVISIONING_ATTEMPT__CAUSE__SCHEDULED,
                "",
                Enablement.UNKNOWN);
    }

    /** Start collecting metrics when an attestation key has been consumed from the pool. */
    public static ProvisioningAttempt createKeyConsumedAttemptMetrics(Context context,
            String remotelyProvisionedComponent) {
        return new ProvisioningAttempt(
                context,
                RkpdStatsLog.REMOTE_KEY_PROVISIONING_ATTEMPT__CAUSE__KEY_CONSUMED,
                remotelyProvisionedComponent,
                getEnablementForComponent(remotelyProvisionedComponent));
    }

    /** Start collecting metrics when the spare attestation key pool is empty. */
    public static ProvisioningAttempt createOutOfKeysAttemptMetrics(Context context,
            String remotelyProvisionedComponent) {
        return new ProvisioningAttempt(
                context,
                RkpdStatsLog.REMOTE_KEY_PROVISIONING_ATTEMPT__CAUSE__OUT_OF_KEYS,
                remotelyProvisionedComponent,
                getEnablementForComponent(remotelyProvisionedComponent));
    }

    /** Record the state of RKP configuration. */
    public void setEnablement(Enablement enablement) {
        mEnablement = enablement;
    }

    /** Set to true if the provisioning encountered an empty key pool. */
    public void setIsKeyPoolEmpty(boolean isEmpty) {
        mIsKeyPoolEmpty = isEmpty;
    }

    /** Set the status for this provisioning attempt. */
    public void setStatus(Status status) {
        mStatus = status;
    }

    /** Set the last HTTP status encountered. */
    public void setHttpStatusError(int httpStatusError) {
        mHttpStatusError = httpStatusError;
    }

    public void setRootCertFingerprint(String rootCertFingerprint) {
        mRootCertFingerprint = rootCertFingerprint;
    }

    public void setCertChainLength(int certChainLength) {
        mCertChainLength = certChainLength;
    }

    /**
     * Starts the server wait timer, returning a reference to an object to be closed when the
     * wait is over.
     */
    public StopWatch startServerWait() {
        mServerWaitTimer.start();
        return mServerWaitTimer;
    }

    /**
     * Starts the binder wait timer, returning a reference to an object to be closed when the
     * wait is over.
     */
    public StopWatch startBinderWait() {
        mBinderWaitTimer.start();
        return mBinderWaitTimer;
    }

    /**
     * Starts the lock wait timer, returning a reference to an object to be closed when the
     * wait is over.
     */
    public StopWatch startLockWait() {
        mLockWaitTimer.start();
        return mLockWaitTimer;
    }

    /** Record the atoms for this metrics object. */
    @Override
    public void close() {
        mTotalTimer.stop();

        int transportType = getTransportTypeForActiveNetwork();
        RkpdStatsLog.write(RkpdStatsLog.REMOTE_KEY_PROVISIONING_ATTEMPT,
                mCause, mRemotelyProvisionedComponent, getUpTimeBucket(), getIntEnablement(),
                mIsKeyPoolEmpty, getIntStatus(), mRootCertFingerprint, mCertChainLength);
        RkpdStatsLog.write(
                RkpdStatsLog.REMOTE_KEY_PROVISIONING_NETWORK_INFO,
                transportType, getIntStatus(), mHttpStatusError);
        RkpdStatsLog.write(RkpdStatsLog.REMOTE_KEY_PROVISIONING_TIMING,
                mServerWaitTimer.getElapsedMillis(), mBinderWaitTimer.getElapsedMillis(),
                mLockWaitTimer.getElapsedMillis(), mTotalTimer.getElapsedMillis(), transportType,
                mRemotelyProvisionedComponent, mCause, getIntStatus());
    }

    private static Enablement getEnablementForComponent(String serviceName) {
        if (serviceName.equals(IRemotelyProvisionedComponent.DESCRIPTOR + "/default")) {
            return readRkpOnlyProperty("remote_provisioning.tee.rkp_only");
        }

        if (serviceName.equals(IRemotelyProvisionedComponent.DESCRIPTOR + "/strongbox")) {
            return readRkpOnlyProperty("remote_provisioning.strongbox.rkp_only");
        }

        Log.w(TAG, "Unknown remotely provisioned component name: " + serviceName);
        return Enablement.UNKNOWN;
    }

    private static Enablement readRkpOnlyProperty(String property) {
        if (SystemProperties.getBoolean(property, false)) {
            return Enablement.ENABLED_RKP_ONLY;
        }
        return Enablement.ENABLED_WITH_FALLBACK;
    }

    private int getTransportTypeForActiveNetwork() {
        ConnectivityManager cm = mContext.getSystemService(ConnectivityManager.class);
        if (cm == null) {
            Log.w(TAG, "Unable to get ConnectivityManager instance");
            return RkpdStatsLog
                    .REMOTE_KEY_PROVISIONING_NETWORK_INFO__TRANSPORT_TYPE__TT_UNKNOWN;
        }

        NetworkCapabilities capabilities = cm.getNetworkCapabilities(cm.getActiveNetwork());
        if (capabilities == null) {
            return RkpdStatsLog
                    .REMOTE_KEY_PROVISIONING_NETWORK_INFO__TRANSPORT_TYPE__TT_UNKNOWN;
        }

        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                    && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return RkpdStatsLog
                        .REMOTE_KEY_PROVISIONING_NETWORK_INFO__TRANSPORT_TYPE__TT_WIFI_CELLULAR_VPN;
            }
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                return RkpdStatsLog
                        .REMOTE_KEY_PROVISIONING_NETWORK_INFO__TRANSPORT_TYPE__TT_CELLULAR_VPN;
            }
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return RkpdStatsLog
                        .REMOTE_KEY_PROVISIONING_NETWORK_INFO__TRANSPORT_TYPE__TT_WIFI_VPN;
            }
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) {
                return RkpdStatsLog
                        .REMOTE_KEY_PROVISIONING_NETWORK_INFO__TRANSPORT_TYPE__TT_BLUETOOTH_VPN;
            }
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                return RkpdStatsLog
                        .REMOTE_KEY_PROVISIONING_NETWORK_INFO__TRANSPORT_TYPE__TT_ETHERNET_VPN;
            }
            return RkpdStatsLog
                    .REMOTE_KEY_PROVISIONING_NETWORK_INFO__TRANSPORT_TYPE__TT_UNKNOWN;
        }

        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            return RkpdStatsLog
                    .REMOTE_KEY_PROVISIONING_NETWORK_INFO__TRANSPORT_TYPE__TT_CELLULAR;
        }
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return RkpdStatsLog
                    .REMOTE_KEY_PROVISIONING_NETWORK_INFO__TRANSPORT_TYPE__TT_WIFI;
        }
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) {
            return RkpdStatsLog
                    .REMOTE_KEY_PROVISIONING_NETWORK_INFO__TRANSPORT_TYPE__TT_BLUETOOTH;
        }
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            return RkpdStatsLog
                    .REMOTE_KEY_PROVISIONING_NETWORK_INFO__TRANSPORT_TYPE__TT_ETHERNET;
        }
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI_AWARE)) {
            return RkpdStatsLog
                    .REMOTE_KEY_PROVISIONING_NETWORK_INFO__TRANSPORT_TYPE__TT_WIFI_AWARE;
        }
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_LOWPAN)) {
            return RkpdStatsLog
                    .REMOTE_KEY_PROVISIONING_NETWORK_INFO__TRANSPORT_TYPE__TT_LOWPAN;
        }

        return RkpdStatsLog
                .REMOTE_KEY_PROVISIONING_NETWORK_INFO__TRANSPORT_TYPE__TT_UNKNOWN;
    }

    private int getUpTimeBucket() {
        final long uptimeMillis = SystemClock.uptimeMillis();
        if (uptimeMillis < Duration.ofMinutes(5).toMillis()) {
            return RkpdStatsLog
                    .REMOTE_KEY_PROVISIONING_ATTEMPT__UPTIME__LESS_THAN_5_MINUTES;
        } else if (uptimeMillis < Duration.ofMinutes(60).toMillis()) {
            return RkpdStatsLog
                    .REMOTE_KEY_PROVISIONING_ATTEMPT__UPTIME__BETWEEN_5_AND_60_MINUTES;
        } else {
            return RkpdStatsLog
                    .REMOTE_KEY_PROVISIONING_ATTEMPT__UPTIME__MORE_THAN_60_MINUTES;
        }
    }

    private int getIntStatus() {
        switch (mStatus) {
            // A whole bunch of generated types here just don't fit in our line length limit.
            // CHECKSTYLE:OFF Generated code
            case UNKNOWN:
                return RkpdStatsLog
                        .REMOTE_KEY_PROVISIONING_NETWORK_INFO__STATUS__REMOTE_KEY_PROVISIONING_STATUS_UNKNOWN;
            case KEYS_SUCCESSFULLY_PROVISIONED:
                return RkpdStatsLog
                        .REMOTE_KEY_PROVISIONING_NETWORK_INFO__STATUS__KEYS_SUCCESSFULLY_PROVISIONED;
            case NO_PROVISIONING_NEEDED:
                return RkpdStatsLog
                        .REMOTE_KEY_PROVISIONING_NETWORK_INFO__STATUS__NO_PROVISIONING_NEEDED;
            case PROVISIONING_DISABLED:
                return RkpdStatsLog
                        .REMOTE_KEY_PROVISIONING_NETWORK_INFO__STATUS__PROVISIONING_DISABLED;
            case INTERNAL_ERROR:
                return RkpdStatsLog
                        .REMOTE_KEY_PROVISIONING_NETWORK_INFO__STATUS__INTERNAL_ERROR;
            case NO_NETWORK_CONNECTIVITY:
                return RkpdStatsLog
                        .REMOTE_KEY_PROVISIONING_NETWORK_INFO__STATUS__NO_NETWORK_CONNECTIVITY;
            case OUT_OF_ERROR_BUDGET:
                return RkpdStatsLog
                        .REMOTE_KEY_PROVISIONING_NETWORK_INFO__STATUS__OUT_OF_ERROR_BUDGET;
            case INTERRUPTED:
                return RkpdStatsLog
                        .REMOTE_KEY_PROVISIONING_NETWORK_INFO__STATUS__INTERRUPTED;
            case GENERATE_KEYPAIR_FAILED:
                return RkpdStatsLog
                        .REMOTE_KEY_PROVISIONING_NETWORK_INFO__STATUS__GENERATE_KEYPAIR_FAILED;
            case GENERATE_CSR_FAILED:
                return RkpdStatsLog
                        .REMOTE_KEY_PROVISIONING_NETWORK_INFO__STATUS__GENERATE_CSR_FAILED;
            case GET_POOL_STATUS_FAILED:
                return RkpdStatsLog
                        .REMOTE_KEY_PROVISIONING_NETWORK_INFO__STATUS__GET_POOL_STATUS_FAILED;
            case INSERT_CHAIN_INTO_POOL_FAILED:
                return RkpdStatsLog
                        .REMOTE_KEY_PROVISIONING_NETWORK_INFO__STATUS__INSERT_CHAIN_INTO_POOL_FAILED;
            case FETCH_GEEK_TIMED_OUT:
                return RkpdStatsLog
                        .REMOTE_KEY_PROVISIONING_NETWORK_INFO__STATUS__FETCH_GEEK_TIMED_OUT;
            case FETCH_GEEK_IO_EXCEPTION:
                return RkpdStatsLog
                        .REMOTE_KEY_PROVISIONING_NETWORK_INFO__STATUS__FETCH_GEEK_IO_EXCEPTION;
            case FETCH_GEEK_HTTP_ERROR:
                return RkpdStatsLog
                        .REMOTE_KEY_PROVISIONING_NETWORK_INFO__STATUS__FETCH_GEEK_HTTP_ERROR;
            case SIGN_CERTS_TIMED_OUT:
                return RkpdStatsLog
                        .REMOTE_KEY_PROVISIONING_NETWORK_INFO__STATUS__SIGN_CERTS_TIMED_OUT;
            case SIGN_CERTS_IO_EXCEPTION:
                return RkpdStatsLog
                        .REMOTE_KEY_PROVISIONING_NETWORK_INFO__STATUS__SIGN_CERTS_IO_EXCEPTION;
            case SIGN_CERTS_HTTP_ERROR:
                return RkpdStatsLog
                        .REMOTE_KEY_PROVISIONING_NETWORK_INFO__STATUS__SIGN_CERTS_HTTP_ERROR;
            case SIGN_CERTS_DEVICE_NOT_REGISTERED:
                return RkpdStatsLog
                  .REMOTE_KEY_PROVISIONING_NETWORK_INFO__STATUS__SIGN_CERTS_DEVICE_NOT_REGISTERED;
        }
        return RkpdStatsLog
                .REMOTE_KEY_PROVISIONING_NETWORK_INFO__STATUS__REMOTE_KEY_PROVISIONING_STATUS_UNKNOWN;
        // CHECKSTYLE:ON Generated code
    }

    private int getIntEnablement() {
        switch (mEnablement) {
            case UNKNOWN:
                return RkpdStatsLog
                        .REMOTE_KEY_PROVISIONING_ATTEMPT__ENABLEMENT__ENABLEMENT_UNKNOWN;
            case ENABLED_WITH_FALLBACK:
                return RkpdStatsLog
                        .REMOTE_KEY_PROVISIONING_ATTEMPT__ENABLEMENT__ENABLED_WITH_FALLBACK;
            case ENABLED_RKP_ONLY:
                return RkpdStatsLog
                        .REMOTE_KEY_PROVISIONING_ATTEMPT__ENABLEMENT__ENABLED_RKP_ONLY;
            case DISABLED:
                return RkpdStatsLog
                        .REMOTE_KEY_PROVISIONING_ATTEMPT__ENABLEMENT__DISABLED;
        }
        return RkpdStatsLog
                .REMOTE_KEY_PROVISIONING_ATTEMPT__ENABLEMENT__ENABLEMENT_UNKNOWN;
    }
}
