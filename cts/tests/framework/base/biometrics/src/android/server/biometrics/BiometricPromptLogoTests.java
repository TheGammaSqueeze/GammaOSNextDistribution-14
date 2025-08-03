/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.mock;

import android.Manifest;
import android.graphics.Bitmap;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.hardware.biometrics.BiometricTestSession;
import android.hardware.biometrics.Flags;
import android.hardware.biometrics.SensorProperties;
import android.os.CancellationSignal;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.util.Log;

import androidx.test.uiautomator.UiObject2;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Test;

/**
 * Basic test cases for logo on biometric prompt.
 */
@Presubmit
public class BiometricPromptLogoTests extends BiometricTestBase {
    private static final String TAG = "BiometricTests/Logo";

    private final int mLogoRes = android.R.drawable.btn_plus;
    private final Bitmap mLogoBitmap =
            Bitmap.createBitmap(400, 400, Bitmap.Config.RGB_565);
    private final String mLogoDescription = "test app";

    /**
     * Test without SET_BIOMETRIC_DIALOG_LOGO permission,
     * {@link BiometricPrompt.Builder#setLogoRes(int)} should throw security exception.
     */
    @ApiTest(apis = {
            "android.hardware.biometrics."
                    + "BiometricManager#canAuthenticate",
            "android.hardware.biometrics."
                    + "BiometricPrompt#authenticate",
            "android.hardware.biometrics."
                    + "PromptVerticalListContentView.Builder#setLogoDescription",
            "android.hardware.biometrics."
                    + "PromptVerticalListContentView.Builder#setLogoRes"})
    @RequiresFlagsEnabled(Flags.FLAG_CUSTOM_BIOMETRIC_PROMPT)
    @Test
    public void test_setLogoRes_withoutPermissionFailed() throws Exception {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        test_setLogo(true /*testLogoRes*/, false /*withPermission*/);
    }

    /**
     * Tests that the logo res value specified through the public API
     * {@link BiometricPrompt.Builder#setLogoRes(int)} are shown on the
     * BiometricPrompt UI when biometric auth is requested.
     */
    @ApiTest(apis = {
            "android.hardware.biometrics."
                    + "BiometricManager#canAuthenticate",
            "android.hardware.biometrics."
                    + "BiometricPrompt#authenticate",
            "android.hardware.biometrics."
                    + "PromptVerticalListContentView.Builder#setLogoDescription",
            "android.hardware.biometrics."
                    + "PromptVerticalListContentView.Builder#setLogoRes"})
    @RequiresFlagsEnabled(Flags.FLAG_CUSTOM_BIOMETRIC_PROMPT)
    @Test
    public void test_setLogoRes_withPermissionSuccessful() throws Exception {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        test_setLogo(true /*testLogoRes*/, true /*withPermission*/);
    }

    /**
     * Test without SET_BIOMETRIC_DIALOG_LOGO permission,
     * {@link BiometricPrompt.Builder#setLogoBitmap(Bitmap)} should throw security exception.
     */
    @ApiTest(apis = {
            "android.hardware.biometrics."
                    + "BiometricManager#canAuthenticate",
            "android.hardware.biometrics."
                    + "BiometricPrompt#authenticate",
            "android.hardware.biometrics."
                    + "PromptVerticalListContentView.Builder#setLogoDescription",
            "android.hardware.biometrics."
                    + "PromptVerticalListContentView.Builder#setLogoBitmap"})
    @RequiresFlagsEnabled(Flags.FLAG_CUSTOM_BIOMETRIC_PROMPT)
    @Test
    public void test_setLogoBitmap_withoutPermissionFailed() throws Exception {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        test_setLogo(false /*testLogoRes*/, false /*withPermission*/);
    }

    /**
     * Tests that the logo res value specified through the public API
     * {@link BiometricPrompt.Builder#setLogoBitmap(Bitmap)} are shown on the
     * BiometricPrompt UI when biometric auth is requested.
     */
    @ApiTest(apis = {
            "android.hardware.biometrics."
                    + "BiometricManager#canAuthenticate",
            "android.hardware.biometrics."
                    + "BiometricPrompt#authenticate",
            "android.hardware.biometrics."
                    + "PromptVerticalListContentView.Builder#setLogoDescription",
            "android.hardware.biometrics."
                    + "PromptVerticalListContentView.Builder#setLogoBitmap"})
    @RequiresFlagsEnabled(Flags.FLAG_CUSTOM_BIOMETRIC_PROMPT)
    @Test
    public void test_setLogoBitmap_withPermissionSuccessful() throws Exception {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        test_setLogo(false /*testLogoRes*/, true /*withPermission*/);
    }

