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

package com.android.sts.common;

import com.android.sts.common.util.TombstoneUtils;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;

public class NativePocCrashAsserter implements NativePocAsserter {
    private TombstoneUtils.Config tombstoneConfig;
    private final boolean checkPocCrashes;

    /** Returns a NativePocAsserter that checks the listed processes for any security crashes. */
    public static NativePocAsserter assertNoCrashIn(String... patterns) {
        return new NativePocCrashAsserter(
                new TombstoneUtils.Config().setProcessPatterns(patterns), false);
    }

    /** Returns a NativePocAsserter that makes sure the Poc does not have a security crash. */
    public static NativePocAsserter assertNoCrash() {
        return new NativePocCrashAsserter(new TombstoneUtils.Config(), true);
    }

    /**
     * Returns a NativePocAsserter that makes sure there is no security crash detected accoridng to
     * the given TombstoneUtils.Config
     */
    public static NativePocAsserter assertNoCrash(TombstoneUtils.Config config) {
        return new NativePocCrashAsserter(config, false);
    }

    private NativePocCrashAsserter(TombstoneUtils.Config config, boolean checkPocCrashes) {
        this.tombstoneConfig = config;
        this.checkPocCrashes = checkPocCrashes;
    }

    @Override
    public AutoCloseable withAutoCloseable(NativePoc nativePoc, ITestDevice device)
            throws DeviceNotAvailableException {
        if (checkPocCrashes) {
            tombstoneConfig.setProcessPatterns(nativePoc.pocName());
        }
        return TombstoneUtils.withAssertNoSecurityCrashes(device, tombstoneConfig);
    }
}
