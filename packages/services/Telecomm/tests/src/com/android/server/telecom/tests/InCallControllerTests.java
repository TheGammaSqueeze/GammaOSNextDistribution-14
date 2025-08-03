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
 * limitations under the License
 */

package com.android.server.telecom.tests;

import static com.android.server.telecom.InCallController.IN_CALL_SERVICE_NOTIFICATION_ID;
import static com.android.server.telecom.InCallController.NOTIFICATION_TAG;
import static com.android.server.telecom.tests.TelecomSystemTest.TEST_TIMEOUT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.Manifest;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.AppOpsManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.UiModeManager;
import android.compat.testing.PlatformCompatChangeRule;
import android.content.AttributionSource;
import android.content.AttributionSourceState;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.PermissionChecker;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.pm.UserInfo;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.permission.PermissionCheckerManager;
import android.telecom.CallAudioState;
import android.telecom.InCallService;
import android.telecom.ParcelableCall;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.test.mock.MockContext;
import android.text.TextUtils;

import androidx.test.filters.MediumTest;
import androidx.test.filters.SmallTest;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.internal.telecom.IInCallAdapter;
import com.android.internal.telecom.IInCallService;
import com.android.server.telecom.Analytics;
import com.android.server.telecom.AnomalyReporterAdapter;
import com.android.server.telecom.Call;
import com.android.server.telecom.CallsManager;
import com.android.server.telecom.CarModeTracker;
import com.android.server.telecom.ClockProxy;
import com.android.server.telecom.DefaultDialerCache;
import com.android.server.telecom.EmergencyCallHelper;
import com.android.server.telecom.InCallController;
import com.android.server.telecom.ParcelableCallUtils;
import com.android.server.telecom.PhoneAccountRegistrar;
import com.android.server.telecom.R;
import com.android.server.telecom.RoleManagerAdapter;
import com.android.server.telecom.SystemStateHelper;
import com.android.server.telecom.TelecomSystem;
import com.android.server.telecom.Timeouts;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@RunWith(JUnit4.class)
public class InCallControllerTests extends TelecomTestCase {
    @Mock CallsManager mMockCallsManager;
    @Mock PhoneAccountRegistrar mMockPhoneAccountRegistrar;
    @Mock SystemStateHelper mMockSystemStateHelper;
    @Mock PackageManager mMockPackageManager;
    @Mock PermissionCheckerManager mMockPermissionCheckerManager;
    @Mock Call mMockCall;
    @Mock Call mMockSystemCall1;
    @Mock Call mMockSystemCall2;
    @Mock Resources mMockResources;
    @Mock AppOpsManager mMockAppOpsManager;
    @Mock MockContext mMockContext;
    @Mock Timeouts.Adapter mTimeoutsAdapter;
    @Mock DefaultDialerCache mDefaultDialerCache;
    @Mock RoleManagerAdapter mMockRoleManagerAdapter;
    @Mock ClockProxy mClockProxy;
    @Mock Analytics.CallInfoImpl mCallInfo;
    @Mock NotificationManager mNotificationManager;
    @Mock PermissionInfo mMockPermissionInfo;
    @Mock InCallController.InCallServiceInfo mInCallServiceInfo;
    @Mock UserManager mMockUserManager;
    @Mock Context mMockCreateContextAsUser;
    @Mock UserManager mMockCurrentUserManager;

    @Rule
    public TestRule compatChangeRule = new PlatformCompatChangeRule();

    private static final int CURRENT_USER_ID = 9;
    private static final String DEF_PKG = "defpkg";
    private static final String DEF_CLASS = "defcls";
    private static final int DEF_UID = 900972;
    private static final String SYS_PKG = "syspkg";
    private static final String SYS_CLASS = "syscls";
    private static final int SYS_UID = 900971;
    private static final String COMPANION_PKG = "cpnpkg";
    private static final String COMPANION_CLASS = "cpncls";
    private static final int COMPANION_UID = 900970;
    private static final String CAR_PKG = "carpkg";
    private static final String CAR2_PKG = "carpkg2";
    private static final String CAR_CLASS = "carcls";
    private static final String CAR2_CLASS = "carcls";
    private static final int CAR_UID = 900969;
    private static final int CAR2_UID = 900968;
    private static final String NONUI_PKG = "nonui_pkg";
    private static final String NONUI_CLASS = "nonui_cls";
    private static final int NONUI_UID = 900973;
    private static final String APPOP_NONUI_PKG = "appop_nonui_pkg";
    private static final String APPOP_NONUI_CLASS = "appop_nonui_cls";
    private static final int APPOP_NONUI_UID = 7;
    private static final String BT_PKG = "btpkg";
    private static final String BT_CLS = "btcls";
    private static final int BT_UID = 900974;

    private static final PhoneAccountHandle PA_HANDLE =
            new PhoneAccountHandle(new ComponentName("pa_pkg", "pa_cls"),
                    "pa_id_0", UserHandle.of(CURRENT_USER_ID));
    private static final UserHandle DUMMY_USER_HANDLE = UserHandle.of(10);

    private UserHandle mUserHandle = UserHandle.of(CURRENT_USER_ID);
    private InCallController mInCallController;
    private TelecomSystem.SyncRoot mLock = new TelecomSystem.SyncRoot() {};
    private EmergencyCallHelper mEmergencyCallHelper;
    private SystemStateHelper.SystemStateListener mSystemStateListener;
    private CarModeTracker mCarModeTracker = spy(new CarModeTracker());
    private BroadcastReceiver mRegisteredReceiver;

    private final int serviceBindingFlags = Context.BIND_AUTO_CREATE
        | Context.BIND_FOREGROUND_SERVICE | Context.BIND_ALLOW_BACKGROUND_ACTIVITY_STARTS
        | Context.BIND_SCHEDULE_LIKE_TOP_APP;

    private UserHandle mChildUserHandle = UserHandle.of(10);
    private @Mock Call mMockChildUserCall;
    private UserHandle mParentUserHandle = UserHandle.of(1);

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.initMocks(this);
        when(mMockCall.getAnalytics()).thenReturn(new Analytics.CallInfo());
        when(mMockCall.getAssociatedUser()).thenReturn(mUserHandle);
        when(mMockCall.getId()).thenReturn("TC@1");
        doReturn(mMockResources).when(mMockContext).getResources();
        doReturn(mMockAppOpsManager).when(mMockContext).getSystemService(AppOpsManager.class);
        doReturn(SYS_PKG).when(mMockResources).getString(
                com.android.internal.R.string.config_defaultDialer);
        doReturn(SYS_CLASS).when(mMockResources).getString(R.string.incall_default_class);
        doReturn(true).when(mMockResources).getBoolean(R.bool.grant_location_permission_enabled);
        when(mDefaultDialerCache.getSystemDialerApplication()).thenReturn(SYS_PKG);
        when(mDefaultDialerCache.getSystemDialerComponent()).thenReturn(
                new ComponentName(SYS_PKG, SYS_CLASS));
        when(mDefaultDialerCache.getBTInCallServicePackage()).thenReturn(BT_PKG);
        mEmergencyCallHelper = new EmergencyCallHelper(mMockContext, mDefaultDialerCache,
                mTimeoutsAdapter);
        when(mMockCallsManager.getRoleManagerAdapter()).thenReturn(mMockRoleManagerAdapter);
        when(mMockContext.getSystemService(eq(Context.NOTIFICATION_SERVICE)))
                .thenReturn(mNotificationManager);
        when(mMockContext.getSystemService(eq(PermissionCheckerManager.class)))
                .thenReturn(mMockPermissionCheckerManager);
        when(mMockPackageManager.getPermissionInfo(anyString(), anyInt())).thenReturn(
                mMockPermissionInfo);
        when(mMockContext.getAttributionSource()).thenReturn(new AttributionSource(Process.myUid(),
                "com.android.server.telecom.tests", null));
        mInCallController = new InCallController(mMockContext, mLock, mMockCallsManager,
                mMockSystemStateHelper, mDefaultDialerCache, mTimeoutsAdapter,
                mEmergencyCallHelper, mCarModeTracker, mClockProxy, mFeatureFlags);
        // Capture the broadcast receiver registered.
        doAnswer(invocation -> {
            mRegisteredReceiver = invocation.getArgument(0);
            return null;
        }).when(mMockContext).registerReceiverAsUser(any(BroadcastReceiver.class),
                any(), any(IntentFilter.class), any(), any());

        ArgumentCaptor<SystemStateHelper.SystemStateListener> systemStateListenerArgumentCaptor
                = ArgumentCaptor.forClass(SystemStateHelper.SystemStateListener.class);
        verify(mMockSystemStateHelper).addListener(systemStateListenerArgumentCaptor.capture());
        mSystemStateListener = systemStateListenerArgumentCaptor.getValue();

        // Companion Apps don't have CONTROL_INCALL_EXPERIENCE permission.
        doAnswer(invocation -> {
            int uid = invocation.getArgument(0);
            switch (uid) {
                case DEF_UID:
                    return new String[] { DEF_PKG };
                case SYS_UID:
                    return new String[] { SYS_PKG };
                case COMPANION_UID:
                    return new String[] { COMPANION_PKG };
                case CAR_UID:
                    return new String[] { CAR_PKG };
                case CAR2_UID:
                    return new String[] { CAR2_PKG };
                case NONUI_UID:
                    return new String[] { NONUI_PKG };
                case APPOP_NONUI_UID:
                    return new String[] { APPOP_NONUI_PKG };
                case BT_UID:
                    return new String[] { BT_PKG };
            }
            return null;
        }).when(mMockPackageManager).getPackagesForUid(anyInt());

        when(mMockPermissionCheckerManager.checkPermission(
                matches(Manifest.permission.CONTROL_INCALL_EXPERIENCE),
                matchesAttributionSourcePackage(COMPANION_PKG), nullable(String.class),
                anyBoolean(), anyBoolean(), anyBoolean(), anyInt()))
                .thenReturn(PackageManager.PERMISSION_DENIED);

        when(mMockPermissionCheckerManager.checkPermission(
                matches(Manifest.permission.CONTROL_INCALL_EXPERIENCE),
                matchesAttributionSourcePackage(CAR_PKG), nullable(String.class),
                anyBoolean(), anyBoolean(), anyBoolean(), anyInt()))
                .thenReturn(PackageManager.PERMISSION_GRANTED);

        when(mMockPermissionCheckerManager.checkPermission(
                matches(Manifest.permission.CONTROL_INCALL_EXPERIENCE),
                matchesAttributionSourcePackage(CAR2_PKG), nullable(String.class),
                anyBoolean(), anyBoolean(), anyBoolean(), anyInt()))
                .thenReturn(PackageManager.PERMISSION_GRANTED);

        when(mMockPermissionCheckerManager.checkPermission(
                matches(Manifest.permission.CONTROL_INCALL_EXPERIENCE),
                matchesAttributionSourcePackage(NONUI_PKG), nullable(String.class),
                anyBoolean(), anyBoolean(), anyBoolean(), anyInt()))
                .thenReturn(PackageManager.PERMISSION_GRANTED);

        when(mMockPermissionCheckerManager.checkPermission(
                matches(Manifest.permission.CONTROL_INCALL_EXPERIENCE),
                matchesAttributionSourcePackage(APPOP_NONUI_PKG), nullable(String.class),
                anyBoolean(), anyBoolean(), anyBoolean(), anyInt()))
                .thenReturn(PackageManager.PERMISSION_DENIED);

        when(mMockCallsManager.getAudioState()).thenReturn(new CallAudioState(false, 0, 0));

