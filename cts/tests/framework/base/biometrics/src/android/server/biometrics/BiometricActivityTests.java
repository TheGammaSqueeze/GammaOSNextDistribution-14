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

package android.server.biometrics;

import static android.server.biometrics.Components.CLASS_2_BIOMETRIC_ACTIVITY;
import static android.server.biometrics.Components.CLASS_2_BIOMETRIC_OR_CREDENTIAL_ACTIVITY;

import static com.android.server.biometrics.nano.BiometricServiceStateProto.STATE_AUTH_PAUSED;
import static com.android.server.biometrics.nano.BiometricServiceStateProto.STATE_AUTH_STARTED_UI_SHOWING;
import static com.android.server.biometrics.nano.BiometricServiceStateProto.STATE_SHOWING_DEVICE_CREDENTIAL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.hardware.biometrics.BiometricPrompt;
import android.hardware.biometrics.BiometricTestSession;
import android.hardware.biometrics.SensorProperties;
import android.platform.test.annotations.Presubmit;
import android.server.wm.TestJournalProvider;
import android.server.wm.WindowManagerState;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Tests that require the use of a test activity.
 */
@Presubmit
public class BiometricActivityTests extends BiometricTestBase {
    private static final String TAG = "BiometricTests/Activity";

    @ApiTest(apis = {
            "android.hardware.biometrics."
                    + "BiometricPrompt.Builder#setAllowedAuthenticators",
            "android.hardware.biometrics."
                    + "BiometricPrompt#authenticate"})
    @Test
    public void testBiometricOnly_authenticateFromForegroundActivity() throws Exception {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        for (SensorProperties prop : mSensorProperties) {
            if (prop.getSensorStrength() == SensorProperties.STRENGTH_CONVENIENCE) {
                continue;
            }

            try (BiometricTestSession session =
                         mBiometricManager.createTestSession(prop.getSensorId());
                 ActivitySession activitySession =
                         new ActivitySession(this, CLASS_2_BIOMETRIC_ACTIVITY)) {
                testBiometricOnly_authenticateFromForegroundActivity_forSensor(
                        session, prop.getSensorId(), activitySession);
            }
        }
    }

    private void testBiometricOnly_authenticateFromForegroundActivity_forSensor(
            @NonNull BiometricTestSession session, int sensorId,
            @NonNull ActivitySession activitySession) throws Exception {
        Log.d(TAG, "testBiometricOnly_authenticateFromForegroundActivity_forSensor: " + sensorId);
        final int userId = Utils.getUserId();
        waitForAllUnenrolled();
        enrollForSensor(session, sensorId);
        final TestJournalProvider.TestJournal journal = TestJournalProvider.TestJournalContainer
                .get(activitySession.getComponentName());

        // Launch test activity
        launchActivityAndWaitForResumed(activitySession);

        // The sensor being tested should not be idle
        BiometricServiceState state = getCurrentState();
        assertTrue(state.toString(), state.mSensorStates.sensorStates.get(sensorId).isBusy());

        // Nothing happened yet
        BiometricCallbackHelper.State callbackState = getCallbackState(journal);
        assertEquals(callbackState.toString(), 0, callbackState.mNumAuthRejected);
        assertEquals(callbackState.toString(), 0, callbackState.mNumAuthAccepted);
        assertEquals(callbackState.toString(), 0, callbackState.mAcquiredReceived.size());
        assertEquals(callbackState.toString(), 0, callbackState.mErrorsReceived.size());

        // Auth and check again now
        successfullyAuthenticate(session, userId, journal);
        assertEquals(callbackState.toString(), 0, callbackState.mNumAuthRejected);
    }

    @ApiTest(apis = {
            "android.hardware.biometrics."
                    + "BiometricPrompt.Builder#setAllowedAuthenticators",
            "android.hardware.biometrics."
                    + "BiometricPrompt#authenticate"})
    @Test
    public void testBiometricOnly_rejectThenErrorFromForegroundActivity() throws Exception {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        for (SensorProperties prop : mSensorProperties) {
            if (prop.getSensorStrength() == SensorProperties.STRENGTH_CONVENIENCE) {
                continue;
            }

            try (BiometricTestSession session =
                         mBiometricManager.createTestSession(prop.getSensorId());
                 ActivitySession activitySession =
                         new ActivitySession(this, CLASS_2_BIOMETRIC_ACTIVITY)) {
                testBiometricOnly_rejectThenErrorFromForegroundActivity_forSensor(
                        session, prop.getSensorId(), activitySession);
            }
        }
    }

