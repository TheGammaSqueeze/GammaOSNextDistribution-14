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

import static java.util.stream.Collectors.toList;

import com.android.timezone.location.lookup.GeoTimeZonesFinder;
import com.android.timezone.location.validation.CitiesFile.City;
import com.android.timezone.location.validation.Types.KnownDifference;
import com.android.timezone.location.validation.Types.KnownDifferenceMismatch;
import com.android.timezone.location.validation.Types.KnownDifferences;
import com.android.timezone.location.validation.Types.Result;
import com.android.timezone.location.validation.Types.TestCaseId;
import com.android.timezone.tzids.TimeZoneIds;

import com.google.common.geometry.S2CellId;
import com.google.common.geometry.S2LatLng;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * A tool that compares the Android location time zone data against geonames data, recording
 * differences and (optionally) comparing them with an historic set of known differences.
 *
 * <p>This can be used to understand what has changed when assessing a new tzs2.dat file or geonames
 * data: we don't expect them to 100% agree, but we are interested in changes to the way they
 * disagree.
 */
public class GeonamesComparison {

    private static final int KNOWN_DIFFERENCE_S2_LEVEL = 16;
    private static final boolean DBG = false;
    private static final boolean OUTPUT_CONFIRMED_KNOWN_DIFFERENCES = false;
    private static final boolean OUTPUT_AMBIGUOUS_RESULT_INFO = false;

    /**
     * Runs the comparison.
     *
     * <p>Args:
     * {tzs2.dat file} {tzids.prototxt file} {ISO 8601 date/time for tz ID replacement}
     * {geonames cities file} {output dir} {knowndifferences.prototxt files comma separated}
     */
    public static void main(String[] args) throws Exception {
        File tzS2File = new File(args[0]);
        File tzIdsFile = new File(args[1]);
        Instant replacementThreshold = Instant.parse(args[2]);
        File geoNamesCitiesFile = new File(args[3]);
        File outputDir = new File(args[4]);
        String knownDifferencesFiles = "";
        if (args.length == 6) {
            knownDifferencesFiles = args[5];
        }

        // Load Android's time zone aliases so we can account for cases where Android may us a
        // different ID than the upstream data.
        TimeZoneIds androidTimeZoneIds = TimeZoneIds.load(tzIdsFile);

        // Load known differences, if any, so we can ignore them and only highlight new differences.
        KnownDifferencesRegistry knownDifferencesRegistry = new KnownDifferencesRegistry();
        if (!knownDifferencesFiles.isEmpty()) {
            String[] knownDifferencesFileNames = knownDifferencesFiles.split(",");
            for (String knownDifferencesFileName : knownDifferencesFileNames) {
                knownDifferencesRegistry.addKnownDifferences(new File(knownDifferencesFileName));
            }
        }

        try (GeoTimeZonesFinder geoTzFinder = GeoTimeZonesFinder.create(tzS2File);
                Stream<City> cityStream = CitiesFile.read(geoNamesCitiesFile)) {
            Iterator<City> cityIterator = cityStream.iterator();
            int locationCheckCount = 0;
            while (cityIterator.hasNext()) {
                locationCheckCount++;
                City city = cityIterator.next();

                String geonamesTimeZoneId = city.getTimeZoneId();
                String geonamesCountryCode = city.getIsoCountryCode();
                String androidCountryCodeForZoneId =
                        androidTimeZoneIds.getCountryCodeForZoneId(geonamesTimeZoneId);

                if (androidCountryCodeForZoneId == null) {
                    logWarn("Geonames data=" + city + " is using an unexpected zoneId:"
                            + " Android cannot map geonamesTimeZoneId=" + geonamesTimeZoneId
                            + " to a country");
                    continue;
                }

                // Modify the geonames expectation to account for Android's preferred time zone IDs.
                Map<String, String> countryIdMap = androidTimeZoneIds.getCountryIdMap(
                        androidCountryCodeForZoneId, replacementThreshold);
                String adjustedGeonamesTimeZoneId = countryIdMap.get(geonamesTimeZoneId);
                if (!adjustedGeonamesTimeZoneId.equals(geonamesTimeZoneId)) {
                    logDebug("Mapped geonames tz=" + geonamesTimeZoneId
                            + " to " + adjustedGeonamesTimeZoneId);
                }

                Result adjustedGeonamesResult = new Result(
                        Collections.singletonList(geonamesCountryCode),
                        Collections.singletonList(adjustedGeonamesTimeZoneId));

                // Perform the Android data lookup.
                S2LatLng latLng = city.getLatLng();
                List<String> cityTimeZoneIds = geoTzFinder.findTimeZonesForLatLng(
                        latLng.latDegrees(), latLng.lngDegrees());
                Result actualResult = createResult(androidTimeZoneIds, cityTimeZoneIds);

                // Remove any matching known difference so we can easily detect old known
                // differences that are no longer being tested.
                S2CellId s2CellId = S2CellId.fromLatLng(latLng);
                S2CellId knownDifferencesCellId = s2CellId.parent(KNOWN_DIFFERENCE_S2_LEVEL);
                TestCaseId testCaseId = new TestCaseId(city.getName(), knownDifferencesCellId);
                knownDifferencesRegistry.recordResult(
                        testCaseId, city, adjustedGeonamesResult, actualResult);
            }

            knownDifferencesRegistry.outputKnownDifferencesProtos(outputDir);

            System.out.println("Checked locations: " + locationCheckCount);
            knownDifferencesRegistry.printStatsReport();
        }
    }

