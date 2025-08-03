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

package com.android.telephony.qns;

import static android.telephony.ims.ImsMmTelManager.WIFI_MODE_CELLULAR_PREFERRED;
import static android.telephony.ims.ImsMmTelManager.WIFI_MODE_WIFI_PREFERRED;

import static com.android.telephony.qns.wfc.WfcCarrierConfigManager.CONFIG_DEFAULT_VOWIFI_REGISTATION_TIMER;
import static com.android.telephony.qns.wfc.WfcCarrierConfigManager.KEY_QNS_VOWIFI_REGISTATION_TIMER_FOR_VOWIFI_ACTIVATION_INT;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.PersistableBundle;
import android.os.SystemClock;
import android.telephony.AccessNetworkConstants;
import android.telephony.Annotation.NetCapability;
import android.telephony.CarrierConfigManager;
import android.telephony.ServiceState;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.data.ApnSetting;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** This class contains QualifiedNetworksService specific utility functions */
class QnsUtils {

    private static final String CARRIER_ID_PREFIX = "carrier_config_carrierid_";

    /**
     * Get supported APN types
     *
     * @param apnTypeBitmask bitmask of APN types.
     * @return list of APN types
     */
    @ApnSetting.ApnType
    static List<Integer> getApnTypes(int apnTypeBitmask) {
        List<Integer> types = new ArrayList<>();

        if ((apnTypeBitmask & ApnSetting.TYPE_DEFAULT) == ApnSetting.TYPE_DEFAULT) {
            types.add(ApnSetting.TYPE_DEFAULT);
            apnTypeBitmask &= ~ApnSetting.TYPE_DEFAULT;
        }
        while (apnTypeBitmask != 0) {
            int highestApnTypeBit = Integer.highestOneBit(apnTypeBitmask);
            types.add(highestApnTypeBit);
            apnTypeBitmask &= ~highestApnTypeBit;
        }
        return types;
    }

    /**
     * Get names of AccessNetworkTypes
     *
     * @param accessNetworkTypes list of accessNetworkType
     * @return String of AccessNetworkTypes name
     */
    static String getStringAccessNetworkTypes(List<Integer> accessNetworkTypes) {
        if (accessNetworkTypes == null || accessNetworkTypes.size() == 0) {
            return "[empty]";
        }
        List<String> types = new ArrayList<>();
        for (Integer net : accessNetworkTypes) {
            types.add(QnsConstants.accessNetworkTypeToString(net));
        }
        return TextUtils.join("|", types);
    }

    /**
     * Get a subId per slot id.
     *
     * @param context Context
     * @param slotId slot id.
     * @return Subscription id per slot id.
     */
    static int getSubId(Context context, int slotId) {
        try {
            return getSubscriptionInfo(context, slotId).getSubscriptionId();
        } catch (IllegalStateException e) {
            return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        }
    }

    /**
     * This method validates the slot index.
     *
     * @param context Context
     * @param slotIndex slot index
     * @return returns true if slotIndex is valid; otherwise false.
     */
    static boolean isValidSlotIndex(Context context, int slotIndex) {
        TelephonyManager tm = context.getSystemService(TelephonyManager.class);
        return slotIndex >= 0 && tm != null && slotIndex < tm.getActiveModemCount();
    }

    static boolean isDefaultDataSubs(int slotId) {
        int ddsSlotId =
                SubscriptionManager.getSlotIndex(
                        SubscriptionManager.getDefaultDataSubscriptionId());
        if (ddsSlotId != SubscriptionManager.INVALID_SIM_SLOT_INDEX) {
            return ddsSlotId == slotId;
        }
        return false;
    }

    static long getSystemElapsedRealTime() {
        return SystemClock.elapsedRealtime();
    }

    private static SubscriptionInfo getSubscriptionInfo(Context context, int slotId)
            throws IllegalStateException {
        SubscriptionManager sm = context.getSystemService(SubscriptionManager.class);
        SubscriptionInfo info = sm.getActiveSubscriptionInfoForSimSlotIndex(slotId);

        if (info == null) {
            throw new IllegalStateException("Subscription info is null.");
        }

        return info;
    }

    /** isCrossSimCallingEnabled */
    public static boolean isCrossSimCallingEnabled(QnsImsManager imsManager) {
        try {
            return imsManager.isCrossSimCallingEnabled();
        } catch (Exception e) {
            // Fail to query Cross-SIM calling setting, just return false to avoid an exception.
        }
        return false;
    }

