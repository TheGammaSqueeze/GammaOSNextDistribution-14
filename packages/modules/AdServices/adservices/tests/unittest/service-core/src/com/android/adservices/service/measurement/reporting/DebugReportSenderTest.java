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

import static org.junit.Assert.assertEquals;

import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;

public class DebugReportSenderTest {

    /** Tests posting a report with a mock HttpUrlConnection. */
    @Test
    public void testSendDebugReport() throws JSONException, IOException {
        HttpURLConnection httpUrlConnection = Mockito.mock(HttpURLConnection.class);

        OutputStream outputStream = new ByteArrayOutputStream();
        Mockito.when(httpUrlConnection.getOutputStream()).thenReturn(outputStream);
        Mockito.when(httpUrlConnection.getResponseCode()).thenReturn(200);

        Uri reportingOrigin = Uri.parse("https://ad-tech.example");
        JSONObject eventReportJson = createDebugReport().toPayloadJson();

        DebugReportSender spyDebugReportSender =
                Mockito.spy(new DebugReportSender(ApplicationProvider.getApplicationContext()));

        Mockito.doReturn(httpUrlConnection)
                .when(spyDebugReportSender)
                .createHttpUrlConnection(Mockito.any());

        int responseCode = spyDebugReportSender.sendReport(reportingOrigin, eventReportJson);

        assertEquals(outputStream.toString(), eventReportJson.toString());
        assertEquals(responseCode, 200);
    }

    private DebugReport createDebugReport() {
        return new DebugReport.Builder()
                .setId("reportId")
                .setType("trigger-event-deduplicated")
                .setBody(
                        " {\n"
                                + "      \"attribution_destination\":"
                                + " \"https://destination.example\",\n"
                                + "      \"source_event_id\": \"45623\"\n"
                                + "    }")
                .setEnrollmentId("1")
                .build();
    }
}
