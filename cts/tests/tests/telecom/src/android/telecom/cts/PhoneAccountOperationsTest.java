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

package android.telecom.cts;

import static android.telecom.cts.TestUtils.*;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.test.InstrumentationTestCase;

import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.internal.telephony.flags.Flags;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


/**
 * Verifies some of the PhoneAccount registration related operations.
 */
public class PhoneAccountOperationsTest extends InstrumentationTestCase {
    public static final PhoneAccountHandle TEST_PHONE_ACCOUNT_HANDLE =
            new PhoneAccountHandle(new ComponentName(PACKAGE, COMPONENT), ACCOUNT_ID_1);
    public static final Bundle TEST_BUNDLE = createTestBundle(false);
    public static final int TEST_LENGTH = 10;
    public static final String TEST_ENCODING = "enUS";

    private TestUtils.InvokeCounter mPhoneAccountRegisteredLatch;
    private TestUtils.InvokeCounter mPhoneAccountUnRegisteredLatch;

    MockPhoneAccountChangedReceiver.IntentListener mPhoneAccountIntentListener =
            new MockPhoneAccountChangedReceiver.IntentListener() {
                @Override
                public void onPhoneAccountRegistered(PhoneAccountHandle handle) {
                    mPhoneAccountRegisteredLatch.invoke(handle);
                }

                @Override
                public void onPhoneAccountUnregistered(PhoneAccountHandle handle) {
                    mPhoneAccountUnRegisteredLatch.invoke(handle);
                }
            };

    private static Bundle createTestBundle(boolean setCallFiltering) {
        Bundle testBundle = new Bundle();
        testBundle.putInt(PhoneAccount.EXTRA_CALL_SUBJECT_MAX_LENGTH, TEST_LENGTH);
        testBundle.putString(PhoneAccount.EXTRA_CALL_SUBJECT_CHARACTER_ENCODING, TEST_ENCODING);
        if (setCallFiltering) {
            testBundle.putBoolean(PhoneAccount.EXTRA_SKIP_CALL_FILTERING, true);
        }
        return testBundle;
    }

