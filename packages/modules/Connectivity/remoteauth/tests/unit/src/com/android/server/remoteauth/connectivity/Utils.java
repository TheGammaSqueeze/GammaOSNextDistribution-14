/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.server.remoteauth.connectivity;

import android.companion.AssociationInfo;
import android.companion.AssociationRequest;
import android.content.pm.PackageManager;
import android.net.MacAddress;

import java.util.ArrayList;
import java.util.List;

public final class Utils {
    public static final int FAKE_CONNECTION_ID = 1;
    public static final int FAKE_USER_ID = 0;
    public static final String FAKE_DISPLAY_NAME = "FAKE-DISPLAY-NAME";
    public static final String FAKE_PEER_ADDRESS = "ff:ff:ff:ff:ff:ff";
    public static final String FAKE_DEVICE_PROFILE = "FAKE-DEVICE-PROFILE";
    public static final String FAKE_PACKAGE_NAME = "FAKE-PACKAGE-NAME";

    public static DiscoveryFilter getFakeDiscoveryFilter() {
        return DiscoveryFilter.Builder.builder()
                .setDeviceName(FAKE_DISPLAY_NAME)
                .setPeerAddress("FAKE-PEER-ADDRESS")
                .setDeviceType(DiscoveryFilter.WATCH)
                .build();
    }

    public static DiscoveredDeviceReceiver getFakeDiscoveredDeviceReceiver() {
        return new DiscoveredDeviceReceiver() {
            @Override
            public void onDiscovered(DiscoveredDevice unused) {}

            @Override
            public void onLost(DiscoveredDevice unused) {}
        };
    }

    /**
     * Returns a fake CDM connection info.
     *
     * @return connection info.
     */
    public static CdmConnectionInfo getFakeCdmConnectionInfo()
            throws PackageManager.NameNotFoundException {
        return new CdmConnectionInfo(FAKE_CONNECTION_ID, getFakeAssociationInfoList(1).get(0));
    }

    /**
     * Returns a fake discovered device.
     *
     * @return discovered device.
     */
    public static DiscoveredDevice getFakeCdmDiscoveredDevice()
            throws PackageManager.NameNotFoundException {
        return new DiscoveredDevice(getFakeCdmConnectionInfo(), FAKE_DISPLAY_NAME);
    }

    /**
     * Returns fake association info array.
     *
     * <p> Creates an AssociationInfo object with fake values.
     *
     * @param associationsSize number of fake association info entries to return.
     * @return list of {@link AssociationInfo} or null.
     */
    public static List<AssociationInfo> getFakeAssociationInfoList(int associationsSize) {
        if (associationsSize > 0) {
            List<AssociationInfo> associations = new ArrayList<>();
            // Association id starts from 1.
            for (int i = 1; i <= associationsSize; ++i) {
                associations.add(
                        (new AssociationInfo.Builder(i, FAKE_USER_ID, FAKE_PACKAGE_NAME))
                                .setDeviceProfile(AssociationRequest.DEVICE_PROFILE_WATCH)
                                .setDisplayName(FAKE_DISPLAY_NAME)
                                .setDeviceMacAddress(MacAddress.fromString(FAKE_PEER_ADDRESS))
                                .build());
            }
            return associations;
        }
        return new ArrayList<>();
    }

    private Utils() {}
}
