/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.cts.deviceowner;

import static com.google.common.truth.Truth.assertWithMessage;

import android.app.PendingIntent;
import android.content.ContentResolver;
import android.os.Process;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;

import com.android.cts.devicepolicy.TestCertificates;

import com.google.common.collect.Range;

import java.io.ByteArrayInputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class AdminActionBookkeepingTest extends BaseDeviceOwnerTest {

    private static final String TAG = AdminActionBookkeepingTest.class.getSimpleName();

    @Override
    protected void tearDown() throws Exception {
        mDevicePolicyManager.setSecurityLoggingEnabled(getWho(), false);
        mDevicePolicyManager.setNetworkLoggingEnabled(getWho(), false);
        mDevicePolicyManager.uninstallCaCert(getWho(), TestCertificates.TEST_CA.getBytes());

        super.tearDown();
    }

    /**
     * Test: Retrieving security logs should update the corresponding timestamp.
     */
    public void testRetrieveSecurityLogs() throws Exception {
        Log.i(TAG, "testRetrieveSecurityLogs()");

        sleep(1);
        final long previousTimestamp = mDevicePolicyManager.getLastSecurityLogRetrievalTime();

        mDevicePolicyManager.setSecurityLoggingEnabled(getWho(), true);

        long timeBefore = System.currentTimeMillis();
        mDevicePolicyManager.retrieveSecurityLogs(getWho());
        long timeAfter = System.currentTimeMillis();

        final long firstTimestamp = mDevicePolicyManager.getLastSecurityLogRetrievalTime();

        assertTimeStamps(timeBefore, previousTimestamp, firstTimestamp, timeAfter);

        sleep(2);
        timeBefore = System.currentTimeMillis();
        final boolean preBootSecurityLogsRetrieved =
                mDevicePolicyManager.retrievePreRebootSecurityLogs(getWho()) != null;
        timeAfter = System.currentTimeMillis();

        final long secondTimestamp = mDevicePolicyManager.getLastSecurityLogRetrievalTime();
        if (preBootSecurityLogsRetrieved) {
            // If the device supports pre-boot security logs, verify that retrieving them updates
            // the timestamp.
            assertTimeStamps(timeBefore, firstTimestamp, secondTimestamp, timeAfter);
        } else {
            // If the device does not support pre-boot security logs, verify that the attempt to
            // retrieve them does not update the timestamp.
            assertWithMessage("timestamp when device does not support pre-boot security logs")
                    .that(firstTimestamp).isEqualTo(secondTimestamp);
        }
    }

    /**
     * Test: Retrieving network logs should update the corresponding timestamp.
     */
    public void testGetLastNetworkLogRetrievalTime() throws Exception {
        Log.i(TAG, "testGetLastNetworkLogRetrievalTime()");

        sleep(1);
        final long previousTimestamp = mDevicePolicyManager.getLastSecurityLogRetrievalTime();

        mDevicePolicyManager.setNetworkLoggingEnabled(getWho(), true);

        long timeBefore = System.currentTimeMillis();
        mDevicePolicyManager.retrieveNetworkLogs(getWho(), 0 /* batchToken */);
        long timeAfter = System.currentTimeMillis();

        final long newTimestamp = mDevicePolicyManager.getLastNetworkLogRetrievalTime();
        assertTimeStamps(timeBefore, previousTimestamp, newTimestamp, timeAfter);
    }

    /**
     * Test: The Device Owner should be able to set and retrieve the name of the organization
     * managing the device.
     */
    public void testDeviceOwnerOrganizationName() throws Exception {
        Log.i(TAG, "testDeviceOwnerOrganizationName()");

        mDevicePolicyManager.setOrganizationName(getWho(), null);
        assertWithMessage("dpm.getDeviceOwnerOrganizationName()")
                .that(mDevicePolicyManager.getDeviceOwnerOrganizationName()).isNull();

        mDevicePolicyManager.setOrganizationName(getWho(), "organization");
        assertWithMessage("dpm.getDeviceOwnerOrganizationName()")
                .that(mDevicePolicyManager.getDeviceOwnerOrganizationName())
                .isEqualTo("organization");

        mDevicePolicyManager.setOrganizationName(getWho(), null);
        assertWithMessage("dpm.getDeviceOwnerOrganizationName()")
                .that(mDevicePolicyManager.getDeviceOwnerOrganizationName()).isNull();
    }

    /**
     * Test: When a Device Owner is set, isDeviceManaged() should return true.
     */
    public void testIsDeviceManaged() throws Exception {
        Log.i(TAG, "testIsDeviceManaged()");

        assertWithMessage("dpm.isDeviceManaged()").that(mDevicePolicyManager.isDeviceManaged())
                .isTrue();
    }

    /**
     * Test: It should be recored whether the Device Owner or the user installed a CA cert.
     */
    public void testGetPolicyInstalledCaCerts() throws Exception {
        Log.i(TAG, "testGetPolicyInstalledCaCerts()");

        final byte[] rawCert = TestCertificates.TEST_CA.getBytes();
        final Certificate cert = CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(rawCert));

        // Install a CA cert.
        KeyStore keyStore = KeyStore.getInstance("AndroidCAStore");
        keyStore.load(null, null);
        assertWithMessage("keystore.getCertificateAlias()").that(keyStore.getCertificateAlias(cert))
                .isNull();
        assertWithMessage("dpm.installCaCert()")
                .that(mDevicePolicyManager.installCaCert(getWho(), rawCert)).isTrue();
        final String alias = keyStore.getCertificateAlias(cert);
        assertWithMessage("keystore.getCertificateAlias()").that(alias).isNotNull();

        // Verify that the CA cert was marked as installed by the Device Owner.
        verifyOwnerInstalledStatus(alias, true);

        // Uninstall the CA cert.
        mDevicePolicyManager.uninstallCaCert(getWho(), rawCert);

        // Verify that the CA cert is no longer marked as installed by the Device Owner.
        verifyOwnerInstalledStatus(alias, false);
    }

    private void verifyOwnerInstalledStatus(String alias, boolean expectOwnerInstalled) {
        final UserHandle user = Process.myUserHandle();
        final List<String> ownerInstalledCerts =
                mDevicePolicyManager.getOwnerInstalledCaCerts(user);
        assertWithMessage("dpm.getOwnerInstalledCaCerts(%s)", user).that(ownerInstalledCerts)
                .isNotNull();
        if (expectOwnerInstalled) {
            assertWithMessage("dpm.getOwnerInstalledCaCerts(%s)", user).that(ownerInstalledCerts)
                    .contains(alias);
        } else {
            assertWithMessage("dpm.getOwnerInstalledCaCerts(%s)", user).that(ownerInstalledCerts)
                    .doesNotContain(alias);
        }
    }

    private void sleep(int durationMs) throws InterruptedException {
        Log.v(TAG, "Sleeping for " + durationMs + " ms on thread " + Thread.currentThread());
        Thread.sleep(durationMs);
        Log.v(TAG, "Woke up");
    }

    private void assertTimeStamps(long before, long timeStamp1, long timeStamp2, long after) {
        assertWithMessage("first and second timestamp order").that(timeStamp2)
                .isGreaterThan(timeStamp1);
        assertWithMessage("second timestamp range").that(timeStamp2)
                .isIn(Range.closed(before, after));
    }
}
