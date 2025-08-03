/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static android.os.VibrationEffect.VibrationParameter.targetAmplitude;
import static android.os.VibrationEffect.VibrationParameter.targetFrequency;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.os.CombinedVibration;
import android.os.SystemClock;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.Vibrator.OnVibratorStateChangedListener;
import android.os.VibratorManager;
import android.os.vibrator.VibratorFrequencyProfile;
import android.os.vibrator.cts.VibratorTestActivity;
import android.util.SparseArray;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.time.Duration;
import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
public class VibratorManagerTest {
    @Rule
    public ActivityScenarioRule<VibratorTestActivity> mActivityRule =
            new ActivityScenarioRule<>(VibratorTestActivity.class);

    @Rule
    public final AdoptShellPermissionsRule mAdoptShellPermissionsRule =
            new AdoptShellPermissionsRule(
                    InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                    android.Manifest.permission.ACCESS_VIBRATOR_STATE);
    
    @Rule
    public final MockitoRule mMockitoRule = MockitoJUnit.rule();

    private static final long CALLBACK_TIMEOUT_MILLIS = 5_000;
    private static final VibrationAttributes VIBRATION_ATTRIBUTES =
            new VibrationAttributes.Builder()
                    .setUsage(VibrationAttributes.USAGE_TOUCH)
                    .build();

    /**
     * These listeners are used for test helper methods like asserting it starts/stops vibrating.
     * It's not strongly required that the interactions with these mocks are validated by all tests.
     */
    private final SparseArray<OnVibratorStateChangedListener> mStateListeners = new SparseArray<>();

    private VibratorManager mVibratorManager;

    @Before
    public void setUp() {
        mVibratorManager =
                InstrumentationRegistry.getInstrumentation().getContext().getSystemService(
                        VibratorManager.class);

        for (int vibratorId : mVibratorManager.getVibratorIds()) {
            OnVibratorStateChangedListener listener = mock(OnVibratorStateChangedListener.class);
            mVibratorManager.getVibrator(vibratorId).addVibratorStateListener(listener);
            mStateListeners.put(vibratorId, listener);
            // Adding a listener to the Vibrator should trigger the callback once with the current
            // vibrator state, so reset mocks to clear it for tests.
            assertVibratorStateChangesTo(vibratorId, false);
            clearInvocations(listener);
        }
    }

    @After
    public void cleanUp() {
        // Clearing invocations so we can use these listeners to wait for the vibrator to
        // asynchronously cancel the ongoing vibration, if any was left pending by a test.
        for (int i = 0; i < mStateListeners.size(); i++) {
            clearInvocations(mStateListeners.valueAt(i));
        }
        mVibratorManager.cancel();

        for (int i = 0; i < mStateListeners.size(); i++) {
            int vibratorId = mStateListeners.keyAt(i);

            // Wait for cancel to take effect, if device is still vibrating.
            if (mVibratorManager.getVibrator(vibratorId).isVibrating()) {
                assertStopsVibrating(vibratorId);
            }

            // Remove all listeners added by the tests.
            mVibratorManager.getVibrator(vibratorId).removeVibratorStateListener(
                    mStateListeners.valueAt(i));
        }
    }

    @Test
    public void testGetVibratorIds() {
        // Just make sure it doesn't crash or return null when this is called; we don't really have
        // a way to test which vibrators will be returned.
        assertThat(mVibratorManager.getVibratorIds()).isNotNull();
        assertThat(mVibratorManager.getVibratorIds()).asList().containsNoDuplicates();
    }

    @Test
    public void testGetNonExistentVibratorId() {
        int missingId = Arrays.stream(mVibratorManager.getVibratorIds()).max().orElse(0) + 1;
        Vibrator vibrator = mVibratorManager.getVibrator(missingId);
        assertThat(vibrator).isNotNull();
        assertThat(vibrator.hasVibrator()).isFalse();
    }

