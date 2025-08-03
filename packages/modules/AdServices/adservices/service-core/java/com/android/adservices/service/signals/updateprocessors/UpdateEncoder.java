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

package com.android.adservices.service.signals.updateprocessors;

import android.net.Uri;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.signals.DBProtectedSignal;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Set;

/**
 * Updates the encoder for a buyer based on updateSignals call The value for this is a JSON object
 * with key "update_encoder"
 *
 * <p>Inside the JSON object the buyer need to provide a valid action from supported choices. The
 * action for update is provided with the key "action" and so far we support "DELETE" & "REGISTER"
 * actions. In case of "REGISTER" the Uri for update is provided in the key "endpoint"
 */
public class UpdateEncoder implements UpdateProcessor {

    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    private static final String UPDATE_ENCODER = "update_encoder";
    private static final String ACTION = "action";
    private static final String ENDPOINT = "endpoint";

    /**
     * @return name for this {@link UpdateProcessor}
     */
    @Override
    public String getName() {
        return UPDATE_ENCODER;
    }

    @Override
    public UpdateOutput processUpdates(
            Object updates, Map<ByteBuffer, Set<DBProtectedSignal>> current) throws JSONException {
        UpdateOutput toReturn = new UpdateOutput();
        JSONObject updatesObject = UpdateProcessorUtils.castToJSONObject(UPDATE_ENCODER, updates);

        try {
            if (!updatesObject.has(ACTION)) {
                sLogger.v("No update event type present, skipping updating encoder");
                return toReturn;
            }
            String action = updatesObject.getString(ACTION);

            UpdateEncoderEvent.Builder eventBuilder = UpdateEncoderEvent.builder();
            eventBuilder.setUpdateType(UpdateEncoderEvent.UpdateType.valueOf(action));

            if (updatesObject.has(ENDPOINT)) {
                String uriString = updatesObject.getString(ENDPOINT);
                eventBuilder.setEncoderEndpointUri(Uri.parse(uriString));
            }

            toReturn.setUpdateEncoderEvent(eventBuilder.build());

        } catch (JSONException e) {
            throw new JSONException("No valid update encoder event found");
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    String.format("Unrecognized update event type: %s", e));
        }

        return toReturn;
    }
}
