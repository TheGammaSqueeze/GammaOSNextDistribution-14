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
package com.android.cts.deviceandprofileowner;

import static android.app.KeyguardManager.PIN;
import static android.app.admin.DevicePolicyManager.KEYGUARD_DISABLE_FINGERPRINT;
import static android.app.admin.DevicePolicyManager.PASSWORD_COMPLEXITY_HIGH;
import static android.app.admin.DevicePolicyManager.PASSWORD_COMPLEXITY_LOW;
import static android.app.admin.DevicePolicyManager.PASSWORD_COMPLEXITY_MEDIUM;
import static android.app.admin.DevicePolicyManager.PASSWORD_COMPLEXITY_NONE;
import static android.app.admin.DevicePolicyManager.PASSWORD_QUALITY_COMPLEX;
import static android.app.admin.SecurityLog.LEVEL_ERROR;
import static android.app.admin.SecurityLog.LEVEL_INFO;
import static android.app.admin.SecurityLog.LEVEL_WARNING;
import static android.app.admin.SecurityLog.TAG_ADB_SHELL_CMD;
import static android.app.admin.SecurityLog.TAG_ADB_SHELL_INTERACTIVE;
import static android.app.admin.SecurityLog.TAG_APP_PROCESS_START;
import static android.app.admin.SecurityLog.TAG_BLUETOOTH_CONNECTION;
import static android.app.admin.SecurityLog.TAG_BLUETOOTH_DISCONNECTION;
import static android.app.admin.SecurityLog.TAG_CAMERA_POLICY_SET;
import static android.app.admin.SecurityLog.TAG_CERT_AUTHORITY_INSTALLED;
import static android.app.admin.SecurityLog.TAG_CERT_AUTHORITY_REMOVED;
import static android.app.admin.SecurityLog.TAG_CERT_VALIDATION_FAILURE;
import static android.app.admin.SecurityLog.TAG_CRYPTO_SELF_TEST_COMPLETED;
import static android.app.admin.SecurityLog.TAG_KEYGUARD_DISABLED_FEATURES_SET;
import static android.app.admin.SecurityLog.TAG_KEYGUARD_DISMISSED;
import static android.app.admin.SecurityLog.TAG_KEYGUARD_DISMISS_AUTH_ATTEMPT;
import static android.app.admin.SecurityLog.TAG_KEYGUARD_SECURED;
import static android.app.admin.SecurityLog.TAG_KEY_DESTRUCTION;
import static android.app.admin.SecurityLog.TAG_KEY_GENERATED;
import static android.app.admin.SecurityLog.TAG_KEY_IMPORT;
import static android.app.admin.SecurityLog.TAG_KEY_INTEGRITY_VIOLATION;
import static android.app.admin.SecurityLog.TAG_LOGGING_STARTED;
import static android.app.admin.SecurityLog.TAG_LOGGING_STOPPED;
import static android.app.admin.SecurityLog.TAG_LOG_BUFFER_SIZE_CRITICAL;
import static android.app.admin.SecurityLog.TAG_MAX_PASSWORD_ATTEMPTS_SET;
import static android.app.admin.SecurityLog.TAG_MAX_SCREEN_LOCK_TIMEOUT_SET;
import static android.app.admin.SecurityLog.TAG_MEDIA_MOUNT;
import static android.app.admin.SecurityLog.TAG_MEDIA_UNMOUNT;
import static android.app.admin.SecurityLog.TAG_OS_SHUTDOWN;
import static android.app.admin.SecurityLog.TAG_OS_STARTUP;
import static android.app.admin.SecurityLog.TAG_PASSWORD_CHANGED;
import static android.app.admin.SecurityLog.TAG_PASSWORD_COMPLEXITY_REQUIRED;
import static android.app.admin.SecurityLog.TAG_PASSWORD_COMPLEXITY_SET;
import static android.app.admin.SecurityLog.TAG_PASSWORD_EXPIRATION_SET;
import static android.app.admin.SecurityLog.TAG_PASSWORD_HISTORY_LENGTH_SET;
import static android.app.admin.SecurityLog.TAG_REMOTE_LOCK;
import static android.app.admin.SecurityLog.TAG_SYNC_RECV_FILE;
import static android.app.admin.SecurityLog.TAG_SYNC_SEND_FILE;
import static android.app.admin.SecurityLog.TAG_USER_RESTRICTION_ADDED;
import static android.app.admin.SecurityLog.TAG_USER_RESTRICTION_REMOVED;
import static android.app.admin.SecurityLog.TAG_WIPE_FAILURE;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;
import static com.android.cts.devicepolicy.TestCertificates.TEST_CA;
import static com.android.cts.devicepolicy.TestCertificates.TEST_CA_SUBJECT;

import static com.google.common.collect.ImmutableList.of;
import static com.google.common.truth.Truth.assertThat;

import android.app.KeyguardManager;
import android.app.admin.DevicePolicyManager;
import android.app.admin.SecurityLog;
import android.app.admin.SecurityLog.SecurityEvent;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Parcel;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.security.keystore.KeyProtection;
import android.text.TextUtils;
import android.util.DebugUtils;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import junit.framework.AssertionFailedError;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.crypto.spec.SecretKeySpec;

public class SecurityLoggingTest extends BaseDeviceAdminTest {
    private static final String TAG = "SecurityLoggingTest";
    private static final String ARG_BATCH_NUMBER = "batchNumber";
    private static final String PREF_KEY_PREFIX = "batch-last-id-";
    private static final String PREF_NAME = "batchIds";
    // system/core/liblog/event.logtags: 1006  liblog (dropped|1)
    private static final int TAG_LIBLOG_DROPPED = 1006;
    private static final String DELEGATE_APP_PKG = "com.android.cts.delegate";
    private static final String DELEGATION_SECURITY_LOGGING = "delegation-security-logging";
    private static final boolean VERBOSE = false;

