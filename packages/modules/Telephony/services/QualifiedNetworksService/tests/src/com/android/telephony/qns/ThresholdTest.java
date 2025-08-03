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

import android.telephony.AccessNetworkConstants;
import android.telephony.SignalThresholdInfo;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ThresholdTest {
    private Threshold mThreshold;
    private int mThresholdValue = -117;
    private int mGid = QnsConstants.INVALID_ID;
    private int mAccessNetwork = AccessNetworkConstants.AccessNetworkType.EUTRAN;
    private int mMeasurementType = SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRP;
    private int mMatchType = QnsConstants.THRESHOLD_EQUAL_OR_SMALLER;
    private int mWaitTime = QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER;

    @Before
    public void setUp() {
        mThreshold =
                new Threshold(
                        mAccessNetwork, mMeasurementType, mThresholdValue, mMatchType, mWaitTime);
    }

    @After
    public void tearDown() {
        mThreshold = null;
    }

    @Test
    public void testCopy() {
        Threshold t2 = mThreshold.copy();
        Assert.assertEquals(mThreshold, t2);
    }

    @Test
    public void testSetThresholdId() {
        int set_tid = 5;
        mThreshold.setThresholdId(set_tid);
        Assert.assertEquals(set_tid, mThreshold.getThresholdId());
    }

    @Test
    public void testGetGroupId() {
        Assert.assertEquals(mGid, mThreshold.getGroupId());
    }

    @Test
    public void testSetGroupId() {
        int set_gid = 5;
        mThreshold.setGroupId(set_gid);
        Assert.assertEquals(set_gid, mThreshold.getGroupId());
    }

    @Test
    public void testGetAccessNetwork() {
        Assert.assertEquals(mAccessNetwork, mThreshold.getAccessNetwork());
    }

    @Test
    public void testSetAccessNetwork() {
        int set_accessNetwork = AccessNetworkConstants.AccessNetworkType.GERAN;
        mThreshold.setAccessNetwork(set_accessNetwork);
        Assert.assertEquals(set_accessNetwork, mThreshold.getAccessNetwork());
    }

    @Test
    public void testGetMeasurementType() {
        Assert.assertEquals(mMeasurementType, mThreshold.getMeasurementType());
    }

    @Test
    public void testSetMeasurementType() {
        int set_measurement = SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSNR;
        mThreshold.setMeasurementType(set_measurement);
        Assert.assertEquals(set_measurement, mThreshold.getMeasurementType());
    }

    @Test
    public void testGetThreshold() {
        Assert.assertEquals(mThresholdValue, mThreshold.getThreshold());
    }

    @Test
    public void testSetThreshold() {
        int set_th = -90;
        mThreshold.setThreshold(set_th);
        Assert.assertEquals(set_th, mThreshold.getThreshold());
    }

    @Test
    public void testGetMatchType() {
        Assert.assertEquals(mMatchType, mThreshold.getMatchType());
    }

    @Test
    public void testSetMatchType() {
        int set_matchType = QnsConstants.THRESHOLD_MATCH_TYPE_EQUAL_TO;
        mThreshold.setMatchType(set_matchType);
        Assert.assertEquals(set_matchType, mThreshold.getMatchType());
    }

    @Test
    public void testGetWaitTime() {
        Assert.assertEquals(QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER, mThreshold.getWaitTime());
    }

    @Test
    public void testSetWaitTime() {
        int set_time = 5000;
        mThreshold.setWaitTime(set_time);
        Assert.assertEquals(set_time, mThreshold.getWaitTime());
    }

    @Test
    public void testIsMultipleNetCapabilityTypeCheckCriteria() {
        Threshold[] th =
                new Threshold[] {
                    new Threshold(
                            AccessNetworkConstants.AccessNetworkType.EUTRAN,
                            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRP,
                            -117,
                            QnsConstants.THRESHOLD_EQUAL_OR_SMALLER,
                            QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER),
                    new Threshold(
                            AccessNetworkConstants.AccessNetworkType.EUTRAN,
                            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRP,
                            -117,
                            QnsConstants.THRESHOLD_EQUAL_OR_SMALLER,
                            QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER),
                    new Threshold(
                            AccessNetworkConstants.AccessNetworkType.EUTRAN,
                            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRQ,
                            -117,
                            QnsConstants.THRESHOLD_EQUAL_OR_LARGER,
                            QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER),
                };
        Assert.assertTrue(th[0].identicalThreshold(th[1]));
        Assert.assertFalse(th[0].identicalThreshold(th[2]));
    }

    @Test
    public void testIsMatching() {
        Threshold[] th =
                new Threshold[] {
                    new Threshold(
                            AccessNetworkConstants.AccessNetworkType.EUTRAN,
                            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRP,
                            -117,
                            QnsConstants.THRESHOLD_EQUAL_OR_SMALLER,
                            QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER),
                    new Threshold(
                            AccessNetworkConstants.AccessNetworkType.EUTRAN,
                            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSNR,
                            -15,
                            QnsConstants.THRESHOLD_EQUAL_OR_SMALLER,
                            QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER),
                    new Threshold(
                            AccessNetworkConstants.AccessNetworkType.EUTRAN,
                            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRP,
                            -100,
                            QnsConstants.THRESHOLD_EQUAL_OR_SMALLER,
                            QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER),
                    new Threshold(
                            AccessNetworkConstants.AccessNetworkType.EUTRAN,
                            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSNR,
                            0,
                            QnsConstants.THRESHOLD_EQUAL_OR_SMALLER,
                            QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER),
                    new Threshold(
                            AccessNetworkConstants.AccessNetworkType.EUTRAN,
                            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRP,
                            -65,
                            QnsConstants.THRESHOLD_EQUAL_OR_LARGER,
                            QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER),
                    new Threshold(
                            AccessNetworkConstants.AccessNetworkType.EUTRAN,
                            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSNR,
                            -10,
                            QnsConstants.THRESHOLD_EQUAL_OR_LARGER,
                            QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER),
                    new Threshold(
                            AccessNetworkConstants.AccessNetworkType.EUTRAN,
                            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRP,
                            -80,
                            QnsConstants.THRESHOLD_EQUAL_OR_LARGER,
                            QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER),
                    new Threshold(
                            AccessNetworkConstants.AccessNetworkType.EUTRAN,
                            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSNR,
                            10,
                            QnsConstants.THRESHOLD_EQUAL_OR_LARGER,
                            QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER),
                };
        Assert.assertTrue(th[0].isMatching(-118));
        Assert.assertTrue(th[1].isMatching(-20));
        Assert.assertFalse(th[2].isMatching(-99)); // not meeting the criteria
        Assert.assertFalse(th[3].isMatching(12)); // not meeting the criteria
        Assert.assertTrue(th[4].isMatching(-60));
        Assert.assertTrue(th[5].isMatching(20));
        Assert.assertFalse(th[6].isMatching(-81)); // not meeting the criteria
        Assert.assertFalse(th[7].isMatching(9)); // not meeting the criteria
    }
}
