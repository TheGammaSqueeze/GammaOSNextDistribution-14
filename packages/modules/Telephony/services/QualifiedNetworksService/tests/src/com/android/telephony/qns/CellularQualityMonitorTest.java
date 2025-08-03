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

import static android.telephony.CellInfo.UNAVAILABLE;

import static com.android.telephony.qns.QualityMonitor.EVENT_CELLULAR_QNS_TELEPHONY_INFO_CHANGED;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.net.NetworkCapabilities;
import android.os.Message;
import android.telephony.AccessNetworkConstants;
import android.telephony.CellSignalStrengthCdma;
import android.telephony.CellSignalStrengthGsm;
import android.telephony.CellSignalStrengthLte;
import android.telephony.CellSignalStrengthNr;
import android.telephony.CellSignalStrengthTdscdma;
import android.telephony.CellSignalStrengthWcdma;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.SignalStrengthUpdateRequest;
import android.telephony.SignalThresholdInfo;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@RunWith(JUnit4.class)
public final class CellularQualityMonitorTest extends QnsTest {
    @Mock private QnsTelephonyListener.QnsTelephonyInfo mQnsTelephonyInfo;
    private CellularQualityMonitor mCellularQualityMonitor;
    int mApnType1 = NetworkCapabilities.NET_CAPABILITY_IMS;
    int mApnType2 = NetworkCapabilities.NET_CAPABILITY_EIMS;
    int mSlotIndex = 0;
    Threshold[] mTh1;
    Threshold[] mTh2;
    Threshold[] mTh3 = new Threshold[1];
    Threshold[] mOutputThs;
    CountDownLatch mLatch;
    ThresholdListener mThresholdListener;

    private class ThresholdListener extends ThresholdCallback
            implements ThresholdCallback.CellularThresholdListener {

        ThresholdListener(Executor executor) {
            this.init(executor);
        }

        @Override
        public void onCellularThresholdChanged(Threshold[] thresholds) {
            mOutputThs = thresholds;
            mLatch.countDown();
        }
    }

    Executor mExecutor = Runnable::run;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        super.setUp();
        mCellularQualityMonitor =
                new CellularQualityMonitor(sMockContext,
                        mMockQnsConfigManager,
                        mMockQnsTelephonyListener,
                        mSlotIndex);
        mLatch = new CountDownLatch(1);
        mThresholdListener = new ThresholdListener(mExecutor);