    // For brevity.
    private static final Class<String> S = String.class;
    private static final Class<Long> L = Long.class;
    private static final Class<Integer> I = Integer.class;

    private static final Map<Integer, List<Class>> PAYLOAD_TYPES_MAP =
            new ImmutableMap.Builder<Integer, List<Class>>()
                    .put(TAG_ADB_SHELL_INTERACTIVE, of())
                    .put(TAG_ADB_SHELL_CMD, of(S))
                    .put(TAG_SYNC_RECV_FILE, of(S))
                    .put(TAG_SYNC_SEND_FILE, of(S))
                    .put(TAG_APP_PROCESS_START, of(S, L, I, I, S, S))
                    .put(TAG_KEYGUARD_DISMISSED, of())
                    .put(TAG_KEYGUARD_DISMISS_AUTH_ATTEMPT, of(I, I))
                    .put(TAG_KEYGUARD_SECURED, of())
                    .put(TAG_OS_STARTUP, of(S, S))
                    .put(TAG_OS_SHUTDOWN, of())
                    .put(TAG_LOGGING_STARTED, of())
                    .put(TAG_LOGGING_STOPPED, of())
                    .put(TAG_MEDIA_MOUNT, of(S, S))
                    .put(TAG_MEDIA_UNMOUNT, of(S, S))
                    .put(TAG_LOG_BUFFER_SIZE_CRITICAL, of())
                    .put(TAG_PASSWORD_EXPIRATION_SET, of(S, I, I, L))
                    .put(TAG_PASSWORD_COMPLEXITY_SET, of(S, I, I, I, I, I, I, I, I, I, I))
                    .put(TAG_PASSWORD_HISTORY_LENGTH_SET, of(S, I, I, I))
                    .put(TAG_MAX_SCREEN_LOCK_TIMEOUT_SET, of(S, I, I, L))
                    .put(TAG_MAX_PASSWORD_ATTEMPTS_SET, of(S, I, I, I))
                    .put(TAG_KEYGUARD_DISABLED_FEATURES_SET, of(S, I, I, I))
                    .put(TAG_REMOTE_LOCK, of(S, I, I))
                    .put(TAG_WIPE_FAILURE, of())
                    .put(TAG_KEY_GENERATED, of(I, S, I))
                    .put(TAG_KEY_IMPORT, of(I, S, I))
                    .put(TAG_KEY_DESTRUCTION, of(I, S, I))
                    .put(TAG_CERT_AUTHORITY_INSTALLED, of(I, S, I))
                    .put(TAG_CERT_AUTHORITY_REMOVED, of(I, S, I))
                    .put(TAG_USER_RESTRICTION_ADDED, of(S, I, S))
                    .put(TAG_USER_RESTRICTION_REMOVED, of(S, I, S))
                    .put(TAG_CRYPTO_SELF_TEST_COMPLETED, of(I))
                    .put(TAG_KEY_INTEGRITY_VIOLATION, of(S, I))
                    .put(TAG_CERT_VALIDATION_FAILURE, of(S))
                    .put(TAG_CAMERA_POLICY_SET, of(S, I, I, I))
                    .put(TAG_PASSWORD_COMPLEXITY_REQUIRED, of(S, I, I, I))
                    .put(TAG_PASSWORD_CHANGED, of(I, I))
                    .put(TAG_BLUETOOTH_CONNECTION, of(S, I, S))
                    .put(TAG_BLUETOOTH_DISCONNECTION, of(S, S))
                    .build();

    private static final String GENERATED_KEY_ALIAS = "generated_key_alias";
    private static final String IMPORTED_KEY_ALIAS = "imported_key_alias";

    // Indices of various fields in event payload.
    private static final int SUCCESS_INDEX = 0;
    private static final int ALIAS_INDEX = 1;
    private static final int UID_INDEX = 2;
    private static final int USERID_INDEX = 2;
    private static final int SUBJECT_INDEX = 1;
    private static final int ADMIN_PKG_INDEX = 0;
    private static final int ADMIN_USER_INDEX = 1;
    private static final int TARGET_USER_INDEX = 2;
    private static final int PWD_LEN_INDEX = 3;
    private static final int PWD_QUALITY_INDEX = 4;
    private static final int LETTERS_INDEX = 5;
    private static final int NON_LETTERS_INDEX = 6;
    private static final int NUMERIC_INDEX = 7;
    private static final int UPPERCASE_INDEX = 8;
    private static final int LOWERCASE_INDEX = 9;
    private static final int SYMBOLS_INDEX = 10;
    private static final int PWD_EXPIRATION_INDEX = 3;
    private static final int PWD_HIST_LEN_INDEX = 3;
    private static final int USER_RESTRICTION_INDEX = 2;
    private static final int MAX_PWD_ATTEMPTS_INDEX = 3;
    private static final int KEYGUARD_FEATURES_INDEX = 3;
    private static final int MAX_SCREEN_TIMEOUT_INDEX = 3;
    private static final int CAMERA_DISABLED_INDEX = 3;

    // Value that indicates success in events that have corresponding field in their payload.
    private static final int SUCCESS_VALUE = 1;

    private static final int TEST_PWD_LENGTH = 10;
    // Min number of various character types to use.
    private static final int TEST_PWD_CHARS = 2;

    private static final long TEST_PWD_EXPIRATION_TIMEOUT = TimeUnit.DAYS.toMillis(356);
    private static final int TEST_PWD_HISTORY_LENGTH = 3;
    private static final int TEST_PWD_MAX_ATTEMPTS = 5;
    private static final long TEST_MAX_TIME_TO_LOCK = TimeUnit.HOURS.toMillis(1);

