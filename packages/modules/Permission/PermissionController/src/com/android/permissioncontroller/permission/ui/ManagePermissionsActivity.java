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

package com.android.permissioncontroller.permission.ui;

import static android.health.connect.HealthPermissions.HEALTH_PERMISSION_GROUP;
import static android.view.WindowManager.LayoutParams.SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS;

import static com.android.permissioncontroller.Constants.ACTION_MANAGE_AUTO_REVOKE;
import static com.android.permissioncontroller.Constants.EXTRA_SESSION_ID;
import static com.android.permissioncontroller.Constants.INVALID_SESSION_ID;
import static com.android.permissioncontroller.Constants.UNUSED_APPS_SAFETY_CENTER_SOURCE_ID;
import static com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSION_GROUPS_FRAGMENT_AUTO_REVOKE_ACTION;
import static com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSION_GROUPS_FRAGMENT_AUTO_REVOKE_ACTION__ACTION__OPENED_FOR_AUTO_REVOKE;
import static com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSION_GROUPS_FRAGMENT_AUTO_REVOKE_ACTION__ACTION__OPENED_FROM_INTENT;
import static com.android.permissioncontroller.PermissionControllerStatsLog.AUTO_REVOKE_NOTIFICATION_CLICKED;
import static com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_USAGE_FRAGMENT_INTERACTION;
import static com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_USAGE_FRAGMENT_INTERACTION__ACTION__OPEN;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PermissionInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;
import android.permission.PermissionManager;
import android.provider.Settings;
import android.safetycenter.SafetyCenterManager;
import android.safetycenter.SafetyEvent;
import android.safetycenter.SafetySourceData;
import android.util.Log;
import android.view.MenuItem;

import androidx.fragment.app.Fragment;
import androidx.navigation.NavGraph;
import androidx.navigation.NavInflater;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;

import com.android.modules.utils.build.SdkLevel;
import com.android.permissioncontroller.Constants;
import com.android.permissioncontroller.DeviceUtils;
import com.android.permissioncontroller.PermissionControllerStatsLog;
import com.android.permissioncontroller.R;
import com.android.permissioncontroller.hibernation.HibernationPolicyKt;
import com.android.permissioncontroller.permission.ui.auto.AutoAllAppPermissionsFragment;
import com.android.permissioncontroller.permission.ui.auto.AutoAppPermissionsFragment;
import com.android.permissioncontroller.permission.ui.auto.AutoManageStandardPermissionsFragment;
import com.android.permissioncontroller.permission.ui.auto.AutoPermissionAppsFragment;
import com.android.permissioncontroller.permission.ui.auto.AutoReviewPermissionDecisionsFragment;
import com.android.permissioncontroller.permission.ui.auto.AutoUnusedAppsFragment;
import com.android.permissioncontroller.permission.ui.auto.dashboard.AutoPermissionUsageDetailsFragment;
import com.android.permissioncontroller.permission.ui.auto.dashboard.AutoPermissionUsageFragment;
import com.android.permissioncontroller.permission.ui.handheld.AppPermissionFragment;
import com.android.permissioncontroller.permission.ui.handheld.AppPermissionGroupsFragment;
import com.android.permissioncontroller.permission.ui.handheld.PermissionAppsFragment;
import com.android.permissioncontroller.permission.ui.handheld.v31.PermissionDetailsWrapperFragment;
import com.android.permissioncontroller.permission.ui.handheld.v31.PermissionUsageWrapperFragment;
import com.android.permissioncontroller.permission.ui.handheld.v34.AppDataSharingUpdatesFragment;
import com.android.permissioncontroller.permission.ui.legacy.AppPermissionActivity;
import com.android.permissioncontroller.permission.ui.television.TvUnusedAppsFragment;
import com.android.permissioncontroller.permission.ui.wear.WearAppPermissionFragment;
import com.android.permissioncontroller.permission.ui.wear.WearPermissionUsageDetailsFragment;
import com.android.permissioncontroller.permission.ui.wear.WearPermissionUsageFragment;
import com.android.permissioncontroller.permission.ui.wear.WearUnusedAppsFragment;
import com.android.permissioncontroller.permission.utils.KotlinUtils;
import com.android.permissioncontroller.permission.utils.PermissionMapping;
import com.android.permissioncontroller.permission.utils.Utils;

