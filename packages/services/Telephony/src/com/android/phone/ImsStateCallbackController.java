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

package com.android.phone;

import static android.telephony.ims.ImsStateCallback.REASON_IMS_SERVICE_DISCONNECTED;
import static android.telephony.ims.ImsStateCallback.REASON_IMS_SERVICE_NOT_READY;
import static android.telephony.ims.ImsStateCallback.REASON_NO_IMS_SERVICE_CONFIGURED;
import static android.telephony.ims.ImsStateCallback.REASON_SUBSCRIPTION_INACTIVE;
import static android.telephony.ims.ImsStateCallback.REASON_UNKNOWN_PERMANENT_ERROR;
import static android.telephony.ims.ImsStateCallback.REASON_UNKNOWN_TEMPORARY_ERROR;
import static android.telephony.ims.feature.ImsFeature.FEATURE_MMTEL;
import static android.telephony.ims.feature.ImsFeature.FEATURE_RCS;
import static android.telephony.ims.feature.ImsFeature.STATE_READY;
import static android.telephony.ims.feature.ImsFeature.STATE_UNAVAILABLE;

import static com.android.ims.FeatureConnector.UNAVAILABLE_REASON_DISCONNECTED;
import static com.android.ims.FeatureConnector.UNAVAILABLE_REASON_IMS_UNSUPPORTED;
import static com.android.ims.FeatureConnector.UNAVAILABLE_REASON_NOT_READY;
import static com.android.ims.FeatureConnector.UNAVAILABLE_REASON_SERVER_UNAVAILABLE;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyRegistryManager;
import android.telephony.ims.feature.ImsFeature;
import android.util.LocalLog;
import android.util.Log;
import android.util.SparseArray;

import com.android.ims.FeatureConnector;
import com.android.ims.ImsManager;
import com.android.ims.RcsFeatureManager;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.IImsStateCallback;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConfigurationManager;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.ims.ImsResolver;
import com.android.internal.telephony.util.HandlerExecutor;
import com.android.internal.util.IndentingPrintWriter;
import com.android.services.telephony.rcs.RcsFeatureController;
import com.android.telephony.Rlog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Implementation of the controller managing {@link ImsStateCallback}s
 */
public class ImsStateCallbackController {
    private static final String TAG = "ImsStateCallbackController";
    private static final boolean VDBG = false;
    private static final int LOG_SIZE = 50;

    /**
     * Create a FeatureConnector for this class to use to connect to an ImsManager.
     */
    @VisibleForTesting
    public interface MmTelFeatureConnectorFactory {
        /**
         * Create a FeatureConnector for this class to use to connect to an ImsManager.
         * @param listener will receive ImsManager instance.
         * @param executor that the Listener callbacks will be called on.
         * @return A FeatureConnector
         */
        FeatureConnector<ImsManager> create(Context context, int slotId,
                String logPrefix, FeatureConnector.Listener<ImsManager> listener,
                Executor executor);
    }

    /**
     * Create a FeatureConnector for this class to use to connect to an RcsFeatureManager.
     */
    @VisibleForTesting
    public interface RcsFeatureConnectorFactory {
        /**
         * Create a FeatureConnector for this class to use to connect to an RcsFeatureManager.
         * @param listener will receive RcsFeatureManager instance.
         * @param executor that the Listener callbacks will be called on.
         * @return A FeatureConnector
         */
        FeatureConnector<RcsFeatureManager> create(Context context, int slotId,
                FeatureConnector.Listener<RcsFeatureManager> listener,
                Executor executor, String logPrefix);
    }

    /** Indicates that the state is not valid, used in ExternalRcsFeatureState only */
    private static final int STATE_UNKNOWN = -1;

    /** The unavailable reason of ImsFeature is not initialized */
    private static final int NOT_INITIALIZED = -1;
    /** The ImsFeature is available. */
    private static final int AVAILABLE = 0;

    private static final int EVENT_SUB_CHANGED = 1;
    private static final int EVENT_REGISTER_CALLBACK = 2;
    private static final int EVENT_UNREGISTER_CALLBACK = 3;
    private static final int EVENT_CARRIER_CONFIG_CHANGED = 4;
    private static final int EVENT_EXTERNAL_RCS_STATE_CHANGED = 5;
    private static final int EVENT_MSIM_CONFIGURATION_CHANGE = 6;

    private static ImsStateCallbackController sInstance;
    private static final LocalLog sLocalLog = new LocalLog(LOG_SIZE);

    /**
     * get the instance
     */
    public static ImsStateCallbackController getInstance() {
        synchronized (ImsStateCallbackController.class) {
            return sInstance;
        }
    }

    private final PhoneGlobals mApp;
    private final Handler mHandler;
    private final ImsResolver mImsResolver;
    private final SparseArray<MmTelFeatureListener> mMmTelFeatureListeners = new SparseArray<>();
    private final SparseArray<RcsFeatureListener> mRcsFeatureListeners = new SparseArray<>();

    // Container to store ImsManager instance by subId
    private final ConcurrentHashMap<Integer, ImsManager> mSubIdToImsManagerCache =
            new ConcurrentHashMap<>();

    private final SubscriptionManager mSubscriptionManager;
    private final TelephonyRegistryManager mTelephonyRegistryManager;
    private MmTelFeatureConnectorFactory mMmTelFeatureFactory;
    private RcsFeatureConnectorFactory mRcsFeatureFactory;

    private HashMap<IBinder, CallbackWrapper> mWrappers = new HashMap<>();

    private final Object mDumpLock = new Object();

