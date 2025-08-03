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

import static com.android.telephony.qns.QnsConstants.INVALID_ID;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.telephony.AccessNetworkConstants;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * This class provides support for the RTT verification for Wifi. It schedules the RTT verification
 * based on the UE state(Wifi connected, Cellular available, IMS registered on WLAN, operator
 * support for RTT, etc).
 */
class WifiBackhaulMonitor {
    private static final int EVENT_START_RTT_CHECK = 1;
    private static final int EVENT_IMS_REGISTRATION_STATE_CHANGED = 2;
    private final String mTag;
    private final ConnectivityManager mConnectivityManager;
    private final QnsImsManager mQnsImsManager;
    private final ConnectivityManager.NetworkCallback mNetworkCallback;
    private final Context mContext;
    private final int mSlotIndex;

    private final QnsRegistrantList mRegistrantList;
    private final HandlerThread mHandlerThread;
    private final Handler mHandler;
    private final QnsCarrierConfigManager mConfigManager;
    private final QnsTimer mQnsTimer;
    private boolean mRttResult = false;

    ArrayList<InetAddress> mValidIpList = new ArrayList<>();
    private boolean mIsCallbackRegistered = false;
    private boolean mIsRttScheduled = false;
    private boolean mIsCellularAvailable = false;
    private boolean mIsIwlanConnected = false;
    private boolean mIsRttRunning = false;
    private String mInterfaceName = null;
    private int mRttTimerId = INVALID_ID;

    private class BackhaulHandler extends Handler {
        BackhaulHandler() {
            super(mHandlerThread.getLooper());
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            log("handleMessage what = " + msg.what);
            QnsAsyncResult ar;
            switch (msg.what) {
                case EVENT_START_RTT_CHECK:
                    onRttCheckStarted();
                    break;
                case EVENT_IMS_REGISTRATION_STATE_CHANGED:
                    ar = (QnsAsyncResult) msg.obj;
                    onImsRegistrationStateChanged((QnsImsManager.ImsRegistrationState) ar.mResult);
                    break;
                default:
                    log("Invalid event = " + msg.what);
            }
        }
    }

    private class WiFiStatusCallback extends ConnectivityManager.NetworkCallback {

        @Override
        public void onAvailable(Network network) {
            super.onAvailable(network);
            if (network != null) {
                LinkProperties lp = mConnectivityManager.getLinkProperties(network);
                if (lp != null && lp.getInterfaceName().contains("wlan")) {
                    mInterfaceName = lp.getInterfaceName();
                }
            }
        }

        @Override
        public void onLost(Network network) {
            super.onLost(network);
            stopRttSchedule();
            mInterfaceName = null;
            mRttResult = false;
        }
    }
    /**
     * Constructor to create WifiBackhaulMonitor instance.
     */
    WifiBackhaulMonitor(
            Context context,
            QnsCarrierConfigManager configManager,
            QnsImsManager imsManager,
            QnsTimer qnstimer,
            int slotIndex) {
        mSlotIndex = slotIndex;
        mTag = WifiBackhaulMonitor.class.getSimpleName() + "[" + mSlotIndex + "]";
        mContext = context;
        mConnectivityManager = mContext.getSystemService(ConnectivityManager.class);
        mConfigManager = configManager;
        mQnsImsManager = imsManager;
        mQnsTimer = qnstimer;
        mNetworkCallback = new WiFiStatusCallback();
        mRegistrantList = new QnsRegistrantList();
        mHandlerThread = new HandlerThread(mTag);
        mHandlerThread.start();
        mHandler = new BackhaulHandler();
    }

    /** This method returns true if operator supports RTT feature. */
    boolean isRttCheckEnabled() {
        return mConfigManager.getWlanRttServerAddressConfig() != null;
    }

    /**
     * Registers to receive the change in Round-trip-time(RTT) ICMP pings for Wifi.
     *
     * @param h {@link Handler} to handle the result of the RTT pings.
     * @param what event which will be notified in handler.
     */
    void registerForRttStatusChange(Handler h, int what) {
        mRegistrantList.addUnique(h, what, null);
        if (!mIsCallbackRegistered) {
            mQnsImsManager.registerImsRegistrationStatusChanged(
                    mHandler, EVENT_IMS_REGISTRATION_STATE_CHANGED);
            mConnectivityManager.registerNetworkCallback(
                    new NetworkRequest.Builder()
                            .clearCapabilities()
                            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                            .build(),
                    mNetworkCallback);
            mIsCallbackRegistered = true;
        }
    }

    /**
     * Unregisters the handler for RTT ICMP pings.
     *
     * @param h {@link Handler} to unregister the event
     */
    void unRegisterForRttStatusChange(Handler h) {
        mRegistrantList.remove(h);
        if (mRegistrantList.size() == 0) {
            clearAll();
        }
    }

    /** Triggers the request to check RTT. */
    void requestRttCheck() {
        if (!mIsRttRunning) {
            if (mRttTimerId != INVALID_ID) {
                mQnsTimer.unregisterTimer(mRttTimerId);
                mRttTimerId = INVALID_ID;
            }
            mHandler.sendEmptyMessage(EVENT_START_RTT_CHECK);
        } else {
            log("RTT check is already running");
        }
    }

    /** Updates cellular availability in WifiBackhaulMonitor. */
    void setCellularAvailable(boolean cellularAvailable) {
        if (mIsCellularAvailable != cellularAvailable) {
            mIsCellularAvailable = cellularAvailable;
            if (mIsCellularAvailable) {
                startRttSchedule();
            } else {
                stopRttSchedule();
            }
        }
    }