import java.util.Objects;
import java.util.Random;

/**
 * Activity to review and manage permissions
 */
public final class ManagePermissionsActivity extends SettingsActivity {
    private static final String LOG_TAG = ManagePermissionsActivity.class.getSimpleName();

    /**
     * Name of the extra parameter that indicates whether or not to show all app permissions
     */
    public static final String EXTRA_ALL_PERMISSIONS =
            "com.android.permissioncontroller.extra.ALL_PERMISSIONS";

    /**
     * Name of the extra parameter that is the fragment that called the current fragment.
     */
    public static final String EXTRA_CALLER_NAME =
            "com.android.permissioncontroller.extra.CALLER_NAME";

    // The permission group which was interacted with
    public static final String EXTRA_RESULT_PERMISSION_INTERACTED = "com.android"
            + ".permissioncontroller.extra.RESULT_PERMISSION_INTERACTED";
    /**
     * The result of the permission in terms of {@link GrantPermissionsViewHandler.Result}
     */
    public static final String EXTRA_RESULT_PERMISSION_RESULT = "com.android"
            + ".permissioncontroller.extra.PERMISSION_RESULT";

    /**
     * Whether to show system apps in UI receiving an intent containing this extra.
     */
    public static final String EXTRA_SHOW_SYSTEM = "com.android"
            + ".permissioncontroller.extra.SHOW_SYSTEM";

    /**
     * Whether to show 7 days permission usage data in UI receiving an intent containing this extra.
     */
    public static final String EXTRA_SHOW_7_DAYS = "com.android"
            + ".permissioncontroller.extra.SHOW_7_DAYS";

    /**
     * Name of the aliased activity.
     */
    public static final String ALIAS_ACTIVITY_NAME =
            "com.android.permissioncontroller.permission.ui.ManagePermissionsActivityAlias";

    /**
     * The requestCode used when we decide not to use this activity, but instead launch
     * another activity in our place. When that activity finishes, we set it's result
     * as our result and then finish.
     */
    private static final int PROXY_ACTIVITY_REQUEST_CODE = 5;

    @TargetApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private static final String LAUNCH_PERMISSION_SETTINGS =
            Manifest.permission.LAUNCH_PERMISSION_SETTINGS;

    @TargetApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private static final String APP_PERMISSIONS_SETTINGS = Settings.ACTION_APP_PERMISSIONS_SETTINGS;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (DeviceUtils.isAuto(this)) {
            // Automotive relies on a different theme. Apply before calling super so that
            // fragments are restored properly on configuration changes.
            setTheme(R.style.CarSettings);
        }
        super.onCreate(savedInstanceState);

        // If this is not a phone or a watch (which uses the Navigation component), and there
        // is a previous instance, re-use its Fragment instead of making a new one.
        if ((DeviceUtils.isTelevision(this) || DeviceUtils.isAuto(this))
                && savedInstanceState != null) {
            return;
        }

        boolean provisioned = Settings.Global.getInt(
                getContentResolver(), Settings.Global.DEVICE_PROVISIONED, 0) != 0;
        boolean completed = Settings.Secure.getInt(
                getContentResolver(), Settings.Secure.USER_SETUP_COMPLETE, 0) != 0;
        if (!provisioned || !completed) {
            Log.e(LOG_TAG, "Device setup incomplete. device provisioned=" + provisioned
                    + ", user setup complete=" + completed);
            finishAfterTransition();
            return;
        }

        android.app.Fragment fragment = null;
        Fragment androidXFragment = null;
        String action = getIntent().getAction();

        getWindow().addSystemFlags(SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS);

        long sessionId = getIntent().getLongExtra(Constants.EXTRA_SESSION_ID, INVALID_SESSION_ID);
        while (sessionId == INVALID_SESSION_ID) {
            sessionId = new Random().nextLong();
        }