    /**
     * Test when setting both {@link BiometricPrompt.Builder#setLogoBitmap(Bitmap)} and
     * {@link BiometricPrompt.Builder#setLogoRes(int)}, an illegal state exception should be thrown.
     */
    @ApiTest(apis = {
            "android.hardware.biometrics."
                    + "BiometricManager#canAuthenticate",
            "android.hardware.biometrics."
                    + "BiometricPrompt#authenticate",
            "android.hardware.biometrics."
                    + "PromptVerticalListContentView.Builder#setLogoDescription",
            "android.hardware.biometrics."
                    + "PromptVerticalListContentView.Builder#setLogoRes",
            "android.hardware.biometrics."
                    + "PromptVerticalListContentView.Builder#setLogoBitmap"})
    @RequiresFlagsEnabled(Flags.FLAG_CUSTOM_BIOMETRIC_PROMPT)
    @Test
    public void test_setLogoResAndBitmap_throwsException() throws Exception {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        for (SensorProperties props : mSensorProperties) {
            if (props.getSensorStrength() == SensorProperties.STRENGTH_CONVENIENCE) {
                continue;
            }

            Log.d(TAG,
                    "test_setLogoResAndBitmap_throwsException, sensor: " + props.getSensorId());

            try (BiometricTestSession session =
                         mBiometricManager.createTestSession(props.getSensorId())) {

                final int authenticatorStrength =
                        Utils.testApiStrengthToAuthenticatorStrength(props.getSensorStrength());

                assertWithMessage("Sensor: " + props.getSensorId()
                        + ", strength: " + props.getSensorStrength()).that(
                        mBiometricManager.canAuthenticate(authenticatorStrength)).isEqualTo(
                        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED);

                enrollForSensor(session, props.getSensorId());

                assertWithMessage("Sensor: " + props.getSensorId()
                        + ", strength: " + props.getSensorStrength()).that(
                        mBiometricManager.canAuthenticate(authenticatorStrength)).isEqualTo(
                        BiometricManager.BIOMETRIC_SUCCESS);

                BiometricPrompt.AuthenticationCallback callback =
                        mock(BiometricPrompt.AuthenticationCallback.class);
                CancellationSignal cancellationSignal = new CancellationSignal();
                IllegalStateException e = assertThrows(IllegalStateException.class,
                        () -> showDefaultBiometricPromptWithLogo(props.getSensorId(), callback,
                                cancellationSignal, mLogoRes, mLogoBitmap, mLogoDescription));
                assertThat(e).hasMessageThat().isEqualTo(
                        "Exclusively one of logo resource or logo bitmap can be set");
            }
        }
    }

    private void test_setLogo(boolean testLogoRes, boolean withPermission) throws Exception {
        if (!withPermission) {
            mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
            mInstrumentation.getUiAutomation().adoptShellPermissionIdentity(
                    android.Manifest.permission.WAKE_LOCK, Manifest.permission.TEST_BIOMETRIC,
                    android.Manifest.permission.USE_BIOMETRIC);

        }
        for (SensorProperties props : mSensorProperties) {
            if (props.getSensorStrength() == SensorProperties.STRENGTH_CONVENIENCE) {
                continue;
            }

            Log.d(TAG, "test_setLogo, sensor: " + props.getSensorId());

            try (BiometricTestSession session =
                         mBiometricManager.createTestSession(props.getSensorId())) {

                final int authenticatorStrength =
                        Utils.testApiStrengthToAuthenticatorStrength(props.getSensorStrength());

                assertWithMessage("Sensor: " + props.getSensorId()
                        + ", strength: " + props.getSensorStrength()).that(
                        mBiometricManager.canAuthenticate(authenticatorStrength)).isEqualTo(
                        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED);

                enrollForSensor(session, props.getSensorId());

                assertWithMessage("Sensor: " + props.getSensorId()
                        + ", strength: " + props.getSensorStrength()).that(
                        mBiometricManager.canAuthenticate(authenticatorStrength)).isEqualTo(
                        BiometricManager.BIOMETRIC_SUCCESS);

                BiometricPrompt.AuthenticationCallback callback =
                        mock(BiometricPrompt.AuthenticationCallback.class);
                if (withPermission) {
                    showBiometricPromptWithLogo(testLogoRes, props.getSensorId(), callback);
                    final UiObject2 actualLogo = findView(LOGO_VIEW);
                    final UiObject2 actualLogoDescription = findView(LOGO_DESCRIPTION_VIEW);

                    assertThat(actualLogo.getVisibleBounds()).isNotNull();
                    assertThat(actualLogoDescription.getText()).isEqualTo(mLogoDescription);

                    // Finish auth
                    successfullyAuthenticate(session, 0 /* userId */, callback);
                } else {
                    SecurityException e = assertThrows(SecurityException.class,
                            () -> showBiometricPromptWithLogo(testLogoRes, props.getSensorId(),
                                    callback));
                    assertThat(e).hasMessageThat().contains(
                            "android.permission.SET_BIOMETRIC_DIALOG_LOGO");
                }
            }
        }
    }

    private void showBiometricPromptWithLogo(boolean testLogoRes, int sensorId,
            BiometricPrompt.AuthenticationCallback callback) throws Exception {

        CancellationSignal cancellationSignal = new CancellationSignal();
        if (testLogoRes) {
            showDefaultBiometricPromptWithLogo(sensorId, callback, cancellationSignal, mLogoRes,
                    null /*logoBitmap*/, mLogoDescription);
        } else {
            showDefaultBiometricPromptWithLogo(sensorId, callback, cancellationSignal,
                    -1 /*logoRes*/, mLogoBitmap, mLogoDescription);
        }
    }
}
