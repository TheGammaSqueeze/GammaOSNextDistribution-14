/*
 * Copyright (C) 2018 The Android Open Source Project
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
package android.permission.cts;

import android.os.PowerManager;
import android.test.AndroidTestCase;

import java.time.Duration;

public class PowerManagerServicePermissionTest extends AndroidTestCase {

    public void testSetBatterySaver_requiresPermissions() {
        PowerManager manager = getContext().getSystemService(PowerManager.class);
        boolean batterySaverOn = manager.isPowerSaveMode();

        try {
            manager.setPowerSaveModeEnabled(!batterySaverOn);
            fail("Toggling battery saver requires POWER_SAVER or DEVICE_POWER permission");
        } catch (SecurityException e) {
            // Expected Exception
        }
    }

    public void testSetDynamicPowerSavings_requiresPermissions() {
        try {
            PowerManager manager = getContext().getSystemService(PowerManager.class);
            manager.setDynamicPowerSaveHint(true, 0);
            fail("Updating the dynamic power savings state requires the POWER_SAVER permission");
        } catch (SecurityException e) {
            // Expected Exception
        }
    }

    public void testSetBatteryDischargePrediction_requiresPermissions() {
        try {
            PowerManager manager = getContext().getSystemService(PowerManager.class);
            manager.setBatteryDischargePrediction(Duration.ofMillis(1000), false);
            fail("Updating the discharge prediction requires the DEVICE_POWER"
                    + " or BATTERY_PREDICTION permission");
        } catch (SecurityException e) {
            // Expected Exception
        }
    }
}
