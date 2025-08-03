/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.bluetooth.mapclient;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothMapClient;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Telephony.Mms;
import android.provider.Telephony.Sms;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.test.mock.MockContentProvider;
import android.test.mock.MockContentResolver;
import android.util.Log;

import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.btservice.AdapterService;
import com.android.bluetooth.btservice.storage.DatabaseManager;
import com.android.vcard.VCardConstants;
import com.android.vcard.VCardEntry;
import com.android.vcard.VCardProperty;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class MapClientContentTest {

    private static final String TAG = "MapClientContentTest";
    private static final int READ = 1;

    private BluetoothAdapter mAdapter;
    private BluetoothDevice mTestDevice;

    private Handler mHandler;
    private Bmessage mTestMessage1;
    private Bmessage mTestMessage2;
    private Long mTestMessage1Timestamp = 1234L;
    private String mTestMessage1Handle = "0001";
    private String mTestMessage2Handle = "0002";
    private static final boolean MESSAGE_SEEN = true;
    private static final boolean MESSAGE_NOT_SEEN = false;


    private VCardEntry mOriginator;

    private ArgumentCaptor<Uri> mUriArgument = ArgumentCaptor.forClass(Uri.class);

    private MapClientContent mMapClientContent;

    @Mock
    private AdapterService mAdapterService;
    @Mock
    private DatabaseManager mDatabaseManager;
    @Mock
    private MapClientService mMockMapClientService;
    @Mock
    private Context mMockContext;
    @Mock
    private MapClientContent.Callbacks mCallbacks;

    private MockContentResolver mMockContentResolver;
    private FakeContentProvider mMockSmsContentProvider;
    private FakeContentProvider mMockMmsContentProvider;
    private FakeContentProvider mMockThreadContentProvider;

    @Mock
    private SubscriptionManager mMockSubscriptionManager;
    @Mock
    private SubscriptionInfo mMockSubscription;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mMockSmsContentProvider = new FakeContentProvider(mMockContext);
        mMockMmsContentProvider = new FakeContentProvider(mMockContext);
        mMockThreadContentProvider = new FakeContentProvider(mMockContext);

        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mTestDevice = mAdapter.getRemoteDevice("00:01:02:03:04:05");
        mMockContentResolver = Mockito.spy(new MockContentResolver());
        mMockContentResolver.addProvider("sms", mMockSmsContentProvider);
        mMockContentResolver.addProvider("mms", mMockMmsContentProvider);
        mMockContentResolver.addProvider("mms-sms", mMockThreadContentProvider);

        when(mMockContext.getContentResolver()).thenReturn(mMockContentResolver);
        when(mMockContext.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE))
                .thenReturn(mMockSubscriptionManager);
        when(mMockContext.getSystemServiceName(SubscriptionManager.class))
                .thenReturn(Context.TELEPHONY_SUBSCRIPTION_SERVICE);

        when(mMockSubscriptionManager.getActiveSubscriptionInfoList())
                .thenReturn(Arrays.asList(mMockSubscription));
        createTestMessages();
    }

    @After
    public void tearDown() throws Exception {
    }

    /**
     * Test that everything initializes correctly with an empty content provider
     */
    @Test
    public void testCreateMapClientContent() {
        mMapClientContent = new MapClientContent(mMockContext, mCallbacks, mTestDevice);
        verify(mMockSubscriptionManager).addSubscriptionInfoRecord(any(), any(), anyInt(),
                eq(SubscriptionManager.SUBSCRIPTION_TYPE_REMOTE_SIM));
        Assert.assertEquals(0, mMockSmsContentProvider.mContentValues.size());
    }

    /**
     * Test that a dirty database gets cleaned at startup.
     */
    @Test
    public void testCleanDirtyDatabase() {
        mMapClientContent = new MapClientContent(mMockContext, mCallbacks, mTestDevice);
        mMapClientContent.storeMessage(mTestMessage1, mTestMessage1Handle, mTestMessage1Timestamp,
                MESSAGE_SEEN);
        verify(mMockSubscriptionManager).addSubscriptionInfoRecord(any(), any(), anyInt(),
                eq(SubscriptionManager.SUBSCRIPTION_TYPE_REMOTE_SIM));
        Assert.assertEquals(1, mMockSmsContentProvider.mContentValues.size());
        mMapClientContent = new MapClientContent(mMockContext, mCallbacks, mTestDevice);
        Assert.assertEquals(0, mMockSmsContentProvider.mContentValues.size());
    }

    /**
     * Test inserting 2 SMS messages and then clearing out the database.
     */
    @Test
    public void testStoreTwoSMS() {
        mMapClientContent = new MapClientContent(mMockContext, mCallbacks, mTestDevice);
        mMapClientContent.storeMessage(mTestMessage1, mTestMessage1Handle, mTestMessage1Timestamp,
                MESSAGE_SEEN);
        verify(mMockSubscriptionManager).addSubscriptionInfoRecord(any(), any(), anyInt(),
                eq(SubscriptionManager.SUBSCRIPTION_TYPE_REMOTE_SIM));
        Assert.assertEquals(1, mMockSmsContentProvider.mContentValues.size());

        mMapClientContent.storeMessage(mTestMessage1, mTestMessage1Handle, mTestMessage1Timestamp,
                MESSAGE_SEEN);
        Assert.assertEquals(2, mMockSmsContentProvider.mContentValues.size());
        Assert.assertEquals(0, mMockMmsContentProvider.mContentValues.size());

        mMapClientContent.cleanUp();
        Assert.assertEquals(0, mMockSmsContentProvider.mContentValues.size());
        Assert.assertEquals(0, mMockThreadContentProvider.mContentValues.size());
    }

    /**
     * Test inserting 2 MMS messages and then clearing out the database.
     */
    @Test
    public void testStoreTwoMMS() {
        mMapClientContent = new MapClientContent(mMockContext, mCallbacks, mTestDevice);
        mMapClientContent.storeMessage(mTestMessage2, mTestMessage1Handle, mTestMessage1Timestamp,
        MESSAGE_SEEN);
        verify(mMockSubscriptionManager).addSubscriptionInfoRecord(any(), any(), anyInt(),
                eq(SubscriptionManager.SUBSCRIPTION_TYPE_REMOTE_SIM));
        Assert.assertEquals(1, mMockMmsContentProvider.mContentValues.size());

        mMapClientContent.storeMessage(mTestMessage2, mTestMessage1Handle, mTestMessage1Timestamp,
                MESSAGE_SEEN);
        Assert.assertEquals(2, mMockMmsContentProvider.mContentValues.size());

        mMapClientContent.cleanUp();
        Assert.assertEquals(0, mMockMmsContentProvider.mContentValues.size());
    }

    /**
     * Test that SMS and MMS messages end up in their respective databases.
     */
    @Test
    public void testStoreOneSMSOneMMS() {
        mMapClientContent = new MapClientContent(mMockContext, mCallbacks, mTestDevice);
        mMapClientContent.storeMessage(mTestMessage2, mTestMessage1Handle, mTestMessage1Timestamp,
                MESSAGE_SEEN);
        verify(mMockSubscriptionManager).addSubscriptionInfoRecord(any(), any(), anyInt(),
                eq(SubscriptionManager.SUBSCRIPTION_TYPE_REMOTE_SIM));
        Assert.assertEquals(1, mMockMmsContentProvider.mContentValues.size());

        mMapClientContent.storeMessage(mTestMessage2, mTestMessage2Handle, mTestMessage1Timestamp,
                MESSAGE_SEEN);
        Assert.assertEquals(2, mMockMmsContentProvider.mContentValues.size());

        mMapClientContent.cleanUp();
        Assert.assertEquals(0, mMockMmsContentProvider.mContentValues.size());
    }

    /**
     * Test read status changed
     */
    @Test
    public void testReadStatusChanged() {
        mMapClientContent = new MapClientContent(mMockContext, mCallbacks, mTestDevice);
        mMapClientContent.storeMessage(mTestMessage2, mTestMessage1Handle, mTestMessage1Timestamp,
                MESSAGE_SEEN);
        verify(mMockSubscriptionManager).addSubscriptionInfoRecord(any(), any(), anyInt(),
                eq(SubscriptionManager.SUBSCRIPTION_TYPE_REMOTE_SIM));
        Assert.assertEquals(1, mMockMmsContentProvider.mContentValues.size());

        mMapClientContent.storeMessage(mTestMessage2, mTestMessage1Handle, mTestMessage1Timestamp,
                MESSAGE_SEEN);
        Assert.assertEquals(2, mMockMmsContentProvider.mContentValues.size());

        mMapClientContent.markRead(mTestMessage1Handle);

        mMapClientContent.cleanUp();
        Assert.assertEquals(0, mMockMmsContentProvider.mContentValues.size());
    }

    /**
     * Test read status changed in local provider
     *
     * Insert a message, and notify the observer about a change
     * The cursor is configured to return messages marked as read
     * Verify that the local change is observed and propagated to the remote
     */
    @Test
    public void testLocalReadStatusChanged() {
        mMapClientContent = new MapClientContent(mMockContext, mCallbacks, mTestDevice);
        mMapClientContent.storeMessage(mTestMessage2, mTestMessage1Handle, mTestMessage1Timestamp,
            MESSAGE_SEEN);
        Assert.assertEquals(1, mMockMmsContentProvider.mContentValues.size());
        mMapClientContent.mContentObserver.onChange(false);
        verify(mCallbacks).onMessageStatusChanged(eq(mTestMessage1Handle),
                eq(BluetoothMapClient.READ));
    }

    /**
     * Test if seen status is set to true in database for SMS
     */
     @Test
     public void testStoreSmsMessageWithSeenTrue_smsWrittenWithSeenTrue() {
        mMapClientContent = new MapClientContent(mMockContext, mCallbacks, mTestDevice);
        mMapClientContent.storeMessage(mTestMessage1, mTestMessage1Handle, mTestMessage1Timestamp,
                MESSAGE_SEEN);
        assertThat(mMockSmsContentProvider.mContentValues.size()).isEqualTo(1);

        ContentValues storedSMS =
                (ContentValues) mMockSmsContentProvider.mContentValues.values().toArray()[0];

        assertThat(storedSMS.get(Sms.SEEN)).isEqualTo(MESSAGE_SEEN);

     }

      /**
     * Test if seen status is set to false in database for SMS
     */
     @Test
     public void testStoreSmsMessageWithSeenFalse_smsWrittenWithSeenFalse() {
        mMapClientContent = new MapClientContent(mMockContext, mCallbacks, mTestDevice);
        mMapClientContent.storeMessage(mTestMessage1, mTestMessage1Handle, mTestMessage1Timestamp,
                MESSAGE_NOT_SEEN);
        assertThat(mMockSmsContentProvider.mContentValues.size()).isEqualTo(1);

        ContentValues storedSMS =
                (ContentValues) mMockSmsContentProvider.mContentValues.values().toArray()[0];

        assertThat(storedSMS.get(Sms.SEEN)).isEqualTo(MESSAGE_NOT_SEEN);

     }

     /**
     * Test if seen status is set to true in database for MMS
     */
     @Test
     public void testStoreMmsMessageWithSeenTrue_mmsWrittenWithSeenTrue() {
        mMapClientContent = new MapClientContent(mMockContext, mCallbacks, mTestDevice);
        mMapClientContent.storeMessage(mTestMessage2, mTestMessage1Handle, mTestMessage1Timestamp,
                MESSAGE_SEEN);
        assertThat(mMockMmsContentProvider.mContentValues.size()).isEqualTo(1);

        ContentValues storedMMS =
                (ContentValues) mMockMmsContentProvider.mContentValues.values().toArray()[0];

        assertThat(storedMMS.get(Mms.SEEN)).isEqualTo(MESSAGE_SEEN);

     }

     /**
     * Test if seen status is set to false in database for MMS
     */
     @Test
     public void testStoreMmsMessageWithSeenFalse_mmsWrittenWithSeenFalse() {
        mMapClientContent = new MapClientContent(mMockContext, mCallbacks, mTestDevice);
        mMapClientContent.storeMessage(mTestMessage2, mTestMessage1Handle, mTestMessage1Timestamp,
                MESSAGE_NOT_SEEN);
        assertThat(mMockMmsContentProvider.mContentValues.size()).isEqualTo(1);

        ContentValues storedMMS =
                (ContentValues) mMockMmsContentProvider.mContentValues.values().toArray()[0];

        assertThat(storedMMS.get(Mms.SEEN)).isEqualTo(MESSAGE_NOT_SEEN);

     }

    /**
     * Test remote message deleted
     *
     * Add a message to the database Simulate the message getting
     * deleted on the phone Verify that the message is deleted locally
     */
    @Test
    public void testMessageDeleted() {
        mMapClientContent = new MapClientContent(mMockContext, mCallbacks, mTestDevice);
        mMapClientContent.storeMessage(mTestMessage1, mTestMessage1Handle, mTestMessage1Timestamp,
                MESSAGE_SEEN);
        verify(mMockSubscriptionManager).addSubscriptionInfoRecord(any(), any(), anyInt(),
                eq(SubscriptionManager.SUBSCRIPTION_TYPE_REMOTE_SIM));
        Assert.assertEquals(1, mMockSmsContentProvider.mContentValues.size());
        // attempt to delete an invalid handle, nothing should be removed.
        mMapClientContent.deleteMessage(mTestMessage2Handle);
        Assert.assertEquals(1, mMockSmsContentProvider.mContentValues.size());

        // delete a valid handle
        mMapClientContent.deleteMessage(mTestMessage1Handle);
        Assert.assertEquals(0, mMockSmsContentProvider.mContentValues.size());
    }

    /**
     * Test read status changed in local provider
     *
     * Insert a message, manually remove it and notify the observer about a change
     * Verify that the local change is observed and propagated to the remote
     */
    @Test
    public void testLocalMessageDeleted() {
        mMapClientContent = new MapClientContent(mMockContext, mCallbacks, mTestDevice);
        mMapClientContent.storeMessage(mTestMessage1, mTestMessage1Handle, mTestMessage1Timestamp,
                MESSAGE_SEEN);
        verify(mMockSubscriptionManager).addSubscriptionInfoRecord(any(), any(), anyInt(),
                eq(SubscriptionManager.SUBSCRIPTION_TYPE_REMOTE_SIM));
        Assert.assertEquals(1, mMockSmsContentProvider.mContentValues.size());
        mMockSmsContentProvider.mContentValues.clear();
        mMapClientContent.mContentObserver.onChange(false);
        verify(mCallbacks).onMessageStatusChanged(eq(mTestMessage1Handle),
                eq(BluetoothMapClient.DELETED));
    }

    /**
     * Preconditions:
     * - Create new {@link MapClientContent}, own phone number not initialized yet.
     *
     * Actions:
     * - Invoke {@link MapClientContent#setRemoteDeviceOwnNumber} with a non-null number.
     *
     * Outcome:
     * - {@link MapClientContent#mPhoneNumber} should now store the number.
     */
    @Test
    public void testSetRemoteDeviceOwnNumber() {
        String testNumber = "5551212";

        mMapClientContent = new MapClientContent(mMockContext, mCallbacks, mTestDevice);
        assertThat(mMapClientContent.mPhoneNumber).isNull();

        mMapClientContent.setRemoteDeviceOwnNumber(testNumber);
        assertThat(mMapClientContent.mPhoneNumber).isEqualTo(testNumber);
    }

    /**
     * Test to validate that some poorly formatted messages don't crash.
     */
    @Test
    public void testStoreBadMessage() {
        mMapClientContent = new MapClientContent(mMockContext, mCallbacks, mTestDevice);
        mTestMessage1 = new Bmessage();
        mTestMessage1.setBodyContent("HelloWorld");
        mTestMessage1.setType(Bmessage.Type.SMS_GSM);
        mTestMessage1.setFolder("telecom/msg/sent");
        mMapClientContent.storeMessage(mTestMessage1, mTestMessage1Handle, mTestMessage1Timestamp,
                MESSAGE_SEEN);

        mTestMessage2 = new Bmessage();
        mTestMessage2.setBodyContent("HelloWorld");
        mTestMessage2.setType(Bmessage.Type.MMS);
        mTestMessage2.setFolder("telecom/msg/inbox");
        mMapClientContent.storeMessage(mTestMessage2, mTestMessage2Handle, mTestMessage1Timestamp,
                MESSAGE_SEEN);
    }

    /**
     * Test to validate that an exception in the Subscription manager won't crash Bluetooth during
     * disconnect.
     */
    @Test
    public void testCleanUpRemoteException() {
        mMapClientContent = new MapClientContent(mMockContext, mCallbacks, mTestDevice);
        doThrow(java.lang.NullPointerException.class).when(mMockSubscriptionManager)
                .removeSubscriptionInfoRecord(any(), anyInt());
        mMapClientContent.cleanUp();
    }

    /**
     * Test to validate old subscriptions are removed at startup.
     */
    @Test
    public void testCleanUpAtStartup() {
        MapClientContent.clearAllContent(mMockContext);
        verify(mMockSubscriptionManager, never()).removeSubscriptionInfoRecord(any(), anyInt());

        when(mMockSubscription.getSubscriptionType())
                .thenReturn(SubscriptionManager.SUBSCRIPTION_TYPE_REMOTE_SIM);
        MapClientContent.clearAllContent(mMockContext);
        verify(mMockSubscriptionManager).removeSubscriptionInfoRecord(any(),
                eq(SubscriptionManager.SUBSCRIPTION_TYPE_REMOTE_SIM));
    }

    /**
     * Test to validate that cleaning content does not crash when no subscription are available.
     */
    @Test
    public void testCleanUpWithNoSubscriptions() {
        when(mMockSubscriptionManager.getActiveSubscriptionInfoList())
                .thenReturn(null);

        MapClientContent.clearAllContent(mMockContext);
    }

    /**
     * Test verifying dumpsys does not cause Bluetooth to crash (esp since we're querying the
     * database to generate dump).
     */
    @Test
    public void testDumpsysDoesNotCauseCrash() {
        testStoreOneSMSOneMMS();
        // mMapClientContent is set in testStoreOneSMSOneMMS
        StringBuilder sb = new StringBuilder("Hello world!\n");
        mMapClientContent.dump(sb);

        assertThat(sb.toString()).isNotNull();
    }

    void createTestMessages() {
        mOriginator = new VCardEntry();
        VCardProperty property = new VCardProperty();
        property.setName(VCardConstants.PROPERTY_TEL);
        property.addValues("555-1212");
        mOriginator.addProperty(property);
        mTestMessage1 = new Bmessage();
        mTestMessage1.setBodyContent("HelloWorld");
        mTestMessage1.setType(Bmessage.Type.SMS_GSM);
        mTestMessage1.setFolder("telecom/msg/inbox");
        mTestMessage1.addOriginator(mOriginator);

        mTestMessage2 = new Bmessage();
        mTestMessage2.setBodyContent("HelloWorld");
        mTestMessage2.setType(Bmessage.Type.MMS);
        mTestMessage2.setFolder("telecom/msg/inbox");
        mTestMessage2.addOriginator(mOriginator);
        mTestMessage2.addRecipient(mOriginator);
    }

    public class FakeContentProvider extends MockContentProvider {

        Map<Uri, ContentValues> mContentValues = new HashMap<>();
        FakeContentProvider(Context context) {
            super(context);
        }

        @Override
        public int delete(Uri uri, String selection, String[] selectionArgs) {
            Log.i(TAG, "Delete " + uri);
            Log.i(TAG, "Contents" + mContentValues.toString());
            mContentValues.remove(uri);
            if (uri.equals(Sms.CONTENT_URI) || uri.equals(Mms.CONTENT_URI)) {
                mContentValues.clear();
            }
            return 1;
        }

        @Override
        public Uri insert(Uri uri, ContentValues values) {
            Log.i(TAG, "URI = " + uri);
            if (uri.equals(Mms.Inbox.CONTENT_URI)) uri = Mms.CONTENT_URI;
            Uri returnUri = Uri.withAppendedPath(uri, String.valueOf(mContentValues.size() + 1));
            //only store top level message parts
            if (uri.equals(Sms.Inbox.CONTENT_URI) || uri.equals(Mms.CONTENT_URI)) {
                Log.i(TAG, "adding content" + values);
                mContentValues.put(returnUri, values);
                Log.i(TAG, "ContentSize = " + mContentValues.size());
            }
            return returnUri;
        }

        @Override
        public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                String sortOrder) {
            Cursor cursor = Mockito.mock(Cursor.class);

            when(cursor.moveToFirst()).thenReturn(true);
            when(cursor.moveToNext()).thenReturn(true).thenReturn(false);

            when(cursor.getLong(anyInt())).thenReturn((long) mContentValues.size());
            when(cursor.getString(anyInt())).thenReturn(String.valueOf(mContentValues.size()));
            when(cursor.getInt(anyInt())).thenReturn(READ);
            return cursor;
        }

        @Override
        public int update(Uri uri, ContentValues values, Bundle extras) {
            return 0;
        }
    }
}
