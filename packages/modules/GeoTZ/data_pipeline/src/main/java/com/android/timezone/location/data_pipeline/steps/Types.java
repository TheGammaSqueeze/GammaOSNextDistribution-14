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

import static java.util.stream.Collectors.toList;

import com.android.timezone.location.common.LicenseSupport.License;
import com.android.timezone.location.data_pipeline.steps.proto.S2Protos;

import com.google.common.geometry.S2CellId;
import com.google.common.geometry.S2CellUnion;
import com.google.common.geometry.S2Loop;
import com.google.common.geometry.S2Point;
import com.google.common.geometry.S2Polygon;
import com.google.protobuf.Message;
import com.google.protobuf.TextFormat;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** A set of shared types for use in the reference time zone geolocation data pipeline. */
public final class Types {

    public static final ProtoStorageFormat DEFAULT_PROTO_STORAGE_FORMAT = ProtoStorageFormat.TEXT;

    private Types() {
    }

    /** The two proto storage formats supported. */
    public enum ProtoStorageFormat {
        TEXT(".prototxt"),
        BINARY(".proto");

        private final String mSuffix;

        ProtoStorageFormat(String suffix) {
            this.mSuffix = suffix;
        }

        /**
         * Stores {@code message} in {@code outputFile} in this format. Also checks for a LICENSE
         * file and adds a header to the file if possible.
         */
        public void store(Message message, File outputFile, License license) throws IOException {
            // Require the LICENSE file to be present.
            license.checkLicensePresentInDir(outputFile.getParentFile());

            if (this == TEXT) {
                storeProtoAsText(message, outputFile, license);
            } else if (this == BINARY) {
                storeProtoAsBinary(message, outputFile);
            } else {
                throw new IllegalArgumentException();
            }
        }

        /**
         * Loads a proto in this format from {@code inputFile} by populating the supplied
         * {@code builder}.
         */
        public void load(Message.Builder builder, File inputFile) throws IOException {
            if (this == TEXT) {
                loadProtoAsText(builder, inputFile);
            } else if (this == BINARY) {
                loadProtoAsBinary(builder, inputFile);
            } else {
                throw new IllegalArgumentException();
            }
        }

        /** Returns the type suffix for this storage format. */
        public String getTypeSuffix() {
            return mSuffix;
        }

        private static void loadProtoAsText(Message.Builder builder, File inputFile)
                throws IOException {
            try (FileReader reader = new FileReader(inputFile)) {
                TextFormat.getParser().merge(reader, builder);
            }
        }

        private static void loadProtoAsBinary(Message.Builder builder, File inputFile)
                throws IOException {
            try (FileInputStream inputStream = new FileInputStream(inputFile)) {
                builder.mergeFrom(inputStream);
            }
        }

        private static void storeProtoAsText(Message message, File outputFile, License license)
                throws IOException {
            try (FileWriter writer = new FileWriter(outputFile)) {
                // Add the license text header.
                writer.append(license.getTextProtoHeader());

                TextFormat.print(message, writer);
            }
        }

        private static void storeProtoAsBinary(Message message, File outputFile)
                throws IOException {
            try (FileOutputStream fileOutputStream = new FileOutputStream(outputFile)) {
                fileOutputStream.write(message.toByteArray());
            }
        }
    }

    /** A basic pair class. */
    static class Pair<A, B> {

        public final A a;

        public final B b;

        Pair(A a, B b) {
            this.a = a;
            this.b = b;
        }
    }

    /** A combination of a time zone ID and a list of {@link S2Polygon} instances. */
    public static class TzS2Polygons {

        private static final String FILE_NAME_CONTENT_IDENTIFIER = "_tzs2polygons";

        public final String tzId;

        public final List<S2Polygon> s2PolygonList;

