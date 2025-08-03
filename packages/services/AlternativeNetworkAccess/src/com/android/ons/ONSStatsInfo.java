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

package com.android.ons;

import com.android.ons.ONSProfileActivator.Result;
import com.android.ons.ONSProfileDownloader.DownloadRetryResultCode;

public final class ONSStatsInfo {
    public static final int INVALID_VALUE = -1;
    private Result mProvisioningResult = null;
    private DownloadRetryResultCode mDownloadResult = null;
    private int mPrimarySimSubId = INVALID_VALUE;
    private int mOppSimCarrierId = INVALID_VALUE;
    private int mRetryCount = INVALID_VALUE;
    private int mDetailedErrCode = INVALID_VALUE;
    private boolean mIsWifiConnected = false;
    private boolean mIsProvisioningResultUpdated = false;

    public Result getProvisioningResult() {
        return mProvisioningResult;
    }

    public DownloadRetryResultCode getDownloadResult() {
        return mDownloadResult;
    }

    public int getPrimarySimSubId() {
        return mPrimarySimSubId;
    }

    public int getOppSimCarrierId() {
        return mOppSimCarrierId;
    }

    public int getRetryCount() {
        return mRetryCount;
    }

    public int getDetailedErrCode() {
        return mDetailedErrCode;
    }

    public boolean isWifiConnected() {
        return mIsWifiConnected;
    }

    public boolean isProvisioningResultUpdated() {
        return mIsProvisioningResultUpdated;
    }

    public ONSStatsInfo setProvisioningResult(Result result) {
        mProvisioningResult = result;
        mDownloadResult = null;
        mIsProvisioningResultUpdated = true;
        return this;
    }

    public ONSStatsInfo setDownloadResult(DownloadRetryResultCode retryResultCode) {
        mProvisioningResult = null;
        mDownloadResult = retryResultCode;
        mIsProvisioningResultUpdated = false;
        return this;
    }

    public ONSStatsInfo setPrimarySimSubId(int primarySimSubId) {
        mPrimarySimSubId = primarySimSubId;
        return this;
    }

    public ONSStatsInfo setOppSimCarrierId(int oppSimCarrierId) {
        mOppSimCarrierId = oppSimCarrierId;
        return this;
    }

    public ONSStatsInfo setRetryCount(int retryCount) {
        mRetryCount = retryCount;
        return this;
    }

    public ONSStatsInfo setDetailedErrCode(int detailedErrCode) {
        mDetailedErrCode = detailedErrCode;
        return this;
    }

    public ONSStatsInfo setWifiConnected(boolean wifiConnected) {
        mIsWifiConnected = wifiConnected;
        return this;
    }

    @Override
    public String toString() {
        return "ONSStatsInfo{"
                + "mProvisioningResult="
                + mProvisioningResult
                + ", mDownloadResult="
                + mDownloadResult
                + ", mPrimarySimSubId="
                + mPrimarySimSubId
                + ", mOppSimCarrierId="
                + mOppSimCarrierId
                + ", mRetryCount="
                + mRetryCount
                + ", mDetailedErrCode="
                + mDetailedErrCode
                + ", mIsWifiConnected="
                + mIsWifiConnected
                + ", mIsProvisioningResultUpdated="
                + mIsProvisioningResultUpdated
                + '}';
    }
}
