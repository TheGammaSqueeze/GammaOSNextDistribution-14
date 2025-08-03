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

package android.server.biometrics.fingerprint;

import static android.hardware.fingerprint.FingerprintManager.FINGERPRINT_ERROR_CANCELED;
import static android.hardware.fingerprint.FingerprintManager.FINGERPRINT_ERROR_USER_CANCELED;
import static android.server.biometrics.fingerprint.Components.AUTH_ON_CREATE_ACTIVITY;
import static android.server.biometrics.util.Components.EMPTY_ACTIVITY;
import static android.server.wm.WindowManagerState.STATE_RESUMED;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.Instrumentation;
import android.hardware.biometrics.BiometricTestSession;
import android.hardware.biometrics.SensorProperties;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Bundle;
import android.platform.test.annotations.AsbSecurityTest;
import android.platform.test.annotations.Presubmit;
import android.server.biometrics.BiometricServiceState;
import android.server.biometrics.SensorStates;
import android.server.biometrics.TestSessionList;
import android.server.biometrics.Utils;
import android.server.wm.ActivityManagerTestBase;
import android.server.wm.TestJournalProvider.TestJournal;
import android.server.wm.TestJournalProvider.TestJournalContainer;
import android.server.wm.UiDeviceUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.android.compatibility.common.util.ApiTest;
import com.android.server.biometrics.nano.SensorServiceStateProto;
import com.android.server.biometrics.nano.SensorStateProto;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

