/*
 * Copyright 2020 The Android Open Source Project
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

package com.google.android.iwlan;

import android.annotation.NonNull;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.Country;
import android.location.CountryDetector;
import android.net.ConnectivityManager;
import android.net.IpPrefix;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.os.PersistableBundle;
import android.os.SystemClock;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.ims.ImsManager;
import android.telephony.ims.ImsMmTelManager;
import android.text.TextUtils;
import android.util.Log;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class IwlanHelper {

    private static final String TAG = IwlanHelper.class.getSimpleName();
    private static CountryDetector mCountryDetector;
    private static final String LAST_KNOWN_COUNTRY_CODE_KEY = "last_known_country_code";
    private static IpPrefix mNat64Prefix = new IpPrefix("64:ff9b::/96");

    public static String getNai(Context context, int slotId, byte[] nextReauthId) {
        if (nextReauthId != null) {
            return new String(nextReauthId, StandardCharsets.UTF_8);
        }

        StringBuilder naiBuilder = new StringBuilder();
        TelephonyManager tm = context.getSystemService(TelephonyManager.class);
        SubscriptionInfo subInfo = null;
        tm = tm.createForSubscriptionId(getSubId(context, slotId));

        try {
            subInfo = getSubInfo(context, slotId);
        } catch (IllegalStateException e) {
            return null;
        }

        String mnc = subInfo.getMncString();
        mnc = (mnc.length() == 2) ? '0' + mnc : mnc;

        naiBuilder.append('0').append(tm.getSubscriberId()).append('@');

        return naiBuilder
                .append("nai.epc.mnc")
                .append(mnc)
                .append(".mcc")
                .append(subInfo.getMccString())
                .append(".3gppnetwork.org")
                .toString();
    }

    public static int getSubId(Context context, int slotId) {
        int subid = SubscriptionManager.INVALID_SUBSCRIPTION_ID;

        try {
            subid = getSubInfo(context, slotId).getSubscriptionId();
        } catch (IllegalStateException e) {
            subid = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        }
        return subid;
    }

    public static int getCarrierId(Context context, int slotId) {
        TelephonyManager tm = context.getSystemService(TelephonyManager.class);
        tm = tm.createForSubscriptionId(IwlanHelper.getSubId(context, slotId));
        return tm.getSimCarrierId();
    }

    private static SubscriptionInfo getSubInfo(Context context, int slotId)
            throws IllegalStateException {
        SubscriptionManager sm = context.getSystemService(SubscriptionManager.class);
        SubscriptionInfo info = sm.getActiveSubscriptionInfoForSimSlotIndex(slotId);

        if (info == null) {
            throw new IllegalStateException("Subscription info is null.");
        }

        return info;
    }

    /**
     * Retrieves all IP addresses of a Network, including stacked IPv4 addresses.
     *
     * @param context a valid {@link Context} instance.
     * @param network the network for which IP addresses are to be retrieved.
     * @return a list of all IP addresses for the specified network. Returns an empty list if none
     *     are found.
     */
    public static List<InetAddress> getAllAddressesForNetwork(Context context, Network network) {
        ConnectivityManager connectivityManager =
                context.getSystemService(ConnectivityManager.class);
        List<InetAddress> gatewayList = new ArrayList<>();
        if (network != null) {
            LinkProperties linkProperties = connectivityManager.getLinkProperties(network);
            if (linkProperties != null) {
                for (LinkAddress linkAddr : linkProperties.getAllLinkAddresses()) {
                    InetAddress inetAddr = linkAddr.getAddress();
                    // skip linklocal and loopback addresses
                    if (!inetAddr.isLoopbackAddress() && !inetAddr.isLinkLocalAddress()) {
                        gatewayList.add(inetAddr);
                    }
                }
                if (linkProperties.getNat64Prefix() != null) {
                    mNat64Prefix = linkProperties.getNat64Prefix();
                }
            }
        }
        return gatewayList;
    }

    /**
     * The method is to check if this IP address is an IPv4-embedded IPv6 address(Pref64::/n).
     *
     * @param ipAddress IP address
     * @return True if it is an IPv4-embedded IPv6 addres, otherwise false.
     */
    public static boolean isIpv4EmbeddedIpv6Address(@NonNull InetAddress ipAddress) {
        return (ipAddress instanceof Inet6Address) && mNat64Prefix.contains(ipAddress);
    }

    public static boolean hasIpv6Address(List<InetAddress> localAddresses) {
        if (localAddresses != null) {
            for (InetAddress address : localAddresses) {
                if (address instanceof Inet6Address) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean hasIpv4Address(List<InetAddress> localAddresses) {
        if (localAddresses != null) {
            for (InetAddress address : localAddresses) {
                if (address instanceof Inet4Address) {
                    return true;
                }
            }
        }
        return false;
    }

    public static InetAddress getIpv4Address(List<InetAddress> localAddresses) {
        for (InetAddress address : localAddresses) {
            if (address instanceof Inet4Address) {
                return address;
            }
        }

        throw new IllegalStateException("Local address should not be null.");
    }

    public static InetAddress getIpv6Address(List<InetAddress> localAddresses) {
        for (InetAddress address : localAddresses) {
            if (address instanceof Inet6Address) {
                return address;
            }
        }

        throw new IllegalStateException("Local address should not be null.");
    }

    /**
     * @deprecated This method is deprecated. Use {@link IwlanCarrierConfig#getConfig()} instead.
     */
    @Deprecated
    public static <T> T getConfig(String key, Context context, int slotId) {
        CarrierConfigManager carrierConfigManager =
                context.getSystemService(CarrierConfigManager.class);
        if (carrierConfigManager == null) {
            throw new IllegalStateException("Carrier config manager is null.");
        }

        PersistableBundle bundle =
                carrierConfigManager.getConfigForSubId(getSubId(context, slotId));

        if (bundle == null || bundle.get(key) == null) {
            return getDefaultConfig(key);
        } else {
            return (T) bundle.get(key);
        }
    }

    /**
     * @deprecated This method is deprecated. Use {@link IwlanCarrierConfig#getDefaultConfig()}
     *     instead.
     */
    @Deprecated
    public static <T> T getDefaultConfig(String key) {
        PersistableBundle bundle = CarrierConfigManager.getDefaultConfig();
        if (bundle == null) {
            throw new IllegalStateException("Default config is null for: " + key);
        }
        return (T) bundle.get(key);
    }

    public static boolean isDefaultDataSlot(Context context, int slotId) {
        SubscriptionManager sm = context.getSystemService(SubscriptionManager.class);
        int ddsSlotId = sm.getSlotIndex(sm.getDefaultDataSubscriptionId());
        if (ddsSlotId != sm.INVALID_SIM_SLOT_INDEX) {
            if (ddsSlotId == slotId) {
                return true;
            }
        }
        return false;
    }

    public static boolean isCrossSimCallingEnabled(Context context, int slotId) {
        boolean isCstEnabled = false;
        int subid = getSubId(context, slotId);

        if (subid == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            // Fail to query subscription id, just return false.
            return false;
        }

        ImsManager imsManager = context.getSystemService(ImsManager.class);
        if (imsManager != null) {
            ImsMmTelManager imsMmTelManager = imsManager.getImsMmTelManager(subid);
            if (imsMmTelManager != null) {
                try {
                    isCstEnabled = imsMmTelManager.isCrossSimCallingEnabled();
                } catch (Exception e) {
                    // Fail to query Cross-SIM calling setting, just return false to avoid an
                    // exception.
                }
            }
        }
        return isCstEnabled;
    }

    public static void startCountryDetector(Context context) {
        mCountryDetector = context.getSystemService(CountryDetector.class);
        if (mCountryDetector != null) {
            updateCountryCodeFromCountryDetector(mCountryDetector.detectCountry());

            mCountryDetector.addCountryListener(
                    (newCountry) -> {
                        updateCountryCodeFromCountryDetector(newCountry);
                    },
                    null);
        }
    }

    @NonNull
    public static String getLastKnownCountryCode(Context context) {
        final SharedPreferences prefs =
                context.getSharedPreferences(LAST_KNOWN_COUNTRY_CODE_KEY, Context.MODE_PRIVATE);
        return prefs.getString(LAST_KNOWN_COUNTRY_CODE_KEY, "");
    }

    public static void updateCountryCodeWhenNetworkConnected() {
        if (mCountryDetector != null) {
            updateCountryCodeFromCountryDetector(mCountryDetector.detectCountry());
        }
    }

    private static void updateLastKnownCountryCode(String countryCode) {
        Context context = IwlanDataService.getContext();
        final SharedPreferences prefs =
                context.getSharedPreferences(LAST_KNOWN_COUNTRY_CODE_KEY, Context.MODE_PRIVATE);
        final SharedPreferences.Editor editor = prefs.edit();
        editor.putString(LAST_KNOWN_COUNTRY_CODE_KEY, countryCode);
        editor.commit();
        Log.d(TAG, "Update the last known country code in sharedPrefs " + countryCode);
    }

    private static void updateCountryCodeFromCountryDetector(Country country) {
        if (country == null) {
            return;
        }

        if (country.getSource() == Country.COUNTRY_SOURCE_NETWORK
                || country.getSource() == Country.COUNTRY_SOURCE_LOCATION) {
            Context context = IwlanDataService.getContext();
            String newCountryCode = country.getCountryIso();
            String lastKnownCountryCode = getLastKnownCountryCode(context);
            if (!TextUtils.isEmpty(newCountryCode)
                    && (TextUtils.isEmpty(lastKnownCountryCode)
                            || !lastKnownCountryCode.equalsIgnoreCase(newCountryCode))) {
                updateLastKnownCountryCode(newCountryCode);
            }
        }
    }

    static long elapsedRealtime() {
        /*Returns milliseconds since boot, including time spent in sleep.*/
        return SystemClock.elapsedRealtime();
    }
}
