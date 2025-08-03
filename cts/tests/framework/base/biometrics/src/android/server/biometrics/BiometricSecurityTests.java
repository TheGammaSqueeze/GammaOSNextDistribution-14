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
import static android.server.biometrics.Components.CLASS_3_BIOMETRIC_ACTIVITY;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.content.ComponentName;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricManager.Authenticators;
import android.hardware.biometrics.BiometricTestSession;
import android.hardware.biometrics.SensorProperties;
import android.platform.test.annotations.Presubmit;
import android.server.wm.TestJournalProvider.TestJournal;
import android.server.wm.TestJournalProvider.TestJournalContainer;
import android.util.Log;

import com.android.compatibility.common.util.ApiTest;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;

/**
 * Tests for security related functionality such as downgrading and upgrading biometric strength.
 */
@Presubmit
public class BiometricSecurityTests extends BiometricTestBase {
    private static final String TAG = "BiometricTests/Security";

    private static final String DEVICE_CONFIG_NAMESPACE = "biometrics";
    private static final String DEVICE_CONFIG_BIO_STRENGTH_KEY = "biometric_strengths";

    @BeforeClass
    public static void ensureReset() throws Exception {
        revertSensorStrengths();
    }

    @After
    public void teardown() throws Exception {
        revertSensorStrengths();
    }

    /**
     * A strong biometric should be able to perform auth with any requested strength, since it is
     * the highest biometric class. For example,
     * +-------------------+--------------------+----------+
     * | Original Strength | Requested Strength | Result   |
     * +-------------------+--------------------+----------+
     * | BIOMETRIC_STRONG  | BIOMETRIC_STRONG   | Accepted |
     * +-------------------+--------------------+----------+
     * | BIOMETRIC_STRONG  | BIOMETRIC_WEAK     | Accepted |
     * +-------------------+--------------------+----------+
     * Note that since BiometricPrompt does not support Convenience biometrics, currently we don't
     * have a way to test cases where the requested strength is BIOMETRIC_CONVENIENCE.
     */
    @ApiTest(apis = {
            "android.hardware.biometrics."
                    + "BiometricManager#canAuthenticate",
            "android.hardware.biometrics."
                    + "BiometricPrompt.Builder#setAllowedAuthenticators",
            "android.hardware.biometrics."
                    + "BiometricPrompt#authenticate"})
    @Test
    public void testBiometricStrength_StrongSensor() throws Exception {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        final List<Integer> sensors = getSensorsOfTargetStrength(SensorProperties.STRENGTH_STRONG);
        assumeTrue("testBiometricStrength_StrongSensor: numSensors=" + sensors.size(),
                sensors.size() > 0);

        // Tuple of originalStrength and requestedStrength
        final int[][] testCases = {
                // Request Strong auth
                {Authenticators.BIOMETRIC_STRONG, Authenticators.BIOMETRIC_STRONG},

                // Request Weak auth
                {Authenticators.BIOMETRIC_STRONG, Authenticators.BIOMETRIC_WEAK}
        };

        for (Integer sensorId : sensors) {
            for (int i = 0; i < testCases.length; i++) {
                testBiometricStrength_forSensor_authAllowed(sensorId,
                        testCases[i][0] /* originalStrength */,
                        testCases[i][1] /* requestedStrength */);
            }
        }
    }

