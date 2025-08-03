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

import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;

/**
 * Defines the common use cases a launcher UI automation helper should fulfill.
 *
 * <p>Class will be instantiated by {@link LauncherStrategyFactory} based on current launcher
 * package, and a {@link UiDevice} instance will be provided via {@link #setUiDevice(UiDevice)}
 * method.
 */
public interface ILauncherStrategy {
    long LAUNCH_FAILED_TIMESTAMP = -1;

    /**
     * Returns the launcher application package that this {@link ILauncherStrategy} can automate
     *
     * @return
     */
    String getSupportedLauncherPackage();

    /**
     * Injects a {@link UiDevice} instance for UI interactions
     *
     * @param uiDevice
     */
    void setUiDevice(UiDevice uiDevice);

    /** Shows the home screen of launcher */
    void open();

    /**
     * Opens the all apps drawer of launcher
     *
     * @param reset if the all apps drawer should be reset to the beginning
     * @return {@link UiObject2} representation of the all apps drawer
     */
    UiObject2 openAllApps(boolean reset);

    /** Opens the overview drawer of launcher */
    void openOverview();

    /**
     * Clears the list of recently used apps from the overview drawer
     *
     * @return If the list of recent apps is empty
     */
    boolean clearRecentAppsFromOverview();

    /**
     * Returns a {@link BySelector} describing the button to open the all apps drawer
     *
     * @return
     */
    BySelector getAllAppsButtonSelector();

    /**
     * Returns a {@link BySelector} describing the all apps drawer
     *
     * @return
     */
    BySelector getAllAppsSelector();

    /**
     * Retrieves the all apps drawer forward scroll direction as implemented by the launcher
     *
     * @return
     */
    Direction getAllAppsScrollDirection();

    /**
     * Opens the all widgets drawer of launcher
     *
     * @param reset if the all widgets drawer should be reset to the beginning
     * @return {@link UiObject2} representation of the all widgets drawer
     */
    UiObject2 openAllWidgets(boolean reset);

    /**
     * Returns a {@link BySelector} describing the all widgets drawer
     *
     * @return
     */
    BySelector getAllWidgetsSelector();

    /**
     * Retrieves the all widgets drawer forward scroll direction as implemented by the launcher
     *
     * @return
     */
    Direction getAllWidgetsScrollDirection();

    /**
     * Returns a {@link BySelector} describing the home screen workspace
     *
     * @return
     */
    BySelector getWorkspaceSelector();

    /**
     * Returns a {@link BySelector} describing the home screen hot seat (app icons at the bottom)
     *
     * @return
     */
    BySelector getHotSeatSelector();

    /**
     * Returns a {@link BySelector} describing the overview screen (recent apps list)
     *
     * @return
     */
    BySelector getOverviewSelector();

    /**
     * Retrieves the home screen workspace forward scroll direction as implemented by the launcher
     *
     * @return
     */
    Direction getWorkspaceScrollDirection();

    /**
     * Launch the named application
     *
     * @param appName the name of the application to launch as shown in launcher
     * @param packageName the expected package name to verify that the application has been launched
     *     into foreground. If <code>null</code> is provided, no verification is performed.
     * @return <code>true</code> if application is verified to be in foreground after launch, or the
     *     verification is skipped; <code>false</code> otherwise.
     */
    long launch(String appName, String packageName);
}
