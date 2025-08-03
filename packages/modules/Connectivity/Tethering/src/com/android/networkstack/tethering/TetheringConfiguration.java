/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.networkstack.tethering;

import static android.net.ConnectivityManager.TYPE_ETHERNET;
import static android.net.ConnectivityManager.TYPE_MOBILE;
import static android.net.ConnectivityManager.TYPE_MOBILE_DUN;
import static android.net.ConnectivityManager.TYPE_MOBILE_HIPRI;
import static android.provider.DeviceConfig.NAMESPACE_CONNECTIVITY;

import static com.android.networkstack.apishim.ConstantsShim.KEY_CARRIER_SUPPORTS_TETHERING_BOOL;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.net.TetheringConfigurationParcel;
import android.os.PersistableBundle;
import android.provider.DeviceConfig;
import android.provider.Settings;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.build.SdkLevel;
import com.android.net.module.util.DeviceConfigUtils;
import com.android.net.module.util.SharedLog;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.StringJoiner;

/**
 * A utility class to encapsulate the various tethering configuration elements.
 *
 * This configuration data includes elements describing upstream properties
 * (preferred and required types of upstream connectivity as well as default
 * DNS servers to use if none are available) and downstream properties (such
 * as regular expressions use to match suitable downstream interfaces and the
 * DHCPv4 ranges to use).
 *
 * @hide
 */
public class TetheringConfiguration {
    private static final String TAG = TetheringConfiguration.class.getSimpleName();

    private static final String[] EMPTY_STRING_ARRAY = new String[0];

    // Default ranges used for the legacy DHCP server.
    // USB is  192.168.42.1 and 255.255.255.0
    // Wifi is 192.168.43.1 and 255.255.255.0
    // BT is limited to max default of 5 connections. 192.168.44.1 to 192.168.48.1
    // with 255.255.255.0
    // P2P is 192.168.49.1 and 255.255.255.0
    private static final String[] LEGACY_DHCP_DEFAULT_RANGE = {
        "192.168.42.2", "192.168.42.254", "192.168.43.2", "192.168.43.254",
        "192.168.44.2", "192.168.44.254", "192.168.45.2", "192.168.45.254",
        "192.168.46.2", "192.168.46.254", "192.168.47.2", "192.168.47.254",
        "192.168.48.2", "192.168.48.254", "192.168.49.2", "192.168.49.254",
    };

    private static final String[] DEFAULT_IPV4_DNS = {"8.8.4.4", "8.8.8.8"};

    @VisibleForTesting
    public static final int TETHER_USB_RNDIS_FUNCTION = 0;

    @VisibleForTesting
    public static final int TETHER_USB_NCM_FUNCTION   = 1;

    /**
     * Override enabling BPF offload configuration for tethering.
     */
    public static final String OVERRIDE_TETHER_ENABLE_BPF_OFFLOAD =
            "override_tether_enable_bpf_offload";

    /**
     * Use the old dnsmasq DHCP server for tethering instead of the framework implementation.
     */
    public static final String TETHER_ENABLE_LEGACY_DHCP_SERVER =
            "tether_enable_legacy_dhcp_server";

    public static final String USE_LEGACY_WIFI_P2P_DEDICATED_IP =
            "use_legacy_wifi_p2p_dedicated_ip";

    /**
     * Experiment flag to force choosing upstreams automatically.
     *
     * This setting is intended to help force-enable the feature on OEM devices that disabled it
     * via resource overlays, and later noticed issues. To that end, it overrides
     * config_tether_upstream_automatic when set to true.
     *
     * This flag is enabled if !=0 and less than the module APEX version: see
     * {@link DeviceConfigUtils#isTetheringFeatureEnabled}. It is also ignored after R, as later
     * devices should just set config_tether_upstream_automatic to true instead.
     */
    public static final String TETHER_FORCE_UPSTREAM_AUTOMATIC_VERSION =
            "tether_force_upstream_automatic_version";

    /**
     * Settings key to foce choosing usb functions for usb tethering.
     *
     * TODO: Remove this hard code string and make Settings#TETHER_FORCE_USB_FUNCTIONS as API.
     */
    public static final String TETHER_FORCE_USB_FUNCTIONS =
            "tether_force_usb_functions";

