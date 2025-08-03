/*
 * Copyright (C) 2015 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Process;
import android.security.KeyChainException;
import android.test.MoreAsserts;

import com.android.cts.devicepolicy.TestCertificates;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Exercise delegated cert installer APIs in {@link DevicePolicyManager} by setting the test app
 * (CtsCertInstallerApp) as a delegated cert installer and then asking it to invoke various
 * cert-related APIs. The expected certificate changes are validated both remotely and locally.
 */
public class DelegatedCertInstallerTest extends BaseDeviceAdminTest {

    private static final String CERT_INSTALLER_PACKAGE = "com.android.cts.certinstaller";
    private static final String NOT_EXIST_CERT_INSTALLER_PACKAGE
            = "com.android.cts.certinstaller.not_exist";

    private static final String ACTION_INSTALL_CERT = "com.android.cts.certinstaller.install_cert";
    private static final String ACTION_REMOVE_CERT = "com.android.cts.certinstaller.remove_cert";
    private static final String ACTION_VERIFY_CERT = "com.android.cts.certinstaller.verify_cert";
    private static final String ACTION_INSTALL_KEYPAIR =
            "com.android.cts.certinstaller.install_keypair";
    private static final String ACTION_CERT_OPERATION_DONE = "com.android.cts.certinstaller.done";
    private static final String ACTION_READ_ENROLLMENT_SPECIFIC_ID =
            "com.android.cts.certinstaller.read_esid";

    private static final String EXTRA_CERT_DATA = "extra_cert_data";
    private static final String EXTRA_KEY_DATA = "extra_key_data";
    private static final String EXTRA_KEY_ALIAS = "extra_key_alias";
    private static final String EXTRA_RESULT_VALUE = "extra_result_value";
    private static final String EXTRA_RESULT_EXCEPTION = "extra_result_exception";
    // package name of receiver has to be specified explicitly as the receiver is registered in
    // manifest
    private static final ComponentName CERT_INSTALLER_COMPONENT = new ComponentName(
            CERT_INSTALLER_PACKAGE, "com.android.cts.certinstaller.CertInstallerReceiver");

    private static final List<String> CERT_INSTALL_SCOPES = Arrays.asList(DELEGATION_CERT_INSTALL);

