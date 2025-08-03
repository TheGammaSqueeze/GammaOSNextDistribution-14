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
import static android.car.VehiclePropertyIds.INITIAL_USER_INFO;
import static android.car.VehiclePropertyIds.REMOVE_USER;
import static android.car.VehiclePropertyIds.SWITCH_USER;
import static android.car.VehiclePropertyIds.USER_IDENTIFICATION_ASSOCIATION;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.annotation.Nullable;
import android.app.ActivityManager;
import android.car.builtin.util.EventLogHelper;
import android.car.builtin.util.Slogf;
import android.car.user.CarUserManager;
import android.hardware.automotive.vehicle.CreateUserRequest;
import android.hardware.automotive.vehicle.CreateUserResponse;
import android.hardware.automotive.vehicle.CreateUserStatus;
import android.hardware.automotive.vehicle.InitialUserInfoRequestType;
import android.hardware.automotive.vehicle.InitialUserInfoResponse;
import android.hardware.automotive.vehicle.InitialUserInfoResponseAction;
import android.hardware.automotive.vehicle.RemoveUserRequest;
import android.hardware.automotive.vehicle.SwitchUserMessageType;
import android.hardware.automotive.vehicle.SwitchUserRequest;
import android.hardware.automotive.vehicle.SwitchUserResponse;
import android.hardware.automotive.vehicle.SwitchUserStatus;
import android.hardware.automotive.vehicle.UserIdentificationAssociation;
import android.hardware.automotive.vehicle.UserIdentificationAssociationType;
import android.hardware.automotive.vehicle.UserIdentificationGetRequest;
import android.hardware.automotive.vehicle.UserIdentificationResponse;
import android.hardware.automotive.vehicle.UserIdentificationSetAssociation;
import android.hardware.automotive.vehicle.UserIdentificationSetRequest;
import android.hardware.automotive.vehicle.UsersInfo;
import android.hardware.automotive.vehicle.VehiclePropError;
import android.hardware.automotive.vehicle.VehiclePropertyStatus;
import android.os.Handler;
import android.os.ServiceSpecificException;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseBooleanArray;

import com.android.car.CarLocalServices;
import com.android.car.CarLog;
import com.android.car.CarServiceUtils;
import com.android.car.CarStatsLog;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.common.UserHelperLite;
import com.android.car.internal.os.CarSystemProperties;
import com.android.car.internal.util.DebugUtils;
import com.android.car.internal.util.FunctionalUtils;
import com.android.car.user.CarUserService;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Service used to integrate the OEM's custom user management with Android's.
 */
public final class UserHalService extends HalServiceBase {

    @VisibleForTesting
    static final String TAG = CarLog.tagFor(UserHalService.class);

    private static final String UNSUPPORTED_MSG = "Vehicle HAL does not support user management";
    private static final String USER_ASSOCIATION_UNSUPPORTED_MSG =
            "Vehicle HAL does not support user association";

    private static final int[] SUPPORTED_PROPERTIES = new int[]{
            CREATE_USER,
            INITIAL_USER_INFO,
            REMOVE_USER,
            SWITCH_USER,
            USER_IDENTIFICATION_ASSOCIATION
    };

    private static final int[] CORE_PROPERTIES = new int[]{
            CREATE_USER,
            INITIAL_USER_INFO,
            REMOVE_USER,
            SWITCH_USER,
    };

    private static final boolean DBG = Slogf.isLoggable(TAG, Log.DEBUG);

    private final Object mLock = new Object();

    private final VehicleHal mHal;

    @GuardedBy("mLock")
    @Nullable
    private SparseArray<HalPropConfig> mProperties;

    // This handler handles 2 types of messages:
    // - "Anonymous" messages (what=0) containing runnables.
    // - "Identifiable" messages used to check for timeouts (whose 'what' is the request id).
    private final Handler mHandler;

    /**
     * Value used on the next request.
     */
    @GuardedBy("mLock")
    private int mNextRequestId = 1;

    /**
     * Base requestID. RequestID logged for metrics will be mBaseRequestID + original
     * requestID
     */
    private final int mBaseRequestId;

    private final HalPropValueBuilder mPropValueBuilder;

    /**
     * Map of callbacks by request id.
     */
    @GuardedBy("mLock")
    private final SparseArray<PendingRequest<?, ?>> mPendingRequests = new SparseArray<>();

    public UserHalService(VehicleHal hal) {
        this(hal, new Handler(CarServiceUtils.getHandlerThread(
                CarUserService.HANDLER_THREAD_NAME).getLooper()));
    }

    @VisibleForTesting
    UserHalService(VehicleHal hal, Handler handler) {
        if (DBG) {
            Slogf.d(TAG, "DBG enabled");
        }
        mHal = hal;
        mHandler = handler;
        mBaseRequestId = ThreadLocalRandom.current().nextInt(0, Integer.MAX_VALUE);
        mPropValueBuilder = hal.getHalPropValueBuilder();
    }

    @Override
    public void init() {
        if (DBG) Slogf.d(TAG, "init()");

        ArrayList<Integer> props = new ArrayList<>();
        synchronized (mLock) {
            if (mProperties == null) {
                return;
            }

            int size = mProperties.size();
            for (int i = 0; i < size; i++) {
                HalPropConfig config = mProperties.valueAt(i);
                if (VehicleHal.isPropertySubscribable(config)) {
                    props.add(config.getPropId());
                }
            }
        }

        for (int i = 0; i < props.size(); i++) {
            int prop = props.get(i);
            if (DBG) Slogf.d(TAG, "subscribing to property " + prop);
            mHal.subscribePropertySafe(this, prop);
        }
    }

    @Override
    public void release() {
        if (DBG) Slogf.d(TAG, "release()");
    }