    private void testBiometricOnly_rejectThenErrorFromForegroundActivity_forSensor(
            @NonNull BiometricTestSession session, int sensorId,
            @NonNull ActivitySession activitySession) throws Exception {
        Log.d(TAG, "testBiometricOnly_rejectThenErrorFromForegroundActivity_forSensor: "
                + sensorId);
        final int userId = Utils.getUserId();
        waitForAllUnenrolled();
        enrollForSensor(session, sensorId);

        final TestJournalProvider.TestJournal journal =
                TestJournalProvider.TestJournalContainer.get(activitySession.getComponentName());

        // Launch test activity
        launchActivityAndWaitForResumed(activitySession);
        BiometricCallbackHelper.State callbackState = getCallbackState(journal);
        assertNotNull(callbackState);

        BiometricServiceState state = getCurrentState();
        assertTrue(state.toString(), state.mSensorStates.sensorStates.get(sensorId).isBusy());

        // Biometric rejected
        session.rejectAuthentication(userId);
        mInstrumentation.waitForIdleSync();
        callbackState = getCallbackState(journal);
        assertEquals(callbackState.toString(), 1, callbackState.mNumAuthRejected);
        assertEquals(callbackState.toString(), 0, callbackState.mNumAuthAccepted);
        assertEquals(callbackState.toString(), 0, callbackState.mAcquiredReceived.size());
        assertEquals(callbackState.toString(), 0, callbackState.mErrorsReceived.size());

        state = getCurrentState();
        Log.d(TAG, "State after rejectAuthentication: " + state);
        if (state.mState == STATE_AUTH_PAUSED) {
            findAndPressButton(BUTTON_ID_TRY_AGAIN);
            mInstrumentation.waitForIdleSync();
            waitForState(STATE_AUTH_STARTED_UI_SHOWING);
        }

        // Send an error
        session.notifyError(userId, BiometricPrompt.BIOMETRIC_ERROR_CANCELED);
        mInstrumentation.waitForIdleSync();
        callbackState = getCallbackState(journal);
        assertEquals(callbackState.toString(), 1, callbackState.mNumAuthRejected);
        assertEquals(callbackState.toString(), 0, callbackState.mNumAuthAccepted);
        assertEquals(callbackState.toString(), 0, callbackState.mAcquiredReceived.size());
        assertEquals(callbackState.toString(), 1, callbackState.mErrorsReceived.size());
        assertEquals(callbackState.toString(), BiometricPrompt.BIOMETRIC_ERROR_CANCELED,
                (int) callbackState.mErrorsReceived.get(0));
    }

    @ApiTest(apis = {
            "android.hardware.biometrics."
                    + "BiometricPrompt.Builder#setAllowedAuthenticators",
            "android.hardware.biometrics."
                    + "BiometricPrompt#authenticate"})
    @Test
    public void testBiometricOnly_rejectThenAuthenticate() throws Exception {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        for (SensorProperties prop : mSensorProperties) {
            if (prop.getSensorStrength() == SensorProperties.STRENGTH_CONVENIENCE) {
                continue;
            }

            try (BiometricTestSession session =
                         mBiometricManager.createTestSession(prop.getSensorId());
                 ActivitySession activitySession =
                         new ActivitySession(this, CLASS_2_BIOMETRIC_ACTIVITY)) {
                testBiometricOnly_rejectThenAuthenticate_forSensor(
                        session, prop.getSensorId(), activitySession);
            }
        }
    }

    private void testBiometricOnly_rejectThenAuthenticate_forSensor(
            @NonNull BiometricTestSession session, int sensorId,
            @NonNull ActivitySession activitySession) throws Exception {
        Log.d(TAG, "testBiometricOnly_rejectThenAuthenticate_forSensor: " + sensorId);

        final int userId = Utils.getUserId();
        waitForAllUnenrolled();
        enrollForSensor(session, sensorId);

        final TestJournalProvider.TestJournal journal =
                TestJournalProvider.TestJournalContainer.get(activitySession.getComponentName());

        // Launch test activity
        activitySession.start();
        mWmState.waitForActivityState(activitySession.getComponentName(),
                WindowManagerState.STATE_RESUMED);
        mInstrumentation.waitForIdleSync();
        BiometricCallbackHelper.State callbackState = getCallbackState(journal);
        assertNotNull(callbackState);

        BiometricServiceState state = getCurrentState();
        assertTrue(state.toString(), state.mSensorStates.sensorStates.get(sensorId).isBusy());

        session.rejectAuthentication(userId);
        mInstrumentation.waitForIdleSync();
        callbackState = getCallbackState(journal);
        assertEquals(callbackState.toString(), 1, callbackState.mNumAuthRejected);
        assertEquals(callbackState.toString(), 0, callbackState.mNumAuthAccepted);
        assertEquals(callbackState.toString(), 0, callbackState.mAcquiredReceived.size());
        assertEquals(callbackState.toString(), 0, callbackState.mErrorsReceived.size());

        state = getCurrentState();
        Log.d(TAG, "State after rejectAuthentication: " + state);
        if (state.mState == STATE_AUTH_PAUSED) {
            findAndPressButton(BUTTON_ID_TRY_AGAIN);
            mInstrumentation.waitForIdleSync();
            waitForState(STATE_AUTH_STARTED_UI_SHOWING);
        }

        // Accept authentication and end
        successfullyAuthenticate(session, userId, journal);
        assertEquals(callbackState.toString(), 1, callbackState.mNumAuthRejected);
    }