    /**
     * Test: retrieving security logs can only be done if there's one user on the device or all
     * secondary users / profiles are affiliated.
     */
    public void testRetrievingSecurityLogsThrowsSecurityException() {
        try {
            mDevicePolicyManager.retrieveSecurityLogs(ADMIN_RECEIVER_COMPONENT);
            fail("did not throw expected SecurityException");
        } catch (SecurityException expected) {
        }
    }

    /**
     * Test: retrieving previous security logs can only be done if there's one user on the device or
     * all secondary users / profiles are affiliated.
     */
    public void testRetrievingPreviousSecurityLogsThrowsSecurityException() {
        try {
            mDevicePolicyManager.retrievePreRebootSecurityLogs(ADMIN_RECEIVER_COMPONENT);
            fail("did not throw expected SecurityException");
        } catch (SecurityException expected) {
        }
    }

    /**
     * Test: retrieves security logs and verifies that all events generated as a result of host
     * side actions and by {@link #testGenerateLogs()} are there.
     */
    public void testVerifyGeneratedLogs() throws Exception {
        forceSecurityLogs();

        final List<SecurityEvent> events = getEvents();

        verifyAutomaticEventsPresent(events);
        verifyKeystoreEventsPresent(events);
        verifyKeyChainEventsPresent(events);
        verifyAdminEventsPresent(events);
        verifyPasswordChangedEventsPresent(events);
        verifyAdbShellCommand(events); // Event generated from host side logic
        if (mDevicePolicyManager.isOrganizationOwnedDeviceWithManagedProfile()) {
            verifyEventsRedacted(events);
        }
    }

    private void forceSecurityLogs() throws Exception {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                .executeShellCommand("dpm force-security-logs");
    }

    private void verifyAutomaticEventsPresent(List<SecurityEvent> events) {
        verifyOsStartupEventPresent(events);
        verifyLoggingStartedEventPresent(events);
        verifyCryptoSelfTestEventPresent(events);
    }

    private void verifyKeyChainEventsPresent(List<SecurityEvent> events) {
        verifyCertInstalledEventPresent(events);
        verifyCertUninstalledEventPresent(events);
    }

    private void verifyKeystoreEventsPresent(List<SecurityEvent> events) {
        verifyKeyGeneratedEventPresent(events, GENERATED_KEY_ALIAS);
        verifyKeyDeletedEventPresent(events, GENERATED_KEY_ALIAS);
        verifyKeyImportedEventPresent(events, IMPORTED_KEY_ALIAS);
        verifyKeyDeletedEventPresent(events, IMPORTED_KEY_ALIAS);
    }

    private void verifyAdminEventsPresent(List<SecurityEvent> events) {
        if (mHasSecureLockScreen) {
            verifyPasswordComplexityEventsPresent(events);
            verifyNewStylePasswordComplexityEventPresent(events);
        }
        verifyLockingPolicyEventsPresent(events);
        verifyUserRestrictionEventsPresent(events);
        verifyCameraPolicyEvents(events);
    }

    private void verifyPasswordChangedEventsPresent(List<SecurityEvent> events) {
        if (!mHasSecureLockScreen) {
            return;
        }
        final int userId = Process.myUserHandle().getIdentifier();
        findEvent("set low complexity password", events,
                e -> e.getTag() == TAG_PASSWORD_CHANGED
                        && getInt(e, 0) == PASSWORD_COMPLEXITY_LOW
                        && getInt(e, 1) == userId);
        findEvent("set medium complexity password", events,
                e -> e.getTag() == TAG_PASSWORD_CHANGED
                        && getInt(e, 0) == PASSWORD_COMPLEXITY_MEDIUM
                        && getInt(e, 1) == userId);
        findEvent("set high complexity password", events,
                e -> e.getTag() == TAG_PASSWORD_CHANGED
                        && getInt(e, 0) == PASSWORD_COMPLEXITY_HIGH
                        && getInt(e, 1) == userId);
        findEvent("set none complexity password", events,
                e -> e.getTag() == TAG_PASSWORD_CHANGED
                        && getInt(e, 0) == PASSWORD_COMPLEXITY_NONE
                        && getInt(e, 1) == userId);
    }

    private void verifyAdbShellCommand(List<SecurityEvent> events) {
        // Won't be able to locate the command on org-owned devices, as it will be redacted.
        if (!mDevicePolicyManager.isOrganizationOwnedDeviceWithManagedProfile()) {
            findEvent("adb command", events,
                    e -> e.getTag() == TAG_ADB_SHELL_CMD &&
                            e.getData().equals("whoami"));

        }
    }

    private void verifyEventsRedacted(List<SecurityEvent> events) {
        final int userId = Process.myUserHandle().getIdentifier();
        for (SecurityEvent event : events) {
            switch (event.getTag()) {
                case TAG_ADB_SHELL_CMD:
                    assertTrue(TextUtils.isEmpty((String) event.getData()));
                    break;
                case TAG_APP_PROCESS_START:
                case TAG_KEY_GENERATED:
                case TAG_KEY_IMPORT:
                case TAG_KEY_DESTRUCTION:
                    assertEquals(userId, UserHandle.getUserId(getInt(event, UID_INDEX)));
                    break;
                case TAG_CERT_AUTHORITY_INSTALLED:
                case TAG_CERT_AUTHORITY_REMOVED:
                    assertEquals(userId, getInt(event, USERID_INDEX));
                    break;
                case TAG_KEY_INTEGRITY_VIOLATION:
                    assertEquals(userId, UserHandle.getUserId(getInt(event, 1)));
                    break;
                case TAG_PASSWORD_CHANGED:
                    assertEquals(userId, getInt(event, 1));
                    break;
            }
        }
    }

