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

import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_SWITCHING;

import static com.android.car.CarServiceUtils.isEventOfType;
import static com.android.car.PermissionHelper.checkHasAtLeastOnePermissionGranted;
import static com.android.car.PermissionHelper.checkHasDumpPermissionGranted;
import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;
import static com.android.car.user.CarUserService.checkManageUsersPermission;
import static com.android.car.user.CarUserService.sendUserSwitchResult;

import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.app.ActivityManager;
import android.car.CarOccupantZoneManager;
import android.car.CarOccupantZoneManager.OccupantTypeEnum;
import android.car.CarOccupantZoneManager.OccupantZoneInfo;
import android.car.IExperimentalCarUserService;
import android.car.SyncResultCallback;
import android.car.builtin.app.ActivityManagerHelper;
import android.car.builtin.os.TraceHelper;
import android.car.builtin.os.UserManagerHelper;
import android.car.builtin.util.Slogf;
import android.car.builtin.util.TimingsTraceLog;
import android.car.user.CarUserManager.UserLifecycleListener;
import android.car.user.UserCreationResult;
import android.car.user.UserLifecycleEventFilter;
import android.car.user.UserSwitchResult;
import android.car.util.concurrent.AndroidFuture;
import android.content.Context;
import android.content.res.Resources;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.proto.ProtoOutputStream;

import com.android.car.CarLog;
import com.android.car.CarServiceBase;
import com.android.car.R;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.ResultCallbackImpl;
import com.android.car.internal.common.UserHelperLite;
import com.android.car.internal.os.CarSystemProperties;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Experimental User Service for Car. Including:
 *  <ul>
 *    <li> Creates a user used as driver.
 *    <li> Creates a user used as passenger.
 *    <li> Switch drivers.
 *  <ul/>
 */
