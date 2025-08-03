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

package android.media.audio.cts;

import static android.media.AudioManager.ADJUST_LOWER;
import static android.media.AudioManager.ADJUST_RAISE;
import static android.media.AudioManager.ADJUST_SAME;
import static android.media.AudioManager.MODE_IN_CALL;
import static android.media.AudioManager.MODE_IN_COMMUNICATION;
import static android.media.AudioManager.MODE_NORMAL;
import static android.media.AudioManager.MODE_RINGTONE;
import static android.media.AudioManager.RINGER_MODE_NORMAL;
import static android.media.AudioManager.RINGER_MODE_SILENT;
import static android.media.AudioManager.RINGER_MODE_VIBRATE;
import static android.media.AudioManager.STREAM_ACCESSIBILITY;
import static android.media.AudioManager.STREAM_ALARM;
import static android.media.AudioManager.STREAM_DTMF;
import static android.media.AudioManager.STREAM_MUSIC;
import static android.media.AudioManager.STREAM_NOTIFICATION;
import static android.media.AudioManager.STREAM_RING;
import static android.media.AudioManager.STREAM_SYSTEM;
import static android.media.AudioManager.STREAM_VOICE_CALL;
import static android.media.AudioManager.VIBRATE_SETTING_OFF;
import static android.media.AudioManager.VIBRATE_SETTING_ON;
import static android.media.AudioManager.VIBRATE_SETTING_ONLY_SILENT;
import static android.media.AudioManager.VIBRATE_TYPE_NOTIFICATION;
import static android.media.AudioManager.VIBRATE_TYPE_RINGER;
import static android.media.audio.Flags.autoPublicVolumeApiHardening;
import static android.media.audio.cts.AudioTestUtil.resetVolumeIndex;
import static android.provider.Settings.Global.APPLY_RAMPING_RINGER;
import static android.provider.Settings.System.SOUND_EFFECTS_ENABLED;

import static com.android.media.mediatestutils.TestUtils.getFutureForIntent;
import static com.android.media.mediatestutils.TestUtils.getFutureForListener;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.app.AutomaticZenRule;
import android.app.Instrumentation;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.media.AudioAttributes;
import android.media.AudioDescriptor;
import android.media.AudioDeviceAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioHalVersionInfo;
import android.media.AudioManager;
import android.media.AudioMixerAttributes;
import android.media.AudioProfile;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.MicrophoneInfo;
import android.media.audio.Flags;
import android.media.audiopolicy.AudioProductStrategy;
import android.media.audiopolicy.AudioVolumeGroup;
import android.media.cts.Utils;
import android.os.Build;
import android.os.SystemClock;
import android.os.Vibrator;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.provider.Settings;
import android.provider.Settings.System;
import android.util.Log;
import android.view.SoundEffectConstants;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.AmUtils;
import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.MediaUtils;
import com.android.compatibility.common.util.NonMainlineTest;
import com.android.compatibility.common.util.SettingsStateKeeperRule;
import com.android.compatibility.common.util.SystemUtil;
import com.android.compatibility.common.util.UserSettings.Namespace;
import com.android.internal.annotations.GuardedBy;
import com.android.media.mediatestutils.CancelAllFuturesRule;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@NonMainlineTest
@AppModeFull(reason = "Waiting for volume/zen mode changes requires receiving intents. " +
        "Several API calls require MODIFY_AUDIO_SETTINGS.")
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
@RunWith(AndroidJUnit4.class)
public class AudioManagerTest {
    private static final String TAG = "AudioManagerTest";

    private static final int INIT_VOL = 1;
    private static final int MP3_TO_PLAY = R.raw.testmp3; // ~ 5 second mp3
    private static final long POLL_TIME_PLAY_MUSIC = 2000;
    private static final long TIME_TO_PLAY = 2000;
    private static final long TIME_TO_WAIT_CALLBACK_MS = 1000;
    private static final String APPOPS_OP_STR = "android:write_settings";
    private static final Set<Integer> ALL_KNOWN_ENCAPSULATION_TYPES = Set.of(
            AudioProfile.AUDIO_ENCAPSULATION_TYPE_IEC61937,
            AudioProfile.AUDIO_ENCAPSULATION_TYPE_PCM);
    private static final Set<Integer> ALL_ENCAPSULATION_TYPES = Set.of(
            AudioProfile.AUDIO_ENCAPSULATION_TYPE_NONE,
            AudioProfile.AUDIO_ENCAPSULATION_TYPE_IEC61937,
            AudioProfile.AUDIO_ENCAPSULATION_TYPE_PCM);
    private static final Set<Integer> ALL_AUDIO_STANDARDS = Set.of(
            AudioDescriptor.STANDARD_NONE,
            AudioDescriptor.STANDARD_EDID,
            AudioDescriptor.STANDARD_SADB,
            AudioDescriptor.STANDARD_VSADB);
    private static final Map<Integer, Integer> DIRECT_OFFLOAD_MAP = Map.of(
            AudioManager.PLAYBACK_OFFLOAD_NOT_SUPPORTED,
                AudioManager.DIRECT_PLAYBACK_NOT_SUPPORTED,
            AudioManager.PLAYBACK_OFFLOAD_SUPPORTED,
                AudioManager.DIRECT_PLAYBACK_OFFLOAD_SUPPORTED,
            AudioManager.PLAYBACK_OFFLOAD_GAPLESS_SUPPORTED,
                AudioManager.DIRECT_PLAYBACK_OFFLOAD_GAPLESS_SUPPORTED);
    private static final Set<Integer> ALL_MIXER_BEHAVIORS = Set.of(
            AudioMixerAttributes.MIXER_BEHAVIOR_DEFAULT,
            AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT);
    private static final int[] PUBLIC_STREAM_TYPES = { STREAM_VOICE_CALL,
            STREAM_SYSTEM, STREAM_RING, STREAM_MUSIC,
            STREAM_ALARM, STREAM_NOTIFICATION,
            STREAM_DTMF,  STREAM_ACCESSIBILITY };

    private static final int FUTURE_WAIT_SECS = 5; // Should never timeout; early fail
    // How long to wait to verify that something that shouldn't happen doesn't happen
    private static final int PROVE_NEGATIVE_DURATION_MS = 300;

    private static final int INVALID_DIRECT_PLAYBACK_MODE = -1;
    private AudioManager mAudioManager;
    private NotificationManager mNm;
    private boolean mHasVibrator;
    private boolean mUseFixedVolume;
    private boolean mIsTelevision;
    private boolean mIsSingleVolume;
    private boolean mSkipRingerTests;
    private boolean mSkipAutoVolumeTests = false;
    // From N onwards, ringer mode adjustments that toggle DND are not allowed unless
    // package has DND access. Many tests in this package toggle DND access in order
    // to get device out of the DND state for the test to proceed correctly.
    // But DND access is disabled completely on low ram devices,
    // so completely skip those tests here.
    // These tests are migrated to CTS verifier tests to ensure test coverage.
    private Context mContext;
    private int mOriginalRingerMode;
    private Map<Integer, Integer> mOriginalStreamVolumes = new HashMap<>();
    private NotificationManager.Policy mOriginalNotificationPolicy;
    private int mOriginalZen;
    private boolean mDoNotCheckUnmute;
    private boolean mAppsBypassingDnd;

    @ClassRule
    public static final SettingsStateKeeperRule mSurroundSoundFormatsSettingsKeeper =
            new SettingsStateKeeperRule(InstrumentationRegistry.getTargetContext(),
                    Namespace.GLOBAL, Settings.Global.ENCODED_SURROUND_OUTPUT_ENABLED_FORMATS);

    @ClassRule
    public static final SettingsStateKeeperRule mSurroundSoundModeSettingsKeeper =
            new SettingsStateKeeperRule(InstrumentationRegistry.getTargetContext(),
                    Namespace.GLOBAL, Settings.Global.ENCODED_SURROUND_OUTPUT);

    @Rule
    public final CancelAllFuturesRule mCancelRule = new CancelAllFuturesRule();

    private static Instrumentation getInstrumentation() {
        return InstrumentationRegistry.getInstrumentation();
    }

    @Before
    public void setUp() throws Exception {
        mContext = getInstrumentation().getContext();
        Utils.enableAppOps(mContext.getPackageName(), APPOPS_OP_STR, getInstrumentation());
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        Vibrator vibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
        mNm = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        mAppsBypassingDnd = NotificationManager.getService().areChannelsBypassingDnd();
        mHasVibrator = (vibrator != null) && vibrator.hasVibrator();
        mUseFixedVolume = mContext.getResources().getBoolean(
                Resources.getSystem().getIdentifier("config_useFixedVolume", "bool", "android"));
        PackageManager packageManager = mContext.getPackageManager();
        mIsTelevision = packageManager != null
                && (packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
                        || packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION));
        mIsSingleVolume = mContext.getResources().getBoolean(
                Resources.getSystem().getIdentifier("config_single_volume", "bool", "android"));
        mSkipRingerTests = mUseFixedVolume || mIsTelevision || mIsSingleVolume;
        if (mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
                && autoPublicVolumeApiHardening()) {
            // setRingerMode is a no-op
            mSkipRingerTests = true;
            // volume SDK APIs are no-ops
            mSkipAutoVolumeTests = true;
        }

        // TODO (b/294941969) pull out volume/ringer/zen state setting/resetting into test rule
        // Store the original volumes that that they can be recovered in tearDown().
        final int[] streamTypes = {
            STREAM_VOICE_CALL,
            STREAM_SYSTEM,
            STREAM_RING,
            STREAM_MUSIC,
            STREAM_ALARM,
            STREAM_NOTIFICATION,
            STREAM_DTMF,
            STREAM_ACCESSIBILITY,
        };
        mOriginalRingerMode = mAudioManager.getRingerMode();
        for (int streamType : streamTypes) {
            mOriginalStreamVolumes.put(streamType, mAudioManager.getStreamVolume(streamType));
        }

