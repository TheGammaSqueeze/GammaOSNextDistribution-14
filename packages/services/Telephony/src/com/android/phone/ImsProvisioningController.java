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

import static android.telephony.ims.ImsRcsManager.CAPABILITY_TYPE_OPTIONS_UCE;
import static android.telephony.ims.ImsRcsManager.CAPABILITY_TYPE_PRESENCE_UCE;
import static android.telephony.ims.ProvisioningManager.KEY_EAB_PROVISIONING_STATUS;
import static android.telephony.ims.ProvisioningManager.KEY_VOICE_OVER_WIFI_ENABLED_OVERRIDE;
import static android.telephony.ims.ProvisioningManager.KEY_VOLTE_PROVISIONING_STATUS;
import static android.telephony.ims.ProvisioningManager.KEY_VT_PROVISIONING_STATUS;
import static android.telephony.ims.ProvisioningManager.PROVISIONING_VALUE_DISABLED;
import static android.telephony.ims.ProvisioningManager.PROVISIONING_VALUE_ENABLED;
import static android.telephony.ims.feature.ImsFeature.FEATURE_MMTEL;
import static android.telephony.ims.feature.ImsFeature.FEATURE_RCS;
import static android.telephony.ims.feature.MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_CALL_COMPOSER;
import static android.telephony.ims.feature.MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_SMS;
import static android.telephony.ims.feature.MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_UT;
import static android.telephony.ims.feature.MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VIDEO;
import static android.telephony.ims.feature.MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VOICE;
import static android.telephony.ims.stub.ImsRegistrationImplBase.REGISTRATION_TECH_CROSS_SIM;
import static android.telephony.ims.stub.ImsRegistrationImplBase.REGISTRATION_TECH_IWLAN;
import static android.telephony.ims.stub.ImsRegistrationImplBase.REGISTRATION_TECH_LTE;
import static android.telephony.ims.stub.ImsRegistrationImplBase.REGISTRATION_TECH_MAX;
import static android.telephony.ims.stub.ImsRegistrationImplBase.REGISTRATION_TECH_NONE;
import static android.telephony.ims.stub.ImsRegistrationImplBase.REGISTRATION_TECH_NR;

import android.annotation.Nullable;
import android.content.Context;
import android.os.AsyncResult;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.PersistableBundle;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.telephony.CarrierConfigManager;
import android.telephony.CarrierConfigManager.Ims;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyRegistryManager;
import android.telephony.ims.ProvisioningManager;
import android.telephony.ims.aidl.IFeatureProvisioningCallback;
import android.telephony.ims.aidl.IImsConfig;
import android.telephony.ims.aidl.IImsConfigCallback;
import android.telephony.ims.feature.MmTelFeature.MmTelCapabilities;
import android.telephony.ims.feature.RcsFeature.RcsImsCapabilities;
import android.telephony.ims.stub.ImsConfigImplBase;
import android.telephony.ims.stub.ImsRegistrationImplBase;
import android.util.SparseArray;

import com.android.ims.FeatureConnector;
import com.android.ims.ImsConfig;
import com.android.ims.ImsException;
import com.android.ims.ImsManager;
import com.android.ims.RcsFeatureManager;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.PhoneConfigurationManager;
import com.android.internal.telephony.util.HandlerExecutor;
import com.android.telephony.Rlog;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Provides APIs for MMTEL and RCS provisioning status. This class handles provisioning status and
 * notifies the status changing for each capability
 * {{@link MmTelCapabilities.MmTelCapability} for MMTel services}
 * {{@link RcsImsCapabilities.RcsImsCapabilityFlag} for RCS services}
 */
public class ImsProvisioningController {
    private static final String TAG = "ImsProvisioningController";
    private static final int INVALID_VALUE = -1;

    private static final int EVENT_SUB_CHANGED = 1;
    private static final int EVENT_PROVISIONING_CAPABILITY_CHANGED = 2;
    @VisibleForTesting
    protected static final int EVENT_MULTI_SIM_CONFIGURATION_CHANGE = 3;
    private static final int EVENT_PROVISIONING_VALUE_CHANGED = 4;

    // Provisioning Keys that are handled via AOSP cache and not sent to the ImsService
    private static final int[] LOCAL_IMS_CONFIG_KEYS = {
            KEY_VOLTE_PROVISIONING_STATUS,
            KEY_VT_PROVISIONING_STATUS,
            KEY_VOICE_OVER_WIFI_ENABLED_OVERRIDE,
            KEY_EAB_PROVISIONING_STATUS
    };
    private static final int[] LOCAL_RADIO_TECHS = {
            REGISTRATION_TECH_LTE,
            REGISTRATION_TECH_IWLAN,
            REGISTRATION_TECH_CROSS_SIM,
            REGISTRATION_TECH_NR
    };

    private static final int MMTEL_CAPABILITY_MIN = MmTelCapabilities.CAPABILITY_TYPE_NONE;
    private static final int MMTEL_CAPABILITY_MAX = MmTelCapabilities.CAPABILITY_TYPE_MAX;

    private static final int RCS_CAPABILITY_MIN = RcsImsCapabilities.CAPABILITY_TYPE_NONE;
    private static final int RCS_CAPABILITY_MAX = RcsImsCapabilities.CAPABILITY_TYPE_MAX;

    private static final int[] LOCAL_MMTEL_CAPABILITY = {
            CAPABILITY_TYPE_VOICE,
            CAPABILITY_TYPE_VIDEO,
            CAPABILITY_TYPE_UT,
            CAPABILITY_TYPE_SMS,
            CAPABILITY_TYPE_CALL_COMPOSER
    };

    /**
     * map the MmTelCapabilities.MmTelCapability and
     * CarrierConfigManager.Ims.KEY_CAPABILITY_TYPE_VOICE_INT
     * CarrierConfigManager.Ims.KEY_CAPABILITY_TYPE_VIDEO_INT
     * CarrierConfigManager.Ims.KEY_CAPABILITY_TYPE_UT_INT
     * CarrierConfigManager.Ims.KEY_CAPABILITY_TYPE_SMS_INT
     * CarrierConfigManager.Ims.KEY_CAPABILITY_TYPE_CALL_COMPOSER_INT
     */
    private static final Map<Integer, String> KEYS_MMTEL_CAPABILITY = Map.of(
            CAPABILITY_TYPE_VOICE, Ims.KEY_CAPABILITY_TYPE_VOICE_INT_ARRAY,
            CAPABILITY_TYPE_VIDEO, Ims.KEY_CAPABILITY_TYPE_VIDEO_INT_ARRAY,
            CAPABILITY_TYPE_UT, Ims.KEY_CAPABILITY_TYPE_UT_INT_ARRAY,
            CAPABILITY_TYPE_SMS, Ims.KEY_CAPABILITY_TYPE_SMS_INT_ARRAY,
            CAPABILITY_TYPE_CALL_COMPOSER, Ims.KEY_CAPABILITY_TYPE_CALL_COMPOSER_INT_ARRAY
    );

    /**
     * map the RcsImsCapabilities.RcsImsCapabilityFlag and
     * CarrierConfigManager.Ims.KEY_CAPABILITY_TYPE_OPTIONS_UCE
     * CarrierConfigManager.Ims.KEY_CAPABILITY_TYPE_PRESENCE_UCE
     */
    private static final Map<Integer, String> KEYS_RCS_CAPABILITY = Map.of(
            CAPABILITY_TYPE_OPTIONS_UCE, Ims.KEY_CAPABILITY_TYPE_OPTIONS_UCE_INT_ARRAY,
            CAPABILITY_TYPE_PRESENCE_UCE, Ims.KEY_CAPABILITY_TYPE_PRESENCE_UCE_INT_ARRAY
    );

    /**
     * Create a FeatureConnector for this class to use to connect to an ImsManager.
     */
    @VisibleForTesting
    public interface MmTelFeatureConnector {
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
    public interface RcsFeatureConnector {
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

    private static ImsProvisioningController sInstance;

    private final PhoneGlobals mApp;
    private final Handler mHandler;
    private final CarrierConfigManager mCarrierConfigManager;
    private final SubscriptionManager mSubscriptionManager;
    private final TelephonyRegistryManager mTelephonyRegistryManager;
    private final MmTelFeatureConnector mMmTelFeatureConnector;
    private final RcsFeatureConnector mRcsFeatureConnector;

