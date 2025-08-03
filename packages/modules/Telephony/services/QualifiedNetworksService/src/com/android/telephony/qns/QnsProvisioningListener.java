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

package com.android.telephony.qns;

import android.annotation.NonNull;
import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.telephony.SubscriptionManager;
import android.telephony.ims.ProvisioningManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;

import java.util.concurrent.ConcurrentHashMap;

class QnsProvisioningListener {

    private static final long REG_CALLBACK_DELAY = 2000L; // 3sec
    private static final int REG_CALLBACK_RETRY = 10; // 10 times
    private static final int EVENT_BASE = 11000;
    private static final int EVENT_REGISTER_PROVISIONING_CALLBACK = EVENT_BASE + 1;
    private static final int EVENT_CALLBACK_REGISTERED = EVENT_BASE + 3;
    private static final int EVENT_NOTIFY_PROVISION_INFO_CHANGED = EVENT_BASE + 4;
    private static final int EVENT_IMS_STATE_CHANGED = EVENT_BASE + 5;
    private final String mLogTag;
    private final Context mContext;
    private final int mSlotIndex;
    private final QnsProvisioningInfo mProvisioningInfo;
    private final QnsImsManager mQnsImsManager;
    @VisibleForTesting QnsProvisioningHandler mQnsProvisioningHandler;

    private final QnsProvisioningCallback mQnsProvisioningCallback;
    private final QnsRegistrantList mRegistrantList;
    private ProvisioningManager mProvisioningManager;
    private boolean mIsProvisioningCallbackRegistered;

    QnsProvisioningListener(Context context, QnsImsManager imsManager, int slotIndex) {
        mSlotIndex = slotIndex;
        mLogTag = QnsProvisioningListener.class.getSimpleName() + "_" + mSlotIndex;
        mContext = context;
        mQnsImsManager = imsManager;
        mProvisioningInfo = new QnsProvisioningInfo();
        mQnsProvisioningCallback = new QnsProvisioningCallback();
        mIsProvisioningCallbackRegistered = false;
        mRegistrantList = new QnsRegistrantList();

        HandlerThread handlerThread = new HandlerThread(mLogTag);
        handlerThread.start();
        mQnsProvisioningHandler = new QnsProvisioningHandler(handlerThread.getLooper());

        registerProvisioningCallback();
        mQnsImsManager.registerImsStateChanged(mQnsProvisioningHandler, EVENT_IMS_STATE_CHANGED);
    }

    void close() {
        mQnsImsManager.unregisterImsStateChanged(mQnsProvisioningHandler);
        mRegistrantList.removeAll();
        mProvisioningInfo.clear();
        unregisterProvisioningCallback();
    }

    private void registerProvisioningCallback() {
        // checks if the callback is already registered
        if (mIsProvisioningCallbackRegistered) {
            log("registerProvisioningCallback: already registered.");
            return;
        }

        // checks for validation subscription id.
        int subId = QnsUtils.getSubId(mContext, mSlotIndex);
        if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            log("registerProvisioningCallback failed Invalid Subscription Id");
            return;
        }

        log("on registering provisioning callback");

