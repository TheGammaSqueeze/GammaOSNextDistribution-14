/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.telephony.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.net.Uri;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.telephony.TelephonyManager;
import android.telephony.data.ApnSetting;
import android.util.ArrayMap;

import com.android.internal.telephony.flags.Flags;

import org.junit.Rule;
import org.junit.Test;

import java.util.Map;

public class ApnSettingTest {
    private static final Map<String, Integer> EXPECTED_STRING_TO_INT_MAP;
    private static final Map<Integer, String> EXPECTED_INT_TO_STRING_MAP;
    static {
        EXPECTED_STRING_TO_INT_MAP = new ArrayMap<>();
        EXPECTED_STRING_TO_INT_MAP.put(ApnSetting.TYPE_DEFAULT_STRING, ApnSetting.TYPE_DEFAULT);
        EXPECTED_STRING_TO_INT_MAP.put(ApnSetting.TYPE_MMS_STRING, ApnSetting.TYPE_MMS);
        EXPECTED_STRING_TO_INT_MAP.put(ApnSetting.TYPE_SUPL_STRING, ApnSetting.TYPE_SUPL);
        EXPECTED_STRING_TO_INT_MAP.put(ApnSetting.TYPE_DUN_STRING, ApnSetting.TYPE_DUN);
        EXPECTED_STRING_TO_INT_MAP.put(ApnSetting.TYPE_HIPRI_STRING, ApnSetting.TYPE_HIPRI);
        EXPECTED_STRING_TO_INT_MAP.put(ApnSetting.TYPE_FOTA_STRING, ApnSetting.TYPE_FOTA);
        EXPECTED_STRING_TO_INT_MAP.put(ApnSetting.TYPE_IMS_STRING, ApnSetting.TYPE_IMS);
        EXPECTED_STRING_TO_INT_MAP.put(ApnSetting.TYPE_CBS_STRING, ApnSetting.TYPE_CBS);
        EXPECTED_STRING_TO_INT_MAP.put(ApnSetting.TYPE_IA_STRING, ApnSetting.TYPE_IA);
        EXPECTED_STRING_TO_INT_MAP.put(ApnSetting.TYPE_EMERGENCY_STRING, ApnSetting.TYPE_EMERGENCY);
        EXPECTED_STRING_TO_INT_MAP.put(ApnSetting.TYPE_MCX_STRING, ApnSetting.TYPE_MCX);
        EXPECTED_STRING_TO_INT_MAP.put(ApnSetting.TYPE_XCAP_STRING, ApnSetting.TYPE_XCAP);
        EXPECTED_STRING_TO_INT_MAP.put(ApnSetting.TYPE_ENTERPRISE_STRING,
                ApnSetting.TYPE_ENTERPRISE);

        EXPECTED_INT_TO_STRING_MAP = new ArrayMap<>();
        EXPECTED_INT_TO_STRING_MAP.put(ApnSetting.TYPE_DEFAULT, ApnSetting.TYPE_DEFAULT_STRING);
        EXPECTED_INT_TO_STRING_MAP.put(ApnSetting.TYPE_MMS, ApnSetting.TYPE_MMS_STRING);
        EXPECTED_INT_TO_STRING_MAP.put(ApnSetting.TYPE_SUPL, ApnSetting.TYPE_SUPL_STRING);
        EXPECTED_INT_TO_STRING_MAP.put(ApnSetting.TYPE_DUN, ApnSetting.TYPE_DUN_STRING);
        EXPECTED_INT_TO_STRING_MAP.put(ApnSetting.TYPE_HIPRI, ApnSetting.TYPE_HIPRI_STRING);
        EXPECTED_INT_TO_STRING_MAP.put(ApnSetting.TYPE_FOTA, ApnSetting.TYPE_FOTA_STRING);
        EXPECTED_INT_TO_STRING_MAP.put(ApnSetting.TYPE_IMS, ApnSetting.TYPE_IMS_STRING);
        EXPECTED_INT_TO_STRING_MAP.put(ApnSetting.TYPE_CBS, ApnSetting.TYPE_CBS_STRING);
        EXPECTED_INT_TO_STRING_MAP.put(ApnSetting.TYPE_IA, ApnSetting.TYPE_IA_STRING);
        EXPECTED_INT_TO_STRING_MAP.put(ApnSetting.TYPE_EMERGENCY, ApnSetting.TYPE_EMERGENCY_STRING);
        EXPECTED_INT_TO_STRING_MAP.put(ApnSetting.TYPE_MCX, ApnSetting.TYPE_MCX_STRING);
        EXPECTED_INT_TO_STRING_MAP.put(ApnSetting.TYPE_XCAP, ApnSetting.TYPE_XCAP_STRING);
        EXPECTED_INT_TO_STRING_MAP.put(ApnSetting.TYPE_ENTERPRISE,
                ApnSetting.TYPE_ENTERPRISE_STRING);
    }

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    public void testIntToString() {
        for (Map.Entry<Integer, String> e : EXPECTED_INT_TO_STRING_MAP.entrySet()) {
            assertEquals(e.getValue(), ApnSetting.getApnTypeString(e.getKey()));
        }
    }

