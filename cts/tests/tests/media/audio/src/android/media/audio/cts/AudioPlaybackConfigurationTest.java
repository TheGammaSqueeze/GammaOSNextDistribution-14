/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static android.app.AppOpsManager.MODE_ALLOWED;
import static android.app.AppOpsManager.MODE_IGNORED;
import static android.app.AppOpsManager.OPSTR_PLAY_AUDIO;
import static android.media.AudioAttributes.ALLOW_CAPTURE_BY_ALL;
import static android.media.AudioAttributes.ALLOW_CAPTURE_BY_NONE;
import static android.media.AudioAttributes.ALLOW_CAPTURE_BY_SYSTEM;
import static android.media.AudioManager.ADJUST_MUTE;
import static android.media.AudioManager.ADJUST_UNMUTE;
import static android.media.AudioManager.STREAM_NOTIFICATION;
import static android.media.AudioPlaybackConfiguration.MUTED_BY_APP_OPS;
import static android.media.AudioPlaybackConfiguration.MUTED_BY_CLIENT_VOLUME;
import static android.media.AudioPlaybackConfiguration.MUTED_BY_STREAM_VOLUME;
import static android.media.AudioPlaybackConfiguration.MUTED_BY_VOLUME_SHAPER;
import static android.media.AudioTrack.WRITE_NON_BLOCKING;
import static android.media.cts.AudioHelper.createSoundDataInShortByteBuffer;
import static android.media.cts.AudioHelper.hasAudioSilentProperty;

import static com.android.compatibility.common.util.AppOpsUtils.getOpMode;
import static com.android.compatibility.common.util.AppOpsUtils.setOpMode;

import android.Manifest;
import android.annotation.Nullable;
import android.annotation.RawRes;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioAttributes.CapturePolicy;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioPlaybackConfiguration;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.media.VolumeShaper;
import android.media.cts.TestUtils;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Parcel;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.CtsAndroidTestCase;
import com.android.compatibility.common.util.NonMainlineTest;
import com.android.internal.annotations.GuardedBy;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

@NonMainlineTest
public class AudioPlaybackConfigurationTest extends CtsAndroidTestCase {
    private static final String TAG = "AudioPlaybackConfigurationTest";

    private static final int TEST_TIMING_TOLERANCE_MS = 150;
    /** acceptable timeout for the time it takes for a prepared MediaPlayer to have an audio device
     * selected and reported when starting to play */
    private static final int PLAY_ROUTING_TIMING_TOLERANCE_MS = 500;
    private static final int TEST_TIMEOUT_SOUNDPOOL_LOAD_MS = 3000;
    private static final long MEDIAPLAYER_PREPARE_TIMEOUT_MS = 2000;

    private static final int TEST_AUDIO_TRACK_SAMPLERATE = 48000;
    private static final double TEST_AUDIO_TRACK_FREQUENCY = 440.0;
    private static final int TEST_AUDIO_TRACK_CHANNELS = 2;
    private static final int TEST_AUDIO_TRACK_PLAY_SECONDS = 2;
    private static final double TEST_AUDIO_TRACK_SWEEP = 0;

    // volume shaper duration in milliseconds.
    private static final long VOLUME_SHAPER_DURATION_MS = 10;

    private static final VolumeShaper.Configuration SHAPER_MUTE =
            new VolumeShaper.Configuration.Builder()
                    .setInterpolatorType(VolumeShaper.Configuration.INTERPOLATOR_TYPE_LINEAR)
                    .setCurve(new float[] { 0.f, 1.f } /* times */,
                            new float[] { 1.f, 0.f } /* volumes */)
                    .setDuration(VOLUME_SHAPER_DURATION_MS)
                    .build();

    private VolumeShaper mMuteShaper;

    // not declared inside test so it can be released in case of failure
    private MediaPlayer mMp;
    private SoundPool mSp;
    private AudioTrack mAt;

    @Override
    protected void tearDown() throws Exception {
        // try/catch for every method in case the tests left the objects in various states
        if (mMp != null) {
            try {
                mMp.stop();
            } catch (Exception ignored) { }
            mMp.release();
            mMp = null;
        }
        if (mSp != null) {
            mSp.release();
            mSp = null;
        }
        if (mAt != null) {
            mAt.release();
            mAt = null;
        }
        super.tearDown();
    }

    private static final int TEST_USAGE = AudioAttributes.USAGE_NOTIFICATION;
    private static final int TEST_CONTENT = AudioAttributes.CONTENT_TYPE_UNKNOWN;
    private static final int TEST_STREAM_FOR_USAGE = STREAM_NOTIFICATION;

