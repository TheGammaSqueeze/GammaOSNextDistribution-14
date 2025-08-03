/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.telecom.cts.cuj.app.integration;

import static android.telecom.cts.apps.TelecomTestApp.SELF_MANAGED_CS_MAIN_ACCOUNT_CUSTOM;
import static android.telecom.cts.apps.TelecomTestApp.TRANSACTIONAL_MAIN_SUPPLEMENTARY_ACCOUNT;
import static android.telecom.cts.apps.TelecomTestApp.TRANSACTIONAL_APP_SUPPLEMENTARY_HANDLE;
import static android.telecom.cts.apps.TelecomTestApp.TRANSACTIONAL_PACKAGE_NAME;
import static android.telecom.cts.apps.TelecomTestApp.ConnectionServiceVoipAppMain;
import static android.telecom.cts.apps.TelecomTestApp.TransactionalVoipAppMain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.os.RemoteException;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telecom.cts.apps.AppControlWrapper;
import android.telecom.cts.cuj.BaseAppVerifier;

import com.android.server.telecom.flags.Flags;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.List;

@RunWith(JUnit4.class)
public class PhoneAccountTest extends BaseAppVerifier {
    /*********************************************************************************************
     *                           ConnectionServiceVoipAppMain
     /*********************************************************************************************/