        int autoRevokeAction =
                APP_PERMISSION_GROUPS_FRAGMENT_AUTO_REVOKE_ACTION__ACTION__OPENED_FOR_AUTO_REVOKE;
        int openFromIntentAction =
                APP_PERMISSION_GROUPS_FRAGMENT_AUTO_REVOKE_ACTION__ACTION__OPENED_FROM_INTENT;

        ComponentName component = getIntent().getComponent();
        if (component != null
                && Objects.equals(component.getClassName(), ALIAS_ACTIVITY_NAME)
                && !Objects.equals(action, APP_PERMISSIONS_SETTINGS)) {
            Log.w(LOG_TAG, ALIAS_ACTIVITY_NAME + " can only be launched with "
                            + APP_PERMISSIONS_SETTINGS);
            finishAfterTransition();
            return;
        }

        String permissionName;
        switch (action) {
            case Intent.ACTION_MANAGE_PERMISSIONS:
                Bundle arguments = new Bundle();
                arguments.putLong(EXTRA_SESSION_ID, sessionId);
                if (DeviceUtils.isAuto(this)) {
                    androidXFragment = AutoManageStandardPermissionsFragment.newInstance();
                    androidXFragment.setArguments(arguments);
                } else if (DeviceUtils.isTelevision(this)) {
                    androidXFragment =
                            com.android.permissioncontroller.permission.ui.television
                                    .ManagePermissionsFragment.newInstance();
                } else {
                    setContentView(R.layout.nav_host_fragment);
                    Navigation.findNavController(this, R.id.nav_host_fragment).setGraph(
                            R.navigation.nav_graph, arguments);
                    return;

                }
                break;

            case Intent.ACTION_REVIEW_PERMISSION_USAGE: {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    finishAfterTransition();
                    return;
                }

                PermissionControllerStatsLog.write(PERMISSION_USAGE_FRAGMENT_INTERACTION, sessionId,
                        PERMISSION_USAGE_FRAGMENT_INTERACTION__ACTION__OPEN);
                if (DeviceUtils.isAuto(this)) {
                    androidXFragment = new AutoPermissionUsageFragment();
                } else if (DeviceUtils.isWear(this)) {
                    androidXFragment = WearPermissionUsageFragment.newInstance(sessionId);
                } else {
                    androidXFragment = PermissionUsageWrapperFragment.newInstance(
                            Long.MAX_VALUE, sessionId);
                }
            } break;

            case Intent.ACTION_REVIEW_PERMISSION_HISTORY: {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    finishAfterTransition();
                    return;
                }

