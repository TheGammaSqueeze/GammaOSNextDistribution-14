/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.server.telecom.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.Nullable;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.PersistableBundle;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.telecom.Log;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.util.Xml;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.MediumTest;
import androidx.test.filters.SmallTest;

import com.android.internal.telecom.IConnectionService;
import com.android.internal.telephony.flags.FeatureFlags;
import com.android.internal.util.FastXmlSerializer;
import com.android.server.telecom.AppLabelProxy;
import com.android.server.telecom.DefaultDialerCache;
import com.android.server.telecom.PhoneAccountRegistrar;
import com.android.server.telecom.PhoneAccountRegistrar.DefaultPhoneAccountHandle;
import com.android.server.telecom.TelecomSystem;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlSerializer;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RunWith(JUnit4.class)
public class PhoneAccountRegistrarTest extends TelecomTestCase {

    private static final int MAX_VERSION = Integer.MAX_VALUE;
    private static final int INVALID_CHAR_LIMIT_COUNT =
            PhoneAccountRegistrar.MAX_PHONE_ACCOUNT_FIELD_CHAR_LIMIT + 1;
    private static final String INVALID_STR = "a".repeat(INVALID_CHAR_LIMIT_COUNT);
    private static final String FILE_NAME = "phone-account-registrar-test-1223.xml";
    private static final String TEST_LABEL = "right";
    private static final String TEST_ID = "123";
    private final String PACKAGE_1 = "PACKAGE_1";
    private final String PACKAGE_2 = "PACKAGE_2";
    private final String COMPONENT_NAME = "com.android.server.telecom.tests.MockConnectionService";
    private final UserHandle USER_HANDLE_10 = new UserHandle(10);
    private final TelecomSystem.SyncRoot mLock = new TelecomSystem.SyncRoot() { };
    private PhoneAccountRegistrar mRegistrar;
    @Mock private SubscriptionManager mSubscriptionManager;
    @Mock private TelecomManager mTelecomManager;
    @Mock private DefaultDialerCache mDefaultDialerCache;
    @Mock private AppLabelProxy mAppLabelProxy;
    @Mock private FeatureFlags mTelephonyFeatureFlags;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.initMocks(this);
        mComponentContextFixture.setTelecomManager(mTelecomManager);
        mComponentContextFixture.setSubscriptionManager(mSubscriptionManager);
        new File(
                mComponentContextFixture.getTestDouble().getApplicationContext().getFilesDir(),
                FILE_NAME)
                .delete();
        when(mDefaultDialerCache.getDefaultDialerApplication(anyInt()))
                .thenReturn("com.android.dialer");
        when(mAppLabelProxy.getAppLabel(anyString()))
                .thenReturn(TEST_LABEL);
        mRegistrar = new PhoneAccountRegistrar(
                mComponentContextFixture.getTestDouble().getApplicationContext(), mLock, FILE_NAME,
                mDefaultDialerCache, mAppLabelProxy, mTelephonyFeatureFlags, mFeatureFlags);
        when(mFeatureFlags.onlyUpdateTelephonyOnValidSubIds()).thenReturn(false);
        when(mTelephonyFeatureFlags.workProfileApiSplit()).thenReturn(false);
    }

    @Override
    @After
    public void tearDown() throws Exception {
        mRegistrar = null;
        new File(
                mComponentContextFixture.getTestDouble().getApplicationContext().getFilesDir(),
                FILE_NAME)
                .delete();
        super.tearDown();
    }

    @MediumTest
    @Test
    public void testPhoneAccountHandle() throws Exception {
        PhoneAccountHandle input = new PhoneAccountHandle(new ComponentName("pkg0", "cls0"), "id0");
        PhoneAccountHandle result = roundTripXml(this, input,
                PhoneAccountRegistrar.sPhoneAccountHandleXml, mContext,
                mTelephonyFeatureFlags, mFeatureFlags);
        assertPhoneAccountHandleEquals(input, result);

        PhoneAccountHandle inputN = new PhoneAccountHandle(new ComponentName("pkg0", "cls0"), null);
        PhoneAccountHandle resultN = roundTripXml(this, inputN,
                PhoneAccountRegistrar.sPhoneAccountHandleXml, mContext,
                mTelephonyFeatureFlags, mFeatureFlags);
        Log.i(this, "inputN = %s, resultN = %s", inputN, resultN);
        assertPhoneAccountHandleEquals(inputN, resultN);
    }

    @MediumTest
    @Test
    public void testPhoneAccount() throws Exception {
        Bundle testBundle = new Bundle();
        testBundle.putInt("EXTRA_INT_1", 1);
        testBundle.putInt("EXTRA_INT_100", 100);
        testBundle.putBoolean("EXTRA_BOOL_TRUE", true);
        testBundle.putBoolean("EXTRA_BOOL_FALSE", false);
        testBundle.putString("EXTRA_STR1", "Hello");
        testBundle.putString("EXTRA_STR2", "There");

        PhoneAccount input = makeQuickAccountBuilder("id0", 0, null)
                .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
                .addSupportedUriScheme(PhoneAccount.SCHEME_VOICEMAIL)
                .setExtras(testBundle)
                .setIsEnabled(true)
                .build();
        PhoneAccount result = roundTripXml(this, input, PhoneAccountRegistrar.sPhoneAccountXml,
                mContext, mTelephonyFeatureFlags, mFeatureFlags);

        assertPhoneAccountEquals(input, result);
    }

    @MediumTest
    @Test
    public void testPhoneAccountParsing_simultaneousCallingRestriction() throws Exception {
        doReturn(true).when(mTelephonyFeatureFlags).simultaneousCallingIndications();
        // workaround: UserManager converts the user to a serial and back, we need to mock this
        // behavior, unfortunately: USER_HANDLE_10 <-> 10L
        UserManager userManager = mContext.getSystemService(UserManager.class);
        doReturn(10L).when(userManager).getSerialNumberForUser(eq(USER_HANDLE_10));
        doReturn(USER_HANDLE_10).when(userManager).getUserForSerialNumber(eq(10L));
        Bundle testBundle = new Bundle();
        testBundle.putInt("EXTRA_INT_1", 1);
        testBundle.putInt("EXTRA_INT_100", 100);
        testBundle.putBoolean("EXTRA_BOOL_TRUE", true);
        testBundle.putBoolean("EXTRA_BOOL_FALSE", false);
        testBundle.putString("EXTRA_STR1", "Hello");
        testBundle.putString("EXTRA_STR2", "There");

        Set<PhoneAccountHandle> restriction = new HashSet<>(10);
        for (int i = 0; i < 10; i++) {
            restriction.add(makeQuickAccountHandleForUser("id" + i, USER_HANDLE_10));
        }

        PhoneAccount input = makeQuickAccountBuilder("id0", 0, USER_HANDLE_10)
                .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
                .addSupportedUriScheme(PhoneAccount.SCHEME_VOICEMAIL)
                .setExtras(testBundle)
                .setIsEnabled(true)
                .setSimultaneousCallingRestriction(restriction)
                .build();
        PhoneAccount result = roundTripXml(this, input, PhoneAccountRegistrar.sPhoneAccountXml,
                mContext, mTelephonyFeatureFlags, mFeatureFlags);

        assertPhoneAccountEquals(input, result);
    }

    @MediumTest
    @Test
    public void testPhoneAccountParsing_simultaneousCallingRestrictionOnOffFlag() throws Exception {
        // Start the test with the flag on
        doReturn(true).when(mTelephonyFeatureFlags).simultaneousCallingIndications();
        // workaround: UserManager converts the user to a serial and back, we need to mock this
        // behavior, unfortunately: USER_HANDLE_10 <-> 10L
        UserManager userManager = mContext.getSystemService(UserManager.class);
        doReturn(10L).when(userManager).getSerialNumberForUser(eq(USER_HANDLE_10));
        doReturn(USER_HANDLE_10).when(userManager).getUserForSerialNumber(eq(10L));
        Bundle testBundle = new Bundle();
        testBundle.putInt("EXTRA_INT_1", 1);
        testBundle.putInt("EXTRA_INT_100", 100);
        testBundle.putBoolean("EXTRA_BOOL_TRUE", true);
        testBundle.putBoolean("EXTRA_BOOL_FALSE", false);
        testBundle.putString("EXTRA_STR1", "Hello");
        testBundle.putString("EXTRA_STR2", "There");

        Set<PhoneAccountHandle> restriction = new HashSet<>(10);
        for (int i = 0; i < 10; i++) {
            restriction.add(makeQuickAccountHandleForUser("id" + i, USER_HANDLE_10));
        }

        PhoneAccount input = makeQuickAccountBuilder("id0", 0, USER_HANDLE_10)
                .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
                .addSupportedUriScheme(PhoneAccount.SCHEME_VOICEMAIL)
                .setExtras(testBundle)
                .setIsEnabled(true)
                .setSimultaneousCallingRestriction(restriction)
                .build();
        byte[] xmlData = toXml(input, PhoneAccountRegistrar.sPhoneAccountXml, mContext,
                mTelephonyFeatureFlags);
        // Simulate turning off the flag after reboot
        doReturn(false).when(mTelephonyFeatureFlags).simultaneousCallingIndications();
        PhoneAccount result = fromXml(xmlData, PhoneAccountRegistrar.sPhoneAccountXml, mContext,
                mTelephonyFeatureFlags, mFeatureFlags);

        assertNotNull(result);
        assertFalse(result.hasSimultaneousCallingRestriction());
    }

    @MediumTest
    @Test
    public void testPhoneAccountParsing_simultaneousCallingRestrictionOffOnFlag() throws Exception {
        // Start the test with the flag on
        doReturn(false).when(mTelephonyFeatureFlags).simultaneousCallingIndications();
        Bundle testBundle = new Bundle();
        testBundle.putInt("EXTRA_INT_1", 1);
        testBundle.putInt("EXTRA_INT_100", 100);
        testBundle.putBoolean("EXTRA_BOOL_TRUE", true);
        testBundle.putBoolean("EXTRA_BOOL_FALSE", false);
        testBundle.putString("EXTRA_STR1", "Hello");
        testBundle.putString("EXTRA_STR2", "There");

        PhoneAccount input = makeQuickAccountBuilder("id0", 0, USER_HANDLE_10)
                .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
                .addSupportedUriScheme(PhoneAccount.SCHEME_VOICEMAIL)
                .setExtras(testBundle)
                .setIsEnabled(true)
                .build();
        byte[] xmlData = toXml(input, PhoneAccountRegistrar.sPhoneAccountXml, mContext,
                mTelephonyFeatureFlags);
        // Simulate turning on the flag after reboot
        doReturn(true).when(mTelephonyFeatureFlags).simultaneousCallingIndications();
        PhoneAccount result = fromXml(xmlData, PhoneAccountRegistrar.sPhoneAccountXml, mContext,
                mTelephonyFeatureFlags, mFeatureFlags);

        assertPhoneAccountEquals(input, result);
    }

    @SmallTest
    @Test
    public void testFilterPhoneAccountForTest() throws Exception {
        ComponentName componentA = new ComponentName("a", "a");
        ComponentName componentB1 = new ComponentName("b", "b1");
        ComponentName componentB2 = new ComponentName("b", "b2");
        ComponentName componentC = new ComponentName("c", "c");

        PhoneAccount simAccountA = new PhoneAccount.Builder(
                makeQuickAccountHandle(componentA, "1"), "1")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .setIsEnabled(true)
                .build();

        List<PhoneAccount> accountAList = new ArrayList<>();
        accountAList.add(simAccountA);

        PhoneAccount simAccountB1 = new PhoneAccount.Builder(
                makeQuickAccountHandle(componentB1, "2"), "2")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .setIsEnabled(true)
                .build();

        PhoneAccount simAccountB2 = new PhoneAccount.Builder(
                makeQuickAccountHandle(componentB2, "3"), "3")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .setIsEnabled(true)
                .build();

        List<PhoneAccount> accountBList = new ArrayList<>();
        accountBList.add(simAccountB1);
        accountBList.add(simAccountB2);

        PhoneAccount simAccountC = new PhoneAccount.Builder(
                makeQuickAccountHandle(componentC, "4"), "4")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .setIsEnabled(true)
                .build();

        List<PhoneAccount> accountCList = new ArrayList<>();
        accountCList.add(simAccountC);

        List<PhoneAccount> allAccounts = new ArrayList<>();
        allAccounts.addAll(accountAList);
        allAccounts.addAll(accountBList);
        allAccounts.addAll(accountCList);

        assertEquals(allAccounts, mRegistrar.filterRestrictedPhoneAccounts(allAccounts));

        mRegistrar.setTestPhoneAccountPackageNameFilter(componentA.getPackageName());
        assertEquals(accountAList, mRegistrar.filterRestrictedPhoneAccounts(allAccounts));

        mRegistrar.setTestPhoneAccountPackageNameFilter(componentB1.getPackageName());
        assertEquals(accountBList, mRegistrar.filterRestrictedPhoneAccounts(allAccounts));

        mRegistrar.setTestPhoneAccountPackageNameFilter(componentC.getPackageName());
        assertEquals(accountCList, mRegistrar.filterRestrictedPhoneAccounts(allAccounts));

        mRegistrar.setTestPhoneAccountPackageNameFilter(null);
        assertEquals(allAccounts, mRegistrar.filterRestrictedPhoneAccounts(allAccounts));
    }

    @MediumTest
    @Test
    public void testDefaultPhoneAccountHandleEmptyGroup() throws Exception {
        DefaultPhoneAccountHandle input = new DefaultPhoneAccountHandle(Process.myUserHandle(),
                makeQuickAccountHandle("i1"), "");
        UserManager userManager = mContext.getSystemService(UserManager.class);
        when(userManager.getSerialNumberForUser(input.userHandle))
                .thenReturn(0L);
        when(userManager.getUserForSerialNumber(0L))
                .thenReturn(input.userHandle);
        DefaultPhoneAccountHandle result = roundTripXml(this, input,
                PhoneAccountRegistrar.sDefaultPhoneAccountHandleXml, mContext,
                mTelephonyFeatureFlags, mFeatureFlags);

        assertDefaultPhoneAccountHandleEquals(input, result);
    }

    /**
     * Test to ensure non-supported values
     * @throws Exception
     */
    @MediumTest
    @Test
    public void testPhoneAccountExtrasEdge() throws Exception {
        Bundle testBundle = new Bundle();
        // Ensure null values for string are not persisted.
        testBundle.putString("EXTRA_STR2", null);
        //

        // Ensure unsupported data types are not persisted.
        testBundle.putShort("EXTRA_SHORT", (short) 2);
        testBundle.putByte("EXTRA_BYTE", (byte) 1);
        testBundle.putParcelable("EXTRA_PARC", new Rect(1, 1, 1, 1));
        // Put in something valid so the bundle exists.
        testBundle.putString("EXTRA_OK", "OK");

        PhoneAccount input = makeQuickAccountBuilder("id0", 0, null)
                .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
                .addSupportedUriScheme(PhoneAccount.SCHEME_VOICEMAIL)
                .setExtras(testBundle)
                .build();
        PhoneAccount result = roundTripXml(this, input, PhoneAccountRegistrar.sPhoneAccountXml,
                mContext, mTelephonyFeatureFlags, mFeatureFlags);

        Bundle extras = result.getExtras();
        assertFalse(extras.keySet().contains("EXTRA_STR2"));
        assertFalse(extras.keySet().contains("EXTRA_SHORT"));
        assertFalse(extras.keySet().contains("EXTRA_BYTE"));
        assertFalse(extras.keySet().contains("EXTRA_PARC"));
    }

    @MediumTest
    @Test
    public void testState() throws Exception {
        PhoneAccountRegistrar.State input = makeQuickState();
        PhoneAccountRegistrar.State result = roundTripXml(this, input,
                PhoneAccountRegistrar.sStateXml, mContext, mTelephonyFeatureFlags, mFeatureFlags);
        assertStateEquals(input, result);
    }

    private void registerAndEnableAccount(PhoneAccount account) {
        mRegistrar.registerPhoneAccount(account);
        mRegistrar.enablePhoneAccount(account.getAccountHandle(), true);
    }

    @MediumTest
    @Test
    public void testAccounts() throws Exception {
        int i = 0;

        mComponentContextFixture.addConnectionService(makeQuickConnectionServiceComponentName(),
                Mockito.mock(IConnectionService.class));

        registerAndEnableAccount(makeQuickAccountBuilder("id" + i, i++, null)
                .setCapabilities(PhoneAccount.CAPABILITY_CONNECTION_MANAGER
                        | PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .build());
        registerAndEnableAccount(makeQuickAccountBuilder("id" + i, i++, null)
                .setCapabilities(PhoneAccount.CAPABILITY_CONNECTION_MANAGER
                        | PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .build());
        registerAndEnableAccount(makeQuickAccountBuilder("id" + i, i++, null)
                .setCapabilities(PhoneAccount.CAPABILITY_CONNECTION_MANAGER
                        | PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .build());
        registerAndEnableAccount(makeQuickAccountBuilder("id" + i, i++, null)
                .setCapabilities(PhoneAccount.CAPABILITY_CONNECTION_MANAGER)
                .build());
        registerAndEnableAccount(makeQuickAccountBuilder("id" + i, i++, USER_HANDLE_10)
                .setCapabilities(PhoneAccount.CAPABILITY_CONNECTION_MANAGER
                        | PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .build());
        registerAndEnableAccount(makeQuickAccountBuilder("id" + i, i++, USER_HANDLE_10)
                .setCapabilities(PhoneAccount.CAPABILITY_CONNECTION_MANAGER)
                .build());

        assertEquals(6, mRegistrar.
                getAllPhoneAccounts(null, true).size());
        assertEquals(4, mRegistrar.getCallCapablePhoneAccounts(null, false,
                null, true).size());
        assertEquals(null, mRegistrar.getSimCallManagerOfCurrentUser());
        assertEquals(null, mRegistrar.getOutgoingPhoneAccountForSchemeOfCurrentUser(
                PhoneAccount.SCHEME_TEL));
    }

    @MediumTest
    @Test
    public void testSimCallManager() throws Exception {
        // TODO
    }

    @MediumTest
    @Test
    public void testDefaultOutgoing() throws Exception {
        mComponentContextFixture.addConnectionService(makeQuickConnectionServiceComponentName(),
                Mockito.mock(IConnectionService.class));

        // By default, there is no default outgoing account (nothing has been registered)
        assertNull(
                mRegistrar.getOutgoingPhoneAccountForSchemeOfCurrentUser(PhoneAccount.SCHEME_TEL));

        // Register one tel: account
        PhoneAccountHandle telAccount = makeQuickAccountHandle("tel_acct");
        registerAndEnableAccount(new PhoneAccount.Builder(telAccount, "tel_acct")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
                .build());
        PhoneAccountHandle defaultAccount =
                mRegistrar.getOutgoingPhoneAccountForSchemeOfCurrentUser(PhoneAccount.SCHEME_TEL);
        assertEquals(telAccount, defaultAccount);

        // Add a SIP account, make sure tel: doesn't change
        PhoneAccountHandle sipAccount = makeQuickAccountHandle("sip_acct");
        registerAndEnableAccount(new PhoneAccount.Builder(sipAccount, "sip_acct")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .addSupportedUriScheme(PhoneAccount.SCHEME_SIP)
                .build());
        defaultAccount = mRegistrar.getOutgoingPhoneAccountForSchemeOfCurrentUser(
                PhoneAccount.SCHEME_SIP);
        assertEquals(sipAccount, defaultAccount);
        defaultAccount = mRegistrar.getOutgoingPhoneAccountForSchemeOfCurrentUser(
                PhoneAccount.SCHEME_TEL);
        assertEquals(telAccount, defaultAccount);

        // Add a connection manager, make sure tel: doesn't change
        PhoneAccountHandle connectionManager = makeQuickAccountHandle("mgr_acct");
        registerAndEnableAccount(new PhoneAccount.Builder(connectionManager, "mgr_acct")
                .setCapabilities(PhoneAccount.CAPABILITY_CONNECTION_MANAGER)
                .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
                .build());
        defaultAccount = mRegistrar.getOutgoingPhoneAccountForSchemeOfCurrentUser(
                PhoneAccount.SCHEME_TEL);
        assertEquals(telAccount, defaultAccount);

        // Unregister the tel: account, make sure there is no tel: default now.
        mRegistrar.unregisterPhoneAccount(telAccount);
        assertNull(
                mRegistrar.getOutgoingPhoneAccountForSchemeOfCurrentUser(PhoneAccount.SCHEME_TEL));
    }

    @MediumTest
    @Test
    public void testReplacePhoneAccountByGroup() throws Exception {
        mComponentContextFixture.addConnectionService(makeQuickConnectionServiceComponentName(),
                Mockito.mock(IConnectionService.class));

        // By default, there is no default outgoing account (nothing has been registered)
        assertNull(
                mRegistrar.getOutgoingPhoneAccountForSchemeOfCurrentUser(PhoneAccount.SCHEME_TEL));

        // Register one tel: account
        PhoneAccountHandle telAccount1 = makeQuickAccountHandle("tel_acct1");
        registerAndEnableAccount(new PhoneAccount.Builder(telAccount1, "tel_acct1")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
                .setGroupId("testGroup")
                .build());
        mRegistrar.setUserSelectedOutgoingPhoneAccount(telAccount1, Process.myUserHandle());
        PhoneAccountHandle defaultAccount =
                mRegistrar.getOutgoingPhoneAccountForSchemeOfCurrentUser(PhoneAccount.SCHEME_TEL);
        assertEquals(telAccount1, defaultAccount);

        // Add call capable SIP account, make sure tel: doesn't change
        PhoneAccountHandle sipAccount = makeQuickAccountHandle("sip_acct");
        registerAndEnableAccount(new PhoneAccount.Builder(sipAccount, "sip_acct")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
                .build());
        defaultAccount = mRegistrar.getOutgoingPhoneAccountForSchemeOfCurrentUser(
                PhoneAccount.SCHEME_TEL);
        assertEquals(telAccount1, defaultAccount);

        // Replace tel: account with another in the same Group
        PhoneAccountHandle telAccount2 = makeQuickAccountHandle("tel_acct2");
        registerAndEnableAccount(new PhoneAccount.Builder(telAccount2, "tel_acct2")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
                .setGroupId("testGroup")
                .build());
        defaultAccount = mRegistrar.getOutgoingPhoneAccountForSchemeOfCurrentUser(
                PhoneAccount.SCHEME_TEL);
        assertEquals(telAccount2, defaultAccount);
        assertNull(mRegistrar.getPhoneAccountUnchecked(telAccount1));
    }

    @MediumTest
    @Test
    public void testAddSameDefault() throws Exception {
        mComponentContextFixture.addConnectionService(makeQuickConnectionServiceComponentName(),
                Mockito.mock(IConnectionService.class));

        // By default, there is no default outgoing account (nothing has been registered)
        assertNull(
                mRegistrar.getOutgoingPhoneAccountForSchemeOfCurrentUser(PhoneAccount.SCHEME_TEL));

        // Register one tel: account
        PhoneAccountHandle telAccount1 = makeQuickAccountHandle("tel_acct1");
        registerAndEnableAccount(new PhoneAccount.Builder(telAccount1, "tel_acct1")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
                .setGroupId("testGroup")
                .build());
        mRegistrar.setUserSelectedOutgoingPhoneAccount(telAccount1, Process.myUserHandle());
        PhoneAccountHandle defaultAccount =
                mRegistrar.getUserSelectedOutgoingPhoneAccount(Process.myUserHandle());
        assertEquals(telAccount1, defaultAccount);
        mRegistrar.unregisterPhoneAccount(telAccount1);

        // Register Emergency Account and unregister
        PhoneAccountHandle emerAccount = makeQuickAccountHandle("emer_acct");
        registerAndEnableAccount(new PhoneAccount.Builder(emerAccount, "emer_acct")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
                .build());
        defaultAccount =
                mRegistrar.getUserSelectedOutgoingPhoneAccount(Process.myUserHandle());
        assertNull(defaultAccount);
        mRegistrar.unregisterPhoneAccount(emerAccount);

        // Re-register the same account and make sure the default is in place
        registerAndEnableAccount(new PhoneAccount.Builder(telAccount1, "tel_acct1")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
                .setGroupId("testGroup")
                .build());
        defaultAccount =
                mRegistrar.getUserSelectedOutgoingPhoneAccount(Process.myUserHandle());
        assertEquals(telAccount1, defaultAccount);
    }

    @MediumTest
    @Test
    public void testAddSameGroup() throws Exception {
        mComponentContextFixture.addConnectionService(makeQuickConnectionServiceComponentName(),
                Mockito.mock(IConnectionService.class));

        // By default, there is no default outgoing account (nothing has been registered)
        assertNull(
                mRegistrar.getOutgoingPhoneAccountForSchemeOfCurrentUser(PhoneAccount.SCHEME_TEL));

        // Register one tel: account
        PhoneAccountHandle telAccount1 = makeQuickAccountHandle("tel_acct1");
        registerAndEnableAccount(new PhoneAccount.Builder(telAccount1, "tel_acct1")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
                .setGroupId("testGroup")
                .build());
        mRegistrar.setUserSelectedOutgoingPhoneAccount(telAccount1, Process.myUserHandle());
        PhoneAccountHandle defaultAccount =
                mRegistrar.getUserSelectedOutgoingPhoneAccount(Process.myUserHandle());
        assertEquals(telAccount1, defaultAccount);
        mRegistrar.unregisterPhoneAccount(telAccount1);

        // Register Emergency Account and unregister
        PhoneAccountHandle emerAccount = makeQuickAccountHandle("emer_acct");
        registerAndEnableAccount(new PhoneAccount.Builder(emerAccount, "emer_acct")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
                .build());
        defaultAccount =
                mRegistrar.getUserSelectedOutgoingPhoneAccount(Process.myUserHandle());
        assertNull(defaultAccount);
        mRegistrar.unregisterPhoneAccount(emerAccount);

        // Re-register a new account with the same group
        PhoneAccountHandle telAccount2 = makeQuickAccountHandle("tel_acct2");
        registerAndEnableAccount(new PhoneAccount.Builder(telAccount2, "tel_acct2")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
                .setGroupId("testGroup")
                .build());
        defaultAccount =
                mRegistrar.getUserSelectedOutgoingPhoneAccount(Process.myUserHandle());
        assertEquals(telAccount2, defaultAccount);
    }

    @MediumTest
    @Test
    public void testAddSameGroupButDifferentComponent() throws Exception {
        mComponentContextFixture.addConnectionService(makeQuickConnectionServiceComponentName(),
                Mockito.mock(IConnectionService.class));

        // By default, there is no default outgoing account (nothing has been registered)
        assertNull(mRegistrar.getOutgoingPhoneAccountForSchemeOfCurrentUser(
                PhoneAccount.SCHEME_TEL));

        // Register one tel: account
        PhoneAccountHandle telAccount1 = makeQuickAccountHandle("tel_acct1");
        registerAndEnableAccount(new PhoneAccount.Builder(telAccount1, "tel_acct1")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
                .setGroupId("testGroup")
                .build());
        mRegistrar.setUserSelectedOutgoingPhoneAccount(telAccount1, Process.myUserHandle());
        PhoneAccountHandle defaultAccount =
                mRegistrar.getUserSelectedOutgoingPhoneAccount(Process.myUserHandle());
        assertEquals(telAccount1, defaultAccount);
        assertNotNull(mRegistrar.getPhoneAccountUnchecked(telAccount1));

        // Register a new account with the same group, but different Component, so don't replace
        // Default
        PhoneAccountHandle telAccount2 =  makeQuickAccountHandle(
                new ComponentName("other1", "other2"), "tel_acct2");
        registerAndEnableAccount(new PhoneAccount.Builder(telAccount2, "tel_acct2")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
                .setGroupId("testGroup")
                .build());
        assertNotNull(mRegistrar.getPhoneAccountUnchecked(telAccount2));

        defaultAccount =
                mRegistrar.getUserSelectedOutgoingPhoneAccount(Process.myUserHandle());
        assertEquals(telAccount1, defaultAccount);
    }

    @MediumTest
    @Test
    public void testAddSameGroupButDifferentComponent2() throws Exception {
        mComponentContextFixture.addConnectionService(makeQuickConnectionServiceComponentName(),
                Mockito.mock(IConnectionService.class));

        // By default, there is no default outgoing account (nothing has been registered)
        assertNull(mRegistrar.getOutgoingPhoneAccountForSchemeOfCurrentUser(
                PhoneAccount.SCHEME_TEL));

        // Register first tel: account
        PhoneAccountHandle telAccount1 =  makeQuickAccountHandle(
                new ComponentName("other1", "other2"), "tel_acct1");
        registerAndEnableAccount(new PhoneAccount.Builder(telAccount1, "tel_acct1")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
                .setGroupId("testGroup")
                .build());
        assertNotNull(mRegistrar.getPhoneAccountUnchecked(telAccount1));
        mRegistrar.setUserSelectedOutgoingPhoneAccount(telAccount1, Process.myUserHandle());

        // Register second account with the same group, but a second Component, so don't replace
        // Default
        PhoneAccountHandle telAccount2 = makeQuickAccountHandle("tel_acct2");
        registerAndEnableAccount(new PhoneAccount.Builder(telAccount2, "tel_acct2")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
                .setGroupId("testGroup")
                .build());

        PhoneAccountHandle defaultAccount =
                mRegistrar.getOutgoingPhoneAccountForSchemeOfCurrentUser(PhoneAccount.SCHEME_TEL);
        assertEquals(telAccount1, defaultAccount);

        // Register third account with the second component name, but same group id
        PhoneAccountHandle telAccount3 = makeQuickAccountHandle("tel_acct3");
        registerAndEnableAccount(new PhoneAccount.Builder(telAccount3, "tel_acct3")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
                .setGroupId("testGroup")
                .build());

        // Make sure that the default account is still the original PhoneAccount and that the
        // second PhoneAccount with the second ComponentName was replaced by the third PhoneAccount
        defaultAccount =
                mRegistrar.getOutgoingPhoneAccountForSchemeOfCurrentUser(PhoneAccount.SCHEME_TEL);
        assertEquals(telAccount1, defaultAccount);

        assertNotNull(mRegistrar.getPhoneAccountUnchecked(telAccount1));
        assertNull(mRegistrar.getPhoneAccountUnchecked(telAccount2));
        assertNotNull(mRegistrar.getPhoneAccountUnchecked(telAccount3));
    }

    @MediumTest
    @Test
    public void testPhoneAccountParceling() throws Exception {
        PhoneAccountHandle handle = makeQuickAccountHandle("foo");
        roundTripPhoneAccount(new PhoneAccount.Builder(handle, null).build());
        roundTripPhoneAccount(new PhoneAccount.Builder(handle, "foo").build());
        roundTripPhoneAccount(
                new PhoneAccount.Builder(handle, "foo")
                        .setAddress(Uri.parse("tel:123456"))
                        .setCapabilities(23)
                        .setHighlightColor(0xf0f0f0)
                        .setIcon(Icon.createWithResource(
                                "com.android.server.telecom.tests", R.drawable.stat_sys_phone_call))
                        // TODO: set icon tint (0xfefefe)
                        .setShortDescription("short description")
                        .setSubscriptionAddress(Uri.parse("tel:2345678"))
                        .setSupportedUriSchemes(Arrays.asList("tel", "sip"))
                        .setGroupId("testGroup")
                        .build());
        roundTripPhoneAccount(
                new PhoneAccount.Builder(handle, "foo")
                        .setAddress(Uri.parse("tel:123456"))
                        .setCapabilities(23)
                        .setHighlightColor(0xf0f0f0)
                        .setIcon(Icon.createWithBitmap(
                                BitmapFactory.decodeResource(
                                        InstrumentationRegistry.getContext().getResources(),
                                        R.drawable.stat_sys_phone_call)))
                        .setShortDescription("short description")
                        .setSubscriptionAddress(Uri.parse("tel:2345678"))
                        .setSupportedUriSchemes(Arrays.asList("tel", "sip"))
                        .setGroupId("testGroup")
                        .build());
    }

    /**
     * Tests ability to register a self-managed PhoneAccount; verifies that the user defined label
     * is overridden.
     * @throws Exception
     */
    @MediumTest
    @Test
    public void testSelfManagedPhoneAccount() throws Exception {
        mComponentContextFixture.addConnectionService(makeQuickConnectionServiceComponentName(),
                Mockito.mock(IConnectionService.class));

        PhoneAccountHandle selfManagedHandle =  makeQuickAccountHandle(
                new ComponentName("self", "managed"), "selfie1");

        PhoneAccount selfManagedAccount = new PhoneAccount.Builder(selfManagedHandle, "Wrong")
                .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
                .build();

        mRegistrar.registerPhoneAccount(selfManagedAccount);

        PhoneAccount registeredAccount = mRegistrar.getPhoneAccountUnchecked(selfManagedHandle);
        assertEquals(TEST_LABEL, registeredAccount.getLabel());
    }

    @MediumTest
    @Test
    public void testSecurityExceptionIsThrownWhenSelfManagedLacksPermissions() {
        PhoneAccountHandle handle = makeQuickAccountHandle(
                new ComponentName("self", "managed"), "selfie1");

        PhoneAccount accountWithoutCapability = new PhoneAccount.Builder(handle, "label")
                .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
                .build();

        assertFalse(mRegistrar.hasTransactionalCallCapabilities(accountWithoutCapability));

        try {
            mRegistrar.registerPhoneAccount(accountWithoutCapability);
            fail("should not be able to register account");
        } catch (SecurityException securityException) {
            // test pass
        } finally {
            mRegistrar.unregisterPhoneAccount(handle);
        }
    }

    @MediumTest
    @Test
    public void testSelfManagedPhoneAccountWithTransactionalOperations() {
        PhoneAccountHandle handle = makeQuickAccountHandle(
                new ComponentName("self", "managed"), "selfie1");

        PhoneAccount accountWithCapability = new PhoneAccount.Builder(handle, "label")
                .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED |
                        PhoneAccount.CAPABILITY_SUPPORTS_TRANSACTIONAL_OPERATIONS)
                .build();

        assertTrue(mRegistrar.hasTransactionalCallCapabilities(accountWithCapability));

        try {
            mRegistrar.registerPhoneAccount(accountWithCapability);
            PhoneAccount registeredAccount = mRegistrar.getPhoneAccountUnchecked(handle);
            assertEquals(TEST_LABEL, registeredAccount.getLabel().toString());
        } finally {
            mRegistrar.unregisterPhoneAccount(handle);
        }
    }

    @MediumTest
    @Test
    public void testRegisterPhoneAccountAmendsSelfManagedCapabilityInternally() {
        // GIVEN
        PhoneAccountHandle handle = makeQuickAccountHandle(
                new ComponentName("self", "managed"), "selfie1");
        PhoneAccount accountWithCapability = new PhoneAccount.Builder(handle, "label")
                .setCapabilities(
                        PhoneAccount.CAPABILITY_SUPPORTS_TRANSACTIONAL_OPERATIONS)
                .build();

        assertTrue(mRegistrar.hasTransactionalCallCapabilities(accountWithCapability));

        try {
            // WHEN
            mRegistrar.registerPhoneAccount(accountWithCapability);
            PhoneAccount registeredAccount = mRegistrar.getPhoneAccountUnchecked(handle);
            // THEN
            assertEquals(PhoneAccount.CAPABILITY_SELF_MANAGED, (registeredAccount.getCapabilities()
                    & PhoneAccount.CAPABILITY_SELF_MANAGED));
        } finally {
            mRegistrar.unregisterPhoneAccount(handle);
        }
    }

    /**
     * Tests to ensure that when registering a self-managed PhoneAccount, it cannot also be defined
     * as a call provider, connection manager, or sim subscription.
     * @throws Exception
     */
    @MediumTest
    @Test
    public void testSelfManagedCapabilityOverride() throws Exception {
        mComponentContextFixture.addConnectionService(makeQuickConnectionServiceComponentName(),
                Mockito.mock(IConnectionService.class));

        PhoneAccountHandle selfManagedHandle =  makeQuickAccountHandle(
                new ComponentName("self", "managed"), "selfie1");

        PhoneAccount selfManagedAccount = new PhoneAccount.Builder(selfManagedHandle, TEST_LABEL)
                .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED |
                        PhoneAccount.CAPABILITY_CALL_PROVIDER |
                        PhoneAccount.CAPABILITY_CONNECTION_MANAGER |
                        PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)
                .build();

        mRegistrar.registerPhoneAccount(selfManagedAccount);

        PhoneAccount registeredAccount = mRegistrar.getPhoneAccountUnchecked(selfManagedHandle);
        assertEquals(PhoneAccount.CAPABILITY_SELF_MANAGED, registeredAccount.getCapabilities());
    }

    @MediumTest
    @Test
    public void testSortSimFirst() throws Exception {
        ComponentName componentA = new ComponentName("a", "a");
        ComponentName componentB = new ComponentName("b", "b");
        mComponentContextFixture.addConnectionService(componentA,
                Mockito.mock(IConnectionService.class));
        mComponentContextFixture.addConnectionService(componentB,
                Mockito.mock(IConnectionService.class));

        PhoneAccount simAccount = new PhoneAccount.Builder(
                makeQuickAccountHandle(componentB, "2"), "2")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER |
                        PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)
                .setIsEnabled(true)
                .build();

        PhoneAccount nonSimAccount = new PhoneAccount.Builder(
                makeQuickAccountHandle(componentA, "1"), "1")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .setIsEnabled(true)
                .build();

        registerAndEnableAccount(nonSimAccount);
        registerAndEnableAccount(simAccount);

        List<PhoneAccount> accounts = mRegistrar.getAllPhoneAccounts(Process.myUserHandle(), false);
        assertTrue(accounts.get(0).getLabel().toString().equals("2"));
        assertTrue(accounts.get(1).getLabel().toString().equals("1"));
    }

    @MediumTest
    @Test
    public void testSortBySortOrder() throws Exception {
        ComponentName componentA = new ComponentName("a", "a");
        ComponentName componentB = new ComponentName("b", "b");
        ComponentName componentC = new ComponentName("c", "c");
        mComponentContextFixture.addConnectionService(componentA,
                Mockito.mock(IConnectionService.class));
        mComponentContextFixture.addConnectionService(componentB,
                Mockito.mock(IConnectionService.class));
        mComponentContextFixture.addConnectionService(componentC,
                Mockito.mock(IConnectionService.class));

        Bundle account1Extras = new Bundle();
        account1Extras.putInt(PhoneAccount.EXTRA_SORT_ORDER, 1);
        PhoneAccount account1 = new PhoneAccount.Builder(
                makeQuickAccountHandle(componentA, "c"), "c")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .setExtras(account1Extras)
                .build();

        Bundle account2Extras = new Bundle();
        account2Extras.putInt(PhoneAccount.EXTRA_SORT_ORDER, 2);
        PhoneAccount account2 = new PhoneAccount.Builder(
                makeQuickAccountHandle(componentB, "b"), "b")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .setExtras(account2Extras)
                .build();

        PhoneAccount account3 = new PhoneAccount.Builder(
                makeQuickAccountHandle(componentC, "c"), "a")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .build();

        registerAndEnableAccount(account3);
        registerAndEnableAccount(account2);
        registerAndEnableAccount(account1);

        List<PhoneAccount> accounts = mRegistrar.getAllPhoneAccounts(Process.myUserHandle(), false);
        assertTrue(accounts.get(0).getLabel().toString().equals("c"));
        assertTrue(accounts.get(1).getLabel().toString().equals("b"));
        assertTrue(accounts.get(2).getLabel().toString().equals("a"));
    }

    @MediumTest
    @Test
    public void testSortByLabel() throws Exception {
        ComponentName componentA = new ComponentName("a", "a");
        ComponentName componentB = new ComponentName("b", "b");
        ComponentName componentC = new ComponentName("c", "c");
        mComponentContextFixture.addConnectionService(componentA,
                Mockito.mock(IConnectionService.class));
        mComponentContextFixture.addConnectionService(componentB,
                Mockito.mock(IConnectionService.class));
        mComponentContextFixture.addConnectionService(componentC,
                Mockito.mock(IConnectionService.class));

        PhoneAccount account1 = new PhoneAccount.Builder(makeQuickAccountHandle(componentA, "c"),
                "c")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .build();

        PhoneAccount account2 = new PhoneAccount.Builder(makeQuickAccountHandle(componentB, "b"),
                "b")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .build();

        PhoneAccount account3 = new PhoneAccount.Builder(makeQuickAccountHandle(componentC, "a"),
                "a")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .build();

        registerAndEnableAccount(account1);
        registerAndEnableAccount(account2);
        registerAndEnableAccount(account3);

        List<PhoneAccount> accounts = mRegistrar.getAllPhoneAccounts(Process.myUserHandle(), false);
        assertTrue(accounts.get(0).getLabel().toString().equals("a"));
        assertTrue(accounts.get(1).getLabel().toString().equals("b"));
        assertTrue(accounts.get(2).getLabel().toString().equals("c"));
    }

    @MediumTest
    @Test
    public void testSortAll() throws Exception {
        ComponentName componentA = new ComponentName("a", "a");
        ComponentName componentB = new ComponentName("b", "b");
        ComponentName componentC = new ComponentName("c", "c");
        ComponentName componentW = new ComponentName("w", "w");
        ComponentName componentX = new ComponentName("x", "x");
        ComponentName componentY = new ComponentName("y", "y");
        ComponentName componentZ = new ComponentName("z", "z");
        mComponentContextFixture.addConnectionService(componentA,
                Mockito.mock(IConnectionService.class));
        mComponentContextFixture.addConnectionService(componentB,
                Mockito.mock(IConnectionService.class));
        mComponentContextFixture.addConnectionService(componentC,
                Mockito.mock(IConnectionService.class));
        mComponentContextFixture.addConnectionService(componentW,
                Mockito.mock(IConnectionService.class));
        mComponentContextFixture.addConnectionService(componentX,
                Mockito.mock(IConnectionService.class));
        mComponentContextFixture.addConnectionService(componentY,
                Mockito.mock(IConnectionService.class));
        mComponentContextFixture.addConnectionService(componentZ,
                Mockito.mock(IConnectionService.class));

        Bundle account1Extras = new Bundle();
        account1Extras.putInt(PhoneAccount.EXTRA_SORT_ORDER, 2);
        PhoneAccount account1 = new PhoneAccount.Builder(makeQuickAccountHandle(
                makeQuickConnectionServiceComponentName(), "y"), "y")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER |
                        PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)
                .setExtras(account1Extras)
                .build();

        Bundle account2Extras = new Bundle();
        account2Extras.putInt(PhoneAccount.EXTRA_SORT_ORDER, 1);
        PhoneAccount account2 = new PhoneAccount.Builder(makeQuickAccountHandle(
                makeQuickConnectionServiceComponentName(), "z"), "z")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER |
                        PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)
                .setExtras(account2Extras)
                .build();

        PhoneAccount account3 = new PhoneAccount.Builder(makeQuickAccountHandle(
                makeQuickConnectionServiceComponentName(), "x"), "x")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER |
                        PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)
                .build();

        PhoneAccount account4 = new PhoneAccount.Builder(makeQuickAccountHandle(
                makeQuickConnectionServiceComponentName(), "w"), "w")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER |
                        PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)
                .build();

        PhoneAccount account5 = new PhoneAccount.Builder(makeQuickAccountHandle(
                makeQuickConnectionServiceComponentName(), "b"), "b")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .build();

        PhoneAccount account6 = new PhoneAccount.Builder(makeQuickAccountHandle(
                makeQuickConnectionServiceComponentName(), "c"), "a")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .build();

        registerAndEnableAccount(account1);
        registerAndEnableAccount(account2);
        registerAndEnableAccount(account3);
        registerAndEnableAccount(account4);
        registerAndEnableAccount(account5);
        registerAndEnableAccount(account6);

        List<PhoneAccount> accounts = mRegistrar.getAllPhoneAccounts(Process.myUserHandle(), false);
        // Sim accts ordered by sort order first
        assertTrue(accounts.get(0).getLabel().toString().equals("z"));
        assertTrue(accounts.get(1).getLabel().toString().equals("y"));

        // Sim accts with no sort order next
        assertTrue(accounts.get(2).getLabel().toString().equals("w"));
        assertTrue(accounts.get(3).getLabel().toString().equals("x"));

        // Other accts sorted by label next
        assertTrue(accounts.get(4).getLabel().toString().equals("a"));
        assertTrue(accounts.get(5).getLabel().toString().equals("b"));
    }

    /**
     * Tests {@link PhoneAccountRegistrar#getCallCapablePhoneAccounts(String, boolean, UserHandle)}
     * to ensure disabled accounts are filtered out of results when requested.
     * @throws Exception
     */
    @MediumTest
    @Test
    public void testGetByEnabledState() throws Exception {
        mComponentContextFixture.addConnectionService(makeQuickConnectionServiceComponentName(),
                Mockito.mock(IConnectionService.class));
        mRegistrar.registerPhoneAccount(makeQuickAccountBuilder("id1", 1, null)
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .build());

        assertEquals(0, mRegistrar.getCallCapablePhoneAccounts(PhoneAccount.SCHEME_TEL,
                false /* includeDisabled */, Process.myUserHandle(), false).size());
        assertEquals(1, mRegistrar.getCallCapablePhoneAccounts(PhoneAccount.SCHEME_TEL,
                true /* includeDisabled */, Process.myUserHandle(), false).size());
    }

    /**
     * Tests {@link PhoneAccountRegistrar#getCallCapablePhoneAccounts(String, boolean, UserHandle)}
     * to ensure scheme filtering operates.
     * @throws Exception
     */
    @MediumTest
    @Test
    public void testGetByScheme() throws Exception {
        mComponentContextFixture.addConnectionService(makeQuickConnectionServiceComponentName(),
                Mockito.mock(IConnectionService.class));
        registerAndEnableAccount(makeQuickAccountBuilder("id1", 1, null)
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .setSupportedUriSchemes(Arrays.asList(PhoneAccount.SCHEME_SIP))
                .build());
        registerAndEnableAccount(makeQuickAccountBuilder("id2", 2, null)
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .setSupportedUriSchemes(Arrays.asList(PhoneAccount.SCHEME_TEL))
                .build());

        assertEquals(1, mRegistrar.getCallCapablePhoneAccounts(PhoneAccount.SCHEME_SIP,
                false /* includeDisabled */, Process.myUserHandle(), false).size());
        assertEquals(1, mRegistrar.getCallCapablePhoneAccounts(PhoneAccount.SCHEME_TEL,
                false /* includeDisabled */, Process.myUserHandle(), false).size());
        assertEquals(2, mRegistrar.getCallCapablePhoneAccounts(null, false /* includeDisabled */,
                Process.myUserHandle(), false).size());
    }

    /**
     * Tests {@link PhoneAccountRegistrar#getCallCapablePhoneAccounts(String, boolean, UserHandle,
     * int)} to ensure capability filtering operates.
     * @throws Exception
     */
    @MediumTest
    @Test
    public void testGetByCapability() throws Exception {
        mComponentContextFixture.addConnectionService(makeQuickConnectionServiceComponentName(),
                Mockito.mock(IConnectionService.class));
        registerAndEnableAccount(makeQuickAccountBuilder("id1", 1, null)
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER
                        | PhoneAccount.CAPABILITY_VIDEO_CALLING)
                .setSupportedUriSchemes(Arrays.asList(PhoneAccount.SCHEME_SIP))
                .build());
        registerAndEnableAccount(makeQuickAccountBuilder("id2", 2, null)
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .setSupportedUriSchemes(Arrays.asList(PhoneAccount.SCHEME_SIP))
                .build());

        assertEquals(1, mRegistrar.getCallCapablePhoneAccounts(PhoneAccount.SCHEME_SIP,
                false /* includeDisabled */, Process.myUserHandle(), false).size(),
                PhoneAccount.CAPABILITY_VIDEO_CALLING);
        assertEquals(2, mRegistrar.getCallCapablePhoneAccounts(PhoneAccount.SCHEME_SIP,
                false /* includeDisabled */, Process.myUserHandle(), false)
                .size(), 0 /* none extra */);
        assertEquals(0, mRegistrar.getCallCapablePhoneAccounts(PhoneAccount.SCHEME_SIP,
                false /* includeDisabled */, Process.myUserHandle(), false).size(),
                PhoneAccount.CAPABILITY_RTT);
    }

    /**
     * Tests {@link PhoneAccount#equals(Object)} operator.
     * @throws Exception
     */
    @MediumTest
    @Test
    public void testPhoneAccountEquality() throws Exception {
        PhoneAccountHandle handle = new PhoneAccountHandle(new ComponentName("foo", "bar"), "id");
        PhoneAccount.Builder builder = new PhoneAccount.Builder(handle, "label");
        builder.addSupportedUriScheme("tel");
        builder.setAddress(Uri.fromParts("tel", "6505551212", null));
        builder.setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER);
        Bundle extras = new Bundle();
        extras.putInt("INT", 1);
        extras.putString("STR", "str");
        builder.setExtras(extras);
        builder.setGroupId("group");
        builder.setHighlightColor(1);
        builder.setShortDescription("short");
        builder.setSubscriptionAddress(Uri.fromParts("tel", "6505551213", null));
        builder.setSupportedAudioRoutes(2);

        PhoneAccount account1 = builder.build();
        PhoneAccount account2 = builder.build();
        assertEquals(account1, account2);
    }

    /**
     * Tests {@link PhoneAccountHandle#areFromSamePackage(PhoneAccountHandle,
     * PhoneAccountHandle)} comparison.
     */
    @SmallTest
    @Test
    public void testSamePhoneAccountHandlePackage() {
        PhoneAccountHandle a = new PhoneAccountHandle(new ComponentName("packageA", "class1"),
                "id1");
        PhoneAccountHandle b = new PhoneAccountHandle(new ComponentName("packageA", "class2"),
                "id2");
        PhoneAccountHandle c = new PhoneAccountHandle(new ComponentName("packageA", "class1"),
                "id3");
        PhoneAccountHandle d = new PhoneAccountHandle(new ComponentName("packageB", "class1"),
                "id1");

        assertTrue(PhoneAccountHandle.areFromSamePackage(null, null));
        assertTrue(PhoneAccountHandle.areFromSamePackage(a, b));
        assertTrue(PhoneAccountHandle.areFromSamePackage(a, c));
        assertTrue(PhoneAccountHandle.areFromSamePackage(b, c));
        assertFalse(PhoneAccountHandle.areFromSamePackage(a, d));
        assertFalse(PhoneAccountHandle.areFromSamePackage(b, d));
        assertFalse(PhoneAccountHandle.areFromSamePackage(c, d));
        assertFalse(PhoneAccountHandle.areFromSamePackage(a, null));
        assertFalse(PhoneAccountHandle.areFromSamePackage(b, null));
        assertFalse(PhoneAccountHandle.areFromSamePackage(c, null));
        assertFalse(PhoneAccountHandle.areFromSamePackage(null, d));
        assertFalse(PhoneAccountHandle.areFromSamePackage(null, d));
        assertFalse(PhoneAccountHandle.areFromSamePackage(null, d));
    }

    /**
     * Tests {@link PhoneAccountRegistrar#cleanupOrphanedPhoneAccounts } cleans up / deletes an
     * orphan account.
     */
    @Test
    public void testCleanUpOrphanAccounts() throws Exception {
        // GIVEN
        mComponentContextFixture.addConnectionService(makeQuickConnectionServiceComponentName(),
                Mockito.mock(IConnectionService.class));
        UserManager userManager = mContext.getSystemService(UserManager.class);

        List<UserHandle> users = Arrays.asList(new UserHandle(0),
                new UserHandle(1000));

        PhoneAccount pa1 = new PhoneAccount.Builder(
                new PhoneAccountHandle(new ComponentName(PACKAGE_1, COMPONENT_NAME), "1234",
                        users.get(0)), "l1").build();
        PhoneAccount pa2 = new PhoneAccount.Builder(
                new PhoneAccountHandle(new ComponentName(PACKAGE_2, COMPONENT_NAME), "5678",
                        users.get(1)), "l2").build();


        registerAndEnableAccount(pa1);
        registerAndEnableAccount(pa2);

        assertEquals(1, mRegistrar.getAllPhoneAccounts(users.get(0), false).size());
        assertEquals(1, mRegistrar.getAllPhoneAccounts(users.get(1), false).size());


        // WHEN
        when(mContext.getPackageManager().getPackageInfo(PACKAGE_1, 0))
                .thenReturn(new PackageInfo());

        when(mContext.getPackageManager().getPackageInfo(PACKAGE_2, 0))
                .thenThrow(new PackageManager.NameNotFoundException());

        when(userManager.getSerialNumberForUser(users.get(0)))
                .thenReturn(0L);

        when(userManager.getSerialNumberForUser(users.get(1)))
                .thenReturn(-1L);

        // THEN
        int deletedAccounts = mRegistrar.cleanupOrphanedPhoneAccounts();
        assertEquals(1, deletedAccounts);
    }

    @Test
    public void testGetSimPhoneAccountsFromSimCallManager() throws Exception {
        // Register the SIM PhoneAccounts
        mComponentContextFixture.addConnectionService(
                makeQuickConnectionServiceComponentName(), Mockito.mock(IConnectionService.class));
        PhoneAccount sim1Account = makeQuickSimAccount(1);
        PhoneAccountHandle sim1Handle = sim1Account.getAccountHandle();
        registerAndEnableAccount(sim1Account);
        PhoneAccount sim2Account = makeQuickSimAccount(2);
        PhoneAccountHandle sim2Handle = sim2Account.getAccountHandle();
        registerAndEnableAccount(sim2Account);

        assertEquals(
            List.of(sim1Handle, sim2Handle), mRegistrar.getSimPhoneAccountsOfCurrentUser());

        // Set up the SIM call manager app + carrier configs
        ComponentName simCallManagerComponent =
                new ComponentName("com.carrier.app", "CarrierConnectionService");
        PhoneAccountHandle simCallManagerHandle =
                makeQuickAccountHandle(simCallManagerComponent, "sim-call-manager");
        setSimCallManagerCarrierConfig(
                1, new ComponentName("com.other.carrier", "OtherConnectionService"));
        setSimCallManagerCarrierConfig(2, simCallManagerComponent);

        // Since SIM 1 names another app, so we only get the handle for SIM 2
        assertEquals(
                List.of(sim2Handle),
                mRegistrar.getSimPhoneAccountsFromSimCallManager(simCallManagerHandle));
        // We do exact component matching, not just package name matching
        assertEquals(
                List.of(),
                mRegistrar.getSimPhoneAccountsFromSimCallManager(
                        makeQuickAccountHandle(
                                new ComponentName("com.carrier.app", "SomeOtherUnrelatedService"),
                                "same-pkg-but-diff-svc")));

        // Results are identical after we register the PhoneAccount
        mComponentContextFixture.addConnectionService(
                simCallManagerComponent, Mockito.mock(IConnectionService.class));
        PhoneAccount simCallManagerAccount =
                new PhoneAccount.Builder(simCallManagerHandle, "SIM call manager")
                        .setCapabilities(PhoneAccount.CAPABILITY_CONNECTION_MANAGER)
                        .build();
        mRegistrar.registerPhoneAccount(simCallManagerAccount);
        assertEquals(
                List.of(sim2Handle),
                mRegistrar.getSimPhoneAccountsFromSimCallManager(simCallManagerHandle));
    }

    @Test
    public void testMaybeNotifyTelephonyForVoiceServiceState() throws Exception {
        // Register the SIM PhoneAccounts
        mComponentContextFixture.addConnectionService(
                makeQuickConnectionServiceComponentName(), Mockito.mock(IConnectionService.class));
        PhoneAccount sim1Account = makeQuickSimAccount(1);
        registerAndEnableAccount(sim1Account);
        PhoneAccount sim2Account = makeQuickSimAccount(2);
        registerAndEnableAccount(sim2Account);
        // Telephony is notified by default when new SIM accounts are registered
        verify(mComponentContextFixture.getTelephonyManager(), times(2))
                .setVoiceServiceStateOverride(false);
        clearInvocations(mComponentContextFixture.getTelephonyManager());

        // Set up the SIM call manager app + carrier configs
        ComponentName simCallManagerComponent =
                new ComponentName("com.carrier.app", "CarrierConnectionService");
        PhoneAccountHandle simCallManagerHandle =
                makeQuickAccountHandle(simCallManagerComponent, "sim-call-manager");
        mComponentContextFixture.addConnectionService(
                simCallManagerComponent, Mockito.mock(IConnectionService.class));
        setSimCallManagerCarrierConfig(1, simCallManagerComponent);
        setSimCallManagerCarrierConfig(2, simCallManagerComponent);

        // When the SIM call manager is registered without the SUPPORTS capability, telephony is
        // still notified for consistency (e.g. runtime capability removal + re-registration).
        PhoneAccount simCallManagerAccount =
                new PhoneAccount.Builder(simCallManagerHandle, "SIM call manager")
                        .setCapabilities(PhoneAccount.CAPABILITY_CONNECTION_MANAGER)
                        .build();
        mRegistrar.registerPhoneAccount(simCallManagerAccount);
        verify(mComponentContextFixture.getTelephonyManager(), times(2))
                .setVoiceServiceStateOverride(false);
        clearInvocations(mComponentContextFixture.getTelephonyManager());

        // Adding the SUPPORTS capability causes the SIMs to get notified with false again for
        // consistency purposes
        simCallManagerAccount =
                copyPhoneAccountAndAddCapabilities(
                        simCallManagerAccount,
                        PhoneAccount.CAPABILITY_SUPPORTS_VOICE_CALLING_INDICATIONS);
        mRegistrar.registerPhoneAccount(simCallManagerAccount);
        verify(mComponentContextFixture.getTelephonyManager(), times(2))
                .setVoiceServiceStateOverride(false);
        clearInvocations(mComponentContextFixture.getTelephonyManager());

        // Adding the AVAILABLE capability updates the SIMs again, this time with hasService = true
        simCallManagerAccount =
                copyPhoneAccountAndAddCapabilities(
                        simCallManagerAccount, PhoneAccount.CAPABILITY_VOICE_CALLING_AVAILABLE);
        mRegistrar.registerPhoneAccount(simCallManagerAccount);
        verify(mComponentContextFixture.getTelephonyManager(), times(2))
                .setVoiceServiceStateOverride(true);
        clearInvocations(mComponentContextFixture.getTelephonyManager());

        // Removing a SIM account does nothing, regardless of SIM call manager capabilities
        mRegistrar.unregisterPhoneAccount(sim1Account.getAccountHandle());
        verify(mComponentContextFixture.getTelephonyManager(), never())
                .setVoiceServiceStateOverride(anyBoolean());
        clearInvocations(mComponentContextFixture.getTelephonyManager());

        // Adding a SIM account while a SIM call manager with both capabilities is registered causes
        // a call to telephony with hasService = true
        mRegistrar.registerPhoneAccount(sim1Account);
        verify(mComponentContextFixture.getTelephonyManager(), times(1))
                .setVoiceServiceStateOverride(true);
        clearInvocations(mComponentContextFixture.getTelephonyManager());

        // Removing the SIM call manager while it has both capabilities causes a call to telephony
        // with hasService = false
        mRegistrar.unregisterPhoneAccount(simCallManagerHandle);
        verify(mComponentContextFixture.getTelephonyManager(), times(2))
                .setVoiceServiceStateOverride(false);
        clearInvocations(mComponentContextFixture.getTelephonyManager());

        // Removing the SIM call manager while it has the SUPPORTS capability but not AVAILABLE
        // still causes a call to telephony with hasService = false for consistency
        simCallManagerAccount =
                copyPhoneAccountAndRemoveCapabilities(
                        simCallManagerAccount, PhoneAccount.CAPABILITY_VOICE_CALLING_AVAILABLE);
        mRegistrar.registerPhoneAccount(simCallManagerAccount);
        clearInvocations(mComponentContextFixture.getTelephonyManager()); // from re-registration
        mRegistrar.unregisterPhoneAccount(simCallManagerHandle);
        verify(mComponentContextFixture.getTelephonyManager(), times(2))
                .setVoiceServiceStateOverride(false);
        clearInvocations(mComponentContextFixture.getTelephonyManager());

        // Finally, removing the SIM call manager while it has neither capability still causes a
        // call to telephony with hasService = false for consistency
        simCallManagerAccount =
                copyPhoneAccountAndRemoveCapabilities(
                        simCallManagerAccount,
                        PhoneAccount.CAPABILITY_SUPPORTS_VOICE_CALLING_INDICATIONS);
        mRegistrar.registerPhoneAccount(simCallManagerAccount);
        clearInvocations(mComponentContextFixture.getTelephonyManager()); // from re-registration
        mRegistrar.unregisterPhoneAccount(simCallManagerHandle);
        verify(mComponentContextFixture.getTelephonyManager(), times(2))
                .setVoiceServiceStateOverride(false);
        clearInvocations(mComponentContextFixture.getTelephonyManager());
    }

    /**
     * Test PhoneAccountHandle Migration Logic.
     */
    @Test
    public void testPhoneAccountMigration() throws Exception {
        PhoneAccountRegistrar.State testState = makeQuickStateWithTelephonyPhoneAccountHandle();
        final int mTestPhoneAccountHandleSubIdInt = 123;
        // Mock SubscriptionManager
        SubscriptionInfo subscriptionInfo = new SubscriptionInfo(
                mTestPhoneAccountHandleSubIdInt, "id0", 1, "a", "b", 1, 1, "test",
                        1, null, null, null, null, false, null, null);
        List<SubscriptionInfo> subscriptionInfoList = new ArrayList<>();
        subscriptionInfoList.add(subscriptionInfo);
        when(mSubscriptionManager.getAllSubscriptionInfoList()).thenReturn(subscriptionInfoList);
        mRegistrar.migratePhoneAccountHandle(testState);
        Collection<DefaultPhoneAccountHandle> defaultPhoneAccountHandles
                = testState.defaultOutgoingAccountHandles.values();
        DefaultPhoneAccountHandle defaultPhoneAccountHandle
                = defaultPhoneAccountHandles.iterator().next();
        assertEquals(Integer.toString(mTestPhoneAccountHandleSubIdInt),
                defaultPhoneAccountHandle.phoneAccountHandle.getId());
    }

    /**
     * Test that an {@link IllegalArgumentException} is thrown when a package registers a
     * {@link PhoneAccountHandle} with a { PhoneAccountHandle#packageName} that is over the
     * character limit set
     */
    @Test
    public void testInvalidPhoneAccountHandlePackageNameThrowsException() {
        // GIVEN
        String invalidPackageName = INVALID_STR;
        PhoneAccountHandle handle = makeQuickAccountHandle(
                new ComponentName(invalidPackageName, this.getClass().getName()), TEST_ID);
        PhoneAccount.Builder builder = makeBuilderWithBindCapabilities(handle);

        // THEN
        try {
            PhoneAccount account = builder.build();
            assertEquals(invalidPackageName,
                    account.getAccountHandle().getComponentName().getPackageName());
            mRegistrar.registerPhoneAccount(account);
            fail("failed to throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // pass test
        } finally {
            mRegistrar.unregisterPhoneAccount(handle);
        }
    }

    /**
     * Test that an {@link IllegalArgumentException} is thrown when a package registers a
     * {@link PhoneAccountHandle} with a { PhoneAccountHandle#className} that is over the
     * character limit set
     */
    @Test
    public void testInvalidPhoneAccountHandleClassNameThrowsException() {
        // GIVEN
        String invalidClassName = INVALID_STR;
        PhoneAccountHandle handle = makeQuickAccountHandle(
                new ComponentName(this.getClass().getPackageName(), invalidClassName), TEST_ID);
        PhoneAccount.Builder builder = makeBuilderWithBindCapabilities(handle);

        // THEN
        try {
            PhoneAccount account = builder.build();
            assertEquals(invalidClassName,
                    account.getAccountHandle().getComponentName().getClassName());
            mRegistrar.registerPhoneAccount(account);
            fail("failed to throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // pass test
        } finally {
            mRegistrar.unregisterPhoneAccount(handle);
        }
    }

    /**
     * Test that an {@link IllegalArgumentException} is thrown when a package registers a
     * {@link PhoneAccountHandle} with a { PhoneAccount#mId} that is over the character limit set
     */
    @Test
    public void testInvalidPhoneAccountHandleIdThrowsException() {
        // GIVEN
        String invalidId = INVALID_STR;
        PhoneAccountHandle handle = makeQuickAccountHandle(invalidId);
        PhoneAccount.Builder builder = makeBuilderWithBindCapabilities(handle);

        // THEN
        try {
            PhoneAccount account = builder.build();
            assertEquals(invalidId, account.getAccountHandle().getId());
            mRegistrar.registerPhoneAccount(account);
            fail("failed to throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // pass test
        } finally {
            mRegistrar.unregisterPhoneAccount(handle);
        }
    }

    /**
     * Test that an {@link IllegalArgumentException} is thrown when a package registers a
     * {@link PhoneAccount} with a { PhoneAccount#mLabel} that is over the character limit set
     */
    @Test
    public void testInvalidLabelThrowsException() {
        // GIVEN
        String invalidLabel = INVALID_STR;
        PhoneAccountHandle handle = makeQuickAccountHandle(TEST_ID);
        PhoneAccount.Builder builder = new PhoneAccount.Builder(handle, invalidLabel)
                .setCapabilities(PhoneAccount.CAPABILITY_SUPPORTS_TRANSACTIONAL_OPERATIONS);

        // WHEN
        when(mAppLabelProxy.getAppLabel(anyString())).thenReturn(invalidLabel);

        // THEN
        try {
            PhoneAccount account = builder.build();
            assertEquals(invalidLabel, account.getLabel());
            mRegistrar.registerPhoneAccount(account);
            fail("failed to throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // pass test
        } finally {
            mRegistrar.unregisterPhoneAccount(handle);
        }
    }

    /**
     * Test that an {@link IllegalArgumentException} is thrown when a package registers a
     * {@link PhoneAccount} with a {PhoneAccount#mShortDescription} that is over the character
     * limit set
     */
    @Test
    public void testInvalidShortDescriptionThrowsException() {
        // GIVEN
        String invalidShortDescription = INVALID_STR;
        PhoneAccountHandle handle = makeQuickAccountHandle(TEST_ID);
        PhoneAccount.Builder builder = makeBuilderWithBindCapabilities(handle)
                .setShortDescription(invalidShortDescription);

        // THEN
        try {
            PhoneAccount account = builder.build();
            assertEquals(invalidShortDescription, account.getShortDescription());
            mRegistrar.registerPhoneAccount(account);
            fail("failed to throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // pass test
        } finally {
            mRegistrar.unregisterPhoneAccount(handle);
        }
    }

    /**
     * Test that an {@link IllegalArgumentException} is thrown when a package registers a
     * {@link PhoneAccount} with a {PhoneAccount#mGroupId} that is over the character limit set
     */
    @Test
    public void testInvalidGroupIdThrowsException() {
        // GIVEN
        String invalidGroupId = INVALID_STR;
        PhoneAccountHandle handle = makeQuickAccountHandle(TEST_ID);
        PhoneAccount.Builder builder = makeBuilderWithBindCapabilities(handle)
                .setGroupId(invalidGroupId);

        // THEN
        try {
            PhoneAccount account = builder.build();
            assertEquals(invalidGroupId, account.getGroupId());
            mRegistrar.registerPhoneAccount(account);
            fail("failed to throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // pass test
        } finally {
            mRegistrar.unregisterPhoneAccount(handle);
        }
    }

    /**
     * Test that an {@link IllegalArgumentException} is thrown when a package registers a
     * {@link PhoneAccount} with a {PhoneAccount#mExtras} that is over the character limit set
     */
    @Test
    public void testInvalidExtraStringKeyThrowsException() {
        // GIVEN
        String invalidBundleKey = INVALID_STR;
        String keyValue = "value";
        Bundle extras = new Bundle();
        extras.putString(invalidBundleKey, keyValue);
        PhoneAccountHandle handle = makeQuickAccountHandle(TEST_ID);
        PhoneAccount.Builder builder = makeBuilderWithBindCapabilities(handle)
                .setExtras(extras);

        // THEN
        try {
            PhoneAccount account = builder.build();
            assertEquals(keyValue, account.getExtras().getString(invalidBundleKey));
            mRegistrar.registerPhoneAccount(account);
            fail("failed to throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // pass test
        } finally {
            mRegistrar.unregisterPhoneAccount(handle);
        }
    }

    /**
     * Test that an {@link IllegalArgumentException} is thrown when a package registers a
     * {@link PhoneAccount} with a {PhoneAccount#mExtras} that is over the character limit set
     */
    @Test
    public void testInvalidExtraStringValueThrowsException() {
        // GIVEN
        String extrasKey = "ExtrasStringKey";
        String invalidBundleValue = INVALID_STR;
        Bundle extras = new Bundle();
        extras.putString(extrasKey, invalidBundleValue);
        PhoneAccountHandle handle = makeQuickAccountHandle(TEST_ID);
        PhoneAccount.Builder builder = makeBuilderWithBindCapabilities(handle)
                .setExtras(extras);

        // THEN
        try {
            PhoneAccount account = builder.build();
            assertEquals(invalidBundleValue, account.getExtras().getString(extrasKey));
            mRegistrar.registerPhoneAccount(account);
            fail("failed to throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // pass test
        } finally {
            mRegistrar.unregisterPhoneAccount(handle);
        }
    }

    /**
     * Test that an {@link IllegalArgumentException} is thrown when a package registers a
     * {@link PhoneAccount} with a {PhoneAccount#mExtras} that is over the (key,value) pair limit
     */
    @Test
    public void testInvalidExtraElementsExceedsLimitAndThrowsException() {
        // GIVEN
        int invalidBundleExtrasLimit =
                PhoneAccountRegistrar.MAX_PHONE_ACCOUNT_EXTRAS_KEY_PAIR_LIMIT + 1;
        Bundle extras = new Bundle();
        for (int i = 0; i < invalidBundleExtrasLimit; i++) {
            extras.putString(UUID.randomUUID().toString(), "value");
        }
        PhoneAccountHandle handle = makeQuickAccountHandle(TEST_ID);
        PhoneAccount.Builder builder = makeBuilderWithBindCapabilities(handle)
                .setExtras(extras);
        // THEN
        try {
            PhoneAccount account = builder.build();
            assertEquals(invalidBundleExtrasLimit, account.getExtras().size());
            mRegistrar.registerPhoneAccount(account);
            fail("failed to throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Test Pass
        } finally {
            mRegistrar.unregisterPhoneAccount(handle);
        }
    }

    /**
     * Ensure an IllegalArgumentException is thrown when adding more than 10 schemes for a single
     * account
     */
    @Test
    public void testLimitOnSchemeCount() {
        PhoneAccountHandle handle = makeQuickAccountHandle(TEST_ID);
        PhoneAccount.Builder builder = new PhoneAccount.Builder(handle, TEST_LABEL);
        for (int i = 0; i < PhoneAccountRegistrar.MAX_PHONE_ACCOUNT_REGISTRATIONS + 1; i++) {
            builder.addSupportedUriScheme(Integer.toString(i));
        }
        try {
            mRegistrar.enforceLimitsOnSchemes(builder.build());
            fail("should have hit exception in enforceLimitOnSchemes");
        } catch (IllegalArgumentException e) {
            // pass test
        }
    }

    /**
     * Ensure an IllegalArgumentException is thrown when adding more 256 chars for a single
     * account
     */
    @Test
    public void testLimitOnSchemeLength() {
        PhoneAccountHandle handle = makeQuickAccountHandle(TEST_ID);
        PhoneAccount.Builder builder = new PhoneAccount.Builder(handle, TEST_LABEL);
        builder.addSupportedUriScheme(INVALID_STR);
        try {
            mRegistrar.enforceLimitsOnSchemes(builder.build());
            fail("should have hit exception in enforceLimitOnSchemes");
        } catch (IllegalArgumentException e) {
            // pass test
        }
    }

    /**
     * Ensure an IllegalArgumentException is thrown when adding too many PhoneAccountHandles to
     * a PhoneAccount.
     */
    @Test
    public void testLimitOnSimultaneousCallingRestriction_tooManyElements() throws Exception {
        doReturn(true).when(mTelephonyFeatureFlags).simultaneousCallingIndications();
        mComponentContextFixture.addConnectionService(makeQuickConnectionServiceComponentName(),
                Mockito.mock(IConnectionService.class));
        Set<PhoneAccountHandle> tooManyElements = new HashSet<>(11);
        for (int i = 0; i < 11; i++) {
            tooManyElements.add(makeQuickAccountHandle(TEST_ID + i));
        }
        PhoneAccount tooManyRestrictionsPA = new PhoneAccount.Builder(
                makeQuickAccountHandle(TEST_ID), TEST_LABEL)
                .setSimultaneousCallingRestriction(tooManyElements)
                .build();
        try {
            mRegistrar.registerPhoneAccount(tooManyRestrictionsPA);
            fail("should have hit registrations exception in "
                    + "enforceSimultaneousCallingRestrictionLimit");
        } catch (IllegalArgumentException e) {
            // pass test
        }
    }

    /**
     * Ensure an IllegalArgumentException is thrown when adding a PhoneAccountHandle where the
     * package name field is too large.
     */
    @Test
    public void testLimitOnSimultaneousCallingRestriction_InvalidPackageName() throws Exception {
        doReturn(true).when(mTelephonyFeatureFlags).simultaneousCallingIndications();
        mComponentContextFixture.addConnectionService(makeQuickConnectionServiceComponentName(),
                Mockito.mock(IConnectionService.class));
        Set<PhoneAccountHandle> invalidElement = new HashSet<>(1);
        invalidElement.add(new PhoneAccountHandle(new ComponentName(INVALID_STR, "Class"),
                TEST_ID));
        PhoneAccount invalidRestrictionPA = new PhoneAccount.Builder(
                makeQuickAccountHandle(TEST_ID), TEST_LABEL)
                .setSimultaneousCallingRestriction(invalidElement)
                .build();
        try {
            mRegistrar.registerPhoneAccount(invalidRestrictionPA);
            fail("should have hit package name size limit exception in "
                    + "enforceSimultaneousCallingRestrictionLimit");
        } catch (IllegalArgumentException e) {
            // pass test
        }
    }

    /**
     * Ensure an IllegalArgumentException is thrown when adding a PhoneAccountHandle where the
     * class name field is too large.
     */
    @Test
    public void testLimitOnSimultaneousCallingRestriction_InvalidClassName() throws Exception {
        doReturn(true).when(mTelephonyFeatureFlags).simultaneousCallingIndications();
        mComponentContextFixture.addConnectionService(makeQuickConnectionServiceComponentName(),
                Mockito.mock(IConnectionService.class));
        Set<PhoneAccountHandle> invalidElement = new HashSet<>(1);
        invalidElement.add(new PhoneAccountHandle(new ComponentName("pkg", INVALID_STR),
                TEST_ID));
        PhoneAccount invalidRestrictionPA = new PhoneAccount.Builder(
                makeQuickAccountHandle(TEST_ID), TEST_LABEL)
                .setSimultaneousCallingRestriction(invalidElement)
                .build();
        try {
            mRegistrar.registerPhoneAccount(invalidRestrictionPA);
            fail("should have hit class name size limit exception in "
                    + "enforceSimultaneousCallingRestrictionLimit");
        } catch (IllegalArgumentException e) {
            // pass test
        }
    }

    /**
     * Ensure an IllegalArgumentException is thrown when adding a PhoneAccountHandle where the
     * ID field is too large.
     */
    @Test
    public void testLimitOnSimultaneousCallingRestriction_InvalidIdSize() throws Exception {
        doReturn(true).when(mTelephonyFeatureFlags).simultaneousCallingIndications();
        mComponentContextFixture.addConnectionService(makeQuickConnectionServiceComponentName(),
                Mockito.mock(IConnectionService.class));
        Set<PhoneAccountHandle> invalidIdElement = new HashSet<>(1);
        invalidIdElement.add(new PhoneAccountHandle(makeQuickConnectionServiceComponentName(),
                INVALID_STR));
        PhoneAccount invalidRestrictionPA = new PhoneAccount.Builder(
                makeQuickAccountHandle(TEST_ID), TEST_LABEL)
                .setSimultaneousCallingRestriction(invalidIdElement)
                .build();
        try {
            mRegistrar.registerPhoneAccount(invalidRestrictionPA);
            fail("should have hit ID size limit exception in "
                    + "enforceSimultaneousCallingRestrictionLimit");
        } catch (IllegalArgumentException e) {
            // pass test
        }
    }

    /**
     * Ensure an IllegalArgumentException is thrown when adding an address over the limit
     */
    @Test
    public void testLimitOnAddress() {
        String text = "a".repeat(100);
        PhoneAccountHandle handle = makeQuickAccountHandle(TEST_ID);
        PhoneAccount.Builder builder = makeBuilderWithBindCapabilities(handle)
                .setAddress(Uri.fromParts(text, text, text));
        try {
            mRegistrar.registerPhoneAccount(builder.build());
            fail("failed to throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // pass test
        }
        finally {
            mRegistrar.unregisterPhoneAccount(handle);
        }
    }

    /**
     * Ensure an IllegalArgumentException is thrown when an Icon that throws an IOException is given
     */
    @Test
    public void testLimitOnIcon() throws Exception {
        Icon mockIcon = mock(Icon.class);
        // GIVEN
        PhoneAccount.Builder builder = makeBuilderWithBindCapabilities(
                makeQuickAccountHandle(TEST_ID)).setIcon(mockIcon);
        try {
            // WHEN
            Mockito.doThrow(new IOException())
                    .when(mockIcon).writeToStream(any(OutputStream.class));
            //THEN
            mRegistrar.enforceIconSizeLimit(builder.build());
            fail("failed to throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // pass test
            assertTrue(e.getMessage().contains(PhoneAccountRegistrar.ICON_ERROR_MSG));
        }
    }

    /**
     * Ensure an IllegalArgumentException is thrown when providing a SubscriptionAddress that
     * exceeds the PhoneAccountRegistrar limit.
     */
    @Test
    public void testLimitOnSubscriptionAddress() throws Exception {
        String text = "a".repeat(100);
        PhoneAccount.Builder builder =  new PhoneAccount.Builder(makeQuickAccountHandle(TEST_ID),
                TEST_LABEL).setSubscriptionAddress(Uri.fromParts(text, text, text));
        try {
            mRegistrar.enforceCharacterLimit(builder.build());
            fail("failed to throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // pass test
        }
    }

    /**
     * PhoneAccounts with CAPABILITY_SUPPORTS_TRANSACTIONAL_OPERATIONS do not require a
     * ConnectionService. Ensure that such an account can be registered and fetched.
     */
    @Test
    public void testFetchingTransactionalAccounts() {
        PhoneAccount account = makeBuilderWithBindCapabilities(
                makeQuickAccountHandle(TEST_ID)).build();

        try {
            assertEquals(0, mRegistrar.getAllPhoneAccounts(null, true).size());
            registerAndEnableAccount(account);
            assertEquals(1, mRegistrar.getAllPhoneAccounts(null, true).size());
        } finally {
            mRegistrar.unregisterPhoneAccount(account.getAccountHandle());
        }
    }

    @Test
    public void testGetPhoneAccountAcrossUsers() throws Exception {
        when(mTelephonyFeatureFlags.workProfileApiSplit()).thenReturn(true);
        mComponentContextFixture.addConnectionService(makeQuickConnectionServiceComponentName(),
                Mockito.mock(IConnectionService.class));

        PhoneAccount accountForCurrent = makeQuickAccountBuilder("id_0", 0, UserHandle.CURRENT)
                .setCapabilities(PhoneAccount.CAPABILITY_CONNECTION_MANAGER
                        | PhoneAccount.CAPABILITY_CALL_PROVIDER).build();
        PhoneAccount accountForAll = makeQuickAccountBuilder("id_0", 0, UserHandle.ALL)
                .setCapabilities(PhoneAccount.CAPABILITY_CONNECTION_MANAGER
                        | PhoneAccount.CAPABILITY_CALL_PROVIDER
                        | PhoneAccount.CAPABILITY_MULTI_USER).build();
        PhoneAccount accountForWorkProfile = makeQuickAccountBuilder("id_1", 1, USER_HANDLE_10)
                .setCapabilities(PhoneAccount.CAPABILITY_CONNECTION_MANAGER
                        | PhoneAccount.CAPABILITY_CALL_PROVIDER).build();

        registerAndEnableAccount(accountForCurrent);
        registerAndEnableAccount(accountForAll);
        registerAndEnableAccount(accountForWorkProfile);

        List<PhoneAccount> accountsForUser = mRegistrar.getPhoneAccounts(0, 0,
                null, null, false, USER_HANDLE_10, false, false);
        List<PhoneAccount> accountsVisibleUser = mRegistrar.getPhoneAccounts(0, 0,
                null, null, false, USER_HANDLE_10, false, true);
        List<PhoneAccount> accountsAcrossUser = mRegistrar.getPhoneAccounts(0, 0,
                null, null, false, USER_HANDLE_10, true, false);

        // Return the account exactly matching the user if it exists
        assertEquals(1, accountsForUser.size());
        assertTrue(accountsForUser.contains(accountForWorkProfile));
        // The accounts visible to the user without across user permission
        assertEquals(2, accountsVisibleUser.size());
        assertTrue(accountsVisibleUser.containsAll(accountsForUser));
        assertTrue(accountsVisibleUser.contains(accountForAll));
        // The accounts visible to the user with across user permission
        assertEquals(3, accountsAcrossUser.size());
        assertTrue(accountsAcrossUser.containsAll(accountsVisibleUser));
        assertTrue(accountsAcrossUser.contains(accountForCurrent));

        mRegistrar.unregisterPhoneAccount(accountForWorkProfile.getAccountHandle());

        accountsForUser = mRegistrar.getPhoneAccounts(0, 0,
                null, null, false, USER_HANDLE_10, false, false);

        // Return the account visible for the user if no account exactly matches the user
        assertEquals(1, accountsForUser.size());
        assertTrue(accountsForUser.contains(accountForAll));
    }

    private static PhoneAccount.Builder makeBuilderWithBindCapabilities(PhoneAccountHandle handle) {
        return new PhoneAccount.Builder(handle, TEST_LABEL)
                .setCapabilities(PhoneAccount.CAPABILITY_SUPPORTS_TRANSACTIONAL_OPERATIONS);
    }

    private static ComponentName makeQuickConnectionServiceComponentName() {
        return new ComponentName(
                "com.android.server.telecom.tests",
                "com.android.server.telecom.tests.MockConnectionService");
    }

    private static PhoneAccountHandle makeQuickAccountHandle(String id) {
        return makeQuickAccountHandle(makeQuickConnectionServiceComponentName(), id);
    }

    private static PhoneAccountHandle makeQuickAccountHandle(ComponentName name, String id) {
        return new PhoneAccountHandle(name, id, Process.myUserHandle());
    }

    private static PhoneAccountHandle makeQuickAccountHandleForUser(
            String id, UserHandle userHandle) {
        return new PhoneAccountHandle(makeQuickConnectionServiceComponentName(), id, userHandle);
    }

    private PhoneAccount.Builder makeQuickAccountBuilder(
            String id, int idx, UserHandle userHandle) {
        return new PhoneAccount.Builder(
                userHandle == null
                        ? makeQuickAccountHandle(id)
                        : makeQuickAccountHandleForUser(id, userHandle),
                "label" + idx);
    }

    private static PhoneAccount copyPhoneAccountAndOverrideCapabilities(
            PhoneAccount base, int newCapabilities) {
        return base.toBuilder().setCapabilities(newCapabilities).build();
    }

    private static PhoneAccount copyPhoneAccountAndAddCapabilities(
            PhoneAccount base, int capabilitiesToAdd) {
        return copyPhoneAccountAndOverrideCapabilities(
                base, base.getCapabilities() | capabilitiesToAdd);
    }

    private static PhoneAccount copyPhoneAccountAndRemoveCapabilities(
            PhoneAccount base, int capabilitiesToRemove) {
        return copyPhoneAccountAndOverrideCapabilities(
                base, base.getCapabilities() & ~capabilitiesToRemove);
    }

    private PhoneAccount makeQuickAccount(String id, int idx) {
        return makeQuickAccountBuilder(id, idx, null)
                .setAddress(Uri.parse("http://foo.com/" + idx))
                .setSubscriptionAddress(Uri.parse("tel:555-000" + idx))
                .setCapabilities(idx)
                .setIcon(Icon.createWithResource(
                            "com.android.server.telecom.tests", R.drawable.stat_sys_phone_call))
                .setShortDescription("desc" + idx)
                .setIsEnabled(true)
                .build();
    }

    /**
     * Similar to {@link #makeQuickAccount}, but also hooks up {@code TelephonyManager} so that it
     * returns {@code simId} as the account's subscriptionId.
     */
    private PhoneAccount makeQuickSimAccount(int simId) {
        PhoneAccount simAccount =
                makeQuickAccountBuilder("sim" + simId, simId, null)
                        .setCapabilities(
                                PhoneAccount.CAPABILITY_CALL_PROVIDER
                                        | PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)
                        .setIsEnabled(true)
                        .build();
        when(mComponentContextFixture
                        .getTelephonyManager()
                        .getSubscriptionId(simAccount.getAccountHandle()))
                .thenReturn(simId);
        // mComponentContextFixture already sets up the createForSubscriptionId self-reference
        when(mComponentContextFixture
                        .getTelephonyManager()
                        .createForPhoneAccountHandle(simAccount.getAccountHandle()))
                .thenReturn(mComponentContextFixture.getTelephonyManager());
        return simAccount;
    }

    /**
     * Hooks up carrier config to point to {@code simCallManagerComponent} for the given {@code
     * subscriptionId}.
     */
    private void setSimCallManagerCarrierConfig(
            int subscriptionId, @Nullable ComponentName simCallManagerComponent) {
        PersistableBundle config = new PersistableBundle();
        config.putString(
                CarrierConfigManager.KEY_DEFAULT_SIM_CALL_MANAGER_STRING,
                simCallManagerComponent != null ? simCallManagerComponent.flattenToString() : null);
        when(mComponentContextFixture.getCarrierConfigManager().getConfigForSubId(subscriptionId))
                .thenReturn(config);
    }

    private static void roundTripPhoneAccount(PhoneAccount original) throws Exception {
        PhoneAccount copy = null;

        {
            Parcel parcel = Parcel.obtain();
            parcel.writeParcelable(original, 0);
            parcel.setDataPosition(0);
            copy = parcel.readParcelable(PhoneAccountRegistrarTest.class.getClassLoader());
            parcel.recycle();
        }

        assertPhoneAccountEquals(original, copy);
    }

    private static <T> T roundTripXml(
            Object self,
            T input,
            PhoneAccountRegistrar.XmlSerialization<T> xml,
            Context context,
            FeatureFlags telephonyFeatureFlags,
            com.android.server.telecom.flags.FeatureFlags telecomFeatureFlags)
            throws Exception {
        Log.d(self, "Input = %s", input);

        byte[] data = toXml(input, xml, context, telephonyFeatureFlags);

        Log.i(self, "====== XML data ======\n%s", new String(data));

        T result = fromXml(data, xml, context, telephonyFeatureFlags, telecomFeatureFlags);

        Log.i(self, "result = " + result);

        return result;
    }

    private static <T> byte[] toXml(T input, PhoneAccountRegistrar.XmlSerialization<T> xml,
            Context context, FeatureFlags telephonyFeatureFlags) throws Exception {
        XmlSerializer serializer = new FastXmlSerializer();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        serializer.setOutput(new BufferedOutputStream(baos), "utf-8");
        xml.writeToXml(input, serializer, context, telephonyFeatureFlags);
        serializer.flush();
        return baos.toByteArray();
    }

    private static <T> T fromXml(byte[] data, PhoneAccountRegistrar.XmlSerialization<T> xml,
            Context context, FeatureFlags telephonyFeatureFlags,
            com.android.server.telecom.flags.FeatureFlags telecomFeatureFlags) throws Exception {
        XmlPullParser parser = Xml.newPullParser();
        parser.setInput(new BufferedInputStream(new ByteArrayInputStream(data)), null);
        parser.nextTag();
        return xml.readFromXml(parser, MAX_VERSION, context,
                telephonyFeatureFlags, telecomFeatureFlags);

    }

    private static void assertPhoneAccountHandleEquals(PhoneAccountHandle a, PhoneAccountHandle b) {
        if (a != b) {
            assertEquals(
                    a.getComponentName().getPackageName(),
                    b.getComponentName().getPackageName());
            assertEquals(
                    a.getComponentName().getClassName(),
                    b.getComponentName().getClassName());
            assertEquals(a.getId(), b.getId());
        }
    }

    private static void assertIconEquals(Icon a, Icon b) {
        if (a != b) {
            if (a != null && b != null) {
                assertEquals(a.toString(), b.toString());
            } else {
                fail("Icons not equal: " + a + ", " + b);
            }
        }
    }

    private static void assertDefaultPhoneAccountHandleEquals(DefaultPhoneAccountHandle a,
            DefaultPhoneAccountHandle b) {
        if (a != b) {
            if (a!= null && b != null) {
                assertEquals(a.userHandle, b.userHandle);
                assertPhoneAccountHandleEquals(a.phoneAccountHandle, b.phoneAccountHandle);
            } else {
                fail("Default phone account handles are not equal: " + a + ", " + b);
            }
        }
    }

    private static void assertPhoneAccountEquals(PhoneAccount a, PhoneAccount b) {
        if (a != b) {
            if (a != null && b != null) {
                assertPhoneAccountHandleEquals(a.getAccountHandle(), b.getAccountHandle());
                assertEquals(a.getAddress(), b.getAddress());
                assertEquals(a.getSubscriptionAddress(), b.getSubscriptionAddress());
                assertEquals(a.getCapabilities(), b.getCapabilities());
                assertIconEquals(a.getIcon(), b.getIcon());
                assertEquals(a.getHighlightColor(), b.getHighlightColor());
                assertEquals(a.getLabel(), b.getLabel());
                assertEquals(a.getShortDescription(), b.getShortDescription());
                assertEquals(a.getSupportedUriSchemes(), b.getSupportedUriSchemes());
                assertBundlesEqual(a.getExtras(), b.getExtras());
                assertEquals(a.isEnabled(), b.isEnabled());
                assertEquals(a.hasSimultaneousCallingRestriction(),
                        b.hasSimultaneousCallingRestriction());
                if (a.hasSimultaneousCallingRestriction()) {
                    assertEquals(a.getSimultaneousCallingRestriction(),
                            b.getSimultaneousCallingRestriction());
                }
            } else {
                fail("Phone accounts not equal: " + a + ", " + b);
            }
        }
    }

    private static void assertBundlesEqual(Bundle a, Bundle b) {
        if (a == null && b == null) {
            return;
        }

        assertNotNull(a);
        assertNotNull(b);
        Set<String> keySetA = a.keySet();
        Set<String> keySetB = b.keySet();

        assertTrue("Bundle keys not the same", keySetA.containsAll(keySetB));
        assertTrue("Bundle keys not the same", keySetB.containsAll(keySetA));

        for (String keyA : keySetA) {
            assertEquals("Bundle value not the same", a.get(keyA), b.get(keyA));
        }
    }

    private static void assertStateEquals(
            PhoneAccountRegistrar.State a, PhoneAccountRegistrar.State b) {
        assertEquals(a.defaultOutgoingAccountHandles.size(),
                b.defaultOutgoingAccountHandles.size());
        for (Map.Entry<UserHandle, DefaultPhoneAccountHandle> e :
                a.defaultOutgoingAccountHandles.entrySet()) {
            assertDefaultPhoneAccountHandleEquals(e.getValue(),
                    b.defaultOutgoingAccountHandles.get(e.getKey()));
        }
        assertEquals(a.accounts.size(), b.accounts.size());
        for (int i = 0; i < a.accounts.size(); i++) {
            assertPhoneAccountEquals(a.accounts.get(i), b.accounts.get(i));
        }
    }

    private PhoneAccountRegistrar.State makeQuickStateWithTelephonyPhoneAccountHandle() {
        UserManager userManager = mContext.getSystemService(UserManager.class);
        PhoneAccountRegistrar.State s = new PhoneAccountRegistrar.State();
        s.accounts.add(makeQuickAccount("id0", 0));
        s.accounts.add(makeQuickAccount("id1", 1));
        s.accounts.add(makeQuickAccount("id2", 2));
        PhoneAccountHandle phoneAccountHandle = new PhoneAccountHandle(new ComponentName(
                "com.android.phone",
                        "com.android.services.telephony.TelephonyConnectionService"), "id0");
        UserHandle userHandle = phoneAccountHandle.getUserHandle();
        when(userManager.getSerialNumberForUser(userHandle))
            .thenReturn(0L);
        when(userManager.getUserForSerialNumber(0L))
            .thenReturn(userHandle);
        s.defaultOutgoingAccountHandles
            .put(userHandle, new DefaultPhoneAccountHandle(userHandle, phoneAccountHandle,
                "testGroup"));
        return s;
    }

    private PhoneAccountRegistrar.State makeQuickState() {
        UserManager userManager = mContext.getSystemService(UserManager.class);
        PhoneAccountRegistrar.State s = new PhoneAccountRegistrar.State();
        s.accounts.add(makeQuickAccount("id0", 0));
        s.accounts.add(makeQuickAccount("id1", 1));
        s.accounts.add(makeQuickAccount("id2", 2));
        PhoneAccountHandle phoneAccountHandle = new PhoneAccountHandle(
                new ComponentName("pkg0", "cls0"), "id0");
        UserHandle userHandle = phoneAccountHandle.getUserHandle();
        when(userManager.getSerialNumberForUser(userHandle))
                .thenReturn(0L);
        when(userManager.getUserForSerialNumber(0L))
                .thenReturn(userHandle);
        s.defaultOutgoingAccountHandles
                .put(userHandle, new DefaultPhoneAccountHandle(userHandle, phoneAccountHandle,
                        "testGroup"));
        return s;
    }
}
