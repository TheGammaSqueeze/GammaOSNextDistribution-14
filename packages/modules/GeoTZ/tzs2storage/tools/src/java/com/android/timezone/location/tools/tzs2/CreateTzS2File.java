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

package com.android.timezone.location.tools.tzs2;

import com.android.timezone.location.common.LicenseSupport;
import com.android.timezone.location.storage.tzs2range.TzS2Range;
import com.android.timezone.location.storage.tzs2range.TzS2RangeFileFormat;
import com.android.timezone.location.storage.tzs2range.write.TzS2RangeFileWriter;
import com.android.timezone.location.tools.proto.GeotzProtos;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.converters.FileConverter;
import com.google.protobuf.TextFormat;

import java.io.File;
import java.io.FileReader;
import java.util.Iterator;
import java.util.List;

/** Creates a TZ S2 file from a text proto file. */
public final class CreateTzS2File {


    private static class Arguments {

        @Parameter(names = "--input-file",
                description = "Proto file",
                required = true,
                converter = FileConverter.class)
        public File inputFile;

        @Parameter(names = "--s2-level",
                description = "s2 level of input data",
                required = true)
        public int s2Level;

        @Parameter(names = "--output-file",
                description = "tz s2 file",
                required = true,
                converter = FileConverter.class)
        public File outputFile;

    }

    /**
     * Usage:
     * CreateTzS2File <[input] proto file> <[input] s2 level of input data> <[output] tz s2 file>
     *
     * The proto file is defined in geotz_protos.proto. The data must be ordered correctly.
     */
    public static void main(String[] args) throws Exception {
        Arguments arguments = new Arguments();
        JCommander.newBuilder()
                .addObject(arguments)
                .build()
                .parse(args);
        File inputFile = arguments.inputFile;
        int s2Level = arguments.s2Level;
        File outputFile = arguments.outputFile;

        // The input file is expected to be associated with a LICENSE file. Copy it to the output
        // directory.
        LicenseSupport.copyLicenseFile(inputFile.getParentFile(), outputFile.getParentFile());

        GeotzProtos.TimeZones timeZonesInput;
        try (FileReader reader = new FileReader(inputFile)) {
            GeotzProtos.TimeZones.Builder builder = GeotzProtos.TimeZones.newBuilder();
            TextFormat.getParser().merge(reader, builder);
            timeZonesInput = builder.build();
        }

        TzS2RangeFileFormat fileFormat = FileFormats.getFileFormatForLevel(s2Level);
        try (TzS2RangeFileWriter writer = TzS2RangeFileWriter.open(outputFile, fileFormat)) {
            List<GeotzProtos.TimeZoneIdSet> timeZoneIdSets = timeZonesInput.getTimeZoneIdSetsList();
            Iterator<TzS2Range> tzS2RangeIterator = timeZonesInput.getRangesList()
                    .stream()
                    .map(x -> createTzS2Range(timeZoneIdSets, x))
                    .iterator();
            writer.processRanges(tzS2RangeIterator);
        }
    }

    private static TzS2Range createTzS2Range(List<GeotzProtos.TimeZoneIdSet> tzIdStrings,
            GeotzProtos.S2Range inputRange) {
        GeotzProtos.TimeZoneIdSet timeZoneIdSet =
                tzIdStrings.get(inputRange.getTimeZoneIdSetIndex());
        List<String> rangeStrings = timeZoneIdSet.getTimeZoneIdsList();
        return new TzS2Range(inputRange.getStartCellId(), inputRange.getEndCellId(), rangeStrings);
    }
}
