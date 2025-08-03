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

import com.google.uwb.support.base.RequiredParam;
import com.google.uwb.support.fira.FiraParams.ServiceID;

/** UWB service configuration for FiRa profile. */
public class ServiceProfile {
    private static final int BUNDLE_VERSION_1 = 1;
    private static final int BUNDLE_VERSION_CURRENT = BUNDLE_VERSION_1;

    @ServiceID
    private final int mServiceID;
    public static final String KEY_BUNDLE_VERSION = "bundle_version";
    public static final String SERVICE_ID = "service_id";

    public static int getBundleVersion() {
        return BUNDLE_VERSION_CURRENT;
    }

    @ServiceID
    public int getServiceID() {
        return mServiceID;
    }

    public ServiceProfile(int serviceID) {
        mServiceID = serviceID;
    }

    public PersistableBundle toBundle() {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putInt(KEY_BUNDLE_VERSION, getBundleVersion());
        bundle.putInt(SERVICE_ID, mServiceID);
        return bundle;
    }

    public static ServiceProfile fromBundle(PersistableBundle bundle) {
        switch (bundle.getInt(KEY_BUNDLE_VERSION)) {
            case BUNDLE_VERSION_1:
                return parseVersion1(bundle);

            default:
                throw new IllegalArgumentException("Invalid bundle version");
        }
    }

    private static ServiceProfile parseVersion1(PersistableBundle bundle) {
        return new ServiceProfile.Builder()
                .setServiceID(bundle.getInt(SERVICE_ID))
                .build();
    }

    /** Builder */
    public static class Builder {
        private final RequiredParam<Integer> mServiceID = new RequiredParam<>();

        public ServiceProfile.Builder setServiceID(int serviceID) {
            mServiceID.set(serviceID);
            return this;
        }

        public ServiceProfile build() {
            return new ServiceProfile(
                    mServiceID.get());
        }
    }
}
