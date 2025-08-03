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

package android.car.builtin.util;

import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.util.EventLog;

/**
 * Helper for {@link EventLog}
 *
 * @hide
 */
@SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)
public final class EventLogHelper {
    public static void writeCarHelperStart() {
        EventLog.writeEvent(EventLogTags.CAR_HELPER_START);
    }

    public static void writeCarHelperBootPhase(int phase) {
        EventLog.writeEvent(EventLogTags.CAR_HELPER_BOOT_PHASE, phase);
    }

    public static void writeCarHelperUserStarting(int userId) {
        EventLog.writeEvent(EventLogTags.CAR_HELPER_USER_STARTING, userId);
    }

    public static void writeCarHelperUserSwitching(int fromUserId, int toUserId) {
        EventLog.writeEvent(EventLogTags.CAR_HELPER_USER_SWITCHING, fromUserId, toUserId);
    }

    public static void writeCarHelperUserUnlocking(int userId) {
        EventLog.writeEvent(EventLogTags.CAR_HELPER_USER_UNLOCKING, userId);
    }

    public static void writeCarHelperUserUnlocked(int userId) {
        EventLog.writeEvent(EventLogTags.CAR_HELPER_USER_UNLOCKED, userId);
    }

    public static void writeCarHelperUserStopping(int userId) {
        EventLog.writeEvent(EventLogTags.CAR_HELPER_USER_STOPPING, userId);
    }

    public static void writeCarHelperUserStopped(int userId) {
        EventLog.writeEvent(EventLogTags.CAR_HELPER_USER_STOPPED, userId);
    }

    public static void writeCarHelperServiceConnected() {
        EventLog.writeEvent(EventLogTags.CAR_HELPER_SVC_CONNECTED);
    }

    public static void writeCarServiceInit(int numberServices) {
        EventLog.writeEvent(EventLogTags.CAR_SERVICE_INIT, numberServices);
    }

    public static void writeCarServiceVhalReconnected(int numberServices) {
        EventLog.writeEvent(EventLogTags.CAR_SERVICE_VHAL_RECONNECTED, numberServices);
    }

    public static void writeCarServiceSetCarServiceHelper(int pid) {
        EventLog.writeEvent(EventLogTags.CAR_SERVICE_SET_CAR_SERVICE_HELPER, pid);
    }

    public static void writeCarServiceOnUserLifecycle(int type, int fromUserId, int toUserId) {
        EventLog.writeEvent(EventLogTags.CAR_SERVICE_ON_USER_LIFECYCLE, type, fromUserId, toUserId);
    }

    public static void writeCarServiceCreate(boolean hasVhal) {
        EventLog.writeEvent(EventLogTags.CAR_SERVICE_CREATE, hasVhal ? 1 : 0);
    }

    public static void writeCarServiceConnected(@Nullable String interfaceName) {
        EventLog.writeEvent(EventLogTags.CAR_SERVICE_CONNECTED, interfaceName);
    }

    public static void writeCarServiceDestroy(boolean hasVhal) {
        EventLog.writeEvent(EventLogTags.CAR_SERVICE_DESTROY, hasVhal ? 1 : 0);
    }

    public static void writeCarServiceVhalDied(long cookie) {
        EventLog.writeEvent(EventLogTags.CAR_SERVICE_VHAL_DIED, cookie);
    }

    public static void writeCarServiceInitBootUser() {
        EventLog.writeEvent(EventLogTags.CAR_SERVICE_INIT_BOOT_USER);
    }

    public static void writeCarServiceOnUserRemoved(int userId) {
        EventLog.writeEvent(EventLogTags.CAR_SERVICE_ON_USER_REMOVED, userId);
    }

    public static void writeCarUserServiceInitialUserInfoReq(int requestType, int timeout,
            int currentUserId, int currentUserFlags, int numberExistingUsers) {
        EventLog.writeEvent(EventLogTags.CAR_USER_SVC_INITIAL_USER_INFO_REQ, requestType, timeout,
                currentUserId, currentUserFlags, numberExistingUsers);
    }

