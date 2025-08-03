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

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import com.android.timezone.location.validation.proto.ValidationProtos;

import com.google.common.geometry.S2CellId;
import com.google.protobuf.Message;
import com.google.protobuf.TextFormat;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Support classes and methods associated with geolocation data comparison / validation. */
class Types {

    private Types() {
    }

    /** A collection of known differences from a KnownDifferences proto file. */
    static class KnownDifferences {

        private final List<KnownDifference> mKnownDifferences;

        private KnownDifferences(List<KnownDifference> knownDifferences) {
            mKnownDifferences = Objects.requireNonNull(knownDifferences);
        }

        /** Creates a {@link KnownDifferences} from a list of {@link KnownDifference} objects. */
        static KnownDifferences create(List<KnownDifference> knownDifferences) {
            return new KnownDifferences(new ArrayList<>(knownDifferences));
        }

        /** Loads a known differences proto txt format file. */
        static KnownDifferences load(File inputFile) throws IOException {
            ValidationProtos.KnownDifferences.Builder builder =
                    ValidationProtos.KnownDifferences.newBuilder();
            try (FileReader reader = new FileReader(inputFile)) {
                TextFormat.getParser().merge(reader, builder);
            }
            ValidationProtos.KnownDifferences knownDifferencesProto = builder.build();

            List<KnownDifference> knownDifferenceList =
                    knownDifferencesProto.getKnownDifferencesList()
                            .stream()
                            .map(KnownDifference::fromProto)
                            .collect(toList());
            return KnownDifferences.create(knownDifferenceList);
        }

        /** Builds a map from the known differences. Each difference is mapped from a {@link
         * TestCaseId} to the {@link KnownDifference} it came from. */
        Map<TestCaseId, KnownDifference> buildIdMap() {
            return mKnownDifferences.stream()
                    .collect(toMap(KnownDifference::getTestCaseId, identity()));
        }

        private ValidationProtos.KnownDifferences toProto() {
            List<ValidationProtos.KnownDifference> knownDifferenceProtos = new ArrayList<>();
            for (KnownDifference knownDifference : mKnownDifferences) {
                knownDifferenceProtos.add(knownDifference.toProto());
            }
            return ValidationProtos.KnownDifferences.newBuilder()
                    .addAllKnownDifferences(knownDifferenceProtos)
                    .build();
        }

        /** Returns the proto txt string form for the known differences. */
        String toProtoText() {
            ValidationProtos.KnownDifferences knownDifferencesProto = toProto();
            return Types.toProtoText(knownDifferencesProto);
        }
    }

    /**
     * An identifier for an individual city test case. It consists of a city's name and its
     * location.
     */
    static class TestCaseId {

        private final String mCityName;

        private final S2CellId mCellId;

        /** Creates a city test case identifier. */
        TestCaseId(String cityName, S2CellId cellId) {
            this.mCityName = Objects.requireNonNull(cityName);
            this.mCellId = Objects.requireNonNull(cellId);
        }

        String getCityName() {
            return mCityName;
        }

        S2CellId getCellId() {
            return mCellId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            TestCaseId that = (TestCaseId) o;
            return mCityName.equals(that.mCityName)
                    && mCellId.equals(that.mCellId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mCityName, mCellId);
        }

        @Override
        public String toString() {
            return "TestCaseId{"
                    + "mCityName='" + mCityName + '\''
                    + ", mCellId=" + mCellId
                    + '}';
        }
    }

    /**
     * Represents a case where there is a recorded {@link KnownDifference}, but where the
     * actual data didn't match it somehow.
     */
    static class KnownDifferenceMismatch {
        /** The referenceData known difference. */
        private final KnownDifference mKnownDifference;

        /** The expected result according to the reference data. */
        private final Result mReferenceDataResult;

        /** The actual result. */
        private final Result mActualResult;

        KnownDifferenceMismatch(KnownDifference knownDifference,
                Result referenceDataResult, Result actualResult) {
            this.mKnownDifference = Objects.requireNonNull(knownDifference);
            this.mReferenceDataResult = Objects.requireNonNull(referenceDataResult);
            this.mActualResult = Objects.requireNonNull(actualResult);
        }

        KnownDifference getReferenceDataKnownDifference() {
            return mKnownDifference;
        }

        KnownDifference getActualKnownDifference() {
            return new KnownDifference(mKnownDifference.mTestCaseId, mReferenceDataResult,
                    mActualResult,
                    mKnownDifference.mType, mKnownDifference.mComment, mKnownDifference.mBugUri);
        }
    }

    /** An historic known difference between an actual result and reference data. */
    static class KnownDifference {

