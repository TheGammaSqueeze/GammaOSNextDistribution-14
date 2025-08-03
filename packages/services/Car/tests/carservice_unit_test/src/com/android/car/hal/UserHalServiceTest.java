/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.car.hal;

import static android.car.VehiclePropertyIds.CREATE_USER;
import static android.car.VehiclePropertyIds.CURRENT_GEAR;
import static android.car.VehiclePropertyIds.INITIAL_USER_INFO;
import static android.car.VehiclePropertyIds.REMOVE_USER;
import static android.car.VehiclePropertyIds.SWITCH_USER;
import static android.car.VehiclePropertyIds.USER_IDENTIFICATION_ASSOCIATION;
import static android.hardware.automotive.vehicle.InitialUserInfoRequestType.COLD_BOOT;
import static android.hardware.automotive.vehicle.UserIdentificationAssociationSetValue.ASSOCIATE_CURRENT_USER;
import static android.hardware.automotive.vehicle.UserIdentificationAssociationType.CUSTOM_1;
import static android.hardware.automotive.vehicle.UserIdentificationAssociationType.KEY_FOB;
import static android.hardware.automotive.vehicle.UserIdentificationAssociationValue.ASSOCIATED_CURRENT_USER;

import static com.android.car.hal.HalPropValueMatcher.isProperty;
import static com.android.car.hal.HalPropValueMatcher.isPropertyWithValues;
import static com.android.car.hal.VehicleHalTestingHelper.newConfig;
import static com.android.car.hal.VehicleHalTestingHelper.newSubscribableConfig;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.car.hardware.property.VehicleHalStatusCode;
import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.car.test.mocks.JavaMockitoHelper;
import android.hardware.automotive.vehicle.CreateUserRequest;
import android.hardware.automotive.vehicle.CreateUserResponse;
import android.hardware.automotive.vehicle.CreateUserStatus;
import android.hardware.automotive.vehicle.InitialUserInfoResponse;
import android.hardware.automotive.vehicle.InitialUserInfoResponseAction;
import android.hardware.automotive.vehicle.RemoveUserRequest;
import android.hardware.automotive.vehicle.SwitchUserMessageType;
import android.hardware.automotive.vehicle.SwitchUserRequest;
import android.hardware.automotive.vehicle.SwitchUserResponse;
import android.hardware.automotive.vehicle.SwitchUserStatus;
import android.hardware.automotive.vehicle.UserIdentificationAssociation;
import android.hardware.automotive.vehicle.UserIdentificationGetRequest;
import android.hardware.automotive.vehicle.UserIdentificationResponse;
import android.hardware.automotive.vehicle.UserIdentificationSetAssociation;
import android.hardware.automotive.vehicle.UserIdentificationSetRequest;
import android.hardware.automotive.vehicle.UserInfo;
import android.hardware.automotive.vehicle.UsersInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.ServiceSpecificException;
import android.os.SystemClock;
import android.os.UserHandle;
import android.util.Log;
import android.util.Pair;

import com.android.car.CarLocalServices;
import com.android.car.CarStatsLog;
import com.android.car.internal.os.CarSystemProperties;
import com.android.car.user.CarUserService;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(MockitoJUnitRunner.class)
public final class UserHalServiceTest extends AbstractExtendedMockitoTestCase {

    private static final String TAG = UserHalServiceTest.class.getSimpleName();

    /**
     * Timeout passed to {@link UserHalService} methods. This is the timeout for which
     * {@link UserHalService} wait for the property change event and return
     * {@link HalCallback.STATUS_HAL_RESPONSE_TIMEOUT} if HAL doesn't respond. This timeout is used
     * where we expect HAL to return something and {@link UserHalService} methods are not expected
     * to timeout. Tests are not supposed to wait for this much so this value can be high. If test
     * requires HAL to timeout, then use {@link HAL_TIMEOUT_MS}.
     */
    private static final int HAL_TIMEOUT_MS = 5_000;

    /**
     * Timeout passed to {@link UserHalService} methods only if test expects that call will be
     * timeout and {@link HalCallback.STATUS_HAL_RESPONSE_TIMEOUT} is expected response. A higher
     * value for this is going to slow down tests. If this timeout is used, then it is expected that
     * {@link HalCallback.STATUS_HAL_RESPONSE_TIMEOUT} is checked.
     */
    private static final int HAL_TIMEOUT_FOR_NEGATIVE_TESTS_MS = 250;

    /**
     * If tests expect {@link UserHalService} call to timeout, and if they require to sleep then
     * sleep for this much time. This value should be higher than {@link HAL_TIMEOUT_MS}. A much
     * higher value for this is going to slow down the test.
     */
    private static final int WAITING_TIME_FOR_NEGATIVE_TESTS_MS = HAL_TIMEOUT_FOR_NEGATIVE_TESTS_MS
            + 100;

    /**
     * Timeout for {@link GenericHalCallback#assertCalled()} for tests. In each case,
     * {@link UserHalService} is supposed to return something - either a valid response or a
     * timeout. This timeout is for the callback to wait for the response. A higher value of this
     * timeout should not affect the test duration.
     */
    private static final int CALLBACK_TIMEOUT = 5_000;

    // Used when crafting a request property - the real value will be set by the mock.
    private static final int REQUEST_ID_PLACE_HOLDER = 1111;

    private static final int DEFAULT_REQUEST_ID = 2222;

    private static final int DEFAULT_USER_ID = 333;
    private static final int DEFAULT_USER_FLAGS = 444;

    private static final int INITIAL_USER_INFO_RESPONSE_ACTION = 108;

    @Mock
    private VehicleHal mVehicleHal;
    @Mock
    private CarUserService mCarUserService;

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private final UserInfo mUser0 = new UserInfo();
    private final UserInfo mUser100 = new UserInfo();

    private final UsersInfo mUsersInfo = UserHalHelper.emptyUsersInfo();

    // Must be a spy so we can mock getNextRequestId()
    private UserHalService mUserHalService;

    private final HalPropValueBuilder mPropValueBuilder = new HalPropValueBuilder(/*isAidl=*/true);

    public UserHalServiceTest() {
        super(UserHalService.TAG);
    }

    @Override
    protected void onSessionBuilder(CustomMockitoSessionBuilder builder) {
        builder.spyStatic(CarSystemProperties.class)
                .spyStatic(CarStatsLog.class);
    }

    @Before
    public void setFixtures() {
        mockUserHalEnabled(true);
        when(mVehicleHal.getHalPropValueBuilder()).thenReturn(mPropValueBuilder);
        mUserHalService = spy(new UserHalService(mVehicleHal, mHandler));
        // Needs at least one property, otherwise isSupported() and isUserAssociationSupported()
        // will return false
        mUserHalService.takeProperties(
                Arrays.asList(newSubscribableConfig(INITIAL_USER_INFO),
                newSubscribableConfig(CREATE_USER),
                newSubscribableConfig(REMOVE_USER),
                newSubscribableConfig(SWITCH_USER),
                newSubscribableConfig(USER_IDENTIFICATION_ASSOCIATION)));

        mUser0.userId = 0;
        mUser0.flags = 100;
        mUser100.userId = 100;
        mUser100.flags = 110;

        mUsersInfo.currentUser = mUser0;
        mUsersInfo.numberUsers = 2;
        mUsersInfo.existingUsers = new UserInfo[]{mUser0, mUser100};

        CarLocalServices.addService(CarUserService.class, mCarUserService);
    }

    @After
    public void clearFixtures() {
        CarLocalServices.removeServiceForTest(CarUserService.class);
    }

    @Test
    public void testTakeSupportedProperties_supportedNoProperties() {
        // Cannot use mUserHalService because it's already set with supported properties
        UserHalService myHalService = new UserHalService(mVehicleHal);

        myHalService.takeProperties(Collections.emptyList());
        assertThat(myHalService.isSupported()).isFalse();
        assertThat(myHalService.isUserAssociationSupported()).isFalse();
    }

    @Test
    public void testTakeSupportedProperties_supportedFewProperties() {
        // Cannot use mUserHalService because it's already set with supported properties
        UserHalService myHalService = new UserHalService(mVehicleHal);
        myHalService.takeProperties(
                Arrays.asList(newSubscribableConfig(INITIAL_USER_INFO),
                newSubscribableConfig(CREATE_USER),
                newSubscribableConfig(REMOVE_USER)));

        assertThat(myHalService.isSupported()).isFalse();
        assertThat(myHalService.isUserAssociationSupported()).isFalse();
    }

    @Test
    public void testTakeSupportedProperties_supportedAllCorePropertiesButEnabledPropertyNotSet() {
        mockUserHalEnabled(null);
        // Cannot use mUserHalService because it's already set with supported properties
        UserHalService myHalService = new UserHalService(mVehicleHal);
        myHalService.takeProperties(
                Arrays.asList(newSubscribableConfig(INITIAL_USER_INFO),
                newSubscribableConfig(CREATE_USER), newSubscribableConfig(REMOVE_USER),
                newSubscribableConfig(SWITCH_USER)));

        assertThat(myHalService.isSupported()).isFalse();
        assertThat(myHalService.isUserAssociationSupported()).isFalse();
    }

    @Test
    public void testTakeSupportedProperties_supportedAllCorePropertiesButDisabled() {
        mockUserHalEnabled(false);
        // Cannot use mUserHalService because it's already set with supported properties
        UserHalService myHalService = new UserHalService(mVehicleHal);
        myHalService.takeProperties(
                Arrays.asList(newSubscribableConfig(INITIAL_USER_INFO),
                newSubscribableConfig(CREATE_USER), newSubscribableConfig(REMOVE_USER),
                newSubscribableConfig(SWITCH_USER)));

        assertThat(myHalService.isSupported()).isFalse();
        assertThat(myHalService.isUserAssociationSupported()).isFalse();
    }

    @Test
    public void testTakeSupportedProperties_supportedAllCoreProperties() {
        // Cannot use mUserHalService because it's already set with supported properties
        UserHalService myHalService = new UserHalService(mVehicleHal);
        myHalService.takeProperties(
                Arrays.asList(newSubscribableConfig(INITIAL_USER_INFO),
                newSubscribableConfig(CREATE_USER),
                newSubscribableConfig(REMOVE_USER),
                newSubscribableConfig(SWITCH_USER)));

        assertThat(myHalService.isSupported()).isTrue();
        assertThat(myHalService.isUserAssociationSupported()).isFalse();
    }

    @Test
    public void testTakeSupportedProperties_supportedAllPropertiesButDisabled() {
        mockUserHalEnabled(false);
        // Cannot use mUserHalService because it's already set with supported properties
        UserHalService myHalService = new UserHalService(mVehicleHal);
        myHalService.takeProperties(
                Arrays.asList(newSubscribableConfig(INITIAL_USER_INFO),
                newSubscribableConfig(CREATE_USER), newSubscribableConfig(REMOVE_USER),
                newSubscribableConfig(SWITCH_USER),
                newSubscribableConfig(USER_IDENTIFICATION_ASSOCIATION)));

        assertThat(myHalService.isSupported()).isFalse();
        assertThat(myHalService.isUserAssociationSupported()).isTrue();
    }

    @Test
    public void testTakeSupportedProperties_supportedAllPropertiesButEnablePropertyNotSet() {
        mockUserHalEnabled(null);
        // Cannot use mUserHalService because it's already set with supported properties
        UserHalService myHalService = new UserHalService(mVehicleHal);
        myHalService.takeProperties(
                Arrays.asList(newSubscribableConfig(INITIAL_USER_INFO),
                newSubscribableConfig(CREATE_USER), newSubscribableConfig(REMOVE_USER),
                newSubscribableConfig(SWITCH_USER),
                newSubscribableConfig(USER_IDENTIFICATION_ASSOCIATION)));

        assertThat(myHalService.isSupported()).isFalse();
        assertThat(myHalService.isUserAssociationSupported()).isTrue();
    }

    @Test
    public void testTakeSupportedProperties_supportedAllProperties() {
        // Cannot use mUserHalService because it's already set with supported properties
        UserHalService myHalService = new UserHalService(mVehicleHal);
        myHalService.takeProperties(
                Arrays.asList(newSubscribableConfig(INITIAL_USER_INFO),
                newSubscribableConfig(CREATE_USER),
                newSubscribableConfig(REMOVE_USER),
                newSubscribableConfig(SWITCH_USER),
                newSubscribableConfig(USER_IDENTIFICATION_ASSOCIATION)));

        assertThat(myHalService.isSupported()).isTrue();
        assertThat(myHalService.isUserAssociationSupported()).isTrue();
    }

