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

package android.voiceinteraction.cts;

import static android.Manifest.permission.CAPTURE_AUDIO_HOTWORD;
import static android.Manifest.permission.MANAGE_HOTWORD_DETECTION;
import static android.Manifest.permission.RECORD_AUDIO;
import static android.os.UserManager.DISALLOW_ASSIST_CONTENT;
import static android.service.voice.VoiceInteractionSession.KEY_FOREGROUND_ACTIVITIES;
import static android.service.voice.VoiceInteractionSession.KEY_SHOW_SESSION_ID;
import static android.voiceinteraction.cts.testcore.Helper.CTS_SERVICE_PACKAGE;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.bedstead.harrier.UserType.WORK_PROFILE;
import static com.android.compatibility.common.util.ActivitiesWatcher.ActivityLifecycle.RESUMED;
import static com.android.queryable.queries.ActivityQuery.activity;

import static com.google.common.truth.Truth.assertThat;

import android.app.Application;
import android.app.UiAutomation;
import android.content.Context;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.os.RemoteCallback;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.service.voice.AlwaysOnHotwordDetector;
import android.service.voice.HotwordDetectionService;
import android.service.voice.HotwordDetector;
import android.service.voice.HotwordRejectedResult;
import android.service.voice.VoiceInteractionService;
import android.service.voice.VoiceInteractionSession;
import android.util.Log;
import android.voiceinteraction.common.Utils;
import android.voiceinteraction.cts.activities.EmptyActivity;
import android.voiceinteraction.cts.services.BaseVoiceInteractionService;
import android.voiceinteraction.cts.services.CtsBasicVoiceInteractionService;
import android.voiceinteraction.cts.testcore.Helper;
import android.voiceinteraction.cts.testcore.VoiceInteractionServiceConnectedRule;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureHasPrivateProfile;
import com.android.bedstead.harrier.annotations.EnsureHasUserRestriction;
import com.android.bedstead.harrier.annotations.EnsureHasWorkProfile;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppActivityReference;
import com.android.bedstead.testapp.TestAppInstance;
import com.android.compatibility.common.util.ActivitiesWatcher;
import com.android.compatibility.common.util.ActivitiesWatcher.ActivityWatcher;
import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.SettingsStateKeeperRule;
import com.android.compatibility.common.util.SettingsStateManager;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Tests for {@link VoiceInteractionService} APIs.
 */
@RunWith(BedsteadJUnit4.class)
@AppModeFull(reason = "No real use case for instant mode")
public class VoiceInteractionServiceTest extends AbstractHdsTestCase {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @Rule
    public final SettingsStateKeeperRule mPublicServiceSettingsKeeper =
            new SettingsStateKeeperRule(getInstrumentation().getTargetContext(),
                    "assist_screenshot_enabled");

    private static final SettingsStateManager sScreenshotEnabledManager = new SettingsStateManager(
            getInstrumentation().getTargetContext(), "assist_screenshot_enabled");
    private static final TestApp sTestApp = sDeviceState.testApps().query()
            .whereActivities()
            .contains(activity().where().exported().isTrue())
            .get();

    private static final String TAG = "VoiceInteractionServiceTest";
    private static final String KEY_SHOW_SESSION_TEST = "showSessionTest";

    // The VoiceInteractionService used by this test
    private static final String SERVICE_COMPONENT =
            "android.voiceinteraction.cts.services.CtsBasicVoiceInteractionService";
    protected final Context mContext = getInstrumentation().getTargetContext();

    @Rule
    public VoiceInteractionServiceConnectedRule mConnectedRule =
            new VoiceInteractionServiceConnectedRule(mContext, getTestVoiceInteractionService());

    private CtsBasicVoiceInteractionService mService;

    public String getTestVoiceInteractionService() {
        Log.d(TAG, "getTestVoiceInteractionService()");
        return CTS_SERVICE_PACKAGE + "/" + SERVICE_COMPONENT;
    }

