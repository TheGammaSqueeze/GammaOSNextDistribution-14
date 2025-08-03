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

package com.android.adservices.service.adselection;

import com.android.adservices.service.Flags;

public class FlagsWithOverriddenFledgeChecks implements Flags {
    private final boolean mEnabled;

    public static Flags createFlagsWithFledgeChecksEnabled() {
        return new FlagsWithOverriddenFledgeChecks(true);
    }

    public static Flags createFlagsWithFledgeChecksDisabled() {
        return new FlagsWithOverriddenFledgeChecks(false);
    }

    FlagsWithOverriddenFledgeChecks(boolean enabled) {
        this.mEnabled = enabled;
    }

    @Override
    public boolean getEnforceForegroundStatusForFledgeRunAdSelection() {
        return mEnabled;
    }

    @Override
    public boolean getEnforceForegroundStatusForFledgeReportImpression() {
        return mEnabled;
    }

    @Override
    public boolean getEnforceForegroundStatusForFledgeOverrides() {
        return mEnabled;
    }

    @Override
    public boolean getDisableFledgeEnrollmentCheck() {
        return !mEnabled;
    }
}
