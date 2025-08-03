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
package com.android.car.settings.enterprise;

import android.Manifest;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.app.admin.DeviceAdminInfo;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;

import com.android.car.settings.common.Logger;
import com.android.settingslib.RestrictedLockUtils.EnforcedAdmin;
import com.android.settingslib.RestrictedLockUtilsInternal;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.List;

/**
 * Generic helper methods for this package.
 */
public final class EnterpriseUtils {

    private static final Logger LOG = new Logger(EnterpriseUtils.class);
    // TODO: ideally, we should not create a special user restriction other than what are
    // defined in UserManager.
    public static final String DISABLED_INPUT_METHOD = "disabled-input-method";
    // TODO: same as for DISABLED_INPUT_METHOD
    public static final String BLOCKED_UNINSTALL_APP = "blocked-uninstall-app";

    static final String[] CAMERA_PERMISSIONS = new String[] {
            Manifest.permission.CAMERA
    };
    static final String[] LOCATION_PERMISSIONS = new String[] {
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
    };
    static final String[] MICROPHONE_PERMISSIONS = new String[] {
            Manifest.permission.RECORD_AUDIO
    };

    /**
     * Gets the active admin for the given package.
     */
    @Nullable
    public static ComponentName getAdminWithinPackage(Context context, String packageName) {
        List<ComponentName> admins = context.getSystemService(DevicePolicyManager.class)
                .getActiveAdmins();
        if (admins == null) {
            LOG.v("getAdminWithinPackage(): no active admins on context");
            return null;
        }
        return admins.stream().filter(i -> i.getPackageName().equals(packageName)).findAny()
                .orElse(null);
    }

    /**
     * Gets the active admin info for the given admin .
     */
    @Nullable
    public static DeviceAdminInfo getDeviceAdminInfo(Context context, ComponentName admin) {
        LOG.d("getDeviceAdminInfo()(): " + admin.flattenToShortString());

        ActivityInfo ai;
        try {
            ai = context.getPackageManager().getReceiverInfo(admin, PackageManager.GET_META_DATA);
        } catch (PackageManager.NameNotFoundException e) {
            LOG.v("Unable to get activity info for " + admin.flattenToShortString() + ": " + e);
            return null;
        }

        try {
            return new DeviceAdminInfo(context, ai);
        } catch (XmlPullParserException | IOException e) {
            LOG.v("Unable to retrieve device policy for " + admin.flattenToShortString() + ": ",
                    e);
            return null;
        }
    }

    /**
     * Checks whether current user has the flag {@link UserManager.FLAG_DEMO}.
     */
    public static boolean isDemoUser(Context context) {
        return UserManager.isDeviceInDemoMode(context)
                && getUserManager(context).isDemoUser();
    }

    /**
     * Checks whether current user has the flag {@link UserManager.FLAG_ADMIN}.
     */
    public static boolean isAdminUser(Context context) {
        return getUserManager(context).isAdminUser();
    }

    /**
     * Checks whether the restriction is set on the current user by device owner / profile owners
     * but not by {@link UserManager}.
     *
     * <p>This includes restriction set on device owner but current user has affiliated profile
     * owner.
     */
    public static boolean hasUserRestrictionByDpm(Context context, String restriction) {
        if (hasUserRestrictionByUm(context, restriction)) {
            return false;
        }
        return getUserManager(context).hasUserRestriction(restriction);
    }

    /**
     * Checks whether there are restrictions set via {@link UserManager} which doesn't include
     * restrictions set by device owner / profile owners.
     */
    public static boolean hasUserRestrictionByUm(Context context, String restriction) {
        return getUserManager(context)
                .hasBaseUserRestriction(restriction, UserHandle.of(context.getUserId()));
    }

    /**
     * Checks whether the device is managed.
     */
    public static boolean isDeviceManaged(Context context) {
        DevicePolicyManager dpm = getDevicePolicyManager(context);
        return dpm.isDeviceManaged();
    }

    /**
     * Checks whether device owner is set on the device.
     */
    public static boolean hasDeviceOwner(Context context) {
        DevicePolicyManager dpm = getDevicePolicyManager(context);
        return dpm.isDeviceManaged() && getDeviceOwner(context) != null;
    }

    /**
     * Gets device owner user id on the device.
     */
    @UserIdInt
    private static int getDeviceOwnerUserId(Context context) {
        return getDevicePolicyManager(context).getDeviceOwnerUserId();
    }

    /**
     * Gets device owner component on the device.
     */
    @Nullable
    private static ComponentName getDeviceOwner(Context context) {
        return getDevicePolicyManager(context).getDeviceOwnerComponentOnAnyUser();
    }

    private static UserManager getUserManager(Context context) {
        return context.getSystemService(UserManager.class);
    }