                String groupName = getIntent()
                        .getStringExtra(Intent.EXTRA_PERMISSION_GROUP_NAME);
                boolean showSystem = getIntent()
                        .getBooleanExtra(EXTRA_SHOW_SYSTEM, false);
                boolean show7Days = getIntent()
                        .getBooleanExtra(EXTRA_SHOW_7_DAYS, false);
                if (DeviceUtils.isAuto(this)) {
                    androidXFragment = AutoPermissionUsageDetailsFragment.Companion.newInstance(
                            groupName, showSystem, sessionId);
                } else if (DeviceUtils.isWear(this)) {
                    androidXFragment = WearPermissionUsageDetailsFragment
                            .newInstance(groupName, showSystem, show7Days);
                } else {
                    androidXFragment = PermissionDetailsWrapperFragment
                            .newInstance(groupName, Long.MAX_VALUE, showSystem, sessionId,
                                    show7Days);
                }
                break;
            }

            case Intent.ACTION_MANAGE_APP_PERMISSION: {
                if (DeviceUtils.isAuto(this) || DeviceUtils.isTelevision(this)) {
                    Intent compatIntent = new Intent(this, AppPermissionActivity.class);
                    compatIntent.putExtras(getIntent().getExtras());
                    startActivityForResult(compatIntent, PROXY_ACTIVITY_REQUEST_CODE);
                    return;
                }
                String packageName = getIntent().getStringExtra(Intent.EXTRA_PACKAGE_NAME);

                if (packageName == null) {
                    Log.i(LOG_TAG, "Missing mandatory argument EXTRA_PACKAGE_NAME");
                    finishAfterTransition();
                    return;
                }
                permissionName = getIntent().getStringExtra(Intent.EXTRA_PERMISSION_NAME);
                String groupName = getIntent().getStringExtra(Intent.EXTRA_PERMISSION_GROUP_NAME);

                if (permissionName == null && groupName == null) {
                    Log.i(LOG_TAG, "Missing mandatory argument EXTRA_PERMISSION_NAME or"
                            + "EXTRA_PERMISSION_GROUP_NAME");
                    finishAfterTransition();
                    return;
                }

                UserHandle userHandle = getIntent().getParcelableExtra(Intent.EXTRA_USER);
                String caller = getIntent().getStringExtra(EXTRA_CALLER_NAME);

                if (groupName == null) {
                    groupName = getGroupFromPermission(permissionName);
                }

                if (groupName != null
                        && groupName.equals(Manifest.permission_group.NOTIFICATIONS)) {
                    // Redirect notification group to notification settings
                    Utils.navigateToAppNotificationSettings(this, packageName, userHandle);
                    finishAfterTransition();
                    return;
                }

                Bundle args;
                if (DeviceUtils.isWear(this)) {
                    args = WearAppPermissionFragment.createArgs(packageName, permissionName,
                            groupName, userHandle, caller, sessionId, null);
                } else {
                    args = AppPermissionFragment.createArgs(packageName, permissionName,
                            groupName, userHandle, caller, sessionId, null);
                }
                setNavGraph(args, R.id.app_permission);
                return;
            }

            case Intent.ACTION_MANAGE_APP_PERMISSIONS:
            case APP_PERMISSIONS_SETTINGS: {
                if (!SdkLevel.isAtLeastV()
                        && Objects.equals(action, APP_PERMISSIONS_SETTINGS)) {
                    PermissionInfo permissionInfo;
                    try {
                        permissionInfo = getPackageManager()
                                .getPermissionInfo(LAUNCH_PERMISSION_SETTINGS, 0);
                    } catch (NameNotFoundException e) {
                        permissionInfo = null;
                    }
                    if (permissionInfo == null
                            || !Objects.equals(permissionInfo.packageName, Utils.OS_PKG)) {
                        Log.w(LOG_TAG, LAUNCH_PERMISSION_SETTINGS
                                + " must be defined by platform.");
                        finishAfterTransition();
                        return;
                    }
                }

                String packageName = getIntent().getStringExtra(Intent.EXTRA_PACKAGE_NAME);
                if (packageName == null) {
                    Log.i(LOG_TAG, "Missing mandatory argument EXTRA_PACKAGE_NAME");
                    finishAfterTransition();
                    return;
                }

                UserHandle userHandle = getIntent().getParcelableExtra(Intent.EXTRA_USER);
                if (userHandle == null) {
                    userHandle = Process.myUserHandle();
                }

                try {
                    int uid = getPackageManager().getApplicationInfoAsUser(packageName, 0,
                            userHandle).uid;
                    long settingsSessionId = getIntent().getLongExtra(
                            Intent.ACTION_AUTO_REVOKE_PERMISSIONS, INVALID_SESSION_ID);
                    if (settingsSessionId != INVALID_SESSION_ID) {
                        sessionId = settingsSessionId;
                        Log.i(LOG_TAG, "sessionId: " + sessionId
                                + " Reaching AppPermissionGroupsFragment for auto revoke. "
                                + "packageName: " + packageName + " uid " + uid);
                        PermissionControllerStatsLog.write(
                                APP_PERMISSION_GROUPS_FRAGMENT_AUTO_REVOKE_ACTION, sessionId, uid,
                                packageName, autoRevokeAction);
                    } else {
                        if (KotlinUtils.INSTANCE.isROrAutoRevokeEnabled(getApplication(),
                                packageName, userHandle)) {
                            Log.i(LOG_TAG, "sessionId: " + sessionId
                                    + " Reaching AppPermissionGroupsFragment from intent. "
                                    + "packageName " + packageName + " uid " + uid);
                            PermissionControllerStatsLog.write(
                                    APP_PERMISSION_GROUPS_FRAGMENT_AUTO_REVOKE_ACTION, sessionId,
                                    uid, packageName, openFromIntentAction);
                        }
                    }
                } catch (PackageManager.NameNotFoundException e) {
                    // Do no logging
                }

                final boolean allPermissions = getIntent().getBooleanExtra(
                        EXTRA_ALL_PERMISSIONS, false);


                if (DeviceUtils.isAuto(this)) {
                    if (allPermissions) {
                        androidXFragment = AutoAllAppPermissionsFragment.newInstance(packageName,
                                userHandle, sessionId);
                    } else {
                        androidXFragment = AutoAppPermissionsFragment.newInstance(packageName,
                                userHandle, sessionId, /* isSystemPermsScreen= */ true);
                    }
                } else if (DeviceUtils.isTelevision(this)) {
                    androidXFragment = com.android.permissioncontroller.permission.ui.television
                            .AppPermissionsFragment.newInstance(packageName, userHandle);
                } else {
                    Bundle args = AppPermissionGroupsFragment.createArgs(packageName, userHandle,
                            sessionId, /* isSystemPermsScreen= */  true);
                    setNavGraph(args, R.id.app_permission_groups);
                    return;
                }
                break;
            }

            case Intent.ACTION_MANAGE_PERMISSION_APPS: {
                permissionName = getIntent().getStringExtra(Intent.EXTRA_PERMISSION_NAME);

                String permissionGroupName = getIntent().getStringExtra(
                        Intent.EXTRA_PERMISSION_GROUP_NAME);

                if (permissionGroupName == null) {
                    try {
                        PermissionInfo permInfo = getPackageManager().getPermissionInfo(
                                permissionName, 0);
                        permissionGroupName = PermissionMapping.getGroupOfPermission(permInfo);
                    } catch (PackageManager.NameNotFoundException e) {
                        Log.i(LOG_TAG, "Permission " + permissionName + " does not exist");
                    }
                }

                if (permissionGroupName == null) {
                    permissionGroupName = permissionName;
                }

                if (permissionName == null && permissionGroupName == null) {
                    Log.i(LOG_TAG, "Missing mandatory argument EXTRA_PERMISSION_NAME or"
                            + "EXTRA_PERMISSION_GROUP_NAME");
                    finishAfterTransition();
                    return;
                }

                // Redirect notification group to notification settings
                if (permissionGroupName.equals(Manifest.permission_group.NOTIFICATIONS)) {
                    Utils.navigateToNotificationSettings(this);
                    finishAfterTransition();
                    return;
                }

                if (Utils.isHealthPermissionUiEnabled() && permissionGroupName
                                .equals(HEALTH_PERMISSION_GROUP)) {
                    Utils.navigateToHealthConnectSettings(this);
                    finishAfterTransition();
                    return;
                }

                if (DeviceUtils.isAuto(this)) {
                    androidXFragment =
                            AutoPermissionAppsFragment.newInstance(permissionGroupName, sessionId);
                } else if (DeviceUtils.isTelevision(this)) {
                    androidXFragment = com.android.permissioncontroller.permission.ui.television
                            .PermissionAppsFragment.newInstance(permissionGroupName);
                } else {
                    Bundle args = PermissionAppsFragment.createArgs(permissionGroupName, sessionId);
                    setNavGraph(args, R.id.permission_apps);
                    return;
                }
            } break;

            case Intent.ACTION_MANAGE_UNUSED_APPS :
                // fall through
            case ACTION_MANAGE_AUTO_REVOKE: {
                Log.i(LOG_TAG, "sessionId " + sessionId + " starting auto revoke fragment"
                        + " from notification");
                PermissionControllerStatsLog.write(AUTO_REVOKE_NOTIFICATION_CLICKED, sessionId);
                if (SdkLevel.isAtLeastT()) {
                    SafetyCenterManager safetyCenterManager =
                            getSystemService(SafetyCenterManager.class);
                    if (safetyCenterManager.isSafetyCenterEnabled()) {
                        SafetySourceData data = safetyCenterManager.getSafetySourceData(
                                UNUSED_APPS_SAFETY_CENTER_SOURCE_ID);
                        if (data != null && !data.getIssues().isEmpty()) {
                            // Clear source data as user has reviewed their unused apps
                            HibernationPolicyKt.setUnusedAppsReviewNeeded(this, false);
                            HibernationPolicyKt.rescanAndPushDataToSafetyCenter(this, sessionId,
                                    new SafetyEvent.Builder(
                                            SafetyEvent.SAFETY_EVENT_TYPE_SOURCE_STATE_CHANGED)
                                            .build());
                            HibernationPolicyKt.cancelUnusedAppsNotification(this);
                        }
                    }
                }

                if (DeviceUtils.isAuto(this)) {
                    androidXFragment = AutoUnusedAppsFragment.newInstance();
                    androidXFragment.setArguments(UnusedAppsFragment.createArgs(sessionId));
                } else if (DeviceUtils.isTelevision(this)) {
                    androidXFragment = TvUnusedAppsFragment.newInstance();
                    androidXFragment.setArguments(UnusedAppsFragment.createArgs(sessionId));
                } else if (DeviceUtils.isWear(this)) {
                    setNavGraph(WearUnusedAppsFragment.createArgs(sessionId), R.id.auto_revoke);
                    return;
                } else {
                    setNavGraph(UnusedAppsFragment.createArgs(sessionId), R.id.auto_revoke);
                    return;
                }

                break;
            }
            case PermissionManager.ACTION_REVIEW_PERMISSION_DECISIONS: {

                UserHandle userHandle = getIntent().getParcelableExtra(Intent.EXTRA_USER);
                if (userHandle == null) {
                    userHandle = Process.myUserHandle();
                }
                if (DeviceUtils.isAuto(this)) {
                    String source = getIntent().getStringExtra(
                            AutoReviewPermissionDecisionsFragment.EXTRA_SOURCE);
                    androidXFragment = AutoReviewPermissionDecisionsFragment.Companion
                            .newInstance(sessionId, userHandle, source);
                } else {
                    Log.e(LOG_TAG, "ACTION_REVIEW_PERMISSION_DECISIONS is not "
                            + "supported on this device type");
                    finishAfterTransition();
                    return;
                }
            } break;

            case Intent.ACTION_REVIEW_APP_DATA_SHARING_UPDATES: {
                if (KotlinUtils.INSTANCE.isSafetyLabelChangeNotificationsEnabled(this)) {
                    setNavGraph(AppDataSharingUpdatesFragment.Companion.createArgs(sessionId),
                            R.id.app_data_sharing_updates);
                } else {
                    finishAfterTransition();
                    return;
                }
            } break;

            default: {
                Log.w(LOG_TAG, "Unrecognized action " + action);
                finishAfterTransition();
                return;
            }
        }

        if (fragment != null) {
            getFragmentManager().beginTransaction().replace(android.R.id.content, fragment)
                    .commit();
        } else if (androidXFragment != null) {
            getSupportFragmentManager().beginTransaction().replace(android.R.id.content,
                    androidXFragment).commit();
        }
    }

    private String getGroupFromPermission(String permissionName) {
        try {
            PermissionInfo permInfo = getPackageManager().getPermissionInfo(
                    permissionName, 0);
            return PermissionMapping.getGroupOfPermission(permInfo);
        } catch (PackageManager.NameNotFoundException e) {
            Log.i(LOG_TAG, "Permission " + permissionName + " does not exist");
        }
        return null;
    }

    private void setNavGraph(Bundle args, int startDestination) {
        setContentView(R.layout.nav_host_fragment);
        NavHostFragment navHost = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        NavInflater inflater = navHost.getNavController().getNavInflater();
        NavGraph graph = inflater.inflate(R.navigation.nav_graph);
        graph.setStartDestination(startDestination);
        navHost.getNavController().setGraph(graph, args);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // in automotive mode, there's no system wide back button, so need to add that
        if (DeviceUtils.isAuto(this)) {
            switch (item.getItemId()) {
                case android.R.id.home:
                    onBackPressed();
                    finishAfterTransition();
                    return true;
                default:
                    return super.onOptionsItemSelected(item);
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PROXY_ACTIVITY_REQUEST_CODE) {
            setResult(resultCode, data);
            finishAfterTransition();
        }
    }

}
