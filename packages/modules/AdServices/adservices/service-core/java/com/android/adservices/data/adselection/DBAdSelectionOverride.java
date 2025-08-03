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

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.google.auto.value.AutoValue;
import com.google.auto.value.AutoValue.CopyAnnotations;

/** This POJO represents the AdSelectionOverride data in the ad_selection_overrides table entity. */
@AutoValue
@CopyAnnotations
@Entity(tableName = "ad_selection_overrides")
public abstract class DBAdSelectionOverride {

    /**
     * @return AdSelectionConfigId, the primary key of the ad_selection_overrides table
     */
    @CopyAnnotations
    @ColumnInfo(name = "ad_selection_config_id")
    @PrimaryKey
    @NonNull
    public abstract String getAdSelectionConfigId();

    /**
     * @return App package name
     */
    @CopyAnnotations
    @ColumnInfo(name = "app_package_name")
    @NonNull
    public abstract String getAppPackageName();

    /**
     * @return The override javascript result
     */
    @CopyAnnotations
    @ColumnInfo(name = "decision_logic")
    @NonNull
    public abstract String getDecisionLogicJS();

    /** @return The override trusted scoring signals */
    @CopyAnnotations
    @ColumnInfo(name = "trusted_scoring_signals")
    @NonNull
    public abstract String getTrustedScoringSignals();

    /** @return DBAdSelectionOverride built with those params */
    public static DBAdSelectionOverride create(
            String adSelectionConfigId,
            String appPackageName,
            String decisionLogicJS,
            String trustedScoringSignals) {
        return builder()
                .setAdSelectionConfigId(adSelectionConfigId)
                .setAppPackageName(appPackageName)
                .setDecisionLogicJS(decisionLogicJS)
                .setTrustedScoringSignals(trustedScoringSignals)
                .build();
    }

    /**
     * @return generic builder
     */
    public static DBAdSelectionOverride.Builder builder() {
        return new AutoValue_DBAdSelectionOverride.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        /** Sets the ID for the {@link DBAdSelectionOverride} entry. */
        public abstract DBAdSelectionOverride.Builder setAdSelectionConfigId(
                String adSelectionConfigId);

        /** Sets the Package Name of the app creating the override. */
        public abstract DBAdSelectionOverride.Builder setAppPackageName(String appPackageName);

        /** Sets the JS code to use instead of fetching it from a trusted server. */
        public abstract DBAdSelectionOverride.Builder setDecisionLogicJS(String decisionLogicJS);

        /** Sets the Trusted Scoring Signals to use instead of fetching it from a trusted server. */
        public abstract DBAdSelectionOverride.Builder setTrustedScoringSignals(
                String trustedScoringSignals);

        /**
         * @return an instance of {@link DBAdSelectionOverride} built with the information in this
         *     builder.
         */
        public abstract DBAdSelectionOverride build();
    }
}