    /**
     * Test the scenario where a self-managed application that registers a default phone account at
     * startup can retrieve their account and unregister it.
     *
     * <h3> Test Steps: </h3>
     * <ul>
     *  1. create a self-managed {@link android.telecom.PhoneAccount }
     * <p>
     *  2. register the managed {@link android.telecom.PhoneAccount } when the application is
     *  started via {@link TelecomManager#registerPhoneAccount(PhoneAccount)}
     * <p>
     *  3. verify the {@link PhoneAccount} is retrievable via
     *  {@link TelecomManager#getOwnSelfManagedPhoneAccounts()}
     * <p>
     *  4. unregister the {@link PhoneAccount} via
     *  {@link TelecomManager#unregisterPhoneAccount(PhoneAccountHandle)}
     * <p>
     *  5. verify the account is unregistered
     *  </ul>
     */
    @Test
    public void testAccountTest_ConnectionServiceVoipAppMain() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        AppControlWrapper voipCsApp = null;
        try {
            voipCsApp = bindToApp(ConnectionServiceVoipAppMain);
            PhoneAccount account = verifyDefaultAccountIsRegistered(voipCsApp);
            unregisterDefaultAndVerify(voipCsApp, account);
        } finally {
            tearDownApp(voipCsApp);
        }
    }

    /**
     * Test the scenario where a self-managed application that registers a secondary phone account
     * on top of an already existing phone account.  The application should also be able to query
     * and unregister it.
     *
     * <h3> Test Steps: </h3>
     * <ul>
     *  1. create a secondary  {@link android.telecom.PhoneAccount }
     * <p>
     *  2. register the managed {@link android.telecom.PhoneAccount } when the application is
     *  started via {@link TelecomManager#registerPhoneAccount(PhoneAccount)}
     * <p>
     *  3. verify the {@link PhoneAccount} is retrievable via
     *  {@link TelecomManager#getOwnSelfManagedPhoneAccounts()}
     * <p>
     *  4. unregister the {@link PhoneAccount} via
     *  {@link TelecomManager#unregisterPhoneAccount(PhoneAccountHandle)}
     * <p>
     *  5. verify the account is unregistered
     *  </ul>
     */
    @Test
    public void testCustomAccountTest_ConnectionServiceVoipAppMain() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        AppControlWrapper voipCsApp = null;
        try {
            voipCsApp = bindToApp(ConnectionServiceVoipAppMain);

            registerAcctAndVerify(
                    voipCsApp,
                    SELF_MANAGED_CS_MAIN_ACCOUNT_CUSTOM,
                    2 /* numOfExpectedAccounts */);

            unregisterAcctAndVerify(
                    voipCsApp,
                    SELF_MANAGED_CS_MAIN_ACCOUNT_CUSTOM,
                    1 /* numOfExpectedAccounts */);
        } finally {
            tearDownApp(voipCsApp);
        }
    }

    /*********************************************************************************************
     *                       TransactionalVoipAppMain
     /*********************************************************************************************/

    /**
     * Test the scenario where a self-managed application that registers a default phone account at
     * startup can retrieve their account and unregister it.
     *
     * <h3> Test Steps: </h3>
     * <ul>
     *  1. create a managed  {@link android.telecom.PhoneAccount }
     * <p>
     *  2. register the managed {@link android.telecom.PhoneAccount } when the application is
     *  started via {@link TelecomManager#registerPhoneAccount(PhoneAccount)}
     * <p>
     *  3. verify the {@link PhoneAccount} is retrievable via
     *  {@link TelecomManager#getOwnSelfManagedPhoneAccounts()}
     * <p>
     *  4. unregister the {@link PhoneAccount} via
     *  {@link TelecomManager#unregisterPhoneAccount(PhoneAccountHandle)}
     * <p>
     *  5. verify the account is unregistered
     *  </ul>
     */
    @Test
    public void testAccountTest_TransactionalVoipAppMain() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        AppControlWrapper transactionalApp = null;
        try {
            transactionalApp = bindToApp(TransactionalVoipAppMain);
            PhoneAccount account = verifyDefaultAccountIsRegistered(transactionalApp);
            unregisterDefaultAndVerify(transactionalApp, account);
        } finally {
            tearDownApp(transactionalApp);
        }
    }

    /**
     * verify {@link TelecomManager#getRegisteredPhoneAccounts(PhoneAccountHandle) } returns
     * the expected {@link PhoneAccount}.
     */
    @RequiresFlagsEnabled(Flags.FLAG_GET_REGISTERED_PHONE_ACCOUNTS)
    @Test
    public void testGetOwnSelfManagedPhoneAccount_TransactionalVoipAppMain() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        final PhoneAccountHandle expectedHandle = TRANSACTIONAL_APP_SUPPLEMENTARY_HANDLE;
        final PhoneAccount expectedAccount      = TRANSACTIONAL_MAIN_SUPPLEMENTARY_ACCOUNT;

        AppControlWrapper transactionalApp = null;

        try {
            transactionalApp = bindToApp(TransactionalVoipAppMain);

            assertEquals(1, getRegisteredPhoneAccounts(transactionalApp).size());

            // register a PhoneAccount app-side so that the  {@link
            // TelecomManager#getOwnSelfManagedPhoneAccount(PhoneAccountHandle) } can be tested.
            registerAcctAndVerify(
                    transactionalApp,
                    expectedAccount,
                    2 /* numOfExpectedAccounts */);

            // API under test
            List<PhoneAccount> accounts = getRegisteredPhoneAccounts(transactionalApp);
            assertEquals(2, accounts.size());
            boolean foundExpectedHandle = false;
            for(PhoneAccount account : accounts){
                if(account.getAccountHandle().equals(expectedHandle)){
                    foundExpectedHandle = true;
                    assertPhoneAccountValuesMatch(expectedAccount, account);
                }
            }
            assertTrue(foundExpectedHandle);

            // cleanup the registered account so other tests are not affected
            unregisterAcctAndVerify(
                    transactionalApp,
                    expectedAccount,
                    1 /* numOfExpectedAccounts */);
        } finally {
            tearDownApp(transactionalApp);
        }
    }

    /**
     * verify the expected behavior when 2 {@link PhoneAccount}s use the same
     * {@link PhoneAccountHandle} and register with Telecom.
     * {@link TelecomManager#getRegisteredPhoneAccounts(PhoneAccountHandle) } returns
     * the last registered {@link PhoneAccount} with the linked {@link PhoneAccountHandle}.
     */
    @RequiresFlagsEnabled(Flags.FLAG_GET_REGISTERED_PHONE_ACCOUNTS)
    @Test
    public void testMultipleAccountsWithUniquePhoneAccountHandle() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        AppControlWrapper transactionalApp = null;
        final PhoneAccountHandle uniqueHandle =
                new PhoneAccountHandle(
                        new ComponentName(TRANSACTIONAL_PACKAGE_NAME, TRANSACTIONAL_PACKAGE_NAME),
                        "777");

        final PhoneAccount acctVideoCap =
                PhoneAccount.builder(uniqueHandle, "PA_VIDEO_CAPS")
                        .setCapabilities(
                                PhoneAccount.CAPABILITY_SUPPORTS_TRANSACTIONAL_OPERATIONS
                                        | PhoneAccount.CAPABILITY_SUPPORTS_VIDEO_CALLING
                                        | PhoneAccount.CAPABILITY_VIDEO_CALLING
                                        | PhoneAccount.CAPABILITY_SELF_MANAGED
                        ).build();

        final PhoneAccount acctAudioOnlyCap =
                PhoneAccount.builder(uniqueHandle, "PA_AUDIO_ONLY_CAPS")
                        .setCapabilities(
                                PhoneAccount.CAPABILITY_SUPPORTS_TRANSACTIONAL_OPERATIONS
                                        | PhoneAccount.CAPABILITY_SELF_MANAGED
                        ).build();

        try {
            transactionalApp = bindToApp(TransactionalVoipAppMain);

            // register a PhoneAccount app-side so that {@link
            // TelecomManager#getOwnSelfManagedPhoneAccount(PhoneAccountHandle) } can be tested
            registerAcctAndVerify(transactionalApp, acctVideoCap, 2 /* numOfAccounts */);

            // API under test
            List<PhoneAccount> accounts = getRegisteredPhoneAccounts(transactionalApp);
            assertEquals(2, accounts.size());
            boolean foundExpectedHandle = false;
            for(PhoneAccount account : accounts){
                if(account.getAccountHandle().equals(uniqueHandle)){
                    foundExpectedHandle = true;
                    assertPhoneAccountValuesMatch(acctVideoCap, account);
                }
            }
            assertTrue(foundExpectedHandle);

            // register another PhoneAccount with the same PhoneAccountHandle. The old PhoneAccount
            // values will be overridden with the new values registered.
            registerAcctAndVerify(transactionalApp, acctAudioOnlyCap, 2 /* numOfAccounts */);

            // API under test
            accounts = getRegisteredPhoneAccounts(transactionalApp);
            assertEquals(2, accounts.size());
            foundExpectedHandle = false;
            for(PhoneAccount account : accounts){
                if(account.getAccountHandle().equals(uniqueHandle)){
                    foundExpectedHandle = true;
                    assertPhoneAccountValuesMatch(acctAudioOnlyCap, account);
                }
            }
            assertTrue(foundExpectedHandle);

            // cleanup the registered account so other tests are not affected
            unregisterAcctAndVerify(transactionalApp, acctAudioOnlyCap, 1 /* numOfAccounts */);
        } finally {
            tearDownApp(transactionalApp);
        }
    }

    /**
     * Test the scenario where a managed application that registers a secondary phone account on top
     * of an already existing phone account.  The application should also be able to query and
     * unregister it.
     *
     * <h3> Test Steps: </h3>
     * <ul>
     *  1. create a secondary  {@link android.telecom.PhoneAccount }
     * <p>
     *  2. register the managed {@link android.telecom.PhoneAccount } when the application is
     *  started via {@link TelecomManager#registerPhoneAccount(PhoneAccount)}
     * <p>
     *  3. verify the {@link PhoneAccount} is retrievable via
     *  {@link TelecomManager#getOwnSelfManagedPhoneAccounts()}
     * <p>
     *  4. unregister the {@link PhoneAccount} via
     *  {@link TelecomManager#unregisterPhoneAccount(PhoneAccountHandle)}
     * <p>
     *  5. verify the account is unregistered
     *  </ul>
     */
    @Test
    public void testCustomAccountTest_TransactionalVoipAppMain() throws Exception {
        if (!mShouldTestTelecom || S_IS_TEST_DISABLED) {
            return;
        }
        AppControlWrapper transactionalApp = null;
        try {
            transactionalApp = bindToApp(TransactionalVoipAppMain);
            registerAcctAndVerify(
                    transactionalApp,
                    TRANSACTIONAL_MAIN_SUPPLEMENTARY_ACCOUNT,
                    2 /* numOfExpectedAccounts */);

            unregisterAcctAndVerify(
                    transactionalApp,
                    TRANSACTIONAL_MAIN_SUPPLEMENTARY_ACCOUNT,
                    1 /* numOfExpectedAccounts */);
        } finally {
            tearDownApp(transactionalApp);
        }
    }

    /*********************************************************************************************
     *                           Helpers
     /*********************************************************************************************/

    private void assertPhoneAccountValuesMatch(PhoneAccount expected, PhoneAccount actual) {
        assertNotNull(actual);
        assertEquals(expected.getAccountHandle(), actual.getAccountHandle());
        assertEquals(expected.getCapabilities(), actual.getCapabilities());
        assertEquals(expected.describeContents(), actual.describeContents());
        assertEquals(expected.getAddress(), actual.getAddress());
        assertEquals(expected.getIcon(), actual.getIcon());
    }

    private PhoneAccount verifyDefaultAccountIsRegistered(AppControlWrapper appControlWrapper)
            throws RemoteException {
        PhoneAccount account = appControlWrapper.getDefaultPhoneAccount();
        assertNotNull(account);
        assertTrue(isPhoneAccountRegistered(account.getAccountHandle()));
        List<PhoneAccountHandle> handles = appControlWrapper.getAccountHandlesForApp();
        assertEquals(1, handles.size());
        return account;
    }

    private void unregisterDefaultAndVerify(AppControlWrapper appControlWrapper,
                                            PhoneAccount acct) throws RemoteException {
        appControlWrapper.unregisterPhoneAccountWithHandle(acct.getAccountHandle());
        assertFalse(isPhoneAccountRegistered(acct.getAccountHandle()));
        List<PhoneAccountHandle> handles = appControlWrapper.getAccountHandlesForApp();
        assertEquals(0, handles.size());
    }

    private void registerAcctAndVerify(AppControlWrapper appControlWrapper,
                                            PhoneAccount acct,
                                            int numOfExpectedAccounts) throws RemoteException {
        appControlWrapper.registerCustomPhoneAccount(acct);
        List<PhoneAccountHandle> handles = appControlWrapper.getAccountHandlesForApp();
        assertEquals(numOfExpectedAccounts, handles.size()); // TODO:: fails here for transactional
        // services due to b/310739126
    }

    private void unregisterAcctAndVerify(AppControlWrapper appControlWrapper,
                                         PhoneAccount acct,
                                         int numOfExpectedAccounts) throws RemoteException {
        appControlWrapper.unregisterPhoneAccountWithHandle(acct.getAccountHandle());
        assertFalse(isPhoneAccountRegistered(acct.getAccountHandle()));
        assertEquals(numOfExpectedAccounts, appControlWrapper.getAccountHandlesForApp().size());
    }
}
