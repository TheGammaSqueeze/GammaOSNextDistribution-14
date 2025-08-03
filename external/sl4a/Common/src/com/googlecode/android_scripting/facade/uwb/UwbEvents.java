/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.googlecode.android_scripting.facade.uwb;

import com.googlecode.android_scripting.jsonrpc.JsonSerializable;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Utility class for Uwb.
 */
public class UwbEvents {

    /**
     * Translates a UWB adapter state event to JSON.
     */
    public static class UwbAdapterStateEvent implements JsonSerializable {
        private String mId;
        private String mUwbAdapterStateEvent;

        public UwbAdapterStateEvent(String id, String event) {
            mId = id;
            mUwbAdapterStateEvent = event;
        }

        /**
         * Create a JSON data-structure.
         */
        public JSONObject toJSON() throws JSONException {
            JSONObject uwbAdapterState = new JSONObject();

            uwbAdapterState.put(UwbConstants.UwbAdapterStateContainer.ID, mId);
            uwbAdapterState.put(
                    UwbConstants.UwbAdapterStateContainer.UWB_ADAPTER_STATE_EVENT,
                    mUwbAdapterStateEvent);

            return uwbAdapterState;
        }
    }


    /**
     * Translates a UWB ranging session event to JSON.
     */
    public static class RangingSessionEvent implements JsonSerializable {
        private String mId;
        private String mRangingSessionEvent;

        public RangingSessionEvent(String id, String event) {
            mId = id;
            mRangingSessionEvent = event;
        }

        /**
         * Create a JSON data-structure.
         */
        public JSONObject toJSON() throws JSONException {
            JSONObject rangingSession = new JSONObject();

            rangingSession.put(UwbConstants.RangingSessionContainer.ID, mId);
            rangingSession.put(
                    UwbConstants.RangingSessionContainer.RANGING_SESSION_EVENT,
                    mRangingSessionEvent);

            return rangingSession;
        }
    }
}
