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

import android.adservices.adselection.AdSelectionFromOutcomesConfig;
import android.adservices.adselection.AdSelectionFromOutcomesInput;
import android.adservices.common.AdTechIdentifier;
import android.annotation.NonNull;
import android.net.Uri;

import androidx.annotation.Nullable;

import com.android.adservices.LoggerFactory;
import com.android.adservices.service.common.Validator;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Objects;

/** Runs validations on {@link AdSelectionFromOutcomesInput} object */
public class AdSelectionFromOutcomesConfigValidator
        implements Validator<AdSelectionFromOutcomesConfig> {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    @VisibleForTesting
    static final String INPUT_PARAM_CANNOT_BE_NULL = "AdSelectionFromOutcomesInput cannot be null";

    @VisibleForTesting
    static final String AD_OUTCOMES_CANNOT_BE_NULL_OR_EMPTY =
            "AdSelectionOutcomes cannot be null or empty";

    @VisibleForTesting
    static final String SELECTION_LOGIC_URI_CANNOT_BE_NULL_OR_EMPTY =
            "SelectionLogicUri cannot be null or empty";

    @VisibleForTesting
    static final String URI_IS_NOT_ABSOLUTE = "The SelectionLogicUri should be absolute";

    @VisibleForTesting
    static final String URI_IS_NOT_HTTPS = "The SelectionLogicUri is not secured by https";

    @VisibleForTesting
    static final String AD_SELECTION_IDS_DONT_EXIST = "Ad Selection Ids don't exist: '%s'";

    @VisibleForTesting
    static final String URI_SHOULD_HAVE_PRESENT_HOST =
            "The AdSelectionFromOutcomesConfig selectionLogicUri should have a valid host.";

    @VisibleForTesting
    static final String SELLER_AND_URI_HOST_ARE_INCONSISTENT =
            "The seller hostname \"%s\" and the seller-provided "
                    + "hostname \"%s\" are not "
                    + "consistent in AdSelectionFromOutcomesConfig selection Logic Uri.";

    @VisibleForTesting static final String HTTPS_PREFIX = "https://";
    private static final String HTTPS_SCHEME = "https";
    @NonNull private final String mCallerPackageName;
    @NonNull private final PrebuiltLogicGenerator mPrebuiltLogicGenerator;

    public AdSelectionFromOutcomesConfigValidator(
            @NonNull String callerPackageName,
            @NonNull PrebuiltLogicGenerator prebuiltLogicGenerator) {
        Objects.requireNonNull(callerPackageName);
        Objects.requireNonNull(prebuiltLogicGenerator);

        mCallerPackageName = callerPackageName;
        mPrebuiltLogicGenerator = prebuiltLogicGenerator;
    }

    /** Validates the object and populate the violations. */
    @Override
    public void addValidation(
            @NonNull AdSelectionFromOutcomesConfig config,
            @NonNull ImmutableCollection.Builder<String> violations) {
        // TODO(b/275211917): Refactor to address the duplicate code between this class and
        //  AdSelectionConfigValidator
        Objects.requireNonNull(config, INPUT_PARAM_CANNOT_BE_NULL);

        violations.addAll(validateAdSelectionIds(config.getAdSelectionIds()));
        if (mPrebuiltLogicGenerator.isPrebuiltUri(config.getSelectionLogicUri())) {
            sLogger.v(
                    "Selection logic uri validation is skipped because prebuilt uri is detected!");
        } else {
            sLogger.v("Validating selection logic URI");
            violations.addAll(
                    validateSelectionLogicUri(config.getSeller(), config.getSelectionLogicUri()));
        }
    }

    private ImmutableList<String> validateAdSelectionIds(@NonNull List<Long> adSelectionIds) {
        ImmutableList.Builder<String> violations = new ImmutableList.Builder<>();
        if (Objects.isNull(adSelectionIds) || adSelectionIds.isEmpty()) {
            violations.add(AD_OUTCOMES_CANNOT_BE_NULL_OR_EMPTY);
        }
        return violations.build();
    }

    private ImmutableList<String> validateSelectionLogicUri(
            @NonNull AdTechIdentifier seller, @NonNull Uri selectionLogicUri) {
        ImmutableList.Builder<String> violations = new ImmutableList.Builder<>();

        if (Objects.isNull(selectionLogicUri) || selectionLogicUri.toString().isEmpty()) {
            violations.add(SELECTION_LOGIC_URI_CANNOT_BE_NULL_OR_EMPTY);
        }

        if (!selectionLogicUri.isAbsolute()) {
            violations.add(URI_IS_NOT_ABSOLUTE);
        } else if (!selectionLogicUri.getScheme().equals(HTTPS_SCHEME)) {
            violations.add(URI_IS_NOT_HTTPS);
        }

        String sellerHost = Uri.parse(HTTPS_PREFIX + seller).getHost();
        String uriHost = selectionLogicUri.getHost();
        if (isStringNullOrEmpty(uriHost)) {
            violations.add(URI_SHOULD_HAVE_PRESENT_HOST);
        } else if (!seller.toString().isEmpty()
                && !Objects.isNull(sellerHost)
                && !sellerHost.isEmpty()
                && !uriHost.equalsIgnoreCase(sellerHost)) {
            violations.add(
                    String.format(SELLER_AND_URI_HOST_ARE_INCONSISTENT, sellerHost, uriHost));
        }
        return violations.build();
    }

    private boolean isStringNullOrEmpty(@Nullable String str) {
        return Objects.isNull(str) || str.isEmpty();
    }
}