    private int mNumSlots;

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) {
                return;
            }
            if (CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED.equals(intent.getAction())) {
                Bundle bundle = intent.getExtras();
                if (bundle == null) {
                    return;
                }
                int slotId = bundle.getInt(CarrierConfigManager.EXTRA_SLOT_INDEX,
                        SubscriptionManager.INVALID_PHONE_INDEX);
                int subId = bundle.getInt(CarrierConfigManager.EXTRA_SUBSCRIPTION_INDEX,
                        SubscriptionManager.INVALID_SUBSCRIPTION_ID);

                if (slotId <= SubscriptionManager.INVALID_SIM_SLOT_INDEX) {
                    loge("onReceive ACTION_CARRIER_CONFIG_CHANGED invalid slotId");
                    return;
                }

                if (subId <= SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                    loge("onReceive ACTION_CARRIER_CONFIG_CHANGED invalid subId");
                    //subscription changed will be notified by mSubChangedListener
                    return;
                }

                notifyCarrierConfigChanged(slotId);
            }
        }
    };

    private final SubscriptionManager.OnSubscriptionsChangedListener mSubChangedListener =
            new SubscriptionManager.OnSubscriptionsChangedListener() {
        @Override
        public void onSubscriptionsChanged() {
            if (!mHandler.hasMessages(EVENT_SUB_CHANGED)) {
                mHandler.sendEmptyMessage(EVENT_SUB_CHANGED);
            }
        }
    };

    private final class MyHandler extends Handler {
        MyHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            if (VDBG) logv("handleMessage: " + msg);
            synchronized (mDumpLock) {
                switch (msg.what) {
                    case EVENT_SUB_CHANGED:
                        onSubChanged();
                        break;

                    case EVENT_REGISTER_CALLBACK:
                        onRegisterCallback((ImsStateCallbackController.CallbackWrapper) msg.obj);
                        break;

                    case EVENT_UNREGISTER_CALLBACK:
                        onUnregisterCallback((IImsStateCallback) msg.obj);
                        break;

                    case EVENT_CARRIER_CONFIG_CHANGED:
                        onCarrierConfigChanged(msg.arg1);
                        break;

                    case EVENT_EXTERNAL_RCS_STATE_CHANGED:
                        if (msg.obj == null) break;
                        onExternalRcsStateChanged((ExternalRcsFeatureState) msg.obj);
                        break;

                    case EVENT_MSIM_CONFIGURATION_CHANGE:
                        AsyncResult result = (AsyncResult) msg.obj;
                        Integer numSlots = (Integer) result.result;
                        if (numSlots == null) {
                            Log.w(TAG, "msim config change with null num slots");
                            break;
                        }
                        updateFeatureControllerSize(numSlots);
                        break;

                    default:
                        loge("Unhandled event " + msg.what);
                }
            }
        }
    }

    private final class MmTelFeatureListener implements FeatureConnector.Listener<ImsManager> {
        private FeatureConnector<ImsManager> mConnector;
        private int mSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        private int mState = STATE_UNAVAILABLE;
        private int mReason = REASON_IMS_SERVICE_DISCONNECTED;

        /*
         * Remember the last return of verifyImsMmTelConfigured().
         * true means ImsResolver found an IMS package for FEATURE_MMTEL.
         *
         * mReason is updated through connectionUnavailable triggered by ImsResolver.
         * mHasConfig is update through notifyConfigChanged triggered by mReceiver.
         * mHasConfig can be a redundancy of (mReason == REASON_NO_IMS_SERVICE_CONFIGURED).
         * However, when a carrier config changes, we are not sure the order
         * of execution of connectionUnavailable and notifyConfigChanged.
         * So, it's safe to use a separated state to retain it.
         * We assume mHasConfig is true, until it's determined explicitly.
         */
        private boolean mHasConfig = true;

        private int mSlotId = -1;
        private String mLogPrefix = "";

        MmTelFeatureListener(int slotId) {
            mSlotId = slotId;
            mLogPrefix = "[" + slotId + ", MMTEL] ";
            if (VDBG) logv(mLogPrefix + "created");

            mConnector = mMmTelFeatureFactory.create(
                    mApp, slotId, TAG, this, new HandlerExecutor(mHandler));
            mConnector.connect();
        }

        void setSubId(int subId) {
            if (VDBG) logv(mLogPrefix + "setSubId mSubId=" + mSubId + ", subId=" + subId);
            if (mSubId == subId) return;
            logd(mLogPrefix + "setSubId changed subId=" + subId);

            // subId changed from valid to invalid
            if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                if (VDBG) logv(mLogPrefix + "setSubId remove ImsManager " + mSubId);
                // remove ImsManager reference associated with subId
                mSubIdToImsManagerCache.remove(mSubId);
            }

            mSubId = subId;
        }

        void destroy() {
            if (VDBG) logv(mLogPrefix + "destroy");
            mConnector.disconnect();
            mConnector = null;
        }

        @Override
        public void connectionReady(ImsManager manager, int subId) {
            logd(mLogPrefix + "connectionReady " + subId);

            mSubId = subId;
            if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) return;

            // store ImsManager reference associated with subId
            if (manager != null) {
                if (VDBG) logv(mLogPrefix + "connectionReady add ImsManager " + subId);
                mSubIdToImsManagerCache.put(subId, manager);
            }

            mState = STATE_READY;
            mReason = AVAILABLE;
            mHasConfig = true;
            onFeatureStateChange(mSubId, FEATURE_MMTEL, mState, mReason);
        }

        @Override
        public void connectionUnavailable(int reason) {
            logd(mLogPrefix + "connectionUnavailable reason=" + connectorReasonToString(reason));

            reason = convertReasonType(reason);
            if (mReason == reason) return;

            // remove ImsManager reference associated with subId
            if (VDBG) logv(mLogPrefix + "connectionUnavailable remove ImsManager " + mSubId);
            mSubIdToImsManagerCache.remove(mSubId);

            connectionUnavailableInternal(reason);
        }

        private void connectionUnavailableInternal(int reason) {
            mState = STATE_UNAVAILABLE;
            mReason = reason;

            /* If having no IMS package for MMTEL,
             * discard the reason except REASON_NO_IMS_SERVICE_CONFIGURED. */
            if (!mHasConfig && reason != REASON_NO_IMS_SERVICE_CONFIGURED) return;

            onFeatureStateChange(mSubId, FEATURE_MMTEL, mState, mReason);
        }

        void notifyConfigChanged(boolean hasConfig) {
            if (mHasConfig == hasConfig) return;

            logd(mLogPrefix + "notifyConfigChanged " + hasConfig);

            mHasConfig = hasConfig;
            if (hasConfig) {
                // REASON_NO_IMS_SERVICE_CONFIGURED is already reported to the clients,
                // since there is no configuration of IMS package for MMTEL.
                // Now, a carrier configuration change is notified and
                // the response from ImsResolver is changed from false to true.
                if (mState != STATE_READY) {
                    if (mReason == REASON_NO_IMS_SERVICE_CONFIGURED) {
                        // In this case, notify clients the reason, REASON_DISCONNCTED,
                        // to update the state.
                        connectionUnavailable(UNAVAILABLE_REASON_DISCONNECTED);
                    } else {
                        // ImsResolver and ImsStateCallbackController run with different Looper.
                        // In this case, FeatureConnectorListener is updated ahead of this.
                        // But, connectionUnavailable didn't notify clients since mHasConfig is
                        // false. So, notify clients here.
                        connectionUnavailableInternal(mReason);
                    }
                }
            } else {
                // FeatureConnector doesn't report UNAVAILABLE_REASON_IMS_UNSUPPORTED,
                // so report the reason here.
                connectionUnavailable(UNAVAILABLE_REASON_IMS_UNSUPPORTED);
            }
        }

        // called from onRegisterCallback
        boolean notifyState(CallbackWrapper wrapper) {
            if (VDBG) logv(mLogPrefix + "notifyState subId=" + wrapper.mSubId);

            return wrapper.notifyState(mSubId, FEATURE_MMTEL, mState, mReason);
        }

        void dump(IndentingPrintWriter pw) {
            pw.println("Listener={slotId=" + mSlotId
                    + ", subId=" + mSubId
                    + ", state=" + ImsFeature.STATE_LOG_MAP.get(mState)
                    + ", reason=" + imsStateReasonToString(mReason)
                    + ", hasConfig=" + mHasConfig
                    + "}");
        }
    }

    private final class RcsFeatureListener implements FeatureConnector.Listener<RcsFeatureManager> {
        private FeatureConnector<RcsFeatureManager> mConnector;
        private int mSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        private int mState = STATE_UNAVAILABLE;
        private int mReason = REASON_IMS_SERVICE_DISCONNECTED;

        /*
         * Remember the last return of verifyImsMmTelConfigured().
         * true means ImsResolver found an IMS package for FEATURE_RCS.
         *
         * mReason is updated through connectionUnavailable triggered by ImsResolver.
         * mHasConfig is update through notifyConfigChanged triggered by mReceiver,
         * and notifyExternalRcsState which triggered by TelephonyRcsService refers it.
         * mHasConfig can be a redundancy of (mReason == REASON_NO_IMS_SERVICE_CONFIGURED).
         * However, when a carrier config changes, we are not sure the order
         * of execution of connectionUnavailable, notifyConfigChanged and notifyExternalRcsState.
         * So, it's safe to use a separated state to retain it.
         * We assume mHasConfig is true, until it's determined explicitly.
         */
        private boolean mHasConfig = true;

        /*
         * TelephonyRcsService doesn’t try to connect to RcsFeature if there is no active feature
         * for a given subscription. The active features are declared by carrier configs and
         * configuration resources. The APIs of ImsRcsManager and SipDelegateManager are available
         * only when the RcsFeatureController has a STATE_READY state connection.
         * This configuration is different from the configuration of IMS package for RCS.
         * ImsStateCallbackController's FeatureConnectorListener can be STATE_READY state,
         * even in case there is no active RCS feature. But Manager's APIs throws exception.
         *
         * For RCS, in addition to mHasConfig, the sate of TelephonyRcsService and
         * RcsFeatureConnector will be traced to determine the state to be notified to clients.
         */
        private ExternalRcsFeatureState mExternalState = null;

        private int mSlotId = -1;
        private String mLogPrefix = "";

        RcsFeatureListener(int slotId) {
            mSlotId = slotId;
            mLogPrefix = "[" + slotId + ", RCS] ";
            if (VDBG) logv(mLogPrefix + "created");

            mConnector = mRcsFeatureFactory.create(
                    mApp, slotId, this, new HandlerExecutor(mHandler), TAG);
            mConnector.connect();
        }

        void setSubId(int subId) {
            if (VDBG) logv(mLogPrefix + "setSubId mSubId=" + mSubId + ", subId=" + subId);
            if (mSubId == subId) return;
            logd(mLogPrefix + "setSubId changed subId=" + subId);

            mSubId = subId;
        }

        void destroy() {
            if (VDBG) logv(mLogPrefix + "destroy");

            mConnector.disconnect();
            mConnector = null;
        }

        @Override
        public void connectionReady(RcsFeatureManager manager, int subId) {
            logd(mLogPrefix + "connectionReady " + subId);

            mSubId = subId;
            if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) return;

            mState = STATE_READY;
            mReason = AVAILABLE;
            mHasConfig = true;

            if (mExternalState != null && mExternalState.isReady()) {
                onFeatureStateChange(mSubId, FEATURE_RCS, mState, mReason);
            }
        }

        @Override
        public void connectionUnavailable(int reason) {
            logd(mLogPrefix + "connectionUnavailable reason=" + connectorReasonToString(reason));

            reason = convertReasonType(reason);
            if (mReason == reason) return;

            connectionUnavailableInternal(reason);
        }

        private void connectionUnavailableInternal(int reason) {
            mState = STATE_UNAVAILABLE;
            mReason = reason;

            /* If having no IMS package for RCS,
             * dicard the reason except REASON_NO_IMS_SERVICE_CONFIGURED. */
            if (!mHasConfig && reason != REASON_NO_IMS_SERVICE_CONFIGURED) return;

            if (mExternalState == null && reason != REASON_NO_IMS_SERVICE_CONFIGURED) {
                // Wait until TelephonyRcsService notifies its state.
                return;
            }

            if (mExternalState != null && !mExternalState.hasActiveFeatures()) {
                // notifyExternalRcsState has notified REASON_NO_IMS_SERVICE_CONFIGURED already
                // ignore it
                return;
            }

            if ((mExternalState != null && mExternalState.hasActiveFeatures())
                    || mReason == REASON_NO_IMS_SERVICE_CONFIGURED) {
                onFeatureStateChange(mSubId, FEATURE_RCS, mState, mReason);
            }
        }

        void notifyConfigChanged(boolean hasConfig) {
            if (mHasConfig == hasConfig) return;

            logd(mLogPrefix + "notifyConfigChanged " + hasConfig);

            mHasConfig = hasConfig;
            if (hasConfig) {
                // REASON_NO_IMS_SERVICE_CONFIGURED is already reported to the clients,
                // since there is no configuration of IMS package for RCS.
                // Now, a carrier configuration change is notified and
                // the response from ImsResolver is changed from false to true.
                if (mState != STATE_READY) {
                    if (mReason == REASON_NO_IMS_SERVICE_CONFIGURED) {
                        // In this case, notify clients the reason, REASON_DISCONNCTED,
                        // to update the state.
                        connectionUnavailable(UNAVAILABLE_REASON_DISCONNECTED);
                    } else {
                        // ImsResolver and ImsStateCallbackController run with different Looper.
                        // In this case, FeatureConnectorListener is updated ahead of this.
                        // But, connectionUnavailable didn't notify clients since mHasConfig is
                        // false. So, notify clients here.
                        connectionUnavailableInternal(mReason);
                    }
                }
            } else {
                // FeatureConnector doesn't report UNAVAILABLE_REASON_IMS_UNSUPPORTED,
                // so report the reason here.
                connectionUnavailable(UNAVAILABLE_REASON_IMS_UNSUPPORTED);
            }
        }

        void notifyExternalRcsState(ExternalRcsFeatureState fs) {
            if (VDBG) {
                logv(mLogPrefix + "notifyExternalRcsState"
                        + " state=" + (fs.mState == STATE_UNKNOWN
                                ? "" : ImsFeature.STATE_LOG_MAP.get(fs.mState))
                        + ", reason=" + imsStateReasonToString(fs.mReason));
            }

            ExternalRcsFeatureState oldFs = mExternalState;
            // External state is from TelephonyRcsService while a feature is added or removed.
            if (fs.mState == STATE_UNKNOWN) {
                if (oldFs != null) fs.mState = oldFs.mState;
                else fs.mState = STATE_UNAVAILABLE;
            }

            mExternalState = fs;

            // No IMS package found.
            // REASON_NO_IMS_SERVICE_CONFIGURED is notified to clients already.
            if (!mHasConfig) return;

            if (fs.hasActiveFeatures()) {
                if (mState == STATE_READY) {
                    if ((oldFs == null || !oldFs.isReady()) && fs.isReady()) {
                        // it is waiting RcsFeatureConnector's notification.
                        // notify clients here.
                        onFeatureStateChange(mSubId, FEATURE_RCS, mState, mReason);
                    } else if (!fs.isReady()) {
                        // Wait RcsFeatureConnector's notification
                    } else {
                        // ignore duplicated notification
                    }
                }
            } else {
                // notify only once
                if (oldFs == null || oldFs.hasActiveFeatures()) {
                    if (mReason != REASON_NO_IMS_SERVICE_CONFIGURED) {
                        onFeatureStateChange(
                                mSubId, FEATURE_RCS, STATE_UNAVAILABLE,
                                REASON_NO_IMS_SERVICE_CONFIGURED);
                    }
                } else {
                    // ignore duplicated notification
                }
            }
        }

        // called from onRegisterCallback
        boolean notifyState(CallbackWrapper wrapper) {
            if (VDBG) logv(mLogPrefix + "notifyState subId=" + wrapper.mSubId);

            if (mHasConfig) {
                if (mExternalState == null) {
                    // Wait until TelephonyRcsService notifies its state.
                    return wrapper.notifyState(mSubId, FEATURE_RCS, STATE_UNAVAILABLE,
                            REASON_IMS_SERVICE_DISCONNECTED);
                } else if (!mExternalState.hasActiveFeatures()) {
                    return wrapper.notifyState(mSubId, FEATURE_RCS, STATE_UNAVAILABLE,
                            REASON_NO_IMS_SERVICE_CONFIGURED);
                }
            }

            return wrapper.notifyState(mSubId, FEATURE_RCS, mState, mReason);
        }

        void dump(IndentingPrintWriter pw) {
            pw.println("Listener={slotId=" + mSlotId
                    + ", subId=" + mSubId
                    + ", state=" + ImsFeature.STATE_LOG_MAP.get(mState)
                    + ", reason=" + imsStateReasonToString(mReason)
                    + ", hasConfig=" + mHasConfig
                    + ", isReady=" + (mExternalState == null ? false : mExternalState.isReady())
                    + ", hasFeatures=" + (mExternalState == null ? false
                            : mExternalState.hasActiveFeatures())
                    + "}");
        }
    }

    /**
     * A wrapper class for the callback registered
     */
    private static class CallbackWrapper {
        private final int mSubId;
        private final int mRequiredFeature;
        private final IImsStateCallback mCallback;
        private final IBinder mBinder;
        private final String mCallingPackage;
        private int mLastReason = NOT_INITIALIZED;

        CallbackWrapper(int subId, int feature, IImsStateCallback callback,
                String callingPackage) {
            mSubId = subId;
            mRequiredFeature = feature;
            mCallback = callback;
            mBinder = callback.asBinder();
            mCallingPackage = callingPackage;
        }

        /**
         * @return false when accessing callback binder throws an Exception.
         * That means the callback binder is not valid any longer.
         * The death of remote process can cause this.
         * This instance shall be removed from the list.
         */
        boolean notifyState(int subId, int feature, int state, int reason) {
            if (VDBG) {
                logv("CallbackWrapper notifyState subId=" + subId
                        + ", feature=" + ImsFeature.FEATURE_LOG_MAP.get(feature)
                        + ", state=" + ImsFeature.STATE_LOG_MAP.get(state)
                        + ", reason=" + imsStateReasonToString(reason));
            }

            try {
                if (state == STATE_READY) {
                    mCallback.onAvailable();
                } else {
                    mCallback.onUnavailable(reason);
                }
                mLastReason = reason;
            } catch (Exception e) {
                loge("CallbackWrapper notifyState e=" + e);
                return false;
            }

            return true;
        }

        void notifyInactive() {
            logd("CallbackWrapper notifyInactive subId=" + mSubId);

            try {
                mCallback.onUnavailable(REASON_SUBSCRIPTION_INACTIVE);
            } catch (Exception e) {
                // ignored
            }
        }

        void dump(IndentingPrintWriter pw) {
            pw.println("CallbackWrapper={subId=" + mSubId
                    + ", feature=" + ImsFeature.FEATURE_LOG_MAP.get(mRequiredFeature)
                    + ", reason=" + imsStateReasonToString(mLastReason)
                    + ", pkg=" + mCallingPackage
                    + "}");
        }
    }

    private static class ExternalRcsFeatureState {
        private int mSlotId;
        private int mState = STATE_UNAVAILABLE;
        private int mReason = NOT_INITIALIZED;

        ExternalRcsFeatureState(int slotId, int state, int reason) {
            mSlotId = slotId;
            mState = state;
            mReason = reason;
        }

        boolean hasActiveFeatures() {
            return mReason != REASON_NO_IMS_SERVICE_CONFIGURED;
        }

        boolean isReady() {
            return mState == STATE_READY;
        }
    }

    /**
     * create an instance
     */
    public static ImsStateCallbackController make(PhoneGlobals app, int numSlots) {
        synchronized (ImsStateCallbackController.class) {
            if (sInstance == null) {
                logd("ImsStateCallbackController created");

                HandlerThread handlerThread = new HandlerThread(TAG);
                handlerThread.start();
                sInstance = new ImsStateCallbackController(app, handlerThread.getLooper(), numSlots,
                        ImsManager::getConnector, RcsFeatureManager::getConnector,
                        ImsResolver.getInstance());
            }
        }
        return sInstance;
    }

    @VisibleForTesting
    public ImsStateCallbackController(PhoneGlobals app, Looper looper, int numSlots,
            MmTelFeatureConnectorFactory mmTelFactory, RcsFeatureConnectorFactory rcsFactory,
            ImsResolver imsResolver) {
        mApp = app;
        mHandler = new MyHandler(looper);
        mImsResolver = imsResolver;
        mSubscriptionManager = mApp.getSystemService(SubscriptionManager.class);
        mTelephonyRegistryManager = mApp.getSystemService(TelephonyRegistryManager.class);
        mMmTelFeatureFactory = mmTelFactory;
        mRcsFeatureFactory = rcsFactory;

        updateFeatureControllerSize(numSlots);

        mTelephonyRegistryManager.addOnSubscriptionsChangedListener(
                mSubChangedListener, mHandler::post);

        PhoneConfigurationManager.registerForMultiSimConfigChange(mHandler,
                EVENT_MSIM_CONFIGURATION_CHANGE, null);

        mApp.registerReceiver(mReceiver, new IntentFilter(
                CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED));

        onSubChanged();
    }

    /**
     * Update the number of {@link RcsFeatureController}s that are created based on the number of
     * active slots on the device.
     */
    @VisibleForTesting
    public void updateFeatureControllerSize(int newNumSlots) {
        if (mNumSlots != newNumSlots) {
            logd("updateFeatures: oldSlots=" + mNumSlots
                    + ", newNumSlots=" + newNumSlots);
            if (mNumSlots < newNumSlots) {
                for (int i = mNumSlots; i < newNumSlots; i++) {
                    MmTelFeatureListener m = new MmTelFeatureListener(i);
                    mMmTelFeatureListeners.put(i, m);
                    RcsFeatureListener r = new RcsFeatureListener(i);
                    mRcsFeatureListeners.put(i, r);
                }
            } else {
                for (int i = (mNumSlots - 1); i > (newNumSlots - 1); i--) {
                    MmTelFeatureListener m = mMmTelFeatureListeners.get(i);
                    if (m != null) {
                        mMmTelFeatureListeners.remove(i);
                        m.destroy();
                    }
                    RcsFeatureListener r = mRcsFeatureListeners.get(i);
                    if (r != null) {
                        mRcsFeatureListeners.remove(i);
                        r.destroy();
                    }
                }
            }
        }
        mNumSlots = newNumSlots;
    }

    /**
     * Dependencies for testing.
     */
    @VisibleForTesting
    public void onSubChanged() {
        for (int i = 0; i < mMmTelFeatureListeners.size(); i++) {
            MmTelFeatureListener l = mMmTelFeatureListeners.valueAt(i);
            l.setSubId(getSubId(i));
        }

        for (int i = 0; i < mRcsFeatureListeners.size(); i++) {
            RcsFeatureListener l = mRcsFeatureListeners.valueAt(i);
            l.setSubId(getSubId(i));
        }

        if (mWrappers.size() == 0) return;

        ArrayList<IBinder> inactiveCallbacks = new ArrayList<>();
        final int[] activeSubs = mSubscriptionManager.getActiveSubscriptionIdList();

        if (VDBG) logv("onSubChanged activeSubs=" + Arrays.toString(activeSubs));

        // Remove callbacks for inactive subscriptions
        for (IBinder binder : mWrappers.keySet()) {
            CallbackWrapper wrapper = mWrappers.get(binder);
            if (wrapper != null) {
                if (!isActive(activeSubs, wrapper.mSubId)) {
                    // inactive subscription
                    inactiveCallbacks.add(binder);
                }
            } else {
                // unexpected, remove it
                inactiveCallbacks.add(binder);
            }
        }
        removeInactiveCallbacks(inactiveCallbacks, "onSubChanged");
    }

    private void onFeatureStateChange(int subId, int feature, int state, int reason) {
        if (VDBG) {
            logv("onFeatureStateChange subId=" + subId
                    + ", feature=" + ImsFeature.FEATURE_LOG_MAP.get(feature)
                    + ", state=" + ImsFeature.STATE_LOG_MAP.get(state)
                    + ", reason=" + imsStateReasonToString(reason));
        }

        ArrayList<IBinder> inactiveCallbacks = new ArrayList<>();
        mWrappers.values().forEach(wrapper -> {
            if (subId == wrapper.mSubId
                    && feature == wrapper.mRequiredFeature
                    && !wrapper.notifyState(subId, feature, state, reason)) {
                // callback has exception, remove it
                inactiveCallbacks.add(wrapper.mBinder);
            }
        });
        removeInactiveCallbacks(inactiveCallbacks, "onFeatureStateChange");
    }

    private void onRegisterCallback(CallbackWrapper wrapper) {
        if (wrapper == null) return;

        if (VDBG) logv("onRegisterCallback before size=" + mWrappers.size());
        if (VDBG) {
            logv("onRegisterCallback subId=" + wrapper.mSubId
                    + ", feature=" + wrapper.mRequiredFeature);
        }

        // Not sure the following case can happen or not:
        // step1) Subscription changed
        // step2) ImsStateCallbackController not processed onSubChanged yet
        // step3) Client registers with a strange subId
        // The validity of the subId is checked PhoneInterfaceManager#registerImsStateCallback.
        // So, register the wrapper here before trying to notifyState.
        // TODO: implement the recovery for this case, notifying the current reson, in onSubChanged
        mWrappers.put(wrapper.mBinder, wrapper);

        if (wrapper.mRequiredFeature == FEATURE_MMTEL) {
            for (int i = 0; i < mMmTelFeatureListeners.size(); i++) {
                if (wrapper.mSubId == getSubId(i)) {
                    MmTelFeatureListener l = mMmTelFeatureListeners.valueAt(i);
                    if (!l.notifyState(wrapper)) {
                        mWrappers.remove(wrapper.mBinder);
                    }
                    break;
                }
            }
        } else if (wrapper.mRequiredFeature == FEATURE_RCS) {
            for (int i = 0; i < mRcsFeatureListeners.size(); i++) {
                if (wrapper.mSubId == getSubId(i)) {
                    RcsFeatureListener l = mRcsFeatureListeners.valueAt(i);
                    if (!l.notifyState(wrapper)) {
                        mWrappers.remove(wrapper.mBinder);
                    }
                    break;
                }
            }
        }

        if (VDBG) logv("onRegisterCallback after size=" + mWrappers.size());
    }

    private void onUnregisterCallback(IImsStateCallback cb) {
        if (cb == null) return;
        mWrappers.remove(cb.asBinder());
    }

    private void onCarrierConfigChanged(int slotId) {
        if (slotId >= mNumSlots) {
            logd("onCarrierConfigChanged invalid slotId "
                    + slotId + ", mNumSlots=" + mNumSlots);
            return;
        }

        logv("onCarrierConfigChanged slotId=" + slotId);

        boolean hasConfig = verifyImsMmTelConfigured(slotId);
        if (slotId < mMmTelFeatureListeners.size()) {
            MmTelFeatureListener listener = mMmTelFeatureListeners.valueAt(slotId);
            listener.notifyConfigChanged(hasConfig);
        }

        hasConfig = verifyImsRcsConfigured(slotId);
        if (slotId < mRcsFeatureListeners.size()) {
            RcsFeatureListener listener = mRcsFeatureListeners.valueAt(slotId);
            listener.notifyConfigChanged(hasConfig);
        }
    }

    private void onExternalRcsStateChanged(ExternalRcsFeatureState fs) {
        logv("onExternalRcsStateChanged slotId=" + fs.mSlotId
                + ", state=" + (fs.mState == STATE_UNKNOWN
                        ? "" : ImsFeature.STATE_LOG_MAP.get(fs.mState))
                + ", reason=" + imsStateReasonToString(fs.mReason));

        RcsFeatureListener listener = mRcsFeatureListeners.get(fs.mSlotId);
        if (listener != null) {
            listener.notifyExternalRcsState(fs);
        } else {
            // unexpected state
            loge("onExternalRcsStateChanged slotId=" + fs.mSlotId + ", no listener.");
        }
    }

    /**
     * Interface to be notified from TelephonyRcsSerice and RcsFeatureController
     *
     * @param ready true if feature's state is STATE_READY. Valid only when it is true.
     * @param hasActiveFeatures true if the RcsFeatureController has active features.
     */
    public void notifyExternalRcsStateChanged(
            int slotId, boolean ready, boolean hasActiveFeatures) {
        int state = STATE_UNKNOWN;
        int reason = REASON_IMS_SERVICE_DISCONNECTED;

        if (ready) {
            // From RcsFeatureController
            state = STATE_READY;
            reason = AVAILABLE;
        } else if (!hasActiveFeatures) {
            // From TelephonyRcsService
            reason = REASON_NO_IMS_SERVICE_CONFIGURED;
            state = STATE_UNAVAILABLE;
        } else {
            // From TelephonyRcsService
            // TelephonyRcsService doesn't know the exact state of FeatureConnection.
            // Only when there is no feature, we can assume the state.
        }

        if (VDBG) {
            logv("notifyExternalRcsStateChanged slotId=" + slotId
                    + ", ready=" + ready
                    + ", hasActiveFeatures=" + hasActiveFeatures);
        }

        ExternalRcsFeatureState fs = new ExternalRcsFeatureState(slotId, state, reason);
        mHandler.sendMessage(mHandler.obtainMessage(EVENT_EXTERNAL_RCS_STATE_CHANGED, fs));
    }

    /**
     * Notifies carrier configuration has changed.
     */
    @VisibleForTesting
    public void notifyCarrierConfigChanged(int slotId) {
        if (VDBG) logv("notifyCarrierConfigChanged slotId=" + slotId);
        mHandler.sendMessage(mHandler.obtainMessage(EVENT_CARRIER_CONFIG_CHANGED, slotId, 0));
    }
    /**
     * Register IImsStateCallback
     *
     * @param feature for which state is changed, ImsFeature.FEATURE_*
     */
    public void registerImsStateCallback(int subId, int feature, IImsStateCallback cb,
            String callingPackage) {
        if (VDBG) {
            logv("registerImsStateCallback subId=" + subId
                    + ", feature=" + feature + ", pkg=" + callingPackage);
        }

        CallbackWrapper wrapper = new CallbackWrapper(subId, feature, cb, callingPackage);
        mHandler.sendMessage(mHandler.obtainMessage(EVENT_REGISTER_CALLBACK, wrapper));
    }

    /**
     * Unegister previously registered callback
     */
    public void unregisterImsStateCallback(IImsStateCallback cb) {
        if (VDBG) logv("unregisterImsStateCallback");

        mHandler.sendMessage(mHandler.obtainMessage(EVENT_UNREGISTER_CALLBACK, cb));
    }

    /**
     * Get ImsManager reference associated with subId
     *
     * @param subId subscribe ID
     * @return instance of ImsManager associated with subId, but if ImsService is not
     * available return null
     */
    public ImsManager getImsManager(int subId) {
        if (VDBG) logv("getImsManager subId = " + subId);

        return mSubIdToImsManagerCache.get(subId);
    }

    private void removeInactiveCallbacks(
            ArrayList<IBinder> inactiveCallbacks, String message) {
        if (inactiveCallbacks == null || inactiveCallbacks.size() == 0) return;

        if (VDBG) {
            logv("removeInactiveCallbacks size="
                    + inactiveCallbacks.size() + " from " + message);
        }

        for (IBinder binder : inactiveCallbacks) {
            CallbackWrapper wrapper = mWrappers.get(binder);
            if (wrapper != null) {
                // Send the reason REASON_SUBSCRIPTION_INACTIVE to the client
                wrapper.notifyInactive();
                mWrappers.remove(binder);
            }
        }
        inactiveCallbacks.clear();
    }

    private int getSubId(int slotId) {
        Phone phone = mPhoneFactoryProxy.getPhone(slotId);
        if (phone != null) return phone.getSubId();
        return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    }

    private static boolean isActive(final int[] activeSubs, int subId) {
        for (int i : activeSubs) {
            if (i == subId) return true;
        }
        return false;
    }

    private static int convertReasonType(int reason) {
        switch(reason) {
            case UNAVAILABLE_REASON_NOT_READY:
                return REASON_IMS_SERVICE_NOT_READY;
            case UNAVAILABLE_REASON_IMS_UNSUPPORTED:
                return REASON_NO_IMS_SERVICE_CONFIGURED;
            default:
                break;
        }

        return REASON_IMS_SERVICE_DISCONNECTED;
    }

    private boolean verifyImsMmTelConfigured(int slotId) {
        boolean ret = false;
        if (mImsResolver == null) {
            loge("verifyImsMmTelConfigured mImsResolver is null");
        } else {
            ret = mImsResolver.isImsServiceConfiguredForFeature(slotId, FEATURE_MMTEL);
        }
        if (VDBG) logv("verifyImsMmTelConfigured slotId=" + slotId + ", ret=" + ret);
        return ret;
    }

    private boolean verifyImsRcsConfigured(int slotId) {
        boolean ret = false;
        if (mImsResolver == null) {
            loge("verifyImsRcsConfigured mImsResolver is null");
        } else {
            ret = mImsResolver.isImsServiceConfiguredForFeature(slotId, FEATURE_RCS);
        }
        if (VDBG) logv("verifyImsRcsConfigured slotId=" + slotId + ", ret=" + ret);
        return ret;
    }

    private static String connectorReasonToString(int reason) {
        switch(reason) {
            case UNAVAILABLE_REASON_DISCONNECTED:
                return "DISCONNECTED";
            case UNAVAILABLE_REASON_NOT_READY:
                return "NOT_READY";
            case UNAVAILABLE_REASON_IMS_UNSUPPORTED:
                return "IMS_UNSUPPORTED";
            case UNAVAILABLE_REASON_SERVER_UNAVAILABLE:
                return "SERVER_UNAVAILABLE";
            default:
                break;
        }
        return "";
    }

    private static String imsStateReasonToString(int reason) {
        switch(reason) {
            case AVAILABLE:
                return "READY";
            case REASON_UNKNOWN_TEMPORARY_ERROR:
                return "UNKNOWN_TEMPORARY_ERROR";
            case REASON_UNKNOWN_PERMANENT_ERROR:
                return "UNKNOWN_PERMANENT_ERROR";
            case REASON_IMS_SERVICE_DISCONNECTED:
                return "IMS_SERVICE_DISCONNECTED";
            case REASON_NO_IMS_SERVICE_CONFIGURED:
                return "NO_IMS_SERVICE_CONFIGURED";
            case REASON_SUBSCRIPTION_INACTIVE:
                return "SUBSCRIPTION_INACTIVE";
            case REASON_IMS_SERVICE_NOT_READY:
                return "IMS_SERVICE_NOT_READY";
            default:
                break;
        }
        return "";
    }

    /**
     * PhoneFactory Dependencies for testing.
     */
    @VisibleForTesting
    public interface PhoneFactoryProxy {
        /**
         * Override getPhone for testing.
         */
        Phone getPhone(int index);
    }

    private PhoneFactoryProxy mPhoneFactoryProxy = new PhoneFactoryProxy() {
        @Override
        public Phone getPhone(int index) {
            return PhoneFactory.getPhone(index);
        }
    };

    private void release() {
        if (VDBG) logv("release");

        mTelephonyRegistryManager.removeOnSubscriptionsChangedListener(mSubChangedListener);
        mApp.unregisterReceiver(mReceiver);

        for (int i = 0; i < mMmTelFeatureListeners.size(); i++) {
            mMmTelFeatureListeners.valueAt(i).destroy();
        }
        mMmTelFeatureListeners.clear();

        for (int i = 0; i < mRcsFeatureListeners.size(); i++) {
            mRcsFeatureListeners.valueAt(i).destroy();
        }
        mRcsFeatureListeners.clear();
    }

    /**
     * destroy the instance
     */
    @VisibleForTesting
    public void destroy() {
        if (VDBG) logv("destroy it");

        release();
        mHandler.getLooper().quit();
    }

    /**
     * get the handler
     */
    @VisibleForTesting
    public Handler getHandler() {
        return mHandler;
    }

    /**
     * Determine whether the callback is registered or not
     */
    @VisibleForTesting
    public boolean isRegistered(IImsStateCallback cb) {
        if (cb == null) return false;
        return mWrappers.containsKey(cb.asBinder());
    }

    /**
     * Dump this instance into a readable format for dumpsys usage.
     */
    public void dump(IndentingPrintWriter pw) {
        pw.increaseIndent();
        synchronized (mDumpLock) {
            pw.println("CallbackWrappers:");
            pw.increaseIndent();
            mWrappers.values().forEach(wrapper -> wrapper.dump(pw));
            pw.decreaseIndent();
            pw.println("MmTelFeatureListeners:");
            pw.increaseIndent();
            for (int i = 0; i < mNumSlots; i++) {
                MmTelFeatureListener l = mMmTelFeatureListeners.get(i);
                if (l == null) continue;
                l.dump(pw);
            }
            pw.decreaseIndent();
            pw.println("RcsFeatureListeners:");
            pw.increaseIndent();
            for (int i = 0; i < mNumSlots; i++) {
                RcsFeatureListener l = mRcsFeatureListeners.get(i);
                if (l == null) continue;
                l.dump(pw);
            }
            pw.decreaseIndent();
            pw.println("Most recent logs:");
            pw.increaseIndent();
            sLocalLog.dump(pw);
            pw.decreaseIndent();
        }
        pw.decreaseIndent();
    }

    private static void logv(String msg) {
        Rlog.d(TAG, msg);
    }

    private static void logd(String msg) {
        Rlog.d(TAG, msg);
        sLocalLog.log(msg);
    }

    private static void loge(String msg) {
        Rlog.e(TAG, msg);
        sLocalLog.log(msg);
    }
}
