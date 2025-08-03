/*
 * Copyright (C) 2019 The Android Open Source Project
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
package android.os.cts.deviceidle;

import static com.android.compatibility.common.util.TestUtils.waitUntil;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.annotation.NonNull;
import android.content.Context;
import android.content.IntentFilter;
import android.os.PowerManager;
import android.provider.DeviceConfig;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.UiDevice;

import com.android.compatibility.common.util.BatteryUtils;
import com.android.compatibility.common.util.CallbackAsserter;
import com.android.compatibility.common.util.DeviceConfigStateHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class DeviceIdleTest {
    private final DeviceConfigStateHelper mDeviceConfigStateHelper =
            new DeviceConfigStateHelper(DeviceConfig.NAMESPACE_DEVICE_IDLE);

    private UiDevice mUiDevice;

    @Before
    public void setUp() {
        mUiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    }

    @After
    public void tearDown() throws Exception {
        BatteryUtils.runDumpsysBatteryReset();
        mDeviceConfigStateHelper.restoreOriginalValues();
    }

    @Test
    public void testDeviceIdleManager() {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        assertNotNull(context.getSystemService(Context.DEVICE_IDLE_CONTROLLER));
    }

    @Test
    public void testLightIdleMode() throws Exception {
        final String output = mUiDevice.executeShellCommand("cmd deviceidle enabled light").trim();
        final boolean isEnabled = Integer.parseInt(output) != 0;
        assumeTrue("device idle not enabled", isEnabled);

        // Explicitly set the light idle timeouts to override device specific values and reduce the
        // runtime of this test.
        mDeviceConfigStateHelper.set("light_idle_to", "500");
        mDeviceConfigStateHelper.set("light_idle_to_initial_flex", "1000");

        // Reset idle state.
        setScreenState(true);
        BatteryUtils.runDumpsysBatteryUnplug();
        setScreenState(false);
        PowerManager powerManager = InstrumentationRegistry.getInstrumentation().getContext()
                .getSystemService(PowerManager.class);
        waitUntil("Never made it to IDLE state", 30 /* seconds */, () -> {
            final CallbackAsserter idleModeChangedBroadcastAsserter = CallbackAsserter.forBroadcast(
                    new IntentFilter(PowerManager.ACTION_DEVICE_LIGHT_IDLE_MODE_CHANGED));
            stepIdleState("light");
            final String state = getIdleState("light");
            if ("IDLE".equals(state)) {
                idleModeChangedBroadcastAsserter.assertCalled(
                        "Didn't get light idle mode changed broadcast", 15 /* 15 seconds */);
                assertTrue(powerManager.isDeviceLightIdleMode());
                return true;
            } else {
                assertFalse("Returned true even though state is " + state,
                        powerManager.isDeviceLightIdleMode());
                return false;
            }
        });

        // We're IDLE. Step to IDLE_MAINTENANCE and confirm false is returned.
        CallbackAsserter idleModeChangedBroadcastAsserter = CallbackAsserter.forBroadcast(
                new IntentFilter(PowerManager.ACTION_DEVICE_LIGHT_IDLE_MODE_CHANGED));
        stepIdleState("light");
        idleModeChangedBroadcastAsserter.assertCalled(
                "Didn't get light idle mode changed broadcast", 15 /* 15 seconds */);
        String state = getIdleState("light");
        assertEquals("IDLE_MAINTENANCE", state);
        assertFalse(powerManager.isDeviceLightIdleMode());

        idleModeChangedBroadcastAsserter = CallbackAsserter.forBroadcast(
                new IntentFilter(PowerManager.ACTION_DEVICE_LIGHT_IDLE_MODE_CHANGED));
        stepIdleState("light");
        idleModeChangedBroadcastAsserter.assertCalled(
                "Didn't get light idle mode changed broadcast", 15 /* 15 seconds */);
        state = getIdleState("light");
        assertEquals("IDLE", state);
        assertTrue(powerManager.isDeviceLightIdleMode());

        idleModeChangedBroadcastAsserter = CallbackAsserter.forBroadcast(
                new IntentFilter(PowerManager.ACTION_DEVICE_LIGHT_IDLE_MODE_CHANGED));
        setScreenState(true);
        idleModeChangedBroadcastAsserter.assertCalled(
                "Didn't get light idle mode changed broadcast", 15 /* 15 seconds */);
        assertFalse(powerManager.isDeviceLightIdleMode());
    }

    @Test
    public void testPowerManagerIgnoringBatteryOptimizations() {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();

        assertTrue(context.getSystemService(PowerManager.class)
                .isIgnoringBatteryOptimizations("com.android.shell"));
        assertFalse(context.getSystemService(PowerManager.class)
                .isIgnoringBatteryOptimizations("no.such.package.!!!"));
    }

    @NonNull
    private String getIdleState(String level) throws Exception {
        return mUiDevice.executeShellCommand("cmd deviceidle get " + level).trim();
    }

    private void stepIdleState(String level) throws Exception {
        mUiDevice.executeShellCommand("cmd deviceidle step " + level);
    }

    /**
     * Set the screen state.
     */
    private void setScreenState(boolean on) throws Exception {
        if (on) {
            mUiDevice.executeShellCommand("input keyevent KEYCODE_WAKEUP");
            mUiDevice.executeShellCommand("wm dismiss-keyguard");
        } else {
            mUiDevice.executeShellCommand("input keyevent KEYCODE_SLEEP");
        }
        // Wait a little bit to make sure the screen state has changed.
        Thread.sleep(2_000);
    }
}
