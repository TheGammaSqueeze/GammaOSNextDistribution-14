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

import androidx.test.uiautomator.UiObject2;

/** Helper class for functional tests of Settings facet */
public interface IAutoSettingHelper extends IAppHelper, Scrollable {

    /**
     * enum for Day/Night mode.
     *
     * <p>The values of DAY_MODE(0) and NIGHT_MODE(2) are determined by the returned value of
     * UiModeManager.getNightMode()
     */
    public enum DayNightMode {
        DAY_MODE(0),
        NIGHT_MODE(2);

        private final int value;

        DayNightMode(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    /**
     * enum for changing(increasing, decreasing) value.
     */
    enum ChangeType{
        INCREASE,
        DECREASE
    }

    /**
     * Setup expectations: The settings app is open
     *
     * @param setting option to find.
     */
    UiObject2 findSettingMenu(String setting);

    /**
     * Setup expectations: The app is open and the settings facet is open
     *
     * @param setting option to open.
     */
    void openSetting(String setting);

    /**
     * Setup expectations: The app is open
     *
     * <p>Open full settings page
     */
    void openFullSettings();

    /**
     * Setup expectations: The app is open and wifi setting options is selected
     *
     * @param option to turn on/off wifi
     */
    void turnOnOffWifi(boolean turnOn);

    /**
     * Setup expectations: The app is open and bluetooth setting options is selected
     *
     * @param option to turn on/off bluetooth
     */
    void turnOnOffBluetooth(boolean turnOn);

    /**
     * Setup expectations: The app is open and Hotspot & tethering setting options is selected
     *
     * @param turnOn to turn on/off Hotspot
     */
    void turnOnOffHotspot(boolean turnOn);

    /**
     * Setup expectations: The app is open.
     *
     * <p>Toggle on/off hotspot.
     */
    void toggleHotspot();

    /**
     * Setup expectations: The app is open.
     *
     * Checks if the wifi is enabled.
     */
    boolean isWifiOn();

    /**
     * Setup expectations: The app is open.
     *
     * Checks if the bluetooth is enabled.
     */
    boolean isBluetoothOn();

    /**
     * Setup expectations: The app is open.
     *
     * Checks if hotspot is enabled.
     */
    boolean isHotspotOn();

    /**
     * Setup expectations: The app is open and the settings facet is open
     */
    void goBackToSettingsScreen();

    /**
     * Force stops the settings application
     */
    void stopSettingsApplication();

    /**
     * Setup expectations: settings app is open.
     *
     * This method is used to open Settings Menu with menuOptions.
     * Example - Settings->App info->Calandar->Permissions
     *           openMenuWith("App info", "Calandar", "Permissions");
     *
     * @param - menuOptions used to pass multiple level of menu options in one go.
     *
     */
    void openMenuWith(String... menuOptions);

    /**
     * Setup expectations: settings app is open.
     *
     * gets the value of the setting.
     * @param setting should be passed. example for setting is screen_brightness.
     */
    int getValue(String setting);

    /**
     * Setup expectations: settings app is open
     *
     * sets the value of the setting.
     * @param setting should be passed. example for setting is screen_brightness.
     */
    void setValue(String setting, int value);

    /**
     * Setup expectations: full settings facet is open.
     *
     * <p>search in settings app and select the first search result.
     *
     * @param item to be searched.
     */
    void searchAndSelect(String item);

    /**
     * Setup expectations: full settings facet is open.
     *
     * <p>search in settings app.
     *
     * @param item to be searched.
     * @param selectedIndex determines which search result to select.
     */
    void searchAndSelect(String item, int selectedIndex);

    /**
     * Setup expectations: search result is open.
     *
     * <p>verify page title contains the searched item.
     *
     * @param item to be verified.
     */
    boolean isValidPageTitle(String item);

    /**
     * Setup expectations: Setting is open.
     *
     * <p>check whether a setting menu in Settings is enabled or not.
     *
     * @param name of the setting menu.
     */
    boolean isSettingMenuEnabled(String menu);

    /**
     * Setup expectations: Setting is open.
     *
     * <p>Get the current page title text.
     */
    String getPageTitleText();

    /**
     * Setup expectations: Setting is open.
     *
     * <p>check whether a setting menu in Settings is displayed or not.
     */
    boolean checkMenuExists(String setting);

    /**
     * Setup expectations: Setting is open.
     *
     * <p>Find the setting menu and perform a click action.
     *
     * @param name of the setting menu.
     */
    void findSettingMenuAndClick(String setting);

    /**
     * Set the brightness and report the resulting brightness value
     *
     * @param targetPercentage Where on the brightness seekbar to tap
     * @return The brightness value as reported by the service
     */
    int setBrightness(float targetPercentage);

    /**
     * Setup expectation: None
     *
     * <p>This method checks if Recently accessed apps is displayed in settings Location
     */
    boolean isRecentAppDisplayedInLocationSettings(String app);
}
