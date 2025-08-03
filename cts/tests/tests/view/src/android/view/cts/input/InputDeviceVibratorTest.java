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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.app.Instrumentation;
import android.hardware.input.InputManager;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.Vibrator.OnVibratorStateChangedListener;
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
import com.android.cts.input.UinputVibratorTestData;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Test {@link android.view.InputDevice} vibrator functionality.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class InputDeviceVibratorTest {
    private static final String TAG = "InputDeviceVibratorTest";
    private static final long CALLBACK_TIMEOUT_MILLIS = 5000;

    private InputManager mInputManager;
    private UinputDevice mUinputDevice;
    private InputJsonParser mParser;
    private Instrumentation mInstrumentation;
    private Vibrator mVibrator;
    /** The device id inside the resource file (register command) */
    private int mDeviceId;

    @Rule
    public MockitoRule rule = MockitoJUnit.rule();
    @Mock
    private OnVibratorStateChangedListener mListener;

    /**
     * Get a vibrator from input device with specified Vendor Id and Product Id.
     * @param vid Vendor Id
     * @param pid Product Id
     * @return Vibrator object in specified InputDevice
     */
    private Vibrator getVibrator(int vid, int pid) {
        final int[] inputDeviceIds = mInputManager.getInputDeviceIds();
        for (int inputDeviceId : inputDeviceIds) {
            final InputDevice inputDevice = mInputManager.getInputDevice(inputDeviceId);
            Vibrator vibrator = inputDevice.getVibrator();
            if (vibrator.hasVibrator() && inputDevice.getVendorId() == vid
                    && inputDevice.getProductId() == pid) {
                Log.i(TAG, "Input device: " + inputDeviceId + " VendorId: "
                        + inputDevice.getVendorId() + " ProductId: " + inputDevice.getProductId());
                return vibrator;
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
        mVibrator = getVibrator(mUinputDevice.getVendorId(), mUinputDevice.getProductId());
        assertNotNull(mVibrator);
        mVibrator.addVibratorStateListener(mListener);
        verify(mListener, timeout(CALLBACK_TIMEOUT_MILLIS)
                .times(1)).onVibratorStateChanged(false);
        reset(mListener);
    }

    @After
    public void tearDown() {
        mUinputDevice.close();
    }

    public void testInputVibratorEvents(int resourceId) {
        final List<UinputVibratorTestData> tests = mParser.getUinputVibratorTestData(resourceId);

        for (UinputVibratorTestData test : tests) {
            assertTrue(test.durations.size() == test.amplitudes.size());
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

            // Start vibration
            mVibrator.vibrate(effect);
            // Verify vibrator state listener
            verify(mListener, timeout(CALLBACK_TIMEOUT_MILLIS)
                    .times(1)).onVibratorStateChanged(true);
            assertTrue(mVibrator.isVibrating());

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
            assertEquals(vibrationCount, totalVibrations);
            // Verify vibrator state listener
            verify(mListener, timeout(CALLBACK_TIMEOUT_MILLIS)
                    .times(1)).onVibratorStateChanged(false);
            assertFalse(mVibrator.isVibrating());
            reset(mListener);
        }
        // Shouldn't get any listener state callback after removal
        mVibrator.removeVibratorStateListener(mListener);
        // Start vibration
        mVibrator.vibrate(VibrationEffect.createOneShot(100 /* duration */, 255 /* amplitude */));
        assertTrue(mVibrator.isVibrating());
        // Verify vibrator state listener
        verify(mListener, never()).onVibratorStateChanged(anyBoolean());
    }

    @Test
    public void testInputVibrator() {
        testInputVibratorEvents(R.raw.google_gamepad_vibratortests);
    }

}
