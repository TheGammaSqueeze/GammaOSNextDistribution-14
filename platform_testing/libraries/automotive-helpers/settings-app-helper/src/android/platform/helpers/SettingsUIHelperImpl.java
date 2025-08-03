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

import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiObject2;

import java.util.List;

/** Settings UI Elements helper file */
public class SettingsUIHelperImpl extends AbstractStandardAppHelper
        implements IAutoUISettingsHelper {

    private ScrollUtility mScrollUtility;
    private ScrollActions mScrollAction;
    private BySelector mBackwardButtonSelector;
    private BySelector mForwardButtonSelector;
    private BySelector mScrollableElementSelector;
    private ScrollDirection mScrollDirection;

    public SettingsUIHelperImpl(Instrumentation instr) {
        super(instr);
        mScrollUtility = ScrollUtility.getInstance(getSpectatioUiUtil());
        mScrollAction =
                ScrollActions.valueOf(
                        getActionFromConfig(
                                AutomotiveConfigConstants.SETTINGS_SUB_SETTING_SCROLL_ACTION));
        mBackwardButtonSelector =
                getUiElementFromConfig(
                        AutomotiveConfigConstants.SETTINGS_SUB_SETTING_SCROLL_BACKWARD_BUTTON);
        mForwardButtonSelector =
                getUiElementFromConfig(
                        AutomotiveConfigConstants.SETTINGS_SUB_SETTING_SCROLL_FORWARD_BUTTON);
        mScrollableElementSelector =
                getUiElementFromConfig(
                        AutomotiveConfigConstants.SETTINGS_UI_SUB_SETTING_SCROLL_ELEMENT);
        mScrollDirection =
                ScrollDirection.valueOf(
                        getActionFromConfig(
                                AutomotiveConfigConstants.SETTINGS_SUB_SETTING_SCROLL_DIRECTION));
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
    public boolean hasUIElement(String element) {
        boolean isElementPresent;
        BySelector elementSelector = getUiElementFromConfig(element);
        isElementPresent = getSpectatioUiUtil().hasUiElement(elementSelector);
        if (!isElementPresent) {
            isElementPresent =
                    mScrollUtility.scrollAndCheckIfUiElementExist(
                            mScrollAction,
                            mScrollDirection,
                            mForwardButtonSelector,
                            mBackwardButtonSelector,
                            mScrollableElementSelector,
                            elementSelector,
                            "scroll and find UI Element");
        }
        return isElementPresent;
    }

    /** {@inheritDoc} */
    @Override
    public void openUIOptions(String targetConstant) {
        UiObject2 object = null;
        BySelector optionSelector = getUiElementFromConfig(targetConstant);
        List<UiObject2> optionObjects = getSpectatioUiUtil().findUiObjects(optionSelector);
        if (optionObjects.size() == 0) {
            object =
                    mScrollUtility.scrollAndFindUiObject(
                            mScrollAction,
                            mScrollDirection,
                            mForwardButtonSelector,
                            mBackwardButtonSelector,
                            mScrollableElementSelector,
                            optionSelector,
                            "scroll and find UI Element");
        }
        if (optionObjects.size() > 0) {
            object = optionObjects.get(optionObjects.size() - 1);
        }
        getSpectatioUiUtil()
                .validateUiObject(
                        object,
                        String.format("Unable to find UI Element for %s menu", targetConstant));
        getSpectatioUiUtil().clickAndWait(object);
        getSpectatioUiUtil().wait5Seconds();
    }

    /** Click on back button on screen */
    public void pressBackButton() {
        BySelector backButtonselectors =
                getUiElementFromConfig(AutomotiveConfigConstants.SETTINGS_BACK_BUTTON);
        List<UiObject2> backButtonObjects = getSpectatioUiUtil().findUiObjects(backButtonselectors);
        getSpectatioUiUtil()
                .validateUiObjects(backButtonObjects, String.format("Back Button on Settings App"));
        if (backButtonObjects != null && backButtonObjects.size() > 0) {
            UiObject2 backButtonObject = backButtonObjects.get(backButtonObjects.size() - 1);
            getSpectatioUiUtil()
                    .validateUiObject(
                            backButtonObject, String.format("Back Button on Settings App"));
            getSpectatioUiUtil().clickAndWait(backButtonObject);
            getSpectatioUiUtil().wait5Seconds();
        }
    }

}