    @Test
    public void testGetDefaultVibratorIsSameAsVibratorService() {
        // Note that VibratorTest parameterization relies on these two vibrators being identical.
        // It only runs vibrator tests on the result of one of the APIs.
        Vibrator systemVibrator =
                InstrumentationRegistry.getInstrumentation().getContext().getSystemService(
                        Vibrator.class);
        assertThat(mVibratorManager.getDefaultVibrator()).isSameInstanceAs(systemVibrator);
    }

    @Test
    public void testCancel() {
        mVibratorManager.vibrate(CombinedVibration.createParallel(
                VibrationEffect.createOneShot(10_000, VibrationEffect.DEFAULT_AMPLITUDE)));
        assertStartsVibrating();

        mVibratorManager.cancel();
        assertStopsVibrating();
    }

    @LargeTest
    @Test
    public void testCombinedVibrationOneShotStartsAndFinishesVibration() {
        VibrationEffect oneShot =
                VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE);
        mVibratorManager.vibrate(CombinedVibration.createParallel(oneShot));
        assertStartsThenStopsVibrating(300);
    }

    @Test
    public void testCombinedVibrationOneShotMaxAmplitude() {
        VibrationEffect oneShot = VibrationEffect.createOneShot(500, 255 /* Max amplitude */);
        mVibratorManager.vibrate(CombinedVibration.createParallel(oneShot));
        assertStartsVibrating();

        mVibratorManager.cancel();
        assertStopsVibrating();
    }

    @Test
    public void testCombinedVibrationOneShotMinAmplitude() {
        VibrationEffect oneShot = VibrationEffect.createOneShot(100, 1 /* Min amplitude */);
        mVibratorManager.vibrate(CombinedVibration.createParallel(oneShot),
                VIBRATION_ATTRIBUTES);
        assertStartsVibrating();
    }

    @LargeTest
    @Test
    public void testCombinedVibrationWaveformStartsAndFinishesVibration() {
        final long[] timings = new long[]{100, 200, 300, 400, 500};
        final int[] amplitudes = new int[]{64, 128, 255, 128, 64};
        VibrationEffect waveform = VibrationEffect.createWaveform(timings, amplitudes, -1);
        mVibratorManager.vibrate(CombinedVibration.createParallel(waveform));
        assertStartsThenStopsVibrating(1500);
    }

    @LargeTest
    @Test
    public void testCombinedVibrationWaveformRepeats() {
        final long[] timings = new long[]{100, 200, 300, 400, 500};
        final int[] amplitudes = new int[]{64, 128, 255, 128, 64};
        VibrationEffect waveform = VibrationEffect.createWaveform(timings, amplitudes, 0);
        mVibratorManager.vibrate(CombinedVibration.createParallel(waveform));
        assertStartsVibrating();

        SystemClock.sleep(2000);
        int[] vibratorIds = mVibratorManager.getVibratorIds();
        for (int vibratorId : vibratorIds) {
            assertWithMessage(
                    "Expected repeating parallel waveform to continue vibrating on vibrator %s"
                            + " after initial duration", vibratorId)
                    .that(mVibratorManager.getVibrator(vibratorId).isVibrating()).isTrue();
        }

        mVibratorManager.cancel();
        assertStopsVibrating();
    }

    @LargeTest
    @Test
    public void testCombinedVibrationWaveformWithFrequencyStartsAndFinishesVibration() {
        Vibrator defaultVibrator = mVibratorManager.getDefaultVibrator();
        assumeTrue(defaultVibrator.hasFrequencyControl());

        VibratorFrequencyProfile frequencyProfile = defaultVibrator.getFrequencyProfile();
        float minFrequency = frequencyProfile.getMinFrequency();
        float maxFrequency = frequencyProfile.getMaxFrequency();
        float resonantFrequency = defaultVibrator.getResonantFrequency();
        float sustainFrequency = Float.isNaN(resonantFrequency)
                ? (maxFrequency + minFrequency) / 2
                : resonantFrequency;

        // Then ramp to zero amplitude at fixed frequency.
        VibrationEffect waveform =
                VibrationEffect.startWaveform(targetAmplitude(0), targetFrequency(minFrequency))
                        // Ramp from min to max frequency and from zero to max amplitude.
                        .addTransition(Duration.ofMillis(10),
                                targetAmplitude(1), targetFrequency(maxFrequency))
                        // Ramp back to min frequency and zero amplitude.
                        .addTransition(Duration.ofMillis(10),
                                targetAmplitude(0), targetFrequency(minFrequency))
                        // Then sustain at a fixed frequency and half amplitude.
                        .addTransition(Duration.ZERO,
                                targetAmplitude(0.5f), targetFrequency(sustainFrequency))
                        .addSustain(Duration.ofMillis(20))
                        // Ramp from min to max frequency and at max amplitude.
                        .addTransition(Duration.ZERO,
                                targetAmplitude(1), targetFrequency(minFrequency))
                        .addTransition(Duration.ofMillis(10), targetFrequency(maxFrequency))
                        // Ramp from max to min amplitude at max frequency.
                        .addTransition(Duration.ofMillis(10), targetAmplitude(0))
                        .build();
        mVibratorManager.vibrate(CombinedVibration.createParallel(waveform));
        assertStartsThenStopsVibrating(50);
    }

    @Test
    public void testCombinedVibrationTargetingSingleVibrator() {
        int[] vibratorIds = mVibratorManager.getVibratorIds();
        assumeTrue(vibratorIds.length >= 2);

        VibrationEffect oneShot =
                VibrationEffect.createOneShot(10_000, VibrationEffect.DEFAULT_AMPLITUDE);

        // Vibrate each vibrator in turn, and assert that all the others are off.
        for (int vibratorId : vibratorIds) {
            Vibrator vibrator = mVibratorManager.getVibrator(vibratorId);
            mVibratorManager.vibrate(
                    CombinedVibration.startParallel()
                            .addVibrator(vibratorId, oneShot)
                            .combine());
            assertStartsVibrating(vibratorId);

            for (int otherVibratorId : vibratorIds) {
                if (otherVibratorId != vibratorId) {
                    assertWithMessage(
                            "Expected vibrator %s not vibrating when combined vibration started"
                                    + " on vibrator %s", otherVibratorId, vibratorId)
                            .that(mVibratorManager.getVibrator(otherVibratorId).isVibrating())
                            .isFalse();
                }
            }

            vibrator.cancel();
            assertStopsVibrating(vibratorId);
        }
    }

    private void assertStartsThenStopsVibrating(long duration) {
        for (int i = 0; i < mStateListeners.size(); i++) {
            assertStartsVibrating(mStateListeners.keyAt(i));
        }
        SystemClock.sleep(duration);
        assertStopsVibrating();
    }

    private void assertStartsVibrating() {
        assertVibratorStateChangesTo(true);
    }

    private void assertStartsVibrating(int vibratorId) {
        assertVibratorStateChangesTo(vibratorId, true);
    }

    private void assertStopsVibrating() {
        assertVibratorStateChangesTo(false);
    }

    private void assertStopsVibrating(int vibratorId) {
        assertVibratorStateChangesTo(vibratorId, false);
    }

    private void assertVibratorStateChangesTo(boolean expected) {
        for (int i = 0; i < mStateListeners.size(); i++) {
            assertVibratorStateChangesTo(mStateListeners.keyAt(i), expected);
        }
    }

    private void assertVibratorStateChangesTo(int vibratorId, boolean expected) {
        OnVibratorStateChangedListener listener = mStateListeners.get(vibratorId);
        verify(listener,
                timeout(CALLBACK_TIMEOUT_MILLIS).atLeastOnce().description(
                        "Vibrator " + vibratorId + " expected to turn "
                                + (expected ? "on" : "off")))
                .onVibratorStateChanged(eq(expected));
    }
}
