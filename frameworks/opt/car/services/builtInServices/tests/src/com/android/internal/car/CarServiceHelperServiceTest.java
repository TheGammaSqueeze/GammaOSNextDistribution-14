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
 * limitations under the License.
 */

package com.android.internal.car;

import static com.android.car.internal.common.CommonConstants.INVALID_PID;
import static com.android.car.internal.common.CommonConstants.USER_LIFECYCLE_EVENT_TYPE_POST_UNLOCKED;
import static com.android.car.internal.common.CommonConstants.USER_LIFECYCLE_EVENT_TYPE_STARTING;
import static com.android.car.internal.common.CommonConstants.USER_LIFECYCLE_EVENT_TYPE_STOPPED;
import static com.android.car.internal.common.CommonConstants.USER_LIFECYCLE_EVENT_TYPE_STOPPING;
import static com.android.car.internal.common.CommonConstants.USER_LIFECYCLE_EVENT_TYPE_SWITCHING;
import static com.android.car.internal.common.CommonConstants.USER_LIFECYCLE_EVENT_TYPE_UNLOCKING;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mock;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;
import static com.android.server.SystemService.UserCompletedEventType.newUserCompletedEventTypeForTest;

import static com.google.common.truth.Truth.assertWithMessage;

import android.annotation.UserIdInt;
import android.app.ActivityManager;
import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.car.watchdoglib.CarWatchdogDaemonHelper;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.ServiceDebugInfo;
import android.os.ServiceManager;
import android.os.UserHandle;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.server.LocalServices;
import com.android.server.SystemService;
import com.android.server.SystemService.TargetUser;
import com.android.server.SystemService.UserCompletedEventType;
import com.android.server.pm.UserManagerInternal;
import com.android.server.wm.CarLaunchParamsModifier;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

/**
 * This class contains unit tests for the {@link CarServiceHelperService}.
 */
@RunWith(AndroidJUnit4.class)
public class CarServiceHelperServiceTest extends AbstractExtendedMockitoTestCase {
    private static final String SAMPLE_AIDL_VHAL_INTERFACE_NAME =
            "android.hardware.automotive.vehicle.IVehicle/SampleVehicleHalService";

    private CarServiceHelperService mHelper;

    @Mock
    private Context mMockContext;
    @Mock
    private PackageManager mPackageManager;
    @Mock
    private CarLaunchParamsModifier mCarLaunchParamsModifier;
    @Mock
    private CarWatchdogDaemonHelper mCarWatchdogDaemonHelper;
    @Mock
    private IBinder mICarBinder;
    @Mock
    private CarServiceHelperServiceUpdatable mCarServiceHelperServiceUpdatable;

    @Mock
    private CarDevicePolicySafetyChecker mCarDevicePolicySafetyChecker;

    @Mock
    private UserManagerInternal mUserManagerInternal;

    @Mock
    private ActivityManager mActivityManager;

    public CarServiceHelperServiceTest() {
        super(CarServiceHelperService.TAG);
    }

    /**
     * Initialize objects and setup testing environment.
     */
    @Override
    protected void onSessionBuilder(CustomMockitoSessionBuilder session) {
        session
                .spyStatic(ServiceManager.class)
                .spyStatic(LocalServices.class);
    }

    @Before
    public void setTestFixtures() {
        mHelper = new CarServiceHelperService(
                mMockContext,
                mCarLaunchParamsModifier,
                mCarWatchdogDaemonHelper,
                mCarServiceHelperServiceUpdatable,
                mCarDevicePolicySafetyChecker);
        when(mMockContext.getPackageManager()).thenReturn(mPackageManager);
        when(mMockContext.getSystemService(ActivityManager.class)).thenReturn(mActivityManager);

        doReturn(mUserManagerInternal)
                .when(() -> LocalServices.getService(UserManagerInternal.class));
    }

    @Test
    public void testIsUserSupported_preCreatedUserIsNotSupported() throws Exception {
        expectWithMessage("isUserSupported")
            .that(mHelper.isUserSupported(newTargetUser(10, /* preCreated= */ true)))
            .isFalse();
    }

    @Test
    public void testIsUserSupported_nonPreCreatedUserIsSupported() throws Exception {
        expectWithMessage("isUserSupported").that(mHelper.isUserSupported(newTargetUser(11)))
            .isTrue();
    }

    @Test
    public void testOnUserStarting_notifiesICar() throws Exception {
        int userId = 10;

        mHelper.onUserStarting(newTargetUser(userId));

        verifyICarOnUserLifecycleEventCalled(USER_LIFECYCLE_EVENT_TYPE_STARTING, userId);
    }

    @Test
    public void testOnUserSwitching_notifiesICar() throws Exception {
        int currentUserId = 10;
        int targetUserId = 11;

        mHelper.onUserSwitching(newTargetUser(currentUserId),
                newTargetUser(targetUserId));

        verifyICarOnUserLifecycleEventCalled(USER_LIFECYCLE_EVENT_TYPE_SWITCHING,
                currentUserId, targetUserId);
    }

    @Test
    public void testOnUserUnlocking_notifiesICar() throws Exception {
        int userId = 10;

        mHelper.onUserUnlocking(newTargetUser(userId));

        verifyICarOnUserLifecycleEventCalled(USER_LIFECYCLE_EVENT_TYPE_UNLOCKING, userId);
    }

