/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.cts.verifier;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.CddTest;
import com.android.interactive.annotations.Interactive;
import com.android.interactive.annotations.SupportMultiDisplayMode;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public final class SecurityTest extends CtsVerifierTest {

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    public void BiometricTest() throws Exception {
        // TODO: This has 15 sub-tests with no common setup - we should launch them directly
        // - if we keep it as one test we'll need to extend the timeout as it can't be done in 10
        // minutes
        requireFeatures("android.software.secure_lock_screen");
        excludeFeatures(
                "android.hardware.type.television",
                "android.software.leanback",
                "android.hardware.type.watch",
                "android.hardware.type.automotive");

        runTest(".biometrics.BiometricTestList");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    @CddTest(requirements = "9.11.3/C-0-2")
    public void IdentityCredentialAuthenticationTest() throws Exception {
        requireFeatures("android.software.secure_lock_screen");
        excludeFeatures(
                "android.hardware.type.television",
                "android.software.leanback",
                "android.hardware.type.watch");

        runTest(".security.IdentityCredentialAuthentication");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    @CddTest(requirements = "9.11.3/C-0-2")
    public void IdentityCredentialAuthenticationMultiDocumentTest() throws Exception {
        requireFeatures("android.software.secure_lock_screen");
        excludeFeatures(
                "android.hardware.type.television",
                "android.software.leanback",
                "android.hardware.type.watch");

        runTest(".security.IdentityCredentialAuthenticationMultiDocument");
    }

    @Interactive
    @Test
    // SingleDisplayMode
    @CddTest(requirements = "9.11.1/C-4-1")
    public void FingerprintBoundKeysTest() throws Exception {
        requireFeatures("android.hardware.fingerprint", "android.software.secure_lock_screen");
        excludeFeatures(
                "android.hardware.type.television",
                "android.software.leanback",
                "android.hardware.type.watch");

        runTest(".security.FingerprintBoundKeysTest");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    @CddTest(requirements = {"9.10/C-3-1", "9.10/C-3-2", "9.10/C-3-3"})
    public void ProtectedConfirmationTest() throws Exception {
        runTest(".security.ProtectedConfirmationTest");
    }

    @Interactive
    @Test
    // SingleDisplayMode
    @CddTest(requirements = "9.11/C-1-3")
    public void ScreenLockBoundKeysTest() throws Exception {
        requireFeatures("android.software.device_admin", "android.software.secure_lock_screen");
        excludeFeatures("android.software.lockscreen_disabled");

        runTest(".security.ScreenLockBoundKeysTest");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    @ApiTest(
            apis = {
                "android.app.KeyguardManager#isDeviceLocked",
                "android.hardware.biometrics.BiometricManager#canAuthenticate"
            })
    public void UnlockedDeviceRequiredTest() throws Exception {
        requireFeatures("android.software.device_admin", "android.software.secure_lock_screen");
        excludeFeatures("android.software.lockscreen_disabled");

        runTest(".security.UnlockedDeviceRequiredTest");
    }

    @Interactive
    @Test
    // SingleDisplayMode
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#ACTION_SET_NEW_PASSWORD")
    public void LockConfirmBypassTest() throws Exception {
        requireFeatures("android.software.device_admin", "android.software.secure_lock_screen");
        excludeFeatures("android.software.lockscreen_disabled");

        runTest(".security.LockConfirmBypassTest");
    }

    @Interactive
    @Test
    // SingleDisplayMode
    @ApiTest(
            apis = {
                "android.app.admin.DevicePolicyManager#ACTION_SET_NEW_PASSWORD",
                "android.app.admin.DevicePolicyManager#EXTRA_PASSWORD_COMPLEXITY",
                "android.app.admin.DevicePolicyManager#PASSWORD_COMPLEXITY_HIGH",
                "android.app.admin.DevicePolicyManager#PASSWORD_COMPLEXITY_MEDIUM",
                "android.app.admin.DevicePolicyManager#PASSWORD_COMPLEXITY_LOW",
                "android.app.admin.DevicePolicyManager#PASSWORD_COMPLEXITY_NONE"
            })
    public void SetNewPasswordComplexityTest() throws Exception {
        requireFeatures("android.software.secure_lock_screen");
        excludeFeatures("android.hardware.type.automotive:android.software.lockscreen_disabled");

        runTest(".security.SetNewPasswordComplexityTest");
    }

    @Interactive
    @Test
    // SingleDisplayMode
    @CddTest(requirements = "2.2.5/9.1/H-0-1")
    @ApiTest(apis = "android.content.pm.PackageManager#FEATURE_SECURITY_MODEL_COMPATIBLE")
    public void SecurityModeFeatureVerifierTest() throws Exception {
        excludeFeatures(
                "android.hardware.type.automotive",
                "android.hardware.type.television",
                "android.hardware.type.watch",
                "android.hardware.security.model.compatible");

        runTest(".security.SecurityModeFeatureVerifierActivity");
    }

    @Interactive
    @Test
    // SingleDisplayMode
    @ApiTest(
            apis = {
                "android.security.KeyChain#createInstallIntent",
                "android.security.KeyChain#choosePrivateKeyAlias",
                "android.security.KeyChain#getCertificateChain",
                "android.security.KeyChain#getPrivateKey"
            })
    public void KeyChainTest() throws Exception {
        excludeFeatures(
                "android.hardware.type.watch",
                "android.hardware.type.television",
                "android.software.leanback",
                "android.hardware.type.automotive");

        runTest(".security.KeyChainTest");
    }

    @Interactive
    @Test
    // SingleDisplayMode
    @ApiTest(apis = "android.security.KeyChain#createInstallIntent")
    public void CaCertInstallViaIntentTest() throws Exception {
        excludeFeatures(
                "android.hardware.type.watch",
                "android.hardware.type.television",
                "android.software.leanback");

        runTest(".security.CaCertInstallViaIntentTest");
    }

    @Interactive
    @Test
    // SingleDisplayMode
    @ApiTest(
            apis = {
                "android.security.KeyChain#createManageCredentialsIntent",
                "android.security.KeyChain#isCredentialManagementApp",
                "android.security.KeyChain#getCredentialManagementAppPolicy",
                "android.security.KeyChain#removeCredentialManagementApp",
                "android.security.KeyChain#choosePrivateKeyAlias",
                "android.app.admin.DevicePolicyManager#generateKeyPair",
                "android.app.admin.DevicePolicyManager#setKeyPairCertificate"
            })
    public void CredentialManagementAppTest() throws Exception {
        excludeFeatures(
                "android.hardware.type.watch",
                "android.hardware.type.television",
                "android.software.leanback",
                "android.hardware.type.automotive");

        runTest(".security.CredentialManagementAppActivity");
    }
}