    // test writing to/ reading from a Parcel for an AudioPlaybackConfiguration instance.
    // Since we can't create an AudioPlaybackConfiguration directly, we first need to
    // play something to get one.
    public void testParcelableWriteToParcel() throws Exception {
        if (!isValidPlatform("testParcelableWriteToParcel")) return;
        if (hasAudioSilentProperty()) {
            // No reasons to test since the started MediaPlayer will be muted and inactive
            Log.w(TAG, "Device has ro.audio.silent set, skipping testParcelableWriteToParcel");
            return;
        }

        // create a player, make it play so we can get an AudioPlaybackConfiguration instance
        AudioManager am = new AudioManager(getContext());
        assertNotNull("Could not create AudioManager", am);
        final AudioAttributes aa = (new AudioAttributes.Builder())
                .setUsage(TEST_USAGE)
                .setContentType(TEST_CONTENT)
                .setAllowedCapturePolicy(ALLOW_CAPTURE_BY_NONE)
                .build();
        mMp = createPreparedMediaPlayer(R.raw.sine1khzs40dblong, aa, am.generateAudioSessionId());
        mMp.start();
        Thread.sleep(TEST_TIMING_TOLERANCE_MS);// waiting for playback to start
        List<AudioPlaybackConfiguration> configs = am.getActivePlaybackConfigurations();
        mMp.stop();
        assertTrue("No playback reported", configs.size() > 0);
        AudioPlaybackConfiguration configToParcel = null;
        for (AudioPlaybackConfiguration config : configs) {
            if (config.getAudioAttributes().equals(aa)) {
                configToParcel = config;
                break;
            }
        }

        assertNotNull("Configuration not found during playback", configToParcel);
        assertEquals(0, configToParcel.describeContents());

        final Parcel srcParcel = Parcel.obtain();
        final Parcel dstParcel = Parcel.obtain();

        configToParcel.writeToParcel(srcParcel, 0 /*no public flags for parcelling operations*/);
        dstParcel.appendFrom(srcParcel, 0 /*offset*/, srcParcel.dataSize() /*size*/);
        dstParcel.setDataPosition(0);
        final AudioPlaybackConfiguration restoredConfig =
                AudioPlaybackConfiguration.CREATOR.createFromParcel(dstParcel);

        assertEquals("Marshalled/restored AudioAttributes don't match",
                configToParcel.getAudioAttributes(), restoredConfig.getAudioAttributes());
    }

    public void testGetterMediaPlayer() throws Exception {
        if (!isValidPlatform("testGetterMediaPlayer")) return;
        if (hasAudioSilentProperty()) {
            // No reasons to test since the started MediaPlayer will be muted and inactive
            Log.w(TAG, "Device has ro.audio.silent set, skipping testGetterMediaPlayer");
            return;
        }

        AudioManager am = new AudioManager(getContext());
        assertNotNull("Could not create AudioManager", am);

        final AudioAttributes aa = (new AudioAttributes.Builder())
                .setUsage(TEST_USAGE)
                .setContentType(TEST_CONTENT)
                .setAllowedCapturePolicy(ALLOW_CAPTURE_BY_ALL)
                .build();

        List<AudioPlaybackConfiguration> configs = am.getActivePlaybackConfigurations();
        final int nbActivePlayersBeforeStart = configs.size();

        mMp = createPreparedMediaPlayer(R.raw.sine1khzs40dblong, aa, am.generateAudioSessionId());
        configs = am.getActivePlaybackConfigurations();
        assertEquals("inactive MediaPlayer, number of configs shouldn't have changed",
                nbActivePlayersBeforeStart /*expected*/, configs.size());

        mMp.start();
        Thread.sleep(TEST_TIMING_TOLERANCE_MS);// waiting for playback to start
        configs = am.getActivePlaybackConfigurations();
        assertEquals("active MediaPlayer, number of configs should have increased",
                nbActivePlayersBeforeStart + 1 /*expected*/,
                configs.size());
        assertTrue("Active player, attributes not found", hasAttr(configs, aa));
        // verify "privileged" fields aren't available through reflection
        final AudioPlaybackConfiguration config = configs.get(0);
        final Class<?> confClass = config.getClass();
        final Method getClientUidMethod = confClass.getDeclaredMethod("getClientUid");
        final Method getClientPidMethod = confClass.getDeclaredMethod("getClientPid");
        final Method getPlayerTypeMethod = confClass.getDeclaredMethod("getPlayerType");
        final Method getSessionIdMethod = confClass.getDeclaredMethod("getSessionId");
        try {
            Integer uid = (Integer) getClientUidMethod.invoke(config, (Object[]) null);
            assertEquals("uid isn't protected", -1 /*expected*/, uid.intValue());
            Integer pid = (Integer) getClientPidMethod.invoke(config, (Object[]) null);
            assertEquals("pid isn't protected", -1 /*expected*/, pid.intValue());
            Integer type = (Integer) getPlayerTypeMethod.invoke(config, (Object[]) null);
            assertEquals("player type isn't protected", -1 /*expected*/, type.intValue());
            Integer sessionId = (Integer) getSessionIdMethod.invoke(config, (Object[]) null);
            assertEquals("session ID isn't protected", 0 /*expected*/, sessionId.intValue());
        } catch (Exception e) {
            fail("Exception thrown during reflection on config privileged fields"+ e);
        }
        assertEquals("spatialized field isn't protected", false, config.isSpatialized());
        assertEquals("sample rate field isn't protected", 0, config.getSampleRate());
        assertEquals("channel mask field isn't protected", 0, config.getChannelMask());
    }

    public void testCallbackMediaPlayer() throws Exception {
        if (!isValidPlatform("testCallbackMediaPlayer")) return;
        doTestCallbackMediaPlayer(false /* no custom Handler for callback */);
    }

    public void testCallbackMediaPlayerHandler() throws Exception {
        if (!isValidPlatform("testCallbackMediaPlayerHandler")) return;
        doTestCallbackMediaPlayer(true /* use custom Handler for callback */);
    }

