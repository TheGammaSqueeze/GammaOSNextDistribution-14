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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.test.filters.SmallTest;

import com.android.server.wifi.WifiBaseTest;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Unit tests for {@link VenueUrlElement}.
 */
@SmallTest
public class VenueUrlElementTest extends WifiBaseTest {
    private static final String TEST_VENUE_URL1 = "https://www.google.com/";
    private static final String TEST_VENUE_URL2 = "https://www.android.com/";
    private static final String TEST_VENUE_URL3 = "https://support.google.com/";
    private static final String TEST_VENUE_URL_INSECURE = "http://support.google.com/";
    private static final String TEST_VENUE_URL_CAPS = "HTTPS://SUPPORT.GOOGLE.COM/";
    private static final String TEST_VENUE_URL_INSECURE_CAPS = "HTTP://SUPPORT.GOOGLE.COM/";
    private static final String TEST_VENUE_URL_INVALID = "htps://invalid.com/";

    /**
     * Helper function for appending a Venue URL to an output stream.
     *
     * @param stream      Stream to write to
     * @param venueNumber Venue number
     * @param url         The URL string
     */
    private void appendVenue(ByteArrayOutputStream stream, int venueNumber, String url)
            throws IOException {
        byte[] venueBytes = url.getBytes(StandardCharsets.UTF_8);
        int length = venueBytes.length + 1;
        stream.write((byte) length);
        stream.write((byte) venueNumber);
        stream.write(venueBytes);
    }

    /**
     * Helper function for generating test data.
     *
     * @return byte[] of data
     */
    private byte[] getTestData(Map<Integer, URL> urls) throws IOException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();

