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

import static android.app.Activity.RESULT_OK;
import static android.os.Process.myUserHandle;

import static com.android.car.settings.enterprise.EnterpriseUtils.getAdminWithinPackage;
import static com.android.car.settings.enterprise.EnterpriseUtils.getDeviceAdminInfo;

import android.app.Activity;
import android.app.admin.DeviceAdminInfo;
import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.VisibleForTesting;
import androidx.preference.PreferenceScreen;

import com.android.car.settings.R;
import com.android.car.settings.common.Logger;
import com.android.car.settings.common.PreferenceController;
import com.android.car.settings.common.SettingsFragment;
import com.android.car.ui.toolbar.MenuItem;
import com.android.car.ui.toolbar.ToolbarController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A screen that shows details about a device administrator.
 */
public final class DeviceAdminAddFragment extends SettingsFragment {

    private static final Logger LOG = new Logger(DeviceAdminAddFragment.class);

    @VisibleForTesting
    static final int UNINSTALL_DEVICE_ADMIN_REQUEST_CODE = 12;

    private CharSequence mAppName;
    private DevicePolicyManager mDpm;
    private String mPackageToUninstall;
    private ComponentName mAdminComponentToUninstall;
    private boolean mIsActive;
    private MenuItem mUninstallButton;

    @VisibleForTesting
    void setDevicePolicyManager(DevicePolicyManager dpm) {
        mDpm = dpm;
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.device_admin_add;
    }

    @Override
    public List<MenuItem> getToolbarMenuItems() {
        return Collections.singletonList(mUninstallButton);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        // Split in 2 as it would be hard to mock requireActivity();
        onAttach(context, requireActivity());
    }

    @VisibleForTesting
    void onAttach(Context context, Activity activity) {
        setDevicePolicyManager(context.getSystemService(DevicePolicyManager.class));
        Intent intent = activity.getIntent();
        if (intent == null) {
            LOG.e("no intent on " + activity);
            activity.finish();
            return;
        }

        ComponentName admin = (ComponentName)
                intent.getParcelableExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN);
        LOG.d("Admin using " + DevicePolicyManager.EXTRA_DEVICE_ADMIN + ": " + admin);
        if (admin == null) {
            String adminPackage = intent
                    .getStringExtra(DeviceAdminAddActivity.EXTRA_DEVICE_ADMIN_PACKAGE_NAME);
            LOG.d("Admin package using " + DeviceAdminAddActivity.EXTRA_DEVICE_ADMIN_PACKAGE_NAME
                    + ": " + adminPackage);
            if (adminPackage == null) {
                LOG.w("Finishing " + activity + " as its intent doesn't have "
                        +  DevicePolicyManager.EXTRA_DEVICE_ADMIN + " or "
                        + DeviceAdminAddActivity.EXTRA_DEVICE_ADMIN_PACKAGE_NAME);
                activity.finish();
                return;
            }
            admin = getAdminWithinPackage(context, adminPackage);
            if (admin == null) {
                LOG.w("Finishing " + activity + " as there is no active admin for " + adminPackage);
                activity.finish();
                return;
            }
            // DeviceAdminAddActivity.EXTRA_DEVICE_ADMIN_PACKAGE_NAME is set only when DeviceAdmin
            // is called via Apps -> Uninstall for an active device admin. Set the package name to
            // uninstall, which will enable and show the "Deactivate & uninstall" button.
            setPackageToUninstall(adminPackage, admin);
        } else {
            // When activating, make sure the given component name is actually a valid device admin.
            // No need to check this when deactivating, because it is safe to deactivate an active
            // invalid device admin.
            if (!isValidAdmin(context, admin)) {
                LOG.w("Request to add invalid device admin: " + admin.flattenToShortString());
                activity.finish();
                return;
            }
        }

        // TODO(b/202342351): both this method and isValidAdmin() call PM to get the ActivityInfo;
        // they should be refactored so it's called just onces; similarly, isValidAdmin()
        // also create a DeviceAdminInfo
        DeviceAdminInfo deviceAdminInfo = getDeviceAdminInfo(context, admin);
        LOG.d("Admin: " + admin + " DeviceAdminInfo: " + deviceAdminInfo);

