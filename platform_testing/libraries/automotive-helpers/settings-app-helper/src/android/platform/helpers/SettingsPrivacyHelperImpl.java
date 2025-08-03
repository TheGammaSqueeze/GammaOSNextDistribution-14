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

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiObject2;

/** Helper class for functional tests of Privacy settings */
public class SettingsPrivacyHelperImpl extends AbstractStandardAppHelper
        implements IAutoPrivacySettingsHelper {
    private static final int MAX_WAIT_COUNT = 5;
    public SettingsPrivacyHelperImpl(Instrumentation instr) {
        super(instr);
    }
    /** {@inheritDoc} */
    @Override
    public String getPackage() {
        return getPackageFromConfig(AutomotiveConfigConstants.SETTINGS_PACKAGE);
    }
    @Override
    public void dismissInitialDialogs() {
        // Nothing to dismiss
    }
    /** {@inheritDoc} */
    @Override
    public String getLauncherName() {
        throw new UnsupportedOperationException("Operation not supported.");
    }
    /** {@inheritDoc} */
    @Override
    public void exit() {
        getSpectatioUiUtil().pressHome();
        getSpectatioUiUtil().wait1Second();
    }

    /** {@inheritDoc} */
    @Override
    public void turnOnOffMicroPhone(boolean onOff) {
        boolean isOn = isMicroPhoneOn();
        if (isOn != onOff) {
            BySelector microPhoneSwitchSelector =
                    getUiElementFromConfig(AutomotiveConfigConstants.TOGGLE_MICROPHONE);
            UiObject2 microPhoneSwitch =
                    getSpectatioUiUtil().findUiObject(microPhoneSwitchSelector);
            getSpectatioUiUtil()
                    .validateUiObject(
                            microPhoneSwitch, AutomotiveConfigConstants.TOGGLE_MICROPHONE);
            getSpectatioUiUtil().clickAndWait(microPhoneSwitch);
        } else {
            throw new RuntimeException("MicroPhone state is already " + (onOff ? "on" : "off"));
        }
    }
    @Override
    public boolean isMicroPhoneOn() {
        BySelector enableOptionSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.MICRO_PHONE_SWITCH);
        UiObject2 enableOption = getSpectatioUiUtil().findUiObject(enableOptionSelector);
        getSpectatioUiUtil()
                .validateUiObject(enableOption, AutomotiveConfigConstants.MICRO_PHONE_SWITCH);
        return enableOption.isChecked();
    }
    /** {@inheritDoc} */
    @Override
    public boolean isMutedMicChipPresentOnStatusBar() {
        BySelector microPhoneChipSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.MICRO_PHONE_MUTED_CHIP_STATUS_BAR);
        return getSpectatioUiUtil().hasUiElement(microPhoneChipSelector);
    }
    /** {@inheritDoc} */
    @Override
    public boolean isMutedMicChipPresentWithMicPanel() {
        BySelector microPhoneChipSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.MICRO_PHONE_MUTED_CHIP_MIC_PANEL);
        return getSpectatioUiUtil().hasUiElement(microPhoneChipSelector);
    }
    /** {@inheritDoc} */
    @Override
    public boolean isMicChipPresentOnStatusBar() {
        // To check if any mic chip is present on screen
        BySelector microPhoneChipSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.MICRO_PHONE_CHIP_STATUS_BAR);
        return getSpectatioUiUtil().hasUiElement(microPhoneChipSelector);
    }
    /** {@inheritDoc} */
    @Override
    public void clickMicroPhoneStatusBar() {
        BySelector microPhoneChipSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.MICRO_PHONE_MUTED_CHIP_STATUS_BAR);
        UiObject2 microPhoneChip = getSpectatioUiUtil().findUiObject(microPhoneChipSelector);
        getSpectatioUiUtil()
                .validateUiObject(
                        microPhoneChip,
                        AutomotiveConfigConstants.MICRO_PHONE_MUTED_CHIP_STATUS_BAR);
        getSpectatioUiUtil().clickAndWait(microPhoneChip);
    }
    /** {@inheritDoc} */
    @Override
    public boolean verifyMicrophoneStatusMessage(String target) {
        BySelector microphoneStatusMessageSelector = getUiElementFromConfig(target);
        return getSpectatioUiUtil().hasUiElement(microphoneStatusMessageSelector);
    }

    /** {@inheritDoc} */
    @Override
    public void clickUnMutedMicroPhoneStatusBar() {
        BySelector microPhoneChipSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.MICRO_PHONE_CHIP_STATUS_BAR);
        UiObject2 microPhoneChip = getSpectatioUiUtil().findUiObject(microPhoneChipSelector);
        getSpectatioUiUtil()
                .validateUiObject(
                        microPhoneChip, AutomotiveConfigConstants.MICRO_PHONE_CHIP_STATUS_BAR);
        getSpectatioUiUtil().clickAndWait(microPhoneChip);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isMicroPhoneSettingsLinkPresent() {
        BySelector microPhoneSettingsLinkSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.MICRO_PHONE_SETTING_LINK);
        return getSpectatioUiUtil().hasUiElement(microPhoneSettingsLinkSelector);
    }
    /** {@inheritDoc} */
    @Override
    public void clickMicroPhoneSettingsLink() {
        BySelector microPhoneSettingsLinkSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.MICRO_PHONE_SETTING_LINK);
        UiObject2 microPhoneSettingsLink =
                getSpectatioUiUtil().findUiObject(microPhoneSettingsLinkSelector);
        getSpectatioUiUtil()
                .validateUiObject(
                        microPhoneSettingsLink, AutomotiveConfigConstants.MICRO_PHONE_SETTING_LINK);
        getSpectatioUiUtil().clickAndWait(microPhoneSettingsLink);
    }
    /** {@inheritDoc} */
    @Override
    public boolean isMicroPhoneTogglePresent() {
        BySelector microPhoneSwitchSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.MICRO_PHONE_SWITCH);
        return getSpectatioUiUtil().hasUiElement(microPhoneSwitchSelector);
    }
    /** {@inheritDoc} */
    @Override
    public void clickMicroPhoneToggleStatusBar() {
        BySelector microPhoneSwitchSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.MICRO_PHONE_SWITCH);
        UiObject2 microPhoneSwitch = getSpectatioUiUtil().findUiObject(microPhoneSwitchSelector);
        getSpectatioUiUtil()
                .validateUiObject(microPhoneSwitch, AutomotiveConfigConstants.MICRO_PHONE_SWITCH);
        getSpectatioUiUtil().clickAndWait(microPhoneSwitch);
        getSpectatioUiUtil().wait1Second();
    }
    /** {@inheritDoc} */
    @Override
    public boolean verifyNoRecentAppsPresent() {
        BySelector noRecentAppsSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.NO_RECENT_APPS);
        return getSpectatioUiUtil().hasUiElement(noRecentAppsSelector);
    }
    /** {@inheritDoc} */
    @Override
    public boolean isMicroPhoneStatusMessageUpdated(String status) {
        return getSpectatioUiUtil().hasUiElement(status);
    }
    /** {@inheritDoc} */
    @Override
    public void clickManageMicroPhonePermissions() {
        BySelector manageMicroPhoneButtonSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.MANAGE_MICRO_PHONE_PERMISSIONS);
        UiObject2 manageMicroPhoneButton =
                getSpectatioUiUtil().findUiObject(manageMicroPhoneButtonSelector);
        getSpectatioUiUtil()
                .validateUiObject(
                        manageMicroPhoneButton,
                        AutomotiveConfigConstants.MANAGE_MICRO_PHONE_PERMISSIONS);
        getSpectatioUiUtil().clickAndWait(manageMicroPhoneButton);
    }

    /** {@inheritDoc} */
    @Override
    public boolean verifyMicrophoneManagePermissionsPage() {
        getSpectatioUiUtil().wait5Seconds();
        BySelector microphoneManagePermissionsSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.MICROPHONE_PERMISSIONS_PAGE);
        return getSpectatioUiUtil().hasUiElement(microphoneManagePermissionsSelector);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isAccountAddedAutofill() {
        getSpectatioUiUtil().wait5Seconds();
        BySelector addAccountSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.ADD_ACCOUNT_TEXT);
        return getSpectatioUiUtil().hasUiElement(addAccountSelector);
    }
    /** {@inheritDoc} */
    @Override
    public boolean isNoAccountAddedDialogOpen() {
        getSpectatioUiUtil().wait5Seconds();
        BySelector noAccountAddedSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.NO_ACCOUNT_TEXT);
        return getSpectatioUiUtil().hasUiElement(noAccountAddedSelector);
    }
    /** {@inheritDoc} */
    @Override
    public boolean isManageActivityControlOpen() {
        // It takes a long time to load manage actvity screen
        int count = 0;
        BySelector manageActivityControlSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.MANAGE_ACTIVITY_CONTROL_TEXT);
        while (!getSpectatioUiUtil().hasUiElement(manageActivityControlSelector)
                && count < MAX_WAIT_COUNT) {
            getSpectatioUiUtil().wait5Seconds();
            count++;
        }
        return getSpectatioUiUtil().hasUiElement(manageActivityControlSelector);
    }
    /** {@inheritDoc} */
    @Override
    public boolean isRecentAppDisplayedWithStamp(String app) {
        BySelector recentAppSelector = By.text(app);
        UiObject2 recentApp = getSpectatioUiUtil().findUiObject(recentAppSelector);
        getSpectatioUiUtil()
                .validateUiObject(recentApp, String.format("Recently accessed app - %s", app));

        UiObject2 recentAppsTime = recentApp.getParent();
        if (recentAppsTime.getChildren().size() < 2) {
            throw new RuntimeException("TimeStamp not displayed for Recently accessed app");
        }

        BySelector recentAppTimeStampSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.RECENT_APPS_TIMESTAMP);
        UiObject2 timestampObject =
                getSpectatioUiUtil()
                        .findUiObjectInGivenElement(recentAppsTime, recentAppTimeStampSelector);
        getSpectatioUiUtil().validateUiObject(timestampObject, String.format("timestamp object"));
        String timestamp = timestampObject.getText();

        String recentAppTimeStampTxt =
                getActionFromConfig(AutomotiveConfigConstants.RECENT_APPS_TIMESTAMP_TEXT);
        return timestamp.contains(recentAppTimeStampTxt);
    }

    /** {@inheritDoc} */
    @Override
    public void clickViewAllLink() {
        BySelector viewAllLinkSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.MICRO_PHONE_VIEW_ALL);
        UiObject2 viewAllLink = getSpectatioUiUtil().findUiObject(viewAllLinkSelector);
        getSpectatioUiUtil()
                .validateUiObject(viewAllLink, AutomotiveConfigConstants.MICRO_PHONE_VIEW_ALL);
        getSpectatioUiUtil().clickAndWait(viewAllLink);
    }

    /** {@inheritDoc} */
    @Override
    public void privacyDashboardPermissionPage(String target) {
        getSpectatioUiUtil().wait5Seconds();
        executeWorkflow(target);
        getSpectatioUiUtil().wait1Second();
    }

    /** {@inheritDoc} */
    @Override
    public void changePermissions(Permission permission) {
        if (permission == Permission.ALLOW) {
            executeWorkflow(AutomotiveConfigConstants.PERMISSION_ALLOW);
        } else if (permission == Permission.ALLOW_ALL_THE_TIME) {
            executeWorkflow(AutomotiveConfigConstants.PERMISSION_ALLOW_ALL_TIME);
        } else if (permission == Permission.ALLOW_WHILE_USING_APP) {
            executeWorkflow(AutomotiveConfigConstants.ALLOW_WHILE_USING_APP);
        } else {
            executeWorkflow(AutomotiveConfigConstants.PERMISSION_DONT_ALLOW);
        }
    }

    /** {@inheritDoc} */
    @Override
    public String getRecentPermissionDecisionMessage(String appName) {
        getSpectatioUiUtil().wait5Seconds();
        UiObject2 decisionObject =
                getSpectatioUiUtil().findUiObject(getUiElementFromConfig(appName));
        getSpectatioUiUtil().validateUiObject(decisionObject, appName);
        return decisionObject.getText();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isMessageDisplayed() {
        getSpectatioUiUtil().wait5Seconds();
        UiObject2 messageObject =
                getSpectatioUiUtil()
                        .findUiObject(
                                getUiElementFromConfig(
                                        AutomotiveConfigConstants.PERMISSION_MESSAGE));
        getSpectatioUiUtil()
                .validateUiObject(messageObject, AutomotiveConfigConstants.PERMISSION_MESSAGE);
        return messageObject.isEnabled();
    }

    /** {@inheritDoc} */
    @Override
    public void cancelButton() {
        UiObject2 cancelObject =
                getSpectatioUiUtil()
                        .findUiObject(getUiElementFromConfig(AutomotiveConfigConstants.CANCEL));
        getSpectatioUiUtil().validateUiObject(cancelObject, AutomotiveConfigConstants.CANCEL);
        getSpectatioUiUtil().clickAndWait(cancelObject);
        getSpectatioUiUtil().waitForIdle();
    }

    /** Returns true if the assistant app's default permission "Allow" is enabled. */
    @Override
    public boolean isAllowDefaultPermission() {
        UiObject2 radioButtonStatus =
                getSpectatioUiUtil()
                        .findUiObject(
                                getUiElementFromConfig(AutomotiveConfigConstants.RADIO_BUTTON));
        getSpectatioUiUtil()
                .validateUiObject(radioButtonStatus, AutomotiveConfigConstants.RADIO_BUTTON);
        return radioButtonStatus.isChecked();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isStartAndroidAutoPopUpPresent() {
        BySelector startAndroidAutoPopUpHeader =
                getUiElementFromConfig(AutomotiveConfigConstants.START_ANDROID_AUTO_POPUP);
        return getSpectatioUiUtil().hasUiElement(startAndroidAutoPopUpHeader);
    }

    /** {@inheritDoc} */
    @Override
    public void skipStartAndroidAutoPopUp() {
        UiObject2 notNowButton =
                getSpectatioUiUtil()
                        .findUiObject(getUiElementFromConfig(AutomotiveConfigConstants.NOT_NOW_START_ANDROID_AUTO_POPUP_BUTTON));
        getSpectatioUiUtil().validateUiObject(notNowButton, AutomotiveConfigConstants.NOT_NOW_START_ANDROID_AUTO_POPUP_BUTTON);
        getSpectatioUiUtil().clickAndWait(notNowButton);
        getSpectatioUiUtil().waitForIdle();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isAssistantImprovementPopUpPresent() {
        BySelector assistantImprovementPopUpHeader =
                getUiElementFromConfig(AutomotiveConfigConstants.ASSISTANT_IMPROVEMENT_POPUP);
        return getSpectatioUiUtil().hasUiElement(assistantImprovementPopUpHeader);
    }

    /** {@inheritDoc} */
    @Override
    public void skipImprovementCallingAndTextingPopUp() {
        UiObject2 continueButton =
                getSpectatioUiUtil()
                        .findUiObject(getUiElementFromConfig(AutomotiveConfigConstants.SKIP_ASSISTANT_IMPROVEMENT_PAGE_BUTTON));
        getSpectatioUiUtil().validateUiObject(continueButton, AutomotiveConfigConstants.SKIP_ASSISTANT_IMPROVEMENT_PAGE_BUTTON);
        getSpectatioUiUtil().clickAndWait(continueButton);
        getSpectatioUiUtil().waitForIdle();
    }
}
