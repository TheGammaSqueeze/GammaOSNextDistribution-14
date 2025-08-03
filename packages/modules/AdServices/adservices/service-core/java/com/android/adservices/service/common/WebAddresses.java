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

package com.android.adservices.service.common;

import android.net.Uri;

import com.google.common.net.InternetDomainName;

import java.util.Optional;

/** Web address utilities. */
public final class WebAddresses {

    private static final String HTTPS_SCHEME = "https";
    private static final String LOCALHOST = "localhost";
    private static final String LOCALHOST_IP = "127.0.0.1";

    private WebAddresses() { }

    /**
     * Returns a {@code Uri} of the scheme concatenated with the first subdomain of the provided URL
     * that is beneath the public suffix or localhost.
     *
     * @param uri the Uri to parse.
     */
    public static Optional<Uri> topPrivateDomainAndScheme(Uri uri) {
        return domainAndScheme(uri, false);
    }

    /**
     * Returns an origin of {@code Uri} that is defined by the concatenation of scheme (protocol),
     * hostname (domain), and port, validating public suffix or localhost.
     *
     * @param uri the Uri to parse.
     * @return
     */
    public static Optional<Uri> originAndScheme(Uri uri) {
        return domainAndScheme(uri, true);
    }

    /**
     * Returns an origin of {@code Uri} that is defined by the concatenation of scheme (protocol),
     * hostname (domain), and port if useOrigin is true. If useOrigin is false the method returns
     * the scheme concatenation of first subdomain that is beneath the public suffix or localhost.
     *
     * @param uri the Uri to parse
     * @param useOrigin true if extract origin, false if extract only top domain
     */
    private static Optional<Uri> domainAndScheme(Uri uri, boolean useOrigin) {
        String scheme = uri.getScheme();
        String host = uri.getHost();
        int port = uri.getPort();

        if (scheme == null || host == null) {
            return Optional.empty();
        }

        String url = scheme + "://";

        if (isLocalhost(uri)) {
            url += host;
        } else {
            try {
                InternetDomainName domainName = InternetDomainName.from(host);
                if (!domainName.hasPublicSuffix()) {
                    return Optional.empty();
                }
                url += useOrigin ? domainName.toString() : domainName.topPrivateDomain().toString();
            } catch (IllegalArgumentException | IllegalStateException e) {
                return Optional.empty();
            }
        }

        if (useOrigin && port >= 0) {
            url += ":" + port;
        }

        return Optional.of(Uri.parse(url));
    }

    /**
     * Determines if the provided URI is effectively localhost for Measurement CTS testing.
     *
     * @param uri the Uri to parse.
     */
    public static boolean isLocalhost(Uri uri) {
        String host = uri.getHost();
        return HTTPS_SCHEME.equals(uri.getScheme())
                && (LOCALHOST.equals(host) || LOCALHOST_IP.equals(host));
    }

    /**
     * Determines if the provided URI is the localhost IP for Measurement CTS testing.
     *
     * @param uri the Uri to parse.
     */
    public static boolean isLocalhostIp(Uri uri) {
        return HTTPS_SCHEME.equals(uri.getScheme()) && LOCALHOST_IP.equals(uri.getHost());
    }
}
