/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.services.telephony.domainselection;

import static android.telephony.AccessNetworkConstants.AccessNetworkType.NGRAN;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_EMERGENCY_OVER_IMS_SUPPORTED_3GPP_NETWORK_TYPES_INT_ARRAY;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PersistableBundle;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.ArrayMap;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.phone.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** Helper class to cache carrier configurations. */
public class CarrierConfigHelper extends Handler {
    private static final String TAG = "CarrierConfigHelper";
    private static final boolean DBG = (SystemProperties.getInt("ro.debuggable", 0) == 1);

    @VisibleForTesting
    public static final String KEY_VONR_EMERGENCY_SUPPORT = "vonr_emergency_support";

    private final Context mContext;
    private final CarrierConfigManager mConfigManager;
    private final TelephonyManager mTelephonyManager;
    private final ArrayMap<Integer, Boolean> mVoNrSupported = new ArrayMap<>();

    private final CarrierConfigManager.CarrierConfigChangeListener mCarrierConfigChangeListener =
            (slotIndex, subId, carrierId, specificCarrierId) -> onCarrierConfigurationChanged(
                    slotIndex, subId, carrierId);

    // For test purpose only
    private final SharedPreferences mSharedPreferences;

    private List<Integer> mIgnoreNrWhenSimRemoved = null;

    /**
     * Creates an instance.
     *
     * @param context The Context this is associated with.
     * @param looper The Looper to run the CarrierConfigHelper.
     */
    public CarrierConfigHelper(@NonNull Context context, @NonNull Looper looper) {
        this(context, looper, null);
    }

    /**
     * Creates an instance.
     *
     * @param context The Context this is associated with.
     * @param looper The Looper to run the CarrierConfigHelper.
     * @param sharedPreferences The SharedPreferences instance.
     */
    @VisibleForTesting
    public CarrierConfigHelper(@NonNull Context context, @NonNull Looper looper,
            @Nullable SharedPreferences sharedPreferences) {
        super(looper);

        mContext = context;
        mTelephonyManager = context.getSystemService(TelephonyManager.class);
        mConfigManager = context.getSystemService(CarrierConfigManager.class);
        mConfigManager.registerCarrierConfigChangeListener(this::post,
                mCarrierConfigChangeListener);
        mSharedPreferences = sharedPreferences;

        readFromSharedPreference();
        readResourceConfiguration();
    }

    /**
     * Returns whether VoNR emergency was supported with the last valid subscription.
     *
     * @param slotIndex The SIM slot index.
     * @return true if VoNR emergency was supported with the last valid subscription.
     *         Otherwise, false.
     */
    public boolean isVoNrEmergencySupported(int slotIndex) {
        return mVoNrSupported.get(Integer.valueOf(slotIndex));
    }

    @Override
    public void handleMessage(Message msg) {
        switch(msg.what) {
            default:
                super.handleMessage(msg);
                break;
        }
    }

    private void readFromSharedPreference() {
        mVoNrSupported.clear();
        int modemCount = mTelephonyManager.getActiveModemCount();
        SharedPreferences sp = (mSharedPreferences != null) ? mSharedPreferences
                : PreferenceManager.getDefaultSharedPreferences(mContext);
        for (int i = 0; i < modemCount; i++) {
            Boolean savedConfig = Boolean.valueOf(
                    sp.getBoolean(KEY_VONR_EMERGENCY_SUPPORT + i, false));
            mVoNrSupported.put(Integer.valueOf(i), savedConfig);
            Log.i(TAG, "readFromSharedPreference slot=" + i + ", " + savedConfig);
        }
    }

    private void onCarrierConfigurationChanged(int slotIndex, int subId, int carrierId) {
        Log.i(TAG, "onCarrierConfigurationChanged slotIndex=" + slotIndex
                + ", subId=" + subId + ", carrierId=" + carrierId);

        if (slotIndex < 0
                || !SubscriptionManager.isValidSubscriptionId(subId)
                || mTelephonyManager.getSimState(slotIndex) != TelephonyManager.SIM_STATE_READY) {
            return;
        }

        PersistableBundle b = mConfigManager.getConfigForSubId(subId,
                KEY_EMERGENCY_OVER_IMS_SUPPORTED_3GPP_NETWORK_TYPES_INT_ARRAY);
        if (b.isEmpty()) {
            Log.e(TAG, "onCarrierConfigurationChanged empty result");
            return;
        }

        if (!CarrierConfigManager.isConfigForIdentifiedCarrier(b)) {
            Log.i(TAG, "onCarrierConfigurationChanged not carrier specific configuration");
            return;
        }

        int[] imsRatsConfig = b.getIntArray(
                KEY_EMERGENCY_OVER_IMS_SUPPORTED_3GPP_NETWORK_TYPES_INT_ARRAY);
        if (imsRatsConfig == null) imsRatsConfig = new int[0];
        boolean carrierConfig = false;
        for (int i = 0; i < imsRatsConfig.length; i++) {
            if (imsRatsConfig[i] == NGRAN) {
                carrierConfig = true;
                break;
            }
        }
        if (mIgnoreNrWhenSimRemoved.contains(carrierId)) carrierConfig = false;

        Boolean savedConfig = mVoNrSupported.get(Integer.valueOf(slotIndex));
        if (carrierConfig == savedConfig) {
            return;
        }

        mVoNrSupported.put(Integer.valueOf(slotIndex), Boolean.valueOf(carrierConfig));

        SharedPreferences sp = (mSharedPreferences != null) ? mSharedPreferences
                : PreferenceManager.getDefaultSharedPreferences(mContext);
        SharedPreferences.Editor editor = sp.edit();
        editor.putBoolean(KEY_VONR_EMERGENCY_SUPPORT + slotIndex, carrierConfig);
        editor.apply();

        Log.i(TAG, "onCarrierConfigurationChanged preference updated slotIndex=" + slotIndex
                + ", supported=" + carrierConfig);
    }

    private void readResourceConfiguration() {
        try {
            mIgnoreNrWhenSimRemoved = Arrays.stream(mContext.getResources().getIntArray(
                    R.array.config_carriers_ignore_ngran_preference_when_sim_removed))
                    .boxed().collect(Collectors.toList());
        } catch (Resources.NotFoundException nfe) {
            Log.e(TAG, "readResourceConfiguration exception=" + nfe);
        } catch (NullPointerException npe) {
            Log.e(TAG, "readResourceConfiguration exception=" + npe);
        }
        if (mIgnoreNrWhenSimRemoved == null) {
            mIgnoreNrWhenSimRemoved = new ArrayList<Integer>();
        }
        Log.i(TAG, "readResourceConfiguration ignoreNrWhenSimRemoved=" + mIgnoreNrWhenSimRemoved);
    }

    /** Destroys the instance. */
    public void destroy() {
        if (DBG) Log.d(TAG, "destroy");
        mConfigManager.unregisterCarrierConfigChangeListener(mCarrierConfigChangeListener);
    }
}
