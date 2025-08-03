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
 * limitations under the License
 */

package com.android.server.telecom.tests;

import static android.Manifest.permission.CALL_PHONE;
import static android.Manifest.permission.CALL_PRIVILEGED;
import static android.Manifest.permission.MANAGE_OWN_CALLS;
import static android.Manifest.permission.MODIFY_PHONE_STATE;
import static android.Manifest.permission.READ_PHONE_NUMBERS;
import static android.Manifest.permission.READ_PHONE_STATE;
import static android.Manifest.permission.READ_PRIVILEGED_PHONE_STATE;
import static android.Manifest.permission.READ_SMS;
import static android.Manifest.permission.REGISTER_SIM_SUBSCRIPTION;
import static android.Manifest.permission.WRITE_SECURE_SETTINGS;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.Manifest;
import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.OutcomeReceiver;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.telecom.CallAttributes;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.telephony.TelephonyManager;

import androidx.test.filters.SmallTest;

import com.android.internal.telecom.ICallEventCallback;
import com.android.internal.telecom.ITelecomService;
import com.android.server.telecom.AnomalyReporterAdapter;
import com.android.server.telecom.Call;
import com.android.server.telecom.CallIntentProcessor;
import com.android.server.telecom.CallState;
import com.android.server.telecom.CallsManager;
import com.android.server.telecom.DefaultDialerCache;
import com.android.server.telecom.InCallController;
import com.android.server.telecom.PhoneAccountRegistrar;
import com.android.server.telecom.TelecomServiceImpl;
import com.android.server.telecom.TelecomSystem;
import com.android.server.telecom.components.UserCallIntentProcessor;
import com.android.server.telecom.components.UserCallIntentProcessorFactory;
import com.android.server.telecom.flags.FeatureFlags;
import com.android.server.telecom.voip.IncomingCallTransaction;
import com.android.server.telecom.voip.OutgoingCallTransaction;
import com.android.server.telecom.voip.TransactionManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.IntConsumer;

@RunWith(JUnit4.class)
public class TelecomServiceImplTest extends TelecomTestCase {

    private static final String CALLING_PACKAGE = TelecomServiceImplTest.class.getPackageName();
    private static final String TEST_NAME = "Alan Turing";
    private static final Uri TEST_URI = Uri.fromParts("tel", "abc", "123");
    public static final String TEST_PACKAGE = "com.test";
    public static final String PACKAGE_NAME = "test";

    public static class CallIntentProcessAdapterFake implements CallIntentProcessor.Adapter {
        @Override
        public void processOutgoingCallIntent(Context context, CallsManager callsManager,
                Intent intent, String callingPackage, FeatureFlags flags) {

        }

        @Override
        public void processIncomingCallIntent(CallsManager callsManager, Intent intent) {

        }

        @Override
        public void processUnknownCallIntent(CallsManager callsManager, Intent intent) {

        }
    }

    public static class SubscriptionManagerAdapterFake
            implements TelecomServiceImpl.SubscriptionManagerAdapter {
        @Override
        public int getDefaultVoiceSubId() {
            return 0;
        }
    }

    public static class SettingsSecureAdapterFake implements
        TelecomServiceImpl.SettingsSecureAdapter {
        @Override
        public void putStringForUser(ContentResolver resolver, String name, String value,
            int userHandle) {

        }

        @Override
        public String getStringForUser(ContentResolver resolver, String name, int userHandle) {
            return THIRD_PARTY_CALL_SCREENING.flattenToString();
        }
    }

    private static class AnyStringIn implements ArgumentMatcher<String> {
        private Collection<String> mStrings;
        public AnyStringIn(Collection<String> strings) {
            this.mStrings = strings;
        }

        @Override
        public boolean matches(String string) {
            return mStrings.contains(string);
        }
    }

    private ITelecomService.Stub mTSIBinder;
    private AppOpsManager mAppOpsManager;
    private UserManager mUserManager;

    @Mock private CallsManager mFakeCallsManager;
    @Mock private PhoneAccountRegistrar mFakePhoneAccountRegistrar;
    @Mock private TelecomManager mTelecomManager;
    private CallIntentProcessor.Adapter mCallIntentProcessorAdapter =
            spy(new CallIntentProcessAdapterFake());
    @Mock private DefaultDialerCache mDefaultDialerCache;
    private IntConsumer mDefaultDialerObserver;
    private TelecomServiceImpl.SubscriptionManagerAdapter mSubscriptionManagerAdapter =
            spy(new SubscriptionManagerAdapterFake());
    private TelecomServiceImpl.SettingsSecureAdapter mSettingsSecureAdapter =
        spy(new SettingsSecureAdapterFake());
    @Mock private UserCallIntentProcessor mUserCallIntentProcessor;
    private PackageManager mPackageManager;
    @Mock private ApplicationInfo mApplicationInfo;
    @Mock private ICallEventCallback mICallEventCallback;
    @Mock private TransactionManager mTransactionManager;
    @Mock private AnomalyReporterAdapter mAnomalyReporterAdapter;
    @Mock private FeatureFlags mFeatureFlags;
    @Mock private com.android.internal.telephony.flags.FeatureFlags mTelephonyFeatureFlags;

    @Mock private InCallController mInCallController;

    private final TelecomSystem.SyncRoot mLock = new TelecomSystem.SyncRoot() { };

    private static final String DEFAULT_DIALER_PACKAGE = "com.google.android.dialer";
    private static final UserHandle USER_HANDLE_16 = new UserHandle(16);
    private static final UserHandle USER_HANDLE_17 = new UserHandle(17);
    private static final PhoneAccountHandle TEL_PA_HANDLE_16 = new PhoneAccountHandle(
            new ComponentName(PACKAGE_NAME, "telComponentName"), "0", USER_HANDLE_16);
    private static final PhoneAccountHandle SIP_PA_HANDLE_17 = new PhoneAccountHandle(
            new ComponentName(PACKAGE_NAME, "sipComponentName"), "1", USER_HANDLE_17);
    private static final PhoneAccountHandle TEL_PA_HANDLE_CURRENT = new PhoneAccountHandle(
            new ComponentName(PACKAGE_NAME, "telComponentName"), "2",
                    Binder.getCallingUserHandle());
    private static final PhoneAccountHandle SIP_PA_HANDLE_CURRENT = new PhoneAccountHandle(
            new ComponentName(PACKAGE_NAME, "sipComponentName"), "3",
                    Binder.getCallingUserHandle());
    private static final ComponentName THIRD_PARTY_CALL_SCREENING = new ComponentName(
            "com.android.thirdparty", "com.android.thirdparty.callscreeningserviceimpl");

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        mContext = mComponentContextFixture.getTestDouble().getApplicationContext();

        doReturn(mContext).when(mContext).getApplicationContext();
        doReturn(mContext).when(mContext).createContextAsUser(any(UserHandle.class), anyInt());
        when(mFakeCallsManager.getInCallController()).thenReturn(mInCallController);
        doNothing().when(mContext).sendBroadcastAsUser(any(Intent.class), any(UserHandle.class),
                anyString());
        when(mContext.checkCallingOrSelfPermission(Manifest.permission.INTERACT_ACROSS_USERS))
                .thenReturn(PackageManager.PERMISSION_GRANTED);
        doAnswer(invocation -> {
            mDefaultDialerObserver = invocation.getArgument(1);
            return null;
        }).when(mDefaultDialerCache).observeDefaultDialerApplication(any(Executor.class),
                any(IntConsumer.class));
        TelecomServiceImpl telecomServiceImpl = new TelecomServiceImpl(
                mContext,
                mFakeCallsManager,
                mFakePhoneAccountRegistrar,
                mCallIntentProcessorAdapter,
                new UserCallIntentProcessorFactory() {
                    @Override
                    public UserCallIntentProcessor create(Context context, UserHandle userHandle) {
                        return mUserCallIntentProcessor;
                    }
                },
                mDefaultDialerCache,
                mSubscriptionManagerAdapter,
                mSettingsSecureAdapter,
                mFeatureFlags,
                mTelephonyFeatureFlags,
                mLock);
        telecomServiceImpl.setTransactionManager(mTransactionManager);
        telecomServiceImpl.setAnomalyReporterAdapter(mAnomalyReporterAdapter);
        mTSIBinder = telecomServiceImpl.getBinder();
        mComponentContextFixture.setTelecomManager(mTelecomManager);
        when(mTelecomManager.getDefaultDialerPackage()).thenReturn(DEFAULT_DIALER_PACKAGE);
        when(mTelecomManager.getSystemDialerPackage()).thenReturn(DEFAULT_DIALER_PACKAGE);

        mAppOpsManager = (AppOpsManager) mContext.getSystemService(Context.APP_OPS_SERVICE);
        mUserManager = (UserManager) mContext.getSystemService(Context.USER_SERVICE);

        when(mDefaultDialerCache.getDefaultDialerApplication(anyInt()))
                .thenReturn(DEFAULT_DIALER_PACKAGE);
        when(mDefaultDialerCache.isDefaultOrSystemDialer(eq(DEFAULT_DIALER_PACKAGE), anyInt()))
                .thenReturn(true);

        mPackageManager = mContext.getPackageManager();
        when(mPackageManager.getPackageUid(anyString(), eq(0))).thenReturn(Binder.getCallingUid());
        when(mFeatureFlags.earlyBindingToIncallService()).thenReturn(true);
        when(mTelephonyFeatureFlags.workProfileApiSplit()).thenReturn(false);
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @SmallTest
    @Test
    public void testGetDefaultOutgoingPhoneAccount() throws RemoteException {
        when(mFakePhoneAccountRegistrar
                .getOutgoingPhoneAccountForScheme(eq("tel"), any(UserHandle.class)))
                .thenReturn(TEL_PA_HANDLE_16);
        when(mFakePhoneAccountRegistrar
                .getOutgoingPhoneAccountForScheme(eq("sip"), any(UserHandle.class)))
                .thenReturn(SIP_PA_HANDLE_17);
        makeAccountsVisibleToAllUsers(TEL_PA_HANDLE_16, SIP_PA_HANDLE_17);
        PhoneAccount phoneAccount = makePhoneAccount(TEL_PA_HANDLE_CURRENT).build();
        phoneAccount.setIsEnabled(true);
        doReturn(phoneAccount).when(mFakePhoneAccountRegistrar).getPhoneAccount(
                eq(TEL_PA_HANDLE_CURRENT), any(UserHandle.class));
        doNothing().when(mAppOpsManager).checkPackage(anyInt(), anyString());

        PhoneAccountHandle returnedHandleTel
                = mTSIBinder.getDefaultOutgoingPhoneAccount("tel", DEFAULT_DIALER_PACKAGE, null);
        assertEquals(TEL_PA_HANDLE_16, returnedHandleTel);

        PhoneAccountHandle returnedHandleSip
                = mTSIBinder.getDefaultOutgoingPhoneAccount("sip", DEFAULT_DIALER_PACKAGE, null);
        assertEquals(SIP_PA_HANDLE_17, returnedHandleSip);
    }

    /**
     * Clear the groupId from the PhoneAccount if a package does NOT have MODIFY_PHONE_STATE
     */
    @SmallTest
    @Test
    public void testGroupIdIsClearedWhenPermissionIsMissing() throws RemoteException {
        // GIVEN
        PhoneAccount phoneAccount = makePhoneAccount(TEL_PA_HANDLE_CURRENT)
                .setGroupId("testId")
                .build();
        // WHEN
        doReturn(phoneAccount).when(mFakePhoneAccountRegistrar).getPhoneAccount(
                eq(TEL_PA_HANDLE_CURRENT), any(UserHandle.class), anyBoolean());
        doNothing().when(mAppOpsManager).checkPackage(anyInt(), anyString());
        doReturn(PackageManager.PERMISSION_DENIED)
                .when(mContext).checkCallingPermission(MODIFY_PHONE_STATE);
        // THEN
        PhoneAccount account =
                mTSIBinder.getPhoneAccount(TEL_PA_HANDLE_CURRENT, PACKAGE_NAME);
        assertEquals("***", account.getGroupId());
    }

    /**
     * Ensure groupId is not cleared if a package has MODIFY_PHONE_STATE
     */
    @SmallTest
    @Test
    public void testGroupIdIsNotCleared() throws RemoteException {
        // GIVEN
        final String groupId = "testId";
        PhoneAccount phoneAccount = makePhoneAccount(TEL_PA_HANDLE_CURRENT)
                .setGroupId(groupId)
                .build();
        // WHEN
        doReturn(phoneAccount).when(mFakePhoneAccountRegistrar).getPhoneAccount(
                eq(TEL_PA_HANDLE_CURRENT), any(UserHandle.class), anyBoolean());
        doNothing().when(mAppOpsManager).checkPackage(anyInt(), anyString());
        doReturn(PackageManager.PERMISSION_GRANTED)
                .when(mContext).checkCallingPermission(MODIFY_PHONE_STATE);
        // THEN
        PhoneAccount account =
                mTSIBinder.getPhoneAccount(TEL_PA_HANDLE_CURRENT, DEFAULT_DIALER_PACKAGE);
        assertEquals(groupId, account.getGroupId());
    }

