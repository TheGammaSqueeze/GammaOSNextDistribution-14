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
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.internal.telephony.satellite.SatelliteNetworkInfo;
import com.android.libraries.entitlement.ServiceEntitlement;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class SatelliteEntitlementResponseTest {
    private static final String TEST_OTHER_APP_ID = "ap201x";
    private static final List<SatelliteNetworkInfo> TEST_PLMN_DATA_PLAN_TYPE_LIST = Arrays.asList(
            new SatelliteNetworkInfo("31026", "unmetered"),
            new SatelliteNetworkInfo("302820", "metered"));
    private static final List<String> TEST_PLMN_BARRED_LIST = Arrays.asList("31017", "302020");
    private static final String RESPONSE_WITHOUT_SATELLITE_APP_ID =
            "{\"VERS\":{\"version\":\"1\",\"validity\":\"172800\"},"
                    + "\"TOKEN\":{\"token\":\"ASH127AHHA88SF\"},\""
                    + TEST_OTHER_APP_ID + "\":{"
                    + "\"EntitlementStatus\":\"" + SATELLITE_ENTITLEMENT_STATUS_ENABLED + "\"}}";
    private static final String RESPONSE_WITHOUT_ENTITLEMENT_STATUS =
            "{\"VERS\":{\"version\":\"1\",\"validity\":\"172800\"},"
                    + "\"TOKEN\":{\"token\":\"ASH127AHHA88SF\"},\""
                    + ServiceEntitlement.APP_SATELLITE_ENTITLEMENT + "\":{}}";

    @Test
    public void testGetSatelliteEntitlementResponse() throws Exception {
        // Received the body with satellite service enabled.
        SatelliteEntitlementResponse response = new SatelliteEntitlementResponse(
                getResponse(SATELLITE_ENTITLEMENT_STATUS_ENABLED));
        assertEquals(SATELLITE_ENTITLEMENT_STATUS_ENABLED, response.getEntitlementStatus());
        assertEquals(TEST_PLMN_DATA_PLAN_TYPE_LIST.get(0).mPlmn,
                response.getPlmnAllowed().get(0).mPlmn);
        assertEquals(TEST_PLMN_DATA_PLAN_TYPE_LIST.get(0).mDataPlanType,
                response.getPlmnAllowed().get(0).mDataPlanType);
        assertEquals(TEST_PLMN_DATA_PLAN_TYPE_LIST.get(1).mPlmn,
                response.getPlmnAllowed().get(1).mPlmn);
        assertEquals(TEST_PLMN_DATA_PLAN_TYPE_LIST.get(1).mDataPlanType,
                response.getPlmnAllowed().get(1).mDataPlanType);
        assertEquals(TEST_PLMN_BARRED_LIST, response.getPlmnBarredList());

        // Received the empty body.
        response = new SatelliteEntitlementResponse("");
        assertEquals(SATELLITE_ENTITLEMENT_STATUS_DISABLED, response.getEntitlementStatus());
        assertTrue(response.getPlmnAllowed().size() == 0);
        assertTrue(response.getPlmnBarredList().size() == 0);

        // Received the body without satellite app id.
        response = new SatelliteEntitlementResponse(RESPONSE_WITHOUT_SATELLITE_APP_ID);
        assertEquals(SATELLITE_ENTITLEMENT_STATUS_DISABLED, response.getEntitlementStatus());
        assertTrue(response.getPlmnAllowed().size() == 0);
        assertTrue(response.getPlmnBarredList().size() == 0);

        // Received the body without EntitlementStatus.
        response = new SatelliteEntitlementResponse(RESPONSE_WITHOUT_ENTITLEMENT_STATUS);
        assertEquals(SATELLITE_ENTITLEMENT_STATUS_DISABLED, response.getEntitlementStatus());
        assertTrue(response.getPlmnAllowed().size() == 0);
        assertTrue(response.getPlmnBarredList().size() == 0);

        // Received the body with an entitlementStatus value of DISABLED.
        response = new SatelliteEntitlementResponse(
                getResponse(SATELLITE_ENTITLEMENT_STATUS_DISABLED));
        assertEquals(SATELLITE_ENTITLEMENT_STATUS_DISABLED, response.getEntitlementStatus());
        assertTrue(response.getPlmnAllowed().size() == 0);
        assertTrue(response.getPlmnBarredList().size() == 0);

        // Received the body with an entitlementStatus value of INCOMPATIBLE.
        response = new SatelliteEntitlementResponse(
                getResponse(SATELLITE_ENTITLEMENT_STATUS_INCOMPATIBLE));
        assertEquals(SATELLITE_ENTITLEMENT_STATUS_INCOMPATIBLE, response.getEntitlementStatus());
        assertTrue(response.getPlmnAllowed().size() == 0);
        assertTrue(response.getPlmnBarredList().size() == 0);

        // Received the body with an entitlementStatus value of PROVISIONING.
        response = new SatelliteEntitlementResponse(
                getResponse(SATELLITE_ENTITLEMENT_STATUS_PROVISIONING));
        assertEquals(SATELLITE_ENTITLEMENT_STATUS_PROVISIONING, response.getEntitlementStatus());
        assertTrue(response.getPlmnAllowed().size() == 0);
        assertTrue(response.getPlmnBarredList().size() == 0);
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
                + "{\"PLMN\":\"302820\",\"DataPlanType\":\"metered\"}],"
                + "\"PLMNBarred\":[{\"PLMN\":\"31017\"},"
                + "{\"PLMN\":\"302020\"}]" : "";
    }
}
