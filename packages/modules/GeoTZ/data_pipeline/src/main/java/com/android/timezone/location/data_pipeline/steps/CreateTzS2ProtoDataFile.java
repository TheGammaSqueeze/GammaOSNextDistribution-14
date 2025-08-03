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

import com.android.timezone.location.common.LicenseSupport;
import com.android.timezone.location.common.LicenseSupport.License;
import com.android.timezone.location.data_pipeline.steps.Types.ProtoStorageFormat;
import com.android.timezone.location.data_pipeline.steps.Types.TzS2Range;
import com.android.timezone.location.data_pipeline.steps.Types.TzS2Ranges;
import com.android.timezone.location.tools.proto.GeotzProtos;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.converters.FileConverter;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An executable class that takes a {@link TzS2Ranges} files and converts it into a
 * {@link GeotzProtos.TimeZones} text proto needed by the Android tzs2 data file generator.
 * See {@link #main(String[])} for usage.
 */
public final class CreateTzS2ProtoDataFile {

    private final File mInputFile;

    private final ProtoStorageFormat mInputProtoFormat;

    private final File mOutputFile;

    private final ProtoStorageFormat mOutputProtoFormat;

    private CreateTzS2ProtoDataFile(
            File inputFile, ProtoStorageFormat inputProtoFormat, File outputFile,
            ProtoStorageFormat outputProtoFormat) {
        this.mInputFile = Objects.requireNonNull(inputFile);
        this.mInputProtoFormat = Objects.requireNonNull(inputProtoFormat);
        this.mOutputFile = Objects.requireNonNull(outputFile);
        this.mOutputProtoFormat = Objects.requireNonNull(outputProtoFormat);
    }

    private static class Arguments {
        @Parameter(names = "--input-file",
                description = "The input TzS2Ranges file to parse",
                required = true,
                converter = FileConverter.class)
        public File inputFile;

        @Parameter(names = "--output-file",
                description = "The output file to produce",
                required = true,
                converter = FileConverter.class)
        public File outputFile;
    }

    /**
     * See {@link CreateTzS2ProtoDataFile} for the purpose of this class.
     *
     * <p>Arguments:
     * <ol>
     *     <li>The input {@link TzS2Ranges} file to parse</li>
     *     <li>The output file to produce</li>
     * </ol>
     */
    public static void main(String[] args) throws Exception {
        Arguments arguments = new Arguments();
        JCommander.newBuilder()
                .addObject(arguments)
                .build()
                .parse(args);
        File inputFile = arguments.inputFile;
        ProtoStorageFormat inputStorageFormat = Types.DEFAULT_PROTO_STORAGE_FORMAT;

        File outputFile = arguments.outputFile;
        // This is currently assumed by the tool that reads the file.
        ProtoStorageFormat outputStorageFormat = ProtoStorageFormat.TEXT;

        outputFile.getParentFile().mkdirs();

        CreateTzS2ProtoDataFile converter = new CreateTzS2ProtoDataFile(
                inputFile, inputStorageFormat, outputFile, outputStorageFormat);

        converter.execute();
    }

    private void execute() throws Exception {
        System.out.println("Reading " + mInputFile + " ...");
        TzS2Ranges tzS2Ranges = TzS2Ranges.load(mInputFile, mInputProtoFormat);
        System.out.println("Converting...");
        GeotzProtos.TimeZones tzS2DataInput = createTimeZonesProto(tzS2Ranges);
        LicenseSupport.copyLicenseFile(mInputFile.getParentFile(), mOutputFile.getParentFile());
        System.out.println("Writing " + mOutputFile + " ...");
        mOutputProtoFormat.store(tzS2DataInput, mOutputFile, License.ODBL);
    }

    private static GeotzProtos.TimeZones createTimeZonesProto(TzS2Ranges ranges) {
        AtomicInteger nextId = new AtomicInteger(0);
        // TODO Switch to bimap.
        Map<List<String>, Integer> idBySet = new HashMap<>();
        Map<Integer, List<String>> setById = new HashMap<>();
        GeotzProtos.TimeZones.Builder builder = GeotzProtos.TimeZones.newBuilder();
        for (TzS2Range range : ranges.getAll()) {
            int id = idBySet.computeIfAbsent(range.tzIds, x -> nextId.getAndIncrement());
            setById.put(id, range.tzIds);

            builder.addRanges(GeotzProtos.S2Range.newBuilder()
                    .setStartCellId(range.rangeStart.id())
                    .setEndCellId(range.rangeEnd.id())
                    .setTimeZoneIdSetIndex(id)
                    .build());
        }

        int totalIds = setById.size();
        for (int i = 0; i < totalIds; i++) {
            GeotzProtos.TimeZoneIdSet tzIdSet = GeotzProtos.TimeZoneIdSet.newBuilder()
                    .addAllTimeZoneIds(setById.get(i))
                    .build();
            builder.addTimeZoneIdSets(tzIdSet);
        }
        return builder.build();
    }
}
