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
 * limitations under the License.
 */

package android.media.audio.cts;

import static android.Manifest.permission.MODIFY_AUDIO_SETTINGS_PRIVILEGED;
import static android.Manifest.permission.QUERY_AUDIO_STATE;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.Manifest;
import android.annotation.Nullable;
import android.annotation.RawRes;
import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFocusRequest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.media.AudioRecord;
import android.media.AudioRouting;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.audio.Flags;
import android.media.cts.TestUtils;
import android.media.cts.Utils;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Vibrator;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.RequireDoesNotHaveFeature;
import com.android.bedstead.harrier.annotations.RequireFeature;
import com.android.bedstead.harrier.annotations.RequireNotAutomotive;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.permissions.PermissionContext;
import com.android.compatibility.common.util.NonMainlineTest;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@RunWith(BedsteadJUnit4.class)
@NonMainlineTest
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")

public class AudioFocusTest {
    private static final String TAG = "AudioFocusTest";

    // if the test APIs for robustness and additional checks are not available,
    // skip the parts of the tests that call them / take advantage of them
    private boolean mDeflakeApisAvailable = Flags.focusFreezeTestApi();

    private static final int TEST_TIMING_TOLERANCE_MS = 200;
    private static final long MEDIAPLAYER_PREPARE_TIMEOUT_MS = 2000;

    private static final AudioAttributes ATTR_DRIVE_DIR = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build();
    private static final AudioAttributes ATTR_MEDIA = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build();
    private static final AudioAttributes ATTR_A11Y = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build();
    private static final AudioAttributes ATTR_CALL = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build();

    private static final String TEST_CALL_ID = "fake call";

    private Context mContext;
    private AudioManager mAM;
    private Instrumentation mInstrumentation;
    /** notification volume to restore */
    private int mInitialNotificationVolume;
    /** ringer mode to restore */
    private int mInitialRingerMode;
    private boolean mHasVibration;

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() throws Exception {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = mInstrumentation.getTargetContext();
        mAM = new AudioManager(mContext);

        Vibrator vibrator = mContext.getSystemService(Vibrator.class);
        mHasVibration = (vibrator != null) && vibrator.hasVibrator();

        mInitialRingerMode = mAM.getRingerMode();
        // need to set ringer mode to normal before starting the test so the volume (to be restored)
        // can be queried
        Utils.toggleNotificationPolicyAccess(mContext.getPackageName(), mInstrumentation, true);
        mAM.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
        mInitialNotificationVolume = mAM.getStreamVolume(
                AudioAttributes.toLegacyStreamType(NOTIFICATION_ATTRIBUTES));
        Utils.toggleNotificationPolicyAccess(mContext.getPackageName(), mInstrumentation, false);
        // for query of fade out duration, focus request/abandon test methods, and focus requests
        // independently of test runner procstate
        mInstrumentation.getUiAutomation().adoptShellPermissionIdentity(
                MODIFY_AUDIO_SETTINGS_PRIVILEGED, QUERY_AUDIO_STATE);
    }

    @After
    public void teardown() throws Exception {
        stopRecording();
        if (mDeflakeApisAvailable) {
            try (PermissionContext p = TestApis.permissions().withPermission(
                    Manifest.permission.MODIFY_AUDIO_SETTINGS_PRIVILEGED)) {
                mAM.exitAudioFocusFreezeForTest();
            }
        }
        // restore ringer mode and notification volume
        Utils.toggleNotificationPolicyAccess(
                mContext.getPackageName(), mInstrumentation, true);
        mAM.setStreamVolume(AudioAttributes.toLegacyStreamType(NOTIFICATION_ATTRIBUTES),
                mInitialNotificationVolume, 0);
        mAM.setRingerMode(mInitialRingerMode);
        Utils.toggleNotificationPolicyAccess(mContext.getPackageName(), mInstrumentation, false);
        mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
    }

    @Test
    public void testInvalidAudioFocusRequestDelayNoListener() throws Exception {
        AudioFocusRequest req = null;
        Exception ex = null;
        try {
            req = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAcceptsDelayedFocusGain(true).build();
        } catch (Exception e) {
            // expected
            ex = e;
        }
        assertNotNull("No exception was thrown for an invalid build", ex);
        assertEquals("Wrong exception thrown", ex.getClass(), IllegalStateException.class);
        assertNull("Shouldn't be able to create delayed request without listener", req);
    }

    @Test
    public void testInvalidAudioFocusRequestPauseOnDuckNoListener() throws Exception {
        AudioFocusRequest req = null;
        Exception ex = null;
        try {
            req = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setWillPauseWhenDucked(true).build();
        } catch (Exception e) {
            // expected
            ex = e;
        }
        assertNotNull("No exception was thrown for an invalid build", ex);
        assertEquals("Wrong exception thrown", ex.getClass(), IllegalStateException.class);
        assertNull("Shouldn't be able to create pause-on-duck request without listener", req);
    }

