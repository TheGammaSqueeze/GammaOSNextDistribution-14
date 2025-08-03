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

import static android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyObject;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.content.pm.PackageManager;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricManager.Authenticators;
import android.hardware.biometrics.BiometricPrompt;
import android.hardware.biometrics.BiometricTestSession;
import android.hardware.biometrics.Flags;
import android.hardware.biometrics.SensorProperties;
import android.os.CancellationSignal;
import android.os.SystemClock;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.util.Log;

import androidx.test.uiautomator.UiObject2;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.CddTest;
import com.android.server.biometrics.nano.SensorStateProto;

import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Simple tests.
 */
@Presubmit
public class BiometricSimpleTests extends BiometricTestBase {
    private static final String TAG = "BiometricTests/Simple";

    /**
     * Tests that enrollments created via {@link BiometricTestSession} show up in the
     * biometric dumpsys.
     */
    @ApiTest(apis = {
            "android.hardware.biometrics."
                    + "BiometricTestSession#startEnroll",
            "android.hardware.biometrics."
                    + "BiometricTestSession#finishEnroll"})
    @Test
    public void testEnroll() throws Exception {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        for (SensorProperties prop : mSensorProperties) {
            try (BiometricTestSession session =
                         mBiometricManager.createTestSession(prop.getSensorId())) {
                enrollForSensor(session, prop.getSensorId());
            }
        }
    }

    /**
     * Tests that the sensorIds retrieved via {@link BiometricManager#getSensorProperties()} and
     * the dumpsys are consistent with each other.
     */
    @ApiTest(apis = {
            "android.hardware.biometrics."
                    + "BiometricManager#getSensorProperties"})
    @Test
    public void testSensorPropertiesAndDumpsysMatch() throws Exception {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        final BiometricServiceState state = getCurrentState();

        assertEquals(mSensorProperties.size(), state.mSensorStates.sensorStates.size());
        for (SensorProperties prop : mSensorProperties) {
            assertTrue(state.mSensorStates.sensorStates.containsKey(prop.getSensorId()));
        }
    }

