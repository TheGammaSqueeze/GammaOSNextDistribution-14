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

package android.sdksandbox.test.scenario.testsdk;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

/** Utility class to help do operations for the client app. */
final class ClientAppUtils {
    private final String mPackageName;
    private final String mActivityName;

    private static final String LOAD_AD_BUTTON = "loadAdButton";
    private static final long UI_NAVIGATION_WAIT_MS = 1000;
    private static final int UI_WAIT_LOAD_AD_MS = 1000;
    private static final int UI_RETRIES_WAIT_LOAD_AD = 5;

    public static final String AD_LOADED_BUTTON_TEXT = "Load Ad (Ad loaded)";

    /** Returns the command for stopping the client app with {@code packageName}. */
    public static String getStopAppCommand(String packageName) {
        return "am force-stop " + packageName;
    }

    /**
     * Returns the command for starting the client app with/without mediation enabled.
     *
     * @param mediationEnabled for checking if mediation is enabled or disabled.
     */
    public String getStartAppCommand(boolean mediationEnabled) {
        return "am start -n "
                + mPackageName
                + "/"
                + mActivityName
                + " --ez mediation_enabled "
                + mediationEnabled;
    }

    /**
     * Finds the loadAd button.
     *
     * @param uiDevice is the device that is being used.
     * @return load ad button.
     */
    public UiObject2 getLoadAdButton(UiDevice uiDevice) {
        return uiDevice.wait(
                Until.findObject(By.res(mPackageName, LOAD_AD_BUTTON)), UI_NAVIGATION_WAIT_MS);
    }

    /**
     * Loads ad by finding the loadAd button and clicking on it.
     *
     * @param uiDevice is the device that is being used.
     * @throws RuntimeException if load ad button is not found.
     */
    public void loadAd(UiDevice uiDevice) throws RuntimeException {
        UiObject2 loadAdButton = getLoadAdButton(uiDevice);
        if (loadAdButton != null) {
            loadAdButton.click();
        } else {
            throw new RuntimeException("Did not find 'Load Ad' button.");
        }
    }

    /**
     * Assert ad is loaded.
     *
     * @param uiDevice is the device that is being used.
     */
    public void assertAdLoaded(UiDevice uiDevice) throws Exception {
        int retries = 0;
        boolean adLoaded = false;
        // wait until Ad Loaded.
        while (!adLoaded && retries < UI_RETRIES_WAIT_LOAD_AD) {
            Thread.sleep(UI_WAIT_LOAD_AD_MS);
            if (getLoadAdButton(uiDevice).getText().equals(ClientAppUtils.AD_LOADED_BUTTON_TEXT)) {
                adLoaded = true;
            }
            retries++;
        }

        assertThat(getLoadAdButton(uiDevice).getText())
                .isEqualTo(ClientAppUtils.AD_LOADED_BUTTON_TEXT);
    }

    /**
     * Constructor for {@link ClientAppUtils}.
     *
     * @param packageName is the package name for the client app.
     * @param activityName is the activity name to start for the test.
     */
    ClientAppUtils(String packageName, String activityName) {
        this.mPackageName = packageName;
        this.mActivityName = activityName;
    }
}