    private void doTestCallbackMediaPlayer(boolean useHandlerInCallback) throws Exception {
        final Handler h;
        if (useHandlerInCallback) {
            HandlerThread handlerThread = new HandlerThread(TAG);
            handlerThread.start();
            h = new Handler(handlerThread.getLooper());
        } else {
            h = null;
        }

        AudioManager am = new AudioManager(getContext());
        assertNotNull("Could not create AudioManager", am);

        MyAudioPlaybackCallback callback = new MyAudioPlaybackCallback();

        MyAudioPlaybackCallback registeredCallback = null;

        final AudioAttributes aa = (new AudioAttributes.Builder())
                .setUsage(TEST_USAGE)
                .setContentType(TEST_CONTENT)
                .build();

        try {
            mMp = createPreparedMediaPlayer(R.raw.sine1khzs40dblong, aa,
                    am.generateAudioSessionId());

            am.registerAudioPlaybackCallback(callback, h /*handler*/);
            registeredCallback = callback;

            // query how many active players before starting the MediaPlayer
            List<AudioPlaybackConfiguration> configs = am.getActivePlaybackConfigurations();
            final int nbActivePlayersBeforeStart = configs.size();

            assertPlayerStartAndCallbackWithPlayerAttributes(mMp, callback,
                    nbActivePlayersBeforeStart + 1, aa);


            // stopping playback: callback is called with no match
            callback.reset();
            mMp.pause();
            Thread.sleep(TEST_TIMING_TOLERANCE_MS);

            assertEquals("onPlaybackConfigChanged call count not expected after pause",
                    1/*expected*/, callback.getCbInvocationNumber());//only 1 pause call since reset
            assertEquals("number of active players not expected after pause",
                    nbActivePlayersBeforeStart/*expected*/, callback.getNbConfigs());

            // unregister callback and start playback again
            am.unregisterAudioPlaybackCallback(callback);
            registeredCallback = null;
            Thread.sleep(TEST_TIMING_TOLERANCE_MS);
            callback.reset();
            mMp.start();
            Thread.sleep(TEST_TIMING_TOLERANCE_MS);
            assertEquals("onPlaybackConfigChanged call count not expected after unregister",
                    0/*expected*/, callback.getCbInvocationNumber()); //callback is unregistered

            // just call the callback once directly so it's marked as tested
            final AudioManager.AudioPlaybackCallback apc =
                    (AudioManager.AudioPlaybackCallback) callback;
            apc.onPlaybackConfigChanged(new ArrayList<AudioPlaybackConfiguration>());
        } finally {
            if (registeredCallback != null) {
                am.unregisterAudioPlaybackCallback(registeredCallback);
            }
            if (h != null) {
                h.getLooper().quit();
            }
        }
    }

    public void testCallbackMediaPlayerRelease() throws Exception {
        final HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        final Handler h = new Handler(handlerThread.getLooper());

        AudioManager am = new AudioManager(getContext());
        assertNotNull("Could not create AudioManager", am);

        MyAudioPlaybackCallback callback = new MyAudioPlaybackCallback();

        final AudioAttributes aa = (new AudioAttributes.Builder())
                .setUsage(TEST_USAGE)
                .setContentType(TEST_CONTENT)
                .build();

        try {
            mMp = createPreparedMediaPlayer(R.raw.sine1khzs40dblong, aa,
                    am.generateAudioSessionId());

            am.registerAudioPlaybackCallback(callback, h /*handler*/);

            // query how many active players before starting the MediaPlayer
            List<AudioPlaybackConfiguration> configs =
                    am.getActivePlaybackConfigurations();
            final int nbActivePlayersBeforeStart = configs.size();

            assertPlayerStartAndCallbackWithPlayerAttributes(mMp, callback,
                    nbActivePlayersBeforeStart + 1, aa);

            // release the player without stopping or pausing it first
            callback.reset();
            mMp.release();
            Thread.sleep(TEST_TIMING_TOLERANCE_MS);

            assertEquals("onPlaybackConfigChanged call count not expected after release",
                    1/*expected*/, callback.getCbInvocationNumber());//only release call since reset
            assertEquals("number of active players not expected after release",
                    nbActivePlayersBeforeStart/*expected*/, callback.getNbConfigs());

        } finally {
            am.unregisterAudioPlaybackCallback(callback);
            if (h != null) {
                h.getLooper().quit();
            }
        }
    }

    public void testGetterSoundPool() throws Exception {
        if (!isValidPlatform("testSoundPool")) return;

        AudioManager am = new AudioManager(getContext());
        assertNotNull("Could not create AudioManager", am);

        MyAudioPlaybackCallback callback = new MyAudioPlaybackCallback();
        am.registerAudioPlaybackCallback(callback, null /*handler*/);

        // query how many active players before starting the SoundPool
        List<AudioPlaybackConfiguration> configs = am.getActivePlaybackConfigurations();
        int nbActivePlayersBeforeStart = 0;
        for (AudioPlaybackConfiguration apc : configs) {
            if (apc.getPlayerState() == AudioPlaybackConfiguration.PLAYER_STATE_STARTED) {
                nbActivePlayersBeforeStart++;
            }
        }

        final AudioAttributes aa = (new AudioAttributes.Builder())
                .setUsage(TEST_USAGE)
                .setContentType(TEST_CONTENT)
                .setAllowedCapturePolicy(ALLOW_CAPTURE_BY_SYSTEM)
                .build();
        mSp = createSoundPool(aa);
        playSoundPool(mSp, getContext());

        Thread.sleep(TEST_TIMING_TOLERANCE_MS);

        mSp.autoPause();
        Thread.sleep(TEST_TIMING_TOLERANCE_MS);
        // query how many active players after pausing
        configs = am.getActivePlaybackConfigurations();
        int nbActivePlayersAfterPause = 0;
        for (AudioPlaybackConfiguration apc : configs) {
            if (apc.getPlayerState() == AudioPlaybackConfiguration.PLAYER_STATE_STARTED) {
                nbActivePlayersAfterPause++;
            }
        }
        assertEquals("Number of active players changed after pausing SoundPool",
                nbActivePlayersBeforeStart, nbActivePlayersAfterPause);
    }

