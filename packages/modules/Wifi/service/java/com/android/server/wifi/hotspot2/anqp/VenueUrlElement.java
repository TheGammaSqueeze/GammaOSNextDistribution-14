/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.server.wifi.hotspot2.anqp;

import android.text.TextUtils;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.wifi.ByteBufferReader;

import java.net.ProtocolException;
import java.net.URL;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * The Venue URL ANQP Element, IEEE802.11-2016 section 9.4.5.20.
 *
 * Format:
 *
 * | Venue URL Duples |
 *      variable
 *
 * Venue URL Duple:
 * | Length | Venue Number | Venue URL Duple #1 (optional) | ...
 *     1          1                 variable
 */
public class VenueUrlElement extends ANQPElement {
    private final Map<Integer, URL> mVenueUrls;
    private static final String TAG = "VenueUrlElement";

    @VisibleForTesting
    public VenueUrlElement(Map<Integer, URL> venueUrls) {
        super(Constants.ANQPElementType.ANQPVenueUrl);
        mVenueUrls = venueUrls;
    }

    /**
     * Parse a VenueUrlElement from the given buffer.
     *
     * @param payload The byte buffer to read from
     * @return {@link VenueUrlElement}
     * @throws BufferUnderflowException
     * @throws ProtocolException
     */
    public static VenueUrlElement parse(ByteBuffer payload)
            throws ProtocolException, BufferUnderflowException {
        Map<Integer, URL> venueUrls = new HashMap<>();
        while (payload.remaining() >= 2) {
            int length = payload.get() & 0xFF;
            int venueNumber = payload.get() & 0xFF;
            if (venueNumber == 0) {
                // In case no Venue Name Tuple subfield was returned in the Venue Name ANQP-element
                break;
            }
            String parsedUrl = ByteBufferReader.readString(payload, length - 1,
                    StandardCharsets.UTF_8);
            URL url;
            try {
                url = new URL(parsedUrl);
            } catch (java.net.MalformedURLException e) {
                Log.e(TAG, "Malformed venue URL: " + parsedUrl + " at index " + venueNumber);
                throw new ProtocolException("Malformed venue URL: " + parsedUrl + " at index "
                        + venueNumber);
            }
            // Reject URLs that are not HTTPS
            String protocol = url.getProtocol();
            if (!TextUtils.equals(protocol, "https")) {
                Log.w(TAG, "Non-HTTPS Venue URL dropped: " + url);
            } else {
                venueUrls.put(Integer.valueOf(venueNumber), url);
            }
        }

        return new VenueUrlElement(venueUrls);
    }

    public Map<Integer, URL> getVenueUrls() {
        return mVenueUrls;
    }

    @Override
    public boolean equals(Object thatObject) {
        if (this == thatObject) {
            return true;
        }
        if (!(thatObject instanceof VenueUrlElement)) {
            return false;
        }
        VenueUrlElement that = (VenueUrlElement) thatObject;
        return mVenueUrls.equals(that.mVenueUrls);
    }

    @Override
    public int hashCode() {
        return mVenueUrls.hashCode();
    }

    @Override
    public String toString() {
        return "VenueUrl{ mUrlList=" + mVenueUrls + "}";
    }

}
