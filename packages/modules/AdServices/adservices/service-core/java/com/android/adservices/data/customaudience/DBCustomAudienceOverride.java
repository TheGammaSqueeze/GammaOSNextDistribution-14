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

package com.android.adservices.data.customaudience;

import android.adservices.common.AdTechIdentifier;
import android.annotation.Nullable;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;

import com.google.auto.value.AutoValue;
import com.google.auto.value.AutoValue.CopyAnnotations;

/**
 * This POJO represents the CustomAudienceOverride data in the custom_audience_overrides table
 * entity.
 */
@AutoValue
@CopyAnnotations
@Entity(
        tableName = "custom_audience_overrides",
        primaryKeys = {"owner", "buyer", "name"})
public abstract class DBCustomAudienceOverride {
    /** @return the owner */
    @CopyAnnotations
    @ColumnInfo(name = "owner")
    @NonNull
    public abstract String getOwner();

    /** @return the buyer */
    @CopyAnnotations
    @ColumnInfo(name = "buyer")
    @NonNull
    public abstract AdTechIdentifier getBuyer();

    /**
     * @return name
     */
    @CopyAnnotations
    @ColumnInfo(name = "name")
    @NonNull
    public abstract String getName();

    /**
     * @return App package name, app package name associated with the caller
     */
    @CopyAnnotations
    @ColumnInfo(name = "app_package_name")
    @NonNull
    public abstract String getAppPackageName();

    /**
     * @return The override javascript result
     */
    @CopyAnnotations
    @ColumnInfo(name = "bidding_logic")
    @NonNull
    public abstract String getBiddingLogicJS();

    /** @return the version of the override javascript result */
    @CopyAnnotations
    @ColumnInfo(name = "bidding_logic_version")
    @Nullable
    public abstract Long getBiddingLogicJsVersion();

    /**
     * @return The override trusted bidding data result
     */
    @CopyAnnotations
    @ColumnInfo(name = "trusted_bidding_data")
    @NonNull
    public abstract String getTrustedBiddingData();

    /** @return DBAdSelectionOverride built with those params */
    public static DBCustomAudienceOverride create(
            String owner,
            AdTechIdentifier buyer,
            String name,
            String appPackageName,
            String biddingLogicJS,
            Long biddingLogicJsVersion,
            String trustedBiddingData) {
        return builder()
                .setOwner(owner)
                .setBuyer(buyer)
                .setName(name)
                .setAppPackageName(appPackageName)
                .setBiddingLogicJS(biddingLogicJS)
                .setBiddingLogicJsVersion(biddingLogicJsVersion)
                .setTrustedBiddingData(trustedBiddingData)
                .build();
    }

    /**
     * @return generic builder
     */
    public static DBCustomAudienceOverride.Builder builder() {
        return new AutoValue_DBCustomAudienceOverride.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        /** Sets the owner of the {@link DBCustomAudienceOverride} entry. */
        public abstract DBCustomAudienceOverride.Builder setOwner(String owner);

        /** Sets the buyer of the {@link DBCustomAudienceOverride} entry. */
        public abstract DBCustomAudienceOverride.Builder setBuyer(AdTechIdentifier buyer);

        /** Sets the name of the {@link DBCustomAudienceOverride} entry. */
        public abstract DBCustomAudienceOverride.Builder setName(String name);

        /** Sets the appPackageName of the {@link DBCustomAudienceOverride} entry. */
        public abstract DBCustomAudienceOverride.Builder setAppPackageName(String appPackageName);

        /** Sets the biddingLogicJS of the {@link DBCustomAudienceOverride} entry. */
        public abstract DBCustomAudienceOverride.Builder setBiddingLogicJS(String biddingLogicJS);

        /** Sets the biddingLogicJSVersion of the {@link DBCustomAudienceOverride} entry. */
        public abstract Builder setBiddingLogicJsVersion(Long value);

        /** Sets the trustedBiddingData of the {@link DBCustomAudienceOverride} entry. */
        public abstract DBCustomAudienceOverride.Builder setTrustedBiddingData(
                String trustedBiddingData);

        /**
         * @return an instance of {@link DBCustomAudienceOverride} built with the information in
         *     this builder.
         */
        public abstract DBCustomAudienceOverride build();
    }
}
