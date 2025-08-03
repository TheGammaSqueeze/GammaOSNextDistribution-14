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

package com.android.ondevicepersonalization.services.data.events;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import android.os.PersistableBundle;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class EventUrlPayloadTest {
    private static final byte[] RESPONSE_BYTES = {'A', 'B'};
    @Test
    public void testPayload() {
        PersistableBundle params = new PersistableBundle();
        params.putInt("x", 1);
        EventUrlPayload payload = new EventUrlPayload(
                params, RESPONSE_BYTES, "image/gif");
        assertEquals(1, payload.getEventParams().getInt("x"));
        assertEquals("image/gif", payload.getMimeType());
        assertArrayEquals(RESPONSE_BYTES, payload.getResponseData());
    }
}