    /**
     * Experiment flag to enable TETHERING_WEAR.
     */
    public static final String TETHER_ENABLE_WEAR_TETHERING =
            "tether_enable_wear_tethering";

    public static final String TETHER_FORCE_RANDOM_PREFIX_BASE_SELECTION =
            "tether_force_random_prefix_base_selection";

    public static final String TETHER_ENABLE_SYNC_SM = "tether_enable_sync_sm";

    /**
     * Default value that used to periodic polls tether offload stats from tethering offload HAL
     * to make the data warnings work.
     */
    public static final int DEFAULT_TETHER_OFFLOAD_POLL_INTERVAL_MS = 5000;

    /** A flag for using synchronous or asynchronous state machine. */
    public static boolean USE_SYNC_SM = false;

    public final String[] tetherableUsbRegexs;
    public final String[] tetherableWifiRegexs;
    public final String[] tetherableWigigRegexs;
    public final String[] tetherableWifiP2pRegexs;
    public final String[] tetherableBluetoothRegexs;
    public final String[] tetherableNcmRegexs;
    public final boolean isDunRequired;
    public final boolean chooseUpstreamAutomatically;
    public final Collection<Integer> preferredUpstreamIfaceTypes;
    public final String[] legacyDhcpRanges;
    public final String[] defaultIPv4DNS;

    public final String[] provisioningApp;
    public final String provisioningAppNoUi;
    public final int provisioningCheckPeriod;
    public final String provisioningResponse;

    public final boolean isCarrierSupportTethering;
    public final boolean isCarrierConfigAffirmsEntitlementCheckRequired;

    public final int activeDataSubId;

    private final Dependencies mDeps;

    private final boolean mEnableLegacyDhcpServer;
    private final int mOffloadPollInterval;
    // TODO: Add to TetheringConfigurationParcel if required.
    private final boolean mEnableBpfOffload;
    private final boolean mEnableWifiP2pDedicatedIp;
    private final int mP2pLeasesSubnetPrefixLength;

    private final boolean mEnableWearTethering;
    private final boolean mRandomPrefixBase;

    private final int mUsbTetheringFunction;
    protected final ContentResolver mContentResolver;

    /**
     * A class wrapping dependencies of {@link TetheringConfiguration}, useful for testing.
     */
    @VisibleForTesting
    public static class Dependencies {
        boolean isFeatureEnabled(@NonNull Context context, @NonNull String name) {
            return DeviceConfigUtils.isTetheringFeatureEnabled(context, name);
        }

        boolean getDeviceConfigBoolean(@NonNull String namespace, @NonNull String name,
                boolean defaultValue) {
            return DeviceConfig.getBoolean(namespace, name, defaultValue);
        }

        /**
         * TETHER_FORCE_UPSTREAM_AUTOMATIC_VERSION is used to force enable the feature on specific
         * R devices. Just checking the flag value is enough since the flag has been pushed to
         * enable the feature on the old version and any new binary will always have a version
         * number newer than the flag.
         * This flag is wrongly configured in the connectivity namespace so this method reads the
         * flag value from the connectivity namespace. But the tethering module should use the
         * tethering namespace. This method can be removed after R EOL.
         */
        boolean isTetherForceUpstreamAutomaticFeatureEnabled() {
            final int flagValue = DeviceConfigUtils.getDeviceConfigPropertyInt(
                    NAMESPACE_CONNECTIVITY, TETHER_FORCE_UPSTREAM_AUTOMATIC_VERSION,
                    0 /* defaultValue */);
            return flagValue > 0;
        }
    }

    public TetheringConfiguration(@NonNull Context ctx, @NonNull SharedLog log, int id) {
        this(ctx, log, id, new Dependencies());
    }

