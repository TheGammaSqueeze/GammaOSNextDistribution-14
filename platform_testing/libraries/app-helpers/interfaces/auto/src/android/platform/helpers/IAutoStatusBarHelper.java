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
package android.platform.helpers;

/** Interface file for status bar tests */
public interface IAutoStatusBarHelper extends IAppHelper {

    /** Opens bluetooth palette. */
    void openBluetoothPalette();

    /**
     * Setup expectations: Should be on home screen.
     *
     * <p>Checks if bluetooth switch is present.
     */
    boolean hasBluetoothSwitch();

    /** Opens bluetooth switch. */
    void openBluetoothSwitch();

    /**
     * Setup expectations: Should be on home screen.
     *
     * <p>Checks if bluetooth on message is present.
     */
    boolean hasToggleOnMessage();

    /**
     * Setup expectations: Should be on home screen.
     *
     * <p>Checks if bluetooth off message is present.
     */
    boolean hasToggleOffMessage();

    /** Opens bluetooth settings. */
    void openBluetoothSettings();

    /**
     * Setup expectations: Should be on home screen.
     *
     * <p>Checks if bluetooth settings page title is present
     */
    boolean hasBluetoothSettingsPageTitle();

    /**
     * Setup expectations: Should be on home screen.
     *
     * <p>Checks if bluetooth enabled or not
     */
    boolean isBluetoothOn();

    /** Bluetooth switch button. */
    void turnOnOffBluetooth(boolean onOff);

    /**
     * Setup expectations: Open Bluetooth Palette
     *
     * <p>This method clicks bluetooth button</>
     */
    void clickBluetoothButton();

    /**
     * Setup expectations: Open Bluetooth Palette
     *
     * <p>This method checks bluetooth connected text</>
     */
    boolean isBluetoothConnected();

    /**
     * Setup expectations: Verify Bluetooth Button
     *
     * <p>This method verifies bluetooth button from bluetooth palette</>
     */
    boolean hasBluetoothButton();

    /**
     * Setup expectations: Verify Phone Button
     *
     * <p>This method verifies phone button from bluetooth palette</>
     */
    boolean hasBluetoothPalettePhoneButton();

    /**
     * Setup expectations: Verify Media Button
     *
     * <p>This method verifies media button from bluetooth palette</>
     */
    boolean hasBluetoothPaletteMediaButton();

    /**
     * Setup expectations: Verify the Device name
     *
     * <p>This method verifies the connected device name</>
     */
    boolean verifyDeviceName();

    /**
     * Setup expectations: Verify the Disabled Bluetooth profile
     *
     * <p>This method verifies the disabled bluetooth profile</>
     */
    boolean isBluetoothButtonEnabled();

    /**
     * Setup expectations: Verify the Disabled Phone profile
     *
     * <p>This method verifies the disabled phone profile</>
     */
    boolean isBluetoothPhoneButtonEnabled();

    /**
     * Setup expectations: Verify the Disabled Media profile
     *
     * <p>This method verifies the disabled Media profile</>
     */
    boolean isBluetoothMediaButtonEnabled();

    /**
     * Setup expectations: Status bar Network palette is open.
     *
     * <p>Open status bar network palette.
     */
    void openNetworkPalette();

    /**
     * Setup expectations: Toggle ON/OFF
     *
     * <p>Click on toggle button from status bar palette
     *
     * @param name options in the palette
     */
    void networkPaletteToggleOnOff(String name);

    /**
     * Setup expectations: Hotspot Name
     *
     * <p>Check if the Hotspot name is displayed
     */
    boolean isHotspotNameDisplayed();

    /**
     * Setup expectations: Status of Toggle ON/OFF
     *
     * <p>Checks if toggle is enabled on status bar palette
     *
     * @param target options in the palette
     */
    boolean isNetworkSwitchEnabled(String target);

    /**
     * Setup expectations: Wi-Fi Name
     *
     * <p>Check if the Wifi name is displayed
     */
    boolean isWifiNameDisplayed();

    /**
     * Setup expectations: Network & Internet
     *
     * <p>click on connected Wifi
     */
    void clickOnConnectedWifi();

    /**
     * Setup expectations: Network & Internet
     *
     * <p>click on forget button
     */
    void forgetWifi();

    /**
     * Setup expectations: None
     *
     * <p>This method changes the device mode to DAY Mode</>
     */
    boolean changeToDayMode();

    /**
     * Setup expectations: None
     *
     * <p>This method changes the device mode to NIGHT Mode</>
     */
    boolean changeToNightMode();

    /**
     * Setup expectations: None
     *
     * <p>This method gets the current night mode, for night mode no the value should be 1 for night
     * mode yes the return value should be 2</>
     */
    int getCurrentDisplayMode();

    /**
     * Setup expectations: Open Bluetooth Palette
     *
     * <p>This method checks bluetooth connected text</>
     *
     * <p>This method checks if mobile is connected to bluetooth</>
     */
    boolean isBluetoothConnectedToMobile();

    /**
     * Setup expectations: Open Bluetooth Palette
     *
     * <p>This method checks if mobile is disconnected to bluetooth</>
     */
    boolean isBluetoothDisconnected();

    /**
     * Setup expectations: Home screen
     *
     * <p>Get time from the Status bar
     */
    String getClockTime();

    /**
     * Setup expectations: None
     *
     * <p>Get the current time for given time zone</>
     */
    String getCurrentTimeWithTimeZone(String timezone);

    /**
     * Setup expectations: Open Bluetooth Palette
     *
     * <p>This method checks whether media button is enabled
     */
    boolean isBluetoothPaletteMediaButtonEnabled();

    /**
     * Setup expectations: Open Bluetooth Palette
     *
     * <p>This method performs click operation on media button
     */
    void clickOnBluetoothPaletteMediaButton();
}
