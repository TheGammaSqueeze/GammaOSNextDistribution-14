/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.server.connectivity;

import static android.net.ConnectivitySettingsManager.NETWORK_AVOID_BAD_WIFI;
import static android.net.ConnectivitySettingsManager.NETWORK_METERED_MULTIPATH_PREFERENCE;

import android.annotation.NonNull;
import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.provider.DeviceConfig;
import android.provider.Settings;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.connectivity.resources.R;
import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.build.SdkLevel;
import com.android.net.module.util.DeviceConfigUtils;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * A class to encapsulate management of the "Smart Networking" capability of
 * avoiding bad Wi-Fi when, for example upstream connectivity is lost or
 * certain critical link failures occur.
 *
 * This enables the device to switch to another form of connectivity, like
 * mobile, if it's available and working.
 *
 * The Runnable |avoidBadWifiCallback|, if given, is posted to the supplied
 * Handler' whenever the computed "avoid bad wifi" value changes.
 *
 * Disabling this reverts the device to a level of networking sophistication
 * circa 2012-13 by disabling disparate code paths each of which contribute to
 * maintaining continuous, working Internet connectivity.
 *
 * @hide
 */
public class MultinetworkPolicyTracker {
    private static String TAG = MultinetworkPolicyTracker.class.getSimpleName();

    // See Dependencies#getConfigActivelyPreferBadWifi
    public static final String CONFIG_ACTIVELY_PREFER_BAD_WIFI = "actively_prefer_bad_wifi";

    private final Context mContext;
    private final ConnectivityResources mResources;
    private final Handler mHandler;
    private final Runnable mAvoidBadWifiCallback;
    private final List<Uri> mSettingsUris;
    private final ContentResolver mResolver;
    private final SettingObserver mSettingObserver;
    private final BroadcastReceiver mBroadcastReceiver;

    private volatile boolean mAvoidBadWifi = true;
    private volatile int mMeteredMultipathPreference;
    private int mActiveSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    private volatile long mTestAllowBadWifiUntilMs = 0;

    /**
     * Dependencies for testing
     */
    @VisibleForTesting
    public static class Dependencies {
        /**
         * @see DeviceConfigUtils#getDeviceConfigPropertyInt
         */
        protected int getConfigActivelyPreferBadWifi() {
            // CONFIG_ACTIVELY_PREFER_BAD_WIFI is not a feature to be rolled out, but an override
            // for tests and an emergency kill switch (which could force the behavior on OR off).
            // As such it uses a -1/null/1 scheme, but features should use
            // DeviceConfigUtils#isFeatureEnabled instead, to make sure rollbacks disable the
            // feature before it's ready on R and before.
            return DeviceConfig.getInt(DeviceConfig.NAMESPACE_CONNECTIVITY,
                    CONFIG_ACTIVELY_PREFER_BAD_WIFI, 0);
        }

        /**
         @see DeviceConfig#addOnPropertiesChangedListener
         */
        protected void addOnDevicePropertiesChangedListener(@NonNull final Executor executor,
                @NonNull final DeviceConfig.OnPropertiesChangedListener listener) {
            DeviceConfig.addOnPropertiesChangedListener(DeviceConfig.NAMESPACE_CONNECTIVITY,
                    executor, listener);
        }

        @VisibleForTesting
        @NonNull
        protected Resources getResourcesForActiveSubId(
                @NonNull final ConnectivityResources resources, final int activeSubId) {
            return SubscriptionManager.getResourcesForSubId(
                    resources.getResourcesContext(), activeSubId);
        }
    }
    private final Dependencies mDeps;

    /**
     * Whether to prefer bad wifi to a network that yields to bad wifis, even if it never validated
     *
     * This setting only makes sense if the system is configured not to avoid bad wifis, i.e.
     * if mAvoidBadWifi is true. If it's not, then no network ever yields to bad wifis
     * ({@see FullScore#POLICY_YIELD_TO_BAD_WIFI}) and this setting has therefore no effect.
     *
     * If this is false, when ranking a bad wifi that never validated against cell data (or any
     * network that yields to bad wifis), the ranker will prefer cell data. It will prefer wifi
     * if wifi loses validation later. This behavior avoids the device losing internet access when
     * walking past a wifi network with no internet access.
     * This is the default behavior up to Android T, but it can be overridden through an overlay
     * to behave like below.
     *
     * If this is true, then in the same scenario, the ranker will prefer cell data until
     * the wifi completes its first validation attempt (or the attempt times out after
     * ConnectivityService#PROMPT_UNVALIDATED_DELAY_MS), then it will prefer the wifi even if it
     * doesn't provide internet access, unless there is a captive portal on that wifi.
     * This is the behavior in U and above.
     */
    private boolean mActivelyPreferBadWifi;

