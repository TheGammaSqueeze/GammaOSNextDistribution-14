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
package com.android.timezone.location.validation;

import com.google.common.geometry.S2LatLng;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.stream.Stream;

/** A support class for accessing a geonames "cities" file. */
class CitiesFile {

    /** The index of the value containing the city name. */
    private static final int NAME_INDEX = 1;
    /** The index of the value containing the city latitude. */
    private static final int LATITUDE_INDEX = 4;
    /** The index of the value containing the city longitude. */
    private static final int LONGITUDE_INDEX = 5;
    /** The index of the value containing the city ISO 3166 alpha-2 country code. */
    private static final int ISO_COUNTRY_CODE_INDEX = 8;
    /** The index of the value containing the time zone Olson ID. */
    private static final int POPULATION_INDEX = 14;
    /** The index of the value containing the time zone Olson ID. */
    private static final int TIME_ZONE_ID_INDEX = 17;

    /** An individual geonames city entry. */
    static class City {

        private final String mName;
        private final S2LatLng mLatLng;
        private final String mIsoCountryCode;
        private final int mPopulation;
        private final String mTimeZoneId;

        City(String name, S2LatLng latLng, String isoCountryCode, int population,
                String timeZoneId) {
            this.mName = Objects.requireNonNull(name);
            this.mLatLng = Objects.requireNonNull(latLng);
            this.mIsoCountryCode = isoCountryCode;
            this.mPopulation = population;
            this.mTimeZoneId = timeZoneId;
        }

        String getName() {
            return mName;
        }

        S2LatLng getLatLng() {
            return mLatLng;
        }

        String getIsoCountryCode() {
            return mIsoCountryCode;
        }

        int getPopulation() {
            return mPopulation;
        }

        String getTimeZoneId() {
            return mTimeZoneId;
        }
    }

    private CitiesFile() {
    }

    /** Creates a {@link Stream} of {@link City} objects from the specified file. */
    static Stream<City> read(File file) throws IOException {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        new FileInputStream(file), StandardCharsets.UTF_8));
        return reader.lines().map(CitiesFile::createCity);
    }

    private static City createCity(String s) {
        String[] fields = s.split("\\t");
        String name = fields[NAME_INDEX];
        String lat = fields[LATITUDE_INDEX];
        String lng = fields[LONGITUDE_INDEX];
        S2LatLng latLng = S2LatLng.fromDegrees(Double.parseDouble(lat), Double.parseDouble(lng));

        String isoCountryCode = fields[ISO_COUNTRY_CODE_INDEX];
        if (isoCountryCode.isEmpty()) {
            isoCountryCode = null;
        } else {
            isoCountryCode = isoCountryCode.toLowerCase();
        }

        String populationString = fields[POPULATION_INDEX];
        int population = 0;
        if (populationString.isEmpty()) {
            System.out.println("No population found for " + name);
        } else {
            population = Integer.parseInt(populationString);
        }

        String timeZoneId = fields[TIME_ZONE_ID_INDEX];
        if (timeZoneId.isEmpty()) {
            timeZoneId = null;
        }

        return new City(name, latLng, isoCountryCode, population, timeZoneId);
    }
}
