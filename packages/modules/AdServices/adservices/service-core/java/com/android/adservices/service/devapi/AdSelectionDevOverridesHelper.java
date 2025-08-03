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

package com.android.adservices.service.devapi;

import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionFromOutcomesConfig;
import android.adservices.adselection.PerBuyerDecisionLogic;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.annotation.NonNull;

import androidx.annotation.Nullable;

import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.DBAdSelectionFromOutcomesOverride;
import com.android.adservices.data.adselection.DBAdSelectionOverride;
import com.android.adservices.data.adselection.DBBuyerDecisionOverride;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Helper class to support the runtime retrieval of dev overrides for the AdSelection API. */
public class AdSelectionDevOverridesHelper {
    private static final HashFunction sHashFunction = Hashing.murmur3_128();
    private static final String API_NOT_AUTHORIZED_MSG =
            "This API is not enabled for the given app because either dev options are disabled or"
                    + " the app is not debuggable.";

    private final DevContext mDevContext;
    private final AdSelectionEntryDao mAdSelectionEntryDao;

    /**
     * Creates an instance of {@link AdSelectionDevOverridesHelper} with the given {@link
     * DevContext} and {@link AdSelectionEntryDao}.
     */
    public AdSelectionDevOverridesHelper(
            @NonNull DevContext devContext, @NonNull AdSelectionEntryDao adSelectionEntryDao) {
        Objects.requireNonNull(devContext);
        Objects.requireNonNull(adSelectionEntryDao);

        this.mDevContext = devContext;
        this.mAdSelectionEntryDao = adSelectionEntryDao;
    }

    /**
     * @return a low-collision ID for the given {@link AdSelectionConfig} instance. We are accepting
     *     collision since this is a developer targeted feature and the collision should be low rate
     *     enough not to constitute a serious issue.
     */
    public static String calculateAdSelectionConfigId(
            @NonNull AdSelectionConfig adSelectionConfig) {
        // See go/hashing#java
        Hasher hasher = sHashFunction.newHasher();
        hasher.putUnencodedChars(adSelectionConfig.getSeller().toString())
                .putUnencodedChars(adSelectionConfig.getDecisionLogicUri().toString())
                .putUnencodedChars(adSelectionConfig.getAdSelectionSignals().toString())
                .putUnencodedChars(adSelectionConfig.getSellerSignals().toString());

        adSelectionConfig.getCustomAudienceBuyers().stream()
                .map(AdTechIdentifier::toString)
                .forEach(hasher::putUnencodedChars);
        adSelectionConfig.getPerBuyerSignals().entrySet().stream()
                .forEach(
                        buyerAndSignals -> {
                            hasher.putUnencodedChars(buyerAndSignals.getKey().toString())
                                    .putUnencodedChars(buyerAndSignals.getValue().toString());
                        });
        return hasher.hash().toString();
    }

    /**
     * @return a low-collision ID for the given {@link AdSelectionConfig} instance. We are accepting
     *     collision since this is a developer targeted feature and the collision should be low rate
     *     enough not to constitute a serious issue.
     */
    public static String calculateAdSelectionFromOutcomesConfigId(
            @NonNull AdSelectionFromOutcomesConfig adSelectionFromOutcomesConfig) {
        // See go/hashing#java
        Hasher hasher = sHashFunction.newHasher();
        hasher.putUnencodedChars(adSelectionFromOutcomesConfig.getSelectionLogicUri().toString())
                .putUnencodedChars(adSelectionFromOutcomesConfig.getSelectionSignals().toString());
        return hasher.hash().toString();
    }

    /**
     * Looks for an override for the given {@link AdSelectionConfig}. Will return {@code null} if
     * {@link DevContext#getDevOptionsEnabled()} returns null for the {@link DevContext} passed in
     * the constructor or if there is no override created by the app with package name specified in
     * {@link DevContext#getCallingAppPackageName()}.
     */
    @Nullable
    public String getDecisionLogicOverride(@NonNull AdSelectionConfig adSelectionConfig) {
        Objects.requireNonNull(adSelectionConfig);

        if (!mDevContext.getDevOptionsEnabled()) {
            return null;
        }
        return mAdSelectionEntryDao.getDecisionLogicOverride(
                calculateAdSelectionConfigId(adSelectionConfig),
                mDevContext.getCallingAppPackageName());
    }

    /**
     * Looks for an override for the given {@link AdSelectionConfig}. Will return {@code null} if
     * {@link DevContext#getDevOptionsEnabled()} returns null for the {@link DevContext} passed in
     * the constructor or if there is no override created by the app with package name specified in
     * {@link DevContext#getCallingAppPackageName()}.
     */
    @Nullable
    public Map<AdTechIdentifier, String> getPerBuyerDecisionLogicOverride(
            @NonNull AdSelectionConfig adSelectionConfig) {
        Objects.requireNonNull(adSelectionConfig);

        if (!mDevContext.getDevOptionsEnabled()) {
            return null;
        }
        return mAdSelectionEntryDao
                .getPerBuyerDecisionLogicOverride(
                        calculateAdSelectionConfigId(adSelectionConfig),
                        mDevContext.getCallingAppPackageName())
                .stream()
                .collect(
                        Collectors.toMap(
                                DBBuyerDecisionOverride::getBuyer,
                                DBBuyerDecisionOverride::getDecisionLogic));
    }

