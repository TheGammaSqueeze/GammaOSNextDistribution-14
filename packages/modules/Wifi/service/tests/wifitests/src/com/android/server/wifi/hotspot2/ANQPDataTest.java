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
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import androidx.test.filters.SmallTest;

import com.android.server.wifi.Clock;
import com.android.server.wifi.WifiBaseTest;
import com.android.server.wifi.hotspot2.anqp.ANQPElement;
import com.android.server.wifi.hotspot2.anqp.CellularNetwork;
import com.android.server.wifi.hotspot2.anqp.Constants;
import com.android.server.wifi.hotspot2.anqp.HSWanMetricsElement;
import com.android.server.wifi.hotspot2.anqp.I18Name;
import com.android.server.wifi.hotspot2.anqp.ThreeGPPNetworkElement;
import com.android.server.wifi.hotspot2.anqp.VenueNameElement;
import com.android.server.wifi.hotspot2.anqp.VenueUrlElement;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.net.URL;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Unit tests for {@link com.android.server.wifi.hotspot2.ANQPData}.
 *
 * TODO(b/33000864): add more test once the ANQP elements cleanup are completed, which will
 * allow easy construction of ANQP elements for testing.
 */
@SmallTest
public class ANQPDataTest extends WifiBaseTest {
    @Mock Clock mClock;
    private static final String TEST_LANGUAGE = "en";
    private static final Locale TEST_LOCALE = Locale.forLanguageTag(TEST_LANGUAGE);
    private static final String TEST_VENUE_NAME1 = "Venue1";
    private static final String TEST_VENUE_NAME2 = "Venue2";
    private static final String TEST_VENUE_URL1 = "https://www.google.com/";
    private static final long TEST_CLOCK_MILLISEOCONDS = 10000L;

    /**
     * Sets up test.
     */
    @Before
    public void setUp() throws Exception {
        initMocks(this);
        // Returning the initial timestamp.
        when(mClock.getElapsedSinceBootMillis()).thenReturn(0L);
    }

    /**
     * Verify creation of ANQPData with null elements.
     *
     * @throws Exception
     */
    @Test
    public void createWithNullElements() throws Exception {
        ANQPData data = new ANQPData(mClock, null);
        Map<Constants.ANQPElementType, ANQPElement> elements = data.getElements();
        assertTrue(elements.isEmpty());
    }

