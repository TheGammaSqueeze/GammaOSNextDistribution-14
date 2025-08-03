/*
 * Copyright (c) 2015, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.phone;

import static android.content.pm.PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION;
import static android.service.carrier.CarrierService.ICarrierServiceWrapper.KEY_CONFIG_BUNDLE;
import static android.service.carrier.CarrierService.ICarrierServiceWrapper.RESULT_ERROR;
import static android.telephony.TelephonyManager.ENABLE_FEATURE_MAPPING;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.AppOpsManager;
import android.app.compat.CompatChanges;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerExecutor;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PermissionEnforcer;
import android.os.PersistableBundle;
import android.os.Process;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.os.UserHandle;
import android.preference.PreferenceManager;
import android.service.carrier.CarrierIdentifier;
import android.service.carrier.CarrierService;
import android.service.carrier.ICarrierService;
import android.telephony.AnomalyReporter;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyFrameworkInitializer;
import android.telephony.TelephonyManager;
import android.telephony.TelephonyRegistryManager;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.LocalLog;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.ICarrierConfigLoader;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConfigurationManager;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.TelephonyPermissions;
import com.android.internal.telephony.flags.FeatureFlags;
import com.android.internal.telephony.subscription.SubscriptionManagerService;
import com.android.internal.telephony.util.ArrayUtils;
import com.android.internal.telephony.util.TelephonyUtils;
import com.android.internal.util.IndentingPrintWriter;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * CarrierConfigLoader binds to privileged carrier apps to fetch carrier config overlays.
 */
public class CarrierConfigLoader extends ICarrierConfigLoader.Stub {
    private static final String LOG_TAG = "CarrierConfigLoader";

    private static final SimpleDateFormat TIME_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

    // Package name for platform carrier config app, bundled with system image.
    @NonNull private final String mPlatformCarrierConfigPackage;

    /** The singleton instance. */
    @Nullable private static CarrierConfigLoader sInstance;
    // The context for phone app, passed from PhoneGlobals.
    @NonNull private Context mContext;

    // All the states below (array indexed by phoneId) are non-null. But the member of the array
    // is nullable, when e.g. the config for the phone is not loaded yet
    // Carrier configs from default app, indexed by phoneID.
    @NonNull private PersistableBundle[] mConfigFromDefaultApp;
    // Carrier configs from privileged carrier config app, indexed by phoneID.
    @NonNull private PersistableBundle[] mConfigFromCarrierApp;
    // Persistent Carrier configs that are provided via the override test API, indexed by phone ID.
    @NonNull private PersistableBundle[] mPersistentOverrideConfigs;
    // Carrier configs that are provided via the override test API, indexed by phone ID.
    @NonNull private PersistableBundle[] mOverrideConfigs;
    // Carrier configs to override code default when there is no SIM inserted
    @NonNull private PersistableBundle mNoSimConfig;
    // Service connection for binding to config app.
    @NonNull private CarrierServiceConnection[] mServiceConnection;
    // Service connection for binding to carrier config app for no SIM config.
    @NonNull private CarrierServiceConnection[] mServiceConnectionForNoSimConfig;
    // Whether we are bound to a service for each phone
    @NonNull private boolean[] mServiceBound;
    // Whether we are bound to a service for no SIM config
    @NonNull private boolean[] mServiceBoundForNoSimConfig;
    // Whether we have sent config change broadcast for each phone id.
    @NonNull private boolean[] mHasSentConfigChange;
    // Whether the broadcast was sent from EVENT_SYSTEM_UNLOCKED, to track rebroadcasts
    @NonNull private boolean[] mFromSystemUnlocked;
    // CarrierService change monitoring
    @NonNull private CarrierServiceChangeCallback[] mCarrierServiceChangeCallbacks;

    // Broadcast receiver for system events
    @NonNull
    private final BroadcastReceiver mSystemBroadcastReceiver = new ConfigLoaderBroadcastReceiver();
    @NonNull private final LocalLog mCarrierConfigLoadingLog = new LocalLog(256);
    // Number of phone instances (active modem count)
    private int mNumPhones;


    // Message codes; see mHandler below.
    // Request from UiccController when SIM becomes absent or error.
    private static final int EVENT_CLEAR_CONFIG = 0;
    // Has connected to default app.
    private static final int EVENT_CONNECTED_TO_DEFAULT = 3;
    // Has connected to carrier app.
    private static final int EVENT_CONNECTED_TO_CARRIER = 4;
    // Config has been loaded from default app (or cache).
    private static final int EVENT_FETCH_DEFAULT_DONE = 5;
    // Config has been loaded from carrier app (or cache).
    private static final int EVENT_FETCH_CARRIER_DONE = 6;
    // Attempt to fetch from default app or read from XML.
    private static final int EVENT_DO_FETCH_DEFAULT = 7;
    // Attempt to fetch from carrier app or read from XML.
    private static final int EVENT_DO_FETCH_CARRIER = 8;
    // A package has been installed, uninstalled, or updated.
    private static final int EVENT_PACKAGE_CHANGED = 9;
    // Bind timed out for the default app.
    private static final int EVENT_BIND_DEFAULT_TIMEOUT = 10;
    // Bind timed out for a carrier app.
    private static final int EVENT_BIND_CARRIER_TIMEOUT = 11;
    // Check if the system fingerprint has changed.
    private static final int EVENT_CHECK_SYSTEM_UPDATE = 12;
    // Rerun carrier config binding after system is unlocked.
    private static final int EVENT_SYSTEM_UNLOCKED = 13;
    // Fetching config timed out from the default app.
    private static final int EVENT_FETCH_DEFAULT_TIMEOUT = 14;
    // Fetching config timed out from a carrier app.
    private static final int EVENT_FETCH_CARRIER_TIMEOUT = 15;
    // SubscriptionManagerService has finished updating the sub for the carrier config.
    private static final int EVENT_SUBSCRIPTION_INFO_UPDATED = 16;
    // Multi-SIM config changed.
    private static final int EVENT_MULTI_SIM_CONFIG_CHANGED = 17;
    // Attempt to fetch from default app or read from XML for no SIM case.
    private static final int EVENT_DO_FETCH_DEFAULT_FOR_NO_SIM_CONFIG = 18;
    // No SIM config has been loaded from default app (or cache).
    private static final int EVENT_FETCH_DEFAULT_FOR_NO_SIM_CONFIG_DONE = 19;
    // Has connected to default app for no SIM config.
    private static final int EVENT_CONNECTED_TO_DEFAULT_FOR_NO_SIM_CONFIG = 20;
    // Bind timed out for the default app when trying to fetch no SIM config.
    private static final int EVENT_BIND_DEFAULT_FOR_NO_SIM_CONFIG_TIMEOUT = 21;
    // Fetching config timed out from the default app for no SIM config.
    private static final int EVENT_FETCH_DEFAULT_FOR_NO_SIM_CONFIG_TIMEOUT = 22;
    // NOTE: any new EVENT_* values must be added to method eventToString().

    private static final int BIND_TIMEOUT_MILLIS = 30000;

    // Keys used for saving and restoring config bundle from file.
    private static final String KEY_VERSION = "__carrier_config_package_version__";

    private static final String OVERRIDE_PACKAGE_ADDITION = "-override";

    // SharedPreferences key for last known build fingerprint.
    private static final String KEY_FINGERPRINT = "build_fingerprint";

    // Argument for #dump that indicates we should also call the default and specified carrier
    // service's #dump method. In multi-SIM devices, it's possible that carrier A is on SIM 1 and
    // carrier B is on SIM 2, in which case we should not dump carrier B's service when carrier A
    // requested the dump.
    private static final String DUMP_ARG_REQUESTING_PACKAGE = "--requesting-package";

    // Configs that should always be included when clients calls getConfig[ForSubId] with specified
    // keys (even configs are not explicitly specified). Those configs have special purpose for the
    // carrier config APIs to work correctly.
    private static final String[] CONFIG_SUBSET_METADATA_KEYS = new String[] {
            CarrierConfigManager.KEY_CARRIER_CONFIG_VERSION_STRING,
            CarrierConfigManager.KEY_CARRIER_CONFIG_APPLIED_BOOL
    };

    // UUID to report anomaly when config changed reported with subId that map to invalid phone
    private static final String UUID_NOTIFY_CONFIG_CHANGED_WITH_INVALID_PHONE =
            "d81cef11-c2f1-4d76-955d-7f50e8590c48";

