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

package com.android.adservices.data.adselection;

import static com.google.auto.value.AutoValue.CopyAnnotations;

import android.adservices.adselection.DecisionLogic;
import android.adservices.common.AdTechIdentifier;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;

import com.google.auto.value.AutoValue;

/** This POJO represents the {@link DecisionLogic} entity associated with an Ad Selection */
@AutoValue
@CopyAnnotations
@Entity(
        tableName = "ad_selection_buyer_logic_overrides",
        primaryKeys = {"ad_selection_config_id", "buyer_identifier"})
public abstract class DBBuyerDecisionOverride {
    /** @return AdSelectionConfigId, the primary key of this table */
    @CopyAnnotations
    @ColumnInfo(name = "ad_selection_config_id")
    @NonNull
    public abstract String getAdSelectionConfigId();

    /** @return App package name */
    @CopyAnnotations
    @ColumnInfo(name = "app_package_name")
    @NonNull
    public abstract String getAppPackageName();

    /** @return buyers associated with the override */
    @CopyAnnotations
    @ColumnInfo(name = "buyer_identifier")
    @NonNull
    public abstract AdTechIdentifier getBuyer();

    /** @return The override javascript result */
    @CopyAnnotations
    @ColumnInfo(name = "decision_logic")
    @NonNull
    public abstract String getDecisionLogic();

    /** Creates an instance of {@link DBBuyerDecisionOverride} */
    public static DBBuyerDecisionOverride create(
            String adSelectionConfigId,
            String appPackageName,
            AdTechIdentifier buyer,
            String decisionLogic) {
        return builder()
                .setAdSelectionConfigId(adSelectionConfigId)
                .setAppPackageName(appPackageName)
                .setBuyer(buyer)
                .setDecisionLogic(decisionLogic)
                .build();
    }

    /** Builds an instance of {@link DBBuyerDecisionOverride} */
    public static DBBuyerDecisionOverride.Builder builder() {
        return new AutoValue_DBBuyerDecisionOverride.Builder();
    }

    /** A builder to create an instance of {@link DBBuyerDecisionOverride} */
    @AutoValue.Builder
    public abstract static class Builder {

        /** Sets the ID for the {@link DBBuyerDecisionOverride} entry. */
        public abstract DBBuyerDecisionOverride.Builder setAdSelectionConfigId(
                String adSelectionConfigId);

        /** Sets the Package Name of the app creating the override. */
        public abstract DBBuyerDecisionOverride.Builder setAppPackageName(String appPackageName);

        /** Sets the buyer associated with this override */
        public abstract DBBuyerDecisionOverride.Builder setBuyer(AdTechIdentifier buyer);

        /** Sets the Buyer decision logic to use instead of fetching it from a trusted server. */
        public abstract DBBuyerDecisionOverride.Builder setDecisionLogic(
                String perBuyerDecisionLogic);

        /** @return an instance of {@link DBBuyerDecisionOverride} */
        public abstract DBBuyerDecisionOverride build();
    }
}
