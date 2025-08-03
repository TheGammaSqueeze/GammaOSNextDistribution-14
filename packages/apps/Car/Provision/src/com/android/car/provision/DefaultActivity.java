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

package com.android.car.provision;

import static android.app.Activity.RESULT_CANCELED;
import static android.app.Activity.RESULT_FIRST_USER;
import static android.app.Activity.RESULT_OK;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_TRIGGER;
import static android.app.admin.DevicePolicyManager.PROVISIONING_TRIGGER_QR_CODE;
import static android.car.settings.CarSettings.Secure.KEY_ENABLE_INITIAL_NOTICE_SCREEN_TO_USER;
import static android.car.settings.CarSettings.Secure.KEY_SETUP_WIZARD_IN_PROGRESS;

import android.Manifest.permission;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.util.Log;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import com.android.car.setupwizardlib.util.CarDrivingStateMonitor;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Reference implementeation for a Car SetupWizard.
 *
 * <p>Features:
 *
 * <ul>
 *   <li>Shows UI where user can confirm setup.
 *   <li>Listen to UX restriction events, so it exits setup when the car moves.
 *   <li>Add option to setup managed-provisioning mode.
 *   <li>Sets car-specific properties.
 * </ul>
 *
 * <p>By default, it doesn't show the UI, unless the {@code persist.dev.car_provision.show_ui}
 * property is set to {@code true}. For example, you can change it by running something like:
 <pre><code>
     adb root
     adb shell setprop persist.dev.car_provision.show_ui true && \
     adb shell pm enable --user cur com.android.car.provision/.DefaultActivity &&\
     adb shell settings put secure --user cur user_setup_complete 0 && \
     adb shell settings put secure --user 0 user_setup_complete 0 &&\
     adb shell settings put global device_provisioned 0 &&\
     adb shell rm -f /data/system/device_policies_version &&\
     adb shell rm -f /data/system/device_policies.xml &&\
     adb shell rm -f /data/system/device_owner_2.xml ;\
     adb shell rm -f /data/system/users/`adb shell am get-current-user`/profile_owner.xml
     adb shell stop && adb shell start
  <code></pre>
 */
public final class DefaultActivity extends Activity {

    static final String TAG = "CarProvision";

    // TODO(b/170333009): copied from ManagedProvisioning app, as they're hidden;
    private static final String PROVISION_FINALIZATION_INSIDE_SUW =
            "android.app.action.PROVISION_FINALIZATION_INSIDE_SUW";
    private static final int RESULT_CODE_PROFILE_OWNER_SET = 122;
    private static final int RESULT_CODE_DEVICE_OWNER_SET = 123;


    private static final int REQUEST_CODE_STEP1 = 42;
    private static final int REQUEST_CODE_STEP2_PO = 43;
    private static final int REQUEST_CODE_STEP2_DO = 44;

    private static final int NOTIFICATION_ID = 108;
    private static final String IMPORTANCE_DEFAULT_ID = "importance_default";

    private static final List<DpcInfo> sSupportedDpcApps = new ArrayList<>(2);

    private static final String TEST_DPC_NAME = "TestDPC (downloadable)";
    private static final String TEST_DPC_PACKAGE = "com.afwsamples.testdpc";
    private static final String TEST_DPC_LEGACY_ACTIVITY = TEST_DPC_PACKAGE
            + ".SetupManagementLaunchActivity";
    private static final String TEST_DPC_RECEIVER = TEST_DPC_PACKAGE
            + ".DeviceAdminReceiver";
    private static final String LOCAL_TEST_DPC_NAME = "TestDPC (local only)";

    private static final String SHOW_UI_SYSTEM_PROPERTY = "persist.dev.car_provision.show_ui";

    static {
        DpcInfo testDpc = new DpcInfo(TEST_DPC_NAME,
                TEST_DPC_PACKAGE,
                TEST_DPC_LEGACY_ACTIVITY,
                TEST_DPC_RECEIVER,
                "gJD2YwtOiWJHkSMkkIfLRlj-quNqG1fb6v100QmzM9w=",
                "https://testdpc-latest-apk.appspot.com/preview");
        // Locally-built version of the TestDPC
        DpcInfo localTestDpc = new DpcInfo(LOCAL_TEST_DPC_NAME,
                TEST_DPC_PACKAGE,
                TEST_DPC_LEGACY_ACTIVITY,
                TEST_DPC_RECEIVER,
                /* checkSum= */ null,
                /* downloadUrl = */ null);
        sSupportedDpcApps.add(testDpc);
        sSupportedDpcApps.add(localTestDpc);
    }