    /**
     * Verify the data expiration behavior.
     *
     * @throws Exception
     */
    @Test
    public void verifyExpiration() throws Exception {
        ANQPData data = new ANQPData(mClock, null);
        assertFalse(data.expired(ANQPData.DATA_LIFETIME_MILLISECONDS - 1));
        assertTrue(data.expired(ANQPData.DATA_LIFETIME_MILLISECONDS));
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
     * Verify creation of ANQPData with data elements and then update the entry.
     *
     * @throws Exception
     */
    @Test
    public void createWithElementsAndUpdate() throws Exception {
        Map<Constants.ANQPElementType, ANQPElement> anqpList1 = new HashMap<>();
        List<I18Name> nameList = new ArrayList<>();
        nameList.add(new I18Name(TEST_LANGUAGE, TEST_LOCALE, TEST_VENUE_NAME1));
        VenueNameElement venueNameElement = new VenueNameElement(nameList);

        // Add one ANQP element
        anqpList1.put(Constants.ANQPElementType.ANQPVenueName, venueNameElement);
        ANQPData data = new ANQPData(mClock, anqpList1);
        assertNotNull(data);
        assertFalse(data.getElements().isEmpty());
        assertTrue(data.getElements().get(Constants.ANQPElementType.ANQPVenueName)
                .equals(venueNameElement));

        // Add another ANQP element to the same entry
        Map<Constants.ANQPElementType, ANQPElement> anqpList2 = new HashMap<>();
        Map<Integer, URL> urlList = new HashMap<>();
        urlList.put(Integer.valueOf(1), createUrlFromString(TEST_VENUE_URL1));
        VenueUrlElement venueUrlElement = new VenueUrlElement(urlList);
        anqpList2.put(Constants.ANQPElementType.ANQPVenueUrl, venueUrlElement);

        // Update the name
        nameList = new ArrayList<>();
        nameList.add(new I18Name(TEST_LANGUAGE, TEST_LOCALE, TEST_VENUE_NAME2));
        venueNameElement = new VenueNameElement(nameList);
        anqpList2.put(Constants.ANQPElementType.ANQPVenueName, venueNameElement);

        data.update(anqpList2);
        assertTrue(data.getElements().get(Constants.ANQPElementType.ANQPVenueName)
                .equals(venueNameElement));
        assertTrue(data.getElements().get(Constants.ANQPElementType.ANQPVenueUrl)
                .equals(venueUrlElement));
    }

    /**
     * Verify the correct data lifetime of an ANQP entry
     *
     * @throws Exception
     */
    @Test
    public void testDataLifetime() throws Exception {
        Map<Constants.ANQPElementType, ANQPElement> anqpList = new HashMap<>();
        List<I18Name> nameList = new ArrayList<>();
        nameList.add(new I18Name(TEST_LANGUAGE, TEST_LOCALE, TEST_VENUE_NAME1));
        VenueNameElement venueNameElement = new VenueNameElement(nameList);
        CellularNetwork cellularNetwork =
                new CellularNetwork(Arrays.asList(new String[]{"123456"}));
        ThreeGPPNetworkElement threeGPPNetworkElement =
                new ThreeGPPNetworkElement(Arrays.asList(new CellularNetwork[]{cellularNetwork}));
        // Setup an uninitialized WAN Metrics element (or initialized with 0's)
        ByteBuffer buffer = ByteBuffer.allocate(HSWanMetricsElement.EXPECTED_BUFFER_SIZE);
        buffer.put(new byte[HSWanMetricsElement.EXPECTED_BUFFER_SIZE]);
        buffer.position(0);
        HSWanMetricsElement wanMetricsElement = HSWanMetricsElement.parse(buffer);

        // Add ANQP elements
        anqpList.put(Constants.ANQPElementType.ANQPVenueName, venueNameElement);
        anqpList.put(Constants.ANQPElementType.ANQP3GPPNetwork, threeGPPNetworkElement);
        anqpList.put(Constants.ANQPElementType.HSWANMetrics, wanMetricsElement);
        when(mClock.getElapsedSinceBootMillis()).thenReturn(10000L);

        // Data life should be DATA_LIFETIME_MILLISECONDS
        ANQPData data = new ANQPData(mClock, anqpList);
        assertNotNull(data);
        assertFalse(data.getElements().isEmpty());
        assertFalse(
                data.expired(TEST_CLOCK_MILLISEOCONDS + ANQPData.DATA_LIFETIME_MILLISECONDS - 1));
        assertTrue(data.expired(TEST_CLOCK_MILLISEOCONDS + ANQPData.DATA_LIFETIME_MILLISECONDS));

        wanMetricsElement = new HSWanMetricsElement(HSWanMetricsElement.LINK_STATUS_UP, true, false,
                10000, 10000, 50, 50, 0);
        anqpList.put(Constants.ANQPElementType.HSWANMetrics, wanMetricsElement);

        // Data life should be DATA_LIFETIME_MILLISECONDS
        data = new ANQPData(mClock, anqpList);
        assertNotNull(data);
        assertFalse(data.getElements().isEmpty());
        assertFalse(
                data.expired(TEST_CLOCK_MILLISEOCONDS + ANQPData.DATA_LIFETIME_MILLISECONDS - 1));
        assertTrue(data.expired(TEST_CLOCK_MILLISEOCONDS + ANQPData.DATA_LIFETIME_MILLISECONDS));

        // The following checks should result in a short data lifetime
        wanMetricsElement = new HSWanMetricsElement(HSWanMetricsElement.LINK_STATUS_DOWN, true,
                false, 10000, 10000, 50, 50, 0);
        anqpList.put(Constants.ANQPElementType.HSWANMetrics, wanMetricsElement);

        // Data life should be DATA_SHORT_LIFETIME_MILLISECONDS
        data = new ANQPData(mClock, anqpList);
        assertNotNull(data);
        assertFalse(data.getElements().isEmpty());
        assertFalse(data.expired(
                TEST_CLOCK_MILLISEOCONDS + ANQPData.DATA_SHORT_LIFETIME_MILLISECONDS - 1));
        assertTrue(
                data.expired(TEST_CLOCK_MILLISEOCONDS + ANQPData.DATA_SHORT_LIFETIME_MILLISECONDS));

        // The following checks should result in a short data lifetime
        wanMetricsElement = new HSWanMetricsElement(HSWanMetricsElement.LINK_STATUS_UP, true, true,
                10000, 10000, 50, 50, 0);
        anqpList.put(Constants.ANQPElementType.HSWANMetrics, wanMetricsElement);

        // Data life should be DATA_SHORT_LIFETIME_MILLISECONDS
        data = new ANQPData(mClock, anqpList);
        assertNotNull(data);
        assertFalse(data.getElements().isEmpty());
        assertFalse(data.expired(
                TEST_CLOCK_MILLISEOCONDS + ANQPData.DATA_SHORT_LIFETIME_MILLISECONDS - 1));
        assertTrue(
                data.expired(TEST_CLOCK_MILLISEOCONDS + ANQPData.DATA_SHORT_LIFETIME_MILLISECONDS));
    }
}