    /** isWfcEnabled */
    public static boolean isWfcEnabled(
            QnsImsManager imsManager, QnsProvisioningListener listener, boolean roaming) {
        try {
            boolean bWfcEnabledByUser;
            boolean bWfcEnabledByPlatform = imsManager.isWfcEnabledByPlatform();
            boolean bWfcProvisionedOnDevice = imsManager.isWfcProvisionedOnDevice();
            if (roaming) {
                bWfcEnabledByUser = imsManager.isWfcRoamingEnabledByUser();
                try {
                    boolean bWfcRoamingEnabled =
                            listener.getLastProvisioningWfcRoamingEnabledInfo();
                    bWfcEnabledByUser = bWfcEnabledByUser && (bWfcRoamingEnabled);
                } catch (Exception e) {
                    log("got exception e:" + e);
                }
            } else {
                bWfcEnabledByUser = imsManager.isWfcEnabledByUser();
            }
            log(
                    "isWfcEnabled slot:"
                            + imsManager.getSlotIndex()
                            + " byUser:"
                            + bWfcEnabledByUser
                            + " byPlatform:"
                            + bWfcEnabledByPlatform
                            + " ProvisionedOnDevice:"
                            + bWfcProvisionedOnDevice
                            + " roam:"
                            + roaming);
            return bWfcEnabledByUser && bWfcEnabledByPlatform && bWfcProvisionedOnDevice;
        } catch (Exception e) {
            loge("isWfcEnabled exception:" + e);
            // Fail to query, just return false to avoid an exception.
        }
        return false;
    }

    /** isWfcEnabledByPlatform */
    public static boolean isWfcEnabledByPlatform(QnsImsManager imsManager) {
        try {
            boolean bWfcEnabledByPlatform = imsManager.isWfcEnabledByPlatform();
            log(
                    "isWfcEnabledByPlatform:"
                            + bWfcEnabledByPlatform
                            + " slot:"
                            + imsManager.getSlotIndex());
            return bWfcEnabledByPlatform;
        } catch (Exception e) {
            loge("isWfcEnabledByPlatform exception:" + e);
            // Fail to query, just return false to avoid an exception.
        }
        return false;
    }

    /** getWfcMode */
    public static int getWfcMode(QnsImsManager imsManager, boolean roaming) {
        try {
            int wfcMode = imsManager.getWfcMode(roaming);
            log(
                    "getWfcMode slot:"
                            + imsManager.getSlotIndex()
                            + " wfcMode:"
                            + wfcMode
                            + " roaming:"
                            + roaming);
            return wfcMode;
        } catch (Exception e) {
            // Fail to query, just return false to avoid an exception.
        }
        return roaming ? WIFI_MODE_WIFI_PREFERRED : WIFI_MODE_CELLULAR_PREFERRED;
    }

    /**
     * This method provides the access network type for the given data registration state and
     * Network Type. It will return value as UNKNOWN(0) if registration state is not in service.
     *
     * @param dataRegState Data registration state.
     * @param dataNetworkType Data network type
     * @return int value of the AccessNetworkType mapped to NetworkType. And UNKNOWN(0) if
     *     registration state is not in service.
     */
    static int getCellularAccessNetworkType(int dataRegState, int dataNetworkType) {
        if (dataRegState == ServiceState.STATE_IN_SERVICE) {
            return QnsConstants.networkTypeToAccessNetworkType(dataNetworkType);
        }
        return AccessNetworkConstants.AccessNetworkType.UNKNOWN;
    }

    static boolean isWifiCallingAvailable(Context context, int slotId) {
        try {
            int subId = QnsUtils.getSubId(context, slotId);
            TelephonyManager telephonyManager =
                    context.getSystemService(TelephonyManager.class).createForSubscriptionId(subId);
            return telephonyManager.isWifiCallingAvailable();
        } catch (Exception e) {
            loge("isWifiCallingAvailable has exception : " + e);
        }
        return false;
    }

    @AccessNetworkConstants.TransportType
    static int getTransportTypeFromAccessNetwork(
            @AccessNetworkConstants.RadioAccessNetworkType int accessNetwork) {
        if (accessNetwork == AccessNetworkConstants.AccessNetworkType.IWLAN) {
            return AccessNetworkConstants.TRANSPORT_TYPE_WLAN;
        } else if (accessNetwork != AccessNetworkConstants.AccessNetworkType.UNKNOWN) {
            return AccessNetworkConstants.TRANSPORT_TYPE_WWAN;
        }
        return AccessNetworkConstants.TRANSPORT_TYPE_INVALID;
    }

