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

package com.android.adservices.service.adselection;

import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdWithBid;
import android.adservices.adselection.SignedContextualAds;
import android.adservices.common.AdTechIdentifier;
import android.annotation.NonNull;
import android.net.Uri;

import com.android.adservices.LoggerFactory;
import com.android.adservices.service.common.AdDataValidator;
import com.android.adservices.service.common.AdRenderIdValidator;
import com.android.adservices.service.common.AdTechIdentifierValidator;
import com.android.adservices.service.common.AdTechUriValidator;
import com.android.adservices.service.common.FrequencyCapAdDataValidator;
import com.android.adservices.service.common.Validator;
import com.android.adservices.service.common.ValidatorUtil;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;

import java.util.Map;
import java.util.Objects;

/** This class runs the validation of the {@link AdSelectionConfig} subfields. */
public class AdSelectionConfigValidator implements Validator<AdSelectionConfig> {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    private static final String AD_SELECTION_CONFIG_CLASS_NAME = "AdSelectionConfig";

    @VisibleForTesting
    static final String TRUSTED_SCORING_SIGNALS_URI_TYPE = "Trusted Scoring Signals URI";

    @VisibleForTesting static final String DECISION_LOGIC_URI_TYPE = "Decision Logic URI";

    @VisibleForTesting
    static final String CONTEXTUAL_ADS_DECISION_LOGIC_FIELD_NAME =
            "Contextual ads decision logic URI";

    @NonNull private final PrebuiltLogicGenerator mPrebuiltLogicGenerator;
    @NonNull private final FrequencyCapAdDataValidator mFrequencyCapAdDataValidator;
    @NonNull private final AdTechIdentifierValidator mAdTechIdentifierValidator;
    // AdRenderId is ignored in contextual ads
    @NonNull private final AdRenderIdValidator mAdRenderIdValidator;

    public AdSelectionConfigValidator(
            @NonNull PrebuiltLogicGenerator prebuiltLogicGenerator,
            @NonNull FrequencyCapAdDataValidator frequencyCapAdDataValidator) {
        Objects.requireNonNull(prebuiltLogicGenerator);
        Objects.requireNonNull(frequencyCapAdDataValidator);

        mPrebuiltLogicGenerator = prebuiltLogicGenerator;
        mFrequencyCapAdDataValidator = frequencyCapAdDataValidator;
        mAdTechIdentifierValidator =
                new AdTechIdentifierValidator(
                        AD_SELECTION_CONFIG_CLASS_NAME, ValidatorUtil.AD_TECH_ROLE_SELLER);
        mAdRenderIdValidator = AdRenderIdValidator.AD_RENDER_ID_VALIDATOR_NO_OP;
    }

    @Override
    public void addValidation(
            @NonNull AdSelectionConfig adSelectionConfig,
            @NonNull ImmutableCollection.Builder<String> violations) {
        if (Objects.isNull(adSelectionConfig)) {
            violations.add("The AdSelectionConfig should not be null.");
        }

        String sellerString = adSelectionConfig.getSeller().toString();
        mAdTechIdentifierValidator.addValidation(sellerString, violations);

        if (mPrebuiltLogicGenerator.isPrebuiltUri(adSelectionConfig.getDecisionLogicUri())) {
            sLogger.v("Decision logic URI validation is skipped because prebuilt URI is detected!");
        } else {
            sLogger.v("Validating decision logic URI");
            AdTechUriValidator sellerDecisionLogicUriValidator =
                    new AdTechUriValidator(
                            ValidatorUtil.AD_TECH_ROLE_SELLER,
                            sellerString,
                            AD_SELECTION_CONFIG_CLASS_NAME,
                            DECISION_LOGIC_URI_TYPE);
            sellerDecisionLogicUriValidator.addValidation(
                    adSelectionConfig.getDecisionLogicUri(), violations);
        }

        if (!adSelectionConfig.getTrustedScoringSignalsUri().equals(Uri.EMPTY)) {
            AdTechUriValidator trustedScoringSignalsUriValidator =
                    new AdTechUriValidator(
                            ValidatorUtil.AD_TECH_ROLE_SELLER,
                            sellerString,
                            AD_SELECTION_CONFIG_CLASS_NAME,
                            TRUSTED_SCORING_SIGNALS_URI_TYPE);
            trustedScoringSignalsUriValidator.addValidation(
                    adSelectionConfig.getTrustedScoringSignalsUri(), violations);
        }

        violations.addAll(
                validateSignedContextualAds(adSelectionConfig.getPerBuyerSignedContextualAds()));
    }

    private ImmutableList<String> validateSignedContextualAds(
            Map<AdTechIdentifier, SignedContextualAds> signedContextualAdsMap) {
        ImmutableList.Builder<String> violations = new ImmutableList.Builder<>();

        for (Map.Entry<AdTechIdentifier, SignedContextualAds> entry :
                signedContextualAdsMap.entrySet()) {
            // Validate that the buyer decision logic for Contextual Ads satisfies buyer eTLD+1
            AdTechUriValidator buyerUriValidator =
                    new AdTechUriValidator(
                            ValidatorUtil.AD_TECH_ROLE_BUYER,
                            entry.getValue().getBuyer().toString(),
                            SignedContextualAds.class.getName(),
                            CONTEXTUAL_ADS_DECISION_LOGIC_FIELD_NAME);
            buyerUriValidator.addValidation(entry.getValue().getDecisionLogicUri(), violations);

            // Validate that the ad render URI for Contextual Ads satisfies buyer eTLD+1
            AdDataValidator adDataValidator =
                    new AdDataValidator(
                            ValidatorUtil.AD_TECH_ROLE_BUYER,
                            entry.getValue().getBuyer().toString(),
                            mFrequencyCapAdDataValidator,
                            mAdRenderIdValidator);
            for (AdWithBid ad : entry.getValue().getAdsWithBid()) {
                adDataValidator.addValidation(ad.getAdData(), violations);
            }
        }
        return violations.build();
    }
}
