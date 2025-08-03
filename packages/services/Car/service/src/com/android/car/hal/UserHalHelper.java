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

import static com.android.car.internal.common.CommonConstants.EMPTY_INT_ARRAY;
import static com.android.car.CarServiceUtils.toIntArray;
import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.PRIVATE_CONSTRUCTOR;
import static com.android.internal.util.Preconditions.checkArgument;

import android.annotation.UserIdInt;
import android.app.ActivityManager;
import android.car.builtin.os.UserManagerHelper;
import android.hardware.automotive.vehicle.CreateUserRequest;
import android.hardware.automotive.vehicle.InitialUserInfoRequestType;
import android.hardware.automotive.vehicle.InitialUserInfoResponse;
import android.hardware.automotive.vehicle.InitialUserInfoResponseAction;
import android.hardware.automotive.vehicle.RemoveUserRequest;
import android.hardware.automotive.vehicle.SwitchUserRequest;
import android.hardware.automotive.vehicle.UserIdentificationAssociation;
import android.hardware.automotive.vehicle.UserIdentificationAssociationSetValue;
import android.hardware.automotive.vehicle.UserIdentificationAssociationType;
import android.hardware.automotive.vehicle.UserIdentificationAssociationValue;
import android.hardware.automotive.vehicle.UserIdentificationGetRequest;
import android.hardware.automotive.vehicle.UserIdentificationResponse;
import android.hardware.automotive.vehicle.UserIdentificationSetAssociation;
import android.hardware.automotive.vehicle.UserIdentificationSetRequest;
import android.hardware.automotive.vehicle.UserInfo;
import android.hardware.automotive.vehicle.UsersInfo;
import android.hardware.automotive.vehicle.VehiclePropertyStatus;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.UserManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.car.hal.HalCallback.HalCallbackStatus;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.DebugUtils;
import com.android.car.user.UserHandleHelper;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Provides utility methods for User HAL related functionalities.
 */
public final class UserHalHelper {

    @VisibleForTesting
    static final String TAG = UserHalHelper.class.getSimpleName();

    public static final int INITIAL_USER_INFO_PROPERTY = 299896583;
    public static final int SWITCH_USER_PROPERTY = 299896584;
    public static final int CREATE_USER_PROPERTY = 299896585;
    public static final int REMOVE_USER_PROPERTY = 299896586;
    public static final int USER_IDENTIFICATION_ASSOCIATION_PROPERTY = 299896587;

    private static final boolean DEBUG = false;
    private static final String STRING_SEPARATOR = "\\|\\|";

    /**
     * Gets user-friendly representation of the status.
     */
    public static String halCallbackStatusToString(@HalCallbackStatus int status) {
        switch (status) {
            case HalCallback.STATUS_OK:
                return "OK";
            case HalCallback.STATUS_HAL_SET_TIMEOUT:
                return "HAL_SET_TIMEOUT";
            case HalCallback.STATUS_HAL_RESPONSE_TIMEOUT:
                return "HAL_RESPONSE_TIMEOUT";
            case HalCallback.STATUS_WRONG_HAL_RESPONSE:
                return "WRONG_HAL_RESPONSE";
            case HalCallback.STATUS_CONCURRENT_OPERATION:
                return "CONCURRENT_OPERATION";
            default:
                return "UNKNOWN-" + status;
        }
    }

    /**
     * Converts a string to a {@link InitialUserInfoRequestType}.
     *
     * @return valid type or numeric value if passed "as is"
     *
     * @throws IllegalArgumentException if type is not valid neither a number
     */
    public static int parseInitialUserInfoRequestType(String type) {
        switch(type) {
            case "FIRST_BOOT":
                return InitialUserInfoRequestType.FIRST_BOOT;
            case "FIRST_BOOT_AFTER_OTA":
                return InitialUserInfoRequestType.FIRST_BOOT_AFTER_OTA;
            case "COLD_BOOT":
                return InitialUserInfoRequestType.COLD_BOOT;
            case "RESUME":
                return InitialUserInfoRequestType.RESUME;
            default:
                try {
                    return Integer.parseInt(type);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("invalid type: " + type, e);
                }
        }
    }

