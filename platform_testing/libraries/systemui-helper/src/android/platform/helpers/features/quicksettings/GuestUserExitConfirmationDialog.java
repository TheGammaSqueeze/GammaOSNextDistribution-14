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

import android.platform.helpers.features.Page;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;

import java.util.regex.Pattern;

/**
 * Helper class for reset confirmation dialog when either switching from an ephemeral guest user,
 * or pressing "Exit guest" when device is configured with config_guestUserAutoCreated.
 *
 * https://hsv.googleplex.com/5025959268843520
 * @deprecated use classes from the "systemui-tapl" library instead
 */
@Deprecated
public final class GuestUserExitConfirmationDialog implements Page {

    // https://hsv.googleplex.com/6067368607350784?node=10
    private static final BySelector sTitleSelector = By.res("android:id/alertTitle")
            .text(Pattern.compile("Exit guest mode\\?", Pattern.CASE_INSENSITIVE));
    // https://hsv.googleplex.com/6067368607350784?node=19
    private static final BySelector sExitSelector = By.res("android:id/button1")
            .text(Pattern.compile("Exit", Pattern.CASE_INSENSITIVE));
    // https://hsv.googleplex.com/6067368607350784?node=18
    private static final BySelector sCancelSelector = By.res("android:id/button3")
            .text(Pattern.compile("Cancel", Pattern.CASE_INSENSITIVE));

    @Override
    public BySelector getPageTitleSelector() {
        return sTitleSelector;
    }

    /**
     * Click the exit button. https://hsv.googleplex.com/5025959268843520?node=16
     */
    public void confirmExitGuest() {
        assertPageVisible(getPageTitleSelector(), getPageName(), MAX_VERIFICATION_TIME_IN_SECONDS);
        getUiDevice().findObject(sExitSelector).click();
    }

    /**
     * Click the cancel button. https://hsv.googleplex.com/5025959268843520?node=15
     */
    public void cancelExitGuest() {
        assertPageVisible(getPageTitleSelector(), getPageName(), MAX_VERIFICATION_TIME_IN_SECONDS);
        getUiDevice().findObject(sCancelSelector).click();
    }
}
