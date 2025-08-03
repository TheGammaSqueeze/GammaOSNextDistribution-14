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

/** FiRa status code defined in Table 32 */
public class FiraStatusCode extends FiraParams {
    private static final int BUNDLE_VERSION_1 = 1;
    private static final int BUNDLE_VERSION_CURRENT = BUNDLE_VERSION_1;

    @StatusCode private final int mStatusCode;

    private static final String KEY_STATUS_CODE = "status_code";

    private FiraStatusCode(@StatusCode int statusCode) {
        mStatusCode = statusCode;
    }

    @Override
    protected int getBundleVersion() {
        return BUNDLE_VERSION_CURRENT;
    }

    @StatusCode
    public int getStatusCode() {
        return mStatusCode;
    }

    @Override
    public PersistableBundle toBundle() {
        PersistableBundle bundle = super.toBundle();
        bundle.putInt(KEY_STATUS_CODE, mStatusCode);
        return bundle;
    }

    public static FiraStatusCode fromBundle(PersistableBundle bundle) {
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

    private static FiraStatusCode parseVersion1(PersistableBundle bundle) {
        return new FiraStatusCode.Builder().setStatusCode(bundle.getInt(KEY_STATUS_CODE)).build();
    }

    public static boolean isBundleValid(PersistableBundle bundle) {
        return bundle.containsKey(KEY_STATUS_CODE);
    }

    /** Builder */
    public static class Builder {
        private final RequiredParam<Integer> mStatusCode = new RequiredParam<>();

        public FiraStatusCode.Builder setStatusCode(int statusCode) {
            mStatusCode.set(statusCode);
            return this;
        }

        public FiraStatusCode build() {
            return new FiraStatusCode(mStatusCode.get());
        }
    }
}
