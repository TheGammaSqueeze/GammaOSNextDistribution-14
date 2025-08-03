/**
 * Copyright (C) 2022 The Android Open Source Project
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package android.platform.helpers.settings.display;

import android.platform.helpers.settings.ISettingsHomeHelper;
import android.support.test.uiautomator.Direction;

/** Extends for Settings > Display */
public interface ISettingsDisplayHelper extends ISettingsHomeHelper {

    /**
     * Setup expectations: Settings Display page is open
     *
     * <p>This method flings Settings Display page.
     */
    void flingDisplay(Direction direction);

    /** This method opens Settings > Display > Display size and text page */
    void goToDisplaySizeAndText();

    /**
     * Setup expectations: Settings Display page is open
     *
     * <p>This method validates Settings Display page.
     */
    void isDisplayPage();

    /**
     * Setup expectations: Settings Display page is open
     *
     * <p>This method scrolls to Settings Dark theme text.
     */
    void scrollToDarkTheme();

    /**
     * Setup expectations: Settings Display page is open
     *
     * <p>This method toggles Settings Dark theme switch.
     */
    void toggleDarkTheme();
}
