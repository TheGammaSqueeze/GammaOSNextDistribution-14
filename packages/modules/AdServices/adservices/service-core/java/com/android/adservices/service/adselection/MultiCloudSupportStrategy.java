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

package com.android.adservices.service.adselection;

import android.annotation.NonNull;
import android.content.Context;

import com.android.adservices.service.Flags;
import com.android.adservices.service.adselection.encryption.ObliviousHttpEncryptor;
import com.android.adservices.service.adselection.encryption.ProtectedServersEncryptionConfigManagerBase;
import com.android.adservices.service.common.CoordinatorOriginUriValidator;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;

/** Interface for strategy related to multi-cloud support */
public interface MultiCloudSupportStrategy {

    /**
     * Returns the {@link CoordinatorOriginUriValidator} to be used with the strategy to validate
     * the coordinator origin URI.
     */
    CoordinatorOriginUriValidator getCoordinatorOriginUriValidator();

    /**
     * Returns the {@link ProtectedServersEncryptionConfigManagerBase} with the given https client
     */
    ProtectedServersEncryptionConfigManagerBase getEncryptionConfigManager(
            @NonNull Context context,
            @NonNull Flags flags,
            @NonNull AdServicesHttpsClient adServicesHttpsClient);

    /** Returns an instance of {@link ObliviousHttpEncryptor} to encrypt the payload */
    // TODO(b/297025763) : Use process stable flags
    ObliviousHttpEncryptor getObliviousHttpEncryptor(
            @NonNull Context context, @NonNull Flags flags);
}