    @Test
    public void testTakeSupportedPropertiesAndInit() {
        // Cannot use mUserHalService because it's already set with supported properties
        UserHalService myHalService = new UserHalService(mVehicleHal);
        HalPropConfig unsupportedConfig = newConfig(CURRENT_GEAR);

        myHalService.takeProperties(
                Arrays.asList(newSubscribableConfig(INITIAL_USER_INFO),
                newSubscribableConfig(CREATE_USER),
                newSubscribableConfig(REMOVE_USER),
                newSubscribableConfig(SWITCH_USER), unsupportedConfig,
                newSubscribableConfig(USER_IDENTIFICATION_ASSOCIATION)));


        // Ideally there should be 2 test methods (one for takeSupportedProperties() and one for
        // init()), but on "real life" VehicleHal calls these 2 methods in sequence, and the latter
        // depends on the properties set by the former, so it's ok to test both here...
        myHalService.init();
        verify(mVehicleHal).subscribePropertySafe(myHalService, INITIAL_USER_INFO);
        verify(mVehicleHal).subscribePropertySafe(myHalService, CREATE_USER);
        verify(mVehicleHal).subscribePropertySafe(myHalService, REMOVE_USER);
        verify(mVehicleHal).subscribePropertySafe(myHalService, SWITCH_USER);
        verify(mVehicleHal).subscribePropertySafe(myHalService, USER_IDENTIFICATION_ASSOCIATION);
    }

    @Test
    public void testSupportedProperties() {
        assertThat(mUserHalService.getAllSupportedProperties()).asList().containsExactly(
                INITIAL_USER_INFO, CREATE_USER, REMOVE_USER, SWITCH_USER,
                USER_IDENTIFICATION_ASSOCIATION);
    }

    @Test
    public void testGetUserInfo_noHalSupported() {
        // Cannot use mUserHalService because it's already set with supported properties
        UserHalService myHalService = new UserHalService(mVehicleHal);

        assertThrows(IllegalStateException.class, () -> myHalService.getInitialUserInfo(COLD_BOOT,
                HAL_TIMEOUT_MS, mUsersInfo, noOpCallback()));
    }

    @Test
    public void testGetUserInfo_invalidTimeout() {
        assertThrows(IllegalArgumentException.class, () ->
                mUserHalService.getInitialUserInfo(COLD_BOOT, 0, mUsersInfo, noOpCallback()));
        assertThrows(IllegalArgumentException.class, () ->
                mUserHalService.getInitialUserInfo(COLD_BOOT, -1, mUsersInfo, noOpCallback()));
    }

    @Test
    public void testGetUserInfo_noUsersInfo() {
        assertThrows(NullPointerException.class, () -> mUserHalService.getInitialUserInfo(COLD_BOOT,
                HAL_TIMEOUT_MS, null, noOpCallback()));
    }

    @Test
    public void testGetUserInfo_noCallback() {
        assertThrows(NullPointerException.class,
                () -> mUserHalService.getInitialUserInfo(COLD_BOOT, HAL_TIMEOUT_MS,
                        mUsersInfo, null));
    }

    @Test
    public void testGetUserInfo_halSetTimedOut() throws Exception {
        replySetPropertyWithTimeoutException(INITIAL_USER_INFO);

        GenericHalCallback<InitialUserInfoResponse> callback = new GenericHalCallback<>(
                CALLBACK_TIMEOUT);
        mUserHalService.getInitialUserInfo(COLD_BOOT, HAL_TIMEOUT_MS, mUsersInfo,
                callback);

        callback.assertCalled();
        assertCallbackStatus(callback, HalCallback.STATUS_HAL_SET_TIMEOUT);
        assertThat(callback.response).isNull();

        // Make sure the pending request was removed
        SystemClock.sleep(WAITING_TIME_FOR_NEGATIVE_TESTS_MS);
        callback.assertNotCalledAgain();
    }

    @Test
    public void testGetUserInfo_halDidNotReply() throws Exception {
        GenericHalCallback<InitialUserInfoResponse> callback = new GenericHalCallback<>(
                CALLBACK_TIMEOUT);
        mUserHalService.getInitialUserInfo(COLD_BOOT, HAL_TIMEOUT_FOR_NEGATIVE_TESTS_MS, mUsersInfo,
                callback);

        callback.assertCalled();
        assertCallbackStatus(callback, HalCallback.STATUS_HAL_RESPONSE_TIMEOUT);
        assertThat(callback.response).isNull();
    }

    @Test
    public void testGetUserInfo_secondCallFailWhilePending() throws Exception {
        GenericHalCallback<InitialUserInfoResponse> callback1 = new GenericHalCallback<>(
                CALLBACK_TIMEOUT);
        GenericHalCallback<InitialUserInfoResponse> callback2 = new GenericHalCallback<>(
                CALLBACK_TIMEOUT);
        mUserHalService.getInitialUserInfo(COLD_BOOT, HAL_TIMEOUT_FOR_NEGATIVE_TESTS_MS, mUsersInfo,
                callback1);
        mUserHalService.getInitialUserInfo(COLD_BOOT, HAL_TIMEOUT_MS, mUsersInfo,
                callback2);

        callback1.assertCalled();
        assertCallbackStatus(callback1, HalCallback.STATUS_HAL_RESPONSE_TIMEOUT);
        assertThat(callback1.response).isNull();

        callback2.assertCalled();
        assertCallbackStatus(callback2, HalCallback.STATUS_CONCURRENT_OPERATION);
        assertThat(callback1.response).isNull();
    }

    @Test
    public void testGetUserInfo_halReplyWithWrongRequestId() throws Exception {
        HalPropValue propResponse = createPropRequest(INITIAL_USER_INFO,
                    REQUEST_ID_PLACE_HOLDER, INITIAL_USER_INFO_RESPONSE_ACTION);

        replySetPropertyWithOnChangeEvent(INITIAL_USER_INFO, propResponse,
                /* rightRequestId= */ false);

        GenericHalCallback<InitialUserInfoResponse> callback = new GenericHalCallback<>(
                CALLBACK_TIMEOUT);
        mUserHalService.getInitialUserInfo(COLD_BOOT, HAL_TIMEOUT_FOR_NEGATIVE_TESTS_MS, mUsersInfo,
                callback);

        callback.assertCalled();
        assertCallbackStatus(callback, HalCallback.STATUS_HAL_RESPONSE_TIMEOUT);
        assertThat(callback.response).isNull();
    }

    @Test
    public void testGetUserInfo_halReturnedInvalidAction() throws Exception {
        HalPropValue propResponse = createPropRequest(INITIAL_USER_INFO,
                    REQUEST_ID_PLACE_HOLDER, INITIAL_USER_INFO_RESPONSE_ACTION);

        AtomicReference<HalPropValue> reqCaptor = replySetPropertyWithOnChangeEvent(
                INITIAL_USER_INFO, propResponse, /* rightRequestId= */ true);

        GenericHalCallback<InitialUserInfoResponse> callback = new GenericHalCallback<>(
                CALLBACK_TIMEOUT);
        mUserHalService.getInitialUserInfo(COLD_BOOT, HAL_TIMEOUT_MS, mUsersInfo,
                callback);

        callback.assertCalled();

        // Make sure the arguments were properly converted
        assertInitialUserInfoSetRequest(reqCaptor.get(), COLD_BOOT);

        // Assert response
        assertCallbackStatus(callback, HalCallback.STATUS_WRONG_HAL_RESPONSE);
        assertThat(callback.response).isNull();
    }

    @Test
    public void testGetUserInfo_successDefault() throws Exception {
        getUserInfoSuccessTest(1);
    }

    private void getUserInfoSuccessTest(int count) throws Exception {
        HalPropValue propResponse = createPropRequest(INITIAL_USER_INFO,
                    REQUEST_ID_PLACE_HOLDER, InitialUserInfoResponseAction.DEFAULT);

        AtomicReference<HalPropValue> reqCaptor = replySetPropertyWithOnChangeEvent(
                INITIAL_USER_INFO, propResponse, /* rightRequestId= */ true);

        GenericHalCallback<InitialUserInfoResponse> callback = new GenericHalCallback<>(
                CALLBACK_TIMEOUT);
        mUserHalService.getInitialUserInfo(COLD_BOOT, HAL_TIMEOUT_MS, mUsersInfo,
                callback);

        callback.assertCalled();

        // Make sure the arguments were properly converted
        assertInitialUserInfoSetRequest(reqCaptor.get(), COLD_BOOT);

        // Assert response
        assertCallbackStatus(callback, HalCallback.STATUS_OK);
        InitialUserInfoResponse actualResponse = callback.response;
        assertThat(actualResponse.action).isEqualTo(InitialUserInfoResponseAction.DEFAULT);
        assertThat(actualResponse.userNameToCreate).isEmpty();
        assertThat(actualResponse.userToSwitchOrCreate).isNotNull();
        assertThat(actualResponse.userToSwitchOrCreate.userId).isEqualTo(UserHandle.USER_NULL);
        assertThat(actualResponse.userToSwitchOrCreate.flags).isEqualTo(0);

        int responseActionDefault = CarStatsLog
                .CAR_USER_HAL_INITIAL_USER_INFO_RESPONSE_REPORTED__RESPONSE_ACTION__DEFAULT;
        verify(() -> CarStatsLog.write(
                eq(CarStatsLog.CAR_USER_HAL_INITIAL_USER_INFO_REQUEST_REPORTED),
                anyInt(),
                eq(CarStatsLog
                        .CAR_USER_HAL_INITIAL_USER_INFO_REQUEST_REPORTED__REQUEST_TYPE__COLD_BOOT),
                eq(HAL_TIMEOUT_MS)), times(count));
        verify(() -> CarStatsLog.write(
                eq(CarStatsLog.CAR_USER_HAL_INITIAL_USER_INFO_RESPONSE_REPORTED),
                anyInt(),
                eq(CarStatsLog.CAR_USER_HAL_MODIFY_USER_RESPONSE_REPORTED__CALLBACK_STATUS__OK),
                eq(responseActionDefault),
                eq(UserHandle.USER_NULL), eq(0), eq("")), timeout(CALLBACK_TIMEOUT).times(count));
    }

    @Test
    public void testGetUserInfo_successSwitchUser() throws Exception {
        int userIdToSwitch = 42;
        HalPropValue propResponse = createPropRequest(INITIAL_USER_INFO,
                    REQUEST_ID_PLACE_HOLDER, InitialUserInfoResponseAction.SWITCH,
                    new int[]{userIdToSwitch});

        AtomicReference<HalPropValue> reqCaptor = replySetPropertyWithOnChangeEvent(
                INITIAL_USER_INFO, propResponse, /* rightRequestId= */ true);

        GenericHalCallback<InitialUserInfoResponse> callback = new GenericHalCallback<>(
                CALLBACK_TIMEOUT);
        mUserHalService.getInitialUserInfo(COLD_BOOT, HAL_TIMEOUT_MS, mUsersInfo,
                callback);

        callback.assertCalled();

        // Make sure the arguments were properly converted
        assertInitialUserInfoSetRequest(reqCaptor.get(), COLD_BOOT);

        assertCallbackStatus(callback, HalCallback.STATUS_OK);
        InitialUserInfoResponse actualResponse = callback.response;
        assertThat(actualResponse.action).isEqualTo(InitialUserInfoResponseAction.SWITCH);
        assertThat(actualResponse.userNameToCreate).isEmpty();
        UserInfo userToSwitch = actualResponse.userToSwitchOrCreate;
        assertThat(userToSwitch).isNotNull();
        assertThat(userToSwitch.userId).isEqualTo(userIdToSwitch);
        assertThat(userToSwitch.flags).isEqualTo(0);

        verify(() -> CarStatsLog.write(
                eq(CarStatsLog.CAR_USER_HAL_INITIAL_USER_INFO_REQUEST_REPORTED),
                anyInt(),
                eq(CarStatsLog
                        .CAR_USER_HAL_INITIAL_USER_INFO_REQUEST_REPORTED__REQUEST_TYPE__COLD_BOOT),
                eq(HAL_TIMEOUT_MS)));
        verify(() -> CarStatsLog.write(
                eq(CarStatsLog.CAR_USER_HAL_INITIAL_USER_INFO_RESPONSE_REPORTED),
                anyInt(),
                eq(CarStatsLog.CAR_USER_HAL_MODIFY_USER_RESPONSE_REPORTED__CALLBACK_STATUS__OK),
                eq(CarStatsLog
                        .CAR_USER_HAL_INITIAL_USER_INFO_RESPONSE_REPORTED__RESPONSE_ACTION__SWITCH),
                eq(userIdToSwitch), eq(0), eq("")), timeout(CALLBACK_TIMEOUT));
    }