    @VisibleForTesting
    public TetheringConfiguration(@NonNull Context ctx, @NonNull SharedLog log, int id,
            @NonNull Dependencies deps) {
        mDeps = deps;
        final SharedLog configLog = log.forSubComponent("config");

        activeDataSubId = id;
        Resources res = getResources(ctx, activeDataSubId);
        mContentResolver = ctx.getContentResolver();

        mUsbTetheringFunction = getUsbTetheringFunction(res);

        final String[] ncmRegexs = getResourceStringArray(res, R.array.config_tether_ncm_regexs);
        // If usb tethering use NCM and config_tether_ncm_regexs is not empty, use
        // config_tether_ncm_regexs for tetherableUsbRegexs.
        if (isUsingNcm() && (ncmRegexs.length != 0)) {
            tetherableUsbRegexs = ncmRegexs;
            tetherableNcmRegexs = EMPTY_STRING_ARRAY;
        } else {
            tetherableUsbRegexs = getResourceStringArray(res, R.array.config_tether_usb_regexs);
            tetherableNcmRegexs = ncmRegexs;
        }
        // TODO: Evaluate deleting this altogether now that Wi-Fi always passes
        // us an interface name. Careful consideration needs to be given to
        // implications for Settings and for provisioning checks.
        tetherableWifiRegexs = getResourceStringArray(res, R.array.config_tether_wifi_regexs);
        // TODO: Remove entire wigig code once tethering module no longer support R devices.
        tetherableWigigRegexs = SdkLevel.isAtLeastS()
                ? new String[0] : getResourceStringArray(res, R.array.config_tether_wigig_regexs);
        tetherableWifiP2pRegexs = getResourceStringArray(
                res, R.array.config_tether_wifi_p2p_regexs);
        tetherableBluetoothRegexs = getResourceStringArray(
                res, R.array.config_tether_bluetooth_regexs);

        isDunRequired = checkDunRequired(ctx);

        // Here is how automatic mode enable/disable support on different Android version:
        // - R   : can be enabled/disabled by resource config_tether_upstream_automatic.
        //         but can be force-enabled by flag TETHER_FORCE_UPSTREAM_AUTOMATIC_VERSION.
        // - S, T: can be enabled/disabled by resource config_tether_upstream_automatic.
        // - U+  : automatic mode only.
        final boolean forceAutomaticUpstream = SdkLevel.isAtLeastU() || (!SdkLevel.isAtLeastS()
                && mDeps.isTetherForceUpstreamAutomaticFeatureEnabled());
        chooseUpstreamAutomatically = forceAutomaticUpstream || getResourceBoolean(
                res, R.bool.config_tether_upstream_automatic, false /** defaultValue */);
        preferredUpstreamIfaceTypes = getUpstreamIfaceTypes(res, isDunRequired);

        legacyDhcpRanges = getLegacyDhcpRanges(res);
        defaultIPv4DNS = copy(DEFAULT_IPV4_DNS);
        mEnableBpfOffload = getEnableBpfOffload(res);
        mEnableLegacyDhcpServer = getEnableLegacyDhcpServer(res);

        provisioningApp = getResourceStringArray(res, R.array.config_mobile_hotspot_provision_app);
        provisioningAppNoUi = getResourceString(res,
                R.string.config_mobile_hotspot_provision_app_no_ui);
        provisioningCheckPeriod = getResourceInteger(res,
                R.integer.config_mobile_hotspot_provision_check_period,
                0 /* No periodic re-check */);
        provisioningResponse = getResourceString(res,
                R.string.config_mobile_hotspot_provision_response);

        PersistableBundle carrierConfigs = getCarrierConfig(ctx, activeDataSubId);
        isCarrierSupportTethering = carrierConfigAffirmsCarrierSupport(carrierConfigs);
        isCarrierConfigAffirmsEntitlementCheckRequired =
                carrierConfigAffirmsEntitlementCheckRequired(carrierConfigs);

        mOffloadPollInterval = getResourceInteger(res,
                R.integer.config_tether_offload_poll_interval,
                DEFAULT_TETHER_OFFLOAD_POLL_INTERVAL_MS);

        mEnableWifiP2pDedicatedIp = getResourceBoolean(res,
                R.bool.config_tether_enable_legacy_wifi_p2p_dedicated_ip,
                false /* defaultValue */);

        mP2pLeasesSubnetPrefixLength = getP2pLeasesSubnetPrefixLengthFromRes(res, configLog);

        mEnableWearTethering = shouldEnableWearTethering(ctx);

        mRandomPrefixBase = mDeps.isFeatureEnabled(ctx, TETHER_FORCE_RANDOM_PREFIX_BASE_SELECTION);

        configLog.log(toString());
    }

