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

package com.android.adservices.service.measurement.attribution;


import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.service.Flags;
import com.android.adservices.service.measurement.noising.SourceNoiseHandler;
import com.android.adservices.service.measurement.reporting.DebugReportApi;
import com.android.adservices.service.measurement.reporting.EventReportWindowCalcDelegate;
import com.android.adservices.service.stats.AdServicesLogger;

/**
 * A wrapper class to expose a constructor for AttributionJobHandler in testing.
 */
public class AttributionJobHandlerWrapper {
    private final AttributionJobHandler mAttributionJobHandler;

    public AttributionJobHandlerWrapper(
            DatastoreManager datastoreManager,
            Flags flags,
            DebugReportApi debugReportApi,
            EventReportWindowCalcDelegate eventReportWindowCalcDelegate,
            SourceNoiseHandler sourceNoiseHandler,
            AdServicesLogger logger) {
        this.mAttributionJobHandler =
                new AttributionJobHandler(
                        datastoreManager,
                        flags,
                        debugReportApi,
                        eventReportWindowCalcDelegate,
                        sourceNoiseHandler,
                        logger,
                        new XnaSourceCreator(flags));
    }

    public boolean performPendingAttributions() {
        return AttributionJobHandler.ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED
                == mAttributionJobHandler.performPendingAttributions();
    }
}
