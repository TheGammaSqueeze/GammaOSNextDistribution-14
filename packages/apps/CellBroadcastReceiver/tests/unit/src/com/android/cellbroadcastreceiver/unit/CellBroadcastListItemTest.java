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

package com.android.cellbroadcastreceiver.unit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.telephony.SmsCbCmasInfo;
import android.telephony.SmsCbLocation;
import android.telephony.SmsCbMessage;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.widget.TextView;

import com.android.cellbroadcastreceiver.CellBroadcastListItem;
import com.android.cellbroadcastreceiver.CellBroadcastSettings;
import com.android.internal.telephony.gsm.SmsCbConstants;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public final class CellBroadcastListItemTest extends
        CellBroadcastActivityTestCase<Activity> {
    private static final String FAKE_MCC = "123";
    private static final String FAKE_TITLE = "Fake Alert";
    private static final int FAKE_SUB_ID = SubscriptionManager.DEFAULT_SUBSCRIPTION_ID;

    @Mock
    private SharedPreferences mMockPref;
    @Mock
    private Resources mMockResources;
    @Mock
    private SubscriptionInfo mMockSubInfo;
    @Mock
    private SubscriptionManager mMockSubManager;
    @Mock
    private TextView mMockChannelView;
    @Mock
    private TextView mMockDateView;
    @Mock
    private TextView mMockMessageView;

    private SmsCbMessage mMessage;
    private CellBroadcastListItem mItem;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.initMocks(this);
        doReturn(FAKE_MCC).when(mMockPref).getString(anyString(), anyString());
        doReturn(FAKE_TITLE).when(mMockResources).getText(anyInt());
        doReturn(mMockSubInfo).when(mMockSubManager).getActiveSubscriptionInfo(anyInt());
        mContext.injectSharedPreferences(mMockPref);
        CellBroadcastSettings.sResourcesCacheByOperator.put(FAKE_MCC, mMockResources);
        mMessage = new SmsCbMessage(1, 2, 1, new SmsCbLocation(),
              SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL,
              "language", "body", SmsCbMessage.MESSAGE_PRIORITY_EMERGENCY, null,
              new SmsCbCmasInfo(0, 2, 3, 4, 5, 6), 0, FAKE_SUB_ID);
        mItem = new CellBroadcastListItem(mContext, null);
        mItem.mChannelView = mMockChannelView;
        mItem.mDateView = mMockDateView;
        mItem.mMessageView = mMockMessageView;
    }

    public CellBroadcastListItemTest() {
        super(Activity.class);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testCellBroadcastListItemBindOnRoaming() {
        mItem.bind(mMessage);

        verify(mMockChannelView, times(1)).setText(eq(FAKE_TITLE), any());
    }
}