    /**
     * Converts Android user flags to HALs.
     */
    public static int convertFlags(UserHandleHelper userHandleHelper, UserHandle user) {
        checkArgument(user != null, "user cannot be null");

        int flags = 0;
        if (user.getIdentifier() == UserHandle.SYSTEM.getIdentifier()) {
            flags |= UserInfo.USER_FLAG_SYSTEM;
        }
        if (userHandleHelper.isAdminUser(user)) {
            flags |= UserInfo.USER_FLAG_ADMIN;
        }
        if (userHandleHelper.isGuestUser(user)) {
            flags |= UserInfo.USER_FLAG_GUEST;
        }
        if (userHandleHelper.isEphemeralUser(user)) {
            flags |= UserInfo.USER_FLAG_EPHEMERAL;
        }
        if (!userHandleHelper.isEnabledUser(user)) {
            flags |= UserInfo.USER_FLAG_DISABLED;
        }
        if (userHandleHelper.isProfileUser(user)) {
            flags |= UserInfo.USER_FLAG_PROFILE;
        }

        return flags;
    }


    /**
     * Converts Android user flags to HALs.
     */
    public static int getFlags(UserHandleHelper userHandleHelper, @UserIdInt int userId) {
        Preconditions.checkArgument(userHandleHelper != null, "UserManager cannot be null");
        UserHandle user = userHandleHelper.getExistingUserHandle(userId);
        Preconditions.checkArgument(user != null, "No user with id %d", userId);
        return convertFlags(userHandleHelper, user);
    }

    /** Checks if a HAL flag contains {@link UserInfo#USER_FLAG_SYSTEM}. */
    public static boolean isSystem(int flags) {
        return (flags & UserInfo.USER_FLAG_SYSTEM) != 0;
    }

    /** Checks if a HAL flag contains {@link UserInfo#USER_FLAG_GUEST}. */
    public static boolean isGuest(int flags) {
        return (flags & UserInfo.USER_FLAG_GUEST) != 0;
    }

    /** Checks if a HAL flag contains {@link UserInfo#USER_FLAG_EPHEMERAL}. */
    public static boolean isEphemeral(int flags) {
        return (flags & UserInfo.USER_FLAG_EPHEMERAL) != 0;
    }

    /** Checks if a HAL flag contains {@link UserInfo#USER_FLAG_ADMIN}. */
    public static boolean isAdmin(int flags) {
        return (flags & UserInfo.USER_FLAG_ADMIN) != 0;
    }

    /** Checks if a HAL flag contains {@link UserInfo#USER_FLAG_DISABLED}. */
    public static boolean isDisabled(int flags) {
        return (flags & UserInfo.USER_FLAG_DISABLED) != 0;
    }

    /** Checks if a HAL flag contains {@link UserInfo#USER_FLAG_PROFILE}. */
    public static boolean isProfile(int flags) {
        return (flags & UserInfo.USER_FLAG_PROFILE) != 0;
    }

    /**
     * Converts HAL flags to Android's.
     */
    public static int toUserInfoFlags(int halFlags) {
        int flags = 0;
        if (isEphemeral(halFlags)) {
            flags |= UserManagerHelper.FLAG_EPHEMERAL;
        }
        if (isAdmin(halFlags)) {
            flags |= UserManagerHelper.FLAG_ADMIN;
        }
        return flags;
    }

    /**
     * Gets a user-friendly representation of the user flags.
     */
    public static String userFlagsToString(int flags) {
        return DebugUtils.flagsToString(UserInfo.class, /* prefix= */ "", flags);
    }

    /**
     * Adds users information to the property's integer values.
     *
     * <p><b>NOTE: </b>it does not validate the semantics of {@link UsersInfo} content (for example,
     * if the current user is present in the list of users or if the flags are valid), only the
     * basic correctness (like number of users matching existing users list size). Use
     * {@link #checkValid(UsersInfo)} for a full check.
     */
    public static void addUsersInfo(List<Integer> intValues, UsersInfo usersInfo) {
        Objects.requireNonNull(usersInfo.currentUser, "Current user cannot be null");
        checkArgument(usersInfo.numberUsers == usersInfo.existingUsers.length,
                "Number of existing users info does not match numberUsers, got %d, want %d",
                usersInfo.numberUsers, usersInfo.existingUsers.length);

        addUserInfo(intValues, usersInfo.currentUser);
        intValues.add(usersInfo.numberUsers);
        for (int i = 0; i < usersInfo.numberUsers; i++) {
            UserInfo userInfo = usersInfo.existingUsers[i];
            addUserInfo(intValues, userInfo);
        }
    }

