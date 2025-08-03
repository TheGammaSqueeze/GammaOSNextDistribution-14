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
import com.android.adservices.service.Flags;
import com.android.adservices.service.measurement.EventReport;

import org.json.JSONException;
import org.json.JSONObject;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;

/**
 * A wrapper class to expose a constructor for EventReportingJobHandler in testing.
 */
public class EventReportingJobHandlerWrapper {
    public static Object[] spyPerformScheduledPendingReportsInWindow(
            DatastoreManager datastoreManager,
            long windowStartTime,
            long windowEndTime,
            boolean isDebugInstance,
            Flags flags,
            Context context)
            throws IOException, JSONException {
        // Set up event reporting job handler spy
        EventReportingJobHandler eventReportingJobHandler =
                Mockito.spy(
                        new EventReportingJobHandler(datastoreManager, flags, context)
                                .setIsDebugInstance(isDebugInstance));
        Mockito.doReturn(200).when(eventReportingJobHandler)
                .makeHttpPostRequest(any(), any());

        // Perform event reports and capture arguments
        eventReportingJobHandler.performScheduledPendingReportsInWindow(
                windowStartTime, windowEndTime);
        ArgumentCaptor<Uri> eventDestination = ArgumentCaptor.forClass(Uri.class);
        ArgumentCaptor<EventReport> eventReport = ArgumentCaptor.forClass(EventReport.class);
        verify(eventReportingJobHandler, atLeast(0))
                .createReportJsonPayload(eventReport.capture());
        ArgumentCaptor<JSONObject> eventPayload = ArgumentCaptor.forClass(JSONObject.class);
        verify(eventReportingJobHandler, atLeast(0))
                .makeHttpPostRequest(eventDestination.capture(), eventPayload.capture());

        eventReportingJobHandler.performScheduledPendingReportsInWindow(
                windowStartTime, windowEndTime);

        // Collect actual reports
        return new Object[]{
                eventReport.getAllValues(),
                eventDestination.getAllValues(),
                eventPayload.getAllValues()
        };
    }
}
