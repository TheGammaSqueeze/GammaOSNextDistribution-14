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

package com.android.phone;

import static com.android.internal.telephony.TelephonyStatsLog.RCS_ACS_PROVISIONING_STATS__RESPONSE_TYPE__PROVISIONING_XML;
import static com.android.internal.telephony.TelephonyStatsLog.RCS_CLIENT_PROVISIONING_STATS__EVENT__DMA_CHANGED;
import static com.android.internal.telephony.TelephonyStatsLog.RCS_CLIENT_PROVISIONING_STATS__EVENT__TRIGGER_RCS_RECONFIGURATION;

import android.Manifest;
import android.app.role.OnRoleHoldersChangedListener;
import android.app.role.RoleManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.os.UserHandle;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyRegistryManager;
import android.telephony.ims.ProvisioningManager;
import android.telephony.ims.RcsConfig;
import android.telephony.ims.aidl.IImsConfig;
import android.telephony.ims.aidl.IRcsConfigCallback;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.SparseArray;

import com.android.ims.FeatureConnector;
import com.android.ims.FeatureUpdates;
import com.android.ims.RcsFeatureManager;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.metrics.RcsStats;
import com.android.internal.telephony.metrics.RcsStats.RcsProvisioningCallback;
import com.android.internal.telephony.util.HandlerExecutor;
import com.android.internal.util.CollectionUtils;
import com.android.telephony.Rlog;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Class to monitor RCS Provisioning Status
 */
public class RcsProvisioningMonitor {
    private static final String TAG = "RcsProvisioningMonitor";
    private static final boolean DBG = Build.IS_ENG;

    private static final int EVENT_SUB_CHANGED = 1;
    private static final int EVENT_DMA_CHANGED = 2;
    private static final int EVENT_CC_CHANGED  = 3;
    private static final int EVENT_CONFIG_RECEIVED = 4;
    private static final int EVENT_RECONFIG_REQUEST = 5;
    private static final int EVENT_DEVICE_CONFIG_OVERRIDE = 6;
    private static final int EVENT_CARRIER_CONFIG_OVERRIDE = 7;
    private static final int EVENT_RESET = 8;
    private static final int EVENT_FEATURE_ENABLED_OVERRIDE = 9;

    // indicate that the carrier single registration capable is initial value as
    // carrier config is not ready yet.
    private static final int MASK_CAP_CARRIER_INIT = 0xF000;

    private final PhoneGlobals mPhone;
    private final Handler mHandler;
    // Cache the RCS provsioning info and related sub id
    private final ConcurrentHashMap<Integer, RcsProvisioningInfo> mRcsProvisioningInfos =
            new ConcurrentHashMap<>();
    private Boolean mDeviceSingleRegistrationEnabledOverride;
    private final HashMap<Integer, Boolean> mCarrierSingleRegistrationEnabledOverride =
            new HashMap<>();
    private final ConcurrentHashMap<Integer, Boolean> mImsFeatureValidationOverride =
            new ConcurrentHashMap<>();
    private String mDmaPackageName;
    private final SparseArray<RcsFeatureListener> mRcsFeatureListeners = new SparseArray<>();
    private volatile boolean mTestModeEnabled;

    private final CarrierConfigManager mCarrierConfigManager;
    private final DmaChangedListener mDmaChangedListener;
    private final SubscriptionManager mSubscriptionManager;
    private final TelephonyRegistryManager mTelephonyRegistryManager;
    private final RoleManagerAdapter mRoleManager;
    private FeatureConnectorFactory<RcsFeatureManager> mFeatureFactory;

    private RcsStats mRcsStats;

    private static RcsProvisioningMonitor sInstance;