    /**
     * A weak biometric may or may not be able to perform auth, depending on the requested strength.
     * For example,
     * +-------------------+--------------------+----------+
     * | Original Strength | Requested Strength | Result   |
     * +-------------------+--------------------+----------+
     * | BIOMETRIC_WEAK    | BIOMETRIC_STRONG   | Error    |
     * +-------------------+--------------------+----------+
     * | BIOMETRIC_WEAK    | BIOMETRIC_WEAK     | Accepted |
     * +-------------------+--------------------+----------+
     * Note that since BiometricPrompt does not support Convenience biometrics, currently we don't
     * have a way to test cases where the requested strength is BIOMETRIC_CONVENIENCE.
     */
    @ApiTest(apis = {
            "android.hardware.biometrics."
                    + "BiometricManager#canAuthenticate",
            "android.hardware.biometrics."
                    + "BiometricPrompt.Builder#setAllowedAuthenticators",
            "android.hardware.biometrics."
                    + "BiometricPrompt#authenticate"})
    @Test
    public void testBiometricStrength_WeakSensor() throws Exception {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        final List<Integer> sensors = getSensorsOfTargetStrength(SensorProperties.STRENGTH_WEAK);
        assumeTrue("testBiometricStrength_WeakSensor: numSensors: " + sensors.size(),
                sensors.size() > 0);

        for (Integer sensorId : sensors) {
            testBiometricStrength_forSensor_authDisallowed(sensorId,
                    Authenticators.BIOMETRIC_WEAK /* originalStrength */,
                    Authenticators.BIOMETRIC_STRONG /* requestedStrength */,
                    mSensorProperties.size() > 1 /* hasMultiSensors */);

            testBiometricStrength_forSensor_authAllowed(sensorId,
                    Authenticators.BIOMETRIC_WEAK /* originalStrength */,
                    Authenticators.BIOMETRIC_WEAK /* requestedStrength */);
        }
    }

    /**
     * A convenience biometric should not be able to perform auth with the following requested
     * strength, due to insufficient strength.
     * +-----------------------+--------------------+--------+
     * | Original Strength     | Requested Strength | Result |
     * +-----------------------+--------------------+--------+
     * | BIOMETRIC_CONVENIENCE | BIOMETRIC_STRONG   | Error  |
     * +-----------------------+--------------------+--------+
     * | BIOMETRIC_CONVENIENCE | BIOMETRIC_WEAK     | Error  |
     * +-----------------------+--------------------+--------+
     * Note that since BiometricPrompt does not support Convenience biometrics, currently we don't
     * have a way to test cases where the requested strength is BIOMETRIC_CONVENIENCE.
     */
    @ApiTest(apis = {
            "android.hardware.biometrics."
                    + "BiometricManager#canAuthenticate",
            "android.hardware.biometrics."
                    + "BiometricPrompt.Builder#setAllowedAuthenticators",
            "android.hardware.biometrics."
                    + "BiometricPrompt#authenticate"})
    @Test
    public void testBiometricStrength_ConvenienceSensor() throws Exception {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        final List<Integer> sensors =
                getSensorsOfTargetStrength(SensorProperties.STRENGTH_CONVENIENCE);
        assumeTrue("testBiometricStrength_ConvenienceSensor: numSensors=" + sensors.size(),
                sensors.size() > 0);

        // Tuple of originalStrength and requestedStrength
        final int[][] testCases = {
                // Request Strong auth
                {Authenticators.BIOMETRIC_CONVENIENCE, Authenticators.BIOMETRIC_STRONG},

                // Request Weak auth
                {Authenticators.BIOMETRIC_CONVENIENCE, Authenticators.BIOMETRIC_WEAK}
        };

        for (Integer sensorId : sensors) {
            for (int i = 0; i < testCases.length; i++) {
                testBiometricStrength_forSensor_authDisallowed(sensorId,
                        testCases[i][0] /* originalStrength */,
                        testCases[i][1] /* requestedStrength */,
                        mSensorProperties.size() > 1 /* hasMultiSensors */);
            }
        }
    }

    private void testBiometricStrength_forSensor_authAllowed(int sensorId, int originalStrength,
            int requestedStrength) throws Exception {
        Log.d(TAG, "testBiometricStrength_forSensor_authAllowed: "
                + ", sensorId=" + sensorId
                + ", originalStrength=" + originalStrength
                + ", requestedStrength=" + requestedStrength);

        final ComponentName componentName = getComponentName(requestedStrength);

        // Reset to the original strength in case it's ever changed before the test
        updateStrengthAndVerify(sensorId, originalStrength);

        try (BiometricTestSession session = mBiometricManager.createTestSession(sensorId);
             ActivitySession activitySession = new ActivitySession(this, componentName)) {
            final int userId = Utils.getUserId();
            waitForAllUnenrolled();
            enrollForSensor(session, sensorId);
            final TestJournal journal =
                    TestJournalContainer.get(activitySession.getComponentName());

            // No error code should be returned for the requested strength
            int errCode = mBiometricManager.canAuthenticate(requestedStrength);
            assertEquals("Device should allow auth with the requested biometric",
                    BiometricManager.BIOMETRIC_SUCCESS, errCode);

            // Launch test activity
            launchActivityAndWaitForResumed(activitySession);

            BiometricCallbackHelper.State callbackState = getCallbackState(journal);
            assertNotNull(callbackState);

            BiometricServiceState state = getCurrentState();
            assertTrue(state.toString(), state.mSensorStates.sensorStates.get(sensorId).isBusy());

            // Auth should work
            successfullyAuthenticate(session, userId, journal);
            assertEquals(callbackState.toString(), 0, callbackState.mNumAuthRejected);
        }
    }

