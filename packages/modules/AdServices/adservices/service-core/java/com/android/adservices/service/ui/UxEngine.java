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

package com.android.adservices.service.ui;

import static com.android.adservices.service.ui.constants.DebugMessages.NO_ENROLLMENT_CHANNEL_AVAILABLE_MESSAGE;

import android.adservices.common.AdServicesStates;
import android.content.Context;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.android.adservices.LogUtil;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.stats.UiStatsLogger;
import com.android.adservices.service.ui.data.UxStatesManager;
import com.android.adservices.service.ui.enrollment.collection.PrivacySandboxEnrollmentChannelCollection;
import com.android.adservices.service.ui.util.UxEngineUtil;
import com.android.adservices.service.ui.ux.collection.PrivacySandboxUxCollection;

/* UxEngine for coordinating UX components such as UXs, enrollment channels, and modes. */
@RequiresApi(Build.VERSION_CODES.S)
public class UxEngine {
    private final ConsentManager mConsentManager;
    private final UxStatesManager mUxStatesManager;
    private final UxEngineUtil mUxEngineUtil;
    private final Context mContext;

    // TO-DO(b/287060615): Clean up dependencies between UX classes.
    UxEngine(
            Context context,
            ConsentManager consentManager,
            UxStatesManager uxStatesManager,
            UxEngineUtil uxEngineUtil) {
        mContext = context;
        mConsentManager = consentManager;
        mUxStatesManager = uxStatesManager;
        mUxEngineUtil = uxEngineUtil;
    }

    /**
     * Returns an instance of the UxEngine. This method should only be invoked by the common
     * manager.
     */
    public static UxEngine getInstance(Context context) {
        LogUtil.d("UxEngine getInstance called.");
        return new UxEngine(
                context,
                ConsentManager.getInstance(),
                UxStatesManager.getInstance(),
                UxEngineUtil.getInstance());
    }

    /**
     * Starts the UxEngine. In which the general UX flow would be carried out as the engine
     * orchestrates tasks and events between various UX components.
     */
    public void start(AdServicesStates adServicesStates) {
        mUxStatesManager.persistAdServicesStates(adServicesStates);
        LogUtil.d("AdServices states persisted.");

        PrivacySandboxUxCollection eligibleUx =
                mUxEngineUtil.getEligibleUxCollection(mConsentManager, mUxStatesManager);

        PrivacySandboxEnrollmentChannelCollection eligibleEnrollmentChannel =
                mUxEngineUtil.getEligibleEnrollmentChannelCollection(
                        eligibleUx, mConsentManager, mUxStatesManager);

        // TO-DO: Add an UNSUPPORTED_ENROLLMENT_CHANNEL, rather than using null handling.
        if (eligibleEnrollmentChannel != null) {
            // UX and channel should only be updated when an enrollment channel exists.
            mConsentManager.setUx(eligibleUx);
            mConsentManager.setEnrollmentChannel(eligibleUx, eligibleEnrollmentChannel);
            LogUtil.d(
                    String.format(
                            "Ux: %s, Enrollment Channel: %s",
                            eligibleUx, eligibleEnrollmentChannel));

            // Entry point request should not trigger enrollment but should refresh the UX states.
            if (adServicesStates.isPrivacySandboxUiRequest()) {
                UiStatsLogger.logEntryPointClicked();
                return;
            }

            LogUtil.d("Running enrollment logic.");
            eligibleUx
                    .getUx()
                    .handleEnrollment(
                            eligibleEnrollmentChannel.getEnrollmentChannel(),
                            mContext,
                            mConsentManager);

            mUxEngineUtil.startBackgroundTasksUponConsent(
                    eligibleUx, mContext, FlagsFactory.getFlags());

            return;
        }

        LogUtil.d(NO_ENROLLMENT_CHANNEL_AVAILABLE_MESSAGE);
    }
}