    private final SubscriptionManager.OnSubscriptionsChangedListener mSubChangedListener =
            new SubscriptionManager.OnSubscriptionsChangedListener() {
        @Override
        public void onSubscriptionsChanged() {
            if (!mHandler.hasMessages(EVENT_SUB_CHANGED)) {
                mHandler.sendEmptyMessage(EVENT_SUB_CHANGED);
            }
        }
    };

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED.equals(
                    intent.getAction())) {
                int subId = intent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,
                        SubscriptionManager.INVALID_SUBSCRIPTION_ID);
                logv("Carrier-config changed for sub : " + subId);
                if (SubscriptionManager.isValidSubscriptionId(subId)
                        && !mHandler.hasMessages(EVENT_CC_CHANGED)) {
                    mHandler.sendEmptyMessage(EVENT_CC_CHANGED);
                }
            }
        }
    };

    private final class DmaChangedListener implements OnRoleHoldersChangedListener {
        @Override
        public void onRoleHoldersChanged(String role, UserHandle user) {
            if (RoleManager.ROLE_SMS.equals(role)) {
                logv("default messaging application changed.");
                mHandler.sendEmptyMessage(EVENT_DMA_CHANGED);
            }
        }

        public void register() {
            try {
                mRoleManager.addOnRoleHoldersChangedListenerAsUser(
                        mPhone.getMainExecutor(), this, UserHandle.SYSTEM);
            } catch (RuntimeException e) {
                loge("Could not register dma change listener due to " + e);
            }
        }

        public void unregister() {
            try {
                mRoleManager.removeOnRoleHoldersChangedListenerAsUser(this, UserHandle.SYSTEM);
            } catch (RuntimeException e) {
                loge("Could not unregister dma change listener due to " + e);
            }
        }
    }

    private final class MyHandler extends Handler {
        MyHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            logv("handleMessage: " + msg);
            switch (msg.what) {
                case EVENT_SUB_CHANGED:
                    onSubChanged();
                    break;
                case EVENT_DMA_CHANGED:
                    onDefaultMessagingApplicationChanged();
                    break;
                case EVENT_CC_CHANGED:
                    onCarrierConfigChange();
                    break;
                case EVENT_CONFIG_RECEIVED:
                    onConfigReceived(msg.arg1, (byte[]) msg.obj, msg.arg2 == 1);
                    break;
                case EVENT_RECONFIG_REQUEST:
                    onReconfigRequest(msg.arg1);
                    break;
                case EVENT_DEVICE_CONFIG_OVERRIDE:
                    Boolean deviceEnabled = (Boolean) msg.obj;
                    if (!booleanEquals(deviceEnabled, mDeviceSingleRegistrationEnabledOverride)) {
                        mDeviceSingleRegistrationEnabledOverride = deviceEnabled;
                        onCarrierConfigChange();
                    }
                    break;
                case EVENT_CARRIER_CONFIG_OVERRIDE:
                    Boolean carrierEnabledOverride = (Boolean) msg.obj;
                    Boolean carrierEnabled = mCarrierSingleRegistrationEnabledOverride.put(
                            msg.arg1, carrierEnabledOverride);
                    if (!booleanEquals(carrierEnabledOverride, carrierEnabled)) {
                        onCarrierConfigChange();
                    }
                    break;
                case EVENT_RESET:
                    reset();
                    break;
                default:
                    loge("Unhandled event " + msg.what);
            }
        }
    }

    private final class RcsProvisioningInfo {
        private int mSubId;
        private volatile int mSingleRegistrationCapability;
        private volatile byte[] mConfig;
        private ArraySet<IRcsConfigCallback> mRcsConfigCallbacks;
        private IImsConfig mIImsConfig;
        private boolean mHasReconfigRequest;

        RcsProvisioningInfo(int subId, int singleRegistrationCapability, byte[] config) {
            mSubId = subId;
            mSingleRegistrationCapability = singleRegistrationCapability;
            mConfig = config;
            mRcsConfigCallbacks = new ArraySet<>();
            registerRcsFeatureListener(this);
        }

        int getSubId() {
            return mSubId;
        }

        void setSingleRegistrationCapability(int singleRegistrationCapability) {
            if (mSingleRegistrationCapability != singleRegistrationCapability) {
                mSingleRegistrationCapability = singleRegistrationCapability;
                notifyDma();

                // update whether single registration supported.
                mRcsStats.setEnableSingleRegistration(mSubId,
                        mSingleRegistrationCapability == ProvisioningManager.STATUS_CAPABLE);
            }
        }

        void notifyDma() {
            // notify only if capable value has been updated when carrier config ready.
            if ((mSingleRegistrationCapability & MASK_CAP_CARRIER_INIT) != MASK_CAP_CARRIER_INIT) {
                logi("notify default messaging app for sub:" + mSubId + " with capability:"
                        + mSingleRegistrationCapability);
                notifyDmaForSub(mSubId, mSingleRegistrationCapability);
            }
        }

        int getSingleRegistrationCapability() {
            return mSingleRegistrationCapability;
        }

        void setConfig(byte[] config) {
            if (!Arrays.equals(mConfig, config)) {
                mConfig = config;
                if (mConfig != null) {
                    notifyRcsAutoConfigurationReceived();
                } else {
                    notifyRcsAutoConfigurationRemoved();
                }
            }
        }

        byte[] getConfig() {
            return mConfig;
        }

        boolean addRcsConfigCallback(IRcsConfigCallback cb) {
            if (mIImsConfig == null) {
                logd("fail to addRcsConfigCallback as imsConfig is null");
                return false;
            }

            synchronized (mRcsConfigCallbacks) {
                try {
                    mIImsConfig.addRcsConfigCallback(cb);
                } catch (RemoteException e) {
                    loge("fail to addRcsConfigCallback due to " + e);
                    return false;
                }
                mRcsConfigCallbacks.add(cb);
            }
            return true;
        }

        boolean removeRcsConfigCallback(IRcsConfigCallback cb) {
            boolean result = true;

            synchronized (mRcsConfigCallbacks) {
                if (mIImsConfig != null) {
                    try {
                        mIImsConfig.removeRcsConfigCallback(cb);
                    } catch (RemoteException e) {
                        loge("fail to removeRcsConfigCallback due to " + e);
                    }
                } else {
                    // Return false but continue to remove the callback
                    result = false;
                }

                try {
                    cb.onRemoved();
                } catch (RemoteException e) {
                    logd("Failed to notify onRemoved due to dead binder of " + cb);
                }
                mRcsConfigCallbacks.remove(cb);
            }
            return result;
        }

        void triggerRcsReconfiguration() {
            if (mIImsConfig != null) {
                try {
                    logv("triggerRcsReconfiguration for sub:" + mSubId);
                    mIImsConfig.triggerRcsReconfiguration();
                    mHasReconfigRequest = false;
                } catch (RemoteException e) {
                    loge("triggerRcsReconfiguration failed due to " + e);
                }
            } else {
                logd("triggerRcsReconfiguration failed due to IImsConfig null.");
                mHasReconfigRequest = true;
            }
        }

        void destroy() {
            unregisterRcsFeatureListener(this);
            clear();
            mIImsConfig = null;
            mRcsConfigCallbacks = null;
        }

        void clear() {
            setConfig(null);
            clearCallbacks();
        }

        void onRcsStatusChanged(IImsConfig binder) {
            logv("onRcsStatusChanged for sub:" + mSubId + ", IImsConfig?" + binder);
            if (mIImsConfig != binder) {
                mIImsConfig = binder;
                if (mIImsConfig != null) {
                    if (mHasReconfigRequest) {
                        triggerRcsReconfiguration();
                    } else {
                        notifyRcsAutoConfigurationReceived();
                    }

                    // check callback for metrics if not registered, register callback
                    registerMetricsCallback();
                } else {
                    // clear callbacks if rcs disconnected
                    clearCallbacks();
                }
            }
        }

        private void notifyRcsAutoConfigurationReceived() {
            if (mConfig == null) {
                logd("Rcs config is null for sub : " + mSubId);
                return;
            }

            if (mIImsConfig != null) {
                try {
                    logv("notifyRcsAutoConfigurationReceived for sub:" + mSubId);
                    mIImsConfig.notifyRcsAutoConfigurationReceived(mConfig, false);
                } catch (RemoteException e) {
                    loge("notifyRcsAutoConfigurationReceived failed due to " + e);
                }
            } else {
                logd("notifyRcsAutoConfigurationReceived failed due to IImsConfig null.");
            }
        }

        private void notifyRcsAutoConfigurationRemoved() {
            if (mIImsConfig != null) {
                try {
                    logv("notifyRcsAutoConfigurationRemoved for sub:" + mSubId);
                    mIImsConfig.notifyRcsAutoConfigurationRemoved();
                } catch (RemoteException e) {
                    loge("notifyRcsAutoConfigurationRemoved failed due to " + e);
                }
            } else {
                logd("notifyRcsAutoConfigurationRemoved failed due to IImsConfig null.");
            }
        }

        private void clearCallbacks() {
            synchronized (mRcsConfigCallbacks) {
                Iterator<IRcsConfigCallback> it = mRcsConfigCallbacks.iterator();
                while (it.hasNext()) {
                    IRcsConfigCallback cb = it.next();
                    if (mIImsConfig != null) {
                        try {
                            mIImsConfig.removeRcsConfigCallback(cb);
                        } catch (RemoteException e) {
                            loge("fail to removeRcsConfigCallback due to " + e);
                        }
                    }
                    try {
                        cb.onRemoved();
                    } catch (RemoteException e) {
                        logd("Failed to notify onRemoved due to dead binder of " + cb);
                    }
                    it.remove();
                }
            }
        }

        private void registerMetricsCallback() {
            RcsProvisioningCallback rcsProvisioningCallback = mRcsStats.getRcsProvisioningCallback(
                    mSubId, mSingleRegistrationCapability == ProvisioningManager.STATUS_CAPABLE);

            // if not yet registered, register callback and set registered value
            if (rcsProvisioningCallback != null && !rcsProvisioningCallback.getRegistered()) {
                if (addRcsConfigCallback(rcsProvisioningCallback)) {
                    rcsProvisioningCallback.setRegistered(true);
                }
            }
        }
    }

    @VisibleForTesting
    public interface FeatureConnectorFactory<U extends FeatureUpdates> {
        /**
         * @return a {@link FeatureConnector} associated for the given {@link FeatureUpdates}
         * and slot index.
         */
        FeatureConnector<U> create(Context context, int slotIndex,
                FeatureConnector.Listener<U> listener, Executor executor, String logPrefix);
    }

    private final class RcsFeatureListener implements FeatureConnector.Listener<RcsFeatureManager> {
        private final ArraySet<RcsProvisioningInfo> mRcsProvisioningInfos = new ArraySet<>();
        private RcsFeatureManager mRcsFeatureManager;
        private FeatureConnector<RcsFeatureManager> mConnector;

        RcsFeatureListener(int slotId) {
            mConnector = mFeatureFactory.create(
                    mPhone, slotId, this, new HandlerExecutor(mHandler), TAG);
            mConnector.connect();
        }

        void destroy() {
            mConnector.disconnect();
            mConnector = null;
            mRcsFeatureManager = null;
            mRcsProvisioningInfos.clear();
        }

        void addRcsProvisioningInfo(RcsProvisioningInfo info) {
            if (!mRcsProvisioningInfos.contains(info)) {
                mRcsProvisioningInfos.add(info);
                info.onRcsStatusChanged(mRcsFeatureManager == null ? null
                        : mRcsFeatureManager.getConfig());
            }
        }

        void removeRcsProvisioningInfo(RcsProvisioningInfo info) {
            mRcsProvisioningInfos.remove(info);
        }

        @Override
        public void connectionReady(RcsFeatureManager manager, int subId) {
            mRcsFeatureManager = manager;
            mRcsProvisioningInfos.forEach(v -> v.onRcsStatusChanged(manager.getConfig()));
        }

        @Override
        public void connectionUnavailable(int reason) {
            mRcsFeatureManager = null;
            mRcsProvisioningInfos.forEach(v -> v.onRcsStatusChanged(null));
        }
    }

    @VisibleForTesting
    public RcsProvisioningMonitor(PhoneGlobals app, Looper looper, RoleManagerAdapter roleManager,
            FeatureConnectorFactory<RcsFeatureManager> factory, RcsStats rcsStats) {
        mPhone = app;
        mHandler = new MyHandler(looper);
        mCarrierConfigManager = mPhone.getSystemService(CarrierConfigManager.class);
        mSubscriptionManager = mPhone.getSystemService(SubscriptionManager.class);
        mTelephonyRegistryManager = mPhone.getSystemService(TelephonyRegistryManager.class);
        mRoleManager = roleManager;
        mDmaPackageName = getDmaPackageName();
        logv("DMA is " + mDmaPackageName);
        mDmaChangedListener = new DmaChangedListener();
        mFeatureFactory = factory;
        mRcsStats = rcsStats;
        init();
    }

    /**
     * create an instance
     */
    public static RcsProvisioningMonitor make(PhoneGlobals app) {
        if (sInstance == null) {
            logd("RcsProvisioningMonitor created.");
            HandlerThread handlerThread = new HandlerThread(TAG);
            handlerThread.start();
            sInstance = new RcsProvisioningMonitor(app, handlerThread.getLooper(),
                    new RoleManagerAdapterImpl(app), RcsFeatureManager::getConnector,
                    RcsStats.getInstance());
        }
        return sInstance;
    }

    /**
     * get the instance
     */
    public static RcsProvisioningMonitor getInstance() {
        return sInstance;
    }

    private void init() {
        logd("init.");
        IntentFilter filter = new IntentFilter();
        filter.addAction(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        mPhone.registerReceiver(mReceiver, filter);
        mTelephonyRegistryManager.addOnSubscriptionsChangedListener(
                mSubChangedListener, mHandler::post);
        mDmaChangedListener.register();
        //initialize configs for all active sub
        onSubChanged();
    }

    private void release() {
        logd("release.");
        mDmaChangedListener.unregister();
        mTelephonyRegistryManager.removeOnSubscriptionsChangedListener(mSubChangedListener);
        mPhone.unregisterReceiver(mReceiver);
        for (int i = 0; i < mRcsFeatureListeners.size(); i++) {
            mRcsFeatureListeners.valueAt(i).destroy();
        }
        mRcsFeatureListeners.clear();
        mRcsProvisioningInfos.forEach((k, v)->v.destroy());
        mRcsProvisioningInfos.clear();
        mCarrierSingleRegistrationEnabledOverride.clear();
    }

    private void reset() {
        release();
        init();
    }

    /**
     * destroy the instance
     */
    @VisibleForTesting
    public void destroy() {
        logd("destroy it.");
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
     * Gets the config for a subscription
     */
    @VisibleForTesting
    public byte[] getConfig(int subId) {
        if (mRcsProvisioningInfos.containsKey(subId)) {
            return mRcsProvisioningInfos.get(subId).getConfig();
        }
        return null;
    }

    /**
     * Returns whether Rcs Volte single registration is enabled for the sub.
     */
    public Boolean isRcsVolteSingleRegistrationEnabled(int subId) {
        if (mRcsProvisioningInfos.containsKey(subId)) {
            return mRcsProvisioningInfos.get(subId).getSingleRegistrationCapability()
                    == ProvisioningManager.STATUS_CAPABLE;
        }
        return null;
    }

    /**
     * Called when the new rcs config is received
     */
    public void updateConfig(int subId, byte[] config, boolean isCompressed) {
        mHandler.sendMessage(mHandler.obtainMessage(
                EVENT_CONFIG_RECEIVED, subId, isCompressed ? 1 : 0, config));
    }

    /**
     * Called when the application needs rcs re-config
     */
    public void requestReconfig(int subId) {
        mHandler.sendMessage(mHandler.obtainMessage(EVENT_RECONFIG_REQUEST, subId, 0));
    }

    /**
     * Called when the application registers rcs provisioning callback
     */
    public boolean registerRcsProvisioningCallback(int subId, IRcsConfigCallback cb) {
        RcsProvisioningInfo info = mRcsProvisioningInfos.get(subId);
        // should not happen in normal case
        if (info == null) {
            logd("fail to register rcs provisioning callback due to subscription unavailable");
            return false;
        }

        return info.addRcsConfigCallback(cb);
    }

    /**
     * Called when the application unregisters rcs provisioning callback
     */
    public boolean unregisterRcsProvisioningCallback(int subId, IRcsConfigCallback cb) {
        RcsProvisioningInfo info = mRcsProvisioningInfos.get(subId);
        // should not happen in normal case
        if (info == null) {
            logd("fail to unregister rcs provisioning changed due to subscription unavailable");
            return false;
        }

        return info.removeRcsConfigCallback(cb);
    }

    /**
     * Enables or disables test mode.
     *
     * <p> If test mode is enabled, any rcs config change will not update the database.
     */
    public void setTestModeEnabled(boolean enabled) {
        logv("setTestModeEnabled as " + enabled);
        if (mTestModeEnabled != enabled) {
            mTestModeEnabled = enabled;
            mHandler.sendMessage(mHandler.obtainMessage(EVENT_RESET));
        }
    }


    /**
     * Returns whether the test mode is enabled.
     */
    public boolean getTestModeEnabled() {
        return mTestModeEnabled;
    }

    /**
     * override the device config whether single registration is enabled
     */
    public void overrideDeviceSingleRegistrationEnabled(Boolean enabled) {
        mHandler.sendMessage(mHandler.obtainMessage(EVENT_DEVICE_CONFIG_OVERRIDE, enabled));
    }

    /**
     * Overrides the carrier config whether single registration is enabled
     */
    public boolean overrideCarrierSingleRegistrationEnabled(int subId, Boolean enabled) {
        if (!mRcsProvisioningInfos.containsKey(subId)) {
            return false;
        }
        mHandler.sendMessage(mHandler.obtainMessage(
                EVENT_CARRIER_CONFIG_OVERRIDE, subId, 0, enabled));
        return true;
    }

    /**
     * override the rcs feature validation result for a subscription
     */
    public boolean overrideImsFeatureValidation(int subId, Boolean enabled) {
        if (enabled == null) {
            mImsFeatureValidationOverride.remove(subId);
        } else {
            mImsFeatureValidationOverride.put(subId, enabled);
        }
        return true;
    }

    /**
     * Returns the device config whether single registration is enabled
     */
    public boolean getDeviceSingleRegistrationEnabled() {
        for (RcsProvisioningInfo info : mRcsProvisioningInfos.values()) {
            return (info.getSingleRegistrationCapability()
                    & ProvisioningManager.STATUS_DEVICE_NOT_CAPABLE) == 0;
        }
        return false;
    }

    /**
     * Returns the carrier config whether single registration is enabled
     */
    public boolean getCarrierSingleRegistrationEnabled(int subId) {
        if (mRcsProvisioningInfos.containsKey(subId)) {
            return (mRcsProvisioningInfos.get(subId).getSingleRegistrationCapability()
                    & ProvisioningManager.STATUS_CARRIER_NOT_CAPABLE) == 0;
        }
        return false;
    }

    /**
     * Returns the rcs feature validation override value, null if it is not set.
     */
    public Boolean getImsFeatureValidationOverride(int subId) {
        return mImsFeatureValidationOverride.get(subId);
    }

    private void onDefaultMessagingApplicationChanged() {
        final String packageName = getDmaPackageName();
        if (!TextUtils.equals(mDmaPackageName, packageName)) {
            mDmaPackageName = packageName;
            logv("new default messaging application " + mDmaPackageName);

            mRcsProvisioningInfos.forEach((k, v) -> {
                v.notifyDma();

                byte[] cachedConfig = v.getConfig();
                //clear old callbacks
                v.clear();
                if (isAcsUsed(k)) {
                    logv("acs used, trigger to re-configure.");
                    updateConfigForSub(k, null, true);
                    v.triggerRcsReconfiguration();
                } else {
                    logv("acs not used, set cached config and notify.");
                    v.setConfig(cachedConfig);
                }

                // store RCS metrics - DMA changed event
                mRcsStats.onRcsClientProvisioningStats(k,
                        RCS_CLIENT_PROVISIONING_STATS__EVENT__DMA_CHANGED);
            });
        }
    }

    private void updateConfigForSub(int subId, byte[] config, boolean isCompressed) {
        logv("updateConfigForSub, subId:" + subId + ", mTestModeEnabled:" + mTestModeEnabled);
        if (!mTestModeEnabled) {
            RcsConfig.updateConfigForSub(mPhone, subId, config, isCompressed);
        }
    }

    private byte[] loadConfigForSub(int subId) {
        logv("loadConfigForSub, subId:" + subId + ", mTestModeEnabled:" + mTestModeEnabled);
        if (!mTestModeEnabled) {
            return RcsConfig.loadRcsConfigForSub(mPhone, subId, false);
        }
        return null;
    }

    private boolean isAcsUsed(int subId) {
        PersistableBundle b = mCarrierConfigManager.getConfigForSubId(subId);
        if (b == null) {
            return false;
        }
        return b.getBoolean(CarrierConfigManager.KEY_USE_ACS_FOR_RCS_BOOL);
    }

    private int getSingleRegistrationRequiredByCarrier(int subId) {
        Boolean enabledByOverride = mCarrierSingleRegistrationEnabledOverride.get(subId);
        if (enabledByOverride != null) {
            return enabledByOverride ? ProvisioningManager.STATUS_CAPABLE
                    : ProvisioningManager.STATUS_CARRIER_NOT_CAPABLE;
        }

        PersistableBundle b = mCarrierConfigManager.getConfigForSubId(subId);
        if (!CarrierConfigManager.isConfigForIdentifiedCarrier(b)) {
            return MASK_CAP_CARRIER_INIT;
        }
        return b.getBoolean(CarrierConfigManager.Ims.KEY_IMS_SINGLE_REGISTRATION_REQUIRED_BOOL)
                ? ProvisioningManager.STATUS_CAPABLE
                : ProvisioningManager.STATUS_CARRIER_NOT_CAPABLE;
    }

    private int getSingleRegistrationCapableValue(int subId) {
        boolean isSingleRegistrationEnabledOnDevice =
                mDeviceSingleRegistrationEnabledOverride != null
                ? mDeviceSingleRegistrationEnabledOverride
                : mPhone.getPackageManager().hasSystemFeature(
                        PackageManager.FEATURE_TELEPHONY_IMS_SINGLE_REGISTRATION);

        int value = (isSingleRegistrationEnabledOnDevice ? ProvisioningManager.STATUS_CAPABLE
                : ProvisioningManager.STATUS_DEVICE_NOT_CAPABLE)
                | getSingleRegistrationRequiredByCarrier(subId);
        logv("SingleRegistrationCapableValue : " + value);
        return value;
    }

    private void onCarrierConfigChange() {
        logv("onCarrierConfigChange");
        mRcsProvisioningInfos.forEach((subId, info) -> {
            info.setSingleRegistrationCapability(
                    getSingleRegistrationCapableValue(subId));
        });
    }

    private void onSubChanged() {
        final int[] activeSubs = mSubscriptionManager.getActiveSubscriptionIdList();
        final ArraySet<Integer> subsToBeDeactivated =
                new ArraySet<>(mRcsProvisioningInfos.keySet());

        for (int i : activeSubs) {
            subsToBeDeactivated.remove(i);
            if (!mRcsProvisioningInfos.containsKey(i)) {
                byte[] data = loadConfigForSub(i);
                int capability = getSingleRegistrationCapableValue(i);
                logv("new info is created for sub : " + i + ", single registration capability :"
                        + capability + ", rcs config : " + Arrays.toString(data));
                mRcsProvisioningInfos.put(i, new RcsProvisioningInfo(i, capability, data));
            }
        }

        subsToBeDeactivated.forEach(i -> {
            RcsProvisioningInfo info = mRcsProvisioningInfos.remove(i);
            if (info != null) {
                info.destroy();
            }
        });
    }

    private void onConfigReceived(int subId, byte[] config, boolean isCompressed) {
        logv("onConfigReceived, subId:" + subId + ", config:"
                + Arrays.toString(config) + ", isCompressed:" + isCompressed);
        RcsProvisioningInfo info = mRcsProvisioningInfos.get(subId);
        if (info == null) {
            logd("sub[" + subId + "] has been removed");
            return;
        }
        info.setConfig(isCompressed ? RcsConfig.decompressGzip(config) : config);
        updateConfigForSub(subId, config, isCompressed);

        // Supporting ACS means config data comes from ACS
        // store RCS metrics - received provisioning event
        if (isAcsUsed(subId)) {
            mRcsStats.onRcsAcsProvisioningStats(subId, 200,
                    RCS_ACS_PROVISIONING_STATS__RESPONSE_TYPE__PROVISIONING_XML,
                    isRcsVolteSingleRegistrationEnabled(subId));
        }
    }

    private void onReconfigRequest(int subId) {
        logv("onReconfigRequest, subId:" + subId);
        RcsProvisioningInfo info = mRcsProvisioningInfos.get(subId);
        if (info != null) {
            info.setConfig(null);
            // clear rcs config stored in db
            updateConfigForSub(subId, null, true);
            info.triggerRcsReconfiguration();
        }

        // store RCS metrics - reconfig event
        mRcsStats.onRcsClientProvisioningStats(subId,
                RCS_CLIENT_PROVISIONING_STATS__EVENT__TRIGGER_RCS_RECONFIGURATION);
    }

    private void notifyDmaForSub(int subId, int capability) {
        final Intent intent = new Intent(
                ProvisioningManager.ACTION_RCS_SINGLE_REGISTRATION_CAPABILITY_UPDATE);
        intent.setPackage(mDmaPackageName);
        intent.putExtra(ProvisioningManager.EXTRA_SUBSCRIPTION_ID, subId);
        intent.putExtra(ProvisioningManager.EXTRA_STATUS, capability);
        logv("notify " + intent + ", sub:" + subId + ", capability:" + capability);
        // Only send permission to the default sms app if it has the correct permissions
        // except test mode enabled
        if (!mTestModeEnabled) {
            mPhone.sendBroadcast(intent, Manifest.permission.PERFORM_IMS_SINGLE_REGISTRATION);
        } else {
            mPhone.sendBroadcast(intent);
        }
    }

    private String getDmaPackageName() {
        try {
            return CollectionUtils.firstOrNull(mRoleManager.getRoleHolders(RoleManager.ROLE_SMS));
        } catch (RuntimeException e) {
            loge("Could not get dma name due to " + e);
            return null;
        }
    }

    void registerRcsFeatureListener(RcsProvisioningInfo info) {
        int slotId = SubscriptionManager.getSlotIndex(info.getSubId());
        RcsFeatureListener cb = mRcsFeatureListeners.get(slotId);
        if (cb == null) {
            cb = new RcsFeatureListener(slotId);
            mRcsFeatureListeners.put(slotId, cb);
        }
        cb.addRcsProvisioningInfo(info);
    }

    void unregisterRcsFeatureListener(RcsProvisioningInfo info) {
        // make sure the info to be removed in any case, even the slotId changed or invalid.
        for (int i  = 0; i < mRcsFeatureListeners.size(); i++) {
            mRcsFeatureListeners.valueAt(i).removeRcsProvisioningInfo(info);
        }
    }

    private static boolean booleanEquals(Boolean val1, Boolean val2) {
        return (val1 == null && val2 == null)
                || (Boolean.TRUE.equals(val1) && Boolean.TRUE.equals(val2))
                || (Boolean.FALSE.equals(val1) && Boolean.FALSE.equals(val2));
    }

    private static void logv(String msg) {
        if (DBG) {
            Rlog.d(TAG, msg);
        }
    }

    private static void logi(String msg) {
        Rlog.i(TAG, msg);
    }

    private static void logd(String msg) {
        Rlog.d(TAG, msg);
    }

    private static void loge(String msg) {
        Rlog.e(TAG, msg);
    }

    /**
     * {@link RoleManager} is final so we have to wrap the implementation for testing.
     */
    @VisibleForTesting
    public interface RoleManagerAdapter {
        /** See {@link RoleManager#getRoleHolders(String)} */
        List<String> getRoleHolders(String roleName);
        /** See {@link RoleManager#addOnRoleHoldersChangedListenerAsUser} */
        void addOnRoleHoldersChangedListenerAsUser(Executor executor,
                OnRoleHoldersChangedListener listener, UserHandle user);
        /** See {@link RoleManager#removeOnRoleHoldersChangedListenerAsUser} */
        void removeOnRoleHoldersChangedListenerAsUser(OnRoleHoldersChangedListener listener,
                UserHandle user);
    }

    private static class RoleManagerAdapterImpl implements RoleManagerAdapter {
        private final RoleManager mRoleManager;

        private RoleManagerAdapterImpl(Context context) {
            mRoleManager = context.getSystemService(RoleManager.class);
        }

        @Override
        public List<String> getRoleHolders(String roleName) {
            return mRoleManager.getRoleHolders(roleName);
        }

        @Override
        public void addOnRoleHoldersChangedListenerAsUser(Executor executor,
                OnRoleHoldersChangedListener listener, UserHandle user) {
            mRoleManager.addOnRoleHoldersChangedListenerAsUser(executor, listener, user);
        }

        @Override
        public void removeOnRoleHoldersChangedListenerAsUser(OnRoleHoldersChangedListener listener,
                UserHandle user) {
            mRoleManager.removeOnRoleHoldersChangedListenerAsUser(listener, user);
        }
    }
}
