/*
 * Copyright 2020 The Android Open Source Project
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

package com.google.android.iwlan;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.data.ApnSetting;
import android.util.Log;

import com.google.android.iwlan.epdg.EpdgSelector;

import java.util.Arrays;

public class IwlanBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "IwlanBroadcastReceiver";

    private static boolean mIsReceiverRegistered = false;
    private static IwlanBroadcastReceiver mInstance;

    public static void startListening(Context context) {
        if (mIsReceiverRegistered) {
            Log.d(TAG, "startListening: Receiver already registered");
            return;
        }
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        intentFilter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        context.registerReceiver(getInstance(), intentFilter);
        mIsReceiverRegistered = true;
    }

    public static void stopListening(Context context) {
        if (!mIsReceiverRegistered) {
            Log.d(TAG, "stopListening: Receiver not registered!");
            return;
        }
        context.unregisterReceiver(getInstance());
        mIsReceiverRegistered = false;
    }

    private static IwlanBroadcastReceiver getInstance() {
        if (mInstance == null) {
            mInstance = new IwlanBroadcastReceiver();
        }
        return mInstance;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "onReceive: " + action);
        switch (action) {
            case Intent.ACTION_AIRPLANE_MODE_CHANGED:
            case WifiManager.WIFI_STATE_CHANGED_ACTION:
                IwlanEventListener.onBroadcastReceived(intent);
                break;
            case TelephonyManager.ACTION_CARRIER_SIGNAL_PCO_VALUE:
                processCarrierSignalPcoValue(intent);
                break;
        }
    }

    private void processCarrierSignalPcoValue(Intent intent) {
        Log.d(TAG, "on CARRIER_SIGNAL_PCO_VALUE intent");
        int intentSubId =
                intent.getIntExtra(
                        SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,
                        SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        int intentSlotIndex = SubscriptionManager.getSlotIndex(intentSubId);
        Log.d(TAG, "intentSubId:" + intentSubId + " intentSlotIndex:" + intentSlotIndex);

        if (intentSlotIndex != SubscriptionManager.INVALID_SIM_SLOT_INDEX) {

            int apnBitMask = intent.getIntExtra(TelephonyManager.EXTRA_APN_TYPE, 0);

            if ((apnBitMask & ApnSetting.TYPE_IMS) != 0) {
                int pcoId = intent.getIntExtra(TelephonyManager.EXTRA_PCO_ID, 0);
                byte[] pcoData = intent.getByteArrayExtra(TelephonyManager.EXTRA_PCO_VALUE);

                if (pcoData == null) {
                    Log.e(TAG, "Pco data unavailable");
                    return;
                }

                Log.d(
                        TAG,
                        "PcoID:"
                                + String.format("0x%04x", pcoId)
                                + " PcoData:"
                                + Arrays.toString(pcoData));

                Context mContext = IwlanDataService.getContext();

                if (mContext != null) {
                    int PCO_ID_IPv6 =
                            IwlanHelper.getConfig(
                                    CarrierConfigManager.Iwlan.KEY_EPDG_PCO_ID_IPV6_INT,
                                    mContext,
                                    intentSlotIndex);
                    int PCO_ID_IPv4 =
                            IwlanHelper.getConfig(
                                    CarrierConfigManager.Iwlan.KEY_EPDG_PCO_ID_IPV4_INT,
                                    mContext,
                                    intentSlotIndex);

                    Log.d(
                            TAG,
                            "PCO_ID_IPv6:"
                                    + String.format("0x%04x", PCO_ID_IPv6)
                                    + " PCO_ID_IPv4:"
                                    + String.format("0x%04x", PCO_ID_IPv4));

                    if (pcoId == PCO_ID_IPv6 || pcoId == PCO_ID_IPv4) {
                        Log.d(TAG, "SetPcoData to EpdgSelector");
                        EpdgSelector selector =
                                EpdgSelector.getSelectorInstance(mContext, intentSlotIndex);
                        boolean ret = selector.setPcoData(pcoId, pcoData);
                    } else {
                        Log.d(TAG, "Unwanted PcoID " + pcoId);
                    }
                } else {
                    Log.e(TAG, "Null context");
                }
            } else {
                Log.d(TAG, "Unwanted Apntype " + apnBitMask);
            }
        } else {
            Log.e(TAG, "Invalid slot index");
        }
    }
}