@SuppressWarnings("deprecation")
@Presubmit
public class FingerprintServiceTest extends ActivityManagerTestBase
        implements TestSessionList.Idler {
    private static final String TAG = "FingerprintServiceTest";
    private static final String DESCRIPTOR =
            "android.hardware.biometrics.fingerprint.IFingerprint";
    private static final String DUMPSYS_FINGERPRINT = "dumpsys fingerprint --proto --state";
    private static final int FINGERPRINT_ERROR_VENDOR_BASE = 1000;
    private static final long WAIT_MS = 2000;
    private static final BySelector SELECTOR_BIOMETRIC_PROMPT =
            By.res("com.android.systemui", "biometric_scrollview");

    private SensorStates getSensorStates() throws Exception {
        final byte[] dump = Utils.executeShellCommand(DUMPSYS_FINGERPRINT);
        SensorServiceStateProto proto = SensorServiceStateProto.parseFrom(dump);
        return SensorStates.parseFrom(proto);
    }

    @Override
    public void waitForIdleSensors() {
        try {
            Utils.waitForIdleService(this::getSensorStates);
        } catch (Exception e) {
            Log.e(TAG, "Exception when waiting for idle", e);
        }
    }

    @Nullable
    private static FingerprintCallbackHelper.State getCallbackState(@NonNull TestJournal journal) {
        Utils.waitFor("Waiting for authentication callback",
                () -> journal.extras.containsKey(FingerprintCallbackHelper.KEY));

        final Bundle bundle = journal.extras.getBundle(FingerprintCallbackHelper.KEY);
        if (bundle == null) {
            return null;
        }

        final FingerprintCallbackHelper.State state =
                FingerprintCallbackHelper.State.fromBundle(bundle);

        // Clear the extras since we want to wait for the journal to sync any new info the next
        // time it's read
        journal.extras.clear();

        return state;
    }

    @NonNull private Instrumentation mInstrumentation;
    @Nullable private FingerprintManager mFingerprintManager;
    @NonNull private List<SensorProperties> mSensorProperties;
    @NonNull private UiDevice mDevice;

    @Before
    public void setUp() throws Exception {
        mInstrumentation = getInstrumentation();
        mDevice = UiDevice.getInstance(mInstrumentation);
        mFingerprintManager = mInstrumentation.getContext()
                .getSystemService(FingerprintManager.class);

        // Tests can be skipped on devices without FingerprintManager
        assumeTrue(mFingerprintManager != null);

        mInstrumentation.getUiAutomation().adoptShellPermissionIdentity();

        mSensorProperties = mFingerprintManager.getSensorProperties();

        // Tests can be skipped on devices without fingerprint sensors
        assumeTrue(!mSensorProperties.isEmpty());

        // Turn screen on and dismiss keyguard
        UiDeviceUtils.pressWakeupButton();
        UiDeviceUtils.pressUnlockButton();
    }

    @After
    public void cleanup() throws Exception {
        if (mFingerprintManager == null) {
            return;
        }

        mInstrumentation.waitForIdleSync();
        Utils.waitForIdleService(this::getSensorStates);

        mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
    }

    @ApiTest(apis = {
            "android.hardware.biometrics."
                    + "BiometricTestSession#startEnroll",
            "android.hardware.biometrics."
                    + "BiometricTestSession#finishEnroll"})
    @Test
    public void testEnroll() throws Exception {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        for (SensorProperties prop : mSensorProperties) {
            try (BiometricTestSession session = mFingerprintManager.createTestSession(
                    prop.getSensorId())) {
                testEnrollForSensor(session, prop.getSensorId());
            }
        }
    }

    private void testEnrollForSensor(BiometricTestSession session, int sensorId) throws Exception {
        final int userId = Utils.getUserId();

        session.startEnroll(userId);
        mInstrumentation.waitForIdleSync();
        Utils.waitForIdleService(this::getSensorStates);

        session.finishEnroll(userId);
        mInstrumentation.waitForIdleSync();
        Utils.waitForIdleService(this::getSensorStates);

        final SensorStates sensorStates = getSensorStates();

        // The (sensorId, userId) has one finger enrolled.
        assertEquals(1, sensorStates.sensorStates
                .get(sensorId).getUserStates().get(userId).numEnrolled);
    }

    @ApiTest(apis = {
            "android.hardware.fingerprint."
                    + "FingerprintManager#authenticate"})
    @Test
    public void testAuthenticateFromForegroundActivity() throws Exception {
        assumeTrue(Utils.isFirstApiLevel29orGreater());

        // Manually keep track and close the sessions, since we want to enroll all sensors before
        // requesting auth.
        final int userId = Utils.getUserId();
        try (TestSessionList testSessions = createTestSessionsWithEnrollments(userId)) {
            final TestJournal journal = TestJournalContainer.get(AUTH_ON_CREATE_ACTIVITY);

            // Launch test activity
            launchActivity(AUTH_ON_CREATE_ACTIVITY);
            mWmState.waitForActivityState(AUTH_ON_CREATE_ACTIVITY, STATE_RESUMED);
            mInstrumentation.waitForIdleSync();

            // At least one sensor should be authenticating
            assertFalse(getSensorStates().areAllSensorsIdle());

            // Nothing happened yet
            FingerprintCallbackHelper.State callbackState = getCallbackState(journal);
            assertNotNull(callbackState);
            assertEquals(0, callbackState.mNumAuthRejected);
            assertEquals(0, callbackState.mNumAuthAccepted);
            assertEquals(0, callbackState.mAcquiredReceived.size());
            assertEquals(0, callbackState.mErrorsReceived.size());

            // Auth and check again now
            testSessions.first().acceptAuthentication(userId);
            mInstrumentation.waitForIdleSync();
            callbackState = getCallbackState(journal);
            assertNotNull(callbackState);
            assertTrue(callbackState.mErrorsReceived.isEmpty());
            assertTrue(callbackState.mAcquiredReceived.isEmpty());
            assertEquals(1, callbackState.mNumAuthAccepted);
            assertEquals(0, callbackState.mNumAuthRejected);
        }
    }

    @ApiTest(apis = {
            "android.hardware.fingerprint."
                    + "FingerprintManager#authenticate"})
    @Test
    public void testRejectThenErrorFromForegroundActivity() throws Exception {
        assumeTrue(Utils.isFirstApiLevel29orGreater());

        // Manually keep track and close the sessions, since we want to enroll all sensors before
        // requesting auth.
        final int userId = Utils.getUserId();
        try (TestSessionList testSessions = createTestSessionsWithEnrollments(userId)) {
            final TestJournal journal = TestJournalContainer.get(AUTH_ON_CREATE_ACTIVITY);

            // Launch test activity
            launchActivity(AUTH_ON_CREATE_ACTIVITY);
            mWmState.waitForActivityState(AUTH_ON_CREATE_ACTIVITY,
                    STATE_RESUMED);
            mInstrumentation.waitForIdleSync();
            FingerprintCallbackHelper.State callbackState = getCallbackState(journal);
            assertNotNull(callbackState);

            // Fingerprint rejected
            testSessions.first().rejectAuthentication(userId);
            mInstrumentation.waitForIdleSync();
            callbackState = getCallbackState(journal);
            assertNotNull(callbackState);
            assertEquals(1, callbackState.mNumAuthRejected);
            assertEquals(0, callbackState.mNumAuthAccepted);
            assertEquals(0, callbackState.mAcquiredReceived.size());
            assertEquals(0, callbackState.mErrorsReceived.size());

            // Send an acquire message
            final int aidlSensorId = Utils.getAidlFingerprintSensorId();
            if (aidlSensorId >= 0 && testSessions.first().equals(
                            testSessions.find(aidlSensorId))) {
                testSessions.first().notifyAcquired(userId, 2 /* AcquiredInfo.PARTIAL */);
            } else {
                testSessions.first().notifyAcquired(userId,
                        FingerprintManager.FINGERPRINT_ACQUIRED_PARTIAL);
            }

            mInstrumentation.waitForIdleSync();
            callbackState = getCallbackState(journal);
            assertNotNull(callbackState);
            assertEquals(1, callbackState.mNumAuthRejected);
            assertEquals(0, callbackState.mNumAuthAccepted);
            assertEquals(1, callbackState.mAcquiredReceived.size());
            assertEquals(FingerprintManager.FINGERPRINT_ACQUIRED_PARTIAL,
                    (int) callbackState.mAcquiredReceived.get(0));
            assertEquals(0, callbackState.mErrorsReceived.size());

            // Send an error
            testSessions.first().notifyError(userId, FINGERPRINT_ERROR_CANCELED);
            mInstrumentation.waitForIdleSync();
            callbackState = getCallbackState(journal);
            assertNotNull(callbackState);
            assertEquals(1, callbackState.mNumAuthRejected);
            assertEquals(0, callbackState.mNumAuthAccepted);
            assertEquals(1, callbackState.mAcquiredReceived.size());
            assertEquals(FingerprintManager.FINGERPRINT_ACQUIRED_PARTIAL,
                    (int) callbackState.mAcquiredReceived.get(0));
            assertEquals(1, callbackState.mErrorsReceived.size());
            assertEquals(FINGERPRINT_ERROR_CANCELED,
                    (int) callbackState.mErrorsReceived.get(0));

            // Authentication lifecycle is done
            assertTrue(getSensorStates().areAllSensorsIdle());
        }
    }

    @ApiTest(apis = {
            "android.hardware.fingerprint."
                    + "FingerprintManager#authenticate"})
    @Test
    @AsbSecurityTest(cveBugId = 214261879)
    public void testAuthCancelsWhenAppSwitched() throws Exception {
        assumeTrue(Utils.isFirstApiLevel29orGreater());

        final int userId = Utils.getUserId();
        try (TestSessionList testSessions = createTestSessionsWithEnrollments(userId)) {
            launchActivity(AUTH_ON_CREATE_ACTIVITY);
            final UiObject2 prompt = mDevice.wait(
                    Until.findObject(SELECTOR_BIOMETRIC_PROMPT), WAIT_MS);
            if (prompt == null) {
                // some devices do not show a prompt (i.e. rear sensor)
                mWmState.waitForActivityState(AUTH_ON_CREATE_ACTIVITY, STATE_RESUMED);
            }
            assertThat(getSensorStates().areAllSensorsIdle()).isFalse();

            launchActivity(EMPTY_ACTIVITY);
            if (prompt != null) {
                assertThat(mDevice.wait(Until.gone(SELECTOR_BIOMETRIC_PROMPT), WAIT_MS)).isTrue();
            } else {
                // devices that do not show a sysui prompt may not cancel until an attempt is made
                mWmState.waitForActivityState(EMPTY_ACTIVITY, STATE_RESUMED);
                //TODO(b/295160922): wait for BP UI animation over before accepting auth
                Thread.sleep(300);
                testSessions.first().acceptAuthentication(userId);
                mInstrumentation.waitForIdleSync();
            }
            waitForIdleSensors();

            final TestJournal journal = TestJournalContainer.get(AUTH_ON_CREATE_ACTIVITY);
            FingerprintCallbackHelper.State callbackState = getCallbackState(journal);
            assertThat(callbackState).isNotNull();
            assertThat(callbackState.mNumAuthAccepted).isEqualTo(0);
            assertThat(callbackState.mNumAuthRejected).isEqualTo(0);

            // FingerprintUtils#isKnownErrorCode does not recognize FINGERPRINT_ERROR_USER_CANCELED
            // so accept this error as a vendor error or the normal value
            assertThat(callbackState.mErrorsReceived).hasSize(1);
            assertThat(callbackState.mErrorsReceived.get(0)).isAnyOf(
                    FINGERPRINT_ERROR_CANCELED,
                    FINGERPRINT_ERROR_USER_CANCELED,
                    FINGERPRINT_ERROR_VENDOR_BASE + FINGERPRINT_ERROR_USER_CANCELED);

            assertThat(getSensorStates().areAllSensorsIdle()).isTrue();
        }
    }

    private TestSessionList createTestSessionsWithEnrollments(int userId) {
        final TestSessionList testSessions = new TestSessionList(this);
        for (SensorProperties prop : mSensorProperties) {
            BiometricTestSession session =
                    mFingerprintManager.createTestSession(prop.getSensorId());
            testSessions.put(prop.getSensorId(), session);

            session.startEnroll(userId);
            mInstrumentation.waitForIdleSync();
            waitForIdleSensors();

            session.finishEnroll(userId);
            mInstrumentation.waitForIdleSync();
            waitForIdleSensors();
        }
        return testSessions;
    }

    private boolean hasUdfps() throws Exception {
        final BiometricServiceState state = Utils.getBiometricServiceCurrentState();
        return state.mSensorStates.containsModalityFlag(SensorStateProto.FINGERPRINT_UDFPS);
    }
}
