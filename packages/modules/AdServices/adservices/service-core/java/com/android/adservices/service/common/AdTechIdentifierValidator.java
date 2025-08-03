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

package com.android.adservices.service.common;

import static com.android.adservices.service.common.ValidatorUtil.HTTPS_SCHEME;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.Uri;

import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ImmutableCollection;
import com.google.common.net.InternetDomainName;

import java.util.Objects;

/** Validation utility class for {@link android.adservices.common.AdTechIdentifier}. */
public class AdTechIdentifierValidator implements Validator<String> {
    @VisibleForTesting
    public static final String IDENTIFIER_SHOULD_NOT_BE_NULL_OR_EMPTY =
            "The %s's %s should not be null nor empty.";

    @VisibleForTesting
    public static final String IDENTIFIER_HAS_MISSING_DOMAIN_NAME =
            "The %s's %s has missing domain name.";

    @VisibleForTesting
    public static final String IDENTIFIER_IS_AN_INVALID_DOMAIN_NAME =
            "The %s's %s is an invalid domain name.";

    @NonNull private final String mClassName;
    @NonNull private final String mAdTechRole;

    /**
     * Constructs a validator which validates an ad tech identifier.
     *
     * @param className the class the name of the field came from, used in error string
     *     construction.
     * @param adTechRole the identifier's field name, used in error string construction.
     */
    public AdTechIdentifierValidator(@NonNull String className, @NonNull String adTechRole) {
        Objects.requireNonNull(className);
        Objects.requireNonNull(adTechRole);

        mClassName = className;
        mAdTechRole = adTechRole;
    }

    /**
     * Validate an ad tech identifier:
     *
     * <ul>
     *   <li>The identifier should not be empty or null.
     *   <li>The identifier should not have a valid domain name.
     *   <li>The identifier should be a domain name.
     * </ul>
     *
     * @param adTechIdentifier the identifier to be validated
     */
    @Override
    public void addValidation(
            @Nullable String adTechIdentifier,
            @NonNull ImmutableCollection.Builder<String> violations) {
        Objects.requireNonNull(violations);

        String host = Uri.parse(HTTPS_SCHEME + "://" + adTechIdentifier).getHost();
        if (ValidatorUtil.isStringNullOrEmpty(adTechIdentifier)) {
            violations.add(
                    String.format(IDENTIFIER_SHOULD_NOT_BE_NULL_OR_EMPTY, mClassName, mAdTechRole));
        } else if (Objects.isNull(host) || host.isEmpty()) {
            violations.add(
                    String.format(IDENTIFIER_HAS_MISSING_DOMAIN_NAME, mClassName, mAdTechRole));
        } else if (!Objects.equals(host, adTechIdentifier)
                || !InternetDomainName.isValid(adTechIdentifier)) {
            violations.add(
                    String.format(IDENTIFIER_IS_AN_INVALID_DOMAIN_NAME, mClassName, mAdTechRole));
        }
    }
}
