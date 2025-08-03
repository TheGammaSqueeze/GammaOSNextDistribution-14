/*
 * Copyright 2022 The Android Open Source Project
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


package com.android.bluetooth.opp;

import static android.os.UserHandle.myUserId;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bluetooth.BluetoothMethodProxy;

import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.FileInputStream;
import java.io.IOException;

@RunWith(TestParameterInjector.class)
public class BluetoothOppSendFileInfoTest {
    Context mContext;
    MatrixCursor mCursor;

    @Mock
    BluetoothMethodProxy mCallProxy;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        BluetoothMethodProxy.setInstanceForTesting(mCallProxy);
    }

    @After
    public void tearDown() {
        BluetoothMethodProxy.setInstanceForTesting(null);
    }

    @Test
    public void createInstance_withFileInputStream() {
        String fileName = "abc.txt";
        String type = "text/plain";
        long length = 10000;
        FileInputStream inputStream = mock(FileInputStream.class);
        int status = BluetoothShare.STATUS_SUCCESS;
        BluetoothOppSendFileInfo info =
                new BluetoothOppSendFileInfo(fileName, type, length, inputStream, status);

        assertThat(info.mStatus).isEqualTo(status);
        assertThat(info.mFileName).isEqualTo(fileName);
        assertThat(info.mLength).isEqualTo(length);
        assertThat(info.mInputStream).isEqualTo(inputStream);
        assertThat(info.mMimetype).isEqualTo(type);
    }

    @Test
    public void createInstance_withoutFileInputStream() {
        String type = "text/plain";
        long length = 10000;
        int status = BluetoothShare.STATUS_SUCCESS;
        String data = "Testing is boring";
        BluetoothOppSendFileInfo info =
                new BluetoothOppSendFileInfo(data, type, length, status);

        assertThat(info.mStatus).isEqualTo(status);
        assertThat(info.mData).isEqualTo(data);
        assertThat(info.mLength).isEqualTo(length);
        assertThat(info.mMimetype).isEqualTo(type);
    }

    @Test
    public void generateFileInfo_withUnsupportedScheme_returnsSendFileInfoError() {
        String type = "text/plain";
        Uri uri = Uri.parse("https://www.google.com");

        BluetoothOppSendFileInfo info = BluetoothOppSendFileInfo.generateFileInfo(mContext, uri,
                type, true);
        assertThat(info).isEqualTo(BluetoothOppSendFileInfo.SEND_FILE_INFO_ERROR);
    }

    @Test
    public void generateFileInfo_withForbiddenExternalUri_returnsSendFileInfoError() {
        String type = "text/plain";
        Uri uri = Uri.parse("content://com.android.bluetooth.map.MmsFileProvider:8080");

        BluetoothOppSendFileInfo info = BluetoothOppSendFileInfo.generateFileInfo(mContext, uri,
                type, true);
        assertThat(info).isEqualTo(BluetoothOppSendFileInfo.SEND_FILE_INFO_ERROR);
    }

    @Test
    @Ignore("TODO: b/377774165 - Use source-built BT APEX instead of prebuilt")
    public void generateFileInfo_withContentUriForOtherUser_returnsSendFileInfoError(
            @TestParameter boolean encodedAt) throws Exception {
        String type = "image/jpeg";
        String authoritySuffix = encodedAt ? "%40media" : "@media";
        Uri uri = buildContentUriWithEncodedAuthority((myUserId() + 1) + authoritySuffix);

        long fileLength = 1000;
        String fileName = "pic.jpg";

        FileInputStream fs = mock(FileInputStream.class);
        AssetFileDescriptor fd = mock(AssetFileDescriptor.class);
        doReturn(fileLength).when(fd).getLength();
        doReturn(fs).when(fd).createInputStream();

        doReturn(fd).when(mCallProxy).contentResolverOpenAssetFileDescriptor(any(), eq(uri), any());

        mCursor =
                new MatrixCursor(new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        mCursor.addRow(new Object[] {fileName, fileLength});

        doReturn(mCursor)
                .when(mCallProxy)
                .contentResolverQuery(any(), eq(uri), any(), any(), any(), any());

        BluetoothOppSendFileInfo info =
                BluetoothOppSendFileInfo.generateFileInfo(mContext, uri, type, true);

        assertThat(info).isEqualTo(BluetoothOppSendFileInfo.SEND_FILE_INFO_ERROR);
    }

    @Test
    public void generateFileInfo_withContentUriForImplicitUser_returnsInfoWithCorrectLength()
            throws Exception {
        String type = "image/jpeg";
        Uri uri = buildContentUriWithEncodedAuthority("media");

        long fileLength = 1000;
        String fileName = "pic.jpg";

        FileInputStream fs = mock(FileInputStream.class);
        AssetFileDescriptor fd = mock(AssetFileDescriptor.class);
        doReturn(fileLength).when(fd).getLength();
        doReturn(fs).when(fd).createInputStream();

        doReturn(fd).when(mCallProxy).contentResolverOpenAssetFileDescriptor(any(), eq(uri), any());

        mCursor =
                new MatrixCursor(new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        mCursor.addRow(new Object[] {fileName, fileLength});

        doReturn(mCursor)
                .when(mCallProxy)
                .contentResolverQuery(any(), eq(uri), any(), any(), any(), any());

        BluetoothOppSendFileInfo info =
                BluetoothOppSendFileInfo.generateFileInfo(mContext, uri, type, true);

        assertThat(info.mInputStream).isEqualTo(fs);
        assertThat(info.mFileName).isEqualTo(fileName);
        assertThat(info.mLength).isEqualTo(fileLength);
        assertThat(info.mStatus).isEqualTo(0);
    }

    @Test
    public void generateFileInfo_withContentUriForSameUser_returnsInfoWithCorrectLength(
            @TestParameter boolean encodedAt) throws Exception {
        String type = "image/jpeg";
        String authoritySuffix = encodedAt ? "%40media" : "@media";
        Uri uri = buildContentUriWithEncodedAuthority(myUserId() + authoritySuffix);

        long fileLength = 1000;
        String fileName = "pic.jpg";

        FileInputStream fs = mock(FileInputStream.class);
        AssetFileDescriptor fd = mock(AssetFileDescriptor.class);
        doReturn(fileLength).when(fd).getLength();
        doReturn(fs).when(fd).createInputStream();

        doReturn(fd).when(mCallProxy).contentResolverOpenAssetFileDescriptor(any(), eq(uri), any());

        mCursor =
                new MatrixCursor(new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        mCursor.addRow(new Object[] {fileName, fileLength});

        doReturn(mCursor)
                .when(mCallProxy)
                .contentResolverQuery(any(), eq(uri), any(), any(), any(), any());

        BluetoothOppSendFileInfo info =
                BluetoothOppSendFileInfo.generateFileInfo(mContext, uri, type, true);

        assertThat(info.mInputStream).isEqualTo(fs);
        assertThat(info.mFileName).isEqualTo(fileName);
        assertThat(info.mLength).isEqualTo(fileLength);
        assertThat(info.mStatus).isEqualTo(0);
    }

    private static Uri buildContentUriWithEncodedAuthority(String authority) {
        return new Uri.Builder()
                .scheme("content")
                .encodedAuthority(authority)
                .path("external/images/media/1")
                .build();
    }

    @Test
    public void generateFileInfo_withoutPermissionForAccessingUri_returnsSendFileInfoError() {
        String type = "text/plain";
        Uri uri = Uri.parse("content:///hello/world");

        doThrow(new SecurityException()).when(mCallProxy).contentResolverQuery(
                any(), eq(uri), any(), any(), any(),
                any());

        BluetoothOppSendFileInfo info = BluetoothOppSendFileInfo.generateFileInfo(mContext, uri,
                type, true);
        assertThat(info).isEqualTo(BluetoothOppSendFileInfo.SEND_FILE_INFO_ERROR);
    }

    @Test
    public void generateFileInfo_withUncorrectableMismatch_returnsSendFileInfoError()
            throws IOException {
        String type = "text/plain";
        Uri uri = Uri.parse("content:///hello/world");

        long fileLength = 0;
        String fileName = "coolName.txt";

        AssetFileDescriptor fd = mock(AssetFileDescriptor.class);
        FileInputStream fs = mock(FileInputStream.class);

        mCursor = new MatrixCursor(new String[]{
                OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE
        });
        mCursor.addRow(new Object[]{fileName, fileLength});

        doReturn(mCursor).when(mCallProxy).contentResolverQuery(
                any(), eq(uri), any(), any(), any(),
                any());

        doReturn(fd).when(mCallProxy).contentResolverOpenAssetFileDescriptor(
                any(), eq(uri), any());
        doReturn(0L).when(fd).getLength();
        doThrow(new IOException()).when(fd).createInputStream();
        doReturn(fs).when(mCallProxy).contentResolverOpenInputStream(any(), eq(uri));
        doReturn(0, -1).when(fs).read(any(), anyInt(), anyInt());

        BluetoothOppSendFileInfo info = BluetoothOppSendFileInfo.generateFileInfo(mContext, uri,
                type, true);

        assertThat(info).isEqualTo(BluetoothOppSendFileInfo.SEND_FILE_INFO_ERROR);
    }

    @Test
    public void generateFileInfo_withCorrectableMismatch_returnInfoWithCorrectLength()
            throws IOException {
        String type = "text/plain";
        Uri uri = Uri.parse("content:///hello/world");

        long fileLength = 0;
        long correctFileLength = 1000;
        String fileName = "coolName.txt";

        AssetFileDescriptor fd = mock(AssetFileDescriptor.class);
        FileInputStream fs = mock(FileInputStream.class);

        mCursor = new MatrixCursor(new String[]{
                OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE
        });
        mCursor.addRow(new Object[]{fileName, fileLength});

        doReturn(mCursor).when(mCallProxy).contentResolverQuery(
                any(), eq(uri), any(), any(), any(),
                any());

        doReturn(fd).when(mCallProxy).contentResolverOpenAssetFileDescriptor(
                any(), eq(uri), any());
        doReturn(0L).when(fd).getLength();
        doReturn(fs).when(fd).createInputStream();

        // the real size will be returned in getStreamSize(fs)
        doReturn((int) correctFileLength, -1).when(fs).read(any(), anyInt(), anyInt());

        BluetoothOppSendFileInfo info = BluetoothOppSendFileInfo.generateFileInfo(mContext, uri,
                type, true);

        assertThat(info.mInputStream).isEqualTo(fs);
        assertThat(info.mFileName).isEqualTo(fileName);
        assertThat(info.mLength).isEqualTo(correctFileLength);
        assertThat(info.mStatus).isEqualTo(0);
    }

    @Test
    public void generateFileInfo_withFileUriNotInExternalStorageDir_returnFileErrorInfo() {
        String type = "text/plain";
        Uri uri = Uri.parse("file:///obviously/not/in/external/storage");

        BluetoothOppSendFileInfo info = BluetoothOppSendFileInfo.generateFileInfo(mContext, uri,
                type, true);

        assertThat(info).isEqualTo(BluetoothOppSendFileInfo.SEND_FILE_INFO_ERROR);
    }
}
