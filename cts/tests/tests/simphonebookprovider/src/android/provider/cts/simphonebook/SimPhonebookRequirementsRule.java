/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.provider.cts.simphonebook;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.Assume.assumeThat;

import android.content.Context;
import android.telephony.TelephonyManager;

import androidx.test.core.app.ApplicationProvider;

import com.android.compatibility.common.util.PollingCheck;

import org.junit.rules.ExternalResource;

class SimPhonebookRequirementsRule extends ExternalResource {
    private static final String TAG = "SimPhonebookRequirementsRule";

    private final int mMinimumSimCount;

    SimPhonebookRequirementsRule() {
        this(1);
    }

    SimPhonebookRequirementsRule(int minimumSimCount) {
        mMinimumSimCount = minimumSimCount;
    }

    @Override
    protected void before() {
        Context context = ApplicationProvider.getApplicationContext();
        TelephonyManager telephonyManager = context.getSystemService(TelephonyManager.class);

        // Skip the test if the device doesn't appear to have any multi-SIM capability. The checks
        // that follow this one are a bit flaky on devices that have an eSIM but don't support
        // DSDS or DSDA (e.g. crosshatch and blueline).
        assumeThat("not enough SIMs",
                telephonyManager.getSupportedModemCount(), greaterThanOrEqualTo(mMinimumSimCount));

        RemovableSims removableSims = new RemovableSims(context);
        assumeThat("Device does not have enough SIMs.",
                removableSims.getRemovableSimSlotCount(),
                greaterThanOrEqualTo(mMinimumSimCount));
        try {
            // Poll for a bit in case it takes a while for the SIMs to be initialized.
            PollingCheck.waitFor(30_000,
                    () -> removableSims
                            .getSubscriptionInfoForRemovableSims().size() >= mMinimumSimCount);
        } catch (AssertionError e) {
            // Swallowed here because the assert that follows validates the same thing.
        }
        assertWithMessage("A SIM must be installed in each SIM slot")
                .that(removableSims.getSubscriptionInfoForRemovableSims().size())
                .isAtLeast(mMinimumSimCount);
    }
}
