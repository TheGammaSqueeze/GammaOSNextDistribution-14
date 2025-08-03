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
 * limitations under the License
 */

package android.telecom.cts;

import static android.telecom.cts.TestUtils.TEST_SELF_MANAGED_HANDLE_1;
import static android.telecom.cts.TestUtils.TEST_SELF_MANAGED_PHONE_ACCOUNT_1;
import static android.telecom.cts.TestUtils.TEST_SIM_PHONE_ACCOUNT;
import static android.telecom.cts.TestUtils.TEST_SIM_PHONE_ACCOUNT_2;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import android.net.Uri;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.util.Log;

import com.android.server.telecom.flags.Flags;

import java.util.ArrayList;
import java.util.List;
/**
 * Tests use of APIs related to changing the default outgoing phone account.
 */
public class DefaultPhoneAccountTest extends BaseTelecomTestWithMockServices {
    private static final String TAG = DefaultPhoneAccountTest.class.getSimpleName();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = getInstrumentation().getContext();
        if (!TestUtils.hasTelephonyFeature(mContext)) {
            mShouldTestTelecom = false;
        }
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     * Verifies that {@link TelecomManager#getUserSelectedOutgoingPhoneAccount()} is able to
     * retrieve the user-selected outgoing phone account.
     * Given that there is a user-selected default, also verifies that
     * {@link TelecomManager#getDefaultOutgoingPhoneAccount(String)} reports this value as well.
     * Note: This test depends on
     * {@code TelecomManager#setUserSelectedOutgoingPhoneAccount(PhoneAccountHandle)} being run
     * through the telecom shell command in order to change the user-selected default outgoing
     * account.
     * @throws Exception
     */
    public void testDefaultIsSet() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        // Make sure to set the default outgoing phone account to the new connection service
        setupConnectionService(null, FLAG_REGISTER | FLAG_ENABLE | FLAG_SET_DEFAULT);

        PhoneAccountHandle handle = mTelecomManager.getUserSelectedOutgoingPhoneAccount();
        assertEquals(TestUtils.TEST_PHONE_ACCOUNT_HANDLE, handle);

        PhoneAccountHandle defaultOutgoing = mTelecomManager.getDefaultOutgoingPhoneAccount(
                PhoneAccount.SCHEME_TEL);
        assertEquals(TestUtils.TEST_PHONE_ACCOUNT_HANDLE, defaultOutgoing);
    }

    /**
     * Verifies that {@link TelecomManager#getUserSelectedOutgoingPhoneAccount()} is able to
     * retrieve the user-selected outgoing phone account.
     * Given that there is a user-selected default, also verifies that
     * {@link TelecomManager#getDefaultOutgoingPhoneAccount(String)} reports this value as well.
     * @throws Exception
     */
    public void testSetUserSelectedOutgoingPhoneAccount() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        // Make sure to set the default outgoing phone account to the new connection service
        setupConnectionService(null, FLAG_REGISTER | FLAG_ENABLE);

        PhoneAccountHandle previousOutgoingPhoneAccount =
                mTelecomManager.getUserSelectedOutgoingPhoneAccount();

        try {
            // Use TelecomManager API to set the outgoing phone account.
            runWithShellPermissionIdentity(() ->
                    mTelecomManager.setUserSelectedOutgoingPhoneAccount(
                            TestUtils.TEST_PHONE_ACCOUNT_HANDLE));

            PhoneAccountHandle handle = mTelecomManager.getUserSelectedOutgoingPhoneAccount();
            assertEquals(TestUtils.TEST_PHONE_ACCOUNT_HANDLE, handle);

            PhoneAccountHandle defaultOutgoing = mTelecomManager.getDefaultOutgoingPhoneAccount(
                    PhoneAccount.SCHEME_TEL);
            assertEquals(TestUtils.TEST_PHONE_ACCOUNT_HANDLE, defaultOutgoing);
        } finally {
            // Restore the default outgoing phone account.
            TestUtils.setDefaultOutgoingPhoneAccount(getInstrumentation(),
                    previousOutgoingPhoneAccount);
        }
    }

    /**
     * Verifies operation of the {@link TelecomManager#getDefaultOutgoingPhoneAccount(String)} API
     * where there is NO user selected default outgoing phone account.
     * In AOSP, this mimics the user having changed the
     * Phone --> Settings --> Call Settings --> Calling accounts --> Make Calls With
     * option to "Ask first".
     *
     * The test assumes that a device either has a single sim, or has multiple sims.
     * In either case, it registers another TEL outgoing calling account.
     *
     * We can expect two things:
     * 1. {@link TelecomManager#getUserSelectedOutgoingPhoneAccount()} returns null, since the
     *    "ask first" option was chosen.
     * 2. {@link TelecomManager#getUserSelectedOutgoingPhoneAccount()} returns null, since there is
     *    now 2 or more potential outgoing phone accounts with the TEL scheme.
     * @throws Exception
     */
    public void testGetDefaultOutgoingNoUserSelected() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }

        PhoneAccountHandle previousOutgoingPhoneAccount =
                mTelecomManager.getUserSelectedOutgoingPhoneAccount();

        // Clear the default outgoing phone account; this is the same as saying "ask every time" in
        // the user settings.
        TestUtils.setDefaultOutgoingPhoneAccount(getInstrumentation(),
                null /* clear default */);
        try {
            // Register another TEL URI phone account; since we expect devices to have at minimum
            // 1 sim, this ensures that we have a scenario where there are multiple potential
            // outgoing phone accounts with the TEL scheme.
            setupConnectionService(null, FLAG_REGISTER | FLAG_ENABLE);

            // There should be NO user selected default outgoing account (we cleared it).
            PhoneAccountHandle handle = mTelecomManager.getUserSelectedOutgoingPhoneAccount();
            assertEquals(null, handle);

            // There should be multiple potential TEL phone accounts now, so we expect null here.
            PhoneAccountHandle defaultOutgoing = mTelecomManager.getDefaultOutgoingPhoneAccount(
                    PhoneAccount.SCHEME_TEL);
            assertEquals(null, defaultOutgoing);
        } finally {
            // Restore the default outgoing phone account.
            TestUtils.setDefaultOutgoingPhoneAccount(getInstrumentation(),
                    previousOutgoingPhoneAccount);
        }
    }

    /**
     * Verifies correct operation of the
     * {@link TelecomManager#getDefaultOutgoingPhoneAccount(String)} API.
     * The purpose of this CTS test is to verify the following scenarios:
     * 1. Where there is NO user selected default outgoing phone account and there is a single
     *    potential phone account, that phone account should be returned.
     * 2. Where there is NO user selected default outgoing phone account and there are multiple
     *    potential phone accounts, null should be returned.
     * This test performs this operation using a test URI scheme to remove dependencies on the
     * number of potential sims in a device, however the test cases above should pass even if the
     * TEL uri scheme was being tested.
     * @throws Exception
     */
    public void testGetDefaultOutgoingPhoneAccountOneOrMany() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }

        PhoneAccountHandle previousOutgoingPhoneAccount =
                mTelecomManager.getUserSelectedOutgoingPhoneAccount();

        // Clear the default outgoing phone account; this is the same as saying "ask every time" in
        // the user settings.
        TestUtils.setDefaultOutgoingPhoneAccount(getInstrumentation(),
                null /* clear default */);

        try {
            // Lets register a new phone account using a test URI scheme 'foobuzz' (this avoids
            // conflicts with any sims on the device).
            registerAndEnablePhoneAccount(TestUtils.TEST_DEFAULT_PHONE_ACCOUNT_1);

            // There should be NO user selected default outgoing account (we cleared it above).
            PhoneAccountHandle handle = mTelecomManager.getUserSelectedOutgoingPhoneAccount();
            assertEquals(null, handle);

            // There should be a single potential phone account in the 'foobuzz' scheme, so it
            // should be reported as the default outgoing phone account.
            PhoneAccountHandle defaultOutgoing = mTelecomManager.getDefaultOutgoingPhoneAccount(
                    TestUtils.TEST_URI_SCHEME);
            assertEquals(TestUtils.TEST_DEFAULT_PHONE_ACCOUNT_HANDLE_1, defaultOutgoing);

            // Next, lets register another new phone account using the test URI scheme 'foobuzz'.
            registerAndEnablePhoneAccount(TestUtils.TEST_DEFAULT_PHONE_ACCOUNT_2);

            // There should still be NO user selected default outgoing account (we cleared it
            // above).
            handle = mTelecomManager.getUserSelectedOutgoingPhoneAccount();
            assertEquals(null, handle);

            // Now that there are two potential outgoing accounts in the same scheme and nothing is
            // chosen as the default, the default outgoing phone account should be "null".
            defaultOutgoing = mTelecomManager.getDefaultOutgoingPhoneAccount(
                    TestUtils.TEST_URI_SCHEME);
            assertEquals(null, defaultOutgoing);
        } finally {
            mTelecomManager.unregisterPhoneAccount(TestUtils.TEST_DEFAULT_PHONE_ACCOUNT_HANDLE_1);
            mTelecomManager.unregisterPhoneAccount(TestUtils.TEST_DEFAULT_PHONE_ACCOUNT_HANDLE_2);

            // Restore the default outgoing phone account.
            TestUtils.setDefaultOutgoingPhoneAccount(getInstrumentation(),
                    previousOutgoingPhoneAccount);
        }
    }

    /**
     * Verifies the DUT can successfully place a self-managed call when the default outgoing account
     * handle has the capability {@link android.telecom.PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION}
     * (aka the default outgoing PhoneAccount is a sim/e-sim).
     */
    public void testSelfManagedCallWithSimBasedPhoneAccountAsDefault() throws Exception {
        if (!mShouldTestTelecom || !Flags.onlyUpdateTelephonyOnValidSubIds()) {
            return;
        }
        // avoid the CtsConnectionService binding which acts as a connection manager
        PhoneAccount simCallManagerAcct = maybeUnregisterConnectionManagerAccount();
        // temp save the DUT default
        PhoneAccountHandle DUT_default_out = mTelecomManager.getUserSelectedOutgoingPhoneAccount();
        // create the list of accounts that need to registered for this test
        ArrayList<PhoneAccount> accounts = new ArrayList<>();
        accounts.add(TEST_SIM_PHONE_ACCOUNT);
        accounts.add(TEST_SELF_MANAGED_PHONE_ACCOUNT_1);

        try {
            registerAccountsAndVerify(accounts);
            setDefaultOutgoingPhoneAccountAndVerify(TestUtils.TEST_SIM_PHONE_ACCOUNT_HANDLE);
            placeAndVerifySelfManagedCall(TestUtils.TEST_SELF_MANAGED_HANDLE_1, getTestNumber());
        } finally {
            cleanupDefaultOutgoingAlteringTest(accounts, DUT_default_out, simCallManagerAcct);
        }
    }

    /**
     * Verifies the DUT can successfully place a self-managed call when the default outgoing account
     * handle is null (meaning there is no call preference and the user will be prompted to select
     * an account when placing a call).
     */
    public void testSelfManagedCallWithNoCallPreferenceAsDefault() throws Exception {
        if (!mShouldTestTelecom || !Flags.onlyUpdateTelephonyOnValidSubIds()) {
            return;
        }
        // avoid the CtsConnectionService binding which acts as a connection manager
        PhoneAccount simCallManagerAcct = maybeUnregisterConnectionManagerAccount();
        // temp save the DUT default
        PhoneAccountHandle DUT_default_out = mTelecomManager.getUserSelectedOutgoingPhoneAccount();
        // create the list of accounts that need to registered for this test
        ArrayList<PhoneAccount> accounts = new ArrayList<>();
        accounts.add(TEST_SIM_PHONE_ACCOUNT);
        accounts.add(TEST_SIM_PHONE_ACCOUNT_2);
        accounts.add(TEST_SELF_MANAGED_PHONE_ACCOUNT_1);

        try {
            registerAccountsAndVerify(accounts);
            setDefaultOutgoingPhoneAccountAndVerify(null);
            placeAndVerifySelfManagedCall(TEST_SELF_MANAGED_HANDLE_1, getTestNumber());
        } finally {
            cleanupDefaultOutgoingAlteringTest(accounts, DUT_default_out, simCallManagerAcct);
        }
    }

    /**
     * Verifies the DUT can place a self-managed call when the default outgoing phoneAccount is
     * a sim based account and there are multiple sim based accounts registered and enabled.
     */
    public void testSelfManagedCallWithMultipleSimBasedAccountsActiveAndAsDefault()
            throws Exception {
        if (!mShouldTestTelecom || !Flags.onlyUpdateTelephonyOnValidSubIds()) {
            return;
        }
        // avoid the CtsConnectionService binding which acts as a connection manager
        PhoneAccount simCallManagerAcct = maybeUnregisterConnectionManagerAccount();
        // temp save the DUT default
        PhoneAccountHandle DUT_default_out = mTelecomManager.getUserSelectedOutgoingPhoneAccount();
        // create the list of accounts that need to registered for this test
        ArrayList<PhoneAccount> accounts = new ArrayList<>();
        accounts.add(TEST_SIM_PHONE_ACCOUNT);
        accounts.add(TEST_SIM_PHONE_ACCOUNT_2);
        accounts.add(TEST_SELF_MANAGED_PHONE_ACCOUNT_1);

        try {
            registerAccountsAndVerify(accounts);
            setDefaultOutgoingPhoneAccountAndVerify(TestUtils.TEST_SIM_PHONE_ACCOUNT_HANDLE_2);
            placeAndVerifySelfManagedCall(TEST_SELF_MANAGED_HANDLE_1, getTestNumber());
        } finally {
            cleanupDefaultOutgoingAlteringTest(accounts, DUT_default_out, simCallManagerAcct);
        }
    }

    /**
     * ===========================================================================================
     *                                    Helpers
     * ===========================================================================================
     */

    /**
     * query the sim call manager for the DUT and unregister it if non-null. This is useful since
     * Telecom cts process will bind to the sim call manager and add VoIP calls which will cause
     * unwanted behavior.
     *
     * @return potential sim call manager account
     */
    private PhoneAccount maybeUnregisterConnectionManagerAccount() {
        PhoneAccountHandle simCallManagerHandle = mTelecomManager.getSimCallManager();
        if (simCallManagerHandle != null) {
            Log.i(TAG, String.format(
                    "maybeUnregisterConnectionManagerAccount: unregistering=[%s] for test",
                    simCallManagerHandle));
            PhoneAccount simCallManagerAcct = mTelecomManager.getPhoneAccount(simCallManagerHandle);
            mTelecomManager.unregisterPhoneAccount(simCallManagerHandle);
            return simCallManagerAcct;
        }
        return null;
    }

    /**
     * reset the DUT to the previous state it was in before running the test
     *
     * @param accountsToUnregister unregister the accounts needed for the test
     * @param DUT_default_out      reset the default outgoing account
     * @param simCallManager       maybe re-register the account that was acting as the sim call
     *                             manager
     * @throws Exception if there is an issue re-registering the simCallManager account
     */
    public void cleanupDefaultOutgoingAlteringTest(List<PhoneAccount> accountsToUnregister,
            PhoneAccountHandle DUT_default_out, PhoneAccount simCallManager) throws Exception {
        // unregister any accounts created in order to change the default outgoing account
        unregisterAccountsAndVerify(accountsToUnregister);
        // if the test required the sim call manager account to be unregistered, re-register it
        if (simCallManager != null) {
            mTelecomManager.registerPhoneAccount(simCallManager);
        }
        // reset the DUTs default outgoing calling account to the value before the test ran
        setDefaultOutgoingPhoneAccountAndVerify(DUT_default_out);
    }

    private void placeAndVerifySelfManagedCall(PhoneAccountHandle handle, Uri address)
            throws Exception {
        // ensure the phoneAccountHandle can place outgoing calls before attempting to place one
        assertIsOutgoingCallPermitted(true, handle);
        // send the request to place the call
        TestUtils.placeOutgoingCall(getInstrumentation(), mTelecomManager, handle, address);
        // ensure Telecom bound to the self-managed CS
        if (!CtsSelfManagedConnectionService.waitForBinding()) {
            fail("Could not bind to Self-Managed ConnectionService");
        }
        // fetch the connection object to alter the call state
        SelfManagedConnection connection = TestUtils.waitForAndGetConnection(address);

        try {
            assertNotNull("Connection should NOT be null.", connection);
            assertFalse("Connection should be outgoing.", connection.isIncomingCall());
            setActiveAndVerify(connection);
        } finally {
            setDisconnectedAndVerify(connection);
        }
    }

    private void setActiveAndVerify(SelfManagedConnection connection) {
        // Set the connection active.
        connection.setActive();
        // Check with Telecom if we're in a call.
        assertIsInCall(true);
        assertIsInManagedCall(false);
    }

    private void setDisconnectedAndVerify(SelfManagedConnection connection) {
        if (connection != null) {
            connection.disconnectAndDestroy();
            assertIsInCall(false);
            assertIsInManagedCall(false);
        }
    }
}
