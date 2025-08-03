/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.soundtrigger.cts;

import static android.Manifest.permission.CAPTURE_AUDIO_HOTWORD;
import static android.Manifest.permission.MANAGE_SOUND_TRIGGER;
import static android.Manifest.permission.RECORD_AUDIO;
import static android.content.pm.PackageManager.FEATURE_MICROPHONE;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import android.app.AppOpsManager;
import android.content.Context;
import android.media.soundtrigger.SoundTriggerDetector;
import android.media.soundtrigger.SoundTriggerDetector.Callback;
import android.media.soundtrigger.SoundTriggerDetector.EventPayload;
import android.media.soundtrigger.SoundTriggerInstrumentation;
import android.media.soundtrigger.SoundTriggerInstrumentation.RecognitionSession;
import android.media.soundtrigger.SoundTriggerManager;
import android.media.soundtrigger.SoundTriggerManager.Model;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.soundtrigger.cts.instrumentation.SoundTriggerInstrumentationObserver;
import android.soundtrigger.cts.instrumentation.SoundTriggerInstrumentationObserver.ModelSessionObserver;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.FlakyTest;

import com.android.compatibility.common.util.RequiredFeatureRule;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


@RunWith(AndroidJUnit4.class)
public class SoundTriggerManagerTest {
    private static final String TAG = "SoundTriggerManagerTest";
    private static final Context sContext = getInstrumentation().getTargetContext();
    private static final int TIMEOUT = 5000;
    private static final UUID MODEL_UUID = new UUID(5, 7);
    private static final Model sModel = Model.create(MODEL_UUID, new UUID(7, 5), new byte[0], 1);

    private SoundTriggerManager mRealManager = null;
    private SoundTriggerManager mManager = null;
    private SoundTriggerInstrumentation mInstrumentation = null;
    private SoundTriggerDetector mDetector = null;

    private final SoundTriggerInstrumentationObserver mInstrumentationObserver =
            new SoundTriggerInstrumentationObserver();

    private final Object mDetectedLock = new Object();
    private SettableFuture<Void> mDetectedFuture;
    private boolean mDroppedCallback = false;

    private Handler mHandler = null;
    private boolean mIsModelLoaded = false;

    @Rule
    public RequiredFeatureRule REQUIRES_MIC_RULE = new RequiredFeatureRule(FEATURE_MICROPHONE);

    @Before
    public void setup() {
        getInstrumentation().getUiAutomation().adoptShellPermissionIdentity();
        mRealManager = sContext.getSystemService(SoundTriggerManager.class);
        mInstrumentationObserver.attachInstrumentation();
        mManager = mRealManager.createManagerForTestModule();
        getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
    }

