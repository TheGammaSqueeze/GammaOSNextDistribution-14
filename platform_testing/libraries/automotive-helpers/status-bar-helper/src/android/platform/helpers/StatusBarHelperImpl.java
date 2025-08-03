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

import android.app.Instrumentation;
import android.app.UiModeManager;
import android.content.Context;

import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiObject2;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/** Helper file for status bar tests */
public class StatusBarHelperImpl extends AbstractStandardAppHelper implements IAutoStatusBarHelper {

    private final UiModeManager mUiModeManager;
    private final Context mContext;

    public StatusBarHelperImpl(Instrumentation instr) {
        super(instr);
        mContext = instr.getContext();
        mUiModeManager = mContext.getSystemService(UiModeManager.class);
    }

    /** {@inheritDoc} */
    @Override
    public String getPackage() {
        return getPackageFromConfig(AutomotiveConfigConstants.HOME_PACKAGE);
    }

    /** {@inheritDoc} */
    @Override
    public String getLauncherName() {
        throw new UnsupportedOperationException("Operation not supported.");
    }

    /** {@inheritDoc} */
    @Override
    public void dismissInitialDialogs() {
        // Nothing to dismiss
    }

    /** {@inheritDoc} */
    @Override
    public void openBluetoothPalette() {
        BySelector bluetoothButtonSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.STATUS_BAR_BLUETOOTH_BUTTON);
        UiObject2 bluetoothButtonLink = getSpectatioUiUtil().findUiObject(bluetoothButtonSelector);
        getSpectatioUiUtil()
                .validateUiObject(
                        bluetoothButtonLink, AutomotiveConfigConstants.STATUS_BAR_BLUETOOTH_BUTTON);
        getSpectatioUiUtil().clickAndWait(bluetoothButtonLink);
    }

    /** {@inheritDoc} */
    @Override
    public boolean hasBluetoothSwitch() {
        BySelector bluetoothSwitchSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.STATUS_BAR_BLUETOOTH_TOGGLE_ON);
        return (getSpectatioUiUtil().hasUiElement(bluetoothSwitchSelector));
    }

    /** {@inheritDoc} */
    @Override
    public void openBluetoothSwitch() {
        BySelector bluetoothButtonSwitchSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.STATUS_BAR_BLUETOOTH_TOGGLE_ON);
        UiObject2 bluetoothButtonSwitchLink =
                getSpectatioUiUtil().findUiObject(bluetoothButtonSwitchSelector);
        getSpectatioUiUtil()
                .validateUiObject(
                        bluetoothButtonSwitchLink,
                        AutomotiveConfigConstants.STATUS_BAR_BLUETOOTH_TOGGLE_ON);
        getSpectatioUiUtil().clickAndWait(bluetoothButtonSwitchLink);
    }

    /** {@inheritDoc} */
    @Override
    public boolean hasToggleOnMessage() {
        BySelector toggleOnMessageSelector =
                getUiElementFromConfig(
                        AutomotiveConfigConstants.STATUS_BAR_BLUETOOTH_TOGGLE_ON_MESSAGE);
        return (getSpectatioUiUtil().hasUiElement(toggleOnMessageSelector));
    }

    /** {@inheritDoc} */
    @Override
    public boolean hasToggleOffMessage() {
        BySelector toggleOffMessageSelector =
                getUiElementFromConfig(
                        AutomotiveConfigConstants.STATUS_BAR_BLUETOOTH_TOGGLE_OFF_MESSAGE);
        return (getSpectatioUiUtil().hasUiElement(toggleOffMessageSelector));
    }

    /** {@inheritDoc} */
    @Override
    public void openBluetoothSettings() {
        BySelector openBluetoothSettingsSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.STATUS_BAR_BLUETOOTH_SETTINGS);
        UiObject2 bluetoothSettingsLink =
                getSpectatioUiUtil().findUiObject(openBluetoothSettingsSelector);
        getSpectatioUiUtil()
                .validateUiObject(
                        bluetoothSettingsLink,
                        AutomotiveConfigConstants.STATUS_BAR_BLUETOOTH_SETTINGS);
        getSpectatioUiUtil().clickAndWait(bluetoothSettingsLink);
        getSpectatioUiUtil().wait5Seconds();
    }

    /** {@inheritDoc} */
    @Override
    public boolean hasBluetoothSettingsPageTitle() {
        BySelector bluetoothSettingsPageTitleSelector =
                getUiElementFromConfig(
                        AutomotiveConfigConstants.STATUS_BAR_BLUETOOTH_SETTINGS_PAGE_TITLE);
        return (getSpectatioUiUtil().hasUiElement(bluetoothSettingsPageTitleSelector));
    }

    @Override
    public boolean isBluetoothOn() {
        BySelector enableOptionSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.STATUS_BAR_BLUETOOTH_TOGGLE_ON);
        UiObject2 enableOption = getSpectatioUiUtil().findUiObject(enableOptionSelector);
        getSpectatioUiUtil()
                .validateUiObject(
                        enableOption, AutomotiveConfigConstants.STATUS_BAR_BLUETOOTH_TOGGLE_ON);
        return enableOption.isChecked();
    }

    /** {@inheritDoc} */
    @Override
    public void turnOnOffBluetooth(boolean onOff) {
        boolean isOn = isBluetoothOn();
        if (isOn != onOff) {
            BySelector enableOptionSelector =
                    getUiElementFromConfig(
                            AutomotiveConfigConstants.STATUS_BAR_BLUETOOTH_TOGGLE_ON);
            UiObject2 enableOption = getSpectatioUiUtil().findUiObject(enableOptionSelector);
            getSpectatioUiUtil()
                    .validateUiObject(
                            enableOption, AutomotiveConfigConstants.STATUS_BAR_BLUETOOTH_TOGGLE_ON);
            getSpectatioUiUtil().clickAndWait(enableOption);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void clickBluetoothButton() {
        BySelector bluetoothButtonSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.BLUETOOTH_BUTTON);
        UiObject2 bluetoothButton = getSpectatioUiUtil().findUiObject(bluetoothButtonSelector);
        getSpectatioUiUtil()
                .validateUiObject(bluetoothButton, AutomotiveConfigConstants.BLUETOOTH_BUTTON);
        getSpectatioUiUtil().clickAndWait(bluetoothButton);
    }

    /** {@inheritDoc} */
    @Override
    public void openNetworkPalette() {
        BySelector networkPaletteSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.NETWORK_PALETTE);
        UiObject2 networkPalette = getSpectatioUiUtil().findUiObject(networkPaletteSelector);
        getSpectatioUiUtil()
                .validateUiObject(networkPalette, AutomotiveConfigConstants.NETWORK_PALETTE);
        getSpectatioUiUtil().clickAndWait(networkPalette);
        getSpectatioUiUtil().wait5Seconds();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isBluetoothConnected() {
        boolean isBluetoothConnected = false;
        BySelector btconnectedDisconnectedTextSelector =
                getUiElementFromConfig(
                        AutomotiveConfigConstants.BLUETOOTH_CONNECTED_DISCONNECTED_TEXT);
        UiObject2 btconnectedDisconnectedText =
                getSpectatioUiUtil().findUiObject(btconnectedDisconnectedTextSelector);
        getSpectatioUiUtil()
                .validateUiObject(
                        btconnectedDisconnectedText,
                        AutomotiveConfigConstants.BLUETOOTH_CONNECTED_DISCONNECTED_TEXT);
        if (getSpectatioUiUtil()
                .getTextForUiElement(btconnectedDisconnectedText)
                .equals("Connected")) {
            return !isBluetoothConnected;
        }
        return isBluetoothConnected;
    }

    /** {@inheritDoc} */
    @Override
    public boolean hasBluetoothButton() {
        BySelector bluetoothSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.BLUETOOTH_BUTTON);
        return getSpectatioUiUtil().hasUiElement(bluetoothSelector);
    }
    /** {@inheritDoc} */
    @Override
    public boolean isNetworkSwitchEnabled(String target) {
        UiObject2 enableOption = getSwitchObject(target);
        return enableOption.isChecked();
    }

    /** {@inheritDoc} */
    @Override
    public boolean hasBluetoothPalettePhoneButton() {
        BySelector phoneSelector = getUiElementFromConfig(AutomotiveConfigConstants.PHONE_BUTTON);
        return getSpectatioUiUtil().hasUiElement(phoneSelector);
    }

    /** {@inheritDoc} */
    @Override
    public void networkPaletteToggleOnOff(String target) {
        UiObject2 object = getSwitchObject(target);
        getSpectatioUiUtil().clickAndWait(object);
        getSpectatioUiUtil().wait5Seconds();
    }

    /** {@inheritDoc} */
    @Override
    public boolean hasBluetoothPaletteMediaButton() {
        BySelector mediaSelector = getUiElementFromConfig(AutomotiveConfigConstants.MEDIA_BUTTON);
        return getSpectatioUiUtil().hasUiElement(mediaSelector);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isHotspotNameDisplayed() {
        BySelector selector =
                getUiElementFromConfig(AutomotiveConfigConstants.NETWORK_PALETTE_HOTSPOT);
        UiObject2 object = getSpectatioUiUtil().findUiObject(selector);
        UiObject2 summary =
                getSpectatioUiUtil()
                        .findUiObjectInGivenElement(
                                object,
                                getUiElementFromConfig(
                                        AutomotiveConfigConstants.NETWORK_PALETTE_SUMMARY));
        getSpectatioUiUtil().validateUiObject(summary, String.format("to get the hotspot name"));
        return (summary.getText() != null && !summary.getText().equalsIgnoreCase("off"));
    }

    /** {@inheritDoc} */
    @Override
    public boolean verifyDeviceName() {
        BySelector deviceNameSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.DEVICE_NAME);
        return getSpectatioUiUtil().hasUiElement(deviceNameSelector);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isWifiNameDisplayed() {
        BySelector selector =
                getUiElementFromConfig(AutomotiveConfigConstants.NETWORK_PALETTE_WIFI);
        getSpectatioUiUtil().wait1Second();
        UiObject2 object = getSpectatioUiUtil().findUiObject(selector);
        UiObject2 summary =
                getSpectatioUiUtil()
                        .findUiObjectInGivenElement(
                                object,
                                getUiElementFromConfig(
                                        AutomotiveConfigConstants.NETWORK_PALETTE_SUMMARY));
        getSpectatioUiUtil().validateUiObject(summary, String.format("to get the Wi-Fi name"));
        return (summary.getText() != null
                && !summary.getText().equalsIgnoreCase("not connected")
                && !summary.getText().equalsIgnoreCase("wi‑fi disabled"));
    }

    private UiObject2 getSwitchObject(String target) {
        BySelector targetSelector = getUiElementFromConfig(target);
        UiObject2 targetObject = getSpectatioUiUtil().findUiObject(targetSelector).getParent();
        getSpectatioUiUtil().validateUiObject(targetObject, target);
        UiObject2 switchWidgetObject =
                targetObject.findObject(
                        getUiElementFromConfig(
                                AutomotiveConfigConstants.NETWORK_PALETTE_SWITCH_WIDGET));
        getSpectatioUiUtil().validateUiObject(switchWidgetObject, target);
        return switchWidgetObject;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isBluetoothButtonEnabled() {
        BySelector disabledBluetoothProfileSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.BLUETOOTH_BUTTON);
        UiObject2 disabledBluetoothProfile =
                getSpectatioUiUtil().findUiObject(disabledBluetoothProfileSelector);
        getSpectatioUiUtil()
                .validateUiObject(
                        disabledBluetoothProfile, AutomotiveConfigConstants.BLUETOOTH_BUTTON);
        return disabledBluetoothProfile.isChecked();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isBluetoothPhoneButtonEnabled() {
        BySelector disabledPhoneProfileNameSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.DISABLED_PHONE_PROFILE);
        UiObject2 disabledPhoneProfile =
                getSpectatioUiUtil().findUiObject(disabledPhoneProfileNameSelector);
        getSpectatioUiUtil()
                .validateUiObject(
                        disabledPhoneProfile, AutomotiveConfigConstants.DISABLED_PHONE_PROFILE);
        return disabledPhoneProfile.isChecked();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isBluetoothMediaButtonEnabled() {
        BySelector disabledMediaProfileNameSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.DISABLED_MEDIA_PROFILE);
        UiObject2 disabledMediaProfile =
                getSpectatioUiUtil().findUiObject(disabledMediaProfileNameSelector);
        getSpectatioUiUtil()
                .validateUiObject(
                        disabledMediaProfile, AutomotiveConfigConstants.DISABLED_MEDIA_PROFILE);
        return disabledMediaProfile.isChecked();
    }
    /** {@inheritDoc} */
    @Override
    public void clickOnConnectedWifi() {
        UiObject2 connectedObject =
                getSpectatioUiUtil()
                        .findUiObject(
                                getUiElementFromConfig(AutomotiveConfigConstants.CONNECTED_WIFI));
        getSpectatioUiUtil()
                .validateUiObject(connectedObject, AutomotiveConfigConstants.CONNECTED_WIFI);
        getSpectatioUiUtil().clickAndWait(connectedObject);
    }
    /** {@inheritDoc} */
    @Override
    public void forgetWifi() {
        UiObject2 forgetObject =
                getSpectatioUiUtil()
                        .findUiObject(
                                getUiElementFromConfig(AutomotiveConfigConstants.FORGET_WIFI));
        getSpectatioUiUtil().validateUiObject(forgetObject, AutomotiveConfigConstants.FORGET_WIFI);
        getSpectatioUiUtil().clickAndWait(forgetObject);
    }

    /** {@inheritDoc} */
    @Override
    public void open() {
        getSpectatioUiUtil().pressHome();
        getSpectatioUiUtil().waitForIdle();
    }
    // BT related
    /** {@inheritDoc} */
    @Override
    public boolean isBluetoothConnectedToMobile() {
        BySelector bluetoothConnectedSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.BT_CONNECTED_STATUS);
        UiObject2 bluetoothConnected =
                getSpectatioUiUtil().findUiObject(bluetoothConnectedSelector);
        getSpectatioUiUtil()
                .validateUiObject(
                        bluetoothConnected, AutomotiveConfigConstants.BT_CONNECTED_STATUS);
        return bluetoothConnected.isEnabled();
    }

    /** {@inheritDoc} */
    @Override
    public String getClockTime() {
        BySelector clockSelector = getUiElementFromConfig(AutomotiveConfigConstants.CLOCK_TIME);
        UiObject2 clockObject = getSpectatioUiUtil().findUiObject(clockSelector);
        getSpectatioUiUtil().validateUiObject(clockObject, AutomotiveConfigConstants.CLOCK_TIME);
        String clockTime = getSpectatioUiUtil().getTextForUiElement(clockObject);
        // To trim the space in the clock text
        String timePattern = "[ \\x{202f}]";
        clockTime = clockTime.replaceAll(timePattern, "");
        return clockTime.trim();
    }

    /** {@inheritDoc} */
    @Override
    public String getCurrentTimeWithTimeZone(String timezone) {
        ZonedDateTime currentTimeInZone = ZonedDateTime.now(ZoneId.of(timezone));
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("hh:mm");
        String time = currentTimeInZone.format(formatter);
        // To trim the 0 prefixed to match the clock time in Status bar
        String timePattern = "^0+(?!$)";
        time = time.replaceAll(timePattern, "");
        return time;
    }

    @Override
    public boolean changeToDayMode() {
        String dayModeResult =
                getSpectatioUiUtil()
                        .executeShellCommand(
                                getCommandFromConfig(AutomotiveConfigConstants.DAY_MODE_COMMAND));
        boolean result = dayModeResult.contains("changed to: day");
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public boolean changeToNightMode() {
        String nightModeResult =
                getSpectatioUiUtil()
                        .executeShellCommand(
                                getCommandFromConfig(AutomotiveConfigConstants.NIGHT_MODE_COMMAND));
        boolean result = nightModeResult.contains("changed to: night");
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public int getCurrentDisplayMode() {
        return mUiModeManager.getNightMode();
    }
    /** {@inheritDoc} */
    @Override
    public boolean isBluetoothDisconnected() {
        BySelector bluetoothDisconnectedSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.BT_DISCONNECTED_STATUS);
        UiObject2 bluetoothDisconnected =
                getSpectatioUiUtil().findUiObject(bluetoothDisconnectedSelector);
        getSpectatioUiUtil()
                .validateUiObject(
                        bluetoothDisconnected, AutomotiveConfigConstants.BT_DISCONNECTED_STATUS);
        return bluetoothDisconnected.isEnabled();
    }
    /** {@inheritDoc} */
    @Override
    public boolean isBluetoothPaletteMediaButtonEnabled() {
        BySelector bluetoothPaletteMediaButtonSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.MEDIA_BUTTON);
        UiObject2 bluetoothPaletteMediaButton =
                getSpectatioUiUtil().findUiObject(bluetoothPaletteMediaButtonSelector);
        getSpectatioUiUtil()
                .validateUiObject(
                        bluetoothPaletteMediaButton, AutomotiveConfigConstants.MEDIA_BUTTON);
        return bluetoothPaletteMediaButton.isChecked();
    }
    /** {@inheritDoc} */
    @Override
    public void clickOnBluetoothPaletteMediaButton() {
        BySelector disabledMediaProfileNameSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.DISABLED_MEDIA_PROFILE);
        UiObject2 disabledMediaProfile =
                getSpectatioUiUtil().findUiObject(disabledMediaProfileNameSelector);
        getSpectatioUiUtil()
                .validateUiObject(
                        disabledMediaProfile, AutomotiveConfigConstants.DISABLED_MEDIA_PROFILE);
        getSpectatioUiUtil().clickAndWait(disabledMediaProfile);
        getSpectatioUiUtil().wait5Seconds();
    }
}
