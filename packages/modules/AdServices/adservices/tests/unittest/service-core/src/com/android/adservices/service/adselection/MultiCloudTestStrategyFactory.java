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

import android.content.Context;

import androidx.annotation.NonNull;

import com.android.adservices.service.Flags;
import com.android.adservices.service.adselection.encryption.ObliviousHttpEncryptor;
import com.android.adservices.service.adselection.encryption.ProtectedServersEncryptionConfigManagerBase;
import com.android.adservices.service.common.CoordinatorOriginUriValidator;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;

public class MultiCloudTestStrategyFactory {
    static class MultiCloudTestStrategyFlagOff implements MultiCloudSupportStrategy {

        private ObliviousHttpEncryptor mObliviousHttpEncryptor;

        MultiCloudTestStrategyFlagOff(ObliviousHttpEncryptor obliviousHttpEncryptor) {
            this.mObliviousHttpEncryptor = obliviousHttpEncryptor;
        }

        @Override
        public CoordinatorOriginUriValidator getCoordinatorOriginUriValidator() {
            return CoordinatorOriginUriValidator.createDisabledInstance();
        }

        @Override
        public ObliviousHttpEncryptor getObliviousHttpEncryptor(
                @NonNull Context context, @NonNull Flags flags) {
            return mObliviousHttpEncryptor;
        }

        @Override
        public ProtectedServersEncryptionConfigManagerBase getEncryptionConfigManager(
                @android.annotation.NonNull Context context,
                @android.annotation.NonNull Flags flags,
                @android.annotation.NonNull AdServicesHttpsClient adServicesHttpsClient) {
            return null;
        }
    }

    static class MultiCloudTestStrategyFlagOn implements MultiCloudSupportStrategy {

        private ObliviousHttpEncryptor mObliviousHttpEncryptor;
        private String mAllowList;

        MultiCloudTestStrategyFlagOn(
                ObliviousHttpEncryptor obliviousHttpEncryptor, String allowList) {
            this.mObliviousHttpEncryptor = obliviousHttpEncryptor;
            this.mAllowList = allowList;
        }

        @Override
        public CoordinatorOriginUriValidator getCoordinatorOriginUriValidator() {
            return CoordinatorOriginUriValidator.createEnabledInstance(mAllowList);
        }

        @Override
        public ProtectedServersEncryptionConfigManagerBase getEncryptionConfigManager(
                @android.annotation.NonNull Context context,
                @android.annotation.NonNull Flags flags,
                @android.annotation.NonNull AdServicesHttpsClient adServicesHttpsClient) {
            return null;
        }

        @Override
        public ObliviousHttpEncryptor getObliviousHttpEncryptor(
                @NonNull Context context, @NonNull Flags flags) {
            return mObliviousHttpEncryptor;
        }
    }

    /** Get a test strategy with multi cloud enabled */
    public static MultiCloudSupportStrategy getEnabledTestStrategy(
            ObliviousHttpEncryptor encryptor, String allowlist) {
        return new MultiCloudTestStrategyFlagOn(encryptor, allowlist);
    }

    /** Get a test strategy with multi cloud disabled */
    public static MultiCloudSupportStrategy getDisabledTestStrategy(
            ObliviousHttpEncryptor encryptor) {
        return new MultiCloudTestStrategyFlagOff(encryptor);
    }
}