    @SmallTest
    @Test
    public void testGetDefaultOutgoingPhoneAccountSucceedsIfCallerIsSimCallManager()
            throws RemoteException {
        when(mFakePhoneAccountRegistrar
                .getOutgoingPhoneAccountForScheme(eq("tel"), any(UserHandle.class)))
                .thenReturn(TEL_PA_HANDLE_16);
        when(mFakePhoneAccountRegistrar
                .getOutgoingPhoneAccountForScheme(eq("sip"), any(UserHandle.class)))
                .thenReturn(SIP_PA_HANDLE_17);
        makeAccountsVisibleToAllUsers(TEL_PA_HANDLE_16, SIP_PA_HANDLE_17);
        PhoneAccount phoneAccount = makePhoneAccount(TEL_PA_HANDLE_CURRENT).build();
        phoneAccount.setIsEnabled(true);
        doReturn(phoneAccount).when(mFakePhoneAccountRegistrar).getPhoneAccount(
                eq(TEL_PA_HANDLE_CURRENT), any(UserHandle.class));
        doReturn(TEL_PA_HANDLE_CURRENT).when(mFakePhoneAccountRegistrar)
                .getSimCallManagerFromHandle(
                eq(TEL_PA_HANDLE_CURRENT), any(UserHandle.class));
        // doNothing will make #isCallerSimCallManager return true
        doNothing().when(mAppOpsManager).checkPackage(anyInt(), anyString());
        doThrow(new SecurityException()).when(mContext)
                .enforceCallingOrSelfPermission(eq(READ_PRIVILEGED_PHONE_STATE), anyString());

        PhoneAccountHandle returnedHandleTel
                = mTSIBinder.getDefaultOutgoingPhoneAccount("tel", DEFAULT_DIALER_PACKAGE, null);
        assertEquals(TEL_PA_HANDLE_16, returnedHandleTel);

        PhoneAccountHandle returnedHandleSip
                = mTSIBinder.getDefaultOutgoingPhoneAccount("sip", DEFAULT_DIALER_PACKAGE, null);
        assertEquals(SIP_PA_HANDLE_17, returnedHandleSip);
    }

    @SmallTest
    @Test
    public void testGetDefaultOutgoingPhoneAccountFailure() throws RemoteException {
        // make sure that the list of user profiles doesn't include anything the PhoneAccountHandles
        // are associated with

        when(mFakePhoneAccountRegistrar
                .getOutgoingPhoneAccountForScheme(eq("tel"), any(UserHandle.class)))
                .thenReturn(TEL_PA_HANDLE_16);
        when(mFakePhoneAccountRegistrar.getPhoneAccountUnchecked(TEL_PA_HANDLE_16)).thenReturn(
                makePhoneAccount(TEL_PA_HANDLE_16).build());
        when(mAppOpsManager.noteOp(eq(AppOpsManager.OP_READ_PHONE_STATE), anyInt(), anyString(),
                nullable(String.class), nullable(String.class)))
                .thenReturn(AppOpsManager.MODE_IGNORED);
        PhoneAccount phoneAccount = makePhoneAccount(TEL_PA_HANDLE_CURRENT).build();
        phoneAccount.setIsEnabled(true);
        doReturn(phoneAccount).when(mFakePhoneAccountRegistrar).getPhoneAccount(
                eq(TEL_PA_HANDLE_CURRENT), any(UserHandle.class));
        doReturn(TEL_PA_HANDLE_16).when(mFakePhoneAccountRegistrar).getSimCallManagerFromHandle(
                eq(TEL_PA_HANDLE_CURRENT), any(UserHandle.class));
        doNothing().when(mAppOpsManager).checkPackage(anyInt(), anyString());
        doThrow(new SecurityException()).when(mContext)
                .enforceCallingOrSelfPermission(eq(READ_PRIVILEGED_PHONE_STATE), anyString());

        PhoneAccountHandle returnedHandleTel
                = mTSIBinder.getDefaultOutgoingPhoneAccount("tel", "", null);
        assertNull(returnedHandleTel);
    }

    @SmallTest
    @Test
    public void testGetUserSelectedOutgoingPhoneAccount() throws RemoteException {
        when(mFakePhoneAccountRegistrar.getUserSelectedOutgoingPhoneAccount(any(UserHandle.class)))
                .thenReturn(TEL_PA_HANDLE_16);
        when(mFakePhoneAccountRegistrar.getPhoneAccountUnchecked(TEL_PA_HANDLE_16)).thenReturn(
                makeMultiUserPhoneAccount(TEL_PA_HANDLE_16).build());

        PhoneAccountHandle returnedHandle
                = mTSIBinder.getUserSelectedOutgoingPhoneAccount(
                        TEL_PA_HANDLE_16.getComponentName().getPackageName());
        assertEquals(TEL_PA_HANDLE_16, returnedHandle);
    }

    @SmallTest
    @Test
    public void testSetUserSelectedOutgoingPhoneAccount() throws RemoteException {
        mTSIBinder.setUserSelectedOutgoingPhoneAccount(TEL_PA_HANDLE_16);
        verify(mFakePhoneAccountRegistrar)
                .setUserSelectedOutgoingPhoneAccount(eq(TEL_PA_HANDLE_16), any(UserHandle.class));
    }

    @Test
    public void testAddCallWithOutgoingCall() throws RemoteException {
        // GIVEN
        CallAttributes mOutgoingCallAttributes = new CallAttributes.Builder(TEL_PA_HANDLE_CURRENT,
                CallAttributes.DIRECTION_OUTGOING, TEST_NAME, TEST_URI)
                .setCallType(CallAttributes.AUDIO_CALL)
                .setCallCapabilities(CallAttributes.SUPPORTS_SET_INACTIVE)
                .build();
        PhoneAccount phoneAccount = makeMultiUserPhoneAccount(TEL_PA_HANDLE_CURRENT).build();
        phoneAccount.setIsEnabled(true);

        // WHEN
        when(mFakePhoneAccountRegistrar.getPhoneAccountUnchecked(TEL_PA_HANDLE_CURRENT)).thenReturn(
                phoneAccount);

        doReturn(phoneAccount).when(mFakePhoneAccountRegistrar).getPhoneAccount(
                eq(TEL_PA_HANDLE_CURRENT), any(UserHandle.class));

        mTSIBinder.addCall(mOutgoingCallAttributes, mICallEventCallback, "1", CALLING_PACKAGE);

        // THEN
        verify(mTransactionManager, times(1))
                .addTransaction(isA(OutgoingCallTransaction.class), isA(OutcomeReceiver.class));
    }

    @Test
    public void testAddCallWithIncomingCall() throws RemoteException {
        // GIVEN
        CallAttributes mIncomingCallAttributes = new CallAttributes.Builder(TEL_PA_HANDLE_CURRENT,
                CallAttributes.DIRECTION_INCOMING, TEST_NAME, TEST_URI)
                .setCallType(CallAttributes.AUDIO_CALL)
                .setCallCapabilities(CallAttributes.SUPPORTS_SET_INACTIVE)
                .build();
        PhoneAccount phoneAccount = makeMultiUserPhoneAccount(TEL_PA_HANDLE_CURRENT).build();
        phoneAccount.setIsEnabled(true);

        // WHEN
        when(mFakePhoneAccountRegistrar.getPhoneAccountUnchecked(TEL_PA_HANDLE_CURRENT)).thenReturn(
                phoneAccount);

        doReturn(phoneAccount).when(mFakePhoneAccountRegistrar).getPhoneAccount(
                eq(TEL_PA_HANDLE_CURRENT), any(UserHandle.class));

        mTSIBinder.addCall(mIncomingCallAttributes, mICallEventCallback, "1", CALLING_PACKAGE);

        // THEN
        verify(mTransactionManager, times(1))
                .addTransaction(isA(IncomingCallTransaction.class), isA(OutcomeReceiver.class));
    }

    @Test
    public void testAddCallWithManagedPhoneAccount() throws RemoteException {
        // GIVEN
        CallAttributes attributes = new CallAttributes.Builder(TEL_PA_HANDLE_CURRENT,
                CallAttributes.DIRECTION_OUTGOING, TEST_NAME, TEST_URI).build();
        PhoneAccount phoneAccount = makeMultiUserPhoneAccount(TEL_PA_HANDLE_CURRENT)
                .setCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)
                .build();
        phoneAccount.setIsEnabled(true);

        // WHEN
        when(mFakePhoneAccountRegistrar.getPhoneAccountUnchecked(TEL_PA_HANDLE_CURRENT)).thenReturn(
                phoneAccount);

        doReturn(phoneAccount).when(mFakePhoneAccountRegistrar).getPhoneAccount(
                eq(TEL_PA_HANDLE_CURRENT), any(UserHandle.class));

        // THEN
        try {
            mTSIBinder.addCall(attributes, mICallEventCallback, "1", CALLING_PACKAGE);
            fail("should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // pass
        }
    }

    @SmallTest
    @Test
    public void testSetUserSelectedOutgoingPhoneAccountWithoutPermission() throws RemoteException {
        doThrow(new SecurityException()).when(mContext).enforceCallingOrSelfPermission(
                anyString(), nullable(String.class));

        assertThrows(SecurityException.class,
                () -> mTSIBinder.setUserSelectedOutgoingPhoneAccount(TEL_PA_HANDLE_16));
    }

    @SmallTest
    @Test
    public void testGetCallCapablePhoneAccounts() throws RemoteException {
        List<PhoneAccountHandle> fullPHList = List.of(TEL_PA_HANDLE_16, SIP_PA_HANDLE_17);
        List<PhoneAccountHandle> smallPHList = List.of(SIP_PA_HANDLE_17);

        // Returns all phone accounts when getCallCapablePhoneAccounts is called.
        when(mFakePhoneAccountRegistrar
                .getCallCapablePhoneAccounts(nullable(String.class), eq(true),
                        nullable(UserHandle.class), eq(true))).thenReturn(fullPHList);
        // Returns only enabled phone accounts when getCallCapablePhoneAccounts is called.
        when(mFakePhoneAccountRegistrar
                .getCallCapablePhoneAccounts(nullable(String.class), eq(false),
                        nullable(UserHandle.class), eq(true))).thenReturn(smallPHList);
        makeAccountsVisibleToAllUsers(TEL_PA_HANDLE_16, SIP_PA_HANDLE_17);

        assertEquals(fullPHList,
                mTSIBinder.getCallCapablePhoneAccounts(
                        true, DEFAULT_DIALER_PACKAGE, null, false).getList());
        assertEquals(smallPHList,
                mTSIBinder.getCallCapablePhoneAccounts(
                        false, DEFAULT_DIALER_PACKAGE, null, false).getList());
    }

    @SmallTest
    @Test
    public void testGetCallCapablePhoneAccountsAcrossProfiles() throws RemoteException {
        List<PhoneAccountHandle> fullPHList = List.of(TEL_PA_HANDLE_16, SIP_PA_HANDLE_17);
        List<PhoneAccountHandle> smallPHList = List.of(SIP_PA_HANDLE_17);

        // Returns all accounts when getCallCapablePhoneAccounts is called with across user.
        doReturn(fullPHList).when(mFakePhoneAccountRegistrar).getCallCapablePhoneAccounts(
                nullable(String.class), anyBoolean(), nullable(UserHandle.class), eq(true));
        // Returns one account when getCallCapablePhoneAccounts is called without across user.
        doReturn(smallPHList).when(mFakePhoneAccountRegistrar).getCallCapablePhoneAccounts(
                nullable(String.class), anyBoolean(), nullable(UserHandle.class), eq(false));
        // With across user permission
        doReturn(PackageManager.PERMISSION_GRANTED).when(mContext).checkCallingOrSelfPermission(
                eq(Manifest.permission.INTERACT_ACROSS_USERS));

        assertEquals(fullPHList,
                mTSIBinder.getCallCapablePhoneAccounts(
                        true, DEFAULT_DIALER_PACKAGE, null, false).getList());

        // Without across user permission
        doReturn(PackageManager.PERMISSION_DENIED).when(mContext).checkCallingOrSelfPermission(
                eq(Manifest.permission.INTERACT_ACROSS_USERS));

        assertEquals(smallPHList,
                mTSIBinder.getCallCapablePhoneAccounts(
                        true, DEFAULT_DIALER_PACKAGE, null, false).getList());

        // Enabled the feature flag of the work profile split mode
        when(mTelephonyFeatureFlags.workProfileApiSplit()).thenReturn(true);

        // With across user permission
        doReturn(PackageManager.PERMISSION_GRANTED).when(mContext).checkCallingOrSelfPermission(
                eq(Manifest.permission.INTERACT_ACROSS_PROFILES));

        assertEquals(fullPHList,
                mTSIBinder.getCallCapablePhoneAccounts(
                        true, DEFAULT_DIALER_PACKAGE, null, true).getList());
        assertEquals(smallPHList,
                mTSIBinder.getCallCapablePhoneAccounts(
                        true, DEFAULT_DIALER_PACKAGE, null, false).getList());

        // Without across user permission
        doReturn(PackageManager.PERMISSION_DENIED).when(mContext).checkCallingOrSelfPermission(
                eq(Manifest.permission.INTERACT_ACROSS_PROFILES));

        assertEquals(smallPHList,
                mTSIBinder.getCallCapablePhoneAccounts(
                        true, DEFAULT_DIALER_PACKAGE, null, true).getList());
        assertEquals(smallPHList,
                mTSIBinder.getCallCapablePhoneAccounts(
                        true, DEFAULT_DIALER_PACKAGE, null, false).getList());
    }

    @SmallTest
    @Test
    public void testGetCallCapablePhoneAccountsWithoutPermission() throws RemoteException {
        List<String> enforcedPermissions = List.of(READ_PHONE_STATE, READ_PRIVILEGED_PHONE_STATE);

        doThrow(new SecurityException()).when(mContext).enforceCallingOrSelfPermission(
                argThat(new AnyStringIn(enforcedPermissions)), anyString());

        assertThrows(SecurityException.class,
                () -> mTSIBinder.getCallCapablePhoneAccounts(true, "", null, false));
    }

    @SmallTest
    @Test
    public void testGetSelfManagedPhoneAccounts() throws RemoteException {
        List<PhoneAccountHandle> accounts = List.of(TEL_PA_HANDLE_16);

        when(mFakePhoneAccountRegistrar.getSelfManagedPhoneAccounts(nullable(UserHandle.class)))
                .thenReturn(accounts);
        makeAccountsVisibleToAllUsers(TEL_PA_HANDLE_16);

        assertEquals(accounts,
                mTSIBinder.getSelfManagedPhoneAccounts(DEFAULT_DIALER_PACKAGE, null).getList());
    }