    private CarDrivingStateMonitor mCarDrivingStateMonitor;

    private TextView mErrorsTextView;
    private Button mFinishSetupButton;
    private Button mFactoryResetButton;
    private Spinner mDpcAppsSpinner;
    private Button mLegacyProvisioningWorkflowButton;
    private Button mProvisioningWorkflowButton;

    private final BroadcastReceiver mDrivingStateExitReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive(): " + intent);
            exitSetup();
        }
    };

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        int userId = getUserId();
        Log.i(TAG, "onCreate() for user " + userId + " Intent: " + getIntent());

        if (userId == UserHandle.USER_SYSTEM && UserManager.isHeadlessSystemUserMode()) {
            // System user will be provisioned together with the first non-system user
            Log.i(TAG, "onCreate(): skipping setup on headless system user");
            disableSelfAndFinish();
            return;
        }

        if (!showUi()) {
            Log.w(TAG, "onCreate(): skipping UI because " + SHOW_UI_SYSTEM_PROPERTY
                    + " was not set to true");
            finishSetup();
            return;
        }

        DevicePolicyManager dpm = getSystemService(DevicePolicyManager.class);
        if (dpm.isDeviceManaged()) {
            Log.i(TAG, "onCreate(): skipping UI on managed device");
            finishSetup();
            return;
        }

        setCarSetupInProgress(true);
        setContentView(R.layout.default_activity);

        mErrorsTextView = findViewById(R.id.error_message);
        mFinishSetupButton = findViewById(R.id.finish_setup);
        mFactoryResetButton = findViewById(R.id.factory_reset);
        mDpcAppsSpinner = findViewById(R.id.dpc_apps);
        mLegacyProvisioningWorkflowButton = findViewById(R.id.legacy_provision_workflow);
        mProvisioningWorkflowButton = findViewById(R.id.provision_workflow);

        mLegacyProvisioningWorkflowButton
                .setOnClickListener((v) -> launchLegacyProvisioningWorkflow());
        mProvisioningWorkflowButton.setOnClickListener((v) -> launchProvisioningWorkflow());
        mFinishSetupButton.setOnClickListener((v) -> finishSetup());
        mFactoryResetButton.setOnClickListener((v) -> factoryReset());

        hideSystemUi();
        updateUi();
        setManagedProvisioning(dpm);
        startMonitor();
    }

    private boolean showUi() {
        boolean result = false;
        try {
            result = SystemProperties.getBoolean(SHOW_UI_SYSTEM_PROPERTY, false);
        } catch (Exception e) {
            Log.w(TAG, "error getting property " + SHOW_UI_SYSTEM_PROPERTY);
        }
        return result;
    }

    private void startMonitor() {
        Log.d(TAG, "startMonitor()");
        registerReceiver(mDrivingStateExitReceiver,
                new IntentFilter(CarDrivingStateMonitor.EXIT_BROADCAST_ACTION),
                permission.DISPATCH_PROVISIONING_MESSAGE, /* scheduler= */ null,
                Context.RECEIVER_EXPORTED);

        mCarDrivingStateMonitor = CarDrivingStateMonitor.get(this);
        mCarDrivingStateMonitor.startMonitor();
    }

    @Override
    public void finish() {
        Log.i(TAG, "finish() for user " + getUserId());

        stopMonitor();

        super.finish();
    };

    @Override
    public void dump(String prefix, FileDescriptor fd, PrintWriter pw, String[] args) {
        if (args == null || args.length == 0) {
            showDpcs(pw);
            showHelp(pw);
            return;
        }

        if (args[0].equals("--help")) {
            showHelp(pw);
            return;
        }

        addDpc(pw, args);
    };

    private void showDpcs(PrintWriter pw) {
        pw.printf("%d DPCs\n", sSupportedDpcApps.size());
        sSupportedDpcApps.forEach((dpc) -> pw.printf("\t%s\n", dpc));
    }

    private void showHelp(PrintWriter pw) {
        pw.println("\nTo add a new DPC, use: --name name --package-name package-name"
                + "--receiver-name receiver-name [--legacy-activity-name legacy-activity-name] "
                + "[--checksum checksum] [--download-url download-url]");
    }

    private void addDpc(PrintWriter pw, String[] args) {
        String name = null;
        String packageName = null;
        String legacyActivityName = null;
        String receiverName = null;
        String checkSum = null;
        String downloadUrl = null;

        for (int i = 0; i < args.length; i++) {
            try {
                switch (args[i]) {
                    case "--name":
                        name = args[++i];
                        break;
                    case "--package-name":
                        packageName = args[++i];
                        break;
                    case "--legacy-activity-name":
                        legacyActivityName = args[++i];
                        break;
                    case "--receiver-name":
                        receiverName = args[++i];
                        break;
                    case "--checksum":
                        checkSum = args[++i];
                        break;
                    case "--download-url":
                        downloadUrl = args[++i];
                        break;
                    default:
                        pw.printf("Invalid option at index %d: %s\n", i, args[i]);
                        return;
                }
            } catch (Exception e) {
                // most likely a missing arg...
                pw.printf("Error handing arg %d: %s\n", i, e);
                return;
            }
        }

        DpcInfo dpc = new DpcInfo(name, packageName, legacyActivityName, receiverName, checkSum,
                downloadUrl);
        Log.i(TAG, "Adding new DPC from dump(): " + dpc);
        sSupportedDpcApps.add(dpc);
        pw.printf("Added new DPC: %s\n", dpc);

        updateUi();
    }

    private void stopMonitor() {
        Log.d(TAG, "stopMonitor()");

        if (mCarDrivingStateMonitor == null) {
            // Happens when device is managed (and startMonitor() is skipped)
            Log.d(TAG, "Already stopped (or never stopped)");
            return;
        }

        if (mDrivingStateExitReceiver != null) {
            unregisterReceiver(mDrivingStateExitReceiver);
        }

        mCarDrivingStateMonitor.stopMonitor();
        mCarDrivingStateMonitor = null;
    }

    private void hideSystemUi() {
        WindowInsetsController insetsController = getWindow().getDecorView()
                .getWindowInsetsController();
        if (insetsController == null) {
            Log.w(TAG, "No insets controller");
            return;
        }
        Log.d(TAG, "Hiding the system UI bars");
        insetsController.hide(WindowInsets.Type.navigationBars());
    }

    private void updateUi() {
        String[] appNames = new String[sSupportedDpcApps.size()];
        for (int i = 0; i < sSupportedDpcApps.size(); i++) {
            appNames[i] = sSupportedDpcApps.get(i).name;
        }
        mDpcAppsSpinner.setAdapter(new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, appNames));
        mDpcAppsSpinner.setSelection(appNames.length - 1);
    }

    private void setManagedProvisioning(DevicePolicyManager dpm) {
        if (!getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_DEVICE_ADMIN)) {
            Log.i(TAG, "Disabling provisioning buttons because device does not have the "
                    + PackageManager.FEATURE_DEVICE_ADMIN + " feature");
            return;
        }
        if (!dpm.isProvisioningAllowed(DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE)) {
            Log.w(TAG, "Disabling provisioning buttons because device cannot be provisioned - "
                    + "it can only be set on first boot");
            return;
        }

        mProvisioningWorkflowButton.setEnabled(true);
        mLegacyProvisioningWorkflowButton.setEnabled(true);
    }

    private boolean checkDpcAppExists(String dpcApp) {
        if (!checkAppExists(dpcApp, UserHandle.USER_SYSTEM)) return false;
        if (!checkAppExists(dpcApp, getUserId())) return false;
        return true;
    }

    private boolean checkAppExists(String app, int userId) {
        Log.d(TAG, "Checking if " + app + " exits for user " + userId);
        try {
            PackageInfo info = getPackageManager().getPackageInfoAsUser(app, /* flags= */ 0,
                    userId);
            if (info == null) {
                Log.i(TAG, "No app " + app + " for user " + userId);
                return false;
            }
            Log.d(TAG, "Found it: " + info);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Error checking if " + app + " exists for user " + userId, e);
            return false;
        }
    }

    private void finishSetup() {
        Log.i(TAG, "finishing setup for user " + getUserId());
        provisionUserAndDevice();
        disableSelfAndFinish();
    }

    private void factoryReset() {
        new AlertDialog.Builder(this).setMessage(R.string.factory_reset_warning)
            .setPositiveButton(android.R.string.ok, (d, w)->sendFactoryResetIntent())
            .show();
    }

    private void sendFactoryResetIntent() {
        provisionUserAndDevice();

        Intent intent = new Intent(Intent.ACTION_FACTORY_RESET);
        intent.setPackage("android");
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        intent.putExtra(Intent.EXTRA_REASON, "Requested by user on SUW");

        Log.i(TAG, "factory resetting device with intent " + intent);
        sendBroadcast(intent);

        disableSelfAndFinish();
    }

    private void provisionUserAndDevice() {
        Log.d(TAG, "setting Settings properties");
        // Add a persistent setting to allow other apps to know the device has been provisioned.
        if (!isDeviceProvisioned()) {
            Settings.Global.putInt(getContentResolver(), Settings.Global.DEVICE_PROVISIONED, 1);
        }

        maybeMarkSystemUserSetupComplete();
        Log.v(TAG, "Marking USER_SETUP_COMPLETE for user " + getUserId());
        markUserSetupComplete(this);

        // Set car-specific properties
        setCarSetupInProgress(false);
        Settings.Secure.putInt(getContentResolver(), KEY_ENABLE_INITIAL_NOTICE_SCREEN_TO_USER, 0);
    }

    private boolean isDeviceProvisioned() {
        try {
            return Settings.Global.getInt(getContentResolver(),
                    Settings.Global.DEVICE_PROVISIONED) == 1;
        } catch (SettingNotFoundException e) {
            Log.wtf(TAG, "DEVICE_PROVISIONED is not found.");
            return false;
        }
    }

    private boolean isUserSetupComplete(Context context) {
        return Settings.Secure.getInt(context.getContentResolver(),
                Settings.Secure.USER_SETUP_COMPLETE, /* default= */ 0) == 1;
    }

    private void maybeMarkSystemUserSetupComplete() {
        Context systemUserContext = getApplicationContext().createContextAsUser(
                UserHandle.SYSTEM, /* flags= */ 0);
        if (!isUserSetupComplete(systemUserContext) && getUserId() != UserHandle.USER_SYSTEM
                && UserManager.isHeadlessSystemUserMode()) {
            Log.v(TAG, "Marking USER_SETUP_COMPLETE for system user");
            markUserSetupComplete(systemUserContext);
        }
    }

    private void setCarSetupInProgress(boolean inProgress) {
        Settings.Secure.putInt(getContentResolver(), KEY_SETUP_WIZARD_IN_PROGRESS,
                inProgress ? 1 : 0);
    }

    private void markUserSetupComplete(Context context) {
        Settings.Secure.putInt(context.getContentResolver(),
                Settings.Secure.USER_SETUP_COMPLETE, 1);
    }

    private void exitSetup() {
        Log.d(TAG, "exiting setup early for user " + getUserId());
        provisionUserAndDevice();
        notifySetupExited();
        disableSelfAndFinish();
    }

    private void notifySetupExited() {
        Log.d(TAG, "Sending exited setup notification");

        NotificationManager notificationMgr = getSystemService(NotificationManager.class);
        notificationMgr.createNotificationChannel(new NotificationChannel(
                IMPORTANCE_DEFAULT_ID, "Importance Default",
                NotificationManager.IMPORTANCE_DEFAULT));
        Notification notification = new Notification
                .Builder(this, IMPORTANCE_DEFAULT_ID)
                .setContentTitle(getString(R.string.exited_setup_title))
                .setContentText(getString(R.string.exited_setup_content))
                .setCategory(Notification.CATEGORY_CAR_INFORMATION)
                .setSmallIcon(R.drawable.car_ic_mode)
                .build();
        notificationMgr.notify(NOTIFICATION_ID, notification);
    }

    private DpcInfo getSelectedDpcInfo() {
        return sSupportedDpcApps.get(mDpcAppsSpinner.getSelectedItemPosition());
    }

    private void launchLegacyProvisioningWorkflow() {
        DpcInfo dpcInfo = getSelectedDpcInfo();
        if (!checkDpcAppExists(dpcInfo.packageName)) {
            showErrorMessage("Cannot provision device because " + dpcInfo.packageName
                    + " is not available.\n Make sure it's installed for both user 0 and user "
                    + getUserId());
            return;
        }

        Intent intent = new Intent();
        intent.setComponent(dpcInfo.getLegacyActivityComponentName());
        Log.i(TAG, "Provisioning device using LEGACY workflow while running as user "
                + getUserId() + ". DPC: " + dpcInfo + ". Intent: " + intent);
        startActivityForResult(intent, REQUEST_CODE_STEP1);
    }

    private void launchProvisioningWorkflow() {
        DpcInfo dpcInfo = getSelectedDpcInfo();

        Intent intent = new Intent(ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE);
        // TODO(b/170333009): add a UI with options for EXTRA_PROVISIONING_TRIGGER.
        intent.putExtra(EXTRA_PROVISIONING_TRIGGER, PROVISIONING_TRIGGER_QR_CODE);
        intent.putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
                dpcInfo.getAdminReceiverComponentName());
        if (dpcInfo.checkSum != null) {
            intent.putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM, dpcInfo.checkSum);
        }
        if (dpcInfo.downloadUrl != null) {
            intent.putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION,
                    dpcInfo.downloadUrl);
        }

        Log.i(TAG, "Provisioning device using NEW workflow while running as user "
                + getUserId() + ". DPC: " + dpcInfo + ". Intent: " + intent);

        startActivityForResult(intent, REQUEST_CODE_STEP1);
    }

    private void disableSelfAndFinish() {
        Log.d(TAG, "disableSelfAndFinish()");

        // Remove this activity from the package manager.
        PackageManager pm = getPackageManager();
        ComponentName name = new ComponentName(this, DefaultActivity.class);
        Log.i(TAG, "Disabling itself (" + name + ") for user " + getUserId());
        pm.setComponentEnabledSetting(name, PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);

        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "onActivityResult(): request=" + requestCode + ", result="
                + resultCodeToString(resultCode) + ", data=" + data);

        switch (requestCode) {
            case REQUEST_CODE_STEP1:
                onProvisioningStep1Result(resultCode);
                break;
            case REQUEST_CODE_STEP2_PO:
            case REQUEST_CODE_STEP2_DO:
                onProvisioningStep2Result(requestCode, resultCode);
                break;
            default:
                showErrorMessage("onActivityResult(): invalid request code " + requestCode);

        }
    }

    private void onProvisioningStep1Result(int resultCode) {
        int requestCodeStep2;
        switch (resultCode) {
            case RESULT_CODE_PROFILE_OWNER_SET:
                requestCodeStep2 = REQUEST_CODE_STEP2_PO;
                break;
            case RESULT_CODE_DEVICE_OWNER_SET:
                requestCodeStep2 = REQUEST_CODE_STEP2_DO;
                break;
            default:
                showErrorMessage("onProvisioningStep1Result(): invalid result code "
                        + resultCodeToString(resultCode)
                        + getManagedProvisioningFailureWarning());
                return;
        }
        Intent intent = new Intent(PROVISION_FINALIZATION_INSIDE_SUW)
                .addCategory(Intent.CATEGORY_DEFAULT);
        Log.i(TAG, "Finalizing DPC with " + intent);
        startActivityForResult(intent, requestCodeStep2);
    }

    private String getManagedProvisioningFailureWarning() {
        return "\n\n" + getString(R.string.provision_failure_message);
    }

    private void onProvisioningStep2Result(int requestCode, int resultCode) {
        boolean doMode = requestCode == REQUEST_CODE_STEP2_DO;
        if (resultCode != RESULT_OK) {
            StringBuilder message = new StringBuilder("onProvisioningStep2Result(): "
                    + "invalid result code ").append(resultCode);
            if (doMode) {
                message.append(getManagedProvisioningFailureWarning());
            }
            showErrorMessage(message.toString());
            return;
        }

        Log.i(TAG, (doMode ? "Device owner" : "Profile owner") + " mode provisioned!");
        finishSetup();
    }

    private static String resultCodeToString(int resultCode)  {
        StringBuilder result = new StringBuilder();
        switch (resultCode) {
            case RESULT_OK:
                result.append("RESULT_OK");
                break;
            case RESULT_CANCELED:
                result.append("RESULT_CANCELED");
                break;
            case RESULT_FIRST_USER:
                result.append("RESULT_FIRST_USER");
                break;
            case RESULT_CODE_PROFILE_OWNER_SET:
                result.append("RESULT_CODE_PROFILE_OWNER_SET");
                break;
            case RESULT_CODE_DEVICE_OWNER_SET:
                result.append("RESULT_CODE_DEVICE_OWNER_SET");
                break;
            default:
                result.append("UNKNOWN_CODE");
        }
        return result.append('(').append(resultCode).append(')').toString();
    }

    private void showErrorMessage(String message) {
        Log.e(TAG, "Error: " + message);
        mErrorsTextView.setText(message);
        findViewById(R.id.errors_container).setVisibility(View.VISIBLE);
    }
}
