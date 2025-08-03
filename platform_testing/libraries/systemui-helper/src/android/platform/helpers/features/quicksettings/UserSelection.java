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

package android.platform.helpers.features.quicksettings;

import static android.platform.helpers.CommonUtils.assertPageVisible;
import static android.platform.helpers.Constants.MAX_VERIFICATION_TIME_IN_SECONDS;
import static android.platform.helpers.ui.UiAutomatorUtils.getInstrumentation;
import static android.platform.helpers.ui.UiAutomatorUtils.getUiDevice;
import static android.platform.helpers.ui.UiSearch.search;

import static com.google.common.truth.Truth.assertThat;

import static java.lang.String.format;

import android.app.Instrumentation;
import android.graphics.Rect;
import android.platform.helpers.features.Page;
import android.system.helpers.QuickSettingsHelper;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;

import java.util.regex.Pattern;

/**
 * Helper class to select user for switching the user. This class will contain all the required
 * methods to switch the user.
 *
 * http://go/hsv/5905004487507968
 * @deprecated use classes from the "systemui-tapl" library instead
 */
@Deprecated
public class UserSelection implements Page {

    // http://go/hsv/5905004487507968?node=9
    private static final BySelector MULTI_USER_TITLE_SELECTOR =
            By.res("android:id/alertTitle")
                    .text(Pattern.compile("Select user", Pattern.CASE_INSENSITIVE));
    // http://go/hsv/5905004487507968?node=17
    private static final String USER_NAME_RES_ID = "com.android.systemui:id/user_name";
    // http://go/hsv/5905004487507968?node=28
    private static final BySelector SETTINGS_SELECTOR = By.res("android:id/button3")
            .text(Pattern.compile("User settings", Pattern.CASE_INSENSITIVE));
    // http://go/hsv/5905004487507968?node=29
    private static final BySelector CLOSE_SELECTOR = By.res("android:id/button1")
            .text(Pattern.compile("Close|Done", Pattern.CASE_INSENSITIVE));
    // http://go/hsv/6460527419064320?node=14
    private static final BySelector BRIGHTNESS_SLIDER = By.res(
            "com.android.systemui:id/brightness_slider");
    // http://go/hsv/5465641194618880?node=84
    private static final BySelector MULTI_USER_SWITCH_SELECTOR = By.res(
            "com.android.systemui:id/multi_user_switch");

    /**
     * To get page selector used for determining the given page
     *
     * @return an instance of given page selector identifier.
     */
    @Override
    public BySelector getPageTitleSelector() {
        return MULTI_USER_TITLE_SELECTOR;
    }

    /**
     * Action required to open the app or page otherwise it will remain empty.
     */
    @Override
    public void open() {
        final Instrumentation inst = getInstrumentation();
        final QuickSettingsHelper qsHelper = new QuickSettingsHelper(
                UiDevice.getInstance(inst), inst);
        qsHelper.launchQuickSetting();
        assertPageVisible(MULTI_USER_SWITCH_SELECTOR, getPageName(),
                MAX_VERIFICATION_TIME_IN_SECONDS);
        clickMultiUserSwitch();
    }

    /**
     * Click on the given user item. http://go/hsv/5008296081620992?node=10#
     *
     * @param userName name of the user to be selected.
     */
    public void selectUserItem(String userName) {
        BySelector userSelector = By.res(USER_NAME_RES_ID).text(
                Pattern.compile(userName, Pattern.CASE_INSENSITIVE));
        assertThat(search(null, userSelector, format("User[%s]", userName),
                MAX_VERIFICATION_TIME_IN_SECONDS)).isTrue();
        getUiDevice().findObject(userSelector).click();
    }

    /**
     * Click the user settings button. http://go/hsv/5008296081620992?node=19
     */
    public void openUserSettings() {
        assertThat(search(null, SETTINGS_SELECTOR,
                "Button[UserSettings]",
                MAX_VERIFICATION_TIME_IN_SECONDS)).isTrue();
        // Clicking the "center" of the footer button does not work. Instead, as a workaround,
        // click the top-left corner.
        clickTopLeftCorner(getUiDevice().findObject(SETTINGS_SELECTOR));
    }

    /**
     * Click the close button. http://go/hsv/5008296081620992?node=20
     */
    public void close() {
        assertThat(search(null, CLOSE_SELECTOR, "Button[Close]",
                MAX_VERIFICATION_TIME_IN_SECONDS)).isTrue();
        // Clicking the "center" of the footer button does not work. Instead, as a workaround,
        // click the top-left corner.
        clickTopLeftCorner(getUiDevice().findObject(CLOSE_SELECTOR));
    }

    /** This is needed as new version of UiAutomator don't support click(Point). */
    private void clickTopLeftCorner(UiObject2 obj) {
        Rect r = obj.getVisibleBounds();
        getUiDevice().click(r.left, r.top);
    }

    /**
     * Click on the multi user switch icon. http://go/hsv/5465641194618880?node=84
     */
    public void clickMultiUserSwitch() {
        assertThat(search(null, MULTI_USER_SWITCH_SELECTOR,
                format("Multi User Switch: %s", MULTI_USER_SWITCH_SELECTOR),
                MAX_VERIFICATION_TIME_IN_SECONDS)).isTrue();
        getUiDevice().findObject(MULTI_USER_SWITCH_SELECTOR).click();
    }
}
