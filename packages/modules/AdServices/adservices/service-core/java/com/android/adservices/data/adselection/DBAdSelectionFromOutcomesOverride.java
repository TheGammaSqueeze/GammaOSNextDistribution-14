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

/**
 * This POJO represents the AdSelectionFromOutcomesOverride data in the ad_selection_overrides table
 * entity.
 */
@AutoValue
@AutoValue.CopyAnnotations
@Entity(tableName = "ad_selection_from_outcomes_overrides")
public abstract class DBAdSelectionFromOutcomesOverride {
    /**
     * @return AdSelectionFromOutcomesConfigId, the primary key of the
     *     ad_selection_from_outcomes_overrides table
     */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "ad_selection_from_outcomes_config_id")
    @PrimaryKey
    @NonNull
    public abstract String getAdSelectionFromOutcomesConfigId();

    /** @return App package name */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "app_package_name")
    @NonNull
    public abstract String getAppPackageName();

    /**
     * @return The override javascript result to use for outcome selection in {@link
     *     com.android.adservices.service.adselection.AdOutcomeSelectorImpl}
     */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "selection_logic_js")
    @NonNull
    public abstract String getSelectionLogicJs();

    /** @return The override selection signals */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "selection_signals")
    @NonNull
    public abstract String getSelectionSignals();

    /** @return {@link DBAdSelectionFromOutcomesOverride } built with those params */
    public static DBAdSelectionFromOutcomesOverride create(
            String adSelectionFromOutcomesConfigId,
            String appPackageName,
            String selectionLogicJs,
            String selectionSignals) {
        return builder()
                .setAdSelectionFromOutcomesConfigId(adSelectionFromOutcomesConfigId)
                .setAppPackageName(appPackageName)
                .setSelectionLogicJs(selectionLogicJs)
                .setSelectionSignals(selectionSignals)
                .build();
    }

    /** @return generic builder */
    public static DBAdSelectionFromOutcomesOverride.Builder builder() {
        return new AutoValue_DBAdSelectionFromOutcomesOverride.Builder();
    }

    /** @return generic builder */
    @AutoValue.Builder
    public abstract static class Builder {
        /** Sets the ID for the {@link DBAdSelectionFromOutcomesOverride} entry. */
        public abstract DBAdSelectionFromOutcomesOverride.Builder
                setAdSelectionFromOutcomesConfigId(String adSelectionFromOutcomesConfigId);

        /** Sets the Package Name of the app creating the override. */
        public abstract DBAdSelectionFromOutcomesOverride.Builder setAppPackageName(
                String appPackageName);

        /** Sets the JS code to use instead of fetching it from a trusted server. */
        public abstract DBAdSelectionFromOutcomesOverride.Builder setSelectionLogicJs(
                String selectionLogicJs);

        /** Sets the Trusted Scoring Signals to use instead of fetching it from a trusted server. */
        public abstract DBAdSelectionFromOutcomesOverride.Builder setSelectionSignals(
                String selectionSignals);

        /**
         * @return an instance of {@link DBAdSelectionFromOutcomesOverride} built with the
         *     information in this builder.
         */
        public abstract DBAdSelectionFromOutcomesOverride build();
    }
}
