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

import android.app.Instrumentation;
import android.platform.helpers.ScrollUtility.ScrollActions;
import android.platform.helpers.ScrollUtility.ScrollDirection;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiObject2;

import java.util.List;

/** Helper class for functional tests of Security settings */
public class SettingsSecurityHelperImpl extends AbstractStandardAppHelper
        implements IAutoSecuritySettingsHelper {

    private static final String CHOOSE_LOCK_TYPE = "Choose a lock type";
    private static final int KEY_ENTER = 66;
    private ScrollUtility mScrollUtility;
    private ScrollActions mScrollAction;
    private BySelector mBackwardButtonSelector;
    private BySelector mForwardButtonSelector;
    private BySelector mScrollableElementSelector;
    private ScrollDirection mScrollDirection;

    public SettingsSecurityHelperImpl(Instrumentation instr) {
        super(instr);
        mScrollUtility = ScrollUtility.getInstance(getSpectatioUiUtil());
        mScrollAction =
                ScrollActions.valueOf(
                        getActionFromConfig(
                                AutomotiveConfigConstants.SECURITY_SETTINGS_SCROLL_ACTION));
        mBackwardButtonSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.SECURITY_SETTINGS_SCROLL_BACKWARD);
        mForwardButtonSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.SECURITY_SETTINGS_SCROLL_FORWARD);
        mScrollableElementSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.SECURITY_SETTINGS_SCROLL_ELEMENT);
        mScrollDirection =
                ScrollDirection.valueOf(
                        getActionFromConfig(
                                AutomotiveConfigConstants.SECURITY_SETTINGS_SCROLL_DIRECTION));
    }

    /** {@inheritDoc} */
    @Override
    public String getPackage() {
        return getPackageFromConfig(AutomotiveConfigConstants.SECURITY_SETTINGS_PACKAGE);
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
    public void setLockByPassword(String password) {
        openChooseLockTypeMenu();
        BySelector password_menuSelector =
                getUiElementFromConfig(
                        AutomotiveConfigConstants.SECURITY_SETTINGS_LOCK_TYPE_PASSWORD);
        UiObject2 password_menu = getSpectatioUiUtil().findUiObject(password_menuSelector);
        getSpectatioUiUtil().clickAndWait(password_menu);
        getSpectatioUiUtil().wait5Seconds();
        typePasswordOnTextEditor(password);
        pressEnter();
        typePasswordOnTextEditor(password);
        pressEnter();
    }

    private void openChooseLockTypeMenu() {
        getSpectatioUiUtil().wait5Seconds();
        BySelector titlesSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.SECURITY_SETTINGS_TITLE);
        List<UiObject2> titles = getSpectatioUiUtil().findUiObjects(titlesSelector);
        getSpectatioUiUtil()
                .validateUiObjects(titles, AutomotiveConfigConstants.SECURITY_SETTINGS_TITLE);
        UiObject2 title = titles.get(titles.size() - 1);
        if (title != null && title.getText().equalsIgnoreCase(CHOOSE_LOCK_TYPE)) {
            // CHOOSE_LOCK_TYPE is already open
            return;
        }
        BySelector profileLockMenuSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.SECURITY_SETTINGS_PROFILE_LOCK);
        UiObject2 profileLockMenu = getSpectatioUiUtil().findUiObject(profileLockMenuSelector);
        getSpectatioUiUtil()
                .validateUiObject(
                        profileLockMenu, String.format("Profile Lock %s", profileLockMenuSelector));
        getSpectatioUiUtil().clickAndWait(profileLockMenu);
            getSpectatioUiUtil().wait5Seconds();
    }

    private void typePasswordOnTextEditor(String password) {
        BySelector textEditorSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.SECURITY_SETTINGS_ENTER_PASSWORD);
        UiObject2 textEditor = getSpectatioUiUtil().findUiObject(textEditorSelector);
        getSpectatioUiUtil()
                .validateUiObject(
                        textEditor, AutomotiveConfigConstants.SECURITY_SETTINGS_ENTER_PASSWORD);
        textEditor.setText(password);
    }

    /** {@inheritDoc} */
    @Override
    public void setLockByPin(String pin) {
        openChooseLockTypeMenu();
        BySelector pin_menuSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.SECURITY_SETTINGS_LOCK_TYPE_PIN);
        UiObject2 pin_menu = getSpectatioUiUtil().findUiObject(pin_menuSelector);
        getSpectatioUiUtil()
                .validateUiObject(
                        pin_menu, AutomotiveConfigConstants.SECURITY_SETTINGS_LOCK_TYPE_PIN);
        getSpectatioUiUtil().clickAndWait(pin_menu);
        getSpectatioUiUtil().wait5Seconds();
        selectPinOnPinPad(pin);
        BySelector continue_buttonSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.SECURITY_SETTINGS_CONTINUE_BUTTON);
        UiObject2 continue_button = getSpectatioUiUtil().findUiObject(continue_buttonSelector);
        getSpectatioUiUtil()
                .validateUiObject(
                        continue_button,
                        AutomotiveConfigConstants.SECURITY_SETTINGS_CONTINUE_BUTTON);
        getSpectatioUiUtil().clickAndWait(continue_button);
        getSpectatioUiUtil().wait5Seconds();
        selectPinOnPinPad(pin);
        BySelector confirm_buttonSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.SECURITY_SETTINGS_CONFIRM_BUTTON);
        UiObject2 confirm_button = getSpectatioUiUtil().findUiObject(confirm_buttonSelector);
        getSpectatioUiUtil()
                .validateUiObject(
                        confirm_button, AutomotiveConfigConstants.SECURITY_SETTINGS_CONFIRM_BUTTON);
        getSpectatioUiUtil().clickAndWait(confirm_button);
        getSpectatioUiUtil().wait5Seconds();
    }

    private void selectPinOnPinPad(String pin) {
        int length = pin.length();
        for (int i = 0; i < length; i++) {
            char c = pin.charAt(i);
            UiObject2 number =
                    getSpectatioUiUtil()
                            .findUiObject(getUiElementFromConfig(Character.toString(c)));
            if (number == null) {
                number = getSpectatioUiUtil().findUiObject(By.text(Character.toString(c)));
            }
            getSpectatioUiUtil()
                    .validateUiObject(
                            number,
                            String.format(
                                    "Unable to find number on pin pad: " + Character.toString(c)));
            getSpectatioUiUtil().clickAndWait(number);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void unlockByPassword(String password) {
        BySelector textEditorSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.SECURITY_SETTINGS_ENTER_PASSWORD);
        UiObject2 textEditor = getSpectatioUiUtil().findUiObject(textEditorSelector);
        getSpectatioUiUtil()
                .validateUiObject(
                        textEditor, AutomotiveConfigConstants.SECURITY_SETTINGS_ENTER_PASSWORD);
        textEditor.setText(password);
        pressEnter();
    }

    /** {@inheritDoc} */
    @Override
    public void unlockByPin(String pin) {

        selectPinOnPinPad(pin);
        BySelector enter_buttonSelector =
                getUiElementFromConfig(
                        AutomotiveConfigConstants.SECURITY_SETTINGS_ENTER_PIN_BUTTON);
        UiObject2 enter_button = getSpectatioUiUtil().findUiObject(enter_buttonSelector);
        getSpectatioUiUtil()
                .validateUiObject(
                        enter_button, AutomotiveConfigConstants.SECURITY_SETTINGS_ENTER_PIN_BUTTON);
        getSpectatioUiUtil().clickAndWait(enter_button);
        BySelector pinPadSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.SECURITY_SETTINGS_PIN_PAD);
        UiObject2 pinPad = getSpectatioUiUtil().findUiObject(pinPadSelector);
        if (pinPad != null) {
            throw new RuntimeException("PIN input is not corrected");
        }
    }

    /** {@inheritDoc} */
    @Override
    public void removeLock() {

        BySelector none_menuSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.SECURITY_SETTINGS_LOCK_TYPE_NONE);
        UiObject2 none_menu = getSpectatioUiUtil().findUiObject(none_menuSelector);
        getSpectatioUiUtil()
                .validateUiObject(
                        none_menu, AutomotiveConfigConstants.SECURITY_SETTINGS_LOCK_TYPE_NONE);
        getSpectatioUiUtil().clickAndWait(none_menu);
        getSpectatioUiUtil().wait5Seconds();
        BySelector remove_buttonSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.SECURITY_SETTINGS_REMOVE_BUTTON);
        UiObject2 remove_button = getSpectatioUiUtil().findUiObject(remove_buttonSelector);
        getSpectatioUiUtil()
                .validateUiObject(
                        remove_button, AutomotiveConfigConstants.SECURITY_SETTINGS_REMOVE_BUTTON);
        getSpectatioUiUtil().clickAndWait(remove_button);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isDeviceLocked() {
        openChooseLockTypeMenu();
        BySelector textEditorSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.SECURITY_SETTINGS_ENTER_PASSWORD);
        UiObject2 textEditor = getSpectatioUiUtil().findUiObject(textEditorSelector);

        BySelector pinPadSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.SECURITY_SETTINGS_PIN_PAD);
        UiObject2 pinPad = getSpectatioUiUtil().findUiObject(pinPadSelector);

        return textEditor != null || pinPad != null;
    }

    protected void pressEnter() {
        getSpectatioUiUtil().pressKeyCode(KEY_ENTER);
    }
}
