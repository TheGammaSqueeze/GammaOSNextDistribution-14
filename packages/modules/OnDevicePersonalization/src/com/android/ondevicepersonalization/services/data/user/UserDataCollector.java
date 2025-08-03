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

package com.android.ondevicepersonalization.services.data.user;

import static android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.BatteryManager;
import android.os.Environment;
import android.os.StatFs;
import android.telephony.TelephonyManager;

import androidx.annotation.NonNull;

import com.android.internal.annotations.VisibleForTesting;
import com.android.ondevicepersonalization.internal.util.LoggerFactory;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

/**
 * A collector for getting user data signals.
 * This class only exposes two public operations: periodic update, and
 * real-time update.
 * Periodic update operation will be run every 4 hours in the background,
 * given several on-device resource constraints are satisfied.
 * Real-time update operation will be run before any ads serving request
 * and update a few time-sensitive signals in UserData to the latest version.
 */
public class UserDataCollector {
    private static final int MILLISECONDS_IN_MINUTE = 60000;

    private static volatile UserDataCollector sUserDataCollector = null;
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = "UserDataCollector";

    @VisibleForTesting
    public static final Set<Integer> ALLOWED_NETWORK_TYPE =
            Set.of(
                    TelephonyManager.NETWORK_TYPE_UNKNOWN,
                    TelephonyManager.NETWORK_TYPE_GPRS,
                    TelephonyManager.NETWORK_TYPE_EDGE,
                    TelephonyManager.NETWORK_TYPE_UMTS,
                    TelephonyManager.NETWORK_TYPE_CDMA,
                    TelephonyManager.NETWORK_TYPE_EVDO_0,
                    TelephonyManager.NETWORK_TYPE_EVDO_A,
                    TelephonyManager.NETWORK_TYPE_1xRTT,
                    TelephonyManager.NETWORK_TYPE_HSDPA,
                    TelephonyManager.NETWORK_TYPE_HSUPA,
                    TelephonyManager.NETWORK_TYPE_HSPA,
                    TelephonyManager.NETWORK_TYPE_EVDO_B,
                    TelephonyManager.NETWORK_TYPE_LTE,
                    TelephonyManager.NETWORK_TYPE_EHRPD,
                    TelephonyManager.NETWORK_TYPE_HSPAP,
                    TelephonyManager.NETWORK_TYPE_GSM,
                    TelephonyManager.NETWORK_TYPE_TD_SCDMA,
                    TelephonyManager.NETWORK_TYPE_IWLAN,
                    TelephonyManager.NETWORK_TYPE_NR
            );

    @NonNull
    private final Context mContext;
    @NonNull
    private final TelephonyManager mTelephonyManager;
    @NonNull final ConnectivityManager mConnectivityManager;
    // Metadata to track whether UserData has been initialized.
    @NonNull
    private boolean mInitialized;

    private UserDataCollector(Context context) {
        mContext = context;

        mTelephonyManager = mContext.getSystemService(TelephonyManager.class);
        mConnectivityManager = mContext.getSystemService(ConnectivityManager.class);
        mInitialized = false;
    }

    /** Returns an instance of UserDataCollector. */
    public static UserDataCollector getInstance(Context context) {
        if (sUserDataCollector == null) {
            synchronized (UserDataCollector.class) {
                if (sUserDataCollector == null) {
                    sUserDataCollector = new UserDataCollector(
                            context.getApplicationContext());
                }
            }
        }
        return sUserDataCollector;
    }

    /**
     * Returns an instance of the UserDataCollector given a context. This is used
     * for testing only.
     */
    @VisibleForTesting
    public static UserDataCollector getInstanceForTest(Context context) {
        synchronized (UserDataCollector.class) {
            if (sUserDataCollector == null) {
                sUserDataCollector = new UserDataCollector(context);
            }
            return sUserDataCollector;
        }
    }

    /** Update real-time user data to the latest per request. */
    public void getRealTimeData(@NonNull RawUserData userData) {
        /**
         * Ads serving requires real-time latency. If user data has not been initialized,
         * we will skip user data collection for the incoming request and wait until the first
         * {@link UserDataCollectionJobService} to be scheduled.
         */
        if (!mInitialized) {
            return;
        }
        getUtcOffset(userData);
        getOrientation(userData);
    }

