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

import static android.telephony.TelephonyManager.ENABLE_FEATURE_MAPPING;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.Service;
import android.app.compat.CompatChanges;
import android.compat.Compatibility;
import android.compat.annotation.ChangeId;
import android.compat.annotation.EnabledAfter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.TelephonyServiceManager.ServiceRegisterer;
import android.os.UserManager;
import android.telephony.AvailableNetworkInfo;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyFrameworkInitializer;
import android.telephony.TelephonyManager;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.IOns;
import com.android.internal.telephony.ISetOpportunisticDataCallback;
import com.android.internal.telephony.IUpdateAvailableNetworksCallback;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.TelephonyPermissions;
import com.android.internal.telephony.flags.Flags;
import com.android.telephony.Rlog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * OpportunisticNetworkService implements ions.
 * It scans network and matches the results with opportunistic subscriptions.
 * Use the same to provide user opportunistic data in areas with corresponding networks
 */
public class OpportunisticNetworkService extends Service {
    @VisibleForTesting protected Context mContext;
    private TelephonyManager mTelephonyManager;
    @VisibleForTesting protected PackageManager mPackageManager;
    @VisibleForTesting protected SubscriptionManager mSubscriptionManager;
    @VisibleForTesting protected ONSProfileActivator mONSProfileActivator;
    private ONSStats mONSStats;
    private Handler mHandler = null;

    private final Object mLock = new Object();
    @VisibleForTesting protected boolean mIsEnabled;
    @VisibleForTesting protected ONSProfileSelector mProfileSelector;
    private SharedPreferences mSharedPref;
    @VisibleForTesting protected HashMap<String, ONSConfigInput> mONSConfigInputHashMap;

    private static final String TAG = "ONS";
    private static final String PREF_NAME = TAG;
    private static final String PREF_ENABLED = "isEnabled";
    private static final String CARRIER_APP_CONFIG_NAME = "carrierApp";
    private static final String SYSTEM_APP_CONFIG_NAME = "systemApp";
    private static final boolean DBG = true;
    /* message to indicate sim state update */
    private static final int MSG_SIM_STATE_CHANGE = 1;
    @VisibleForTesting protected CarrierConfigManager mCarrierConfigManager;
    @VisibleForTesting protected UserManager mUserManager;

    /**
     * To expand the error codes for {@link TelephonyManager#updateAvailableNetworks} and
     * {@link TelephonyManager#setPreferredOpportunisticDataSubscription}.
     */
    @ChangeId
    @EnabledAfter(targetSdkVersion = Build.VERSION_CODES.Q)
    static final long CALLBACK_ON_MORE_ERROR_CODE_CHANGE = 130595455L;

    /**
     * Profile selection callback. Will be called once Profile selector decides on
     * the opportunistic data profile.
     */
    private ONSProfileSelector.ONSProfileSelectionCallback  mProfileSelectionCallback =
            new ONSProfileSelector.ONSProfileSelectionCallback() {

                @Override
                public void onProfileSelectionDone() {
                    logDebug("profile selection done");
                }
            };

