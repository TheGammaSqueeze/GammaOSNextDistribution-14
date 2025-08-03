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

package com.android.adservices.service.measurement.registration;

import android.net.Uri;

import com.android.adservices.service.Flags;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Class for handling redirects */
public class AsyncRedirects {
    public static final String HEADER_ATTRIBUTION_REPORTING_REDIRECT_CONFIG =
            "Attribution-Reporting-Redirect-Config";
    public static final String REDIRECT_302_TO_WELL_KNOWN = "redirect-302-to-well-known";
    public static final String WELL_KNOWN_PATH_SEGMENT =
            ".well-known/attribution-reporting/register-redirect";
    public static final String WELL_KNOWN_QUERY_PARAM = "302_url";
    public static final String REDIRECT_LIST_HEADER_KEY = "Attribution-Reporting-Redirect";
    public static final String REDIRECT_LOCATION_HEADER_KEY = "Location";
    private final List<AsyncRedirect> mLocationRedirects;
    private final List<AsyncRedirect> mListRedirects;

    public AsyncRedirects() {
        mLocationRedirects = new ArrayList<>();
        mListRedirects = new ArrayList<>();
    }

    /** Return flattened list of {@link AsyncRedirect} */
    public List<AsyncRedirect> getRedirects() {
        List<AsyncRedirect> allRedirects = new ArrayList<>(mListRedirects);
        allRedirects.addAll(mLocationRedirects);

        return allRedirects;
    }

    /** Get list of {@link AsyncRedirect} by redirect type */
    public List<AsyncRedirect> getRedirectsByType(AsyncRegistration.RedirectType redirectType) {
        if (redirectType == AsyncRegistration.RedirectType.LOCATION) {
            return new ArrayList<>(mLocationRedirects);
        } else {
            return new ArrayList<>(mListRedirects);
        }
    }

    /** Process redirects based on the given headers */
    public void configure(
            Map<String, List<String>> headers, Flags flags, AsyncRegistration parentRegistration) {
        if (!parentRegistration.shouldProcessRedirects()) {
            return;
        }

        Map<AsyncRegistration.RedirectType, List<Uri>> urisByType =
                FetcherUtil.parseRedirects(headers);

        for (Uri locationRedirectUri : urisByType.get(AsyncRegistration.RedirectType.LOCATION)) {
            if (shouldRedirect302ToWellKnown(headers, flags, parentRegistration)) {
                mLocationRedirects.add(
                        new AsyncRedirect(
                                getLocationRedirectToWellKnownUri(locationRedirectUri),
                                AsyncRedirect.RedirectBehavior.LOCATION_TO_WELL_KNOWN));
            } else {
                mLocationRedirects.add(
                        new AsyncRedirect(
                                locationRedirectUri, AsyncRedirect.RedirectBehavior.AS_IS));
            }
        }

        for (Uri listRedirectUri : urisByType.get(AsyncRegistration.RedirectType.LIST)) {
            mListRedirects.add(
                    new AsyncRedirect(listRedirectUri, AsyncRedirect.RedirectBehavior.AS_IS));
        }
    }

    private static boolean shouldRedirect302ToWellKnown(
            Map<String, List<String>> headers, Flags flags, AsyncRegistration parentRegistration) {
        boolean isParentRegistrationRedirectsToWellKnown =
                AsyncRedirect.RedirectBehavior.LOCATION_TO_WELL_KNOWN.equals(
                        parentRegistration.getRedirectBehavior());

        return flags.getMeasurementEnableRedirectToWellKnownPath()
                && (isParentRegistrationRedirectsToWellKnown
                        || isRedirect302ToWellKnownPath(headers));
    }

    /**
     * Return true if the given headers indicate redirects should prepend well known prefix to the
     * path.
     */
    private static boolean isRedirect302ToWellKnownPath(Map<String, List<String>> headers) {
        if (!headers.containsKey(HEADER_ATTRIBUTION_REPORTING_REDIRECT_CONFIG)) {
            return false;
        }
        List<String> config = headers.get(HEADER_ATTRIBUTION_REPORTING_REDIRECT_CONFIG);
        if (config == null || config.size() != 1) {
            return false;
        }

        return config.get(0).equalsIgnoreCase(REDIRECT_302_TO_WELL_KNOWN);
    }

    private Uri getLocationRedirectToWellKnownUri(Uri redirectUri) {
        return redirectUri
                .buildUpon()
                .encodedPath(WELL_KNOWN_PATH_SEGMENT)
                .clearQuery()
                .appendQueryParameter(WELL_KNOWN_QUERY_PARAM, redirectUri.toString())
                .build();
    }
}