        mTh1 =
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
                            QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER)
                };
        mTh2 =
                new Threshold[] {
                    new Threshold(
                            AccessNetworkConstants.AccessNetworkType.EUTRAN,
                            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRP,
                            -110,
                            QnsConstants.THRESHOLD_EQUAL_OR_SMALLER,
                            QnsConstants.INVALID_ID)
                };
        mTh3 =
                new Threshold[] {
                    new Threshold(
                            AccessNetworkConstants.AccessNetworkType.EUTRAN,
                            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSNR,
                            -18,
                            QnsConstants.THRESHOLD_EQUAL_OR_SMALLER,
                            QnsConstants.INVALID_ID)
                };
    }

    @Test
    public void testGetCurrentQuality() {
        ArrayList<Byte> NrCqiReport = new ArrayList<>(Arrays.asList((byte) 3, (byte) 2, (byte) 1));
        SignalStrength ss =
                new SignalStrength(
                        new CellSignalStrengthCdma(-93, -132, -89, -125, 5),
                        new CellSignalStrengthGsm(-79, 2, 5),
                        new CellSignalStrengthWcdma(-94, 4, -102, -5),
                        new CellSignalStrengthTdscdma(-95, 2, -103), // not using Tdscdma
                        new CellSignalStrengthLte(-85, -91, -6, -10, 1, 12, 1),
                        new CellSignalStrengthNr(-91, -6, 3, 1, NrCqiReport, -80, -7, 4, 1));
        when(mMockTelephonyManager.getSignalStrength()).thenReturn(ss);
        int quality =
                mCellularQualityMonitor.getCurrentQuality(
                        AccessNetworkConstants.AccessNetworkType.EUTRAN,
                        SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRP);
        Assert.assertEquals(-91, quality);

        quality =
                mCellularQualityMonitor.getCurrentQuality(
                        AccessNetworkConstants.AccessNetworkType.EUTRAN,
                        SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRQ);
        Assert.assertEquals(-6, quality);

        quality =
                mCellularQualityMonitor.getCurrentQuality(
                        AccessNetworkConstants.AccessNetworkType.EUTRAN,
                        SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSNR);
        Assert.assertEquals(-10, quality);

        quality =
                mCellularQualityMonitor.getCurrentQuality(
                        AccessNetworkConstants.AccessNetworkType.GERAN,
                        SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI);
        Assert.assertEquals(-79, quality);

        quality =
                mCellularQualityMonitor.getCurrentQuality(
                        AccessNetworkConstants.AccessNetworkType.NGRAN,
                        SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSRSRP);
        Assert.assertEquals(-80, quality);

        quality =
                mCellularQualityMonitor.getCurrentQuality(
                        AccessNetworkConstants.AccessNetworkType.NGRAN,
                        SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSRSRQ);
        Assert.assertEquals(-7, quality);

        quality =
                mCellularQualityMonitor.getCurrentQuality(
                        AccessNetworkConstants.AccessNetworkType.NGRAN,
                        SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSSINR);
        Assert.assertEquals(4, quality);

        quality =
                mCellularQualityMonitor.getCurrentQuality(
                        AccessNetworkConstants.AccessNetworkType.UTRAN,
                        SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_ECNO);
        Assert.assertEquals(-5, quality);

        quality =
                mCellularQualityMonitor.getCurrentQuality(
                        AccessNetworkConstants.AccessNetworkType.EUTRAN,
                        SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_UNKNOWN);
        Assert.assertEquals(SignalStrength.INVALID, quality);
    }

    @Test
    public void testRegisterThresholdChange() {

        mCellularQualityMonitor.registerThresholdChange(
                mThresholdListener, mApnType1, mTh1, mSlotIndex);
        Assert.assertEquals(1, mCellularQualityMonitor.mThresholdCallbackMap.size());
        Assert.assertEquals(1, mCellularQualityMonitor.mThresholdsList.size());
        Assert.assertEquals(2, mCellularQualityMonitor.getSignalThresholdInfo().size());

        mCellularQualityMonitor.registerThresholdChange(
                mThresholdListener, mApnType2, mTh2, mSlotIndex);
        Assert.assertEquals(2, mCellularQualityMonitor.mThresholdCallbackMap.size());
        Assert.assertEquals(2, mCellularQualityMonitor.mThresholdsList.size());

        Assert.assertTrue(
                mCellularQualityMonitor.mThresholdsList.containsKey(
                        mCellularQualityMonitor.getKey(mApnType1, mSlotIndex)));
        Assert.assertTrue(
                mCellularQualityMonitor.mThresholdsList.containsKey(
                        mCellularQualityMonitor.getKey(mApnType2, mSlotIndex)));

        Assert.assertEquals(
                2,
                mCellularQualityMonitor
                        .mThresholdsList
                        .get(mCellularQualityMonitor.getKey(mApnType1, mSlotIndex))
                        .size());
        Assert.assertEquals(
                1,
                mCellularQualityMonitor
                        .mThresholdsList
                        .get(mCellularQualityMonitor.getKey(mApnType2, mSlotIndex))
                        .size());

        // multiple measurement type supported
        Assert.assertEquals(2, mCellularQualityMonitor.getSignalThresholdInfo().size());
    }

    @Test
    public void testUnregisterThresholdChange() {
        testRegisterThresholdChange();
        mCellularQualityMonitor.unregisterThresholdChange(mApnType1, mSlotIndex);
        Assert.assertEquals(1, mCellularQualityMonitor.mThresholdCallbackMap.size());
        Assert.assertEquals(1, mCellularQualityMonitor.mThresholdsList.size());
        Assert.assertTrue(
                mCellularQualityMonitor.mThresholdsList.containsKey(
                        mCellularQualityMonitor.getKey(mApnType2, mSlotIndex)));
        Assert.assertFalse(
                mCellularQualityMonitor.mThresholdsList.containsKey(
                        mCellularQualityMonitor.getKey(mApnType1, mSlotIndex)));
        Assert.assertEquals(1, mCellularQualityMonitor.getSignalThresholdInfo().size());

        mCellularQualityMonitor.unregisterThresholdChange(mApnType2, mSlotIndex);
        Assert.assertEquals(0, mCellularQualityMonitor.mThresholdCallbackMap.size());
        Assert.assertEquals(0, mCellularQualityMonitor.mThresholdsList.size());

        Assert.assertFalse(
                mCellularQualityMonitor.mThresholdsList.containsKey(
                        mCellularQualityMonitor.getKey(mApnType1, mSlotIndex)));
        Assert.assertFalse(
                mCellularQualityMonitor.mThresholdsList.containsKey(
                        mCellularQualityMonitor.getKey(mApnType2, mSlotIndex)));

        Assert.assertEquals(0, mCellularQualityMonitor.getSignalThresholdInfo().size());
    }

    @Test
    public void testUpdateThresholdsForApn() {
        testRegisterThresholdChange();
        mCellularQualityMonitor.updateThresholdsForNetCapability(mApnType2, mSlotIndex, mTh3);
        Assert.assertEquals(2, mCellularQualityMonitor.mThresholdCallbackMap.size());
        Assert.assertEquals(2, mCellularQualityMonitor.mThresholdsList.size());
        Assert.assertTrue(
                mCellularQualityMonitor.mThresholdsList.containsKey(
                        mCellularQualityMonitor.getKey(mApnType1, mSlotIndex)));
        Assert.assertTrue(
                mCellularQualityMonitor.mThresholdsList.containsKey(
                        mCellularQualityMonitor.getKey(mApnType2, mSlotIndex)));

        Assert.assertEquals(
                1,
                mCellularQualityMonitor
                        .mThresholdsList
                        .get(mCellularQualityMonitor.getKey(mApnType2, mSlotIndex))
                        .size());
        Assert.assertEquals(2, mCellularQualityMonitor.getSignalThresholdInfo().size());
    }

    @Test
    public void testGetSignalThresholdInfo() {
        testRegisterThresholdChange();
        List<SignalThresholdInfo> stInfoList = mCellularQualityMonitor.getSignalThresholdInfo();
        Assert.assertEquals(2, stInfoList.size()); // multiple measurement type supported
    }

    @Test
    public void testDiffApnDiffThresholdSameMeasurementType() {
        int[] thresholds = new int[] {-110, -112, -99, -100, -70};
        int apn1 = NetworkCapabilities.NET_CAPABILITY_MMS;
        int apn2 = NetworkCapabilities.NET_CAPABILITY_IMS;
        int apn3 = NetworkCapabilities.NET_CAPABILITY_XCAP;
        Threshold[] t1 =
                new Threshold[] {
                    new Threshold(
                            AccessNetworkConstants.AccessNetworkType.EUTRAN,
                            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRP,
                            thresholds[0],
                            QnsConstants.THRESHOLD_EQUAL_OR_LARGER,
                            QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER)
                };
        Threshold[] t2 =
                new Threshold[] {
                    new Threshold(
                            AccessNetworkConstants.AccessNetworkType.EUTRAN,
                            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRP,
                            thresholds[1],
                            QnsConstants.THRESHOLD_EQUAL_OR_LARGER,
                            QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER),
                    new Threshold(
                            AccessNetworkConstants.AccessNetworkType.EUTRAN,
                            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRP,
                            thresholds[2],
                            QnsConstants.THRESHOLD_EQUAL_OR_LARGER,
                            QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER),
                };
        Threshold[] t3 =
                new Threshold[] {
                    new Threshold(
                            AccessNetworkConstants.AccessNetworkType.EUTRAN,
                            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRP,
                            thresholds[3],
                            QnsConstants.THRESHOLD_EQUAL_OR_LARGER,
                            QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER),
                    new Threshold(
                            AccessNetworkConstants.AccessNetworkType.EUTRAN,
                            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRP,
                            thresholds[4],
                            QnsConstants.THRESHOLD_EQUAL_OR_LARGER,
                            QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER),
                };
        mCellularQualityMonitor.registerThresholdChange(mThresholdListener, apn1, t1, mSlotIndex);
        List<SignalThresholdInfo> stInfo = mCellularQualityMonitor.getSignalThresholdInfo();
        Assert.assertEquals(1, stInfo.size());
        Assert.assertArrayEquals(new int[] {thresholds[0]}, stInfo.get(0).getThresholds());

        mCellularQualityMonitor.registerThresholdChange(mThresholdListener, apn2, t2, mSlotIndex);
        stInfo = mCellularQualityMonitor.getSignalThresholdInfo();
        Assert.assertEquals(1, stInfo.size());
        int[] th_array = new int[] {thresholds[0], thresholds[1], thresholds[2]};
        Arrays.sort(th_array);
        Assert.assertArrayEquals(th_array, stInfo.get(0).getThresholds());

        mCellularQualityMonitor.registerThresholdChange(mThresholdListener, apn3, t3, mSlotIndex);
        stInfo = mCellularQualityMonitor.getSignalThresholdInfo();
        Assert.assertEquals(1, stInfo.size());
        th_array = new int[] {thresholds[0], thresholds[1], thresholds[2], thresholds[3]};
        Arrays.sort(th_array);
        Assert.assertArrayEquals(th_array, stInfo.get(0).getThresholds());
    }

    @Test
    public void testDiffApnSameThresholdSameMeasurementType() {
        int[] thresholds = new int[] {-110, -100, -110, -100, -70};
        int apn1 = NetworkCapabilities.NET_CAPABILITY_MMS;
        int apn2 = NetworkCapabilities.NET_CAPABILITY_IMS;
        int apn3 = NetworkCapabilities.NET_CAPABILITY_XCAP;
        Threshold[] t1 =
                new Threshold[] {
                    new Threshold(
                            AccessNetworkConstants.AccessNetworkType.EUTRAN,
                            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRP,
                            thresholds[0],
                            QnsConstants.THRESHOLD_EQUAL_OR_LARGER,
                            QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER)
                };
        Threshold[] t2 =
                new Threshold[] {
                    new Threshold(
                            AccessNetworkConstants.AccessNetworkType.EUTRAN,
                            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRP,
                            thresholds[1],
                            QnsConstants.THRESHOLD_EQUAL_OR_LARGER,
                            QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER),
                    new Threshold(
                            AccessNetworkConstants.AccessNetworkType.EUTRAN,
                            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRP,
                            thresholds[2],
                            QnsConstants.THRESHOLD_EQUAL_OR_LARGER,
                            QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER),
                };
        Threshold[] t3 =
                new Threshold[] {
                    new Threshold(
                            AccessNetworkConstants.AccessNetworkType.EUTRAN,
                            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRP,
                            thresholds[3],
                            QnsConstants.THRESHOLD_EQUAL_OR_LARGER,
                            QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER),
                    new Threshold(
                            AccessNetworkConstants.AccessNetworkType.EUTRAN,
                            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRP,
                            thresholds[4],
                            QnsConstants.THRESHOLD_EQUAL_OR_LARGER,
                            QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER),
                };
        mCellularQualityMonitor.registerThresholdChange(mThresholdListener, apn1, t1, mSlotIndex);
        List<SignalThresholdInfo> stInfo = mCellularQualityMonitor.getSignalThresholdInfo();
        Assert.assertEquals(1, stInfo.size());
        Assert.assertArrayEquals(new int[] {thresholds[0]}, stInfo.get(0).getThresholds());

        mCellularQualityMonitor.registerThresholdChange(mThresholdListener, apn2, t2, mSlotIndex);
        stInfo = mCellularQualityMonitor.getSignalThresholdInfo();
        Assert.assertEquals(1, stInfo.size());
        int[] th_array = new int[] {thresholds[0], thresholds[1]};
        Arrays.sort(th_array);
        Assert.assertArrayEquals(th_array, stInfo.get(0).getThresholds());

        mCellularQualityMonitor.registerThresholdChange(mThresholdListener, apn3, t3, mSlotIndex);
        stInfo = mCellularQualityMonitor.getSignalThresholdInfo();
        Assert.assertEquals(1, stInfo.size());
        th_array = new int[] {thresholds[0], thresholds[1], thresholds[4]};
        Arrays.sort(th_array);
        Assert.assertArrayEquals(th_array, stInfo.get(0).getThresholds());
    }

    @Test
    public void testSameApnDiffThresholdsDiffMeasurementType() {
        int[] thresholdsRsrp = new int[] {-110, -100};
        int[] thresholdsRssnr = new int[] {-11, -10};
        int[] thresholdsRsrq = new int[] {-20};
        int apn1 = NetworkCapabilities.NET_CAPABILITY_IMS;
        Threshold[] t =
                new Threshold[] {
                    new Threshold(
                            AccessNetworkConstants.AccessNetworkType.EUTRAN,
                            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRP,
                            thresholdsRsrp[0],
                            QnsConstants.THRESHOLD_EQUAL_OR_LARGER,
                            QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER),
                    new Threshold(
                            AccessNetworkConstants.AccessNetworkType.EUTRAN,
                            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRP,
                            thresholdsRsrp[1],
                            QnsConstants.THRESHOLD_EQUAL_OR_LARGER,
                            QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER),
                    new Threshold(
                            AccessNetworkConstants.AccessNetworkType.EUTRAN,
                            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSNR,
                            thresholdsRssnr[0],
                            QnsConstants.THRESHOLD_EQUAL_OR_LARGER,
                            QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER),
                    new Threshold(
                            AccessNetworkConstants.AccessNetworkType.EUTRAN,
                            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSNR,
                            thresholdsRssnr[1],
                            QnsConstants.THRESHOLD_EQUAL_OR_LARGER,
                            QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER),
                    new Threshold(
                            AccessNetworkConstants.AccessNetworkType.EUTRAN,
                            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRQ,
                            thresholdsRsrq[0],
                            QnsConstants.THRESHOLD_EQUAL_OR_LARGER,
                            QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER)
                };
        mCellularQualityMonitor.registerThresholdChange(mThresholdListener, apn1, t, mSlotIndex);
        List<SignalThresholdInfo> stInfoList = mCellularQualityMonitor.getSignalThresholdInfo();
        Assert.assertEquals(3, stInfoList.size());
        for (SignalThresholdInfo stInfo : stInfoList) {
            if (stInfo.getSignalMeasurementType()
                    == SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSNR) {
                Assert.assertArrayEquals(thresholdsRssnr, stInfo.getThresholds());
            } else if (stInfo.getSignalMeasurementType()
                    == SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRP) {
                Assert.assertArrayEquals(thresholdsRsrp, stInfo.getThresholds());
            } else if (stInfo.getSignalMeasurementType()
                    == SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRQ) {
                Assert.assertArrayEquals(thresholdsRsrq, stInfo.getThresholds());
            } else {
                Assert.fail();
            }
        }
    }

    @Test
    public void testNullThresholds() {
        mCellularQualityMonitor.registerThresholdChange(
                mThresholdListener, mApnType1, null, mSlotIndex);
        Assert.assertEquals(1, mCellularQualityMonitor.mThresholdCallbackMap.size());
        Assert.assertEquals(0, mCellularQualityMonitor.mThresholdsList.size());
    }

    @Test
    public void testBackhaulTimerUpdate() {
        int waitTime = 4000;
        mTh1[0].setWaitTime(waitTime);
        mCellularQualityMonitor.registerThresholdChange(
                mThresholdListener, mApnType1, new Threshold[] {mTh1[0]}, mSlotIndex);
        List<SignalThresholdInfo> signalThresholdInfoList =
                mCellularQualityMonitor.getSignalThresholdInfo();
        assertNotNull(signalThresholdInfoList);
        SignalThresholdInfo stInfo = signalThresholdInfoList.get(0);
        Assert.assertEquals(waitTime, stInfo.getHysteresisMs());

        mCellularQualityMonitor.updateThresholdsForNetCapability(mApnType1, mSlotIndex, mTh2);
        signalThresholdInfoList = mCellularQualityMonitor.getSignalThresholdInfo();
        assertNotNull(signalThresholdInfoList);
        stInfo = signalThresholdInfoList.get(0);
        Assert.assertEquals(0, stInfo.getHysteresisMs());
    }

    @Test
    public void testOnSignalStrengthsChanged_Matching() throws InterruptedException {
        mTh1 =
                new Threshold[] {
                    new Threshold(
                            AccessNetworkConstants.AccessNetworkType.EUTRAN,
                            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRP,
                            -117,
                            QnsConstants.THRESHOLD_EQUAL_OR_LARGER,
                            QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER)
                };
        mCellularQualityMonitor.registerThresholdChange(
                mThresholdListener, mApnType1, mTh1, mSlotIndex);
        ArgumentCaptor<TelephonyCallback> capture =
                ArgumentCaptor.forClass(TelephonyCallback.class);
        verify(mMockTelephonyManager)
                .registerTelephonyCallback(isA(Executor.class), capture.capture());
        TelephonyCallback.SignalStrengthsListener callback =
                (TelephonyCallback.SignalStrengthsListener) capture.getValue();
        assertNotNull(callback);

        ArrayList<Byte> NrCqiReport = new ArrayList<>(Arrays.asList((byte) 3, (byte) 2, (byte) 1));
        SignalStrength ss =
                new SignalStrength(
                        new CellSignalStrengthCdma(-93, -132, -89, -125, 5),
                        new CellSignalStrengthGsm(-79, 2, 5),
                        new CellSignalStrengthWcdma(-94, 4, -102, -5),
                        new CellSignalStrengthTdscdma(-95, 2, -103), // not using Tdscdma
                        new CellSignalStrengthLte(-85, -91, -6, -10, 1, 12, 1),
                        new CellSignalStrengthNr(-91, -6, 3, 1, NrCqiReport, -80, -7, 4, 1));

        mLatch = new CountDownLatch(1);
        mOutputThs = null;
        callback.onSignalStrengthsChanged(ss);
        verifyReportedThreshold(-91);

        mTh1 =
                new Threshold[] {
                    new Threshold(
                            AccessNetworkConstants.AccessNetworkType.UTRAN,
                            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSCP,
                            -90,
                            QnsConstants.THRESHOLD_EQUAL_OR_SMALLER,
                            QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER)
                };
        mCellularQualityMonitor.updateThresholdsForNetCapability(mApnType1, mSlotIndex, mTh1);

        mLatch = new CountDownLatch(1);
        mOutputThs = null;
        callback.onSignalStrengthsChanged(ss);
        verifyReportedThreshold(-102);

        mTh1 =
                new Threshold[] {
                    new Threshold(
                            AccessNetworkConstants.AccessNetworkType.GERAN,
                            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI,
                            -70,
                            QnsConstants.THRESHOLD_EQUAL_OR_SMALLER,
                            QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER)
                };
        mCellularQualityMonitor.updateThresholdsForNetCapability(mApnType1, mSlotIndex, mTh1);

        mLatch = new CountDownLatch(1);
        mOutputThs = null;
        callback.onSignalStrengthsChanged(ss);
        verifyReportedThreshold(-79);

        mTh1 =
                new Threshold[] {
                    new Threshold(
                            AccessNetworkConstants.AccessNetworkType.NGRAN,
                            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSRSRP,
                            -90,
                            QnsConstants.THRESHOLD_EQUAL_OR_LARGER,
                            QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER)
                };
        mCellularQualityMonitor.updateThresholdsForNetCapability(mApnType1, mSlotIndex, mTh1);

        mLatch = new CountDownLatch(1);
        mOutputThs = null;
        callback.onSignalStrengthsChanged(ss);
        verifyReportedThreshold(-80);
    }

    private void verifyReportedThreshold(int expected) throws InterruptedException {
        assertTrue(mLatch.await(100, TimeUnit.MILLISECONDS));
        assertTrue(mOutputThs.length > 0);
        assertEquals(expected, mOutputThs[0].getThreshold());
    }

    @Test
    public void testOnSignalStrengthsChanged_NotMatching() throws InterruptedException {
        mTh1 =
                new Threshold[] {
                    new Threshold(
                            AccessNetworkConstants.AccessNetworkType.EUTRAN,
                            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRP,
                            -90,
                            QnsConstants.THRESHOLD_EQUAL_OR_LARGER,
                            QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER)
                };
        mCellularQualityMonitor.registerThresholdChange(
                mThresholdListener, mApnType1, mTh1, mSlotIndex);
        ArgumentCaptor<TelephonyCallback> capture =
                ArgumentCaptor.forClass(TelephonyCallback.class);
        verify(mMockTelephonyManager)
                .registerTelephonyCallback(isA(Executor.class), capture.capture());
        TelephonyCallback.SignalStrengthsListener callback =
                (TelephonyCallback.SignalStrengthsListener) capture.getValue();
        assertNotNull(callback);

        ArrayList<Byte> NrCqiReport = new ArrayList<>(Arrays.asList((byte) 3, (byte) 2, (byte) 1));
        SignalStrength ss =
                new SignalStrength(
                        new CellSignalStrengthCdma(-93, -132, -89, -125, 5),
                        new CellSignalStrengthGsm(-79, 2, 5),
                        new CellSignalStrengthWcdma(-94, 4, -102, -5),
                        new CellSignalStrengthTdscdma(-95, 2, -103), // not using Tdscdma
                        new CellSignalStrengthLte(-85, -91, -6, -10, 1, 12, 1),
                        new CellSignalStrengthNr(-91, -6, 3, 1, NrCqiReport, -80, -7, 4, 1));

        mLatch = new CountDownLatch(1);
        mOutputThs = null;
        callback.onSignalStrengthsChanged(ss);
        assertFalse(mLatch.await(100, TimeUnit.MILLISECONDS));
        assertNull(mOutputThs);

        mTh1 =
                new Threshold[] {
                    new Threshold(
                            AccessNetworkConstants.AccessNetworkType.UTRAN,
                            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSCP,
                            -105,
                            QnsConstants.THRESHOLD_EQUAL_OR_SMALLER,
                            QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER)
                };
        mCellularQualityMonitor.updateThresholdsForNetCapability(mApnType1, mSlotIndex, mTh1);

        mLatch = new CountDownLatch(1);
        mOutputThs = null;
        callback.onSignalStrengthsChanged(ss);
        assertFalse(mLatch.await(100, TimeUnit.MILLISECONDS));
        assertNull(mOutputThs);

        mTh1 =
                new Threshold[] {
                    new Threshold(
                            AccessNetworkConstants.AccessNetworkType.GERAN,
                            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI,
                            -70,
                            QnsConstants.THRESHOLD_EQUAL_OR_LARGER,
                            QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER)
                };
        mCellularQualityMonitor.updateThresholdsForNetCapability(mApnType1, mSlotIndex, mTh1);

        mLatch = new CountDownLatch(1);
        mOutputThs = null;
        callback.onSignalStrengthsChanged(ss);
        assertFalse(mLatch.await(100, TimeUnit.MILLISECONDS));
        assertNull(mOutputThs);

        mTh1 =
                new Threshold[] {
                    new Threshold(
                            AccessNetworkConstants.AccessNetworkType.NGRAN,
                            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSRSRP,
                            -81,
                            QnsConstants.THRESHOLD_EQUAL_OR_SMALLER,
                            QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER)
                };
        mCellularQualityMonitor.updateThresholdsForNetCapability(mApnType1, mSlotIndex, mTh1);

        mLatch = new CountDownLatch(1);
        mOutputThs = null;
        callback.onSignalStrengthsChanged(ss);
        assertFalse(mLatch.await(100, TimeUnit.MILLISECONDS));
        assertNull(mOutputThs);
    }

    @Test
    public void testOnQnsTelephonyInfoChanged() {
        testRegisterThresholdChange();
        when(mQnsTelephonyInfo.getDataRegState()).thenReturn(ServiceState.STATE_IN_SERVICE);
        when(mQnsTelephonyInfo.getDataNetworkType()).thenReturn(TelephonyManager.NETWORK_TYPE_LTE);
        when(mQnsTelephonyInfo.isCellularAvailable()).thenReturn(true, false);
        when(mQnsTelephonyInfo.getDataRegState()).thenReturn(ServiceState.STATE_IN_SERVICE);
        Message.obtain(
                        mCellularQualityMonitor.mHandler,
                        EVENT_CELLULAR_QNS_TELEPHONY_INFO_CHANGED,
                        new QnsAsyncResult(null, mQnsTelephonyInfo, null))
                .sendToTarget();
        verify(mMockTelephonyManager)
                .clearSignalStrengthUpdateRequest(isA(SignalStrengthUpdateRequest.class));
        Mockito.clearInvocations(mMockTelephonyManager);

        Message.obtain(
                        mCellularQualityMonitor.mHandler,
                        EVENT_CELLULAR_QNS_TELEPHONY_INFO_CHANGED,
                        new QnsAsyncResult(null, mQnsTelephonyInfo, null))
                .sendToTarget();
        verify(mMockTelephonyManager, never())
                .clearSignalStrengthUpdateRequest(isA(SignalStrengthUpdateRequest.class));
    }

    @Test
    public void testNrThresholdsValidity() throws InterruptedException {
        mTh1 =
                new Threshold[] {
                    new Threshold(
                            AccessNetworkConstants.AccessNetworkType.NGRAN,
                            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSRSRP,
                            -117,
                            QnsConstants.THRESHOLD_EQUAL_OR_LARGER,
                            QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER)
                };
        mCellularQualityMonitor.registerThresholdChange(
                mThresholdListener, mApnType1, mTh1, mSlotIndex);
        ArgumentCaptor<TelephonyCallback> capture =
                ArgumentCaptor.forClass(TelephonyCallback.class);
        verify(mMockTelephonyManager)
                .registerTelephonyCallback(isA(Executor.class), capture.capture());
        TelephonyCallback.SignalStrengthsListener callback =
                (TelephonyCallback.SignalStrengthsListener) capture.getValue();
        assertNotNull(callback);

        ArrayList<Byte> NrCqiReport = new ArrayList<>(Arrays.asList((byte) 3, (byte) 2, (byte) 1));

        // Valid thresholds - All NR signal params are valid.
        SignalStrength ss =
                new SignalStrength(
                        new CellSignalStrengthCdma(
                                UNAVAILABLE, UNAVAILABLE, UNAVAILABLE, UNAVAILABLE, UNAVAILABLE),
                        new CellSignalStrengthGsm(UNAVAILABLE, UNAVAILABLE, UNAVAILABLE),
                        new CellSignalStrengthWcdma(
                                UNAVAILABLE, UNAVAILABLE, UNAVAILABLE, UNAVAILABLE),
                        new CellSignalStrengthTdscdma(
                                UNAVAILABLE, UNAVAILABLE, UNAVAILABLE), // not using Tdscdma
                        new CellSignalStrengthLte(
                                UNAVAILABLE,
                                UNAVAILABLE,
                                UNAVAILABLE,
                                UNAVAILABLE,
                                UNAVAILABLE,
                                UNAVAILABLE,
                                UNAVAILABLE),
                        new CellSignalStrengthNr(-91, -6, 3, 1, NrCqiReport, -80, -7, 4, 1));

        mLatch = new CountDownLatch(1);
        mOutputThs = null;
        callback.onSignalStrengthsChanged(ss);
        // signal strength is valid, so CQM should notify SignalStrength.
        assertTrue(mLatch.await(200, TimeUnit.MILLISECONDS));

        // Valid thresholds - Only SSRSRP, SSRSRQ and SSSINR for NR are valid.
        ss =
                new SignalStrength(
                        new CellSignalStrengthCdma(
                                UNAVAILABLE, UNAVAILABLE, UNAVAILABLE, UNAVAILABLE, UNAVAILABLE),
                        new CellSignalStrengthGsm(UNAVAILABLE, UNAVAILABLE, UNAVAILABLE),
                        new CellSignalStrengthWcdma(
                                UNAVAILABLE, UNAVAILABLE, UNAVAILABLE, UNAVAILABLE),
                        new CellSignalStrengthTdscdma(
                                UNAVAILABLE, UNAVAILABLE, UNAVAILABLE), // not using Tdscdma
                        new CellSignalStrengthLte(
                                UNAVAILABLE,
                                UNAVAILABLE,
                                UNAVAILABLE,
                                UNAVAILABLE,
                                UNAVAILABLE,
                                UNAVAILABLE,
                                UNAVAILABLE),
                        new CellSignalStrengthNr(
                                UNAVAILABLE,
                                UNAVAILABLE,
                                UNAVAILABLE,
                                UNAVAILABLE,
                                NrCqiReport,
                                -80,
                                -7,
                                4,
                                1));

        mLatch = new CountDownLatch(1);
        mOutputThs = null;
        callback.onSignalStrengthsChanged(ss);
        // signal strength is valid, so CQM should notify SignalStrength.
        assertTrue(mLatch.await(200, TimeUnit.MILLISECONDS));

        // Invalid thresholds - All NR signal params UNAVAILABLE
        ss =
                new SignalStrength(
                        new CellSignalStrengthCdma(
                                UNAVAILABLE, UNAVAILABLE, UNAVAILABLE, UNAVAILABLE, UNAVAILABLE),
                        new CellSignalStrengthGsm(UNAVAILABLE, UNAVAILABLE, UNAVAILABLE),
                        new CellSignalStrengthWcdma(
                                UNAVAILABLE, UNAVAILABLE, UNAVAILABLE, UNAVAILABLE),
                        new CellSignalStrengthTdscdma(
                                UNAVAILABLE, UNAVAILABLE, UNAVAILABLE), // not using Tdscdma
                        new CellSignalStrengthLte(
                                UNAVAILABLE,
                                UNAVAILABLE,
                                UNAVAILABLE,
                                UNAVAILABLE,
                                UNAVAILABLE,
                                UNAVAILABLE,
                                UNAVAILABLE),
                        new CellSignalStrengthNr(
                                UNAVAILABLE,
                                UNAVAILABLE,
                                UNAVAILABLE,
                                UNAVAILABLE,
                                NrCqiReport,
                                UNAVAILABLE,
                                UNAVAILABLE,
                                UNAVAILABLE,
                                1));

        mLatch = new CountDownLatch(1);
        mOutputThs = null;
        callback.onSignalStrengthsChanged(ss);
        // signal strength is not valid, so CQM should not notify SignalStrength.
        assertFalse(mLatch.await(200, TimeUnit.MILLISECONDS));

        // Invalid thresholds - Only CSI signal params are valid. SSRSRP, SSRSRQ and SSSINR for NR
        // are invalid.
        ss =
                new SignalStrength(
                        new CellSignalStrengthCdma(
                                UNAVAILABLE, UNAVAILABLE, UNAVAILABLE, UNAVAILABLE, UNAVAILABLE),
                        new CellSignalStrengthGsm(UNAVAILABLE, UNAVAILABLE, UNAVAILABLE),
                        new CellSignalStrengthWcdma(
                                UNAVAILABLE, UNAVAILABLE, UNAVAILABLE, UNAVAILABLE),
                        new CellSignalStrengthTdscdma(
                                UNAVAILABLE, UNAVAILABLE, UNAVAILABLE), // not using Tdscdma
                        new CellSignalStrengthLte(
                                UNAVAILABLE,
                                UNAVAILABLE,
                                UNAVAILABLE,
                                UNAVAILABLE,
                                UNAVAILABLE,
                                UNAVAILABLE,
                                UNAVAILABLE),
                        new CellSignalStrengthNr(
                                -91,
                                -6,
                                3,
                                1,
                                NrCqiReport,
                                UNAVAILABLE,
                                UNAVAILABLE,
                                UNAVAILABLE,
                                1));

        mLatch = new CountDownLatch(1);
        mOutputThs = null;
        callback.onSignalStrengthsChanged(ss);
        // signal strength is not valid, so CQM should not notify SignalStrength.
        assertFalse(mLatch.await(200, TimeUnit.MILLISECONDS));
    }

    @After
    public void tearDown() {
        if (mCellularQualityMonitor != null) {
            mCellularQualityMonitor.close();
        }
    }
}