        if (deviceAdminInfo == null) {
            LOG.w("Finishing " + activity + " as it could not get DeviceAdminInfo for "
                    + admin.flattenToShortString());
            activity.finish();
            return;
        }

        // This admin already exists, and we have two options at this point:
        // 1. If new policy bits are set, show the user the new list.
        // 2. If nothing has changed, simply return "OK" immediately.
        if (isActionAddDeviceAdminActivity(activity)) {
            boolean refreshing = false;
            if (mDpm.isAdminActive(admin)) {
                if (mDpm.isRemovingAdmin(admin, myUserHandle().getIdentifier())) {
                    LOG.w("Requested admin is already being removed: " + admin);
                    activity.finish();
                    return;
                }
                ArrayList<DeviceAdminInfo.PolicyInfo> policies = deviceAdminInfo.getUsedPolicies();
                for (int i = 0, size = policies.size(); i < size; i++) {
                    DeviceAdminInfo.PolicyInfo pi = policies.get(i);
                    if (!mDpm.hasGrantedPolicy(admin, pi.ident)) {
                        refreshing = true;
                        break;
                    }
                }
                LOG.i("Try to add device admin for " + admin + ", refreshing=" + refreshing);
                if (!refreshing) {
                    // Nothing changed (or policies were removed) - return immediately
                    activity.setResult(Activity.RESULT_OK);
                    activity.finish();
                    return;
                }
                // Update the active admin with the refreshed policies.
                mDpm.setActiveAdmin(admin, refreshing);
            }
        }

        mAppName = deviceAdminInfo.loadLabel(context.getPackageManager());

        boolean showUninstallButton =
                (mPackageToUninstall != null) && (mAdminComponentToUninstall != null);
        setUninstallButton(context, showUninstallButton);