    /** Adds user information to the property's integer values. */
    public static void addUserInfo(List<Integer> intValues, UserInfo userInfo) {
        Objects.requireNonNull(userInfo, "UserInfo cannot be null");

        intValues.add(userInfo.userId);
        intValues.add(userInfo.flags);
    }

    /**
     * Checks if the given {@code value} is a valid {@link UserIdentificationAssociationType}.
     */
    public static boolean isValidUserIdentificationAssociationType(int type) {
        switch(type) {
            case UserIdentificationAssociationType.KEY_FOB:
            case UserIdentificationAssociationType.CUSTOM_1:
            case UserIdentificationAssociationType.CUSTOM_2:
            case UserIdentificationAssociationType.CUSTOM_3:
            case UserIdentificationAssociationType.CUSTOM_4:
                return true;
            default:
                break;
        }
        return false;
    }

    /**
     * Checks if the given {@code value} is a valid {@link UserIdentificationAssociationValue}.
     */
    public static boolean isValidUserIdentificationAssociationValue(int value) {
        switch(value) {
            case UserIdentificationAssociationValue.ASSOCIATED_ANOTHER_USER:
            case UserIdentificationAssociationValue.ASSOCIATED_CURRENT_USER:
            case UserIdentificationAssociationValue.NOT_ASSOCIATED_ANY_USER:
            case UserIdentificationAssociationValue.UNKNOWN:
                return true;
            default:
                break;
        }
        return false;
    }

    /**
     * Checks if the given {@code value} is a valid {@link UserIdentificationAssociationSetValue}.
     */
    public static boolean isValidUserIdentificationAssociationSetValue(int value) {
        switch(value) {
            case UserIdentificationAssociationSetValue.ASSOCIATE_CURRENT_USER:
            case UserIdentificationAssociationSetValue.DISASSOCIATE_CURRENT_USER:
            case UserIdentificationAssociationSetValue.DISASSOCIATE_ALL_USERS:
                return true;
            default:
                break;
        }
        return false;
    }

    /**
     * Creates a {@link UserIdentificationResponse} from a generic {@link HalPropValue} sent by
     * HAL.
     *
     * @throws IllegalArgumentException if the HAL property doesn't have the proper format.
     */
    public static UserIdentificationResponse toUserIdentificationResponse(HalPropValue prop) {
        Objects.requireNonNull(prop, "prop cannot be null");
        checkArgument(prop.getPropId() == USER_IDENTIFICATION_ASSOCIATION_PROPERTY,
                "invalid prop on %s", prop);
        // need at least 4: request_id, number associations, type1, value1
        assertMinimumSize(prop, 4);

        int requestId = prop.getInt32Value(0);
        checkArgument(requestId > 0, "invalid request id (%d) on %s", requestId, prop);

        int numberAssociations = prop.getInt32Value(1);
        checkArgument(numberAssociations >= 1, "invalid number of items on %s", prop);
        int numberOfNonItems = 2; // requestId and size
        int numberItems = prop.getInt32ValuesSize() - numberOfNonItems;
        checkArgument(numberItems == numberAssociations * 2, "number of items mismatch on %s",
                prop);

        UserIdentificationResponse response = new UserIdentificationResponse();
        response.requestId = requestId;
        response.errorMessage = prop.getStringValue();

        response.numberAssociation = numberAssociations;
        int i = numberOfNonItems;
        ArrayList<UserIdentificationAssociation> associations = new ArrayList<>(numberAssociations);
        for (int a = 0; a < numberAssociations; a++) {
            int index;
            UserIdentificationAssociation association = new UserIdentificationAssociation();
            index = i++;
            association.type = prop.getInt32Value(index);
            checkArgument(isValidUserIdentificationAssociationType(association.type),
                    "invalid type at index %d on %s", index, prop);
            index = i++;
            association.value = prop.getInt32Value(index);
            checkArgument(isValidUserIdentificationAssociationValue(association.value),
                    "invalid value at index %d on %s", index, prop);
            associations.add(association);
        }

        response.associations = associations.toArray(
                new UserIdentificationAssociation[associations.size()]);

        return response;
    }

