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

package com.android.server.wifi;

import android.app.AlarmManager;
import android.content.Context;
import android.net.NetworkCapabilities;
import android.net.NetworkFactory;
import android.net.NetworkRequest;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.Looper;
import android.os.WorkSource;
import android.util.LocalLog;
import android.util.Log;
import android.util.SparseArray;

import com.android.server.wifi.util.WifiPermissionsUtil;

import java.io.FileDescriptor;
import java.io.PrintWriter;

/**
 * Network factory to handle multi internet wifi network requests.
 */
public class MultiInternetWifiNetworkFactory extends NetworkFactory {
    private static final String TAG = "MultiInternetWifiNetworkFactory";
    private static final int SCORE_FILTER = Integer.MAX_VALUE;

    private final WifiConnectivityManager mWifiConnectivityManager;
    private final MultiInternetManager mMultiInternetManager;
    private final WifiPermissionsUtil mWifiPermissionsUtil;
    private final FrameworkFacade mFacade;
    private final Context mContext;
    private final AlarmManager mAlarmManager;
    private final LocalLog mLocalLog;

    // Verbose logging flag.
    private boolean mVerboseLoggingEnabled = false;
    // Connection request state per each band.
    private SparseArray<NetworkRequestState> mNetworkRequestStates = new SparseArray<>();
    // Connection request count per each band.
    private SparseArray<Integer> mConnectionReqCount = new SparseArray<>();

    // A helper to log debugging information in the local log buffer, which can
    // be retrieved in bugreport.
    private void localLog(String log) {
        mLocalLog.log(log);
        if (mVerboseLoggingEnabled) Log.v(TAG, log);
    }

    /**
     * Internal network request state for multi internet networks
     */
    public static class NetworkRequestState {
        public final NetworkRequest networkRequest;
        public final WifiNetworkSpecifier networkRequestSpecifier;
        public final boolean isFromSetting;
        public final boolean isFromForegroundApp;
        public final boolean isFromForegroundAppOrService;

        NetworkRequestState(NetworkRequest request,
                WifiNetworkSpecifier specifier,
                boolean setting,
                boolean foregroundApp,
                boolean foregroundAppOrService) {
            networkRequest = request;
            networkRequestSpecifier = specifier;
            isFromSetting = setting;
            isFromForegroundApp = foregroundApp;
            isFromForegroundAppOrService = foregroundAppOrService;
        }
    }

    /**
     * Check if the network request is for multi internet Wifi network.
     * @param networkRequest the network requested by connectivity service
     * @return true if the request if for multi internet Wifi network, false if not.
     */
    public static boolean isWifiMultiInternetRequest(NetworkRequest networkRequest,
            boolean isFromSettings) {
        if (networkRequest.getNetworkSpecifier() == null
                || !(networkRequest.getNetworkSpecifier() instanceof WifiNetworkSpecifier)) {
            return false;
        }
        WifiNetworkSpecifier wns = (WifiNetworkSpecifier) networkRequest.getNetworkSpecifier();
        // Multi internet request must have internet capability, with specifier of band request.
        // It must not have SSID/BSSID pattern matcher - except a request from Settings which can
        // specify a BSSID (while an SSID specification is allowed here, it is dropped later on).
        if (networkRequest.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && wns.getBand() != ScanResult.UNSPECIFIED
                && (isFromSettings || WifiConfigurationUtil.isMatchAllNetworkSpecifier(wns))) {
            return true;
        }
        return false;
    }

    public MultiInternetWifiNetworkFactory(Looper looper, Context context, NetworkCapabilities nc,
            FrameworkFacade facade, AlarmManager alarmManager,
            WifiPermissionsUtil wifiPermissionsUtil,
            MultiInternetManager multiInternetManager,
            WifiConnectivityManager connectivityManager,
            LocalLog localLog) {
        super(looper, context, TAG, nc);
        mContext = context;
        mFacade = facade;
        mAlarmManager = alarmManager;
        mWifiPermissionsUtil = wifiPermissionsUtil;
        mMultiInternetManager = multiInternetManager;
        mWifiConnectivityManager = connectivityManager;
        mLocalLog = localLog;
        setScoreFilter(SCORE_FILTER);
    }

    /**
     * Enable verbose logging.
     */
    public void enableVerboseLogging(boolean verbose) {
        mVerboseLoggingEnabled = verbose;
    }

