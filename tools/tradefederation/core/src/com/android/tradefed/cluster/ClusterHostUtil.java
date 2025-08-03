/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tradefed.cluster;

import com.android.tradefed.command.remote.DeviceDescriptor;
import com.android.tradefed.config.GlobalConfiguration;
import com.android.tradefed.device.DeviceManager;
import com.android.tradefed.device.DeviceManager.FastbootDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.VersionParser;

import com.google.common.base.Strings;
import com.google.common.net.HostAndPort;
import com.google.common.primitives.Longs;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Static util functions for TF Cluster to get global config instances, host information, etc. */
public class ClusterHostUtil {

    private static String sHostName = null;

    private static String sHostIpAddress = null;

    static final String DEFAULT_TF_VERSION = "(unknown)";
    static final String EMULATOR_SERIAL_PREFIX = "emulator-";
    static final String NULL_DEVICE_SERIAL_PLACEHOLDER = "(no device serial)";
    static final String UNKNOWN = "UNKNOWN";
    static final String TRADEFED = "TRADEFED";
    static final String LOCALHOST_IP = "127.0.0.1";
    static final String LOCALHOST_NAME = "localhost";

    private static long sTfStartTime = getCurrentTimeMillis();

    /**
     * Gets the hostname.
     *
     * <p>1. Try to get hostname from InetAddress. 2. If fail, try to get hostname from HOSTNAME
     * env. 3. If not set, generate a unique hostname.
     *
     * @return the hostname or null if we were unable to fetch it.
     */
    public static String getHostName() {
        if (sHostName != null) {
            return sHostName;
        }
        try {
            sHostName = InetAddress.getLocalHost().getHostName();
            return sHostName;
        } catch (UnknownHostException e) {
            CLog.w("Failed to get hostname from InetAddress: %s", e);
        }
        CLog.i("Get hostname from HOSTNAME env.");
        sHostName = System.getenv("HOSTNAME");
        if (!Strings.isNullOrEmpty(sHostName)) {
            return sHostName;
        }
        sHostName = "unknown-" + UUID.randomUUID().toString();
        CLog.i("No HOSTNAME env set. Generate hostname: %s.", sHostName);
        return sHostName;
    }

    /**
     * Returns a unique device serial for a device.
     *
     * <p>Non-physical devices (e.g. emulator) have pseudo serials which are not unique across
     * hosts. This method prefixes those with a hostname to make them unique.
     *
     * @param device a device descriptor.
     * @return a unique device serial.
     */
    public static String getUniqueDeviceSerial(DeviceDescriptor device) {
        String serial = device.getSerial();
        if (Strings.isNullOrEmpty(serial)) {
            return String.format("%s:%s", getHostName(), NULL_DEVICE_SERIAL_PLACEHOLDER);
        }
        if ((device.isStubDevice()
                        && !FastbootDevice.class.getSimpleName().equals(device.getDeviceClass()))
                || serial.startsWith(EMULATOR_SERIAL_PREFIX)) {
            return String.format("%s:%s", getHostName(), serial);
        }
        return serial;
    }

