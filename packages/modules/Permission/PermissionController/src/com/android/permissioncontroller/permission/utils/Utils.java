/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.permissioncontroller.permission.utils;

import static android.Manifest.permission_group.ACTIVITY_RECOGNITION;
import static android.Manifest.permission_group.CALENDAR;
import static android.Manifest.permission_group.CALL_LOG;
import static android.Manifest.permission_group.CAMERA;
import static android.Manifest.permission_group.CONTACTS;
import static android.Manifest.permission_group.LOCATION;
import static android.Manifest.permission_group.MICROPHONE;
import static android.Manifest.permission_group.NEARBY_DEVICES;
import static android.Manifest.permission_group.NOTIFICATIONS;
import static android.Manifest.permission_group.PHONE;
import static android.Manifest.permission_group.READ_MEDIA_AURAL;
import static android.Manifest.permission_group.READ_MEDIA_VISUAL;
import static android.Manifest.permission_group.SENSORS;
import static android.Manifest.permission_group.SMS;
import static android.Manifest.permission_group.STORAGE;
import static android.app.AppOpsManager.MODE_ALLOWED;
import static android.app.AppOpsManager.OPSTR_LEGACY_STORAGE;
import static android.content.Context.MODE_PRIVATE;
import static android.content.Intent.EXTRA_PACKAGE_NAME;
import static android.content.pm.PackageManager.FLAG_PERMISSION_RESTRICTION_INSTALLER_EXEMPT;
import static android.content.pm.PackageManager.FLAG_PERMISSION_RESTRICTION_SYSTEM_EXEMPT;
import static android.content.pm.PackageManager.FLAG_PERMISSION_RESTRICTION_UPGRADE_EXEMPT;
import static android.content.pm.PackageManager.FLAG_PERMISSION_USER_SENSITIVE_WHEN_DENIED;
import static android.content.pm.PackageManager.FLAG_PERMISSION_USER_SENSITIVE_WHEN_GRANTED;
import static android.content.pm.PackageManager.MATCH_SYSTEM_ONLY;
import static android.health.connect.HealthConnectManager.ACTION_MANAGE_HEALTH_PERMISSIONS;
import static android.health.connect.HealthPermissions.HEALTH_PERMISSION_GROUP;
import static android.os.UserHandle.myUserId;

import static com.android.permissioncontroller.Constants.EXTRA_SESSION_ID;
import static com.android.permissioncontroller.Constants.INVALID_SESSION_ID;

import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.Manifest;
import android.app.AppOpsManager;
import android.app.Application;
import android.app.admin.DevicePolicyManager;
import android.app.role.RoleManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageItemInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PermissionGroupInfo;
import android.content.pm.PermissionInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.UserProperties;
import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.SensorPrivacyManager;
import android.health.connect.HealthConnectManager;
import android.os.Binder;
import android.os.Build;
import android.os.Parcelable;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.DeviceConfig;
import android.provider.Settings;
import android.text.Html;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.ArrayMap;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.ChecksSdkIntAtLeast;
import androidx.annotation.ColorRes;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.StringRes;
import androidx.core.text.BidiFormatter;
import androidx.core.util.Preconditions;

import com.android.launcher3.icons.IconFactory;
import com.android.modules.utils.build.SdkLevel;
import com.android.permissioncontroller.Constants;
import com.android.permissioncontroller.DeviceUtils;
import com.android.permissioncontroller.PermissionControllerApplication;
import com.android.permissioncontroller.R;
import com.android.permissioncontroller.permission.model.AppPermissionGroup;
import com.android.permissioncontroller.permission.model.livedatatypes.LightAppPermGroup;
import com.android.permissioncontroller.permission.model.livedatatypes.LightPackageInfo;

import kotlin.Triple;

import java.lang.annotation.Retention;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

public final class Utils {

    @Retention(SOURCE)
    @IntDef(value = {LAST_24H_SENSOR_TODAY, LAST_24H_SENSOR_YESTERDAY,
            LAST_24H_CONTENT_PROVIDER, NOT_IN_LAST_7D})
    public @interface AppPermsLastAccessType {}
    public static final int LAST_24H_SENSOR_TODAY = 1;
    public static final int LAST_24H_SENSOR_YESTERDAY = 2;
    public static final int LAST_24H_CONTENT_PROVIDER = 3;
    public static final int LAST_7D_SENSOR = 4;
    public static final int LAST_7D_CONTENT_PROVIDER = 5;
    public static final int NOT_IN_LAST_7D = 6;

    private static final String LOG_TAG = "Utils";

    public static final String OS_PKG = "android";

    public static final float DEFAULT_MAX_LABEL_SIZE_PX = 500f;

    /** The time an app needs to be unused in order to be hibernated */
    public static final String PROPERTY_HIBERNATION_UNUSED_THRESHOLD_MILLIS =
            "auto_revoke_unused_threshold_millis2";

    /** The frequency of running the job for hibernating apps */
    public static final String PROPERTY_HIBERNATION_CHECK_FREQUENCY_MILLIS =
            "auto_revoke_check_frequency_millis";

    /** Whether hibernation targets apps that target a pre-S SDK */
    public static final String PROPERTY_HIBERNATION_TARGETS_PRE_S_APPS =
            "app_hibernation_targets_pre_s_apps";

    /** Whether or not app hibernation is enabled on the device **/
    public static final String PROPERTY_APP_HIBERNATION_ENABLED = "app_hibernation_enabled";

    /** Whether the system exempt from hibernation is enabled on the device **/
    public static final String PROPERTY_SYSTEM_EXEMPT_HIBERNATION_ENABLED =
            "system_exempt_hibernation_enabled";

    /** The timeout for one-time permissions */
    private static final String PROPERTY_ONE_TIME_PERMISSIONS_TIMEOUT_MILLIS =
            "one_time_permissions_timeout_millis";

    /** The delay before ending a one-time permission session when all processes are dead */
    private static final String PROPERTY_ONE_TIME_PERMISSIONS_KILLED_DELAY_MILLIS =
            "one_time_permissions_killed_delay_millis";

    /** Whether to show health permission in various permission controller UIs. */
    private static final String PROPERTY_HEALTH_PERMISSION_UI_ENABLED =
            "health_permission_ui_enabled";


    /** How frequently to check permission event store to scrub old data */
    public static final String PROPERTY_PERMISSION_EVENTS_CHECK_OLD_FREQUENCY_MILLIS =
            "permission_events_check_old_frequency_millis";

    /**
     * Whether to store the exact time for permission changes. Only for use in tests and should
     * not be modified in prod.
     */
    public static final String PROPERTY_PERMISSION_CHANGES_STORE_EXACT_TIME =
            "permission_changes_store_exact_time";

    /** The max amount of time permission data can stay in the storage before being scrubbed */
    public static final String PROPERTY_PERMISSION_DECISIONS_MAX_DATA_AGE_MILLIS =
            "permission_decisions_max_data_age_millis";

    /** All permission whitelists. */
    public static final int FLAGS_PERMISSION_WHITELIST_ALL =
            PackageManager.FLAG_PERMISSION_WHITELIST_SYSTEM
                    | PackageManager.FLAG_PERMISSION_WHITELIST_UPGRADE
                    | PackageManager.FLAG_PERMISSION_WHITELIST_INSTALLER;

    /** All permission restriction exemptions. */
    public static final int FLAGS_PERMISSION_RESTRICTION_ANY_EXEMPT =
            FLAG_PERMISSION_RESTRICTION_SYSTEM_EXEMPT
                    | FLAG_PERMISSION_RESTRICTION_UPGRADE_EXEMPT
                    | FLAG_PERMISSION_RESTRICTION_INSTALLER_EXEMPT;

    /**
     * The default length of the timeout for one-time permissions
     */
    public static final long ONE_TIME_PERMISSIONS_TIMEOUT_MILLIS = 1 * 60 * 1000; // 1 minute

    /**
     * The default length to wait before ending a one-time permission session after all processes
     * are dead.
     */
    public static final long ONE_TIME_PERMISSIONS_KILLED_DELAY_MILLIS = 5 * 1000;

