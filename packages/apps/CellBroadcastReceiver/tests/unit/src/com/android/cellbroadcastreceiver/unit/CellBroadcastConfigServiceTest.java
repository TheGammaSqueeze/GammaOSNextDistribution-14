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

import static com.android.cellbroadcastreceiver.CellBroadcastConfigService.CbConfig;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.RemoteException;
import android.telephony.CellBroadcastIdRange;
import android.telephony.SmsCbMessage;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import androidx.test.filters.SmallTest;

import com.android.cellbroadcastreceiver.CellBroadcastConfigService;
import com.android.cellbroadcastreceiver.CellBroadcastSettings;
import com.android.internal.telephony.ISms;
import com.android.internal.telephony.cdma.sms.SmsEnvelope;
import com.android.internal.telephony.gsm.SmsCbConstants;
import com.android.modules.utils.build.SdkLevel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Cell broadcast config service tests
 */
public class CellBroadcastConfigServiceTest extends CellBroadcastTest {

    @Mock
    ISms.Stub mMockedSmsService;

    @Mock
    SharedPreferences mMockedSharedPreferences;

    @Mock
    SubscriptionManager mMockSubscriptionManager;

    @Mock
    SubscriptionInfo mMockSubscriptionInfo;

    @Mock
    Intent mIntent;

    private CellBroadcastConfigService mConfigService;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mConfigService = spy(new CellBroadcastConfigService());
        TelephonyManager.disableServiceHandleCaching();
        doReturn(mTelephonyManager).when(mTelephonyManager).createForSubscriptionId(anyInt());

        Class[] cArgs = new Class[1];
        cArgs[0] = Context.class;

        Method method = ContextWrapper.class.getDeclaredMethod("attachBaseContext", cArgs);
        method.setAccessible(true);
        method.invoke(mConfigService, mContext);

        doReturn(mMockedSharedPreferences).when(mContext)
                .getSharedPreferences(anyString(), anyInt());

        mMockedServiceManager.replaceService("isms", mMockedSmsService);
        doReturn(mMockedSmsService).when(mMockedSmsService).queryLocalInterface(anyString());