    /**
     * Get Set of network capabilities from string joined by {@code |}, space is ignored. If input
     * string contains unknown capability or malformatted(e.g. empty string), -1 is included in the
     * returned set.
     *
     * @param capabilitiesString capability strings joined by {@code |}
     * @return Set of capabilities
     */
    @NetCapability
    static Set<Integer> getNetworkCapabilitiesFromString(@NonNull String capabilitiesString) {
        // e.g. "IMS|" is not allowed
        if (!capabilitiesString.matches("(\\s*[a-zA-Z]+\\s*)(\\|\\s*[a-zA-Z]+\\s*)*")) {
            return Collections.singleton(-1);
        }
        return Arrays.stream(capabilitiesString.split("\\s*\\|\\s*"))
                .map(String::trim)
                .map(QnsUtils::getNetworkCapabilityFromString)
                .collect(Collectors.toSet());
    }

    /**
     * Returns another transport type.
     * @param transportType transport type
     * @return another transport type of input parameter
     */
    @AccessNetworkConstants.TransportType
    static int getOtherTransportType(@AccessNetworkConstants.TransportType int transportType) {
        if (transportType == AccessNetworkConstants.TRANSPORT_TYPE_WLAN) {
            return AccessNetworkConstants.TRANSPORT_TYPE_WWAN;
        } else if (transportType == AccessNetworkConstants.TRANSPORT_TYPE_WWAN) {
            return AccessNetworkConstants.TRANSPORT_TYPE_WLAN;
        }
        return AccessNetworkConstants.TRANSPORT_TYPE_INVALID;
    }

    /**
     * Convert network capabilities to string.
     *
     * <p>This is for debugging and logging purposes only.
     *
     * @param netCaps Network capabilities.
     * @return Network capabilities in string format.
     */
    @NonNull
    static String networkCapabilitiesToString(
            @NetCapability @Nullable Collection<Integer> netCaps) {
        if (netCaps == null || netCaps.isEmpty()) return "";
        return "["
                + netCaps.stream()
                        .map(QnsUtils::networkCapabilityToString)
                        .collect(Collectors.joining("|"))
                + "]";
    }

    /**
     * Convert a network capability to string.
     *
     * <p>This is for debugging and logging purposes only.
     *
     * @param netCap Network capability.
     * @return Network capability in string format.
     */
    @NonNull
    static String networkCapabilityToString(@NetCapability int netCap) {
        switch (netCap) {
            case NetworkCapabilities.NET_CAPABILITY_MMS:
                return "MMS";
            case NetworkCapabilities.NET_CAPABILITY_SUPL:
                return "SUPL";
            case NetworkCapabilities.NET_CAPABILITY_DUN:
                return "DUN";
            case NetworkCapabilities.NET_CAPABILITY_FOTA:
                return "FOTA";
            case NetworkCapabilities.NET_CAPABILITY_IMS:
                return "IMS";
            case NetworkCapabilities.NET_CAPABILITY_CBS:
                return "CBS";
            case NetworkCapabilities.NET_CAPABILITY_WIFI_P2P:
                return "WIFI_P2P";
            case NetworkCapabilities.NET_CAPABILITY_IA:
                return "IA";
            case NetworkCapabilities.NET_CAPABILITY_RCS:
                return "RCS";
            case NetworkCapabilities.NET_CAPABILITY_XCAP:
                return "XCAP";
            case NetworkCapabilities.NET_CAPABILITY_EIMS:
                return "EIMS";
            case NetworkCapabilities.NET_CAPABILITY_NOT_METERED:
                return "NOT_METERED";
            case NetworkCapabilities.NET_CAPABILITY_INTERNET:
                return "INTERNET";
            case NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED:
                return "NOT_RESTRICTED";
            case NetworkCapabilities.NET_CAPABILITY_TRUSTED:
                return "TRUSTED";
            case NetworkCapabilities.NET_CAPABILITY_NOT_VPN:
                return "NOT_VPN";
            case NetworkCapabilities.NET_CAPABILITY_VALIDATED:
                return "VALIDATED";
            case NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL:
                return "CAPTIVE_PORTAL";
            case NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING:
                return "NOT_ROAMING";
            case NetworkCapabilities.NET_CAPABILITY_FOREGROUND:
                return "FOREGROUND";
            case NetworkCapabilities.NET_CAPABILITY_NOT_CONGESTED:
                return "NOT_CONGESTED";
            case NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED:
                return "NOT_SUSPENDED";
            case NetworkCapabilities.NET_CAPABILITY_OEM_PAID:
                return "OEM_PAID";
            case NetworkCapabilities.NET_CAPABILITY_MCX:
                return "MCX";
            case NetworkCapabilities.NET_CAPABILITY_PARTIAL_CONNECTIVITY:
                return "PARTIAL_CONNECTIVITY";
            case NetworkCapabilities.NET_CAPABILITY_TEMPORARILY_NOT_METERED:
                return "TEMPORARILY_NOT_METERED";
            case NetworkCapabilities.NET_CAPABILITY_OEM_PRIVATE:
                return "OEM_PRIVATE";
            case NetworkCapabilities.NET_CAPABILITY_VEHICLE_INTERNAL:
                return "VEHICLE_INTERNAL";
            case NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED:
                return "NOT_VCN_MANAGED";
            case NetworkCapabilities.NET_CAPABILITY_ENTERPRISE:
                return "ENTERPRISE";
            case NetworkCapabilities.NET_CAPABILITY_VSIM:
                return "VSIM";
            case NetworkCapabilities.NET_CAPABILITY_BIP:
                return "BIP";
            case NetworkCapabilities.NET_CAPABILITY_HEAD_UNIT:
                return "HEAD_UNIT";
            case NetworkCapabilities.NET_CAPABILITY_MMTEL:
                return "MMTEL";
            case NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_LATENCY:
                return "PRIORITIZE_LATENCY";
            case NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_BANDWIDTH:
                return "PRIORITIZE_BANDWIDTH";
            default:
                return "Unknown(" + netCap + ")";
        }
    }

