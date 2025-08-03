/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.ons;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelUuid;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.euicc.EuiccManager;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;

/**
 * @class ONSProfileConfigurator
 * @brief Helper class to support ONSProfileActivator to read and update profile, operator and CBRS
 * configurations.
 */
public class ONSProfileConfigurator {

    private static final String TAG = ONSProfileConfigurator.class.getName();
    @VisibleForTesting protected static final String PARAM_SUB_ID = "SUB_ID";
    @VisibleForTesting protected static final String PARAM_REQUEST_TYPE = "REQUEST_TYPE";
    @VisibleForTesting protected static final int REQUEST_CODE_ACTIVATE_SUB = 1;
    @VisibleForTesting protected static final int REQUEST_CODE_DELETE_SUB = 2;
    @VisibleForTesting
    protected static final String ACTION_ONS_ESIM_CONFIG = "com.android.ons.action.ESIM_CONFIG";

    private final Context mContext;
    private final SubscriptionManager mSubscriptionManager;
    private final CarrierConfigManager mCarrierConfigManager;
    private final EuiccManager mEuiccManager;
    private ONSProfConfigListener mONSProfConfigListener = null;
    private final Handler mHandler;

    public ONSProfileConfigurator(Context context, SubscriptionManager subscriptionManager,
                                  CarrierConfigManager carrierConfigManager,
                                  EuiccManager euiccManager, ONSProfConfigListener listener) {
        mContext = context;
        mSubscriptionManager = subscriptionManager;
        mCarrierConfigManager = carrierConfigManager;
        mEuiccManager = euiccManager;
        mONSProfConfigListener = listener;

        mHandler = new Handler(Looper.myLooper()) {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                callbackMsgHandler(msg);
            }
        };
    }

    /**
     * Callback to receive result for subscription activate request and process the same.
     *
     * @param intent
     * @param resultCode
     */
    public void onCallbackIntentReceived(Intent intent, int resultCode) {
        Message msg = new Message();
        msg.obj = intent;
        msg.arg1 = resultCode;
        mHandler.sendMessage(msg);
    }

    @VisibleForTesting
    protected void callbackMsgHandler(Message msg) {
        Intent intent = (Intent) msg.obj;
        int resultCode = msg.arg1;

        int reqCode = intent.getIntExtra(PARAM_REQUEST_TYPE, 0);
        switch (reqCode) {
            case REQUEST_CODE_ACTIVATE_SUB: {
                /*int subId = intent.getIntExtra(ACTIVATE_SUB_ID, 0);*/
                Log.d(TAG, "Opportunistic subscription activated successfully. SubId:"
                        + intent.getIntExtra(PARAM_SUB_ID, 0));
                Log.d(TAG, "Detailed result code: " + intent.getIntExtra(
                        EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_DETAILED_CODE, 0));
            }
            break;
            case REQUEST_CODE_DELETE_SUB: {
                if (resultCode != EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_OK) {
                    Log.e(TAG, "Error removing euicc opportunistic profile."
                            + "Detailed error code = " + intent.getIntExtra(
                                    EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_DETAILED_CODE, 0));
                } else if (mONSProfConfigListener != null) {
                    int subId = intent.getIntExtra(PARAM_SUB_ID, 0);
                    mONSProfConfigListener.onOppSubscriptionDeleted(subId);
                    Log.d(TAG, "Opportunistic subscription deleted successfully. Id:" + subId);
                }
            }
            break;
        }
    };

    /**
     * Adds downloaded subscription to the group, activates and enables opportunistic data.
     *
     * @param opportunisticESIM
     * @param groupUuid
     */
    @VisibleForTesting
    protected void groupWithPSIMAndSetOpportunistic(
            SubscriptionInfo opportunisticESIM, ParcelUuid groupUuid) {
        if (groupUuid != null && groupUuid.equals(opportunisticESIM.getGroupUuid())) {
            Log.d(TAG, "opportunistc eSIM and CBRS pSIM already grouped");
        } else {
            Log.d(TAG, "Grouping opportunistc eSIM and CBRS pSIM");
            ArrayList<Integer> subList = new ArrayList<>();
            subList.add(opportunisticESIM.getSubscriptionId());
            try {
                mSubscriptionManager.addSubscriptionsIntoGroup(subList, groupUuid);
            } catch (RuntimeException re) {
                // Telephony not found
                Log.e(TAG, "Subscription group add failed.", re);
            }
        }

        if (!opportunisticESIM.isOpportunistic()) {
            Log.d(TAG, "set Opportunistic to TRUE");
            mSubscriptionManager.setOpportunistic(true,
                    opportunisticESIM.getSubscriptionId());
        }
        //activateSubscription(opportunisticESIM);// -> activate after download flag is passed as
        //true in download request so no need of explicit activation.
    }

    /**
     * Activates provided subscription. Result is received in method onCallbackIntentReceived.
     */
    public void activateSubscription(int subId) {
        Intent intent = new Intent(mContext, ONSProfileResultReceiver.class);
        intent.setAction(ACTION_ONS_ESIM_CONFIG);
        intent.putExtra(PARAM_REQUEST_TYPE, REQUEST_CODE_ACTIVATE_SUB);
        intent.putExtra(PARAM_SUB_ID, subId);
        PendingIntent callbackIntent = PendingIntent.getBroadcast(mContext,
                REQUEST_CODE_ACTIVATE_SUB, intent, PendingIntent.FLAG_IMMUTABLE);
        Log.d(TAG, "Activate oppSub request sent to SubManager");
        mEuiccManager.switchToSubscription(subId, callbackIntent);
    }

    /**
     * Deletes inactive opportunistic subscriptions irrespective of the CBRS operator.
     * Called when sufficient memory is not available before downloading new profile.
     */
    public boolean deleteInactiveOpportunisticSubscriptions(int pSIMId) {
        Log.d(TAG, "deleteInactiveOpportunisticSubscriptions");

        List<SubscriptionInfo> subList = mSubscriptionManager.getOpportunisticSubscriptions();
        if (subList == null || subList.size() <= 0) {
            return false;
        }

        for (SubscriptionInfo subInfo : subList) {
            int subId = subInfo.getSubscriptionId();
            if (!mSubscriptionManager.isActiveSubscriptionId(subId)) {
                deleteSubscription(subId);
                return true;
            }
        }

        return false;
    }

    /**
     * Returns previously downloaded opportunistic eSIM associated with pSIM CBRS operator.
     * Helpful to cleanup before downloading new opportunistic eSIM from the same CBRS operator.
     *
     * @return true - If an eSIM is found.
     *          false - If no eSIM is found.
     */
    ArrayList<Integer> getOpportunisticSubIdsofPSIMOperator(int pSIMSubId) {
        Log.d(TAG, "getOpportunisticSubIdsofPSIMOperator");
        ArrayList<Integer> opportunisticSubIds = new ArrayList<Integer>();
        //1.Get the list of all opportunistic carrier-ids of newly inserted pSIM from carrier config
        PersistableBundle config = mCarrierConfigManager.getConfigForSubId(pSIMSubId);
        int[] oppCarrierIdArr = config.getIntArray(
                CarrierConfigManager.KEY_OPPORTUNISTIC_CARRIER_IDS_INT_ARRAY);
        if (oppCarrierIdArr == null || oppCarrierIdArr.length <= 0) {
            return null;
        }

        //2. Get list of all subscriptions
        List<SubscriptionInfo> oppSubList = mSubscriptionManager.getAvailableSubscriptionInfoList();
        for (SubscriptionInfo subInfo : oppSubList) {
            for (int oppCarrierId : oppCarrierIdArr) {
                //Carrier-id of opportunistic eSIM matches with one of thecarrier-ids in carrier
                // config of pSIM
                if (subInfo.isEmbedded() && oppCarrierId == subInfo
                        .getCarrierId()) {
                    //3.if carrier-id of eSIM matches with one of the pSIM opportunistic carrier-ids
                    //and eSIM's pSIM carrier-id matches with new pSIM then delete the subscription
                    opportunisticSubIds.add(subInfo.getSubscriptionId());
                }
            }
        }

        return opportunisticSubIds;
    }

    /**
     * Sends delete request to the eUICC manager to delete a given subscription.
     * @param subId
     */
    public void deleteSubscription(int subId) {
        Log.d(TAG, "deleting subscription. SubId: " + subId);
        Intent intent = new Intent(mContext, ONSProfileResultReceiver.class);
        intent.setAction(ACTION_ONS_ESIM_CONFIG);
        intent.putExtra(PARAM_REQUEST_TYPE, REQUEST_CODE_DELETE_SUB);
        intent.putExtra(PARAM_SUB_ID, subId);
        PendingIntent callbackIntent = PendingIntent.getBroadcast(mContext,
                REQUEST_CODE_DELETE_SUB, intent, PendingIntent.FLAG_MUTABLE);
        mEuiccManager.deleteSubscription(subId, callbackIntent);
    }

    /**
     * Creates Subscription Group for PSIM if it doesn't exist or returns existing group-id.
     */
    public ParcelUuid getPSIMGroupId(SubscriptionInfo primaryCBRSSubInfo) {
        ParcelUuid groupId = primaryCBRSSubInfo.getGroupUuid();
        if (groupId != null) {
            return groupId;
        }

        Log.d(TAG, "Creating Group for Primary SIM");
        List<Integer> pSubList = new ArrayList<>();
        pSubList.add(primaryCBRSSubInfo.getSubscriptionId());
        ParcelUuid puid = null;
        try {
            puid = mSubscriptionManager.createSubscriptionGroup(pSubList);
        } catch (RuntimeException re) {
            // Telephony not found
            Log.e(TAG, "Subscription group creation failed.", re);
        }
        return puid;
    }

    /**
     * Searches for opportunistic profile in all available subscriptions using carrier-ids
     * from carrier configuration and returns opportunistic subscription.
     */
    public SubscriptionInfo findOpportunisticSubscription(int pSIMId) {
        Log.d(TAG, "findOpportunisticSubscription. PSIM Id : " + pSIMId);

        //Get the list of active subscriptions
        List<SubscriptionInfo> availSubInfoList = mSubscriptionManager
                .getAvailableSubscriptionInfoList();
        if (availSubInfoList == null) {
            Log.e(TAG, "getAvailableSubscriptionInfoList returned null");
            return null;
        }
        Log.d(TAG, "Available subscriptions: " + availSubInfoList.size());

        //Get the list of opportunistic carrier-ids list from carrier config.
        PersistableBundle config = mCarrierConfigManager.getConfigForSubId(pSIMId);
        int[] oppCarrierIdArr = config.getIntArray(
                CarrierConfigManager.KEY_OPPORTUNISTIC_CARRIER_IDS_INT_ARRAY);
        if (oppCarrierIdArr == null || oppCarrierIdArr.length <= 0) {
            Log.e(TAG, "Opportunistic carrier-ids list is empty in carrier config");
            return null;
        }

        SubscriptionInfo subscriptionInfo = mSubscriptionManager.getActiveSubscriptionInfo(pSIMId);
        if (subscriptionInfo == null) {
            Log.e(TAG, "getActiveSubscriptionInfo returned null for: " + pSIMId);
            return null;
        }
        ParcelUuid pSIMSubGroupId = subscriptionInfo.getGroupUuid();
        for (SubscriptionInfo subInfo : availSubInfoList) {
            if (subInfo.getSubscriptionId() != pSIMId) {
                for (int carrId : oppCarrierIdArr) {
                    if (subInfo.isEmbedded() && carrId == subInfo.getCarrierId()) {
                        //An active eSIM whose carrier-id is listed as opportunistic carrier in
                        // carrier config is newly downloaded opportunistic eSIM.

                        ParcelUuid oppSubGroupId = subInfo.getGroupUuid();
                        if (oppSubGroupId == null /*Immediately after opp eSIM is downloaded case*/
                                || oppSubGroupId.equals(pSIMSubGroupId) /*Already downloaded and
                                grouped case.*/) {
                            Log.d(TAG, "Opp subscription:" + subInfo.getSubscriptionId());
                            return subInfo;
                        }
                    }
                }
            }
        }

        return null;
    }

    /**
     * Listener interface to notify delete subscription operation.
     */
    public interface ONSProfConfigListener {
        /**
         * Called when the delete subscription request is processed successfully.
         */
        void onOppSubscriptionDeleted(int pSIMId);
    }
}