public final class ExperimentalCarUserService extends IExperimentalCarUserService.Stub
        implements CarServiceBase {

    @VisibleForTesting
    static final String TAG = CarLog.tagFor(ExperimentalCarUserService.class);

    private final int mHalTimeoutMs = CarSystemProperties.getUserHalTimeout().orElse(5_000);

    private final CopyOnWriteArrayList<PassengerCallback> mPassengerCallbacks =
            new CopyOnWriteArrayList<>();

    private final Context mContext;
    private final CarUserService mCarUserService;
    private final UserManager mUserManager;
    private final boolean mEnablePassengerSupport;
    private final UserHandleHelper mUserHandleHelper;

    private final Object mLock = new Object();
    // Only one passenger is supported.
    @GuardedBy("mLock")
    private @UserIdInt int mLastPassengerId = UserManagerHelper.USER_NULL;

    @GuardedBy("mLock")
    private ZoneUserBindingHelper mZoneUserBindingHelper;

    private final UserLifecycleListener mUserLifecycleListener = event -> {
        if (!isEventOfType(TAG, event, USER_LIFECYCLE_EVENT_TYPE_SWITCHING)) {
            return;
        }
        Slogf.d(TAG, "ExperimentalCarUserService.onEvent: %s", event);

        onUserSwitching(event.getPreviousUserId(), event.getUserId());
    };

    /** Interface for callbacks related to passenger activities. */
    public interface PassengerCallback {
        /** Called when passenger is started at a certain zone. */
        void onPassengerStarted(@UserIdInt int passengerId, int zoneId);
        /** Called when passenger is stopped. */
        void onPassengerStopped(@UserIdInt int passengerId);
    }

    /** Interface for delegating zone-related implementation to CarOccupantZoneService. */
    public interface ZoneUserBindingHelper {
        /** Gets occupant zones corresponding to the occupant type. */
        List<OccupantZoneInfo> getOccupantZones(@OccupantTypeEnum int occupantType);
        /** Assigns the user to the occupant zone. */
        boolean assignUserToOccupantZone(@UserIdInt int userId, int zoneId);
        /** Makes the occupant zone unoccupied. */
        boolean unassignUserFromOccupantZone(@UserIdInt int userId);
        /** Returns whether there is a passenger display. */
        boolean isPassengerDisplayAvailable();
    }

    public ExperimentalCarUserService(Context context, CarUserService carUserService,
            UserManager userManager) {
        this(context, carUserService, userManager, new UserHandleHelper(context, userManager));
    }

    @VisibleForTesting
    public ExperimentalCarUserService(Context context, CarUserService carUserService,
            UserManager userManager, UserHandleHelper userHandleHelper) {
        mContext = context;
        mUserManager = userManager;
        mCarUserService = carUserService;
        Resources resources = context.getResources();
        mEnablePassengerSupport = resources.getBoolean(R.bool.enablePassengerSupport);
        mUserHandleHelper = userHandleHelper;
    }

    @Override
    public void init() {
        Slogf.d(TAG, "init()");

        UserLifecycleEventFilter userSwitchingEventFilter = new UserLifecycleEventFilter.Builder()
                .addEventType(USER_LIFECYCLE_EVENT_TYPE_SWITCHING).build();
        mCarUserService.addUserLifecycleListener(userSwitchingEventFilter, mUserLifecycleListener);
    }

    @Override
    public void release() {
        Slogf.d(TAG, "release()");

        mCarUserService.removeUserLifecycleListener(mUserLifecycleListener);
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dump(IndentingPrintWriter writer) {
        checkHasDumpPermissionGranted(mContext, "dump()");

        writer.println("*ExperimentalCarUserService*");

        List<UserHandle> allDrivers = getAllDrivers();
        int driversSize = allDrivers.size();
        writer.println("NumberOfDrivers: " + driversSize);
        writer.increaseIndent();
        for (int i = 0; i < driversSize; i++) {
            int driverId = allDrivers.get(i).getIdentifier();
            writer.printf("#%d: id=%d", i, driverId);
            List<UserHandle> passengers = getPassengers(driverId);
            int passengersSize = passengers.size();
            writer.print(" NumberPassengers: " + passengersSize);
            if (passengersSize > 0) {
                writer.print(" [");
                for (int j = 0; j < passengersSize; j++) {
                    writer.print(passengers.get(j).getIdentifier());
                    if (j < passengersSize - 1) {
                        writer.print(" ");
                    }
                }
                writer.print("]");
            }
            writer.println();
        }
        writer.decreaseIndent();
        writer.printf("EnablePassengerSupport: %s\n", mEnablePassengerSupport);
        synchronized (mLock) {
            writer.printf("LastPassengerId: %d\n", mLastPassengerId);
        }

        writer.printf("User HAL timeout: %dms\n",  mHalTimeoutMs);
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dumpProto(ProtoOutputStream proto) {}

    @Override
    public AndroidFuture<UserCreationResult> createDriver(String name, boolean admin) {
        checkManageUsersPermission("createDriver");
        Objects.requireNonNull(name, "name cannot be null");

        AndroidFuture<UserCreationResult> future = new AndroidFuture<>();

        ResultCallbackImpl<UserCreationResult> resultResultCallbackImpl = new ResultCallbackImpl<>(
                Runnable::run, new SyncResultCallback<>()) {
            @Override
            protected void onCompleted(UserCreationResult result) {
                if (result == null) {
                    Slogf.w(TAG, "createDriver(%s, %s) failed", name, admin);
                }
                future.complete(result);
                super.onCompleted(result);
            }
        };
        int flags = 0;

        if (admin) {
            if (!(mUserManager.isAdminUser() || mUserManager.isSystemUser())) {
                String internalErrorMsg =
                        "Only admin users and system user can create other admins.";
                Slogf.e(TAG, internalErrorMsg);
                mCarUserService.sendUserCreationFailure(resultResultCallbackImpl,
                        UserCreationResult.STATUS_INVALID_REQUEST,
                        internalErrorMsg);
                return future;
            }
            flags = UserManagerHelper.FLAG_ADMIN;
        }

        mCarUserService.createUser(name,
                UserManagerHelper.getDefaultUserTypeForUserInfoFlags(flags), flags, mHalTimeoutMs,
                resultResultCallbackImpl, false);
        return future;
    }

    @Override
    @Nullable
    public UserHandle createPassenger(String name, @UserIdInt int driverId) {
        checkManageUsersPermission("createPassenger");
        Objects.requireNonNull(name, "name cannot be null");

        UserHandle driver = mUserHandleHelper.getExistingUserHandle(driverId);
        if (driver == null) {
            Slogf.w(TAG, "the driver is invalid for driverId: %d", driverId);
            return null;
        }
        if (mUserHandleHelper.isGuestUser(driver)) {
            Slogf.w(TAG, "a guest driver with id %d cannot create a passenger", driverId);
            return null;
        }
        // createPassenger doesn't use user HAL because user HAL doesn't support profile user yet.
        UserManager userManager = mContext.createContextAsUser(driver, /* flags= */ 0)
                .getSystemService(UserManager.class);
        UserHandle user = userManager.createProfile(name, UserManager.USER_TYPE_PROFILE_MANAGED,
                /* disallowedPackages= */ null);

        if (user == null) {
            // Couldn't create user, most likely because there are too many.
            Slogf.w(TAG, "can't create a profile for user %d", driverId);
            return null;
        }
        return user;
    }

    @Override
    public void switchDriver(@UserIdInt int driverId, AndroidFuture<UserSwitchResult> receiver) {
        checkManageUsersPermission("switchDriver");
        ResultCallbackImpl<UserSwitchResult> resultCallbackImpl = new ResultCallbackImpl<>(
                Runnable::run, new SyncResultCallback<>()) {
            @Override
            protected void onCompleted(UserSwitchResult result) {
                receiver.complete(result);
                super.onCompleted(result);
            }
        };

        if (UserHelperLite.isHeadlessSystemUser(driverId)) {
            // System user doesn't associate with real person, can not be switched to.
            Slogf.w(TAG, "switching to system user in headless system user mode is not allowed");
            sendUserSwitchResult(resultCallbackImpl, /* isLogout= */ false,
                    UserSwitchResult.STATUS_INVALID_REQUEST);
            return;
        }
        int userSwitchable = mUserManager.getUserSwitchability();
        if (userSwitchable != UserManager.SWITCHABILITY_STATUS_OK) {
            Slogf.w(TAG, "current process is not allowed to switch user");
            sendUserSwitchResult(resultCallbackImpl, /* isLogout= */ false,
                    UserSwitchResult.STATUS_INVALID_REQUEST);
            return;
        }
        mCarUserService.switchUser(driverId, mHalTimeoutMs, resultCallbackImpl,
                /* ignoreUxRestriction= */ false);
    }

    /**
     * Returns all drivers who can occupy the driving zone. Guest users are included in the list.
     *
     * @return the list of {@link UserHandle} who can be a driver on the device.
     */
    @Override
    public List<UserHandle> getAllDrivers() {
        checkManageUsersOrDumpPermission("getAllDrivers");

        return getUsersHandle(
                (user) -> !UserHelperLite.isHeadlessSystemUser(user.getIdentifier())
                        && mUserHandleHelper.isEnabledUser(user)
                        && !mUserHandleHelper.isManagedProfile(user)
                        && !mUserHandleHelper.isEphemeralUser(user));
    }

    /**
     * Returns all passengers under the given driver.
     *
     * @param driverId User id of a driver.
     * @return the list of {@link UserHandle} who is a passenger under the given driver.
     */
    @Override
    public List<UserHandle> getPassengers(@UserIdInt int driverId) {
        checkManageUsersOrDumpPermission("getPassengers");

        return getUsersHandle((user) -> {
            return !UserHelperLite.isHeadlessSystemUser(user.getIdentifier())
                    && mUserHandleHelper.isEnabledUser(user)
                    && mUserHandleHelper.isManagedProfile(user)
                    && mUserManager.isSameProfileGroup(user, UserHandle.of(driverId));
        });
    }

    @Override
    public boolean startPassenger(@UserIdInt int passengerId, int zoneId) {
        checkManageUsersPermission("startPassenger");

        synchronized (mLock) {
            if (!ActivityManagerHelper.startUserInBackground(passengerId)) {
                Slogf.w(TAG, "could not start passenger");
                return false;
            }
            if (!assignUserToOccupantZone(passengerId, zoneId)) {
                Slogf.w(TAG, "could not assign passenger to zone");
                return false;
            }
            mLastPassengerId = passengerId;
        }
        for (PassengerCallback callback : mPassengerCallbacks) {
            callback.onPassengerStarted(passengerId, zoneId);
        }
        return true;
    }

    @Override
    public boolean stopPassenger(@UserIdInt int passengerId) {
        checkManageUsersPermission("stopPassenger");

        return stopPassengerInternal(passengerId, true);
    }

    /** Adds callback to listen to passenger activity events. */
    public void addPassengerCallback(PassengerCallback callback) {
        Objects.requireNonNull(callback, "callback cannot be null");
        mPassengerCallbacks.add(callback);
    }

    /** Removes previously added callback to listen passenger events. */
    public void removePassengerCallback(PassengerCallback callback) {
        Objects.requireNonNull(callback, "callback cannot be null");
        mPassengerCallbacks.remove(callback);
    }

    /** Sets the implementation of ZoneUserBindingHelper. */
    public void setZoneUserBindingHelper(ZoneUserBindingHelper helper) {
        synchronized (mLock) {
            mZoneUserBindingHelper = helper;
        }
    }

    private boolean stopPassengerInternal(@UserIdInt int passengerId, boolean checkCurrentDriver) {
        synchronized (mLock) {
            // NULL passengerId means the last passenger.
            // This is to avoid accessing mPassengerId without obtaining mLock.
            if (passengerId == UserManagerHelper.USER_NULL) {
                passengerId = mLastPassengerId;
            }
            UserHandle passenger = mUserHandleHelper.getExistingUserHandle(passengerId);
            if (passenger == null) {
                Slogf.w(TAG, "passenger %d doesn't exist", passengerId);
                return false;
            }
            if (mLastPassengerId != passengerId) {
                Slogf.w(TAG, "passenger %d hasn't been started", passengerId);
                return true;
            }
            if (checkCurrentDriver) {
                int currentUserId = ActivityManager.getCurrentUser();
                if (!mUserManager.isSameProfileGroup(passenger, UserHandle.of(currentUserId))) {
                    Slogf.w(TAG, "passenger %d is not a profile of the current user %d",
                            passengerId, currentUserId);
                    return false;
                }
            }
            // Passenger is a profile, so cannot be stopped through activity manager.
            // Instead, activities started by the passenger are stopped and the passenger is
            // unassigned from the zone.
            ActivityManagerHelper.stopAllTasksForUser(passengerId);
            if (!unassignUserFromOccupantZone(passengerId)) {
                Slogf.w(TAG, "could not unassign user %d from occupant zone", passengerId);
                return false;
            }
            mLastPassengerId = UserManagerHelper.USER_NULL;
        }
        for (PassengerCallback callback : mPassengerCallbacks) {
            callback.onPassengerStopped(passengerId);
        }
        return true;
    }

    private void onUserSwitching(@UserIdInt int fromUserId, @UserIdInt int toUserId) {
        Slogf.d(TAG, "onUserSwitching() callback from user %d to user %d", fromUserId, toUserId);
        TimingsTraceLog t = new TimingsTraceLog(TAG, TraceHelper.TRACE_TAG_CAR_SERVICE);
        t.traceBegin("onUserSwitching-" + toUserId);

        stopPassengerInternal(/* passengerId= */ UserManagerHelper.USER_NULL, false);

        if (mEnablePassengerSupport && isPassengerDisplayAvailable()) {
            setupPassengerUser();
            startFirstPassenger(toUserId);
        }
        t.traceEnd();
    }

    interface UserFilter {
        boolean isEligibleUser(UserHandle user);
    }

    /** Returns all users who are matched by the given filter. */
    private List<UserHandle> getUsersHandle(UserFilter filter) {
        List<UserHandle> users = UserManagerHelper.getUserHandles(mUserManager,
                /* excludeDying= */ false);
        List<UserHandle> usersFiltered = new ArrayList<UserHandle>();

        for (Iterator<UserHandle> iterator = users.iterator(); iterator.hasNext(); ) {
            UserHandle user = iterator.next();
            if (filter.isEligibleUser(user)) {
                usersFiltered.add(user);
            }
        }

        return usersFiltered;
    }

    private void checkManageUsersOrDumpPermission(String message) {
        checkHasAtLeastOnePermissionGranted(mContext, message,
                android.Manifest.permission.MANAGE_USERS,
                android.Manifest.permission.DUMP);
    }

    private int getNumberOfManagedProfiles(@UserIdInt int userId) {
        List<UserHandle> users = UserManagerHelper.getUserHandles(mUserManager,
                /* excludeDying= */ false);
        // Count all users that are managed profiles of the given user.
        int managedProfilesCount = 0;
        for (UserHandle user : users) {
            if (mUserHandleHelper.isManagedProfile(user)
                    && mUserManager.isSameProfileGroup(user, UserHandle.of(userId))) {
                managedProfilesCount++;
            }
        }
        return managedProfilesCount;
    }

    /**
     * Starts the first passenger of the given driver and assigns the passenger to the front
     * passenger zone.
     *
     * @param driverId User id of the driver.
     * @return whether it succeeds.
     */
    private boolean startFirstPassenger(@UserIdInt int driverId) {
        int zoneId = getAvailablePassengerZone();
        if (zoneId == CarOccupantZoneManager.OccupantZoneInfo.INVALID_ZONE_ID) {
            Slogf.w(TAG, "passenger occupant zone is not found");
            return false;
        }
        List<UserHandle> passengers = getPassengers(driverId);
        if (passengers.size() < 1) {
            Slogf.w(TAG, "passenger is not found for driver %d", driverId);
            return false;
        }
        // Only one passenger is supported. If there are two or more passengers, the first passenger
        // is chosen.
        int passengerId = passengers.get(0).getIdentifier();
        if (!startPassenger(passengerId, zoneId)) {
            Slogf.w(TAG, "cannot start passenger %d", passengerId);
            return false;
        }
        return true;
    }

    private int getAvailablePassengerZone() {
        int[] occupantTypes = new int[] {CarOccupantZoneManager.OCCUPANT_TYPE_FRONT_PASSENGER,
                CarOccupantZoneManager.OCCUPANT_TYPE_REAR_PASSENGER};
        for (int occupantType : occupantTypes) {
            int zoneId = getZoneId(occupantType);
            if (zoneId != CarOccupantZoneManager.OccupantZoneInfo.INVALID_ZONE_ID) {
                return zoneId;
            }
        }
        return CarOccupantZoneManager.OccupantZoneInfo.INVALID_ZONE_ID;
    }

    /**
     * Creates a new passenger user when there is no passenger user.
     */
    private void setupPassengerUser() {
        int currentUser = ActivityManager.getCurrentUser();
        int profileCount = getNumberOfManagedProfiles(currentUser);
        if (profileCount > 0) {
            Slogf.w(TAG, "max profile of user %d is exceeded: current profile count is %d",
                    currentUser, profileCount);
            return;
        }
        // TODO(b/140311342): Use resource string for the default passenger name.
        UserHandle passenger = createPassenger("Passenger", currentUser);
        if (passenger == null) {
            // Couldn't create user, most likely because there are too many.
            Slogf.w(TAG, "cannot create a passenger user");
            return;
        }
    }

    private List<OccupantZoneInfo> getOccupantZones(@OccupantTypeEnum int occupantType) {
        ZoneUserBindingHelper helper = null;
        synchronized (mLock) {
            if (mZoneUserBindingHelper == null) {
                Slogf.w(TAG, "implementation is not delegated");
                return new ArrayList<OccupantZoneInfo>();
            }
            helper = mZoneUserBindingHelper;
        }
        return helper.getOccupantZones(occupantType);
    }

    private boolean assignUserToOccupantZone(@UserIdInt int userId, int zoneId) {
        ZoneUserBindingHelper helper = null;
        synchronized (mLock) {
            if (mZoneUserBindingHelper == null) {
                Slogf.w(TAG, "implementation is not delegated");
                return false;
            }
            helper = mZoneUserBindingHelper;
        }
        return helper.assignUserToOccupantZone(userId, zoneId);
    }

    private boolean unassignUserFromOccupantZone(@UserIdInt int userId) {
        ZoneUserBindingHelper helper = null;
        synchronized (mLock) {
            if (mZoneUserBindingHelper == null) {
                Slogf.w(TAG, "implementation is not delegated");
                return false;
            }
            helper = mZoneUserBindingHelper;
        }
        return helper.unassignUserFromOccupantZone(userId);
    }

    private boolean isPassengerDisplayAvailable() {
        ZoneUserBindingHelper helper = null;
        synchronized (mLock) {
            if (mZoneUserBindingHelper == null) {
                Slogf.w(TAG, "implementation is not delegated");
                return false;
            }
            helper = mZoneUserBindingHelper;
        }
        return helper.isPassengerDisplayAvailable();
    }

    /**
     * Gets the zone id of the given occupant type.
     *
     * @param occupantType The type of an occupant.
     * @return The zone id of the given occupant type.
     *         the first found zone, if there are two or more zones.
     *         {@link CarOccupantZoneManager.OccupantZoneInfo.INVALID_ZONE_ID}, if not found.
     */
    private int getZoneId(@CarOccupantZoneManager.OccupantTypeEnum int occupantType) {
        List<CarOccupantZoneManager.OccupantZoneInfo> zoneInfos = getOccupantZones(occupantType);
        return (zoneInfos.size() > 0)
                ? zoneInfos.get(0).zoneId
                : CarOccupantZoneManager.OccupantZoneInfo.INVALID_ZONE_ID;
    }
}
