/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.google.uwb.support.fira;

import android.os.PersistableBundle;

import com.google.uwb.support.base.RequiredParam;

/** FiRa Multicast List update status code defined in UCI 1.0 Table 27 */
public class FiraMulticastListUpdateStatusCode extends FiraParams {
    private static final int BUNDLE_VERSION_1 = 1;
    private static final int BUNDLE_VERSION_CURRENT = BUNDLE_VERSION_1;

    @MulticastListUpdateStatus private final int mStatusCode;

    private static final String KEY_STATUS_CODE = "multicast_list_update_status_code";

    private FiraMulticastListUpdateStatusCode(@MulticastListUpdateStatus int statusCode) {
        mStatusCode = statusCode;
    }

    @Override
    protected int getBundleVersion() {
        return BUNDLE_VERSION_CURRENT;
    }

    @MulticastListUpdateStatus
    public int getStatusCode() {
        return mStatusCode;
    }

    @Override
    public PersistableBundle toBundle() {
        PersistableBundle bundle = super.toBundle();
        bundle.putInt(KEY_STATUS_CODE, mStatusCode);
        return bundle;
    }

    public static FiraMulticastListUpdateStatusCode fromBundle(PersistableBundle bundle) {
        if (!isCorrectProtocol(bundle)) {
            throw new IllegalArgumentException("Invalid protocol");
        }

        switch (getBundleVersion(bundle)) {
            case BUNDLE_VERSION_1:
                return parseVersion1(bundle);

            default:
                throw new IllegalArgumentException("Invalid bundle version");
        }
    }

    public static boolean isBundleValid(PersistableBundle bundle) {
        return bundle.containsKey(KEY_STATUS_CODE);
    }

    private static FiraMulticastListUpdateStatusCode parseVersion1(PersistableBundle bundle) {
        return new FiraMulticastListUpdateStatusCode.Builder()
                .setStatusCode(bundle.getInt(KEY_STATUS_CODE))
                .build();
    }

    /** Builder */
    public static class Builder {
        private final RequiredParam<Integer> mStatusCode = new RequiredParam<>();

        public FiraMulticastListUpdateStatusCode.Builder setStatusCode(int statusCode) {
            mStatusCode.set(statusCode);
            return this;
        }

        public FiraMulticastListUpdateStatusCode build() {
            return new FiraMulticastListUpdateStatusCode(mStatusCode.get());
        }
    }
}