    /**
     * Check whether to accept the new network connection request. Validate the incoming request
     * and return true if valid.
     */
    @Override
    public boolean acceptRequest(NetworkRequest networkRequest) {
        final int uid = networkRequest.getRequestorUid();
        boolean isFromSetting = mWifiPermissionsUtil.checkNetworkSettingsPermission(uid)
                || mWifiPermissionsUtil.checkNetworkSetupWizardPermission(uid);
        if (!mMultiInternetManager.isStaConcurrencyForMultiInternetEnabled()
                || !isWifiMultiInternetRequest(networkRequest, isFromSetting)) {
            return false;
        }
        boolean isFromNetworkStack = mWifiPermissionsUtil.checkNetworkStackPermission(uid)
                || mWifiPermissionsUtil.checkMainlineNetworkStackPermission(uid);
        // Only allow specific wifi network request with band from apps or services with settings
        // or network stack permission.
        if (!isFromSetting && !isFromNetworkStack) {
            // Do not release the network request. The app will not get onUnavailable right away,
            // it can wait when another app with permission make the request and obtain the network.
            Log.w(TAG, "Request is from app or service does not have the permission."
                    + " Rejecting request from " + networkRequest.getRequestorPackageName());
            return false;
        }
        WifiNetworkSpecifier wns = (WifiNetworkSpecifier) networkRequest.getNetworkSpecifier();
        final int band = wns.getBand();
        // TODO: b/181741503 Check if the band is supported.
        localLog("Accepted network request with specifier for band " + band);
        return true;
    }

    @Override
    protected void needNetworkFor(NetworkRequest networkRequest) {
        boolean isFromSetting = mWifiPermissionsUtil.checkNetworkSettingsPermission(
                networkRequest.getRequestorUid());
        if (!mMultiInternetManager.isStaConcurrencyForMultiInternetEnabled()
                || !isWifiMultiInternetRequest(networkRequest, isFromSetting)) {
            return;
        }
        WifiNetworkSpecifier wns = (WifiNetworkSpecifier) networkRequest.getNetworkSpecifier();
        final int band = wns.getBand();
        boolean isFromForegroundApp = mFacade.isRequestFromForegroundApp(mContext,
                networkRequest.getRequestorPackageName());
        boolean isFromForegroundAppOrService =
                mFacade.isRequestFromForegroundAppOrService(mContext,
                        networkRequest.getRequestorPackageName());
        NetworkRequestState nrs = new NetworkRequestState(networkRequest,
                new WifiNetworkSpecifier(
                wns.ssidPatternMatcher, wns.bssidPatternMatcher, wns.getBand(),
                wns.wifiConfiguration, wns.getPreferredChannelFrequenciesMhz()),
                isFromSetting,
                isFromForegroundApp,
                isFromForegroundAppOrService);
        mNetworkRequestStates.put(band, nrs);
        // If multi internet is requested, without specifying SSID or BSSID,
        // The WifiConnectivityManager will perform network selection to choose a candidate.
        // Create a worksource using the caller's UID.
        WorkSource workSource = new WorkSource(networkRequest.getRequestorUid());
        int reqCount = 0;
        if (mConnectionReqCount.contains(band)) {
            reqCount = mConnectionReqCount.get(band);
        }
        if (reqCount == 0 || isFromSetting) {
            localLog("Need network : Uid " + networkRequest.getRequestorUid() + " PackageName "
                    + networkRequest.getRequestorPackageName() + " for band " + band
                    + " is rom Setting " + isFromSetting + " ForegroundApp " + isFromForegroundApp
                    + " ForegroundAppOrService " + isFromForegroundApp);
            mMultiInternetManager.setMultiInternetConnectionWorksource(
                    band, wns.wifiConfiguration.BSSID,
                    new WorkSource(networkRequest.getRequestorUid(),
                    networkRequest.getRequestorPackageName()));
        }
        mConnectionReqCount.put(band, reqCount + 1);
    }

    @Override
    protected void releaseNetworkFor(NetworkRequest networkRequest) {
        boolean isFromSetting = mWifiPermissionsUtil.checkNetworkSettingsPermission(
                networkRequest.getRequestorUid());
        if (!isWifiMultiInternetRequest(networkRequest, isFromSetting)) {
            return;
        }
        localLog("releaseNetworkFor " + networkRequest);
        final int band = ((WifiNetworkSpecifier) networkRequest.getNetworkSpecifier()).getBand();
        int reqCount = mConnectionReqCount.contains(band)
                ? mConnectionReqCount.get(band) : 0;
        if (reqCount == 0) {
            Log.e(TAG, "No valid network request to release");
            return;
        }
        if (reqCount == 1) {
            mMultiInternetManager.setMultiInternetConnectionWorksource(band, null, null);
        }
        mConnectionReqCount.put(band, reqCount - 1);
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        super.dump(fd, pw, args);
        pw.println("Dump of MultiInternetWifiNetworkFactory");
        for (int i = 0; i < mConnectionReqCount.size(); i++) {
            final int band = mConnectionReqCount.keyAt(i);
            NetworkRequestState state = mNetworkRequestStates.get(band);
            pw.println("    Band " + band + " Req count " + mConnectionReqCount.valueAt(i)
                    + " isFromSetting " + state.isFromSetting
                    + " isFromForegroundApp " + state.isFromForegroundApp
                    + " isFromForegroundAppOrService " + state.isFromForegroundAppOrService
                    + " Uid " + state.networkRequest.getRequestorUid()
                    + " PackageName " + state.networkRequest.getRequestorPackageName());
        }
        mLocalLog.dump(fd, pw, args);
        mMultiInternetManager.dump(fd, pw, args);
    }
}