        when(mMockContext.getSystemService(eq(Context.USER_SERVICE))).thenReturn(mMockUserManager);
        when(mMockContext.getSystemService(eq(UserManager.class)))
                .thenReturn(mMockUserManager);
        when(mMockContext.createContextAsUser(any(UserHandle.class), eq(0)))
                .thenReturn(mMockCreateContextAsUser);
        when(mMockCreateContextAsUser.getSystemService(eq(UserManager.class)))
                .thenReturn(mMockCurrentUserManager);
        // Mock user info to allow binding on user stored in the phone account (mUserHandle).
        when(mFeatureFlags.separatelyBindToBtIncallService()).thenReturn(false);
        when(mFeatureFlags.telecomResolveHiddenDependencies()).thenReturn(true);
        when(mMockCurrentUserManager.isManagedProfile()).thenReturn(true);
        when(mFeatureFlags.profileUserSupport()).thenReturn(false);
    }

    @Override
    @After
    public void tearDown() throws Exception {
        mInCallController.getHandler().removeCallbacksAndMessages(null);
        waitForHandlerAction(mInCallController.getHandler(), 1000);
        super.tearDown();
    }

    @SmallTest
    @Test
    public void testBringToForeground_NoInCallServices() {
        // verify that there is not any bound InCallServices for the user requesting for foreground
        assertFalse(mInCallController.getInCallServices().containsKey(mUserHandle));
        // ensure that the method behaves properly on invocation
        mInCallController.bringToForeground(true /* showDialPad */, mUserHandle /* callingUser */);
    }

    @SmallTest
    @Test
    public void testCarModeAppRemoval() {
        setupMockPackageManager(true /* default */, true /* system */, true /* external calls */);
        when(mMockCallsManager.getCurrentUserHandle()).thenReturn(mUserHandle);
        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);

        when(mMockSystemStateHelper.isCarModeOrProjectionActive()).thenReturn(true);

        mSystemStateListener.onCarModeChanged(666, CAR_PKG, true);
        verify(mCarModeTracker).handleEnterCarMode(666, CAR_PKG);
        assertTrue(mCarModeTracker.isInCarMode());

        mSystemStateListener.onPackageUninstalled(CAR_PKG);
        verify(mCarModeTracker).forceRemove(CAR_PKG);
        assertFalse(mCarModeTracker.isInCarMode());
    }

    /**
     * Ensure that if we remove a random unrelated app we don't exit car mode.
     */
    @SmallTest
    @Test
    public void testRandomAppRemovalInCarMode() {
        setupMockPackageManager(true /* default */, true /* system */, true /* external calls */);
        when(mMockCallsManager.getCurrentUserHandle()).thenReturn(mUserHandle);
        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);

        when(mMockSystemStateHelper.isCarModeOrProjectionActive()).thenReturn(true);

        mSystemStateListener.onCarModeChanged(666, CAR_PKG, true);
        verify(mCarModeTracker).handleEnterCarMode(666, CAR_PKG);
        assertTrue(mCarModeTracker.isInCarMode());

        mSystemStateListener.onPackageUninstalled("com.foo.test");
        verify(mCarModeTracker, never()).forceRemove(CAR_PKG);
        assertTrue(mCarModeTracker.isInCarMode());
    }

    @SmallTest
    @Test
    public void testAutomotiveProjectionAppRemoval() {
        setupMockPackageManager(true /* default */, true /* system */, true /* external calls */);
        when(mMockCallsManager.getCurrentUserHandle()).thenReturn(mUserHandle);
        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);

        when(mMockSystemStateHelper.isCarModeOrProjectionActive()).thenReturn(true);

        mSystemStateListener.onAutomotiveProjectionStateSet(CAR_PKG);
        verify(mCarModeTracker).handleSetAutomotiveProjection(CAR_PKG);
        assertTrue(mCarModeTracker.isInCarMode());

        mSystemStateListener.onPackageUninstalled(CAR_PKG);
        verify(mCarModeTracker).forceRemove(CAR_PKG);
        assertFalse(mCarModeTracker.isInCarMode());
    }

    @MediumTest
    @Test
    public void testBindToService_NoServicesFound_IncomingCall() throws Exception {
        when(mMockCallsManager.getCurrentUserHandle()).thenReturn(mUserHandle);
        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);
        when(mMockCallsManager.isInEmergencyCall()).thenReturn(false);
        when(mMockCall.isIncoming()).thenReturn(true);
        when(mMockCall.isExternalCall()).thenReturn(false);
        when(mMockCall.getTargetPhoneAccount()).thenReturn(PA_HANDLE);
        when(mTimeoutsAdapter.getEmergencyCallbackWindowMillis(any(ContentResolver.class)))
                .thenReturn(300_000L);

        setupMockPackageManager(false /* default */, true /* system */, false /* external calls */);
        mInCallController.bindToServices(mMockCall, false);

        ArgumentCaptor<Intent> bindIntentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mMockContext).bindServiceAsUser(
                bindIntentCaptor.capture(),
                any(ServiceConnection.class),
                eq(serviceBindingFlags),
                eq(mUserHandle));

        Intent bindIntent = bindIntentCaptor.getValue();
        assertEquals(InCallService.SERVICE_INTERFACE, bindIntent.getAction());
        assertEquals(SYS_PKG, bindIntent.getComponent().getPackageName());
        assertEquals(SYS_CLASS, bindIntent.getComponent().getClassName());
        assertNull(bindIntent.getExtras());
    }

    @MediumTest
    @Test
    public void testBindToService_NoServicesFound_OutgoingCall() throws Exception {
        Bundle callExtras = new Bundle();
        callExtras.putBoolean("whatever", true);

        when(mMockCallsManager.getCurrentUserHandle()).thenReturn(mUserHandle);
        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);
        when(mMockCallsManager.isInEmergencyCall()).thenReturn(false);
        when(mMockCall.isIncoming()).thenReturn(false);
        when(mMockCall.getAssociatedUser()).thenReturn(mUserHandle);
        when(mMockCall.getTargetPhoneAccount()).thenReturn(PA_HANDLE);
        when(mMockCall.getIntentExtras()).thenReturn(callExtras);
        when(mMockCall.isExternalCall()).thenReturn(false);
        when(mTimeoutsAdapter.getEmergencyCallbackWindowMillis(any(ContentResolver.class)))
                .thenReturn(300_000L);

        Intent queryIntent = new Intent(InCallService.SERVICE_INTERFACE);
        setupMockPackageManager(false /* default */, true /* system */, false /* external calls */);
        mInCallController.bindToServices(mMockCall, false);

        ArgumentCaptor<Intent> bindIntentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mMockContext).bindServiceAsUser(
                bindIntentCaptor.capture(),
                any(ServiceConnection.class),
                eq(serviceBindingFlags),
                eq(mUserHandle));

        Intent bindIntent = bindIntentCaptor.getValue();
        assertEquals(InCallService.SERVICE_INTERFACE, bindIntent.getAction());
        assertEquals(SYS_PKG, bindIntent.getComponent().getPackageName());
        assertEquals(SYS_CLASS, bindIntent.getComponent().getClassName());
        assertEquals(PA_HANDLE, bindIntent.getExtras().getParcelable(
                TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE));
        assertEquals(callExtras, bindIntent.getExtras().getParcelable(
                TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS));
    }

    @MediumTest
    @Test
    public void testBindToService_DefaultDialer_NoEmergency() throws Exception {
        Bundle callExtras = new Bundle();
        callExtras.putBoolean("whatever", true);

        when(mMockCallsManager.getCurrentUserHandle()).thenReturn(mUserHandle);
        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);
        when(mMockCallsManager.isInEmergencyCall()).thenReturn(false);
        when(mMockCall.isIncoming()).thenReturn(false);
        when(mMockCall.getAssociatedUser()).thenReturn(mUserHandle);
        when(mMockCall.getTargetPhoneAccount()).thenReturn(PA_HANDLE);
        when(mMockCall.getIntentExtras()).thenReturn(callExtras);
        when(mMockCall.isExternalCall()).thenReturn(false);
        when(mDefaultDialerCache.getDefaultDialerApplication(CURRENT_USER_ID))
                .thenReturn(DEF_PKG);
        when(mMockContext.bindServiceAsUser(any(Intent.class), any(ServiceConnection.class),
                anyInt(), eq(mUserHandle))).thenReturn(true);

        setupMockPackageManager(true /* default */, true /* system */, false /* external calls */);
        mInCallController.bindToServices(mMockCall, false);

        // Query for the different InCallServices
        ArgumentCaptor<Intent> queryIntentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mMockPackageManager, times(4)).queryIntentServicesAsUser(
                queryIntentCaptor.capture(),
                eq(PackageManager.GET_META_DATA | PackageManager.MATCH_DISABLED_COMPONENTS),
                eq(CURRENT_USER_ID));

        // Verify call for default dialer InCallService
        assertEquals(DEF_PKG, queryIntentCaptor.getAllValues().get(0).getPackage());
        // Verify call for car-mode InCallService
        assertEquals(null, queryIntentCaptor.getAllValues().get(1).getPackage());
        // Verify call for non-UI InCallServices
        assertEquals(null, queryIntentCaptor.getAllValues().get(2).getPackage());

        ArgumentCaptor<Intent> bindIntentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mMockContext, times(1)).bindServiceAsUser(
                bindIntentCaptor.capture(),
                any(ServiceConnection.class),
                eq(serviceBindingFlags),
                eq(mUserHandle));

        Intent bindIntent = bindIntentCaptor.getValue();
        assertEquals(InCallService.SERVICE_INTERFACE, bindIntent.getAction());
        assertEquals(DEF_PKG, bindIntent.getComponent().getPackageName());
        assertEquals(DEF_CLASS, bindIntent.getComponent().getClassName());
        assertEquals(PA_HANDLE, bindIntent.getExtras().getParcelable(
                TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE));
        assertEquals(callExtras, bindIntent.getExtras().getParcelable(
                TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS));
    }

    @MediumTest
    @Test
    public void testBindToService_SystemDialer_Emergency() throws Exception {
        Bundle callExtras = new Bundle();
        callExtras.putBoolean("whatever", true);

        when(mMockCallsManager.getCurrentUserHandle()).thenReturn(mUserHandle);
        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);
        when(mMockCallsManager.isInEmergencyCall()).thenReturn(true);
        when(mMockCall.isEmergencyCall()).thenReturn(true);
        when(mMockContext.getSystemService(eq(UserManager.class)))
            .thenReturn(mMockUserManager);
        when(mMockCurrentUserManager.isQuietModeEnabled(any(UserHandle.class)))
                .thenReturn(false);
        when(mMockCall.isIncoming()).thenReturn(false);
        when(mMockCall.getAssociatedUser()).thenReturn(mUserHandle);
        when(mMockCall.getTargetPhoneAccount()).thenReturn(PA_HANDLE);
        when(mMockCall.getIntentExtras()).thenReturn(callExtras);
        when(mMockCall.isExternalCall()).thenReturn(false);
        when(mDefaultDialerCache.getDefaultDialerApplication(CURRENT_USER_ID))
                .thenReturn(DEF_PKG);
        when(mMockContext.bindServiceAsUser(any(Intent.class), any(ServiceConnection.class),
                eq(serviceBindingFlags),
                eq(mUserHandle))).thenReturn(true);
        when(mTimeoutsAdapter.getEmergencyCallbackWindowMillis(any(ContentResolver.class)))
                .thenReturn(300_000L);

        setupMockPackageManager(true /* default */, true /* system */, false /* external calls */);
        setupMockPackageManagerLocationPermission(SYS_PKG, false /* granted */);

        mInCallController.bindToServices(mMockCall, false);

        // Query for the different InCallServices
        ArgumentCaptor<Intent> queryIntentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mMockPackageManager, times(4)).queryIntentServicesAsUser(
                queryIntentCaptor.capture(),
                eq(PackageManager.GET_META_DATA | PackageManager.MATCH_DISABLED_COMPONENTS),
                eq(CURRENT_USER_ID));

        // Verify call for default dialer InCallService
        assertEquals(DEF_PKG, queryIntentCaptor.getAllValues().get(0).getPackage());
        // Verify call for car-mode InCallService
        assertEquals(null, queryIntentCaptor.getAllValues().get(1).getPackage());
        // Verify call for non-UI InCallServices
        assertEquals(null, queryIntentCaptor.getAllValues().get(2).getPackage());

        ArgumentCaptor<Intent> bindIntentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mMockContext, times(1)).bindServiceAsUser(
                bindIntentCaptor.capture(),
                any(ServiceConnection.class),
                eq(serviceBindingFlags),
                eq(mUserHandle));

        Intent bindIntent = bindIntentCaptor.getValue();
        assertEquals(InCallService.SERVICE_INTERFACE, bindIntent.getAction());
        assertEquals(SYS_PKG, bindIntent.getComponent().getPackageName());
        assertEquals(SYS_CLASS, bindIntent.getComponent().getClassName());
        assertEquals(PA_HANDLE, bindIntent.getExtras().getParcelable(
                TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE));
        assertEquals(callExtras, bindIntent.getExtras().getParcelable(
                TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS));

        verify(mMockPackageManager).grantRuntimePermission(eq(SYS_PKG),
                eq(Manifest.permission.ACCESS_FINE_LOCATION), eq(mUserHandle));

        // Pretend that the call has gone away.
        when(mMockCallsManager.getCalls()).thenReturn(Collections.emptyList());
        mInCallController.onCallRemoved(mMockCall);
        waitForHandlerAction(new Handler(Looper.getMainLooper()), TEST_TIMEOUT);

        verify(mMockPackageManager).revokeRuntimePermission(eq(SYS_PKG),
                eq(Manifest.permission.ACCESS_FINE_LOCATION), eq(mUserHandle));
    }

    @MediumTest
    @Test
    public void
    testBindToService_UserAssociatedWithCallIsInQuietMode_EmergCallInCallUi_BindsToPrimaryUser()
        throws Exception {
        when(mMockCallsManager.getCurrentUserHandle()).thenReturn(mUserHandle);
        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);
        when(mMockCall.isEmergencyCall()).thenReturn(true);
        when(mMockCall.isIncoming()).thenReturn(true);
        when(mMockCall.getAssociatedUser()).thenReturn(DUMMY_USER_HANDLE);
        when(mMockCall.getTargetPhoneAccount()).thenReturn(PA_HANDLE);
        when(mMockContext.getSystemService(eq(UserManager.class)))
            .thenReturn(mMockUserManager);
        when(mMockCurrentUserManager.isQuietModeEnabled(any(UserHandle.class)))
                .thenReturn(true);
        setupMockPackageManager(true /* default */, true /* system */, false /* external calls */);
        setupMockPackageManagerLocationPermission(SYS_PKG, false /* granted */);

        mInCallController.bindToServices(mMockCall, false);

        ArgumentCaptor<Intent> bindIntentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mMockContext, times(1)).bindServiceAsUser(
            bindIntentCaptor.capture(),
            any(ServiceConnection.class),
            eq(serviceBindingFlags),
            eq(mUserHandle));
        Intent bindIntent = bindIntentCaptor.getValue();
        assertEquals(InCallService.SERVICE_INTERFACE, bindIntent.getAction());
    }

    @MediumTest
    @Test
    public void
    testBindToService_UserAssociatedWithCallIsInQuietMode_NonEmergCallECBM_BindsToPrimaryUser()
            throws Exception {
        when(mMockCallsManager.getCurrentUserHandle()).thenReturn(mUserHandle);
        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);
        when(mMockCall.isEmergencyCall()).thenReturn(false);
        when(mMockCall.isInECBM()).thenReturn(true);
        when(mMockCall.isIncoming()).thenReturn(true);
        when(mMockCall.getAssociatedUser()).thenReturn(DUMMY_USER_HANDLE);
        when(mMockCall.getTargetPhoneAccount()).thenReturn(PA_HANDLE);
        when(mMockContext.getSystemService(eq(UserManager.class)))
                .thenReturn(mMockUserManager);
        when(mMockCurrentUserManager.isQuietModeEnabled(any(UserHandle.class)))
                .thenReturn(true);
        setupMockPackageManager(true /* default */, true /* system */, false /* external calls */);
        setupMockPackageManagerLocationPermission(SYS_PKG, false /* granted */);

        mInCallController.bindToServices(mMockCall, false);

        ArgumentCaptor<Intent> bindIntentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mMockContext, times(1)).bindServiceAsUser(
                bindIntentCaptor.capture(),
                any(ServiceConnection.class),
                eq(serviceBindingFlags),
                eq(mUserHandle));
        Intent bindIntent = bindIntentCaptor.getValue();
        assertEquals(InCallService.SERVICE_INTERFACE, bindIntent.getAction());
    }

    @MediumTest
    @Test
    public void
    testBindToService_UserAssociatedWithCallSecondary_NonEmergCallECBM_BindsToSecondaryUser()
            throws Exception {
        UserHandle newUser = new UserHandle(13);
        when(mMockCallsManager.getCurrentUserHandle()).thenReturn(newUser);
        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);
        when(mMockCall.isEmergencyCall()).thenReturn(false);
        when(mMockCall.isInECBM()).thenReturn(true);
        when(mMockCall.isIncoming()).thenReturn(true);
        when(mMockCall.getAssociatedUser()).thenReturn(DUMMY_USER_HANDLE);
        when(mMockCall.getTargetPhoneAccount()).thenReturn(PA_HANDLE);
        when(mMockContext.getSystemService(eq(UserManager.class)))
                .thenReturn(mMockUserManager);
        when(mMockCurrentUserManager.isQuietModeEnabled(any(UserHandle.class)))
                .thenReturn(false);
        when(mMockCurrentUserManager.isAdminUser()).thenReturn(false);
        setupMockPackageManager(true /* default */, true /* system */, false /* external calls */);
        setupMockPackageManagerLocationPermission(SYS_PKG, false /* granted */);

        mInCallController.bindToServices(mMockCall, false);

        ArgumentCaptor<Intent> bindIntentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mMockContext, times(1)).bindServiceAsUser(
                bindIntentCaptor.capture(),
                any(ServiceConnection.class),
                eq(serviceBindingFlags),
                eq(newUser));
        Intent bindIntent = bindIntentCaptor.getValue();
        assertEquals(InCallService.SERVICE_INTERFACE, bindIntent.getAction());
    }

    @MediumTest
    @Test
    public void
    testBindToService_UserAssociatedWithCallNotInQuietMode_EmergCallInCallUi_BindsToAssociatedUser()
        throws Exception {
        when(mMockCallsManager.getCurrentUserHandle()).thenReturn(mUserHandle);
        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);
        when(mMockCall.isEmergencyCall()).thenReturn(true);
        when(mMockCall.getAssociatedUser()).thenReturn(DUMMY_USER_HANDLE);
        when(mMockContext.getSystemService(eq(UserManager.class)))
            .thenReturn(mMockUserManager);
        when(mMockCurrentUserManager.isQuietModeEnabled(any(UserHandle.class)))
                .thenReturn(false);
        when(mMockCurrentUserManager.isAdminUser()).thenReturn(true);
        setupMockPackageManager(true /* default */, true /* system */, false /* external calls */);
        setupMockPackageManagerLocationPermission(SYS_PKG, false /* granted */);

        mInCallController.bindToServices(mMockCall, false);

        ArgumentCaptor<Intent> bindIntentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mMockContext, times(1)).bindServiceAsUser(
            bindIntentCaptor.capture(),
            any(ServiceConnection.class),
            eq(serviceBindingFlags),
            eq(DUMMY_USER_HANDLE));
        Intent bindIntent = bindIntentCaptor.getValue();
        assertEquals(InCallService.SERVICE_INTERFACE, bindIntent.getAction());
    }

    /**
     * This test verifies the behavior of Telecom when the system dialer crashes on binding and must
     * be restarted.  Specifically, it ensures when the system dialer crashes we revoke the runtime
     * location permission, and when it restarts we re-grant the permission.
     * @throws Exception
     */
    @MediumTest
    @Test
    public void testBindToService_SystemDialer_Crash() throws Exception {
        Bundle callExtras = new Bundle();
        callExtras.putBoolean("whatever", true);

        when(mMockCallsManager.getCurrentUserHandle()).thenReturn(mUserHandle);
        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);
        when(mMockCallsManager.isInEmergencyCall()).thenReturn(true);
        when(mMockCall.isEmergencyCall()).thenReturn(true);
        when(mMockContext.getSystemService(eq(UserManager.class)))
            .thenReturn(mMockUserManager);
        when(mMockCurrentUserManager.isQuietModeEnabled(any(UserHandle.class)))
                .thenReturn(false);
        when(mMockCall.isIncoming()).thenReturn(false);
        when(mMockCall.getAssociatedUser()).thenReturn(mUserHandle);
        when(mMockCall.getTargetPhoneAccount()).thenReturn(PA_HANDLE);
        when(mMockCall.getIntentExtras()).thenReturn(callExtras);
        when(mMockCall.isExternalCall()).thenReturn(false);
        when(mDefaultDialerCache.getDefaultDialerApplication(CURRENT_USER_ID))
                .thenReturn(DEF_PKG);
        ArgumentCaptor<ServiceConnection> serviceConnectionCaptor =
                ArgumentCaptor.forClass(ServiceConnection.class);
        when(mMockContext.bindServiceAsUser(any(Intent.class), serviceConnectionCaptor.capture(),
                eq(serviceBindingFlags),
                eq(mUserHandle))).thenReturn(true);
        when(mTimeoutsAdapter.getEmergencyCallbackWindowMillis(any(ContentResolver.class)))
                .thenReturn(300_000L);

        setupMockPackageManager(true /* default */, true /* system */, false /* external calls */);
        setupMockPackageManagerLocationPermission(SYS_PKG, false /* granted */);

        mInCallController.bindToServices(mMockCall, false);

        // Query for the different InCallServices
        ArgumentCaptor<Intent> queryIntentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mMockPackageManager, times(4)).queryIntentServicesAsUser(
                queryIntentCaptor.capture(),
                eq(PackageManager.GET_META_DATA | PackageManager.MATCH_DISABLED_COMPONENTS),
                eq(CURRENT_USER_ID));

        // Verify call for default dialer InCallService
        assertEquals(DEF_PKG, queryIntentCaptor.getAllValues().get(0).getPackage());
        // Verify call for car-mode InCallService
        assertEquals(null, queryIntentCaptor.getAllValues().get(1).getPackage());
        // Verify call for non-UI InCallServices
        assertEquals(null, queryIntentCaptor.getAllValues().get(2).getPackage());

        ArgumentCaptor<Intent> bindIntentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mMockContext, times(1)).bindServiceAsUser(
                bindIntentCaptor.capture(),
                any(ServiceConnection.class),
                eq(serviceBindingFlags),
                eq(mUserHandle));

        Intent bindIntent = bindIntentCaptor.getValue();
        assertEquals(InCallService.SERVICE_INTERFACE, bindIntent.getAction());
        assertEquals(SYS_PKG, bindIntent.getComponent().getPackageName());
        assertEquals(SYS_CLASS, bindIntent.getComponent().getClassName());
        assertEquals(PA_HANDLE, bindIntent.getExtras().getParcelable(
                TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE));
        assertEquals(callExtras, bindIntent.getExtras().getParcelable(
                TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS));

        verify(mMockPackageManager).grantRuntimePermission(eq(SYS_PKG),
                eq(Manifest.permission.ACCESS_FINE_LOCATION), eq(mUserHandle));

        // Emulate a crash in the system dialer; we'll use the captured service connection to signal
        // to InCallController that the dialer died.
        ServiceConnection serviceConnection = serviceConnectionCaptor.getValue();
        serviceConnection.onServiceDisconnected(bindIntent.getComponent());

        // We expect that the permission is revoked at this point.
        verify(mMockPackageManager).revokeRuntimePermission(eq(SYS_PKG),
                eq(Manifest.permission.ACCESS_FINE_LOCATION), eq(mUserHandle));

        // Now, we expect to auto-rebind to the system dialer (verify 2 times since this is the
        // second binding).
        verify(mMockContext, times(2)).bindServiceAsUser(
                bindIntentCaptor.capture(),
                any(ServiceConnection.class),
                eq(serviceBindingFlags),
                eq(mUserHandle));

        // Verify we were re-granted the runtime permission.
        verify(mMockPackageManager, times(2)).grantRuntimePermission(eq(SYS_PKG),
                eq(Manifest.permission.ACCESS_FINE_LOCATION), eq(mUserHandle));
    }

    @MediumTest
    @Test
    public void testBindToService_DefaultDialer_FallBackToSystem() throws Exception {
        Bundle callExtras = new Bundle();
        callExtras.putBoolean("whatever", true);

        when(mMockCallsManager.getCurrentUserHandle()).thenReturn(mUserHandle);
        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);
        when(mMockCallsManager.isInEmergencyCall()).thenReturn(false);
        when(mMockCallsManager.getCalls()).thenReturn(Collections.singletonList(mMockCall));
        when(mMockCallsManager.getAudioState()).thenReturn(null);
        when(mMockCallsManager.canAddCall()).thenReturn(false);
        when(mMockCall.isIncoming()).thenReturn(false);
        when(mMockCall.getAssociatedUser()).thenReturn(mUserHandle);
        when(mMockCall.getTargetPhoneAccount()).thenReturn(PA_HANDLE);
        when(mMockCall.getIntentExtras()).thenReturn(callExtras);
        when(mMockCall.isExternalCall()).thenReturn(false);
        when(mMockCall.getConferenceableCalls()).thenReturn(Collections.emptyList());
        when(mDefaultDialerCache.getDefaultDialerApplication(CURRENT_USER_ID))
                .thenReturn(DEF_PKG);
        when(mMockContext.bindServiceAsUser(
                any(Intent.class), any(ServiceConnection.class), anyInt(), any(UserHandle.class)))
                .thenReturn(true);

        setupMockPackageManager(true /* default */, true /* system */, false /* external calls */);
        mInCallController.bindToServices(mMockCall, false);

        // Query for the different InCallServices
        ArgumentCaptor<Intent> queryIntentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mMockPackageManager, times(4)).queryIntentServicesAsUser(
                queryIntentCaptor.capture(),
                eq(PackageManager.GET_META_DATA | PackageManager.MATCH_DISABLED_COMPONENTS),
                eq(CURRENT_USER_ID));

        // Verify call for default dialer InCallService
        assertEquals(DEF_PKG, queryIntentCaptor.getAllValues().get(0).getPackage());
        // Verify call for car-mode InCallService
        assertEquals(null, queryIntentCaptor.getAllValues().get(1).getPackage());
        // Verify call for non-UI InCallServices
        assertEquals(null, queryIntentCaptor.getAllValues().get(2).getPackage());

        ArgumentCaptor<Intent> bindIntentCaptor = ArgumentCaptor.forClass(Intent.class);
        ArgumentCaptor<ServiceConnection> serviceConnectionCaptor =
                ArgumentCaptor.forClass(ServiceConnection.class);
        verify(mMockContext, times(1)).bindServiceAsUser(
                bindIntentCaptor.capture(),
                serviceConnectionCaptor.capture(),
                eq(serviceBindingFlags),
                eq(mUserHandle));

        Intent bindIntent = bindIntentCaptor.getValue();
        assertEquals(InCallService.SERVICE_INTERFACE, bindIntent.getAction());
        assertEquals(DEF_PKG, bindIntent.getComponent().getPackageName());
        assertEquals(DEF_CLASS, bindIntent.getComponent().getClassName());
        assertEquals(PA_HANDLE, bindIntent.getExtras().getParcelable(
                TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE));
        assertEquals(callExtras, bindIntent.getExtras().getParcelable(
                TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS));

        // We have a ServiceConnection for the default dialer, lets start the connection, and then
        // simulate a crash so that we fallback to system.
        ServiceConnection serviceConnection = serviceConnectionCaptor.getValue();
        ComponentName defDialerComponentName = new ComponentName(DEF_PKG, DEF_CLASS);
        IBinder mockBinder = mock(IBinder.class);
        IInCallService mockInCallService = mock(IInCallService.class);
        when(mockBinder.queryLocalInterface(anyString())).thenReturn(mockInCallService);


        // Start the connection with IInCallService
        serviceConnection.onServiceConnected(defDialerComponentName, mockBinder);
        verify(mockInCallService).setInCallAdapter(any(IInCallAdapter.class));

        // Now crash the damn thing!
        serviceConnection.onServiceDisconnected(defDialerComponentName);

        ArgumentCaptor<Intent> bindIntentCaptor2 = ArgumentCaptor.forClass(Intent.class);
        verify(mMockContext, times(2)).bindServiceAsUser(
                bindIntentCaptor2.capture(),
                any(ServiceConnection.class),
                eq(serviceBindingFlags),
                eq(mUserHandle));

        bindIntent = bindIntentCaptor2.getValue();
        assertEquals(SYS_PKG, bindIntent.getComponent().getPackageName());
        assertEquals(SYS_CLASS, bindIntent.getComponent().getClassName());
    }

    @Test
    public void testBindToService_NullBinding_FallBackToSystem() throws Exception {
        ApplicationInfo applicationInfo = new ApplicationInfo();
        applicationInfo.targetSdkVersion = Build.VERSION_CODES.R;
        when(mMockCallsManager.isInEmergencyCall()).thenReturn(false);
        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);
        when(mMockCall.isIncoming()).thenReturn(false);
        when(mMockCall.getAssociatedUser()).thenReturn(mUserHandle);
        when(mMockCall.isExternalCall()).thenReturn(false);
        when(mMockCall.getTargetPhoneAccount()).thenReturn(PA_HANDLE);
        when(mMockCallsManager.getCurrentUserHandle()).thenReturn(mUserHandle);
        when(mMockCall.getAnalytics()).thenReturn(mCallInfo);
        when(mMockContext.bindServiceAsUser(
                any(Intent.class), any(ServiceConnection.class), anyInt(), any(UserHandle.class)))
                .thenReturn(true);
        when(mMockContext.getApplicationInfo()).thenReturn(applicationInfo);
        when(mDefaultDialerCache.getDefaultDialerApplication(CURRENT_USER_ID)).thenReturn(DEF_PKG);

        setupMockPackageManager(true /* default */, true /* system */, false /* external calls */);
        mInCallController.bindToServices(mMockCall, false);

        ArgumentCaptor<Intent> bindIntentCaptor = ArgumentCaptor.forClass(Intent.class);
        ArgumentCaptor<ServiceConnection> serviceConnectionCaptor =
                ArgumentCaptor.forClass(ServiceConnection.class);
        verify(mMockContext, times(1)).bindServiceAsUser(
                bindIntentCaptor.capture(),
                serviceConnectionCaptor.capture(),
                anyInt(),
                any(UserHandle.class));
        ServiceConnection serviceConnection = serviceConnectionCaptor.getValue();
        ComponentName defDialerComponentName = new ComponentName(DEF_PKG, DEF_CLASS);
        ComponentName sysDialerComponentName = new ComponentName(SYS_PKG, SYS_CLASS);

        IBinder mockBinder = mock(IBinder.class);
        IInCallService mockInCallService = mock(IInCallService.class);
        when(mockBinder.queryLocalInterface(anyString())).thenReturn(mockInCallService);

        serviceConnection.onServiceConnected(defDialerComponentName, mockBinder);
        // verify(mockInCallService).setInCallAdapter(any(IInCallAdapter.class));
        serviceConnection.onNullBinding(defDialerComponentName);

        verify(mNotificationManager).notifyAsUser(eq(NOTIFICATION_TAG),
                eq(IN_CALL_SERVICE_NOTIFICATION_ID), any(Notification.class),
                eq(mUserHandle));
        verify(mCallInfo).addInCallService(eq(defDialerComponentName.flattenToShortString()),
                anyInt(), anyLong(), eq(true));

        ArgumentCaptor<Intent> bindIntentCaptor2 = ArgumentCaptor.forClass(Intent.class);
        verify(mMockContext, times(2)).bindServiceAsUser(
                bindIntentCaptor2.capture(),
                any(ServiceConnection.class),
                eq(serviceBindingFlags),
                eq(mUserHandle));
        assertEquals(sysDialerComponentName, bindIntentCaptor2.getValue().getComponent());
    }

    @Test
    public void testBindToService_CarModeUI_Crash() throws Exception {
        setupMocks(false /* isExternalCall */);
        setupMockPackageManager(true /* default */, true /* system */, true /* external calls */);

        // Enable car mode
        when(mMockSystemStateHelper.isCarModeOrProjectionActive()).thenReturn(true);
        mInCallController.handleCarModeChange(UiModeManager.DEFAULT_PRIORITY, CAR_PKG, true);

        // Now bind; we should only bind to one app.
        mInCallController.bindToServices(mMockCall, false);

        // Bind InCallServices
        ArgumentCaptor<Intent> bindIntentCaptor = ArgumentCaptor.forClass(Intent.class);
        ArgumentCaptor<ServiceConnection> serviceConnectionCaptor =
                ArgumentCaptor.forClass(ServiceConnection.class);
        verify(mMockContext, times(1)).bindServiceAsUser(
                bindIntentCaptor.capture(),
                serviceConnectionCaptor.capture(),
                eq(serviceBindingFlags),
                eq(mUserHandle));

        // Verify bind car mode ui
        assertEquals(1, bindIntentCaptor.getAllValues().size());
        verifyBinding(bindIntentCaptor, 0, CAR_PKG, CAR_CLASS);

        // Emulate a crash in the CarModeUI
        ServiceConnection serviceConnection = serviceConnectionCaptor.getValue();
        serviceConnection.onServiceDisconnected(bindIntentCaptor.getValue().getComponent());

        ArgumentCaptor<Intent> bindIntentCaptor2 = ArgumentCaptor.forClass(Intent.class);
        verify(mMockContext, times(2)).bindServiceAsUser(
                bindIntentCaptor2.capture(),
                any(ServiceConnection.class),
                eq(serviceBindingFlags),
                eq(mUserHandle));

        verifyBinding(bindIntentCaptor2, 1, CAR_PKG, CAR_CLASS);
    }

    /**
     * This test verifies the behavior of Telecom when the system dialer crashes on binding and must
     * be restarted.  Specifically, it ensures when the system dialer crashes we revoke the runtime
     * location permission, and when it restarts we re-grant the permission.
     * @throws Exception
     */
    @MediumTest
    @Test
    public void testBindToLateConnectionNonUiIcs() throws Exception {
        Bundle callExtras = new Bundle();
        callExtras.putBoolean("whatever", true);

        // Make a basic call and bind to the default dialer.
        when(mMockCallsManager.getCurrentUserHandle()).thenReturn(mUserHandle);
        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);
        when(mMockCallsManager.isInEmergencyCall()).thenReturn(true);
        when(mMockCall.isEmergencyCall()).thenReturn(true);
        when(mMockContext.getSystemService(eq(UserManager.class)))
                .thenReturn(mMockUserManager);
        when(mMockCurrentUserManager.isQuietModeEnabled(any(UserHandle.class)))
                .thenReturn(false);
        when(mMockCall.isIncoming()).thenReturn(false);
        when(mMockCall.getTargetPhoneAccount()).thenReturn(PA_HANDLE);
        when(mMockCall.getIntentExtras()).thenReturn(callExtras);
        when(mMockCall.isExternalCall()).thenReturn(false);
        when(mMockCall.getAssociatedUser()).thenReturn(mUserHandle);
        when(mDefaultDialerCache.getDefaultDialerApplication(CURRENT_USER_ID))
                .thenReturn(DEF_PKG);
        ArgumentCaptor<ServiceConnection> serviceConnectionCaptor =
                ArgumentCaptor.forClass(ServiceConnection.class);
        when(mMockContext.bindServiceAsUser(any(Intent.class), serviceConnectionCaptor.capture(),
                eq(serviceBindingFlags),
                eq(mUserHandle))).thenReturn(true);
        when(mTimeoutsAdapter.getEmergencyCallbackWindowMillis(any(ContentResolver.class)))
                .thenReturn(300_000L);

        // Setup package manager; there is a dialer and disable non-ui ICS
        when(mMockPackageManager.queryIntentServicesAsUser(
                any(Intent.class), anyInt(), anyInt())).thenReturn(
                Arrays.asList(
                        getDefResolveInfo(false /* externalCalls */, false /* selfMgd */),
                        getNonUiResolveinfo(false /* selfManaged */,
                                false /* isEnabled */)
                )
        );
        when(mMockPackageManager
                .getComponentEnabledSetting(new ComponentName(DEF_PKG, DEF_CLASS)))
                .thenReturn(PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
        when(mMockPackageManager
                .getComponentEnabledSetting(new ComponentName(NONUI_PKG, NONUI_CLASS)))
                .thenReturn(PackageManager.COMPONENT_ENABLED_STATE_DISABLED);

        mInCallController.addCall(mMockCall);
        mInCallController.bindToServices(mMockCall, false);

        // There will be 4 calls for the various types of ICS.
        verify(mMockPackageManager, times(4)).queryIntentServicesAsUser(
                any(Intent.class),
                eq(PackageManager.GET_META_DATA | PackageManager.MATCH_DISABLED_COMPONENTS),
                eq(CURRENT_USER_ID));

        // Verify bind to the dialer
        ArgumentCaptor<Intent> bindIntentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mMockContext, times(1)).bindServiceAsUser(
                bindIntentCaptor.capture(),
                any(ServiceConnection.class),
                eq(serviceBindingFlags),
                eq(mUserHandle));

        Intent bindIntent = bindIntentCaptor.getValue();
        assertEquals(InCallService.SERVICE_INTERFACE, bindIntent.getAction());
        assertEquals(SYS_PKG, bindIntent.getComponent().getPackageName());
        assertEquals(SYS_CLASS, bindIntent.getComponent().getClassName());

        // Setup mocks to enable nonui ICS
        when(mMockPackageManager.queryIntentServicesAsUser(
                any(Intent.class), anyInt(), anyInt())).thenReturn(
                        Arrays.asList(
                                getDefResolveInfo(false /* externalCalls */, false /* selfMgd */),
                                getNonUiResolveinfo(false /* selfManaged */,
                                        true /* isEnabled */)
                        )
        );
        when(mMockPackageManager
                .getComponentEnabledSetting(new ComponentName(NONUI_PKG, NONUI_CLASS)))
                .thenReturn(PackageManager.COMPONENT_ENABLED_STATE_ENABLED);

        // Emulate a late enable of the non-ui ICS
        Intent packageUpdated = new Intent(Intent.ACTION_PACKAGE_CHANGED);
        packageUpdated.setData(Uri.fromParts("package", NONUI_PKG, null));
        packageUpdated.putExtra(Intent.EXTRA_CHANGED_COMPONENT_NAME_LIST,
                new String[] {NONUI_CLASS});
        packageUpdated.putExtra(Intent.EXTRA_UID, NONUI_UID);
        mRegisteredReceiver.onReceive(mMockContext, packageUpdated);

        // Now, we expect to auto-rebind to the system dialer (verify 2 times since this is the
        // second binding).
        verify(mMockContext, times(2)).bindServiceAsUser(
                bindIntentCaptor.capture(),
                any(ServiceConnection.class),
                eq(serviceBindingFlags),
                eq(mUserHandle));

        // Unbind!
        mInCallController.unbindFromServices(UserHandle.of(CURRENT_USER_ID));

        // Make sure we unbound 2 times
        verify(mMockContext, times(2)).unbindService(any(ServiceConnection.class));
    }

    /**
     * Tests a case where InCallController DOES NOT bind to ANY InCallServices when the call is
     * first added, but then one becomes available after the call starts.  This test was originally
     * added to reproduce a bug which would cause the call id mapper in the InCallController to not
     * track a newly added call unless something was bound when the call was first added.
     * @throws Exception
     */
    @MediumTest
    @Test
    public void testNoInitialBinding() throws Exception {
        Bundle callExtras = new Bundle();
        callExtras.putBoolean("whatever", true);

        // Make a basic call
        when(mMockCallsManager.getCurrentUserHandle()).thenReturn(mUserHandle);
        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);
        when(mMockCallsManager.isInEmergencyCall()).thenReturn(true);
        when(mMockCall.isEmergencyCall()).thenReturn(true);
        when(mMockContext.getSystemService(eq(UserManager.class)))
                .thenReturn(mMockUserManager);
        when(mMockCurrentUserManager.isQuietModeEnabled(any(UserHandle.class)))
                .thenReturn(false);
        when(mMockCall.isIncoming()).thenReturn(false);
        when(mMockCall.getAssociatedUser()).thenReturn(mUserHandle);
        when(mMockCall.getTargetPhoneAccount()).thenReturn(PA_HANDLE);
        when(mMockCall.getIntentExtras()).thenReturn(callExtras);
        when(mMockCall.isExternalCall()).thenReturn(false);
        when(mMockCall.isSelfManaged()).thenReturn(true);
        when(mMockCall.visibleToInCallService()).thenReturn(true);

        // Dialer doesn't handle these calls, but non-UI ICS does.
        when(mDefaultDialerCache.getDefaultDialerApplication(CURRENT_USER_ID))
                .thenReturn(DEF_PKG);
        ArgumentCaptor<ServiceConnection> serviceConnectionCaptor =
                ArgumentCaptor.forClass(ServiceConnection.class);
        when(mMockContext.bindServiceAsUser(any(Intent.class), serviceConnectionCaptor.capture(),
                eq(serviceBindingFlags),
                eq(mUserHandle))).thenReturn(true);
        when(mTimeoutsAdapter.getEmergencyCallbackWindowMillis(any(ContentResolver.class)))
                .thenReturn(300_000L);

        // Setup package manager; there is a dialer and disable non-ui ICS
        when(mMockPackageManager.queryIntentServicesAsUser(
                any(Intent.class), anyInt(), anyInt())).thenReturn(
                Arrays.asList(
                        getDefResolveInfo(false /* externalCalls */, false /* selfMgd */),
                        getNonUiResolveinfo(true /* selfManaged */,
                                false /* isEnabled */)
                )
        );
        when(mMockPackageManager
                .getComponentEnabledSetting(new ComponentName(DEF_PKG, DEF_CLASS)))
                .thenReturn(PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
        when(mMockPackageManager
                .getComponentEnabledSetting(new ComponentName(NONUI_PKG, NONUI_CLASS)))
                .thenReturn(PackageManager.COMPONENT_ENABLED_STATE_DISABLED);

        // Add the call.
        mInCallController.onCallAdded(mMockCall);

        // There will be 4 calls for the various types of ICS; this is normal.
        verify(mMockPackageManager, times(4)).queryIntentServicesAsUser(
                any(Intent.class),
                eq(PackageManager.GET_META_DATA | PackageManager.MATCH_DISABLED_COMPONENTS),
                eq(CURRENT_USER_ID));

        // Verify no bind at this point
        ArgumentCaptor<Intent> bindIntentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mMockContext, never()).bindServiceAsUser(
                bindIntentCaptor.capture(),
                any(ServiceConnection.class),
                eq(serviceBindingFlags),
                eq(mUserHandle));

        // Setup mocks to enable non-ui ICS
        when(mMockPackageManager.queryIntentServicesAsUser(
                any(Intent.class), anyInt(), anyInt())).thenReturn(
                Arrays.asList(
                        getDefResolveInfo(false /* externalCalls */, false /* selfMgd */),
                        getNonUiResolveinfo(true /* selfManaged */,
                                true /* isEnabled */)
                )
        );
        when(mMockPackageManager
                .getComponentEnabledSetting(new ComponentName(NONUI_PKG, NONUI_CLASS)))
                .thenReturn(PackageManager.COMPONENT_ENABLED_STATE_ENABLED);

        // Emulate a late enable of the non-ui ICS
        Intent packageUpdated = new Intent(Intent.ACTION_PACKAGE_CHANGED);
        packageUpdated.setData(Uri.fromParts("package", NONUI_PKG, null));
        packageUpdated.putExtra(Intent.EXTRA_CHANGED_COMPONENT_NAME_LIST,
                new String[] {NONUI_CLASS});
        packageUpdated.putExtra(Intent.EXTRA_UID, NONUI_UID);
        mRegisteredReceiver.onReceive(mMockContext, packageUpdated);

        // Make sure we bound to it.
        verify(mMockContext, times(1)).bindServiceAsUser(
                bindIntentCaptor.capture(),
                any(ServiceConnection.class),
                eq(serviceBindingFlags),
                eq(mUserHandle));
    }

    /**
     * Ensures that the {@link InCallController} will bind to an {@link InCallService} which
     * supports external calls.
     */
    @MediumTest
    @Test
    public void testBindToService_IncludeExternal() throws Exception {
        setupMocks(true /* isExternalCall */);
        setupMockPackageManager(true /* default */, true /* system */, true /* external calls */);
        mInCallController.bindToServices(mMockCall, false);

        // Query for the different InCallServices
        ArgumentCaptor<Intent> queryIntentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mMockPackageManager, times(4)).queryIntentServicesAsUser(
                queryIntentCaptor.capture(),
                eq(PackageManager.GET_META_DATA | PackageManager.MATCH_DISABLED_COMPONENTS),
                eq(CURRENT_USER_ID));

        // Verify call for default dialer InCallService
        assertEquals(DEF_PKG, queryIntentCaptor.getAllValues().get(0).getPackage());
        // Verify call for car-mode InCallService
        assertEquals(null, queryIntentCaptor.getAllValues().get(1).getPackage());
        // Verify call for non-UI InCallServices
        assertEquals(null, queryIntentCaptor.getAllValues().get(2).getPackage());

        ArgumentCaptor<Intent> bindIntentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mMockContext, times(1)).bindServiceAsUser(
                bindIntentCaptor.capture(),
                any(ServiceConnection.class),
                eq(serviceBindingFlags),
                eq(mUserHandle));

        Intent bindIntent = bindIntentCaptor.getValue();
        assertEquals(InCallService.SERVICE_INTERFACE, bindIntent.getAction());
        assertEquals(DEF_PKG, bindIntent.getComponent().getPackageName());
        assertEquals(DEF_CLASS, bindIntent.getComponent().getClassName());
    }

    /**
     * Make sure that if a call goes away before the in-call service finishes binding and another
     * call gets connected soon after, the new call will still be sent to the in-call service.
     */
    @MediumTest
    @Test
    public void testUnbindDueToCallDisconnect() throws Exception {
        when(mMockCallsManager.getCurrentUserHandle()).thenReturn(mUserHandle);
        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);
        when(mMockCallsManager.isInEmergencyCall()).thenReturn(false);
        when(mMockCall.isIncoming()).thenReturn(true);
        when(mMockCall.isExternalCall()).thenReturn(false);
        when(mMockCall.getTargetPhoneAccount()).thenReturn(PA_HANDLE);
        when(mDefaultDialerCache.getDefaultDialerApplication(CURRENT_USER_ID)).thenReturn(DEF_PKG);
        when(mMockContext.bindServiceAsUser(nullable(Intent.class),
                nullable(ServiceConnection.class), anyInt(), nullable(UserHandle.class)))
                .thenReturn(true);
        when(mTimeoutsAdapter.getCallRemoveUnbindInCallServicesDelay(
                nullable(ContentResolver.class))).thenReturn(500L);

        when(mMockCallsManager.getCalls()).thenReturn(Collections.singletonList(mMockCall));
        setupMockPackageManager(true /* default */, true /* system */, false /* external calls */);
        mInCallController.bindToServices(mMockCall, false);

        ArgumentCaptor<Intent> bindIntentCaptor = ArgumentCaptor.forClass(Intent.class);
        ArgumentCaptor<ServiceConnection> serviceConnectionCaptor =
                ArgumentCaptor.forClass(ServiceConnection.class);
        verify(mMockContext, times(1)).bindServiceAsUser(
                bindIntentCaptor.capture(),
                serviceConnectionCaptor.capture(),
                eq(serviceBindingFlags),
                eq(mUserHandle));

        // Pretend that the call has gone away.
        when(mMockCallsManager.getCalls()).thenReturn(Collections.emptyList());
        mInCallController.onCallRemoved(mMockCall);

        // Start the connection, make sure we don't unbind, and make sure that we don't send
        // anything to the in-call service yet.
        ServiceConnection serviceConnection = serviceConnectionCaptor.getValue();
        ComponentName defDialerComponentName = new ComponentName(DEF_PKG, DEF_CLASS);
        IBinder mockBinder = mock(IBinder.class);
        IInCallService mockInCallService = mock(IInCallService.class);
        when(mockBinder.queryLocalInterface(anyString())).thenReturn(mockInCallService);

        serviceConnection.onServiceConnected(defDialerComponentName, mockBinder);
        verify(mockInCallService).setInCallAdapter(nullable(IInCallAdapter.class));
        verify(mMockContext, never()).unbindService(serviceConnection);
        verify(mockInCallService, never()).addCall(any(ParcelableCall.class));

        // Now, we add in the call again and make sure that it's sent to the InCallService.
        when(mMockCallsManager.getCalls()).thenReturn(Collections.singletonList(mMockCall));
        mInCallController.onCallAdded(mMockCall);
        verify(mockInCallService).addCall(any(ParcelableCall.class));
    }

    /**
     * Ensures that the {@link InCallController} will bind to an {@link InCallService} which
     * supports third party car mode ui calls
     */
    @MediumTest
    @Test
    public void testBindToService_CarModeUI() throws Exception {
        setupMocks(true /* isExternalCall */);
        setupMockPackageManager(true /* default */, true /* system */, true /* external calls */);

        // Enable car mode
        when(mMockSystemStateHelper.isCarModeOrProjectionActive()).thenReturn(true);
        mInCallController.handleCarModeChange(UiModeManager.DEFAULT_PRIORITY, CAR_PKG, true);

        // Now bind; we should only bind to one app.
        mInCallController.bindToServices(mMockCall, false);

        // Bind InCallServices
        ArgumentCaptor<Intent> bindIntentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mMockContext, times(1)).bindServiceAsUser(
                bindIntentCaptor.capture(),
                any(ServiceConnection.class),
                eq(serviceBindingFlags),
                eq(mUserHandle));
        // Verify bind car mode ui
        assertEquals(1, bindIntentCaptor.getAllValues().size());
        verifyBinding(bindIntentCaptor, 0, CAR_PKG, CAR_CLASS);
    }

    @MediumTest
    @Test
    public void testNoBindToInvalidService_CarModeUI() throws Exception {
        setupMocks(true /* isExternalCall */);
        setupMockPackageManager(true /* default */, true /* system */, true /* external calls */);
        mInCallController.bindToServices(mMockCall, false);

        when(mMockPackageManager.checkPermission(
                matches(Manifest.permission.CONTROL_INCALL_EXPERIENCE),
                matches(CAR_PKG))).thenReturn(PackageManager.PERMISSION_DENIED);
        // Enable car mode
        when(mMockSystemStateHelper.isCarModeOrProjectionActive()).thenReturn(true);

        // Register the fact that the invalid app entered car mode.
        mInCallController.handleCarModeChange(UiModeManager.DEFAULT_PRIORITY, CAR_PKG, true);

        // Bind InCallServices
        ArgumentCaptor<Intent> bindIntentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mMockContext, times(1)).bindServiceAsUser(
                bindIntentCaptor.capture(),
                any(ServiceConnection.class),
                eq(serviceBindingFlags),
                eq(mUserHandle));
        // Verify bind to default package, instead of the invalid car mode ui.
        assertEquals(1, bindIntentCaptor.getAllValues().size());
        verifyBinding(bindIntentCaptor, 0, DEF_PKG, DEF_CLASS);
    }

   /**
     * Ensures that the {@link InCallController} will bind to an {@link InCallService} which
     * supports third party app.
     */
    @MediumTest
    @Test
    public void testBindToService_ThirdPartyApp() throws Exception {
        final MockitoSession mockitoSession = ExtendedMockito.mockitoSession()
                .strictness(Strictness.WARN)
                .spyStatic(PermissionChecker.class)
                .startMocking();
        try {
            setupMocks(false /* isExternalCall */);
            setupMockPackageManager(false /* default */, false /* nonui */, true /* appop_nonui */,
                    true /* system */, false /* external calls */, false /* self mgd in default */,
                    false /* self mgd in car*/);

            ApplicationInfo applicationInfo = new ApplicationInfo();
            applicationInfo.targetSdkVersion = Build.VERSION_CODES.TIRAMISU;
            // set up mock call for ICSC#sendCrashedInCallServiceNotification(String)
            when(mMockContext.getApplicationInfo()).thenReturn(applicationInfo);

            // Enable Third Party Companion App
            ExtendedMockito.doReturn(PermissionChecker.PERMISSION_GRANTED).when(() ->
                    PermissionChecker.checkPermissionForDataDeliveryFromDataSource(
                            any(Context.class), eq(Manifest.permission.MANAGE_ONGOING_CALLS),
                            anyInt(), any(AttributionSource.class), nullable(String.class)));

            // Now bind; we should bind to the system dialer and app op non ui app.
            mInCallController.bindToServices(mMockCall, false);

            // Bind InCallServices
            ArgumentCaptor<Intent> bindIntentCaptor = ArgumentCaptor.forClass(Intent.class);
            verify(mMockContext, times(2)).bindServiceAsUser(
                    bindIntentCaptor.capture(),
                    any(ServiceConnection.class),
                    eq(serviceBindingFlags),
                    eq(mUserHandle));

            // Verify bind
            assertEquals(2, bindIntentCaptor.getAllValues().size());

            // Should have first bound to the system dialer.
            verifyBinding(bindIntentCaptor, 0, SYS_PKG, SYS_CLASS);

            // Should have next bound to the third party app op non ui app.
            verifyBinding(bindIntentCaptor, 1, APPOP_NONUI_PKG, APPOP_NONUI_CLASS);

        } finally {
            mockitoSession.finishMocking();
        }
    }

    /**
     * Ensures that the {@link InCallController} will bind to a non-ui service even if no ui service
     * is bound if the call is self managed.
     */
    @MediumTest
    @Test
    public void testBindToService_NonUiSelfManaged() throws Exception {
        setupMocks(false /* isExternalCall */, true);
        setupMockPackageManager(false /* default */, true/* nonui */, true /* appop_nonui */,
                true /* system */, false /* external calls */, false /* self mgd in default */,
                false /* self mgd in car*/, true /* self managed in nonui */);

        ApplicationInfo applicationInfo = new ApplicationInfo();
        applicationInfo.targetSdkVersion = Build.VERSION_CODES.TIRAMISU;
        when(mMockContext.getApplicationInfo()).thenReturn(applicationInfo);
        // Package doesn't have metadata of TelecomManager.METADATA_IN_CALL_SERVICE_UI should
        // not be the default dialer. This is to mock the default dialer is null in this case.
        when(mDefaultDialerCache.getDefaultDialerApplication(CURRENT_USER_ID)).thenReturn(null);

        // we should bind to only the non ui app.
        mInCallController.bindToServices(mMockCall, false);

        // Bind InCallServices
        ArgumentCaptor<Intent> bindIntentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mMockContext, times(1)).bindServiceAsUser(
                bindIntentCaptor.capture(),
                any(ServiceConnection.class),
                eq(serviceBindingFlags),
                eq(mUserHandle));

        // Verify bind
        assertEquals(1, bindIntentCaptor.getAllValues().size());

        // Should have bound to the third party non ui app.
        verifyBinding(bindIntentCaptor, 0, NONUI_PKG, NONUI_CLASS);

        // Verify notification is not sent by NotificationManager
        verify(mNotificationManager, times(0)).notifyAsUser(
                eq(InCallController.NOTIFICATION_TAG),
                eq(InCallController.IN_CALL_SERVICE_NOTIFICATION_ID), any(),
                eq(mUserHandle));
    }

    @MediumTest
    @Test
    public void testSanitizeContactName() throws Exception {
        setupMocks(false /* isExternalCall */);
        setupMockPackageManager(true /* default */, true /* system */, true /* external calls */);
        when(mMockPackageManager.checkPermission(
                matches(Manifest.permission.READ_CONTACTS),
                matches(DEF_PKG))).thenReturn(PackageManager.PERMISSION_DENIED);
        when(mMockCall.getName()).thenReturn("evil");

        mInCallController.bindToServices(mMockCall, false);

        // Bind InCallServices
        ArgumentCaptor<Intent> bindIntentCaptor = ArgumentCaptor.forClass(Intent.class);
        ArgumentCaptor<ServiceConnection> serviceConnectionCaptor =
                ArgumentCaptor.forClass(ServiceConnection.class);
        verify(mMockContext, times(1)).bindServiceAsUser(
                bindIntentCaptor.capture(),
                serviceConnectionCaptor.capture(),
                eq(serviceBindingFlags),
                eq(mUserHandle));
        assertEquals(1, bindIntentCaptor.getAllValues().size());
        verifyBinding(bindIntentCaptor, 0, DEF_PKG, DEF_CLASS);

        IInCallService.Stub mockInCallServiceStub = mock(IInCallService.Stub.class);
        IInCallService mockInCallService = mock(IInCallService.class);
        when(mockInCallServiceStub.queryLocalInterface(anyString())).thenReturn(mockInCallService);
        serviceConnectionCaptor.getValue().onServiceConnected(new ComponentName(DEF_PKG, DEF_CLASS),
                mockInCallServiceStub);

        mInCallController.onCallAdded(mMockCall);
        ArgumentCaptor<ParcelableCall> parcelableCallCaptor =
                ArgumentCaptor.forClass(ParcelableCall.class);
        verify(mockInCallService).addCall(parcelableCallCaptor.capture());
        assertTrue(TextUtils.isEmpty(parcelableCallCaptor.getValue().getContactDisplayName()));
    }

    /**
     * Ensures that the {@link InCallController} will bind to a higher priority car mode service
     * when one becomes available.
     */
    @MediumTest
    @Test
    public void testRandomAppRemovalWhenNotInCarMode() throws Exception {
        setupMocks(true /* isExternalCall */);
        setupMockPackageManager(true /* default */, true /* system */, true /* external calls */);
        // Bind to default dialer.
        mInCallController.bindToServices(mMockCall, false);

        // Uninstall an unrelated app.
        mSystemStateListener.onPackageUninstalled("com.joe.stuff");

        // Bind InCallServices, just once; we should not re-bind to the same app.
        ArgumentCaptor<Intent> bindIntentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mMockContext).bindServiceAsUser(
                bindIntentCaptor.capture(),
                any(ServiceConnection.class),
                eq(serviceBindingFlags),
                eq(mUserHandle));
    }

    /**
     * Ensures that the {@link InCallController} will bind to a higher priority car mode service
     * when one becomes available.
     */
    @MediumTest
    @Test
    public void testCarmodeRebindHigherPriority() throws Exception {
        setupMocks(true /* isExternalCall */);
        setupMockPackageManager(true /* default */, true /* system */, true /* external calls */);
        // Bind to default dialer.
        mInCallController.bindToServices(mMockCall, false);

        // Enable car mode and enter car mode at default priority.
        when(mMockSystemStateHelper.isCarModeOrProjectionActive()).thenReturn(true);
        mInCallController.handleCarModeChange(UiModeManager.DEFAULT_PRIORITY, CAR_PKG, true);

        // And change to the second car mode app.
        mInCallController.handleCarModeChange(100, CAR2_PKG, true);

        // Exit car mode at higher priority.
        mInCallController.handleCarModeChange(100, CAR2_PKG, false);

        // Bind InCallServices
        ArgumentCaptor<Intent> bindIntentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mMockContext, times(4)).bindServiceAsUser(
                bindIntentCaptor.capture(),
                any(ServiceConnection.class),
                eq(serviceBindingFlags),
                eq(mUserHandle));
        // Verify bind car mode ui
        assertEquals(4, bindIntentCaptor.getAllValues().size());

        // Should have first bound to the default dialer.
        verifyBinding(bindIntentCaptor, 0, DEF_PKG, DEF_CLASS);

        // Should have next bound to the car mode app.
        verifyBinding(bindIntentCaptor, 1, CAR_PKG, CAR_CLASS);

        // Finally, should have bound to the higher priority car mode app
        verifyBinding(bindIntentCaptor, 2, CAR2_PKG, CAR2_CLASS);

        // Should have rebound to the car mode app.
        verifyBinding(bindIntentCaptor, 3, CAR_PKG, CAR_CLASS);
    }

    public void verifyBinding(ArgumentCaptor<Intent> bindIntentCaptor, int i, String carPkg,
            String carClass) {
        Intent bindIntent = bindIntentCaptor.getAllValues().get(i);
        assertEquals(InCallService.SERVICE_INTERFACE, bindIntent.getAction());
        assertEquals(carPkg, bindIntent.getComponent().getPackageName());
        assertEquals(carClass, bindIntent.getComponent().getClassName());
    }

    /**
     * Make sure the InCallController completes its binding future when the in call service
     * finishes binding.
     */
    @MediumTest
    @Test
    public void testBindingFuture() throws Exception {
        when(mMockCallsManager.getCurrentUserHandle()).thenReturn(mUserHandle);
        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);
        when(mMockCallsManager.isInEmergencyCall()).thenReturn(false);
        when(mMockCall.isIncoming()).thenReturn(false);
        when(mMockCall.getAssociatedUser()).thenReturn(mUserHandle);
        when(mMockCall.isExternalCall()).thenReturn(false);
        when(mMockCall.getTargetPhoneAccount()).thenReturn(PA_HANDLE);
        when(mDefaultDialerCache.getDefaultDialerApplication(CURRENT_USER_ID)).thenReturn(DEF_PKG);
        when(mMockContext.bindServiceAsUser(nullable(Intent.class),
                nullable(ServiceConnection.class), anyInt(), nullable(UserHandle.class)))
                .thenReturn(true);
        when(mTimeoutsAdapter.getCallRemoveUnbindInCallServicesDelay(
                nullable(ContentResolver.class))).thenReturn(500L);

        when(mMockCallsManager.getCalls()).thenReturn(Collections.singletonList(mMockCall));
        setupMockPackageManager(true /* default */, true /* nonui */, false /* appop_nonui */ ,
                true /* system */, false /* external calls */,
                false /* self mgd in default*/, false /* self mgd in car*/);
        mInCallController.bindToServices(mMockCall, false);

        ArgumentCaptor<Intent> bindIntentCaptor = ArgumentCaptor.forClass(Intent.class);
        ArgumentCaptor<ServiceConnection> serviceConnectionCaptor =
                ArgumentCaptor.forClass(ServiceConnection.class);
        verify(mMockContext, times(2)).bindServiceAsUser(
                bindIntentCaptor.capture(),
                serviceConnectionCaptor.capture(),
                eq(serviceBindingFlags),
                eq(mUserHandle));

        CompletableFuture<Boolean> bindTimeout = mInCallController.getBindingFuture();

        assertFalse(bindTimeout.isDone());

        // Start the connection, make sure we don't unbind, and make sure that we don't send
        // anything to the in-call service yet.
        List<ServiceConnection> serviceConnections = serviceConnectionCaptor.getAllValues();
        List<Intent> intents = bindIntentCaptor.getAllValues();

        // Find the non-ui service and have it connect first.
        int nonUiIdx = findFirstIndexMatching(intents,
                i -> NONUI_PKG.equals(i.getComponent().getPackageName()));
        if (nonUiIdx < 0) {
            fail("Did not bind to non-ui incall");
        }

        {
            ComponentName nonUiComponentName = new ComponentName(NONUI_PKG, NONUI_CLASS);
            IBinder mockBinder = mock(IBinder.class);
            IInCallService mockInCallService = mock(IInCallService.class);
            when(mockBinder.queryLocalInterface(anyString())).thenReturn(mockInCallService);
            serviceConnections.get(nonUiIdx).onServiceConnected(nonUiComponentName, mockBinder);

            // Make sure the non-ui binding didn't trigger the future.
            assertFalse(bindTimeout.isDone());
        }

        int defDialerIdx = findFirstIndexMatching(intents,
                i -> DEF_PKG.equals(i.getComponent().getPackageName()));
        if (defDialerIdx < 0) {
            fail("Did not bind to default dialer incall");
        }

        ComponentName defDialerComponentName = new ComponentName(DEF_PKG, DEF_CLASS);
        IBinder mockBinder = mock(IBinder.class);
        IInCallService mockInCallService = mock(IInCallService.class);
        when(mockBinder.queryLocalInterface(anyString())).thenReturn(mockInCallService);

        serviceConnections.get(defDialerIdx).onServiceConnected(defDialerComponentName, mockBinder);
        verify(mockInCallService).setInCallAdapter(nullable(IInCallAdapter.class));

        // Make sure that the future completed without timing out.
        assertTrue(bindTimeout.getNow(false));
    }

    /**
     * Verify that if we go from a dialer which doesn't support self managed calls to a car mode
     * dialer that does support them, we will bind.
     */
    @MediumTest
    @Test
    public void testBindToService_SelfManagedCarModeUI() throws Exception {
        setupMocks(true /* isExternalCall */, true /* isSelfManaged*/);
        setupMockPackageManager(true /* default */, true /* system */, true /* external calls */,
                false /* selfManagedInDefaultDialer */, true /* selfManagedInCarModeDialer */);

        // Bind; we should not bind to anything right now; the dialer does not support self
        // managed calls.
        mInCallController.bindToServices(mMockCall, false);

        // Bind InCallServices; make sure no binding took place.  InCallController handles not
        // binding initially, but the rebind (see next test case) will always happen.
        verify(mMockContext, never()).bindServiceAsUser(
                any(Intent.class),
                any(ServiceConnection.class),
                eq(serviceBindingFlags),
                eq(mUserHandle));

        // Now switch to car mode.
        // Enable car mode and enter car mode at default priority.
        when(mMockSystemStateHelper.isCarModeOrProjectionActive()).thenReturn(true);
        mInCallController.handleCarModeChange(UiModeManager.DEFAULT_PRIORITY, CAR_PKG, true);

        ArgumentCaptor<Intent> bindIntentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mMockContext, times(1)).bindServiceAsUser(
                bindIntentCaptor.capture(),
                any(ServiceConnection.class),
                eq(serviceBindingFlags),
                eq(mUserHandle));
        // Verify bind car mode ui
        assertEquals(1, bindIntentCaptor.getAllValues().size());
        verifyBinding(bindIntentCaptor, 0, CAR_PKG, CAR_CLASS);
    }

    /**
     * Verify that if we go from a dialer which doesn't support self managed calls to a car mode
     * dialer that does not support them, the calls are not sent to the call mode UI.
     */
    @MediumTest
    @Test
    public void testBindToService_SelfManagedNoCarModeUI() throws Exception {
        setupMocks(true /* isExternalCall */, true /* isSelfManaged*/);
        setupMockPackageManager(true /* default */, true /* system */, true /* external calls */,
                false /* selfManagedInDefaultDialer */, false /* selfManagedInCarModeDialer */);

        // Bind; we should not bind to anything right now; the dialer does not support self
        // managed calls.
        mInCallController.bindToServices(mMockCall, false);

        // Bind InCallServices; make sure no binding took place.
        verify(mMockContext, never()).bindServiceAsUser(
                any(Intent.class),
                any(ServiceConnection.class),
                eq(serviceBindingFlags),
                eq(mUserHandle));

        // Now switch to car mode.
        // Enable car mode and enter car mode at default priority.
        when(mMockSystemStateHelper.isCarModeOrProjectionActive()).thenReturn(true);
        mInCallController.handleCarModeChange(UiModeManager.DEFAULT_PRIORITY, CAR_PKG, true);

        // We currently will bind to the car-mode InCallService even if there are no calls available
        // for it.  Its not perfect, but it reflects the fact that the InCallController isn't
        // sophisticated enough to realize until its already bound whether there are in fact calls
        // which will be sent to it.
        ArgumentCaptor<ServiceConnection> serviceConnectionCaptor =
                ArgumentCaptor.forClass(ServiceConnection.class);
        verify(mMockContext, times(1)).bindServiceAsUser(
                any(Intent.class),
                serviceConnectionCaptor.capture(),
                eq(serviceBindingFlags),
                eq(mUserHandle));

        ServiceConnection serviceConnection = serviceConnectionCaptor.getValue();
        ComponentName defDialerComponentName = new ComponentName(DEF_PKG, DEF_CLASS);
        IBinder mockBinder = mock(IBinder.class);
        IInCallService mockInCallService = mock(IInCallService.class);
        when(mockBinder.queryLocalInterface(anyString())).thenReturn(mockInCallService);

        // Emulate successful connection.
        serviceConnection.onServiceConnected(defDialerComponentName, mockBinder);
        verify(mockInCallService).setInCallAdapter(any(IInCallAdapter.class));

        // We should not have gotten informed about any calls
        verify(mockInCallService, never()).addCall(any(ParcelableCall.class));
    }

    @Test
    public void testSanitizeDndExtraFromParcelableCall() throws Exception {
        setupMocks(false /* isExternalCall */);
        setupMockPackageManager(true /* default */, true /* system */, true /* external calls */);
        when(mMockPackageManager.checkPermission(
                matches(Manifest.permission.READ_CONTACTS),
                matches(DEF_PKG))).thenReturn(PackageManager.PERMISSION_DENIED);

        when(mMockCall.getExtras()).thenReturn(null);
        ParcelableCall parcelableCallNullExtras = Mockito.spy(
                ParcelableCallUtils.toParcelableCall(mMockCall,
                        false /* includevideoProvider */,
                        null /* phoneAccountRegistrar */,
                        false /* supportsExternalCalls */,
                        false /* includeRttCall */,
                        false /* isForSystemDialer */));

        when(parcelableCallNullExtras.getExtras()).thenReturn(null);
        assertNull(parcelableCallNullExtras.getExtras());
        when(mInCallServiceInfo.getComponentName())
                .thenReturn(new ComponentName(DEF_PKG, DEF_CLASS));
        // ensure sanitizeParcelableCallForService does not hit a NPE when Null extras are provided
        mInCallController.sanitizeParcelableCallForService(mInCallServiceInfo,
                parcelableCallNullExtras);


        Bundle extras = new Bundle();
        extras.putBoolean(android.telecom.Call.EXTRA_IS_SUPPRESSED_BY_DO_NOT_DISTURB, true);
        when(mMockCall.getExtras()).thenReturn(extras);

        ParcelableCall parcelableCallWithExtras = ParcelableCallUtils.toParcelableCall(mMockCall,
                false /* includevideoProvider */,
                null /* phoneAccountRegistrar */,
                false /* supportsExternalCalls */,
                false /* includeRttCall */,
                false /* isForSystemDialer */);

        // ensure sanitizeParcelableCallForService sanitizes the
        // EXTRA_IS_SUPPRESSED_BY_DO_NOT_DISTURB from a ParcelableCall
        // w/o  Manifest.permission.READ_CONTACTS
        ParcelableCall sanitizedCall =
                mInCallController.sanitizeParcelableCallForService(mInCallServiceInfo,
                        parcelableCallWithExtras);

        // sanitized call should not have the extra
        assertFalse(sanitizedCall.getExtras().containsKey(
                android.telecom.Call.EXTRA_IS_SUPPRESSED_BY_DO_NOT_DISTURB));

        // root ParcelableCall should still have the extra
        assertTrue(parcelableCallWithExtras.getExtras().containsKey(
                android.telecom.Call.EXTRA_IS_SUPPRESSED_BY_DO_NOT_DISTURB));
    }

    @Test
    public void testSecondaryUserCallBindToCurrentUser() throws Exception {
        setupMocks(true /* isExternalCall */);
        setupMockPackageManager(true /* default */, true /* system */, false /* external calls */);
        // Force the difference between the phone account user and current user. This is supposed to
        // simulate a secondary user placing a call over an unassociated sim.
        assertFalse(mUserHandle.equals(UserHandle.USER_CURRENT));
        when(mMockCurrentUserManager.isManagedProfile()).thenReturn(false);

        mInCallController.bindToServices(mMockCall, false);

        // Bind InCallService on UserHandle.CURRENT and not the user from the call (mUserHandle)
        ArgumentCaptor<Intent> bindIntentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mMockContext, times(1)).bindServiceAsUser(
                bindIntentCaptor.capture(),
                any(ServiceConnection.class),
                eq(serviceBindingFlags),
                eq(UserHandle.CURRENT));
    }

    @Test
    public void testGetUserFromCall_TargetPhoneAccountNotSet() throws Exception {
        setupMocks(false /* isExternalCall */);
        setupMockPackageManager(true /* default */, true /* system */, false /* external calls */);
        UserHandle testUser = new UserHandle(10);

        when(mMockCall.getTargetPhoneAccount()).thenReturn(null);
        when(mMockCall.getAssociatedUser()).thenReturn(testUser);

        // Bind to ICS. The mapping should've been inserted with the testUser as the key.
        mInCallController.bindToServices(mMockCall, false);
        assertTrue(mInCallController.getInCallServiceConnections().containsKey(testUser));

        // Set the target phone account. Simulates the flow when the user has chosen which sim to
        // place the call on.
        when(mMockCall.getTargetPhoneAccount()).thenReturn(PA_HANDLE);

        // Remove the call. This invokes getUserFromCall to remove the ICS mapping.
        when(mMockCallsManager.getCalls()).thenReturn(Collections.emptyList());
        mInCallController.onCallRemoved(mMockCall);
        waitForHandlerAction(new Handler(Looper.getMainLooper()), TEST_TIMEOUT);

        // Verify that the mapping was properly removed.
        assertNull(mInCallController.getInCallServiceConnections().get(testUser));
    }

    @Test
    public void testGetUserFromCall_IncomingCall() throws Exception {
        setupMocks(false /* isExternalCall */);
        setupMockPackageManager(true /* default */, true /* system */, false /* external calls */);
        // Explicitly test on a different user to avoid interference with current user.
        UserHandle testUser = new UserHandle(10);

        // Set user handle in target phone account to test user
        when(mMockCall.getAssociatedUser()).thenReturn(testUser);
        when(mMockCall.isIncoming()).thenReturn(true);

        // Bind to ICS. The mapping should've been inserted with the testUser as the key.
        mInCallController.bindToServices(mMockCall, false);
        assertTrue(mInCallController.getInCallServiceConnections().containsKey(testUser));

        // Remove the call. This invokes getUserFromCall to remove the ICS mapping.
        when(mMockCallsManager.getCalls()).thenReturn(Collections.emptyList());
        mInCallController.onCallRemoved(mMockCall);
        waitForHandlerAction(new Handler(Looper.getMainLooper()), TEST_TIMEOUT);

        // Verify that the mapping was properly removed.
        assertNull(mInCallController.getInCallServiceConnections().get(testUser));
    }

    @Test
    public void testRemoveAllServiceConnections_MultiUser() throws Exception {
        when(mFeatureFlags.associatedUserRefactorForWorkProfile()).thenReturn(true);
        setupMocks(false /* isExternalCall */);
        setupMockPackageManager(true /* default */, true /* system */, false /* external calls */);
        UserHandle workUser = new UserHandle(12);
        when(mMockCurrentUserManager.isManagedProfile()).thenReturn(false);
        when(mMockCall.getAssociatedUser()).thenReturn(workUser);
        setupFakeSystemCall(mMockSystemCall1, 1);
        setupFakeSystemCall(mMockSystemCall2, 2);

        // Add "work" call to service. The mapping should've been inserted
        // with the workUser as the key.
        mInCallController.onCallAdded(mMockCall);
        // Add system call to service. The mapping should've been
        // inserted with the system user as the key.
        mInCallController.onCallAdded(mMockSystemCall1);

        ArgumentCaptor<Intent> bindIntentCaptor = ArgumentCaptor.forClass(Intent.class);
        // Make sure we bound to the system call as well as the work call.
        verify(mMockContext, times(2)).bindServiceAsUser(
                bindIntentCaptor.capture(),
                any(ServiceConnection.class),
                eq(serviceBindingFlags),
                eq(UserHandle.CURRENT));
        assertTrue(mInCallController.getInCallServiceConnections().containsKey(workUser));
        assertTrue(mInCallController.getInCallServiceConnections().containsKey(UserHandle.SYSTEM));

        // Remove the work call. This leverages getUserFromCall to remove the ICS mapping.
        when(mMockCallsManager.getCalls()).thenReturn(Collections.singletonList(mMockSystemCall1));
        mInCallController.onCallRemoved(mMockCall);
        waitForHandlerAction(new Handler(Looper.getMainLooper()), TEST_TIMEOUT);
        // Verify that the mapping was properly removed.
        assertNull(mInCallController.getInCallServiceConnections().get(workUser));
        // Verify mapping for system user is still present.
        assertNotNull(mInCallController.getInCallServiceConnections().get(UserHandle.SYSTEM));

        // Add another system call
        mInCallController.onCallAdded(mMockSystemCall2);
        when(mMockCallsManager.getCalls()).thenReturn(Collections.singletonList(mMockSystemCall2));
        // Remove first system call and verify that mapping is present
        mInCallController.onCallRemoved(mMockSystemCall1);
        waitForHandlerAction(new Handler(Looper.getMainLooper()), TEST_TIMEOUT);
        // Verify mapping for system user is still present.
        assertNotNull(mInCallController.getInCallServiceConnections().get(UserHandle.SYSTEM));
        // Remove last system call and verify that connection isn't present in ICS mapping.
        when(mMockCallsManager.getCalls()).thenReturn(Collections.emptyList());
        mInCallController.onCallRemoved(mMockSystemCall2);
        waitForHandlerAction(new Handler(Looper.getMainLooper()), TEST_TIMEOUT);
        assertNull(mInCallController.getInCallServiceConnections().get(UserHandle.SYSTEM));
    }

    private void setupFakeSystemCall(@Mock Call call, int id) {
        when(call.getAssociatedUser()).thenReturn(UserHandle.SYSTEM);
        when(call.getTargetPhoneAccount()).thenReturn(PA_HANDLE);
        when(call.getAnalytics()).thenReturn(new Analytics.CallInfo());
        when(call.getId()).thenReturn("TC@" + id);
    }

    private void setupMocksForProfileTest() {
        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);
        when(mMockCallsManager.isInEmergencyCall()).thenReturn(false);
        when(mMockChildUserCall.isIncoming()).thenReturn(false);
        when(mMockChildUserCall.getTargetPhoneAccount()).thenReturn(PA_HANDLE);
        when(mDefaultDialerCache.getDefaultDialerApplication(CURRENT_USER_ID)).thenReturn(DEF_PKG);
        when(mMockContext.bindServiceAsUser(any(Intent.class), any(ServiceConnection.class),
                anyInt(), any())).thenReturn(true);
        when(mMockChildUserCall.isExternalCall()).thenReturn(false);
        when(mMockChildUserCall.isSelfManaged()).thenReturn(true);
        when(mMockChildUserCall.visibleToInCallService()).thenReturn(true);

        //Setup up parent and child/work profile relation
        when(mMockChildUserCall.getAssociatedUser()).thenReturn(mChildUserHandle);
        when(mMockCallsManager.getCurrentUserHandle()).thenReturn(mParentUserHandle);
        when(mMockUserManager.getProfileParent(mChildUserHandle)).thenReturn(mParentUserHandle);
        when(mFeatureFlags.profileUserSupport()).thenReturn(true);
    }

    @Test
    public void testProfileCallQueriesIcsUsingParentUserToo() throws Exception {
        setupMocksForProfileTest();
        setupMockPackageManager(true /* default */, true /* system */, false /* external calls */);
        setupMockPackageManager(true /* default */,
                true /*useNonUiInCalls*/, true /*useAppOpNonUiInCalls*/,
                true /*useSystemDialer*/, false /*includeExternalCalls*/,
                true /*includeSelfManagedCallsInDefaultDialer*/,
                true /*includeSelfManagedCallsInCarModeDialer*/,
                true /*includeSelfManagedCallsInNonUi*/);

        //pass in call by child/profile user
        mInCallController.bindToServices(mMockChildUserCall, false);
        // Verify that queryIntentServicesAsUser is also called with parent handle
        // Query for the different InCallServices
        ArgumentCaptor<Integer> userIdCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Intent> queryIntentCaptor = ArgumentCaptor.forClass(Intent.class);
        ArgumentCaptor<Integer> flagCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mMockPackageManager, times(6)).queryIntentServicesAsUser(
                queryIntentCaptor.capture(), flagCaptor.capture(), userIdCaptor.capture());
        List<Integer> userIds = userIdCaptor.getAllValues();

        //check if queryIntentServices was called with child user handle
        assertTrue("no query parent user handle",
                userIds.contains(mChildUserHandle.getIdentifier()));
        //check if queryIntentServices was also called with parent user handle
        assertTrue("no query parent user handle",
                userIds.contains(mParentUserHandle.getIdentifier()));
    }

    @Test
    public void testSeparatelyBluetoothService() {
        setupMocks(false /* isExternalCall */);
        setupMockPackageManager(true /* default */, true /* system */, false /* external calls */);
        Intent expectedIntent = new Intent(InCallService.SERVICE_INTERFACE);
        expectedIntent.setPackage(mDefaultDialerCache.getBTInCallServicePackage());
        LinkedList<ResolveInfo> resolveInfo = new LinkedList<ResolveInfo>();
        resolveInfo.add(getBluetoothResolveinfo());
        when(mFeatureFlags.separatelyBindToBtIncallService()).thenReturn(true);
        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);
        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            LinkedList<ResolveInfo> resolveInfo1 = new LinkedList<ResolveInfo>();
            Intent intent = (Intent) args[0];
            if (intent.getAction().equals(InCallService.SERVICE_INTERFACE)) {
                resolveInfo1.add(getBluetoothResolveinfo());
            }
            return resolveInfo1;
        }).when(mMockPackageManager).queryIntentServicesAsUser(any(Intent.class), anyInt(),
                anyInt());

        mInCallController.bindToBTService(mMockCall);

        ArgumentCaptor<Intent> captor = ArgumentCaptor.forClass(Intent.class);
        verify(mMockContext).bindServiceAsUser(captor.capture(), any(ServiceConnection.class),
                anyInt(), any(UserHandle.class));
    }

    private void setupMocks(boolean isExternalCall) {
        setupMocks(isExternalCall, false /* isSelfManagedCall */);
    }

    private void setupMocks(boolean isExternalCall, boolean isSelfManagedCall) {
        when(mMockCallsManager.getCurrentUserHandle()).thenReturn(mUserHandle);
        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);
        when(mMockCallsManager.isInEmergencyCall()).thenReturn(false);
        when(mMockCall.isIncoming()).thenReturn(false);
        when(mMockCall.getAssociatedUser()).thenReturn(mUserHandle);
        when(mMockCall.getTargetPhoneAccount()).thenReturn(PA_HANDLE);
        when(mDefaultDialerCache.getDefaultDialerApplication(CURRENT_USER_ID)).thenReturn(DEF_PKG);
        when(mMockContext.bindServiceAsUser(any(Intent.class), any(ServiceConnection.class),
                anyInt(), any(UserHandle.class))).thenReturn(true);
        when(mMockCall.isExternalCall()).thenReturn(isExternalCall);
        when(mMockCall.isSelfManaged()).thenReturn(isSelfManagedCall);
        when(mMockCall.visibleToInCallService()).thenReturn(isSelfManagedCall);
    }

    private ResolveInfo getDefResolveInfo(final boolean includeExternalCalls,
            final boolean includeSelfManagedCalls) {
        return new ResolveInfo() {{
            serviceInfo = new ServiceInfo();
            serviceInfo.packageName = DEF_PKG;
            serviceInfo.name = DEF_CLASS;
            serviceInfo.applicationInfo = new ApplicationInfo();
            serviceInfo.applicationInfo.uid = DEF_UID;
            serviceInfo.permission = Manifest.permission.BIND_INCALL_SERVICE;
            serviceInfo.enabled = true;
            serviceInfo.metaData = new Bundle();
            serviceInfo.metaData.putBoolean(
                    TelecomManager.METADATA_IN_CALL_SERVICE_UI, true);
            if (includeExternalCalls) {
                serviceInfo.metaData.putBoolean(
                        TelecomManager.METADATA_INCLUDE_EXTERNAL_CALLS, true);
            }
            if (includeSelfManagedCalls) {
                serviceInfo.metaData.putBoolean(
                        TelecomManager.METADATA_INCLUDE_SELF_MANAGED_CALLS, true);
            }
        }};
    }

    private ResolveInfo getCarModeResolveinfo(final String packageName, final String className,
            final boolean includeExternalCalls, final boolean includeSelfManagedCalls) {
        return new ResolveInfo() {{
            serviceInfo = new ServiceInfo();
            serviceInfo.packageName = packageName;
            serviceInfo.name = className;
            serviceInfo.applicationInfo = new ApplicationInfo();
            if (CAR_PKG.equals(packageName)) {
                serviceInfo.applicationInfo.uid = CAR_UID;
            } else {
                serviceInfo.applicationInfo.uid = CAR2_UID;
            }
            serviceInfo.permission = Manifest.permission.BIND_INCALL_SERVICE;
            serviceInfo.enabled = true;
            serviceInfo.metaData = new Bundle();
            serviceInfo.metaData.putBoolean(
                    TelecomManager.METADATA_IN_CALL_SERVICE_CAR_MODE_UI, true);
            if (includeExternalCalls) {
                serviceInfo.metaData.putBoolean(
                        TelecomManager.METADATA_INCLUDE_EXTERNAL_CALLS, true);
            }
            if (includeSelfManagedCalls) {
                serviceInfo.metaData.putBoolean(
                        TelecomManager.METADATA_INCLUDE_SELF_MANAGED_CALLS, true);
            }
        }};
    }

    private ResolveInfo getSysResolveinfo() {
        return new ResolveInfo() {{
            serviceInfo = new ServiceInfo();
            serviceInfo.packageName = SYS_PKG;
            serviceInfo.name = SYS_CLASS;
            serviceInfo.applicationInfo = new ApplicationInfo();
            serviceInfo.applicationInfo.uid = SYS_UID;
            serviceInfo.enabled = true;
            serviceInfo.permission = Manifest.permission.BIND_INCALL_SERVICE;
        }};
    }

    private ResolveInfo getCompanionResolveinfo() {
        return new ResolveInfo() {{
            serviceInfo = new ServiceInfo();
            serviceInfo.packageName = COMPANION_PKG;
            serviceInfo.name = COMPANION_CLASS;
            serviceInfo.applicationInfo = new ApplicationInfo();
            serviceInfo.applicationInfo.uid = COMPANION_UID;
            serviceInfo.enabled = true;
            serviceInfo.permission = Manifest.permission.BIND_INCALL_SERVICE;
        }};
    }

    private ResolveInfo getNonUiResolveinfo(boolean supportsSelfManaged, boolean isEnabled) {
        return new ResolveInfo() {{
            serviceInfo = new ServiceInfo();
            serviceInfo.packageName = NONUI_PKG;
            serviceInfo.name = NONUI_CLASS;
            serviceInfo.applicationInfo = new ApplicationInfo();
            serviceInfo.applicationInfo.uid = NONUI_UID;
            serviceInfo.enabled = isEnabled;
            serviceInfo.permission = Manifest.permission.BIND_INCALL_SERVICE;
            serviceInfo.metaData = new Bundle();
            if (supportsSelfManaged) {
                serviceInfo.metaData.putBoolean(
                        TelecomManager.METADATA_INCLUDE_SELF_MANAGED_CALLS, true);
            }
        }};
    }

    private ResolveInfo getAppOpNonUiResolveinfo() {
        return new ResolveInfo() {{
            serviceInfo = new ServiceInfo();
            serviceInfo.packageName = APPOP_NONUI_PKG;
            serviceInfo.name = APPOP_NONUI_CLASS;
            serviceInfo.applicationInfo = new ApplicationInfo();
            serviceInfo.applicationInfo.uid = APPOP_NONUI_UID;
            serviceInfo.enabled = true;
            serviceInfo.permission = Manifest.permission.BIND_INCALL_SERVICE;
        }};
    }

    private ResolveInfo getBluetoothResolveinfo() {
        return new ResolveInfo() {{
            serviceInfo = new ServiceInfo();
            serviceInfo.packageName = BT_PKG;
            serviceInfo.name = BT_CLS;
            serviceInfo.applicationInfo = new ApplicationInfo();
            serviceInfo.applicationInfo.uid = BT_UID;
            serviceInfo.enabled = true;
            serviceInfo.permission = Manifest.permission.BIND_INCALL_SERVICE;
        }};
    }

    private void setupMockPackageManager(final boolean useDefaultDialer,
            final boolean useSystemDialer, final boolean includeExternalCalls) {
        setupMockPackageManager(useDefaultDialer, false, false, useSystemDialer, includeExternalCalls,
                false /* self mgd */, false /* self mgd */);
    }

    private void setupMockPackageManager(final boolean useDefaultDialer,
            final boolean useSystemDialer, final boolean includeExternalCalls,
            final boolean includeSelfManagedCallsInDefaultDialer,
            final boolean includeSelfManagedCallsInCarModeDialer) {
        setupMockPackageManager(useDefaultDialer, false /* nonui */, false /* appop_nonui */,
                useSystemDialer, includeExternalCalls, includeSelfManagedCallsInDefaultDialer,
                includeSelfManagedCallsInCarModeDialer);
    }

    private void setupMockPackageManager(final boolean useDefaultDialer,
            final boolean useNonUiInCalls, final boolean useAppOpNonUiInCalls,
            final boolean useSystemDialer, final boolean includeExternalCalls,
            final boolean includeSelfManagedCallsInDefaultDialer,
            final boolean includeSelfManagedCallsInCarModeDialer) {
        setupMockPackageManager(useDefaultDialer, useNonUiInCalls/* nonui */,
                useAppOpNonUiInCalls/* appop_nonui */,
                useSystemDialer, includeExternalCalls, includeSelfManagedCallsInDefaultDialer,
                includeSelfManagedCallsInCarModeDialer, false);
    }

    private void setupMockPackageManager(final boolean useDefaultDialer,
            final boolean useNonUiInCalls, final boolean useAppOpNonUiInCalls,
            final boolean useSystemDialer, final boolean includeExternalCalls,
            final boolean includeSelfManagedCallsInDefaultDialer,
            final boolean includeSelfManagedCallsInCarModeDialer,
            final boolean includeSelfManagedCallsInNonUi) {
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                Intent intent = (Intent) args[0];
                String packageName = intent.getPackage();
                ComponentName componentName = intent.getComponent();
                if (componentName != null) {
                    packageName = componentName.getPackageName();
                }
                LinkedList<ResolveInfo> resolveInfo = new LinkedList<ResolveInfo>();
                if (!TextUtils.isEmpty(packageName)) {
                    if (packageName.equals(DEF_PKG) && useDefaultDialer) {
                        resolveInfo.add(getDefResolveInfo(includeExternalCalls,
                                includeSelfManagedCallsInDefaultDialer));
                    }

                    if (packageName.equals(SYS_PKG) && useSystemDialer) {
                        resolveInfo.add(getSysResolveinfo());
                    }

                    if (packageName.equals(COMPANION_PKG)) {
                        resolveInfo.add(getCompanionResolveinfo());
                    }

                    if (packageName.equals(CAR_PKG)) {
                        resolveInfo.add(getCarModeResolveinfo(CAR_PKG, CAR_CLASS,
                                includeExternalCalls, includeSelfManagedCallsInCarModeDialer));
                    }

                    if (packageName.equals(CAR2_PKG)) {
                        resolveInfo.add(getCarModeResolveinfo(CAR2_PKG, CAR2_CLASS,
                                includeExternalCalls, includeSelfManagedCallsInCarModeDialer));
                    }
                } else {
                    // InCallController uses a blank package name when querying for non-ui incalls
                    if (useNonUiInCalls) {
                        resolveInfo.add(getNonUiResolveinfo(includeSelfManagedCallsInNonUi, true));
                    }
                    // InCallController uses a blank package name when querying for App Op non-ui incalls
                    if (useAppOpNonUiInCalls) {
                        resolveInfo.add(getAppOpNonUiResolveinfo());
                    }
                }

                return resolveInfo;
            }
        }).when(mMockPackageManager).queryIntentServicesAsUser(
                any(Intent.class), anyInt(), anyInt());

        if (useDefaultDialer) {
            when(mMockPackageManager
                    .getComponentEnabledSetting(new ComponentName(DEF_PKG, DEF_CLASS)))
                    .thenReturn(PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
        }

        when(mMockPackageManager
                .getComponentEnabledSetting(new ComponentName(SYS_PKG, SYS_CLASS)))
                .thenReturn(PackageManager.COMPONENT_ENABLED_STATE_ENABLED);

        when(mMockPackageManager
                .getComponentEnabledSetting(new ComponentName(CAR_PKG, CAR_CLASS)))
                .thenReturn(PackageManager.COMPONENT_ENABLED_STATE_ENABLED);

        when(mMockPackageManager
                .getComponentEnabledSetting(new ComponentName(COMPANION_PKG, COMPANION_CLASS)))
                .thenReturn(PackageManager.COMPONENT_ENABLED_STATE_ENABLED);

        when(mMockPackageManager
                .getComponentEnabledSetting(new ComponentName(CAR2_PKG, CAR2_CLASS)))
                .thenReturn(PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
    }

    private void setupMockPackageManagerLocationPermission(final String pkg,
            final boolean granted) {
        when(mMockPackageManager.checkPermission(Manifest.permission.ACCESS_FINE_LOCATION, pkg))
                .thenReturn(granted
                        ? PackageManager.PERMISSION_GRANTED
                        : PackageManager.PERMISSION_DENIED);
  }

    private static AttributionSourceState matchesAttributionSourcePackage(
            @Nullable String packageName) {
        return argThat(new PackageNameArgumentMatcher(packageName));
    }

    private static class PackageNameArgumentMatcher implements
            ArgumentMatcher<AttributionSourceState> {
        @Nullable
        private final String mPackgeName;

        PackageNameArgumentMatcher(@Nullable String packageName) {
            mPackgeName = packageName;
        }

        @Override
        public boolean matches(@NonNull AttributionSourceState attributionSource) {
            return Objects.equals(mPackgeName, attributionSource.packageName);
        }
    }
}
