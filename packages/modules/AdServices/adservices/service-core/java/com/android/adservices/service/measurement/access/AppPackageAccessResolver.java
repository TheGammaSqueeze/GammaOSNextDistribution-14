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

package com.android.adservices.service.measurement.access;

import static android.adservices.common.AdServicesStatusUtils.FAILURE_REASON_PACKAGE_BLOCKLISTED;
import static android.adservices.common.AdServicesStatusUtils.FAILURE_REASON_PACKAGE_NOT_IN_ALLOWLIST;
import static android.adservices.common.AdServicesStatusUtils.FAILURE_REASON_UNSET;

import android.adservices.common.AdServicesStatusUtils;
import android.annotation.NonNull;
import android.content.Context;

import com.android.adservices.service.common.AllowLists;

/** Used for web context and general API access APIs. Checks if the package has access to them. */
public class AppPackageAccessResolver implements IAccessResolver {
    private static final String ERROR_MESSAGE = "Package %s is not allowed to call the API.";
    private final String mAllowList;
    private final String mBlockList;
    private final String mPackageName;

    public AppPackageAccessResolver(
            String allowList, String blockList, @NonNull String packageName) {
        mAllowList = allowList == null ? AllowLists.ALLOW_NONE : allowList;
        mBlockList = blockList == null ? AllowLists.ALLOW_NONE : blockList;
        mPackageName = packageName;
    }

    @Override
    public AccessInfo getAccessInfo(@NonNull Context context) {
        boolean isInAllowList = AllowLists.isPackageAllowListed(mAllowList, mPackageName);
        boolean isInBlockList = isBlocked();
        if (isInAllowList && !isInBlockList) {
            return new AccessInfo(true, FAILURE_REASON_UNSET);
        }
        return new AccessInfo(
                false,
                !isInAllowList
                        ? FAILURE_REASON_PACKAGE_NOT_IN_ALLOWLIST
                        : FAILURE_REASON_PACKAGE_BLOCKLISTED);
    }

    @NonNull
    @Override
    @AdServicesStatusUtils.StatusCode
    public int getErrorStatusCode() {
        return AdServicesStatusUtils.STATUS_CALLER_NOT_ALLOWED;
    }

    @NonNull
    @Override
    public String getErrorMessage() {
        return String.format(ERROR_MESSAGE, mPackageName);
    }

    private boolean isBlocked() {
        // Allowlist is misnomer in this case. isBlocked method for code clarity.
        return AllowLists.isPackageAllowListed(mBlockList, mPackageName);
    }
}