    // TODO(b/236763921): fix this test and unignore.
    @Ignore
    @ApiTest(apis = {
            "android.hardware.biometrics."
                    + "BiometricPrompt.Builder#setAllowedAuthenticators",
            "android.hardware.biometrics."
                    + "BiometricPrompt.Builder#setNegativeButton",
            "android.hardware.biometrics."
                    + "BiometricPrompt#authenticate"})
    @Test
    public void testBiometricOnly_negativeButtonInvoked() throws Exception {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        for (SensorProperties prop : mSensorProperties) {
            if (prop.getSensorStrength() == SensorProperties.STRENGTH_CONVENIENCE) {
                continue;
            }

            try (BiometricTestSession session =
                         mBiometricManager.createTestSession(prop.getSensorId());
                 ActivitySession activitySession =
                         new ActivitySession(this, CLASS_2_BIOMETRIC_ACTIVITY)) {
                testBiometricOnly_negativeButtonInvoked_forSensor(
                        session, prop.getSensorId(), activitySession);
            }
        }
    }

    private void testBiometricOnly_negativeButtonInvoked_forSensor(
            @NonNull BiometricTestSession session, int sensorId,
            @NonNull ActivitySession activitySession) throws Exception {
        Log.d(TAG, "testBiometricOnly_negativeButtonInvoked_forSensor: " + sensorId);
        waitForAllUnenrolled();
        enrollForSensor(session, sensorId);
        final TestJournalProvider.TestJournal journal = TestJournalProvider.TestJournalContainer
                .get(activitySession.getComponentName());

        // Launch test activity
        launchActivityAndWaitForResumed(activitySession);
        BiometricCallbackHelper.State callbackState = getCallbackState(journal);
        assertNotNull(callbackState);

        BiometricServiceState state = getCurrentState();
        assertFalse(state.toString(), state.mSensorStates.areAllSensorsIdle());
        assertFalse(state.toString(), callbackState.mNegativeButtonPressed);

        // Press the negative button
        findAndPressButton(BUTTON_ID_NEGATIVE);

        callbackState = getCallbackState(journal);
        assertTrue(callbackState.toString(), callbackState.mNegativeButtonPressed);
        assertEquals(callbackState.toString(), 0, callbackState.mNumAuthRejected);
        assertEquals(callbackState.toString(), 0, callbackState.mNumAuthAccepted);
        assertEquals(callbackState.toString(), 0, callbackState.mAcquiredReceived.size());
        assertEquals(callbackState.toString(), 0, callbackState.mErrorsReceived.size());
    }


    // TODO(b/236763921): fix this test and unignore.
    @Ignore
    @ApiTest(apis = {
            "android.hardware.biometrics."
                    + "BiometricPrompt.Builder#setAllowedAuthenticators",
            "android.hardware.biometrics."
                    + "BiometricPrompt#authenticate"})
    @Test
    public void testBiometricOrCredential_credentialButtonInvoked_biometricEnrolled()
            throws Exception {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        // Test behavior for each sensor when biometrics are enrolled
        try (CredentialSession credentialSession = new CredentialSession()) {
            credentialSession.setCredential();
            for (SensorProperties prop : mSensorProperties) {
                if (prop.getSensorStrength() == SensorProperties.STRENGTH_CONVENIENCE) {
                    continue;
                }

                try (BiometricTestSession session =
                             mBiometricManager.createTestSession(prop.getSensorId());
                     ActivitySession activitySession =
                             new ActivitySession(this, CLASS_2_BIOMETRIC_OR_CREDENTIAL_ACTIVITY)) {
                    testBiometricOrCredential_credentialButtonInvoked_forConfiguration(
                            session, prop.getSensorId(), true /* shouldEnrollBiometric */,
                            activitySession);
                }
            }
        }
    }

