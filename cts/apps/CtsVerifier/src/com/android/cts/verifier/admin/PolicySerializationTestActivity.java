/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.cts.verifier.admin;

import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;
import com.android.cts.verifier.managedprovisioning.DeviceAdminTestReceiver;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Test that checks that device policies are properly saved and loaded across reboots. The user
 * clicks a button to generate a random policy and is then asked to reboot the device. When
 * returning to the test, the activity checks that the device manager is reporting the values
 * it set before the user rebooted the device.
 */
public final class PolicySerializationTestActivity extends PassFailButtons.ListActivity {

    private static final String TAG = PolicySerializationTestActivity.class.getSimpleName();

    /**
     * Whether or not to load the expected policy from the preferences and check against
     * what the {@link DevicePolicyManager} reports.
     */
    private static final String LOAD_EXPECTED_POLICY_PREFERENCE = "load-expected-policy";

    private static final int ADD_DEVICE_ADMIN_REQUEST_CODE = 1;

    private DevicePolicyManager mDevicePolicyManager;
    private ComponentName mAdmin;

    private List<PolicyItem<?>> mPolicyItems = new ArrayList<PolicyItem<?>>();
    private PolicyAdapter mAdapter;

    private View mGeneratePolicyButton;
    private View mApplyPolicyButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.da_policy_main);
        setInfoResources(R.string.da_policy_serialization_test,
                R.string.da_policy_serialization_info, -1);
        setPassFailButtonClickListeners();

        mDevicePolicyManager = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        mAdmin = DeviceAdminTestReceiver.getReceiverComponentName();

        mGeneratePolicyButton = findViewById(R.id.generate_policy_button);
        mGeneratePolicyButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                generateRandomPolicy();
                updateWidgets();
            }
        });

        mApplyPolicyButton = findViewById(R.id.apply_policy_button);
        mApplyPolicyButton.setOnClickListener((v) -> applyPolicy());

        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_SECURE_LOCK_SCREEN)) {
            mPolicyItems.add(new MaximumFailedPasswordsForWipePolicy(this));
        }

        mPolicyItems.add(new MaximumTimeToLockPolicy(this));

        Log.d(TAG, "onCreate(): " + mPolicyItems.size() + " policy items: " + mPolicyItems);

        mAdapter = new PolicyAdapter(this);
        setListAdapter(mAdapter);

        loadPolicy();
        updateWidgets();
    }

    private void loadPolicy() {
        mAdapter.clear();
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        if (prefs.getBoolean(LOAD_EXPECTED_POLICY_PREFERENCE, false)) {
            for (PolicyItem<?> item : mPolicyItems) {
                item.loadExpectedValue(prefs);
                item.loadActualValue(mDevicePolicyManager, mAdmin);
                Log.d(TAG, "loading " + item);
                mAdapter.add(item);
            }
        } else {
            Log.d(TAG, "loadPolicy(): " + LOAD_EXPECTED_POLICY_PREFERENCE + " is false");
        }
    }

    private void generateRandomPolicy() {
        Random random = new Random();
        mAdapter.clear();
        for (PolicyItem<?> item : mPolicyItems) {
            Log.d(TAG, "Adding " + item);
            item.setRandomExpectedValue(random);
            item.resetActualValue();
            mAdapter.add(item);
        }

        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.clear();
        Log.d(TAG, "setting LOAD_EXPECTED_POLICY_PREFERENCE to false");
        editor.putBoolean(LOAD_EXPECTED_POLICY_PREFERENCE, false);
        editor.apply();

        Toast.makeText(this, R.string.da_random_policy, Toast.LENGTH_SHORT).show();
    }

    private void applyPolicy() {
        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                DeviceAdminTestReceiver.getReceiverComponentName());
        Log.d(TAG, "Starting " + intent);
        startActivityForResult(intent, ADD_DEVICE_ADMIN_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult(): req=" + requestCode + ", resp=" + resultCode);

        switch (requestCode) {
            case ADD_DEVICE_ADMIN_REQUEST_CODE:
                handleAddDeviceAdminResult(resultCode, data);
                break;
        }
    }

    private void handleAddDeviceAdminResult(int resultCode, Intent data) {
        if (resultCode != RESULT_OK) {
            Log.w(TAG, "handleAddDeviceAdminResult(): invalid result: " + resultCode);
            return;
        }
        ComponentName admin = DeviceAdminTestReceiver.getReceiverComponentName();
        for (PolicyItem<?> item : mPolicyItems) {
            item.applyExpectedValue(mDevicePolicyManager, admin);
        }

        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.clear();
        Log.d(TAG, "setting LOAD_EXPECTED_POLICY_PREFERENCE to true");
        editor.putBoolean(LOAD_EXPECTED_POLICY_PREFERENCE, true);
        for (PolicyItem<?> item : mPolicyItems) {
            item.saveExpectedValue(editor);
        }
        editor.apply();
        showRebootDialog();
    }

    private void showRebootDialog() {
        new AlertDialog.Builder(this)
            .setIcon(android.R.drawable.ic_dialog_info)
            .setTitle(R.string.da_policy_serialization_test)
            .setMessage(R.string.da_policy_reboot)
            .setPositiveButton(android.R.string.ok, null)
            .show();
    }

    private void updateWidgets() {
        mApplyPolicyButton.setEnabled(!mAdapter.isEmpty());

        // All items need to have been serialized properly for the pass button to activate.
        boolean enablePass = !mAdapter.isEmpty();
        int numItems = mAdapter.getCount();
        for (int i = 0; i < numItems; i++) {
            PolicyItem<?> item = mAdapter.getItem(i);
            if (!item.matchesExpectedValue()) {
                enablePass = false;
            }
        }
        getPassButton().setEnabled(enablePass);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        PolicyItem<?> item = mAdapter.getItem(position);
        new AlertDialog.Builder(this)
            .setIcon(android.R.drawable.ic_dialog_info)
            .setTitle(item.getDisplayName())
            .setMessage(getString(R.string.da_policy_info,
                    item.getDisplayExpectedValue(),
                    item.getDisplayActualValue()))
            .setPositiveButton(android.R.string.ok, null)
            .show();
    }

    private static final class PolicyAdapter extends ArrayAdapter<PolicyItem<?>> {

        public PolicyAdapter(Context context) {
            super(context, android.R.layout.simple_list_item_1);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView view = (TextView) super.getView(position, convertView, parent);

            PolicyItem<?> item = getItem(position);
            int backgroundResource = 0;
            int iconResource = 0;
            if (item.getExpectedValue() != null && item.getActualValue() != null) {
                if (item.matchesExpectedValue()) {
                    backgroundResource = R.drawable.test_pass_gradient;
                    iconResource = R.drawable.fs_good;
                } else {
                    backgroundResource = R.drawable.test_fail_gradient;
                    iconResource = R.drawable.fs_error;
                }
            }
            view.setBackgroundResource(backgroundResource);
            view.setPadding(10, 0, 10, 0);
            view.setCompoundDrawablePadding(10);
            view.setCompoundDrawablesWithIntrinsicBounds(0, 0, iconResource, 0);

            return view;
        }
    }

    private interface PolicyItem<T> {

        void setRandomExpectedValue(Random random);

        void applyExpectedValue(DevicePolicyManager deviceManager, ComponentName admin);

        void loadExpectedValue(SharedPreferences prefs);

        void saveExpectedValue(SharedPreferences.Editor editor);

        void resetActualValue();

        void loadActualValue(DevicePolicyManager deviceManager, ComponentName admin);

        String getDisplayName();

        T getExpectedValue();

        String getDisplayExpectedValue();

        T getActualValue();

        String getDisplayActualValue();

        boolean matchesExpectedValue();
    }

    private abstract static class BasePolicyItem<T> implements PolicyItem<T> {
        private String mDisplayName;
        private T mExpectedValue;
        private T mActualValue;

        BasePolicyItem(Context context, int nameResId) {
            mDisplayName = context.getString(nameResId);
        }

        @Override
        public final void setRandomExpectedValue(Random random) {
            mExpectedValue = getRandomExpectedValue(random);
        }

        protected abstract T getRandomExpectedValue(Random random);

        @Override
        public final void loadExpectedValue(SharedPreferences prefs) {
            mExpectedValue = getPreferencesValue(prefs);
            Log.d(TAG, "loaded expected value for " + mDisplayName + ": " + mExpectedValue);
        }

        protected abstract T getPreferencesValue(SharedPreferences prefs);

        @Override
        public final void loadActualValue(DevicePolicyManager deviceManager, ComponentName admin) {
            mActualValue = getDeviceManagerValue(deviceManager, admin);
        }

        protected abstract T getDeviceManagerValue(DevicePolicyManager deviceManager,
                ComponentName admin);

        @Override
        public final void resetActualValue() {
            Log.d(TAG, "resetting " + mDisplayName);
            mActualValue = null;
        }

        @Override
        public final String getDisplayName() {
            return mDisplayName;
        }

        @Override
        public final T getExpectedValue() {
            return mExpectedValue;
        }

        @Override
        public final String getDisplayExpectedValue() {
            return mExpectedValue != null ? getDisplayValue(mExpectedValue) : "";
        }

        @Override
        public final T getActualValue() {
            return mActualValue;
        }

        @Override
        public final String getDisplayActualValue() {
            return mActualValue != null ? getDisplayValue(mActualValue) : "";
        }

        protected String getDisplayValue(T value) {
            return "" + value;
        }

        @Override
        public final boolean matchesExpectedValue() {
            return mExpectedValue != null && mExpectedValue.equals(mActualValue);
        }

        @Override
        public String toString() {
            return getDisplayName();
        }
    }

    private abstract static class IntegerPolicyItem extends BasePolicyItem<Integer> {

        private String mPreferenceKey;

        IntegerPolicyItem(Context context, int nameResId, String preferenceKey) {
            super(context, nameResId);
            mPreferenceKey = preferenceKey;
        }

        @Override
        protected final Integer getPreferencesValue(SharedPreferences prefs) {
            int value = prefs.getInt(mPreferenceKey, -1);
            Log.d(TAG, "loaded pref for " + getDisplayName() + ": " + mPreferenceKey + "=" + value);
            return value;
        }

        @Override
        public final void saveExpectedValue(Editor editor) {
            int value = getExpectedValue();
            Log.d(TAG, "saving pref for " + getDisplayName() + ": " + mPreferenceKey + "=" + value);
            editor.putInt(mPreferenceKey, value);
        }
    }

    private abstract static class LongPolicyItem extends BasePolicyItem<Long> {

        private String mPreferenceKey;

        LongPolicyItem(Context context, int nameResId, String preferenceKey) {
            super(context, nameResId);
            mPreferenceKey = preferenceKey;
        }

        @Override
        protected final Long getPreferencesValue(SharedPreferences prefs) {
            long value = prefs.getLong(mPreferenceKey, -1);
            Log.d(TAG, "loaded pref for " + getDisplayName() + ": " + mPreferenceKey + "=" + value);
            return value;
        }

        @Override
        public final void saveExpectedValue(Editor editor) {
            long value = getExpectedValue();
            Log.d(TAG, "saving pref for " + getDisplayName() + ": " + mPreferenceKey + "=" + value);
            editor.putLong(mPreferenceKey, value);
        }
    }

    private static final class MaximumFailedPasswordsForWipePolicy extends IntegerPolicyItem {

        MaximumFailedPasswordsForWipePolicy(Context context) {
            super(context, R.string.da_maximum_failed_passwords_for_wipe,
                    "maximum-failed-passwords-for-wipe");
        }

        @Override
        protected Integer getRandomExpectedValue(Random random) {
            return random.nextInt(50);
        }

        @Override
        public void applyExpectedValue(DevicePolicyManager deviceManager, ComponentName admin) {
            deviceManager.setMaximumFailedPasswordsForWipe(admin, getExpectedValue());
        }

        @Override
        protected Integer getDeviceManagerValue(DevicePolicyManager deviceManager,
                ComponentName admin) {
            return deviceManager.getMaximumFailedPasswordsForWipe(admin);
        }
    }

    private static final class MaximumTimeToLockPolicy extends LongPolicyItem {

        MaximumTimeToLockPolicy(Context context) {
            super(context, R.string.da_maximum_time_to_lock, "maximum-time-to-lock");
        }

        @Override
        protected Long getRandomExpectedValue(Random random) {
            return (long)(1000 + random.nextInt(60 * 60 * 1000));
        }

        @Override
        public void applyExpectedValue(DevicePolicyManager deviceManager, ComponentName admin) {
            deviceManager.setMaximumTimeToLock(admin, getExpectedValue());
        }

        @Override
        protected Long getDeviceManagerValue(DevicePolicyManager deviceManager,
                ComponentName admin) {
            return deviceManager.getMaximumTimeToLock(admin);
        }
    }
}