    /**
     * Generates events for positive test cases.
     */
    public void testGenerateLogs() throws Exception {
        generateKeystoreEvents();
        generateKeyChainEvents();
        generateAdminEvents();
        generatePasswordChangedEvents();
    }

    private void generateKeyChainEvents() {
        installCaCert();
        uninstallCaCert();
    }

    private void generateKeystoreEvents() throws Exception {
        generateKey(GENERATED_KEY_ALIAS);
        deleteKey(GENERATED_KEY_ALIAS);
        importKey(IMPORTED_KEY_ALIAS);
        deleteKey(IMPORTED_KEY_ALIAS);
    }

    private void generateAdminEvents() {
        if (mHasSecureLockScreen) {
            generatePasswordComplexityEvents();
            generateNewStylePasswordComplexityEvents();
        }
        generateLockingPolicyEvents();
        generateUserRestrictionEvents();
        generateCameraPolicyEvents();
    }

    private void generatePasswordChangedEvents() {
        if (!mHasSecureLockScreen) {
            return;
        }
        mOnPasswordChangedCalled = new CountDownLatch(4);
        KeyguardManager km = mContext.getSystemService(KeyguardManager.class);
        runWithShellPermissionIdentity(() -> {
            km.setLock(PIN, "1111".getBytes(), PIN, null);
            km.setLock(PIN, "1914".getBytes(), PIN, "1111".getBytes());
            km.setLock(PIN, "83651865".getBytes(), PIN, "1914".getBytes());
            km.setLock(PIN, null, PIN, "83651865".getBytes());
        });
        try {
            mOnPasswordChangedCalled.await(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            fail(e.toString());
        } finally {
            mOnPasswordChangedCalled = null;
        }
    }
    /**
     * Fetches and checks the events.
     */
    private List<SecurityEvent> getEvents() throws Exception {
        List<SecurityEvent> events = null;
        // Retry once after sleeping for 1 second, in case "dpm force-security-logs" hasn't taken
        // effect just yet.
        for (int i = 0; i < 5 && events == null; i++) {
            events = mDevicePolicyManager.retrieveSecurityLogs(ADMIN_RECEIVER_COMPONENT);
            Log.v(TAG, "getEvents(), batch #" + i + ": "  + (events == null ? -1 : events.size())
                    + " events");
            if (events == null) sleep((i + 1) * 1000);
        }

        Log.d(TAG, "getEvents(): received " + (events == null ? -1 : events.size()) + " events");
        if (VERBOSE) dumpSecurityLogs(events);

        try {
            verifySecurityLogs(events);
        } catch (AssertionFailedError e) {
            dumpSecurityLogs(events);
            throw e;
        }

        return events;
    }

    /**
     * Test: check that there are no gaps between ids in two consecutive batches. Shared preference
     * is used to store these numbers between test invocations.
     */
    public void testVerifyLogIds() throws Exception {
        forceSecurityLogs();
        final String param = InstrumentationRegistry.getArguments().getString(ARG_BATCH_NUMBER);
        final int batchId = param == null ? 0 : Integer.parseInt(param);
        final List<SecurityEvent> events = getEvents();
        final SharedPreferences prefs =
                mContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        final long firstId = events.get(0).getId();
        if (batchId == 0) {
            assertEquals("Event id wasn't reset.", 0L, firstId);
        } else {
            final String prevBatchLastIdKey = PREF_KEY_PREFIX + (batchId - 1);
            assertTrue("Last event id from previous batch not found in shared prefs",
                    prefs.contains(prevBatchLastIdKey));
            final long prevBatchLastId = prefs.getLong(prevBatchLastIdKey, 0);
            assertEquals("Event ids aren't consecutive between batches",
                    firstId, prevBatchLastId + 1);
        }

        final String currBatchLastIdKey = PREF_KEY_PREFIX + batchId;
        final long lastId = events.get(events.size() - 1).getId();
        prefs.edit().putLong(currBatchLastIdKey, lastId).commit();
    }

    private void verifySecurityLogs(List<SecurityEvent> events) {
        assertTrue("Unable to get events", events != null && events.size() > 0);

        // We don't know much about the events, so just call public API methods.
        for (int i = 0; i < events.size(); i++) {
            final SecurityEvent event = events.get(i);

            // Skip liblog dropped event.
            if (event.getTag() == TAG_LIBLOG_DROPPED) {
                continue;
            }

            verifyPayloadTypes(event);

            // Test id for monotonically increasing.
            if (i > 0) {
                assertEquals("Event IDs are not monotonically increasing within the batch",
                        events.get(i - 1).getId() + 1, event.getId());
            }

            // Test parcelling: flatten to a parcel.
            Parcel p = Parcel.obtain();
            event.writeToParcel(p, 0);
            p.setDataPosition(0);

            // Restore from parcel and check contents.
            final SecurityEvent restored = SecurityEvent.CREATOR.createFromParcel(p);
            p.recycle();

            final int level = event.getLogLevel();
            assertTrue(level == LEVEL_INFO || level == LEVEL_WARNING || level == LEVEL_ERROR);

            // For some events data is encapsulated into Object array.
            if (event.getData() instanceof Object[]) {
                assertTrue("Parcelling changed the array returned by getData",
                        Arrays.equals((Object[]) event.getData(), (Object[]) restored.getData()));
            } else {
                assertEquals("Parcelling changed the result of getData",
                        event.getData(), restored.getData());
            }
            assertEquals("Parcelling changed the result of getId",
                    event.getId(), restored.getId());
            assertEquals("Parcelling changed the result of getTag",
                    event.getTag(), restored.getTag());
            assertEquals("Parcelling changed the result of getTimeNanos",
                    event.getTimeNanos(), restored.getTimeNanos());
            assertEquals("Parcelling changed the result of describeContents",
                    event.describeContents(), restored.describeContents());
        }
    }

    private void verifyPayloadTypes(SecurityEvent event) {
        final List<Class> payloadTypes = PAYLOAD_TYPES_MAP.get(event.getTag());
        assertNotNull("event type unknown: " + event.getTag(), payloadTypes);

        if (payloadTypes.size() == 0) {
            // No payload.
            assertNull("non-null payload", event.getData());
        } else if (payloadTypes.size() == 1) {
            // Singleton payload.
            assertTrue(payloadTypes.get(0).isInstance(event.getData()));
        } else {
            // Payload is incapsulated into Object[]
            assertTrue(event.getData() instanceof Object[]);
            final Object[] dataArray = (Object[]) event.getData();
            assertEquals(payloadTypes.size(), dataArray.length);
            for (int i = 0; i < payloadTypes.size(); i++) {
                assertTrue(payloadTypes.get(i).isInstance(dataArray[i]));
            }
        }
    }

    private void verifyOsStartupEventPresent(List<SecurityEvent> events) {
        final SecurityEvent event = findEvent("os startup", events, TAG_OS_STARTUP);
        // Verified boot state, empty if running on emulator
        assertOneOf(ImmutableSet.of("", "green", "yellow", "orange"), getString(event, 0));
        // dm-verity mode, empty if it is disabled
        assertOneOf(ImmutableSet.of("", "enforcing", "eio", "disabled"), getString(event, 1));
    }

    private void assertOneOf(Set<String> allowed, String s) {
        assertTrue(String.format("\"%s\" is not one of [%s]", s, String.join(", ", allowed)),
                allowed.contains(s));
    }

    private void verifyCryptoSelfTestEventPresent(List<SecurityEvent> events) {
        final SecurityEvent event = findEvent("crypto self test complete",
                events, TAG_CRYPTO_SELF_TEST_COMPLETED);
        // Success code.
        assertEquals(1, getInt(event));
    }

    private void verifyLoggingStartedEventPresent(List<SecurityEvent> events) {
        findEvent("logging started", events, TAG_LOGGING_STARTED);
    }

    private SecurityEvent findEvent(String description, List<SecurityEvent> events, int tag) {
        return findEvent(description, events, e -> e.getTag() == tag);
    }

    private List<SecurityEvent> findEvents(List<SecurityEvent> events,
            Predicate<SecurityEvent> predicate) {
        return events.stream().filter(predicate).collect(Collectors.toList());
    }

    private SecurityEvent findEvent(String description, List<SecurityEvent> events,
            Predicate<SecurityEvent> predicate) {
        final List<SecurityEvent> matches = findEvents(events, predicate);
        assertEquals("Invalid number of matching events: " + description, 1, matches.size());
        return matches.get(0);
    }

    private void assertNumberEvents(String description, List<SecurityEvent> events,
            Predicate<SecurityEvent> predicate, int expectedSize) {
        assertEquals("Invalid number of matching events: " + description, expectedSize,
                findEvents(events, predicate).size());
    }

    private static Object getDatum(SecurityEvent event, int index) {
        final Object[] dataArray = (Object[]) event.getData();
        return dataArray[index];
    }

    private static String getString(SecurityEvent event, int index) {
        return (String) getDatum(event, index);
    }

    private static int getInt(SecurityEvent event) {
        return (Integer) event.getData();
    }

    private static int getInt(SecurityEvent event, int index) {
        return (Integer) getDatum(event, index);
    }

    private static long getLong(SecurityEvent event, int index) {
        return (Long) getDatum(event, index);
    }

    /**
     * Test: Test enabling security logging. This test should be executed after installing a device
     * owner so that we check that logging is not enabled by default. This test has a side effect:
     * security logging is enabled after its execution.
     */
    public void testEnablingSecurityLogging() {
        assertFalse(mDevicePolicyManager.isSecurityLoggingEnabled(ADMIN_RECEIVER_COMPONENT));
        mDevicePolicyManager.setSecurityLoggingEnabled(ADMIN_RECEIVER_COMPONENT, true);
        assertTrue(mDevicePolicyManager.isSecurityLoggingEnabled(ADMIN_RECEIVER_COMPONENT));
    }

    /**
     * Test: Test disabling security logging. This test has a side effect: security logging is
     * disabled after its execution.
     */
    public void testDisablingSecurityLogging() {
        mDevicePolicyManager.setSecurityLoggingEnabled(ADMIN_RECEIVER_COMPONENT, false);
        assertFalse(mDevicePolicyManager.isSecurityLoggingEnabled(ADMIN_RECEIVER_COMPONENT));

        // Verify that logs are actually not available.
        assertNull(mDevicePolicyManager.retrieveSecurityLogs(ADMIN_RECEIVER_COMPONENT));
    }

    /**
     * Test: retrieving security logs should be rate limited - subsequent attempts should return
     * null.
     */
    public void testSecurityLoggingRetrievalRateLimited() {
        final List<SecurityEvent> logs = mDevicePolicyManager.retrieveSecurityLogs(
                ADMIN_RECEIVER_COMPONENT);
        // if logs is null it means that that attempt was rate limited => test PASS
        if (logs != null) {
            assertNull(mDevicePolicyManager.retrieveSecurityLogs(ADMIN_RECEIVER_COMPONENT));
            assertNull(mDevicePolicyManager.retrieveSecurityLogs(ADMIN_RECEIVER_COMPONENT));
        }
    }

    public void testSetDelegateScope_delegationSecurityLogging() {
        setDelegatedScopes(DELEGATE_APP_PKG, Arrays.asList(DELEGATION_SECURITY_LOGGING));

        assertThat(mDevicePolicyManager.getDelegatedScopes(
                ADMIN_RECEIVER_COMPONENT, DELEGATE_APP_PKG)).contains(DELEGATION_SECURITY_LOGGING);
    }

    public void testSetDelegateScope_noDelegation() {
        setDelegatedScopes(DELEGATE_APP_PKG, Arrays.asList());

        assertThat(mDevicePolicyManager.getDelegatedScopes(
                ADMIN_RECEIVER_COMPONENT, DELEGATE_APP_PKG))
                .doesNotContain(DELEGATION_SECURITY_LOGGING);
    }

    private void generateKey(String keyAlias) throws Exception {
        final KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA", "AndroidKeyStore");
        generator.initialize(
                new KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_SIGN).build());
        final KeyPair keyPair = generator.generateKeyPair();
        assertNotNull(keyPair);
    }