    @ApiTest(apis = {
            "android.hardware.biometrics."
                    + "BiometricPrompt.Builder#setAllowedAuthenticators",
            "android.hardware.biometrics."
                    + "BiometricPrompt#authenticate"})
    @Test
    public void testBiometricOrCredential_credentialButtonInvoked_biometricNotEnrolled()
            throws Exception {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        // Test behavior for each sensor when biometrics are not enrolled
        try (CredentialSession credentialSession = new CredentialSession()) {
            credentialSession.setCredential();
            for (SensorProperties prop : mSensorProperties) {
                try (
                        BiometricTestSession session = mBiometricManager.createTestSession(
                                prop.getSensorId());
                        ActivitySession activitySession = new ActivitySession(this,
                                CLASS_2_BIOMETRIC_OR_CREDENTIAL_ACTIVITY)
                ) {
                    testBiometricOrCredential_credentialButtonInvoked_forConfiguration(session,
                            prop.getSensorId(), false /* shouldEnrollBiometric */, activitySession);
                }
            }
        }
    }

    @ApiTest(apis = {
            "android.hardware.biometrics."
                    + "BiometricPrompt.Builder#setAllowedAuthenticators",
            "android.hardware.biometrics."
                    + "BiometricPrompt#authenticate"})
    @Test
    public void testBiometricOrCredential_credentialButtonInvoked_noBiometricSensor()
            throws Exception {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        assumeTrue(mSensorProperties.isEmpty());
        try (CredentialSession credentialSession = new CredentialSession()) {
            credentialSession.setCredential();
            try (ActivitySession activitySession =
                         new ActivitySession(this, CLASS_2_BIOMETRIC_OR_CREDENTIAL_ACTIVITY)) {
                testBiometricOrCredential_credentialButtonInvoked_forConfiguration(null,
                        0 /* sensorId */, false /* shouldEnrollBiometric */, activitySession);
            }
        }
    }

    private void testBiometricOrCredential_credentialButtonInvoked_forConfiguration(
            @Nullable BiometricTestSession session, int sensorId, boolean shouldEnrollBiometric,
            @NonNull ActivitySession activitySession)
            throws Exception {
        Log.d(TAG, "testBiometricOrCredential_credentialButtonInvoked_forConfiguration: "
                + "sensorId=" + sensorId
                + ", shouldEnrollBiometric=" + shouldEnrollBiometric);
        if (shouldEnrollBiometric) {
            assertNotNull(session);
            waitForAllUnenrolled();
            enrollForSensor(session, sensorId);
        }

        final TestJournalProvider.TestJournal journal = TestJournalProvider.TestJournalContainer
                .get(activitySession.getComponentName());

        // Launch test activity
        launchActivityAndWaitForResumed(activitySession);
        BiometricCallbackHelper.State callbackState;

        BiometricServiceState state = getCurrentState();
        Log.d(TAG, "State after launching activity: " + state);
        if (shouldEnrollBiometric) {
            waitForState(STATE_AUTH_STARTED_UI_SHOWING);
            assertTrue(state.toString(), state.mSensorStates.sensorStates.get(sensorId).isBusy());
            // Press the credential button
            findAndPressButton(BUTTON_ID_USE_CREDENTIAL);
            callbackState = getCallbackState(journal);
            assertFalse(callbackState.toString(), callbackState.mNegativeButtonPressed);
            assertEquals(callbackState.toString(), 0, callbackState.mNumAuthRejected);
            assertEquals(callbackState.toString(), 0, callbackState.mNumAuthAccepted);
            assertEquals(callbackState.toString(), 0, callbackState.mAcquiredReceived.size());
            assertEquals(callbackState.toString(), 0, callbackState.mErrorsReceived.size());
            waitForState(STATE_SHOWING_DEVICE_CREDENTIAL);
        }

        successfullyEnterCredential();

        callbackState = getCallbackState(journal);
        assertEquals(callbackState.toString(), 0, callbackState.mNumAuthRejected);
        assertEquals(callbackState.toString(), 1, callbackState.mNumAuthAccepted);
        assertEquals(callbackState.toString(), 0, callbackState.mAcquiredReceived.size());
        assertEquals(callbackState.toString(), 0, callbackState.mErrorsReceived.size());
    }

}
