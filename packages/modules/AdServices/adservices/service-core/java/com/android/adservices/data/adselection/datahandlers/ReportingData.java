/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.adservices.data.adselection.datahandlers;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.Uri;

import androidx.room.Ignore;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;

import java.util.Objects;

/** Data class representing the event level reporting data associated with an ad selection run. */
@AutoValue
public abstract class ReportingData {

    /** Win reporting uri associated with the buyer adtech. */
    @Nullable
    public abstract Uri getBuyerWinReportingUri();

    /** Win reporting uri associated with the seller adtech. */
    @Nullable
    public abstract Uri getSellerWinReportingUri();

    /** The data required for computing the reporting Uris. */
    @AutoValue.CopyAnnotations
    @Nullable
    @Ignore
    public abstract ReportingComputationData getReportingComputationData();

    /**
     * @return generic builder
     */
    public static Builder builder() {
        return new AutoValue_ReportingData.Builder();
    }

    /**
     * Factory method to create ReportingData containing URIs. Room uses this factory method to
     * create objects.
     */
    public static ReportingData createWithUris(
            @NonNull Uri buyerWinReportingUri, @NonNull Uri sellerWinReportingUri) {
        Objects.requireNonNull(buyerWinReportingUri);
        Objects.requireNonNull(sellerWinReportingUri);

        return builder()
                .setBuyerWinReportingUri(buyerWinReportingUri)
                .setSellerWinReportingUri(sellerWinReportingUri)
                .build();
    }

    /** Builder for ReportingData. */
    @AutoValue.Builder
    public abstract static class Builder {

        /** Sets the win reporting uri associated with the buyer adtech. */
        public abstract Builder setBuyerWinReportingUri(@Nullable Uri buyerWinReportingUri);

        /** Sets the win reporting uri associated with the seller adtech. */
        public abstract Builder setSellerWinReportingUri(@Nullable Uri sellerWinReportingUri);

        /** Sets the data required for computing the reporting Uris. */
        public abstract Builder setReportingComputationData(
                @Nullable ReportingComputationData reportingComputationData);

        abstract ReportingData autoBuild();

        /** Builds a {@link ReportingData} object. */
        public ReportingData build() {
            ReportingData reportingData = autoBuild();

            boolean reportingUriSet =
                    reportingData.getBuyerWinReportingUri() != null
                            || reportingData.getSellerWinReportingUri() != null;
            boolean reportingComputationDataSet =
                    reportingData.getReportingComputationData() != null;
            Preconditions.checkArgument(
                    reportingUriSet ^ reportingComputationDataSet,
                    "Both reporting Uris and reporting computation data set "
                            + "to non null values in ReportingData. Only one of those should"
                            + "be set.");
            return reportingData;
        }
    }
}
