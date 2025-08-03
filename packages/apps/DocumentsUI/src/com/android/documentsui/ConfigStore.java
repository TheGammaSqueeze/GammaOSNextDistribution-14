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

package com.android.documentsui;

import android.os.Binder;
import android.provider.DeviceConfig;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.android.modules.utils.build.SdkLevel;

import com.google.common.base.Supplier;

public interface ConfigStore {
    // TODO(b/288066342): Remove and replace after new constant definition in
    //  {@link android.provider.DeviceConfig}.
    String NAMESPACE_MEDIAPROVIDER = "mediaprovider";

    boolean DEFAULT_PICKER_PRIVATE_SPACE_ENABLED = false;

    /**
     * @return if the Private-Space-in-DocsUI is enabled
     */
    default boolean isPrivateSpaceInDocsUIEnabled() {
        return DEFAULT_PICKER_PRIVATE_SPACE_ENABLED;
    }

    /**
     * Implementation of the {@link ConfigStore} that reads "real" configs from
     * {@link android.provider.DeviceConfig}. Meant to be used by the "production" code.
     */
    class ConfigStoreImpl implements ConfigStore {
        @VisibleForTesting
        public static final String KEY_PRIVATE_SPACE_FEATURE_ENABLED =
                "private_space_feature_enabled";

        private static final boolean sCanReadDeviceConfig = SdkLevel.isAtLeastS();

        private Boolean mIsPrivateSpaceEnabled = null;

        @Override
        public boolean isPrivateSpaceInDocsUIEnabled() {
            if (mIsPrivateSpaceEnabled == null) {
                mIsPrivateSpaceEnabled = getBooleanDeviceConfig(
                        NAMESPACE_MEDIAPROVIDER,
                        KEY_PRIVATE_SPACE_FEATURE_ENABLED,
                        DEFAULT_PICKER_PRIVATE_SPACE_ENABLED);
            }
            return sCanReadDeviceConfig && mIsPrivateSpaceEnabled;
        }

        private static boolean getBooleanDeviceConfig(@NonNull String namespace,
                @NonNull String key, boolean defaultValue) {
            if (!sCanReadDeviceConfig) {
                return defaultValue;
            }
            return withCleanCallingIdentity(
                    () -> DeviceConfig.getBoolean(namespace, key, defaultValue));
        }

        private static <T> T withCleanCallingIdentity(@NonNull Supplier<T> action) {
            final long callingIdentity = Binder.clearCallingIdentity();
            try {
                return action.get();
            } finally {
                Binder.restoreCallingIdentity(callingIdentity);
            }
        }
    }
}
