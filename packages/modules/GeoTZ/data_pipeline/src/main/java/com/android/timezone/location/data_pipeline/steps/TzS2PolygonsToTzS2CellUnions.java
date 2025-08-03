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
import com.android.timezone.location.data_pipeline.steps.Types.TzS2Polygons;
import com.android.timezone.location.data_pipeline.util.NamedFuture;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.converters.FileConverter;
import com.google.common.base.Stopwatch;
import com.google.common.geometry.S2CellId;
import com.google.common.geometry.S2CellUnion;
import com.google.common.geometry.S2Polygon;
import com.google.common.geometry.S2RegionCoverer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
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
 * An executable class that takes a set of {@link TzS2Polygons} files and produces
 * {@link TzS2CellUnion} files, one per input file. See {@link #main(String[])} for usage.
 */
public final class TzS2PolygonsToTzS2CellUnions {

    private static final int MAX_CELL_UNION_CELLS = 1000000;

    private final File mInputDir;

    private final ExecutorService mExecutorService;

    private final File mOutputDir;

    private final int mMaxS2Level;

    private final ProtoStorageFormat mProtoStorageFormat;

    private TzS2PolygonsToTzS2CellUnions(
            File inputDir, ExecutorService executorService, File outputDir, int maxS2Level,
            ProtoStorageFormat protoStorageFormat) {
        this.mInputDir = Objects.requireNonNull(inputDir);
        this.mExecutorService = Objects.requireNonNull(executorService);
        this.mOutputDir = Objects.requireNonNull(outputDir);
        if (maxS2Level < 1 || maxS2Level > S2CellId.MAX_LEVEL) {
            throw new IllegalArgumentException("Bad S2 level=" + maxS2Level);
        }
        this.mMaxS2Level = maxS2Level;
        this.mProtoStorageFormat = Objects.requireNonNull(protoStorageFormat);
    }

    private static class Arguments {
        @Parameter(names = "--input",
                description = "The input directory containing the TzS2Polygons files",
                required = true,
                converter = FileConverter.class)
        public File inputDir;

        @Parameter(names = "--num-threads",
                description = "The number of threads to use",
                required = true)
        public int numThreads;

        @Parameter(names = "--output",
                description = "The output directory to store the TzS2CellUnion files",
                required = true,
                converter = FileConverter.class)
        public File outputDir;

        @Parameter(names = "--max-s2-level",
                description = "The maximum S2 level of the cells to include in the S2 cell unions",
                required = true)
        public int maxS2Level;
    }

    /**
     * See {@link TzS2PolygonsToTzS2CellUnions} for the purpose of this class.
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
        int maxS2Level = arguments.maxS2Level;
        ProtoStorageFormat protoStorageFormat = Types.DEFAULT_PROTO_STORAGE_FORMAT;

        outputDir.mkdirs();

        ExecutorService executorService = Executors.newFixedThreadPool(threads);
        TzS2PolygonsToTzS2CellUnions converter =
                new TzS2PolygonsToTzS2CellUnions(inputDir, executorService, outputDir, maxS2Level,
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

        List<File> inputFiles = TzS2Polygons.listFiles(mInputDir, mProtoStorageFormat);
        List<NamedFuture<Pair<String, File>>> futures = new ArrayList<>();
        for (File file : inputFiles) {
            Future<Pair<String, File>> future =
                    mExecutorService.submit(() -> processFile(file, mMaxS2Level, mOutputDir));
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

    private Pair<String, File> processFile(
            File tzS2PolygonFile, int maxS2Level, File outputDir) throws IOException {
        TzS2Polygons tzS2Polygons = TzS2Polygons.load(tzS2PolygonFile, mProtoStorageFormat);
        TzS2CellUnion tzS2CellUnion = createTzS2CellUnion(tzS2Polygons, maxS2Level);
        File outputFile = TzIds.createFile(
                outputDir, tzS2CellUnion.tzId, TzS2CellUnion.getFileSuffix(mProtoStorageFormat));
        TzS2CellUnion.store(tzS2CellUnion, outputFile, mProtoStorageFormat, License.ODBL);
        return new Pair<>(tzS2Polygons.tzId, outputFile);
    }

    private static TzS2CellUnion createTzS2CellUnion(TzS2Polygons tzPolygons, int maxS2Level) {
        Stopwatch stopwatch = Stopwatch.createStarted();

        S2RegionCoverer s2RegionCovererQuad = new S2RegionCoverer();
        s2RegionCovererQuad.setMinLevel(1);
        s2RegionCovererQuad.setMaxLevel(maxS2Level);
        s2RegionCovererQuad.setMaxCells(MAX_CELL_UNION_CELLS);

        List<S2Polygon> s2Polygons = tzPolygons.s2PolygonList;
        String tzId = tzPolygons.tzId;
        ArrayList<S2CellId> cellIds = new ArrayList<>();
        for (S2Polygon s2Polygon : s2Polygons) {
            S2CellUnion covering = s2RegionCovererQuad.getCovering(s2Polygon);
            cellIds.addAll(covering.cellIds());
        }
        if (cellIds.size() >= (MAX_CELL_UNION_CELLS * 95) / 100) {
            System.err.println(
                    "Possible overflow. for " + tzPolygons.tzId + "size=" + cellIds.size() + "...");
            throw new IllegalStateException();
        }
        S2CellUnion combinedCellUnion = new S2CellUnion();
        combinedCellUnion.initFromCellIds(cellIds);
        System.out.printf("Created S2CellUnion for %s containing %s cells at level %S in %s...\n",
                tzId, cellIds.size(), maxS2Level, stopwatch.elapsed());
        return new TzS2CellUnion(tzId, combinedCellUnion);
    }
}