    private volatile boolean mReceivedResult;
    private volatile Exception mReceivedException;
    private Semaphore mAvailableResultSemaphore;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_CERT_OPERATION_DONE.equals(intent.getAction())) {
                synchronized (DelegatedCertInstallerTest.this) {
                    mReceivedResult = intent.getBooleanExtra(EXTRA_RESULT_VALUE, false);
                    mReceivedException =
                            (Exception) intent.getSerializableExtra(EXTRA_RESULT_EXCEPTION);
                    mAvailableResultSemaphore.release();
                }
            }
        }
    };

    @Override
    public void setUp() throws Exception {
        super.setUp();

        mAvailableResultSemaphore = new Semaphore(0);
        mReceivedResult = false;
        mReceivedException = null;
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_CERT_OPERATION_DONE);
        mContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
    }

    @Override
    public void tearDown() throws Exception {
        mContext.unregisterReceiver(receiver);
        mDevicePolicyManager.uninstallCaCert(ADMIN_RECEIVER_COMPONENT,
                TestCertificates.TEST_CA.getBytes());
        // Installed private key pair will be removed once the lockscreen password is cleared,
        // which is done in the hostside test.
        mDevicePolicyManager.setCertInstallerPackage(ADMIN_RECEIVER_COMPONENT, null);
        super.tearDown();
    }

    public void testCaCertsOperations() throws InterruptedException, GeneralSecurityException,
           KeyStoreException, IOException {
        final byte[] cert = TestCertificates.TEST_CA.getBytes();
        final Certificate caCert = CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(cert));


        mDevicePolicyManager.setCertInstallerPackage(ADMIN_RECEIVER_COMPONENT,
                CERT_INSTALLER_PACKAGE);
        assertEquals(CERT_INSTALLER_PACKAGE,
                mDevicePolicyManager.getCertInstallerPackage(ADMIN_RECEIVER_COMPONENT));

        // Exercise installCaCert()
        KeyStore keyStore = KeyStore.getInstance("AndroidCAStore");
        keyStore.load(null, null);
        assertNull(keyStore.getCertificateAlias(caCert));
        installCaCert(cert);
        assertResult("installCaCert", true);
        assertTrue("Certificate is not installed properly", mDevicePolicyManager.hasCaCertInstalled(
                ADMIN_RECEIVER_COMPONENT, cert));

        // Exercise getInstalledCaCerts()
        verifyCaCert(cert);
        assertResult("getInstalledCaCerts()", true);

        // Verify that the CA cert was marked as installed by the Device Owner / Profile Owner.
        final String alias = keyStore.getCertificateAlias(caCert);
        assertNotNull(alias);
        verifyOwnerInstalledStatus(alias, true);

        // Exercise uninstallCaCert()
        removeCaCert(cert);
        assertResult("uninstallCaCert()", true);
        assertFalse("Certificate is not removed properly", mDevicePolicyManager.hasCaCertInstalled(
                ADMIN_RECEIVER_COMPONENT, cert));

        // Verify that the CA cert is no longer reported as installed by the Device Owner / Profile
        // Owner.
        verifyOwnerInstalledStatus(alias, false);

        // Clear delegated cert installer.
        // Tests after this are expected to fail.
        mDevicePolicyManager.setCertInstallerPackage(ADMIN_RECEIVER_COMPONENT, null);

        installCaCert(cert);
        assertResult("installCaCert", false);
    }

    public void testInstallKeyPair() throws InterruptedException, KeyChainException {
        final String alias = "delegated-cert-installer-test-key";

        // Clear delegated cert installer.
        mDevicePolicyManager.setCertInstallerPackage(ADMIN_RECEIVER_COMPONENT, null);
        // The app is not the cert installer , it shouldn't have have privilege to call
        // installKeyPair().
        installKeyPair(TestCertificates.TEST_KEY, TestCertificates.TEST_CERT, alias);
        assertResult("installKeyPair", false);

        // Set the app to be cert installer.
        mDevicePolicyManager.setCertInstallerPackage(ADMIN_RECEIVER_COMPONENT,
                CERT_INSTALLER_PACKAGE);
        assertEquals(CERT_INSTALLER_PACKAGE,
                mDevicePolicyManager.getCertInstallerPackage(ADMIN_RECEIVER_COMPONENT));

        // Exercise installKeyPair()
        installKeyPair(TestCertificates.TEST_KEY, TestCertificates.TEST_CERT, alias);
        assertResult("installKeyPair", true);
    }

    /**
     * If DPC is targeting N+, @{link IllegalArgumentException } should be thrown if the package
     * is missing.
     */
    public void testSetNotExistCertInstallerPackage() throws Exception {
        boolean shouldThrowException = getTargetApiLevel() >= Build.VERSION_CODES.N;
        try {
            mDevicePolicyManager.setCertInstallerPackage(
                    ADMIN_RECEIVER_COMPONENT, NOT_EXIST_CERT_INSTALLER_PACKAGE);
            if (shouldThrowException) {
                fail("Did not throw IllegalArgumentException");
            }
        } catch (IllegalArgumentException ex) {
            if (!shouldThrowException) {
                fail("Should not throw exception");
            }
            MoreAsserts.assertContainsRegex("is not installed on the current user",
                        ex.getMessage());
        }
        if (!shouldThrowException) {
            assertTrue("Cert install delegate was not set on uninstalled package",
                    NOT_EXIST_CERT_INSTALLER_PACKAGE.equals(
                            mDevicePolicyManager
                                    .getCertInstallerPackage(ADMIN_RECEIVER_COMPONENT)));
        }
    }

    public void testSettingDelegatedCertInstallerAPICompatibility_oldSetNewGet() {
        // Set a delegated cert installer using the deprecated API and verify that the same
        // package is considered as the delegated cert installer using the new API.
        mDevicePolicyManager.setCertInstallerPackage(ADMIN_RECEIVER_COMPONENT,
                CERT_INSTALLER_PACKAGE);
        assertThat(mDevicePolicyManager.getCertInstallerPackage(ADMIN_RECEIVER_COMPONENT))
                .isEqualTo(CERT_INSTALLER_PACKAGE);
        assertThat(mDevicePolicyManager.getDelegatePackages(ADMIN_RECEIVER_COMPONENT,
                DELEGATION_CERT_INSTALL)).containsExactly(CERT_INSTALLER_PACKAGE);

        // Remove a delegate using the old API, make sure no delegates are found using
        // the new API.
        mDevicePolicyManager.setCertInstallerPackage(ADMIN_RECEIVER_COMPONENT, null);
        assertThat(mDevicePolicyManager.getCertInstallerPackage(ADMIN_RECEIVER_COMPONENT)).isNull();
        assertThat(mDevicePolicyManager.getDelegatePackages(ADMIN_RECEIVER_COMPONENT,
                DELEGATION_CERT_INSTALL)).isEmpty();
    }

    public void testSettingDelegatedCertInstallerAPICompatibility_newSetOldGet() {
        // Set a delegate using the new API, verify that the deprecated API returns the same
        // delegate.
        setDelegatedScopes(CERT_INSTALLER_PACKAGE, CERT_INSTALL_SCOPES);
        assertThat(mDevicePolicyManager.getDelegatePackages(ADMIN_RECEIVER_COMPONENT,
                DELEGATION_CERT_INSTALL)).containsExactly(CERT_INSTALLER_PACKAGE);
        assertThat(mDevicePolicyManager.getCertInstallerPackage(ADMIN_RECEIVER_COMPONENT))
                .isEqualTo(CERT_INSTALLER_PACKAGE);

        // Remove the delegate using the new API, verify that the deprecated API returns null
        // as the current delegated cert installer.
        setDelegatedScopes(CERT_INSTALLER_PACKAGE, Arrays.asList());
        assertThat(mDevicePolicyManager.getDelegatePackages(ADMIN_RECEIVER_COMPONENT,
                DELEGATION_CERT_INSTALL)).isEmpty();
        assertThat(mDevicePolicyManager.getCertInstallerPackage(ADMIN_RECEIVER_COMPONENT)).isNull();
    }

    public void testCanReadEnrollmentSpecificId() throws InterruptedException {
        // Set the organization ID only if not already set, to avoid potential conflict
        // with other tests.
        if (mDevicePolicyManager.getEnrollmentSpecificId().isEmpty()) {
            mDevicePolicyManager.setOrganizationId("SOME_ID");
        }
        setDelegatedScopes(CERT_INSTALLER_PACKAGE, CERT_INSTALL_SCOPES);

        readEnrollmentId();
        assertResult("testCanReadEnrollmentSpecificId", true);
    }

    private void installCaCert(byte[] cert) {
        Intent intent = new Intent();
        intent.setAction(ACTION_INSTALL_CERT);
        intent.setComponent(CERT_INSTALLER_COMPONENT);
        intent.putExtra(EXTRA_CERT_DATA, cert);
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        mContext.sendBroadcast(intent);
    }

    private void removeCaCert(byte[] cert) {
        Intent intent = new Intent();
        intent.setAction(ACTION_REMOVE_CERT);
        intent.setComponent(CERT_INSTALLER_COMPONENT);
        intent.putExtra(EXTRA_CERT_DATA, cert);
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        mContext.sendBroadcast(intent);
    }

    private void verifyCaCert(byte[] cert) {
        Intent intent = new Intent();
        intent.setAction(ACTION_VERIFY_CERT);
        intent.setComponent(CERT_INSTALLER_COMPONENT);
        intent.putExtra(EXTRA_CERT_DATA, cert);
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        mContext.sendBroadcast(intent);
    }

    private void verifyOwnerInstalledStatus(String alias, boolean expectOwnerInstalled) {
        final List<String> ownerInstalledCerts =
                mDevicePolicyManager.getOwnerInstalledCaCerts(Process.myUserHandle());
        assertNotNull(ownerInstalledCerts);
        assertEquals(expectOwnerInstalled, ownerInstalledCerts.contains(alias));
    }

    private void assertResult(String testName, Boolean expectSuccess) throws InterruptedException {
        assertTrue("Cert installer did not respond in time.",
                mAvailableResultSemaphore.tryAcquire(180, TimeUnit.SECONDS));
        synchronized (this) {
            if (expectSuccess) {
                assertTrue(testName + " failed unexpectedly.", mReceivedResult);
                assertNull(testName + " raised exception", mReceivedException);
            } else {
                assertFalse(testName + " succeeded unexpectedly.", mReceivedResult);
                assertTrue(testName + " did not raise SecurityException",
                        mReceivedException != null &&
                        mReceivedException instanceof SecurityException);
            }
        }
    }

    private void installKeyPair(String key, String cert, String alias) {
        Intent intent = new Intent();
        intent.setAction(ACTION_INSTALL_KEYPAIR);
        intent.setComponent(CERT_INSTALLER_COMPONENT);
        intent.putExtra(EXTRA_CERT_DATA, cert);
        intent.putExtra(EXTRA_KEY_DATA, key);
        intent.putExtra(EXTRA_KEY_ALIAS, alias);
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        mContext.sendBroadcast(intent);
    }

    private void readEnrollmentId() {
        Intent intent = new Intent();
        intent.setAction(ACTION_READ_ENROLLMENT_SPECIFIC_ID);
        intent.setComponent(CERT_INSTALLER_COMPONENT);
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        mContext.sendBroadcast(intent);
    }
}
