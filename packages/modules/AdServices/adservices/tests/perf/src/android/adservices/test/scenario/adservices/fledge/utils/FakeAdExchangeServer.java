/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.adservices.test.scenario.adservices.fledge.utils;

import android.util.Log;

import androidx.test.core.app.ApplicationProvider;

import com.google.common.io.BaseEncoding;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Utility class holding methods that enable Unified Flow auction */
public class FakeAdExchangeServer {

    private static final long NANO_TO_MILLISECONDS = 1000000;
    private static final String TAG = "AdSelectionDataE2ETest";

    private static Gson sGson =
            new GsonBuilder()
                    .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                    .create();

    public static SelectAdResponse runServerAuction(
            String contextualSignalFileName,
            byte[] adSelectionData,
            String sfeAddress,
            boolean loggingEnabled)
            throws IOException {
        // Add contextual data
        SelectAdRequest selectAdRequest =
                getSelectAdRequestWithContextualSignals(contextualSignalFileName);

        if (loggingEnabled) {
            Log.d(TAG, "get ad selection data : " + BaseEncoding.base64().encode(adSelectionData));
        }

        // Because we are making a HTTPS call, we need to encode the ciphertext byte array
        selectAdRequest.setProtectedAudienceCiphertext(
                BaseEncoding.base64().encode(adSelectionData));

        return makeSelectAdsCall(selectAdRequest, sfeAddress, loggingEnabled);
    }

    private static SelectAdResponse makeSelectAdsCall(
            SelectAdRequest request, String sfeAddress, boolean loggingEnabled) throws IOException {
        String requestPayload = getSelectAdPayload(request);
        String response = makeHttpPostCall(sfeAddress, requestPayload, loggingEnabled);
        if (loggingEnabled) {
            Log.d(TAG, "Response from b&a : " + response);
        }
        return parseSelectAdResponse(response);
    }

    private static SelectAdRequest getSelectAdRequestWithContextualSignals(String fileName)
            throws IOException {
        String jsonString = getJsonFromAssets(fileName);

        return sGson.fromJson(jsonString, SelectAdRequest.class);
    }

    private static SelectAdResponse parseSelectAdResponse(String jsonString) {
        return new GsonBuilder().create().fromJson(jsonString, SelectAdResponse.class);
    }

    private static String getSelectAdPayload(SelectAdRequest selectAdRequest) {
        return sGson.toJson(selectAdRequest);
    }

    private static String makeHttpPostCall(
            String address, String jsonInputString, boolean loggingEnabled) throws IOException {
        URL url = new URL(address);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/json");
        con.setRequestProperty("Accept", "application/json");
        con.setDoOutput(true);
        try (OutputStream os = con.getOutputStream()) {
            byte[] input = jsonInputString.getBytes("utf-8");
            os.write(input, 0, input.length);
            if (loggingEnabled) {
                Log.d(TAG, "HTTP Post call made with payload : ");
                largeLog(TAG, jsonInputString);
            }
        }

        try (BufferedReader br =
                new BufferedReader(new InputStreamReader(con.getInputStream(), "utf-8"))) {
            StringBuilder response = new StringBuilder();
            String responseLine = null;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
            if (loggingEnabled) {
                Log.d(TAG, "Response from B&A : " + response.toString());
            }

            return response.toString();
        }
    }

    private static String generateLogLabel(
            String classSimpleName, String testName, long elapsedMs) {
        return "("
                + "SELECT_ADS_LATENCY_"
                + classSimpleName
                + "#"
                + testName
                + ": "
                + elapsedMs
                + " ms)";
    }

    private static void largeLog(String tag, String content) {
        if (content.length() > 4000) {
            Log.d(tag, content.substring(0, 4000));
            largeLog(tag, content.substring(4000));
        } else {
            Log.d(tag, content);
        }
    }

    private static String getJsonFromAssets(String fileName) {
        String jsonString;
        try {
            InputStream is = ApplicationProvider.getApplicationContext().getAssets().open(fileName);

            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();

            jsonString = new String(buffer, "UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        return jsonString;
    }
}
