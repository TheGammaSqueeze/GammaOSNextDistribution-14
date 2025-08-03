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

package com.android.cts.delegate;

import static android.app.admin.SecurityLog.TAG_KEY_DESTRUCTION;
import static android.app.admin.SecurityLog.TAG_KEY_GENERATED;

import static com.android.cts.delegate.DelegateTestUtils.assertExpectException;

import static com.google.common.truth.Truth.assertThat;

import android.app.admin.SecurityLog.SecurityEvent;
import android.os.Process;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import org.junit.Before;
import org.junit.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Tests that a delegate app with DELEGATION_SECURITY_LOGGING is able to control and access
 * security logging.
 */
public class SecurityLoggingDelegateTest extends BaseTestCase {
    private static final String GENERATED_KEY_ALIAS = "generated_key_alias";

    // Indices of various fields in SecurityEvent payload.
    private static final int SUCCESS_INDEX = 0;
    private static final int ALIAS_INDEX = 1;
    private static final int UID_INDEX = 2;
    // Value that indicates success in events that have corresponding field in their payload.
    private static final int SUCCESS_VALUE = 1;

    @Before
    public void setUp() {
        DelegateTestUtils.DelegatedLogsReceiver.sBatchCountDown = new CountDownLatch(1);
    }

    @Test
    public void testCannotAccessApis() {
        assertExpectException(SecurityException.class, null,
                () -> mDpm.isSecurityLoggingEnabled(null));

        assertExpectException(SecurityException.class, null,
                () -> mDpm.setSecurityLoggingEnabled(null, true));

        assertExpectException(SecurityException.class, null,
                () -> mDpm.retrieveSecurityLogs(null));
    }

    /**
     * Test: Test enabling security logging.
     * This test has a side effect: security logging is enabled after its execution.
     */
    @Test
    public void testEnablingSecurityLogging() {
        mDpm.setSecurityLoggingEnabled(null, true);

        assertThat(mDpm.isSecurityLoggingEnabled(null)).isTrue();
    }

    /**
     * Generates security events related to Keystore
     */
    @Test
    public void testGenerateLogs() throws Exception {
        // If any logs are available, fetch them so that force-security-logs causes a broadcast.
        mDpm.retrieveSecurityLogs(null);
        try {
            final KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA", "AndroidKeyStore");
            keyGen.initialize(new KeyGenParameterSpec.Builder(
                    GENERATED_KEY_ALIAS, KeyProperties.PURPOSE_SIGN).build());
            // Should emit key generation event.
            final KeyPair keyPair = keyGen.generateKeyPair();
            assertThat(keyPair).isNotNull();
        } finally {
            final KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            // Should emit key destruction event.
            ks.deleteEntry(GENERATED_KEY_ALIAS);
        }
    }

    /**
     * Test: retrieves security logs and verifies that all events generated as a result of host
     * side actions and by {@link #testGenerateLogs()} are there.
     */
    @Test
    public void testVerifyGeneratedLogs() throws Exception {
        DelegateTestUtils.DelegatedLogsReceiver.forceAndWaitForSecurityLogsBroadcast();
        final List<SecurityEvent> events =
                DelegateTestUtils.DelegatedLogsReceiver.getSecurityEvents();

        int keyGenerationEvents = 0;
        int keyDeletionEvents = 0;

        final int myUid = Process.myUid();

        for (final SecurityEvent event : events) {
            if (event.getTag() == TAG_KEY_GENERATED && getInt(event, UID_INDEX) == myUid) {
                verifyKeyEvent(event);
                keyGenerationEvents++;
            }

            if (event.getTag() == TAG_KEY_DESTRUCTION && getInt(event, UID_INDEX) == myUid) {
                verifyKeyEvent(event);
                keyDeletionEvents++;
            }
        }

        assertThat(keyGenerationEvents).isEqualTo(1);
        assertThat(keyDeletionEvents).isEqualTo(1);
    }

    /**
     * Verifies that a security event represents a successful key modification event for
     * keyAlias
     */
    private static void verifyKeyEvent(SecurityEvent event) {
        assertThat(getInt(event, SUCCESS_INDEX)).isEqualTo(SUCCESS_VALUE);
        assertThat(getString(event, ALIAS_INDEX)).isEqualTo(GENERATED_KEY_ALIAS);
    }

    /**
     * Test: retrieving security logs should be rate limited - subsequent attempts should return
     * null.
     */
    @Test
    public void testSecurityLoggingRetrievalRateLimited() {
        final List<SecurityEvent> logs = mDpm.retrieveSecurityLogs(null);
        // if logs is null it means that that attempt was rate limited => test PASS
        if (logs != null) {
            assertThat(mDpm.retrieveSecurityLogs(null)).isNull();
            assertThat(mDpm.retrieveSecurityLogs(null)).isNull();
        }
    }

    /**
     * Test: Test disabling security logging.
     * This test has a side effect: security logging is disabled after its execution.
     */
    @Test
    public void testDisablingSecurityLogging() {
        mDpm.setSecurityLoggingEnabled(null, false);

        assertThat(mDpm.isSecurityLoggingEnabled(null)).isFalse();
    }

    private static Object getDatum(SecurityEvent event, int index) {
        final Object[] dataArray = (Object[]) event.getData();
        return dataArray[index];
    }

    private static String getString(SecurityEvent event, int index) {
        return (String) getDatum(event, index);
    }

    private static int getInt(SecurityEvent event, int index) {
        return (Integer) getDatum(event, index);
    }
}
