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
import android.app.UiAutomation;
import android.content.Context;
import android.provider.Settings;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiObject2;

/** Display settings helper class. Wraps common actions performed on the Display Settings page. */
public class SettingsDisplayHelperImpl extends AbstractStandardAppHelper
        implements IAutoDisplaySettingsHelper {
    private ScrollUtility mScrollUtility;
    private ScrollUtility.ScrollActions mScrollAction;
    private BySelector mBackwardButtonSelector;
    private BySelector mForwardButtonSelector;
    private BySelector mScrollableElementSelector;
    private ScrollUtility.ScrollDirection mScrollDirection;

    private final UiAutomation mUiAutomation =
            InstrumentationRegistry.getInstrumentation().getUiAutomation();

    private Context mContext;

    public SettingsDisplayHelperImpl(Instrumentation instr) {
        super(instr);
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mUiAutomation.adoptShellPermissionIdentity("android.car.permission.CAR_DISPLAY_IN_CLUSTER");

        mScrollUtility = ScrollUtility.getInstance(getSpectatioUiUtil());
        mScrollAction =
                ScrollUtility.ScrollActions.valueOf(
                        getActionFromConfig(
                                AutomotiveConfigConstants.DISPLAY_SETTINGS_LIST_SCROLL_ACTION));
        mBackwardButtonSelector =
                getUiElementFromConfig(
                        AutomotiveConfigConstants.DISPLAY_SETTINGS_SCROLL_BACKWARD_BUTTON);
        mForwardButtonSelector =
                getUiElementFromConfig(
                        AutomotiveConfigConstants.DISPLAY_SETTINGS_SCROLL_FORWARD_BUTTON);
        mScrollableElementSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.DISPLAY_SETTINGS_SCROLL_ELEMENT);
        mScrollDirection =
                ScrollUtility.ScrollDirection.valueOf(
                        getActionFromConfig(
                                AutomotiveConfigConstants.DISPLAY_SETTINGS_LIST_SCROLL_DIRECTION));
        mScrollUtility.setScrollValues(
                Integer.valueOf(
                        getActionFromConfig(
                                AutomotiveConfigConstants.DISPLAY_SETTINGS_SCROLL_MARGIN)),
                Integer.valueOf(
                        getActionFromConfig(
                                AutomotiveConfigConstants.DISPLAY_SETTINGS_SCROLL_WAIT_TIME)));
    }

    private UiObject2 getAdaptiveBrightnessSwitch() {

        BySelector adaptiveBrightnessToggle =
                getUiElementFromConfig(
                        AutomotiveConfigConstants.DISPLAY_SETTINGS_ADAPTIVE_BRIGHTNESS_TOGGLE);
        UiObject2 toggle =
                mScrollUtility.scrollAndFindUiObject(
                        mScrollAction,
                        mScrollDirection,
                        mForwardButtonSelector,
                        mBackwardButtonSelector,
                        mScrollableElementSelector,
                        adaptiveBrightnessToggle,
                        String.format(
                                "Scroll on display to find %s",
                                adaptiveBrightnessToggle.toString()));

        getSpectatioUiUtil()
                .validateUiObject(
                        toggle, String.format("Validating UI object %s", adaptiveBrightnessToggle));

        return toggle;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isAdaptiveBrightnessEnabled() {

        String currentMode =
                Settings.System.getString(
                        mContext.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE);
        String adaptiveMode = Integer.toString(Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC);

        return currentMode.equals(adaptiveMode);
    }

    /** {@inheritDoc} */
    @Override
    public void toggleAdaptiveBrightness() {
        getAdaptiveBrightnessSwitch().click();
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

}
