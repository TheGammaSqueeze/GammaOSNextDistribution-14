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

import android.content.Context;
import android.util.Log;
import android.widget.TextView;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import junit.framework.Assert;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class WearLauncherStrategy implements ILauncherStrategy {

    private static final String LAUNCHER_PKG = "com.google.android.wearable.app";
    private static final String LOG_TAG = WearLauncherStrategy.class.getSimpleName();
    protected UiDevice mDevice;
    protected Context mContext;

    /** {@inheritDoc} */
    @Override
    public String getSupportedLauncherPackage() {
        return LAUNCHER_PKG;
    }

    /** {@inheritDoc} */
    @Override
    public void setUiDevice(UiDevice uiDevice) {
        mDevice = uiDevice;
    }

    /** Shows the home screen of launcher */
    @Override
    public void open() {
        if (!mDevice.hasObject(getHotSeatSelector())) {
            mDevice.pressHome();
            if (!mDevice.wait(Until.hasObject(getHotSeatSelector()), 5000)) {
                dumpScreen("Return Watch face");
                Assert.fail("Failed to open launcher");
            }
            mDevice.waitForIdle();
        }
    }

    /**
     * Opens the all apps drawer of launcher
     *
     * @param reset if the all apps drawer should be reset to the beginning
     * @return {@link UiObject2} representation of the all apps drawer
     */
    @Override
    public UiObject2 openAllApps(boolean reset) {
        if (!mDevice.hasObject(getAllAppsSelector())) {
            mDevice.pressHome();
            mDevice.waitForIdle();
            if (!mDevice.wait(Until.hasObject(getAllAppsSelector()), 5000)) {
                dumpScreen("Open launcher");
                Assert.fail("Failed to open launcher");
            }
        }
        UiObject2 allAppsContainer = mDevice.wait(Until.findObject(getAllAppsSelector()), 2000);
        if (reset) {
            CommonLauncherHelper.getInstance(mDevice)
                    .scrollBackToBeginning(
                            allAppsContainer, Direction.reverse(getAllAppsScrollDirection()));
        }
        if (allAppsContainer == null) {
            Assert.fail("Failed to find launcher");
        }
        return allAppsContainer;
    }

    /** {@inheritDoc} */
    @Override
    public void openOverview() {
        throw new UnsupportedOperationException(
                "The 'Overview' is not available on Android Wear Launcher.");
    }

    /** {@inheritDoc} */
    @Override
    public boolean clearRecentAppsFromOverview() {
        throw new UnsupportedOperationException(
                "The 'Recent Apps' are not available on Android Wear Launcher.");
    }

    /** {@inheritDoc} */
    @Override
    public BySelector getHotSeatSelector() {
        return By.res(getSupportedLauncherPackage(), "watchface_overlay");
    }

    /** {@inheritDoc} */
    @Override
    public BySelector getOverviewSelector() {
        throw new UnsupportedOperationException(
                "The 'Overview' are not available on Android Wear Launcher.");
    }

    /**
     * Returns a {@link BySelector} describing the all apps drawer
     *
     * @return
     */
    @Override
    public BySelector getAllAppsSelector() {
        return By.res(getSupportedLauncherPackage(), "launcher_view");
    }

    /**
     * Retrieves the all apps drawer forward scroll direction as implemented by the launcher
     *
     * @return
     */
    @Override
    public Direction getAllAppsScrollDirection() {
        return Direction.DOWN;
    }

    @Override
    public BySelector getAllAppsButtonSelector() {
        throw new UnsupportedOperationException(
                "The 'All Apps' button is not available on Android Wear Launcher.");
    }

    @Override
    public UiObject2 openAllWidgets(boolean reset) {
        throw new UnsupportedOperationException(
                "The 'All Widgets' button is not available on Android Wear Launcher.");
    }

    @Override
    public BySelector getAllWidgetsSelector() {
        throw new UnsupportedOperationException(
                "The 'All Widgets' button is not available on Android Wear Launcher.");
    }

    @Override
    public Direction getAllWidgetsScrollDirection() {
        throw new UnsupportedOperationException(
                "The 'All Widgets' button is not available on Android Wear Launcher.");
    }

    @Override
    public BySelector getWorkspaceSelector() {
        throw new UnsupportedOperationException(
                "The 'Work space' is not available on Android Wear Launcher.");
    }

    @Override
    public Direction getWorkspaceScrollDirection() {
        throw new UnsupportedOperationException(
                "The 'Work Space' is not available on Android Wear Launcher.");
    }

    /**
     * Launch the named application
     *
     * @param appName the name of the application to launch as shown in launcher
     * @param packageName the expected package name to verify that the application has been launched
     *     into foreground. If <code>null</code> is provided, no verification is performed.
     * @return <code>true</code> if application is verified to be in foreground after launch, or the
     *     verification is skipped; <code>false</code> otherwise.
     */
    @Override
    public long launch(String appName, String packageName) {
        BySelector app =
                By.res(getSupportedLauncherPackage(), "title").clazz(TextView.class).text(appName);
        return CommonLauncherHelper.getInstance(mDevice).launchApp(this, app, packageName);
    }

    private void dumpScreen(String description) {
        // DEBUG: dump hierarchy to logcat
        Log.d(LOG_TAG, "Dump Screen at " + description);
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            mDevice.dumpWindowHierarchy(baos);
            baos.flush();
            String[] lines = baos.toString().split(System.lineSeparator());
            for (String line : lines) {
                Log.d(LOG_TAG, line.trim());
            }
        } catch (IOException ioe) {
            Log.e(LOG_TAG, "error dumping XML to logcat", ioe);
        }
    }
}