    private void verifyKeyGeneratedEventPresent(List<SecurityEvent> events, String alias) {
        findEvent("key generated", events,
                e -> e.getTag() == TAG_KEY_GENERATED
                        && getInt(e, SUCCESS_INDEX) == SUCCESS_VALUE
                        && getString(e, ALIAS_INDEX).contains(alias)
                        && getInt(e, UID_INDEX) == Process.myUid());
    }

    private void importKey(String alias) throws Exception{
        final KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        ks.setEntry(alias, new KeyStore.SecretKeyEntry(new SecretKeySpec(new byte[32], "AES")),
                new KeyProtection.Builder(KeyProperties.PURPOSE_ENCRYPT).build());
    }

    private void verifyKeyImportedEventPresent(List<SecurityEvent> events, String alias) {
        findEvent("key imported", events,
                e -> e.getTag() == TAG_KEY_IMPORT
                        && getInt(e, SUCCESS_INDEX) == SUCCESS_VALUE
                        && getString(e, ALIAS_INDEX).contains(alias)
                        && getInt(e, UID_INDEX) == Process.myUid());
    }

    private void deleteKey(String keyAlias) throws Exception {
        final KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        ks.deleteEntry(keyAlias);
    }

    private void verifyKeyDeletedEventPresent(List<SecurityEvent> events, String alias) {
        findEvent("key deleted", events,
                e -> e.getTag() == TAG_KEY_DESTRUCTION
                        && getInt(e, SUCCESS_INDEX) == SUCCESS_VALUE
                        && getString(e, ALIAS_INDEX).contains(alias)
                        && getInt(e, UID_INDEX) == Process.myUid());
    }