    @Test
    public void testStringToInt() {
        for (Map.Entry<String, Integer> e : EXPECTED_STRING_TO_INT_MAP.entrySet()) {
            assertEquals((int) e.getValue(), ApnSetting.getApnTypeInt(e.getKey()));
        }
    }

    @Test
    public void testBuilderGet() {
        int apnTypeBitMask = ApnSetting.TYPE_DEFAULT;
        int profileId = 9;
        int mtuV4 = 1;
        int mtuV6 = 2;
        int proxyPort = 5;
        int mmsPort = 3;
        int authType = ApnSetting.AUTH_TYPE_NONE;
        int protocol = ApnSetting.PROTOCOL_IP;
        int networkTypeBitmask =  (int) TelephonyManager.NETWORK_TYPE_BITMASK_LTE;
        int roamingProtocol = 1;
        int mvnoType = ApnSetting.MVNO_TYPE_SPN;
        int carrierId = 123;
        boolean isPersistent = true;
        boolean carrierEnabled = true;
        Uri mmsc = new Uri.Builder().build();
        String mmsProxy = "12.34.56";
        String proxyAddress = "11.22.33.44";
        String apnName = "testApnName";
        String entryName = "entryName";
        String user = "testUser";
        String password = "testPWD";
        String operatorNumeric = "123";
        ApnSetting apnSettingUT = new ApnSetting.Builder()
                .setApnTypeBitmask(apnTypeBitMask)
                .setApnName(apnName)
                .setEntryName(entryName)
                .setMtuV4(mtuV4)
                .setMtuV6(mtuV6)
                .setProxyPort(proxyPort)
                .setMmsProxyPort(mmsPort)
                .setAuthType(authType)
                .setProtocol(protocol)
                .setNetworkTypeBitmask(networkTypeBitmask)
                .setRoamingProtocol(roamingProtocol)
                .setMvnoType(mvnoType)
                .setCarrierId(carrierId)
                .setCarrierEnabled(carrierEnabled)
                .setProfileId(profileId)
                .setPersistent(isPersistent)
                .setMmsc(mmsc)
                .setMmsProxyAddress(mmsProxy)
                .setProxyAddress(proxyAddress)
                .setUser(user)
                .setPassword(password)
                .setOperatorNumeric(operatorNumeric)
                .build();
        assertEquals(apnTypeBitMask, apnSettingUT.getApnTypeBitmask());
        assertEquals(profileId, apnSettingUT.getProfileId());
        assertEquals(mtuV4, apnSettingUT.getMtuV4());
        assertEquals(mtuV6, apnSettingUT.getMtuV6());
        assertEquals(proxyPort, apnSettingUT.getProxyPort());
        assertEquals(mmsPort, apnSettingUT.getMmsProxyPort());
        assertEquals(authType, apnSettingUT.getAuthType());
        assertEquals(protocol, apnSettingUT.getProtocol());
        assertEquals(networkTypeBitmask, apnSettingUT.getNetworkTypeBitmask());
        assertEquals(roamingProtocol, apnSettingUT.getRoamingProtocol());
        assertEquals(mvnoType, apnSettingUT.getMvnoType());
        assertEquals(carrierId, apnSettingUT.getCarrierId());
        assertEquals(mmsc, apnSettingUT.getMmsc());
        assertEquals(mmsProxy, apnSettingUT.getMmsProxyAddressAsString());
        assertEquals(proxyAddress, apnSettingUT.getProxyAddressAsString());
        assertEquals(apnName, apnSettingUT.getApnName());
        assertEquals(entryName, apnSettingUT.getEntryName());
        assertEquals(user, apnSettingUT.getUser());
        assertEquals(password, apnSettingUT.getPassword());
        assertEquals(operatorNumeric, apnSettingUT.getOperatorNumeric());
        assertTrue(apnSettingUT.isPersistent());
        assertTrue(apnSettingUT.isEnabled());
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_APN_SETTING_FIELD_SUPPORT_FLAG)
    public void testBuilderGetWithFlagEnabled() {
        int apnTypeBitMask = ApnSetting.TYPE_DEFAULT;
        int profileId = 9;
        int mtuV4 = 1;
        int mtuV6 = 2;
        int proxyPort = 5;
        int mmsPort = 3;
        int authType = ApnSetting.AUTH_TYPE_NONE;
        int protocol = ApnSetting.PROTOCOL_IP;
        int networkTypeBitmask =  (int) TelephonyManager.NETWORK_TYPE_BITMASK_LTE;
        int roamingProtocol = 1;
        int mvnoType = ApnSetting.MVNO_TYPE_SPN;
        int carrierId = 123;
        boolean isPersistent = true;
        boolean carrierEnabled = true;
        boolean isAlwaysOn = true;
        Uri mmsc = new Uri.Builder().build();
        String mmsProxy = "12.34.56";
        String proxyAddress = "11.22.33.44";
        String apnName = "testApnName";
        String entryName = "entryName";
        String user = "testUser";
        String password = "testPWD";
        String operatorNumeric = "123";
        ApnSetting apnSettingUT = new ApnSetting.Builder()
                .setApnTypeBitmask(apnTypeBitMask)
                .setApnName(apnName)
                .setEntryName(entryName)
                .setMtuV4(mtuV4)
                .setMtuV6(mtuV6)
                .setProxyPort(proxyPort)
                .setMmsProxyPort(mmsPort)
                .setAuthType(authType)
                .setProtocol(protocol)
                .setNetworkTypeBitmask(networkTypeBitmask)
                .setRoamingProtocol(roamingProtocol)
                .setMvnoType(mvnoType)
                .setCarrierId(carrierId)
                .setCarrierEnabled(carrierEnabled)
                .setProfileId(profileId)
                .setPersistent(isPersistent)
                .setMmsc(mmsc)
                .setMmsProxyAddress(mmsProxy)
                .setProxyAddress(proxyAddress)
                .setUser(user)
                .setPassword(password)
                .setOperatorNumeric(operatorNumeric)
                .setAlwaysOn(isAlwaysOn)
                .build();
        assertEquals(apnTypeBitMask, apnSettingUT.getApnTypeBitmask());
        assertEquals(profileId, apnSettingUT.getProfileId());
        assertEquals(mtuV4, apnSettingUT.getMtuV4());
        assertEquals(mtuV6, apnSettingUT.getMtuV6());
        assertEquals(proxyPort, apnSettingUT.getProxyPort());
        assertEquals(mmsPort, apnSettingUT.getMmsProxyPort());
        assertEquals(authType, apnSettingUT.getAuthType());
        assertEquals(protocol, apnSettingUT.getProtocol());
        assertEquals(networkTypeBitmask, apnSettingUT.getNetworkTypeBitmask());
        assertEquals(roamingProtocol, apnSettingUT.getRoamingProtocol());
        assertEquals(mvnoType, apnSettingUT.getMvnoType());
        assertEquals(carrierId, apnSettingUT.getCarrierId());
        assertEquals(mmsc, apnSettingUT.getMmsc());
        assertEquals(mmsProxy, apnSettingUT.getMmsProxyAddressAsString());
        assertEquals(proxyAddress, apnSettingUT.getProxyAddressAsString());
        assertEquals(apnName, apnSettingUT.getApnName());
        assertEquals(entryName, apnSettingUT.getEntryName());
        assertEquals(user, apnSettingUT.getUser());
        assertEquals(password, apnSettingUT.getPassword());
        assertEquals(operatorNumeric, apnSettingUT.getOperatorNumeric());
        assertTrue(apnSettingUT.isPersistent());
        assertTrue(apnSettingUT.isEnabled());
        assertTrue(apnSettingUT.isAlwaysOn());
    }
}
