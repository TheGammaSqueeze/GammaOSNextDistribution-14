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

import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_CROSS_STACK_REDIAL_TIMER_SEC_INT;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_QUICK_CROSS_STACK_REDIAL_TIMER_SEC_INT;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_START_QUICK_CROSS_STACK_REDIAL_TIMER_WHEN_REGISTERED_BOOL;
import static android.telephony.CarrierConfigManager.ImsEmergency.REDIAL_TIMER_DISABLED;
import static android.telephony.PreciseDisconnectCause.EMERGENCY_PERM_FAILURE;

import android.annotation.NonNull;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PersistableBundle;
import android.os.SystemProperties;
import android.telephony.AccessNetworkConstants;
import android.telephony.Annotation.PreciseDisconnectCauses;
import android.telephony.CarrierConfigManager;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.PhoneNumberUtils;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.emergency.EmergencyNumber;
import android.text.TextUtils;
import android.util.LocalLog;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Controls the cross stack redialing. */
public class CrossSimRedialingController extends Handler {
    private static final String TAG = "CrossSimRedialingCtrl";
    private static final boolean DBG = (SystemProperties.getInt("ro.debuggable", 0) == 1);
    private static final int LOG_SIZE = 50;

    /** An interface of a helper to check emergency number. */
    public interface EmergencyNumberHelper {
        /**
         * Returns whether the number is an emergency number in the given modem slot.
         *
         * @param subId The sub id to be checked.
         * @param number The number.
         * @return {@code true} if the number is an emergency number in the given slot.
         */
        boolean isEmergencyNumber(int subId, String number);
    }

    @VisibleForTesting
    public static final int MSG_CROSS_STACK_TIMEOUT = 1;
    @VisibleForTesting
    public static final int MSG_QUICK_CROSS_STACK_TIMEOUT = 2;

    private static final LocalLog sLocalLog = new LocalLog(LOG_SIZE);

    private final ArrayList<Integer> mStackSelectionHistory = new ArrayList<>();
    private final ArrayList<Integer> mPermanentRejectedSlots = new ArrayList<>();
    private final TelephonyManager mTelephonyManager;

    private EmergencyNumberHelper mEmergencyNumberHelper = new EmergencyNumberHelper() {
        @Override
        public boolean isEmergencyNumber(int subId, String number) {
            number = PhoneNumberUtils.stripSeparators(number);
            if (TextUtils.isEmpty(number)) return false;
            Map<Integer, List<EmergencyNumber>> lists = null;
            try {
                lists = mTelephonyManager.getEmergencyNumberList();
            } catch (IllegalStateException ise) {
                loge("isEmergencyNumber ise=" + ise);
            } catch (RuntimeException rte) {
                loge("isEmergencyNumber rte=" + rte);
            }
            if (lists == null) return false;

            List<EmergencyNumber> list = lists.get(subId);
            if (list == null || list.isEmpty()) return false;
            for (EmergencyNumber eNumber : list) {
                if (number.equals(eNumber.getNumber())) return true;
            }
            return false;
        }
    };

    private int mModemCount;

    /** A cache of the carrier config {@link #KEY_CROSS_STACK_REDIAL_TIMER_SEC_INT}. */
    private int mCrossStackTimer;
    /** A cache of the carrier config {@link #KEY_QUICK_CROSS_STACK_REDIAL_TIMER_SEC_INT}. */
    private int mQuickCrossStackTimer;
    /**
     * A cache of the carrier config
     * {@link #KEY_START_QUICK_CROSS_STACK_REDIAL_TIMER_WHEN_REGISTERED_BOOL}.
     */
    private boolean mStartQuickCrossStackTimerWhenInService;

    private String mCallId;
    private EmergencyCallDomainSelector mSelector;
    private String mNumber;
    private int mSlotId;
    private int mSubId;

    /**
     * Creates an instance.
     *
     * @param context The Context this is associated with.
     * @param looper The Looper to run the CrossSimRedialingController.
     */
    public CrossSimRedialingController(@NonNull Context context, @NonNull Looper looper) {
        super(looper);

        mTelephonyManager = context.getSystemService(TelephonyManager.class);
    }

