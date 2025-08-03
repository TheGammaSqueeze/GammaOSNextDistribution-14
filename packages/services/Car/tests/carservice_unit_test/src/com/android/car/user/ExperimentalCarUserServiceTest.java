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

package com.android.car.user;

import static android.car.test.mocks.AndroidMockitoHelper.mockAmSwitchUser;
import static android.car.test.mocks.AndroidMockitoHelper.mockUmCreateUser;
import static android.car.test.mocks.JavaMockitoHelper.getResult;

import static com.android.car.user.MockedUserHandleBuilder.expectAdminUserExists;
import static com.android.car.user.MockedUserHandleBuilder.expectDisabledUserExists;
import static com.android.car.user.MockedUserHandleBuilder.expectEphemeralUserExists;
import static com.android.car.user.MockedUserHandleBuilder.expectGuestUserExists;
import static com.android.car.user.MockedUserHandleBuilder.expectManagedProfileExists;
import static com.android.car.user.MockedUserHandleBuilder.expectRegularUserExists;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import android.annotation.NonNull;
import android.annotation.UserIdInt;
import android.car.CarOccupantZoneManager.OccupantTypeEnum;
import android.car.CarOccupantZoneManager.OccupantZoneInfo;
import android.car.builtin.app.ActivityManagerHelper;
import android.car.builtin.os.UserManagerHelper;
import android.car.user.UserCreationResult;
import android.car.user.UserSwitchResult;
import android.car.util.concurrent.AndroidFuture;
import android.hardware.automotive.vehicle.CreateUserStatus;
import android.hardware.automotive.vehicle.SwitchUserStatus;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.SparseArray;

import com.android.car.hal.HalCallback;
import com.android.car.internal.user.UserHelper;
import com.android.car.user.ExperimentalCarUserService.ZoneUserBindingHelper;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Unit tests for the {@link ExperimentalCarUserService}.
 */
public final class ExperimentalCarUserServiceTest extends BaseCarUserServiceTestCase {

    private ExperimentalCarUserService mExperimentalCarUserService;

    public ExperimentalCarUserServiceTest() {
        super(ExperimentalCarUserService.TAG);
    }

    @Before
    public void setUp() {
        mExperimentalCarUserService =
                new ExperimentalCarUserService(mMockContext, mCarUserService, mMockedUserManager,
                        mMockedUserHandleHelper);

        // TODO(b/172262561): refactor this call, which is not assigning the service to anything
        // (but without it some tests fail due to NPE).
        new FakeCarOccupantZoneService(mExperimentalCarUserService);
    }

    @Test
    public void testCreateAdminDriver_IfCurrentUserIsAdminUser() throws Exception {
        when(mMockedUserManager.isSystemUser()).thenReturn(true);
        mockUmCreateUser(mMockedUserManager, "testUser", UserManager.USER_TYPE_FULL_SECONDARY,
                UserManagerHelper.FLAG_ADMIN, UserHandle.of(100));
        mockHalCreateUser(HalCallback.STATUS_OK, CreateUserStatus.SUCCESS);

        AndroidFuture<UserCreationResult> future =
                mExperimentalCarUserService.createDriver("testUser", true);
        waitForHandlerThreadToFinish();

        UserCreationResult result = getCreateDriverResult(future);
        assertThat(result.getStatus()).isEqualTo(UserCreationResult.STATUS_SUCCESSFUL);
        assertThat(result.getUser().getIdentifier()).isEqualTo(100);
    }

    @Test
    public void testCreateAdminDriver_IfCurrentUserIsNotSystemUser() throws Exception {
        when(mMockedUserManager.isSystemUser()).thenReturn(false);
        AndroidFuture<UserCreationResult> future =
                mExperimentalCarUserService.createDriver("testUser", true);
        waitForHandlerThreadToFinish();
        assertThat(getCreateDriverResult(future).getStatus())
                .isEqualTo(UserCreationResult.STATUS_INVALID_REQUEST);
    }

    @Test
    public void testCreateNonAdminDriver() throws Exception {
        mockUmCreateUser(mMockedUserManager, "testUser", UserManager.USER_TYPE_FULL_SECONDARY,
                NO_USER_INFO_FLAGS, UserHandle.of(100));
        mockHalCreateUser(HalCallback.STATUS_OK, CreateUserStatus.SUCCESS);

        AndroidFuture<UserCreationResult> future =
                mExperimentalCarUserService.createDriver("testUser", false);
        waitForHandlerThreadToFinish();

        UserCreationResult result = getCreateDriverResult(future);
        assertThat(result.getStatus()).isEqualTo(UserCreationResult.STATUS_SUCCESSFUL);
        assertThat(result.getUser().getIdentifier()).isEqualTo(100);
    }

