/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.mms.service.metrics;

import static com.android.mms.MmsStatsLog.INCOMING_MMS__RESULT__MMS_RESULT_ERROR_UNSPECIFIED;
import static com.android.mms.MmsStatsLog.INCOMING_MMS__RESULT__MMS_RESULT_SUCCESS;
import static com.android.mms.MmsStatsLog.OUTGOING_MMS__RESULT__MMS_RESULT_ERROR_UNSPECIFIED;
import static com.android.mms.MmsStatsLog.OUTGOING_MMS__RESULT__MMS_RESULT_SUCCESS;

import android.app.Activity;
import android.content.Context;
import android.os.Binder;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.UserManager;
import android.telephony.ServiceState;
import android.telephony.SmsManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.UiccCardInfo;

import com.android.internal.telephony.SmsApplication;
import com.android.internal.telephony.flags.Flags;
import com.android.mms.IncomingMms;
import com.android.mms.OutgoingMms;

import java.util.List;

/** Collects mms events for the pulled atom. */
public class MmsStats {
    private static final String TAG = MmsStats.class.getSimpleName();

    private final Context mContext;
    private final PersistMmsAtomsStorage mPersistMmsAtomsStorage;
    private final String mCallingPkg;
    private final boolean mIsIncomingMms;
    private final long mTimestamp;
    private int mSubId;
    private TelephonyManager mTelephonyManager;

    public MmsStats(Context context, PersistMmsAtomsStorage persistMmsAtomsStorage, int subId,
            TelephonyManager telephonyManager, String callingPkg, boolean isIncomingMms) {
        mContext = context;
        mPersistMmsAtomsStorage = persistMmsAtomsStorage;
        mSubId = subId;
        mTelephonyManager = telephonyManager;
        mCallingPkg = callingPkg;
        mIsIncomingMms = isIncomingMms;
        mTimestamp = SystemClock.elapsedRealtime();
    }

    /** Updates subId and corresponding telephonyManager. */
    public void updateSubId(int subId, TelephonyManager telephonyManager) {
        mSubId = subId;
        mTelephonyManager = telephonyManager;
    }

    /** Adds incoming or outgoing mms atom to storage. */
    public void addAtomToStorage(int result) {
        addAtomToStorage(result, 0, false);
    }