    private void testBiometricStrength_forSensor_authDisallowed(int sensorId, int originalStrength,
            int requestedStrength, boolean hasMultiSensors) throws Exception {
        Log.d(TAG, "testBiometricStrength_forSensor_authDisallowed: "
                + ", sensorId=" + sensorId
                + ", originalStrength=" + originalStrength
                + ", requestedStrength=" + requestedStrength
                + ", hasMultiSensors=" + hasMultiSensors);

        final ComponentName componentName = getComponentName(requestedStrength);

        // Reset to the original strength in case it's ever changed before the test
        updateStrengthAndVerify(sensorId, originalStrength);

        try (BiometricTestSession session = mBiometricManager.createTestSession(sensorId);
             ActivitySession activitySession = new ActivitySession(this, componentName)) {
            waitForAllUnenrolled();
            enrollForSensor(session, sensorId);
            final TestJournal journal =
                    TestJournalContainer.get(activitySession.getComponentName());

            // Error code should be returned for the requested strength due to insufficient strength
            int errCode = mBiometricManager.canAuthenticate(requestedStrength);
            checkErrCode("Device shouldn't allow auth with biometrics that have insufficient"
                            + " strength. errCode: " + errCode,
                    errCode, BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
                    hasMultiSensors);

            // Launch test activity
            launchActivityAndWaitForResumed(activitySession);

            // Auth shouldn't work and error code should be returned
            mInstrumentation.waitForIdleSync();
            BiometricCallbackHelper.State callbackState = getCallbackState(journal);
            assertNotNull(callbackState);
            assertEquals(callbackState.toString(), 0, callbackState.mNumAuthRejected);
            assertEquals(callbackState.toString(), 0, callbackState.mNumAuthAccepted);
            assertEquals(callbackState.toString(), 0, callbackState.mAcquiredReceived.size());
            assertEquals(callbackState.toString(), 1, callbackState.mErrorsReceived.size());
            checkErrCode(callbackState.toString(), (int) callbackState.mErrorsReceived.get(0),
                    BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE, hasMultiSensors);
        }
    }