    /** For unit test only */
    @VisibleForTesting
    public CrossSimRedialingController(@NonNull Context context, @NonNull Looper looper,
            EmergencyNumberHelper emergencyNumberHelper) {
        this(context, looper);

        mEmergencyNumberHelper = emergencyNumberHelper;
    }

    /**
     * Starts the timer.
     *
     * @param context The Context this is associated with.
     * @param selector The instance of {@link EmergencyCallDomainSelector}.
     * @param callId The call identifier.
     * @param number The dialing number.
     * @param inService Indicates that normal service is available.
     * @param roaming Indicates that it's in roaming or non-domestic network.
     * @param modemCount The number of active modem count
     */
    public void startTimer(@NonNull Context context,
            @NonNull EmergencyCallDomainSelector selector,
            @NonNull String callId, @NonNull String number,
            boolean inService, boolean roaming, int modemCount) {
        logi("startTimer callId=" + callId
                + ", in service=" + inService + ", roaming=" + roaming);

        if (!TextUtils.equals(mCallId, callId)) {
            logi("startTimer callId changed");
            mCallId = callId;
            mStackSelectionHistory.clear();
            mPermanentRejectedSlots.clear();
        }
        mSelector = selector;
        mSlotId = selector.getSlotId();
        mSubId = selector.getSubId();
        mNumber = number;
        mModemCount = modemCount;

        updateCarrierConfiguration(context);

        boolean firstAttempt = !mStackSelectionHistory.contains(mSlotId);
        logi("startTimer slot=" + mSlotId + ", firstAttempt=" + firstAttempt);
        mStackSelectionHistory.add(mSlotId);

        if (firstAttempt && mQuickCrossStackTimer > REDIAL_TIMER_DISABLED && !roaming) {
            if (inService || !mStartQuickCrossStackTimerWhenInService) {
                logi("startTimer quick timer started");
                sendEmptyMessageDelayed(MSG_QUICK_CROSS_STACK_TIMEOUT,
                        mQuickCrossStackTimer);
                return;
            }
        }

        if (mCrossStackTimer > REDIAL_TIMER_DISABLED) {
            logi("startTimer timer started");
            sendEmptyMessageDelayed(MSG_CROSS_STACK_TIMEOUT, mCrossStackTimer);
        }
    }

    /** Stops the timers. */
    public void stopTimer() {
        logi("stopTimer");
        removeMessages(MSG_CROSS_STACK_TIMEOUT);
        removeMessages(MSG_QUICK_CROSS_STACK_TIMEOUT);
    }

    /**
     * Informs the call failure.
     * @param cause The call failure cause.
     */
    public void notifyCallFailure(@PreciseDisconnectCauses int cause) {
        logi("notifyCallFailure cause=" + cause);
        if (cause == EMERGENCY_PERM_FAILURE) {
            mPermanentRejectedSlots.add(mSlotId);
        }
    }

    @Override
    public void handleMessage(Message msg) {
        switch(msg.what) {
            case MSG_CROSS_STACK_TIMEOUT:
            case MSG_QUICK_CROSS_STACK_TIMEOUT:
                handleCrossStackTimeout();
                break;
            default:
                super.handleMessage(msg);
                break;
        }
    }

    private void handleCrossStackTimeout() {
        logi("handleCrossStackTimeout");

        if (isThereOtherSlot()) {
            mSelector.notifyCrossStackTimerExpired();
        }
    }

    /**
     * Returns whether there is another slot with which normal service is available.
     *
     * @return {@code true} if there is another slot with which normal service is available.
     *         {@code false} otherwise.
     */
    public boolean isThereOtherSlotInService() {
        return isThereOtherSlot(true);
    }

    /**
     * Returns whether there is another slot emergency capable.
     *
     * @return {@code true} if there is another slot emergency capable,
     *         {@code false} otherwise.
     */
    public boolean isThereOtherSlot() {
        return isThereOtherSlot(false);
    }

