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
package com.android.car.settings.network;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.net.NetworkTemplate;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import androidx.preference.Preference;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;
import com.android.car.settings.datausage.DataUsageUtils;

/**
 * Defines the shared getters and setters used by {@link PreferenceController} implementations
 * related to mobile network fragment.
 *
 * @param <V> the upper bound on the type of {@link Preference} on which the controller
 *            expects to operate.
 */
public abstract class NetworkBasePreferenceController<V extends Preference> extends
        PreferenceController<V> {

    private int mSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    private TelephonyManager mTelephonyManager;
    private NetworkTemplate mNetworkTemplate;

    public NetworkBasePreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    /** Gets the SubId. */
    public int getSubId() {
        return mSubId;
    }

    /** Gets the {@link TelephonyManager}. */
    public TelephonyManager getTelephonyManager() {
        return mTelephonyManager;
    }

    /** Gets the {@link NetworkTemplate}. */
    public NetworkTemplate getNetworkTemplate() {
        return mNetworkTemplate;
    }

    /** Sets the SubId, {@link TelephonyManager}, and {@link NetworkTemplate}. */
    public void setFields(int subId) {
        mSubId = subId;
        mTelephonyManager = TelephonyManager.from(getContext()).createForSubscriptionId(mSubId);
        mNetworkTemplate = DataUsageUtils.getMobileNetworkTemplate(mTelephonyManager, subId);
    }
}
