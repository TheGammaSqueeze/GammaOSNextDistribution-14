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

import static android.car.test.mocks.AndroidMockitoHelper.mockAmGetCurrentUser;
import static android.car.test.mocks.AndroidMockitoHelper.mockUmGetUserHandles;
import static android.hardware.automotive.vehicle.UserIdentificationAssociationSetValue.ASSOCIATE_CURRENT_USER;
import static android.hardware.automotive.vehicle.UserIdentificationAssociationSetValue.DISASSOCIATE_ALL_USERS;
import static android.hardware.automotive.vehicle.UserIdentificationAssociationSetValue.DISASSOCIATE_CURRENT_USER;
import static android.hardware.automotive.vehicle.UserIdentificationAssociationType.CUSTOM_1;
import static android.hardware.automotive.vehicle.UserIdentificationAssociationType.CUSTOM_2;
import static android.hardware.automotive.vehicle.UserIdentificationAssociationType.CUSTOM_3;
import static android.hardware.automotive.vehicle.UserIdentificationAssociationType.CUSTOM_4;
import static android.hardware.automotive.vehicle.UserIdentificationAssociationType.KEY_FOB;
import static android.hardware.automotive.vehicle.UserIdentificationAssociationValue.ASSOCIATED_ANOTHER_USER;
import static android.hardware.automotive.vehicle.UserIdentificationAssociationValue.ASSOCIATED_CURRENT_USER;
import static android.hardware.automotive.vehicle.UserIdentificationAssociationValue.NOT_ASSOCIATED_ANY_USER;
import static android.hardware.automotive.vehicle.UserIdentificationAssociationValue.UNKNOWN;

import static com.android.car.hal.UserHalHelper.CREATE_USER_PROPERTY;
import static com.android.car.hal.UserHalHelper.INITIAL_USER_INFO_PROPERTY;
import static com.android.car.hal.UserHalHelper.REMOVE_USER_PROPERTY;
import static com.android.car.hal.UserHalHelper.SWITCH_USER_PROPERTY;
import static com.android.car.hal.UserHalHelper.USER_IDENTIFICATION_ASSOCIATION_PROPERTY;
import static com.android.car.user.MockedUserHandleBuilder.expectAdminUserExists;
import static com.android.car.user.MockedUserHandleBuilder.expectEphemeralUserExists;
import static com.android.car.user.MockedUserHandleBuilder.expectGuestUserExists;
import static com.android.car.user.MockedUserHandleBuilder.expectRegularUserExists;
import static com.android.car.user.MockedUserHandleBuilder.expectSystemUserExists;
import static com.android.car.user.MockedUserHandleBuilder.expectUserExistsButGettersFail;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

import android.annotation.NonNull;
import android.app.ActivityManager;
import android.car.builtin.os.UserManagerHelper;
import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.hardware.automotive.vehicle.CreateUserRequest;
import android.hardware.automotive.vehicle.InitialUserInfoRequestType;
import android.hardware.automotive.vehicle.InitialUserInfoResponse;
import android.hardware.automotive.vehicle.InitialUserInfoResponseAction;
import android.hardware.automotive.vehicle.RemoveUserRequest;
import android.hardware.automotive.vehicle.SwitchUserMessageType;
import android.hardware.automotive.vehicle.SwitchUserRequest;
import android.hardware.automotive.vehicle.UserIdentificationAssociation;
import android.hardware.automotive.vehicle.UserIdentificationAssociationType;
import android.hardware.automotive.vehicle.UserIdentificationAssociationValue;
import android.hardware.automotive.vehicle.UserIdentificationGetRequest;
import android.hardware.automotive.vehicle.UserIdentificationResponse;
import android.hardware.automotive.vehicle.UserIdentificationSetAssociation;
import android.hardware.automotive.vehicle.UserIdentificationSetRequest;
import android.hardware.automotive.vehicle.UserInfo;
import android.hardware.automotive.vehicle.UsersInfo;
import android.os.UserHandle;
import android.os.UserManager;

import com.android.car.internal.util.DebugUtils;
import com.android.car.user.UserHandleHelper;

import org.junit.Test;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.List;

public final class UserHalHelperTest extends AbstractExtendedMockitoTestCase {

    public UserHalHelperTest() {
        super(UserHalHelper.TAG);
    }

    private HalPropValueBuilder mPropValueBuilder = new HalPropValueBuilder(/*isAidl=*/true);

    @Mock
    private UserManager mUm;

    @Mock
    private UserHandleHelper mUserHandleHelper;