    @Override
    public void onHalEvents(List<HalPropValue> values) {
        if (DBG) Slogf.d(TAG, "handleHalEvents(): " + values);

        for (int i = 0; i < values.size(); i++) {
            HalPropValue value = values.get(i);
            switch (value.getPropId()) {
                case INITIAL_USER_INFO:
                    mHandler.post(() -> handleOnInitialUserInfoResponse(value));
                    break;
                case SWITCH_USER:
                    mHandler.post(() -> handleOnSwitchUserResponse(value));
                    break;
                case CREATE_USER:
                    mHandler.post(() -> handleOnCreateUserResponse(value));
                    break;
                case REMOVE_USER:
                    Slogf.w(TAG, "Received REMOVE_USER HAL event: " + value);
                    break;
                case USER_IDENTIFICATION_ASSOCIATION:
                    mHandler.post(() -> handleOnUserIdentificationAssociation(value));
                    break;
                default:
                    Slogf.w(TAG, "received unsupported event from HAL: " + value);
            }
        }
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void onPropertySetError(ArrayList<VehiclePropError> errors) {
        if (DBG) {
            for (VehiclePropError error : errors) {
                Slogf.d(TAG, "handlePropertySetError(" + error.propId + "/" + error.areaId + ")");
            }
        }
    }

    @Override
    public int[] getAllSupportedProperties() {
        return SUPPORTED_PROPERTIES;
    }

    @Override
    public void takeProperties(Collection<HalPropConfig> properties) {
        if (properties.isEmpty()) {
            Slogf.w(TAG, UNSUPPORTED_MSG);
            return;
        }
        SparseArray<HalPropConfig> supportedProperties = new SparseArray<>(5);
        for (HalPropConfig config : properties) {
            supportedProperties.put(config.getPropId(), config);
        }
        synchronized (mLock) {
            mProperties = supportedProperties;
        }
    }

    /**
     * Checks if the Vehicle HAL supports core user management actions.
     */
    public boolean isSupported() {
        if (!CarSystemProperties.getUserHalEnabled().orElse(false)) return false;

        synchronized (mLock) {
            if (mProperties == null) return false;

            for (int i = 0; i < CORE_PROPERTIES.length; i++) {
                if (mProperties.get(CORE_PROPERTIES[i]) == null) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Checks if the Vehicle HAL supports core user management actions.
     */
    public boolean isUserAssociationSupported() {
        synchronized (mLock) {
            return mProperties != null && mProperties.get(USER_IDENTIFICATION_ASSOCIATION) != null;
        }
    }

    private void checkSupported() {
        Preconditions.checkState(isSupported(), UNSUPPORTED_MSG);
    }

    private void checkUserAssociationSupported() {
        Preconditions.checkState(isUserAssociationSupported(), USER_ASSOCIATION_UNSUPPORTED_MSG);
    }

    // Returns mBaseRequestId + originalRequestID. If it overflows, then MOD by Integer.MAX_VALUE
    // This request Id is used for logging data in statsd for metrics. As original request id
    // starts with 1 after every restart, a random id is desired for co-relating metrics on the
    // server side. mBaseRequestId is generated as a random id on each restart.
    private int getRequestIdForStatsLog(int originalRequestId) {
        if (Integer.MAX_VALUE - mBaseRequestId < originalRequestId) {
            // overflow
            return (mBaseRequestId - Integer.MAX_VALUE) + originalRequestId;
        }
        return mBaseRequestId + originalRequestId;
    }

    /**
     * Calls HAL to asynchronously get info about the initial user.
     *
     * @param requestType type of request (as defined by
     * {@link android.hardware.automotive.vehicle.InitialUserInfoRequestType}).
     * @param timeoutMs how long to wait (in ms) for the property change event.
     * @param usersInfo current state of Android users.
     * @param callback to handle the response.
     *
     * @throws IllegalStateException if the HAL does not support user management (callers should
     * call {@link #isSupported()} first to avoid this exception).
     */
    public void getInitialUserInfo(int requestType, int timeoutMs, UsersInfo usersInfo,
            HalCallback<InitialUserInfoResponse> callback) {
        if (DBG) Slogf.d(TAG, "getInitialInfo(" + requestType + ")");
        Preconditions.checkArgumentPositive(timeoutMs, "timeout must be positive");
        Objects.requireNonNull(usersInfo);
        UserHalHelper.checkValid(usersInfo);
        Objects.requireNonNull(callback);
        checkSupported();

        int requestId = getNextRequestId();
        List<Integer> intValues = new ArrayList<>(2);
        intValues.add(requestId);
        intValues.add(requestType);
        UserHalHelper.addUsersInfo(intValues, usersInfo);

        HalPropValue propRequest = mPropValueBuilder.build(INITIAL_USER_INFO, /* areaId= */ 0,
                SystemClock.elapsedRealtime(), VehiclePropertyStatus.AVAILABLE,
                CarServiceUtils.toIntArray(intValues));

        synchronized (mLock) {
            if (hasPendingRequestLocked(InitialUserInfoResponse.class, callback)) return;
            addPendingRequestLocked(requestId, InitialUserInfoResponse.class, callback);
        }

        EventLogHelper.writeCarUserHalInitialUserInfoReq(requestId, requestType, timeoutMs);
        CarStatsLog.write(CarStatsLog.CAR_USER_HAL_INITIAL_USER_INFO_REQUEST_REPORTED,
                getRequestIdForStatsLog(requestId),
                getInitialUserInfoRequestTypeForStatsd(requestType), timeoutMs);

        sendHalRequest(requestId, timeoutMs, propRequest, callback);
    }

    private static int getInitialUserInfoRequestTypeForStatsd(int requestType) {
        // CHECKSTYLE:OFF IndentationCheck
        switch (requestType) {
            case InitialUserInfoRequestType.FIRST_BOOT:
                return CarStatsLog
                        .CAR_USER_HAL_INITIAL_USER_INFO_REQUEST_REPORTED__REQUEST_TYPE__FIRST_BOOT;
            case InitialUserInfoRequestType.FIRST_BOOT_AFTER_OTA:
                return CarStatsLog
               .CAR_USER_HAL_INITIAL_USER_INFO_REQUEST_REPORTED__REQUEST_TYPE__FIRST_BOOT_AFTER_OTA;
            case InitialUserInfoRequestType.COLD_BOOT:
                return CarStatsLog
                        .CAR_USER_HAL_INITIAL_USER_INFO_REQUEST_REPORTED__REQUEST_TYPE__COLD_BOOT;
            case InitialUserInfoRequestType.RESUME:
                return CarStatsLog
                        .CAR_USER_HAL_INITIAL_USER_INFO_REQUEST_REPORTED__REQUEST_TYPE__RESUME;
            default:
                return CarStatsLog
                        .CAR_USER_HAL_INITIAL_USER_INFO_REQUEST_REPORTED__REQUEST_TYPE__UNKNOWN;
        }
        // CHECKSTYLE:ON IndentationCheck
    }

    private void sendHalRequest(int requestId, int timeoutMs, HalPropValue request,
            HalCallback<?> callback) {
        mHandler.postDelayed(() -> handleCheckIfRequestTimedOut(requestId), requestId, timeoutMs);
        try {
            if (DBG) Slogf.d(TAG, "Calling hal.set(): " + request);
            mHal.set(request);
        } catch (ServiceSpecificException e) {
            handleRemovePendingRequest(requestId);
            Slogf.w(TAG, "Failed to set " + request, e);
            callback.onResponse(HalCallback.STATUS_HAL_SET_TIMEOUT, null);
        }
    }

    /**
     * Calls HAL to asynchronously switch user.
     *
     * @param request metadata
     * @param timeoutMs how long to wait (in ms) for the property change event.
     * @param callback to handle the response.
     *
     * @throws IllegalStateException if the HAL does not support user management (callers should
     * call {@link #isSupported()} first to avoid this exception).
     */
    public void switchUser(SwitchUserRequest request, int timeoutMs,
            HalCallback<SwitchUserResponse> callback) {
        Preconditions.checkArgumentPositive(timeoutMs, "timeout must be positive");
        Objects.requireNonNull(callback, "callback cannot be null");
        Objects.requireNonNull(request, "request cannot be null");
        if (DBG) Slogf.d(TAG, "switchUser(" + request + ")");

        checkSupported();
        request.requestId = getNextRequestId();
        request.messageType = SwitchUserMessageType.ANDROID_SWITCH;
        HalPropValue propRequest = UserHalHelper.toHalPropValue(mPropValueBuilder, request);

        synchronized (mLock) {
            if (hasPendingRequestLocked(SwitchUserResponse.class, callback)) return;
            addPendingRequestLocked(request.requestId, SwitchUserResponse.class, callback);
        }

        EventLogHelper.writeCarUserHalSwitchUserReq(request.requestId, request.targetUser.userId,
                request.targetUser.flags, timeoutMs);
        CarStatsLog.write(CarStatsLog.CAR_USER_HAL_MODIFY_USER_REQUEST_REPORTED,
                getRequestIdForStatsLog(request.requestId),
                CarStatsLog
                .CAR_USER_HAL_MODIFY_USER_REQUEST_REPORTED__REQUEST_TYPE__SWITCH_REQUEST_ANDROID,
                request.usersInfo.currentUser.userId, request.usersInfo.currentUser.flags,
                request.targetUser.userId, request.targetUser.flags, timeoutMs);

        sendHalRequest(request.requestId, timeoutMs, propRequest, callback);
    }

    /**
     * Calls HAL to remove user.
     *
     * @throws IllegalStateException if the HAL does not support user management (callers should
     * call {@link #isSupported()} first to avoid this exception).
     */
    public void removeUser(RemoveUserRequest request) {
        Objects.requireNonNull(request, "request cannot be null");
        if (DBG) Slogf.d(TAG, "removeUser(" + request + ")");

        checkSupported();
        request.requestId = getNextRequestId();
        HalPropValue propRequest = UserHalHelper.toHalPropValue(mPropValueBuilder, request);

        EventLogHelper.writeCarUserHalRemoveUserReq(request.removedUserInfo.userId,
                request.usersInfo.currentUser.userId);
        CarStatsLog.write(CarStatsLog.CAR_USER_HAL_MODIFY_USER_REQUEST_REPORTED,
                getRequestIdForStatsLog(request.requestId),
                CarStatsLog
                .CAR_USER_HAL_MODIFY_USER_REQUEST_REPORTED__REQUEST_TYPE__REMOVE_REQUEST,
                request.usersInfo.currentUser.userId, request.usersInfo.currentUser.flags,
                request.removedUserInfo.userId, request.removedUserInfo.flags, /* timeout */ -1);

        try {
            if (DBG) Slogf.d(TAG, "Calling hal.set(): " + propRequest);
            mHal.set(propRequest);
        } catch (ServiceSpecificException e) {
            Slogf.w(TAG, "Failed to set REMOVE USER", e);
        }
    }

    /**
     * Calls HAL to indicate an Android user was created.
     *
     * @param request info about the created user.
     * @param timeoutMs how long to wait (in ms) for the property change event.
     * @param callback to handle the response.
     *
     * @throws IllegalStateException if the HAL does not support user management (callers should
     * call {@link #isSupported()} first to avoid this exception).
     */
    public void createUser(CreateUserRequest request, int timeoutMs,
            HalCallback<CreateUserResponse> callback) {
        Objects.requireNonNull(request);
        Preconditions.checkArgumentPositive(timeoutMs, "timeout must be positive");
        Objects.requireNonNull(callback);
        if (DBG) Slogf.d(TAG, "createUser(): req=" + request + ", timeout=" + timeoutMs);

        checkSupported();
        request.requestId = getNextRequestId();
        HalPropValue propRequest = UserHalHelper.toHalPropValue(mPropValueBuilder, request);

        synchronized (mLock) {
            if (hasPendingRequestLocked(CreateUserResponse.class, callback)) return;
            addPendingRequestLocked(request.requestId, CreateUserResponse.class, callback);
        }

        EventLogHelper.writeCarUserHalCreateUserReq(request.requestId,
                UserHelperLite.safeName(request.newUserName), request.newUserInfo.flags, timeoutMs);
        CarStatsLog.write(CarStatsLog.CAR_USER_HAL_MODIFY_USER_REQUEST_REPORTED,
                getRequestIdForStatsLog(request.requestId),
                CarStatsLog
                .CAR_USER_HAL_MODIFY_USER_REQUEST_REPORTED__REQUEST_TYPE__CREATE_REQUEST,
                request.usersInfo.currentUser.userId, request.usersInfo.currentUser.flags,
                request.newUserInfo.userId, request.newUserInfo.flags, timeoutMs);

        sendHalRequest(request.requestId, timeoutMs, propRequest, callback);
    }

    /**
     * Calls HAL after android user switch.
     */
    public void postSwitchResponse(SwitchUserRequest request) {
        Objects.requireNonNull(request, "request cannot be null");
        if (DBG) Slogf.d(TAG, "postSwitchResponse(" + request + ")");

        checkSupported();
        request.messageType = SwitchUserMessageType.ANDROID_POST_SWITCH;
        HalPropValue propRequest = UserHalHelper.toHalPropValue(mPropValueBuilder, request);

        EventLogHelper.writeCarUserHalPostSwitchUserReq(request.requestId,
                request.targetUser.userId, request.usersInfo.currentUser.userId);
        CarStatsLog.write(CarStatsLog.CAR_USER_HAL_POST_SWITCH_RESPONSE_REPORTED,
                getRequestIdForStatsLog(request.requestId),
                request.targetUser.userId == request.usersInfo.currentUser.userId
                ? CarStatsLog.CAR_USER_HAL_POST_SWITCH_RESPONSE_REPORTED__SWITCH_STATUS__SUCCESS
                : CarStatsLog.CAR_USER_HAL_POST_SWITCH_RESPONSE_REPORTED__SWITCH_STATUS__FAILURE);

        try {
            if (DBG) Slogf.d(TAG, "Calling hal.set(): " + propRequest);
            mHal.set(propRequest);
        } catch (ServiceSpecificException e) {
            Slogf.w(TAG, "Failed to set ANDROID POST SWITCH", e);
        }
    }

    /**
     * Calls HAL to switch user after legacy Android user switch. Legacy Android user switch means
     * user switch is not requested by {@link CarUserManager} or OEM, and user switch is directly
     * requested by {@link ActivityManager}
     */
    public void legacyUserSwitch(SwitchUserRequest request) {
        Objects.requireNonNull(request, "request cannot be null");
        if (DBG) Slogf.d(TAG, "userSwitchLegacy(" + request + ")");

        checkSupported();
        request.requestId = getNextRequestId();
        request.messageType = SwitchUserMessageType.LEGACY_ANDROID_SWITCH;
        HalPropValue propRequest = UserHalHelper.toHalPropValue(mPropValueBuilder, request);

        EventLogHelper.writeCarUserHalLegacySwitchUserReq(request.requestId,
                request.targetUser.userId, request.usersInfo.currentUser.userId);
        //CHECKSTYLE:OFF IndentationCheck
        CarStatsLog.write(CarStatsLog.CAR_USER_HAL_MODIFY_USER_REQUEST_REPORTED,
                getRequestIdForStatsLog(request.requestId), CarStatsLog
                .CAR_USER_HAL_MODIFY_USER_REQUEST_REPORTED__REQUEST_TYPE__SWITCH_REQUEST_LEGACY,
                request.usersInfo.currentUser.userId, request.usersInfo.currentUser.flags,
                request.targetUser.userId, request.targetUser.flags, /* timeout_ms= */ -1);
        //CHECKSTYLE:ON IndentationCheck

        try {
            if (DBG) Slogf.d(TAG, "Calling hal.set(): " + propRequest);
            mHal.set(propRequest);
        } catch (ServiceSpecificException e) {
            Slogf.w(TAG, "Failed to set LEGACY ANDROID SWITCH", e);
        }
    }

    /**
     * Calls HAL to get the value of the user identifications associated with the given user.
     *
     * @return HAL response or {@code null} if it was invalid (for example, mismatch on the
     * requested number of associations).
     */
    @Nullable
    public UserIdentificationResponse getUserAssociation(UserIdentificationGetRequest request) {
        Objects.requireNonNull(request, "request cannot be null");
        checkUserAssociationSupported();

        // Check that it doesn't have dupes
        SparseBooleanArray types = new SparseBooleanArray(request.numberAssociationTypes);
        for (int i = 0; i < request.numberAssociationTypes; i++) {
            int type = request.associationTypes[i];
            Preconditions.checkArgument(!types.get(type), "type %s found more than once on %s",
                    DebugUtils.constantToString(UserIdentificationAssociationType.class, type),
                    request);
            types.put(type, true);
        }

        request.requestId = getNextRequestId();

        if (DBG) Slogf.d(TAG, "getUserAssociation(): req=" + request);

        HalPropValue requestAsPropValue = UserHalHelper.toHalPropValue(mPropValueBuilder,
                request);

        EventLogHelper.writeCarUserHalGetUserAuthReq((Object[]) toIntArray(requestAsPropValue));
        HalPropValue responseAsPropValue;
        try {
            responseAsPropValue = mHal.get(requestAsPropValue);
        } catch (ServiceSpecificException e) {
            Slogf.w(TAG, "HAL returned error for request " + requestAsPropValue, e);
            return null;
        }
        if (responseAsPropValue == null) {
            Slogf.w(TAG, "HAL returned null for request " + requestAsPropValue);
            return null;
        }

        EventLogHelper
                .writeCarUserHalGetUserAuthResp(getEventDataWithErrorMessage(responseAsPropValue));
        if (DBG) Slogf.d(TAG, "getUserAssociation(): responseAsPropValue=" + responseAsPropValue);

        UserIdentificationResponse response;
        try {
            response = UserHalHelper.toUserIdentificationResponse(responseAsPropValue);
        } catch (IllegalArgumentException e) {
            Slogf.w(TAG, "invalid response from HAL for " + requestAsPropValue, e);
            return null;
        }
        if (DBG) Slogf.d(TAG, "getUserAssociation(): response=" + response);

        // Validate the response according to the request
        if (response.requestId != request.requestId) {
            Slogf.w(TAG, "invalid request id (should be " + request.requestId + ") on HAL "
                    + "response: " + response);
            return null;
        }
        if (response.numberAssociation != request.numberAssociationTypes) {
            Slogf.w(TAG, "Wrong number of association types on HAL response (expected "
                    + request.numberAssociationTypes + ") for request " + requestAsPropValue
                    + ": " + response);
            return null;
        }
        for (int i = 0; i < request.numberAssociationTypes; i++) {
            int expectedType = request.associationTypes[i];
            int actualType = response.associations[i].type;
            if (actualType != expectedType) {
                Slogf.w(TAG, "Wrong type on index " + i + " of HAL response (" + response + ") for "
                        + "request " + requestAsPropValue + " : expected "
                        + DebugUtils.constantToString(
                                UserIdentificationAssociationType.class, expectedType)
                        + ", got " + DebugUtils.constantToString(
                                UserIdentificationAssociationType.class, actualType));
                return null;
            }
        }

        // TODO(b/153900032): move this logic to a common helper
        int[] associationTypes = new int[response.numberAssociation];
        int[] associationValues = new int[response.numberAssociation];
        for (int i = 0; i < response.numberAssociation; i++) {
            UserIdentificationAssociation association = response.associations[i];
            associationTypes[i] = association.type;
            associationValues[i] = association.value;
        }

        CarStatsLog.write(CarStatsLog.CAR_USER_HAL_USER_ASSOCIATION_REQUEST_REPORTED,
                getRequestIdForStatsLog(request.requestId),
                CarStatsLog.CAR_USER_HAL_USER_ASSOCIATION_REQUEST_REPORTED__REQUEST_TYPE__GET,
                request.userInfo.userId,
                request.userInfo.flags,
                request.numberAssociationTypes,
                Arrays.toString(associationTypes), Arrays.toString(associationValues));

        return response;
    }

    /**
     * Calls HAL to set the value of the user identifications associated with the given user.
     *
     * @throws IllegalArgumentException if request is invalid (mismatch on number of associations,
     *   duplicated association, invalid association type values, etc).
     */
    public void setUserAssociation(int timeoutMs, UserIdentificationSetRequest request,
            HalCallback<UserIdentificationResponse> callback) {
        Preconditions.checkArgumentPositive(timeoutMs, "timeout must be positive");
        Objects.requireNonNull(request, "request cannot be null");
        Objects.requireNonNull(callback, "callback cannot be null");
        if (DBG) Slogf.d(TAG, "setUserAssociation(" + request + ")");

        // Check that it doesn't have dupes
        SparseBooleanArray types = new SparseBooleanArray(request.numberAssociations);
        for (int i = 0; i < request.numberAssociations; i++) {
            int type = request.associations[i].type;
            Preconditions.checkArgument(!types.get(type), "type %s found more than once on %s",
                    DebugUtils.constantToString(UserIdentificationAssociationType.class, type),
                    request);
            types.put(type, true);
        }

        checkUserAssociationSupported();
        request.requestId = getNextRequestId();
        HalPropValue propRequest = UserHalHelper.toHalPropValue(mPropValueBuilder, request);

        synchronized (mLock) {
            if (hasPendingRequestLocked(UserIdentificationResponse.class, callback)) return;
            addPendingRequestLocked(request.requestId, UserIdentificationResponse.class, request,
                    callback);
        }

        EventLogHelper.writeCarUserHalSetUserAuthReq((Object[]) toIntArray(propRequest));
        // TODO(b/153900032): move this logic to a common helper
        int[] associationTypes = new int[request.numberAssociations];
        int[] associationValues = new int[request.numberAssociations];
        for (int i = 0; i < request.numberAssociations; i++) {
            UserIdentificationSetAssociation association = request.associations[i];
            associationTypes[i] = association.type;
            associationValues[i] = association.value;
        }
        CarStatsLog.write(CarStatsLog.CAR_USER_HAL_USER_ASSOCIATION_REQUEST_REPORTED,
                getRequestIdForStatsLog(request.requestId),
                CarStatsLog.CAR_USER_HAL_USER_ASSOCIATION_REQUEST_REPORTED__REQUEST_TYPE__SET,
                request.userInfo.userId, request.userInfo.flags, request.numberAssociations,
                Arrays.toString(associationTypes), Arrays.toString(associationValues));
        sendHalRequest(request.requestId, timeoutMs, propRequest, callback);
    }

    private void handleOnUserIdentificationAssociation(HalPropValue value) {
        EventLogHelper.writeCarUserHalSetUserAuthResp(getEventDataWithErrorMessage(value));

        if (DBG) Slogf.d(TAG, "handleOnUserIdentificationAssociation(): " + value);

        int requestId = value.getInt32Value(0);
        HalCallback<UserIdentificationResponse> callback = handleGetPendingCallback(requestId,
                UserIdentificationResponse.class);
        if (callback == null) {
            Slogf.w(TAG, "no callback for requestId " + requestId + ": " + value);
            return;
        }
        PendingRequest<?, ?> pendingRequest = handleRemovePendingRequest(requestId);
        UserIdentificationResponse response;
        try {
            response = UserHalHelper.toUserIdentificationResponse(value);
        } catch (RuntimeException e) {
            Slogf.w(TAG, "error parsing UserIdentificationResponse (" + value + ")", e);
            callback.onResponse(HalCallback.STATUS_WRONG_HAL_RESPONSE, null);
            CarStatsLog.write(CarStatsLog.CAR_USER_HAL_SET_USER_ASSOCIATION_RESPONSE_REPORTED,
                    getRequestIdForStatsLog(requestId),
                    getHalCallbackStatusForStatsd(HalCallback.STATUS_WRONG_HAL_RESPONSE),
                    /* number_associations= */ 0, /* user_identification_association_types= */ "",
                    /* user_identification_association_values= */ "");
            return;
        }

        // Validate the response according to the request
        UserIdentificationSetRequest request = PendingRequest.getRequest(pendingRequest,
                UserIdentificationSetRequest.class, requestId);

        if (request == null) {
            // already logged on PendingRequest.getRequest
            callback.onResponse(HalCallback.STATUS_WRONG_HAL_RESPONSE, null);
            logSetUserAssociationResponse(requestId, response,
                    HalCallback.STATUS_WRONG_HAL_RESPONSE);
            return;
        }

        if (response.numberAssociation != request.numberAssociations) {
            Slogf.w(TAG, "Wrong number of association types on HAL response (expected "
                    + request.numberAssociations + ") for request " + request
                    + ": " + response);
            callback.onResponse(HalCallback.STATUS_WRONG_HAL_RESPONSE, null);
            logSetUserAssociationResponse(requestId, response,
                    HalCallback.STATUS_WRONG_HAL_RESPONSE);
            return;
        }

        for (int i = 0; i < request.numberAssociations; i++) {
            int expectedType = request.associations[i].type;
            int actualType = response.associations[i].type;
            if (actualType != expectedType) {
                Slogf.w(TAG, "Wrong type on index " + i + " of HAL response (" + response + ") for "
                        + "request " + value + " : expected "
                        + DebugUtils.constantToString(UserIdentificationAssociationType.class,
                                expectedType)
                        + ", got "
                        + DebugUtils.constantToString(UserIdentificationAssociationType.class,
                                actualType));
                callback.onResponse(HalCallback.STATUS_WRONG_HAL_RESPONSE, null);
                logSetUserAssociationResponse(requestId, response,
                        HalCallback.STATUS_WRONG_HAL_RESPONSE);
                return;
            }
        }

        if (DBG) Slogf.d(TAG, "replying to request " + requestId + " with " + response);
        callback.onResponse(HalCallback.STATUS_OK, response);
        logSetUserAssociationResponse(requestId, response, HalCallback.STATUS_OK);
    }

    private void logSetUserAssociationResponse(int requestId, UserIdentificationResponse response,
            int halCallbackStatus) {
        // TODO(b/153900032): move this logic to a common helper
        int[] associationTypes = new int[response.numberAssociation];
        int[] associationValues = new int[response.numberAssociation];
        for (int i = 0; i < response.numberAssociation; i++) {
            UserIdentificationAssociation association = response.associations[i];
            associationTypes[i] = association.type;
            associationValues[i] = association.value;
        }
        CarStatsLog.write(CarStatsLog.CAR_USER_HAL_SET_USER_ASSOCIATION_RESPONSE_REPORTED,
                getRequestIdForStatsLog(requestId),
                getHalCallbackStatusForStatsd(halCallbackStatus), response.numberAssociation,
                Arrays.toString(associationTypes), Arrays.toString(associationValues));
    }

    private static Object[] getEventDataWithErrorMessage(HalPropValue value) {
        if (TextUtils.isEmpty(value.getStringValue())) {
            return (Object[]) toIntArray(value);
        } else {
            // Must manually append the error message to the array of values
            int size = value.getInt32ValuesSize();
            Object[] list = new Object[size + 1];
            for (int i = 0; i < size; i++) {
                list[i] = value.getInt32Value(i);
            }
            list[list.length - 1] = value.getStringValue();
            return list;
        }
    }

    private static Integer[] toIntArray(HalPropValue value) {
        int size = value.getInt32ValuesSize();
        Integer[] list = new Integer[size];
        for (int i = 0; i < size; i++) {
            list[i] = value.getInt32Value(i);
        }
        return list;
    }

    @VisibleForTesting
    int getNextRequestId() {
        synchronized (mLock) {
            return mNextRequestId++;
        }
    }

    @GuardedBy("mLock")
    private <REQ, RESP> void addPendingRequestLocked(int requestId, Class<RESP> responseClass,
            REQ request, HalCallback<RESP> callback) {
        PendingRequest<?, RESP> pendingRequest = new PendingRequest<>(responseClass, request,
                callback);
        if (DBG) {
            Slogf.d(TAG, "adding pending request (" + pendingRequest + ") for requestId "
                    + requestId);
        }
        mPendingRequests.put(requestId, pendingRequest);
    }

    @GuardedBy("mLock")
    private <RESP> void addPendingRequestLocked(int requestId, Class<RESP> responseClass,
            HalCallback<RESP> callback) {
        addPendingRequestLocked(requestId, responseClass, /* request= */ null,
                callback);
    }

    /**
     * Checks if there is a pending request of type {@code requestClass}, calling {@code callback}
     * with {@link HalCallback#STATUS_CONCURRENT_OPERATION} when there is.
     */
    @GuardedBy("mLock")
    private boolean hasPendingRequestLocked(Class<?> responseClass, HalCallback<?> callback) {
        for (int i = 0; i < mPendingRequests.size(); i++) {
            PendingRequest<?, ?> pendingRequest = mPendingRequests.valueAt(i);
            if (pendingRequest.responseClass == responseClass) {
                Slogf.w(TAG, "Already have pending request of type " + responseClass);
                callback.onResponse(HalCallback.STATUS_CONCURRENT_OPERATION, null);
                return true;
            }
        }
        return false;
    }

    /**
     * Removes the pending request and its timeout callback.
     */
    @Nullable
    private PendingRequest<?, ?> handleRemovePendingRequest(int requestId) {
        if (DBG) Slogf.d(TAG, "Removing pending request #" + requestId);
        mHandler.removeMessages(requestId);
        PendingRequest<?, ?> pendingRequest;
        synchronized (mLock) {
            pendingRequest = mPendingRequests.get(requestId);
            mPendingRequests.remove(requestId);
        }
        return pendingRequest;
    }

    private void handleCheckIfRequestTimedOut(int requestId) {
        PendingRequest<?, ?> pendingRequest = getPendingRequest(requestId);
        if (pendingRequest == null) return;

        Slogf.w(TAG, "Request #" + requestId + " timed out");
        handleRemovePendingRequest(requestId);
        pendingRequest.callback.onResponse(HalCallback.STATUS_HAL_RESPONSE_TIMEOUT, null);
    }

    @Nullable
    private PendingRequest<?, ?> getPendingRequest(int requestId) {
        synchronized (mLock) {
            return mPendingRequests.get(requestId);
        }
    }

    private void handleOnInitialUserInfoResponse(HalPropValue value) {
        int requestId = value.getInt32Value(0);
        HalCallback<InitialUserInfoResponse> callback = handleGetPendingCallback(requestId,
                InitialUserInfoResponse.class);
        if (callback == null) {
            EventLogHelper.writeCarUserHalInitialUserInfoResp(requestId,
                    HalCallback.STATUS_INVALID, /* action= */ 0, /* userId= */ 0,
                    /* flags= */ 0, /* safeName= */ "", /* userLocales- */ "");
            CarStatsLog.write(CarStatsLog.CAR_USER_HAL_INITIAL_USER_INFO_RESPONSE_REPORTED,
                    getRequestIdForStatsLog(requestId),
                    getHalCallbackStatusForStatsd(HalCallback.STATUS_INVALID),
                    getInitialUserInfoResponseActionForStatsd(
                            InitialUserInfoResponseAction.DEFAULT),
                    /* user id= */ -1, /* flag= */ -1, /* user locales= */ "");

            Slogf.w(TAG, "no callback for requestId " + requestId + ": " + value);
            return;
        }
        handleRemovePendingRequest(requestId);

        InitialUserInfoResponse response;
        try {
            response = UserHalHelper.toInitialUserInfoResponse(value);
        } catch (RuntimeException e) {
            Slogf.e(TAG, "invalid response (" + value + ") from HAL", e);
            EventLogHelper.writeCarUserHalInitialUserInfoResp(requestId,
                    HalCallback.STATUS_WRONG_HAL_RESPONSE, /* action= */ 0, /* userId= */ 0,
                    /* flags= */ 0, /* safeName= */ "", /* userLocales- */ "");
            CarStatsLog.write(CarStatsLog.CAR_USER_HAL_INITIAL_USER_INFO_RESPONSE_REPORTED,
                    getRequestIdForStatsLog(requestId),
                    getHalCallbackStatusForStatsd(HalCallback.STATUS_WRONG_HAL_RESPONSE),
                    getInitialUserInfoResponseActionForStatsd(
                            InitialUserInfoResponseAction.DEFAULT),
                    /* user id= */ -1, /* flag= */ -1, /* user locales= */ "");

            callback.onResponse(HalCallback.STATUS_WRONG_HAL_RESPONSE, null);
            return;
        }

        EventLogHelper.writeCarUserHalInitialUserInfoResp(requestId,
                HalCallback.STATUS_OK, response.action,
                response.userToSwitchOrCreate.userId, response.userToSwitchOrCreate.flags,
                response.userNameToCreate, response.userLocales);
        CarStatsLog.write(CarStatsLog.CAR_USER_HAL_INITIAL_USER_INFO_RESPONSE_REPORTED,
                getRequestIdForStatsLog(requestId),
                getHalCallbackStatusForStatsd(HalCallback.STATUS_OK),
                getInitialUserInfoResponseActionForStatsd(response.action),
                response.userToSwitchOrCreate.userId, response.userToSwitchOrCreate.flags,
                response.userLocales);

        if (DBG) Slogf.d(TAG, "replying to request " + requestId + " with " + response);
        callback.onResponse(HalCallback.STATUS_OK, response);
    }

    private static int getInitialUserInfoResponseActionForStatsd(int action) {
        switch (action) {
            case InitialUserInfoResponseAction.CREATE:
                return CarStatsLog
                        .CAR_USER_HAL_INITIAL_USER_INFO_RESPONSE_REPORTED__RESPONSE_ACTION__CREATE;
            case InitialUserInfoResponseAction.SWITCH:
                return CarStatsLog
                        .CAR_USER_HAL_INITIAL_USER_INFO_RESPONSE_REPORTED__RESPONSE_ACTION__SWITCH;
            default:
                return CarStatsLog
                        .CAR_USER_HAL_INITIAL_USER_INFO_RESPONSE_REPORTED__RESPONSE_ACTION__DEFAULT;
        }
    }

    private void handleOnSwitchUserResponse(HalPropValue value) {
        int requestId = value.getInt32Value(0);
        int messageType = value.getInt32Value(1);

        if (messageType == SwitchUserMessageType.VEHICLE_RESPONSE) {
            handleOnSwitchUserVehicleResponse(value);
            return;
        }

        if (messageType == SwitchUserMessageType.VEHICLE_REQUEST) {
            handleOnSwitchUserVehicleRequest(value);
            return;
        }

        Slogf.e(TAG, "handleOnSwitchUserResponse invalid message type (" + messageType
                + ") from HAL: " + value);

        // check if a callback exists for the request ID
        HalCallback<SwitchUserResponse> callback =
                handleGetPendingCallback(requestId, SwitchUserResponse.class);
        if (callback != null) {
            handleRemovePendingRequest(requestId);
            EventLogHelper.writeCarUserHalSwitchUserResp(requestId,
                    HalCallback.STATUS_WRONG_HAL_RESPONSE, /* result= */ 0, /* errorMessage= */ "");
            callback.onResponse(HalCallback.STATUS_WRONG_HAL_RESPONSE, null);
            return;
        }
    }

    private void handleOnSwitchUserVehicleRequest(HalPropValue value) {
        int requestId = value.getInt32Value(0);
        // Index 1 is message type, which is not required in this call.
        int targetUserId = value.getInt32Value(2);
        EventLogHelper.writeCarUserHalOemSwitchUserReq(requestId, targetUserId);
        CarStatsLog.write(CarStatsLog.CAR_USER_HAL_MODIFY_USER_REQUEST_REPORTED,
                getRequestIdForStatsLog(requestId),
                CarStatsLog
                    .CAR_USER_HAL_MODIFY_USER_REQUEST_REPORTED__REQUEST_TYPE__SWITCH_REQUEST_OEM,
                    /* current user id= */ -1, /* current user flag= */ -1, targetUserId,
                    /* target user flag= */ -1, /* timeout_ms= */ -1);

        // HAL vehicle request should have negative request ID
        if (requestId >= 0) {
            Slogf.e(TAG, "handleVehicleRequest invalid requestId (" + requestId + ") from HAL: "
                    + value);
            return;
        }

        CarUserService userService = CarLocalServices.getService(CarUserService.class);
        userService.switchAndroidUserFromHal(requestId, targetUserId);
    }

    private void handleOnSwitchUserVehicleResponse(HalPropValue value) {
        int requestId = value.getInt32Value(0);
        HalCallback<SwitchUserResponse> callback =
                handleGetPendingCallback(requestId, SwitchUserResponse.class);
        if (callback == null) {
            EventLogHelper.writeCarUserHalSwitchUserResp(requestId,
                    HalCallback.STATUS_INVALID, /* result= */ 0, /* errorMessage= */ "");
            Slogf.w(TAG, "no callback for requestId " + requestId + ": " + value);
            logHalSwitchUserResponse(requestId, HalCallback.STATUS_WRONG_HAL_RESPONSE);
            return;
        }
        handleRemovePendingRequest(requestId);
        SwitchUserResponse response = new SwitchUserResponse();
        response.requestId = requestId;
        response.messageType = value.getInt32Value(1);
        response.status = value.getInt32Value(2);
        response.errorMessage = value.getStringValue();
        if (response.status == SwitchUserStatus.SUCCESS
                || response.status == SwitchUserStatus.FAILURE) {
            if (DBG) {
                Slogf.d(TAG, "replying to request " + requestId + " with " + response);
            }
            EventLogHelper.writeCarUserHalSwitchUserResp(requestId,
                    HalCallback.STATUS_OK, response.status, response.errorMessage);
            callback.onResponse(HalCallback.STATUS_OK, response);
            logHalSwitchUserResponse(requestId, HalCallback.STATUS_OK, response.status);
        } else {
            EventLogHelper.writeCarUserHalSwitchUserResp(requestId,
                    HalCallback.STATUS_WRONG_HAL_RESPONSE, response.status, response.errorMessage);
            Slogf.e(TAG, "invalid status (" + response.status + ") from HAL: " + value);
            callback.onResponse(HalCallback.STATUS_WRONG_HAL_RESPONSE, null);
            logHalSwitchUserResponse(requestId, HalCallback.STATUS_WRONG_HAL_RESPONSE,
                    response.status);
        }
    }

    private void handleOnCreateUserResponse(HalPropValue value) {
        int requestId = value.getInt32Value(0);
        HalCallback<CreateUserResponse> callback =
                handleGetPendingCallback(requestId, CreateUserResponse.class);
        if (callback == null) {
            EventLogHelper.writeCarUserHalCreateUserResp(requestId,
                    HalCallback.STATUS_INVALID, /* result= */ 0, /* errorMessage= */ "");
            Slogf.w(TAG, "no callback for requestId " + requestId + ": " + value);
            return;
        }
        handleRemovePendingRequest(requestId);
        CreateUserResponse response = new CreateUserResponse();
        response.requestId = requestId;
        response.status = value.getInt32Value(1);
        response.errorMessage = value.getStringValue();
        if (response.status == CreateUserStatus.SUCCESS
                || response.status == CreateUserStatus.FAILURE) {
            if (DBG) {
                Slogf.d(TAG, "replying to request " + requestId + " with " + response);
            }
            EventLogHelper.writeCarUserHalCreateUserResp(requestId,
                    HalCallback.STATUS_OK, response.status, response.errorMessage);
            callback.onResponse(HalCallback.STATUS_OK, response);
            logHalCreateUserResponse(requestId, HalCallback.STATUS_OK, response.status);
        } else {
            EventLogHelper.writeCarUserHalCreateUserResp(requestId,
                    HalCallback.STATUS_WRONG_HAL_RESPONSE, response.status, response.errorMessage);
            Slogf.e(TAG, "invalid status (" + response.status + ") from HAL: " + value);
            callback.onResponse(HalCallback.STATUS_WRONG_HAL_RESPONSE, null);
            logHalCreateUserResponse(requestId, HalCallback.STATUS_WRONG_HAL_RESPONSE);
        }
    }

    private void logHalSwitchUserResponse(int requestId, int halCallbackStatus) {
        //CHECKSTYLE:OFF IndentationCheck
        CarStatsLog.write(CarStatsLog.CAR_USER_HAL_MODIFY_USER_RESPONSE_REPORTED,
                getRequestIdForStatsLog(requestId),
                getHalCallbackStatusForStatsd(halCallbackStatus),
               CarStatsLog.CAR_USER_HAL_MODIFY_USER_RESPONSE_REPORTED__REQUEST_STATUS__UNSPECIFIED);
        //CHECKSTYLE:ON IndentationCheck
    }

    private void logHalSwitchUserResponse(int requestId, int halCallbackStatus,
            int userSwitchstatus) {
        int userSwitchstatusForStatsd = userSwitchstatus == SwitchUserStatus.SUCCESS
                ? CarStatsLog.CAR_USER_HAL_MODIFY_USER_RESPONSE_REPORTED__REQUEST_STATUS__SUCCESS
                : CarStatsLog.CAR_USER_HAL_MODIFY_USER_RESPONSE_REPORTED__REQUEST_STATUS__FAILURE;
        CarStatsLog.write(CarStatsLog.CAR_USER_HAL_MODIFY_USER_RESPONSE_REPORTED,
                getRequestIdForStatsLog(requestId),
                getHalCallbackStatusForStatsd(halCallbackStatus), userSwitchstatusForStatsd);
    }

    private void logHalCreateUserResponse(int requestId, int halCallbackStatus) {
        //CHECKSTYLE:OFF IndentationCheck
        CarStatsLog.write(CarStatsLog.CAR_USER_HAL_MODIFY_USER_RESPONSE_REPORTED,
                getRequestIdForStatsLog(requestId),
                getHalCallbackStatusForStatsd(halCallbackStatus),
               CarStatsLog.CAR_USER_HAL_MODIFY_USER_RESPONSE_REPORTED__REQUEST_STATUS__UNSPECIFIED);
        //CHECKSTYLE:ON IndentationCheck
    }

    private void logHalCreateUserResponse(int requestId, int halCallbackStatus,
            int userCreatestatus) {
        int userCreatestatusForStatsd = userCreatestatus == CreateUserStatus.SUCCESS
                ? CarStatsLog.CAR_USER_HAL_MODIFY_USER_RESPONSE_REPORTED__REQUEST_STATUS__SUCCESS
                : CarStatsLog.CAR_USER_HAL_MODIFY_USER_RESPONSE_REPORTED__REQUEST_STATUS__FAILURE;
        CarStatsLog.write(CarStatsLog.CAR_USER_HAL_MODIFY_USER_RESPONSE_REPORTED,
                getRequestIdForStatsLog(requestId),
                getHalCallbackStatusForStatsd(halCallbackStatus), userCreatestatusForStatsd);
    }

    private int getHalCallbackStatusForStatsd(int halCallbackStatus) {
        // CHECKSTYLE:OFF IndentationCheck
        switch (halCallbackStatus) {
            case HalCallback.STATUS_OK:
                return CarStatsLog.CAR_USER_HAL_MODIFY_USER_RESPONSE_REPORTED__CALLBACK_STATUS__OK;
            case HalCallback.STATUS_HAL_SET_TIMEOUT:
                return CarStatsLog
                      .CAR_USER_HAL_MODIFY_USER_RESPONSE_REPORTED__CALLBACK_STATUS__HAL_SET_TIMEOUT;
            case HalCallback.STATUS_HAL_RESPONSE_TIMEOUT:
                return CarStatsLog
                 .CAR_USER_HAL_MODIFY_USER_RESPONSE_REPORTED__CALLBACK_STATUS__HAL_RESPONSE_TIMEOUT;
            case HalCallback.STATUS_WRONG_HAL_RESPONSE:
                return CarStatsLog
                   .CAR_USER_HAL_MODIFY_USER_RESPONSE_REPORTED__CALLBACK_STATUS__WRONG_HAL_RESPONSE;
            case HalCallback.STATUS_CONCURRENT_OPERATION:
                return CarStatsLog
                 .CAR_USER_HAL_MODIFY_USER_RESPONSE_REPORTED__CALLBACK_STATUS__CONCURRENT_OPERATION;
            default:
                return CarStatsLog
                        .CAR_USER_HAL_MODIFY_USER_RESPONSE_REPORTED__CALLBACK_STATUS__INVALID;
        }
        // CHECKSTYLE:ON IndentationCheck
    }

    private <T> HalCallback<T> handleGetPendingCallback(int requestId, Class<T> clazz) {
        PendingRequest<?, ?> pendingRequest = getPendingRequest(requestId);
        if (pendingRequest == null) return null;

        if (pendingRequest.responseClass != clazz) {
            Slogf.e(TAG, "Invalid callback class for request " + requestId + ": expected" + clazz
                    + ", but got is " + pendingRequest.responseClass);
            // TODO(b/150413515): add unit test for this scenario once it supports other properties
            return null;
        }
        @SuppressWarnings("unchecked")
        HalCallback<T> callback = (HalCallback<T>) pendingRequest.callback;
        return callback;
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dump(PrintWriter writer) {
        String indent = "  ";
        writer.printf("*User HAL*\n");

        writer.printf("DBG: %b\n", DBG);
        writer.printf("Relevant CarSystemProperties\n");
        dumpSystemProperty(writer, indent, "user_hal_enabled",
                CarSystemProperties.getUserHalEnabled());
        dumpSystemProperty(writer, indent, "user_hal_timeout",
                CarSystemProperties.getUserHalTimeout());

        synchronized (mLock) {
            if (!isSupported()) {
                writer.println(UNSUPPORTED_MSG);
                return;
            }
            int numberProperties = mProperties.size();
            writer.printf("%d supported properties\n", numberProperties);
            for (int i = 0; i < numberProperties; i++) {
                writer.printf("%s%s\n", indent, mProperties.valueAt(i));
            }
            writer.printf("Base request id: %d\n", mBaseRequestId);
            writer.printf("next request id: %d\n", mNextRequestId);

            int numberPendingCallbacks = mPendingRequests.size();
            if (numberPendingCallbacks == 0) {
                writer.println("no pending callbacks");
            } else {
                writer.printf("%d pending callbacks:\n", numberPendingCallbacks);
                for (int i = 0; i < numberPendingCallbacks; i++) {
                    writer.print(indent);
                    mPendingRequests.valueAt(i).dump(writer);
                    writer.println();
                }
            }
        }
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    private static void dumpSystemProperty(PrintWriter writer, String indent, String name,
            Optional<?> prop) {
        String value = prop.isPresent() ? prop.get().toString() : "<NOT SET>";
        writer.printf("%s%s=%s\n", indent, name, value);
    }

    private static final class PendingRequest<REQ, RESP> {
        public final Class<RESP> responseClass;

        @Nullable
        public final REQ request;

        public final HalCallback<RESP> callback;

        PendingRequest(Class<RESP> responseClass, @Nullable REQ request,
                HalCallback<RESP> callback) {
            this.responseClass = responseClass;
            this.request = request;
            this.callback = callback;
        }

        /**
         * Gets the safely cast request for a given pending request.
         */
        @Nullable
        private static <T> T getRequest(@Nullable PendingRequest<?, ?> pendingRequest,
                Class<T> clazz, int requestId) {
            if (pendingRequest == null) {
                Slogf.e(TAG, "No pending request for id " + requestId);
                return null;

            }
            Object request = pendingRequest.request;
            if (!clazz.isInstance(request)) {
                Slogf.e(TAG, "Wrong pending request for id " + requestId + ": " + pendingRequest);
                return null;
            }
            return clazz.cast(request);
        }

        public void dump(PrintWriter pw) {
            pw.printf("Class: %s Callback: %s", responseClass.getSimpleName(),
                    FunctionalUtils.getLambdaName(callback));
            if (request != null) {
                pw.printf(" Request: %s", request);
            }
        }

        @Override
        public String toString() {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            pw.print("[PendingRequest: ");
            dump(pw);
            pw.print("]");
            pw.flush();
            return sw.toString();
        }
    }
}
