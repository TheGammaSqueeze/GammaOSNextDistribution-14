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
 * Helper class for setting up a new secondary (non-guest) user
 * https://hsv.googleplex.com/5357563224784896
 */
public final class NewUserSetupPage implements Page {

    // https://hsv.googleplex.com/5357563224784896?node=14
    private static final BySelector TITLE_SELECTOR =
            By.res("com.google.android.setupwizard:id/suc_layout_title")
                    .text(Pattern.compile("Set up new user", Pattern.CASE_INSENSITIVE));
    // https://hsv.googleplex.com/5357563224784896?node=33
    private static final BySelector CONTINUE_SELECTOR =
            By.text(Pattern.compile("Continue", Pattern.CASE_INSENSITIVE));
    // https://hsv.googleplex.com/5357563224784896?node=31
    private static final BySelector CANCEL_SELECTOR =
            By.text(Pattern.compile("Cancel", Pattern.CASE_INSENSITIVE));

    @Override
    public BySelector getPageTitleSelector() {
        return TITLE_SELECTOR;
    }

    /**
     * Click the continue button. https://hsv.googleplex.com/5357563224784896?node=33
     */
    public void clickContinue() {
        assertPageVisible(getPageTitleSelector(), getPageName(), MAX_VERIFICATION_TIME_IN_SECONDS);
        assertThat(search(null, CONTINUE_SELECTOR, "Button[Continue]",
                MAX_VERIFICATION_TIME_IN_SECONDS)).isTrue();
        getUiDevice().findObject(CONTINUE_SELECTOR).click();
    }

    /**
     * Clicks the cancel button. https://hsv.googleplex.com/5357563224784896?node=31
     */
    public void clickCancel() {
        assertPageVisible(getPageTitleSelector(), getPageName(), MAX_VERIFICATION_TIME_IN_SECONDS);
        assertThat(search(null, CANCEL_SELECTOR, "Button[Cancel]",
                MAX_VERIFICATION_TIME_IN_SECONDS)).isTrue();
        getUiDevice().findObject(CANCEL_SELECTOR).click();
    }
}
