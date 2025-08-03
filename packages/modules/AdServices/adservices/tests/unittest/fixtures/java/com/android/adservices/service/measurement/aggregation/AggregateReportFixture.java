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

package com.android.adservices.service.measurement.aggregation;

import android.net.Uri;

import com.android.adservices.LogUtil;
import com.android.adservices.common.WebUtil;
import com.android.adservices.service.measurement.EventReport;
import com.android.adservices.service.measurement.util.UnsignedLong;

import org.json.JSONException;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class AggregateReportFixture {
    private AggregateReportFixture() {}

    private static final long MIN_TIME_MS = TimeUnit.MINUTES.toMillis(10L);
    private static final long MAX_TIME_MS = TimeUnit.MINUTES.toMillis(60L);

    // Assume the field values in this AggregateReport.Builder have no relation to the field
    // values in {@link ValidAggregateReportParams}
    public static AggregateReport.Builder getValidAggregateReportBuilder() {
        return new AggregateReport.Builder()
                .setPublisher(ValidAggregateReportParams.PUBLISHER)
                .setAttributionDestination(ValidAggregateReportParams.ATTRIBUTION_DESTINATION)
                .setSourceRegistrationTime(ValidAggregateReportParams.SOURCE_REGISTRATION_TIME)
                .setScheduledReportTime(ValidAggregateReportParams.TRIGGER_TIME + getRandomTime())
                .setEnrollmentId(ValidAggregateReportParams.ENROLLMENT_ID)
                .setSourceDebugKey(ValidAggregateReportParams.SOURCE_DEBUG_KEY)
                .setTriggerDebugKey(ValidAggregateReportParams.TRIGGER_DEBUG_KEY)
                .setDebugCleartextPayload(ValidAggregateReportParams.getDebugPayload())
                .setStatus(EventReport.Status.PENDING)
                .setDebugReportStatus(EventReport.DebugReportStatus.PENDING)
                .setDedupKey(ValidAggregateReportParams.DEDUP_KEY)
                .setRegistrationOrigin(ValidAggregateReportParams.REGISTRATION_ORIGIN)
                .setAggregationCoordinatorOrigin(
                        ValidAggregateReportParams.AGGREGATION_COORDINATOR_ORIGIN)
                .setIsFakeReport(false);
    }

    public static AggregateReport getValidAggregateReport() {
        return getValidAggregateReportBuilder().build();
    }

    public static class ValidAggregateReportParams {
        public static final Uri PUBLISHER = Uri.parse("android-app://com.registrant");
        public static final Uri ATTRIBUTION_DESTINATION =
                Uri.parse("android-app://com.destination");
        public static final long SOURCE_REGISTRATION_TIME = 8640000000L;
        public static final long TRIGGER_TIME = 8640000000L;
        public static final UnsignedLong SOURCE_DEBUG_KEY = new UnsignedLong(43254545L);
        public static final UnsignedLong TRIGGER_DEBUG_KEY = new UnsignedLong(67878545L);
        public static final String ENROLLMENT_ID = "enrollment-id";
        public static final UnsignedLong DEDUP_KEY = new UnsignedLong(67878545L);
        public static final Uri REGISTRATION_ORIGIN =
                WebUtil.validUri("https://subdomain.example.test");
        public static final Uri AGGREGATION_COORDINATOR_ORIGIN =
                WebUtil.validUri("https://coordinator.example.test");

        public static final String getDebugPayload() {
            List<AggregateHistogramContribution> contributions = new ArrayList<>();
            AggregateHistogramContribution contribution1 =
                    new AggregateHistogramContribution.Builder()
                            .setKey(BigInteger.valueOf(1369L)).setValue(32768).build();
            AggregateHistogramContribution contribution2 =
                    new AggregateHistogramContribution.Builder()
                            .setKey(BigInteger.valueOf(3461L)).setValue(1664).build();
            contributions.add(contribution1);
            contributions.add(contribution2);
            String debugPayload = null;
            try {
                debugPayload = AggregateReport.generateDebugPayload(contributions);
            } catch (JSONException e) {
                LogUtil.e("JSONException when generating debug payload.");
            }
            return debugPayload;
        }
    }

    private static long getRandomTime() {
        return (long) ((Math.random() * (MAX_TIME_MS - MIN_TIME_MS)) + MIN_TIME_MS);
    }
}
