/*
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
 * limitations under the License
 */

package com.android.cellbroadcastreceiver.unit;

import static com.android.cellbroadcastreceiver.CellBroadcastAlertAudio.ALERT_AUDIO_TONE_TYPE;
import static com.android.cellbroadcastreceiver.CellBroadcastAlertService.PROP_DISPLAY;
import static com.android.cellbroadcastreceiver.CellBroadcastAlertService.SHOW_NEW_ALERT_ACTION;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Handler;
import android.os.IPowerManager;
import android.os.Looper;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.provider.Telephony;
import android.service.notification.StatusBarNotification;
import android.telephony.AccessNetworkConstants;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SmsCbCmasInfo;
import android.telephony.SmsCbEtwsInfo;
import android.telephony.SmsCbLocation;
import android.telephony.SmsCbMessage;
import android.telephony.TelephonyManager;
import android.view.Display;

import com.android.cellbroadcastreceiver.CellBroadcastAlertAudio;
import com.android.cellbroadcastreceiver.CellBroadcastAlertService;
import com.android.cellbroadcastreceiver.CellBroadcastSettings;
import com.android.internal.telephony.gsm.SmsCbConstants;
import com.android.modules.utils.build.SdkLevel;

import org.junit.After;
import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class CellBroadcastAlertServiceTest extends
        CellBroadcastServiceTestCase<CellBroadcastAlertService> {
    @Mock
    ServiceState mockSS;

    @Mock
    SharedPreferences.Editor mMockEditor;

    public CellBroadcastAlertServiceTest() {
        super(CellBroadcastAlertService.class);
    }

    static SmsCbMessage createMessage(int serialNumber) {
        return createMessageForCmasMessageClass(serialNumber,
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL, 0);
    }

    static SmsCbMessage createMessageForCmasMessageClass(int serialNumber,
            int serviceCategory, int cmasMessageClass) {
        return new SmsCbMessage(1, 2, serialNumber, new SmsCbLocation(), serviceCategory,
                "language", "body",
                SmsCbMessage.MESSAGE_PRIORITY_EMERGENCY, null,
                new SmsCbCmasInfo(cmasMessageClass, 2, 3, 4, 5, 6),
                0, 1);
    }

    static SmsCbMessage createCmasMessageWithLanguage(int serialNumber, int serviceCategory,
            int cmasMessageClass, String language) {
        return new SmsCbMessage(1, 2, serialNumber, new SmsCbLocation(),
                serviceCategory, language, "body",
                SmsCbMessage.MESSAGE_PRIORITY_EMERGENCY, null,
                new SmsCbCmasInfo(cmasMessageClass, 2, 3, 4, 5, 6),
                0, 1);
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
        // No roaming supported by default
        doReturn("").when(mMockedSharedPreferences).getString(anyString(), anyString());
        doReturn(mMockEditor).when(mMockedSharedPreferences).edit();
        doReturn(mMockEditor).when(mMockEditor).putBoolean(anyString(), anyBoolean());
        doNothing().when(mMockEditor).apply();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    private static void compareEtwsWarningInfo(SmsCbEtwsInfo info1, SmsCbEtwsInfo info2) {
        if (info1 == info2) return;
        assertEquals(info1.toString(), info2.toString());
        assertArrayEquals(info1.getPrimaryNotificationSignature(),
                info2.getPrimaryNotificationSignature());
        assertEquals(info1.isPrimary(), info2.isPrimary());
    }

    private static void compareCmasWarningInfo(SmsCbCmasInfo info1, SmsCbCmasInfo info2) {
        if (info1 == info2) return;
        assertEquals(info1.getCategory(), info2.getCategory());
        assertEquals(info1.getCertainty(), info2.getCertainty());
        assertEquals(info1.getMessageClass(), info2.getMessageClass());
        assertEquals(info1.getResponseType(), info2.getResponseType());
        assertEquals(info1.getSeverity(), info2.getSeverity());
        assertEquals(info1.getUrgency(), info2.getUrgency());
    }

    private static void compareCellBroadCastMessage(SmsCbMessage m1, SmsCbMessage m2) {
        if (m1 == m2) return;
        assertEquals(m1.getCmasWarningInfo().getMessageClass(),
                m2.getCmasWarningInfo().getMessageClass());
        compareCmasWarningInfo(m1.getCmasWarningInfo(), m2.getCmasWarningInfo());
        compareEtwsWarningInfo(m1.getEtwsWarningInfo(), m2.getEtwsWarningInfo());
        assertEquals(m1.getLanguageCode(), m2.getLanguageCode());
        assertEquals(m1.getMessageBody(), m2.getMessageBody());
        assertEquals(m1.getServiceCategory(), m2.getServiceCategory());
        assertEquals(m1.getSerialNumber(), m2.getSerialNumber());
    }

    private void sendMessage(int serialNumber) {
        Intent intent = new Intent(mContext, CellBroadcastAlertService.class);
        intent.setAction(Telephony.Sms.Intents.ACTION_SMS_EMERGENCY_CB_RECEIVED);

        SmsCbMessage m = createMessage(serialNumber);
        sendMessage(m, intent);
    }

    private void sendMessageForCmasMessageClass(int serialNumber, int cmasMessageClass) {
        Intent intent = new Intent(mContext, CellBroadcastAlertService.class);
        intent.setAction(Telephony.Sms.Intents.ACTION_SMS_EMERGENCY_CB_RECEIVED);

        SmsCbMessage m = createMessageForCmasMessageClass(serialNumber, cmasMessageClass,
                cmasMessageClass);
        sendMessage(m, intent);
    }

    private void sendMessageForCmasMessageClassAndLanguage(int serialNumber, int cmasMessageClass,
            String language) {
        Intent intent = new Intent(mContext, CellBroadcastAlertService.class);
        intent.setAction(Telephony.Sms.Intents.ACTION_SMS_EMERGENCY_CB_RECEIVED);

        SmsCbMessage m = createCmasMessageWithLanguage(serialNumber, cmasMessageClass,
                cmasMessageClass, language);
        sendMessage(m, intent);
    }

    private void sendMessage(SmsCbMessage m, Intent intent) {
        intent.putExtra("message", m);
        startService(intent);
    }

    private void waitForServiceIntent() {
        waitFor(() -> mServiceIntentToVerify != null);
    }

    // Test handleCellBroadcastIntent method
    @InstrumentationTest
    // This test has a module dependency, so it is disabled for OEM testing because it is not a true
    // unit test
    public void testHandleCellBroadcastIntent() {
        doReturn(new String[]{"0x1112:rat=gsm, emergency=true"})
                .when(mResources).getStringArray(
                        eq(com.android.cellbroadcastreceiver.R.array.etws_alerts_range_strings));
        enablePreference(CellBroadcastSettings.KEY_ENABLE_ALERTS_MASTER_TOGGLE);
        sendMessage(987654321);
        waitForServiceIntent();

        assertEquals(SHOW_NEW_ALERT_ACTION, mServiceIntentToVerify.getAction());

        SmsCbMessage cbmTest = (SmsCbMessage) mServiceIntentToVerify.getExtras().get("message");
        SmsCbMessage cbm = createMessage(987654321);

        compareCellBroadCastMessage(cbm, cbmTest);
    }

    // Test testHandleCellBroadcastIntentDomesticRoaming method
    @InstrumentationTest
    // This test has a module dependency, so it is disabled for OEM testing because it is not a true
    // unit test
    public void testHandleCellBroadcastIntentDomesticRoaming() throws Exception {
        doReturn(mockSS).when(mMockedTelephonyManager).getServiceState();
        NetworkRegistrationInfo mockNeRegInfo = new NetworkRegistrationInfo(
                NetworkRegistrationInfo.DOMAIN_CS, AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                NetworkRegistrationInfo.REGISTRATION_STATE_ROAMING, 0, 0, false,
                null, null, "", true, 0, 0, 0);
        mockNeRegInfo.setRoamingType(ServiceState.ROAMING_TYPE_DOMESTIC);
        doReturn(mockNeRegInfo).when(mockSS).getNetworkRegistrationInfo(anyInt(), anyInt());
        doReturn(new String[]{"0x1112:rat=gsm, emergency=true, scope=domestic"})
                .when(mResources).getStringArray(
                        eq(com.android.cellbroadcastreceiver.R.array.etws_alerts_range_strings));

        enablePreference(CellBroadcastSettings.KEY_ENABLE_ALERTS_MASTER_TOGGLE);
        sendMessage(987654321);
        waitForServiceIntent();

        assertEquals(SHOW_NEW_ALERT_ACTION, mServiceIntentToVerify.getAction());

        SmsCbMessage cbmTest = (SmsCbMessage) mServiceIntentToVerify.getExtras().get("message");
        SmsCbMessage cbm = createMessage(987654321);

        compareCellBroadCastMessage(cbm, cbmTest);
    }

    // Test testHandleCellBroadcastIntentInternationalRoaming method
    @InstrumentationTest
    // This test has a module dependency, so it is disabled for OEM testing because it is not a true
    // unit test
    public void testHandleCellBroadcastIntentInternationalRoaming() throws Exception {
        doReturn(mockSS).when(mMockedTelephonyManager).getServiceState();
        NetworkRegistrationInfo mockNeRegInfo = new NetworkRegistrationInfo(
                NetworkRegistrationInfo.DOMAIN_CS, AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                NetworkRegistrationInfo.REGISTRATION_STATE_ROAMING, 0, 0, false,
                null, null, "", true, 0, 0, 0);
        mockNeRegInfo.setRoamingType(ServiceState.ROAMING_TYPE_INTERNATIONAL);
        doReturn(mockNeRegInfo).when(mockSS).getNetworkRegistrationInfo(anyInt(), anyInt());
        doReturn(new String[]{"0x1112:rat=gsm, emergency=true, scope=international"})
                .when(mResources).getStringArray(
                        eq(com.android.cellbroadcastreceiver.R.array.etws_alerts_range_strings));

        enablePreference(CellBroadcastSettings.KEY_ENABLE_ALERTS_MASTER_TOGGLE);
        sendMessage(987654321);
        waitForServiceIntent();

        assertEquals(SHOW_NEW_ALERT_ACTION, mServiceIntentToVerify.getAction());

        SmsCbMessage cbmTest = (SmsCbMessage) mServiceIntentToVerify.getExtras().get("message");
        SmsCbMessage cbm = createMessage(987654321);

        compareCellBroadCastMessage(cbm, cbmTest);
    }

    // Test testHandleCellBroadcastIntentNonRoaming method
    @InstrumentationTest
    // This test has a module dependency, so it is disabled for OEM testing because it is not a true
    // unit test
    public void testHandleCellBroadcastIntentNonRoaming() throws Exception {
        doReturn(mockSS).when(mMockedTelephonyManager).getServiceState();
        NetworkRegistrationInfo mockNeRegInfo = new NetworkRegistrationInfo(
                NetworkRegistrationInfo.DOMAIN_CS, AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME, 0, 0, false,
                null, null, "", true, 0, 0, 0);
        mockNeRegInfo.setRoamingType(ServiceState.ROAMING_TYPE_NOT_ROAMING);
        doReturn(mockNeRegInfo).when(mockSS).getNetworkRegistrationInfo(anyInt(), anyInt());
        doReturn(new String[]{"0x1112:rat=gsm, emergency=true, scope=international"})
                .when(mResources).getStringArray(
                        eq(com.android.cellbroadcastreceiver.R.array.etws_alerts_range_strings));

        enablePreference(CellBroadcastSettings.KEY_ENABLE_ALERTS_MASTER_TOGGLE);
        sendMessage(987654321);
        waitForServiceIntent();
        assertEquals(SHOW_NEW_ALERT_ACTION, mServiceIntentToVerify.getAction());

        SmsCbMessage cbmTest = (SmsCbMessage) mServiceIntentToVerify.getExtras().get("message");
        SmsCbMessage cbm = createMessage(987654321);

        compareCellBroadCastMessage(cbm, cbmTest);
    }

    // Test testHandleCellBroadcastIntentNonMatchedScope method
    @InstrumentationTest
    // This test has a module dependency, so it is disabled for OEM testing because it is not a true
    // unit test
    public void testHandleCellBroadcastIntentNonMatchedScope() throws Exception {
        doReturn(mockSS).when(mMockedTelephonyManager).getServiceState();
        NetworkRegistrationInfo mockNeRegInfo = new NetworkRegistrationInfo(
                NetworkRegistrationInfo.DOMAIN_CS, AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME, 0, 0, false,
                null, null, "", true, 0, 0, 0);
        mockNeRegInfo.setRoamingType(ServiceState.ROAMING_TYPE_DOMESTIC);
        doReturn(mockNeRegInfo).when(mockSS).getNetworkRegistrationInfo(anyInt(), anyInt());
        doReturn(new String[]{"0x1112:rat=gsm, emergency=true, scope=international"})
                .when(mResources).getStringArray(
                        eq(com.android.cellbroadcastreceiver.R.array.etws_alerts_range_strings));

        enablePreference(CellBroadcastSettings.KEY_ENABLE_ALERTS_MASTER_TOGGLE);
        sendMessage(987654321);
        waitForServiceIntent();
        assertNull(mServiceIntentToVerify);
    }

    // Test showNewAlert method
    @InstrumentationTest
    // This test has a module dependency, so it is disabled for OEM testing because it is not a true
    // unit test
    public void testShowNewAlert() {
        Intent intent = new Intent(mContext, CellBroadcastAlertService.class);
        intent.setAction(SHOW_NEW_ALERT_ACTION);
        SmsCbMessage message = createMessage(34788612);
        intent.putExtra("message", message);
        startService(intent);
        waitForServiceIntent();

        // verify audio service intent
        assertEquals(CellBroadcastAlertAudio.ACTION_START_ALERT_AUDIO,
                mServiceIntentToVerify.getAction());
        assertEquals(CellBroadcastAlertService.AlertType.DEFAULT,
                mServiceIntentToVerify.getSerializableExtra(ALERT_AUDIO_TONE_TYPE));
        assertEquals(message.getMessageBody(),
                mServiceIntentToVerify.getStringExtra(
                        CellBroadcastAlertAudio.ALERT_AUDIO_MESSAGE_BODY));

        // verify alert dialog activity intent
        ArrayList<SmsCbMessage> newMessageList = mActivityIntentToVerify
                .getParcelableArrayListExtra(CellBroadcastAlertService.SMS_CB_MESSAGE_EXTRA);
        assertEquals(1, newMessageList.size());
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK,
                (mActivityIntentToVerify.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK));
        compareCellBroadCastMessage(message, newMessageList.get(0));
    }

    // Test showNewAlert method with a CMAS child abduction alert, using the default language code
    @InstrumentationTest
    // This test has a module dependency, so it is disabled for OEM testing because it is not a true
    // unit test
    public void testShowNewAlertChildAbductionWithDefaultLanguage() {
        enablePreference(CellBroadcastSettings.KEY_ENABLE_ALERTS_MASTER_TOGGLE);
        doReturn(new String[]{"0x111B:rat=gsm, emergency=true"})
                .when(mResources).getStringArray(
                        eq(com.android.cellbroadcastreceiver.R.array.etws_alerts_range_strings));
        doReturn("").when(mResources).getString(anyInt());

        sendMessageForCmasMessageClass(34788613,
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY);
        waitForServiceIntent();

        assertEquals(SHOW_NEW_ALERT_ACTION, mServiceIntentToVerify.getAction());

        SmsCbMessage cbmTest = (SmsCbMessage) mServiceIntentToVerify.getExtras().get("message");
        SmsCbMessage cbm = createMessageForCmasMessageClass(34788613,
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY,
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY);

        compareCellBroadCastMessage(cbm, cbmTest);
    }

    // Test showNewAlert method with a CMAS child abduction alert
    @InstrumentationTest
    // This test has a module dependency, so it is disabled for OEM testing because it is not a true
    // unit test
    public void testShowNewAlertChildAbduction() {
        enablePreference(CellBroadcastSettings.KEY_ENABLE_ALERTS_MASTER_TOGGLE);
        enablePreference(CellBroadcastSettings.KEY_RECEIVE_CMAS_IN_SECOND_LANGUAGE);

        final String language = "es";
        doReturn(new String[]{"0x111B:rat=gsm, emergency=true, filter_language=true"})
                .when(mResources).getStringArray(
                        eq(com.android.cellbroadcastreceiver.R.array.etws_alerts_range_strings));
        doReturn(language).when(mResources).getString(anyInt());

        sendMessageForCmasMessageClassAndLanguage(34788614,
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY, language);
        waitForServiceIntent();

        assertEquals(SHOW_NEW_ALERT_ACTION, mServiceIntentToVerify.getAction());

        SmsCbMessage cbmTest = (SmsCbMessage) mServiceIntentToVerify.getExtras().get("message");
        SmsCbMessage cbm = createCmasMessageWithLanguage(34788614,
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY,
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY, language);

        compareCellBroadCastMessage(cbm, cbmTest);
    }

    /*
     * Regarding the sub-user, it's channel config is following the owner's one.
     * But the sub-user doesn't have SharedPreferences, so it will refer the default value.
     * The default value should be from resources per each mcc-mnc
     */
    public void testShouldDisplayMessageForSubUsers() {
        Context mockContext = mock(Context.class);
        doReturn(mResources).when(mockContext).getResources();
        ((TestContextWrapper) mContext).injectCreateConfigurationContext(mockContext);

        putResources(com.android.cellbroadcastreceiver.R.bool.show_separate_exercise_settings,
                true);
        putResources(com.android.cellbroadcastreceiver.R.array.exercise_alert_range_strings,
                new String[]{
                    "0x111D:rat=gsm, emergency=true",
                    "0x112A:rat=gsm, emergency=true"});

        SmsCbMessage message = new SmsCbMessage(1, 2, 3, new SmsCbLocation(),
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXERCISE,
                "language", "body",
                SmsCbMessage.MESSAGE_PRIORITY_NORMAL, null,
                null, 0, 1);

        FakeSharedPreferences fakeSharedPreferences = new FakeSharedPreferences();
        fakeSharedPreferences.putBoolean(
                CellBroadcastSettings.KEY_ENABLE_ALERTS_MASTER_TOGGLE, true);

        mMockedSharedPreferences = fakeSharedPreferences;
        doReturn(mMockedSharedPreferences).when(mockContext)
                .getSharedPreferences(anyString(), anyInt());

        sendMessage(1);
        waitForServiceIntent();
        CellBroadcastAlertService cellBroadcastAlertService =
                (CellBroadcastAlertService) getService();

        // subuser doesn't have SharedPreferences so it will work according to resources
        putResources(com.android.cellbroadcastreceiver
                .R.bool.test_exercise_alerts_enabled_default, true);
        assertTrue(cellBroadcastAlertService.shouldDisplayMessage(message));

        putResources(com.android.cellbroadcastreceiver
                .R.bool.test_exercise_alerts_enabled_default, false);
        assertFalse(cellBroadcastAlertService.shouldDisplayMessage(message));

        // Owner has SharedPreferences so it will work according to sharedpreferences
        fakeSharedPreferences.putBoolean(CellBroadcastSettings.KEY_ENABLE_EXERCISE_ALERTS, false);
        mMockedSharedPreferences = fakeSharedPreferences;

        putResources(com.android.cellbroadcastreceiver
                .R.bool.test_exercise_alerts_enabled_default, true);
        assertFalse(cellBroadcastAlertService.shouldDisplayMessage(message));

        putResources(com.android.cellbroadcastreceiver
                .R.bool.test_exercise_alerts_enabled_default, false);
        assertFalse(cellBroadcastAlertService.shouldDisplayMessage(message));

        fakeSharedPreferences.putBoolean(CellBroadcastSettings.KEY_ENABLE_EXERCISE_ALERTS, true);
        mMockedSharedPreferences = fakeSharedPreferences;

        putResources(com.android.cellbroadcastreceiver
                .R.bool.test_exercise_alerts_enabled_default, true);
        assertTrue(cellBroadcastAlertService.shouldDisplayMessage(message));

        putResources(com.android.cellbroadcastreceiver
                .R.bool.test_exercise_alerts_enabled_default, false);
        assertTrue(cellBroadcastAlertService.shouldDisplayMessage(message));
    }

    public void testShouldDisplayMessage() {
        putResources(com.android.cellbroadcastreceiver.R.array
                .state_local_test_alert_range_strings, new String[]{
                    "0x112E:rat=gsm, emergency=true",
                    "0x112F:rat=gsm, emergency=true",
                });
        sendMessage(1);
        waitForServiceIntent();

        CellBroadcastAlertService cellBroadcastAlertService =
                (CellBroadcastAlertService) getService();

        // shouldDisplayMessage should return true for ETWS message
        SmsCbEtwsInfo etwsInfo = new SmsCbEtwsInfo(SmsCbEtwsInfo.ETWS_WARNING_TYPE_EARTHQUAKE, true,
                true, true, null);
        SmsCbMessage message = new SmsCbMessage(1, 2, 3, new SmsCbLocation(),
                SmsCbEtwsInfo.ETWS_WARNING_TYPE_EARTHQUAKE,
                "language", "body",
                SmsCbMessage.MESSAGE_PRIORITY_EMERGENCY, etwsInfo,
                null, 0, 1);

        enablePreference(CellBroadcastSettings.KEY_ENABLE_ALERTS_MASTER_TOGGLE);

        assertNotNull("Message should have non-null ETWS info", message.getEtwsWarningInfo());
        assertTrue("Message should be ETWS message", message.isEtwsMessage());
        assertTrue("Should display ETWS message",
                cellBroadcastAlertService.shouldDisplayMessage(message));

        SmsCbMessage message2 = new SmsCbMessage(1, 2, 3, new SmsCbLocation(),
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_STATE_LOCAL_TEST,
                "language", "body",
                SmsCbMessage.MESSAGE_PRIORITY_NORMAL, null,
                null, 0, 1);

        // check disable when setting is shown and preference is false
        disablePreference(CellBroadcastSettings.KEY_ENABLE_STATE_LOCAL_TEST_ALERTS);
        putResources(com.android.cellbroadcastreceiver.R.bool
                .show_state_local_test_settings, true);
        putResources(com.android.cellbroadcastreceiver.R.bool
                .state_local_test_alerts_enabled_default, true);
        assertFalse("Should disable local test channel",
                cellBroadcastAlertService.shouldDisplayMessage(message2));

        // check disable when setting is not shown and default preference is false
        putResources(com.android.cellbroadcastreceiver.R.bool
                .show_state_local_test_settings, false);
        putResources(com.android.cellbroadcastreceiver.R.bool
                .state_local_test_alerts_enabled_default, false);
        assertFalse("Should disable local test channel",
                cellBroadcastAlertService.shouldDisplayMessage(message2));

        // check enable when setting is not shown and default preference is true
        putResources(com.android.cellbroadcastreceiver.R.bool
                .show_state_local_test_settings, false);
        putResources(com.android.cellbroadcastreceiver.R.bool
                .state_local_test_alerts_enabled_default, true);
        assertTrue("Should enable local test channel",
                cellBroadcastAlertService.shouldDisplayMessage(message2));

        // check enable when setting is shown and preference is true
        enablePreference(CellBroadcastSettings.KEY_ENABLE_STATE_LOCAL_TEST_ALERTS);
        putResources(com.android.cellbroadcastreceiver.R.bool
                .show_state_local_test_settings, true);
        putResources(com.android.cellbroadcastreceiver.R.bool
                .state_local_test_alerts_enabled_default, false);
        assertTrue("Should enable local test channel",
                cellBroadcastAlertService.shouldDisplayMessage(message2));

        // roaming case
        Context mockContext = mock(Context.class);
        Resources mockResources = mock(Resources.class);
        doReturn(mockResources).when(mockContext).getResources();
        ((TestContextWrapper) mContext).injectCreateConfigurationContext(mockContext);
        // inject roaming operator
        doReturn("123").when(mMockedSharedPreferences)
                .getString(anyString(), anyString());
        doReturn(true).when(mockResources).getBoolean(
                eq(com.android.cellbroadcastreceiver.R.bool
                        .state_local_test_alerts_enabled_default));

        disablePreference(CellBroadcastSettings.KEY_ENABLE_STATE_LOCAL_TEST_ALERTS);
        assertTrue("Should enable local test channel",
                cellBroadcastAlertService.shouldDisplayMessage(message2));
        enablePreference(CellBroadcastSettings.KEY_ENABLE_STATE_LOCAL_TEST_ALERTS);
        assertTrue("Should enable local test channel",
                cellBroadcastAlertService.shouldDisplayMessage(message2));

        doReturn(false).when(mockResources).getBoolean(
                eq(com.android.cellbroadcastreceiver.R.bool
                        .state_local_test_alerts_enabled_default));
        disablePreference(CellBroadcastSettings.KEY_ENABLE_STATE_LOCAL_TEST_ALERTS);
        assertFalse("Should disable local test channel",
                cellBroadcastAlertService.shouldDisplayMessage(message2));
        enablePreference(CellBroadcastSettings.KEY_ENABLE_STATE_LOCAL_TEST_ALERTS);
        assertTrue("Should enable local test channel",
                cellBroadcastAlertService.shouldDisplayMessage(message2));
        ((TestContextWrapper) mContext).injectCreateConfigurationContext(null);
    }

    public void testFilterLanguage() {
        final String language = "en";
        final String language2nd = "es";
        doReturn(new String[]{"0x112E:rat=gsm, emergency=true, filter_language=true",
                "0x112F:rat=gsm, emergency=true"}).when(mResources).getStringArray(
                        eq(com.android.cellbroadcastreceiver.R.array
                                .state_local_test_alert_range_strings));
        doReturn(language).when(mResources).getString(
                eq(com.android.cellbroadcastreceiver.R.string
                        .emergency_alert_second_language_code));
        enablePreference(CellBroadcastSettings.KEY_ENABLE_ALERTS_MASTER_TOGGLE);
        enablePreference(CellBroadcastSettings.KEY_ENABLE_STATE_LOCAL_TEST_ALERTS);
        enablePreference(CellBroadcastSettings.KEY_RECEIVE_CMAS_IN_SECOND_LANGUAGE);

        sendMessage(1);
        waitForServiceIntent();
        CellBroadcastAlertService cellBroadcastAlertService =
                (CellBroadcastAlertService) getService();

        // Verify the message with the same language to be displayed for the channel
        // with filter_language=true
        SmsCbMessage message = new SmsCbMessage(1, 2, 3, new SmsCbLocation(), 0x112E,
                language, "body", SmsCbMessage.MESSAGE_PRIORITY_NORMAL, null, null, 0, 1);

        assertTrue("Should display the message",
                cellBroadcastAlertService.shouldDisplayMessage(message));

        // Verify the message with the different language not to be displayed for the channel
        // with filter_language=true
        SmsCbMessage message2 = new SmsCbMessage(1, 2, 3, new SmsCbLocation(), 0x112E,
                language2nd, "body", SmsCbMessage.MESSAGE_PRIORITY_NORMAL, null, null, 0, 1);

        assertFalse("Should not display the message",
                cellBroadcastAlertService.shouldDisplayMessage(message2));

        // Verify the message with the different language to be displayed for the channel
        // without filter_language=true
        SmsCbMessage message3 = new SmsCbMessage(1, 2, 3, new SmsCbLocation(), 0x112F,
                language2nd, "body", SmsCbMessage.MESSAGE_PRIORITY_NORMAL, null, null, 0, 1);

        assertTrue("Should display the message",
                cellBroadcastAlertService.shouldDisplayMessage(message3));
    }

    public void testMuteAlert() {
        if (!SdkLevel.isAtLeastS()) {
            return;
        }

        doReturn(new String[]{
                "0x1113:rat=gsm, type=mute, emergency=true, always_on=true",
                "0x112F:rat=gsm, emergency=true"}).when(mResources).getStringArray(
                eq(com.android.cellbroadcastreceiver.R.array
                        .additional_cbs_channels_strings));

        Intent intent = new Intent(mContext, CellBroadcastAlertService.class);
        intent.setAction(SHOW_NEW_ALERT_ACTION);

        SmsCbMessage message = createMessageForCmasMessageClass(13788634, 0x1113, 0x1113);
        intent.putExtra("message", message);
        startService(intent);
        waitForServiceIntent();

        assertEquals(CellBroadcastAlertService.AlertType.MUTE,
                mServiceIntentToVerify.getSerializableExtra(ALERT_AUDIO_TONE_TYPE));

        message = createMessageForCmasMessageClass(14788634, 0x112F, 0x112F);
        intent.putExtra("message", message);
        startService(intent);
        waitForServiceIntent();

        assertNotEquals(CellBroadcastAlertService.AlertType.MUTE,
                mServiceIntentToVerify.getSerializableExtra(ALERT_AUDIO_TONE_TYPE));
    }

    private static Map<String, NotificationChannel> mapNotificationChannelCaptor(
            ArgumentCaptor<NotificationChannel> captor) {
        Map<String, NotificationChannel> m = new HashMap<>();
        for (NotificationChannel notificationChannel : captor.getAllValues()) {
            m.put(notificationChannel.getId(), notificationChannel);
        }
        return m;
    }


    public void testAddToNotificationBarForWatch() throws RemoteException {
        if (!SdkLevel.isAtLeastS()) {
            return;
        }

        setWatchFeatureEnabled(true);
        Handler handler = new Handler(Looper.getMainLooper());
        IPowerManager mockedPowerService = mock(IPowerManager.class);
        mMockedPowerManager = new PowerManager(mContext, mockedPowerService, null, handler);
        doReturn("alert dialog title").when(mResources).getText(anyInt());

        Intent intent = new Intent(mContext, CellBroadcastAlertService.class);
        intent.setAction(SHOW_NEW_ALERT_ACTION);
        SmsCbMessage message = createMessageForCmasMessageClass(0xbbaa, 0x1112, 0);
        intent.putExtra("message", message);
        startService(intent);

        waitForServiceIntent();
        assertEquals(CellBroadcastAlertAudio.ACTION_START_ALERT_AUDIO,
                mServiceIntentToVerify.getAction());
        assertEquals(CellBroadcastAlertService.AlertType.DEFAULT,
                mServiceIntentToVerify.getSerializableExtra(ALERT_AUDIO_TONE_TYPE));
        assertEquals(message.getMessageBody(),
                mServiceIntentToVerify.getStringExtra(
                        CellBroadcastAlertAudio.ALERT_AUDIO_MESSAGE_BODY));
        assertTrue(mServiceIntentToVerify.getBooleanExtra(
                CellBroadcastAlertAudio.ALERT_AUDIO_OVERRIDE_DND_EXTRA, false));
        ArgumentCaptor<NotificationChannel> notificationChannelCaptor =
                ArgumentCaptor.forClass(NotificationChannel.class);
        verify(mMockedNotificationManager, times(5))
                .createNotificationChannel(notificationChannelCaptor.capture());
        Map<String, NotificationChannel> notificationChannelCreated =
                mapNotificationChannelCaptor(notificationChannelCaptor);
        assertTrue(notificationChannelCreated.get("broadcastMessagesHighPriority")
                .shouldVibrate());
        assertTrue(notificationChannelCreated.get("broadcastMessages").shouldVibrate());
        assertTrue(notificationChannelCreated.get("broadcastMessagesNonEmergency").shouldVibrate());
        assertTrue(notificationChannelCreated.get("broadcastMessagesInVoiceCall").shouldVibrate());
        assertEquals(NotificationManager.IMPORTANCE_MAX,
                notificationChannelCreated.get("broadcastMessagesHighPriority")
                .getImportance());
        assertTrue(notificationChannelCreated.get("broadcastMessagesHighPriority")
                .canBypassDnd());
        assertEquals(NotificationManager.IMPORTANCE_HIGH,
                notificationChannelCreated.get("broadcastMessages")
                .getImportance());
        assertTrue(notificationChannelCreated.get("broadcastMessages")
                .canBypassDnd());
        assertEquals(NotificationManager.IMPORTANCE_HIGH,
                notificationChannelCreated.get("broadcastMessagesNonEmergency")
                .getImportance());
        assertEquals(NotificationManager.IMPORTANCE_HIGH,
                notificationChannelCreated.get("broadcastMessagesInVoiceCall")
                .getImportance());
        ArgumentCaptor<Notification> notificationCaptor =
                ArgumentCaptor.forClass(Notification.class);
        verify(mMockedNotificationManager, times(1))
            .notify(eq(0x1112bbaa), notificationCaptor.capture());
        Notification notificationPosted = notificationCaptor.getValue();
        assertTrue(notificationPosted.deleteIntent.isBroadcast());
        assertEquals(1, notificationPosted.actions.length);
        assertSame(notificationPosted.deleteIntent, notificationPosted.actions[0].actionIntent);
    }

    public void testClamshellCoverDisplayId() {
        if (!SdkLevel.isAtLeastS()) {
            return;
        }
        doReturn(new String[]{
                "0x1113:rat=gsm, type=mute, emergency=true, always_on=true",
                "0x112F:rat=gsm, emergency=true"}).when(mResources).getStringArray(
                eq(com.android.cellbroadcastreceiver.R.array
                        .additional_cbs_channels_strings));

        Intent intent = new Intent(mContext, CellBroadcastAlertService.class);
        intent.setAction(SHOW_NEW_ALERT_ACTION);
        SmsCbMessage message = createMessageForCmasMessageClass(13788634, 0x1113, 0x1113);
        intent.putExtra("message", message);

        startService(intent);
        waitForServiceIntent();

        int display_id = SystemProperties.getInt(PROP_DISPLAY, Display.DEFAULT_DISPLAY);
        if (display_id == Display.DEFAULT_DISPLAY) {
            assertEquals(mBundle, null);
        } else {
            assertEquals(mBundle.getInt("android.activity.launchDisplayId", 0), display_id);
        }
    }

    public void testPlayPendingAlert() throws Exception {
        if (!SdkLevel.isAtLeastS()) {
            return;
        }
        doReturn(true).when(mResources).getBoolean(
                eq(com.android.cellbroadcastreceiver.R.bool.enable_alert_handling_during_call));
        doReturn(TelephonyManager.CALL_STATE_RINGING).when(mMockedTelephonyManager).getCallState();
        doReturn("alert dialog title").when(mResources).getText(anyInt());

        Intent intent = new Intent(mContext, CellBroadcastAlertService.class);
        intent.setAction(SHOW_NEW_ALERT_ACTION);
        SmsCbMessage message = createMessage(12345);
        intent.putExtra("message", message);
        startService(intent);

        // Verify do-nothing, when onCallStateChanged is called after Call is Active
        ArgumentCaptor<PhoneStateListener> phoneStateListenerCaptor =
                ArgumentCaptor.forClass(PhoneStateListener.class);
        verify(mMockedTelephonyManager).listen(phoneStateListenerCaptor.capture(),
                eq(PhoneStateListener.LISTEN_CALL_STATE));
        PhoneStateListener mPhoneStateListener = phoneStateListenerCaptor.getValue();
        mPhoneStateListener.onCallStateChanged(TelephonyManager.CALL_STATE_OFFHOOK, "");
        waitForServiceIntent();

        verify(mMockedNotificationManager, never()).getActiveNotifications();

        // Verify to trigger playPendingAlert, when onCallStateChanged is called and PendingAlert
        // exist after Call is IDLE
        StatusBarNotification mStatusBarNotification = mock(StatusBarNotification.class);
        doReturn(new StatusBarNotification[]{mStatusBarNotification}).when(
                mMockedNotificationManager).getActiveNotifications();

        mPhoneStateListener.onCallStateChanged(TelephonyManager.CALL_STATE_IDLE, "");
        waitForServiceIntent();

        // Verify alert dialog activity intent
        ArrayList<SmsCbMessage> newMessageList = mActivityIntentToVerify
                .getParcelableArrayListExtra(CellBroadcastAlertService.SMS_CB_MESSAGE_EXTRA);
        assertEquals(1, newMessageList.size());
        assertEquals(Intent.FLAG_ACTIVITY_NO_USER_ACTION,
                (mActivityIntentToVerify.getFlags() & Intent.FLAG_ACTIVITY_NO_USER_ACTION));
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK,
                (mActivityIntentToVerify.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK));
    }
}