    @ApiTest(apis = {"android.media.AudioManager#getActivePlaybackConfigurations",
            "android.media.AudioManager.AudioPlaybackCallback#onPlaybackConfigChanged"})
    public void testGetterAndCallbackConsistency() throws Exception {
        if (!isValidPlatform("testGetterAndCallbackConsistency")) return;

        AudioManager am = new AudioManager(getContext());
        assertNotNull("Could not create AudioManager", am);

        MyAudioPlaybackCallback callback = new MyAudioPlaybackCallback();
        am.registerAudioPlaybackCallback(callback, null /*handler*/);

        final AudioAttributes aa = (new AudioAttributes.Builder())
                .setUsage(TEST_USAGE)
                .setContentType(TEST_CONTENT)
                .setAllowedCapturePolicy(ALLOW_CAPTURE_BY_SYSTEM)
                .build();
        mSp = createSoundPool(aa);
        mMp = createPreparedMediaPlayer(R.raw.sine1khzs40dblong, aa,
                am.generateAudioSessionId());

        try {
            playSoundPool(mSp, getContext());

            callback.reset();
            mMp.start();

            assertTrue("onPlaybackConfigChanged should have been called for start and new device",
                    callback.waitForCallbacks(2,
                            TEST_TIMING_TOLERANCE_MS + PLAY_ROUTING_TIMING_TOLERANCE_MS));
            assertListsAreConsistent(am.getActivePlaybackConfigurations(), callback.getConfigs());

            mSp.autoPause();
            mMp.stop();
        } finally {
            am.unregisterAudioPlaybackCallback(callback);
        }
    }

    public void testGetAudioDeviceInfoMediaPlayerStart() throws Exception {
        if (!isValidPlatform("testGetAudioDeviceInfoMediaPlayerStart")) return;

        final HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        final Handler h = new Handler(handlerThread.getLooper());

        AudioManager am = new AudioManager(getContext());
        assertNotNull("Could not create AudioManager", am);

        MyAudioPlaybackCallback callback = new MyAudioPlaybackCallback();

        final AudioAttributes aa = (new AudioAttributes.Builder())
                .setUsage(TEST_USAGE)
                .setContentType(TEST_CONTENT)
                .build();

        try {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .adoptShellPermissionIdentity(Manifest.permission.MODIFY_AUDIO_ROUTING);

            mMp = createPreparedMediaPlayer(R.raw.sine1khzs40dblong, aa,
                    am.generateAudioSessionId());

            am.registerAudioPlaybackCallback(callback, h /*handler*/);

            mMp.start();
            // time for the new configuration to propagate
            Thread.sleep(TEST_TIMING_TOLERANCE_MS + PLAY_ROUTING_TIMING_TOLERANCE_MS);

            assertTrue("Active player, device not found",
                    hasDevice(callback.getConfigs(), aa));

        } finally {
            am.unregisterAudioPlaybackCallback(callback);
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
            if (h != null) {
                h.getLooper().quit();
            }
        }
    }

    @ApiTest(apis = {"android.media.AudioManager#getActivePlaybackConfigurations",
            "android.media.AudioManager.AudioPlaybackCallback#onPlaybackConfigChanged",
            "android.media.AudioManager.AudioPlaybackCallback#isMuted",
            "android.media.AudioManager.AudioPlaybackCallback#getMutedBy"})
    public void testAudioTrackMuteFromAppOpsNotification() throws Exception {
        if (!isValidPlatform("testAudioTrackMuteFromAppOpsNotification")) return;
        if (hasAudioSilentProperty()) {
            Log.w(TAG, "Device has ro.audio.silent set, skipping "
                            + "testAudioTrackMuteFromAppOpsNotification");
            return;
        }

        initializeAudioTrack();
        checkMuteFromAppOpsNotification(new MyPlayer(mAt));
    }

    @ApiTest(apis = {"android.media.AudioManager#getActivePlaybackConfigurations",
            "android.media.AudioManager.AudioPlaybackCallback#onPlaybackConfigChanged",
            "android.media.AudioManager.AudioPlaybackCallback#isMuted",
            "android.media.AudioManager.AudioPlaybackCallback#getMutedBy"})
    public void testMediaPlayerMuteFromAppOpsNotification() throws Exception {
        if (!isValidPlatform("testMediaPlayerMuteFromAppOpsNotification")) return;
        if (hasAudioSilentProperty()) {
            Log.w(TAG, "Device has ro.audio.silent set, skipping "
                            + "testMediaPlayerMuteFromAppOpsNotification");
            return;
        }

        initializeMediaPlayer();
        checkMuteFromAppOpsNotification(new MyPlayer(mMp));
    }

