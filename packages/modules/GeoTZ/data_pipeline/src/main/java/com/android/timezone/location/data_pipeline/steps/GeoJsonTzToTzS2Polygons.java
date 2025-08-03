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

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.android.timezone.location.common.LicenseSupport;
import com.android.timezone.location.common.LicenseSupport.License;
import com.android.timezone.location.data_pipeline.steps.Types.ProtoStorageFormat;
import com.android.timezone.location.data_pipeline.steps.Types.TzS2Polygons;
import com.android.timezone.location.data_pipeline.util.NamedFuture;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.converters.FileConverter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;
import com.google.common.geometry.S2LatLng;
import com.google.common.geometry.S2Loop;
import com.google.common.geometry.S2Point;
import com.google.common.geometry.S2Polygon;

import org.geojson.Feature;
import org.geojson.FeatureCollection;
import org.geojson.Geometry;
import org.geojson.LngLatAlt;
import org.geojson.MultiPolygon;
import org.geojson.Polygon;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
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
 * An executable class that takes a geojson file produced by the external/timezone-boundary-builder
 * project and converts all the time zone polygons into {@link Types.TzS2Polygons}, one per time
 * zone ID encountered. The resulting files can be discovered using
 * {@link TzS2Polygons#listFiles(File, ProtoStorageFormat)} and loaded individually using
 * {@link TzS2Polygons#load(File, ProtoStorageFormat)}. See {@link #main(String[])} for usage.
 */
public final class GeoJsonTzToTzS2Polygons {

    private final File mInputFile;

    private final ExecutorService mExecutorService;

    private final File mOutputDir;

    private final ProtoStorageFormat mProtoStorageFormat;

    private final Set<String> mTzIds;

    private GeoJsonTzToTzS2Polygons(
            File inputFile, ExecutorService executorService, File outputDir,
            ProtoStorageFormat protoStorageFormat,
            Set<String> tzIds) {
        this.mInputFile = Objects.requireNonNull(inputFile);
        this.mExecutorService = Objects.requireNonNull(executorService);
        this.mOutputDir = Objects.requireNonNull(outputDir);
        this.mProtoStorageFormat = Objects.requireNonNull(protoStorageFormat);
        this.mTzIds = Objects.requireNonNull(tzIds);
    }

    private static class Arguments {

        @Parameter(names = "--geo-json",
                description = "The input geojson file to parse",
                required = true,
                converter = FileConverter.class)
        public File geoJsonFile;

        @Parameter(names = "--num-threads",
                description = "The number of threads to use",
                required = true)
        public int numThreads;

        @Parameter(names = "--output",
                description = "The output directory",
                required = true,
                converter = FileConverter.class)
        public File outputDir;

        @Parameter(names = "--tz-ids",
                description = "Comma separated list of time zones to build S2Polygons for")
        public String tzIds;

        Set<String> tzIds() {
            return tzIds == null
                    ? ImmutableSet.of()
                    : Arrays.stream(tzIds.split(","))
                            .filter(tzId -> !tzId.isEmpty())
                            .collect(toImmutableSet());
        }
    }

    /**
     * See {@link GeoJsonTzToTzS2Polygons} for the purpose of this class.
     */
    public static void main(String[] args) throws Exception {
        Arguments arguments = new Arguments();
        JCommander.newBuilder()
                .addObject(arguments)
                .build()
                .parse(args);

        File inputFile = arguments.geoJsonFile;
        int threads = arguments.numThreads;
        File outputDir = arguments.outputDir;
        Set<String> tzIds = arguments.tzIds();
        ProtoStorageFormat protoStorageFormat = Types.DEFAULT_PROTO_STORAGE_FORMAT;

        outputDir.mkdirs();

        ExecutorService executorService = Executors.newFixedThreadPool(threads);

        GeoJsonTzToTzS2Polygons converter = new GeoJsonTzToTzS2Polygons(
                inputFile, executorService, outputDir, protoStorageFormat, tzIds);

        try {
            converter.execute();
        } finally {
            System.out.println("Waiting for shutdown");
            executorService.shutdown();
            executorService.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private void execute() throws Exception {
        FeatureCollection featureCollection = loadFeatures(mInputFile);

        LicenseSupport.copyLicenseFile(mInputFile.getParentFile(), mOutputDir);

        if (!mTzIds.isEmpty()) {
            System.out.println("Building polygons for " + mTzIds);
        }

        List<Feature> features = featureCollection.getFeatures();
        List<NamedFuture<TzS2Polygons>> futures = new ArrayList<>(features.size());
        for (Feature feature : features) {
            String tzId = (String) feature.getProperties().get("tzid");
            if (!mTzIds.isEmpty() && !mTzIds.contains(tzId)) {
                continue;
            }
            System.out.println("Submitting " + tzId + " ...");
            Future<TzS2Polygons> future = mExecutorService.submit(() -> processFeature(feature));
            NamedFuture<TzS2Polygons> namedFuture = new NamedFuture<>(tzId, future);
            futures.add(namedFuture);
        }

        Set<String> knownTzIds = new HashSet<>(features.size());
        for (NamedFuture<TzS2Polygons> future : futures) {
            try {
                if (!future.isDone()) {
                    System.out.println("Waiting for " + future.getName());
                }
                TzS2Polygons tzPolygons = future.get();
                String tzId = tzPolygons.tzId;
                if (knownTzIds.contains(tzId)) {
                    throw new IllegalStateException("Multiple entries found for: " + tzId);
                }
                knownTzIds.add(tzId);

                String fileSuffix = TzS2Polygons.getFileSuffix(mProtoStorageFormat);
                File outputFile = TzIds.createFile(mOutputDir, tzPolygons.tzId, fileSuffix);
                TzS2Polygons.store(tzPolygons, outputFile, mProtoStorageFormat, License.ODBL);
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static TzS2Polygons processFeature(Feature feature) {
        String tzId = (String) feature.getProperties().get("tzid");
        System.out.println("Converting " + tzId + " to S2 geometry...");
        Geometry geometry = (Geometry) feature.getGeometry();
        List<Polygon> polygons;
        if (geometry instanceof Polygon) {
            polygons = Collections.singletonList((Polygon) geometry);
        } else if (geometry instanceof MultiPolygon) {
            MultiPolygon multiPolygon = (MultiPolygon) geometry;
            polygons = new ArrayList<>();
            for (List<List<LngLatAlt>> polygonCoordinates : multiPolygon.getCoordinates()) {
                Polygon polygon = new Polygon();
                for (List<LngLatAlt> loop : polygonCoordinates) {
                    polygon.add(loop);
                }
                polygons.add(polygon);
            }
        } else {
            throw new IllegalStateException(geometry.getClass().toString());
        }

        List<S2Polygon> s2PolygonList = new ArrayList<>();
        for (Polygon polygon : polygons) {
            S2Polygon s2Polygon = createS2Polygon(polygon);
            s2PolygonList.add(s2Polygon);
        }
        return new TzS2Polygons(tzId, s2PolygonList);
    }

    private static FeatureCollection loadFeatures(File file) throws IOException {
        FeatureCollection featureCollection;
        final int bufferSize = 256 * 1024;
        try (InputStream inputStream = new BufferedInputStream(
                new FileInputStream(file), bufferSize)) {
            featureCollection = new ObjectMapper().readValue(inputStream, FeatureCollection.class);
            System.out.println("Features read: " + featureCollection.getFeatures().size());
        }
        return featureCollection;
    }

    private static S2Polygon createS2Polygon(Polygon polygon) {
        List<S2Loop> s2Loops = new ArrayList<>();
        for (List<LngLatAlt> loop : polygon.getCoordinates()) {
            S2Loop s2Loop = createS2Loop(loop);
            // There's a problem with geojson in that the order of coordinates is undefined,
            // while S2 requires them in CCW order. normalize() should address this.
            s2Loop.normalize();
            if (!s2Loop.isValid()) {
                throw new IllegalStateException("Invalid loop from polygon:" + polygon);
            }
            s2Loops.add(s2Loop);
        }
        return new S2Polygon(s2Loops);
    }

    private static S2Loop createS2Loop(List<LngLatAlt> loop) {
        List<S2Point> s2Points = new ArrayList<>();
        for (LngLatAlt point : loop) {
            S2LatLng s2LatLng = S2LatLng.fromDegrees(point.getLatitude(), point.getLongitude());
            S2Point s2Point = s2LatLng.toPoint();
            s2Points.add(s2Point);
        }
        // It appears to be normal for geojson to have the first and last point be the same. S2Loop
        // considers this invalid so we remove it.
        if (s2Points.get(0).equals(s2Points.get(s2Points.size() - 1))) {
            s2Points.remove(s2Points.size() - 1);
        } else {
            // If you see this look for other changes in the input format.
            throw new IllegalStateException(
                    "GeoJSON loop did not start and end with the same point");
        }
        return new S2Loop(s2Points);
    }
}
