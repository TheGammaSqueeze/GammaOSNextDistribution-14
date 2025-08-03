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

import android.os.RemoteException;
import android.widget.Button;
import android.widget.TextView;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import junit.framework.Assert;

/** Implementation of {@link ILauncherStrategy} to support AOSP launcher */
public class AospLauncherStrategy implements ILauncherStrategy {

    private static final String LAUNCHER_PKG = "com.android.launcher";
    private static final BySelector APPS_CONTAINER =
            By.res(LAUNCHER_PKG, "apps_customize_pane_content");
    private static final BySelector OVERVIEW = By.res(LAUNCHER_PKG, "overview_panel");
    private static final BySelector WORKSPACE = By.res(LAUNCHER_PKG, "workspace");
    private static final BySelector HOTSEAT = By.res(LAUNCHER_PKG, "hotseat");
    private UiDevice mDevice;

    /** {@inheritDoc} */
    @Override
    public void open() {
        // if we see hotseat, assume at home screen already
        if (!mDevice.hasObject(getHotSeatSelector())) {
            mDevice.pressHome();
            Assert.assertTrue(
                    "Failed to open launcher",
                    mDevice.wait(Until.hasObject(By.pkg(LAUNCHER_PKG)), 5000));
            mDevice.waitForIdle();
        }
        // remove cling if it exists
        UiObject2 cling = mDevice.findObject(By.res(LAUNCHER_PKG, "workspace_cling"));
        if (cling != null) {
            cling.findObject(By.clazz(Button.class).text("OK")).click();
        }
    }

    /** {@inheritDoc} */
    @Override
    public UiObject2 openAllApps(boolean reset) {
        // if we see apps container, skip the opening step, only ensure that the "Apps" tab is
        // selected
        if (!mDevice.hasObject(APPS_CONTAINER)) {
            open();
            // taps on the "apps" button at the bottom of the screen
            mDevice.findObject(By.desc("Apps")).click();
            // wait until hotseat disappears, so that we know that we are no longer on home screen
            mDevice.wait(Until.gone(getHotSeatSelector()), 2000);
            mDevice.waitForIdle();
        }
        // check if there's a "cling" on screen
        UiObject2 cling =
                mDevice.findObject(
                        By.res(LAUNCHER_PKG, "cling_dismiss").clazz(Button.class).text("OK"));
        if (cling != null) {
            cling.click();
        }
        // taps on the "apps" page selector near the top of the screen
        UiObject2 appsTab =
                mDevice.findObject(By.desc("Apps").clazz(TextView.class).selected(false));
        if (appsTab != null) {
            appsTab.click();
        }
        UiObject2 allAppsContainer = mDevice.findObject(APPS_CONTAINER);
        Assert.assertNotNull("openAllApps: did not find all apps container", allAppsContainer);
        if (reset) {
            CommonLauncherHelper.getInstance(mDevice)
                    .scrollBackToBeginning(
                            allAppsContainer, Direction.reverse(getAllAppsScrollDirection()));
        }
        return allAppsContainer;
    }

    /** {@inheritDoc} */
    @Override
    public void openOverview() {
        try {
            mDevice.pressRecentApps();
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }

        // use a long timeout to wait until recents populated
        UiObject2 overviewScreen = mDevice.wait(Until.findObject(getOverviewSelector()), 2000);
        Assert.assertNotNull("openOverview: did not find overview", overviewScreen);
    }