    public static void writeCarUserServiceInitialUserInfoResp(int status, int action, int userId,
            int flags, @Nullable String safeName, @Nullable String userLocales) {
        EventLog.writeEvent(EventLogTags.CAR_USER_SVC_INITIAL_USER_INFO_RESP, status, action,
                userId, flags, safeName, userLocales);
    }

    public static void writeCarUserServiceSetInitialUser(int userId) {
        EventLog.writeEvent(EventLogTags.CAR_USER_SVC_SET_INITIAL_USER, userId);
    }

    public static void writeCarUserServiceSetLifecycleListener(int uid,
            @Nullable String packageName) {
        EventLog.writeEvent(EventLogTags.CAR_USER_SVC_SET_LIFECYCLE_LISTENER, uid, packageName);
    }

    public static void writeCarUserServiceResetLifecycleListener(int uid,
            @Nullable String packageName) {
        EventLog.writeEvent(EventLogTags.CAR_USER_SVC_RESET_LIFECYCLE_LISTENER, uid, packageName);
    }

    public static void writeCarUserServiceSwitchUserReq(int userId, int timeout) {
        EventLog.writeEvent(EventLogTags.CAR_USER_SVC_SWITCH_USER_REQ, userId, timeout);
    }

    public static void writeCarUserServiceSwitchUserResp(int halCallbackStatus,
            int userSwitchStatus, @Nullable String errorMessage) {
        EventLog.writeEvent(EventLogTags.CAR_USER_SVC_SWITCH_USER_RESP, halCallbackStatus,
                userSwitchStatus, errorMessage);
    }

    /**
     * Logs a {@code EventLogTags.CAR_USER_SVC_LOGOUT_USER_REQ} event.
     */
    public static void writeCarUserServiceLogoutUserReq(int userId, int timeout) {
        EventLog.writeEvent(EventLogTags.CAR_USER_SVC_LOGOUT_USER_REQ, userId, timeout);
    }

    /**
     * Logs a {@code EventLogTags.CAR_USER_SVC_LOGOUT_USER_RESP} event.
     */
    public static void writeCarUserServiceLogoutUserResp(int halCallbackStatus,
            int userSwitchStatus, @Nullable String errorMessage) {
        EventLog.writeEvent(EventLogTags.CAR_USER_SVC_LOGOUT_USER_RESP, halCallbackStatus,
                userSwitchStatus, errorMessage);
    }
    public static void writeCarUserServicePostSwitchUserReq(int targetUserId, int currentUserId) {
        EventLog.writeEvent(EventLogTags.CAR_USER_SVC_POST_SWITCH_USER_REQ, targetUserId,
                currentUserId);
    }

    public static void writeCarUserServiceGetUserAuthReq(int uid, int userId, int numberTypes) {
        EventLog.writeEvent(EventLogTags.CAR_USER_SVC_GET_USER_AUTH_REQ, uid, userId, numberTypes);
    }

    public static void writeCarUserServiceGetUserAuthResp(int numberValues) {
        EventLog.writeEvent(EventLogTags.CAR_USER_SVC_GET_USER_AUTH_RESP, numberValues);
    }

    public static void writeCarUserServiceSwitchUserUiReq(int userId) {
        EventLog.writeEvent(EventLogTags.CAR_USER_SVC_SWITCH_USER_UI_REQ, userId);
    }

    public static void writeCarUserServiceSwitchUserFromHalReq(int requestId, int uid) {
        EventLog.writeEvent(EventLogTags.CAR_USER_SVC_SWITCH_USER_FROM_HAL_REQ, requestId, uid);
    }

    public static void writeCarUserServiceSetUserAuthReq(int uid, int userId,
            int numberAssociations) {
        EventLog.writeEvent(EventLogTags.CAR_USER_SVC_SET_USER_AUTH_REQ, uid, userId,
                numberAssociations);
    }

