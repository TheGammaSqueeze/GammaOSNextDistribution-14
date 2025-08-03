/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static junit.framework.Assert.assertTrue;

import android.app.Instrumentation;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.platform.helpers.ScrollUtility.ScrollActions;
import android.platform.helpers.ScrollUtility.ScrollDirection;
import android.util.Log;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.UiObject2;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** App info settings helper file */
public class SettingsAppInfoHelperImpl extends AbstractStandardAppHelper
        implements IAutoAppInfoSettingsHelper {

    private static final int MAX_SCROLL_COUNT = 100;
    private static final String LOGGING_TAG = "SettingsAppInfoHelperImpl";

    private static final String PERMISSION_HEADER = "Apps with this permission";
    private ScrollUtility mScrollUtility;
    private ScrollActions mScrollAction;
    private BySelector mBackwardButtonSelector;
    private BySelector mForwardButtonSelector;
    private BySelector mAppInfoPermissionsScrollableElementSelector;
    private BySelector mScrollableElementSelector;
    private ScrollDirection mScrollDirection;

    public SettingsAppInfoHelperImpl(Instrumentation instr) {
        super(instr);
        mScrollUtility = ScrollUtility.getInstance(getSpectatioUiUtil());
        mScrollAction =
                ScrollActions.valueOf(
                        getActionFromConfig(
                                AutomotiveConfigConstants.APP_INFO_SETTINGS_SCROLL_ACTION));
        mBackwardButtonSelector =
                getUiElementFromConfig(
                        AutomotiveConfigConstants.APP_INFO_SETTINGS_SCROLL_BACKWARD_BUTTON);
        mForwardButtonSelector =
                getUiElementFromConfig(
                        AutomotiveConfigConstants.APP_INFO_SETTINGS_SCROLL_FORWARD_BUTTON);
        mScrollableElementSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.APP_INFO_SETTINGS_SCROLL_ELEMENT);
        mAppInfoPermissionsScrollableElementSelector =
                getUiElementFromConfig(
                        AutomotiveConfigConstants.APP_INFO_SETTINGS_PERMISSIONS_SCROLL_ELEMENT);
        mScrollDirection =
                ScrollDirection.valueOf(
                        getActionFromConfig(
                                AutomotiveConfigConstants.APP_INFO_SETTINGS_SCROLL_DIRECTION));
        mScrollUtility.setScrollValues(
                Integer.valueOf(
                        getActionFromConfig(
                                AutomotiveConfigConstants.APP_INFO_SETTINGS_SCROLL_MARGIN)),
                Integer.valueOf(
                        getActionFromConfig(
                                AutomotiveConfigConstants.APP_INFO_SETTINGS_SCROLL_WAIT_TIME)));
    }

    /** {@inheritDoc} */
    @Override
    public void open() {
        getSpectatioUiUtil()
                .executeShellCommand(
                        getCommandFromConfig(AutomotiveConfigConstants.OPEN_SETTINGS_COMMAND));
    }

    /** {@inheritDoc} */
    @Override
    public String getPackage() {
        return getPackageFromConfig(AutomotiveConfigConstants.SETTINGS_PACKAGE);
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
    public void showAllApps() {
        BySelector selector =
                By.clickable(true)
                        .hasDescendant(
                                getUiElementFromConfig(
                                        AutomotiveConfigConstants.APP_INFO_SETTINGS_VIEW_ALL));
        UiObject2 show_all_apps_menu =
                mScrollUtility.scrollAndFindUiObject(
                        mScrollAction,
                        mScrollDirection,
                        mForwardButtonSelector,
                        mBackwardButtonSelector,
                        mScrollableElementSelector,
                        selector,
                        "Scroll on Apps to find View all button");
        getSpectatioUiUtil().validateUiObject(show_all_apps_menu, "View all button");
        getSpectatioUiUtil().clickAndWait(show_all_apps_menu);
    }

    /** {@inheritDoc} */
    @Override
    public void openPermissionManager() {
        BySelector permissions_selector =
                getUiElementFromConfig(
                        AutomotiveConfigConstants.APP_INFO_SETTINGS_PERMISSION_MANAGER);

        final String action = "Scroll to find the Permission manager";
        UiObject2 permissionsManager =
                mScrollUtility.scrollAndFindUiObject(
                        mScrollAction,
                        mScrollDirection,
                        mForwardButtonSelector,
                        mBackwardButtonSelector,
                        mScrollableElementSelector,
                        permissions_selector,
                        action);

        getSpectatioUiUtil().validateUiObject(permissionsManager, action);
        getSpectatioUiUtil().clickAndWait(permissionsManager);
    }

    /**
     * Assumes we are on the Permission manager settings page. Returns the summary (text field in
     * form "[X] of [Y] apps allowed") for a given permission.
     *
     * @param permissionName - The permission to be summarized
     * @return - The text field holding the permission's summary (how many apps are allowed).
     */
    private UiObject2 getPermissionSummary(String permissionName) {
        BySelector permissionSummarySelector =
                getUiElementFromConfig(
                        AutomotiveConfigConstants.APP_INFO_SETTINGS_SINGLE_PERMISSION_SUMMARY);

        BySelector permissionObjectSelector =
                permissionSummarySelector.hasChild(By.text(permissionName));

        getSpectatioUiUtil().scrollToBeginning(mAppInfoPermissionsScrollableElementSelector, true);
        UiObject2 permissionSummary =
                mScrollUtility.scrollAndFindUiObject(
                        mScrollAction,
                        mScrollDirection,
                        mForwardButtonSelector,
                        mBackwardButtonSelector,
                        mAppInfoPermissionsScrollableElementSelector,
                        permissionObjectSelector,
                        String.format(
                                "Scroll on Permission manager to find permission: %s",
                                permissionName));

        return permissionSummary;
    }

    /** {@inheritDoc} */
    @Override
    public void enableDisableApplication(State state) {
        BySelector enableDisableBtnSelector =
                getUiElementFromConfig(
                        AutomotiveConfigConstants.APP_INFO_SETTINGS_ENABLE_DISABLE_BUTTON);
        UiObject2 enableDisableBtn =
                mScrollUtility.scrollAndFindUiObject(
                        mScrollAction,
                        mScrollDirection,
                        mForwardButtonSelector,
                        mBackwardButtonSelector,
                        mScrollableElementSelector,
                        enableDisableBtnSelector,
                        String.format("Scroll on App info to find %s", state));
        getSpectatioUiUtil()
                .validateUiObject(enableDisableBtn, String.format("Enable Disable Button"));
        getSpectatioUiUtil().clickAndWait(enableDisableBtn.getParent());
        if (state == State.ENABLE) {
            assertTrue(
                    "application is not enabled",
                    enableDisableBtn
                            .getText()
                            .matches(
                                    "(?i)"
                                            + AutomotiveConfigConstants
                                                    .APP_INFO_SETTINGS_DISABLE_BUTTON_TEXT));
        } else {
            BySelector disableAppBtnSelector =
                    getUiElementFromConfig(
                            AutomotiveConfigConstants.APP_INFO_SETTINGS_DISABLE_APP_BUTTON);
            UiObject2 disableAppBtn = getSpectatioUiUtil().findUiObject(disableAppBtnSelector);
            getSpectatioUiUtil()
                    .validateUiObject(disableAppBtn, String.format("Disable app button"));
            getSpectatioUiUtil().clickAndWait(disableAppBtn);
            assertTrue(
                    "application is not disabled",
                    enableDisableBtn
                            .getText()
                            .matches(
                                    "(?i)"
                                            + AutomotiveConfigConstants
                                                    .APP_INFO_SETTINGS_ENABLE_BUTTON_TEXT));
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean isCurrentApplicationRunning() {
        UiObject2 forceStopButton = getForceStopButton();
        if (forceStopButton == null) {
            throw new RuntimeException("Cannot find force stop button");
        }
        return forceStopButton.isEnabled() ? true : false;
    }

    /** {@inheritDoc} */
    @Override
    public void forceStop() {
        UiObject2 forceStopButton = getForceStopButton();
        getSpectatioUiUtil().validateUiObject(forceStopButton, "force stop button");
        getSpectatioUiUtil().clickAndWait(forceStopButton);
        BySelector okBtnSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.APP_INFO_SETTINGS_OK_BUTTON);
        UiObject2 okBtn = getSpectatioUiUtil().findUiObject(okBtnSelector);
        getSpectatioUiUtil().validateUiObject(okBtn, "Ok button");
        getSpectatioUiUtil().clickAndWait(okBtn);
    }

    /** {@inheritDoc} */
    @Override
    public void setAppPermission(String permission, State state) {
        BySelector permissions_selector =
                getUiElementFromConfig(
                        AutomotiveConfigConstants.APP_INFO_SETTINGS_PERMISSIONS_MENU);
        UiObject2 permissions_menu =
                mScrollUtility.scrollAndFindUiObject(
                        mScrollAction,
                        mScrollDirection,
                        mForwardButtonSelector,
                        mBackwardButtonSelector,
                        mAppInfoPermissionsScrollableElementSelector,
                        permissions_selector,
                        String.format("Scroll on %s permission to find %s", permission, state));
        getSpectatioUiUtil().clickAndWait(permissions_menu);
        getSpectatioUiUtil().waitForIdle();
        BySelector permission_selector = By.text(permission);
        UiObject2 permission_menu =
                mScrollUtility.scrollAndFindUiObject(
                        mScrollAction,
                        mScrollDirection,
                        mForwardButtonSelector,
                        mBackwardButtonSelector,
                        mScrollableElementSelector,
                        permission_selector,
                        String.format("Scroll on %s permission to find %s", permission, state));
        if (permission_menu == null) {
            throw new RuntimeException("Cannot find the permission_selector" + permission);
        }
        getSpectatioUiUtil().clickAndWait(permission_menu);
        getSpectatioUiUtil().waitForIdle();
        getSpectatioUiUtil().wait1Second();
        if (state == State.ENABLE) {
            UiObject2 allow_btn =
                    getSpectatioUiUtil()
                            .findUiObject(
                                    getUiElementFromConfig(
                                            AutomotiveConfigConstants
                                                    .APP_INFO_SETTINGS_ALLOW_BUTTON));
            getSpectatioUiUtil().validateUiObject(allow_btn, "Allow button");
            getSpectatioUiUtil().clickAndWait(allow_btn);
        } else {
            UiObject2 dont_allow_btn =
                    getSpectatioUiUtil()
                            .findUiObject(
                                    getUiElementFromConfig(
                                            AutomotiveConfigConstants
                                                    .APP_INFO_SETTINGS_DONT_ALLOW_BUTTON));
            getSpectatioUiUtil().clickAndWait(dont_allow_btn);
            getSpectatioUiUtil().wait1Second();
            UiObject2 dont_allow_anyway_btn =
                    getSpectatioUiUtil()
                            .findUiObject(
                                    getUiElementFromConfig(
                                            AutomotiveConfigConstants
                                                    .APP_INFO_SETTINGS_DONT_ALLOW_ANYWAY_BUTTON));
            getSpectatioUiUtil().clickAndWait(dont_allow_anyway_btn);
        }
        getSpectatioUiUtil().pressBack();
        getSpectatioUiUtil().pressBack();
    }

    /** {@inheritDoc} */
    @Override
    public String getCurrentPermissions() {
        BySelector permissions_selector =
                getUiElementFromConfig(
                        AutomotiveConfigConstants.APP_INFO_SETTINGS_PERMISSIONS_MENU);
        UiObject2 permission_menu =
                mScrollUtility.scrollAndFindUiObject(
                        mScrollAction,
                        mScrollDirection,
                        mForwardButtonSelector,
                        mBackwardButtonSelector,
                        mScrollableElementSelector,
                        permissions_selector,
                        "Scroll on App info to find permission menu");
        String currentPermissions = permission_menu.getParent().getChildren().get(1).getText();
        return currentPermissions;
    }

    /** {@inheritDoc} */
    @Override
    public void selectApp(String application) {

        BySelector applicationSelector = By.text(application);
        UiObject2 object =
                mScrollUtility.scrollAndFindUiObject(
                        mScrollAction,
                        mScrollDirection,
                        mForwardButtonSelector,
                        mBackwardButtonSelector,
                        mScrollableElementSelector,
                        applicationSelector,
                        String.format("Scroll on App info to find %s", application));
        getSpectatioUiUtil().validateUiObject(object, String.format("App %s", application));
        getSpectatioUiUtil().clickAndWait(object);
        getSpectatioUiUtil().wait5Seconds();
    }

    private UiObject2 getForceStopButton() {
        BySelector forceStopSelector =
                getUiElementFromConfig(
                        AutomotiveConfigConstants.APP_INFO_SETTINGS_FORCE_STOP_BUTTON);
        UiObject2 forceStopButton =
                mScrollUtility.scrollAndFindUiObject(
                        mScrollAction,
                        mScrollDirection,
                        mForwardButtonSelector,
                        mBackwardButtonSelector,
                        mScrollableElementSelector,
                        forceStopSelector,
                        "Scroll on App info to find force stop button");
        return forceStopButton;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isApplicationDisabled(String packageName) {
        boolean applicationDisabled = false;
        try {
            PackageManager pm = mInstrumentation.getContext().getPackageManager();
            applicationDisabled = !(pm.getApplicationInfo(packageName, 0).enabled);
        } catch (NameNotFoundException e) {
            throw new RuntimeException(String.format("Failed to find package: %s", packageName), e);
        }
        return applicationDisabled;
    }

    /** {@inheritDoc} */
    @Override
    public List<Integer> validateAppsPermissionManager(String permissionName) {

        UiObject2 permissionSummary = getPermissionSummary(permissionName);

        BySelector counterSelector =
                getUiElementFromConfig(
                        AutomotiveConfigConstants.APP_INFO_SETTINGS_PERMISSION_MANAGER_APP_COUNTER);

        // Get the text displaying the number of allowed apps.
        String appTally = permissionSummary.findObject(counterSelector).getText();
        // Parse out the number of apps (allowed and total)
        List<Integer> appNums = parseAppNumbers(appTally);

        // Check in the individual permission object for the listed apps
        // This call will click into the object for the check,
        // then click 'back' to return to the permission manager page
        // causing the permissionObject to expire.
        List<Integer> listedApps = numAppsAllowedAndTotal(permissionSummary);

        appNums.addAll(listedApps);
        return appNums;
    }

    /**
     * Assumes we are on a detailed permission screen (listing the apps that are allowed).
     *
     * @return An arraylist of two integers: the number of allowed apps listed, and the total number
     *     of apps listed.
     */
    private List<Integer> numAppsAllowedAndTotal(UiObject2 permissionObject) {

        getSpectatioUiUtil().clickAndWait(permissionObject);
        List<String> textFields = scrollForTextFields();
        int allowed = 0;
        int notAllowed = 0;

        boolean inAllowedList = false;

        for (String text : textFields) {

            switch (text) {
                case "Allowed":
                    inAllowedList = true;
                    break;
                case "Not allowed":
                    inAllowedList = false;
                    break;
                default:
                    if (inAllowedList) allowed++;
                    else notAllowed++;
                    break;
            }
        }

        List<Integer> appNums = new ArrayList<>();
        appNums.add(allowed);
        appNums.add(notAllowed + allowed);

        assertTrue(
                "Could not return from individual permission screen.",
                getSpectatioUiUtil().pressBack()); // Return to the general permissions screen

        return appNums;
    }

    /**
     * Assumes unique text fields on the permission app list screens, except for the first text
     * field, containing the permission title. For example, this method assumes that no two apps
     * share the same name, or share a name with the "Allowed" or "Not Allowed" tags.
     *
     * @return An arraylist of each text field on this (scrollable) view, in the order that it
     *     appears on screen.
     */
    private List<String> scrollForTextFields() {

        Set<String> textSeen = new HashSet<>();
        List<String> textFields = new ArrayList<>();

        BySelector layoutWithText =
                getUiElementFromConfig(
                        AutomotiveConfigConstants.APP_INFO_SETTINGS_APP_NAME_ELEMENT);
        BySelector permissionAppListView =
                getUiElementFromConfig(
                        AutomotiveConfigConstants.APP_INFO_SETTINGS_PERMISSION_APP_LIST_VIEW);

        // Get a complete list of text from UI elements.
        // While scrolling
        int scrollCount = 0;
        boolean canScroll = true;
        while (scrollCount < MAX_SCROLL_COUNT) {
            UiObject2 permissionView = getSpectatioUiUtil().findUiObject(permissionAppListView);
            getSpectatioUiUtil()
                    .validateUiObject(permissionView, "Searching for permission app list view.");
            List<UiObject2> layoutsWithText = permissionView.findObjects(layoutWithText);

            boolean newText = false;
            for (UiObject2 layout : layoutsWithText) {

                // Filter out the permission name header.
                if (layout.getChildCount() < 2
                        || !layout.getChildren().get(1).getText().contains(PERMISSION_HEADER)) {

                    UiObject2 textView = layout.getChildren().get(0);
                    String content = textView.getText();

                    // Scrolling may leave text fields on screen, so only unique values are added.
                    if (!textSeen.contains(content)) {
                        textFields.add(content);
                        textSeen.add(content);
                        newText = true;
                    }
                }
            }
            if (!canScroll || !newText) {
                break;
            }
            scrollCount++;
            canScroll = permissionView.scroll(Direction.DOWN, 0.6f);
        }

        // Debug statements
        Log.d(LOGGING_TAG, "Gathered text fields: \n " + textFields.toString());

        return textFields;
    }

    /**
     * @param appTally - The string containing the app permission summary ('X of Y apps allowed')
     * @return A list of all integers found in the String.
     */
    private List<Integer> parseAppNumbers(String appTally) {

        ArrayList<Integer> tallies = new ArrayList<Integer>();
        Pattern intPattern = Pattern.compile("\\d+");

        for (String token : appTally.split(" ")) {
            if (intPattern.matcher(token).matches()) {
                try {
                    tallies.add(Integer.parseInt(token));
                } catch (Exception e) {
                    Log.d(LOGGING_TAG, "Exception while parsing app numbers: " + e.toString());
                }
            }
        }

        return tallies;
    }
}
