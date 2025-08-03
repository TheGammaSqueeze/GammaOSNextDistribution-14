/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.imsserviceentitlement.utils;

import static android.os.SystemClock.uptimeMillis;

import static com.android.imsserviceentitlement.ImsServiceEntitlementStatsLog.IMS_SERVICE_ENTITLEMENT_UPDATED;
import static com.android.imsserviceentitlement.ImsServiceEntitlementStatsLog.IMS_SERVICE_ENTITLEMENT_UPDATED__PURPOSE__UNKNOWN_PURPOSE;

import com.android.imsserviceentitlement.ImsServiceEntitlementStatsLog;

/** Utility for writing the metrics logs. */
public class MetricsLogger {
    private final TelephonyUtils mTelephonyUtils;

    private int mPurpose = IMS_SERVICE_ENTITLEMENT_UPDATED__PURPOSE__UNKNOWN_PURPOSE;
    private long mStartTime = uptimeMillis();

    public MetricsLogger(TelephonyUtils telephonyUtils) {
        mTelephonyUtils = telephonyUtils;
    }

    /** Starts the log session for the purpose as well as record the start time. */
    public void start(int purpose) {
        mStartTime = uptimeMillis();
        mPurpose = purpose;
    }

    /** Writes the metrics log. */
    public void write(int appId, int appResult) {
        ImsServiceEntitlementStatsLog.write(
                IMS_SERVICE_ENTITLEMENT_UPDATED,
                mTelephonyUtils.getCarrierId(),
                mTelephonyUtils.getSpecificCarrierId(),
                mPurpose,
                appId,
                appResult,
                uptimeMillis() - mStartTime);
    }
}
