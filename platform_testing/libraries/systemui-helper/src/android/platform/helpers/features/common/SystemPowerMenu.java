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

package android.platform.helpers.features.common;

import static android.view.KeyEvent.KEYCODE_POWER;

import static android.platform.helpers.CommonUtils.executeShellCommand;

import static java.util.regex.Pattern.CASE_INSENSITIVE;
import static java.util.regex.Pattern.compile;


import android.platform.helpers.features.Page2;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

/**
 * Helper class for System power menu. This contains the all the possible helper methods for
 * menu.
 * HSV: https://hsv.googleplex.com/6244730548518912 for power menu button in
 * new quick settings footer design.
 */
public class SystemPowerMenu implements Page2 {
    private static final String POWER_COMMAND = String.format("input keyevent --longpress  %s",
            KEYCODE_POWER);

    // https://hsv.googleplex.com/6244730548518912?node=13
    private BySelector mPageTitleSelector = By.text(compile("Power off", CASE_INSENSITIVE));

    private static final long OPEN_MENU_TIMEOUT_MSEC = 5000;

    private UiDevice mUiDevice;

    public SystemPowerMenu(UiDevice uiDevice) {
        mUiDevice = uiDevice;
    }

    /**
     * To get page selector used for determining the given page.
     * https://hsv.googleplex.com/6504676126097408?node=13
     *
     * @return an instance of given page selector identifier.
     */
    @Override
    public BySelector getPageTitleSelector() {
        return mPageTitleSelector;
    }

    /**
     * Action required to open the app or page otherwise it will remain empty.
     */
    @Override
    public void open() {
        executeShellCommand(POWER_COMMAND);
        mUiDevice.waitForIdle();

        if (!mUiDevice.wait(Until.hasObject(mPageTitleSelector), OPEN_MENU_TIMEOUT_MSEC)) {
            throw new AssertionError("Timed out trying to open system power menu");
        }
    }
}