    /**
     * Get the network capability from the string.
     *
     * @param capabilityString The capability in string format
     * @return The network capability. -1 if not found.
     */
    @NetCapability
    static int getNetworkCapabilityFromString(@NonNull String capabilityString) {
        switch (capabilityString.toUpperCase(Locale.ROOT)) {
            case "MMS":
                return NetworkCapabilities.NET_CAPABILITY_MMS;
            case "SUPL":
                return NetworkCapabilities.NET_CAPABILITY_SUPL;
            case "DUN":
                return NetworkCapabilities.NET_CAPABILITY_DUN;
            case "FOTA":
                return NetworkCapabilities.NET_CAPABILITY_FOTA;
            case "IMS":
                return NetworkCapabilities.NET_CAPABILITY_IMS;
            case "CBS":
                return NetworkCapabilities.NET_CAPABILITY_CBS;
            case "XCAP":
                return NetworkCapabilities.NET_CAPABILITY_XCAP;
            case "EIMS":
                return NetworkCapabilities.NET_CAPABILITY_EIMS;
            case "INTERNET":
                return NetworkCapabilities.NET_CAPABILITY_INTERNET;
            case "MCX":
                return NetworkCapabilities.NET_CAPABILITY_MCX;
            case "VSIM":
                return NetworkCapabilities.NET_CAPABILITY_VSIM;
            case "BIP":
                return NetworkCapabilities.NET_CAPABILITY_BIP;
            case "ENTERPRISE":
                return NetworkCapabilities.NET_CAPABILITY_ENTERPRISE;
            case "PRIORITIZE_BANDWIDTH":
                return NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_BANDWIDTH;
            case "PRIORITIZE_LATENCY":
                return NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_LATENCY;
            default:
                return -1;
        }
    }

    /**
     * Get the network capability from the apn type.
     *
     * @param apnType apn type.
     * @return network capability
     */
    @NetCapability
    static int getNetCapabilityFromApnType(int apnType) {
        switch (apnType) {
            case ApnSetting.TYPE_IMS:
                return NetworkCapabilities.NET_CAPABILITY_IMS;
            case ApnSetting.TYPE_EMERGENCY:
                return NetworkCapabilities.NET_CAPABILITY_EIMS;
            case ApnSetting.TYPE_MMS:
                return NetworkCapabilities.NET_CAPABILITY_MMS;
            case ApnSetting.TYPE_XCAP:
                return NetworkCapabilities.NET_CAPABILITY_XCAP;
            case ApnSetting.TYPE_CBS:
                return NetworkCapabilities.NET_CAPABILITY_CBS;
            default:
                throw new IllegalArgumentException("Unsupported apnType: " + apnType);
        }
    }

    /**
     * Get the apn type from the network capability.
     *
     * @param netCapability network capability.
     * @return apn type.
     */
    static int getApnTypeFromNetCapability(@NetCapability int netCapability) {
        switch (netCapability) {
            case NetworkCapabilities.NET_CAPABILITY_IMS:
                return ApnSetting.TYPE_IMS;
            case NetworkCapabilities.NET_CAPABILITY_EIMS:
                return ApnSetting.TYPE_EMERGENCY;
            case NetworkCapabilities.NET_CAPABILITY_MMS:
                return ApnSetting.TYPE_MMS;
            case NetworkCapabilities.NET_CAPABILITY_XCAP:
                return ApnSetting.TYPE_XCAP;
            case NetworkCapabilities.NET_CAPABILITY_CBS:
                return ApnSetting.TYPE_CBS;
            default:
                throw new IllegalArgumentException("Unsupported netCapability: " + netCapability);
        }
    }