    @Test
    public void testGetUserInfo_successCreateUser() throws Exception {
        int newUserFlags = 108;
        String newUserName = "Groot";
        int unusedUserId = 666;
        HalPropValue propResponse = createPropRequest(INITIAL_USER_INFO,
                    REQUEST_ID_PLACE_HOLDER, InitialUserInfoResponseAction.CREATE,
                    new int[]{unusedUserId, newUserFlags}, "||" + newUserName);

        AtomicReference<HalPropValue> reqCaptor = replySetPropertyWithOnChangeEvent(
                INITIAL_USER_INFO, propResponse, /* rightRequestId= */ true);

        GenericHalCallback<InitialUserInfoResponse> callback = new GenericHalCallback<>(
                CALLBACK_TIMEOUT);
        mUserHalService.getInitialUserInfo(COLD_BOOT, HAL_TIMEOUT_MS, mUsersInfo,
                callback);

        callback.assertCalled();

        // Make sure the arguments were properly converted
        assertInitialUserInfoSetRequest(reqCaptor.get(), COLD_BOOT);

        assertCallbackStatus(callback, HalCallback.STATUS_OK);
        assertThat(callback.status).isEqualTo(HalCallback.STATUS_OK);
        InitialUserInfoResponse actualResponse = callback.response;
        assertThat(actualResponse.action).isEqualTo(InitialUserInfoResponseAction.CREATE);
        assertThat(actualResponse.userLocales).isEmpty();
        assertThat(actualResponse.userNameToCreate).isEqualTo(newUserName);
        UserInfo newUser = actualResponse.userToSwitchOrCreate;
        assertThat(newUser).isNotNull();
        assertThat(newUser.userId).isEqualTo(UserHandle.USER_NULL);
        assertThat(newUser.flags).isEqualTo(newUserFlags);

        verify(() -> CarStatsLog.write(
                eq(CarStatsLog.CAR_USER_HAL_INITIAL_USER_INFO_REQUEST_REPORTED),
                anyInt(),
                eq(CarStatsLog
                        .CAR_USER_HAL_INITIAL_USER_INFO_REQUEST_REPORTED__REQUEST_TYPE__COLD_BOOT),
                eq(HAL_TIMEOUT_MS)));
        verify(() -> CarStatsLog.write(
                eq(CarStatsLog.CAR_USER_HAL_INITIAL_USER_INFO_RESPONSE_REPORTED),
                anyInt(),
                eq(CarStatsLog.CAR_USER_HAL_MODIFY_USER_RESPONSE_REPORTED__CALLBACK_STATUS__OK),
                eq(CarStatsLog
                        .CAR_USER_HAL_INITIAL_USER_INFO_RESPONSE_REPORTED__RESPONSE_ACTION__CREATE),
                eq(UserHandle.USER_NULL), eq(newUserFlags), eq("")), timeout(CALLBACK_TIMEOUT));
    }

    @Test
    public void testGetUserInfo_twoSuccessfulCalls() throws Exception {
        getUserInfoSuccessTest(1);

        getUserInfoSuccessTest(2);
    }

    @Test
    public void testSwitchUser_noHalSupported() {
        // Cannot use mUserHalService because it's already set with supported properties
        UserHalService myHalService = new UserHalService(mVehicleHal);

        assertThrows(IllegalStateException.class,
                () -> myHalService.switchUser(createUserSwitchRequest(mUser100, mUsersInfo),
                        HAL_TIMEOUT_MS, noOpCallback()));
    }

    @Test
    public void testSwitchUser_invalidTimeout() {
        assertThrows(IllegalArgumentException.class, () -> mUserHalService
                .switchUser(createUserSwitchRequest(mUser100, mUsersInfo), 0, noOpCallback()));
        assertThrows(IllegalArgumentException.class, () -> mUserHalService
                .switchUser(createUserSwitchRequest(mUser100, mUsersInfo), -1, noOpCallback()));
    }

    @Test
    public void testSwitchUser_noUsersInfo() {
        assertThrows(IllegalArgumentException.class, () -> mUserHalService
                .switchUser(createUserSwitchRequest(mUser100, null), HAL_TIMEOUT_MS,
                        noOpCallback()));
    }

    @Test
    public void testSwitchUser_noCallback() {
        assertThrows(NullPointerException.class, () -> mUserHalService
                .switchUser(createUserSwitchRequest(mUser100, mUsersInfo), HAL_TIMEOUT_MS, null));
    }

    @Test
    public void testSwitchUser_nullRequest() {
        assertThrows(NullPointerException.class, () -> mUserHalService
                .switchUser(null, HAL_TIMEOUT_MS, noOpCallback()));
    }

    @Test
    public void testSwitchUser_noTarget() {
        assertThrows(NullPointerException.class, () -> mUserHalService
                .switchUser(createUserSwitchRequest(null, mUsersInfo), HAL_TIMEOUT_MS,
                        noOpCallback()));
    }

    @Test
    public void testSwitchUser_halSetTimedOut() throws Exception {
        replySetPropertyWithTimeoutException(SWITCH_USER);

        GenericHalCallback<SwitchUserResponse> callback = new GenericHalCallback<>(
                CALLBACK_TIMEOUT);
        mUserHalService.switchUser(createUserSwitchRequest(mUser100, mUsersInfo),
                HAL_TIMEOUT_FOR_NEGATIVE_TESTS_MS,
                callback);

        callback.assertCalled();
        assertCallbackStatus(callback, HalCallback.STATUS_HAL_SET_TIMEOUT);
        assertThat(callback.response).isNull();

        // Make sure the pending request was removed
        SystemClock.sleep(WAITING_TIME_FOR_NEGATIVE_TESTS_MS);
        callback.assertNotCalledAgain();
    }

    @Test
    public void testSwitchUser_halDidNotReply() throws Exception {
        GenericHalCallback<SwitchUserResponse> callback = new GenericHalCallback<>(
                CALLBACK_TIMEOUT);
        mUserHalService.switchUser(createUserSwitchRequest(mUser100, mUsersInfo),
                HAL_TIMEOUT_FOR_NEGATIVE_TESTS_MS,
                callback);

        callback.assertCalled();
        assertCallbackStatus(callback, HalCallback.STATUS_HAL_RESPONSE_TIMEOUT);
        assertThat(callback.response).isNull();
    }

    @Test
    public void testSwitchUser_halReplyWithWrongRequestId() throws Exception {
        HalPropValue propResponse = createPropRequest(SWITCH_USER,
                    REQUEST_ID_PLACE_HOLDER, SwitchUserMessageType.VEHICLE_RESPONSE,
                    new int[]{SwitchUserStatus.SUCCESS});

        replySetPropertyWithOnChangeEvent(SWITCH_USER, propResponse,
                /* rightRequestId= */ false);

        GenericHalCallback<SwitchUserResponse> callback = new GenericHalCallback<>(
                CALLBACK_TIMEOUT);
        mUserHalService.switchUser(createUserSwitchRequest(mUser100, mUsersInfo),
                HAL_TIMEOUT_FOR_NEGATIVE_TESTS_MS,
                callback);

        callback.assertCalled();
        assertCallbackStatus(callback, HalCallback.STATUS_HAL_RESPONSE_TIMEOUT);
        assertThat(callback.response).isNull();

        int switchRequestAndroid = CarStatsLog
                .CAR_USER_HAL_MODIFY_USER_REQUEST_REPORTED__REQUEST_TYPE__SWITCH_REQUEST_ANDROID;
        int statusUnspecified =
                CarStatsLog.CAR_USER_HAL_MODIFY_USER_RESPONSE_REPORTED__REQUEST_STATUS__UNSPECIFIED;
        int statusWrongHalResponse = CarStatsLog
                .CAR_USER_HAL_MODIFY_USER_RESPONSE_REPORTED__CALLBACK_STATUS__WRONG_HAL_RESPONSE;

        verify(() -> CarStatsLog.write(
                eq(CarStatsLog.CAR_USER_HAL_MODIFY_USER_REQUEST_REPORTED),
                anyInt(),
                eq(switchRequestAndroid),
                eq(0), eq(100), eq(100), eq(110), eq(HAL_TIMEOUT_FOR_NEGATIVE_TESTS_MS)));
        verify(() -> CarStatsLog.write(
                eq(CarStatsLog.CAR_USER_HAL_MODIFY_USER_RESPONSE_REPORTED),
                anyInt(),
                eq(statusWrongHalResponse),
                eq(statusUnspecified)), timeout(CALLBACK_TIMEOUT));
    }

    @Test
    public void testSwitchUser_halReturnedInvalidMessageType() throws Exception {
        HalPropValue propResponse = createPropRequest(SWITCH_USER,
                REQUEST_ID_PLACE_HOLDER, SwitchUserMessageType.LEGACY_ANDROID_SWITCH,
                new int[]{SwitchUserStatus.SUCCESS});

        AtomicReference<HalPropValue> reqCaptor = replySetPropertyWithOnChangeEvent(
                SWITCH_USER, propResponse, /* rightRequestId= */ true);

        GenericHalCallback<SwitchUserResponse> callback = new GenericHalCallback<>(
                CALLBACK_TIMEOUT);
        mUserHalService.switchUser(createUserSwitchRequest(mUser100, mUsersInfo), HAL_TIMEOUT_MS,
                callback);

        callback.assertCalled();

        // Make sure the arguments were properly converted
        assertHalSetSwitchUserRequest(reqCaptor.get(), SwitchUserMessageType.ANDROID_SWITCH,
                mUser100);

        // Assert response
        assertCallbackStatus(callback, HalCallback.STATUS_WRONG_HAL_RESPONSE);
        assertThat(callback.response).isNull();
    }

    @Test
    public void testSwitchUser_success() throws Exception {
        HalPropValue propResponse = createPropRequest(SWITCH_USER,
                    REQUEST_ID_PLACE_HOLDER, SwitchUserMessageType.VEHICLE_RESPONSE,
                    new int[]{SwitchUserStatus.SUCCESS});

        AtomicReference<HalPropValue> reqCaptor = replySetPropertyWithOnChangeEvent(
                SWITCH_USER, propResponse, /* rightRequestId= */ true);

        GenericHalCallback<SwitchUserResponse> callback = new GenericHalCallback<>(
                CALLBACK_TIMEOUT);
        mUserHalService.switchUser(createUserSwitchRequest(mUser100, mUsersInfo), HAL_TIMEOUT_MS,
                callback);

        callback.assertCalled();

        // Make sure the arguments were properly converted
        assertHalSetSwitchUserRequest(reqCaptor.get(), SwitchUserMessageType.ANDROID_SWITCH,
                mUser100);

        // Assert response
        assertCallbackStatus(callback, HalCallback.STATUS_OK);
        SwitchUserResponse actualResponse = callback.response;
        assertThat(actualResponse.status).isEqualTo(SwitchUserStatus.SUCCESS);
        assertThat(actualResponse.messageType).isEqualTo(SwitchUserMessageType.VEHICLE_RESPONSE);
        assertThat(actualResponse.errorMessage).isEmpty();

        int switchRequestAndroid = CarStatsLog
                .CAR_USER_HAL_MODIFY_USER_REQUEST_REPORTED__REQUEST_TYPE__SWITCH_REQUEST_ANDROID;
        int statusSuccess = CarStatsLog
                .CAR_USER_HAL_MODIFY_USER_RESPONSE_REPORTED__REQUEST_STATUS__SUCCESS;

        verify(() -> CarStatsLog.write(
                eq(CarStatsLog.CAR_USER_HAL_MODIFY_USER_REQUEST_REPORTED),
                anyInt(),
                eq(switchRequestAndroid),
                eq(0), eq(100), eq(100), eq(110), eq(HAL_TIMEOUT_MS)));
        verify(() -> CarStatsLog.write(
                eq(CarStatsLog.CAR_USER_HAL_MODIFY_USER_RESPONSE_REPORTED),
                anyInt(),
                eq(CarStatsLog.CAR_USER_HAL_MODIFY_USER_RESPONSE_REPORTED__CALLBACK_STATUS__OK),
                eq(statusSuccess)), timeout(CALLBACK_TIMEOUT));
    }