        // Tests require the known state of volumes set to INIT_VOL and zen mode
        // turned off.
        try {
            Utils.toggleNotificationPolicyAccess(
                    mContext.getPackageName(), getInstrumentation(), true);

            SystemUtil.runWithShellPermissionIdentity(
                    () -> {
                        mOriginalNotificationPolicy = mNm.getNotificationPolicy();
                        mOriginalZen = mNm.getCurrentInterruptionFilter();
                        mAudioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                        setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL);
                    },
                    Manifest.permission.STATUS_BAR_SERVICE);
        } finally {
            Utils.toggleNotificationPolicyAccess(
                    mContext.getPackageName(), getInstrumentation(), false);
        }

        for (int streamType : streamTypes) {
            mAudioManager.setStreamVolume(streamType, INIT_VOL, 0 /* flags */);
        }

        // Check original microphone mute/unmute status
        mDoNotCheckUnmute = false;
        if (mAudioManager.isMicrophoneMute()) {
            mAudioManager.setMicrophoneMute(false);
            if (mAudioManager.isMicrophoneMute()) {
                Log.w(TAG, "Mic seems muted by hardware! Please unmute and rerrun the test.");
                mDoNotCheckUnmute = true;
            }
        }
        // Reduce flake due to late intent delivery
        AmUtils.waitForBroadcastIdle();
    }

    @After
    public void tearDown() throws Exception {
        try {
            Utils.toggleNotificationPolicyAccess(
                    mContext.getPackageName(), getInstrumentation(), true);
            mAudioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);

            SystemUtil.runWithShellPermissionIdentity(
                    () -> {
                        mNm.setNotificationPolicy(mOriginalNotificationPolicy);
                        setInterruptionFilter(mOriginalZen);
                    },
                    Manifest.permission.STATUS_BAR_SERVICE);

            Map<String, AutomaticZenRule> rules = mNm.getAutomaticZenRules();
            for (String ruleId : rules.keySet()) {
                mNm.removeAutomaticZenRule(ruleId);
            }

            // Recover the volume and the ringer mode that the test may have overwritten.
            for (Map.Entry<Integer, Integer> e : mOriginalStreamVolumes.entrySet()) {
                mAudioManager.setStreamVolume(e.getKey(), e.getValue(),
                                              AudioManager.FLAG_ALLOW_RINGER_MODES);
            }
            mAudioManager.setRingerMode(mOriginalRingerMode);
        } finally {
            Utils.toggleNotificationPolicyAccess(
                    mContext.getPackageName(), getInstrumentation(), false);
        }
    }

    @AppModeFull(reason = "Instant apps cannot hold android.permission.MODIFY_AUDIO_SETTINGS")
    @Test
    public void testMicrophoneMute() throws Exception {
        mAudioManager.setMicrophoneMute(true);
        assertTrue(mAudioManager.isMicrophoneMute());
        mAudioManager.setMicrophoneMute(false);
        assertFalse(mAudioManager.isMicrophoneMute() && !mDoNotCheckUnmute);
    }

    @AppModeFull(reason = "Instant apps cannot hold android.permission.MODIFY_AUDIO_SETTINGS")
    @Test
    public void testMicrophoneMuteIntent() throws Exception {
        assumeFalse(mDoNotCheckUnmute);

        final boolean initialMicMute = mAudioManager.isMicrophoneMute();
        var future = mCancelRule.registerFuture(getFutureForIntent(
                mContext,
                AudioManager.ACTION_MICROPHONE_MUTE_CHANGED,
                i -> true));
        try {
            // change the mic mute state
            mAudioManager.setMicrophoneMute(!initialMicMute);
            // verify a change was reported
            future.get(FUTURE_WAIT_SECS, TimeUnit.SECONDS);
            // verify the mic mute state is expected
            assertWithMessage("New mic mute should be changed after intent")
                    .that(mAudioManager.isMicrophoneMute())
                    .isNotEqualTo(initialMicMute);
        } finally {
            mAudioManager.setMicrophoneMute(initialMicMute);
        }
    }

    @AppModeFull(reason = "Instant apps cannot hold android.permission.MODIFY_AUDIO_SETTINGS")
    @Test
    public void testSpeakerphoneIntent() throws Exception {
        //  Speaker Phone Not supported in Automotive
        assumeFalse(mContext.getPackageManager().hasSystemFeature(
                    PackageManager.FEATURE_AUTOMOTIVE));

        assumeTrue(hasBuiltinSpeaker());

        var future = mCancelRule.registerFuture(getFutureForIntent(
                    mContext,
                    AudioManager.ACTION_SPEAKERPHONE_STATE_CHANGED,
                    i -> true));

        final boolean initialSpeakerphoneState = mAudioManager.isSpeakerphoneOn();
        try {
            getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(
                    Manifest.permission.MODIFY_PHONE_STATE);

            // change the speakerphone state
            mAudioManager.setSpeakerphoneOn(!initialSpeakerphoneState);
            future.get(FUTURE_WAIT_SECS, TimeUnit.SECONDS);

            // verify the speakerphone state is expected
            assertWithMessage("New speakerphone state should be changed after intent")
                    .that(mAudioManager.isSpeakerphoneOn())
                    .isNotEqualTo(initialSpeakerphoneState);
        } finally {
            mAudioManager.setSpeakerphoneOn(initialSpeakerphoneState);
            getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
        }
    }

    private boolean hasBuiltinSpeaker() {
        AudioDeviceInfo[] devices = mAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        for (AudioDeviceInfo device : devices) {
            final int type = device.getType();
            if (type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    || type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE) {
                return true;
            }
        }
        return false;
    }

    @AppModeFull(
            reason =
                    "ACTION_VOLUME_CHANGED is not sent to Instant apps (no"
                        + " FLAG_RECEIVER_VISIBLE_TO_INSTANT_APPS)")
    @Test
    public void testVolumeChangedIntent() throws Exception {
        if (mAudioManager.isVolumeFixed()) {
            return;
        }
        if (mSkipAutoVolumeTests) {
            // setStreamVolume is a no-op
            return;
        }
        // safe media can block the raising the volume, disable it
        getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.STATUS_BAR_SERVICE);
        mAudioManager.disableSafeMediaVolume();
        getInstrumentation().getUiAutomation().dropShellPermissionIdentity();

        var future = mCancelRule.registerFuture(getFutureForIntent(
                    mContext,
                    AudioManager.ACTION_VOLUME_CHANGED,
                    i -> (i != null)
                        && (i.getIntExtra(AudioManager.EXTRA_VOLUME_STREAM_TYPE,
                                Integer.MIN_VALUE) == STREAM_MUSIC)));

        int mediaVol = mAudioManager.getStreamVolume(STREAM_MUSIC);
        final int origVol = mediaVol;
        final int maxMediaVol = mAudioManager.getStreamMaxVolume(STREAM_MUSIC);
        // change media volume from current value
        mAudioManager.setStreamVolume(STREAM_MUSIC,
                mediaVol == maxMediaVol ? --mediaVol : ++mediaVol,
                0 /*flags*/);
        // verify a change was reported
        final Intent intent = future.get(FUTURE_WAIT_SECS, TimeUnit.SECONDS);

        assertWithMessage("Not an intent for STREAM_MUSIC")
                .that(intent.getIntExtra(AudioManager.EXTRA_VOLUME_STREAM_TYPE, -1))
                .isEqualTo(STREAM_MUSIC);
        assertWithMessage("New STREAM_MUSIC volume not as expected")
                .that(intent.getIntExtra(AudioManager.EXTRA_VOLUME_STREAM_VALUE, -1))
                .isEqualTo(mediaVol);
        assertWithMessage("Previous STREAM_MUSIC volume not as expected")
                .that(intent.getIntExtra(AudioManager.EXTRA_PREV_VOLUME_STREAM_VALUE, -1))
                .isEqualTo(origVol);
    }

    private static final class MyBlockingRunnableListener {
        private final SafeWaitObject mLock = new SafeWaitObject();
        @GuardedBy("mLock")
        private boolean mEventReceived = false;

        public void onSomeEventThatsExpected() {
            synchronized (mLock) {
                mEventReceived = true;
                mLock.notify();
            }
        }

        public boolean waitForExpectedEvent(long timeOutMs) {
            synchronized (mLock) {
                return mLock.waitFor(timeOutMs, () -> mEventReceived);
            }
        }
    }

    @Test
    public void testSoundEffects() throws Exception {
        Settings.System.putInt(mContext.getContentResolver(), SOUND_EFFECTS_ENABLED, 1);

        // should hear sound after loadSoundEffects() called.
        mAudioManager.loadSoundEffects();
        Thread.sleep(TIME_TO_PLAY);
        float volume = 0.5f;  // volume should be between 0.f to 1.f (or -1).
        mAudioManager.playSoundEffect(SoundEffectConstants.CLICK);
        mAudioManager.playSoundEffect(AudioManager.FX_FOCUS_NAVIGATION_UP);
        mAudioManager.playSoundEffect(AudioManager.FX_FOCUS_NAVIGATION_DOWN);
        mAudioManager.playSoundEffect(AudioManager.FX_FOCUS_NAVIGATION_LEFT);
        mAudioManager.playSoundEffect(AudioManager.FX_FOCUS_NAVIGATION_RIGHT);

        mAudioManager.playSoundEffect(AudioManager.FX_FOCUS_NAVIGATION_UP, volume);
        mAudioManager.playSoundEffect(AudioManager.FX_FOCUS_NAVIGATION_DOWN, volume);
        mAudioManager.playSoundEffect(AudioManager.FX_FOCUS_NAVIGATION_LEFT, volume);
        mAudioManager.playSoundEffect(AudioManager.FX_FOCUS_NAVIGATION_RIGHT, volume);

        // won't hear sound after unloadSoundEffects() called();
        mAudioManager.unloadSoundEffects();
        mAudioManager.playSoundEffect(AudioManager.FX_KEY_CLICK);
        mAudioManager.playSoundEffect(AudioManager.FX_FOCUS_NAVIGATION_UP);
        mAudioManager.playSoundEffect(AudioManager.FX_FOCUS_NAVIGATION_DOWN);
        mAudioManager.playSoundEffect(AudioManager.FX_FOCUS_NAVIGATION_LEFT);
        mAudioManager.playSoundEffect(AudioManager.FX_FOCUS_NAVIGATION_RIGHT);

        mAudioManager.playSoundEffect(AudioManager.FX_FOCUS_NAVIGATION_UP, volume);
        mAudioManager.playSoundEffect(AudioManager.FX_FOCUS_NAVIGATION_DOWN, volume);
        mAudioManager.playSoundEffect(AudioManager.FX_FOCUS_NAVIGATION_LEFT, volume);
        mAudioManager.playSoundEffect(AudioManager.FX_FOCUS_NAVIGATION_RIGHT, volume);
    }

    @Test
    public void testCheckingZenModeBlockDoesNotRequireNotificationPolicyAccess() throws Exception {
        // set zen mode to priority only, so playSoundEffect will check notification policy
        Utils.toggleNotificationPolicyAccess(mContext.getPackageName(), getInstrumentation(),
                true);
        setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY);
        Settings.System.putInt(mContext.getContentResolver(), SOUND_EFFECTS_ENABLED, 1);

        // take away write-notification policy access from the package
        Utils.toggleNotificationPolicyAccess(mContext.getPackageName(), getInstrumentation(),
                false);

        // playSoundEffect should NOT throw a security exception; all apps have read-access
        mAudioManager.playSoundEffect(SoundEffectConstants.CLICK);
    }

    @Test
    public void testMusicActive() throws Exception {
        if (mAudioManager.isMusicActive()) {
            return;
        }
        MediaPlayer mp = MediaPlayer.create(mContext, MP3_TO_PLAY);
        assertNotNull(mp);
        mp.setAudioStreamType(STREAM_MUSIC);
        mp.start();
        assertMusicActive(true);
        mp.stop();
        mp.release();
        assertMusicActive(false);
    }

    @AppModeFull(reason = "Instant apps cannot hold android.permission.MODIFY_AUDIO_SETTINGS")
    @Test
    public void testAccessMode() throws Exception {
        mAudioManager.setMode(MODE_RINGTONE);
        assertEquals(MODE_RINGTONE, mAudioManager.getMode());
        mAudioManager.setMode(MODE_IN_COMMUNICATION);
        assertEquals(MODE_IN_COMMUNICATION, mAudioManager.getMode());
        mAudioManager.setMode(MODE_NORMAL);
        assertEquals(MODE_NORMAL, mAudioManager.getMode());
    }

    @Test
    public void testSetSurroundFormatEnabled() throws Exception {
        getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(
                Manifest.permission.WRITE_SETTINGS);

        int audioFormat = AudioFormat.ENCODING_DTS;

        mAudioManager.setSurroundFormatEnabled(audioFormat, true /*enabled*/);
        assertTrue(mAudioManager.isSurroundFormatEnabled(audioFormat));

        mAudioManager.setSurroundFormatEnabled(audioFormat, false /*enabled*/);
        assertFalse(mAudioManager.isSurroundFormatEnabled(audioFormat));

        getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
    }

    @AppModeFull(reason = "Instant apps cannot hold android.permission.WRITE_SETTINGS")
    @Test
    public void testSetEncodedSurroundMode() throws Exception {
        getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(
                Manifest.permission.WRITE_SETTINGS);

        int expectedSurroundFormatsMode = Settings.Global.ENCODED_SURROUND_OUTPUT_MANUAL;
        mAudioManager.setEncodedSurroundMode(expectedSurroundFormatsMode);
        assertEquals(expectedSurroundFormatsMode, mAudioManager.getEncodedSurroundMode());

        expectedSurroundFormatsMode = Settings.Global.ENCODED_SURROUND_OUTPUT_NEVER;
        mAudioManager.setEncodedSurroundMode(expectedSurroundFormatsMode);
        assertEquals(expectedSurroundFormatsMode, mAudioManager.getEncodedSurroundMode());

        getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
    }

    @SuppressWarnings("deprecation")
    @AppModeFull(reason = "Instant apps cannot hold android.permission.MODIFY_AUDIO_SETTINGS")
    @Test
    public void testRouting() throws Exception {
        // setBluetoothA2dpOn is a no-op, and getRouting should always return -1
        boolean oldA2DP = mAudioManager.isBluetoothA2dpOn();
        mAudioManager.setBluetoothA2dpOn(true);
        assertEquals(oldA2DP, mAudioManager.isBluetoothA2dpOn());
        mAudioManager.setBluetoothA2dpOn(false);
        assertEquals(oldA2DP, mAudioManager.isBluetoothA2dpOn());

        assertEquals(-1, mAudioManager.getRouting(MODE_RINGTONE));
        assertEquals(-1, mAudioManager.getRouting(MODE_NORMAL));
        assertEquals(-1, mAudioManager.getRouting(MODE_IN_CALL));
        assertEquals(-1, mAudioManager.getRouting(MODE_IN_COMMUNICATION));

        mAudioManager.setBluetoothScoOn(true);
        assertTrueCheckTimeout(mAudioManager, p -> p.isBluetoothScoOn(),
                DEFAULT_ASYNC_CALL_TIMEOUT_MS, "isBluetoothScoOn returned false");

        mAudioManager.setBluetoothScoOn(false);
        assertTrueCheckTimeout(mAudioManager, p -> !p.isBluetoothScoOn(),
                DEFAULT_ASYNC_CALL_TIMEOUT_MS, "isBluetoothScoOn returned true");

        //  Speaker Phone Not supported in Automotive
        if (isAutomotive()) {
            return;
        }

        try {
            getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(
                    Manifest.permission.MODIFY_PHONE_STATE);

            mAudioManager.setSpeakerphoneOn(true);
            assertTrueCheckTimeout(mAudioManager, p -> p.isSpeakerphoneOn(),
                    DEFAULT_ASYNC_CALL_TIMEOUT_MS, "isSpeakerPhoneOn() returned false");

            mAudioManager.setSpeakerphoneOn(false);
            assertTrueCheckTimeout(mAudioManager, p -> !p.isSpeakerphoneOn(),
                    DEFAULT_ASYNC_CALL_TIMEOUT_MS, "isSpeakerPhoneOn() returned true");

        } finally {
            getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
        }
    }

    @Test
    public void testVibrateNotification() throws Exception {
        if (mUseFixedVolume || !mHasVibrator) {
            return;
        }
        if (mSkipAutoVolumeTests) {
            // setRingerMode is a no-op
            return;
        }
        Utils.toggleNotificationPolicyAccess(
                mContext.getPackageName(), getInstrumentation(), true);
        // VIBRATE_SETTING_ON
        mAudioManager.setVibrateSetting(VIBRATE_TYPE_NOTIFICATION, VIBRATE_SETTING_ON);
        assertEquals(mHasVibrator ? VIBRATE_SETTING_ON : VIBRATE_SETTING_OFF,
                mAudioManager.getVibrateSetting(VIBRATE_TYPE_NOTIFICATION));
        mAudioManager.setRingerMode(RINGER_MODE_NORMAL);
        assertEquals(mHasVibrator, mAudioManager.shouldVibrate(VIBRATE_TYPE_NOTIFICATION));

        mAudioManager.setRingerMode(RINGER_MODE_SILENT);
        assertFalse(mAudioManager.shouldVibrate(VIBRATE_TYPE_NOTIFICATION));

        mAudioManager.setRingerMode(RINGER_MODE_VIBRATE);
        assertEquals(mHasVibrator ? RINGER_MODE_VIBRATE : RINGER_MODE_SILENT,
                mAudioManager.getRingerMode());
        assertEquals(mHasVibrator, mAudioManager.shouldVibrate(VIBRATE_TYPE_NOTIFICATION));

        // VIBRATE_SETTING_OFF
        mAudioManager.setVibrateSetting(VIBRATE_TYPE_NOTIFICATION, VIBRATE_SETTING_OFF);
        assertEquals(VIBRATE_SETTING_OFF,
                mAudioManager.getVibrateSetting(VIBRATE_TYPE_NOTIFICATION));
        mAudioManager.setRingerMode(RINGER_MODE_NORMAL);
        assertFalse(mAudioManager.shouldVibrate(VIBRATE_TYPE_NOTIFICATION));

        mAudioManager.setRingerMode(RINGER_MODE_SILENT);
        assertFalse(mAudioManager.shouldVibrate(VIBRATE_TYPE_NOTIFICATION));

        mAudioManager.setRingerMode(RINGER_MODE_VIBRATE);
        assertEquals(mHasVibrator ? RINGER_MODE_VIBRATE : RINGER_MODE_SILENT,
                mAudioManager.getRingerMode());
        assertFalse(mAudioManager.shouldVibrate(VIBRATE_TYPE_NOTIFICATION));

        // VIBRATE_SETTING_ONLY_SILENT
        mAudioManager.setVibrateSetting(VIBRATE_TYPE_NOTIFICATION, VIBRATE_SETTING_ONLY_SILENT);
        assertEquals(mHasVibrator ? VIBRATE_SETTING_ONLY_SILENT : VIBRATE_SETTING_OFF,
                mAudioManager.getVibrateSetting(VIBRATE_TYPE_NOTIFICATION));
        mAudioManager.setRingerMode(RINGER_MODE_NORMAL);
        assertFalse(mAudioManager.shouldVibrate(VIBRATE_TYPE_NOTIFICATION));

        mAudioManager.setRingerMode(RINGER_MODE_SILENT);
        assertFalse(mAudioManager.shouldVibrate(VIBRATE_TYPE_NOTIFICATION));

        mAudioManager.setRingerMode(RINGER_MODE_VIBRATE);
        assertEquals(mHasVibrator ? RINGER_MODE_VIBRATE : RINGER_MODE_SILENT,
                mAudioManager.getRingerMode());
        assertEquals(mHasVibrator, mAudioManager.shouldVibrate(VIBRATE_TYPE_NOTIFICATION));

        // VIBRATE_TYPE_NOTIFICATION
        mAudioManager.setVibrateSetting(VIBRATE_TYPE_NOTIFICATION, VIBRATE_SETTING_ON);
        assertEquals(mHasVibrator ? VIBRATE_SETTING_ON : VIBRATE_SETTING_OFF,
                mAudioManager.getVibrateSetting(VIBRATE_TYPE_NOTIFICATION));
        mAudioManager.setVibrateSetting(VIBRATE_TYPE_NOTIFICATION, VIBRATE_SETTING_OFF);
        assertEquals(VIBRATE_SETTING_OFF, mAudioManager
                .getVibrateSetting(VIBRATE_TYPE_NOTIFICATION));
        mAudioManager.setVibrateSetting(VIBRATE_TYPE_NOTIFICATION, VIBRATE_SETTING_ONLY_SILENT);
        assertEquals(mHasVibrator ? VIBRATE_SETTING_ONLY_SILENT : VIBRATE_SETTING_OFF,
                mAudioManager.getVibrateSetting(VIBRATE_TYPE_NOTIFICATION));
    }

    @Test
    public void testVibrateRinger() throws Exception {
        if (mUseFixedVolume || !mHasVibrator) {
            return;
        }
        if (mSkipAutoVolumeTests) {
            // setRingerMode is a no-op
            return;
        }
        Utils.toggleNotificationPolicyAccess(
                mContext.getPackageName(), getInstrumentation(), true);
        // VIBRATE_TYPE_RINGER
        mAudioManager.setVibrateSetting(VIBRATE_TYPE_RINGER, VIBRATE_SETTING_ON);
        assertEquals(mHasVibrator ? VIBRATE_SETTING_ON : VIBRATE_SETTING_OFF,
                mAudioManager.getVibrateSetting(VIBRATE_TYPE_RINGER));
        mAudioManager.setRingerMode(RINGER_MODE_NORMAL);
        assertEquals(mHasVibrator, mAudioManager.shouldVibrate(VIBRATE_TYPE_RINGER));

        mAudioManager.setRingerMode(RINGER_MODE_SILENT);
        assertFalse(mAudioManager.shouldVibrate(VIBRATE_TYPE_RINGER));

        mAudioManager.setRingerMode(RINGER_MODE_VIBRATE);
        assertEquals(mHasVibrator ? RINGER_MODE_VIBRATE : RINGER_MODE_SILENT,
                mAudioManager.getRingerMode());
        assertEquals(mHasVibrator, mAudioManager.shouldVibrate(VIBRATE_TYPE_RINGER));

        // VIBRATE_SETTING_OFF
        mAudioManager.setVibrateSetting(VIBRATE_TYPE_RINGER, VIBRATE_SETTING_OFF);
        assertEquals(VIBRATE_SETTING_OFF, mAudioManager.getVibrateSetting(VIBRATE_TYPE_RINGER));
        mAudioManager.setRingerMode(RINGER_MODE_NORMAL);
        assertFalse(mAudioManager.shouldVibrate(VIBRATE_TYPE_RINGER));

        mAudioManager.setRingerMode(RINGER_MODE_SILENT);
        assertFalse(mAudioManager.shouldVibrate(VIBRATE_TYPE_RINGER));

        mAudioManager.setRingerMode(RINGER_MODE_VIBRATE);
        assertEquals(mHasVibrator ? RINGER_MODE_VIBRATE : RINGER_MODE_SILENT,
                mAudioManager.getRingerMode());
        // Note: as of Froyo, if VIBRATE_TYPE_RINGER is set to OFF, it will
        // not vibrate, even in RINGER_MODE_VIBRATE. This allows users to
        // disable the vibration for incoming calls only.
        assertFalse(mAudioManager.shouldVibrate(VIBRATE_TYPE_RINGER));

        // VIBRATE_SETTING_ONLY_SILENT
        mAudioManager.setVibrateSetting(VIBRATE_TYPE_RINGER, VIBRATE_SETTING_ONLY_SILENT);
        assertEquals(mHasVibrator ? VIBRATE_SETTING_ONLY_SILENT : VIBRATE_SETTING_OFF,
                mAudioManager.getVibrateSetting(VIBRATE_TYPE_RINGER));
        mAudioManager.setRingerMode(RINGER_MODE_NORMAL);
        assertFalse(mAudioManager.shouldVibrate(VIBRATE_TYPE_RINGER));

        mAudioManager.setRingerMode(RINGER_MODE_SILENT);
        assertFalse(mAudioManager.shouldVibrate(VIBRATE_TYPE_RINGER));

        mAudioManager.setRingerMode(RINGER_MODE_VIBRATE);
        assertEquals(mHasVibrator ? RINGER_MODE_VIBRATE : RINGER_MODE_SILENT,
                mAudioManager.getRingerMode());
        assertEquals(mHasVibrator, mAudioManager.shouldVibrate(VIBRATE_TYPE_RINGER));

        // VIBRATE_TYPE_NOTIFICATION
        mAudioManager.setVibrateSetting(VIBRATE_TYPE_RINGER, VIBRATE_SETTING_ON);
        assertEquals(mHasVibrator ? VIBRATE_SETTING_ON : VIBRATE_SETTING_OFF,
                mAudioManager.getVibrateSetting(VIBRATE_TYPE_RINGER));
        mAudioManager.setVibrateSetting(VIBRATE_TYPE_RINGER, VIBRATE_SETTING_OFF);
        assertEquals(VIBRATE_SETTING_OFF, mAudioManager.getVibrateSetting(VIBRATE_TYPE_RINGER));
        mAudioManager.setVibrateSetting(VIBRATE_TYPE_RINGER, VIBRATE_SETTING_ONLY_SILENT);
        assertEquals(mHasVibrator ? VIBRATE_SETTING_ONLY_SILENT : VIBRATE_SETTING_OFF,
                mAudioManager.getVibrateSetting(VIBRATE_TYPE_RINGER));
    }

    @Test
    public void testAccessRingMode() throws Exception {
        Utils.toggleNotificationPolicyAccess(
                mContext.getPackageName(), getInstrumentation(), true);
        mAudioManager.setRingerMode(RINGER_MODE_NORMAL);
        assertEquals(RINGER_MODE_NORMAL, mAudioManager.getRingerMode());

        mAudioManager.setRingerMode(RINGER_MODE_SILENT);
        // AudioService#setRingerMode() has:
        // if (isTelevision) return;
        if (mSkipRingerTests) {
            assertEquals(RINGER_MODE_NORMAL, mAudioManager.getRingerMode());
        } else {
            assertEquals(RINGER_MODE_SILENT, mAudioManager.getRingerMode());
        }

        mAudioManager.setRingerMode(RINGER_MODE_VIBRATE);
        if (mSkipRingerTests) {
            assertEquals(RINGER_MODE_NORMAL, mAudioManager.getRingerMode());
        } else {
            assertEquals(mHasVibrator ? RINGER_MODE_VIBRATE : RINGER_MODE_SILENT,
                    mAudioManager.getRingerMode());
        }
    }

    // TODO explain the intended behavior in this test
    /**
     * Test that in RINGER_MODE_VIBRATE we observe:
     * if NOTIFICATION & RING are not aliased:
     *   ADJUST_UNMUTE NOTIFICATION -> no change (no mode change, NOTIF still muted)
     *   ADJUST_UNMUTE NOTIFICATION + FLAG_ALLOW_RINGER_MODES -> MODE_NORMAL
     * if NOTIFICATION & RING are aliased:
     *   ADJUST_UNMUTE NOTIFICATION -> MODE_NORMAL
     *   ADJUST_UNMUTE NOTIFICATION + FLAG_ALLOW_RINGER_MODES -> MODE_NORMAL
     * @throws Exception
     */
    @Test
    public void testAdjustUnmuteNotificationInVibrate() throws Exception {
        Log.i(TAG, "starting testAdjustUnmuteNotificationInVibrate");
        if (mSkipRingerTests) {
            Log.i(TAG, "skipping testAdjustUnmuteNotificationInVibrate");
            return;
        }
        if (!mHasVibrator) {
            Log.i(TAG, "skipping testAdjustUnmuteNotificationInVibrate, no vibrator");
            return;
        }
        // set mode to VIBRATE
        Utils.toggleNotificationPolicyAccess(
                mContext.getPackageName(), getInstrumentation(), true);

        Map<Integer, MuteStateTransition> expectedVibrateTransitions = Map.of(
                STREAM_MUSIC, new MuteStateTransition(false, false),
                STREAM_RING, new MuteStateTransition(false, true),
                STREAM_NOTIFICATION, new MuteStateTransition(false, true),
                STREAM_ALARM, new MuteStateTransition(false, false));

        assertStreamMuteStateChange(() -> mAudioManager.setRingerMode(RINGER_MODE_VIBRATE),
                expectedVibrateTransitions,
                "RING and NOTIF should be muted in MODE_VIBRATE");

        assertEquals(RINGER_MODE_VIBRATE, mAudioManager.getRingerMode());
        Utils.toggleNotificationPolicyAccess(
                mContext.getPackageName(), getInstrumentation(), false);

        getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.MODIFY_AUDIO_SETTINGS_PRIVILEGED);
        final int notifiAliasedStream = mAudioManager.getStreamTypeAlias(STREAM_NOTIFICATION);
        getInstrumentation().getUiAutomation().dropShellPermissionIdentity();

        Map<Integer, MuteStateTransition> unmuteRingerTransitions = Map.of(
            STREAM_MUSIC, new MuteStateTransition(false, false),
            STREAM_RING, new MuteStateTransition(true , false),
            STREAM_NOTIFICATION, new MuteStateTransition(true, false),
            STREAM_ALARM, new MuteStateTransition(false, false));

        if (notifiAliasedStream == STREAM_NOTIFICATION) {
            Log.i(TAG, "testAdjustUnmuteNotificationInVibrate: NOTIF independent");

            Map<Integer, MuteStateTransition> noMuteTransitions = Map.of(
                STREAM_MUSIC, new MuteStateTransition(false, false),
                STREAM_RING, new MuteStateTransition(true , true),
                STREAM_NOTIFICATION, new MuteStateTransition(true, true),
                STREAM_ALARM, new MuteStateTransition(false, false));

            // unmute NOTIFICATION
            assertStreamMuteStateChange(() -> mAudioManager.adjustStreamVolume(
                        STREAM_NOTIFICATION, AudioManager.ADJUST_UNMUTE, 0),
                    noMuteTransitions,
                    "NOTIFICATION should not unmute");
            // unmuting NOTIFICATION should not have exited RINGER_MODE_VIBRATE
            assertEquals(RINGER_MODE_VIBRATE, mAudioManager.getRingerMode());


            assertStreamMuteStateChange(() -> mAudioManager.adjustStreamVolume(
                        STREAM_NOTIFICATION,
                        AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_ALLOW_RINGER_MODES),
                    unmuteRingerTransitions,
                    "NOTIFICATION(+FLAG_ALLOW_RINGER_MODES) should unmute RING/NOTIF");
            // unmuting NOTIFICATION w/ FLAG_ALLOW_RINGER_MODES should have exited MODE_VIBRATE
            assertEquals(RINGER_MODE_NORMAL, mAudioManager.getRingerMode());
        } else if (notifiAliasedStream == STREAM_RING) {
            Log.i(TAG, "testAdjustUnmuteNotificationInVibrate: NOTIF/RING aliased");
            // unmute NOTIFICATION (should be just like unmuting RING)
            assertStreamMuteStateChange(() -> mAudioManager.adjustStreamVolume(
                        STREAM_NOTIFICATION, AudioManager.ADJUST_UNMUTE, 0),
                        unmuteRingerTransitions,
                        "when aliased NOTIF/RING should be unmuted");

            assertEquals(RINGER_MODE_NORMAL, mAudioManager.getRingerMode());

            // test again with FLAG_ALLOW_RINGER_MODES
            Utils.toggleNotificationPolicyAccess(
                    mContext.getPackageName(), getInstrumentation(), true);
            mAudioManager.setRingerMode(RINGER_MODE_VIBRATE);
            assertEquals(RINGER_MODE_VIBRATE, mAudioManager.getRingerMode());
            Utils.toggleNotificationPolicyAccess(
                    mContext.getPackageName(), getInstrumentation(), false);

            // unmute NOTIFICATION (should be just like unmuting RING)
            assertStreamMuteStateChange(() -> mAudioManager.adjustStreamVolume(
                        STREAM_NOTIFICATION,
                        AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_ALLOW_RINGER_MODES),
                    unmuteRingerTransitions,
                    "when aliased NOTIF/RING should be unmuted");

            // unmuting NOTIFICATION should have exited RINGER_MODE_VIBRATE
            assertEquals(RINGER_MODE_NORMAL, mAudioManager.getRingerMode());
        }
    }

    /**
     * Test that in RINGER_MODE_SILENT we observe:
     * ADJUST_UNMUTE NOTIFICATION -> no change (no mode change, NOTIF still muted)
     *
     * Note that in SILENT we cannot test ADJUST_UNMUTE NOTIFICATION + FLAG_ALLOW_RINGER_MODES
     * because it depends on VolumePolicy.volumeUpToExitSilent.
     * TODO add test API to query VolumePolicy, expected in MODE_SILENT:
     * ADJUST_UNMUTE NOTIFICATION + FLAG_ALLOW_RINGER_MODE ->
     *                            no change if VolumePolicy.volumeUpToExitSilent false (default?)
     * ADJUST_UNMUTE NOTIFICATION + FLAG_ALLOW_RINGER_MODE ->
     *                            MODE_NORMAL if VolumePolicy.volumeUpToExitSilent true
     * @throws Exception
     */
    @Test
    public void testAdjustUnmuteNotificationInSilent() throws Exception {
        assumeFalse(mSkipRingerTests);

        Map<Integer, MuteStateTransition> expectedTransitionsSilentMode = Map.of(
                STREAM_MUSIC, new MuteStateTransition(false, false),
                STREAM_NOTIFICATION, new MuteStateTransition(false, true),
                STREAM_RING, new MuteStateTransition(false, true),
                STREAM_ALARM, new MuteStateTransition(false, false));


        // set mode to SILENT
        Utils.toggleNotificationPolicyAccess(
                mContext.getPackageName(), getInstrumentation(), true);
        assertStreamMuteStateChange(() -> mAudioManager.setRingerMode(RINGER_MODE_SILENT),
                expectedTransitionsSilentMode,
                "RING/NOTIF should mute in SILENT");
        assertEquals(RINGER_MODE_SILENT, mAudioManager.getRingerMode());
        Utils.toggleNotificationPolicyAccess(
                mContext.getPackageName(), getInstrumentation(), false);

        Map<Integer, MuteStateTransition> expectedTransitionsRemainSilentMode = Map.of(
                STREAM_MUSIC, new MuteStateTransition(false, false),
                STREAM_NOTIFICATION, new MuteStateTransition(true, true),
                STREAM_RING, new MuteStateTransition(true, true),
                STREAM_ALARM, new MuteStateTransition(false, false));


        // unmute NOTIFICATION
        assertStreamMuteStateChange(() -> mAudioManager.adjustStreamVolume(
                    STREAM_NOTIFICATION, AudioManager.ADJUST_UNMUTE, 0),
                expectedTransitionsRemainSilentMode,
                "Unmute NOTIF should have no effect in SILENT");

        // unmuting NOTIFICATION should not have exited RINGER_MODE_SILENT
        assertEquals(RINGER_MODE_SILENT, mAudioManager.getRingerMode());
    }

    @Test
    public void testSetRingerModePolicyAccess() throws Exception {
        assumeFalse(mSkipRingerTests);
        // Apps without policy access cannot change silent -> normal or silent -> vibrate.
        Utils.toggleNotificationPolicyAccess(
                mContext.getPackageName(), getInstrumentation(), true);
        mAudioManager.setRingerMode(RINGER_MODE_SILENT);
        assertEquals(RINGER_MODE_SILENT, mAudioManager.getRingerMode());
        Utils.toggleNotificationPolicyAccess(
                mContext.getPackageName(), getInstrumentation(), false);

        try {
            mAudioManager.setRingerMode(RINGER_MODE_NORMAL);
            fail("Apps without notification policy access cannot change ringer mode");
        } catch (SecurityException e) {
        }

        try {
            mAudioManager.setRingerMode(RINGER_MODE_VIBRATE);
            fail("Apps without notification policy access cannot change ringer mode");
        } catch (SecurityException e) {
        }

        // Apps without policy access cannot change normal -> silent.
        Utils.toggleNotificationPolicyAccess(
                mContext.getPackageName(), getInstrumentation(), true);
        mAudioManager.setRingerMode(RINGER_MODE_NORMAL);
        assertEquals(RINGER_MODE_NORMAL, mAudioManager.getRingerMode());
        Utils.toggleNotificationPolicyAccess(
                mContext.getPackageName(), getInstrumentation(), false);

        try {
            mAudioManager.setRingerMode(RINGER_MODE_SILENT);
            fail("Apps without notification policy access cannot change ringer mode");
        } catch (SecurityException e) {
        }
        assertEquals(RINGER_MODE_NORMAL, mAudioManager.getRingerMode());

        if (mHasVibrator) {
            // Apps without policy access cannot change vibrate -> silent.
            Utils.toggleNotificationPolicyAccess(
                    mContext.getPackageName(), getInstrumentation(), true);
            mAudioManager.setRingerMode(RINGER_MODE_VIBRATE);
            assertEquals(RINGER_MODE_VIBRATE, mAudioManager.getRingerMode());
            Utils.toggleNotificationPolicyAccess(
                    mContext.getPackageName(), getInstrumentation(), false);

            try {
                mAudioManager.setRingerMode(RINGER_MODE_SILENT);
                fail("Apps without notification policy access cannot change ringer mode");
            } catch (SecurityException e) {
            }

            // Apps without policy access can change vibrate -> normal and vice versa.
            assertEquals(RINGER_MODE_VIBRATE, mAudioManager.getRingerMode());
            mAudioManager.setRingerMode(RINGER_MODE_NORMAL);
            assertEquals(RINGER_MODE_NORMAL, mAudioManager.getRingerMode());
            mAudioManager.setRingerMode(RINGER_MODE_VIBRATE);
            assertEquals(RINGER_MODE_VIBRATE, mAudioManager.getRingerMode());
        }
    }

    @Test
    public void testAccessRampingRinger() {
        boolean originalEnabledState = mAudioManager.isRampingRingerEnabled();
        try {
            mAudioManager.setRampingRingerEnabled(false);
            assertFalse(mAudioManager.isRampingRingerEnabled());

            mAudioManager.setRampingRingerEnabled(true);
            assertTrue(mAudioManager.isRampingRingerEnabled());
        } finally {
            mAudioManager.setRampingRingerEnabled(originalEnabledState);
        }
    }

    @Test
    public void testRampingRingerSetting() {
        boolean originalEnabledState = mAudioManager.isRampingRingerEnabled();
        try {
            // Deprecated public setting should still be supported and affect the setting getter.
            Settings.Global.putInt(mContext.getContentResolver(), APPLY_RAMPING_RINGER, 0);
            assertFalse(mAudioManager.isRampingRingerEnabled());

            Settings.Global.putInt(mContext.getContentResolver(), APPLY_RAMPING_RINGER, 1);
            assertTrue(mAudioManager.isRampingRingerEnabled());
        } finally {
            mAudioManager.setRampingRingerEnabled(originalEnabledState);
        }
    }

    @Test
    public void testVolume() throws Exception {
        if (MediaUtils.check(mIsTelevision, "No volume test due to fixed/full vol devices"))
            return;
        if (mSkipAutoVolumeTests) {
            // setStreamVolume/adjustVolume are no-op
            return;
        }
        Utils.toggleNotificationPolicyAccess(
                mContext.getPackageName(), getInstrumentation(), true);
        int volume, volumeDelta;
        int[] streams = {STREAM_ALARM,
                STREAM_MUSIC,
                STREAM_VOICE_CALL,
                STREAM_RING};

        int maxMusicVolume = mAudioManager.getStreamMaxVolume(STREAM_MUSIC);

        for (int stream : streams) {
            if (mIsSingleVolume && stream != STREAM_MUSIC) {
                continue;
            }

            // set ringer mode to back normal to not interfere with volume tests
            mAudioManager.setRingerMode(RINGER_MODE_NORMAL);

            int maxVolume = mAudioManager.getStreamMaxVolume(stream);
            int minVolume = mAudioManager.getStreamMinVolume(stream);

            // validate min
            assertTrue(String.format("minVolume(%d) must be >= 0", minVolume), minVolume >= 0);
            assertTrue(String.format("minVolume(%d) must be < maxVolume(%d)", minVolume,
                    maxVolume),
                    minVolume < maxVolume);

            final int minNonZeroVolume = Math.max(minVolume, 1);
            mAudioManager.setStreamVolume(stream, minNonZeroVolume, 0);
            if (mUseFixedVolume) {
                assertEquals(maxVolume, mAudioManager.getStreamVolume(stream));
                continue;
            }
            assertEquals(String.format("stream=%d", stream),
                    minNonZeroVolume, mAudioManager.getStreamVolume(stream));

            if (stream == STREAM_MUSIC && mAudioManager.isWiredHeadsetOn()) {
                // due to new regulations, music sent over a wired headset may be volume limited
                // until the user explicitly increases the limit, so we can't rely on being able
                // to set the volume to getStreamMaxVolume(). Instead, determine the current limit
                // by increasing the volume until it won't go any higher, then use that volume as
                // the maximum for the purposes of this test
                int curvol = 0;
                int prevvol = 0;
                do {
                    prevvol = curvol;
                    mAudioManager.adjustStreamVolume(stream, ADJUST_RAISE, 0);
                    curvol = mAudioManager.getStreamVolume(stream);
                } while (curvol != prevvol);
                maxVolume = maxMusicVolume = curvol;
            }
            waitForStreamVolumeSet(stream, maxVolume);
            assertCallDoesNotChangeStreamVolume(
                    () -> mAudioManager.adjustStreamVolume(stream, ADJUST_RAISE, 0),
                    stream,
                    "No change expected at max volume");

            volumeDelta = getVolumeDelta(mAudioManager.getStreamVolume(stream));
            assertCallChangesStreamVolume(
                    () -> mAudioManager.adjustSuggestedStreamVolume(ADJUST_LOWER, stream, 0),
                    stream, maxVolume - volumeDelta,
                    "Vol ADJUST_LOWER suggested stream:" + stream + " maxVol:" + maxVolume);

            // volume lower
            mAudioManager.setStreamVolume(stream, maxVolume, 0);
            volume = mAudioManager.getStreamVolume(stream);
            while (volume > minVolume) {
                volumeDelta = getVolumeDelta(mAudioManager.getStreamVolume(stream));
                assertCallChangesStreamVolume(
                        () -> mAudioManager.adjustStreamVolume(stream, ADJUST_LOWER, 0),
                        stream,  Math.max(0, volume - volumeDelta),
                        "Vol ADJUST_LOWER on stream:" + stream + " vol:" + volume
                                + " minVol:" + minVolume + " volDelta:" + volumeDelta);
                volume = mAudioManager.getStreamVolume(stream);
            }

            mAudioManager.adjustStreamVolume(stream, ADJUST_SAME, 0);

            // volume raise
            mAudioManager.setStreamVolume(stream, minNonZeroVolume, 0);
            volume = mAudioManager.getStreamVolume(stream);
            while (volume < maxVolume) {
                volumeDelta = getVolumeDelta(mAudioManager.getStreamVolume(stream));
                assertCallChangesStreamVolume(
                        () -> mAudioManager.adjustStreamVolume(stream, ADJUST_RAISE, 0),
                        stream,   Math.min(volume + volumeDelta, maxVolume),
                        "Vol ADJUST_RAISE on stream:" + stream + " vol:" + volume
                                + " maxVol:" + maxVolume + " volDelta:" + volumeDelta);
                volume = mAudioManager.getStreamVolume(stream);
            }

            // volume same
            waitForStreamVolumeSet(stream, maxVolume);
            assertCallDoesNotChangeStreamVolume(
                    () -> mAudioManager.adjustStreamVolume(stream, ADJUST_SAME, 0),
                    stream,
                    "Vol ADJUST_RAISE onADJUST_SAME stream:" + stream);
            mAudioManager.setStreamVolume(stream, maxVolume, 0);
        }

        if (mUseFixedVolume) {
            return;
        }

        boolean isMusicPlayingBeforeTest = false;
        if (mAudioManager.isMusicActive()) {
            isMusicPlayingBeforeTest = true;
        }

        // TODO this doesn't test anything now that STREAM_MUSIC is the default
        MediaPlayer mp = MediaPlayer.create(mContext, MP3_TO_PLAY);
        assertNotNull(mp);
        mp.setAudioStreamType(STREAM_MUSIC);
        mp.setLooping(true);
        mp.start();
        assertMusicActive(true);

        waitForStreamVolumeSet(STREAM_MUSIC, maxMusicVolume - 1);
        // adjust volume as ADJUST_SAME
        assertCallDoesNotChangeStreamVolume(
                () -> mAudioManager.adjustVolume(ADJUST_SAME, 0),
                STREAM_MUSIC);

        // adjust volume as ADJUST_RAISE
        waitForStreamVolumeSet(STREAM_MUSIC, 0);
        volumeDelta = getVolumeDelta(mAudioManager.getStreamVolume(STREAM_MUSIC));
        assertCallChangesStreamVolume(
                () -> mAudioManager.adjustVolume(ADJUST_RAISE, 0),
                STREAM_MUSIC,
                volumeDelta);

        // adjust volume as ADJUST_LOWER
        waitForStreamVolumeSet(STREAM_MUSIC, maxMusicVolume);
        volumeDelta = getVolumeDelta(mAudioManager.getStreamVolume(STREAM_MUSIC));
        assertCallChangesStreamVolume(
                () -> mAudioManager.adjustVolume(ADJUST_LOWER, 0),
                STREAM_MUSIC,
                maxMusicVolume - volumeDelta);

        mp.stop();
        mp.release();
        if (!isMusicPlayingBeforeTest) {
            assertMusicActive(false);
        }
    }

    @Test
    public void testAccessibilityVolume() throws Exception {
        // TODO this does not test the positive case (having permissions)
        assumeFalse("AudioManagerTest testAccessibilityVolume() skipped: fixed volume",
                mUseFixedVolume);

        final int maxA11yVol = mAudioManager.getStreamMaxVolume(STREAM_ACCESSIBILITY);
        assertWithMessage("Max a11yVol must be strictly positive")
                .that(maxA11yVol)
                .isGreaterThan(0);

        // changing STREAM_ACCESSIBILITY is subject to permission
        assertCallDoesNotChangeStreamVolume(
                () -> mAudioManager.setStreamVolume(STREAM_ACCESSIBILITY, INIT_VOL + 1, 0),
                STREAM_ACCESSIBILITY,
                "Setting accessibility vol requires perms");
        assertCallDoesNotChangeStreamVolume(
                () -> mAudioManager.adjustStreamVolume(STREAM_ACCESSIBILITY, ADJUST_LOWER, 0),
                STREAM_ACCESSIBILITY,
                "Setting accessibility vol requires perms");

        assertCallDoesNotChangeStreamVolume(
                () -> mAudioManager.adjustStreamVolume(STREAM_ACCESSIBILITY, ADJUST_RAISE, 0),
                STREAM_ACCESSIBILITY,
                "Setting accessibility vol requires perms");
    }

    @Test
    public void testSetVoiceCallVolumeToZeroPermission() {
        // Verify that only apps with MODIFY_PHONE_STATE can set VOICE_CALL_STREAM to 0
        mAudioManager.setStreamVolume(STREAM_VOICE_CALL, 0, 0);
        assertTrue("MODIFY_PHONE_STATE is required in order to set voice call volume to 0",
                    mAudioManager.getStreamVolume(STREAM_VOICE_CALL) != 0);
    }

    @Test
    public void testMuteFixedVolume() throws Exception {
        if (mSkipAutoVolumeTests) {
            // adjustStreamVolume is a no-op
            return;
        }
        int[] streams = {
                STREAM_VOICE_CALL,
                STREAM_MUSIC,
                STREAM_RING,
                STREAM_ALARM,
                STREAM_NOTIFICATION,
                STREAM_SYSTEM};
        if (mUseFixedVolume) {
            for (int stream : streams) {
                mAudioManager.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, 0);
                assertFalse("Muting should not affect a fixed volume device.",
                        mAudioManager.isStreamMute(stream));

                mAudioManager.adjustStreamVolume(stream, AudioManager.ADJUST_TOGGLE_MUTE, 0);
                assertFalse("Toggling mute should not affect a fixed volume device.",
                        mAudioManager.isStreamMute(stream));

                mAudioManager.setStreamMute(stream, true);
                assertFalse("Muting should not affect a fixed volume device.",
                        mAudioManager.isStreamMute(stream));
            }
        }
    }

    @Test
    public void testMuteDndAffectedStreams() throws Exception {
        assumeFalse(mSkipRingerTests);
        int[] streams = { STREAM_RING };
        // Mute streams
        Utils.toggleNotificationPolicyAccess(
                mContext.getPackageName(), getInstrumentation(), true);
        mAudioManager.setRingerMode(RINGER_MODE_SILENT);
        Utils.toggleNotificationPolicyAccess(
                mContext.getPackageName(), getInstrumentation(), false);
        // Verify streams cannot be unmuted without policy access.
        for (int stream : streams) {
            try {
                mAudioManager.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, 0);
                assertEquals("Apps without Notification policy access can't change ringer mode",
                        RINGER_MODE_SILENT, mAudioManager.getRingerMode());
            } catch (SecurityException e) {
            }

            try {
                mAudioManager.adjustStreamVolume(stream, AudioManager.ADJUST_TOGGLE_MUTE,
                        0);
                assertEquals("Apps without Notification policy access can't change ringer mode",
                        RINGER_MODE_SILENT, mAudioManager.getRingerMode());
            } catch (SecurityException e) {
            }

            try {
                mAudioManager.setStreamMute(stream, false);
                assertEquals("Apps without Notification policy access can't change ringer mode",
                        RINGER_MODE_SILENT, mAudioManager.getRingerMode());
            } catch (SecurityException e) {
            }
        }

        // This ensures we're out of vibrate or silent modes.
        Utils.toggleNotificationPolicyAccess(
                mContext.getPackageName(), getInstrumentation(), true);
        mAudioManager.setRingerMode(RINGER_MODE_NORMAL);
        for (int stream : streams) {
            // ensure each stream is on and turned up.
            mAudioManager.setStreamVolume(stream,
                    mAudioManager.getStreamMaxVolume(stream),
                    0);

            Utils.toggleNotificationPolicyAccess(
                    mContext.getPackageName(), getInstrumentation(), false);
            try {
                mAudioManager.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, 0);
                assertEquals("Apps without Notification policy access can't change ringer mode",
                        RINGER_MODE_NORMAL, mAudioManager.getRingerMode());
            } catch (SecurityException e) {
            }
            try {
                mAudioManager.adjustStreamVolume(
                        stream, AudioManager.ADJUST_TOGGLE_MUTE, 0);
                assertEquals("Apps without Notification policy access can't change ringer mode",
                        RINGER_MODE_NORMAL, mAudioManager.getRingerMode());
            } catch (SecurityException e) {
            }

            try {
                mAudioManager.setStreamMute(stream, true);
                assertEquals("Apps without Notification policy access can't change ringer mode",
                        RINGER_MODE_NORMAL, mAudioManager.getRingerMode());
            } catch (SecurityException e) {
            }
            Utils.toggleNotificationPolicyAccess(
                    mContext.getPackageName(), getInstrumentation(), true);
            testStreamMuting(stream);
        }
    }

    @Test
    public void testMuteDndUnaffectedStreams() throws Exception {
        assumeFalse(mSkipRingerTests);
        int[] streams = {
                STREAM_VOICE_CALL,
                STREAM_MUSIC,
                STREAM_ALARM
        };

        int muteAffectedStreams = System.getInt(mContext.getContentResolver(),
                System.MUTE_STREAMS_AFFECTED,
                // same defaults as in AudioService. Should be kept in sync.
                 (1 << STREAM_MUSIC) |
                         (1 << STREAM_RING) |
                         (1 << STREAM_NOTIFICATION) |
                         (1 << STREAM_SYSTEM) |
                         (1 << STREAM_VOICE_CALL));

        Utils.toggleNotificationPolicyAccess(
                mContext.getPackageName(), getInstrumentation(), true);
        // This ensures we're out of vibrate or silent modes.
        mAudioManager.setRingerMode(RINGER_MODE_NORMAL);
        Utils.toggleNotificationPolicyAccess(
                mContext.getPackageName(), getInstrumentation(), false);
        for (int stream : streams) {
            // ensure each stream is on and turned up.
            mAudioManager.setStreamVolume(stream,
                    mAudioManager.getStreamMaxVolume(stream),
                    0);
            if (((1 << stream) & muteAffectedStreams) == 0) {
                mAudioManager.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, 0);
                assertFalse("Stream " + stream + " should not be affected by mute.",
                        mAudioManager.isStreamMute(stream));
                mAudioManager.setStreamMute(stream, true);
                assertFalse("Stream " + stream + " should not be affected by mute.",
                        mAudioManager.isStreamMute(stream));
                mAudioManager.adjustStreamVolume(stream, AudioManager.ADJUST_TOGGLE_MUTE,
                        0);
                assertFalse("Stream " + stream + " should not be affected by mute.",
                        mAudioManager.isStreamMute(stream));
                continue;
            }
            testStreamMuting(stream);
        }
    }

    private void testStreamMuting(int stream) {
        if (mSkipAutoVolumeTests) {
            // adjustStreamVolume is a no-op
            return;
        }
        getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.QUERY_AUDIO_STATE);

        final int streamVolume = mAudioManager.getLastAudibleStreamVolume(stream);

        // Voice call requires MODIFY_PHONE_STATE, so we should not be able to mute
        if (stream == STREAM_VOICE_CALL) {
            mAudioManager.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, 0);
            assertFalse("Muting voice call stream (" + stream + ") should require "
                            + "MODIFY_PHONE_STATE.", mAudioManager.isStreamMute(stream));
        } else {
            mAudioManager.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, 0);
            assertTrue("Muting stream " + stream + " failed.",
                    mAudioManager.isStreamMute(stream));

            assertEquals(streamVolume, mAudioManager.getLastAudibleStreamVolume(stream));

            mAudioManager.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, 0);
            assertFalse("Unmuting stream " + stream + " failed.",
                    mAudioManager.isStreamMute(stream));

            assertEquals(streamVolume, mAudioManager.getLastAudibleStreamVolume(stream));

            mAudioManager.adjustStreamVolume(stream, AudioManager.ADJUST_TOGGLE_MUTE, 0);
            assertTrue("Toggling mute on stream " + stream + " failed.",
                    mAudioManager.isStreamMute(stream));

            assertEquals(streamVolume, mAudioManager.getLastAudibleStreamVolume(stream));

            mAudioManager.adjustStreamVolume(stream, AudioManager.ADJUST_TOGGLE_MUTE, 0);
            assertFalse("Toggling mute on stream " + stream + " failed.",
                    mAudioManager.isStreamMute(stream));

            assertEquals(streamVolume, mAudioManager.getLastAudibleStreamVolume(stream));

            mAudioManager.setStreamMute(stream, true);
            assertTrue("Muting stream " + stream + " using setStreamMute failed",
                    mAudioManager.isStreamMute(stream));

            assertEquals(streamVolume, mAudioManager.getLastAudibleStreamVolume(stream));

            // mute it three more times to verify the ref counting is gone.
            mAudioManager.setStreamMute(stream, true);
            mAudioManager.setStreamMute(stream, true);
            mAudioManager.setStreamMute(stream, true);

            mAudioManager.setStreamMute(stream, false);
            assertFalse("Unmuting stream " + stream + " using setStreamMute failed.",
                    mAudioManager.isStreamMute(stream));
        }
        assertEquals(streamVolume, mAudioManager.getLastAudibleStreamVolume(stream));

        getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
    }

    @Test
    public void testSetInvalidRingerMode() {
        int ringerMode = mAudioManager.getRingerMode();
        mAudioManager.setRingerMode(-1337);
        assertEquals(ringerMode, mAudioManager.getRingerMode());

        mAudioManager.setRingerMode(-3007);
        assertEquals(ringerMode, mAudioManager.getRingerMode());
    }

    /**
     * Ensure adjusting volume when total silence zen mode is enabled does not affect
     * stream volumes.
     */
    @Test
    public void testAdjustVolumeInTotalSilenceMode() throws Exception {
        if (mSkipAutoVolumeTests) {
            // adjustStreamVolume is a no-op
            return;
        }
        assumeFalse(mSkipRingerTests);

        final int SILENCE_VOL = 0;
        final int prevVol = mAudioManager.getStreamVolume(STREAM_MUSIC);
        Utils.toggleNotificationPolicyAccess(mContext.getPackageName(), getInstrumentation(), true);

        // Set to silence
        setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE);
        assertThat(mAudioManager.getStreamVolume(STREAM_MUSIC)).isEqualTo(SILENCE_VOL);

        // Raise shouldn't work when silenced
        assertCallDoesNotChangeStreamVolume(
                () -> mAudioManager.adjustStreamVolume(STREAM_MUSIC, ADJUST_RAISE, 0 /* flags */),
                STREAM_MUSIC);

        // Set the mode out of silence
        setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL);

        // Volume should be back to normal
        assertThat(mAudioManager.getStreamVolume(STREAM_MUSIC)).isEqualTo(INIT_VOL);

        final int MEDIA_DELTA = getVolumeDelta(mAudioManager.getStreamVolume(STREAM_MUSIC));
        assertCallChangesStreamVolume(
                () -> mAudioManager.adjustStreamVolume(STREAM_MUSIC, ADJUST_RAISE, 0 /* flags */),
                STREAM_MUSIC,
                INIT_VOL + MEDIA_DELTA);
    }

    @Test
    public void testAdjustVolumeInAlarmsOnlyMode() throws Exception {
        assumeFalse(mSkipRingerTests);

        Utils.toggleNotificationPolicyAccess(
                mContext.getPackageName(), getInstrumentation(), true);

        setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS);

        int volumeDelta = getVolumeDelta(mAudioManager.getStreamVolume(STREAM_MUSIC));

        // Why should this go through? This call doesn't exit zen mode, for reasons...
        assertCallChangesStreamVolume(
                () -> mAudioManager.adjustStreamVolume(STREAM_MUSIC, ADJUST_RAISE, 0),
                STREAM_MUSIC,
                INIT_VOL + volumeDelta,
                "Changing music volume should work when in alarm only mode");
    }

    @Test
    public void testSetStreamVolumeInTotalSilenceMode() throws Exception {
        assumeFalse(mSkipRingerTests);
        Utils.toggleNotificationPolicyAccess(
                mContext.getPackageName(), getInstrumentation(), true);

        setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE);

        // cannot adjust music, can adjust ringer since it could exit DND
        assertCallDoesNotChangeStreamVolume(
                () -> mAudioManager.setStreamVolume(STREAM_MUSIC, 7, 0),
                STREAM_MUSIC,
                "Should not be able to adjust media volume in Zen mode");
        assertCallChangesStreamVolume(
                () -> mAudioManager.setStreamVolume(STREAM_RING, 7, 0),
                STREAM_RING,
                7,
                "Should be able to adjust ring volume in Zen mode");
    }

    @Test
    public void testSetStreamVolumeInAlarmsOnlyMode() throws Exception {
        assumeFalse(mSkipRingerTests);
        Utils.toggleNotificationPolicyAccess(
                mContext.getPackageName(), getInstrumentation(), true);

        setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS);

        // can still adjust music and ring volume
        assertCallChangesStreamVolume(
                () -> mAudioManager.setStreamVolume(STREAM_MUSIC, 3, 0),
                STREAM_MUSIC,
                3,
                "Stream volume settable in alarm only zen");
        assertCallChangesStreamVolume(
                () -> mAudioManager.setStreamVolume(STREAM_RING, 7, 0),
                STREAM_RING,
                7,
                "Stream volume settable in alarm only zen");

    }

    @Test
    public void testSetStreamVolumeInPriorityOnlyMode() throws Exception {
        assumeFalse(mSkipRingerTests);
        Utils.toggleNotificationPolicyAccess(
                mContext.getPackageName(), getInstrumentation(), true);

        final int testRingerVol = getTestRingerVol();

        // disallow all sounds in priority only, turn on priority only DND
        mNm.setNotificationPolicy(new NotificationManager.Policy(0, 0 , 0));
        setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY);

        // attempt to change volume
        assertCallDoesNotChangeStreamVolume(
                () -> mAudioManager.setStreamVolume(STREAM_MUSIC, 3, 0),
                STREAM_MUSIC,
                "Should not be able to change MUSIC volume in priority zen");
        assertCallDoesNotChangeStreamVolume(
                () ->  mAudioManager.setStreamVolume(STREAM_ALARM, 5, 0),
                STREAM_ALARM,
                "Should not be able to change ALARM volume in priority zen");

        assertCallChangesStreamVolume(
                () -> mAudioManager.setStreamVolume(STREAM_RING, testRingerVol, 0),
                STREAM_RING,
                testRingerVol,
                "Should be able to set ring volume in zen");


        // Turn off zen to evaluate stream vols following zen
        setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL);

        assertEquals(INIT_VOL, mAudioManager.getStreamVolume(STREAM_MUSIC));
        assertEquals(INIT_VOL, mAudioManager.getStreamVolume(STREAM_ALARM));
        assertEquals(testRingerVol, mAudioManager.getStreamVolume(STREAM_RING));
    }

    @Test
    public void testAdjustVolumeInPriorityOnly() throws Exception {
        assumeFalse(mSkipRingerTests);
        Utils.toggleNotificationPolicyAccess(
                mContext.getPackageName(), getInstrumentation(), true);

        // disallow all sounds in priority only, turn on priority only DND, try to change volume
        mNm.setNotificationPolicy(new NotificationManager.Policy(0, 0, 0));
        setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY);


        int volumeDelta = getVolumeDelta(mAudioManager.getStreamVolume(STREAM_RING));
        assertCallDoesNotChangeStreamVolume(
                () -> mAudioManager.adjustStreamVolume(STREAM_MUSIC, ADJUST_RAISE, 0),
                STREAM_MUSIC,
                "Should not be able to set music vol in zen");

        assertCallDoesNotChangeStreamVolume(
                () -> mAudioManager.adjustStreamVolume(STREAM_ALARM, ADJUST_RAISE, 0),
                STREAM_ALARM,
                "Should not be able to set alarm vol in zen");

        assertCallChangesStreamVolume(
                () -> mAudioManager.adjustStreamVolume(STREAM_RING, ADJUST_RAISE, 0),
                STREAM_RING,
                INIT_VOL + volumeDelta,
                "Should be able to set ring volume in zen");

        // Turn off zen and make sure stream levels are still the same prior to zen
        // aside from ringer since ringer can exit dnd
        setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL);

        assertEquals(INIT_VOL, mAudioManager.getStreamVolume(STREAM_MUSIC));
        assertEquals(INIT_VOL, mAudioManager.getStreamVolume(STREAM_ALARM));
        assertEquals(INIT_VOL + volumeDelta, mAudioManager.getStreamVolume(STREAM_RING));
    }

    @Test
    public void testPriorityOnlyMuteAll() throws Exception {
        assumeFalse(mSkipRingerTests);
        Utils.toggleNotificationPolicyAccess(
                mContext.getPackageName(), getInstrumentation(), true);
        Map<Integer, MuteStateTransition> expectedTransitions = Map.of(
                STREAM_MUSIC, new MuteStateTransition(false, true),
                STREAM_SYSTEM, new MuteStateTransition(false, true),
                STREAM_ALARM, new MuteStateTransition(false, true),
                // if channels cannot bypass DND, the Ringer stream should be muted, else it
                // shouldn't be muted
                STREAM_RING, new MuteStateTransition(false, !mAppsBypassingDnd));

        assertStreamMuteStateChange(() -> {
                    // disallow all sounds in priority only, turn on priority only DND
                    mNm.setNotificationPolicy(new NotificationManager.Policy(0, 0, 0));
                    setInterruptionFilter( NotificationManager.INTERRUPTION_FILTER_PRIORITY);
                },
                expectedTransitions,
                "Priority mute all should mute all streams including ringer if" +
                "channels cannot bypass DND");
    }

    @Test
    public void testPriorityOnlyMediaAllowed() throws Exception {
        assumeFalse(mSkipRingerTests);
        Utils.toggleNotificationPolicyAccess(
                mContext.getPackageName(), getInstrumentation(), true);

        Map<Integer, MuteStateTransition> expectedTransitions = Map.of(
                STREAM_MUSIC, new MuteStateTransition(false, false),
                STREAM_SYSTEM, new MuteStateTransition(false, true),
                STREAM_ALARM, new MuteStateTransition(false, true),
                STREAM_RING, new MuteStateTransition(false, !mAppsBypassingDnd));
        assertStreamMuteStateChange(() -> {
                    // allow only media in priority only
                    mNm.setNotificationPolicy(new NotificationManager.Policy(
                            NotificationManager.Policy.PRIORITY_CATEGORY_MEDIA, 0, 0));
                    setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY);
                },
                expectedTransitions,
                "Priority category media should leave media unmuted, and rest muted");
    }

    @Test
    public void testPriorityOnlySystemAllowed() throws Exception {
        assumeFalse(mSkipRingerTests);
        Utils.toggleNotificationPolicyAccess(
                mContext.getPackageName(), getInstrumentation(), true);

        Map<Integer, MuteStateTransition> expectedTransitions = Map.of(
                STREAM_MUSIC, new MuteStateTransition(false, true),
                STREAM_SYSTEM, new MuteStateTransition(false, false),
                STREAM_ALARM, new MuteStateTransition(false, true),
                STREAM_RING, new MuteStateTransition(false, false));

        assertStreamMuteStateChange(() -> {
                    // allow only system in priority only
                    mNm.setNotificationPolicy(new NotificationManager.Policy(
                            NotificationManager.Policy.PRIORITY_CATEGORY_SYSTEM, 0, 0));
                    setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY);
                },
                expectedTransitions,
                "PRIORITY_CATEGORY_SYSTEM should leave RING and SYSTEM unmuted");
    }

    @Test
    public void testPriorityOnlySystemDisallowedWithRingerMuted() throws Exception {
        assumeFalse(mSkipRingerTests);

        Utils.toggleNotificationPolicyAccess(
                mContext.getPackageName(), getInstrumentation(), true);
        Map<Integer, MuteStateTransition> expectedSilentTransition = Map.of(
                STREAM_MUSIC, new MuteStateTransition(false, false),
                STREAM_SYSTEM, new MuteStateTransition(false, true),
                STREAM_ALARM, new MuteStateTransition(false, false),
                STREAM_RING, new MuteStateTransition(false, true));

        assertStreamMuteStateChange(() -> {
                    mAudioManager.setStreamVolume(STREAM_RING, 0, 0);
                    mAudioManager.setRingerMode(RINGER_MODE_SILENT);
                },
                expectedSilentTransition,
                "RING/SYSTEM should be silenced by RINGER_MODE");

        Map<Integer, MuteStateTransition> expectedTransitions = Map.of(
                STREAM_MUSIC, new MuteStateTransition(false, true),
                STREAM_SYSTEM, new MuteStateTransition(true, true),
                STREAM_ALARM, new MuteStateTransition(false, true),
                STREAM_RING, new MuteStateTransition(true, true));

        assertStreamMuteStateChange(() -> {
                // allow only system in priority only
                mNm.setNotificationPolicy(new NotificationManager.Policy(
                        NotificationManager.Policy.PRIORITY_CATEGORY_SYSTEM, 0, 0));
                setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY);
            },
            expectedTransitions,
            "SYSTEM/RING should stay muted if RINGER_MODE_SILENT entering zen");
    }

    @Test
    public void testPriorityOnlyAlarmsAllowed() throws Exception {
        assumeFalse(mSkipRingerTests);

        Utils.toggleNotificationPolicyAccess(
                mContext.getPackageName(), getInstrumentation(), true);

        Map<Integer, MuteStateTransition> expectedTransitions = Map.of(
                STREAM_MUSIC, new MuteStateTransition(false, true),
                STREAM_SYSTEM, new MuteStateTransition(false, true),
                STREAM_ALARM, new MuteStateTransition(false, false),
                // if channels cannot bypass DND, the Ringer stream should be muted, else it
                // shouldn't be muted
                STREAM_RING, new MuteStateTransition(false, !mAppsBypassingDnd));


        assertStreamMuteStateChange(() -> {
                    // allow only alarms in priority only
                    mNm.setNotificationPolicy(new NotificationManager.Policy(
                            NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS, 0, 0));
                    setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY);
                },
                expectedTransitions,
                "Alarm stream should be unmuted, all others muted");
    }

    @Test
    public void testPriorityOnlyRingerAllowed() throws Exception {
        assumeFalse(mSkipRingerTests);

        Utils.toggleNotificationPolicyAccess(
                mContext.getPackageName(), getInstrumentation(), true);

        Map<Integer, MuteStateTransition> expectedTransitions = Map.of(
                STREAM_MUSIC, new MuteStateTransition(false, true),
                STREAM_SYSTEM, new MuteStateTransition(false, true),
                STREAM_ALARM, new MuteStateTransition(false, true),
                STREAM_RING, new MuteStateTransition(false, false));

        assertStreamMuteStateChange(() -> {
                    // allow only reminders in priority only
                    mNm.setNotificationPolicy(new NotificationManager.Policy(
                            NotificationManager.Policy.PRIORITY_CATEGORY_REMINDERS, 0, 0));
                    setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY);
                },
                expectedTransitions,
                "All streams except ring should be unmuted");
    }

    @Test
    public void testPriorityOnlyChannelsCanBypassDnd() throws Exception {
        assumeFalse(mSkipRingerTests);

        Utils.toggleNotificationPolicyAccess(
                mContext.getPackageName(), getInstrumentation(), true);

        final String NOTIFICATION_CHANNEL_ID = "test_id_" + SystemClock.uptimeMillis();
        NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, "TEST",
                NotificationManager.IMPORTANCE_DEFAULT);
        try {

            // create a channel that can bypass dnd
            channel.setBypassDnd(true);
            mNm.createNotificationChannel(channel);
            Map<Integer, MuteStateTransition> expectedTransitions = Map.of(
                    STREAM_MUSIC, new MuteStateTransition(false, true),
                    STREAM_SYSTEM, new MuteStateTransition(false, true),
                    STREAM_ALARM, new MuteStateTransition(false, true),
                    STREAM_RING, new MuteStateTransition(false, false));

            // allow nothing
            assertStreamMuteStateChange(() -> {
                            mNm.setNotificationPolicy(new NotificationManager.Policy(0,0, 0));
                            setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY);
                    },
                    expectedTransitions,
                    "Ringer stream should not be muted."
                            + " areChannelsBypassing="
                            + NotificationManager.getService().areChannelsBypassingDnd());

            // delete the channel that can bypass dnd
            Map<Integer, MuteStateTransition> expectedTransitionsDeleteChannel = Map.of(
                    STREAM_MUSIC, new MuteStateTransition(true, true),
                    STREAM_SYSTEM, new MuteStateTransition(true, true),
                    STREAM_ALARM, new MuteStateTransition(true, true),
                    // if channels cannot bypass DND, the Ringer stream should be muted, else it
                    // shouldn't be muted
                    STREAM_RING, new MuteStateTransition(false, !mAppsBypassingDnd));

            assertStreamMuteStateChange(() -> mNm.deleteNotificationChannel(
                        NOTIFICATION_CHANNEL_ID),
                    expectedTransitionsDeleteChannel,
                    "Ringer stream should be muted if apps are not bypassing dnd"
                            + " areChannelsBypassing="
                            + NotificationManager.getService().areChannelsBypassingDnd());
        } finally {
            mNm.deleteNotificationChannel(NOTIFICATION_CHANNEL_ID);
        }
    }

    @Test
    public void testAdjustVolumeWithIllegalDirection() throws Exception {
        if (mSkipAutoVolumeTests) {
            // adjustVolume is a no-op
            return;
        }
        // Call the method with illegal direction. System should not reboot.
        mAudioManager.adjustVolume(37, 0);
    }

    @Test
    public void testGetStreamVolumeDbWithIllegalArguments() throws Exception {
        Exception ex = null;
        // invalid stream type
        try {
            float gain = mAudioManager.getStreamVolumeDb(-100 /*streamType*/, 0,
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER);
        } catch (Exception e) {
            ex = e; // expected
        }
        assertNotNull("No exception was thrown for an invalid stream type", ex);
        assertEquals("Wrong exception thrown for invalid stream type",
                ex.getClass(), IllegalArgumentException.class);

        // invalid volume index
        ex = null;
        try {
            float gain = mAudioManager.getStreamVolumeDb(STREAM_MUSIC, -101 /*volume*/,
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER);
        } catch (Exception e) {
            ex = e; // expected
        }
        assertNotNull("No exception was thrown for an invalid volume index", ex);
        assertEquals("Wrong exception thrown for invalid volume index",
                ex.getClass(), IllegalArgumentException.class);

        // invalid out of range volume index
        ex = null;
        try {
            final int maxVol = mAudioManager.getStreamMaxVolume(STREAM_MUSIC);
            float gain = mAudioManager.getStreamVolumeDb(STREAM_MUSIC, maxVol + 1,
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER);
        } catch (Exception e) {
            ex = e; // expected
        }
        assertNotNull("No exception was thrown for an invalid out of range volume index", ex);
        assertEquals("Wrong exception thrown for invalid out of range volume index",
                ex.getClass(), IllegalArgumentException.class);

        // invalid device type
        ex = null;
        try {
            float gain = mAudioManager.getStreamVolumeDb(STREAM_MUSIC, 0,
                    -102 /*deviceType*/);
        } catch (Exception e) {
            ex = e; // expected
        }
        assertNotNull("No exception was thrown for an invalid device type", ex);
        assertEquals("Wrong exception thrown for invalid device type",
                ex.getClass(), IllegalArgumentException.class);

        // invalid input device type
        ex = null;
        try {
            float gain = mAudioManager.getStreamVolumeDb(STREAM_MUSIC, 0,
                    AudioDeviceInfo.TYPE_BUILTIN_MIC);
        } catch (Exception e) {
            ex = e; // expected
        }
        assertNotNull("No exception was thrown for an invalid input device type", ex);
        assertEquals("Wrong exception thrown for invalid input device type",
                ex.getClass(), IllegalArgumentException.class);
    }

    @Test
    public void testGetStreamVolumeDb() throws Exception {
        for (int streamType : PUBLIC_STREAM_TYPES) {
            // verify mininum index is strictly inferior to maximum index
            final int minIndex = mAudioManager.getStreamMinVolume(streamType);
            final int maxIndex = mAudioManager.getStreamMaxVolume(streamType);
            assertTrue("Min vol index (" + minIndex + ") for stream " + streamType + " not inferior"
                    + " to max vol index (" + maxIndex + ")", minIndex <= maxIndex);
            float prevGain = Float.NEGATIVE_INFINITY;
            // verify gain increases with the volume indices
            for (int idx = minIndex ; idx <= maxIndex ; idx++) {
                float gain = mAudioManager.getStreamVolumeDb(streamType, idx,
                        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER);
                assertTrue("Non-monotonically increasing gain at index " + idx + " for stream"
                        + streamType, prevGain <= gain);
                prevGain = gain;
            }
        }
    }

    @Test
    public void testAdjustSuggestedStreamVolumeWithIllegalArguments() throws Exception {
        // Call the method with illegal direction. System should not reboot.
        mAudioManager.adjustSuggestedStreamVolume(37, STREAM_MUSIC, 0);

        // Call the method with illegal stream. System should not reboot.
        mAudioManager.adjustSuggestedStreamVolume(ADJUST_RAISE, 66747, 0);
    }

    @CddTest(requirement="5.4.1/C-1-4")
    @Test
    public void testGetMicrophones() throws Exception {
        if (!mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_MICROPHONE)) {
            return;
        }
        List<MicrophoneInfo> microphones = mAudioManager.getMicrophones();
        assertTrue(microphones.size() > 0);
        for (int i = 0; i < microphones.size(); i++) {
            MicrophoneInfo microphone = microphones.get(i);
            Log.i(TAG, "deviceId:" + microphone.getDescription());
            Log.i(TAG, "portId:" + microphone.getId());
            Log.i(TAG, "type:" + microphone.getType());
            Log.i(TAG, "address:" + microphone.getAddress());
            Log.i(TAG, "deviceLocation:" + microphone.getLocation());
            Log.i(TAG, "deviceGroup:" + microphone.getGroup()
                    + " index:" + microphone.getIndexInTheGroup());
            MicrophoneInfo.Coordinate3F position = microphone.getPosition();
            Log.i(TAG, "position:" + position.x + " " + position.y + " " + position.z);
            MicrophoneInfo.Coordinate3F orientation = microphone.getOrientation();
            Log.i(TAG, "orientation:" + orientation.x + " "
                    + orientation.y + " " + orientation.z);
            Log.i(TAG, "frequencyResponse:" + microphone.getFrequencyResponse());
            Log.i(TAG, "channelMapping:" + microphone.getChannelMapping());
            Log.i(TAG, "sensitivity:" + microphone.getSensitivity());
            Log.i(TAG, "max spl:" + microphone.getMaxSpl());
            Log.i(TAG, "min spl:" + microphone.getMinSpl());
            Log.i(TAG, "directionality:" + microphone.getDirectionality());
            Log.i(TAG, "--------------");
        }
    }

    @Test
    public void testIsHapticPlaybackSupported() {
        // Calling the API to make sure it doesn't crash.
        Log.i(TAG, "isHapticPlaybackSupported: " + AudioManager.isHapticPlaybackSupported());
    }

    @Test
    public void testIsUltrasoundSupported() {
        // Calling the API to make sure it must crash due to no permission.
        try {
            mAudioManager.isUltrasoundSupported();
            fail("isUltrasoundSupported must fail due to no permission");
        } catch (SecurityException e) {
        }
    }

    @Test
    public void testIsHotwordStreamSupported() {
        // Validate API requires permission
        assertThrows(SecurityException.class, () -> mAudioManager.isHotwordStreamSupported(false));
        assertThrows(SecurityException.class, () -> mAudioManager.isHotwordStreamSupported(true));
        // Validate functionality when caller holds appropriate permissions
        InstrumentationRegistry.getInstrumentation()
                               .getUiAutomation()
                               .adoptShellPermissionIdentity(
                                Manifest.permission.CAPTURE_AUDIO_HOTWORD);
        boolean result1 = mAudioManager.isHotwordStreamSupported(false);
        boolean result2 = mAudioManager.isHotwordStreamSupported(true);

        InstrumentationRegistry.getInstrumentation()
                               .getUiAutomation()
                               .dropShellPermissionIdentity();
    }

    @Test
    public void testGetAudioHwSyncForSession() {
        // AudioManager.getAudioHwSyncForSession is not supported before S
        if (ApiLevelUtil.isAtMost(Build.VERSION_CODES.R)) {
            Log.i(TAG, "testGetAudioHwSyncForSession skipped, release: " + Build.VERSION.SDK_INT);
            return;
        }
        try {
            int sessionId = mAudioManager.generateAudioSessionId();
            assertNotEquals("testGetAudioHwSyncForSession cannot get audio session ID",
                    AudioManager.ERROR, sessionId);
            int hwSyncId = mAudioManager.getAudioHwSyncForSession(sessionId);
            Log.i(TAG, "getAudioHwSyncForSession: " + hwSyncId);
        } catch (UnsupportedOperationException e) {
            Log.i(TAG, "getAudioHwSyncForSession not supported");
        } catch (Exception e) {
            fail("Unexpected exception thrown by getAudioHwSyncForSession: " + e);
        }
    }

    private void setInterruptionFilter(int filter) throws Exception {
        // TODO (b/294941884) investigate uncommenting this
        /*
        assertWithMessage("Setting interruption filter relies on unset ringer mode")
                .that(mAudioManager.getRingerMode())
                .isEqualTo(AudioManager.RINGER_MODE_NORMAL);
        */

        if (mNm.getCurrentInterruptionFilter() == filter) {
            return;
        }
        final int expectedRingerMode = switch(filter) {
                case NotificationManager.INTERRUPTION_FILTER_NONE,
                     NotificationManager.INTERRUPTION_FILTER_PRIORITY,
                     NotificationManager.INTERRUPTION_FILTER_ALARMS
                         -> AudioManager.RINGER_MODE_SILENT;
                case NotificationManager.INTERRUPTION_FILTER_ALL -> AudioManager.RINGER_MODE_NORMAL;
                default -> throw new AssertionError("Unexpected notification type");
        };


        var future = mCancelRule.registerFuture(getFutureForIntent(
                mContext,
                AudioManager.RINGER_MODE_CHANGED_ACTION,
                i -> (i != null)
                        && i.getIntExtra(AudioManager.EXTRA_RINGER_MODE, -1)
                    == expectedRingerMode));
        mNm.setInterruptionFilter(filter);
        var intent = future.get(FUTURE_WAIT_SECS, TimeUnit.SECONDS);
    }

    private int getVolumeDelta(int volume) {
        return 1;
    }

    private int getTestRingerVol() {
        final int currentRingVol = mAudioManager.getStreamVolume(STREAM_RING);
        final int maxRingVol = mAudioManager.getStreamMaxVolume(STREAM_RING);
        if (currentRingVol != maxRingVol) {
            return maxRingVol;
        } else {
            return maxRingVol - 1;
        }
    }

    @Test
    public void testAllowedCapturePolicy() throws Exception {
        final int policy = mAudioManager.getAllowedCapturePolicy();
        assertEquals("Wrong default capture policy", AudioAttributes.ALLOW_CAPTURE_BY_ALL, policy);

        for (int setPolicy : new int[] { AudioAttributes.ALLOW_CAPTURE_BY_NONE,
                                      AudioAttributes.ALLOW_CAPTURE_BY_SYSTEM,
                                      AudioAttributes.ALLOW_CAPTURE_BY_ALL}) {
            mAudioManager.setAllowedCapturePolicy(setPolicy);
            final int getPolicy = mAudioManager.getAllowedCapturePolicy();
            assertEquals("Allowed capture policy doesn't match", setPolicy, getPolicy);
        }
    }

    @Test
    public void testIsHdmiSystemAudidoSupported() {
        // just make sure the call works
        boolean isSupported = mAudioManager.isHdmiSystemAudioSupported();
        Log.d(TAG, "isHdmiSystemAudioSupported() = " + isSupported);
    }

    @Test
    public void testIsBluetoothScoAvailableOffCall() {
        // just make sure the call works
        boolean isSupported = mAudioManager.isBluetoothScoAvailableOffCall();
        Log.d(TAG, "isBluetoothScoAvailableOffCall() = " + isSupported);
    }

    @Test
    public void testStartStopBluetoothSco() {
        mAudioManager.startBluetoothSco();
        mAudioManager.stopBluetoothSco();
    }

    @Test
    public void testStartStopBluetoothScoVirtualCall() {
        mAudioManager.startBluetoothScoVirtualCall();
        mAudioManager.stopBluetoothSco();
    }

    @Test
    public void testGetAdditionalOutputDeviceDelay() {
        AudioDeviceInfo[] devices = mAudioManager.getDevices(AudioManager.GET_DEVICES_ALL);
        for (AudioDeviceInfo device : devices) {
            long delay = mAudioManager.getAdditionalOutputDeviceDelay(device);
            assertTrue("getAdditionalOutputDeviceDelay() = " + delay +" (should be >= 0)",
                    delay >= 0);
            delay = mAudioManager.getMaxAdditionalOutputDeviceDelay(device);
            assertTrue("getMaxAdditionalOutputDeviceDelay() = " + delay +" (should be >= 0)",
                    delay >= 0);
        }
    }

    static class MyPrevDevForStrategyListener implements
            AudioManager.OnPreferredDevicesForStrategyChangedListener {
        @Override
        public void onPreferredDevicesForStrategyChanged(AudioProductStrategy strategy,
                List<AudioDeviceAttributes> devices) {
            fail("onPreferredDevicesForStrategyChanged must not be called");
        }
    }

    @Test
    public void testPreferredDevicesForStrategy() {
        // setPreferredDeviceForStrategy
        AudioDeviceInfo[] devices = mAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        if (devices.length <= 0) {
            Log.i(TAG, "Skip testPreferredDevicesForStrategy as there is no output device");
            return;
        }
        final AudioDeviceAttributes ada = new AudioDeviceAttributes(devices[0]);

        final AudioAttributes mediaAttr = new AudioAttributes.Builder().setUsage(
                AudioAttributes.USAGE_MEDIA).build();
        final List<AudioProductStrategy> strategies =
                AudioProductStrategy.getAudioProductStrategies();
        AudioProductStrategy strategyForMedia = null;
        for (AudioProductStrategy strategy : strategies) {
            if (strategy.supportsAudioAttributes(mediaAttr)) {
                strategyForMedia = strategy;
                break;
            }
        }
        if (strategyForMedia == null) {
            Log.i(TAG, "Skip testPreferredDevicesForStrategy as there is no strategy for media");
            return;
        }
        Log.i(TAG, "Found strategy " + strategyForMedia.getName() + " for media");
        try {
            mAudioManager.setPreferredDeviceForStrategy(strategyForMedia, ada);
            fail("setPreferredDeviceForStrategy must fail due to no permission");
        } catch (SecurityException e) {
        }
        try {
            mAudioManager.getPreferredDeviceForStrategy(strategyForMedia);
            fail("getPreferredDeviceForStrategy must fail due to no permission");
        } catch (SecurityException e) {
        }
        final List<AudioDeviceAttributes> adas = new ArrayList<>();
        adas.add(ada);
        try {
            mAudioManager.setPreferredDevicesForStrategy(strategyForMedia, adas);
            fail("setPreferredDevicesForStrategy must fail due to no permission");
        } catch (SecurityException e) {
        }
        try {
            mAudioManager.getPreferredDevicesForStrategy(strategyForMedia);
            fail("getPreferredDevicesForStrategy must fail due to no permission");
        } catch (SecurityException e) {
        }
        MyPrevDevForStrategyListener listener = new MyPrevDevForStrategyListener();
        try {
            mAudioManager.addOnPreferredDevicesForStrategyChangedListener(
                    Executors.newSingleThreadExecutor(), listener);
            fail("addOnPreferredDevicesForStrategyChangedListener must fail due to no permission");
        } catch (SecurityException e) {
        }
        try {
            // removeOnPreferredDevicesForStrategyChangedListener should throw on non-registered
            // listener.
            mAudioManager.removeOnPreferredDevicesForStrategyChangedListener(listener);
            fail("removeOnPreferredDevicesForStrategyChangedListener must fail on bad listener");
        } catch (IllegalArgumentException e) {
        }
    }

    static class MyPrevDevicesForCapturePresetChangedListener implements
            AudioManager.OnPreferredDevicesForCapturePresetChangedListener {
        @Override
        public void onPreferredDevicesForCapturePresetChanged(
                int capturePreset, List<AudioDeviceAttributes> devices) {
            fail("onPreferredDevicesForCapturePresetChanged must not be called");
        }
    }

    @Test
    public void testPreferredDeviceForCapturePreset() {
        AudioDeviceInfo[] devices = mAudioManager.getDevices(AudioManager.GET_DEVICES_INPUTS);
        if (devices.length <= 0) {
            Log.i(TAG, "Skip testPreferredDevicesForStrategy as there is no input device");
            return;
        }
        final AudioDeviceAttributes ada = new AudioDeviceAttributes(devices[0]);

        try {
            mAudioManager.setPreferredDeviceForCapturePreset(MediaRecorder.AudioSource.MIC, ada);
            fail("setPreferredDeviceForCapturePreset must fail due to no permission");
        } catch (SecurityException e) {
        }
        try {
            mAudioManager.getPreferredDevicesForCapturePreset(MediaRecorder.AudioSource.MIC);
            fail("getPreferredDevicesForCapturePreset must fail due to no permission");
        } catch (SecurityException e) {
        }
        try {
            mAudioManager.clearPreferredDevicesForCapturePreset(MediaRecorder.AudioSource.MIC);
            fail("clearPreferredDevicesForCapturePreset must fail due to no permission");
        } catch (SecurityException e) {
        }
        MyPrevDevicesForCapturePresetChangedListener listener =
                new MyPrevDevicesForCapturePresetChangedListener();
        try {
            mAudioManager.addOnPreferredDevicesForCapturePresetChangedListener(
                Executors.newSingleThreadExecutor(), listener);
            fail("addOnPreferredDevicesForCapturePresetChangedListener must fail"
                    + "due to no permission");
        } catch (SecurityException e) {
        }
        // There is not listener added at server side. Nothing to remove.
        mAudioManager.removeOnPreferredDevicesForCapturePresetChangedListener(listener);
    }

    @Test
    public void testGetDevices() {
        AudioDeviceInfo[] devices = mAudioManager.getDevices(AudioManager.GET_DEVICES_ALL);
        for (AudioDeviceInfo device : devices) {
            Set<Integer> formats = IntStream.of(device.getEncodings()).boxed()
                    .collect(Collectors.toSet());
            Set<Integer> channelMasks = IntStream.of(device.getChannelMasks()).boxed()
                    .collect(Collectors.toSet());
            Set<Integer> channelIndexMasks = IntStream.of(device.getChannelIndexMasks()).boxed()
                    .collect(Collectors.toSet());
            Set<Integer> sampleRates = IntStream.of(device.getSampleRates()).boxed()
                    .collect(Collectors.toSet());
            HashSet<Integer> formatsFromProfile = new HashSet<>();
            HashSet<Integer> channelMasksFromProfile = new HashSet<>();
            HashSet<Integer> channelIndexMasksFromProfile = new HashSet<>();
            HashSet<Integer> sampleRatesFromProfile = new HashSet<>();
            for (AudioProfile profile : device.getAudioProfiles()) {
                formatsFromProfile.add(profile.getFormat());
                channelMasksFromProfile.addAll(Arrays.stream(profile.getChannelMasks()).boxed()
                        .collect(Collectors.toList()));
                channelIndexMasksFromProfile.addAll(Arrays.stream(profile.getChannelIndexMasks())
                        .boxed().collect(Collectors.toList()));
                sampleRatesFromProfile.addAll(Arrays.stream(profile.getSampleRates()).boxed()
                        .collect(Collectors.toList()));
                assertTrue(ALL_ENCAPSULATION_TYPES.contains(profile.getEncapsulationType()));
            }
            for (AudioDescriptor descriptor : device.getAudioDescriptors()) {
                assertNotEquals(AudioDescriptor.STANDARD_NONE, descriptor.getStandard());
                assertNotNull(descriptor.getDescriptor());
                assertTrue(
                        ALL_KNOWN_ENCAPSULATION_TYPES.contains(descriptor.getEncapsulationType()));
            }
            assertEquals(formats, formatsFromProfile);
            assertEquals(channelMasks, channelMasksFromProfile);
            assertEquals(channelIndexMasks, channelIndexMasksFromProfile);
            assertEquals(sampleRates, sampleRatesFromProfile);
        }
    }

    @Test
    @RequiresFlagsEnabled(value = Flags.FLAG_SUPPORTED_DEVICE_TYPES_API)
    public void testGetSupportedDeviceTypes() {
        Set<Integer> deviceTypesOutputs =
                mAudioManager.getSupportedDeviceTypes(AudioManager.GET_DEVICES_OUTPUTS);
        assertNotEquals(deviceTypesOutputs, null);

        if (AudioTestUtil.hasAudioOutput(mContext)) {
            assertNotEquals(deviceTypesOutputs.size(), 0);
        } else {
            assertEquals(deviceTypesOutputs.size(), 0);
        }

        Set<Integer> deviceTypesInputs =
                mAudioManager.getSupportedDeviceTypes(AudioManager.GET_DEVICES_INPUTS);
        assertNotEquals(deviceTypesInputs, null);

        if (AudioTestUtil.hasAudioInput(mContext)) {
            assertNotEquals(deviceTypesInputs.size(), 0);
        } else {
            // We can't really check this.
            // We are not sure of the equivalence of has "microphone" and "never support audio
            // inputs". For instance an android device could support input devices like HDMI IN
            // but not have a microphone.
            // assertEquals(deviceTypesInputs.size(), 0);
        }
    }

    @Test
    public void testGetDirectPlaybackSupport() {
        assertEquals(AudioManager.DIRECT_PLAYBACK_NOT_SUPPORTED,
                AudioManager.getDirectPlaybackSupport(
                        new AudioFormat.Builder().build(),
                        new AudioAttributes.Builder().build()));
        AudioDeviceInfo[] devices = mAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        AudioAttributes attr = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setLegacyStreamType(STREAM_MUSIC).build();
        for (AudioDeviceInfo device : devices) {
            for (int encoding : device.getEncodings()) {
                for (int channelMask : device.getChannelMasks()) {
                    for (int sampleRate : device.getSampleRates()) {
                        AudioFormat format = new AudioFormat.Builder()
                                .setEncoding(encoding)
                                .setChannelMask(channelMask)
                                .setSampleRate(sampleRate).build();
                        final int directPlaybackSupport =
                                AudioManager.getDirectPlaybackSupport(format, attr);
                        assertEquals(
                                AudioTrack.isDirectPlaybackSupported(format, attr),
                                directPlaybackSupport
                                        != AudioManager.DIRECT_PLAYBACK_NOT_SUPPORTED);
                        if (directPlaybackSupport == AudioManager.DIRECT_PLAYBACK_NOT_SUPPORTED) {
                            assertEquals(
                                    DIRECT_OFFLOAD_MAP.getOrDefault(
                                            AudioManager.getPlaybackOffloadSupport(format, attr),
                                            INVALID_DIRECT_PLAYBACK_MODE).intValue(),
                                    directPlaybackSupport);
                        } else if ((directPlaybackSupport
                                & AudioManager.DIRECT_PLAYBACK_OFFLOAD_SUPPORTED)
                                != AudioManager.DIRECT_PLAYBACK_NOT_SUPPORTED) {
                            // AudioManager.getPlaybackOffloadSupport can only query offload
                            // support but not other direct support like passthrough.
                            assertNotEquals(
                                    AudioManager.DIRECT_PLAYBACK_NOT_SUPPORTED,
                                    DIRECT_OFFLOAD_MAP.getOrDefault(
                                            AudioManager.getPlaybackOffloadSupport(format, attr),
                                            AudioManager.DIRECT_PLAYBACK_NOT_SUPPORTED)
                                            & directPlaybackSupport);
                        }
                    }
                }
            }
        }
    }

    @AppModeFull(reason = "Instant apps cannot hold permission.MODIFY_AUDIO_SETTINGS_PRIVILEGED")
    @Test
    public void testIndependentStreamTypes() throws Exception {
        Log.i(TAG, "starting testIndependentStreamTypes");
        getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.MODIFY_AUDIO_SETTINGS_PRIVILEGED);
        try {
            final List<Integer> independentStreamTypes = mAudioManager.getIndependentStreamTypes();
            assertNotNull("Null list of independent stream types", independentStreamTypes);
            final boolean usesGroups = mAudioManager.isVolumeControlUsingVolumeGroups();
            Log.i(TAG, "testIndependentStreamTypes: usesGroups:" + usesGroups
                    + " independentTypes" + independentStreamTypes);
            if (usesGroups) {
                assertTrue("Empty list of independent stream types with volume groups",
                        independentStreamTypes.size() > 0);
                return;
            }
            assertTrue("Unexpected number of independent stream types "
                    + independentStreamTypes.size(), independentStreamTypes.size() > 0);
            // verify independent streams are not aliased
            for (int indepStream : independentStreamTypes) {
                final int alias = mAudioManager.getStreamTypeAlias(indepStream);
                assertEquals("Independent stream " + indepStream + " has alias " + alias,
                        indepStream, alias);
            }
            // verify aliased streams are not independent, and non-aliased streams are
            for (int stream : PUBLIC_STREAM_TYPES) {
                final int alias = mAudioManager.getStreamTypeAlias(stream);
                if (alias != stream) {
                    assertFalse("Stream" + stream + " aliased to " + alias
                            + " but marked independent", independentStreamTypes.contains(stream));
                } else {
                    // independent stream
                    assertTrue("Stream " + stream
                            + " has no alias but is not marked as independent",
                            independentStreamTypes.contains(stream));
                }
            }
        } finally {
            getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
        }
    }

    @AppModeFull(reason = "Instant apps cannot hold permission.MODIFY_AUDIO_SETTINGS_PRIVILEGED")
    @Test
    public void testStreamTypeAliasChange() throws Exception {
        if (!mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            Log.i(TAG, "skipping testStreamTypeAliasChange, not a phone");
            return;
        }
        Log.i(TAG, "starting testStreamTypeAliasChange");
        getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.MODIFY_AUDIO_SETTINGS_PRIVILEGED);

        // get initial state
        final int notifAliasAtStart = mAudioManager.getStreamTypeAlias(STREAM_NOTIFICATION);
        if (notifAliasAtStart != STREAM_NOTIFICATION && notifAliasAtStart != STREAM_RING) {
            // skipping test because it can't take advantage of the test API to modify
            // the notification alias
            Log.i(TAG, "skipping testStreamTypeAliasChange: NOTIFICATION aliased to stream "
                    + notifAliasAtStart);
            return;
        }
        boolean notifAliasedToRingAtStart = (notifAliasAtStart == STREAM_RING);
        final MyBlockingRunnableListener streamAliasCb = new MyBlockingRunnableListener();
        Runnable onStreamAliasChanged = () -> streamAliasCb.onSomeEventThatsExpected();
        try {
            if (!notifAliasedToRingAtStart) {
                // if notif and ring are not aliased, they should each be independent streams
                final List<Integer> indies = mAudioManager.getIndependentStreamTypes();
                assertTrue("NOTIFICATION not in independent streams " + indies,
                        indies.contains(STREAM_NOTIFICATION));
                assertTrue("RING not in independent streams " + indies,
                        indies.contains(STREAM_RING));
            }
            mAudioManager.addOnStreamAliasingChangedListener(
                    Executors.newSingleThreadExecutor(),
                    onStreamAliasChanged);
            mAudioManager.setNotifAliasRingForTest(!notifAliasedToRingAtStart);
            final String aliasing = notifAliasedToRingAtStart ? "unaliasing" : "aliasing";
            assertTrue(aliasing + " RING and NOTIFICATION didn't trigger callback",
                    streamAliasCb.waitForExpectedEvent(TIME_TO_WAIT_CALLBACK_MS));
            final int expectedNotifAlias = notifAliasedToRingAtStart ? STREAM_NOTIFICATION
                    : STREAM_RING;
            assertEquals("After " + aliasing + " alias incorrect",
                    expectedNotifAlias, mAudioManager.getStreamTypeAlias(STREAM_NOTIFICATION));
            if (notifAliasedToRingAtStart) {
                // if notif and ring were aliased, they should now be independent streams
                final List<Integer> indies = mAudioManager.getIndependentStreamTypes();
                assertTrue("After alias change, NOTIFICATION not in independent streams "
                                + indies,
                        indies.contains(STREAM_NOTIFICATION));
                assertTrue("After alias change, RING not in independent streams " + indies,
                        indies.contains(STREAM_RING));
            }

        } finally {
            mAudioManager.setNotifAliasRingForTest(notifAliasedToRingAtStart);
            mAudioManager.removeOnStreamAliasingChangedListener(onStreamAliasChanged);
            getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
        }
    }

    @Test
    public void testAssistantUidRouting() {
        try {
            mAudioManager.addAssistantServicesUids(new int[0]);
            fail("addAssistantServicesUids must fail due to no permission");
        } catch (SecurityException e) {
        }

        try {
            mAudioManager.removeAssistantServicesUids(new int[0]);
            fail("removeAssistantServicesUids must fail due to no permission");
        } catch (SecurityException e) {
        }

        try {
            int[] uids = mAudioManager.getAssistantServicesUids();
            fail("getAssistantServicesUids must fail due to no permission");
        } catch (SecurityException e) {
        }

        try {
            mAudioManager.setActiveAssistantServiceUids(new int[0]);
            fail("setActiveAssistantServiceUids must fail due to no permission");
        } catch (SecurityException e) {
        }

        try {
            int[] activeUids = mAudioManager.getActiveAssistantServicesUids();
            fail("getActiveAssistantServicesUids must fail due to no permission");
        } catch (SecurityException e) {
        }
    }

    @AppModeFull(reason = "Instant apps cannot hold android.permission.MODIFY_AUDIO_ROUTING")
    @Test
    public void testBluetoothVariableLatency() throws Exception {
        assertThrows(SecurityException.class,
                () -> mAudioManager.supportsBluetoothVariableLatency());
        assertThrows(SecurityException.class,
                () -> mAudioManager.setBluetoothVariableLatencyEnabled(false));
        assertThrows(SecurityException.class,
                () -> mAudioManager.setBluetoothVariableLatencyEnabled(true));
        assertThrows(SecurityException.class,
                () -> mAudioManager.isBluetoothVariableLatencyEnabled());

        getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.MODIFY_AUDIO_ROUTING);
        if (mAudioManager.supportsBluetoothVariableLatency()) {
            boolean savedEnabled = mAudioManager.isBluetoothVariableLatencyEnabled();
            mAudioManager.setBluetoothVariableLatencyEnabled(false);
            assertFalse(mAudioManager.isBluetoothVariableLatencyEnabled());
            mAudioManager.setBluetoothVariableLatencyEnabled(true);
            assertTrue(mAudioManager.isBluetoothVariableLatencyEnabled());
            mAudioManager.setBluetoothVariableLatencyEnabled(savedEnabled);
        }
        getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
    }

    @Test
    public void testGetHalVersion() {
        AudioHalVersionInfo halVersion = AudioManager.getHalVersion();
        assertNotEquals(null, halVersion);
        assertTrue(
                AudioHalVersionInfo.AUDIO_HAL_TYPE_AIDL == halVersion.getHalType()
                        || AudioHalVersionInfo.AUDIO_HAL_TYPE_HIDL == halVersion.getHalType());
        assertTrue(halVersion.getMajorVersion() > 0);
        assertTrue(halVersion.getMinorVersion() >= 0);
    }

    @Test
    public void testPreferredMixerAttributes() throws Exception {
        final AudioAttributes attr = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA).build();
        final AudioMixerAttributes defaultMixerAttributes = new AudioMixerAttributes.Builder(
                new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .setSampleRate(48000)
                        .build())
                .setMixerBehavior(AudioMixerAttributes.MIXER_BEHAVIOR_DEFAULT)
                .build();

        for (AudioDeviceInfo device : mAudioManager.getDevices(AudioManager.GET_DEVICES_ALL)) {
            List<AudioMixerAttributes> supportedMixerAttributes =
                    mAudioManager.getSupportedMixerAttributes(device);
            if (supportedMixerAttributes.isEmpty()) {
                // Setting preferred mixer attributes is not supported
                assertFalse(mAudioManager.setPreferredMixerAttributes(
                        attr, device, defaultMixerAttributes));
            } else {
                for (AudioMixerAttributes mixerAttr : supportedMixerAttributes) {
                    assertNotNull(mixerAttr.getFormat());
                    assertTrue(ALL_MIXER_BEHAVIORS.contains(mixerAttr.getMixerBehavior()));
                    assertTrue(mAudioManager.setPreferredMixerAttributes(attr, device, mixerAttr));
                    waitForMixerAttrChanged(attr, device.getId());
                    final AudioMixerAttributes mixerAttrFromQuery =
                            mAudioManager.getPreferredMixerAttributes(attr, device);
                    assertEquals(mixerAttr, mixerAttrFromQuery);
                    assertTrue(mAudioManager.clearPreferredMixerAttributes(attr, device));
                    waitForMixerAttrChanged(attr, device.getId());
                    assertNull(mAudioManager.getPreferredMixerAttributes(attr, device));
                }
            }
        }
    }

    @Test
    public void testAdjustVolumeGroupVolume() {
        getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(
                Manifest.permission.MODIFY_AUDIO_SETTINGS_PRIVILEGED,
                Manifest.permission.MODIFY_AUDIO_ROUTING,
                Manifest.permission.QUERY_AUDIO_STATE,
                Manifest.permission.MODIFY_PHONE_STATE);

        List<AudioVolumeGroup> audioVolumeGroups = mAudioManager.getAudioVolumeGroups();
        assertTrue(audioVolumeGroups.size() > 0);

        final AudioAttributes callAa = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .build();
        int voiceCallVolumeGroup = mAudioManager.getVolumeGroupIdForAttributes(callAa);

        assertNotEquals(voiceCallVolumeGroup, AudioVolumeGroup.DEFAULT_VOLUME_GROUP);

        AudioVolumeGroupCallbackHelper vgCbReceiver = new AudioVolumeGroupCallbackHelper();
        mAudioManager.registerVolumeGroupCallback(mContext.getMainExecutor(), vgCbReceiver);

        try {
            // Validate Audio Volume Groups callback reception
            for (final AudioVolumeGroup audioVolumeGroup : audioVolumeGroups) {
                int volumeGroupId = audioVolumeGroup.getId();
                int[] avgStreamTypes = audioVolumeGroup.getLegacyStreamTypes();
                if (avgStreamTypes.length != 0) {
                    // filters out bijective as API is dispatched to stream.
                    // Following compatibility test will ensure API are dispatched
                    continue;
                }
                int indexMax = mAudioManager.getVolumeGroupMaxVolumeIndex(volumeGroupId);
                int indexMin = mAudioManager.getVolumeGroupMinVolumeIndex(volumeGroupId);
                boolean isMutable = (indexMin == 0) || (volumeGroupId == voiceCallVolumeGroup);

                // Set the receiver to filter only the current group callback
                int index = resetVolumeIndex(indexMin, indexMax);
                vgCbReceiver.setExpectedVolumeGroup(volumeGroupId);
                mAudioManager.setVolumeGroupVolumeIndex(volumeGroupId, index, 0/*flags*/);
                assertTrue(vgCbReceiver.waitForExpectedVolumeGroupChanged(
                        AudioVolumeGroupCallbackHelper.ASYNC_TIMEOUT_MS));
                int readIndex = mAudioManager.getVolumeGroupVolumeIndex(volumeGroupId);
                assertEquals("Failed to set volume for group id "
                        + volumeGroupId, readIndex, index);

                while (index < indexMax) {
                    vgCbReceiver.setExpectedVolumeGroup(volumeGroupId);
                    mAudioManager.adjustVolumeGroupVolume(
                            volumeGroupId, AudioManager.ADJUST_RAISE, 0/*flags*/);
                    assertTrue(vgCbReceiver.waitForExpectedVolumeGroupChanged(
                            AudioVolumeGroupCallbackHelper.ASYNC_TIMEOUT_MS));
                    readIndex = mAudioManager.getVolumeGroupVolumeIndex(volumeGroupId);
                    index += 1;
                    assertEquals(readIndex, index);
                }
                // Max reached
                vgCbReceiver.setExpectedVolumeGroup(volumeGroupId);
                mAudioManager.adjustVolumeGroupVolume(
                        volumeGroupId, AudioManager.ADJUST_RAISE, 0/*flags*/);
                assertTrue("Cb expected for group "
                        + volumeGroupId, vgCbReceiver.waitForExpectedVolumeGroupChanged(
                        AudioVolumeGroupCallbackHelper.ASYNC_TIMEOUT_MS));
                readIndex = mAudioManager.getVolumeGroupVolumeIndex(volumeGroupId);
                assertEquals(readIndex, indexMax);

                while (index > indexMin) {
                    vgCbReceiver.setExpectedVolumeGroup(volumeGroupId);
                    mAudioManager.adjustVolumeGroupVolume(
                            volumeGroupId, AudioManager.ADJUST_LOWER, 0/*flags*/);
                    assertTrue(vgCbReceiver.waitForExpectedVolumeGroupChanged(
                            AudioVolumeGroupCallbackHelper.ASYNC_TIMEOUT_MS));
                    index -= 1;
                    readIndex = mAudioManager.getVolumeGroupVolumeIndex(volumeGroupId);
                    assertEquals("Failed to decrease volume for group id "
                            + volumeGroupId, readIndex, index);
                }
                // Min reached
                vgCbReceiver.setExpectedVolumeGroup(volumeGroupId);
                mAudioManager.adjustVolumeGroupVolume(
                        volumeGroupId, AudioManager.ADJUST_LOWER, 0/*flags*/);
                assertTrue("Cb expected for group "
                        + volumeGroupId, vgCbReceiver.waitForExpectedVolumeGroupChanged(
                        AudioVolumeGroupCallbackHelper.ASYNC_TIMEOUT_MS));
                readIndex = mAudioManager.getVolumeGroupVolumeIndex(volumeGroupId);
                assertEquals("Failed to decrease volume for group id "
                        + volumeGroupId, readIndex, indexMin);

                // Mute/Unmute
                if (isMutable) {
                    int lastAudibleIndex;
                    index = resetVolumeIndex(indexMin, indexMax);
                    vgCbReceiver.setExpectedVolumeGroup(volumeGroupId);
                    mAudioManager.setVolumeGroupVolumeIndex(volumeGroupId, index, 0/*flags*/);
                    assertTrue(vgCbReceiver.waitForExpectedVolumeGroupChanged(
                            AudioVolumeGroupCallbackHelper.ASYNC_TIMEOUT_MS));

                    readIndex = mAudioManager.getVolumeGroupVolumeIndex(volumeGroupId);
                    assertEquals("Failed to set volume for group id "
                            + volumeGroupId, readIndex, index);

                    lastAudibleIndex =
                            mAudioManager.getLastAudibleVolumeForVolumeGroup(volumeGroupId);
                    assertEquals(lastAudibleIndex, index);
                    assertFalse(mAudioManager.isVolumeGroupMuted(volumeGroupId));

                    // Mute
                    vgCbReceiver.setExpectedVolumeGroup(volumeGroupId);
                    mAudioManager.adjustVolumeGroupVolume(
                            volumeGroupId, AudioManager.ADJUST_MUTE, 0/*flags*/);
                    assertTrue(vgCbReceiver.waitForExpectedVolumeGroupChanged(
                            AudioVolumeGroupCallbackHelper.ASYNC_TIMEOUT_MS));
                    readIndex = mAudioManager.getVolumeGroupVolumeIndex(volumeGroupId);
                    assertEquals("Failed to mute volume for group id "
                            + volumeGroupId, readIndex, indexMin);
                    assertEquals(lastAudibleIndex,
                            mAudioManager.getLastAudibleVolumeForVolumeGroup(volumeGroupId));
                    assertTrue(mAudioManager.isVolumeGroupMuted(volumeGroupId));

                    // Unmute
                    vgCbReceiver.setExpectedVolumeGroup(volumeGroupId);
                    mAudioManager.adjustVolumeGroupVolume(
                            volumeGroupId, AudioManager.ADJUST_UNMUTE, 0/*flags*/);
                    assertTrue(vgCbReceiver.waitForExpectedVolumeGroupChanged(
                            AudioVolumeGroupCallbackHelper.ASYNC_TIMEOUT_MS));
                    readIndex = mAudioManager.getVolumeGroupVolumeIndex(volumeGroupId);
                    assertEquals("Failed to unmute volume for group id "
                            + volumeGroupId, readIndex, lastAudibleIndex);
                    assertEquals(lastAudibleIndex,
                            mAudioManager.getLastAudibleVolumeForVolumeGroup(volumeGroupId));
                    assertFalse(mAudioManager.isVolumeGroupMuted(volumeGroupId));

                    // Toggle Mute (from unmuted)
                    vgCbReceiver.setExpectedVolumeGroup(volumeGroupId);
                    mAudioManager.adjustVolumeGroupVolume(
                            volumeGroupId, AudioManager.ADJUST_TOGGLE_MUTE, 0/*flags*/);
                    assertTrue(vgCbReceiver.waitForExpectedVolumeGroupChanged(
                            AudioVolumeGroupCallbackHelper.ASYNC_TIMEOUT_MS));
                    readIndex = mAudioManager.getVolumeGroupVolumeIndex(volumeGroupId);
                    assertEquals("Failed to mute volume for group id "
                            + volumeGroupId, readIndex, indexMin);
                    assertEquals(lastAudibleIndex,
                            mAudioManager.getLastAudibleVolumeForVolumeGroup(volumeGroupId));
                    assertTrue(mAudioManager.isVolumeGroupMuted(volumeGroupId));

                    // Toggle Mute (from muted)
                    vgCbReceiver.setExpectedVolumeGroup(volumeGroupId);
                    mAudioManager.adjustVolumeGroupVolume(
                            volumeGroupId, AudioManager.ADJUST_TOGGLE_MUTE, 0/*flags*/);
                    assertTrue(vgCbReceiver.waitForExpectedVolumeGroupChanged(
                            AudioVolumeGroupCallbackHelper.ASYNC_TIMEOUT_MS));
                    readIndex = mAudioManager.getVolumeGroupVolumeIndex(volumeGroupId);
                    assertEquals("Failed to unmute volume for group id "
                            + volumeGroupId, readIndex, lastAudibleIndex);
                    assertEquals(lastAudibleIndex,
                            mAudioManager.getLastAudibleVolumeForVolumeGroup(volumeGroupId));
                    assertFalse(mAudioManager.isVolumeGroupMuted(volumeGroupId));
                } else {
                    int lastAudibleIndex;
                    index = resetVolumeIndex(indexMin, indexMax);
                    vgCbReceiver.setExpectedVolumeGroup(volumeGroupId);
                    mAudioManager.setVolumeGroupVolumeIndex(volumeGroupId, index, 0/*flags*/);
                    assertTrue(vgCbReceiver.waitForExpectedVolumeGroupChanged(
                            AudioVolumeGroupCallbackHelper.ASYNC_TIMEOUT_MS));
                    readIndex = mAudioManager.getVolumeGroupVolumeIndex(volumeGroupId);
                    assertEquals(readIndex, index);

                    lastAudibleIndex =
                            mAudioManager.getLastAudibleVolumeForVolumeGroup(volumeGroupId);
                    assertEquals(lastAudibleIndex, index);
                    assertFalse(mAudioManager.isVolumeGroupMuted(volumeGroupId));

                    // Mute
                    vgCbReceiver.setExpectedVolumeGroup(volumeGroupId);
                    mAudioManager.adjustVolumeGroupVolume(
                            volumeGroupId, AudioManager.ADJUST_MUTE, 0/*flags*/);
                    assertFalse(vgCbReceiver.waitForExpectedVolumeGroupChanged(
                            AudioVolumeGroupCallbackHelper.ASYNC_TIMEOUT_MS));
                    readIndex = mAudioManager.getVolumeGroupVolumeIndex(volumeGroupId);
                    assertEquals("Unexpected volume mute for group id " + volumeGroupId
                            + " readIndex=" + readIndex, readIndex, lastAudibleIndex);
                    assertEquals(lastAudibleIndex,
                            mAudioManager.getLastAudibleVolumeForVolumeGroup(volumeGroupId));
                    assertFalse(mAudioManager.isVolumeGroupMuted(volumeGroupId));

                    // Unmute
                    vgCbReceiver.setExpectedVolumeGroup(volumeGroupId);
                    mAudioManager.adjustVolumeGroupVolume(
                            volumeGroupId, AudioManager.ADJUST_UNMUTE, 0/*flags*/);
                    assertFalse(vgCbReceiver.waitForExpectedVolumeGroupChanged(
                            AudioVolumeGroupCallbackHelper.ASYNC_TIMEOUT_MS));
                    readIndex = mAudioManager.getVolumeGroupVolumeIndex(volumeGroupId);
                    assertEquals(readIndex, lastAudibleIndex);
                    assertEquals(lastAudibleIndex,
                            mAudioManager.getLastAudibleVolumeForVolumeGroup(volumeGroupId));
                    assertFalse(mAudioManager.isVolumeGroupMuted(volumeGroupId));

                    // Toggle Mute (from unmuted)
                    vgCbReceiver.setExpectedVolumeGroup(volumeGroupId);
                    mAudioManager.adjustVolumeGroupVolume(
                            volumeGroupId, AudioManager.ADJUST_TOGGLE_MUTE, 0/*flags*/);
                    assertFalse(vgCbReceiver.waitForExpectedVolumeGroupChanged(
                            AudioVolumeGroupCallbackHelper.ASYNC_TIMEOUT_MS));
                    readIndex = mAudioManager.getVolumeGroupVolumeIndex(volumeGroupId);
                    assertEquals(readIndex, lastAudibleIndex);
                    assertEquals(lastAudibleIndex,
                            mAudioManager.getLastAudibleVolumeForVolumeGroup(volumeGroupId));
                    assertFalse(mAudioManager.isVolumeGroupMuted(volumeGroupId));

                    // Toggle Mute (from muted)
                    vgCbReceiver.setExpectedVolumeGroup(volumeGroupId);
                    mAudioManager.adjustVolumeGroupVolume(
                            volumeGroupId, AudioManager.ADJUST_TOGGLE_MUTE, 0/*flags*/);
                    assertFalse(vgCbReceiver.waitForExpectedVolumeGroupChanged(
                            AudioVolumeGroupCallbackHelper.ASYNC_TIMEOUT_MS));
                    readIndex = mAudioManager.getVolumeGroupVolumeIndex(volumeGroupId);
                    assertEquals(readIndex, lastAudibleIndex);
                    assertEquals(lastAudibleIndex,
                            mAudioManager.getLastAudibleVolumeForVolumeGroup(volumeGroupId));
                    assertFalse(mAudioManager.isVolumeGroupMuted(volumeGroupId));
                }
            }
        } finally {
            mAudioManager.unregisterVolumeGroupCallback(vgCbReceiver);
            getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
        }
    }

    private void waitForMixerAttrChanged(AudioAttributes audioAttributes, int deviceId)
            throws Exception {
        final ListenableFuture<Void> future =
                mCancelRule.registerFuture(
                        getFutureForListener(
                                listener ->
                                        mAudioManager.addOnPreferredMixerAttributesChangedListener(
                                                MoreExecutors.directExecutor(), listener),
                                mAudioManager::removeOnPreferredMixerAttributesChangedListener,
                                (completer) ->
                                        (AudioAttributes aa,
                                                AudioDeviceInfo device,
                                                AudioMixerAttributes ma) -> {
                                            if (device.getId() == deviceId
                                                    && Objects.equals(aa, audioAttributes)) {
                                                completer.set(null);
                                            }
                                        },
                                "Wait for mixer attr changed future"));
        future.get(FUTURE_WAIT_SECS, TimeUnit.MILLISECONDS);
    }

    private void assertCallChangesStreamVolume(Runnable r, int stream, int expectedVolume)
            throws Exception {
        assertCallChangesStreamVolume(r, stream, expectedVolume, null);
    }

    private void assertCallChangesStreamVolume(Runnable r, int stream, int expectedVolume,
            String msg)
            throws Exception {
        var initVol = mAudioManager.getStreamVolume(stream);
        assertWithMessage("Stream volume is already at desired")
            .that(initVol)
            .isNotEqualTo(expectedVolume);

        var future = mCancelRule.registerFuture(getFutureForIntent(
                            mContext,
                            AudioManager.ACTION_VOLUME_CHANGED,
                            i -> (i != null)
                                && i.getIntExtra(AudioManager.EXTRA_VOLUME_STREAM_TYPE, -1)
                                        == stream));
        r.run();
        var intent = future.get(FUTURE_WAIT_SECS, TimeUnit.SECONDS);
        String assertMessage = "Unexpected volume for stream " + stream + ". "
                + ((msg != null) ? msg : "");
        // TODO prev volume from intent is not zeroed when moving out of zen
        /*
        assertWithMessage(assertMessage)
                .that(intent.getIntExtra(AudioManager.EXTRA_PREV_VOLUME_STREAM_VALUE, -1))
                .isEqualTo(initVol);
        */
        assertWithMessage(assertMessage)
                .that(intent.getIntExtra(AudioManager.EXTRA_VOLUME_STREAM_VALUE, -1))
                .isEqualTo(expectedVolume);
        assertWithMessage(assertMessage)
                .that(mAudioManager.getStreamVolume(stream))
                .isEqualTo(expectedVolume);
    }

    private void assertCallDoesNotChangeStreamVolume(Runnable r, int stream) throws Exception {
        assertCallDoesNotChangeStreamVolume(r, stream, null);
    }

    private void assertCallDoesNotChangeStreamVolume(Runnable r, int stream, String message)
            throws Exception {
        // It is hard to test a negative, but we will do our best
        final int initVol = mAudioManager.getStreamVolume(stream);
        // Set the volume to a known value

        var future = mCancelRule.registerFuture(getFutureForIntent(
                mContext,
                AudioManager.ACTION_VOLUME_CHANGED,
                i -> (i != null)
                && i.getIntExtra(AudioManager.EXTRA_VOLUME_STREAM_TYPE, -1)
                    == stream));
        r.run();
        SystemClock.sleep(PROVE_NEGATIVE_DURATION_MS);
        AmUtils.waitForBroadcastBarrier();
        assertThat(future.isDone())
                .isFalse();

        assertWithMessage("Call expected to not change volume. "
                + ((message != null) ? message : ""))
                .that(mAudioManager.getStreamVolume(stream))
                .isEqualTo(initVol);
    }

    private void waitForStreamVolumeSet(int stream, int expectedVolume) throws Exception {
        final var initVol = mAudioManager.getStreamVolume(stream);
        // Set the volume to a known value
        if (initVol != expectedVolume) {
            var future = mCancelRule.registerFuture(getFutureForIntent(
                    mContext,
                    AudioManager.ACTION_VOLUME_CHANGED,
                    i -> (i != null)
                    && i.getIntExtra(AudioManager.EXTRA_VOLUME_STREAM_TYPE, -1)
                        == stream));
            mAudioManager.setStreamVolume(stream,
                    expectedVolume, 0 /* flags */);
            assertThat(future.get(FUTURE_WAIT_SECS, TimeUnit.SECONDS)
                    .getIntExtra(AudioManager.EXTRA_VOLUME_STREAM_VALUE, -1))
                    .isEqualTo(expectedVolume);
        }
        assertWithMessage("Failed to set stream volume for " + stream + " to " + expectedVolume)
                .that(mAudioManager.getStreamVolume(stream))
                .isEqualTo(expectedVolume);

    }


    private void pollWithBackoff(BooleanSupplier isDone, long initialMs,
            long backoff, long maxBackoff, long timeout) {
        final long startTime = SystemClock.uptimeMillis();
        long waitMs = initialMs;
        while (true) {
            if (isDone.getAsBoolean()) {
                return;
            }
            long timeLeft = timeout - (SystemClock.uptimeMillis() - startTime);
            if (timeLeft < 0) {
                throw new AssertionError("Polling timeout");
            }
            waitMs = Math.min(Math.min(waitMs + backoff, maxBackoff), timeLeft);
            SystemClock.sleep(waitMs);
        }
    }

    private ListenableFuture<Intent> createMuteFuture(int stream) {
        return mCancelRule.registerFuture(getFutureForIntent(mContext,
                    "android.media.STREAM_MUTE_CHANGED_ACTION",
                i -> (i != null) &&
                    i.getIntExtra(AudioManager.EXTRA_VOLUME_STREAM_TYPE, -1) == stream));
    }

    private static record MuteStateTransition(boolean before, boolean after) {}

    private static interface ThrowingRunnable {
        public void run() throws Exception;
    }

    private void assertStreamMuteStateChange(ThrowingRunnable r,
            Map<Integer, MuteStateTransition> streamMuteMap,
            String msg)
            throws Exception {

        streamMuteMap.forEach(
                (Integer stream, MuteStateTransition mute)
                        -> assertWithMessage(msg + " Initial stream mute state for " + stream +
                            "does not correspond to expected mute state")
                    .that(mAudioManager.isStreamMute(stream))
                    .isEqualTo(mute.before()));

        ListenableFuture<List<Intent>> futures = null;
        List<ListenableFuture<Intent>> unchangedFutures = null;

        futures = Futures.allAsList(streamMuteMap.entrySet().stream()
                .filter(e -> e.getValue().before() != e.getValue().after())
                .map(e -> {
                    return Futures.transform(createMuteFuture(e.getKey()),
                            (Intent i) -> {
                                assertWithMessage(msg + " Stream " + e.getKey() + " failed to mute")
                                    .that(i.getBooleanExtra(
                                                "android.media.EXTRA_STREAM_VOLUME_MUTED",
                                                false))
                                    .isEqualTo(e.getValue().after());
                                return i;
                    }, MoreExecutors.directExecutor());
                })
                .collect(Collectors.toList()));

        unchangedFutures = streamMuteMap.entrySet().stream()
                .filter(e -> e.getValue().before() == e.getValue().after())
                .map(e -> createMuteFuture(e.getKey()))
                .collect(Collectors.toList());

        r.run();

        SystemClock.sleep(PROVE_NEGATIVE_DURATION_MS);
        AmUtils.waitForBroadcastBarrier();
        futures.get(FUTURE_WAIT_SECS, TimeUnit.SECONDS);

        for (var f : unchangedFutures) {
            if (f.isDone()) {
                throw new AssertionError(msg + " Unexpected unmute: " + f.get());
            }
        }

        streamMuteMap.forEach(
                (Integer stream, MuteStateTransition mute)
                        -> assertWithMessage(msg + " Final stream mute state for " + stream
                            + " does not correspond to expected mute state")
                    .that(mAudioManager.isStreamMute(stream))
                    .isEqualTo(mute.after()));
    }

    private void assertMusicActive(boolean expectedIsMusicActive) throws Exception {
        final long startPoll = SystemClock.uptimeMillis();
        boolean actualIsMusicActive = mAudioManager.isMusicActive();
        while (SystemClock.uptimeMillis() - startPoll < POLL_TIME_PLAY_MUSIC
                && expectedIsMusicActive != actualIsMusicActive) {
            actualIsMusicActive = mAudioManager.isMusicActive();
        }
        assertEquals(actualIsMusicActive, actualIsMusicActive);
    }

    private static final long REPEATED_CHECK_POLL_PERIOD_MS = 100; // 100ms
    private static final long DEFAULT_ASYNC_CALL_TIMEOUT_MS = 5 * REPEATED_CHECK_POLL_PERIOD_MS;

    /**
     * Makes multiple attempts over a given timeout period to test the predicate on an AudioManager
     * instance. Test success is evaluated against a true predicate result.
     * @param am the AudioManager instance to use for the test
     * @param predicate the test to run either until it returns true, or until the timeout expires
     * @param timeoutMs the maximum time allowed for the test to pass
     * @param errorString the string to be displayed in case of failure
     * @throws Exception
     */
    private void assertTrueCheckTimeout(AudioManager am, Predicate<AudioManager> predicate,
            long timeoutMs, String errorString) throws Exception {
        long checkStart = SystemClock.uptimeMillis();
        boolean result = false;
        while (SystemClock.uptimeMillis() - checkStart < timeoutMs) {
            result = predicate.test(am);
            if (result) {
                break;
            }
            Thread.sleep(REPEATED_CHECK_POLL_PERIOD_MS);
        }
        assertTrue(errorString, result);
    }

    private boolean isAutomotive() {
        PackageManager pm = mContext.getPackageManager();
        return pm.hasSystemFeature(pm.FEATURE_AUTOMOTIVE);
    }

    // getParameters() & setParameters() are deprecated, so don't test

    // setAdditionalOutputDeviceDelay(), getAudioVolumeGroups(), getVolumeIndexForAttributes()
    // getMinVolumeIndexForAttributes(), getMaxVolumeIndexForAttributes() &
    // setVolumeIndexForAttributes() require privledged permission MODIFY_AUDIO_ROUTING
    // and thus cannot be tested here.
}