    /**
     * Creates a {@link InitialUserInfoResponse} from a generic {@link HalPropValue} sent by
     * HAL.
     *
     * @throws IllegalArgumentException if the HAL property doesn't have the proper format.
     */
    public static InitialUserInfoResponse toInitialUserInfoResponse(HalPropValue prop) {
        if (DEBUG) Log.d(TAG, "toInitialUserInfoResponse(): " + prop);
        Objects.requireNonNull(prop, "prop cannot be null");
        checkArgument(prop.getPropId() == INITIAL_USER_INFO_PROPERTY, "invalid prop on %s", prop);

        // need at least 2: request_id, action_type
        assertMinimumSize(prop, 2);

        int requestId = prop.getInt32Value(0);
        checkArgument(requestId > 0, "invalid request id (%d) on %s", requestId, prop);

        InitialUserInfoResponse response = new InitialUserInfoResponse();
        response.userToSwitchOrCreate = new UserInfo();
        response.userLocales = "";
        response.userNameToCreate = "";
        response.requestId = requestId;
        response.action = prop.getInt32Value(1);

        String[] stringValues = null;
        if (!TextUtils.isEmpty(prop.getStringValue())) {
            stringValues = TextUtils.split(prop.getStringValue(), STRING_SEPARATOR);
            if (DEBUG) {
                Log.d(TAG, "toInitialUserInfoResponse(): values=" + Arrays.toString(stringValues)
                        + " length: " + stringValues.length);
            }
        }
        if (stringValues != null && stringValues.length > 0) {
            response.userLocales = stringValues[0];
        }

        switch (response.action) {
            case InitialUserInfoResponseAction.DEFAULT:
                response.userToSwitchOrCreate.userId = UserManagerHelper.USER_NULL;
                break;
            case InitialUserInfoResponseAction.SWITCH:
                assertMinimumSize(prop, 3); // request_id, action_type, user_id
                response.userToSwitchOrCreate.userId = prop.getInt32Value(2);
                break;
            case InitialUserInfoResponseAction.CREATE:
                assertMinimumSize(prop, 4); // request_id, action_type, user_id, user_flags
                // user id is set at index 2, but it's ignored
                response.userToSwitchOrCreate.userId = UserManagerHelper.USER_NULL;
                response.userToSwitchOrCreate.flags = prop.getInt32Value(3);
                if (stringValues.length > 1) {
                    response.userNameToCreate = stringValues[1];
                }
                break;
            default:
                throw new IllegalArgumentException("Invalid response action (" + response.action
                        + " on " + prop);
        }

        if (DEBUG) Log.d(TAG, "returning : " + response);

        return response;
    }

    /**
     * Creates a generic {@link HalPropValue} (that can be sent to HAL) from a
     * {@link UserIdentificationGetRequest}.
     *
     * @throws IllegalArgumentException if the request doesn't have the proper format.
     */
    public static HalPropValue toHalPropValue(HalPropValueBuilder builder,
            UserIdentificationGetRequest request) {
        Objects.requireNonNull(request, "request cannot be null");
        Objects.requireNonNull(request.associationTypes, "associationTypes must not be null");
        checkArgument(request.numberAssociationTypes > 0,
                "invalid number of association types mismatch on %s", request);
        checkArgument(request.numberAssociationTypes == request.associationTypes.length,
                "number of association types mismatch on %s", request);
        checkArgument(request.requestId > 0, "invalid requestId on %s", request);

        List<Integer> intValues = new ArrayList<>(request.numberAssociationTypes + 2);
        intValues.add(request.requestId);
        addUserInfo(intValues, request.userInfo);
        intValues.add(request.numberAssociationTypes);

        for (int i = 0; i < request.numberAssociationTypes; i++) {
            int type = request.associationTypes[i];
            checkArgument(isValidUserIdentificationAssociationType(type),
                    "invalid type at index %d on %s", i, request);
            intValues.add(type);
        }

        HalPropValue propValue = builder.build(USER_IDENTIFICATION_ASSOCIATION_PROPERTY,
                /* areaId= */ 0, SystemClock.elapsedRealtime(),
                VehiclePropertyStatus.AVAILABLE,
                toIntArray(intValues));

        return propValue;
    }

