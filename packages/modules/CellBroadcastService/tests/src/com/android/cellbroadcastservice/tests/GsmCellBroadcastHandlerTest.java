/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.cellbroadcastservice.tests;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationRequest;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.provider.Telephony;
import android.telephony.AccessNetworkConstants;
import android.telephony.CbGeoUtils;
import android.telephony.CellIdentityLte;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SmsCbCmasInfo;
import android.telephony.SmsCbLocation;
import android.telephony.SmsCbMessage;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.test.mock.MockContentProvider;
import android.test.mock.MockContentResolver;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.text.format.DateUtils;

import androidx.test.filters.SmallTest;

import com.android.cellbroadcastservice.CbSendMessageCalculator;
import com.android.cellbroadcastservice.CellBroadcastHandler;
import com.android.cellbroadcastservice.CellBroadcastProvider;
import com.android.cellbroadcastservice.GsmCellBroadcastHandler;
import com.android.cellbroadcastservice.SmsCbConstants;
import com.android.modules.utils.build.SdkLevel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.List;
import java.util.Map;
import java.util.Random;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class GsmCellBroadcastHandlerTest extends CellBroadcastServiceTestBase {

    private static final String TAG = GsmCellBroadcastHandlerTest.class.getSimpleName();
    private GsmCellBroadcastHandler mGsmCellBroadcastHandler;

    private TestableLooper mTestableLooper;

    @Mock
    private Map<Integer, Resources> mMockedResourcesCache;

    @Mock
    private SubscriptionInfo mSubInfo;

    private CellBroadcastHandlerTest.CbSendMessageCalculatorFactoryFacade mSendMessageFactory;

    private Configuration mConfiguration;

    private class CellBroadcastContentProvider extends MockContentProvider {
        @Override
        public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                            String sortOrder) {

            // Assume the message was received 2 hours ago.
            long receivedTime = System.currentTimeMillis() - DateUtils.HOUR_IN_MILLIS * 2;
            long locationCheckTime = receivedTime;

            if (uri.compareTo(Telephony.CellBroadcasts.CONTENT_URI) == 0
                    && Long.parseLong(selectionArgs[selectionArgs.length - 1]) <= receivedTime) {
                MatrixCursor mc = new MatrixCursor(CellBroadcastProvider.QUERY_COLUMNS);

                mc.addRow(new Object[]{
                        1,              // _ID
                        0,              // SLOT_INDEX
                        1,              // SUBSCRIPTION_ID
                        0,              // GEOGRAPHICAL_SCOPE
                        "311480",       // PLMN
                        0,              // LAC
                        0,              // CID
                        1234,           // SERIAL_NUMBER
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL,
                        "en",           // LANGUAGE_CODE
                        1,              // DATA_CODING_SCHEME
                        "Test Message", // MESSAGE_BODY
                        1,              // MESSAGE_FORMAT
                        3,              // MESSAGE_PRIORITY
                        0,              // ETWS_WARNING_TYPE
                        0,              // ETWS_IS_PRIMARY
                        SmsCbCmasInfo.CMAS_CLASS_PRESIDENTIAL_LEVEL_ALERT, // CMAS_MESSAGE_CLASS
                        0,              // CMAS_CATEGORY
                        0,              // CMAS_RESPONSE_TYPE
                        0,              // CMAS_SEVERITY
                        0,              // CMAS_URGENCY
                        0,              // CMAS_CERTAINTY
                        receivedTime,   // RECEIVED_TIME
                        locationCheckTime, // LOCATION_CHECK_TIME
                        false,          // MESSAGE_BROADCASTED
                        true,           // MESSAGE_DISPLAYED
                        "",             // GEOMETRIES
                        5,              // MAXIMUM_WAIT_TIME
                });

                return mc;
            }

            return null;
        }

        @Override
        public int update(Uri url, ContentValues values, String where, String[] whereArgs) {
            return 1;
        }

        @Override
        public Uri insert(Uri uri, ContentValues values) {
            return null;
        }

    }

    private class SettingsProvider extends MockContentProvider {
        @Override
        public Bundle call(String method, String arg, Bundle extras) {
            return null;
        }

        @Override
        public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                            String sortOrder) {
            return null;
        }
    }

    private CellBroadcastHandler.HandlerHelper mHandlerHelper;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        mTestableLooper = TestableLooper.get(GsmCellBroadcastHandlerTest.this);

        mSendMessageFactory = new CellBroadcastHandlerTest.CbSendMessageCalculatorFactoryFacade();

        mHandlerHelper = mock(CellBroadcastHandler.HandlerHelper.class);

        // need to init mocked resources before creating GsmCellBroadcastHandler
        ((MockContentResolver) mMockedContext.getContentResolver()).addProvider(
                Telephony.CellBroadcasts.CONTENT_URI.getAuthority(),
                new CellBroadcastContentProvider());
        ((MockContentResolver) mMockedContext.getContentResolver()).addProvider(
                Settings.AUTHORITY, new SettingsProvider());
        doReturn(mMockedContext).when(mMockedContext).createConfigurationContext(any());
        doReturn("com.android.cellbroadcastservice").when(mMockedContext).getPackageName();
        doReturn(mMockedResources).when(mMockedContext).getResources();
        mConfiguration = new Configuration();
        doReturn(mConfiguration).when(mMockedResources).getConfiguration();
        doReturn(true).when(mMockedResourcesCache).containsKey(anyInt());
        doReturn(mMockedResources).when(mMockedResourcesCache).get(anyInt());
        putResources(com.android.cellbroadcastservice.R.integer.message_expiration_time, 86400000);
        putResources(com.android.cellbroadcastservice.R.array
                .additional_cell_broadcast_receiver_packages, new String[]{});
        putResources(com.android.cellbroadcastservice.R.array.area_info_channels, new int[]{});
        putResources(
                com.android.cellbroadcastservice.R.array.config_area_info_receiver_packages,
                new String[]{"fake.inforeceiver.pkg"});
        putResources(com.android.cellbroadcastservice.R.bool.reset_area_info_on_oos, true);
        doReturn(1).when(mMockedTelephonyManager).getActiveModemCount();
        doReturn(mSubInfo).when(mMockedSubscriptionManager)
                .getActiveSubscriptionInfoForSimSlotIndex(anyInt());
        doReturn(FAKE_SUBID).when(mSubInfo).getSubscriptionId();
        mGsmCellBroadcastHandler = new GsmCellBroadcastHandler(mMockedContext,
                mTestableLooper.getLooper(), mSendMessageFactory, mHandlerHelper, mMockedResources,
                FAKE_SUBID);

        // after mGsmCellBroadcastHandler's constructor has run, now we can replace the
        // mResourcesCache instance
        replaceInstance(CellBroadcastHandler.class, "mResourcesCache",
                mGsmCellBroadcastHandler, mMockedResourcesCache);

        doAnswer(invocation -> {
            Runnable r = invocation.getArgument(0);
            mGsmCellBroadcastHandler.getHandler().post(r);
            return null;
        }).when(mHandlerHelper).post(any());
        doReturn(mGsmCellBroadcastHandler.getHandler()).when(mHandlerHelper).getHandler();
        mGsmCellBroadcastHandler.start();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testTriggerMessage() throws Exception {
        final byte[] pdu = hexStringToBytes("0001113001010010C0111204D2");
        mGsmCellBroadcastHandler.onGsmCellBroadcastSms(0, pdu);
        mTestableLooper.processAllMessages();

        LocationListener listener = getLocationCallback();
        listener.onLocationChanged(mock(Location.class));

        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mMockedContext).sendOrderedBroadcast(intentCaptor.capture(), any(),
                (Bundle) any(), any(), any(), anyInt(), any(), any());
        Intent intent = intentCaptor.getValue();
        assertEquals(Telephony.Sms.Intents.ACTION_SMS_EMERGENCY_CB_RECEIVED, intent.getAction());
        SmsCbMessage msg = intent.getParcelableExtra("message");

        assertEquals(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL,
                msg.getServiceCategory());
        assertEquals(1234, msg.getSerialNumber());
        assertEquals("Test Message", msg.getMessageBody());
    }

    @Test
    public void testCleanup() throws Exception {
        mGsmCellBroadcastHandler.cleanup();
        // mGsmReceiver and mReceiver should be unregistered
        verify(mMockedContext, times(2)).unregisterReceiver(any());
    }

    @Test
    @SmallTest
    public void testAirplaneModeReset() {
        putResources(com.android.cellbroadcastservice.R.bool.reset_on_power_cycle_or_airplane_mode,
                true);
        Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        intent.putExtra("state", true);
        // Send fake airplane mode on event.
        sendBroadcast(intent);

        final byte[] pdu = hexStringToBytes("0001113001010010C0111204D2");
        mGsmCellBroadcastHandler.onGsmCellBroadcastSms(0, pdu);
        mTestableLooper.processAllMessages();

        verify(mMockedContext, never()).sendOrderedBroadcast(any(), anyString(), anyString(),
                any(), any(), anyInt(), any(), any());
    }

    @Test
    @SmallTest
    public void testGeofencingAlertOutOfPolygon() {
        final byte[] pdu = hexStringToBytes("01111D7090010254747A0E4ACF416110B538A582DE6650906AA28"
                + "2AE6979995D9ECF41C576597E2EBBC77950905D96D3D3EE33689A9FD3CB6D1708CA2E87E76550FAE"
                + "C7ECBCB203ABA0C6A97E7F3F0B9EC02C15CB5769A5D0652A030FB1ECECF5D5076393C2F83C8E9B9B"
                + "C7C0ECBC9203A3A3D07B5CBF379F85C06E16030580D660BB662B51A0D57CC3500000000000000000"
                + "0000000000000000000000000000000000000000000000000003021002078B53B6CA4B84B53988A4"
                + "B86B53958A4C2DB53B54A4C28B53B6CA4B840100CFF");
        mGsmCellBroadcastHandler.onGsmCellBroadcastSms(0, pdu);
        mTestableLooper.processAllMessages();

        LocationListener listener = getLocationCallback();
        listener.onLocationChanged(mock(Location.class));

        verify(mMockedContext, never()).sendOrderedBroadcast(any(), anyString(), anyString(),
                any(), any(), anyInt(), any(), any());
    }

    @Test
    @SmallTest
    public void testSmsCbLocation() {
        final byte[] pdu = hexStringToBytes("01111B40110101C366701A09368545692408000000000000000"
                + "00000000000000000000000000000000000000000000000000000000000000000000000000000000"
                + "000000000000000000000000000000000000000000000000B");

        final String fakePlmn = "310999";
        final int fakeTac = 1234;
        final int fakeCid = 5678;

        doReturn(fakePlmn).when(mMockedTelephonyManager).getNetworkOperator();
        ServiceState ss = mock(ServiceState.class);
        doReturn(ss).when(mMockedTelephonyManager).getServiceState();
        NetworkRegistrationInfo nri = new NetworkRegistrationInfo.Builder()
                .setDomain(NetworkRegistrationInfo.DOMAIN_CS)
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_LTE)
                .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_HOME)
                .setCellIdentity(new CellIdentityLte(0, 0, fakeCid, 0, fakeTac))
                .build();
        doReturn(nri).when(ss).getNetworkRegistrationInfo(anyInt(), anyInt());

        mGsmCellBroadcastHandler.onGsmCellBroadcastSms(0, pdu);
        mTestableLooper.processAllMessages();

        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mMockedContext).sendOrderedBroadcast(intentCaptor.capture(), any(),
                (Bundle) any(), any(), any(), anyInt(), any(), any());
        Intent intent = intentCaptor.getValue();
        assertEquals(Telephony.Sms.Intents.ACTION_SMS_EMERGENCY_CB_RECEIVED, intent.getAction());
        SmsCbMessage msg = intent.getParcelableExtra("message");

        SmsCbLocation location = msg.getLocation();
        assertEquals(fakePlmn, location.getPlmn());
        assertEquals(fakeTac, location.getLac());
        assertEquals(fakeCid, location.getCid());
    }

    @Test
    @SmallTest
    public void testGeofencingAmbiguousWithMockCalculator() {

        // Create Mock calculator
        CbSendMessageCalculator mockCalculator = createMockCalculatorAndSendCellBroadcast();


        ArgumentCaptor<List<CbGeoUtils.Geometry>> geosCaptor =
                ArgumentCaptor.forClass((Class) List.class);
        verify(mSendMessageFactory.getUnderlyingFactory()).createNew(any(), geosCaptor.capture());
        List<CbGeoUtils.Geometry> geos = geosCaptor.getValue();
        assertEquals(1, geos.size());
        doReturn(geos).when(mockCalculator).getFences();

        // Set location to be ambiguous
        setMockCalculation(mockCalculator, CbSendMessageCalculator.SEND_MESSAGE_ACTION_AMBIGUOUS,
                false, true);

        // Set location again to be ambiguous
        setMockCalculation(mockCalculator, CbSendMessageCalculator.SEND_MESSAGE_ACTION_AMBIGUOUS,
                false, true);

        // Run location unavailable
        runLocationUnavailableWhenMaxTimeReached();


        // Verify mark as sent and the right kind of broadcast has been sent
        verify(mockCalculator, times(1)).markAsSent();

        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mMockedContext).sendOrderedBroadcast(intentCaptor.capture(), any(),
                (Bundle) any(), any(), any(), anyInt(), any(), any());
        Intent intent = intentCaptor.getValue();
        assertEquals(Telephony.Sms.Intents.ACTION_SMS_EMERGENCY_CB_RECEIVED, intent.getAction());
    }

    @Test
    @SmallTest
    public void testGeofencingNoCoordinatesWithMockCalculator() {

        CbSendMessageCalculator mockCalculator = createMockCalculatorAndSendCellBroadcast();

        ArgumentCaptor<List<CbGeoUtils.Geometry>> geosCaptor =
                ArgumentCaptor.forClass((Class) List.class);
        verify(mSendMessageFactory.getUnderlyingFactory()).createNew(any(), geosCaptor.capture());
        List<CbGeoUtils.Geometry> geos = geosCaptor.getValue();
        assertEquals(1, geos.size());
        doReturn(geos).when(mockCalculator).getFences();

        runLocationUnavailableWhenMaxTimeReached();
        verifyBroadcastSent(mockCalculator);
    }

    @Test
    @SmallTest
    public void testGeofencingSendImmediatelyWithMockCalculator() {

        CbSendMessageCalculator mockCalculator = createMockCalculatorAndSendCellBroadcast();

        ArgumentCaptor<List<CbGeoUtils.Geometry>> geosCaptor =
                ArgumentCaptor.forClass((Class) List.class);
        verify(mSendMessageFactory.getUnderlyingFactory()).createNew(any(), geosCaptor.capture());
        List<CbGeoUtils.Geometry> geos = geosCaptor.getValue();
        assertEquals(1, geos.size());
        doReturn(geos).when(mockCalculator).getFences();


        // Set location to AMBIGUOUS
        setMockCalculation(mockCalculator, CbSendMessageCalculator.SEND_MESSAGE_ACTION_AMBIGUOUS,
                false, true);


        // Set location to SEND
        setMockCalculation(mockCalculator, CbSendMessageCalculator.SEND_MESSAGE_ACTION_SEND,
                true, true);

        // Make sure we don't send again
        doReturn(CbSendMessageCalculator.SEND_MESSAGE_ACTION_SENT).when(mockCalculator).getAction();

        // Check location request was cancelled
        assertLocationRequestCancelled();

        // This should never hit since the was cancelled
        setMockCalculation(mockCalculator, CbSendMessageCalculator.SEND_MESSAGE_ACTION_SENT,
                false, false);

        // Not sent
        verifyBroadcastNotSent(mockCalculator);
    }

    @Test
    @SmallTest
    public void testGeofencingDontSendWithMockCalculator() {

        // Create Mock calculator
        CbSendMessageCalculator mockCalculator = createMockCalculatorAndSendCellBroadcast();

        setMockCalculation(mockCalculator, CbSendMessageCalculator.SEND_MESSAGE_ACTION_DONT_SEND,
                false, true);

        // This method is copied form #testSmsCbLocation that sends out a message.  Except, in
        // this case, we are overriding the calculator with DONT_SEND and so our verification is
        // is that no broadcast was sent.
        final byte[] pdu = hexStringToBytes("01111B40110101C366701A09368545692408000000000000000"
                + "00000000000000000000000000000000000000000000000000000000000000000000000000000000"
                + "000000000000000000000000000000000000000000000000B");

        final String fakePlmn = "310999";
        final int fakeTac = 1234;
        final int fakeCid = 5678;

        doReturn(fakePlmn).when(mMockedTelephonyManager).getNetworkOperator();
        ServiceState ss = mock(ServiceState.class);
        doReturn(ss).when(mMockedTelephonyManager).getServiceState();
        NetworkRegistrationInfo nri = new NetworkRegistrationInfo.Builder()
                .setDomain(NetworkRegistrationInfo.DOMAIN_CS)
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_LTE)
                .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_HOME)
                .setCellIdentity(new CellIdentityLte(0, 0, fakeCid, 0, fakeTac))
                .build();
        doReturn(nri).when(ss).getNetworkRegistrationInfo(anyInt(), anyInt());

        mGsmCellBroadcastHandler.onGsmCellBroadcastSms(0, pdu);
        mTestableLooper.processAllMessages();
        verifyBroadcastNotSent(mockCalculator);

        assertLocationRequestCancelled();
        verifyBroadcastNotSent(mockCalculator);
    }

    @Test
    @SmallTest
    public void testConsecutiveGeofencingTriggerMessages() throws Exception {
        final byte[] pdu = hexStringToBytes("0001113001010010C0111204D2");
        mGsmCellBroadcastHandler.onGsmCellBroadcastSms(0, pdu);
        mTestableLooper.processAllMessages();
        LocationListener listener = getLocationCallback();
        mGsmCellBroadcastHandler.sendMessageBroadcastNotRequired();

        mGsmCellBroadcastHandler.onGsmCellBroadcastSms(0, pdu);
        mTestableLooper.processAllMessages();

        listener.onLocationChanged(mock(Location.class));

        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mMockedContext, times(2)).sendOrderedBroadcast(intentCaptor.capture(), any(),
                (Bundle) any(), any(), any(), anyInt(), any(), any());
        Intent intent = intentCaptor.getValue();
        assertEquals(Telephony.Sms.Intents.ACTION_SMS_EMERGENCY_CB_RECEIVED, intent.getAction());
        SmsCbMessage msg = intent.getParcelableExtra("message");

        assertEquals(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL,
                msg.getServiceCategory());
        assertEquals(1234, msg.getSerialNumber());
        assertEquals("Test Message", msg.getMessageBody());
    }

    @Test
    @SmallTest
    public void testConsecutiveGeoFencingMessages() throws Exception {
        CbSendMessageCalculator mockCalculator = createMockCalculatorAndSendCellBroadcast();
        mGsmCellBroadcastHandler.sendMessageBroadcastNotRequired();
        CbSendMessageCalculator mockCalculator2 = createMockCalculatorAndSendCellBroadcast();

        ArgumentCaptor<List<CbGeoUtils.Geometry>> geosCaptor =
                ArgumentCaptor.forClass((Class) List.class);
        verify(mSendMessageFactory.getUnderlyingFactory()).createNew(any(), geosCaptor.capture());
        List<CbGeoUtils.Geometry> geos = geosCaptor.getValue();
        assertEquals(1, geos.size());
        doReturn(geos).when(mockCalculator).getFences();

        // Set the new action to return
        doReturn(CbSendMessageCalculator.SEND_MESSAGE_ACTION_DONT_SEND).when(mockCalculator2)
                .getAction();

        // Set location to SEND
        setMockCalculation(mockCalculator, CbSendMessageCalculator.SEND_MESSAGE_ACTION_SEND,
                true, true);
    }

    @Test
    @SmallTest
    public void testResetAreaInfoOnOutOfService() {
        String areaInfo = "0000000000000000";
        mGsmCellBroadcastHandler.setCellBroadcastAreaInfo(0, areaInfo);
        assertEquals(areaInfo, mGsmCellBroadcastHandler.getCellBroadcastAreaInfo(0));

        ArgumentCaptor<PhoneStateListener> listenerCaptor =
                ArgumentCaptor.forClass(PhoneStateListener.class);
        verify(mMockedTelephonyManager).listen(listenerCaptor.capture(), anyInt());

        PhoneStateListener listener = listenerCaptor.getValue();
        ServiceState ss = mock(ServiceState.class);
        doReturn(ServiceState.STATE_OUT_OF_SERVICE).when(ss).getState();
        listener.onServiceStateChanged(ss);

        assertEquals("", mGsmCellBroadcastHandler.getCellBroadcastAreaInfo(0));
    }

    @Test
    @SmallTest
    public void testResetAreaInfoWithDefaultSubChanged() {
        String areaInfo = "0000000000000000";
        mGsmCellBroadcastHandler.setCellBroadcastAreaInfo(0, areaInfo);
        assertEquals(areaInfo, mGsmCellBroadcastHandler.getCellBroadcastAreaInfo(0));

        TelephonyManager tm2 = mock(TelephonyManager.class);
        doReturn(tm2).when(mMockedTelephonyManager).createForSubscriptionId(FAKE_SUBID + 1);
        SubscriptionInfo subInfo = mock(SubscriptionInfo.class);
        doReturn(subInfo).when(mMockedSubscriptionManager)
                .getActiveSubscriptionInfoForSimSlotIndex(anyInt());
        doReturn(FAKE_SUBID + 1).when(subInfo).getSubscriptionId();

        Intent intent = new Intent(SubscriptionManager.ACTION_DEFAULT_SUBSCRIPTION_CHANGED);
        intent.putExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, FAKE_SUBID + 1);
        sendBroadcast(intent);

        ArgumentCaptor<PhoneStateListener> listenerCaptor =
                ArgumentCaptor.forClass(PhoneStateListener.class);
        mTestableLooper.processAllMessages();

        verify(mMockedTelephonyManager).listen(any(), eq(PhoneStateListener.LISTEN_NONE));
        verify(tm2).listen(listenerCaptor.capture(), anyInt());

        PhoneStateListener listener = listenerCaptor.getValue();
        ServiceState ss = mock(ServiceState.class);
        doReturn(ServiceState.STATE_OUT_OF_SERVICE).when(ss).getState();
        listener.onServiceStateChanged(ss);

        assertEquals("", mGsmCellBroadcastHandler.getCellBroadcastAreaInfo(0));
    }

    @Test
    @SmallTest
    public void testDoNotResetAreaInfoWithInvalidSubId() {
        new GsmCellBroadcastHandler(mMockedContext,
                mTestableLooper.getLooper(), mSendMessageFactory, mHandlerHelper, mMockedResources,
                SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        verify(mMockedContext, never()).getResources();
    }

    @Test
    @SmallTest
    public void testConstructorRegistersReceiverWithExpectedFlag() {
        int expectedFlag = SdkLevel.isAtLeastT() ? Context.RECEIVER_EXPORTED : 0;
        clearInvocations(mMockedContext);

        GsmCellBroadcastHandler gsmCellBroadcastHandler = new GsmCellBroadcastHandler(
                mMockedContext, mTestableLooper.getLooper(), mSendMessageFactory, mHandlerHelper);

        verify(mMockedContext, times(1)).registerReceiver(any(), any(), eq(expectedFlag));
        gsmCellBroadcastHandler.cleanup();
    }

    @Test
    @SmallTest
    public void testConstructorWithResourcesRegistersReceiverWithExpectedFlag() {
        int expectedFlag = SdkLevel.isAtLeastT() ? Context.RECEIVER_EXPORTED : 0;
        clearInvocations(mMockedContext);

        GsmCellBroadcastHandler gsmCellBroadcastHandler = new GsmCellBroadcastHandler(
                mMockedContext, mTestableLooper.getLooper(), mSendMessageFactory, mHandlerHelper,
                mMockedResources, SubscriptionManager.INVALID_SUBSCRIPTION_ID);

        verify(mMockedContext, times(1)).registerReceiver(any(), any(), eq(expectedFlag));
        gsmCellBroadcastHandler.cleanup();
    }

    /* Below are helper methods for setting up mocks, verifying actions, etc. */

    private void verifyBroadcastSent(CbSendMessageCalculator mockCalculator) {
        verify(mMockedContext).sendOrderedBroadcast(any(), any(),
                (Bundle) any(), any(), any(), anyInt(), any(), any());
        clearInvocations(mMockedContext);
        verify(mockCalculator, times(1)).markAsSent();
        clearInvocations(mockCalculator);
    }

    private void verifyBroadcastNotSent(CbSendMessageCalculator mockCalculator) {
        verify(mockCalculator, times(0)).markAsSent();
        verify(mMockedContext, never()).sendOrderedBroadcast(any(), anyString(), anyString(),
                any(), any(), anyInt(), any(), any());
    }

    private CbSendMessageCalculator createMockCalculatorAndSendCellBroadcast() {
        CbSendMessageCalculator calculator = createMockCalculator();

        final byte[] pdu = hexStringToBytes("01111D7090010254747A0E4ACF416110B538A582DE6650906AA28"
                + "2AE6979995D9ECF41C576597E2EBBC77950905D96D3D3EE33689A9FD3CB6D1708CA2E87E76550FAE"
                + "C7ECBCB203ABA0C6A97E7F3F0B9EC02C15CB5769A5D0652A030FB1ECECF5D5076393C2F83C8E9B9B"
                + "C7C0ECBC9203A3A3D07B5CBF379F85C06E16030580D660BB662B51A0D57CC3500000000000000000"
                + "0000000000000000000000000000000000000000000000000003021002078B53B6CA4B84B53988A4"
                + "B86B53958A4C2DB53B54A4C28B53B6CA4B840100CFF");
        mGsmCellBroadcastHandler.onGsmCellBroadcastSms(0, pdu);
        mTestableLooper.processAllMessages();
        return calculator;
    }

    private CbSendMessageCalculator createMockCalculator() {
        CbSendMessageCalculator mockCalculator = mock(CbSendMessageCalculator.class);
        CellBroadcastHandler.CbSendMessageCalculatorFactory factory = mock(
                CellBroadcastHandler.CbSendMessageCalculatorFactory.class);
        mSendMessageFactory.setUnderlyingFactory(factory);

        doReturn(mockCalculator).when(factory).createNew(any(), any());
        doReturn(CbSendMessageCalculator.SEND_MESSAGE_ACTION_NO_COORDINATES)
                .when(mockCalculator).getAction();
        return mockCalculator;
    }

    Random mRandom = new Random(10);
    void setMockCalculation(CbSendMessageCalculator mockCalculator,
            @CbSendMessageCalculator.SendMessageAction int newAction,
            boolean broadcastShouldBeSent, boolean addCoordinateShouldBeCalled) {
        // Create a random location and accuracy.  The values are ignored since the calculator
        // is a mock.
        CbGeoUtils.LatLng latLng = new CbGeoUtils.LatLng(mRandom.nextFloat() % 150 + 1,
                mRandom.nextFloat() % 150 + 1);
        float accuracy = mRandom.nextFloat() % 3000 + 1;

        // Create location based off of specified coordinates and accuracy
        Location location = createMockLocation(latLng, accuracy);

        // Set the new action to return
        doReturn(newAction).when(mockCalculator).getAction();

        // Send the new location through location manager
        LocationListener locationListener = getLocationCallback();
        locationListener.onLocationChanged(location);

        // Verify that the correct coordinate was sent to calculator
        ArgumentCaptor<CbGeoUtils.LatLng> acLatLng =
                ArgumentCaptor.forClass(CbGeoUtils.LatLng.class);
        verify(mockCalculator, times(addCoordinateShouldBeCalled ? 1 : 0))
                .addCoordinate(acLatLng.capture(), eq(accuracy));

        if (addCoordinateShouldBeCalled) {
            assertEquals(acLatLng.getValue().lat, latLng.lat);
            assertEquals(acLatLng.getValue().lng, latLng.lng);
        }

        if (broadcastShouldBeSent) {
            verifyBroadcastSent(mockCalculator);
        } else {
            verifyBroadcastNotSent(mockCalculator);
        }
    }

    Location createMockLocation(CbGeoUtils.LatLng latLng, float accuracy) {
        Location location = mock(Location.class);
        doReturn(latLng.lat).when(location).getLatitude();
        doReturn(latLng.lng).when(location).getLongitude();
        doReturn(accuracy).when(location).getAccuracy();
        return location;
    }

    private void runLocationUnavailableWhenMaxTimeReached() {
        ArgumentCaptor<Runnable> onLocationUnavailableCaptor =
                ArgumentCaptor.forClass(Runnable.class);
        verify(mHandlerHelper).postDelayed(onLocationUnavailableCaptor.capture(), anyLong());

        // Before running location unavailable, we check that the callback wasn't removed.
        // Similar to assertLocationRequestCancelled
        verify(mHandlerHelper, times(0))
                .removeCallbacks(onLocationUnavailableCaptor.getValue());

        onLocationUnavailableCaptor.getValue().run();
    }

    private void assertLocationRequestCancelled() {
        ArgumentCaptor<Runnable> onLocationUnavailableCaptor =
                ArgumentCaptor.forClass(Runnable.class);
        verify(mHandlerHelper).postDelayed(onLocationUnavailableCaptor.capture(), anyLong());
        verify(mHandlerHelper, times(1))
                .removeCallbacks(onLocationUnavailableCaptor.getValue());
    }

    private LocationListener getLocationCallback() {
        ArgumentCaptor<LocationListener> locationListenerCaptor =
                ArgumentCaptor.forClass(LocationListener.class);
        verify(mMockedLocationManager).requestLocationUpdates(
                any(LocationRequest.class), any(), locationListenerCaptor.capture());
        return locationListenerCaptor.getValue();
    }
}
