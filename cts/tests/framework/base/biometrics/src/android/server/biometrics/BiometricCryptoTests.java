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

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import android.hardware.biometrics.BiometricPrompt;
import android.hardware.biometrics.BiometricTestSession;
import android.hardware.biometrics.SensorProperties;
import android.platform.test.annotations.Presubmit;
import android.security.keystore.KeyProperties;
import android.util.Log;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Test;

import java.security.InvalidAlgorithmParameterException;

/**
 * Tests for cryptographic/keystore related functionality.
 */
@Presubmit
public class BiometricCryptoTests extends BiometricTestBase {
    private static final String TAG = "BiometricTests/Crypto";

    @ApiTest(apis = {
            "android.security.keystore."
                    + "KeyGenParameterSpec.Builder#setUserAuthenticationRequired",
            "android.security.keystore."
                    + "KeyGenParameterSpec.Builder#setUserAuthenticationParameters"})
    @Test
    public void testGenerateKeyWithoutDeviceCredential_throwsException() {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        assertThrows("Key shouldn't be generatable before device credentials are enrolled",
                Exception.class,
                () -> Utils.generateBiometricBoundKey("keyBeforeCredentialEnrolled",
                        false /* useStrongBox */));
    }

    @ApiTest(apis = {
            "android.security.keystore."
                + "KeyGenParameterSpec.Builder#setUserAuthenticationRequired",
            "android.security.keystore."
                + "KeyGenParameterSpec.Builder#setUserAuthenticationParameters"})
    @Test
    public void testGenerateKeyWithoutBiometricEnrolled_throwsInvalidAlgorithmParameterException()
            throws Exception {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        try (CredentialSession session = new CredentialSession()){
            session.setCredential();
            assertThrows("Key shouldn't be generatable before biometrics are enrolled",
                    InvalidAlgorithmParameterException.class,
                    () -> Utils.generateBiometricBoundKey("keyBeforeBiometricEnrolled",
                            false /* useStrongBox */));
        }
    }

    @ApiTest(apis = {
            "android.security.keystore."
                    + "KeyGenParameterSpec.Builder#setUserAuthenticationRequired",
            "android.security.keystore."
                    + "KeyGenParameterSpec.Builder#setUserAuthenticationParameters",
            "android.security.keystore."
                    + "KeyGenParameterSpec.Builder#setUserAuthenticationValidityDurationSeconds"})
    @Test
    public void testGenerateKeyWhenCredentialAndBiometricEnrolled() throws Exception {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        try (CredentialSession credentialSession = new CredentialSession()) {
            credentialSession.setCredential();

            // 1) Test biometric or credential time-based key. These should be generatable
            // regardless of biometric strength and enrollment, since credentials are enrolled.
            int authType = KeyProperties.AUTH_BIOMETRIC_STRONG
                    | KeyProperties.AUTH_DEVICE_CREDENTIAL;
            Utils.createTimeBoundSecretKey_deprecated("credential_tb_d", false /* useStrongBox */);
            Utils.createTimeBoundSecretKey("credential_tb", authType, false /* useStrongBox */);
            if (mHasStrongBox) {
                Utils.createTimeBoundSecretKey_deprecated("credential_tb_d_sb",
                        true /* useStrongBox */);
                Utils.createTimeBoundSecretKey("credential_tb_sb", authType,
                        true /* useStrongBox */);
            }

            for (SensorProperties prop : mSensorProperties) {
                final String keyPrefix = "key" + prop.getSensorId();
                Log.d(TAG, "Testing sensor: " + prop + ", key name: " + keyPrefix);

                try (BiometricTestSession session =
                             mBiometricManager.createTestSession(prop.getSensorId())) {
                    waitForAllUnenrolled();
                    enrollForSensor(session, prop.getSensorId());

                    if (prop.getSensorStrength() == SensorProperties.STRENGTH_STRONG) {
                        // Test biometric-bound key
                        Utils.generateBiometricBoundKey(keyPrefix, false /* useStrongBox */);
                        if (mHasStrongBox) {
                            Utils.generateBiometricBoundKey(keyPrefix + "sb",
                                    true /* useStrongBox */);
                        }
                        // We can test initializing the key, which in this case is a Cipher.
                        // However, authenticating it and using it is not testable, since that
                        // requires a real authentication from the TEE or equivalent.
                        BiometricPrompt.CryptoObject crypto =
                                Utils.initializeCryptoObject(keyPrefix);
                    } else {
                        // 1) Test biometric auth-per-use keys
                        assertThrows("Biometric auth-per-use key shouldn't be generatable with"
                                        + " non-strong biometrics",
                                InvalidAlgorithmParameterException.class,
                                () -> Utils.generateBiometricBoundKey(keyPrefix,
                                        false /* useStrongBox */));
                        if (mHasStrongBox) {
                            assertThrows("Biometric auth-per-use strongbox-backed key shouldn't"
                                            + " be generatable with non-strong biometrics",
                                    InvalidAlgorithmParameterException.class,
                                    () -> Utils.generateBiometricBoundKey(keyPrefix,
                                            true /* useStrongBox */));
                        }

                        // 2) Test biometric time-based keys
                        assertThrows("Biometric time-based key shouldn't be generatable with"
                                        + " non-strong biometrics",
                                Exception.class,
                                () -> Utils.createTimeBoundSecretKey(keyPrefix + "tb",
                                        KeyProperties.AUTH_BIOMETRIC_STRONG,
                                        false /* useStrongBox */));
                        if (mHasStrongBox) {
                            assertThrows("Biometric time-based strongbox-backed key shouldn't be"
                                            + " generatable with non-strong biometrics",
                                    Exception.class,
                                    () -> Utils.createTimeBoundSecretKey(keyPrefix + "tb",
                                            KeyProperties.AUTH_BIOMETRIC_STRONG,
                                            true /* useStrongBox */));
                        }
                    }
                }
            }
        }
    }
}
