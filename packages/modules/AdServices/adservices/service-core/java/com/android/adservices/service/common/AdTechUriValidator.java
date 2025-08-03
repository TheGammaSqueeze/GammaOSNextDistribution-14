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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.Uri;

import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ImmutableCollection;

import java.util.Locale;
import java.util.Objects;

/**
 * Validates an ad tech uri against an {@link android.adservices.common.AdTechIdentifier}.
 *
 * <p>If the ad tech identifier is built from an empty string, ad tech identifier host matching is
 * skipped.
 */
public class AdTechUriValidator implements Validator<Uri> {

    @VisibleForTesting
    public static final String URI_SHOULD_BE_SPECIFIED = "The %s's %s should be specified.";

    @VisibleForTesting
    public static final String URI_SHOULD_HAVE_PRESENT_HOST =
            "The %s's %s should have present host.";

    @VisibleForTesting
    public static final String URI_SHOULD_USE_HTTPS = "The %s's %s should use HTTPS.";

    @VisibleForTesting
    public static final String IDENTIFIER_AND_URI_ARE_INCONSISTENT =
            "The %s host name %s and the %s-provided %s's host name %s are not consistent.";

    @NonNull public final String mAdTechRole;
    @NonNull public final String mAdTechIdentifier;
    @NonNull public final String mClassName;
    @NonNull public final String mUriFieldName;

    public AdTechUriValidator(
            @NonNull String adTechRole,
            @NonNull String adTechIdentifier,
            @NonNull String className,
            @NonNull String uriFieldName) {
        Objects.requireNonNull(adTechRole);
        Objects.requireNonNull(adTechIdentifier);
        Objects.requireNonNull(className);
        Objects.requireNonNull(uriFieldName);

        mAdTechRole = adTechRole;
        mAdTechIdentifier = adTechIdentifier;
        mClassName = className;
        mUriFieldName = uriFieldName;
    }

    /**
     * Validate an uri uses HTTPS and under the ad tech identifier domain.
     *
     * <p>If the ad tech identifier used to build the {@link AdTechUriValidator} object is empty,
     * then ad tech identifier host matching is skipped.
     *
     * @param uri the uri to be validated.
     */
    @Override
    public void addValidation(
            @Nullable Uri uri, @NonNull ImmutableCollection.Builder<String> violations) {
        Objects.requireNonNull(violations);

        if (Objects.isNull(uri)) {
            violations.add(
                    String.format(
                            Locale.ENGLISH, URI_SHOULD_BE_SPECIFIED, mClassName, mUriFieldName));
        } else {
            String uriHost = uri.getHost();
            if (ValidatorUtil.isStringNullOrEmpty(uriHost)) {
                violations.add(
                        String.format(
                                Locale.ENGLISH,
                                URI_SHOULD_HAVE_PRESENT_HOST,
                                mClassName,
                                mUriFieldName));
            } else if (!ValidatorUtil.isStringNullOrEmpty(mAdTechIdentifier)
                    && !matchesEtldPlus1(uriHost)) {
                violations.add(
                        String.format(
                                Locale.ENGLISH,
                                IDENTIFIER_AND_URI_ARE_INCONSISTENT,
                                mAdTechRole,
                                mAdTechIdentifier,
                                mAdTechRole,
                                mUriFieldName,
                                uriHost));
            } else if (!ValidatorUtil.HTTPS_SCHEME.equalsIgnoreCase(uri.getScheme())) {
                violations.add(String.format(URI_SHOULD_USE_HTTPS, mClassName, mUriFieldName));
            }
        }
    }

    private boolean matchesEtldPlus1(@Nullable String uriHost) {
        if (ValidatorUtil.isStringNullOrEmpty(uriHost)) {
            return false;
        }

        // Positive match if exact match or valid suffix with separating '.' (all case-insensitive)
        return mAdTechIdentifier.equalsIgnoreCase(uriHost)
                || uriHost.toLowerCase(Locale.ENGLISH)
                        .endsWith("." + mAdTechIdentifier.toLowerCase(Locale.ENGLISH));
    }
}
