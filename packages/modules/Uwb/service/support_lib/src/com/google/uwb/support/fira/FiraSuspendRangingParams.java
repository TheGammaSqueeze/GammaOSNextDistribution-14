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

package com.google.uwb.support.fira;

import com.google.uwb.support.fira.FiraParams.SuspendRanging;
import com.google.uwb.support.base.RequiredParam;

import android.os.PersistableBundle;

/**
 * UWB parameters used to pause/resume ranging
*
* <p>This is passed as a bundle to the service API {@link RangingSession#pause} and
* {@link RangingSession#resume}.
*/
public class FiraSuspendRangingParams extends FiraParams {
    private static final int BUNDLE_VERSION_1 = 1;
    private static final int BUNDLE_VERSION_CURRENT = BUNDLE_VERSION_1;

    @SuspendRanging private final int mSuspendRangingRounds;

    private static final String KEY_SUSPEND_RANGING_ROUNDS = "suspend_ranging_rounds";

    private FiraSuspendRangingParams(@SuspendRanging int suspendRangingRounds) {
        mSuspendRangingRounds = suspendRangingRounds;
    }

    @Override
    protected int getBundleVersion() {
        return BUNDLE_VERSION_CURRENT;
    }

    @SuspendRanging
    public int getSuspendRangingRounds() {
        return mSuspendRangingRounds;
    }

    @Override
    public PersistableBundle toBundle() {
        PersistableBundle bundle = super.toBundle();
        bundle.putInt(KEY_SUSPEND_RANGING_ROUNDS, mSuspendRangingRounds);
        return bundle;
    }

    public static FiraSuspendRangingParams fromBundle(PersistableBundle bundle) {
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

    private static FiraSuspendRangingParams parseVersion1(PersistableBundle bundle) {
        FiraSuspendRangingParams.Builder builder = new FiraSuspendRangingParams.Builder();

        builder.setSuspendRangingRounds(
                bundle.getInt(KEY_SUSPEND_RANGING_ROUNDS));
        return builder.build();
    }

    /** Builder */
    public static class Builder {
        private final RequiredParam<Integer> mSuspendRangingRounds = new RequiredParam<>();

        public FiraSuspendRangingParams.Builder setSuspendRangingRounds
                (@SuspendRanging int suspendRangingRounds) {
            mSuspendRangingRounds.set(suspendRangingRounds);
            return this;
        }

        public FiraSuspendRangingParams build() {
            return new FiraSuspendRangingParams(mSuspendRangingRounds.get());
        }
    }
}
