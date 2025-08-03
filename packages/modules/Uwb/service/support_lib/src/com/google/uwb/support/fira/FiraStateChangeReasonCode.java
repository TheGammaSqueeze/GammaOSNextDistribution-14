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

/** FiRa State Change code defined in UCI 1.2 Table 15 */
public class FiraStateChangeReasonCode extends FiraParams {
    private static final int BUNDLE_VERSION_1 = 1;
    private static final int BUNDLE_VERSION_CURRENT = BUNDLE_VERSION_1;

    @StateChangeReasonCode private final int mReasonCode;

    private static final String KEY_REASON_CODE = "reason_code";

    private FiraStateChangeReasonCode(@StateChangeReasonCode int reasonCode) {
        mReasonCode = reasonCode;
    }

    @Override
    protected int getBundleVersion() {
        return BUNDLE_VERSION_CURRENT;
    }

    @StateChangeReasonCode
    public int getReasonCode() {
        return mReasonCode;
    }

    @Override
    public PersistableBundle toBundle() {
        PersistableBundle bundle = super.toBundle();
        bundle.putInt(KEY_REASON_CODE, mReasonCode);
        return bundle;
    }

    public static FiraStateChangeReasonCode fromBundle(PersistableBundle bundle) {
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

    private static FiraStateChangeReasonCode parseVersion1(PersistableBundle bundle) {
        return new FiraStateChangeReasonCode.Builder()
                .setReasonCode(bundle.getInt(KEY_REASON_CODE))
                .build();
    }

    public static boolean isBundleValid(PersistableBundle bundle) {
        return bundle.containsKey(KEY_REASON_CODE);
    }

    /** Builder */
    public static class Builder {
        private final RequiredParam<Integer> mReasonCode = new RequiredParam<>();

        public FiraStateChangeReasonCode.Builder setReasonCode(int reasonCode) {
            mReasonCode.set(reasonCode);
            return this;
        }

        public FiraStateChangeReasonCode build() {
            return new FiraStateChangeReasonCode(mReasonCode.get());
        }
    }
}