    private void installCaCert() {
        assertTrue(
                mDevicePolicyManager.installCaCert(ADMIN_RECEIVER_COMPONENT, TEST_CA.getBytes()));
    }

    private void verifyCertInstalledEventPresent(List<SecurityEvent> events) {
        findEvent("cert authority installed", events,
                e -> e.getTag() == TAG_CERT_AUTHORITY_INSTALLED
                        && getInt(e, SUCCESS_INDEX) == SUCCESS_VALUE
                        && getString(e, SUBJECT_INDEX).equals(TEST_CA_SUBJECT));
    }

    private void uninstallCaCert() {
        mDevicePolicyManager.uninstallCaCert(ADMIN_RECEIVER_COMPONENT, TEST_CA.getBytes());
    }

    private void verifyCertUninstalledEventPresent(List<SecurityEvent> events) {
        findEvent("cert authority removed", events,
                e -> e.getTag() == TAG_CERT_AUTHORITY_REMOVED
                        && getInt(e, SUCCESS_INDEX) == SUCCESS_VALUE
                        && getString(e, SUBJECT_INDEX).equals(TEST_CA_SUBJECT));
    }

    private void generatePasswordComplexityEvents() {
        DevicePolicyManager dpm = getDpmToGenerateEvents();

        dpm.setPasswordQuality(ADMIN_RECEIVER_COMPONENT, PASSWORD_QUALITY_COMPLEX);
        dpm.setPasswordMinimumLength(ADMIN_RECEIVER_COMPONENT, TEST_PWD_LENGTH);
        dpm.setPasswordMinimumLetters(ADMIN_RECEIVER_COMPONENT, TEST_PWD_CHARS);
        dpm.setPasswordMinimumNonLetter(ADMIN_RECEIVER_COMPONENT, TEST_PWD_CHARS);
        dpm.setPasswordMinimumUpperCase(ADMIN_RECEIVER_COMPONENT, TEST_PWD_CHARS);
        dpm.setPasswordMinimumLowerCase(ADMIN_RECEIVER_COMPONENT, TEST_PWD_CHARS);
        dpm.setPasswordMinimumNumeric(ADMIN_RECEIVER_COMPONENT, TEST_PWD_CHARS);
        dpm.setPasswordMinimumSymbols(ADMIN_RECEIVER_COMPONENT, TEST_PWD_CHARS);
    }

    private void generateNewStylePasswordComplexityEvents() {
        DevicePolicyManager dpm = getDpmToGenerateEvents();

        dpm.setRequiredPasswordComplexity(PASSWORD_COMPLEXITY_HIGH);
    }