    /**
     * Convert a network capability to string.
     *
     * <p>This is for debugging and logging purposes only.
     *
     * @param netCapability Network capability.
     * @return Network capability in string format.
     */
    @NonNull
    static String getNameOfNetCapability(@NetCapability int netCapability) {
        switch (netCapability) {
            case NetworkCapabilities.NET_CAPABILITY_IMS:
                return "ims";
            case NetworkCapabilities.NET_CAPABILITY_EIMS:
                return "eims";
            case NetworkCapabilities.NET_CAPABILITY_MMS:
                return "mms";
            case NetworkCapabilities.NET_CAPABILITY_CBS:
                return "cbs";
            case NetworkCapabilities.NET_CAPABILITY_XCAP:
                return "xcap";
            default:
                throw new IllegalArgumentException("Unsupported netCapability: " + netCapability);
        }
    }

    /**
     * Get the network capability from the string.
     *
     * @param types string array of APN types.
     * @return list of Network Capabilities
     */
    @NetCapability
    static List<Integer> getNetCapabilitiesFromApnTypesString(@NonNull String[] types) {
        int apnTypesBitmask = 0;
        for (String str : types) {
            apnTypesBitmask |= ApnSetting.getApnTypeInt(str);
        }
        return getNetCapabilitiesFromApnTypeBitmask(apnTypesBitmask);
    }

    /**
     * Get a list of supported network capabilities from apnTypeBitmask
     *
     * @param apnTypeBitmask bitmask of APN types.
     * @return list of Network Capabilities
     */
    static List<Integer> getNetCapabilitiesFromApnTypeBitmask(int apnTypeBitmask) {
        List<Integer> netCapabilities = new ArrayList<>();
        List<Integer> apnTypes = getApnTypes(apnTypeBitmask);
        for (int apnType : apnTypes) {
            try {
                netCapabilities.add(getNetCapabilityFromApnType(apnType));
            } catch (IllegalArgumentException e) {
                continue;
            }
        }
        return netCapabilities;
    }

    static PersistableBundle readQnsDefaultConfigFromAssets(Context context, int qnsCarrierID) {

        if (qnsCarrierID == TelephonyManager.UNKNOWN_CARRIER_ID) {
            return null;
        }

        return readConfigFromAssets(context, CARRIER_ID_PREFIX + qnsCarrierID + "_");
    }

    static synchronized <T> T getConfig(
            PersistableBundle carrierConfigBundle,
            PersistableBundle assetConfigBundle,
            String key) {

        // TODO: PersistableBundle.get is deprecated.
        if (carrierConfigBundle == null || carrierConfigBundle.get(key) == null) {
            log("key not set in pb file: " + key);

            if (assetConfigBundle == null || assetConfigBundle.get(key) == null) {
                return (T) getDefaultValueForKey(key);
            } else {
                return (T) assetConfigBundle.get(key);
            }
        }
        return (T) carrierConfigBundle.get(key);
    }

