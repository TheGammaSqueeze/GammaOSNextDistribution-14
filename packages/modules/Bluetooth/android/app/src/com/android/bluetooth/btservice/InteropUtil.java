/*
 * Copyright (c) 2022 The Android Open Source Project
 * Copyright (c) 2020 The Linux Foundation
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

package com.android.bluetooth.btservice;

import android.util.Log;

/**
 * APIs of interoperability workaround utilities.
 * These APIs will call stack layer's interop APIs of interop.cc to do matching
 * or entry adding/removing.
 */
public class InteropUtil {
    private static final String TAG = "InteropUtil";

    /**
     * Add interop feature from device/include/interop.h to below InteropFeature if
     * this feature needs to be matched at java layer. Feature's name will be passed to
     * stack layer to do matching, so make sure that the added feature's name is exactly
     * same as that in device/include/interop.h.
     */
    public enum InteropFeature {
        INTEROP_NOT_UPDATE_AVRCP_PAUSED_TO_REMOTE,
        INTEROP_PHONE_POLICY_INCREASED_DELAY_CONNECT_OTHER_PROFILES,
        INTEROP_PHONE_POLICY_REDUCED_DELAY_CONNECT_OTHER_PROFILES,
        INTEROP_HFP_FAKE_INCOMING_CALL_INDICATOR,
        INTEROP_HFP_SEND_CALL_INDICATORS_BACK_TO_BACK,
        INTEROP_SETUP_SCO_WITH_NO_DELAY_AFTER_SLC_DURING_CALL,
        INTEROP_RETRY_SCO_AFTER_REMOTE_REJECT_SCO,
        INTEROP_ADV_PBAP_VER_1_2;
    }

    /**
     * Check if a given address matches a known interoperability workaround
     * identified by the interop feature.
     *
     * @param feature a given interop feature defined in {@link InteropFeature}.
     * @param address a given address to be matched.
     * @return true if matched, false otherwise.
     */
    public static boolean interopMatchAddr(InteropFeature feature, String address) {
        AdapterService adapterService = AdapterService.getAdapterService();
        if (adapterService == null) {
            Log.d(TAG, "interopMatchAddr: feature=" + feature.name()
                    + ", adapterService is null or vendor intf is not enabled");
            return false;
        }

        Log.d(TAG, "interopMatchAddr: feature=" + feature.name() + ", address=" + address);
        if (address == null) {
            return false;
        }

        boolean matched = adapterService.interopMatchAddr(feature, address);
        Log.d(TAG, "interopMatchAddr: matched=" + matched);
        return matched;
    }

    /**
     * Check if a given name matches a known interoperability workaround
     * identified by the interop feature.
     *
     * @param feature a given interop feature defined in {@link InteropFeature}.
     * @param name a given name to be matched.
     * @return true if matched, false otherwise.
     */
    public static boolean interopMatchName(InteropFeature feature, String name) {
        AdapterService adapterService = AdapterService.getAdapterService();
        if (adapterService == null) {
            Log.d(TAG, "interopMatchName: feature=" + feature.name()
                    + ", adapterService is null or vendor intf is not enabled");
            return false;
        }

        Log.d(TAG, "interopMatchName: feature=" + feature.name() + ", name=" + name);
        if (name == null) {
            return false;
        }

        boolean matched = adapterService.interopMatchName(feature, name);
        Log.d(TAG, "interopMatchName: matched=" + matched);
        return matched;
    }

    /**
     * Check if a given address or remote device name matches a known interoperability workaround
     * identified by the interop feature. remote device name will be fetched internally based on
     * the given address at stack layer.
     *
     * @param feature a given interop feature defined in {@link InteropFeature}.
     * @param address a given address to be matched.
     * @return true if matched, false otherwise
     */
    public static boolean interopMatchAddrOrName(InteropFeature feature, String address) {
        AdapterService adapterService = AdapterService.getAdapterService();
        if (adapterService == null) {
            Log.d(TAG, "interopMatchAddrOrName: feature=" + feature.name()
                    + ", adapterService is null or vendor intf is not enabled");
            return false;
        }

        Log.d(TAG, "interopMatchAddrOrName: feature=" + feature.name() + ", address=" + address);
        if (address == null) {
            return false;
        }

        boolean matched = adapterService.interopMatchAddrOrName(feature, address);
        Log.d(TAG, "interopMatchAddrOrName: matched=" + matched);
        return matched;
    }

    /**
     * Add a dynamic address interop database entry identified by the interop feature
     * for a device matching the first length bytes of addr.
     *
     * @param feature a given interop feature defined in {@link InteropFeature}.
     * @param address a given address to be added.
     * @param length the number of bytes of address to be stored,
     * length must be in [1,6], and usually it is 3.
     */
    public static void interopDatabaseAddAddr(InteropFeature feature,
            String address, int length) {
        AdapterService adapterService = AdapterService.getAdapterService();
        if (adapterService == null) {
            Log.d(TAG, "interopDatabaseAddAddr: feature=" + feature.name()
                    + ", adapterService is null or vendor intf is not enabled");
            return;
        }

        Log.d(TAG, "interopDatabaseAddAddr: feature=" + feature.name()
                + ", address=" + address + ", length=" + length);
        if (address == null || (length <= 0 || length > 6)) {
            return;
        }

        adapterService.interopDatabaseAddAddr(feature, address, length);
    }

    /**
     * Remove a dynamic address interop database entry identified by the interop feature
     * for a device matching the addr.
     *
     * @param feature a given interop feature defined in {@link InteropFeature}.
     * @param address a given address to be removed.
     */
    public static void interopDatabaseRemoveAddr(InteropFeature feature, String address) {
        AdapterService adapterService = AdapterService.getAdapterService();
        if (adapterService == null) {
            Log.d(TAG, "interopDatabaseRemoveAddr: feature=" + feature.name()
                    + ", adapterService is null or vendor intf is not enabled");
            return;
        }

        Log.d(TAG, "interopDatabaseRemoveAddr: feature=" + feature.name() + ", address=" + address);
        if (address == null) {
            return;
        }

        adapterService.interopDatabaseRemoveAddr(feature, address);
    }

    /**
     * Add a dynamic name interop database entry identified by the interop feature for the name.
     *
     * @param feature a given interop feature defined in {@link InteropFeature}.
     * @param name a given name to be added.
     */
    public static void interopDatabaseAddName(InteropFeature feature, String name) {
        AdapterService adapterService = AdapterService.getAdapterService();
        if (adapterService == null) {
            Log.d(TAG, "interopDatabaseAddName: feature=" + feature.name()
                    + ", adapterService is null or vendor intf is not enabled");
            return;
        }

        Log.d(TAG, "interopDatabaseAddName: feature=" + feature.name() + ", name=" + name);
        if (name == null) {
            return;
        }

        adapterService.interopDatabaseAddName(feature, name);
    }

    /**
     * Remove a dynamic name interop database entry identified by the interop feature for the name.
     *
     * @param feature a given interop feature defined in {@link InteropFeature}.
     * @param name a given name to be removed.
     */
    public static void interopDatabaseRemoveName(InteropFeature feature, String name) {
        AdapterService adapterService = AdapterService.getAdapterService();
        if (adapterService == null) {
            Log.d(TAG, "interopDatabaseRemoveName: feature=" + feature.name()
                    + ", adapterService is null or vendor intf is not enabled");
            return;
        }

        Log.d(TAG, "interopDatabaseRemoveName: feature=" + feature.name() + ", name=" + name);
        if (name == null) {
            return;
        }

        adapterService.interopDatabaseRemoveName(feature, name);
    }
}