    private static final ArrayMap<String, Integer> PERM_GROUP_REQUEST_RES;
    private static final ArrayMap<String, Integer> PERM_GROUP_REQUEST_DEVICE_AWARE_RES;
    private static final ArrayMap<String, Integer> PERM_GROUP_REQUEST_DETAIL_RES;
    private static final ArrayMap<String, Integer> PERM_GROUP_BACKGROUND_REQUEST_RES;
    private static final ArrayMap<String, Integer> PERM_GROUP_BACKGROUND_REQUEST_DEVICE_AWARE_RES;
    private static final ArrayMap<String, Integer> PERM_GROUP_BACKGROUND_REQUEST_DETAIL_RES;
    private static final ArrayMap<String, Integer> PERM_GROUP_UPGRADE_REQUEST_RES;
    private static final ArrayMap<String, Integer> PERM_GROUP_UPGRADE_REQUEST_DEVICE_AWARE_RES;
    private static final ArrayMap<String, Integer> PERM_GROUP_UPGRADE_REQUEST_DETAIL_RES;

    /** Permission -> Sensor codes */
    private static final ArrayMap<String, Integer> PERM_SENSOR_CODES;
    /** Permission -> Icon res id */
    private static final ArrayMap<String, Integer> PERM_BLOCKED_ICON;
    /** Permission -> Title res id */
    private static final ArrayMap<String, Integer> PERM_BLOCKED_TITLE;

    public static final int FLAGS_ALWAYS_USER_SENSITIVE =
            FLAG_PERMISSION_USER_SENSITIVE_WHEN_GRANTED
                    | FLAG_PERMISSION_USER_SENSITIVE_WHEN_DENIED;

    private static final String SYSTEM_AMBIENT_AUDIO_INTELLIGENCE =
            "android.app.role.SYSTEM_AMBIENT_AUDIO_INTELLIGENCE";
    private static final String SYSTEM_UI_INTELLIGENCE =
            "android.app.role.SYSTEM_UI_INTELLIGENCE";
    private static final String SYSTEM_AUDIO_INTELLIGENCE =
            "android.app.role.SYSTEM_AUDIO_INTELLIGENCE";
    private static final String SYSTEM_NOTIFICATION_INTELLIGENCE =
            "android.app.role.SYSTEM_NOTIFICATION_INTELLIGENCE";
    private static final String SYSTEM_TEXT_INTELLIGENCE =
            "android.app.role.SYSTEM_TEXT_INTELLIGENCE";
    private static final String SYSTEM_VISUAL_INTELLIGENCE =
            "android.app.role.SYSTEM_VISUAL_INTELLIGENCE";

    // TODO: theianchen Using hardcoded values here as a WIP solution for now.
    private static final String[] EXEMPTED_ROLES = {
            SYSTEM_AMBIENT_AUDIO_INTELLIGENCE,
            SYSTEM_UI_INTELLIGENCE,
            SYSTEM_AUDIO_INTELLIGENCE,
            SYSTEM_NOTIFICATION_INTELLIGENCE,
            SYSTEM_TEXT_INTELLIGENCE,
            SYSTEM_VISUAL_INTELLIGENCE,
    };

    static {

        PERM_GROUP_REQUEST_RES = new ArrayMap<>();
        PERM_GROUP_REQUEST_RES.put(CONTACTS, R.string.permgrouprequest_contacts);
        PERM_GROUP_REQUEST_RES.put(LOCATION, R.string.permgrouprequest_location);
        PERM_GROUP_REQUEST_RES.put(NEARBY_DEVICES, R.string.permgrouprequest_nearby_devices);
        PERM_GROUP_REQUEST_RES.put(CALENDAR, R.string.permgrouprequest_calendar);
        PERM_GROUP_REQUEST_RES.put(SMS, R.string.permgrouprequest_sms);
        PERM_GROUP_REQUEST_RES.put(STORAGE, R.string.permgrouprequest_storage);
        PERM_GROUP_REQUEST_RES.put(READ_MEDIA_AURAL, R.string.permgrouprequest_read_media_aural);
        PERM_GROUP_REQUEST_RES.put(READ_MEDIA_VISUAL, R.string.permgrouprequest_read_media_visual);
        PERM_GROUP_REQUEST_RES.put(MICROPHONE, R.string.permgrouprequest_microphone);
        PERM_GROUP_REQUEST_RES
                .put(ACTIVITY_RECOGNITION, R.string.permgrouprequest_activityRecognition);
        PERM_GROUP_REQUEST_RES.put(CAMERA, R.string.permgrouprequest_camera);
        PERM_GROUP_REQUEST_RES.put(CALL_LOG, R.string.permgrouprequest_calllog);
        PERM_GROUP_REQUEST_RES.put(PHONE, R.string.permgrouprequest_phone);
        PERM_GROUP_REQUEST_RES.put(SENSORS, R.string.permgrouprequest_sensors);
        PERM_GROUP_REQUEST_RES.put(NOTIFICATIONS, R.string.permgrouprequest_notifications);

        PERM_GROUP_REQUEST_DEVICE_AWARE_RES = new ArrayMap<>();
        PERM_GROUP_REQUEST_DEVICE_AWARE_RES.put(CONTACTS,
                R.string.permgrouprequest_device_aware_contacts);
        PERM_GROUP_REQUEST_DEVICE_AWARE_RES.put(LOCATION,
                R.string.permgrouprequest_device_aware_location);
        PERM_GROUP_REQUEST_DEVICE_AWARE_RES.put(NEARBY_DEVICES,
                R.string.permgrouprequest_device_aware_nearby_devices);
        PERM_GROUP_REQUEST_DEVICE_AWARE_RES.put(CALENDAR,
                R.string.permgrouprequest_device_aware_calendar);
        PERM_GROUP_REQUEST_DEVICE_AWARE_RES.put(SMS, R.string.permgrouprequest_device_aware_sms);
        PERM_GROUP_REQUEST_DEVICE_AWARE_RES.put(STORAGE,
                R.string.permgrouprequest_device_aware_storage);
        PERM_GROUP_REQUEST_DEVICE_AWARE_RES.put(READ_MEDIA_AURAL,
                R.string.permgrouprequest_device_aware_read_media_aural);
        PERM_GROUP_REQUEST_DEVICE_AWARE_RES.put(READ_MEDIA_VISUAL,
                R.string.permgrouprequest_device_aware_read_media_visual);
        PERM_GROUP_REQUEST_DEVICE_AWARE_RES.put(MICROPHONE,
                R.string.permgrouprequest_device_aware_microphone);
        PERM_GROUP_REQUEST_DEVICE_AWARE_RES
                .put(ACTIVITY_RECOGNITION,
                        R.string.permgrouprequest_device_aware_activityRecognition);
        PERM_GROUP_REQUEST_DEVICE_AWARE_RES.put(CAMERA,
                R.string.permgrouprequest_device_aware_camera);
        PERM_GROUP_REQUEST_DEVICE_AWARE_RES.put(CALL_LOG,
                R.string.permgrouprequest_device_aware_calllog);
        PERM_GROUP_REQUEST_DEVICE_AWARE_RES.put(PHONE,
                R.string.permgrouprequest_device_aware_phone);
        PERM_GROUP_REQUEST_DEVICE_AWARE_RES.put(SENSORS,
                R.string.permgrouprequest_device_aware_sensors);
        PERM_GROUP_REQUEST_DEVICE_AWARE_RES.put(NOTIFICATIONS,
                R.string.permgrouprequest_device_aware_notifications);

        PERM_GROUP_REQUEST_DETAIL_RES = new ArrayMap<>();
        PERM_GROUP_REQUEST_DETAIL_RES.put(LOCATION, R.string.permgrouprequestdetail_location);
        PERM_GROUP_REQUEST_DETAIL_RES.put(MICROPHONE, R.string.permgrouprequestdetail_microphone);
        PERM_GROUP_REQUEST_DETAIL_RES.put(CAMERA, R.string.permgrouprequestdetail_camera);

        PERM_GROUP_BACKGROUND_REQUEST_RES = new ArrayMap<>();
        PERM_GROUP_BACKGROUND_REQUEST_RES
                .put(LOCATION, R.string.permgroupbackgroundrequest_location);
        PERM_GROUP_BACKGROUND_REQUEST_RES
                .put(MICROPHONE, R.string.permgroupbackgroundrequest_microphone);
        PERM_GROUP_BACKGROUND_REQUEST_RES
                .put(CAMERA, R.string.permgroupbackgroundrequest_camera);
        PERM_GROUP_BACKGROUND_REQUEST_RES
                .put(SENSORS, R.string.permgroupbackgroundrequest_sensors);

        PERM_GROUP_BACKGROUND_REQUEST_DEVICE_AWARE_RES = new ArrayMap<>();
        PERM_GROUP_BACKGROUND_REQUEST_DEVICE_AWARE_RES
                .put(LOCATION, R.string.permgroupbackgroundrequest_device_aware_location);
        PERM_GROUP_BACKGROUND_REQUEST_DEVICE_AWARE_RES
                .put(MICROPHONE, R.string.permgroupbackgroundrequest_device_aware_microphone);
        PERM_GROUP_BACKGROUND_REQUEST_DEVICE_AWARE_RES
                .put(CAMERA, R.string.permgroupbackgroundrequest_device_aware_camera);
        PERM_GROUP_BACKGROUND_REQUEST_DEVICE_AWARE_RES
                .put(SENSORS, R.string.permgroupbackgroundrequest_device_aware_sensors);

        PERM_GROUP_BACKGROUND_REQUEST_DETAIL_RES = new ArrayMap<>();
        PERM_GROUP_BACKGROUND_REQUEST_DETAIL_RES
                .put(LOCATION, R.string.permgroupbackgroundrequestdetail_location);
        PERM_GROUP_BACKGROUND_REQUEST_DETAIL_RES
                .put(MICROPHONE, R.string.permgroupbackgroundrequestdetail_microphone);
        PERM_GROUP_BACKGROUND_REQUEST_DETAIL_RES
                .put(CAMERA, R.string.permgroupbackgroundrequestdetail_camera);
        PERM_GROUP_BACKGROUND_REQUEST_DETAIL_RES
                .put(SENSORS, R.string.permgroupbackgroundrequestdetail_sensors);

        PERM_GROUP_UPGRADE_REQUEST_RES = new ArrayMap<>();
        PERM_GROUP_UPGRADE_REQUEST_RES.put(LOCATION, R.string.permgroupupgraderequest_location);
        PERM_GROUP_UPGRADE_REQUEST_RES.put(MICROPHONE, R.string.permgroupupgraderequest_microphone);
        PERM_GROUP_UPGRADE_REQUEST_RES.put(CAMERA, R.string.permgroupupgraderequest_camera);
        PERM_GROUP_UPGRADE_REQUEST_RES.put(SENSORS, R.string.permgroupupgraderequest_sensors);

        PERM_GROUP_UPGRADE_REQUEST_DEVICE_AWARE_RES = new ArrayMap<>();
        PERM_GROUP_UPGRADE_REQUEST_DEVICE_AWARE_RES.put(LOCATION,
                R.string.permgroupupgraderequest_device_aware_location);
        PERM_GROUP_UPGRADE_REQUEST_DEVICE_AWARE_RES.put(MICROPHONE,
                R.string.permgroupupgraderequest_device_aware_microphone);
        PERM_GROUP_UPGRADE_REQUEST_DEVICE_AWARE_RES.put(CAMERA,
                R.string.permgroupupgraderequest_device_aware_camera);
        PERM_GROUP_UPGRADE_REQUEST_DEVICE_AWARE_RES.put(SENSORS,
                R.string.permgroupupgraderequest_device_aware_sensors);

        PERM_GROUP_UPGRADE_REQUEST_DETAIL_RES = new ArrayMap<>();
        PERM_GROUP_UPGRADE_REQUEST_DETAIL_RES
                .put(LOCATION, R.string.permgroupupgraderequestdetail_location);
        PERM_GROUP_UPGRADE_REQUEST_DETAIL_RES
                .put(MICROPHONE, R.string.permgroupupgraderequestdetail_microphone);
        PERM_GROUP_UPGRADE_REQUEST_DETAIL_RES
                .put(CAMERA, R.string.permgroupupgraderequestdetail_camera);
        PERM_GROUP_UPGRADE_REQUEST_DETAIL_RES
                .put(SENSORS,  R.string.permgroupupgraderequestdetail_sensors);

        PERM_SENSOR_CODES = new ArrayMap<>();
        if (SdkLevel.isAtLeastS()) {
            PERM_SENSOR_CODES.put(CAMERA, SensorPrivacyManager.Sensors.CAMERA);
            PERM_SENSOR_CODES.put(MICROPHONE, SensorPrivacyManager.Sensors.MICROPHONE);
        }

        PERM_BLOCKED_ICON = new ArrayMap<>();
        PERM_BLOCKED_ICON.put(CAMERA, R.drawable.ic_camera_blocked);
        PERM_BLOCKED_ICON.put(MICROPHONE, R.drawable.ic_mic_blocked);
        PERM_BLOCKED_ICON.put(LOCATION, R.drawable.ic_location_blocked);

        PERM_BLOCKED_TITLE = new ArrayMap<>();
        PERM_BLOCKED_TITLE.put(CAMERA, R.string.blocked_camera_title);
        PERM_BLOCKED_TITLE.put(MICROPHONE, R.string.blocked_microphone_title);
        PERM_BLOCKED_TITLE.put(LOCATION, R.string.blocked_location_title);

    }

