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

package com.google.uwb.support.ccc;

import android.os.Build.VERSION_CODES;
import android.os.PersistableBundle;
import android.uwb.RangingSession;

import androidx.annotation.RequiresApi;

import com.google.uwb.support.base.RequiredParam;

/**
 * Defines parameters for CCC reconfigure operation.
 *
 * <p>This is passed as a bundle to the service API {@link RangingSession#stop}.
 */
@RequiresApi(VERSION_CODES.LOLLIPOP)
public class CccRangingStoppedParams extends CccParams {

    private static final int BUNDLE_VERSION_1 = 1;
    private static final int BUNDLE_VERSION_CURRENT = BUNDLE_VERSION_1;

    private static final String KEY_LAST_STS_INDEX = "last_sts_index";

    private final int mLastStsIndexUsed;

    private CccRangingStoppedParams(Builder builder) {
        this.mLastStsIndexUsed = builder.mLastStsIndexUsed;
    }

    @Override
    protected int getBundleVersion() {
        return BUNDLE_VERSION_CURRENT;
    }

    @Override
    public PersistableBundle toBundle() {
        PersistableBundle bundle = super.toBundle();
        bundle.putInt(KEY_LAST_STS_INDEX, mLastStsIndexUsed);
        return bundle;
    }

    public static CccRangingStoppedParams fromBundle(PersistableBundle bundle) {
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

    public int getLastStsIndexUsed() {
        return mLastStsIndexUsed;
    }

    private static CccRangingStoppedParams parseVersion1(PersistableBundle bundle) {
        return new Builder()
            .setLastStsIndexUsed(bundle.getInt(KEY_LAST_STS_INDEX))
            .build();
    }

    /** Builder */
    public static class Builder {
        private int mLastStsIndexUsed = 0;

        public Builder setLastStsIndexUsed(int lastStsIndexUsed) {
            mLastStsIndexUsed = lastStsIndexUsed;
            return this;
        }

        public CccRangingStoppedParams build() {
            return new CccRangingStoppedParams(this);
        }
    }
}