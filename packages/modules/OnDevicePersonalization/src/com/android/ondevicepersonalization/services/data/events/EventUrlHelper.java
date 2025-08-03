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

import android.annotation.NonNull;
import android.net.Uri;

import com.android.ondevicepersonalization.services.util.CryptUtils;

/**
 * Helper class to manage creation of ODP event URLs.
 */
public class EventUrlHelper {
    public static final String URI_AUTHORITY = "localhost";
    public static final String URI_SCHEME = "odp";
    public static final String URL_LANDING_PAGE_EVENT_KEY = "r";

    private static final String BASE_URL = URI_SCHEME + "://" + URI_AUTHORITY;
    private static final String URL_EVENT_KEY = "e";

    private EventUrlHelper() {
    }

    private static String encryptEvent(EventUrlPayload event) throws Exception {
        return CryptUtils.encrypt(event);
    }

    private static EventUrlPayload decryptEvent(String base64Event) throws Exception {
        return (EventUrlPayload) CryptUtils.decrypt(base64Event);
    }

    /**
     * Creates an encrypted ODP event URL for the given event
     *
     * @param event The event to create the URL for.
     * @return Encrypted ODP event URL
     */
    public static Uri getEncryptedOdpEventUrl(@NonNull EventUrlPayload event) throws Exception {
        String encryptedEvent = encryptEvent(event);
        return Uri.parse(BASE_URL).buildUpon().appendQueryParameter(URL_EVENT_KEY,
                encryptedEvent).build();
    }

    /**
     * Creates an encrypted ODP event URL for the given event and landing page
     *
     * @param event The event to create the URL for.
     * @return Encrypted ODP event URL with a landingPage parameter
     */
    public static Uri getEncryptedClickTrackingUrl(@NonNull EventUrlPayload event,
            @NonNull String landingPage)
            throws Exception {
        return getEncryptedOdpEventUrl(event).buildUpon().appendQueryParameter(
                URL_LANDING_PAGE_EVENT_KEY, landingPage).build();
    }

    /**
     * Retrieved the event from the encrypted ODP event URL
     *
     * @param url The encrypted ODP event URL
     * @return Event object retrieved from the URL
     */
    public static EventUrlPayload getEventFromOdpEventUrl(@NonNull String url) throws Exception {
        Uri uri = Uri.parse(url);
        String encryptedEvent = uri.getQueryParameter(URL_EVENT_KEY);
        if (encryptedEvent == null || !isOdpUrl(url)) {
            throw new IllegalArgumentException("Invalid url: " + url);
        }
        return decryptEvent(encryptedEvent);
    }

    /**
     * Returns whether a given URL is an ODP url
     *
     * @return true if URL is an ODP url, false otherwise
     */
    public static boolean isOdpUrl(@NonNull String url) {
        Uri uri = Uri.parse(url);
        return URI_SCHEME.equals(uri.getScheme())
                && URI_AUTHORITY.equals(uri.getAuthority());
    }
}
