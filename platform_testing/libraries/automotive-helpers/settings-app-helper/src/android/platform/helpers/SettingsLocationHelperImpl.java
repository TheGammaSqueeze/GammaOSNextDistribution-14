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

import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiObject2;

/** Helper class for functional tests of Location settings */
public class SettingsLocationHelperImpl extends AbstractStandardAppHelper
        implements IAutoSettingsLocationHelper {

    public SettingsLocationHelperImpl(Instrumentation instr) {
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
    public void locationAccess() {
        BySelector locationAccessSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.LOCATION_ACCESS);
        UiObject2 locationAccess = getSpectatioUiUtil().findUiObject(locationAccessSelector);
        getSpectatioUiUtil()
                .validateUiObject(locationAccess, AutomotiveConfigConstants.LOCATION_ACCESS);
        getSpectatioUiUtil().clickAndWait(locationAccess);
    }

    /** {@inheritDoc} */
    @Override
    public boolean hasRecentlyAccessed() {
        BySelector recentlyClosedTextSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.RECENTLY_CLOSED_TEXT);
        return (getSpectatioUiUtil().hasUiElement(recentlyClosedTextSelector));
    }

    /** {@inheritDoc} */
    @Override
    public void toggleLocation(boolean onOff) {
        boolean isOn = isLocationOn();
        if (isOn != onOff) {
            BySelector locationSwitchSelector =
                    getUiElementFromConfig(AutomotiveConfigConstants.TOGGLE_LOCATION);
            UiObject2 locationSwitch = getSpectatioUiUtil().findUiObject(locationSwitchSelector);
            getSpectatioUiUtil()
                    .validateUiObject(locationSwitch, AutomotiveConfigConstants.TOGGLE_LOCATION);
            getSpectatioUiUtil().clickAndWait(locationSwitch);
            getSpectatioUiUtil().waitNSeconds(1000);
        } else {
            throw new RuntimeException("Location state is already " + (onOff ? "on" : "off"));
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean isLocationOn() {
        BySelector enableOptionSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.LOCATION_SWITCH);
        UiObject2 enableOption = getSpectatioUiUtil().findUiObject(enableOptionSelector);
        getSpectatioUiUtil()
                .validateUiObject(enableOption, AutomotiveConfigConstants.LOCATION_SWITCH);
        return enableOption.isChecked();
    }

    /** {@inheritDoc} */
    @Override
    public boolean hasMapsWidget() {
        BySelector mapsWidgetSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.LOCATION_WIDGET);
        return (getSpectatioUiUtil().hasUiElement(mapsWidgetSelector));
    }

}
