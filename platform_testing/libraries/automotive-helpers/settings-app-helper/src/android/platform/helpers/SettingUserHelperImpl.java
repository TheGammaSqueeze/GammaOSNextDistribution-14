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
import android.platform.helpers.ScrollUtility.ScrollActions;
import android.platform.helpers.ScrollUtility.ScrollDirection;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiObject2;

/** Implementation of {@link IAutoUserHelper} to support tests of account settings */
public class SettingUserHelperImpl extends AbstractStandardAppHelper implements IAutoUserHelper {

    // constants
    private static final String TAG = "SettingUserHelperImpl";

    private ScrollUtility mScrollUtility;
    private ScrollActions mScrollAction;
    private BySelector mBackwardButtonSelector;
    private BySelector mForwardButtonSelector;
    private BySelector mScrollableElementSelector;
    private ScrollDirection mScrollDirection;
    private UiObject2 mEnableOption;

    public SettingUserHelperImpl(Instrumentation instr) {
        super(instr);
        mScrollUtility = ScrollUtility.getInstance(getSpectatioUiUtil());
        mScrollAction =
                ScrollActions.valueOf(
                        getActionFromConfig(AutomotiveConfigConstants.USER_SETTINGS_SCROLL_ACTION));
        mBackwardButtonSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.USER_SETTINGS_SCROLL_BACKWARD);
        mForwardButtonSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.USER_SETTINGS_SCROLL_FORWARD);
        mScrollableElementSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.USER_SETTINGS_SCROLL_ELEMENT);
        mScrollDirection =
                ScrollDirection.valueOf(
                        getActionFromConfig(
                                AutomotiveConfigConstants.USER_SETTINGS_SCROLL_DIRECTION));
        mScrollUtility.setScrollValues(
                Integer.valueOf(
                        getActionFromConfig(AutomotiveConfigConstants.USER_SETTINGS_SCROLL_MARGIN)),
                Integer.valueOf(
                        getActionFromConfig(
                                AutomotiveConfigConstants.USER_SETTINGS_SCROLL_WAIT_TIME)));
    }

    /** {@inheritDoc} */
    @Override
    public String getPackage() {
        return getPackageFromConfig(AutomotiveConfigConstants.USER_SETTINGS_PACKAGE);
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
    // Add a new user
    @Override
    public void addUser() {
        clickbutton(AutomotiveConfigConstants.USER_SETTINGS_ADD_PROFILE);
        clickbutton(AutomotiveConfigConstants.USER_SETTINGS_OK);
        getSpectatioUiUtil().wait5Seconds();
    }
    // opens permission page of a user
    @Override
    public void openPermissionsPage(String user) {
        if (isUserPresent(user)) {
            BySelector userSelector = By.text(user);
            UiObject2 userObject = getSpectatioUiUtil().findUiObject(userSelector);
            getSpectatioUiUtil().validateUiObject(userObject, String.format("User %s", user));
            getSpectatioUiUtil().clickAndWait(userObject);
        }
    }

    // delete an existing user
    @Override
    public void deleteUser(String user) {
        if (isUserPresent(user)) {
            BySelector userSelector = By.text(user);
            UiObject2 userObject = getSpectatioUiUtil().findUiObject(userSelector);
            getSpectatioUiUtil().validateUiObject(userObject, String.format("User %s", user));
            getSpectatioUiUtil().clickAndWait(userObject);
            clickbutton(AutomotiveConfigConstants.USER_SETTINGS_DELETE);
            clickbutton(AutomotiveConfigConstants.USER_SETTINGS_DELETE);
            getSpectatioUiUtil().wait5Seconds();
        }
    }

    // delete self User
    @Override
    public void deleteCurrentUser() {
        clickbutton(AutomotiveConfigConstants.USER_SETTINGS_DELETE_SELF);
        clickbutton(AutomotiveConfigConstants.USER_SETTINGS_DELETE);
        getSpectatioUiUtil().wait5Seconds();
    }

    /** {@inheritDoc} */
    // check if a user is present in the list of existing users
    @Override
    public boolean isUserPresent(String user) {
        BySelector buttonSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.USER_SETTINGS_ADD_PROFILE);
        UiObject2 addUserButton =
                mScrollUtility.scrollAndFindUiObject(
                        mScrollAction,
                        mScrollDirection,
                        mForwardButtonSelector,
                        mBackwardButtonSelector,
                        mScrollableElementSelector,
                        buttonSelector,
                        String.format("Scroll to find %s", user));
        // Log.v(
        //         TAG,
        //        String.format(
        //              "AddProfileButton = %s ; UI_Obj = %s",
        //             AutomotiveConfigConstants.USER_SETTINGS_ADD_PROFILE, addUserButton));
        if (addUserButton == null) {
            clickbutton(AutomotiveConfigConstants.USER_SETTINGS_MANAGE_OTHER_PROFILES);
            getSpectatioUiUtil().wait5Seconds();
            UiObject2 UserObject =
                    mScrollUtility.scrollAndFindUiObject(
                            mScrollAction,
                            mScrollDirection,
                            mForwardButtonSelector,
                            mBackwardButtonSelector,
                            mScrollableElementSelector,
                            By.text(user),
                            String.format("Scroll to find %s", user));
            getSpectatioUiUtil().wait1Second();
            return UserObject != null;
        }
        return false;
    }

    // switch User from current user to given user
    @Override
    public void switchUser(String userFrom, String userTo) {
        goToQuickSettings();
        BySelector userFromSelector = By.text(userFrom);
        UiObject2 userFromObject = getSpectatioUiUtil().findUiObject(userFromSelector);
        getSpectatioUiUtil().validateUiObject(userFromObject, String.format("User %s", userFrom));
        getSpectatioUiUtil().clickAndWait(userFromObject);
        BySelector userToSelector = By.text(userTo);
        UiObject2 userToObject = getSpectatioUiUtil().findUiObject(userToSelector);
        getSpectatioUiUtil().validateUiObject(userToObject, String.format("User %s", userTo));
        getSpectatioUiUtil().clickAndWait(userToObject);
        getSpectatioUiUtil().wait5Seconds();
    }

    // add User via quick settings
    @Override
    public void addUserQuickSettings(String userFrom) {
        goToQuickSettings();
        BySelector userFromSelector = By.text(userFrom);
        UiObject2 userFromObject = getSpectatioUiUtil().findUiObject(userFromSelector);
        getSpectatioUiUtil().validateUiObject(userFromObject, String.format("user %s", userFrom));
        getSpectatioUiUtil().wait1Second();
        addUser();
    }

    // make an existing user admin
    @Override
    public void makeUserAdmin(String user) {
        if (isUserPresent(user)) {
            BySelector userSelector = By.text(user);
            UiObject2 userObject = getSpectatioUiUtil().findUiObject(userSelector);
            getSpectatioUiUtil().validateUiObject(userObject, String.format("user %s", user));
            getSpectatioUiUtil().clickAndWait(userObject);
            clickbutton(AutomotiveConfigConstants.USER_SETTINGS_MAKE_ADMIN);
            clickbutton(AutomotiveConfigConstants.USER_SETTINGS_MAKE_ADMIN_CONFIRM);
        }
    }

    // click an on-screen element if expected text for that element is present
    private void clickbutton(String buttonText) {
        BySelector buttonSelector = getUiElementFromConfig(buttonText);
        UiObject2 buttonObject =
                mScrollUtility.scrollAndFindUiObject(
                        mScrollAction,
                        mScrollDirection,
                        mForwardButtonSelector,
                        mBackwardButtonSelector,
                        mScrollableElementSelector,
                        buttonSelector,
                        String.format("Scroll to find %s", buttonText));
        // Log.v(TAG, String.format("button =  %s ; UI_Obj = %s", buttonText, buttonObject));
        getSpectatioUiUtil().validateUiObject(buttonObject, String.format("button %s", buttonText));
        getSpectatioUiUtil().clickAndWait(buttonObject);
        getSpectatioUiUtil().wait1Second();
    }

    @Override
    public boolean isNewUserAnAdmin(String user) {
        boolean isUserAdmin = true;
        clickbutton(AutomotiveConfigConstants.USER_SETTINGS_MANAGE_OTHER_PROFILES);
        getSpectatioUiUtil().wait5Seconds();
        UiObject2 UserObject =
                mScrollUtility.scrollAndFindUiObject(
                        mScrollAction,
                        mScrollDirection,
                        mForwardButtonSelector,
                        mBackwardButtonSelector,
                        mScrollableElementSelector,
                        By.text(user),
                        String.format("Scroll to find %s", user));
        getSpectatioUiUtil().clickAndWait(UserObject);
        getSpectatioUiUtil().wait1Second();
        if (getSpectatioUiUtil()
                .hasUiElement(
                        getUiElementFromConfig(
                                AutomotiveConfigConstants.USER_SETTINGS_MAKE_ADMIN))) {
            return !isUserAdmin;
        }
        return isUserAdmin;
    }

    // go to quick Settings for switching User
    private void goToQuickSettings() {
        clickbutton(AutomotiveConfigConstants.USER_SETTINGS_MAKE_TIME_PATTERN);
    }
    // checks whether the Add profile button is visible
    @Override
    public boolean isVisibleAddProfile() {
        boolean isVisibleAddProfile = true;
        if (getSpectatioUiUtil()
                .hasUiElement(
                        getUiElementFromConfig(
                                AutomotiveConfigConstants.USER_SETTINGS_ADD_PROFILE))) {
            return isVisibleAddProfile;
        }
        return !isVisibleAddProfile;
    }

    @Override
    public boolean isToggleOn(String buttonText) {
        BySelector mEnableOptionSelector = getUiElementFromConfig(buttonText);
        mEnableOption =
                mScrollUtility.scrollAndFindUiObject(
                        mScrollAction,
                        mScrollDirection,
                        mForwardButtonSelector,
                        mBackwardButtonSelector,
                        mScrollableElementSelector,
                        mEnableOptionSelector,
                        String.format("Scroll to find %s", buttonText));
        getSpectatioUiUtil().validateUiObject(mEnableOption, buttonText);
        return mEnableOption.isChecked();
    }
    // Toggles the switch from one mode to the other
    @Override
    public boolean toggle(String buttonText) {
        boolean currentStatus = isToggleOn(buttonText);
        getSpectatioUiUtil().clickAndWait(mEnableOption);
        boolean finalStatus = isToggleOn(buttonText);
        return finalStatus != currentStatus;
    }
}