    // Handler to process various events.
    //
    // For each phoneId, the event sequence should be:
    //     fetch default, connected to default, fetch default (async), fetch default done,
    //     fetch carrier, connected to carrier, fetch carrier (async), fetch carrier done.
    //
    // If there is a saved config file for either the default app or the carrier app, we skip
    // binding to the app and go straight from fetch to loaded.
    //
    // At any time, at most one connection is active. If events are not in this order, previous
    // connection will be unbound, so only latest event takes effect.
    //
    // We broadcast ACTION_CARRIER_CONFIG_CHANGED after:
    // 1. loading from carrier app (even if read from a file)
    // 2. loading from default app if there is no carrier app (even if read from a file)
    // 3. clearing config (e.g. due to sim removal)
    // 4. encountering bind or IPC error
    private class ConfigHandler extends Handler {
        ConfigHandler(@NonNull Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            final int phoneId = msg.arg1;
            logd(eventToString(msg.what) + " phoneId: " + phoneId);
            if (!SubscriptionManager.isValidPhoneId(phoneId)
                    && msg.what != EVENT_MULTI_SIM_CONFIG_CHANGED) {
                return;
            }
            switch (msg.what) {
                case EVENT_CLEAR_CONFIG: {
                    clearConfigForPhone(phoneId, true);
                    break;
                }

                case EVENT_SYSTEM_UNLOCKED: {
                    for (int i = 0; i < mNumPhones; ++i) {
                        // When the user unlocks the device, send the broadcast again (with a
                        // rebroadcast extra) if we have sent it before unlock. This will avoid
                        // trying to load the carrier config when the SIM is still loading when the
                        // unlock happens.
                        if (mHasSentConfigChange[i]) {
                            logdWithLocalLog("System unlocked");
                            mFromSystemUnlocked[i] = true;
                            updateConfigForPhoneId(i);
                        }
                    }
                    break;
                }

                case EVENT_PACKAGE_CHANGED: {
                    final String carrierPackageName = (String) msg.obj;
                    // Always clear up the cache and re-load config from scratch since the carrier
                    // service change is reliable and specific to the phoneId now.
                    clearCachedConfigForPackage(carrierPackageName);
                    logdWithLocalLog("Package changed: " + carrierPackageName
                            + ", phone=" + phoneId);
                    updateConfigForPhoneId(phoneId);
                    break;
                }

                case EVENT_DO_FETCH_DEFAULT: {
                    // Clear in-memory cache for carrier app config, so when carrier app gets
                    // uninstalled, no stale config is left.
                    if (mConfigFromCarrierApp[phoneId] != null
                            && getCarrierPackageForPhoneId(phoneId) == null) {
                        mConfigFromCarrierApp[phoneId] = null;
                    }
                    // Restore persistent override values.
                    PersistableBundle config = restoreConfigFromXml(
                            mPlatformCarrierConfigPackage, OVERRIDE_PACKAGE_ADDITION, phoneId);
                    if (config != null) {
                        mPersistentOverrideConfigs[phoneId] = config;
                    }

                    config = restoreConfigFromXml(mPlatformCarrierConfigPackage, "", phoneId);
                    if (config != null) {
                        mConfigFromDefaultApp[phoneId] = config;
                        Message newMsg = obtainMessage(EVENT_FETCH_DEFAULT_DONE, phoneId, -1);
                        newMsg.getData().putBoolean("loaded_from_xml", true);
                        mHandler.sendMessage(newMsg);
                    } else {
                        // No cached config, so fetch it from the default app.
                        if (bindToConfigPackage(
                                mPlatformCarrierConfigPackage,
                                phoneId,
                                EVENT_CONNECTED_TO_DEFAULT)) {
                            sendMessageDelayed(
                                    obtainMessage(EVENT_BIND_DEFAULT_TIMEOUT, phoneId, -1 /*arg2*/,
                                            getMessageToken(phoneId)),
                                    BIND_TIMEOUT_MILLIS);
                        } else {
                            // Put a stub bundle in place so that the rest of the logic continues
                            // smoothly.
                            mConfigFromDefaultApp[phoneId] = new PersistableBundle();
                            // Send broadcast if bind fails.
                            updateSubscriptionDatabase(phoneId);
                            // TODO: We *must* call unbindService even if bindService returns false.
                            // (And possibly if SecurityException was thrown.)
                            loge("binding to default app: "
                                    + mPlatformCarrierConfigPackage + " fails");
                        }
                    }
                    break;
                }

                case EVENT_CONNECTED_TO_DEFAULT: {
                    removeMessages(EVENT_BIND_DEFAULT_TIMEOUT, getMessageToken(phoneId));
                    final CarrierServiceConnection conn = (CarrierServiceConnection) msg.obj;
                    // If new service connection has been created, unbind.
                    if (mServiceConnection[phoneId] != conn || conn.service == null) {
                        unbindIfBound(mContext, conn, phoneId);
                        break;
                    }
                    final CarrierIdentifier carrierId = getCarrierIdentifierForPhoneId(phoneId);
                    // ResultReceiver callback will execute in this Handler's thread.
                    final ResultReceiver resultReceiver =
                            new ResultReceiver(this) {
                                @Override
                                public void onReceiveResult(int resultCode, Bundle resultData) {
                                    unbindIfBound(mContext, conn, phoneId);
                                    removeMessages(EVENT_FETCH_DEFAULT_TIMEOUT,
                                            getMessageToken(phoneId));
                                    // If new service connection has been created, this is stale.
                                    if (mServiceConnection[phoneId] != conn) {
                                        loge("Received response for stale request.");
                                        return;
                                    }
                                    if (resultCode == RESULT_ERROR || resultData == null) {
                                        // On error, abort config fetching.
                                        loge("Failed to get carrier config");
                                        updateSubscriptionDatabase(phoneId);
                                        return;
                                    }
                                    PersistableBundle config =
                                            resultData.getParcelable(KEY_CONFIG_BUNDLE);
                                    saveConfigToXml(mPlatformCarrierConfigPackage, "", phoneId,
                                            carrierId, config);
                                    mConfigFromDefaultApp[phoneId] = config;
                                    sendMessage(
                                            obtainMessage(
                                                    EVENT_FETCH_DEFAULT_DONE, phoneId, -1));
                                }
                            };
                    // Now fetch the config asynchronously from the ICarrierService.
                    try {
                        ICarrierService carrierService =
                                ICarrierService.Stub.asInterface(conn.service);
                        carrierService.getCarrierConfig(phoneId, carrierId, resultReceiver);
                        logdWithLocalLog("Fetch config for default app: "
                                + mPlatformCarrierConfigPackage
                                + ", carrierId=" + carrierId.getSpecificCarrierId());
                    } catch (RemoteException e) {
                        loge("Failed to get carrier config from default app: " +
                                mPlatformCarrierConfigPackage + " err: " + e);
                        unbindIfBound(mContext, conn, phoneId);
                        break; // So we don't set a timeout.
                    }
                    sendMessageDelayed(
                            obtainMessage(EVENT_FETCH_DEFAULT_TIMEOUT, phoneId, -1 /*arg2*/,
                                    getMessageToken(phoneId)),
                            BIND_TIMEOUT_MILLIS);
                    break;
                }

                case EVENT_BIND_DEFAULT_TIMEOUT:
                case EVENT_FETCH_DEFAULT_TIMEOUT: {
                    loge("Bind/fetch time out from " + mPlatformCarrierConfigPackage);
                    removeMessages(EVENT_FETCH_DEFAULT_TIMEOUT, getMessageToken(phoneId));
                    // If we attempted to bind to the app, but the service connection is null due to
                    // the race condition that clear config event happens before bind/fetch complete
                    // then config was cleared while we were waiting and we should not continue.
                    if (mServiceConnection[phoneId] != null) {
                        // If a ResponseReceiver callback is in the queue when this happens, we will
                        // unbind twice and throw an exception.
                        unbindIfBound(mContext, mServiceConnection[phoneId], phoneId);
                        broadcastConfigChangedIntent(phoneId);
                    }
                    // Put a stub bundle in place so that the rest of the logic continues smoothly.
                    mConfigFromDefaultApp[phoneId] = new PersistableBundle();
                    updateSubscriptionDatabase(phoneId);
                    break;
                }

                case EVENT_FETCH_DEFAULT_DONE: {
                    // If we attempted to bind to the app, but the service connection is null, then
                    // config was cleared while we were waiting and we should not continue.
                    if (!msg.getData().getBoolean("loaded_from_xml", false)
                            && mServiceConnection[phoneId] == null) {
                        break;
                    }
                    final String carrierPackageName = getCarrierPackageForPhoneId(phoneId);
                    if (carrierPackageName != null) {
                        logd("Found carrier config app: " + carrierPackageName);
                        sendMessage(obtainMessage(EVENT_DO_FETCH_CARRIER, phoneId, -1));
                    } else {
                        updateSubscriptionDatabase(phoneId);
                    }
                    break;
                }

                case EVENT_DO_FETCH_CARRIER: {
                    final String carrierPackageName = getCarrierPackageForPhoneId(phoneId);
                    final PersistableBundle config =
                            restoreConfigFromXml(carrierPackageName, "", phoneId);
                    if (config != null) {
                        mConfigFromCarrierApp[phoneId] = config;
                        Message newMsg = obtainMessage(EVENT_FETCH_CARRIER_DONE, phoneId, -1);
                        newMsg.getData().putBoolean("loaded_from_xml", true);
                        sendMessage(newMsg);
                    } else {
                        // No cached config, so fetch it from a carrier app.
                        if (carrierPackageName != null && bindToConfigPackage(carrierPackageName,
                                phoneId, EVENT_CONNECTED_TO_CARRIER)) {
                            sendMessageDelayed(
                                    obtainMessage(EVENT_BIND_CARRIER_TIMEOUT, phoneId, -1 /*arg2*/,
                                            getMessageToken(phoneId)),
                                    BIND_TIMEOUT_MILLIS);
                        } else {
                            // Put a stub bundle in place so that the rest of the logic continues
                            // smoothly.
                            mConfigFromCarrierApp[phoneId] = new PersistableBundle();
                            // Send broadcast if bind fails.
                            broadcastConfigChangedIntent(phoneId);
                            loge("Bind to carrier app: " + carrierPackageName + " fails");
                            updateSubscriptionDatabase(phoneId);
                        }
                    }
                    break;
                }

                case EVENT_CONNECTED_TO_CARRIER: {
                    removeMessages(EVENT_BIND_CARRIER_TIMEOUT, getMessageToken(phoneId));
                    final CarrierServiceConnection conn = (CarrierServiceConnection) msg.obj;
                    // If new service connection has been created, unbind.
                    if (mServiceConnection[phoneId] != conn || conn.service == null) {
                        unbindIfBound(mContext, conn, phoneId);
                        break;
                    }
                    final CarrierIdentifier carrierId = getCarrierIdentifierForPhoneId(phoneId);
                    // ResultReceiver callback will execute in this Handler's thread.
                    final ResultReceiver resultReceiver =
                            new ResultReceiver(this) {
                                @Override
                                public void onReceiveResult(int resultCode, Bundle resultData) {
                                    unbindIfBound(mContext, conn, phoneId);
                                    removeMessages(EVENT_FETCH_CARRIER_TIMEOUT,
                                            getMessageToken(phoneId));
                                    // If new service connection has been created, this is stale.
                                    if (mServiceConnection[phoneId] != conn) {
                                        loge("Received response for stale request.");
                                        return;
                                    }
                                    if (resultCode == RESULT_ERROR || resultData == null) {
                                        // On error, abort config fetching.
                                        loge("Failed to get carrier config from carrier app: "
                                                + getCarrierPackageForPhoneId(phoneId));
                                        broadcastConfigChangedIntent(phoneId);
                                        updateSubscriptionDatabase(phoneId);
                                        return;
                                    }
                                    PersistableBundle config =
                                            resultData.getParcelable(KEY_CONFIG_BUNDLE);
                                    saveConfigToXml(getCarrierPackageForPhoneId(phoneId), "",
                                            phoneId, carrierId, config);
                                    if (config != null) {
                                        mConfigFromCarrierApp[phoneId] = config;
                                    } else {
                                        logdWithLocalLog("Config from carrier app is null "
                                                + "for phoneId " + phoneId);
                                        // Put a stub bundle in place so that the rest of the logic
                                        // continues smoothly.
                                        mConfigFromCarrierApp[phoneId] = new PersistableBundle();
                                    }
                                    sendMessage(
                                            obtainMessage(
                                                    EVENT_FETCH_CARRIER_DONE, phoneId, -1));
                                }
                            };
                    // Now fetch the config asynchronously from the ICarrierService.
                    try {
                        ICarrierService carrierService =
                                ICarrierService.Stub.asInterface(conn.service);
                        carrierService.getCarrierConfig(phoneId, carrierId, resultReceiver);
                        logdWithLocalLog("Fetch config for carrier app: "
                                + getCarrierPackageForPhoneId(phoneId)
                                + ", carrierId=" + carrierId.getSpecificCarrierId());
                    } catch (RemoteException e) {
                        loge("Failed to get carrier config: " + e);
                        unbindIfBound(mContext, conn, phoneId);
                        break; // So we don't set a timeout.
                    }
                    sendMessageDelayed(
                            obtainMessage(EVENT_FETCH_CARRIER_TIMEOUT, phoneId, -1 /*arg2*/,
                                    getMessageToken(phoneId)),
                            BIND_TIMEOUT_MILLIS);
                    break;
                }

                case EVENT_BIND_CARRIER_TIMEOUT:
                case EVENT_FETCH_CARRIER_TIMEOUT: {
                    loge("Bind/fetch from carrier app timeout, package="
                            + getCarrierPackageForPhoneId(phoneId));
                    removeMessages(EVENT_FETCH_CARRIER_TIMEOUT, getMessageToken(phoneId));
                    // If we attempted to bind to the app, but the service connection is null due to
                    // the race condition that clear config event happens before bind/fetch complete
                    // then config was cleared while we were waiting and we should not continue.
                    if (mServiceConnection[phoneId] != null) {
                        // If a ResponseReceiver callback is in the queue when this happens, we will
                        // unbind twice and throw an exception.
                        unbindIfBound(mContext, mServiceConnection[phoneId], phoneId);
                        broadcastConfigChangedIntent(phoneId);
                    }
                    // Put a stub bundle in place so that the rest of the logic continues smoothly.
                    mConfigFromCarrierApp[phoneId] = new PersistableBundle();
                    updateSubscriptionDatabase(phoneId);
                    break;
                }
                case EVENT_FETCH_CARRIER_DONE: {
                    // If we attempted to bind to the app, but the service connection is null, then
                    // config was cleared while we were waiting and we should not continue.
                    if (!msg.getData().getBoolean("loaded_from_xml", false)
                            && mServiceConnection[phoneId] == null) {
                        break;
                    }
                    updateSubscriptionDatabase(phoneId);
                    break;
                }

                case EVENT_CHECK_SYSTEM_UPDATE: {
                    SharedPreferences sharedPrefs =
                            PreferenceManager.getDefaultSharedPreferences(mContext);
                    final String lastFingerprint = sharedPrefs.getString(KEY_FINGERPRINT, null);
                    if (!Build.VERSION.INCREMENTAL.equals(lastFingerprint)) {
                        logd(
                                "Build incremental version changed. old: "
                                        + lastFingerprint
                                        + " new: "
                                        + Build.VERSION.INCREMENTAL);
                        clearCachedConfigForPackage(null);
                        sharedPrefs
                                .edit()
                                .putString(KEY_FINGERPRINT, Build.VERSION.INCREMENTAL)
                                .apply();
                    }
                    break;
                }

                case EVENT_SUBSCRIPTION_INFO_UPDATED:
                    broadcastConfigChangedIntent(phoneId);
                    break;
                case EVENT_MULTI_SIM_CONFIG_CHANGED:
                    onMultiSimConfigChanged();
                    break;

                case EVENT_DO_FETCH_DEFAULT_FOR_NO_SIM_CONFIG: {
                    PersistableBundle config =
                            restoreNoSimConfigFromXml(mPlatformCarrierConfigPackage);

                    if (config != null) {
                        mNoSimConfig = config;
                        sendMessage(
                                obtainMessage(
                                        EVENT_FETCH_DEFAULT_FOR_NO_SIM_CONFIG_DONE,
                                            phoneId, -1));
                    } else {
                        // No cached config, so fetch it from the default app.
                        if (bindToConfigPackage(
                                mPlatformCarrierConfigPackage,
                                phoneId,
                                EVENT_CONNECTED_TO_DEFAULT_FOR_NO_SIM_CONFIG)) {
                            sendMessageDelayed(
                                    obtainMessage(
                                            EVENT_BIND_DEFAULT_FOR_NO_SIM_CONFIG_TIMEOUT,
                                                phoneId, -1), BIND_TIMEOUT_MILLIS);
                        } else {
                            broadcastConfigChangedIntent(phoneId, false);
                            // TODO: We *must* call unbindService even if bindService returns false.
                            // (And possibly if SecurityException was thrown.)
                            loge("binding to default app to fetch no SIM config: "
                                    + mPlatformCarrierConfigPackage + " fails");
                        }
                    }
                    break;
                }

                case EVENT_FETCH_DEFAULT_FOR_NO_SIM_CONFIG_DONE: {
                    broadcastConfigChangedIntent(phoneId, false);
                    break;
                }

                case EVENT_BIND_DEFAULT_FOR_NO_SIM_CONFIG_TIMEOUT:
                case EVENT_FETCH_DEFAULT_FOR_NO_SIM_CONFIG_TIMEOUT: {
                    loge("Bind/fetch time out for no SIM config from "
                            + mPlatformCarrierConfigPackage);
                    removeMessages(EVENT_FETCH_DEFAULT_FOR_NO_SIM_CONFIG_TIMEOUT);
                    // If we attempted to bind to the app, but the service connection is null due to
                    // the race condition that clear config event happens before bind/fetch complete
                    // then config was cleared while we were waiting and we should not continue.
                    if (mServiceConnectionForNoSimConfig[phoneId] != null) {
                        // If a ResponseReceiver callback is in the queue when this happens, we will
                        // unbind twice and throw an exception.
                        unbindIfBoundForNoSimConfig(mContext,
                                mServiceConnectionForNoSimConfig[phoneId], phoneId);
                    }
                    broadcastConfigChangedIntent(phoneId, false);
                    break;
                }

                case EVENT_CONNECTED_TO_DEFAULT_FOR_NO_SIM_CONFIG: {
                    removeMessages(EVENT_BIND_DEFAULT_FOR_NO_SIM_CONFIG_TIMEOUT);
                    final CarrierServiceConnection conn = (CarrierServiceConnection) msg.obj;
                    // If new service connection has been created, unbind.
                    if (mServiceConnectionForNoSimConfig[phoneId] != conn || conn.service == null) {
                        unbindIfBoundForNoSimConfig(mContext, conn, phoneId);
                        break;
                    }

                    // ResultReceiver callback will execute in this Handler's thread.
                    final ResultReceiver resultReceiver =
                            new ResultReceiver(this) {
                                @Override
                                public void onReceiveResult(int resultCode, Bundle resultData) {
                                    unbindIfBoundForNoSimConfig(mContext, conn, phoneId);
                                    // If new service connection has been created, this is stale.
                                    if (mServiceConnectionForNoSimConfig[phoneId] != conn) {
                                        loge("Received response for stale request.");
                                        return;
                                    }
                                    removeMessages(EVENT_FETCH_DEFAULT_FOR_NO_SIM_CONFIG_TIMEOUT);
                                    if (resultCode == RESULT_ERROR || resultData == null) {
                                        // On error, abort config fetching.
                                        loge("Failed to get no SIM carrier config");
                                        return;
                                    }
                                    PersistableBundle config =
                                            resultData.getParcelable(KEY_CONFIG_BUNDLE);
                                    saveNoSimConfigToXml(mPlatformCarrierConfigPackage, config);
                                    mNoSimConfig = config;
                                    sendMessage(
                                            obtainMessage(
                                                    EVENT_FETCH_DEFAULT_FOR_NO_SIM_CONFIG_DONE,
                                                        phoneId, -1));
                                }
                            };
                    // Now fetch the config asynchronously from the ICarrierService.
                    try {
                        ICarrierService carrierService =
                                ICarrierService.Stub.asInterface(conn.service);
                        carrierService.getCarrierConfig(phoneId, null, resultReceiver);
                        logdWithLocalLog("Fetch no sim config from default app: "
                                + mPlatformCarrierConfigPackage);
                    } catch (RemoteException e) {
                        loge("Failed to get no sim carrier config from default app: " +
                                mPlatformCarrierConfigPackage + " err: " + e);
                        unbindIfBoundForNoSimConfig(mContext, conn, phoneId);
                        break; // So we don't set a timeout.
                    }
                    sendMessageDelayed(
                            obtainMessage(
                                    EVENT_FETCH_DEFAULT_FOR_NO_SIM_CONFIG_TIMEOUT,
                                        phoneId, -1), BIND_TIMEOUT_MILLIS);
                    break;
                }
            }
        }
    }

