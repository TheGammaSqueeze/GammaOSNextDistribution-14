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

import android.app.Instrumentation;
import android.system.helpers.ActivityHelper;

import androidx.test.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import org.junit.Assert;

public class AllAppsScreenHelper {

    private static final int TIMEOUT = 3000;
    private static final int LONG_TIMEOUT = 10000;
    private UiDevice mDevice;
    private Instrumentation mInstrumentation;
    private ILauncherStrategy mLauncherStrategy =
            LauncherStrategyFactory.getInstance(mDevice).getLauncherStrategy();
    private String allApps = "apps_view";
    private String appsListView = "apps_list_view";
    private String searchBox = "search_box_input";
    private ActivityHelper mActivityHelper;

    public AllAppsScreenHelper() {
        try {
            mInstrumentation = InstrumentationRegistry.getInstrumentation();
        } catch (IllegalStateException e) {
            mInstrumentation = androidx.test.InstrumentationRegistry.getInstrumentation();
        }
        mActivityHelper = ActivityHelper.getInstance();
        mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    }

    public String getLauncherPackage() {
        return mDevice.getLauncherPackageName();
    }

    public void launchAllAppsScreen() {
        mDevice.pressHome();
        mLauncherStrategy.openAllApps(false);
        mDevice.wait(Until.hasObject(By.res(getLauncherPackage(), allApps)), TIMEOUT);
    }

    public void searchAllAppsScreen(String searchString, String[] appNamesExpected)
            throws Exception {
        launchAllAppsScreen();
        UiObject2 searchBoxObject =
                mDevice.wait(Until.findObject(By.res(getLauncherPackage(), searchBox)), TIMEOUT);
        searchBoxObject.setText(searchString);
        for (String appName : appNamesExpected) {
            Assert.assertNotNull(
                    "The following app couldn't be found in the search results: " + appName,
                    mDevice.wait(Until.findObject(By.text(appName)), TIMEOUT));
        }
    }
}