        try {
            // checks ImsException for ims not supported or unavailable.
            if (mQnsImsManager.getImsServiceState() != 2) { // STATE_READY
                throw new Exception();
            }

            // create provisioning manager.
            if (mProvisioningManager == null) {
                mProvisioningManager = ProvisioningManager.createForSubscriptionId(subId);
            }

            // Register provisioning changed callback
            mProvisioningManager.registerProvisioningChangedCallback(
                    mContext.getMainExecutor(), mQnsProvisioningCallback);

            // Set the provisioning callback is registered.
            mIsProvisioningCallbackRegistered = true;

            log("registered provisioning callback");

            mQnsProvisioningHandler.sendProvisioningCallbackRegistered();
        } catch (Exception e) {
            loge("registerProvisioningCallback error: " + e);

            // Unregister the callback
            unregisterProvisioningCallback();

            // Retry registering provisioning callback.
            if (!mIsProvisioningCallbackRegistered) {
                mQnsProvisioningHandler.sendRegisterProvisioningCallback();
            }
        }
    }

    private void unregisterProvisioningCallback() {
        log("unregisterProvisioningCallback");

        if (mProvisioningManager != null) {
            try {
                mProvisioningManager.unregisterProvisioningChangedCallback(
                        mQnsProvisioningCallback);
            } catch (Exception e) {
                loge("unregisterProvisioningCallback error:" + e);
            }
        }
        if (mIsProvisioningCallbackRegistered) {
            mIsProvisioningCallbackRegistered = false;
        }
        if (mProvisioningManager != null) {
            mProvisioningManager = null;
        }
    }

    /**
     * Register an event for Provisioning value changed.
     *
     * @param h the Handler to get event.
     * @param what the event.
     * @param userObj user object.
     * @param notifyImmediately set true if you want to notify immediately.
     */
    void registerProvisioningItemInfoChanged(
            Handler h, int what, Object userObj, boolean notifyImmediately) {
        if (h != null) {
            QnsRegistrant r = new QnsRegistrant(h, what, userObj);
            mRegistrantList.add(r);
            if (notifyImmediately) {
                r.notifyRegistrant(
                        new QnsAsyncResult(null, new QnsProvisioningInfo(mProvisioningInfo), null));
            }
        }
    }

    /**
     * Unregister an event for Provisioning value changed.
     *
     * @param h the handler to get event.
     */
    void unregisterProvisioningItemInfoChanged(Handler h) {
        if (h != null) {
            mRegistrantList.remove(h);
        }
    }

    boolean getLastProvisioningWfcRoamingEnabledInfo() {
        try {
            return mProvisioningInfo.getIntegerItem(
                            ProvisioningManager.KEY_VOICE_OVER_WIFI_ROAMING_ENABLED_OVERRIDE)
                    != 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void notifyProvisioningItemInfoChanged(@NonNull QnsProvisioningInfo info) {
        log("notify ProvisioningItemInfo:" + info);
        mRegistrantList.notifyRegistrants(new QnsAsyncResult(null, info, null));
    }

    private void loadDefaultItems() {
        synchronized (mProvisioningInfo) {
            loadIntegerItem(ProvisioningManager.KEY_VOLTE_PROVISIONING_STATUS);
            loadIntegerItem(ProvisioningManager.KEY_VOICE_OVER_WIFI_ROAMING_ENABLED_OVERRIDE);
            loadIntegerItem(ProvisioningManager.KEY_VOICE_OVER_WIFI_MODE_OVERRIDE);
            loadIntegerItem(ProvisioningManager.KEY_VOICE_OVER_WIFI_ENABLED_OVERRIDE);
            loadIntegerItem(ProvisioningManager.KEY_LTE_THRESHOLD_1);
            loadIntegerItem(ProvisioningManager.KEY_LTE_THRESHOLD_2);
            loadIntegerItem(ProvisioningManager.KEY_LTE_THRESHOLD_3);
            loadIntegerItem(ProvisioningManager.KEY_1X_THRESHOLD);
            loadIntegerItem(ProvisioningManager.KEY_WIFI_THRESHOLD_A);
            loadIntegerItem(ProvisioningManager.KEY_WIFI_THRESHOLD_B);
            loadIntegerItem(ProvisioningManager.KEY_LTE_EPDG_TIMER_SEC);
            loadIntegerItem(ProvisioningManager.KEY_WIFI_EPDG_TIMER_SEC);
            loadIntegerItem(ProvisioningManager.KEY_1X_EPDG_TIMER_SEC);
            loadStringItem(ProvisioningManager.KEY_VOICE_OVER_WIFI_ENTITLEMENT_ID);
            if (mProvisioningInfo.isUpdated()) {
                mQnsProvisioningHandler.sendNotifyProvisioningInfoChanged();
            }
        }
    }

    private void loadIntegerItem(int item) {
        try {
            int value = mProvisioningManager.getProvisioningIntValue(item);
            log("loadIntegerItem item:" + item + " value:" + value);
            mProvisioningInfo.setIntegerItem(item, value);
        } catch (Exception e) {
            loge("got exception e:" + e);
        }
    }

    private void loadStringItem(int item) {
        try {
            String value = mProvisioningManager.getProvisioningStringValue(item);
            log("loadStringItem item:" + item + " value:" + value);
            mProvisioningInfo.setStringItem(item, value);
        } catch (Exception e) {
            loge("got exception e:" + e);
        }
    }

    protected void log(String s) {
        Log.d(mLogTag, s);
    }

    protected void loge(String s) {
        Log.e(mLogTag, s);
    }

    static class QnsProvisioningInfo {

        private final ConcurrentHashMap<Integer, Integer> mIntegerItems;
        private final ConcurrentHashMap<Integer, String> mStringItems;
        private boolean mUpdated;

        QnsProvisioningInfo() {
            mIntegerItems = new ConcurrentHashMap<>();
            mStringItems = new ConcurrentHashMap<>();
            mUpdated = false;
        }

        QnsProvisioningInfo(QnsProvisioningInfo info) {
            mIntegerItems = new ConcurrentHashMap<>();
            mStringItems = new ConcurrentHashMap<>();
            mIntegerItems.putAll(info.mIntegerItems);
            mStringItems.putAll(info.mStringItems);
            mUpdated = info.mUpdated;
        }

        @Override
        public String toString() {
            return "QnsProvisioningInfo{"
                    + "mIntegerItems="
                    + mIntegerItems
                    + ", mStringItems="
                    + mStringItems
                    + ", mUpdated="
                    + mUpdated
                    + '}';
        }

        boolean hasItem(int item) {
            return mIntegerItems.get(item) != null || mStringItems.get(item) != null;
        }

        private void setIntegerItem(int item, int value) {
            if (value == ProvisioningManager.PROVISIONING_RESULT_UNKNOWN
                    || (!isValueZeroValidItem(item)
                            && value == ProvisioningManager.PROVISIONING_VALUE_DISABLED)) {
                if (mIntegerItems.remove(item) != null) {
                    markUpdated(true);
                }
                return;
            }
            if (getIntegerItem(item) != null && getIntegerItem(item) == value) {
                return;
            }
            mIntegerItems.put(item, value);
            markUpdated(true);
        }

        Integer getIntegerItem(int item) {
            return mIntegerItems.get(item);
        }

        private boolean isValueZeroValidItem(int key) {
            switch (key) {
                case ProvisioningManager.KEY_VOLTE_PROVISIONING_STATUS:
                case ProvisioningManager.KEY_VOICE_OVER_WIFI_ROAMING_ENABLED_OVERRIDE:
                case ProvisioningManager.KEY_VOICE_OVER_WIFI_MODE_OVERRIDE:
                case ProvisioningManager.KEY_VOICE_OVER_WIFI_ENABLED_OVERRIDE:
                case ProvisioningManager.KEY_VOICE_OVER_WIFI_ENTITLEMENT_ID:
                    return true;
                case ProvisioningManager.KEY_LTE_THRESHOLD_1:
                case ProvisioningManager.KEY_LTE_THRESHOLD_2:
                case ProvisioningManager.KEY_LTE_THRESHOLD_3:
                case ProvisioningManager.KEY_1X_THRESHOLD:
                case ProvisioningManager.KEY_WIFI_THRESHOLD_A:
                case ProvisioningManager.KEY_WIFI_THRESHOLD_B:
                case ProvisioningManager.KEY_LTE_EPDG_TIMER_SEC:
                case ProvisioningManager.KEY_WIFI_EPDG_TIMER_SEC:
                case ProvisioningManager.KEY_1X_EPDG_TIMER_SEC:
                    return false;
            }
            return true;
        }

        private void setStringItem(int item, String value) {
            if (ProvisioningManager.STRING_QUERY_RESULT_ERROR_GENERIC.equals(value)
                    || ProvisioningManager.STRING_QUERY_RESULT_ERROR_NOT_READY.equals(value)) {
                if (mStringItems.remove(item) != null) {
                    markUpdated(true);
                }
                return;
            }
            if (TextUtils.equals(value, getStringItem(item))) {
                return;
            }
            mStringItems.put(item, value);
            markUpdated(true);
        }

        String getStringItem(int item) {
            return mStringItems.get(item);
        }

        void clear() {
            mIntegerItems.clear();
            mStringItems.clear();
            markUpdated(false);
        }

        void markUpdated(boolean bUpdated) {
            mUpdated = bUpdated;
        }

        boolean isUpdated() {
            return mUpdated;
        }

        boolean equalsIntegerItem(QnsProvisioningInfo info, int key) {
            Integer my = getIntegerItem(key);
            Integer other = info.getIntegerItem(key);
            if (my == null && other == null) {
                return true;
            } else if (my != null && other != null) {
                int myvalue = my;
                int othervalue = other;
                return myvalue == othervalue;
            }
            return false;
        }
    }

    private class QnsProvisioningCallback extends ProvisioningManager.Callback {
        /** Constructor */
        QnsProvisioningCallback() {}

        /**
         * Called when a provisioning item has changed.
         *
         * @param item the IMS provisioning key constant, as defined by the OEM.
         * @param value the new integer value of the IMS provisioning key.
         */
        @Override
        public void onProvisioningIntChanged(int item, int value) {
            synchronized (mProvisioningInfo) {
                mProvisioningInfo.setIntegerItem(item, value);
                if (mProvisioningInfo.isUpdated()) {
                    mQnsProvisioningHandler.sendNotifyProvisioningInfoChanged();
                }
            }
        }

        /**
         * Called when a provisioning item has changed.
         *
         * @param item the IMS provisioning key constant, as defined by the OEM.
         * @param value the new String value of the IMS configuration constant.
         */
        @Override
        public void onProvisioningStringChanged(int item, String value) {
            synchronized (mProvisioningInfo) {
                mProvisioningInfo.setStringItem(item, value);
                if (mProvisioningInfo.isUpdated()) {
                    mQnsProvisioningHandler.sendNotifyProvisioningInfoChanged();
                }
            }
        }
    }

    @VisibleForTesting
    class QnsProvisioningHandler extends Handler {
        private int mRetryRegisterProvisioningCallbackCount;

        QnsProvisioningHandler(Looper looper) {
            super(looper);
            mRetryRegisterProvisioningCallbackCount = REG_CALLBACK_RETRY;
        }

        void resetRetryRegisterProvisioningCallbackCount() {
            mRetryRegisterProvisioningCallbackCount = REG_CALLBACK_RETRY;
        }

        @Override
        public void handleMessage(Message message) {
            log("message what:" + message.what);
            QnsAsyncResult ar = (QnsAsyncResult) message.obj;
            switch (message.what) {
                case EVENT_IMS_STATE_CHANGED:
                    if (ar != null) {
                        QnsImsManager.ImsState state = (QnsImsManager.ImsState) ar.mResult;
                        if (state.isImsAvailable()) {
                            log("ImsState is changed to available");
                            unregisterProvisioningCallback();
                            resetRetryRegisterProvisioningCallbackCount();
                            registerProvisioningCallback();
                        } else {
                            log("ImsState is changed to unavailable");
                            clearLastProvisioningInfo();
                        }
                    }
                    break;
                case EVENT_REGISTER_PROVISIONING_CALLBACK:
                    registerProvisioningCallback();
                    break;
                case EVENT_CALLBACK_REGISTERED:
                    loadDefaultItems();
                    break;
                case EVENT_NOTIFY_PROVISION_INFO_CHANGED:
                    synchronized (mProvisioningInfo) {
                        if (mProvisioningInfo.isUpdated()) {
                            notifyProvisioningItemInfoChanged(
                                    new QnsProvisioningInfo(mProvisioningInfo));
                            mProvisioningInfo.markUpdated(false);
                        }
                    }
                    break;
                default:
                    break;
            }
        }

        void sendRegisterProvisioningCallback() {
            mRetryRegisterProvisioningCallbackCount--;
            if (mRetryRegisterProvisioningCallbackCount > 0) {
                removeMessages(EVENT_REGISTER_PROVISIONING_CALLBACK);
                Message msg = obtainMessage(EVENT_REGISTER_PROVISIONING_CALLBACK);
                sendMessageDelayed(msg, REG_CALLBACK_DELAY);
            }
        }

        void sendProvisioningCallbackRegistered() {
            removeMessages(EVENT_REGISTER_PROVISIONING_CALLBACK);
            resetRetryRegisterProvisioningCallbackCount();
            Message msg = obtainMessage(EVENT_CALLBACK_REGISTERED);
            sendMessage(msg);
        }

        void sendNotifyProvisioningInfoChanged() {
            removeMessages(EVENT_NOTIFY_PROVISION_INFO_CHANGED);
            Message msg = obtainMessage(EVENT_NOTIFY_PROVISION_INFO_CHANGED);
            sendMessageDelayed(msg, 100);
        }
    }

    private void clearLastProvisioningInfo() {
        synchronized (mProvisioningInfo) {
            mProvisioningInfo.clear();
        }
    }
}
