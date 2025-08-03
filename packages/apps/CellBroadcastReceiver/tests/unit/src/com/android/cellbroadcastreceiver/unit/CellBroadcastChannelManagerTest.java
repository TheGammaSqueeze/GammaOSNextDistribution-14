/**
 * Copyright (C) 2016 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;

import android.telephony.SmsCbCmasInfo;
import android.telephony.SmsCbLocation;
import android.telephony.SmsCbMessage;
import android.telephony.SubscriptionManager;

import androidx.test.filters.SmallTest;

import com.android.cellbroadcastreceiver.CellBroadcastAlertService.AlertType;
import com.android.cellbroadcastreceiver.CellBroadcastChannelManager;
import com.android.cellbroadcastreceiver.CellBroadcastChannelManager.CellBroadcastChannelRange;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * APN retry manager tests
 */
public class CellBroadcastChannelManagerTest extends CellBroadcastTest {

    private static final String[] CHANNEL_CONFIG1 = {
        "12:type=etws_earthquake, emergency=true, display=false, always_on=true",
        "456:type=etws_tsunami, emergency=true, alert_duration=60000, scope=domestic",
        "0xAC00-0xAFED:type=other, emergency=false, override_dnd=true, scope=carrier",
        "54-60:emergency=true, testing_mode=true, dialog_with_notification=true",
        "100-200",
        "0xA804:type=test, emergency=true, exclude_from_sms_inbox=true, vibration=0|350|250|350",
        "0xA901:type=test, emergency=true, pulsation=0xFFFFFFFF",
        "0xA902:type=test, emergency=true, pulsation=0xFFFFFFFF|30000",
        "0xA903:type=test, emergency=true, pulsation=0xFFFFFFFF|30000|1000|1000",
        "0x1113:type=mute, emergency=true, always_on=true",
        "0x111E:debug_build=true"};

    private static final String[] CHANNEL_CONFIG2 = {
        "12:type=etws_earthquake, emergency=true, display=true, always_on=false",
        "456:type=etws_tsunami, emergency=true, alert_duration=20000, scope=domestic",
        "0xAC00-0xAEFF:type=other, emergency=false, override_dnd=true, scope=carrier"};
    private static final String[] CHANNEL_CONFIG3 = {
        "0xA804:type=test, emergency=true, exclude_from_sms_inbox=true, vibration=0|350|250|350"
    };

    private static final String OPERATOR = "123456";
    private static final int SUB_ID = SubscriptionManager.DEFAULT_SUBSCRIPTION_ID;