    // maps a slotId to a list of MmTelFeatureListeners
    private final SparseArray<MmTelFeatureListener> mMmTelFeatureListenersSlotMap =
            new SparseArray<>();
    // maps a slotId to a list of RcsFeatureListeners
    private final SparseArray<RcsFeatureListener> mRcsFeatureListenersSlotMap =
            new SparseArray<>();
    // map a slotId to a list of ProvisioningCallbackManager
    private final SparseArray<ProvisioningCallbackManager> mProvisioningCallbackManagersSlotMap =
            new SparseArray<>();
    private final ImsProvisioningLoader mImsProvisioningLoader;

    private int mNumSlot;

    /**
     * This class contains the provisioning status to notify changes.
     * {{@link MmTelCapabilities.MmTelCapability} for MMTel services}
     * {{@link android.telephony.ims.ImsRcsManager.RcsImsCapabilityFlag} for RCS services}
     * {{@link ImsRegistrationImplBase.ImsRegistrationTech} for Registration tech}
     */
    private static final class FeatureProvisioningData {
        public final int mCapability;
        public final int mTech;
        public final boolean mProvisioned;
        public final boolean mIsMmTel;

        FeatureProvisioningData(int capability, int tech, boolean provisioned, boolean isMmTel) {
            mCapability = capability;
            mTech = tech;
            mProvisioned = provisioned;
            mIsMmTel = isMmTel;
        }
    }

    private final class MessageHandler extends Handler {
        private static final String LOG_PREFIX = "Handler";
        MessageHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_SUB_CHANGED:
                    onSubscriptionsChanged();
                    break;
                case EVENT_PROVISIONING_CAPABILITY_CHANGED:
                    try {
                        mProvisioningCallbackManagersSlotMap.get(msg.arg1)
                                .notifyProvisioningCapabilityChanged(
                                        (FeatureProvisioningData) msg.obj);
                    } catch (NullPointerException e) {
                        logw(LOG_PREFIX, msg.arg1,
                                "can not find callback manager message" + msg.what);
                    }
                    break;
                case EVENT_MULTI_SIM_CONFIGURATION_CHANGE:
                    int activeModemCount = (int) ((AsyncResult) msg.obj).result;
                    onMultiSimConfigChanged(activeModemCount);
                    break;
                case EVENT_PROVISIONING_VALUE_CHANGED:
                    log("subId " + msg.arg1 + " changed provisioning value item : " + msg.arg2
                            + " value : " + (int) msg.obj);
                    updateCapabilityTechFromKey(msg.arg1, msg.arg2, (int) msg.obj);
                    break;
                default:
                    log("unknown message " + msg);
                    break;
            }
        }
    }

    private final SubscriptionManager.OnSubscriptionsChangedListener mSubChangedListener =
            new SubscriptionManager.OnSubscriptionsChangedListener() {
                @Override
                public void onSubscriptionsChanged() {
                    if (!mHandler.hasMessages(EVENT_SUB_CHANGED)) {
                        mHandler.sendEmptyMessage(EVENT_SUB_CHANGED);
                    }
                }
            };

    private final class ProvisioningCallbackManager {
        private static final String LOG_PREFIX = "ProvisioningCallbackManager";
        private RemoteCallbackList<IFeatureProvisioningCallback> mIFeatureProvisioningCallbackList;
        private int mSubId;
        private int mSlotId;

        ProvisioningCallbackManager(int slotId) {
            mIFeatureProvisioningCallbackList =
                    new RemoteCallbackList<IFeatureProvisioningCallback>();
            mSlotId = slotId;
            mSubId = getSubId(slotId);
            log(LOG_PREFIX, mSlotId, "ProvisioningCallbackManager create");
        }

        public void clear() {
            log(LOG_PREFIX, mSlotId, "ProvisioningCallbackManager clear ");

            mIFeatureProvisioningCallbackList.kill();

            // All registered callbacks are unregistered, and the list is disabled
            // need to create again
            mIFeatureProvisioningCallbackList =
                    new RemoteCallbackList<IFeatureProvisioningCallback>();
        }

        public void registerCallback(IFeatureProvisioningCallback localCallback) {
            if (!mIFeatureProvisioningCallbackList.register(localCallback, (Object) mSubId)) {
                log(LOG_PREFIX, mSlotId, "registration callback fail");
            }
        }

        public void unregisterCallback(IFeatureProvisioningCallback localCallback) {
            mIFeatureProvisioningCallbackList.unregister(localCallback);
        }

        public void setSubId(int subId) {
            if (mSubId == subId) {
                log(LOG_PREFIX, mSlotId, "subId is not changed ");
                return;
            }

            mSubId = subId;
            mSlotId = getSlotId(subId);

            // subId changed means the registered callbacks are not available.
            clear();
        }

        public boolean hasCallblacks() {
            int size = mIFeatureProvisioningCallbackList.beginBroadcast();
            mIFeatureProvisioningCallbackList.finishBroadcast();

            return (size > 0);
        }

        public void notifyProvisioningCapabilityChanged(FeatureProvisioningData data) {
            int size = mIFeatureProvisioningCallbackList.beginBroadcast();
            for (int index = 0; index < size; index++) {
                try {
                    IFeatureProvisioningCallback imsFeatureProvisioningCallback =
                            mIFeatureProvisioningCallbackList.getBroadcastItem(index);

                    // MMTEL
                    if (data.mIsMmTel
                            && Arrays.stream(LOCAL_MMTEL_CAPABILITY)
                            .anyMatch(value -> value == data.mCapability)) {
                        imsFeatureProvisioningCallback.onFeatureProvisioningChanged(
                                data.mCapability, data.mTech, data.mProvisioned);
                        logi(LOG_PREFIX, mSlotId, "notifyProvisioningCapabilityChanged : "
                                + "onFeatureProvisioningChanged"
                                + " capability " + data.mCapability
                                + " tech "  + data.mTech
                                + " isProvisioned " + data.mProvisioned);
                    } else if (data.mCapability == CAPABILITY_TYPE_PRESENCE_UCE) {
                        imsFeatureProvisioningCallback.onRcsFeatureProvisioningChanged(
                                data.mCapability, data.mTech, data.mProvisioned);
                        logi(LOG_PREFIX, mSlotId, "notifyProvisioningCapabilityChanged : "
                                + "onRcsFeatureProvisioningChanged"
                                + " capability " + data.mCapability
                                + " tech "  + data.mTech
                                + " isProvisioned " + data.mProvisioned);
                    } else {
                        loge(LOG_PREFIX, mSlotId, "notifyProvisioningCapabilityChanged : "
                                + "unknown capability "
                                + data.mCapability);
                    }
                } catch (RemoteException e) {
                    loge(LOG_PREFIX, mSlotId,
                            "notifyProvisioningChanged: callback #" + index + " failed");
                }
            }
            mIFeatureProvisioningCallbackList.finishBroadcast();
        }
    }

    private final class MmTelFeatureListener implements FeatureConnector.Listener<ImsManager> {
        private static final String LOG_PREFIX = "MmTelFeatureListener";
        private FeatureConnector<ImsManager> mConnector;
        private ImsManager mImsManager;
        private boolean mReady = false;
        // stores whether the initial provisioning key value should be notified to ImsService
        private boolean mRequiredNotify = false;
        private int mSubId;
        private int mSlotId;
        private ConfigCallback mConfigCallback;

        MmTelFeatureListener(int slotId) {
            log(LOG_PREFIX, slotId, "created");

            mSlotId = slotId;
            mSubId = getSubId(slotId);
            mConfigCallback = new ConfigCallback(mSubId);

            mConnector = mMmTelFeatureConnector.create(
                    mApp, slotId, TAG, this, new HandlerExecutor(mHandler));
            mConnector.connect();
        }

        public void setSubId(int subId) {
            if (mRequiredNotify && mReady) {
                mRequiredNotify = false;
                setInitialProvisioningKeys(subId);
            }
            if (mSubId == subId) {
                log(LOG_PREFIX, mSlotId, "subId is not changed");
                return;
            }

            mSubId = subId;
            mSlotId = getSlotId(subId);
            mConfigCallback.setSubId(subId);
        }