    @Before
    public void setup() {
        // VoiceInteractionServiceConnectedRule handles the service connected, we should be
        // able to get service
        mService = (CtsBasicVoiceInteractionService) BaseVoiceInteractionService.getService();

        // Check we can get the service, we need service object to call the service provided method
        Objects.requireNonNull(mService);

        // Set whether voice activation permission enabled.
        mService.setVoiceActivationPermissionEnabled(mVoiceActivationPermissionEnabled);

        VoiceInteractionTestReceiver.reset();
    }

    @After
    public void tearDown() {
        mService = null;
    }

    @ApiTest(apis = {
            "android.service.voice.VoiceInteractionSession#onHandleScreenshot",
            "android.service.voice.VoiceInteractionSession#onHandleAssist",
            "android.service.voice.VoiceInteractionSession#onShow"
    })
    @Test
    public void onHandleScreenShotAndAssist_initialUser_success() throws Exception {
        try (TestAppInstance unused = startActivityAndShowSession(
                sDeviceState.initialUser())) {
            assertHasAssistDataAndScreenshot();
        }
    }

    @ApiTest(apis = {
            "android.service.voice.VoiceInteractionSession#onHandleScreenshot",
            "android.service.voice.VoiceInteractionSession#onHandleAssist",
            "android.service.voice.VoiceInteractionSession#onShow"
    })
    @EnsureHasPrivateProfile
    @Test
    public void onHandleScreenShotAndAssist_privateProfile_failed() throws Exception {
        try (TestAppInstance unused = startActivityAndShowSession(
                sDeviceState.privateProfile())) {
            assertHasNoAssistDataAndScreenshot();
        }
    }

    @ApiTest(apis = {
            "android.service.voice.VoiceInteractionSession#onHandleScreenshot",
            "android.service.voice.VoiceInteractionSession#onHandleAssist",
            "android.service.voice.VoiceInteractionSession#onShow"
    })
    @EnsureHasWorkProfile
    @Test
    public void onHandleScreenShotAndAssist_workProfileWithoutDisallowPolicy_success()
            throws Exception {
        try (TestAppInstance unused = startActivityAndShowSession(
                sDeviceState.workProfile())) {
            assertHasAssistDataAndScreenshot();
        }
    }

    @ApiTest(apis = {
            "android.service.voice.VoiceInteractionSession#onHandleScreenshot",
            "android.service.voice.VoiceInteractionSession#onHandleAssist",
            "android.service.voice.VoiceInteractionSession#onShow"
    })
    @EnsureHasWorkProfile
    @EnsureHasUserRestriction(value = DISALLOW_ASSIST_CONTENT, onUser = WORK_PROFILE)
    @Test
    public void onHandleScreenShotAndAssist_workProfileWithDisallowPolicy_failed()
            throws Exception {
        try (TestAppInstance unused = startActivityAndShowSession(
                sDeviceState.workProfile())) {
            assertHasNoAssistDataAndScreenshot();
        }
    }

    @ApiTest(apis = {
            "android.service.voice.VoiceInteractionSession#onHandleScreenshot",
            "android.service.voice.VoiceInteractionSession#onHandleAssist",
            "android.service.voice.VoiceInteractionSession#onShow"
    })
    @EnsureHasWorkProfile
    @EnsureHasUserRestriction(value = DISALLOW_ASSIST_CONTENT, onUser = WORK_PROFILE)
    @Test
    public void onHandleScreenShotAndAssist_workProfileWithDisallowPolicy_successInInitialUser()
            throws Exception {
        try (TestAppInstance unused = startActivityAndShowSession(
                sDeviceState.initialUser())) {
            assertHasAssistDataAndScreenshot();
        }
    }

    private void assertHasNoAssistDataAndScreenshot() throws InterruptedException {
        assertAssistDataAvailability(false);
    }

    private void assertHasAssistDataAndScreenshot() throws InterruptedException {
        assertAssistDataAvailability(true);
    }

