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
package android.platform.helpers.settings.sound_and_vibration;

import android.support.test.uiautomator.Direction;

/** Extends for Settings > Sound & vibration > Phone ringtone */
public interface ISettingsPhoneRingtoneHelper extends ISettingsSoundAndVibrationHelper {

    /**
     * Setup expectations: Settings Phone ringtone page is open
     *
     * <p>This method flings Settings Phone ringtone page.
     */
    void flingPhoneRingtone(Direction direction);

    /**
     * Setup expectations: Settings Phone ringtone page is open
     *
     * <p>This method validates Settings Phone ringtone page.
     */
    void isPhoneRingtonePage();
}
