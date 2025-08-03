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
package com.android.timezone.location.lookup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.android.timezone.location.lookup.GeoTimeZonesFinder.LocationToken;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

/** Device-side confidence checks for {@link GeoTimeZonesFinder}. */
public class GeoTimeZonesFinderTest {

    private File mGeoDataFile;

    @Before
    public void setUp() throws Exception {
        // Extract the tzs2.dat resource as a file so it can be used by the GeoTimeZonesFinder.
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("tzs2.dat");
        Path geoDataPath = Files.createTempFile("GeoTimeZonesFinderTest", ".dat");
        Files.copy(inputStream, geoDataPath, StandardCopyOption.REPLACE_EXISTING);
        mGeoDataFile = geoDataPath.toFile();
    }

    @After
    public void tearDown() throws Exception {
        if (mGeoDataFile != null) {
            mGeoDataFile.delete();
        }
    }

    @Test
    public void sampleLookups() throws Exception {
        // Uncontroversial + unambiguous locations and answers for time zone lookups.
        double[][] latLngDegreess = {
                { 51.4934, 0 }, // London, UK
                { 48.8566, 2.3522 }, // Paris, France
                { 37.3861, -122.0839 }, // Mountain View CA, USA
                { 39.9042, 116.4074 }, // Beijing, China
                { -33.8688, 151.2093 }, // Sydney, Australia
        };
        String[] timeZoneIds = {
                "Europe/London",
                "Europe/Paris",
                "America/Los_Angeles",
                "Asia/Shanghai",
                "Australia/Sydney",
        };

        try (GeoTimeZonesFinder geoTimeZonesFinder = getGeoTimeZonesFinderForTest()) {
            for (int i = 0; i < latLngDegreess.length; i++) {
                double latDegrees = latLngDegreess[i][0];
                double lngDegrees = latLngDegreess[i][1];
                String expectedTimeZoneId = timeZoneIds[i];
                assertEquals(Arrays.asList(expectedTimeZoneId),
                        geoTimeZonesFinder.findTimeZonesForLatLng(latDegrees, lngDegrees));
            }
        }
    }

    @Test
    public void locationTokenBehavior() throws Exception {
        try (GeoTimeZonesFinder geoTimeZonesFinder = getGeoTimeZonesFinderForTest()) {
            double greenwichLat = 51.4934;
            double greenwichLng = 0;
            LocationToken greenwichToken =
                    geoTimeZonesFinder.createLocationTokenForLatLng(greenwichLat, greenwichLng);
            assertEquals(Arrays.asList("Europe/London"),
                    geoTimeZonesFinder.findTimeZonesForLocationToken(greenwichToken));
            assertEquals(Arrays.asList("Europe/London"),
                    geoTimeZonesFinder.findTimeZonesForLatLng(greenwichLat, greenwichLng));

            // Find a lat/lng that is different, but has the same location token. The token
            // algorithm is implementation dependent, so just keep making smaller and smaller
            // adjustments until we find something that generates the same token.
            LocationToken greenwichToken2;
            double greenwichLat2;
            double latAdjustment = 0.1;
            double greenwichLng2 = greenwichLng;
            do {
                latAdjustment /= 10;
                greenwichLat2 = greenwichLat + latAdjustment;
                greenwichToken2 = geoTimeZonesFinder.createLocationTokenForLatLng(
                        greenwichLat2, greenwichLng2);
            } while (!greenwichToken.equals(greenwichToken2) && latAdjustment > 0);

            if (latAdjustment == 0) {
                fail("Unable to compute a second test location for Greenwich");
            }

            // The results should be the same.
            assertEquals(geoTimeZonesFinder.findTimeZonesForLocationToken(greenwichToken),
                    geoTimeZonesFinder.findTimeZonesForLocationToken(greenwichToken2));
            assertEquals(geoTimeZonesFinder.findTimeZonesForLatLng(greenwichLat, greenwichLng),
                    geoTimeZonesFinder.findTimeZonesForLatLng(greenwichLat2, greenwichLng2));
        }
    }

    private GeoTimeZonesFinder getGeoTimeZonesFinderForTest() throws IOException {
        return GeoTimeZonesFinder.create(mGeoDataFile);
    }
}