    @NonNull private final Handler mHandler;

    @NonNull private final FeatureFlags  mFeatureFlags;

    @NonNull private final PackageManager mPackageManager;

    /**
     * Constructs a CarrierConfigLoader, registers it as a service, and registers a broadcast
     * receiver for relevant events.
     */
    @VisibleForTesting
    /* package */ CarrierConfigLoader(@NonNull Context context, @NonNull Looper looper,
            @NonNull FeatureFlags featureFlags) {
        super(PermissionEnforcer.fromContext(context));
        mContext = context;
        mPlatformCarrierConfigPackage =
                mContext.getString(R.string.platform_carrier_config_package);
        mHandler = new ConfigHandler(looper);

        IntentFilter systemEventsFilter = new IntentFilter();
        systemEventsFilter.addAction(Intent.ACTION_BOOT_COMPLETED);
        context.registerReceiver(mSystemBroadcastReceiver, systemEventsFilter);

        mNumPhones = TelephonyManager.from(context).getActiveModemCount();
        mConfigFromDefaultApp = new PersistableBundle[mNumPhones];
        mConfigFromCarrierApp = new PersistableBundle[mNumPhones];
        mPersistentOverrideConfigs = new PersistableBundle[mNumPhones];
        mOverrideConfigs = new PersistableBundle[mNumPhones];
        mNoSimConfig = new PersistableBundle();
        mServiceConnection = new CarrierServiceConnection[mNumPhones];
        mServiceBound = new boolean[mNumPhones];
        mHasSentConfigChange = new boolean[mNumPhones];
        mFromSystemUnlocked = new boolean[mNumPhones];
        mServiceConnectionForNoSimConfig = new CarrierServiceConnection[mNumPhones];
        mServiceBoundForNoSimConfig = new boolean[mNumPhones];
        mCarrierServiceChangeCallbacks = new CarrierServiceChangeCallback[mNumPhones];
        for (int phoneId = 0; phoneId < mNumPhones; phoneId++) {
            mCarrierServiceChangeCallbacks[phoneId] = new CarrierServiceChangeCallback(phoneId);
            TelephonyManager.from(context).registerCarrierPrivilegesCallback(phoneId,
                    new HandlerExecutor(mHandler), mCarrierServiceChangeCallbacks[phoneId]);
        }
        mFeatureFlags = featureFlags;
        mPackageManager = context.getPackageManager();
        logd("CarrierConfigLoader has started");

        PhoneConfigurationManager.registerForMultiSimConfigChange(
                mHandler, EVENT_MULTI_SIM_CONFIG_CHANGED, null);

        mHandler.sendEmptyMessage(EVENT_CHECK_SYSTEM_UPDATE);
    }