        public void destroy() {
            log("destroy");
            if (mImsManager != null) {
                try {
                    ImsConfig imsConfig = getImsConfig(mImsManager);
                    if (imsConfig != null) {
                        imsConfig.removeConfigCallback(mConfigCallback);
                    }
                } catch (ImsException e) {
                    logw(LOG_PREFIX, mSlotId, "destroy : " + e.getMessage());
                }
            }
            mConfigCallback = null;
            mConnector.disconnect();
            mConnector = null;
            mReady = false;
            mImsManager = null;
        }

        public @Nullable ImsManager getImsManager() {
            return mImsManager;
        }

        @Override
        public void connectionReady(ImsManager manager, int subId) {
            log(LOG_PREFIX, mSlotId, "connection ready");
            mReady = true;
            mImsManager = manager;

            if (mImsManager != null) {
                try {
                    ImsConfig imsConfig = getImsConfig(mImsManager);
                    if (imsConfig != null) {
                        imsConfig.addConfigCallback(mConfigCallback);
                    }
                } catch (ImsException e) {
                    logw(LOG_PREFIX, mSlotId, "addConfigCallback : " + e.getMessage());
                }
            }

            onMmTelAvailable();
        }

        @Override
        public void connectionUnavailable(int reason) {
            log(LOG_PREFIX, mSlotId, "connection unavailable " + reason);

            mReady = false;
            mImsManager = null;

            // keep the callback for other reason
            if (reason == FeatureConnector.UNAVAILABLE_REASON_IMS_UNSUPPORTED) {
                onMmTelUnavailable();
            }
        }

        public int setProvisioningValue(int key, int value) {
            int retVal = ImsConfigImplBase.CONFIG_RESULT_FAILED;

            if (!mReady) {
                loge(LOG_PREFIX, mSlotId, "service is Unavailable");
                return retVal;
            }
            try {
                // getConfigInterface() will return not null or throw the ImsException
                // need not null checking
                ImsConfig imsConfig = getImsConfig(mImsManager);
                retVal = imsConfig.setConfig(key, value);
                log(LOG_PREFIX, mSlotId, "setConfig called with key " + key + " value " + value);
            } catch (ImsException e) {
                logw(LOG_PREFIX, mSlotId,
                        "setConfig operation failed for key =" + key
                        + ", value =" + value + ". Exception:" + e.getMessage());
            }
            return retVal;
        }

        public int getProvisioningValue(int key) {
            if (!mReady) {
                loge(LOG_PREFIX, mSlotId, "service is Unavailable");
                return INVALID_VALUE;
            }

            int retValue = INVALID_VALUE;
            try {
                // getConfigInterface() will return not null or throw the ImsException
                // need not null checking
                ImsConfig imsConfig = getImsConfig(mImsManager);
                retValue = imsConfig.getConfigInt(key);
            } catch (ImsException e) {
                logw(LOG_PREFIX, mSlotId,
                        "getConfig operation failed for key =" + key
                        + ", value =" + retValue + ". Exception:" + e.getMessage());
            }
            return retValue;
        }

        public void onMmTelAvailable() {
            log(LOG_PREFIX, mSlotId, "onMmTelAvailable");

            if (isValidSubId(mSubId)) {
                mRequiredNotify = false;

                // notify provisioning key value to ImsService
                setInitialProvisioningKeys(mSubId);
            } else {
                // wait until subId is valid
                mRequiredNotify = true;
            }
        }

        public void onMmTelUnavailable() {
            log(LOG_PREFIX, mSlotId, "onMmTelUnavailable");

            try {
                // delete all callbacks reference from ProvisioningManager
                mProvisioningCallbackManagersSlotMap.get(getSlotId(mSubId)).clear();
            } catch (NullPointerException e) {
                logw(LOG_PREFIX, getSlotId(mSubId), "can not find callback manager to clear");
            }
        }

        private void setInitialProvisioningKeys(int subId) {
            boolean required;
            int value = ImsProvisioningLoader.STATUS_NOT_SET;

            // updating KEY_VOLTE_PROVISIONING_STATUS
            try {
                required = isImsProvisioningRequiredForCapability(subId, CAPABILITY_TYPE_VOICE,
                        REGISTRATION_TECH_LTE);
            } catch (IllegalArgumentException e) {
                logw("setInitialProvisioningKeys: KEY_VOLTE_PROVISIONING_STATUS failed for"
                        + " subId=" + subId + ", exception: " + e.getMessage());
                return;
            }

            log(LOG_PREFIX, mSlotId,
                    "setInitialProvisioningKeys provisioning required(voice, lte) " + required);
            if (required) {
                value = mImsProvisioningLoader.getProvisioningStatus(subId, FEATURE_MMTEL,
                        CAPABILITY_TYPE_VOICE, REGISTRATION_TECH_LTE);
                if (value != ImsProvisioningLoader.STATUS_NOT_SET) {
                    value = (value == ImsProvisioningLoader.STATUS_PROVISIONED)
                            ? PROVISIONING_VALUE_ENABLED : PROVISIONING_VALUE_DISABLED;
                    setProvisioningValue(KEY_VOLTE_PROVISIONING_STATUS, value);
                }
            }

            // updating KEY_VT_PROVISIONING_STATUS
            try {
                required = isImsProvisioningRequiredForCapability(subId, CAPABILITY_TYPE_VIDEO,
                        REGISTRATION_TECH_LTE);
            } catch (IllegalArgumentException e) {
                logw("setInitialProvisioningKeys: KEY_VT_PROVISIONING_STATUS failed for"
                        + " subId=" + subId + ", exception: " + e.getMessage());
                return;
            }

            log(LOG_PREFIX, mSlotId,
                    "setInitialProvisioningKeys provisioning required(video, lte) " + required);
            if (required) {
                value = mImsProvisioningLoader.getProvisioningStatus(subId, FEATURE_MMTEL,
                        CAPABILITY_TYPE_VIDEO, REGISTRATION_TECH_LTE);
                if (value != ImsProvisioningLoader.STATUS_NOT_SET) {
                    value = (value == ImsProvisioningLoader.STATUS_PROVISIONED)
                            ? PROVISIONING_VALUE_ENABLED : PROVISIONING_VALUE_DISABLED;
                    setProvisioningValue(KEY_VT_PROVISIONING_STATUS, value);
                }
            }

            // updating KEY_VOICE_OVER_WIFI_ENABLED_OVERRIDE
            try {
                required = isImsProvisioningRequiredForCapability(subId, CAPABILITY_TYPE_VOICE,
                        REGISTRATION_TECH_IWLAN);
            } catch (IllegalArgumentException e) {
                logw("setInitialProvisioningKeys: KEY_VOICE_OVER_WIFI_ENABLED_OVERRIDE failed"
                        + " for subId=" + subId + ", exception: " + e.getMessage());
                return;
            }

            log(LOG_PREFIX, mSlotId,
                    "setInitialProvisioningKeys provisioning required(voice, iwlan) " + required);
            if (required) {
                value = mImsProvisioningLoader.getProvisioningStatus(subId, FEATURE_MMTEL,
                        CAPABILITY_TYPE_VOICE, REGISTRATION_TECH_IWLAN);
                if (value != ImsProvisioningLoader.STATUS_NOT_SET) {
                    value = (value == ImsProvisioningLoader.STATUS_PROVISIONED)
                            ? PROVISIONING_VALUE_ENABLED : PROVISIONING_VALUE_DISABLED;
                    setProvisioningValue(KEY_VOICE_OVER_WIFI_ENABLED_OVERRIDE, value);
                }
            }
        }
    }

    private final class RcsFeatureListener implements FeatureConnector.Listener<RcsFeatureManager> {
        private static final String LOG_PREFIX = "RcsFeatureListener";
        private FeatureConnector<RcsFeatureManager> mConnector;
        private RcsFeatureManager mRcsFeatureManager;
        private boolean mReady = false;
        // stores whether the initial provisioning key value should be notified to ImsService
        private boolean mRequiredNotify = false;
        private int mSubId;
        private int mSlotId;
        private ConfigCallback mConfigCallback;

