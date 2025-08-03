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

package com.android.server.wifi.p2p;

import android.content.Context;
import android.net.MacAddress;
import android.net.NetworkInfo;
import android.net.wifi.SynchronousExecutor;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pGroupList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Binder;
import android.os.Message;
import android.os.Process;

import com.android.internal.util.Protocol;
import com.android.modules.utils.BasicShellCommandHandler;
import com.android.modules.utils.build.SdkLevel;

import java.io.PrintWriter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Interprets and executes 'adb shell cmd wifip2p [args]'.
 * The leading command name is defined by android.content.Context.WIFI_P2P_SERVICE.
 */
public class WifiP2pShellCommand extends BasicShellCommandHandler {
    private static final String TAG = "WifiP2pShellCommand";

    private static WifiP2pManager.Channel sWifiP2pChannel;

    private final Context mContext;

    private final WifiP2pManager mWifiP2pManager;

    public WifiP2pShellCommand(Context context) {
        mContext = context;
        mWifiP2pManager = mContext.getSystemService(WifiP2pManager.class);
    }

    private int handleCommand(String cmd, PrintWriter pw) throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        WifiP2pManager.ActionListener actionListener = new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                countDownLatch.countDown();
            }
            @Override
            public void onFailure(int reason) {
                pw.println("FAILED with reason " + reason);
                countDownLatch.countDown();
            }
        };

        switch (cmd) {
            case "init":
                if (null != sWifiP2pChannel) sWifiP2pChannel.close();
                sWifiP2pChannel = mWifiP2pManager.initialize(
                        mContext, mContext.getMainLooper(), null);
                if (null == sWifiP2pChannel) {
                    pw.println("Cannot initialize p2p channel.");
                    return -1;
                }
                return 0;
            case "deinit":
                if (null != sWifiP2pChannel) sWifiP2pChannel.close();
                sWifiP2pChannel = null;
                return 0;
            case "start-peer-discovery":
                mWifiP2pManager.discoverPeers(sWifiP2pChannel, actionListener);
                countDownLatch.await(1000, TimeUnit.MILLISECONDS);
                return 0;
            case "start-peer-discovery-on-social-channels":
                if (!SdkLevel.isAtLeastT()) {
                    pw.println("This feature is only supported on SdkLevel T or later.");
                    return -1;
                }
                mWifiP2pManager.discoverPeersOnSocialChannels(sWifiP2pChannel, actionListener);
                countDownLatch.await(1000, TimeUnit.MILLISECONDS);
                return 0;
            case "start-peer-discovery-on-specific-frequency":
                if (!SdkLevel.isAtLeastT()) {
                    pw.println("This feature is only supported on SdkLevel T or later.");
                    return -1;
                }
                int frequencyMhz;
                try {
                    frequencyMhz = Integer.parseInt(getNextArgRequired());
                } catch (NumberFormatException e) {
                    pw.println(
                            "Invalid argument to 'start-peer-discovery-on-specific-frequency' "
                                    + "- must be an integer");
                    return -1;
                }
                if (frequencyMhz <= 0) {
                    pw.println("Invalid argument to 'start-peer-discovery-on-specific-frequency' "
                            + "- must be a positive integer.");
                    return -1;
                }
                mWifiP2pManager.discoverPeersOnSpecificFrequency(
                        sWifiP2pChannel, frequencyMhz, actionListener);
                countDownLatch.await(1000, TimeUnit.MILLISECONDS);
                return 0;
            case "stop-peer-discovery":
                mWifiP2pManager.stopPeerDiscovery(sWifiP2pChannel, actionListener);
                countDownLatch.await(1000, TimeUnit.MILLISECONDS);
                return 0;
            case "start-service-discovery":
                mWifiP2pManager.discoverServices(sWifiP2pChannel, actionListener);
                countDownLatch.await(1000, TimeUnit.MILLISECONDS);
                return 0;
            case "stop-service-discovery":
                mWifiP2pManager.stopPeerDiscovery(sWifiP2pChannel, actionListener);
                countDownLatch.await(1000, TimeUnit.MILLISECONDS);
                return 0;
            case "list-peers":
                mWifiP2pManager.requestPeers(sWifiP2pChannel,
                        new WifiP2pManager.PeerListListener() {
                            @Override
                            public void onPeersAvailable(WifiP2pDeviceList peers) {
                                pw.println(String.format("%-32s %-24s %-10s %-10s %-10s",
                                        "Name", "Address", "DevCaps", "GroupCaps", "Status"));
                                for (WifiP2pDevice d: peers.getDeviceList()) {
                                    pw.println(String.format("%-32s %-24s 0x%010x 0x%010x %-10s",
                                            d.deviceName, d.deviceAddress,
                                            d.deviceCapability, d.groupCapability,
                                            wifiP2pDeviceStatusToStr(d.status)));
                                }
                                countDownLatch.countDown();
                            }
                        });
                countDownLatch.await(1000, TimeUnit.MILLISECONDS);
                return 0;
            case "remove-client":
                if (!SdkLevel.isAtLeastT()) {
                    pw.println("This feature is only supported on SdkLevel T or later.");
                    return -1;
                }
                MacAddress peerAddress;
                try {
                    peerAddress = MacAddress.fromString(getNextArgRequired());
                } catch (IllegalArgumentException e) {
                    pw.println(
                            "Invalid argument to 'remove-client' "
                                    + "- must be a valid mac address");
                    return -1;
                }
                mWifiP2pManager.removeClient(sWifiP2pChannel, peerAddress, actionListener);
                countDownLatch.await(1000, TimeUnit.MILLISECONDS);
                return 0;
            case "cancel-connect":
                mWifiP2pManager.cancelConnect(sWifiP2pChannel, actionListener);
                countDownLatch.await(1000, TimeUnit.MILLISECONDS);
                return 0;
            case "create-group":
                mWifiP2pManager.createGroup(sWifiP2pChannel, actionListener);
                countDownLatch.await(1000, TimeUnit.MILLISECONDS);
                return 0;
            case "remove-group":
                mWifiP2pManager.removeGroup(sWifiP2pChannel, actionListener);
                countDownLatch.await(1000, TimeUnit.MILLISECONDS);
                return 0;
            case "set-device-name":
                String deviceName = getNextArgRequired();
                mWifiP2pManager.setDeviceName(sWifiP2pChannel, deviceName, actionListener);
                countDownLatch.await(1000, TimeUnit.MILLISECONDS);
                return 0;
            case "get-connection-info":
                mWifiP2pManager.requestConnectionInfo(sWifiP2pChannel,
                        new WifiP2pManager.ConnectionInfoListener() {
                            @Override
                            public void onConnectionInfoAvailable(WifiP2pInfo info) {
                                pw.println(info.toString());
                                countDownLatch.countDown();
                            }
                        });
                countDownLatch.await(1000, TimeUnit.MILLISECONDS);
                return 0;
            case "get-group-info":
                mWifiP2pManager.requestGroupInfo(sWifiP2pChannel,
                        new WifiP2pManager.GroupInfoListener() {
                            @Override
                            public void onGroupInfoAvailable(WifiP2pGroup group) {
                                pw.println(group);
                                countDownLatch.countDown();
                            }
                        });
                countDownLatch.await(1000, TimeUnit.MILLISECONDS);
                return 0;
            case "get-state":
                mWifiP2pManager.requestP2pState(sWifiP2pChannel,
                        new WifiP2pManager.P2pStateListener() {
                            @Override
                            public void onP2pStateAvailable(int state) {
                                switch (state) {
                                    case WifiP2pManager.WIFI_P2P_STATE_DISABLED:
                                        pw.println("DISABLED");
                                        break;
                                    case WifiP2pManager.WIFI_P2P_STATE_ENABLED:
                                        pw.println("ENABLED");
                                        break;
                                    default:
                                        pw.println("UNKNOWN");
                                        break;
                                }
                                countDownLatch.countDown();
                            }
                        });
                countDownLatch.await(1000, TimeUnit.MILLISECONDS);
                return 0;
            case "get-discovery-state":
                mWifiP2pManager.requestDiscoveryState(sWifiP2pChannel,
                        new WifiP2pManager.DiscoveryStateListener() {
                            @Override
                            public void onDiscoveryStateAvailable(int state) {
                                switch (state) {
                                    case WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED:
                                        pw.println("STARTED");
                                        break;
                                    case WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED:
                                        pw.println("STOPPED");
                                        break;
                                    default:
                                        pw.println("UNKNOWN");
                                        break;
                                }
                                countDownLatch.countDown();
                            }
                        });
                countDownLatch.await(1000, TimeUnit.MILLISECONDS);
                return 0;
            case "get-listen-state": {
                mWifiP2pManager.getListenState(sWifiP2pChannel, new SynchronousExecutor(),
                        state -> {
                            switch (state) {
                                case WifiP2pManager.WIFI_P2P_LISTEN_STARTED:
                                    pw.println("STARTED");
                                    break;
                                case WifiP2pManager.WIFI_P2P_LISTEN_STOPPED:
                                    pw.println("STOPPED");
                                    break;
                                default:
                                    pw.println("UNKNOWN");
                                    break;
                            }
                            countDownLatch.countDown();
                        });
                countDownLatch.await(1000, TimeUnit.MILLISECONDS);
                return 0;
            }
            case "get-network-info":
                mWifiP2pManager.requestNetworkInfo(sWifiP2pChannel,
                        new WifiP2pManager.NetworkInfoListener() {
                            @Override
                            public void onNetworkInfoAvailable(NetworkInfo networkInfo) {
                                pw.println(networkInfo.toString());
                                countDownLatch.countDown();
                            }
                        });
                countDownLatch.await(1000, TimeUnit.MILLISECONDS);
                return 0;
            case "get-device-info":
                mWifiP2pManager.requestDeviceInfo(sWifiP2pChannel,
                        new WifiP2pManager.DeviceInfoListener() {
                            @Override
                            public void onDeviceInfoAvailable(WifiP2pDevice dev) {
                                pw.println(dev.toString());
                                countDownLatch.countDown();
                            }
                        });
                countDownLatch.await(1000, TimeUnit.MILLISECONDS);
                return 0;
            case "list-saved-groups":
                mWifiP2pManager.requestPersistentGroupInfo(sWifiP2pChannel,
                        new WifiP2pManager.PersistentGroupInfoListener() {
                            @Override
                            public void onPersistentGroupInfoAvailable(WifiP2pGroupList groups) {
                                pw.println(groups.toString());
                                countDownLatch.countDown();
                            }
                        });
                countDownLatch.await(1000, TimeUnit.MILLISECONDS);
                return 0;
            case "delete-saved-group":
                int netId;
                try {
                    netId = Integer.parseInt(getNextArgRequired());
                } catch (NumberFormatException e) {
                    pw.println(
                            "Invalid argument to 'delete-saved-group' "
                                    + "- must be an integer");
                    return -1;
                }
                if (netId < 0) {
                    pw.println("Invalid argument to 'delete-saved-group' "
                            + "- must be 0 or a positive integer.");
                    return -1;
                }
                mWifiP2pManager.deletePersistentGroup(sWifiP2pChannel, netId, actionListener);
                countDownLatch.await(1000, TimeUnit.MILLISECONDS);
                return 0;
            case "set-channels":
                int listeningChannel, operatingChannel;
                try {
                    listeningChannel = Integer.parseInt(getNextArgRequired());
                    operatingChannel = Integer.parseInt(getNextArgRequired());
                } catch (NumberFormatException e) {
                    pw.println(
                            "Invalid argument to 'set-channels' "
                                    + "- must be an integer");
                    return -1;
                }
                if (listeningChannel < 0 || operatingChannel < 0) {
                    pw.println("Invalid argument to 'set-channels' "
                            + "- must be 0 or a positive integer.");
                    return -1;
                }
                mWifiP2pManager.setWifiP2pChannels(sWifiP2pChannel,
                        listeningChannel, operatingChannel, actionListener);
                countDownLatch.await(1000, TimeUnit.MILLISECONDS);
                return 0;
            case "start-listening":
                mWifiP2pManager.startListening(sWifiP2pChannel, actionListener);
                countDownLatch.await(1000, TimeUnit.MILLISECONDS);
                return 0;
            case "stop-listening":
                mWifiP2pManager.stopListening(sWifiP2pChannel, actionListener);
                countDownLatch.await(1000, TimeUnit.MILLISECONDS);
                return 0;
            case "set-miracast-mode":
                int mode;
                try {
                    mode = Integer.parseInt(getNextArgRequired());
                } catch (NumberFormatException e) {
                    pw.println("Invalid argument to 'set-miracast-mode' "
                            + "- must be an integer");
                    return -1;
                }
                if (mode < 0) {
                    pw.println("Invalid argument to 'set-miracast-mode' "
                            + "- must be 0 or a positive integer.");
                    return -1;
                }
                mWifiP2pManager.setMiracastMode(mode);
                return 0;
            case "factory-reset":
                mWifiP2pManager.factoryReset(sWifiP2pChannel, actionListener);
                countDownLatch.await(1000, TimeUnit.MILLISECONDS);
                return 0;
            case "connect": {
                WifiP2pConfig config = buildWifiP2pConfig(pw);
                if (null == config) {
                    pw.println("Invalid argument to 'connect'");
                    return -1;
                }
                mWifiP2pManager.connect(sWifiP2pChannel, config, actionListener);
                countDownLatch.await(3000, TimeUnit.MILLISECONDS);
                return 0;
            }
            case "accept-connection":
                mWifiP2pManager.getP2pStateMachineMessenger()
                        .send(Message.obtain(null, Protocol.BASE_WIFI_P2P_SERVICE + 2));
                return 0;
            case "reject-connection":
                mWifiP2pManager.getP2pStateMachineMessenger()
                        .send(Message.obtain(null, Protocol.BASE_WIFI_P2P_SERVICE + 3));
                return 0;
            case "create-group-with-config": {
                WifiP2pConfig config = prepareWifiP2pConfig(pw);
                if (null == config) {
                    pw.println("Invalid argument to 'create-group-with-config'");
                    return -1;
                }
                mWifiP2pManager.createGroup(sWifiP2pChannel, config, actionListener);
                countDownLatch.await(1000, TimeUnit.MILLISECONDS);
                return 0;
            }
            case "connect-with-config": {
                WifiP2pConfig config = prepareWifiP2pConfig(pw);
                if (null == config) {
                    pw.println("Invalid argument to 'connect-with-config'");
                    return -1;
                }
                mWifiP2pManager.connect(sWifiP2pChannel, config, actionListener);
                countDownLatch.await(1000, TimeUnit.MILLISECONDS);
                return 0;
            }
            default:
                return handleDefaultCommands(cmd);
        }
    }

    @Override
    public int onCommand(String cmd) {
        final PrintWriter pw = getOutPrintWriter();
        checkRootPermission();

        // Treat no command as help command.
        if (cmd == null || cmd.equals("")) {
            cmd = "help";
        }
        if (!commandDoesNotRequireP2pAlreadyInitialized(cmd, pw)) return -1;

        try {
            return handleCommand(cmd, pw);
        } catch (Exception e) {
            pw.println("Exception: " + e);
        }

        return -1;
    }

    private boolean commandDoesNotRequireP2pAlreadyInitialized(String cmd, PrintWriter pw) {
        if (cmd.equals("init")) return true;
        if (cmd.equals("deinit")) return true;
        if (cmd.equals("help")) return true;

        if (null == mWifiP2pManager) {
            pw.println("P2p service is not available.");
            return false;
        }

        if (null == sWifiP2pChannel) {
            pw.println("P2p client is not initialized,  execute init first.");
            return false;
        }

        return true;
    }

    private String wifiP2pDeviceStatusToStr(int status) {
        switch (status) {
            case WifiP2pDevice.CONNECTED:
                return "CONNECTED";
            case WifiP2pDevice.INVITED:
                return "INVITED";
            case WifiP2pDevice.FAILED:
                return "FAILED";
            case WifiP2pDevice.AVAILABLE:
                return "AVAILABLE";
            case WifiP2pDevice.UNAVAILABLE:
                return "UNAVAILABLE";
        }
        return "UNKNOWN";
    }

    private WifiP2pConfig buildWifiP2pConfig(PrintWriter pw) {
        String deviceAddress = getNextArgRequired();
        int goIntent = WifiP2pConfig.GROUP_OWNER_INTENT_AUTO;
        int ipProvisioningMode = WifiP2pConfig.GROUP_CLIENT_IP_PROVISIONING_MODE_IPV4_DHCP;
        String option = getNextOption();
        while (option != null) {
            if (option.equals("-i")) {
                try {
                    goIntent = Integer.parseInt(getNextArgRequired());
                } catch (NumberFormatException e) {
                    pw.println("Invalid argument for group owner intent "
                            + "- must be an integer");
                    return null;
                }
                if (goIntent < WifiP2pConfig.GROUP_OWNER_INTENT_MIN
                        || goIntent > WifiP2pConfig.GROUP_OWNER_INTENT_MAX) {
                    pw.println("Invalid argument for group owner intent "
                            + "- must be from "
                            + WifiP2pConfig.GROUP_OWNER_INTENT_MIN + " to "
                            + WifiP2pConfig.GROUP_OWNER_INTENT_MAX);
                    return null;
                }
            } else if (option.equals("-m")) {
                if (!SdkLevel.isAtLeastT()) {
                    pw.println("Invalid argument for group client IP provisioning mode "
                            + "- IP provisioning mode is supported only on SdkLevel T or later");
                    return null;
                }
                try {
                    ipProvisioningMode = Integer.parseInt(getNextArgRequired());
                } catch (NumberFormatException e) {
                    pw.println("Invalid argument for group client IP provisioning mode "
                            + "- must be an integer");
                    return null;
                }
                if (ipProvisioningMode != WifiP2pConfig.GROUP_CLIENT_IP_PROVISIONING_MODE_IPV4_DHCP
                        && ipProvisioningMode
                        != WifiP2pConfig.GROUP_CLIENT_IP_PROVISIONING_MODE_IPV6_LINK_LOCAL) {
                    pw.println("Invalid argument for group client IP provisioning mode "
                            + "- must be "
                            + WifiP2pConfig.GROUP_CLIENT_IP_PROVISIONING_MODE_IPV4_DHCP + " or "
                            + WifiP2pConfig.GROUP_CLIENT_IP_PROVISIONING_MODE_IPV6_LINK_LOCAL);
                    return null;
                }
            } else {
                pw.println("Ignoring unknown option " + option);
            }
            option = getNextOption();
        }
        WifiP2pConfig.Builder configBuilder = new WifiP2pConfig.Builder();
        configBuilder.setDeviceAddress(MacAddress.fromString(deviceAddress));
        if (SdkLevel.isAtLeastT()) {
            configBuilder.setGroupClientIpProvisioningMode(ipProvisioningMode);
        }
        WifiP2pConfig config = configBuilder.build();
        config.groupOwnerIntent = goIntent;
        return config;
    }

    private WifiP2pConfig prepareWifiP2pConfig(PrintWriter pw) {
        String networkName = getNextArgRequired();
        String passphrase = getNextArgRequired();
        int operatingBandOrFreq;
        try {
            operatingBandOrFreq = Integer.parseInt(getNextArgRequired());
        } catch (NumberFormatException e) {
            pw.println("Invalid argument to for wifi p2p config opeartingBandOrFreq "
                    + "- must be an integer");
            return null;
        }
        if (operatingBandOrFreq < 0) {
            pw.println("Invalid argument to for wifi p2p config opeartingBandOrFreq "
                    + "- must be 0 or a positive integer.");
            return null;
        }
        boolean isPersistent = getNextArgRequiredTrueOrFalse("true", "false");
        WifiP2pConfig.Builder builder = new WifiP2pConfig.Builder()
                .setNetworkName(networkName)
                .setPassphrase(passphrase);
        if (operatingBandOrFreq < 1000) {
            builder.setGroupOperatingBand(operatingBandOrFreq);
        } else {
            builder.setGroupOperatingFrequency(operatingBandOrFreq);
        }
        builder.enablePersistentMode(isPersistent);
        return builder.build();
    }

    private boolean getNextArgRequiredTrueOrFalse(String trueString, String falseString)
            throws IllegalArgumentException {
        String nextArg = getNextArgRequired();
        if (trueString.equals(nextArg)) {
            return true;
        } else if (falseString.equals(nextArg)) {
            return false;
        } else {
            throw new IllegalArgumentException("Expected '" + trueString + "' or '" + falseString
                    + "' as next arg but got '" + nextArg + "'");
        }
    }

    private void checkRootPermission() {
        final int uid = Binder.getCallingUid();
        if (uid == Process.ROOT_UID) {
            // Root can do anything.
            return;
        }
        throw new SecurityException("Uid " + uid + " does not have access to wifip2p commands");
    }

    @Override
    public void onHelp() {
        final PrintWriter pw = getOutPrintWriter();

        pw.println("Wi-Fi P2P (wifip2p) commands:");
        pw.println("  help");
        pw.println("    Print this help text.");
        pw.println("  init");
        pw.println("    Init p2p client, this must be called before executing p2p commands.");
        pw.println("  deinit");
        pw.println("    De-init p2p client, this must be called at the end, or wifi service will"
                        + " keep the p2p client and block SoftAp or NAN.");
        pw.println("  start-peer-discovery");
        pw.println("    Start p2p peer discovery.");
        pw.println("  start-peer-discovery-on-social-channels");
        pw.println("    Start p2p peer discovery on social channels.");
        pw.println("  start-peer-discovery-on-specific-frequency <frequency>");
        pw.println("    Start p2p peer discovery on specific frequency.");
        pw.println("  stop-peer-discovery");
        pw.println("    Stop p2p peer discovery.");
        pw.println("  start-service-discovery");
        pw.println("    Start p2p service discovery.");
        pw.println("  stop-service-discovery");
        pw.println("    Stop p2p service discovery.");
        pw.println("  start-listening");
        pw.println("    Start p2p listening.");
        pw.println("  stop-listening");
        pw.println("    Stop p2p listening.");
        pw.println("  list-peers");
        pw.println("    List scanned peers.");
        pw.println("  set-device-name <name>");
        pw.println("    Set the p2p device name.");
        pw.println("  get-connection-info");
        pw.println("    Get current connection information.");
        pw.println("  get-group-info");
        pw.println("    Get current group information.");
        pw.println("  get-network-info");
        pw.println("    Get current P2P network information.");
        pw.println("  get-device-info");
        pw.println("    Get the device information");
        pw.println("  get-state");
        pw.println("    Get P2P state.");
        pw.println("  get-discovery-state");
        pw.println("    Indicate whether p2p discovery is running or not.");
        pw.println("  get-listen-state");
        pw.println("    Indicate whether p2p listen is running or not.");
        pw.println("  list-saved-groups");
        pw.println("    List saved groups.");
        pw.println("  delete-saved-group <networkId>");
        pw.println("    Delete a saved group.");
        pw.println("  set-channels <listening channel> <operating channel>");
        pw.println("    Set listening channel and operating channel.");
        pw.println("  set-miracast-mode (0|1|2)");
        pw.println("    Set Miracast mode. 0 is DISABLED, 1 is SOURCE, and 2 is SINK.");
        pw.println("  factory-reset");
        pw.println("    Do P2P factory reset.");
        pw.println("  accept-connection");
        pw.println("    Accept an incoming connection request.");
        pw.println("  reject-connection");
        pw.println("    Reject an incoming connection request.");
        pw.println("  connect <device address> [-i <groupOwnerIntent>] "
                        + "[-m <groupClientIpProvisioningMode>]");
        pw.println("    <device address> - the peer's MAC address.");
        pw.println("    <groupOwnerIntent> - Set group owner intent value. The value range is "
                + WifiP2pConfig.GROUP_OWNER_INTENT_MIN + " to "
                + WifiP2pConfig.GROUP_OWNER_INTENT_MAX);
        pw.println("    <groupClientIpProvisioningMode> - Set group client IP provisioning "
                        + "mode (supported on SdkLevel T or later)");
        pw.println("        - Use '" + WifiP2pConfig.GROUP_CLIENT_IP_PROVISIONING_MODE_IPV4_DHCP
                + "' to select IPv4 DHCP which is system default behavior");
        pw.println("        - Use '"
                + WifiP2pConfig.GROUP_CLIENT_IP_PROVISIONING_MODE_IPV6_LINK_LOCAL
                + "' to select IPv6 link-local.");
        pw.println("    Connect to a device with provided params.");
        pw.println("  connect-with-config <network name> <passphrase>"
                        + " <bandOrFreq> <persistent>");
        pw.println("    <bandOrFreq> - select the preferred band or frequency.");
        pw.println("        - Use '2' to select 2.4GHz band as the preferred band");
        pw.println("        - Use '5' to select 5GHz band as the preferred band");
        pw.println("        - Use a frequency in MHz to indicate the preferred frequency.");
        pw.println("    <persistent> true for a persistent group; otherwise false.");
        pw.println("    Connect to a device with a configuration.");
        pw.println("  remove-client <peerAddress>");
        pw.println("    <peerAddress> the MAC address of the p2p client.");
        pw.println("    Remove the p2p client.");
        pw.println("  cancel-connect");
        pw.println("    Cancel an onging connection request.");
        pw.println("  create-group");
        pw.println("    Create a persistent autonomous group.");
        pw.println("  create-group-with-config <network name> <passphrase>"
                        + " <bandOrFreq> <persistent>");
        pw.println("    <bandOrFreq> - select the preferred band or frequency.");
        pw.println("        - Use '2' to select 2.4GHz band as the preferred band");
        pw.println("        - Use '5' to select 5GHz band as the preferred band");
        pw.println("        - Use a frequency in MHz to indicate the preferred frequency.");
        pw.println("    <persistent> true for a persistent group; otherwise false.");
        pw.println("    Create an autonomous group with a configuration.");
        pw.println("  remove-group");
        pw.println("    Remove current formed group.");
        pw.println();
    }
}
