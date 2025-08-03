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

package com.google.uwb.support.profile;

import android.os.PersistableBundle;

import java.util.Optional;
import java.util.UUID;

/* UWB return status */
public class UuidBundleWrapper {

    private static final int BUNDLE_VERSION_1 = 1;
    private static final int BUNDLE_VERSION_CURRENT = BUNDLE_VERSION_1;

    private final Optional<UUID> mServiceInstanceID;

    public static final String KEY_BUNDLE_VERSION = "bundle_version";
    public static final String  SERVICE_INSTANCE_ID = "service_instance_id";

    public UuidBundleWrapper(Optional<UUID> serviceInstanceID) {
        mServiceInstanceID = serviceInstanceID;
    }

    public static int getBundleVersion() {
        return BUNDLE_VERSION_CURRENT;
    }

    public Optional<UUID> getServiceInstanceID() {
        return mServiceInstanceID;
    }

    public PersistableBundle toBundle() {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putInt(KEY_BUNDLE_VERSION, getBundleVersion());
        if (mServiceInstanceID.isPresent()) {
            bundle.putString(SERVICE_INSTANCE_ID, mServiceInstanceID.get().toString());
        }
        return bundle;
    }

    public static boolean isUuidBundle(PersistableBundle bundle) {
        return bundle.containsKey(SERVICE_INSTANCE_ID);
    }

    public static UuidBundleWrapper fromBundle(PersistableBundle bundle) {
        switch (bundle.getInt(KEY_BUNDLE_VERSION)) {
            case BUNDLE_VERSION_1:
                return parseVersion1(bundle);

            default:
                throw new IllegalArgumentException("Invalid bundle version");
        }
    }

    private static UuidBundleWrapper parseVersion1(PersistableBundle bundle) {
        UuidBundleWrapper.Builder builder =  new UuidBundleWrapper.Builder();
        if (bundle.containsKey(SERVICE_INSTANCE_ID)) {
            builder.setServiceInstanceID(Optional.of(UUID.fromString(
                    bundle.getString(SERVICE_INSTANCE_ID))));
        }
        return builder.build();
    }

    /** Builder */
    public static class Builder {
        private Optional<UUID> mServiceInstanceID = Optional.empty();

        public UuidBundleWrapper.Builder setServiceInstanceID(Optional<UUID> serviceInstanceID) {
            mServiceInstanceID = serviceInstanceID;
            return this;
        }

        public UuidBundleWrapper build() {
            return new UuidBundleWrapper(mServiceInstanceID);
        }
    }
}
