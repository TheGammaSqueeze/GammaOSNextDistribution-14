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
import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bluetooth.BluetoothMethodProxy;
import com.android.bluetooth.R;
import com.android.bluetooth.TestUtils;
import com.android.bluetooth.opp.BluetoothOppTestUtils.CursorMockData;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public class BluetoothOppUtilityTest {

    private static final Uri CORRECT_FORMAT_BUT_INVALID_FILE_URI = Uri.parse(
            "content://com.android.bluetooth.opp/btopp/0123455343467");
    private static final Uri INCORRECT_FORMAT_URI = Uri.parse("www.google.com");

    Context mContext;
    @Mock
    Cursor mCursor;

    @Spy
    BluetoothMethodProxy mCallProxy = BluetoothMethodProxy.getInstance();


    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        BluetoothMethodProxy.setInstanceForTesting(mCallProxy);
        TestUtils.setUpUiTest();
    }

    @After
    public void tearDown() throws Exception {
        TestUtils.tearDownUiTest();

        BluetoothMethodProxy.setInstanceForTesting(null);
    }

    @Test
    public void isBluetoothShareUri_correctlyCheckUri() {
        assertThat(BluetoothOppUtility.isBluetoothShareUri(INCORRECT_FORMAT_URI)).isFalse();
        assertThat(BluetoothOppUtility.isBluetoothShareUri(CORRECT_FORMAT_BUT_INVALID_FILE_URI))
                .isTrue();
    }

    @Test
    public void queryRecord_withInvalidFileUrl_returnsNull() {
        doReturn(null).when(mCallProxy).contentResolverQuery(any(),
                eq(CORRECT_FORMAT_BUT_INVALID_FILE_URI), eq(null), eq(null),
                eq(null), eq(null));
        assertThat(BluetoothOppUtility.queryRecord(mContext,
                CORRECT_FORMAT_BUT_INVALID_FILE_URI)).isNull();
    }

    @Test
    public void queryRecord_mockCursor_returnsInstance() {
        String destinationValue = "AA:BB:CC:00:11:22";

        doReturn(mCursor).when(mCallProxy).contentResolverQuery(any(),
                eq(CORRECT_FORMAT_BUT_INVALID_FILE_URI), eq(null), eq(null),
                eq(null), eq(null));
        doReturn(true).when(mCursor).moveToFirst();
        doReturn(destinationValue).when(mCursor).getString(anyInt());
        assertThat(BluetoothOppUtility.queryRecord(mContext,
                CORRECT_FORMAT_BUT_INVALID_FILE_URI)).isInstanceOf(BluetoothOppTransferInfo.class);
    }

    @Test
    public void queryTransfersInBatch_returnsCorrectUrlArrayList() {
        long timestampValue = 123456;
        String where = BluetoothShare.TIMESTAMP + " == " + timestampValue;
        AtomicInteger cnt = new AtomicInteger(1);

        doReturn(mCursor).when(mCallProxy).contentResolverQuery(any(),
                eq(BluetoothShare.CONTENT_URI), eq(new String[]{
                        BluetoothShare._DATA
                }), eq(where), eq(null), eq(BluetoothShare._ID));


        doAnswer(invocation -> cnt.incrementAndGet() > 5).when(mCursor).isAfterLast();
        doReturn(CORRECT_FORMAT_BUT_INVALID_FILE_URI.toString()).when(mCursor)
                .getString(0);

        ArrayList<String> answer = BluetoothOppUtility.queryTransfersInBatch(mContext,
                timestampValue);
        for (String url : answer) {
            assertThat(url).isEqualTo(CORRECT_FORMAT_BUT_INVALID_FILE_URI.toString());
        }
    }

    @Test
    public void openReceivedFile_fileNotExist() {

        Uri contentResolverUri = Uri.parse("content://com.android.bluetooth.opp/btopp/0123");
        Uri fileUri = Uri.parse("content:///tmp/randomFileName.txt");

        Context spiedContext = spy(new ContextWrapper(mContext));

        doReturn(0).when(mCallProxy).contentResolverDelete(any(), any(), any(), any());
        doReturn(mCursor).when(mCallProxy).contentResolverQuery(any(),
                eq(contentResolverUri), any(), eq(null),
                eq(null), eq(null));

        doReturn(true).when(mCursor).moveToFirst();
        doReturn(fileUri.toString()).when(mCursor).getString(anyInt());

        doReturn(0).when(mCallProxy).contentResolverDelete(any(), any(), nullable(String.class),
                nullable(String[].class));

        BluetoothOppUtility.openReceivedFile(spiedContext, "randomFileName.txt",
                "text/plain", 0L, contentResolverUri);

        verify(spiedContext).startActivity(argThat(argument
                -> Objects.equals(argument.getComponent().getClassName(),
                BluetoothOppBtErrorActivity.class.getName())
        ));
    }

    @Test
    public void openReceivedFile_fileExist_HandlingApplicationExist() throws FileNotFoundException {
        Uri contentResolverUri = Uri.parse("content://com.android.bluetooth.opp/btopp/0123");
        Uri fileUri = Uri.parse("content:///tmp/randomFileName.txt");

        Context spiedContext = spy(new ContextWrapper(mContext));
        // Control BluetoothOppUtility#fileExists flow
        doReturn(mCursor).when(mCallProxy).contentResolverQuery(any(),
                eq(contentResolverUri), any(), eq(null),
                eq(null), eq(null));

        doReturn(true).when(mCursor).moveToFirst();
        doReturn(fileUri.toString()).when(mCursor).getString(anyInt());

        doReturn(0).when(mCallProxy).contentResolverDelete(any(), any(), any(), any());
        doReturn(null).when(mCallProxy).contentResolverOpenFileDescriptor(any(),
                eq(fileUri), any());

        // Control BluetoothOppUtility#isRecognizedFileType flow
        PackageManager mockManager = mock(PackageManager.class);
        doReturn(mockManager).when(spiedContext).getPackageManager();
        doReturn(List.of(new ResolveInfo())).when(mockManager).queryIntentActivities(any(),
                anyInt());

        BluetoothOppUtility.openReceivedFile(spiedContext, "randomFileName.txt",
                "text/plain", 0L, contentResolverUri);

        verify(spiedContext).startActivity(argThat(argument
                        -> Objects.equals(
                        argument.getData(), Uri.parse("content:///tmp/randomFileName.txt")
                ) && Objects.equals(argument.getAction(), Intent.ACTION_VIEW)
        ));
    }

    @Test
    public void openReceivedFile_fileExist_HandlingApplicationNotExist()
            throws FileNotFoundException {

        Uri contentResolverUri = Uri.parse("content://com.android.bluetooth.opp/btopp/0123");
        Uri fileUri = Uri.parse("content:///tmp/randomFileName.txt");

        Context spiedContext = spy(new ContextWrapper(mContext));
        // Control BluetoothOppUtility#fileExists flow
        doReturn(mCursor).when(mCallProxy).contentResolverQuery(any(),
                eq(contentResolverUri), any(), eq(null),
                eq(null), eq(null));

        doReturn(true).when(mCursor).moveToFirst();
        doReturn(fileUri.toString()).when(mCursor).getString(anyInt());


        doReturn(0).when(mCallProxy).contentResolverDelete(any(), any(), any(), any());
        doReturn(null).when(mCallProxy).contentResolverOpenFileDescriptor(any(),
                eq(fileUri), any());

        // Control BluetoothOppUtility#isRecognizedFileType flow
        PackageManager mockManager = mock(PackageManager.class);
        doReturn(mockManager).when(spiedContext).getPackageManager();
        doReturn(List.of()).when(mockManager).queryIntentActivities(any(), anyInt());

        BluetoothOppUtility.openReceivedFile(spiedContext, "randomFileName.txt",
                "text/plain", 0L, contentResolverUri);

        verify(spiedContext).startActivity(
                argThat(argument -> argument.getComponent().getClassName().equals(
                        BluetoothOppBtErrorActivity.class.getName())
                ));
    }


    @Test
    public void fillRecord_filledAllProperties() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        int idValue = 1234;
        int directionValue = BluetoothShare.DIRECTION_OUTBOUND;
        long totalBytesValue = 10;
        long currentBytesValue = 1;
        int statusValue = BluetoothShare.STATUS_PENDING;
        Long timestampValue = 123456789L;
        String destinationValue = "AA:BB:CC:00:11:22";
        String fileNameValue = mContext.getString(R.string.unknown_file);
        String fileTypeValue = "text/plain";
        BluetoothDevice remoteDevice = adapter.getRemoteDevice(destinationValue);
        String deviceNameValue =
                BluetoothOppManager.getInstance(mContext).getDeviceName(remoteDevice);

        List<CursorMockData> cursorMockDataList = List.of(
                new CursorMockData(BluetoothShare._ID, 0, idValue),
                new CursorMockData(BluetoothShare.STATUS, 1, statusValue),
                new CursorMockData(BluetoothShare.DIRECTION, 2, directionValue),
                new CursorMockData(BluetoothShare.TOTAL_BYTES, 3, totalBytesValue),
                new CursorMockData(BluetoothShare.CURRENT_BYTES, 4, currentBytesValue),
                new CursorMockData(BluetoothShare.TIMESTAMP, 5, timestampValue),
                new CursorMockData(BluetoothShare.DESTINATION, 6, destinationValue),
                new CursorMockData(BluetoothShare._DATA, 7, null),
                new CursorMockData(BluetoothShare.FILENAME_HINT, 8, null),
                new CursorMockData(BluetoothShare.MIMETYPE, 9, fileTypeValue)
        );

        BluetoothOppTestUtils.setUpMockCursor(mCursor, cursorMockDataList);

        BluetoothOppTransferInfo info = new BluetoothOppTransferInfo();
        BluetoothOppUtility.fillRecord(mContext, mCursor, info);

        assertThat(info.mID).isEqualTo(idValue);
        assertThat(info.mStatus).isEqualTo(statusValue);
        assertThat(info.mDirection).isEqualTo(directionValue);
        assertThat(info.mTotalBytes).isEqualTo(totalBytesValue);
        assertThat(info.mCurrentBytes).isEqualTo(currentBytesValue);
        assertThat(info.mTimeStamp).isEqualTo(timestampValue);
        assertThat(info.mDestAddr).isEqualTo(destinationValue);
        assertThat(info.mFileUri).isEqualTo(null);
        assertThat(info.mFileType).isEqualTo(fileTypeValue);
        assertThat(info.mDeviceName).isEqualTo(deviceNameValue);
        assertThat(info.mHandoverInitiated).isEqualTo(false);
        assertThat(info.mFileName).isEqualTo(fileNameValue);
    }

    @Test
    public void fileExists_returnFalse() {
        assertThat(
                BluetoothOppUtility.fileExists(mContext, CORRECT_FORMAT_BUT_INVALID_FILE_URI)
        ).isFalse();
    }

    @Test
    public void isRecognizedFileType_withWrongFileUriAndMimeType_returnFalse() {
        assertThat(
                BluetoothOppUtility.isRecognizedFileType(mContext,
                        CORRECT_FORMAT_BUT_INVALID_FILE_URI,
                        "aWrongMimeType")
        ).isFalse();
    }

    @Test
    public void formatProgressText() {
        assertThat(BluetoothOppUtility.formatProgressText(100, 42)).isEqualTo("42%");
    }

    @Test
    public void formatResultText() {
        String text = BluetoothOppUtility.formatResultText(1, 2, mContext);

        assertThat(text.contains("1") && text.contains("2")).isTrue();
    }

    @Test
    public void getStatusDescription_returnCorrectString() {
        String deviceName = "randomName";
        assertThat(BluetoothOppUtility.getStatusDescription(mContext,
                BluetoothShare.STATUS_PENDING, deviceName)).isEqualTo(
                mContext.getString(R.string.status_pending));
        assertThat(BluetoothOppUtility.getStatusDescription(mContext,
                BluetoothShare.STATUS_RUNNING, deviceName)).isEqualTo(
                mContext.getString(R.string.status_running));
        assertThat(BluetoothOppUtility.getStatusDescription(mContext,
                BluetoothShare.STATUS_SUCCESS, deviceName)).isEqualTo(
                mContext.getString(R.string.status_success));
        assertThat(BluetoothOppUtility.getStatusDescription(mContext,
                BluetoothShare.STATUS_NOT_ACCEPTABLE, deviceName)).isEqualTo(
                mContext.getString(R.string.status_not_accept));
        assertThat(BluetoothOppUtility.getStatusDescription(mContext,
                BluetoothShare.STATUS_FORBIDDEN, deviceName)).isEqualTo(
                mContext.getString(R.string.status_forbidden));
        assertThat(BluetoothOppUtility.getStatusDescription(mContext,
                BluetoothShare.STATUS_CANCELED, deviceName)).isEqualTo(
                mContext.getString(R.string.status_canceled));
        assertThat(BluetoothOppUtility.getStatusDescription(mContext,
                BluetoothShare.STATUS_FILE_ERROR, deviceName)).isEqualTo(
                mContext.getString(R.string.status_file_error));
        assertThat(BluetoothOppUtility.getStatusDescription(mContext,
                BluetoothShare.STATUS_CONNECTION_ERROR, deviceName)).isEqualTo(
                mContext.getString(R.string.status_connection_error));
        assertThat(BluetoothOppUtility.getStatusDescription(mContext,
                BluetoothShare.STATUS_ERROR_NO_SDCARD, deviceName)).isEqualTo(
                mContext.getString(BluetoothOppUtility.deviceHasNoSdCard()
                        ? R.string.status_no_sd_card_nosdcard
                        : R.string.status_no_sd_card_default)
        );
        assertThat(BluetoothOppUtility.getStatusDescription(mContext,
                BluetoothShare.STATUS_ERROR_SDCARD_FULL, deviceName)).isEqualTo(
                mContext.getString(
                        BluetoothOppUtility.deviceHasNoSdCard() ? R.string.bt_sm_2_1_nosdcard
                                : R.string.bt_sm_2_1_default)
        );
        assertThat(BluetoothOppUtility.getStatusDescription(mContext,
                BluetoothShare.STATUS_BAD_REQUEST, deviceName)).isEqualTo(
                mContext.getString(R.string.status_protocol_error));
        assertThat(BluetoothOppUtility.getStatusDescription(mContext, 12345465,
                deviceName)).isEqualTo(mContext.getString(R.string.status_unknown_error));
    }

    @Test
    public void originalUri_trimBeforeAt() {
        Uri originalUri = Uri.parse("com.android.bluetooth.opp.BluetoothOppSendFileInfo");
        Uri uri = Uri.parse("com.android.bluetooth.opp.BluetoothOppSendFileInfo@dfe15a6");
        assertThat(BluetoothOppUtility.originalUri(uri)).isEqualTo(originalUri);
    }

    @Test
    public void fileInfo_testFileInfoFunctions() {
        assertThat(
                BluetoothOppUtility.getSendFileInfo(CORRECT_FORMAT_BUT_INVALID_FILE_URI)
        ).isEqualTo(
                BluetoothOppSendFileInfo.SEND_FILE_INFO_ERROR
        );
        assertThat(BluetoothOppUtility.generateUri(CORRECT_FORMAT_BUT_INVALID_FILE_URI,
                BluetoothOppSendFileInfo.SEND_FILE_INFO_ERROR).toString()
        ).contains(
                CORRECT_FORMAT_BUT_INVALID_FILE_URI.toString());
        try {
            BluetoothOppUtility.putSendFileInfo(CORRECT_FORMAT_BUT_INVALID_FILE_URI,
                    BluetoothOppSendFileInfo.SEND_FILE_INFO_ERROR);
            BluetoothOppUtility.closeSendFileInfo(CORRECT_FORMAT_BUT_INVALID_FILE_URI);
        } catch (Exception e) {
            assertWithMessage("Exception should not happen. " + e).fail();
        }
    }

}
