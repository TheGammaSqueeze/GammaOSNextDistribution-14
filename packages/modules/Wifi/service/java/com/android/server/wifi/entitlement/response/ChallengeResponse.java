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

import android.text.TextUtils;
import android.util.Log;

import com.android.server.wifi.entitlement.RequestFactory;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** The response of authentication challenge request. */
public class ChallengeResponse extends Response {
    private static final String TAG = "WifiEntitlement(ChallengeResponse)";

    public ChallengeResponse(String responseBody) {
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
                if (id == RequestFactory.MESSAGE_ID_3GPP_AUTHENTICATION) {
                    parse3gppAuthentication(object);
                } else {
                    Log.e(TAG, "Unexpected Message ID >> " + id);
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "ERROR! not a valid JSONArray String![" + responseBody + "]", e);
        }
    }
    /** Returns {@code mEapAkaChallenge}. */
    public String getEapAkaChallenge() {
        return mEapAkaChallenge;
    }
}

