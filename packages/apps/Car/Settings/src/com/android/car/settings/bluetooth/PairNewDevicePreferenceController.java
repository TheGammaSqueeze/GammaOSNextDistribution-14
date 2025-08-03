/*
 * Copyright 2018 The Android Open Source Project
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

package com.android.car.settings.bluetooth;

import static android.os.UserManager.DISALLOW_BLUETOOTH;
import static android.os.UserManager.DISALLOW_CONFIG_BLUETOOTH;

import static com.android.car.settings.enterprise.ActionDisabledByAdminDialogFragment.DISABLED_BY_ADMIN_CONFIRM_DIALOG_TAG;
import static com.android.car.settings.enterprise.EnterpriseUtils.hasUserRestrictionByDpm;

import android.bluetooth.BluetoothAdapter;
import android.car.drivingstate.CarUxRestrictions;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.UserManager;

import androidx.lifecycle.LifecycleObserver;
import androidx.preference.Preference;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.Logger;
import com.android.car.settings.common.PreferenceController;
import com.android.car.settings.common.Settings;
import com.android.car.settings.enterprise.EnterpriseUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Controls a preference that, when clicked, launches the page for pairing new Bluetooth devices.
 * The associated preference for this controller should define the fragment attribute or an intent
 * to launch for the Bluetooth device pairing page. If the adapter is not enabled, a click will
 * enable Bluetooth. The summary message is updated to indicate this effect to the user.
 */
public class PairNewDevicePreferenceController extends PreferenceController<Preference> implements
        LifecycleObserver {

    private static final Logger LOG = new Logger(PairNewDevicePreferenceController.class);

    private final IntentFilter mIntentFilter = new IntentFilter(
            BluetoothAdapter.ACTION_STATE_CHANGED);
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshUi();
        }
    };
    private final UserManager mUserManager;

    public PairNewDevicePreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mUserManager = UserManager.get(context);
    }

    @Override
    protected Class<Preference> getPreferenceType() {
        return Preference.class;
    }

    @Override
    protected void onCreateInternal() {
        super.onCreateInternal();
        setClickableWhileDisabled(getPreference(), /* clickable= */ true, p -> {
            if (getAvailabilityStatus() == AVAILABLE_FOR_VIEWING) {
                showActionDisabledByAdminDialog();
            }
        });
    }

    private void showActionDisabledByAdminDialog() {
        getFragmentController().showDialog(
                EnterpriseUtils.getActionDisabledByAdminDialog(getContext(),
                        DISALLOW_CONFIG_BLUETOOTH),
                DISABLED_BY_ADMIN_CONFIRM_DIALOG_TAG);
    }

    @Override
    protected void checkInitialized() {
        if (getPreference().getIntent() == null && getPreference().getFragment() == null) {
            throw new IllegalStateException(
                    "Preference should declare fragment or intent for page to pair new devices");
        }
    }

    @Override
    protected int getDefaultAvailabilityStatus() {
        if (!getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) {
            return UNSUPPORTED_ON_DEVICE;
        }
        if (hasUserRestrictionByDpm(getContext(), DISALLOW_CONFIG_BLUETOOTH)) {
            return AVAILABLE_FOR_VIEWING;
        }
        return isUserRestricted() ? DISABLED_FOR_PROFILE : AVAILABLE;
    }

    private boolean isUserRestricted() {
        return mUserManager.hasUserRestriction(DISALLOW_BLUETOOTH)
                || mUserManager.hasUserRestriction(DISALLOW_CONFIG_BLUETOOTH);
    }

    @Override
    protected void onStartInternal() {
        getContext().registerReceiver(mReceiver, mIntentFilter);
    }

    @Override
    protected void onStopInternal() {
        getContext().unregisterReceiver(mReceiver);
    }

    @Override
    protected void updateState(Preference preference) {
        preference.setSummary(
                BluetoothAdapter.getDefaultAdapter().isEnabled() ? "" : getContext().getString(
                        R.string.bluetooth_pair_new_device_summary));
    }

    /**
     * Checks whether provided application object represents system application.
     */
    private boolean isSystemApp(ResolveInfo info) {
        return (info.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
    }

    @Override
    protected boolean handlePreferenceClicked(Preference preference) {
        // Enable the adapter if it is not on (user is notified via summary message).
        BluetoothAdapter.getDefaultAdapter().enable();

        Context context = getContext();

        if (!context.getResources().getBoolean(
                R.bool.config_use_custom_pair_device_flow)) {
            LOG.i("Custom pairing device flow is deactivated");
            return false;
        }

        Intent intent = getPreference().getIntent();
        if (intent == null) {
            LOG.e("Preference doesn't contain " + Settings.ACTION_PAIR_DEVICE_SETTINGS + " intent");
            return false;
        }

        List<ResolveInfo> infos = context.getPackageManager().queryIntentActivities(intent,
                PackageManager.MATCH_DEFAULT_ONLY);

        List<String> packages = infos.stream().filter(this::isSystemApp).map(
                ri -> ri.activityInfo.packageName).collect(
                Collectors.toUnmodifiableList());

        LOG.i("Found " + packages.size() + " system packages matching "
                + intent.getAction() + " intent action. Found packages are: " + packages);

        if (packages.size() > 1) {
            LOG.w("More than one package found satisfying " + intent.getAction()
                    + " intent action. Picking the first one.");
        }

        for (String packageName : packages) {
            LOG.i("Starting custom pair device activity identified by " + packageName + " package");

            intent.setPackage(packageName);
            context.startActivity(intent);
            return true; // new activity will handle pairing workflow
        }

        return false;
    }
}
