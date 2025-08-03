/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.internal.annotations.VisibleForTesting;

import java.util.List;
import java.util.Objects;

/**
 * Validates whether a coordinator origin URI is valid.
 *
 * <p>The origin must contain only scheme and hostname and must belong to the allowlist.
 */
public interface CoordinatorOriginUriValidator extends Validator<Uri> {
    @VisibleForTesting
    String URI_SHOULD_HAVE_PRESENT_HOST = "The coordinator origin uri should have present host.";

    @VisibleForTesting String URI_SHOULD_USE_HTTPS = "The coordinator origin uri should use HTTPS.";

    @VisibleForTesting
    String URI_SHOULD_NOT_HAVE_PATH = "The coordinator origin uri should not have a path.";

    @VisibleForTesting
    String URI_SHOULD_BELONG_TO_ALLOWLIST =
            "The coordinator origin uri should belong to the allowlist.";

    CoordinatorOriginUriValidator VALIDATOR_NO_OP = (object, violations) -> {};

    /** Creates an instance of the disabled validator */
    static CoordinatorOriginUriValidator createDisabledInstance() {
        return VALIDATOR_NO_OP;
    }

    /** Creates an instance of an enabled validator */
    static CoordinatorOriginUriValidator createEnabledInstance(String allowlist) {
        return (uri, violations) -> {
            if (!Objects.isNull(uri)) {
                if (ValidatorUtil.isStringNullOrEmpty(uri.getHost())) {
                    violations.add(URI_SHOULD_HAVE_PRESENT_HOST);
                } else if (!ValidatorUtil.HTTPS_SCHEME.equalsIgnoreCase(uri.getScheme())) {
                    violations.add(URI_SHOULD_USE_HTTPS);
                } else if (!ValidatorUtil.isStringNullOrEmpty(uri.getPath())) {
                    violations.add(URI_SHOULD_NOT_HAVE_PATH);
                } else if (!isUrlAllowListed(allowlist, uri)) {
                    violations.add(URI_SHOULD_BELONG_TO_ALLOWLIST);
                }
            }
        };
    }

    private static boolean isUrlAllowListed(String allowlist, Uri uri) {
        List<String> allowedUrls = AllowLists.splitAllowList(allowlist);

        for (String url : allowedUrls) {
            Uri allowedUri = Uri.parse(url);
            if (uri.getHost().equals(allowedUri.getHost())) {
                return true;
            }
        }

        return false;
    }
}