        /** Creates a new instance. */
        public TzS2Polygons(String tzId, List<S2Polygon> s2PolygonList) {
            this.tzId = Objects.requireNonNull(tzId);

            // Defensive copy of mutable S2 objects.
            this.s2PolygonList = new ArrayList<>();
            s2PolygonList.forEach(x -> this.s2PolygonList.add(new S2Polygon(x)));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            TzS2Polygons that = (TzS2Polygons) o;
            return tzId.equals(that.tzId)
                    && s2PolygonsEquals(s2PolygonList, that.s2PolygonList);
        }

        private static boolean s2PolygonsEquals(List<S2Polygon> one, List<S2Polygon> two) {
            if (one.size() != two.size()) {
                return false;
            }
            for (int polyIndex = 0; polyIndex < one.size(); polyIndex++) {
                S2Polygon onePoly = one.get(polyIndex);
                S2Polygon twoPoly = two.get(polyIndex);
                if (!s2PolygonEquals(onePoly, twoPoly)) {
                    return false;
                }
            }
            return true;
        }

        private static boolean s2PolygonEquals(S2Polygon one, S2Polygon two) {
            if (one.numLoops() != two.numLoops()) {
                return false;
            }

            for (int loopIndex = 0; loopIndex < one.numLoops(); loopIndex++) {
                S2Loop oneLoop = one.loop(loopIndex);
                S2Loop twoLoop = two.loop(loopIndex);
                if (!s2LoopEquals(oneLoop, twoLoop)) {
                    return false;
                }
            }
            return true;
        }

