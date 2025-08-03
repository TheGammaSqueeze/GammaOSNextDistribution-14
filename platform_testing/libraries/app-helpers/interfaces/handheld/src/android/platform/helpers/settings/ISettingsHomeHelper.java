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
package android.platform.helpers.settings;

import android.platform.helpers.ISettingsIntelligenceHelper;
import android.support.test.uiautomator.Direction;

/** Extends for Settings homepage */
public interface ISettingsHomeHelper extends ISettingsIntelligenceHelper {

    /**
     * Setup expectations: Settings homepage is open
     *
     * <p>This method flings Settings homepage.
     */
    void flingHome(Direction direction);

    /** This method opens Settings > Accessibility page */
    void goToAccessibility();

    /** This method opens Settings > Apps page */
    void goToApps();

    /** This method opens Settings > Connected devices page */
    void goToConnectedDevices();

    /** This method opens Settings > Display page */
    void goToDisplay();

    /** This method opens Settings > Network & internet page */
    void goToNetworkAndInternet();

    /** This method opens Settings > Notifications page */
    void goToNotifications();

    /** This method opens Settings > Sound & vibration page */
    void goToSoundAndVibration();

    /** This method opens Settings > Storage page */
    void goToStorage();

    /**
     * Setup expectations: Settings homepage is open
     *
     * <p>This method validates Settings homepage.
     */
    void isHomePage();
}
