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

import static android.platform.helpers.ui.UiAutomatorUtils.getUiDevice;

import static com.android.systemui.Flags.migrateClocksToBlueprint;

import static com.google.common.truth.Truth.assertWithMessage;

import static java.util.concurrent.TimeUnit.SECONDS;

import android.os.RemoteException;
import android.platform.helpers.features.Page;
import android.util.Log;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import java.util.regex.Pattern;

/**
 * Helper class for Lock screen Home page. This contains the all the possible helper methods for the
 * page.
 *
 * HSV:
 *  - Android 11: http://go/hsv/4836673386971136
 *  - Android 12: http://go/hsv/5398171133935616
 *  - Android 12 (big clock): http://go/hsv/4759092784529408
 * @deprecated use classes from the "systemui-tapl" library instead
 */
@Deprecated
public class HomeLockscreenPage implements Page {

    // https://hsv.googleplex.com/4836673386971136?node=68
    public static final BySelector SWIPEABLE_AREA =
            By.res("com.android.systemui:id/notification_panel");
    // https://hsv.googleplex.com/5130837462876160?node=117
    public static final Pattern PAGE_TITLE_SELECTOR_PATTERN;

    static {
        if (migrateClocksToBlueprint()) {
            PAGE_TITLE_SELECTOR_PATTERN = Pattern.compile(
                    String.format("com.android.systemui:id/%s", "keyguard_indication_area"));
        } else {
            PAGE_TITLE_SELECTOR_PATTERN = Pattern.compile(
                    String.format("com.android.systemui:id/%s", "keyguard_clock_container"));
        }
    }

    private static final BySelector PAGE_TITLE_SELECTOR =
            By.res(PAGE_TITLE_SELECTOR_PATTERN);
    private static final int SHORT_SLEEP_IN_SECONDS = 2;
    private static final int WAIT_TIME_MILLIS = 5000;

    @Override
    public void open() {
        try {
            // Turning off the screen for lockscreen to enable
            getUiDevice().sleep();
            // Immediately waking up the device after sleep acts weird.
            SECONDS.sleep(SHORT_SLEEP_IN_SECONDS);
            // Waking up the device.
            getUiDevice().wakeUp();
        } catch (RemoteException | InterruptedException e) {
            Log.e(getPageName(), String.format("Exception Occurred: %s", e));
            throw new RuntimeException(e);
        }
    }

    /**
     * To get page selector used for determining the given page
     *
     * @return an instance of given page selector identifier.
     */
    @Override
    public BySelector getPageTitleSelector() {
        return PAGE_TITLE_SELECTOR;
    }

    /** If we're currently on the lock screen. */
    public boolean isVisible() {
        return getUiDevice().findObject(PAGE_TITLE_SELECTOR) != null;
    }

    /**
     * To swipe the keyguard ui element up.
     * HSV: https://hsv.googleplex.com/4836673386971136?node=68
     */
    public void swipeUp() {
        UiObject2 swipeableArea = getUiDevice().wait(Until.findObject(SWIPEABLE_AREA),
                WAIT_TIME_MILLIS);
        assertWithMessage("Swipeable area not found").that(swipeableArea).isNotNull();
        //shift swipe gesture over to left so we don't begin the gesture on the lock icon
        //   this can be removed if b/229696938 gets resolved to allow for swiping on the icon
        swipeableArea.setGestureMargins(
                /* left= */ 0,
                /* top= */ 0,
                swipeableArea.getVisibleCenter().x,
                /* bottom= */ 0
        );
        swipeableArea.swipe(Direction.UP, /* percent= */ 0.7f , /* speed= */ 1000 );
    }
}