    private Utils() {
        /* do nothing - hide constructor */
    }

    private static Object sLock = new Object();

    private static ArrayMap<UserHandle, Context> sUserContexts = new ArrayMap<>();

    /**
     * Creates and caches a PackageContext for the requested user, or returns the previously cached
     * value. The package of the PackageContext is the application's package.
     *
     * @param context The context of the currently running application
     * @param user The desired user for the context
     *
     * @return The generated or cached Context for the requested user
     *
     * @throws RuntimeException If the app has no package name attached, which should never happen
     */
    public static @NonNull Context getUserContext(Context context, UserHandle user) {
        synchronized (sLock) {
            if (!sUserContexts.containsKey(user)) {
                sUserContexts.put(user, context.getApplicationContext()
                        .createContextAsUser(user, 0));
            }
            return Preconditions.checkNotNull(sUserContexts.get(user));
        }
    }

    /**
     * {@code @NonNull} version of {@link Context#getSystemService(Class)}
     */
    public static @NonNull <M> M getSystemServiceSafe(@NonNull Context context, Class<M> clazz) {
        return Preconditions.checkNotNull(context.getSystemService(clazz),
                "Could not resolve " + clazz.getSimpleName());
    }

    /**
     * {@code @NonNull} version of {@link Context#getSystemService(Class)}
     */
    public static @NonNull <M> M getSystemServiceSafe(@NonNull Context context, Class<M> clazz,
            @NonNull UserHandle user) {
        try {
            return Preconditions.checkNotNull(context.createPackageContextAsUser(
                    context.getPackageName(), 0, user).getSystemService(clazz),
                    "Could not resolve " + clazz.getSimpleName());
        } catch (PackageManager.NameNotFoundException neverHappens) {
            throw new IllegalStateException();
        }
    }

    /**
     * {@code @NonNull} version of {@link Intent#getParcelableExtra(String)}
     */
    public static @NonNull <T extends Parcelable> T getParcelableExtraSafe(@NonNull Intent intent,
            @NonNull String name) {
        return Preconditions.checkNotNull(intent.getParcelableExtra(name),
                "Could not get parcelable extra for " + name);
    }

    /**
     * {@code @NonNull} version of {@link Intent#getStringExtra(String)}
     */
    public static @NonNull String getStringExtraSafe(@NonNull Intent intent,
            @NonNull String name) {
        return Preconditions.checkNotNull(intent.getStringExtra(name),
                "Could not get string extra for " + name);
    }

    /**
     * Returns true if a permission is dangerous, installed, and not removed
     * @param permissionInfo The permission we wish to check
     * @return If all of the conditions are met
     */
    public static boolean isPermissionDangerousInstalledNotRemoved(PermissionInfo permissionInfo) {
        return permissionInfo != null
                  && permissionInfo.getProtection() == PermissionInfo.PROTECTION_DANGEROUS
                  && (permissionInfo.flags & PermissionInfo.FLAG_INSTALLED) != 0
                  && (permissionInfo.flags & PermissionInfo.FLAG_REMOVED) == 0;
    }

