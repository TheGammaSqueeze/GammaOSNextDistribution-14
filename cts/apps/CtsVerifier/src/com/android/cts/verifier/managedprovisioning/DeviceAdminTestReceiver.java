/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.cts.verifier.managedprovisioning;

import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE;

import android.Manifest;
import android.app.Service;
import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.PersistableBundle;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.android.bedstead.dpmwrapper.DeviceOwnerHelper;
import com.android.bedstead.dpmwrapper.TestAppSystemServiceFactory;
import com.android.compatibility.common.util.enterprise.DeviceAdminReceiverUtils;
import com.android.cts.verifier.R;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Profile owner receiver for BYOD flow test.
 * Setup cross-profile intent filter after successful provisioning.
 */
public class DeviceAdminTestReceiver extends DeviceAdminReceiver {
    public static final String KEY_BUNDLE_WIPE_IMMEDIATELY = "wipe_immediately";
    private static final String TAG = "DeviceAdminTestReceiver";
    private static final String DEVICE_OWNER_PKG =
            "com.android.cts.verifier";
    private static final String ADMIN_RECEIVER_TEST_CLASS =
            DEVICE_OWNER_PKG + ".managedprovisioning.DeviceAdminTestReceiver";
    private static final ComponentName RECEIVER_COMPONENT_NAME = new ComponentName(
            DEVICE_OWNER_PKG, ADMIN_RECEIVER_TEST_CLASS);
    public static final String EXTRA_MANAGED_USER_TEST =
            "com.android.cts.verifier.managedprovisioning.extra.MANAGED_USER_TEST";
    public static final String EXTRA_LOGOUT_ON_START =
            "com.android.cts.verifier.managedprovisioning.extra.LOGOUT_ON_START";
    public static final String AFFILIATION_ID = "affiliationId";

    public static ComponentName getReceiverComponentName() {
        return RECEIVER_COMPONENT_NAME;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (DeviceAdminReceiverUtils.disableSelf(context, intent)) return;
        if (DeviceOwnerHelper.runManagerMethod(this, context, intent)) return;

        DevicePolicyManager dpm = getManager(context);
        String action = intent.getAction();
        Log.d(TAG, "onReceive(): user=" + UserHandle.myUserId() + ", action=" + action
                + ", EXTRA_USER=" + intent.getExtra(Intent.EXTRA_USER)
                + ", dpm=" + dpm);

        // Must set affiliation on headless system user, otherwise some operations in the current
        // user (which is PO) won't be allowed (like uininstalling a package)
        if (ACTION_DEVICE_ADMIN_ENABLED.equals(action) && UserManager.isHeadlessSystemUserMode()) {
            Set<String> ids = new HashSet<>(1);
            ids.add(DeviceAdminTestReceiver.AFFILIATION_ID);
            ComponentName admin = getWho(context);
            Log.i(TAG, "Setting affiliation ids to " + ids);
            dpm.setAffiliationIds(admin, ids);
            if (dpm.isProfileOwnerApp(context.getPackageName()) && !dpm.isEphemeralUser(admin)) {
                // For profile owner, also set the affiliation IDs for DO to make sure that PO is
                // affiliated. For system user, DO affiliation ids are already set above, so no need
                // to set them again.
                Log.i(TAG, "Also setting the affiliation ids for device owner");
                DevicePolicyManager doDpm = TestAppSystemServiceFactory.getDevicePolicyManager(
                        context, getClass(), /* forDeviceOwner= */ true);
                doDpm.setAffiliationIds(admin, ids);
            }
            Log.i(TAG, "Is affiliated: " + dpm.isAffiliatedUser());
        }

        super.onReceive(context, intent);
    }

    @Override
    public void onProfileProvisioningComplete(Context context, Intent intent) {
        Log.d(TAG, "Provisioning complete intent received");
        setupProfile(context);
        wipeIfNecessary(context, intent);
    }

    @Override
    public void onBugreportSharingDeclined(Context context, Intent intent) {
        Log.i(TAG, "Bugreport sharing declined");
        Utils.showBugreportNotification(context, context.getString(
                R.string.bugreport_sharing_declined), Utils.BUGREPORT_NOTIFICATION_ID);
    }

    @Override
    public void onBugreportShared(Context context, Intent intent, String bugreportFileHash) {
        Log.i(TAG, "Bugreport shared");
        Utils.showBugreportNotification(context, context.getString(
                R.string.bugreport_shared_successfully), Utils.BUGREPORT_NOTIFICATION_ID);
    }

    @Override
    public void onBugreportFailed(Context context, Intent intent, int failureCode) {
        Log.i(TAG, "Bugreport collection operation failed, code: " + failureCode);
        Utils.showBugreportNotification(context, context.getString(
                R.string.bugreport_failed_completing), Utils.BUGREPORT_NOTIFICATION_ID);
    }

    @Override
    public void onLockTaskModeEntering(Context context, Intent intent, String pkg) {
        Log.i(TAG, "Entering LockTask mode: " + pkg);
        LocalBroadcastManager.getInstance(context)
                .sendBroadcast(new Intent(LockTaskUiTestActivity.ACTION_LOCK_TASK_STARTED));
    }

