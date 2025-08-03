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

import org.junit.Rule;
import org.junit.Test;

public class UpdateEncoderEventTest {

    private final Uri mUri = CommonFixture.getUri(CommonFixture.VALID_BUYER_1, "/encoder");

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastT();

    @Test
    public void testUpdateEncoderEventBuild() {
        UpdateEncoderEvent event =
                UpdateEncoderEvent.builder()
                        .setEncoderEndpointUri(mUri)
                        .setUpdateType(UpdateEncoderEvent.UpdateType.REGISTER)
                        .build();

        assertEquals(UpdateEncoderEvent.UpdateType.REGISTER, event.getUpdateType());
        assertEquals(mUri, event.getEncoderEndpointUri());
    }

    @Test
    public void testUpdateEncoderEventNullUriAllowed() {
        UpdateEncoderEvent event =
                UpdateEncoderEvent.builder()
                        .setUpdateType(UpdateEncoderEvent.UpdateType.REGISTER)
                        .build();
        assertEquals(UpdateEncoderEvent.UpdateType.REGISTER, event.getUpdateType());
        assertNull(event.getEncoderEndpointUri());
    }

    @Test
    public void testUpdateEncoderEventNullEventTypeNotAllowed() {
        assertThrows(
                IllegalStateException.class,
                () -> {
                    UpdateEncoderEvent.builder().setEncoderEndpointUri(mUri).build();
                });
    }
}