    /**
     * Get the {@link PermissionInfo infos} for all permission infos belonging to a group.
     *
     * @param pm    Package manager to use to resolve permission infos
     * @param group the group
     *
     * @return The infos of permissions belonging to the group or an empty list if the group
     *         does not have runtime permissions
     */
    public static @NonNull List<PermissionInfo> getPermissionInfosForGroup(
            @NonNull PackageManager pm, @NonNull String group)
            throws PackageManager.NameNotFoundException {
        List<PermissionInfo> permissions = pm.queryPermissionsByGroup(group, 0);
        permissions.addAll(PermissionMapping.getPlatformPermissionsOfGroup(pm, group));

        /*
         * If the undefined group is requested, the package manager will return all platform
         * permissions, since they are marked as Undefined in the manifest. Do not return these
         * permissions.
         */
        if (group.equals(Manifest.permission_group.UNDEFINED)) {
            List<PermissionInfo> undefinedPerms = new ArrayList<>();
            for (PermissionInfo permissionInfo : permissions) {
                String permGroup =
                        PermissionMapping.getGroupOfPlatformPermission(permissionInfo.name);
                if (permGroup == null || permGroup.equals(Manifest.permission_group.UNDEFINED)) {
                    undefinedPerms.add(permissionInfo);
                }
            }
            return undefinedPerms;
        }

        return permissions;
    }

    /**
     * Get the {@link PermissionInfo infos} for all runtime installed permission infos belonging to
     * a group.
     *
     * @param pm    Package manager to use to resolve permission infos
     * @param group the group
     *
     * @return The infos of installed runtime permissions belonging to the group or an empty list
     * if the group does not have runtime permissions
     */
    public static @NonNull List<PermissionInfo> getInstalledRuntimePermissionInfosForGroup(
            @NonNull PackageManager pm, @NonNull String group)
            throws PackageManager.NameNotFoundException {
        List<PermissionInfo> permissions = pm.queryPermissionsByGroup(group, 0);
        permissions.addAll(PermissionMapping.getPlatformPermissionsOfGroup(pm, group));

        List<PermissionInfo> installedRuntime = new ArrayList<>();
        for (PermissionInfo permissionInfo: permissions) {
            if (permissionInfo.getProtection() == PermissionInfo.PROTECTION_DANGEROUS
                    && (permissionInfo.flags & PermissionInfo.FLAG_INSTALLED) != 0
                    && (permissionInfo.flags & PermissionInfo.FLAG_REMOVED) == 0) {
                installedRuntime.add(permissionInfo);
            }
        }

        /*
         * If the undefined group is requested, the package manager will return all platform
         * permissions, since they are marked as Undefined in the manifest. Do not return these
         * permissions.
         */
        if (group.equals(Manifest.permission_group.UNDEFINED)) {
            List<PermissionInfo> undefinedPerms = new ArrayList<>();
            for (PermissionInfo permissionInfo : installedRuntime) {
                String permGroup =
                        PermissionMapping.getGroupOfPlatformPermission(permissionInfo.name);
                if (permGroup == null || permGroup.equals(Manifest.permission_group.UNDEFINED)) {
                    undefinedPerms.add(permissionInfo);
                }
            }
            return undefinedPerms;
        }

        return installedRuntime;
    }

    /**
     * Get the {@link PackageItemInfo infos} for the given permission group.
     *
     * @param groupName the group
     * @param context the {@code Context} to retrieve {@code PackageManager}
     *
     * @return The info of permission group or null if the group does not have runtime permissions.
     */
    public static @Nullable PackageItemInfo getGroupInfo(@NonNull String groupName,
            @NonNull Context context) {
        try {
            return context.getPackageManager().getPermissionGroupInfo(groupName, 0);
        } catch (NameNotFoundException e) {
            /* ignore */
        }
        try {
            return context.getPackageManager().getPermissionInfo(groupName, 0);
        } catch (NameNotFoundException e) {
            /* ignore */
        }
        return null;
    }

    /**
     * Get the {@link PermissionInfo infos} for all permission infos belonging to a group.
     *
     * @param groupName the group
     * @param context the {@code Context} to retrieve {@code PackageManager}
     *
     * @return The infos of permissions belonging to the group or null if the group does not have
     *         runtime permissions.
     */
    public static @Nullable List<PermissionInfo> getGroupPermissionInfos(@NonNull String groupName,
            @NonNull Context context) {
        try {
            return Utils.getPermissionInfosForGroup(context.getPackageManager(), groupName);
        } catch (NameNotFoundException e) {
            /* ignore */
        }
        try {
            PermissionInfo permissionInfo = context.getPackageManager()
                    .getPermissionInfo(groupName, 0);
            List<PermissionInfo> permissions = new ArrayList<>();
            permissions.add(permissionInfo);
            return permissions;
        } catch (NameNotFoundException e) {
            /* ignore */
        }
        return null;
    }

    /**
     * Get the label for an application, truncating if it is too long.
     *
     * @param applicationInfo the {@link ApplicationInfo} of the application
     * @param context the {@code Context} to retrieve {@code PackageManager}
     *
     * @return the label for the application
     */
    @NonNull
    public static String getAppLabel(@NonNull ApplicationInfo applicationInfo,
            @NonNull Context context) {
        return getAppLabel(applicationInfo, DEFAULT_MAX_LABEL_SIZE_PX, context);
    }

    /**
     * Get the full label for an application without truncation.
     *
     * @param applicationInfo the {@link ApplicationInfo} of the application
     * @param context the {@code Context} to retrieve {@code PackageManager}
     *
     * @return the label for the application
     */
    @NonNull
    public static String getFullAppLabel(@NonNull ApplicationInfo applicationInfo,
            @NonNull Context context) {
        return getAppLabel(applicationInfo, 0, context);
    }

    /**
     * Get the label for an application with the ability to control truncating.
     *
     * @param applicationInfo the {@link ApplicationInfo} of the application
     * @param ellipsizeDip see {@link TextUtils#makeSafeForPresentation}.
     * @param context the {@code Context} to retrieve {@code PackageManager}
     *
     * @return the label for the application
     */
    @NonNull
    private static String getAppLabel(@NonNull ApplicationInfo applicationInfo, float ellipsizeDip,
            @NonNull Context context) {
        return BidiFormatter.getInstance().unicodeWrap(applicationInfo.loadSafeLabel(
                context.getPackageManager(), ellipsizeDip,
                TextUtils.SAFE_STRING_FLAG_TRIM | TextUtils.SAFE_STRING_FLAG_FIRST_LINE)
                .toString());
    }

    public static Drawable loadDrawable(PackageManager pm, String pkg, int resId) {
        try {
            return pm.getResourcesForApplication(pkg).getDrawable(resId, null);
        } catch (Resources.NotFoundException | PackageManager.NameNotFoundException e) {
            Log.d(LOG_TAG, "Couldn't get resource", e);
            return null;
        }
    }

    /**
     * Should UI show this permission.
     *
     * <p>If the user cannot change the group, it should not be shown.
     *
     * @param group The group that might need to be shown to the user
     *
     * @return
     */
    public static boolean shouldShowPermission(Context context, AppPermissionGroup group) {
        if (!group.isGrantingAllowed()) {
            return false;
        }

        final boolean isPlatformPermission = group.getDeclaringPackage().equals(OS_PKG);
        // Show legacy permissions only if the user chose that.
        if (isPlatformPermission
                && !PermissionMapping.isPlatformPermissionGroup(group.getName())) {
            return false;
        }
        return true;
    }

    public static Drawable applyTint(Context context, Drawable icon, int attr) {
        Theme theme = context.getTheme();
        TypedValue typedValue = new TypedValue();
        theme.resolveAttribute(attr, typedValue, true);
        icon = icon.mutate();
        icon.setTint(context.getColor(typedValue.resourceId));
        return icon;
    }

    public static Drawable applyTint(Context context, int iconResId, int attr) {
        return applyTint(context, context.getDrawable(iconResId), attr);
    }

    /**
     * Get the color resource id based on the attribute
     *
     * @return Resource id for the color
     */
    @ColorRes
    public static int getColorResId(Context context, int attr) {
        Theme theme = context.getTheme();
        TypedValue typedValue = new TypedValue();
        theme.resolveAttribute(attr, typedValue, true);
        return typedValue.resourceId;
    }

    /**
     * Is the group or background group user sensitive?
     *
     * @param group The group that might be user sensitive
     *
     * @return {@code true} if the group (or it's subgroup) is user sensitive.
     */
    public static boolean isGroupOrBgGroupUserSensitive(AppPermissionGroup group) {
        return group.isUserSensitive() || (group.getBackgroundPermissions() != null
                && group.getBackgroundPermissions().isUserSensitive());
    }