    public static void writeCarUserServiceSetUserAuthResp(int numberValues,
            @Nullable String errorMessage) {
        EventLog.writeEvent(EventLogTags.CAR_USER_SVC_SET_USER_AUTH_RESP, numberValues,
                errorMessage);
    }

    public static void writeCarUserServiceCreateUserReq(@Nullable String safeName,
            @Nullable String userType, int flags, int timeout, int hasCallerRestrictions) {
        EventLog.writeEvent(EventLogTags.CAR_USER_SVC_CREATE_USER_REQ, safeName, userType, flags,
                timeout, hasCallerRestrictions);
    }

    public static void writeCarUserServiceCreateUserResp(int status, int result,
            @Nullable String errorMessage) {
        EventLog.writeEvent(EventLogTags.CAR_USER_SVC_CREATE_USER_RESP, status, result,
                errorMessage);
    }

    public static void writeCarUserServiceCreateUserUserCreated(int userId,
            @Nullable String safeName, @Nullable String userType, int flags) {
        EventLog.writeEvent(EventLogTags.CAR_USER_SVC_CREATE_USER_USER_CREATED, userId, safeName,
                userType, flags);
    }

    public static void writeCarUserServiceCreateUserUserRemoved(int userId,
            @Nullable String reason) {
        EventLog.writeEvent(EventLogTags.CAR_USER_SVC_CREATE_USER_USER_REMOVED, userId, reason);
    }

    public static void writeCarUserServiceRemoveUserReq(int userId, int hascallerrestrictions) {
        EventLog.writeEvent(EventLogTags.CAR_USER_SVC_REMOVE_USER_REQ, userId,
                hascallerrestrictions);
    }

    public static void writeCarUserServiceRemoveUserResp(int userId, int result) {
        EventLog.writeEvent(EventLogTags.CAR_USER_SVC_REMOVE_USER_RESP, userId, result);
    }

    public static void writeCarUserServiceNotifyAppLifecycleListener(int uid,
            @Nullable String packageName, int eventType, int fromUserId, int toUserId) {
        EventLog.writeEvent(EventLogTags.CAR_USER_SVC_NOTIFY_APP_LIFECYCLE_LISTENER, uid,
                packageName, eventType, fromUserId, toUserId);
    }

    public static void writeCarUserServiceNotifyInternalLifecycleListener(
            @Nullable String listenerName, int eventType, int fromUserId, int toUserId) {
        EventLog.writeEvent(EventLogTags.CAR_USER_SVC_NOTIFY_INTERNAL_LIFECYCLE_LISTENER,
                listenerName, eventType, fromUserId, toUserId);
    }

    public static void writeCarUserServicePreCreationRequested(int numberUsers, int numberGuests) {
        EventLog.writeEvent(EventLogTags.CAR_USER_SVC_PRE_CREATION_REQUESTED, numberUsers,
                numberGuests);
    }

    public static void writeCarUserServicePreCreationStatus(int numberExistingUsers,
            int numberUsersToAdd, int numberUsersToRemove, int numberExistingGuests,
            int numberGuestsToAdd, int numberGuestsToRemove, int numberInvalidUsersToRemove) {
        EventLog.writeEvent(EventLogTags.CAR_USER_SVC_PRE_CREATION_STATUS, numberExistingUsers,
                numberUsersToAdd, numberUsersToRemove, numberExistingGuests, numberGuestsToAdd,
                numberGuestsToRemove, numberInvalidUsersToRemove);
    }

    public static void writeCarUserServiceStartUserInBackgroundReq(int userId) {
        EventLog.writeEvent(EventLogTags.CAR_USER_SVC_START_USER_IN_BACKGROUND_REQ, userId);
    }

