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

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.net.Uri;

import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.stats.AdServicesLoggerImpl;

import org.json.JSONArray;
import org.json.JSONException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;

/** A wrapper class to expose a constructor for DebugReportingJobHandler in testing. */
public class DebugReportingJobHandlerWrapper {
    public static Object[] spyPerformScheduledPendingReports(
            DatastoreManager datastoreManager, Context context)
            throws IOException, JSONException {
        // Set up debug reporting job handler spy
        DebugReportingJobHandler debugReportingJobHandler =
                Mockito.spy(
                        new DebugReportingJobHandler(
                                datastoreManager,
                                FlagsFactory.getFlags(),
                                AdServicesLoggerImpl.getInstance(),
                                context));
        Mockito.doReturn(200).when(debugReportingJobHandler).makeHttpPostRequest(any(), any());

        debugReportingJobHandler.performScheduledPendingReports();
        ArgumentCaptor<Uri> reportDestination = ArgumentCaptor.forClass(Uri.class);
        ArgumentCaptor<DebugReport> debugReport = ArgumentCaptor.forClass(DebugReport.class);
        verify(debugReportingJobHandler, atLeast(0)).createReportJsonPayload(debugReport.capture());
        ArgumentCaptor<JSONArray> reportPayload = ArgumentCaptor.forClass(JSONArray.class);
        verify(debugReportingJobHandler, atLeast(0))
                .makeHttpPostRequest(reportDestination.capture(), reportPayload.capture());

        debugReportingJobHandler.performScheduledPendingReports();

        // Collect actual reports
        return new Object[] {
            debugReport.getAllValues(),
            reportDestination.getAllValues(),
            reportPayload.getAllValues()
        };
    }
}