    @Test
    // TODO(b/213374587): remove UserInfo usages
    public void testCreatePassenger() {
        // assignDefaultIconForUser is not used for testing
        doReturn(null).when(() -> UserHelper.assignDefaultIcon(any(), any()));
        doNothing()
                .when(() -> UserHelper.setDefaultNonAdminRestrictions(any(), any(), anyBoolean()));
        int driverId = 90;
        int passengerId = 99;
        String userName = "testUser";
        UserHandle passenger = expectManagedProfileExists(mMockedUserHandleHelper, passengerId);

        mockCreateProfile(driverId, userName, passenger);

        expectRegularUserExists(mMockedUserHandleHelper, driverId);
        assertThat(mExperimentalCarUserService.createPassenger(userName, driverId))
                .isEqualTo(passenger);
    }

    @Test
    public void testCreatePassenger_IfMaximumProfileAlreadyCreated() {
        UserHandle driver = expectManagedProfileExists(mMockedUserHandleHelper, 90);
        String userName = "testUser";

        mockCreateProfile(driver.getIdentifier(), userName, null);

        assertThat(mExperimentalCarUserService.createPassenger(userName, driver.getIdentifier()))
                .isNull();
    }

    @Test
    public void testCreatePassenger_IfDriverIsGuest() {
        int driverId = 90;
        expectGuestUserExists(mMockedUserHandleHelper, driverId, /* isEphemeral= */ false);
        String userName = "testUser";
        assertThat(mExperimentalCarUserService.createPassenger(userName, driverId)).isNull();
    }

    @Test
    public void testSwitchDriver() throws Exception {
        mockExistingUsersAndCurrentUser(mAdminUser);
        int requestId = 42;
        mSwitchUserResponse.status = SwitchUserStatus.SUCCESS;
        mSwitchUserResponse.requestId = requestId;
        mockHalSwitch(mAdminUser.getIdentifier(), mRegularUser, mSwitchUserResponse);
        mockAmSwitchUser(mMockedActivityManager, mRegularUser, true);
        when(mMockedUserManager.hasUserRestriction(UserManager.DISALLOW_USER_SWITCH))
                .thenReturn(false);

        mExperimentalCarUserService.switchDriver(mRegularUserId, mUserSwitchFuture);

        assertThat(getUserSwitchResult(mRegularUserId).getStatus())
                .isEqualTo(UserSwitchResult.STATUS_SUCCESSFUL);
    }

    @Test
    public void testSwitchDriver_failUxRestrictions() throws Exception {
        mockExistingUsersAndCurrentUser(mAdminUser);
        mockGetUxRestrictions(/* restricted= */ true);
        initService();

        mExperimentalCarUserService.switchDriver(mRegularUserId, mUserSwitchFuture);

        assertThat(getUserSwitchResult(mRegularUserId).getStatus())
                .isEqualTo(UserSwitchResult.STATUS_UX_RESTRICTION_FAILURE);
        verifyNoUserSwitch();
        assertNoHalUserSwitch();
    }

    @Test
    public void testSwitchDriver_IfUserSwitchIsNotAllowed() throws Exception {
        when(mMockedUserManager.getUserSwitchability())
                .thenReturn(UserManager.SWITCHABILITY_STATUS_USER_SWITCH_DISALLOWED);

        mExperimentalCarUserService.switchDriver(mRegularUserId, mUserSwitchFuture);

        assertThat(getUserSwitchResult(mRegularUserId).getStatus())
                .isEqualTo(UserSwitchResult.STATUS_INVALID_REQUEST);
    }

    @Test
    public void testSwitchDriver_IfSwitchedToCurrentUser() throws Exception {
        mockExistingUsersAndCurrentUser(mAdminUser);
        when(mMockedUserManager.hasUserRestriction(UserManager.DISALLOW_USER_SWITCH))
                .thenReturn(false);

        mExperimentalCarUserService.switchDriver(mAdminUserId, mUserSwitchFuture);

        assertThat(getUserSwitchResult(mAdminUserId).getStatus())
                .isEqualTo(UserSwitchResult.STATUS_OK_USER_ALREADY_IN_FOREGROUND);
    }

    @Test
    public void testStartPassenger() throws RemoteException {
        int passenger1Id = 91;
        int passenger2Id = 92;
        int passenger3Id = 93;
        int zone1Id = 1;
        int zone2Id = 2;
        doReturn(true)
                .when(() -> ActivityManagerHelper.startUserInBackground(anyInt()));
        assertThat(mExperimentalCarUserService.startPassenger(passenger1Id, zone1Id)).isTrue();
        assertThat(mExperimentalCarUserService.startPassenger(passenger2Id, zone2Id)).isTrue();
        assertThat(mExperimentalCarUserService.startPassenger(passenger3Id, zone2Id)).isFalse();
    }

