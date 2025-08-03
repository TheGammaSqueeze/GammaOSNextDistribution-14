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

package android.cts.install.host;

import static com.google.common.truth.Truth.assertThat;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.rules.ExternalResource;

/**
 * Changes default launcher with {@link #mTestLauncher} while testing. Restores to default
 * launcher after test finished.
 */
final class LauncherRule extends ExternalResource {
    private final BaseHostJUnit4Test mHostTest;
    private final String mTestLauncher;

    /**
     * This value will be used to reset the default launcher to its correct component upon test
     * completion.
     */
    private String mDefaultLauncher = null;

    /**
     * Constructs {@link LauncherRule} instance.
     *
     * @param hostTest Test to apply this rule.
     * @param testLauncher Component name of customized launcher to set as default while testing.
     */
    LauncherRule(BaseHostJUnit4Test hostTest, String testLauncher) {
        mHostTest = hostTest;
        mTestLauncher = testLauncher;
    }


    protected void before() throws Throwable {
        mDefaultLauncher = fetchDefaultLauncher();
        setDefaultLauncher(mTestLauncher);
    }

    protected void after() {
        try {
            setDefaultLauncher(mDefaultLauncher);
        } catch (DeviceNotAvailableException e) {
            e.printStackTrace();
        }
    }

    /** Fetches the component name of the default launcher. Assert error if no launcher found. */
    private String fetchDefaultLauncher() throws DeviceNotAvailableException {
        final String PREFIX = "Launcher: ComponentInfo{";
        final String POSTFIX = "}";
        for (String s : mHostTest.getDevice().executeShellCommand(
                "cmd shortcut get-default-launcher").split("\n")) {
            if (s.startsWith(PREFIX) && s.endsWith(POSTFIX)) {
                return s.substring(PREFIX.length(), s.length() - POSTFIX.length());
            }
        }
        throw new AssertionError("No default launcher found");
    }

    /**
     * Set the default launcher to a given component.
     * If set to the broadcast receiver component of this test app, this will allow the test app to
     * receive SESSION_COMMITTED broadcasts.
     */
    private void setDefaultLauncher(String launcherComponent) throws DeviceNotAvailableException {
        assertThat(launcherComponent).isNotEmpty();
        mHostTest.getDevice().executeShellCommand(
                "cmd package set-home-activity " + launcherComponent);
    }
}