    /**
     * The strength of a Strong biometric may need to be downgraded to a weaker one if the biometric
     * requires a security update. After downgrading, the biometric may or may not be able to
     * perform auth with the requested strength. For example,
     * +-------------------+-----------------------+--------------------+----------+
     * | Original Strength | Target Strength       | Requested Strength | Result   |
     * +-------------------+-----------------------+--------------------+----------+
     * | BIOMETRIC_STRONG  | BIOMETRIC_WEAK        | BIOMETRIC_STRONG   | Error    |
     * +-------------------+-----------------------+--------------------+----------+
     * | BIOMETRIC_STRONG  | BIOMETRIC_WEAK        | BIOMETRIC_WEAK     | Accepted |
     * +-------------------+-----------------------+--------------------+----------+
     * | BIOMETRIC_STRONG  | BIOMETRIC_CONVENIENCE | BIOMETRIC_STRONG   | Error    |
     * +-------------------+-----------------------+--------------------+----------+
     * | BIOMETRIC_STRONG  | BIOMETRIC_CONVENIENCE | BIOMETRIC_WEAK     | Error    |
     * +-------------------+-----------------------+--------------------+----------+
     * Note that since BiometricPrompt does not support Convenience biometrics, currently we don't
     * have a way to test cases where the requested strength is BIOMETRIC_CONVENIENCE.
     */
    @Test
    public void testBiometricStrengthDowngraded_StrongSensor() throws Exception {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        final List<Integer> sensors = getSensorsOfTargetStrength(SensorProperties.STRENGTH_STRONG);
        assumeTrue("testBiometricStrengthDowngraded_StrongSensor: numSensors=" + sensors.size(),
                sensors.size() > 0);

        // Tuple of originalStrength, targetStrength, and requestedStrength
        final int[][] testCases = {
                // Downgrade Strong to Weak, and request Strong auth
                {Authenticators.BIOMETRIC_STRONG, Authenticators.BIOMETRIC_WEAK,
                        Authenticators.BIOMETRIC_STRONG},

                // Downgrade Strong to Weak, and request Weak auth
                {Authenticators.BIOMETRIC_STRONG, Authenticators.BIOMETRIC_WEAK,
                        Authenticators.BIOMETRIC_WEAK},

                // Downgrade Strong to Convenience, and request Strong auth
                {Authenticators.BIOMETRIC_STRONG, Authenticators.BIOMETRIC_CONVENIENCE,
                        Authenticators.BIOMETRIC_STRONG},

                // Downgrade Strong to Convenience, and request Weak auth
                {Authenticators.BIOMETRIC_STRONG, Authenticators.BIOMETRIC_CONVENIENCE,
                        Authenticators.BIOMETRIC_WEAK}
        };

        for (Integer sensorId : sensors) {
            for (int i = 0; i < testCases.length; i++) {
                testBiometricStrengthDowngraded_forSensor(sensorId,
                        testCases[i][0] /* originalStrength */,
                        testCases[i][1] /* targetStrength */,
                        testCases[i][2] /* requestedStrength */,
                        mSensorProperties.size() > 1 /* hasMultiSensors */);
            }
        }
    }

    /**
     * The strength of a Weak biometric may need to be downgraded to a weaker one if the biometric
     * requires a security update. After downgrading, the biometric may or may not be able to
     * perform auth with the requested strength. For example,
     * +-------------------+-----------------------+--------------------+--------+
     * | Original Strength | Target Strength       | Requested Strength | Result |
     * +-------------------+-----------------------+--------------------+--------+
     * | BIOMETRIC_WEAK    | BIOMETRIC_CONVENIENCE | BIOMETRIC_WEAK     | Error  |
     * +-------------------+-----------------------+--------------------+--------+
     * Note that since BiometricPrompt does not support Convenience biometrics, currently we don't
     * have a way to test cases where the requested strength is BIOMETRIC_CONVENIENCE.
     */
    @Test
    public void testBiometricStrengthDowngraded_WeakSensor() throws Exception {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        final List<Integer> sensors = getSensorsOfTargetStrength(SensorProperties.STRENGTH_WEAK);
        assumeTrue("testBiometricStrengthDowngraded_WeakSensor: numSensors: " + sensors.size(),
                sensors.size() > 0);

        // Tuple of originalStrength, targetStrength, and requestedStrength
        final int[][] testCases = {
                // Downgrade Weak to Convenience, and request Weak auth
                {Authenticators.BIOMETRIC_WEAK, Authenticators.BIOMETRIC_CONVENIENCE,
                        Authenticators.BIOMETRIC_WEAK}
        };

        for (Integer sensorId : sensors) {
            for (int i = 0; i < testCases.length; i++) {
                testBiometricStrengthDowngraded_forSensor(sensorId,
                        testCases[i][0] /* originalStrength */,
                        testCases[i][1] /* targetStrength */,
                        testCases[i][2] /* requestedStrength */,
                        mSensorProperties.size() > 1 /* hasMultiSensors */);
            }
        }
    }