    @Test
    public void testSwitchUser_failure() throws Exception {
        HalPropValue propResponse = createPropRequest(SWITCH_USER,
                    REQUEST_ID_PLACE_HOLDER, SwitchUserMessageType.VEHICLE_RESPONSE,
                    new int[]{SwitchUserStatus.FAILURE}, "D'OH!");

        AtomicReference<HalPropValue> reqCaptor = replySetPropertyWithOnChangeEvent(
                SWITCH_USER, propResponse, /* rightRequestId= */ true);

        GenericHalCallback<SwitchUserResponse> callback = new GenericHalCallback<>(
                CALLBACK_TIMEOUT);
        mUserHalService.switchUser(createUserSwitchRequest(mUser100, mUsersInfo), HAL_TIMEOUT_MS,
                callback);

        callback.assertCalled();

        // Make sure the arguments were properly converted
        assertHalSetSwitchUserRequest(reqCaptor.get(), SwitchUserMessageType.ANDROID_SWITCH,
                mUser100);

        // Assert response
        assertCallbackStatus(callback, HalCallback.STATUS_OK);
        SwitchUserResponse actualResponse = callback.response;
        assertThat(actualResponse.status).isEqualTo(SwitchUserStatus.FAILURE);
        assertThat(actualResponse.messageType).isEqualTo(SwitchUserMessageType.VEHICLE_RESPONSE);
        assertThat(actualResponse.errorMessage).isEqualTo("D'OH!");

        int switchRequestAndroid = CarStatsLog
                .CAR_USER_HAL_MODIFY_USER_REQUEST_REPORTED__REQUEST_TYPE__SWITCH_REQUEST_ANDROID;
        int statusFailure = CarStatsLog
                .CAR_USER_HAL_MODIFY_USER_RESPONSE_REPORTED__REQUEST_STATUS__FAILURE;

        verify(() -> CarStatsLog.write(
                eq(CarStatsLog.CAR_USER_HAL_MODIFY_USER_REQUEST_REPORTED),
                anyInt(),
                eq(switchRequestAndroid),
                eq(0), eq(100), eq(100), eq(110), eq(HAL_TIMEOUT_MS)));
        verify(() -> CarStatsLog.write(
                eq(CarStatsLog.CAR_USER_HAL_MODIFY_USER_RESPONSE_REPORTED),
                anyInt(),
                eq(CarStatsLog.CAR_USER_HAL_MODIFY_USER_RESPONSE_REPORTED__CALLBACK_STATUS__OK),
                eq(statusFailure)), timeout(CALLBACK_TIMEOUT));
    }

    @Test
    public void testSwitchUser_secondCallFailWhilePending() throws Exception {
        GenericHalCallback<SwitchUserResponse> callback1 = new GenericHalCallback<>(
                CALLBACK_TIMEOUT);
        GenericHalCallback<SwitchUserResponse> callback2 = new GenericHalCallback<>(
                CALLBACK_TIMEOUT);
        mUserHalService.switchUser(createUserSwitchRequest(mUser100, mUsersInfo),
                HAL_TIMEOUT_FOR_NEGATIVE_TESTS_MS,
                callback1);
        mUserHalService.switchUser(createUserSwitchRequest(mUser100, mUsersInfo), HAL_TIMEOUT_MS,
                callback2);

        callback1.assertCalled();
        assertCallbackStatus(callback1, HalCallback.STATUS_HAL_RESPONSE_TIMEOUT);
        assertThat(callback1.response).isNull();

        callback2.assertCalled();
        assertCallbackStatus(callback2, HalCallback.STATUS_CONCURRENT_OPERATION);
        assertThat(callback1.response).isNull();
    }

    @Test
    public void testSwitchUser_halReturnedInvalidStatus() throws Exception {
        HalPropValue propResponse = createPropRequest(SWITCH_USER,
                    REQUEST_ID_PLACE_HOLDER, SwitchUserMessageType.VEHICLE_RESPONSE,
                    new int[]{/*status =*/ 110});

        AtomicReference<HalPropValue> reqCaptor = replySetPropertyWithOnChangeEvent(
                SWITCH_USER, propResponse, /* rightRequestId= */ true);

        GenericHalCallback<SwitchUserResponse> callback = new GenericHalCallback<>(
                CALLBACK_TIMEOUT);
        mUserHalService.switchUser(createUserSwitchRequest(mUser100, mUsersInfo), HAL_TIMEOUT_MS,
                callback);

        callback.assertCalled();

        // Make sure the arguments were properly converted
        assertHalSetSwitchUserRequest(reqCaptor.get(), SwitchUserMessageType.ANDROID_SWITCH,
                mUser100);

        // Assert response
        assertCallbackStatus(callback, HalCallback.STATUS_WRONG_HAL_RESPONSE);
        assertThat(callback.response).isNull();

        int switchRequestAndroid = CarStatsLog
                .CAR_USER_HAL_MODIFY_USER_REQUEST_REPORTED__REQUEST_TYPE__SWITCH_REQUEST_ANDROID;
        int statusFailure = CarStatsLog
                .CAR_USER_HAL_MODIFY_USER_RESPONSE_REPORTED__REQUEST_STATUS__FAILURE;
        int statusWrongHalResponse = CarStatsLog
                .CAR_USER_HAL_MODIFY_USER_RESPONSE_REPORTED__CALLBACK_STATUS__WRONG_HAL_RESPONSE;
        verify(() -> CarStatsLog.write(
                eq(CarStatsLog.CAR_USER_HAL_MODIFY_USER_REQUEST_REPORTED),
                anyInt(),
                eq(switchRequestAndroid),
                eq(0), eq(100), eq(100), eq(110), eq(HAL_TIMEOUT_MS)));
        verify(() -> CarStatsLog.write(
                eq(CarStatsLog.CAR_USER_HAL_MODIFY_USER_RESPONSE_REPORTED),
                anyInt(),
                eq(statusWrongHalResponse),
                eq(statusFailure)), timeout(CALLBACK_TIMEOUT));
    }

    @Test
    public void testSwitchUser_halResponseAfterTimeout() throws Exception {
        HalPropValue propResponse = createPropRequest(SWITCH_USER,
                    REQUEST_ID_PLACE_HOLDER, SwitchUserMessageType.VEHICLE_RESPONSE,
                    new int[]{SwitchUserStatus.SUCCESS});
        CountDownLatch latch = new CountDownLatch(1);

        replySetPropertyWithOnChangeEvent(SWITCH_USER, propResponse, /* rightRequestId= */ true,
                latch);

        GenericHalCallback<SwitchUserResponse> callback = new GenericHalCallback<>(
                CALLBACK_TIMEOUT);

        mUserHalService.switchUser(createUserSwitchRequest(mUser100, mUsersInfo),
                HAL_TIMEOUT_FOR_NEGATIVE_TESTS_MS, callback);

        int switchRequestAndroid = CarStatsLog
                .CAR_USER_HAL_MODIFY_USER_REQUEST_REPORTED__REQUEST_TYPE__SWITCH_REQUEST_ANDROID;
        verify(() -> CarStatsLog.write(eq(CarStatsLog.CAR_USER_HAL_MODIFY_USER_REQUEST_REPORTED),
                anyInt(),
                eq(switchRequestAndroid),
                eq(0), eq(100), eq(100), eq(110), eq(HAL_TIMEOUT_FOR_NEGATIVE_TESTS_MS)));

        // The event has not been sent before the callback.
        callback.assertCalled();
        assertCallbackStatus(callback, HalCallback.STATUS_HAL_RESPONSE_TIMEOUT);
        assertThat(callback.response).isNull();

        // Now send the event after the request already timed-out.
        latch.countDown();

        int statusUnspecified =
                CarStatsLog.CAR_USER_HAL_MODIFY_USER_RESPONSE_REPORTED__REQUEST_STATUS__UNSPECIFIED;
        int statusWrongHalResponse = CarStatsLog
                .CAR_USER_HAL_MODIFY_USER_RESPONSE_REPORTED__CALLBACK_STATUS__WRONG_HAL_RESPONSE;

        verify(() -> CarStatsLog.write(
                eq(CarStatsLog.CAR_USER_HAL_MODIFY_USER_RESPONSE_REPORTED),
                anyInt(),
                eq(statusWrongHalResponse),
                eq(statusUnspecified)), timeout(CALLBACK_TIMEOUT));
    }

    @Test
    public void testUserSwitch_OEMRequest_success() throws Exception {
        int requestId = -4;
        int targetUserId = 11;
        HalPropValue propResponse = createPropRequest(SWITCH_USER, requestId,
                SwitchUserMessageType.VEHICLE_REQUEST, new int[]{targetUserId});

        mUserHalService.onHalEvents(Arrays.asList(propResponse));
        waitForHandler();

        verify(mCarUserService).switchAndroidUserFromHal(requestId, targetUserId);

        int switchRequestOem = CarStatsLog
                .CAR_USER_HAL_MODIFY_USER_REQUEST_REPORTED__REQUEST_TYPE__SWITCH_REQUEST_OEM;

        verify(() -> CarStatsLog.write(
                eq(CarStatsLog.CAR_USER_HAL_MODIFY_USER_REQUEST_REPORTED),
                /* requestId= */ anyInt(),
                eq(switchRequestOem),
                /* current user id= */ eq(-1),
                /* current user flag= */ eq(-1),
                /* targetUserId= */ eq(11),
                /* target user flag= */ eq(-1),
                /* timeout_ms= */ eq(-1)));
    }

    @Test
    public void testUserSwitch_OEMRequest_failure_positiveRequestId() throws Exception {
        int requestId = 4;
        int targetUserId = 11;
        HalPropValue propResponse = createPropRequest(SWITCH_USER, requestId,
                SwitchUserMessageType.VEHICLE_REQUEST, new int[]{targetUserId});

        mUserHalService.onHalEvents(Arrays.asList(propResponse));
        waitForHandler();

        verify(mCarUserService, never()).switchAndroidUserFromHal(anyInt(), anyInt());
    }

    @Test
    public void testPostSwitchResponse_noHalSupported() {
        // Cannot use mUserHalService because it's already set with supported properties
        UserHalService myHalService = new UserHalService(mVehicleHal);

        assertThrows(IllegalStateException.class,
                () -> myHalService.postSwitchResponse(UserHalHelper.emptySwitchUserRequest()));
    }

    @Test
    public void testPostSwitchResponse_nullRequest() {
        assertThrows(NullPointerException.class, () -> mUserHalService.postSwitchResponse(null));
    }

    @Test
    public void testPostSwitchResponse_noUsersInfo() {
        SwitchUserRequest request = createUserSwitchRequest(mUser100, null);
        request.requestId = 42;
        assertThrows(IllegalArgumentException.class,
                () -> mUserHalService.postSwitchResponse(request));
    }

    @Test
    public void testPostSwitchResponse_HalCalledWithCorrectProp() {
        SwitchUserRequest request = createUserSwitchRequest(mUser100, mUsersInfo);
        request.requestId = 42;
        mUserHalService.postSwitchResponse(request);
        ArgumentCaptor<HalPropValue> propCaptor =
                ArgumentCaptor.forClass(HalPropValue.class);
        verify(mVehicleHal).set(propCaptor.capture());
        HalPropValue prop = propCaptor.getValue();
        assertHalSetSwitchUserRequest(prop, SwitchUserMessageType.ANDROID_POST_SWITCH, mUser100);
    }

    @Test
    public void testLegacyUserSwitch_nullRequest() {
        assertThrows(NullPointerException.class, () -> mUserHalService.legacyUserSwitch(null));
    }

    @Test
    public void testLegacyUserSwitch_noMessageType() {
        SwitchUserRequest request = UserHalHelper.emptySwitchUserRequest();

        assertThrows(IllegalArgumentException.class,
                () -> mUserHalService.legacyUserSwitch(request));
    }

    @Test
    public void testLegacyUserSwitch_noTargetUserInfo() {
        SwitchUserRequest request = UserHalHelper.emptySwitchUserRequest();
        request.messageType = SwitchUserMessageType.ANDROID_SWITCH;

        assertThrows(IllegalArgumentException.class,
                () -> mUserHalService.legacyUserSwitch(request));
    }