    /**
     * Creates a generic {@link HalPropValue} (that can be sent to HAL) from a
     * {@link UserIdentificationSetRequest}.
     *
     * @throws IllegalArgumentException if the request doesn't have the proper format.
     */
    public static HalPropValue toHalPropValue(HalPropValueBuilder builder,
            UserIdentificationSetRequest request) {
        Objects.requireNonNull(request, "request cannot be null");
        Objects.requireNonNull(request.associations, "associations must not be null");
        checkArgument(request.numberAssociations > 0,
                "invalid number of associations  mismatch on %s", request);
        checkArgument(request.numberAssociations == request.associations.length,
                "number of associations mismatch on %s", request);
        checkArgument(request.requestId > 0, "invalid requestId on %s", request);

        List<Integer> intValues = new ArrayList<>(2);
        intValues.add(request.requestId);
        addUserInfo(intValues, request.userInfo);
        intValues.add(request.numberAssociations);

        for (int i = 0; i < request.numberAssociations; i++) {
            UserIdentificationSetAssociation association = request.associations[i];
            checkArgument(isValidUserIdentificationAssociationType(association.type),
                    "invalid type at index %d on %s", i, request);
            intValues.add(association.type);
            checkArgument(isValidUserIdentificationAssociationSetValue(association.value),
                    "invalid value at index %d on %s", i, request);
            intValues.add(association.value);
        }

        HalPropValue propValue = builder.build(USER_IDENTIFICATION_ASSOCIATION_PROPERTY,
                /* areaId= */ 0, SystemClock.elapsedRealtime(),
                VehiclePropertyStatus.AVAILABLE,
                toIntArray(intValues));

        return propValue;
    }

    /**
     * Creates a generic {@link HalPropValue} (that can be sent to HAL) from a
     * {@link CreateUserRequest}.
     *
     * @throws IllegalArgumentException if the request doesn't have the proper format.
     */
    public static HalPropValue toHalPropValue(HalPropValueBuilder builder,
            CreateUserRequest request) {
        Objects.requireNonNull(request, "request cannot be null");
        Objects.requireNonNull(request.newUserInfo, "NewUserInfo cannot be null");
        checkArgument(request.requestId > 0, "invalid requestId on %s", request);
        checkValid(request.usersInfo);
        checkArgument(request.newUserName != null, "newUserName cannot be null (should be empty "
                + "instead) on %s", request);

        boolean hasNewUser = false;
        int newUserFlags = 0;
        for (int i = 0; i < request.usersInfo.existingUsers.length; i++) {
            UserInfo user = request.usersInfo.existingUsers[i];
            if (user.userId == request.newUserInfo.userId) {
                hasNewUser = true;
                newUserFlags = user.flags;
                break;
            }
        }
        Preconditions.checkArgument(hasNewUser,
                "new user's id not present on existing users on request %s", request);
        Preconditions.checkArgument(request.newUserInfo.flags == newUserFlags,
                "new user flags mismatch on existing users on %s", request);

        List<Integer> intValues = new ArrayList<>(2);
        intValues.add(request.requestId);
        addUserInfo(intValues, request.newUserInfo);
        addUsersInfo(intValues, request.usersInfo);

        HalPropValue propValue = builder.build(CREATE_USER_PROPERTY, /* areaId= */ 0,
                SystemClock.elapsedRealtime(), VehiclePropertyStatus.AVAILABLE,
                toIntArray(intValues), new float[0], new long[0], request.newUserName, new byte[0]);

        return propValue;
    }

