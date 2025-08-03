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
package android.support.test.launcherhelper2;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiObject2;

public class Launcher3Strategy extends BaseLauncher3Strategy {

    private static final String LAUNCHER_PKG = "com.android.launcher3";

    /** {@inheritDoc} */
    @Override
    public String getSupportedLauncherPackage() {
        return LAUNCHER_PKG;
    }

    /** {@inheritDoc} */
    @Override
    protected void dismissHomeScreenCling() {
        super.dismissHomeScreenCling();
        // dismiss first run cling
        UiObject2 gotItButton =
                mDevice.findObject(
                        By.res(getSupportedLauncherPackage(), "cling_dismiss_longpress_info"));
        if (gotItButton != null) {
            gotItButton.click();
        }
    }

    /** {@inheritDoc} */
    @Override
    public BySelector getAllAppsButtonSelector() {
        return By.res(getSupportedLauncherPackage(), "all_apps_handle");
    }
}
