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

package com.android.cellbroadcastreceiver.unit;

import static com.android.cellbroadcastreceiver.CellBroadcastReceiverMetrics.CBR_CONFIG_UPDATED;
import static com.android.cellbroadcastreceiver.CellBroadcastReceiverMetrics.FeatureMetrics.ALERT_IN_CALL;
import static com.android.cellbroadcastreceiver.CellBroadcastReceiverMetrics.FeatureMetrics.OVERRIDE_DND;
import static com.android.cellbroadcastreceiver.CellBroadcastReceiverMetrics.FeatureMetrics.ROAMING_SUPPORT;
import static com.android.cellbroadcastreceiver.CellBroadcastReceiverMetrics.FeatureMetrics.STORE_SMS;
import static com.android.cellbroadcastreceiver.CellBroadcastReceiverMetrics.FeatureMetrics.TEST_MODE;
import static com.android.cellbroadcastreceiver.CellBroadcastReceiverMetrics.FeatureMetrics.TEST_MODE_ON_USER_BUILD;
import static com.android.cellbroadcastreceiver.CellBroadcastReceiverMetrics.FeatureMetrics.TTS_MODE;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.SharedPreferences;
import android.telephony.SubscriptionManager;
import android.util.Pair;

import com.android.cellbroadcastreceiver.CellBroadcastChannelManager;
import com.android.cellbroadcastreceiver.CellBroadcastReceiverMetrics;
import com.android.cellbroadcastreceiver.Cellbroadcastmetric;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.io.IOException;
import java.util.HashSet;

public class CellBroadcastReceiverMetricsTest extends CellBroadcastTest {

    private static final String[] CHANNEL_CONFIG = {
            "12:type=etws_earthquake, emergency=true, display=false, always_on=true",
            "456:type=etws_tsunami, emergency=true, override_dnd=true, scope=domestic",
            "0xAC00-0xAFED:type=other, emergency=false, override_dnd=true, scope=carrier",
            "54-60:emergency=true, testing_mode=true, dialog_with_notification=true",
            "100-200",
            "0xA804:type=test, emergency=true, exclude_from_sms_inbox=true, "
                    + "vibration=0|350|250|350",
            "0x111E:debug_build=true"};

    private static final String OPERATOR = "123456";
    private static final int SUB_ID = SubscriptionManager.DEFAULT_SUBSCRIPTION_ID;

    private CellBroadcastChannelManager mChannelManager;