    private int getP2pLeasesSubnetPrefixLengthFromRes(final Resources res, final SharedLog log) {
        if (!mEnableWifiP2pDedicatedIp) return 0;

        int prefixLength = getResourceInteger(res,
                R.integer.config_p2p_leases_subnet_prefix_length, 0 /* default value */);

        // DhcpLeaseRepository ignores the first and last addresses of the range so the max prefix
        // length is 30.
        if (prefixLength < 0 || prefixLength > 30) {
            log.e("Invalid p2p leases subnet prefix length configuration: " + prefixLength);
            return 0;
        }

        return prefixLength;
    }

    /** Check whether using legacy dhcp server. */
    public boolean useLegacyDhcpServer() {
        return mEnableLegacyDhcpServer;
    }

    /** Check whether using ncm for usb tethering */
    public boolean isUsingNcm() {
        return mUsbTetheringFunction == TETHER_USB_NCM_FUNCTION;
    }

    /** Check whether input interface belong to usb.*/
    public boolean isUsb(String iface) {
        return matchesDownstreamRegexs(iface, tetherableUsbRegexs);
    }

    /** Check whether input interface belong to wifi.*/
    public boolean isWifi(String iface) {
        return matchesDownstreamRegexs(iface, tetherableWifiRegexs);
    }

    /** Check whether input interface belong to wigig.*/
    public boolean isWigig(String iface) {
        return matchesDownstreamRegexs(iface, tetherableWigigRegexs);
    }

    /** Check whether this interface is Wifi P2P interface. */
    public boolean isWifiP2p(String iface) {
        return matchesDownstreamRegexs(iface, tetherableWifiP2pRegexs);
    }

    /** Check whether using legacy mode for wifi P2P. */
    public boolean isWifiP2pLegacyTetheringMode() {
        return (tetherableWifiP2pRegexs == null || tetherableWifiP2pRegexs.length == 0);
    }

    /** Check whether input interface belong to bluetooth.*/
    public boolean isBluetooth(String iface) {
        return matchesDownstreamRegexs(iface, tetherableBluetoothRegexs);
    }

    /** Check if interface is ncm */
    public boolean isNcm(String iface) {
        return matchesDownstreamRegexs(iface, tetherableNcmRegexs);
    }

    /** Check whether no ui entitlement application is available.*/
    public boolean hasMobileHotspotProvisionApp() {
        return !TextUtils.isEmpty(provisioningAppNoUi);
    }

    /** Check whether dedicated wifi p2p address is enabled. */
    public boolean shouldEnableWifiP2pDedicatedIp() {
        return mEnableWifiP2pDedicatedIp;
    }

    /**
     * Get subnet prefix length of dhcp leases for wifi p2p.
     * This feature only support when wifi p2p use dedicated address. If
     * #shouldEnableWifiP2pDedicatedIp is false, this method would always return 0.
     */
    public int getP2pLeasesSubnetPrefixLength() {
        return mP2pLeasesSubnetPrefixLength;
    }

    /** Returns true if wearable device tethering is enabled. */
    public boolean isWearTetheringEnabled() {
        return mEnableWearTethering;
    }

    public boolean isRandomPrefixBaseEnabled() {
        return mRandomPrefixBase;
    }

    /**
     * Check whether sync SM is enabled then set it to USE_SYNC_SM. This should be called once
     * when tethering is created. Otherwise if the flag is pushed while tethering is enabled,
     * then it's possible for some IpServer(s) running the new sync state machine while others
     * use the async state machine.
     */
    public void readEnableSyncSM(final Context ctx) {
        USE_SYNC_SM = mDeps.isFeatureEnabled(ctx, TETHER_ENABLE_SYNC_SM);
    }

