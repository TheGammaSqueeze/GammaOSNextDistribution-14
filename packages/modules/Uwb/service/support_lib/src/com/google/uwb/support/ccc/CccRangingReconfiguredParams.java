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

package com.google.uwb.support.ccc;

import android.os.Build.VERSION_CODES;
import android.os.PersistableBundle;
import android.uwb.RangingSession;

import androidx.annotation.RequiresApi;

/**
 * Defines parameters for CCC reconfigure operation
 *
 * <p>This is passed as a bundle to the client callback
 * {@link RangingSession.Callback#onReconfigured}.
 */
@RequiresApi(VERSION_CODES.LOLLIPOP)
public class CccRangingReconfiguredParams extends CccParams {

    private static final int BUNDLE_VERSION_1 = 1;
    private static final int BUNDLE_VERSION_CURRENT = BUNDLE_VERSION_1;

    @Override
    protected int getBundleVersion() {
        return BUNDLE_VERSION_CURRENT;
    }

    public static CccRangingReconfiguredParams fromBundle(PersistableBundle bundle) {
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

    private static CccRangingReconfiguredParams parseVersion1(PersistableBundle unusedBundle) {
        // Nothing to parse for now
        return new CccRangingReconfiguredParams.Builder().build();
    }

    /** Builder */
    public static class Builder {
        public CccRangingReconfiguredParams build() {
            return new CccRangingReconfiguredParams();
        }
    }
}