    @Override
    public void onLockTaskModeExiting(Context context, Intent intent) {
        Log.i(TAG, "Exiting LockTask mode");
        LocalBroadcastManager.getInstance(context)
                .sendBroadcast(new Intent(LockTaskUiTestActivity.ACTION_LOCK_TASK_STOPPED));
    }

    @Override
    public void onEnabled(Context context, Intent intent) {
        int myUserId = UserHandle.myUserId();
        Log.i(TAG, "Device admin enabled on user " + myUserId);
        if (intent.getBooleanExtra(EXTRA_MANAGED_USER_TEST, false)) {
            DevicePolicyManager dpm = context.getSystemService(DevicePolicyManager.class);
            ComponentName admin = getReceiverComponentName();
            dpm.setAffiliationIds(admin,
                    Collections.singleton(DeviceAdminTestReceiver.AFFILIATION_ID));
            context.startActivity(
                    new Intent(context, ManagedUserPositiveTestActivity.class).setFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK));

            bindPrimaryUserService(context, iCrossUserService -> {
                try {
                    UserHandle userHandle = Process.myUserHandle();
                    Log.d(TAG, "calling switchUser(" + userHandle + ") from " + myUserId);
                    iCrossUserService.switchUser(userHandle);
                } catch (RemoteException re) {
                    Log.e(TAG, "Error when calling primary user", re);
                }
            });
        } else if (intent.getBooleanExtra(EXTRA_LOGOUT_ON_START, false)) {
            DevicePolicyManager dpm = context.getSystemService(DevicePolicyManager.class);
            ComponentName admin = getReceiverComponentName();
            dpm.setAffiliationIds(admin,
                    Collections.singleton(DeviceAdminTestReceiver.AFFILIATION_ID));
            dpm.logoutUser(admin);
        }
    }

    private void setupProfile(Context context) {
        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        dpm.setProfileEnabled(new ComponentName(context.getApplicationContext(), getClass()));

        // Setup cross-profile intent filter to allow communications between the two versions of CtsVerifier
        // Primary -> work direction
        IntentFilter filter = new IntentFilter();
        filter.addAction(ByodHelperActivity.ACTION_QUERY_PROFILE_OWNER);
        filter.addAction(ByodHelperActivity.ACTION_REMOVE_MANAGED_PROFILE);
        filter.addAction(ByodHelperActivity.ACTION_CHECK_DISK_ENCRYPTION);
        filter.addAction(ByodHelperActivity.ACTION_INSTALL_APK);
        filter.addAction(ByodHelperActivity.ACTION_INSTALL_APK_WORK_PROFILE_GLOBAL_RESTRICTION);
        filter.addAction(ByodHelperActivity.ACTION_INSTALL_APK_PRIMARY_PROFILE_GLOBAL_RESTRICTION);
        filter.addAction(ByodHelperActivity.ACTION_CHECK_INTENT_FILTERS);
        filter.addAction(ByodHelperActivity.ACTION_CAPTURE_AND_CHECK_IMAGE);
        filter.addAction(ByodHelperActivity.ACTION_CAPTURE_AND_CHECK_VIDEO_WITH_EXTRA_OUTPUT);
        filter.addAction(ByodHelperActivity.ACTION_CAPTURE_AND_CHECK_VIDEO_WITHOUT_EXTRA_OUTPUT);
        filter.addAction(ByodHelperActivity.ACTION_CAPTURE_AND_CHECK_AUDIO);
        filter.addAction(ByodHelperActivity.ACTION_KEYGUARD_DISABLED_FEATURES);
        filter.addAction(ByodHelperActivity.ACTION_LOCKNOW);
        filter.addAction(ByodHelperActivity.ACTION_TEST_CROSS_PROFILE_INTENTS_DIALOG);
        filter.addAction(ByodHelperActivity.ACTION_TEST_APP_LINKING_DIALOG);
        filter.addAction(ByodHelperActivity.ACTION_NOTIFICATION);
        filter.addAction(ByodHelperActivity.ACTION_NOTIFICATION_ON_LOCKSCREEN);
        filter.addAction(ByodHelperActivity.ACTION_CLEAR_NOTIFICATION);
        filter.addAction(ByodHelperActivity.ACTION_SET_USER_RESTRICTION);
        filter.addAction(ByodHelperActivity.ACTION_CLEAR_USER_RESTRICTION);
        filter.addAction(CrossProfileTestActivity.ACTION_CROSS_PROFILE_TO_WORK);
        filter.addAction(ScreenshotCaptureActivity.ACTION_CAPTURE_SCREENSHOT);
        filter.addAction(
                PermissionLockdownTestActivity.ACTION_MANAGED_PROFILE_CHECK_PERMISSION_LOCKDOWN);
        filter.addAction(AuthenticationBoundKeyTestActivity.ACTION_AUTH_BOUND_KEY_TEST);
        filter.addAction(VpnTestActivity.ACTION_VPN);
        filter.addAction(AlwaysOnVpnSettingsTestActivity.ACTION_ALWAYS_ON_VPN_SETTINGS_TEST);
        filter.addAction(RecentsRedactionActivity.ACTION_RECENTS);
        filter.addAction(ByodHelperActivity.ACTION_TEST_SELECT_WORK_CHALLENGE);
        filter.addAction(ByodHelperActivity.ACTION_TEST_PATTERN_WORK_CHALLENGE);
        filter.addAction(ByodHelperActivity.ACTION_LAUNCH_CONFIRM_WORK_CREDENTIALS);
        filter.addAction(ByodHelperActivity.ACTION_SET_ORGANIZATION_INFO);
        filter.addAction(ByodHelperActivity.ACTION_TEST_PARENT_PROFILE_PASSWORD);
        filter.addAction(SetSupportMessageActivity.ACTION_SET_SUPPORT_MSG);
        filter.addAction(KeyChainTestActivity.ACTION_KEYCHAIN);
        filter.addAction(CommandReceiverActivity.ACTION_EXECUTE_COMMAND);
        filter.addAction(WorkProfileWidgetActivity.ACTION_TEST_WORK_PROFILE_WIDGET);
        filter.addAction(
                CrossProfilePermissionControlActivity.ACTION_CROSS_PROFILE_PERMISSION_CONTROL);
        filter.addAction(LocationCheckerActivity.ACTION_CHECK_LOCATION_WORK);
        dpm.addCrossProfileIntentFilter(getWho(context), filter,
                DevicePolicyManager.FLAG_MANAGED_CAN_ACCESS_PARENT);

        // Work -> primary direction
        filter = new IntentFilter();
        filter.addAction(ByodHelperActivity.ACTION_PROFILE_OWNER_STATUS);
        filter.addAction(ByodHelperActivity.ACTION_DISK_ENCRYPTION_STATUS);
        filter.addAction(ByodHelperActivity.ACTION_INSTALL_APK_IN_PRIMARY);
        filter.addAction(ByodFlowTestActivity.ACTION_TEST_RESULT);
        filter.addAction(CrossProfileTestActivity.ACTION_CROSS_PROFILE_TO_PERSONAL);
        filter.addAction(ScreenshotTestActivity.ACTION_SCREENSHOT_TEST);
        filter.addAction(Intent.ACTION_SEND);
        dpm.addCrossProfileIntentFilter(getWho(context), filter,
                DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED);

        // Disable the work profile instance of this activity, because it is a helper activity for
        // the work -> primary direction.
        context.getPackageManager().setComponentEnabledSetting(
                new ComponentName(context, ByodPrimaryHelperActivity.class.getName()),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);

        // Disable the work profile instance of ByodFlowTestActivity
        context.getPackageManager().setComponentEnabledSetting(
                new ComponentName(context, ByodFlowTestActivity.class),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);

        dpm.setPermissionGrantState(getWho(context), context.getPackageName(),
                Manifest.permission.POST_NOTIFICATIONS,
                DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED);
    }

    private void wipeIfNecessary(Context context, Intent intent) {
        PersistableBundle bundle = intent.getParcelableExtra(
                EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE);
        if (bundle != null && bundle.getBoolean(KEY_BUNDLE_WIPE_IMMEDIATELY, false)) {
            getManager(context).wipeData(0);
        }
    }

    private void bindPrimaryUserService(Context context, Consumer<ICrossUserService> consumer) {
        DevicePolicyManager devicePolicyManager = context.getSystemService(
                DevicePolicyManager.class);
        List<UserHandle> adminUsers = devicePolicyManager.getBindDeviceAdminTargetUsers(
                getReceiverComponentName());
        Log.d(TAG, "bindPrimaryUserService(): admins=" + adminUsers);
        UserHandle primaryUser = adminUsers.get(0);

        Log.d(TAG, "Calling primary user: " + primaryUser);
        final ServiceConnection serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                Log.d(TAG, "onServiceConnected is called");
                consumer.accept(ICrossUserService.Stub.asInterface(service));
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                Log.d(TAG, "onServiceDisconnected is called");
            }
        };
        final Intent serviceIntent = new Intent(context, PrimaryUserService.class);
        devicePolicyManager.bindDeviceAdminServiceAsUser(getReceiverComponentName(), serviceIntent,
                serviceConnection, Context.BindServiceFlags.of(Context.BIND_AUTO_CREATE),
                primaryUser);
    }

    public static final class PrimaryUserService extends Service {
        private final ICrossUserService.Stub mBinder = new ICrossUserService.Stub() {
            public void switchUser(UserHandle userHandle) {
                Log.d(TAG, "switchUser: " + userHandle);
                getSystemService(DevicePolicyManager.class).switchUser(getReceiverComponentName(),
                        userHandle);
            }
        };

        @Override
        public IBinder onBind(Intent intent) {
            return mBinder;
        }
    }
}
