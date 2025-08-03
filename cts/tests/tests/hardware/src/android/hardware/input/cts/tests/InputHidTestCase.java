/*
 * Copyright 2020 The Android Open Source Project
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

package android.hardware.input.cts.tests;

import static android.hardware.lights.LightsRequest.Builder;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.hardware.BatteryState;
import android.hardware.input.InputManager;
import android.hardware.lights.Light;
import android.hardware.lights.LightState;
import android.hardware.lights.LightsManager;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.Vibrator.OnVibratorStateChangedListener;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.WindowManager;

import com.android.cts.input.HidBatteryTestData;
import com.android.cts.input.HidDevice;
import com.android.cts.input.HidLightTestData;
import com.android.cts.input.HidResultData;
import com.android.cts.input.HidTestData;
import com.android.cts.input.HidVibratorTestData;
import com.android.cts.input.InputJsonParser;

import com.google.common.primitives.Floats;

import org.junit.Rule;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public abstract class InputHidTestCase extends InputTestCase {

    private static final String TAG = "InputHidTestCase";
    // Sync with linux uhid_event_type::UHID_OUTPUT
    private static final byte UHID_EVENT_TYPE_UHID_OUTPUT = 6;
    private static final long CALLBACK_TIMEOUT_MILLIS = 5000;

    private final int mRegisterResourceId;
    private final WindowManager mWindowManager;
    private final boolean mIsLeanback;
    private final boolean mVolumeKeysHandledInWindowManager;

    private HidDevice mHidDevice;
    private int mDeviceId;
    private boolean mDelayAfterSetup = false;
    private InputJsonParser mParser;
    private int mVid;
    private int mPid;

    @Rule
    public MockitoRule rule = MockitoJUnit.rule();
    @Mock
    private OnVibratorStateChangedListener mListener;

    InputHidTestCase(int registerResourceId) {
        mRegisterResourceId = registerResourceId;
        Context context = mInstrumentation.getTargetContext();
        mWindowManager = context.getSystemService(WindowManager.class);
        mIsLeanback = context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK);
        mVolumeKeysHandledInWindowManager = context.getResources().getBoolean(
                Resources.getSystem().getIdentifier("config_handleVolumeKeysInWindowManager",
                        "bool", "android"));
    }

    @Override
    void onSetUp() {
        mParser = new InputJsonParser(mInstrumentation.getTargetContext());
        mVid = mParser.readVendorId(mRegisterResourceId);
        mPid = mParser.readProductId(mRegisterResourceId);
        mDeviceId = mParser.readDeviceId(mRegisterResourceId);
        mHidDevice = new HidDevice(mInstrumentation,
                mDeviceId,
                mVid,
                mPid,
                mParser.readSources(mRegisterResourceId) | getAdditionalSources(),
                mParser.readRegisterCommand(mRegisterResourceId));
        assertNotNull(mHidDevice);
        // Even though we already wait for all possible callbacks such as UHID_START and UHID_OPEN,
        // and wait for the correct device to appear by specifying expected source type in the
        // register command, some devices, perhaps due to splitting, do not produce events as soon
        // as they are created. Adding a small delay resolves this issue.
        if (mDelayAfterSetup) {
            SystemClock.sleep(1000);
        }
    }

    @Override
    void onTearDown() {
        if (mHidDevice != null) {
            mHidDevice.close();
        }
    }

    protected void addDelayAfterSetup() {
        mDelayAfterSetup = true;
    }

    protected int getAdditionalSources() {
        return 0;
    }

    /** Check if input device has specific capability */
    interface Capability {
        boolean check(InputDevice inputDevice);
    }

    private boolean isForwardedToApps(KeyEvent e) {
        int keyCode = e.getKeyCode();
        if (mWindowManager.isGlobalKey(keyCode)) {
            return false;
        }
        if (isVolumeKey(keyCode) && (mIsLeanback || mVolumeKeysHandledInWindowManager)) {
            return false;
        }
        return true;
    }

    private boolean isVolumeKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_VOLUME_UP
                || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE;
    }

    /**
     * Gets an input device with the given capability and with Vendor and Product IDs that match the
     * ones specified in the registration command.
     */
    protected InputDevice getInputDevice(Capability capability) {
        final InputManager inputManager = Objects.requireNonNull(
                mInstrumentation.getTargetContext().getSystemService(InputManager.class));
        final int[] inputDeviceIds = inputManager.getInputDeviceIds();
        for (int inputDeviceId : inputDeviceIds) {
            final InputDevice inputDevice = inputManager.getInputDevice(inputDeviceId);
            Objects.requireNonNull(inputDevice, "Failed to get InputDevice");
            if (inputDevice.getVendorId() == mVid && inputDevice.getProductId() == mPid
                    && capability.check(inputDevice)) {
                return inputDevice;
            }
        }
        return null;
    }

    /**
     * Gets a vibrator from input device with specified Vendor Id and Product Id
     * from device registration command.
     * @return Vibrator object in specified InputDevice
     */
    private Vibrator getVibrator() {
        InputDevice inputDevice = getInputDevice((d) -> d.getVibrator().hasVibrator());
        if (inputDevice == null) {
            fail("Failed to find test device with vibrator");
        }
        return inputDevice.getVibrator();
    }

    /**
     * Gets a light manager object from input device with specified Vendor Id and Product Id
     * from device registration command.
     * @return LightsManager object in specified InputDevice
     */
    private LightsManager getLightsManager() {
        InputDevice inputDevice = getInputDevice(
                (d) -> !d.getLightsManager().getLights().isEmpty());
        if (inputDevice == null) {
            fail("Failed to find test device with light");
        }
        return inputDevice.getLightsManager();
    }

    protected void testInputEvents(int resourceId) {
        List<HidTestData> tests = mParser.getHidTestData(resourceId);
        // Remove tests which contain keys that are not forwarded to apps
        tests.removeIf(testData -> testData.events.stream().anyMatch(
                e -> e instanceof KeyEvent && !isForwardedToApps((KeyEvent) e)));

        for (HidTestData testData: tests) {
            mCurrentTestCase = testData.name;
            // Send all of the HID reports
            for (int i = 0; i < testData.reports.size(); i++) {
                final String report = testData.reports.get(i);
                mHidDevice.sendHidReport(report);
            }
            verifyEvents(testData.events);
        }
        assertNoMoreEvents();
    }

    private boolean verifyVibratorReportData(HidVibratorTestData test, HidResultData result) {
        for (Map.Entry<Integer, Integer> entry : test.verifyMap.entrySet()) {
            final int index = entry.getKey();
            final int value = entry.getValue();
            if ((result.reportData[index] & 0XFF) != value) {
                Log.v(TAG, "index=" + index + " value= " + value
                        + "actual= " + (result.reportData[index] & 0XFF));
                return false;
            }
        }
        final int ffLeft = result.reportData[test.leftFfIndex] & 0xFF;
        final int ffRight = result.reportData[test.rightFfIndex] & 0xFF;

        return ffLeft > 0 && ffRight > 0;
    }

    public void testInputVibratorEvents(int resourceId) throws Exception {
        final List<HidVibratorTestData> tests = mParser.getHidVibratorTestData(resourceId);

        for (HidVibratorTestData test : tests) {
            assertEquals(test.durations.size(), test.amplitudes.size());
            assertTrue(test.durations.size() > 0);

            final long timeoutMills;
            final long totalVibrations = test.durations.size();
            final VibrationEffect effect;
            if (test.durations.size() == 1) {
                long duration = test.durations.get(0);
                int amplitude = test.amplitudes.get(0);
                effect = VibrationEffect.createOneShot(duration, amplitude);
                // Set timeout to be 2 times of the effect duration.
                timeoutMills = duration * 2;
            } else {
                long[] durations = test.durations.stream().mapToLong(Long::longValue).toArray();
                int[] amplitudes = test.amplitudes.stream().mapToInt(Integer::intValue).toArray();
                effect = VibrationEffect.createWaveform(
                    durations, amplitudes, -1);
                // Set timeout to be 2 times of the effect total duration.
                timeoutMills = Arrays.stream(durations).sum() * 2;
            }

            final Vibrator vibrator = getVibrator();
            assertNotNull(vibrator);
            vibrator.addVibratorStateListener(mListener);
            verify(mListener, timeout(CALLBACK_TIMEOUT_MILLIS)
                    .times(1)).onVibratorStateChanged(false);
            reset(mListener);
            // Start vibration
            vibrator.vibrate(effect);
            // Verify vibrator state listener
            verify(mListener, timeout(CALLBACK_TIMEOUT_MILLIS)
                    .times(1)).onVibratorStateChanged(true);
            assertTrue(vibrator.isVibrating());

            final long startTime = SystemClock.elapsedRealtime();
            List<HidResultData> results = new ArrayList<>();
            int vibrationCount = 0;
            // Check the vibration ffLeft and ffRight amplitude to be expected.
            while (vibrationCount < totalVibrations
                    && SystemClock.elapsedRealtime() - startTime < timeoutMills) {
                SystemClock.sleep(1000);

                results = mHidDevice.getResults(mDeviceId, UHID_EVENT_TYPE_UHID_OUTPUT);
                if (results.size() < totalVibrations) {
                    continue;
                }
                vibrationCount = 0;
                for (int i = 0; i < results.size(); i++) {
                    HidResultData result = results.get(i);
                    if (result.deviceId == mDeviceId
                            && verifyVibratorReportData(test, result)) {
                        int ffLeft = result.reportData[test.leftFfIndex] & 0xFF;
                        int ffRight = result.reportData[test.rightFfIndex] & 0xFF;
                        Log.v(TAG, "eventId=" + result.eventId + " reportType="
                                + result.reportType + " left=" + ffLeft + " right=" + ffRight);
                        // Check the amplitudes of FF effect are expected.
                        if (ffLeft == test.amplitudes.get(vibrationCount)
                                && ffRight == test.amplitudes.get(vibrationCount)) {
                            vibrationCount++;
                        }
                    }
                }
            }
            assertEquals(vibrationCount, totalVibrations);
            // Verify vibrator state listener
            verify(mListener, timeout(CALLBACK_TIMEOUT_MILLIS)
                    .times(1)).onVibratorStateChanged(false);
            assertFalse(vibrator.isVibrating());
            vibrator.removeVibratorStateListener(mListener);
            reset(mListener);
        }
    }

    public void testInputVibratorManagerEvents(int resourceId) throws Exception {
        final List<HidVibratorTestData> tests = mParser.getHidVibratorTestData(resourceId);

        for (HidVibratorTestData test : tests) {
            assertEquals(test.durations.size(), test.amplitudes.size());
            assertTrue(test.durations.size() > 0);

            final long timeoutMills;
            final long totalVibrations = test.durations.size();
            final VibrationEffect effect;
            if (test.durations.size() == 1) {
                long duration = test.durations.get(0);
                int amplitude = test.amplitudes.get(0);
                effect = VibrationEffect.createOneShot(duration, amplitude);
                // Set timeout to be 2 times of the effect duration.
                timeoutMills = duration * 2;
            } else {
                long[] durations = test.durations.stream().mapToLong(Long::longValue).toArray();
                int[] amplitudes = test.amplitudes.stream().mapToInt(Integer::intValue).toArray();
                effect = VibrationEffect.createWaveform(
                    durations, amplitudes, -1);
                // Set timeout to be 2 times of the effect total duration.
                timeoutMills = Arrays.stream(durations).sum() * 2;
            }

            final Vibrator vibrator = getVibrator();
            assertNotNull(vibrator);
            // Start vibration
            vibrator.vibrate(effect);
            final long startTime = SystemClock.elapsedRealtime();
            List<HidResultData> results = new ArrayList<>();
            int vibrationCount = 0;
            // Check the vibration ffLeft and ffRight amplitude to be expected.
            while (vibrationCount < totalVibrations
                    && SystemClock.elapsedRealtime() - startTime < timeoutMills) {
                SystemClock.sleep(1000);

                results = mHidDevice.getResults(mDeviceId, UHID_EVENT_TYPE_UHID_OUTPUT);
                if (results.size() < totalVibrations) {
                    continue;
                }
                vibrationCount = 0;
                for (int i = 0; i < results.size(); i++) {
                    HidResultData result = results.get(i);
                    if (result.deviceId == mDeviceId
                            && verifyVibratorReportData(test, result)) {
                        int ffLeft = result.reportData[test.leftFfIndex] & 0xFF;
                        int ffRight = result.reportData[test.rightFfIndex] & 0xFF;
                        Log.v(TAG, "eventId=" + result.eventId + " reportType="
                                + result.reportType + " left=" + ffLeft + " right=" + ffRight);
                        // Check the amplitudes of FF effect are expected.
                        if (ffLeft == test.amplitudes.get(vibrationCount)
                                && ffRight == test.amplitudes.get(vibrationCount)) {
                            vibrationCount++;
                        }
                    }
                }
            }
            assertEquals(vibrationCount, totalVibrations);
        }
    }

    public void testInputBatteryEvents(int resourceId) {
        final InputDevice inputDevice = getInputDevice((d) -> d.getBatteryState().isPresent());
        assertNotNull("Failed to find test device with battery", inputDevice);

        final List<HidBatteryTestData> tests = mParser.getHidBatteryTestData(resourceId);
        for (HidBatteryTestData testData : tests) {

            // Send all of the HID reports
            for (int i = 0; i < testData.reports.size(); i++) {
                final String report = testData.reports.get(i);
                mHidDevice.sendHidReport(report);
            }
            // Wait for power_supply sysfs node get updated.
            SystemClock.sleep(100);

            final BatteryState batteryState = inputDevice.getBatteryState();
            assertNotNull(batteryState);
            assertEquals("Test: " + testData.name, testData.status, batteryState.getStatus());
            final float capacity = batteryState.getCapacity();
            assertTrue("Test: " + testData.name
                            + " got capacity " + capacity
                            + ", expected " + Arrays.toString(testData.capacities),
                    Floats.contains(testData.capacities, capacity));
        }
    }

    public void testInputLightsManager(int resourceId) throws Exception {
        final LightsManager lightsManager = getLightsManager();
        final List<Light> lights = lightsManager.getLights();

        final List<HidLightTestData> tests = mParser.getHidLightTestData(resourceId);
        for (HidLightTestData test : tests) {
            Light light = null;
            for (int i = 0; i < lights.size(); i++) {
                if (lights.get(i).getType() == test.lightType
                        && test.lightName.equals(lights.get(i).getName())) {
                    light = lights.get(i);
                }
            }
            assertNotNull("Light type " + test.lightType + " name " + test.lightName
                    + " does not exist.  Lights found: " + lights, light);
            try (LightsManager.LightsSession session = lightsManager.openSession()) {
                // Can't set both player id and color in same LightState
                assertFalse(test.lightColor > 0 && test.lightPlayerId > 0);
                // Issue the session requests to turn single light on
                if (test.lightPlayerId > 0) {
                    session.requestLights(new Builder()
                            .addLight(light, (new LightState.Builder())
                            .setPlayerId(test.lightPlayerId).build()).build());
                } else {
                    session.requestLights(new Builder()
                            .addLight(light, (new LightState.Builder()).setColor(test.lightColor)
                            .build()).build());
                }
                // Some devices (e.g. Sixaxis) defer sending output packets until they've seen at
                // least one input packet.
                if (!test.report.isEmpty()) {
                    mHidDevice.sendHidReport(test.report);
                }
                // Delay before sysfs node was updated.
                SystemClock.sleep(200);
                // Verify HID report data
                List<HidResultData> results = mHidDevice.getResults(mDeviceId,
                        test.hidEventType);
                assertFalse(results.isEmpty());
                // We just check the last HID output to be expected.
                HidResultData result = results.get(results.size() - 1);
                for (Map.Entry<Integer, Integer> entry : test.expectedHidData.entrySet()) {
                    final int index = entry.getKey();
                    final int value = entry.getValue();
                    int actual = result.reportData[index] & 0xFF;
                    assertEquals("Led data index " + index, value, actual);

                }

                // Then the light state should be what we requested.
                if (test.lightPlayerId > 0) {
                    assertThat(lightsManager.getLightState(light).getPlayerId())
                            .isEqualTo(test.lightPlayerId);
                } else {
                    assertThat(lightsManager.getLightState(light).getColor())
                            .isEqualTo(test.lightColor);
                }
            }
        }
    }
}