    public static void writeCarUserServiceStartUserInBackgroundResp(int userId, int result) {
        EventLog.writeEvent(EventLogTags.CAR_USER_SVC_START_USER_IN_BACKGROUND_RESP, userId,
                result);
    }

    /** Logs a {@code EventLogTags.CAR_USER_SVC_START_USER_VISIBLE_ON_DISPLAY_REQ} event. */
    public static void writeCarUserServiceStartUserVisibleOnDisplayReq(int userId, int displayId) {
        EventLog.writeEvent(EventLogTags.CAR_USER_SVC_START_USER_VISIBLE_ON_DISPLAY_REQ,
                userId, displayId);
    }

    /** Logs a {@code EventLogTags.CAR_USER_SVC_START_USER_VISIBLE_ON_DISPLAY_RESP} event. */
    public static void writeCarUserServiceStartUserVisibleOnDisplayResp(
            int userId, int displayId, int result) {
        EventLog.writeEvent(EventLogTags.CAR_USER_SVC_START_USER_VISIBLE_ON_DISPLAY_RESP,
                userId, displayId, result);
    }

    public static void writeCarUserServiceStopUserReq(int userId) {
        EventLog.writeEvent(EventLogTags.CAR_USER_SVC_STOP_USER_REQ, userId);
    }

    public static void writeCarUserServiceStopUserResp(int userId, int result) {
        EventLog.writeEvent(EventLogTags.CAR_USER_SVC_STOP_USER_RESP, userId, result);
    }

    public static void writeCarUserServiceInitialUserInfoReqComplete(int requestType) {
        EventLog.writeEvent(EventLogTags.CAR_USER_SVC_INITIAL_USER_INFO_REQ_COMPLETE, requestType);
    }

    public static void writeCarUserHalInitialUserInfoReq(int requestId, int requestType,
            int timeout) {
        EventLog.writeEvent(EventLogTags.CAR_USER_HAL_INITIAL_USER_INFO_REQ, requestId, requestType,
                timeout);
    }

    public static void writeCarUserHalInitialUserInfoResp(int requestId, int status, int action,
            int userId, int flags, @Nullable String safeName, @Nullable String userLocales) {
        EventLog.writeEvent(EventLogTags.CAR_USER_HAL_INITIAL_USER_INFO_RESP, requestId, status,
                action, userId, flags, safeName, userLocales);
    }

    public static void writeCarInitialUserStartFgUser(int userId) {
        EventLog.writeEvent(EventLogTags.CAR_INITIAL_USER_START_FG_USER, userId);
    }

    public static void writeCarInitialUserInfo(int type, boolean replaceGuest,
            int switchUserId, @Nullable String newUserName, int newUserFlags,
            boolean supportsOverrideUserIdProperty, @Nullable String userLocales) {
        EventLog.writeEvent(EventLogTags.CAR_INITIAL_USER_INFO, type, replaceGuest ? 1 : 0,
                switchUserId,
                newUserName, newUserFlags, supportsOverrideUserIdProperty ? 1 : 0, userLocales);
    }

    public static void writeCarInitialUserFallbackDefaultBehavior(@Nullable String reason) {
        EventLog.writeEvent(EventLogTags.CAR_INITIAL_USER_FALLBACK_DEFAULT_BEHAVIOR, reason);
    }

    public static void writeCarInitialUserReplaceGuest(int userId) {
        EventLog.writeEvent(EventLogTags.CAR_INITIAL_USER_REPLACE_GUEST, userId);
    }

    public static void writeCarInitialUserUnlockSystemUser() {
        EventLog.writeEvent(EventLogTags.CAR_INITIAL_USER_UNLOCK_SYSTEM_USER);
    }

    public static void writeCarInitialUserSetLastActive(int userId) {
        EventLog.writeEvent(EventLogTags.CAR_INITIAL_USER_SET_LAST_ACTIVE, userId);
    }