    private void checkMuteFromAppOpsNotification(MyPlayer player) throws Exception {
        verifyMuteUnmuteNotifications(/*start=*/player.mPlay,
                /*mute=*/() -> {
                    try {
                        setOpMode(getContext().getPackageName(), OPSTR_PLAY_AUDIO, MODE_IGNORED);
                    } catch (IOException e) {
                        fail("Failed to set AppOps ignore for play audio: " + e);
                    }
                },
                /*unmute=*/() -> {
                    try {
                        if (getOpMode(getContext().getPackageName(), OPSTR_PLAY_AUDIO)
                                != MODE_ALLOWED) {
                            setOpMode(getContext().getPackageName(), OPSTR_PLAY_AUDIO,
                                    MODE_ALLOWED);
                        }
                    } catch (IOException e) {
                        fail("Failed to set AppOps allow for play audio: " + e);
                    }
                }, /*muteChangesActiveState=*/true, MUTED_BY_APP_OPS);
    }

    @ApiTest(apis = {"android.media.AudioManager#getActivePlaybackConfigurations",
            "android.media.AudioManager.AudioPlaybackCallback#onPlaybackConfigChanged",
            "android.media.AudioManager.AudioPlaybackCallback#isMuted",
            "android.media.AudioManager.AudioPlaybackCallback#getMutedBy"})
    public void testAudioTrackMuteFromStreamVolumeNotification() throws Exception {
        if (!isValidPlatform("testAudioTrackMuteFromStreamVolumeNotification")) return;
        if (hasAudioSilentProperty()) {
            Log.w(TAG, "Device has ro.audio.silent set, skipping "
                            + "testAudioTrackMuteFromStreamVolumeNotification");
            return;
        }

        initializeAudioTrack();
        checkMuteFromStreamVolumeNotification(new MyPlayer(mAt));
    }

    @ApiTest(apis = {"android.media.AudioManager#getActivePlaybackConfigurations",
            "android.media.AudioManager.AudioPlaybackCallback#onPlaybackConfigChanged",
            "android.media.AudioManager.AudioPlaybackCallback#isMuted",
            "android.media.AudioManager.AudioPlaybackCallback#getMutedBy"})
    public void testMediaPlayerMuteFromStreamVolumeNotification() throws Exception {
        if (!isValidPlatform("testMediaPlayerMuteFromStreamVolumeNotification")) return;
        if (hasAudioSilentProperty()) {
            Log.w(TAG, "Device has ro.audio.silent set, skipping "
                            + "testMediaPlayerMuteFromStreamVolumeNotification");
            return;
        }

        initializeMediaPlayer();
        checkMuteFromStreamVolumeNotification(new MyPlayer(mMp));
    }

    private void checkMuteFromStreamVolumeNotification(MyPlayer player) throws Exception {
        AudioManager am = new AudioManager(getContext());
        assertNotNull("Could not create AudioManager", am);

        if (am.isVolumeFixed()) {
            Log.w(TAG, "Skipping testMuteFromStreamVolumeNotification, device has volume fixed.");
            return;
        }

        verifyMuteUnmuteNotifications(/*start=*/player.mPlay,
                /*mute=*/
                () -> am.adjustStreamVolume(TEST_STREAM_FOR_USAGE, ADJUST_MUTE, /* flags= */0),
                /*unmute=*/
                () -> am.adjustStreamVolume(TEST_STREAM_FOR_USAGE, ADJUST_UNMUTE, /* flags= */0),
                /*muteChangesActiveState=*/false, MUTED_BY_STREAM_VOLUME);
    }

    @ApiTest(apis = {"android.media.AudioManager#getActivePlaybackConfigurations",
            "android.media.AudioManager.AudioPlaybackCallback#onPlaybackConfigChanged",
            "android.media.AudioManager.AudioPlaybackCallback#isMuted",
            "android.media.AudioManager.AudioPlaybackCallback#getMutedBy"})
    public void testAudioTrackMuteFromClientVolumeNotification() throws Exception {
        if (!isValidPlatform("testAudioTrackMuteFromClientVolumeNotification")) return;
        if (hasAudioSilentProperty()) {
            Log.w(TAG, "Device has ro.audio.silent set, skipping "
                            + "testAudioTrackMuteFromClientVolumeNotification");
            return;
        }

        initializeAudioTrack();
        checkMuteFromClientVolumeNotification(new MyPlayer(mAt));
    }


    @ApiTest(apis = {"android.media.AudioManager#getActivePlaybackConfigurations",
            "android.media.AudioManager.AudioPlaybackCallback#onPlaybackConfigChanged",
            "android.media.AudioManager.AudioPlaybackCallback#isMuted",
            "android.media.AudioManager.AudioPlaybackCallback#getMutedBy"})
    public void testMediaPlayerMuteFromClientVolumeNotification() throws Exception {
        if (!isValidPlatform("testMediaPlayerMuteFromClientVolumeNotification")) return;
        if (hasAudioSilentProperty()) {
            Log.w(TAG, "Device has ro.audio.silent set, skipping "
                            + "testMediaPlayerMuteFromClientVolumeNotification");
            return;
        }

        initializeMediaPlayer();
        checkMuteFromClientVolumeNotification(new MyPlayer(mMp));
    }