    /**
     * Looks for an override for the given {@link AdSelectionConfig}. Will return {@code null} if
     * {@link DevContext#getDevOptionsEnabled()} returns false for the {@link DevContext} passed in
     * the constructor or if there is no override created by the app with package name specified in
     * {@link DevContext#getCallingAppPackageName()}.
     */
    @Nullable
    public AdSelectionSignals getTrustedScoringSignalsOverride(
            @NonNull AdSelectionConfig adSelectionConfig) {
        Objects.requireNonNull(adSelectionConfig);

        if (!mDevContext.getDevOptionsEnabled()) {
            return null;
        }
        String overrideSignals =
                mAdSelectionEntryDao.getTrustedScoringSignalsOverride(
                        calculateAdSelectionConfigId(adSelectionConfig),
                        mDevContext.getCallingAppPackageName());
        return overrideSignals == null ? null : AdSelectionSignals.fromString(overrideSignals);
    }

    /**
     * Adds an override of the {@code decisionLogicJS} along with {@link
     * DevContext#getCallingAppPackageName()} for the given {@link AdSelectionConfig}.
     *
     * @throws SecurityException if{@link DevContext#getDevOptionsEnabled()} returns false for the
     *     {@link DevContext}
     */
    public void addAdSelectionSellerOverride(
            @NonNull AdSelectionConfig adSelectionConfig,
            @NonNull String decisionLogicJS,
            @NonNull AdSelectionSignals trustedScoringSignals,
            @NonNull PerBuyerDecisionLogic perBuyerDecisionLogic) {
        Objects.requireNonNull(adSelectionConfig);
        Objects.requireNonNull(decisionLogicJS);

        if (!mDevContext.getDevOptionsEnabled()) {
            throw new SecurityException(API_NOT_AUTHORIZED_MSG);
        }
        final String adSelectionConfigId = calculateAdSelectionConfigId(adSelectionConfig);
        mAdSelectionEntryDao.persistAdSelectionOverride(
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(adSelectionConfigId)
                        .setAppPackageName(mDevContext.getCallingAppPackageName())
                        .setDecisionLogicJS(decisionLogicJS)
                        .setTrustedScoringSignals(trustedScoringSignals.toString())
                        .build());

        List<DBBuyerDecisionOverride> dbBuyerDecisionOverrideList =
                perBuyerDecisionLogic.getPerBuyerLogicMap().entrySet().stream()
                        .map(
                                x ->
                                        DBBuyerDecisionOverride.builder()
                                                .setBuyer(x.getKey())
                                                .setDecisionLogic(x.getValue().getLogic())
                                                .setAdSelectionConfigId(adSelectionConfigId)
                                                .setAppPackageName(
                                                        mDevContext.getCallingAppPackageName())
                                                .build())
                        .collect(Collectors.toList());
        mAdSelectionEntryDao.persistPerBuyerDecisionLogicOverride(dbBuyerDecisionOverrideList);
    }

    /**
     * Removes an override for the given {@link AdSelectionConfig}.
     *
     * @throws SecurityException if{@link DevContext#getDevOptionsEnabled()} returns false for the
     *     {@link DevContext}
     */
    public void removeAdSelectionSellerOverride(@NonNull AdSelectionConfig adSelectionConfig) {
        Objects.requireNonNull(adSelectionConfig);

        if (!mDevContext.getDevOptionsEnabled()) {
            throw new SecurityException(API_NOT_AUTHORIZED_MSG);
        }

        String adSelectionConfigId = calculateAdSelectionConfigId(adSelectionConfig);
        String appPackageName = mDevContext.getCallingAppPackageName();

        mAdSelectionEntryDao.removeAdSelectionOverrideByIdAndPackageName(
                adSelectionConfigId, appPackageName);
        mAdSelectionEntryDao.removeBuyerDecisionLogicOverrideByIdAndPackageName(
                adSelectionConfigId, appPackageName);
    }

    /**
     * Removes all ad selection overrides that match {@link DevContext#getCallingAppPackageName()}.
     *
     * @throws SecurityException if{@link DevContext#getDevOptionsEnabled()} returns false for the
     *     {@link DevContext}
     */
    public void removeAllDecisionLogicOverrides() {
        if (!mDevContext.getDevOptionsEnabled()) {
            throw new SecurityException(API_NOT_AUTHORIZED_MSG);
        }

        mAdSelectionEntryDao.removeAllAdSelectionOverrides(mDevContext.getCallingAppPackageName());
        mAdSelectionEntryDao.removeAllBuyerDecisionOverrides(
                mDevContext.getCallingAppPackageName());
    }

