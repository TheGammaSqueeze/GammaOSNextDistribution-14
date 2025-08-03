/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.platform.test.rule;

import android.os.SystemClock;

import androidx.annotation.VisibleForTesting;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Until;

import org.junit.runner.Description;

/** This rule opens apps then goes to home before a test class. */
public class QuickstepPressureRule extends TestWatcher {
    // TODO: b/243991517 Replace static sleep with process not crashing verification.
    private static final long MIN_CRASH_WAIT_TIMEOUT = 500;
    private static final long UI_RESPONSE_TIMEOUT_MSECS = 10000;
    @VisibleForTesting static final String PACKAGES_OPTION = "quickstep-packages";

    private String[] mPackages;

    public QuickstepPressureRule(String... packages) {
        mPackages = packages;
    }

    @Override
    protected void starting(Description description) {
        String packageOption = getArguments().getString(PACKAGES_OPTION);
        mPackages = packageOption == null ? mPackages : packageOption.split(",");
        if (mPackages.length == 0) {
            throw new IllegalArgumentException("Must supply an application to open.");
        }

        // Start each app in sequence.
        for (String pkg : mPackages) {
            startActivity(pkg);
        }

        // Press the home button.
        getUiDevice().pressHome();
    }

    /** Launches the specified activity and keeps it open briefly. */
    @VisibleForTesting
    void startActivity(String pkg) {
        // Open the application and ensure it reaches the foreground.
        getContext().startActivity(getContext().getPackageManager().getLaunchIntentForPackage(pkg));
        if (!getUiDevice().wait(Until.hasObject(By.pkg(pkg).depth(0)), UI_RESPONSE_TIMEOUT_MSECS)) {
            throw new RuntimeException(
                    String.format("Application not found in foreground (package = %s).", pkg));
        }
        // Ensure the app doesn't immediately crash in the foreground.
        SystemClock.sleep(MIN_CRASH_WAIT_TIMEOUT);
    }
}
