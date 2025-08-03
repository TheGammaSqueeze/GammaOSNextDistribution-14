/**
 * Copyright (c) 2017-2020, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.codeaurora.ims.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;

import org.codeaurora.ims.QtiCallConstants;

public class QtiCarrierConfigHelper {
    static final String TAG  = QtiCarrierConfigHelper.class.getSimpleName();
    private static final boolean DEBUG =
            android.util.Log.isLoggable(TAG, android.util.Log.DEBUG);
    private static final String DELIMITER = " : ";
    private static int PHONE_COUNT;
    private Context mContext;
    SubscriptionManager mSubscriptionManager;
    CarrierConfigManager mCarrierConfigManager;
    Map<Integer, PersistableBundle> mConfigsMap = new ConcurrentHashMap<>();
    private AtomicBoolean mInitialized = new AtomicBoolean(false);
    private int[] subCache;

    private static class SingletonHolder {
        public final static QtiCarrierConfigHelper sInstance = new QtiCarrierConfigHelper();
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && (intent.getAction()
                    .equals(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED) ||
                    intent.getAction().equals(QtiCallConstants.ACTION_ESSENTIAL_RECORDS_LOADED))) {
                int phoneId = intent.getIntExtra(CarrierConfigManager.EXTRA_SLOT_INDEX,
                        SubscriptionManager.INVALID_SIM_SLOT_INDEX);
                if (mSubscriptionManager != null) {
                    SubscriptionInfo subInfo = mSubscriptionManager
                            .getActiveSubscriptionInfoForSimSlotIndex(phoneId);
                    if (subInfo != null && mSubscriptionManager.isActiveSubscriptionId(
                            subInfo.getSubscriptionId())) {
                        Log.d(TAG, "Reload carrier configs on phone Id: " + phoneId
                                + " sub Id: " + subInfo.getSubscriptionId());
                        loadConfigsForSubInfo(subInfo);
                    } else {
                        Log.d(TAG, "Clear carrier configs on phone Id: " + phoneId);
                        mConfigsMap.remove(phoneId);
                    }
                }
            }
        }
    };

    private final SubscriptionManager.OnSubscriptionsChangedListener mOnSubscriptionsChangeListener
            = new QtiCarrierConfigHelperOnSubscriptionsChangedListener();

    private class QtiCarrierConfigHelperOnSubscriptionsChangedListener
            extends SubscriptionManager.OnSubscriptionsChangedListener {

        @Override
        public void onSubscriptionsChanged() {
            if (mSubscriptionManager != null) {
                List<SubscriptionInfo> subInfos =
                        mSubscriptionManager.getActiveSubscriptionInfoList();
                if (subInfos != null) {
                    for (SubscriptionInfo subInfo : subInfos) {
                        if (isValidPhoneId(subInfo.getSimSlotIndex()) &&
                                (subCache[subInfo.getSimSlotIndex()]
                                != subInfo.getSubscriptionId())) {
                            subCache[subInfo.getSimSlotIndex()] = subInfo.getSubscriptionId();
                            Log.d(TAG, "Reload carrier configs on sub Id due sub changed: "
                                    + subInfo.getSubscriptionId());
                            loadConfigsForSubInfo(subInfo);
                        }
                    }
                }
            }
        }
    };

    private QtiCarrierConfigHelper () {
    }

    public static QtiCarrierConfigHelper getInstance() {
        return SingletonHolder.sInstance;
    }

    public void setup(Context context) {
        if (context == null) {
            throw new NullPointerException("QtiCarrierConfigHelper context is null in setup");
        }
        Context appContext = context.getApplicationContext();
        if (appContext == null) {
            throw new NullPointerException("QtiCarrierConfigHelper app context is null in setup");
        }
        logd("setup", "mInitialized - " + mInitialized.get());
        if (!mInitialized.compareAndSet(false, true)) {
            logd("setup", "already initialized exiting");
            return;
        }
        mContext = appContext;
        PHONE_COUNT = ((TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE)).
            getPhoneCount();
        mSubscriptionManager = (SubscriptionManager) mContext
            .getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        mCarrierConfigManager = (CarrierConfigManager) mContext.getSystemService(
                Context.CARRIER_CONFIG_SERVICE);

        List<SubscriptionInfo> subInfos = mSubscriptionManager
                .getActiveSubscriptionInfoList();
        if (subInfos != null) {
            for (SubscriptionInfo subInfo : subInfos) {
                loadConfigsForSubInfo(subInfo);
            }
        }
        subCache = new int[PHONE_COUNT];
        IntentFilter filter = new IntentFilter(CarrierConfigManager
                .ACTION_CARRIER_CONFIG_CHANGED);
        filter.addAction(QtiCallConstants.ACTION_ESSENTIAL_RECORDS_LOADED);
        mContext.registerReceiver(mReceiver, filter, Context.RECEIVER_EXPORTED);
        mSubscriptionManager.addOnSubscriptionsChangedListener(mOnSubscriptionsChangeListener);
    }

    public void teardown() {
        logd("teardown", "mInitialized - " + mInitialized.get());
        if (!mInitialized.compareAndSet(true, false)) {
            logd("teardown", "WARNING, Not set up yet or already torn down.");
            return;
        }
        mConfigsMap.clear();
        if (mContext != null) {
            mContext.unregisterReceiver(mReceiver);
            if (mSubscriptionManager != null) {
                mSubscriptionManager
                        .removeOnSubscriptionsChangedListener(mOnSubscriptionsChangeListener);
            }
        }
    }

    void loadConfigsForSubInfo(SubscriptionInfo subInfo) {
        if (subInfo != null && mCarrierConfigManager != null) {
            PersistableBundle pb = mCarrierConfigManager
                     .getConfigForSubId(subInfo.getSubscriptionId());
            if (pb != null) {
                Log.d(TAG, "Load carrier configs on sub Id: " + subInfo.getSubscriptionId()
                        + " slot Id: " + subInfo.getSimSlotIndex());
                mConfigsMap.put(subInfo.getSimSlotIndex(), pb);
            } else {
                Log.d(TAG, "No configs on sub Id: " + subInfo.getSubscriptionId());
                mConfigsMap.put(subInfo.getSimSlotIndex(), PersistableBundle.EMPTY);
            }
        }
    }

    private void sanityCheckConfigsLoaded(Context context, int phoneId) {
        // Handling additional checks for initialized state and context within setup
        setup(context);
    }

    public boolean isValidPhoneId(int phoneId) {
        return 0 <= phoneId && phoneId < PHONE_COUNT;
    }

    public boolean getBoolean(Context context, int phoneId, String key) {
        if (!isValidPhoneId(phoneId)) {
            Log.d(TAG, "Invalid phone ID: " + phoneId);
            return false;
        }
        sanityCheckConfigsLoaded(context, phoneId);
        logd("getBoolean", "mInitialized - " + mInitialized.get() + " context - " + context);
        PersistableBundle pb = mConfigsMap.get(phoneId);
        if (pb != null) {
            return pb.getBoolean(key, false);
        }
        Log.d(TAG, "WARNING, no carrier configs on phone Id: " + phoneId);
        return false;
    }

    public boolean getBoolean(int phoneId, String key) {
        if (!isValidPhoneId(phoneId)) {
            logd("getBoolean", "Invalid phone ID: " + phoneId);
            return false;
        }
        if (!mInitialized.get()) {
            logd("getBoolean", "WARNING, Not set up yet.");
            return false;
        }
        PersistableBundle pb = mConfigsMap.get(phoneId);
        if (pb != null) {
            return pb.getBoolean(key, false);
        }
        Log.d(TAG, "WARNING, no carrier configs on phone Id: " + phoneId);
        return false;
    }

    public String[] getStringArray(Context context, int phoneId, String key) {
        if (!isValidPhoneId(phoneId)) {
            Log.d(TAG, "Invalid phone ID: " + phoneId);
            return null;
        }
        sanityCheckConfigsLoaded(context, phoneId);
        logd("getString", "mInitialized - " + mInitialized.get() + " context - " + context);
        PersistableBundle pb = mConfigsMap.get(phoneId);
        if (pb != null) {
            return pb.getStringArray(key);
        }
        Log.d(TAG, "WARNING, no carrier configs on phone Id: " + phoneId);
        return null;
    }

    private static void logd(String tag, String msg) {
        if (DEBUG) {
            android.util.Log.d(TAG, tag + DELIMITER + msg);
        }
    }
}