    public static final PhoneAccount TEST_SIM_PHONE_ACCOUNT = PhoneAccount.builder(
            TEST_PHONE_ACCOUNT_HANDLE, ACCOUNT_LABEL)
            .setAddress(Uri.parse("tel:555-TEST"))
            .setSubscriptionAddress(Uri.parse("tel:555-TEST"))
            .setCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)
            .setHighlightColor(Color.RED)
            .setShortDescription(ACCOUNT_LABEL)
            .setSupportedUriSchemes(Arrays.asList(PhoneAccount.SCHEME_TEL))
            .build();

    public static final PhoneAccount TEST_NO_SIM_PHONE_ACCOUNT = PhoneAccount.builder(
            TEST_PHONE_ACCOUNT_HANDLE, ACCOUNT_LABEL)
            .setAddress(Uri.parse("tel:555-TEST"))
            .setSubscriptionAddress(Uri.parse("tel:555-TEST"))
            .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER |
                    PhoneAccount.CAPABILITY_VIDEO_CALLING)
            .setHighlightColor(Color.RED)
            .setShortDescription(ACCOUNT_LABEL)
            .setSupportedUriSchemes(Arrays.asList(
                    PhoneAccount.SCHEME_TEL, PhoneAccount.SCHEME_VOICEMAIL))
            .setExtras(TEST_BUNDLE)
            .build();

    public static final PhoneAccount TEST_CALL_MANAGER_PHONE_ACCOUNT = PhoneAccount.builder(
            TEST_PHONE_ACCOUNT_HANDLE, ACCOUNT_LABEL)
            .setAddress(Uri.parse("tel:555-TEST"))
            .setSubscriptionAddress(Uri.parse("tel:555-TEST"))
            .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER |
                    PhoneAccount.CAPABILITY_CONNECTION_MANAGER)
            .setHighlightColor(Color.RED)
            .setShortDescription(ACCOUNT_LABEL)
            .setSupportedUriSchemes(Arrays.asList(
                    PhoneAccount.SCHEME_TEL, PhoneAccount.SCHEME_VOICEMAIL))
            .build();

    public static final PhoneAccount TEST_SKIP_CALL_FILTERING_ACCOUNT = PhoneAccount.builder(
                    TEST_PHONE_ACCOUNT_HANDLE, ACCOUNT_LABEL)
            .setAddress(Uri.parse("tel:555-TEST"))
            .setSubscriptionAddress(Uri.parse("tel:555-TEST"))
            .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER
                    | PhoneAccount.CAPABILITY_VIDEO_CALLING)
            .setHighlightColor(Color.RED)
            .setShortDescription(ACCOUNT_LABEL)
            .setSupportedUriSchemes(Arrays.asList(
                    PhoneAccount.SCHEME_TEL, PhoneAccount.SCHEME_VOICEMAIL))
            .setExtras(createTestBundle(true))
            .build();

    private static PhoneAccount copyPhoneAccountAndOverrideCapabilities(
            PhoneAccount base, int newCapabilities) {
        return base.toBuilder().setCapabilities(newCapabilities).build();
    }

    private static PhoneAccount copyPhoneAccountAndAddCapabilities(
            PhoneAccount base, int capabilitiesToAdd) {
        return copyPhoneAccountAndOverrideCapabilities(
                base, base.getCapabilities() | capabilitiesToAdd);
    }

    private Context mContext;
    private TelecomManager mTelecomManager;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = getInstrumentation().getContext();
        if (!TestUtils.shouldTestTelecom(mContext)) {
            return;
        }
        // We do not expect CTS to be the default dialer, since it confers some permissions that we
        // explicitly assume that we don't hold during testing.
        TestUtils.setDefaultDialer(getInstrumentation(), "");

        mTelecomManager = (TelecomManager) mContext.getSystemService(Context.TELECOM_SERVICE);
        mPhoneAccountRegisteredLatch = new TestUtils.InvokeCounter("registerPhoneAcct");
        mPhoneAccountUnRegisteredLatch = new TestUtils.InvokeCounter("unRegisterPhoneAcct");
    }

    @Override
    protected void tearDown() throws Exception {
        if (!TestUtils.shouldTestTelecom(mContext)) {
            return;
        }
        try {
            mTelecomManager.unregisterPhoneAccount(TEST_PHONE_ACCOUNT_HANDLE);
            PhoneAccount retrievedPhoneAccount = mTelecomManager.getPhoneAccount(
                    TEST_PHONE_ACCOUNT_HANDLE);
            assertNull("Test account not deregistered.", retrievedPhoneAccount);
        } finally {
            // Force tearDown if setUp errors out to ensure unused listeners are cleaned up.
            super.tearDown();
        }
    }

    public void testRegisterPhoneAccount_simultaneousCallingRestrictions() {
        if (!shouldTestTelecom(mContext) || !Flags.simultaneousCallingIndications()) {
            return;
        }
        PhoneAccountHandle testPhoneAccountInvalid = new PhoneAccountHandle(
                        new ComponentName("com.android.anotherPackage", COMPONENT),
                        ACCOUNT_ID_1);

        // Empty Set
        PhoneAccount accountEmptyRestrictions = new PhoneAccount.Builder(TEST_SIM_PHONE_ACCOUNT)
                .setSimultaneousCallingRestriction(Collections.emptySet())
                .build();
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelecomManager, tm -> {
            try {
                tm.registerPhoneAccount(accountEmptyRestrictions);
                PhoneAccount testAcct = tm.getPhoneAccount(TEST_PHONE_ACCOUNT_HANDLE);
                assertTrue(testAcct.hasSimultaneousCallingRestriction());
                assertEquals(Collections.emptySet(), testAcct.getSimultaneousCallingRestriction());
            } catch (SecurityException e) {
                throw new AssertionError("TelecomManager#registerPhoneAccount must not throw a"
                        + " SecurityException if there is a restriction set with no entries");
            } finally {
                tm.unregisterPhoneAccount(TEST_PHONE_ACCOUNT_HANDLE);
            }
        });

        // One valid entry in the Set
        Set<PhoneAccountHandle> handles = new HashSet<>(2);
        handles.add(TEST_PHONE_ACCOUNT_HANDLE_2);
        PhoneAccount accountRestrictions = new PhoneAccount.Builder(TEST_SIM_PHONE_ACCOUNT)
                .setSimultaneousCallingRestriction(handles)
                .build();

        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelecomManager, tm -> {
            try {
                tm.registerPhoneAccount(accountRestrictions);
                PhoneAccount testAcct = tm.getPhoneAccount(TEST_PHONE_ACCOUNT_HANDLE);
                assertTrue(testAcct.hasSimultaneousCallingRestriction());
                assertEquals(handles, testAcct.getSimultaneousCallingRestriction());
            } catch (SecurityException e) {
                throw new AssertionError("TelecomManager#registerPhoneAccount must not throw a"
                        + " SecurityException if there is a restriction set with"
                        + " PhoneAccountHandles containing the same package name");
            } finally {
                tm.unregisterPhoneAccount(TEST_PHONE_ACCOUNT_HANDLE);
            }
        });

        // One valid and one invalid entry in the Set
        handles.add(testPhoneAccountInvalid);
        PhoneAccount accountInvalidRestrictions = new PhoneAccount.Builder(TEST_SIM_PHONE_ACCOUNT)
                .setSimultaneousCallingRestriction(handles)
                .build();
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelecomManager, tm -> {
            try {
                tm.registerPhoneAccount(accountInvalidRestrictions);
                throw new AssertionError("TelecomManager#registerPhoneAccount MUST throw a"
                        + " SecurityException if there is a restriction set containing one or more"
                        + " PhoneAccountHandles with a different package name");
            } catch (SecurityException e) {
                //expected
            } finally {
                tm.unregisterPhoneAccount(TEST_PHONE_ACCOUNT_HANDLE);
            }
        });
    }

    public void testRegisterPhoneAccount_correctlyThrowsSecurityException() throws Exception {
        if (!shouldTestTelecom(mContext)) {
            return;
        }
        try {
            mTelecomManager.registerPhoneAccount(TEST_SIM_PHONE_ACCOUNT);
            fail("TelecomManager.registerPhoneAccount should throw SecurityException if "
                    + "not a system app.");
        } catch (SecurityException e) {
            assertTrue("Unexpected security exception.", (e.getMessage().indexOf(
                    "android.permission.REGISTER_SIM_SUBSCRIPTION") >= 0));
        }
    }

    public void testRegisterPhoneAccount_NotEnabledAutomatically() throws Exception {
        if (!shouldTestTelecom(mContext)) {
            return;
        }
        mTelecomManager.registerPhoneAccount(TEST_NO_SIM_PHONE_ACCOUNT);
        PhoneAccount retrievedPhoneAccount = mTelecomManager.getPhoneAccount(
                TEST_PHONE_ACCOUNT_HANDLE);
        assertNotNull("Failed to retrieve test account.", retrievedPhoneAccount);
        assertFalse("Phone account should not be automatically enabled.",
                retrievedPhoneAccount.isEnabled());
    }

    public void testRegisterPhoneAccount_DisallowEnable() throws Exception {
        if (!shouldTestTelecom(mContext)) {
            return;
        }
        Method setIsEnabled = null;
        PhoneAccount.Builder phoneAccountBuilder = PhoneAccount.builder(
                TEST_PHONE_ACCOUNT_HANDLE, ACCOUNT_LABEL)
                .setAddress(Uri.parse("tel:555-TEST"))
                .setSubscriptionAddress(Uri.parse("tel:555-TEST"))
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .setHighlightColor(Color.RED)
                .setShortDescription(ACCOUNT_LABEL)
                .setSupportedUriSchemes(Arrays.asList("tel"));
        try {
            setIsEnabled = PhoneAccount.Builder.class.getDeclaredMethod(
                    "setIsEnabled", boolean.class);
        } catch (NoSuchMethodException e) {
            // This is the ideal case; ideally we should NOT be able to even reflect this method
            // since its hidden.
            return;
        }
        // However, if reflection somehow finds the @hide method, we'll try executing it.
        setIsEnabled.invoke(phoneAccountBuilder, true);
        final PhoneAccount phoneAccount  = phoneAccountBuilder.build();
        mTelecomManager.registerPhoneAccount(phoneAccount);
        PhoneAccount retrievedPhoneAccount = mTelecomManager.getPhoneAccount(
                TEST_PHONE_ACCOUNT_HANDLE);
        assertNotNull("Failed to retrieve test account.", retrievedPhoneAccount);
        assertFalse("3rd party app cannot enable its own phone account.",
                retrievedPhoneAccount.isEnabled());
    }

    public void testRegisterPhoneAccount_ListEnabledAccounts() throws Exception {
        if (!shouldTestTelecom(mContext)) {
            return;
        }
        mTelecomManager.registerPhoneAccount(TEST_NO_SIM_PHONE_ACCOUNT);
        final List<PhoneAccountHandle> oldAccounts = mTelecomManager.getCallCapablePhoneAccounts();
        final int oldAccountsListSize = oldAccounts.size();
        if (oldAccountsListSize > 0) {
            assertFalse("Enabled Phone accounts should not contain the test account.",
                    oldAccounts.contains(TEST_PHONE_ACCOUNT_HANDLE));
        }

        try {
            final List<PhoneAccountHandle> allAccounts =
                    mTelecomManager.getCallCapablePhoneAccounts(true);
            assertTrue("No results expected without READ_PRIVILEGED_PHONE_STATE",
                    allAccounts.isEmpty());
        } catch (SecurityException e) {
            // expected
        }

        final List<PhoneAccountHandle> allAccounts =
                ShellIdentityUtils.invokeMethodWithShellPermissions(mTelecomManager,
                        (telecomManager) -> telecomManager.getCallCapablePhoneAccounts(true));
        assertTrue("All Phone accounts should contain the test account.",
                allAccounts.contains(TEST_PHONE_ACCOUNT_HANDLE));

        TestUtils.enablePhoneAccount(getInstrumentation(), TEST_PHONE_ACCOUNT_HANDLE);
        final List<PhoneAccountHandle> newAccounts = mTelecomManager.getCallCapablePhoneAccounts();
        assertNotNull("No enabled Phone account found.", newAccounts);
        assertEquals("1 new enabled Phone account expected.", newAccounts.size(),
                oldAccountsListSize + 1);
        assertTrue("Enabled Phone accounts do not contain the test account.",
                newAccounts.contains(TEST_PHONE_ACCOUNT_HANDLE));
    }

    public void testRegisterPhoneAccount_CheckCapabilities() throws Exception {
        if (!shouldTestTelecom(mContext)) {
            return;
        }
        mTelecomManager.registerPhoneAccount(TEST_NO_SIM_PHONE_ACCOUNT);
        PhoneAccount retrievedPhoneAccount = mTelecomManager.getPhoneAccount(
                TEST_PHONE_ACCOUNT_HANDLE);
        assertTrue("Phone account should have call provider & video calling capability.",
                retrievedPhoneAccount.hasCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER |
                        PhoneAccount.CAPABILITY_VIDEO_CALLING));
    }

    public void testRegisterPhoneAccount_CheckExtras() throws Exception {
        if (!shouldTestTelecom(mContext)) {
            return;
        }
        mTelecomManager.registerPhoneAccount(TEST_NO_SIM_PHONE_ACCOUNT);
        PhoneAccount retrievedPhoneAccount = mTelecomManager.getPhoneAccount(
                TEST_PHONE_ACCOUNT_HANDLE);
        Bundle extras = retrievedPhoneAccount.getExtras();
        assertTrue(extras.containsKey(PhoneAccount.EXTRA_CALL_SUBJECT_CHARACTER_ENCODING));
        assertEquals(TEST_ENCODING,
                extras.getString(PhoneAccount.EXTRA_CALL_SUBJECT_CHARACTER_ENCODING));
        assertTrue(extras.containsKey(PhoneAccount.EXTRA_CALL_SUBJECT_MAX_LENGTH));
        assertEquals(TEST_LENGTH,
                extras.getInt(PhoneAccount.EXTRA_CALL_SUBJECT_MAX_LENGTH));
    }

    public void testRegisterPhoneAccount_CheckURISchemeSupported() throws Exception {
        if (!shouldTestTelecom(mContext)) {
            return;
        }
        mTelecomManager.registerPhoneAccount(TEST_NO_SIM_PHONE_ACCOUNT);
        PhoneAccount retrievedPhoneAccount = mTelecomManager.getPhoneAccount(
                TEST_PHONE_ACCOUNT_HANDLE);
        assertTrue("Phone account should support tel URI scheme.",
                retrievedPhoneAccount.supportsUriScheme(PhoneAccount.SCHEME_TEL));
        assertTrue("Phone account should support voicemail URI scheme.",
                retrievedPhoneAccount.supportsUriScheme(PhoneAccount.SCHEME_VOICEMAIL));
    }

    /**
     * Verifies that the {@link TelecomManager#ACTION_PHONE_ACCOUNT_REGISTERED} intent is sent to
     * the default dialer when a phone account is registered and,
     * {@link TelecomManager#ACTION_PHONE_ACCOUNT_UNREGISTERED} is sent when a phone account is
     * unregistered.
     * @throws Exception
     */
    public void testRegisterUnregisterPhoneAccountIntent() throws Exception {
        if (!shouldTestTelecom(mContext)) {
            return;
        }

        MockPhoneAccountChangedReceiver.setIntentListener(mPhoneAccountIntentListener);
        String previousDefaultDialer = TestUtils.getDefaultDialer(getInstrumentation());
        try {
            TestUtils.setDefaultDialer(getInstrumentation(), TestUtils.PACKAGE);

            mTelecomManager.registerPhoneAccount(TEST_NO_SIM_PHONE_ACCOUNT);

            mPhoneAccountRegisteredLatch.waitForCount(1);
            PhoneAccountHandle handle =
                    (PhoneAccountHandle) mPhoneAccountRegisteredLatch.getArgs(0)[0];
            assertEquals(TEST_PHONE_ACCOUNT_HANDLE, handle);

            mTelecomManager.unregisterPhoneAccount(TEST_PHONE_ACCOUNT_HANDLE);
            mPhoneAccountUnRegisteredLatch.waitForCount(1);
            PhoneAccountHandle handle2 =
                    (PhoneAccountHandle) mPhoneAccountUnRegisteredLatch.getArgs(0)[0];
            assertEquals(TEST_PHONE_ACCOUNT_HANDLE, handle2);
        } finally {
            TestUtils.setDefaultDialer(getInstrumentation(), previousDefaultDialer);
        }
    }

    public void testRegisterPhoneAccount_VoiceIndicationCapabilities_NoPrerequisiteCapabilities()
            throws Exception {
        if (!shouldTestTelecom(mContext)) {
            return;
        }
        try {
            mTelecomManager.registerPhoneAccount(
                    copyPhoneAccountAndAddCapabilities(
                            TEST_NO_SIM_PHONE_ACCOUNT,
                            PhoneAccount.CAPABILITY_SUPPORTS_VOICE_CALLING_INDICATIONS));
            fail("TelecomManager.registerPhoneAccount should throw SecurityException if "
                    + "PhoneAccounts declare CAPABILITY_SUPPORTS_VOICE_CALLING_INDICATIONS but not "
                    + "CAPABILITY_SIM_SUBSCRIPTION or CAPABILITY_CONNECTION_MANAGER");
        } catch (SecurityException e) {
            // expected
        }
        try {
            mTelecomManager.registerPhoneAccount(
                    copyPhoneAccountAndAddCapabilities(
                            TEST_NO_SIM_PHONE_ACCOUNT,
                            PhoneAccount.CAPABILITY_VOICE_CALLING_AVAILABLE));
            fail("TelecomManager.registerPhoneAccount should throw SecurityException if "
                    + "PhoneAccounts declare CAPABILITY_VOICE_CALLING_AVAILABLE but not "
                    + "CAPABILITY_SIM_SUBSCRIPTION or CAPABILITY_CONNECTION_MANAGER");
        } catch (SecurityException e) {
            // expected
        }
        try {
            mTelecomManager.registerPhoneAccount(
                    copyPhoneAccountAndAddCapabilities(
                            TEST_NO_SIM_PHONE_ACCOUNT,
                            PhoneAccount.CAPABILITY_SUPPORTS_VOICE_CALLING_INDICATIONS
                                    | PhoneAccount.CAPABILITY_VOICE_CALLING_AVAILABLE));
            fail("TelecomManager.registerPhoneAccount should throw SecurityException if "
                    + "PhoneAccounts declare CAPABILITY_SUPPORTS_VOICE_CALLING_INDICATIONS and "
                    + "CAPABILITY_VOICE_CALLING_AVAILABLE but not CAPABILITY_SIM_SUBSCRIPTION or "
                    + "CAPABILITY_CONNECTION_MANAGER");
        } catch (SecurityException e) {
            // expected
        }
    }

    public void testRegisterPhoneAccount_VoiceIndicationCapabilities_SimSubscription()
            throws Exception {
        if (!shouldTestTelecom(mContext)) {
            return;
        }
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                mTelecomManager,
                tm ->
                        tm.registerPhoneAccount(
                                copyPhoneAccountAndAddCapabilities(
                                        TEST_SIM_PHONE_ACCOUNT,
                                        PhoneAccount
                                                .CAPABILITY_SUPPORTS_VOICE_CALLING_INDICATIONS)),
                "android.permission.REGISTER_SIM_SUBSCRIPTION");
        PhoneAccount retrievedPhoneAccount =
                mTelecomManager.getPhoneAccount(TEST_PHONE_ACCOUNT_HANDLE);
        assertTrue(
                "Phone account should have call SIM subscription & voice indication capability.",
                retrievedPhoneAccount.hasCapabilities(
                        PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION
                                | PhoneAccount.CAPABILITY_SUPPORTS_VOICE_CALLING_INDICATIONS));

        // Adding in CAPABILITY_VOICE_CALLING_AVAILABLE is how the account dynamically indicates
        // whether it can _currently_ place voice calls or not.
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                mTelecomManager,
                tm ->
                        tm.registerPhoneAccount(
                                copyPhoneAccountAndAddCapabilities(
                                        TEST_SIM_PHONE_ACCOUNT,
                                        PhoneAccount.CAPABILITY_SUPPORTS_VOICE_CALLING_INDICATIONS
                                                | PhoneAccount.CAPABILITY_VOICE_CALLING_AVAILABLE)),
                "android.permission.REGISTER_SIM_SUBSCRIPTION");
        retrievedPhoneAccount = mTelecomManager.getPhoneAccount(TEST_PHONE_ACCOUNT_HANDLE);
        assertTrue(
                "Phone account should have call SIM subscription & voice indication capabilities.",
                retrievedPhoneAccount.hasCapabilities(
                        PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION
                                | PhoneAccount.CAPABILITY_SUPPORTS_VOICE_CALLING_INDICATIONS
                                | PhoneAccount.CAPABILITY_VOICE_CALLING_AVAILABLE));
    }

    public void testRegisterPhoneAccount_ExtraCallFiltering() throws Exception {
        if (!shouldTestTelecom(mContext)) {
            return;
        }

        // Register phone account without MODIFY_PHONE_STATE permission. Registration should fail.
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    mTelecomManager,
                    tm -> tm.registerPhoneAccount(TEST_SKIP_CALL_FILTERING_ACCOUNT),
                    "android.permission.REGISTER_SIM_SUBSCRIPTION");
            fail("TelecomManager#registerPhoneAccount should throw SecurityException since caller "
                    + "is not a system app.");
        } catch (SecurityException e) {
            // expected (caller should have the MODIFY_PHONE_STATE permission)
        }

        // Register phone account with MODIFY_PHONE_STATE permission. Registration should still fail
        // because CTS app is not considered a system app but holding the MODIFY_PHONE_STATE
        // permission is only granted to system apps so this check should be sufficient.
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelecomManager, tm -> {
            try {
                tm.registerPhoneAccount(TEST_SKIP_CALL_FILTERING_ACCOUNT);
                fail("TelecomManager#registerPhoneAccount should not throw a SecurityException"
                        + "as the caller is a system app (granted MODIFY_PHONE_STATE permission)");
            } catch (SecurityException e) {
                // expected
                String callFilteringSystemAppErrorMsg = "EXTRA_SKIP_CALL_FILTERING is only "
                        + "available to system apps.";
                assertEquals(callFilteringSystemAppErrorMsg, e.getMessage());
            }
        });
    }
}
