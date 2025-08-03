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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.service.measurement.util.UnsignedLong;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.util.List;

public class EventReportSenderTest {

    private static final List<Uri> ATTRIBUTION_DESTINATIONS =
            List.of(Uri.parse("https://toasters.example"));
    private static final String SCHEDULED_REPORT_TIME = "1675459163";
    private static final UnsignedLong SOURCE_EVENT_ID = new UnsignedLong(12345L);
    private static final UnsignedLong TRIGGER_DATA = new UnsignedLong(2L);
    private static final String REPORT_ID = "678";
    private static final String SOURCE_TYPE = "event";
    private static final double RANDOMIZED_TRIGGER_RATE = 0.0024;

    protected static final Context sContext = ApplicationProvider.getApplicationContext();

    /**
     * Example event report payload.
     */
    private EventReportPayload createEventReportPayloadExample1() {
        return new EventReportPayload.Builder()
                .setAttributionDestination(ATTRIBUTION_DESTINATIONS)
                .setScheduledReportTime(SCHEDULED_REPORT_TIME)
                .setSourceEventId(SOURCE_EVENT_ID)
                .setTriggerData(TRIGGER_DATA)
                .setReportId(REPORT_ID)
                .setSourceType(SOURCE_TYPE)
                .setRandomizedTriggerRate(RANDOMIZED_TRIGGER_RATE)
                .build();
    }

    /**
     *
     * Tests posting a report with a mock HttpUrlConnection.
     */
    @Test
    public void testSendEventReport() throws JSONException, IOException {
        HttpURLConnection httpUrlConnection = Mockito.mock(HttpURLConnection.class);

        OutputStream outputStream = new ByteArrayOutputStream();
        Mockito.when(httpUrlConnection.getOutputStream()).thenReturn(outputStream);
        Mockito.when(httpUrlConnection.getResponseCode()).thenReturn(200);

        Uri reportingOrigin = Uri.parse("https://ad-tech.example");
        JSONObject eventReportJson = createEventReportPayloadExample1().toJson();

        EventReportSender eventReportSender = new EventReportSender(false, sContext);
        EventReportSender spyEventReportSender = Mockito.spy(eventReportSender);

        Mockito.doReturn(httpUrlConnection).when(spyEventReportSender)
                .createHttpUrlConnection(Mockito.any());

        int responseCode = spyEventReportSender.sendReport(reportingOrigin,
                eventReportJson);

        assertEquals(outputStream.toString(), eventReportJson.toString());
        assertEquals(responseCode, 200);
    }

    @Test
    public void testDebugReportUriPath() {
        assertThat(new EventReportSender(false, sContext).getReportUriPath())
                .isEqualTo(EventReportSender.EVENT_ATTRIBUTION_REPORT_URI_PATH);
        assertThat(new EventReportSender(true, sContext).getReportUriPath())
                .isEqualTo(EventReportSender.DEBUG_EVENT_ATTRIBUTION_REPORT_URI_PATH);
    }
}