    @Test
    public void testRemoveUser_noHalSupported() {
        // Cannot use mUserHalService because it's already set with supported properties
        UserHalService myHalService = new UserHalService(mVehicleHal);

        assertThrows(IllegalStateException.class,
                () -> myHalService.removeUser(UserHalHelper.emptyRemoveUserRequest()));
    }

    @Test
    public void testRemoveUser_nullRequest() {
        RemoveUserRequest request = null;

        assertThrows(NullPointerException.class,
                () -> mUserHalService.removeUser(request));
    }

    @Test
    public void testRemoveUser_noRequestId() {
        RemoveUserRequest request = UserHalHelper.emptyRemoveUserRequest();

        assertThrows(IllegalArgumentException.class,
                () -> mUserHalService.removeUser(request));
    }

    @Test
    public void testRemoveUser_noRemovedUserInfo() {
        RemoveUserRequest request = UserHalHelper.emptyRemoveUserRequest();
        request.requestId = 1;

        assertThrows(IllegalArgumentException.class,
                () -> mUserHalService.removeUser(request));
    }

    @Test
    public void testRemoveUser_noUsersInfo() {
        RemoveUserRequest request = UserHalHelper.emptyRemoveUserRequest();
        request.requestId = 1;
        request.removedUserInfo = mUser100;

        assertThrows(IllegalArgumentException.class,
                () -> mUserHalService.removeUser(request));
    }

    @Test
    public void testRemoveUser_HalCalledWithCorrectProp() {
        RemoveUserRequest request = UserHalHelper.emptyRemoveUserRequest();
        request.removedUserInfo = mUser100;
        request.usersInfo = mUsersInfo;
        ArgumentCaptor<HalPropValue> propCaptor =
                ArgumentCaptor.forClass(HalPropValue.class);

        mUserHalService.removeUser(request);

        verify(mVehicleHal).set(propCaptor.capture());
        assertHalSetRemoveUserRequest(propCaptor.getValue(), mUser100);
    }

    @Test
    public void testLegacyUserSwitch_noHalSupported() {
        // Cannot use mUserHalService because it's already set with supported properties
        UserHalService myHalService = new UserHalService(mVehicleHal);

        assertThrows(IllegalStateException.class,
                () -> myHalService.legacyUserSwitch(UserHalHelper.emptySwitchUserRequest()));
    }

    @Test
    public void testLegacyUserSwitch_noUsersInfo() {
        SwitchUserRequest request = UserHalHelper.emptySwitchUserRequest();
        request.messageType = SwitchUserMessageType.ANDROID_SWITCH;
        request.targetUser = mUser100;

        assertThrows(IllegalArgumentException.class,
                () -> mUserHalService.legacyUserSwitch(request));
    }

    @Test
    public void testLegacyUserSwitch_HalCalledWithCorrectProp() {
        SwitchUserRequest request = UserHalHelper.emptySwitchUserRequest();
        request.messageType = SwitchUserMessageType.LEGACY_ANDROID_SWITCH;
        request.targetUser = mUser100;
        request.usersInfo = mUsersInfo;

        mUserHalService.legacyUserSwitch(request);
        ArgumentCaptor<HalPropValue> propCaptor =
                ArgumentCaptor.forClass(HalPropValue.class);
        verify(mVehicleHal).set(propCaptor.capture());
        HalPropValue prop = propCaptor.getValue();
        assertHalSetSwitchUserRequest(prop, SwitchUserMessageType.LEGACY_ANDROID_SWITCH,
                mUser100);
    }

    @Test
    public void testCreateUser_noHalSupported() {
        // Cannot use mUserHalService because it's already set with supported properties
        UserHalService myHalService = new UserHalService(mVehicleHal);

        assertThrows(IllegalStateException.class,
                () -> myHalService.createUser(UserHalHelper.emptyCreateUserRequest(),
                        HAL_TIMEOUT_MS, noOpCallback()));
    }

    @Test
    public void testCreateUser_noRequest() {
        assertThrows(NullPointerException.class, () -> mUserHalService
                .createUser(null, HAL_TIMEOUT_MS, noOpCallback()));
    }

    @Test
    public void testCreateUser_invalidTimeout() {
        assertThrows(IllegalArgumentException.class, () -> mUserHalService
                .createUser(UserHalHelper.emptyCreateUserRequest(), 0, noOpCallback()));
        assertThrows(IllegalArgumentException.class, () -> mUserHalService
                .createUser(UserHalHelper.emptyCreateUserRequest(), -1, noOpCallback()));
    }

    @Test
    public void testCreateUser_noCallback() {
        CreateUserRequest request = UserHalHelper.emptyCreateUserRequest();
        request.newUserInfo.userId = 10;
        request.usersInfo.existingUsers = new UserInfo[]{request.newUserInfo};

        assertThrows(NullPointerException.class, () -> mUserHalService
                .createUser(request, HAL_TIMEOUT_MS, null));
    }

    /**
     * Creates a valid {@link CreateUserRequest} for tests that doesn't check its contents.
     */
    @NonNull
    private CreateUserRequest newValidCreateUserRequest() {
        CreateUserRequest request = UserHalHelper.emptyCreateUserRequest();
        request.newUserInfo = mUser100;
        request.usersInfo = mUsersInfo;
        return request;
    }

    @Test
    public void testCreateUser_halSetTimedOut() throws Exception {
        replySetPropertyWithTimeoutException(CREATE_USER);

        GenericHalCallback<CreateUserResponse> callback = new GenericHalCallback<>(
                CALLBACK_TIMEOUT);
        mUserHalService.createUser(newValidCreateUserRequest(), HAL_TIMEOUT_FOR_NEGATIVE_TESTS_MS,
                callback);

        callback.assertCalled();
        assertCallbackStatus(callback, HalCallback.STATUS_HAL_SET_TIMEOUT);
        assertThat(callback.response).isNull();

        // Make sure the pending request was removed
        SystemClock.sleep(WAITING_TIME_FOR_NEGATIVE_TESTS_MS);
        callback.assertNotCalledAgain();
    }

    @Test
    public void testCreateUser_halDidNotReply() throws Exception {
        GenericHalCallback<CreateUserResponse> callback = new GenericHalCallback<>(
                CALLBACK_TIMEOUT);
        mUserHalService.createUser(newValidCreateUserRequest(), HAL_TIMEOUT_FOR_NEGATIVE_TESTS_MS,
                callback);

        callback.assertCalled();
        assertCallbackStatus(callback, HalCallback.STATUS_HAL_RESPONSE_TIMEOUT);
        assertThat(callback.response).isNull();
    }

    @Test
    public void testCreateUser_halReplyWithWrongRequestId() throws Exception {
        HalPropValue propResponse = createPropRequest(CREATE_USER, REQUEST_ID_PLACE_HOLDER);

        replySetPropertyWithOnChangeEvent(CREATE_USER, propResponse,
                /* rightRequestId= */ false);

        GenericHalCallback<CreateUserResponse> callback = new GenericHalCallback<>(
                CALLBACK_TIMEOUT);
        mUserHalService.createUser(newValidCreateUserRequest(), HAL_TIMEOUT_FOR_NEGATIVE_TESTS_MS,
                callback);

        callback.assertCalled();
        assertCallbackStatus(callback, HalCallback.STATUS_HAL_RESPONSE_TIMEOUT);
        assertThat(callback.response).isNull();
    }

    @Test
    public void testCreateUser_success() throws Exception {
        HalPropValue propResponse = createPropRequest(CREATE_USER, REQUEST_ID_PLACE_HOLDER,
                new int[]{CreateUserStatus.SUCCESS});

        AtomicReference<HalPropValue> reqCaptor = replySetPropertyWithOnChangeEvent(
                CREATE_USER, propResponse, /* rightRequestId= */ true);

        CreateUserRequest request = UserHalHelper.emptyCreateUserRequest();
        request.newUserInfo = mUser100;
        request.usersInfo = mUsersInfo;
        GenericHalCallback<CreateUserResponse> callback = new GenericHalCallback<>(
                CALLBACK_TIMEOUT);
        mUserHalService.createUser(request, HAL_TIMEOUT_MS, callback);

        callback.assertCalled();

        // Make sure the arguments were properly converted
        assertHalSetCreateUserRequest(reqCaptor.get(), request);

        // Assert response
        assertCallbackStatus(callback, HalCallback.STATUS_OK);
        CreateUserResponse actualResponse = callback.response;
        assertThat(actualResponse.status).isEqualTo(CreateUserStatus.SUCCESS);
        assertThat(actualResponse.errorMessage).isEmpty();

        int createRequest = CarStatsLog
                .CAR_USER_HAL_MODIFY_USER_REQUEST_REPORTED__REQUEST_TYPE__CREATE_REQUEST;
        int statusOk = CarStatsLog.CAR_USER_HAL_MODIFY_USER_RESPONSE_REPORTED__CALLBACK_STATUS__OK;
        int statusSuccess = CarStatsLog
                .CAR_USER_HAL_MODIFY_USER_RESPONSE_REPORTED__REQUEST_STATUS__SUCCESS;
        verify(() -> CarStatsLog.write(eq(CarStatsLog.CAR_USER_HAL_MODIFY_USER_REQUEST_REPORTED),
                anyInt(), eq(createRequest), eq(0), eq(100), eq(100), eq(110),
                eq(HAL_TIMEOUT_MS)));
        verify(() -> CarStatsLog.write(eq(CarStatsLog.CAR_USER_HAL_MODIFY_USER_RESPONSE_REPORTED),
                anyInt(), eq(statusOk), eq(statusSuccess)), timeout(CALLBACK_TIMEOUT));
    }

    @Test
    public void testCreateUser_failure() throws Exception {
        HalPropValue propResponse = createPropRequest(CREATE_USER, REQUEST_ID_PLACE_HOLDER,
                /* requestType= */ null,  new int[]{CreateUserStatus.FAILURE}, "D'OH!");

        AtomicReference<HalPropValue> reqCaptor = replySetPropertyWithOnChangeEvent(
                CREATE_USER, propResponse, /* rightRequestId= */ true);

        CreateUserRequest request = UserHalHelper.emptyCreateUserRequest();
        request.newUserInfo = mUser100;
        request.usersInfo = mUsersInfo;
        GenericHalCallback<CreateUserResponse> callback = new GenericHalCallback<>(
                CALLBACK_TIMEOUT);
        mUserHalService.createUser(request, HAL_TIMEOUT_MS, callback);

        callback.assertCalled();

        // Make sure the arguments were properly converted
        assertHalSetCreateUserRequest(reqCaptor.get(), request);

        // Assert response
        assertCallbackStatus(callback, HalCallback.STATUS_OK);
        CreateUserResponse actualResponse = callback.response;
        assertThat(actualResponse.status).isEqualTo(CreateUserStatus.FAILURE);
        assertThat(actualResponse.errorMessage).isEqualTo("D'OH!");

        int createRequest = CarStatsLog
                .CAR_USER_HAL_MODIFY_USER_REQUEST_REPORTED__REQUEST_TYPE__CREATE_REQUEST;
        int statusOk = CarStatsLog.CAR_USER_HAL_MODIFY_USER_RESPONSE_REPORTED__CALLBACK_STATUS__OK;
        int statusFailure = CarStatsLog
                .CAR_USER_HAL_MODIFY_USER_RESPONSE_REPORTED__REQUEST_STATUS__FAILURE;
        verify(() -> CarStatsLog.write(eq(CarStatsLog.CAR_USER_HAL_MODIFY_USER_REQUEST_REPORTED),
                anyInt(), eq(createRequest), eq(0), eq(100), eq(100), eq(110),
                eq(HAL_TIMEOUT_MS)));
        verify(() -> CarStatsLog.write(eq(CarStatsLog.CAR_USER_HAL_MODIFY_USER_RESPONSE_REPORTED),
                anyInt(), eq(statusOk), eq(statusFailure)), timeout(CALLBACK_TIMEOUT));
    }

