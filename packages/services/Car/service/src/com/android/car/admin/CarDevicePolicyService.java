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

package com.android.car.admin;

import static com.android.car.PermissionHelper.checkHasDumpPermissionGranted;
import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.app.ActivityManager;
import android.app.admin.DevicePolicyManager;
import android.car.admin.CarDevicePolicyManager;
import android.car.admin.ICarDevicePolicyService;
import android.car.builtin.os.UserManagerHelper;
import android.car.builtin.util.Slogf;
import android.car.user.UserCreationRequest;
import android.car.user.UserCreationResult;
import android.car.user.UserRemovalResult;
import android.car.user.UserStartResult;
import android.car.user.UserStopResult;
import android.car.util.concurrent.AndroidFuture;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.SparseIntArray;
import android.util.proto.ProtoOutputStream;

import com.android.car.BuiltinPackageDependency;
import com.android.car.CarLog;
import com.android.car.CarServiceBase;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.ResultCallbackImpl;
import com.android.car.internal.common.UserHelperLite;
import com.android.car.internal.os.CarSystemProperties;
import com.android.car.internal.util.DebugUtils;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.car.user.CarUserService;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Service for device policy related features.
 */
public final class CarDevicePolicyService extends ICarDevicePolicyService.Stub
        implements CarServiceBase {

    @VisibleForTesting
    static final String TAG = CarLog.tagFor(CarDevicePolicyService.class);

    private static final int HAL_TIMEOUT_MS = CarSystemProperties.getUserHalTimeout().orElse(5_000);
    private static final String PREFIX_NEW_USER_DISCLAIMER_STATUS = "NEW_USER_DISCLAIMER_STATUS_";

    // TODO(b/175057848) must be public because of DebugUtils.constantToString()
    public static final int NEW_USER_DISCLAIMER_STATUS_NEVER_RECEIVED = 0;
    public static final int NEW_USER_DISCLAIMER_STATUS_RECEIVED = 1;
    public static final int NEW_USER_DISCLAIMER_STATUS_NOTIFICATION_SENT = 2;
    public static final int NEW_USER_DISCLAIMER_STATUS_SHOWN = 3;
    public static final int NEW_USER_DISCLAIMER_STATUS_ACKED = 4;

    private final Object mLock = new Object();
    private final CarUserService mCarUserService;
    private final Context mContext;
    private final Context mCarServiceBuiltinPackageContext;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(flag = false, prefix = { PREFIX_NEW_USER_DISCLAIMER_STATUS }, value = {
            NEW_USER_DISCLAIMER_STATUS_NEVER_RECEIVED,
            NEW_USER_DISCLAIMER_STATUS_NOTIFICATION_SENT,
            NEW_USER_DISCLAIMER_STATUS_RECEIVED,
            NEW_USER_DISCLAIMER_STATUS_SHOWN,
            NEW_USER_DISCLAIMER_STATUS_ACKED
    })
    public @interface NewUserDisclaimerStatus {}

    @GuardedBy("mLock")
    private final SparseIntArray mUserDisclaimerStatusPerUser = new SparseIntArray();

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int userId = ActivityManager.getCurrentUser();
            Slogf.d(TAG, "Received intent for user " + userId + ": " + intent);
            if (!mContext.getPackageManager()
                    .hasSystemFeature(PackageManager.FEATURE_DEVICE_ADMIN)) {
                Slogf.d(TAG, "Not handling ACTION_SHOW_NEW_USER_DISCLAIMER because device "
                        + "doesn't have %s", PackageManager.FEATURE_DEVICE_ADMIN);
                return;
            }
            switch(intent.getAction()) {
                case DevicePolicyManager.ACTION_SHOW_NEW_USER_DISCLAIMER:
                    Slogf.d(TAG, "Action show new user disclaimer");
                    setUserDisclaimerStatus(userId, NEW_USER_DISCLAIMER_STATUS_RECEIVED);
                    showNewUserDisclaimer(userId);
                    break;
                default:
                    Slogf.w(TAG, "received unexpected intent: %s" , intent);
            }
        }
    };

    public CarDevicePolicyService(@NonNull Context context,
            @NonNull Context carServiceBuiltinPackageContext,
            @NonNull CarUserService carUserService) {
        mCarUserService = carUserService;
        mContext = context;
        mCarServiceBuiltinPackageContext = carServiceBuiltinPackageContext;
    }

    @Override
    public void init() {
        Slogf.d(TAG, "init()");
        mContext.registerReceiverForAllUsers(mBroadcastReceiver,
                new IntentFilter(DevicePolicyManager.ACTION_SHOW_NEW_USER_DISCLAIMER),
                /* broadcastPermissions= */ null, /* scheduler= */ null,
                Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    public void release() {
        Slogf.d(TAG, "release()");
        mContext.unregisterReceiver(mBroadcastReceiver);
    }

    @Override
    public void removeUser(@UserIdInt int userId, ResultCallbackImpl<UserRemovalResult> callback) {
        mCarUserService.removeUser(userId, /* hasCallerRestrictions= */ true, callback);
    }

    @Override
    public void createUser(@Nullable String name, @CarDevicePolicyManager.UserType int type,
            ResultCallbackImpl<UserCreationResult> callback) {
        UserCreationRequest.Builder userCreationRequestBuilder =
                new UserCreationRequest.Builder().setName(name);
        int userInfoFlags = 0;
        String userType = UserManager.USER_TYPE_FULL_SECONDARY;
        switch(type) {
            case CarDevicePolicyManager.USER_TYPE_REGULAR:
                break;
            case CarDevicePolicyManager.USER_TYPE_ADMIN:
                userInfoFlags = UserManagerHelper.FLAG_ADMIN;
                userCreationRequestBuilder.setAdmin();
                break;
            case CarDevicePolicyManager.USER_TYPE_GUEST:
                userType = UserManager.USER_TYPE_FULL_GUEST;
                userCreationRequestBuilder.setGuest();
                break;
            default:
                Slogf.d(TAG, "createUser(): invalid userType (%s) / flags (%08x) "
                        + "combination", userType, userInfoFlags);
                callback.complete(
                        new UserCreationResult(UserCreationResult.STATUS_INVALID_REQUEST));
                return;
        }

        Slogf.d(TAG, "calling createUser(%s, %s, %d, %d)",
                UserHelperLite.safeName(name), userType, userInfoFlags, HAL_TIMEOUT_MS);

        mCarUserService.createUser(userCreationRequestBuilder.build(), HAL_TIMEOUT_MS,
                callback);
    }

    @Override
    public void startUserInBackground(@UserIdInt int userId,
            AndroidFuture<UserStartResult> receiver) {
        mCarUserService.startUserInBackground(userId, receiver);
    }

    @Override
    public void stopUser(@UserIdInt int userId, AndroidFuture<UserStopResult> receiver) {
        mCarUserService.stopUser(userId, receiver);
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dump(@NonNull IndentingPrintWriter writer) {
        checkHasDumpPermissionGranted(mContext, "dump()");

        writer.println("*CarDevicePolicyService*");

        synchronized (mLock) {
            int numUsers = mUserDisclaimerStatusPerUser.size();
            writer.println("**mDisclaimerStatusPerUser**");
            for (int i = 0; i < numUsers; i++) {
                int userId = mUserDisclaimerStatusPerUser.keyAt(i);
                int status = mUserDisclaimerStatusPerUser.get(userId);
                writer.printf("userId=%d disclaimerStatus=%s\n", userId,
                        newUserDisclaimerStatusToString(status));
            }
        }

        writer.printf("HAL_TIMEOUT_MS: %d\n", HAL_TIMEOUT_MS);
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dumpProto(ProtoOutputStream proto) {}

    /**
     * Updates the internal state with the disclaimer status as shown.
     */
    @Override
    public void setUserDisclaimerShown(int userId) {
        setUserDisclaimerStatus(userId, NEW_USER_DISCLAIMER_STATUS_SHOWN);
    }

    /**
     * Updates the internal state with the disclaimer status as acknowledged.
     */
    @Override
    public void setUserDisclaimerAcknowledged(int userId) {
        setUserDisclaimerStatus(userId, NEW_USER_DISCLAIMER_STATUS_ACKED);
        UserHandle user = UserHandle.of(userId);
        BuiltinPackageDependency.createNotificationHelper(mCarServiceBuiltinPackageContext)
                .cancelUserDisclaimerNotification(user);

        DevicePolicyManager dpm = mContext.createContextAsUser(user, 0)
                .getSystemService(DevicePolicyManager.class);
        dpm.acknowledgeNewUserDisclaimer();
    }

    @VisibleForTesting
    @NewUserDisclaimerStatus
    int getNewUserDisclaimerStatus(int userId) {
        synchronized (mLock) {
            return mUserDisclaimerStatusPerUser.get(userId,
                    NEW_USER_DISCLAIMER_STATUS_NEVER_RECEIVED);
        }
    }

    private void showNewUserDisclaimer(@UserIdInt int userId) {
        // TODO(b/175057848) persist status so it's shown again if car service crashes?

        BuiltinPackageDependency.createNotificationHelper(mCarServiceBuiltinPackageContext)
                .showUserDisclaimerNotification(UserHandle.of(userId));

        setUserDisclaimerStatus(userId, NEW_USER_DISCLAIMER_STATUS_NOTIFICATION_SENT);
    }

    private void setUserDisclaimerStatus(@UserIdInt int userId,
            @NewUserDisclaimerStatus int status) {
        synchronized (mLock) {
            Slogf.d(TAG, "Changing status from %s to %s",
                    newUserDisclaimerStatusToString(
                            mUserDisclaimerStatusPerUser.get(
                                    userId, NEW_USER_DISCLAIMER_STATUS_NEVER_RECEIVED)),
                    newUserDisclaimerStatusToString(status));
            mUserDisclaimerStatusPerUser.put(userId, status);
        }
    }

    @VisibleForTesting
    static String newUserDisclaimerStatusToString(@NewUserDisclaimerStatus int status) {
        return DebugUtils.constantToString(CarDevicePolicyService.class,
                PREFIX_NEW_USER_DISCLAIMER_STATUS, status);
    }
}