        for (Map.Entry<Integer, URL> entry : urls.entrySet()) {
            appendVenue(stream, entry.getKey(), entry.getValue().toString());
        }
        return stream.toByteArray();
    }

    /**
     * Helper function for generating test data.
     *
     * @return byte[] of data
     */
    private byte[] getTestData(int venueNumber, String url) throws IOException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        appendVenue(stream, venueNumber, url);
        return stream.toByteArray();
    }

    /**
     * Verify that an empty URL list will be returned when parsing an empty buffer.
     */
    @Test
    public void parseEmptyBuffer() throws Exception {
        assertTrue(VenueUrlElement.parse(ByteBuffer.allocate(0)).getVenueUrls().isEmpty());
    }

    /**
     * Verify that BufferUnderflowException will be thrown when parsing a truncated buffer
     * (missing a byte at the end).
     */
    @Test(expected = BufferUnderflowException.class)
    public void parseTruncatedBuffer() throws Exception {
        Map<Integer, URL> urlList = new HashMap<>();
        urlList.put(Integer.valueOf(1), createUrlFromString(TEST_VENUE_URL1));
        ByteBuffer buffer = ByteBuffer.wrap(getTestData(urlList));
        // Truncate a byte at the end.
        buffer.limit(buffer.remaining() - 1);
        VenueUrlElement.parse(buffer);
    }

    /**
     * Verify that ProtocolException is thrown when parsing a buffer with an empty venue URL.
     */
    @Test (expected = ProtocolException.class)
    public void parseBufferWithEmptyVenueUrl() throws Exception {
        ByteBuffer buffer = ByteBuffer.wrap(getTestData(1, new String()));
        VenueUrlElement.parse(buffer);
    }

    /**
     * Verify that ProtocolException is thrown when parsing a buffer with an invalid venue URL.
     */
    @Test (expected = ProtocolException.class)
    public void parseBufferWithInvalidVenueUrl() throws Exception {
        ByteBuffer buffer = ByteBuffer.wrap(getTestData(1, TEST_VENUE_URL_INVALID));
        VenueUrlElement.parse(buffer);
    }

    /**
     * Verify that BufferUnderflowException is thrown when parsing a buffer with an invalid length.
     */
    @Test (expected = BufferUnderflowException.class)
    public void parseBufferWithInvalidLength() throws Exception {
        // Craft a payload with an invalid length that should cause underflow when parsing
        String url = TEST_VENUE_URL1;
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        byte[] venueBytes = url.getBytes(StandardCharsets.UTF_8);
        int length = venueBytes.length + 3; // One extra byte
        stream.write((byte) length);
        stream.write((byte) 1 /* venueNumber */);
        stream.write(venueBytes);
        VenueUrlElement.parse(ByteBuffer.wrap(stream.toByteArray()));
    }

    /**
     * Verify that a VenueUrlElement with empty URL list will be returned when parsing a buffer
     * contained venue number equals to 0 and no venue URL (only contained the venue URL data).
     */
    @Test
    public void parseBufferWithZeroVenueNumber() throws Exception {
        ByteBuffer buffer = ByteBuffer.wrap(getTestData(0, new String()));
        assertTrue(VenueUrlElement.parse(buffer).getVenueUrls().isEmpty());
    }

    /**
     * Verify that a VenueUrlElement with empty URL list will be returned when parsing a buffer
     * contained venue number equals to 0 and some venue URL (only contained the venue URL data).
     */
    @Test
    public void parseBufferWithZeroVenueNumberAndUrlData() throws Exception {
        ByteBuffer buffer = ByteBuffer.wrap(getTestData(0, TEST_VENUE_URL1));
        assertTrue(VenueUrlElement.parse(buffer).getVenueUrls().isEmpty());
    }

    /**
     * Verify that a VenueUrlElement with a single URL will be returned when parsing a buffer
     * contained a negative venue number and some venue URL. The negative number will be parsed
     * as a positive number (two's complement).
     */
    @Test
    public void parseBufferWithNegativeVenueNumberAndUrlData() throws Exception {
        // Setup expected element.
        Map<Integer, URL> urlList = new HashMap<>();
        urlList.put(Integer.valueOf(249), createUrlFromString(TEST_VENUE_URL1));
        VenueUrlElement expectedElement = new VenueUrlElement(urlList);

        ByteBuffer buffer = ByteBuffer.wrap(getTestData(-7, TEST_VENUE_URL1));
        assertEquals(expectedElement, VenueUrlElement.parse(buffer));
    }

    private URL createUrlFromString(String stringUrl) {
        URL url;
        try {
            url = new URL(stringUrl);
        } catch (java.net.MalformedURLException e) {
            return null;
        }
        return url;
    }

    /**
     * Verify that an expected VenueUrlElement will be returned when parsing a buffer contained
     * valid Venue URL data.
     */
    @Test
    public void parseBufferWithValidVenueUrls() throws Exception {
        // Setup expected element.
        Map<Integer, URL> urlList = new HashMap<>();
        urlList.put(Integer.valueOf(1), createUrlFromString(TEST_VENUE_URL1));
        urlList.put(Integer.valueOf(2), createUrlFromString(TEST_VENUE_URL2));
        urlList.put(Integer.valueOf(4), createUrlFromString(TEST_VENUE_URL3));
        VenueUrlElement expectedElement = new VenueUrlElement(urlList);

        ByteBuffer buffer = ByteBuffer.wrap(getTestData(urlList));
        assertEquals(expectedElement, VenueUrlElement.parse(buffer));
    }

    /**
     * Verify that an expected VenueUrlElement will be returned when parsing a buffer contained
     * valid Venue URL data.
     */
    @Test
    public void parseBufferWithValidVenueUrlsAndCapsUrls() throws Exception {
        // Setup expected element.
        Map<Integer, URL> urlList = new HashMap<>();
        urlList.put(Integer.valueOf(1), createUrlFromString(TEST_VENUE_URL1));
        urlList.put(Integer.valueOf(2), createUrlFromString(TEST_VENUE_URL2));
        urlList.put(Integer.valueOf(3), createUrlFromString(TEST_VENUE_URL_CAPS));
        urlList.put(Integer.valueOf(4), createUrlFromString(TEST_VENUE_URL3));
        VenueUrlElement expectedElement = new VenueUrlElement(urlList);

        ByteBuffer buffer = ByteBuffer.wrap(getTestData(urlList));
        assertEquals(expectedElement, VenueUrlElement.parse(buffer));
    }

    /**
     * Verify that a VenueUrlElement with empty URL list will be returned when parsing a buffer
     * with an insecure (Non-HTTPS) URL.
     */
    @Test
    public void parseBufferWithInsecureUrlData() throws Exception {
        ByteBuffer buffer = ByteBuffer.wrap(getTestData(1, TEST_VENUE_URL_INSECURE));
        assertTrue(VenueUrlElement.parse(buffer).getVenueUrls().isEmpty());
    }

    /**
     * Verify that a VenueUrlElement with empty URL list will be returned when parsing a buffer
     * with an insecure (Non-HTTPS) URL (letters capitalized).
     */
    @Test
    public void parseBufferWithInsecureCapsUrlData() throws Exception {
        ByteBuffer buffer = ByteBuffer.wrap(getTestData(1, TEST_VENUE_URL_INSECURE_CAPS));
        assertTrue(VenueUrlElement.parse(buffer).getVenueUrls().isEmpty());
    }

    /**
     * Verify that an expected VenueUrlElement will be returned when parsing a buffer contained
     * valid Venue URL data and a single insecure URL which will be dropped.
     */
    @Test
    public void parseBufferWithValidVenueUrlsAndOneInsecureUrl() throws Exception {
        // Setup expected element.
        Map<Integer, URL> urlList = new HashMap<>();
        urlList.put(Integer.valueOf(1), createUrlFromString(TEST_VENUE_URL1));
        urlList.put(Integer.valueOf(2), createUrlFromString(TEST_VENUE_URL2));
        urlList.put(Integer.valueOf(3), createUrlFromString(TEST_VENUE_URL_INSECURE));
        urlList.put(Integer.valueOf(4), createUrlFromString(TEST_VENUE_URL3));

        // Create a buffer with a single insecure URL
        ByteBuffer buffer = ByteBuffer.wrap(getTestData(urlList));

        // Create the expected result
        urlList.remove(Integer.valueOf(3));
        VenueUrlElement expectedElement = new VenueUrlElement(urlList);

        assertEquals(expectedElement, VenueUrlElement.parse(buffer));
    }
}
