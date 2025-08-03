/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.hardware.biometrics.cts;

import static android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_CONVENIENCE;
import static android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG;
import static android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_WEAK;
import static android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.Flags;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.text.TextUtils;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Basic test cases for BiometricManager. See the manual biometric tests in CtsVerifier for a more
 * comprehensive test suite.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
@Presubmit
public class BiometricManagerTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();
    private Context mContext;
    private BiometricManager mBiometricManager;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mBiometricManager = mContext.getSystemService(BiometricManager.class);
    }

    @Test
    public void test_canAuthenticate() {

        assertNotEquals("Device should not have any biometrics enrolled",
                mBiometricManager.canAuthenticate(), BiometricManager.BIOMETRIC_SUCCESS);

        assertNotEquals("Device should not have any biometrics enrolled",
                mBiometricManager.canAuthenticate(DEVICE_CREDENTIAL | BIOMETRIC_WEAK),
                BiometricManager.BIOMETRIC_SUCCESS);
    }

    @Test
    public void test_getButtonLabel_isDifferentForDistinctAuthTypes() {
        // Ensure labels for biometrics and credential are different (if non-empty).
        final CharSequence biometricLabel = mBiometricManager.getStrings(BIOMETRIC_WEAK)
                .getButtonLabel();
        final CharSequence credentialLabel = mBiometricManager.getStrings(DEVICE_CREDENTIAL)
                .getButtonLabel();
        if (!TextUtils.isEmpty(biometricLabel) || !TextUtils.isEmpty(credentialLabel)) {
            assertFalse("Biometric and credential button labels should not match",
                    TextUtils.equals(biometricLabel, credentialLabel));
        }
    }

    @Test
    public void test_getButtonLabel_isNonEmptyIfPresentForSubAuthType() {
        // Ensure label for biometrics|credential is non-empty if one for biometrics or credential
        // (or both) is non-empty.
        final CharSequence biometricOrCredentialLabel = mBiometricManager.getStrings(
                BIOMETRIC_WEAK | DEVICE_CREDENTIAL).getButtonLabel();
        final CharSequence biometricLabel =
                mBiometricManager.getStrings(BIOMETRIC_WEAK).getButtonLabel();
        final CharSequence credentialLabel = mBiometricManager.getStrings(
                DEVICE_CREDENTIAL).getButtonLabel();
        final boolean isLabelPresentForSubAuthType =
                !TextUtils.isEmpty(biometricLabel) || !TextUtils.isEmpty(credentialLabel);
        boolean isBiometricOrCredentialLabel;
        if (hasOnlyConvenienceSensors()) {
            isBiometricOrCredentialLabel = TextUtils.isEmpty(credentialLabel);
        } else {
            isBiometricOrCredentialLabel = TextUtils.isEmpty(biometricOrCredentialLabel);
        }
        assertFalse("Label should not be empty if one for an authenticator sub-type is non-empty",
                isBiometricOrCredentialLabel && isLabelPresentForSubAuthType);
    }

    @Test
    public void test_getPromptMessage_isDifferentForDistinctAuthTypes() {
        // Ensure messages for biometrics and credential are different (if non-empty).
        final CharSequence biometricMessage = mBiometricManager.getStrings(BIOMETRIC_WEAK)
                .getPromptMessage();
        final CharSequence credentialMessage = mBiometricManager.getStrings(DEVICE_CREDENTIAL)
                .getPromptMessage();
        if (!TextUtils.isEmpty(biometricMessage) || !TextUtils.isEmpty(credentialMessage)) {
            assertFalse("Biometric and credential prompt messages should not match",
                    TextUtils.equals(biometricMessage, credentialMessage));
        }
    }

    @Test
    public void test_getPromptMessage_isDifferentForBiometricsIfCredentialAllowed() {
        // Ensure message for biometrics and biometrics|credential are different (if non-empty).
        final CharSequence biometricMessage = mBiometricManager.getStrings(BIOMETRIC_WEAK)
                .getPromptMessage();
        final CharSequence bioOrCredMessage = mBiometricManager.getStrings(
                BIOMETRIC_WEAK | DEVICE_CREDENTIAL).getPromptMessage();
        if (!TextUtils.isEmpty(biometricMessage) || !TextUtils.isEmpty(bioOrCredMessage)) {
            assertFalse("Biometric and biometric|credential prompt messages should not match",
                    TextUtils.equals(biometricMessage, bioOrCredMessage));
        }
    }

    @Test
    public void test_getSettingName_forBiometrics() {
        final PackageManager pm = mContext.getPackageManager();
        final boolean hasFingerprint = pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT);
        final boolean hasIris = pm.hasSystemFeature(PackageManager.FEATURE_IRIS);
        final boolean hasFace = pm.hasSystemFeature(PackageManager.FEATURE_FACE);
        assumeTrue("Test requires biometric hardware", hasFingerprint || hasIris || hasFace);
        assumeFalse("Test requires biometric hardware above weak level",
                hasOnlyConvenienceSensors());

        // Ensure biometric setting name is non-empty if device supports biometrics.
        assertFalse("Name should be non-empty if device supports biometric authentication",
                TextUtils.isEmpty(mBiometricManager.getStrings(BIOMETRIC_WEAK).getSettingName()));
        assertFalse("Name should be non-empty if device supports biometric authentication",
                TextUtils.isEmpty(mBiometricManager.getStrings(BIOMETRIC_WEAK | DEVICE_CREDENTIAL)
                        .getSettingName()));
    }

    private boolean hasOnlyConvenienceSensors() {
        return mBiometricManager.canAuthenticate(BIOMETRIC_WEAK)
                == BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE;
    }

    @Test
    public void test_getSettingName_forCredential() {
        final KeyguardManager km = mContext.getSystemService(KeyguardManager.class);
        assumeTrue("Test requires KeyguardManager", km != null);

        // Ensure credential setting name is non-empty if device supports PIN/pattern/password.
        assertFalse("Name should be non-empty if device supports PIN/pattern/password",
                TextUtils.isEmpty(mBiometricManager.getStrings(DEVICE_CREDENTIAL)
                        .getSettingName()));
        assertFalse("Name should be non-empty if device supports PIN/pattern/password",
                TextUtils.isEmpty(mBiometricManager.getStrings(BIOMETRIC_WEAK | DEVICE_CREDENTIAL)
                        .getSettingName()));
    }

    @Test
    @ApiTest(apis = {"android.hardware.biometrics.BiometricManager#getLastAuthenticationTime"})
    @RequiresFlagsDisabled(Flags.FLAG_LAST_AUTHENTICATION_TIME)
    public void testGetLastAuthenticationTime_flagOff_throwsUnsupportedOperation() {
        assertThrows(
                "Should throw UnsupportedOperationException when flag is disabled",
                UnsupportedOperationException.class,
                () -> mBiometricManager.getLastAuthenticationTime(BIOMETRIC_STRONG));
    }

    @Test
    @ApiTest(apis = {"android.hardware.biometrics.BiometricManager#getLastAuthenticationTime"})
    @RequiresFlagsEnabled(Flags.FLAG_LAST_AUTHENTICATION_TIME)
    public void testGetLastAuthenticationTime_allowsStrongAuthenticator() {
        assertEquals("BIOMETRIC_STRONG should have no auth time",
                BiometricManager.BIOMETRIC_NO_AUTHENTICATION,
                mBiometricManager.getLastAuthenticationTime(BIOMETRIC_STRONG));
    }

    @Test
    @ApiTest(apis = {"android.hardware.biometrics.BiometricManager#getLastAuthenticationTime"})
    @RequiresFlagsEnabled(Flags.FLAG_LAST_AUTHENTICATION_TIME)
    public void testGetLastAuthenticationTime_allowsDeviceCredentialAuthenticator() {
        assertEquals("DEVICE_CREDENTIAL should have no auth time",
                BiometricManager.BIOMETRIC_NO_AUTHENTICATION,
                mBiometricManager.getLastAuthenticationTime(DEVICE_CREDENTIAL));
    }

    @Test
    @ApiTest(apis = {"android.hardware.biometrics.BiometricManager#getLastAuthenticationTime"})
    @RequiresFlagsEnabled(Flags.FLAG_LAST_AUTHENTICATION_TIME)
    public void testGetLastAuthenticationTime_allowsDeviceCredentialAndStrongAuthenticator() {
        assertEquals("DEVICE_CREDENTIAL and BIOMETRIC_STRONG should have no auth time",
                BiometricManager.BIOMETRIC_NO_AUTHENTICATION,
                mBiometricManager.getLastAuthenticationTime(DEVICE_CREDENTIAL | BIOMETRIC_STRONG));
    }

    @Test
    @ApiTest(apis = {"android.hardware.biometrics.BiometricManager#getLastAuthenticationTime"})
    public void testGetLastAuthenticationTime_throwsNoAuthenticator() {
        assertThrows("0 should not be allowed",
                IllegalArgumentException.class,
                () -> mBiometricManager.getLastAuthenticationTime(0));
    }

    @Test
    @ApiTest(apis = {"android.hardware.biometrics.BiometricManager#getLastAuthenticationTime"})
    public void testGetLastAuthenticationTime_throwsBogusAuthenticator() {
        assertThrows("42 should not be allowed",
                IllegalArgumentException.class,
                () -> mBiometricManager.getLastAuthenticationTime(42));
    }

    @Test
    @ApiTest(apis = {"android.hardware.biometrics.BiometricManager#getLastAuthenticationTime"})
    public void testGetLastAuthenticationTime_throwsWeakAuthenticator() {
        assertThrows("BIOMETRIC_WEAK should not be allowed",
                IllegalArgumentException.class,
                () -> mBiometricManager.getLastAuthenticationTime(BIOMETRIC_WEAK));
    }

    @Test
    @ApiTest(apis = {"android.hardware.biometrics.BiometricManager#getLastAuthenticationTime"})
    public void testGetLastAuthenticationTime_throwsConvenienceAuthenticator() {
        assertThrows("BIOMETRIC_CONVENIENCE should not be allowed",
                IllegalArgumentException.class,
                () -> mBiometricManager.getLastAuthenticationTime(BIOMETRIC_CONVENIENCE));
    }
}