    static synchronized <T> T getDefaultValueForKey(String key) {
        switch (key) {
            case QnsCarrierConfigManager.KEY_QNS_SUPPORT_WFC_DURING_AIRPLANE_MODE_BOOL:
            case QnsCarrierConfigManager.KEY_BLOCK_IPV6_ONLY_WIFI_BOOL:
            case CarrierConfigManager.ImsVoice.KEY_CARRIER_VOLTE_ROAMING_AVAILABLE_BOOL:
                return (T) Boolean.valueOf(true);
            case QnsCarrierConfigManager
                    .KEY_QNS_ALLOW_VIDEO_OVER_IWLAN_WITH_CELLULAR_LIMITED_CASE_BOOL:
            case QnsCarrierConfigManager.KEY_QNS_HO_GUARDING_BY_PREFERENCE_BOOL:
            case QnsCarrierConfigManager.KEY_QNS_SUPPORT_SERVICE_BARRING_CHECK_BOOL:
            case QnsCarrierConfigManager
                    .KEY_ROAM_TRANSPORT_TYPE_SELECTION_WITHOUT_SIGNAL_STRENGTH_BOOL:
            case QnsCarrierConfigManager.KEY_PREFER_CURRENT_TRANSPORT_TYPE_IN_VOICE_CALL_BOOL:
            case QnsCarrierConfigManager.KEY_POLICY_OVERRIDE_CELL_PREF_TO_IMS_PREF_HOME_BOOL:
            case QnsCarrierConfigManager
                    .KEY_QNS_ROVE_OUT_POLICY_WITH_WIFI_BAD_GUARDTIMER_CONDITIONS_BOOL:
            case QnsCarrierConfigManager.KEY_QNS_ALLOW_IMS_OVER_IWLAN_CELLULAR_LIMITED_CASE_BOOL:
            case QnsCarrierConfigManager.KEY_BLOCK_IWLAN_IN_INTERNATIONAL_ROAMING_WITHOUT_WWAN_BOOL:
            case QnsCarrierConfigManager
                    .KEY_IN_CALL_HO_DECISION_WLAN_TO_WWAN_WITHOUT_VOPS_CONDITION_BOOL:
                return (T) Boolean.valueOf(false);
            case QnsCarrierConfigManager.KEY_SIP_DIALOG_SESSION_POLICY_INT:
                return (T) Integer.valueOf(QnsConstants.SIP_DIALOG_SESSION_POLICY_NONE);
            case QnsCarrierConfigManager.KEY_QNS_CELLULAR_SS_THRESHOLDBACKHAUL_TIMER_MS_INT:
                return (T) Integer.valueOf(QnsConstants.KEY_DEFAULT_VALUE);
            case QnsCarrierConfigManager.KEY_QNS_WIFI_RSSI_THRESHOLDBACKHAUL_TIMER_MS_INT:
                return (T) Integer.valueOf(QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER);
            case QnsCarrierConfigManager.KEY_QNS_IMS_TRANSPORT_TYPE_INT:
                return (T) Integer.valueOf(QnsConstants.TRANSPORT_TYPE_ALLOWED_BOTH);
            case QnsCarrierConfigManager.KEY_QNS_MMS_TRANSPORT_TYPE_INT:
            case QnsCarrierConfigManager.KEY_QNS_CBS_TRANSPORT_TYPE_INT:
            case QnsCarrierConfigManager.KEY_QNS_SOS_TRANSPORT_TYPE_INT:
                return (T) Integer.valueOf(QnsConstants.TRANSPORT_TYPE_ALLOWED_WWAN);
            case QnsCarrierConfigManager.KEY_QNS_XCAP_RAT_PREFERENCE_INT:
            case QnsCarrierConfigManager.KEY_QNS_SOS_RAT_PREFERENCE_INT:
            case QnsCarrierConfigManager.KEY_QNS_MMS_RAT_PREFERENCE_INT:
            case QnsCarrierConfigManager.KEY_QNS_CBS_RAT_PREFERENCE_INT:
                return (T) Integer.valueOf(QnsConstants.RAT_PREFERENCE_DEFAULT);
            case KEY_QNS_VOWIFI_REGISTATION_TIMER_FOR_VOWIFI_ACTIVATION_INT:
                return (T) Integer.valueOf(CONFIG_DEFAULT_VOWIFI_REGISTATION_TIMER);
            case CarrierConfigManager.ImsVoice.KEY_VOICE_RTP_JITTER_THRESHOLD_MILLIS_INT:
            case CarrierConfigManager.ImsVoice.KEY_VOICE_RTP_PACKET_LOSS_RATE_THRESHOLD_INT:
                return (T) Integer.valueOf(QnsConstants.INVALID_VALUE);
            case QnsCarrierConfigManager.KEY_QNS_MEDIA_THRESHOLD_RTP_PACKET_LOSS_TIME_MILLIS_INT:
                return (T) Integer.valueOf(QnsConstants.KEY_DEFAULT_PACKET_LOSS_TIME_MILLIS);
            case CarrierConfigManager.ImsVoice.KEY_VOICE_RTP_INACTIVITY_TIME_THRESHOLD_MILLIS_LONG:
                return (T) Long.valueOf(QnsConstants.INVALID_VALUE);
            case QnsCarrierConfigManager
                    .KEY_QNS_IN_CALL_ROVEIN_ALLOWED_COUNT_AND_FALLBACK_REASON_INT_ARRAY:
                return (T)
                        new int[] {
                            QnsConstants.MAX_COUNT_INVALID, QnsConstants.FALLBACK_REASON_INVALID
                        };
            case QnsCarrierConfigManager
                    .KEY_WAITING_TIME_FOR_PREFERRED_TRANSPORT_WHEN_POWER_ON_INT_ARRAY:
            case QnsCarrierConfigManager.KEY_NON_IMS_WWAN_HYSTERESIS_TIMER_MS_INT_ARRAY:
            case QnsCarrierConfigManager.KEY_NON_IMS_WLAN_HYSTERESIS_TIMER_MS_INT_ARRAY:
                return (T)
                        new int[] {QnsConstants.KEY_DEFAULT_VALUE, QnsConstants.KEY_DEFAULT_VALUE};
            case QnsCarrierConfigManager.KEY_QNS_IMS_NETWORK_ENABLE_HO_HYSTERESIS_TIMER_INT:
                return (T) Integer.valueOf(QnsConstants.COVERAGE_BOTH);
            case QnsCarrierConfigManager
                    .KEY_QNS_HO_RESTRICT_TIME_WITH_LOW_RTP_QUALITY_MILLIS_INT_ARRAY:
                return (T)
                        new int[] {
                            QnsConstants.KEY_DEFAULT_IWLAN_AVOID_TIME_LOW_RTP_QUALITY_MILLIS,
                            QnsConstants.KEY_DEFAULT_WWAN_AVOID_TIME_LOW_RTP_QUALITY_MILLIS,
                        };
            case QnsCarrierConfigManager.KEY_IMS_WWAN_HYSTERESIS_TIMER_MS_INT_ARRAY:
            case QnsCarrierConfigManager.KEY_IMS_WLAN_HYSTERESIS_TIMER_MS_INT_ARRAY:
                return (T)
                        new int[] {
                            QnsConstants.KEY_DEFAULT_HYST_TIMER,
                            QnsConstants.KEY_DEFAULT_HYST_TIMER,
                            QnsConstants.KEY_DEFAULT_HYST_TIMER
                        };
            case QnsCarrierConfigManager.KEY_MINIMUM_HANDOVER_GUARDING_TIMER_MS_INT:
                return (T) Integer.valueOf(QnsConstants.CONFIG_DEFAULT_MIN_HANDOVER_GUARDING_TIMER);
            case QnsCarrierConfigManager
                    .KEY_CHOOSE_WFC_PREFERRED_TRANSPORT_IN_BOTH_BAD_CONDITION_INT_ARRAY:
                return (T) new int[] {};
            case QnsCarrierConfigManager.KEY_IMS_CELLULAR_ALLOWED_RAT_STRING_ARRAY:
                return (T) new String[] {"LTE", "NR"};
            case QnsCarrierAnspSupportConfig.KEY_IDLE_NGRAN_SSRSRP_INT_ARRAY:
            case QnsCarrierAnspSupportConfig.KEY_VOICE_NGRAN_SSRSRP_INT_ARRAY:
            case QnsCarrierAnspSupportConfig.KEY_VIDEO_NGRAN_SSRSRP_INT_ARRAY:
                return (T)
                        new int[] {
                            QnsConstants.KEY_DEFAULT_THRESHOLD_SSRSRP_GOOD,
                            QnsConstants.KEY_DEFAULT_THRESHOLD_SSRSRP_BAD,
                            QnsCarrierConfigManager.QnsConfigArray.INVALID
                        };
            case QnsCarrierAnspSupportConfig.KEY_IDLE_EUTRAN_RSRP_INT_ARRAY:
            case QnsCarrierAnspSupportConfig.KEY_VOICE_EUTRAN_RSRP_INT_ARRAY:
            case QnsCarrierAnspSupportConfig.KEY_VIDEO_EUTRAN_RSRP_INT_ARRAY:
                return (T)
                        new int[] {
                            QnsConstants.KEY_DEFAULT_THRESHOLD_RSRP_GOOD,
                            QnsConstants.KEY_DEFAULT_THRESHOLD_RSRP_BAD,
                            QnsCarrierConfigManager.QnsConfigArray.INVALID
                        };
            case QnsCarrierAnspSupportConfig.KEY_IDLE_UTRAN_RSCP_INT_ARRAY:
            case QnsCarrierAnspSupportConfig.KEY_VOICE_UTRAN_RSCP_INT_ARRAY:
            case QnsCarrierAnspSupportConfig.KEY_VIDEO_UTRAN_RSCP_INT_ARRAY:
                return (T)
                        new int[] {
                            QnsConstants.KEY_DEFAULT_THRESHOLD_RSCP_GOOD,
                            QnsConstants.KEY_DEFAULT_THRESHOLD_RSCP_BAD,
                            QnsCarrierConfigManager.QnsConfigArray.INVALID
                        };
            case QnsCarrierAnspSupportConfig.KEY_IDLE_GERAN_RSSI_INT_ARRAY:
            case QnsCarrierAnspSupportConfig.KEY_VOICE_GERAN_RSSI_INT_ARRAY:
            case QnsCarrierAnspSupportConfig.KEY_VIDEO_GERAN_RSSI_INT_ARRAY:
                return (T)
                        new int[] {
                            QnsConstants.KEY_DEFAULT_THRESHOLD_GERAN_RSSI_GOOD,
                            QnsConstants.KEY_DEFAULT_THRESHOLD_GERAN_RSSI_BAD,
                            QnsCarrierConfigManager.QnsConfigArray.INVALID
                        };
            case QnsCarrierAnspSupportConfig.KEY_IDLE_WIFI_RSSI_INT_ARRAY:
            case QnsCarrierAnspSupportConfig.KEY_VOICE_WIFI_RSSI_INT_ARRAY:
            case QnsCarrierAnspSupportConfig.KEY_VIDEO_WIFI_RSSI_INT_ARRAY:
                return (T)
                        new int[] {
                            QnsConstants.KEY_DEFAULT_THRESHOLD_WIFI_RSSI_GOOD,
                            QnsConstants.KEY_DEFAULT_THRESHOLD_WIFI_RSSI_BAD
                        };
            case QnsCarrierAnspSupportConfig.KEY_OVERRIDE_WIFI_PREF_IDLE_WIFI_RSSI_INT_ARRAY:
            case QnsCarrierAnspSupportConfig.KEY_OVERRIDE_WIFI_PREF_VOICE_WIFI_RSSI_INT_ARRAY:
            case QnsCarrierAnspSupportConfig.KEY_OVERRIDE_WIFI_PREF_VIDEO_WIFI_RSSI_INT_ARRAY:
            case CarrierConfigManager.Ims.KEY_IMS_PDN_ENABLED_IN_NO_VOPS_SUPPORT_INT_ARRAY:
                return (T) new int[] {};
            case QnsCarrierConfigManager.KEY_QNS_WLAN_RTT_BACKHAUL_CHECK_ON_ICMP_PING_STRING:
                return (T) "";
            case QnsCarrierConfigManager
                    .KEY_QNS_FALLBACK_ON_INITIAL_CONNECTION_FAILURE_STRING_ARRAY:
                return (T) new String[] {};
            default:
                break;
        }
        return (T) null;
    }