    private static void outputKnownDifferences(File outputFile,
            List<KnownDifference> knownDifferencesList) throws IOException {
        KnownDifferences knownDifferences = KnownDifferences.create(knownDifferencesList);
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(outputFile),
                StandardCharsets.UTF_8)) {
            writer.write(knownDifferences.toProtoText());
        }
    }

    private static void outputKnownDifferenceMismatches(
            File outputFile, List<KnownDifferenceMismatch> knownDifferenceMismatches)
            throws IOException {

        try (Writer writer = new OutputStreamWriter(new FileOutputStream(outputFile),
                StandardCharsets.UTF_8)) {
            for (KnownDifferenceMismatch knownDifferenceMismatch : knownDifferenceMismatches) {
                writer.append("Recorded known difference:\n");
                KnownDifference referenceDataKnownDifference =
                        knownDifferenceMismatch.getReferenceDataKnownDifference();
                writer.append(referenceDataKnownDifference.toProtoText());
                writer.append("\n\n");
                writer.append("Actual:\n");
                writer.append(knownDifferenceMismatch.getActualKnownDifference().toProtoText());
                writer.append("\n\n===========================\n");
            }
        }
    }

    private static Types.KnownDifference createNewKnownDifference(
            TestCaseId testCaseId, Result referenceDataResult, Result actualResult) {
        return new Types.KnownDifference(
                testCaseId, referenceDataResult, actualResult, Types.KnownDifference.Type.UNKNOWN,
                "Add a comment here", URI.create("http://b/bugId"));
    }

    private static Result createResult(
            TimeZoneIds androidTimeZoneIds, List<String> cityTimeZoneIds) {
        List<String> uniqueIsoCountryCodes = cityTimeZoneIds.stream()
                .map(androidTimeZoneIds::getCountryCodeForZoneId)
                .distinct()
                .collect(toList());
        return new Result(uniqueIsoCountryCodes, cityTimeZoneIds);
    }

    static class KnownDifferencesRegistry {

        public Map<TestCaseId, KnownDifference> knownDifferences = new HashMap<>();
        public Map<TestCaseId, Types.KnownDifference> mutableKnownDifferencesMap = new HashMap<>();
        public List<Types.KnownDifference> newKnownDifferences = new ArrayList<>();
        public List<Types.KnownDifference> confirmedKnownDifferences = new ArrayList<>();
        public List<KnownDifferenceMismatch> incorrectKnownDifferences = new ArrayList<>();
        public List<CityResult> multipleIdAndroidResults = new ArrayList<>();

        void addKnownDifferences(File knownDifferencesFile) throws IOException {
            Map<TestCaseId, KnownDifference> knownDifferencesMap =
                    KnownDifferences.load(knownDifferencesFile).buildIdMap();
            Map<TestCaseId, KnownDifference> dupes =
                    putAllNoDupes(knownDifferences, knownDifferencesMap);
            for (TestCaseId dupeKey : dupes.keySet()) {
                logWarn("Duplicated key in file " + knownDifferencesFile + ": " + dupeKey);
            }
            putAllNoDupes(mutableKnownDifferencesMap, knownDifferencesMap);

        }

        private static <K, V> Map<K, V> putAllNoDupes(Map<K, V> target, Map<K, V> source) {
            Map<K, V> dupes = new HashMap<>();
            for (Map.Entry<K, V> entry : source.entrySet()) {
                if (target.putIfAbsent(entry.getKey(), entry.getValue()) != null) {
                    dupes.put(entry.getKey(), entry.getValue());
                }
            }
            return dupes;
        }

        void recordResult(TestCaseId testCaseId,
                City city,
                Result geonamesResult,
                Result androidResult) {

            Types.KnownDifference preexistingKnownDifference =
                    mutableKnownDifferencesMap.remove(testCaseId);
            // A null known difference is the common case.

            if (androidResult.hasMultipleZoneIds()) {
                multipleIdAndroidResults.add(new CityResult(city, androidResult));
            }

            // Check the actual meets expectations.
            if (preexistingKnownDifference == null) {
                if (!geonamesResult.intersects(androidResult)) {
                    // Not a known difference: Report it!
                    Types.KnownDifference newKnownDifference = createNewKnownDifference(
                            testCaseId, geonamesResult, androidResult);
                    newKnownDifferences.add(newKnownDifference);
                }
            } else {
                Result knownDifferenceActualResult =
                        preexistingKnownDifference.getActualResult();
                Result knownDifferenceReferenceDataResult =
                        preexistingKnownDifference.getReferenceDataResult();
                if (knownDifferenceActualResult.equals(androidResult)
                        && knownDifferenceReferenceDataResult.equals(geonamesResult)) {
                    confirmedKnownDifferences.add(preexistingKnownDifference);
                } else {
                    // There is a known difference, but it doesn't match: Report it!
                    KnownDifferenceMismatch mismatch = new KnownDifferenceMismatch(
                            preexistingKnownDifference, geonamesResult, androidResult);
                    incorrectKnownDifferences.add(mismatch);
                }
            }
        }

        void outputKnownDifferencesProtos(File outputDir) throws IOException {
            System.out.println();
            System.out.println("=====================");
            System.out.println("Outputting diff info:");
            System.out.println();

            if (newKnownDifferences.size() > 0) {
                File outputFile = new File(outputDir, "new_diffs.prototxt");
                outputKnownDifferences(outputFile, newKnownDifferences);
                System.out.println("New differences: " + outputFile + " (investigate these)");
                System.out.println();
            }

            if (incorrectKnownDifferences.size() > 0) {
                File outputFile = new File(outputDir, "incorrect_diffs.prototxt");
                outputKnownDifferenceMismatches(outputFile, incorrectKnownDifferences);
                System.out.println(
                        "Incorrect known differences: " + outputFile + " (investigate these)");
                System.out.println();
            }

            if (OUTPUT_CONFIRMED_KNOWN_DIFFERENCES && confirmedKnownDifferences.size() > 0) {
                File outputFile = new File(outputDir, "confirmed_diffs.prototxt");
                outputKnownDifferences(outputFile, confirmedKnownDifferences);
                System.out.println(
                        "Correct known differences: " + outputFile + " (don't touch these)");
                System.out.println();
            }

            // Whatever is left in the mutableKnownDifferencesMap are test cases that no longer
            // exist.
            if (mutableKnownDifferencesMap.size() > 0) {
                File outputFile = new File(outputDir, "missing_diffs.prototxt");
                outputKnownDifferences(outputFile,
                        new ArrayList<>(mutableKnownDifferencesMap.values()));
                System.out.println(
                        "No longer tested known differences: " + outputFile + " (remove these)");
                System.out.println();
            }

            if (OUTPUT_AMBIGUOUS_RESULT_INFO) {
                // Calculate the population of lookups that resulted in ambiguous (multi-zone)
                // answers, which should help us prioritize the parts of the world to look at for
                // resolution improvements.
                Map<Result, Long> ambiguousResultStats = new HashMap<>();
                for (CityResult cityResult : multipleIdAndroidResults) {
                    Result result = cityResult.getResult();
                    ambiguousResultStats.merge(
                            result, (long) cityResult.getCity().getPopulation(), Long::sum);
                }

                if (ambiguousResultStats.size() > 0) {
                    File outputFile = new File(outputDir, "ambiguous_lookups.txt");
                    outputResultCounts(outputFile, ambiguousResultStats);
                    System.out.println("Ambiguous lookups file: " + outputFile);
                }
            }
        }

        private static void outputResultCounts(
                File outputFile, Map<Result, Long> ambiguousResultStats)
                throws IOException {
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(outputFile),
                    StandardCharsets.UTF_8)) {
                writer.write("Result, Population affected\n");
                for (Map.Entry<Result, Long> resultEntry : ambiguousResultStats.entrySet()) {
                    writer.write(resultEntry.getKey() + ": " + resultEntry.getValue());
                    writer.write("\n");
                }
            }
        }

        void printStatsReport() {
            System.out.println("New known differences:" + newKnownDifferences.size());
            System.out.println("Incorrect known differences:" + incorrectKnownDifferences.size());
            System.out.println("Confirmed known differences:" + confirmedKnownDifferences.size());
            System.out.println();
            System.out.println("No longer tested locations: " + mutableKnownDifferencesMap.size());
            System.out.println();
            System.out.println("Number of cities with multiple TZ ID results: "
                    + multipleIdAndroidResults.size());
        }
    }

    private static class CityResult {
        private final CitiesFile.City mCity;
        private final Result mResult;

        CityResult(CitiesFile.City city,
                Result result) {
            this.mCity = city;
            this.mResult = result;
        }

        CitiesFile.City getCity() {
            return mCity;
        }

        Result getResult() {
            return mResult;
        }
    }

    private static void logWarn(String message) {
        System.err.println(message);
    }

    private static void logDebug(String message) {
        if (DBG) {
            System.out.println(message);
        }
    }
}