    private void checkMuteFromClientVolumeNotification(MyPlayer player) throws Exception {
        verifyMuteUnmuteNotifications(/*start=*/player.mPlay,
                /*mute=*/() -> player.mSetClientVolume.accept(0.f),
                /*unmute=*/() -> player.mSetClientVolume.accept(1.f),
                /*muteChangesActiveState=*/true, MUTED_BY_CLIENT_VOLUME);
    }

    @ApiTest(apis = {"android.media.AudioManager#getActivePlaybackConfigurations",
            "android.media.AudioManager.AudioPlaybackCallback#onPlaybackConfigChanged",
            "android.media.AudioManager.AudioPlaybackCallback#isMuted",
            "android.media.AudioManager.AudioPlaybackCallback#getMutedBy"})
    public void testAudioTrackMuteFromVolumeShaperNotification() throws Exception {
        if (!isValidPlatform("testAudioTrackMuteFromVolumeShaperNotification")) return;
        if (hasAudioSilentProperty()) {
            Log.w(TAG, "Device has ro.audio.silent set, skipping "
                            + "testAudioTrackMuteFromVolumeShaperNotification");
            return;
        }

        initializeAudioTrack();
        checkMuteFromVolumeShaperNotification(new MyPlayer(mAt));
    }

    @ApiTest(apis = {"android.media.AudioManager#getActivePlaybackConfigurations",
            "android.media.AudioManager.AudioPlaybackCallback#onPlaybackConfigChanged",
            "android.media.AudioManager.AudioPlaybackCallback#isMuted",
            "android.media.AudioManager.AudioPlaybackCallback#getMutedBy"})
    public void testMediaPlayerMuteFromVolumeShaperNotification() throws Exception {
        if (!isValidPlatform("testMediaPlayerMuteFromVolumeShaperNotification")) return;
        if (hasAudioSilentProperty()) {
            Log.w(TAG, "Device has ro.audio.silent set, skipping "
                            + "testMediaPlayerMuteFromVolumeShaperNotification");
            return;
        }

        initializeMediaPlayer();
        checkMuteFromVolumeShaperNotification(new MyPlayer(mMp));
    }

    private void checkMuteFromVolumeShaperNotification(MyPlayer player) throws Exception {
        verifyMuteUnmuteNotifications(/*start=*/player.mPlay,
                /*mute=*/() -> {
                    mMuteShaper = player.mCreateVolumeShaper.apply(SHAPER_MUTE);
                    mMuteShaper.apply(VolumeShaper.Operation.PLAY);
                },
                /*unmute=*/() -> {
                    mMuteShaper.replace(SHAPER_MUTE, VolumeShaper.Operation.REVERSE, /*join=*/
                            false);
                    mMuteShaper.apply(VolumeShaper.Operation.PLAY);
                }, /*muteChangesActiveState=*/true, MUTED_BY_VOLUME_SHAPER);
    }

    private void verifyMuteUnmuteNotifications(Runnable start, Runnable mute, Runnable unmute,
            boolean muteChangesActiveState, int checkFlag)
            throws Exception {
        AudioManager am = new AudioManager(getContext());
        assertNotNull("Could not create AudioManager", am);

        boolean isMuted = am.isStreamMute(TEST_STREAM_FOR_USAGE);
        if (isMuted) {
            am.adjustStreamVolume(TEST_STREAM_FOR_USAGE, ADJUST_UNMUTE, 0);
        }
        Thread.sleep(TEST_TIMING_TOLERANCE_MS + PLAY_ROUTING_TIMING_TOLERANCE_MS);

        MyAudioPlaybackCallback callback = new MyAudioPlaybackCallback();

        try {
            am.registerAudioPlaybackCallback(callback, null /*handler*/);

            // start playing audio
            start.run();

            if (muteChangesActiveState) {
                assertTrue("onPlaybackConfigChanged play, format and device expected",
                        callback.waitForCallbacks(3,
                                TEST_TIMING_TOLERANCE_MS + PLAY_ROUTING_TIMING_TOLERANCE_MS));
            } else {
                Thread.sleep(TEST_TIMING_TOLERANCE_MS + PLAY_ROUTING_TIMING_TOLERANCE_MS);
            }

            // mute with Runnable
            callback.reset();
            mute.run();

            if (muteChangesActiveState) {
                assertTrue("onPlaybackConfigChanged for mute expected",
                        callback.waitForCallbacks(1,
                                TEST_TIMING_TOLERANCE_MS + PLAY_ROUTING_TIMING_TOLERANCE_MS));
            } else {
                Thread.sleep(TEST_TIMING_TOLERANCE_MS + PLAY_ROUTING_TIMING_TOLERANCE_MS);
            }

            checkMutedApi(checkFlag);

            // unmute with Runnable
            callback.reset();
            unmute.run();

            if (muteChangesActiveState) {
                assertTrue("onPlaybackConfigChanged for unmute expected",
                        callback.waitForCallbacks(1,
                                TEST_TIMING_TOLERANCE_MS + PLAY_ROUTING_TIMING_TOLERANCE_MS));
            } else {
                Thread.sleep(TEST_TIMING_TOLERANCE_MS + PLAY_ROUTING_TIMING_TOLERANCE_MS);
            }
        } finally {
            am.unregisterAudioPlaybackCallback(callback);
            unmute.run();

            if (isMuted) {
                am.adjustStreamVolume(TEST_STREAM_FOR_USAGE, ADJUST_MUTE, 0);
            }
        }
    }