    @After
    public void tearDown() {
        getInstrumentation().getUiAutomation().adoptShellPermissionIdentity();
        try {
            if (mIsModelLoaded) {
                mManager.deleteModel(MODEL_UUID);
            }
        } catch (Exception e) {
        }
        if (mHandler != null) {
            mHandler.getLooper().quit();
        }

        // Clean up any unexpected HAL state
        // Wait for stray callbacks and to disambiguate the logs
        SystemClock.sleep(50);
        try {
            mInstrumentationObserver.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        // Wait for the mock HAL to reboot
        SystemClock.sleep(100);
        getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
    }

    public static <V> V waitForFutureDoneAndAssertSuccessful(Future<V> future) {
        try {
            return future.get(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new AssertionError("future failed to complete", e);
        }
    }

    private void loadModelForRecognition() {
        mManager.updateModel(sModel);
        assertThat(mManager.loadSoundModel(sModel.getSoundModel())).isEqualTo(0);
        mIsModelLoaded = true;
    }

    private ListenableFuture<Void> listenForDetection() {
        synchronized (mDetectedLock) {
            if (mDetectedFuture != null) {
                assertThat(mDetectedFuture.isDone()).isTrue();
                assertThat(mDroppedCallback).isFalse();
            }
            mDetectedFuture = SettableFuture.create();
            Log.d(TAG, "Begin listen for detection" + mDetectedFuture);
            return mDetectedFuture;
        }
    }

    private void setUpDetector() {
        var thread = new HandlerThread("SoundTriggerDetectorHandler");
        thread.start();
        mHandler = new Handler(thread.getLooper());
        mDetector =
                mManager.createSoundTriggerDetector(
                        MODEL_UUID,
                        new Callback() {
                            @Override
                            public void onAvailabilityChanged(int status) {}

                            @Override
                            public void onDetected(EventPayload eventPayload) {
                                synchronized (mDetectedLock) {
                                    mDroppedCallback = !mDetectedFuture.set(null);
                                    if (mDroppedCallback) {
                                        Log.e(TAG, "Dropped detection" + mDetectedFuture);
                                    } else {
                                        Log.d(TAG, "Detection" + mDetectedFuture);
                                    }
                                }
                            }

                            @Override
                            public void onError() {}

                            @Override
                            public void onRecognitionPaused() {}

                            @Override
                            public void onRecognitionResumed() {}
                        },
                        mHandler);
    }

    private void getSoundTriggerPermissions() {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        RECORD_AUDIO, CAPTURE_AUDIO_HOTWORD, MANAGE_SOUND_TRIGGER);
    }

    @Test
    public void testStartRecognitionFails_whenMissingRecordPermission() {
        getSoundTriggerPermissions();
        loadModelForRecognition();
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(CAPTURE_AUDIO_HOTWORD, MANAGE_SOUND_TRIGGER);
        setUpDetector();
        assertThat(mDetector.startRecognition(0)).isFalse();
    }

    @Test
    public void testStartRecognitionFails_whenMissingHotwordPermission() {
        getSoundTriggerPermissions();
        loadModelForRecognition();
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(RECORD_AUDIO, MANAGE_SOUND_TRIGGER);
        setUpDetector();
        assertThat(mDetector.startRecognition(0)).isFalse();
    }

    @Test
    public void testStartRecognitionSucceeds_whenHoldingPermissions() throws Exception {
        getSoundTriggerPermissions();
        var detectedFuture = listenForDetection();
        loadModelForRecognition();
        setUpDetector();
        assertThat(mDetector.startRecognition(0)).isTrue();

        RecognitionSession recognitionSession = waitForFutureDoneAndAssertSuccessful(
                mInstrumentationObserver.getOnRecognitionStartedFuture());
        assertThat(recognitionSession).isNotNull();

        recognitionSession.triggerRecognitionEvent(new byte[] {0x11}, null);
        waitForFutureDoneAndAssertSuccessful(detectedFuture);
    }

    @Test
    public void testRecognitionEvent_notesAppOps() throws Exception {
        getSoundTriggerPermissions();

        loadModelForRecognition();
        setUpDetector();
        assertThat(mDetector.startRecognition(0)).isTrue();
        RecognitionSession recognitionSession = waitForFutureDoneAndAssertSuccessful(
                mInstrumentationObserver.getOnRecognitionStartedFuture());

        assertThat(recognitionSession).isNotNull();

        final SettableFuture<Void> ambientOpFuture = SettableFuture.create();
        var detectedFuture = listenForDetection();

        AppOpsManager appOpsManager =
                sContext.getSystemService(AppOpsManager.class);
        final String[] OPS_TO_WATCH =
                new String[] {
                    AppOpsManager.OPSTR_RECEIVE_AMBIENT_TRIGGER_AUDIO
                };

        runWithShellPermissionIdentity(() ->
                appOpsManager.startWatchingNoted(
                        OPS_TO_WATCH,
                        (op, uid, pkgName, attributionTag, flags, result) -> {
                            if (Objects.equals(
                                    op, AppOpsManager.OPSTR_RECEIVE_AMBIENT_TRIGGER_AUDIO)) {
                                ambientOpFuture.set(null);
                            }
                        }));

        // Grab permissions again since we transitioned out of shell identity
        getSoundTriggerPermissions();

        recognitionSession.triggerRecognitionEvent(new byte[] {0x11}, null);
        waitForFutureDoneAndAssertSuccessful(ambientOpFuture);
        waitForFutureDoneAndAssertSuccessful(detectedFuture);
    }

    @Test
    public void testAttachInvalidSession_whenNoDspAvailable() {
        getSoundTriggerPermissions();
        if (mManager.listModuleProperties().size() == 1) {
            assertThrows(IllegalStateException.class,
                    () -> mRealManager.loadSoundModel(sModel.getSoundModel()));
        }
    }

    @Test
    public void testNullModuleProperties_whenNoDspAvailable() {
        getSoundTriggerPermissions();
        if (mManager.listModuleProperties().size() == 1) {
            assertThat(mRealManager.getModuleProperties()).isNull();
        }
    }

    @Test
    public void testAttachThrows_whenMissingRecordPermission() {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(CAPTURE_AUDIO_HOTWORD, MANAGE_SOUND_TRIGGER);
        assertThrows(
                SecurityException.class,
                () -> mRealManager.createManagerForTestModule());
    }

    @Test
    public void testAttachThrows_whenMissingCaptureHotwordPermission() {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(RECORD_AUDIO, MANAGE_SOUND_TRIGGER);
        assertThrows(
                SecurityException.class,
                () -> mRealManager.createManagerForTestModule());
    }

    // This test is inherently flaky, since the raciness it tests isn't totally solved.
    @FlakyTest
    @Test
    public void testStartTriggerStopRecognitionRace_doesNotFail() throws Exception {
        // Disable this test for now since it is flaky
        assumeTrue(false);
        final int ITERATIONS = 20;
        getSoundTriggerPermissions();
        final ListenableFuture<ModelSessionObserver> modelSessionFuture =
                mInstrumentationObserver.getGlobalCallbackObserver().getOnModelLoadedFuture();

        loadModelForRecognition();
        setUpDetector();
        assertThat(mDetector.startRecognition(0)).isTrue();
        final ModelSessionObserver modelSessionObserver = waitForFutureDoneAndAssertSuccessful(
                modelSessionFuture);

        for (int i = 0; i < ITERATIONS; i++) {
            var detectedFuture = listenForDetection();
            RecognitionSession recognitionSession = waitForFutureDoneAndAssertSuccessful(
                    modelSessionObserver.getOnRecognitionStartedFuture());
            assertThat(recognitionSession).isNotNull();
            modelSessionObserver.resetOnRecognitionStartedFuture();
            // Attempt to interleave a stopRecognition + startRecognition and an upward
            // recognition event
            recognitionSession.triggerRecognitionEvent(new byte[] {0x11}, null);
            mDetector.stopRecognition();
            // Due to limitations in the STHAL API, we are still vulnerable to raciness, and
            // we could receive the recognition event for this startRecognition
            assertThat(mDetector.startRecognition(0)).isTrue();
            // Get the new recognition session
            recognitionSession = waitForFutureDoneAndAssertSuccessful(
                    modelSessionObserver.getOnRecognitionStartedFuture());
            assertThat(recognitionSession).isNotNull();
            // Wait a bit to receive a recognition event which we *may* have gotten
            SystemClock.sleep(50);
            if (detectedFuture.isDone()) {
                detectedFuture = listenForDetection();
            }
            // Check that the validation layer doesn't think that the new recognition session is
            // stopped
            recognitionSession.triggerRecognitionEvent(new byte[] {0x11}, null);
            waitForFutureDoneAndAssertSuccessful(detectedFuture);
            modelSessionObserver.resetOnRecognitionStartedFuture();
            assertThat(mDetector.startRecognition(0)).isTrue();
        }
    }
}
