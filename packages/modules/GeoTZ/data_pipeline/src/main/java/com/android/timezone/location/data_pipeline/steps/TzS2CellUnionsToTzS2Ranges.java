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
import com.android.timezone.location.data_pipeline.steps.Types.Pair;
import com.android.timezone.location.data_pipeline.steps.Types.ProtoStorageFormat;
import com.android.timezone.location.data_pipeline.steps.Types.TzS2CellUnion;
import com.android.timezone.location.data_pipeline.steps.Types.TzS2Range;
import com.android.timezone.location.data_pipeline.steps.Types.TzS2Ranges;
import com.android.timezone.location.data_pipeline.util.NamedFuture;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.converters.FileConverter;
import com.google.common.base.Stopwatch;
import com.google.common.geometry.S2CellId;
import com.google.common.geometry.S2CellUnion;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * An executable class that takes a set of {@link TzS2CellUnion} files and produces
 * {@link TzS2Ranges} files, one per input file. See {@link #main(String[])} for usage.
 */
public final class TzS2CellUnionsToTzS2Ranges {

    private final File mInputDir;

    private final ExecutorService mExecutorService;

    private final File mOutputDir;

    private final int mS2Level;

    private final ProtoStorageFormat mProtoStorageFormat;

    private TzS2CellUnionsToTzS2Ranges(
            File inputDir, ExecutorService executorService, File outputDir, int s2Level,
            ProtoStorageFormat protoStorageFormat) {
        this.mInputDir = Objects.requireNonNull(inputDir);
        this.mExecutorService = Objects.requireNonNull(executorService);
        this.mOutputDir = Objects.requireNonNull(outputDir);
        if (s2Level < 1 || s2Level > S2CellId.MAX_LEVEL) {
            throw new IllegalArgumentException("Bad S2 level=" + s2Level);
        }
        this.mS2Level = s2Level;
        this.mProtoStorageFormat = Objects.requireNonNull(protoStorageFormat);
    }

    private static class Arguments {
        @Parameter(names = "--input",
                description = "The input directory containing the TzS2CellUnion files",
                required = true,
                converter = FileConverter.class)
        public File inputDir;

        @Parameter(names = "--num-threads",
                description = "The number of threads to use",
                required = true)
        public int numThreads;

        @Parameter(names = "--output",
                description = "The output directory to store the TzS2Ranges files",
                required = true,
                converter = FileConverter.class)
        public File outputDir;

        @Parameter(names = "--s2-level",
                description = "The S2 level of the ranges to produce. The TzS2CellUnion must not "
                        + "contain S2 Cell IDs with a higher level than this",
                required = true)
        public int s2Level;
    }

    /**
     * See {@link TzS2CellUnionsToTzS2Ranges} for the purpose of this class.
     */
    public static void main(String[] args) throws Exception {
        Arguments arguments = new Arguments();
        JCommander.newBuilder()
                .addObject(arguments)
                .build()
                .parse(args);
        File inputDir = arguments.inputDir;
        int threads = arguments.numThreads;
        File outputDir = arguments.outputDir;
        int s2Level = arguments.s2Level;
        ProtoStorageFormat protoStorageFormat = Types.DEFAULT_PROTO_STORAGE_FORMAT;

        outputDir.mkdirs();

        ExecutorService executorService = Executors.newFixedThreadPool(threads);
        TzS2CellUnionsToTzS2Ranges converter =
                new TzS2CellUnionsToTzS2Ranges(inputDir, executorService, outputDir, s2Level,
                        protoStorageFormat);

        try {
            converter.execute();
        } finally {
            System.out.println("Waiting for shutdown");
            executorService.shutdown();
            executorService.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private void execute() throws Exception {
        LicenseSupport.copyLicenseFile(mInputDir, mOutputDir);

        List<File> inputFiles = TzS2CellUnion.listFiles(mInputDir, mProtoStorageFormat);
        List<NamedFuture<Pair<String, File>>> futures = new ArrayList<>();
        for (File file : inputFiles) {
            Future<Pair<String, File>> future =
                    mExecutorService.submit(() -> processFile(file));
            NamedFuture<Pair<String, File>> namedFuture = new NamedFuture<>(file.getName(), future);
            futures.add(namedFuture);
        }

        Set<String> knownTzIds = new HashSet<>();
        for (NamedFuture<Pair<String, File>> future : futures) {
            try {
                if (!future.isDone()) {
                    System.out.println("Waiting for " + future.getName());
                }
                Pair<String, File> output = future.get();
                String tzId = output.a;
                if (knownTzIds.contains(tzId)) {
                    throw new IllegalStateException("Multiple entries found for: " + tzId);
                }
                knownTzIds.add(tzId);
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private Pair<String, File> processFile(File tzS2CellUnionFile) throws IOException {
        TzS2CellUnion tzS2CellUnion = TzS2CellUnion.load(tzS2CellUnionFile, mProtoStorageFormat);
        TzS2Ranges tzS2Ranges = createTzS2Ranges(tzS2CellUnion, mS2Level);

        File outputFile = TzIds.createFile(
                mOutputDir, tzS2CellUnion.tzId, TzS2Ranges.getFileSuffix(mProtoStorageFormat));
        TzS2Ranges.store(tzS2Ranges, outputFile, mProtoStorageFormat, License.ODBL);
        return new Pair<>(tzS2CellUnion.tzId, outputFile);
    }

    private static TzS2Ranges createTzS2Ranges(TzS2CellUnion tzS2CellUnion, int s2Level) {
        Stopwatch stopwatch = Stopwatch.createStarted();

        String tzId = tzS2CellUnion.tzId;
        List<String> tzIds = Collections.singletonList(tzId);
        List<TzS2Range> ranges = new ArrayList<>();

        S2CellUnion s2CellUnion = tzS2CellUnion.s2CellUnion;
        ArrayList<S2CellId> cellIds = s2CellUnion.cellIds();

        if (cellIds.size() > 0) {
            S2CellId rangeStart = null;
            S2CellId rangeEnd = null;
            for (int i = 0; i < cellIds.size(); i++) {
                S2CellId currentS2CellId = cellIds.get(i);
                checkCellIdIsAtLevel(currentS2CellId, s2Level);

                Pair<S2CellId, S2CellId> currentRangeStartAndEnd =
                        createRangeStartAndEnd(currentS2CellId, s2Level);
                S2CellId currentS2CellRangeStart = currentRangeStartAndEnd.a;
                S2CellId currentS2CellRangeEnd = currentRangeStartAndEnd.b;

                if (rangeStart == null) {
                    // First time round the loop initialize rangeStart / rangeEnd only.
                    rangeStart = currentS2CellRangeStart;
                } else if (rangeEnd.id() != currentS2CellRangeStart.id()) {
                    // If there's a gap between cellIds, store the range we have so far and start a
                    // new range.
                    ranges.add(new TzS2Range(tzIds, rangeStart, rangeEnd));
                    rangeStart = currentS2CellRangeStart;
                }
                rangeEnd = currentS2CellRangeEnd;
            }
            ranges.add(new TzS2Range(tzIds, rangeStart, rangeEnd));
        }

        // Sorting the ranges is not necessary. As the input is sorted , it will already be sorted.

        System.out.printf("Created %s S2Ranges for %s in %s...\n",
                ranges.size(), tzId, stopwatch.elapsed());
        return new TzS2Ranges(ranges);
    }

    private static Pair<S2CellId, S2CellId> createRangeStartAndEnd(
            S2CellId s2CellId, int s2Level) {
        S2CellId firstS2CellRangeStart = s2CellId.childBegin(s2Level);
        S2CellId firstS2CellRangeEnd = s2CellId.childEnd(s2Level);

        if (firstS2CellRangeEnd.face() < firstS2CellRangeStart.face()
                || !firstS2CellRangeEnd.isValid()) {
            // Fix this if it becomes an issue.
            throw new IllegalStateException("firstS2CellId=" + s2CellId
                    + ", childEnd(" + s2Level + ") produced an unsupported"
                    + " value=" + firstS2CellRangeEnd);
        }
        return new Pair<>(firstS2CellRangeStart, firstS2CellRangeEnd);
    }

    private static void checkCellIdIsAtLevel(S2CellId cellId, int s2Level) {
        if (cellId.level() > s2Level) {
            throw new IllegalStateException("Bad level for cellId=" + cellId
                    + ". Must be s2Level=" + s2Level + " or lower");
        }
    }
}