    @SmallTest
    @Test
    public void testGetSelfManagedPhoneAccountsWithoutPermission() throws RemoteException {
        List<String> enforcedPermissions = List.of(READ_PHONE_STATE, READ_PRIVILEGED_PHONE_STATE);
        doThrow(new SecurityException()).when(mContext).enforceCallingOrSelfPermission(
                argThat(new AnyStringIn(enforcedPermissions)), anyString());

        assertThrows(SecurityException.class,
                () -> mTSIBinder.getSelfManagedPhoneAccounts("", null));
    }

    @SmallTest
    @Test
    public void testGetOwnSelfManagedPhoneAccounts() throws RemoteException {
        List<PhoneAccountHandle> accounts = List.of(TEL_PA_HANDLE_16);

        when(mFakePhoneAccountRegistrar.getSelfManagedPhoneAccountsForPackage(
                eq(DEFAULT_DIALER_PACKAGE), nullable(UserHandle.class)))
                .thenReturn(accounts);
        makeAccountsVisibleToAllUsers(TEL_PA_HANDLE_16);

        assertEquals(accounts,
                mTSIBinder.getOwnSelfManagedPhoneAccounts(DEFAULT_DIALER_PACKAGE, null).getList());
    }

    @SmallTest
    @Test
    public void testGetOwnSelfManagedPhoneAccountsWithoutPermission() throws RemoteException {
        List<String> enforcedPermissions = List.of(MANAGE_OWN_CALLS);
        doThrow(new SecurityException()).when(mContext).enforceCallingOrSelfPermission(
                argThat(new AnyStringIn(enforcedPermissions)), anyString());

        assertThrows(SecurityException.class,
                () -> mTSIBinder.getOwnSelfManagedPhoneAccounts("", null));
    }

    @SmallTest
    @Test
    public void testGetPhoneAccountsSupportingScheme() throws RemoteException {
        List<PhoneAccountHandle> sipPHList = List.of(SIP_PA_HANDLE_17);
        List<PhoneAccountHandle> telPHList = List.of(TEL_PA_HANDLE_16);

        when(mFakePhoneAccountRegistrar
                .getCallCapablePhoneAccounts(eq("tel"), anyBoolean(),
                        any(UserHandle.class), anyBoolean()))
                .thenReturn(telPHList);
        when(mFakePhoneAccountRegistrar
                .getCallCapablePhoneAccounts(eq("sip"), anyBoolean(),
                        any(UserHandle.class), anyBoolean()))
                .thenReturn(sipPHList);
        makeAccountsVisibleToAllUsers(TEL_PA_HANDLE_16, SIP_PA_HANDLE_17);

        assertEquals(telPHList,
                mTSIBinder.getPhoneAccountsSupportingScheme(
                        "tel", DEFAULT_DIALER_PACKAGE).getList());
        assertEquals(sipPHList,
                mTSIBinder.getPhoneAccountsSupportingScheme(
                        "sip", DEFAULT_DIALER_PACKAGE).getList());
    }

    @SmallTest
    @Test
    public void testGetPhoneAccountsSupportingSchemeWithoutPermission() throws RemoteException {
        List<String> enforcedPermissions = List.of(MODIFY_PHONE_STATE);
        doThrow(new SecurityException()).when(mContext).enforceCallingOrSelfPermission(
                argThat(new AnyStringIn(enforcedPermissions)), anyString());

        assertTrue(mTSIBinder.getPhoneAccountsSupportingScheme("any", "").getList().isEmpty());
    }

    @SmallTest
    @Test
    public void testGetPhoneAccountsForPackage() throws RemoteException {
        List<PhoneAccountHandle> phoneAccountHandleList = List.of(
            TEL_PA_HANDLE_16, SIP_PA_HANDLE_17);
        when(mFakePhoneAccountRegistrar
                .getAllPhoneAccountHandlesForPackage(any(UserHandle.class), anyString()))
                .thenReturn(phoneAccountHandleList);
        makeAccountsVisibleToAllUsers(TEL_PA_HANDLE_16, SIP_PA_HANDLE_17);
        assertEquals(phoneAccountHandleList,
                mTSIBinder.getPhoneAccountsForPackage(
                        TEL_PA_HANDLE_16.getComponentName().getPackageName()).getList());
    }

    @SmallTest
    @Test
    public void testGetPhoneAccountsForPackageWithoutPermission() throws RemoteException {
        List<String> enforcedPermissions = List.of(READ_PRIVILEGED_PHONE_STATE);
        doThrow(new SecurityException()).when(mContext).enforceCallingOrSelfPermission(
                argThat(new AnyStringIn(enforcedPermissions)), any());

        assertThrows(SecurityException.class,
                () -> mTSIBinder.getPhoneAccountsForPackage(""));
    }

    @SmallTest
    @Test
    public void testGetPhoneAccount() throws Exception {
        doReturn(PackageManager.PERMISSION_GRANTED)
                .when(mContext).checkCallingPermission(MODIFY_PHONE_STATE);
        makeAccountsVisibleToAllUsers(TEL_PA_HANDLE_16, SIP_PA_HANDLE_17);
        assertEquals(TEL_PA_HANDLE_16, mTSIBinder.getPhoneAccount(TEL_PA_HANDLE_16,
                mContext.getPackageName()).getAccountHandle());
        assertEquals(SIP_PA_HANDLE_17, mTSIBinder.getPhoneAccount(SIP_PA_HANDLE_17,
                mContext.getPackageName()).getAccountHandle());
        try {
            // Try to call the method without using the caller's package name
            mTSIBinder.getPhoneAccount(TEL_PA_HANDLE_16, null);
            fail("Should have thrown a SecurityException");
        } catch (SecurityException expected) {
        }
    }

    @SmallTest
    @Test
    public void testGetAllPhoneAccountsCount() throws RemoteException {
        List<PhoneAccount> phoneAccountList = List.of(
                makePhoneAccount(TEL_PA_HANDLE_16).build(),
                makePhoneAccount(SIP_PA_HANDLE_17).build());

        when(mFakePhoneAccountRegistrar.getAllPhoneAccounts(any(UserHandle.class), anyBoolean()))
                .thenReturn(phoneAccountList);

        assertEquals(phoneAccountList.size(), mTSIBinder.getAllPhoneAccountsCount());
    }

    @SmallTest
    @Test
    public void testGetAllPhoneAccountsCountWithoutPermission() throws RemoteException {
        List<String> enforcedPermissions = List.of(MODIFY_PHONE_STATE);
        doThrow(new SecurityException()).when(mContext).enforceCallingOrSelfPermission(
                argThat(new AnyStringIn(enforcedPermissions)), any());

        assertThrows(SecurityException.class,
                () -> mTSIBinder.getAllPhoneAccountsCount());
    }

    @SmallTest
    @Test
    public void testGetAllPhoneAccounts() throws RemoteException {
        List<PhoneAccount> phoneAccountList = List.of(
                makePhoneAccount(TEL_PA_HANDLE_16).build(),
                makePhoneAccount(SIP_PA_HANDLE_17).build());

        when(mFakePhoneAccountRegistrar.getAllPhoneAccounts(any(UserHandle.class), anyBoolean()))
                .thenReturn(phoneAccountList);

        assertEquals(phoneAccountList.size(), mTSIBinder.getAllPhoneAccounts().getList().size());
    }

    @SmallTest
    @Test
    public void testGetAllPhoneAccountsWithoutPermission() throws RemoteException {
        List<String> enforcedPermissions = List.of(MODIFY_PHONE_STATE);
        doThrow(new SecurityException()).when(mContext).enforceCallingOrSelfPermission(
                argThat(new AnyStringIn(enforcedPermissions)), any());

        assertThrows(SecurityException.class,
                () -> mTSIBinder.getAllPhoneAccounts());
    }

    @SmallTest
    @Test
    public void testGetAllPhoneAccountHandles() throws RemoteException {
        List<PhoneAccountHandle> handles = List.of(TEL_PA_HANDLE_16, SIP_PA_HANDLE_17);
        when(mFakePhoneAccountRegistrar.getAllPhoneAccountHandles(
                any(UserHandle.class), anyBoolean())).thenReturn(handles);

        assertEquals(handles, mTSIBinder.getAllPhoneAccountHandles().getList());
    }

    @SmallTest
    @Test
    public void testGetAllPhoneAccountHandlesWithoutPermission() throws RemoteException {
        List<String> enforcedPermissions = List.of(MODIFY_PHONE_STATE);
        doThrow(new SecurityException()).when(mContext).enforceCallingOrSelfPermission(
                argThat(new AnyStringIn(enforcedPermissions)), any());

        assertThrows(SecurityException.class,
                () -> mTSIBinder.getAllPhoneAccountHandles());
    }

    @SmallTest
    @Test
    public void testGetSimCallManager() throws RemoteException {
        final PhoneAccountHandle handle = TEL_PA_HANDLE_16;
        final int subId = 1;
        when(mFakePhoneAccountRegistrar.getSimCallManager(eq(subId), any(UserHandle.class)))
                .thenReturn(handle);

        assertEquals(handle, mTSIBinder.getSimCallManager(subId, "any"));
    }

    @SmallTest
    @Test
    public void testGetSimCallManagerForUser() throws RemoteException {
        final PhoneAccountHandle handle = TEL_PA_HANDLE_16;
        final int user = 1;
        when(mFakePhoneAccountRegistrar.getSimCallManager(
                argThat(userHandle -> {
                    return userHandle.getIdentifier() == user;
                })))
                .thenReturn(handle);

        assertEquals(handle, mTSIBinder.getSimCallManagerForUser(user, "any"));
    }

    @SmallTest
    @Test
    public void testRegisterPhoneAccount() throws RemoteException {
        String packageNameToUse = "com.android.officialpackage";
        PhoneAccountHandle phHandle = new PhoneAccountHandle(new ComponentName(
                packageNameToUse, "cs"), "test", Binder.getCallingUserHandle());
        PhoneAccount phoneAccount = makePhoneAccount(phHandle).build();
        doReturn(PackageManager.PERMISSION_GRANTED)
                .when(mContext).checkCallingOrSelfPermission(MODIFY_PHONE_STATE);

        registerPhoneAccountTestHelper(phoneAccount, true);
    }

    @SmallTest
    @Test
    public void testRegisterPhoneAccountSimultaneousCallingVerification() throws RemoteException {
        doReturn(true).when(mTelephonyFeatureFlags).simultaneousCallingIndications();
        doReturn(PackageManager.PERMISSION_GRANTED)
                .when(mContext).checkCallingOrSelfPermission(MODIFY_PHONE_STATE);
        String packageNameToUse = "com.android.officialpackage";
        PhoneAccountHandle phHandle = new PhoneAccountHandle(new ComponentName(
                packageNameToUse, "cs"), "test", Binder.getCallingUserHandle());
        PhoneAccountHandle phAllowedRestriction = new PhoneAccountHandle(new ComponentName(
                packageNameToUse, "cs"), "test2", Binder.getCallingUserHandle());

        PhoneAccount phoneAccountEmptyRestriction = makePhoneAccount(phHandle)
                .setSimultaneousCallingRestriction(Collections.emptySet())
                .build();
        try {
            mTSIBinder.registerPhoneAccount(phoneAccountEmptyRestriction, CALLING_PACKAGE);
            verify(mFakePhoneAccountRegistrar).registerPhoneAccount(phoneAccountEmptyRestriction);
        } catch (SecurityException e) {
            fail("registerPhoneAccount must not throw a SecurityException if there is a "
                    + " restriction registered with the same package name.");
        }

        Set<PhoneAccountHandle> restriction = new HashSet<>(3);
        restriction.add(phAllowedRestriction);
        PhoneAccount phoneAccount = makePhoneAccount(phHandle)
                .setSimultaneousCallingRestriction(restriction)
                .build();

        try {
            mTSIBinder.registerPhoneAccount(phoneAccount, CALLING_PACKAGE);
            verify(mFakePhoneAccountRegistrar).registerPhoneAccount(phoneAccount);
        } catch (SecurityException e) {
            fail("registerPhoneAccount must not throw a SecurityException if there is a "
                    + " restriction registered with the same package name.");
        }

        String anotherPackageName = "com.android.anotherpackage";
        PhoneAccountHandle phDisallowedRestriction = new PhoneAccountHandle(new ComponentName(
                anotherPackageName, "cs"), "test", Binder.getCallingUserHandle());
        restriction.add(phDisallowedRestriction);
        phoneAccount = makePhoneAccount(phHandle)
                .setSimultaneousCallingRestriction(restriction)
                .build();

        try {
            mTSIBinder.registerPhoneAccount(phoneAccount, CALLING_PACKAGE);
            // there should not be another call to registerPhoneAccount
            verify(mFakePhoneAccountRegistrar, times(1)).registerPhoneAccount(phoneAccount);
            fail("registerPhoneAccount must throw a SecurityException if there is a "
                    + " restriction registered with a different package name.");
        } catch (SecurityException e) {
            //expected
        }
    }

    @SmallTest
    @Test
    public void testRegisterPhoneAccountWithoutPermissionAnomalyReported() throws RemoteException {
        PhoneAccountHandle handle = new PhoneAccountHandle(
                new ComponentName("package", "cs"), "test", Binder.getCallingUserHandle());
        PhoneAccount account = makeSelfManagedPhoneAccount(handle).build();

        List<String> enforcedPermissions = List.of(MANAGE_OWN_CALLS);
        doThrow(new SecurityException()).when(mContext).enforceCallingOrSelfPermission(
                argThat(new AnyStringIn(enforcedPermissions)), any());

        registerPhoneAccountTestHelper(account, false);
        verify(mAnomalyReporterAdapter).reportAnomaly(
                TelecomServiceImpl.REGISTER_PHONE_ACCOUNT_ERROR_UUID,
                TelecomServiceImpl.REGISTER_PHONE_ACCOUNT_ERROR_MSG);
    }

