/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.server.telecom.tests;

import static android.os.VibrationEffect.EFFECT_CLICK;
import static android.provider.Settings.Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.VolumeShaper;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorInfo;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.util.Pair;

import androidx.test.filters.SmallTest;

import com.android.server.telecom.AsyncRingtonePlayer;
import com.android.server.telecom.Call;
import com.android.server.telecom.CallState;
import com.android.server.telecom.InCallController;
import com.android.server.telecom.InCallTonePlayer;
import com.android.server.telecom.Ringer;
import com.android.server.telecom.RingtoneFactory;
import com.android.server.telecom.SystemSettingsUtil;
import com.android.server.telecom.flags.FeatureFlags;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Spy;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

@RunWith(JUnit4.class)
public class RingerTest extends TelecomTestCase {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final Uri FAKE_RINGTONE_URI = Uri.parse("content://media/fake/audio/1729");
    // Returned when the a URI-based VibrationEffect is attempted, to avoid depending on actual
    // device configuration for ringtone URIs. The actual Uri can be verified via the
    // VibrationEffectProxy mock invocation.
    private static final VibrationEffect URI_VIBRATION_EFFECT =
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK);
    private static final VibrationEffect EXPECTED_SIMPLE_VIBRATION_PATTERN =
            VibrationEffect.createWaveform(
                    new long[] {0, 1000, 1000}, new int[] {0, 255, 0}, 1);
    private static final VibrationEffect EXPECTED_PULSE_VIBRATION_PATTERN =
            VibrationEffect.createWaveform(
                    Ringer.PULSE_PATTERN, Ringer.PULSE_AMPLITUDE, 5);

    @Mock InCallTonePlayer.Factory mockPlayerFactory;
    @Mock SystemSettingsUtil mockSystemSettingsUtil;
    @Mock RingtoneFactory mockRingtoneFactory;
    @Mock Vibrator mockVibrator;
    @Mock VibratorInfo mockVibratorInfo;
    @Mock InCallController mockInCallController;
    @Mock NotificationManager mockNotificationManager;
    @Mock Ringer.AccessibilityManagerAdapter mockAccessibilityManagerAdapter;
    @Mock private FeatureFlags mFeatureFlags;

    @Spy Ringer.VibrationEffectProxy spyVibrationEffectProxy;

    @Mock InCallTonePlayer mockTonePlayer;
    @Mock Call mockCall1;
    @Mock Call mockCall2;

    private static final PhoneAccountHandle PA_HANDLE =
            new PhoneAccountHandle(new ComponentName("pa_pkg", "pa_cls"),
                    "pa_id");

    boolean mIsHapticPlaybackSupported = true;  // Note: initializeRinger() after changes.
    AsyncRingtonePlayer asyncRingtonePlayer = new AsyncRingtonePlayer();
    Ringer mRingerUnderTest;
    AudioManager mockAudioManager;
    CompletableFuture<Void> mRingCompletionFuture = new CompletableFuture<>();

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        mContext = spy(mComponentContextFixture.getTestDouble().getApplicationContext());
        when(mFeatureFlags.telecomResolveHiddenDependencies()).thenReturn(true);
        doReturn(URI_VIBRATION_EFFECT).when(spyVibrationEffectProxy).get(any(), any());
        when(mockPlayerFactory.createPlayer(any(Call.class), anyInt())).thenReturn(mockTonePlayer);
        mockAudioManager = mContext.getSystemService(AudioManager.class);
        when(mockAudioManager.getRingerMode()).thenReturn(AudioManager.RINGER_MODE_NORMAL);
        when(mockVibrator.getInfo()).thenReturn(mockVibratorInfo);
        when(mockSystemSettingsUtil.isHapticPlaybackSupported(any(Context.class)))
                .thenAnswer((invocation) -> mIsHapticPlaybackSupported);
        mockNotificationManager =mContext.getSystemService(NotificationManager.class);
        when(mockTonePlayer.startTone()).thenReturn(true);
        when(mockNotificationManager.matchesCallFilter(any(Uri.class))).thenReturn(true);
        when(mockCall1.getState()).thenReturn(CallState.RINGING);
        when(mockCall2.getState()).thenReturn(CallState.RINGING);
        when(mockCall1.getAssociatedUser()).thenReturn(PA_HANDLE.getUserHandle());
        when(mockCall2.getAssociatedUser()).thenReturn(PA_HANDLE.getUserHandle());
        when(mockCall1.getTargetPhoneAccount()).thenReturn(PA_HANDLE);
        when(mockCall2.getTargetPhoneAccount()).thenReturn(PA_HANDLE);
        // Set BT active state in tests to ensure that we do not end up blocking tests for 1 sec
        // waiting for BT to connect in unit tests by default.
        asyncRingtonePlayer.updateBtActiveState(true);

        createRingerUnderTest();
    }

    /**
     * (Re-)Creates the Ringer for the test. This needs to be called if changing final properties,
     * like mIsHapticPlaybackSupported.
     */
    private void createRingerUnderTest() {
        mRingerUnderTest = new Ringer(mockPlayerFactory, mContext, mockSystemSettingsUtil,
                asyncRingtonePlayer, mockRingtoneFactory, mockVibrator, spyVibrationEffectProxy,
                mockInCallController, mockNotificationManager, mockAccessibilityManagerAdapter,
                mFeatureFlags);
        // This future is used to wait for AsyncRingtonePlayer to finish its part.
        mRingerUnderTest.setBlockOnRingingFuture(mRingCompletionFuture);
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @SmallTest
    @Test
    public void testSimpleVibrationPrecedesValidSupportedDefaultRingVibrationOverride()
            throws Exception {
        when(mFeatureFlags.useDeviceProvidedSerializedRingerVibration()).thenReturn(true);
        mockVibrationResourceValues(
                """
                    <vibration-effect>
                        <predefined-effect name="click"/>
                    </vibration-effect>
                """,
                /* useSimpleVibration= */ true);
        when(mockVibratorInfo.areVibrationFeaturesSupported(any())).thenReturn(true);

        createRingerUnderTest();

        assertEquals(EXPECTED_SIMPLE_VIBRATION_PATTERN, mRingerUnderTest.mDefaultVibrationEffect);
    }

    @SmallTest
    @Test
    public void testDefaultRingVibrationOverrideNotUsedWhenFeatureIsDisabled()
            throws Exception {
        when(mFeatureFlags.useDeviceProvidedSerializedRingerVibration()).thenReturn(false);
        mockVibrationResourceValues(
                """
                    <vibration-effect>
                        <waveform-effect>
                            <waveform-entry durationMs="100" amplitude="0"/>
                            <repeating>
                                <waveform-entry durationMs="500" amplitude="default"/>
                                <waveform-entry durationMs="700" amplitude="0"/>
                            </repeating>
                        </waveform-effect>
                    </vibration-effect>
                """,
                /* useSimpleVibration= */ false);
        when(mockVibratorInfo.areVibrationFeaturesSupported(any())).thenReturn(true);

        createRingerUnderTest();

        assertEquals(EXPECTED_PULSE_VIBRATION_PATTERN, mRingerUnderTest.mDefaultVibrationEffect);
    }

    @SmallTest
    @Test
    public void testValidSupportedRepeatingDefaultRingVibrationOverride() throws Exception {
        when(mFeatureFlags.useDeviceProvidedSerializedRingerVibration()).thenReturn(true);
        mockVibrationResourceValues(
                """
                    <vibration-effect>
                        <waveform-effect>
                            <waveform-entry durationMs="100" amplitude="0"/>
                            <repeating>
                                <waveform-entry durationMs="500" amplitude="default"/>
                                <waveform-entry durationMs="700" amplitude="0"/>
                            </repeating>
                        </waveform-effect>
                    </vibration-effect>
                """,
                /* useSimpleVibration= */ false);
        when(mockVibratorInfo.areVibrationFeaturesSupported(any())).thenReturn(true);

        createRingerUnderTest();

        assertEquals(
                VibrationEffect.createWaveform(new long[]{100, 500, 700}, /* repeat= */ 1),
                mRingerUnderTest.mDefaultVibrationEffect);
    }

    @SmallTest
    @Test
    public void testValidSupportedNonRepeatingDefaultRingVibrationOverride() throws Exception {
        when(mFeatureFlags.useDeviceProvidedSerializedRingerVibration()).thenReturn(true);
        mockVibrationResourceValues(
                """
                    <vibration-effect>
                        <predefined-effect name="click"/>
                    </vibration-effect>
                """,
                /* useSimpleVibration= */ false);
        when(mockVibratorInfo.areVibrationFeaturesSupported(any())).thenReturn(true);

        createRingerUnderTest();

        assertEquals(
                VibrationEffect
                        .startComposition()
                        .repeatEffectIndefinitely(
                                VibrationEffect
                                        .startComposition()
                                        .addEffect(VibrationEffect.createPredefined(EFFECT_CLICK))
                                        .addOffDuration(Duration.ofSeconds(1))
                                        .compose()
                        )
                        .compose(),
                mRingerUnderTest.mDefaultVibrationEffect);
    }

    @SmallTest
    @Test
    public void testValidButUnsupportedDefaultRingVibrationOverride() throws Exception {
        when(mFeatureFlags.useDeviceProvidedSerializedRingerVibration()).thenReturn(true);
        mockVibrationResourceValues(
                """
                    <vibration-effect>
                        <predefined-effect name="click"/>
                    </vibration-effect>
                """,
                /* useSimpleVibration= */ false);
        when(mockVibratorInfo.areVibrationFeaturesSupported(
                eq(VibrationEffect.createPredefined(EFFECT_CLICK)))).thenReturn(false);

        createRingerUnderTest();

        assertEquals(EXPECTED_SIMPLE_VIBRATION_PATTERN, mRingerUnderTest.mDefaultVibrationEffect);
    }

    @SmallTest
    @Test
    public void testInvalidDefaultRingVibrationOverride() throws Exception {
        when(mFeatureFlags.useDeviceProvidedSerializedRingerVibration()).thenReturn(true);
        mockVibrationResourceValues(
                /* defaultVibrationContent= */ "bad serialization",
                /* useSimpleVibration= */ false);
        when(mockVibratorInfo.areVibrationFeaturesSupported(any())).thenReturn(true);

        createRingerUnderTest();

        assertEquals(EXPECTED_SIMPLE_VIBRATION_PATTERN, mRingerUnderTest.mDefaultVibrationEffect);
    }

    @SmallTest
    @Test
    public void testEmptyDefaultRingVibrationOverride() throws Exception {
        when(mFeatureFlags.useDeviceProvidedSerializedRingerVibration()).thenReturn(true);
        mockVibrationResourceValues(
                /* defaultVibrationContent= */ "", /* useSimpleVibration= */ false);
        when(mockVibratorInfo.areVibrationFeaturesSupported(any())).thenReturn(true);

        createRingerUnderTest();

        assertEquals(EXPECTED_SIMPLE_VIBRATION_PATTERN, mRingerUnderTest.mDefaultVibrationEffect);
    }

    @SmallTest
    @Test
    public void testNoActionInTheaterMode() throws Exception {
        // Start call waiting to make sure that it doesn't stop when we start ringing
        mRingerUnderTest.startCallWaiting(mockCall1);
        when(mockSystemSettingsUtil.isTheaterModeOn(any(Context.class))).thenReturn(true);
        assertFalse(startRingingAndWaitForAsync(mockCall2, false));
        verifyZeroInteractions(mockRingtoneFactory);
        verify(mockTonePlayer, never()).stopTone();
        verify(mockVibrator, never())
                .vibrate(any(VibrationEffect.class), any(VibrationAttributes.class));
    }

    @SmallTest
    @Test
    public void testNoActionWithExternalRinger() throws Exception {
        Bundle externalRingerExtra = new Bundle();
        externalRingerExtra.putBoolean(TelecomManager.EXTRA_CALL_HAS_IN_BAND_RINGTONE, true);
        when(mockCall1.getIntentExtras()).thenReturn(externalRingerExtra);
        when(mockCall2.getIntentExtras()).thenReturn(externalRingerExtra);
        // Start call waiting to make sure that it doesn't stop when we start ringing
        mRingerUnderTest.startCallWaiting(mockCall1);
        assertFalse(startRingingAndWaitForAsync(mockCall2, false));

        verifyZeroInteractions(mockRingtoneFactory);
        verify(mockTonePlayer, never()).stopTone();
        verify(mockVibrator, never())
                .vibrate(any(VibrationEffect.class), any(VibrationAttributes.class));
    }

    @SmallTest
    @Test
    public void testNoActionWhenDialerRings() throws Exception {
        // Start call waiting to make sure that it doesn't stop when we start ringing
        mRingerUnderTest.startCallWaiting(mockCall1);
        when(mockInCallController.doesConnectedDialerSupportRinging(
                any(UserHandle.class))).thenReturn(true);
        ensureRingerIsNotAudible();
        assertFalse(startRingingAndWaitForAsync(mockCall2, false));

        verifyZeroInteractions(mockRingtoneFactory);
        verify(mockTonePlayer, never()).stopTone();
        verify(mockVibrator, never())
                .vibrate(any(VibrationEffect.class), any(AudioAttributes.class));
    }

    @SmallTest
    @Test
    public void testAudioFocusStillAcquiredWhenDialerRings() throws Exception {

        // Start call waiting to make sure that it doesn't stop when we start ringing
        mRingerUnderTest.startCallWaiting(mockCall1);
        when(mockInCallController.doesConnectedDialerSupportRinging(
                any(UserHandle.class))).thenReturn(true);
        ensureRingerIsAudible();
        assertTrue(startRingingAndWaitForAsync(mockCall2, false));
        verifyZeroInteractions(mockRingtoneFactory);
        verify(mockTonePlayer, never()).stopTone();
        verify(mockVibrator, never())
                .vibrate(any(VibrationEffect.class), any(VibrationAttributes.class));
    }

    @SmallTest
    @Test
    public void testNoActionWhenCallIsSelfManaged() throws Exception {
        // Start call waiting to make sure that it doesn't stop when we start ringing
        mRingerUnderTest.startCallWaiting(mockCall1);
        when(mockCall2.isSelfManaged()).thenReturn(true);
        // We do want to acquire audio focus when self-managed
        assertTrue(startRingingAndWaitForAsync(mockCall2, true));

        verifyZeroInteractions(mockRingtoneFactory);
        verify(mockTonePlayer, never()).stopTone();
        verify(mockVibrator, never())
                .vibrate(any(VibrationEffect.class), any(VibrationAttributes.class));
    }

    @SmallTest
    @Test
    public void testCallWaitingButNoRingForSpecificContacts() throws Exception {
        when(mockNotificationManager.matchesCallFilter(any(Uri.class))).thenReturn(false);
        // Start call waiting to make sure that it does stop when we start ringing
        mRingerUnderTest.startCallWaiting(mockCall1);
        verify(mockTonePlayer).startTone();

        assertFalse(startRingingAndWaitForAsync(mockCall2, false));

        verifyZeroInteractions(mockRingtoneFactory);
        verify(mockTonePlayer).stopTone();
        verify(mockVibrator, never())
                .vibrate(any(VibrationEffect.class), any(VibrationAttributes.class));
    }

    @SmallTest
    @Test
    public void testNoVibrateDueToAudioCoupledHaptics() throws Exception {
        Ringtone mockRingtone = ensureRingtoneMocked();

        mRingerUnderTest.startCallWaiting(mockCall1);
        ensureRingerIsAudible();
        enableVibrationWhenRinging();
        // Pretend we're using audio coupled haptics.
        setIsUsingHaptics(mockRingtone, true);
        assertTrue(startRingingAndWaitForAsync(mockCall1, false));
        verify(mockRingtoneFactory, atLeastOnce())
            .getRingtone(any(Call.class), nullable(VolumeShaper.Configuration.class), anyBoolean());
        verifyNoMoreInteractions(mockRingtoneFactory);
        verify(mockTonePlayer).stopTone();
        verify(mockRingtone).play();
        verify(mockVibrator, never()).vibrate(any(VibrationEffect.class),
                any(VibrationAttributes.class));
    }

    @SmallTest
    @Test
    public void testVibrateButNoRingForNullRingtone() throws Exception {
        when(mockRingtoneFactory.getRingtone(
                 any(Call.class), nullable(VolumeShaper.Configuration.class), anyBoolean()))
            .thenReturn(null);

        mRingerUnderTest.startCallWaiting(mockCall1);
        when(mockAudioManager.getRingerMode()).thenReturn(AudioManager.RINGER_MODE_NORMAL);
        enableVibrationWhenRinging();
        // The ringtone isn't known to be null until the async portion after the call completes,
        // so startRinging still returns true here as there should nominally be a ringtone.
        // Notably, vibration still happens in this scenario.
        assertTrue(startRingingAndWaitForAsync(mockCall2, false));
        verify(mockTonePlayer).stopTone();

        // Just the one call to mockRingtoneFactory, which returned null.
        verify(mockRingtoneFactory).getRingtone(
                any(Call.class), nullable(VolumeShaper.Configuration.class), anyBoolean());
        verifyNoMoreInteractions(mockRingtoneFactory);

        // Play default vibration when future completes with no audio coupled haptics
        verify(mockVibrator).vibrate(eq(mRingerUnderTest.mDefaultVibrationEffect),
                any(VibrationAttributes.class));
    }

    @SmallTest
    @Test
    public void testVibrateButNoRingForSilentRingtone() throws Exception {
        Ringtone mockRingtone = ensureRingtoneMocked();

        mRingerUnderTest.startCallWaiting(mockCall1);
        when(mockRingtoneFactory.getRingtone(any(Call.class), eq(null), anyBoolean()))
            .thenReturn(new Pair(FAKE_RINGTONE_URI, mockRingtone));
        when(mockAudioManager.getRingerMode()).thenReturn(AudioManager.RINGER_MODE_VIBRATE);
        when(mockAudioManager.getStreamVolume(AudioManager.STREAM_RING)).thenReturn(0);
        enableVibrationWhenRinging();
        assertFalse(startRingingAndWaitForAsync(mockCall2, false));
        verify(mockTonePlayer).stopTone();
        // Try to play a silent haptics ringtone
        verify(mockRingtoneFactory, atLeastOnce()).getHapticOnlyRingtone();
        verifyNoMoreInteractions(mockRingtoneFactory);
        verify(mockRingtone).play();

        // Play default vibration when future completes with no audio coupled haptics
        verify(mockVibrator).vibrate(eq(mRingerUnderTest.mDefaultVibrationEffect),
                any(VibrationAttributes.class));
    }

    @SmallTest
    @Test
    public void testVibrateButNoRingForSilentRingtoneWithoutAudioHapticSupport() throws Exception {
        mIsHapticPlaybackSupported = false;
        createRingerUnderTest();  // Needed after changing haptic playback support.
        mRingerUnderTest.startCallWaiting(mockCall1);
        when(mockAudioManager.getRingerMode()).thenReturn(AudioManager.RINGER_MODE_VIBRATE);
        when(mockAudioManager.getStreamVolume(AudioManager.STREAM_RING)).thenReturn(0);
        enableVibrationWhenRinging();
        assertFalse(startRingingAndWaitForAsync(mockCall2, false));
        verify(mockTonePlayer).stopTone();
        verifyZeroInteractions(mockRingtoneFactory);

        // Play default vibration when future completes with no audio coupled haptics
        verify(mockVibrator).vibrate(eq(mRingerUnderTest.mDefaultVibrationEffect),
                any(VibrationAttributes.class));
    }

    @SmallTest
    @Test
    public void testAudioCoupledHapticsForSilentRingtone() throws Exception {
        Ringtone mockRingtone = ensureRingtoneMocked();

        mRingerUnderTest.startCallWaiting(mockCall1);
        when(mockAudioManager.getRingerMode()).thenReturn(AudioManager.RINGER_MODE_VIBRATE);
        when(mockAudioManager.getStreamVolume(AudioManager.STREAM_RING)).thenReturn(0);
        setIsUsingHaptics(mockRingtone, true);
        enableVibrationWhenRinging();
        assertFalse(startRingingAndWaitForAsync(mockCall2, false));

        verify(mockRingtoneFactory, atLeastOnce()).getHapticOnlyRingtone();
        verifyNoMoreInteractions(mockRingtoneFactory);
        verify(mockTonePlayer).stopTone();
        // Try to play a silent haptics ringtone
        verify(mockRingtone).play();
        // Skip vibration for audio coupled haptics
        verify(mockVibrator, never()).vibrate(any(VibrationEffect.class),
                any(VibrationAttributes.class));
    }

    @SmallTest
    @Test
    public void testCustomVibrationForRingtone() throws Exception {
        mRingerUnderTest.startCallWaiting(mockCall1);
        Ringtone mockRingtone = ensureRingtoneMocked();
        when(mockAudioManager.getRingerMode()).thenReturn(AudioManager.RINGER_MODE_NORMAL);
        when(mockRingtone.getUri()).thenReturn(FAKE_RINGTONE_URI);
        enableVibrationWhenRinging();
        assertTrue(startRingingAndWaitForAsync(mockCall2, false));
        verify(mockTonePlayer).stopTone();
        verify(mockRingtoneFactory, atLeastOnce())
            .getRingtone(any(Call.class), isNull(), anyBoolean());
        verifyNoMoreInteractions(mockRingtoneFactory);
        verify(mockRingtone).play();
        verify(spyVibrationEffectProxy).get(eq(FAKE_RINGTONE_URI), any(Context.class));
        verify(mockVibrator).vibrate(eq(URI_VIBRATION_EFFECT), any(VibrationAttributes.class));
    }

    @SmallTest
    @Test
    public void testRingAndNoVibrate() throws Exception {
        Ringtone mockRingtone = ensureRingtoneMocked();

        mRingerUnderTest.startCallWaiting(mockCall1);
        ensureRingerIsAudible();
        enableVibrationOnlyWhenNotRinging();
        assertTrue(startRingingAndWaitForAsync(mockCall2, false));
        verify(mockRingtoneFactory, atLeastOnce())
            .getRingtone(any(Call.class), nullable(VolumeShaper.Configuration.class), anyBoolean());
        verifyNoMoreInteractions(mockRingtoneFactory);
        verify(mockTonePlayer).stopTone();
        verify(mockRingtone).play();
        verify(mockVibrator, never())
                .vibrate(any(VibrationEffect.class), any(VibrationAttributes.class));
    }

    @SmallTest
    @Test
    public void testRingWithRampingRinger() throws Exception {
        Ringtone mockRingtone = ensureRingtoneMocked();

        mRingerUnderTest.startCallWaiting(mockCall1);
        ensureRingerIsAudible();
        enableRampingRinger();
        enableVibrationWhenRinging();
        assertTrue(startRingingAndWaitForAsync(mockCall2, false));
        verify(mockRingtoneFactory, atLeastOnce())
            .getRingtone(any(Call.class), nullable(VolumeShaper.Configuration.class), anyBoolean());
        verifyNoMoreInteractions(mockRingtoneFactory);
        verify(mockTonePlayer).stopTone();
        verify(mockRingtone).play();
    }

    @SmallTest
    @Test
    public void testSilentRingWithHfpStillAcquiresFocus() throws Exception {
        mRingerUnderTest.startCallWaiting(mockCall1);
        when(mockAudioManager.getRingerMode()).thenReturn(AudioManager.RINGER_MODE_NORMAL);
        when(mockAudioManager.getStreamVolume(AudioManager.STREAM_RING)).thenReturn(0);
        enableVibrationOnlyWhenNotRinging();
        assertTrue(startRingingAndWaitForAsync(mockCall2, true));
        verify(mockTonePlayer).stopTone();
        // Ringer not audible, so never tries to create a ringtone.
        verifyZeroInteractions(mockRingtoneFactory);
        verify(mockVibrator, never())
                .vibrate(any(VibrationEffect.class), any(VibrationAttributes.class));
    }

    @SmallTest
    @Test
    public void testRingAndVibrateForAllowedCallInDndMode() throws Exception {
        mRingerUnderTest.startCallWaiting(mockCall1);
        Ringtone mockRingtone = ensureRingtoneMocked();
        when(mockNotificationManager.getZenMode()).thenReturn(ZEN_MODE_IMPORTANT_INTERRUPTIONS);
        when(mockAudioManager.getRingerMode()).thenReturn(AudioManager.RINGER_MODE_SILENT);
        when(mockAudioManager.getStreamVolume(AudioManager.STREAM_RING)).thenReturn(100);
        enableVibrationWhenRinging();
        assertTrue(startRingingAndWaitForAsync(mockCall2, true));
        verify(mockRingtoneFactory, atLeastOnce())
            .getRingtone(any(Call.class), isNull(), anyBoolean());
        verifyNoMoreInteractions(mockRingtoneFactory);
        verify(mockTonePlayer).stopTone();
        verify(mockRingtone).play();
    }

    @SmallTest
    @Test
    public void testDelayRingerForBtHfpDevices() throws Exception {
        asyncRingtonePlayer.updateBtActiveState(false);
        Ringtone mockRingtone = ensureRingtoneMocked();

        ensureRingerIsAudible();
        assertTrue(mRingerUnderTest.startRinging(mockCall1, true));
        assertTrue(mRingerUnderTest.isRinging());
        // We should not have the ringtone play until BT moves active
        verify(mockRingtone, never()).play();

        asyncRingtonePlayer.updateBtActiveState(true);
        mRingCompletionFuture.get();
        verify(mockRingtoneFactory, atLeastOnce())
                .getRingtone(any(Call.class), nullable(VolumeShaper.Configuration.class),
                        anyBoolean());
        verifyNoMoreInteractions(mockRingtoneFactory);
        verify(mockRingtone).play();

        mRingerUnderTest.stopRinging();
        verify(mockRingtone, timeout(1000/*ms*/)).stop();
        assertFalse(mRingerUnderTest.isRinging());
    }

    @SmallTest
    @Test
    public void testUnblockRingerForStopCommand() throws Exception {
        asyncRingtonePlayer.updateBtActiveState(false);
        Ringtone mockRingtone = ensureRingtoneMocked();

        ensureRingerIsAudible();
        assertTrue(mRingerUnderTest.startRinging(mockCall1, true));
        // We should not have the ringtone play until BT moves active
        verify(mockRingtone, never()).play();

        // We are not setting BT active, but calling stop ringing while the other thread is waiting
        // for BT active should also unblock it.
        mRingerUnderTest.stopRinging();
        verify(mockRingtone, timeout(1000/*ms*/)).stop();
    }

    /**
     * test shouldRingForContact will suppress the incoming call if matchesCallFilter returns
     * false (meaning DND is ON and the caller cannot bypass the settings)
     */
    @Test
    public void testShouldRingForContact_CallSuppressed() {
        // WHEN
        when(mockCall1.wasDndCheckComputedForCall()).thenReturn(false);
        when(mockCall1.getHandle()).thenReturn(Uri.parse(""));
        when(mContext.getSystemService(NotificationManager.class)).thenReturn(
                mockNotificationManager);
        // suppress the call
        when(mockNotificationManager.matchesCallFilter(any(Uri.class))).thenReturn(false);

        // run the method under test
        assertFalse(mRingerUnderTest.shouldRingForContact(mockCall1));

        // THEN
        // verify we never set the call object and matchesCallFilter is called
        verify(mockCall1, never()).setCallIsSuppressedByDoNotDisturb(true);
        verify(mockNotificationManager, times(1))
                .matchesCallFilter(any(Uri.class));
    }

    /**
     * test shouldRingForContact will alert the user of an incoming call if matchesCallFilter
     * returns true (meaning DND is NOT suppressing the caller)
     */
    @Test
    public void testShouldRingForContact_CallShouldRing() {
        // WHEN
        when(mockCall1.wasDndCheckComputedForCall()).thenReturn(false);
        when(mockCall1.getHandle()).thenReturn(Uri.parse(""));
        // alert the user of the call

        // run the method under test
        assertTrue(mRingerUnderTest.shouldRingForContact(mockCall1));

        // THEN
        // verify we never set the call object and matchesCallFilter is called
        verify(mockCall1, never()).setCallIsSuppressedByDoNotDisturb(false);
        verify(mockNotificationManager, times(1))
                .matchesCallFilter(any(Uri.class));
    }

    /**
     * ensure Telecom does not re-query the NotificationManager if the call object already has
     * the result.
     */
    @Test
    public void testShouldRingForContact_matchesCallFilterIsAlreadyComputed() {
        // WHEN
        when(mockCall1.wasDndCheckComputedForCall()).thenReturn(true);
        when(mockCall1.isCallSuppressedByDoNotDisturb()).thenReturn(true);

        // THEN
        assertFalse(mRingerUnderTest.shouldRingForContact(mockCall1));
        verify(mockCall1, never()).setCallIsSuppressedByDoNotDisturb(false);
        verify(mockNotificationManager, never()).matchesCallFilter(any(Uri.class));
    }

    @Test
    public void testNoFlashNotificationWhenCallSuppressed() throws Exception {
        ensureRingtoneMocked();
        // Start call waiting to make sure that it doesn't stop when we start ringing
        mRingerUnderTest.startCallWaiting(mockCall1);
        when(mockCall2.wasDndCheckComputedForCall()).thenReturn(false);
        when(mockCall2.getHandle()).thenReturn(Uri.parse(""));
        when(mockNotificationManager.matchesCallFilter(any(Uri.class))).thenReturn(false);

        assertFalse(mRingerUnderTest.shouldRingForContact(mockCall2));
        assertFalse(startRingingAndWaitForAsync(mockCall2, false));
        verify(mockAccessibilityManagerAdapter, never())
                .startFlashNotificationSequence(any(Context.class), anyInt());
    }

    @Test
    public void testStartFlashNotificationWhenRingStarts() throws Exception {
        ensureRingtoneMocked();
        // Start call waiting to make sure that it doesn't stop when we start ringing
        mRingerUnderTest.startCallWaiting(mockCall1);
        when(mockCall2.wasDndCheckComputedForCall()).thenReturn(false);
        when(mockCall2.getHandle()).thenReturn(Uri.parse(""));

        assertTrue(mRingerUnderTest.shouldRingForContact(mockCall2));
        assertTrue(startRingingAndWaitForAsync(mockCall2, false));
        verify(mockAccessibilityManagerAdapter, atLeastOnce())
                .startFlashNotificationSequence(any(Context.class), anyInt());
    }

    @Test
    public void testStopFlashNotificationWhenRingStops() throws Exception {
        Ringtone mockRingtone = mock(Ringtone.class);
        when(mockRingtoneFactory.getRingtone(
                any(Call.class), nullable(VolumeShaper.Configuration.class), anyBoolean()))
                .thenAnswer(x -> {
                    // Be slow to create ringtone.
                    try {
                        Thread.sleep(300);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return new Pair(FAKE_RINGTONE_URI, mockRingtone);
                });
        // Start call waiting to make sure that it doesn't stop when we start ringing
        enableVibrationWhenRinging();
        mRingerUnderTest.startCallWaiting(mockCall1);
        when(mockCall2.wasDndCheckComputedForCall()).thenReturn(false);
        when(mockCall2.getHandle()).thenReturn(Uri.parse(""));

        assertTrue(mRingerUnderTest.shouldRingForContact(mockCall2));
        assertTrue(mRingerUnderTest.startRinging(mockCall2, false));
        mRingerUnderTest.stopRinging();
        verify(mockAccessibilityManagerAdapter, atLeastOnce())
                .stopFlashNotificationSequence(any(Context.class));
        mRingCompletionFuture.get();  // Don't leak async work.
        verify(mockVibrator, never())  // cancelled before it started.
                .vibrate(any(VibrationEffect.class), any(VibrationAttributes.class));
    }

    @SmallTest
    @Test
    public void testNoRingingForQuietProfile() throws Exception {
        UserManager um = mContext.getSystemService(UserManager.class);
        when(um.isManagedProfile(PA_HANDLE.getUserHandle().getIdentifier())).thenReturn(true);
        when(um.isQuietModeEnabled(PA_HANDLE.getUserHandle())).thenReturn(true);
        // We don't want to acquire audio focus when self-managed
        assertFalse(startRingingAndWaitForAsync(mockCall2, true));

        verify(mockTonePlayer, never()).stopTone();
        verifyZeroInteractions(mockRingtoneFactory);
        verify(mockVibrator, never())
                .vibrate(any(VibrationEffect.class), any(VibrationAttributes.class));
    }

    /**
     * Call startRinging and wait for its effects to have played out, to allow reliable assertions
     * after it. The effects are generally "start playing ringtone" and "start vibration" - not
     * waiting for anything open-ended.
     */
    private boolean startRingingAndWaitForAsync(Call mockCall2, boolean isHfpDeviceAttached)
            throws Exception {
        boolean result = mRingerUnderTest.startRinging(mockCall2, isHfpDeviceAttached);
        mRingCompletionFuture.get();
        return result;
    }

    private void ensureRingerIsAudible() {
        when(mockAudioManager.getRingerMode()).thenReturn(AudioManager.RINGER_MODE_NORMAL);
        when(mockAudioManager.getStreamVolume(AudioManager.STREAM_RING)).thenReturn(100);
    }

    private void ensureRingerIsNotAudible() {
        when(mockAudioManager.getRingerMode()).thenReturn(AudioManager.RINGER_MODE_NORMAL);
        when(mockAudioManager.getStreamVolume(AudioManager.STREAM_RING)).thenReturn(0);
    }

    private void enableVibrationWhenRinging() {
        when(mockVibrator.hasVibrator()).thenReturn(true);
        when(mockSystemSettingsUtil.isRingVibrationEnabled(any(Context.class))).thenReturn(true);
    }

    private void enableVibrationOnlyWhenNotRinging() {
        when(mockVibrator.hasVibrator()).thenReturn(true);
        when(mockSystemSettingsUtil.isRingVibrationEnabled(any(Context.class))).thenReturn(false);
    }

    private void enableRampingRinger() {
        when(mockSystemSettingsUtil.isRampingRingerEnabled(any(Context.class))).thenReturn(true);
    }

    private void setIsUsingHaptics(Ringtone mockRingtone, boolean useHaptics) {
        // Note: using haptics can also depend on mIsHapticPlaybackSupported. If changing
        // that, the ringerUnderTest needs to be re-created.
        when(mockSystemSettingsUtil.isAudioCoupledVibrationForRampingRingerEnabled())
            .thenReturn(useHaptics);
        when(mockRingtone.hasHapticChannels()).thenReturn(useHaptics);
    }

    private Ringtone ensureRingtoneMocked() {
        Ringtone mockRingtone = mock(Ringtone.class);
        Pair<Uri, Ringtone> ringtoneInfo = new Pair(
                FAKE_RINGTONE_URI, mockRingtone);
        when(mockRingtoneFactory.getRingtone(
                any(Call.class), nullable(VolumeShaper.Configuration.class), anyBoolean()))
                .thenReturn(ringtoneInfo);
        when(mockRingtoneFactory.getHapticOnlyRingtone()).thenReturn(ringtoneInfo);
        return mockRingtone;
    }

    private void mockVibrationResourceValues(
            String defaultVibrationContent, boolean useSimpleVibration) {
        mComponentContextFixture.putRawResource(
                com.android.internal.R.raw.default_ringtone_vibration_effect,
                defaultVibrationContent);
        mComponentContextFixture.putBooleanResource(
                R.bool.use_simple_vibration_pattern, useSimpleVibration);
    }
}
