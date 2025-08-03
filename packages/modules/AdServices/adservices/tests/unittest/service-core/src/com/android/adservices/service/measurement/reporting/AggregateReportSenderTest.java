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

import android.content.Context;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.measurement.aggregation.AggregateCryptoFixture;
import com.android.modules.utils.testing.TestableDeviceConfig;

import com.google.common.truth.Truth;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

public class AggregateReportSenderTest {

    @Rule
    public final TestableDeviceConfig.TestableDeviceConfigRule mDeviceConfigRule =
            new TestableDeviceConfig.TestableDeviceConfigRule();

    private static final String ATTRIBUTION_DESTINATION = "https://attribution.destination";
    private static final String SOURCE_REGISTRATION_TIME = "1246174152155";
    private static final String SCHEDULED_REPORT_TIME = "1246174158155";
    private static final String VERSION = "1234";
    private static final String REPORT_ID = "A1";
    private static final String REPORTING_ORIGIN = "https://adtech.domain";
    private static final String COORDINATOR_ORIGIN = "https://coordinator.origin";
    private static final String DEBUG_CLEARTEXT_PAYLOAD = "{\"operation\":\"histogram\","
            + "\"data\":[{\"bucket\":\"1369\",\"value\":32768},{\"bucket\":\"3461\","
            + "\"value\":1664}]}";

    protected static final Context sContext = ApplicationProvider.getApplicationContext();

    private AggregateReportBody createAggregateReportBodyExample1() {
        return new AggregateReportBody.Builder()
                .setAttributionDestination(ATTRIBUTION_DESTINATION)
                .setSourceRegistrationTime(SOURCE_REGISTRATION_TIME)
                .setScheduledReportTime(SCHEDULED_REPORT_TIME)
                .setApiVersion(VERSION)
                .setReportId(REPORT_ID)
                .setReportingOrigin(REPORTING_ORIGIN)
                .setDebugCleartextPayload(DEBUG_CLEARTEXT_PAYLOAD)
                .setAggregationCoordinatorOrigin(Uri.parse(COORDINATOR_ORIGIN))
                .build();
    }

    /**
     *
     * Tests posting a report with a mock HttpUrlConnection.
     */
    @Test
    public void testSendAggregateReport() throws JSONException, IOException {
        HttpURLConnection httpUrlConnection = Mockito.mock(HttpURLConnection.class);

        OutputStream outputStream = new ByteArrayOutputStream();
        Mockito.when(httpUrlConnection.getOutputStream()).thenReturn(outputStream);
        Mockito.when(httpUrlConnection.getResponseCode()).thenReturn(200);

        JSONObject aggregateReportJson =
                createAggregateReportBodyExample1()
                        .toJson(AggregateCryptoFixture.getKey(), FlagsFactory.getFlags());
        Uri reportingOrigin = Uri.parse(REPORTING_ORIGIN);

        AggregateReportSender aggregateReportSender = new AggregateReportSender(false, sContext);
        AggregateReportSender spyAggregateReportSender = Mockito.spy(aggregateReportSender);

        Mockito.doReturn(httpUrlConnection).when(spyAggregateReportSender)
                .createHttpUrlConnection(Mockito.any());

        int responseCode = spyAggregateReportSender.sendReport(reportingOrigin,
                aggregateReportJson);

        assertEquals(outputStream.toString(), aggregateReportJson.toString());
        assertEquals(responseCode, 200);
    }

    @Test
    public void testCreateHttpUrlConnection() throws Exception {
        HttpsURLConnection mockConnection = Mockito.mock(HttpsURLConnection.class);
        URL spyUrl = Mockito.spy(new URL("https://foo"));
        Mockito.doReturn(mockConnection).when(spyUrl).openConnection();

        AggregateReportSender aggregateReportSender = new AggregateReportSender(false, sContext);
        HttpsURLConnection connection =
                (HttpsURLConnection) aggregateReportSender.createHttpUrlConnection(spyUrl);
        assertEquals(mockConnection, connection);
    }

    @Test
    public void testDebugReportUriPath() {
        Truth.assertThat(new AggregateReportSender(false, sContext).getReportUriPath())
                .isEqualTo(AggregateReportSender.AGGREGATE_ATTRIBUTION_REPORT_URI_PATH);
        Truth.assertThat(new AggregateReportSender(true, sContext).getReportUriPath())
                .isEqualTo(AggregateReportSender.DEBUG_AGGREGATE_ATTRIBUTION_REPORT_URI_PATH);
    }
}