    private void verifyPasswordComplexityEventsPresent(List<SecurityEvent> events) {
        final int userId = Process.myUserHandle().getIdentifier();
        // This reflects default values for password complexity event payload fields.
        final Object[] expectedPayload = new Object[] {
                ADMIN_RECEIVER_COMPONENT.getPackageName(), // admin package
                userId,                    // admin user
                userId,                    // target user
                0,                         // default password length
                0,                         // default password quality
                1,                         // default min letters
                0,                         // default min non-letters
                1,                         // default min numeric
                0,                         // default min uppercase
                0,                         // default min lowercase
                1,                         // default min symbols
        };

        // The order should be consistent with the order in generatePasswordComplexityEvents(), so
        // that the expected values change in the same sequence as when setting password policies.
        expectedPayload[PWD_QUALITY_INDEX] = PASSWORD_QUALITY_COMPLEX;
        assertPasswordComplexityEvent("set pwd quality", events, expectedPayload);
        expectedPayload[PWD_LEN_INDEX] = TEST_PWD_LENGTH;
        assertPasswordComplexityEvent("set pwd length", events, expectedPayload);
        expectedPayload[LETTERS_INDEX] = TEST_PWD_CHARS;
        assertPasswordComplexityEvent("set pwd min letters", events, expectedPayload);
        expectedPayload[NON_LETTERS_INDEX] = TEST_PWD_CHARS;
        assertPasswordComplexityEvent("set pwd min non-letters", events, expectedPayload);
        expectedPayload[UPPERCASE_INDEX] = TEST_PWD_CHARS;
        assertPasswordComplexityEvent("set pwd min uppercase", events, expectedPayload);
        expectedPayload[LOWERCASE_INDEX] = TEST_PWD_CHARS;
        assertPasswordComplexityEvent("set pwd min lowercase", events, expectedPayload);
        expectedPayload[NUMERIC_INDEX] = TEST_PWD_CHARS;
        assertPasswordComplexityEvent("set pwd min numeric", events, expectedPayload);
        expectedPayload[SYMBOLS_INDEX] = TEST_PWD_CHARS;
        assertPasswordComplexityEvent("set pwd min symbols", events, expectedPayload);
    }

    private void verifyNewStylePasswordComplexityEventPresent(List<SecurityEvent> events) {
        final int userId = Process.myUserHandle().getIdentifier();
        // This reflects default values for password complexity event payload fields.
        final Object[] expectedPayload = new Object[] {
                ADMIN_RECEIVER_COMPONENT.getPackageName(), // admin package
                userId,                    // admin user
                userId,                    // target user
                PASSWORD_COMPLEXITY_HIGH   // password complexity
        };

        findNewStylePasswordComplexityEvent("require password complexity", events, expectedPayload);
    }

    private void generateLockingPolicyEvents() {
        DevicePolicyManager dpm = mContext.getSystemService(DevicePolicyManager.class);

        if (mHasSecureLockScreen) {
            dpm.setPasswordExpirationTimeout(ADMIN_RECEIVER_COMPONENT, TEST_PWD_EXPIRATION_TIMEOUT);
            dpm.setPasswordHistoryLength(ADMIN_RECEIVER_COMPONENT, TEST_PWD_HISTORY_LENGTH);
            dpm.setMaximumFailedPasswordsForWipe(ADMIN_RECEIVER_COMPONENT, TEST_PWD_MAX_ATTEMPTS);
        }
        dpm.setKeyguardDisabledFeatures(ADMIN_RECEIVER_COMPONENT,
                KEYGUARD_DISABLE_FINGERPRINT);
        dpm.setMaximumTimeToLock(ADMIN_RECEIVER_COMPONENT, TEST_MAX_TIME_TO_LOCK);
        dpm.lockNow();
    }

    private void verifyLockingPolicyEventsPresent(List<SecurityEvent> events) {
        final int userId = Process.myUserHandle().getIdentifier();
        final String packageName = ADMIN_RECEIVER_COMPONENT.getPackageName();
        if (mHasSecureLockScreen) {
            findEvent("set password expiration", events,
                    e -> e.getTag() == TAG_PASSWORD_EXPIRATION_SET &&
                            getString(e, ADMIN_PKG_INDEX).equals(packageName) &&
                            getInt(e, ADMIN_USER_INDEX) == userId &&
                            getInt(e, TARGET_USER_INDEX) == userId &&
                            getLong(e, PWD_EXPIRATION_INDEX) == TEST_PWD_EXPIRATION_TIMEOUT);

            findEvent("set password history length", events,
                    e -> e.getTag() == TAG_PASSWORD_HISTORY_LENGTH_SET &&
                            getString(e, ADMIN_PKG_INDEX).equals(packageName) &&
                            getInt(e, ADMIN_USER_INDEX) == userId &&
                            getInt(e, TARGET_USER_INDEX) == userId &&
                            getInt(e, PWD_HIST_LEN_INDEX) == TEST_PWD_HISTORY_LENGTH);

            findEvent("set password attempts", events,
                    e -> e.getTag() == TAG_MAX_PASSWORD_ATTEMPTS_SET &&
                            getString(e, ADMIN_PKG_INDEX).equals(packageName) &&
                            getInt(e, ADMIN_USER_INDEX) == userId &&
                            getInt(e, TARGET_USER_INDEX) == userId &&
                            getInt(e, MAX_PWD_ATTEMPTS_INDEX) == TEST_PWD_MAX_ATTEMPTS);
        }

        findEvent("set keyguard disabled features", events,
                e -> e.getTag() == TAG_KEYGUARD_DISABLED_FEATURES_SET &&
                        getString(e, ADMIN_PKG_INDEX).equals(packageName) &&
                        getInt(e, ADMIN_USER_INDEX) == userId &&
                        getInt(e, TARGET_USER_INDEX) == userId &&
                        getInt(e, KEYGUARD_FEATURES_INDEX) == KEYGUARD_DISABLE_FINGERPRINT);

        findEvent("set screen lock timeout", events,
                e -> e.getTag() == TAG_MAX_SCREEN_LOCK_TIMEOUT_SET &&
                        getString(e, ADMIN_PKG_INDEX).equals(packageName) &&
                        getInt(e, ADMIN_USER_INDEX) == userId &&
                        getInt(e, TARGET_USER_INDEX) == userId &&
                        getLong(e, MAX_SCREEN_TIMEOUT_INDEX) == TEST_MAX_TIME_TO_LOCK);

        findEvent("set screen lock timeout", events,
                e -> e.getTag() == TAG_REMOTE_LOCK &&
                        getString(e, ADMIN_PKG_INDEX).equals(packageName) &&
                        getInt(e, ADMIN_USER_INDEX) == userId);
    }