    private void onRttCheckStarted() {
        mIsRttRunning = true;
        mRttResult = startRttCheck();
        if (mIsRttScheduled && mRttResult) {
            mIsRttScheduled = false;
            startRttSchedule();
        }
        mIsRttRunning = false;
        notifyRttResult();
    }

    private void onImsRegistrationStateChanged(QnsImsManager.ImsRegistrationState info) {
        if (info.getTransportType() == AccessNetworkConstants.TRANSPORT_TYPE_WLAN) {
            if (info.getEvent() == QnsConstants.IMS_REGISTRATION_CHANGED_REGISTERED) {
                mIsIwlanConnected = true;
                startRttSchedule();
            } else if (info.getEvent() == QnsConstants.IMS_REGISTRATION_CHANGED_UNREGISTERED) {
                mIsIwlanConnected = false;
                stopRttSchedule();
            }
        } else if (info.getTransportType() == AccessNetworkConstants.TRANSPORT_TYPE_WWAN
                && info.getEvent() == QnsConstants.IMS_REGISTRATION_CHANGED_REGISTERED) {
            mIsIwlanConnected = false;
            stopRttSchedule();
        }
    }

    private void startRttSchedule() {
        if (!mIsRttScheduled && mIsCellularAvailable && mIsIwlanConnected) {
            int delay = mConfigManager.getWlanRttOtherConfigs()[4];
            if (delay > 0) {
                startRttSchedule(delay);
            }
        }
    }

    private void startRttSchedule(int delay) {
        log("start RTT schedule for " + delay);
        mRttTimerId = mQnsTimer.registerTimer(Message.obtain(mHandler, EVENT_START_RTT_CHECK),
                delay);
        mIsRttScheduled = true;
    }

    private void stopRttSchedule() {
        if (mIsRttScheduled) {
            log("stop RTT schedule");
            mQnsTimer.unregisterTimer(mRttTimerId);
            mRttTimerId = INVALID_ID;
            mIsRttScheduled = false;
        }
    }

    private void notifyRttResult() {
        mRegistrantList.notifyResult(mRttResult);
        mValidIpList.clear();
    }

    private boolean startRttCheck() {
        if (mInterfaceName == null) {
            log("Wifi interface is not set for RTT check");
            return false;
        }
        int[] config = mConfigManager.getWlanRttOtherConfigs();
        if (config == null || config.length == 0) {
            log("No configurations are set for RTT check");
            return true;
        }

        int pingCount = config[0];
        int intervalTime = Math.max(config[1], 200);
        int pingSize = config[2];
        int requiredRttAverage = config[3];
        String rttPingServer = mConfigManager.getWlanRttServerAddressConfig();

        List<String>[] hostAddresses;
        try {
            hostAddresses = getHostAddresses(rttPingServer);
        } catch (UnknownHostException e) {
            log("Host not found for " + rttPingServer);
            return true;
        }

        boolean rttResult = true;
        Runtime runtime = Runtime.getRuntime();
        int ver = 0;
        String[] pings = new String[] {"ping", "ping6"}; // ping for IPv4 and IPv6
        for (String ping : pings) {
            List<String> addresses = hostAddresses[ver];
            for (String address : addresses) {
                StringBuilder command = new StringBuilder(ping);
                command.append(" -I ").append(mInterfaceName);
                command.append(" -i ").append((float) intervalTime / 1000);
                command.append(" -s ").append(pingSize);
                command.append(" -c ").append(pingCount);
                command.append(" ").append(address);
                try {
                    Process p = runtime.exec(command.toString());
                    BufferedReader br =
                            new BufferedReader(new InputStreamReader(p.getInputStream()));
                    String s;
                    while ((s = br.readLine()) != null) {
                        if (s.contains("/avg/")) {
                            int i = s.indexOf("/", s.indexOf("="));
                            String time = s.substring(i + 1, s.indexOf("/", i + 2));
                            float avgRtt = Float.parseFloat(time);
                            rttResult = avgRtt <= requiredRttAverage;
                            if (rttResult) {
                                log("RTT check is success.");
                                return true;
                            }
                        }
                    }
                } catch (IOException | NumberFormatException e) {
                    e.printStackTrace();
                }
            }
            ver++;
        }

        log("RTT Result: " + rttResult);
        return rttResult;
    }

    private List<String>[] getHostAddresses(String rttPingServer) throws UnknownHostException {
        List<String>[] lists = new List[2];
        lists[0] = new ArrayList<>(); // for IPv4
        lists[1] = new ArrayList<>(); // for IPv6
        InetAddress[] inetAddress = InetAddress.getAllByName(rttPingServer);
        for (InetAddress addr : inetAddress) {
            if (addr instanceof Inet4Address) {
                lists[0].add(addr.getHostAddress());
            } else {
                lists[1].add(addr.getHostAddress());
            }
        }
        return lists;
    }

    /** Closes the current instance. */
    void close() {
        mHandlerThread.quit();
        clearAll();
    }

    /** Method to clear all settings in WifiBackhaulMonitor */
    void clearAll() {
        stopRttSchedule();
        mRegistrantList.removeAll();
        if (mIsCallbackRegistered) {
            mConnectivityManager.unregisterNetworkCallback(mNetworkCallback);
            mQnsImsManager.unregisterImsRegistrationStatusChanged(mHandler);
            mIsCallbackRegistered = false;
        }
        mIsRttRunning = false;
        mIsCellularAvailable = false;
        mIsIwlanConnected = false;
        mIsRttScheduled = false;
    }

    @VisibleForTesting
    int getRttTimerId() {
        return mRttTimerId;
    }

    private void log(String s) {
        Log.d(mTag, s);
    }
}