    @SmallTest
    @Test
    public void testRegisterPhoneAccountSelfManagedWithoutPermission() throws RemoteException {
        PhoneAccountHandle handle = new PhoneAccountHandle(
                new ComponentName("package", "cs"), "test", Binder.getCallingUserHandle());
        PhoneAccount account = makeSelfManagedPhoneAccount(handle).build();

        List<String> enforcedPermissions = List.of(MANAGE_OWN_CALLS);
        doThrow(new SecurityException()).when(mContext).enforceCallingOrSelfPermission(
                argThat(new AnyStringIn(enforcedPermissions)), any());

        registerPhoneAccountTestHelper(account, false);
    }

    @SmallTest
    @Test
    public void testRegisterPhoneAccountSelfManagedInvalidCapabilities() throws RemoteException {
        PhoneAccountHandle handle = new PhoneAccountHandle(
                new ComponentName("package", "cs"), "test", Binder.getCallingUserHandle());

        PhoneAccount selfManagedCallProviderAccount = makePhoneAccount(handle)
                .setCapabilities(
                    PhoneAccount.CAPABILITY_SELF_MANAGED |
                    PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .build();
        registerPhoneAccountTestHelper(selfManagedCallProviderAccount, false);

        PhoneAccount selfManagedConnectionManagerAccount = makePhoneAccount(handle)
                .setCapabilities(
                    PhoneAccount.CAPABILITY_SELF_MANAGED |
                    PhoneAccount.CAPABILITY_CONNECTION_MANAGER)
                .build();
        registerPhoneAccountTestHelper(selfManagedConnectionManagerAccount, false);

        PhoneAccount selfManagedSimSubscriptionAccount = makePhoneAccount(handle)
                .setCapabilities(
                    PhoneAccount.CAPABILITY_SELF_MANAGED |
                    PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)
                .build();
        registerPhoneAccountTestHelper(selfManagedSimSubscriptionAccount, false);
    }

    @SmallTest
    @Test
    public void testRegisterPhoneAccountWithoutModifyPermission() throws RemoteException {
        // tests the case where the package does not have MODIFY_PHONE_STATE but is
        // registering its own phone account as a third-party connection service
        String packageNameToUse = "com.thirdparty.connectionservice";
        PhoneAccountHandle phHandle = new PhoneAccountHandle(new ComponentName(
                packageNameToUse, "cs"), "asdf", Binder.getCallingUserHandle());
        PhoneAccount phoneAccount = makePhoneAccount(phHandle).build();

        doReturn(PackageManager.PERMISSION_DENIED)
                .when(mContext).checkCallingOrSelfPermission(MODIFY_PHONE_STATE);
        PackageManager pm = mContext.getPackageManager();
        when(pm.hasSystemFeature(PackageManager.FEATURE_TELECOM)).thenReturn(true);

        registerPhoneAccountTestHelper(phoneAccount, true);
    }

    @SmallTest
    @Test
    public void testRegisterPhoneAccountWithOldFeatureFlag() throws RemoteException {
        // tests the case where the package does not have MODIFY_PHONE_STATE but is
        // registering its own phone account as a third-party connection service
        String packageNameToUse = "com.thirdparty.connectionservice";
        PhoneAccountHandle phHandle = new PhoneAccountHandle(new ComponentName(
                packageNameToUse, "cs"), "asdf", Binder.getCallingUserHandle());
        PhoneAccount phoneAccount = makePhoneAccount(phHandle).build();

        doReturn(PackageManager.PERMISSION_DENIED)
                .when(mContext).checkCallingOrSelfPermission(MODIFY_PHONE_STATE);
        PackageManager pm = mContext.getPackageManager();
        when(pm.hasSystemFeature(PackageManager.FEATURE_TELECOM)).thenReturn(false);
        when(pm.hasSystemFeature(PackageManager.FEATURE_CONNECTION_SERVICE)).thenReturn(true);

        registerPhoneAccountTestHelper(phoneAccount, true);
    }

    @SmallTest
    @Test
    public void testRegisterPhoneAccountWithoutModifyPermissionFailure() throws RemoteException {
        // tests the case where the third party package should not be allowed to register a phone
        // account due to the lack of modify permission.
        String packageNameToUse = "com.thirdparty.connectionservice";
        PhoneAccountHandle phHandle = new PhoneAccountHandle(new ComponentName(
                packageNameToUse, "cs"), "asdf", Binder.getCallingUserHandle());
        PhoneAccount phoneAccount = makePhoneAccount(phHandle).build();

        doReturn(PackageManager.PERMISSION_DENIED)
                .when(mContext).checkCallingOrSelfPermission(MODIFY_PHONE_STATE);
        PackageManager pm = mContext.getPackageManager();
        when(pm.hasSystemFeature(PackageManager.FEATURE_TELECOM)).thenReturn(false);

        registerPhoneAccountTestHelper(phoneAccount, false);
    }

    @SmallTest
    @Test
    public void testRegisterPhoneAccountWithoutSimSubscriptionPermissionFailure()
            throws RemoteException {
        String packageNameToUse = "com.thirdparty.connectionservice";
        PhoneAccountHandle phHandle = new PhoneAccountHandle(new ComponentName(
                packageNameToUse, "cs"), "asdf", Binder.getCallingUserHandle());
        PhoneAccount phoneAccount = makePhoneAccount(phHandle)
                .setCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION).build();

        doReturn(PackageManager.PERMISSION_GRANTED)
                .when(mContext).checkCallingOrSelfPermission(MODIFY_PHONE_STATE);
        doThrow(new SecurityException())
                .when(mContext)
                .enforceCallingOrSelfPermission(eq(REGISTER_SIM_SUBSCRIPTION),
                        nullable(String.class));

        registerPhoneAccountTestHelper(phoneAccount, false);
    }

    @SmallTest
    @Test
    public void testRegisterPhoneAccountWithoutMultiUserPermissionFailure()
            throws Exception {
        String packageNameToUse = "com.thirdparty.connectionservice";
        PhoneAccountHandle phHandle = new PhoneAccountHandle(new ComponentName(
                packageNameToUse, "cs"), "asdf", Binder.getCallingUserHandle());
        PhoneAccount phoneAccount = makeMultiUserPhoneAccount(phHandle).build();

        doReturn(PackageManager.PERMISSION_GRANTED)
                .when(mContext).checkCallingOrSelfPermission(MODIFY_PHONE_STATE);

        PackageManager packageManager = mContext.getPackageManager();
        when(packageManager.getApplicationInfo(packageNameToUse, PackageManager.GET_META_DATA))
                .thenReturn(new ApplicationInfo());

        registerPhoneAccountTestHelper(phoneAccount, false);
    }

    private void registerPhoneAccountTestHelper(PhoneAccount testPhoneAccount,
            boolean shouldSucceed) throws RemoteException {
        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        boolean didExceptionOccur = false;
        try {
            mTSIBinder.registerPhoneAccount(testPhoneAccount, CALLING_PACKAGE);
        } catch (Exception e) {
            didExceptionOccur = true;
        }

        if (shouldSucceed) {
            assertFalse(didExceptionOccur);
            verify(mFakePhoneAccountRegistrar).registerPhoneAccount(testPhoneAccount);
        } else {
            assertTrue(didExceptionOccur);
            verify(mFakePhoneAccountRegistrar, never())
                    .registerPhoneAccount(any(PhoneAccount.class));
        }
    }

    @SmallTest
    @Test
    public void testRegisterPhoneAccountImageIconCrossUser() throws RemoteException {
        String packageNameToUse = "com.android.officialpackage";
        PhoneAccountHandle phHandle = new PhoneAccountHandle(new ComponentName(
                packageNameToUse, "cs"), "test", Binder.getCallingUserHandle());
        Icon icon = Icon.createWithContentUri("content://10@media/external/images/media/");
        PhoneAccount phoneAccount = makePhoneAccount(phHandle).setIcon(icon).build();
        doReturn(PackageManager.PERMISSION_GRANTED)
                .when(mContext).checkCallingOrSelfPermission(MODIFY_PHONE_STATE);

        // This should fail; security exception will be thrown.
        registerPhoneAccountTestHelper(phoneAccount, false);

        icon = Icon.createWithContentUri(
                new Uri.Builder().scheme("content")
                        .encodedAuthority("10%40media")
                        .path("external/images/media/${mediaId.text}".trim())
                        .build());
        phoneAccount = makePhoneAccount(phHandle).setIcon(icon).build();
        // This should fail; security exception will be thrown
        registerPhoneAccountTestHelper(phoneAccount, false);

        icon = Icon.createWithContentUri( Uri.parse("content://10%40play.ground"));
        phoneAccount = makePhoneAccount(phHandle).setIcon(icon).build();
        // This should fail; security exception will be thrown
        registerPhoneAccountTestHelper(phoneAccount, false);

        icon = Icon.createWithContentUri("content://0@media/external/images/media/");
        phoneAccount = makePhoneAccount(phHandle).setIcon(icon).build();
        // This should succeed.
        registerPhoneAccountTestHelper(phoneAccount, true);
    }

    @SmallTest
    @Test
    public void testUnregisterPhoneAccount() throws RemoteException {
        String packageNameToUse = "com.android.officialpackage";
        PhoneAccountHandle phHandle = new PhoneAccountHandle(new ComponentName(
                packageNameToUse, "cs"), "test", Binder.getCallingUserHandle());

        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        doReturn(PackageManager.PERMISSION_GRANTED)
                .when(mContext).checkCallingOrSelfPermission(MODIFY_PHONE_STATE);

        mTSIBinder.unregisterPhoneAccount(phHandle, CALLING_PACKAGE);
        verify(mFakePhoneAccountRegistrar).unregisterPhoneAccount(phHandle);
    }

    @SmallTest
    @Test
    public void testUnregisterPhoneAccountFailure() throws RemoteException {
        String packageNameToUse = "com.thirdparty.connectionservice";
        PhoneAccountHandle phHandle = new PhoneAccountHandle(new ComponentName(
                packageNameToUse, "cs"), "asdf", Binder.getCallingUserHandle());

        doReturn(PackageManager.PERMISSION_DENIED)
                .when(mContext).checkCallingOrSelfPermission(MODIFY_PHONE_STATE);
        PackageManager pm = mContext.getPackageManager();
        when(pm.hasSystemFeature(PackageManager.FEATURE_TELECOM)).thenReturn(false);

        try {
            mTSIBinder.unregisterPhoneAccount(phHandle, CALLING_PACKAGE);
        } catch (UnsupportedOperationException e) {
            // expected behavior
        }
        verify(mFakePhoneAccountRegistrar, never())
                .unregisterPhoneAccount(any(PhoneAccountHandle.class));
        verify(mContext, never())
                .sendBroadcastAsUser(any(Intent.class), any(UserHandle.class), anyString());
    }

    @SmallTest
    @Test
    public void testClearAccounts() throws RemoteException {
        mTSIBinder.clearAccounts(CALLING_PACKAGE);

        verify(mFakePhoneAccountRegistrar)
                .clearAccounts(CALLING_PACKAGE, mTSIBinder.getCallingUserHandle());
    }

    @SmallTest
    @Test
    public void testClearAccountsWithoutPermission() throws RemoteException {
        doReturn(PackageManager.PERMISSION_DENIED)
                .when(mContext).checkCallingOrSelfPermission(MODIFY_PHONE_STATE);

        assertThrows(UnsupportedOperationException.class,
                () -> mTSIBinder.clearAccounts(CALLING_PACKAGE));
    }

    @SmallTest
    @Test
    public void testAddNewIncomingCall() throws Exception {
        PhoneAccount phoneAccount = makePhoneAccount(TEL_PA_HANDLE_16).build();
        phoneAccount.setIsEnabled(true);
        doReturn(phoneAccount).when(mFakePhoneAccountRegistrar).getPhoneAccount(
                eq(TEL_PA_HANDLE_16), any(UserHandle.class));
        doNothing().when(mAppOpsManager).checkPackage(anyInt(), anyString());
        Bundle extras = createSampleExtras();

        mTSIBinder.addNewIncomingCall(TEL_PA_HANDLE_16, extras, CALLING_PACKAGE);

        verify(mFakePhoneAccountRegistrar).getPhoneAccount(
                TEL_PA_HANDLE_16, TEL_PA_HANDLE_16.getUserHandle());
        verify(mInCallController, never()).bindToServices(any(), anyBoolean());
        addCallTestHelper(TelecomManager.ACTION_INCOMING_CALL,
                CallIntentProcessor.KEY_IS_INCOMING_CALL, extras,
                TEL_PA_HANDLE_16, false);
    }

