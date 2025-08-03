/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.server.wifi.hotspot2;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import androidx.test.filters.SmallTest;

import com.android.server.wifi.Clock;
import com.android.server.wifi.WifiBaseTest;
import com.android.server.wifi.hotspot2.anqp.ANQPElement;
import com.android.server.wifi.hotspot2.anqp.Constants;
import com.android.server.wifi.hotspot2.anqp.I18Name;
import com.android.server.wifi.hotspot2.anqp.VenueNameElement;
import com.android.server.wifi.hotspot2.anqp.VenueUrlElement;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Unit tests for {@link com.android.server.wifi.hotspot2.AnqpCache}.
 *
 * TODO(b/33000864): add more test once the ANQP elements cleanup are completed, which will
 * allow easy construction of ANQP elements for testing.
 */
@SmallTest
public class AnqpCacheTest extends WifiBaseTest {
    private static final ANQPNetworkKey ENTRY_KEY = new ANQPNetworkKey("test", 0L, 0L, 1);
    private static final String TEST_LANGUAGE = "en";
    private static final Locale TEST_LOCALE = Locale.forLanguageTag(TEST_LANGUAGE);
    private static final String TEST_VENUE_NAME1 = "Venue1";
    private static final String TEST_VENUE_NAME2 = "Venue2";
    private static final String TEST_VENUE_URL1 = "https://www.google.com/";
    private static final String TEST_VENUE_URL2 = "https://www.android.com/";
    private static final String TEST_VENUE_URL3 = "https://support.google.com/";

    @Mock Clock mClock;
    AnqpCache mCache;

    /**
     * Sets up test.
     */
    @Before
    public void setUp() throws Exception {
        initMocks(this);
        // Returning the initial timestamp.
        when(mClock.getElapsedSinceBootMillis()).thenReturn(0L);
        mCache = new AnqpCache(mClock);
    }

    /**
     * Verify expectation for addEntry and getEntry.
     *
     * @throws Exception
     */
    @Test
    public void addAndGetEntry() throws Exception {
        mCache.addEntry(ENTRY_KEY, null);
        ANQPData data = mCache.getEntry(ENTRY_KEY);
        assertNotNull(data);
        assertTrue(data.getElements().isEmpty());
    }

    /**
     * Verify that getting a non-existing entry will return a null.
     *
     * @throws Exception
     */
    @Test
    public void getNonExistEntry() throws Exception {
        assertNull(mCache.getEntry(ENTRY_KEY));
    }

    /**
     * Verify the expectation for the sweep function (expired entries will be removed).
     *
     * @throws Exception
     */
    @Test
    public void sweepRemoveExpiredEntry() throws Exception {
        mCache.addEntry(ENTRY_KEY, null);

        // Sweep the cache when the entry is not expired.
        when(mClock.getElapsedSinceBootMillis())
                .thenReturn(AnqpCache.CACHE_SWEEP_INTERVAL_MILLISECONDS);
        mCache.sweep();
        assertNotNull(mCache.getEntry(ENTRY_KEY));

        // Sweep the cache when the entry is expired.
        when(mClock.getElapsedSinceBootMillis()).thenReturn(ANQPData.DATA_LIFETIME_MILLISECONDS);
        mCache.sweep();
        assertNull(mCache.getEntry(ENTRY_KEY));
    }

    /**
     * Verify the expectation for the flush function (all entries will be removed).
     *
     * @throws Exception
     */
    @Test
    public void testCacheFlush() throws Exception {
        mCache.addEntry(ENTRY_KEY, null);
        mCache.flush();
        assertNull(mCache.getEntry(ENTRY_KEY));
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
     * Verify expectation for addOrUpdateEntry and getEntry.
     *
     * @throws Exception
     */
    @Test
    public void addThenUpdateAndGetEntry() throws Exception {
        Map<Constants.ANQPElementType, ANQPElement> anqpList1 = new HashMap<>();
        List<I18Name> nameList = new ArrayList<>();
        nameList.add(new I18Name(TEST_LANGUAGE, TEST_LOCALE, TEST_VENUE_NAME1));
        nameList.add(new I18Name(TEST_LANGUAGE, TEST_LOCALE, TEST_VENUE_NAME2));
        VenueNameElement venueNameElement = new VenueNameElement(nameList);

        // Add one ANQP element
        anqpList1.put(Constants.ANQPElementType.ANQPVenueName, venueNameElement);
        mCache.addOrUpdateEntry(ENTRY_KEY, anqpList1);
        ANQPData data = mCache.getEntry(ENTRY_KEY);
        assertNotNull(data);
        assertFalse(data.getElements().isEmpty());
        assertTrue(data.getElements().get(Constants.ANQPElementType.ANQPVenueName)
                .equals(venueNameElement));

        // Add another ANQP element to the same entry
        Map<Constants.ANQPElementType, ANQPElement> anqpList2 = new HashMap<>();
        Map<Integer, URL> urlList = new HashMap<>();
        urlList.put(Integer.valueOf(1), createUrlFromString(TEST_VENUE_URL1));
        urlList.put(Integer.valueOf(2), createUrlFromString(TEST_VENUE_URL2));
        urlList.put(Integer.valueOf(4), createUrlFromString(TEST_VENUE_URL3));
        VenueUrlElement venueUrlElement = new VenueUrlElement(urlList);
        anqpList2.put(Constants.ANQPElementType.ANQPVenueUrl, venueUrlElement);
        mCache.addOrUpdateEntry(ENTRY_KEY, anqpList2);
        data = mCache.getEntry(ENTRY_KEY);
        assertTrue(data.getElements().get(Constants.ANQPElementType.ANQPVenueName)
                .equals(venueNameElement));
        assertTrue(data.getElements().get(Constants.ANQPElementType.ANQPVenueUrl)
                .equals(venueUrlElement));
    }
}
