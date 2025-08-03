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

package com.android.ondevicepersonalization.services.data.events;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.net.Uri;
import android.os.PersistableBundle;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class EventUrlHelperTest {
    private static final EventUrlPayload TEST_EVENT_URL_PAYLOAD = createTestEventUrlPayload();

    @Test
    public void testEncryptDecryptEvent() throws Exception {
        Uri uri = EventUrlHelper.getEncryptedOdpEventUrl(TEST_EVENT_URL_PAYLOAD);
        assertEquals(uri.getScheme(), EventUrlHelper.URI_SCHEME);
        assertEquals(uri.getAuthority(), EventUrlHelper.URI_AUTHORITY);
        assertEquals(uri.getQueryParameterNames().size(), 1);

        EventUrlPayload decryptedEventUrlPayload =
                EventUrlHelper.getEventFromOdpEventUrl(uri.toString());
        PersistableBundle eventParams = decryptedEventUrlPayload.getEventParams();
        assertEquals(1, eventParams.getInt("x"));
        assertEquals("abc", eventParams.getString("y"));
    }

    @Test
    public void testEncryptDecryptClickTrackingUrlEvent() throws Exception {
        String landingPage = "https://google.com/";
        Uri uri = EventUrlHelper.getEncryptedClickTrackingUrl(TEST_EVENT_URL_PAYLOAD,
                landingPage);
        assertEquals(uri.getScheme(), EventUrlHelper.URI_SCHEME);
        assertEquals(uri.getAuthority(), EventUrlHelper.URI_AUTHORITY);
        assertEquals(uri.getQueryParameterNames().size(), 2);

        assertEquals(uri.getQueryParameter(EventUrlHelper.URL_LANDING_PAGE_EVENT_KEY), landingPage);

        EventUrlPayload decryptedEventUrlPayload =
                EventUrlHelper.getEventFromOdpEventUrl(uri.toString());
        PersistableBundle eventParams = decryptedEventUrlPayload.getEventParams();
        assertEquals(1, eventParams.getInt("x"));
        assertEquals("abc", eventParams.getString("y"));
    }

    @Test
    public void testInvalidUrl() {
        assertThrows(IllegalArgumentException.class,
                () -> EventUrlHelper.getEventFromOdpEventUrl("https://google.com/"));
    }

    private static EventUrlPayload createTestEventUrlPayload() {
        PersistableBundle params = new PersistableBundle();
        params.putInt("x", 1);
        params.putString("y", "abc");
        return new EventUrlPayload(params, null, null);
    }
}