    /** Does the dumping.*/
    public void dump(PrintWriter pw) {
        pw.print("activeDataSubId: ");
        pw.println(activeDataSubId);

        dumpStringArray(pw, "tetherableUsbRegexs", tetherableUsbRegexs);
        dumpStringArray(pw, "tetherableWifiRegexs", tetherableWifiRegexs);
        dumpStringArray(pw, "tetherableWifiP2pRegexs", tetherableWifiP2pRegexs);
        dumpStringArray(pw, "tetherableBluetoothRegexs", tetherableBluetoothRegexs);
        dumpStringArray(pw, "tetherableNcmRegexs", tetherableNcmRegexs);

        pw.print("isDunRequired: ");
        pw.println(isDunRequired);

        pw.print("chooseUpstreamAutomatically: ");
        pw.println(chooseUpstreamAutomatically);
        pw.print("legacyPreredUpstreamIfaceTypes: ");
        pw.println(Arrays.toString(toIntArray(preferredUpstreamIfaceTypes)));

        dumpStringArray(pw, "legacyDhcpRanges", legacyDhcpRanges);
        dumpStringArray(pw, "defaultIPv4DNS", defaultIPv4DNS);

        pw.print("offloadPollInterval: ");
        pw.println(mOffloadPollInterval);

        dumpStringArray(pw, "provisioningApp", provisioningApp);
        pw.print("provisioningAppNoUi: ");
        pw.println(provisioningAppNoUi);

        pw.println("isCarrierSupportTethering: " + isCarrierSupportTethering);
        pw.println("isCarrierConfigAffirmsEntitlementCheckRequired: "
                + isCarrierConfigAffirmsEntitlementCheckRequired);

        pw.print("enableBpfOffload: ");
        pw.println(mEnableBpfOffload);

        pw.print("enableLegacyDhcpServer: ");
        pw.println(mEnableLegacyDhcpServer);

        pw.print("enableWifiP2pDedicatedIp: ");
        pw.println(mEnableWifiP2pDedicatedIp);

        pw.print("p2pLeasesSubnetPrefixLength: ");
        pw.println(mP2pLeasesSubnetPrefixLength);

        pw.print("enableWearTethering: ");
        pw.println(mEnableWearTethering);

        pw.print("mUsbTetheringFunction: ");
        pw.println(isUsingNcm() ? "NCM" : "RNDIS");

        pw.print("mRandomPrefixBase: ");
        pw.println(mRandomPrefixBase);

        pw.print("USE_SYNC_SM: ");
        pw.println(USE_SYNC_SM);
    }

    /** Returns the string representation of this object.*/
    public String toString() {
        final StringJoiner sj = new StringJoiner(" ");
        sj.add(String.format("activeDataSubId:%d", activeDataSubId));
        sj.add(String.format("tetherableUsbRegexs:%s", makeString(tetherableUsbRegexs)));
        sj.add(String.format("tetherableWifiRegexs:%s", makeString(tetherableWifiRegexs)));
        sj.add(String.format("tetherableWifiP2pRegexs:%s", makeString(tetherableWifiP2pRegexs)));
        sj.add(String.format("tetherableBluetoothRegexs:%s",
                makeString(tetherableBluetoothRegexs)));
        sj.add(String.format("isDunRequired:%s", isDunRequired));
        sj.add(String.format("chooseUpstreamAutomatically:%s", chooseUpstreamAutomatically));
        sj.add(String.format("offloadPollInterval:%d", mOffloadPollInterval));
        sj.add(String.format("preferredUpstreamIfaceTypes:%s",
                toIntArray(preferredUpstreamIfaceTypes)));
        sj.add(String.format("provisioningApp:%s", makeString(provisioningApp)));
        sj.add(String.format("provisioningAppNoUi:%s", provisioningAppNoUi));
        sj.add(String.format("isCarrierSupportTethering:%s", isCarrierSupportTethering));
        sj.add(String.format("isCarrierConfigAffirmsEntitlementCheckRequired:%s",
                isCarrierConfigAffirmsEntitlementCheckRequired));
        sj.add(String.format("enableBpfOffload:%s", mEnableBpfOffload));
        sj.add(String.format("enableLegacyDhcpServer:%s", mEnableLegacyDhcpServer));
        sj.add(String.format("enableWearTethering:%s", mEnableWearTethering));
        return String.format("TetheringConfiguration{%s}", sj.toString());
    }

    private static void dumpStringArray(PrintWriter pw, String label, String[] values) {
        pw.print(label);
        pw.print(": ");

        if (values != null) {
            final StringJoiner sj = new StringJoiner(", ", "[", "]");
            for (String value : values) sj.add(value);
            pw.print(sj.toString());
        } else {
            pw.print("null");
        }

        pw.println();
    }

    private static String makeString(String[] strings) {
        if (strings == null) return "null";
        final StringJoiner sj = new StringJoiner(",", "[", "]");
        for (String s : strings) sj.add(s);
        return sj.toString();
    }

    /** Check whether dun is required. */
    public static boolean checkDunRequired(Context ctx) {
        return false;
    }

