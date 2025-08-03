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
import static android.platform.helpers.Constants.SHORT_WAIT_TIME_IN_SECONDS;
import static android.platform.helpers.ui.UiAutomatorUtils.getUiDevice;
import static android.platform.helpers.ui.UiSearch.search;

import static com.google.common.truth.Truth.assertThat;

import android.platform.helpers.features.Page;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import java.util.regex.Pattern;

/**
 * Helper class required for setting the current user's name and user icon
 * https://hsv.googleplex.com/4709486012923904
 */
public final class EditCurrentUserDialog implements Page {

    // https://hsv.googleplex.com/4709486012923904?node=7
    private static final BySelector TITLE_SELECTOR = By.res("android:id/alertTitle")
            .text(Pattern.compile("Add user", Pattern.CASE_INSENSITIVE));
    // https://hsv.googleplex.com/4709486012923904?node=12
    private static final BySelector EDIT_TEXT_SELECTOR =
            By.res("com.android.systemui:id/user_name");
    // https://hsv.googleplex.com/4709486012923904?node=16
    private static final BySelector OK_SELECTOR = By.res("android:id/button1")
            .text(Pattern.compile("OK", Pattern.CASE_INSENSITIVE));
    // https://hsv.googleplex.com/4709486012923904?node=15
    private static final BySelector CANCEL_SELECTOR = By.res("android:id/button2")
            .text(Pattern.compile("Cancel", Pattern.CASE_INSENSITIVE));

    @Override
    public BySelector getPageTitleSelector() {
        return TITLE_SELECTOR;
    }

    /**
     * Change the user's name using the text field.
     * https://hsv.googleplex.com/4709486012923904?node=12
     */
    public void setUserNameText(String userName) {
        assertPageVisible(getPageTitleSelector(), getPageName(), MAX_VERIFICATION_TIME_IN_SECONDS);
        assertThat(search(null, EDIT_TEXT_SELECTOR, "EditText[CurrentUserName]",
                MAX_VERIFICATION_TIME_IN_SECONDS)).isTrue();
        UiObject2 inputTextField = getUiDevice().findObject(EDIT_TEXT_SELECTOR);
        inputTextField.clickAndWait(Until.newWindow(), SHORT_WAIT_TIME_IN_SECONDS * 1000);
        inputTextField.setText(userName);
    }

    /**
     * Click the OK button. https://hsv.googleplex.com/4709486012923904?node=16
     */
    public void clickOK() {
        assertPageVisible(getPageTitleSelector(), getPageName(), MAX_VERIFICATION_TIME_IN_SECONDS);
        assertThat(
                search(null, OK_SELECTOR, "Button[OK]", MAX_VERIFICATION_TIME_IN_SECONDS)).isTrue();
        getUiDevice().findObject(OK_SELECTOR).click();
    }

    /**
     * Click the cancel button. https://hsv.googleplex.com/4709486012923904?node=15
     */
    public void clickCancel() {
        assertPageVisible(getPageTitleSelector(), getPageName(), MAX_VERIFICATION_TIME_IN_SECONDS);
        assertThat(search(null, CANCEL_SELECTOR, "Button[Cancel]",
                MAX_VERIFICATION_TIME_IN_SECONDS)).isTrue();
        getUiDevice().findObject(CANCEL_SELECTOR).click();
    }
}
