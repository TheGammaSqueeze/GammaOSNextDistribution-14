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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import android.content.Context;
import android.content.ContextWrapper;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.BluetoothMethodProxy;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class BluetoothOppReceiveFileInfoTest {
    Context mContext;
    BluetoothMethodProxy mCallProxy;

    MatrixCursor mCursor;

    @Before
    public void setUp() {
        mContext = spy(new ContextWrapper(
                InstrumentationRegistry.getInstrumentation().getTargetContext()));

        mCallProxy = spy(BluetoothMethodProxy.getInstance());
        BluetoothMethodProxy.setInstanceForTesting(mCallProxy);

        doReturn(null).when(mCallProxy).contentResolverInsert(
                any(), eq(BluetoothShare.CONTENT_URI), any());
    }

    @After
    public void tearDown() {
        BluetoothMethodProxy.setInstanceForTesting(null);
        BluetoothOppManager.sInstance = null;
    }

    @Test
    public void createInstance_withStatus_createCorrectly() {
        BluetoothOppReceiveFileInfo info =
                new BluetoothOppReceiveFileInfo(BluetoothShare.STATUS_CANCELED);

        assertThat(info.mStatus).isEqualTo(BluetoothShare.STATUS_CANCELED);
    }

    @Test
    public void createInstance_withData_createCorrectly() {
        String data = "abcdef";
        int status = BluetoothShare.STATUS_SUCCESS;
        BluetoothOppReceiveFileInfo info =
                new BluetoothOppReceiveFileInfo(data, data.length(), status);

        assertThat(info.mStatus).isEqualTo(status);
        assertThat(info.mLength).isEqualTo(data.length());
        assertThat(info.mData).isEqualTo(data);
    }

    @Test
    public void createInstance_withFileName_createCorrectly() {
        String fileName = "abcdef.txt";
        int length = 10;
        int status = BluetoothShare.STATUS_SUCCESS;
        Uri uri = Uri.parse("content:///abc/xyz");
        BluetoothOppReceiveFileInfo info =
                new BluetoothOppReceiveFileInfo(fileName, length, uri, status);

        assertThat(info.mStatus).isEqualTo(status);
        assertThat(info.mLength).isEqualTo(length);
        assertThat(info.mFileName).isEqualTo(fileName);
        assertThat(info.mInsertUri).isEqualTo(uri);
    }

    @Test
    public void generateFileInfo_wrongHint_fileError() {
        Assume.assumeTrue("Ignore test when if there is no media mounted",
                Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED));
        int id = 0;
        long fileLength = 100;
        String hint = "content:///arandomhint/";
        String mimeType = "text/plain";

        mCursor = new MatrixCursor(
                new String[]{BluetoothShare.FILENAME_HINT, BluetoothShare.TOTAL_BYTES,
                        BluetoothShare.MIMETYPE});
        mCursor.addRow(new Object[]{hint, fileLength, mimeType});

        doReturn(mCursor).when(mCallProxy).contentResolverQuery(
                any(), eq(Uri.parse(BluetoothShare.CONTENT_URI + "/" + id)), any(), any(), any(),
                any());

        BluetoothOppReceiveFileInfo info =
                BluetoothOppReceiveFileInfo.generateFileInfo(mContext, id);

        assertThat(info.mStatus).isEqualTo(BluetoothShare.STATUS_FILE_ERROR);
    }

    @Test
    public void generateFileInfo_noMediaMounted_noSdcardError() {
        Assume.assumeTrue("Ignore test when if there is media mounted",
                !Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED));
        int id = 0;

        BluetoothOppReceiveFileInfo info =
                BluetoothOppReceiveFileInfo.generateFileInfo(mContext, id);

        assertThat(info.mStatus).isEqualTo(BluetoothShare.STATUS_ERROR_NO_SDCARD);
    }

    @Test
    public void generateFileInfo_noInsertUri_returnFileError() {
        Assume.assumeTrue("Ignore test when if there is not media mounted",
                Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED));
        int id = 0;
        long fileLength = 100;
        String hint = "content:///arandomhint.txt";
        String mimeType = "text/plain";

        mCursor = new MatrixCursor(
                new String[]{BluetoothShare.FILENAME_HINT, BluetoothShare.TOTAL_BYTES,
                        BluetoothShare.MIMETYPE});
        mCursor.addRow(new Object[]{hint, fileLength, mimeType});

        doReturn(mCursor).when(mCallProxy).contentResolverQuery(
                any(), eq(Uri.parse(BluetoothShare.CONTENT_URI + "/" + id)), any(), any(), any(),
                any());

        doReturn(null).when(mCallProxy).contentResolverInsert(
                any(), eq(MediaStore.Downloads.EXTERNAL_CONTENT_URI), any());

        BluetoothOppReceiveFileInfo info =
                BluetoothOppReceiveFileInfo.generateFileInfo(mContext, id);

        assertThat(info.mStatus).isEqualTo(BluetoothShare.STATUS_FILE_ERROR);
    }

    @Test
    public void generateFileInfo_withInsertUri_workCorrectly() {
        Assume.assumeTrue("Ignore test when if there is not media mounted",
                Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED));
        int id = 0;
        long fileLength = 100;
        String hint = "content:///arandomhint.txt";
        String mimeType = "text/plain";
        Uri insertUri = Uri.parse("content:///abc/xyz");

        mCursor = new MatrixCursor(
                new String[]{BluetoothShare.FILENAME_HINT, BluetoothShare.TOTAL_BYTES,
                        BluetoothShare.MIMETYPE});
        mCursor.addRow(new Object[]{hint, fileLength, mimeType});

        doReturn(mCursor).when(mCallProxy).contentResolverQuery(
                any(), eq(Uri.parse(BluetoothShare.CONTENT_URI + "/" + id)), any(), any(), any(),
                any());

        doReturn(insertUri).when(mCallProxy).contentResolverInsert(
                any(), eq(MediaStore.Downloads.EXTERNAL_CONTENT_URI), any());

        assertThat(mCursor.moveToFirst()).isTrue();

        BluetoothOppReceiveFileInfo info =
                BluetoothOppReceiveFileInfo.generateFileInfo(mContext, id);

        assertThat(info.mStatus).isEqualTo(0);
        assertThat(info.mInsertUri).isEqualTo(insertUri);
        assertThat(info.mLength).isEqualTo(fileLength);
    }

    @Test
    public void generateFileInfo_longFileName_trimFileName() {
        Assume.assumeTrue("Ignore test when if there is not media mounted",
                Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED));
        int id = 0;
        long fileLength = 100;
        String hint = "content:///" + "a".repeat(500) + ".txt";
        String mimeType = "text/plain";
        Uri insertUri = Uri.parse("content:///abc/xyz");

        mCursor = new MatrixCursor(
                new String[]{BluetoothShare.FILENAME_HINT, BluetoothShare.TOTAL_BYTES,
                        BluetoothShare.MIMETYPE});
        mCursor.addRow(new Object[]{hint, fileLength, mimeType});

        doReturn(mCursor).when(mCallProxy).contentResolverQuery(
                any(), eq(Uri.parse(BluetoothShare.CONTENT_URI + "/" + id)), any(), any(), any(),
                any());

        doReturn(insertUri).when(mCallProxy).contentResolverInsert(
                any(), eq(MediaStore.Downloads.EXTERNAL_CONTENT_URI), any());

        assertThat(mCursor.moveToFirst()).isTrue();

        BluetoothOppReceiveFileInfo info =
                BluetoothOppReceiveFileInfo.generateFileInfo(mContext, id);

        assertThat(info.mStatus).isEqualTo(0);
        assertThat(info.mInsertUri).isEqualTo(insertUri);
        assertThat(info.mLength).isEqualTo(fileLength);
        // maximum file length for Linux is 255
        assertThat(info.mFileName.length()).isLessThan(256);
    }
}