    /**
     * Tests that the PackageManager features and biometric dumpsys are consistent with each other.
     */
    @ApiTest(apis = {
            "android.content.pm."
                    + "PackageManager#FEATURE_FINGERPRINT",
            "android.content.pm."
                    + "PackageManager#FEATURE_FACE"})
    @Test
    public void testPackageManagerAndDumpsysMatch() throws Exception {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        final BiometricServiceState state = getCurrentState();
        final PackageManager pm = mContext.getPackageManager();
        if (mSensorProperties.isEmpty()) {
            assertTrue(state.mSensorStates.sensorStates.isEmpty());

            final File initGsiRc = new File("/system/system_ext/etc/init/init.gsi.rc");
            if (!initGsiRc.exists()) {
                assertFalse(pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT));
                assertFalse(pm.hasSystemFeature(PackageManager.FEATURE_FACE));
                assertFalse(pm.hasSystemFeature(PackageManager.FEATURE_IRIS));
            }

            assertTrue(state.mSensorStates.sensorStates.isEmpty());
        } else {
            assertEquals(pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT),
                    state.mSensorStates.containsModality(SensorStateProto.FINGERPRINT));
            assertEquals(pm.hasSystemFeature(PackageManager.FEATURE_FACE),
                    state.mSensorStates.containsModality(SensorStateProto.FACE));
            assertEquals(pm.hasSystemFeature(PackageManager.FEATURE_IRIS),
                    state.mSensorStates.containsModality(SensorStateProto.IRIS));
        }
    }

    @ApiTest(apis = {
            "android.hardware.biometrics."
                    + "BiometricManager#canAuthenticate"})
    @Test
    public void testCanAuthenticate_whenNoSensors() {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        if (mSensorProperties.isEmpty()) {
            assertEquals(BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
                    mBiometricManager.canAuthenticate(Authenticators.BIOMETRIC_WEAK));
            assertEquals(BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
                    mBiometricManager.canAuthenticate(Authenticators.BIOMETRIC_STRONG));
        }
    }

    @ApiTest(apis = {
            "android.hardware.biometrics."
                    + "BiometricPrompt.Builder#setConfirmationRequired",
            "android.hardware.biometrics."
                    + "BiometricPrompt#authenticate",
            "android.hardware.biometrics."
                    + "BiometricPrompt#isConfirmationRequired"})
    @Test
    public void testIsConfirmationRequired() throws Exception {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        for (SensorProperties props : mSensorProperties) {
            if (props.getSensorStrength() == SensorProperties.STRENGTH_CONVENIENCE) {
                continue;
            }

            Log.d(TAG, "testIsConfirmationRequired, sensor: " + props.getSensorId());

            try (BiometricTestSession session =
                         mBiometricManager.createTestSession(props.getSensorId())) {

                final int authenticatorStrength =
                        Utils.testApiStrengthToAuthenticatorStrength(props.getSensorStrength());

                assertEquals("Sensor: " + props.getSensorId()
                                + ", strength: " + props.getSensorStrength(),
                        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED,
                        mBiometricManager.canAuthenticate(authenticatorStrength));

                enrollForSensor(session, props.getSensorId());

                assertEquals("Sensor: " + props.getSensorId()
                                + ", strength: " + props.getSensorStrength(),
                        BiometricManager.BIOMETRIC_SUCCESS,
                        mBiometricManager.canAuthenticate(authenticatorStrength));

                BiometricPrompt.AuthenticationCallback callback =
                        mock(BiometricPrompt.AuthenticationCallback.class);

                BiometricPrompt prompt = showDefaultBiometricPrompt(props.getSensorId(), callback,
                        new CancellationSignal());

                assertTrue(prompt.isConfirmationRequired());
                successfullyAuthenticate(session, 0 /* userId */, callback);
            }
        }
    }

    @ApiTest(apis = {
            "android.hardware.biometrics."
                    + "BiometricPrompt.Builder#setAllowedAuthenticators",
            "android.hardware.biometrics."
                    + "BiometricPrompt#authenticate",
            "android.hardware.biometrics."
                    + "BiometricPrompt#getAllowedAuthenticators"})
    @Test
    public void testSetAllowedAuthenticators_weakBiometric() {
        testSetAllowedAuthenticators(Authenticators.BIOMETRIC_WEAK);
    }

    @ApiTest(apis = {
            "android.hardware.biometrics."
                    + "BiometricPrompt.Builder#setAllowedAuthenticators",
            "android.hardware.biometrics."
                    + "BiometricPrompt#authenticate",
            "android.hardware.biometrics."
                    + "BiometricPrompt#getAllowedAuthenticators"})
    @Test
    public void testSetAllowedAuthenticators_strongBiometric() {
        testSetAllowedAuthenticators(Authenticators.BIOMETRIC_STRONG);
    }

    @ApiTest(apis = {
            "android.hardware.biometrics."
                    + "BiometricPrompt.Builder#setAllowedAuthenticators",
            "android.hardware.biometrics."
                    + "BiometricPrompt#authenticate",
            "android.hardware.biometrics."
                    + "BiometricPrompt#getAllowedAuthenticators"})
    @Test
    public void testSetAllowedAuthenticators_credential() {
        testSetAllowedAuthenticators(Authenticators.DEVICE_CREDENTIAL);
    }

    @ApiTest(apis = {
            "android.hardware.biometrics."
                    + "BiometricPrompt.Builder#setAllowedAuthenticators",
            "android.hardware.biometrics."
                    + "BiometricPrompt#authenticate",
            "android.hardware.biometrics."
                    + "BiometricPrompt#getAllowedAuthenticators"})
    @Test
    public void testSetAllowedAuthenticators_weakBiometricAndCredential() {
        testSetAllowedAuthenticators(
                Authenticators.BIOMETRIC_WEAK | Authenticators.DEVICE_CREDENTIAL);
    }

    @ApiTest(apis = {
            "android.hardware.biometrics."
                    + "BiometricPrompt.Builder#setAllowedAuthenticators",
            "android.hardware.biometrics."
                    + "BiometricPrompt#authenticate",
            "android.hardware.biometrics."
                    + "BiometricPrompt#getAllowedAuthenticators"})
    @Test
    public void testSetAllowedAuthenticators_StrongBiometricAndCredential() {
        testSetAllowedAuthenticators(
                Authenticators.BIOMETRIC_STRONG | Authenticators.DEVICE_CREDENTIAL);
    }

    private void testSetAllowedAuthenticators(int authenticators) {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        BiometricPrompt prompt = showBiometricPromptWithAuthenticators(authenticators);
        assertEquals(authenticators, prompt.getAllowedAuthenticators());
    }

    @ApiTest(apis = {
            "android.hardware.biometrics."
                    + "BiometricManager#canAuthenticate",
            "android.hardware.biometrics."
                    + "BiometricPrompt.Builder#setAllowedAuthenticators",
            "android.hardware.biometrics."
                    + "BiometricPrompt#authenticate"})
    @Test
    public void testInvalidInputs() {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        for (int i = 0; i < 32; i++) {
            final int authenticator = 1 << i;
            // If it's a public constant, no need to test
            if (Utils.isPublicAuthenticatorConstant(authenticator)) {
                continue;
            }

            // Test canAuthenticate(int)
            assertThrows("Invalid authenticator in canAuthenticate must throw exception: "
                            + authenticator,
                    Exception.class,
                    () -> mBiometricManager.canAuthenticate(authenticator));

            // Test BiometricPrompt
            assertThrows("Invalid authenticator in authenticate must throw exception: "
                            + authenticator,
                    Exception.class,
                    () -> showBiometricPromptWithAuthenticators(authenticator));
        }
    }

    /**
     * When device credential is not enrolled, check the behavior for
     * 1) BiometricManager#canAuthenticate(DEVICE_CREDENTIAL)
     * 2) BiometricPrompt#setAllowedAuthenticators(DEVICE_CREDENTIAL)
     * 3) @deprecated BiometricPrompt#setDeviceCredentialAllowed(true)
     */
    @ApiTest(apis = {
            "android.hardware.biometrics."
                    + "BiometricManager#canAuthenticate",
            "android.hardware.biometrics."
                    + "BiometricPrompt.Builder#setAllowedAuthenticators",
            "android.hardware.biometrics."
                    + "BiometricPrompt.Builder#setDeviceCredentialAllowed",
            "android.hardware.biometrics."
                    + "BiometricPrompt#authenticate"})
    @Test
    public void testWhenCredentialNotEnrolled() throws Exception {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        // First case above
        final int result = mBiometricManager.canAuthenticate(BiometricManager
                .Authenticators.DEVICE_CREDENTIAL);
        assertEquals(BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED, result);

        // Second case above
        BiometricPrompt.AuthenticationCallback callback =
                mock(BiometricPrompt.AuthenticationCallback.class);
        showCredentialOnlyBiometricPrompt(callback, new CancellationSignal(),
                false /* shouldShow */);
        verify(callback).onAuthenticationError(
                eq(BiometricPrompt.BIOMETRIC_ERROR_NO_DEVICE_CREDENTIAL),
                any());

        // Third case above. Since the deprecated API is intended to allow credential in addition
        // to biometrics, we should be receiving BIOMETRIC_ERROR_NO_BIOMETRICS.
        final boolean noSensors = mSensorProperties.isEmpty();
        int expectedError;
        if (noSensors) {
            expectedError = BiometricPrompt.BIOMETRIC_ERROR_NO_DEVICE_CREDENTIAL;
        } else if (hasOnlyConvenienceSensors()) {
            expectedError = BiometricPrompt.BIOMETRIC_ERROR_HW_NOT_PRESENT;
        } else {
            expectedError = BiometricPrompt.BIOMETRIC_ERROR_NO_BIOMETRICS;
        }
        callback = mock(BiometricPrompt.AuthenticationCallback.class);
        showDeviceCredentialAllowedBiometricPrompt(callback, new CancellationSignal(),
                false /* shouldShow */);
        verify(callback).onAuthenticationError(
                eq(expectedError),
                any());
    }

    private boolean hasOnlyConvenienceSensors() {
        for (SensorProperties sensor : mSensorProperties) {
            if (sensor.getSensorStrength() != SensorProperties.STRENGTH_CONVENIENCE) {
                return false;
            }
        }
        return true;
    }

    /**
     * When device credential is enrolled, check the behavior for
     * 1) BiometricManager#canAuthenticate(DEVICE_CREDENTIAL)
     * 2a) Successfully authenticating BiometricPrompt#setAllowedAuthenticators(DEVICE_CREDENTIAL)
     * 2b) Cancelling authentication for the above
     * 3a) @deprecated BiometricPrompt#setDeviceCredentialALlowed(true)
     * 3b) Cancelling authentication for the above
     * 4) Cancelling auth for options 2) and 3)
     */
    @ApiTest(apis = {
            "android.hardware.biometrics."
                    + "BiometricManager#canAuthenticate",
            "android.hardware.biometrics."
                    + "BiometricPrompt.Builder#setAllowedAuthenticators",
            "android.hardware.biometrics."
                    + "BiometricPrompt.Builder#setDeviceCredentialAllowed",
            "android.hardware.biometrics."
                    + "BiometricPrompt.AuthenticationCallback#onAuthenticationSucceeded",
            "android.hardware.biometrics."
                    + "BiometricPrompt#authenticate"})
    @Test
    public void testWhenCredentialEnrolled() throws Exception {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        try (CredentialSession session = new CredentialSession()) {
            session.setCredential();

            // First case above
            final int result = mBiometricManager.canAuthenticate(BiometricManager
                    .Authenticators.DEVICE_CREDENTIAL);
            assertEquals(BiometricManager.BIOMETRIC_SUCCESS, result);

            // 2a above
            BiometricPrompt.AuthenticationCallback callback =
                    mock(BiometricPrompt.AuthenticationCallback.class);
            showCredentialOnlyBiometricPrompt(callback, new CancellationSignal(),
                    true /* shouldShow */);
            successfullyEnterCredential();
            verify(callback).onAuthenticationSucceeded(any());

            // 2b above
            CancellationSignal cancel = new CancellationSignal();
            callback = mock(BiometricPrompt.AuthenticationCallback.class);
            showCredentialOnlyBiometricPrompt(callback, cancel, true /* shouldShow */);
            cancelAuthentication(cancel);
            verify(callback).onAuthenticationError(eq(BiometricPrompt.BIOMETRIC_ERROR_CANCELED),
                    any());

            // 3a above
            callback = mock(BiometricPrompt.AuthenticationCallback.class);
            showDeviceCredentialAllowedBiometricPrompt(callback, new CancellationSignal(),
                    true /* shouldShow */);
            successfullyEnterCredential();
            verify(callback).onAuthenticationSucceeded(any());

            // 3b above
            cancel = new CancellationSignal();
            callback = mock(BiometricPrompt.AuthenticationCallback.class);
            showDeviceCredentialAllowedBiometricPrompt(callback, cancel, true /* shouldShow */);
            cancelAuthentication(cancel);
            verify(callback).onAuthenticationError(eq(BiometricPrompt.BIOMETRIC_ERROR_CANCELED),
                    any());
        }
    }

    @CddTest(requirements = {"7.3.10/C-4-2"})
    @ApiTest(apis = {
            "android.hardware.biometrics."
                    + "BiometricManager#canAuthenticate",
            "android.hardware.biometrics."
                    + "BiometricPrompt.AuthenticationCallback#onAuthenticationError"})
    @Test
    public void testSimpleBiometricAuth_convenience() throws Exception {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        for (SensorProperties props : mSensorProperties) {
            if (props.getSensorStrength() != SensorProperties.STRENGTH_CONVENIENCE) {
                continue;
            }

            Log.d(TAG, "testSimpleBiometricAuth_convenience, sensor: " + props.getSensorId());

            try (BiometricTestSession session =
                         mBiometricManager.createTestSession(props.getSensorId())) {

                // Let's just try to check+auth against WEAK, since CONVENIENCE isn't even
                // exposed to public BiometricPrompt APIs (as intended).
                final int authenticatorStrength = Authenticators.BIOMETRIC_WEAK;
                assertNotEquals("Sensor: " + props.getSensorId()
                                + ", strength: " + props.getSensorStrength(),
                        BiometricManager.BIOMETRIC_SUCCESS,
                        mBiometricManager.canAuthenticate(authenticatorStrength));

                enrollForSensor(session, props.getSensorId());

                assertNotEquals("Sensor: " + props.getSensorId()
                                + ", strength: " + props.getSensorStrength(),
                        BiometricManager.BIOMETRIC_SUCCESS,
                        mBiometricManager.canAuthenticate(authenticatorStrength));

                BiometricPrompt.AuthenticationCallback callback =
                        mock(BiometricPrompt.AuthenticationCallback.class);

                showDefaultBiometricPrompt(props.getSensorId(), callback,
                        new CancellationSignal());

                verify(callback).onAuthenticationError(anyInt(), anyObject());
            }
        }
    }

    /**
     * Tests that the values specified through the public APIs are shown on the BiometricPrompt UI
     * when biometric auth is requested.
     *
     * Upon successful authentication, checks that the result is
     * {@link BiometricPrompt#AUTHENTICATION_RESULT_TYPE_BIOMETRIC}
     *
     * TODO(b/236763921): fix this test and unignore.
     */
    @Ignore
    @CddTest(requirements = {"7.3.10/C-4-2"})
    @ApiTest(apis = {
            "android.hardware.biometrics."
                    + "BiometricManager#canAuthenticate",
            "android.hardware.biometrics."
                    + "BiometricPrompt.Builder#setTitle",
            "android.hardware.biometrics."
                    + "BiometricPrompt.Builder#setSubtitle",
            "android.hardware.biometrics."
                    + "BiometricPrompt.Builder#setDescription",
            "android.hardware.biometrics."
                    + "BiometricPrompt.Builder#setNegativeButton",
            "android.hardware.biometrics."
                    + "BiometricPrompt.AuthenticationCallback#onAuthenticationSucceeded",
            "android.hardware.biometrics."
                    + "BiometricPrompt#authenticate",
            "android.hardware.biometrics."
                    + "BiometricPrompt.AuthenticationResult#getAuthenticationType"})
    @Test
    public void testSimpleBiometricAuth_nonConvenience() throws Exception {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        for (SensorProperties props : mSensorProperties) {
            if (props.getSensorStrength() == SensorProperties.STRENGTH_CONVENIENCE) {
                continue;
            }

            Log.d(TAG, "testSimpleBiometricAuth, sensor: " + props.getSensorId());

            try (BiometricTestSession session =
                         mBiometricManager.createTestSession(props.getSensorId())) {

                final int authenticatorStrength =
                        Utils.testApiStrengthToAuthenticatorStrength(props.getSensorStrength());

                assertEquals("Sensor: " + props.getSensorId()
                                + ", strength: " + props.getSensorStrength(),
                        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED,
                        mBiometricManager.canAuthenticate(authenticatorStrength));

                enrollForSensor(session, props.getSensorId());

                assertEquals("Sensor: " + props.getSensorId()
                                + ", strength: " + props.getSensorStrength(),
                        BiometricManager.BIOMETRIC_SUCCESS,
                        mBiometricManager.canAuthenticate(authenticatorStrength));

                final Random random = new Random();
                final String randomTitle = String.valueOf(random.nextInt(10000));
                final String randomSubtitle = String.valueOf(random.nextInt(10000));
                final String randomDescription = String.valueOf(random.nextInt(10000));
                final String randomNegativeButtonText = String.valueOf(random.nextInt(10000));

                BiometricPrompt.AuthenticationCallback callback =
                        mock(BiometricPrompt.AuthenticationCallback.class);

                showDefaultBiometricPromptWithContents(props.getSensorId(), 0 /* userId */,
                        true /* requireConfirmation */, callback, randomTitle, randomSubtitle,
                        randomDescription, null /* contentView */, randomNegativeButtonText);

                final UiObject2 actualTitle = findView(TITLE_VIEW);
                final UiObject2 actualSubtitle = findView(SUBTITLE_VIEW);
                final UiObject2 actualDescription = findView(DESCRIPTION_VIEW);
                final UiObject2 actualNegativeButton = findView(BUTTON_ID_NEGATIVE);
                assertEquals(randomTitle, actualTitle.getText());
                assertEquals(randomSubtitle, actualSubtitle.getText());
                assertEquals(randomDescription, actualDescription.getText());
                assertEquals(randomNegativeButtonText, actualNegativeButton.getText());

                // Finish auth
                successfullyAuthenticate(session, 0 /* userId */, callback);
            }
        }
    }

    /**
     * Tests that the values specified through the public APIs are shown on the BiometricPrompt UI
     * when credential auth is requested.
     *
     * Upon successful authentication, checks that the result is
     * {@link BiometricPrompt#AUTHENTICATION_RESULT_TYPE_BIOMETRIC}
     */
    @ApiTest(apis = {
            "android.hardware.biometrics."
                    + "BiometricPrompt.Builder#setTitle",
            "android.hardware.biometrics."
                    + "BiometricPrompt.Builder#setSubtitle",
            "android.hardware.biometrics."
                    + "BiometricPrompt.Builder#setDescription",
            "android.hardware.biometrics."
                    + "BiometricPrompt#authenticate",
            "android.hardware.biometrics."
                    + "BiometricPrompt.AuthenticationResult#getAuthenticationType"})
    @Test
    public void testSimpleCredentialAuth() throws Exception {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        try (CredentialSession session = new CredentialSession()) {
            session.setCredential();

            final Random random = new Random();
            final String randomTitle = String.valueOf(random.nextInt(10000));
            final String randomSubtitle = String.valueOf(random.nextInt(10000));
            final String randomDescription = String.valueOf(random.nextInt(10000));

            CountDownLatch latch = new CountDownLatch(1);
            BiometricPrompt.AuthenticationCallback callback =
                    new BiometricPrompt.AuthenticationCallback() {
                        @Override
                        public void onAuthenticationSucceeded(
                                BiometricPrompt.AuthenticationResult result) {
                            assertEquals("Must be TYPE_CREDENTIAL",
                                    BiometricPrompt.AUTHENTICATION_RESULT_TYPE_DEVICE_CREDENTIAL,
                                    result.getAuthenticationType());
                            latch.countDown();
                        }
                    };
            showCredentialOnlyBiometricPromptWithContents(callback, new CancellationSignal(),
                    true /* shouldShow */, randomTitle, randomSubtitle, randomDescription,
                    null /* contentView */);

            final UiObject2 actualTitle = findView(TITLE_VIEW);
            final UiObject2 actualSubtitle = findView(SUBTITLE_VIEW);
            final UiObject2 actualDescription = findView(DESCRIPTION_VIEW);
            assertEquals(randomTitle, actualTitle.getText());
            assertEquals(randomSubtitle, actualSubtitle.getText());
            assertEquals(randomDescription, actualDescription.getText());

            // Finish auth
            successfullyEnterCredential();
            latch.await(3, TimeUnit.SECONDS);
        }
    }

    /**
     * Tests that cancelling auth succeeds, and that ERROR_CANCELED is received.
     */
    @ApiTest(apis = {
            "android.hardware.biometrics."
                    + "BiometricPrompt.AuthenticationCallback#onAuthenticationError"})
    @Test
    public void testBiometricCancellation() throws Exception {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        for (SensorProperties props : mSensorProperties) {
            if (props.getSensorStrength() == SensorProperties.STRENGTH_CONVENIENCE) {
                continue;
            }

            try (BiometricTestSession session =
                         mBiometricManager.createTestSession(props.getSensorId())) {
                enrollForSensor(session, props.getSensorId());

                BiometricPrompt.AuthenticationCallback callback =
                        mock(BiometricPrompt.AuthenticationCallback.class);
                CancellationSignal cancellationSignal = new CancellationSignal();

                showDefaultBiometricPrompt(props.getSensorId(), callback, cancellationSignal);

                cancelAuthentication(cancellationSignal);
                verify(callback).onAuthenticationError(eq(BiometricPrompt.BIOMETRIC_ERROR_CANCELED),
                        any());
                verifyNoMoreInteractions(callback);
            }
        }
    }

    /**
     * Tests that {@link BiometricManager#getLastAuthenticationTime(int)} result changes
     * appropriately for DEVICE_CREDENTIAL after a PIN unlock.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_LAST_AUTHENTICATION_TIME)
    public void testGetLastAuthenticationTime_unlockWithCorrectDeviceCredential() throws Exception {
        try (CredentialSession credentialSession = new CredentialSession()) {
            credentialSession.setCredential();

            final long startTime = SystemClock.elapsedRealtime();

            credentialSession.verifyCredential();

            // There's a race between the auth token being sent to keystore2 and the
            // getLastAuthenticationTime() call, so retry if we don't get a valid time.
            long lastAuthTime = BiometricManager.BIOMETRIC_NO_AUTHENTICATION;
            for (int i = 0; i < 10; i++) {
                lastAuthTime = mBiometricManager.getLastAuthenticationTime(
                        DEVICE_CREDENTIAL);
                if (lastAuthTime != BiometricManager.BIOMETRIC_NO_AUTHENTICATION) {
                    break;
                }

                Thread.sleep(100);
            }

            assertThat(lastAuthTime).isGreaterThan(startTime);
        }
    }

    /**
     * Tests that {@link BiometricManager#getLastAuthenticationTime(int)} result does not change
     * when an incorrect PIN is entered.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_LAST_AUTHENTICATION_TIME)
    public void testGetLastAuthenticationTime_unlockWithIncorrectDeviceCredential()
            throws Exception {
        try (CredentialSession credentialSession = new CredentialSession()) {
            credentialSession.setCredential();

            final long initialLastAuthTime = mBiometricManager.getLastAuthenticationTime(
                    DEVICE_CREDENTIAL);

            credentialSession.verifyIncorrectCredential();

            long lastAuthTime = mBiometricManager.getLastAuthenticationTime(
                    DEVICE_CREDENTIAL);

            assertThat(lastAuthTime).isEqualTo(initialLastAuthTime);
        }
    }

    /**
     * Tests that {@link BiometricManager#getLastAuthenticationTime(int)} result returns
     * {@link BiometricManager#BIOMETRIC_NO_AUTHENTICATION} if there is no password/PIN set.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_LAST_AUTHENTICATION_TIME)
    public void testGetLastAuthenticationTime_noCredential() throws Exception {
        final long lastAuthTime = mBiometricManager.getLastAuthenticationTime(
                DEVICE_CREDENTIAL);

        assertThat(lastAuthTime).isEqualTo(BiometricManager.BIOMETRIC_NO_AUTHENTICATION);
    }
}
