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

package com.android.adservices.service.measurement.reporting;

import android.content.Context;
import android.net.Uri;

import com.android.internal.annotations.VisibleForTesting;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Class to construct the full reporting url specific to aggregate reports.
 */
public class AggregateReportSender extends MeasurementReportSender {

    @VisibleForTesting
    public static final String AGGREGATE_ATTRIBUTION_REPORT_URI_PATH =
            ".well-known/attribution-reporting/report-aggregate-attribution";

    @VisibleForTesting
    public static final String DEBUG_AGGREGATE_ATTRIBUTION_REPORT_URI_PATH =
            ".well-known/attribution-reporting/debug/report-aggregate-attribution";

    private String mReportUriPath;

    public AggregateReportSender(boolean isDebugReport, Context context) {
        super(context);
        this.mReportUriPath = AGGREGATE_ATTRIBUTION_REPORT_URI_PATH;
        if (isDebugReport) {
            this.mReportUriPath = DEBUG_AGGREGATE_ATTRIBUTION_REPORT_URI_PATH;
        }
    }

    /** The report uri path. */
    @VisibleForTesting
    public String getReportUriPath() {
        return mReportUriPath;
    }

    /**
     * Given a String reportingOrigin, returns the URL Object
     * of the URL to send the POST request to.
     */
    URL createReportingFullUrl(Uri adTechDomain)
            throws MalformedURLException {
        Uri reportingFullUrl = Uri.withAppendedPath(adTechDomain, mReportUriPath);
        return new URL(reportingFullUrl.toString());
    }
}