    @Test
    public void testCreateUser_secondCallFailWhilePending() throws Exception {
        GenericHalCallback<CreateUserResponse> callback1 = new GenericHalCallback<>(
                CALLBACK_TIMEOUT);
        GenericHalCallback<CreateUserResponse> callback2 = new GenericHalCallback<>(
                CALLBACK_TIMEOUT);
        mUserHalService.createUser(newValidCreateUserRequest(), HAL_TIMEOUT_FOR_NEGATIVE_TESTS_MS,
                callback1);
        mUserHalService.createUser(newValidCreateUserRequest(), HAL_TIMEOUT_MS, callback2);

        callback1.assertCalled();
        assertCallbackStatus(callback1, HalCallback.STATUS_HAL_RESPONSE_TIMEOUT);
        assertThat(callback1.response).isNull();

        callback2.assertCalled();
        assertCallbackStatus(callback2, HalCallback.STATUS_CONCURRENT_OPERATION);
        assertThat(callback1.response).isNull();
    }

    @Test
    public void testCreateUser_halReturnedInvalidStatus() throws Exception {
        HalPropValue propResponse = createPropRequest(CREATE_USER, REQUEST_ID_PLACE_HOLDER,
                new int[]{/*status =*/ -1});

        replySetPropertyWithOnChangeEvent(CREATE_USER, propResponse, /* rightRequestId= */ true);

        GenericHalCallback<CreateUserResponse> callback = new GenericHalCallback<>(
                CALLBACK_TIMEOUT);
        mUserHalService.createUser(newValidCreateUserRequest(), HAL_TIMEOUT_MS, callback);

        callback.assertCalled();

        // Assert response
        assertCallbackStatus(callback, HalCallback.STATUS_WRONG_HAL_RESPONSE);
        assertThat(callback.response).isNull();
    }

    @Test
    public void testGetUserAssociation_noHalSupported() {
        // Cannot use mUserHalService because it's already set with supported properties
        UserHalService myHalService = new UserHalService(mVehicleHal);

        assertThrows(IllegalStateException.class,
                () -> myHalService.getUserAssociation(new UserIdentificationGetRequest()));
    }

    @Test
    public void testGetUserAssociation_nullRequest() {
        assertThrows(NullPointerException.class, () -> mUserHalService.getUserAssociation(null));
    }

    @Test
    public void testGetUserAssociation_requestWithDuplicatedTypes() {
        UserIdentificationGetRequest request = new UserIdentificationGetRequest();
        request.numberAssociationTypes = 2;
        request.associationTypes = new int[]{KEY_FOB, KEY_FOB};

        assertThrows(IllegalArgumentException.class,
                () -> mUserHalService.getUserAssociation(request));
    }

    @Test
    public void testGetUserAssociation_invalidResponse() {
        HalPropValue propResponse = mPropValueBuilder.build(USER_IDENTIFICATION_ASSOCIATION,
                /* areaId= */ 0,
                new int[]{
                    DEFAULT_REQUEST_ID,
                    // 1 associations
                    1,
                    // type only, it's missing value
                    KEY_FOB});

        UserIdentificationGetRequest request = replyToValidGetUserIdentificationRequest(
                propResponse);

        assertThat(mUserHalService.getUserAssociation(request)).isNull();
    }

    @Test
    public void testGetUserAssociation_nullResponse() {
        UserIdentificationGetRequest request = replyToValidGetUserIdentificationRequest(null);

        assertThat(mUserHalService.getUserAssociation(request)).isNull();

        verifyValidGetUserIdentificationRequestMade();
    }

    @Test
    public void testGetUserAssociation_ServiceExceptionFromHal() {
        UserIdentificationGetRequest request =
                replyToValidGetUserIdentificationRequestWithException(
                        new ServiceSpecificException(VehicleHalStatusCode.STATUS_TRY_AGAIN));

        assertThat(mUserHalService.getUserAssociation(request)).isNull();

        verifyValidGetUserIdentificationRequestMade();
    }

    @Test
    public void testGetUserAssociation_wrongNumberOfAssociationsOnResponse() {
        HalPropValue propResponse = mPropValueBuilder.build(USER_IDENTIFICATION_ASSOCIATION,
                /* areaId= */ 0,
                new int[]{
                    DEFAULT_REQUEST_ID,
                    // 2 associations
                    2,
                    KEY_FOB,
                    ASSOCIATED_CURRENT_USER,
                    CUSTOM_1,
                    ASSOCIATED_CURRENT_USER
                });
        UserIdentificationGetRequest request = replyToValidGetUserIdentificationRequest(
                propResponse);

        assertThat(mUserHalService.getUserAssociation(request)).isNull();

        verifyValidGetUserIdentificationRequestMade();
    }

    @Test
    public void testGetUserAssociation_typesOnResponseMismatchTypesOnRequest() {
        HalPropValue propResponse = mPropValueBuilder.build(USER_IDENTIFICATION_ASSOCIATION,
                /* areaId= */ 0,
                new int[]{
                    DEFAULT_REQUEST_ID,
                    // 1 association
                    1,
                    CUSTOM_1,
                    ASSOCIATED_CURRENT_USER
                });
        UserIdentificationGetRequest request = replyToValidGetUserIdentificationRequest(
                propResponse);

        assertThat(mUserHalService.getUserAssociation(request)).isNull();

        verifyValidGetUserIdentificationRequestMade();
    }

    @Test
    public void testGetUserAssociation_requestIdMismatch() {
        HalPropValue propResponse = mPropValueBuilder.build(USER_IDENTIFICATION_ASSOCIATION,
                /* areaId= */ 0,
                new int[]{
                    DEFAULT_REQUEST_ID + 1,
                    // 1 association
                    1,
                    KEY_FOB,
                    ASSOCIATED_CURRENT_USER
                });
        UserIdentificationGetRequest request = replyToValidGetUserIdentificationRequest(
                propResponse);

        assertThat(mUserHalService.getUserAssociation(request)).isNull();

        verifyValidGetUserIdentificationRequestMade();
    }

    @Test
    public void testGetUserAssociation_ok() {
        HalPropValue propResponse = mPropValueBuilder.build(USER_IDENTIFICATION_ASSOCIATION,
                /* areaId= */ 0,
                new int[]{
                    DEFAULT_REQUEST_ID,
                    // 1 association
                    1,
                    KEY_FOB,
                    ASSOCIATED_CURRENT_USER
                });
        UserIdentificationGetRequest request = replyToValidGetUserIdentificationRequest(
                propResponse);

        UserIdentificationResponse response = mUserHalService.getUserAssociation(request);

        assertThat(response.requestId).isEqualTo(DEFAULT_REQUEST_ID);
        assertThat(response.numberAssociation).isEqualTo(1);
        assertThat(response.associations.length).isEqualTo(1);
        UserIdentificationAssociation actualAssociation = response.associations[0];
        assertThat(actualAssociation.type).isEqualTo(KEY_FOB);
        assertThat(actualAssociation.value).isEqualTo(ASSOCIATED_CURRENT_USER);
    }

    @Test
    public void testSetUserAssociation_noHalSupported() {
        // Cannot use mUserHalService because it's already set with supported properties
        UserHalService myHalService = new UserHalService(mVehicleHal);

        assertThrows(IllegalStateException.class,
                () -> myHalService.setUserAssociation(HAL_TIMEOUT_MS,
                        UserHalHelper.emptyUserIdentificationSetRequest(), noOpCallback()));
    }

    @Test
    public void testSetUserAssociation_invalidTimeout() {
        UserIdentificationSetRequest request = UserHalHelper.emptyUserIdentificationSetRequest();
        assertThrows(IllegalArgumentException.class, () ->
                mUserHalService.setUserAssociation(0, request, noOpCallback()));
        assertThrows(IllegalArgumentException.class, () ->
                mUserHalService.setUserAssociation(-1, request, noOpCallback()));
    }

    @Test
    public void testSetUserAssociation_nullRequest() {
        assertThrows(NullPointerException.class, () ->
                mUserHalService.setUserAssociation(HAL_TIMEOUT_MS, null, noOpCallback()));
    }

    @Test
    public void testSetUserAssociation_nullCallback() {
        UserIdentificationSetRequest request = UserHalHelper.emptyUserIdentificationSetRequest();
        assertThrows(NullPointerException.class, () ->
                mUserHalService.setUserAssociation(HAL_TIMEOUT_MS, request, null));
    }

    @Test
    public void testSetUserAssociation_requestWithDuplicatedTypes() {
        UserIdentificationSetRequest request = UserHalHelper.emptyUserIdentificationSetRequest();
        request.numberAssociations = 2;
        UserIdentificationSetAssociation association1 = new UserIdentificationSetAssociation();
        association1.type = KEY_FOB;
        association1.value = ASSOCIATE_CURRENT_USER;
        request.associations = new UserIdentificationSetAssociation[]{association1, association1};

        assertThrows(IllegalArgumentException.class, () ->
                mUserHalService.setUserAssociation(HAL_TIMEOUT_MS, request, noOpCallback()));
    }

    @Test
    public void testSetUserAssociation_halSetTimedOut() throws Exception {
        UserIdentificationSetRequest request = validUserIdentificationSetRequest();
        GenericHalCallback<UserIdentificationResponse> callback = new GenericHalCallback<>(
                CALLBACK_TIMEOUT);
        replySetPropertyWithTimeoutException(USER_IDENTIFICATION_ASSOCIATION);

        mUserHalService.setUserAssociation(HAL_TIMEOUT_FOR_NEGATIVE_TESTS_MS, request, callback);

        callback.assertCalled();
        assertCallbackStatus(callback, HalCallback.STATUS_HAL_SET_TIMEOUT);
        assertThat(callback.response).isNull();

        // Make sure the pending request was removed
        SystemClock.sleep(WAITING_TIME_FOR_NEGATIVE_TESTS_MS);
        callback.assertNotCalledAgain();
    }

    @Test
    public void testSetUserAssociation_halDidNotReply() throws Exception {
        UserIdentificationSetRequest request = validUserIdentificationSetRequest();
        GenericHalCallback<UserIdentificationResponse> callback = new GenericHalCallback<>(
                CALLBACK_TIMEOUT);

        mUserHalService.setUserAssociation(HAL_TIMEOUT_FOR_NEGATIVE_TESTS_MS, request, callback);

        callback.assertCalled();
        assertCallbackStatus(callback, HalCallback.STATUS_HAL_RESPONSE_TIMEOUT);
        assertThat(callback.response).isNull();
    }

    @Test
    public void testSetUserAssociation_secondCallFailWhilePending() throws Exception {
        UserIdentificationSetRequest request = validUserIdentificationSetRequest();
        GenericHalCallback<UserIdentificationResponse> callback1 = new GenericHalCallback<>(
                CALLBACK_TIMEOUT);
        GenericHalCallback<UserIdentificationResponse> callback2 = new GenericHalCallback<>(
                CALLBACK_TIMEOUT);

        mUserHalService.setUserAssociation(HAL_TIMEOUT_FOR_NEGATIVE_TESTS_MS, request, callback1);
        mUserHalService.setUserAssociation(HAL_TIMEOUT_MS, request, callback2);

        callback1.assertCalled();
        assertCallbackStatus(callback1, HalCallback.STATUS_HAL_RESPONSE_TIMEOUT);
        assertThat(callback1.response).isNull();

        callback2.assertCalled();
        assertCallbackStatus(callback2, HalCallback.STATUS_CONCURRENT_OPERATION);
        assertThat(callback1.response).isNull();
    }

    @Test
    public void testSetUserAssociation_responseWithWrongRequestId() throws Exception {
        HalPropValue propResponse = mPropValueBuilder.build(USER_IDENTIFICATION_ASSOCIATION,
                /* areaId= */ 0, new int[]{
                    DEFAULT_REQUEST_ID,
                    // 1 association
                    1,
                    KEY_FOB,
                    ASSOCIATED_CURRENT_USER
                });

        AtomicReference<HalPropValue> propRequest = replySetPropertyWithOnChangeEvent(
                USER_IDENTIFICATION_ASSOCIATION, propResponse, /* rightRequestId= */ false);
        UserIdentificationSetRequest request = replyToValidSetUserIdentificationRequest();
        GenericHalCallback<UserIdentificationResponse> callback = new GenericHalCallback<>(
                CALLBACK_TIMEOUT);

        mUserHalService.setUserAssociation(HAL_TIMEOUT_FOR_NEGATIVE_TESTS_MS, request, callback);

        // Assert request
        verifyValidSetUserIdentificationRequestMade(propRequest.get());
        // Assert response
        callback.assertCalled();
        assertCallbackStatus(callback, HalCallback.STATUS_HAL_RESPONSE_TIMEOUT);
        assertThat(callback.response).isNull();
        verify(() -> CarStatsLog.write(
                eq(CarStatsLog.CAR_USER_HAL_USER_ASSOCIATION_REQUEST_REPORTED),
                anyInt(),
                eq(CarStatsLog.CAR_USER_HAL_USER_ASSOCIATION_REQUEST_REPORTED__REQUEST_TYPE__SET),
                eq(DEFAULT_USER_ID), eq(DEFAULT_USER_FLAGS), eq(1), anyString(), anyString()));
    }