    /**
     * Returns a local device serial for a given unique device serial.
     *
     * <p>TFC sends down unique device serials for non-physical devices which TF does not
     * understand. This method converts them back to local device serials.
     *
     * @param serial a unique device serial from TFC.
     * @return a local device serial.
     */
    public static String getLocalDeviceSerial(String serial) {
        String prefix = getHostName() + ":";
        if (serial.startsWith(prefix)) {
            return serial.substring(prefix.length());
        }
        return serial;
    }
    /**
     * Gets the IP address.
     *
     * @return the IPV4 address String or "UNKNOWN" if we were unable to fetch it.
     */
    public static String getHostIpAddress() {
        if (sHostIpAddress == null) {
            List<InetAddress> addresses = new ArrayList<>();
            try {
                Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
                if (interfaces == null) {
                    return UNKNOWN;
                }
                for (NetworkInterface networkInterface : Collections.list(interfaces)) {
                    if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                        continue;
                    }
                    for (InetAddress address :
                            Collections.list(networkInterface.getInetAddresses())) {
                        if (address.isLinkLocalAddress()
                                || address.isLoopbackAddress()
                                || address instanceof Inet6Address) {
                            continue;
                        }
                        addresses.add(address);
                    }
                }
            } catch (SocketException e) {
                CLog.w(e);
            }
            if (!addresses.isEmpty()) {
                sHostIpAddress = addresses.get(0).getHostAddress();
            }
        }
        return sHostIpAddress == null ? UNKNOWN : sHostIpAddress;
    }

    /**
     * Gets the TF version running on this host.
     *
     * @return this host's TF version.
     */
    public static String getTfVersion() {
        final String version = VersionParser.fetchVersion();
        return toValidTfVersion(version);
    }

    /**
     * Validates a TF version and returns it if it is OK.
     *
     * @param version The string for a TF version provided by {@link VersionParser}
     * @return the version if valid or a default if not.
     */
    protected static String toValidTfVersion(String version) {
        if (Strings.isNullOrEmpty(version) || Longs.tryParse(version) == null) {
            // Making sure the version is valid. It should be a build number
            return DEFAULT_TF_VERSION;
        }
        return version;
    }

    /**
     * Returns the run target for a given device descriptor.
     *
     * @param device {@link DeviceDescriptor} to get run target for.
     * @return run target.
     */
    public static String getRunTarget(
            DeviceDescriptor device, String runTargetFormat, Map<String, String> deviceTags) {
        if (runTargetFormat != null) {
            // Make sure the pattern is non-greedy.
            Pattern p = Pattern.compile("\\{([^:\\}]+)(:.*)?\\}");
            Matcher m = p.matcher(runTargetFormat);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                String pattern = m.group(1);
                String key = null;
                String txt = null;
                switch (pattern) {
                    case "PRODUCT":
                        txt = device.getProduct();
                        break;
                    case "PRODUCT_OR_DEVICE_CLASS":
                        // TODO: Refactor the logic to handle more flexible combinations.
                        txt = device.getProduct();
                        if (device.isStubDevice()) {
                            String deviceClass = device.getDeviceClass();
                            // If it's a fastboot device we report it as the product
                            if (!FastbootDevice.class.getSimpleName().equals(deviceClass)) {
                                txt = deviceClass;
                            }
                        }
                        break;
                    case "PRODUCT_VARIANT":
                        txt = device.getProductVariant();
                        break;
                    case "API_LEVEL":
                        txt = device.getSdkVersion();
                        break;
                    case "DEVICE_CLASS":
                        txt = device.getDeviceClass();
                        break;
                    case "SERIAL":
                        txt = getUniqueDeviceSerial(device);
                        break;
                    case "TAG":
                        if (deviceTags == null || deviceTags.isEmpty()) {
                            // simply delete the placeholder if there's nothing to match
                            txt = "";
                        } else {
                            txt = deviceTags.get(device.getSerial());
                            if (txt == null) {
                                txt = ""; // simply delete it if a tag does not exist
                            }
                        }
                        break;
                    case "DEVICE_PROP":
                        key = m.group(2).substring(1);
                        txt = device.getProperty(key);
                        break;
                    default:
                        throw new InvalidParameterException(
                                String.format(
                                        "Unsupported pattern '%s' found for run target '%s'",
                                        pattern, runTargetFormat));
                }
                if (txt == null || DeviceManager.UNKNOWN_DISPLAY_STRING.equals(txt)) {
                    return DeviceManager.UNKNOWN_DISPLAY_STRING;
                }
                m.appendReplacement(sb, Matcher.quoteReplacement(txt));
            }
            m.appendTail(sb);
            return sb.toString();
        }
        // Default behavior.
        // TODO: Remove this when we cluster default run target is changed.
        String runTarget = device.getProduct();
        if (!runTarget.equals(device.getProductVariant())) {
            runTarget += ":" + device.getProductVariant();
        }
        return runTarget;
    }

    /**
     * Checks if a given input is a localhost IP:PORT string.
     *
     * @param input a string to check
     * @return true if the given input is a localhost IP:PORT string
     */
    public static boolean isLocalhostIpPort(String input) {
        try {
            HostAndPort hostAndPort = HostAndPort.fromString(input);
            return (LOCALHOST_IP.equals(hostAndPort.getHost())
                    || LOCALHOST_NAME.equals(hostAndPort.getHost()));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Returns the current system time.
     *
     * @return time in millis.
     */
    public static long getCurrentTimeMillis() {
        return System.currentTimeMillis();
    }

    public static long getTfStartTimeMillis() {
        return sTfStartTime;
    }

    /** Get the {@link IClusterOptions} instance used to store cluster-related settings. */
    public static IClusterOptions getClusterOptions() {
        IClusterOptions clusterOptions =
                (IClusterOptions)
                        GlobalConfiguration.getInstance()
                                .getConfigurationObject(ClusterOptions.TYPE_NAME);
        if (clusterOptions == null) {
            throw new IllegalStateException(
                    "cluster_options not defined. You must add this "
                            + "object to your global config. See google/atp/cluster.xml.");
        }

        return clusterOptions;
    }

    /** Get the {@link IClusterClient} instance used to interact with the TFC backend. */
    public static IClusterClient getClusterClient() {
        IClusterClient ClusterClient =
                (IClusterClient)
                        GlobalConfiguration.getInstance()
                                .getConfigurationObject(IClusterClient.TYPE_NAME);
        if (ClusterClient == null) {
            throw new IllegalStateException(
                    "cluster_client not defined. You must add this "
                            + "object to your global config. See google/atp/cluster.xml.");
        }

        return ClusterClient;
    }

    public static String getTestHarness() {
        return TRADEFED;
    }
}
