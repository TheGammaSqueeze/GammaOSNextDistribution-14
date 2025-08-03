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

package com.android.server.sdksandbox.verifier;

import android.annotation.NonNull;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.sdksandbox.proto.Verifier.AllowedApisList;
import com.android.server.sdksandbox.proto.Verifier.AllowedApisPerTargetSdk;

import com.google.protobuf.InvalidProtocolBufferException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.Map;

/**
 * Loads API allowlists that are used by the SdkDexVerifier to scan for disallowed API usages.
 *
 * @hide
 */
public class ApiAllowlistProvider {

    private String mAllowlistResource;

    public ApiAllowlistProvider() {
        mAllowlistResource = "platform_api_allowlist_per_target_sdk_version_current.binarypb";
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    public ApiAllowlistProvider(String allowlistResource) {
        mAllowlistResource = allowlistResource;
    }

    /** Loads current allowlists from pre bundled proto. */
    @NonNull
    public Map<Long, AllowedApisList> loadPlatformApiAllowlist()
            throws FileNotFoundException, InvalidProtocolBufferException, IOException {
        URL resourceURL = this.getClass().getClassLoader().getResource(mAllowlistResource);
        if (resourceURL == null) {
            throw new FileNotFoundException(mAllowlistResource + " not found.");
        }

        byte[] allowlistBytes = resourceURL.openStream().readAllBytes();

        AllowedApisPerTargetSdk allowedApisPerTargetSdk =
                AllowedApisPerTargetSdk.parseFrom(allowlistBytes);

        return allowedApisPerTargetSdk.getAllowlistPerTargetSdk();
    }
}