    public static boolean areGroupPermissionsIndividuallyControlled(Context context, String group) {
        if (!context.getPackageManager().arePermissionsIndividuallyControlled()) {
            return false;
        }
        return Manifest.permission_group.SMS.equals(group)
                || Manifest.permission_group.PHONE.equals(group)
                || Manifest.permission_group.CONTACTS.equals(group)
                || Manifest.permission_group.CALL_LOG.equals(group);
    }

    public static boolean isPermissionIndividuallyControlled(Context context, String permission) {
        if (!context.getPackageManager().arePermissionsIndividuallyControlled()) {
            return false;
        }
        return Manifest.permission.READ_CONTACTS.equals(permission)
                || Manifest.permission.WRITE_CONTACTS.equals(permission)
                || Manifest.permission.SEND_SMS.equals(permission)
                || Manifest.permission.RECEIVE_SMS.equals(permission)
                || Manifest.permission.READ_SMS.equals(permission)
                || Manifest.permission.RECEIVE_MMS.equals(permission)
                || Manifest.permission.CALL_PHONE.equals(permission)
                || Manifest.permission.READ_CALL_LOG.equals(permission)
                || Manifest.permission.WRITE_CALL_LOG.equals(permission);
    }

    /**
     * Get the message shown to grant a permission group to an app.
     *
     * @param appLabel The label of the app
     * @param packageName The package name of the app
     * @param groupName The name of the permission group
     * @param context A context to resolve resources
     * @param requestRes The resource id of the grant request message
     * @return The formatted message to be used as title when granting permissions
     */
    @NonNull
    public static CharSequence getRequestMessage(
            @NonNull String appLabel,
            @NonNull String packageName,
            @NonNull String groupName,
            @NonNull Context context,
            @StringRes int requestRes) {
        String escapedAppLabel = Html.escapeHtml(appLabel);

        boolean isIsolatedStorage;
        try {
            isIsolatedStorage = !isNonIsolatedStorage(context, packageName);
        } catch (NameNotFoundException e) {
            isIsolatedStorage = false;
        }
        if (groupName.equals(STORAGE) && isIsolatedStorage) {
            return Html.fromHtml(
                    String.format(
                            context.getResources().getConfiguration().getLocales().get(0),
                            context.getString(R.string.permgrouprequest_storage_isolated),
                            escapedAppLabel),
                    0);
        } else if (requestRes != 0) {
            return Html.fromHtml(context.getResources().getString(requestRes, escapedAppLabel), 0);
        }

        return Html.fromHtml(
                context.getString(
                        R.string.permission_warning_template,
                        escapedAppLabel,
                        loadGroupDescription(context, groupName, context.getPackageManager())),
                0);
    }

    /**
     * Get the message shown to grant a permission group to an app.
     *
     * @param appLabel The label of the app
     * @param packageName The package name of the app
     * @param groupName The name of the permission group
     * @param context A context to resolve resources
     * @param requestRes The resource id of the grant request message
     * @return The formatted message to be used as title when granting permissions
     */
    @NonNull
    public static CharSequence getRequestMessage(
            @NonNull String appLabel,
            @NonNull String packageName,
            @NonNull String groupName,
            @NonNull String deviceLabel,
            @NonNull Context context,
            Boolean isDeviceAwareMessage,
            @StringRes int requestRes) {
        if (!isDeviceAwareMessage) {
            return getRequestMessage(appLabel, packageName, groupName, context, requestRes);
        }
        String escapedAppLabel = Html.escapeHtml(appLabel);

        boolean isIsolatedStorage;
        try {
            isIsolatedStorage = !isNonIsolatedStorage(context, packageName);
        } catch (NameNotFoundException e) {
            isIsolatedStorage = false;
        }
        if (groupName.equals(STORAGE) && isIsolatedStorage) {
            String escapedDeviceLabel = Html.escapeHtml(deviceLabel);
            return Html.fromHtml(
                    String.format(
                            context.getResources().getConfiguration().getLocales().get(0),
                            context.getString(
                                    R.string.permgrouprequest_device_aware_storage_isolated),
                            escapedAppLabel,
                            escapedDeviceLabel),
                    0);

        } else if (requestRes != 0) {
            String escapedDeviceLabel = Html.escapeHtml(deviceLabel);
            return Html.fromHtml(context.getResources().getString(requestRes, escapedAppLabel,
                    escapedDeviceLabel), 0);
        }

        return Html.fromHtml(
                context.getString(
                        R.string.permission_warning_template,
                        escapedAppLabel,
                        loadGroupDescription(context, groupName, context.getPackageManager())),
                0);
    }

    private static CharSequence loadGroupDescription(Context context, String groupName,
            @NonNull PackageManager packageManager) {
        PackageItemInfo groupInfo = getGroupInfo(groupName, context);
        CharSequence description = null;
        if (groupInfo instanceof PermissionGroupInfo) {
            description = ((PermissionGroupInfo) groupInfo).loadDescription(packageManager);
        } else if (groupInfo instanceof PermissionInfo) {
            description = ((PermissionInfo) groupInfo).loadDescription(packageManager);
        }

        if (description == null || description.length() <= 0) {
            description = context.getString(R.string.default_permission_description);
        }

        return description;
    }

    /**
     * Whether or not the given package has non-isolated storage permissions
     * @param context The current context
     * @param packageName The package name to check
     * @return True if the package has access to non-isolated storage, false otherwise
     * @throws NameNotFoundException
     */
    public static boolean isNonIsolatedStorage(@NonNull Context context,
            @NonNull String packageName) throws NameNotFoundException {
        PackageInfo packageInfo = context.getPackageManager().getPackageInfo(packageName, 0);
        AppOpsManager manager = context.getSystemService(AppOpsManager.class);


        return packageInfo.applicationInfo.targetSdkVersion < Build.VERSION_CODES.P
                || (packageInfo.applicationInfo.targetSdkVersion < Build.VERSION_CODES.R
                && manager.unsafeCheckOpNoThrow(OPSTR_LEGACY_STORAGE,
                packageInfo.applicationInfo.uid, packageInfo.packageName) == MODE_ALLOWED);
    }

    /**
     * Gets whether the STORAGE group should be hidden from the UI for this package. This is true
     * when the platform is T+, and the package has legacy storage access (i.e., either the package
     * has a targetSdk less than Q, or has a targetSdk equal to Q and has OPSTR_LEGACY_STORAGE).
     *
     * TODO jaysullivan: This is always calling AppOpsManager; not taking advantage of LiveData
     *
     * @param pkg The package to check
     */
    public static boolean shouldShowStorage(LightPackageInfo pkg) {
        if (!SdkLevel.isAtLeastT()) {
            return true;
        }
        int targetSdkVersion = pkg.getTargetSdkVersion();
        PermissionControllerApplication app = PermissionControllerApplication.get();
        Context context = Utils.getUserContext(app, UserHandle.getUserHandleForUid(pkg.getUid()));
        AppOpsManager appOpsManager = context.getSystemService(AppOpsManager.class);
        if (appOpsManager == null) {
            return true;
        }

        return targetSdkVersion < Build.VERSION_CODES.Q
                || (targetSdkVersion == Build.VERSION_CODES.Q
                && appOpsManager.unsafeCheckOpNoThrow(OPSTR_LEGACY_STORAGE, pkg.getUid(),
                pkg.getPackageName()) == MODE_ALLOWED);
    }

    /**
     * Build a string representing the given time if it happened on the current day and the date
     * otherwise.
     *
     * @param context the context.
     * @param lastAccessTime the time in milliseconds.
     *
     * @return a string representing the time or date of the given time or null if the time is 0.
     */
    public static @Nullable String getAbsoluteTimeString(@NonNull Context context,
            long lastAccessTime) {
        if (lastAccessTime == 0) {
            return null;
        }
        if (isToday(lastAccessTime)) {
            return DateFormat.getTimeFormat(context).format(lastAccessTime);
        } else {
            return DateFormat.getMediumDateFormat(context).format(lastAccessTime);
        }
    }

    /**
     * Check whether the given time (in milliseconds) is in the current day.
     *
     * @param time the time in milliseconds
     *
     * @return whether the given time is in the current day.
     */
    private static boolean isToday(long time) {
        Calendar today = Calendar.getInstance(Locale.getDefault());
        today.setTimeInMillis(System.currentTimeMillis());
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);