    private static DevicePolicyManager getDevicePolicyManager(Context context) {
        return context.getSystemService(DevicePolicyManager.class);
    }

    /**
     * Gets an {@code ActionDisabledByAdminDialogFragment} for the target restriction to show on
     * the current user.
     */
    public static ActionDisabledByAdminDialogFragment getActionDisabledByAdminDialog(
            Context context, String restriction) {
        return getActionDisabledByAdminDialog(context, restriction, /* restrictedPackage= */ null);
    }

    /**
     * Gets an {@code ActionDisabledByAdminDialogFragment} when the input method is restricted for
     * the current user.
     */
    public static ActionDisabledByAdminDialogFragment getInputMethodDisabledByAdminDialog(
            Context context, String restriction) {
        return getActionDisabledByAdminDialog(context, restriction, /* restrictedPackage= */ null);
    }

    /**
     * Gets an {@code ActionDisabledByAdminDialogFragment} for the target restriction to show on
     * the current user with additional restricted package information.
     */
    public static ActionDisabledByAdminDialogFragment getActionDisabledByAdminDialog(
            Context context, String restriction, @Nullable String restrictedPackage) {
        int adminUser = hasDeviceOwner(context)
                ? getDeviceOwnerUserId(context)
                : context.getUserId();
        return ActionDisabledByAdminDialogFragment
                .newInstance(restriction, restrictedPackage, adminUser);
    }

    /**
     * Gets enforced admin information from Intent that started the
     * {@code ActionDisabledByAdminDialogActivity}.
     */
    public static EnforcedAdmin getEnforcedAdminFromIntent(Context context, Intent intent) {
        EnforcedAdmin admin = new EnforcedAdmin(null, context.getUser());
        if (intent == null) {
            return admin;
        }
        admin.component = intent.getParcelableExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN);
        int userId = intent.getIntExtra(Intent.EXTRA_USER_ID, context.getUserId());

        Bundle adminDetails = null;
        if (admin.component == null) {
            DevicePolicyManager devicePolicyManager = getDevicePolicyManager(context);
            admin.component = adminDetails.getParcelable(DevicePolicyManager.EXTRA_DEVICE_ADMIN);
        }

        if (intent.hasExtra(Intent.EXTRA_USER)) {
            admin.user = intent.getParcelableExtra(Intent.EXTRA_USER);
        } else {
            if (adminDetails != null) {
                userId = adminDetails.getInt(Intent.EXTRA_USER_ID, UserHandle.myUserId());
            }
            if (userId == UserHandle.USER_NULL) {
                admin.user = null;
            } else {
                admin.user = UserHandle.of(userId);
            }
        }
        return admin;
    }

    /**
     * Gets {@code RestrictedLockUtils.EnforcedAdmin} for the device policy that affects
     * current user.
     *
     * @param context for current user
     * @param adminUser which can be either profile owner on current user or device owner on
     *        headless system user
     * @param restriction which can be user restriction or restriction policy defined
     *        in this class
     * @param restrictedPackage is the target package that restriction policy is set
     * @return {@code RestrictedLockUtils.EnforcedAdmin}
     */
    public static EnforcedAdmin getEnforcedAdmin(Context context, @UserIdInt int adminUser,
            @Nullable String restriction, String restrictedPackage) {
        if (restriction == null) {
            return null;
        }
        EnforcedAdmin admin = null;
        if (hasUserRestrictionByDpm(context, restriction)) {
            admin = RestrictedLockUtilsInternal.checkIfRestrictionEnforced(
                    context, restriction, context.getUserId());
            LOG.v("getEnforcedAdmin(): " + adminUser + " restriction: " + restriction
                    + " restrictedPackage: " + restrictedPackage);

            if (admin.component == null && context.getUserId() != adminUser) {
                // User restriction might be set on primary user which is user 0 as a device-wide
                // policy.
                admin = RestrictedLockUtilsInternal.checkIfRestrictionEnforced(
                        context, restriction, adminUser);
            }
        } else if (restriction.equals(DISABLED_INPUT_METHOD)) {
            if (restrictedPackage == null) {
                LOG.e("getEnforcedAdmin() for " + DISABLED_INPUT_METHOD
                        + " fails since restrictedPackage is null");
                return admin;
            }
            admin = RestrictedLockUtilsInternal.checkIfInputMethodDisallowed(
                    context, restrictedPackage, context.getUserId());
        } else if (restriction.equals(BLOCKED_UNINSTALL_APP)) {
            admin = RestrictedLockUtilsInternal.checkIfUninstallBlocked(
                    context, restrictedPackage, context.getUserId());
        }
        LOG.v("getEnforcedAdmin():" + admin);
        return admin;
    }

    private EnterpriseUtils() {
        throw new UnsupportedOperationException("Provides only static methods");
    }
}