    private void assertAssistDataAvailability(boolean isAvailable)
            throws InterruptedException {
        boolean obtainedScreenshot = VoiceInteractionTestReceiver.waitScreenshotReceived(5,
                TimeUnit.SECONDS);
        boolean obtainedAssistData = VoiceInteractionTestReceiver.waitAssistDataReceived(5,
                TimeUnit.SECONDS);
        Bundle onShowArgs = VoiceInteractionTestReceiver.waitOnShowReceived(5,
                TimeUnit.SECONDS);
        assertThat(obtainedScreenshot).isEqualTo(isAvailable);
        assertThat(obtainedAssistData).isEqualTo(isAvailable);
        assertThat(onShowArgs).isNotNull();
        assertThat(onShowArgs.containsKey(KEY_FOREGROUND_ACTIVITIES)).isEqualTo(isAvailable);
    }

    private TestAppInstance startActivityAndShowSession(UserReference userToStartActivity) {
        sScreenshotEnabledManager.set("1");
        TestAppInstance instance = sTestApp.install(userToStartActivity);
        TestAppActivityReference activityReference =
                instance.activities().query().whereActivity().exported().isTrue().get();
        activityReference.start();
        mService.showSession(new Bundle(), VoiceInteractionSession.SHOW_WITH_SCREENSHOT
                | VoiceInteractionSession.SHOW_WITH_ASSIST);
        return instance;
    }

    @Test
    public void testVoiceInteractionSession_startAssistantActivityWithActivityOptions()
            throws Exception {
        startSessionForStartAssistantActivity(/* useActivityOptions= */ true);
    }

    @Test
    public void testVoiceInteractionSession_startAssistantActivityWithoutActivityOptions()
            throws Exception {
        startSessionForStartAssistantActivity(/* useActivityOptions= */ false);
    }

