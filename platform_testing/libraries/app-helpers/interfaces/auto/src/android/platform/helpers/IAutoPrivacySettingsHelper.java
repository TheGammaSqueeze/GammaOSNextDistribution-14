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

/** Interface class for privacy setting helper */
public interface IAutoPrivacySettingsHelper extends IAppHelper {
    /** enum for permission allow or don't allow state */
    enum Permission {
        ALLOW,
        ALLOW_ALL_THE_TIME,
        ALLOW_WHILE_USING_APP,
        DONT_ALLOW
    }

    /**
     * Setup expectation: MicroPhone settings is open.
     *
     * <p>This method turns on and Off the MicroPhone.
     */
    void turnOnOffMicroPhone(boolean onOff);

    /**
     * Setup expectation: MicroPhone settings is open.
     *
     * <p>This method checks if MicroPhone is turned on or off.
     */
    boolean isMicroPhoneOn();

    /**
     * Setup expectation: MicroPhone Muted.
     *
     * <p>This method checks if muted MicroPhone Chip is present on Status bar.
     */
    boolean isMutedMicChipPresentOnStatusBar();

    /**
     * Setup expectation: MicroPhone unMuted.
     *
     * <p>This method checks if muted MicroPhone Chip is present on Status bar when micphrone panel
     * is opened .
     */
    boolean isMutedMicChipPresentWithMicPanel();

    /**
     * Setup expectation: None.
     *
     * <p>This method checks if MicroPhone Chip(dark/light) is present on Status bar.
     */
    boolean isMicChipPresentOnStatusBar();

    /**
     * Setup expectation: MicroPhone settings is open.
     *
     * <p>This method taps microphone button in status bar.
     */
    void clickMicroPhoneStatusBar();

    /**
     * Setup expectation: MicroPhone panel is open.
     *
     * <p>This method checks if Micro Phone Settings links is present in the micro phone panel
     */
    boolean isMicroPhoneSettingsLinkPresent();

    /**
     * Setup expectation: MicroPhone panel is open.
     *
     * <p>This method taos on Micro Phone Settings links is present in the micro phone panel
     */
    void clickMicroPhoneSettingsLink();

    /**
     * Setup expectation: MicroPhone panel is open.
     *
     * <p>This method checks if Micro Phone Toggle is present in the panel
     */
    boolean isMicroPhoneTogglePresent();

    /**
     * Setup expectation: MicroPhone panel is open.
     *
     * <p>This method taps on micro phone toggle
     */
    void clickMicroPhoneToggleStatusBar();

    /**
     * Setup expectation: MicroPhone Settings is open and no apps has used microphone recently.
     *
     * <p>This method checks if no recent apps is present.
     */
    boolean verifyNoRecentAppsPresent();

    /**
     * Setup expectation: Micro Phone Panel is open
     *
     * <p>This method checks correct micro phone status is displayed in Micro Phone Panel.
     */
    boolean isMicroPhoneStatusMessageUpdated(String status);

    /**
     * Setup expectation: MicroPhone settings is open
     *
     * <p>This method taps on Micro Phone Permissions
     */
    void clickManageMicroPhonePermissions();

    /**
     * Setup expectation: MicroPhone settings is open, google account is added.
     *
     * <p>This method checks if Activity Control Page is displayed.
     */
    boolean isManageActivityControlOpen();

    /**
     * Setup expectation: MicroPhone settings is open, no google account is added
     *
     * <p>This method checks if No account added dialog is displayed.
     */
    boolean isNoAccountAddedDialogOpen();

    /**
     * Setup expectation: MicroPhone settings is open, no google account is added
     *
     * <p>This method checks if add account text is displayed on Autofill Page
     */
    boolean isAccountAddedAutofill();

    /**
     * Setup expectation: None
     *
     * <p>This method checks if Recently accessed apps is displayed in MicroPhone settings with
     * timestamp
     */
    boolean isRecentAppDisplayedWithStamp(String app);

    /**
     * Setup expectation: MicroPhone Settings is open
     *
     * <p>This method clicks on ViewAll Link.
     */
    void clickViewAllLink();

    /**
     * Setup expectation: open Google assistant app, microphone is on
     *
     * <p>Tap on unmuted microphone status bar
     */
    void clickUnMutedMicroPhoneStatusBar();

    /**
     * Setup expectation: Microphone status bar is opened
     *
     * <p>This method clicks on microphone on status bar
     *
     * @param target is config constant of the Status message
     */
    boolean verifyMicrophoneStatusMessage(String target);

    /**
     * Setup expectation: Privacy Dashboard/Permission is open for given app
     *
     * <p>This method opens Privacy Dashboard/permissions page for given app
     *
     * @param target app name to open permission page in Privacy dashboard/permission
     */
    void privacyDashboardPermissionPage(String target);

    /**
     * Setup expectation: Get the Permission decision
     *
     * <p>This method will get the text from App Permission
     *
     * @param appName of the app that decision message need to be verified
     */
    String getRecentPermissionDecisionMessage(String appName);

    /**
     * Setup expectation: Get the Permission decision
     *
     * <p>This method will click on given permission
     *
     * @param permission Permission status allow or don't allow
     */
    void changePermissions(Permission permission);

    /**
     * Setup expectation: Message should be displayed
     *
     * <p>This method will click while changing permission message is displayed
     */
    boolean isMessageDisplayed();

    /**
     * Setup expectation: Cancel the Message displayed
     *
     * <p>This method will click on cancel button
     */
    void cancelButton();

    /**
     * Setup expectation: Permission Allow
     *
     * <p>To verify if Permission default status is Allow
     */
    boolean isAllowDefaultPermission();

    /**
     * Setup expectation: verify microphone manage permissions page
     *
     * <p>To verify if microphone manage permissions page is displayed
     */
    boolean verifyMicrophoneManagePermissionsPage();

    /**
     * Setup expectation: Start Android Auto pop-up displayed
     *
     * <p>To verify if Start Android Auto pop-up displayed
     */
    boolean isStartAndroidAutoPopUpPresent();

    /**
     * Setup expectation: Assistant improvement pop-up displayed
     *
     * <p>To verify if Assistant improvement pop-up displayed
     */
    boolean isAssistantImprovementPopUpPresent();

    /**
     * Setup expectation: Start Android Auto pop-up displayed
     *
     * <p>This method will click on Not Now button
     */
    void skipStartAndroidAutoPopUp();

    /**
     * Setup expectation: Use contact names pop-uo displayed
     *
     * <p>This method will click on Not Now button
     */
    void skipImprovementCallingAndTextingPopUp();

}