        RcsFeatureListener(int slotId) {
            log(LOG_PREFIX, slotId, "created");

            mSlotId = slotId;
            mSubId = getSubId(slotId);
            mConfigCallback = new ConfigCallback(mSubId);

            mConnector = mRcsFeatureConnector.create(
                    mApp, slotId, this, new HandlerExecutor(mHandler), TAG);
            mConnector.connect();
        }

        public void setSubId(int subId) {
            if (mRequiredNotify && mReady) {
                mRequiredNotify = false;
                setInitialProvisioningKeys(subId);
            }
            if (mSubId == subId) {
                log(LOG_PREFIX, mSlotId, "subId is not changed");
                return;
            }

            mSubId = subId;
            mSlotId = getSlotId(subId);
            mConfigCallback.setSubId(subId);
        }

        public void destroy() {
            log(LOG_PREFIX, mSlotId, "destroy");
            if (mRcsFeatureManager != null) {
                try {
                    ImsConfig imsConfig = getImsConfig(mRcsFeatureManager.getConfig());
                    if (imsConfig != null) {
                        imsConfig.removeConfigCallback(mConfigCallback);
                    }
                } catch (ImsException e) {
                    logw(LOG_PREFIX, mSlotId, "destroy :" + e.getMessage());
                }
            }
            mConfigCallback = null;
            mConnector.disconnect();
            mConnector = null;
            mReady = false;
            mRcsFeatureManager = null;
        }

        @Override
        public void connectionReady(RcsFeatureManager manager, int subId) {
            log(LOG_PREFIX, mSlotId, "connection ready");
            mReady = true;
            mRcsFeatureManager = manager;

            if (mRcsFeatureManager != null) {
                try {
                    ImsConfig imsConfig = getImsConfig(mRcsFeatureManager.getConfig());
                    if (imsConfig != null) {
                        imsConfig.addConfigCallback(mConfigCallback);
                    }
                } catch (ImsException e) {
                    logw(LOG_PREFIX, mSlotId, "addConfigCallback :" + e.getMessage());
                }
            }

            onRcsAvailable();
        }

        @Override
        public void connectionUnavailable(int reason) {
            log(LOG_PREFIX, mSlotId, "connection unavailable");
            mReady = false;
            mRcsFeatureManager = null;

            // keep the callback for other reason
            if (reason == FeatureConnector.UNAVAILABLE_REASON_IMS_UNSUPPORTED) {
                onRcsUnavailable();
            }
        }

        public int setProvisioningValue(int key, int value) {
            int retVal = ImsConfigImplBase.CONFIG_RESULT_FAILED;

            if (!mReady) {
                loge(LOG_PREFIX, mSlotId, "service is Unavailable");
                return retVal;
            }

            try {
                // getConfigInterface() will return not null or throw the ImsException
                // need not null checking
                ImsConfig imsConfig = getImsConfig(mRcsFeatureManager.getConfig());
                retVal = imsConfig.setConfig(key, value);
                log(LOG_PREFIX, mSlotId, "setConfig called with key " + key + " value " + value);
            } catch (ImsException e) {
                logw(LOG_PREFIX, mSlotId,
                        "setConfig operation failed for key =" + key
                        + ", value =" + value + ". Exception:" + e.getMessage());
            }
            return retVal;
        }

        public int getProvisioningValue(int key) {
            if (!mReady) {
                loge(LOG_PREFIX, mSlotId, "service is Unavailable");
                return INVALID_VALUE;
            }

            int retValue = INVALID_VALUE;
            try {
                // getConfigInterface() will return not null or throw the ImsException
                // need not null checking
                ImsConfig imsConfig = getImsConfig(mRcsFeatureManager.getConfig());
                retValue = imsConfig.getConfigInt(key);
            } catch (ImsException e) {
                logw(LOG_PREFIX, mSlotId,
                        "getConfig operation failed for key =" + key
                        + ", value =" + retValue + ". Exception:" + e.getMessage());
            }
            return retValue;
        }

        public boolean isConnectionReady() {
            return mReady;
        }

        public void onRcsAvailable() {
            log(LOG_PREFIX, mSlotId, "onRcsAvailable");

            if (isValidSubId(mSubId)) {
                mRequiredNotify = false;

                // notify provisioning key value to ImsService
                setInitialProvisioningKeys(mSubId);
            } else {
                // wait until subId is valid
                mRequiredNotify = true;
            }
        }

        public void onRcsUnavailable() {
            log(LOG_PREFIX, mSlotId, "onRcsUnavailable");

            try {
                // delete all callbacks reference from ProvisioningManager
                mProvisioningCallbackManagersSlotMap.get(getSlotId(mSubId)).clear();
            } catch (NullPointerException e) {
                logw(LOG_PREFIX, getSlotId(mSubId), "can not find callback manager to clear");
            }
        }

        private void setInitialProvisioningKeys(int subId) {
            boolean required;
            int value = ImsProvisioningLoader.STATUS_NOT_SET;

            // KEY_EAB_PROVISIONING_STATUS
            int capability = CAPABILITY_TYPE_PRESENCE_UCE;
            // Assume that all radio techs have the same provisioning value
            int tech = REGISTRATION_TECH_LTE;

            try {
                required = isRcsProvisioningRequiredForCapability(subId, capability, tech);
            } catch (IllegalArgumentException e) {
                logw("setInitialProvisioningKeys: KEY_EAB_PROVISIONING_STATUS failed for"
                        + " subId=" + subId + ", exception: " + e.getMessage());
                return;
            }

            if (required) {
                value = mImsProvisioningLoader.getProvisioningStatus(subId, FEATURE_RCS,
                        capability, tech);
                if (value != ImsProvisioningLoader.STATUS_NOT_SET) {
                    value = (value == ImsProvisioningLoader.STATUS_PROVISIONED)
                            ? PROVISIONING_VALUE_ENABLED : PROVISIONING_VALUE_DISABLED;
                    setProvisioningValue(KEY_EAB_PROVISIONING_STATUS, value);
                }
            }
        }
    }

    // When vendor ImsService changed provisioning data, which should be updated in AOSP.
    // Catch the event using IImsConfigCallback.
    private final class ConfigCallback extends IImsConfigCallback.Stub {
        private int mSubId;

        ConfigCallback(int subId) {
            mSubId = subId;
        }

        public void setSubId(int subId) {
            mSubId = subId;
        }

        @Override
        public void onIntConfigChanged(int item, int value) throws RemoteException {
            if (!Arrays.stream(LOCAL_IMS_CONFIG_KEYS).anyMatch(keyValue -> keyValue == item)) {
                return;
            }

            final long callingIdentity = Binder.clearCallingIdentity();
            try {
                if (mHandler != null) {
                    mHandler.sendMessage(mHandler.obtainMessage(
                            EVENT_PROVISIONING_VALUE_CHANGED, mSubId, item, (Object) value));
                }
            } finally {
                Binder.restoreCallingIdentity(callingIdentity);
            }
        }

        @Override
        public void onStringConfigChanged(int item, String value) throws RemoteException {
            // Ignore this callback.
        }
    }

    /**
     * Do NOT use this directly, instead use {@link #getInstance()}.
     */
    @VisibleForTesting
    public ImsProvisioningController(PhoneGlobals app, int numSlot, Looper looper,
            MmTelFeatureConnector mmTelFeatureConnector, RcsFeatureConnector rcsFeatureConnector,
            ImsProvisioningLoader imsProvisioningLoader) {
        log("ImsProvisioningController");
        mApp = app;
        mNumSlot = numSlot;
        mHandler = new MessageHandler(looper);
        mMmTelFeatureConnector = mmTelFeatureConnector;
        mRcsFeatureConnector = rcsFeatureConnector;
        mCarrierConfigManager = mApp.getSystemService(CarrierConfigManager.class);
        mSubscriptionManager = mApp.getSystemService(SubscriptionManager.class);
        mTelephonyRegistryManager = mApp.getSystemService(TelephonyRegistryManager.class);
        mTelephonyRegistryManager.addOnSubscriptionsChangedListener(
                mSubChangedListener, mHandler::post);
        mImsProvisioningLoader = imsProvisioningLoader;

        PhoneConfigurationManager.registerForMultiSimConfigChange(mHandler,
                EVENT_MULTI_SIM_CONFIGURATION_CHANGE, null);

        initialize(numSlot);
    }