    /** Adds incoming or outgoing mms atom to storage. */
    public void addAtomToStorage(int result, int retryId, boolean handledByCarrierApp) {
        long identity = Binder.clearCallingIdentity();
        try {
            if (mIsIncomingMms) {
                onIncomingMms(result, retryId, handledByCarrierApp);
            } else {
                onOutgoingMms(result, retryId, handledByCarrierApp);
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /** Creates a new atom when MMS is received. */
    private void onIncomingMms(int result, int retryId, boolean handledByCarrierApp) {
        IncomingMms incomingMms = IncomingMms.newBuilder()
                .setRat(getDataNetworkType())
                .setResult(getIncomingMmsResult(result))
                .setRoaming(getDataRoamingType())
                .setSimSlotIndex(getSlotIndex())
                .setIsMultiSim(getIsMultiSim())
                .setIsEsim(getIsEuicc())
                .setCarrierId(getSimCarrierId())
                .setAvgIntervalMillis(getInterval())
                .setMmsCount(1)
                .setRetryId(retryId)
                .setHandledByCarrierApp(handledByCarrierApp)
                .setIsManagedProfile(isManagedProfile())
                .build();
        mPersistMmsAtomsStorage.addIncomingMms(incomingMms);
    }

    /** Creates a new atom when MMS is sent. */
    private void onOutgoingMms(int result, int retryId, boolean handledByCarrierApp) {
        OutgoingMms outgoingMms = OutgoingMms.newBuilder()
                .setRat(getDataNetworkType())
                .setResult(getOutgoingMmsResult(result))
                .setRoaming(getDataRoamingType())
                .setSimSlotIndex(getSlotIndex())
                .setIsMultiSim(getIsMultiSim())
                .setIsEsim(getIsEuicc())
                .setCarrierId(getSimCarrierId())
                .setAvgIntervalMillis(getInterval())
                .setMmsCount(1)
                .setIsFromDefaultApp(isDefaultMmsApp())
                .setRetryId(retryId)
                .setHandledByCarrierApp(handledByCarrierApp)
                .setIsManagedProfile(isManagedProfile())
                .build();
        mPersistMmsAtomsStorage.addOutgoingMms(outgoingMms);
    }

    /** @return {@code true} if this SIM is dedicated to work profile */
    private boolean isManagedProfile() {
        SubscriptionManager subManager = mContext.getSystemService(SubscriptionManager.class);
        if (subManager == null || !subManager.isActiveSubscriptionId(mSubId)) return false;
        UserHandle userHandle = subManager.getSubscriptionUserHandle(mSubId);
        UserManager userManager = mContext.getSystemService(UserManager.class);
        if (userHandle == null || userManager == null) return false;
        return userManager.isManagedProfile(userHandle.getIdentifier());
    }

    /** Returns data network type of current subscription. */
    private int getDataNetworkType() {
        return mTelephonyManager.getDataNetworkType();
    }

    /** Returns incoming mms result. */
    private int getIncomingMmsResult(int result) {
        switch (result) {
            case SmsManager.MMS_ERROR_UNSPECIFIED:
                // SmsManager.MMS_ERROR_UNSPECIFIED(1) -> MMS_RESULT_ERROR_UNSPECIFIED(0)
                return INCOMING_MMS__RESULT__MMS_RESULT_ERROR_UNSPECIFIED;
            case Activity.RESULT_OK:
                // Activity.RESULT_OK -> MMS_RESULT_SUCCESS(1)
                return INCOMING_MMS__RESULT__MMS_RESULT_SUCCESS;
            default:
                // Int value of other SmsManager.MMS_ERROR matches MMS_RESULT_ERROR
                return result;
        }
    }

    /** Returns outgoing mms result. */
    private int getOutgoingMmsResult(int result) {
        switch (result) {
            case SmsManager.MMS_ERROR_UNSPECIFIED:
                // SmsManager.MMS_ERROR_UNSPECIFIED(1) -> MMS_RESULT_ERROR_UNSPECIFIED(0)
                return OUTGOING_MMS__RESULT__MMS_RESULT_ERROR_UNSPECIFIED;
            case Activity.RESULT_OK:
                // Activity.RESULT_OK -> MMS_RESULT_SUCCESS(1)
                return OUTGOING_MMS__RESULT__MMS_RESULT_SUCCESS;
            default:
                // Int value of other SmsManager.MMS_ERROR matches MMS_RESULT_ERROR
                return result;
        }
    }

    /** Returns data network roaming type of current subscription. */
    private int getDataRoamingType() {
        ServiceState serviceState = mTelephonyManager.getServiceState();
        return (serviceState != null) ? serviceState.getDataRoamingType() :
                ServiceState.ROAMING_TYPE_NOT_ROAMING;
    }

    /** Returns slot index associated with the subscription. */
    private int getSlotIndex() {
        return SubscriptionManager.getSlotIndex(mSubId);
    }

    /** Returns whether the device has multiple active SIM profiles. */
    private boolean getIsMultiSim() {
        SubscriptionManager subManager = mContext.getSystemService(SubscriptionManager.class);
        if(subManager == null) {
            return false;
        }
        if (Flags.workProfileApiSplit()) {
            subManager = subManager.createForAllUserProfiles();
        }
        List<SubscriptionInfo> activeSubscriptionInfo = subManager.getActiveSubscriptionInfoList();
        return (activeSubscriptionInfo.size() > 1);
    }

    /** Returns if current subscription is embedded subscription. */
    private boolean getIsEuicc() {
        List<UiccCardInfo> uiccCardInfoList = mTelephonyManager.getUiccCardsInfo();
        for (UiccCardInfo card : uiccCardInfoList) {
            if (card.getPhysicalSlotIndex() == getSlotIndex()) {
                return card.isEuicc();
            }
        }
        return false;
    }

    /** Returns carrier id of the current subscription used by MMS. */
    private int getSimCarrierId() {
        return mTelephonyManager.getSimCarrierId();
    }

    /** Returns if the MMS was originated from the default MMS application. */
    private boolean isDefaultMmsApp() {
        UserHandle userHandle = null;
        SubscriptionManager subManager = mContext.getSystemService(SubscriptionManager.class);
        if ((subManager != null) && (subManager.isActiveSubscriptionId(mSubId))) {
            userHandle = subManager.getSubscriptionUserHandle(mSubId);
        }
        return SmsApplication.isDefaultMmsApplicationAsUser(mContext, mCallingPkg, userHandle);
    }

    /**
     * Returns the interval in milliseconds between sending/receiving MMS message and current time.
     * Calculates the time taken to send message to the network
     * or download message from the network.
     */
    private long getInterval() {
        return (SystemClock.elapsedRealtime() - mTimestamp);
    }
}