    /**
     * Creates a generic {@link HalPropValue} (that can be sent to HAL) from a
     * {@link SwitchUserRequest}.
     *
     * @throws IllegalArgumentException if the request doesn't have the proper format.
     */
    public static HalPropValue toHalPropValue(HalPropValueBuilder builder,
            SwitchUserRequest request) {
        Objects.requireNonNull(request, "request cannot be null");
        checkArgument(request.messageType > 0, "invalid messageType on %s", request);
        UserInfo targetInfo = request.targetUser;
        UsersInfo usersInfo = request.usersInfo;
        Objects.requireNonNull(targetInfo);
        checkValid(usersInfo);

        List<Integer> intValues = new ArrayList<>(2);
        intValues.add(request.requestId);
        intValues.add(request.messageType);

        addUserInfo(intValues, targetInfo);
        addUsersInfo(intValues, usersInfo);

        HalPropValue propValue = builder.build(SWITCH_USER_PROPERTY, /* areaId= */ 0,
                SystemClock.elapsedRealtime(), VehiclePropertyStatus.AVAILABLE,
                toIntArray(intValues));

        return propValue;
    }

    /**
     * Creates a generic {@link HalPropValue} (that can be sent to HAL) from a
     * {@link RemoveUserRequest}.
     *
     * @throws IllegalArgumentException if the request doesn't have the proper format.
     */
    public static HalPropValue toHalPropValue(HalPropValueBuilder builder,
            RemoveUserRequest request) {
        checkArgument(request.requestId > 0, "invalid requestId on %s", request);
        UserInfo removedUserInfo = request.removedUserInfo;
        Objects.requireNonNull(removedUserInfo);
        UsersInfo usersInfo = request.usersInfo;
        checkValid(usersInfo);

        List<Integer> intValues = new ArrayList<>(1);
        intValues.add(request.requestId);

        addUserInfo(intValues, removedUserInfo);
        addUsersInfo(intValues, usersInfo);

        HalPropValue propValue = builder.build(REMOVE_USER_PROPERTY, /* areaId= */ 0,
                SystemClock.elapsedRealtime(), VehiclePropertyStatus.AVAILABLE,
                toIntArray(intValues));
        return propValue;
    }

    /**
     * Creates a {@link UsersInfo} instance populated with the current users, using
     * {@link ActivityManager#getCurrentUser()} as the current user.
     */
    public static UsersInfo newUsersInfo(UserManager um, UserHandleHelper userHandleHelper) {
        return newUsersInfo(um, userHandleHelper, ActivityManager.getCurrentUser());
    }

    /**
     * Creates a {@link UsersInfo} instance populated with the current users, using
     * {@code userId} as the current user.
     */
    public static UsersInfo newUsersInfo(UserManager um, UserHandleHelper userHandleHelper,
            @UserIdInt int userId) {
        Preconditions.checkArgument(um != null, "UserManager cannot be null");
        Preconditions.checkArgument(userHandleHelper != null, "UserHandleHelper cannot be null");

        List<UserHandle> users = UserManagerHelper.getUserHandles(um, /* excludeDying= */ false);

        if (users == null || users.isEmpty()) {
            Log.w(TAG, "newUsersInfo(): no users");
            return emptyUsersInfo();
        }

        UsersInfo usersInfo = emptyUsersInfo();
        usersInfo.currentUser.userId = userId;
        UserHandle currentUser = null;
        int allUsersSize = users.size();
        ArrayList<UserInfo> halUsers = new ArrayList<>(allUsersSize);
        for (int i = 0; i < allUsersSize; i++) {
            UserHandle user = users.get(i);
            try {
                if (user.getIdentifier() == usersInfo.currentUser.userId) {
                    currentUser = user;
                }
                UserInfo halUser = new UserInfo();
                halUser.userId = user.getIdentifier();
                halUser.flags = convertFlags(userHandleHelper, user);
                halUsers.add(halUser);
            } catch (Exception e) {
                // Most likely the user was removed
                Log.w(TAG, "newUsersInfo(): ignoring user " + user + " due to exception", e);
            }
        }
        int existingUsersSize = halUsers.size();
        usersInfo.numberUsers = existingUsersSize;
        usersInfo.existingUsers = halUsers.toArray(new UserInfo[existingUsersSize]);

        if (currentUser != null) {
            usersInfo.currentUser.flags = convertFlags(userHandleHelper, currentUser);
        } else {
            // This should not happen.
            Log.wtf(TAG, "Current user is not part of existing users. usersInfo: " + usersInfo);
        }

        return usersInfo;
    }

