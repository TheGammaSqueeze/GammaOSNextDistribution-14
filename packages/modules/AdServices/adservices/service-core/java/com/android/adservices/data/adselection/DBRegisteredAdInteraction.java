/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.adservices.data.adselection;

import android.adservices.adselection.ReportEventRequest;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;

import com.google.auto.value.AutoValue;
import com.google.auto.value.AutoValue.CopyAnnotations;

/**
 * This POJO represents the DBRegisteredAdInteraction data in the registered_ad_interactions table
 * entity.
 */
@AutoValue
@CopyAnnotations
@Entity(
        tableName = "registered_ad_interactions",
        primaryKeys = {"ad_selection_id", "interaction_key", "destination"})
public abstract class DBRegisteredAdInteraction {

    /**
     * @return adSelectionId, the unique identifier for the ad selection process associated with
     *     this registered interaction
     */
    @CopyAnnotations
    @ColumnInfo(name = "ad_selection_id")
    public abstract long getAdSelectionId();

    /** @return the name of interaction being registered (e.g., click, view, etc.) */
    @CopyAnnotations
    @ColumnInfo(name = "interaction_key")
    @NonNull
    public abstract String getInteractionKey();

    /**
     * @return the reporting destination of this registered interaction during reporting (buyer or
     *     seller, etc.)
     */
    @CopyAnnotations
    @ColumnInfo(name = "destination")
    @ReportEventRequest.ReportingDestination
    public abstract int getDestination();

    /** @return Uri to be used during interaction reporting */
    @CopyAnnotations
    @ColumnInfo(name = "interaction_reporting_uri")
    @NonNull
    public abstract Uri getInteractionReportingUri();

    /**
     * @return DBRegisteredAdInteraction built with those params
     */
    @NonNull
    public static DBRegisteredAdInteraction create(
            long adSelectionId,
            String interactionKey,
            @ReportEventRequest.ReportingDestination int destination,
            Uri interactionReportingUri) {
        return builder()
                .setAdSelectionId(adSelectionId)
                .setInteractionKey(interactionKey)
                .setDestination(destination)
                .setInteractionReportingUri(interactionReportingUri)
                .build();
    }

    /** @return generic builder */
    @NonNull
    public static DBRegisteredAdInteraction.Builder builder() {
        return new AutoValue_DBRegisteredAdInteraction.Builder();
    }

    /** AutoValue Builder */
    @AutoValue.Builder
    public abstract static class Builder {
        /** Sets the adSelectionId for the {@link DBRegisteredAdInteraction} entry. */
        @NonNull
        public abstract DBRegisteredAdInteraction.Builder setAdSelectionId(long adSelectionId);

        /** Sets the interactionKey for the {@link DBRegisteredAdInteraction} entry. */
        @NonNull
        public abstract DBRegisteredAdInteraction.Builder setInteractionKey(
                @NonNull String interactionKey);

        /** Sets the reporting destination for the {@link DBRegisteredAdInteraction} entry. */
        @NonNull
        public abstract DBRegisteredAdInteraction.Builder setDestination(
                @ReportEventRequest.ReportingDestination int destination);

        /** Sets the interactionReportingUri for the {@link DBRegisteredAdInteraction} entry. */
        @NonNull
        public abstract DBRegisteredAdInteraction.Builder setInteractionReportingUri(
                @NonNull Uri interactionReportingUri);

        /**
         * @return an instance of {@link DBRegisteredAdInteraction} built with the information in
         *     this builder.
         */
        @NonNull
        public abstract DBRegisteredAdInteraction build();
    }
}
