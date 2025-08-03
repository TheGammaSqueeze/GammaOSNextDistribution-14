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

package com.android.telephony.qns;

import static com.android.telephony.qns.QnsConstants.CALL_TYPE_IDLE;
import static com.android.telephony.qns.QnsConstants.CALL_TYPE_VIDEO;
import static com.android.telephony.qns.QnsConstants.CALL_TYPE_VOICE;
import static com.android.telephony.qns.QnsConstants.CELL_PREF;
import static com.android.telephony.qns.QnsConstants.COVERAGE_HOME;
import static com.android.telephony.qns.QnsConstants.COVERAGE_ROAM;
import static com.android.telephony.qns.QnsConstants.ROVE_IN;
import static com.android.telephony.qns.QnsConstants.ROVE_OUT;
import static com.android.telephony.qns.QnsConstants.WIFI_PREF;

import android.net.NetworkCapabilities;
import android.telephony.AccessNetworkConstants;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.MockitoAnnotations;

import java.util.List;

@RunWith(JUnit4.class)
public class AnspImsPreferModePolicyBuilderTest extends QnsTest {

    private AnspImsPreferModePolicyBuilder mBuilder;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        super.setUp();
        mBuilder =
                new AnspImsPreferModePolicyBuilder(
                        mMockQnsConfigManager, NetworkCapabilities.NET_CAPABILITY_IMS);
    }

    @Test
    public void testGetPolicyInMap_rove_in() {
        String[] result =
                mBuilder.getPolicyInMap(
                        ROVE_IN,
                        new AccessNetworkSelectionPolicy.PreCondition(
                                CALL_TYPE_IDLE, WIFI_PREF, COVERAGE_HOME));
        Assert.assertEquals("Condition:WIFI_AVAILABLE", result[0]);

        result =
                mBuilder.getPolicyInMap(
                        QnsConstants.ROVE_IN,
                        new AccessNetworkSelectionPolicy.PreCondition(
                                CALL_TYPE_VOICE, WIFI_PREF, COVERAGE_HOME));
        Assert.assertEquals("Condition:WIFI_GOOD", result[0]);

        result =
                mBuilder.getPolicyInMap(
                        QnsConstants.ROVE_IN,
                        new AccessNetworkSelectionPolicy.PreCondition(
                                CALL_TYPE_VIDEO, WIFI_PREF, COVERAGE_HOME));
        Assert.assertEquals("Condition:WIFI_GOOD", result[0]);

        result =
                mBuilder.getPolicyInMap(
                        QnsConstants.ROVE_IN,
                        new AccessNetworkSelectionPolicy.PreCondition(
                                CALL_TYPE_IDLE, CELL_PREF, COVERAGE_ROAM));
        Assert.assertEquals("Condition:WIFI_GOOD,CELLULAR_BAD", result[0]);

        result =
                mBuilder.getPolicyInMap(
                        QnsConstants.ROVE_IN,
                        new AccessNetworkSelectionPolicy.PreCondition(
                                CALL_TYPE_VOICE, CELL_PREF, COVERAGE_ROAM));
        Assert.assertEquals("Condition:WIFI_GOOD,CELLULAR_BAD", result[0]);

        result =
                mBuilder.getPolicyInMap(
                        QnsConstants.ROVE_IN,
                        new AccessNetworkSelectionPolicy.PreCondition(
                                CALL_TYPE_VIDEO, CELL_PREF, COVERAGE_ROAM));
        Assert.assertEquals("Condition:WIFI_GOOD,CELLULAR_BAD", result[0]);

        result =
                mBuilder.getPolicyInMap(
                        QnsConstants.ROVE_IN,
                        new AccessNetworkSelectionPolicy.PreCondition(
                                CALL_TYPE_IDLE, CELL_PREF, COVERAGE_HOME));
        Assert.assertEquals("Condition:WIFI_GOOD,EUTRAN_BAD", result[0]);
        Assert.assertEquals("Condition:WIFI_GOOD,UTRAN_AVAILABLE", result[1]);
        Assert.assertEquals("Condition:WIFI_GOOD,GERAN_AVAILABLE", result[2]);

        result =
                mBuilder.getPolicyInMap(
                        QnsConstants.ROVE_IN,
                        new AccessNetworkSelectionPolicy.PreCondition(
                                CALL_TYPE_VOICE, CELL_PREF, COVERAGE_HOME));
        Assert.assertEquals("Condition:WIFI_GOOD,EUTRAN_BAD", result[0]);
        Assert.assertEquals("Condition:WIFI_GOOD,UTRAN_AVAILABLE", result[1]);
        Assert.assertEquals("Condition:WIFI_GOOD,GERAN_AVAILABLE", result[2]);

        result =
                mBuilder.getPolicyInMap(
                        QnsConstants.ROVE_IN,
                        new AccessNetworkSelectionPolicy.PreCondition(
                                CALL_TYPE_VIDEO, CELL_PREF, COVERAGE_HOME));
        Assert.assertEquals("Condition:WIFI_GOOD,EUTRAN_BAD", result[0]);
        Assert.assertEquals("Condition:WIFI_GOOD,UTRAN_AVAILABLE", result[1]);
        Assert.assertEquals("Condition:WIFI_GOOD,GERAN_AVAILABLE", result[2]);
    }

    @Test
    public void testGetPolicyInMap_rove_out() {
        String[] result =
                mBuilder.getPolicyInMap(
                        ROVE_OUT,
                        new AccessNetworkSelectionPolicy.PreCondition(
                                CALL_TYPE_IDLE, WIFI_PREF, COVERAGE_HOME));
        Assert.assertEquals("Condition:EUTRAN_TOLERABLE", result[0]);

        result =
                mBuilder.getPolicyInMap(
                        ROVE_OUT,
                        new AccessNetworkSelectionPolicy.PreCondition(
                                CALL_TYPE_VOICE, WIFI_PREF, COVERAGE_HOME));
        Assert.assertEquals("Condition:WIFI_BAD,EUTRAN_TOLERABLE", result[0]);

        result =
                mBuilder.getPolicyInMap(
                        ROVE_OUT,
                        new AccessNetworkSelectionPolicy.PreCondition(
                                CALL_TYPE_VIDEO, WIFI_PREF, COVERAGE_HOME));
        Assert.assertEquals("Condition:WIFI_BAD,EUTRAN_TOLERABLE", result[0]);

        result =
                mBuilder.getPolicyInMap(
                        ROVE_OUT,
                        new AccessNetworkSelectionPolicy.PreCondition(
                                CALL_TYPE_IDLE, CELL_PREF, COVERAGE_ROAM));
        Assert.assertEquals("Condition:CELLULAR_GOOD", result[0]);

        result =
                mBuilder.getPolicyInMap(
                        ROVE_OUT,
                        new AccessNetworkSelectionPolicy.PreCondition(
                                CALL_TYPE_VOICE, CELL_PREF, COVERAGE_ROAM));
        Assert.assertEquals("Condition:WIFI_BAD,EUTRAN_TOLERABLE", result[0]);

        result =
                mBuilder.getPolicyInMap(
                        ROVE_OUT,
                        new AccessNetworkSelectionPolicy.PreCondition(
                                CALL_TYPE_VIDEO, CELL_PREF, COVERAGE_ROAM));
        Assert.assertEquals("Condition:WIFI_BAD,EUTRAN_TOLERABLE", result[0]);

        result =
                mBuilder.getPolicyInMap(
                        ROVE_OUT,
                        new AccessNetworkSelectionPolicy.PreCondition(
                                CALL_TYPE_IDLE, CELL_PREF, COVERAGE_HOME));
        Assert.assertEquals("Condition:EUTRAN_GOOD", result[0]);
        Assert.assertEquals("Condition:WIFI_BAD,EUTRAN_TOLERABLE", result[1]);
        Assert.assertEquals("Condition:WIFI_BAD,UTRAN_GOOD", result[2]);
        Assert.assertEquals("Condition:WIFI_BAD,GERAN_GOOD", result[3]);

        result =
                mBuilder.getPolicyInMap(
                        ROVE_OUT,
                        new AccessNetworkSelectionPolicy.PreCondition(
                                CALL_TYPE_VOICE, CELL_PREF, COVERAGE_HOME));
        Assert.assertEquals("Condition:EUTRAN_GOOD", result[0]);
        Assert.assertEquals("Condition:WIFI_BAD,EUTRAN_TOLERABLE", result[1]);
        Assert.assertEquals("Condition:WIFI_BAD,UTRAN_GOOD", result[2]);
        Assert.assertEquals("Condition:WIFI_BAD,GERAN_GOOD", result[3]);

        result =
                mBuilder.getPolicyInMap(
                        ROVE_OUT,
                        new AccessNetworkSelectionPolicy.PreCondition(
                                CALL_TYPE_VIDEO, CELL_PREF, COVERAGE_HOME));
        Assert.assertEquals("Condition:EUTRAN_GOOD", result[0]);
        Assert.assertEquals("Condition:WIFI_BAD,EUTRAN_TOLERABLE", result[1]);
        Assert.assertEquals("Condition:WIFI_BAD,UTRAN_GOOD", result[2]);
        Assert.assertEquals("Condition:WIFI_BAD,GERAN_GOOD", result[3]);
    }

    @Test
    public void testGetSupportAccessNetworkTypes() {
        List<Integer> ans = mBuilder.getSupportAccessNetworkTypes();
        Assert.assertTrue(ans.contains(AccessNetworkConstants.AccessNetworkType.EUTRAN));
        Assert.assertTrue(ans.contains(AccessNetworkConstants.AccessNetworkType.UTRAN));
        Assert.assertTrue(ans.contains(AccessNetworkConstants.AccessNetworkType.GERAN));
        Assert.assertTrue(ans.contains(AccessNetworkConstants.AccessNetworkType.IWLAN));
        Assert.assertFalse(ans.contains(AccessNetworkConstants.AccessNetworkType.NGRAN));
    }
}
