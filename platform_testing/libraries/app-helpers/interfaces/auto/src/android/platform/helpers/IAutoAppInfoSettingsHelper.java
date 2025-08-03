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

package android.platform.helpers;

import java.util.List;

/**
 * Helper class for functional tests of App Info settings
 */

public interface IAutoAppInfoSettingsHelper extends IAppHelper {

    /** enum for enable/disable state */
    public enum State {
        ENABLE,
        DISABLE
    }

    /**
     * Setup expectation: Apps & notifications setting is open
     *
     * This method is to open an application in App info setting
     *
     * @param application - name of the application
     */
    void selectApp(String application);

    /**
     * Setup expectation: Apps & notifications setting is open
     *
     * This method is to click on Show all apps menu
     */
    void showAllApps();

    /**
     * Setup expectation: An application in Apps & notifications setting is open
     *
     * <p>This method is to enable/disable an application
     *
     * @param state - ENABLE: to enable, DISABLE: to disable
     */
    void enableDisableApplication(State state);

    /**
     * Setup expectation: An application in Apps & notifications setting is open
     *
     * This method is to check whether an application is running in background from UI
     */
    boolean isCurrentApplicationRunning();

    /**
     * Setup expectation: An application in Apps & notifications setting is open
     *
     * This method is to force stop the application
     */
    void forceStop();

    /**
     * Setup expectation: Apps & notifications setting is open
     *
     * <p>This method is to add a permission to an application
     *
     * @param permission - name of the permission
     * @param state - ENABLE: to enable, DISABLE: to disable
     */
    void setAppPermission(String permission, State state);

    /**
     * Setup expectation: An application in Apps & notifications setting is open
     *
     * Get the current enabled permission summary in String format for an application
     */
    String getCurrentPermissions();

    /**
     * Setup expectation: None
     *
     * <p>This method is to check if an application has been disabled.
     *
     * @param packageName - package of the application to be checked.
     */
    boolean isApplicationDisabled(String packageName);

    /**
     * Setup expectation: Settings screen is open.
     *
     * <p>Opens the 'Permission manager' submenu from the Apps menu.
     */
    void openPermissionManager();

    /**
     * Setup expectation: The Permission manager page is currently open (listing each permission
     * with a summary of the number of apps that have that permission.
     *
     * <p>Verifies that for a given permission, the number of apps displayed as being 'allowed'
     * matches the actual number of apps that have that permission.
     *
     * @param permissionName - Name of the permission to validate, as it appears on the permission
     *     UI element.
     * @return - True if the number of allowed and total apps listed in the permission summary
     *     matches the number listed when the permission is clicked on.
     */
    List<Integer> validateAppsPermissionManager(String permissionName);
}