        putResources(com.android.cellbroadcastreceiver.R.array
                .cmas_presidential_alerts_channels_range_strings, new String[]{
                "0x1112-0x1112:rat=gsm",
                "0x1000-0x1000:rat=cdma",
                "0x111F-0x111F:rat=gsm",
        });
        putResources(com.android.cellbroadcastreceiver.R.array
                .cmas_alert_extreme_channels_range_strings, new String[]{
                "0x1113-0x1114:rat=gsm",
                "0x1001-0x1001:rat=cdma",
                "0x1120-0x1121:rat=gsm",
        });
        putResources(com.android.cellbroadcastreceiver.R.array
                .cmas_alerts_severe_range_strings, new String[]{
                "0x1115-0x111A:rat=gsm",
                "0x1002-0x1002:rat=cdma",
                "0x1122-0x1127:rat=gsm",
        });
        putResources(com.android.cellbroadcastreceiver.R.array
                .required_monthly_test_range_strings, new String[]{
                "0x111C-0x111C:rat=gsm",
                "0x1004-0x1004:rat=cdma",
                "0x1129-0x1129:rat=gsm",
        });
        putResources(com.android.cellbroadcastreceiver.R.array
                .exercise_alert_range_strings, new String[]{
                "0x111D-0x111D:rat=gsm",
                "0x112A-0x112A:rat=gsm",
        });
        putResources(com.android.cellbroadcastreceiver.R.array
                .operator_defined_alert_range_strings, new String[]{
                "0x111E-0x111E:rat=gsm",
                "0x112B-0x112B:rat=gsm",
        });
        putResources(com.android.cellbroadcastreceiver.R.array
                .etws_alerts_range_strings, new String[]{
                "0x1100-0x1102:rat=gsm",
                "0x1104-0x1104:rat=gsm",
        });
        putResources(com.android.cellbroadcastreceiver.R.array
                .etws_test_alerts_range_strings, new String[]{
                "0x1103-0x1103:rat=gsm",
        });
        putResources(com.android.cellbroadcastreceiver.R.array
                .cmas_amber_alerts_channels_range_strings, new String[]{
                "0x111B-0x111B:rat=gsm",
                "0x1003-0x1003:rat=cdma",
                "0x1128-0x1128:rat=gsm",
        });
        putResources(com.android.cellbroadcastreceiver.R.array
                .geo_fencing_trigger_messages_range_strings, new String[]{
                    "0x1130:rat=gsm, emergency=true",
                });
        putResources(com.android.cellbroadcastreceiver.R.array
                .state_local_test_alert_range_strings, new String[]{
                    "0x112E:rat=gsm, emergency=true",
                    "0x112F:rat=gsm, emergency=true",
                });
        putResources(com.android.cellbroadcastreceiver.R.array
                .public_safety_messages_channels_range_strings, new String[]{
                    "0x112C:rat=gsm, emergency=true",
                    "0x112D:rat=gsm, emergency=true",
                });
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
        TelephonyManager.enableServiceHandleCaching();
    }

    private void setCellBroadcastRange(int subId, List<CbConfig> ranges) throws Exception {

        Class[] cArgs = new Class[2];
        cArgs[0] = Integer.TYPE;
        cArgs[1] = List.class;

        Method method =
                CellBroadcastConfigService.class.getDeclaredMethod("setCellBroadcastRange", cArgs);
        method.setAccessible(true);

        method.invoke(mConfigService, subId, ranges);
    }

    /**
     * Test enable cell broadcast range
     */
    @Test
    @SmallTest
    public void testEnableCellBroadcastRange() throws Exception {
        List<CbConfig> ranges = new ArrayList<>();
        ranges.add(new CbConfig(10, 20, 1, true));
        setCellBroadcastRange(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID, ranges);
        ArgumentCaptor<Integer> captorStart = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> captorEnd = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> captorType = ArgumentCaptor.forClass(Integer.class);

        CbConfig[] configs = new CbConfig[]{new CbConfig(10, 20, 1, true)};
        verifySetRanges(configs, 1, 1);

        ranges.clear();
        ranges.add(new CbConfig(10, 20, 1, true));
        setCellBroadcastRange(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID, ranges);

        configs = new CbConfig[]{new CbConfig(10, 20, 1, true)};
        verifySetRanges(configs, 2, 2);
    }

    /**
     * Test disable cell broadcast range
     */
    @Test
    @SmallTest
    public void testDisableCellBroadcastRange() throws Exception {
        List<CbConfig> ranges = new ArrayList<>();
        ranges.add(new CbConfig(10, 20, 1, false));
        setCellBroadcastRange(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID, ranges);
        ArgumentCaptor<Integer> captorStart = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> captorEnd = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> captorType = ArgumentCaptor.forClass(Integer.class);

        CbConfig[] configs = new CbConfig[]{new CbConfig(10, 20, 1, false)};
        verifySetRanges(configs, 1, 1);

        ranges.clear();
        setCellBroadcastRange(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID, ranges);
        if (SdkLevel.isAtLeastU()) {
            ArgumentCaptor<List<CellBroadcastIdRange>> captorRanges =
                    ArgumentCaptor.forClass(List.class);
            verify(mTelephonyManager, times(2)).setCellBroadcastIdRanges(
                    captorRanges.capture(), any(), any());
            List<CellBroadcastIdRange> outputs = captorRanges.getAllValues().get(1);
            assertFalse(outputs.contains(new CellBroadcastIdRange(10, 20, 1, false)));
        } else {
            verify(mMockedSmsService, times(1)).disableCellBroadcastRangeForSubscriber(anyInt(),
                    captorStart.capture(), captorEnd.capture(), captorType.capture());
        }
    }

    private void setPreference(String pref, boolean value) {
        doReturn(value).when(mMockedSharedPreferences).getBoolean(eq(pref), eq(true));
        doReturn(value).when(mMockedSharedPreferences).getBoolean(eq(pref), eq(false));
    }

    /**
     * Test enabling channels for default countries (US)
     */
    @Test
    @SmallTest
    public void testEnablingChannelsDefault() throws Exception {
        setPreference(CellBroadcastSettings.KEY_ENABLE_ALERTS_MASTER_TOGGLE, true);
        setPreference(CellBroadcastSettings.KEY_ENABLE_CMAS_EXTREME_THREAT_ALERTS, true);
        setPreference(CellBroadcastSettings.KEY_ENABLE_CMAS_SEVERE_THREAT_ALERTS, true);
        setPreference(CellBroadcastSettings.KEY_ENABLE_CMAS_AMBER_ALERTS, true);
        CbConfig[] configs = new CbConfig[]{
                new CbConfig(SmsEnvelope.SERVICE_CATEGORY_CMAS_PRESIDENTIAL_LEVEL_ALERT,
                        SmsEnvelope.SERVICE_CATEGORY_CMAS_PRESIDENTIAL_LEVEL_ALERT,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP2, true),
                new CbConfig(SmsEnvelope.SERVICE_CATEGORY_CMAS_EXTREME_THREAT,
                        SmsEnvelope.SERVICE_CATEGORY_CMAS_EXTREME_THREAT,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP2, true),
                new CbConfig(SmsEnvelope.SERVICE_CATEGORY_CMAS_SEVERE_THREAT,
                        SmsEnvelope.SERVICE_CATEGORY_CMAS_SEVERE_THREAT,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP2, true),
                new CbConfig(SmsEnvelope.SERVICE_CATEGORY_CMAS_CHILD_ABDUCTION_EMERGENCY,
                        SmsEnvelope.SERVICE_CATEGORY_CMAS_CHILD_ABDUCTION_EMERGENCY,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP2, true),
                new CbConfig(SmsEnvelope.SERVICE_CATEGORY_CMAS_TEST_MESSAGE,
                        SmsEnvelope.SERVICE_CATEGORY_CMAS_TEST_MESSAGE,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP2, false),

                // GSM
                new CbConfig(SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_WARNING,
                        SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_AND_TSUNAMI_WARNING,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
                new CbConfig(SmsCbConstants.MESSAGE_ID_ETWS_OTHER_EMERGENCY_TYPE,
                        SmsCbConstants.MESSAGE_ID_ETWS_OTHER_EMERGENCY_TYPE,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
                new CbConfig(SmsCbConstants.MESSAGE_ID_ETWS_TEST_MESSAGE,
                        SmsCbConstants.MESSAGE_ID_ETWS_TEST_MESSAGE,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP, false),
                new CbConfig(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL,
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
                new CbConfig(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL_LANGUAGE,
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL_LANGUAGE,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
                new CbConfig(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED,
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_LIKELY,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
                new CbConfig(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_OBSERVED,
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_LIKELY,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
                new CbConfig(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY,
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
                new CbConfig(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_REQUIRED_MONTHLY_TEST,
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_REQUIRED_MONTHLY_TEST,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP, false),
                new CbConfig(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXERCISE,
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXERCISE,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP, false),
                new CbConfig(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PUBLIC_SAFETY,
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PUBLIC_SAFETY,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP, false),
                new CbConfig(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_STATE_LOCAL_TEST,
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_STATE_LOCAL_TEST,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP, false),
                new CbConfig(SmsCbConstants.MESSAGE_ID_CMAS_GEO_FENCING_TRIGGER,
                        SmsCbConstants.MESSAGE_ID_CMAS_GEO_FENCING_TRIGGER,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
        };

        mConfigService.enableCellBroadcastChannels(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);
        verifySetRanges(configs, 1, 1);
    }

    /**
     * Test enabling channels for Presidential alert
     */
    @Test
    @SmallTest
    public void testEnablingPresidential() throws Exception {
        setPreference(CellBroadcastSettings.KEY_ENABLE_ALERTS_MASTER_TOGGLE, true);
        CbConfig[] configs = new CbConfig[]{
                new CbConfig(SmsEnvelope.SERVICE_CATEGORY_CMAS_PRESIDENTIAL_LEVEL_ALERT,
                        SmsEnvelope.SERVICE_CATEGORY_CMAS_PRESIDENTIAL_LEVEL_ALERT,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP2, true),
                new CbConfig(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL,
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
                new CbConfig(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL_LANGUAGE,
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL_LANGUAGE,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP, true),

        };
        mConfigService.enableCellBroadcastChannels(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);
        verifySetRanges(configs, 1, 1);

        setPreference(CellBroadcastSettings.KEY_ENABLE_ALERTS_MASTER_TOGGLE, false);
        mConfigService.enableCellBroadcastChannels(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);
        verifySetRanges(configs, 2, 2);

        setPreference(CellBroadcastSettings.KEY_ENABLE_ALERTS_MASTER_TOGGLE, true);
        mConfigService.enableCellBroadcastChannels(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);
        verifySetRanges(configs, 3, 3);
    }

    /**
     * Test enabling channels for extreme alert
     */
    @Test
    @SmallTest
    public void testEnablingExtreme() throws Exception {
        setPreference(CellBroadcastSettings.KEY_ENABLE_ALERTS_MASTER_TOGGLE, true);
        setPreference(CellBroadcastSettings.KEY_ENABLE_CMAS_EXTREME_THREAT_ALERTS, true);
        CbConfig[] configs = new CbConfig[]{
                new CbConfig(SmsEnvelope.SERVICE_CATEGORY_CMAS_EXTREME_THREAT,
                        SmsEnvelope.SERVICE_CATEGORY_CMAS_EXTREME_THREAT,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP2, true),
                new CbConfig(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED,
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_LIKELY,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
                new CbConfig(
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED_LANGUAGE,
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_LIKELY_LANGUAGE,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
        };

        mConfigService.enableCellBroadcastChannels(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);
        verifySetRanges(configs, 1, 1);

        setPreference(CellBroadcastSettings.KEY_ENABLE_CMAS_EXTREME_THREAT_ALERTS, false);
        configs = new CbConfig[]{
                new CbConfig(SmsEnvelope.SERVICE_CATEGORY_CMAS_EXTREME_THREAT,
                        SmsEnvelope.SERVICE_CATEGORY_CMAS_EXTREME_THREAT,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP2, false),
                new CbConfig(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED,
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_LIKELY,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP, false),
                new CbConfig(
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED_LANGUAGE,
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_LIKELY_LANGUAGE,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP, false),
        };
        mConfigService.enableCellBroadcastChannels(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);
        verifySetRanges(configs, 2, 1);

        setPreference(CellBroadcastSettings.KEY_ENABLE_CMAS_EXTREME_THREAT_ALERTS, true);
        configs = new CbConfig[]{
                new CbConfig(SmsEnvelope.SERVICE_CATEGORY_CMAS_EXTREME_THREAT,
                        SmsEnvelope.SERVICE_CATEGORY_CMAS_EXTREME_THREAT,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP2, true),
                new CbConfig(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED,
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_LIKELY,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
                new CbConfig(
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED_LANGUAGE,
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_LIKELY_LANGUAGE,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
        };
        mConfigService.enableCellBroadcastChannels(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);
        verifySetRanges(configs, 3, 2);
    }

    /**
     * Test enabling channels for severe alert
     */
    @Test
    @SmallTest
    public void testEnablingSevere() throws Exception {
        setPreference(CellBroadcastSettings.KEY_ENABLE_ALERTS_MASTER_TOGGLE, true);
        setPreference(CellBroadcastSettings.KEY_ENABLE_CMAS_SEVERE_THREAT_ALERTS, true);
        CbConfig[] configs = new CbConfig[]{
                new CbConfig(SmsEnvelope.SERVICE_CATEGORY_CMAS_SEVERE_THREAT,
                        SmsEnvelope.SERVICE_CATEGORY_CMAS_SEVERE_THREAT,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP2, true),
                new CbConfig(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_OBSERVED,
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_LIKELY,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
                new CbConfig(
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_OBSERVED_LANGUAGE,
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_LIKELY_LANGUAGE,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
        };
        mConfigService.enableCellBroadcastChannels(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);
        verifySetRanges(configs, 1, 1);

        setPreference(CellBroadcastSettings.KEY_ENABLE_CMAS_SEVERE_THREAT_ALERTS, false);
        configs = new CbConfig[]{
                new CbConfig(SmsEnvelope.SERVICE_CATEGORY_CMAS_SEVERE_THREAT,
                        SmsEnvelope.SERVICE_CATEGORY_CMAS_SEVERE_THREAT,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP2, false),
                new CbConfig(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_OBSERVED,
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_LIKELY,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP, false),
                new CbConfig(
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_OBSERVED_LANGUAGE,
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_LIKELY_LANGUAGE,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP, false),
        };
        mConfigService.enableCellBroadcastChannels(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);
        verifySetRanges(configs, 2, 1);

        setPreference(CellBroadcastSettings.KEY_ENABLE_CMAS_SEVERE_THREAT_ALERTS, true);
        configs = new CbConfig[]{
                new CbConfig(SmsEnvelope.SERVICE_CATEGORY_CMAS_SEVERE_THREAT,
                        SmsEnvelope.SERVICE_CATEGORY_CMAS_SEVERE_THREAT,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP2, true),
                new CbConfig(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_OBSERVED,
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_LIKELY,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
                new CbConfig(
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_OBSERVED_LANGUAGE,
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_LIKELY_LANGUAGE,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
        };
        mConfigService.enableCellBroadcastChannels(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);
        verifySetRanges(configs, 3, 2);
    }

    /**
     * Test enabling channels for amber alert
     */
    @Test
    @SmallTest
    public void testEnablingAmber() throws Exception {
        setPreference(CellBroadcastSettings.KEY_ENABLE_ALERTS_MASTER_TOGGLE, true);
        setPreference(CellBroadcastSettings.KEY_ENABLE_CMAS_AMBER_ALERTS, true);
        CbConfig[] configs = new CbConfig[]{
                new CbConfig(SmsEnvelope.SERVICE_CATEGORY_CMAS_CHILD_ABDUCTION_EMERGENCY,
                        SmsEnvelope.SERVICE_CATEGORY_CMAS_CHILD_ABDUCTION_EMERGENCY,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP2, true),
                new CbConfig(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY,
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
                new CbConfig(
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY_LANGUAGE,
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY_LANGUAGE,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
        };
        mConfigService.enableCellBroadcastChannels(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);
        verifySetRanges(configs, 1, 1);

        setPreference(CellBroadcastSettings.KEY_ENABLE_CMAS_AMBER_ALERTS, false);
        configs = new CbConfig[]{
                new CbConfig(SmsEnvelope.SERVICE_CATEGORY_CMAS_CHILD_ABDUCTION_EMERGENCY,
                        SmsEnvelope.SERVICE_CATEGORY_CMAS_CHILD_ABDUCTION_EMERGENCY,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP2, false),
                new CbConfig(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY,
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP, false),
                new CbConfig(
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY_LANGUAGE,
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY_LANGUAGE,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP, false),
        };
        mConfigService.enableCellBroadcastChannels(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);
        verifySetRanges(configs, 2, 1);

        setPreference(CellBroadcastSettings.KEY_ENABLE_CMAS_AMBER_ALERTS, true);
        configs = new CbConfig[]{
                new CbConfig(SmsEnvelope.SERVICE_CATEGORY_CMAS_CHILD_ABDUCTION_EMERGENCY,
                        SmsEnvelope.SERVICE_CATEGORY_CMAS_CHILD_ABDUCTION_EMERGENCY,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP2, true),
                new CbConfig(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY,
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
                new CbConfig(
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY_LANGUAGE,
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY_LANGUAGE,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
        };
        mConfigService.enableCellBroadcastChannels(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);
        verifySetRanges(configs, 3, 2);
    }

    /**
     * Test enabling channels for ETWS alert
     */
    @Test
    @SmallTest
    public void testEnablingETWS() throws Exception {
        setPreference(CellBroadcastSettings.KEY_ENABLE_ALERTS_MASTER_TOGGLE, true);
        CbConfig[] configs = new CbConfig[]{
                new CbConfig(SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_WARNING,
                        SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_AND_TSUNAMI_WARNING,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
                new CbConfig(SmsCbConstants.MESSAGE_ID_ETWS_OTHER_EMERGENCY_TYPE,
                        SmsCbConstants.MESSAGE_ID_ETWS_OTHER_EMERGENCY_TYPE,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
        };
        mConfigService.enableCellBroadcastChannels(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);
        verifySetRanges(configs, 1, 1);

        setPreference(CellBroadcastSettings.KEY_ENABLE_ALERTS_MASTER_TOGGLE, false);
        configs = new CbConfig[]{
                new CbConfig(SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_WARNING,
                        SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_AND_TSUNAMI_WARNING,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP, false),
                new CbConfig(SmsCbConstants.MESSAGE_ID_ETWS_OTHER_EMERGENCY_TYPE,
                        SmsCbConstants.MESSAGE_ID_ETWS_OTHER_EMERGENCY_TYPE,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP, false),
        };
        mConfigService.enableCellBroadcastChannels(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);
        verifySetRanges(configs, 2, 1);

        setPreference(CellBroadcastSettings.KEY_ENABLE_ALERTS_MASTER_TOGGLE, true);
        configs = new CbConfig[]{
                new CbConfig(SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_WARNING,
                        SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_AND_TSUNAMI_WARNING,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
                new CbConfig(SmsCbConstants.MESSAGE_ID_ETWS_OTHER_EMERGENCY_TYPE,
                        SmsCbConstants.MESSAGE_ID_ETWS_OTHER_EMERGENCY_TYPE,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
        };
        mConfigService.enableCellBroadcastChannels(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);
        verifySetRanges(configs, 3, 2);
    }

    /**
     * Test enabling channels for geo-fencing message
     */
    @Test
    @SmallTest
    public void testEnablingGeoFencingTriggeredChannel() throws Exception {
        CbConfig[] configs = new CbConfig[]{
                new CbConfig(SmsCbConstants.MESSAGE_ID_CMAS_GEO_FENCING_TRIGGER,
                        SmsCbConstants.MESSAGE_ID_CMAS_GEO_FENCING_TRIGGER,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
        };
        mConfigService.enableCellBroadcastChannels(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);
        verifySetRanges(configs, 1, 1);
    }

    /**
     * Test disabling channels for geo-fencing message
     */
    @Test
    @SmallTest
    public void testDisablingGeoFencingTriggeredChannel() throws Exception {
        putResources(com.android.cellbroadcastreceiver.R.array
                .geo_fencing_trigger_messages_range_strings, new String[]{
                });
        CbConfig[] configs = new CbConfig[]{
                new CbConfig(SmsCbConstants.MESSAGE_ID_CMAS_GEO_FENCING_TRIGGER,
                        SmsCbConstants.MESSAGE_ID_CMAS_GEO_FENCING_TRIGGER,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
        };
        mConfigService.enableCellBroadcastChannels(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);
        if (SdkLevel.isAtLeastU()) {
            ArgumentCaptor<List<CellBroadcastIdRange>> captorRanges =
                    ArgumentCaptor.forClass(List.class);
            verify(mTelephonyManager, times(1)).setCellBroadcastIdRanges(
                    captorRanges.capture(), any(), any());
            List<CellBroadcastIdRange> ranges = captorRanges.getAllValues().get(0);
            assertFalse(ranges.contains(new CellBroadcastIdRange(configs[0].mStartId,
                    configs[0].mEndId, configs[0].mRanType, configs[0].mEnable)));
        } else {
            verify(mMockedSmsService, times(0))
                    .disableCellBroadcastRangeForSubscriber(eq(0),
                            eq(configs[0].mStartId), eq(configs[0].mEndId),
                            eq(configs[0].mRanType));
        }
    }

    /**
     * Test enabling channels for non-cmas series message
     */
    @Test
    @SmallTest
    public void testEnablingNonCmasMessages() throws Exception {
        putResources(com.android.cellbroadcastreceiver.R.array
                .emergency_alerts_channels_range_strings, new String[]{
                    "0xA000:rat=gsm",
                });
        setPreference(CellBroadcastSettings.KEY_ENABLE_ALERTS_MASTER_TOGGLE, true);
        setPreference(CellBroadcastSettings.KEY_ENABLE_EMERGENCY_ALERTS, true);
        CbConfig[] configs = new CbConfig[]{
                new CbConfig(0xA000, 0xA000,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
        };
        mConfigService.enableCellBroadcastChannels(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);
        verifySetRanges(configs, 1, 1);

        setPreference(CellBroadcastSettings.KEY_ENABLE_ALERTS_MASTER_TOGGLE, true);
        setPreference(CellBroadcastSettings.KEY_ENABLE_EMERGENCY_ALERTS, false);
        configs = new CbConfig[]{
                new CbConfig(0xA000, 0xA000,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP, false),
        };
        mConfigService.enableCellBroadcastChannels(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);
        verifySetRanges(configs, 2, 1);
    }

    /**
     * Test enabling channels for additional channels
     */
    @Test
    @SmallTest
    public void testEnablingAdditionalChannels() throws Exception {
        putResources(com.android.cellbroadcastreceiver.R.array
                .additional_cbs_channels_strings, new String[]{
                    "0x032:type=area, emergency=false",
                });
        doReturn(true).when(mMockedSharedPreferences).getBoolean(
                eq(CellBroadcastSettings.KEY_ENABLE_AREA_UPDATE_INFO_ALERTS), eq(false));
        doReturn(mResources).when(mConfigService).getResources(anyInt(), anyString());
        CbConfig[] configs = new CbConfig[]{
                new CbConfig(0x032, 0x032,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
        };
        putResources(com.android.cellbroadcastreceiver.R.bool.config_showAreaUpdateInfoSettings,
                true);

        mConfigService.enableCellBroadcastChannels(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);
        verifySetRanges(configs, 1, 1);

        doReturn(false).when(mMockedSharedPreferences).getBoolean(
                eq(CellBroadcastSettings.KEY_ENABLE_AREA_UPDATE_INFO_ALERTS), eq(false));
        configs = new CbConfig[]{
                new CbConfig(0x032, 0x032,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP, false),
        };
        mConfigService.enableCellBroadcastChannels(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);
        verifySetRanges(configs, 2, 1);
    }

    /**
     * Test enabling channels for local test channels
     */
    @Test
    @SmallTest
    public void testEnablingLocalTestChannels() throws Exception {
        setPreference(CellBroadcastSettings.KEY_ENABLE_ALERTS_MASTER_TOGGLE, true);

        // check disable when setting is shown and preference is false
        setPreference(CellBroadcastSettings.KEY_ENABLE_STATE_LOCAL_TEST_ALERTS, false);
        putResources(com.android.cellbroadcastreceiver.R.bool
                .show_state_local_test_settings, true);
        putResources(com.android.cellbroadcastreceiver.R.bool
                .state_local_test_alerts_enabled_default, true);

        CbConfig[] configs = new CbConfig[]{
                new CbConfig(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_STATE_LOCAL_TEST,
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_STATE_LOCAL_TEST,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP, false),
        };
        mConfigService.enableCellBroadcastChannels(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);
        verifySetRanges(configs, 1, 1);

        // check disable when setting is not shown and default preference is false
        putResources(com.android.cellbroadcastreceiver.R.bool
                .show_state_local_test_settings, false);
        putResources(com.android.cellbroadcastreceiver.R.bool
                .state_local_test_alerts_enabled_default, false);

        mConfigService.enableCellBroadcastChannels(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);
        verifySetRanges(configs, 2, 2);

        // check enable when setting is not shown and default preference is true
        putResources(com.android.cellbroadcastreceiver.R.bool
                .show_state_local_test_settings, false);
        putResources(com.android.cellbroadcastreceiver.R.bool
                .state_local_test_alerts_enabled_default, true);

        configs = new CbConfig[]{
                new CbConfig(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_STATE_LOCAL_TEST,
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_STATE_LOCAL_TEST,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
        };
        mConfigService.enableCellBroadcastChannels(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);
        verifySetRanges(configs, 3, 1);

        // check enable when setting is shown and preference is true
        doReturn(true).when(mMockedSharedPreferences).getBoolean(
                eq(CellBroadcastSettings.KEY_ENABLE_STATE_LOCAL_TEST_ALERTS), eq(false));
        putResources(com.android.cellbroadcastreceiver.R.bool
                .show_state_local_test_settings, true);
        putResources(com.android.cellbroadcastreceiver.R.bool
                .state_local_test_alerts_enabled_default, false);

        mConfigService.enableCellBroadcastChannels(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);
        verifySetRanges(configs, 4, 2);
    }

    /**
     * Test handling the intent to enable channels
     */
    @Test
    @SmallTest
    public void testOnHandleIntentActionEnableChannels() throws Exception {
        List<SubscriptionInfo> sl = new ArrayList<>();
        sl.add(mMockSubscriptionInfo);
        doReturn(SubscriptionManager.INVALID_SUBSCRIPTION_ID).when(
                mMockSubscriptionInfo).getSubscriptionId();
        doReturn(mContext).when(mConfigService).getApplicationContext();
        doReturn(mMockSubscriptionManager).when(mContext).getSystemService(
                Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        doReturn(sl).when(mMockSubscriptionManager).getActiveSubscriptionInfoList();
        doReturn(CellBroadcastConfigService.ACTION_ENABLE_CHANNELS).when(mIntent).getAction();
        doNothing().when(mConfigService).enableCellBroadcastChannels(anyInt());

        Method method = CellBroadcastConfigService.class.getDeclaredMethod(
                "onHandleIntent", new Class[]{Intent.class});
        method.setAccessible(true);
        method.invoke(mConfigService, mIntent);

        verify(mConfigService, times(1)).enableCellBroadcastChannels(
                eq(SubscriptionManager.INVALID_SUBSCRIPTION_ID));

        if (!SdkLevel.isAtLeastU()) {
            doReturn(true).when(mConfigService).isMockModemRunning();
            method.invoke(mConfigService, mIntent);
            verify(mContext, times(1)).sendBroadcast(any(), anyString());

            doReturn(false).when(mConfigService).isMockModemRunning();
            method.invoke(mConfigService, mIntent);
            verify(mContext, times(1)).sendBroadcast(any(), anyString());
        }
    }

    /**
     * Test resetting cell broadcast channels before enabling channels
     */
    @Test
    @SmallTest
    public void testResetChannelsOnEnableCellBroadcastChannels() {
        mConfigService.enableCellBroadcastChannels(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);
        verify(mConfigService, times(1))
                .resetCellBroadcastChannels(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);
    }

    /**
     * Test to call Telephony API for resetting cell broadcast channels
     */
    @Test
    @SmallTest
    public void testResetCellBroadcastChannels() throws Exception {
        mConfigService.resetCellBroadcastChannels(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);
        if (SdkLevel.isAtLeastU()) {
            verify(mTelephonyManager, never())
                    .setCellBroadcastIdRanges(any(), any(), any());
        } else {
            verify(mMockedSmsService, times(1))
                    .resetAllCellBroadcastRanges(anyInt());
        }
    }

    private void verifySetRanges(CbConfig[] configs, int invocationNumForU, int invocationNum)
            throws RemoteException {
        if (SdkLevel.isAtLeastU()) {
            ArgumentCaptor<List<CellBroadcastIdRange>> captorRanges =
                    ArgumentCaptor.forClass(List.class);
            verify(mTelephonyManager, times(invocationNumForU)).setCellBroadcastIdRanges(
                    captorRanges.capture(), any(), any());
            List<CellBroadcastIdRange> ranges = captorRanges.getAllValues()
                    .get(invocationNumForU - 1);
            for (int i = 0; i < configs.length; i++) {
                assertTrue(ranges.contains(new CellBroadcastIdRange(configs[i].mStartId,
                        configs[i].mEndId, configs[i].mRanType, configs[i].mEnable)));
            }
        } else {
            for (int i = 0; i < configs.length; i++) {
                if (configs[i].mEnable) {
                    verify(mMockedSmsService, times(invocationNum))
                            .enableCellBroadcastRangeForSubscriber(eq(0),
                                    eq(configs[i].mStartId), eq(configs[i].mEndId),
                                    eq(configs[i].mRanType));
                } else {
                    verify(mMockedSmsService, times(invocationNum))
                            .disableCellBroadcastRangeForSubscriber(eq(0),
                                    eq(configs[i].mStartId), eq(configs[i].mEndId),
                                    eq(configs[i].mRanType));
                }
            }
        }
    }

    /**
     * Test enabling cell broadcast roaming channels as needed
     */
    @Test
    @SmallTest
    public void testEnableCellBroadcastRoamingChannelsAsNeeded() throws Exception {
        setPreference(CellBroadcastSettings.KEY_ENABLE_ALERTS_MASTER_TOGGLE, false);
        setPreference(CellBroadcastSettings.KEY_ENABLE_CMAS_EXTREME_THREAT_ALERTS, false);
        setPreference(CellBroadcastSettings.KEY_ENABLE_CMAS_SEVERE_THREAT_ALERTS, false);
        setPreference(CellBroadcastSettings.KEY_ENABLE_CMAS_AMBER_ALERTS, false);
        setPreference(CellBroadcastSettings.KEY_ENABLE_EMERGENCY_ALERTS, false);
        setPreference(CellBroadcastSettings.KEY_ENABLE_STATE_LOCAL_TEST_ALERTS, false);
        doReturn("").when(mMockedSharedPreferences).getString(anyString(), anyString());

        mConfigService.enableCellBroadcastChannels(
                SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);

        //do nothing if operator is empty
        verify(mConfigService, never()).getResources(anyInt(), anyString());

        Context mockContext = mock(Context.class);
        doReturn(mResources).when(mockContext).getResources();
        doReturn(mockContext).when(mContext).createConfigurationContext(any());
        doReturn("123").when(mMockedSharedPreferences).getString(anyString(), anyString());
        doReturn(mResources).when(mConfigService).getResources(anyInt(), anyString());
        putResources(com.android.cellbroadcastreceiver.R.bool.master_toggle_enabled_default, true);
        putResources(com.android.cellbroadcastreceiver.R.bool
                .extreme_threat_alerts_enabled_default, true);
        putResources(com.android.cellbroadcastreceiver.R.bool
                .severe_threat_alerts_enabled_default, true);
        putResources(com.android.cellbroadcastreceiver.R.bool.amber_alerts_enabled_default, true);
        putResources(com.android.cellbroadcastreceiver.R.bool.show_test_settings, true);
        putResources(com.android.cellbroadcastreceiver.R.bool.test_alerts_enabled_default, true);
        putResources(com.android.cellbroadcastreceiver.R.bool
                .test_exercise_alerts_enabled_default, true);
        putResources(com.android.cellbroadcastreceiver.R.bool
                .test_operator_defined_alerts_enabled_default, true);
        putResources(com.android.cellbroadcastreceiver.R.bool
                .area_update_info_alerts_enabled_default, true);
        putResources(com.android.cellbroadcastreceiver.R.bool
                .public_safety_messages_enabled_default, true);
        putResources(com.android.cellbroadcastreceiver.R.bool
                .state_local_test_alerts_enabled_default, true);
        putResources(com.android.cellbroadcastreceiver.R.bool
                .emergency_alerts_enabled_default, true);

        CbConfig[] configs = new CbConfig[]{
                new CbConfig(SmsEnvelope.SERVICE_CATEGORY_CMAS_PRESIDENTIAL_LEVEL_ALERT,
                        SmsEnvelope.SERVICE_CATEGORY_CMAS_PRESIDENTIAL_LEVEL_ALERT,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP2, true),
                new CbConfig(SmsEnvelope.SERVICE_CATEGORY_CMAS_EXTREME_THREAT,
                        SmsEnvelope.SERVICE_CATEGORY_CMAS_EXTREME_THREAT,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP2, true),
                new CbConfig(SmsEnvelope.SERVICE_CATEGORY_CMAS_SEVERE_THREAT,
                        SmsEnvelope.SERVICE_CATEGORY_CMAS_SEVERE_THREAT,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP2, true),
                new CbConfig(SmsEnvelope.SERVICE_CATEGORY_CMAS_CHILD_ABDUCTION_EMERGENCY,
                        SmsEnvelope.SERVICE_CATEGORY_CMAS_CHILD_ABDUCTION_EMERGENCY,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP2, true),
                new CbConfig(SmsEnvelope.SERVICE_CATEGORY_CMAS_TEST_MESSAGE,
                        SmsEnvelope.SERVICE_CATEGORY_CMAS_TEST_MESSAGE,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP2, true),

                new CbConfig(SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_WARNING,
                        SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_AND_TSUNAMI_WARNING,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
                new CbConfig(SmsCbConstants.MESSAGE_ID_ETWS_OTHER_EMERGENCY_TYPE,
                        SmsCbConstants.MESSAGE_ID_ETWS_OTHER_EMERGENCY_TYPE,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
                new CbConfig(SmsCbConstants.MESSAGE_ID_ETWS_TEST_MESSAGE,
                        SmsCbConstants.MESSAGE_ID_ETWS_TEST_MESSAGE,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
                new CbConfig(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL,
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
                new CbConfig(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED,
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_LIKELY,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
                new CbConfig(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_OBSERVED,
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_LIKELY,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
                new CbConfig(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY,
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
                new CbConfig(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_REQUIRED_MONTHLY_TEST,
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_REQUIRED_MONTHLY_TEST,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
                new CbConfig(SmsCbConstants.MESSAGE_ID_ETWS_TEST_MESSAGE,
                        SmsCbConstants.MESSAGE_ID_ETWS_TEST_MESSAGE,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
                new CbConfig(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_OPERATOR_DEFINED_USE,
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_OPERATOR_DEFINED_USE,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
                new CbConfig(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXERCISE,
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXERCISE,
                        SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
        };
        mConfigService.enableCellBroadcastChannels(
                SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);

        if (SdkLevel.isAtLeastU()) {
            ArgumentCaptor<List<CellBroadcastIdRange>> captorRanges =
                    ArgumentCaptor.forClass(List.class);
            verify(mTelephonyManager, times(2)).setCellBroadcastIdRanges(
                    captorRanges.capture(), any(), any());
            List<CellBroadcastIdRange> ranges = captorRanges.getAllValues().get(1);
            for (int i = 0; i < configs.length; i++) {
                boolean result = false;
                for (int j = 0; j < ranges.size(); j++) {
                    if (configs[i].mStartId >= ranges.get(j).getStartId()
                            && configs[i].mEndId <= ranges.get(j).getEndId()) {
                        result = true;
                        break;
                    }
                }
                assertTrue(result);
            }
        } else {
            ArgumentCaptor<Integer> startIds = ArgumentCaptor.forClass(Integer.class);
            ArgumentCaptor<Integer> endIds = ArgumentCaptor.forClass(Integer.class);
            ArgumentCaptor<Integer> types = ArgumentCaptor.forClass(Integer.class);
            verify(mMockedSmsService, atLeastOnce())
                    .enableCellBroadcastRangeForSubscriber(
                            anyInt(), startIds.capture(), endIds.capture(), types.capture());
            for (int i = 0; i < configs.length; i++) {
                boolean result = false;
                for (int j = 0; j < startIds.getAllValues().size(); j++) {
                    if (configs[i].mStartId >= startIds.getAllValues().get(j).intValue()
                            && configs[i].mEndId <= endIds.getAllValues().get(j).intValue()) {
                        result = true;
                        break;
                    }
                }
                assertTrue(result);
            }
        }
    }

    @Test
    public void testMergeRangesAsNeeded() {
        // Verify that there is no conflict channel. Conflicted channel means that there is overlap
        // between the ranges of disabled and the ranges of enabled.
        int[][] enableChannels = new int[][]{
                {0, 999}, {1000, 1003}, {1004, 0x0FFF}, {0x1000, 0x10FF}};
        int[][] disableChannels = new int[][]{};
        CbConfig[] expectedRanges = new CbConfig[]{
                new CbConfig(0, 999, SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
                new CbConfig(1000, 1003, SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
                new CbConfig(1004, 0x0FFF, SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
                new CbConfig(0x1000, 0x10FF, SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
                new CbConfig(0, 999, SmsCbMessage.MESSAGE_FORMAT_3GPP2, true),
                new CbConfig(1000, 1003, SmsCbMessage.MESSAGE_FORMAT_3GPP2, true),
                new CbConfig(1004, 0x0FFF, SmsCbMessage.MESSAGE_FORMAT_3GPP2, true),
                new CbConfig(0x1000, 0x10FF, SmsCbMessage.MESSAGE_FORMAT_3GPP2, true),
        };
        verifyRangesAfterMerging(enableChannels, disableChannels, true, expectedRanges);

        enableChannels = new int[][]{{500, 1050}, {1500, 1800}};
        disableChannels = new int[][]{{0, 999}, {1000, 2000}};
        expectedRanges = new CbConfig[]{
                new CbConfig(0, 499, SmsCbMessage.MESSAGE_FORMAT_3GPP, false),
                new CbConfig(500, 1050, SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
                new CbConfig(1051, 1499, SmsCbMessage.MESSAGE_FORMAT_3GPP, false),
                new CbConfig(1500, 1800, SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
                new CbConfig(1801, 2000, SmsCbMessage.MESSAGE_FORMAT_3GPP, false),
        };
        verifyRangesAfterMerging(enableChannels, disableChannels, expectedRanges);

        enableChannels = new int[][]{{0, 1050}, {1200, 1500}};
        disableChannels = new int[][]{{0, 999}, {1200, 2000}};
        expectedRanges = new CbConfig[]{
                new CbConfig(0, 1050, SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
                new CbConfig(1200, 1500, SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
                new CbConfig(1501, 2000, SmsCbMessage.MESSAGE_FORMAT_3GPP, false),
        };
        verifyRangesAfterMerging(enableChannels, disableChannels, expectedRanges);

        enableChannels = new int[][]{{0, 500}, {1000, 1500}};
        disableChannels = new int[][]{{0, 999}, {1200, 2000}};
        expectedRanges = new CbConfig[]{
                new CbConfig(0, 500, SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
                new CbConfig(501, 999, SmsCbMessage.MESSAGE_FORMAT_3GPP, false),
                new CbConfig(1000, 1500, SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
                new CbConfig(1501, 2000, SmsCbMessage.MESSAGE_FORMAT_3GPP, false),
        };
        verifyRangesAfterMerging(enableChannels, disableChannels, expectedRanges);

        enableChannels = new int[][]{{0, 999}, {1200, 2000}};
        disableChannels = new int[][]{{0, 500}, {1000, 1500}};
        expectedRanges = new CbConfig[]{
                new CbConfig(0, 999, SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
                new CbConfig(1000, 1199, SmsCbMessage.MESSAGE_FORMAT_3GPP, false),
                new CbConfig(1200, 2000, SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
        };
        verifyRangesAfterMerging(enableChannels, disableChannels, expectedRanges);

        enableChannels = new int[][]{{0, 500}};
        disableChannels = new int[][]{{200, 700}, {300, 800}};
        expectedRanges = new CbConfig[]{
                new CbConfig(0, 500, SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
                new CbConfig(501, 800, SmsCbMessage.MESSAGE_FORMAT_3GPP, false),
        };
        verifyRangesAfterMerging(enableChannels, disableChannels, expectedRanges);

        enableChannels = new int[][]{{200, 700}, {300, 800}};
        disableChannels = new int[][]{{0, 500}};
        expectedRanges = new CbConfig[]{
                new CbConfig(0, 199, SmsCbMessage.MESSAGE_FORMAT_3GPP, false),
                new CbConfig(200, 800, SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
        };
        verifyRangesAfterMerging(enableChannels, disableChannels, expectedRanges);

        enableChannels = new int[][]{{0, 500}};
        disableChannels = new int[][]{{200, 700}, {300, 800}};
        expectedRanges = new CbConfig[]{
                new CbConfig(0, 500, SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
                new CbConfig(501, 800, SmsCbMessage.MESSAGE_FORMAT_3GPP, false),
        };
        verifyRangesAfterMerging(enableChannels, disableChannels, expectedRanges);

        enableChannels = new int[][]{{100, 130}, {140, 160}};
        disableChannels = new int[][]{{120, 200}};
        expectedRanges = new CbConfig[]{
                new CbConfig(100, 130, SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
                new CbConfig(131, 139, SmsCbMessage.MESSAGE_FORMAT_3GPP, false),
                new CbConfig(140, 160, SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
                new CbConfig(161, 200, SmsCbMessage.MESSAGE_FORMAT_3GPP, false),
        };
        verifyRangesAfterMerging(enableChannels, disableChannels, expectedRanges);

        enableChannels = new int[][]{{120, 200}};
        disableChannels = new int[][]{{100, 130}, {140, 160}};
        expectedRanges = new CbConfig[]{
                new CbConfig(100, 119, SmsCbMessage.MESSAGE_FORMAT_3GPP, false),
                new CbConfig(120, 200, SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
        };
        verifyRangesAfterMerging(enableChannels, disableChannels, expectedRanges);

        enableChannels = new int[][]{{100, 140}, {120, 200}};
        disableChannels = new int[][]{{150, 170}, {160, 250}};
        expectedRanges = new CbConfig[]{
                new CbConfig(100, 200, SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
                new CbConfig(201, 250, SmsCbMessage.MESSAGE_FORMAT_3GPP, false),
        };
        verifyRangesAfterMerging(enableChannels, disableChannels, expectedRanges);

        enableChannels = new int[][]{{200, 250}, {270, 290}};
        disableChannels = new int[][]{{100, 300}, {260, 280}};
        expectedRanges = new CbConfig[]{
                new CbConfig(100, 199, SmsCbMessage.MESSAGE_FORMAT_3GPP, false),
                new CbConfig(200, 250, SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
                new CbConfig(251, 269, SmsCbMessage.MESSAGE_FORMAT_3GPP, false),
                new CbConfig(270, 290, SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
                new CbConfig(291, 300, SmsCbMessage.MESSAGE_FORMAT_3GPP, false),
        };
        verifyRangesAfterMerging(enableChannels, disableChannels, expectedRanges);

        enableChannels = new int[][]{{4370, 4370}, {4372, 4372}};
        disableChannels = new int[][]{{4370, 4370}, {4372, 4372}};
        expectedRanges = new CbConfig[]{
                new CbConfig(4370, 4370, SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
                new CbConfig(4372, 4372, SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
        };
        verifyRangesAfterMerging(enableChannels, disableChannels, expectedRanges);

        enableChannels = new int[][]{{0, 100}, {150, 300}};
        disableChannels = new int[][]{{80, 200}};
        expectedRanges = new CbConfig[]{
                new CbConfig(0, 100, SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
                new CbConfig(101, 149, SmsCbMessage.MESSAGE_FORMAT_3GPP, false),
                new CbConfig(150, 300, SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
        };
        verifyRangesAfterMerging(enableChannels, disableChannels, expectedRanges);

        enableChannels = new int[][]{{100, 200}, {400, 700}};
        disableChannels = new int[][]{{100, 300}, {400, 500}};
        expectedRanges = new CbConfig[]{
                new CbConfig(100, 200, SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
                new CbConfig(201, 300, SmsCbMessage.MESSAGE_FORMAT_3GPP, false),
                new CbConfig(400, 700, SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
        };
        verifyRangesAfterMerging(enableChannels, disableChannels, expectedRanges);

        enableChannels = new int[][]{{100, 200}, {601, 601}};
        disableChannels = new int[][]{{100, 201}, {300, 400}, {500, 600}};
        expectedRanges = new CbConfig[]{
                new CbConfig(100, 200, SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
                new CbConfig(201, 201, SmsCbMessage.MESSAGE_FORMAT_3GPP, false),
                new CbConfig(300, 400, SmsCbMessage.MESSAGE_FORMAT_3GPP, false),
                new CbConfig(500, 600, SmsCbMessage.MESSAGE_FORMAT_3GPP, false),
                new CbConfig(601, 601, SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
        };
        verifyRangesAfterMerging(enableChannels, disableChannels, expectedRanges);

        enableChannels = new int[][]{{100, 200}, {150, 300}};
        disableChannels = new int[][]{{250, 400}, {270, 500}};
        expectedRanges = new CbConfig[]{
                new CbConfig(100, 300, SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
                new CbConfig(301, 500, SmsCbMessage.MESSAGE_FORMAT_3GPP, false),
        };
        verifyRangesAfterMerging(enableChannels, disableChannels, expectedRanges);

        enableChannels = new int[][]{{100, 200}, {150, 300}, {350, 370}};
        disableChannels = new int[][]{{250, 400}, {270, 500}};
        expectedRanges = new CbConfig[]{
                new CbConfig(100, 300, SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
                new CbConfig(301, 349, SmsCbMessage.MESSAGE_FORMAT_3GPP, false),
                new CbConfig(350, 370, SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
                new CbConfig(371, 500, SmsCbMessage.MESSAGE_FORMAT_3GPP, false),
        };
        verifyRangesAfterMerging(enableChannels, disableChannels, expectedRanges);

        enableChannels = new int[][]{{0, 200}, {50, 150}};
        disableChannels = new int[][]{{170, 300}};
        expectedRanges = new CbConfig[]{
                new CbConfig(0, 200, SmsCbMessage.MESSAGE_FORMAT_3GPP, true),
                new CbConfig(201, 300, SmsCbMessage.MESSAGE_FORMAT_3GPP, false),
        };
        verifyRangesAfterMerging(enableChannels, disableChannels, expectedRanges);
    }

    private void verifyRangesAfterMerging(int[][] enableChannels, int[][] disableChannels,
            CbConfig[] expectedRanges) {
        verifyRangesAfterMerging(enableChannels, disableChannels, false, expectedRanges);
    }
    private void verifyRangesAfterMerging(int[][] enableChannels, int[][] disableChannels,
            boolean differentType, CbConfig[] expectedRanges) {
        List<CbConfig> config = new ArrayList<>();
        for (int i = 0; i < enableChannels.length; i++) {
            config.add(new CbConfig(enableChannels[i][0], enableChannels[i][1],
                    SmsCbMessage.MESSAGE_FORMAT_3GPP, true));
            if (differentType) {
                config.add(new CbConfig(enableChannels[i][0], enableChannels[i][1],
                        SmsCbMessage.MESSAGE_FORMAT_3GPP2, true));
            }
        }
        for (int i = 0; i < disableChannels.length; i++) {
            config.add(new CbConfig(disableChannels[i][0], disableChannels[i][1],
                    SmsCbMessage.MESSAGE_FORMAT_3GPP, false));
            if (differentType) {
                config.add(new CbConfig(enableChannels[i][0], enableChannels[i][1],
                        SmsCbMessage.MESSAGE_FORMAT_3GPP2, true));
            }
        }
        List<CbConfig> ranges = mConfigService.mergeConfigAsNeeded(config);
        assertEquals(expectedRanges.length, ranges.size());
        for (int i = 0; i < expectedRanges.length; i++) {
            assertEquals(expectedRanges[i].mStartId, ranges.get(i).mStartId);
            assertEquals(expectedRanges[i].mEndId, ranges.get(i).mEndId);
            assertEquals(expectedRanges[i].mEnable, ranges.get(i).mEnable);
            assertEquals(expectedRanges[i].mRanType, ranges.get(i).mRanType);
        }
    }

    /**
     * Test resetting cell broadcast settings as needed
     */
    @Test
    @SmallTest
    public void testResetCellBroadcastSettingsAsNeeded() throws Exception {
        doNothing().when(mConfigService).resetAllPreferences();
        doReturn(CellBroadcastConfigService.ACTION_RESET_SETTINGS_AS_NEEDED)
                .when(mIntent).getAction();
        doReturn(mResources).when(mConfigService).getResources(anyInt(), eq(null));

        boolean[][] combNoResetting = {
                // The settings are changed by the user
                {true, true, true}, {true, true, false}, {true, false, true}, {true, false, false},
                // The master toggle values of preferences and config are same
                {false, true, true}, {false, false, false}};

        Method method = CellBroadcastConfigService.class.getDeclaredMethod(
                "onHandleIntent", new Class[]{Intent.class});
        method.setAccessible(true);

        // Verify the settings preference not to be reset
        for (int i = 0; i < combNoResetting.length; i++) {
            setPreference(CellBroadcastSettings.ANY_PREFERENCE_CHANGED_BY_USER,
                    combNoResetting[i][0]);
            setPreference(CellBroadcastSettings.KEY_ENABLE_ALERTS_MASTER_TOGGLE,
                    combNoResetting[i][1]);
            putResources(com.android.cellbroadcastreceiver.R.bool.master_toggle_enabled_default,
                    combNoResetting[i][2]);

            method.invoke(mConfigService, mIntent);

            verify(mConfigService, never()).resetAllPreferences();
        }

        boolean[][] combResetting = {{false, true, false}, {false, false, true}};

        // Verify the settings preference to be reset
        for (int i = 0, c = 0; i < combResetting.length; i++) {
            setPreference(CellBroadcastSettings.ANY_PREFERENCE_CHANGED_BY_USER,
                    combResetting[i][0]);
            setPreference(CellBroadcastSettings.KEY_ENABLE_ALERTS_MASTER_TOGGLE,
                    combResetting[i][1]);
            putResources(com.android.cellbroadcastreceiver.R.bool.master_toggle_enabled_default,
                    combResetting[i][2]);

            method.invoke(mConfigService, mIntent);

            verify(mConfigService, times(++c)).resetAllPreferences();
        }
    }
}