    @Test
    public void testStopPassenger() throws RemoteException {
        int user1Id = 101;
        int passenger1Id = 901;
        int passenger2Id = 902;
        int zoneId = 1;
        UserHandle user1 = expectRegularUserExists(mMockedUserHandleHelper, user1Id);
        UserHandle passenger1 = expectRegularUserExists(mMockedUserHandleHelper, passenger1Id);

        associateParentChild(user1, passenger1);
        mockGetCurrentUser(user1Id);
        doReturn(true)
                .when(() -> ActivityManagerHelper.startUserInBackground(anyInt()));
        assertThat(mExperimentalCarUserService.startPassenger(passenger1Id, zoneId)).isTrue();
        assertThat(mExperimentalCarUserService.stopPassenger(passenger1Id)).isTrue();
        // Test of stopping an already stopped passenger.
        assertThat(mExperimentalCarUserService.stopPassenger(passenger1Id)).isTrue();
        // Test of stopping a non-existing passenger.
        assertThat(mExperimentalCarUserService.stopPassenger(passenger2Id)).isFalse();
    }

    private List<UserHandle> prepareUserList() {
        List<UserHandle> users = new ArrayList<>(Arrays.asList(
                expectAdminUserExists(mMockedUserHandleHelper, 100),
                expectRegularUserExists(mMockedUserHandleHelper, 101),
                expectManagedProfileExists(mMockedUserHandleHelper, 102),
                expectRegularUserExists(mMockedUserHandleHelper, 103),
                expectGuestUserExists(mMockedUserHandleHelper, 104, /* isEphemeral= */ false),
                expectEphemeralUserExists(mMockedUserHandleHelper, 105),
                expectDisabledUserExists(mMockedUserHandleHelper, 106),
                expectManagedProfileExists(mMockedUserHandleHelper, 107),
                expectManagedProfileExists(mMockedUserHandleHelper, 108),
                expectRegularUserExists(mMockedUserHandleHelper, 109)));

        // Parent: test100, child: test102
        associateParentChild(users.get(0), users.get(2));
        // Parent: test103, child: test107
        associateParentChild(users.get(3), users.get(7));
        // Parent: test103, child: test108
        associateParentChild(users.get(3), users.get(8));
        return users;
    }

    @Test
    public void testGetAllPossibleDrivers() {
        Set<Integer> expected = new HashSet<Integer>(Arrays.asList(100, 101, 103, 104));
        mockExistingUsers(prepareUserList());
        mockIsHeadlessSystemUser(109, true);
        for (UserHandle user : mExperimentalCarUserService.getAllDrivers()) {
            assertThat(expected).contains(user.getIdentifier());
            expected.remove(user.getIdentifier());
        }
        assertThat(expected).isEmpty();
    }

    @Test
    public void testGetAllPassengers() {
        SparseArray<HashSet<Integer>> testCases = new SparseArray<HashSet<Integer>>() {
            {
                put(0, new HashSet<Integer>());
                put(100, new HashSet<Integer>(Arrays.asList(102)));
                put(101, new HashSet<Integer>());
                put(103, new HashSet<Integer>(Arrays.asList(107)));
            }
        };
        mockIsHeadlessSystemUser(108, true);
        for (int i = 0; i < testCases.size(); i++) {
            mockExistingUsers(prepareUserList());
            List<UserHandle> passengers = mExperimentalCarUserService
                    .getPassengers(testCases.keyAt(i));
            HashSet<Integer> expected = testCases.valueAt(i);
            for (UserHandle user : passengers) {
                assertThat(expected).contains(user.getIdentifier());
                expected.remove(user.getIdentifier());
            }
            assertThat(expected).isEmpty();
        }
    }

    private UserCreationResult getCreateDriverResult(AndroidFuture<UserCreationResult> future) {
        return getResult(future, "create driver");
    }

    private static final class FakeCarOccupantZoneService {
        private final SparseArray<Integer> mZoneUserMap = new SparseArray<Integer>();
        private final ZoneUserBindingHelper mZoneUserBindigHelper =
                new ZoneUserBindingHelper() {
                    @Override
                    @NonNull
                    public List<OccupantZoneInfo> getOccupantZones(
                            @OccupantTypeEnum int occupantType) {
                        return Collections.emptyList();
                    }

                    @Override
                    public boolean assignUserToOccupantZone(@UserIdInt int userId, int zoneId) {
                        if (mZoneUserMap.get(zoneId) != null) {
                            return false;
                        }
                        mZoneUserMap.put(zoneId, userId);
                        return true;
                    }

                    @Override
                    public boolean unassignUserFromOccupantZone(@UserIdInt int userId) {
                        for (int index = 0; index < mZoneUserMap.size(); index++) {
                            if (mZoneUserMap.valueAt(index) == userId) {
                                mZoneUserMap.removeAt(index);
                                break;
                            }
                        }
                        return true;
                    }

                    @Override
                    public boolean isPassengerDisplayAvailable() {
                        return true;
                    }
                };

        FakeCarOccupantZoneService(ExperimentalCarUserService experimentalCarUserService) {
            experimentalCarUserService.setZoneUserBindingHelper(mZoneUserBindigHelper);
        }
    }
}
