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

package android.system.helpers;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

public class NavigationControlHelper {

    private static final BySelector NAVIGATION_BAR_VIEW =
            By.res("com.android.systemui", "navigation_bar_view");

    private static final UiDevice sDevice =
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

    public static void assertNavigationBarNotVisible() {
        final boolean navBarInvisible = sDevice.wait(Until.gone(NAVIGATION_BAR_VIEW), 2_000);
        if (!navBarInvisible) {
            throw new AssertionError("Navigation bar is visible, expected: invisible");
        }
    }
}