    /**
     * Initialize the singleton CarrierConfigLoader instance.
     *
     * This is only done once, at startup, from {@link com.android.phone.PhoneApp#onCreate}.
     */
    @NonNull
    /* package */ static CarrierConfigLoader init(@NonNull Context context,
            @NonNull FeatureFlags featureFlags) {
        synchronized (CarrierConfigLoader.class) {
            if (sInstance == null) {
                sInstance = new CarrierConfigLoader(context, Looper.myLooper(), featureFlags);
                // Make this service available through ServiceManager.
                TelephonyFrameworkInitializer.getTelephonyServiceManager()
                        .getCarrierConfigServiceRegisterer().register(sInstance);
            } else {
                Log.wtf(LOG_TAG, "init() called multiple times!  sInstance = " + sInstance);
            }
            return sInstance;
        }
    }

    @VisibleForTesting
    /* package */ void clearConfigForPhone(int phoneId, boolean fetchNoSimConfig) {
        /* Ignore clear configuration request if device is being shutdown. */
        Phone phone = PhoneFactory.getPhone(phoneId);
        if (phone != null) {
            if (phone.isShuttingDown()) {
                return;
            }
        }

        if (mConfigFromDefaultApp.length <= phoneId) {
            Log.wtf(LOG_TAG, "Invalid phone id " + phoneId);
            return;
        }

        mConfigFromDefaultApp[phoneId] = null;
        mConfigFromCarrierApp[phoneId] = null;
        mServiceConnection[phoneId] = null;
        mHasSentConfigChange[phoneId] = false;

        if (fetchNoSimConfig) {
            // To fetch no SIM config
            mHandler.sendMessage(
                    mHandler.obtainMessage(
                            EVENT_DO_FETCH_DEFAULT_FOR_NO_SIM_CONFIG, phoneId, -1));
        }
    }

    private void updateSubscriptionDatabase(int phoneId) {
        logd("updateSubscriptionDatabase: phoneId=" + phoneId);
        String configPackageName;
        PersistableBundle configToSend;
        int carrierId = getSpecificCarrierIdForPhoneId(phoneId);
        // Prefer the carrier privileged carrier app, but if there is not one, use the platform
        // default carrier app.
        if (mConfigFromCarrierApp[phoneId] != null) {
            configPackageName = getCarrierPackageForPhoneId(phoneId);
            configToSend = mConfigFromCarrierApp[phoneId];
        } else {
            configPackageName = mPlatformCarrierConfigPackage;
            configToSend = mConfigFromDefaultApp[phoneId];
        }

        if (configToSend == null) {
            configToSend = new PersistableBundle();
        }

        // mOverrideConfigs is for testing. And it will override current configs.
        PersistableBundle config = mOverrideConfigs[phoneId];
        if (config != null) {
            configToSend = new PersistableBundle(configToSend);
            configToSend.putAll(config);
        }

        SubscriptionManagerService.getInstance().updateSubscriptionByCarrierConfig(
                phoneId, configPackageName, configToSend,
                () -> mHandler.obtainMessage(EVENT_SUBSCRIPTION_INFO_UPDATED, phoneId, -1)
                        .sendToTarget());
    }

    private void broadcastConfigChangedIntent(int phoneId) {
        broadcastConfigChangedIntent(phoneId, true);
    }

    private void broadcastConfigChangedIntent(int phoneId, boolean addSubIdExtra) {
        int subId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        int carrierId = TelephonyManager.UNKNOWN_CARRIER_ID;
        int specificCarrierId = TelephonyManager.UNKNOWN_CARRIER_ID;

        Intent intent = new Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT |
                Intent.FLAG_RECEIVER_FOREGROUND);
        if (addSubIdExtra) {
            int simApplicationState = getSimApplicationStateForPhone(phoneId);
            // Include subId/carrier id extra only if SIM records are loaded
            if (simApplicationState != TelephonyManager.SIM_STATE_UNKNOWN
                    && simApplicationState != TelephonyManager.SIM_STATE_NOT_READY) {
                subId = SubscriptionManager.getSubscriptionId(phoneId);
                carrierId = getCarrierIdForPhoneId(phoneId);
                specificCarrierId = getSpecificCarrierIdForPhoneId(phoneId);
                intent.putExtra(TelephonyManager.EXTRA_SPECIFIC_CARRIER_ID, specificCarrierId);
                SubscriptionManager.putPhoneIdAndSubIdExtra(intent, phoneId);
                intent.putExtra(TelephonyManager.EXTRA_CARRIER_ID, carrierId);
            }
        }
        intent.putExtra(CarrierConfigManager.EXTRA_SLOT_INDEX, phoneId);
        intent.putExtra(CarrierConfigManager.EXTRA_REBROADCAST_ON_UNLOCK,
                mFromSystemUnlocked[phoneId]);

        TelephonyRegistryManager trm = mContext.getSystemService(TelephonyRegistryManager.class);
        // Unlike broadcast, we wouldn't notify registrants on carrier config change when device is
        // unlocked. Only real carrier config change will send the notification to registrants.
        if (trm != null && !mFromSystemUnlocked[phoneId]) {
            trm.notifyCarrierConfigChanged(phoneId, subId, carrierId, specificCarrierId);
        }

        mContext.sendBroadcastAsUser(intent, UserHandle.ALL);