    private void testBiometricStrengthDowngraded_forSensor(int sensorId, int originalStrength,
            int targetStrength, int requestedStrength, boolean hasMultiSensors) throws Exception {
        Log.d(TAG, "testBiometricStrengthDowngraded_forSensor: "
                + ", sensorId=" + sensorId
                + ", originalStrength=" + originalStrength
                + ", targetStrength=" + targetStrength
                + ", requestedStrength=" + requestedStrength
                + ", hasMultiSensors=" + hasMultiSensors);

        final ComponentName componentName = getComponentName(requestedStrength);

        try (BiometricTestSession session = mBiometricManager.createTestSession(sensorId);
             ActivitySession activitySession = new ActivitySession(this, componentName)) {
            final int userId = Utils.getUserId();
            waitForAllUnenrolled();
            enrollForSensor(session, sensorId);
            final TestJournal journal =
                    TestJournalContainer.get(activitySession.getComponentName());

            BiometricServiceState state;

            // Downgrade the biometric strength to the target strength
            updateStrengthAndVerify(sensorId, targetStrength);

            // After downgrading, check whether auth works
            // TODO: should check if targetStrength is at least as strong as the requestedStrength,
            // but some strength constants that are needed for the calculation are not exposed in
            // BiometricManager.
            if (targetStrength == requestedStrength) {
                Log.d(TAG, "The targetStrength is as strong as the requestedStrength");
                // No error code should be returned since biometric has sufficient strength if
                // request weak auth
                int errCode = mBiometricManager.canAuthenticate(requestedStrength);
                assertEquals("Device should allow auth with the requested biometric",
                        BiometricManager.BIOMETRIC_SUCCESS, errCode);

                // Launch test activity
                launchActivityAndWaitForResumed(activitySession);

                state = getCurrentState();
                assertTrue(state.toString(),
                        state.mSensorStates.sensorStates.get(sensorId).isBusy());

                // Auth should work
                successfullyAuthenticate(session, userId, journal);
            } else {
                Log.d(TAG, "The targetStrength is not strong enough");
                // Error code should be returned
                int errCode = mBiometricManager.canAuthenticate(requestedStrength);
                checkErrCode("Device shouldn't allow auth with biometrics that require security"
                                + " update. errCode: " + errCode,
                        errCode, BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED,
                        hasMultiSensors);

                // Launch test activity
                launchActivityAndWaitForResumed(activitySession);

                // Auth shouldn't work and error code should be returned
                mInstrumentation.waitForIdleSync();
                BiometricCallbackHelper.State callbackState = getCallbackState(journal);
                assertNotNull(callbackState);
                assertEquals(callbackState.toString(), 0, callbackState.mNumAuthRejected);
                assertEquals(callbackState.toString(), 0, callbackState.mNumAuthAccepted);
                assertEquals(callbackState.toString(), 0, callbackState.mAcquiredReceived.size());
                assertEquals(callbackState.toString(), 1, callbackState.mErrorsReceived.size());
                checkErrCode(callbackState.toString(), (int) callbackState.mErrorsReceived.get(0),
                        BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED, hasMultiSensors);
            }
        }
    }

    /**
     * Trying to upgrade the strength of a Weak biometric to a stronger strength will not
     * succeed (ie, it's no-op and the biometric strength is still Weak), since the biometric's
     * actual strength can't go past its original strength. After upgrading, the biometric without
     * sufficient strength should not be able to perform the requested auth. For example,
     * +-------------------+------------------+--------------------+--------+
     * | Original Strength | Target Strength  | Requested Strength | Result |
     * +-------------------+------------------+--------------------+--------+
     * | BIOMETRIC_WEAK    | BIOMETRIC_STRONG | BIOMETRIC_STRONG   | Error  |
     * +-------------------+------------------+--------------------+--------+
     */
    @Test
    public void testBiometricStrengthUpgraded_WeakSensor() throws Exception {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        final List<Integer> sensors = getSensorsOfTargetStrength(SensorProperties.STRENGTH_WEAK);
        assumeTrue("testBiometricStrengthUpgraded_WeakSensor: numSensors: " + sensors.size(),
                sensors.size() > 0);

        // Tuple of originalStrength, targetStrength, and requestedStrength
        final int[][] testCases = {
                // Upgrade Weak to Strong, and request Strong auth
                {Authenticators.BIOMETRIC_WEAK, Authenticators.BIOMETRIC_STRONG,
                        Authenticators.BIOMETRIC_STRONG}
        };

        for (Integer sensorId : sensors) {
            for (int i = 0; i < testCases.length; i++) {
                testBiometricStrengthUpgraded_forSensor(sensorId,
                        testCases[i][0] /* originalStrength */,
                        testCases[i][1] /* targetStrength */,
                        testCases[i][2] /* requestedStrength */,
                        mSensorProperties.size() > 1 /* hasMultiSensors */);
            }
        }
    }