    public static void writeCarInitialUserResetGlobalProperty(@Nullable String name) {
        EventLog.writeEvent(EventLogTags.CAR_INITIAL_USER_RESET_GLOBAL_PROPERTY, name);
    }

    public static void writeCarUserHalSwitchUserReq(int requestId, int userId, int userFlags,
            int timeout) {
        EventLog.writeEvent(EventLogTags.CAR_USER_HAL_SWITCH_USER_REQ, requestId, userId, userFlags,
                timeout);
    }

    public static void writeCarUserHalSwitchUserResp(int requestId, int status, int result,
            @Nullable String errorMessage) {
        EventLog.writeEvent(EventLogTags.CAR_USER_HAL_SWITCH_USER_RESP, requestId, status, result,
                errorMessage);
    }

    public static void writeCarUserHalPostSwitchUserReq(int requestId, int targetUserId,
            int currentUserId) {
        EventLog.writeEvent(EventLogTags.CAR_USER_HAL_POST_SWITCH_USER_REQ, requestId, targetUserId,
                currentUserId);
    }

    public static void writeCarUserHalGetUserAuthReq(@Nullable Object[] int32Values) {
        EventLog.writeEvent(EventLogTags.CAR_USER_HAL_GET_USER_AUTH_REQ, int32Values);
    }

    public static void writeCarUserHalGetUserAuthResp(@Nullable Object[] valuesAndError) {
        EventLog.writeEvent(EventLogTags.CAR_USER_HAL_GET_USER_AUTH_RESP, valuesAndError);
    }

    public static void writeCarUserHalLegacySwitchUserReq(int requestId, int targetUserId,
            int currentUserId) {
        EventLog.writeEvent(EventLogTags.CAR_USER_HAL_LEGACY_SWITCH_USER_REQ, requestId,
                targetUserId, currentUserId);
    }

    public static void writeCarUserHalSetUserAuthReq(@Nullable Object[] int32Values) {
        EventLog.writeEvent(EventLogTags.CAR_USER_HAL_SET_USER_AUTH_REQ, int32Values);
    }

    public static void writeCarUserHalSetUserAuthResp(@Nullable Object[] valuesAndError) {
        EventLog.writeEvent(EventLogTags.CAR_USER_HAL_SET_USER_AUTH_RESP, valuesAndError);
    }

    public static void writeCarUserHalOemSwitchUserReq(int requestId, int targetUserId) {
        EventLog.writeEvent(EventLogTags.CAR_USER_HAL_OEM_SWITCH_USER_REQ, requestId, targetUserId);
    }

    public static void writeCarUserHalCreateUserReq(int requestId, @Nullable String safeName,
            int flags, int timeout) {
        EventLog.writeEvent(EventLogTags.CAR_USER_HAL_CREATE_USER_REQ, requestId, safeName, flags,
                timeout);
    }

    public static void writeCarUserHalCreateUserResp(int requestId, int status, int result,
            @Nullable String errorMessage) {
        EventLog.writeEvent(EventLogTags.CAR_USER_HAL_CREATE_USER_RESP, requestId, status, result,
                errorMessage);
    }

    public static void writeCarUserHalRemoveUserReq(int targetUserId, int currentUserId) {
        EventLog.writeEvent(EventLogTags.CAR_USER_HAL_REMOVE_USER_REQ, targetUserId, currentUserId);
    }

    /** Logs a {@code EventLogTags.CAR_USER_MGR_ADD_LISTENER} event. */
    public static void writeCarUserManagerAddListener(int uid, @Nullable String packageName,
            boolean hasFilter) {
        EventLog.writeEvent(EventLogTags.CAR_USER_MGR_ADD_LISTENER, uid, packageName,
                hasFilter ? 1 : 0);
    }

    public static void writeCarUserManagerRemoveListener(int uid, @Nullable String packageName) {
        EventLog.writeEvent(EventLogTags.CAR_USER_MGR_REMOVE_LISTENER, uid, packageName);
    }

