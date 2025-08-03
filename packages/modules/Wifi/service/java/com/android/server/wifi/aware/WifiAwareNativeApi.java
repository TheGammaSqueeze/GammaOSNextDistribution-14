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

package com.android.server.wifi.aware;

import android.net.MacAddress;
import android.net.wifi.aware.AwareParams;
import android.net.wifi.aware.ConfigRequest;
import android.net.wifi.aware.PublishConfig;
import android.net.wifi.aware.SubscribeConfig;
import android.net.wifi.aware.WifiAwareDataPathSecurityConfig;
import android.net.wifi.util.HexEncoding;
import android.util.ArrayMap;
import android.util.Log;
import android.util.SparseIntArray;

import com.android.modules.utils.BasicShellCommandHandler;
import com.android.server.wifi.hal.WifiNanIface;
import com.android.server.wifi.hal.WifiNanIface.PowerParameters;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Translates Wi-Fi Aware requests from the framework to the HAL.
 *
 * Delegates the management of the NAN interface to WifiAwareNativeManager.
 */
public class WifiAwareNativeApi implements WifiAwareShellCommand.DelegatedShellCommand {
    private static final String TAG = "WifiAwareNativeApi";
    private boolean mVdbg = false; // STOPSHIP if true
    private boolean mVerboseLoggingEnabled = false;

    private final WifiAwareNativeManager mHal;
    private SparseIntArray mTransactionIds; // VDBG only!

    public WifiAwareNativeApi(WifiAwareNativeManager wifiAwareNativeManager) {
        mHal = wifiAwareNativeManager;
        onReset();
    }

    /**
     * Enable/Disable verbose logging.
     *
     */
    public void enableVerboseLogging(boolean verboseEnabled, boolean vDbg) {
        mVerboseLoggingEnabled = verboseEnabled;
        mVdbg = vDbg;
    }

    private void recordTransactionId(int transactionId) {
        if (!mVdbg) return;

        if (transactionId == 0) {
            return; // tid == 0 is used as a placeholder transaction ID in several commands
        }

        if (mTransactionIds == null) {
            mTransactionIds = new SparseIntArray();
        }
        int count = mTransactionIds.get(transactionId);
        if (count != 0) {
            Log.wtf(TAG, "Repeated transaction ID == " + transactionId);
        }
        mTransactionIds.append(transactionId, count + 1);
    }

    /*
     * Parameters settable through the shell command.
     * see wifi/1.0/types.hal NanBandSpecificConfig.discoveryWindowIntervalVal and
     * wifi/1.2/types.hal NanConfigRequestSupplemental_1_2 for description
     */
    /* package */ static final String POWER_PARAM_DEFAULT_KEY = "default";
    /* package */ static final String POWER_PARAM_INACTIVE_KEY = "inactive";
    /* package */ static final String POWER_PARAM_IDLE_KEY = "idle";

    /* package */ static final String PARAM_DW_24GHZ = "dw_24ghz";
    private static final int PARAM_DW_24GHZ_DEFAULT = 1; // 1 -> DW=1, latency=512ms
    private static final int PARAM_DW_24GHZ_INACTIVE = 4; // 4 -> DW=8, latency=4s
    private static final int PARAM_DW_24GHZ_IDLE = 4; // == inactive

    /* package */ static final String PARAM_DW_5GHZ = "dw_5ghz";
    private static final int PARAM_DW_5GHZ_DEFAULT = 1; // 1 -> DW=1, latency=512ms
    private static final int PARAM_DW_5GHZ_INACTIVE = 0; // 0 = disabled
    private static final int PARAM_DW_5GHZ_IDLE = 0; // == inactive

    /* package */ static final String PARAM_DW_6GHZ = "dw_6ghz";
    private static final int PARAM_DW_6GHZ_DEFAULT = 1; // 1 -> DW=1, latency=512ms
    private static final int PARAM_DW_6GHZ_INACTIVE = 0; // 0 = disabled
    private static final int PARAM_DW_6GHZ_IDLE = 0; // == inactive

    /* package */ static final String PARAM_DISCOVERY_BEACON_INTERVAL_MS =
            "disc_beacon_interval_ms";
    private static final int PARAM_DISCOVERY_BEACON_INTERVAL_MS_DEFAULT = 0; // Firmware defaults
    private static final int PARAM_DISCOVERY_BEACON_INTERVAL_MS_INACTIVE = 0; // Firmware defaults
    private static final int PARAM_DISCOVERY_BEACON_INTERVAL_MS_IDLE = 0; // Firmware defaults

    /* package */ static final String PARAM_NUM_SS_IN_DISCOVERY = "num_ss_in_discovery";
    private static final int PARAM_NUM_SS_IN_DISCOVERY_DEFAULT = 0; // Firmware defaults
    private static final int PARAM_NUM_SS_IN_DISCOVERY_INACTIVE = 0; // Firmware defaults
    private static final int PARAM_NUM_SS_IN_DISCOVERY_IDLE = 0; // Firmware defaults