    /**
     * Looks for an override for the given {@link AdSelectionFromOutcomesConfig}. Will return {@code
     * null} if {@link DevContext#getDevOptionsEnabled()} returns false for the {@link DevContext}
     * passed in the constructor or if there is no override created by the app with package name
     * specified in {@link DevContext#getCallingAppPackageName()}.
     */
    @Nullable
    public String getSelectionLogicOverride(@NonNull AdSelectionFromOutcomesConfig config) {
        Objects.requireNonNull(config);

        if (!mDevContext.getDevOptionsEnabled()) {
            return null;
        }
        return mAdSelectionEntryDao.getSelectionLogicOverride(
                calculateAdSelectionFromOutcomesConfigId(config),
                mDevContext.getCallingAppPackageName());
    }

    /**
     * Looks for an override for the given {@link AdSelectionFromOutcomesConfig}. Will return {@code
     * null} if {@link DevContext#getDevOptionsEnabled()} returns false for the {@link DevContext}
     * passed in the constructor or if there is no override created by the app with package name
     * specified in {@link DevContext#getCallingAppPackageName()}.
     */
    @Nullable
    public AdSelectionSignals getSelectionSignalsOverride(
            @NonNull AdSelectionFromOutcomesConfig config) {
        Objects.requireNonNull(config);

        if (!mDevContext.getDevOptionsEnabled()) {
            return null;
        }
        String overrideSignals =
                mAdSelectionEntryDao.getSelectionSignalsOverride(
                        calculateAdSelectionFromOutcomesConfigId(config),
                        mDevContext.getCallingAppPackageName());
        return overrideSignals == null ? null : AdSelectionSignals.fromString(overrideSignals);
    }

    /**
     * Adds an override of the {@code decisionLogicJS} along with {@link
     * DevContext#getCallingAppPackageName()} for the given {@link AdSelectionConfig}.
     *
     * @throws SecurityException if{@link DevContext#getDevOptionsEnabled()} returns false for the
     *     {@link DevContext}
     */
    public void addAdSelectionOutcomeSelectorOverride(
            @NonNull AdSelectionFromOutcomesConfig adSelectionFromOutcomesConfig,
            @NonNull String selectionLogicJs,
            @NonNull AdSelectionSignals selectionSignals) {
        Objects.requireNonNull(adSelectionFromOutcomesConfig);
        Objects.requireNonNull(selectionLogicJs);
        Objects.requireNonNull(selectionSignals);

        if (!mDevContext.getDevOptionsEnabled()) {
            throw new SecurityException(API_NOT_AUTHORIZED_MSG);
        }
        mAdSelectionEntryDao.persistAdSelectionFromOutcomesOverride(
                DBAdSelectionFromOutcomesOverride.builder()
                        .setAdSelectionFromOutcomesConfigId(
                                calculateAdSelectionFromOutcomesConfigId(
                                        adSelectionFromOutcomesConfig))
                        .setAppPackageName(mDevContext.getCallingAppPackageName())
                        .setSelectionLogicJs(selectionLogicJs)
                        .setSelectionSignals(selectionSignals.toString())
                        .build());
    }

    /**
     * Removes an override for the given {@link AdSelectionFromOutcomesConfig}.
     *
     * @throws SecurityException if{@link DevContext#getDevOptionsEnabled()} returns false for the
     *     {@link DevContext}
     */
    public void removeAdSelectionOutcomeSelectorOverride(
            @NonNull AdSelectionFromOutcomesConfig config) {
        Objects.requireNonNull(config);

        if (!mDevContext.getDevOptionsEnabled()) {
            throw new SecurityException(API_NOT_AUTHORIZED_MSG);
        }

        String adSelectionConfigId = calculateAdSelectionFromOutcomesConfigId(config);
        String appPackageName = mDevContext.getCallingAppPackageName();

        mAdSelectionEntryDao.removeAdSelectionFromOutcomesOverrideByIdAndPackageName(
                adSelectionConfigId, appPackageName);
    }

    /**
     * Removes all ad selection from outcomes overrides that match {@link DevContext
     * #getCallingAppPackageName()}.
     *
     * @throws SecurityException if{@link DevContext#getDevOptionsEnabled()} returns false for the
     *     {@link DevContext}
     */
    public void removeAllSelectionLogicOverrides() {
        if (!mDevContext.getDevOptionsEnabled()) {
            throw new SecurityException(API_NOT_AUTHORIZED_MSG);
        }

        mAdSelectionEntryDao.removeAllAdSelectionFromOutcomesOverrides(
                mDevContext.getCallingAppPackageName());
    }
}
