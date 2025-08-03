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

package android.telecom.cts;

import static android.telecom.PhoneAccount.CAPABILITY_CALL_PROVIDER;
import static android.telecom.PhoneAccount.CAPABILITY_SELF_MANAGED;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telecom.cts.carmodetestapp.ICtsCarModeInCallServiceControl;
import android.telecom.cts.carmodetestappselfmanaged.CtsCarModeInCallServiceControlSelfManaged;
import android.util.ArraySet;
import android.util.Log;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.internal.telephony.flags.Flags;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class PhoneAccountRegistrarTest extends BaseTelecomTestWithMockServices {

    private static final String TAG = "PhoneAccountRegistrarTest";
    private static final long TIMEOUT = 3000L;
    private static final int LARGE_ACCT_HANDLE_ID_MIN_SIZE = 50000;
    private static final String RANDOM_CHAR_VALUE = "a";
    private static final String TEL_PREFIX = "tel:";
    private static final String TELECOM_CLEANUP_ACCTS_CMD = "telecom cleanup-orphan-phone-accounts";
    public static final long SEED = 52L; // random seed chosen
    public static final int MAX_PHONE_ACCOUNT_REGISTRATIONS = 10; // mirrors constant in...
    // PhoneAccountRegistrar called MAX_PHONE_ACCOUNT_REGISTRATIONS

    // permissions
    private static final String READ_PRIVILEGED_PHONE_STATE =
            "android.permission.READ_PRIVILEGED_PHONE_STATE";
    private static final String MODIFY_PHONE_STATE_PERMISSION =
            "android.permission.MODIFY_PHONE_STATE";
    private static final String REGISTER_SIM_SUBSCRIPTION_PERMISSION =
            "android.permission.REGISTER_SIM_SUBSCRIPTION";

    // telecom cts test package (default package that registers phoneAccounts)
    private static final ComponentName TEST_COMPONENT_NAME =
            new ComponentName(TestUtils.PACKAGE, TestUtils.COMPONENT);

    // secondary test package (extra package that can be set up to register phoneAccounts)
    private static final String SELF_MANAGED_CAR_PACKAGE =
            CtsCarModeInCallServiceControlSelfManaged.class.getPackage().getName();
    private static final ComponentName SELF_MANAGED_CAR_RELATIVE_COMPONENT = ComponentName
            .createRelative(SELF_MANAGED_CAR_PACKAGE,
                    CtsCarModeInCallServiceControlSelfManaged.class.getName());
    private static final ComponentName CAR_COMPONENT = new ComponentName(SELF_MANAGED_CAR_PACKAGE,
            TestUtils.SELF_MANAGED_COMPONENT);
    private static final String CAR_MODE_CONTROL =
            "android.telecom.cts.carmodetestapp.ACTION_CAR_MODE_CONTROL";
    // variables to interface with the second test package
    TestServiceConnection mControl;
    ICtsCarModeInCallServiceControl mSecondaryTestPackageControl;

    @Override
    public void setUp() throws Exception {
        // Sets up this package as default dialer in super.
        super.setUp();
        NewOutgoingCallBroadcastReceiver.reset();
        if (!mShouldTestTelecom) return;
        setupConnectionService(null, 0);
        // cleanup any accounts registered to the test package before starting tests
        cleanupPhoneAccounts();
    }

    @Override
    public void tearDown() throws Exception {
        // cleanup any accounts registered to the test package after testing to avoid crashing other
        // tests.
        cleanupPhoneAccounts();
        super.tearDown();
    }

    /**
     * Test scenario where a single package can register MAX_PHONE_ACCOUNT_REGISTRATIONS via
     * {@link android.telecom.TelecomManager#registerPhoneAccount(PhoneAccount)}  without an
     * exception being thrown.
     */
    public void testRegisterMaxPhoneAccountsWithoutException() {
        if (!mShouldTestTelecom) return;

        // ensure the test starts without any phone accounts registered to the test package
        cleanupPhoneAccounts();

        //  determine the number of phone accounts that can be registered before hitting limit
        int numberOfAccountsThatCanBeRegistered = MAX_PHONE_ACCOUNT_REGISTRATIONS
                - getNumberOfPhoneAccountsRegisteredToTestPackage();

        // create the remaining number of phone accounts via helper function
        // in order to reach the upper bound MAX_PHONE_ACCOUNT_REGISTRATIONS
        ArrayList<PhoneAccount> accounts = TestUtils.generateRandomPhoneAccounts(SEED,
                numberOfAccountsThatCanBeRegistered, TestUtils.PACKAGE, TestUtils.COMPONENT);
        try {
            // register all accounts created
            accounts.stream().forEach(a -> mTelecomManager.registerPhoneAccount(a));
            // assert the maximum accounts that can be registered were registered successfully
            assertEquals(MAX_PHONE_ACCOUNT_REGISTRATIONS,
                    getNumberOfPhoneAccountsRegisteredToTestPackage());
        } finally {
            // cleanup accounts registered
            accounts.stream().forEach(
                    d -> mTelecomManager.unregisterPhoneAccount(d.getAccountHandle()));
        }
    }

    /**
     * Tests a scenario where a PhoneAccount with more than MAX_PHONE_ACCOUNT_REGISTRATIONS
     * PhoneAccountHandles set as a simultaneous calling restriction throws an
     * {@link IllegalArgumentException}.
     */
    public void testExceptionThrownDueToInvalidSimultaneousCallRestriction_tooManyAccounts() {
        if (!mShouldTestTelecom || !Flags.simultaneousCallingIndications()) return;

        // ensure the test starts without any phone accounts registered to the test package
        cleanupPhoneAccounts();

        // Create MAX_PHONE_ACCOUNT_REGISTRATIONS + 1 via helper function
        ArrayList<PhoneAccountHandle> restrictionHandles =
                TestUtils.generateRandomPhoneAccountHandles(SEED,
                MAX_PHONE_ACCOUNT_REGISTRATIONS + 1, TestUtils.PACKAGE, TestUtils.COMPONENT);
        PhoneAccountHandle acctHandle = TestUtils.makePhoneAccountHandle(
                TestUtils.DEFAULT_TEST_ACCOUNT_1_ID);
        PhoneAccount acct = TestUtils.buildSelfManagedPhoneAccount(acctHandle,
                        TestUtils.ACCOUNT_LABEL)
                .setSimultaneousCallingRestriction(new ArraySet<>(restrictionHandles)).build();

        try {
            // Try to register more phone accounts than allowed by the upper bound limit
            // MAX_PHONE_ACCOUNT_REGISTRATIONS
            mTelecomManager.registerPhoneAccount(acct);
            // A successful test should never reach this line of execution.
            // However, if it does, fail the test by throwing a fail(...)
            fail("Test failed. The test did not throw an IllegalArgumentException when "
                    + "registering a phone account where the Set of PhoneAccountHandles in the "
                    + "simultaneous calling restriction is over the upper bound: "
                    + "MAX_PHONE_ACCOUNT_REGISTRATIONS");
        } catch (IllegalArgumentException e) {
            // Assert the IllegalArgumentException was thrown
            assertNotNull(e.toString());
        } finally {
            // Cleanup accounts registered
            mTelecomManager.unregisterPhoneAccount(acctHandle);
        }
    }

    /**
     * Tests a scenario where a PhoneAccount contains a simultaneous calling restriction and the
     * associated PhoneAccountHandle contains an ID where the field exceeds the maximum size.
     */
    public void testExceptionThrownDueToInvalidSimultaneousCallRestriction_invalidId() {
        if (!mShouldTestTelecom || !Flags.simultaneousCallingIndications()) return;

        // ensure the test starts without any phone accounts registered to the test package
        cleanupPhoneAccounts();

        // Exceed the field size for the ID
        ArraySet<PhoneAccountHandle> invalidRestrictionHandles = new ArraySet<>(1);
        invalidRestrictionHandles.add(TestUtils.makePhoneAccountHandle("a".repeat(257)));
        PhoneAccountHandle acctHandle = TestUtils.makePhoneAccountHandle(
                TestUtils.DEFAULT_TEST_ACCOUNT_1_ID);
        PhoneAccount acct = TestUtils.buildSelfManagedPhoneAccount(acctHandle,
                        TestUtils.ACCOUNT_LABEL)
                .setSimultaneousCallingRestriction(invalidRestrictionHandles).build();

        try {
            // Try to register more phone accounts than allowed by the upper bound limit
            // MAX_PHONE_ACCOUNT_REGISTRATIONS
            mTelecomManager.registerPhoneAccount(acct);
            // A successful test should never reach this line of execution.
            // However, if it does, fail the test by throwing a fail(...)
            fail("Test failed. The test did not throw an IllegalArgumentException when "
                    + "registering a phone account where the Set of PhoneAccountHandles in the "
                    + "simultaneous calling restriction is over the upper bound: "
                    + "MAX_PHONE_ACCOUNT_REGISTRATIONS");
        } catch (IllegalArgumentException e) {
            // Assert the IllegalArgumentException was thrown
            assertNotNull(e.toString());
        } finally {
            // Cleanup accounts registered
            mTelecomManager.unregisterPhoneAccount(acctHandle);
        }
    }

    /**
     * Tests a scenario where a single package exceeds MAX_PHONE_ACCOUNT_REGISTRATIONS and
     * an {@link IllegalArgumentException}  is thrown. Will fail if no exception is thrown.
     */
    public void testExceptionThrownDueUserExceededMaxPhoneAccountRegistrations()
            throws IllegalArgumentException {
        if (!mShouldTestTelecom) return;

        // ensure the test starts without any phone accounts registered to the test package
        cleanupPhoneAccounts();

        // Create MAX_PHONE_ACCOUNT_REGISTRATIONS + 1 via helper function
        ArrayList<PhoneAccount> accounts = TestUtils.generateRandomPhoneAccounts(SEED,
                MAX_PHONE_ACCOUNT_REGISTRATIONS + 1, TestUtils.PACKAGE,
                TestUtils.COMPONENT);

        try {
            // Try to register more phone accounts than allowed by the upper bound limit
            // MAX_PHONE_ACCOUNT_REGISTRATIONS
            accounts.stream().forEach(a -> mTelecomManager.registerPhoneAccount(a));
            // A successful test should never reach this line of execution.
            // However, if it does, fail the test by throwing a fail(...)
            fail("Test failed. The test did not throw an IllegalArgumentException when "
                    + "registering phone accounts over the upper bound: "
                    + "MAX_PHONE_ACCOUNT_REGISTRATIONS");
        } catch (IllegalArgumentException e) {
            // Assert the IllegalArgumentException was thrown
            assertNotNull(e.toString());
        } finally {
            // Cleanup accounts registered
            accounts.stream().forEach(d -> mTelecomManager.unregisterPhoneAccount(
                    d.getAccountHandle()));
        }
    }

    /**
     * Ensure an app does not register accounts over the upper bound limit by disabling them
     */
    public void testDisablingAccountsAfterRegStillThrowsException() throws Exception {
        if (!mShouldTestTelecom) return;

        // ensure the test starts without any phone accounts registered to the test package
        cleanupPhoneAccounts();

        // Create MAX_PHONE_ACCOUNT_REGISTRATIONS + 1 via helper function
        ArrayList<PhoneAccount> accounts = TestUtils.generateRandomPhoneAccounts(SEED,
                MAX_PHONE_ACCOUNT_REGISTRATIONS + 1, TestUtils.PACKAGE,
                TestUtils.COMPONENT);

        try {
            // Try to register more phone accounts than allowed by the upper bound limit
            for (PhoneAccount pa : accounts) {
                mTelecomManager.registerPhoneAccount(pa);
                TestUtils.disablePhoneAccount(getInstrumentation(), pa.getAccountHandle());
                // verify the account is both registered and disabled
                verifyAccountIsDisabled(pa);
            }

            // A successful test should never reach this line of execution.
            // However, if it does, fail the test by throwing a fail(...)
            fail("Test failed. The test did not throw an IllegalArgumentException when "
                    + "registering phone accounts over the upper bound: "
                    + "MAX_PHONE_ACCOUNT_REGISTRATIONS");
        } catch (IllegalArgumentException e) {
            // Assert the IllegalArgumentException was thrown
            assertNotNull(e.toString());
        } finally {
            // Cleanup accounts registered
            accounts.stream().forEach(d -> mTelecomManager.unregisterPhoneAccount(
                    d.getAccountHandle()));
        }
    }

    /**
     * Ensure an app does not register accounts that will be auto-disabled upon registered and
     * bypass the limit. Note: CAPABILITY_CALL_PROVIDER will register the account as disabled.
     */
    public void testDisabledAccountsThrowsException() throws Exception {
        if (!mShouldTestTelecom) return;

        // ensure the test starts without any phone accounts registered to the test package
        cleanupPhoneAccounts();

        // Create MAX_PHONE_ACCOUNT_REGISTRATIONS + 1
        ArrayList<PhoneAccount> accounts = new ArrayList<>();
        for (int i = 0; i < MAX_PHONE_ACCOUNT_REGISTRATIONS + 1; i++) {
            accounts.add(new PhoneAccount.Builder(
                    TestUtils.makePhoneAccountHandle(Integer.toString(i)),
                    TestUtils.ACCOUNT_LABEL)
                    .setCapabilities(CAPABILITY_CALL_PROVIDER)
                    .build());
        }

        try {
            // Try to register more phone accounts than allowed by the upper bound limit
            for (PhoneAccount pa : accounts) {
                mTelecomManager.registerPhoneAccount(pa);
                // verify the account is both registered and disabled
                verifyAccountIsDisabled(pa);
            }
            // A successful test should never reach this line of execution.
            // However, if it does, fail the test by throwing a fail(...)
            fail("Test failed. The test did not throw an IllegalArgumentException when "
                    + "registering phone accounts over the upper bound: "
                    + "MAX_PHONE_ACCOUNT_REGISTRATIONS");
        } catch (IllegalArgumentException e) {
            // Assert the IllegalArgumentException was thrown
            assertNotNull(e.toString());
        } finally {
            // Cleanup accounts registered
            accounts.stream().forEach(d -> mTelecomManager.unregisterPhoneAccount(
                    d.getAccountHandle()));
        }
    }

    /**
     * Test scenario where two distinct packages register MAX_PHONE_ACCOUNT_REGISTRATIONS via
     * {@link
     * android.telecom.TelecomManager#registerPhoneAccount(PhoneAccount)} without an exception being
     * thrown.
     * This ensures that PhoneAccountRegistrar is handling {@link PhoneAccount} registrations
     * to distinct packages correctly.
     */
    public void testTwoPackagesRegisterMax() throws Exception {
        if (!mShouldTestTelecom) return;

        // ensure the test starts without any phone accounts registered to the test package
        cleanupPhoneAccounts();

        //  determine the number of phone accounts that can be registered to package 1
        int numberOfAccountsThatCanBeRegisteredToPackage1 = MAX_PHONE_ACCOUNT_REGISTRATIONS
                - getNumberOfPhoneAccountsRegisteredToTestPackage();

        // Create MAX phone accounts for package 1
        ArrayList<PhoneAccount> accountsPackage1 = TestUtils.generateRandomPhoneAccounts(SEED,
                numberOfAccountsThatCanBeRegisteredToPackage1, TestUtils.PACKAGE,
                TestUtils.COMPONENT);

        // Constants for creating a second package to register phone accounts
        final String carPkgSelfManaged =
                CtsCarModeInCallServiceControlSelfManaged.class
                        .getPackage().getName();
        final ComponentName carComponentSelfManaged = ComponentName.createRelative(
                carPkgSelfManaged, CtsCarModeInCallServiceControlSelfManaged.class.getName());
        final String carModeControl =
                "android.telecom.cts.carmodetestapp.ACTION_CAR_MODE_CONTROL";

        // Set up binding for second package. This is needed in order to bypass a SecurityException
        // thrown by a second test package registering phone accounts.
        TestServiceConnection control = setUpControl(carModeControl,
                carComponentSelfManaged);

        ICtsCarModeInCallServiceControl carModeIncallServiceControlSelfManaged =
                ICtsCarModeInCallServiceControl.Stub
                        .asInterface(control.getService());

        carModeIncallServiceControlSelfManaged.reset(); //... done setting up binding

        // Create MAX phone accounts for package 2
        ArrayList<PhoneAccount> accountsPackage2 = TestUtils.generateRandomPhoneAccounts(SEED,
                MAX_PHONE_ACCOUNT_REGISTRATIONS, carPkgSelfManaged,
                TestUtils.SELF_MANAGED_COMPONENT);

        try {

            // register all accounts for package 1
            accountsPackage1.stream().forEach(a -> mTelecomManager.registerPhoneAccount(a));


            // register all phone accounts for package 2
            for (int i = 0; i < MAX_PHONE_ACCOUNT_REGISTRATIONS; i++) {
                carModeIncallServiceControlSelfManaged.registerPhoneAccount(
                        accountsPackage2.get(i));
            }

        } finally {
            // cleanup all phone accounts registered. Note, unregisterPhoneAccount will not
            // cause a runtime error if no phone account is found when trying to unregister.

            accountsPackage1.stream().forEach(d -> mTelecomManager.unregisterPhoneAccount(
                    d.getAccountHandle()));

            for (int i = 0; i < MAX_PHONE_ACCOUNT_REGISTRATIONS; i++) {
                carModeIncallServiceControlSelfManaged.unregisterPhoneAccount(
                        accountsPackage2.get(i).getAccountHandle());
            }
        }
        // unbind from second package
        mContext.unbindService(control);
    }

    /**
     * Test the scenario where {@link android.telecom.TelecomManager
     * #getCallCapablePhoneAccounts(boolean)} is called with a heavy payload
     * that could cause a {@link android.os.TransactionTooLargeException}.  Telecom is expected to
     * handle this by splitting the parcels via {@link android.content.pm.ParceledListSlice}.
     */
    public void testGettingLargeCallCapablePhoneAccountHandlePayload() throws Exception {
        if (!mShouldTestTelecom) return;
        // ensure the test starts without any phone accounts registered to the test package
        cleanupPhoneAccounts();

        // generate a large phoneAccountHandle id string to create a large payload
        String largeAccountHandleId = generateLargeString(
                LARGE_ACCT_HANDLE_ID_MIN_SIZE, RANDOM_CHAR_VALUE);
        assertEquals(LARGE_ACCT_HANDLE_ID_MIN_SIZE, largeAccountHandleId.length());

        // create handles for package 1
        List<PhoneAccount> phoneAccountsForPackage1 =
                generatePhoneAccountsForPackage(TEST_COMPONENT_NAME, largeAccountHandleId,
                        numberOfPhoneAccountsCtsPackageCanRegister(), CAPABILITY_CALL_PROVIDER);

        //create handles for package 2
        List<PhoneAccount> phoneAccountsForPackage2 =
                generatePhoneAccountsForPackage(CAR_COMPONENT, largeAccountHandleId,
                        MAX_PHONE_ACCOUNT_REGISTRATIONS, CAPABILITY_CALL_PROVIDER);
        try {
            // register all accounts for package 1
            phoneAccountsForPackage1.stream()
                    .forEach(a -> mTelecomManager.registerPhoneAccount(a));
            // verify all can be fetched
            verifyCanFetchCallCapableAccounts();
            // register all accounts for package 2
            bindToSecondTestPackageAndRegisterAccounts(phoneAccountsForPackage2);
            // verify all can be fetched
            verifyCanFetchCallCapableAccounts();
        } catch (IllegalArgumentException e) {
            // allow test pass ...
            Log.i(TAG, "testGettingLargeCallCapablePhoneAccountHandlePayload:"
                    + " illegal arg exception thrown.");
        } finally {
            unbindSecondTestPackageAndUnregisterAccounts(phoneAccountsForPackage2);
            cleanupPhoneAccounts();
        }
    }

    /**
     * Test the scenario where {@link android.telecom.TelecomManager#getSelfManagedPhoneAccounts()}
     * is called with a heavy payload that could cause a {@link
     * android.os.TransactionTooLargeException}.  Telecom is expected to handle this by splitting
     * the parcels via {@link android.content.pm.ParceledListSlice}.
     */
    public void testGettingLargeSelfManagedPhoneAccountHandlePayload() throws Exception {
        if (!mShouldTestTelecom) return;
        // ensure the test starts without any phone accounts registered to the test package
        cleanupPhoneAccounts();

        // generate a large phoneAccountHandle id string to create a large payload
        String largeAccountHandleId = generateLargeString(
                LARGE_ACCT_HANDLE_ID_MIN_SIZE, RANDOM_CHAR_VALUE);
        assertEquals(LARGE_ACCT_HANDLE_ID_MIN_SIZE, largeAccountHandleId.length());

        // create handles for package 1
        List<PhoneAccount> phoneAccountsForPackage1 =
                generatePhoneAccountsForPackage(TEST_COMPONENT_NAME, largeAccountHandleId,
                        numberOfPhoneAccountsCtsPackageCanRegister(), CAPABILITY_SELF_MANAGED);

        //create handles for package 2
        List<PhoneAccount> phoneAccountsForPackage2 =
                generatePhoneAccountsForPackage(CAR_COMPONENT, largeAccountHandleId,
                        MAX_PHONE_ACCOUNT_REGISTRATIONS, CAPABILITY_SELF_MANAGED);
        try {
            // register all accounts for package 1
            phoneAccountsForPackage1.stream()
                    .forEach(a -> mTelecomManager.registerPhoneAccount(a));
            // verify all can be fetched
            verifyCanFetchSelfManagedPhoneAccounts();
            // register all accounts for package 2
            bindToSecondTestPackageAndRegisterAccounts(phoneAccountsForPackage2);
            // verify all can be fetched
            verifyCanFetchSelfManagedPhoneAccounts();
        } catch (IllegalArgumentException e) {
            // allow test pass ...
            Log.i(TAG, "testGettingLargeSelfManagedPhoneAccountHandlePayload:"
                    + " illegal arg exception thrown.");
        } finally {
            unbindSecondTestPackageAndUnregisterAccounts(phoneAccountsForPackage2);
            cleanupPhoneAccounts();
        }
    }

    /**
     * Test the scenario where {@link android.telecom.TelecomManager#getAllPhoneAccountHandles()}
     * is called with a heavy payload that could cause a {@link
     * android.os.TransactionTooLargeException}.  Telecom is expected to handle this by splitting
     * the parcels via {@link android.content.pm.ParceledListSlice}.
     */
    public void testGettingAllPhoneAccountHandlesWithLargePayload() throws Exception {
        if (!mShouldTestTelecom) return;

        // ensure the test starts without any phone accounts registered to the test package
        cleanupPhoneAccounts();

        // generate a large phoneAccountHandle id string to create a large payload
        String largeAccountHandleId = generateLargeString(
                LARGE_ACCT_HANDLE_ID_MIN_SIZE, RANDOM_CHAR_VALUE);
        assertEquals(LARGE_ACCT_HANDLE_ID_MIN_SIZE, largeAccountHandleId.length());

        // create handles for package 1
        List<PhoneAccount> phoneAccountsForPackage1 =
                generatePhoneAccountsForPackage(TEST_COMPONENT_NAME, largeAccountHandleId,
                        numberOfPhoneAccountsCtsPackageCanRegister(), CAPABILITY_SELF_MANAGED);

        //create handles for package 2
        List<PhoneAccount> phoneAccountsForPackage2 =
                generatePhoneAccountsForPackage(CAR_COMPONENT, largeAccountHandleId,
                        MAX_PHONE_ACCOUNT_REGISTRATIONS, CAPABILITY_SELF_MANAGED);
        try {
            // register all accounts for package 1
            phoneAccountsForPackage1.stream()
                    .forEach(a -> mTelecomManager.registerPhoneAccount(a));
            // verify all can be fetched
            verifyCanFetchAllPhoneAccountHandles();
            // register all accounts for package 2
            bindToSecondTestPackageAndRegisterAccounts(phoneAccountsForPackage2);
            // verify all can be fetched
            verifyCanFetchAllPhoneAccountHandles();
        } catch (IllegalArgumentException e) {
            // allow test pass ...
        } finally {

            unbindSecondTestPackageAndUnregisterAccounts(phoneAccountsForPackage2);
            cleanupPhoneAccounts();
        }
    }

    /**
     * Test the scenario where {@link TelecomManager#getAllPhoneAccounts()}
     * is called with a heavy payload that could cause a {@link
     * android.os.TransactionTooLargeException}.  Telecom is expected to handle this by splitting
     * the parcels via {@link android.content.pm.ParceledListSlice}.
     */
    public void testGetAllPhoneAccountsWithLargePayload() throws Exception {
        if (!mShouldTestTelecom) return;

        // ensure the test starts without any phone accounts registered to the test package
        cleanupPhoneAccounts();

        // generate a large phoneAccountHandle id string to create a large payload
        String largeAccountHandleId = generateLargeString(
                LARGE_ACCT_HANDLE_ID_MIN_SIZE, RANDOM_CHAR_VALUE);
        assertEquals(LARGE_ACCT_HANDLE_ID_MIN_SIZE, largeAccountHandleId.length());

        // create handles for package 1
        List<PhoneAccount> phoneAccountsForPackage1 =
                generatePhoneAccountsForPackage(TEST_COMPONENT_NAME, largeAccountHandleId,
                        numberOfPhoneAccountsCtsPackageCanRegister(),
                        CAPABILITY_CALL_PROVIDER
                                | PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION);

        //create handles for package 2
        List<PhoneAccount> phoneAccountsForPackage2 =
                generatePhoneAccountsForPackage(CAR_COMPONENT, largeAccountHandleId,
                        MAX_PHONE_ACCOUNT_REGISTRATIONS,
                        CAPABILITY_SELF_MANAGED);
        try {
            // register all accounts for package 1
            for (PhoneAccount pa : phoneAccountsForPackage1) {
                ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelecomManager,
                        tm -> tm.registerPhoneAccount(pa), REGISTER_SIM_SUBSCRIPTION_PERMISSION);
            }
            // verify all can be fetched
            verifyCanFetchAllPhoneAccounts();
            // register all accounts for package 2
            bindToSecondTestPackageAndRegisterAccounts(phoneAccountsForPackage2);
            // verify all can be fetched
            verifyCanFetchAllPhoneAccounts();
        } catch (IllegalArgumentException e) {
            // allow test pass ...
        } finally {
            unbindSecondTestPackageAndUnregisterAccounts(phoneAccountsForPackage2);
            cleanupPhoneAccounts();
        }
    }

    @ApiTest(apis = {
            "android.telecom.TelecomManager#getCallCapablePhoneAccounts",
            "android.telecom.TelecomManager#getCallCapablePhoneAccountsAcrossProfiles"})
    public void testGetCallCapablePhoneAccountsAcrossProfiles() throws Exception {
        if (!mShouldTestTelecom || !Flags.workProfileApiSplit()) {
            return;
        }

        // ensure the test starts without any phone accounts registered to the test package
        cleanupPhoneAccounts();

        Random random = new Random(SEED);
        PhoneAccountHandle currentHandle = TestUtils.createPhoneAccountHandle(
                random, TestUtils.PACKAGE, TestUtils.REMOTE_COMPONENT,
                Process.myUserHandle().getIdentifier());
        PhoneAccountHandle otherHandle = TestUtils.createPhoneAccountHandle(
                random, TestUtils.PACKAGE, TestUtils.REMOTE_COMPONENT,
                UserHandle.MIN_SECONDARY_USER_ID);

        PhoneAccount currentAccount = new PhoneAccount.Builder(
                currentHandle, this.getClass().getName())
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .build();
        PhoneAccount otherAccount = new PhoneAccount.Builder(
                otherHandle, this.getClass().getName())
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .build();

        try {
            mTelecomManager.registerPhoneAccount(currentAccount);
            mTelecomManager.registerPhoneAccount(otherAccount);

            TestUtils.enablePhoneAccount(getInstrumentation(), currentHandle);
            TestUtils.enablePhoneAccount(getInstrumentation(), otherHandle);

            List<PhoneAccountHandle> handlesCurrent = mTelecomManager.getCallCapablePhoneAccounts();
            List<PhoneAccountHandle> handlesAcrossProfiles = mTelecomManager
                    .getCallCapablePhoneAccountsAcrossProfiles();

            assertEquals(handlesAcrossProfiles.size(), handlesCurrent.size() + 1);
            assertTrue(handlesAcrossProfiles.containsAll(handlesCurrent));
            assertTrue(handlesAcrossProfiles.contains(otherHandle));

            //test for disabled account
            TestUtils.disablePhoneAccount(getInstrumentation(), currentHandle);

            handlesCurrent = mTelecomManager.getCallCapablePhoneAccounts();
            handlesAcrossProfiles = mTelecomManager.getCallCapablePhoneAccountsAcrossProfiles();
            List<PhoneAccountHandle> handlesAcrossProfilesWithDisabledAccount = mTelecomManager
                    .getCallCapablePhoneAccountsAcrossProfiles(true);

            assertEquals(handlesAcrossProfiles.size(), handlesCurrent.size() + 1);
            assertEquals(handlesAcrossProfilesWithDisabledAccount.size(),
                    handlesAcrossProfiles.size() + 1);
            assertTrue(handlesAcrossProfiles.containsAll(handlesCurrent));
            assertTrue(handlesAcrossProfilesWithDisabledAccount.containsAll(handlesAcrossProfiles));
            assertTrue(handlesAcrossProfilesWithDisabledAccount.contains(currentHandle));
        } catch (IllegalArgumentException e) {
            // allow test pass ...
        } finally {
            cleanupPhoneAccounts();
        }
    }

    // -- The following are helper methods for this testing class. --

    private String generateLargeString(int size, String repeatStrValue) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < size; i++) {
            sb.append(repeatStrValue);
        }
        return sb.toString();
    }

    private List<PhoneAccount> generatePhoneAccountsForPackage(ComponentName cn, String baseId,
            int numOfAccountsToRegister, int capabilities) {
        List<PhoneAccount> accounts = new ArrayList<>();

        for (int i = 0; i < numOfAccountsToRegister; i++) {
            String id = baseId + i;
            PhoneAccountHandle pah = new PhoneAccountHandle(cn, id);
            // create phoneAccount
            String number = TEL_PREFIX + i;
            PhoneAccount pa = PhoneAccount.builder(pah, TestUtils.ACCOUNT_LABEL)
                    .setAddress(Uri.parse(number))
                    .setSubscriptionAddress(Uri.parse(number))
                    .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
                    .setCapabilities(capabilities)
                    .build();
            accounts.add(pa);
        }
        return accounts;
    }

    public void bindToSecondTestPackageAndRegisterAccounts(List<PhoneAccount> accounts)
            throws Exception {
        bindToSecondTestPackage();
        registerAccountsToSecondTestPackage(accounts);
    }

    public void unbindSecondTestPackageAndUnregisterAccounts(List<PhoneAccount> accounts) {
        try {
            mContext.unbindService(mControl);
            unRegisterAccountsForSecondTestPackage(accounts);
        } catch (Exception e) {
            Log.d(TAG,
                    "exception thrown while trying to unbind and unregister accts for 2nd package");
        }
    }

    public void bindToSecondTestPackage() throws RemoteException {
        // Set up binding for second package. This is needed in order to bypass a SecurityException
        // thrown by a second test package registering phone accounts.
        mControl = setUpControl(CAR_MODE_CONTROL, SELF_MANAGED_CAR_RELATIVE_COMPONENT);
        mSecondaryTestPackageControl =
                ICtsCarModeInCallServiceControl.Stub.asInterface(mControl.getService());
        // reset all package variables etc.
        if (mSecondaryTestPackageControl != null) {
            mSecondaryTestPackageControl.reset(); //... done setting up binding
        }
    }

    public void registerAccountsToSecondTestPackage(List<PhoneAccount> accounts)
            throws Exception {
        if (mSecondaryTestPackageControl != null) {
            for (PhoneAccount p : accounts) {
                mSecondaryTestPackageControl.registerPhoneAccount(p);
                TestUtils.enablePhoneAccount(getInstrumentation(), p.getAccountHandle());
            }
        }
    }

    public void unRegisterAccountsForSecondTestPackage(List<PhoneAccount> accounts)
            throws RemoteException {
        if (mSecondaryTestPackageControl != null) {
            for (PhoneAccount p : accounts) {
                mSecondaryTestPackageControl.unregisterPhoneAccount(p.getAccountHandle());
            }
        }
    }

    public void verifyAccountIsDisabled(PhoneAccount account) {
        PhoneAccount phoneAccount = mTelecomManager.getPhoneAccount(account.getAccountHandle());
        assertNotNull(phoneAccount);
        assertFalse(phoneAccount.isEnabled());
    }

    public void verifyCanFetchCallCapableAccounts() {
        List<PhoneAccountHandle> res =
                mTelecomManager.getCallCapablePhoneAccounts(true);
        assertNotNull(res);
        assertTrue(res.size() > 0);
    }

    public void verifyCanFetchAllPhoneAccountHandles() {
        List<PhoneAccountHandle> res =
                ShellIdentityUtils.invokeMethodWithShellPermissions(
                        mTelecomManager, (tm) -> tm.getAllPhoneAccountHandles(),
                        MODIFY_PHONE_STATE_PERMISSION);
        assertNotNull(res);
        assertTrue(res.size() > 0);
    }

    public void verifyCanFetchAllPhoneAccounts() {
        List<PhoneAccount> res =
                ShellIdentityUtils.invokeMethodWithShellPermissions(
                        mTelecomManager, (tm) -> tm.getAllPhoneAccounts(),
                        MODIFY_PHONE_STATE_PERMISSION);
        assertNotNull(res);
        assertTrue(res.size() > 0);
    }

    public void verifyCanFetchSelfManagedPhoneAccounts() {
        List<PhoneAccountHandle> res =
                mTelecomManager.getSelfManagedPhoneAccounts();
        assertNotNull(res);
        assertTrue(res.size() > 0);
    }

    private int numberOfPhoneAccountsCtsPackageCanRegister() {
        return MAX_PHONE_ACCOUNT_REGISTRATIONS - getNumberOfPhoneAccountsRegisteredToTestPackage();
    }

    private TestServiceConnection setUpControl(String action, ComponentName componentName) {
        Intent bindIntent = new Intent(action);
        bindIntent.setComponent(componentName);

        TestServiceConnection
                serviceConnection = new TestServiceConnection();
        mContext.bindService(bindIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        if (!serviceConnection.waitBind()) {
            fail("fail bind to service");
        }
        return serviceConnection;
    }

    private class TestServiceConnection implements ServiceConnection {
        private IBinder mService;
        private CountDownLatch mLatch = new CountDownLatch(1);
        private boolean mIsConnected;

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            Log.i(TAG, "Service Connected: " + componentName);
            mService = service;
            mIsConnected = true;
            mLatch.countDown();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mService = null;
        }

        public IBinder getService() {
            return mService;
        }

        public boolean waitBind() {
            try {
                mLatch.await(TIMEOUT, TimeUnit.MILLISECONDS);
                return mIsConnected;
            } catch (InterruptedException e) {
                return false;
            }
        }
    }

    /**
     * Helper that cleans up any phone accounts registered to this testing package.  Requires
     * the permission READ_PRIVILEGED_PHONE_STATE in order to invoke the
     * getPhoneAccountsForPackage() method.
     */
    private void cleanupPhoneAccounts() {
        try {
            if (mTelecomManager != null) {
                // Get all handles registered to the testing package
                List<PhoneAccountHandle> handles =
                        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelecomManager,
                                (tm) -> tm.getPhoneAccountsForPackage(),
                                READ_PRIVILEGED_PHONE_STATE);

                // cleanup any extra phone accounts registered to the testing package
                if (handles.size() > 0 && mTelecomManager != null) {
                    handles.stream().forEach(
                            d -> mTelecomManager.unregisterPhoneAccount(d));
                }

                TestUtils.executeShellCommand(getInstrumentation(), TELECOM_CLEANUP_ACCTS_CMD);
            }
        } catch (Exception e) {
            Log.d(TAG, "cleanupPhoneAccounts: hit exception while trying to clean");
        }
    }

    /**
     * Helper that gets the number of phone accounts registered to the testing package. Requires
     * the permission READ_PRIVILEGED_PHONE_STATE in order to invoke the
     * getPhoneAccountsForPackage() method.
     * @return number of phone accounts registered to the testing package.
     */
    private int getNumberOfPhoneAccountsRegisteredToTestPackage() {
        if (mTelecomManager != null) {
            return ShellIdentityUtils.invokeMethodWithShellPermissions(mTelecomManager,
                    (tm) -> tm.getPhoneAccountsForPackage(),
                    READ_PRIVILEGED_PHONE_STATE).size();
        }
        return 0;
    }
}