    private void assertPasswordComplexityEvent(
            String description, List<SecurityEvent> events, Object[] expectedPayload) {
        int expectedSize = mIsAutomotive ? 0 : 1;
        assertNumberEvents(description, events,
                byTagAndPayload(TAG_PASSWORD_COMPLEXITY_SET, expectedPayload), expectedSize);
    }

    private void findNewStylePasswordComplexityEvent(
            String description, List<SecurityEvent> events, Object[] expectedPayload) {
        findEvent(description, events,
                byTagAndPayload(TAG_PASSWORD_COMPLEXITY_REQUIRED, expectedPayload));
    }

    private Predicate<SecurityEvent> byTagAndPayload(int expectedTag, Object[] expectedPayload) {
        return (event) -> {
            boolean tagMatch = event.getTag() == expectedTag;
            if (!tagMatch) return false;

            Object[] payload = (Object[]) event.getData();
            boolean payloadMatch = Arrays.equals(payload, expectedPayload);

            if (!payloadMatch) {
                Log.w(TAG, "Found event (id=" + event.getId() + ") with tag "
                        + eventLogtoString(event.getTag()) + ", but invalid payload: "
                        + "expected=" + Arrays.toString(expectedPayload)
                        + ", actual=" + Arrays.toString(payload));
            } else if (VERBOSE) {
                Log.v(TAG, "Found event (id=" + event.getId() + ") with tag "
                        + eventLogtoString(event.getTag()) + ", and expected payload ("
                        + Arrays.toString(payload) + ")");
            }
            return payloadMatch;
        };
    }

    private void generateUserRestrictionEvents() {
        DevicePolicyManager dpm = getDpmToGenerateEvents();

        dpm.addUserRestriction(ADMIN_RECEIVER_COMPONENT, UserManager.DISALLOW_PRINTING);
        dpm.clearUserRestriction(ADMIN_RECEIVER_COMPONENT, UserManager.DISALLOW_PRINTING);
    }

    private void verifyUserRestrictionEventsPresent(List<SecurityEvent> events) {
        findUserRestrictionEvent("set user restriction", events, TAG_USER_RESTRICTION_ADDED);
        findUserRestrictionEvent("clear user restriction", events, TAG_USER_RESTRICTION_REMOVED);
    }

    private void findUserRestrictionEvent(String description, List<SecurityEvent> events, int tag) {
        final int userId = Process.myUserHandle().getIdentifier();
        findEvent(description, events,
                e -> e.getTag() == tag &&
                        getString(e, ADMIN_PKG_INDEX).equals(
                                ADMIN_RECEIVER_COMPONENT.getPackageName()) &&
                        getInt(e, ADMIN_USER_INDEX) == userId &&
                        UserManager.DISALLOW_PRINTING.equals(getString(e, USER_RESTRICTION_INDEX)));
    }

    private void generateCameraPolicyEvents() {
        DevicePolicyManager dpm = getDpmToGenerateEvents();

        dpm.setCameraDisabled(ADMIN_RECEIVER_COMPONENT, true);
        dpm.setCameraDisabled(ADMIN_RECEIVER_COMPONENT, false);
    }

    private void verifyCameraPolicyEvents(List<SecurityEvent> events) {
        final int userId = Process.myUserHandle().getIdentifier();

        findEvent("set camera disabled", events,
                e -> e.getTag() == TAG_CAMERA_POLICY_SET &&
                        getString(e, ADMIN_PKG_INDEX).equals(
                                ADMIN_RECEIVER_COMPONENT.getPackageName()) &&
                        getInt(e, ADMIN_USER_INDEX) == userId &&
                        getInt(e, TARGET_USER_INDEX) == userId &&
                        getInt(e, CAMERA_DISABLED_INDEX) == 1);

        findEvent("set camera enabled", events,
                e -> e.getTag() == TAG_CAMERA_POLICY_SET &&
                        getString(e, ADMIN_PKG_INDEX).equals(
                                ADMIN_RECEIVER_COMPONENT.getPackageName()) &&
                        getInt(e, ADMIN_USER_INDEX) == userId &&
                        getInt(e, TARGET_USER_INDEX) == userId &&
                        getInt(e, CAMERA_DISABLED_INDEX) == 0);
    }

    private DevicePolicyManager getDpmToGenerateEvents() {
        // It must use the dpm for the current user, as mDevicePolicyManager tunnels the calls to
        // the device owner user on headless system user, which would cause a mismatch in the events
        return mContext.getSystemService(DevicePolicyManager.class);
    }

    private static String eventLogtoString(int log) {
        return DebugUtils.constantToString(SecurityLog.class, "TAG_", log);
    }

    private static String toString(SecurityEvent event) {
        return "Event[id=" + event.getId() + ",tag=" + eventLogtoString(event.getTag()) + "]";
    }

    private void dumpSecurityLogs(List<SecurityEvent> events) {
        Log.d(TAG, "Security events dump (" + events.size() + " events):");
        events.forEach((event) -> Log.d(TAG, toString(event)));
    }
}
