/*
 * Copyright (C) 2009 The Android Open Source Project
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

package android.os.cts;

import static android.os.Flags.batterySaverSupportedCheckApi;

import static com.android.compatibility.common.util.TestUtils.waitUntil;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Flags;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.platform.test.annotations.AppModeFull;
import android.provider.Settings.Global;
import android.test.AndroidTestCase;

import androidx.test.filters.LargeTest;

import com.android.compatibility.common.util.BatteryUtils;
import com.android.compatibility.common.util.CallbackAsserter;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;

import java.time.Duration;

@AppModeFull(reason = "Instant Apps don't have the WRITE_SECURE_SETTINGS permission "
        + "required in tearDown for Global#putInt")
public class PowerManagerTest extends AndroidTestCase {
    private static final String TAG = "PowerManagerTest";
    public static final long TIME = 3000;
    public static final int MORE_TIME = 300;
    private static final int BROADCAST_TIMEOUT_SECONDS = 70;
    private static final Duration LONG_DISCHARGE_DURATION = Duration.ofMillis(2000);
    private static final Duration SHORT_DISCHARGE_DURATION = Duration.ofMillis(1000);

    private int mInitialPowerSaverMode;
    private int mInitialDynamicPowerSavingsEnabled;
    private int mInitialThreshold;

    /**
     * test points:
     * 1 Get a wake lock at the level of the flags parameter
     * 2 Force the device to go to sleep
     * 3 User activity happened
     */
    public void testPowerManager() throws InterruptedException {
        PowerManager pm = (PowerManager)getContext().getSystemService(Context.POWER_SERVICE);

        WakeLock wl = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, TAG);
        wl.acquire(TIME);
        assertTrue(wl.isHeld());
        Thread.sleep(TIME + MORE_TIME);
        assertFalse(wl.isHeld());

        try {
            pm.reboot("Testing");
            fail("reboot should throw SecurityException");
        } catch (SecurityException e) {
            // expected
        }
    }

    @Before
    public void setUp() {
        // store the current value so we can restore it
        ContentResolver resolver = getContext().getContentResolver();
        mInitialPowerSaverMode = Global.getInt(resolver, Global.AUTOMATIC_POWER_SAVE_MODE, 0);
        mInitialDynamicPowerSavingsEnabled =
                Global.getInt(resolver, Global.DYNAMIC_POWER_SAVINGS_ENABLED, 0);
        mInitialThreshold =
                Global.getInt(resolver, Global.DYNAMIC_POWER_SAVINGS_DISABLE_THRESHOLD, 0);

    }

    @After
    public void tearDown() {
        SystemUtil.runWithShellPermissionIdentity(() -> {
            ContentResolver resolver = getContext().getContentResolver();

            // Verify we can change it to dynamic.
            Global.putInt(resolver, Global.AUTOMATIC_POWER_SAVE_MODE, mInitialPowerSaverMode);
            Global.putInt(resolver,
                    Global.DYNAMIC_POWER_SAVINGS_ENABLED, mInitialDynamicPowerSavingsEnabled);
            Global.putInt(resolver,
                    Global.DYNAMIC_POWER_SAVINGS_DISABLE_THRESHOLD, mInitialThreshold);
        });
    }

    public void testPowerManager_getPowerSaveMode() {
        PowerManager manager = BatteryUtils.getPowerManager();
        if (batterySaverSupportedCheckApi() && !manager.isBatterySaverSupported()) {
            return;
        }

        SystemUtil.runWithShellPermissionIdentity(() -> {
            ContentResolver resolver = getContext().getContentResolver();

            // Verify we can change it to percentage.
            Global.putInt(resolver, Global.AUTOMATIC_POWER_SAVE_MODE, 0);
            assertEquals(
                    PowerManager.POWER_SAVE_MODE_TRIGGER_PERCENTAGE,
                    manager.getPowerSaveModeTrigger());

            // Verify we can change it to dynamic.
            Global.putInt(resolver, Global.AUTOMATIC_POWER_SAVE_MODE, 1);
            assertEquals(
                    PowerManager.POWER_SAVE_MODE_TRIGGER_DYNAMIC,
                    manager.getPowerSaveModeTrigger());
        });
    }

    public void testPowerManager_setDynamicPowerSavings() {
        PowerManager manager = BatteryUtils.getPowerManager();
        if (batterySaverSupportedCheckApi() && !manager.isBatterySaverSupported()) {
            return;
        }

        SystemUtil.runWithShellPermissionIdentity(() -> {
            ContentResolver resolver = getContext().getContentResolver();

            // Verify settings are actually updated.
            manager.setDynamicPowerSaveHint(true, 80);
            assertEquals(1, Global.getInt(resolver, Global.DYNAMIC_POWER_SAVINGS_ENABLED, 0));
            assertEquals(80, Global.getInt(resolver,
                    Global.DYNAMIC_POWER_SAVINGS_DISABLE_THRESHOLD, 0));

            manager.setDynamicPowerSaveHint(false, 20);
            assertEquals(0, Global.getInt(resolver, Global.DYNAMIC_POWER_SAVINGS_ENABLED, 1));
            assertEquals(20, Global.getInt(resolver,
                    Global.DYNAMIC_POWER_SAVINGS_DISABLE_THRESHOLD, 0));
        });
    }

    @LargeTest
    public void testPowerManager_batteryDischargePrediction() throws Exception {
        final PowerManager manager = BatteryUtils.getPowerManager();
        if (batterySaverSupportedCheckApi() && !manager.isBatterySaverSupported()) {
            return;
        }

        if (!BatteryUtils.hasBattery()) {
            assertNull(manager.getBatteryDischargePrediction());
            return;
        }

        // Unplug to ensure the plugged in broadcast is sent.
        BatteryUtils.runDumpsysBatteryUnplug();

        // Plugged in. No prediction should be given.
        final CallbackAsserter pluggedBroadcastAsserter = CallbackAsserter.forBroadcast(
                new IntentFilter(Intent.ACTION_POWER_CONNECTED));
        BatteryUtils.runDumpsysBatterySetPluggedIn(true);
        pluggedBroadcastAsserter.assertCalled("Didn't get power connected broadcast",
                BROADCAST_TIMEOUT_SECONDS);
        // PowerManagerService may get the BATTERY_CHANGED broadcast after we get our broadcast,
        // so we have to have a little wait.
        waitUntil("PowerManager doesn't think the device has connected power",
                () -> manager.getBatteryDischargePrediction() == null);

        // Not plugged in. At the very least, the basic discharge estimation should be returned.
        final CallbackAsserter unpluggedBroadcastAsserter = CallbackAsserter.forBroadcast(
                new IntentFilter(Intent.ACTION_POWER_DISCONNECTED));
        BatteryUtils.runDumpsysBatteryUnplug();
        unpluggedBroadcastAsserter.assertCalled("Didn't get power disconnected broadcast",
                BROADCAST_TIMEOUT_SECONDS);
        // PowerManagerService may get the BATTERY_CHANGED broadcast after we get our broadcast,
        // so we have to have a little wait.
        waitUntil("PowerManager still thinks the device has connected power",
                () -> manager.getBatteryDischargePrediction() != null);

        CallbackAsserter predictionChangedBroadcastAsserter = CallbackAsserter.forBroadcast(
                new IntentFilter(PowerManager.ACTION_ENHANCED_DISCHARGE_PREDICTION_CHANGED));
        setDischargePrediction(LONG_DISCHARGE_DURATION, true);
        assertDischargePrediction(LONG_DISCHARGE_DURATION, true);
        predictionChangedBroadcastAsserter.assertCalled("Prediction changed broadcast not received",
                BROADCAST_TIMEOUT_SECONDS);


        predictionChangedBroadcastAsserter = CallbackAsserter.forBroadcast(
                new IntentFilter(PowerManager.ACTION_ENHANCED_DISCHARGE_PREDICTION_CHANGED));
        setDischargePrediction(SHORT_DISCHARGE_DURATION, false);
        assertDischargePrediction(SHORT_DISCHARGE_DURATION, false);
        predictionChangedBroadcastAsserter.assertCalled("Prediction changed broadcast not received",
                BROADCAST_TIMEOUT_SECONDS);
    }

    private void setDischargePrediction(Duration d, boolean isPersonalized) {
        final PowerManager manager = BatteryUtils.getPowerManager();
        SystemUtil.runWithShellPermissionIdentity(
                () -> manager.setBatteryDischargePrediction(d, isPersonalized),
                android.Manifest.permission.BATTERY_PREDICTION);
    }

    private void assertDischargePrediction(Duration d, boolean isPersonalized) {
        final PowerManager manager = BatteryUtils.getPowerManager();
        // We can't pause time so must use >= because the time remaining should decrease as
        // time goes on.
        Duration prediction = manager.getBatteryDischargePrediction();
        assertTrue("Prediction is greater than " + d.toMillis() + "ms: "
                + prediction, d.toMillis() >= prediction.toMillis());
        assertEquals(isPersonalized, manager.isBatteryDischargePredictionPersonalized());
    }
}