    private void initialize(int numSlot) {
        for (int i = 0; i < numSlot; i++) {
            MmTelFeatureListener m = new MmTelFeatureListener(i);
            mMmTelFeatureListenersSlotMap.put(i, m);

            RcsFeatureListener r = new RcsFeatureListener(i);
            mRcsFeatureListenersSlotMap.put(i, r);

            ProvisioningCallbackManager p = new ProvisioningCallbackManager(i);
            mProvisioningCallbackManagersSlotMap.put(i, p);
        }
    }

    private void onMultiSimConfigChanged(int newNumSlot) {
        log("onMultiSimConfigChanged: NumSlot " + mNumSlot + " newNumSlot " + newNumSlot);

        if (mNumSlot < newNumSlot) {
            for (int i = mNumSlot; i < newNumSlot; i++) {
                MmTelFeatureListener m = new MmTelFeatureListener(i);
                mMmTelFeatureListenersSlotMap.put(i, m);

                RcsFeatureListener r = new RcsFeatureListener(i);
                mRcsFeatureListenersSlotMap.put(i, r);

                ProvisioningCallbackManager p = new ProvisioningCallbackManager(i);
                mProvisioningCallbackManagersSlotMap.put(i, p);
            }
        } else if (mNumSlot > newNumSlot) {
            for (int i = (mNumSlot - 1); i > (newNumSlot - 1); i--) {
                MmTelFeatureListener m = mMmTelFeatureListenersSlotMap.get(i);
                mMmTelFeatureListenersSlotMap.remove(i);
                m.destroy();

                RcsFeatureListener r = mRcsFeatureListenersSlotMap.get(i);
                mRcsFeatureListenersSlotMap.remove(i);
                r.destroy();

                ProvisioningCallbackManager p = mProvisioningCallbackManagersSlotMap.get(i);
                mProvisioningCallbackManagersSlotMap.remove(i);
                p.clear();
            }
        }

        mNumSlot = newNumSlot;
    }

    /**
     * destroy the instance
     */
    @VisibleForTesting
    public void destroy() {
        log("destroy");

        mHandler.getLooper().quit();

        mTelephonyRegistryManager.removeOnSubscriptionsChangedListener(mSubChangedListener);

        for (int i = 0; i < mMmTelFeatureListenersSlotMap.size(); i++) {
            mMmTelFeatureListenersSlotMap.get(i).destroy();
        }
        mMmTelFeatureListenersSlotMap.clear();

        for (int i = 0; i < mRcsFeatureListenersSlotMap.size(); i++) {
            mRcsFeatureListenersSlotMap.get(i).destroy();
        }
        mRcsFeatureListenersSlotMap.clear();

        for (int i = 0; i < mProvisioningCallbackManagersSlotMap.size(); i++) {
            mProvisioningCallbackManagersSlotMap.get(i).clear();
        }
    }

    /**
     * create an instance
     */
    @VisibleForTesting
    public static ImsProvisioningController make(PhoneGlobals app, int numSlot) {
        synchronized (ImsProvisioningController.class) {
            if (sInstance == null) {
                Rlog.i(TAG, "ImsProvisioningController created");
                HandlerThread handlerThread = new HandlerThread(TAG);
                handlerThread.start();
                sInstance = new ImsProvisioningController(app, numSlot, handlerThread.getLooper(),
                        ImsManager::getConnector, RcsFeatureManager::getConnector,
                        new ImsProvisioningLoader(app));
            }
        }
        return sInstance;
    }

    /**
     * Gets a ImsProvisioningController instance
     */
    @VisibleForTesting
    public static ImsProvisioningController getInstance() {
        synchronized (ImsProvisioningController.class) {
            return sInstance;
        }
    }

    /**
     * Register IFeatureProvisioningCallback from ProvisioningManager
     */

    @VisibleForTesting
    public void addFeatureProvisioningChangedCallback(int subId,
            IFeatureProvisioningCallback callback) {
        if (callback == null) {
            throw new IllegalArgumentException("provisioning callback can't be null");
        }
        int slotId = getSlotId(subId);
        if (slotId < 0 || slotId >= mNumSlot) {
            throw new IllegalArgumentException("subscription id is not available");
        }

        try {
            mProvisioningCallbackManagersSlotMap.get(slotId).registerCallback(callback);
            log("Feature Provisioning Callback registered.");
        } catch (NullPointerException e) {
            logw("can not access callback manager to add callback");
        }
    }

    /**
     * Remove IFeatureProvisioningCallback
     */
    @VisibleForTesting
    public void removeFeatureProvisioningChangedCallback(int subId,
            IFeatureProvisioningCallback callback) {
        if (callback == null) {
            throw new IllegalArgumentException("provisioning callback can't be null");
        }

        int slotId = getSlotId(subId);
        if (slotId < 0 || slotId >= mNumSlot) {
            throw new IllegalArgumentException("subscription id is not available");
        }

        try {
            mProvisioningCallbackManagersSlotMap.get(slotId).unregisterCallback(callback);
            log("Feature Provisioning Callback removed.");
        } catch (NullPointerException e) {
            logw("can not access callback manager to remove callback");
        }
    }

    /**
     * return the boolean whether MmTel capability is required provisioning or not
     */
    @VisibleForTesting
    public boolean isImsProvisioningRequiredForCapability(int subId, int capability, int tech) {
        // check subId
        int slotId = getSlotId(subId);
        if (slotId <= SubscriptionManager.INVALID_SIM_SLOT_INDEX || slotId >= mNumSlot) {
            loge("Fail to retrieve slotId from subId");
            throw new IllegalArgumentException("subscribe id is invalid");
        }

        // check valid capability
        if (!(MMTEL_CAPABILITY_MIN < capability && capability < MMTEL_CAPABILITY_MAX)) {
            throw new IllegalArgumentException("MmTel capability '" + capability + "' is invalid");
        }

        // check valid radio tech
        if (!(REGISTRATION_TECH_NONE < tech && tech < REGISTRATION_TECH_MAX)) {
            log("Ims not matched radio tech " + tech);
            throw new IllegalArgumentException("Registration technology '" + tech + "' is invalid");
        }

        // check new carrier config first KEY_MMTEL_REQUIRES_PROVISIONING_BUNDLE
        boolean retVal = isProvisioningRequired(subId, capability, tech, /*isMmTel*/true);

        // if that returns false, check deprecated carrier config
        // KEY_CARRIER_VOLTE_PROVISIONING_REQUIRED_BOOL, KEY_CARRIER_UT_PROVISIONING_REQUIRED_BOOL
        if (!retVal && (capability == CAPABILITY_TYPE_VOICE
                || capability == CAPABILITY_TYPE_VIDEO
                || capability == CAPABILITY_TYPE_UT)) {
            String key = CarrierConfigManager.KEY_CARRIER_VOLTE_PROVISIONING_REQUIRED_BOOL;
            if (capability == CAPABILITY_TYPE_UT) {
                key = CarrierConfigManager.KEY_CARRIER_UT_PROVISIONING_REQUIRED_BOOL;
            }

            PersistableBundle imsCarrierConfigs = mCarrierConfigManager.getConfigForSubId(subId);
            if (imsCarrierConfigs != null) {
                retVal = imsCarrierConfigs.getBoolean(key);
            } else {
                retVal = CarrierConfigManager.getDefaultConfig().getBoolean(key);
            }
        }

        log("isImsProvisioningRequiredForCapability capability " + capability
                + " tech " + tech + " return value " + retVal);

        return retVal;
    }