    @SmallTest
    @Test
    public void testAddNewIncomingFlagDisabledNoEarlyBinding() throws Exception {
        when(mFeatureFlags.earlyBindingToIncallService()).thenReturn(false);
        PhoneAccount phoneAccount = makeSkipCallFilteringPhoneAccount(TEL_PA_HANDLE_16).build();
        phoneAccount.setIsEnabled(true);
        doReturn(phoneAccount).when(mFakePhoneAccountRegistrar).getPhoneAccount(
                eq(TEL_PA_HANDLE_16), any(UserHandle.class));
        doReturn(phoneAccount).when(mFakePhoneAccountRegistrar).getPhoneAccountUnchecked(
                eq(TEL_PA_HANDLE_16));
        doNothing().when(mAppOpsManager).checkPackage(anyInt(), anyString());
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)).thenReturn(true);
        Bundle extras = createSampleExtras();

        mTSIBinder.addNewIncomingCall(TEL_PA_HANDLE_16, extras, CALLING_PACKAGE);

        verify(mInCallController, never()).bindToServices(eq(null), anyBoolean());
    }

    @SmallTest
    @Test
    public void testAddNewIncomingCallEarlyBindingForNoCallFilterCalls() throws Exception {
        PhoneAccount phoneAccount = makeSkipCallFilteringPhoneAccount(TEL_PA_HANDLE_16).build();
        phoneAccount.setIsEnabled(true);
        doReturn(phoneAccount).when(mFakePhoneAccountRegistrar).getPhoneAccount(
                eq(TEL_PA_HANDLE_16), any(UserHandle.class));
        doReturn(phoneAccount).when(mFakePhoneAccountRegistrar).getPhoneAccountUnchecked(
                eq(TEL_PA_HANDLE_16));
        doNothing().when(mAppOpsManager).checkPackage(anyInt(), anyString());
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)).thenReturn(true);
        Bundle extras = createSampleExtras();

        mTSIBinder.addNewIncomingCall(TEL_PA_HANDLE_16, extras, CALLING_PACKAGE);

        verify(mInCallController).bindToServices(eq(null), anyBoolean());
    }

    @SmallTest
    @Test
    public void testAddNewIncomingCallEarlyBindingNotEnableForNonWatchDevices() throws Exception {
        PhoneAccount phoneAccount = makeSkipCallFilteringPhoneAccount(TEL_PA_HANDLE_16).build();
        phoneAccount.setIsEnabled(true);
        doReturn(phoneAccount).when(mFakePhoneAccountRegistrar).getPhoneAccount(
                eq(TEL_PA_HANDLE_16), any(UserHandle.class));
        doReturn(phoneAccount).when(mFakePhoneAccountRegistrar).getPhoneAccountUnchecked(
                eq(TEL_PA_HANDLE_16));
        doNothing().when(mAppOpsManager).checkPackage(anyInt(), anyString());
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)).thenReturn(false);
        Bundle extras = createSampleExtras();

        mTSIBinder.addNewIncomingCall(TEL_PA_HANDLE_16, extras, CALLING_PACKAGE);

        verify(mInCallController, never()).bindToServices(eq(null), anyBoolean());
    }

    @SmallTest
    @Test
    public void testAddNewIncomingCallEarlyBindingNotEnableForPhoneAccountHasCallFilters()
            throws Exception {
        PhoneAccount phoneAccount = makePhoneAccount(TEL_PA_HANDLE_16).build();
        phoneAccount.setIsEnabled(true);
        doReturn(phoneAccount).when(mFakePhoneAccountRegistrar).getPhoneAccount(
                eq(TEL_PA_HANDLE_16), any(UserHandle.class));
        doReturn(phoneAccount).when(mFakePhoneAccountRegistrar).getPhoneAccountUnchecked(
                eq(TEL_PA_HANDLE_16));
        doNothing().when(mAppOpsManager).checkPackage(anyInt(), anyString());
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)).thenReturn(true);
        Bundle extras = createSampleExtras();

        mTSIBinder.addNewIncomingCall(TEL_PA_HANDLE_16, extras, CALLING_PACKAGE);

        verify(mInCallController, never()).bindToServices(eq(null), anyBoolean());
    }


    @SmallTest
    @Test
    public void testAddNewIncomingCallFailure() throws Exception {
        try {
            mTSIBinder.addNewIncomingCall(TEL_PA_HANDLE_16, null, CALLING_PACKAGE);
        } catch (SecurityException e) {
            // expected
        }

        doThrow(new SecurityException()).when(mAppOpsManager).checkPackage(anyInt(), anyString());

        try {
            mTSIBinder.addNewIncomingCall(TEL_PA_HANDLE_CURRENT, null, CALLING_PACKAGE);
        } catch (SecurityException e) {
            // expected
        }

        // Verify that neither of these attempts got through
        verify(mCallIntentProcessorAdapter, never())
                .processIncomingCallIntent(any(CallsManager.class), any(Intent.class));
    }

    @SmallTest
    @Test
    public void testAddNewUnknownCall() throws Exception {
        PhoneAccount phoneAccount = makePhoneAccount(TEL_PA_HANDLE_CURRENT).build();
        phoneAccount.setIsEnabled(true);
        doReturn(phoneAccount).when(mFakePhoneAccountRegistrar).getPhoneAccount(
                eq(TEL_PA_HANDLE_CURRENT), any(UserHandle.class));
        doNothing().when(mAppOpsManager).checkPackage(anyInt(), anyString());
        Bundle extras = createSampleExtras();

        mTSIBinder.addNewUnknownCall(TEL_PA_HANDLE_CURRENT, extras);

        addCallTestHelper(TelecomManager.ACTION_NEW_UNKNOWN_CALL,
                CallIntentProcessor.KEY_IS_UNKNOWN_CALL, extras, TEL_PA_HANDLE_CURRENT, true);
    }

    @SmallTest
    @Test
    public void testAddNewUnknownCallFailure() throws Exception {
        try {
            mTSIBinder.addNewUnknownCall(TEL_PA_HANDLE_16, null);
        } catch (SecurityException e) {
            // expected
        }

        doThrow(new SecurityException()).when(mAppOpsManager).checkPackage(anyInt(), anyString());

        try {
            mTSIBinder.addNewUnknownCall(TEL_PA_HANDLE_CURRENT, null);
        } catch (SecurityException e) {
            // expected
        }

        // Verify that neither of these attempts got through
        verify(mCallIntentProcessorAdapter, never())
                .processIncomingCallIntent(any(CallsManager.class), any(Intent.class));
    }

    private void addCallTestHelper(String expectedAction, String extraCallKey,
            Bundle expectedExtras, PhoneAccountHandle expectedPhoneAccountHandle,
            boolean isUnknown) {
        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        if (isUnknown) {
            verify(mCallIntentProcessorAdapter).processUnknownCallIntent(any(CallsManager.class),
                    intentCaptor.capture());
        } else {
            verify(mCallIntentProcessorAdapter).processIncomingCallIntent(any(CallsManager.class),
                    intentCaptor.capture());
        }
        Intent capturedIntent = intentCaptor.getValue();
        assertEquals(expectedAction, capturedIntent.getAction());
        Bundle intentExtras = capturedIntent.getExtras();
        assertEquals(expectedPhoneAccountHandle,
                intentExtras.get(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE));
        assertTrue(intentExtras.getBoolean(extraCallKey));

        if (isUnknown) {
            for (String expectedKey : expectedExtras.keySet()) {
                assertTrue(intentExtras.containsKey(expectedKey));
                assertEquals(expectedExtras.get(expectedKey), intentExtras.get(expectedKey));
            }
        }
        else {
            assertTrue(areBundlesEqual(expectedExtras,
                    (Bundle) intentExtras.get(TelecomManager.EXTRA_INCOMING_CALL_EXTRAS)));
        }
    }

    /**
     * Place a managed call with no PhoneAccount specified and ensure no security exception is
     * thrown.
     */
    @SmallTest
    @Test
    public void testPlaceCallWithNonEmergencyPermission() throws Exception {
        Uri handle = Uri.parse("tel:6505551234");
        Bundle extras = createSampleExtras();

        // We have passed in the DEFAULT_DIALER_PACKAGE for this test, so canCallPhone is always
        // true.
        when(mAppOpsManager.noteOp(eq(AppOpsManager.OP_CALL_PHONE), anyInt(), anyString(),
                nullable(String.class), nullable(String.class)))
                .thenReturn(AppOpsManager.MODE_ALLOWED);
        doReturn(PackageManager.PERMISSION_GRANTED)
                .when(mContext).checkCallingOrSelfPermission(CALL_PHONE);
        doReturn(PackageManager.PERMISSION_DENIED)
                .when(mContext).checkCallingOrSelfPermission(CALL_PRIVILEGED);

        mTSIBinder.placeCall(handle, extras, DEFAULT_DIALER_PACKAGE, null);
        placeCallTestHelper(handle, extras, /*isSelfManagedExpected*/ false,
                /*shouldNonEmergencyBeAllowed*/ true);
    }

    /**
     * Ensure that we get a SecurityException if the UID of the caller doesn't match the UID of the
     * UID of the package name passed in.
     */
    @SmallTest
    @Test
    public void testPlaceCall_enforceCallingPackageFailure() throws Exception {
        Uri handle = Uri.parse("tel:6505551234");
        Bundle extras = createSampleExtras();
        // callingPackage matches the PhoneAccountHandle, so this is an app with a self-managed
        // ConnectionService.
        extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, TEL_PA_HANDLE_CURRENT);

        // Return a non-matching UID for testing purposes.
        when(mPackageManager.getPackageUid(anyString(), eq(0))).thenReturn(-1);
        try {
            mTSIBinder.placeCall(handle, extras, PACKAGE_NAME, null);
            fail("Expected SecurityException because calling package doesn't match");
        } catch(SecurityException e) {
            // expected
        }
    }

    /**
     * In the case that there is a self-managed call request and MANAGE_OWN_CALLS is granted, ensure
     * that placeCall does not generate a SecurityException.
     */
    @SmallTest
    @Test
    public void testPlaceCall_selfManaged_permissionGranted() throws Exception {
        doReturn(false).when(mDefaultDialerCache).isDefaultOrSystemDialer(
                eq(DEFAULT_DIALER_PACKAGE), anyInt());
        when(mFakePhoneAccountRegistrar.getPhoneAccountUnchecked(TEL_PA_HANDLE_CURRENT)).thenReturn(
                makeSelfManagedPhoneAccount(TEL_PA_HANDLE_CURRENT).build());
        Uri handle = Uri.parse("tel:6505551234");
        Bundle extras = createSampleExtras();
        // callingPackage matches the PhoneAccountHandle, so this is an app with a self-managed
        // ConnectionService.
        extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, TEL_PA_HANDLE_CURRENT);

        // pass MANAGE_OWN_CALLS check, but do not have CALL_PHONE
        doNothing().when(mContext).enforceCallingOrSelfPermission(
                eq(Manifest.permission.MANAGE_OWN_CALLS), anyString());
        doReturn(PackageManager.PERMISSION_DENIED)
                .when(mContext).checkCallingOrSelfPermission(CALL_PHONE);
        doReturn(PackageManager.PERMISSION_DENIED)
                .when(mContext).checkCallingOrSelfPermission(CALL_PRIVILEGED);

        try {
            mTSIBinder.placeCall(handle, extras, PACKAGE_NAME, null);
            placeCallTestHelper(handle, extras, /*isSelfManagedExpected*/ true,
                    /*shouldNonEmergencyBeAllowed*/ false);
        } catch(SecurityException e) {
            fail("Unexpected SecurityException - MANAGE_OWN_CALLS is set");
        }
    }

    /**
     * In the case that the placeCall API is being used place a self-managed call
     * (phone account is marked self-managed and the calling application owns that PhoneAccount),
     * ensure that the call gets placed as not self-managed as to not disclose PA info.
     */
    @SmallTest
    @Test
    public void testPlaceCall_selfManaged_noPermission() throws Exception {
        doReturn(false).when(mDefaultDialerCache).isDefaultOrSystemDialer(
                eq(DEFAULT_DIALER_PACKAGE), anyInt());
        when(mFakePhoneAccountRegistrar.getPhoneAccountUnchecked(TEL_PA_HANDLE_CURRENT)).thenReturn(
                makeSelfManagedPhoneAccount(TEL_PA_HANDLE_CURRENT).build());
        Uri handle = Uri.parse("tel:6505551234");
        Bundle extras = createSampleExtras();
        // callingPackage matches the PhoneAccountHandle, so this is an app with a self-managed
        // ConnectionService.
        extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, TEL_PA_HANDLE_CURRENT);

        doThrow(new SecurityException()).when(mContext).enforceCallingOrSelfPermission(
                eq(Manifest.permission.MANAGE_OWN_CALLS), anyString());

        try {
            mTSIBinder.placeCall(handle, extras, PACKAGE_NAME, null);
            fail("Expected SecurityException because MANAGE_OWN_CALLS is not set");
        } catch(SecurityException e) {
            // expected
        }
    }

    /**
     * In the case that there is a self-managed call request and the app doesn't own that
     * PhoneAccount, we will need to check CALL_PHONE. If they do not have CALL_PHONE permission,
     * we need to throw a security exception.
     */
    @SmallTest
    @Test
    public void testPlaceCall_selfManaged_permissionFail() throws Exception {
        doReturn(false).when(mDefaultDialerCache).isDefaultOrSystemDialer(
                eq(DEFAULT_DIALER_PACKAGE), anyInt());
        when(mFakePhoneAccountRegistrar.getPhoneAccountUnchecked(TEL_PA_HANDLE_CURRENT)).thenReturn(
                makeSelfManagedPhoneAccount(TEL_PA_HANDLE_CURRENT).build());
        Uri handle = Uri.parse("tel:6505551234");
        Bundle extras = createSampleExtras();
        // callingPackage doesn't match the PhoneAccountHandle package
        extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, TEL_PA_HANDLE_CURRENT);

        // pass MANAGE_OWN_CALLS check, but do not have CALL PHONE
        doNothing().when(mContext).enforceCallingOrSelfPermission(
                eq(Manifest.permission.MANAGE_OWN_CALLS), anyString());
        doReturn(PackageManager.PERMISSION_DENIED)
                .when(mContext).checkCallingOrSelfPermission(CALL_PRIVILEGED);
        doThrow(new SecurityException())
                .when(mContext).enforceCallingOrSelfPermission(eq(CALL_PHONE), anyString());

        try {
            // Calling package is received and is not the same as PACKAGE_NAME
            mTSIBinder.placeCall(handle, extras, PACKAGE_NAME + "2", null);
            fail("Expected a SecurityException - CALL_PHONE was not granted");
        } catch(SecurityException e) {
            // expected
        }
    }

    /**
     * In the case that there is a self-managed call request and the app doesn't own that
     * PhoneAccount, we will need to check CALL_PHONE. If they have the CALL_PHONE permission, but
     * the app op has been denied, this should throw a security exception.
     */
    @SmallTest
    @Test
    public void testPlaceCall_selfManaged_appOpPermissionFail() throws Exception {
        doReturn(false).when(mDefaultDialerCache).isDefaultOrSystemDialer(
                eq(DEFAULT_DIALER_PACKAGE), anyInt());
        when(mFakePhoneAccountRegistrar.getPhoneAccountUnchecked(TEL_PA_HANDLE_CURRENT)).thenReturn(
                makeSelfManagedPhoneAccount(TEL_PA_HANDLE_CURRENT).build());
        Uri handle = Uri.parse("tel:6505551234");
        Bundle extras = createSampleExtras();
        // callingPackage doesn't match the PhoneAccountHandle package.
        extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, TEL_PA_HANDLE_CURRENT);

        // pass MANAGE_OWN_CALLS check, but do not have CALL PHONE
        doNothing().when(mContext).enforceCallingOrSelfPermission(
                eq(Manifest.permission.MANAGE_OWN_CALLS), anyString());
        doNothing().when(mContext).enforceCallingOrSelfPermission(eq(CALL_PHONE), anyString());
        doReturn(PackageManager.PERMISSION_DENIED)
                .when(mContext).checkCallingOrSelfPermission(CALL_PRIVILEGED);
        when(mAppOpsManager.noteOp(eq(AppOpsManager.OP_CALL_PHONE), anyInt(), anyString(),
                nullable(String.class), nullable(String.class)))
                .thenReturn(AppOpsManager.MODE_ERRORED);
        try {
            mTSIBinder.placeCall(handle, extras, PACKAGE_NAME + "2", null);
            fail("Expected a SecurityException - CALL_PHONE app op is denied");
        } catch(SecurityException e) {
            // expected
        }
    }

    /**
     * In the case that there is a self-managed call request and the app doesn't own that
     * PhoneAccount, we will need to check CALL_PHONE. If they have the correct permissions, the
     * call will go through, however we will have removed the self-managed PhoneAccountHandle. The
     * call will go through as a normal managed call request with no PhoneAccountHandle.
     */
    @SmallTest
    @Test
    public void testPlaceCall_selfManaged_differentCallingPackage() throws Exception {
        doReturn(false).when(mDefaultDialerCache).isDefaultOrSystemDialer(
                eq(DEFAULT_DIALER_PACKAGE), anyInt());
        when(mFakePhoneAccountRegistrar.getPhoneAccountUnchecked(TEL_PA_HANDLE_CURRENT)).thenReturn(
                makeSelfManagedPhoneAccount(TEL_PA_HANDLE_CURRENT).build());
        Uri handle = Uri.parse("tel:6505551234");
        Bundle extras = createSampleExtras();
        // callingPackage doesn't match the PhoneAccountHandle package
        extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, TEL_PA_HANDLE_CURRENT);

        // simulate default dialer so CALL_PHONE is granted.
        when(mAppOpsManager.noteOp(eq(AppOpsManager.OP_CALL_PHONE), anyInt(), anyString(),
                nullable(String.class), nullable(String.class)))
                .thenReturn(AppOpsManager.MODE_ALLOWED);
        doReturn(PackageManager.PERMISSION_GRANTED)
                .when(mContext).checkCallingOrSelfPermission(CALL_PHONE);
        doReturn(PackageManager.PERMISSION_DENIED)
                .when(mContext).checkCallingOrSelfPermission(CALL_PRIVILEGED);

        // We expect the call to go through with no PhoneAccount specified, since the request
        // contained a self-managed PhoneAccountHandle that didn't belong to this app.
        Bundle expectedExtras = extras.deepCopy();
        expectedExtras.remove(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE);
        try {
            mTSIBinder.placeCall(handle, extras, DEFAULT_DIALER_PACKAGE, null);
        } catch (SecurityException e) {
            fail("Unexpected SecurityException - CTS is default dialer and MANAGE_OWN_CALLS is not"
                    + " required. Exception: " + e);
        }
        placeCallTestHelper(handle, extras, /*isSelfManagedExpected*/ false,
                /*shouldNonEmergencyBeAllowed*/ true);
    }

    /**
     * In the case that there is a managed call request and the app owns that
     * PhoneAccount (but is not a self-managed), we will still need to check CALL_PHONE.
     */
    @SmallTest
    @Test
    public void testPlaceCall_samePackage_managedPhoneAccount_permissionFail() throws Exception {
        doReturn(false).when(mDefaultDialerCache).isDefaultOrSystemDialer(
                eq(DEFAULT_DIALER_PACKAGE), anyInt());
        when(mFakePhoneAccountRegistrar.getPhoneAccountUnchecked(TEL_PA_HANDLE_CURRENT)).thenReturn(
                makePhoneAccount(TEL_PA_HANDLE_CURRENT).build());
        Uri handle = Uri.parse("tel:6505551234");
        Bundle extras = createSampleExtras();
        // callingPackage doesn't match the PhoneAccountHandle package
        extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, TEL_PA_HANDLE_CURRENT);

        // CALL_PHONE is not granted to the device.
        doThrow(new SecurityException())
                .when(mContext).enforceCallingOrSelfPermission(
                        eq(Manifest.permission.MANAGE_OWN_CALLS), anyString());
        doReturn(PackageManager.PERMISSION_DENIED)
                .when(mContext).checkCallingOrSelfPermission(CALL_PRIVILEGED);
        doThrow(new SecurityException())
                .when(mContext).enforceCallingOrSelfPermission(eq(CALL_PHONE), anyString());

        try {
            mTSIBinder.placeCall(handle, extras, PACKAGE_NAME + "2", null);
            fail("Expected a SecurityException - CALL_PHONE is not granted");
        } catch(SecurityException e) {
            // expected
        }
    }

    /**
     * In the case that there is a managed call request and the app owns that
     * PhoneAccount (but is not a self-managed), we will still need to check CALL_PHONE.
     */
    @SmallTest
    @Test
    public void testPlaceCall_samePackage_managedPhoneAccount_AppOpFail() throws Exception {
        doReturn(false).when(mDefaultDialerCache).isDefaultOrSystemDialer(
                eq(DEFAULT_DIALER_PACKAGE), anyInt());
        when(mFakePhoneAccountRegistrar.getPhoneAccountUnchecked(TEL_PA_HANDLE_CURRENT)).thenReturn(
                makePhoneAccount(TEL_PA_HANDLE_CURRENT).build());
        Uri handle = Uri.parse("tel:6505551234");
        Bundle extras = createSampleExtras();
        // callingPackage matches the PhoneAccountHandle, but this is not a self managed phone
        // account.
        extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, TEL_PA_HANDLE_CURRENT);

        // CALL_PHONE is granted, but the app op is not
        doThrow(new SecurityException())
                .when(mContext).enforceCallingOrSelfPermission(
                        eq(Manifest.permission.MANAGE_OWN_CALLS), anyString());
        doReturn(PackageManager.PERMISSION_DENIED)
                .when(mContext).checkCallingOrSelfPermission(CALL_PRIVILEGED);
        doNothing().when(mContext).enforceCallingOrSelfPermission(eq(CALL_PHONE), anyString());
        when(mAppOpsManager.noteOp(eq(AppOpsManager.OP_CALL_PHONE), anyInt(), anyString(),
                nullable(String.class), nullable(String.class)))
                .thenReturn(AppOpsManager.MODE_ERRORED);

        try {
            mTSIBinder.placeCall(handle, extras, PACKAGE_NAME + "2", null);
            fail("Expected a SecurityException - CALL_PHONE app op is denied");
        } catch(SecurityException e) {
            // expected
        }
    }

    /**
     * Since this is a self-managed call being requested, so ensure we report the call as
     * self-managed and without non-emergency permissions.
     */
    @SmallTest
    @Test
    public void testPlaceCall_selfManaged_nonEmergencyPermission() throws Exception {
        doReturn(false).when(mDefaultDialerCache).isDefaultOrSystemDialer(
                eq(DEFAULT_DIALER_PACKAGE), anyInt());
        when(mFakePhoneAccountRegistrar.getPhoneAccountUnchecked(TEL_PA_HANDLE_CURRENT)).thenReturn(
                makeSelfManagedPhoneAccount(TEL_PA_HANDLE_CURRENT).build());
        Uri handle = Uri.parse("tel:6505551234");
        Bundle extras = createSampleExtras();
        // callingPackage matches the PhoneAccountHandle, so this is an app with a self-managed
        // ConnectionService.
        extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, TEL_PA_HANDLE_CURRENT);

        // enforceCallingOrSelfPermission is implicitly granted for MANAGE_OWN_CALLS here and
        // CALL_PHONE is not required.
        when(mAppOpsManager.noteOp(eq(AppOpsManager.OP_CALL_PHONE), anyInt(), anyString(),
                nullable(String.class), nullable(String.class)))
                .thenReturn(AppOpsManager.MODE_IGNORED);
        doReturn(PackageManager.PERMISSION_DENIED)
                .when(mContext).checkCallingOrSelfPermission(CALL_PHONE);
        doReturn(PackageManager.PERMISSION_DENIED)
                .when(mContext).checkCallingOrSelfPermission(CALL_PRIVILEGED);

        mTSIBinder.placeCall(handle, extras, PACKAGE_NAME, null);
        placeCallTestHelper(handle, extras, /*isSelfManagedExpected*/ true,
                /*shouldNonEmergencyBeAllowed*/ false);
    }

    /**
     * Default dialer is calling placeCall and has CALL_PHONE granted, so non-emergency calls
     * are allowed.
     */
    @SmallTest
    @Test
    public void testPlaceCall_managed_nonEmergencyGranted() throws Exception {
        doReturn(false).when(mDefaultDialerCache).isDefaultOrSystemDialer(
                eq(DEFAULT_DIALER_PACKAGE), anyInt());
        Uri handle = Uri.parse("tel:6505551234");
        Bundle extras = createSampleExtras();
        // callingPackage doesn't match the PhoneAccountHandle, so this app does not have a
        // self-managed ConnectionService
        extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, TEL_PA_HANDLE_CURRENT);

        // CALL_PHONE granted
        when(mAppOpsManager.noteOp(eq(AppOpsManager.OP_CALL_PHONE), anyInt(), anyString(),
                nullable(String.class), nullable(String.class)))
                .thenReturn(AppOpsManager.MODE_ALLOWED);
        doReturn(PackageManager.PERMISSION_GRANTED)
                .when(mContext).checkCallingOrSelfPermission(CALL_PHONE);
        doReturn(PackageManager.PERMISSION_DENIED)
                .when(mContext).checkCallingOrSelfPermission(CALL_PRIVILEGED);

        mTSIBinder.placeCall(handle, extras, DEFAULT_DIALER_PACKAGE, null);
        placeCallTestHelper(handle, extras, /*isSelfManagedExpected*/ false,
                /*shouldNonEmergencyBeAllowed*/ true);
    }

    /**
     * In the case that there is a managed normal call request and the app has CALL_PRIVILEGED
     * permission, place call should complete successfully.
     */
    @SmallTest
    @Test
    public void testPlaceCallPrivileged() throws Exception {
        doReturn(false).when(mDefaultDialerCache).isDefaultOrSystemDialer(
                eq(DEFAULT_DIALER_PACKAGE), anyInt());
        Uri handle = Uri.parse("tel:6505551234");

        // CALL_PHONE is not granted, but CALL_PRIVILEGED is
        doThrow(new SecurityException())
                .when(mContext).enforceCallingOrSelfPermission(
                        eq(Manifest.permission.MANAGE_OWN_CALLS), anyString());
        doReturn(PackageManager.PERMISSION_GRANTED)
                .when(mContext).checkCallingOrSelfPermission(CALL_PRIVILEGED);
        doThrow(new SecurityException()).when(mContext).enforceCallingOrSelfPermission(
                eq(CALL_PHONE), anyString());
        when(mAppOpsManager.noteOp(eq(AppOpsManager.OP_CALL_PHONE), anyInt(), anyString(),
                nullable(String.class), nullable(String.class)))
                .thenReturn(AppOpsManager.MODE_ERRORED);

        try {
            mTSIBinder.placeCall(handle, null, PACKAGE_NAME + "2", null);
        } catch(SecurityException e) {
            fail("Expected no SecurityException - CALL_PRIVILEGED is granted");
        }
        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mUserCallIntentProcessor).processIntent(intentCaptor.capture(), anyString(),
                eq(false), eq(true), eq(true));
        Intent capturedIntent = intentCaptor.getValue();
        assertEquals(Intent.ACTION_CALL_PRIVILEGED, capturedIntent.getAction());
        assertEquals(handle, capturedIntent.getData());
    }

    /**
     * The default dialer is requesting to place a call and CALL_PHONE is granted, however
     * OP_CALL_PHONE app op is denied to that app, so non-emergency calls will be denied.
     */
    @SmallTest
    @Test
    public void testPlaceCallWithAppOpsOff() throws Exception {
        Uri handle = Uri.parse("tel:6505551234");
        Bundle extras = createSampleExtras();

        // We have passed in the DEFAULT_DIALER_PACKAGE for this test, so canCallPhone is always
        // true.
        when(mAppOpsManager.noteOp(eq(AppOpsManager.OP_CALL_PHONE), anyInt(), anyString(),
                nullable(String.class), nullable(String.class)))
                .thenReturn(AppOpsManager.MODE_IGNORED);
        doReturn(PackageManager.PERMISSION_GRANTED)
                .when(mContext).checkCallingOrSelfPermission(CALL_PHONE);
        doReturn(PackageManager.PERMISSION_DENIED)
                .when(mContext).checkCallingOrSelfPermission(CALL_PRIVILEGED);

        mTSIBinder.placeCall(handle, extras, DEFAULT_DIALER_PACKAGE, null);
        placeCallTestHelper(handle, extras, /*isSelfManagedExpected*/ false,
                /*shouldNonEmergencyBeAllowed*/ false);
    }

    /**
     * The default dialer is requesting to place a call, however CALL_PHONE is denied to that app,
     * so non-emergency calls will be denied.
     */
    @SmallTest
    @Test
    public void testPlaceCallWithNoCallingPermission() throws Exception {
        Uri handle = Uri.parse("tel:6505551234");
        Bundle extras = createSampleExtras();

        // We are assumed to be default dialer in this test, so canCallPhone is always true.
        when(mAppOpsManager.noteOp(eq(AppOpsManager.OP_CALL_PHONE), anyInt(), anyString(),
                nullable(String.class), nullable(String.class)))
                .thenReturn(AppOpsManager.MODE_ALLOWED);
        doReturn(PackageManager.PERMISSION_DENIED)
                .when(mContext).checkCallingOrSelfPermission(CALL_PHONE);
        doReturn(PackageManager.PERMISSION_DENIED)
                .when(mContext).checkCallingOrSelfPermission(CALL_PRIVILEGED);

        mTSIBinder.placeCall(handle, extras, DEFAULT_DIALER_PACKAGE, null);
        placeCallTestHelper(handle, extras, /*isSelfManagedExpected*/ false,
                /*shouldNonEmergencyBeAllowed*/ false);
    }

    /**
     * Ensure the expected handle, extras, and non-emergency call permission checks have been
     * correctly included in the ACTION_CALL intent as part of the
     * {@link UserCallIntentProcessor#processIntent} method called during the placeCall procedure.
     * @param expectedHandle Expected outgoing number handle
     * @param expectedExtras Expected extras in the ACTION_CALL intent.
     * @param shouldNonEmergencyBeAllowed true if non-emergency calls should be allowed, false if
     *                                    permission checks failed for non-emergency.
     */
    private void placeCallTestHelper(Uri expectedHandle, Bundle expectedExtras,
            boolean isSelfManagedExpected, boolean shouldNonEmergencyBeAllowed) {
        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mUserCallIntentProcessor).processIntent(intentCaptor.capture(), anyString(),
                eq(isSelfManagedExpected), eq(shouldNonEmergencyBeAllowed), eq(true));
        Intent capturedIntent = intentCaptor.getValue();
        assertEquals(Intent.ACTION_CALL, capturedIntent.getAction());
        assertEquals(expectedHandle, capturedIntent.getData());
        assertTrue(areBundlesEqual(expectedExtras, capturedIntent.getExtras()));
    }

    /**
     * Ensure that if the caller was never granted CALL_PHONE (and is not the default dialer), a
     * SecurityException is thrown.
     */
    @SmallTest
    @Test
    public void testPlaceCallFailure() throws Exception {
        Uri handle = Uri.parse("tel:6505551234");
        Bundle extras = createSampleExtras();

        // The app is not considered a privileged dialer and does not have the CALL_PHONE
        // permission.
        doThrow(new SecurityException())
                .when(mContext).enforceCallingOrSelfPermission(eq(CALL_PHONE), anyString());
        doReturn(PackageManager.PERMISSION_DENIED)
                .when(mContext).checkCallingOrSelfPermission(CALL_PRIVILEGED);

        try {
            mTSIBinder.placeCall(handle, extras, "arbitrary_package_name", null);
            fail("Expected SecurityException because CALL_PHONE was not granted to caller");
        } catch (SecurityException e) {
            // expected
        }

        verify(mUserCallIntentProcessor, never())
                .processIntent(any(Intent.class), anyString(), eq(false), anyBoolean(), eq(true));
    }

    /**
     * Ensure that if the caller was granted CALL_PHONE, but did not get the OP_CALL_PHONE app op
     * (and is not the default dialer), a SecurityException is thrown.
     */
    @SmallTest
    @Test
    public void testPlaceCallAppOpFailure() throws Exception {
        Uri handle = Uri.parse("tel:6505551234");
        Bundle extras = createSampleExtras();

        // The app is not considered a privileged dialer and does not have the OP_CALL_PHONE
        // app op.
        doNothing().when(mContext).enforceCallingOrSelfPermission(eq(CALL_PHONE), anyString());
        doReturn(PackageManager.PERMISSION_DENIED)
                .when(mContext).checkCallingOrSelfPermission(CALL_PRIVILEGED);
        when(mAppOpsManager.noteOp(eq(AppOpsManager.OP_CALL_PHONE), anyInt(), anyString(),
                nullable(String.class), nullable(String.class)))
                .thenReturn(AppOpsManager.MODE_IGNORED);

        try {
            mTSIBinder.placeCall(handle, extras, "arbitrary_package_name", null);
            fail("Expected SecurityException because CALL_PHONE was not granted to caller");
        } catch (SecurityException e) {
            // expected
        }

        verify(mUserCallIntentProcessor, never())
                .processIntent(any(Intent.class), anyString(), eq(false), anyBoolean(), eq(true));
    }

    @SmallTest
    @Test
    public void testSetDefaultDialer() throws Exception {
        String packageName = "sample.package";
        int currentUser = ActivityManager.getCurrentUser();

        String[] defaultDialer = new String[1];
        doAnswer(invocation -> {
            defaultDialer[0] = packageName;
            mDefaultDialerObserver.accept(currentUser);
            return true;
        }).when(mDefaultDialerCache).setDefaultDialer(eq(packageName), eq(currentUser));
        doAnswer(invocation -> defaultDialer[0]).when(mDefaultDialerCache)
                .getDefaultDialerApplication(eq(currentUser));

        mTSIBinder.setDefaultDialer(packageName);

        verify(mDefaultDialerCache).setDefaultDialer(eq(packageName), eq(currentUser));
        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext).sendBroadcastAsUser(intentCaptor.capture(), any(UserHandle.class));
        Intent capturedIntent = intentCaptor.getValue();
        assertEquals(TelecomManager.ACTION_DEFAULT_DIALER_CHANGED, capturedIntent.getAction());
        String packageNameExtra = capturedIntent.getStringExtra(
                TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME);
        assertEquals(packageName, packageNameExtra);
    }

    @SmallTest
    @Test
    public void testSetDefaultDialerNoModifyPhoneStatePermission() throws Exception {
        doThrow(new SecurityException()).when(mContext).enforceCallingOrSelfPermission(
                eq(MODIFY_PHONE_STATE), nullable(String.class));
        setDefaultDialerFailureTestHelper();
    }

    @SmallTest
    @Test
    public void testSetDefaultDialerNoWriteSecureSettingsPermission() throws Exception {
        doThrow(new SecurityException()).when(mContext).enforceCallingOrSelfPermission(
                eq(WRITE_SECURE_SETTINGS), nullable(String.class));
        setDefaultDialerFailureTestHelper();
    }

    private void setDefaultDialerFailureTestHelper() throws Exception {
        boolean exceptionThrown = false;
        try {
            mTSIBinder.setDefaultDialer(DEFAULT_DIALER_PACKAGE);
        } catch (SecurityException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
        verify(mDefaultDialerCache, never()).setDefaultDialer(anyString(), anyInt());
        verify(mContext, never()).sendBroadcastAsUser(any(Intent.class), any(UserHandle.class));
    }

    /**
     * FeatureFlags is autogenerated code, so there could be a situation where something changes
     * outside of Telecom control that breaks reflection. This test attempts to ensure that changes
     * to auto-generated FeatureFlags code that breaks reflection are caught early.
     */
    @SmallTest
    @Test
    public void testFlagConfigReflectionWorks() {
        try {
            Method[] methods = FeatureFlags.class.getMethods();
            for (Method m : methods) {
                // test getting the name and invoking the flag code
                String name = m.getName();
                Object val = m.invoke(mFeatureFlags);
                assertNotNull(name);
                assertNotNull(val);
            }
        } catch (Exception e) {
            fail("Reflection failed for FeatureFlags with error: " + e);
        }
    }

    @SmallTest
    @Test
    public void testIsVoicemailNumber() throws Exception {
        String vmNumber = "010";
        makeAccountsVisibleToAllUsers(TEL_PA_HANDLE_CURRENT);

        doReturn(true).when(mFakePhoneAccountRegistrar).isVoiceMailNumber(TEL_PA_HANDLE_CURRENT,
                vmNumber);
        assertTrue(mTSIBinder.isVoiceMailNumber(TEL_PA_HANDLE_CURRENT,
                vmNumber, DEFAULT_DIALER_PACKAGE, null));
    }

    @SmallTest
    @Test
    public void testIsVoicemailNumberAccountNotVisibleFailure() throws Exception {
        String vmNumber = "010";

        doReturn(true).when(mFakePhoneAccountRegistrar).isVoiceMailNumber(TEL_PA_HANDLE_CURRENT,
                vmNumber);

        when(mFakePhoneAccountRegistrar.getPhoneAccount(TEL_PA_HANDLE_CURRENT,
                Binder.getCallingUserHandle())).thenReturn(null);
        assertFalse(mTSIBinder
                .isVoiceMailNumber(TEL_PA_HANDLE_CURRENT, vmNumber, DEFAULT_DIALER_PACKAGE, null));
    }

    @SmallTest
    @Test
    public void testGetVoicemailNumberWithNullAccountHandle() throws Exception {
        when(mFakePhoneAccountRegistrar.getPhoneAccount(isNull(PhoneAccountHandle.class),
                eq(Binder.getCallingUserHandle())))
                .thenReturn(makePhoneAccount(TEL_PA_HANDLE_CURRENT).build());
        int subId = 58374;
        String vmNumber = "543";
        doReturn(subId).when(mSubscriptionManagerAdapter).getDefaultVoiceSubId();

        TelephonyManager mockTelephonyManager =
                (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        when(mockTelephonyManager.getVoiceMailNumber()).thenReturn(vmNumber);

        assertEquals(vmNumber, mTSIBinder.getVoiceMailNumber(null, DEFAULT_DIALER_PACKAGE, null));
    }

    @SmallTest
    @Test
    public void testGetVoicemailNumberWithNonNullAccountHandle() throws Exception {
        when(mFakePhoneAccountRegistrar.getPhoneAccount(eq(TEL_PA_HANDLE_CURRENT),
                eq(Binder.getCallingUserHandle())))
                .thenReturn(makePhoneAccount(TEL_PA_HANDLE_CURRENT).build());
        int subId = 58374;
        String vmNumber = "543";

        TelephonyManager mockTelephonyManager =
                (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        when(mockTelephonyManager.getVoiceMailNumber()).thenReturn(vmNumber);
        when(mFakePhoneAccountRegistrar.getSubscriptionIdForPhoneAccount(TEL_PA_HANDLE_CURRENT))
                .thenReturn(subId);

        assertEquals(vmNumber,
                mTSIBinder.getVoiceMailNumber(TEL_PA_HANDLE_CURRENT, DEFAULT_DIALER_PACKAGE, null));
    }

    @SmallTest
    @Test
    public void testGetLine1NumberWithNoPermissionTargetPreR() throws Exception {
        setupGetLine1NumberTest();
        setTargetSdkVersion(Build.VERSION_CODES.Q);

        try {
            String line1Number = mTSIBinder.getLine1Number(TEL_PA_HANDLE_CURRENT,
                    DEFAULT_DIALER_PACKAGE, null);
            fail("Should have thrown a SecurityException when invoking getLine1Number without "
                    + "permission, received "
                    + line1Number);
        } catch (SecurityException expected) {
        }
    }

    @SmallTest
    @Test
    public void testGetLine1NumberWithNoPermissionTargetR() throws Exception {
        setupGetLine1NumberTest();

        try {
            String line1Number = mTSIBinder.getLine1Number(TEL_PA_HANDLE_CURRENT,
                    DEFAULT_DIALER_PACKAGE, null);
            fail("Should have thrown a SecurityException when invoking getLine1Number without "
                    + "permission, received "
                    + line1Number);
        } catch (SecurityException expected) {
        }
    }

    @SmallTest
    @Test
    public void testGetLine1NumberWithReadPhoneStateTargetPreR() throws Exception {
        String line1Number = setupGetLine1NumberTest();
        setTargetSdkVersion(Build.VERSION_CODES.Q);
        grantPermissionAndAppOp(READ_PHONE_STATE, AppOpsManager.OPSTR_READ_PHONE_STATE);

        assertEquals(line1Number,
                mTSIBinder.getLine1Number(TEL_PA_HANDLE_CURRENT, DEFAULT_DIALER_PACKAGE, null));
    }

    @SmallTest
    @Test
    public void testGetLine1NumberWithReadPhoneStateTargetR() throws Exception {
        setupGetLine1NumberTest();
        grantPermissionAndAppOp(READ_PHONE_STATE, AppOpsManager.OPSTR_READ_PHONE_STATE);

        try {
            String line1Number = mTSIBinder.getLine1Number(TEL_PA_HANDLE_CURRENT,
                    DEFAULT_DIALER_PACKAGE, null);
            fail("Should have thrown a SecurityException when invoking getLine1Number on target R"
                    + " with READ_PHONE_STATE permission, received "
                    + line1Number);
        } catch (SecurityException expected) {
        }
    }

    @SmallTest
    @Test
    public void testGetLine1NumberWithReadPhoneNumbersTargetR() throws Exception {
        String line1Number = setupGetLine1NumberTest();
        grantPermissionAndAppOp(READ_PHONE_NUMBERS, AppOpsManager.OPSTR_READ_PHONE_NUMBERS);

        assertEquals(line1Number,
                mTSIBinder.getLine1Number(TEL_PA_HANDLE_CURRENT, DEFAULT_DIALER_PACKAGE, null));
    }

    @SmallTest
    @Test
    public void testGetLine1NumberWithReadSmsTargetR() throws Exception {
        String line1Number = setupGetLine1NumberTest();
        grantPermissionAndAppOp(READ_SMS, AppOpsManager.OPSTR_READ_SMS);

        assertEquals(line1Number,
                mTSIBinder.getLine1Number(TEL_PA_HANDLE_CURRENT, DEFAULT_DIALER_PACKAGE, null));
    }

    @SmallTest
    @Test
    public void testGetLine1NumberWithWriteSmsTargetR() throws Exception {
        String line1Number = setupGetLine1NumberTest();
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOpsManager).noteOpNoThrow(
                eq(AppOpsManager.OPSTR_WRITE_SMS), anyInt(), eq(DEFAULT_DIALER_PACKAGE), any(),
                any());

        assertEquals(line1Number,
                mTSIBinder.getLine1Number(TEL_PA_HANDLE_CURRENT, DEFAULT_DIALER_PACKAGE, null));
    }


    @SmallTest
    @Test
    public void testGetLine1NumberAsDefaultDialer() throws Exception {
        String line1Number = setupGetLine1NumberTest();
        doReturn(true).when(mDefaultDialerCache).isDefaultOrSystemDialer(
                eq(DEFAULT_DIALER_PACKAGE), anyInt());

        assertEquals(line1Number,
                mTSIBinder.getLine1Number(TEL_PA_HANDLE_CURRENT, DEFAULT_DIALER_PACKAGE, null));
    }

    private String setupGetLine1NumberTest() throws Exception {
        int subId = 58374;
        String line1Number = "9482752023479";

        setTargetSdkVersion(Build.VERSION_CODES.R);
        doReturn(AppOpsManager.MODE_DEFAULT).when(mAppOpsManager).noteOpNoThrow(anyString(),
                anyInt(), eq(DEFAULT_DIALER_PACKAGE), any(), any());
        makeAccountsVisibleToAllUsers(TEL_PA_HANDLE_CURRENT);
        when(mFakePhoneAccountRegistrar.getSubscriptionIdForPhoneAccount(TEL_PA_HANDLE_CURRENT))
                .thenReturn(subId);
        TelephonyManager mockTelephonyManager =
                (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        when(mockTelephonyManager.getLine1Number()).thenReturn(line1Number);
        doThrow(new SecurityException()).when(mContext).enforceCallingOrSelfPermission(anyString(),
                anyString());
        doReturn(PackageManager.PERMISSION_DENIED).when(mContext).checkCallingOrSelfPermission(
                anyString());
        doReturn(false).when(mDefaultDialerCache).isDefaultOrSystemDialer(
                eq(DEFAULT_DIALER_PACKAGE), anyInt());
        return line1Number;
    }

    private void grantPermissionAndAppOp(String permission, String appop) {
        doReturn(PackageManager.PERMISSION_GRANTED).when(mContext).checkCallingOrSelfPermission(
                eq(permission));
        doNothing().when(mContext).enforceCallingOrSelfPermission(eq(permission), anyString());
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOpsManager).noteOp(eq(appop), anyInt(),
                eq(DEFAULT_DIALER_PACKAGE), any(), any());
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOpsManager).noteOpNoThrow(eq(appop), anyInt(),
                eq(DEFAULT_DIALER_PACKAGE), any(), any());
    }

    private void setTargetSdkVersion(int targetSdkVersion) throws Exception {
        mApplicationInfo.targetSdkVersion = targetSdkVersion;
        doReturn(mApplicationInfo).when(mPackageManager).getApplicationInfoAsUser(
                eq(DEFAULT_DIALER_PACKAGE), anyInt(), any());
    }

    @SmallTest
    @Test
    public void testGetDefaultDialerPackageForUser() throws Exception {
        final int userId = 1;
        final String packageName = "some.package";

        when(mDefaultDialerCache.getDefaultDialerApplication(userId))
                .thenReturn(packageName);

        assertEquals(packageName, mTSIBinder.getDefaultDialerPackageForUser(userId));
    }

    @SmallTest
    @Test
    public void testGetSystemDialerPackage() throws Exception {
        final String packageName = "some.package";

        when(mDefaultDialerCache.getSystemDialerApplication())
                .thenReturn(packageName);

        assertEquals(packageName, mTSIBinder.getSystemDialerPackage(CALLING_PACKAGE));
    }

    @SmallTest
    @Test
    public void testEndCallWithRingingForegroundCall() throws Exception {
        Call call = mock(Call.class);
        when(call.getState()).thenReturn(CallState.RINGING);
        when(mFakeCallsManager.getForegroundCall()).thenReturn(call);
        assertTrue(mTSIBinder.endCall(TEST_PACKAGE));
        verify(mFakeCallsManager).rejectCall(eq(call), eq(false), isNull());
    }

    @SmallTest
    @Test
    public void testEndCallWithSimulatedRingingForegroundCall() throws Exception {
        Call call = mock(Call.class);
        when(call.getState()).thenReturn(CallState.SIMULATED_RINGING);
        when(mFakeCallsManager.getForegroundCall()).thenReturn(call);
        assertTrue(mTSIBinder.endCall(TEST_PACKAGE));
        verify(mFakeCallsManager).rejectCall(eq(call), eq(false), isNull());
    }

    @SmallTest
    @Test
    public void testEndCallWithNonRingingForegroundCall() throws Exception {
        Call call = mock(Call.class);
        when(call.getState()).thenReturn(CallState.ACTIVE);
        when(mFakeCallsManager.getForegroundCall()).thenReturn(call);
        assertTrue(mTSIBinder.endCall(TEST_PACKAGE));
        verify(mFakeCallsManager).disconnectCall(eq(call));
    }

    @SmallTest
    @Test
    public void testEndCallWithNoForegroundCall() throws Exception {
        Call call = mock(Call.class);
        when(call.getState()).thenReturn(CallState.ACTIVE);
        when(mFakeCallsManager.getFirstCallWithState(any())).thenReturn(call);
        assertTrue(mTSIBinder.endCall(TEST_PACKAGE));
        verify(mFakeCallsManager).disconnectCall(eq(call));
    }

    @SmallTest
    @Test
    public void testEndCallWithNoCalls() throws Exception {
        assertFalse(mTSIBinder.endCall(null));
    }

    @SmallTest
    @Test
    public void testAcceptRingingCall() throws Exception {
        Call call = mock(Call.class);
        when(mFakeCallsManager.getFirstCallWithState(anyInt(), anyInt())).thenReturn(call);
        // Not intended to be a real video state. Here to ensure that the call will be answered
        // with whatever video state it's currently in.
        int fakeVideoState = 29578215;
        when(call.getVideoState()).thenReturn(fakeVideoState);
        mTSIBinder.acceptRingingCall("");
        verify(mFakeCallsManager).answerCall(eq(call), eq(fakeVideoState));
    }

    @SmallTest
    @Test
    public void testAcceptRingingCallWithValidVideoState() throws Exception {
        Call call = mock(Call.class);
        when(mFakeCallsManager.getFirstCallWithState(anyInt(), anyInt())).thenReturn(call);
        // Not intended to be a real video state. Here to ensure that the call will be answered
        // with the video state passed in to acceptRingingCallWithVideoState
        int fakeVideoState = 29578215;
        int realVideoState = VideoProfile.STATE_RX_ENABLED | VideoProfile.STATE_TX_ENABLED;
        when(call.getVideoState()).thenReturn(fakeVideoState);
        mTSIBinder.acceptRingingCallWithVideoState("", realVideoState);
        verify(mFakeCallsManager).answerCall(eq(call), eq(realVideoState));
    }

    @SmallTest
    @Test
    public void testIsInCall() throws Exception {
        when(mFakeCallsManager.hasOngoingCalls(any(UserHandle.class), anyBoolean()))
                .thenReturn(true);
        assertTrue(mTSIBinder.isInCall(DEFAULT_DIALER_PACKAGE, null));
    }

    @SmallTest
    @Test
    public void testNotIsInCall() throws Exception {
        when(mFakeCallsManager.hasOngoingCalls(any(UserHandle.class), anyBoolean()))
                .thenReturn(false);
        assertFalse(mTSIBinder.isInCall(DEFAULT_DIALER_PACKAGE, null));
    }

    @SmallTest
    @Test
    public void testIsInCallFail() throws Exception {
        doThrow(new SecurityException()).when(mContext).enforceCallingOrSelfPermission(
                anyString(), any());
        try {
            mTSIBinder.isInCall("blah", null);
            fail();
        } catch (SecurityException e) {
            // desired result
        }
        verify(mFakeCallsManager, never()).hasOngoingCalls(any(UserHandle.class), anyBoolean());
    }

    @SmallTest
    @Test
    public void testIsInManagedCall() throws Exception {
        when(mFakeCallsManager.hasOngoingManagedCalls(any(UserHandle.class), anyBoolean()))
                .thenReturn(true);
        assertTrue(mTSIBinder.isInManagedCall(DEFAULT_DIALER_PACKAGE, null));
    }

    @SmallTest
    @Test
    public void testNotIsInManagedCall() throws Exception {
        when(mFakeCallsManager.hasOngoingManagedCalls(any(UserHandle.class), anyBoolean()))
                .thenReturn(false);
        assertFalse(mTSIBinder.isInManagedCall(DEFAULT_DIALER_PACKAGE, null));
    }

    @SmallTest
    @Test
    public void testIsInManagedCallFail() throws Exception {
        doThrow(new SecurityException()).when(mContext).enforceCallingOrSelfPermission(
                anyString(), any());
        try {
            mTSIBinder.isInManagedCall("blah", null);
            fail();
        } catch (SecurityException e) {
            // desired result
        }
        verify(mFakeCallsManager, never()).hasOngoingCalls(any(UserHandle.class), anyBoolean());
    }

    /**
     * Ensure self-managed calls cannot be ended using {@link TelecomManager#endCall()}.
     * @throws Exception
     */
    @SmallTest
    @Test
    public void testCannotEndSelfManagedCall() throws Exception {
        Call call = mock(Call.class);
        when(call.isSelfManaged()).thenReturn(true);
        when(call.getState()).thenReturn(CallState.ACTIVE);
        when(mFakeCallsManager.getFirstCallWithState(any()))
                .thenReturn(call);
        assertFalse(mTSIBinder.endCall(TEST_PACKAGE));
        verify(mFakeCallsManager, never()).disconnectCall(eq(call));
    }

    /**
     * Ensure self-managed calls cannot be answered using {@link TelecomManager#acceptRingingCall()}
     * or {@link TelecomManager#acceptRingingCall(int)}.
     * @throws Exception
     */
    @SmallTest
    @Test
    public void testCannotAnswerSelfManagedCall() throws Exception {
        Call call = mock(Call.class);
        when(call.isSelfManaged()).thenReturn(true);
        when(call.getState()).thenReturn(CallState.ACTIVE);
        when(mFakeCallsManager.getFirstCallWithState(any()))
                .thenReturn(call);
        mTSIBinder.acceptRingingCall(TEST_PACKAGE);
        verify(mFakeCallsManager, never()).answerCall(eq(call), anyInt());
    }

    @SmallTest
    @Test
    public void testGetAdnUriForPhoneAccount() throws Exception {
        final int subId = 1;
        final Uri adnUri = Uri.parse("content://icc/adn/subId/" + subId);
        PhoneAccount phoneAccount = makePhoneAccount(TEL_PA_HANDLE_CURRENT).build();
        when(mFakePhoneAccountRegistrar.getPhoneAccount(
                eq(TEL_PA_HANDLE_CURRENT), any(UserHandle.class)))
                .thenReturn(phoneAccount);
        when(mFakePhoneAccountRegistrar.getSubscriptionIdForPhoneAccount(TEL_PA_HANDLE_CURRENT))
                .thenReturn(subId);

        assertEquals(adnUri,
                mTSIBinder.getAdnUriForPhoneAccount(TEL_PA_HANDLE_CURRENT, DEFAULT_DIALER_PACKAGE));
    }

    /**
     * Register phone accounts for the supplied PhoneAccountHandles to make them
     * visible to all users (via the isVisibleToCaller method in TelecomServiceImpl.
     * @param handles the handles for which phone accounts should be created for.
     */
    private void makeAccountsVisibleToAllUsers(PhoneAccountHandle... handles) {
        for (PhoneAccountHandle ph : handles) {
            when(mFakePhoneAccountRegistrar.getPhoneAccountUnchecked(eq(ph))).thenReturn(
                    makeMultiUserPhoneAccount(ph).build());
            when(mFakePhoneAccountRegistrar
                    .getPhoneAccount(eq(ph), nullable(UserHandle.class), anyBoolean()))
                    .thenReturn(makeMultiUserPhoneAccount(ph).build());
            when(mFakePhoneAccountRegistrar
                    .getPhoneAccount(eq(ph), nullable(UserHandle.class)))
                    .thenReturn(makeMultiUserPhoneAccount(ph).build());
        }
    }

    private PhoneAccount.Builder makeMultiUserPhoneAccount(PhoneAccountHandle paHandle) {
        PhoneAccount.Builder paBuilder = makePhoneAccount(paHandle);
        paBuilder.setCapabilities(PhoneAccount.CAPABILITY_MULTI_USER);
        return paBuilder;
    }

    private PhoneAccount.Builder makeSelfManagedPhoneAccount(PhoneAccountHandle paHandle) {
        PhoneAccount.Builder paBuilder = makePhoneAccount(paHandle);
        paBuilder.setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED);
        return paBuilder;
    }

    private PhoneAccount.Builder makePhoneAccount(PhoneAccountHandle paHandle) {
        return new PhoneAccount.Builder(paHandle, "testLabel");
    }

    private PhoneAccount.Builder makeSkipCallFilteringPhoneAccount(PhoneAccountHandle paHandle) {
        Bundle extras = new Bundle();
        extras.putBoolean(PhoneAccount.EXTRA_SKIP_CALL_FILTERING, true);
        return new PhoneAccount.Builder(paHandle, "testLabel").setExtras(extras);
    }

    private Bundle createSampleExtras() {
        Bundle extras = new Bundle();
        extras.putString("test_key", "test_value");
        return extras;
    }

    private static boolean areBundlesEqual(Bundle b1, Bundle b2) {
        if (b1.keySet().size() != b2.keySet().size()) return false;

        for (String key1 : b1.keySet()) {
            if (!b1.get(key1).equals(b2.get(key1))) {
                return false;
            }
        }

        for (String key2 : b2.keySet()) {
            if (!b2.get(key2).equals(b1.get(key2))) {
                return false;
            }
        }
        return true;
    }
}
