/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.phone.satellite.entitlement;

import static com.android.phone.satellite.entitlement.SatelliteEntitlementResult.SATELLITE_ENTITLEMENT_STATUS_DISABLED;
import static com.android.phone.satellite.entitlement.SatelliteEntitlementResult.SATELLITE_ENTITLEMENT_STATUS_ENABLED;
import static com.android.phone.satellite.entitlement.SatelliteEntitlementResult.SATELLITE_ENTITLEMENT_STATUS_INCOMPATIBLE;
import static com.android.phone.satellite.entitlement.SatelliteEntitlementResult.SATELLITE_ENTITLEMENT_STATUS_PROVISIONING;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyVararg;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

import android.content.Context;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.libraries.entitlement.ServiceEntitlement;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class SatelliteEntitlementApiTest {
    private static final String TEST_URL = "https://test.url";
    private static final List<String> TEST_PLMN_ALLOWED = Arrays.asList("31026", "302820");
    @Mock
    Context mContext;
    @Mock
    ServiceEntitlement mServiceEntitlement;
    @Mock
    CarrierConfigManager mCarrierConfigManager;
    @Mock
    TelephonyManager mTelephonyManager;
    private PersistableBundle mCarrierConfigBundle;
    private SatelliteEntitlementApi mSatelliteEntitlementAPI;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        doReturn(Context.CARRIER_CONFIG_SERVICE).when(mContext).getSystemServiceName(
                CarrierConfigManager.class);
        doReturn(mCarrierConfigManager).when(mContext).getSystemService(
                Context.CARRIER_CONFIG_SERVICE);
        mCarrierConfigBundle = new PersistableBundle();
        doReturn(mCarrierConfigBundle)
                .when(mCarrierConfigManager).getConfigForSubId(anyInt(), anyVararg());
        doReturn(Context.TELEPHONY_SERVICE).when(mContext).getSystemServiceName(
                TelephonyManager.class);
        doReturn(mTelephonyManager).when(mContext).getSystemService(Context.TELEPHONY_SERVICE);
        doReturn(mTelephonyManager).when(mTelephonyManager).createForSubscriptionId(anyInt());

        mSatelliteEntitlementAPI = new SatelliteEntitlementApi(mContext, mCarrierConfigBundle,
                SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);
    }

    @Test
    public void testCheckEntitlementStatus() throws Exception {
        mCarrierConfigBundle.putString(
                CarrierConfigManager.ImsServiceEntitlement.KEY_ENTITLEMENT_SERVER_URL_STRING,
                TEST_URL);
        Field fieldServiceEntitlement = SatelliteEntitlementApi.class.getDeclaredField(
                "mServiceEntitlement");
        fieldServiceEntitlement.setAccessible(true);
        fieldServiceEntitlement.set(mSatelliteEntitlementAPI, mServiceEntitlement);

        // Get the EntitlementStatus to DISABLED
        int expectedEntitlementStatus = SATELLITE_ENTITLEMENT_STATUS_DISABLED;
        doReturn(getResponse(SATELLITE_ENTITLEMENT_STATUS_DISABLED))
                .when(mServiceEntitlement)
                .queryEntitlementStatus(eq(ServiceEntitlement.APP_SATELLITE_ENTITLEMENT), any());
        SatelliteEntitlementResult result =
                mSatelliteEntitlementAPI.checkEntitlementStatus();
        assertNotNull(result);
        assertEquals(expectedEntitlementStatus, result.getEntitlementStatus());
        assertTrue(result.getAllowedPLMNList().size() == 0);

        // Get the EntitlementStatus to ENABLED
        expectedEntitlementStatus = SATELLITE_ENTITLEMENT_STATUS_ENABLED;
        doReturn(getResponse(SATELLITE_ENTITLEMENT_STATUS_ENABLED))
                .when(mServiceEntitlement)
                .queryEntitlementStatus(eq(ServiceEntitlement.APP_SATELLITE_ENTITLEMENT), any());
        result = mSatelliteEntitlementAPI.checkEntitlementStatus();
        assertNotNull(result);
        assertEquals(expectedEntitlementStatus, result.getEntitlementStatus());
        assertEquals(TEST_PLMN_ALLOWED, result.getAllowedPLMNList());

        // Get the EntitlementStatus to INCOMPATIBLE
        expectedEntitlementStatus = SATELLITE_ENTITLEMENT_STATUS_INCOMPATIBLE;
        doReturn(getResponse(SATELLITE_ENTITLEMENT_STATUS_INCOMPATIBLE))
                .when(mServiceEntitlement)
                .queryEntitlementStatus(eq(ServiceEntitlement.APP_SATELLITE_ENTITLEMENT), any());
        result = mSatelliteEntitlementAPI.checkEntitlementStatus();
        assertNotNull(result);
        assertEquals(expectedEntitlementStatus, result.getEntitlementStatus());
        assertTrue(result.getAllowedPLMNList().size() == 0);

        // Get the EntitlementStatus to PROVISIONING
        expectedEntitlementStatus = SATELLITE_ENTITLEMENT_STATUS_PROVISIONING;
        doReturn(getResponse(SATELLITE_ENTITLEMENT_STATUS_PROVISIONING))
                .when(mServiceEntitlement)
                .queryEntitlementStatus(eq(ServiceEntitlement.APP_SATELLITE_ENTITLEMENT), any());
        result = mSatelliteEntitlementAPI.checkEntitlementStatus();
        assertNotNull(result);
        assertEquals(expectedEntitlementStatus, result.getEntitlementStatus());
        assertTrue(result.getAllowedPLMNList().size() == 0);
    }

    private String getResponse(int entitlementStatus) {
        return "{\"VERS\":{\"version\":\"1\",\"validity\":\"172800\"},"
                + "\"TOKEN\":{\"token\":\"ASH127AHHA88SF\"},\""
                + ServiceEntitlement.APP_SATELLITE_ENTITLEMENT + "\":{"
                + "\"EntitlementStatus\":\"" + entitlementStatus + "\""
                + getPLMNListOrEmpty(entitlementStatus)
                + "}}";
    }

    private String getPLMNListOrEmpty(int entitlementStatus) {
        return entitlementStatus == SATELLITE_ENTITLEMENT_STATUS_ENABLED ? ","
                + "\"PLMNAllowed\":[{\"PLMN\":\"31026\",\"DataPlanType\":\"unmetered\"},"
                + "{\"PLMN\":\"302820\",\"DataPlanType\":\"metered\"}]" : "";
    }
}
