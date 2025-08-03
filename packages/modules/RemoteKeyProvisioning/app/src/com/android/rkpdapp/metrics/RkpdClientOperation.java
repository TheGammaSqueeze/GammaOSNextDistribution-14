/**
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

import com.android.rkpdapp.service.RemoteProvisioningService;
import com.android.rkpdapp.utils.StopWatch;

/**
 * Contains the metrics values that are recorded for every client call into RKPD.
 * This class will automatically push an atom on close, and is intended to be used with a
 * try-with-resources block to ensure metrics are automatically logged.
 */
public final class RkpdClientOperation implements AutoCloseable {
    private static final String TAG = RemoteProvisioningService.TAG;

    public enum Result {
        UNKNOWN(RkpdStatsLog.RKPD_CLIENT_OPERATION__RESULT__RESULT_UNKNOWN),
        SUCCESS(RkpdStatsLog.RKPD_CLIENT_OPERATION__RESULT__RESULT_SUCCESS),
        CANCELED(RkpdStatsLog.RKPD_CLIENT_OPERATION__RESULT__RESULT_CANCELED),
        RKP_UNSUPPORTED(RkpdStatsLog.RKPD_CLIENT_OPERATION__RESULT__RESULT_RKP_UNSUPPORTED),
        ERROR_INTERNAL(RkpdStatsLog.RKPD_CLIENT_OPERATION__RESULT__RESULT_ERROR_INTERNAL),
        ERROR_REQUIRES_SECURITY_PATCH(
                RkpdStatsLog
                        .RKPD_CLIENT_OPERATION__RESULT__RESULT_ERROR_REQUIRES_SECURITY_PATCH),
        ERROR_PENDING_INTERNET_CONNECTIVITY(
                RkpdStatsLog
                        .RKPD_CLIENT_OPERATION__RESULT__RESULT_ERROR_PENDING_INTERNET_CONNECTIVITY),
        ERROR_PERMANENT(RkpdStatsLog.RKPD_CLIENT_OPERATION__RESULT__RESULT_ERROR_PERMANENT),
        ERROR_INVALID_HAL(RkpdStatsLog.RKPD_CLIENT_OPERATION__RESULT__RESULT_ERROR_INVALID_HAL),
        ERROR_KEY_NOT_FOUND(RkpdStatsLog.RKPD_CLIENT_OPERATION__RESULT__RESULT_ERROR_KEY_NOT_FOUND);

        private final int mAtomValue;

        Result(int atomValue) {
            mAtomValue = atomValue;
        }

        public int getAtomValue() {
            return mAtomValue;
        }
    }

    private final StopWatch mTimer = new StopWatch(TAG);
    private final int mClientUid;
    private final String mRemotelyProvisionedComponent;
    private final int mOperationId;
    private int mResult = RkpdStatsLog.RKPD_CLIENT_OPERATION__RESULT__RESULT_UNKNOWN;

    /** Create an object that records an atom for a getRegistration call */
    public static RkpdClientOperation getRegistration(int clientUid,
            String remotelyProvisionedComponent) {
        return new RkpdClientOperation(clientUid, remotelyProvisionedComponent,
                RkpdStatsLog.RKPD_CLIENT_OPERATION__OPERATION__OPERATION_GET_REGISTRATION);
    }

    /** Create an object that records an atom for a getKey call */
    public static RkpdClientOperation getKey(int clientUid,
            String remotelyProvisionedComponent) {
        return new RkpdClientOperation(clientUid, remotelyProvisionedComponent,
                RkpdStatsLog.RKPD_CLIENT_OPERATION__OPERATION__OPERATION_GET_KEY);
    }

    /** Create an object that records an atom for a cancelGetKey call */
    public static RkpdClientOperation cancelGetKey(int clientUid,
            String remotelyProvisionedComponent) {
        return new RkpdClientOperation(clientUid, remotelyProvisionedComponent,
                RkpdStatsLog.RKPD_CLIENT_OPERATION__OPERATION__OPERATION_CANCEL_GET_KEY);
    }

    /** Create an object that records an atom for a storeUpgradedKey call */
    public static RkpdClientOperation storeUpgradedKey(int clientUid,
            String remotelyProvisionedComponent) {
        return new RkpdClientOperation(clientUid, remotelyProvisionedComponent,
                RkpdStatsLog.RKPD_CLIENT_OPERATION__OPERATION__OPERATION_STORE_UPGRADED_KEY);
    }

    private RkpdClientOperation(int clientUid, String remotelyProvisionedComponent,
            int operationId) {
        mClientUid = clientUid;
        mRemotelyProvisionedComponent = remotelyProvisionedComponent;
        mOperationId = operationId;
        mTimer.start();
    }

    public void setResult(Result result) {
        mResult = result.getAtomValue();
    }

    /** Record the atoms for this metrics object. */
    @Override
    public void close() {
        mTimer.stop();
        RkpdStatsLog.write(RkpdStatsLog.RKPD_CLIENT_OPERATION, mRemotelyProvisionedComponent,
                mClientUid, mOperationId, mResult, mTimer.getElapsedMillis());
    }
}