        ((DeviceAdminAddHeaderPreferenceController) use(
                DeviceAdminAddHeaderPreferenceController.class,
                R.string.pk_device_admin_add_header).setDeviceAdmin(deviceAdminInfo))
                        .setActivationListener((value) -> onActivation(value));
        ((DeviceAdminAddExplanationPreferenceController) use(
                DeviceAdminAddExplanationPreferenceController.class,
                R.string.pk_device_admin_add_explanation).setDeviceAdmin(deviceAdminInfo))
                        .setExplanation(intent
                                .getCharSequenceExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION));
        use(DeviceAdminAddWarningPreferenceController.class,
                R.string.pk_device_admin_add_warning).setDeviceAdmin(deviceAdminInfo);
        use(DeviceAdminAddPoliciesPreferenceController.class,
                R.string.pk_device_admin_add_policies).setDeviceAdmin(deviceAdminInfo);
        use(DeviceAdminAddSupportPreferenceController.class,
                R.string.pk_device_admin_add_support).setDeviceAdmin(deviceAdminInfo);
    }

    private void onActivation(boolean value) {
        Activity activity = requireActivity();
        if (!isActionAddDeviceAdminActivity(activity)) {
            return;
        }

        int result = value ? Activity.RESULT_OK : Activity.RESULT_CANCELED;
        LOG.d("Setting " + activity + " result to " + result);
        activity.setResult(result);
    }

    @VisibleForTesting
    void setPackageToUninstall(String packageName, ComponentName componentName) {
        mPackageToUninstall = packageName;
        mAdminComponentToUninstall = componentName;
    }

    @VisibleForTesting
    void setUninstallButton(Context context, boolean showButton) {
        mUninstallButton = new MenuItem.Builder(context)
                .setTitle(R.string.deactivate_and_uninstall_device_admin)
                .setEnabled(showButton)
                .setVisible(showButton)
                .setOnClickListener(i -> startUninstall())
                .build();
    }

    @VisibleForTesting
    void startUninstall() {
        mIsActive = mDpm.isAdminActive(mAdminComponentToUninstall);
        if (mIsActive) {
            LOG.i("Deactivating device admin: " + mAdminComponentToUninstall);
            mDpm.removeActiveAdmin(mAdminComponentToUninstall);
        }
        LOG.i("Uninstalling package: " + mPackageToUninstall);
        Uri packageUri = Uri.parse("package:" + mPackageToUninstall);
        Intent uninstallIntent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE, packageUri);
        uninstallIntent.putExtra(Intent.EXTRA_RETURN_RESULT, true);
        startActivityForResult(uninstallIntent, UNINSTALL_DEVICE_ADMIN_REQUEST_CODE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Call super method to handle callback.
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != UNINSTALL_DEVICE_ADMIN_REQUEST_CODE) {
            return;
        }
        if (resultCode == RESULT_OK) {
            Activity activity = requireActivity();
            // On successful uninstalling, sets the results and finish the activity.
            activity.setResult(RESULT_OK);
            activity.finish();
        } else {
            if (mIsActive) {
                // Set active admin back when uninstalling was failed/canceled.
                mDpm.setActiveAdmin(mAdminComponentToUninstall, /* refreshing= */ false);
            }
            LOG.e("Uninstall failed with result " + resultCode);
        }
    }

    @Override
    public void setPreferenceScreen(PreferenceScreen preferenceScreen) {
        super.setPreferenceScreen(preferenceScreen);

        // Split for testing, to avoid calling super.setPreferenceScreen() in tests.
        setPreferenceScreenTitle(preferenceScreen);
    }

    @VisibleForTesting
    void setPreferenceScreenTitle(PreferenceScreen preferenceScreen) {
        if (!TextUtils.isEmpty(mAppName)) {
            preferenceScreen.setTitle(mAppName);
        }
    }

    @Override
    protected void setupToolbar(ToolbarController toolbar) {
        super.setupToolbar(toolbar);

        // Must split in 2, otherwise tests would need to mock what's needed by super.setupToolbar()
        setToolbarTitle(toolbar);
    }

    @VisibleForTesting
    void setToolbarTitle(ToolbarController toolbar) {
        if (isActionAddDeviceAdminActivity(requireActivity())) {
            toolbar.setTitle(R.string.add_device_admin_msg);
        }
    }

    private boolean isActionAddDeviceAdminActivity(Activity activity) {
        Intent intent = activity.getIntent();
        String action = intent == null ? null : intent.getAction();
        return DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN.equals(action);
    }

    // Must override so it can be spied (it's the exact same signature and modifier access, but it
    // works now because the test class is in the same package).
    @Override
    protected <T extends PreferenceController> T use(Class<T> clazz, int preferenceKeyResId) {
        return super.use(clazz, preferenceKeyResId);
    }

    private boolean isValidAdmin(Context context, ComponentName who) {
        PackageManager pm = context.getPackageManager();
        ActivityInfo ai;
        try {
            ai = pm.getReceiverInfo(who, PackageManager.GET_META_DATA);
        } catch (PackageManager.NameNotFoundException e) {
            LOG.w("Unable to retrieve device policy " + who, e);
            return false;
        }

        if (mDpm.isAdminActive(who)) {
            return true;
        }
        List<ResolveInfo> avail = pm.queryBroadcastReceivers(
                new Intent(DeviceAdminReceiver.ACTION_DEVICE_ADMIN_ENABLED),
                PackageManager.GET_DISABLED_UNTIL_USED_COMPONENTS);
        int count = avail == null ? 0 : avail.size();
        boolean found = false;
        for (int i = 0; i < count; i++) {
            ResolveInfo ri = avail.get(i);
            if (ai.packageName.equals(ri.activityInfo.packageName)
                    && ai.name.equals(ri.activityInfo.name)) {
                try {
                    // We didn't retrieve the meta data for all possible matches, so
                    // need to use the activity info of this specific one that was retrieved.
                    ri.activityInfo = ai;
                    new DeviceAdminInfo(context, ri);
                    found = true;
                } catch (Exception e) {
                    LOG.w("Bad " + ri.activityInfo, e);
                }
                break;
            }
        }
        if (!found) {
            LOG.d("didn't find enabled admin receiver for " + who.flattenToShortString());
        }
        return found;
    }
}