        if (SubscriptionManager.isValidSubscriptionId(subId)) {
            logd("Broadcast CARRIER_CONFIG_CHANGED for phone " + phoneId + ", subId=" + subId);
        } else {
            logd("Broadcast CARRIER_CONFIG_CHANGED for phone " + phoneId);
        }
        mHasSentConfigChange[phoneId] = true;
        mFromSystemUnlocked[phoneId] = false;
    }

    private int getSimApplicationStateForPhone(int phoneId) {
        int simApplicationState = TelephonyManager.SIM_STATE_UNKNOWN;
        int subId = SubscriptionManager.getSubscriptionId(phoneId);
        if (SubscriptionManager.isValidSubscriptionId(subId)) {
            TelephonyManager telMgr = TelephonyManager.from(mContext)
                    .createForSubscriptionId(subId);
            simApplicationState = telMgr.getSimApplicationState();
        }
        return simApplicationState;
    }

    /** Binds to the default or carrier config app. */
    private boolean bindToConfigPackage(@NonNull String pkgName, int phoneId, int eventId) {
        logdWithLocalLog("Binding to " + pkgName + " for phone " + phoneId);
        Intent carrierService = new Intent(CarrierService.CARRIER_SERVICE_INTERFACE);
        carrierService.setPackage(pkgName);
        CarrierServiceConnection serviceConnection =  new CarrierServiceConnection(
                phoneId, pkgName, eventId);
        if (eventId == EVENT_CONNECTED_TO_DEFAULT_FOR_NO_SIM_CONFIG) {
            mServiceConnectionForNoSimConfig[phoneId] = serviceConnection;
        } else {
            mServiceConnection[phoneId] = serviceConnection;
        }
        try {
            if (mContext.bindService(carrierService, serviceConnection,
                    Context.BIND_AUTO_CREATE)) {
                if (eventId == EVENT_CONNECTED_TO_DEFAULT_FOR_NO_SIM_CONFIG) {
                    mServiceBoundForNoSimConfig[phoneId] = true;
                } else {
                    mServiceBound[phoneId] = true;
                }
                return true;
            } else {
                return false;
            }
        } catch (SecurityException ex) {
            return false;
        }
    }

    @VisibleForTesting
    @NonNull
    /* package */ CarrierIdentifier getCarrierIdentifierForPhoneId(int phoneId) {
        String mcc = "";
        String mnc = "";
        String imsi = "";
        String gid1 = "";
        String gid2 = "";
        String spn = TelephonyManager.from(mContext).getSimOperatorNameForPhone(phoneId);
        String simOperator = TelephonyManager.from(mContext).getSimOperatorNumericForPhone(phoneId);
        int carrierId = TelephonyManager.UNKNOWN_CARRIER_ID;
        int specificCarrierId = TelephonyManager.UNKNOWN_CARRIER_ID;
        // A valid simOperator should be 5 or 6 digits, depending on the length of the MNC.
        if (simOperator != null && simOperator.length() >= 3) {
            mcc = simOperator.substring(0, 3);
            mnc = simOperator.substring(3);
        }
        Phone phone = PhoneFactory.getPhone(phoneId);
        if (phone != null) {
            imsi = phone.getSubscriberId();
            gid1 = phone.getGroupIdLevel1();
            gid2 = phone.getGroupIdLevel2();
            carrierId = phone.getCarrierId();
            specificCarrierId = phone.getSpecificCarrierId();
        }
        return new CarrierIdentifier(mcc, mnc, spn, imsi, gid1, gid2, carrierId, specificCarrierId);
    }

    /** Returns the package name of a privileged carrier app, or null if there is none. */
    @Nullable
    private String getCarrierPackageForPhoneId(int phoneId) {
        final long token = Binder.clearCallingIdentity();
        try {
            return TelephonyManager.from(mContext)
                    .getCarrierServicePackageNameForLogicalSlot(phoneId);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    @Nullable
    private String getIccIdForPhoneId(int phoneId) {
        if (!SubscriptionManager.isValidPhoneId(phoneId)) {
            return null;
        }
        Phone phone = PhoneFactory.getPhone(phoneId);
        if (phone == null) {
            return null;
        }
        return phone.getIccSerialNumber();
    }

    /**
     * Get the sim specific carrier id {@link TelephonyManager#getSimSpecificCarrierId()}
     */
    private int getSpecificCarrierIdForPhoneId(int phoneId) {
        if (!SubscriptionManager.isValidPhoneId(phoneId)) {
            return TelephonyManager.UNKNOWN_CARRIER_ID;
        }
        Phone phone = PhoneFactory.getPhone(phoneId);
        if (phone == null) {
            return TelephonyManager.UNKNOWN_CARRIER_ID;
        }
        return phone.getSpecificCarrierId();
    }

    /**
     * Get the sim carrier id {@link TelephonyManager#getSimCarrierId() }
     */
    private int getCarrierIdForPhoneId(int phoneId) {
        if (!SubscriptionManager.isValidPhoneId(phoneId)) {
            return TelephonyManager.UNKNOWN_CARRIER_ID;
        }
        Phone phone = PhoneFactory.getPhone(phoneId);
        if (phone == null) {
            return TelephonyManager.UNKNOWN_CARRIER_ID;
        }
        return phone.getCarrierId();
    }

    /**
     * Writes a bundle to an XML file.
     *
     * The bundle will be written to a file named after the package name, ICCID and
     * specific carrier id {@link TelephonyManager#getSimSpecificCarrierId()}. the same carrier
     * should have a single copy of XML file named after carrier id. However, it's still possible
     * that platform doesn't recognize the current sim carrier, we will use iccid + carrierid as
     * the canonical file name. carrierid can also handle the cases SIM OTA resolves to different
     * carrier while iccid remains the same.
     *
     * The file can be restored later with {@link @restoreConfigFromXml}. The XML output will
     * include the bundle and the current version of the specified package.
     *
     * In case of errors or invalid input, no file will be written.
     *
     * @param packageName   the name of the package from which we fetched this bundle.
     * @param extraString   An extra string to be used in the XML file name.
     * @param phoneId       the phone ID.
     * @param carrierId     contains all carrier-identifying information.
     * @param config        the bundle to be written. Null will be treated as an empty bundle.
     * @param isNoSimConfig whether this is invoked for noSimConfig or not.
     */
    private void saveConfigToXml(@Nullable String packageName, @NonNull String extraString,
            int phoneId, @Nullable CarrierIdentifier carrierId, @NonNull PersistableBundle config,
            boolean isNoSimConfig) {
        if (packageName == null) {
            loge("Cannot save config with null packageName. phoneId=" + phoneId);
            return;
        }

        String fileName;
        if (isNoSimConfig) {
            fileName = getFilenameForNoSimConfig(packageName);
        } else {
            if (TelephonyManager.getSimStateForSlotIndex(phoneId)
                    != TelephonyManager.SIM_STATE_LOADED) {
                loge("Skip saving config because SIM records are not loaded. phoneId=" + phoneId);
                return;
            }

            final String iccid = getIccIdForPhoneId(phoneId);
            final int cid = carrierId != null ? carrierId.getSpecificCarrierId()
                    : TelephonyManager.UNKNOWN_CARRIER_ID;
            if (iccid == null) {
                loge("Cannot save config with null iccid. phoneId=" + phoneId);
                return;
            }
            fileName = getFilenameForConfig(packageName, extraString, iccid, cid);
        }

        // b/32668103 Only save to file if config isn't empty.
        // In case of failure, not caching an empty bundle will
        // try loading config again on next power on or sim loaded.
        // Downside is for genuinely empty bundle, will bind and load
        // on every power on.
        if (config == null || config.isEmpty()) {
            return;
        }

        final String version = getPackageVersion(packageName);
        if (version == null) {
            loge("Failed to get package version for: " + packageName + ", phoneId=" + phoneId);
            return;
        }

        logdWithLocalLog("Save carrier config to cache. phoneId=" + phoneId
                        + ", xml=" + getFilePathForLogging(fileName) + ", version=" + version);

        FileOutputStream outFile = null;
        try {
            outFile = new FileOutputStream(new File(mContext.getFilesDir(), fileName));
            config.putString(KEY_VERSION, version);
            config.writeToStream(outFile);
            outFile.flush();
            outFile.close();
        } catch (IOException e) {
            loge(e.toString());
        }
    }

    @VisibleForTesting
    /* package */ void saveConfigToXml(@Nullable String packageName, @NonNull String extraString,
            int phoneId, @NonNull CarrierIdentifier carrierId, @NonNull PersistableBundle config) {
        saveConfigToXml(packageName, extraString, phoneId, carrierId, config, false);
    }

    @VisibleForTesting
    /* package */ void saveNoSimConfigToXml(@Nullable String packageName,
            @NonNull PersistableBundle config) {
        saveConfigToXml(packageName, "", -1, null, config, true);
    }

    /**
     * Reads a bundle from an XML file.
     *
     * This restores a bundle that was written with {@link #saveConfigToXml}. This returns the saved
     * config bundle for the given package and phone ID.
     *
     * In case of errors, or if the saved config is from a different package version than the
     * current version, then null will be returned.
     *
     * @param packageName    the name of the package from which we fetched this bundle.
     * @param extraString    An extra string to be used in the XML file name.
     * @param phoneId        the phone ID.
     * @param isNoSimConfig  whether this is invoked for noSimConfig or not.
     * @return the bundle from the XML file. Returns null if there is no saved config, the saved
     * version does not match, or reading config fails.
     */
    @Nullable
    private PersistableBundle restoreConfigFromXml(@Nullable String packageName,
            @NonNull String extraString, int phoneId, boolean isNoSimConfig) {
        if (packageName == null) {
            loge("Cannot restore config with null packageName");
        }
        final String version = getPackageVersion(packageName);
        if (version == null) {
            loge("Failed to get package version for: " + packageName);
            return null;
        }

        String fileName;
        String iccid = null;
        if (isNoSimConfig) {
            fileName = getFilenameForNoSimConfig(packageName);
        } else {
            if (TelephonyManager.getSimStateForSlotIndex(phoneId)
                    != TelephonyManager.SIM_STATE_LOADED) {
                loge("Skip restore config because SIM records are not loaded. phoneId=" + phoneId);
                return null;
            }

            iccid = getIccIdForPhoneId(phoneId);
            final int cid = getSpecificCarrierIdForPhoneId(phoneId);
            if (iccid == null) {
                loge("Cannot restore config with null iccid. phoneId=" + phoneId);
                return null;
            }
            fileName = getFilenameForConfig(packageName, extraString, iccid, cid);
        }

        PersistableBundle restoredBundle = null;
        File file = new File(mContext.getFilesDir(), fileName);
        String filePath = file.getPath();
        String savedVersion = null;
        try (FileInputStream inFile = new FileInputStream(file)) {

            restoredBundle = PersistableBundle.readFromStream(inFile);
            savedVersion = restoredBundle.getString(KEY_VERSION);
            restoredBundle.remove(KEY_VERSION);

            if (!version.equals(savedVersion)) {
                loge("Saved version mismatch: " + version + " vs " + savedVersion
                        + ", phoneId=" + phoneId);
                restoredBundle = null;
            }
        } catch (FileNotFoundException e) {
            // Missing file is normal occurrence that might occur with a new sim or when restoring
            // an override file during boot and should not be treated as an error.
            if (isNoSimConfig) {
                logd("File not found: " + file.getPath() + ", phoneId=" + phoneId);
            } else {
                logd("File not found : " + getFilePathForLogging(filePath, iccid)
                        + ", phoneId=" + phoneId);
            }
        } catch (IOException e) {
            loge(e.toString());
        }

        if (restoredBundle != null) {
            logdWithLocalLog("Restored carrier config from cache. phoneId=" + phoneId + ", xml="
                    + getFilePathForLogging(fileName) + ", version=" + savedVersion
                    + ", modified time=" + getFileTime(filePath));
        }
        return restoredBundle;
    }

    /**
     * This method will mask most part of iccid in the filepath for logging on userbuild
     */
    @NonNull
    private String getFilePathForLogging(@Nullable String filePath, @Nullable String iccid) {
        // If loggable then return with actual file path
        if (TelephonyUtils.IS_DEBUGGABLE) {
            return filePath;
        }
        String path = filePath;
        int length = (iccid != null) ? iccid.length() : 0;
        if (length > 5 && filePath != null) {
            path = filePath.replace(iccid.substring(5), "***************");
        }
        return path;
    }

    @Nullable
    private PersistableBundle restoreConfigFromXml(@Nullable String packageName,
            @NonNull String extraString, int phoneId) {
        return restoreConfigFromXml(packageName, extraString, phoneId, false);
    }

    @Nullable
    private PersistableBundle restoreNoSimConfigFromXml(@Nullable String packageName) {
        return restoreConfigFromXml(packageName, "", -1, true);
    }

    /**
     * Clears cached carrier config.
     * This deletes all saved XML files associated with the given package name. If packageName is
     * null, then it deletes all saved XML files.
     *
     * @param packageName the name of a carrier package, or null if all cached config should be
     *                    cleared.
     * @return true iff one or more files were deleted.
     */
    private boolean clearCachedConfigForPackage(@Nullable final String packageName) {
        File dir = mContext.getFilesDir();
        File[] packageFiles = dir.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String filename) {
                if (packageName != null) {
                    return filename.startsWith("carrierconfig-" + packageName + "-");
                } else {
                    return filename.startsWith("carrierconfig-");
                }
            }
        });
        if (packageFiles == null || packageFiles.length < 1) return false;
        for (File f : packageFiles) {
            logdWithLocalLog("Deleting " + getFilePathForLogging(f.getName()));
            f.delete();
        }
        return true;
    }

    private String getFilePathForLogging(String filePath) {
        if (!TextUtils.isEmpty(filePath)) {
            String[] fileTokens = filePath.split("-");
            if (fileTokens != null && fileTokens.length > 2) {
                String iccid = fileTokens[fileTokens.length -2];
                return getFilePathForLogging(filePath, iccid);
            }
            return filePath;
        }
        return filePath;
    }

    /** Builds a canonical file name for a config file. */
    @NonNull
    private static String getFilenameForConfig(
            @NonNull String packageName, @NonNull String extraString,
            @NonNull String iccid, int cid) {
        // the same carrier should have a single copy of XML file named after carrier id.
        // However, it's still possible that platform doesn't recognize the current sim carrier,
        // we will use iccid + carrierid as the canonical file name. carrierid can also handle the
        // cases SIM OTA resolves to different carrier while iccid remains the same.
        return "carrierconfig-" + packageName + extraString + "-" + iccid + "-" + cid + ".xml";
    }

    /** Builds a canonical file name for no SIM config file. */
    @NonNull
    private String getFilenameForNoSimConfig(@NonNull String packageName) {
        return "carrierconfig-" + packageName + "-" + "nosim" + ".xml";
    }

    /** Return the current version code of a package, or null if the name is not found. */
    @Nullable
    private String getPackageVersion(@NonNull String packageName) {
        try {
            PackageInfo info = mContext.getPackageManager().getPackageInfo(packageName, 0);
            return Long.toString(info.getLongVersionCode());
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    /**
     * Read up to date config.
     *
     * This reads config bundles for the given phoneId. That means getting the latest bundle from
     * the default app and a privileged carrier app, if present. This will not bind to an app if we
     * have a saved config file to use instead.
     */
    private void updateConfigForPhoneId(int phoneId) {
        mHandler.sendMessage(mHandler.obtainMessage(EVENT_DO_FETCH_DEFAULT, phoneId, -1));
    }

    private void onMultiSimConfigChanged() {
        int oldNumPhones = mNumPhones;
        mNumPhones = TelephonyManager.from(mContext).getActiveModemCount();
        if (mNumPhones == oldNumPhones) {
            return;
        }
        logdWithLocalLog("mNumPhones change from " + oldNumPhones + " to " + mNumPhones);

        // If DS -> SS switch, release the resources BEFORE truncating the arrays to avoid leaking
        for (int phoneId = mNumPhones; phoneId < oldNumPhones; phoneId++) {
            if (mServiceConnection[phoneId] != null) {
                unbindIfBound(mContext, mServiceConnection[phoneId], phoneId);
            }
            if (mServiceConnectionForNoSimConfig[phoneId] != null) {
                unbindIfBoundForNoSimConfig(mContext, mServiceConnectionForNoSimConfig[phoneId],
                        phoneId);
            }
        }

        // The phone to slot mapping may change, unregister here and re-register callbacks later
        for (int phoneId = 0; phoneId < oldNumPhones; phoneId++) {
            if (mCarrierServiceChangeCallbacks[phoneId] != null) {
                TelephonyManager.from(mContext).unregisterCarrierPrivilegesCallback(
                        mCarrierServiceChangeCallbacks[phoneId]);
            }
        }

        // Copy the original arrays, truncate or padding with zeros (if necessary) to new length
        mConfigFromDefaultApp = Arrays.copyOf(mConfigFromDefaultApp, mNumPhones);
        mConfigFromCarrierApp = Arrays.copyOf(mConfigFromCarrierApp, mNumPhones);
        mPersistentOverrideConfigs = Arrays.copyOf(mPersistentOverrideConfigs, mNumPhones);
        mOverrideConfigs = Arrays.copyOf(mOverrideConfigs, mNumPhones);
        mServiceConnection = Arrays.copyOf(mServiceConnection, mNumPhones);
        mServiceConnectionForNoSimConfig =
                Arrays.copyOf(mServiceConnectionForNoSimConfig, mNumPhones);
        mServiceBound = Arrays.copyOf(mServiceBound, mNumPhones);
        mServiceBoundForNoSimConfig = Arrays.copyOf(mServiceBoundForNoSimConfig, mNumPhones);
        mHasSentConfigChange = Arrays.copyOf(mHasSentConfigChange, mNumPhones);
        mFromSystemUnlocked = Arrays.copyOf(mFromSystemUnlocked, mNumPhones);
        mCarrierServiceChangeCallbacks = Arrays.copyOf(mCarrierServiceChangeCallbacks, mNumPhones);

        // Load the config for all the phones and re-register callback AFTER padding the arrays.
        for (int phoneId = 0; phoneId < mNumPhones; phoneId++) {
            updateConfigForPhoneId(phoneId);
            mCarrierServiceChangeCallbacks[phoneId] = new CarrierServiceChangeCallback(phoneId);
            TelephonyManager.from(mContext).registerCarrierPrivilegesCallback(phoneId,
                    new HandlerExecutor(mHandler), mCarrierServiceChangeCallbacks[phoneId]);
        }
    }

    @Override
    @NonNull
    public PersistableBundle getConfigForSubId(int subscriptionId, @NonNull String callingPackage) {
        return getConfigForSubIdWithFeature(subscriptionId, callingPackage, null);
    }

    @Override
    @NonNull
    public PersistableBundle getConfigForSubIdWithFeature(int subscriptionId,
            @NonNull String callingPackage, @Nullable String callingFeatureId) {
        if (!TelephonyPermissions.checkCallingOrSelfReadPhoneState(mContext, subscriptionId,
                callingPackage, callingFeatureId, "getCarrierConfig")) {
            return new PersistableBundle();
        }

        enforceTelephonyFeatureWithException(callingPackage, "getConfigForSubIdWithFeature");

        int phoneId = SubscriptionManager.getPhoneId(subscriptionId);
        PersistableBundle retConfig = CarrierConfigManager.getDefaultConfig();
        if (SubscriptionManager.isValidPhoneId(phoneId)) {
            PersistableBundle config = mConfigFromDefaultApp[phoneId];
            if (config != null) {
                retConfig.putAll(config);
            }
            config = mConfigFromCarrierApp[phoneId];
            if (config != null) {
                retConfig.putAll(config);
            }
            config = mPersistentOverrideConfigs[phoneId];
            if (config != null) {
                retConfig.putAll(config);
            }
            config = mOverrideConfigs[phoneId];
            if (config != null) {
                retConfig.putAll(config);
            }
            // Ignore the theoretical case of the default app not being present since that won't
            // work in CarrierConfigLoader today.
            final boolean allConfigsApplied =
                    (mConfigFromCarrierApp[phoneId] != null
                        || getCarrierPackageForPhoneId(phoneId) == null)
                    && mConfigFromDefaultApp[phoneId] != null;
            retConfig.putBoolean(
                    CarrierConfigManager.KEY_CARRIER_CONFIG_APPLIED_BOOL, allConfigsApplied);
        } else {
            if (mNoSimConfig != null) {
                retConfig.putAll(mNoSimConfig);
            }
        }
        return retConfig;
    }

    @Override
    @NonNull
    public PersistableBundle getConfigSubsetForSubIdWithFeature(int subscriptionId,
            @NonNull String callingPackage, @Nullable String callingFeatureId,
            @NonNull String[] keys) {
        Objects.requireNonNull(callingPackage, "Calling package must be non-null");
        Objects.requireNonNull(keys, "Config keys must be non-null");
        enforceCallerIsSystemOrRequestingPackage(callingPackage);

        enforceTelephonyFeatureWithException(callingPackage,
                "getConfigSubsetForSubIdWithFeature");

        // Permission check is performed inside and an empty bundle will return on failure.
        // No SecurityException thrown here since most clients expect to retrieve the overridden
        // value if present or use default one if not
        PersistableBundle allConfigs = getConfigForSubIdWithFeature(subscriptionId, callingPackage,
                callingFeatureId);
        if (allConfigs.isEmpty()) {
            return allConfigs;
        }
        for (String key : keys) {
            Objects.requireNonNull(key, "Config key must be non-null");
        }

        PersistableBundle configSubset = new PersistableBundle(
                keys.length + CONFIG_SUBSET_METADATA_KEYS.length);
        for (String carrierConfigKey : keys) {
            Object value = allConfigs.get(carrierConfigKey);
            if (value == null) {
                // Filter out keys without values.
                // In history, many AOSP or OEMs/carriers private configs didn't provide default
                // values. We have to continue supporting them for now. See b/261776046 for details.
                continue;
            }
            // Config value itself could be PersistableBundle which requires different API to put
            if (value instanceof PersistableBundle) {
                configSubset.putPersistableBundle(carrierConfigKey, (PersistableBundle) value);
            } else {
                configSubset.putObject(carrierConfigKey, value);
            }
        }

        // Configs in CONFIG_SUBSET_ALWAYS_INCLUDED_KEYS should always be included
        for (String generalKey : CONFIG_SUBSET_METADATA_KEYS) {
            configSubset.putObject(generalKey, allConfigs.get(generalKey));
        }

        return configSubset;
    }

    @android.annotation.EnforcePermission(android.Manifest.permission.MODIFY_PHONE_STATE)
    @Override
    public void overrideConfig(int subscriptionId, @Nullable PersistableBundle overrides,
            boolean persistent) {
        overrideConfig_enforcePermission();
        int phoneId = SubscriptionManager.getPhoneId(subscriptionId);
        if (!SubscriptionManager.isValidPhoneId(phoneId)) {
            logd("Ignore invalid phoneId: " + phoneId + " for subId: " + subscriptionId);
            throw new IllegalArgumentException(
                    "Invalid phoneId " + phoneId + " for subId " + subscriptionId);
        }

        enforceTelephonyFeatureWithException(getCurrentPackageName(), "overrideConfig");

        // Post to run on handler thread on which all states should be confined.
        mHandler.post(() -> {
            overrideConfig(mOverrideConfigs, phoneId, overrides);

            if (persistent) {
                overrideConfig(mPersistentOverrideConfigs, phoneId, overrides);

                if (overrides != null) {
                    final CarrierIdentifier carrierId = getCarrierIdentifierForPhoneId(phoneId);
                    saveConfigToXml(mPlatformCarrierConfigPackage, OVERRIDE_PACKAGE_ADDITION,
                            phoneId,
                            carrierId, mPersistentOverrideConfigs[phoneId]);
                } else {
                    final String iccid = getIccIdForPhoneId(phoneId);
                    final int cid = getSpecificCarrierIdForPhoneId(phoneId);
                    String fileName = getFilenameForConfig(mPlatformCarrierConfigPackage,
                            OVERRIDE_PACKAGE_ADDITION, iccid, cid);
                    File fileToDelete = new File(mContext.getFilesDir(), fileName);
                    fileToDelete.delete();
                }
            }
            logdWithLocalLog("overrideConfig: subId=" + subscriptionId + ", persistent="
                    + persistent + ", overrides=" + overrides);
            updateSubscriptionDatabase(phoneId);
        });
    }

    private void overrideConfig(@NonNull PersistableBundle[] currentOverrides, int phoneId,
            @Nullable PersistableBundle overrides) {
        if (overrides == null) {
            currentOverrides[phoneId] = new PersistableBundle();
        } else if (currentOverrides[phoneId] == null) {
            currentOverrides[phoneId] = overrides;
        } else {
            currentOverrides[phoneId].putAll(overrides);
        }
    }

    @Override
    public void notifyConfigChangedForSubId(int subscriptionId) {
        // Requires the calling app to be either a carrier privileged app for this subId or
        // system privileged app with MODIFY_PHONE_STATE permission.
        TelephonyPermissions.enforceCallingOrSelfModifyPermissionOrCarrierPrivilege(mContext,
                subscriptionId, "Require carrier privileges or MODIFY_PHONE_STATE permission.");

        int phoneId = SubscriptionManager.getPhoneId(subscriptionId);
        if (!SubscriptionManager.isValidPhoneId(phoneId)) {
            final String msg =
                    "Ignore invalid phoneId: " + phoneId + " for subId: " + subscriptionId;
            if (mFeatureFlags.addAnomalyWhenNotifyConfigChangedWithInvalidPhone()) {
                AnomalyReporter.reportAnomaly(
                        UUID.fromString(UUID_NOTIFY_CONFIG_CHANGED_WITH_INVALID_PHONE), msg);
            }
            logd(msg);
            throw new IllegalArgumentException(msg);
        }

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                "notifyConfigChangedForSubId");

        logdWithLocalLog("Notified carrier config changed. phoneId=" + phoneId
                + ", subId=" + subscriptionId);

        // This method should block until deleting has completed, so that an error which prevents us
        // from clearing the cache is passed back to the carrier app. With the files successfully
        // deleted, this can return and we will eventually bind to the carrier app.
        String callingPackageName = mContext.getPackageManager().getNameForUid(
                Binder.getCallingUid());
        clearCachedConfigForPackage(callingPackageName);
        updateConfigForPhoneId(phoneId);
    }

    @android.annotation.EnforcePermission(android.Manifest.permission.MODIFY_PHONE_STATE)
    @Override
    public void updateConfigForPhoneId(int phoneId, @NonNull String simState) {
        updateConfigForPhoneId_enforcePermission();
        logdWithLocalLog("Update config for phoneId=" + phoneId + " simState=" + simState);
        if (!SubscriptionManager.isValidPhoneId(phoneId)) {
            throw new IllegalArgumentException("Invalid phoneId: " + phoneId);
        }

        enforceTelephonyFeatureWithException(getCurrentPackageName(), "updateConfigForPhoneId");

        // requires Java 7 for switch on string.
        switch (simState) {
            case IccCardConstants.INTENT_VALUE_ICC_ABSENT:
            case IccCardConstants.INTENT_VALUE_ICC_CARD_IO_ERROR:
            case IccCardConstants.INTENT_VALUE_ICC_CARD_RESTRICTED:
            case IccCardConstants.INTENT_VALUE_ICC_UNKNOWN:
            case IccCardConstants.INTENT_VALUE_ICC_NOT_READY:
                mHandler.sendMessage(mHandler.obtainMessage(EVENT_CLEAR_CONFIG, phoneId, -1));
                break;
            case IccCardConstants.INTENT_VALUE_ICC_LOADED:
            case IccCardConstants.INTENT_VALUE_ICC_LOCKED:
                updateConfigForPhoneId(phoneId);
                break;
        }
    }

    @android.annotation.EnforcePermission(android.Manifest.permission.READ_PRIVILEGED_PHONE_STATE)
    @Override
    @NonNull
    public String getDefaultCarrierServicePackageName() {
        getDefaultCarrierServicePackageName_enforcePermission();

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                "getDefaultCarrierServicePackageName");

        return mPlatformCarrierConfigPackage;
    }

    @VisibleForTesting
    @NonNull
    /* package */ Handler getHandler() {
        return mHandler;
    }

    @VisibleForTesting
    @Nullable
    /* package */ PersistableBundle getConfigFromDefaultApp(int phoneId) {
        return mConfigFromDefaultApp[phoneId];
    }

    @VisibleForTesting
    @Nullable
    /* package */ PersistableBundle getConfigFromCarrierApp(int phoneId) {
        return mConfigFromCarrierApp[phoneId];
    }

    @VisibleForTesting
    @NonNull
     /* package */ PersistableBundle getNoSimConfig() {
        return mNoSimConfig;
    }

    @VisibleForTesting
    @Nullable
    /* package */ PersistableBundle getOverrideConfig(int phoneId) {
        return mOverrideConfigs[phoneId];
    }

    // TODO(b/185129900): always call unbindService after bind, no matter if it succeeded
    private void unbindIfBound(@NonNull Context context, @NonNull CarrierServiceConnection conn,
            int phoneId) {
        if (mServiceBound[phoneId]) {
            mServiceBound[phoneId] = false;
            context.unbindService(conn);
        }
    }

    private void unbindIfBoundForNoSimConfig(@NonNull Context context,
            @NonNull CarrierServiceConnection conn, int phoneId) {
        if (mServiceBoundForNoSimConfig[phoneId]) {
            mServiceBoundForNoSimConfig[phoneId] = false;
            context.unbindService(conn);
        }
    }

    /**
     * Returns a boxed Integer object for phoneId, services as message token to distinguish messages
     * with same code when calling {@link Handler#removeMessages(int, Object)}.
     */
    @NonNull
    private Integer getMessageToken(int phoneId) {
        if (phoneId < -128 || phoneId > 127) {
            throw new IllegalArgumentException("phoneId should be in range [-128, 127], inclusive");
        }
        // Integer#valueOf guarantees the integers within [-128, 127] are cached and thus memory
        // comparison (==) returns true for the same integer.
        return Integer.valueOf(phoneId);
    }

    /**
     * Get the file time in readable format.
     *
     * @param filePath The full file path.
     *
     * @return The time in string format.
     */
    @Nullable
    private String getFileTime(@NonNull String filePath) {
        String formattedModifiedTime = null;
        try {
            // Convert the modified time to a readable format
            formattedModifiedTime = TIME_FORMAT.format(Files.readAttributes(Paths.get(filePath),
                    BasicFileAttributes.class).lastModifiedTime().toMillis());
        } catch (Exception e) {
            e.printStackTrace();
        }

        return formattedModifiedTime;
    }

    /**
     * If {@code args} contains {@link #DUMP_ARG_REQUESTING_PACKAGE} and a following package name,
     * we'll also call {@link IBinder#dump} on the default carrier service (if bound) and the
     * specified carrier service (if bound). Typically, this is done for connectivity bug reports
     * where we don't call {@code dumpsys activity service all-non-platform} because that contains
     * too much info, but we still want to let carrier apps include their diagnostics.
     */
    @Override
    public void dump(@NonNull FileDescriptor fd, @NonNull PrintWriter pw, @NonNull String[] args) {
        IndentingPrintWriter indentPW = new IndentingPrintWriter(pw, "    ");
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.DUMP)
                != PackageManager.PERMISSION_GRANTED) {
            indentPW.println("Permission Denial: can't dump carrierconfig from from pid="
                    + Binder.getCallingPid() + ", uid=" + Binder.getCallingUid());
            return;
        }
        String requestingPackage = null;
        int requestingPackageIndex = ArrayUtils.indexOf(args, DUMP_ARG_REQUESTING_PACKAGE);
        if (requestingPackageIndex >= 0 && requestingPackageIndex < args.length - 1
                && !TextUtils.isEmpty(args[requestingPackageIndex + 1])) {
            requestingPackage = args[requestingPackageIndex + 1];
            // Throws a SecurityException if the caller is impersonating another app in an effort to
            // dump extra info (which may contain PII the caller doesn't have a right to).
            enforceCallerIsSystemOrRequestingPackage(requestingPackage);
        }

        indentPW.println("CarrierConfigLoader: " + this);
        for (int i = 0; i < mNumPhones; i++) {
            indentPW.println("Phone Id = " + i);
            // display default values in CarrierConfigManager
            printConfig(CarrierConfigManager.getDefaultConfig(), indentPW,
                    "Default Values from CarrierConfigManager");
            // display ConfigFromDefaultApp
            printConfig(mConfigFromDefaultApp[i], indentPW, "mConfigFromDefaultApp");
            // display ConfigFromCarrierApp
            printConfig(mConfigFromCarrierApp[i], indentPW, "mConfigFromCarrierApp");
            printConfig(mPersistentOverrideConfigs[i], indentPW, "mPersistentOverrideConfigs");
            printConfig(mOverrideConfigs[i], indentPW, "mOverrideConfigs");
        }

        printConfig(mNoSimConfig, indentPW, "mNoSimConfig");
        indentPW.println("mNumPhones=" + mNumPhones);
        indentPW.println("mPlatformCarrierConfigPackage=" + mPlatformCarrierConfigPackage);
        indentPW.println("mServiceConnection=[" + Stream.of(mServiceConnection)
                .map(c -> c != null ? c.pkgName : null)
                .collect(Collectors.joining(", ")) + "]");
        indentPW.println("mServiceBoundForNoSimConfig="
                + Arrays.toString(mServiceBoundForNoSimConfig));
        indentPW.println("mHasSentConfigChange=" + Arrays.toString(mHasSentConfigChange));
        indentPW.println("mFromSystemUnlocked=" + Arrays.toString(mFromSystemUnlocked));
        indentPW.println();
        indentPW.println("CarrierConfigLoader local log=");
        indentPW.increaseIndent();
        mCarrierConfigLoadingLog.dump(fd, indentPW, args);
        indentPW.decreaseIndent();

        if (requestingPackage != null) {
            logd("Including default and requesting package " + requestingPackage
                    + " carrier services in dump");
            indentPW.println("");
            indentPW.println("Connected services");
            dumpCarrierServiceIfBound(fd, indentPW, "Default config package",
                    mPlatformCarrierConfigPackage, false /* considerCarrierPrivileges */);
            dumpCarrierServiceIfBound(fd, indentPW, "Requesting package", requestingPackage,
                    true /* considerCarrierPrivileges */);
        }

        indentPW.println();
        indentPW.println("Cached config files:");
        indentPW.increaseIndent();
        for (File f : mContext.getFilesDir().listFiles((FilenameFilter) (d, filename)
                -> filename.startsWith("carrierconfig-"))) {
            indentPW.println(getFilePathForLogging(f.getName()) + ", modified time="
                    + getFileTime(f.getAbsolutePath()));
        }
        indentPW.decreaseIndent();
    }

    private void printConfig(@NonNull PersistableBundle configApp,
            @NonNull IndentingPrintWriter indentPW, @NonNull String name) {
        indentPW.increaseIndent();
        if (configApp == null) {
            indentPW.println(name + " : null ");
            indentPW.decreaseIndent();
            indentPW.println("");
            return;
        }
        indentPW.println(name + " : ");
        List<String> sortedKeys = new ArrayList<String>(configApp.keySet());
        Collections.sort(sortedKeys);
        indentPW.increaseIndent();
        indentPW.increaseIndent();
        for (String key : sortedKeys) {
            if (configApp.get(key) != null && configApp.get(key) instanceof Object[]) {
                indentPW.println(key + " = " +
                        Arrays.toString((Object[]) configApp.get(key)));
            } else if (configApp.get(key) != null && configApp.get(key) instanceof int[]) {
                indentPW.println(key + " = " + Arrays.toString((int[]) configApp.get(key)));
            } else {
                indentPW.println(key + " = " + configApp.get(key));
            }
        }
        indentPW.decreaseIndent();
        indentPW.decreaseIndent();
        indentPW.decreaseIndent();
        indentPW.println("");
    }

    /**
     * Passes without problem when one of these conditions is true:
     * - The caller is a privileged UID (e.g. for dumpstate.cpp generating a bug report, where the
     * system knows the true caller plumbed in through the {@link android.os.BugreportManager} API).
     * - The caller's UID matches the supplied package.
     *
     * @throws SecurityException if none of the above conditions are met.
     */
    private void enforceCallerIsSystemOrRequestingPackage(@NonNull String requestingPackage)
            throws SecurityException {
        final int callingUid = Binder.getCallingUid();
        if (callingUid == Process.ROOT_UID || callingUid == Process.SYSTEM_UID
                || callingUid == Process.SHELL_UID || callingUid == Process.PHONE_UID) {
            // Bug reports (dumpstate.cpp) run as SHELL, and let some other privileged UIDs through
            // as well.
            return;
        }
        // An app is trying to dump extra detail, block it if they aren't who they claim to be.
        AppOpsManager appOps = mContext.getSystemService(AppOpsManager.class);
        if (appOps == null) {
            throw new SecurityException("No AppOps");
        }
        // Will throw a SecurityException if the UID and package don't match.
        appOps.checkPackage(callingUid, requestingPackage);
    }

    /**
     * Searches for one or more appropriate {@link CarrierService} instances to dump based on the
     * current connections.
     *
     * @param targetPkgName             the target package name to dump carrier services for
     * @param considerCarrierPrivileges if true, allow a carrier service to be dumped if it shares
     *                                  carrier privileges with {@code targetPkgName};
     *                                  otherwise, only dump a carrier service if it is {@code
     *                                  targetPkgName}
     */
    private void dumpCarrierServiceIfBound(@NonNull FileDescriptor fd,
            @NonNull IndentingPrintWriter indentPW, @NonNull String prefix,
            @NonNull String targetPkgName, boolean considerCarrierPrivileges) {
        // Null package is possible if it's early in the boot process, there was a recent crash, we
        // loaded the config from XML most recently, or a SIM slot is empty. Carrier apps with
        // long-lived bindings should typically get dumped here regardless. Even if an app is being
        // used for multiple phoneIds, we assume that it's smart enough to handle that on its own,
        // and that in most cases we'd just be dumping duplicate information and bloating a report.
        indentPW.increaseIndent();
        indentPW.println(prefix + " : " + targetPkgName);
        Set<String> dumpedPkgNames = new ArraySet<>(mServiceConnection.length);
        for (CarrierServiceConnection connection : mServiceConnection) {
            if (connection == null || !SubscriptionManager.isValidPhoneId(connection.phoneId)
                    || TextUtils.isEmpty(connection.pkgName)) {
                continue;
            }
            final String servicePkgName = connection.pkgName;
            // Note: we intentionally ignore system components here because we should NOT match the
            // shell caller that's typically used for bug reports via non-BugreportManager triggers.
            final boolean exactPackageMatch = TextUtils.equals(targetPkgName, servicePkgName);
            final boolean carrierPrivilegesMatch =
                    considerCarrierPrivileges && hasCarrierPrivileges(targetPkgName,
                            connection.phoneId);
            if (!exactPackageMatch && !carrierPrivilegesMatch) continue;
            // Make sure this service is actually alive before trying to dump it. We don't pay
            // attention to mServiceBound[connection.phoneId] because typically carrier apps will
            // request long-lived bindings, and even if we unbind the app, it may still be alive due
            // to CarrierServiceBindHelper. Pull it out as a reference so even if it gets set to
            // null within the ServiceConnection during unbinding we can avoid an NPE.
            final IBinder service = connection.service;
            if (service == null || !service.isBinderAlive() || !service.pingBinder()) continue;
            // We've got a live service. Last check is just to make sure we don't dump a package
            // multiple times.
            if (!dumpedPkgNames.add(servicePkgName)) continue;
            if (!exactPackageMatch) {
                logd(targetPkgName + " has carrier privileges on phoneId " + connection.phoneId
                        + ", service provided by " + servicePkgName);
                indentPW.increaseIndent();
                indentPW.println("Proxy : " + servicePkgName);
                indentPW.decreaseIndent();
            }
            // Flush before we let the app output anything to ensure correct ordering of output.
            // Internally, Binder#dump calls flush on its printer after finishing so we don't
            // need to do anything after.
            indentPW.flush();
            try {
                logd("Dumping " + servicePkgName);
                // We don't need to give the carrier service any args.
                connection.service.dump(fd, null /* args */);
                logd("Done with " + servicePkgName);
            } catch (RemoteException e) {
                logd("RemoteException from " + servicePkgName, e);
                indentPW.increaseIndent();
                indentPW.println("RemoteException");
                indentPW.increaseIndent();
                e.printStackTrace(indentPW);
                indentPW.decreaseIndent();
                indentPW.decreaseIndent();
                // We won't retry this package again because now it's in dumpedPkgNames.
            }
            indentPW.println("");
        }
        if (dumpedPkgNames.isEmpty()) {
            indentPW.increaseIndent();
            indentPW.println("Not bound");
            indentPW.decreaseIndent();
            indentPW.println("");
        }
        indentPW.decreaseIndent();
    }

    private boolean hasCarrierPrivileges(@NonNull String pkgName, int phoneId) {
        int subId = SubscriptionManager.getSubscriptionId(phoneId);
        if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            return false;
        }
        return TelephonyManager.from(mContext).createForSubscriptionId(subId)
                .checkCarrierPrivilegesForPackage(pkgName)
                == TelephonyManager.CARRIER_PRIVILEGE_STATUS_HAS_ACCESS;
    }

    /**
     * Get the current calling package name.
     * @return the current calling package name
     */
    @Nullable
    private String getCurrentPackageName() {
        if (mPackageManager == null) return null;
        String[] callingUids = mPackageManager.getPackagesForUid(Binder.getCallingUid());
        return (callingUids == null) ? null : callingUids[0];
    }

    /**
     * Make sure the device has required telephony feature
     *
     * @throws UnsupportedOperationException if the device does not have required telephony feature
     */
    private void enforceTelephonyFeatureWithException(@Nullable String callingPackage,
            @NonNull String methodName) {
        if (callingPackage == null || mPackageManager == null) {
            return;
        }

        if (!mFeatureFlags.enforceTelephonyFeatureMappingForPublicApis()
                || !CompatChanges.isChangeEnabled(ENABLE_FEATURE_MAPPING, callingPackage,
                Binder.getCallingUserHandle())) {
            return;
        }

        if (!mPackageManager.hasSystemFeature(FEATURE_TELEPHONY_SUBSCRIPTION)) {
            throw new UnsupportedOperationException(
                    methodName + " is unsupported without " + FEATURE_TELEPHONY_SUBSCRIPTION);
        }
    }

    private class CarrierServiceConnection implements ServiceConnection {
        final int phoneId;
        @NonNull final String pkgName;
        final int eventId;
        IBinder service;

        CarrierServiceConnection(int phoneId, @NonNull String pkgName, int eventId) {
            this.phoneId = phoneId;
            this.pkgName = pkgName;
            this.eventId = eventId;
        }

        @Override
        public void onServiceConnected(@NonNull ComponentName name, @NonNull IBinder service) {
            logd("Connected to config app: " + name.flattenToShortString());
            this.service = service;
            mHandler.sendMessage(mHandler.obtainMessage(eventId, phoneId, -1, this));
        }

        @Override
        public void onServiceDisconnected(@NonNull ComponentName name) {
            logd("Disconnected from config app: " + name.flattenToShortString());
            this.service = null;
        }

        @Override
        public void onBindingDied(@NonNull ComponentName name) {
            logd("Binding died from config app: " + name.flattenToShortString());
            this.service = null;
        }

        @Override
        public void onNullBinding(@NonNull ComponentName name) {
            logd("Null binding from config app: " + name.flattenToShortString());
            this.service = null;
        }
    }

    private class ConfigLoaderBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(@NonNull Context context, @NonNull Intent intent) {
            switch (intent.getAction()) {
                case Intent.ACTION_BOOT_COMPLETED:
                    mHandler.sendMessage(mHandler.obtainMessage(EVENT_SYSTEM_UNLOCKED, null));
                    break;
            }
        }
    }

    private class CarrierServiceChangeCallback implements
            TelephonyManager.CarrierPrivilegesCallback {
        final int mPhoneId;
        // CarrierPrivilegesCallback will be triggered upon registration. Filter the first callback
        // here since we really care of the *change* of carrier service instead of the content
        private boolean mHasSentServiceChangeCallback;

        CarrierServiceChangeCallback(int phoneId) {
            this.mPhoneId = phoneId;
            this.mHasSentServiceChangeCallback = false;
        }

        @Override
        public void onCarrierPrivilegesChanged(
                @androidx.annotation.NonNull Set<String> privilegedPackageNames,
                @androidx.annotation.NonNull Set<Integer> privilegedUids) {
            // Ignored, not interested here
        }

        @Override
        public void onCarrierServiceChanged(
                @androidx.annotation.Nullable String carrierServicePackageName,
                int carrierServiceUid) {
            // Ignore the first callback which is triggered upon registration
            if (!mHasSentServiceChangeCallback) {
                mHasSentServiceChangeCallback = true;
                return;
            }
            mHandler.sendMessage(
                    mHandler.obtainMessage(EVENT_PACKAGE_CHANGED, mPhoneId, -1,
                            carrierServicePackageName));
        }
    }

    // Get readable string for the message code supported in this class.
    @NonNull
    private static String eventToString(int code) {
        switch (code) {
            case EVENT_CLEAR_CONFIG:
                return "EVENT_CLEAR_CONFIG";
            case EVENT_CONNECTED_TO_DEFAULT:
                return "EVENT_CONNECTED_TO_DEFAULT";
            case EVENT_CONNECTED_TO_CARRIER:
                return "EVENT_CONNECTED_TO_CARRIER";
            case EVENT_FETCH_DEFAULT_DONE:
                return "EVENT_FETCH_DEFAULT_DONE";
            case EVENT_FETCH_CARRIER_DONE:
                return "EVENT_FETCH_CARRIER_DONE";
            case EVENT_DO_FETCH_DEFAULT:
                return "EVENT_DO_FETCH_DEFAULT";
            case EVENT_DO_FETCH_CARRIER:
                return "EVENT_DO_FETCH_CARRIER";
            case EVENT_PACKAGE_CHANGED:
                return "EVENT_PACKAGE_CHANGED";
            case EVENT_BIND_DEFAULT_TIMEOUT:
                return "EVENT_BIND_DEFAULT_TIMEOUT";
            case EVENT_BIND_CARRIER_TIMEOUT:
                return "EVENT_BIND_CARRIER_TIMEOUT";
            case EVENT_CHECK_SYSTEM_UPDATE:
                return "EVENT_CHECK_SYSTEM_UPDATE";
            case EVENT_SYSTEM_UNLOCKED:
                return "EVENT_SYSTEM_UNLOCKED";
            case EVENT_FETCH_DEFAULT_TIMEOUT:
                return "EVENT_FETCH_DEFAULT_TIMEOUT";
            case EVENT_FETCH_CARRIER_TIMEOUT:
                return "EVENT_FETCH_CARRIER_TIMEOUT";
            case EVENT_SUBSCRIPTION_INFO_UPDATED:
                return "EVENT_SUBSCRIPTION_INFO_UPDATED";
            case EVENT_MULTI_SIM_CONFIG_CHANGED:
                return "EVENT_MULTI_SIM_CONFIG_CHANGED";
            case EVENT_DO_FETCH_DEFAULT_FOR_NO_SIM_CONFIG:
                return "EVENT_DO_FETCH_DEFAULT_FOR_NO_SIM_CONFIG";
            case EVENT_FETCH_DEFAULT_FOR_NO_SIM_CONFIG_DONE:
                return "EVENT_FETCH_DEFAULT_FOR_NO_SIM_CONFIG_DONE";
            case EVENT_CONNECTED_TO_DEFAULT_FOR_NO_SIM_CONFIG:
                return "EVENT_CONNECTED_TO_DEFAULT_FOR_NO_SIM_CONFIG";
            case EVENT_BIND_DEFAULT_FOR_NO_SIM_CONFIG_TIMEOUT:
                return "EVENT_BIND_DEFAULT_FOR_NO_SIM_CONFIG_TIMEOUT";
            case EVENT_FETCH_DEFAULT_FOR_NO_SIM_CONFIG_TIMEOUT:
                return "EVENT_FETCH_DEFAULT_FOR_NO_SIM_CONFIG_TIMEOUT";
            default:
                return "UNKNOWN(" + code + ")";
        }
    }

    private void logd(@NonNull String msg) {
        Log.d(LOG_TAG, msg);
    }

    private void logd(@NonNull String msg, Throwable tr) {
        Log.d(LOG_TAG, msg, tr);
    }

    private void logdWithLocalLog(@NonNull String msg) {
        Log.d(LOG_TAG, msg);
        mCarrierConfigLoadingLog.log(msg);
    }

    private void loge(@NonNull String msg) {
        Log.e(LOG_TAG, msg);
        mCarrierConfigLoadingLog.log(msg);
    }
}