        Calendar date = Calendar.getInstance(Locale.getDefault());
        date.setTimeInMillis(time);
        return !date.before(today);
    }

    /**
     * Add a menu item for searching Settings, if there is an activity handling the action.
     *
     * @param menu the menu to add the menu item into
     * @param context the context for checking whether there is an activity handling the action
     */
    public static void prepareSearchMenuItem(@NonNull Menu menu, @NonNull Context context) {
        Intent intent = new Intent(Settings.ACTION_APP_SEARCH_SETTINGS);
        if (context.getPackageManager().resolveActivity(intent, 0) == null) {
            return;
        }
        MenuItem searchItem = menu.add(Menu.NONE, Menu.NONE, Menu.NONE,
                com.android.settingslib.search.widget.R.string.search_menu);
        searchItem.setIcon(com.android.settingslib.search.widget.R.drawable.ic_search_24dp);
        searchItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        searchItem.setOnMenuItemClickListener(item -> {
            try {
                context.startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Log.e(LOG_TAG, "Cannot start activity to search settings", e);
            }
            return true;
        });
    }

    /**
     * Get badged app icon if necessary, similar as used in the Settings UI.
     *
     * @param context The context to use
     * @param appInfo The app the icon belong to
     *
     * @return The icon to use
     */
    public static @NonNull Drawable getBadgedIcon(@NonNull Context context,
            @NonNull ApplicationInfo appInfo) {
        UserHandle user = UserHandle.getUserHandleForUid(appInfo.uid);
        try (IconFactory iconFactory = IconFactory.obtain(context)) {
            Bitmap iconBmp = iconFactory.createBadgedIconBitmap(
                    appInfo.loadUnbadgedIcon(context.getPackageManager()), user, false).icon;
            return new BitmapDrawable(context.getResources(), iconBmp);
        }
    }

    /**
     * Get a string saying what apps with the given permission group can do.
     *
     * @param context The context to use
     * @param groupName The name of the permission group
     * @param description The description of the permission group
     *
     * @return a string saying what apps with the given permission group can do.
     */
    public static @NonNull String getPermissionGroupDescriptionString(@NonNull Context context,
            @NonNull String groupName, @NonNull CharSequence description) {
        switch (groupName) {
            case ACTIVITY_RECOGNITION:
                return context.getString(
                        R.string.permission_description_summary_activity_recognition);
            case CALENDAR:
                return context.getString(R.string.permission_description_summary_calendar);
            case CALL_LOG:
                return context.getString(R.string.permission_description_summary_call_log);
            case CAMERA:
                return context.getString(R.string.permission_description_summary_camera);
            case CONTACTS:
                return context.getString(R.string.permission_description_summary_contacts);
            case LOCATION:
                return context.getString(R.string.permission_description_summary_location);
            case MICROPHONE:
                return context.getString(R.string.permission_description_summary_microphone);
            case NEARBY_DEVICES:
                return context.getString(R.string.permission_description_summary_nearby_devices);
            case PHONE:
                return context.getString(R.string.permission_description_summary_phone);
            case READ_MEDIA_AURAL:
                return context.getString(R.string.permission_description_summary_read_media_aural);
            case READ_MEDIA_VISUAL:
                return context.getString(R.string.permission_description_summary_read_media_visual);
            case SENSORS:
                return context.getString(R.string.permission_description_summary_sensors);
            case SMS:
                return context.getString(R.string.permission_description_summary_sms);
            case STORAGE:
                return context.getString(R.string.permission_description_summary_storage);
            default:
                return context.getString(R.string.permission_description_summary_generic,
                        description);
        }
    }

    /**
     * Whether we should show health permissions as platform permissions in the various
     * permission controller UI.
     */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codename = "UpsideDownCake")
    public static boolean isHealthPermissionUiEnabled() {
        final long token = Binder.clearCallingIdentity();
        try {
            return SdkLevel.isAtLeastU()
                    && DeviceConfig.getBoolean(DeviceConfig.NAMESPACE_PRIVACY,
                    PROPERTY_HEALTH_PERMISSION_UI_ENABLED, true);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    /**
     * Returns true if the group name passed is that of the Platform health group.
     * @param permGroupName name of the group that needs to be checked.
     */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public static Boolean isHealthPermissionGroup(String permGroupName) {
        return SdkLevel.isAtLeastU() && HEALTH_PERMISSION_GROUP.equals(permGroupName);
    }

    /**
     * Return whether health permission setting entry should be shown or not
     *
     * Should not show Health permissions preference if the package doesn't handle
     * VIEW_PERMISSION_USAGE_INTENT.
     *
     * Will show if above is true AND permission is already granted.
     *
     * @param packageInfo the {@link PackageInfo} app which uses the permission
     * @param permGroupName the health permission group name to show
     * @return {@code TRUE} iff health permission should be shown
     */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public static Boolean shouldShowHealthPermission(LightPackageInfo packageInfo,
            String permGroupName) {
        if (!isHealthPermissionGroup(permGroupName)) {
            return false;
        }

        PermissionControllerApplication app = PermissionControllerApplication.get();
        PackageManager pm = app.getPackageManager();
        Context context = getUserContext(app, UserHandle.getUserHandleForUid(packageInfo.getUid()));

        List<PermissionInfo> permissions = new ArrayList<>();
        try {
            permissions.addAll(getPermissionInfosForGroup(pm, permGroupName));
        } catch (NameNotFoundException e) {
            Log.e(LOG_TAG, "No permissions found for permission group " + permGroupName);
            return false;
        }

        // Check in permission is already granted as we should not hide it in the UX at that point.
        List<String> grantedPermissions = packageInfo.getGrantedPermissions();
        for (PermissionInfo permission : permissions) {
            boolean isCurrentlyGranted = grantedPermissions.contains(permission.name);
            if (isCurrentlyGranted) {
                Log.d(LOG_TAG, "At least one Health permission group permission is granted, "
                        + "show permission group entry");
                return true;
            }
        }

        Intent viewUsageIntent = new Intent(Intent.ACTION_VIEW_PERMISSION_USAGE);
        viewUsageIntent.addCategory(HealthConnectManager.CATEGORY_HEALTH_PERMISSIONS);
        viewUsageIntent.setPackage(packageInfo.getPackageName());

        ResolveInfo resolveInfo = pm.resolveActivity(viewUsageIntent, PackageManager.MATCH_ALL);
        if (resolveInfo == null) {
            Log.e(LOG_TAG, "Package that asks for Health permission must also handle "
                    + "VIEW_PERMISSION_USAGE_INTENT.");
            return false;
        }
        return true;
    }

    /**
     * Get a device protected storage based shared preferences. Avoid storing sensitive data in it.
     *
     * @param context the context to get the shared preferences
     * @return a device protected storage based shared preferences
     */
    @NonNull
    public static SharedPreferences getDeviceProtectedSharedPreferences(@NonNull Context context) {
        if (!context.isDeviceProtectedStorage()) {
            context = context.createDeviceProtectedStorageContext();
        }
        return context.getSharedPreferences(Constants.PREFERENCES_FILE, MODE_PRIVATE);
    }

    public static long getOneTimePermissionsTimeout() {
        return DeviceConfig.getLong(DeviceConfig.NAMESPACE_PERMISSIONS,
                PROPERTY_ONE_TIME_PERMISSIONS_TIMEOUT_MILLIS, ONE_TIME_PERMISSIONS_TIMEOUT_MILLIS);
    }

    /**
     * Returns the delay in milliseconds before revoking permissions at the end of a one-time
     * permission session if all processes have been killed.
     * If the session was triggered by a self-revocation, then revocation should happen
     * immediately. For a regular one-time permission session, a grace period allows a quick
     * app restart without losing the permission.
     * @param isSelfRevoked If true, return the delay for a self-revocation session. Otherwise,
     *                      return delay for a regular one-time permission session.
     */
    public static long getOneTimePermissionsKilledDelay(boolean isSelfRevoked) {
        if (isSelfRevoked) {
            // For a self-revoked session, we revoke immediately when the process dies.
            return 0;
        }
        return DeviceConfig.getLong(DeviceConfig.NAMESPACE_PERMISSIONS,
                PROPERTY_ONE_TIME_PERMISSIONS_KILLED_DELAY_MILLIS,
                ONE_TIME_PERMISSIONS_KILLED_DELAY_MILLIS);
    }

    /**
     * Get context of the parent user of the profile group (i.e. usually the 'personal' profile,
     * not the 'work' profile).
     *
     * @param context The context of a user of the profile user group.
     *
     * @return The context of the parent user
     */
    public static Context getParentUserContext(@NonNull Context context) {
        UserHandle parentUser = getSystemServiceSafe(context, UserManager.class)
                .getProfileParent(UserHandle.of(myUserId()));

        if (parentUser == null) {
            return context;
        }

        // In a multi profile environment perform all operations as the parent user of the
        // current profile
        try {
            return context.createPackageContextAsUser(context.getPackageName(), 0,
                    parentUser);
        } catch (PackageManager.NameNotFoundException e) {
            // cannot happen
            throw new IllegalStateException("Could not switch to parent user " + parentUser, e);
        }
    }

    /**
     * The resource id for the request message for a permission group
     * @param groupName Permission group name
     * @return The id or 0 if the permission group doesn't exist or have a message
     */
    public static int getRequest(String groupName) {
        return getRequest(groupName, false);
    }

    /**
     * The resource id for the request message for a permission group for a specific device
     *
     * @param groupName Permission group name
     * @return The id or 0 if the permission group doesn't exist or have a message
     */
    public static int getRequest(String groupName, Boolean isDeviceAwareMessage) {
        if (isDeviceAwareMessage) {
            return PERM_GROUP_REQUEST_DEVICE_AWARE_RES.getOrDefault(groupName, 0);
        } else {
            return PERM_GROUP_REQUEST_RES.getOrDefault(groupName, 0);
        }
    }

    /**
     * The resource id for the request detail message for a permission group
     * @param groupName Permission group name
     * @return The id or 0 if the permission group doesn't exist or have a message
     */
    public static int getRequestDetail(String groupName) {
        return PERM_GROUP_REQUEST_DETAIL_RES.getOrDefault(groupName, 0);
    }

    /**
     * The resource id for the background request message for a permission group
     * @param groupName Permission group name
     * @return The id or 0 if the permission group doesn't exist or have a message
     */
    public static int getBackgroundRequest(String groupName) {
        return getBackgroundRequest(groupName, false);
    }

    /**
     * The resource id for the background request message for a permission group for a specific
     * device
     *
     * @param groupName Permission group name
     * @return The id or 0 if the permission group doesn't exist or have a message
     */
    public static int getBackgroundRequest(String groupName, Boolean isDeviceAwareMessage) {
        if (isDeviceAwareMessage) {
            return PERM_GROUP_BACKGROUND_REQUEST_DEVICE_AWARE_RES.getOrDefault(groupName, 0);
        } else {
            return PERM_GROUP_BACKGROUND_REQUEST_RES.getOrDefault(groupName, 0);
        }
    }

    /**
     * The resource id for the background request detail message for a permission group
     * @param groupName Permission group name
     * @return The id or 0 if the permission group doesn't exist or have a message
     */
    public static int getBackgroundRequestDetail(String groupName) {
        return PERM_GROUP_BACKGROUND_REQUEST_DETAIL_RES.getOrDefault(groupName, 0);
    }

    /**
     * The resource id for the upgrade request message for a permission group
     * @param groupName Permission group name
     * @return The id or 0 if the permission group doesn't exist or have a message
     */
    public static int getUpgradeRequest(String groupName) {
        return getUpgradeRequest(groupName, false);
    }

    /**
     * The resource id for the upgrade request message for a permission group for a specific device.
     *
     * @param groupName Permission group name
     * @return The id or 0 if the permission group doesn't exist or have a message
     */
    public static int getUpgradeRequest(String groupName, Boolean isDeviceAwareMessage) {
        if (isDeviceAwareMessage) {
            return PERM_GROUP_UPGRADE_REQUEST_DEVICE_AWARE_RES.getOrDefault(groupName, 0);
        } else {
            return PERM_GROUP_UPGRADE_REQUEST_RES.getOrDefault(groupName, 0);
        }
    }

    /**
     * The resource id for the upgrade request detail message for a permission group
     * @param groupName Permission group name
     * @return The id or 0 if the permission group doesn't exist or have a message
     */
    public static int getUpgradeRequestDetail(String groupName) {
        return PERM_GROUP_UPGRADE_REQUEST_DETAIL_RES.getOrDefault(groupName, 0);
    }

    /**
     * The resource id for the fine location request message for a specific device
     *
     * @return The id
     */
    public static int getFineLocationRequest(Boolean isDeviceAwareMessage) {
        if (isDeviceAwareMessage) {
            return R.string.permgrouprequest_device_aware_fineupgrade;
        } else {
            return R.string.permgrouprequest_fineupgrade;
        }
    }

    /**
     * The resource id for the coarse location request message for a specific device
     *
     * @return The id
     */
    public static int getCoarseLocationRequest(Boolean isDeviceAwareMessage) {
        if (isDeviceAwareMessage) {
            return R.string.permgrouprequest_device_aware_coarselocation;
        } else {
            return R.string.permgrouprequest_coarselocation;
        }
    }

    /**
     * The resource id for the get more photos request message for a specific device
     *
     * @return The id
     */
    public static int getMorePhotosRequest(Boolean isDeviceAwareMessage) {
        if (isDeviceAwareMessage) {
            return R.string.permgrouprequest_device_aware_more_photos;
        } else {
            return R.string.permgrouprequest_more_photos;
        }
    }

    /**
     * Returns a random session ID value that's guaranteed to not be {@code INVALID_SESSION_ID}.
     *
     * @return A valid session ID.
     */
    public static long getValidSessionId() {
        long sessionId = INVALID_SESSION_ID;
        while (sessionId == INVALID_SESSION_ID) {
            sessionId = new Random().nextLong();
        }
        return sessionId;
    }

    /**
     * Retrieves an existing session ID from the given intent or generates a new one if none is
     * present.
     *
     * @return A valid session ID.
     */
    public static long getOrGenerateSessionId(Intent intent) {
        long sessionId = intent.getLongExtra(EXTRA_SESSION_ID, INVALID_SESSION_ID);
        if (sessionId == INVALID_SESSION_ID) {
            sessionId = getValidSessionId();
        }
        return sessionId;
    }

    /**
     * Gets the label of the Settings application
     *
     * @param pm The packageManager used to get the activity resolution
     *
     * @return The CharSequence title of the settings app
     */
    @Nullable
    public static CharSequence getSettingsLabelForNotifications(PackageManager pm) {
        // We pretend we're the Settings app sending the notification, so figure out its name.
        Intent openSettingsIntent = new Intent(Settings.ACTION_SETTINGS);
        ResolveInfo resolveInfo = pm.resolveActivity(openSettingsIntent, MATCH_SYSTEM_ONLY);
        if (resolveInfo == null) {
            return null;
        }
        return pm.getApplicationLabel(resolveInfo.activityInfo.applicationInfo);
    }

    /**
     * Determines if a given user is disabled, or is a work profile.
     * @param user The user to check
     * @return true if the user is disabled, or the user is a work profile
     */
    public static boolean isUserDisabledOrWorkProfile(UserHandle user) {
        Application app = PermissionControllerApplication.get();
        UserManager userManager = app.getSystemService(UserManager.class);
        // In android TV, parental control accounts are managed profiles
        return !userManager.getEnabledProfiles().contains(user)
                || (userManager.isManagedProfile(user.getIdentifier())
                    && !DeviceUtils.isTelevision(app));
    }

    /**
     * Determines if a given user ID belongs to a managed profile user.
     * @param userId The user ID to check
     * @return true if the user is a managed profile
     */
    public static boolean isUserManagedProfile(int userId) {
        return PermissionControllerApplication.get()
                .getSystemService(UserManager.class)
                .isManagedProfile(userId);
    }

    /**
     * Get all the exempted packages.
     */
    public static Set<String> getExemptedPackages(@NonNull RoleManager roleManager) {
        Set<String> exemptedPackages = new HashSet<>();

        exemptedPackages.add(OS_PKG);
        for (int i = 0; i < EXEMPTED_ROLES.length; i++) {
            exemptedPackages.addAll(roleManager.getRoleHolders(EXEMPTED_ROLES[i]));
        }

        return exemptedPackages;
    }

    /**
     * Get the timestamp and lastAccessType for the summary text
     * in app permission groups and permission apps screens
     * @return Triple<String, Integer, String> with the first being the formatted time
     * the second being lastAccessType and the third being the formatted date.
     */
    public static Triple<String, Integer, String> getPermissionLastAccessSummaryTimestamp(
            Long lastAccessTime, Context context, String groupName) {
        long midnightToday = ZonedDateTime.now().truncatedTo(ChronoUnit.DAYS).toEpochSecond()
                * 1000L;
        long midnightYesterday = ZonedDateTime.now().minusDays(1).truncatedTo(ChronoUnit.DAYS)
                .toEpochSecond() * 1000L;
        long yesterdayAtThisTime = ZonedDateTime.now().minusDays(1).toEpochSecond() * 1000L;

        boolean isLastAccessToday = lastAccessTime != null
                && midnightToday <= lastAccessTime;
        boolean isLastAccessWithinPast24h = lastAccessTime != null
                && yesterdayAtThisTime <= lastAccessTime;
        boolean isLastAccessTodayOrYesterday = lastAccessTime != null
                && midnightYesterday <= lastAccessTime;

        String lastAccessTimeFormatted = "";
        String lastAccessDateFormatted = "";
        @AppPermsLastAccessType int lastAccessType = NOT_IN_LAST_7D;

        if (lastAccessTime != null) {
            lastAccessTimeFormatted = DateFormat.getTimeFormat(context)
                    .format(lastAccessTime);
            lastAccessDateFormatted = DateFormat.getDateFormat(context)
                    .format(lastAccessTime);

            if (!PermissionMapping.SENSOR_DATA_PERMISSIONS.contains(groupName)) {
                // For content providers we show either the last access is within
                // past 24 hours or past 7 days
                lastAccessType = isLastAccessWithinPast24h
                        ? LAST_24H_CONTENT_PROVIDER : LAST_7D_CONTENT_PROVIDER;
            } else {
                // For sensor data permissions we show if the last access
                // is today, yesterday or older than yesterday
                lastAccessType = isLastAccessToday
                        ? LAST_24H_SENSOR_TODAY : isLastAccessTodayOrYesterday
                        ? LAST_24H_SENSOR_YESTERDAY : LAST_7D_SENSOR;
            }
        }

        return new Triple<>(lastAccessTimeFormatted, lastAccessType, lastAccessDateFormatted);
    }

    /**
     * Returns if the permission group is Camera or Microphone (status bar indicators).
     **/
    public static boolean isStatusBarIndicatorPermission(@NonNull String permissionGroupName) {
        return CAMERA.equals(permissionGroupName) || MICROPHONE.equals(permissionGroupName);
    }

    /**
     * Navigate to notification settings for all apps
     * @param context The current Context
     */
    public static void navigateToNotificationSettings(@NonNull Context context) {
        Intent notificationIntent = new Intent(Settings.ACTION_ALL_APPS_NOTIFICATION_SETTINGS);
        context.startActivity(notificationIntent);
    }

    /**
     * Navigate to notification settings for an app
     * @param context The current Context
     * @param packageName The package to navigate to
     * @param user Specifies the user of the package which should be navigated to. If null, the
     *             current user is used.
     */
    public static void navigateToAppNotificationSettings(@NonNull Context context,
            @NonNull String packageName, @NonNull UserHandle user) {
        Intent notificationIntent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        notificationIntent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName);
        context.startActivityAsUser(notificationIntent, user);
    }

    /**
     * Navigate to health connect settings for all apps
     * @param context The current Context
     */
    public static void navigateToHealthConnectSettings(@NonNull Context context) {
        Intent healthConnectIntent = new Intent(ACTION_MANAGE_HEALTH_PERMISSIONS);
        context.startActivity(healthConnectIntent);
    }

    /**
     * Navigate to health connect settings for an app
     * @param context The current Context
     * @param packageName The package's health connect settings to navigate to
     */
    public static void navigateToAppHealthConnectSettings(@NonNull Context context,
            @NonNull String packageName, @NonNull UserHandle user) {
        Intent appHealthConnectIntent = new Intent(ACTION_MANAGE_HEALTH_PERMISSIONS);
        appHealthConnectIntent.putExtra(EXTRA_PACKAGE_NAME, packageName);
        appHealthConnectIntent.putExtra(Intent.EXTRA_USER, user);
        context.startActivity(appHealthConnectIntent);
    }

    /**
     * Returns if a card should be shown if the sensor is blocked
     **/
    public static boolean shouldDisplayCardIfBlocked(@NonNull String permissionGroupName) {
        return CAMERA.equals(permissionGroupName) || MICROPHONE.equals(permissionGroupName)
                || LOCATION.equals(permissionGroupName);
    }

    /**
     * Returns the sensor code for a permission
     **/
    @RequiresApi(Build.VERSION_CODES.S)
    public static int getSensorCode(@NonNull String permissionGroupName) {
        return PERM_SENSOR_CODES.getOrDefault(permissionGroupName, -1);
    }

    /**
     * Returns the blocked icon code for a permission
     **/
    public static int getBlockedIcon(@NonNull String permissionGroupName) {
        return PERM_BLOCKED_ICON.getOrDefault(permissionGroupName, -1);
    }

    /**
     * Returns the blocked title code for a permission
     **/
    public static int getBlockedTitle(@NonNull String permissionGroupName) {
        return PERM_BLOCKED_TITLE.getOrDefault(permissionGroupName, -1);
    }

    /**
     * Returns if the permission group has a background mode, even if the background mode is
     * introduced in a platform version after the one currently running
     **/
    public static boolean hasPermWithBackgroundModeCompat(LightAppPermGroup group) {
        if (SdkLevel.isAtLeastS()) {
            return group.getHasPermWithBackgroundMode();
        }
        String groupName = group.getPermGroupName();
        return group.getHasPermWithBackgroundMode()
                || Manifest.permission_group.CAMERA.equals(groupName)
                || Manifest.permission_group.MICROPHONE.equals(groupName);
    }

    /**
     * Returns the appropriate enterprise string for the provided IDs
     */
    @NonNull
    public static String getEnterpriseString(@NonNull Context context,
            @NonNull String updatableStringId, int defaultStringId, @NonNull Object... formatArgs) {
        return SdkLevel.isAtLeastT()
                ? getUpdatableEnterpriseString(
                        context, updatableStringId, defaultStringId, formatArgs)
                : context.getString(defaultStringId, formatArgs);
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @NonNull
    private static String getUpdatableEnterpriseString(@NonNull Context context,
            @NonNull String updatableStringId, int defaultStringId, @NonNull Object... formatArgs) {
        DevicePolicyManager dpm = getSystemServiceSafe(context, DevicePolicyManager.class);
        return  dpm.getResources().getString(updatableStringId, () -> context.getString(
                defaultStringId, formatArgs), formatArgs);
    }

    /**
     * Get {@link PackageInfo} for this ComponentName.
     *
     * @param context The current Context
     * @param component component to get package info for
     * @return The package info
     *
     * @throws PackageManager.NameNotFoundException if package does not exist
     */
    @NonNull
    public static PackageInfo getPackageInfoForComponentName(@NonNull Context context,
            @NonNull ComponentName component) throws PackageManager.NameNotFoundException {
        return context.getPackageManager().getPackageInfo(component.getPackageName(), 0);
    }

    /**
     * Return the label to use for this application.
     *
     * @param context The current Context
     * @param applicationInfo The {@link ApplicationInfo} of the application to get the label of.
     * @return the label associated with this application, or its name if there is no label.
     */
    @NonNull
    public static String getApplicationLabel(@NonNull Context context,
            @NonNull ApplicationInfo applicationInfo) {
        return context.getPackageManager().getApplicationLabel(applicationInfo).toString();
    }

    /**
     * Returns whether the given user should be shown in the Settings UI in SdkLevel V+. This method
     * will always return true for SdkLevels below V.
     *
     * @param userHandle The user for which to check whether it should be shown or not.
     * @return true if it should be shown, false otherwise.
     */
    public static boolean shouldShowInSettings(UserHandle userHandle, UserManager userManager) {
        return !SdkLevel.isAtLeastV() || shouldShowInSettingsInternal(userHandle, userManager);
    }

    /**
     * Returns whether the given user should be shown in the Settings UI.
     *
     * @param userHandle The user for which to check whether it should be shown or not.
     * @return true if it should be shown, false otherwise.
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private static boolean shouldShowInSettingsInternal(
            UserHandle userHandle, UserManager userManager) {
        var userProperties = userManager.getUserProperties(userHandle);
        return !userManager.isQuietModeEnabled(userHandle)
                || userProperties.getShowInQuietMode() != UserProperties.SHOW_IN_QUIET_MODE_HIDDEN;
    }
}
