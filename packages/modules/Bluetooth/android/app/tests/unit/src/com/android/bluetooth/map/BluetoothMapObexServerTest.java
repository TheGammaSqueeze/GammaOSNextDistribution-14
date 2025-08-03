/*
 * Copyright 2023 The Android Open Source Project
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

package com.android.bluetooth.map;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.ContentProviderClient;
import android.content.Context;
import android.database.MatrixCursor;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.RemoteException;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.BluetoothMethodProxy;
import com.android.bluetooth.mapapi.BluetoothMapContract;
import com.android.obex.ResponseCodes;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class BluetoothMapObexServerTest {
    private static final int TEST_MAS_ID = 1;
    private static final boolean TEST_ENABLE_SMS_MMS = true;
    private static final String TEST_NAME = "test_name";
    private static final String TEST_PACKAGE_NAME = "test.package.name";
    private static final String TEST_ID = "1111";
    private static final String TEST_PROVIDER_AUTHORITY = "test.project.provider";
    private static final Drawable TEST_DRAWABLE = new ColorDrawable();
    private static final BluetoothMapUtils.TYPE TEST_TYPE = BluetoothMapUtils.TYPE.IM;
    private static final String TEST_UCI = "uci";
    private static final String TEST_UCI_PREFIX = "uci_prefix";

    private BluetoothMapAccountItem mAccountItem;
    private BluetoothMapMasInstance mMasInstance;
    private BluetoothMapObexServer mObexServer;
    private BluetoothMapAppParams mParams;

    @Mock
    private Context mContext;
    @Mock
    private BluetoothMapService mMapService;
    @Mock
    private ContentProviderClient mProviderClient;
    @Mock
    private BluetoothMapContentObserver mObserver;
    @Spy
    private BluetoothMethodProxy mMapMethodProxy = BluetoothMethodProxy.getInstance();

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        BluetoothMethodProxy.setInstanceForTesting(mMapMethodProxy);
        doReturn(mProviderClient).when(
                mMapMethodProxy).contentResolverAcquireUnstableContentProviderClient(any(), any());
        mAccountItem = BluetoothMapAccountItem.create(TEST_ID, TEST_NAME, TEST_PACKAGE_NAME,
                TEST_PROVIDER_AUTHORITY, TEST_DRAWABLE, TEST_TYPE, TEST_UCI, TEST_UCI_PREFIX);
        mMasInstance = new BluetoothMapMasInstance(mMapService, mContext,
                mAccountItem, TEST_MAS_ID, TEST_ENABLE_SMS_MMS);
        mParams = new BluetoothMapAppParams();
        mObexServer = new BluetoothMapObexServer(null, mContext, mObserver, mMasInstance,
                mAccountItem, TEST_ENABLE_SMS_MMS);
    }

    @Test
    public void setOwnerStatus_withAccountTypeEmail() throws Exception {
        doReturn(null).when(mProviderClient).query(any(), any(), any(), any(), any());
        BluetoothMapAccountItem accountItemWithTypeEmail = BluetoothMapAccountItem.create(TEST_ID,
                TEST_NAME, TEST_PACKAGE_NAME, TEST_PROVIDER_AUTHORITY, TEST_DRAWABLE,
                BluetoothMapUtils.TYPE.EMAIL, TEST_UCI, TEST_UCI_PREFIX);
        BluetoothMapObexServer obexServer = new BluetoothMapObexServer(null, mContext, mObserver,
                mMasInstance, accountItemWithTypeEmail, TEST_ENABLE_SMS_MMS);

        assertThat(obexServer.setOwnerStatus(mParams)).isEqualTo(
                ResponseCodes.OBEX_HTTP_UNAVAILABLE);
    }

    @Test
    public void setOwnerStatus_withAppParamsInvalid() throws Exception {
        BluetoothMapAppParams params = mock(BluetoothMapAppParams.class);
        when(params.getPresenceAvailability()).thenReturn(
                BluetoothMapAppParams.INVALID_VALUE_PARAMETER);
        when(params.getPresenceStatus()).thenReturn(null);
        when(params.getLastActivity()).thenReturn(
                (long) BluetoothMapAppParams.INVALID_VALUE_PARAMETER);
        when(params.getChatState()).thenReturn(BluetoothMapAppParams.INVALID_VALUE_PARAMETER);
        when(params.getChatStateConvoIdString()).thenReturn(null);

        assertThat(mObexServer.setOwnerStatus(params)).isEqualTo(
                ResponseCodes.OBEX_HTTP_PRECON_FAILED);
    }

    @Test
    public void setOwnerStatus_withNonNullBundle() throws Exception {
        setUpBluetoothMapAppParams(mParams);
        Bundle bundle = new Bundle();
        when(mProviderClient.call(any(), any(), any())).thenReturn(bundle);

        assertThat(mObexServer.setOwnerStatus(mParams)).isEqualTo(
                ResponseCodes.OBEX_HTTP_OK);
    }

    @Test
    public void setOwnerStatus_withNullBundle() throws Exception {
        setUpBluetoothMapAppParams(mParams);
        when(mProviderClient.call(any(), any(), any())).thenReturn(null);

        assertThat(mObexServer.setOwnerStatus(mParams)).isEqualTo(
                ResponseCodes.OBEX_HTTP_NOT_IMPLEMENTED);
    }

    @Test
    public void setOwnerStatus_withRemoteExceptionThrown() throws Exception {
        setUpBluetoothMapAppParams(mParams);
        doThrow(RemoteException.class).when(mProviderClient).call(any(), any(), any());

        assertThat(mObexServer.setOwnerStatus(mParams)).isEqualTo(
                ResponseCodes.OBEX_HTTP_UNAVAILABLE);
    }

    @Test
    public void setOwnerStatus_withNullPointerExceptionThrown() throws Exception {
        setUpBluetoothMapAppParams(mParams);
        doThrow(NullPointerException.class).when(mProviderClient).call(any(), any(), any());

        assertThat(mObexServer.setOwnerStatus(mParams)).isEqualTo(
                ResponseCodes.OBEX_HTTP_UNAVAILABLE);
    }

    @Test
    public void setOwnerStatus_withIllegalArgumentExceptionThrown() throws Exception {
        setUpBluetoothMapAppParams(mParams);
        doThrow(IllegalArgumentException.class).when(mProviderClient).call(any(), any(), any());

        assertThat(mObexServer.setOwnerStatus(mParams)).isEqualTo(
                ResponseCodes.OBEX_HTTP_UNAVAILABLE);
    }

    @Test
    public void addEmailFolders() throws Exception {
        MatrixCursor cursor = new MatrixCursor(new String[]{BluetoothMapContract.FolderColumns.NAME,
                BluetoothMapContract.FolderColumns._ID});
        long parentId = 1;
        long childId = 2;
        cursor.addRow(new Object[]{"test_name", childId});
        cursor.moveToFirst();
        BluetoothMapFolderElement parentFolder = new BluetoothMapFolderElement("parent", null);
        parentFolder.setFolderId(parentId);
        doReturn(cursor).when(mProviderClient).query(any(), any(),
                eq(BluetoothMapContract.FolderColumns.PARENT_FOLDER_ID + " = " + parentId), any(),
                any());

        mObexServer.addEmailFolders(parentFolder);

        assertThat(parentFolder.getFolderById(childId)).isNotNull();
    }

    @Test
    public void setMsgTypeFilterParams_withAccountNull_andOverwriteTrue() throws Exception {
        BluetoothMapObexServer obexServer = new BluetoothMapObexServer(null, mContext, mObserver,
                mMasInstance, null, false);

        obexServer.setMsgTypeFilterParams(mParams, true);

        int expectedMask = 0;
        expectedMask |= BluetoothMapAppParams.FILTER_NO_SMS_CDMA;
        expectedMask |= BluetoothMapAppParams.FILTER_NO_SMS_GSM;
        expectedMask |= BluetoothMapAppParams.FILTER_NO_MMS;
        expectedMask |= BluetoothMapAppParams.FILTER_NO_EMAIL;
        expectedMask |= BluetoothMapAppParams.FILTER_NO_IM;
        assertThat(mParams.getFilterMessageType()).isEqualTo(expectedMask);
    }

    @Test
    public void setMsgTypeFilterParams_withInvalidFilterMessageType() throws Exception {
        BluetoothMapAccountItem accountItemWithTypeEmail = BluetoothMapAccountItem.create(TEST_ID,
                TEST_NAME, TEST_PACKAGE_NAME, TEST_PROVIDER_AUTHORITY, TEST_DRAWABLE,
                BluetoothMapUtils.TYPE.EMAIL, TEST_UCI, TEST_UCI_PREFIX);
        BluetoothMapObexServer obexServer = new BluetoothMapObexServer(null, mContext, mObserver,
                mMasInstance, accountItemWithTypeEmail, TEST_ENABLE_SMS_MMS);

        // Passing mParams without any previous settings pass invalid filter message type
        assertThrows(IllegalArgumentException.class,
                () -> obexServer.setMsgTypeFilterParams(mParams, false));
    }

    @Test
    public void setMsgTypeFilterParams_withValidFilterMessageType() throws Exception {
        BluetoothMapAccountItem accountItemWithTypeIm = BluetoothMapAccountItem.create(TEST_ID,
                TEST_NAME, TEST_PACKAGE_NAME, TEST_PROVIDER_AUTHORITY, TEST_DRAWABLE,
                BluetoothMapUtils.TYPE.IM, TEST_UCI, TEST_UCI_PREFIX);
        BluetoothMapObexServer obexServer = new BluetoothMapObexServer(null, mContext, mObserver,
                mMasInstance, accountItemWithTypeIm, TEST_ENABLE_SMS_MMS);
        int expectedMask = 1;
        mParams.setFilterMessageType(expectedMask);

        obexServer.setMsgTypeFilterParams(mParams, false);

        int masFilterMask = 0;
        masFilterMask |= BluetoothMapAppParams.FILTER_NO_EMAIL;
        expectedMask |= masFilterMask;
        assertThat(mParams.getFilterMessageType()).isEqualTo(expectedMask);
    }

    private void setUpBluetoothMapAppParams(BluetoothMapAppParams params) {
        params.setPresenceAvailability(1);
        params.setPresenceStatus("test_presence_status");
        params.setLastActivity(0);
        params.setChatState(1);
        params.setChatStateConvoId(1, 1);
    }
}