    /** Update user data per periodic job servce. */
    public void updateUserData(@NonNull RawUserData userData) {
        if (!mInitialized) {
            initializeUserData(userData);
            return;
        }
        getAvailableStorageBytes(userData);
        getBatteryPercentage(userData);
        getCarrier(userData);
        getNetworkCapabilities(userData);
        getDataNetworkType(userData);

        getInstalledApps(userData.appsInfo);
    }

    /**
     * Collects in-memory user data signals and stores in a UserData object
     * for the schedule of {@link UserDataCollectionJobService}
     */
    private void initializeUserData(@NonNull RawUserData userData) {
        getUtcOffset(userData);
        getOrientation(userData);
        getAvailableStorageBytes(userData);
        getBatteryPercentage(userData);
        getCarrier(userData);
        getNetworkCapabilities(userData);
        getDataNetworkType(userData);

        getInstalledApps(userData.appsInfo);

        mInitialized = true;
    }

    /** Collects current device's time zone in +/- offset of minutes from UTC. */
    @VisibleForTesting
    public void getUtcOffset(RawUserData userData) {
        try {
            userData.utcOffset = TimeZone.getDefault().getOffset(System.currentTimeMillis())
                    / MILLISECONDS_IN_MINUTE;
        } catch (Exception e) {
            sLogger.w(TAG + ": Failed to collect timezone offset.");
        }
    }

    /** Collects the current device orientation. */
    @VisibleForTesting
    public void getOrientation(RawUserData userData) {
        try {
            userData.orientation = mContext.getResources().getConfiguration().orientation;
        } catch (Exception e) {
            sLogger.w(TAG + ": Failed to collect device orientation.");
        }
    }

    /** Collects available bytes and converts to MB. */
    @VisibleForTesting
    public void getAvailableStorageBytes(RawUserData userData) {
        try {
            StatFs statFs = new StatFs(Environment.getDataDirectory().getPath());
            userData.availableStorageBytes = statFs.getAvailableBytes();
        } catch (Exception e) {
            sLogger.w(TAG + ": Failed to collect availableStorageBytes.");
        }
    }

    /** Collects the battery percentage of the device. */
    @VisibleForTesting
    public void getBatteryPercentage(RawUserData userData) {
        try {
            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = mContext.registerReceiver(null, ifilter);

            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            if (level >= 0 && scale > 0) {
                userData.batteryPercentage = Math.round(level * 100.0f / (float) scale);
            }
        } catch (Exception e) {
            sLogger.w(TAG + ": Failed to collect batteryPercentage.");
        }
    }

    /** Collects carrier info. */
    @VisibleForTesting
    public void getCarrier(RawUserData userData) {
        // TODO (b/307158231): handle i18n later if the carrier's name is in non-English script.
        try {
            switch (mTelephonyManager.getSimOperatorName().toUpperCase(Locale.US)) {
                case "RELIANCE JIO" -> userData.carrier = Carrier.RELIANCE_JIO;
                case "VODAFONE" -> userData.carrier = Carrier.VODAFONE;
                case "T-MOBILE - US", "T-MOBILE" -> userData.carrier = Carrier.T_MOBILE;
                case "VERIZON WIRELESS" -> userData.carrier = Carrier.VERIZON_WIRELESS;
                case "AIRTEL" -> userData.carrier = Carrier.AIRTEL;
                case "ORANGE" -> userData.carrier = Carrier.ORANGE;
                case "NTT DOCOMO" -> userData.carrier = Carrier.NTT_DOCOMO;
                case "MOVISTAR" -> userData.carrier = Carrier.MOVISTAR;
                case "AT&T" -> userData.carrier = Carrier.AT_T;
                case "TELCEL" -> userData.carrier = Carrier.TELCEL;
                case "VIVO" -> userData.carrier = Carrier.VIVO;
                case "VI" -> userData.carrier = Carrier.VI;
                case "TIM" -> userData.carrier = Carrier.TIM;
                case "O2" -> userData.carrier = Carrier.O2;
                case "TELEKOM" -> userData.carrier = Carrier.TELEKOM;
                case "CLARO BR" -> userData.carrier = Carrier.CLARO_BR;
                case "SK TELECOM" -> userData.carrier = Carrier.SK_TELECOM;
                case "MTC" -> userData.carrier = Carrier.MTC;
                case "AU" -> userData.carrier = Carrier.AU;
                case "TELE2" -> userData.carrier = Carrier.TELE2;
                case "SFR" -> userData.carrier = Carrier.SFR;
                case "ETECSA" -> userData.carrier = Carrier.ETECSA;
                case "IR-MCI (HAMRAHE AVVAL)" -> userData.carrier = Carrier.IR_MCI;
                case "KT" -> userData.carrier = Carrier.KT;
                case "TELKOMSEL" -> userData.carrier = Carrier.TELKOMSEL;
                case "IRANCELL" -> userData.carrier = Carrier.IRANCELL;
                case "MEGAFON" -> userData.carrier = Carrier.MEGAFON;
                case "TELEFONICA" -> userData.carrier = Carrier.TELEFONICA;
                default -> userData.carrier = Carrier.UNKNOWN;
            }
        } catch (Exception e) {
            sLogger.w(TAG + "Failed to collect carrier info.");
        }
    }