    /**
     * return the boolean whether RCS capability is required provisioning or not
     */
    @VisibleForTesting
    public boolean isRcsProvisioningRequiredForCapability(int subId, int capability, int tech) {
        // check slotId and Phone object
        int slotId = getSlotId(subId);
        if (slotId <= SubscriptionManager.INVALID_SIM_SLOT_INDEX || slotId >= mNumSlot) {
            loge("Fail to retrieve slotId from subId");
            throw new IllegalArgumentException("subscribe id is invalid");
        }

        // check valid capability
        if (!(RCS_CAPABILITY_MIN < capability && capability < RCS_CAPABILITY_MAX)) {
            throw new IllegalArgumentException("Rcs capability '" + capability + "' is invalid");
        }

        // check valid radio tech
        if (!(REGISTRATION_TECH_NONE < tech && tech < REGISTRATION_TECH_MAX)) {
            log("Rcs not matched radio tech " + tech);
            throw new IllegalArgumentException("Registration technology '" + tech + "' is invalid");
        }

        // check new carrier config first KEY_RCS_REQUIRES_PROVISIONING_BUNDLE
        boolean retVal = isProvisioningRequired(subId, capability, tech, /*isMmTel*/false);

        // if that returns false, check deprecated carrier config
        // KEY_CARRIER_RCS_PROVISIONING_REQUIRED_BOOL
        if (!retVal) {
            PersistableBundle imsCarrierConfigs = mCarrierConfigManager.getConfigForSubId(subId);
            if (imsCarrierConfigs != null) {
                retVal = imsCarrierConfigs.getBoolean(
                        CarrierConfigManager.KEY_CARRIER_RCS_PROVISIONING_REQUIRED_BOOL);
            } else {
                retVal = CarrierConfigManager.getDefaultConfig().getBoolean(
                        CarrierConfigManager.KEY_CARRIER_RCS_PROVISIONING_REQUIRED_BOOL);
            }
        }

        log("isRcsProvisioningRequiredForCapability capability " + capability
                + " tech " + tech + " return value " + retVal);

        return retVal;
    }

    /**
     * return the provisioning status for MmTel capability in specific radio tech
     */
    @VisibleForTesting
    public boolean getImsProvisioningStatusForCapability(int subId, int capability, int tech) {
        boolean mmTelProvisioned = isImsProvisioningRequiredForCapability(subId, capability, tech);
        if (!mmTelProvisioned) { // provisioning not required
            log("getImsProvisioningStatusForCapability : not required "
                    + " capability " + capability + " tech " + tech);
            return true;
        }

        // read value from ImsProvisioningLoader
        int result = mImsProvisioningLoader.getProvisioningStatus(subId, FEATURE_MMTEL,
                capability, tech);
        if (result == ImsProvisioningLoader.STATUS_NOT_SET) {
            // not set means initial value
            // read data from vendor ImsService and store that in ImsProvisioningLoader
            result = getValueFromImsService(subId, capability, tech);
            mmTelProvisioned = getBoolValue(result);
            if (result != ProvisioningManager.PROVISIONING_RESULT_UNKNOWN) {
                setAndNotifyMmTelProvisioningValue(subId, capability, tech, mmTelProvisioned);
            }
        } else {
            mmTelProvisioned = getBoolValue(result);
        }

        log("getImsProvisioningStatusForCapability : "
                + " capability " + capability
                + " tech " + tech
                + " result " + mmTelProvisioned);
        return mmTelProvisioned;
    }

    /**
     * set MmTel provisioning status in specific tech
     */
    @VisibleForTesting
    public void setImsProvisioningStatusForCapability(int subId, int capability, int tech,
            boolean isProvisioned) {
        boolean mmTelProvisioned = isImsProvisioningRequiredForCapability(subId, capability, tech);
        if (!mmTelProvisioned) { // provisioning not required
            log("setImsProvisioningStatusForCapability : not required "
                    + " capability " + capability + " tech " + tech);
            return;
        }

        // write value to ImsProvisioningLoader
        boolean isChanged = setAndNotifyMmTelProvisioningValue(subId, capability, tech,
                isProvisioned);
        if (!isChanged) {
            log("status not changed mmtel capability " + capability + " tech " + tech);
            return;
        }

        int slotId = getSlotId(subId);
        // find matched key from capability and tech
        int value = getIntValue(isProvisioned);
        int key = getKeyFromCapability(capability, tech);
        if (key != INVALID_VALUE) {
            log("setImsProvisioningStatusForCapability : matched key " + key);
            try {
                // set key and value to vendor ImsService for MmTel
                mMmTelFeatureListenersSlotMap.get(slotId).setProvisioningValue(key, value);
            } catch (NullPointerException e) {
                loge("can not access MmTelFeatureListener with capability " + capability);
            }
        }
    }

    /**
     * return the provisioning status for RCS capability in specific radio tech
     */
    @VisibleForTesting
    public boolean getRcsProvisioningStatusForCapability(int subId, int capability, int tech) {
        boolean rcsProvisioned = isRcsProvisioningRequiredForCapability(subId, capability, tech);
        if (!rcsProvisioned) { // provisioning not required
            log("getRcsProvisioningStatusForCapability : not required"
                    + " capability " + capability + " tech " + tech);
            return true;
        }

        // read data from ImsProvisioningLoader
        int result = mImsProvisioningLoader.getProvisioningStatus(subId, FEATURE_RCS,
                capability, tech);
        if (result == ImsProvisioningLoader.STATUS_NOT_SET) {
            // not set means initial value
            // read data from vendor ImsService and store that in ImsProvisioningLoader
            result = getRcsValueFromImsService(subId, capability);
            rcsProvisioned = getBoolValue(result);
            if (result != ProvisioningManager.PROVISIONING_RESULT_UNKNOWN) {
                setAndNotifyRcsProvisioningValueForAllTech(subId, capability, rcsProvisioned);
            }
        } else {
            rcsProvisioned = getBoolValue(result);
        }

        log("getRcsProvisioningStatusForCapability : "
                + " capability " + capability
                + " tech " + tech
                + " result " + rcsProvisioned);
        return rcsProvisioned;
    }

    /**
     * set RCS provisioning status in specific tech
     */
    @VisibleForTesting
    public void setRcsProvisioningStatusForCapability(int subId, int capability, int tech,
            boolean isProvisioned) {
        boolean rcsProvisioned = isRcsProvisioningRequiredForCapability(subId, capability, tech);
        if (!rcsProvisioned) { // provisioning not required
            log("set rcs provisioning status but not required");
            return;
        }

        // write status using ImsProvisioningLoader
        boolean isChanged = setAndNotifyRcsProvisioningValue(subId, capability, tech,
                isProvisioned);
        if (!isChanged) {
            log("status not changed rcs capability " + capability + " tech " + tech);
            return;
        }

        int slotId = getSlotId(subId);
        int key =  ProvisioningManager.KEY_EAB_PROVISIONING_STATUS;
        int value = getIntValue(isProvisioned);
        try {
            // On some older devices, EAB is managed on the MmTel ImsService when the RCS
            // ImsService is not configured. If there is no RCS ImsService defined, fallback to
            // MmTel. In the rare case that we hit a race condition where the RCS ImsService has
            // crashed or has not come up yet, the value will be synchronized via
            // setInitialProvisioningKeys().
            if (mRcsFeatureListenersSlotMap.get(slotId).isConnectionReady()) {
                mRcsFeatureListenersSlotMap.get(slotId).setProvisioningValue(key, value);
            }

            // EAB provisioning status should be updated to both the Rcs and MmTel ImsService,
            // because the provisioning callback is listening to only MmTel provisioning key
            // changes.
            mMmTelFeatureListenersSlotMap.get(slotId).setProvisioningValue(key, value);
        } catch (NullPointerException e) {
            loge("can not access RcsFeatureListener with capability " + capability);
        }
    }