    @Test
    public void testAudioFocusRequestBuilderDefault() throws Exception {
        final AudioFocusRequest reqDefaults =
                new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).build();
        assertEquals("Focus gain differs", AudioManager.AUDIOFOCUS_GAIN,
                reqDefaults.getFocusGain());
        assertEquals("Listener differs", null, reqDefaults.getOnAudioFocusChangeListener());
        assertEquals("Handler differs", null, reqDefaults.getOnAudioFocusChangeListenerHandler());
        assertEquals("Duck behavior differs", false, reqDefaults.willPauseWhenDucked());
        assertEquals("Delayed focus differs", false, reqDefaults.acceptsDelayedFocusGain());
    }

    @Test
    public void testAudioFocusRequestCopyBuilder() throws Exception {
        final FocusChangeListener focusListener = new FocusChangeListener();
        final int focusGain = AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK;
        final AudioFocusRequest reqToCopy =
                new AudioFocusRequest.Builder(focusGain)
                .setAudioAttributes(ATTR_DRIVE_DIR)
                .setOnAudioFocusChangeListener(focusListener)
                .setAcceptsDelayedFocusGain(true)
                .setWillPauseWhenDucked(true)
                .build();

        AudioFocusRequest newReq = new AudioFocusRequest.Builder(reqToCopy).build();
        assertEquals("AudioAttributes differ", ATTR_DRIVE_DIR, newReq.getAudioAttributes());
        assertEquals("Listener differs", focusListener, newReq.getOnAudioFocusChangeListener());
        assertEquals("Focus gain differs", focusGain, newReq.getFocusGain());
        assertEquals("Duck behavior differs", true, newReq.willPauseWhenDucked());
        assertEquals("Delayed focus differs", true, newReq.acceptsDelayedFocusGain());

        newReq = new AudioFocusRequest.Builder(reqToCopy)
                .setWillPauseWhenDucked(false)
                .setFocusGain(AudioManager.AUDIOFOCUS_GAIN)
                .build();
        assertEquals("AudioAttributes differ", ATTR_DRIVE_DIR, newReq.getAudioAttributes());
        assertEquals("Listener differs", focusListener, newReq.getOnAudioFocusChangeListener());
        assertEquals("Focus gain differs", AudioManager.AUDIOFOCUS_GAIN, newReq.getFocusGain());
        assertEquals("Duck behavior differs", false, newReq.willPauseWhenDucked());
        assertEquals("Delayed focus differs", true, newReq.acceptsDelayedFocusGain());
    }

    @Test
    public void testNullListenerHandlerNpe() throws Exception {
        final AudioFocusRequest.Builder afBuilder =
                new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN);
        try {
            afBuilder.setOnAudioFocusChangeListener(null);
            fail("no NPE when setting a null listener");
        } catch (NullPointerException e) {
        }

        final HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        final Handler h = new Handler(handlerThread.getLooper());
        final AudioFocusRequest.Builder afBuilderH =
                new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN);
        try {
            afBuilderH.setOnAudioFocusChangeListener(null, h);
            fail("no NPE when setting a null listener with non-null Handler");
        } catch (NullPointerException e) {
        }

        final AudioFocusRequest.Builder afBuilderL =
                new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN);
        try {
            afBuilderL.setOnAudioFocusChangeListener(new FocusChangeListener(), null);
            fail("no NPE when setting a non-null listener with null Handler");
        } catch (NullPointerException e) {
        }
    }

    @Test
    public void testAudioFocusRequestGainLoss() throws Exception {
        final AudioAttributes[] attributes = { ATTR_DRIVE_DIR, ATTR_MEDIA };
        doTestTwoPlayersGainLoss(AudioManager.AUDIOFOCUS_GAIN, attributes, false /*no handler*/);
    }

    @Test
    public void testAudioFocusRequestGainLossHandler() throws Exception {
        final AudioAttributes[] attributes = { ATTR_DRIVE_DIR, ATTR_MEDIA };
        doTestTwoPlayersGainLoss(AudioManager.AUDIOFOCUS_GAIN, attributes, true /*with handler*/);
    }

    @Test
    public void testAudioFocusRequestGainLossTransient() throws Exception {
        final AudioAttributes[] attributes = { ATTR_DRIVE_DIR, ATTR_MEDIA };
        doTestTwoPlayersGainLoss(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT, attributes,
                false /*no handler*/);
    }

    @Test
    public void testAudioFocusRequestGainLossTransientHandler() throws Exception {
        final AudioAttributes[] attributes = { ATTR_DRIVE_DIR, ATTR_MEDIA };
        doTestTwoPlayersGainLoss(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT, attributes,
                true /*with handler*/);
    }

    @Test
    @RequireNotAutomotive(reason = "Auto has its own focus policy")
    public void testAudioFocusRequestGainLossTransientDuck() throws Exception {
        final AudioAttributes[] attributes = { ATTR_DRIVE_DIR, ATTR_MEDIA };
        doTestTwoPlayersGainLoss(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK, attributes,
                false /*no handler*/);
    }

    @Test
    @RequireNotAutomotive(reason = "Auto has its own focus policy")
    public void testAudioFocusRequestGainLossTransientDuckHandler() throws Exception {
        final AudioAttributes[] attributes = { ATTR_DRIVE_DIR, ATTR_MEDIA };
        doTestTwoPlayersGainLoss(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK, attributes,
                true /*with handler*/);
    }

    @Test
    @RequireNotAutomotive(reason = "Auto has its own focus policy")
    public void testAudioFocusRequestForceDuckNotA11y() throws Exception {
        // verify a request that is "force duck"'d still causes loss of focus because it doesn't
        // come from an A11y service, and requests are from same uid
        final AudioAttributes[] attributes = {ATTR_MEDIA, ATTR_A11Y};
        doTestTwoPlayersGainLoss(AudioManager.AUDIOFOCUS_GAIN,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK, attributes,
                false /*no handler*/, true /* forceDucking */);
    }

    @Test
    public void testAudioFocusRequestA11y() throws Exception {
        final AudioAttributes[] attributes = {ATTR_DRIVE_DIR, ATTR_A11Y};
        doTestTwoPlayersGainLoss(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE, attributes,
                false /*no handler*/, false /* forceDucking */);
    }

    /**
     * Test delayed focus behaviors with the sequence:
     * 1/ (simulated) call with focus lock: media gets FOCUS_LOSS_TRANSIENT
     * 2/ media requests FOCUS_GAIN + delay OK: is delayed
     * 3/ call ends: media gets FOCUS_GAIN
     * @throws Exception when failing
     */
    @Test
    public void testAudioMediaFocusDelayedByCall() throws Exception {
        Log.i(TAG, "testAudioMediaFocusDelayedByCall");
        Handler handler = new Handler(Looper.getMainLooper());

        AudioFocusRequest callFocusReq =
                new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                        .setAudioAttributes(ATTR_CALL)
                        .setLocksFocus(true)
                        .build();

        FocusChangeListener mediaListener = new FocusChangeListener();
        AudioFocusRequest mediaFocusReq =
                new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(ATTR_MEDIA)
                        .setAcceptsDelayedFocusGain(true)
                        .setOnAudioFocusChangeListener(mediaListener, handler)
                        .build();

        try {
            // call requests audio focus
            int res = mAM.requestAudioFocusForTest(callFocusReq, TEST_CALL_ID, 1977,
                    Build.VERSION_CODES.S);
            assertEquals("call request failed", AudioManager.AUDIOFOCUS_REQUEST_GRANTED, res);
            // media requests audio focus, verify it's delayed
            res = mAM.requestAudioFocus(mediaFocusReq);
            assertEquals("Focus request from media wasn't delayed",
                    AudioManager.AUDIOFOCUS_REQUEST_DELAYED, res);
            // end the call, verify media gets focus
            mAM.abandonAudioFocusForTest(callFocusReq, TEST_CALL_ID);
            mediaListener.waitForFocusChange("testAudioMediaFocusDelayedByCall",
                    TEST_TIMING_TOLERANCE_MS, /* shouldAcquire= */ true);

            assertEquals("Focus gain not dispatched to media after call",
                    AudioManager.AUDIOFOCUS_GAIN, mediaListener.getFocusChangeAndReset());
        } finally {
            mAM.abandonAudioFocusForTest(callFocusReq, TEST_CALL_ID);
            mAM.abandonAudioFocusRequest(mediaFocusReq);
        }
    }

    /**
     * Test delayed focus behaviors with the sequence:
     * 1/ media requests FOCUS_GAIN
     * 2/ (simulated) call with focus lock: media gets FOCUS_LOSS_TRANSIENT
     * 3/ drive dir requests FOCUS_GAIN + delay OK: is delayed + media gets FOCUS_LOSS
     * 4/ call ends: drive dir gets FOCUS_GAIN
     * @throws Exception when failing
     */
    @Test
    @RequireNotAutomotive(reason = "Auto has its own focus policy")
    @RequireDoesNotHaveFeature(value = PackageManager.FEATURE_PC) // not required for Desktop
    public void testAudioFocusDelayedByCall() throws Exception {
        Log.i(TAG, "testAudioFocusDelayedByCall");
        final HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        final Handler handler = new Handler(handlerThread.getLooper());

        final AudioFocusRequest callFocusReq =
                new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                        .setLocksFocus(true).build();
        final FocusChangeListener mediaListener = new FocusChangeListener();
        final AudioFocusRequest mediaFocusReq =
                new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(ATTR_MEDIA)
                        .setOnAudioFocusChangeListener(mediaListener, handler)
                        .build();
        final FocusChangeListener driveListener = new FocusChangeListener();
        final AudioFocusRequest driveFocusReq =
                new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(ATTR_DRIVE_DIR)
                        .setAcceptsDelayedFocusGain(true)
                        .setOnAudioFocusChangeListener(driveListener, handler)
                        .build();

        try {
            // media requests audio focus
            int res = mAM.requestAudioFocus(mediaFocusReq);
            assertEquals("media request failed", AudioManager.AUDIOFOCUS_REQUEST_GRANTED, res);
            // call requests audio focus
            mAM.requestAudioFocusForTest(callFocusReq, TEST_CALL_ID, 1977, Build.VERSION_CODES.S);
            assertEquals("call request failed", AudioManager.AUDIOFOCUS_REQUEST_GRANTED, res);
            // verify media lost focus with LOSS_TRANSIENT
            mediaListener.waitForFocusChange("testAudioFocusDelayedByCall",
                    TEST_TIMING_TOLERANCE_MS, /* shouldAcquire= */ true);
            assertEquals("Focus loss not dispatched to media after call start",
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, mediaListener.getFocusChangeAndReset());
            // drive dir requests audio focus, verify it's delayed
            res = mAM.requestAudioFocus(driveFocusReq);
            assertEquals("Focus request from drive dir. wasn't delayed",
                    AudioManager.AUDIOFOCUS_REQUEST_DELAYED, res);
            // verify media lost focus with LOSS as it's being kicked out of the focus stack
            mediaListener.waitForFocusChange("testAudioFocusDelayedByCall",
                    TEST_TIMING_TOLERANCE_MS, /* shouldAcquire= */ true);
            assertEquals("Focus loss not dispatched to media after drive dir delayed focus",
                    AudioManager.AUDIOFOCUS_LOSS, mediaListener.getFocusChangeAndReset());
            // end the call, verify drive dir gets focus
            mAM.abandonAudioFocusForTest(callFocusReq, TEST_CALL_ID);
            driveListener.waitForFocusChange("testAudioFocusDelayedByCall",
                    TEST_TIMING_TOLERANCE_MS, /* shouldAcquire= */ true);
            assertEquals("Focus gain not dispatched to drive dir after call",
                    AudioManager.AUDIOFOCUS_GAIN, driveListener.getFocusChangeAndReset());
        } finally {
            mAM.abandonAudioFocusForTest(callFocusReq, TEST_CALL_ID);
            mAM.abandonAudioFocusRequest(driveFocusReq);
            mAM.abandonAudioFocusRequest(mediaFocusReq);
            handler.getLooper().quit();
            handlerThread.quitSafely();
        }
    }

    /**
     * Test delayed focus behaviors with the sequence:
     * 1/ media requests FOCUS_GAIN
     * 2/ (simulated) call with focus lock: media gets FOCUS_LOSS_TRANSIENT
     * 3/ drive dir requests AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK + delay OK: is delayed
     * 4/ call ends: drive dir gets FOCUS_GAIN
     * 5/ drive dir ends: media gets FOCUS_GAIN (because it was still in the stack,
     *                    unlike in testAudioFocusDelayedByCall)
     * @throws Exception when failing
     */
    @Test
    @RequireNotAutomotive(reason = "Auto has its own focus policy")
    @RequireDoesNotHaveFeature(value = PackageManager.FEATURE_PC) // not required for Desktop
    public void testAudioFocusTransientDelayedByCall() throws Exception {
        Log.i(TAG, "testAudioFocusTransientDelayedByCall start");
        final HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        final Handler handler = new Handler(handlerThread.getLooper());

        final AudioFocusRequest callFocusReq =
                new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                        .setLocksFocus(true).build();
        final FocusChangeListener mediaListener = new FocusChangeListener();
        final AudioFocusRequest mediaFocusReq =
                new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(ATTR_MEDIA)
                        .setOnAudioFocusChangeListener(mediaListener, handler)
                        .build();
        final FocusChangeListener driveListener = new FocusChangeListener();
        final AudioFocusRequest driveFocusReq =
                new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                        .setAudioAttributes(ATTR_DRIVE_DIR)
                        .setAcceptsDelayedFocusGain(true)
                        .setOnAudioFocusChangeListener(driveListener, handler)
                        .build();

        try {
            // media requests audio focus
            int res = mAM.requestAudioFocus(mediaFocusReq);
            assertEquals("media request failed", AudioManager.AUDIOFOCUS_REQUEST_GRANTED, res);
            // call requests audio focus
            mAM.requestAudioFocusForTest(callFocusReq, TEST_CALL_ID, 1977, Build.VERSION_CODES.S);
            assertEquals("call request failed", AudioManager.AUDIOFOCUS_REQUEST_GRANTED, res);
            // verify media lost focus with LOSS_TRANSIENT
            mediaListener.waitForFocusChange("testAudioFocusTransientDelayedByCall",
                    TEST_TIMING_TOLERANCE_MS, /* shouldAcquire= */ true);
            assertEquals("Focus loss not dispatched to media after call start",
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, mediaListener.getFocusChangeAndReset());
            // drive dir requests audio focus, verify it's delayed
            res = mAM.requestAudioFocus(driveFocusReq);
            assertEquals("Focus request from drive dir. wasn't delayed",
                    AudioManager.AUDIOFOCUS_REQUEST_DELAYED, res);
            // end the call, verify drive dir gets focus, and media didn't get focus change
            mAM.abandonAudioFocusForTest(callFocusReq, TEST_CALL_ID);
            driveListener.waitForFocusChange("testAudioFocusTransientDelayedByCall",
                    TEST_TIMING_TOLERANCE_MS, /* shouldAcquire= */ true);
            assertEquals("Focus gain not dispatched to drive dir after call",
                    AudioManager.AUDIOFOCUS_GAIN, driveListener.getFocusChangeAndReset());
            mediaListener.waitForFocusChange("testAudioFocusTransientDelayedByCall",
                    TEST_TIMING_TOLERANCE_MS, /* shouldAcquire= */ false);
            assertEquals("Focus change was dispatched to media",
                    AudioManager.AUDIOFOCUS_NONE, mediaListener.getFocusChangeAndReset());
            // end the drive dir, verify media gets focus
            mAM.abandonAudioFocusRequest(driveFocusReq);
            mediaListener.waitForFocusChange("testAudioFocusTransientDelayedByCall",
                    TEST_TIMING_TOLERANCE_MS, /* shouldAcquire= */ true);
            assertEquals("Focus gain not dispatched to media after drive dir",
                    AudioManager.AUDIOFOCUS_GAIN, mediaListener.getFocusChangeAndReset());
        } finally {
            mAM.abandonAudioFocusForTest(callFocusReq, TEST_CALL_ID);
            mAM.abandonAudioFocusRequest(driveFocusReq);
            mAM.abandonAudioFocusRequest(mediaFocusReq);
            handler.getLooper().quit();
            handlerThread.quitSafely();
        }
    }

    private boolean hasMicFeature() {
        return mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_MICROPHONE);
    }

    /**
     * Test delayed focus loss after fade out
     * @throws Exception on failure
     */
    @Test
    @RequireNotAutomotive(reason = "Auto has its own focus policy")
    public void testAudioFocusRequestMediaGainLossWithPlayer() throws Exception {
        Log.i(TAG, "testAudioFocusRequestMediaGainLossWithPlayer start");

        final int NB_FOCUS_OWNERS = 2;
        final AudioFocusRequest[] focusRequests = new AudioFocusRequest[NB_FOCUS_OWNERS];
        final FocusChangeListener[] focusListeners = new FocusChangeListener[NB_FOCUS_OWNERS];
        final int FOCUS_UNDER_TEST = 0;// index of focus owner to be tested
        final int FOCUS_SIMULATED = 1; // index of focus requester used to simulate a request coming
                                       //   from another client on a different UID than CTS

        final HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        final Handler handler = new Handler(handlerThread.getLooper());

        final AudioAttributes mediaAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build();
        for (int focusIndex : new int[]{ FOCUS_UNDER_TEST, FOCUS_SIMULATED }) {
            focusListeners[focusIndex] = new FocusChangeListener();
            focusRequests[focusIndex] = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(mediaAttributes)
                    .setOnAudioFocusChangeListener(focusListeners[focusIndex], handler)
                    .build();
        }

        MediaPlayer mp = null;
        final String simFocusClientId = "fakeClientId";
        try {
            // set up the test conditions: a focus owner is playing media on a MediaPlayer
            mp = createPreparedMediaPlayer(R.raw.sine1khzs40dblong, mediaAttributes);
            int res = mAM.requestAudioFocus(focusRequests[FOCUS_UNDER_TEST]);
            assertEquals("real focus request failed",
                    AudioManager.AUDIOFOCUS_REQUEST_GRANTED, res);
            mp.start();
            Thread.sleep(TEST_TIMING_TOLERANCE_MS);
            long fadeDuration = mAM.getFadeOutDurationOnFocusLossMillis(mediaAttributes);
            assertTrue("Fade out duration cannot be negative", fadeDuration >= 0);
            // since SystemClock#uptimeMillis is not always accurate, consider
            // an error margin of 10%
            long errMargin = (fadeDuration / 10);
            fadeDuration = fadeDuration - errMargin;

            Log.i(TAG, "using corrected fade out duration = " + fadeDuration);

            res = mAM.requestAudioFocusForTest(focusRequests[FOCUS_SIMULATED],
                    simFocusClientId, Integer.MAX_VALUE /*fakeClientUid*/, Build.VERSION_CODES.S);
            assertEquals("test focus request failed",
                    AudioManager.AUDIOFOCUS_REQUEST_GRANTED, res);

            if (fadeDuration > 0) {
                assertEquals("Focus loss dispatched too early", AudioManager.AUDIOFOCUS_NONE,
                        focusListeners[FOCUS_UNDER_TEST].getFocusChangeAndReset());
                focusListeners[FOCUS_UNDER_TEST]
                        .waitForFocusChange(
                                "testAudioFocusRequestMediaGainLossWithPlayer fadeDuration",
                                fadeDuration, /* shouldAcquire= */ false);
            }

            focusListeners[FOCUS_UNDER_TEST].waitForFocusChange(
                    "testAudioFocusRequestMediaGainLossWithPlayer",
                    TEST_TIMING_TOLERANCE_MS + errMargin, /* shouldAcquire= */ true);
            assertEquals("Focus loss not dispatched", AudioManager.AUDIOFOCUS_LOSS,
                    focusListeners[FOCUS_UNDER_TEST].getFocusChangeAndReset());

        }
        finally {
            handler.getLooper().quit();
            handlerThread.quitSafely();
            if (mp != null) {
                mp.release();
            }
            mAM.abandonAudioFocusForTest(focusRequests[FOCUS_SIMULATED], simFocusClientId);
            mAM.abandonAudioFocusRequest(focusRequests[FOCUS_UNDER_TEST]);
        }
    }

    private static final AudioFocusRequest EXCLUSIVE_FOCUS_REQUEST = new AudioFocusRequest.Builder(
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .build())
            .build();

    private static final AudioAttributes NOTIFICATION_ATTRIBUTES = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION).build();
    /**
     * Test GAIN_TRANSIENT_EXCLUSIVE and AudioManager#shouldNotificationSoundPlay
     * by changing ringer mode
     * @throws Exception on failure
     */
    @Test
    @RequireNotAutomotive(reason = "Auto has its own focus policy")
    @RequireDoesNotHaveFeature(value = PackageManager.FEATURE_PC) // not required for Desktop
    @AppModeFull(reason = "Instant apps cannot hold permission.MODIFY_AUDIO_SETTINGS_PRIVILEGED")
    @RequiresFlagsEnabled(Flags.FLAG_FOCUS_EXCLUSIVE_WITH_RECORDING)
    public void testAudioFocusExclusive() throws Exception {
        Log.i(TAG, "testAudioFocusExclusive start");
        if (!mDeflakeApisAvailable) {
            Log.i(TAG, "running testAudioFocusExclusive without deflake test APIs");
        }

        /*try (PermissionContext p = TestApis.permissions().withPermission(
                Manifest.permission.QUERY_AUDIO_STATE,
                Manifest.permission.MODIFY_AUDIO_SETTINGS_PRIVILEGED)) */
        try {
            final int testUid = android.os.Process.myUid();
            if (mDeflakeApisAvailable) {
                assertTrue(mAM.enterAudioFocusFreezeForTest(Arrays.asList(testUid)));
            }
            Utils.toggleNotificationPolicyAccess(
                    mContext.getPackageName(), mInstrumentation, true);

            mAM.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
            final int streamType = AudioAttributes.toLegacyStreamType(NOTIFICATION_ATTRIBUTES);
            mAM.setStreamVolume(streamType, mAM.getStreamMaxVolume(streamType), 0);
            assertEquals("GAIN_TRANSIENT_EXCLUSIVE request failed",
                    AudioManager.AUDIOFOCUS_REQUEST_GRANTED,
                    mAM.requestAudioFocus(EXCLUSIVE_FOCUS_REQUEST));
            // RINGER_MODE_NORMAL + GAIN_TRANSIENT_EXCLUSIVE expect shouldNotifSoundPlay true
            assertTrue("Wrong shouldNotificationSoundPlay for ringer NORMAL + focus exclusive",
                    mAM.shouldNotificationSoundPlay(NOTIFICATION_ATTRIBUTES));

            if (mHasVibration) {
                mAM.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
                // RINGER_MODE_VIBRATE + GAIN_TRANSIENT_EXCLUSIVE expect shouldNotifSoundPlay false
                assertFalse(
                        "Wrong shouldNotificationSoundPlay for ringer VIBRATE + focus exclusive",
                        mAM.shouldNotificationSoundPlay(NOTIFICATION_ATTRIBUTES));
            }

            mAM.setRingerMode(AudioManager.RINGER_MODE_SILENT);
            // RINGER_MODE_SILENT + GAIN_TRANSIENT_EXCLUSIVE expect shouldNotifSoundPlay false
            assertFalse("Wrong shouldNotificationSoundPlay for ringer SILENT + focus exclusive",
                    mAM.shouldNotificationSoundPlay(NOTIFICATION_ATTRIBUTES));

            mAM.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
            // RINGER_MODE_NORMAL + GAIN_TRANSIENT_EXCLUSIVE expect shouldNotifSoundPlay true
            assertTrue("Wrong shouldNotificationSoundPlay for ringer SILENT + focus exclusive",
                    mAM.shouldNotificationSoundPlay(NOTIFICATION_ATTRIBUTES));
        } finally {
            mAM.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
            mAM.abandonAudioFocusRequest(EXCLUSIVE_FOCUS_REQUEST);
            Utils.toggleNotificationPolicyAccess(
                    mContext.getPackageName(), mInstrumentation, false);
        }
    }

    /**
     * Test GAIN_TRANSIENT_EXCLUSIVE and AudioManager#shouldNotificationSoundPlay
     * by changing ringer mode and recording
     * @throws Exception on failure
     */
    @Test
    @RequireNotAutomotive(reason = "Auto has its own focus policy")
    @RequireDoesNotHaveFeature(value = PackageManager.FEATURE_PC) // not required for Desktop
    @RequireFeature(value = PackageManager.FEATURE_MICROPHONE)
    @AppModeFull(reason = "Instant apps cannot hold permission.MODIFY_AUDIO_SETTINGS_PRIVILEGED")
    @RequiresFlagsEnabled(Flags.FLAG_FOCUS_EXCLUSIVE_WITH_RECORDING)
    public void testAudioFocusExclusiveAndRecording() throws Exception {
        Log.i(TAG, "testAudioFocusExclusiveAndRecording start");
        if (!mDeflakeApisAvailable) {
            Log.i(TAG, "running testAudioFocusExclusiveAndRecording without deflake test APIs");
        }

        try {
            final int testUid = android.os.Process.myUid();
            if (mDeflakeApisAvailable) {
                assertTrue(mAM.enterAudioFocusFreezeForTest(Arrays.asList(testUid)));
            }
            Utils.toggleNotificationPolicyAccess(
                    mContext.getPackageName(), mInstrumentation, true);

            mAM.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
            final int streamType = AudioAttributes.toLegacyStreamType(NOTIFICATION_ATTRIBUTES);
            mAM.setStreamVolume(streamType, mAM.getStreamMaxVolume(streamType), 0);
            assertEquals("GAIN_TRANSIENT_EXCLUSIVE request failed",
                    AudioManager.AUDIOFOCUS_REQUEST_GRANTED,
                    mAM.requestAudioFocus(EXCLUSIVE_FOCUS_REQUEST));

            startRecording();
            // RINGER_MODE_NORMAL + GAIN_TRANSIENT_EXCLUSIVE + recording
            //     expect shouldNotifSoundPlay false
            assertFalse("Wrong shouldNotificationSoundPlay for ringer focus exclusive + recording",
                    mAM.shouldNotificationSoundPlay(NOTIFICATION_ATTRIBUTES));

            mAM.setRingerMode(AudioManager.RINGER_MODE_SILENT);
            // RINGER_MODE_SILENT + GAIN_TRANSIENT_EXCLUSIVE + recording
            //      expect shouldNotifSoundPlay false
            assertFalse("Wrong shouldNotificationSoundPlay for ringer SILENT + "
                    + "focus exclusive + recording",
                    mAM.shouldNotificationSoundPlay(NOTIFICATION_ATTRIBUTES));

            stopRecording();
            mAM.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
            // RINGER_MODE_NORMAL + GAIN_TRANSIENT_EXCLUSIVE + no more recording
            //     expect shouldNotifSoundPlay true
            assertTrue("Wrong shouldNotificationSoundPlay for focus exclusive + recording",
                    mAM.shouldNotificationSoundPlay(NOTIFICATION_ATTRIBUTES));

        } finally {
            mAM.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
            mAM.abandonAudioFocusRequest(EXCLUSIVE_FOCUS_REQUEST);
            Utils.toggleNotificationPolicyAccess(
                    mContext.getPackageName(), mInstrumentation, false);
        }
    }

    private AudioRecord mAudioRecord;
    private AudioTestUtil.AudioRecordingCallbackUtil mRecordingCallback;

    /**
     * Start recording audio and returns when the recording is reported as active
     */
    private void startRecording() {
        assertNull("Non null MediaRecorder before starting record, bad state", mAudioRecord);
        mAudioRecord = new AudioRecord.Builder()
                .setAudioFormat(new AudioFormat.Builder()
                        .setSampleRate(8000)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
                .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                .setBufferSizeInBytes(
                        AudioRecord.getMinBufferSize(8000,
                                AudioFormat.CHANNEL_IN_MONO,
                                AudioFormat.ENCODING_PCM_16BIT) * 10)
                .build();
        mRecordingCallback = new AudioTestUtil.AudioRecordingCallbackUtil(
                mAudioRecord.getAudioSessionId(), MediaRecorder.AudioSource.VOICE_RECOGNITION);
        mAudioRecord.registerAudioRecordingCallback(Executors.newSingleThreadExecutor(),
                mRecordingCallback);
        mAudioRecord.startRecording();
        assertEquals("Invalid recording state, system in bad state",
                AudioRecord.RECORDSTATE_RECORDING, mAudioRecord.getRecordingState());

        mRecordingCallback.await(TEST_TIMING_TOLERANCE_MS);
        assertTrue(mRecordingCallback.mCalled);
        assertTrue(mRecordingCallback.hasRecording(mAudioRecord.getAudioSessionId(),
                MediaRecorder.AudioSource.VOICE_RECOGNITION));
        mRecordingCallback.reset();
    }

    private void stopRecording() {
        if (mAudioRecord == null) {
            return;
        }
        final int session = mAudioRecord.getAudioSessionId();
        mAudioRecord.release();
        mRecordingCallback.await(TEST_TIMING_TOLERANCE_MS);
        assertFalse(mRecordingCallback.hasRecording(session,
                MediaRecorder.AudioSource.VOICE_RECOGNITION));
        mAudioRecord = null;
        mRecordingCallback = null;
    }

    private void runDuckedUidsTest(String testName, AudioAttributes mediaAttributes,
                                   boolean expectDuck) throws Exception {
        if (!mDeflakeApisAvailable) {
            Log.i(TAG, "running " + testName + " without deflake test APIs");
        }

        java.util.List<Integer> duckedUids;
        if (mDeflakeApisAvailable) {
            duckedUids = mAM.getFocusDuckedUidsForTest();
            assertEquals("Test start, expected no ducked UIDs bug got " + duckedUids,
                    0, duckedUids.size());
        }

        final int NbFocusOwners = 3;
        final AudioFocusRequest[] focusRequests = new AudioFocusRequest[NbFocusOwners];
        final FocusChangeListener[] focusListeners = new FocusChangeListener[NbFocusOwners];
        // index of focus owner to be tested, has an active player
        final int FocusUnderTest = 0;
        // index of focus requester used to simulate a request coming from another client
        // on a different UID than CTS
        final int FocusHelperAssist = 1; // will simulate assistant
        final int FocusHelperMedia = 2;  // will simulate another media player
        final int FocusHelperAssistUid = Integer.MAX_VALUE;
        final int FocusHelperMediaUid = Integer.MAX_VALUE - 2;
        // index of another simulated focus requester
        int[] focusRequestTypes = {
                AudioManager.AUDIOFOCUS_GAIN                    /*FocusUnderTest*/,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK /*FocusHelperAssist*/,
                AudioManager.AUDIOFOCUS_GAIN                    /*FocusHelperMedia*/ };

        final HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        final Handler handler = new Handler(handlerThread.getLooper());

        final AudioAttributes assistantAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
        AudioAttributes[] focusAttr = {
                mediaAttributes     /*FocusUnderTest*/,
                assistantAttributes /*FocusHelperAssist*/,
                mediaAttributes     /*FocusHelperMedia*/
        };
        for (int focusIndex = 0; focusIndex < NbFocusOwners; focusIndex++) {
            focusListeners[focusIndex] = new FocusChangeListener();
            focusRequests[focusIndex] = new AudioFocusRequest.Builder(focusRequestTypes[focusIndex])
                    .setAudioAttributes(focusAttr[focusIndex])
                    .setOnAudioFocusChangeListener(focusListeners[focusIndex], handler)
                    .build();
        }

        MediaPlayer mp = null;
        final String assistFocusClientId = "fakeAssistantClientId";
        final String mediaFocusClientId = "fakeMediaClientId";
        final int playerUnderTestUid = android.os.Process.myUid();
        try {
            // prevent audio focus from apps other than CTS and the fake UIDs for test
            if (mDeflakeApisAvailable) {
                assertTrue(mAM.enterAudioFocusFreezeForTest(Arrays.asList(
                        FocusHelperAssistUid, FocusHelperMediaUid, playerUnderTestUid)));
            }
            // set up the test conditions: a focus owner is playing media on a MediaPlayer
            mp = createPreparedMediaPlayer(R.raw.sine1khzs40dblong, mediaAttributes);
            final MediaPlayerRoutingListener routingListener = new MediaPlayerRoutingListener(mp);
            int res = mAM.requestAudioFocus(focusRequests[FocusUnderTest]);
            assertEquals("real focus request failed",
                    AudioManager.AUDIOFOCUS_REQUEST_GRANTED, res);
            mp.setLooping(true);
            mp.start();
            routingListener.waitForRoutingChange(2 * TEST_TIMING_TOLERANCE_MS);

            // assistant use case requests focus with GAIN_TRANSIENT_MAY_DUCK
            res = mAM.requestAudioFocusForTest(focusRequests[FocusHelperAssist],
                    assistFocusClientId, FocusHelperAssistUid /*fakeClientUid*/,
                    Build.VERSION_CODES.S);
            assertEquals("assistant (test) focus request failed",
                    AudioManager.AUDIOFOCUS_REQUEST_GRANTED, res);
            if (expectDuck) {
                // after the GAIN_TRANSIENT_MAY_DUCK request, the media
                // gets ducked (strong because assistant) by the framework,
                // thus no focus loss is sent, but the player's UID should be in the list
                // of the ducked UIDs (which in theory should be of size 1, but we only check it's
                // in the list for test robustness)
                Thread.sleep(TEST_TIMING_TOLERANCE_MS);
                if (mDeflakeApisAvailable) {
                    duckedUids = mAM.getFocusDuckedUidsForTest();
                    assertTrue("List of ducked UIDs doesn't contain the player UID ("
                                    + playerUnderTestUid + ") list:" + duckedUids,
                            duckedUids.contains(playerUnderTestUid));
                }
                // check that no focus change was received by the player under test
                assertEquals("Player shouldn't have received a focus change",
                        AudioManager.AUDIOFOCUS_NONE,
                        focusListeners[FocusUnderTest].getFocusChangeAndReset());
            } else {
                // after the GAIN_TRANSIENT_MAY_DUCK request,
                // the media player under test should get LOSS_TRANSIENT_CAN_DUCK
                focusListeners[FocusUnderTest].waitForFocusChange(
                        "testDuckedUidsAfterMediaSpeech",
                        TEST_TIMING_TOLERANCE_MS,
                        /* shouldAcquire= */ true);
                assertEquals("Focus loss from media to assistant not dispatched",
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
                        focusListeners[FocusUnderTest].getFocusChangeAndReset());
                // verify the UID of the player is not ducked
                if (mDeflakeApisAvailable) {
                    duckedUids = mAM.getFocusDuckedUidsForTest();
                    assertFalse("List of ducked UIDs contains the player UID ("
                                    + playerUnderTestUid + ") list:" + duckedUids,
                            duckedUids.contains(playerUnderTestUid));
                }
            }

            // another (fake) media app requests focus with GAIN, the initial focus holder should
            // be notified of the loss now with LOSS
            res = mAM.requestAudioFocusForTest(focusRequests[FocusHelperMedia],
                    mediaFocusClientId, FocusHelperMediaUid /*fakeClientUid*/,
                    Build.VERSION_CODES.S);
            assertEquals("media (test) focus request failed",
                    AudioManager.AUDIOFOCUS_REQUEST_GRANTED, res);
            if (mDeflakeApisAvailable) {
                focusListeners[FocusUnderTest].waitForFocusChange("testDuckedUids",
                        mAM.getFocusFadeOutDurationForTest()
                                + TEST_TIMING_TOLERANCE_MS,
                        /* shouldAcquire= */ true);
                assertEquals("Focus loss from media to assistant not dispatched",
                        AudioManager.AUDIOFOCUS_LOSS,
                        focusListeners[FocusUnderTest].getFocusChangeAndReset());
            }

            // check there is more ducking going on
            if (mDeflakeApisAvailable) {
                SafeWaitObject.checkConditionFor(mAM.getFocusUnmuteDelayAfterFadeOutForTest() * 2,
                        /*period*/50, () -> mAM.getFocusDuckedUidsForTest().size() == 0);
                duckedUids = mAM.getFocusDuckedUidsForTest();
                assertEquals("Expected no ducked UIDs, got " + duckedUids, 0, duckedUids.size());
            }
        } finally {
            if (mDeflakeApisAvailable) {
                mAM.exitAudioFocusFreezeForTest();
            }
            handler.getLooper().quit();
            handlerThread.quitSafely();
            if (mp != null) {
                mp.release();
            }
            mAM.abandonAudioFocusRequest(focusRequests[FocusUnderTest]);
            mAM.abandonAudioFocusForTest(focusRequests[FocusHelperAssist], mediaFocusClientId);
            mAM.abandonAudioFocusForTest(focusRequests[FocusHelperMedia], assistFocusClientId);
        }
    }

    @Test
    @AppModeFull(reason = "Instant apps cannot hold permission.MODIFY_AUDIO_SETTINGS_PRIVILEGED")
    @RequireNotAutomotive(reason = "Auto has its own focus policy")
    public void testDuckedUidsAfterMediaMusic() throws Exception {
        // the media requests are done on USAGE_MEDIA but CONTENT_TYPE_SPEECH so there is ducking
        final AudioAttributes mediaAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();

        runDuckedUidsTest("testDuckedUidsAfterMediaMusic", mediaAttributes,
                /*expectDuck*/ true); // expecting ducking because this is not SPEECH content
    }

    @Test
    @AppModeFull(reason = "Instant apps cannot hold permission.MODIFY_AUDIO_SETTINGS_PRIVILEGED")
    @RequireNotAutomotive(reason = "Auto has its own focus policy")
    public void testDuckedUidsAfterMediaSpeech() throws Exception {
        // the media requests are done on USAGE_MEDIA but CONTENT_TYPE_SPEECH so there is no ducking
        final AudioAttributes mediaAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();

        runDuckedUidsTest("testDuckedUidsAfterMediaSpeech", mediaAttributes,
                /*expectDuck*/ false); // not expecting ducking because this is SPEECH content
    }

    /**
     * Test there is no delayed focus loss when focus loser is playing speech
     * @throws Exception
     */
    @Test
    @AppModeFull(reason = "Instant apps cannot hold permission.QUERY_AUDIO_STATE")
    @RequireNotAutomotive(reason = "Auto has its own focus policy")
    public void testAudioFocusRequestMediaGainLossWithSpeechPlayer() throws Exception {
        doTwoFocusOwnerOnePlayerFocusLoss(
                true /*playSpeech*/,
                false /*speechFocus*/,
                false /*pauseOnDuck*/);
    }

    /**
     * Test there is no delayed focus loss when focus loser had requested focus with
     * AudioAttributes with speech content type
     * @throws Exception
     */
    @Test
    @AppModeFull(reason = "Instant apps cannot hold permission.QUERY_AUDIO_STATE")
    @RequireNotAutomotive(reason = "Auto has its own focus policy")
    public void testAudioFocusRequestMediaGainLossWithSpeechFocusRequest() throws Exception {
        doTwoFocusOwnerOnePlayerFocusLoss(
                false /*playSpeech*/,
                true /*speechFocus*/,
                false /*pauseOnDuck*/);
    }

    /**
     * Test there is no delayed focus loss when focus loser had requested focus specifying
     * it pauses on duck
     * @throws Exception
     */
    @Test
    @AppModeFull(reason = "Instant apps cannot hold permission.QUERY_AUDIO_STATE")
    @RequireNotAutomotive(reason = "Auto has its own focus policy")
    public void testAudioFocusRequestMediaGainLossWithPauseOnDuckFocusRequest() throws Exception {
        doTwoFocusOwnerOnePlayerFocusLoss(
                false /*playSpeech*/,
                false /*speechFocus*/,
                true /*pauseOnDuck*/);
    }

    private void doTwoFocusOwnerOnePlayerFocusLoss(boolean playSpeech, boolean speechFocus,
            boolean pauseOnDuck) throws Exception {

        final int NB_FOCUS_OWNERS = 2;
        final AudioFocusRequest[] focusRequests = new AudioFocusRequest[NB_FOCUS_OWNERS];
        final FocusChangeListener[] focusListeners = new FocusChangeListener[NB_FOCUS_OWNERS];
        // index of focus owner to be tested, has an active player
        final int FOCUS_UNDER_TEST = 0;
        // index of focus requester used to simulate a request coming from another client
        // on a different UID than CTS
        final int FOCUS_SIMULATED = 1;

        final HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        final Handler handler = new Handler(handlerThread.getLooper());

        final AudioAttributes focusAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(playSpeech ? AudioAttributes.CONTENT_TYPE_SPEECH
                        : AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        final AudioAttributes playerAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(speechFocus ? AudioAttributes.CONTENT_TYPE_SPEECH
                        : AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        for (int focusIndex : new int[]{ FOCUS_UNDER_TEST, FOCUS_SIMULATED }) {
            focusListeners[focusIndex] = new FocusChangeListener();
            focusRequests[focusIndex] = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(focusIndex == FOCUS_UNDER_TEST ? playerAttributes
                            : focusAttributes)
                    .setWillPauseWhenDucked(pauseOnDuck)
                    .setOnAudioFocusChangeListener(focusListeners[focusIndex], handler)
                    .build();
        }

        MediaPlayer mp = null;
        final String simFocusClientId = "fakeClientId";
        try {
            // set up the test conditions: a focus owner is playing media on a MediaPlayer
            mp = createPreparedMediaPlayer(R.raw.sine1khzs40dblong, playerAttributes);
            int res = mAM.requestAudioFocus(focusRequests[FOCUS_UNDER_TEST]);
            assertEquals("real focus request failed",
                    AudioManager.AUDIOFOCUS_REQUEST_GRANTED, res);
            mp.start();
            Thread.sleep(TEST_TIMING_TOLERANCE_MS);

            res = mAM.requestAudioFocusForTest(focusRequests[FOCUS_SIMULATED],
                    simFocusClientId, Integer.MAX_VALUE /*fakeClientUid*/, Build.VERSION_CODES.S);
            assertEquals("test focus request failed",
                    AudioManager.AUDIOFOCUS_REQUEST_GRANTED, res);

            focusListeners[FOCUS_UNDER_TEST].waitForFocusChange("doTwoFocusOwnerOnePlayerFocusLoss",
                    TEST_TIMING_TOLERANCE_MS, /* shouldAcquire= */ true);
            assertEquals("Focus loss not dispatched", AudioManager.AUDIOFOCUS_LOSS,
                    focusListeners[FOCUS_UNDER_TEST].getFocusChangeAndReset());

        }
        finally {
            handler.getLooper().quit();
            handlerThread.quitSafely();
            if (mp != null) {
                mp.release();
            }
            mAM.abandonAudioFocusForTest(focusRequests[FOCUS_SIMULATED], simFocusClientId);
            mAM.abandonAudioFocusRequest(focusRequests[FOCUS_UNDER_TEST]);
        }
    }
    //-----------------------------------
    // Test utilities

    /**
     * Test focus request and abandon between two focus owners
     * @param gainType focus gain of the focus owner on top (== 2nd focus requester)
     */
    private void doTestTwoPlayersGainLoss(int gainType, AudioAttributes[] attributes,
            boolean useHandlerInListener) throws Exception {
        doTestTwoPlayersGainLoss(AudioManager.AUDIOFOCUS_GAIN, gainType, attributes,
                useHandlerInListener, false /*forceDucking*/);
    }

    /**
     * Same as {@link #doTestTwoPlayersGainLoss(int, AudioAttributes[], boolean)} with forceDucking
     *   set to false.
     * @param gainTypeForFirstPlayer focus gain of the focus owner on bottom (== 1st focus request)
     * @param gainTypeForSecondPlayer focus gain of the focus owner on top (== 2nd focus request)
     * @param attributes Audio attributes for first and second player, in order.
     * @param useHandlerInListener
     * @param forceDucking value used for setForceDucking in request for focus requester at top of
     *   stack (second requester in test).
     * @throws Exception
     */
    private void doTestTwoPlayersGainLoss(int gainTypeForFirstPlayer, int gainTypeForSecondPlayer,
            AudioAttributes[] attributes, boolean useHandlerInListener,
            boolean forceDucking) throws Exception {
        final int NB_FOCUS_OWNERS = 2;
        if (NB_FOCUS_OWNERS != attributes.length) {
            throw new IllegalArgumentException("Invalid test: invalid number of attributes");
        }
        final AudioFocusRequest[] focusRequests = new AudioFocusRequest[NB_FOCUS_OWNERS];
        final FocusChangeListener[] focusListeners = new FocusChangeListener[NB_FOCUS_OWNERS];
        final int[] focusGains = { gainTypeForFirstPlayer, gainTypeForSecondPlayer };
        int expectedLoss = 0;
        switch (gainTypeForSecondPlayer) {
            case AudioManager.AUDIOFOCUS_GAIN:
                expectedLoss = AudioManager.AUDIOFOCUS_LOSS;
                break;
            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:
            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE:
                expectedLoss = AudioManager.AUDIOFOCUS_LOSS_TRANSIENT;
                break;
            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:
                expectedLoss = AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK;
                break;
            default:
                fail("invalid focus gain used in test");
        }

        final Handler h;
        if (useHandlerInListener) {
            HandlerThread handlerThread = new HandlerThread(TAG);
            handlerThread.start();
            h = new Handler(handlerThread.getLooper());
        } else {
            h = null;
        }

        try {
            for (int i = 0 ; i < NB_FOCUS_OWNERS ; i++) {
                focusListeners[i] = new FocusChangeListener();
                final boolean forceDuck = i == NB_FOCUS_OWNERS - 1 ? forceDucking : false;
                if (h != null) {
                    focusRequests[i] = new AudioFocusRequest.Builder(focusGains[i])
                            .setAudioAttributes(attributes[i])
                            .setOnAudioFocusChangeListener(focusListeners[i], h /*handler*/)
                            .setForceDucking(forceDuck)
                            .build();
                } else {
                    focusRequests[i] = new AudioFocusRequest.Builder(focusGains[i])
                            .setAudioAttributes(attributes[i])
                            .setOnAudioFocusChangeListener(focusListeners[i])
                            .setForceDucking(forceDuck)
                            .build();
                }
            }

            // focus owner 0 requests focus with GAIN,
            // then focus owner 1 requests focus with gainType
            // then 1 abandons focus, then 0 abandons focus
            int res = mAM.requestAudioFocus(focusRequests[0]);
            assertEquals("1st focus request failed",
                    AudioManager.AUDIOFOCUS_REQUEST_GRANTED, res);
            res = mAM.requestAudioFocus(focusRequests[1]);
            assertEquals("2nd focus request failed", AudioManager.AUDIOFOCUS_REQUEST_GRANTED, res);
            focusListeners[0].waitForFocusChange("doTestTwoPlayersGainLoss",
                    TEST_TIMING_TOLERANCE_MS, /* shouldAcquire= */ true);
            assertEquals("Focus loss not dispatched", expectedLoss,
                    focusListeners[0].getFocusChangeAndReset());
            res = mAM.abandonAudioFocusRequest(focusRequests[1]);
            assertEquals("1st abandon failed", AudioManager.AUDIOFOCUS_REQUEST_GRANTED, res);
            focusRequests[1] = null;
            focusListeners[0].waitForFocusChange("doTestTwoPlayersGainLoss",
                    TEST_TIMING_TOLERANCE_MS,
                    gainTypeForSecondPlayer != AudioManager.AUDIOFOCUS_GAIN);
            // when focus was lost because it was requested with GAIN, focus is not given back
            if (gainTypeForSecondPlayer != AudioManager.AUDIOFOCUS_GAIN) {
                assertEquals("Focus gain not dispatched", AudioManager.AUDIOFOCUS_GAIN,
                        focusListeners[0].getFocusChangeAndReset());
            } else {
                // verify there was no focus change because focus user 0 was kicked out of stack
                assertEquals("Focus change was dispatched", AudioManager.AUDIOFOCUS_NONE,
                        focusListeners[0].getFocusChangeAndReset());
            }
            res = mAM.abandonAudioFocusRequest(focusRequests[0]);
            assertEquals("2nd abandon failed", AudioManager.AUDIOFOCUS_REQUEST_GRANTED, res);
            focusRequests[0] = null;
        }
        finally {
            for (int i = 0 ; i < NB_FOCUS_OWNERS ; i++) {
                if (focusRequests[i] != null) {
                    mAM.abandonAudioFocusRequest(focusRequests[i]);
                }
            }
            if (h != null) {
                h.getLooper().quit();
            }
        }
    }

    private @Nullable MediaPlayer createPreparedMediaPlayer(
            @RawRes int resID, AudioAttributes aa) throws Exception {
        final TestUtils.Monitor onPreparedCalled = new TestUtils.Monitor();

        MediaPlayer mp = new MediaPlayer();
        mp.setAudioAttributes(aa);
        AssetFileDescriptor afd = mContext.getResources().openRawResourceFd(resID);
        try {
            mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
        } finally {
            afd.close();
        }
        mp.setOnPreparedListener(mp1 -> onPreparedCalled.signal());
        mp.prepare();
        onPreparedCalled.waitForSignal(MEDIAPLAYER_PREPARE_TIMEOUT_MS);
        assertTrue(
                "MediaPlayer wasn't prepared in under " + MEDIAPLAYER_PREPARE_TIMEOUT_MS + " ms",
                onPreparedCalled.isSignalled());
        return mp;
    }

    private static class FocusChangeListener implements OnAudioFocusChangeListener {
        private final Object mLock = new Object();
        private final Semaphore mChangeEventSignal = new Semaphore(0);
        private int mFocusChange = AudioManager.AUDIOFOCUS_NONE;

        int getFocusChangeAndReset() {
            final int change;
            synchronized (mLock) {
                change = mFocusChange;
                mFocusChange = AudioManager.AUDIOFOCUS_NONE;
            }
            mChangeEventSignal.drainPermits();
            return change;
        }

        void waitForFocusChange(String caller, long timeoutMs, boolean shouldAcquire)
                throws Exception {
            boolean acquired = mChangeEventSignal.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS);
            assertWithMessage(caller + " wait acquired").that(acquired).isEqualTo(shouldAcquire);
        }

        @Override
        public void onAudioFocusChange(int focusChange) {
            Log.i(TAG, "onAudioFocusChange:" + focusChange + " listener:" + this);
            synchronized (mLock) {
                mFocusChange = focusChange;
            }
            mChangeEventSignal.release();
        }
    }

    private static class MediaPlayerRoutingListener
            implements AudioRouting.OnRoutingChangedListener {
        private final Object mLock = new Object();
        private final Semaphore mChangeEventSignal = new Semaphore(0);
        private AudioDeviceInfo mRoutedToDevice = null;

        MediaPlayerRoutingListener(MediaPlayer mp) {
            mp.addOnRoutingChangedListener(this, new Handler(Looper.getMainLooper()));
        }

        AudioDeviceInfo getRoutedToDeviceAndReset() {
            final AudioDeviceInfo route;
            synchronized (mLock) {
                route = mRoutedToDevice;
                mRoutedToDevice = null;
            }
            mChangeEventSignal.drainPermits();
            return route;
        }

        void waitForRoutingChange(long timeoutMs)
                throws Exception {
            synchronized (mLock) {
                if (mRoutedToDevice != null) {
                    return;
                }
            }
            boolean acquired = mChangeEventSignal.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS);
            assertWithMessage("MediaPlayerRoutingListener wait acquired").that(acquired).isTrue();
        }
        @Override
        public void onRoutingChanged(AudioRouting router) {
            String type = (router.getRoutedDevice() != null)
                    ? "type:" + router.getRoutedDevice().getType()
                    : "none";
            Log.i(TAG, "onRoutingChanged: " + type + " listener:" + this);
            synchronized (mLock) {
                mRoutedToDevice = router.getRoutedDevice();
            }
            mChangeEventSignal.release();
        }
    }
}
