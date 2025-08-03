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

package com.android.cts.deviceandprofileowner;

import static android.app.admin.DevicePolicyManager.DELEGATION_CERT_INSTALL;
import static android.app.admin.DevicePolicyManager.DELEGATION_CERT_SELECTION;

import android.keystore.cts.KeyGenerationUtils;

import java.util.Arrays;
import java.util.List;

/**
 * A helper class for setting the DelegatedCertInstaller to the desired package.
 * Does not actually contain any tests.
 */
public class DelegatedCertInstallerHelper extends BaseDeviceAdminTest {
    private static final String CERT_INSTALLER_PACKAGE = "com.android.cts.certinstaller";

    private static final List<String> CERT_INSTALL_SCOPES = Arrays.asList(
            DELEGATION_CERT_INSTALL, DELEGATION_CERT_SELECTION);

    // MUST match the alias in PreSelectedKeyAccessTest
    private static final String PRE_SELECTED_ALIAS = "pre-selected-rsa";

    public void testManualSetCertInstallerDelegate() {
        setDelegatedScopes(CERT_INSTALLER_PACKAGE, CERT_INSTALL_SCOPES);
        assertTrue(mDevicePolicyManager.getDelegatePackages(ADMIN_RECEIVER_COMPONENT,
                DELEGATION_CERT_INSTALL).contains(CERT_INSTALLER_PACKAGE));
        assertTrue(mDevicePolicyManager.getDelegatePackages(ADMIN_RECEIVER_COMPONENT,
                DELEGATION_CERT_SELECTION).contains(CERT_INSTALLER_PACKAGE));
    }

    public void testManualClearCertInstallerDelegate() {
        setDelegatedScopes(CERT_INSTALLER_PACKAGE, Arrays.asList());
        assertFalse(mDevicePolicyManager.getDelegatePackages(ADMIN_RECEIVER_COMPONENT,
                DELEGATION_CERT_INSTALL).contains(CERT_INSTALLER_PACKAGE));
        assertFalse(mDevicePolicyManager.getDelegatePackages(ADMIN_RECEIVER_COMPONENT,
                DELEGATION_CERT_SELECTION).contains(CERT_INSTALLER_PACKAGE));
    }

    public void testManualGenerateKeyAndGrantAccess() {
        KeyGenerationUtils.generateRsaKey(mDevicePolicyManager, ADMIN_RECEIVER_COMPONENT,
                PRE_SELECTED_ALIAS);
        assertTrue(mDevicePolicyManager.grantKeyPairToApp(ADMIN_RECEIVER_COMPONENT,
                PRE_SELECTED_ALIAS, CERT_INSTALLER_PACKAGE));
    }

    public void testManualRemoveKeyGrant() {
        assertTrue(mDevicePolicyManager.revokeKeyPairFromApp(ADMIN_RECEIVER_COMPONENT,
                PRE_SELECTED_ALIAS, CERT_INSTALLER_PACKAGE));
    }

    public void testManualClearGeneratedKey() {
        assertTrue(mDevicePolicyManager
                .removeKeyPair(ADMIN_RECEIVER_COMPONENT, PRE_SELECTED_ALIAS));
    }
}