    static synchronized int getConfigCarrierId(Context context, int slotId) {
        TelephonyManager tm = context.getSystemService(TelephonyManager.class);
        tm = tm.createForSubscriptionId(QnsUtils.getSubId(context, slotId));
        return tm.getSimCarrierId();
    }

    private static String getAssetFileName(Context context, String carrierIDConfig) {
        String[] configFileNameList;
        try {
            configFileNameList = context.getAssets().list("");
            for (String fileName : configFileNameList) {
                if (fileName.startsWith(carrierIDConfig)) {
                    log("matched file: " + fileName);
                    return fileName;
                }
            }
        } catch (Exception e) {
            loge("getFileName, can't find " + carrierIDConfig + " asset");
        }
        return null;
    }

    private static PersistableBundle readConfigFromAssets(Context context, String carrierIDConfig) {
        PersistableBundle bundleFromAssets = new PersistableBundle();

        String fileName = getAssetFileName(context, carrierIDConfig);
        InputStream inputStream = null;
        InputStreamReader inputStreamReader = null;
        try {
            inputStream = context.getAssets().open(fileName);
            inputStreamReader = new InputStreamReader(inputStream);
            Stream<String> streamOfString = new BufferedReader(inputStreamReader).lines();
            String streamToString = streamOfString.collect(Collectors.joining());

            String configTag = "carrier_config";
            int begin = streamToString.indexOf(configTag);
            int end = streamToString.lastIndexOf(configTag) + configTag.length();
            String bundleString = "<" + streamToString.substring(begin, end) + ">";

            InputStream targetStream = new ByteArrayInputStream(bundleString.getBytes());
            bundleFromAssets = PersistableBundle.readFromStream(targetStream);

            log("bundleFromAssets created : " + bundleFromAssets);

        } catch (Exception e) {
            loge("readConfigFromAssets, e: " + e);
        } finally {
            if (inputStreamReader != null) {
                try {
                    inputStreamReader.close();
                } catch (Exception e) {
                    loge("inputStreamReader.close e:" + e);
                }
            }
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (Exception e) {
                    loge("inputStream.close e:" + e);
                }
            }
        }
        return bundleFromAssets;
    }

    protected static void loge(String log) {
        Log.e(QnsUtils.class.getSimpleName(), log);
    }

    protected static void log(String log) {
        Log.d(QnsUtils.class.getSimpleName(), log);
    }

    protected static class QnsExecutor implements Executor {
        private final Handler mHandler;

        QnsExecutor(Handler handler) {
            mHandler = handler;
        }

        @Override
        public void execute(Runnable command) {
            if (!mHandler.post(command)) {
                throw new RejectedExecutionException(mHandler + " is shutting down");
            }
        }
    }
}