    private void checkMutedApi(int checkFlag) {
        InstrumentationRegistry.getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(
                Manifest.permission.MODIFY_AUDIO_ROUTING);

        AudioPlaybackConfiguration currentConfiguration = findConfiguration(checkFlag);
        assertTrue("APC should be muted", currentConfiguration.isMuted());
        assertTrue("APC muted by wrong source",
                (currentConfiguration.getMutedBy() & checkFlag) != 0);

        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
    }

    private AudioPlaybackConfiguration findConfiguration(int muteHint) {
        int uid;
        Context context = getContext();
        try {
            uid = context.getPackageManager().getApplicationInfo(context.getPackageName(),
                    PackageManager.ApplicationInfoFlags.of(0)).uid;
        } catch (PackageManager.NameNotFoundException e) {
            uid = -1;
        }

        AudioManager am = new AudioManager(getContext());
        List<AudioPlaybackConfiguration> configList = am.getActivePlaybackConfigurations();
        AudioPlaybackConfiguration result = null;
        for (AudioPlaybackConfiguration config : configList) {
            if (config.getClientUid() == uid && config.getAudioDeviceInfo() != null
                    && config.getAudioAttributes().getUsage() == TEST_USAGE
                    && config.getAudioAttributes().getContentType() == TEST_CONTENT) {
                Log.v(TAG,
                        "AudioPlaybackConfiguration " + config + " uid " + config.getClientUid());
                result = config;
                if ((config.getMutedBy() & muteHint) != 0) {
                    break;
                }
            }
        }
        assertNotNull("Could not find AudioPlaybackConfiguration for uid " + uid, result);
        return result;
    }

