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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.app.ActivityManager;
import android.app.IActivityManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.IIntentSender;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.provider.Telephony;
import android.telephony.CbGeoUtils;
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
import android.util.Singleton;

import androidx.annotation.NonNull;
import androidx.test.filters.SmallTest;

import com.android.cellbroadcastservice.CbSendMessageCalculator;
import com.android.cellbroadcastservice.CellBroadcastHandler;
import com.android.cellbroadcastservice.CellBroadcastProvider;
import com.android.cellbroadcastservice.SmsCbConstants;
import com.android.internal.telephony.ISub;
import com.android.modules.utils.build.SdkLevel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class CellBroadcastHandlerTest extends CellBroadcastServiceTestBase {

    private CellBroadcastHandler mCellBroadcastHandler;

    private TestableLooper mTestbleLooper;

    private CbSendMessageCalculatorFactoryFacade mSendMessageFactory;

    private CellBroadcastHandler.HandlerHelper mHandlerHelper;

    protected HashMap<String, IBinder> mServiceManagerMockedServices = new HashMap<>();

    @Mock
    private IBinder mIBinder;

    @Mock
    private IActivityManager mIActivityManager;

    @Mock
    private IIntentSender mIIntentSender;

    @Mock
    private Singleton<IActivityManager> mIActivityManagerSingleton;

    @Mock
    private ISub mISub;

    private Configuration mConfiguration;

    private class CellBroadcastContentProvider extends MockContentProvider {
        @Override
        public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                String sortOrder) {

            if (uri.compareTo(Telephony.CellBroadcasts.CONTENT_URI) == 0) {
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
                        System.currentTimeMillis() - DateUtils.HOUR_IN_MILLIS * 2,
                        System.currentTimeMillis() - DateUtils.HOUR_IN_MILLIS * 2,
                        true,           // MESSAGE_BROADCASTED
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
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();

        mTestbleLooper = TestableLooper.get(CellBroadcastHandlerTest.this);
        mSendMessageFactory = new CbSendMessageCalculatorFactoryFacade();
        mHandlerHelper = mock(CellBroadcastHandler.HandlerHelper.class);

        mCellBroadcastHandler = new CellBroadcastHandler("CellBroadcastHandlerUT",
                mMockedContext, mTestbleLooper.getLooper(), mSendMessageFactory, mHandlerHelper);

        doAnswer(invocation -> {
            Runnable r = invocation.getArgument(0);
            mCellBroadcastHandler.getHandler().post(r);
            return null;
        }).when(mHandlerHelper).post(any());
        doReturn(mCellBroadcastHandler.getHandler()).when(mHandlerHelper).getHandler();

        ((MockContentResolver) mMockedContext.getContentResolver()).addProvider(
                Telephony.CellBroadcasts.CONTENT_URI.getAuthority(),
                new CellBroadcastContentProvider());
        doReturn("com.android.cellbroadcastservice").when(mMockedContext).getPackageName();
        doReturn(mMockedContext).when(mMockedContext).createConfigurationContext(any());
        doReturn(mMockedResources).when(mMockedContext).getResources();
        mConfiguration = new Configuration();
        doReturn(mConfiguration).when(mMockedResources).getConfiguration();
        putResources(com.android.cellbroadcastservice.R.integer.message_expiration_time,
                (int) DateUtils.DAY_IN_MILLIS);
        putResources(com.android.cellbroadcastservice.R.bool.duplicate_compare_service_category,
                true);

        replaceInstance(ActivityManager.class, "IActivityManagerSingleton", null,
                mIActivityManagerSingleton);

        replaceInstance(Singleton.class, "mInstance", mIActivityManagerSingleton,
                mIActivityManager);
        replaceInstance(ServiceManager.class, "sCache", null, mServiceManagerMockedServices);

        doReturn(mIIntentSender).when(mIActivityManager).getIntentSenderWithFeature(anyInt(),
                nullable(String.class), nullable(String.class), nullable(IBinder.class),
                nullable(String.class), anyInt(), nullable(Intent[].class),
                nullable(String[].class), anyInt(), nullable(Bundle.class), anyInt());
        doReturn(mIBinder).when(mIIntentSender).asBinder();
        doReturn(mISub).when(mIBinder).queryLocalInterface(anyString());
        mServiceManagerMockedServices.put("isub", mIBinder);
        TelephonyManager.disableServiceHandleCaching();
        SubscriptionManager.disableCaching();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    private SmsCbMessage createSmsCbMessage(int serialNumber, int serviceCategory,
            String messageBody) {
        return new SmsCbMessage(SmsCbMessage.MESSAGE_FORMAT_3GPP,
                0, serialNumber, new SmsCbLocation("311480", 0, 0),
                serviceCategory, "en", messageBody, 3,
                null, null, 0, 1);
    }

    @Test
    @SmallTest
    public void testDuplicate() throws Exception {
        SmsCbMessage msg = createSmsCbMessage(1234, 4370, "msg");
        assertTrue(mCellBroadcastHandler.isDuplicate(msg));
    }

    @Test
    @SmallTest
    public void testNotDuplicateSerialDifferent() throws Exception {
        SmsCbMessage msg = createSmsCbMessage(1235, 4370, "msg");
        assertFalse(mCellBroadcastHandler.isDuplicate(msg));
    }

    @Test
    @SmallTest
    public void testNotDuplicateServiceCategoryDifferent() throws Exception {
        SmsCbMessage msg = createSmsCbMessage(1234, 4371, "msg");
        assertFalse(mCellBroadcastHandler.isDuplicate(msg));
    }

    @Test
    @SmallTest
    public void testNotDuplicateMessageBodyDifferent() throws Exception {
        putResources(com.android.cellbroadcastservice.R.bool.duplicate_compare_body, true);
        SmsCbMessage msg = createSmsCbMessage(1234, 4370, "msg");
        assertFalse(mCellBroadcastHandler.isDuplicate(msg));
    }

    @Test
    @SmallTest
    public void testNotDuplicateCellLocationDifferent() throws Exception {
        SmsCbMessage msg = new SmsCbMessage(SmsCbMessage.MESSAGE_FORMAT_3GPP,
                0, 1234, new SmsCbLocation("311480", 0, 1),
                4370, "en", "Test Message", 3,
                null, null, 0, 1);
        assertFalse(mCellBroadcastHandler.isDuplicate(msg));
    }

    @Test
    @SmallTest
    public void testMakeCellBroadcastHandler() throws Exception {
        CellBroadcastHandler cellBroadcastHandler =
                CellBroadcastHandler.makeCellBroadcastHandler(mMockedContext);
        // sanity test for make, just assert that returned object is not null
        assertTrue(cellBroadcastHandler != null);
        cellBroadcastHandler.cleanup();
    }

    @Test
    @SmallTest
    public void testDump() throws Exception {
            mCellBroadcastHandler.dump(null, new PrintWriter(new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                    // no implementation needed for sanity test
                }
            }), null);
    }

    @Test
    @SmallTest
    public void testPutPhoneIdAndSubIdExtra() throws Exception {
        Intent intent = new Intent();
        int phoneId = 0;
        if (SdkLevel.isAtLeastU()) {
            doReturn(FAKE_SUBID).when(mISub).getSubId(phoneId);
        }
        CellBroadcastHandler.putPhoneIdAndSubIdExtra(mMockedContext, intent, phoneId);
        assertEquals(FAKE_SUBID, intent.getIntExtra(
                SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, FAKE_SUBID + 1));
        assertEquals(FAKE_SUBID, intent.getIntExtra(
                "subscription", FAKE_SUBID + 1));
        assertEquals(phoneId, intent.getIntExtra(
                SubscriptionManager.EXTRA_SLOT_INDEX, phoneId + 1));
        assertEquals(phoneId, intent.getIntExtra("phone", phoneId + 1));

        // if subId is not available, subscription extras should not be added
        Intent intentNoSubId = new Intent();
        if (SdkLevel.isAtLeastU()) {
            doReturn(SubscriptionManager.INVALID_SUBSCRIPTION_ID).when(mISub).getSubId(phoneId);
        } else {
            doReturn(null).when(mMockedSubscriptionManager).getSubscriptionIds(anyInt());
        }
        CellBroadcastHandler.putPhoneIdAndSubIdExtra(mMockedContext, intentNoSubId, phoneId);
        assertEquals(FAKE_SUBID + 1, intentNoSubId.getIntExtra(
                SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, FAKE_SUBID + 1));
        assertEquals(FAKE_SUBID + 1, intentNoSubId.getIntExtra(
                "subscription", FAKE_SUBID + 1));
        assertEquals(phoneId, intentNoSubId.getIntExtra(
                SubscriptionManager.EXTRA_SLOT_INDEX, phoneId + 1));
        assertEquals(phoneId, intentNoSubId.getIntExtra(
                "phone", phoneId + 1));
    }

    @Test
    @SmallTest
    public void testDuplicateDetection() throws Exception {
        // if IS_DEBUGGABLE
        if (SystemProperties.getInt("ro.debuggable", 0) == 1) {
            SmsCbMessage msg = createSmsCbMessage(1234, 4370, "msg");
            // message should be detected as duplicate initially
            assertTrue(mCellBroadcastHandler.isDuplicate(msg));

            // disable duplicate detection
            Intent intent =
                    new Intent("com.android.cellbroadcastservice.action.DUPLICATE_DETECTION");
            intent.putExtra("enable", false);
            sendBroadcast(intent);

            // message should not be detected as duplicate
            assertFalse(mCellBroadcastHandler.isDuplicate(msg));

            // enable duplicate detection
            intent.putExtra("enable", true);
            sendBroadcast(intent);

            // message should be detected as duplicate again
            assertTrue(mCellBroadcastHandler.isDuplicate(msg));
        }
    }

    @Test
    @SmallTest
    public void testCrossSimDuplicateDetection() throws Exception {
        int differentSlotID = 1;
        int differentSubID = SubscriptionManager.DEFAULT_SUBSCRIPTION_ID;

        // enable cross_sim_duplicate_detection
        putResources(com.android.cellbroadcastservice.R.bool.cross_sim_duplicate_detection, true);

        // The message with different subId will be detected as duplication.
        SmsCbMessage msg1 = new SmsCbMessage(SmsCbMessage.MESSAGE_FORMAT_3GPP,
                0, 1234, new SmsCbLocation("311480", 0, 0),
                4370, "en", "Test Message", 3,
                null, null, 0, differentSubID);
        assertTrue(mCellBroadcastHandler.isDuplicate(msg1));

        // The message with different body won't be detected as a duplication.
        SmsCbMessage msg2 = new SmsCbMessage(SmsCbMessage.MESSAGE_FORMAT_3GPP,
                0, 1234, new SmsCbLocation("311480", 0, 0),
                4370, "en", "Different Message", 3,
                null, null, 0, differentSubID);
        assertFalse(mCellBroadcastHandler.isDuplicate(msg2));

        // The message with different slotId will be detected as a duplication.
        SmsCbMessage msg3 = new SmsCbMessage(SmsCbMessage.MESSAGE_FORMAT_3GPP,
                0, 1234, new SmsCbLocation("311480", 0, 0),
                4370, "en", "Test Message", 3,
                null, null, differentSlotID, 1);
        assertTrue(mCellBroadcastHandler.isDuplicate(msg3));

        // The message with different slotId and body will be detected as a duplication.
        SmsCbMessage msg4 = new SmsCbMessage(SmsCbMessage.MESSAGE_FORMAT_3GPP,
                0, 1234, new SmsCbLocation("311480", 0, 0),
                4370, "en", "Different Message", 3,
                null, null, differentSlotID, 1);
        assertTrue(mCellBroadcastHandler.isDuplicate(msg4));
    }

    @Test
    @SmallTest
    public void testGetResources() throws Exception {
        SubscriptionInfo mockSubInfo = mock(SubscriptionInfo.class);
        doReturn(mockSubInfo).when(mMockedSubscriptionManager).getActiveSubscriptionInfo(anyInt());
        doReturn(001).when(mockSubInfo).getMcc();
        doReturn(01).when(mockSubInfo).getMnc();

        // verify not to call SubscriptionManager#getResourcesForSubId for DEFAULT ID
        mCellBroadcastHandler.getResources(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);

        verify(mMockedContext, never()).createConfigurationContext(any());
        verify(mMockedContext, times(1)).getResources();

        // verify not to call SubscriptionManager#getResourcesForSubId for INVALID ID
        mCellBroadcastHandler.getResources(SubscriptionManager.INVALID_SUBSCRIPTION_ID);

        verify(mMockedContext, never()).createConfigurationContext(any());
        verify(mMockedContext, times(2)).getResources();

        // verify to call SubscriptionManager#getResourcesForSubId for normal sub id
        mCellBroadcastHandler.getResources(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID - 1);

        verify(mMockedContext, times(1)).createConfigurationContext(any());

        // verify to call SubscriptionManager#getResourcesForSubId again for the same sub
        mCellBroadcastHandler.getResources(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID - 1);

        verify(mMockedContext, times(1)).createConfigurationContext(any());
    }

    @Test
    @SmallTest
    public void testConstructorRegistersReceiverWithExpectedFlag() {
        int expectedFlag = SdkLevel.isAtLeastT() ? Context.RECEIVER_EXPORTED : 0;
        clearInvocations(mMockedContext);

        CellBroadcastHandler cellBroadcastHandler = new CellBroadcastHandler(
                "CellBroadcastHandlerUT", mMockedContext, mTestbleLooper.getLooper(),
                mSendMessageFactory, mHandlerHelper);

        verify(mMockedContext, times(1)).registerReceiver(any(), any(), eq(expectedFlag));
        cellBroadcastHandler.cleanup();
    }

    /**
     * Makes injecting a mock factory easy.
     */
    static class CbSendMessageCalculatorFactoryFacade extends
            CellBroadcastHandler.CbSendMessageCalculatorFactory {

        @NonNull
        private CellBroadcastHandler.CbSendMessageCalculatorFactory mUnderlyingFactory;

        @NonNull CellBroadcastHandler.CbSendMessageCalculatorFactory getUnderlyingFactory() {
            return mUnderlyingFactory;
        }

        void setUnderlyingFactory(
                @NonNull final CellBroadcastHandler.CbSendMessageCalculatorFactory factory) {
            mUnderlyingFactory = factory;
        }

        CbSendMessageCalculatorFactoryFacade() {
            mUnderlyingFactory = new CellBroadcastHandler.CbSendMessageCalculatorFactory();
        }

        @Override
        public CbSendMessageCalculator createNew(@NonNull Context context,
                @NonNull List<CbGeoUtils.Geometry> fences) {
            return mUnderlyingFactory.createNew(context, fences);
        }
    }
}