    /** Collects network capabilities. */
    @VisibleForTesting
    public void getNetworkCapabilities(RawUserData userData) {
        try {
            NetworkCapabilities networkCapabilities = mConnectivityManager.getNetworkCapabilities(
                    mConnectivityManager.getActiveNetwork());
            userData.networkCapabilities = getFilteredNetworkCapabilities(networkCapabilities);
        } catch (Exception e) {
            sLogger.w(TAG + ": Failed to collect networkCapabilities.");
        }
    }

    @VisibleForTesting
    public void getDataNetworkType(RawUserData userData) {
        try {
            int dataNetworkType = mTelephonyManager.getDataNetworkType();
            if (!ALLOWED_NETWORK_TYPE.contains(dataNetworkType)) {
                userData.dataNetworkType = TelephonyManager.NETWORK_TYPE_UNKNOWN;
            } else {
                userData.dataNetworkType = dataNetworkType;
            }
        } catch (Exception e) {
            sLogger.w(TAG + ": Failed to collect data network type.");
        }
    }

    /** Get app install and uninstall record. */
    @VisibleForTesting
    public void getInstalledApps(@NonNull List<AppInfo> appsInfo) {
        try {
            appsInfo.clear();
            PackageManager packageManager = mContext.getPackageManager();
            for (ApplicationInfo appInfo :
                    packageManager.getInstalledApplications(MATCH_UNINSTALLED_PACKAGES)) {
                AppInfo app = new AppInfo();
                app.packageName = appInfo.packageName;
                if ((appInfo.flags & ApplicationInfo.FLAG_INSTALLED) != 0) {
                    app.installed = true;
                } else {
                    app.installed = false;
                }
                appsInfo.add(app);
            }
            sLogger.d(TAG + ": Finished collecting AppInfo.");
        } catch (Exception e) {
            sLogger.w(TAG + ": Failed to collect installed AppInfo.");
        }
    }

    /**
     * Util to reset all fields in [UserData] to default for testing purpose
     */
    public void clearUserData(@NonNull RawUserData userData) {
        userData.utcOffset = 0;
        userData.orientation = Configuration.ORIENTATION_PORTRAIT;
        userData.availableStorageBytes = 0;
        userData.batteryPercentage = 0;
        userData.carrier = Carrier.UNKNOWN;
        userData.networkCapabilities = null;
        userData.appsInfo.clear();
    }

    /**
     * Util to reset all in-memory metadata for testing purpose.
     */
    public void clearMetadata() {
        mInitialized = false;
    }

    @VisibleForTesting
    public boolean isInitialized() {
        return mInitialized;
    }

    @VisibleForTesting
    static NetworkCapabilities getFilteredNetworkCapabilities(
            NetworkCapabilities networkCapabilities) {
        NetworkCapabilities.Builder builder =
                NetworkCapabilities.Builder.withoutDefaultCapabilities()
                    .setLinkDownstreamBandwidthKbps(
                            networkCapabilities.getLinkDownstreamBandwidthKbps())
                    .setLinkUpstreamBandwidthKbps(
                            networkCapabilities.getLinkUpstreamBandwidthKbps());
        if (networkCapabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) {
            builder.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
        }
        return builder.build();
    }
}
