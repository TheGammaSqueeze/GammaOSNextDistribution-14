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
import android.annotation.RequiresApi;
import android.content.Context;
import android.os.Build;

import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.AdSelectionServiceFilter;
import com.android.adservices.service.common.BinderFlagReader;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.measurement.MeasurementImpl;
import com.android.adservices.service.stats.AdServicesLogger;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.Objects;
import java.util.concurrent.ExecutorService;

/** Factory for {@link EventReporter} implementations. */
@RequiresApi(Build.VERSION_CODES.S)
public final class EventReporterFactory {
    // Flags determining which instance to return.
    private final boolean mFledgeRegisterAdBeaconEnabled;
    private final boolean mFledgeMeasurementReportAndRegisterEventApiEnabled;
    private final boolean mFledgeMeasurementReportAndRegisterEventApiFallbackEnabled;

    // Values to construct the chosen instance with.
    @NonNull private final AdSelectionEntryDao mAdSelectionEntryDao;
    @NonNull private final AdServicesHttpsClient mAdServicesHttpsClient;
    @NonNull final ListeningExecutorService mLightweightExecutorService;
    @NonNull private final ListeningExecutorService mBackgroundExecutorService;
    @NonNull private final AdServicesLogger mAdServicesLogger;
    @NonNull private final Flags mFlags;
    @NonNull private final AdSelectionServiceFilter mAdSelectionServiceFilter;
    private int mCallerUid;
    @NonNull private final FledgeAuthorizationFilter mFledgeAuthorizationFilter;
    @NonNull private final DevContext mDevContext;
    @NonNull private final MeasurementImpl mMeasurementService;
    @NonNull private final ConsentManager mConsentManager;
    @NonNull private final Context mContext;
    private final boolean mShouldUseUnifiedTables;

    public EventReporterFactory(
            @NonNull AdSelectionEntryDao adSelectionEntryDao,
            @NonNull AdServicesHttpsClient adServicesHttpsClient,
            @NonNull ExecutorService lightweightExecutorService,
            @NonNull ExecutorService backgroundExecutorService,
            @NonNull AdServicesLogger adServicesLogger,
            @NonNull Flags flags,
            @NonNull AdSelectionServiceFilter adSelectionServiceFilter,
            int callerUid,
            @NonNull FledgeAuthorizationFilter fledgeAuthorizationFilter,
            @NonNull DevContext devContext,
            @NonNull MeasurementImpl measurementService,
            @NonNull ConsentManager consentManager,
            @NonNull Context context,
            boolean shouldUseUnifiedTables) {
        Objects.requireNonNull(adSelectionEntryDao);
        Objects.requireNonNull(adServicesHttpsClient);
        Objects.requireNonNull(lightweightExecutorService);
        Objects.requireNonNull(backgroundExecutorService);
        Objects.requireNonNull(adServicesLogger);
        Objects.requireNonNull(flags);
        Objects.requireNonNull(adSelectionServiceFilter);
        Objects.requireNonNull(fledgeAuthorizationFilter);
        Objects.requireNonNull(devContext);
        Objects.requireNonNull(measurementService);
        Objects.requireNonNull(context);
        Objects.requireNonNull(consentManager);

        mAdSelectionEntryDao = adSelectionEntryDao;
        mAdServicesHttpsClient = adServicesHttpsClient;
        mLightweightExecutorService = MoreExecutors.listeningDecorator(lightweightExecutorService);
        mBackgroundExecutorService = MoreExecutors.listeningDecorator(backgroundExecutorService);
        mAdServicesLogger = adServicesLogger;
        mFlags = flags;
        mAdSelectionServiceFilter = adSelectionServiceFilter;
        mCallerUid = callerUid;
        mFledgeAuthorizationFilter = fledgeAuthorizationFilter;
        mDevContext = devContext;
        mMeasurementService = measurementService;
        mConsentManager = consentManager;
        mContext = context;

        mFledgeRegisterAdBeaconEnabled =
                BinderFlagReader.readFlag(flags::getFledgeRegisterAdBeaconEnabled);
        mFledgeMeasurementReportAndRegisterEventApiEnabled =
                BinderFlagReader.readFlag(
                        flags::getFledgeMeasurementReportAndRegisterEventApiEnabled);
        mFledgeMeasurementReportAndRegisterEventApiFallbackEnabled =
                BinderFlagReader.readFlag(
                        flags::getFledgeMeasurementReportAndRegisterEventApiFallbackEnabled);
        mShouldUseUnifiedTables = shouldUseUnifiedTables;
    }

    /**
     * @return an {@link EventReporter} instance.
     */
    public EventReporter getEventReporter() {
        // If reportEvent itself is disabled: return an instance that immediately fails if called.
        if (!mFledgeRegisterAdBeaconEnabled) {
            return new ReportEventDisabledImpl(
                    mAdSelectionEntryDao,
                    mAdServicesHttpsClient,
                    mLightweightExecutorService,
                    mBackgroundExecutorService,
                    mAdServicesLogger,
                    mFlags,
                    mAdSelectionServiceFilter,
                    mCallerUid,
                    mFledgeAuthorizationFilter,
                    mDevContext,
                    mShouldUseUnifiedTables);
        }

        // If reportEvent is enabled but reportAndRegisterEvent is disabled: return an instance
        // that only reports the event.
        // The event will be reported using a single network call triggered by PA.
        if (!mFledgeMeasurementReportAndRegisterEventApiEnabled) {
            return new ReportEventImpl(
                    mAdSelectionEntryDao,
                    mAdServicesHttpsClient,
                    mLightweightExecutorService,
                    mBackgroundExecutorService,
                    mAdServicesLogger,
                    mFlags,
                    mAdSelectionServiceFilter,
                    mCallerUid,
                    mFledgeAuthorizationFilter,
                    mDevContext,
                    mShouldUseUnifiedTables);
        }

        // If reportEvent and reportAndRegisterEvent are enabled but reportAndRegisterEventFallback
        // is disabled: return an instance that reports and registers the event.
        // The event will be reported and registered using a single network call triggered by ARA.
        if (!mFledgeMeasurementReportAndRegisterEventApiFallbackEnabled) {
            return new ReportAndRegisterEventImpl(
                    mAdSelectionEntryDao,
                    mAdServicesHttpsClient,
                    mLightweightExecutorService,
                    mBackgroundExecutorService,
                    mAdServicesLogger,
                    mFlags,
                    mAdSelectionServiceFilter,
                    mCallerUid,
                    mFledgeAuthorizationFilter,
                    mDevContext,
                    mMeasurementService,
                    mConsentManager,
                    mContext,
                    mShouldUseUnifiedTables);
        }

        // If reportEvent, reportAndRegisterEvent and reportAndRegisterEventFallback are enabled:
        // return an instance that reports and registers the event.
        // The event will be separately reported and registered by PA and ARA respectively using a
        // network call each.
        return new ReportAndRegisterEventFallbackImpl(
                mAdSelectionEntryDao,
                mAdServicesHttpsClient,
                mLightweightExecutorService,
                mBackgroundExecutorService,
                mAdServicesLogger,
                mFlags,
                mAdSelectionServiceFilter,
                mCallerUid,
                mFledgeAuthorizationFilter,
                mDevContext,
                mMeasurementService,
                mConsentManager,
                mContext,
                mShouldUseUnifiedTables);
    }
}
