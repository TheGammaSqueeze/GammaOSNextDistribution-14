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

package com.android.server.wifi.entitlement.response;

import static android.text.format.DateUtils.HOUR_IN_MILLIS;

import android.text.TextUtils;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.wifi.entitlement.PseudonymInfo;
import com.android.server.wifi.entitlement.RequestFactory;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Optional;


/** The response of get IMSI pseudonym request */
public class GetImsiPseudonymResponse extends Response {
    private static final String TAG = "WifiEntitlement(GetImsiPseudonymResponse)";
    @VisibleForTesting
    static final String JSON_KEY_IMSI_PSEUDONYM = "imsi-pseudonym";
    @VisibleForTesting
    public static final String JSON_KEY_REFRESH_INTERVAL = "refresh-interval";

    // Refer to https://www.rfc-editor.org/rfc/rfc3748#section-3.1
    private static final int EAP_MTU = 1020;
    private int mGetImsiPseudonymResponseCode;
    private int mRefreshInterval;
    private String mImsiPseudonym;

    public GetImsiPseudonymResponse(String responseBody) {
        if (TextUtils.isEmpty(responseBody)) {
            Log.e(TAG, "Error! Empty responseBody!");
            return;
        }
        try {
            JSONArray array = new JSONArray(responseBody);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) {
                    continue;
                }
                int id = object.optInt(JSON_KEY_MESSAGE_ID, -1);
                switch (id) {
                    case RequestFactory.MESSAGE_ID_3GPP_AUTHENTICATION:
                        parse3gppAuthentication(object);
                        break;
                    case RequestFactory.MESSAGE_ID_GET_IMSI_PSEUDONYM:
                        parseGetImsiPseudonym(object);
                        break;
                    default:
                        Log.e(TAG, "Unexpected Message ID >> " + id);
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "ERROR! not a valid JSONArray String![" + responseBody + "]", e);
        }
    }

    private void parseGetImsiPseudonym(JSONObject object) {
        mGetImsiPseudonymResponseCode = object.optInt(JSON_KEY_RESPONSE_CODE, -1);
        mImsiPseudonym = object.optString(JSON_KEY_IMSI_PSEUDONYM, null);
        mRefreshInterval = object.optInt(JSON_KEY_REFRESH_INTERVAL, -1);
    }

    /** Returns {@code PseudonymInfo} with server returned information. */
    public Optional<PseudonymInfo> toPseudonymInfo(String imsi) {
        PseudonymInfo pseudonymInfo = null;
        boolean success =
                (mAuthResponseCode == RESPONSE_CODE_REQUEST_SUCCESSFUL)
                        && (mGetImsiPseudonymResponseCode == RESPONSE_CODE_REQUEST_SUCCESSFUL);
        if (success && (mImsiPseudonym != null) && (mImsiPseudonym.length() <= EAP_MTU)) {
            if (mRefreshInterval <= 0) {
                pseudonymInfo = new PseudonymInfo(mImsiPseudonym, imsi);
            } else {
                pseudonymInfo = new PseudonymInfo(mImsiPseudonym, imsi,
                        mRefreshInterval * HOUR_IN_MILLIS);
            }
        }
        return Optional.ofNullable(pseudonymInfo);
    }

    /** Returns AKA token. */
    public String getAkaToken() {
        return mAkaToken;
    }

    public int getGetImsiPseudonymResponseCode() {
        return mGetImsiPseudonymResponseCode;
    }
}