    @Test
    public void testSetUserAssociation_notEnoughValuesOnResponse() throws Exception {
        // need at least 4: requestId, number associations, type1, value1
        HalPropValue propResponse = mPropValueBuilder.build(USER_IDENTIFICATION_ASSOCIATION,
                /* areaId= */ 0, new int[]{1, 2, 3});

        AtomicReference<HalPropValue> propRequest = replySetPropertyWithOnChangeEvent(
                USER_IDENTIFICATION_ASSOCIATION, propResponse, /* rightRequestId= */ true);
        UserIdentificationSetRequest request = replyToValidSetUserIdentificationRequest();
        GenericHalCallback<UserIdentificationResponse> callback = new GenericHalCallback<>(
                CALLBACK_TIMEOUT);

        mUserHalService.setUserAssociation(HAL_TIMEOUT_MS, request, callback);

        // Assert request
        verifyValidSetUserIdentificationRequestMade(propRequest.get());
        // Assert response
        callback.assertCalled();
        assertCallbackStatus(callback, HalCallback.STATUS_WRONG_HAL_RESPONSE);
        assertThat(callback.response).isNull();

        int wrongHalResponse = CarStatsLog
                .CAR_USER_HAL_MODIFY_USER_RESPONSE_REPORTED__CALLBACK_STATUS__WRONG_HAL_RESPONSE;
        verify(() -> CarStatsLog.write(
                eq(CarStatsLog.CAR_USER_HAL_USER_ASSOCIATION_REQUEST_REPORTED),
                anyInt(),
                eq(CarStatsLog.CAR_USER_HAL_USER_ASSOCIATION_REQUEST_REPORTED__REQUEST_TYPE__SET),
                eq(DEFAULT_USER_ID), eq(DEFAULT_USER_FLAGS), eq(1), anyString(), anyString()));
        verify(() -> CarStatsLog.write(
                eq(CarStatsLog.CAR_USER_HAL_SET_USER_ASSOCIATION_RESPONSE_REPORTED),
                anyInt(), eq(wrongHalResponse), eq(0), eq(""), eq("")), timeout(CALLBACK_TIMEOUT));
    }

    @Test
    public void testSetUserAssociation_wrongNumberOfAssociationsOnResponse() throws Exception {
        HalPropValue propResponse = mPropValueBuilder.build(USER_IDENTIFICATION_ASSOCIATION,
                /* areaId= */ 0, new int[]{
                    DEFAULT_REQUEST_ID,
                    // 2 associations; request is just 1
                    2,
                    KEY_FOB,
                    ASSOCIATED_CURRENT_USER,
                    CUSTOM_1,
                    ASSOCIATED_CURRENT_USER
                });

        AtomicReference<HalPropValue> propRequest = replySetPropertyWithOnChangeEvent(
                USER_IDENTIFICATION_ASSOCIATION, propResponse, /* rightRequestId= */ true);
        UserIdentificationSetRequest request = replyToValidSetUserIdentificationRequest();
        GenericHalCallback<UserIdentificationResponse> callback = new GenericHalCallback<>(
                CALLBACK_TIMEOUT);

        mUserHalService.setUserAssociation(HAL_TIMEOUT_MS, request, callback);

        // Assert request
        verifyValidSetUserIdentificationRequestMade(propRequest.get());
        // Assert response
        callback.assertCalled();
        assertCallbackStatus(callback, HalCallback.STATUS_WRONG_HAL_RESPONSE);
        assertThat(callback.response).isNull();

        int wrongHalResponse = CarStatsLog
                .CAR_USER_HAL_MODIFY_USER_RESPONSE_REPORTED__CALLBACK_STATUS__WRONG_HAL_RESPONSE;
        verify(() -> CarStatsLog.write(
                eq(CarStatsLog.CAR_USER_HAL_USER_ASSOCIATION_REQUEST_REPORTED),
                anyInt(),
                eq(CarStatsLog.CAR_USER_HAL_USER_ASSOCIATION_REQUEST_REPORTED__REQUEST_TYPE__SET),
                eq(DEFAULT_USER_ID), eq(DEFAULT_USER_FLAGS), eq(1), anyString(), anyString()));
        verify(() -> CarStatsLog.write(
                eq(CarStatsLog.CAR_USER_HAL_SET_USER_ASSOCIATION_RESPONSE_REPORTED),
                anyInt(), eq(wrongHalResponse), eq(2), anyString(), anyString()),
                timeout(CALLBACK_TIMEOUT));
    }

    @Test
    public void testSetUserAssociation_typeMismatchOnResponse() throws Exception {
        HalPropValue propResponse = mPropValueBuilder.build(USER_IDENTIFICATION_ASSOCIATION,
                /* areaId= */ 0, new int[]{
                    DEFAULT_REQUEST_ID,
                    // 1 association
                    1,
                    // request is KEY_FOB
                    CUSTOM_1,
                    ASSOCIATED_CURRENT_USER
                });

        AtomicReference<HalPropValue> propRequest = replySetPropertyWithOnChangeEvent(
                USER_IDENTIFICATION_ASSOCIATION, propResponse, /* rightRequestId= */ true);
        UserIdentificationSetRequest request = replyToValidSetUserIdentificationRequest();
        GenericHalCallback<UserIdentificationResponse> callback = new GenericHalCallback<>(
                CALLBACK_TIMEOUT);

        mUserHalService.setUserAssociation(HAL_TIMEOUT_MS, request, callback);

        // Assert request
        verifyValidSetUserIdentificationRequestMade(propRequest.get());
        // Assert response
        callback.assertCalled();
        assertCallbackStatus(callback, HalCallback.STATUS_WRONG_HAL_RESPONSE);
        assertThat(callback.response).isNull();

        int wrongHalResponse = CarStatsLog
                .CAR_USER_HAL_MODIFY_USER_RESPONSE_REPORTED__CALLBACK_STATUS__WRONG_HAL_RESPONSE;
        verify(() -> CarStatsLog.write(
                eq(CarStatsLog.CAR_USER_HAL_USER_ASSOCIATION_REQUEST_REPORTED),
                anyInt(),
                eq(CarStatsLog.CAR_USER_HAL_USER_ASSOCIATION_REQUEST_REPORTED__REQUEST_TYPE__SET),
                eq(DEFAULT_USER_ID), eq(DEFAULT_USER_FLAGS), eq(1), anyString(), anyString()));
        verify(() -> CarStatsLog.write(
                eq(CarStatsLog.CAR_USER_HAL_SET_USER_ASSOCIATION_RESPONSE_REPORTED),
                anyInt(), eq(wrongHalResponse), eq(1), anyString(), anyString()),
                timeout(CALLBACK_TIMEOUT));
    }

    @Test
    public void testSetUserAssociation_ok() throws Exception {
        HalPropValue propResponse = mPropValueBuilder.build(USER_IDENTIFICATION_ASSOCIATION,
                /* areaId= */ 0, new int[]{
                    DEFAULT_REQUEST_ID,
                    // 1 association
                    1,
                    KEY_FOB,
                    ASSOCIATED_CURRENT_USER
                });

        AtomicReference<HalPropValue> propRequest = replySetPropertyWithOnChangeEvent(
                USER_IDENTIFICATION_ASSOCIATION, propResponse, /* rightRequestId= */ true);
        UserIdentificationSetRequest request = replyToValidSetUserIdentificationRequest();
        GenericHalCallback<UserIdentificationResponse> callback = new GenericHalCallback<>(
                CALLBACK_TIMEOUT);

        mUserHalService.setUserAssociation(HAL_TIMEOUT_MS, request, callback);

        // Assert request
        verifyValidSetUserIdentificationRequestMade(propRequest.get());
        // Assert response
        callback.assertCalled();
        assertCallbackStatus(callback, HalCallback.STATUS_OK);

        UserIdentificationResponse actualResponse = callback.response;

        assertThat(actualResponse.requestId).isEqualTo(DEFAULT_REQUEST_ID);
        assertThat(actualResponse.numberAssociation).isEqualTo(1);
        assertThat(actualResponse.associations.length).isEqualTo(1);
        UserIdentificationAssociation actualAssociation = actualResponse.associations[0];
        assertThat(actualAssociation.type).isEqualTo(KEY_FOB);
        assertThat(actualAssociation.value).isEqualTo(ASSOCIATED_CURRENT_USER);

        int statusOk = CarStatsLog
                .CAR_USER_HAL_MODIFY_USER_RESPONSE_REPORTED__CALLBACK_STATUS__OK;
        verify(() -> CarStatsLog.write(
                eq(CarStatsLog.CAR_USER_HAL_USER_ASSOCIATION_REQUEST_REPORTED),
                anyInt(),
                eq(CarStatsLog.CAR_USER_HAL_USER_ASSOCIATION_REQUEST_REPORTED__REQUEST_TYPE__SET),
                eq(DEFAULT_USER_ID), eq(DEFAULT_USER_FLAGS), eq(1), anyString(), anyString()));
        verify(() -> CarStatsLog.write(
                eq(CarStatsLog.CAR_USER_HAL_SET_USER_ASSOCIATION_RESPONSE_REPORTED),
                anyInt(), eq(statusOk), eq(1), anyString(), anyString()),
                timeout(CALLBACK_TIMEOUT));
    }

    /**
     * Asserts the given {@link UsersInfo} is properly represented in the {@link HalPropValue}.
     *
     * @param value property containing the info
     * @param info info to be checked
     * @param initialIndex first index of the info values in the property's {@code int32Values}
     */
    private void assertUsersInfo(HalPropValue value, UsersInfo info, int initialIndex) {
        // TODO: consider using UserHalHelper to convert the property into a specific request,
        // and compare the request's UsersInfo.
        // But such method is not needed in production code yet.
        List<Integer> values = getIntValues(value);
        assertWithMessage("wrong values size").that(values)
                .hasSize(initialIndex + 3 + info.numberUsers * 2);

        int i = initialIndex;
        assertWithMessage("currentUser.id mismatch at index %s", i).that(values.get(i))
                .isEqualTo(info.currentUser.userId);
        i++;
        assertWithMessage("currentUser.flags mismatch at index %s", i).that(values.get(i))
            .isEqualTo(info.currentUser.flags);
        i++;
        assertWithMessage("numberUsers mismatch at index %s", i).that(values.get(i))
            .isEqualTo(info.numberUsers);
        i++;

        for (int j = 0; j < info.numberUsers; j++) {
            int actualUserId = values.get(i++);
            int actualUserFlags = values.get(i++);
            UserInfo expectedUser = info.existingUsers[j];
            assertWithMessage("wrong id for existing user#%s at index %s", j, i)
                .that(actualUserId).isEqualTo(expectedUser.userId);
            assertWithMessage("wrong flags for existing user#%s at index %s", j, i)
                .that(actualUserFlags).isEqualTo(expectedUser.flags);
        }
    }

    /**
     * @see #replySetPropertyWithOnChangeEvent(int, HalPropValue, boolean, int)
     */
    private AtomicReference<HalPropValue> replySetPropertyWithOnChangeEvent(int prop,
            HalPropValue response, boolean rightRequestId) throws Exception {
        return replySetPropertyWithOnChangeEvent(prop, response, rightRequestId,
                /* countDownLatch= */ null);
    }

