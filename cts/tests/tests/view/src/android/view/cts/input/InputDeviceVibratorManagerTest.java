/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.view.cts.input;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.hardware.input.InputManager;
import android.os.CombinedVibration;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.util.Log;
import android.view.InputDevice;
import android.view.cts.R;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.cts.input.InputJsonParser;
import com.android.cts.input.UinputDevice;
import com.android.cts.input.UinputResultData;
import com.android.cts.input.UinputVibratorManagerTestData;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Test {@link android.view.InputDevice} vibrator manager functionality.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class InputDeviceVibratorManagerTest {
    private static final String TAG = "InputDeviceVibratorTest";
    private InputManager mInputManager;
    private UinputDevice mUinputDevice;
    private InputJsonParser mParser;
    private Instrumentation mInstrumentation;
    private VibratorManager mVibratorManager;
    /** The device id inside the resource file (register command) */
    private int mDeviceId;

    /**
     * Get a vibrator manager from input device with specified Vendor Id and Product Id.
     * @param vid Vendor Id
     * @param pid Product Id
     * @return VibratorManager object in specified InputDevice
     */
    private VibratorManager getVibratorManager(int vid, int pid) {
        final int[] inputDeviceIds = mInputManager.getInputDeviceIds();
        for (int inputDeviceId : inputDeviceIds) {
            final InputDevice inputDevice = mInputManager.getInputDevice(inputDeviceId);
            if (inputDevice.getVendorId() == vid && inputDevice.getProductId() == pid) {
                VibratorManager vibratorManager = inputDevice.getVibratorManager();
                Log.i(TAG, "Input device: " + inputDeviceId + " VendorId: "
                        + inputDevice.getVendorId() + " ProductId: " + inputDevice.getProductId());
                return vibratorManager;
            }
        }
        return null;
    }

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mInputManager = mInstrumentation.getTargetContext().getSystemService(InputManager.class);
        assertNotNull(mInputManager);
        mParser = new InputJsonParser(mInstrumentation.getTargetContext());

        mUinputDevice = UinputDevice.create(mInstrumentation, R.raw.google_gamepad_register,
                InputDevice.SOURCE_KEYBOARD);
        mDeviceId = mUinputDevice.getRegisterCommandDeviceId();
        mVibratorManager = getVibratorManager(mUinputDevice.getVendorId(),
                mUinputDevice.getProductId());
        assertTrue(mVibratorManager != null);
    }

    @After
    public void tearDown() {
        mUinputDevice.close();
    }

    /*
     * Return vibration count
     * @totalVibrations expected vibration times
     * @timeoutMills timeout in milliseconds
     * @return Actual vibration times
     */
    private int getVibrationCount(long totalVibrations, long timeoutMills) {
        final long startTime = SystemClock.elapsedRealtime();
        List<UinputResultData> results = new ArrayList<>();
        int vibrationCount = 0;

        while (vibrationCount < totalVibrations
                && SystemClock.elapsedRealtime() - startTime < timeoutMills) {
            SystemClock.sleep(1000);
            try {
                results = mUinputDevice.getResults(mDeviceId, "vibrating");
                if (results.size() < totalVibrations) {
                    continue;
                }
                vibrationCount = 0;
                for (int i = 0; i < results.size(); i++) {
                    UinputResultData result = results.get(i);
                    if (result.reason.equals("vibrating") && result.deviceId == mDeviceId
                            && (result.status > 0)) {
                        vibrationCount++;
                    }
                }
            }  catch (IOException ex) {
                throw new RuntimeException("Could not get JSON results from HidDevice");
            }
        }
        return vibrationCount;
    }

    /*
     * Test with predefined vibration effects
     * @resourceId The Json file contains predefined vibration effects
     */
    public void testInputVibratorManagerEvents(int resourceId) {
        final List<UinputVibratorManagerTestData> tests =
                mParser.getUinputVibratorManagerTestData(resourceId);

        for (UinputVibratorManagerTestData testData : tests) {
            // Vibration durations must be greater than 0
            assertTrue(testData.durations.size() > 0);
            // Only Mono or Stereo vibration effects are allowed
            assertTrue(testData.amplitudes.size() == 1 || testData.amplitudes.size() == 2);

            final long totalVibrations = testData.durations.size();
            long timeoutMills = 0;
            CombinedVibration.ParallelCombination comb = CombinedVibration.startParallel();

            final int[] ids = mVibratorManager.getVibratorIds();
            for (int i = 0; i < testData.amplitudes.size(); i++) {
                // Verify each vibrator's amplitude array size is same as duration array size
                assertTrue(testData.durations.size() == testData.amplitudes.valueAt(i).size());

                final VibrationEffect effect;
                if (testData.durations.size() == 1) {
                    long duration = testData.durations.get(0);
                    int amplitude = testData.amplitudes.valueAt(i).get(0);
                    effect = VibrationEffect.createOneShot(duration, amplitude);
                    // Set timeout to be 2 times of the effect duration.
                    timeoutMills = duration * 2;
                } else {
                    long[] durations = testData.durations.stream()
                            .mapToLong(Long::longValue).toArray();
                    int[] amplitudes = testData.amplitudes.valueAt(i).stream()
                            .mapToInt(Integer::intValue).toArray();
                    effect = VibrationEffect.createWaveform(
                        durations, amplitudes, -1);
                    // Set timeout to be 2 times of the effect total duration.
                    timeoutMills = Arrays.stream(durations).sum() * 2;
                }

                if (testData.amplitudes.size() == 1) {
                    CombinedVibration mono = CombinedVibration.createParallel(effect);
                    // Start vibration
                    mVibratorManager.vibrate(mono);
                } else {  // testData.amplitudes.size() == 2
                    comb.addVibrator(ids[i], effect);
                    if (i > 0) {
                        // Start vibration
                        CombinedVibration stereo = comb.combine();
                        mVibratorManager.vibrate(stereo);
                    }
                }
            }
            // Verify we got expected numbers of vibration
            assertEquals(totalVibrations, getVibrationCount(totalVibrations, timeoutMills));
        }
    }

    @Test
    public void testInputVibratorManager() {
        testInputVibratorManagerEvents(R.raw.google_gamepad_vibratormanagertests);
    }

    @Test
    public void testGetVibrators() {
        int[] ids = mVibratorManager.getVibratorIds();
        assertEquals(2, ids.length);

        final Vibrator defaultVibrator = mVibratorManager.getDefaultVibrator();
        assertNotNull(defaultVibrator);
        assertTrue(defaultVibrator.hasVibrator());

        for (int i = 0; i < ids.length; i++) {
            final Vibrator vibrator = mVibratorManager.getVibrator(ids[i]);
            assertNotNull(vibrator);
            assertTrue(vibrator.hasVibrator());
        }
    }

    @Test
    public void testUnsupportedVibrationEffectsPreBaked() {
        final int[] ids = mVibratorManager.getVibratorIds();
        CombinedVibration.ParallelCombination comb = CombinedVibration.startParallel();
        for (int i = 0; i < ids.length; i++) {
            comb.addVibrator(ids[i], VibrationEffect.createPredefined(
                    VibrationEffect.EFFECT_CLICK));
        }
        CombinedVibration stereo = comb.combine();
        mVibratorManager.vibrate(stereo);
        // Shouldn't get any vibrations for unsupported effects
        assertEquals(0, getVibrationCount(1 /* totalVibrations */, 1000 /* timeoutMills */));
    }

    @Test
    public void testMixedVibrationEffectsOneShotAndPreBaked() {
        final int[] ids = mVibratorManager.getVibratorIds();
        CombinedVibration.ParallelCombination comb = CombinedVibration.startParallel();
        comb.addVibrator(ids[0], VibrationEffect.createOneShot(1000,
                VibrationEffect.DEFAULT_AMPLITUDE));
        comb.addVibrator(ids[1], VibrationEffect.createPredefined(
                VibrationEffect.EFFECT_CLICK));
        CombinedVibration stereo = comb.combine();
        mVibratorManager.vibrate(stereo);
        // Shouldn't get any vibrations for combination of OneShot and Prebaked.
        // Prebaked effect is not supported by input device vibrator, if the second effect
        // in combined effects is prebaked the combined effect will not be played.
        assertEquals(0, getVibrationCount(1 /* totalVibrations */, 1000 /* timeoutMills */));
    }

    @Test
    public void testMixedVibrationEffectsPreBakedAndOneShot() {
        final int[] ids = mVibratorManager.getVibratorIds();
        CombinedVibration.ParallelCombination comb = CombinedVibration.startParallel();
        comb.addVibrator(ids[0], VibrationEffect.createPredefined(
                VibrationEffect.EFFECT_CLICK));
        comb.addVibrator(ids[1], VibrationEffect.createOneShot(1000,
                VibrationEffect.DEFAULT_AMPLITUDE));
        CombinedVibration stereo = comb.combine();
        mVibratorManager.vibrate(stereo);
        // Shouldn't get any vibrations for combination of Prebaked and OneShot.
        // Prebaked effect is not supported by input device vibrator, if the first effect
        // in combined effects is prebaked the combined effect will not be played.
        assertEquals(0, getVibrationCount(1 /* totalVibrations */, 1000 /* timeoutMills */));
    }

    @Test
    public void testCombinedVibrationOnSingleVibratorId() {
        final int[] ids = mVibratorManager.getVibratorIds();
        CombinedVibration.ParallelCombination comb = CombinedVibration.startParallel();
        comb.addVibrator(ids[0], VibrationEffect.createOneShot(1000,
                VibrationEffect.DEFAULT_AMPLITUDE));
        mVibratorManager.vibrate(comb.combine());
        assertEquals(1, getVibrationCount(1 /* totalVibrations */, 1000 /* timeoutMills */));
    }
}