    @Mock
    private SharedPreferences.Editor mEditor;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());

        doReturn(null).when(mTelephonyManager).getServiceState();
        doReturn(mTelephonyManager).when(mTelephonyManager).createForSubscriptionId(anyInt());
        doReturn(mContext).when(mContext).createConfigurationContext(any());

        CellBroadcastChannelManager.clearAllCellBroadcastChannelRanges();

        putResources(com.android.cellbroadcastreceiver.R.array.additional_cbs_channels_strings,
                CHANNEL_CONFIG);
        mChannelManager = new CellBroadcastChannelManager(mContext, SUB_ID, null, false);

        doReturn(false).when(mSharedPreferences).getBoolean(anyString(), anyBoolean());
        doReturn(String.valueOf(0)).when(mSharedPreferences).getString(anyString(), anyString());

        doReturn(mEditor).when(mSharedPreferences).edit();
        doReturn(mEditor).when(mEditor).putBoolean(anyString(), anyBoolean());
        doNothing().when(mEditor).apply();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testGetInstance() {
        CellBroadcastReceiverMetrics testFeatureMetrics1 =
                CellBroadcastReceiverMetrics.getInstance();
        CellBroadcastReceiverMetrics testFeatureMetrics2 =
                CellBroadcastReceiverMetrics.getInstance();

        assertSame(testFeatureMetrics1, testFeatureMetrics2);
    }

    @Test
    public void testGetFeatureMetrics() {
        CellBroadcastReceiverMetrics.FeatureMetrics testFeatureMetrics =
                CellBroadcastReceiverMetrics.getInstance().getFeatureMetrics(mContext);
        CellBroadcastReceiverMetrics.FeatureMetrics testFeatureMetricsClone =
                CellBroadcastReceiverMetrics.getInstance().getFeatureMetrics(mContext);

        assertSame(testFeatureMetrics, testFeatureMetricsClone);

        doReturn(false).when(mSharedPreferences)
                .getBoolean(ALERT_IN_CALL, false);
        doReturn(String.valueOf(0)).when(mSharedPreferences)
                .getString(OVERRIDE_DND, String.valueOf(0));
        doReturn(false).when(mSharedPreferences)
                .getBoolean(ROAMING_SUPPORT, false);
        doReturn(false).when(mSharedPreferences)
                .getBoolean(STORE_SMS, false);
        doReturn(false).when(mSharedPreferences)
                .getBoolean(TEST_MODE, false);
        doReturn(true).when(mSharedPreferences)
                .getBoolean(TTS_MODE, true);
        doReturn(true).when(mSharedPreferences)
                .getBoolean(TEST_MODE_ON_USER_BUILD, true);

        CellBroadcastReceiverMetrics.getInstance().setFeatureMetrics(null);
        CellBroadcastReceiverMetrics.getInstance().setFeatureMetricsSharedPreferences(null);

        testFeatureMetrics = CellBroadcastReceiverMetrics.getInstance().getFeatureMetrics(
                mContext);


        assertFalse(testFeatureMetrics.isAlertDuringCall());
        HashSet<Pair<Integer, Integer>> compareSet = new HashSet<>();
        compareSet.add(new Pair(0, 0));
        assertTrue(testFeatureMetrics.getDnDChannelSet().equals(compareSet));
        assertFalse(testFeatureMetrics.isRoamingSupport());
        assertFalse(testFeatureMetrics.isStoreSms());
        assertFalse(testFeatureMetrics.isTestMode());
        assertTrue(testFeatureMetrics.isEnableAlertSpeech());
        assertTrue(testFeatureMetrics.isTestModeOnUserBuild());

        CellBroadcastReceiverMetrics.getInstance().getFeatureMetrics(mContext)
                .onChangedAlertDuringCall(true);
        CellBroadcastReceiverMetrics.getInstance().getFeatureMetrics(mContext)
                .onChangedOverrideDnD(mChannelManager, false);
        CellBroadcastReceiverMetrics.getInstance().getFeatureMetrics(mContext)
                .onChangedRoamingSupport(true);
        CellBroadcastReceiverMetrics.getInstance().getFeatureMetrics(mContext)
                .onChangedStoreSms(true);
        CellBroadcastReceiverMetrics.getInstance().getFeatureMetrics(mContext)
                .onChangedTestMode(true);
        CellBroadcastReceiverMetrics.getInstance().getFeatureMetrics(mContext)
                .onChangedEnableAlertSpeech(true);
        CellBroadcastReceiverMetrics.getInstance().getFeatureMetrics(mContext)
                .onChangedTestModeOnUserBuild(false);

        testFeatureMetrics = CellBroadcastReceiverMetrics.getInstance().getFeatureMetrics(
                mContext);

        assertTrue(testFeatureMetrics.isAlertDuringCall());
        compareSet.clear();
        compareSet.add(new Pair(456, 456));
        compareSet.add(new Pair(44032, 45037));
        assertTrue(testFeatureMetrics.getDnDChannelSet().equals(compareSet));
        assertTrue(testFeatureMetrics.isRoamingSupport());
        assertTrue(testFeatureMetrics.isStoreSms());
        assertTrue(testFeatureMetrics.isTestMode());
        assertTrue(testFeatureMetrics.isEnableAlertSpeech());
        assertFalse(testFeatureMetrics.isTestModeOnUserBuild());

        CellBroadcastReceiverMetrics.getInstance().getFeatureMetrics(mContext)
                .onChangedOverrideDnD(mChannelManager, true);
        testFeatureMetrics = CellBroadcastReceiverMetrics.getInstance().getFeatureMetrics(
                mContext);
        compareSet.clear();
        compareSet.add(new Pair(Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertTrue(testFeatureMetrics.getDnDChannelSet().equals(compareSet));
    }

    @Test
    public void testEquals() {
        CellBroadcastReceiverMetrics.getInstance().setFeatureMetrics(null);
        CellBroadcastReceiverMetrics.FeatureMetrics testFeatureMetrics =
                CellBroadcastReceiverMetrics.getInstance().getFeatureMetrics(mContext);
        CellBroadcastReceiverMetrics.FeatureMetrics testSharedPreferenceFeatureMetrics =
                CellBroadcastReceiverMetrics.getInstance().getFeatureMetricsSharedPreferences();

        assertTrue(testFeatureMetrics.equals(testSharedPreferenceFeatureMetrics));
        assertNotSame(testFeatureMetrics, testSharedPreferenceFeatureMetrics);

        testFeatureMetrics.onChangedAlertDuringCall(true);
        testFeatureMetrics.onChangedOverrideDnD(mChannelManager, false);

        assertFalse(testFeatureMetrics.equals(testSharedPreferenceFeatureMetrics));

        testSharedPreferenceFeatureMetrics.onChangedAlertDuringCall(true);
        testSharedPreferenceFeatureMetrics.onChangedOverrideDnD(mChannelManager, false);

        assertTrue(testFeatureMetrics.equals(testSharedPreferenceFeatureMetrics));

        testFeatureMetrics.onChangedOverrideDnD(mChannelManager, true);

        assertFalse(testFeatureMetrics.equals(testSharedPreferenceFeatureMetrics));
    }

    @Test
    public void testClone() throws CloneNotSupportedException {
        CellBroadcastReceiverMetrics.getInstance().setFeatureMetrics(null);
        CellBroadcastReceiverMetrics.FeatureMetrics testFeatureMetrics =
                CellBroadcastReceiverMetrics.getInstance().getFeatureMetrics(mContext);
        CellBroadcastReceiverMetrics.FeatureMetrics testSharedPreferenceFeatureMetrics =
                CellBroadcastReceiverMetrics.getInstance().getFeatureMetricsSharedPreferences();

        assertTrue(testFeatureMetrics.equals(testSharedPreferenceFeatureMetrics));

        testFeatureMetrics.onChangedAlertDuringCall(true);
        testFeatureMetrics.onChangedOverrideDnD(mChannelManager, false);

        assertFalse(testFeatureMetrics.equals(testSharedPreferenceFeatureMetrics));

        testSharedPreferenceFeatureMetrics =
                (CellBroadcastReceiverMetrics.FeatureMetrics) testFeatureMetrics.clone();

        assertTrue(testFeatureMetrics.equals(testSharedPreferenceFeatureMetrics));
        assertNotSame(testFeatureMetrics, testSharedPreferenceFeatureMetrics);
    }

    @Test
    public void testConvertToProtoBuffer() throws IOException {
        HashSet<Pair<Integer, Integer>> testChannelSet1 = new HashSet<>();
        testChannelSet1.add(new Pair(4370, 4375));
        testChannelSet1.add(new Pair(9000, 9000));
        testChannelSet1.add(new Pair(1112, 1133));
        testChannelSet1.add(new Pair(4370, 4376));
        testChannelSet1.add(new Pair(2345, 2900));
        testChannelSet1.add(new Pair(1112, 1199));
        testChannelSet1.add(new Pair(1112, 1111));

        HashSet<Pair<Integer, Integer>> testChannelSet_sorted = new HashSet<>();
        testChannelSet_sorted.add(new Pair(1112, 1133));
        testChannelSet_sorted.add(new Pair(4370, 4376));
        testChannelSet_sorted.add(new Pair(2345, 2900));
        testChannelSet_sorted.add(new Pair(4370, 4375));
        testChannelSet_sorted.add(new Pair(1112, 1199));
        testChannelSet_sorted.add(new Pair(9000, 9000));
        testChannelSet_sorted.add(new Pair(1112, 1111));

        byte[] testArrayByte = CellBroadcastReceiverMetrics.getInstance()
                .convertToProtoBuffer(testChannelSet1);

        Cellbroadcastmetric.CellBroadcastChannelRangesProto channelRangesProto =
                Cellbroadcastmetric.CellBroadcastChannelRangesProto
                        .parser().parseFrom(testArrayByte);

        HashSet<Pair<Integer, Integer>> testChannelSet2 = new HashSet<>();

        for (Cellbroadcastmetric.CellBroadcastChannelRangeProto range :
                channelRangesProto.getChannelRangesList()) {
            testChannelSet2.add(new Pair(range.getStart(), range.getEnd()));
        }

        assertTrue(testChannelSet1.equals(testChannelSet2));

        byte[] testArrayByte_sorted = CellBroadcastReceiverMetrics.getInstance()
                .convertToProtoBuffer(testChannelSet_sorted);

        assertArrayEquals(testArrayByte, testArrayByte_sorted);

        HashSet<Pair<Integer, Integer>> testChannelSetEmpty1 = new HashSet<>();

        byte[] testArrayByteEmpty = CellBroadcastReceiverMetrics.getInstance()
                .convertToProtoBuffer(testChannelSetEmpty1);

        Cellbroadcastmetric.CellBroadcastChannelRangesProto channelRangesProtoEmpty =
                Cellbroadcastmetric.CellBroadcastChannelRangesProto.parser().parseFrom(
                        testArrayByteEmpty);

        HashSet<Pair<Integer, Integer>> testChannelSetEmpty2 = new HashSet<>();

        for (Cellbroadcastmetric.CellBroadcastChannelRangeProto range :
                channelRangesProtoEmpty.getChannelRangesList()) {
            testChannelSetEmpty2.add(new Pair(range.getStart(), range.getEnd()));
        }

        assertTrue(testChannelSetEmpty1.equals(testChannelSetEmpty2));
    }

    @Test
    public void testGetDataFromProtoArrayByte() throws IOException {
        HashSet<Pair<Integer, Integer>> testChannelSet1 = new HashSet<>();
        testChannelSet1.add(new Pair(4370, 4375));
        testChannelSet1.add(new Pair(9000, 9000));
        testChannelSet1.add(new Pair(1112, 1133));

        byte[] testArrayByte = CellBroadcastReceiverMetrics.getInstance()
                .convertToProtoBuffer(testChannelSet1);

        HashSet<Pair<Integer, Integer>> testChannelSet2 =
                CellBroadcastReceiverMetrics.getInstance().getDataFromProtoArrayByte(testArrayByte);
        assertTrue(testChannelSet1.equals(testChannelSet2));
    }

    @Test
    public void testGetChannelSetFromString() {
        String inString1 = "4370,4380,4700-4750,5600,50,9600-9700,10000-11001";
        HashSet<Pair<Integer, Integer>> outChSet1;
        HashSet<Pair<Integer, Integer>> compareChSet1 = new HashSet<>();
        compareChSet1.add(new Pair(4370, 4370));
        compareChSet1.add(new Pair(4380, 4380));
        compareChSet1.add(new Pair(4700, 4750));
        compareChSet1.add(new Pair(5600, 5600));
        compareChSet1.add(new Pair(50, 50));
        compareChSet1.add(new Pair(9600, 9700));
        compareChSet1.add(new Pair(10000, 11001));

        outChSet1 = CellBroadcastReceiverMetrics.getInstance().getChannelSetFromString(inString1);

        assertTrue(outChSet1.equals(compareChSet1));

        String inString2 = "4370-4380, 4370-4380, 50, 50";
        HashSet<Pair<Integer, Integer>> outChSet2;
        HashSet<Pair<Integer, Integer>> compareChSet2 = new HashSet<>();

        compareChSet2.add(new Pair(4370, 4380));
        compareChSet2.add(new Pair(4370, 4380));
        compareChSet2.add(new Pair(50, 50));
        compareChSet2.add(new Pair(50, 50));

        outChSet2 = CellBroadcastReceiverMetrics.getInstance().getChannelSetFromString(inString2);

        assertTrue(outChSet2.equals(compareChSet2));
    }

    @Test
    public void testGetStringFromChannelSet() {
        HashSet<Pair<Integer, Integer>> intChSet = new HashSet<>();
        intChSet.add(new Pair(4370, 4370));
        intChSet.add(new Pair(4380, 4380));
        intChSet.add(new Pair(4700, 4750));
        intChSet.add(new Pair(5600, 5600));
        intChSet.add(new Pair(50, 50));
        intChSet.add(new Pair(9600, 9700));
        intChSet.add(new Pair(10000, 11001));

        String outStr = CellBroadcastReceiverMetrics.getInstance()
                .getStringFromChannelSet(intChSet);
        HashSet<Pair<Integer, Integer>> compareChSet =
                CellBroadcastReceiverMetrics.getInstance().getChannelSetFromString(outStr);

        assertTrue(compareChSet.equals(intChSet));
    }

    @Test
    public void testLogFeatureChangedAsNeeded() throws CloneNotSupportedException {
        CellBroadcastReceiverMetrics.getInstance().setFeatureMetrics(null);
        CellBroadcastReceiverMetrics.FeatureMetrics testFeatureMetrics =
                CellBroadcastReceiverMetrics.getInstance().getFeatureMetrics(mContext);
        CellBroadcastReceiverMetrics.FeatureMetrics testSharedPreferenceFeatureMetrics =
                CellBroadcastReceiverMetrics.getInstance().getFeatureMetricsSharedPreferences();

        assertTrue(testFeatureMetrics.equals(testSharedPreferenceFeatureMetrics));

        testFeatureMetrics.onChangedAlertDuringCall(true);
        testFeatureMetrics.onChangedOverrideDnD(mChannelManager, false);

        assertFalse(testFeatureMetrics.equals(testSharedPreferenceFeatureMetrics));

        CellBroadcastReceiverMetrics.getInstance().logFeatureChangedAsNeeded(mContext);

        CellBroadcastReceiverMetrics.FeatureMetrics mockedFeatureMetrics =
                mock(CellBroadcastReceiverMetrics.FeatureMetrics.class);

        CellBroadcastReceiverMetrics.getInstance()
                .setFeatureMetricsSharedPreferences(mockedFeatureMetrics);

        assertFalse(mockedFeatureMetrics.equals(testFeatureMetrics));

        CellBroadcastReceiverMetrics.getInstance().logFeatureChangedAsNeeded(mContext);

        CellBroadcastReceiverMetrics.getInstance().setFeatureMetrics(mockedFeatureMetrics);

        assertTrue(testFeatureMetrics.equals(CellBroadcastReceiverMetrics.getInstance()
                .getFeatureMetricsSharedPreferences()));

        CellBroadcastReceiverMetrics.getInstance().logFeatureChangedAsNeeded(mContext);

        verify(mockedFeatureMetrics, times(1)).logFeatureChanged();
        verify(mockedFeatureMetrics, times(1)).updateSharedPreferences();
        verify(mockedFeatureMetrics, times(1)).clone();
    }

    @Test
    public void testOnConfigUpdated() {
        doReturn(String.valueOf(0)).when(mSharedPreferences).getString(anyString(), anyString());

        CellBroadcastReceiverMetrics.getInstance().onConfigUpdated(mContext, OPERATOR, null);

        verify(mSharedPreferences, times(0)).getString(CBR_CONFIG_UPDATED, String.valueOf(0));

        HashSet<Pair<Integer, Integer>> curChRange = new HashSet<>();
        curChRange.add(new Pair(4370, 4370));
        curChRange.add(new Pair(4380, 4380));

        CellBroadcastReceiverMetrics.getInstance().onConfigUpdated(mContext, OPERATOR, curChRange);

        verify(mEditor, times(1)).apply();
        HashSet<Pair<Integer, Integer>> mCachedChSet =
                CellBroadcastReceiverMetrics.getInstance().getCachedChannelSet();
        assertTrue(curChRange.equals(mCachedChSet));
        assertNotSame(curChRange, mCachedChSet);
    }
}

