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

package com.android.server.wifi;

import android.annotation.Nullable;
import android.util.Log;

import com.android.server.wifi.hal.WifiChip;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * This class stores information about the response of the AFC Server to a query
 */
public class AfcServerResponse {
    private static final String TAG = "WifiAfcServerResponse";
    static final int SUCCESS_AFC_RESPONSE_CODE = 0;
    static final int SUCCESS_HTTP_RESPONSE_CODE = 200;

    /*
     * The value represented by the AFC response code is mapped out as follows, taken from the AFC
     * server specs:
     * -1: General Failure
     * 0: SUCCESS
     * 100 – 199: General errors related to the protocol
     * 300 – 399: Error events specific to message exchanges for the Available Spectrum Inquiry
     */
    private int mAfcResponseCode;
    // HTTP response code is 200 if the request succeeded, otherwise the request failed
    private int mHttpResponseCode;
    private String mAfcResponseDescription;
    private WifiChip.AfcChannelAllowance mAfcChannelAllowance;

    /**
     * Parses the spectrum inquiry response object received from the AFC server into an
     * AfcServerResponse object. Returns null if the available spectrum inquiry response is
     * incorrectly formatted.
     *
     * @param httpResponseCode the HTTP response code of the AFC server.
     * @param spectrumInquiryResponse the available spectrum inquiry response to parse.
     * @return the parsed response object as an AfcServerResponse, or null if the input JSON is
     * incorrectly formatted.
     */
    public static AfcServerResponse fromSpectrumInquiryResponse(int httpResponseCode,
            JSONObject spectrumInquiryResponse) {
        AfcServerResponse afcServerResponse = new AfcServerResponse();

        if (spectrumInquiryResponse == null) {
            return null;
        }

        afcServerResponse.setHttpResponseCode(httpResponseCode);
        afcServerResponse.setAfcResponseCode(parseAfcResponseCode(spectrumInquiryResponse));
        afcServerResponse.setAfcResponseDescription(getResponseShortDescriptionFromJSON(
                spectrumInquiryResponse));

        // no need to keep parsing if either the AFC or HTTP codes indicate a failed request
        if (afcServerResponse.mAfcResponseCode != SUCCESS_AFC_RESPONSE_CODE
                || afcServerResponse.mHttpResponseCode != SUCCESS_HTTP_RESPONSE_CODE) {
            return afcServerResponse;
        }

        // parse the available frequencies and channels, and their expiration time
        try {
            WifiChip.AfcChannelAllowance afcChannelAllowance = new WifiChip.AfcChannelAllowance();
            afcChannelAllowance.availableAfcFrequencyInfos = parseAvailableFrequencyInfo(
                    spectrumInquiryResponse.optJSONArray("availableFrequencyInfo"));
            afcChannelAllowance.availableAfcChannelInfos = parseAvailableChannelInfo(
                    spectrumInquiryResponse.optJSONArray("availableChannelInfo"));

            // expiry time is required as we know the response was successful
            String availabilityExpireTimeString = spectrumInquiryResponse.getString(
                    "availabilityExpireTime");
            afcChannelAllowance.availabilityExpireTimeMs =
                    convertExpireTimeStringToTimestamp(availabilityExpireTimeString);

            afcServerResponse.setAfcChannelAllowance(afcChannelAllowance);
        } catch (JSONException | NullPointerException e) {
            Log.e(TAG, e.toString());
            return null;
        }
        return afcServerResponse;
    }

    /**
     * Parses the available frequencies of an AfcServerResponse.
     */
    private static List<WifiChip.AvailableAfcFrequencyInfo> parseAvailableFrequencyInfo(
            JSONArray availableFrequencyInfoJSON) {
        if (availableFrequencyInfoJSON != null) {
            List<WifiChip.AvailableAfcFrequencyInfo> availableFrequencyInfo = new ArrayList<>();

            try {
                for (int i = 0; i < availableFrequencyInfoJSON.length(); ++i) {
                    JSONObject frequencyRange = availableFrequencyInfoJSON.getJSONObject(i)
                            .getJSONObject("frequencyRange");

                    WifiChip.AvailableAfcFrequencyInfo availableFrequency = new WifiChip
                            .AvailableAfcFrequencyInfo();
                    availableFrequency.startFrequencyMhz =
                            frequencyRange.getInt("lowFrequency");
                    availableFrequency.endFrequencyMhz =
                            frequencyRange.getInt("highFrequency");
                    availableFrequency.maxPsdDbmPerMhz =
                            availableFrequencyInfoJSON.getJSONObject(i).getInt("maxPsd");

                    availableFrequencyInfo.add(availableFrequency);
                }
                return availableFrequencyInfo;
            } catch (JSONException | NullPointerException e) {
                Log.e(TAG, "Error occurred when parsing available AFC frequency info.");
            }
        }
        return null;
    }

    /**
     * Parses the available channels of an AfcServerResponse.
     */
    private static List<WifiChip.AvailableAfcChannelInfo> parseAvailableChannelInfo(
            JSONArray availableChannelInfoJSON) {
        if (availableChannelInfoJSON != null) {
            List<WifiChip.AvailableAfcChannelInfo> availableChannelInfo = new ArrayList<>();

            try {
                for (int i = 0; i < availableChannelInfoJSON.length(); ++i) {
                    int globalOperatingClass = availableChannelInfoJSON
                            .getJSONObject(i).getInt("globalOperatingClass");

                    for (int j = 0; j < availableChannelInfoJSON.getJSONObject(i).getJSONArray(
                            "channelCfi").length(); ++j) {
                        WifiChip.AvailableAfcChannelInfo availableChannel = new WifiChip
                                .AvailableAfcChannelInfo();

                        availableChannel.globalOperatingClass = globalOperatingClass;
                        availableChannel.channelCfi = availableChannelInfoJSON.getJSONObject(i)
                                        .getJSONArray("channelCfi").getInt(j);
                        availableChannel.maxEirpDbm = availableChannelInfoJSON.getJSONObject(i)
                                        .getJSONArray("maxEirp").getInt(j);
                        availableChannelInfo.add(availableChannel);
                    }
                }
                return availableChannelInfo;
            } catch (JSONException | NullPointerException e) {
                Log.e(TAG, "Error occurred when parsing available AFC channel info.");
            }
        }
        return null;
    }