    public static void writeCarUserManagerDisconnected(int uid) {
        EventLog.writeEvent(EventLogTags.CAR_USER_MGR_DISCONNECTED, uid);
    }

    public static void writeCarUserManagerSwitchUserReq(int uid, int userId) {
        EventLog.writeEvent(EventLogTags.CAR_USER_MGR_SWITCH_USER_REQ, uid, userId);
    }

    public static void writeCarUserManagerSwitchUserResp(int uid, int status,
            @Nullable String errorMessage) {
        EventLog.writeEvent(EventLogTags.CAR_USER_MGR_SWITCH_USER_RESP, uid, status, errorMessage);
    }

    /**
     * Logs a {@code EventLogTags.CAR_USER_MGR_LOGOUT_USER_REQ} event.
     */
    public static void writeCarUserManagerLogoutUserReq(int uid) {
        EventLog.writeEvent(EventLogTags.CAR_USER_MGR_LOGOUT_USER_REQ, uid);
    }

    /**
     * Logs a {@code EventLogTags.CAR_USER_MGR_LOGOUT_USER_RESP} event.
     */
    public static void writeCarUserManagerLogoutUserResp(int uid, int status,
            @Nullable String errorMessage) {
        EventLog.writeEvent(EventLogTags.CAR_USER_MGR_LOGOUT_USER_RESP, uid, status, errorMessage);
    }

    public static void writeCarUserManagerGetUserAuthReq(@Nullable Object[] types) {
        EventLog.writeEvent(EventLogTags.CAR_USER_MGR_GET_USER_AUTH_REQ, types);
    }

    public static void writeCarUserManagerGetUserAuthResp(@Nullable Object[] values) {
        EventLog.writeEvent(EventLogTags.CAR_USER_MGR_GET_USER_AUTH_RESP, values);
    }

    public static void writeCarUserManagerSetUserAuthReq(@Nullable Object[] typesAndValuesPairs) {
        EventLog.writeEvent(EventLogTags.CAR_USER_MGR_SET_USER_AUTH_REQ, typesAndValuesPairs);
    }

    public static void writeCarUserManagerSetUserAuthResp(@Nullable Object[] values) {
        EventLog.writeEvent(EventLogTags.CAR_USER_MGR_SET_USER_AUTH_RESP, values);
    }

    public static void writeCarUserManagerCreateUserReq(int uid, @Nullable String safeName,
            @Nullable String userType, int flags) {
        EventLog.writeEvent(EventLogTags.CAR_USER_MGR_CREATE_USER_REQ, uid, safeName, userType,
                flags);
    }

    public static void writeCarUserManagerCreateUserResp(int uid, int status,
            @Nullable String errorMessage) {
        EventLog.writeEvent(EventLogTags.CAR_USER_MGR_CREATE_USER_RESP, uid, status, errorMessage);
    }

    public static void writeCarUserManagerRemoveUserReq(int uid, int userId) {
        EventLog.writeEvent(EventLogTags.CAR_USER_MGR_REMOVE_USER_REQ, uid, userId);
    }

    public static void writeCarUserManagerRemoveUserResp(int uid, int status) {
        EventLog.writeEvent(EventLogTags.CAR_USER_MGR_REMOVE_USER_RESP, uid, status);
    }

    /** Logs a {@code EventLogTags.CAR_USER_MGR_START_USER_REQ} event. */
    public static void writeCarUserManagerStartUserReq(int uid, int userId, int displayId) {
        EventLog.writeEvent(EventLogTags.CAR_USER_MGR_START_USER_REQ, uid, userId, displayId);
    }

    /** Logs a {@code EventLogTags.CAR_USER_MGR_START_USER_RESP} event. */
    public static void writeCarUserManagerStartUserResp(
            int uid, int userId, int displayId, int status) {
        EventLog.writeEvent(EventLogTags.CAR_USER_MGR_START_USER_RESP,
                uid, userId, displayId, status);
    }