        static KnownDifference fromProto(
                ValidationProtos.KnownDifference knownDifferenceProto) {
            TestCaseId testCaseId = new TestCaseId(
                    knownDifferenceProto.getCityName(),
                    new S2CellId(knownDifferenceProto.getS2CellId()));
            return new KnownDifference(
                    testCaseId,
                    Result.fromProto(knownDifferenceProto.getReferenceDataResult()),
                    Result.fromProto(knownDifferenceProto.getActualResult()),
                    Type.valueOf(knownDifferenceProto.getType()),
                    knownDifferenceProto.getComment(),
                    URI.create(knownDifferenceProto.getBugUri())
            );
        }

        ValidationProtos.KnownDifference toProto() {
            return ValidationProtos.KnownDifference.newBuilder()
                    .setCityName(mTestCaseId.getCityName())
                    .setS2CellId(mTestCaseId.getCellId().id())
                    .setReferenceDataResult(mReferenceDataResult.toProto())
                    .setActualResult(mActualResult.toProto())
                    .setComment(mComment)
                    .setType(mType.toString())
                    .setBugUri(mBugUri.toString())
                    .build();
        }

        /** A categorization for causes of known differences. */
        enum Type {
            /** Uncategorized. */
            UNKNOWN,
            /** A known bug in the data generation pipeline. */
            PIPELINE_BUG,
            /** A difference due to geopolitical position. */
            GEOPOLITICS,
            /** A generic difference in data not generated by Android. */
            UPSTREAM_DIFFERENCE,
        }

        private final TestCaseId mTestCaseId;

        private final Result mReferenceDataResult;

        private final Result mActualResult;

        /** A broad categorization of the cause for the difference. */
        private final Type mType;

        /** Free-form details about the difference. */
        private final String mComment;

        /** A bug URL tracking the investigation / fix for the difference, if there is one. */
        private final URI mBugUri;

        KnownDifference(TestCaseId testCaseId, Result referenceDataResult,
                Result actualResult, Type type, String comment, URI bugUri) {
            this.mTestCaseId = Objects.requireNonNull(testCaseId);
            this.mReferenceDataResult = Objects.requireNonNull(referenceDataResult);
            this.mActualResult = Objects.requireNonNull(actualResult);
            this.mType = Objects.requireNonNull(type);
            this.mComment = Objects.requireNonNull(comment);
            this.mBugUri = Objects.requireNonNull(bugUri);
        }

        /** Returns the identifier for the test case that differed. */
        TestCaseId getTestCaseId() {
            return mTestCaseId;
        }

        /** Returns the answer according to the reference data set. */
        Result getReferenceDataResult() {
            return mReferenceDataResult;
        }

        /** REturns the answer according to Android's data set. */
        Result getActualResult() {
            return mActualResult;
        }

        String toProtoText() {
            ValidationProtos.KnownDifference proto = toProto();
            return Types.toProtoText(proto);
        }
    }

    private static String toProtoText(Message proto) {
        try (StringWriter writer = new StringWriter()) {
            TextFormat.print(proto, writer);
            return writer.getBuffer().toString();
        } catch (IOException e) {
            throw new IllegalStateException("This will never happen", e);
        }
    }

    /** The result of a city lookup. */
    static class Result {

        private final List<String> mIsoCountryCodes;
        private final List<String> mZoneIds;

        Result(List<String> isoCountryCodes, List<String> zoneIds) {
            this.mIsoCountryCodes = isoCountryCodes;
            this.mZoneIds = new ArrayList<>(zoneIds);
        }

        boolean hasMultipleZoneIds() {
            return mZoneIds.size() > 1;
        }

        /**
         * Returns {@code true} if there is an intersection between the country codes and zone IDs.
         */
        boolean intersects(Result other) {
            boolean zonesIntersect = intersect(mZoneIds, other.mZoneIds);
            boolean countriesIntersect = intersect(mIsoCountryCodes, other.mIsoCountryCodes);
            return countriesIntersect && zonesIntersect;
        }

        private static <T> boolean intersect(List<T> one, List<T> two) {
            Set<T> intersectionSet = new HashSet<>(one);
            intersectionSet.retainAll(two);
            return !intersectionSet.isEmpty();
        }

        ValidationProtos.Result toProto() {
            return ValidationProtos.Result.newBuilder()
                    .addAllIsoCountryCodes(mIsoCountryCodes)
                    .addAllZoneIds(mZoneIds)
                    .build();
        }

        static Result fromProto(ValidationProtos.Result proto) {
            return new Result(proto.getIsoCountryCodesList(), proto.getZoneIdsList());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Result result = (Result) o;
            return mIsoCountryCodes.equals(result.mIsoCountryCodes)
                    && mZoneIds.equals(result.mZoneIds);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mIsoCountryCodes, mZoneIds);
        }

        @Override
        public String toString() {
            return "Result{"
                    + "mIsoCountryCodes=" + mIsoCountryCodes
                    + ", mZoneIds=" + mZoneIds
                    + '}';
        }
    }
}