    // For SUB1 without roaming support
    private CellBroadcastChannelManager mChannelManager1;
    // For SUB1 with roaming support of OPERATOR
    private CellBroadcastChannelManager mChannelManager2;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());

        doReturn(null).when(mTelephonyManager).getServiceState();
        doReturn(mTelephonyManager).when(mTelephonyManager).createForSubscriptionId(anyInt());
        doReturn(mContext).when(mContext).createConfigurationContext(any());

        CellBroadcastChannelManager.clearAllCellBroadcastChannelRanges();
        // Init mChannelManager1 for SUB1
        putResources(com.android.cellbroadcastreceiver.R.array.additional_cbs_channels_strings,
                CHANNEL_CONFIG1);
        mChannelManager1 = new CellBroadcastChannelManager(mContext, SUB_ID, null, false);

        // Init mChannelManager2 for SUB2 and OPERATOR
        putResources(com.android.cellbroadcastreceiver.R.array.additional_cbs_channels_strings,
                CHANNEL_CONFIG2);
        putResources(
                com.android.cellbroadcastreceiver.R.array.emergency_alerts_channels_range_strings,
                CHANNEL_CONFIG3);
        mChannelManager2 = new CellBroadcastChannelManager(mContext, SUB_ID, OPERATOR, false);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
        CellBroadcastChannelManager.clearAllCellBroadcastChannelRanges();
    }

    @Test
    @SmallTest
    public void testGetCellBroadcastChannelRanges() throws Exception {
        List<CellBroadcastChannelRange> list = mChannelManager1.getCellBroadcastChannelRanges(
                com.android.cellbroadcastreceiver.R.array.additional_cbs_channels_strings);

        verifyChannelRangesForConfig1(list);

        list = mChannelManager2.getCellBroadcastChannelRanges(
                com.android.cellbroadcastreceiver.R.array.additional_cbs_channels_strings);

        verifyChannelRangesForConfig2(list);
    }

    private void verifyChannelRangesForConfig1(List<CellBroadcastChannelRange> list)
            throws Exception {
        assertEquals(10, list.size());

        assertEquals(12, list.get(0).mStartId);
        assertEquals(12, list.get(0).mEndId);
        assertEquals(AlertType.ETWS_EARTHQUAKE, list.get(0).mAlertType);
        assertEquals(CellBroadcastChannelRange.LEVEL_EMERGENCY, list.get(0).mEmergencyLevel);
        assertTrue(list.get(0).mAlwaysOn);
        assertFalse(list.get(0).mDisplay);
        assertFalse(list.get(0).mOverrideDnd);
        assertTrue(list.get(0).mWriteToSmsInbox);
        assertFalse(list.get(0).mTestMode);
        assertFalse(list.get(0).mDisplayDialogWithNotification);

        assertEquals(456, list.get(1).mStartId);
        assertEquals(456, list.get(1).mEndId);
        assertEquals(AlertType.ETWS_TSUNAMI, list.get(1).mAlertType);
        assertEquals(CellBroadcastChannelRange.LEVEL_EMERGENCY, list.get(1).mEmergencyLevel);
        assertFalse(list.get(1).mAlwaysOn);
        assertTrue(list.get(1).mDisplay);
        assertFalse(list.get(1).mOverrideDnd);
        assertTrue(list.get(1).mWriteToSmsInbox);
        assertFalse(list.get(1).mTestMode);
        assertEquals(60000, list.get(1).mAlertDuration);
        assertFalse(list.get(1).mDisplayDialogWithNotification);

        assertEquals(0xAC00, list.get(2).mStartId);
        assertEquals(0xAFED, list.get(2).mEndId);
        assertEquals(AlertType.OTHER, list.get(2).mAlertType);
        assertEquals(CellBroadcastChannelRange.LEVEL_NOT_EMERGENCY, list.get(2).mEmergencyLevel);
        assertFalse(list.get(2).mAlwaysOn);
        assertTrue(list.get(2).mDisplay);
        assertTrue(list.get(2).mOverrideDnd);
        assertTrue(list.get(2).mWriteToSmsInbox);
        assertFalse(list.get(2).mTestMode);
        assertEquals(list.get(2).mScope, CellBroadcastChannelRange.SCOPE_CARRIER);
        assertFalse(list.get(2).mDisplayDialogWithNotification);

        assertEquals(54, list.get(3).mStartId);
        assertEquals(60, list.get(3).mEndId);
        assertEquals(AlertType.DEFAULT, list.get(3).mAlertType);
        assertEquals(CellBroadcastChannelRange.LEVEL_EMERGENCY, list.get(3).mEmergencyLevel);
        assertFalse(list.get(3).mAlwaysOn);
        assertTrue(list.get(3).mDisplay);
        assertFalse(list.get(3).mOverrideDnd);
        assertTrue(list.get(3).mWriteToSmsInbox);
        assertTrue(list.get(3).mTestMode);
        assertTrue(list.get(3).mDisplayDialogWithNotification);

        assertEquals(100, list.get(4).mStartId);
        assertEquals(200, list.get(4).mEndId);
        assertEquals(AlertType.DEFAULT, list.get(4).mAlertType);
        assertEquals(CellBroadcastChannelRange.LEVEL_UNKNOWN, list.get(4).mEmergencyLevel);
        assertFalse(list.get(4).mAlwaysOn);
        assertTrue(list.get(4).mDisplay);
        assertFalse(list.get(4).mOverrideDnd);
        assertTrue(list.get(4).mWriteToSmsInbox);
        assertFalse(list.get(4).mTestMode);
        assertFalse(list.get(4).mDisplayDialogWithNotification);

        assertEquals(0xA804, list.get(5).mStartId);
        assertEquals(0xA804, list.get(5).mEndId);
        assertEquals(AlertType.TEST, list.get(5).mAlertType);
        assertEquals(CellBroadcastChannelRange.LEVEL_EMERGENCY, list.get(5).mEmergencyLevel);
        assertFalse(list.get(5).mAlwaysOn);
        assertTrue(list.get(5).mDisplay);
        assertFalse(list.get(5).mOverrideDnd);
        assertFalse(list.get(5).mWriteToSmsInbox);
        assertFalse(list.get(5).mTestMode);
        assertTrue(Arrays.equals(new int[]{0, 350, 250, 350}, list.get(5).mVibrationPattern));
        assertNotEquals(list.get(4).toString(), list.get(5).toString());
        assertFalse(list.get(5).mDisplayDialogWithNotification);

        assertEquals(0xA901, list.get(6).mStartId);
        assertEquals(0xA901, list.get(6).mEndId);
        assertEquals(1, list.get(6).mPulsationPattern.length);
        assertEquals(0xFFFFFFFF, list.get(6).mPulsationPattern[0]);

        assertEquals(0xA902, list.get(7).mStartId);
        assertEquals(0xA902, list.get(7).mEndId);
        assertEquals(2, list.get(7).mPulsationPattern.length);
        assertEquals(0xFFFFFFFF, list.get(7).mPulsationPattern[0]);
        assertEquals(30000, list.get(7).mPulsationPattern[1]);

        assertEquals(0xA903, list.get(8).mStartId);
        assertEquals(0xA903, list.get(8).mEndId);
        assertEquals(4, list.get(8).mPulsationPattern.length);
        assertEquals(0xFFFFFFFF, list.get(8).mPulsationPattern[0]);
        assertEquals(30000, list.get(8).mPulsationPattern[1]);
        assertEquals(1000, list.get(8).mPulsationPattern[2]);
        assertEquals(1000, list.get(8).mPulsationPattern[3]);

        assertEquals(0x1113, list.get(9).mStartId);
        assertEquals(0x1113, list.get(9).mEndId);
        assertEquals(AlertType.MUTE, list.get(9).mAlertType);
        assertEquals(CellBroadcastChannelRange.LEVEL_EMERGENCY, list.get(9).mEmergencyLevel);
        assertTrue(list.get(9).mAlwaysOn);
    }

    private void verifyChannelRangesForConfig2(List<CellBroadcastChannelRange> list)
            throws Exception {
        assertEquals(3, list.size());

        assertEquals(12, list.get(0).mStartId);
        assertEquals(12, list.get(0).mEndId);
        assertEquals(AlertType.ETWS_EARTHQUAKE, list.get(0).mAlertType);
        assertEquals(CellBroadcastChannelRange.LEVEL_EMERGENCY, list.get(0).mEmergencyLevel);
        assertFalse(list.get(0).mAlwaysOn);
        assertTrue(list.get(0).mDisplay);
        assertFalse(list.get(0).mOverrideDnd);
        assertTrue(list.get(0).mWriteToSmsInbox);
        assertFalse(list.get(0).mTestMode);
        assertFalse(list.get(0).mDisplayDialogWithNotification);

        assertEquals(456, list.get(1).mStartId);
        assertEquals(456, list.get(1).mEndId);
        assertEquals(AlertType.ETWS_TSUNAMI, list.get(1).mAlertType);
        assertEquals(CellBroadcastChannelRange.LEVEL_EMERGENCY, list.get(1).mEmergencyLevel);
        assertFalse(list.get(1).mAlwaysOn);
        assertTrue(list.get(1).mDisplay);
        assertFalse(list.get(1).mOverrideDnd);
        assertTrue(list.get(1).mWriteToSmsInbox);
        assertFalse(list.get(1).mTestMode);
        assertEquals(20000, list.get(1).mAlertDuration);
        assertFalse(list.get(1).mDisplayDialogWithNotification);

        assertEquals(0xAC00, list.get(2).mStartId);
        assertEquals(0xAEFF, list.get(2).mEndId);
        assertEquals(AlertType.OTHER, list.get(2).mAlertType);
        assertEquals(CellBroadcastChannelRange.LEVEL_NOT_EMERGENCY, list.get(2).mEmergencyLevel);
        assertFalse(list.get(2).mAlwaysOn);
        assertTrue(list.get(2).mDisplay);
        assertTrue(list.get(2).mOverrideDnd);
        assertTrue(list.get(2).mWriteToSmsInbox);
        assertFalse(list.get(2).mTestMode);
        assertEquals(list.get(2).mScope, CellBroadcastChannelRange.SCOPE_CARRIER);
        assertFalse(list.get(2).mDisplayDialogWithNotification);
    }

    @Test
    @SmallTest
    public void testGetCellBroadcastChannelResourcesKey() throws Exception {
        assertEquals(mChannelManager1.getCellBroadcastChannelResourcesKey(0xA804),
                com.android.cellbroadcastreceiver.R.array.additional_cbs_channels_strings);

        assertEquals(mChannelManager2.getCellBroadcastChannelResourcesKey(0xA804),
                com.android.cellbroadcastreceiver.R.array.emergency_alerts_channels_range_strings);
        // It should hit the channel ranges for sub as no config for the operator
        assertEquals(mChannelManager2.getCellBroadcastChannelResourcesKey(0xAFED),
                com.android.cellbroadcastreceiver.R.array.additional_cbs_channels_strings);
    }

    @Test
    @SmallTest
    public void testGetCellBroadcastChannelRange() throws Exception {
        CellBroadcastChannelRange channelRange = mChannelManager1
                .getCellBroadcastChannelRange(0xAC00);

        assertEquals(0xAC00, channelRange.mStartId);
        assertEquals(0xAFED, channelRange.mEndId);

        channelRange = mChannelManager2.getCellBroadcastChannelRange(0xAC00);

        assertEquals(0xAC00, channelRange.mStartId);
        assertEquals(0xAEFF, channelRange.mEndId);
    }

    @Test
    @SmallTest
    public void testGetAllCellBroadcastChannelRanges() throws Exception {
        List<CellBroadcastChannelRange> ranges =
                mChannelManager1.getAllCellBroadcastChannelRanges();

        verifyChannelRangesForConfig1(ranges);

        ranges = mChannelManager2.getAllCellBroadcastChannelRanges();

        assertEquals(14, ranges.size());
        verifyChannelRangesForConfig2(new ArrayList<>(ranges).subList(0, 3));
        verifyChannelRangesForConfig1(new ArrayList<>(ranges).subList(4, 14));
    }

    @Test
    @SmallTest
    public void testGetCellBroadcastChannelRangeFromMessage() throws Exception {
        SmsCbMessage msg = createMessageForCmasMessageClass(1, 0xAC00, 0);

        CellBroadcastChannelRange range = mChannelManager1
                .getCellBroadcastChannelRangeFromMessage(msg);

        assertEquals(0xAC00, range.mStartId);
        assertEquals(0xAFED, range.mEndId);

        range = mChannelManager2.getCellBroadcastChannelRangeFromMessage(msg);

        assertEquals(0xAC00, range.mStartId);
        assertEquals(0xAEFF, range.mEndId);
    }

    @Test
    @SmallTest
    public void testIsEmergencyMessage() throws Exception {
        assertFalse(mChannelManager1.isEmergencyMessage(null));

        SmsCbMessage msg = createMessageForCmasMessageClass(1, 0xA804, 0);

        assertTrue(mChannelManager1.isEmergencyMessage(msg));
    }

    private SmsCbMessage createMessageForCmasMessageClass(int serialNumber,
            int serviceCategory, int cmasMessageClass) {
        return new SmsCbMessage(1, 2, serialNumber, new SmsCbLocation(), serviceCategory,
                "language", "body",
                SmsCbMessage.MESSAGE_PRIORITY_EMERGENCY, null,
                new SmsCbCmasInfo(cmasMessageClass, 2, 3, 4, 5, 6),
                0, SUB_ID);
    }
}