    /**
     * Trying to upgrade the strength of a Convenience biometric to a stronger strength will not
     * succeed (ie, it's no-op and the biometric strength is still Convenience), since the
     * biometric's actual strength can't go past its original strength. After upgrading, the
     * biometric without sufficient strength should not be able to perform the requested auth.
     * For example,
     * +-----------------------+------------------+--------------------+--------+
     * | Original Strength     | Target Strength  | Requested Strength | Result |
     * +-----------------------+------------------+--------------------+--------+
     * | BIOMETRIC_CONVENIENCE | BIOMETRIC_STRONG | BIOMETRIC_STRONG   | Error  |
     * +-----------------------+------------------+--------------------+--------+
     * | BIOMETRIC_CONVENIENCE | BIOMETRIC_STRONG | BIOMETRIC_WEAK     | Error  |
     * +-----------------------+------------------+--------------------+--------+
     * | BIOMETRIC_CONVENIENCE | BIOMETRIC_WEAK   | BIOMETRIC_WEAK     | Error  |
     * +-----------------------+------------------+--------------------+--------+
     */
    @Test
    public void testBiometricStrengthUpgraded_ConvenienceSensor() throws Exception {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        final List<Integer> sensors =
                getSensorsOfTargetStrength(SensorProperties.STRENGTH_CONVENIENCE);
        assumeTrue("testBiometricStrengthUpgraded_ConvenienceSensor: numSensors=" + sensors.size(),
                sensors.size() > 0);

        // Tuple of originalStrength, targetStrength, and requestedStrength
        final int[][] testCases = {
                // Upgrade Convenience to Strong, and request Strong auth
                {Authenticators.BIOMETRIC_CONVENIENCE, Authenticators.BIOMETRIC_STRONG,
                        Authenticators.BIOMETRIC_STRONG},

                // Upgrade Convenience to Strong, and request Weak auth
                {Authenticators.BIOMETRIC_CONVENIENCE, Authenticators.BIOMETRIC_STRONG,
                        Authenticators.BIOMETRIC_WEAK},

                // Upgrade Convenience to Weak, and request Weak auth
                {Authenticators.BIOMETRIC_CONVENIENCE, Authenticators.BIOMETRIC_WEAK,
                        Authenticators.BIOMETRIC_WEAK}
        };

        for (Integer sensorId : sensors) {
            for (int i = 0; i < testCases.length; i++) {
                testBiometricStrengthUpgraded_forSensor(sensorId,
                        testCases[i][0] /* originalStrength */,
                        testCases[i][1] /* targetStrength */,
                        testCases[i][2] /* requestedStrength */,
                        mSensorProperties.size() > 1 /* hasMultiSensors */);
            }
        }
    }