    /**
     * set RCS provisioning status in specific key and value
     * @param key integer key, defined as one of
     * {@link ProvisioningManager#KEY_VOLTE_PROVISIONING_STATUS}
     * {@link ProvisioningManager#KEY_VT_PROVISIONING_STATUS}
     * {@link ProvisioningManager#KEY_VOICE_OVER_WIFI_ENABLED_OVERRIDE}
     * {@link ProvisioningManager#KEY_EAB_PROVISIONING_STATUS}
     * @param value in Integer format.
     * @return the result of setting the configuration value, defined as one of
     * {@link ImsConfigImplBase#CONFIG_RESULT_FAILED} or
     * {@link ImsConfigImplBase#CONFIG_RESULT_SUCCESS} or
     */
    @VisibleForTesting
    public int setProvisioningValue(int subId, int key, int value) {
        log("setProvisioningValue");

        int retVal = ImsConfigImplBase.CONFIG_RESULT_FAILED;
        // check key value
        if (!Arrays.stream(LOCAL_IMS_CONFIG_KEYS).anyMatch(keyValue -> keyValue == key)) {
            log("not matched key " + key);
            return ImsConfigImplBase.CONFIG_RESULT_UNKNOWN;
        }

        // check subId
        int slotId = getSlotId(subId);
        if (slotId <= SubscriptionManager.INVALID_SIM_SLOT_INDEX || slotId >= mNumSlot) {
            loge("Fail to retrieve slotId from subId");
            return ImsConfigImplBase.CONFIG_RESULT_FAILED;
        }

        try {
            // set key and value to vendor ImsService for MmTel
            // EAB provisioning status should be updated to both the Rcs and MmTel ImsService,
            // because the provisioning callback is listening to only MmTel provisioning key
            // changes.
            retVal = mMmTelFeatureListenersSlotMap.get(slotId).setProvisioningValue(key, value);

            // If the  Rcs ImsService is not available, the EAB provisioning status will be written
            // to the MmTel ImsService for backwards compatibility. In the rare case that this is
            // hit due to RCS ImsService temporarily unavailable, the value will be synchronized
            // via setInitialProvisioningKeys() when the RCS ImsService comes back up.
            if (key == KEY_EAB_PROVISIONING_STATUS
                    && mRcsFeatureListenersSlotMap.get(slotId).isConnectionReady()) {
                // set key and value to vendor ImsService for RCS and use retVal from RCS if
                // related to EAB when possible.
                retVal = mRcsFeatureListenersSlotMap.get(slotId).setProvisioningValue(key, value);
            }
        } catch (NullPointerException e) {
            loge("can not access FeatureListener to set provisioning value");
            return ImsConfigImplBase.CONFIG_RESULT_FAILED;
        }

        // update and notify provisioning status changed capability and tech from key
        updateCapabilityTechFromKey(subId, key, value);

        return retVal;
    }

    /**
     * get RCS provisioning status in specific key and value
     * @param key integer key, defined as one of
     * {@link ProvisioningManager#KEY_VOLTE_PROVISIONING_STATUS}
     * {@link ProvisioningManager#KEY_VT_PROVISIONING_STATUS}
     * {@link ProvisioningManager#KEY_VOICE_OVER_WIFI_ENABLED_OVERRIDE}
     * {@link ProvisioningManager#KEY_EAB_PROVISIONING_STATUS}
     * @return the result of setting the configuration value, defined as one of
     * {@link ImsConfigImplBase#CONFIG_RESULT_FAILED} or
     * {@link ImsConfigImplBase#CONFIG_RESULT_SUCCESS} or
     * {@link ImsConfigImplBase#CONFIG_RESULT_UNKNOWN}
     */
    @VisibleForTesting
    public int getProvisioningValue(int subId, int key) {
        // check key value
        if (!Arrays.stream(LOCAL_IMS_CONFIG_KEYS).anyMatch(keyValue -> keyValue == key)) {
            log("not matched key " + key);
            return ImsConfigImplBase.CONFIG_RESULT_UNKNOWN;
        }

        // check subId
        int slotId = getSlotId(subId);
        if (slotId <= SubscriptionManager.INVALID_SIM_SLOT_INDEX || slotId >= mNumSlot) {
            loge("Fail to retrieve slotId from subId");
            return ImsConfigImplBase.CONFIG_RESULT_UNKNOWN;
        }

        // check data from ImsProvisioningLoader
        int capability = getCapabilityFromKey(key);
        int tech = getTechFromKey(key);
        int result;
        if (capability != INVALID_VALUE && tech != INVALID_VALUE) {
            if (key == KEY_EAB_PROVISIONING_STATUS) {
                result = mImsProvisioningLoader.getProvisioningStatus(subId, FEATURE_RCS,
                        capability, tech);
            } else {
                result = mImsProvisioningLoader.getProvisioningStatus(subId, FEATURE_MMTEL,
                        capability, tech);
            }
            if (result != ImsProvisioningLoader.STATUS_NOT_SET) {
                log("getProvisioningValue from loader : key " + key + " result " + result);
                return result;
            }
        }

        // get data from ImsService, update it in ImsProvisioningLoader
        if (key == KEY_EAB_PROVISIONING_STATUS) {
            result = getRcsValueFromImsService(subId, capability);
            if (result == ImsConfigImplBase.CONFIG_RESULT_UNKNOWN) {
                logw("getProvisioningValue : fail to get data from ImsService capability"
                        + capability);
                return result;
            }
            log("getProvisioningValue from vendor : key " + key + " result " + result);

            setAndNotifyRcsProvisioningValueForAllTech(subId, capability, getBoolValue(result));
            return result;
        } else {
            result = getValueFromImsService(subId, capability, tech);
            if (result == ImsConfigImplBase.CONFIG_RESULT_UNKNOWN) {
                logw("getProvisioningValue : fail to get data from ImsService capability"
                        + capability);
                return result;
            }
            log("getProvisioningValue from vendor : key " + key + " result " + result);

            setAndNotifyMmTelProvisioningValue(subId, capability, tech, getBoolValue(result));
            return result;
        }
    }

    /**
     * get the handler
     */
    @VisibleForTesting
    public Handler getHandler() {
        return mHandler;
    }

    private boolean isProvisioningRequired(int subId, int capability, int tech, boolean isMmTel) {
        int[] techArray;
        techArray = getTechsFromCarrierConfig(subId, capability, isMmTel);
        if (techArray == null) {
            logw("isProvisioningRequired : getTechsFromCarrierConfig failed");
            // not exist in CarrierConfig that means provisioning is not required
            return false;
        }

        // compare with carrier config
        if (Arrays.stream(techArray).anyMatch(keyValue -> keyValue == tech)) {
            // existing same tech means provisioning required
            return true;
        }

        log("isProvisioningRequired : not matched capability " + capability + " tech " + tech);
        return false;
    }

    private int[] getTechsFromCarrierConfig(int subId, int capability, boolean isMmTel) {
        String featureKey;
        String capabilityKey;
        if (isMmTel) {
            featureKey = CarrierConfigManager.Ims.KEY_MMTEL_REQUIRES_PROVISIONING_BUNDLE;
            capabilityKey = KEYS_MMTEL_CAPABILITY.get(capability);
        } else {
            featureKey = CarrierConfigManager.Ims.KEY_RCS_REQUIRES_PROVISIONING_BUNDLE;
            capabilityKey = KEYS_RCS_CAPABILITY.get(capability);
        }

        if (capabilityKey != null) {
            PersistableBundle imsCarrierConfigs = mCarrierConfigManager.getConfigForSubId(subId);
            if (imsCarrierConfigs == null) {
                log("getTechsFromCarrierConfig : imsCarrierConfigs null");
                return null;
            }

            PersistableBundle provisioningBundle =
                    imsCarrierConfigs.getPersistableBundle(featureKey);
            if (provisioningBundle == null) {
                log("getTechsFromCarrierConfig : provisioningBundle null");
                return null;
            }

            return provisioningBundle.getIntArray(capabilityKey);
        }

        return null;
    }

    private int getValueFromImsService(int subId, int capability, int tech) {
        int config = ImsConfigImplBase.CONFIG_RESULT_UNKNOWN;

        // operation is based on capability
        switch (capability) {
            case CAPABILITY_TYPE_VOICE:
                int item = (tech == ImsRegistrationImplBase.REGISTRATION_TECH_IWLAN)
                        ? ProvisioningManager.KEY_VOICE_OVER_WIFI_ENABLED_OVERRIDE
                        : ProvisioningManager.KEY_VOLTE_PROVISIONING_STATUS;
                // read data from vendor ImsService
                config = mMmTelFeatureListenersSlotMap.get(getSlotId(subId))
                        .getProvisioningValue(item);
                break;
            case CAPABILITY_TYPE_VIDEO:
                // read data from vendor ImsService
                config = mMmTelFeatureListenersSlotMap.get(getSlotId(subId))
                        .getProvisioningValue(ProvisioningManager.KEY_VT_PROVISIONING_STATUS);
                break;
            default:
                log("Capability " + capability + " has been provisioning");
                break;
        }

        return config;
    }

