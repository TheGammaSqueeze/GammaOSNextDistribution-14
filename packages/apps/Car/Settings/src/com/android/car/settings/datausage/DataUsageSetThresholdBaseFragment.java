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

package com.android.car.settings.datausage;

import android.content.Context;
import android.net.NetworkPolicyManager;
import android.net.NetworkTemplate;
import android.os.Bundle;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.XmlRes;

import com.android.car.settings.R;
import com.android.car.settings.common.SettingsFragment;
import com.android.car.ui.toolbar.MenuItem;
import com.android.settingslib.NetworkPolicyEditor;

import java.util.Collections;
import java.util.List;

/** Screen used to pick the data usage warning threshold bytes. */
public abstract class DataUsageSetThresholdBaseFragment extends SettingsFragment {

    @VisibleForTesting
    static final long MIB_IN_BYTES = 1024 * 1024;
    @VisibleForTesting
    static final long GIB_IN_BYTES = MIB_IN_BYTES * 1024;
    @VisibleForTesting
    static final long MAX_DATA_LIMIT_BYTES = 50000 * GIB_IN_BYTES;
    @VisibleForTesting
    static final float MB_GB_SUFFIX_THRESHOLD = 1.5f;

    @VisibleForTesting
    DataUsageUnitPreferenceController mDataWarningUnitsPreferenceController;
    @VisibleForTesting
    DataUsageEditTextPreferenceController mDataUsageEditTextPreferenceController;
    @VisibleForTesting
    NetworkPolicyEditor mPolicyEditor;
    @VisibleForTesting
    NetworkTemplate mNetworkTemplate;

    private MenuItem mSaveButton;
    private TelephonyManager mTelephonyManager;
    private SubscriptionManager mSubscriptionManager;

    @Override
    @XmlRes
    protected int getPreferenceScreenResId() {
        return R.xml.data_usage_set_threshold_fragment;
    }

    @Override
    protected List<MenuItem> getToolbarMenuItems() {
        return Collections.singletonList(mSaveButton);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        if (mPolicyEditor == null) {
            mPolicyEditor = new NetworkPolicyEditor(NetworkPolicyManager.from(context));

            // Loads the current policies to the policy editor cache.
            mPolicyEditor.read();
        }
        mNetworkTemplate = getArguments().getParcelable(
                NetworkPolicyManager.EXTRA_NETWORK_TEMPLATE);
        if (mNetworkTemplate == null) {
            mTelephonyManager = context.getSystemService(TelephonyManager.class);
            mSubscriptionManager = context.getSystemService(SubscriptionManager.class);
            mNetworkTemplate = DataUsageUtils.getMobileNetworkTemplate(mTelephonyManager,
                    DataUsageUtils.getDefaultSubscriptionId(mSubscriptionManager));
        }

        mSaveButton = new MenuItem.Builder(context)
                .setTitle(R.string.data_usage_warning_save_title)
                .setOnClickListener(i -> {
                    onSaveClicked();
                })
                .build();

        mDataUsageEditTextPreferenceController = use(
                DataUsageEditTextPreferenceController.class,
                R.string.pk_data_usage_edit_text);
        mDataWarningUnitsPreferenceController = use(DataUsageUnitPreferenceController.class,
                R.string.pk_data_usage_radio_group);

        long currentWarningBytes = getInitialBytes();
        String bytesText;
        if (currentWarningBytes > MB_GB_SUFFIX_THRESHOLD * GIB_IN_BYTES) {
            mDataWarningUnitsPreferenceController.setDefaultSelection(/* isGb= */ true);
            bytesText = formatText(currentWarningBytes / (float) GIB_IN_BYTES);
        } else {
            mDataWarningUnitsPreferenceController.setDefaultSelection(/* isGb= */ false);
            bytesText = formatText(currentWarningBytes / (float) MIB_IN_BYTES);
        }

        mDataUsageEditTextPreferenceController.setDefaultText(bytesText);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getPreferenceScreen().setTitle(getTitleResId());
    }

    @VisibleForTesting
    void onSaveClicked() {
        onSave(getCurrentThreshold());
        goBack();
    }

    @VisibleForTesting
    long getCurrentThreshold() {
        String bytesString = mDataUsageEditTextPreferenceController.getText();
        if (bytesString.isEmpty() || bytesString.equals(".")) {
            bytesString = "0";
        }

        long bytes = (long) (Float.parseFloat(bytesString)
                * (mDataWarningUnitsPreferenceController.isGbSelected()
                ? GIB_IN_BYTES : MIB_IN_BYTES));

        // To fix the overflow problem.
        return Math.min(MAX_DATA_LIMIT_BYTES, bytes);
    }

    private String formatText(float v) {
        v = Math.round(v * 100) / 100f;
        return String.valueOf(v);
    }

    /**
     * Callback when save button is clicked
     *
     * @param threshold number of bytes set
     */
    @VisibleForTesting
    abstract void onSave(long threshold);

    /** Returns the screen title res id */
    @StringRes
    protected abstract int getTitleResId();

    /** Returns the initial bytes to be displayed */
    protected abstract long getInitialBytes();
}
