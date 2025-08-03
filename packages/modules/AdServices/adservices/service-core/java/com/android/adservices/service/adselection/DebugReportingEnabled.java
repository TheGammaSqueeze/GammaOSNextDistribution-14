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

package com.android.adservices.service.adselection;

import android.annotation.NonNull;
import android.content.Context;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.android.adservices.data.adselection.AdSelectionDebugReportDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.devapi.DevContext;

/** Class to provide implementation when Debug Reporting is enabled for on device auction. */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public class DebugReportingEnabled extends DebugReporting {

    private final Context mContext;
    private final boolean mShouldSendReportImmediately;
    private final AdServicesHttpsClient mAdServicesHttpsClient;
    private final DevContext mDevContext;
    private final AdSelectionDebugReportDao mAdSelectionDebugReportDao;

    public DebugReportingEnabled(
            @NonNull Context context,
            @NonNull Flags flags,
            @NonNull AdServicesHttpsClient adServicesHttpsClient,
            @NonNull DevContext devContext,
            @NonNull AdSelectionDebugReportDao adSelectionDebugReportDao) {
        mContext = context;
        mAdServicesHttpsClient = adServicesHttpsClient;
        mDevContext = devContext;
        mAdSelectionDebugReportDao = adSelectionDebugReportDao;
        mShouldSendReportImmediately = shouldSendDebugReportsImmediately(flags);
    }

    @Override
    public DebugReportingScriptStrategy getScriptStrategy() {
        return new DebugReportingEnabledScriptStrategy();
    }

    @Override
    public DebugReportSenderStrategy getSenderStrategy() {
        return mShouldSendReportImmediately
                ? new DebugReportSenderStrategyHttpImpl(mAdServicesHttpsClient, mDevContext)
                : new DebugReportSenderStrategyBatchImpl(
                        mContext, mAdSelectionDebugReportDao, mDevContext);
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    private boolean shouldSendDebugReportsImmediately(Flags flags) {
        return flags.getFledgeEventLevelDebugReportSendImmediately();
    }
}