    private boolean isThereOtherSlot(boolean networkRegisteredOnly) {
        logi("isThereOtherSlot modemCount=" + mModemCount);
        if (mModemCount < 2) return false;

        for (int i = 0; i < mModemCount; i++) {
            if (i == mSlotId) continue;

            if (mPermanentRejectedSlots.contains(i)) {
                logi("isThereOtherSlot index=" + i + ", permanent rejected");
                continue;
            }

            int simState = mTelephonyManager.getSimState(i);
            if (simState != TelephonyManager.SIM_STATE_READY) {
                logi("isThereOtherSlot index=" + i + ", simState=" + simState);
                continue;
            }

            int subId = SubscriptionManager.getSubscriptionId(i);
            if (mEmergencyNumberHelper.isEmergencyNumber(subId, mNumber)) {
                logi("isThereOtherSlot index=" + i + "(" + subId + "), found");
                if (networkRegisteredOnly) {
                    if (isNetworkRegistered(subId)) {
                        return true;
                    }
                } else {
                    return true;
                }
            } else {
                logi("isThereOtherSlot index=" + i + "(" + subId + "), not emergency number");
            }
        }

        return false;
    }

    private boolean isNetworkRegistered(int subId) {
        if (!SubscriptionManager.isValidSubscriptionId(subId)) return false;

        TelephonyManager tm = mTelephonyManager.createForSubscriptionId(subId);
        ServiceState ss = tm.getServiceState();
        if (ss != null) {
            NetworkRegistrationInfo nri = ss.getNetworkRegistrationInfo(
                    NetworkRegistrationInfo.DOMAIN_PS,
                    AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
            if (nri != null && nri.isNetworkRegistered()) {
                // PS is IN_SERVICE state.
                return true;
            }
            nri = ss.getNetworkRegistrationInfo(
                    NetworkRegistrationInfo.DOMAIN_CS,
                    AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
            if (nri != null && nri.isNetworkRegistered()) {
                // CS is IN_SERVICE state.
                return true;
            }
        }
        logi("isNetworkRegistered subId=" + subId + " not network registered");
        return false;
    }

    /**
     * Caches the configuration.
     */
    private void updateCarrierConfiguration(Context context) {
        CarrierConfigManager configMgr = context.getSystemService(CarrierConfigManager.class);
        PersistableBundle b = configMgr.getConfigForSubId(mSubId,
                KEY_CROSS_STACK_REDIAL_TIMER_SEC_INT,
                KEY_QUICK_CROSS_STACK_REDIAL_TIMER_SEC_INT,
                KEY_START_QUICK_CROSS_STACK_REDIAL_TIMER_WHEN_REGISTERED_BOOL);
        if (b == null) {
            b = CarrierConfigManager.getDefaultConfig();
        }

        mCrossStackTimer = b.getInt(KEY_CROSS_STACK_REDIAL_TIMER_SEC_INT) * 1000;
        mQuickCrossStackTimer =
                b.getInt(KEY_QUICK_CROSS_STACK_REDIAL_TIMER_SEC_INT) * 1000;
        mStartQuickCrossStackTimerWhenInService =
                b.getBoolean(KEY_START_QUICK_CROSS_STACK_REDIAL_TIMER_WHEN_REGISTERED_BOOL);

        logi("updateCarrierConfiguration "
                + ", crossStackTimer=" + mCrossStackTimer
                + ", quickCrossStackTimer=" + mQuickCrossStackTimer
                + ", startQuickTimerInService=" + mStartQuickCrossStackTimerWhenInService);
    }

    /** Test purpose only. */
    @VisibleForTesting
    public EmergencyNumberHelper getEmergencyNumberHelper() {
        return mEmergencyNumberHelper;
    }

    /** Destroys the instance. */
    public void destroy() {
        if (DBG) logd("destroy");

        removeMessages(MSG_CROSS_STACK_TIMEOUT);
        removeMessages(MSG_QUICK_CROSS_STACK_TIMEOUT);
    }

    private void logd(String s) {
        Log.d(TAG, "[" + mSlotId + "|" + mSubId + "] " + s);
    }

    private void logi(String s) {
        Log.i(TAG, "[" + mSlotId + "|" + mSubId + "] " + s);
        sLocalLog.log(s);
    }

    private void loge(String s) {
        Log.e(TAG, "[" + mSlotId + "|" + mSubId + "] " + s);
        sLocalLog.log(s);
    }
}