    /* package */ static final String PARAM_ENABLE_DW_EARLY_TERM = "enable_dw_early_term";
    private static final int PARAM_ENABLE_DW_EARLY_TERM_DEFAULT = 0; // boolean: 0 = false
    private static final int PARAM_ENABLE_DW_EARLY_TERM_INACTIVE = 0; // boolean: 0 = false
    private static final int PARAM_ENABLE_DW_EARLY_TERM_IDLE = 0; // boolean: 0 = false

    /* package */ static final String PARAM_MAC_RANDOM_INTERVAL_SEC = "mac_random_interval_sec";
    private static final int PARAM_MAC_RANDOM_INTERVAL_SEC_DEFAULT = 1800; // 30 minutes

    private final Map<String, Map<String, Integer>> mSettablePowerParameters = new HashMap<>();
    private final Map<String, Integer> mSettableParameters = new HashMap<>();
    private final Map<String, Integer> mExternalSetParams = new ArrayMap<>();

    /**
     * Accept using parameter from external to config the Aware
     */
    public void setAwareParams(AwareParams parameters) {
        mExternalSetParams.clear();
        if (parameters == null) {
            return;
        }
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "setting Aware Parameters=" + parameters);
        }
        if (parameters.getDiscoveryWindowWakeInterval24Ghz() > 0
                && parameters.getDiscoveryWindowWakeInterval24Ghz() <= 5) {
            mExternalSetParams.put(PARAM_DW_24GHZ,
                    parameters.getDiscoveryWindowWakeInterval24Ghz());
        }
        if (parameters.getDiscoveryWindowWakeInterval5Ghz() >= 0
                && parameters.getDiscoveryWindowWakeInterval5Ghz() <= 5) {
            mExternalSetParams.put(PARAM_DW_5GHZ, parameters.getDiscoveryWindowWakeInterval5Ghz());
        }
        if (parameters.getDiscoveryBeaconIntervalMillis() > 0) {
            mExternalSetParams.put(PARAM_DISCOVERY_BEACON_INTERVAL_MS,
                    parameters.getDiscoveryBeaconIntervalMillis());
        }
        if (parameters.getNumSpatialStreamsInDiscovery() > 0) {
            mExternalSetParams.put(PARAM_NUM_SS_IN_DISCOVERY,
                    parameters.getNumSpatialStreamsInDiscovery());
        }
        if (parameters.getMacRandomizationIntervalSeconds() > 0
                && parameters.getMacRandomizationIntervalSeconds() <= 1800) {
            mExternalSetParams.put(PARAM_MAC_RANDOM_INTERVAL_SEC,
                    parameters.getMacRandomizationIntervalSeconds());
        }
        if (parameters.isDwEarlyTerminationEnabled()) {
            mExternalSetParams.put(PARAM_ENABLE_DW_EARLY_TERM, 1);
        }
    }


    /**
     * Interpreter of adb shell command 'adb shell wifiaware native_api ...'.
     *
     * @return -1 if parameter not recognized or invalid value, 0 otherwise.
     */
    @Override
    public int onCommand(BasicShellCommandHandler parentShell) {
        final PrintWriter pw = parentShell.getErrPrintWriter();

        String subCmd = parentShell.getNextArgRequired();
        if (mVdbg) Log.v(TAG, "onCommand: subCmd='" + subCmd + "'");
        switch (subCmd) {
            case "set": {
                String name = parentShell.getNextArgRequired();
                if (mVdbg) Log.v(TAG, "onCommand: name='" + name + "'");
                if (!mSettableParameters.containsKey(name)) {
                    pw.println("Unknown parameter name -- '" + name + "'");
                    return -1;
                }

                String valueStr = parentShell.getNextArgRequired();
                if (mVdbg) Log.v(TAG, "onCommand: valueStr='" + valueStr + "'");
                int value;
                try {
                    value = Integer.valueOf(valueStr);
                } catch (NumberFormatException e) {
                    pw.println("Can't convert value to integer -- '" + valueStr + "'");
                    return -1;
                }
                mSettableParameters.put(name, value);
                return 0;
            }
            case "set-power": {
                String mode = parentShell.getNextArgRequired();
                String name = parentShell.getNextArgRequired();
                String valueStr = parentShell.getNextArgRequired();

                if (mVdbg) {
                    Log.v(TAG, "onCommand: mode='" + mode + "', name='" + name + "'" + ", value='"
                            + valueStr + "'");
                }

                if (!mSettablePowerParameters.containsKey(mode)) {
                    pw.println("Unknown mode name -- '" + mode + "'");
                    return -1;
                }
                if (!mSettablePowerParameters.get(mode).containsKey(name)) {
                    pw.println("Unknown parameter name '" + name + "' in mode '" + mode + "'");
                    return -1;
                }

                int value;
                try {
                    value = Integer.valueOf(valueStr);
                } catch (NumberFormatException e) {
                    pw.println("Can't convert value to integer -- '" + valueStr + "'");
                    return -1;
                }
                mSettablePowerParameters.get(mode).put(name, value);
                return 0;
            }
            case "get": {
                String name = parentShell.getNextArgRequired();
                if (mVdbg) Log.v(TAG, "onCommand: name='" + name + "'");
                if (!mSettableParameters.containsKey(name)) {
                    pw.println("Unknown parameter name -- '" + name + "'");
                    return -1;
                }

                parentShell.getOutPrintWriter().println((int) mSettableParameters.get(name));
                return 0;
            }
            case "get-power": {
                String mode = parentShell.getNextArgRequired();
                String name = parentShell.getNextArgRequired();
                if (mVdbg) Log.v(TAG, "onCommand: mode='" + mode + "', name='" + name + "'");
                if (!mSettablePowerParameters.containsKey(mode)) {
                    pw.println("Unknown mode -- '" + mode + "'");
                    return -1;
                }
                if (!mSettablePowerParameters.get(mode).containsKey(name)) {
                    pw.println("Unknown parameter name -- '" + name + "' in mode '" + mode + "'");
                    return -1;
                }

                parentShell.getOutPrintWriter().println(
                        (int) mSettablePowerParameters.get(mode).get(name));
                return 0;
            }
            default:
                pw.println("Unknown 'wifiaware native_api <cmd>'");
        }

        return -1;
    }

    @Override
    public void onReset() {
        Map<String, Integer> defaultMap = new HashMap<>();
        defaultMap.put(PARAM_DW_24GHZ, PARAM_DW_24GHZ_DEFAULT);
        defaultMap.put(PARAM_DW_5GHZ, PARAM_DW_5GHZ_DEFAULT);
        defaultMap.put(PARAM_DW_6GHZ, PARAM_DW_6GHZ_DEFAULT);
        defaultMap.put(PARAM_DISCOVERY_BEACON_INTERVAL_MS,
                PARAM_DISCOVERY_BEACON_INTERVAL_MS_DEFAULT);
        defaultMap.put(PARAM_NUM_SS_IN_DISCOVERY, PARAM_NUM_SS_IN_DISCOVERY_DEFAULT);
        defaultMap.put(PARAM_ENABLE_DW_EARLY_TERM, PARAM_ENABLE_DW_EARLY_TERM_DEFAULT);

        Map<String, Integer> inactiveMap = new HashMap<>();
        inactiveMap.put(PARAM_DW_24GHZ, PARAM_DW_24GHZ_INACTIVE);
        inactiveMap.put(PARAM_DW_5GHZ, PARAM_DW_5GHZ_INACTIVE);
        inactiveMap.put(PARAM_DW_6GHZ, PARAM_DW_6GHZ_INACTIVE);
        inactiveMap.put(PARAM_DISCOVERY_BEACON_INTERVAL_MS,
                PARAM_DISCOVERY_BEACON_INTERVAL_MS_INACTIVE);
        inactiveMap.put(PARAM_NUM_SS_IN_DISCOVERY, PARAM_NUM_SS_IN_DISCOVERY_INACTIVE);
        inactiveMap.put(PARAM_ENABLE_DW_EARLY_TERM, PARAM_ENABLE_DW_EARLY_TERM_INACTIVE);

        Map<String, Integer> idleMap = new HashMap<>();
        idleMap.put(PARAM_DW_24GHZ, PARAM_DW_24GHZ_IDLE);
        idleMap.put(PARAM_DW_5GHZ, PARAM_DW_5GHZ_IDLE);
        idleMap.put(PARAM_DW_6GHZ, PARAM_DW_6GHZ_IDLE);
        idleMap.put(PARAM_DISCOVERY_BEACON_INTERVAL_MS,
                PARAM_DISCOVERY_BEACON_INTERVAL_MS_IDLE);
        idleMap.put(PARAM_NUM_SS_IN_DISCOVERY, PARAM_NUM_SS_IN_DISCOVERY_IDLE);
        idleMap.put(PARAM_ENABLE_DW_EARLY_TERM, PARAM_ENABLE_DW_EARLY_TERM_IDLE);

        mSettablePowerParameters.put(POWER_PARAM_DEFAULT_KEY, defaultMap);
        mSettablePowerParameters.put(POWER_PARAM_INACTIVE_KEY, inactiveMap);
        mSettablePowerParameters.put(POWER_PARAM_IDLE_KEY, idleMap);

        mSettableParameters.put(PARAM_MAC_RANDOM_INTERVAL_SEC,
                PARAM_MAC_RANDOM_INTERVAL_SEC_DEFAULT);
        mExternalSetParams.clear();
    }

    @Override
    public void onHelp(String command, BasicShellCommandHandler parentShell) {
        final PrintWriter pw = parentShell.getOutPrintWriter();

        pw.println("  " + command);
        pw.println("    set <name> <value>: sets named parameter to value. Names: "
                + mSettableParameters.keySet());
        pw.println("    set-power <mode> <name> <value>: sets named power parameter to value."
                + " Modes: " + mSettablePowerParameters.keySet()
                + ", Names: " + mSettablePowerParameters.get(POWER_PARAM_DEFAULT_KEY).keySet());
        pw.println("    get <name>: gets named parameter value. Names: "
                + mSettableParameters.keySet());
        pw.println("    get-power <mode> <name>: gets named parameter value."
                + " Modes: " + mSettablePowerParameters.keySet()
                + ", Names: " + mSettablePowerParameters.get(POWER_PARAM_DEFAULT_KEY).keySet());
    }

    /**
     * Query the firmware's capabilities.
     *
     * @param transactionId Transaction ID for the transaction - used in the async callback to
     *                      match with the original request.
     */
    public boolean getCapabilities(short transactionId) {
        if (mVerboseLoggingEnabled) Log.v(TAG, "getCapabilities: transactionId=" + transactionId);
        recordTransactionId(transactionId);

        WifiNanIface iface = mHal.getWifiNanIface();
        if (iface == null) {
            Log.e(TAG, "getCapabilities: null interface");
            return false;
        }
        return iface.getCapabilities(transactionId);
    }

    /**
     * Enable and configure Aware.
     * @param transactionId Transaction ID for the transaction - used in the
     *            async callback to match with the original request.
     * @param configRequest Requested Aware configuration.
     * @param notifyIdentityChange Indicates whether or not to get address change callbacks.
     * @param initialConfiguration Specifies whether initial configuration
*            (true) or an update (false) to the configuration.
     * @param isInteractive PowerManager.isInteractive
     * @param isIdle PowerManager.isIdle
     * @param rangingEnabled Indicates whether or not enable ranging.
     * @param isInstantCommunicationEnabled Indicates whether or not enable instant communication
     * @param instantModeChannel
     * @param clusterId the id of the cluster to join.
     */
    public boolean enableAndConfigure(short transactionId, ConfigRequest configRequest,
            boolean notifyIdentityChange, boolean initialConfiguration, boolean isInteractive,
            boolean isIdle, boolean rangingEnabled, boolean isInstantCommunicationEnabled,
            int instantModeChannel, int clusterId) {
        Log.d(TAG, "enableAndConfigure: transactionId=" + transactionId + ", configRequest="
                + configRequest + ", notifyIdentityChange=" + notifyIdentityChange
                + ", initialConfiguration=" + initialConfiguration
                + ", isInteractive=" + isInteractive + ", isIdle=" + isIdle
                + ", isRangingEnabled=" + rangingEnabled
                + ", isInstantCommunicationEnabled=" + isInstantCommunicationEnabled
                + ", instantModeChannel=" + instantModeChannel
                + ", clusterId=" + clusterId);
        recordTransactionId(transactionId);

        WifiNanIface iface = mHal.getWifiNanIface();
        if (iface == null) {
            Log.e(TAG, "enableAndConfigure: null interface");
            return false;
        }
        return iface.enableAndConfigure(transactionId, configRequest, notifyIdentityChange,
                initialConfiguration, rangingEnabled,
                isInstantCommunicationEnabled, instantModeChannel, clusterId,
                mExternalSetParams.getOrDefault(PARAM_MAC_RANDOM_INTERVAL_SEC,
                        mSettableParameters.get(PARAM_MAC_RANDOM_INTERVAL_SEC)),
                getPowerParameters(isInteractive, isIdle));
    }

    /**
     * Disable Aware.
     *
     * @param transactionId transactionId Transaction ID for the transaction -
     *            used in the async callback to match with the original request.
     */
    public boolean disable(short transactionId) {
        if (mVerboseLoggingEnabled) Log.d(TAG, "disable");
        recordTransactionId(transactionId);

        WifiNanIface iface = mHal.getWifiNanIface();
        if (iface == null) {
            Log.e(TAG, "disable: null interface");
            return false;
        }
        boolean result = iface.disable(transactionId);
        return result;
    }

    /**
     * Start or modify a service publish session.
     *  @param transactionId transactionId Transaction ID for the transaction -
     *            used in the async callback to match with the original request.
     * @param publishId ID of the requested session - 0 to request a new publish
     *            session.
     * @param publishConfig Configuration of the discovery session.
     * @param nik
     */
    public boolean publish(short transactionId, byte publishId, PublishConfig publishConfig,
            byte[] nik) {
        if (mVerboseLoggingEnabled) {
            Log.d(TAG, "publish: transactionId=" + transactionId + ", publishId=" + publishId
                    + ", config=" + publishConfig);
        }
        recordTransactionId(transactionId);

        WifiNanIface iface = mHal.getWifiNanIface();
        if (iface == null) {
            Log.e(TAG, "publish: null interface");
            return false;
        }
        return iface.publish(transactionId, publishId, publishConfig, nik);
    }

    /**
     * Start or modify a service subscription session.
     *  @param transactionId transactionId Transaction ID for the transaction -
     *            used in the async callback to match with the original request.
     * @param subscribeId ID of the requested session - 0 to request a new
     *            subscribe session.
     * @param subscribeConfig Configuration of the discovery session.
     * @param nik
     */
    public boolean subscribe(short transactionId, byte subscribeId,
            SubscribeConfig subscribeConfig, byte[] nik) {
        if (mVerboseLoggingEnabled) {
            Log.d(TAG, "subscribe: transactionId=" + transactionId + ", subscribeId=" + subscribeId
                    + ", config=" + subscribeConfig);
        }
        recordTransactionId(transactionId);

        WifiNanIface iface = mHal.getWifiNanIface();
        if (iface == null) {
            Log.e(TAG, "subscribe: null interface");
            return false;
        }
        return iface.subscribe(transactionId, subscribeId, subscribeConfig, nik);
    }

    /**
     * Send a message through an existing discovery session.
     *
     * @param transactionId transactionId Transaction ID for the transaction -
     *            used in the async callback to match with the original request.
     * @param pubSubId ID of the existing publish/subscribe session.
     * @param requestorInstanceId ID of the peer to communicate with - obtained
     *            through a previous discovery (match) operation with that peer.
     * @param dest MAC address of the peer to communicate with - obtained
     *            together with requestorInstanceId.
     * @param message Message.
     * @param messageId Arbitary integer from host (not sent to HAL - useful for
     *                  testing/debugging at this level)
     */
    public boolean sendMessage(short transactionId, byte pubSubId, int requestorInstanceId,
            byte[] dest, byte[] message, int messageId) {
        if (mVerboseLoggingEnabled) {
            Log.d(TAG,
                    "sendMessage: transactionId=" + transactionId + ", pubSubId=" + pubSubId
                            + ", requestorInstanceId=" + requestorInstanceId + ", dest="
                            + String.valueOf(HexEncoding.encode(dest)) + ", messageId=" + messageId
                            + ", message=" + (message == null ? "<null>"
                            : HexEncoding.encode(message)) + ", message.length=" + (message == null
                            ? 0 : message.length));
        }
        recordTransactionId(transactionId);

        WifiNanIface iface = mHal.getWifiNanIface();
        if (iface == null) {
            Log.e(TAG, "sendMessage: null interface");
            return false;
        }

        try {
            MacAddress destMac = MacAddress.fromBytes(dest);
            return iface.sendMessage(
                    transactionId, pubSubId, requestorInstanceId, destMac, message);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Invalid dest mac received: " + Arrays.toString(dest));
            return false;
        }
    }

    /**
     * Terminate a publish discovery session.
     *
     * @param transactionId transactionId Transaction ID for the transaction -
     *            used in the async callback to match with the original request.
     * @param pubSubId ID of the publish/subscribe session - obtained when
     *            creating a session.
     */
    public boolean stopPublish(short transactionId, byte pubSubId) {
        if (mVerboseLoggingEnabled) {
            Log.d(TAG, "stopPublish: transactionId=" + transactionId + ", pubSubId=" + pubSubId);
        }
        recordTransactionId(transactionId);

        WifiNanIface iface = mHal.getWifiNanIface();
        if (iface == null) {
            Log.e(TAG, "stopPublish: null interface");
            return false;
        }
        return iface.stopPublish(transactionId, pubSubId);
    }

    /**
     * Terminate a subscribe discovery session.
     *
     * @param transactionId transactionId Transaction ID for the transaction -
     *            used in the async callback to match with the original request.
     * @param pubSubId ID of the publish/subscribe session - obtained when
     *            creating a session.
     */
    public boolean stopSubscribe(short transactionId, byte pubSubId) {
        if (mVerboseLoggingEnabled) {
            Log.d(TAG, "stopSubscribe: transactionId=" + transactionId + ", pubSubId=" + pubSubId);
        }
        recordTransactionId(transactionId);

        WifiNanIface iface = mHal.getWifiNanIface();
        if (iface == null) {
            Log.e(TAG, "stopSubscribe: null interface");
            return false;
        }
        return iface.stopSubscribe(transactionId, pubSubId);
    }

    /**
     * Create a Aware network interface. This only creates the Linux interface - it doesn't actually
     * create the data connection.
     *
     * @param transactionId Transaction ID for the transaction - used in the async callback to
     *                      match with the original request.
     * @param interfaceName The name of the interface, e.g. "aware0".
     */
    public boolean createAwareNetworkInterface(short transactionId, String interfaceName) {
        Log.d(TAG, "createAwareNetworkInterface: transactionId=" + transactionId + ", "
                    + "interfaceName=" + interfaceName);
        recordTransactionId(transactionId);

        WifiNanIface iface = mHal.getWifiNanIface();
        if (iface == null) {
            Log.e(TAG, "createAwareNetworkInterface: null interface");
            return false;
        }
        return iface.createAwareNetworkInterface(transactionId, interfaceName);
    }

    /**
     * Deletes a Aware network interface. The data connection can (should?) be torn down previously.
     *
     * @param transactionId Transaction ID for the transaction - used in the async callback to
     *                      match with the original request.
     * @param interfaceName The name of the interface, e.g. "aware0".
     */
    public boolean deleteAwareNetworkInterface(short transactionId, String interfaceName) {
        Log.d(TAG, "deleteAwareNetworkInterface: transactionId=" + transactionId + ", "
                + "interfaceName=" + interfaceName);
        recordTransactionId(transactionId);

        WifiNanIface iface = mHal.getWifiNanIface();
        if (iface == null) {
            Log.e(TAG, "deleteAwareNetworkInterface: null interface");
            return false;
        }
        return iface.deleteAwareNetworkInterface(transactionId, interfaceName);
    }

    /**
     * Initiates setting up a data-path between device and peer. Security is provided by either
     * PMK or Passphrase (not both) - if both are null then an open (unencrypted) link is set up.
     *
     * @param transactionId      Transaction ID for the transaction - used in the async callback to
     *                           match with the original request.
     * @param peerId             ID of the peer ID to associate the data path with. A value of 0
     *                           indicates that not associated with an existing session.
     * @param channelRequestType Indicates whether the specified channel is available, if available
     *                           requested or forced (resulting in failure if cannot be
     *                           accommodated).
     * @param channel            The channel on which to set up the data-path.
     * @param peer               The MAC address of the peer to create a connection with.
     * @param interfaceName      The interface on which to create the data connection.
     * @param isOutOfBand        Is the data-path out-of-band (i.e. without a corresponding Aware
     *                           discovery
     *                           session).
     * @param appInfo            Arbitrary binary blob transmitted to the peer.
     * @param capabilities       The capabilities of the firmware.
     * @param securityConfig     Security config to encrypt the data-path
     */
    public boolean initiateDataPath(short transactionId, int peerId, int channelRequestType,
            int channel, byte[] peer, String interfaceName,
            boolean isOutOfBand, byte[] appInfo, Capabilities capabilities,
            WifiAwareDataPathSecurityConfig securityConfig, byte pubSubId) {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "initiateDataPath: transactionId=" + transactionId + ", peerId=" + peerId
                    + ", channelRequestType=" + channelRequestType + ", channel=" + channel
                    + ", peer=" + String.valueOf(HexEncoding.encode(peer)) + ", interfaceName="
                    + interfaceName + ", securityConfig=" + securityConfig
                    + ", isOutOfBand=" + isOutOfBand + ", appInfo.length="
                    + ((appInfo == null) ? 0 : appInfo.length) + ", capabilities=" + capabilities);
        }
        recordTransactionId(transactionId);

        WifiNanIface iface = mHal.getWifiNanIface();
        if (iface == null) {
            Log.e(TAG, "initiateDataPath: null interface");
            return false;
        }

        try {
            MacAddress peerMac = MacAddress.fromBytes(peer);
            return iface.initiateDataPath(transactionId, peerId, channelRequestType, channel,
                    peerMac, interfaceName, isOutOfBand, appInfo, capabilities, securityConfig,
                    pubSubId);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Invalid peer mac received: " + Arrays.toString(peer));
            return false;
        }
    }

    /**
     * Responds to a data request from a peer. Security is provided by either PMK or Passphrase (not
     * both) - if both are null then an open (unencrypted) link is set up.
     *
     * @param transactionId  Transaction ID for the transaction - used in the async callback to
     *                       match with the original request.
     * @param accept         Accept (true) or reject (false) the original call.
     * @param ndpId          The NDP (Aware data path) ID. Obtained from the request callback.
     * @param interfaceName  The interface on which the data path will be setup. Obtained from the
     *                       request callback.
     * @param appInfo        Arbitrary binary blob transmitted to the peer.
     * @param isOutOfBand    Is the data-path out-of-band (i.e. without a corresponding Aware
     *                       discovery
     *                       session).
     * @param capabilities   The capabilities of the firmware.
     * @param securityConfig Security config to encrypt the data-path
     */
    public boolean respondToDataPathRequest(short transactionId, boolean accept, int ndpId,
            String interfaceName, byte[] appInfo,
            boolean isOutOfBand, Capabilities capabilities,
            WifiAwareDataPathSecurityConfig securityConfig, byte pubSubId) {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "respondToDataPathRequest: transactionId=" + transactionId + ", accept="
                    + accept + ", int ndpId=" + ndpId + ", interfaceName=" + interfaceName
                    + ", appInfo.length=" + ((appInfo == null) ? 0 : appInfo.length)
                    + ", securityConfig" + securityConfig);
        }
        recordTransactionId(transactionId);

        WifiNanIface iface = mHal.getWifiNanIface();
        if (iface == null) {
            Log.e(TAG, "respondToDataPathRequest: null interface");
            return false;
        }
        return iface.respondToDataPathRequest(transactionId, accept, ndpId, interfaceName, appInfo,
                isOutOfBand, capabilities, securityConfig, pubSubId);
    }

    /**
     * Terminate an existing data-path (does not delete the interface).
     *
     * @param transactionId Transaction ID for the transaction - used in the async callback to
     *                      match with the original request.
     * @param ndpId The NDP (Aware data path) ID to be terminated.
     */
    public boolean endDataPath(short transactionId, int ndpId) {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "endDataPath: transactionId=" + transactionId + ", ndpId=" + ndpId);
        }
        recordTransactionId(transactionId);

        WifiNanIface iface = mHal.getWifiNanIface();
        if (iface == null) {
            Log.e(TAG, "endDataPath: null interface");
            return false;
        }
        return iface.endDataPath(transactionId, ndpId);
    }

    /**
     * Terminate an existing pairing setup
     *
     * @param transactionId Transaction ID for the transaction - used in the async callback to
     *                      match with the original request.
     * @param pairId The id of the pairing session
     */
    public boolean endPairing(short transactionId, int pairId) {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "endPairing: transactionId=" + transactionId + ", ndpId=" + pairId);
        }
        recordTransactionId(transactionId);

        WifiNanIface iface = mHal.getWifiNanIface();
        if (iface == null) {
            Log.e(TAG, "endDataPath: null interface");
            return false;
        }
        return iface.endPairing(transactionId, pairId);
    }

    /**
     * Initiate a NAN pairing request for this publish/subscribe session
     *
     * @param transactionId      Transaction ID for the transaction - used in the
     *                           async callback to match with the original request.
     * @param peerId             ID of the peer. Obtained through previous communication (a
     *                           match indication).
     * @param pairingIdentityKey NAN identity key
     * @param requestType        Setup or verification
     * @param pmk                credential for the pairing verification
     * @param password           credential for the pairing setup
     * @param akm                Key exchange method is used for pairing
     * @return True is the request send succeed.
     */
    public boolean initiatePairing(short transactionId, int peerId, byte[] peer,
            byte[] pairingIdentityKey, boolean enablePairingCache, int requestType, byte[] pmk,
            String password, int akm, int cipherSuite) {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "initiatePairing: transactionId=" + transactionId + ", peerId=" + peerId
                    + ", requestType=" + requestType + ", enablePairingCache=" + enablePairingCache
                    + ", peer=" + String.valueOf(HexEncoding.encode(peer)));
        }
        recordTransactionId(transactionId);

        WifiNanIface iface = mHal.getWifiNanIface();
        if (iface == null) {
            Log.e(TAG, "initiatePairing: null interface");
            return false;
        }

        try {
            MacAddress peerMac = MacAddress.fromBytes(peer);
            return iface.initiatePairing(transactionId, peerId, peerMac, pairingIdentityKey,
                    enablePairingCache, requestType, pmk, password, akm, cipherSuite);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Invalid peer mac received: " + Arrays.toString(peer));
            return false;
        }
    }

    /**
     * Response to a NAN pairing request for this from this session
     *
     * @param transactionId      Transaction ID for the transaction - used in the
     *                           async callback to match with the original request.
     * @param pairingId          The id of the current pairing session
     * @param accept             True if accpect, false otherwise
     * @param pairingIdentityKey NAN identity key
     * @param requestType        Setup or verification
     * @param pmk                credential for the pairing verification
     * @param password           credential for the pairing setup
     * @param akm                Key exchange method is used for pairing
     * @return True is the request send succeed.
     */
    public boolean respondToPairingRequest(short transactionId, int pairingId, boolean accept,
            byte[] pairingIdentityKey, boolean enablePairingCache, int requestType, byte[] pmk,
            String password, int akm, int cipherSuite) {
        if (mVerboseLoggingEnabled) {
            Log.v(
                    TAG,
                    "respondToPairingRequest: transactionId="
                            + transactionId
                            + ", accept="
                            + accept
                            + ", int pairingId="
                            + pairingId
                            + ", enablePairingCache="
                            + enablePairingCache
                            + ", requestType"
                            + requestType);
        }
        recordTransactionId(transactionId);

        WifiNanIface iface = mHal.getWifiNanIface();
        if (iface == null) {
            Log.e(TAG, "respondToPairingRequest: null interface");
            return false;
        }
        return iface.respondToPairingRequest(transactionId, pairingId, accept, pairingIdentityKey,
                enablePairingCache, requestType, pmk, password, akm, cipherSuite);
    }

    /**
     * Initiate a Bootstrapping request for this publish/subscribe session
     *
     * @param transactionId Transaction ID for the transaction - used in the
     *                      async callback to match with the original request.
     * @param peerId        ID of the peer. Obtained through previous communication (a match
     *                      indication).
     * @param peer          The MAC address of the peer to create a connection with.
     * @param method        proposed bootstrapping method
     * @param pubSubId      ID of the publish/subscribe session - obtained when creating a session.
     * @param isComeBack    If the request is for a previous comeback response
     * @return True if the request send success
     */
    public boolean initiateBootstrapping(short transactionId, int peerId, byte[] peer, int method,
            byte[] cookie, byte pubSubId, boolean isComeBack) {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "initiateBootstrapping: transactionId=" + transactionId
                    + ", peerId=" + peerId + ", method=" + method
                    + ", peer=" + String.valueOf(HexEncoding.encode(peer)));
        }
        recordTransactionId(transactionId);

        WifiNanIface iface = mHal.getWifiNanIface();
        if (iface == null) {
            Log.e(TAG, "initiateBootstrapping: null interface");
            return false;
        }

        try {
            MacAddress peerMac = MacAddress.fromBytes(peer);
            return iface.initiateBootstrapping(transactionId, peerId, peerMac, method, cookie,
                    pubSubId, isComeBack);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Invalid peer mac received: " + Arrays.toString(peer));
            return false;
        }
    }

    /**
     * Response to a bootstrapping request for this from this session
     *
     * @param transactionId   Transaction ID for the transaction - used in the
     *                        async callback to match with the original request.
     * @param bootstrappingId The id of the current boostraping session
     * @param accept          True is proposed method is accepte
     * @param pubSubId        ID of the publish/subscribe session - obtained when creating a
     *                        session.
     * @return True if the request send success
     */
    public boolean respondToBootstrappingRequest(short transactionId, int bootstrappingId,
            boolean accept, byte pubSubId) {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "respondToBootstrappingRequest: transactionId=" + transactionId);
        }
        recordTransactionId(transactionId);

        WifiNanIface iface = mHal.getWifiNanIface();
        if (iface == null) {
            Log.e(TAG, "respondToBootstrappingRequest: null interface");
            return false;
        }

        return iface.respondToBootstrappingRequest(transactionId, bootstrappingId, accept,
                pubSubId);
    }

    /**
     * Suspends the specified Aware session.
     * @param transactionId transactionId Transaction ID for the transaction -
     *            used in the async callback to match with the original request.
     * @param pubSubId ID of the existing publish/subscribe session.
     * @return True if the request is sent successfully.
     */
    public boolean suspendRequest(short transactionId, byte pubSubId) {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "suspendRequest: transactionId=" + transactionId);
        }
        recordTransactionId(transactionId);

        WifiNanIface iface = mHal.getWifiNanIface();
        if (iface == null) {
            Log.e(TAG, "suspendRequest: null interface");
            return false;
        }

        return iface.suspendRequest(transactionId, pubSubId);
    }

    /**
     * Resumes the specified (suspended) Aware session.
     * @param transactionId transactionId Transaction ID for the transaction -
     *            used in the async callback to match with the original request.
     * @param pubSubId ID of the existing publish/subscribe session.
     * @return True if the request is sent successfully.
     */
    public boolean resumeRequest(short transactionId, byte pubSubId) {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "resumeRequest: transactionId=" + transactionId);
        }
        recordTransactionId(transactionId);

        WifiNanIface iface = mHal.getWifiNanIface();
        if (iface == null) {
            Log.e(TAG, "resumeRequest: null interface");
            return false;
        }

        return iface.resumeRequest(transactionId, pubSubId);
    }

    // utilities

    /**
     * Create a PowerParameters object to pass our cached parameters to the HAL.
     */
    private PowerParameters getPowerParameters(
            boolean isInteractive, boolean isIdle) {
        PowerParameters params = new PowerParameters();
        String key = POWER_PARAM_DEFAULT_KEY;
        if (isIdle) {
            key = POWER_PARAM_IDLE_KEY;
        } else if (!isInteractive) {
            key = POWER_PARAM_INACTIVE_KEY;
        }

        params.discoveryWindow24Ghz = getSettablePowerParameters(key, PARAM_DW_24GHZ);
        params.discoveryWindow5Ghz = getSettablePowerParameters(key, PARAM_DW_5GHZ);
        params.discoveryWindow6Ghz = getSettablePowerParameters(key, PARAM_DW_6GHZ);
        params.discoveryBeaconIntervalMs = getSettablePowerParameters(key,
                PARAM_DISCOVERY_BEACON_INTERVAL_MS);
        params.numberOfSpatialStreamsInDiscovery = getSettablePowerParameters(key,
                PARAM_NUM_SS_IN_DISCOVERY);
        params.enableDiscoveryWindowEarlyTermination = getSettablePowerParameters(key,
                PARAM_ENABLE_DW_EARLY_TERM) != 0;
        return params;
    }

    private int getSettablePowerParameters(String state, String key) {
        if (mExternalSetParams.containsKey(key)) {
            return mExternalSetParams.get(key);
        }
        return mSettablePowerParameters.get(state).get(key);
    }

    /**
     * Dump the internal state of the class.
     */
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("WifiAwareNativeApi:");
        pw.println("  mSettableParameters: " + mSettableParameters);
        pw.println("  mExternalSetParams" + mExternalSetParams);
        mHal.dump(fd, pw, args);
    }
}