    private int getRcsValueFromImsService(int subId, int capability) {
        int config = ImsConfigImplBase.CONFIG_RESULT_UNKNOWN;
        int slotId = getSlotId(subId);

        if (capability != CAPABILITY_TYPE_PRESENCE_UCE) {
            log("Capability " + capability + " has been provisioning");
            return config;
        }
        try {
            if (mRcsFeatureListenersSlotMap.get(slotId).isConnectionReady()) {
                config = mRcsFeatureListenersSlotMap.get(slotId)
                        .getProvisioningValue(ProvisioningManager.KEY_EAB_PROVISIONING_STATUS);
            } else {
                log("Rcs ImsService is not available, "
                        + "EAB provisioning status should be read from MmTel ImsService");
                config = mMmTelFeatureListenersSlotMap.get(slotId)
                        .getProvisioningValue(ProvisioningManager.KEY_EAB_PROVISIONING_STATUS);
            }
        } catch (NullPointerException e) {
            logw("can not access FeatureListener : " + e.getMessage());
        }

        return config;
    }

    private void onSubscriptionsChanged() {
        for (int index = 0; index < mMmTelFeatureListenersSlotMap.size(); index++) {
            MmTelFeatureListener m = mMmTelFeatureListenersSlotMap.get(index);
            m.setSubId(getSubId(index));
        }
        for (int index = 0; index < mRcsFeatureListenersSlotMap.size(); index++) {
            RcsFeatureListener r = mRcsFeatureListenersSlotMap.get(index);
            r.setSubId(getSubId(index));
        }
        for (int index = 0; index < mProvisioningCallbackManagersSlotMap.size(); index++) {
            ProvisioningCallbackManager m = mProvisioningCallbackManagersSlotMap.get(index);
            m.setSubId(getSubId(index));
        }
    }

    private void  updateCapabilityTechFromKey(int subId, int key, int value) {
        boolean isProvisioned = getBoolValue(value);
        int capability = getCapabilityFromKey(key);
        int tech = getTechFromKey(key);

        if (capability == INVALID_VALUE || tech == INVALID_VALUE) {
            logw("updateCapabilityTechFromKey : unknown key " + key);
            return;
        }

        if (key == KEY_VOLTE_PROVISIONING_STATUS
                || key == KEY_VT_PROVISIONING_STATUS
                || key == KEY_VOICE_OVER_WIFI_ENABLED_OVERRIDE) {
            setAndNotifyMmTelProvisioningValue(subId, capability, tech, isProvisioned);
        }
        if (key == KEY_EAB_PROVISIONING_STATUS) {
            setAndNotifyRcsProvisioningValueForAllTech(subId, capability, isProvisioned);
        }
    }

    private int getCapabilityFromKey(int key) {
        int capability;
        switch (key) {
            case ProvisioningManager.KEY_VOLTE_PROVISIONING_STATUS:
                // intentional fallthrough
            case ProvisioningManager.KEY_VOICE_OVER_WIFI_ENABLED_OVERRIDE:
                capability = CAPABILITY_TYPE_VOICE;
                break;
            case ProvisioningManager.KEY_VT_PROVISIONING_STATUS:
                capability = CAPABILITY_TYPE_VIDEO;
                break;
            case ProvisioningManager.KEY_EAB_PROVISIONING_STATUS:
                // default CAPABILITY_TYPE_PRESENCE_UCE used for KEY_EAB_PROVISIONING_STATUS
                capability = CAPABILITY_TYPE_PRESENCE_UCE;
                break;
            default:
                capability = INVALID_VALUE;
                break;
        }
        return capability;
    }

    private int getTechFromKey(int key) {
        int tech;
        switch (key) {
            case ProvisioningManager.KEY_VOICE_OVER_WIFI_ENABLED_OVERRIDE:
                tech = ImsRegistrationImplBase.REGISTRATION_TECH_IWLAN;
                break;
            case ProvisioningManager.KEY_VOLTE_PROVISIONING_STATUS:
                // intentional fallthrough
            case ProvisioningManager.KEY_VT_PROVISIONING_STATUS:
                // intentional fallthrough
            case ProvisioningManager.KEY_EAB_PROVISIONING_STATUS:
                tech = ImsRegistrationImplBase.REGISTRATION_TECH_LTE;
                break;
            default:
                tech = INVALID_VALUE;
                break;
        }
        return tech;
    }

    private int getKeyFromCapability(int capability, int tech) {
        int key = INVALID_VALUE;
        if (capability == CAPABILITY_TYPE_VOICE && tech == REGISTRATION_TECH_IWLAN) {
            key = ProvisioningManager.KEY_VOICE_OVER_WIFI_ENABLED_OVERRIDE;
        } else if (capability == CAPABILITY_TYPE_VOICE && tech == REGISTRATION_TECH_LTE) {
            key = ProvisioningManager.KEY_VOLTE_PROVISIONING_STATUS;
        } else if (capability == CAPABILITY_TYPE_VIDEO && tech == REGISTRATION_TECH_LTE) {
            key = ProvisioningManager.KEY_VT_PROVISIONING_STATUS;
        }

        return key;
    }

    protected int getSubId(int slotId) {
        return SubscriptionManager.getSubscriptionId(slotId);
    }

    protected int getSlotId(int subId) {
        return mSubscriptionManager.getPhoneId(subId);
    }

    protected ImsConfig getImsConfig(ImsManager imsManager) throws ImsException {
        return imsManager.getConfigInterface();
    }

    protected ImsConfig getImsConfig(IImsConfig iImsConfig) {
        return new ImsConfig(iImsConfig);
    }

    private int getIntValue(boolean isProvisioned) {
        return isProvisioned ? ProvisioningManager.PROVISIONING_VALUE_ENABLED
                : ProvisioningManager.PROVISIONING_VALUE_DISABLED;
    }

    private boolean getBoolValue(int value) {
        return value == ProvisioningManager.PROVISIONING_VALUE_ENABLED ? true : false;
    }

    private boolean setAndNotifyMmTelProvisioningValue(int subId, int capability, int tech,
            boolean isProvisioned) {
        boolean changed = mImsProvisioningLoader.setProvisioningStatus(subId, FEATURE_MMTEL,
                capability, tech, isProvisioned);
        // notify MmTel capability changed
        if (changed) {
            mHandler.sendMessage(mHandler.obtainMessage(EVENT_PROVISIONING_CAPABILITY_CHANGED,
                    getSlotId(subId), 0, (Object) new FeatureProvisioningData(
                            capability, tech, isProvisioned, /*isMmTel*/true)));
        }

        return changed;
    }

    private boolean setAndNotifyRcsProvisioningValue(int subId, int capability, int tech,
            boolean isProvisioned) {
        boolean isChanged = mImsProvisioningLoader.setProvisioningStatus(subId, FEATURE_RCS,
                capability, tech, isProvisioned);

        if (isChanged) {
            int slotId = getSlotId(subId);

            // notify RCS capability changed
            mHandler.sendMessage(mHandler.obtainMessage(EVENT_PROVISIONING_CAPABILITY_CHANGED,
                    slotId, 0, (Object) new FeatureProvisioningData(
                            capability, tech, isProvisioned, /*isMmtel*/false)));
        }

        return isChanged;
    }

    private boolean setAndNotifyRcsProvisioningValueForAllTech(int subId, int capability,
            boolean isProvisioned) {
        boolean isChanged = false;

        for (int tech : LOCAL_RADIO_TECHS) {
            isChanged |= setAndNotifyRcsProvisioningValue(subId, capability, tech, isProvisioned);
        }

        return isChanged;
    }

    protected boolean isValidSubId(int subId) {
        int slotId = getSlotId(subId);
        if (slotId <= SubscriptionManager.INVALID_SIM_SLOT_INDEX || slotId >= mNumSlot) {
            return false;
        }

        return true;
    }

    private void log(String s) {
        Rlog.d(TAG, s);
    }

    private void log(String prefix, int slotId, String s) {
        Rlog.d(TAG, prefix + "[" + slotId + "] " + s);
    }

    private void logi(String prefix, int slotId, String s) {
        Rlog.i(TAG, prefix + "[" + slotId + "] " + s);
    }

    private void logw(String s) {
        Rlog.w(TAG, s);
    }

    private void logw(String prefix, int slotId, String s) {
        Rlog.w(TAG, prefix + "[" + slotId + "] " + s);
    }

    private void loge(String s) {
        Rlog.e(TAG, s);
    }

    private void loge(String prefix, int slotId, String s) {
        Rlog.e(TAG, prefix + "[" + slotId + "] " + s);
    }
}