    @Test
    public void testOnUserStopping_notifiesICar() throws Exception {
        int userId = 10;

        mHelper.onUserStopping(newTargetUser(userId));

        verifyICarOnUserLifecycleEventCalled(USER_LIFECYCLE_EVENT_TYPE_STOPPING, userId);
    }

    @Test
    public void testOnUserStopped_notifiesICar() throws Exception {
        int userId = 10;

        mHelper.onUserStopped(newTargetUser(userId));

        verifyICarOnUserLifecycleEventCalled(USER_LIFECYCLE_EVENT_TYPE_STOPPED, userId);
    }

    @Test
    public void testOnBootPhase_thirdPartyCanStart_initBootUser() throws Exception {
        mHelper.onBootPhase(SystemService.PHASE_THIRD_PARTY_APPS_CAN_START);

        verifyInitBootUser();
    }

    @Test
    public void testOnUserCompletedEvent_notifiesPostUnlockedEvent() throws Exception {
        int userId = 10;

        mHelper.onUserCompletedEvent(newTargetUser(userId), newUserCompletedEventTypeForTest(
                UserCompletedEventType.EVENT_TYPE_USER_UNLOCKED));

        verifyICarOnUserLifecycleEventCalled(USER_LIFECYCLE_EVENT_TYPE_POST_UNLOCKED, userId);
    }

    @Test
    public void testGetMainDisplayAssignedToUser() throws Exception {
        when(mUserManagerInternal.getMainDisplayAssignedToUser(42)).thenReturn(108);

        assertWithMessage("getMainDisplayAssignedToUser(42)")
                .that(mHelper.getMainDisplayAssignedToUser(42)).isEqualTo(108);
    }

    @Test
    public void testGetUserAssignedToDisplay() throws Exception {
        when(mUserManagerInternal.getUserAssignedToDisplay(108)).thenReturn(42);

        assertWithMessage("getUserAssignedToDisplay(108)")
                .that(mHelper.getUserAssignedToDisplay(108)).isEqualTo(42);
    }

    @Test
    public void testStartUserInBackgroundVisibleOnDisplay() throws Exception {
        int userId = 100;
        int displayId = 2;

        mHelper.startUserInBackgroundVisibleOnDisplay(userId, displayId);

        verify(mActivityManager).startUserInBackgroundVisibleOnDisplay(userId, displayId);
    }

    @Test
    public void testFetchAidlVhalPid() throws Exception {
        int vhalPid = 5643;
        ServiceDebugInfo[] debugInfos = {
            newServiceDebugInfo(SAMPLE_AIDL_VHAL_INTERFACE_NAME, vhalPid),
            newServiceDebugInfo("some.service", 1234),
        };
        doReturn(debugInfos).when(() -> ServiceManager.getServiceDebugInfo());

        assertWithMessage("AIDL VHAL pid").that(mHelper.fetchAidlVhalPid()).isEqualTo(vhalPid);
    }

    @Test
    public void testFetchAidlVhalPid_missingAidlVhalService() throws Exception {
        ServiceDebugInfo[] debugInfos = {
            newServiceDebugInfo("random.service", 8535),
            newServiceDebugInfo("some.service", 1234),
        };
        doReturn(debugInfos).when(() -> ServiceManager.getServiceDebugInfo());

        assertWithMessage("AIDL VHAL pid").that(mHelper.fetchAidlVhalPid())
                .isEqualTo(INVALID_PID);
    }

    private TargetUser newTargetUser(int userId) {
        return newTargetUser(userId, /* preCreated= */ false);
    }

    private TargetUser newTargetUser(int userId, boolean preCreated) {
        TargetUser targetUser = mock(TargetUser.class);
        when(targetUser.getUserIdentifier()).thenReturn(userId);
        when(targetUser.getUserHandle()).thenReturn(UserHandle.of(userId));
        when(targetUser.isPreCreated()).thenReturn(preCreated);
        return targetUser;
    }

    enum InitialUserInfoAction {
        DEFAULT,
        DEFAULT_WITH_LOCALE,
        DO_NOT_REPLY,
        DELAYED_REPLY,
        NON_OK_RESULT_CODE,
        NULL_BUNDLE,
        SWITCH_OK,
        SWITCH_OK_WITH_LOCALE,
        SWITCH_MISSING_USER_ID
    }

    private void verifyICarOnUserLifecycleEventCalled(int eventType,
            @UserIdInt int fromId, @UserIdInt int toId) throws Exception {
        verify(mCarServiceHelperServiceUpdatable).sendUserLifecycleEvent(eventType,
                UserHandle.of(fromId), UserHandle.of(toId));
    }

    private void verifyICarOnUserLifecycleEventCalled(int eventType,
            @UserIdInt int userId) throws Exception {
        verify(mCarServiceHelperServiceUpdatable).sendUserLifecycleEvent(eventType,
                null, UserHandle.of(userId));
    }

    private void verifyInitBootUser() throws Exception {
        verify(mCarServiceHelperServiceUpdatable).initBootUser();
    }

    private ServiceDebugInfo newServiceDebugInfo(String name, int debugPid) {
        ServiceDebugInfo serviceDebugInfo = new ServiceDebugInfo();
        serviceDebugInfo.name = name;
        serviceDebugInfo.debugPid = debugPid;
        return serviceDebugInfo;
    }
}
