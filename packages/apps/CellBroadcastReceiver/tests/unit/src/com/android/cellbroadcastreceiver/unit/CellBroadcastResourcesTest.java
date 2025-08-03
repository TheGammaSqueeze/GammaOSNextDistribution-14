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

package com.android.cellbroadcastreceiver.unit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.telephony.SmsCbCmasInfo;
import android.telephony.SmsCbEtwsInfo;
import android.telephony.SmsCbLocation;
import android.telephony.SmsCbMessage;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import com.android.cellbroadcastreceiver.CellBroadcastChannelManager;
import com.android.cellbroadcastreceiver.CellBroadcastResources;
import com.android.cellbroadcastreceiver.R;
import com.android.internal.telephony.gsm.SmsCbConstants;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Method;

public class CellBroadcastResourcesTest {

    @Mock
    private Context mContext;

    @Mock
    private Resources mResources;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        doReturn(mResources).when(mContext).getResources();
        String stringResultToReturn = "";
        doReturn(stringResultToReturn).when(mResources).getString(anyInt());
        CellBroadcastChannelManager.clearAllCellBroadcastChannelRanges();
    }

    @Test
    public void testGetMessageDetails() {
        SmsCbMessage smsCbMessage = new SmsCbMessage(1, 2, 0, new SmsCbLocation(),
                SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_AND_TSUNAMI_WARNING, "language", "body",
                SmsCbMessage.MESSAGE_PRIORITY_EMERGENCY, null, new SmsCbCmasInfo(0, 2, 3, 4, 5, 6),
                0, 1);
        CharSequence details = CellBroadcastResources.getMessageDetails(mContext, true,
                smsCbMessage, -1, false,
                null);
        assertNotNull(details);
    }

    @Test
    public void testGetMessageDetailsCmasMessage() {
        SmsCbMessage smsCbMessage = new SmsCbMessage(1, 2, 0, new SmsCbLocation(),
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL, "language", "body",
                SmsCbMessage.MESSAGE_PRIORITY_EMERGENCY, null, new SmsCbCmasInfo(0, 2, 3, 4, 5, 6),
                0, 1);
        CharSequence details = CellBroadcastResources.getMessageDetails(mContext, true,
                smsCbMessage, -1, false,
                null);
        assertNotNull(details);
    }

    @Test
    public void testGetCmasCategoryResId() throws Exception {
        int[] cats = {SmsCbCmasInfo.CMAS_CATEGORY_GEO, SmsCbCmasInfo.CMAS_CATEGORY_MET,
                SmsCbCmasInfo.CMAS_CATEGORY_SAFETY, SmsCbCmasInfo.CMAS_CATEGORY_SECURITY,
                SmsCbCmasInfo.CMAS_CATEGORY_RESCUE, SmsCbCmasInfo.CMAS_CATEGORY_FIRE,
                SmsCbCmasInfo.CMAS_CATEGORY_HEALTH, SmsCbCmasInfo.CMAS_CATEGORY_ENV,
                SmsCbCmasInfo.CMAS_CATEGORY_TRANSPORT, SmsCbCmasInfo.CMAS_CATEGORY_INFRA,
                SmsCbCmasInfo.CMAS_CATEGORY_CBRNE, SmsCbCmasInfo.CMAS_CATEGORY_OTHER};
        for (int c : cats) {
            assertNotEquals(0, getCmasCategoryResId(new SmsCbCmasInfo(0, c, 0, 0, 0, 0)));
        }

        assertEquals(0, getCmasCategoryResId(new SmsCbCmasInfo(
                0, SmsCbCmasInfo.CMAS_CATEGORY_UNKNOWN, 0, 0, 0, 0)));
    }

    @Test
    public void testGetDialogTitleResource() throws Exception {
        mockSubscriptionManager();
        Context mockContext2 = mock(Context.class);
        doReturn(mResources).when(mockContext2).getResources();
        Configuration config = new Configuration();
        doReturn(config).when(mResources).getConfiguration();
        doReturn(mockContext2).when(mContext).createConfigurationContext(any());

        setFakeSharedPreferences();
        putResources(R.array.cmas_alert_extreme_channels_range_strings, new String[]{
                    "0x1113-0x1114:rat=gsm",
                    "0x1001-0x1001:rat=cdma",
                    "0x1120-0x1121:rat=gsm",
                });
        putResources(R.array.public_safety_messages_channels_range_strings, new String[]{
                    "0x112C:rat=gsm, emergency=true",
                    "0x112D:rat=gsm, emergency=true",
                });
        SmsCbMessage smsCbMessage = new SmsCbMessage(1, 2, 0, new SmsCbLocation(),
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED, "language", "body",
                SmsCbMessage.MESSAGE_PRIORITY_EMERGENCY, null, new SmsCbCmasInfo(0, 2, 3, 4, 5, 6),
                0, 1);
        int expectedResult = getDialogTitleResource(mContext, smsCbMessage);

        SmsCbMessage testSmsCbMessage = new SmsCbMessage(1, 2, 0, new SmsCbLocation(),
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED, "language", "body",
                SmsCbMessage.MESSAGE_PRIORITY_EMERGENCY, null, new SmsCbCmasInfo(0, 2, 3,
                SmsCbCmasInfo.CMAS_SEVERITY_EXTREME, SmsCbCmasInfo.CMAS_URGENCY_IMMEDIATE,
                SmsCbCmasInfo.CMAS_CERTAINTY_LIKELY), 0, 1);

        int result = getDialogTitleResource(mContext, testSmsCbMessage);
        assertEquals(expectedResult, result);

        SmsCbMessage testPublicSafetyMessage = new SmsCbMessage(1, 2, 0, new SmsCbLocation(),
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PUBLIC_SAFETY, "language", "body",
                SmsCbMessage.MESSAGE_PRIORITY_EMERGENCY, null, new SmsCbCmasInfo(0, 2, 3,
                SmsCbCmasInfo.CMAS_SEVERITY_EXTREME, SmsCbCmasInfo.CMAS_URGENCY_IMMEDIATE,
                SmsCbCmasInfo.CMAS_CERTAINTY_LIKELY), 0, 1);
        result = getDialogTitleResource(mContext, testPublicSafetyMessage);
        assertNotEquals(expectedResult, result);

        // received other channel message, check the res id matching service category
        final int[] expectedResources = {
                R.string.pws_other_message_identifiers, R.string.cmas_presidential_level_alert,
                R.string.cmas_severe_alert, R.string.cmas_amber_alert,
                R.string.cmas_required_monthly_test,
                R.string.cmas_exercise_alert, R.string.cmas_operator_defined_alert,
                R.string.state_local_test_alert};
        putResources(R.array.emergency_alerts_channels_range_strings,
                new String[]{"0x1123:rat=gsm, emergency=true"});
        putResources(R.array.cmas_presidential_alerts_channels_range_strings,
                new String[]{"0x1112:rat=gsm, emergency=true"});
        putResources(R.array.cmas_alerts_severe_range_strings,
                new String[]{"0x1115:rat=gsm, emergency=true"});
        putResources(R.array.cmas_amber_alerts_channels_range_strings,
                new String[]{"0x111B:rat=gsm, emergency=true"});
        putResources(R.array.required_monthly_test_range_strings,
                new String[]{"0x111C:rat=gsm, emergency=true"});
        putResources(R.array.exercise_alert_range_strings,
                new String[]{"0x111D:rat=gsm, emergency=true"});
        putResources(R.array.operator_defined_alert_range_strings,
                new String[]{"0x111E:rat=gsm, emergency=true"});
        putResources(R.array.state_local_test_alert_range_strings,
                new String[]{"0x1122:rat=gsm, emergency=true"});

        final int[] serviceCategory =
                {0x1123, 0x1112, 0x1115, 0x111B, 0x111C, 0x111D, 0x111E, 0x1122};
        for (int i = 0; i < serviceCategory.length; i++) {
            SmsCbMessage message = new SmsCbMessage(0, 0, 0, null,
                    serviceCategory[i], "", "", 0, null,
                    null, 0, SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);
            assertEquals(expectedResources[i], getDialogTitleResource(mContext, message));
        }
    }

    @Test
    public void testGetCmasResponseResId() throws Exception {
        int[] resps = {SmsCbCmasInfo.CMAS_RESPONSE_TYPE_SHELTER,
                SmsCbCmasInfo.CMAS_RESPONSE_TYPE_EVACUATE,
                SmsCbCmasInfo.CMAS_RESPONSE_TYPE_PREPARE,
                SmsCbCmasInfo.CMAS_RESPONSE_TYPE_EXECUTE,
                SmsCbCmasInfo.CMAS_RESPONSE_TYPE_MONITOR,
                SmsCbCmasInfo.CMAS_RESPONSE_TYPE_AVOID,
                SmsCbCmasInfo.CMAS_RESPONSE_TYPE_ASSESS,
                SmsCbCmasInfo.CMAS_RESPONSE_TYPE_NONE};
        for (int r : resps) {
            assertNotEquals(0, getCmasResponseResId(new SmsCbCmasInfo(0, 0, r, 0, 0, 0)));
        }

        assertEquals(0, getCmasResponseResId(new SmsCbCmasInfo(
                0, 0, SmsCbCmasInfo.CMAS_RESPONSE_TYPE_UNKNOWN, 0, 0, 0)));
    }

    @Test
    public void testGetSmsSenderAddressResource() throws Exception {
        mockSubscriptionManager();
        mockTelephonyManager();
        setFakeSharedPreferences();
        doReturn(new Configuration()).when(mResources).getConfiguration();
        doReturn(mContext).when(mContext).createConfigurationContext(any());

        final int[] expectedResources = {
                R.string.sms_cb_sender_name_presidential, R.string.sms_cb_sender_name_emergency,
                R.string.sms_cb_sender_name_public_safety, R.string.sms_cb_sender_name_default};
        putResources(R.array.cmas_presidential_alerts_channels_range_strings,
                new String[]{"0x1112:rat=gsm, emergency=true"});
        putResources(R.array.emergency_alerts_channels_range_strings,
                new String[]{"0x111B:rat=gsm, emergency=true"});
        putResources(R.array.public_safety_messages_channels_range_strings,
                new String[]{"0x112C:rat=gsm, emergency=true"});
        putResources(R.array.cmas_alert_extreme_channels_range_strings,
                new String[]{"0x1113:rat=gsm, emergency=true"});

        final String[] expectedStrings = {
                "Wireless emergency alerts(presidential)", "Wireless emergency alerts(emergency)",
                "Informational notification", "Wireless emergency alerts(default)"};
        doReturn(expectedStrings[0]).when(mResources).getText(eq(expectedResources[0]));
        doReturn(expectedStrings[1]).when(mResources).getText(eq(expectedResources[1]));
        doReturn(expectedStrings[2]).when(mResources).getText(eq(expectedResources[2]));
        doReturn(expectedStrings[3]).when(mResources).getText(eq(expectedResources[3]));

        // check the sms sender address resource id and string
        final int[] serviceCategory = {0x1112, 0x111B, 0x112C, 0x1113};
        for (int i = 0; i < serviceCategory.length; i++) {
            SmsCbMessage message = new SmsCbMessage(0, 0, 0, null,
                    serviceCategory[i], "", "", 0, null,
                    null, 0, SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);
            assertEquals(expectedResources[i],
                    CellBroadcastResources.getSmsSenderAddressResource(mContext, message));
            assertEquals(expectedStrings[i],
                    CellBroadcastResources.getSmsSenderAddressResourceEnglishString(mContext,
                            message));
        }
    }

    @Test
    public void testGetDialogPictogramResource() throws Exception {
        final int[] expectedResources = {R.drawable.pict_icon_earthquake,
                R.drawable.pict_icon_earthquake, R.drawable.pict_icon_tsunami};
        final int[] warningType = {SmsCbEtwsInfo.ETWS_WARNING_TYPE_EARTHQUAKE,
                SmsCbEtwsInfo.ETWS_WARNING_TYPE_EARTHQUAKE_AND_TSUNAMI,
                SmsCbEtwsInfo.ETWS_WARNING_TYPE_TSUNAMI};

        // when SmsCbEtwsInfo exist, check the drawable res id that matches the warningType
        for (int i = 0; i < warningType.length; i++) {
            SmsCbMessage message = new SmsCbMessage(0, 0, 0, null, 0, "", "", 0,
                    new SmsCbEtwsInfo(warningType[i], false, false, false, null),
                    null, 0, SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);
            assertEquals(expectedResources[i], getDialogPictogramResource(mContext, message));
        }

        mockTelephonyManager();
        setFakeSharedPreferences();
        putResources(R.array.additional_cbs_channels_strings,
                new String[]{"0xA800:type=etws_earthquake, emergency=true",
                        "0xAFEE:type=etws_tsunami, emergency=true",
                        "0xAC00:type=other, emergency=true",
                        "0xA802:type=test, emergency=false"});
        // received an additional channel message, check the drawable res id matching alertType
        final int[] expectedResources2 = {R.drawable.pict_icon_earthquake,
                R.drawable.pict_icon_tsunami, -1, -1};
        final int[] serviceCategory = {0xA800, 0xAFEE, 0xAC00, 0xA802};
        for (int i = 0; i < serviceCategory.length; i++) {
            SmsCbMessage message = new SmsCbMessage(0, 0, 0, null,
                    serviceCategory[i], "", "", 0, null,
                    null, 0, SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);
            assertEquals(expectedResources2[i], getDialogPictogramResource(mContext, message));
        }
    }

    @Test
    public void testOverrideTranslation() throws Exception {
        final String expectedStrings = "Presidential alert";

        doReturn(expectedStrings).when(mResources)
                .getText(R.string.cmas_presidential_level_alert);
        doReturn(false).when(mResources)
                .getBoolean(R.bool.override_alert_title_language_to_match_message_locale);

        Context mockContext2 = mock(Context.class);
        doReturn(mResources).when(mockContext2).getResources();
        Configuration config = new Configuration();
        doReturn(config).when(mResources).getConfiguration();
        doReturn(mockContext2).when(mContext).createConfigurationContext(any());

        String strResult = CellBroadcastResources.overrideTranslation(mContext,
                R.string.cmas_presidential_level_alert, mResources, "en");
        verify(mContext, times(0)).createConfigurationContext(any());
        assertEquals(expectedStrings, strResult);

        doReturn(true).when(mResources)
                .getBoolean(R.bool.override_alert_title_language_to_match_message_locale);

        CellBroadcastResources.overrideTranslation(mContext,
                R.string.cmas_presidential_level_alert, mResources, "en");

        verify(mContext, times(1)).createConfigurationContext(any());
    }

    @Test
    public void testGetDialogTitleResourceForExistEtwsWarningInfo() throws Exception {
        setFakeSharedPreferences();

        // when SmsCbEtwsInfo exist, check the string res id that matches the warningType
        final int[] expectedResources = {R.string.etws_earthquake_warning,
                R.string.etws_tsunami_warning, R.string.etws_earthquake_and_tsunami_warning,
                R.string.etws_test_message, R.string.etws_other_emergency_type,
                R.string.etws_other_emergency_type};
        final int[] warningType = {SmsCbEtwsInfo.ETWS_WARNING_TYPE_EARTHQUAKE,
                SmsCbEtwsInfo.ETWS_WARNING_TYPE_TSUNAMI,
                SmsCbEtwsInfo.ETWS_WARNING_TYPE_EARTHQUAKE_AND_TSUNAMI,
                SmsCbEtwsInfo.ETWS_WARNING_TYPE_TEST_MESSAGE,
                SmsCbEtwsInfo.ETWS_WARNING_TYPE_OTHER_EMERGENCY,
                SmsCbEtwsInfo.ETWS_WARNING_TYPE_UNKNOWN};
        for (int i = 0; i < warningType.length; i++) {
            SmsCbMessage message = new SmsCbMessage(0, 0, 0, null, 0, "", "", 0,
                    new SmsCbEtwsInfo(warningType[i], false, false, false, null),
                    null, 0, SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);
            assertEquals(expectedResources[i], getDialogTitleResource(mContext, message));
        }
    }

    @Test
    public void testGetDialogTitleResourceForAdditionalChannel() throws Exception {
        setFakeSharedPreferences();

        // received an additional channel message, check the res id matching alertType
        CellBroadcastChannelManager.clearAllCellBroadcastChannelRanges();
        mockTelephonyManager();
        putResources(R.array.additional_cbs_channels_strings,
                new String[]{"0xAC01:type=default, emergency=true",
                        "0xA800:type=etws_earthquake, emergency=true",
                        "0xAFEE:type=etws_tsunami, emergency=true",
                        "0xA802:type=test, emergency=true",
                        "0xAC00:type=other, emergency=true",
                        "0xA803:type=etws_default, emergency=true",
                        "0xA804:type=mute, emergency=true",
                        "0xA805:type=test, emergency=false"});
        final int[] expectedResources = {R.string.pws_other_message_identifiers,
                R.string.etws_earthquake_warning, R.string.etws_tsunami_warning,
                R.string.etws_test_message,
                R.string.etws_other_emergency_type, R.string.etws_other_emergency_type,
                R.string.pws_other_message_identifiers, R.string.cb_other_message_identifiers};
        final int[] serviceCategory =
                {0xAC01, 0xA800, 0xAFEE, 0xA802, 0xAC00, 0xA803, 0xA804, 0xA805};
        for (int i = 0; i < serviceCategory.length; i++) {
            SmsCbMessage message = new SmsCbMessage(0, 0, 0, null, serviceCategory[i], "", "", 0,
                    null, null, 0, SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);
            assertEquals(expectedResources[i], getDialogTitleResource(mContext, message));
        }
    }

    private int getCmasCategoryResId(SmsCbCmasInfo info) throws Exception {
        Method method = CellBroadcastResources.class.getDeclaredMethod(
                "getCmasCategoryResId", SmsCbCmasInfo.class);
        method.setAccessible(true);
        return (int) method.invoke(null, info);
    }

    private int getCmasResponseResId(SmsCbCmasInfo info) throws Exception {
        Method method = CellBroadcastResources.class.getDeclaredMethod(
                "getCmasResponseResId", SmsCbCmasInfo.class);
        method.setAccessible(true);
        return (int) method.invoke(null, info);
    }

    private int getDialogTitleResource(Context context, SmsCbMessage info) throws Exception {
        Method method = CellBroadcastResources.class.getDeclaredMethod(
                "getDialogTitleResource", Context.class, SmsCbMessage.class);
        method.setAccessible(true);
        return (int) method.invoke(null, context, info);
    }

    private int getDialogPictogramResource(Context context, SmsCbMessage info) throws Exception {
        Method method = CellBroadcastResources.class.getDeclaredMethod(
                "getDialogPictogramResource", Context.class, SmsCbMessage.class);
        method.setAccessible(true);
        return (int) method.invoke(null, context, info);
    }

    void putResources(int id, String[] values) {
        doReturn(values).when(mResources).getStringArray(eq(id));
    }

    private void setFakeSharedPreferences() {
        FakeSharedPreferences mFakeSharedPreferences = new FakeSharedPreferences();
        doReturn(mFakeSharedPreferences).when(mContext).getSharedPreferences(anyString(), anyInt());
    }

    private void mockSubscriptionManager() {
        SubscriptionManager mockSubManager = mock(SubscriptionManager.class);
        doReturn(mockSubManager).when(mContext).getSystemService(
                eq(Context.TELEPHONY_SUBSCRIPTION_SERVICE));
        SubscriptionInfo mockSubInfo = mock(SubscriptionInfo.class);
        doReturn(mockSubInfo).when(mockSubManager).getActiveSubscriptionInfo(anyInt());
    }

    private void mockTelephonyManager() {
        TelephonyManager mMockTelephonyManager = mock(TelephonyManager.class);
        doReturn(mMockTelephonyManager).when(mMockTelephonyManager)
                .createForSubscriptionId(anyInt());
        doReturn(Context.TELEPHONY_SERVICE).when(mContext).getSystemServiceName(
                TelephonyManager.class);
        doReturn(mMockTelephonyManager).when(mContext).getSystemService(Context.TELEPHONY_SERVICE);
    }
}
