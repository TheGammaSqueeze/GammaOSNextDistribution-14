/*
 * Copyright (C) 2019 The Android Open Source Project
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
import android.net.NetworkTemplate;
import android.os.Bundle;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import androidx.annotation.VisibleForTesting;
import androidx.annotation.XmlRes;
import androidx.loader.app.LoaderManager;

import com.android.car.settings.R;
import com.android.car.settings.common.Logger;
import com.android.car.settings.common.SettingsFragment;
import com.android.settingslib.net.DataUsageController;
import com.android.settingslib.net.NetworkCycleChartData;

import java.util.Map;

/**
 * Screen to display list of applications using the data.
 */
public class AppDataUsageFragment extends SettingsFragment implements
        DataUsageCyclePreferenceController.DataCyclePickedListener<NetworkCycleChartData> {

    private static final Logger LOG = new Logger(AppDataUsageFragment.class);

    private static final String ARG_NETWORK_SUB_ID = "network_sub_id";
    /** Value to represent that the subscription id hasn't been computed yet. */
    private static final int SUB_ID_NULL = Integer.MIN_VALUE;

    private AppsNetworkStatsManager mAppsNetworkStatsManager;
    private DataUsageCycleManager mDataUsageCycleManager;
    private NetworkTemplate mNetworkTemplate;
    private DataUsageController mDataUsageController;

    private Bundle mBundle;
    private LoaderManager mLoaderManager;
    private AppDataUsageTotalPreferenceController mAppDataUsageTotalPreferenceController;

    /**
     * Creates a new instance of the {@link AppDataUsageFragment}, which shows settings related to
     * the given {@code subId}.
     */
    public static AppDataUsageFragment newInstance(int subId) {
        AppDataUsageFragment fragment = new AppDataUsageFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_NETWORK_SUB_ID, subId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    @XmlRes
    protected int getPreferenceScreenResId() {
        return R.xml.app_data_usage_fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        TelephonyManager telephonyManager = context.getSystemService(TelephonyManager.class);
        int subId = getArguments() != null
                ? getArguments().getInt(ARG_NETWORK_SUB_ID, SUB_ID_NULL) : SUB_ID_NULL;
        if (subId == SUB_ID_NULL) {
            LOG.d("Cannot get the subscription id from arguments. Switching to default "
                    + "subscription Id: " + subId);
            SubscriptionManager subscriptionManager = context.getSystemService(
                    SubscriptionManager.class);
            subId = DataUsageUtils.getDefaultSubscriptionId(subscriptionManager);
        }
        mNetworkTemplate = DataUsageUtils.getMobileNetworkTemplate(telephonyManager, subId);
        mDataUsageController = new DataUsageController(context);
        mAppsNetworkStatsManager = new AppsNetworkStatsManager(getContext());
        mAppsNetworkStatsManager.registerListener(
                use(AppDataUsagePreferenceController.class, R.string.pk_app_data_usage_detail));
        mDataUsageCycleManager = new DataUsageCycleManager(getContext(), mNetworkTemplate);
        mDataUsageCycleManager.registerListener(use(DataUsageCyclePreferenceController.class,
                R.string.pk_data_usage_usage_history));
        use(DataUsageCyclePreferenceController.class,
                R.string.pk_data_usage_usage_history)
                .setDataCyclePickedListener(this)
                .setDataUsageInfo(mDataUsageController.getDataUsageInfo(mNetworkTemplate));

        long usage = mDataUsageController.getDataUsageInfo(mNetworkTemplate).usageLevel;
        mAppDataUsageTotalPreferenceController =
                use(AppDataUsageTotalPreferenceController.class, R.string.pk_data_usage_all_apps);
        mAppDataUsageTotalPreferenceController.setDataUsage(usage);

        use(AppDataUsagePreferenceController.class, R.string.pk_app_data_usage_detail)
                .setNetworkTemplate(mNetworkTemplate);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBundle = getBundleForNetworkStats();

        mLoaderManager = LoaderManager.getInstance(this);
        mAppsNetworkStatsManager.startLoading(mLoaderManager, mBundle);
        mDataUsageCycleManager.startLoading(mLoaderManager);
    }

    private Bundle getBundleForNetworkStats() {
        DataUsageController.DataUsageInfo dataUsageInfo =
                mDataUsageController.getDataUsageInfo(mNetworkTemplate);
        long start = dataUsageInfo.cycleStart;
        long end = dataUsageInfo.cycleEnd;

        return SummaryForAllUidLoader.buildArgs(mNetworkTemplate, start, end);
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    Bundle getBundle() {
        return mBundle;
    }

    @Override
    public void onDataCyclePicked(String cycle, Map<CharSequence, NetworkCycleChartData> usages) {
        mAppDataUsageTotalPreferenceController.setDataUsage(usages.get(cycle).getTotalUsage());

        mBundle = SummaryForAllUidLoader.buildArgs(
                mNetworkTemplate,
                usages.get(cycle).getStartTime(),
                usages.get(cycle).getEndTime());
        mAppsNetworkStatsManager.startLoading(mLoaderManager, mBundle);
    }
}