    /** Broadcast receiver to get SIM card state changed event */
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mHandler.sendEmptyMessage(MSG_SIM_STATE_CHANGE);
        }
    };

    private void createMsgHandler() {
        mHandler = new Handler(Looper.myLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_SIM_STATE_CHANGE:
                        synchronized (mLock) {
                            handleSimStateChange();
                        }
                        break;
                    default:
                        log("invalid message");
                        break;
                }
            }
        };
    }

    private void startWorkerThreadAndInit() {
        Thread thread = new Thread() {
            @Override
            public void run() {
                super.run();
                Looper.prepare();
                Looper looper = Looper.myLooper();
                initialize(getBaseContext());
                synchronized (this) {
                    this.notifyAll();
                }
                looper.loop();
            }
        };

        thread.start();
        synchronized (thread) {
            try {
                thread.wait();
            } catch (Exception e) {
                log(e.getLocalizedMessage());
            }
        }
    }

    private static boolean enforceModifyPhoneStatePermission(Context context) {
        if (context.checkCallingOrSelfPermission(android.Manifest.permission.MODIFY_PHONE_STATE)
                == PackageManager.PERMISSION_GRANTED) {
            return true;
        }

        return false;
    }

    @VisibleForTesting
    protected void handleSimStateChange() {
        logDebug("SIM state changed");

        ONSConfigInput carrierAppConfigInput = mONSConfigInputHashMap.get(CARRIER_APP_CONFIG_NAME);
        if (carrierAppConfigInput == null) {
            return;
        }
        List<SubscriptionInfo> subscriptionInfos =
            mSubscriptionManager.getActiveSubscriptionInfoList(false);
        if (subscriptionInfos == null) {
          return;
        }

        logDebug("handleSimStateChange: subscriptionInfos - " + subscriptionInfos);
        for (SubscriptionInfo subscriptionInfo : subscriptionInfos) {
            if (subscriptionInfo.getSubscriptionId() == carrierAppConfigInput.getPrimarySub()) {
                return;
            }
        }

        logDebug("Carrier subscription is not available, removing entry");
        mONSConfigInputHashMap.put(CARRIER_APP_CONFIG_NAME, null);
        if (!mIsEnabled) {
            return;
        }
        if (mONSConfigInputHashMap.get(SYSTEM_APP_CONFIG_NAME) != null) {
            mProfileSelector.startProfileSelection(
                    mONSConfigInputHashMap.get(SYSTEM_APP_CONFIG_NAME).getAvailableNetworkInfos(),
                    mONSConfigInputHashMap.get(
                            SYSTEM_APP_CONFIG_NAME).getAvailableNetworkCallback());
        }
    }

    private boolean hasOpportunisticSubPrivilege(String callingPackage, int subId) {
        return mTelephonyManager.hasCarrierPrivileges(subId)
                || mSubscriptionManager.canManageSubscription(
                mProfileSelector.getOpprotunisticSubInfo(subId), callingPackage);
    }

    private final IOns.Stub mBinder = new IOns.Stub() {
        /**
         * Enable or disable Opportunistic Network service.
         *
         * This method should be called to enable or disable
         * OpportunisticNetwork service on the device.
         *
         * <p>
         * Requires Permission:
         *   {@link android.Manifest.permission#MODIFY_PHONE_STATE MODIFY_PHONE_STATE}
         * Or the calling app has carrier privileges. @see #hasCarrierPrivileges
         *
         * @param enable enable(True) or disable(False)
         * @param callingPackage caller's package name
         * @return returns true if successfully set.
         */
        @Override
        public boolean setEnable(boolean enable, String callingPackage) {
            TelephonyPermissions.enforceCallingOrSelfModifyPermissionOrCarrierPrivilege(
                    mContext, mSubscriptionManager.getDefaultSubscriptionId(), "setEnable");

            enforceTelephonyFeatureWithException(callingPackage,
                    PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS, "setEnable");

            log("setEnable: " + enable);

            final long identity = Binder.clearCallingIdentity();
            try {
                enableOpportunisticNetwork(enable);
            } finally {
                Binder.restoreCallingIdentity(identity);
            }

            return true;
        }

        /**
         * is Opportunistic Network service enabled
         *
         * This method should be called to determine if the Opportunistic Network service
         * is enabled
         *
         * <p>
         * Requires Permission:
         *   {@link android.Manifest.permission#READ_PRIVILEGED_PHONE_STATE}
         * Or the calling app has carrier privileges. @see #hasCarrierPrivileges
         *
         * @param callingPackage caller's package name
         */
        @Override
        public boolean isEnabled(String callingPackage) {
            TelephonyPermissions
                    .enforceCallingOrSelfReadPrivilegedPhoneStatePermissionOrCarrierPrivilege(
                            mContext, mSubscriptionManager.getDefaultSubscriptionId(), "isEnabled");

            enforceTelephonyFeatureWithException(callingPackage,
                    PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS, "isEnabled");

            return mIsEnabled;
        }

        /**
         * Set preferred opportunistic data.
         *
         * <p>Requires that the calling app has carrier privileges on both primary and
         * secondary subscriptions (see
         * {@link #hasCarrierPrivileges}), or has permission
         * {@link android.Manifest.permission#MODIFY_PHONE_STATE MODIFY_PHONE_STATE}.
         * @param subId which opportunistic subscription
         * {@link SubscriptionManager#getOpportunisticSubscriptions} is preferred for cellular data.
         * Pass {@link SubscriptionManager#DEFAULT_SUBSCRIPTION_ID} to unset the preference
         * @param needValidation whether validation is needed before switch happens.
         * @param callback callback upon request completion.
         * @param callingPackage caller's package name
         *
         */
        public void setPreferredDataSubscriptionId(int subId, boolean needValidation,
                ISetOpportunisticDataCallback callbackStub, String callingPackage) {
            logDebug("setPreferredDataSubscriptionId subId:" + subId
                    + " callingPackage:" + callingPackage);
            if (!enforceModifyPhoneStatePermission(mContext)) {
                TelephonyPermissions.enforceCallingOrSelfCarrierPrivilege(mContext,
                        mSubscriptionManager.getDefaultSubscriptionId(), "setPreferredDataSubscriptionId");
                if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                    TelephonyPermissions.enforceCallingOrSelfCarrierPrivilege(mContext, subId,
                            "setPreferredDataSubscriptionId");
                }
            } else {
                if (mONSConfigInputHashMap.get(CARRIER_APP_CONFIG_NAME) != null) {
                    sendSetOpptCallbackHelper(callbackStub,
                        TelephonyManager.SET_OPPORTUNISTIC_SUB_VALIDATION_FAILED);
                    return;
                }
            }

            enforceTelephonyFeatureWithException(callingPackage,
                    PackageManager.FEATURE_TELEPHONY_DATA, "setPreferredDataSubscriptionId");

            final long identity = Binder.clearCallingIdentity();
            try {
                mProfileSelector.selectProfileForData(subId, needValidation, callbackStub);
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }

        /**
         * Get preferred default data sub Id
         *
         * <p>Requires that the calling app has carrier privileges
         * (see {@link #hasCarrierPrivileges}),or has either
         * {@link android.Manifest.permission#READ_PRIVILEGED_PHONE_STATE} or.
         * {@link android.Manifest.permission#READ_PHONE_STATE} permission.
         * @return subId preferred opportunistic subscription id or
         * {@link SubscriptionManager#DEFAULT_SUBSCRIPTION_ID} if there are no preferred
         * subscription id
         *
         */
        @Override
        public int getPreferredDataSubscriptionId(String callingPackage,
                String callingFeatureId) {
            if (!TelephonyPermissions.checkReadPhoneStateOnAnyActiveSub(
                    mContext,
                    Binder.getCallingPid(),
                    Binder.getCallingUid(),
                    callingPackage,
                    callingFeatureId,
                    "getPreferredDataSubscriptionId")) {
                throw new SecurityException(
                        "getPreferredDataSubscriptionId requires READ_PHONE_STATE,"
                        + " READ_PRIVILEGED_PHONE_STATE, or carrier privileges on"
                        + " any active subscription.");
            }

            enforceTelephonyFeatureWithException(callingPackage,
                    PackageManager.FEATURE_TELEPHONY_DATA, "getPreferredDataSubscriptionId");

            final long identity = Binder.clearCallingIdentity();
            try {
                return mProfileSelector.getPreferredDataSubscriptionId();
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }

        /**
         * Update availability of a list of networks in the current location.
         *
         * This api should be called if the caller is aware of the availability of a network
         * at the current location. This information will be used by OpportunisticNetwork service
         * to decide to attach to the network. If an empty list is passed,
         * it is assumed that no network is available.
         *  @param availableNetworks is a list of available network information.
         *  @param callback callback upon request completion.
         *  @param callingPackage caller's package name
         * <p>
         * <p>Requires that the calling app has carrier privileges on both primary and
         * secondary subscriptions (see
         * {@link #hasCarrierPrivileges}), or has permission
         * {@link android.Manifest.permission#MODIFY_PHONE_STATE MODIFY_PHONE_STATE}.
         *
         */
        public void updateAvailableNetworks(List<AvailableNetworkInfo> availableNetworks,
                IUpdateAvailableNetworksCallback callbackStub, String callingPackage) {
            logDebug("updateAvailableNetworks: " + availableNetworks);
            /* check if system app */
            if (enforceModifyPhoneStatePermission(mContext)) {

                enforceTelephonyFeatureWithException(callingPackage,
                        PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS, "updateAvailableNetworks");

                handleSystemAppAvailableNetworks(
                        (ArrayList<AvailableNetworkInfo>) availableNetworks, callbackStub);
            } else {
                /* check if the app has primary carrier permission */
                TelephonyPermissions.enforceCallingOrSelfCarrierPrivilege(mContext,
                        mSubscriptionManager.getDefaultSubscriptionId(), "updateAvailableNetworks");

                enforceTelephonyFeatureWithException(callingPackage,
                        PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS, "updateAvailableNetworks");

                handleCarrierAppAvailableNetworks(
                        (ArrayList<AvailableNetworkInfo>) availableNetworks, callbackStub,
                        callingPackage);
            }
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        startWorkerThreadAndInit();

        /* register the service */
        ServiceRegisterer opportunisticNetworkServiceRegisterer = TelephonyFrameworkInitializer
                .getTelephonyServiceManager()
                .getOpportunisticNetworkServiceRegisterer();
        if (opportunisticNetworkServiceRegisterer.get() == null) {
            opportunisticNetworkServiceRegisterer.register(mBinder);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mHandler.post(new Runnable() {

            private Intent mIntent = null;
            Runnable setIntent(Intent intent) {
                mIntent = intent;
                return this;
            }

            @Override
            public void run() {
                if (mIntent == null) {
                    return;
                }

                String action = mIntent.getAction();
                if (action == null) {
                    return;
                }

                switch (action) {
                    case ONSProfileSelector.ACTION_SUB_SWITCH: {
                        if (mProfileSelector != null) {
                            mProfileSelector.onSubSwitchComplete(mIntent);
                        }
                    }
                    break;

                    case ONSProfileDownloader.ACTION_ONS_ESIM_DOWNLOAD: {
                        mONSProfileActivator.getONSProfileDownloader().onCallbackIntentReceived(
                                mIntent.getParcelableExtra(Intent.EXTRA_INTENT),
                                mIntent.getIntExtra(ONSProfileResultReceiver.EXTRA_RESULT_CODE, 0)
                        );
                    }
                    break;

                    case ONSProfileConfigurator.ACTION_ONS_ESIM_CONFIG: {
                        mONSProfileActivator.getONSProfileConfigurator().onCallbackIntentReceived(
                                mIntent.getParcelableExtra(Intent.EXTRA_INTENT),
                                mIntent.getIntExtra(ONSProfileResultReceiver.EXTRA_RESULT_CODE, 0)
                        );
                    }
                    break;
                }
            }
        }.setIntent(intent));

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        log("Destroyed Successfully...");
        mHandler.getLooper().quitSafely();

        if (mCarrierConfigManager != null && mCarrierConfigChangeListener != null) {
            mCarrierConfigManager.unregisterCarrierConfigChangeListener(
                    mCarrierConfigChangeListener);
        }
    }

    /**
     * initialize ONS and register as service.
     * Read persistent state to update enable state
     * Start sub components if already enabled.
     * @param context context instance
     */
    @VisibleForTesting
    protected void initialize(Context context) {
        mContext = context;
        createMsgHandler();
        mTelephonyManager = TelephonyManager.from(mContext);
        mProfileSelector = new ONSProfileSelector(mContext, mProfileSelectionCallback);
        mSharedPref = mContext.createDeviceProtectedStorageContext().getSharedPreferences(
                PREF_NAME, Context.MODE_PRIVATE);
        mSubscriptionManager = (SubscriptionManager) mContext.getSystemService(
                Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        if (Flags.workProfileApiSplit()) {
            mSubscriptionManager = mSubscriptionManager.createForAllUserProfiles();
        }
        mONSConfigInputHashMap = new HashMap<String, ONSConfigInput>();
        mONSStats = new ONSStats(mContext, mSubscriptionManager);
        mContext.registerReceiver(mBroadcastReceiver,
            new IntentFilter(TelephonyIntents.ACTION_SIM_STATE_CHANGED));
        enableOpportunisticNetwork(getPersistentEnableState());
        mONSProfileActivator = new ONSProfileActivator(mContext, mONSStats);
        mCarrierConfigManager = mContext.getSystemService(CarrierConfigManager.class);
        // Registers for carrier config changes and runs on handler thread
        mCarrierConfigManager.registerCarrierConfigChangeListener(mHandler::post,
                mCarrierConfigChangeListener);
        mUserManager = mContext.getSystemService(UserManager.class);
        mPackageManager = mContext.getPackageManager();
    }

    /**
     * This is only register CarrierConfigChangeListener for testing
     */
    @VisibleForTesting
    protected void registerCarrierConfigChangListener() {
        mCarrierConfigManager.registerCarrierConfigChangeListener(mHandler::post,
                mCarrierConfigChangeListener);
    }

    private final CarrierConfigManager.CarrierConfigChangeListener mCarrierConfigChangeListener =
            new CarrierConfigManager.CarrierConfigChangeListener() {
                @Override
                public void onCarrierConfigChanged(int logicalSlotIndex, int subscriptionId,
                        int carrierId, int specificCarrierId) {

                    boolean isUserUnlocked = mUserManager.isUserUnlocked();
                    if (isUserUnlocked) {
                        mONSProfileActivator.handleCarrierConfigChange();
                    } else {
                        log("User is locked");
                        // Register the UnlockReceiver for trigger after Unlocked.
                        mContext.registerReceiver(mUserUnlockedReceiver, new IntentFilter(
                                        Intent.ACTION_USER_UNLOCKED));
                    }
                }
            };

    /**
     * This is only sent to registered receivers, not manifest receivers.
     * Note: The user's actual state might have changed by the time the broadcast is received.
     */
    private final BroadcastReceiver mUserUnlockedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_USER_UNLOCKED.equals(intent.getAction())) {
                log("Received UserUnlockedReceiver");
                mONSProfileActivator.handleCarrierConfigChange();
            }
        }
    };

    private void handleCarrierAppAvailableNetworks(
            ArrayList<AvailableNetworkInfo> availableNetworks,
            IUpdateAvailableNetworksCallback callbackStub, String callingPackage) {
        if ((availableNetworks != null) && (availableNetworks.size() > 0)) {
            /* carrier apps should report only subscription */
            if (availableNetworks.size() > 1) {
                log("Carrier app should not pass more than one subscription");
                if (Compatibility.isChangeEnabled(CALLBACK_ON_MORE_ERROR_CODE_CHANGE)) {
                    sendUpdateNetworksCallbackHelper(callbackStub,
                            TelephonyManager
                                    .UPDATE_AVAILABLE_NETWORKS_MULTIPLE_NETWORKS_NOT_SUPPORTED);
                } else {
                    sendUpdateNetworksCallbackHelper(callbackStub,
                            TelephonyManager.UPDATE_AVAILABLE_NETWORKS_INVALID_ARGUMENTS);
                }
                return;
            }

            if (!mProfileSelector.hasOpprotunisticSub(availableNetworks)) {
                log("No opportunistic subscriptions received");
                if (Compatibility.isChangeEnabled(CALLBACK_ON_MORE_ERROR_CODE_CHANGE)) {
                    sendUpdateNetworksCallbackHelper(callbackStub,
                            TelephonyManager
                                    .UPDATE_AVAILABLE_NETWORKS_NO_OPPORTUNISTIC_SUB_AVAILABLE);
                } else {
                    sendUpdateNetworksCallbackHelper(callbackStub,
                            TelephonyManager.UPDATE_AVAILABLE_NETWORKS_INVALID_ARGUMENTS);
                }
                return;
            }

            for (AvailableNetworkInfo availableNetworkInfo : availableNetworks) {
                final long identity = Binder.clearCallingIdentity();
                boolean isActiveSubId = false;
                try {
                    isActiveSubId =
                            mSubscriptionManager.isActiveSubId(availableNetworkInfo.getSubId());
                } finally {
                    Binder.restoreCallingIdentity(identity);
                }
                if (isActiveSubId) {
                    TelephonyPermissions.enforceCallingOrSelfCarrierPrivilege(mContext,
                        availableNetworkInfo.getSubId(), "updateAvailableNetworks");
                } else {
                    /* check if the app has opportunistic carrier permission */
                    if (!hasOpportunisticSubPrivilege(callingPackage,
                        availableNetworkInfo.getSubId())) {
                        log("No carrier privilege for opportunistic subscription");
                        sendUpdateNetworksCallbackHelper(callbackStub,
                            TelephonyManager.UPDATE_AVAILABLE_NETWORKS_NO_CARRIER_PRIVILEGE);
                        return;
                    }
                }
            }

            final long identity = Binder.clearCallingIdentity();
            try {
                ONSConfigInput onsConfigInput = new ONSConfigInput(availableNetworks, callbackStub);
                SubscriptionInfo subscriptionInfo = mSubscriptionManager.getDefaultVoiceSubscriptionInfo();
                if (subscriptionInfo != null) {
                    onsConfigInput.setPrimarySub(subscriptionInfo.getSubscriptionId());
                    onsConfigInput.setPreferredDataSub(availableNetworks.get(0).getSubId());
                    mONSConfigInputHashMap.put(CARRIER_APP_CONFIG_NAME, onsConfigInput);
                }
                /* standalone opportunistic subscription should be handled in priority. */
                if (mONSConfigInputHashMap.get(SYSTEM_APP_CONFIG_NAME) != null) {
                    if (mProfileSelector.containStandaloneOppSubs(mONSConfigInputHashMap.get(
                            SYSTEM_APP_CONFIG_NAME).getAvailableNetworkInfos())) {
                        log("standalone opportunistic subscription is using.");
                        return;
                    }
                }

                if (mIsEnabled) {
                    /*  if carrier is reporting availability, then it takes higher priority. */
                    mProfileSelector.startProfileSelection(availableNetworks, callbackStub);
                } else {
                    if (Compatibility.isChangeEnabled(CALLBACK_ON_MORE_ERROR_CODE_CHANGE)) {
                        sendUpdateNetworksCallbackHelper(callbackStub,
                                TelephonyManager.UPDATE_AVAILABLE_NETWORKS_SERVICE_IS_DISABLED);
                    } else {
                        sendUpdateNetworksCallbackHelper(callbackStub,
                                TelephonyManager.UPDATE_AVAILABLE_NETWORKS_ABORTED);
                    }
                }
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        } else {
            final long identity = Binder.clearCallingIdentity();
            try {
                mONSConfigInputHashMap.put(CARRIER_APP_CONFIG_NAME, null);
                if (!mIsEnabled) {
                    sendUpdateNetworksCallbackHelper(callbackStub,
                        TelephonyManager.UPDATE_AVAILABLE_NETWORKS_SUCCESS);
                    return;
                }
                /* if carrier is reporting unavailability, then decide whether to start
                   system app request or not. */
                if (mONSConfigInputHashMap.get(SYSTEM_APP_CONFIG_NAME) != null) {
                    sendUpdateNetworksCallbackHelper(callbackStub,
                            TelephonyManager.UPDATE_AVAILABLE_NETWORKS_SUCCESS);
                    mProfileSelector.startProfileSelection(
                            mONSConfigInputHashMap.get(SYSTEM_APP_CONFIG_NAME)
                                    .getAvailableNetworkInfos(),
                            mONSConfigInputHashMap.get(
                                    SYSTEM_APP_CONFIG_NAME).getAvailableNetworkCallback());
                } else {
                    mProfileSelector.stopProfileSelection(callbackStub);
                }
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }
    }

    private void sendUpdateNetworksCallbackHelper(IUpdateAvailableNetworksCallback callback, int result) {
        if (callback == null) return;
        try {
            callback.onComplete(result);
        } catch (RemoteException exception) {
            log("RemoteException " + exception);
        }
    }

    private void sendSetOpptCallbackHelper(ISetOpportunisticDataCallback callback, int result) {
        if (callback == null) return;
        try {
            callback.onComplete(result);
        } catch (RemoteException exception) {
            log("RemoteException " + exception);
        }
    }

    private boolean getPersistentEnableState() {
        return mSharedPref.getBoolean(PREF_ENABLED, true);
    }

    private void handleSystemAppAvailableNetworks(
            ArrayList<AvailableNetworkInfo> availableNetworks,
            IUpdateAvailableNetworksCallback callbackStub) {
        final long identity = Binder.clearCallingIdentity();
        try {
            if ((availableNetworks != null) && (availableNetworks.size() > 0)) {
                /* all subscriptions should be opportunistic subscriptions */
                if (!mProfileSelector.hasOpprotunisticSub(availableNetworks)) {
                    log("No opportunistic subscriptions received");
                    if (Compatibility.isChangeEnabled(CALLBACK_ON_MORE_ERROR_CODE_CHANGE)) {
                        sendUpdateNetworksCallbackHelper(callbackStub,
                                TelephonyManager
                                        .UPDATE_AVAILABLE_NETWORKS_NO_OPPORTUNISTIC_SUB_AVAILABLE);
                    } else {
                        sendUpdateNetworksCallbackHelper(callbackStub,
                                TelephonyManager.UPDATE_AVAILABLE_NETWORKS_INVALID_ARGUMENTS);
                    }
                    return;
                }
                mONSConfigInputHashMap.put(SYSTEM_APP_CONFIG_NAME,
                        new ONSConfigInput(availableNetworks, callbackStub));
                /* reporting availability. proceed if carrier app has not requested any, but
                   standalone opportunistic subscription should be handled in priority. */
                if (mIsEnabled) {
                    if (mONSConfigInputHashMap.get(CARRIER_APP_CONFIG_NAME) == null
                            || mProfileSelector.containStandaloneOppSubs(availableNetworks)) {
                        mProfileSelector.startProfileSelection(availableNetworks, callbackStub);
                    }
                } else {
                    if (Compatibility.isChangeEnabled(CALLBACK_ON_MORE_ERROR_CODE_CHANGE)) {
                        sendUpdateNetworksCallbackHelper(callbackStub,
                                TelephonyManager.UPDATE_AVAILABLE_NETWORKS_SERVICE_IS_DISABLED);
                    } else {
                        sendUpdateNetworksCallbackHelper(callbackStub,
                                TelephonyManager.UPDATE_AVAILABLE_NETWORKS_ABORTED);
                    }
                }
            } else {
                if (!mIsEnabled) {
                    mONSConfigInputHashMap.put(SYSTEM_APP_CONFIG_NAME, null);
                    sendUpdateNetworksCallbackHelper(callbackStub,
                        TelephonyManager.UPDATE_AVAILABLE_NETWORKS_SUCCESS);
                    return;
                }
                /* if system is reporting unavailability, then decide whether to start
                   carrier app request or not. */
                mONSConfigInputHashMap.put(SYSTEM_APP_CONFIG_NAME, null);
                if (mONSConfigInputHashMap.get(CARRIER_APP_CONFIG_NAME) == null) {
                    mProfileSelector.stopProfileSelection(callbackStub);
                } else {
                    sendUpdateNetworksCallbackHelper(callbackStub,
                            TelephonyManager.UPDATE_AVAILABLE_NETWORKS_SUCCESS);
                    log("Try to start carrier app request");
                    mProfileSelector.startProfileSelection(
                            mONSConfigInputHashMap.get(CARRIER_APP_CONFIG_NAME)
                                    .getAvailableNetworkInfos(),
                            mONSConfigInputHashMap.get(
                                    CARRIER_APP_CONFIG_NAME).getAvailableNetworkCallback());
                }
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    private void updateEnableState(boolean enable) {
        mIsEnabled = enable;
        mSharedPref.edit().putBoolean(PREF_ENABLED, mIsEnabled).apply();
    }

    /**
     * update the enable state
     * start profile selection if enabled.
     * @param enable enable(true) or disable(false)
     */
    private void enableOpportunisticNetwork(boolean enable) {
        synchronized (mLock) {
            if (mIsEnabled != enable) {
                updateEnableState(enable);
                if (!mIsEnabled) {
                    mProfileSelector.stopProfileSelection(null);
                } else {
                    if (mONSConfigInputHashMap.get(CARRIER_APP_CONFIG_NAME) != null &&
                        mONSConfigInputHashMap.get(CARRIER_APP_CONFIG_NAME)
                            .getAvailableNetworkInfos() != null) {
                        mProfileSelector.startProfileSelection(
                            mONSConfigInputHashMap.get(CARRIER_APP_CONFIG_NAME)
                                .getAvailableNetworkInfos(),
                            mONSConfigInputHashMap.get(
                                CARRIER_APP_CONFIG_NAME).getAvailableNetworkCallback());
                    } else if (mONSConfigInputHashMap.get(SYSTEM_APP_CONFIG_NAME) != null &&
                        mONSConfigInputHashMap.get(SYSTEM_APP_CONFIG_NAME)
                            .getAvailableNetworkInfos() != null) {
                        mProfileSelector.startProfileSelection(
                            mONSConfigInputHashMap.get(SYSTEM_APP_CONFIG_NAME)
                                .getAvailableNetworkInfos(),
                            mONSConfigInputHashMap.get(
                                SYSTEM_APP_CONFIG_NAME).getAvailableNetworkCallback());
                    }
                }
            }
        }
        logDebug("service is enable state " + mIsEnabled);
    }

    /**
     * Make sure the device has required telephony feature
     *
     * @throws UnsupportedOperationException if the device does not have required telephony feature
     */
    private void enforceTelephonyFeatureWithException(@Nullable String callingPackage,
            @NonNull String telephonyFeature, @NonNull String methodName) {
        if (callingPackage == null || mPackageManager == null) {
            return;
        }

        if (!Flags.enforceTelephonyFeatureMappingForPublicApis()
                || !CompatChanges.isChangeEnabled(ENABLE_FEATURE_MAPPING, callingPackage,
                Binder.getCallingUserHandle())) {
            return;
        }

        if (!mPackageManager.hasSystemFeature(telephonyFeature)) {
            throw new UnsupportedOperationException(
                    methodName + " is unsupported without " + telephonyFeature);
        }
    }

    private void log(String msg) {
        Rlog.d(TAG, msg);
    }

    private void logDebug(String msg) {
        if (DBG) Rlog.d(TAG, msg);
    }
}
