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
import android.content.res.Resources;
import android.platform.helpers.ScrollUtility.ScrollActions;
import android.platform.helpers.ScrollUtility.ScrollDirection;
import android.platform.spectatio.exceptions.MissingUiElementException;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiObject2;

import java.text.SimpleDateFormat;
import java.time.Month;
import java.util.Date;

/** Helper class for functional tests of System settings */
public class SettingsSystemHelperImpl extends AbstractStandardAppHelper
        implements IAutoSystemSettingsHelper {

    private ScrollUtility mScrollUtility;
    private ScrollActions mScrollAction;
    private BySelector mBackwardButtonSelector;
    private BySelector mForwardButtonSelector;
    private BySelector mScrollableElementSelector;
    private ScrollDirection mScrollDirection;

    public SettingsSystemHelperImpl(Instrumentation instr) {
        super(instr);
        mScrollUtility = ScrollUtility.getInstance(getSpectatioUiUtil());
        mScrollAction =
                ScrollActions.valueOf(
                        getActionFromConfig(
                                AutomotiveConfigConstants.SYSTEM_SETTINGS_SCROLL_ACTION));
        mBackwardButtonSelector =
                getUiElementFromConfig(
                        AutomotiveConfigConstants.SYSTEM_SETTINGS_SCROLL_BACKWARD_BUTTON);
        mForwardButtonSelector =
                getUiElementFromConfig(
                        AutomotiveConfigConstants.SYSTEM_SETTINGS_SCROLL_FORWARD_BUTTON);
        mScrollableElementSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.SYSTEM_SETTINGS_SCROLL_ELEMENT);
        mScrollDirection =
                ScrollDirection.valueOf(
                        getActionFromConfig(
                                AutomotiveConfigConstants.SYSTEM_SETTINGS_SCROLL_DIRECTION));
        mScrollUtility.setScrollValues(
                Integer.valueOf(
                        getActionFromConfig(
                                AutomotiveConfigConstants.SYSTEM_SETTINGS_SCROLL_MARGIN)),
                Integer.valueOf(
                        getActionFromConfig(
                                AutomotiveConfigConstants.SYSTEM_SETTINGS_SCROLL_WAIT_TIME)));
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
    public void setDisplayLanguage(String language) {
        openLanguageMenu();
        BySelector languageSelector = By.clickable(true).hasDescendant(By.textStartsWith(language));
        UiObject2 languageObject = getMenu(languageSelector);
        getSpectatioUiUtil()
                .validateUiObject(
                        languageObject,
                        String.format("Unable to find UI Element for language selector"));
        getSpectatioUiUtil().clickAndWait(languageObject);
        String systemLanguage =
                Resources.getSystem().getConfiguration().getLocales().get(0).getDisplayLanguage();
        if (!language.toLowerCase().contains(systemLanguage.toLowerCase())) {
            throw new RuntimeException("System language is different from selected language");
        }
    }

    /** {@inheritDoc} */
    @Override
    public String getCurrentLanguage() {
        UiObject2 object =
                getBtnByText(
                        AutomotiveConfigConstants.LANGUAGES_MENU,
                        AutomotiveConfigConstants.LANGUAGES_MENU_IN_SELECTED_LANGUAGE);
        getSpectatioUiUtil()
                .validateUiObject(
                        object, String.format("Unable to find UI Element for current language"));
        String currentLanguage = getSummeryText(object);
        return currentLanguage;
    }

    /** {@inheritDoc} */
    @Override
    public String getDeviceModel() {
        openAboutMenu();
        UiObject2 object = getMenu(getUiElementFromConfig(AutomotiveConfigConstants.DEVICE_MODEL));
        getSpectatioUiUtil()
                .validateUiObject(
                        object, String.format("Unable to find UI Element for device model"));
        String modelName = getSummeryText(object);
        return modelName;
    }

    /** {@inheritDoc} */
    @Override
    public String getAndroidVersion() {
        openAboutMenu();
        UiObject2 object =
                getMenu(getUiElementFromConfig(AutomotiveConfigConstants.ANDROID_VERSION));
        getSpectatioUiUtil()
                .validateUiObject(
                        object, String.format("Unable to find UI Element for current language"));
        String androidVersion = getSummeryText(object);
        return androidVersion;
    }

    /** {@inheritDoc} */
    @Override
    public Date getAndroidSecurityPatchLevel() {
        openAboutMenu();
        UiObject2 object =
                getMenu(
                        getUiElementFromConfig(
                                AutomotiveConfigConstants.ANDROID_SECURITY_PATCH_LEVEL));
        getSpectatioUiUtil()
                .validateUiObject(
                        object,
                        String.format(
                                "Unable to find UI Element for android security patch level"));
        String androidSecurityPatchLevel = getSummeryText(object);
        Date patchDate = parseDate(androidSecurityPatchLevel, "MMMM dd, yyyy");
        if (patchDate == null) {
            patchDate = parseDate(androidSecurityPatchLevel, "dd MMMM yyyy");
        }
        if (patchDate == null) {
            throw new RuntimeException("Cannot find date from UI");
        }
        return formatDate(patchDate, "MMMM dd, yyyy"); // return locale independent date
    }

    private Date formatDate(Date date, String format) {
        SimpleDateFormat dateFormatter = new SimpleDateFormat(format);
        String dateString = dateFormatter.format(date);
        String[] arr = dateString.split(" ");
        int year = Integer.valueOf(arr[2]);
        int month = Month.valueOf(arr[0].toUpperCase()).getValue() - 1;
        int day = Integer.valueOf(arr[1].substring(0, arr[1].length() - 1));
        return new Date(year, month, day);
    }

    private Date parseDate(String date, String format) {
        Date parsedDate = null;
        try {
            SimpleDateFormat dateFormatter = new SimpleDateFormat(format);
            parsedDate = dateFormatter.parse(date);
        } catch (Exception e) {
            // do nothing
        }
        return parsedDate;
    }

    /** {@inheritDoc} */
    @Override
    public String getKernelVersion() {
        openAboutMenu();
        UiObject2 object =
                getMenu(getUiElementFromConfig(AutomotiveConfigConstants.KERNEL_VERSION));
        getSpectatioUiUtil()
                .validateUiObject(
                        object, String.format("Unable to find UI Element for kernel version"));
        String kernelVersion = getSummeryText(object);
        return kernelVersion;
    }

    /** {@inheritDoc} */
    @Override
    public String getBuildNumber() {
        openAboutMenu();
        UiObject2 object = getMenu(getUiElementFromConfig(AutomotiveConfigConstants.BUILD_NUMBER));
        getSpectatioUiUtil()
                .validateUiObject(
                        object, String.format("Unable to find UI Element for build number"));
        String buildNumber = getSummeryText(object);
        return buildNumber;
    }

    /** {@inheritDoc} */
    @Override
    public void resetNetwork() {
        openResetOptionsMenu();
        BySelector resetNetworkSelector =
                By.clickable(true)
                        .hasDescendant(
                                getUiElementFromConfig(AutomotiveConfigConstants.RESET_NETWORK));
        UiObject2 resetNetworkMenu = getMenu(resetNetworkSelector);
        getSpectatioUiUtil()
                .validateUiObject(resetNetworkMenu, AutomotiveConfigConstants.RESET_NETWORK);
        getSpectatioUiUtil().clickAndWait(resetNetworkMenu);
        BySelector resetSettingsSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.RESET_SETTINGS);

        UiObject2 resetSettingsButton1 = getMenu(resetSettingsSelector);
        getSpectatioUiUtil()
                .validateUiObject(resetSettingsButton1, AutomotiveConfigConstants.RESET_SETTINGS);
        getSpectatioUiUtil().clickAndWait(resetSettingsButton1);

        UiObject2 resetSettingsButton2 =
                getMenu(getUiElementFromConfig(AutomotiveConfigConstants.RESET_SETTINGS));
        getSpectatioUiUtil()
                .validateUiObject(resetSettingsButton2, AutomotiveConfigConstants.RESET_SETTINGS);
        getSpectatioUiUtil().clickAndWait(resetSettingsButton2);
    }

    /** {@inheritDoc} */
    @Override
    public void resetAppPreferences() {
        openResetOptionsMenu();
        BySelector selector =
                By.clickable(true)
                        .hasDescendant(
                                getUiElementFromConfig(
                                        AutomotiveConfigConstants.RESET_APP_PREFERENCES));
        UiObject2 object = getMenu(selector);
        getSpectatioUiUtil()
                .validateUiObject(object, AutomotiveConfigConstants.RESET_APP_PREFERENCES);
        getSpectatioUiUtil().clickAndWait(object);
        BySelector reset_apps_selector =
                getUiElementFromConfig(AutomotiveConfigConstants.RESET_APPS);

        UiObject2 reset_apps_button = getMenu(reset_apps_selector);
        getSpectatioUiUtil()
                .validateUiObject(reset_apps_button, AutomotiveConfigConstants.RESET_APPS);
        getSpectatioUiUtil().clickAndWait(reset_apps_button);
    }

    /** {@inheritDoc} */
    @Override
    public void openLanguagesInputMenu() {
        UiObject2 languagesInputMenu =
                getMenu(getUiElementFromConfig(AutomotiveConfigConstants.LANGUAGES_AND_INPUT_MENU));
        getSpectatioUiUtil()
                .validateUiObject(
                        languagesInputMenu,
                        String.format("Unable to find UI Element for language input menu"));
        getSpectatioUiUtil().clickAndWait(languagesInputMenu);
    }

    private String getSummeryText(UiObject2 object) {
        UiObject2 parent = object.getParent();
        if (parent.getChildren().size() < 2) {
            BySelector swipeSelector =
                    getUiElementFromConfig(
                            AutomotiveConfigConstants.SYSTEM_SETTINGS_SCROLL_ELEMENT);
            UiObject2 swipeObject = getSpectatioUiUtil().findUiObject(swipeSelector);
            getSpectatioUiUtil()
                    .validateUiObject(
                            swipeObject, AutomotiveConfigConstants.SYSTEM_SETTINGS_SCROLL_ELEMENT);
            getSpectatioUiUtil().swipeUp(swipeObject);
        }
        return object.getParent().getChildren().get(1).getText();
    }

    private void openResetOptionsMenu() {
        UiObject2 resetOptionMenu =
                getMenu(getUiElementFromConfig(AutomotiveConfigConstants.RESET_OPTIONS_MENU));
        getSpectatioUiUtil()
                .validateUiObject(
                        resetOptionMenu, String.format("Unable to find UI Element for reset menu"));
        getSpectatioUiUtil().clickAndWait(resetOptionMenu);
    }

    private void openAboutMenu() {
        getSpectatioUiUtil().wait1Second();
        UiObject2 aboutMenu = getMenu(getUiElementFromConfig(AutomotiveConfigConstants.ABOUT_MENU));
        getSpectatioUiUtil()
                .validateUiObject(
                        aboutMenu, String.format("Unable to find UI Element for about menu"));
        getSpectatioUiUtil().clickAndWait(aboutMenu);
    }

    /** {@inheritDoc} */
    @Override
    public void openViewAll() {
        getSpectatioUiUtil().wait1Second();
        UiObject2 viewAll =
                getMenu(
                        getUiElementFromConfig(
                                AutomotiveConfigConstants.LOCATION_SETTINGS_VIEW_ALL));
        getSpectatioUiUtil()
                .validateUiObject(viewAll, String.format("Unable to find UI Element for view all"));
        getSpectatioUiUtil().clickAndWait(viewAll);
    }

    private void openLanguageMenu() {
        UiObject2 languageMenu =
                getBtnByText(
                        AutomotiveConfigConstants.LANGUAGES_MENU,
                        AutomotiveConfigConstants.LANGUAGES_MENU_IN_SELECTED_LANGUAGE);
        getSpectatioUiUtil()
                .validateUiObject(
                        languageMenu, String.format("Unable to find UI Element for language menu"));
        getSpectatioUiUtil().clickAndWait(languageMenu);
    }

    private UiObject2 getBtnByText(String... texts) {
        for (String text : texts) {
            BySelector btnSelector = By.text(text);
            UiObject2 btn = getSpectatioUiUtil().findUiObject(btnSelector);
            if (btn != null) {
                return btn;
            }
        }
        throw new RuntimeException("Cannot find button");
    }

    /** {@inheritDoc} */
    @Override
    public void openStorageMenu() {
        UiObject2 aboutMenu =
                getMenu(
                        getUiElementFromConfig(
                                AutomotiveConfigConstants.STORAGE_SYSTEM_SUB_SETTINGS));
        getSpectatioUiUtil()
                .validateUiObject(
                        aboutMenu, String.format("Unable to find UI Element for Storage menu"));
        getSpectatioUiUtil().clickAndWait(aboutMenu);
        getSpectatioUiUtil().wait5Seconds();
    }

    /** {@inheritDoc} */
    @Override
    public boolean verifyUsageinGB(String option) {
        boolean isUsageinGB = false;
        UiObject2 targetObject =
                getSpectatioUiUtil()
                        .findUiObject(
                                getUiElementFromConfig(
                                        AutomotiveConfigConstants
                                                .SETTINGS_UI_SUB_SETTING_SCROLL_ELEMENT));
        UiObject2 target =
                getSpectatioUiUtil()
                        .findUiObjectInGivenElement(targetObject, getUiElementFromConfig(option));
        String targetText = getSummeryText(target);
        if (targetText.contains("GB")) {
            isUsageinGB = true;
        }
        return isUsageinGB;
    }

    private UiObject2 getMenu(BySelector selector) {
        UiObject2 object =
                mScrollUtility.scrollAndFindUiObject(
                        mScrollAction,
                        mScrollDirection,
                        mForwardButtonSelector,
                        mBackwardButtonSelector,
                        mScrollableElementSelector,
                        selector,
                        String.format("Scroll on system setting to find %s", selector));

        getSpectatioUiUtil()
                .validateUiObject(
                        object,
                        String.format("Unable to find UI Element %s.", selector.toString()));
        return object;
    }

    /** {@inheritDoc} */
    @Override
    public boolean hasDeveloperOptions() throws MissingUiElementException {
        getSpectatioUiUtil().wait1Second();
        boolean hasDeveloperOptions =
                getSpectatioUiUtil()
                        .scrollAndCheckIfUiElementExist(
                                mScrollableElementSelector, "Developer options", true);
        return hasDeveloperOptions;
    }

    /** {@inheritDoc} */
    @Override
    public void openDeveloperOptions() {
        getSpectatioUiUtil().wait1Second();
        UiObject2 developerOptions =
                getMenu(getUiElementFromConfig(AutomotiveConfigConstants.DEVELOPER_OPTIONS));
        getSpectatioUiUtil()
                .validateUiObject(
                        developerOptions,
                        String.format("Unable to find UI Element for Developer Options"));
        getSpectatioUiUtil().clickAndWait(developerOptions);
    }

    /** {@inheritDoc} */
    @Override
    public void enterDeveloperMode() {
        openAboutMenu();
        getSpectatioUiUtil().wait1Second();
        UiObject2 buildNumber =
                getMenu(getUiElementFromConfig(AutomotiveConfigConstants.BUILD_NUMBER));
        getSpectatioUiUtil()
                .validateUiObject(
                        buildNumber, String.format("Unable to find UI Element for build number"));
        for (int i = 0; i <= 6; i++) {
            getSpectatioUiUtil().clickAndWait(buildNumber);
            getSpectatioUiUtil().wait1Second();
        }
        getSpectatioUiUtil().pressBack();
        getSpectatioUiUtil().waitForIdle();
    }
}
