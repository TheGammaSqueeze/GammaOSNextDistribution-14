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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import android.adservices.common.CommonFixture;
import android.net.Uri;

import com.android.adservices.common.SdkLevelSupportRule;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;

import java.util.Collections;

public class UpdateEncoderTest {

    private static final String UPDATE_ENCODER = "update_encoder";
    private static final String ACTION = "action";
    private static final String ENDPOINT = "endpoint";

    private final Uri mEndpointUri = CommonFixture.getUri(CommonFixture.VALID_BUYER_1, "/encoder");

    private UpdateEncoder mUpdateEncoder = new UpdateEncoder();

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastT();

    @Test
    public void testGetName() {
        assertEquals(UPDATE_ENCODER, mUpdateEncoder.getName());
    }

    @Test
    public void testUpdateEmptyEvent() throws JSONException {
        JSONObject updateJson = new JSONObject();

        UpdateOutput output = mUpdateEncoder.processUpdates(updateJson, Collections.emptyMap());
        assertNull(
                "Update event should have been skipped due to empty JSON",
                output.getUpdateEncoderEvent());
    }

    @Test
    public void testUpdateEventInvalidType() throws JSONException {
        JSONObject updateJson = new JSONObject();
        updateJson.put(ACTION, "NOT_RECOGNIZED");
        updateJson.put(ENDPOINT, mEndpointUri);

        assertThrows(
                "Non-recognized update event should've lead to this exception",
                IllegalArgumentException.class,
                () -> {
                    mUpdateEncoder.processUpdates(updateJson, Collections.emptyMap());
                });
    }

    @Test
    public void testUpdateRegisterEvent() throws JSONException {
        JSONObject updateJson = new JSONObject();
        updateJson.put(ACTION, "REGISTER");
        updateJson.put(ENDPOINT, mEndpointUri);

        UpdateOutput output = mUpdateEncoder.processUpdates(updateJson, Collections.emptyMap());
        UpdateEncoderEvent event =
                UpdateEncoderEvent.builder()
                        .setUpdateType(UpdateEncoderEvent.UpdateType.REGISTER)
                        .setEncoderEndpointUri(mEndpointUri)
                        .build();
        assertEquals(event, output.getUpdateEncoderEvent());
    }
}