    // Mainline module can't use internal HandlerExecutor, so add an identical executor here.
    private static class HandlerExecutor implements Executor {
        @NonNull
        private final Handler mHandler;

        HandlerExecutor(@NonNull Handler handler) {
            mHandler = handler;
        }
        @Override
        public void execute(Runnable command) {
            if (!mHandler.post(command)) {
                throw new RejectedExecutionException(mHandler + " is shutting down");
            }
        }
    }
    // TODO: Set the mini sdk to 31 and remove @TargetApi annotation when b/205923322 is addressed.
    @VisibleForTesting @TargetApi(Build.VERSION_CODES.S)
    protected class ActiveDataSubscriptionIdListener extends TelephonyCallback
            implements TelephonyCallback.ActiveDataSubscriptionIdListener {
        @Override
        public void onActiveDataSubscriptionIdChanged(int subId) {
            mActiveSubId = subId;
            reevaluateInternal();
        }
    }

    public MultinetworkPolicyTracker(Context ctx, Handler handler, Runnable avoidBadWifiCallback) {
        this(ctx, handler, avoidBadWifiCallback, new Dependencies());
    }

    public MultinetworkPolicyTracker(Context ctx, Handler handler, Runnable avoidBadWifiCallback,
            Dependencies deps) {
        mContext = ctx;
        mResources = new ConnectivityResources(ctx);
        mHandler = handler;
        mAvoidBadWifiCallback = avoidBadWifiCallback;
        mDeps = deps;
        mSettingsUris = Arrays.asList(
                Settings.Global.getUriFor(NETWORK_AVOID_BAD_WIFI),
                Settings.Global.getUriFor(NETWORK_METERED_MULTIPATH_PREFERENCE));
        mResolver = mContext.getContentResolver();
        mSettingObserver = new SettingObserver();
        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                reevaluateInternal();
            }
        };

        updateAvoidBadWifi();
        updateMeteredMultipathPreference();
    }

    // TODO: Set the mini sdk to 31 and remove @TargetApi annotation when b/205923322 is addressed.
    @TargetApi(Build.VERSION_CODES.S)
    public void start() {
        for (Uri uri : mSettingsUris) {
            mResolver.registerContentObserver(uri, false, mSettingObserver);
        }

        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
        mContext.registerReceiverForAllUsers(mBroadcastReceiver, intentFilter,
                null /* broadcastPermission */, mHandler);

        final Executor handlerExecutor = new HandlerExecutor(mHandler);
        mContext.getSystemService(TelephonyManager.class).registerTelephonyCallback(
                handlerExecutor, new ActiveDataSubscriptionIdListener());
        mDeps.addOnDevicePropertiesChangedListener(handlerExecutor,
                properties -> reevaluateInternal());

        reevaluate();
    }

    public void shutdown() {
        mResolver.unregisterContentObserver(mSettingObserver);

        mContext.unregisterReceiver(mBroadcastReceiver);
    }

    public boolean getAvoidBadWifi() {
        return mAvoidBadWifi;
    }

    public boolean getActivelyPreferBadWifi() {
        return mActivelyPreferBadWifi;
    }

    // TODO: move this to MultipathPolicyTracker.
    public int getMeteredMultipathPreference() {
        return mMeteredMultipathPreference;
    }

    /**
     * Whether the device or carrier configuration disables avoiding bad wifi by default.
     */
    public boolean configRestrictsAvoidBadWifi() {
        final boolean allowBadWifi = mTestAllowBadWifiUntilMs > 0
                && mTestAllowBadWifiUntilMs > System.currentTimeMillis();
        // If the config returns true, then avoid bad wifi design can be controlled by the
        // NETWORK_AVOID_BAD_WIFI setting.
        if (allowBadWifi) return true;

        return mDeps.getResourcesForActiveSubId(mResources, mActiveSubId)
                .getInteger(R.integer.config_networkAvoidBadWifi) == 0;
    }

    /**
     * Whether the device config prefers bad wifi actively, when it doesn't avoid them
     *
     * This is only relevant when the device is configured not to avoid bad wifis. In this
     * case, "actively" preferring a bad wifi means that the device will switch to a bad
     * wifi it just connected to, as long as it's not a captive portal.
     *
     * On U and above this always returns true. On T and below it reads a configuration option.
     */
    public boolean configActivelyPrefersBadWifi() {
        // See the definition of config_activelyPreferBadWifi for a description of its meaning.
        // On U and above, the config is ignored, and bad wifi is always actively preferred.
        if (SdkLevel.isAtLeastU()) return true;

        // On T and below, 1 means to actively prefer bad wifi, 0 means not to prefer
        // bad wifi (only stay stuck on it if already on there). This implementation treats
        // any non-0 value like 1, on the assumption that anybody setting it non-zero wants
        // the newer behavior.
        return 0 != mDeps.getResourcesForActiveSubId(mResources, mActiveSubId)
                .getInteger(R.integer.config_activelyPreferBadWifi);
    }

    /**
     * Temporarily allow bad wifi to override {@code config_networkAvoidBadWifi} configuration.
     * The value works when the time set is more than {@link System.currentTimeMillis()}.
     */
    public void setTestAllowBadWifiUntil(long timeMs) {
        Log.d(TAG, "setTestAllowBadWifiUntil: " + timeMs);
        mTestAllowBadWifiUntilMs = timeMs;
        reevaluateInternal();
    }

    /**
     * Whether we should display a notification when wifi becomes unvalidated.
     */
    public boolean shouldNotifyWifiUnvalidated() {
        return configRestrictsAvoidBadWifi() && getAvoidBadWifiSetting() == null;
    }

    public String getAvoidBadWifiSetting() {
        return Settings.Global.getString(mResolver, NETWORK_AVOID_BAD_WIFI);
    }

    /**
     * Returns whether device config says the device should actively prefer bad wifi.
     *
     * {@see #configActivelyPrefersBadWifi} for a description of what this does. This device
     * config overrides that config overlay.
     *
     * @return True on Android U and above.
     *         True if device config says to actively prefer bad wifi.
     *         False if device config says not to actively prefer bad wifi.
     *         null if device config doesn't have an opinion (then fall back on the resource).
     */
    public Boolean deviceConfigActivelyPreferBadWifi() {
        if (SdkLevel.isAtLeastU()) return true;
        switch (mDeps.getConfigActivelyPreferBadWifi()) {
            case 1:
                return Boolean.TRUE;
            case -1:
                return Boolean.FALSE;
            default:
                return null;
        }
    }

    @VisibleForTesting
    public void reevaluate() {
        mHandler.post(this::reevaluateInternal);
    }

    /**
     * Reevaluate the settings. Must be called on the handler thread.
     */
    private void reevaluateInternal() {
        if (updateAvoidBadWifi() && mAvoidBadWifiCallback != null) {
            mAvoidBadWifiCallback.run();
        }
        updateMeteredMultipathPreference();
    }

    public boolean updateAvoidBadWifi() {
        final boolean settingAvoidBadWifi = "1".equals(getAvoidBadWifiSetting());
        final boolean prevAvoid = mAvoidBadWifi;
        mAvoidBadWifi = settingAvoidBadWifi || !configRestrictsAvoidBadWifi();

        final boolean prevActive = mActivelyPreferBadWifi;
        final Boolean deviceConfigPreferBadWifi = deviceConfigActivelyPreferBadWifi();
        if (null == deviceConfigPreferBadWifi) {
            mActivelyPreferBadWifi = configActivelyPrefersBadWifi();
        } else {
            mActivelyPreferBadWifi = deviceConfigPreferBadWifi;
        }

        return mAvoidBadWifi != prevAvoid || mActivelyPreferBadWifi != prevActive;
    }

    /**
     * The default (device and carrier-dependent) value for metered multipath preference.
     */
    public int configMeteredMultipathPreference() {
        return mDeps.getResourcesForActiveSubId(mResources, mActiveSubId)
                .getInteger(R.integer.config_networkMeteredMultipathPreference);
    }

    public void updateMeteredMultipathPreference() {
        String setting = Settings.Global.getString(mResolver, NETWORK_METERED_MULTIPATH_PREFERENCE);
        try {
            mMeteredMultipathPreference = Integer.parseInt(setting);
        } catch (NumberFormatException e) {
            mMeteredMultipathPreference = configMeteredMultipathPreference();
        }
    }

    private class SettingObserver extends ContentObserver {
        public SettingObserver() {
            super(null);
        }

        @Override
        public void onChange(boolean selfChange) {
            Log.wtf(TAG, "Should never be reached.");
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            if (!mSettingsUris.contains(uri)) {
                Log.wtf(TAG, "Unexpected settings observation: " + uri);
            }
            reevaluate();
        }
    }
}