        private static boolean s2LoopEquals(S2Loop one, S2Loop two) {
            if (one.numVertices() != two.numVertices()) {
                return false;
            }

            for (int vertexIndex = 0; vertexIndex < one.numVertices(); vertexIndex++) {
                S2Point onePoint = one.vertex(vertexIndex);
                S2Point twoPoint = two.vertex(vertexIndex);
                if (!onePoint.equals(twoPoint)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int hashCode() {
            return Objects.hash(tzId);
        }

        @Override
        public String toString() {
            return "TzS2Polygons{"
                    + "tzId='" + tzId + '\''
                    + ", s2PolygonList=" + s2PolygonList
                    + '}';
        }

        /**
         * Stores the supplied object in {@code outputFile} in the specified format, adding license
         * information to the header when possible and checking the target directory contains the
         * necessary LICENSE file.
         */
        public static void store(TzS2Polygons tzPolygons, File outputFile,
                ProtoStorageFormat storageFormat, License license) throws IOException {
            S2Protos.TzS2Polygons.Builder s2PolygonsProtoBuilder =
                    S2Protos.TzS2Polygons.newBuilder().setTzId(tzPolygons.tzId);
            for (S2Polygon s2Polygon : tzPolygons.s2PolygonList) {
                S2Protos.S2Polygon.Builder s2PolygonProtoBuilder = S2Protos.S2Polygon.newBuilder();
                for (int loopIndex = 0; loopIndex < s2Polygon.numLoops(); loopIndex++) {
                    S2Loop s2Loop = s2Polygon.loop(loopIndex);
                    S2Protos.S2Loop.Builder s2LoopProtoBuilder = S2Protos.S2Loop.newBuilder();
                    for (int vIndex = 0; vIndex < s2Loop.numVertices(); vIndex++) {
                        S2Point s2Point = s2Loop.vertex(vIndex);
                        S2Protos.S2Point s2PointProto = S2Protos.S2Point.newBuilder()
                                .setX(s2Point.get(0))
                                .setY(s2Point.get(1))
                                .setZ(s2Point.get(2))
                                .build();
                        s2LoopProtoBuilder.addVertices(s2PointProto);
                    }
                    s2PolygonProtoBuilder.addLoops(s2LoopProtoBuilder.build());
                }
                s2PolygonsProtoBuilder.addPolygons(s2PolygonProtoBuilder.build());
            }
            S2Protos.TzS2Polygons s2PolygonsProto = s2PolygonsProtoBuilder.build();
            storageFormat.store(s2PolygonsProto, outputFile, license);
        }

        /**
         * Lists the files that have the file suffix returned by
         * {@link #getFileSuffix(ProtoStorageFormat)} in the specified directory.
         */
        public static List<File> listFiles(File dir, ProtoStorageFormat storageFormat) {
            String fileSuffix = getFileSuffix(storageFormat);
            FileFilter fileFilter = x -> x.getName().endsWith(fileSuffix);
            return Types.listFiles(dir, fileFilter);
        }

        /** Returns a standard suffix for this type in the specified file format. */
        public static String getFileSuffix(ProtoStorageFormat storageFormat) {
            return TzS2Polygons.FILE_NAME_CONTENT_IDENTIFIER + storageFormat.getTypeSuffix();
        }

        /** Loads the {@code inputFile} in the specified format as a {@link TzS2Polygons}. */
        public static TzS2Polygons load(File inputFile, ProtoStorageFormat storageFormat)
                throws IOException {
            S2Protos.TzS2Polygons.Builder builder = S2Protos.TzS2Polygons.newBuilder();
            storageFormat.load(builder, inputFile);
            S2Protos.TzS2Polygons tzS2PolygonsProto = builder.build();

            List<S2Polygon> s2Polygons = new ArrayList<>();
            for (S2Protos.S2Polygon s2PolygonProto : tzS2PolygonsProto.getPolygonsList()) {
                List<S2Loop> s2Loops = new ArrayList<>();
                for (S2Protos.S2Loop s2LoopProto : s2PolygonProto.getLoopsList()) {
                    List<S2Point> vertices = new ArrayList<>();
                    for (S2Protos.S2Point s2PointProto : s2LoopProto.getVerticesList()) {
                        S2Point s2Point = new S2Point(
                                s2PointProto.getX(),
                                s2PointProto.getY(),
                                s2PointProto.getZ());
                        vertices.add(s2Point);
                    }
                    S2Loop s2Loop = new S2Loop(vertices);
                    s2Loops.add(s2Loop);
                }
                S2Polygon s2Polygon = new S2Polygon(s2Loops);
                s2Polygons.add(s2Polygon);
            }
            return new TzS2Polygons(tzS2PolygonsProto.getTzId(), s2Polygons);
        }
    }

    /** A combination of a time zone ID and a {@link S2CellUnion}. */
    public static class TzS2CellUnion {

        private static final String FILE_NAME_CONTENT_IDENTIFIER = "_tzs2cellunion";

        public final String tzId;

        public final S2CellUnion s2CellUnion;

        /** Creates a new instance. */
        public TzS2CellUnion(String tzId, S2CellUnion s2CellUnion) {
            this.tzId = Objects.requireNonNull(tzId);
            this.s2CellUnion = (S2CellUnion) s2CellUnion.clone();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            TzS2CellUnion that = (TzS2CellUnion) o;
            return tzId.equals(that.tzId)
                    && s2CellUnionEquals(this.s2CellUnion, that.s2CellUnion);
        }

        private static boolean s2CellUnionEquals(S2CellUnion one, S2CellUnion two) {
            return one.cellIds().equals(two.cellIds());
        }

        @Override
        public int hashCode() {
            return Objects.hash(tzId);
        }

        @Override
        public String toString() {
            return "TzS2CellUnion{"
                    + "tzId='" + tzId + '\''
                    + ", s2CellUnion=" + s2CellUnion
                    + '}';
        }

        /**
         * Stores the supplied object in {@code outputFile} in the specified format, adding license
         * information to the header when possible and checking the target directory contains the
         * necessary LICENSE file.
         */
        public static void store(TzS2CellUnion tzS2CellUnion, File outputFile,
                ProtoStorageFormat storageFormat, License license) throws IOException {
            ArrayList<S2CellId> s2CellIds = tzS2CellUnion.s2CellUnion.cellIds();
            List<Long> cellIds = s2CellIds.stream().map(S2CellId::id).collect(toList());
            S2Protos.TzS2CellUnion message = S2Protos.TzS2CellUnion.newBuilder()
                    .setTzId(tzS2CellUnion.tzId)
                    .addAllCellIds(cellIds)
                    .build();
            storageFormat.store(message, outputFile, license);
        }

        /** Loads the {@code inputFile} in the specified format as a {@link TzS2CellUnion}. */
        public static TzS2CellUnion load(File file, ProtoStorageFormat storageFormat) {
            S2Protos.TzS2CellUnion.Builder builder = S2Protos.TzS2CellUnion.newBuilder();
            try {
                storageFormat.load(builder, file);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            S2CellUnion s2CellUnion = new S2CellUnion();
            List<S2CellId> cellIds =
                    builder.getCellIdsList().stream().map(S2CellId::new).collect(toList());
            s2CellUnion.initFromCellIds(new ArrayList<>(cellIds));
            return new TzS2CellUnion(builder.getTzId(), s2CellUnion);
        }

        /**
         * Lists the files that have the file suffix returned by
         * {@link #getFileSuffix(ProtoStorageFormat)} in the specified directory.
         */
        public static List<File> listFiles(File dir, ProtoStorageFormat storageFormat) {
            String fileSuffix = getFileSuffix(storageFormat);
            FileFilter fileFilter = x -> x.getName().endsWith(fileSuffix);
            return Types.listFiles(dir, fileFilter);
        }

        /** Returns a standard suffix for this type in the specified file format. */
        public static String getFileSuffix(ProtoStorageFormat storageFormat) {
            return FILE_NAME_CONTENT_IDENTIFIER + storageFormat.getTypeSuffix();
        }
    }

    /** A <em>mutable</em> container for a list of {@link TzS2Range} instances. */
    public static class TzS2Ranges {

        private static final String FILE_NAME_CONTENT_IDENTIFIER = "_tzs2ranges";

        private final List<TzS2Range> mValues;

        public TzS2Ranges(List<TzS2Range> values) {
            this.mValues = new ArrayList<>(values);
        }

        /**
         * Replaces the {@link TzS2Range} with the specified index with a new range. Returns the
         * old range at the index.
         */
        public TzS2Range replace(int i, TzS2Range newRange) {
            return mValues.set(i, newRange);
        }

        /** Returns the {@link TzS2Range} at the specified index. */
        public TzS2Range get(int i) {
            return mValues.get(i);
        }

        /** Returns the number of {@link TzS2Range} instances. */
        public int size() {
            return mValues.size();
        }

        /** Returns (a copy of) the list of {@link TzS2Range} instances held. */
        public List<TzS2Range> getAll() {
            return new ArrayList<>(mValues);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            TzS2Ranges that = (TzS2Ranges) o;
            return mValues.equals(that.mValues);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mValues);
        }

        @Override
        public String toString() {
            return "TzS2Ranges{"
                    + "values=" + mValues
                    + '}';
        }

        /** Loads the {@code inputFile} in the specified format as a {@link TzS2Ranges}. */
        public static TzS2Ranges load(File inputFile, ProtoStorageFormat storageFormat) {
            S2Protos.TzS2Ranges.Builder builder = S2Protos.TzS2Ranges.newBuilder();
            try {
                storageFormat.load(builder, inputFile);
            } catch (IOException e) {
                throw new RuntimeException("Failure while reading " + inputFile, e);
            }

            List<TzS2Range> values = builder.getRangesList().stream()
                    .map(x -> new TzS2Range(
                            x.getValuesList(),
                            new S2CellId(x.getStartCellId()),
                            new S2CellId(x.getEndCellId())))
                    .collect(toList());
            return new TzS2Ranges(values);
        }

        /**
         * Stores the supplied object in {@code outputFile} in the specified format, adding license
         * information to the header when possible and checking the target directory contains the
         * necessary LICENSE file.
         */
        public static void store(TzS2Ranges ranges, File outputFile,
                ProtoStorageFormat storageFormat, License license) throws IOException {
            List<S2Protos.TzS2Range> rangeProtos = ranges.mValues.stream()
                    .map(TzS2Range::createTzS2RangeProto)
                    .collect(toList());
            S2Protos.TzS2Ranges message = S2Protos.TzS2Ranges.newBuilder()
                    .addAllRanges(rangeProtos)
                    .build();
            storageFormat.store(message, outputFile, license);
        }

        /**
         * Lists the files that have the file suffix returned by
         * {@link #getFileSuffix(ProtoStorageFormat)} in the specified directory.
         */
        public static List<File> listFiles(File dir, ProtoStorageFormat storageFormat) {
            FileFilter fileFilter = x -> x.getName().endsWith(getFileSuffix(storageFormat));
            return Types.listFiles(dir, fileFilter);
        }

        /** Returns a standard suffix for this type in the specified file format. */
        public static String getFileSuffix(ProtoStorageFormat storageFormat) {
            return FILE_NAME_CONTENT_IDENTIFIER + storageFormat.getTypeSuffix();
        }
    }

    /**
     * A range of S2 cell IDs at a fixed S2 level associated with a list of time zone IDs. The range
     * is expressed as a start cell ID (inclusive) and an end cell ID (exclusive).
     */
    public static class TzS2Range {

        public final List<String> tzIds;

        public final S2CellId rangeStart;

        public final S2CellId rangeEnd;

        /**
         * Creates an instance. If the range is invalid or the cell IDs are from different levels
         * this method throws an {@link IllegalArgumentException}.
         */
        public TzS2Range(List<String> tzIds, S2CellId rangeStart, S2CellId rangeEnd) {
            this.tzIds = new ArrayList<>(tzIds);
            this.rangeStart = Objects.requireNonNull(rangeStart);
            this.rangeEnd = Objects.requireNonNull(rangeEnd);
            if (rangeStart.level() != rangeEnd.level()) {
                throw new IllegalArgumentException(
                        "Levels differ: rangeStart=" + rangeStart + ", rangeEnd=" + rangeEnd);
            }
            if (rangeStart.greaterOrEquals(rangeEnd)) {
                throw new IllegalArgumentException(
                        "Range start (" + rangeStart + " >= range end (" + rangeEnd + ")");
            }
        }

        private static S2Protos.TzS2Range createTzS2RangeProto(TzS2Range x) {
            return S2Protos.TzS2Range.newBuilder()
                    .setStartCellId(x.rangeStart.id())
                    .setEndCellId(x.rangeEnd.id())
                    .addAllValues(x.tzIds)
                    .build();
        }

        /**
         * Returns the number of cells in this range.
         */
        public long size() {
            // Convert into unsigned values.
            final long rangeStartVal = rangeStart.id() >>> 1;
            final long rangeEndVal = rangeEnd.id() >>> 1;
            // Since we shifted the above values right, we need not shift the lowestOnBit left to
            // obtain the difference between adjacent S2 cells at the given S2 level.
            final long increment = rangeStart.lowestOnBit();
            return (rangeEndVal - rangeStartVal) / increment;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            TzS2Range tzS2Range = (TzS2Range) o;
            return tzIds.equals(tzS2Range.tzIds)
                    && rangeStart.equals(tzS2Range.rangeStart)
                    && rangeEnd.equals(tzS2Range.rangeEnd);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tzIds, rangeStart, rangeEnd);
        }

        @Override
        public String toString() {
            return "TzS2Range{"
                    + " value=" + tzIds
                    + ", rangeStart=" + rangeStart
                    + ", rangeEnd=" + rangeEnd
                    + '}';
        }
    }

    /** Returns a list of files that match the supplied {@link FileFilter}. */
    private static List<File> listFiles(File dir, FileFilter fileFilter) {
        File[] files = dir.listFiles(fileFilter);
        if (files == null) {
            return Collections.emptyList();
        }

        // Sort the results as listFiles() isn't deterministic, which can lead to variations
        // in output that (for example) cause tests to break occasionally.
        Arrays.sort(files);

        return Arrays.asList(files);
    }
}