    public int getOffloadPollInterval() {
        return mOffloadPollInterval;
    }

    public boolean isBpfOffloadEnabled() {
        return mEnableBpfOffload;
    }

    private int getUsbTetheringFunction(Resources res) {
        final int valueFromRes = getResourceInteger(res, R.integer.config_tether_usb_functions,
                TETHER_USB_RNDIS_FUNCTION /* defaultValue */);
        return getSettingsIntValue(TETHER_FORCE_USB_FUNCTIONS, valueFromRes);
    }

    private int getSettingsIntValue(final String name, final int defaultValue) {
        final String value = getSettingsValue(name);
        try {
            return value != null ? Integer.parseInt(value) : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @VisibleForTesting
    protected String getSettingsValue(final String name) {
        return Settings.Global.getString(mContentResolver, name);
    }

    private static Collection<Integer> getUpstreamIfaceTypes(Resources res, boolean dunRequired) {
        final int[] ifaceTypes = res.getIntArray(R.array.config_tether_upstream_types);
        final ArrayList<Integer> upstreamIfaceTypes = new ArrayList<>(ifaceTypes.length);
        for (int i : ifaceTypes) {
            switch (i) {
                case TYPE_MOBILE:
                case TYPE_MOBILE_HIPRI:
                    if (dunRequired) continue;
                    break;
                case TYPE_MOBILE_DUN:
                    if (!dunRequired) continue;
                    break;
            }
            upstreamIfaceTypes.add(i);
        }

        // Fix up upstream interface types for DUN or mobile. NOTE: independent
        // of the value of |dunRequired|, cell data of one form or another is
        // *always* an upstream, regardless of the upstream interface types
        // specified by configuration resources.
        if (dunRequired) {
            appendIfNotPresent(upstreamIfaceTypes, TYPE_MOBILE_DUN);
        } else {
            // Do not modify if a cellular interface type is already present in the
            // upstream interface types. Add TYPE_MOBILE and TYPE_MOBILE_HIPRI if no
            // cellular interface types are found in the upstream interface types.
            // This preserves backwards compatibility and prevents the DUN and default
            // mobile types incorrectly appearing together, which could happen on
            // previous releases in the common case where checkDunRequired returned
            // DUN_UNSPECIFIED.
            if (!containsOneOf(upstreamIfaceTypes, TYPE_MOBILE, TYPE_MOBILE_HIPRI)) {
                upstreamIfaceTypes.add(TYPE_MOBILE);
                upstreamIfaceTypes.add(TYPE_MOBILE_HIPRI);
            }
        }

        // Always make sure our good friend Ethernet is present.
        // TODO: consider unilaterally forcing this at the front.
        prependIfNotPresent(upstreamIfaceTypes, TYPE_ETHERNET);

        return upstreamIfaceTypes;
    }

    private static boolean matchesDownstreamRegexs(String iface, String[] regexs) {
        for (String regex : regexs) {
            if (iface.matches(regex)) return true;
        }
        return false;
    }

    private static String[] getLegacyDhcpRanges(Resources res) {
        final String[] fromResource = getResourceStringArray(res, R.array.config_tether_dhcp_range);
        if ((fromResource.length > 0) && (fromResource.length % 2 == 0)) {
            return fromResource;
        }
        return copy(LEGACY_DHCP_DEFAULT_RANGE);
    }

    private static String getResourceString(Resources res, final int resId) {
        try {
            return res.getString(resId);
        } catch (Resources.NotFoundException e) {
            return "";
        }
    }

    private static boolean getResourceBoolean(Resources res, int resId, boolean defaultValue) {
        try {
            return res.getBoolean(resId);
        } catch (Resources.NotFoundException e404) {
            return defaultValue;
        }
    }

    private static String[] getResourceStringArray(Resources res, int resId) {
        try {
            final String[] strArray = res.getStringArray(resId);
            return (strArray != null) ? strArray : EMPTY_STRING_ARRAY;
        } catch (Resources.NotFoundException e404) {
            return EMPTY_STRING_ARRAY;
        }
    }

    private static int getResourceInteger(Resources res, int resId, int defaultValue) {
        try {
            return res.getInteger(resId);
        } catch (Resources.NotFoundException e404) {
            return defaultValue;
        }
    }

    private boolean getEnableBpfOffload(final Resources res) {
        // Get BPF offload config
        // Priority 1: Device config
        // Priority 2: Resource config
        // Priority 3: Default value
        final boolean defaultValue = getResourceBoolean(
                res, R.bool.config_tether_enable_bpf_offload, true /** default value */);

        return getDeviceConfigBoolean(OVERRIDE_TETHER_ENABLE_BPF_OFFLOAD, defaultValue);
    }

    private boolean getEnableLegacyDhcpServer(final Resources res) {
        return getResourceBoolean(
                res, R.bool.config_tether_enable_legacy_dhcp_server, false /** defaultValue */)
                || getDeviceConfigBoolean(
                TETHER_ENABLE_LEGACY_DHCP_SERVER, false /** defaultValue */);
    }

    private boolean shouldEnableWearTethering(Context context) {
        return SdkLevel.isAtLeastT()
            && mDeps.isFeatureEnabled(context, TETHER_ENABLE_WEAR_TETHERING);
    }

    private boolean getDeviceConfigBoolean(final String name, final boolean defaultValue) {
        return mDeps.getDeviceConfigBoolean(NAMESPACE_CONNECTIVITY, name, defaultValue);
    }

    private Resources getResources(Context ctx, int subId) {
        if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            return getResourcesForSubIdWrapper(ctx, subId);
        } else {
            return ctx.getResources();
        }
    }

    @VisibleForTesting
    protected Resources getResourcesForSubIdWrapper(Context ctx, int subId) {
        return SubscriptionManager.getResourcesForSubId(ctx, subId);
    }

    private static String[] copy(String[] strarray) {
        return Arrays.copyOf(strarray, strarray.length);
    }

    private static void prependIfNotPresent(ArrayList<Integer> list, int value) {
        if (list.contains(value)) return;
        list.add(0, value);
    }

    private static void appendIfNotPresent(ArrayList<Integer> list, int value) {
        if (list.contains(value)) return;
        list.add(value);
    }

    private static boolean containsOneOf(ArrayList<Integer> list, Integer... values) {
        for (Integer value : values) {
            if (list.contains(value)) return true;
        }
        return false;
    }

    private static int[] toIntArray(Collection<Integer> values) {
        final int[] result = new int[values.size()];
        int index = 0;
        for (Integer value : values) {
            result[index++] = value;
        }
        return result;
    }

    private static boolean carrierConfigAffirmsEntitlementCheckRequired(
            PersistableBundle carrierConfig) {
        if (carrierConfig == null) {
            return true;
        }
        return carrierConfig.getBoolean(
                CarrierConfigManager.KEY_REQUIRE_ENTITLEMENT_CHECKS_BOOL, true);
    }

    private static boolean carrierConfigAffirmsCarrierSupport(PersistableBundle carrierConfig) {
        if (!SdkLevel.isAtLeastT() || carrierConfig == null) {
            return true;
        }
        return carrierConfig.getBoolean(KEY_CARRIER_SUPPORTS_TETHERING_BOOL, true);
    }

    /**
     * Get carrier configuration bundle.
     */
    public static PersistableBundle getCarrierConfig(Context context, int activeDataSubId) {
        final CarrierConfigManager configManager =
                context.getSystemService(CarrierConfigManager.class);
        if (configManager == null) {
            return null;
        }

        final PersistableBundle carrierConfig = configManager.getConfigForSubId(activeDataSubId);
        if (CarrierConfigManager.isConfigForIdentifiedCarrier(carrierConfig)) {
            return carrierConfig;
        }
        return null;
    }

    /**
     * Convert this TetheringConfiguration to a TetheringConfigurationParcel.
     */
    public TetheringConfigurationParcel toStableParcelable() {
        final TetheringConfigurationParcel parcel = new TetheringConfigurationParcel();
        parcel.tetherableUsbRegexs = tetherableUsbRegexs;
        parcel.tetherableWifiRegexs = tetherableWifiRegexs;
        parcel.tetherableBluetoothRegexs = tetherableBluetoothRegexs;
        parcel.legacyDhcpRanges = legacyDhcpRanges;
        parcel.provisioningApp = provisioningApp;
        parcel.provisioningAppNoUi = provisioningAppNoUi;

        return parcel;
    }
}