    private void initializeAudioTrack() {
        final int bufferSizeInBytes =
                TEST_AUDIO_TRACK_PLAY_SECONDS * TEST_AUDIO_TRACK_SAMPLERATE
                        * TEST_AUDIO_TRACK_CHANNELS;

        ByteBuffer audioData = createSoundDataInShortByteBuffer(bufferSizeInBytes,
                TEST_AUDIO_TRACK_SAMPLERATE, TEST_AUDIO_TRACK_FREQUENCY,
                TEST_AUDIO_TRACK_SWEEP);

        final AudioAttributes aa = (new AudioAttributes.Builder())
                .setUsage(TEST_USAGE)
                .setContentType(TEST_CONTENT)
                .build();

        mAt = new AudioTrack.Builder()
                .setAudioAttributes(aa)
                .setAudioFormat(new AudioFormat.Builder()
                        .setSampleRate(TEST_AUDIO_TRACK_SAMPLERATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build())
                .setBufferSizeInBytes(bufferSizeInBytes)
                .build();

        mAt.write(audioData, audioData.remaining(), WRITE_NON_BLOCKING);
    }

    private void initializeMediaPlayer() throws Exception {
        AudioManager am = new AudioManager(getContext());
        assertNotNull("Could not create AudioManager", am);

        final AudioAttributes aa = (new AudioAttributes.Builder())
                .setUsage(TEST_USAGE)
                .setContentType(TEST_CONTENT)
                .build();

        mMp = createPreparedMediaPlayer(R.raw.sine1khzs40dblong, aa,
                am.generateAudioSessionId());
    }

    @Nullable
    private MediaPlayer createPreparedMediaPlayer(
            @RawRes int resID, AudioAttributes aa, int session) throws Exception {
        final TestUtils.Monitor onPreparedCalled = new TestUtils.Monitor();
        final MediaPlayer mp = createPlayer(resID, aa, session);
        mp.setOnPreparedListener(mp1 -> onPreparedCalled.signal());
        mp.prepare();
        onPreparedCalled.waitForSignal(MEDIAPLAYER_PREPARE_TIMEOUT_MS);
        assertTrue(
                "MediaPlayer wasn't prepared in under " + MEDIAPLAYER_PREPARE_TIMEOUT_MS + " ms",
                onPreparedCalled.isSignalled());
        return mp;
    }

    private MediaPlayer createPlayer(
            @RawRes int resID, AudioAttributes aa, int session) throws IOException {
        MediaPlayer mp = new MediaPlayer();
        mp.setAudioAttributes(aa);
        mp.setAudioSessionId(session);
        AssetFileDescriptor afd = getContext().getResources().openRawResourceFd(resID);
        try {
            mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
        } finally {
            afd.close();
        }
        return mp;
    }

    private static SoundPool createSoundPool(AudioAttributes aa) {
        return new SoundPool.Builder()
                .setAudioAttributes(aa)
                .setMaxStreams(1)
                .build();
    }

    /** Loads a track and plays it with the passed {@link SoundPool}. */
    private static void playSoundPool(SoundPool sp, Context context) throws InterruptedException {
        final Object loadLock = new Object();
        final SoundPool zepool = sp;
        // load a sound and play it once load completion is reported
        sp.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
            @Override
            public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
                assertEquals("Receiving load completion for wrong SoundPool", zepool, sp);
                assertEquals("Load completion error", 0 /*success expected*/, status);
                synchronized (loadLock) {
                    loadLock.notify();
                }
            }
        });
        final int loadId = sp.load(context, R.raw.sine1320hz5sec, 1/*priority*/);
        synchronized (loadLock) {
            loadLock.wait(TEST_TIMEOUT_SOUNDPOOL_LOAD_MS);
        }

        int res = sp.play(loadId, 1.0f /*leftVolume*/, 1.0f /*rightVolume*/, 1 /*priority*/,
                0 /*loop*/, 1.0f/*rate*/);
        // FIXME SoundPool activity is not reported yet, but exercise creation/release with
        //       an AudioPlaybackCallback registered
        assertTrue("Error playing sound through SoundPool", res > 0);
    }

    private static void assertListsAreConsistent(List<AudioPlaybackConfiguration> config1,
            List<AudioPlaybackConfiguration> config2) {
        assertEquals("Different size of audio playback configurations reported", config1.size(),
                config2.size());
        assertTrue("Reported audio playback configurations are inconsistent",
                config1.containsAll(config2) && config2.containsAll(config1));
    }

    private void assertPlayerStartAndCallbackWithPlayerAttributes(
            MediaPlayer mp, MyAudioPlaybackCallback callback,
            int activePlayerCount, AudioAttributes aa) throws Exception{
        mp.start();

        assertTrue("onPlaybackConfigChanged play, format and device events expected ",
                callback.waitForCallbacks(3,
                        TEST_TIMING_TOLERANCE_MS + PLAY_ROUTING_TIMING_TOLERANCE_MS));
        assertEquals("number of active players not expected",
                // one more player active
                activePlayerCount/*expected*/, callback.getNbConfigs());
        assertTrue("Active player, attributes not found", hasAttr(callback.getConfigs(), aa));
    }

    private static class MyAudioPlaybackCallback extends AudioManager.AudioPlaybackCallback {
        private final Object mCbLock = new Object();
        @GuardedBy("mCbLock")
        private int mCalled;
        @GuardedBy("mCbLock")
        private List<AudioPlaybackConfiguration> mConfigs;

        final TestUtils.Monitor mOnCalledMonitor = new TestUtils.Monitor();

        void reset() {
            synchronized (mCbLock) {
                mCalled = 0;
                mConfigs = new ArrayList<AudioPlaybackConfiguration>();
            }
            mOnCalledMonitor.reset();
        }

        int getCbInvocationNumber() {
            synchronized (mCbLock) {
                return mCalled;
            }
        }

        int getNbConfigs() {
            return getConfigs().size();
        }

        List<AudioPlaybackConfiguration> getConfigs() {
            synchronized (mCbLock) {
                return mConfigs;
            }
        }

        MyAudioPlaybackCallback() {
            reset();
        }

        @Override
        public void onPlaybackConfigChanged(List<AudioPlaybackConfiguration> configs) {
            synchronized (mCbLock) {
                mCalled++;
                mConfigs = configs;
            }
            mOnCalledMonitor.signal();
        }

        public boolean waitForCallbacks(int calledCount, long timeoutMs)
                throws InterruptedException {
            int signalsCounted =
                    mOnCalledMonitor.waitForCountedSignals(calledCount, timeoutMs);
            return (signalsCounted == calledCount);
        }
    }

    private static class MyPlayer {
        Runnable mPlay;
        Consumer<Float> mSetClientVolume;
        Function<VolumeShaper.Configuration, VolumeShaper> mCreateVolumeShaper;

        MyPlayer(AudioTrack at) {
            mPlay = at::play;
            mSetClientVolume = at::setVolume;
            mCreateVolumeShaper = at::createVolumeShaper;
        }

        MyPlayer(MediaPlayer mp) {
            mPlay = mp::start;
            mSetClientVolume = mp::setVolume;
            mCreateVolumeShaper = mp::createVolumeShaper;
        }
    }

    private static boolean hasAttr(List<AudioPlaybackConfiguration> configs, AudioAttributes aa) {
        for (AudioPlaybackConfiguration apc : configs) {
            if (apc.getAudioAttributes().getContentType() == aa.getContentType()
                && apc.getAudioAttributes().getUsage() == aa.getUsage()
                && apc.getAudioAttributes().getFlags() == aa.getFlags()
                && anonymizeCapturePolicy(apc.getAudioAttributes().getAllowedCapturePolicy())
                    == aa.getAllowedCapturePolicy()) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasDevice(List<AudioPlaybackConfiguration> configs, AudioAttributes aa) {
        for (AudioPlaybackConfiguration apc : configs) {
            if (apc.getAudioAttributes().getContentType() == aa.getContentType()
                    && apc.getAudioAttributes().getUsage() == aa.getUsage()
                    && apc.getAudioAttributes().getFlags() == aa.getFlags()
                    && anonymizeCapturePolicy(apc.getAudioAttributes().getAllowedCapturePolicy())
                            == aa.getAllowedCapturePolicy()
                    && apc.getAudioDeviceInfo() != null) {
                return true;
            }
        }
        return false;
    }

    /** ALLOW_CAPTURE_BY_SYSTEM is anonymized to ALLOW_CAPTURE_BY_NONE. */
    @CapturePolicy
    private static int anonymizeCapturePolicy(@CapturePolicy int policy) {
        if (policy == ALLOW_CAPTURE_BY_SYSTEM) {
            return ALLOW_CAPTURE_BY_NONE;
        }
        return policy;
    }

    private boolean isValidPlatform(String testName) {
        if (!getContext().getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT)) {
            Log.w(TAG,"AUDIO_OUTPUT feature not found. This system might not have a valid "
                    + "audio output HAL, skipping test " + testName);
            return false;
        }
        return true;
    }
}
