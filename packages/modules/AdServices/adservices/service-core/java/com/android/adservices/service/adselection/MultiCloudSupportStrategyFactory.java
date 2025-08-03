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

import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.adselection.AdSelectionServerDatabase;
import com.android.adservices.service.Flags;
import com.android.adservices.service.adselection.encryption.AdSelectionEncryptionKeyManager;
import com.android.adservices.service.adselection.encryption.ObliviousHttpEncryptor;
import com.android.adservices.service.adselection.encryption.ObliviousHttpEncryptorImpl;
import com.android.adservices.service.adselection.encryption.ProtectedServersEncryptionConfigManager;
import com.android.adservices.service.adselection.encryption.ProtectedServersEncryptionConfigManagerBase;
import com.android.adservices.service.common.CoordinatorOriginUriValidator;
import com.android.adservices.service.common.cache.CacheProviderFactory;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;

/** Factory for {@link MultiCloudSupportStrategy} */
public class MultiCloudSupportStrategyFactory {

    private static class MultiCloudEnabledStrategy implements MultiCloudSupportStrategy {
        private final String mAllowlist;

        MultiCloudEnabledStrategy(String allowlist) {
            this.mAllowlist = allowlist;
        }

        @Override
        public CoordinatorOriginUriValidator getCoordinatorOriginUriValidator() {
            return CoordinatorOriginUriValidator.createEnabledInstance(mAllowlist);
        }

        @Override
        public ProtectedServersEncryptionConfigManagerBase getEncryptionConfigManager(
                @NonNull Context context,
                @NonNull Flags flags,
                @NonNull AdServicesHttpsClient adServicesHttpsClient) {
            return new ProtectedServersEncryptionConfigManager(
                    AdSelectionServerDatabase.getInstance(context)
                            .protectedServersEncryptionConfigDao(),
                    flags,
                    adServicesHttpsClient,
                    AdServicesExecutors.getLightWeightExecutor());
        }

        @Override
        // TODO(b/297025763) : Use process stable flags
        public ObliviousHttpEncryptor getObliviousHttpEncryptor(
                @NonNull Context context, @NonNull Flags flags) {
            return new ObliviousHttpEncryptorImpl(
                    getProtectedServersEncryptionConfigManager(context, flags),
                    AdSelectionServerDatabase.getInstance(context).encryptionContextDao(),
                    AdServicesExecutors.getLightWeightExecutor());
        }

        private ProtectedServersEncryptionConfigManagerBase
                getProtectedServersEncryptionConfigManager(
                        @NonNull Context context, @NonNull Flags flags) {
            AdServicesHttpsClient adServicesHttpsClient =
                    new AdServicesHttpsClient(
                            AdServicesExecutors.getBlockingExecutor(),
                            CacheProviderFactory.create(context, flags));
            return new ProtectedServersEncryptionConfigManager(
                    AdSelectionServerDatabase.getInstance(context)
                            .protectedServersEncryptionConfigDao(),
                    flags,
                    adServicesHttpsClient,
                    AdServicesExecutors.getLightWeightExecutor());
        }
    }

    private static class MultiCloudDisabledStrategy implements MultiCloudSupportStrategy {
        @Override
        public CoordinatorOriginUriValidator getCoordinatorOriginUriValidator() {
            return CoordinatorOriginUriValidator.createDisabledInstance();
        }

        @Override
        // TODO(b/297025763) : Use process stable flags
        public ObliviousHttpEncryptor getObliviousHttpEncryptor(
                @androidx.annotation.NonNull Context context,
                @androidx.annotation.NonNull Flags flags) {
            return new ObliviousHttpEncryptorImpl(
                    getProtectedServersEncryptionConfigManager(context, flags),
                    AdSelectionServerDatabase.getInstance(context).encryptionContextDao(),
                    AdServicesExecutors.getLightWeightExecutor());
        }

        @Override
        public ProtectedServersEncryptionConfigManagerBase getEncryptionConfigManager(
                @NonNull Context context,
                @NonNull Flags flags,
                @NonNull AdServicesHttpsClient adServicesHttpsClient) {
            return new AdSelectionEncryptionKeyManager(
                    AdSelectionServerDatabase.getInstance(context).encryptionKeyDao(),
                    flags,
                    adServicesHttpsClient,
                    AdServicesExecutors.getLightWeightExecutor());
        }

        private ProtectedServersEncryptionConfigManagerBase
                getProtectedServersEncryptionConfigManager(
                        @NonNull Context context, @NonNull Flags flags) {
            AdServicesHttpsClient adServicesHttpsClient =
                    new AdServicesHttpsClient(
                            AdServicesExecutors.getBlockingExecutor(),
                            CacheProviderFactory.create(context, flags));
            return new AdSelectionEncryptionKeyManager(
                    AdSelectionServerDatabase.getInstance(context).encryptionKeyDao(),
                    flags,
                    adServicesHttpsClient,
                    AdServicesExecutors.getLightWeightExecutor());
        }
    }

    /** Get the strategy corresponding to whether multi-cloud feature is enabled or not. */
    public static MultiCloudSupportStrategy getStrategy(
            boolean multiCloudEnabled, String allowlist) {
        if (multiCloudEnabled) {
            return new MultiCloudEnabledStrategy(allowlist);
        }

        return new MultiCloudDisabledStrategy();
    }


}