    /**
     * Sets the VHAL mock to emulate a property change event upon a call to set a property.
     *
     * @param prop prop to be set
     * @param response response to be set on event
     * @param rightRequestId whether the response id should match the request
     * @param countDownLatch A {@link CountDownLatch} to control when to send the event.
     * @return A copy of the update property value.
     *
     * @return reference to the value passed to {@code set()}.
     */
    private AtomicReference<HalPropValue> replySetPropertyWithOnChangeEvent(int prop,
            HalPropValue response, boolean rightRequestId, CountDownLatch latch)
            throws Exception {
        AtomicReference<HalPropValue> ref = new AtomicReference<>();
        doAnswer((inv) -> {
            HalPropValue request = inv.getArgument(0);
            ref.set(request);
            int requestId = request.getInt32Value(0);
            int responseId = rightRequestId ? requestId : requestId + 1000;

            int[] intValues = new int[response.getInt32ValuesSize()];
            for (int i = 0; i < response.getInt32ValuesSize(); i++) {
                if (i == 0) {
                    intValues[i] = responseId;
                    continue;
                }
                intValues[i] = response.getInt32Value(i);
            }
            HalPropValue responseCopy = mPropValueBuilder.build(response.getPropId(),
                    response.getAreaId(), response.getTimestamp(), response.getStatus(), intValues,
                    /*floatValues=*/new float[0], /*int64Values=*/new long[0],
                    response.getStringValue(), /*byteValues=*/new byte[0]);

            if (latch == null) {
                Log.d(TAG, "replySetPropertyWithOnChangeEvent(): resp=" + responseCopy + " for req="
                        + request);
                mUserHalService.onHalEvents(Arrays.asList(responseCopy));
                return null;
            }

            new Thread(() -> {
                JavaMockitoHelper.silentAwait(latch, CALLBACK_TIMEOUT);
                Log.d(TAG, "replySetPropertyWithOnChangeEvent(): resp=" + responseCopy + " for req="
                        + request);
                mUserHalService.onHalEvents(Arrays.asList(responseCopy));
            }, "replySetpropertyWithOnChangeEventThread").start();

            return null;
        }).when(mVehicleHal).set(isProperty(prop));
        return ref;
    }

    /**
     * Sets the VHAL mock to emulate a property timeout exception upon a call to set a property.
     */
    private void replySetPropertyWithTimeoutException(int prop) throws Exception {
        doThrow(new ServiceSpecificException(VehicleHalStatusCode.STATUS_TRY_AGAIN,
                "PropId: 0x" + Integer.toHexString(prop))).when(mVehicleHal)
                .set(isProperty(prop));
    }

    @NonNull
    private SwitchUserRequest createUserSwitchRequest(@NonNull UserInfo targetUser,
            @NonNull UsersInfo usersInfo) {
        SwitchUserRequest request = UserHalHelper.emptySwitchUserRequest();
        request.targetUser = targetUser;
        request.usersInfo = usersInfo;
        return request;
    }

    /**
     * Creates and set expectations for a valid request.
     */
    private UserIdentificationGetRequest replyToValidGetUserIdentificationRequest(
            @NonNull HalPropValue response) {
        mockNextRequestId(DEFAULT_REQUEST_ID);

        UserIdentificationGetRequest request = new UserIdentificationGetRequest();
        request.userInfo = new UserInfo();
        request.userInfo.userId = DEFAULT_USER_ID;
        request.userInfo.flags = DEFAULT_USER_FLAGS;
        request.numberAssociationTypes = 1;
        request.associationTypes = new int[]{KEY_FOB};

        when(mVehicleHal.get(isPropertyWithValues(USER_IDENTIFICATION_ASSOCIATION,
                DEFAULT_REQUEST_ID, DEFAULT_USER_ID, DEFAULT_USER_FLAGS,
                /* numberAssociations= */ 1, KEY_FOB)))
            .thenReturn(response);

        return request;
    }

    private UserIdentificationGetRequest replyToValidGetUserIdentificationRequestWithException(
            Exception e) {
        mockNextRequestId(DEFAULT_REQUEST_ID);

        UserIdentificationGetRequest request = new UserIdentificationGetRequest();
        request.userInfo = new UserInfo();
        request.userInfo.userId = DEFAULT_USER_ID;
        request.userInfo.flags = DEFAULT_USER_FLAGS;
        request.numberAssociationTypes = 1;
        request.associationTypes = new int[]{KEY_FOB};

        when(mVehicleHal.get(isPropertyWithValues(USER_IDENTIFICATION_ASSOCIATION,
                DEFAULT_REQUEST_ID, DEFAULT_USER_ID, DEFAULT_USER_FLAGS,
                /* numberAssociations= */ 1, KEY_FOB))).thenThrow(e);

        return request;
    }

    /**
     * Creates and set expectations for a valid request.
     */
    private UserIdentificationSetRequest replyToValidSetUserIdentificationRequest() {
        mockNextRequestId(DEFAULT_REQUEST_ID);
        return validUserIdentificationSetRequest();
    }

    /**
     * Creates a valid request that can be used in test cases where its content is not asserted.
     */
    private UserIdentificationSetRequest validUserIdentificationSetRequest() {
        UserIdentificationSetRequest request = UserHalHelper.emptyUserIdentificationSetRequest();
        request.userInfo.userId = DEFAULT_USER_ID;
        request.userInfo.flags = DEFAULT_USER_FLAGS;
        request.numberAssociations = 1;
        UserIdentificationSetAssociation association1 = new UserIdentificationSetAssociation();
        association1.type = KEY_FOB;
        association1.value = ASSOCIATE_CURRENT_USER;
        request.associations = new UserIdentificationSetAssociation[]{association1};
        return request;
    }

    /**
     * Run empty runnable to make sure that all posted handlers are done.
     */
    private void waitForHandler() {
        mHandler.runWithScissors(() -> { }, /* Default timeout */ CALLBACK_TIMEOUT);
    }

    private void mockNextRequestId(int requestId) {
        doReturn(requestId).when(mUserHalService).getNextRequestId();
    }

    private void mockUserHalEnabled(@Nullable Boolean enabled) {
        Optional<Boolean> value = enabled != null ? Optional.of(enabled) : Optional.empty();
        doReturn(value).when(() -> CarSystemProperties.getUserHalEnabled());
    }

    private void assertInitialUserInfoSetRequest(HalPropValue req, int requestType) {
        assertThat(req.getInt32Value(1)).isEqualTo(requestType);
        assertUsersInfo(req, mUsersInfo, 2);
    }

    private void assertHalSetSwitchUserRequest(HalPropValue req, int messageType,
            UserInfo targetUserInfo) {
        assertThat(req.getPropId()).isEqualTo(SWITCH_USER);
        assertWithMessage("wrong request Id on %s", req).that(req.getInt32Value(0))
            .isAtLeast(1);
        assertThat(req.getInt32Value(1)).isEqualTo(messageType);
        assertWithMessage("targetuser.id mismatch on %s", req).that(req.getInt32Value(2))
                .isEqualTo(targetUserInfo.userId);
        assertWithMessage("targetuser.flags mismatch on %s", req).that(req.getInt32Value(3))
                .isEqualTo(targetUserInfo.flags);
        assertUsersInfo(req, mUsersInfo, 4);
    }

    private void assertHalSetRemoveUserRequest(HalPropValue req, UserInfo userInfo) {
        assertThat(req.getPropId()).isEqualTo(REMOVE_USER);
        assertWithMessage("wrong request Id on %s", req).that(req.getInt32Value(0))
                .isAtLeast(1);
        assertWithMessage("user.id mismatch on %s", req).that(req.getInt32Value(1))
                .isEqualTo(userInfo.userId);
        assertWithMessage("user.flags mismatch on %s", req).that(req.getInt32Value(2))
                .isEqualTo(userInfo.flags);
        assertUsersInfo(req, mUsersInfo, 3);
    }

    private void assertHalSetCreateUserRequest(HalPropValue prop, CreateUserRequest request) {
        assertThat(prop.getPropId()).isEqualTo(CREATE_USER);
        assertWithMessage("wrong request Id on %s", prop).that(prop.getInt32Value(0))
                .isEqualTo(request.requestId);
        assertWithMessage("newUser.userId mismatch on %s", prop).that(prop.getInt32Value(1))
                .isEqualTo(request.newUserInfo.userId);
        assertWithMessage("newUser.flags mismatch on %s", prop).that(prop.getInt32Value(2))
                .isEqualTo(request.newUserInfo.flags);
        assertUsersInfo(prop, request.usersInfo, 3);
    }

    private void assertCallbackStatus(GenericHalCallback<?> callback, int expectedStatus) {
        int actualStatus = callback.status;
        if (actualStatus == expectedStatus) return;

        fail("Wrong callback status; expected "
                + UserHalHelper.halCallbackStatusToString(expectedStatus) + ", got "
                + UserHalHelper.halCallbackStatusToString(actualStatus));
    }

    /**
     * Verifies {@code hal.get()} was called with the values used on
     * {@link #replyToValidGetUserIdentificationRequest(HalPropValue)}.
     */
    private void verifyValidGetUserIdentificationRequestMade() {
        verify(mVehicleHal).get(isPropertyWithValues(USER_IDENTIFICATION_ASSOCIATION,
                DEFAULT_REQUEST_ID, DEFAULT_USER_ID, DEFAULT_USER_FLAGS,
                /* numberAssociations= */ 1, KEY_FOB));
    }

    /**
     * Verifies {@code hal.set()} was called with the values used on
     * {@link #replyToValidSetUserIdentificationRequest(HalPropValue)}.
     */
    private void verifyValidSetUserIdentificationRequestMade(@NonNull HalPropValue request) {
        assertThat(request.getPropId()).isEqualTo(USER_IDENTIFICATION_ASSOCIATION);

        assertThat(getIntValues(request)).containsExactly(DEFAULT_REQUEST_ID, DEFAULT_USER_ID,
                DEFAULT_USER_FLAGS,
                /* numberAssociations= */ 1, KEY_FOB, ASSOCIATE_CURRENT_USER);
    }

    private HalPropValue createPropRequest(int propId, int requestId) {
        return createPropRequest(propId, requestId, null, new int[0], new String());
    }

    private HalPropValue createPropRequest(int propId, int requestId, int[] intValues) {
        return createPropRequest(propId, requestId, null, intValues, new String());
    }

    private HalPropValue createPropRequest(int propId, int requestId, int requestType) {
        return createPropRequest(propId, requestId, requestType, new int[0], new String());
    }

    private HalPropValue createPropRequest(int propId, int requestId, int requestType,
            int[] intValues) {
        return createPropRequest(propId, requestId, requestType, intValues, new String());
    }

    private HalPropValue createPropRequest(int propId, int requestId, Integer requestType,
            int[] intValues, String stringValue) {
        int intLength = intValues.length + 1;
        if (requestType != null) {
            intLength += 1;
        }
        int[] values = new int[intLength];
        values[0] = requestId;
        int start = 1;

        if (requestType != null) {
            values[1] = requestType;
            start = 2;
        }
        for (int i = 0; i < intValues.length; i++) {
            values[i + start] = intValues[i];
        }
        return mPropValueBuilder.build(propId, /* areaId= */ 0,
                SystemClock.elapsedRealtime(), /* status= */ 0, values,
                new float[0], new long[0], stringValue, new byte[0]);
    }

    private static <T> HalCallback<T> noOpCallback() {
        return (i, r) -> { };
    }

    private static List<Integer> getIntValues(HalPropValue value) {
        ArrayList<Integer> values = new ArrayList<Integer>();
        for (int i = 0; i < value.getInt32ValuesSize(); i++) {
            values.add(value.getInt32Value(i));
        }
        return values;
    }

    private static final class GenericHalCallback<R> implements HalCallback<R> {

        private final CountDownLatch mLatch = new CountDownLatch(1);
        private final int mTimeout;
        private final List<Pair<Integer, R>> mExtraCalls = new ArrayList<>();

        public int status;
        public R response;

        GenericHalCallback(int timeout) {
            this.mTimeout = timeout;
        }

        @Override
        public void onResponse(int status, R response) {
            Log.d(TAG, "onResponse(): status=" + status + ", response=" + response);
            if (mLatch.getCount() == 0) {
                Log.e(TAG, "Already responded");
                mExtraCalls.add(new Pair<>(status, response));
                return;
            }
            this.status = status;
            this.response = response;
            mLatch.countDown();
        }

        /**
         * Asserts that the callback was called, or fail if it timed out.
         */
        public void assertCalled() throws InterruptedException {
            Log.d(TAG, "assertCalled(): waiting " + mTimeout + "ms");
            if (!mLatch.await(mTimeout, TimeUnit.MILLISECONDS)) {
                throw new AssertionError("callback not called in " + mTimeout + "ms");
            }
        }

        /**
         * Asserts that the callback was not called more than once.
         */
        public void assertNotCalledAgain() {
            if (mExtraCalls.isEmpty()) return;
            throw new AssertionError("Called " + mExtraCalls.size() + " times more than expected: "
                    + mExtraCalls);
        }
    }
}