    /** Logs a {@code EventLogTags.CAR_USER_MGR_STOP_USER_REQ} event. */
    public static void writeCarUserManagerStopUserReq(int uid, int userId) {
        EventLog.writeEvent(EventLogTags.CAR_USER_MGR_STOP_USER_REQ, uid, userId);
    }

    /** Logs a {@code EventLogTags.CAR_USER_MGR_STOP_USER_RESP} event. */
    public static void writeCarUserManagerStopUserResp(int uid, int userId, int status) {
        EventLog.writeEvent(EventLogTags.CAR_USER_MGR_STOP_USER_RESP, uid, userId, status);
    }

    public static void writeCarUserManagerNotifyLifecycleListener(int numberListeners,
            int eventType, int fromUserId, int toUserId) {
        EventLog.writeEvent(EventLogTags.CAR_USER_MGR_NOTIFY_LIFECYCLE_LISTENER, numberListeners,
                eventType, fromUserId, toUserId);
    }

    public static void writeCarUserManagerPreCreateUserReq(int uid) {
        EventLog.writeEvent(EventLogTags.CAR_USER_MGR_PRE_CREATE_USER_REQ, uid);
    }

    public static void writeCarDevicePolicyManagerRemoveUserReq(int uid, int userId) {
        EventLog.writeEvent(EventLogTags.CAR_DP_MGR_REMOVE_USER_REQ, uid, userId);
    }

    public static void writeCarDevicePolicyManagerRemoveUserResp(int uid, int status) {
        EventLog.writeEvent(EventLogTags.CAR_DP_MGR_REMOVE_USER_RESP, uid, status);
    }

    public static void writeCarDevicePolicyManagerCreateUserReq(int uid, @Nullable String safeName,
            int flags) {
        EventLog.writeEvent(EventLogTags.CAR_DP_MGR_CREATE_USER_REQ, uid, safeName, flags);
    }

    public static void writeCarDevicePolicyManagerCreateUserResp(int uid, int status) {
        EventLog.writeEvent(EventLogTags.CAR_DP_MGR_CREATE_USER_RESP, uid, status);
    }

    public static void writeCarDevicePolicyManagerStartUserInBackgroundReq(int uid, int userId) {
        EventLog.writeEvent(EventLogTags.CAR_DP_MGR_START_USER_IN_BACKGROUND_REQ, uid, userId);
    }

    public static void writeCarDevicePolicyManagerStartUserInBackgroundResp(int uid, int status) {
        EventLog.writeEvent(EventLogTags.CAR_DP_MGR_START_USER_IN_BACKGROUND_RESP, uid, status);
    }

    public static void writeCarDevicePolicyManagerStopUserReq(int uid, int userId) {
        EventLog.writeEvent(EventLogTags.CAR_DP_MGR_STOP_USER_REQ, uid, userId);
    }

    public static void writeCarDevicePolicyManagerStopUserResp(int uid, int status) {
        EventLog.writeEvent(EventLogTags.CAR_DP_MGR_STOP_USER_RESP, uid, status);
    }

    public static void writePowerPolicyChange(String policy) {
        EventLog.writeEvent(EventLogTags.CAR_PWR_MGR_PWR_POLICY_CHANGE, policy);
    }

    public static void writeCarPowerManagerStateChange(int state) {
        EventLog.writeEvent(EventLogTags.CAR_PWR_MGR_STATE_CHANGE, state);
    }

    public static void writeCarPowerManagerStateRequest(int state, int param) {
        EventLog.writeEvent(EventLogTags.CAR_PWR_MGR_STATE_REQ, state, param);
    }

    public static void writeGarageModeEvent(int status) {
        EventLog.writeEvent(EventLogTags.CAR_PWR_MGR_GARAGE_MODE, status);
    }

    private EventLogHelper() {
        throw new UnsupportedOperationException();
    }
}
