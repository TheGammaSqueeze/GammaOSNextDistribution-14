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

package com.android.adservices.service.measurement.actions;

import static com.android.adservices.service.measurement.E2ETest.getInputEvent;
import static com.android.adservices.service.measurement.E2ETest.getUriConfigMap;
import static com.android.adservices.service.measurement.E2ETest.getUriToResponseHeadersMap;
import static com.android.adservices.service.measurement.E2ETest.hasAdIdPermission;
import static com.android.adservices.service.measurement.E2ETest.hasArDebugPermission;
import static com.android.adservices.service.measurement.E2ETest.hasSourceDebugReportingPermission;

import android.adservices.measurement.WebSourceParams;
import android.adservices.measurement.WebSourceRegistrationRequest;
import android.adservices.measurement.WebSourceRegistrationRequestInternal;
import android.net.Uri;

import com.android.adservices.service.measurement.E2ETest.TestFormatJsonMapping;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class RegisterWebSource implements Action {
    public final WebSourceRegistrationRequestInternal mRegistrationRequest;
    public final Map<String, List<Map<String, List<String>>>> mUriToResponseHeadersMap;
    public final Map<String, UriConfig> mUriConfigMap;
    public final long mTimestamp;
    public final boolean mDebugReporting;
    public final boolean mAdIdPermission;

    public RegisterWebSource(JSONObject obj) throws JSONException {
        JSONObject regParamsJson =
                obj.getJSONObject(TestFormatJsonMapping.REGISTRATION_REQUEST_KEY);
        JSONArray sourceParamsArray =
                regParamsJson.getJSONArray(TestFormatJsonMapping.SOURCE_PARAMS_REGISTRATIONS_KEY);
        Uri appDestination =
                getNullableStringUri(
                        regParamsJson, TestFormatJsonMapping.SOURCE_APP_DESTINATION_URI_KEY);
        Uri webDestination =
                getNullableStringUri(
                        regParamsJson, TestFormatJsonMapping.SOURCE_WEB_DESTINATION_URI_KEY);
        Uri verifiedDestination =
                getNullableStringUri(
                        regParamsJson, TestFormatJsonMapping.SOURCE_VERIFIED_DESTINATION_URI_KEY);

        String packageName = regParamsJson.optString(TestFormatJsonMapping.ATTRIBUTION_SOURCE_KEY);

        WebSourceRegistrationRequest registrationRequest =
                new WebSourceRegistrationRequest.Builder(
                                createSourceParams(sourceParamsArray, obj),
                                Uri.parse(
                                        regParamsJson.getString(
                                                TestFormatJsonMapping.SOURCE_TOP_ORIGIN_URI_KEY)))
                        .setInputEvent(
                                regParamsJson
                                                .getString(TestFormatJsonMapping.INPUT_EVENT_KEY)
                                                .equals(TestFormatJsonMapping.SOURCE_VIEW_TYPE)
                                        ? null
                                        : getInputEvent())
                        .setAppDestination(appDestination)
                        .setWebDestination(webDestination)
                        .setVerifiedDestination(verifiedDestination)
                        .build();

        mRegistrationRequest =
                new WebSourceRegistrationRequestInternal.Builder(
                                registrationRequest,
                                packageName,
                                /* sdkPackageName = */ "",
                                /* requestTime =*/ 2000L)
                        .build();

        mUriToResponseHeadersMap = getUriToResponseHeadersMap(obj);
        mTimestamp = obj.getLong(TestFormatJsonMapping.TIMESTAMP_KEY);
        mDebugReporting = hasSourceDebugReportingPermission(obj);
        mAdIdPermission = hasAdIdPermission(obj);
        mUriConfigMap = getUriConfigMap(obj);
    }

    @Override
    public long getComparable() {
        return mTimestamp;
    }

    private List<WebSourceParams> createSourceParams(JSONArray sourceParamsArray, JSONObject obj)
            throws JSONException {
        List<WebSourceParams> sourceParamsList = new ArrayList<>(sourceParamsArray.length());

        for (int i = 0; i < sourceParamsArray.length(); i++) {
            JSONObject sourceParams = sourceParamsArray.getJSONObject(i);
            sourceParamsList.add(
                    new WebSourceParams.Builder(
                                    Uri.parse(
                                            sourceParams.getString(
                                                    TestFormatJsonMapping.REGISTRATION_URI_KEY)))
                            .setDebugKeyAllowed(hasArDebugPermission(obj))
                            .build());
        }

        return sourceParamsList;
    }

    private Uri getNullableStringUri(JSONObject regParamsJson, String key) throws JSONException {
        return regParamsJson.isNull(key) ? null : Uri.parse(regParamsJson.getString(key));
    }
}
