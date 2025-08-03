/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.telecom.cts;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.DropBoxManager;
import android.provider.CallLog;
import android.telecom.Call;
import android.telecom.Connection;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccount;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class EmergencyCallTests extends BaseTelecomTestWithMockServices {
    private static final String TAG = EmergencyCallTests.class.getSimpleName();
    // mirrors constant in PhoneAccountRegistrar called MAX_PHONE_ACCOUNT_REGISTRATIONS
    public static final int MAX_PHONE_ACCOUNT_REGISTRATIONS = 10;
    public static final int MAX_LINES_TO_VERIFY_IN_DUMPSYS_OUTPUT = 20;
    private static final String DROPBOX_TAG = "ecall_diagnostic_data";
    private static final String DUMPSYS_COMMAND = "dumpsys telecom";
    private static final int MIN_LINES_PER_DROPBOX_ENTRY = 15;
    private static final int MAX_READ_BYTES_PER_DROP_BOX_ENTRY = 5000;
    private static final long DAYS_BACK_TO_SEARCH_EMERGENCY_DROP_BOX_ENTRIES_IN_MS =
            30L * 24L * 60L * 60L * 1000L;
    private static final String DIAG_ERROR_MSG = "DiagnosticDataCollector error executing cmd";
    private CountDownLatch mLatchForDropBox;
    private IntentFilter dropBoxIntentFilter;

    @Override
    public void setUp() throws Exception {
        // Sets up this package as default dialer in super.
        super.setUp();
        NewOutgoingCallBroadcastReceiver.reset();
        if (!mShouldTestTelecom) return;
        setupConnectionService(null, FLAG_REGISTER | FLAG_ENABLE);
        setupForEmergencyCalling(TEST_EMERGENCY_NUMBER);
        TestUtils.executeShellCommand(getInstrumentation(),
                "device_config put telephony "
                        + "enable_telephony_dump_collection_for_emergency_call_diagnostics true");
        TestUtils.executeShellCommand(getInstrumentation(),
                "device_config put telephony "
                        + "enable_logcat_collection_for_emergency_call_diagnostics true");
        TestUtils.executeShellCommand(getInstrumentation(),
                "device_config put telephony "
                        + "enable_telecom_dump_collection_for_emergency_call_diagnostics true");
        mLatchForDropBox = new CountDownLatch(3); //expect 3 entries
        dropBoxIntentFilter = new IntentFilter();
        dropBoxIntentFilter.addAction(DropBoxManager.ACTION_DROPBOX_ENTRY_ADDED);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     * Tests a scenario where an emergency call could fail due to the presence of
     * invalid
     * {@link PhoneAccount} data.
     * The seed and quantity for
     * {@link TestUtils#generateRandomPhoneAccounts(long, int, String,
     * String)} is chosen to represent a set of phone accounts which is known in AOSP
     * to cause a
     * failure placing an emergency call.  {@code 52L} was chosen as a random seed
     * and {@code 50}
     * was chosen as the set size for {@link PhoneAccount}s as these were observed in
     * repeated test
     * invocations to induce the failure method.
     */
    public void testEmergencyCallFailureDueToInvalidPhoneAccounts() throws Exception {
        if (!mShouldTestTelecom) return;

        // needed in order to call mTelecomManager.getPhoneAccountsForPackage()
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity("android.permission.READ_PRIVILEGED_PHONE_STATE");

        // determine the number of phone accounts already registered to this package.
        int phoneAccountsRegisteredAlready =
                mTelecomManager.getPhoneAccountsForPackage().size();

        // now, determine the number of accounts remaining
        int numberOfAccountsThatCanBeRegistered =
                MAX_PHONE_ACCOUNT_REGISTRATIONS - phoneAccountsRegisteredAlready;

        // create the remaining phone accounts allowed
        ArrayList<PhoneAccount> accounts = TestUtils.generateRandomPhoneAccounts(52L,
                numberOfAccountsThatCanBeRegistered,
                TestUtils.PACKAGE, TestUtils.COMPONENT);

        try {
            // register the phone accounts
            accounts.stream().forEach(a -> mTelecomManager.registerPhoneAccount(a));
            // assert all were registered successfully
            assertTrue(mTelecomManager.getPhoneAccountsForPackage().size()
                    >= MAX_PHONE_ACCOUNT_REGISTRATIONS);

            // The existing start emergency call test is impacted if there is a failure due to
            // excess phone accounts being present.
            testStartEmergencyCall();
        } finally {
            accounts.stream().forEach(d -> mTelecomManager.unregisterPhoneAccount(
                    d.getAccountHandle()));
            // cleanup permission that was added
            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation().dropShellPermissionIdentity();
        }
    }

    /**
     * Place an outgoing emergency call and ensure it is started successfully.
     */
    public void testStartEmergencyCall() throws Exception {
        if (!mShouldTestTelecom) return;
        Connection conn = placeAndVerifyEmergencyCall(true /*supportsHold*/);
        Call eCall = getInCallService().getLastCall();
        assertCallState(eCall, Call.STATE_DIALING);

        assertIsInCall(true);
        assertIsInManagedCall(true);
        conn.setActive();
        conn.setDisconnected(new DisconnectCause(DisconnectCause.LOCAL));
        conn.destroy();
        assertConnectionState(conn, Connection.STATE_DISCONNECTED);
        assertIsInCall(false);
    }

    private void verifyEmergencyDropBoxEntriesCreatedAndDumped(long entriesAfterTime)
            throws Exception {
        int totalEntries = 0;
        DropBoxManager dm = mContext.getSystemService(DropBoxManager.class);
        DropBoxManager.Entry entry;

        //capture telecom dump
        String dumpOutput = TestUtils.executeShellCommand(getInstrumentation(),
                DUMPSYS_COMMAND);

        entry = dm.getNextEntry(DROPBOX_TAG, entriesAfterTime);
        assertNotNull("No emergency diagnostic dropbox entries found", entry);
        while (entry != null) {
            long entryTime = entry.getTimeMillis();
            totalEntries++;
            String content[] = entry.getText(MAX_READ_BYTES_PER_DROP_BOX_ENTRY).split(
                    System.lineSeparator());
            assertNotNull("Dropbox entry content is null", content);
            assertTrue(content.length > MIN_LINES_PER_DROPBOX_ENTRY);
            int lineCount = 1;
            int foundCount = 0;
            for (String line : content) {
                assertFalse(line.contains(DIAG_ERROR_MSG));
                //verify that telecom dumpsys output also has this data
                if (lineCount++ < MAX_LINES_TO_VERIFY_IN_DUMPSYS_OUTPUT) {
                    //we only check top x lines to verify presence in dumpsys output
                    if (dumpOutput.contains(line)) {
                        foundCount++;
                    }
                }
            }
            assertTrue("Should have found ~50% of expected lines in dropbox",
                    foundCount >= MAX_LINES_TO_VERIFY_IN_DUMPSYS_OUTPUT / 2);
            entry = dm.getNextEntry(DROPBOX_TAG, entryTime);
            if(totalEntries >= 3)
                break;
        }
    }

    /**
     * Place an outgoing emergency call fail it to trigger persisting of diagnostic data
     */
    public void testEmergencyCallFailureCreatesDropboxEntries() throws Exception {
        if (!mShouldTestTelecom  || !TestUtils.hasTelephonyFeature(mContext)) return;
        long startTime = System.currentTimeMillis()
                - DAYS_BACK_TO_SEARCH_EMERGENCY_DROP_BOX_ENTRIES_IN_MS;
        mContext.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String tag =
                        intent.getStringExtra(DropBoxManager.EXTRA_TAG);
                if (tag.equals(DROPBOX_TAG)) {
                    Log.d(TAG, "entry added to dropbox");
                    mLatchForDropBox.countDown();
                }
            }
        }, dropBoxIntentFilter);
        Connection conn = placeAndVerifyEmergencyCall(true /*supportsHold*/);
        Call eCall = getInCallService().getLastCall();
        assertCallState(eCall, Call.STATE_DIALING);

        assertIsInCall(true);
        assertIsInManagedCall(true);
        //error
        conn.setDisconnected(new DisconnectCause(DisconnectCause.REJECTED));
        conn.destroy();
        assertConnectionState(conn, Connection.STATE_DISCONNECTED);
        assertIsInCall(false);

        //although collection of logcat/persisting takes only 1 sec
        //some AVDs are very slow and I see up to 10 seconds from call
        //transitioning to disconnected to actual data being collected
        Log.d(TAG, "Starting wait for dropbox latch");
        boolean result = mLatchForDropBox.await(13000L, TimeUnit.MILLISECONDS);
        assertTrue("Latch did not reach zero", result);
        Log.d(TAG, "verify entries after timestamp: " + startTime);
        verifyEmergencyDropBoxEntriesCreatedAndDumped(startTime);
    }

    /**
     * Place an outgoing emergency call and ensure any incoming call is rejected
     * automatically and
     * logged in call log as a new missed call.
     *
     * Note: PSAPs have requirements that active emergency calls can not be put on hold, so
     * if for
     * some reason an incoming emergency callback happens while on another emergency call,
     * that call
     * will automatically be rejected as well.
     */
    public void testOngoingEmergencyCallAndReceiveIncomingCall() throws Exception {
        if (!mShouldTestTelecom) return;

        Connection eConnection = placeAndVerifyEmergencyCall(true /*supportsHold*/);
        assertIsInCall(true);
        assertIsInManagedCall(true);
        Call eCall = getInCallService().getLastCall();
        assertCallState(eCall, Call.STATE_DIALING);
        eConnection.setActive();
        assertCallState(eCall, Call.STATE_ACTIVE);

        Uri normalCallNumber = createRandomTestNumber();
        addAndVerifyNewFailedIncomingCall(normalCallNumber, null);
        assertCallState(eCall, Call.STATE_ACTIVE);

        // Notify as missed instead of rejected, since the user did not explicitly reject.
        verifyCallLogging(normalCallNumber, CallLog.Calls.MISSED_TYPE,
                TestUtils.TEST_PHONE_ACCOUNT_HANDLE);
    }

    /**
     * Receive an incoming ringing call and place an emergency call. The ringing call should be
     * rejected and logged as a new missed call.
     */
    public void testIncomingRingingCallAndPlaceEmergencyCall() throws Exception {
        if (!mShouldTestTelecom) return;

        Uri normalCallNumber = createRandomTestNumber();
        addAndVerifyNewIncomingCall(normalCallNumber, null);
        MockConnection incomingConnection = verifyConnectionForIncomingCall();
        // Ensure destroy happens after emergency call is placed to prevent unbind -> rebind.
        incomingConnection.disableAutoDestroy();
        Call incomingCall = getInCallService().getLastCall();
        assertCallState(incomingCall, Call.STATE_RINGING);

        // Do not support holding incoming call for emergency call.
        Connection eConnection = placeAndVerifyEmergencyCall(false /*supportsHold*/);
        Call eCall = getInCallService().getLastCall();
        assertCallState(eCall, Call.STATE_DIALING);

        incomingConnection.destroy();
        assertConnectionState(incomingConnection, Connection.STATE_DISCONNECTED);
        assertCallState(incomingCall, Call.STATE_DISCONNECTED);

        eConnection.setActive();
        assertCallState(eCall, Call.STATE_ACTIVE);

        // Notify as missed instead of rejected, since the user did not explicitly reject.
        verifyCallLogging(normalCallNumber, CallLog.Calls.MISSED_TYPE,
                TestUtils.TEST_PHONE_ACCOUNT_HANDLE);
    }

    /**
     * While on an outgoing call, receive an incoming ringing call and then place an
     * emergency call.
     * The other two calls should stay active while the ringing call should be rejected and
     * logged
     * as a new missed call.
     */
    public void testActiveCallAndIncomingRingingCallAndPlaceEmergencyCall() throws Exception {
        if (!mShouldTestTelecom) return;

        Uri normalOutgoingCallNumber = createRandomTestNumber();
        Bundle extras = new Bundle();
        extras.putParcelable(TestUtils.EXTRA_PHONE_NUMBER, normalOutgoingCallNumber);
        placeAndVerifyCall(extras);
        Connection outgoingConnection = verifyConnectionForOutgoingCall();
        Call outgoingCall = getInCallService().getLastCall();
        outgoingConnection.setActive();
        assertCallState(outgoingCall, Call.STATE_ACTIVE);

        Uri normalIncomingCallNumber = createRandomTestNumber();
        addAndVerifyNewIncomingCall(normalIncomingCallNumber, null);
        MockConnection incomingConnection = verifyConnectionForIncomingCall();
        // Ensure destroy happens after emergency call is placed to prevent unbind -> rebind.
        incomingConnection.disableAutoDestroy();
        Call incomingCall = getInCallService().getLastCall();
        assertCallState(incomingCall, Call.STATE_RINGING);

        // Do not support holding incoming call for emergency call.
        Connection eConnection = placeAndVerifyEmergencyCall(false /*supportsHold*/);
        Call eCall = getInCallService().getLastCall();
        assertCallState(eCall, Call.STATE_DIALING);

        incomingConnection.destroy();
        assertConnectionState(incomingConnection, Connection.STATE_DISCONNECTED);
        assertCallState(incomingCall, Call.STATE_DISCONNECTED);

        eConnection.setActive();
        assertCallState(eCall, Call.STATE_ACTIVE);

        // Notify as missed instead of rejected, since the user did not explicitly reject.
        verifyCallLogging(normalIncomingCallNumber, CallLog.Calls.MISSED_TYPE,
                TestUtils.TEST_PHONE_ACCOUNT_HANDLE);
    }

    public void testEmergencyCallAndNoAdditionalCallPermitted() throws Exception {
        if (!mShouldTestTelecom) return;

        Connection eConnection = placeAndVerifyEmergencyCall(true);
        Call eCall = getInCallService().getLastCall();
        assertCallState(eCall, Call.STATE_DIALING);
        eConnection.setActive();
        assertCallState(eCall, Call.STATE_ACTIVE);

        assertIsOutgoingCallPermitted(false, TestUtils.TEST_PHONE_ACCOUNT_HANDLE);
        assertIsIncomingCallPermitted(false, TestUtils.TEST_PHONE_ACCOUNT_HANDLE);
    }
}
