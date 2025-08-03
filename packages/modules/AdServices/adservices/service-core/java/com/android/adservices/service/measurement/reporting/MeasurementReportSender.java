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

import com.android.adservices.service.measurement.MeasurementHttpClient;
import com.android.internal.annotations.VisibleForTesting;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Class to send reports by making a non-credentialed secure HTTP POST request to the reporting
 * origin.
 */
public abstract class MeasurementReportSender {
    private final MeasurementHttpClient mNetworkConnection;

    public MeasurementReportSender(Context context) {
        mNetworkConnection = new MeasurementHttpClient(context);
    }

    /**
     * Sends an event report to the reporting origin.
     */
    public int sendReport(Uri adTechDomain, JSONObject reportJson)
            throws IOException {
        int returnCode;
        URL reportingFullUrl = createReportingFullUrl(adTechDomain);

        HttpURLConnection urlConnection = createHttpUrlConnection(reportingFullUrl);
        returnCode = sendReportPostRequest(urlConnection, reportJson);
        return returnCode;
    }

    /** Sends an event report to the reporting origin. */
    public int sendReport(Uri adTechDomain, JSONArray reportJsonArray) throws IOException {
        URL reportingFullUrl = createReportingFullUrl(adTechDomain);

        HttpURLConnection urlConnection = createHttpUrlConnection(reportingFullUrl);
        return sendReportPostRequest(urlConnection, reportJsonArray);
    }

    /**
     * Given a Uri adTechDomain, returns the URL Object
     * of the URL to send the POST request to.
     */
    abstract URL createReportingFullUrl(Uri adTechDomain) throws MalformedURLException;

    /**
     * Opens the HTTPUrlConnection from the URL object.
     */
    @VisibleForTesting
    public HttpURLConnection createHttpUrlConnection(URL reportingOriginURL) throws IOException {
        return (HttpURLConnection) mNetworkConnection.setup(reportingOriginURL);
    }

    /** Posts the reportJsonObject to the HttpUrlConnection. */
    private int sendReportPostRequest(HttpURLConnection urlConnection, JSONObject reportJson)
            throws IOException {
        return sendReportPostRequest(urlConnection, reportJson.toString().getBytes());
    }

    /** Posts the reportJsonArray to the HttpUrlConnection. */
    private int sendReportPostRequest(HttpURLConnection urlConnection, JSONArray reportJsonArray)
            throws IOException {
        return sendReportPostRequest(urlConnection, reportJsonArray.toString().getBytes());
    }

    /** Posts bytes to the HttpUrlConnection. */
    private int sendReportPostRequest(HttpURLConnection urlConnection, byte[] bytes)
            throws IOException {
        int code;
        try {
            urlConnection.setRequestMethod("POST");
            urlConnection.setDoOutput(true);
            urlConnection.setRequestProperty("Content-Type", "application/json");
            urlConnection.setRequestProperty("Origin", "null");

            OutputStream out = new BufferedOutputStream(urlConnection.getOutputStream());
            out.write(bytes);
            out.flush();
            out.close();

            code = urlConnection.getResponseCode();
        } finally {
            urlConnection.disconnect();
        }
        return code;
    }
}