    private static List<Integer> getInt32Values(HalPropValue propValue) {
        int size = propValue.getInt32ValuesSize();
        ArrayList<Integer> intValues = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            intValues.add(propValue.getInt32Value(i));
        }
        return intValues;
    }

    @Override
    protected void onSessionBuilder(CustomMockitoSessionBuilder session) {
        session.spyStatic(ActivityManager.class);
    }

    @Test
    public void testHalCallbackStatusToString() {
        assertThat(UserHalHelper.halCallbackStatusToString(-666)).isNotNull();
    }

    @Test
    public void testParseInitialUserInfoRequestType_valid() {
        assertThat(UserHalHelper.parseInitialUserInfoRequestType("FIRST_BOOT"))
                .isEqualTo(InitialUserInfoRequestType.FIRST_BOOT);
        assertThat(UserHalHelper.parseInitialUserInfoRequestType("COLD_BOOT"))
            .isEqualTo(InitialUserInfoRequestType.COLD_BOOT);
        assertThat(UserHalHelper.parseInitialUserInfoRequestType("FIRST_BOOT_AFTER_OTA"))
            .isEqualTo(InitialUserInfoRequestType.FIRST_BOOT_AFTER_OTA);
        assertThat(UserHalHelper.parseInitialUserInfoRequestType("RESUME"))
            .isEqualTo(InitialUserInfoRequestType.RESUME);
    }

    @Test
    public void testParseInitialUserInfoRequestType_unknown() {
        assertThat(UserHalHelper.parseInitialUserInfoRequestType("666")).isEqualTo(666);
    }

    @Test
    public void testParseInitialUserInfoRequestType_invalid() {
        assertThrows(IllegalArgumentException.class,
                () -> UserHalHelper.parseInitialUserInfoRequestType("NumberNotIAm"));
    }

    @Test
    public void testConvertFlags_nullUser() {
        assertThrows(IllegalArgumentException.class, () -> UserHalHelper.convertFlags(null, null));
    }

    @Test
    public void testConvertFlags() {
        UserHandle user = expectSystemUserExists(mUserHandleHelper, UserHandle.USER_SYSTEM);
        assertConvertFlags(UserInfo.USER_FLAG_SYSTEM, user);

        user = expectRegularUserExists(mUserHandleHelper, 101);
        assertConvertFlags(0, user);

        user = expectAdminUserExists(mUserHandleHelper, 102);
        assertConvertFlags(UserInfo.USER_FLAG_ADMIN, user);

        user = expectEphemeralUserExists(mUserHandleHelper, 103);
        assertConvertFlags(UserInfo.USER_FLAG_EPHEMERAL, user);

        user = expectGuestUserExists(mUserHandleHelper, 104, /* isEphemeral= */ true);
        assertConvertFlags(UserInfo.USER_FLAG_GUEST | UserInfo.USER_FLAG_EPHEMERAL, user);
    }

    @Test
    public void testGetFlags_nullUserManager() {
        assertThrows(IllegalArgumentException.class, () -> UserHalHelper.getFlags(null, 10));
    }

    @Test
    public void testGetFlags_noUser() {
        assertThrows(IllegalArgumentException.class,
                () -> UserHalHelper.getFlags(mUserHandleHelper, 101));
    }

    @Test
    public void testGetFlags_ok() {
        UserHandle user = expectSystemUserExists(mUserHandleHelper, UserHandle.USER_SYSTEM);
        assertGetFlags(UserInfo.USER_FLAG_SYSTEM, user);

        user = expectRegularUserExists(mUserHandleHelper, 101);
        assertGetFlags(0, user);

        user = expectAdminUserExists(mUserHandleHelper, 102);
        assertGetFlags(UserInfo.USER_FLAG_ADMIN, user);

        user = expectEphemeralUserExists(mUserHandleHelper, 103);
        assertGetFlags(UserInfo.USER_FLAG_EPHEMERAL, user);

        user = expectGuestUserExists(mUserHandleHelper, 104, /* isEphemeral= */ true);
        assertGetFlags(UserInfo.USER_FLAG_GUEST | UserInfo.USER_FLAG_EPHEMERAL, user);
    }

    @Test
    public void testIsSystem() {
        assertThat(UserHalHelper.isSystem(UserInfo.USER_FLAG_SYSTEM)).isTrue();
        assertThat(UserHalHelper.isSystem(UserInfo.USER_FLAG_SYSTEM | 666)).isTrue();
        assertThat(UserHalHelper.isSystem(UserInfo.USER_FLAG_GUEST)).isFalse();
    }

    @Test
    public void testIsGuest() {
        assertThat(UserHalHelper.isGuest(UserInfo.USER_FLAG_GUEST)).isTrue();
        assertThat(UserHalHelper.isGuest(UserInfo.USER_FLAG_GUEST | 666)).isTrue();
        assertThat(UserHalHelper.isGuest(UserInfo.USER_FLAG_SYSTEM)).isFalse();
    }

    @Test
    public void testIsEphemeral() {
        assertThat(UserHalHelper.isEphemeral(UserInfo.USER_FLAG_EPHEMERAL)).isTrue();
        assertThat(UserHalHelper.isEphemeral(UserInfo.USER_FLAG_EPHEMERAL | 666)).isTrue();
        assertThat(UserHalHelper.isEphemeral(UserInfo.USER_FLAG_GUEST)).isFalse();
    }

    @Test
    public void testIsAdmin() {
        assertThat(UserHalHelper.isAdmin(UserInfo.USER_FLAG_ADMIN)).isTrue();
        assertThat(UserHalHelper.isAdmin(UserInfo.USER_FLAG_ADMIN | 666)).isTrue();
        assertThat(UserHalHelper.isAdmin(UserInfo.USER_FLAG_GUEST)).isFalse();
    }

    @Test
    public void testIsDisabled() {
        assertThat(UserHalHelper.isDisabled(UserInfo.USER_FLAG_DISABLED)).isTrue();
        assertThat(UserHalHelper.isDisabled(UserInfo.USER_FLAG_DISABLED | 666)).isTrue();
        assertThat(UserHalHelper.isDisabled(UserInfo.USER_FLAG_GUEST)).isFalse();
    }

    @Test
    public void testIsProfile() {
        assertThat(UserHalHelper.isProfile(UserInfo.USER_FLAG_PROFILE)).isTrue();
        assertThat(UserHalHelper.isProfile(UserInfo.USER_FLAG_PROFILE | 666)).isTrue();
        assertThat(UserHalHelper.isProfile(UserInfo.USER_FLAG_GUEST)).isFalse();
    }

    @Test
    public void testToUserInfoFlags() {
        assertThat(UserHalHelper.toUserInfoFlags(0)).isEqualTo(0);
        assertThat(UserHalHelper.toUserInfoFlags(UserInfo.USER_FLAG_EPHEMERAL))
                .isEqualTo(UserManagerHelper.FLAG_EPHEMERAL);
        assertThat(UserHalHelper.toUserInfoFlags(UserInfo.USER_FLAG_ADMIN))
                .isEqualTo(UserManagerHelper.FLAG_ADMIN);
        assertThat(UserHalHelper.toUserInfoFlags(
                UserInfo.USER_FLAG_EPHEMERAL | UserInfo.USER_FLAG_ADMIN))
                .isEqualTo(UserManagerHelper.FLAG_EPHEMERAL | UserManagerHelper.FLAG_ADMIN);

        // test flags that should be ignored
        assertThat(UserHalHelper.toUserInfoFlags(UserInfo.USER_FLAG_SYSTEM)).isEqualTo(0);
        assertThat(UserHalHelper.toUserInfoFlags(UserInfo.USER_FLAG_GUEST)).isEqualTo(0);
        assertThat(UserHalHelper.toUserInfoFlags(1024)).isEqualTo(0);
    }

    private void assertConvertFlags(int expectedFlags, @NonNull UserHandle user) {
        assertWithMessage("flags mismatch: user=%s, flags=%s",
                user, UserHalHelper.userFlagsToString(expectedFlags))
                        .that(UserHalHelper.convertFlags(mUserHandleHelper, user))
                        .isEqualTo(expectedFlags);
    }

    private void assertGetFlags(int expectedFlags, @NonNull UserHandle user) {
        assertWithMessage("flags mismatch: user=%s, flags=%s",
                user, UserHalHelper.userFlagsToString(expectedFlags))
                        .that(UserHalHelper.getFlags(mUserHandleHelper, user.getIdentifier()))
                        .isEqualTo(expectedFlags);
    }

    @Test
    public void testUserFlagsToString() {
        assertThat(UserHalHelper.userFlagsToString(-666)).isNotNull();
    }

    @Test
    public void testAddUsersInfo_nullCurrentUser() {
        ArrayList<Integer> intValues = new ArrayList<>();

        UsersInfo infos = UserHalHelper.emptyUsersInfo();
        infos.currentUser = null;
        assertThrows(NullPointerException.class, () ->
                UserHalHelper.addUsersInfo(intValues, infos));
    }

    @Test
    public void testAddUsersInfo_mismatchNumberUsers() {
        ArrayList<Integer> intValues = new ArrayList<>();

        UsersInfo infos = UserHalHelper.emptyUsersInfo();
        infos.currentUser.userId = 42;
        infos.currentUser.flags = 1;
        infos.numberUsers = 1;
        assertThat(infos.existingUsers).isEmpty();
        assertThrows(IllegalArgumentException.class, () ->
                UserHalHelper.addUsersInfo(intValues, infos));
    }

    @Test
    public void testAddUsersInfo_success() {
        ArrayList<Integer> intValues = new ArrayList<>();

        UsersInfo infos = UserHalHelper.emptyUsersInfo();
        infos.currentUser.userId = 42;
        infos.currentUser.flags = 1;
        infos.numberUsers = 1;

        UserInfo userInfo = new UserInfo();
        userInfo.userId = 43;
        userInfo.flags = 1;
        infos.existingUsers = new UserInfo[]{userInfo};
        UserHalHelper.addUsersInfo(intValues, infos);

        assertThat(intValues)
                .containsExactly(42, 1, 1, 43, 1)
                .inOrder();
    }

    @Test
    public void testAddUserInfo_nullCurrentUser() {
        ArrayList<Integer> intValues = new ArrayList<>();

        assertThrows(NullPointerException.class, () -> UserHalHelper.addUserInfo(intValues, null));
    }

    @Test
    public void testAddUserInfo_success() {
        ArrayList<Integer> intValues = new ArrayList<>();

        UserInfo userInfo = new UserInfo();
        userInfo.userId = 42;
        userInfo.flags = 1;

        UserHalHelper.addUserInfo(intValues, userInfo);

        assertThat(intValues).containsExactly(42, 1).inOrder();
    }

    @Test
    public void testIsValidUserIdentificationAssociationType_valid() {
        assertThat(UserHalHelper.isValidUserIdentificationAssociationType(KEY_FOB)).isTrue();
        assertThat(UserHalHelper.isValidUserIdentificationAssociationType(CUSTOM_1)).isTrue();
        assertThat(UserHalHelper.isValidUserIdentificationAssociationType(CUSTOM_2)).isTrue();
        assertThat(UserHalHelper.isValidUserIdentificationAssociationType(CUSTOM_3)).isTrue();
        assertThat(UserHalHelper.isValidUserIdentificationAssociationType(CUSTOM_4)).isTrue();
    }

    @Test
    public void testIsValidUserIdentificationAssociationType_invalid() {
        assertThat(UserHalHelper.isValidUserIdentificationAssociationType(CUSTOM_4 + 1)).isFalse();
    }

    @Test
    public void testIsValidUserIdentificationAssociationValue_valid() {
        assertThat(UserHalHelper.isValidUserIdentificationAssociationValue(ASSOCIATED_ANOTHER_USER))
                .isTrue();
        assertThat(UserHalHelper.isValidUserIdentificationAssociationValue(ASSOCIATED_CURRENT_USER))
                .isTrue();
        assertThat(UserHalHelper.isValidUserIdentificationAssociationValue(NOT_ASSOCIATED_ANY_USER))
                .isTrue();
        assertThat(UserHalHelper.isValidUserIdentificationAssociationValue(UNKNOWN)).isTrue();
    }

    @Test
    public void testIsValidUserIdentificationAssociationValue_invalid() {
        assertThat(UserHalHelper.isValidUserIdentificationAssociationValue(0)).isFalse();
    }

    @Test
    public void testIsValidUserIdentificationAssociationSetValue_valid() {
        assertThat(UserHalHelper
                .isValidUserIdentificationAssociationSetValue(ASSOCIATE_CURRENT_USER)).isTrue();
        assertThat(UserHalHelper
                .isValidUserIdentificationAssociationSetValue(DISASSOCIATE_CURRENT_USER)).isTrue();
        assertThat(UserHalHelper
                .isValidUserIdentificationAssociationSetValue(DISASSOCIATE_ALL_USERS)).isTrue();
    }

    @Test
    public void testIsValidUserIdentificationAssociationSetValue_invalid() {
        assertThat(UserHalHelper.isValidUserIdentificationAssociationSetValue(0)).isFalse();
    }

    @Test
    public void testUserIdentificationGetRequestToHalPropValue_null() {
        assertThrows(NullPointerException.class,
                () -> UserHalHelper.toHalPropValue(
                        mPropValueBuilder, (UserIdentificationGetRequest) null));
    }

    @Test
    public void testUserIdentificationGetRequestToHalPropValue_nullAssociationTypes() {
        UserIdentificationGetRequest request = UserHalHelper.emptyUserIdentificationGetRequest();
        request.associationTypes = null;

        assertThrows(NullPointerException.class,
                () -> UserHalHelper.toHalPropValue(mPropValueBuilder, request));
    }

    @Test
    public void testUserIdentificationGetRequestToHalPropValue_emptyRequest() {
        UserIdentificationGetRequest request = UserHalHelper.emptyUserIdentificationGetRequest();

        assertThrows(IllegalArgumentException.class,
                () -> UserHalHelper.toHalPropValue(mPropValueBuilder, request));
    }

    @Test
    public void testUserIdentificationGetRequestToHalPropValue_wrongNumberOfAssociations() {
        UserIdentificationGetRequest request = UserHalHelper.emptyUserIdentificationGetRequest();
        request.numberAssociationTypes = 1;

        assertThrows(IllegalArgumentException.class,
                () -> UserHalHelper.toHalPropValue(mPropValueBuilder, request));
    }

    @Test
    public void testUserIdentificationGetRequestToHalPropValue_invalidType() {
        UserIdentificationGetRequest request = UserHalHelper.emptyUserIdentificationGetRequest();
        request.numberAssociationTypes = 1;
        request.associationTypes = new int[]{CUSTOM_4 + 1};

        assertThrows(IllegalArgumentException.class,
                () -> UserHalHelper.toHalPropValue(mPropValueBuilder, request));
    }

    @Test
    public void testUserIdentificationGetRequestToHalPropValue_missingRequestId() {
        UserIdentificationGetRequest request = UserHalHelper.emptyUserIdentificationGetRequest();
        request.userInfo.userId = 42;
        request.userInfo.flags = 108;
        request.numberAssociationTypes = 1;
        request.associationTypes = new int[]{KEY_FOB};

        assertThrows(IllegalArgumentException.class,
                () -> UserHalHelper.toHalPropValue(mPropValueBuilder, request));
    }

    @Test
    public void testUserIdentificationGetRequestToHalPropValue_ok() {
        UserIdentificationGetRequest request = UserHalHelper.emptyUserIdentificationGetRequest();
        request.requestId = 1;
        request.userInfo.userId = 42;
        request.userInfo.flags = 108;
        request.numberAssociationTypes = 2;
        request.associationTypes = new int[]{KEY_FOB, CUSTOM_1};

        HalPropValue propValue = UserHalHelper.toHalPropValue(mPropValueBuilder, request);
        assertWithMessage("wrong prop on %s", propValue).that(propValue.getPropId())
                .isEqualTo(USER_IDENTIFICATION_ASSOCIATION_PROPERTY);
        assertWithMessage("wrong int32values on %s", propValue).that(getInt32Values(propValue))
                .containsExactly(1, 42, 108, 2, KEY_FOB, CUSTOM_1).inOrder();
    }

    @Test
    public void testToUserIdentificationResponse_null() {
        assertThrows(NullPointerException.class,
                () -> UserHalHelper.toUserIdentificationResponse(null));
    }

    @Test
    public void testToUserIdentificationResponse_invalidPropType() {
        HalPropValue prop = mPropValueBuilder.build(/* propId= */ 0, /* areaId= */ 0);

        assertThrows(IllegalArgumentException.class,
                () -> UserHalHelper.toUserIdentificationResponse(prop));
    }

    @Test
    public void testToUserIdentificationResponse_invalidSize() {
        // need at least 4: request_id, number associations, type1, value1
        HalPropValue prop = mPropValueBuilder.build(
                UserHalHelper.USER_IDENTIFICATION_ASSOCIATION_PROPERTY, /* areaId= */ 0,
                new int[]{1, 2, 3});

        assertThrows(IllegalArgumentException.class,
                () -> UserHalHelper.toUserIdentificationResponse(prop));
    }

    @Test
    public void testToUserIdentificationResponse_invalidRequest() {
        HalPropValue prop = mPropValueBuilder.build(
                UserHalHelper.USER_IDENTIFICATION_ASSOCIATION_PROPERTY, /* areaId= */ 0,
                new int[]{0});

        assertThrows(IllegalArgumentException.class,
                () -> UserHalHelper.toUserIdentificationResponse(prop));
    }

    @Test
    public void testToUserIdentificationResponse_invalidType() {
        HalPropValue prop = mPropValueBuilder.build(
                UserHalHelper.USER_IDENTIFICATION_ASSOCIATION_PROPERTY, /* areaId= */ 0,
                new int[]{
                    // request id
                    42,
                    // number of associations
                    1,
                    CUSTOM_4 + 1,
                    ASSOCIATED_ANOTHER_USER});

        assertThrows(IllegalArgumentException.class,
                () -> UserHalHelper.toUserIdentificationResponse(prop));
    }

    @Test
    public void testToUserIdentificationResponse_invalidValue() {
        HalPropValue prop = mPropValueBuilder.build(
                UserHalHelper.USER_IDENTIFICATION_ASSOCIATION_PROPERTY, /* areaId= */ 0,
                new int[]{
                    // request id
                    42,
                    // number of associations
                    1,
                    KEY_FOB,
                    0});

        assertThrows(IllegalArgumentException.class,
                () -> UserHalHelper.toUserIdentificationResponse(prop));
    }

    @Test
    public void testToUserIdentificationResponse_ok() {
        HalPropValue prop = mPropValueBuilder.build(
                UserHalHelper.USER_IDENTIFICATION_ASSOCIATION_PROPERTY, /* areaId= */ 0,
                /* timestamp= */ 0, /* status= */ 0,
                /*int32Values=*/new int[]{
                    // request id
                    42,
                    // number of associations
                    3,
                    KEY_FOB,
                    ASSOCIATED_ANOTHER_USER,
                    CUSTOM_1,
                    ASSOCIATED_CURRENT_USER,
                    CUSTOM_2,
                    NOT_ASSOCIATED_ANY_USER
                }, new float[0], new long[0], "D'OH!", new byte[0]);

        UserIdentificationResponse response = UserHalHelper.toUserIdentificationResponse(prop);

        assertWithMessage("Wrong request id on %s", response)
            .that(response.requestId).isEqualTo(42);
        assertWithMessage("Wrong number of associations on %s", response)
            .that(response.numberAssociation).isEqualTo(3);
        assertAssociation(response, 0, KEY_FOB, ASSOCIATED_ANOTHER_USER);
        assertAssociation(response, 1, CUSTOM_1, ASSOCIATED_CURRENT_USER);
        assertAssociation(response, 2, CUSTOM_2, NOT_ASSOCIATED_ANY_USER);
        assertWithMessage("Wrong error message on %s", response)
            .that(response.errorMessage).isEqualTo("D'OH!");
    }

    @Test
    public void testToInitialUserInfoResponse_null() {
        assertThrows(NullPointerException.class,
                () -> UserHalHelper.toInitialUserInfoResponse(null));
    }

    @Test
    public void testToInitialUserInfoResponse_invalidPropType() {
        HalPropValue prop = mPropValueBuilder.build(/* propId= */ 0, /* areaId= */ 0);
        assertThrows(IllegalArgumentException.class,
                () -> UserHalHelper.toInitialUserInfoResponse(prop));
    }

    @Test
    public void testToInitialUserInfoResponse_invalidSize() {
        //  need at least 2 intValues: request_id, action_type.
        HalPropValue prop = mPropValueBuilder.build(INITIAL_USER_INFO_PROPERTY, /* areaId= */ 0,
                42);

        assertThrows(IllegalArgumentException.class,
                () -> UserHalHelper.toInitialUserInfoResponse(prop));
    }

    @Test
    public void testToInitialUserInfoResponse_invalidRequest() {
        HalPropValue prop = mPropValueBuilder.build(INITIAL_USER_INFO_PROPERTY, /* areaId= */ 0, 0);

        assertThrows(IllegalArgumentException.class,
                () -> UserHalHelper.toInitialUserInfoResponse(prop));
    }

    @Test
    public void testToInitialUserInfoResponse_invalidAction() {
        HalPropValue prop = mPropValueBuilder.build(INITIAL_USER_INFO_PROPERTY, /* areaId= */ 0,
                new int[]{
                    // request id
                    42,
                    // InitialUserInfoResponseAction
                    -1
                });

        assertThrows(IllegalArgumentException.class,
                () -> UserHalHelper.toInitialUserInfoResponse(prop));
    }

    @Test
    public void testToInitialUserInfoResponse_default_ok_noStringValue() {
        HalPropValue prop = mPropValueBuilder.build(INITIAL_USER_INFO_PROPERTY, /* areaId= */ 0,
                new int[]{
                    // request id
                    42,
                    // InitialUserInfoResponseAction
                    InitialUserInfoResponseAction.DEFAULT
                });

        InitialUserInfoResponse response = UserHalHelper.toInitialUserInfoResponse(prop);

        assertThat(response).isNotNull();
        assertThat(response.requestId).isEqualTo(42);
        assertThat(response.action).isEqualTo(InitialUserInfoResponseAction.DEFAULT);
        assertThat(response.userNameToCreate).isEmpty();
        assertThat(response.userToSwitchOrCreate.userId).isEqualTo(UserHandle.USER_NULL);
        assertThat(response.userToSwitchOrCreate.flags).isEqualTo(0);
        assertThat(response.userLocales).isEmpty();
    }

    @Test
    public void testToInitialUserInfoResponse_default_ok_stringValueWithJustSeparator() {
        HalPropValue prop = mPropValueBuilder.build(INITIAL_USER_INFO_PROPERTY, /* areaId= */ 0,
                /* timestamp= */ 0, /* status= */ 0,
                new int[]{
                    // request id
                    42,
                    // InitialUserInfoResponseAction
                    InitialUserInfoResponseAction.DEFAULT
                }, new float[0], new long[0], "||", new byte[0]);

        InitialUserInfoResponse response = UserHalHelper.toInitialUserInfoResponse(prop);

        assertThat(response).isNotNull();
        assertThat(response.requestId).isEqualTo(42);
        assertThat(response.action).isEqualTo(InitialUserInfoResponseAction.DEFAULT);
        assertThat(response.userNameToCreate).isEmpty();
        assertThat(response.userToSwitchOrCreate.userId).isEqualTo(UserHandle.USER_NULL);
        assertThat(response.userToSwitchOrCreate.flags).isEqualTo(0);
        assertThat(response.userLocales).isEmpty();
    }

    @Test
    public void testToInitialUserInfoResponse_default_ok_stringValueWithLocale() {
        HalPropValue prop = mPropValueBuilder.build(INITIAL_USER_INFO_PROPERTY, /* areaId= */ 0,
                /* timestamp= */ 0, /* status= */ 0,
                new int[]{
                    // request id
                    42,
                    // InitialUserInfoResponseAction
                    InitialUserInfoResponseAction.DEFAULT
                }, new float[0], new long[0], "esperanto,klingon", new byte[0]);

        InitialUserInfoResponse response = UserHalHelper.toInitialUserInfoResponse(prop);

        assertThat(response).isNotNull();
        assertThat(response.requestId).isEqualTo(42);
        assertThat(response.action).isEqualTo(InitialUserInfoResponseAction.DEFAULT);
        assertThat(response.userNameToCreate).isEmpty();
        assertThat(response.userToSwitchOrCreate.userId).isEqualTo(UserHandle.USER_NULL);
        assertThat(response.userToSwitchOrCreate.flags).isEqualTo(0);
        assertThat(response.userLocales).isEqualTo("esperanto,klingon");
    }

    @Test
    public void testToInitialUserInfoResponse_default_ok_stringValueWithLocaleWithHalfSeparator() {
        HalPropValue prop = mPropValueBuilder.build(INITIAL_USER_INFO_PROPERTY, /* areaId= */ 0,
                /* timestamp= */ 0, /* status= */ 0,
                new int[]{
                    // request id
                    42,
                    // InitialUserInfoResponseAction
                    InitialUserInfoResponseAction.DEFAULT
                }, new float[0], new long[0], "esperanto|klingon", new byte[0]);

        InitialUserInfoResponse response = UserHalHelper.toInitialUserInfoResponse(prop);

        assertThat(response).isNotNull();
        assertThat(response.requestId).isEqualTo(42);
        assertThat(response.action).isEqualTo(InitialUserInfoResponseAction.DEFAULT);
        assertThat(response.userNameToCreate).isEmpty();
        assertThat(response.userToSwitchOrCreate.userId).isEqualTo(UserHandle.USER_NULL);
        assertThat(response.userToSwitchOrCreate.flags).isEqualTo(0);
        assertThat(response.userLocales).isEqualTo("esperanto|klingon");
    }

    @Test
    public void testToInitialUserInfoResponse_switch_missingUserId() {
        HalPropValue prop = mPropValueBuilder.build(INITIAL_USER_INFO_PROPERTY, /* areaId= */ 0,
                new int[]{
                    // request id
                    42,
                    InitialUserInfoResponseAction.SWITCH
                });

        assertThrows(IllegalArgumentException.class,
                () -> UserHalHelper.toInitialUserInfoResponse(prop));
    }

    @Test
    public void testToInitialUserInfoResponse_switch_ok_noLocale() {
        HalPropValue prop = mPropValueBuilder.build(INITIAL_USER_INFO_PROPERTY, /* areaId= */ 0,
                new int[]{
                    // request id
                    42,
                    InitialUserInfoResponseAction.SWITCH,
                    // user id
                    108,
                    // flags - should be ignored
                    666
                });

        InitialUserInfoResponse response = UserHalHelper.toInitialUserInfoResponse(prop);

        assertThat(response).isNotNull();
        assertThat(response.requestId).isEqualTo(42);
        assertThat(response.action).isEqualTo(InitialUserInfoResponseAction.SWITCH);
        assertThat(response.userNameToCreate).isEmpty();
        assertThat(response.userToSwitchOrCreate.userId).isEqualTo(108);
        assertThat(response.userToSwitchOrCreate.flags).isEqualTo(0);
        assertThat(response.userLocales).isEmpty();
    }

    @Test
    public void testToInitialUserInfoResponse_switch_ok_withLocale() {
        HalPropValue prop = mPropValueBuilder.build(INITIAL_USER_INFO_PROPERTY, /* areaId= */ 0,
                /* timestamp= */ 0, /* status= */ 0,
                new int[]{
                    // request id
                    42,
                    InitialUserInfoResponseAction.SWITCH,
                    // user id
                    108,
                    // flags - should be ignored
                    666
                }, new float[0], new long[0],
                // add some extra | to make sure they're ignored
                "esperanto,klingon|||", new byte[0]);

        InitialUserInfoResponse response = UserHalHelper.toInitialUserInfoResponse(prop);

        assertThat(response).isNotNull();
        assertThat(response.requestId).isEqualTo(42);
        assertThat(response.action).isEqualTo(InitialUserInfoResponseAction.SWITCH);
        assertThat(response.userNameToCreate).isEmpty();
        assertThat(response.userToSwitchOrCreate.userId).isEqualTo(108);
        assertThat(response.userToSwitchOrCreate.flags).isEqualTo(0);
        assertThat(response.userLocales).isEqualTo("esperanto,klingon");
    }

    @Test
    public void testToInitialUserInfoResponse_create_missingUserId() {
        HalPropValue prop = mPropValueBuilder.build(INITIAL_USER_INFO_PROPERTY, /* areaId= */ 0,
                new int[]{
                    // request id
                    42,
                    InitialUserInfoResponseAction.CREATE
                });

        assertThrows(IllegalArgumentException.class,
                () -> UserHalHelper.toInitialUserInfoResponse(prop));
    }

    @Test
    public void testToInitialUserInfoResponse_create_missingFlags() {
        HalPropValue prop = mPropValueBuilder.build(INITIAL_USER_INFO_PROPERTY, /* areaId= */ 0,
                new int[]{
                    // request id
                    42,
                    InitialUserInfoResponseAction.CREATE,
                    // user id
                    108
                });

        assertThrows(IllegalArgumentException.class,
                () -> UserHalHelper.toInitialUserInfoResponse(prop));
    }

    @Test
    public void testToInitialUserInfoResponse_create_ok_noLocale() {
        HalPropValue prop = mPropValueBuilder.build(INITIAL_USER_INFO_PROPERTY, /* areaId= */ 0,
                /* timestamp= */ 0, /* status= */ 0,
                new int[]{
                    // request id
                    42,
                    InitialUserInfoResponseAction.CREATE,
                    // user id - not used
                    666,
                    UserInfo.USER_FLAG_GUEST
                }, new float[0], new long[0], "||ElGuesto", new byte[0]);

        InitialUserInfoResponse response = UserHalHelper.toInitialUserInfoResponse(prop);

        assertThat(response).isNotNull();
        assertThat(response.requestId).isEqualTo(42);
        assertThat(response.action).isEqualTo(InitialUserInfoResponseAction.CREATE);
        assertThat(response.userNameToCreate).isEqualTo("ElGuesto");
        assertThat(response.userToSwitchOrCreate.userId).isEqualTo(UserHandle.USER_NULL);
        assertThat(response.userToSwitchOrCreate.flags).isEqualTo(UserInfo.USER_FLAG_GUEST);
        assertThat(response.userLocales).isEmpty();
    }

    @Test
    public void testToInitialUserInfoResponse_create_ok_withLocale() {
        HalPropValue prop = mPropValueBuilder.build(INITIAL_USER_INFO_PROPERTY, /* areaId= */ 0,
                /* timestamp= */ 0, /* status= */ 0,
                new int[]{
                    // request id
                    42,
                    InitialUserInfoResponseAction.CREATE,
                    // user id - not used
                    666,
                    UserInfo.USER_FLAG_GUEST
                }, new float[0], new long[0], "esperanto,klingon||ElGuesto", new byte[0]);

        InitialUserInfoResponse response = UserHalHelper.toInitialUserInfoResponse(prop);

        assertThat(response).isNotNull();
        assertThat(response.requestId).isEqualTo(42);
        assertThat(response.action).isEqualTo(InitialUserInfoResponseAction.CREATE);
        assertThat(response.userNameToCreate).isEqualTo("ElGuesto");
        assertThat(response.userToSwitchOrCreate.userId).isEqualTo(UserHandle.USER_NULL);
        assertThat(response.userToSwitchOrCreate.flags).isEqualTo(UserInfo.USER_FLAG_GUEST);
        assertThat(response.userLocales).isEqualTo("esperanto,klingon");
    }

    @Test
    public void testToInitialUserInfoResponse_create_ok_nameAndLocaleWithHalfDelimiter() {
        HalPropValue prop = mPropValueBuilder.build(INITIAL_USER_INFO_PROPERTY, /* areaId= */ 0,
                /* timestamp= */ 0, /* status= */ 0,
                new int[]{
                    // request id
                    42,
                    InitialUserInfoResponseAction.CREATE,
                    // user id - not used
                    666,
                    UserInfo.USER_FLAG_GUEST
                }, new float[0], new long[0], "esperanto|klingon||El|Guesto", new byte[0]);

        InitialUserInfoResponse response = UserHalHelper.toInitialUserInfoResponse(prop);

        assertThat(response).isNotNull();
        assertThat(response.requestId).isEqualTo(42);
        assertThat(response.action).isEqualTo(InitialUserInfoResponseAction.CREATE);
        assertThat(response.userNameToCreate).isEqualTo("El|Guesto");
        assertThat(response.userToSwitchOrCreate.userId).isEqualTo(UserHandle.USER_NULL);
        assertThat(response.userToSwitchOrCreate.flags).isEqualTo(UserInfo.USER_FLAG_GUEST);
        assertThat(response.userLocales).isEqualTo("esperanto|klingon");
    }

    @Test
    public void testUserIdentificationSetRequestToHalPropValue_null() {
        assertThrows(NullPointerException.class,
                () -> UserHalHelper.toHalPropValue(
                        mPropValueBuilder, (UserIdentificationSetRequest) null));
    }

    @Test
    public void testUserIdentificationSetRequestToHalPropValue_nullAssociations() {
        UserIdentificationSetRequest request = UserHalHelper.emptyUserIdentificationSetRequest();
        request.associations = null;

        assertThrows(NullPointerException.class,
                () -> UserHalHelper.toHalPropValue(mPropValueBuilder, request));
    }


    @Test
    public void testUserIdentificationSetRequestToHalPropValue_emptyRequest() {
        UserIdentificationSetRequest request = UserHalHelper.emptyUserIdentificationSetRequest();

        assertThrows(IllegalArgumentException.class,
                () -> UserHalHelper.toHalPropValue(mPropValueBuilder, request));
    }

    @Test
    public void testUserIdentificationSetRequestToHalPropValue_wrongNumberOfAssociations() {
        UserIdentificationSetRequest request = UserHalHelper.emptyUserIdentificationSetRequest();
        request.numberAssociations = 1;

        assertThrows(IllegalArgumentException.class,
                () -> UserHalHelper.toHalPropValue(mPropValueBuilder, request));
    }

    @Test
    public void testUserIdentificationSetRequestToHalPropValue_invalidType() {
        UserIdentificationSetRequest request = UserHalHelper.emptyUserIdentificationSetRequest();
        request.numberAssociations = 1;
        UserIdentificationSetAssociation association1 = new UserIdentificationSetAssociation();
        request.associations = new UserIdentificationSetAssociation[]{association1};
        association1.type = CUSTOM_4 + 1;
        association1.value = ASSOCIATE_CURRENT_USER;

        assertThrows(IllegalArgumentException.class,
                () -> UserHalHelper.toHalPropValue(mPropValueBuilder, request));
    }

    @Test
    public void testUserIdentificationSetRequestToHalPropValue_invalidValue() {
        UserIdentificationSetRequest request = UserHalHelper.emptyUserIdentificationSetRequest();
        request.numberAssociations = 1;
        UserIdentificationSetAssociation association1 = new UserIdentificationSetAssociation();
        request.associations = new UserIdentificationSetAssociation[]{association1};
        association1.type = KEY_FOB;
        association1.value = -1;

        assertThrows(IllegalArgumentException.class,
                () -> UserHalHelper.toHalPropValue(mPropValueBuilder, request));
    }

    @Test
    public void testUserIdentificationSetRequestToHalPropValue_missingRequestId() {
        UserIdentificationSetRequest request = UserHalHelper.emptyUserIdentificationSetRequest();
        request.userInfo.userId = 42;
        request.userInfo.flags = 108;
        request.numberAssociations = 1;
        UserIdentificationSetAssociation association1 = new UserIdentificationSetAssociation();
        association1.type = KEY_FOB;
        association1.value = ASSOCIATE_CURRENT_USER;
        request.associations = new UserIdentificationSetAssociation[]{association1};

        assertThrows(IllegalArgumentException.class,
                () -> UserHalHelper.toHalPropValue(mPropValueBuilder, request));
    }

    @Test
    public void testUserIdentificationSetRequestToHalPropValue_ok() {
        UserIdentificationSetRequest request = UserHalHelper.emptyUserIdentificationSetRequest();
        request.requestId = 1;
        request.userInfo.userId = 42;
        request.userInfo.flags = 108;
        request.numberAssociations = 2;
        UserIdentificationSetAssociation association1 = new UserIdentificationSetAssociation();
        association1.type = KEY_FOB;
        association1.value = ASSOCIATE_CURRENT_USER;
        UserIdentificationSetAssociation association2 = new UserIdentificationSetAssociation();
        association2.type = CUSTOM_1;
        association2.value = DISASSOCIATE_CURRENT_USER;
        request.associations = new UserIdentificationSetAssociation[]{association1, association2};

        HalPropValue propValue = UserHalHelper.toHalPropValue(mPropValueBuilder, request);
        assertWithMessage("wrong prop on %s", propValue).that(propValue.getPropId())
                .isEqualTo(USER_IDENTIFICATION_ASSOCIATION_PROPERTY);

        assertWithMessage("wrong int32values on %s", propValue).that(getInt32Values(propValue))
                .containsExactly(1, 42, 108, 2,
                        KEY_FOB, ASSOCIATE_CURRENT_USER,
                        CUSTOM_1, DISASSOCIATE_CURRENT_USER)
                .inOrder();
    }

    @Test
    public void testRemoveUserRequestToHalPropValue_null() {
        assertThrows(NullPointerException.class,
                () -> UserHalHelper.toHalPropValue(mPropValueBuilder, (RemoveUserRequest) null));
    }

    @Test
    public void testRemoveUserRequestToHalPropValue_nullRemovedUserInfo() {
        RemoveUserRequest request = UserHalHelper.emptyRemoveUserRequest();
        request.requestId = 1;
        request.removedUserInfo = null;

        assertThrows(NullPointerException.class,
                () -> UserHalHelper.toHalPropValue(mPropValueBuilder, request));
    }

    @Test
    public void testRemoveUserRequestToHalPropValue_nullUsersInfo() {
        RemoveUserRequest request = UserHalHelper.emptyRemoveUserRequest();
        request.usersInfo = null;

        assertThrows(IllegalArgumentException.class,
                () -> UserHalHelper.toHalPropValue(mPropValueBuilder, request));
    }

    @Test
    public void testRemoveUserRequestToHalPropValue_empty() {
        RemoveUserRequest request = UserHalHelper.emptyRemoveUserRequest();

        assertThrows(IllegalArgumentException.class,
                () -> UserHalHelper.toHalPropValue(mPropValueBuilder, request));
    }

    @Test
    public void testRemoveUserRequestToHalPropValue_missingRequestId() {
        RemoveUserRequest request = UserHalHelper.emptyRemoveUserRequest();
        request.removedUserInfo.userId = 11;
        request.usersInfo.existingUsers = new UserInfo[]{request.removedUserInfo};

        assertThrows(IllegalArgumentException.class,
                () -> UserHalHelper.toHalPropValue(mPropValueBuilder, request));
    }

    @Test
    public void testRemoveUserRequestToHalPropValue_ok() {
        RemoveUserRequest request = UserHalHelper.emptyRemoveUserRequest();
        request.requestId = 42;

        UserInfo user10 = new UserInfo();
        user10.userId = 10;
        user10.flags = UserInfo.USER_FLAG_ADMIN;

        // existing users
        request.usersInfo.numberUsers = 1;
        request.usersInfo.existingUsers = new UserInfo[]{user10};

        // current user
        request.usersInfo.currentUser = user10;
        // user to remove
        request.removedUserInfo = user10;

        HalPropValue propValue = UserHalHelper.toHalPropValue(mPropValueBuilder, request);

        assertWithMessage("wrong prop on %s", propValue).that(propValue.getPropId())
                .isEqualTo(REMOVE_USER_PROPERTY);
        assertWithMessage("wrong int32values on %s", propValue).that(getInt32Values(propValue))
                .containsExactly(42, // request id
                        10, UserInfo.USER_FLAG_ADMIN, // user to remove
                        10, UserInfo.USER_FLAG_ADMIN, // current user
                        1, // number of users
                        10, UserInfo.USER_FLAG_ADMIN  // existing user 1
                        ).inOrder();
    }

    @Test
    public void testCreateUserRequestToHalPropValue_null() {
        assertThrows(NullPointerException.class,
                () -> UserHalHelper.toHalPropValue(mPropValueBuilder, (CreateUserRequest) null));
    }

    @Test
    public void testCreateUserRequestToHalPropValue_nullNewUserInfo() {
        CreateUserRequest request = UserHalHelper.emptyCreateUserRequest();
        request.newUserInfo = null;

        assertThrows(NullPointerException.class,
                () -> UserHalHelper.toHalPropValue(mPropValueBuilder, request));
    }

    @Test
    public void testCreateUserRequestToHalPropValue_nullUsersInfo() {
        CreateUserRequest request = UserHalHelper.emptyCreateUserRequest();
        request.usersInfo = null;

        assertThrows(IllegalArgumentException.class,
                () -> UserHalHelper.toHalPropValue(mPropValueBuilder, request));
    }

    @Test
    public void testCreateUserRequestToHalPropValue_empty() {
        CreateUserRequest request = UserHalHelper.emptyCreateUserRequest();

        assertThrows(IllegalArgumentException.class,
                () -> UserHalHelper.toHalPropValue(mPropValueBuilder, request));
    }

    @Test
    public void testCreateUserRequestToHalPropValue_emptyRequest() {
        CreateUserRequest request = UserHalHelper.emptyCreateUserRequest();

        assertThrows(IllegalArgumentException.class,
                () -> UserHalHelper.toHalPropValue(mPropValueBuilder, request));
    }

    @Test
    public void testCreateUserRequestToHalPropValue_missingRequestId() {
        CreateUserRequest request = UserHalHelper.emptyCreateUserRequest();
        request.newUserInfo = new UserInfo();
        request.newUserInfo.userId = 10;
        request.usersInfo.existingUsers = new UserInfo[]{request.newUserInfo};

        assertThrows(IllegalArgumentException.class,
                () -> UserHalHelper.toHalPropValue(mPropValueBuilder, request));
    }

    @Test
    public void testCreateUserRequestToHalPropValue_nullNewUserName() {
        CreateUserRequest request = UserHalHelper.emptyCreateUserRequest();
        request.requestId = 42;

        request.newUserInfo.userId = 10;
        request.newUserInfo.flags = UserInfo.USER_FLAG_ADMIN;
        request.newUserName = null;

        request.usersInfo.numberUsers = 1;
        request.usersInfo.currentUser.userId = request.newUserInfo.userId;
        request.usersInfo.currentUser.flags = request.newUserInfo.flags;
        request.usersInfo.existingUsers = new UserInfo[]{request.usersInfo.currentUser};

        assertThrows(IllegalArgumentException.class,
                () -> UserHalHelper.toHalPropValue(mPropValueBuilder, request));
    }

    @Test
    public void testCreateUserRequestToHalPropValue_usersInfoDoesNotContainNewUser() {
        CreateUserRequest request = UserHalHelper.emptyCreateUserRequest();
        request.requestId = 42;
        request.newUserInfo.userId = 10;
        UserInfo user = new UserInfo();
        user.userId = 11;
        request.usersInfo.existingUsers = new UserInfo[]{user};

        assertThrows(IllegalArgumentException.class,
                () -> UserHalHelper.toHalPropValue(mPropValueBuilder, request));
    }

    @Test
    public void testCreateUserRequestToHalPropValue_newUserFlagsMismatch() {
        CreateUserRequest request = UserHalHelper.emptyCreateUserRequest();
        request.requestId = 42;
        request.newUserInfo.userId = 10;
        request.newUserInfo.flags = UserInfo.USER_FLAG_ADMIN;
        UserInfo user = new UserInfo();
        user.userId = 10;
        request.newUserInfo.flags = UserInfo.USER_FLAG_SYSTEM;
        request.usersInfo.existingUsers = new UserInfo[]{user};

        assertThrows(IllegalArgumentException.class,
                () -> UserHalHelper.toHalPropValue(mPropValueBuilder, request));
    }

    @Test
    public void testCreateUserRequestToHalPropValue_ok() {
        CreateUserRequest request = UserHalHelper.emptyCreateUserRequest();
        request.requestId = 42;

        UserInfo user10 = new UserInfo();
        user10.userId = 10;
        user10.flags = UserInfo.USER_FLAG_ADMIN;
        UserInfo user11 = new UserInfo();
        user11.userId = 11;
        user11.flags = UserInfo.USER_FLAG_SYSTEM;
        UserInfo user12 = new UserInfo();
        user12.userId = 12;
        user12.flags = UserInfo.USER_FLAG_GUEST;

        // existing users
        request.usersInfo.numberUsers = 3;
        request.usersInfo.existingUsers = new UserInfo[]{user10, user11, user12};

        // current user
        request.usersInfo.currentUser.userId = 12;
        request.usersInfo.currentUser.flags = UserInfo.USER_FLAG_GUEST;

        // new user
        request.newUserInfo.userId = 10;
        request.newUserInfo.flags = UserInfo.USER_FLAG_ADMIN;
        request.newUserName = "Dude";


        HalPropValue propValue = UserHalHelper.toHalPropValue(mPropValueBuilder, request);

        assertWithMessage("wrong prop on %s", propValue).that(propValue.getPropId())
                .isEqualTo(CREATE_USER_PROPERTY);
        assertWithMessage("wrong int32values on %s", propValue).that(getInt32Values(propValue))
                .containsExactly(42, // request id
                        10, UserInfo.USER_FLAG_ADMIN, // new user
                        12, UserInfo.USER_FLAG_GUEST, // current user
                        3, // number of users
                        10, UserInfo.USER_FLAG_ADMIN,  // existing user 1
                        11, UserInfo.USER_FLAG_SYSTEM, // existing user 2
                        12, UserInfo.USER_FLAG_GUEST   // existing user 3
                        ).inOrder();
        assertWithMessage("wrong name %s", propValue).that(propValue.getStringValue())
                .isEqualTo("Dude");
    }

    @Test
    public void testSwitchUserRequestToHalPropValue_null() {
        assertThrows(NullPointerException.class,
                () -> UserHalHelper.toHalPropValue(mPropValueBuilder, (SwitchUserRequest) null));
    }

    @Test
    public void testSwitchUserRequestToHalPropValue_nullTargetUser() {
        SwitchUserRequest request = UserHalHelper.emptySwitchUserRequest();
        request.messageType = 1;
        request.targetUser = null;

        assertThrows(NullPointerException.class,
                () -> UserHalHelper.toHalPropValue(mPropValueBuilder, request));
    }

    @Test
    public void testSwitchUserRequestToHalPropValue_nullUsersInfo() {
        SwitchUserRequest request = UserHalHelper.emptySwitchUserRequest();
        request.usersInfo = null;

        assertThrows(IllegalArgumentException.class,
                () -> UserHalHelper.toHalPropValue(mPropValueBuilder, request));
    }

    @Test
    public void testSwitchUserRequestToHalPropValue_empty() {
        SwitchUserRequest request = UserHalHelper.emptySwitchUserRequest();

        assertThrows(IllegalArgumentException.class,
                () -> UserHalHelper.toHalPropValue(mPropValueBuilder, request));
    }

    @Test
    public void testSwitchUserRequestToHalPropValue_missingMessageType() {
        SwitchUserRequest request = UserHalHelper.emptySwitchUserRequest();
        request.requestId = 42;
        UserInfo user10 = new UserInfo();
        user10.userId = 10;
        request.usersInfo.numberUsers = 1;
        request.usersInfo.existingUsers = new UserInfo[]{user10};
        request.usersInfo.currentUser = user10;
        request.targetUser = user10;

        assertThrows(IllegalArgumentException.class,
                () -> UserHalHelper.toHalPropValue(mPropValueBuilder, request));
    }

    @Test
    public void testSwitchUserRequestToHalPropValue_incorrectMessageType() {
        SwitchUserRequest request = UserHalHelper.emptySwitchUserRequest();
        request.requestId = 42;
        request.messageType = -1;
        UserInfo user10 = new UserInfo();
        user10.userId = 10;
        request.usersInfo.numberUsers = 1;
        request.usersInfo.existingUsers = new UserInfo[]{user10};
        request.usersInfo.currentUser = user10;
        request.targetUser = user10;

        assertThrows(IllegalArgumentException.class,
                () -> UserHalHelper.toHalPropValue(mPropValueBuilder, request));
    }

    @Test
    public void tesSwitchUserRequestToHalPropValue_ok() {
        SwitchUserRequest request = UserHalHelper.emptySwitchUserRequest();
        request.requestId = 42;
        UserInfo user10 = new UserInfo();
        user10.userId = 10;
        user10.flags = UserInfo.USER_FLAG_ADMIN;
        // existing users
        request.usersInfo.numberUsers = 1;
        request.usersInfo.existingUsers = new UserInfo[]{user10};
        // current user
        request.usersInfo.currentUser = user10;
        // user to remove
        request.targetUser = user10;
        request.messageType = SwitchUserMessageType.ANDROID_SWITCH;

        HalPropValue propValue = UserHalHelper.toHalPropValue(mPropValueBuilder, request);

        assertWithMessage("wrong prop on %s", propValue).that(propValue.getPropId())
                .isEqualTo(SWITCH_USER_PROPERTY);
        assertWithMessage("wrong int32values on %s", propValue).that(getInt32Values(propValue))
                .containsExactly(42, // request id
                        SwitchUserMessageType.ANDROID_SWITCH, // message type
                        10, UserInfo.USER_FLAG_ADMIN, // target user
                        10, UserInfo.USER_FLAG_ADMIN, // current user
                        1, // number of users
                        10, UserInfo.USER_FLAG_ADMIN  // existing user 1
                        ).inOrder();
    }

    @Test
    public void testNewUsersInfo_nullUm() {
        assertThrows(IllegalArgumentException.class, () -> UserHalHelper.newUsersInfo(null, null));
        assertThrows(IllegalArgumentException.class, () -> UserHalHelper.newUsersInfo(mUm, null));
    }

    @Test
    public void testNewUsersInfo_nullUsers() {
        UsersInfo usersInfo = UserHalHelper.newUsersInfo(mUm, mUserHandleHelper);

        assertEmptyUsersInfo(usersInfo);
    }

    @Test
    public void testNewUsersInfo_noUsers() {
        mockGetAllUsers(new UserHandle[0]);

        UsersInfo usersInfo = UserHalHelper.newUsersInfo(mUm, mUserHandleHelper);

        assertEmptyUsersInfo(usersInfo);
    }

    @Test
    public void testNewUsersInfo_ok() {
        UserHandle user100 = expectAdminUserExists(mUserHandleHelper, 100);
        UserHandle user200 = expectRegularUserExists(mUserHandleHelper, 200);
        UserHandle user300 = expectRegularUserExists(mUserHandleHelper, 300);

        mockGetAllUsers(user100, user200, user300);
        mockAmGetCurrentUser(300); // just to make sure it's not used

        UsersInfo usersInfo = UserHalHelper.newUsersInfo(mUm, mUserHandleHelper);

        assertThat(usersInfo).isNotNull();
        assertThat(usersInfo.currentUser.userId).isEqualTo(300);
        assertThat(usersInfo.currentUser.flags).isEqualTo(0);

        assertThat(usersInfo.numberUsers).isEqualTo(3);
        assertThat(usersInfo.existingUsers.length).isEqualTo(3);

        assertThat(usersInfo.existingUsers[0].userId).isEqualTo(100);
        assertThat(usersInfo.existingUsers[0].flags).isEqualTo(UserInfo.USER_FLAG_ADMIN);
        assertThat(usersInfo.existingUsers[1].userId).isEqualTo(200);
        assertThat(usersInfo.existingUsers[1].flags).isEqualTo(0);
        assertThat(usersInfo.existingUsers[2].userId).isEqualTo(300);
        assertThat(usersInfo.existingUsers[2].flags).isEqualTo(0);
    }

    @Test
    public void testNewUsersInfo_currentUser_ok() {
        UserHandle user100 = expectAdminUserExists(mUserHandleHelper, 100);
        UserHandle user200 = expectRegularUserExists(mUserHandleHelper, 200);

        mockGetAllUsers(user100, user200);
        mockAmGetCurrentUser(100);

        UsersInfo usersInfo = UserHalHelper.newUsersInfo(mUm, mUserHandleHelper);

        assertThat(usersInfo).isNotNull();
        assertThat(usersInfo.currentUser.userId).isEqualTo(100);
        assertThat(usersInfo.currentUser.flags).isEqualTo(UserInfo.USER_FLAG_ADMIN);

        assertThat(usersInfo.numberUsers).isEqualTo(2);
        assertThat(usersInfo.existingUsers.length).isEqualTo(2);

        assertThat(usersInfo.existingUsers[0].userId).isEqualTo(100);
        assertThat(usersInfo.existingUsers[0].flags).isEqualTo(UserInfo.USER_FLAG_ADMIN);
        assertThat(usersInfo.existingUsers[1].userId).isEqualTo(200);
        assertThat(usersInfo.existingUsers[1].flags).isEqualTo(0);
    }

    @Test
    @ExpectWtf
    public void testNewUsersInfo_noCurrentUser() {
        UserHandle user100 = expectAdminUserExists(mUserHandleHelper, 100);
        UserHandle user200 = expectRegularUserExists(mUserHandleHelper, 200);

        mockGetAllUsers(user100, user200);
        mockAmGetCurrentUser(300);

        UsersInfo usersInfo = UserHalHelper.newUsersInfo(mUm, mUserHandleHelper);

        assertThat(usersInfo).isNotNull();
        assertThat(usersInfo.currentUser.userId).isEqualTo(300);
        assertThat(usersInfo.currentUser.flags).isEqualTo(0);

        assertThat(usersInfo.numberUsers).isEqualTo(2);
        assertThat(usersInfo.existingUsers.length).isEqualTo(2);

        assertThat(usersInfo.existingUsers[0].userId).isEqualTo(100);
        assertThat(usersInfo.existingUsers[0].flags).isEqualTo(UserInfo.USER_FLAG_ADMIN);
        assertThat(usersInfo.existingUsers[1].userId).isEqualTo(200);
        assertThat(usersInfo.existingUsers[1].flags).isEqualTo(0);
    }

    @Test
    public void testNewUsersInfo_flagConversionFails() {
        UserHandle user100 = expectAdminUserExists(mUserHandleHelper, 100);
        UserHandle user200 = expectUserExistsButGettersFail(mUserHandleHelper, 200);
        UserHandle user300 = expectRegularUserExists(mUserHandleHelper, 300);

        mockGetAllUsers(user100, user200, user300);
        mockAmGetCurrentUser(300); // just to make sure it's not used

        UsersInfo usersInfo = UserHalHelper.newUsersInfo(mUm, mUserHandleHelper);

        assertThat(usersInfo).isNotNull();
        assertThat(usersInfo.currentUser.userId).isEqualTo(300);
        assertThat(usersInfo.currentUser.flags).isEqualTo(0);

        assertThat(usersInfo.numberUsers).isEqualTo(2);
        assertThat(usersInfo.existingUsers.length).isEqualTo(2);

        assertThat(usersInfo.existingUsers[0].userId).isEqualTo(100);
        assertThat(usersInfo.existingUsers[0].flags).isEqualTo(UserInfo.USER_FLAG_ADMIN);
        assertThat(usersInfo.existingUsers[1].userId).isEqualTo(300);
        assertThat(usersInfo.existingUsers[1].flags).isEqualTo(0);
    }

    @Test
    public void testCheckValidUsersInfo_null() {
        assertThrows(IllegalArgumentException.class, () -> UserHalHelper.checkValid(null));
    }

    @Test
    public void testCheckValidUsersInfo_nullCurrentUser() {
        UsersInfo usersInfo = UserHalHelper.emptyUsersInfo();
        usersInfo.currentUser = null;
        usersInfo.existingUsers = new UserInfo[0];
        assertThrows(IllegalArgumentException.class, () -> UserHalHelper.checkValid(usersInfo));
    }

    @Test
    public void testCheckValidUsersInfo_nullExistingUsers() {
        UsersInfo usersInfo = UserHalHelper.emptyUsersInfo();
        usersInfo.currentUser = new UserInfo();
        usersInfo.existingUsers = null;
        assertThrows(IllegalArgumentException.class, () -> UserHalHelper.checkValid(usersInfo));
    }

    @Test
    public void testCheckValidUsersInfo_sizeMismatch() {
        UsersInfo usersInfo = UserHalHelper.emptyUsersInfo();
        usersInfo.numberUsers = 1;
        assertThrows(IllegalArgumentException.class, () -> UserHalHelper.checkValid(usersInfo));
    }

    @Test
    public void testCheckValidUsersInfo_currentUserMissing() {
        UsersInfo usersInfo = UserHalHelper.emptyUsersInfo();
        usersInfo.numberUsers = 1;
        usersInfo.currentUser.userId = 10;
        usersInfo.existingUsers = new UserInfo[]{new UserInfo()};

        assertThrows(IllegalArgumentException.class, () -> UserHalHelper.checkValid(usersInfo));
    }

    @Test
    public void testCheckValidUsersInfo_currentUserFlagsMismatch() {
        UsersInfo usersInfo = UserHalHelper.emptyUsersInfo();
        usersInfo.numberUsers = 1;
        usersInfo.currentUser.userId = 10;
        usersInfo.currentUser.flags = UserInfo.USER_FLAG_ADMIN;
        UserInfo currentUser = new UserInfo();
        currentUser.userId = 10;
        currentUser.flags = UserInfo.USER_FLAG_SYSTEM;
        usersInfo.existingUsers = new UserInfo[]{currentUser};

        assertThrows(IllegalArgumentException.class, () -> UserHalHelper.checkValid(usersInfo));
    }

    @Test
    public void testCheckValidUsersInfo_ok() {
        UsersInfo usersInfo = UserHalHelper.emptyUsersInfo();
        usersInfo.numberUsers = 1;
        usersInfo.currentUser.userId = 10;

        UserInfo currentUser = new UserInfo();
        currentUser.userId = 10;
        usersInfo.existingUsers = new UserInfo[]{currentUser};

        UserHalHelper.checkValid(usersInfo);
    }

    private void mockGetAllUsers(@NonNull UserHandle... users) {
        mockUmGetUserHandles(mUm, /* excludeDying= */ false, users);
    }

    private static void assertEmptyUsersInfo(UsersInfo usersInfo) {
        assertThat(usersInfo).isNotNull();
        assertThat(usersInfo.currentUser.userId).isEqualTo(UserHandle.USER_NULL);
        assertThat(usersInfo.currentUser.flags).isEqualTo(0);
        assertThat(usersInfo.numberUsers).isEqualTo(0);
        assertThat(usersInfo.existingUsers).isEmpty();
    }

    private static void assertAssociation(@NonNull UserIdentificationResponse response, int index,
            int expectedType, int expectedValue) {
        UserIdentificationAssociation actualAssociation = response.associations[index];
        if (actualAssociation.type != expectedType) {
            fail("Wrong type for association at index " + index + " on " + response + "; expected "
                    + DebugUtils.constantToString(
                            UserIdentificationAssociationType.class, expectedType)
                    + ", got "
                    + DebugUtils.constantToString(
                            UserIdentificationAssociationType.class, actualAssociation.type));
        }
        if (actualAssociation.type != expectedType) {
            fail("Wrong value for association at index " + index + " on " + response + "; expected "
                    + DebugUtils.constantToString(
                            UserIdentificationAssociationValue.class, expectedValue)
                    + ", got "
                     + DebugUtils.constantToString(
                            UserIdentificationAssociationValue.class, actualAssociation.value));
        }
    }
}