    /** {@inheritDoc} */
    @Override
    public boolean clearRecentAppsFromOverview() {
        for (int i = 0; i < 10; i++) {
            mDevice.swipe(
                    mDevice.getDisplayWidth() / 2,
                    mDevice.getDisplayHeight() / 2,
                    mDevice.getDisplayWidth(),
                    mDevice.getDisplayHeight() / 2,
                    5);

            BySelector noRecentItemsSelector = getOverviewSelector().desc("No recent items");
            UiObject2 noRecentItems = mDevice.wait(Until.findObject(noRecentItemsSelector), 100);

            // If "No recent items"  is displayed, there're no apps to remove
            if (noRecentItems != null) {
                return true;
            }

            // If "Clear all"  button appears, use it
            BySelector clearAllSelector = By.res(mDevice.getLauncherPackageName(), "clear_all");
            UiObject2 clearAllButton = mDevice.wait(Until.findObject(clearAllSelector), 100);
            if (clearAllButton != null) {
                clearAllButton.click();
                return true;
            }
        }

        return false;
    }

    /** {@inheritDoc} */
    @Override
    public Direction getAllAppsScrollDirection() {
        return Direction.RIGHT;
    }

    /** {@inheritDoc} */
    @Override
    public UiObject2 openAllWidgets(boolean reset) {
        boolean needReset = true;
        // if we see apps container, skip the opening step, only ensure that the "Widgets" tab is
        // selected
        if (!mDevice.hasObject(APPS_CONTAINER)) {
            open();
            // taps on the "apps" button at the bottom of the screen
            mDevice.findObject(getAllAppsButtonSelector()).click();
            // wait until hotseat disappears, so that we know that we are no longer on home screen
            mDevice.wait(Until.gone(getHotSeatSelector()), 2000);
            mDevice.waitForIdle();
        }
        // taps on the "Widgets" page selector near the top of the screen
        UiObject2 widgetsTab =
                mDevice.findObject(By.desc("Widgets").clazz(TextView.class).selected(false));
        if (widgetsTab != null) {
            widgetsTab.click();
            // if we switched into widget page, then there's no need to reset, since it will go
            // back to beginning
            needReset = false;
        }
        UiObject2 allWidgetsContainer = mDevice.findObject(APPS_CONTAINER);
        Assert.assertNotNull(
                "openAllWidgets: did not find all widgets container", allWidgetsContainer);
        if (reset && needReset) {
            // only way to reset is to switch to "Apps" then back to "Widget", scroll to beginning
            // won't work
            mDevice.findObject(By.desc("Apps").selected(false)).click();
            mDevice.waitForIdle();
            mDevice.findObject(By.desc("Widgets").selected(false)).click();
            mDevice.waitForIdle();
        }
        return allWidgetsContainer;
    }

    /** {@inheritDoc} */
    @Override
    public Direction getAllWidgetsScrollDirection() {
        return Direction.RIGHT;
    }

    /** {@inheritDoc} */
    @Override
    public long launch(String appName, String packageName) {
        return CommonLauncherHelper.getInstance(mDevice)
                .launchApp(this, By.res("").clazz(TextView.class).desc(appName), packageName);
    }

    /** {@inheritDoc} */
    @Override
    public void setUiDevice(UiDevice uiDevice) {
        mDevice = uiDevice;
    }

    /** {@inheritDoc} */
    @Override
    public String getSupportedLauncherPackage() {
        return LAUNCHER_PKG;
    }

    /** {@inheritDoc} */
    @Override
    public BySelector getAllAppsButtonSelector() {
        return By.desc("Apps");
    }

    /** {@inheritDoc} */
    @Override
    public BySelector getAllAppsSelector() {
        return APPS_CONTAINER;
    }

    /** {@inheritDoc} */
    @Override
    public BySelector getAllWidgetsSelector() {
        return APPS_CONTAINER;
    }

    /** {@inheritDoc} */
    @Override
    public BySelector getWorkspaceSelector() {
        return WORKSPACE;
    }

    /** {@inheritDoc} */
    @Override
    public BySelector getHotSeatSelector() {
        return HOTSEAT;
    }

    /** {@inheritDoc} */
    /** {@inheritDoc} */
    @Override
    public BySelector getOverviewSelector() {
        return OVERVIEW;
    }

    /** {@inheritDoc} */
    @Override
    public Direction getWorkspaceScrollDirection() {
        return Direction.RIGHT;
    }
}