    /**
     * Checks if the given {@code usersInfo} is valid.
     *
     * @throws IllegalArgumentException if it isn't.
     */
    public static void checkValid(UsersInfo usersInfo) {
        Preconditions.checkArgument(usersInfo != null);
        Preconditions.checkArgument(usersInfo.existingUsers != null);
        Preconditions.checkArgument(usersInfo.currentUser != null);
        Preconditions.checkArgument(usersInfo.numberUsers == usersInfo.existingUsers.length,
                "sizes mismatch: numberUsers=%d, existingUsers.size=%d", usersInfo.numberUsers,
                usersInfo.existingUsers.length);
        boolean hasCurrentUser = false;
        int currentUserFlags = 0;
        for (int i = 0; i < usersInfo.numberUsers; i++) {
            UserInfo user = usersInfo.existingUsers[i];
            if (user.userId == usersInfo.currentUser.userId) {
                hasCurrentUser = true;
                currentUserFlags = user.flags;
                break;
            }
        }
        Preconditions.checkArgument(hasCurrentUser,
                "current user not found on existing users on %s", usersInfo);
        Preconditions.checkArgument(usersInfo.currentUser.flags == currentUserFlags,
                "current user flags mismatch on existing users on %s", usersInfo);
    }

    /**
     * Gets an empty CreateUserRequest with fields initialized to valid empty values (not
     * {@code null}).
     *
     * @return An empty {@link CreateUserRequest}.
     */
    public static CreateUserRequest emptyCreateUserRequest() {
        CreateUserRequest request = new CreateUserRequest();
        request.newUserInfo = new UserInfo();
        request.usersInfo = emptyUsersInfo();
        request.newUserName = "";
        return request;
    }

    /**
     * Gets an empty SwitchUserRequest with fields initialized to valid empty values (not
     * {@code null}
     *
     * @return An empty {@link SwitchUserRequest}.
     */
    public static SwitchUserRequest emptySwitchUserRequest() {
        SwitchUserRequest request = new SwitchUserRequest();
        request.targetUser = new UserInfo();
        request.usersInfo = emptyUsersInfo();
        return request;
    }

    /**
     * Gets an empty RemoveUserRequest with fields initialized to valid empty values (not
     * {@code null}
     *
     * @return An empty {@link RemoveUserRequest}.
     */
    public static RemoveUserRequest emptyRemoveUserRequest() {
        RemoveUserRequest request = new RemoveUserRequest();
        request.removedUserInfo = new UserInfo();
        request.usersInfo = emptyUsersInfo();
        return request;
    }

    /**
     * Gets an empty UserIdentificationGetRequest with fields initialized to valid empty values
     * (not {@code null}).
     *
     * @return An empty {@link UserIdentificationGetRequest}.
     */
    public static UserIdentificationGetRequest emptyUserIdentificationGetRequest() {
        UserIdentificationGetRequest request = new UserIdentificationGetRequest();
        request.userInfo = new UserInfo();
        request.associationTypes = EMPTY_INT_ARRAY;
        return request;
    }

    /**
     * Gets an empty UserIdentificationSetRequest with fields initialized to valid empty values
     * (not {@code null}).
     *
     * @return An empty {@link UserIdentificationSetRequest}.
     */
    public static UserIdentificationSetRequest emptyUserIdentificationSetRequest() {
        UserIdentificationSetRequest request = new UserIdentificationSetRequest();
        request.userInfo = new UserInfo();
        request.associations = new UserIdentificationSetAssociation[0];
        return request;
    }

    /**
     * Gets an empty UsersInfo with fields initialized to valid empty values (not {@code null}).
     *
     * @return An empty {@link UsersInfo}.
     */
    public static UsersInfo emptyUsersInfo() {
        UsersInfo usersInfo = new UsersInfo();
        usersInfo.currentUser = new UserInfo();
        usersInfo.existingUsers = new UserInfo[0];
        usersInfo.currentUser.userId = UserManagerHelper.USER_NULL;
        return usersInfo;
    }

    private static void assertMinimumSize(HalPropValue prop, int minSize) {
        checkArgument(prop.getInt32ValuesSize() >= minSize,
                "not enough int32Values (minimum is %d) on %s", minSize, prop);
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = PRIVATE_CONSTRUCTOR)
    private UserHalHelper() {
        throw new UnsupportedOperationException("contains only static methods");
    }
}
