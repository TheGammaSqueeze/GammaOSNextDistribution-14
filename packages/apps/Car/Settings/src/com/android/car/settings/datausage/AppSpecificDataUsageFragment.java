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

import androidx.annotation.XmlRes;
import androidx.loader.app.LoaderManager;

import com.android.car.settings.R;
import com.android.car.settings.common.Logger;
import com.android.car.settings.common.SettingsFragment;
import com.android.car.settings.common.UpdateSummaryPreferenceController;
import com.android.settingslib.AppItem;
import com.android.settingslib.net.DataUsageController;
import com.android.settingslib.net.NetworkCycleDataForUid;
import com.android.settingslib.net.UidDetail;
import com.android.settingslib.net.UidDetailProvider;

import java.util.Map;

/**
 * Screen to display detailed data usage information for a specific app.
 */
public class AppSpecificDataUsageFragment extends SettingsFragment implements
        AppSpecificDataUsageCyclePreferenceController.DataCyclePickedListener
                <NetworkCycleDataForUid>  {
    private static final Logger LOG = new Logger(AppSpecificDataUsageFragment.class);

    private static final String EXTRA_PACKAGE_NAME = "extra_app_item_key";

    private AppSpecificDataUsageManager mAppSpecificDataUsageManager;

    /** Creates an instance of this fragment, passing packageName as an argument. */
    public static AppSpecificDataUsageFragment getInstance(AppItem appItem,
            NetworkTemplate networkTemplate) {
        AppSpecificDataUsageFragment
                appSpecificDataUsageFragment = new AppSpecificDataUsageFragment();
        Bundle bundle = new Bundle();
        bundle.putObject(EXTRA_PACKAGE_NAME, appItem);
        bundle.putParcelable(NetworkPolicyManager.EXTRA_NETWORK_TEMPLATE, networkTemplate);
        appSpecificDataUsageFragment.setArguments(bundle);
        return appSpecificDataUsageFragment;
    }

    @Override
    @XmlRes
    protected int getPreferenceScreenResId() {
        return R.xml.app_specific_data_usage_fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        AppItem appItem = (AppItem) getArguments().get(EXTRA_PACKAGE_NAME);

        NetworkTemplate networkTemplate = getArguments().getParcelable(
                NetworkPolicyManager.EXTRA_NETWORK_TEMPLATE);
        if (networkTemplate == null) {
            TelephonyManager telephonyManager = context.getSystemService(TelephonyManager.class);
            SubscriptionManager subscriptionManager =
                    context.getSystemService(SubscriptionManager.class);
            networkTemplate = DataUsageUtils.getMobileNetworkTemplate(telephonyManager,
                    DataUsageUtils.getDefaultSubscriptionId(subscriptionManager));
        }

        mAppSpecificDataUsageManager = new AppSpecificDataUsageManager(context, networkTemplate,
                appItem.key);
        mAppSpecificDataUsageManager.registerListener(
                use(AppSpecificDataUsageCyclePreferenceController.class,
                        R.string.pk_app_specific_usage_cycle));

        UidDetailProvider uidDetailProvider = new UidDetailProvider(context);
        UidDetail uidDetail = uidDetailProvider.getUidDetail(appItem.key, true /* blocking */);
        use(AppSpecificDataUsageHeaderPreferenceController.class,
                R.string.pk_app_specific_usage_title)
                .setTitle(uidDetail.label.toString())
                .setIcon(uidDetail.icon);

        DataUsageController dataUsageController = new DataUsageController(context);
        use(AppSpecificDataUsageCyclePreferenceController.class,
                R.string.pk_app_specific_usage_cycle)
                .setDataCyclePickedListener(this)
                .setDataUsageInfo(dataUsageController.getDataUsageInfo(networkTemplate));
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LoaderManager loaderManager = LoaderManager.getInstance(this);
        mAppSpecificDataUsageManager.startLoading(loaderManager);
    }

    @Override
    public void onDataCyclePicked(String cycle, Map<CharSequence, NetworkCycleDataForUid> usages) {
        // TODO: (b/203824535) Default to generic "Calculating..." string after string freeze when
        //  not yet loaded.
        NetworkCycleDataForUid cycleData = usages.get(cycle);
        use(UpdateSummaryPreferenceController.class,
                R.string.pk_app_specific_usage_total)
                .setSummary(DataUsageUtils.bytesToIecUnits(getContext(), cycleData.getTotalUsage())
                        .toString());
        use(UpdateSummaryPreferenceController.class,
                R.string.pk_app_specific_usage_foreground)
                .setSummary(DataUsageUtils.bytesToIecUnits(getContext(),
                        cycleData.getForegroudUsage()).toString());
        use(UpdateSummaryPreferenceController.class,
                R.string.pk_app_specific_usage_background)
                .setSummary(DataUsageUtils.bytesToIecUnits(getContext(),
                        cycleData.getBackgroudUsage()).toString());
    }
}
