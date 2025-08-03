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
package com.android.adservices.measurement;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_CLASS__MEASUREMENT;

import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.RequiresApi;

import com.android.adservices.LogUtil;
import com.android.adservices.download.MddJobService;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.PackageChangedReceiver;
import com.android.adservices.service.consent.AdServicesApiType;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.encryptionkey.EncryptionKeyJobService;
import com.android.adservices.service.measurement.CachedFlags;
import com.android.adservices.service.measurement.DeleteExpiredJobService;
import com.android.adservices.service.measurement.DeleteUninstalledJobService;
import com.android.adservices.service.measurement.MeasurementServiceImpl;
import com.android.adservices.service.measurement.attribution.AttributionFallbackJobService;
import com.android.adservices.service.measurement.attribution.AttributionJobService;
import com.android.adservices.service.measurement.registration.AsyncRegistrationFallbackJobService;
import com.android.adservices.service.measurement.registration.AsyncRegistrationQueueJobService;
import com.android.adservices.service.measurement.reporting.AggregateFallbackReportingJobService;
import com.android.adservices.service.measurement.reporting.AggregateReportingJobService;
import com.android.adservices.service.measurement.reporting.DebugReportingFallbackJobService;
import com.android.adservices.service.measurement.reporting.EventFallbackReportingJobService;
import com.android.adservices.service.measurement.reporting.EventReportingJobService;
import com.android.adservices.service.measurement.reporting.VerboseDebugReportingFallbackJobService;
import com.android.adservices.shared.util.Clock;

/** Measurement Service */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public class MeasurementService extends Service {

    /** The binder service. This field must only be accessed on the main thread. */
    private MeasurementServiceImpl mMeasurementService;

    @Override
    public void onCreate() {
        super.onCreate();
        Flags flags = FlagsFactory.getFlags();
        if (!flags.getMeasurementEnabled()) {
            LogUtil.e("Measurement API is disabled");
            return;
        }

        if (mMeasurementService == null) {
            final AppImportanceFilter appImportanceFilter =
                    AppImportanceFilter.create(
                            this,
                            AD_SERVICES_API_CALLED__API_CLASS__MEASUREMENT,
                            () -> FlagsFactory.getFlags().getForegroundStatuslLevelForValidation());

            mMeasurementService =
                    new MeasurementServiceImpl(
                            this,
                            Clock.getInstance(),
                            ConsentManager.getInstance(),
                            new CachedFlags(flags),
                            appImportanceFilter);
        }

        if (hasUserConsent()) {
            PackageChangedReceiver.enableReceiver(this, flags);
            schedulePeriodicJobsIfNeeded();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (!FlagsFactory.getFlags().getMeasurementEnabled()) {
            LogUtil.e("Measurement API is disabled");
            // Return null so that clients can not bind to the service.
            return null;
        }
        return mMeasurementService;
    }

    private boolean hasUserConsent() {
        return ConsentManager.getInstance().getConsent(AdServicesApiType.MEASUREMENTS).isGiven();
    }

    private void schedulePeriodicJobsIfNeeded() {
        AggregateReportingJobService.scheduleIfNeeded(this, false);
        AggregateFallbackReportingJobService.scheduleIfNeeded(this, false);
        AttributionJobService.scheduleIfNeeded(this, false);
        AttributionFallbackJobService.scheduleIfNeeded(this, false);
        EventReportingJobService.scheduleIfNeeded(this, false);
        EventFallbackReportingJobService.scheduleIfNeeded(this, false);
        DeleteExpiredJobService.scheduleIfNeeded(this, false);
        DeleteUninstalledJobService.scheduleIfNeeded(this, false);
        MddJobService.scheduleIfNeeded(this, false);
        AsyncRegistrationQueueJobService.scheduleIfNeeded(this, false);
        AsyncRegistrationFallbackJobService.scheduleIfNeeded(this, false);
        DebugReportingFallbackJobService.scheduleIfNeeded(this, false);
        VerboseDebugReportingFallbackJobService.scheduleIfNeeded(this, false);
        EncryptionKeyJobService.scheduleIfNeeded(this, false);
    }
}
