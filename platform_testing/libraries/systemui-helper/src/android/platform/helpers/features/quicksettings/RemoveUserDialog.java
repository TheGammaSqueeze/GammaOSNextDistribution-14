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
import static android.platform.helpers.ui.UiAutomatorUtils.getUiDevice;
import static android.platform.helpers.ui.UiSearch.search;

import static com.google.common.truth.Truth.assertThat;

import android.platform.helpers.features.Page;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;

import java.util.regex.Pattern;

/**
 * Helper class for removing a user
 * https://hsv.googleplex.com/4700004604182528
 */
public final class RemoveUserDialog implements Page {

    // https://hsv.googleplex.com/4700004604182528?node=14
    private static final BySelector TITLE_SELECTOR = By.res("android:id/message")
            .text(Pattern.compile("Remove user\\?", Pattern.CASE_INSENSITIVE));
    // https://hsv.googleplex.com/4700004604182528?node=23
    private static final BySelector REMOVE_USER_SELECTOR = By.res("android:id/button1")
            .text(Pattern.compile("Remove user", Pattern.CASE_INSENSITIVE));
    // https://hsv.googleplex.com/4700004604182528?node=22
    private static final BySelector KEEP_USER_SELECTOR = By.res("android:id/button2")
            .text(Pattern.compile("Keep user", Pattern.CASE_INSENSITIVE));

    @Override
    public BySelector getPageTitleSelector() {
        return TITLE_SELECTOR;
    }

    /**
     * Click remove user button. https://hsv.googleplex.com/4700004604182528?node=23
     */
    public void removeUser() {
        assertPageVisible(getPageTitleSelector(), getPageName(), MAX_VERIFICATION_TIME_IN_SECONDS);
        assertThat(search(null, REMOVE_USER_SELECTOR, "Button[RemoveUser]",
                MAX_VERIFICATION_TIME_IN_SECONDS)).isTrue();
        getUiDevice().findObject(REMOVE_USER_SELECTOR).click();
    }

    /**
     * Click keep user button. https://hsv.googleplex.com/4700004604182528?node=22
     */
    public void keepUser() {
        assertPageVisible(getPageTitleSelector(), getPageName(), MAX_VERIFICATION_TIME_IN_SECONDS);
        assertThat(search(null, KEEP_USER_SELECTOR, "Button[KeepUser]",
                MAX_VERIFICATION_TIME_IN_SECONDS)).isTrue();
        getUiDevice().findObject(KEEP_USER_SELECTOR).click();
    }
}
