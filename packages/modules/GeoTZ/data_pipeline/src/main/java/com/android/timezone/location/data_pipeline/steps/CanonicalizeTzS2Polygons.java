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

package com.android.timezone.location.data_pipeline.steps;

import com.android.libcore.timezone.util.Errors;
import com.android.timezone.location.common.LicenseSupport;
import com.android.timezone.location.data_pipeline.steps.Types.ProtoStorageFormat;
import com.android.timezone.location.data_pipeline.steps.Types.TzS2Polygons;
import com.android.timezone.tzids.TimeZoneIds;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.converters.FileConverter;
import com.google.common.geometry.S2Polygon;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * An executable class that takes a directory of {@link TzS2Polygons} files and maps time zone IDs
 * to the ones preferred on Android, combining as necessary. The resulting {@link TzS2Polygons} are
 * written to a new directory. See {@link #main(String[])} for usage.
 */
public class CanonicalizeTzS2Polygons {

    /**
     * The time zone IDs referenced in the input data that are expected and allowed to not be mapped
     * to a country code.
     *
     * <p>Normally, the Android team wants time zone IDs that we have mapped to countries because
     * the stock manual tz picker in Settings is region-based and uses that mapping to work back to
     * the country from the current device time zone. Use of other zones will either leave the
     * picker without a region selected, or (if the zone is one of the recognized "fixed offset"
     * zones) the picker will be put into "fixed offset" mode.
     */
    private static final Set<String> COUNTRY_MAPPED_EXCEPTIONS;
    static {
        COUNTRY_MAPPED_EXCEPTIONS = new HashSet<>();
        // Used in timezone-boundary-builder for a zone in Antarctica: with such a small population
        // we don't worry about users in one Antarctic base getting slightly different behavior in
        // the manual time zone picker in settings.
        // If Etc/UTC becomes more widely used in source data then it should be stripped out at an
        // earlier step (rather than mapping Etc/UTC to a country in tzlookup.xml / tzids.prototxt,
        // which would probably break various assumptions elsewhere).
        COUNTRY_MAPPED_EXCEPTIONS.add("Etc/UTC");
    }

    private final File mInputDir;

    private final File mTzIdsFile;

    private final Instant mReplacementThreshold;

    private final File mOutputDir;

    private final ProtoStorageFormat mProtoFormat;

    private CanonicalizeTzS2Polygons(
            File inputDir, File tzIdsFile, Instant replacementThreshold,
            File outputDir, ProtoStorageFormat protoFormat) {
        mInputDir = Objects.requireNonNull(inputDir);
        mTzIdsFile = Objects.requireNonNull(tzIdsFile);
        mReplacementThreshold = replacementThreshold;
        mOutputDir = Objects.requireNonNull(outputDir);
        mProtoFormat = Objects.requireNonNull(protoFormat);
    }

    private static class Arguments {

        @Parameter(names = "--input",
                description = "The input directory containing TzS2Polygons files",
                required = true,
                converter = FileConverter.class)
        public File inputDir;

        @Parameter(names = "--tz-ids",
                description = "The input TzIds prototz file",
                required = true,
                converter = FileConverter.class)
        public File tzIdsFile;

        @Parameter(names = "--replacement-threshold",
                description = "The ISO 8601 format date/time to use when generating time zone"
                        + " ID replacements",
                required = true)
        public String replacementThreshold;

        @Parameter(names = "--output",
                description = "The output dir to write TzS2Polygons files to",
                required = true,
                converter = FileConverter.class)
        public File outputDir;

        Instant replacementThreshold() {
            return Instant.parse(replacementThreshold);
        }
    }

    /**
     * See {@link CanonicalizeTzS2Polygons} for the purpose of this class.
     */
    public static void main(String[] args) throws Exception {
        Arguments arguments = new Arguments();
        JCommander.newBuilder()
                .addObject(arguments)
                .build()
                .parse(args);
        File inputDir = arguments.inputDir;
        File tzIdsFile = arguments.tzIdsFile;
        Instant replacementThreshold = arguments.replacementThreshold();
        File outputDir = arguments.outputDir;
        ProtoStorageFormat storageFormat = Types.DEFAULT_PROTO_STORAGE_FORMAT;

        outputDir.getParentFile().mkdirs();

        CanonicalizeTzS2Polygons converter = new CanonicalizeTzS2Polygons(
                inputDir, tzIdsFile, replacementThreshold, outputDir, storageFormat);

        converter.execute();
    }

    private void execute() throws Exception {
        Errors errors = new Errors();
        try {
            LicenseSupport.copyLicenseFile(mInputDir, mOutputDir);

            TimeZoneIds timeZoneIds = TimeZoneIds.load(mTzIdsFile);

            List<File> inputFiles = TzS2Polygons.listFiles(mInputDir, mProtoFormat);
            for (File inputFile : inputFiles) {
                errors.pushScope(inputFile.getName());
                processFile(timeZoneIds, inputFile, errors);
                errors.popScope();
            }
        } finally {
            System.err.println("Errors encountered:");
            System.err.println(errors.asString());
            errors.throwIfError("Errors encountered.");
        }
        System.out.println("Stage completed successfully");
    }

    private void processFile(TimeZoneIds timeZoneIds, File inputFile, Errors errors)
            throws IOException {
        System.out.println("Reading " + inputFile);

        TzS2Polygons inputTzS2Polygons = TzS2Polygons.load(inputFile, mProtoFormat);

        String timeZoneId = inputTzS2Polygons.tzId;
        String canonicalTzId;
        if (COUNTRY_MAPPED_EXCEPTIONS.contains(timeZoneId)) {
            canonicalTzId = timeZoneId;
        } else {
            String countryIsoCode = timeZoneIds.getCountryCodeForZoneId(timeZoneId);
            if (countryIsoCode == null) {
                errors.addError(timeZoneId + " is not mapped to a country");
                // Skip processing this file: the error will be reported later.
                return;
            }

            // We cannot tell from TzS2Polygons which country the source data expected the zone to
            // to be mapped to, so unfortunately  we cannot confirm countryIsoCode that Android maps
            // the zone ID to is "correct". It seems unlikely that it would be wildly wrong.
            // The worst that will happen if the geo boundary for a zone ID spans countries (or is
            // simply for the "wrong" country) is that the region picker on Android will not select
            // the region expected by the user (assuming the geo boundary and the mapping to a zone
            // ID is otherwise correct).
            Map<String, String> countryTzIdsMap =
                    timeZoneIds.getCountryIdMap(countryIsoCode, mReplacementThreshold);
            canonicalTzId = countryTzIdsMap.get(timeZoneId);
        }

        String fileSuffix = TzS2Polygons.getFileSuffix(mProtoFormat);
        File outputFile = TzIds.createFile(mOutputDir, canonicalTzId, fileSuffix);
        TzS2Polygons tzS2Polygons;
        if (outputFile.exists()) {
            System.out.println("Extending " + outputFile);
            TzS2Polygons oldOutputPolygons = TzS2Polygons.load(outputFile, mProtoFormat);
            List<S2Polygon> combinedS2Polygons =
                    new ArrayList<>(oldOutputPolygons.s2PolygonList);
            combinedS2Polygons.addAll(inputTzS2Polygons.s2PolygonList);
            tzS2Polygons = new TzS2Polygons(canonicalTzId, combinedS2Polygons);
        } else {
            System.out.println("Creating " + outputFile);
            tzS2Polygons = new TzS2Polygons(canonicalTzId, inputTzS2Polygons.s2PolygonList);
        }
        TzS2Polygons.store(tzS2Polygons, outputFile, mProtoFormat, LicenseSupport.License.ODBL);
    }
}