    private void startSessionForStartAssistantActivity(boolean useActivityOptions)
            throws Exception {
        // Start an ActivityWatcher to wait target test Activity
        ActivitiesWatcher activitiesWatcher = new ActivitiesWatcher(5_000);
        final Application app = ApplicationProvider.getApplicationContext();
        app.registerActivityLifecycleCallbacks(activitiesWatcher);
        ActivityWatcher watcher = activitiesWatcher.watch(EmptyActivity.class);

        try {
            // Request to show session
            final Bundle args = new Bundle();
            args.putInt(KEY_SHOW_SESSION_TEST, 100);
            final int flags = VoiceInteractionSession.SHOW_WITH_ASSIST;
            final CountDownLatch latch = new CountDownLatch(1);
            final RemoteCallback onNewSessionCalled = new RemoteCallback((b) -> latch.countDown());
            args.putParcelable(Utils.VOICE_INTERACTION_KEY_REMOTE_CALLBACK_FOR_NEW_SESSION,
                    onNewSessionCalled);
            args.putString(Utils.VOICE_INTERACTION_KEY_COMMAND, "startAssistantActivity");
            args.putBoolean(Utils.VOICE_INTERACTION_KEY_USE_ACTIVITY_OPTIONS, useActivityOptions);
            mService.showSession(args, flags);

            // Wait the VoiceInteractionSessionService onNewSession called
            final long timeoutMs = Utils.getAdjustedOperationTimeoutMs();
            if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                throw new TimeoutException(
                        "result not received in " + timeoutMs + "ms");
            }

            // Wait target Activity can be started by startAssistantActivity
            watcher.waitFor(RESUMED);
        } finally {
            app.unregisterActivityLifecycleCallbacks(activitiesWatcher);
        }
    }

    @Test
    public void testShowSession_onPrepareToShowSessionCalled() throws Exception {
        final Bundle args = new Bundle();
        final int value = 100;
        args.putInt(KEY_SHOW_SESSION_TEST, value);
        final int flags = VoiceInteractionSession.SHOW_WITH_ASSIST;

        BaseVoiceInteractionService.initShowSessionLatch();
        mService.showSession(args, flags);
        BaseVoiceInteractionService.waitOnPrepareToShowSession();

        final Bundle resultArgs = mService.getPrepareToShowSessionArgs();
        assertThat(resultArgs.getInt(KEY_SHOW_SESSION_TEST, /* defaultValue= */ -1))
                .isEqualTo(value);
        assertThat(resultArgs.containsKey(KEY_SHOW_SESSION_ID)).isTrue();
        assertThat(mService.getPrepareToShowSessionFlags()).isEqualTo(flags);
    }

    @Test
    public void testShowSessionWithNullArgs_onPrepareToShowSessionCalledHasId() throws Exception {
        final int flags = VoiceInteractionSession.SHOW_WITH_ASSIST;

        BaseVoiceInteractionService.initShowSessionLatch();
        mService.showSession(/* args= */ null, flags);
        BaseVoiceInteractionService.waitOnPrepareToShowSession();

        final Bundle resultArgs = mService.getPrepareToShowSessionArgs();
        assertThat(resultArgs).isNotNull();
        assertThat(resultArgs.containsKey(KEY_SHOW_SESSION_ID)).isTrue();
        assertThat(mService.getPrepareToShowSessionFlags()).isEqualTo(flags);
    }

    @Test
    public void testShowSession_onPrepareToShowSessionCalledTwiceIdIsDifferent() throws Exception {
        final Bundle args = new Bundle();
        final int flags = 0;

        // trigger showSession first time
        BaseVoiceInteractionService.initShowSessionLatch();
        mService.showSession(args, flags);
        BaseVoiceInteractionService.waitOnPrepareToShowSession();

        // get the first showSession id
        Bundle resultArgs = mService.getPrepareToShowSessionArgs();
        assertThat(resultArgs.containsKey(KEY_SHOW_SESSION_ID)).isTrue();
        final int firstId = resultArgs.getInt(KEY_SHOW_SESSION_ID);

        // trigger showSession second time
        BaseVoiceInteractionService.initShowSessionLatch();
        mService.showSession(args, flags);
        BaseVoiceInteractionService.waitOnPrepareToShowSession();

        // get the second showSession id
        resultArgs = mService.getPrepareToShowSessionArgs();
        assertThat(resultArgs.containsKey(KEY_SHOW_SESSION_ID)).isTrue();
        final int secondId = resultArgs.getInt(KEY_SHOW_SESSION_ID);

        assertThat(secondId).isGreaterThan(firstId);
    }

    @ApiTest(apis = {
            "android.service.voice.VoiceInteractionService#createAlwaysOnHotwordDetector"})
    @Test
    public void testCreateDspHotwordDetectorNoHDSNoExecutorMainThread_callbackRunsOnMainThread()
            throws Exception {
        testCreateAlwaysOnHotwordDetectorNoHotwordDetectionService(/* useExecutor= */
                false, /* runOnMainThread= */ true, /* callbackShouldRunOnMainThread= */ true);
    }

    @ApiTest(apis = {
            "android.service.voice.VoiceInteractionService#createAlwaysOnHotwordDetector"})
    @Test
    public void testCreateDspHotwordDetectorNoHDSNoExecutorOtherThread_callbackRunsOnNonMainThread()
            throws Exception {
        testCreateAlwaysOnHotwordDetectorNoHotwordDetectionService(/* useExecutor= */
                false, /* runOnMainThread= */ false, /* callbackShouldRunOnMainThread= */ false);
    }

    @ApiTest(apis = {
            "android.service.voice.VoiceInteractionService#createAlwaysOnHotwordDetector"})
    @Test
    public void testCreateDspHotwordDetectorNoHDSWithExecutor_callbackRunsOnNonMainThread()
            throws Exception {
        testCreateAlwaysOnHotwordDetectorNoHotwordDetectionService(/* useExecutor= */
                true, /* runOnMainThread= */ true, /* callbackShouldRunOnMainThread= */ false);
    }

    private void testCreateAlwaysOnHotwordDetectorNoHotwordDetectionService(boolean useExecutor,
            boolean runOnMainThread, boolean callbackShouldRunOnMainThread) throws Exception {
        // reset the value to non-expected value and initAvailabilityChangeLatch
        mService.setIsDetectorCallbackRunningOnMainThread(!callbackShouldRunOnMainThread);
        mService.initAvailabilityChangeLatch();

        // Create alwaysOnHotwordDetector and wait result
        mService.createAlwaysOnHotwordDetectorNoHotwordDetectionService(useExecutor,
                runOnMainThread);
        mService.waitCreateAlwaysOnHotwordDetectorNoHotwordDetectionServiceReady();

        // The AlwaysOnHotwordDetector should be created correctly
        AlwaysOnHotwordDetector alwaysOnHotwordDetector = mService.getAlwaysOnHotwordDetector();
        Objects.requireNonNull(alwaysOnHotwordDetector);

        mService.waitAvailabilityChangedCalled();

        assertThat(mService.isDetectorCallbackRunningOnMainThread()).isEqualTo(
                callbackShouldRunOnMainThread);
    }

    @ApiTest(apis = {
            "android.service.voice.VoiceInteractionService#createAlwaysOnHotwordDetector"})
    @Test
    public void testCreateAlwaysOnHotwordDetectorNoExecutorMainThread_callbackRunsOnMainThread()
            throws Exception {
        testCreateAlwaysOnHotwordDetector(/* useExecutor= */ false, /* runOnMainThread= */ true,
                /* callbackShouldRunOnMainThread= */ true);
    }

    @ApiTest(apis = {
            "android.service.voice.VoiceInteractionService#createAlwaysOnHotwordDetector"})
    @Test
    public void testCreateAlwaysOnHotwordDetectorNoExecutorOtherThread_callbackRunsOnNonMainThread()
            throws Exception {
        testCreateAlwaysOnHotwordDetector(/* useExecutor= */ false, /* runOnMainThread= */ false,
                /* callbackShouldRunOnMainThread= */ false);
    }

    @ApiTest(apis = {
            "android.service.voice.VoiceInteractionService#createAlwaysOnHotwordDetector"})
    @Test
    public void testCreateAlwaysOnHotwordDetectorWithExecutor_callbackRunsOnNonMainThread()
            throws Exception {
        testCreateAlwaysOnHotwordDetector(/* useExecutor= */ true, /* runOnMainThread= */ true,
                /* callbackShouldRunOnMainThread= */ false);
    }

    private void testCreateAlwaysOnHotwordDetector(boolean useExecutor, boolean runOnMainThread,
            boolean callbackShouldRunOnMainThread) throws Exception {
        // Wait the original HotwordDetectionService finish clean up to avoid flaky
        SystemClock.sleep(10_000);

        // Create alwaysOnHotwordDetector and wait result
        mService.createAlwaysOnHotwordDetector(useExecutor, runOnMainThread);

        // reset the value to non-expected value
        mService.setIsDetectorCallbackRunningOnMainThread(!callbackShouldRunOnMainThread);

        // verify callback result
        mService.waitSandboxedDetectionServiceInitializedCalledOrException();
        assertThat(mService.getSandboxedDetectionServiceInitializedResult()).isEqualTo(
                HotwordDetectionService.INITIALIZATION_STATUS_SUCCESS);
        assertThat(mService.isDetectorCallbackRunningOnMainThread()).isEqualTo(
                callbackShouldRunOnMainThread);

        // The AlwaysOnHotwordDetector should be created correctly
        AlwaysOnHotwordDetector alwaysOnHotwordDetector = mService.getAlwaysOnHotwordDetector();
        Objects.requireNonNull(alwaysOnHotwordDetector);

        // reset the value to non-expected value
        mService.setIsDetectorCallbackRunningOnMainThread(!callbackShouldRunOnMainThread);

        // override availability and wait onAvailabilityChanged() callback called
        mService.initAvailabilityChangeLatch();
        alwaysOnHotwordDetector.overrideAvailability(
                AlwaysOnHotwordDetector.STATE_KEYPHRASE_ENROLLED);

        // verify callback result
        mService.waitAvailabilityChangedCalled();
        assertThat(mService.getHotwordDetectionServiceAvailabilityResult()).isEqualTo(
                AlwaysOnHotwordDetector.STATE_KEYPHRASE_ENROLLED);
        assertThat(mService.isDetectorCallbackRunningOnMainThread()).isEqualTo(
                callbackShouldRunOnMainThread);

        try {
            adoptShellPermissionIdentityForHotword();

            verifyDetectFromDspSuccess(alwaysOnHotwordDetector, callbackShouldRunOnMainThread);
            verifyDetectFromDspRejected(alwaysOnHotwordDetector, callbackShouldRunOnMainThread);
            verifyDetectFromDspError(alwaysOnHotwordDetector, callbackShouldRunOnMainThread);

            // destroy detector
            alwaysOnHotwordDetector.destroy();
        } finally {
            // Drop identity adopted.
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    @ApiTest(apis = {
            "android.service.voice.VoiceInteractionService#createHotwordDetector"})
    @Test
    public void testCreateHotwordDetectorNoExecutorMainThread_callbackRunsOnMainThread()
            throws Exception {
        testCreateHotwordDetector(/* useExecutor= */ false, /* runOnMainThread= */ true,
                /* callbackShouldRunOnMainThread= */ true);
    }

    @ApiTest(apis = {
            "android.service.voice.VoiceInteractionService#createHotwordDetector"})
    @Test
    public void testCreateHotwordDetectorNoExecutorOtherThread_callbackRunsOnMainThread()
            throws Exception {
        testCreateHotwordDetector(/* useExecutor= */ false, /* runOnMainThread= */ false,
                /* callbackShouldRunOnMainThread= */ true);
    }

    @ApiTest(apis = {
            "android.service.voice.VoiceInteractionService#createHotwordDetector"})
    @Test
    public void testCreateHotwordDetectorWithExecutor_callbackRunsOnNonMainThread()
            throws Exception {
        testCreateHotwordDetector(/* useExecutor= */ true, /* runOnMainThread= */ true,
                /* callbackShouldRunOnMainThread= */ false);
    }

    private void testCreateHotwordDetector(boolean useExecutor, boolean runOnMainThread,
            boolean callbackShouldRunOnMainThread) throws Exception {
        // Wait the original HotwordDetectionService finish clean up to avoid flaky
        SystemClock.sleep(10_000);

        // Create SoftwareHotwordDetector and wait result
        mService.createSoftwareHotwordDetector(useExecutor, runOnMainThread);

        // reset the value to non-expected value
        mService.setIsDetectorCallbackRunningOnMainThread(!callbackShouldRunOnMainThread);

        // verify callback result
        mService.waitSandboxedDetectionServiceInitializedCalledOrException();
        assertThat(mService.getSandboxedDetectionServiceInitializedResult()).isEqualTo(
                HotwordDetectionService.INITIALIZATION_STATUS_SUCCESS);
        assertThat(mService.isDetectorCallbackRunningOnMainThread()).isEqualTo(
                callbackShouldRunOnMainThread);

        // The SoftwareHotwordDetector should be created correctly
        HotwordDetector softwareHotwordDetector = mService.getSoftwareHotwordDetector();
        Objects.requireNonNull(softwareHotwordDetector);

        try {
            adoptShellPermissionIdentityForHotword();

            // reset the value to non-expected value
            mService.setIsDetectorCallbackRunningOnMainThread(!callbackShouldRunOnMainThread);

            mService.initDetectRejectLatch();
            softwareHotwordDetector.startRecognition();

            // wait onDetected() called and verify the result
            mService.waitOnDetectOrRejectCalled();
            AlwaysOnHotwordDetector.EventPayload detectResult =
                    mService.getHotwordServiceOnDetectedResult();
            Helper.verifyDetectedResult(detectResult, Helper.DETECTED_RESULT);
            assertThat(mService.isDetectorCallbackRunningOnMainThread()).isEqualTo(
                    callbackShouldRunOnMainThread);

            // destroy detector
            softwareHotwordDetector.destroy();
        } finally {
            // Drop identity adopted.
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    private void adoptShellPermissionIdentityForHotword() {
        // Drop any identity adopted earlier.
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        uiAutomation.dropShellPermissionIdentity();
        // need to retain the identity until the callback is triggered
        uiAutomation.adoptShellPermissionIdentity(RECORD_AUDIO, CAPTURE_AUDIO_HOTWORD,
                MANAGE_HOTWORD_DETECTION);
    }

    private void verifyDetectFromDspSuccess(AlwaysOnHotwordDetector alwaysOnHotwordDetector,
            boolean callbackShouldRunOnMainThread)
            throws Exception {
        // reset the value to non-expected value, test onDetected callback
        mService.setIsDetectorCallbackRunningOnMainThread(!callbackShouldRunOnMainThread);

        mService.initDetectRejectLatch();
        alwaysOnHotwordDetector.triggerHardwareRecognitionEventForTest(
                /* status= */ 0, /* soundModelHandle= */ 100, /* halEventReceivedMillis */ 12345,
                /* captureAvailable= */ true, /* captureSession= */ 101, /* captureDelayMs= */ 1000,
                /* capturePreambleMs= */ 1001, /* triggerInData= */ true,
                Helper.createFakeAudioFormat(), new byte[1024],
                Helper.createFakeKeyphraseRecognitionExtraList());

        // wait onDetected() called and verify the result
        mService.waitOnDetectOrRejectCalled();
        AlwaysOnHotwordDetector.EventPayload detectResult =
                mService.getHotwordServiceOnDetectedResult();

        Helper.verifyDetectedResult(detectResult, Helper.DETECTED_RESULT);
        assertThat(mService.isDetectorCallbackRunningOnMainThread()).isEqualTo(
                callbackShouldRunOnMainThread);
    }

    private void verifyDetectFromDspRejected(AlwaysOnHotwordDetector alwaysOnHotwordDetector,
            boolean callbackShouldRunOnMainThread)
            throws Exception {
        // reset the value to non-expected value, test onRejected callback
        mService.setIsDetectorCallbackRunningOnMainThread(!callbackShouldRunOnMainThread);

        mService.initDetectRejectLatch();
        // pass null data parameter
        alwaysOnHotwordDetector.triggerHardwareRecognitionEventForTest(
                /* status= */ 0, /* soundModelHandle= */ 100, /* halEventReceivedMillis= */ 12345,
                /* captureAvailable= */ true, /* captureSession= */ 101, /* captureDelayMs= */ 1000,
                /* capturePreambleMs= */ 1001, /* triggerInData= */ true,
                Helper.createFakeAudioFormat(), null,
                Helper.createFakeKeyphraseRecognitionExtraList());

        // wait onRejected() called and verify the result
        mService.waitOnDetectOrRejectCalled();
        HotwordRejectedResult rejectedResult =
                mService.getHotwordServiceOnRejectedResult();

        assertThat(rejectedResult).isEqualTo(Helper.REJECTED_RESULT);
        assertThat(mService.isDetectorCallbackRunningOnMainThread()).isEqualTo(
                callbackShouldRunOnMainThread);
    }

    private void verifyDetectFromDspError(AlwaysOnHotwordDetector alwaysOnHotwordDetector,
            boolean callbackShouldRunOnMainThread)
            throws Exception {
        // reset the value to non-expected value, test onError callback
        mService.setIsDetectorCallbackRunningOnMainThread(!callbackShouldRunOnMainThread);

        // Update HotwordDetectionService options to delay detection, to cause a timeout
        PersistableBundle options = Helper.createFakePersistableBundleData();
        options.putInt(Utils.KEY_DETECTION_DELAY_MS, 5000);
        alwaysOnHotwordDetector.updateState(options,
                Helper.createFakeSharedMemoryData());

        mService.initOnErrorLatch();
        alwaysOnHotwordDetector.triggerHardwareRecognitionEventForTest(
                /* status= */ 0, /* soundModelHandle= */ 100, /* halEventReceivedMillis= */ 12345,
                /* captureAvailable= */ true, /* captureSession= */ 101, /* captureDelayMs= */ 1000,
                /* capturePreambleMs= */ 1001, /* triggerInData= */ true,
                Helper.createFakeAudioFormat(), new byte[1024],
                Helper.createFakeKeyphraseRecognitionExtraList());

        // wait onError() called
        mService.waitOnErrorCalled();
        assertThat(mService.isDetectorCallbackRunningOnMainThread()).isEqualTo(
                callbackShouldRunOnMainThread);
    }
}
