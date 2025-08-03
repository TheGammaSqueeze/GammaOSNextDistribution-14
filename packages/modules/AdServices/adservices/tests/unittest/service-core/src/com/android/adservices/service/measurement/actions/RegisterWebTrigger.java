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

import static com.android.adservices.service.measurement.E2ETest.getUriConfigMap;
import static com.android.adservices.service.measurement.E2ETest.getUriToResponseHeadersMap;
import static com.android.adservices.service.measurement.E2ETest.hasAdIdPermission;
import static com.android.adservices.service.measurement.E2ETest.hasArDebugPermission;
import static com.android.adservices.service.measurement.E2ETest.hasTriggerDebugReportingPermission;

import android.adservices.measurement.WebTriggerParams;
import android.adservices.measurement.WebTriggerRegistrationRequest;
import android.adservices.measurement.WebTriggerRegistrationRequestInternal;
import android.net.Uri;

import com.android.adservices.service.measurement.E2ETest.TestFormatJsonMapping;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class RegisterWebTrigger implements Action {
    public final WebTriggerRegistrationRequestInternal mRegistrationRequest;
    public final Map<String, List<Map<String, List<String>>>> mUriToResponseHeadersMap;
    public final Map<String, UriConfig> mUriConfigMap;
    public final long mTimestamp;
    public final boolean mDebugReporting;
    public final boolean mAdIdPermission;

    public RegisterWebTrigger(JSONObject obj) throws JSONException {
        JSONObject regParamsJson =
                obj.getJSONObject(TestFormatJsonMapping.REGISTRATION_REQUEST_KEY);
        JSONArray triggerParamsArray =
                regParamsJson.getJSONArray(TestFormatJsonMapping.TRIGGER_PARAMS_REGISTRATIONS_KEY);

        String packageName = regParamsJson.optString(TestFormatJsonMapping.ATTRIBUTION_SOURCE_KEY);

        WebTriggerRegistrationRequest registrationRequest =
                new WebTriggerRegistrationRequest.Builder(
                                createTriggerParams(triggerParamsArray, obj),
                                Uri.parse(
                                        regParamsJson.getString(
                                                TestFormatJsonMapping.TRIGGER_TOP_ORIGIN_URI_KEY)))
                        .build();

        mRegistrationRequest =
                new WebTriggerRegistrationRequestInternal.Builder(
                                registrationRequest, packageName, /* sdkPackageName = */ "")
                        .build();

        mUriToResponseHeadersMap = getUriToResponseHeadersMap(obj);
        mTimestamp = obj.getLong(TestFormatJsonMapping.TIMESTAMP_KEY);
        mDebugReporting = hasTriggerDebugReportingPermission(obj);
        mAdIdPermission = hasAdIdPermission(obj);
        mUriConfigMap = getUriConfigMap(obj);
    }

    @Override
    public long getComparable() {
        return mTimestamp;
    }

    private List<WebTriggerParams> createTriggerParams(JSONArray triggerParamsArray, JSONObject obj)
            throws JSONException {
        List<WebTriggerParams> triggerParamsList = new ArrayList<>(triggerParamsArray.length());

        for (int i = 0; i < triggerParamsArray.length(); i++) {
            JSONObject triggerParams = triggerParamsArray.getJSONObject(i);
            triggerParamsList.add(
                    new WebTriggerParams.Builder(
                                    Uri.parse(
                                            triggerParams.getString(
                                                    TestFormatJsonMapping.REGISTRATION_URI_KEY)))
                            .setDebugKeyAllowed(hasArDebugPermission(obj))
                            .build());
        }

        return triggerParamsList;
    }
}
