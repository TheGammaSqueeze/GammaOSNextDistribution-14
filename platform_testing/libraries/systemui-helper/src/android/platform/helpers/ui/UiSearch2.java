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

package android.platform.helpers.ui;

import static androidx.test.uiautomator.Until.hasObject;

import static java.lang.String.format;
import static java.lang.System.currentTimeMillis;
import static java.util.concurrent.TimeUnit.SECONDS;

import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiScrollable;
import androidx.test.uiautomator.UiSelector;

/**
 * Helper class for searching Ui Element. Androidx test version of {@link UiSearch}
 */
public class UiSearch2 {

    private static final String TAG = "UiSearch2";
    private static final int SHORT_WAIT_IN_SECONDS = 2;
    private static final int MAX_SWIPE_STEPS = 50;

    private UiSearch2() {
    }

    private static UiDevice getUiDevice() {
        return UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    }

    /**
     * Searches the given UiSelector in the given page by scrolling through the page.
     *
     * @param scroller            lookup scroller.
     * @param selector            selector to be searched.
     * @param name                name of the selector.
     * @param maxTimeoutInSeconds number time to retry. By default it will run 1 time if retry given
     *                            is 0.
     * @return an UiSelector instance of the searched selector if found otherwise null.
     */
    public static boolean search(UiScrollable scroller, UiSelector selector, String name,
            int maxTimeoutInSeconds) {
        if (scroller != null) {
            Log.d(TAG,
                    format("Looking for %s[%s] in the List[%s] within %s seconds", name, selector,
                            scroller.getSelector(), maxTimeoutInSeconds));
        } else {
            Log.d(TAG,
                    format("Looking for %s[%s] within %s seconds", name, selector,
                            maxTimeoutInSeconds));
        }

        try {
            long endTime = currentTimeMillis() + SECONDS.toMillis(maxTimeoutInSeconds);
            //Checks if the given selector is null
            if (selector == null) {
                Log.w(TAG, format("Selector[%s] is null", selector));
                return false;
            }
            // Checks if given selector is present on the current screen.
            if (search(selector, SHORT_WAIT_IN_SECONDS)) {
                return true;
            }
            // If given scroller is null and search for the selector on the current page.
            if (scroller == null) {
                Log.w(TAG, format("Scroller is null. So looking for %s[%s] in in %s seconds", name,
                        selector, maxTimeoutInSeconds));
                return search(selector, maxTimeoutInSeconds);
            }
            // Checks if given scroller exist or not.
            if (!search(scroller.getSelector(), maxTimeoutInSeconds)) {
                Log.w(TAG, format("Given Scroller[%s] was not found in %s seconds",
                        scroller.getSelector(), maxTimeoutInSeconds));
                return false;
            }
            // Looking for given selector.
            do {
                // Checks if given searchable selector isElementVisible or not.
                if (scroller.scrollIntoView(selector)) {
                    return true;
                }
            } while (currentTimeMillis() <= endTime);
        } catch (UiObjectNotFoundException e) {
            throw new RuntimeException(e);
        }
        return false;
    }

    /**
     * Searches the given BySelector in the given page by scrolling through the page.
     *
     * @param scroller            lookup scroller.
     * @param selector            selector to be searched.
     * @param name                name of the selector.
     * @param maxTimeoutInSeconds number time to retry. By default it will run 1 time if retry given
     *                            is 0.
     * @return true if found otherwise false.
     */
    public static boolean search(UiScrollable scroller, BySelector selector, String name,
            int maxTimeoutInSeconds) {
        if (scroller != null) {
            Log.d(TAG,
                    format("Looking for %s[%s] in the List[%s] within %s seconds", name, selector,
                            scroller.getSelector(), maxTimeoutInSeconds));
        } else {
            Log.d(TAG,
                    format("Looking for %s[%s] within %s seconds", name, selector,
                            maxTimeoutInSeconds));
        }
        try {
            long endTime = currentTimeMillis() + SECONDS.toMillis(maxTimeoutInSeconds);
            // Checks is searchable selector is null
            if (selector == null) {
                Log.w(TAG, format("Selector[%s] is null", selector));
                return false;
            }
            // Checks if given selector is present on the current screen.
            if (getUiDevice().wait(hasObject(selector),
                    SECONDS.toMillis(SHORT_WAIT_IN_SECONDS))) {
                return true;
            }
            // If given scroller is null and search the selector on the current page.
            if (scroller == null) {
                Log.w(TAG, format("Scroller is null. So looking for %s[%s] in in %s seconds", name,
                        selector, maxTimeoutInSeconds));
                return getUiDevice().wait(hasObject(selector),
                        SECONDS.toMillis(maxTimeoutInSeconds));
            }
            // Checks if given scroller exist or not.
            if (!search(scroller.getSelector(), maxTimeoutInSeconds)) {
                Log.w(TAG, format("Given Scroller[%s] was not found in %s seconds",
                        scroller.getSelector(), maxTimeoutInSeconds));
                return false;
            }
            // Looking for given selector in the given list.
            do {
                scroller.scrollToBeginning(MAX_SWIPE_STEPS);
                // Max page scroll to lookup item in list 5 Swipe through the screen to search given
                // selector
                int maxSwipe = 5;
                while (maxSwipe >= 0) {
                    if (getUiDevice().hasObject(selector)) {
                        return true;
                    }
                    scroller.swipeUp(MAX_SWIPE_STEPS);
                    maxSwipe--;
                }
            } while (currentTimeMillis() <= endTime);
        } catch (UiObjectNotFoundException e) {
            throw new RuntimeException(e);
        }
        return false;
    }

    /**
     * Looks for the given UiSelector with given max timeout.
     *
     * @param selector            selector to be searched.
     * @param maxTimeoutInSeconds max time to look for the selector.
     * @return if found return true otherwise false.
     */
    public static boolean search(UiSelector selector, int maxTimeoutInSeconds) {
        Log.d(TAG, format("Looking for Selector[%s] within %s seconds", selector,
                maxTimeoutInSeconds));
        // Checks is searchable selector is null
        if (selector == null) {
            Log.w(TAG, format("Selector[%s] is null", selector));
            return false;
        }

        // Looking for the given selector.
        long endTime = currentTimeMillis() + SECONDS.toMillis(maxTimeoutInSeconds);
        while (currentTimeMillis() <= endTime) {
            if (getUiDevice().findObject(selector).exists()) {
                return true;
            }
        }
        return false;
    }
}