    /**
     * Converts a date string in the format: YYYY-MM-DDThh:mm:ssZ to a Unix timestamp.
     *
     * @param availabilityExpireTime the expiration time of the AFC frequency and channel
     * availability.
     * @return the expiration time as a Unix timestamp in milliseconds.
     */
    static long convertExpireTimeStringToTimestamp(String availabilityExpireTime) {
        // Sometimes the expiration time is incorrectly formatted and includes appended
        // milliseconds after the seconds section, so we want to remove these before converting to
        // a timestamp
        if (availabilityExpireTime.length() > 20) {
            availabilityExpireTime = availabilityExpireTime.substring(0, 19) + "Z";
        }

        return Instant.parse(availabilityExpireTime).toEpochMilli();
    }

    /**
     * Returns the AFC response code for an available spectrum inquiry response or Integer.MIN_VALUE
     * if it does not exist.
     */
    static int parseAfcResponseCode(JSONObject spectrumInquiryResponse) {
        try {
            // parse the response code and return it. Throws an error if a field does not exist.
            int responseCode = spectrumInquiryResponse.getJSONObject("response")
                    .getInt("responseCode");

            return responseCode;
        } catch (JSONException | NullPointerException e) {
            Log.e(TAG, "The available spectrum inquiry response does not contain a response "
                    + "code field.");

            // Currently the internal Google AFC server doesn't provide an AFC response code of 0
            // for successful queries.
            // TODO (b/294594249): If the server is modified to correctly provide an AFC response
            //  code of 0 upon a successful query, handle this case accordingly.
            return 0;
        }
    }

    /**
     * Returns the short description of an available spectrum inquiry response, or an empty string
     * if it does not exist.
     */
    static String getResponseShortDescriptionFromJSON(JSONObject spectrumInquiryResponse) {
        try {
            // parse and return the response description. Throws an error if a field does not exist.
            return spectrumInquiryResponse.getJSONObject("response").getString("shortDescription");
        } catch (JSONException | NullPointerException e) {
            return "";
        }
    }

    /**
     * Sets the AFC response code associated with an available spectrum inquiry response.
     */
    public void setAfcResponseCode(int afcResponseCode) {
        mAfcResponseCode = afcResponseCode;
    }

    /**
     * Sets the description associated with an available spectrum inquiry response.
     */
    public void setAfcResponseDescription(String afcResponseDescription) {
        mAfcResponseDescription = afcResponseDescription;
    }

    /**
     * Sets the AFC channel and frequency allowance.
     */
    public void setAfcChannelAllowance(WifiChip.AfcChannelAllowance afcChannelAllowance) {
        mAfcChannelAllowance = afcChannelAllowance;
    }

    /**
     * Sets the AFC server HTTP response code.
     */
    public void setHttpResponseCode(int httpResponseCode) {
        mHttpResponseCode = httpResponseCode;
    }

    /**
     * Returns the AFC response code of the available spectrum inquiry response.
     */
    public int getAfcResponseCode() {
        return mAfcResponseCode;
    }

    /**
     * Returns the HTTP response code of the AFC server's response.
     */
    public int getHttpResponseCode() {
        return mHttpResponseCode;
    }

    /**
     * Returns the AFC response description.
     */
    public String getAfcResponseDescription() {
        return mAfcResponseDescription;
    }

    /**
     * Returns the available channels and frequencies received from the AFC query.
     */
    @Nullable
    public WifiChip.AfcChannelAllowance getAfcChannelAllowance() {
        return mAfcChannelAllowance;
    }

    @Override
    public String toString() {
        StringBuilder sbuf = new StringBuilder();

        if (mAfcChannelAllowance != null) {
            // print frequency info
            if (mAfcChannelAllowance.availableAfcFrequencyInfos != null) {
                sbuf.append("Available frequencies:\n");

                for (WifiChip.AvailableAfcFrequencyInfo frequency :
                        mAfcChannelAllowance.availableAfcFrequencyInfos) {
                    sbuf.append("   [" + frequency.startFrequencyMhz + ", "
                            + frequency.endFrequencyMhz + "], maxPsd: " + frequency.maxPsdDbmPerMhz
                            + "dBm per MHZ\n");
                }
            }

            // print channel info
            if (mAfcChannelAllowance.availableAfcChannelInfos != null) {
                sbuf.append("Available channels:\n");

                for (WifiChip.AvailableAfcChannelInfo channel :
                        mAfcChannelAllowance.availableAfcChannelInfos) {
                    sbuf.append("   Global operating class: " + channel.globalOperatingClass
                            + ", Cfi: " + channel.channelCfi + " maxEirp: " + channel.maxEirpDbm
                            + "dBm\n");
                }
            }

            sbuf.append("Availability expiration time (ms): "
                    + mAfcChannelAllowance.availabilityExpireTimeMs + "\n");
        }

        sbuf.append("HTTP response code: " + mHttpResponseCode + "\n");
        sbuf.append("AFC response code: " + mAfcResponseCode + "\n");
        sbuf.append("AFC response short description: " + mAfcResponseDescription + "\n");
        return sbuf.toString();
    }
}