    private void testBiometricStrengthUpgraded_forSensor(int sensorId, int originalStrength,
            int targetStrength, int requestedStrength, boolean hasMultiSensors) throws Exception {
        Log.d(TAG, "testBiometricStrengthUpgraded_forSensor: "
                + "sensorId=" + sensorId
                + ", originalStrength=" + originalStrength
                + ", targetStrength=" + targetStrength
                + ", requestedStrength=" + requestedStrength
                + ", hasMultiSensors=" + hasMultiSensors);

        final ComponentName componentName = getComponentName(requestedStrength);

        // Reset to the original strength in case it's ever changed before the test
        updateStrengthAndVerify(sensorId, originalStrength);

        try (BiometricTestSession session = mBiometricManager.createTestSession(sensorId);
             ActivitySession activitySession = new ActivitySession(this, componentName)) {
            waitForAllUnenrolled();
            enrollForSensor(session, sensorId);
            final TestJournal journal =
                    TestJournalContainer.get(activitySession.getComponentName());

            // Try to upgrade the biometric strength to the target strength. The upgrading operation
            // is no-op since the biometric can't be upgraded past its original strength.
            updateStrengthAndIdle(sensorId, targetStrength);
            final int currentStrength = getCurrentStrength(sensorId);
            assertTrue("currentStrength: " + currentStrength, currentStrength == originalStrength);

            // After upgrading, check whether auth works
            // Error code should be returned
            int errCode = mBiometricManager.canAuthenticate(requestedStrength);
            checkErrCode("Device shouldn't allow auth with biometrics without sufficient strength."
                            + " errCode: " + errCode,
                    errCode, BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE, hasMultiSensors);

            // Launch test activity
            launchActivityAndWaitForResumed(activitySession);

            // Auth shouldn't work and error code should be returned
            mInstrumentation.waitForIdleSync();
            BiometricCallbackHelper.State callbackState = getCallbackState(journal);
            assertNotNull(callbackState);
            assertEquals(callbackState.toString(), 0, callbackState.mNumAuthRejected);
            assertEquals(callbackState.toString(), 0, callbackState.mNumAuthAccepted);
            assertEquals(callbackState.toString(), 0, callbackState.mAcquiredReceived.size());
            assertEquals(callbackState.toString(), 1, callbackState.mErrorsReceived.size());
            checkErrCode(callbackState.toString(), (int) callbackState.mErrorsReceived.get(0),
                    BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE, hasMultiSensors);
        }
    }

    private void checkErrCode(String msg, int errCode, int expectedErrCode,
            boolean hasMultiSensors) {
        if (!hasMultiSensors) {
            assertTrue(msg, errCode == expectedErrCode);
        } else {
            // In the multi-sensor case, error code for the first ineligible sensor may be
            // returned so the following error codes are accepted
            assertTrue(msg, errCode == expectedErrCode
                    || errCode == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
                    || errCode == BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE);
        }
    }

    private static ComponentName getComponentName(int requestedStrength) {
        assertTrue("requestedStrength: " + requestedStrength,
                requestedStrength == Authenticators.BIOMETRIC_STRONG ||
                        requestedStrength == Authenticators.BIOMETRIC_WEAK);

        if (requestedStrength == Authenticators.BIOMETRIC_STRONG) {
            return CLASS_3_BIOMETRIC_ACTIVITY;
        }
        return CLASS_2_BIOMETRIC_ACTIVITY;
    }

    private static void revertSensorStrengths() throws Exception {
        Log.d(TAG, "revertSensorStrengths");
        Utils.executeShellCommand("device_config delete " +
                DEVICE_CONFIG_NAMESPACE + " " +
                DEVICE_CONFIG_BIO_STRENGTH_KEY);
        // this is probably not needed, but there are not too many tests so pause to ensure
        // the settings have settled
        Thread.sleep(1000);
    }

    private void updateStrengthAndVerify(int sensorId, int targetStrength) throws Exception {
        updateSensorStrength(sensorId, targetStrength, /* verify */ true);
    }

    private void updateStrengthAndIdle(int sensorId, int targetStrength) throws Exception {
        updateSensorStrength(sensorId, targetStrength, /* verify */ false);
    }

    private void updateSensorStrength(int sensorId, int targetStrength, boolean verify)
            throws Exception {
        Log.d(TAG, "updateStrength: update sensorId=" + sensorId + " to targetStrength="
                + targetStrength);
        Utils.executeShellCommand("device_config put " +
                DEVICE_CONFIG_NAMESPACE + " " +
                DEVICE_CONFIG_BIO_STRENGTH_KEY + " " +
                String.format("%s:%s", sensorId, targetStrength));

        final boolean matchesTarget = waitForSensorToBecomeStrength(sensorId, targetStrength);
        if (verify && !matchesTarget) {
            fail("Timed out waiting for sensorId " + sensorId + " to become target strength: "
                    + targetStrength);
        }
    }

    private boolean waitForSensorToBecomeStrength(int sensorId, int targetStrength)
            throws Exception {
        for (int i = 0; i < 20; i++) {
            final int currentStrength = getCurrentStrength(sensorId);
            if (currentStrength == targetStrength) {
                return true;
            }
            Log.d(TAG, "Not at target strength yet, current: " + currentStrength);
            Thread.sleep(300);
        }
        return false;
    }
}
